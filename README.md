# FdSwarm

![Tests](https://img.shields.io/badge/tests-passed-brightgreen)
![Coverage](https://img.shields.io/badge/coverage-11.7%25-red)
![License](https://img.shields.io/badge/license-GPL--3.0-blue)

This project uses [Mill](https://mill-build.com/) as the build tool.

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
To run the project tests:
```bash
./mill fdswarm.test.testLocal
```

### Test Coverage Reports
This project uses Scoverage for code coverage.

To generate an HTML coverage report:
```bash
./mill fdswarm.scoverage.htmlReport
```
The report will be available at: `out/fdswarm/scoverage/htmlReport.dest/index.html`

## Releases and Artifacts

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

The MSI is written to:

```text
out/fdswarm/winMsi.dest/
```

Windows ARM64 packaging requires WiX 3.14.1 on the PATH.

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
release/artifacts/FdSwarm-<version>-windows-x64-launch4j.zip
release/artifacts/FdSwarm-<version>-windows-arm64-launch4j.zip
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
`.fdswarm-runtimes/`:

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

Other coverage report formats:
- **XML Report**: `./mill fdswarm.scoverage.xmlReport`
- **Console Report**: `./mill fdswarm.scoverage.consoleReport`
- **Cobertura XML**: `./mill fdswarm.scoverage.xmlCoberturaReport`

## Documentation

Documentation sources live in `docs/src` and are rendered with Laika.

```bash
./mill docs.site
```

The generated site is available at `out/docs/site.dest/site/index.html`.
When `fdswarm.jar` is built after `./mill docs.site`, the generated site is
packaged into the jar and served from the Help > FDSwarmDocs menu item.
