"""Harness checks that do not launch JavaFX or use the network."""

import importlib.util
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import Mock

SPEC = importlib.util.spec_from_file_location("swarm_integration", Path(__file__).resolve().parents[1] / "swarm-integration.py")
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class SwarmHarnessTest(unittest.TestCase):
    def test_coverage_uses_a_fresh_run_directory(self):
        first = object.__new__(MODULE.SwarmTest)
        second = object.__new__(MODULE.SwarmTest)
        first.args = second.args = Mock(coverage=True)
        first.directory = Path("/private/tmp/first-run")
        second.directory = Path("/private/tmp/second-run")
        self.assertNotEqual(first.build_dir, second.build_dir)
        self.assertEqual(first.build_dir.parent, first.directory)

    def test_coverage_rejects_reusing_a_jar_before_launching_any_nodes(self):
        result = subprocess.run([sys.executable, str(Path(MODULE.__file__)), "--coverage", "--skip-build"],
                                capture_output=True, text=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("requires a fresh instrumented build", result.stderr)

    def test_thread_measurements_do_not_count_as_separate_jvms(self):
        with tempfile.TemporaryDirectory() as directory:
            run = object.__new__(MODULE.SwarmTest)
            run.args = Mock(coverage=True)
            run.directory = Path(directory)
            run.nodes = [{"name": "A"}, {"name": "B"}]
            data = run.build_dir / "fdswarm/scoverage/data.dest"
            data.mkdir(parents=True)
            for thread in (1, 2, 3):
                (data / f"scoverage.measurements.01234567-89ab-cdef-0123-456789abcdef.{thread}").write_text("1\n")
            with self.assertRaisesRegex(AssertionError, "2 JVMs, found 1"):
                run.coverage_report()

    def setUp(self):
        self.run = object.__new__(MODULE.SwarmTest)
        self.run.nodes = [{"name": "A"}, {"name": "B"}]
        self.config = {"fingerPrint": "same", "club": "expected"}
        self.qso = {"uuid": "one", "callsign": "W1AW", "stamp": "2026-10-25T20:00:00Z"}
        self.snapshots = {name: {"qsos": [dict(self.qso)], "statusMessage": {"contestStart": "start"}}
                          for name in ("A", "B")}
        self.configs = {name: dict(self.config) for name in ("A", "B")}
        self.run.request = lambda node, path: (self.configs[node["name"]] if path == "contestConfig"
                                               else self.snapshots[node["name"]])

    def test_convergence_requires_complete_records_and_configuration(self):
        expected = {"one": self.qso}
        self.assertTrue(self.run.converged(self.config, expected, "start"))
        self.snapshots["B"]["qsos"][0]["stamp"] = "wrong"
        self.assertFalse(self.run.converged(self.config, expected, "start"))
        self.snapshots["B"]["qsos"] = [dict(self.qso)]
        self.configs["B"]["club"] = "wrong even though fingerprint matches"
        self.assertFalse(self.run.converged(self.config, expected, "start"))

    def test_duplicate_records_and_stale_contest_start_do_not_pass(self):
        self.snapshots["B"]["qsos"].append(dict(self.qso))
        self.assertFalse(self.run.converged(self.config, {"one": self.qso}, "start"))
        self.snapshots["B"]["qsos"] = [dict(self.qso)]
        self.snapshots["B"]["statusMessage"]["contestStart"] = "old start"
        self.assertFalse(self.run.converged(self.config, {"one": self.qso}, "start"))

    def test_exited_node_fails_immediately_instead_of_waiting_for_timeout(self):
        process = Mock()
        process.poll.return_value = 7
        self.run.nodes = [{"name": "A", "process": process}]
        self.run.args = Mock(timeout=60)
        condition = Mock()
        with self.assertRaisesRegex(RuntimeError, "Node A exited with code 7"):
            self.run.wait_for("test", condition)
        condition.assert_not_called()
