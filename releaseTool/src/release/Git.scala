package release

object Git {

  def ensureClean(): Unit = {

    val result =
      statusPorcelain()

    if result.nonEmpty then
      sys.error(
        s"""
           |git working tree not clean:
           |
           |$result
           |""".stripMargin
      )
  }

  def statusPorcelain(): String =
    os.proc("git", "status", "--porcelain")
      .call()
      .out
      .text()
      .trim()

}
