# Installation

Download FdSwarm from [GitHub Releases](https://github.com/dicklieber/fdswarm/releases).

## Packages

| Package | Description |
|---------|-------------|
| `FdSwarm-<version>-windows-x64-setup.exe` | Windows 10/11 on Intel or AMD x64; also usable on Windows 11 ARM through x64 emulation |
| `.pkg` | macOS installer |
| `.zip` | Portable application bundle with a platform-specific Java runtime |
| `fdswarm.jar` | Application JAR; install Java separately |

## Windows installation

Run the Windows setup EXE. It installs FdSwarm for your Windows account without
requiring administrator privileges or a separate Java installation. The installer
includes Java 21 and JavaFX, creates a Start menu shortcut, and offers an optional
desktop shortcut. The installer is currently unsigned, so Windows may display an
unknown-publisher or SmartScreen prompt.

Run FdSwarm from the Start menu. Install a later setup EXE to update it. To uninstall,
use Windows Settings. Your contest logs and configuration in
`%LOCALAPPDATA%\FdSwarm` are retained. If you previously installed FdSwarm with an
MSI, uninstall that older application separately before using the new installer.

The x64 installer does not support Windows 10 ARM or 32-bit Windows. Native ARM64
ZIP bundles remain available as a backup where provided, but Windows ARM64 JavaFX
has limitations affecting the map display. Prefer the x64 installer on Windows 11 ARM.

## Java installation for the JAR

The Windows installer, macOS PKG, and runtime ZIP bundles include Java. To run
`fdswarm.jar` directly, install [Liberica JDK 21 Full](https://bell-sw.com/pages/downloads/#jdk-21-lts)
for your operating system and architecture. Select **Full JDK**, which includes JavaFX.

```sh
java -jar fdswarm.jar
```

## Network access

Allow FdSwarm's bundled Java through Windows Firewall on your private contest LAN
when prompted so nodes can communicate. No Internet connection is required for
normal logging or synchronization.
