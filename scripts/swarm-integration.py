#!/usr/bin/env python3
"""Build the application JAR and test replication between four real FdSwarm nodes."""

import argparse
from contextlib import contextmanager
from datetime import datetime, timedelta, timezone
import json
import os
import re
from pathlib import Path
import secrets
import shutil
import socket
import subprocess
import sys
import tempfile
import time
import traceback
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
# Ignore workstation HTTP proxy settings for requests to the local test nodes.
HTTP = urllib.request.build_opener(urllib.request.ProxyHandler({}))


class SwarmTest:
    def __init__(self, args):
        self.args = args
        self.directory = Path(tempfile.mkdtemp(prefix="fdswarm-swarm-integration-"))
        self.log_file = (self.directory / "progress.log").open("w", encoding="utf-8")
        self.token = secrets.token_hex(32)
        self.nodes = []
        self.results = []
        self.last_state = {}
        self.expected = {}
        self.config = None
        self.udp = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        self.udp.bind(("0.0.0.0", 0))
        self.udp_port = self.udp.getsockname()[1]
        self.reservations = []
        for _ in range(4):
            reservation = socket.socket()
            reservation.bind(("127.0.0.1", 0))
            self.reservations.append(reservation)

    def log(self, message):
        line = f"{datetime.now().astimezone().isoformat(timespec='seconds')} {message}"
        print(line, flush=True)
        print(line, file=self.log_file, flush=True)

    @contextmanager
    def phase(self, name):
        started = time.monotonic()
        self.log(f"START {name}")
        error = None
        try:
            yield
        except BaseException:
            error = traceback.format_exc()
            raise
        finally:
            seconds = time.monotonic() - started
            self.results.append((name, seconds, error))
            self.log(f"{'FAIL' if error else 'PASS'} {name} ({seconds:.2f}s)")

    def build(self):
        if self.args.skip_build:
            self.jar = self.args.jar.resolve() if self.args.jar else self.build_dir / "fdswarm/assembly.dest/fdswarm.jar"
        else:
            if self.args.jar:
                raise ValueError("--jar requires --skip-build")
            env = dict(os.environ, MILL_OUTPUT_DIR=str(self.build_dir))
            self.log(f"Building fdswarm.jar; detailed output: {self.directory / 'build.log'}")
            with (self.directory / "build.log").open("wb") as output:
                process = subprocess.Popen([str(ROOT / "mill"), "--no-server",
                                            "fdswarm.integrationCoverageAssembly" if self.args.coverage else "fdswarm.assembly"],
                                           cwd=ROOT, env=env, stdout=output, stderr=subprocess.STDOUT)
                try:
                    while True:
                        try:
                            code = process.wait(timeout=5)
                            break
                        except subprocess.TimeoutExpired:
                            self.log("Build still running…")
                    if code:
                        raise RuntimeError(f"JAR build failed with exit code {code}; see build.log")
                finally:
                    if process.poll() is None:
                        process.terminate()
                        process.wait()
            self.jar = self.build_dir / ("fdswarm/integrationCoverageAssembly.dest/fdswarm-coverage.jar"
                                        if self.args.coverage else "fdswarm/assembly.dest/fdswarm.jar")
        if not self.jar.is_file():
            raise FileNotFoundError(self.jar)

    @property
    def build_dir(self):
        if self.args.coverage:
            # Instrumentation embeds this path. A fresh directory prevents mixing unit
            # tests, previous runs, or measurements from different statement IDs.
            return self.directory / "coverage-build"
        return Path(os.environ.get("MILL_OUTPUT_DIR", "/private/tmp/fdswarm-swarm-build")).resolve()

    def request(self, node, path, body=None, control=False):
        headers = {"Accept": "*/*"}
        if control:
            headers["X-Fdswarm-Test-Token"] = self.token
        if body is not None:
            headers["Content-Type"] = "application/json"
        request = urllib.request.Request(f"http://127.0.0.1:{node['port']}/{path}",
            data=json.dumps(body).encode() if body is not None else None,
            headers=headers, method="POST" if body is not None else "GET")
        with HTTP.open(request, timeout=3) as response:
            content = response.read()
            return json.loads(content) if content else None

    def wait_for(self, description, check, stable_seconds=0):
        deadline = time.monotonic() + self.args.timeout
        stable_since = None
        next_message = 0
        last_error = "condition not met"
        while time.monotonic() < deadline:
            for node in self.nodes:
                code = node["process"].poll()
                if code is not None:
                    raise RuntimeError(f"Node {node['name']} exited with code {code}; see its stdout/stderr logs")
            try:
                matches = check()
                if matches:
                    if stable_since is None:
                        stable_since = time.monotonic()
                    if time.monotonic() - stable_since >= stable_seconds:
                        return
                    last_error = "matching; checking stability"
                else:
                    stable_since = None
                    last_error = "waiting for convergence"
            except (OSError, ValueError, urllib.error.URLError) as error:
                stable_since = None
                last_error = str(error)
            if time.monotonic() >= next_message:
                self.log(f"WAIT {description}: {last_error}; {json.dumps(self.last_state, sort_keys=True)}")
                next_message = time.monotonic() + 5
            time.sleep(0.25)
        raise AssertionError(f"Timed out after {self.args.timeout}s: {description}; {last_error}; {self.last_state}")

    def launch(self, index):
        name = chr(ord('A') + index)
        node_dir = self.directory / f"node-{name}"
        node_dir.mkdir()
        data_dir = node_dir / "data"
        data_dir.mkdir()
        assert not list(data_dir.iterdir()), "Nodes must begin with an empty application directory"
        reservation = self.reservations[index]
        port = reservation.getsockname()[1]
        startup = {"enable": True, "operator": ["W1AAA", "W1BBB", "W1CCC", "W1DDD"][index],
                   "bandMode": ["20m CW", "40m PH", "15m DIGI", "10m CW"][index],
                   "clearQsos": True, "skipInitDiscover": True, "debugMode": "Off",
                   "id": f"swarm-{name}-{secrets.token_hex(4)}",
                   "integrationTest": {"dataDirectory": str(data_dir), "udpPort": self.udp_port, "token": self.token,
                                       "broadcastAddress": self.broadcast_address}}
        startup_path = node_dir / "startup.json"
        startup_path.write_text(json.dumps(startup, indent=2), encoding="utf-8")
        # Each process receives a separate home as well as its explicit app data directory.
        home = node_dir / "home"
        home.mkdir()
        env = dict(os.environ, PORT=str(port))
        env.pop("PREDELETE_CONTEST_CONFIG", None)
        stdout = (node_dir / "stdout.log").open("wb")
        stderr = (node_dir / "stderr.log").open("wb")
        reservation.close()
        if index == 0:
            self.udp.close()
        command = [self.args.java, f"-Duser.home={home}", "-Xmx512m", "-jar", str(self.jar),
                   "--startupInfo", str(startup_path), "--integration-test"]
        self.log(f"Launching node {name}: HTTP {port}, shared UDP {self.udp_port}, logs {node_dir}")
        try:
            process = subprocess.Popen(command, cwd=node_dir, env=env, stdout=stdout, stderr=stderr)
        finally:
            stdout.close()
            stderr.close()
        node = {"name": name, "port": port, "process": process, "directory": node_dir, "startup": startup}
        self.nodes.append(node)
        self.wait_for(f"node {name} ready", lambda: self.request(node, "integration-test/ready", control=True) is True)
        initial = self.request(node, "statusMessage")
        assert initial["bandNodeOperator"]["operator"] == startup["operator"], "Startup operator was not applied"
        assert initial["bandNodeOperator"]["bandMode"] == startup["bandMode"], "Startup band/mode was not applied"
        # Confirm that a local caller without the per-run token cannot invoke controls.
        try:
            self.request(node, "integration-test/ready")
        except urllib.error.HTTPError as error:
            assert error.code in (400, 401, 403), error
        else:
            raise AssertionError("Test controls accepted a request without the test token")
        return node

    def converged(self, config, qsos, contest_start=None):
        good = True
        self.last_state = {}
        for node in self.nodes:
            snapshot = self.request(node, "qsos")
            actual_config = self.request(node, "contestConfig")
            actual_qsos = {qso["uuid"]: qso for qso in snapshot["qsos"]}
            actual_start = snapshot["statusMessage"]["contestStart"]
            self.last_state[node["name"]] = {"qsos": len(snapshot["qsos"]),
                "fingerPrint": (actual_config or {}).get("fingerPrint"), "contestStart": actual_start}
            good &= (actual_qsos == qsos and len(snapshot["qsos"]) == len(qsos)
                     and actual_config == config and (contest_start is None or actual_start == contest_start))
        return good

    def enter_qsos(self, nodes, prefix):
        for index, node in enumerate(nodes):
            for number in range(3):
                body = {"callsign": f"K{index + 1}{prefix}{chr(ord('A') + number)}",
                        "exchange": "2A IL" if self.args.contest == "ARRL" else "2O IL",
                        "bandMode": node["startup"]["bandMode"],
                        "stamp": (datetime.now(timezone.utc) - timedelta(seconds=5)).isoformat().replace("+00:00", "Z")}
                qso = self.request(node, "integration-test/qso", body, control=True)
                assert qso["qsoMetadata"]["node"]["instanceId"] == node["startup"]["id"]
                assert qso["qsoMetadata"]["station"]["operator"] == node["startup"]["operator"]
                assert qso["callsign"] == body["callsign"]
                self.expected[qso["uuid"]] = qso
                self.log(f"Entered {qso['callsign']} on {node['name']} UUID={qso['uuid']}")
        (self.directory / f"expected-qsos-{prefix.lower()}.json").write_text(json.dumps(list(self.expected.values()), indent=2), encoding="utf-8")

    def select_broadcast_address(self):
        candidates = [self.args.broadcast_address] if self.args.broadcast_address else []
        if not candidates and Path("/sbin/ifconfig").is_file():
            interfaces = subprocess.check_output(["/sbin/ifconfig"], text=True)
            candidates.extend(re.findall(r"\bbroadcast (\d+\.\d+\.\d+\.\d+)", interfaces))
        if not self.args.broadcast_address:
            candidates.append("255.255.255.255")
        for address in dict.fromkeys(candidates):
            sockets = []
            try:
                for _ in range(2):
                    receiver = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
                    sockets.append(receiver)
                    receiver.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
                    if hasattr(socket, "SO_REUSEPORT"):
                        receiver.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEPORT, 1)
                    receiver.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
                    receiver.bind(("0.0.0.0", sockets[0].getsockname()[1] if len(sockets) > 1 else 0))
                    receiver.settimeout(0.5)
                sockets[0].sendto(b"fdswarm-integration-broadcast-probe", (address, sockets[0].getsockname()[1]))
                if all(receiver.recvfrom(128)[0] == b"fdswarm-integration-broadcast-probe" for receiver in sockets):
                    self.broadcast_address = address
                    self.log(f"Broadcast preflight passed: {address}; test UDP port {self.udp_port}")
                    return
            except OSError as error:
                self.log(f"Broadcast preflight {address}: {error}")
            finally:
                for receiver in sockets:
                    receiver.close()
        raise RuntimeError("No working UDP broadcast address. Check network access or supply --broadcast-address with your LAN broadcast address.")

    def scenario(self):
        with self.phase("0. Verify local UDP broadcast delivery"):
            self.select_broadcast_address()
        with self.phase("1. Build application JAR"):
            self.build()
        with self.phase("2. Start three empty nodes and configure node A"):
            for index in range(3):
                self.launch(index)
            config_request = {"name": self.args.contest, "ourCallsign": "W1AW", "transmitters": 2,
                              "ourClass": "A" if self.args.contest == "ARRL" else "O",
                              "ourSection": "CT", "club": "Integration initial"}
            self.config = self.request(self.nodes[0], "integration-test/contest", config_request, control=True)
            self.wait_for("initial configuration on A/B/C", lambda: self.converged(self.config, {}))
        with self.phase("3. Enter QSOs on each original node and verify complete propagation"):
            self.enter_qsos(self.nodes, "INT")
            self.wait_for("all 9 QSOs on A/B/C", lambda: self.converged(self.config, self.expected))
        with self.phase("4. Add empty node D and verify QSO and configuration catch-up"):
            self.launch(3)
            self.wait_for("all 9 QSOs and full configuration on A/B/C/D",
                          lambda: self.converged(self.config, self.expected))
        with self.phase("5. Start a contest on B; verify all nodes clear and remain clear"):
            self.contest_start = self.request(self.nodes[1], "integration-test/start-contest", {}, control=True)
            self.wait_for("empty stores with matching contest-start timestamp",
                          lambda: self.converged(self.config, {}, self.contest_start), stable_seconds=12)
            self.expected = {}
        with self.phase("6. Change contest configuration on C and verify all four nodes"):
            config_request["club"] = "Integration changed on node C"
            config_request["transmitters"] = 3
            previous = self.config
            self.config = self.request(self.nodes[2], "integration-test/contest", config_request, control=True)
            assert previous["fingerPrint"] != self.config["fingerPrint"]
            assert previous["timeStamp"] != self.config["timeStamp"]
            self.wait_for("new complete configuration on A/B/C/D",
                          lambda: self.converged(self.config, {}, self.contest_start))
        with self.phase("7. Enter new-contest QSOs and verify replication still works"):
            self.enter_qsos(self.nodes, "NEW")
            self.wait_for("all 12 new QSOs, no old QSOs",
                          lambda: self.converged(self.config, self.expected, self.contest_start), stable_seconds=2)

    def coverage_report(self):
        data = self.build_dir / "fdswarm/scoverage/data.dest"
        measurements = list(data.glob("scoverage.measurements.*"))
        runtimes = {match.group(1) for path in measurements
                    if (match := re.match(r"scoverage\.measurements\.([0-9a-f-]{36})", path.name))}
        if len(runtimes) < len(self.nodes):
            raise AssertionError(f"Expected measurements from {len(self.nodes)} JVMs, found {len(runtimes)}")
        self.log(f"Combining {len(measurements)} measurement files from {len(runtimes)} JVMs")
        env = dict(os.environ, MILL_OUTPUT_DIR=str(self.build_dir))
        with (self.directory / "coverage.log").open("wb") as output:
            for target in ("fdswarm.scoverage.htmlReport", "fdswarm.scoverage.xmlReport"):
                subprocess.run([str(ROOT / "mill"), "--no-server", target], cwd=ROOT,
                               env=env, stdout=output, stderr=subprocess.STDOUT, check=True)
        report = self.build_dir / "fdswarm/scoverage/htmlReport.dest"
        shutil.copytree(report, self.directory / "coverage")
        xml = self.build_dir / "fdswarm/scoverage/xmlReport.dest/scoverage.xml"
        shutil.copy2(xml, self.directory / "coverage.xml")
        summary = ET.parse(xml).getroot()
        invoked = int(summary.get("statements-invoked", "0"))
        assert invoked > 0, "Coverage report contains no executed statements"
        self.log(f"Coverage: {invoked} of {summary.get('statement-count')} statements invoked; "
                 f"HTML report: {self.directory / 'coverage/index.html'}")

    def finish(self, failed):
        if failed:
            for node in self.nodes:
                for endpoint in ("qsos", "contestConfig", "statusMessage"):
                    try:
                        value = self.request(node, endpoint)
                        (node["directory"] / f"failure-{endpoint}.json").write_text(json.dumps(value, indent=2), encoding="utf-8")
                    except Exception as error:
                        self.log(f"Could not snapshot {node['name']}/{endpoint}: {error}")
        for node in self.nodes:
            node["process"].terminate()
        for node in self.nodes:
            try:
                node["process"].wait(timeout=10)
            except subprocess.TimeoutExpired:
                node["process"].kill()
                node["process"].wait()
            self.log(f"Stopped node {node['name']}")
        for reservation in self.reservations:
            reservation.close()
        self.udp.close()
        if self.args.coverage and self.nodes:
            try:
                with self.phase("8. Combine node coverage and generate reports"):
                    self.coverage_report()
            except Exception as error:
                self.log(f"Coverage generation failed: {error}; see coverage.log")
                failed = True
        suite = ET.Element("testsuite", name="fdswarm.swarmIntegration", tests=str(len(self.results)),
                           failures=str(sum(error is not None for _, _, error in self.results)),
                           errors="0", skipped="0", time=f"{sum(seconds for _, seconds, _ in self.results):.3f}")
        for name, seconds, error in self.results:
            case = ET.SubElement(suite, "testcase", classname="fdswarm.swarmIntegration", name=name, time=f"{seconds:.3f}")
            if error:
                ET.SubElement(case, "failure", message="Integration phase failed").text = error
        xml = self.directory / "test-report.xml"
        ET.ElementTree(suite).write(xml, encoding="utf-8", xml_declaration=True)
        subprocess.run([sys.executable, str(ROOT / "scripts/junit-to-html.py"), str(xml)], check=True)
        self.log(f"{'FAILED' if failed else 'PASSED'} — artifacts and HTML report: {self.directory}")
        self.log_file.close()
        if self.args.open:
            reports = [str(self.directory / "test-report.html")]
            coverage = self.directory / "coverage/index.html"
            if coverage.exists():
                reports.append(str(coverage))
            subprocess.run(["open", *reports], check=True)
        return failed


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--coverage", action="store_true", help="Build an instrumented JAR and combine coverage from all nodes")
    parser.add_argument("--open", action="store_true", help="Open generated HTML reports in the default browser (macOS)")
    parser.add_argument("--skip-build", action="store_true", help="Reuse the existing assembly for a repeat run")
    parser.add_argument("--jar", type=Path, help="Existing JAR (requires --skip-build)")
    parser.add_argument("--java", default="java", help="Liberica JDK 21 Full java executable")
    parser.add_argument("--contest", choices=("ARRL", "WFD"), default="ARRL")
    parser.add_argument("--broadcast-address", help="LAN broadcast address; detected from macOS interfaces by default")
    parser.add_argument("--timeout", type=float, default=60, help="Deadline per convergence check in seconds")
    args = parser.parse_args()
    if args.coverage and (args.skip_build or args.jar):
        parser.error("--coverage requires a fresh instrumented build; it cannot be combined with --skip-build or --jar")
    if args.open and sys.platform != "darwin":
        parser.error("--open currently supports macOS only")
    if args.timeout <= 12:
        parser.error("--timeout must exceed the 12-second stability check")
    run = SwarmTest(args)
    run.log(f"Artifacts: {run.directory}; tail -f {run.directory / 'progress.log'}")
    failed = True
    try:
        run.scenario()
        failed = False
    except (Exception, KeyboardInterrupt) as error:
        run.log(f"Integration run failed: {error}")
    finally:
        failed = run.finish(failed)
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
