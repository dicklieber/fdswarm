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

To create a new release with downloadable artifacts (JAR, Windows MSI, macOS PKG):
1. Make sure the GitHub CLI is installed and authenticated with `gh auth login`.
2. Run `./scripts/release.sh`.
3. Accept the suggested release tag or enter another tag with three numeric version components, such as `v1.0.0`.
4. The script updates `main` from GitHub, including the latest `README.md` badge changes, creates the tag on GitHub, and starts the tag-based release workflow.
5. The GitHub Action will automatically build the project, run tests, and create a GitHub Release with the artifacts.

To skip the prompt for the version number, pass the tag explicitly:
```bash
./scripts/release.sh v1.0.0
```

Release tags provide the public app version (`X.Y.Z`). GitHub Actions provides the build number from the workflow run number. Installer metadata uses a three-part installer-safe version for MSI/PKG compatibility, while artifact filenames include the build number, for example `FdSwarm-1.0.0-build123-windows-x64.msi`.

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
