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

package fdswarm

import munit.FunSuite
import _root_.io.circe._
import _root_.io.circe.syntax._
import _root_.io.circe.parser._

import fdswarm.model.{BandMode, Callsign}
import fdswarm.{DebugMode, StartupConfig}
import fdswarm.util.Ids.Id

class StartupConfigTest extends FunSuite {

  private val testOperator: Callsign = Callsign("W1AW")
  private val testBandMode: BandMode = BandMode("20m", "CW")
  private val testId: Id = "ABC"

  test("StartupConfig round-trips through JSON with defaults") {
    val original = StartupConfig(operator = testOperator, bandMode = testBandMode, id = testId)
    val jsonStr = original.asJson.noSpaces
    // Verify correct JSON structure generated
    assert(jsonStr.startsWith("{\"enable\":true"))
    assert(jsonStr.contains("\"operator\":\"W1AW\""))
    assert(jsonStr.contains("\"bandMode\":\"20m CW\""))
    assert(jsonStr.contains("\"clearQsos\":false"))
    assert(jsonStr.contains("\"skipInitDiscover\":false"))
    assert(jsonStr.contains("\"debugMode\":\"Off\""))
    assert(jsonStr.contains("\"id\":\"ABC\""))
    val parsed = parse(jsonStr).flatMap(_.as[StartupConfig])
    assertEquals(parsed, Right(original))
    // Existing manager configuration files predate the optional integration settings.
    val legacy = original.asJson.mapObject(_.remove("integrationTest")).noSpaces
    assertEquals(decode[StartupConfig](legacy), Right(original))
  }

  test("StartupConfig round-trips through JSON with non-defaults") {
    val original = StartupConfig(
      enable = false,
      operator = testOperator,
      bandMode = testBandMode,
      clearQsos = true,
      skipInitDiscover = true,
      debugMode = DebugMode.Debug,
      id = testId
    )
    val jsonStr = original.asJson.noSpaces
    // Verify correct JSON structure generated
    assert(jsonStr.startsWith("{\"enable\":false"))
    assert(jsonStr.contains("\"clearQsos\":true"))
    assert(jsonStr.contains("\"skipInitDiscover\":true"))
    assert(jsonStr.contains("\"debugMode\":\"Debug\""))
    val parsed = parse(jsonStr).flatMap(_.as[StartupConfig])
    assertEquals(parsed, Right(original))
  }

  test("StartupConfig round-trips through JSON with Wait debug mode") {
    val original = StartupConfig(
      operator = testOperator,
      bandMode = testBandMode,
      debugMode = DebugMode.Wait,
      id = testId
    )
    val jsonStr = original.asJson.spaces2
    assertEquals(jsonStr, """{
                            |  "enable" : true,
                            |  "operator" : "W1AW",
                            |  "bandMode" : "20m CW",
                            |  "clearQsos" : false,
                            |  "skipInitDiscover" : false,
                            |  "debugMode" : "Wait",
                            |  "id" : "ABC",
                            |  "integrationTest" : null
                            |}""".stripMargin)
    val parsed = parse(jsonStr).flatMap(_.as[StartupConfig])
    assertEquals(parsed, Right(original))
  }

}
