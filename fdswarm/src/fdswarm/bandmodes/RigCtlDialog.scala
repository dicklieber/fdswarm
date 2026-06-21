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

package fdswarm.bandmodes

import fdswarm.fx.utils.editor.{CaseClassPropertyEditor, HelpBrowser}
import fdswarm.logging.LazyStructuredLogging
import jakarta.inject.{Inject, Singleton}
import scala.jdk.CollectionConverters.*
import scalafx.Includes.*
import scalafx.application.Platform
import scalafx.beans.property.{BooleanProperty, StringProperty}
import scalafx.collections.ObservableBuffer
import scalafx.geometry.Insets
import scalafx.scene.Scene
import scalafx.scene.control.*
import scalafx.scene.layout.*
import scalafx.stage.{Stage, Window}

/** Dialog for configuring and managing the rigctld integration.
  *
  * Rig model selection uses a [[TableView]] with columns for model number,
  * manufacturer, model name, and status.  A filter field narrows the list.
  * Settings are persisted automatically via [[RigCtlManager]].
  */
@Singleton
class RigCtlDialog @Inject()(rigCtlManager: RigCtlManager, rigctldPoller: RigctldPoller)
    extends LazyStructuredLogging:

  private var stage: Option[Stage] = None

  def show(ownerWindow: Window): Unit =
    stage match
      case Some(s) => s.requestFocus()
      case None    => buildAndShowStage(ownerWindow)

  private def buildAndShowStage(ownerWindow: Window): Unit =
    val cfg = rigCtlManager.config.value

    // ── Property editor (all fields except rigModel) ───────────────────────

    val editor = new CaseClassPropertyEditor[RigCtlConfig](cfg)
    editor.hideField("rigModel")

    val settingsPane = editor.vertical
    settingsPane.padding = Insets(12, 12, 4, 12)

    // ── Rig model table ───────────────────────────────────────────────────────

    val statusLabel = new Label(""):
      style = "-fx-text-fill: grey;"
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
      prefHeight = 220

    // ── Rig list loading ──────────────────────────────────────────────────────

    def loadRigModels(): Unit =
      statusLabel.text = "Running rigctl -l …"
      statusLabel.style = "-fx-text-fill: grey;"
      val t = new Thread(() => {
        rigCtlManager.listRigs() match
          case Right(rigs) =>
            Platform.runLater {
              allRigs.setAll(rigs.asJava)
              applyFilter(filterField.text.value)
              tableRows.zipWithIndex
                .find { case (r, _) => r.modelNumber == rigCtlManager.config.value.rigModel }
                .foreach { case (_, idx) =>
                  rigTable.selectionModel().select(idx)
                  rigTable.scrollTo(idx)
                }
              statusLabel.text = s"${rigs.size} rigs loaded."
            }
          case Left(err) =>
            Platform.runLater {
              statusLabel.text = s"Could not load rig list: $err"
              statusLabel.style = "-fx-text-fill: red;"
            }
      }, "rigctl-list-loader")
      t.setDaemon(true)
      t.start()

    loadRigModels()

    // ── Helpers ───────────────────────────────────────────────────────────────

    def selectedModel: Int =
      Option(rigTable.selectionModel().getSelectedItem)
        .map(_.modelNumber)
        .getOrElse(cfg.rigModel)

    def currentConfig(): RigCtlConfig =
      editor.finish().copy(rigModel = selectedModel)

    // ── Command line & dirty tracking ─────────────────────────────────────────

    val commandLineField = new TextField:
      editable = false
      text     = rigCtlManager.commandLine(cfg)
      style    = "-fx-font-family: monospace; -fx-background-color: #f4f4f4;"
      maxWidth = Double.MaxValue

    val dirty = BooleanProperty(false)

    def refresh(): Unit =
      val c = currentConfig()
      commandLineField.text = rigCtlManager.commandLine(c)
      dirty.value = c != cfg

    editor.onAnyFieldChange(() => refresh())
    rigTable.selectionModel().selectedItemProperty().addListener((_, _, _) => refresh())

    // ── Buttons ───────────────────────────────────────────────────────────────

    val saveButton = new Button("Save"):
      disable <== dirty.not()
      onAction = _ =>
        rigCtlManager.saveConfig(currentConfig())
        dirty.value = false
        statusLabel.text = "Configuration saved."
        statusLabel.style = "-fx-text-fill: green;"

    val refreshButton = new Button("Refresh"):
      onAction = _ => loadRigModels()

    val closeButton = new Button("Close"):
      onAction = _ => stage.foreach(_.close())

    val helpButton = new Button("Help"):
      onAction = _ => HelpBrowser.open("/fdswarmDocs/user/rigctl.html")

    // ── Layout ────────────────────────────────────────────────────────────────

    val filterBar = new HBox(8, filterField, refreshButton):
      padding = Insets(0, 12, 4, 12)
      HBox.setHgrow(filterField, Priority.Always)

    val rigModelSection = new TitledPane:
      text        = "Rig Model"
      collapsible = false
      content     = new VBox(6, filterBar, rigTable):
        padding = Insets(6, 12, 6, 12)

    val statusBar = new HBox(statusLabel):
      padding = Insets(2, 12, 2, 12)
      HBox.setHgrow(statusLabel, Priority.Always)

    val commandLineBar = new HBox(8, new Label("Command:"), commandLineField):
      padding = Insets(4, 12, 4, 12)
      HBox.setHgrow(commandLineField, Priority.Always)

    val buttonBar = new HBox(10, saveButton, closeButton, helpButton):
      padding = Insets(8, 12, 10, 12)

    val root = new VBox(settingsPane, rigModelSection, statusBar, commandLineBar, buttonBar):
      VBox.setVgrow(rigModelSection, Priority.Always)

    val newStage = new Stage:
      title    = "RigCtl Manager"
      initOwner(ownerWindow)
      resizable = true
      minWidth  = 560
      minHeight = 480
      scene     = new Scene(root, 620, 560)
      onHidden  = _ => stage = None

    newStage.show()
    stage = Some(newStage)
