//> using file support/ScriptSupport.scala support/JunitReport.scala

import fdswarm.scripts.JunitReport
import java.nio.file.Path
import scala.util.control.NonFatal

@main def junitToHtml(arguments: String*): Unit =
  val help = "Usage: scala-cli run scripts/junit-to-html.scala -- REPORT.xml [--output REPORT.html]"
  if arguments.contains("--help") then println(help)
  else
    try
      val (input, output) = arguments.toList match
        case input :: Nil => (Path.of(input), None)
        case input :: "--output" :: output :: Nil => (Path.of(input), Some(Path.of(output)))
        case _ => throw new IllegalArgumentException(help)
      println(JunitReport.convert(input, output))
    catch case NonFatal(error) =>
      Console.err.println(s"Cannot generate test report: ${error.getMessage}")
      sys.exit(1)
