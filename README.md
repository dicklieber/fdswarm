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
BellSoft Liberica JDK 21 Full and a desktop session for the four JavaFX application
windows. The scripts run through the repository's Mill launcher and use the
project's Scala, Circe, and MUnit versions. Keep Liberica JDK 21 Full selected.
No UI testing framework is required.

```sh
./mill --no-server scripts.runMain runSwarmIntegration
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

Application build output defaults to `/private/tmp/fdswarm-swarm-build`; set
`FDSWARM_SWARM_BUILD_DIR` to choose another location. Keep this separate from the
outer Mill process's `MILL_OUTPUT_DIR` so nested builds do not wait on its lock.
To repeat with the existing JAR, or test WFD:

```sh
./mill --no-server scripts.runMain runSwarmIntegration --skip-build
./mill --no-server scripts.runMain runSwarmIntegration --contest WFD
```

To collect coverage from the four application JVMs and open both HTML reports on macOS:

```sh
./mill --no-server scripts.runMain runSwarmIntegration --coverage --open
```

Coverage mode builds a separate instrumented JAR with
`fdswarm.integrationCoverageAssembly`. Each run uses a fresh `coverage-build`
directory inside its artifact directory, so its measurements are not mixed with
unit tests or earlier runs. All four nodes contribute to the combined report.
The nodes execute a copy at the persistent path
`/private/tmp/fdswarm-swarm-coverage/fdswarm-coverage.jar`, allowing a persistent
Little Snitch path rule. A filesystem lock protects that copy until cleanup
finishes; an overlapping coverage run fails with a retry message rather than
replacing a running JAR. Cleanup releases the lock but retains the JAR and lock
file. Per-run builds, measurements, node directories, and reports remain separate.
This mode requires a new build and cannot be combined with `--skip-build` or
`--jar`; the fresh compilation adds time compared with a cached normal run.

The artifact directory also contains `coverage/index.html`, `coverage.xml`, and
`coverage-html.log` and `coverage-xml.log`. Coverage reports are generated after stopping the nodes, including
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

Run the script/report MUnit tests without launching application nodes:

```sh
./mill --no-server scripts.test
```

Script sources live in `scripts/src` and their MUnit tests in `scripts/test/src`.
Set `MILL_OUTPUT_DIR` to place Mill output outside the repository, as for other
project commands.

### Test Results Report

Application test runs automatically generate a JUnit XML report. Convert it to a
self-contained HTML report with the Mill scripts module. The report shows
counts, pass/fail results, skipped tests, execution times, full failure details,
and any output captured in the XML.

After running `fdswarm.test`, generate and open the HTML report on macOS:

```sh
./mill --no-server scripts.runMain junitToHtml "$MILL_OUTPUT_DIR/fdswarm/test/testForked.dest/test-report.xml" &&
  open "$MILL_OUTPUT_DIR/fdswarm/test/testForked.dest/test-report.html"
```

After running `fdswarm.test.testOnly`, use:

```sh
./mill --no-server scripts.runMain junitToHtml "$MILL_OUTPUT_DIR/fdswarm/test/testOnly.dest/test-report.xml" &&
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
  ./mill --no-server scripts.runMain junitToHtml "$MILL_OUTPUT_DIR/fdswarm/test/testForked.dest/test-report.xml" &&
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

To build and publish a build-number release interactively, run from the repository root:

```sh
./mill --no-server Release.scala
```

At startup, the script asks three questions, each defaulting to yes:

- Publish macOS DMG?
- Publish Windows installer?
- Publish ZIP installers?

The JAR is always built and published, including when all three choices are no.
The script requires a clean Git working tree and an authenticated GitHub CLI (`gh`).
It increments `build.number`, builds the release in an isolated temporary Mill output
directory, verifies the JAR version and documentation, and saves `release/fdswarm.jar`.
It restores the snapshot version, commits the build number, pushes the commit, and
publishes the selected artifacts without further confirmation prompts.

DMG publishing requires macOS, Liberica JDK 21 Full on `PATH`, and signing/notarization
credentials. It produces `release/FdSwarm-<version>-macos-<architecture>.dmg` and a
SHA-256 checksum. When DMG publishing is disabled, those credentials are not required.

ZIP publishing builds all five platforms from that same JAR using local runtimes
under `fdswarm-runtimes/` (or `FDSWARM_RUNTIMES_DIR`). If any are missing, the release
script prompts before fetching these large downloads. Existing runtimes are reused;
only missing platforms are fetched. Declining cancels the release before the build
number changes. No fetch prompt appears when all runtimes are present or ZIP
publishing is disabled. ZIPs are retained in `release/`.

Windows publishing dispatches the workflow from the release tag, prints status,
elapsed time, a run link, and the current job/step every ten seconds, and verifies
the installer and checksum are attached. The publisher is called directly from the
release script, avoiding another Mill startup while waiting for Windows.
A failed run prints its failed-step logs and fails the release command; already
uploaded artifacts remain available. The GitHub CLI login needs permission to dispatch Actions.
The release script does not run tests;
run the existing test suites before releasing. If publication fails after the
commit, the commit and local release artifacts are retained for manual recovery.

Use `./mill --no-server Release.scala --help` for usage.

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

## Local Package Builds

Build the assembly JAR first. This also builds and embeds the documentation site:

```bash
./mill --no-daemon fdswarm.assembly
./scripts/ci/verify-docs-in-jar.sh out/fdswarm/assembly.dest/fdswarm.jar
```

On macOS, build the DMG:

```bash
FDSWARM_ASSEMBLY_JAR=out/fdswarm/assembly.dest/fdswarm.jar ./scripts/ci/package-macos.sh
```

The DMG is written to:

```text
out/fdswarm/macDmg.dest/jpackage/
```

