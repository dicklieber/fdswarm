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

package fdswarm.fx.contest

import fdswarm.model.Callsign
import munit.FunSuite
import scalafx.scene.control.Label
import scalafx.scene.layout.GridPane

class ContestConfigTest extends FunSuite:

  test("exchange formats transmitters class section") {
    val config = ContestConfig(ContestTypes.configured("WFD"), Callsign("W1AW"), 2, "O", "CT")
    assertEquals(config.exchange, "2O CT")
  }

  test("weAre formats with phonetics") {
    val config = ContestConfig(ContestTypes.configured("WFD"), Callsign("W1AW"), 2, "O", "CT")
    val weAre = config.weAre(true)
    assertEquals(weAre, "We are Whiskey One Alpha Whiskey 2 Oscar Charlie Tango")
  }

  test("weAre handles null callsign without throwing") {
    val config = ContestConfig(
      contestType = ContestTypes.configured("WFD"),
      ourCallsign = null,
      transmitters = 2,
      ourClass = "O",
      ourSection = "CT"
    )
    val weAre = config.weAre(
      usePhonetic = false
    )
    assertEquals(
      weAre,
      "We are  2O CT"
    )
  }

  test("exchange single transmitter") {
    val config = ContestConfig(ContestTypes.configured("ARRL"), Callsign("K1ABC"), 1, "1A", "NH")
    assertEquals(config.exchange, "11A NH")
  }



  test("ContestConfig implements ContestConfigFields") {
    val config: ContestConfigFields = ContestConfig(ContestTypes.configured("WFD"), Callsign("W1AW"), 2, "O", "CT")
    assertEquals(config.contestType, ContestTypes.configured("WFD"))
    assertEquals(config.ourCallsign, Callsign("W1AW"))
    assertEquals(config.transmitters, 2)
    assertEquals(config.ourClass, "O")
    assertEquals(config.ourSection, "CT")
  }


  test("ContestConfig rejects a contest not in the configured definitions"):
    val unknown = ContestTypes.configured("WFD").copy(name = "Unknown")
    intercept[IllegalArgumentException] {
      ContestConfig(unknown, Callsign("W1AW"), 1, "O", "CT")
    }

  test("ContestConfig rejects a modified definition with an existing name"):
    val modified = ContestTypes.configured("WFD").copy(cabrilloContestName = "Other")
    intercept[IllegalArgumentException] {
      ContestConfig(modified, Callsign("W1AW"), 1, "O", "CT")
    }

  test("class descriptions come from contest resources"):
    assertEquals(ContestConfig.classDescription(ContestTypes.configured("WFD"), "2O"), Some("Outdoor"))
    assertEquals(ContestConfig.classDescription(ContestTypes.configured("ARRL"), "A"),
      Some("Club / group portable station (not at a home location)"))
    assertEquals(ContestConfig.classDescription(ContestTypes.configured("WFD"), "Z"), None)
