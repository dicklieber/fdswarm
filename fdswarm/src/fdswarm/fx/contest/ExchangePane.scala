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

import fdswarm.fx.UserConfig
import jakarta.inject.Inject
import scalafx.Includes.*
import scalafx.beans.binding.Bindings
import scalafx.beans.property.{BooleanProperty, ReadOnlyObjectProperty}
import scalafx.scene.control.{CheckBox, Label}
import scalafx.scene.layout.{Region, VBox}

final class ExchangePane @Inject()(val userConfig: UserConfig) :

  def pane(contestConfig: ReadOnlyObjectProperty[ContestConfig]): VBox = {
    val phoneticProp: BooleanProperty = userConfig.getProperty[BooleanProperty]("usePhonetic")

    val weAreText = Bindings.createStringBinding(
      () => contestConfig.value.weAre(phoneticProp.value),
      contestConfig.delegate,
      phoneticProp.delegate
    )

    new VBox(spacing = 8) {
      fillWidth = true
      maxWidth = Double.MaxValue
      children ++= Seq(
        new CheckBox {
          text = "Use Phonetic"
          minWidth = Region.USE_PREF_SIZE
          selected <==> phoneticProp
        },
        new Label {
          text <== weAreText
          wrapText = true
          minWidth = 0
          maxWidth = Double.MaxValue
          minHeight = Region.USE_PREF_SIZE
        }
      )
    }
  }