DMG publishing requires a valid Developer ID Application certificate and private key in
Keychain. The release command selects the only valid Developer ID Application
identity, or uses `JPACKAGE_MAC_SIGNING_KEY_USER_NAME` when explicitly configured.
Store notarization credentials once with `xcrun notarytool store-credentials
fdswarm-notary`. Set `FDSWARM_NOTARY_PROFILE` to use another Keychain profile.

The release command signs the app and DMG, submits the DMG to Apple, waits for
acceptance, staples and validates the ticket, and checks Gatekeeper before
calculating the checksum or publishing. Credentials remain in Keychain.
Notarization responses and failure logs are retained alongside the DMG in `release/`.
Standalone `macDmg` builds remain unsigned unless `JPACKAGE_MAC_SIGN` and
`JPACKAGE_MAC_SIGNING_KEY_USER_NAME` are set. To notarize a signed DMG manually:

```sh
./scripts/ci/notarize-macos.sh path/to/FdSwarm.dmg fdswarm-notary
```

## Windows Installer

`Release.scala` queues `.github/workflows/windows-installer.yml` after uploading
`fdswarm.jar`. The action downloads that exact release asset, verifies its manifest
version against the requested tag, and packages it without rebuilding Scala sources.
It uses Inno Setup on `windows-2022` and bundles the latest Liberica JDK 21 Full
for Windows x64, including JavaFX WebView. Java does not need to be installed on
the user's computer, and normal operation requires no Internet connection.

The installer supports Windows 10/11 x64. It also allows Windows 11 ARM64 to use
Windows' x64 emulation; native ARM64 installers are not currently built. Windows
10 ARM64 is not supported by this x64 installer. Windows 10 and ARM64 compatibility
still require manual testing on those operating systems.

Installation is per user, under `%LOCALAPPDATA%\Programs\FdSwarm`, without
administrator privileges. It creates a Start menu shortcut and an optional desktop
shortcut. Upgrades retain the same application identity. User data under
`%LOCALAPPDATA%\FdSwarm` is preserved during installation and uninstallation.
Existing installations made by the former MSI installer should be uninstalled
separately; Inno Setup does not migrate Windows Installer registrations.

The action checks silent installation, reinstallation, bundled Java/JavaFX,
shortcut targets, uninstallation, and preservation of user data before uploading:

```text
FdSwarm-<version>-windows-x64-setup.exe
FdSwarm-<version>-windows-x64-setup.exe.sha256
```

The installer is currently unsigned. Windows may display an unknown-publisher or
SmartScreen prompt. These checks do not replace a manual application/UI test.

To retry packaging an already published release (without rerunning `Release.scala`
and incrementing the build number):

```sh
./mill --no-server PublishWindowsInstaller.scala v0.0.4-30
```

Run the offline publication checks with:

```sh
./mill --no-server PublishWindowsInstallerTest.scala
```

Packaging scripts come from the selected workflow branch/commit; the application
JAR always comes from the requested release. This allows packaging fixes to be
applied to an existing release without rebuilding its JAR.

The workflow can also be started from the GitHub Actions **Run workflow** button.
It attaches the installer and SHA-256 checksum to the same release and keeps
installer verification logs as workflow artifacts. A failed Windows build leaves
the published JAR available; retry packaging after fixing the failure.

For local packaging on Windows, install PowerShell 7 and Inno Setup 6.3 or later,
then run from the repository root:

```powershell
pwsh -File ./scripts/ci/package-windows.ps1 -AssemblyJar release/fdswarm.jar
```

The script downloads and verifies a Windows x64 Liberica runtime. To use an existing
Java 21 Full runtime instead, pass `-RuntimeDirectory C:\path\to\jdk`. Set `ISCC`
or pass `-Iscc` if `ISCC.exe` is outside its usual installation directory.
Installers are written to `out/release/artifacts`.

## Zip Distribution Builds

Zip distributions are plain file-copy bundles. They do not use `jpackage`, MSI, DMG, PKG, WiX, or app-image.

`BuildZipInstallers.scala` and `PublishZipInstallers.scala` replace the former
`scripts/release-zips.sh` and `scripts/publish-zip-installers.sh`. The builder has no
GitHub dependency; the publisher reuses it and never creates a different release.
Both require all five local runtime images. To build without uploading:

```sh
./mill --no-server BuildZipInstallers.scala release/fdswarm.jar
```

To publish ZIPs for an existing release, downloading its JAR first:

```sh
./mill --no-server PublishZipInstallers.scala v0.0.4-32
```

Supply a local JAR as the second argument to skip downloading it. Both scripts accept
runtime and output directory arguments; see `--help`. The JAR version must match
the target tag. ZIP outputs are retained for inspection or retry, and unrelated
artifacts are preserved. Run the offline packaging and release-selection checks with:

```sh
./mill --no-server ZipInstallersTest.scala + ReleaseTest.scala
```

Each zip contains:

```text
FdSwarm/
  bin/
    fdswarm
    fdswarm.bat
    install-start-menu-shortcut.bat on Windows
  lib/
    fdswarm.jar
  runtime/
    <bundled platform JDK>
  conf/
    application.conf if present
```

On Windows, run `bin\install-start-menu-shortcut.bat` from the extracted `FdSwarm`
folder to add a per-user `FdSwarm` shortcut to the Windows Start Menu.

Use BellSoft Liberica JDK 21 Full archives from https://bell-sw.com/pages/downloads/#jdk-21-lts. Use the Full JDK because FdSwarm is a JavaFX app and the Full bundle includes LibericaFX.

### Existing Mill Distribution Tasks

The existing `fdswarm.distAll` tasks remain available; these use `fdswarm-all.jar`
in their bundles and can fetch missing runtimes automatically.

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
