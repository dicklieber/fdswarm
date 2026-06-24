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

package fdswarm.fx.bands

import fdswarm.bands.Modes
import fdswarm.fx.GridColumns
import fdswarm.logging.LazyStructuredLogging
import fdswarm.model.Mode
import jakarta.inject.{Inject, Singleton}
import scalafx.scene.Node
import scalafx.scene.control.{CheckBox, Label, Tooltip}
import scalafx.scene.layout.VBox

@Singleton
final class ModeCheckBoxPane @Inject()() extends LazyStructuredLogging:

  private val checkBoxesByMode: Seq[(Mode, CheckBox)] =
    Mode.values.map(mode =>
      mode -> new CheckBox() {
        text = mode.toString
        selected = Modes.modes.contains(mode)
      }
    ).toSeq

  private val checkBoxes: Seq[CheckBox] = checkBoxesByMode.map(_._2)

  private val headerLabel = new Label("Modes") {
    style = "-fx-cursor: hand; -fx-text-fill: derive(-fx-accent, -20%); -fx-underline: true;"
    tooltip = Tooltip("Click to toggle all modes")
    onMouseClicked = _ => {
      val allChecked = checkBoxes.forall(_.selected.value)
      val newState = !allChecked
      checkBoxes.foreach(_.selected.value = newState)
    }
  }

  val node: Node =
    GridColumns.fieldSet(
      headerLabel,
      new VBox {
        spacing = 12.0
        children = checkBoxes
      }
    )
