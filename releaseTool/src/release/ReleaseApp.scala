package release

object ReleaseApp {

  private val releaseDir =
    os.pwd / "release"

  private val jdksDir =
    releaseDir / "jdks"

  def main(args: Array[String]): Unit = {

    args.toList match
      case "fetch-jdks" :: Nil =>
        fetchJdks()

      case "check-jdks" :: Nil =>
        checkJdks()

      case "build-zips" :: Nil =>
        Packaging.buildZips()

      case "prepare-release" :: Nil =>
        prepareRelease()

      case "abort-release" :: Nil =>
        Versioning.abortRelease()

      case "finish-release" :: Nil =>
        Versioning.finishRelease(None)

      case "finish-release" :: nextSnapshot :: Nil =>
        Versioning.finishRelease(Some(nextSnapshot))

      case "publish-release" :: Nil =>
        Github.publishRelease()

      case _ =>
        usage()
  }

  private def usage(): Unit = {

    println(
      """
        |usage:
        |
        |  ./mill releaseTool.run fetch-jdks
        |  ./mill releaseTool.run check-jdks
        |  ./mill releaseTool.run build-zips
        |  ./mill releaseTool.run prepare-release
        |  ./mill releaseTool.run abort-release
        |  ./mill releaseTool.run finish-release
        |  ./mill releaseTool.run finish-release 0.0.1-SNAPSHOT
        |  ./mill releaseTool.run publish-release
        |
        |prepare-release:
        |  - requires clean git
        |  - requires version.txt ending with -SNAPSHOT
        |  - increments buildnumber.txt
        |  - writes release version to version.txt
        |  - does not commit or tag yet
        |
        |abort-release:
        |  - changes 0.0.0-1 back to 0.0.0-SNAPSHOT
        |  - does not decrement buildnumber.txt
        |
        |finish-release:
        |  - changes 0.0.0-1 to 0.0.1-SNAPSHOT by default
        |  - or uses the supplied next snapshot version
        |
        |publish-release:
        |  - requires gh CLI
        |  - creates GitHub release if missing
        |  - uploads release/artifacts/*.zip
        |""".stripMargin
    )
  }

  private def prepareRelease(): Unit = {

    Git.ensureClean()

    val rv =
      Versioning.prepareReleaseVersion()

    Versioning.writePreparedRelease(rv)

    println()
    println("Next commands:")
    println("  ./mill fdswarm.assembly")
    println("  ./mill releaseTool.run build-zips")
    println("  git add version.txt buildnumber.txt")
    println(s"""  git commit -m 'Release ${rv.releaseVersion}'""")
    println(s"  git tag ${rv.tagName}")
    println("  ./mill releaseTool.run publish-release")
    println("  ./mill releaseTool.run finish-release")
  }

  private def fetchJdks(): Unit = {

    os.makeDir.all(jdksDir)

    Platforms.all.foreach(fetchJdk)
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

    os.move(
      extracted,
      runtimeDir
    )

    println(s"[ok] ${platform.id}")
  }

  def checkJdks(): Unit = {

    Platforms.all.foreach { platform =>

      val javaExe =
        if platform.id.startsWith("windows")
        then
          jdksDir / platform.id / "runtime" / "bin" / "java.exe"
        else
          jdksDir / platform.id / "runtime" / "bin" / "java"

      if !os.exists(javaExe) then
        sys.error(s"missing java for ${platform.id}: $javaExe")

      println(s"[ok] ${platform.id}")
    }
  }

}
