package fdswarm.importer

import fdswarm.fx.contest.ContestTypes
import fdswarm.logging.LazyStructuredLogging
import fdswarm.model.Qso
import fdswarm.store.QsoStore
import io.circe.parser.decode
import jakarta.inject.{Inject, Singleton}
import java.io.{File, FileInputStream}
import java.util.Locale
import scala.io.Source
import scala.util.{Try, Using}

enum QsoImportFormat:
  case Adif, Journal

object QsoImportFormat:
  def forFile(file: File): QsoImportFormat =
    val name = file.getName.toLowerCase(Locale.ROOT)
    if name.endsWith(".json") then QsoImportFormat.Journal
    else if name.endsWith(".adi") || name.endsWith(".adif") then QsoImportFormat.Adif
    else throw new IllegalArgumentException("Select a .json, .adi or .adif file")

case class QsoImportResult(
    format: QsoImportFormat,
    totalRecords: Int,
    added: Int,
    duplicates: Int,
    parseErrors: List[QsoError],
    duplicateRecords: List[QsoError] = Nil):
  def parsedRecords: Int = totalRecords - parseErrors.size
  def failedRecords: List[QsoError] = parseErrors ++ duplicateRecords

/** Journal import adopts the same UUID merge policy as HTTP QSO synchronization. */
@Singleton
final class QsoFileImporter @Inject()(qsoStore: QsoStore, contestTypes: ContestTypes)
    extends LazyStructuredLogging:

  def importFile(file: File, progress: (Int, Int) => Unit = (_, _) => ()): QsoImportResult =
    val result = QsoImportFormat.forFile(file) match
      case QsoImportFormat.Journal => importJournal(file, progress)
      case QsoImportFormat.Adif => importAdif(file, progress)
    logger.info("QSO import completed",
      "file" -> file.getAbsolutePath,
      "format" -> result.format.toString,
      "totalRecords" -> result.totalRecords,
      "parsedRecords" -> result.parsedRecords,
      "added" -> result.added,
      "duplicates" -> result.duplicates,
      "parseErrors" -> result.parseErrors.size)
    result.parseErrors.foreach { error =>
      logger.warn("QSO import parse error", "file" -> file.getAbsolutePath,
        import        "line" -> error.lineNumber, "error" -> error.message, "record" -> error.record)
    }
    result.duplicateRecords.foreach { error =>
      logger.debug("QSO import duplicate", "file" -> file.getAbsolutePath,
        "error" -> error.message, "record" -> error.record)
    }
    result

  private def importJournal(file: File, progress: (Int, Int) => Unit): QsoImportResult =
    val lines = Using.resource(Source.fromFile(file, "UTF-8")) { source =>
      source.getLines().zipWithIndex.filter(_._1.trim.nonEmpty).toVector
    }
    val records = Vector.newBuilder[Qso]
    val errors = List.newBuilder[QsoError]
    progress(0, lines.size)
    lines.zipWithIndex.foreach { case ((line, physicalIndex), index) =>
      // Some field decoders can throw; one bad line must not abort the journal.
      Try(decode[Qso](line)).toEither.flatMap(identity) match
        case Right(qso) => records += qso
        case Left(error) =>
          val exception = error match
            case value: Exception => value
            case value => new RuntimeException(value.getMessage, value)
          errors += QsoError(line, exception, Some(physicalIndex + 1))
      progress(index + 1, lines.size)
    }
    val parsed = records.result()
    // Preserve UUIDs, timestamps and metadata, including records predating contest start,
    // exactly as node synchronization does. The store also handles in-file duplicates.
    val added = if parsed.nonEmpty then qsoStore.addReplicated(parsed) else 0
    QsoImportResult(QsoImportFormat.Journal, lines.size, added, parsed.size - added, errors.result())

  private def importAdif(file: File, progress: (Int, Int) => Unit): QsoImportResult =
    Using.resource(new FileInputStream(file)) { input =>
      // Retain the existing ADIF metadata and callsign/band/mode duplicate policy.
      val parser = new AdifParser(input, contestTypes("ARRL"))
      var processed = 0
      var added = 0
      val duplicates = List.newBuilder[QsoError]
      val errors = List.newBuilder[QsoError]
      progress(0, parser.recordCount)
      parser.process { (raw, result) =>
        result match
          case Right(qso) =>
            val message = qsoStore.add(qso)
            if message.css == "duplicate-qso" then
              duplicates += QsoError(raw, new Exception(message.text))
            else added += 1
          case Left(error) => errors += error
        processed += 1
        progress(processed, parser.recordCount)
      }
      val rejected = duplicates.result()
      QsoImportResult(QsoImportFormat.Adif, parser.recordCount, added, rejected.size, errors.result(), rejected)
    }
