//| scalaVersion: 3.10.0
//| extends: mill.script.ScalaModule.Raw
//| moduleDeps: [PublishWindowsInstaller.scala]

/** Offline regression checks: ./mill --no-server PublishWindowsInstallerTest.scala */
object PublishWindowsInstallerTest:
  import PublishWindowsInstaller.Result

  private class GitHub(scenario: String):
    val calls = scala.collection.mutable.ArrayBuffer.empty[Seq[String]]
    private var lists = 0
    private var progress = 0
    def apply(args: Seq[String]): Result =
      calls += args
      args.take(2) match
        case Seq("run", "list") =>
          lists += 1
          Result(output = if lists == 1 || scenario == "old-run" then "42" else "43")
        case Seq("run", "view") if args.contains("--json") =>
          progress += 1
          val state = if scenario == "progress" && progress == 1 then "queued\t\thttps://example.test/run/43"
          else if scenario == "progress" && progress == 2 then
            "in_progress\t\thttps://example.test/run/43\nwindows-x64: in_progress — Build installer"
          else if scenario == "failed-run" then "completed\tfailure\thttps://example.test/run/43"
          else "completed\tsuccess\thttps://example.test/run/43"
          Result(output = state)
        case Seq("release", "view") => Result(output = if scenario == "missing-assets" then "false" else "true")
        case _ => Result()
    def publish(tag: String = "v0.0.4-30"): Unit =
      PublishWindowsInstaller.publish(tag, "release-ref", apply, () => ())

  private def fails(body: => Unit): Unit =
    val failed = try
      body
      false
    catch
      case _: IllegalStateException | _: IllegalArgumentException => true
    assert(failed, "Expected publication to fail")

  def main(args: Array[String]): Unit =
    val success = new GitHub("success")
    success.publish()
    assert(success.calls.contains(Seq("workflow", "run", "windows-installer.yml",
      "--ref", "release-ref", "-f", "tag=v0.0.4-30")))
    val view = success.calls.find(call => call.take(2) == Seq("run", "view") && call.contains("--json")).get
    assert(view(2) == "43" && view.contains("status,conclusion,url,jobs"))
    assert(success.calls.find(_.take(2) == Seq("release", "view")).get.last
      .contains("FdSwarm-0.0.4-30-windows-x64-setup.exe.sha256"))

    val failed = new GitHub("failed-run")
    fails(failed.publish())
    assert(failed.calls.contains(Seq("run", "view", "43", "--log-failed")))
    assert(!failed.calls.exists(_.take(2) == Seq("release", "view")))

    val missing = new GitHub("missing-assets")
    fails(missing.publish())

    val old = new GitHub("old-run")
    fails(old.publish())
    assert(!old.calls.exists(_.take(2) == Seq("run", "view")))

    val invalid = new GitHub("success")
    fails(invalid.publish("invalid-tag"))
    assert(invalid.calls.isEmpty)
    val progressing = new GitHub("progress")
    val output = new java.io.ByteArrayOutputStream()
    Console.withOut(output) { progressing.publish() }
    val text = output.toString(java.nio.charset.StandardCharsets.UTF_8)
    assert(text.contains("queued") && text.contains("in_progress") && text.contains("Build installer"))
    assert(text.contains("elapsed") && text.contains("https://example.test/run/43"))
    println("All 6 Windows publication regression checks passed.")
