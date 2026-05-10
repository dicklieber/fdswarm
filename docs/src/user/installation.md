# Installation

FdSwarm can be downloaded from https://github.com/dicklieber/fdlog_full/releases

## Packages

| Package               | Description                                                 |
|-----------------------|-------------------------------------------------------------|
| xxx.windows-x64.msi   | For X64 (Intel)                                             |
| xxx.windows-arm64.msi | For ARM processors like Parallels on a modern Mac           |
| xxx.pkg               | For macOS                                                   |
| xxx.all.jar           | For Linux or any platform, you must install Java seperatrly |

The Windows (MSI) and Mac (PKG) packages are self-contained installers that include all necessary dependencies for running FDswarm on their respective operating systems. 
The xxx.all.jar package is a standalone JAR file that requires Java to be installed separately.

## Java Installation
If bit using the MSI or PKG packages, you will need to have Java installed on your system. 
FDswarm requires java 21 or higher, with JavaFx support. The best source for Java with JavaFx is the https://bell-sw.com/pages/downloads/#jdk-21-lts. 
Be sure to select the appropriate version for your operating system and architecture and _Full JDK_.
Other sources may work as long as JavaFx is supported.

# Running FDswarm
if you have installed the MSI or PKG package there should be 
an Icon in your start menu (Windows) or Applications folder on (Mac).

To start on Linux or when using the xxx.all.jar package, you will need to have Java installed and run the command

```java -jar xxx.all.jar```