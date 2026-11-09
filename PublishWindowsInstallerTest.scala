//| scalaVersion: 3.10.0
//| extends: mill.script.ScalaModule.Raw
//| moduleDeps: [PublishWindowsInstaller.scala]

/** Offline regression checks: ./mill --no-server PublishWindowsInstallerTest.scala */
object PublishWindowsInstallerTest:
  import PublishWindowsInstaller.Result

  private class GitHub(scenario: String):
    val calls = scala.collection.mutable.ArrayBuffer.empty[Seq[String]]
    private var lists = 0
    def apply(args: Seq[String]): Result =
      calls += args
      args.take(2) match
        case Seq("run", "list") =>
          lists += 1
          Result(output = if lists == 1 || scenario == "old-run" then "42" else "43")
        case Seq("run", "watch") => Result(exitCode = if scenario == "failed-run" then 1 else 0)
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
    val watch = success.calls.find(_.take(2) == Seq("run", "watch")).get
    assert(watch(2) == "43" && watch.contains("--exit-status"))
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
    assert(!old.calls.exists(_.take(2) == Seq("run", "watch")))

    val invalid = new GitHub("success")
    fails(invalid.publish("invalid-tag"))
    assert(invalid.calls.isEmpty)
    println("All 5 Windows publication regression checks passed.")
