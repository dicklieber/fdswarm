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

import fdswarm.fx.components.{ButtonVariant, DialogButton}
import fdswarm.fx.utils.UiStyles
import javafx.stage.Modality
import scalafx.Includes.*
import scalafx.application.Platform
import scalafx.beans.property.StringProperty
import scalafx.collections.ObservableBuffer
import scalafx.geometry.Insets
import scalafx.scene.Scene
import scalafx.scene.control.*
import scalafx.scene.input.MouseEvent
import scalafx.scene.layout.*
import scalafx.stage.{Stage, Window}

import scala.jdk.CollectionConverters.*

/** A modal rig-picker dialog.
  *
  * Displays a searchable [[TableView]] of all Hamlib-supported rig models.
  * The user selects a rig by double-clicking a row; the `onSelect` callback
  * is called with the chosen [[RigInfo]] and the dialog closes.
  *
  * Usage:
  * {{{
  *   RigPickerDialog.show(owner, currentModel, rigCtlManager) { rig =>
  *     // handle selection
  *   }
  * }}}
  */
object RigPickerDialog:

  def show(ownerWindow: Window, currentModel: Int, rigCtlManager: RigCtlManager)(onSelect: RigInfo => Unit): Unit =

    var stage: Option[Stage] = None

    val allRigs   = ObservableBuffer.empty[RigInfo]
    val tableRows = ObservableBuffer.empty[RigInfo]

    val statusLabel = new Label(""):
      style    = "-fx-text-fill: grey;"
      wrapText = true
      maxWidth = Double.MaxValue

    val filterField = new TextField:
      promptText = "Filter by manufacturer or model…"

    def applyFilter(text: String): Unit =
      val q = text.trim.toLowerCase
      val filtered: Seq[RigInfo] =
        if q.isEmpty then allRigs.toSeq
        else allRigs.toSeq.filter(r =>
          r.manufacturer.toLowerCase.contains(q) || r.model.toLowerCase.contains(q))
      tableRows.setAll(filtered.asJava)

    filterField.text.onChange((_, _, newText) => applyFilter(newText))

    def col(header: String, colWidth: Double)(f: RigInfo => String): TableColumn[RigInfo, String] =
      new TableColumn[RigInfo, String]:
        text             = header
        prefWidth        = colWidth
        sortable         = false
        cellValueFactory = r => StringProperty(f(r.value))

    val rigTable = new TableView[RigInfo](tableRows):
      columns ++= Seq(
        col("#",            60)(_.modelNumber.toString),
        col("Manufacturer", 160)(_.manufacturer),
        col("Model",        220)(_.model),
        col("Status",       80)(_.status)
      )
      columnResizePolicy = TableView.ConstrainedResizePolicy
      prefHeight         = 340

    // Double-click to select
    rigTable.onMouseClicked = (e: MouseEvent) =>
      if e.clickCount == 2 then
        Option(rigTable.selectionModel().getSelectedItem).foreach { rig =>
          onSelect(rig)
          stage.foreach(_.close())
        }

    def loadRigModels(): Unit =
      statusLabel.text  = "Running rigctl -l …"
      statusLabel.style = "-fx-text-fill: grey;"
      val t = new Thread(
        () =>
          rigCtlManager.listRigs() match
            case Right(rigs) =>
              Platform.runLater {
                allRigs.setAll(rigs.asJava)
                applyFilter(filterField.text.value)
                tableRows.zipWithIndex
                  .find { case (r, _) => r.modelNumber == currentModel }
                  .foreach { case (_, idx) =>
                    rigTable.selectionModel().select(idx)
                    rigTable.scrollTo(idx)
                  }
                statusLabel.text = s"${rigs.size} rigs loaded."
              }
            case Left(err) =>
              Platform.runLater {
                statusLabel.text  = s"Could not load rig list: $err"
                statusLabel.style = "-fx-text-fill: red;"
              }
        ,
        "rigctl-picker-loader"
      )
      t.setDaemon(true)
      t.start()

    loadRigModels()

    // ── Buttons ───────────────────────────────────────────────────────────────

    val refreshButton = DialogButton("Refresh", "arrow-clockwise", ButtonVariant.Secondary, "Reload rig list")
    refreshButton.onAction = _ => loadRigModels()

    val closeButton = DialogButton("Cancel", "x-circle", ButtonVariant.Secondary, "Close without selecting")
    closeButton.onAction = _ => stage.foreach(_.close())

    val hintLabel = new Label("Double-click a row to select"):
      style = "-fx-font-style: italic; -fx-text-fill: grey;"

    // ── Layout ────────────────────────────────────────────────────────────────

    val filterBar = new HBox(8, filterField, refreshButton):
      padding = Insets(0, 0, 4, 0)
      HBox.setHgrow(filterField, Priority.Always)

    val statusBar = new HBox(statusLabel):
      HBox.setHgrow(statusLabel, Priority.Always)

    val buttonBar = new HBox(10, hintLabel, closeButton):
      padding = Insets(8, 0, 0, 0)
      HBox.setHgrow(hintLabel, Priority.Always)

    val root = new VBox(8, filterBar, rigTable, statusBar, buttonBar):
      padding = Insets(12)
      VBox.setVgrow(rigTable, Priority.Always)

    val dialogScene = new Scene(root, 580, 460)
    UiStyles.applyTo(dialogScene)

    val newStage = new Stage:
      title     = "Select Rig Model"
      initOwner(ownerWindow)
      initModality(Modality.WINDOW_MODAL)
      resizable = true
      minWidth  = 480
      minHeight = 380
      scene     = dialogScene
      onHidden  = _ => stage = None

    newStage.show()
    stage = Some(newStage)
