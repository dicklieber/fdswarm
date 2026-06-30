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

  // Mode.fromString handles only canonical serialised values.
  // Raw radio / WSJT-X strings (CWR, FT8, RTTY, …) are handled by
  // ModeMappingManager.fromString (fdswarm module) — see ModeMappingManagerTest.

  test("CW should produce CW"):
    assertEquals(Mode.fromString("CW"), Mode.CW)

  test("PH should produce PH"):
    assertEquals(Mode.fromString("PH"), Mode.PH)

  test("DIGI should produce DIGI"):
    assertEquals(Mode.fromString("DIGI"), Mode.DIGI)

  test("unknown string falls back to PH"):
    assertEquals(Mode.fromString("SSB"), Mode.PH)
    assertEquals(Mode.fromString("FM"),  Mode.PH)

  test("fromString is case-insensitive"):
    assertEquals(Mode.fromString("cw"),   Mode.CW)
    assertEquals(Mode.fromString("ph"),   Mode.PH)
    assertEquals(Mode.fromString("digi"), Mode.DIGI)
