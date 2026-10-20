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
    assert(result.contains("CONTEST: WFDA-CONTEST"))
    assert(result.contains("QSO: 14035 CW 2026-02-25 1400 W1AW         1A  CT  K1ABC        1O  CT "))
    assert(result.contains("END-OF-LOG:"))

  test("cabrilloFieldName is used correctly"):
    val resultWfd = exporter.exportQsos(Seq.empty, testConfig)
    assert(resultWfd.contains("CONTEST: WFDA-CONTEST"))
    
    val arrlConfig = testConfig.copy(contestType = ContestTypes.configured("ARRL"))
    val resultArrl = exporter.exportQsos(Seq.empty, arrlConfig)
    assert(resultArrl.contains("CONTEST: ARRL-FIELD-DAY"))
