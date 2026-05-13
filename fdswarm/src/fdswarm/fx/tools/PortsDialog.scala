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

package fdswarm.fx.tools

import fdswarm.Ports
import jakarta.inject.{Inject, Singleton}
import scalafx.Includes.*
import scalafx.collections.ObservableBuffer
import scalafx.geometry.Insets
import scalafx.scene.control.*
import scalafx.scene.layout.VBox
import scalafx.stage.Window

@Singleton
final class PortsDialog @Inject() ():
  def show(ownerWindow: Window): Unit =
    val ports = ObservableBuffer.from(Ports.ports())
    val portList = new ListView[String](ports):
      prefHeight = 300
      prefWidth = 240

    val dialog = new Dialog[Unit]:
      title = "Ports"
      headerText = "Known fdswarm ports"
      initOwner(ownerWindow)

    dialog.dialogPane().buttonTypes = Seq(ButtonType.Close)
    dialog.dialogPane().content = new VBox:
      spacing = 10
      padding = Insets(10)
      prefWidth = 260
      prefHeight = 340
      children = Seq(portList)

    dialog.showAndWait()
