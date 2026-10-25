package fdswarm.integration

import fdswarm.{JavaFxTestKit, StartupInfo, StationConfigManager}
import fdswarm.bands.{AvailableBandsManager, AvailableModesManager, BandModeBuilder}
import fdswarm.bandmodes.SelectedBandModeManager
import fdswarm.contestStart.ContestStartManager
import fdswarm.exporter.{AdifExporter, CabrilloExporter}
import fdswarm.fx.contest.{ContestCatalog, ContestConfig, ContestConfigManager, ContestTypes}
import fdswarm.importer.QsoFileImporter
import fdswarm.io.FileHelper
import fdswarm.model.*
import fdswarm.replication.*
import fdswarm.replication.status.SwarmData
import fdswarm.scoring.ContestScoringService
import fdswarm.store.{BigQsosGenerator, QsoStore}
import fdswarm.util.{FilenameStamp, NodeIdentity, NodeIdentityManager}
import io.circe.parser.decode
import munit.FunSuite
import org.mockito.Mockito.{mock, when}

import java.io.ByteArrayOutputStream
import java.time.{Instant, ZoneOffset}
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** Exercises real generator, store, file exporters and importer, without LAN traffic. */
class QsoExportImportTest extends FunSuite:
  test("100 generated QSOs survive ADIF export, clear and import; Cabrillo matches saved JSON"):
    val artifactDirectory = os.temp.dir(prefix = "fdswarm-export-import-", deleteOnExit = false)
    var passed = false
    val started = System.nanoTime()
    def timed[A](phase: String)(body: => A): A =
      val begin = System.nanoTime()
      try body
      finally println(f"QsoExportImportTest $phase: ${(System.nanoTime() - begin) / 1e6}%.1f ms")
    try
      timed("JavaFX initialization")(JavaFxTestKit.init())
      JavaFxTestKit.runOnFx {
        val files = new FileHelper:
          override val directory: os.Path = artifactDirectory
        val startup = new StartupInfo(Array.empty[String])
        val transport = new Transport:
          val mode = "integration-test"
          def send(service: Service[?], data: Array[Byte]): Unit = ()
          def stop(): Unit = ()
        val dispatcher = mock(classOf[NodeStatusDispatcher])
        val stamp = new FilenameStamp()
        val types = ContestTypes.configured
        val config = ContestConfig(types("ARRL"), Callsign("WM9W"), 5, "A", "IL")
        val contests = new ContestConfigManager(files, stamp, dispatcher, 5)
        contests.setConfig(config)
        val builder = new BandModeBuilder
        val starts = new ContestStartManager(files, transport, dispatcher)
        // The application normally initializes this singleton before constructing its store.
        if NodeIdentityManager._ourNodeIdentity == null then
          NodeIdentityManager._ourNodeIdentity = NodeIdentity("127.0.0.1", 8080, "integration-test", "integration-test")
        val status = new LocalNodeStatus(new StationConfigManager(files, startup),
          new SelectedBandModeManager(files, builder, startup), contests, starts, mock(classOf[SwarmData]))
        val identity = mock(classOf[NodeIdentityManager])
        when(identity.current).thenReturn(NodeIdentityManager.nodeIdentity)
        val store = new QsoStore(files, transport, startup, starts, stamp, status,
          new ContestScoringService(), dispatcher)
        files.save("bands.json", Seq(Band.Band_40m, Band.Band_20m, Band.Band_6m, Band.Band_23cm))
        files.save("modes.json", Seq(Mode.CW, Mode.PH, Mode.DIGI))
        val generator = new BigQsosGenerator(store, builder, identity, contests,
          new ContestCatalog(types), new AvailableBandsManager(files), new AvailableModesManager(files))
        val now = Instant.parse("2026-10-25T18:23:45.123456789Z")
        timed("generate and save 100 QSOs") {
          generator.qsos(100, 24, "WA9", now)
          assertEquals(store.size, 100)
          os.write(artifactDirectory / "original.json", store.all.map(_.asJsonCompact).mkString("", "\n", "\n"))
        }
        val original = os.read.lines(artifactDirectory / "original.json").map(line => decode[Qso](line).fold(throw _, identity => identity))
        assertEquals(original.map(_.uuid).distinct.size, 100)
        assertEquals(original.map(_.dupCriterion).distinct.size, 100)
        def exportFile(name: String)(write: ByteArrayOutputStream => Unit): Unit =
          val output = new ByteArrayOutputStream()
          try
            write(output)
            os.write(artifactDirectory / name, output.toByteArray)
          finally output.close()
        timed("export ADIF and Cabrillo") {
          exportFile("contacts.adi")(new AdifExporter(contests, store).doExport)
          exportFile("contacts.cbr")(new CabrilloExporter(store, contests).doExport)
        }
        timed("validate Cabrillo") {
          val lines = os.read.lines(artifactDirectory / "contacts.cbr")
          assertEquals(lines.head, "START-OF-LOG: 3.0")
          assertEquals(lines.last, "END-OF-LOG:")
          assert(lines.contains("CALLSIGN: WM9W"))
          assert(lines.contains("CONTEST: ARRL-FD"))
          assert(lines.contains("LOCATION: IL"))
          val records = lines.filter(_.startsWith("QSO:"))
          assertEquals(records.size, 100)
          val frequencies = Map(Band.Band_40m -> "7030", Band.Band_20m -> "14035",
            Band.Band_6m -> "50", Band.Band_23cm -> "1.2G")
          val modes = Map(Mode.CW -> "CW", Mode.PH -> "PH", Mode.DIGI -> "DG")
          val date = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC)
          val time = DateTimeFormatter.ofPattern("HHmm").withZone(ZoneOffset.UTC)
          records.zip(original.sortBy(_.stamp)).foreach { (line, qso) =>
            assertEquals(line.trim.split("\\s+").toSeq, Seq("QSO:", frequencies(qso.bandMode.band),
              modes(qso.bandMode.mode), date.format(qso.stamp), time.format(qso.stamp),
              "WM9W", "5A", "IL", qso.callsign.value, qso.exchange.fdClass.toString, qso.exchange.sectionCode))
          }
        }
        timed("clear store") {
          store.archiveAndClear()
          assertEquals(store.size, 0)
          assertEquals(store.qsoCollection.size, 0)
          assert(!os.exists(artifactDirectory / "qsosJournal.json"))
        }
        val importer = new QsoFileImporter(store, types)
        timed("import 100 ADIF QSOs") {
          val result = importer.importFile((artifactDirectory / "contacts.adi").toIO, now = now)
          assertEquals(result.totalRecords, 100)
          assertEquals(result.added, 100)
          assertEquals(result.duplicates, 0)
          assertEquals(result.ignoredOlder, 0)
          assertEquals(result.parseErrors, Nil)
        }
        timed("compare saved JSON, imported QSOs and journal") {
          val expected = original.map(qso => qso.copy(
            stamp = qso.stamp.truncatedTo(ChronoUnit.SECONDS),
            qsoMetadata = QsoMetadata.adifImportQsoMetadata(types("ARRL"))
              .withOperator(qso.qsoMetadata.station.operator))).sortBy(_.uuid)
          assertEquals(store.all.sortBy(_.uuid), expected)
          assertEquals(store.qsoCollection.size, 100)
          val journal = os.read.lines(artifactDirectory / "qsosJournal.json").map(line => decode[Qso](line).fold(throw _, value => value))
          assertEquals(journal.sortBy(_.uuid), expected)
        }
        timed("repeat import rejects duplicates") {
          val result = importer.importFile((artifactDirectory / "contacts.adi").toIO, now = now)
          assertEquals(result.added, 0)
          assertEquals(result.duplicates, 100)
          assertEquals(store.size, 100)
          assertEquals(os.read.lines(artifactDirectory / "qsosJournal.json").size, 100)
        }
      }
      passed = true
    finally
      println(f"QsoExportImportTest total: ${(System.nanoTime() - started) / 1e6}%.1f ms")
      if passed then os.remove.all(artifactDirectory)
      else println(s"QsoExportImportTest failure artifacts: $artifactDirectory")

