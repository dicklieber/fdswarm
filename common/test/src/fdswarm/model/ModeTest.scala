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

package fdswarm.model

import munit.FunSuite

class ModeTest extends FunSuite:

  test("PKTLSB should produce DIGI"):
    assertEquals(Mode.fromString("PKTLSB"), Mode.DIGI)

  test("PKTUSB should produce DIGI"):
    assertEquals(Mode.fromString("PKTUSB"), Mode.DIGI)

  test("RTTY should produce DIGI"):
    assertEquals(Mode.fromString("RTTY"), Mode.DIGI)

  test("CW should produce CW"):
    assertEquals(Mode.fromString("CW"), Mode.CW)

  test("CWR should produce CW"):
    assertEquals(Mode.fromString("CWR"), Mode.CW)

  test("SSB should produce PH"):
    assertEquals(Mode.fromString("SSB"), Mode.PH)

  test("FM should produce PH"):
    assertEquals(Mode.fromString("FM"), Mode.PH)

  test("DFM should produce PH"):
    assertEquals(Mode.fromString("DFM"), Mode.PH)

  test("fromString is case-insensitive"):
    assertEquals(Mode.fromString("pktlsb"), Mode.DIGI)
    assertEquals(Mode.fromString("cw"), Mode.CW)
