package fdswarm.importer

import fdswarm.fx.contest.ContestTypes
import fdswarm.model.*
import fdswarm.store.{QsoStore, StyledMessage}
import io.circe.Json
import io.circe.syntax.*
import munit.FunSuite
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{mock, never, times, verify, verifyNoInteractions, verifyNoMoreInteractions, when}
import java.io.File
import java.time.Instant
import scala.jdk.CollectionConverters.*

class QsoFileImporterTest extends FunSuite:
  private val first = Qso(Callsign("W1AW"), Exchange(FdClass("1O"), "CT"), BandMode("20m CW"),
    QsoMetadata.testQsoMetadata, Instant.parse("2020-01-01T12:00:00.123456789Z"), "first-id")
  private val second = first.copy(uuid = "second-id")

  private def importer(store: QsoStore) = new QsoFileImporter(store, ContestTypes.configured)

  test("journal age filter defaults on, includes the two-month boundary, and can be disabled"):
    val store = mock(classOf[QsoStore])
    when(store.addReplicated(any[Seq[Qso]]())).thenAnswer(invocation =>
      invocation.getArgument[Seq[Qso]](0).size)
    val now = Instant.parse("2026-10-25T18:00:00Z")
    val boundary = first.copy(stamp = Instant.parse("2026-08-25T18:00:00Z"), uuid = "boundary")
    val old = first.copy(stamp = boundary.stamp.minusNanos(1))
    val recent = first.copy(stamp = now.minusSeconds(60), uuid = "recent")
    withFile("journal.json", Seq(old, boundary, recent).map(_.asJsonCompact).mkString("\n")) { file =>
      val filtered = importer(store).importFile(file, now = now)
      assertEquals(filtered.added, 2)
      assertEquals(filtered.ignoredOlder, 1)
      assertEquals(filtered.parsedRecords, 3)
      assertEquals(filtered.duplicates, 0)
      val unfiltered = importer(store).importFile(file, includeOlderThanTwoMonths = true, now = now)
      assertEquals(unfiltered.added, 3)
      assertEquals(unfiltered.ignoredOlder, 0)
    }
    val captured = ArgumentCaptor.forClass(classOf[Seq[Qso]])
    verify(store, times(2)).addReplicated(captured.capture())
    assertEquals(captured.getAllValues.get(0), Seq(boundary, recent))

  test("ADIF age filter skips old contacts before duplicate checks and can be disabled"):
    val store = mock(classOf[QsoStore])
    when(store.add(any[Qso]())).thenReturn(StyledMessage("Added", "addQsoOk"))
    val base = "<CALL:5>K1ABC <BAND:3>20M <MODE:2>CW <CLASS:2>1A <ARRL_SECT:2>IL <QSO_DATE:8>20260627 "
    withFile("contacts.adi", "<EOH>" + base + "<TIME_ON:6>115959 <EOR>" + base + "<TIME_ON:6>120000 <EOR>") { file =>
      val now = Instant.parse("2026-08-27T12:00:00Z")
      val filtered = importer(store).importFile(file, now = now)
      assertEquals(filtered.added, 1)
      assertEquals(filtered.ignoredOlder, 1)
      assertEquals(filtered.duplicates, 0)
      val unfiltered = importer(store).importFile(file, includeOlderThanTwoMonths = true, now = now)
      assertEquals(unfiltered.added, 2)
      assertEquals(unfiltered.ignoredOlder, 0)
    }
    verify(store, times(3)).add(any[Qso]())

  private def withFile[A](name: String, text: String)(run: File => A): A =
    val directory = os.temp.dir(prefix = "fdswarm-import-test-", deleteOnExit = false)
    val path = directory / name
    try
      os.write(path, text)
      run(path.toIO)
    finally os.remove.all(directory)

  test("journals use the replicated batch merge, preserving all data and delegating UUID duplicates"):
    val store = mock(classOf[QsoStore])
    when(store.addReplicated(any[Seq[Qso]]())).thenReturn(2, 0)
    val service = importer(store)
    val records = Seq(first, second, first)
    withFile("qsosJournal.JSON", records.map(_.asJson.noSpaces).mkString("\n")) { file =>
      val initial = service.importFile(file, includeOlderThanTwoMonths = true)
      assertEquals(initial.format, QsoImportFormat.Journal)
      assertEquals(initial.totalRecords, 3)
      assertEquals(initial.parsedRecords, 3)
      assertEquals(initial.added, 2)
      assertEquals(initial.duplicates, 1)
      assertEquals(initial.parseErrors, Nil)
      val repeated = service.importFile(file, includeOlderThanTwoMonths = true)
      assertEquals(repeated.added, 0)
      assertEquals(repeated.duplicates, 3)
    }
    val captured = ArgumentCaptor.forClass(classOf[Seq[Qso]])
    verify(store, times(2)).addReplicated(captured.capture())
    assertEquals(captured.getValue, records)
    // Both UUIDs share a contact's callsign/band/mode; they must reach the sync merge.
    assertEquals(captured.getValue.map(_.uuid), Seq("first-id", "second-id", "first-id"))
    verifyNoMoreInteractions(store)

  test("bad JSON and throwing field decoders report physical line numbers and do not stop later records"):
    val store = mock(classOf[QsoStore])
    when(store.addReplicated(any[Seq[Qso]]())).thenReturn(2)
    val invalid = first.asJson.mapObject(_.add("bandMode", Json.fromString("invalid-band-mode"))).noSpaces
    val malformed = "not JSON " + "x" * 500
    val text = Seq(first.asJson.noSpaces, malformed, "   ", invalid, second.asJson.noSpaces, "{}").mkString("\n")
    var progress = Vector.empty[(Int, Int)]
    withFile("journal.json", text) { file =>
      val result = importer(store).importFile(file, (done, total) => progress :+= (done, total), includeOlderThanTwoMonths = true)
      assertEquals(result.totalRecords, 5)
      assertEquals(result.parsedRecords, 2)
      assertEquals(result.added, 2)
      assertEquals(result.duplicates, 0)
      assertEquals(result.parseErrors.map(_.lineNumber), List(Some(2), Some(4), Some(6)))
      assertEquals(result.parseErrors.head.record, malformed)
      assert(result.parseErrors.head.message.startsWith("Line 2: "))
      assertEquals(progress, (0 to 5).map(_ -> 5).toVector)
    }
    val captured = ArgumentCaptor.forClass(classOf[Seq[Qso]])
    verify(store).addReplicated(captured.capture())
    assertEquals(captured.getValue, Seq(first, second))
    verifyNoMoreInteractions(store)

  test("empty and all-invalid journals never call the merge"):
    val store = mock(classOf[QsoStore])
    withFile("empty.json", "\n  \n") { file =>
      val result = importer(store).importFile(file, includeOlderThanTwoMonths = true)
      assertEquals(result.totalRecords, 0)
      assertEquals(result.added, 0)
      assertEquals(result.duplicates, 0)
      assertEquals(result.parseErrors, Nil)
    }
    withFile("invalid.json", "{}\nnot JSON\n") { file =>
      val result = importer(store).importFile(file, includeOlderThanTwoMonths = true)
      assertEquals(result.totalRecords, 2)
      assertEquals(result.parsedRecords, 0)
      assertEquals(result.parseErrors.size, 2)
    }
    verifyNoInteractions(store)

  test("ADIF keeps the existing single-contact add path and duplicate reporting"):
    val store = mock(classOf[QsoStore])
    when(store.add(any[Qso]())).thenReturn(
      StyledMessage("Added", "addQsoOk"), StyledMessage("Duplicate", "duplicate-qso"))
    val good = "<CALL:5>K1ABC <BAND:3>20M <MODE:2>CW <CLASS:2>1A <ARRL_SECT:2>IL <EOR>"
    val bad = "<BAND:3>20M <MODE:2>CW <CLASS:2>1A <ARRL_SECT:2>IL <EOR>"
    withFile("contacts.AdIf", "<ADIF_VER:5>3.1.4 <EOH>" + good + good + bad) { file =>
      val result = importer(store).importFile(file, includeOlderThanTwoMonths = true)
      assertEquals(result.format, QsoImportFormat.Adif)
      assertEquals(result.totalRecords, 3)
      assertEquals(result.parsedRecords, 2)
      assertEquals(result.added, 1)
      assertEquals(result.duplicates, 1)
      assertEquals(result.parseErrors.size, 1)
      assertEquals(result.duplicateRecords.size, 1)
      assertEquals(result.failedRecords.size, 2)
    }
    verify(store, times(2)).add(any[Qso]())
    verify(store, never).addReplicated(any[Seq[Qso]]())

  test("file extensions choose the parser and unsupported extensions fail before modifying the store"):
    Seq("contacts.adi", "contacts.ADIF").foreach { name =>
      assertEquals(QsoImportFormat.forFile(new File(name)), QsoImportFormat.Adif)
    }
    assertEquals(QsoImportFormat.forFile(new File("qsoJournal.json")), QsoImportFormat.Journal)
    val store = mock(classOf[QsoStore])
    intercept[IllegalArgumentException](importer(store).importFile(new File("contacts.txt")))
    verifyNoInteractions(store)

  test("the structured summary logs the same report counts and identifies each bad journal line"):
    val store = mock(classOf[QsoStore])
    when(store.addReplicated(any[Seq[Qso]]())).thenReturn(1)
    val logger = org.apache.logging.log4j.LogManager.getLogger("importer.QsoFileImporter")
      .asInstanceOf[org.apache.logging.log4j.core.Logger]
    val previousLevel = logger.getLevel
    val appender = new org.apache.logging.log4j.core.test.appender.ListAppender("qso-import-report")
    appender.start()
    logger.addAppender(appender)
    logger.setLevel(org.apache.logging.log4j.Level.INFO)
    try
      withFile("journal.json", first.asJson.noSpaces + "\ninvalid JSON\n") { file =>
        val result = importer(store).importFile(file, includeOlderThanTwoMonths = true)
        val events = appender.getEvents.asScala.toVector
          .map(event => io.circe.parser.parse(event.getMessage.getFormattedMessage).toOption.get)
        val summary = events.find(_.hcursor.get[String]("message").contains("QSO import completed")).get.hcursor
        Seq("totalRecords" -> result.totalRecords, "parsedRecords" -> result.parsedRecords,
          "added" -> result.added, "duplicates" -> result.duplicates,
          "parseErrors" -> result.parseErrors.size).foreach { (key, value) =>
          assertEquals(summary.get[String](key), Right(value.toString))
        }
        val error = events.find(_.hcursor.get[String]("message").contains("QSO import parse error")).get.hcursor
        assertEquals(error.get[String]("line"), Right("2"))
        assertEquals(error.get[String]("record"), Right("invalid JSON"))
      }
    finally
      logger.removeAppender(appender)
      logger.setLevel(previousLevel)
      appender.stop()
