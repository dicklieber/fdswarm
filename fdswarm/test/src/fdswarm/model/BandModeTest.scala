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

import io.circe.parser.decode
import io.circe.syntax.*
import munit.FunSuite
import sttp.tapir.Schema

class BandModeTest extends FunSuite:

  test("BandMode toString should format correctly"):
    assertEquals(BandMode("40M", "CW").toString, "40m CW")
    assertEquals(BandMode("20M", "PH").toString, "20m PH")

  test("BandMode apply(String) should parse correctly"):
    assertEquals(BandMode("40M CW"), BandMode("40M", "CW"))
    assertEquals(BandMode("20M PH"), BandMode("20M", "PH"))
    assertEquals(BandMode("2M FM"), BandMode("2M", "PH"))
    assertEquals(BandMode("1.25M SSB"), BandMode("1.25M", "PH"))
    assertEquals(BandMode("70cm DIGI"), BandMode("70cm", "DIGI"))
    assertEquals(BandMode("4m DIGI"), BandMode("4m", "DIGI"))

  test("BandMode falls back to phone for noncanonical mode strings"):
    assertEquals(BandMode("70cm DI"), BandMode("70cm", "PH"))

  test("BandMode apply(String) with extra spaces should parse correctly"):
    assertEquals(BandMode(" 40M CW "), BandMode("40M", "CW"))
    assertEquals(BandMode("40M  CW"), BandMode("40M", "CW"))

  test("BandMode apply(String) should throw IllegalArgumentException for invalid input"):
    intercept[IllegalArgumentException] {
      BandMode("40MCW")
    }
    intercept[IllegalArgumentException] {
      BandMode("40M")
    }
    intercept[IllegalArgumentException] {
      BandMode("")
    }

  test("BandMode Circe round trip"):
    val bandMode = BandMode("20M", "PH")
    val json = bandMode.asJson.noSpaces
    assertEquals(json, "\"20m PH\"")
    val decoded = decode[BandMode](json).getOrElse(fail("failed to decode"))
    assertEquals(decoded, bandMode)

  test("BandMode Tapir schema should be a string"):
    val schema = summon[Schema[BandMode]]
    assertEquals(schema, Schema.string)
