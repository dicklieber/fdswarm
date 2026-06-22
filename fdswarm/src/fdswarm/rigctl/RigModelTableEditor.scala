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
import fdswarm.fx.utils.editor.CustomFieldEditor
import scalafx.Includes.*
import scalafx.application.Platform
import scalafx.beans.property.{IntegerProperty, StringProperty}
import scalafx.collections.ObservableBuffer
import scalafx.scene.Node
import scalafx.scene.control.*
import scalafx.scene.layout.*

import scala.jdk.CollectionConverters.*

/** A [[CustomFieldEditor]] for the `rigModel` field in [[RigCtlConfig]].
  *
  * Presents a searchable [[TableView]] of all Hamlib-supported rig models,
  * populated asynchronously via `rigctl -l`.  Selecting a row updates the
  * underlying [[IntegerProperty]] so
  * [[fdswarm.fx.utils.editor.CaseClassPropertyEditor#finish]] picks up the
  * correct value automatically without any extra wiring in the owning dialog.
  *
  * Register with:
  * {{{
  *   editor.setCustomEditor("rigModel", new RigModelTableEditor(rigCtlManager))
  * }}}
  */
class RigModelTableEditor(rigCtlManager: RigCtlManager) extends CustomFieldEditor:

  /** Always valid — any model number is acceptable. */
  override def isValid: Boolean = true

  override def editor(fieldProperty: Any): Node =
    buildNode(fieldProperty.asInstanceOf[IntegerProperty])

  private def buildNode(rigModelProp: IntegerProperty): Node =

    val statusLabel = new Label(""):
      style    = "-fx-text-fill: grey;"
      wrapText = true
      maxWidth = Double.MaxValue

    val allRigs   = ObservableBuffer.empty[RigInfo]
    val tableRows = ObservableBuffer.empty[RigInfo]

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
        col("Model",        200)(_.model),
        col("Status",       80)(_.status)
      )
      columnResizePolicy = TableView.ConstrainedResizePolicy
      prefHeight         = 260

    rigTable.selectionModel().selectedItemProperty().addListener { (_, _, selected) =>
      if selected != null then
        rigModelProp.value = selected.modelNumber
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
                  .find { case (r, _) => r.modelNumber == rigModelProp.value }
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
        "rigctl-list-loader"
      )
      t.setDaemon(true)
      t.start()

    loadRigModels()

    val refreshButton = DialogButton("Refresh", "arrow-clockwise", ButtonVariant.Secondary, "Reload rig list")
    refreshButton.onAction = _ => loadRigModels()

    val filterBar = new HBox(8, filterField, refreshButton):
      HBox.setHgrow(filterField, Priority.Always)

    val statusBar = new HBox(statusLabel):
      HBox.setHgrow(statusLabel, Priority.Always)

    new VBox(4, filterBar, rigTable, statusBar)
