package release

object ReleaseApp {

  def main(args: Array[String]): Unit = {

    args.toList match
      case "fetch-jdks" :: Nil =>
        Jdks.fetchJdks()

      case "check-jdks" :: Nil =>
        Jdks.checkJdks()

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
        |normal manual release:
        |
        |  ./mill releaseTool.run prepare-release
        |  ./mill fdswarm.assembly
        |  ./mill releaseTool.run build-zips
        |  git add version.txt buildnumber.txt
        |  git commit -m 'Release <version>'
        |  git tag v<version>
        |  git push
        |  git push --tags
        |  ./mill releaseTool.run publish-release
        |  ./mill releaseTool.run finish-release
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
    println("  git push")
    println("  git push --tags")
    println("  ./mill releaseTool.run publish-release")
    println("  ./mill releaseTool.run finish-release")
  }

}
