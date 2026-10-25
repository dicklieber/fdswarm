# FdSwarm

![Tests](https://img.shields.io/badge/tests-passed-brightgreen)
![Coverage](https://img.shields.io/badge/coverage-11.7%25-red)
![License](https://img.shields.io/badge/license-GPL--3.0-blue)

FdSwarm is a free, open-source, and cross-platform tool for logging Ham Radio field day events. Handles both ARRL and Winter Field Day.
This project uses [Mill](https://mill-build.com/) as the build tool.

## macOS: Allowing the Downloaded JAR to Run

macOS may block `fdswarm.jar` because it was downloaded from the internet and
is not currently signed/notarized as a macOS application.

If macOS blocks it:

1. Open **System Settings**.
2. Go to **Privacy & Security**.
3. Scroll to **Security**.
4. Find the message about `fdswarm.jar` being blocked.
5. Click **Allow Anyway**.
6. Run FdSwarm again.

Advanced users can remove the quarantine flag from Terminal:

```sh
xattr -d com.apple.quarantine fdswarm.jar
java -jar fdswarm.jar
```

FdSwarm is currently distributed as a portable Java JAR. A signed and notarized
macOS installer is planned, but not yet available.

## Developing with Codex

FdSwarm uses OpenAI Codex as a coding assistant alongside IntelliJ IDEA Ultimate
and Mill. Codex handles code analysis, implementation, bug fixes, refactoring,
and writing tests. The developer handles builds, test execution, debugging,
reviewing changes, and Git commits.

### Start Codex

From the repository root, run:

```bash
codex --sandbox workspace-write --ask-for-approval never
```

Codex reads the repository's `AGENTS.md` for project conventions, architectural
constraints, and the division of responsibilities. Codex and IntelliJ edit the
same working tree, so changes appear in IntelliJ without synchronization.

### Review and validate changes

1. Review Codex's edits in IntelliJ.
2. Compile and run tests with the Mill commands below.
3. Make any needed corrections.
4. Commit the changes using IntelliJ's Git integration.

For a Codex review of uncommitted changes, run:

```bash
codex review --uncommitted
```

Codex does not run builds or tests, change dependencies, or perform Git writes
unless explicitly authorized, as specified in `AGENTS.md`.

## Build and Development Commands

### Cleaning
To clean the project build artifacts:
```bash
./mill clean
```
### Building
To compile all the modules:
```
./mill __.compile
```

### Running Tests

Run these commands from the repository root, using BellSoft Liberica JDK 21 Full
(which includes JavaFX). UI tests and the QSO export/import integration test need
an available desktop session.

Keep test output separate from IntelliJ's build output:

```sh
export MILL_OUTPUT_DIR=/private/tmp/fdswarm-tests
./mill --no-server fdswarm.test
```

The application suite includes `fdswarm.integration.QsoExportImportTest`. To run
only that test (generate 100 QSOs, export ADIF and Cabrillo, clear the temporary
store, import ADIF, and compare against saved JSON):

```sh
./mill --no-server fdswarm.test.testOnly fdswarm.integration.QsoExportImportTest
```

To run the shared-module tests as well:

```sh
./mill --no-server common.test
```

### Multi-node Integration Test

Run the real application swarm test separately from the MUnit suite. It requires
Python 3, BellSoft Liberica JDK 21 Full, and a desktop session for the four JavaFX
application windows. No additional Python packages or UI testing framework are required.

```sh
python3 scripts/swarm-integration.py
```

The script builds `fdswarm.jar` using `./mill --no-server fdswarm.assembly`, then:

1. Starts three nodes with fresh, separate application directories and instance IDs.
2. Configures a contest on one node and verifies the full configuration propagates.
3. Enters three QSOs on each node and checks that all nodes have identical records.
4. Starts an empty fourth node and checks it receives the configuration and all QSOs.
5. Starts a contest on one node and checks every node clears its QSOs and stays cleared.
6. Changes another node's contest configuration and checks full convergence.
7. Enters new QSOs on all four nodes and verifies replication still works.

The run prints progress and the location of its temporary artifact directory.
That directory contains `progress.log` (which can be followed with `tail -f`),
`build.log`, per-node startup settings and application logs, expected QSO JSON,
and `test-report.xml` / `test-report.html`. Failed runs also capture each node's
state through the replication APIs. Artifacts are retained for inspection;
the script stops only the processes it launched, including after Ctrl-C.

Build output defaults to `/private/tmp/fdswarm-swarm-build`; set `MILL_OUTPUT_DIR`
to choose another location. To repeat with the existing JAR, or test WFD:

```sh
python3 scripts/swarm-integration.py --skip-build
python3 scripts/swarm-integration.py --contest WFD
```

To collect coverage from the four application JVMs and open both HTML reports on macOS:

```sh
python3 scripts/swarm-integration.py --coverage --open
```

Coverage mode builds a separate instrumented JAR with
`fdswarm.integrationCoverageAssembly`. Each run uses a fresh `coverage-build`
directory inside its artifact directory, so its measurements are not mixed with
unit tests or earlier runs. All four nodes contribute to the combined report.
This mode requires a new build and cannot be combined with `--skip-build` or
`--jar`; the fresh compilation adds time compared with a cached normal run.

The artifact directory also contains `coverage/index.html`, `coverage.xml`, and
`coverage.log`. Coverage reports are generated after stopping the nodes, including
when a scenario fails after launching nodes. The scope matches existing application
coverage (`fdswarm` sources; the shared `common` module is not instrumented).
Omit `--open` to generate the reports without opening the browser. `--open` also
works without `--coverage` to open just the test results.

Use `--java /path/to/java` to select the Java executable, `--timeout 90` to increase
the deadline per convergence check, or `--skip-build --jar /absolute/path/fdswarm.jar`
to test an existing build. Open the retained HTML report with macOS `open`.

Before launching, the harness verifies UDP broadcast delivery. On macOS it detects
a working interface broadcast address; use `--broadcast-address 192.168.1.255`
(with your LAN’s address) to select one explicitly. Nodes use an isolated UDP port
and OS-assigned HTTP ports. The harness uses
`--startupInfo` with `StartupConfig` for each node's operator, band/mode, instance
ID, clear-QSO flag, discovery setting, and optional `integrationTest` settings.
Test controls are registered only when the node also receives `--integration-test`;
they require the matching per-run token. Normal app and manager launches retain
the normal data directories, UDP port, and API routes.

### Test Results Report

Application test runs automatically generate a JUnit XML report. Convert it to a
self-contained HTML report with Python 3 (standard library only). The report shows
counts, pass/fail results, skipped tests, execution times, full failure details,
and any output captured in the XML.

After running `fdswarm.test`, generate and open the HTML report on macOS:

```sh
python3 scripts/junit-to-html.py "$MILL_OUTPUT_DIR/fdswarm/test/testForked.dest/test-report.xml" &&
  open "$MILL_OUTPUT_DIR/fdswarm/test/testForked.dest/test-report.html"
```

After running `fdswarm.test.testOnly`, use:

```sh
python3 scripts/junit-to-html.py "$MILL_OUTPUT_DIR/fdswarm/test/testOnly.dest/test-report.xml" &&
  open "$MILL_OUTPUT_DIR/fdswarm/test/testOnly.dest/test-report.html"
```

These commands assume `MILL_OUTPUT_DIR` was exported as shown above. Otherwise,
replace `$MILL_OUTPUT_DIR` with `out`. The original XML remains available for tools
that support JUnit reports. Each HTML report is written beside its XML input.

### Test Coverage Reports

The application tests collect Scoverage data automatically. Run the full suite
before generating a report; running only one test gives partial coverage.

On macOS, run the tests, generate both HTML reports, and open both in the default
browser. Reports are generated even if tests fail; the final command retains the
test failure status:

```sh
(
  export MILL_OUTPUT_DIR=/private/tmp/fdswarm-tests
  test_status=0
  ./mill --no-server fdswarm.test || test_status=$?
  python3 scripts/junit-to-html.py "$MILL_OUTPUT_DIR/fdswarm/test/testForked.dest/test-report.xml" &&
    ./mill --no-server fdswarm.scoverage.htmlReport &&
    open "$MILL_OUTPUT_DIR/fdswarm/test/testForked.dest/test-report.html" \
         "$MILL_OUTPUT_DIR/fdswarm/scoverage/htmlReport.dest/index.html" &&
    test "$test_status" -eq 0
)
```

Use the same `MILL_OUTPUT_DIR` for testing and reporting. Without that environment
variable, the report is written to `out/fdswarm/scoverage/htmlReport.dest/index.html`.
The `open` command is macOS-specific.

Other coverage report formats:

- **XML Report**: `./mill --no-server fdswarm.scoverage.xmlReport`
- **Console Report**: `./mill --no-server fdswarm.scoverage.consoleReport`
- **Cobertura XML**: `./mill --no-server fdswarm.scoverage.xmlCoberturaReport`

## Releases and Artifacts
### Build & Release fdswarm.jar
fdswrm.jar is the primary artifact of the project. It contains the fdswarm application and the docs. It can be run with
```java -jar fdswarm.jar```
Running this way requires a Java JDK version 21 or higher, with JavaFX be installed.

While this can be

Build release artifacts locally with the package commands below. Set
`FDSWARM_VERSION` to a three-part version such as `1.0.0` before building
installers or zip distributions when producing a named release.

## Local Package Helpers

Scripts under `scripts/ci` contain repeated package steps that are useful when
debugging local packaging issues:

```bash
./scripts/ci/name-release-jar.sh
FDSWARM_ASSEMBLY_JAR=out/fdswarm/assembly.dest/fdswarm.jar ./scripts/ci/package-macos.sh
```

On Windows, run:
```powershell
$env:FDSWARM_ASSEMBLY_JAR = 'out/fdswarm/assembly.dest/fdswarm.jar'
.\scripts\ci\package-windows.ps1
```

## Local Package Builds

Build the assembly JAR first. This also builds and embeds the documentation site:

```bash
./mill --no-daemon fdswarm.assembly
./scripts/ci/verify-docs-in-jar.sh out/fdswarm/assembly.dest/fdswarm.jar
```

On macOS, build the PKG installer:

```bash
FDSWARM_ASSEMBLY_JAR=out/fdswarm/assembly.dest/fdswarm.jar ./scripts/ci/package-macos.sh
```

To publish the generated MSI files to the macOS web root, set
`FDSWARM_MSI_SSH_HOST` to a macOS SSH target, for example `user@mac-host`. The
script creates `/Library/WebServer/Documents/fdswarm` on the target and copies
the MSI files there with `scp`. Set `FDSWARM_MSI_DIR` to publish somewhere else,
or `FDSWARM_SKIP_MSI_PUBLISH=1` to leave the files only under
`out/release/artifacts`.

The PKG is written to:

```text
out/fdswarm/macPkg.dest/jpackage/
```

On Windows, build the MSI installer from PowerShell:

```powershell
.\mill.bat --no-daemon fdswarm.assembly
bash .\scripts\ci\verify-docs-in-jar.sh out/fdswarm/assembly.dest/fdswarm.jar
$env:FDSWARM_ASSEMBLY_JAR = 'out/fdswarm/assembly.dest/fdswarm.jar'
.\scripts\ci\package-windows.ps1
```

The MSI files are written to:

```text
out/release/artifacts/FdSwarm-<version>-windows-x64.msi
```

To publish Windows `.exe` installers with Inno Setup for both Windows x64 and
Windows ARM64, install Inno Setup 6 on the Windows build machine and run:

```powershell
.\scripts\publish-inno-installers.ps1
```

The Inno installers consume `fdswarm.jar` from the latest GitHub release,
extract `Implementation-Version` from its manifest, build from the local
Windows runtimes under `fdswarm-runtimes`, and upload to the matching
`v<version>` GitHub release. The installers are written to:

```text
out/release/inno-installers/artifacts/FdSwarm-<version>-windows-x64-setup.exe
out/release/inno-installers/artifacts/FdSwarm-<version>-windows-arm64-setup.exe
```

To Authenticode-sign the Windows installer artifacts, pass a certificate
thumbprint:

```powershell
.\scripts\publish-inno-installers.ps1 -CertificateThumbprint '0123456789ABCDEF0123456789ABCDEF01234567'
```

To download `fdswarm.jar` from a specific source release instead of the latest
release:

```powershell
.\scripts\publish-inno-installers.ps1 -Tag 'v1.2.3'
```

To build self-contained Windows Launch4j ZIP packages from an existing assembly
JAR and bundled Windows runtimes, run:

```powershell
$env:FDSWARM_ASSEMBLY_JAR = 'out/fdswarm/assembly.dest/fdswarm.jar'
.\scripts\build-launch4j-installers.ps1
```

The script uses `LAUNCH4J` or `LAUNCH4J_HOME` when set. Otherwise, it downloads
a local Launch4j copy under `out/fdswarm/launch4j.dest/tools`.

The Launch4j ZIP files are written to:

```text
out/release/artifacts/FdSwarm-<version>-windows-x64-launch4j.zip
out/release/artifacts/FdSwarm-<version>-windows-arm64-launch4j.zip
```

The generated Launch4j ZIP files are also copied to:

```text
/Library/WebServer/Documents/fdswarm/launch4j
```

Set `FDSWARM_LAUNCH4J_DIR` to publish them somewhere else.

## Zip Distribution Builds

Zip distributions are plain file-copy bundles. They do not use `jpackage`, MSI, DMG, PKG, WiX, or app-image.

Each zip contains:

```text
FdSwarm/
  bin/
    fdswarm
    fdswarm.bat
    install-start-menu-shortcut.bat on Windows
  lib/
    fdswarm-all.jar
  runtime/
    <bundled platform JDK>
  conf/
    application.conf if present
```

On Windows, run `bin\install-start-menu-shortcut.bat` from the extracted `FdSwarm`
folder to add a per-user `FdSwarm` shortcut to the Windows Start Menu.

Use BellSoft Liberica JDK 21 Full archives from https://bell-sw.com/pages/downloads/#jdk-21-lts. Use the Full JDK because FdSwarm is a JavaFX app and the Full bundle includes LibericaFX.

### Local Zip Builds

Build all zip distributions:

```bash
MILL_OUTPUT_DIR=/private/tmp/fdswarm-mill-out ./mill --no-server fdswarm.distAll
```

The Mill zip tasks download missing Liberica JDK 21 Full runtimes under
`fdswarm-runtimes/`:

- Windows x64 ZIP
- Windows ARM64 ZIP
- macOS ARM64 TAR.GZ
- Linux x64 TAR.GZ
- Linux ARM64 TAR.GZ

You can still set variables manually to override the downloaded runtime paths:

```bash
export FDSWARM_RUNTIME_WINDOWS_X64=/path/to/unpacked/windows-jdk-full
export FDSWARM_RUNTIME_WINDOWS_ARM64=/path/to/unpacked/windows-arm64-jdk-full
export FDSWARM_RUNTIME_MACOS_AARCH64=/path/to/unpacked/macos-jdk-full.jdk/Contents/Home
export FDSWARM_RUNTIME_LINUX_X64=/path/to/unpacked/linux-jdk-full
export FDSWARM_RUNTIME_LINUX_AARCH64=/path/to/unpacked/linux-arm64-jdk-full
```

To avoid building every zip, run one platform task:

```bash
MILL_OUTPUT_DIR=/private/tmp/fdswarm-mill-out ./mill --no-server fdswarm.distWindowsX64
MILL_OUTPUT_DIR=/private/tmp/fdswarm-mill-out ./mill --no-server fdswarm.distWindowsArm64
MILL_OUTPUT_DIR=/private/tmp/fdswarm-mill-out ./mill --no-server fdswarm.distMacosAarch64
MILL_OUTPUT_DIR=/private/tmp/fdswarm-mill-out ./mill --no-server fdswarm.distLinuxX64
MILL_OUTPUT_DIR=/private/tmp/fdswarm-mill-out ./mill --no-server fdswarm.distLinuxAarch64
```

The zip files are written to:

```text
out/fdswarm/distWindowsX64.dest/FdSwarm-<version>-windows-x64.zip
out/fdswarm/distWindowsArm64.dest/FdSwarm-<version>-windows-arm64.zip
out/fdswarm/distMacosAarch64.dest/FdSwarm-<version>-macos-aarch64.zip
out/fdswarm/distLinuxX64.dest/FdSwarm-<version>-linux-x64.zip
out/fdswarm/distLinuxAarch64.dest/FdSwarm-<version>-linux-aarch64.zip
```

## Using the manager
A manager is available to manage a bunch of instances of fdswarm, on a single host.
```
./run-manager.sh 
```
Running `manager.run` keeps that Mill process alive. Using a dedicated `MILL_OUTPUT_DIR` prevents it from blocking other commands (for example `./mill fdswarm.compile`) in another terminal.

## Logging
For information on how to configure and change log levels, see [docs/logging.md](docsOLD/logging.md).

## Documentation

Documentation sources live in `docs/src` and are rendered with Laika.

```bash
./mill docs.site
```

The generated site is available at `out/docs/site.dest/site/index.html`.
When `fdswarm.jar` is built after `./mill docs.site`, the generated site is
packaged into the jar and served from the Help > FDSwarmDocs menu item.
