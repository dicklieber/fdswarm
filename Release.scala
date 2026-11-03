//| scalaVersion: 3.10.0
//| extends: mill.script.ScalaModule.Raw
//| mvnDeps: [com.lihaoyi::os-lib:0.11.6]

import java.util.jar.JarFile
import scala.util.Using

/** Run from the repository root: ./mill --no-server Release.scala */
object Release {
  def main(args: Array[String]): Unit =
    if args.toSeq == Seq("--help") then
      println("Usage: ./mill --no-server Release.scala\nInteractively build and publish fdswarm.jar to GitHub.")
      return
    require(args.isEmpty, "No arguments expected (or use --help).")

    val root = os.pwd
    def check(ok: Boolean, message: => String): Unit =
      if !ok then throw new IllegalStateException(message)
    def capture(command: String*): os.CommandResult =
      os.proc(command).call(cwd = root, check = false, stderr = os.Pipe)
    def run(command: String*): Unit =
      os.proc(command).call(cwd = root, stdin = os.Inherit, stdout = os.Inherit, stderr = os.Inherit)
      ()
    def confirm(label: String): Boolean =
      val answer = scala.io.StdIn.readLine(s"$label [y/N]: ")
      answer != null && Set("y", "yes", "ok").contains(answer.trim.toLowerCase(java.util.Locale.ROOT))

    val versionFile = root / "version.txt"
    val buildNumberFile = root / "build.number"
    check(os.isFile(versionFile) && os.isFile(root / "build.mill"),
      "Run from the fdswarm repository root.")
    val status = capture("git", "status", "--porcelain")
    check(status.exitCode == 0 && status.out.text().isEmpty,
      s"Git working tree must be clean before releasing.\n${status.out.text()}")
    check(capture("gh", "auth", "status").exitCode == 0,
      "GitHub CLI must be installed and authenticated. Run: gh auth login")

    val originalVersion = os.read(versionFile)
    val snapshotVersion = originalVersion.trim
    check(snapshotVersion.matches(raw"\d+\.\d+\.\d+-SNAPSHOT"),
      s"version.txt must contain X.Y.Z-SNAPSHOT; found '$snapshotVersion'.")
    val originalBuildNumber = Option.when(os.isFile(buildNumberFile))(os.read(buildNumberFile))
    val buildNumber = originalBuildNumber.map(_.trim).filter(_.nonEmpty).getOrElse("0")
    check(buildNumber.matches("[0-9]+"), s"build.number must contain an integer; found '$buildNumber'.")
    val nextBuildNumber = BigInt(buildNumber) + 1
    val releaseVersion = s"${snapshotVersion.stripSuffix("-SNAPSHOT")}-$nextBuildNumber"
    val tag = s"v$releaseVersion"
    val artifact = root / "release" / "fdswarm.jar"

    println(s"Snapshot version: $snapshotVersion\nRelease version : $releaseVersion\nTag             : $tag\nArtifact        : $artifact")
    println("Build steps     : clean, fdswarm.assembly (isolated temporary Mill output directory)")
    if !confirm("Build and publish this release") then
      println("Release cancelled.")
      return

    val buildOutput = os.temp.dir(prefix = "fdswarm-release-")
    var committed = false
    var releaseCommit = ""
    def mill(target: String): Unit =
      os.proc("./mill", "--no-server", target).call(
        cwd = root, env = Map("MILL_OUTPUT_DIR" -> buildOutput.toString),
        stdin = os.Inherit, stdout = os.Inherit, stderr = os.Inherit)
      ()

    try
      os.write.over(buildNumberFile, s"$nextBuildNumber\n")
      os.write.over(versionFile, s"$releaseVersion\n")
      mill("clean")
      mill("fdswarm.assembly")
      val assemblyDirectory = buildOutput / "fdswarm" / "assembly.dest"
      val namedJar = assemblyDirectory / "fdswarm.jar"
      val jarPath =
        if os.isFile(namedJar) then namedJar
        else
          check(os.isDir(assemblyDirectory), s"Assembly directory not found: $assemblyDirectory")
          val jars = os.list(assemblyDirectory).filter(path => os.isFile(path) && path.ext == "jar")
          check(jars.size == 1, s"Expected one assembly JAR in $assemblyDirectory; found ${jars.size}.")
          jars.head

      Using.resource(new JarFile(jarPath.toIO)) { jar =>
        val manifest = jar.getManifest
        check(manifest != null, "Assembly has no manifest.")
        val actualVersion = manifest.getMainAttributes.getValue("Implementation-Version")
        check(actualVersion == releaseVersion,
          s"Assembly version is '$actualVersion', expected '$releaseVersion'.")
      }
      run("./scripts/ci/verify-docs-in-jar.sh", jarPath.toString)
      os.copy.over(jarPath, artifact, createFolders = true)

      // Commit the incremented build number while retaining the development version.
      os.write.over(versionFile, originalVersion)
      run("git", "add", "version.txt", "build.number")
      run("git", "commit", "-m", s"Release build $nextBuildNumber")
      committed = true
      val head = capture("git", "rev-parse", "HEAD")
      check(head.exitCode == 0, "Cannot determine the release commit.")
      releaseCommit = head.out.text().trim

      if confirm("Push release commit") then run("git", "push")
      else println("Push skipped. The release target commit must already be available on GitHub.")

      if capture("gh", "release", "view", tag).exitCode != 0 then
        run("gh", "release", "create", tag, "--target", releaseCommit, "--title", tag, "--generate-notes")
      run("gh", "release", "upload", tag, artifact.toString, "--clobber")
      println(s"Published $artifact to GitHub release $tag")
      // Dispatch only after the JAR is available; a release-created event can race its upload.
      run("gh", "workflow", "run", "windows-installer.yml", "--ref", tag, "-f", s"tag=$tag")
      println(s"Windows installer build queued for $tag. Monitor with: gh run list --workflow windows-installer.yml")
    catch
      case scala.util.control.NonFatal(error) =>
        if committed then
          System.err.println(s"Release commit $releaseCommit was retained. Check GitHub release $tag before retrying publication. JAR: $artifact")
        throw error
    finally
      try
        os.write.over(versionFile, originalVersion)
        if !committed then
          originalBuildNumber match
            case Some(content) => os.write.over(buildNumberFile, content)
            case None => os.remove(buildNumberFile)
      finally os.remove.all(buildOutput)
}
