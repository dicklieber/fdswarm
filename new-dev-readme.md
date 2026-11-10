# New Developer Setup

FdSwarm is a Scala 3 desktop application for distributed amateur radio contest
logging. Its UI uses ScalaFX/JavaFX, and nodes synchronize QSOs over a local LAN
using UDP broadcast. Start by reading [AGENTS.md](AGENTS.md) for project conventions
and [README.md](README.md) for additional build and release commands.

## Required tools

| Tool or capability | What you need |
| --- | --- |
| Git | Clone the repository, review changes, and manage commits. Configure your name and email before committing. |
| BellSoft Liberica JDK 21 **Full** | Install the Full JDK for your operating system and CPU architecture. It includes the JavaFX modules this project expects. A standard JDK or JRE alone is insufficient. |
| Mill | Use the repository's `./mill` launcher. It obtains the version specified in `.mill-version`; a separate global Mill installation is unnecessary. |
| IntelliJ IDEA Ultimate with the Scala plugin | The project's established IDE. Configure it to use the same Liberica JDK as the terminal. Command-line development is also possible. |
| Shell and basic command-line tools | The checked-in launcher and helper scripts use a Unix-style shell. Bash is needed for `.sh` helpers; `curl`, `tar`, and `unzip` are needed for runtime downloads, and `zip` for portable package builds. |
| Graphical desktop session | Required to run the application and JavaFX UI tests. The multi-node integration harness opens several application windows. |
| Internet access during setup | Needed to download Mill and Maven dependencies. Runtime package downloads and publishing also need Internet access. Normal contest logging and LAN synchronization do not. |

Official downloads:

