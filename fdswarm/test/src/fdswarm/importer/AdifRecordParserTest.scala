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

import fdswarm.fx.contest.ContestTypes

import fdswarm.model.{Band, Mode}
import munit.FunSuite

import java.time.Instant

class AdifRecordParserTest extends FunSuite:

  private val parser = AdifRecordParser(() => fdswarm.model.QsoMetadata.adifImportQsoMetadata(ContestTypes.configured("ARRL")))

  /** Replicates AdifParser's body-splitting logic to obtain raw record strings from a file. */
  private def recordsFromFile(path: os.Path): Seq[String] =
    val content = os.read(path)
    val eohIdx  = "(?i)<eoh>".r.findFirstMatchIn(content).map(_.end).getOrElse(0)
    val body    = content.substring(eohIdx)
    body.split("(?i)<eor>").toSeq.map(_.trim).filter(_.nonEmpty)

  private val n3fjpRecords    = recordsFromFile(os.pwd / "testdata" / "ARRL-FD-N3FJP-export.adi")
  private val fieldDayRecords = recordsFromFile(os.pwd / "testdata" / "fieldday.adif")

  // ─── extractFields ────────────────────────────────────────────────────────

  test("extractFields: extracts all fields from N3FJP first record"):
    val fields = parser.extractFields(n3fjpRecords.head)
    assertEquals(fields.get("CALL"), Some("K9DRS"))
    assertEquals(fields.get("BAND"), Some("15M"))
    assertEquals(fields.get("MODE"), Some("CW"))
    assertEquals(fields.get("CLASS"), Some("1H"))
    assertEquals(fields.get("ARRL_SECT"), Some("IL"))
    assertEquals(fields.get("OPERATOR"), Some("WA9NNN"))

  test("extractFields: lowercase field names are normalised to uppercase"):
    val fields = parser.extractFields(fieldDayRecords.head)
    assertEquals(fields.get("CALL"), Some("W1AW"))
    assertEquals(fields.get("BAND"), Some("20M"))
    assertEquals(fields.get("MODE"), Some("PHON"))

  test("extractFields: handles underdeclared band field (fieldday second record <band:2>40M)"):
    // <band:2>40M declares length 2 but value is 3 chars — parser must extend past declared end
    val fields = parser.extractFields(fieldDayRecords(1))
    assertEquals(fields.get("BAND"), Some("40M"))

  // ─── parseRecord — N3FJP records ─────────────────────────────────────────

  test("parseRecord: N3FJP first record — K9DRS 15M CW"):
    val qso = parser.parseRecord(n3fjpRecords.head).get
    assertEquals(qso.callsign.value, "K9DRS")
    assertEquals(qso.bandMode.band.name, "15m")
    assertEquals(qso.bandMode.mode, Mode.CW)
    assertEquals(qso.exchange.fdClass.transmitters, 1)
    assertEquals(qso.exchange.fdClass.classLetter, 'H')
    assertEquals(qso.exchange.sectionCode, "IL")
    assertEquals(qso.qsoMetadata.station.operator.value, "WA9NNN")
    assertEquals(qso.stamp, Instant.parse("2026-06-26T15:13:09Z"))

  test("parseRecord: N3FJP second record — WA9DEW 15M CW"):
    val qso = parser.parseRecord(n3fjpRecords(1)).get
    assertEquals(qso.callsign.value, "WA9DEW")
    assertEquals(qso.bandMode.band.name, "15m")
    assertEquals(qso.bandMode.mode, Mode.CW)
    assertEquals(qso.exchange.fdClass.transmitters, 1)
    assertEquals(qso.exchange.fdClass.classLetter, 'O')
    assertEquals(qso.exchange.sectionCode, "IN")
    assertEquals(qso.qsoMetadata.station.operator.value, "WA9NNN")
    assertEquals(qso.stamp, Instant.parse("2026-06-26T15:15:51Z"))

  // ─── parseRecord — fieldday records ──────────────────────────────────────

  test("parseRecord: fieldday first record — W1AW 20M PHON"):
    val qso = parser.parseRecord(fieldDayRecords.head).get
    assertEquals(qso.callsign.value, "W1AW")
    assertEquals(qso.bandMode.band.name, "20m")
    assertEquals(qso.bandMode.mode, Mode.PH)
    assertEquals(qso.exchange.fdClass.transmitters, 2)
    assertEquals(qso.exchange.fdClass.classLetter, 'A')
    assertEquals(qso.exchange.sectionCode, "IL")
    assertEquals(qso.stamp, Instant.parse("2026-06-27T18:15:23Z"))

  test("parseRecord: fieldday second record — N0CALL 40M PHON with underdeclared band"):
    val qso = parser.parseRecord(fieldDayRecords(1)).get
    assertEquals(qso.callsign.value, "N0CALL")
    assertEquals(qso.bandMode.band.name, "40m")
    assertEquals(qso.bandMode.mode, Mode.PH)
    assertEquals(qso.exchange.fdClass.transmitters, 1)
    assertEquals(qso.exchange.fdClass.classLetter, 'B')
    assertEquals(qso.exchange.sectionCode, "LA")
    assertEquals(qso.stamp, Instant.parse("2026-06-27T18:22:45Z"))

  // ─── parseRecord — edge cases ─────────────────────────────────────────────

  test("parseRecord: returns None when required field CALL is missing"):
    val record = "<BAND:3>15M<MODE:2>CW<CLASS:2>1H<ARRL_SECT:2>IL"
    assertEquals(parser.parseRecord(record).isFailure, true)

  test("parseRecord: returns None when required field ARRL_SECT is missing"):
    val record = "<CALL:5>K9DRS<BAND:3>15M<MODE:2>CW<CLASS:2>1H"
    assertEquals(parser.parseRecord(record).isFailure, true)

  test("parseRecord: APP_FDSWARM_UUID is preserved when present"):
    val record = "<CALL:5>K9DRS<BAND:3>15M<MODE:2>CW<CLASS:2>1H<ARRL_SECT:2>IL<APP_FDSWARM_UUID:8>TESTID12"
    val qso    = parser.parseRecord(record).get
    assertEquals(qso.uuid, "TESTID12")

  test("parseRecord: falls back to STATION_CALLSIGN when OPERATOR is absent"):
    val record = "<CALL:5>K9DRS<BAND:3>15M<MODE:2>CW<CLASS:2>1H<ARRL_SECT:2>IL<STATION_CALLSIGN:6>WA9NNN"
    val qso    = parser.parseRecord(record).get
    assertEquals(qso.qsoMetadata.station.operator.value, "WA9NNN")

  test("parseRecord: operator is empty string when neither OPERATOR nor STATION_CALLSIGN present"):
    val record = "<CALL:5>K9DRS<BAND:3>15M<MODE:2>CW<CLASS:2>1H<ARRL_SECT:2>IL"
    val qso    = parser.parseRecord(record).get
    assertEquals(qso.qsoMetadata.station.operator.value, "")
  test("parse wsjt-x ADIF"):
    val record = "<call:6>XX4XXX <gridsquare:0> <mode:3>FT8 <rst_sent:0> <rst_rcvd:0> <qso_date:8>20260626 <time_on:6>224845 <qso_date_off:8>20260626 <time_off:6>224950 <band:4>160m <freq:8>1.901397 <station_callsign:6>WA9NNN <my_gridsquare:4>EN61 <tx_pwr:2>50 <comment:22>spooltenna in backyard <contest_id:14>ARRL-FIELD-DAY <SRX_STRING:5>9B IL <class:2>9B <arrl_sect:2>IL <eor>"
    val qso    = parser.parseRecord(record).get
    val band = qso.bandMode.band
    assertEquals(band, Band.Band_160m)
    val mode = qso.bandMode.mode
    assertEquals(mode, Mode.DIGI)
    val station = qso.qsoMetadata.station
    assertEquals(station.operator.value, "WA9NNN")
