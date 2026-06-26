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

import fdswarm.fx.contest.ContestType
import fdswarm.fx.station.StationConfig
import fdswarm.model.*
import fdswarm.util.{Ids, NodeIdentity}

import java.time.{Instant, LocalDateTime, ZoneOffset}
import java.time.format.DateTimeFormatter

/** Parses a single ADIF record string into an optional [[Qso]].
  *
  * Extracted as a standalone class to enable unit testing of the record-level
  * parsing logic independently of I/O concerns in [[AdifParser]].
  *
  * @param node    node identity to embed in imported QSOs
  * @param contest contest type to embed in imported QSOs
  */
class AdifRecordParser(
    node: NodeIdentity = NodeIdentity.mockNodeIdentity,
    contest: ContestType = ContestType.ARRL):

  private val FieldRx = """<([^:>]+):(\d+)(?::[^>]*)?>""".r

  /** Parses a single ADIF record string into an optional [[Qso]].
    * Returns [[None]] if any required field (CALL, BAND, MODE, CLASS, ARRL_SECT) is missing.
    */
  def parseRecord(record: String): Option[Qso] =
    val fields = extractFields(record)

    for
      callStr  <- fields.get("CALL")
      bandStr  <- fields.get("BAND")
      modeStr  <- fields.get("MODE")
      classStr <- fields.get("CLASS")
      sectStr  <- fields.get("ARRL_SECT")
    yield
      val callsign = Callsign(callStr)
      val bandMode = BandMode(bandStr, modeStr)
      val exchange = Exchange(FdClass(classStr), sectStr)
      val stamp    = parseStamp(fields)
      val uuid     = fields.getOrElse("APP_FDSWARM_UUID", Ids.generateId())
      val operator = Callsign(fields.getOrElse("OPERATOR", fields.getOrElse("STATION_CALLSIGN", "")))
      val metadata = QsoMetadata(StationConfig(operator = operator), node, contest)
      Qso(callsign, exchange, bandMode, metadata, stamp, uuid)

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
