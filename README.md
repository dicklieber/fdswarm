# FdSwarm

![Tests](https://img.shields.io/badge/tests-passed-brightgreen)
![Coverage](https://img.shields.io/badge/coverage-11.7%25-red)
![License](https://img.shields.io/badge/license-GPL--3.0-blue)

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
`release/artifacts`.

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
release/artifacts/FdSwarm-<version>-windows-x64.msi
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
release/inno-installers/artifacts/FdSwarm-<version>-windows-x64-setup.exe
release/inno-installers/artifacts/FdSwarm-<version>-windows-arm64-setup.exe
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
