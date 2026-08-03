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

import fdswarm.model.*
import munit.FunSuite
import java.time.Instant

class SummaryExporterTest extends FunSuite:
  val qsoMetadata = fdswarm.model.QsoMetadata.testQsoMetadata

  test("SummaryExporter should generate correct format"):
    val exporter = new SummaryExporter(null)
    val qsos = Seq(
      Qso(Callsign("K8LOW"), Exchange(FdClass("1A"), "IL"), BandMode("160m", "CW"), qsoMetadata, Instant.now()),
      Qso(Callsign("W8AB"), Exchange(FdClass("1A"), "IL"), BandMode("80m", "CW"), qsoMetadata, Instant.now()),
      Qso(Callsign("AA5FA"), Exchange(FdClass("1A"), "IL"), BandMode("80m", "DIGI"), qsoMetadata, Instant.now()),
      Qso(Callsign("AB8I"), Exchange(FdClass("1A"), "IL"), BandMode("80m", "PH"), qsoMetadata, Instant.now()),
      Qso(Callsign("W9YRC"), Exchange(FdClass("1A"), "IL"), BandMode("1.25m", "CW"), qsoMetadata, Instant.now()),
      Qso(Callsign("N3FJP"), Exchange(FdClass("1A"), "IL"), BandMode("6cm", "DIGI"), qsoMetadata, Instant.now())
    )
    
    val result = exporter.exportQsos(qsos)
    
    // Check Header
    assert(result.contains("Total Contacts by Band and Mode:"))
    assert(result.contains("Band       CW   Power     Dig   Power   Phone   Power   Total       %"))
    
    // Total count = 6
    // CW: 160(1), 80(1), 1.25(1) = 3
    // DIGI: 80(1), 6cm(1) = 2
    // PH: 80(1) = 1
    
    // Check 160 line
    // 1/6 = 17%
    assert(result.contains(" 160        1"))
    
    // Check 80 line
    // 3/6 = 50%
    assert(result.contains("  80        1               1               1               3      50"))

    // Check 1.25 line
    // 1/6 = 17%
    assert(result.contains("1.25        1               0               0               1      17"))

    // Check 6cm line
    // 1/6 = 17%
    assert(result.contains(" 6cm        0               1               0               1      17"))
    
    // Check Total line
    assert(result.contains("Total       3               2               1               6     100"))
