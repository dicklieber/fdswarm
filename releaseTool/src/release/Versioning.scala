package release

case class ReleaseVersion(
    snapshotVersion: String,
    baseVersion: String,
    buildNumber: Int,
    releaseVersion: String,
    tagName: String
)

object Versioning {

  private val versionFile =
    os.pwd / "version.txt"

  private val buildNumberFile =
    os.pwd / "buildnumber.txt"

  def currentVersion(): String = {
    if !os.exists(versionFile) then
      sys.error("missing version.txt")

    os.read(versionFile).trim
  }

  def currentBuildNumber(): Int = {
    if !os.exists(buildNumberFile) then {
      os.write.over(buildNumberFile, "0\n")
      0
    } else {
      val text =
        os.read(buildNumberFile).trim

      if text.isEmpty then 0
      else
        text.toIntOption.getOrElse(
          sys.error(s"buildnumber.txt must contain an integer, found: $text")
        )
    }
  }

  def prepareReleaseVersion(): ReleaseVersion = {

    val snapshot =
      currentVersion()

    if !snapshot.endsWith("-SNAPSHOT") then
      sys.error(s"version.txt must end with -SNAPSHOT, found: $snapshot")

    val base =
      snapshot.stripSuffix("-SNAPSHOT")

    val nextBuild =
      currentBuildNumber() + 1

    val release =
      s"$base-$nextBuild"

    ReleaseVersion(
      snapshotVersion = snapshot,
      baseVersion = base,
      buildNumber = nextBuild,
      releaseVersion = release,
      tagName = s"v$release"
    )
  }

  def writePreparedRelease(rv: ReleaseVersion): Unit = {

    os.write.over(versionFile, s"${rv.releaseVersion}\n")
    os.write.over(buildNumberFile, s"${rv.buildNumber}\n")

    println(s"[version] ${rv.snapshotVersion} -> ${rv.releaseVersion}")
    println(s"[buildnumber] ${rv.buildNumber}")
    println(s"[tag] ${rv.tagName}")
  }

  def abortRelease(): Unit = {

    val version =
      currentVersion()

    if version.endsWith("-SNAPSHOT") then {
      println(s"[ok] already snapshot: $version")
      return
    }

    val snapshot =
      version.replaceFirst("-[0-9]+$", "-SNAPSHOT")

    if snapshot == version then
      sys.error(s"cannot infer snapshot version from: $version")

    os.write.over(versionFile, s"$snapshot\n")

    println(s"[version] $version -> $snapshot")
    println("[note] buildnumber.txt was not decremented")
  }

  def finishRelease(nextSnapshot: Option[String]): Unit = {

    val version =
      currentVersion()

    if version.endsWith("-SNAPSHOT") then
      sys.error(s"version.txt is already a snapshot: $version")

    val next =
      nextSnapshot.getOrElse(defaultNextSnapshot(version))

    if !next.endsWith("-SNAPSHOT") then
      sys.error(s"next snapshot must end with -SNAPSHOT, found: $next")

    os.write.over(versionFile, s"$next\n")

    println(s"[version] $version -> $next")
  }

  private def defaultNextSnapshot(releaseVersion: String): String = {

    val base =
      releaseVersion.replaceFirst("-[0-9]+$", "")

    val parts =
      base.split("\\.").toList

    parts match
      case major :: minor :: patch :: Nil =>
        val nextPatch =
          patch.toIntOption.getOrElse(
            sys.error(s"cannot parse patch version from: $base")
          ) + 1

        s"$major.$minor.$nextPatch-SNAPSHOT"

      case _ =>
        sys.error(s"cannot infer next snapshot from: $releaseVersion")
  }

}
