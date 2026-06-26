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

class FieldDayAdifParserTest extends FunSuite:

  private val testFile = os.pwd / "testdata" / "fieldday.adif"

  test("AdifParser reads two QSOs from fieldday.adif"):
    val qsos = AdifParser(os.read.inputStream(testFile)).qsos
    assertEquals(qsos.length, 2)

  test("AdifParser parses first QSO (W1AW) correctly"):
    val qso = AdifParser(os.read.inputStream(testFile)).qsos.head.toOption.get

    assertEquals(qso.callsign.value, "W1AW")
    assertEquals(qso.bandMode.band.name, "20m")
    assertEquals(qso.bandMode.mode, Mode.PH)
    assertEquals(qso.exchange.fdClass.transmitters, 2)
    assertEquals(qso.exchange.fdClass.classLetter, 'A')
    assertEquals(qso.exchange.sectionCode, "IL")
    assertEquals(qso.stamp, Instant.parse("2026-06-27T18:15:23Z"))

  test("AdifParser parses second QSO (N0CALL) correctly"):
    val qso = AdifParser(os.read.inputStream(testFile)).qsos(1).toOption.get

    assertEquals(qso.callsign.value, "N0CALL")
    assertEquals(qso.bandMode.band.name, "40m")
    assertEquals(qso.bandMode.mode, Mode.PH)
    assertEquals(qso.exchange.fdClass.transmitters, 1)
    assertEquals(qso.exchange.fdClass.classLetter, 'B')
    assertEquals(qso.exchange.sectionCode, "LA")
    assertEquals(qso.stamp, Instant.parse("2026-06-27T18:22:45Z"))
