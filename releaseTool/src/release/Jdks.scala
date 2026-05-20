package release

object Jdks {

  private val releaseDir =
    os.pwd / "release"

  private val jdksDir =
    releaseDir / "jdks"

  def fetchJdks(): Unit = {

    os.makeDir.all(jdksDir)

    Platforms.all.foreach(fetchJdk)
  }

  def checkJdks(): Unit = {

    Platforms.all.foreach { platform =>

      val javaExe =
        if platform.isWindows
        then jdksDir / platform.id / "runtime" / "bin" / "java.exe"
        else jdksDir / platform.id / "runtime" / "bin" / "java"

      if !os.exists(javaExe) then
        sys.error(s"missing java for ${platform.id}: $javaExe")

      println(s"[ok] ${platform.id}")
    }
  }

  private def fetchJdk(platform: Platform): Unit = {

    val platformDir =
      jdksDir / platform.id

    val runtimeDir =
      platformDir / "runtime"

    if os.exists(runtimeDir) then {
      println(s"[exists] ${platform.id}")
      return
    }

    os.remove.all(platformDir)
    os.makeDir.all(platformDir)

    val archiveName =
      platform.libericaUrl.split("/").last

    val archiveFile =
      platformDir / archiveName

    println(s"[download] ${platform.id}")

    Process.run(
      Seq(
        "curl",
        "-fL",
        platform.libericaUrl,
        "-o",
        archiveFile.toString
      )
    )

    if archiveName.endsWith(".zip") then
      Process.run(
        Seq(
          "unzip",
          "-q",
          archiveFile.toString,
          "-d",
          platformDir.toString
        )
      )
    else
      Process.run(
        Seq(
          "tar",
          "xf",
          archiveFile.toString,
          "-C",
          platformDir.toString
        )
      )

    val extracted =
      os.list(platformDir)
        .find(p => os.isDir(p) && p.last != "runtime")
        .getOrElse(
          sys.error(s"unable to locate extracted dir for ${platform.id}")
        )

    os.move(extracted, runtimeDir)

    println(s"[ok] ${platform.id}")
  }

}
