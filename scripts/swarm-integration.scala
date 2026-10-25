//> using file support/ScriptSupport.scala support/JunitReport.scala

import fdswarm.scripts.{JunitReport, ScriptSupport}
import ScriptSupport.*
import io.circe.Json
import io.circe.syntax.*
import java.io.IOException
import java.net.{DatagramPacket, DatagramSocket, InetAddress, InetSocketAddress, NetworkInterface, Proxy, ProxySelector, SocketAddress, URI}
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.channels.DatagramChannel
import java.nio.file.{Files, Path}
import java.security.SecureRandom
import java.time.{Duration, Instant, OffsetDateTime}
import java.util.concurrent.TimeUnit
import scala.collection.mutable.ArrayBuffer
import scala.jdk.CollectionConverters.*
import scala.util.{Using, Try}
import scala.util.control.NonFatal

object SwarmIntegration:
  case class Options(coverage: Boolean = false, open: Boolean = false, skipBuild: Boolean = false,
      jar: Option[Path] = None, javaCommand: String = "java", contest: String = "ARRL",
      broadcastAddress: Option[String] = None, timeoutSeconds: Double = 60):
    def buildDirectory(directory: Path): Path =
      if coverage then directory.resolve("coverage-build")
      else Path.of(sys.env.getOrElse("MILL_OUTPUT_DIR", "/private/tmp/fdswarm-swarm-build")).toAbsolutePath.normalize()

  val help = """Usage: scala-cli run scripts/swarm-integration.scala -- [options]
  --coverage                 Build an instrumented JAR; combine coverage from all nodes
  --open                     Open generated HTML reports in the default browser (macOS)
  --skip-build               Reuse the existing application assembly
  --jar PATH                 Existing JAR (requires --skip-build)
  --java PATH                Liberica JDK 21 Full java executable (default: java)
  --contest ARRL|WFD         Contest to exercise (default: ARRL)
  --broadcast-address IP     Explicit LAN broadcast address (otherwise detected)
  --timeout SECONDS          Deadline per convergence check (default: 60)
  --help                     Show this help"""

  def parseOptions(arguments: Seq[String]): Options =
    @annotation.tailrec
    def parse(rest: List[String], options: Options): Options = rest match
      case Nil => options
      case "--coverage" :: tail => parse(tail, options.copy(coverage = true))
      case "--open" :: tail => parse(tail, options.copy(open = true))
      case "--skip-build" :: tail => parse(tail, options.copy(skipBuild = true))
      case "--jar" :: value :: tail => parse(tail, options.copy(jar = Some(Path.of(value).toAbsolutePath.normalize())))
      case "--java" :: value :: tail => parse(tail, options.copy(javaCommand = value))
      case "--contest" :: value :: tail => parse(tail, options.copy(contest = value))
      case "--broadcast-address" :: value :: tail => parse(tail, options.copy(broadcastAddress = Some(value)))
      case "--timeout" :: value :: tail => parse(tail, options.copy(timeoutSeconds = value.toDouble))
      case invalid => throw new IllegalArgumentException(s"Unknown or incomplete options: ${invalid.mkString(" ")}\n$help")
    val options = parse(arguments.toList, Options())
    require(Set("ARRL", "WFD").contains(options.contest), "--contest must be ARRL or WFD")
    require(options.timeoutSeconds.isFinite && options.timeoutSeconds > 12, "--timeout must exceed the 12-second stability check")
    require(!(options.coverage && (options.skipBuild || options.jar.nonEmpty)),
      "--coverage requires a fresh instrumented build; it cannot be combined with --skip-build or --jar")
    require(options.jar.isEmpty || options.skipBuild, "--jar requires --skip-build")
    require(!options.open || System.getProperty("os.name").toLowerCase.contains("mac"), "--open currently supports macOS only")
    options

  def matches(snapshot: Json, actualConfig: Json, config: Json, expected: Map[String, Json], start: Option[String]): Boolean =
    val records = jsonArray(snapshot, "qsos")
    val actual = records.map(qso => jsonString(qso, "uuid") -> qso).toMap
    actual == expected && records.size == expected.size && actualConfig == config &&
      start.forall(_ == jsonString(jsonField(snapshot, "statusMessage"), "contestStart"))

  def checkExitCodes(codes: Seq[(String, Option[Int])]): Unit =
    codes.foreach { (name, code) =>
      code.foreach(value => throw new IllegalStateException(s"Node $name exited with code $value; see its stdout/stderr logs"))
    }

  def measurementRuntimes(paths: Seq[Path]): Set[String] =
    val pattern = "scoverage\\.measurements\\.([0-9a-f-]{36}).*".r
    paths.flatMap(path => path.getFileName.toString match
      case pattern(id) => Some(id)
      case _ => None).toSet

  case class Node(name: String, port: Int, directory: Path, startup: Json, process: Process)
  case class PhaseResult(name: String, seconds: Double, error: Option[String])
  case class HttpFailure(code: Int, response: String) extends IOException(s"HTTP $code: $response")

  def run(arguments: Seq[String]): Int =
    if arguments.contains("--help") then
      println(help)
      0
    else
      val options = parseOptions(arguments)
      val test = new SwarmTest(options, repositoryRoot)
      val hook = new Thread(() => test.finish(true), "swarm-test-cleanup")
      Runtime.getRuntime.addShutdownHook(hook)
      var failed = true
      try
        test.scenario()
        failed = false
      catch case NonFatal(error) => test.log(s"Integration run failed: ${error.getMessage}")
      finally
        try failed = test.finish(failed)
        finally Runtime.getRuntime.removeShutdownHook(hook)
      if failed then 1 else 0

  final class SwarmTest(options: Options, root: Path):
    val directory: Path = Files.createTempDirectory("fdswarm-swarm-integration-")
    private val logFile = Files.newBufferedWriter(directory.resolve("progress.log"))
    private val random = new SecureRandom()
    private def randomToken(bytes: Int): String =
      val data = new Array[Byte](bytes)
      random.nextBytes(data)
      java.util.HexFormat.of().formatHex(data)
    private val token = randomToken(32)
    private val nodes = ArrayBuffer.empty[Node]
    private val processes = ArrayBuffer.empty[Process]
    private val results = ArrayBuffer.empty[PhaseResult]
    private var lastState = Json.obj()
    private var expected = Map.empty[String, Json]
    private var config = Json.Null
    private var broadcastAddress = ""
    private var jar: Path = null
    private var finished = false
    private var finalFailed = true
    private var udpReservation: Option[DatagramSocket] = None
    private var udpPort = 0
    private var reservations = Vector.empty[java.net.ServerSocket]
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
      .proxy(new ProxySelector:
        def select(uri: URI): java.util.List[Proxy] = java.util.List.of(Proxy.NO_PROXY)
        def connectFailed(uri: URI, address: SocketAddress, error: IOException): Unit = ()
      ).build()
    private def buildDir = options.buildDirectory(directory)

    log(s"Artifacts: $directory; tail -f ${directory.resolve("progress.log")}")

    def log(message: String): Unit = synchronized {
      val line = s"${OffsetDateTime.now().withNano(0)} $message"
      println(line)
      logFile.write(line)
      logFile.newLine()
      logFile.flush()
    }

    private def phase[A](name: String)(body: => A): A =
      val started = System.nanoTime()
      log(s"START $name")
      var error = Option.empty[String]
      try body
      catch
        case NonFatal(failure) =>
          error = Some(trace(failure))
          throw failure
      finally
        val seconds = (System.nanoTime() - started) / 1e9
        results += PhaseResult(name, seconds, error)
        log(f"${if error.isDefined then "FAIL" else "PASS"} $name ($seconds%.2fs)")

    private def spawn(command: Seq[String], cwd: Path, stdout: Path, stderr: Option[Path] = None,
        environment: Map[String, String] = Map.empty, removeEnvironment: Seq[String] = Nil): Process =
      val builder = new ProcessBuilder(command.asJava).directory(cwd.toFile).redirectOutput(stdout.toFile)
      stderr match
        case Some(path) => builder.redirectError(path.toFile)
        case None => builder.redirectErrorStream(true)
      environment.foreach((name, value) => builder.environment().put(name, value))
      removeEnvironment.foreach(builder.environment().remove)
      val process = builder.start()
      processes.synchronized { processes += process }
      process

    private def mill(target: String, logPath: Path, progress: Boolean = false): Unit =
      val process = spawn(Seq(root.resolve("mill").toString, "--no-server", target), root, logPath,
        environment = Map("MILL_OUTPUT_DIR" -> buildDir.toString))
      while !process.waitFor(5, TimeUnit.SECONDS) do
        if progress then log("Build still running…")
      require(process.exitValue() == 0, s"Mill $target failed with exit code ${process.exitValue()}; see $logPath")

    private def build(): Unit =
      if options.skipBuild then
        jar = options.jar.getOrElse(buildDir.resolve("fdswarm/assembly.dest/fdswarm.jar"))
      else
        log(s"Building fdswarm.jar; detailed output: ${directory.resolve("build.log")}")
        mill(if options.coverage then "fdswarm.integrationCoverageAssembly" else "fdswarm.assembly",
          directory.resolve("build.log"), progress = true)
        jar = buildDir.resolve(if options.coverage then "fdswarm/integrationCoverageAssembly.dest/fdswarm-coverage.jar"
          else "fdswarm/assembly.dest/fdswarm.jar")
      require(Files.isRegularFile(jar), s"Application JAR not found: $jar")

    private def request(node: Node, path: String, body: Option[Json] = None, control: Boolean = false): Json =
      val builder = HttpRequest.newBuilder(URI.create(s"http://127.0.0.1:${node.port}/$path"))
        .timeout(Duration.ofSeconds(3)).header("Accept", "*/*")
      if control then builder.header("X-Fdswarm-Test-Token", token)
      body match
        case Some(json) => builder.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(json.noSpaces))
        case None => builder.GET()
      val response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString())
      if response.statusCode() >= 400 then throw HttpFailure(response.statusCode(), response.body())
      if response.body().isBlank then Json.Null
      else io.circe.parser.parse(response.body()).fold(error => throw new IOException(error.getMessage, error), identity)

    private def waitFor(description: String, stableSeconds: Double = 0)(condition: => Boolean): Unit =
      val deadline = System.nanoTime() + (options.timeoutSeconds * 1e9).toLong
      var stableSince = Option.empty[Long]
      var nextMessage = 0L
      var lastError = "condition not met"
      while System.nanoTime() < deadline do
        checkExitCodes(nodes.toVector.map(node => node.name -> Option.when(!node.process.isAlive)(node.process.exitValue())))
        try
          if condition then
            if stableSince.isEmpty then stableSince = Some(System.nanoTime())
            if (System.nanoTime() - stableSince.get) / 1e9 >= stableSeconds then return
            lastError = "matching; checking stability"
          else
            stableSince = None
            lastError = "waiting for convergence"
        catch case error: IOException =>
          stableSince = None
          lastError = Option(error.getMessage).getOrElse(error.toString)
        if System.nanoTime() >= nextMessage then
          log(s"WAIT $description: $lastError; ${lastState.noSpaces}")
          nextMessage = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        Thread.sleep(250)
      throw new AssertionError(s"Timed out after ${options.timeoutSeconds}s: $description; $lastError; ${lastState.noSpaces}")

    private def reservePorts(): Unit =
      val udp = new DatagramSocket(new InetSocketAddress("0.0.0.0", 0))
      udpReservation = Some(udp)
      udpPort = udp.getLocalPort
      val reserved = ArrayBuffer.empty[java.net.ServerSocket]
      try
        for _ <- 0 until 4 do
          val socket = new java.net.ServerSocket()
          socket.bind(new InetSocketAddress("127.0.0.1", 0))
          reserved += socket
        reservations = reserved.toVector
      catch case NonFatal(error) =>
        reserved.foreach(_.close())
        throw error

    private def selectBroadcastAddress(): Unit =
      reservePorts()
      val candidates = options.broadcastAddress.toSeq match
        case Seq() =>
          val detected = NetworkInterface.getNetworkInterfaces.asScala.toVector.filter(_.isUp)
            .flatMap(_.getInterfaceAddresses.asScala).flatMap(address => Option(address.getBroadcast).map(_.getHostAddress))
          (detected :+ "255.255.255.255").distinct
        case explicit => explicit
      for address <- candidates do
        val sockets = ArrayBuffer.empty[DatagramChannel]
        try
          for _ <- 0 until 2 do
            val channel = DatagramChannel.open(java.net.StandardProtocolFamily.INET)
            sockets += channel
            channel.setOption[java.lang.Boolean](java.net.StandardSocketOptions.SO_REUSEADDR, true)
            if channel.supportedOptions().contains(java.net.StandardSocketOptions.SO_REUSEPORT) then
              channel.setOption[java.lang.Boolean](java.net.StandardSocketOptions.SO_REUSEPORT, true)
            channel.setOption[java.lang.Boolean](java.net.StandardSocketOptions.SO_BROADCAST, true)
            val port = if sockets.size == 1 then 0 else sockets.head.socket().getLocalPort
            channel.bind(new InetSocketAddress("0.0.0.0", port))
            channel.socket().setSoTimeout(500)
          val payload = "fdswarm-integration-broadcast-probe".getBytes(java.nio.charset.StandardCharsets.UTF_8)
          sockets.head.socket().send(new DatagramPacket(payload, payload.length, InetAddress.getByName(address), sockets.head.socket().getLocalPort))
          val delivered = sockets.forall { channel =>
            val packet = new DatagramPacket(new Array[Byte](128), 128)
            channel.socket().receive(packet)
            java.util.Arrays.equals(packet.getData.take(packet.getLength), payload)
          }
          if delivered then
            broadcastAddress = address
            log(s"Broadcast preflight passed: $address; test UDP port $udpPort")
            return
        catch case error: IOException => log(s"Broadcast preflight $address: ${error.getMessage}")
        finally sockets.foreach(_.close())
      throw new IOException("No working UDP broadcast address. Check network access or supply --broadcast-address with your LAN broadcast address.")

    private def launch(index: Int): Node =
      val name = ('A' + index).toChar.toString
      val nodeDir = Files.createDirectory(directory.resolve(s"node-$name"))
      val dataDir = Files.createDirectory(nodeDir.resolve("data"))
      val reservation = reservations(index)
      val port = reservation.getLocalPort
      val startup = Json.obj("enable" -> true.asJson,
        "operator" -> Vector("W1AAA", "W1BBB", "W1CCC", "W1DDD")(index).asJson,
        "bandMode" -> Vector("20m CW", "40m PH", "15m DIGI", "10m CW")(index).asJson,
        "clearQsos" -> true.asJson, "skipInitDiscover" -> true.asJson, "debugMode" -> "Off".asJson,
        "id" -> s"swarm-$name-${randomToken(4)}".asJson,
        "integrationTest" -> Json.obj("dataDirectory" -> dataDir.toString.asJson,
          "udpPort" -> udpPort.asJson, "token" -> token.asJson, "broadcastAddress" -> broadcastAddress.asJson))
      val startupPath = nodeDir.resolve("startup.json")
      write(startupPath, startup.spaces2)
      val home = Files.createDirectory(nodeDir.resolve("home"))
      reservation.close()
      if index == 0 then udpReservation.foreach(_.close())
      val command = Seq(options.javaCommand, s"-Duser.home=$home", "-Xmx512m", "-jar", jar.toString,
        "--startupInfo", startupPath.toString, "--integration-test")
      log(s"Launching node $name: HTTP $port, shared UDP $udpPort, logs $nodeDir")
      val process = spawn(command, nodeDir, nodeDir.resolve("stdout.log"), Some(nodeDir.resolve("stderr.log")),
        Map("PORT" -> port.toString), Seq("PREDELETE_CONTEST_CONFIG"))
      val node = Node(name, port, nodeDir, startup, process)
      nodes += node
      waitFor(s"node $name ready")(request(node, "integration-test/ready", control = true) == true.asJson)
      val initial = jsonField(request(node, "statusMessage"), "bandNodeOperator")
      assert(jsonField(initial, "operator") == jsonField(startup, "operator"), "Startup operator was not applied")
      assert(jsonField(initial, "bandMode") == jsonField(startup, "bandMode"), "Startup band/mode was not applied")
      val rejected = Try(request(node, "integration-test/ready")).failed.toOption
      assert(rejected.exists { case error: HttpFailure => Set(400, 401, 403).contains(error.code); case _ => false },
        "Test controls must reject requests without the test token")
      node

    private def converged(config: Json, expected: Map[String, Json], start: Option[String] = None): Boolean =
      val snapshots = nodes.toVector.map { node =>
        val snapshot = request(node, "qsos")
        val actualConfig = request(node, "contestConfig")
        val summary = Json.obj("qsos" -> jsonArray(snapshot, "qsos").size.asJson,
          "fingerPrint" -> actualConfig.hcursor.downField("fingerPrint").focus.getOrElse(Json.Null),
          "contestStart" -> jsonField(jsonField(snapshot, "statusMessage"), "contestStart"))
        (node.name, summary, matches(snapshot, actualConfig, config, expected, start))
      }
      lastState = Json.obj(snapshots.map((name, summary, _) => name -> summary)*)
      snapshots.forall(_._3)

    private def enterQsos(sources: Seq[Node], prefix: String): Unit =
      for (node, index) <- sources.zipWithIndex; number <- 0 until 3 do
        val body = Json.obj("callsign" -> s"K${index + 1}$prefix${('A' + number).toChar}".asJson,
          "exchange" -> (if options.contest == "ARRL" then "2A IL" else "2O IL").asJson,
          "bandMode" -> jsonField(node.startup, "bandMode"), "stamp" -> Instant.now().minusSeconds(5).toString.asJson)
        val qso = request(node, "integration-test/qso", Some(body), control = true)
        val metadata = jsonField(qso, "qsoMetadata")
        assert(jsonField(jsonField(metadata, "node"), "instanceId") == jsonField(node.startup, "id"))
        assert(jsonField(jsonField(metadata, "station"), "operator") == jsonField(node.startup, "operator"))
        assert(jsonField(qso, "callsign") == jsonField(body, "callsign"))
        val uuid = jsonString(qso, "uuid")
        expected += uuid -> qso
        log(s"Entered ${jsonString(qso, "callsign")} on ${node.name} UUID=$uuid")
      write(directory.resolve(s"expected-qsos-${prefix.toLowerCase}.json"), Json.arr(expected.values.toSeq*).spaces2)

    def scenario(): Unit =
      phase("0. Verify local UDP broadcast delivery")(selectBroadcastAddress())
      phase("1. Build application JAR")(build())
      var configRequest = Json.obj("name" -> options.contest.asJson, "ourCallsign" -> "W1AW".asJson,
        "transmitters" -> 2.asJson, "ourClass" -> (if options.contest == "ARRL" then "A" else "O").asJson,
        "ourSection" -> "CT".asJson, "club" -> "Integration initial".asJson)
      phase("2. Start three empty nodes and configure node A") {
        (0 until 3).foreach(launch)
        config = request(nodes.head, "integration-test/contest", Some(configRequest), control = true)
        waitFor("initial configuration on A/B/C")(converged(config, Map.empty))
      }
      phase("3. Enter QSOs on each original node and verify complete propagation") {
        enterQsos(nodes.toVector, "INT")
        waitFor("all 9 QSOs on A/B/C")(converged(config, expected))
      }
      phase("4. Add empty node D and verify QSO and configuration catch-up") {
        launch(3)
        waitFor("all 9 QSOs and full configuration on A/B/C/D")(converged(config, expected))
      }
      val contestStart = phase("5. Start a contest on B; verify all nodes clear and remain clear") {
        val start = request(nodes(1), "integration-test/start-contest", Some(Json.obj()), control = true).asString.get
        waitFor("empty stores with matching contest-start timestamp", stableSeconds = 12)(converged(config, Map.empty, Some(start)))
        expected = Map.empty
        start
      }
      phase("6. Change contest configuration on C and verify all four nodes") {
        configRequest = configRequest.mapObject(_.add("club", "Integration changed on node C".asJson).add("transmitters", 3.asJson))
        val previous = config
        config = request(nodes(2), "integration-test/contest", Some(configRequest), control = true)
        assert(jsonField(previous, "fingerPrint") != jsonField(config, "fingerPrint"))
        assert(jsonField(previous, "timeStamp") != jsonField(config, "timeStamp"))
        waitFor("new complete configuration on A/B/C/D")(converged(config, Map.empty, Some(contestStart)))
      }
      phase("7. Enter new-contest QSOs and verify replication still works") {
        enterQsos(nodes.toVector, "NEW")
        waitFor("all 12 new QSOs, no old QSOs", stableSeconds = 2)(converged(config, expected, Some(contestStart)))
      }

    private def coverageReport(): Unit =
      val data = buildDir.resolve("fdswarm/scoverage/data.dest")
      val measurements = Using.resource(Files.list(data))(_.iterator().asScala.filter(_.getFileName.toString.startsWith("scoverage.measurements.")).toVector)
      val runtimes = measurementRuntimes(measurements)
      require(runtimes.size >= nodes.size, s"Expected measurements from ${nodes.size} JVMs, found ${runtimes.size}")
      log(s"Combining ${measurements.size} measurement files from ${runtimes.size} JVMs")
      mill("fdswarm.scoverage.htmlReport", directory.resolve("coverage-html.log"))
      mill("fdswarm.scoverage.xmlReport", directory.resolve("coverage-xml.log"))
      copyTree(buildDir.resolve("fdswarm/scoverage/htmlReport.dest"), directory.resolve("coverage"))
      val xml = directory.resolve("coverage.xml")
      Files.copy(buildDir.resolve("fdswarm/scoverage/xmlReport.dest/scoverage.xml"), xml)
      val summary = readXml(xml).getDocumentElement
      val invoked = attribute(summary, "statements-invoked", "0").toInt
      assert(invoked > 0, "Coverage report contains no executed statements")
      log(s"Coverage: $invoked of ${attribute(summary, "statement-count")} statements invoked; HTML report: ${directory.resolve("coverage/index.html")}")

    def finish(initialFailed: Boolean): Boolean = synchronized {
      if finished then return finalFailed
      finished = true
      var failed = initialFailed
      if failed then
        for node <- nodes.toVector; endpoint <- Seq("qsos", "contestConfig", "statusMessage") do
          try write(node.directory.resolve(s"failure-$endpoint.json"), request(node, endpoint).spaces2)
          catch case NonFatal(error) => log(s"Could not snapshot ${node.name}/$endpoint: ${error.getMessage}")
      processes.synchronized { processes.toVector.reverse }.foreach { process =>
        try ScriptSupport.stop(process)
        catch case NonFatal(error) =>
          log(s"Could not stop process ${process.pid()}: ${error.getMessage}")
          failed = true
      }
      nodes.foreach(node => log(s"Stopped node ${node.name}"))
      reservations.foreach(_.close())
      udpReservation.foreach(_.close())
      if options.coverage && nodes.nonEmpty then
        try phase("8. Combine node coverage and generate reports")(coverageReport())
        catch case NonFatal(error) =>
          log(s"Coverage generation failed: ${error.getMessage}; see coverage-html.log and coverage-xml.log")
          failed = true
      val document = xmlFactory.newDocumentBuilder().newDocument()
      val suite = document.createElement("testsuite")
      document.appendChild(suite)
      Seq("name" -> "fdswarm.swarmIntegration", "tests" -> results.size.toString,
        "failures" -> results.count(_.error.nonEmpty).toString, "errors" -> "0", "skipped" -> "0",
        "time" -> "%.3f".formatLocal(java.util.Locale.ROOT, results.map(_.seconds).sum))
        .foreach((name, value) => suite.setAttribute(name, value))
      results.foreach { result =>
        val test = append(document, suite, "testcase", "classname" -> "fdswarm.swarmIntegration",
          "name" -> result.name, "time" -> "%.3f".formatLocal(java.util.Locale.ROOT, result.seconds))
        result.error.foreach(error => append(document, test, "failure", "message" -> "Integration phase failed").setTextContent(error))
      }
      val xml = directory.resolve("test-report.xml")
      writeXml(document, xml)
      JunitReport.convert(xml)
      log(s"${if failed then "FAILED" else "PASSED"} — artifacts and HTML report: $directory")
      logFile.close()
      if options.open then
        val reports = Seq(directory.resolve("test-report.html"), directory.resolve("coverage/index.html")).filter(Files.exists(_))
        val browser = new ProcessBuilder((Seq("open") ++ reports.map(_.toString)).asJava).inheritIO().start()
        require(browser.waitFor() == 0, "Could not open HTML reports")
      finalFailed = failed
      failed
    }

@main def swarmIntegration(arguments: String*): Unit =
  try sys.exit(SwarmIntegration.run(arguments))
  catch case NonFatal(error) =>
    Console.err.println(error.getMessage)
    sys.exit(1)
