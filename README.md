# FdSwarm

![Build](https://github.com/dicklieber/fdswarm/actions/workflows/ci.yaml/badge.svg)
![Tests](https://img.shields.io/badge/tests-passed-brightgreen)
![Coverage](https://img.shields.io/badge/coverage-11.7%25-red)
![Release](https://img.shields.io/github/v/release/dicklieber/fdlog_full)
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

To create a new release with downloadable artifacts (JAR, Windows MSI, macOS PKG, zip distributions):
1. Make sure the GitHub CLI is installed and authenticated with `gh auth login`.
2. Run `./scripts/release.sh`.
3. Accept the suggested release tag or enter another tag with three numeric version components, such as `v1.0.0`.
4. The script updates `main` from GitHub, including the latest `README.md` badge changes, creates the tag on GitHub, and starts the tag-based release workflow.
5. The GitHub Action will automatically build the project, run tests, and create a GitHub Release with the artifacts.

To skip the prompt for the version number, pass the tag explicitly:
```bash
./scripts/release.sh v1.0.0
```

Release tags provide the public app version (`X.Y.Z`). GitHub Actions provides the build number from the workflow run number. Installer metadata uses a three-part installer-safe version for MSI/PKG compatibility, while installer artifact filenames include the build number, for example `FdSwarm-1.0.0-build123-windows-x64.msi`. Zip distribution filenames use the public app version, for example `FdSwarm-1.0.0-linux-x64.zip`.

Artifacts are also available as GitHub Action run artifacts for every build on the `main` branch.

## Local CI Checks

The GitHub Actions workflow calls scripts under `scripts/ci` for the repeated release steps. Run those scripts locally when debugging packaging issues:

```bash
./scripts/ci/set-package-version.sh
./scripts/ci/name-release-jar.sh
FDSWARM_ASSEMBLY_JAR=out/fdswarm/assembly.dest/fdswarm.jar ./scripts/ci/package-macos.sh
```

On Windows, run:
```powershell
.\scripts\ci\set-package-version.ps1
$env:FDSWARM_ASSEMBLY_JAR = 'out/fdswarm/assembly.dest/fdswarm.jar'
.\scripts\ci\package-windows.ps1
```

For lightweight GitHub Actions checks, install `act` and run:
```bash
./scripts/act-ci.sh package-assembly
```

`act` is useful for Linux workflow checks and YAML wiring. Native macOS and Windows installer packaging still needs the matching operating system because `jpackage` produces platform-specific installers.

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

Windows ARM64 packaging requires WiX 3.14.1 on the PATH. The GitHub Actions workflow installs it with `scripts/ci/install-wix.ps1`.

## Zip Distribution Builds

Zip distributions are plain file-copy bundles. They do not use `jpackage`, MSI, DMG, PKG, WiX, or app-image.

Each zip contains:

```text
FdSwarm/
  bin/
    fdswarm
    fdswarm.bat
  lib/
    fdswarm-all.jar
  runtime/
    <bundled platform JDK>
  conf/
    application.conf if present
```

Use BellSoft Liberica JDK 21 Full archives from https://bell-sw.com/pages/downloads/#jdk-21-lts. Use the Full JDK because FdSwarm is a JavaFX app and the Full bundle includes LibericaFX.

### Local Zip Builds

Download and unpack these Liberica JDK 21 Full archives:

- Windows x64 ZIP
- macOS ARM64 TAR.GZ
- Linux x64 TAR.GZ

Then point the distribution tasks at the unpacked JDK directories:

```bash
export FDSWARM_RUNTIME_WINDOWS_X64=/path/to/unpacked/windows-jdk-full
export FDSWARM_RUNTIME_MACOS_AARCH64=/path/to/unpacked/macos-jdk-full.jdk/Contents/Home
export FDSWARM_RUNTIME_LINUX_X64=/path/to/unpacked/linux-jdk-full
```

Build all zip distributions:

```bash
./mill --no-daemon fdswarm.distAll
```

Or build one platform zip:

```bash
./mill --no-daemon fdswarm.distWindowsX64
./mill --no-daemon fdswarm.distMacosAarch64
./mill --no-daemon fdswarm.distLinuxX64
```

The zip files are written to:

```text
out/fdswarm/distWindowsX64.dest/FdSwarm-<version>-windows-x64.zip
out/fdswarm/distMacosAarch64.dest/FdSwarm-<version>-macos-aarch64.zip
out/fdswarm/distLinuxX64.dest/FdSwarm-<version>-linux-x64.zip
```

### GitHub Zip Builds

The `Zip Distributions` workflow runs on `workflow_dispatch` and tags matching `v*`. It builds `fdswarm.assembly` once, downloads the current BellSoft Liberica JDK 21 Full archives with the BellSoft discovery API, builds all three zip distributions, and uploads them as the `FdSwarm-Zip-Distributions` artifact.

To run it manually:

1. Open GitHub Actions.
2. Select `Zip Distributions`.
3. Choose `Run workflow`.
4. Optionally provide `release_version`, such as `1.0.0` or `v1.0.0`.

For tagged releases, push a `v*` tag. The workflow derives the app version from the tag through `scripts/ci/set-package-version.sh`.

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
