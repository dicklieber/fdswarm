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

class DupeSheetExporterTest extends FunSuite:
  val qsoMetadata = fdswarm.model.QsoMetadata.testQsoMetadata

  test("DupeSheetExporter should generate correct format"):
    val exporter = new DupeSheetExporter(null, null, null)
    
    val qsos = Seq(
      Qso(Callsign("K8LOW"), Exchange(FdClass("1A"), "IL"), BandMode("80m", "PH"), qsoMetadata, Instant.now()),
      Qso(Callsign("W8AB"), Exchange(FdClass("1A"), "IL"), BandMode("80m", "PH"), qsoMetadata, Instant.now()),
      Qso(Callsign("AA5FA"), Exchange(FdClass("1A"), "IL"), BandMode("40m", "DIGI"), qsoMetadata, Instant.now()),
      Qso(Callsign("AB8I"), Exchange(FdClass("1A"), "IL"), BandMode("40m", "DIGI"), qsoMetadata, Instant.now())
    )
    
    val result = exporter.exportQsos(qsos, "WM9W", "1H", "IL")
    
    assert(result.contains("Call Used: WM9W  Class: 1H  ARRL Section: IL"))
    assert(result.contains("Dupe Sheet"))
    
    // Check 80 PH section
    assert(result.contains("80  PH"))
    assert(result.contains("K8LOW"))
    assert(result.contains("W8AB"))
    assert(result.contains("80  PH  Total Contacts = 2"))
    
    // Check 40 DIG section
    assert(result.contains("40  DIG"))
    assert(result.contains("AA5FA"))
    assert(result.contains("AB8I"))
    assert(result.contains("40  DIG  Total Contacts = 2"))
    
    // Check sorting: 80 should come before 40 (frequency ascending)
    val index80 = result.indexOf("80  PH")
    val index40 = result.indexOf("40  DIG")
    assert(index80 < index40)

  test("DupeSheetExporter should handle alphabetical sorting of callsigns"):
    val exporter = new DupeSheetExporter(null, null, null)
    val qsos = Seq(
      Qso(Callsign("W9YRC"), Exchange(FdClass("1A"), "IL"), BandMode("80m", "PH"), qsoMetadata, Instant.now()),
      Qso(Callsign("K8LOW"), Exchange(FdClass("1A"), "IL"), BandMode("80m", "PH"), qsoMetadata, Instant.now()),
      Qso(Callsign("W8AB"), Exchange(FdClass("1A"), "IL"), BandMode("80m", "PH"), qsoMetadata, Instant.now())
    )
    
    val result = exporter.exportQsos(qsos, "WM9W", "1H", "IL")
    
    val lines = result.split("\n").map(_.trim).filter(_.nonEmpty)
    val callsignStartIndex = lines.indexOf("80  PH") + 1
    val callsigns = lines.slice(callsignStartIndex, callsignStartIndex + 3)
    
    assertEquals(callsigns.toSeq, Seq("K8LOW", "W8AB", "W9YRC"))
