import fdswarm.scripts.{JunitReport, ScriptSupport}
import ScriptSupport.*
import io.circe.Json
import io.circe.syntax.*
import java.nio.file.{Files, Path}
import munit.FunSuite

class ScriptTests extends FunSuite:
  private def withDirectory[A](body: Path => A): A =
    val directory = Files.createTempDirectory("fdswarm-script-test-")
    try body(directory)
    finally deleteTree(directory)

  test("nested suites preserve statuses and complete escaped diagnostics"):
    withDirectory { directory =>
      val input = directory.resolve("test-report.xml")
      val longMessage = "details " * 300
      write(input, s"""<testsuites><testsuite name="outer"><testsuite name="inner">
        <testcase name="passes" time="0.25"/>
        <testcase name="fails" time="0.5"><failure message="&lt;script&gt;bad&lt;/script&gt;">$longMessage</failure></testcase>
        <testcase name="errors"><error type="Exception">trace</error></testcase>
        <testcase name="skips"><skipped message="reason"/></testcase>
        <system-out>full output &amp; data</system-out>
        </testsuite></testsuite></testsuites>""")
      val output = JunitReport.convert(input)
      val html = Files.readString(output)
      Seq("Total: 4", "Passed: 1", "Failed: 1", "Error: 1", "Skipped: 1", "0.750 seconds",
        longMessage, "full output &amp; data", "&lt;script&gt;bad&lt;/script&gt;").foreach { expected =>
        assert(html.contains(expected), expected)
      }
      assert(!html.contains("<script>"))
    }

  test("a single suite supports an explicit HTML output path"):
    withDirectory { directory =>
      val input = directory.resolve("source.xml")
      val output = directory.resolve("custom.html")
      write(input, """<testsuite name="single"><testcase name="one"/></testsuite>""")
      assertEquals(JunitReport.convert(input, Some(output)), output)
      assert(Files.readString(output).contains("Total: 1"))
    }

  test("malformed XML and input overwrite are rejected without changing the source"):
    withDirectory { directory =>
      val input = directory.resolve("invalid.xml")
      write(input, "broken XML")
      intercept[org.xml.sax.SAXParseException](JunitReport.convert(input))
      intercept[IllegalArgumentException](JunitReport.convert(input, Some(input)))
      assertEquals(Files.readString(input), "broken XML")
    }

  test("coverage uses a separate directory for every run"):
    val options = SwarmIntegration.Options(coverage = true)
    val first = Path.of("/private/tmp/first-run")
    val second = Path.of("/private/tmp/second-run")
    assertNotEquals(options.buildDirectory(first), options.buildDirectory(second))
    assertEquals(options.buildDirectory(first).getParent, first)

  test("coverage executable path is stable, exclusive, and reusable after cleanup"):
    withDirectory { directory =>
      val source = directory.resolve("source.jar")
      val stable = directory.resolve("executable")
      write(source, "first run")
      val first = new SwarmIntegration.CoverageExecutable(stable)
      val jar = try
        val jar = first.stage(source)
        assertEquals(jar, stable.resolve("fdswarm-coverage.jar"))
        assertEquals(Files.readString(jar), "first run")
        val error = intercept[IllegalStateException](new SwarmIntegration.CoverageExecutable(stable))
        assert(error.getMessage.contains("Another coverage integration run"))
        assertEquals(Files.readString(jar), "first run")
        jar
      finally first.close()
      write(source, "second run")
      val second = new SwarmIntegration.CoverageExecutable(stable)
      try
        assertEquals(second.stage(source), jar)
        assertEquals(Files.readString(jar), "second run")
      finally second.close()
      assert(Files.isRegularFile(jar))
    }

  test("coverage executable lease can be released after staging fails"):
    withDirectory { directory =>
      val executable = new SwarmIntegration.CoverageExecutable(directory)
      try intercept[java.nio.file.NoSuchFileException](executable.stage(directory.resolve("missing.jar")))
      finally executable.close()
      val next = new SwarmIntegration.CoverageExecutable(directory)
      next.close()
    }

  test("coverage rejects reusing a JAR before starting nodes"):
    Seq(Seq("--coverage", "--skip-build"), Seq("--coverage", "--jar", "existing.jar")).foreach { args =>
      val error = intercept[IllegalArgumentException](SwarmIntegration.parseOptions(args))
      assert(error.getMessage.contains("requires a fresh instrumented build"))
    }
    assertEquals(SwarmIntegration.parseOptions(Seq("--coverage", "--contest", "WFD")).contest, "WFD")

  test("thread measurement files count as one JVM per runtime UUID"):
    val id = "01234567-89ab-cdef-0123-456789abcdef"
    val paths = (1 to 3).map(thread => Path.of(s"scoverage.measurements.$id.$thread"))
    assertEquals(SwarmIntegration.measurementRuntimes(paths), Set(id))
    assertEquals(SwarmIntegration.measurementRuntimes(paths :+ Path.of("scoverage.measurements.abcdef01-2345-6789-abcd-ef0123456789.1")).size, 2)

  private val config = Json.obj("fingerPrint" -> "same".asJson, "club" -> "expected".asJson)
  private val qso = Json.obj("uuid" -> "one".asJson, "callsign" -> "W1AW".asJson, "stamp" -> "2026-10-25T20:00:00Z".asJson)
  private def snapshot(records: Seq[Json] = Seq(qso), start: String = "start"): Json =
    Json.obj("qsos" -> Json.arr(records*), "statusMessage" -> Json.obj("contestStart" -> start.asJson))

  test("convergence requires complete records and full configuration"):
    val expected = Map("one" -> qso)
    assert(SwarmIntegration.matches(snapshot(), config, config, expected, Some("start")))
    val modified = qso.mapObject(_.add("stamp", "wrong".asJson))
    assert(!SwarmIntegration.matches(snapshot(Seq(modified)), config, config, expected, Some("start")))
    val changedConfig = config.mapObject(_.add("club", "wrong despite identical fingerprint".asJson))
    assert(!SwarmIntegration.matches(snapshot(), changedConfig, config, expected, Some("start")))

  test("duplicates and stale contest-start timestamps cannot pass convergence"):
    val expected = Map("one" -> qso)
    assert(!SwarmIntegration.matches(snapshot(Seq(qso, qso)), config, config, expected, Some("start")))
    assert(!SwarmIntegration.matches(snapshot(start = "old start"), config, config, expected, Some("start")))

  test("an exited node fails immediately instead of waiting for a timeout"):
    SwarmIntegration.checkExitCodes(Seq("A" -> None))
    val error = intercept[IllegalStateException](SwarmIntegration.checkExitCodes(Seq("A" -> Some(7))))
    assert(error.getMessage.contains("Node A exited with code 7"))
