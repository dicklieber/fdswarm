//| scalaVersion: 3.10.0
//| extends: mill.script.ScalaModule.Raw
//| moduleDeps: [Release.scala]

object ReleaseTest:
  def main(args: Array[String]): Unit =
    for mac <- Seq(false, true); windows <- Seq(false, true); zips <- Seq(false, true) do
      val answers = Iterator(mac, windows, zips).map(if _ then "yes" else "no")
      val prompts = scala.collection.mutable.ArrayBuffer.empty[String]
      val selected = Release.publishing { label =>
        prompts += label
        answers.next()
      }
      assert(selected == Release.Publishing(mac, windows, zips))
      assert(prompts.size == 3 && prompts.head.contains("macOS") && prompts(1).contains("Windows") && prompts(2).contains("ZIP"))
    assert(Release.publishing(_ => "") == Release.Publishing(true, true, true))
    val retried = Iterator("invalid", "n", "n", "y")
    assert(Release.publishing(_ => retried.next()) == Release.Publishing(false, false, true))
    val closed = try
      Release.publishing(_ => null)
      false
    catch case _: IllegalArgumentException => true
    assert(closed)
    println("Release selection checks passed: all eight combinations, defaults, invalid input, and closed input.")
