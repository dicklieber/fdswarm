//| scalaVersion: 3.10.0
//| extends: mill.script.ScalaModule.Raw
//| moduleDeps: [BuildZipInstallers.scala]

/** Publish portable ZIPs without creating another release or incrementing its version. */
object PublishZipInstallers:
  private def run(args: String*): Unit =
    os.proc(args).call(stdin = os.Inherit, stdout = os.Inherit, stderr = os.Inherit)
    ()

  def publish(tag: String, zips: Seq[os.Path]): Unit =
    require(tag.matches(raw"v\d+\.\d+\.\d+(?:-\d+)?"), s"Invalid release tag: $tag")
    val expected = BuildZipInstallers.platforms.map(p => s"FdSwarm-${tag.stripPrefix("v")}-$p.zip").toSet
    require(zips.map(_.last).toSet == expected && zips.size == expected.size && zips.forall(os.isFile(_)),
      "Expected all five ZIPs with versions matching the release tag")
    for zip <- zips do
      println(s"Uploading ${zip.last} to $tag")
      run("gh", "release", "upload", tag, zip.toString, "--clobber")

  def main(args: Array[String]): Unit =
    val usage = "Usage: ./mill --no-server PublishZipInstallers.scala TAG [JAR] [RUNTIMES_DIR] [OUTPUT_DIR]"
    if args.toSeq == Seq("--help") then println(usage)
    else
      require(args.length >= 1 && args.length <= 4, usage)
      val tag = args(0)
      require(tag.matches(raw"v\d+\.\d+\.\d+(?:-\d+)?"), s"Invalid release tag: $tag")
      val output = os.Path(args.lift(3).getOrElse("release"), os.pwd)
      val jar = args.lift(1).map(os.Path(_, os.pwd)).getOrElse {
        val download = output / "download" / tag
        os.makeDir.all(download)
        println(s"Downloading fdswarm.jar from $tag")
        run("gh", "release", "download", tag, "--pattern", "fdswarm.jar", "--dir", download.toString, "--clobber")
        download / "fdswarm.jar"
      }
      require(s"v${BuildZipInstallers.version(jar)}" == tag, "JAR version does not match release tag")
      val runtimeRoot = os.Path(args.lift(2).getOrElse(sys.env.getOrElse("FDSWARM_RUNTIMES_DIR", "fdswarm-runtimes")), os.pwd)
      publish(tag, BuildZipInstallers.build(jar, runtimeRoot, output))
