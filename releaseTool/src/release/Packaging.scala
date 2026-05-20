package release

object Packaging {

  private val releaseDir =
    os.pwd / "release"

  private val jdksDir =
    releaseDir / "jdks"

  private val stagingDir =
    releaseDir / "staging"

  private val artifactsDir =
    releaseDir / "artifacts"

  def buildZips(): Unit = {

    Jdks.checkJdks()

    os.remove.all(stagingDir)
    os.makeDir.all(stagingDir)
    os.makeDir.all(artifactsDir)

    val assemblyJar =
      findAssemblyJar()

    val version =
      Versioning.currentVersion()

    println(s"[assembly] $assemblyJar")
    println(s"[version] $version")

    Platforms.all.foreach { platform =>
      buildPlatformZip(platform, version, assemblyJar)
    }

    println()
    println(s"Artifacts written to: $artifactsDir")
  }

  private def findAssemblyJar(): os.Path = {

    val assemblyDir =
      os.pwd / "out" / "fdswarm" / "assembly.dest"

    if !os.exists(assemblyDir) then
      sys.error(s"assembly output directory does not exist: $assemblyDir")

    val jars =
      os.walk(assemblyDir)
        .filter(p => os.isFile(p) && p.last.endsWith(".jar"))
        .toSeq

    if jars.isEmpty then
      sys.error(s"no jar found under $assemblyDir")

    jars.maxBy(p => os.size(p))
  }

  private def buildPlatformZip(
      platform: Platform,
      version: String,
      assemblyJar: os.Path
  ): Unit = {

    val name =
      s"fdswarm-$version-${platform.id}"

    val appDir =
      stagingDir / name

    val runtimeDir =
      jdksDir / platform.id / "runtime"

    val binDir =
      appDir / "bin"

    val appLibDir =
      appDir / "app" / "lib"

    os.remove.all(appDir)
    os.makeDir.all(binDir)
    os.makeDir.all(appLibDir)

    os.copy.over(
      from = assemblyJar,
      to = appLibDir / "fdswarm.jar",
      createFolders = true
    )

    os.copy.over(
      from = runtimeDir,
      to = appDir / "runtime",
      createFolders = true
    )

    writeLauncher(platform, binDir)

    val zipFile =
      artifactsDir / s"$name.zip"

    os.remove.all(zipFile)

    Process.run(
      Seq(
        "zip",
        "-qr",
        zipFile.toString,
        name
      ),
      cwd = stagingDir
    )

    println(s"[zip] $zipFile")
  }

  private def writeLauncher(
      platform: Platform,
      binDir: os.Path
  ): Unit = {

    if platform.isWindows then
      os.write.over(
        binDir / "fdswarm.bat",
        windowsLauncher
      )
    else {
      val launcher =
        binDir / "fdswarm"

      os.write.over(launcher, unixLauncher)

      Process.run(Seq("chmod", "+x", launcher.toString))
    }
  }

  private val unixLauncher =
    """#!/usr/bin/env sh
      |set -eu
      |
      |APP_HOME="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
      |exec "$APP_HOME/runtime/bin/java" -jar "$APP_HOME/app/lib/fdswarm.jar" "$@"
      |""".stripMargin

  private val windowsLauncher =
    """@echo off
      |setlocal
      |set APP_HOME=%~dp0..
      |"%APP_HOME%\runtime\bin\java.exe" -jar "%APP_HOME%\app\lib\fdswarm.jar" %*
      |""".stripMargin

}