- [Liberica JDK](https://bell-sw.com/pages/downloads/#jdk-21-lts): select Java 21, your OS/architecture, and **Full JDK**.
- [IntelliJ IDEA](https://www.jetbrains.com/idea/): install and enable the Scala plugin.
- [Git](https://git-scm.com/downloads).

On macOS and Linux, the shell commands below can be run directly in a terminal.
On Windows, use an environment with Bash and the required Unix utilities, such as
Git Bash, and select a Windows Liberica JDK. Native Windows installer packaging
uses PowerShell 7 instead. The existing release process builds macOS DMGs locally
on a Mac and builds the Windows installer through GitHub Actions.

### Use the versions in the repository

Scala and library versions are defined in [build.mill](build.mill) and
[common-settings.mill](common-settings.mill). Scala is currently 3.10.0. Mill
resolves the compiler and dependencies; installing Scala, sbt, or Scala CLI
separately is not required. Do not use sbt for this project or upgrade dependencies
as part of machine setup.

There is currently a documentation mismatch: `AGENTS.md` describes Mill 2.0, while
[.mill-version](.mill-version) pins 1.3.1. Use the checked-in launcher and pin, and
coordinate any version change with the maintainer rather than substituting a
system-installed Mill.

[.mill-jvm-version](.mill-jvm-version) selects `system`, so the Java environment on
your machine matters. All Java tools used for builds must come from Liberica
JDK 21 Full.

## Clone and configure Java

```sh
git clone https://github.com/dicklieber/fdswarm.git
cd fdswarm
```

If you plan to push changes, configure GitHub authentication using either HTTPS
credentials or an SSH key, and obtain repository write access or use a fork.
Read-only cloning of the public repository does not require release permissions.

Set `JAVA_HOME` to your actual Liberica JDK installation and put its tools first
on `PATH`. For example, in Bash or zsh:

```sh
export JAVA_HOME="/path/to/liberica-jdk-21-full"
export PATH="$JAVA_HOME/bin:$PATH"
```

On macOS, a typical installation path is
`/Library/Java/JavaVirtualMachines/liberica-jdk-21-full.jdk/Contents/Home`.
Use the path that actually exists on your machine. Set `JAVA_HOME` through Windows
environment settings when using native Windows tools.

Verify the selected tools:

```sh
java -version
javac -version
java --list-modules
./java-check.sh
```

Confirm Java 21 and JavaFX modules including `javafx.controls`, `javafx.fxml`,
`javafx.swing`, and `javafx.web`. If JavaFX is absent, check that you installed the
**Full** JDK and that another Java installation is not taking precedence on `PATH`.
The JDK also supplies `jar`, `jlink`, and `jpackage`; these need no separate install.

## IntelliJ setup

1. Open the repository root in IntelliJ IDEA Ultimate.
2. Install or enable the Scala plugin.
3. Set the project SDK and the build/import JVM to Liberica JDK 21 Full.
4. Import the Mill build using its BSP support. If IntelliJ does not discover the
   build, follow the BSP setup instructions for the repository's pinned Mill version.
5. Let dependency resolution and indexing finish before treating unresolved imports
   as source errors.

Keep the IDE and terminal on the same working tree and JDK. Do not import this as
an sbt project or create a second checkout just for the IDE.

## First build and run

Run commands from the repository root. Prefer `--no-server` and a separate output
directory so terminal builds do not interfere with IntelliJ's build state:

```sh
export MILL_OUTPUT_DIR="${TMPDIR:-/tmp}/fdswarm-dev-mill"
./mill --no-server fdswarm.compile
./mill --no-server fdswarm.run
```

The first build downloads dependencies and may take substantially longer than later
builds. Documentation is generated and bundled with the application by the build;
there is no separate Node/npm or Python documentation toolchain to install.

To build a runnable JAR:

```sh
./mill --no-server fdswarm.assembly
java -jar "$MILL_OUTPUT_DIR/fdswarm/assembly.dest/fdswarm.jar"
```

For IDE run configurations, the application main class is `fdswarm.FdSwarm`.
Use the repository root as the working directory.

## Tests and integration prerequisites

MUnit and Scoverage are resolved by Mill. No separate test runner or coverage tool
installation is needed.

```sh
./mill --no-server common.test
./mill --no-server fdswarm.test
./mill --no-server manager.test
./mill --no-server scripts.test
```

Keep a graphical desktop session available for the application tests. To run just
one suite, use, for example:

```sh
./mill --no-server fdswarm.test.testOnly fdswarm.fx.NextFieldTest
```

Release automation also has standalone Mill test scripts:

```sh
./mill --no-server ReleaseTest.scala + ZipInstallersTest.scala + PublishWindowsInstallerTest.scala
```

For full application replication tests:

```sh
./mill --no-server scripts.runMain runSwarmIntegration
```

This launches four application nodes with isolated test data and produces retained
logs and reports. It requires working local UDP broadcast and a desktop session.
The harness uses its own application build directory; keep that separate from the
outer process's `MILL_OUTPUT_DIR` to avoid nested build locks. See the
[README integration instructions](README.md#multi-node-integration-test) for details.

## Network and optional radio tools

- Allow Java/FdSwarm LAN communication in the operating system firewall. Normal
  defaults are UDP port **8900** for replication and TCP port **8080** for the HTTP
  API; see [reference.conf](common/resources/reference.conf).
- For multi-machine testing, use a LAN that permits UDP broadcasts between nodes.
  Guest Wi-Fi isolation, VPN routing, or firewall rules can prevent discovery.
- A database server, broker, or Internet coordination service is not required.
- **Hamlib / `rigctld`** is needed only to develop or exercise live radio integration.
  It can run locally or on a reachable host; the default rig-control TCP port is
  **4532**. A physical radio is not required for ordinary UI and logging work.
- **WSJT-X** is optional and needed only for its integration. Its UDP integration
  is disabled by default.

See [radio integration](docs/src/user/rigctl/index.md) and
[startup configuration](docs/src/user/startupConfig.md) for related behavior.

## Extra prerequisites for packaging and publishing

These are separate from everyday source development. Running `Release.scala`
increments the build number, commits and pushes the release commit, and publishes
assets to GitHub; it is not a build-only smoke test.

| Task | Additional prerequisites |
| --- | --- |
| Publish any release | GitHub CLI (`gh`), an authenticated account with repository release/write access, a configured Git identity, and a clean working tree. Git push authentication must also work. |
| Publish Windows installer through Actions | Permission to dispatch and inspect the Windows workflow. The workflow must exist in the release ref. A local Windows machine is not required. |
| Build Windows installer locally | Windows, PowerShell 7, Inno Setup 6.3 or later, and the Java tooling described above. See `scripts/ci/package-windows.ps1`. |
| Build a macOS DMG | macOS and Liberica JDK 21 Full with `jpackage` on `PATH`. The output targets the build Mac's architecture. |
| Sign and notarize a DMG | Apple Developer Program membership, a valid **Developer ID Application** certificate with its matching private key in Keychain, and Apple developer tools providing `xcrun notarytool` and `xcrun stapler`. |
| Build portable ZIP installers | `zip`, disk space for five JDK/runtime images plus staging/output ZIPs, and the platform runtimes listed below. Fetching them requires `curl`, `tar`, and `unzip`. |

Authenticate GitHub CLI once using `gh auth login`. Notarization credentials are
stored once per Mac/Keychain using your own Apple Developer account and Team ID:

```sh
xcrun notarytool store-credentials "fdswarm-notary" \
  --apple-id "YOUR_APPLE_ACCOUNT_EMAIL" \
  --team-id "YOUR_TEAM_ID"
```

Enter an Apple **app-specific password** at the terminal prompt. Keep private keys
and passwords out of source control. The release command reuses the saved
`fdswarm-notary` profile; it does not need a new password for each release.
Set `FDSWARM_NOTARY_PROFILE` to use a different profile, or
`JPACKAGE_MAC_SIGNING_KEY_USER_NAME` to choose among multiple signing identities.
macOS may require an initial Keychain approval for `codesign` to use the private key.

`Release.scala` asks at startup whether to publish the macOS DMG, Windows installer,
and ZIP installers. The JAR is always published. Disabling DMG publishing removes
the requirement for macOS signing and notarization credentials.

The ZIP platforms are Windows x64, Windows ARM64, macOS ARM64, Linux x64, and Linux
ARM64. Runtimes live under `fdswarm-runtimes/`, or the directory specified by
`FDSWARM_RUNTIMES_DIR`. If ZIP publishing is selected and runtimes are missing,
`Release.scala` prompts before fetching the large downloads. It reuses existing
images and fetches only missing platforms. Manual preparation is optional:

```sh
./scripts/fetch-liberica-runtimes.sh --missing-only
```

For a build-only ZIP check using an existing release JAR:

```sh
./mill --no-server BuildZipInstallers.scala release/fdswarm.jar
```

See [README.md](README.md) for signing details, retrying an existing release's
Windows/ZIP packaging, and retained artifact locations.

## Repository guide and working conventions

| Path | Purpose |
| --- | --- |
| `common/src`, `common/test/src` | Shared domain models, configuration, utilities, and tests. |
| `fdswarm/src`, `fdswarm/test/src` | Desktop app, replication, scoring, UI, and application tests. |
| `manager/src`, `manager/test/src` | Manager application and tests. |
| `scripts/src`, `scripts/test/src` | Scala automation helpers and their MUnit tests. |
| Root `*.scala` scripts | Standalone Mill release, packaging, and automation test entry points. |
| `docs/src` | User/developer documentation and diagrams. |
| `build.mill`, `common-settings.mill`, `packaging.mill`, `docs.mill` | Build, dependency, documentation, and packaging configuration. |

Use straightforward Scala 3, preserve JavaFX thread requirements, and keep changes
focused. Prefer Scala Mill scripts for automation and script tests; do not introduce
Python. Keep runtime downloads, generated packages, credentials, and personal IDE
configuration out of commits. Read `AGENTS.md` before using a coding assistant or
changing build configuration, dependencies, or the distributed architecture.
