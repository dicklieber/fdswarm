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

package fdswarm.wsjtx

import fdswarm.fx.utils.StyledDialog
import fdswarm.fx.utils.editor.CaseClassPropertyEditor
import jakarta.inject.Inject
import scalafx.Includes.*
import scalafx.scene.control.ButtonType

/** Dialog for configuring the WSJT-X multicast listener.
  *
  * Uses [[CaseClassPropertyEditor]] to auto-generate a form from [[WsjtxConfig]].
  * Pressing OK persists the new config via [[WsjtxConfigManager]] and applies it
  * immediately by calling [[WsjtxListener.restart]].
  */
class WsjtxConfigDialog @Inject() (
    wsjtxConfigManager: WsjtxConfigManager,
    wsjtxListener: WsjtxListener
) extends StyledDialog[ButtonType]:

  title    = "WSJT-X Configuration"
  resizable = true

  private val editor = new CaseClassPropertyEditor[WsjtxConfig](wsjtxConfigManager.wsjtxConfig.value)

  dialogPane().content     = editor.vertical
  dialogPane().buttonTypes = Seq(ButtonType.OK, ButtonType.Cancel)

  dialogPane()
    .lookupButton(ButtonType.OK)
    .addEventFilter(
      javafx.event.ActionEvent.ACTION,
      (_: javafx.event.ActionEvent) =>
        wsjtxConfigManager.save(editor.finish())
        wsjtxListener.restart()
    )
