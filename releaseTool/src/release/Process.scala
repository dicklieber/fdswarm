package release

import scala.sys.process.*

object Process {

  def run(
      cmd: Seq[String],
      cwd: os.Path = os.pwd
  ): Unit = {

    println(s"[run] ${cmd.mkString(" ")}")

    val rc =
      scala.sys.process.Process(cmd, cwd.toIO).!

    if rc != 0 then
      sys.error(s"command failed: ${cmd.mkString(" ")}")
  }

}
