//| scalaVersion: 3.10.0
//| extends: mill.script.ScalaModule.Raw
//| mvnDeps: [com.lihaoyi::os-lib:0.11.6]

/** Build and verify Windows assets for an existing GitHub release. */
object PublishWindowsInstaller:
  case class Result(exitCode: Int = 0, output: String = "")

  private def execute(args: Seq[String]): Result =
    val result = os.proc(Seq("gh") ++ args).call(
      check = false, stdin = os.Inherit, stderr = os.Inherit,
      stdout = os.Pipe
    )
    Result(result.exitCode, result.out.text().trim)

  def publish(
      tag: String,
      ref: String = "main",
      gh: Seq[String] => Result = execute,
      pause: () => Unit = () => Thread.sleep(10000)
  ): Unit =
    require(tag.matches(raw"v\d+\.\d+\.\d+(?:-\d+)?"),
      "Expected a release tag such as v0.0.4-30.")
    def check(ok: Boolean, message: => String): Unit =
      if !ok then throw new IllegalStateException(message)
    def checked(args: String*): String =
      val result = gh(args.toSeq)
      check(result.exitCode == 0, s"gh ${args.mkString(" ")} failed: ${result.output}")
      result.output.trim

    val workflow = "windows-installer.yml"
    val title = s"Windows x64 installer for $tag"
    val query = s"""map(select(.displayTitle == "$title")) | map(.databaseId) | max // 0"""
    def latestRun(): Long =
      val output = checked("run", "list", "--workflow", workflow,
        "--event", "workflow_dispatch", "--limit", "100",
        "--json", "databaseId,displayTitle", "--jq", query)
      output.toLongOption.filter(_ >= 0).getOrElse {
        throw new IllegalStateException(s"Cannot identify existing Windows runs: $output")
      }

    // Ignore successful runs from earlier attempts at packaging this tag.
    println(s"Preparing Windows installer workflow for $tag (ref $ref)...")
    val previous = latestRun()
    println(s"Dispatching $workflow...")
    checked("workflow", "run", workflow, "--ref", ref, "-f", s"tag=$tag")
    var runId = Option.empty[Long]
    for attempt <- 0 until 12 if runId.isEmpty do
      println(s"Waiting for GitHub to register the Windows run (${attempt * 10}s elapsed)...")
      val candidate = latestRun()
      if candidate > previous then runId = Some(candidate)
      else if attempt < 11 then pause()
    val id = runId.getOrElse {
      throw new IllegalStateException(
        s"Windows workflow dispatched for $tag, but no new run appeared within two minutes. " +
          s"Inspect with: gh run list --workflow $workflow")
    }
    println(s"Waiting for Windows installer: run $id ($tag)")
    val started = System.nanoTime()
    val progressQuery = """([.status, (.conclusion // ""), .url] | @tsv),
      |(.jobs[]? | .name + ": " + .status + " " + (.conclusion // "") +
      |  ([.steps[]? | select(.status == "in_progress") | " — " + .name] | join("")))""".stripMargin
    var conclusion = Option.empty[String]
    for poll <- 0 until 360 if conclusion.isEmpty do
      val progress = checked("run", "view", id.toString, "--json", "status,conclusion,url,jobs", "--jq", progressQuery)
      val lines = progress.linesIterator.toVector
      val state = lines.headOption.getOrElse("").split("\t", -1)
      check(state.length == 3, s"Cannot read Windows workflow progress: $progress")
      val elapsed = (System.nanoTime() - started) / 1000000000L
      println(s"Windows installer: ${state(0)} (${elapsed}s elapsed) — ${state(2)}")
      lines.drop(1).foreach(line => println(s"  $line"))
      if state(0) == "completed" then conclusion = Some(state(1))
      else if poll < 359 then pause()
    check(conclusion.isDefined, s"Stopped waiting after one hour; Windows run $id may still be running. Inspect with: gh run view $id")
    if conclusion.get != "success" then
      val logs = gh(Seq("run", "view", id.toString, "--log-failed"))
      System.err.println(logs.output)
      throw new IllegalStateException(
        s"Windows installer ${conclusion.get}. Retry: ./mill --no-server PublishWindowsInstaller.scala $tag $ref")

    val installer = s"FdSwarm-${tag.stripPrefix("v")}-windows-x64-setup.exe"
    val assets = checked("release", "view", tag, "--json", "assets", "--jq",
      s"""[.assets[].name] | index("$installer") != null and index("$installer.sha256") != null""")
    check(assets == "true", s"Windows run $id succeeded but release $tag is missing $installer or its checksum.")
    println(s"Published Windows installer and checksum for $tag (run $id).")

  def main(args: Array[String]): Unit =
    if args.toSeq == Seq("--help") then
      println("Usage: ./mill --no-server PublishWindowsInstaller.scala TAG [WORKFLOW_REF]")
    else
      require(args.length == 1 || args.length == 2,
        "Usage: ./mill --no-server PublishWindowsInstaller.scala TAG [WORKFLOW_REF]")
      publish(args(0), args.lift(1).getOrElse("main"))
