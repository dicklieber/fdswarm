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

Windows MSI packaging requires WiX 3.14.1 on the PATH.

For release MSIs, build the release JAR first so it exists at
`out/fdswarm/assembly.dest/fdswarm.jar`, and make sure the Windows x64 and
Windows ARM64 JDK runtimes exist under `fdswarm-runtimes`. Then run from
PowerShell on Windows:

```powershell
.\scripts\release-windows-msi.ps1
```

The release MSIs are written to:

```text
release/artifacts/FdSwarm-<version>-windows-x64.msi
release/artifacts/FdSwarm-<version>-windows-arm64.msi
```

To Authenticode-sign the MSI, pass a certificate thumbprint:

```powershell
.\scripts\release-windows-msi.ps1 -CertificateThumbprint '0123456789ABCDEF0123456789ABCDEF01234567'
```

To upload the MSI to the matching GitHub release:

```powershell
.\scripts\release-windows-msi.ps1 -Publish
```

## Zip Installer Publishing

Zip installers are plain file-copy bundles. They do not use `jpackage`, MSI,
DMG, PKG, WiX, Launch4j, or app-image.

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

The publisher consumes the `fdswarm.jar` asset from the latest GitHub release,
extracts the release version from its `Implementation-Version` manifest entry,
builds all zip installers from local runtimes under `fdswarm-runtimes`, and
uploads the zip files to the matching GitHub release.

```bash
./scripts/publish-zip-installers.sh
```

To publish from a specific source release instead of the latest release:

```bash
./scripts/publish-zip-installers.sh --tag v1.0.0-1
```

The script requires these runtime directories to exist:

```text
fdswarm-runtimes/windows-x64/
fdswarm-runtimes/windows-arm64/
fdswarm-runtimes/macos-aarch64/
fdswarm-runtimes/linux-x64/
fdswarm-runtimes/linux-aarch64/
```

Use BellSoft Liberica JDK 21 Full archives from
https://bell-sw.com/pages/downloads/#jdk-21-lts. Use the Full JDK because
FdSwarm is a JavaFX app and the Full bundle includes LibericaFX. The helper
below downloads and unpacks the runtimes into the expected layout:

```bash
./scripts/fetch-liberica-runtimes.sh
```

The local zip outputs are written to:

```text
release/zip-installers/artifacts/FdSwarm-<version>-windows-x64.zip
release/zip-installers/artifacts/FdSwarm-<version>-windows-arm64.zip
release/zip-installers/artifacts/FdSwarm-<version>-macos-aarch64.zip
release/zip-installers/artifacts/FdSwarm-<version>-linux-x64.zip
release/zip-installers/artifacts/FdSwarm-<version>-linux-aarch64.zip
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
