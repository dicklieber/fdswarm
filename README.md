# FDLog Swarm

![Build](https://github.com/dicklieber/fdlog_full/actions/workflows/ci.yaml/badge.svg)
![Tests](https://img.shields.io/badge/tests-passed-brightgreen)
![Coverage](https://img.shields.io/badge/coverage-12.2%25-red)
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
1. Push a tag to the repository matching the pattern `v*` (e.g., `v1.0.0`).
2. The GitHub Action will automatically build the project, run tests, and create a GitHub Release with the artifacts.

Artifacts are also available as GitHub Action run artifacts for every build on the `main` branch.

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
