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
 *
 */

package fdswarm.exporter

import fdswarm.fx.contest.ContestTypes

import fdswarm.fx.contest.{ContestConfig, ContestConfigManager, ContestType}
import fdswarm.model.*
import munit.FunSuite
import java.time.Instant

class CabrilloExporterTest extends FunSuite:
  val testConfig = ContestConfig(
    contestType = ContestTypes.configured("WFD"),
    ourCallsign = Callsign("W1AW"),
    transmitters = 1,
    ourClass = "A",
    ourSection = "CT"
  )
  val exporter = new CabrilloExporter(null, null)

  test("exportQsos creates a valid Cabrillo format"):
    val qso1 = Qso(
      callsign = Callsign("K1ABC"),
      exchange = Exchange(FdClass("1O"), "CT"),
      bandMode = BandMode("20m CW"),
      qsoMetadata = fdswarm.model.QsoMetadata.testQsoMetadata,
      stamp = Instant.parse("2026-02-25T14:00:00Z")
    )

    val qsos = Seq(qso1)
    val result = exporter.exportQsos(qsos, testConfig)
    
    assert(result.contains("START-OF-LOG: 3.0"))
    assert(result.contains("CALLSIGN: W1AW"))
    assert(result.contains("CONTEST: WFD\n"))
    assert(result.contains("QSO: 14035 CW 2026-02-25 1400 W1AW         1A  CT  K1ABC        1O  CT "))
    assert(result.contains("END-OF-LOG:"))

  test("cabrilloFieldName is used correctly"):
    val resultWfd = exporter.exportQsos(Seq.empty, testConfig)
    assert(resultWfd.contains("CONTEST: WFD\n"))
    
    val arrlConfig = testConfig.copy(contestType = ContestTypes.configured("ARRL"))
    val resultArrl = exporter.exportQsos(Seq.empty, arrlConfig)
    assert(resultArrl.contains("CONTEST: ARRL-FD\n"))

  test("the reported missing-band contacts retain UTC times and complete Cabrillo fields"):
    val cases = Seq(("WA9AAE", "23cm PH", "2026-10-23T13:59:00.473310Z", "1.2G", "PH", "1359"),
      ("WA9AAB", "4m DIGI", "2026-10-23T15:11:00.473310Z", "70", "DG", "1511"),
      ("WA9AAA", "3cm CW", "2026-10-23T15:35:00.473310Z", "10G", "CW", "1535"))
    cases.foreach { (call, bandMode, stamp, frequency, mode, time) =>
      val qso = Qso(Callsign(call), Exchange(FdClass("1O"), "IL"), BandMode(bandMode),
        fdswarm.model.QsoMetadata.testQsoMetadata, Instant.parse(stamp))
      val fields = exporter.qsoLines(Seq(qso), testConfig).head.trim.split("\\s+").toSeq
      assertEquals(fields, Seq("QSO:", frequency, mode, "2026-10-23", time, "W1AW", "1A", "CT", call, "1O", "IL"))
    }
