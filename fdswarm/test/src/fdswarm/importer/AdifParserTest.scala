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

import fdswarm.model.Mode
import munit.FunSuite

import java.time.Instant

class AdifParserTest extends FunSuite:

  private val testFile = os.pwd / "testdata" / "ARRL-FD-N3FJP-export.adi"

  test("AdifParser reads two QSOs from N3FJP export"):
    val qsos = AdifParser(os.read.inputStream(testFile)).qsos
    assertEquals(qsos.length, 2)

  test("AdifParser parses first QSO correctly"):
    val qso = AdifParser(os.read.inputStream(testFile)).qsos.head.get

    assertEquals(qso.callsign.value, "K9DRS")
    assertEquals(qso.bandMode.band.name, "15m")
    assertEquals(qso.bandMode.mode, Mode.CW)
    assertEquals(qso.exchange.fdClass.transmitters, 1)
    assertEquals(qso.exchange.fdClass.classLetter, 'H')
    assertEquals(qso.exchange.sectionCode, "IL")
    assertEquals(qso.qsoMetadata.station.operator.value, "WA9NNN")
    assertEquals(qso.stamp, Instant.parse("2026-06-26T15:13:09Z"))

  test("AdifParser parses second QSO correctly"):
    val qso = AdifParser(os.read.inputStream(testFile)).qsos(1).get

    assertEquals(qso.callsign.value, "WA9DEW")
    assertEquals(qso.bandMode.band.name, "15m")
    assertEquals(qso.bandMode.mode, Mode.CW)
    assertEquals(qso.exchange.fdClass.transmitters, 1)
    assertEquals(qso.exchange.fdClass.classLetter, 'O')
    assertEquals(qso.exchange.sectionCode, "IN")
    assertEquals(qso.qsoMetadata.station.operator.value, "WA9NNN")
    assertEquals(qso.stamp, Instant.parse("2026-06-26T15:15:51Z"))
