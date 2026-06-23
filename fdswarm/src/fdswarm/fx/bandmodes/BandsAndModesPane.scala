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
import fdswarm.fx.GridColumns
import fdswarm.fx.bands.{BandCheckBoxPane, ModeCheckBoxPane}
import fdswarm.fx.components.{ButtonVariant, DialogButton}
import fdswarm.fx.utils.IconButton
import fdswarm.rigctl.{RigCtlDialog, RigctldPoller}
import jakarta.inject.{Inject, Singleton}
import scalafx.Includes.*
import scalafx.geometry.Insets
import scalafx.scene.control.*
import scalafx.scene.layout.*
import scalafx.scene.paint.Color
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
                                         contestManager: fdswarm.fx.contest.ContestConfigManager,
                                         rigctldPoller: RigctldPoller,
                                         rigCtlDialog: RigCtlDialog
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
    // Keep ButtonType.Close so ESC closes the dialog; we'll hide the auto-generated button
    dialog.dialogPane().buttonTypes = Seq(ButtonType.Close)
    val autoCloseBtn = dialog.dialogPane().delegate.lookupButton(ButtonType.Close.delegate)
    autoCloseBtn.setVisible(false)
    autoCloseBtn.setManaged(false)

    val closeButton = DialogButton("Close", "x-circle", ButtonVariant.Secondary, "Close")
    closeButton.onAction = _ => dialog.close()

    val rigCtlButton = IconButton("gear", 15, "Open RigCtl Manager", color = Color.BLUE)
    rigCtlButton.onAction = _ => rigCtlDialog.show(ownerWindow)

    val pauseCheckBox = new CheckBox("Pause RigCtl"):
      disable = !rigctldPoller.isEnabled
    pauseCheckBox.onAction = _ =>
      if pauseCheckBox.selected.value then rigctldPoller.pause() else rigctldPoller.resume()

    val rigCtlGroup = GridColumns.fieldSet("RigCtl",
      new HBox(4, rigCtlButton, pauseCheckBox)
    )

    val buttonBar = dialog.dialogPane().delegate.lookup(".button-bar")
      .asInstanceOf[javafx.scene.control.ButtonBar]
    javafx.scene.control.ButtonBar.setButtonData(rigCtlGroup.delegate,
      javafx.scene.control.ButtonBar.ButtonData.LEFT)
    javafx.scene.control.ButtonBar.setButtonData(closeButton.delegate,
      javafx.scene.control.ButtonBar.ButtonData.CANCEL_CLOSE)
    buttonBar.getButtons.addAll(rigCtlGroup.delegate, closeButton.delegate)

    dialog.showAndWait()

/*
  private val modeSelected: Map[String, BooleanProperty] =
    val cur = store.currentBandMode.modes
    allModes.map(m => m -> BooleanProperty(cur.contains(m))).toMap
  private val buttons: HBox =
    new HBox:
      spacing = 8
      padding = Insets(6, 0, 0, 0)
      children = Seq(
        new Button("Defaults (all on)") {
          onAction = handler {
            allBands.foreach(b => bandSelected(b).value = true)
            allModes.foreach(m => modeSelected(m).value = true)

            val enabled = allModes.map(m => m -> allBands.toSet).toMap
            store.setBands(allBands.toSet)
            store.setModes(allModes.toSet)
            store.setEnabled(enabled)

            refreshMatrixVisibility()
            matrixPane.refreshEnabledFromStore()
            matrixPane.refreshFromStore()
          }
        },
        new Button("Clear") {
          onAction = handler {
            allBands.foreach(b => bandSelected(b).value = false)
            allModes.foreach(m => modeSelected(m).value = false)
            store.setBands(Set.empty)
            store.setModes(Set.empty)
            store.setEnabled(Map.empty)
            refreshMatrixVisibility()
            matrixPane.refreshEnabledFromStore()
            matrixPane.refreshFromStore()
            matrixPane.clearSelection()
          }
        },
        new Button("Clear Cell Selection") {
          onAction = handler {
            matrixPane.clearSelection()
          }
        }
      )

  private def handler(body: => Unit): EventHandler[JfxActionEvent] =
    new EventHandler[JfxActionEvent]:
      override def handle(e: JfxActionEvent): Unit = body

  padding = Insets(10)
  left = new VBox:
    spacing = 10
    padding = Insets(0, 10, 0, 0)
    children = Seq(bandsPane, modesPane, buttons)

  center = matrixPane

  // initial matrix visibility
  refreshMatrixVisibility()
  matrixPane.refreshEnabledFromStore()
  matrixPane.refreshFromStore()

  private def persistSelections(): Unit =
    store.setBands(selectedBandsNow.toSet)
    store.setModes(selectedModesNow.toSet)

    refreshMatrixVisibility()
    // Enabled matrix may have changed elsewhere; always re-apply disabled styling.
    matrixPane.refreshEnabledFromStore()
    matrixPane.refreshFromStore()

  private def refreshMatrixVisibility(): Unit =
    matrixPane.setVisible(selectedModesNow, selectedBandsNow)

  private def selectedBandsNow: Seq[String] =
    allBands.filter(b => bandSelected(b).value)

  private def selectedModesNow: Seq[String] =
    allModes.filter(m => modeSelected(m).value)
*/
