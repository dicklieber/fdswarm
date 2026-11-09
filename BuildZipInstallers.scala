//| scalaVersion: 3.10.0
//| extends: mill.script.ScalaModule.Raw
//| mvnDeps: [com.lihaoyi::os-lib:0.11.6]

import java.util.jar.JarFile
import scala.util.Using

/** Build the five portable distributions from the same release JAR. */
object BuildZipInstallers:
  val platforms = Seq("windows-x64", "windows-arm64", "macos-aarch64", "linux-x64", "linux-aarch64")

  def version(jar: os.Path): String =
    Using.resource(new JarFile(jar.toIO)) { file =>
      val value = Option(file.getManifest).flatMap(m => Option(m.getMainAttributes.getValue("Implementation-Version")))
        .getOrElse(throw new IllegalArgumentException(s"Missing Implementation-Version: $jar"))
      require(value.matches(raw"\d+\.\d+\.\d+(?:-\d+)?"), s"Expected a release JAR, found version: $value")
      for entry <- Seq("fdswarm/FdSwarm.class", "FDSwarmDocs/index.html") do
        require(file.getEntry(entry) != null, s"JAR is missing $entry: $jar")
      value
    }

  def runtimes(root: os.Path): Seq[(String, os.Path)] =
    platforms.map { platform =>
      val directory = root / platform
      require(os.isDir(directory), s"Missing runtime: $directory. Run scripts/fetch-liberica-runtimes.sh first.")
      val executable = if platform.startsWith("windows-") then "java.exe" else "java"
      val homes = os.walk(directory).filter(p => os.isFile(p) && p.last == executable && (p / os.up).last == "bin")
        .map(_ / os.up / os.up)
      require(homes.size == 1, s"Expected one bin/$executable under $directory; found ${homes.size}")
      platform -> homes.head
    }

  def build(jar: os.Path, runtimeRoot: os.Path, output: os.Path, repo: os.Path = os.pwd): Seq[os.Path] =
    val releaseVersion = version(jar)
    val images = runtimes(runtimeRoot) // Fail before producing any ZIPs if a runtime is missing.
    os.makeDir.all(output)
    images.map { (platform, runtime) =>
      println(s"Building ZIP: $platform ($releaseVersion)")
      val work = os.temp.dir(prefix = "fdswarm-zip-")
      try
        val app = work / "FdSwarm"
        os.makeDir.all(app / "bin")
        os.copy(jar, app / "lib" / "fdswarm.jar", createFolders = true)
        os.copy(runtime, app / "runtime")
        Seq(repo / "application.conf", repo / "fdswarm" / "resources" / "application.conf")
          .find(os.isFile(_)).foreach(conf => os.copy(conf, app / "conf" / "application.conf", createFolders = true))
        writeLaunchers(app, platform)
        val zip = output / s"FdSwarm-$releaseVersion-$platform.zip"
        val stagedZip = work / zip.last
        os.proc("zip", "-qr", stagedZip.toString, "FdSwarm").call(cwd = work)
        os.copy.over(stagedZip, zip)
        println(s"Built $zip")
        zip
      finally os.remove.all(work)
    }

  private def writeLaunchers(app: os.Path, platform: String): Unit =
    if platform.startsWith("windows-") then
      os.write(app / "bin" / "fdswarm.bat", """@echo off
setlocal
set APP_HOME=%~dp0..
"%APP_HOME%\runtime\bin\java.exe" -jar "%APP_HOME%\lib\fdswarm.jar" %*
""")
      os.write(app / "bin" / "install-start-menu-shortcut.bat", """@echo off
setlocal
set "APP_HOME=%~dp0.."
set "LAUNCHER=%~dp0fdswarm.bat"
set "START_MENU=%APPDATA%\Microsoft\Windows\Start Menu\Programs"
set "SHORTCUT=%START_MENU%\FdSwarm.lnk"
if not exist "%START_MENU%" mkdir "%START_MENU%"
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$shell = New-Object -ComObject WScript.Shell; $shortcut = $shell.CreateShortcut($env:SHORTCUT); $shortcut.TargetPath = $env:LAUNCHER; $shortcut.WorkingDirectory = $env:APP_HOME; $shortcut.Description = 'FdSwarm'; $shortcut.Save()"
if errorlevel 1 (
  echo Failed to create Start Menu shortcut.
  exit /b 1
)
echo Created Start Menu shortcut: %SHORTCUT%
""")
    else
      os.write(app / "bin" / "fdswarm", unixLauncher(platform))
      os.perms.set(app / "bin" / "fdswarm", "rwxr-xr-x")

  private def unixLauncher(platform: String): String =
    """#!/usr/bin/env sh
set -eu
APP_HOME="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
EXPECTED_PLATFORM="@PLATFORM@"
case "$(uname -s)" in
  Darwin) ACTUAL_OS="macos" ;;
  Linux) ACTUAL_OS="linux" ;;
  *) ACTUAL_OS="$(uname -s)" ;;
esac
case "$(uname -m)" in
  x86_64|amd64) ACTUAL_ARCH="x64" ;;
  arm64|aarch64) ACTUAL_ARCH="aarch64" ;;
  *) ACTUAL_ARCH="$(uname -m)" ;;
esac
ACTUAL_PLATFORM="$ACTUAL_OS-$ACTUAL_ARCH"
if [ "$EXPECTED_PLATFORM" != "$ACTUAL_PLATFORM" ]; then
  echo "This FdSwarm package is for $EXPECTED_PLATFORM, but this system is $ACTUAL_PLATFORM." >&2
  exit 126
fi
exec "$APP_HOME/runtime/bin/java" -jar "$APP_HOME/lib/fdswarm.jar" "$@"
""".replace("@PLATFORM@", platform)

  def main(args: Array[String]): Unit =
    val usage = "Usage: ./mill --no-server BuildZipInstallers.scala JAR [RUNTIMES_DIR] [OUTPUT_DIR]"
    if args.toSeq == Seq("--help") then println(usage)
    else
      require(args.length >= 1 && args.length <= 3, usage)
      build(os.Path(args(0), os.pwd),
        os.Path(args.lift(1).getOrElse(sys.env.getOrElse("FDSWARM_RUNTIMES_DIR", "fdswarm-runtimes")), os.pwd),
        os.Path(args.lift(2).getOrElse("release"), os.pwd))
