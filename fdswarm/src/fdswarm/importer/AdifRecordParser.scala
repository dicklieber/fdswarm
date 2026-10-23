/*
 * Copyright (c) 2026. Dick Lieber, WA9NNN
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package fdswarm.importer

import fdswarm.QsoMetadataManager
import fdswarm.model.*
import fdswarm.rigctl.ModeMappingManager
import fdswarm.util.Ids
import jakarta.inject.{Inject, Singleton}

import java.time.format.DateTimeFormatter
import java.time.{Instant, LocalDateTime, ZoneOffset}
import scala.util.Try

/** Parses a single ADIF record string into an optional [[Qso]].
  *
  * Extracted as a standalone class to enable unit testing of the record-level
  * parsing logic independently of I/O concerns in [[AdifParser]].
  *
  * When used with dependency injection, [[ModeMappingManager.fromString]] is used
  * as the mode resolver, making the radio→[[Mode]] mapping user-configurable.
  * For non-DI contexts (tests, [[AdifParser]]), the primary constructor defaults
  * to [[AdifRecordParser.defaultModeFromString]], which replicates the built-in
  * radio-mode patterns.
  */
@Singleton
class AdifRecordParser(
    getMetadata: () => QsoMetadata,
    modeFromString: String => Mode = AdifRecordParser.defaultModeFromString
):
  import AdifRecordParser.FieldRx

  @Inject() def this(qsoMetadataManager: QsoMetadataManager, modeMappingManager: ModeMappingManager) =
    this(() => qsoMetadataManager.current, modeMappingManager.fromString)

  /** Parses a single ADIF record string into a [[Try]] of [[Qso]].
    * MODE can be replaced by APP_FDSWARM_MODE for generic digital exports.
    */
  def parseRecord(record: String): Try[Qso] =
    val qsoMetadata = getMetadata()
    val fields = extractFields(record)
    Try {
      val callStr  = fields("CALL")
      val bandStr  = fields("BAND")
      val mode = fields.get("MODE") match
        // Older fdswarm exports used DATA, which is not a standard ADIF mode.
        case Some(raw) if raw.trim.equalsIgnoreCase("DATA") => Mode.DIGI
        case Some(raw) => modeFromString(raw)
        case None =>
          val stored = fields("APP_FDSWARM_MODE").trim.toUpperCase
          Mode.values.find(_.toString == stored)
            .getOrElse(throw new IllegalArgumentException(s"Unknown fdswarm mode: $stored"))
      val classStr = fields("CLASS").toUpperCase
      val sectStr  = fields("ARRL_SECT").toUpperCase
      val callsign = Callsign(callStr)
      val bandMode = new BandMode(Band.fromString(bandStr), mode)
      val exchange = Exchange(FdClass(classStr), sectStr)
      val stamp    = parseStamp(fields)
      val uuid     = fields.getOrElse("APP_FDSWARM_UUID", Ids.generateId())
      val operatorOpt = fields.get("OPERATOR").orElse(fields.get("STATION_CALLSIGN"))
      val metadata = operatorOpt.map(op => qsoMetadata.withOperator(Callsign(op))).getOrElse(qsoMetadata)
      Qso(callsign, exchange, bandMode, metadata, stamp, uuid)
    }

  /** Extracts all <FIELD:len>value entries from a record into an uppercase-keyed map. */
  def extractFields(record: String): Map[String, String] =
    val builder = Map.newBuilder[String, String]
    var pos = 0
    while pos < record.length do
      FieldRx.findFirstMatchIn(record.substring(pos)) match
        case None => pos = record.length
        case Some(m) =>
          val name     = m.group(1).toUpperCase
          val length   = m.group(2).toInt
          val start    = pos + m.end
          val declared = math.min(start + length, record.length)
          // Extend past the declared length to handle underdeclared fields (e.g. <band:2>40M).
          // Stop at the next field delimiter '<' or at whitespace.
          var end = declared
          while end < record.length && record(end) != '<' && !record(end).isWhitespace do
            end += 1
          builder += name -> record.substring(start, end).trim
          pos = end
    builder.result()

  private def parseStamp(fields: Map[String, String]): Instant =
    (fields.get("QSO_DATE"), fields.get("TIME_ON")) match
      case (Some(date), Some(time)) =>
        scala.util.Try {
          val dt = LocalDateTime.parse(
            date.trim + time.trim.padTo(6, '0'),
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
          )
          dt.toInstant(ZoneOffset.UTC)
        }.getOrElse(Instant.now())
      case _ => Instant.now()

object AdifRecordParser:
  private val FieldRx = """<([^:>]+):(\d+)(?::[^>]*)?>""".r

  private val cwRx   = "CW.*".r
  private val digiRx = "(?:PKT|RTTY|DI|FT|JT|JS8|WSPR|PSK|MSK|MFSK).*".r

  /** Default radio-mode resolver used in non-DI contexts (file import, tests).
    * Matches the built-in patterns from `reference.conf`.  When DI is active,
    * [[ModeMappingManager.fromString]] replaces this so mappings are user-configurable.
    */
  val defaultModeFromString: String => Mode = mode =>
    mode.trim.toUpperCase match
      case "CW"     => Mode.CW
      case "PH"     => Mode.PH
      case "DIGI"   => Mode.DIGI
      case cwRx()   => Mode.CW
      case digiRx() => Mode.DIGI
      case _        => Mode.PH
