//| scalaVersion: 3.10.0
//| extends: mill.script.ScalaModule.Raw
//| mvnDeps: [com.lihaoyi::os-lib:0.11.6]
//| moduleDeps: [PublishWindowsInstaller.scala, PublishZipInstallers.scala]

import java.util.jar.JarFile
import scala.util.Using

/** Run from the repository root: ./mill --no-server Release.scala */
object Release {
  case class Publishing(mac: Boolean, windows: Boolean, zips: Boolean)

  def publishing(read: String => String): Publishing =
    def choose(label: String): Boolean =
      val answer = read(s"$label [Y/n]: ")
      require(answer != null, "Release input closed; nothing published.")
      answer.trim.toLowerCase(java.util.Locale.ROOT) match
        case "" | "y" | "yes" | "ok" => true
        case "n" | "no" => false
        case _ =>
          println("Enter yes or no.")
          choose(label)
    Publishing(choose("Publish macOS DMG"), choose("Publish Windows installer"), choose("Publish ZIP installers"))

  def main(args: Array[String]): Unit =
    if args.toSeq == Seq("--help") then
      println("Usage: ./mill --no-server Release.scala\nChoose macOS DMG, Windows installer, and ZIP publishing at startup. The JAR is always published. DMG publishing requires macOS.")
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

    val selected = publishing(label => scala.io.StdIn.readLine(label))

    val versionFile = root / "version.txt"
    val buildNumberFile = root / "build.number"
    check(os.isFile(versionFile) && os.isFile(root / "build.mill"),
      "Run from the fdswarm repository root.")
    val status = capture("git", "status", "--porcelain")
    check(status.exitCode == 0 && status.out.text().isEmpty,
      s"Git working tree must be clean before releasing.\n${status.out.text()}")
    check(capture("gh", "auth", "status").exitCode == 0,
      "GitHub CLI must be installed and authenticated. Run: gh auth login")

    check(!selected.mac || sys.props("os.name").toLowerCase(java.util.Locale.ROOT).contains("mac"),
      "Releases must run on macOS to build the DMG alongside the JAR.")

    val signingIdentity = if !selected.mac then "" else
      val identities = capture("security", "find-identity", "-v", "-p", "codesigning")
        .out.text().linesIterator.flatMap { line =>
          val start = line.indexOf("\"Developer ID Application:")
          Option.when(start >= 0)(line.substring(start + 1).takeWhile(_ != '"'))
        }.toVector
      sys.env.get("JPACKAGE_MAC_SIGNING_KEY_USER_NAME")
        .filter(_.nonEmpty).getOrElse {
          check(identities.size == 1, "Expected one Developer ID Application identity; set JPACKAGE_MAC_SIGNING_KEY_USER_NAME to select one.")
          identities.head.stripPrefix("Developer ID Application: ")
        }
    val notaryProfile = sys.env.getOrElse("FDSWARM_NOTARY_PROFILE", "fdswarm-notary")
    if selected.mac then run("xcrun", "notarytool", "history", "--keychain-profile", notaryProfile)
    val runtimeRoot = os.Path(sys.env.getOrElse("FDSWARM_RUNTIMES_DIR", "fdswarm-runtimes"), root)
    if selected.zips then BuildZipInstallers.runtimes(runtimeRoot)

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

    val macArch = if !selected.mac then "" else sys.props("os.arch") match
      case "aarch64" | "arm64" => "aarch64"
      case "x86_64" | "amd64" => "x64"
      case other => throw new IllegalStateException(s"Unsupported macOS architecture: $other")
    val dmgName = s"FdSwarm-$releaseVersion-macos-$macArch.dmg"
    val dmgArtifact = artifact / os.up / dmgName
    val dmgChecksum = artifact / os.up / s"$dmgName.sha256"

    println(s"Snapshot version: $snapshotVersion\nRelease version : $releaseVersion\nTag             : $tag\nArtifact        : $artifact")
    println(s"Publishing      : JAR; macOS DMG=${selected.mac}; Windows installer=${selected.windows}; ZIPs=${selected.zips}")
    println("Building in an isolated temporary Mill output directory.")

    val buildOutput = os.temp.dir(prefix = "fdswarm-release-")
    var committed = false
    var releaseCommit = ""
    def mill(target: String): Unit =
      os.proc("./mill", "--no-server", target).call(
        cwd = root, env = Map("MILL_OUTPUT_DIR" -> buildOutput.toString) ++
          (if target == "fdswarm.macDmg" then Map(
            "FDSWARM_ASSEMBLY_JAR" -> artifact.toString,
            "JPACKAGE_MAC_SIGN" -> "true",
            "JPACKAGE_MAC_SIGNING_KEY_USER_NAME" -> signingIdentity
          ) else Map.empty[String, String]),
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

      if selected.mac then
        mill("fdswarm.macDmg")
        val dmgPath = buildOutput / "fdswarm" / "macDmg.dest" / "jpackage" / dmgName
        check(os.isFile(dmgPath), s"DMG not found: $dmgPath")
        run("hdiutil", "verify", dmgPath.toString)
        os.copy.over(dmgPath, dmgArtifact)
        run("./scripts/ci/notarize-macos.sh", dmgArtifact.toString, notaryProfile,
          if signingIdentity.startsWith("Developer ID Application: ") then signingIdentity
          else s"Developer ID Application: $signingIdentity")
        val hash = capture("shasum", "-a", "256", dmgArtifact.toString)
        check(hash.exitCode == 0, "Cannot calculate DMG checksum.")
        os.write.over(dmgChecksum, s"${hash.out.text().takeWhile(!_.isWhitespace)}  $dmgName\n")

      val zipArtifacts = if selected.zips then
        BuildZipInstallers.build(artifact, runtimeRoot, root / "release")
      else Seq.empty

      // Commit the incremented build number while retaining the development version.
      os.write.over(versionFile, originalVersion)
      run("git", "add", "version.txt", "build.number")
      run("git", "commit", "-m", s"Release build $nextBuildNumber")
      committed = true
      val head = capture("git", "rev-parse", "HEAD")
      check(head.exitCode == 0, "Cannot determine the release commit.")
      releaseCommit = head.out.text().trim

      run("git", "push")

      if capture("gh", "release", "view", tag).exitCode != 0 then
        run("gh", "release", "create", tag, "--target", releaseCommit, "--title", tag, "--generate-notes")
      val assets = Seq(artifact) ++ (if selected.mac then Seq(dmgArtifact, dmgChecksum) else Seq.empty)
      run((Seq("gh", "release", "upload", tag) ++ assets.map(_.toString) ++ Seq("--clobber"))*)
      println(s"Published ${assets.map(_.last).mkString(", ")} to $tag")
      if selected.zips then PublishZipInstallers.publish(tag, zipArtifacts)
      // Dispatch only after the JAR is available; a release-created event can race its upload.
      if selected.windows then PublishWindowsInstaller.publish(tag, tag)
      println(s"Release $tag complete. All selected artifacts published.")
    catch
      case scala.util.control.NonFatal(error) =>
        if committed then
          System.err.println(s"Release commit $releaseCommit was retained. Check GitHub release $tag before retrying publication. Local artifacts: ${root / "release"}")
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
