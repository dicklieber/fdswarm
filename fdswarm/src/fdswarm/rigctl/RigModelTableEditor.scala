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

package fdswarm.rigctl

import fdswarm.fx.utils.BootstrapIcons
import fdswarm.fx.utils.editor.CustomFieldEditor
import scalafx.Includes.*
import scalafx.application.Platform
import scalafx.beans.property.IntegerProperty
import scalafx.scene.Node
import scalafx.scene.control.Button
import scalafx.scene.paint.Color

/** A [[CustomFieldEditor]] for the `rigModel` field in [[RigCtlConfig]].
  *
  * Renders as a compact button showing the currently-selected rig's
  * manufacturer and model name.  Clicking the button opens [[RigPickerDialog]],
  * a modal table where the user selects a rig by double-clicking a row.
  *
  * Register with:
  * {{{
  *   editor.setCustomEditor("rigModel", new RigModelTableEditor(rigCtlManager))
  * }}}
  */
class RigModelTableEditor(rigCtlManager: RigCtlManager) extends CustomFieldEditor:

  /** Always valid — every model number is acceptable. */
  override def isValid: Boolean = true

  override def editor(fieldProperty: Any): Node =
    val rigModelProp = fieldProperty.asInstanceOf[IntegerProperty]

    val pickerBtn = new Button(s"Model #${rigModelProp.value}"):
      graphic  = BootstrapIcons.svgPath("search", 13, Color.White)
      styleClass += "btn-secondary"
      maxWidth = Double.MaxValue

    // Resolve the saved model number to a display name in the background.
    val t = new Thread(
      () =>
        rigCtlManager.listRigs() match
          case Right(rigs) =>
            rigs.find(_.modelNumber == rigModelProp.value).foreach { rig =>
              Platform.runLater { pickerBtn.text = rigLabel(rig) }
            }
          case Left(_) => ()
      ,
      "rigctl-init-resolve"
    )
    t.setDaemon(true)
    t.start()

    pickerBtn.onAction = _ =>
      val owner = pickerBtn.scene.value.window.value
      RigPickerDialog.show(ownerWindow = owner, rigModelProp.value, rigCtlManager) { rig =>
        rigModelProp.value = rig.modelNumber
        pickerBtn.text     = rigLabel(rig)
      }

    pickerBtn

  private def rigLabel(rig: RigInfo): String =
    s"${rig.manufacturer}  —  ${rig.model}"
