package fdswarm.scripts

import java.nio.file.{Files, Path}
import org.w3c.dom.Element
import ScriptSupport.*

object JunitReport:
  private val style = """
body { font: 1rem/1.5 system-ui, sans-serif; margin: 0 auto; padding: 1.5rem;
       max-width: 90rem; color: #18212b; background: #f6f8fa; }
h1, h2, h3, p, summary { overflow-wrap: anywhere; }
.totals { display: flex; flex-wrap: wrap; gap: .75rem; margin: 1rem 0; }
.totals span, article { background: white; border: 1px solid #c5ccd3;
                       border-radius: .4rem; padding: 1rem; }
article { margin: 1rem 0; border-left: .4rem solid #27733c; }
article.failed, article.error { border-left-color: #b42318; }
article.skipped { border-left-color: #8a6100; }
h3 { margin: 0; font-size: 1.05rem; }
.status { font-weight: bold; }
pre { white-space: pre-wrap; overflow-wrap: anywhere; font-size: .95rem; }
summary { cursor: pointer; }
"""

  def escape(text: String): String =
    text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
      .replace("\"", "&quot;").replace("'", "&#39;")

  private def details(element: Element, heading: String): String =
    val attributes = element.getAttributes
    val values = (0 until attributes.getLength).map { index =>
      val attribute = attributes.item(index)
      s"${attribute.getNodeName}: ${attribute.getNodeValue}"
    }
    val content = (values :+ element.getTextContent).filter(_.nonEmpty).mkString("\n")
    s"<details open><summary>${escape(heading)}</summary><pre>${escape(content)}</pre></details>"

  def render(root: Element, source: Path): String =
    require(Set("testsuite", "testsuites").contains(root.getTagName),
      "Expected a JUnit testsuite or testsuites root element")
    val counts = scala.collection.mutable.Map.from(Seq("passed", "failed", "error", "skipped").map(_ -> 0))
    var duration = 0.0
    val suites = Option.when(root.getTagName == "testsuite")(root).toVector ++ descendants(root, "testsuite")
    val sections = suites.map { suite =>
      val cases = children(suite, "testcase").map { test =>
        val status = if children(test, "error").nonEmpty then "error"
          else if children(test, "failure").nonEmpty then "failed"
          else if children(test, "skipped").nonEmpty then "skipped" else "passed"
        counts(status) += 1
        val seconds = attribute(test, "time", "0")
        duration += seconds.toDouble
        val diagnostics = Seq("failure" -> "Failure", "error" -> "Error", "skipped" -> "Skipped",
          "system-out" -> "Standard output", "system-err" -> "Standard error").flatMap { (tag, title) =>
          children(test, tag).map(details(_, title))
        }.mkString
        s"""<article class="$status"><h3>${escape(attribute(test, "name", "Unnamed test"))}</h3>
<p>${escape(attribute(test, "classname"))}</p><p><span class="status">${status.capitalize}</span>
· ${escape(seconds)} seconds</p>$diagnostics</article>"""
      }.mkString
      val output = Seq("system-out" -> "Suite standard output", "system-err" -> "Suite standard error")
        .flatMap((tag, title) => children(suite, tag).map(details(_, title))).mkString
      s"<section><h2>${escape(attribute(suite, "name", "Unnamed suite"))}</h2>$cases$output</section>"
    }.mkString
    val totals = s"<span>Total: ${counts.values.sum}</span>" +
      Seq("passed", "failed", "error", "skipped").map(label => s"<span>${label.capitalize}: ${counts(label)}</span>").mkString
    val time = "%.3f".formatLocal(java.util.Locale.ROOT, duration)
    s"""<!doctype html><html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>FdSwarm test results</title><style>$style</style></head><body>
<h1>FdSwarm test results</h1><p>Source: ${escape(source.toString)}</p>
<div class="totals">$totals<span>Total test time: $time seconds</span></div>$sections</body></html>"""

  def convert(input: Path, output: Option[Path] = None): Path =
    val destination = output.getOrElse {
      val name = input.getFileName.toString
      val dot = name.lastIndexOf('.')
      input.resolveSibling((if dot > 0 then name.take(dot) else name) + ".html")
    }
    require(destination.toAbsolutePath.normalize() != input.toAbsolutePath.normalize() &&
      !(Files.exists(destination) && Files.isSameFile(input, destination)),
      "HTML output must not overwrite the XML input")
    val document = readXml(input)
    write(destination, render(document.getDocumentElement, input))
    destination
