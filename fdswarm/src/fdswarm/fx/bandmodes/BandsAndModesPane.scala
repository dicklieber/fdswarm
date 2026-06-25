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

package fdswarm.fx.bandmodes

import fdswarm.bandmodes.BandModeMatrixPane
import fdswarm.fx.components.{ButtonVariant, DialogButton}
import fdswarm.fx.bands.{BandCheckBoxPane, ModeCheckBoxPane}
import jakarta.inject.{Inject, Singleton}
import scalafx.Includes.*
import scalafx.geometry.Insets
import scalafx.scene.control.*
import scalafx.scene.layout.*
import scalafx.stage.Window

/** 3-pane editor:
 * 1) band checkboxes (from AvailableBandsStore.availableBands.bandNames)
 * 2) mode checkboxes (from application.conf fdswarm.modes)
 * 3) matrix pane (BandModeMatrixPane) showing only selected bands/modes
 *
 * Persists:
 * - selected bands/modes + enabled matrix via BandModeStore (bandmodes.json)
 * - selected cell via SelectedBandModeStore (selected-bandmode.json) through matrix pane
 */
@Singleton
final class BandsAndModesPane @Inject()(
                                         bandCheckBoxPane: BandCheckBoxPane,
                                         modeCheckBoxPane: ModeCheckBoxPane,
                                         matrixPane: BandModeMatrixPane,
                                         contestManager: fdswarm.fx.contest.ContestConfigManager
                                      ) extends GridPane:

  matrixPane.onConfigRequest = Some(() => {
    Option(matrixPane.node.scene.value).map(_.getWindow).foreach { window =>
      this.show(window)
    }
  })

  padding = Insets(10)
  hgap = 10
  vgap = 10

  add(modeCheckBoxPane.node, 0, 1)
  add(bandCheckBoxPane.node, 1, 0)
  matrixPane.showConfigButton.value = false
  add(matrixPane.node, 1,1)

  contestManager.hasConfiguration.onChange { (_, _, hasConfig) =>
    if hasConfig then matrixPane.buildGrid()
  }
  if contestManager.hasConfiguration.value then matrixPane.buildGrid()

  def show(ownerWindow: Window): Unit =
    val dialog = new Dialog[Unit] {
      initOwner(ownerWindow)
      title = "Band / Mode Manager"
      headerText = "Manage Bands and Modes"
    }
    dialog.dialogPane().content = this
    dialog.dialogPane().buttonTypes = Seq(ButtonType.Close)
    val autoCloseBtn = dialog.dialogPane().delegate.lookupButton(ButtonType.Close.delegate)
    autoCloseBtn.setVisible(false)
    autoCloseBtn.setManaged(false)

    val closeButton = DialogButton("Close", "x-circle", ButtonVariant.Secondary, "Close")
    closeButton.onAction = _ => dialog.close()

    val buttonBar = dialog.dialogPane().delegate.lookup(".button-bar")
      .asInstanceOf[javafx.scene.control.ButtonBar]
    javafx.scene.control.ButtonBar.setButtonData(closeButton.delegate,
      javafx.scene.control.ButtonBar.ButtonData.CANCEL_CLOSE)
    buttonBar.getButtons.add(closeButton.delegate)

    dialog.showAndWait()
