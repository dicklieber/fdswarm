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

import fdswarm.exporter.{CabrilloExporter, ExportService}
import fdswarm.logging.LazyStructuredLogging
import fdswarm.model.Callsign
import fdswarm.store.QsoStore
import fdswarm.util.FilenameStamp
import jakarta.inject.{Inject, Singleton}
import javafx.scene.input.{Clipboard, ClipboardContent}
import scalafx.Includes.*
import scalafx.beans.property.StringProperty
import scalafx.collections.ObservableBuffer
import scalafx.geometry.Insets
import scalafx.scene.control.*
import scalafx.scene.layout.{BorderPane, HBox}
import scalafx.stage.Window

@Singleton
class SummaryDialog @Inject()(
    qsoStore: QsoStore,
    exportService: ExportService,
    cabrilloExporter: CabrilloExporter,
    filenameStamp: FilenameStamp
) extends LazyStructuredLogging:

  case class OperatorSummary(callsign: Callsign, qsoCount: Int)

  def show(ownerWindow: Window): Unit =
    val qsos = qsoStore.all
    val operatorCounts = qsos.groupBy(_.qsoMetadata.station.operator)
      .map { case (op, list) => OperatorSummary(op, list.size) }
      .toSeq
      .sortBy(_.callsign)

    val summaryBuffer = ObservableBuffer.from[OperatorSummary](operatorCounts)

    val table = new TableView[OperatorSummary](summaryBuffer):
      columnResizePolicy = TableView.ConstrainedResizePolicy
      columns ++= List(
        new TableColumn[OperatorSummary, String]("Operator") {
          cellValueFactory = c => StringProperty(c.value.callsign.value)
        },
        new TableColumn[OperatorSummary, Int]("QSOs") {
          cellValueFactory = c => scalafx.beans.property.ObjectProperty[Int](c.value.qsoCount)
        }
      )

    val copyButton = new Button("Copy Operators"):
      onAction = _ =>
        val sortedOps = operatorCounts.map(_.callsign).sorted
        val opList = sortedOps.mkString(", ")
        val content = new ClipboardContent()
        content.putString(opList)
        Clipboard.getSystemClipboard.setContent(content)

    val saveCabrilloButton = new Button("Save Cabrillo"):
      onAction = _ =>
        val initialFilename = filenameStamp.build()
        val fileChooser = new javafx.stage.FileChooser()
        fileChooser.setTitle("Save Cabrillo File")
        fileChooser.setInitialFileName(s"$initialFilename.cbr")
        fileChooser.getExtensionFilters.add(new javafx.stage.FileChooser.ExtensionFilter("Cabrillo Files (*.cbr)", "*.cbr"))
        
        val selectedFile = fileChooser.showSaveDialog(ownerWindow)
        if selectedFile != null then
          try
            exportService.executeExport(os.Path(selectedFile.getAbsolutePath), cabrilloExporter)
            new Alert(Alert.AlertType.Information) {
              initOwner(ownerWindow)
              title = "Export Success"
              headerText = "Cabrillo export successful"
              contentText = s"Saved to ${selectedFile.getAbsolutePath}"
            }.showAndWait()
          catch
            case e: Exception =>
              logger.error("Cabrillo export failed", e)
              new Alert(Alert.AlertType.Error) {
                initOwner(ownerWindow)
                title = "Export Failed"
                headerText = "Cabrillo export failed"
                contentText = e.getMessage
              }.showAndWait()

    val buttonBox = new HBox(10, copyButton, saveCabrilloButton):
      padding = Insets(10)
      alignment = scalafx.geometry.Pos.CenterRight

    val dialog = new Dialog[Unit]():
      initOwner(ownerWindow)
      title = "Summary Report"
      headerText = "Operator Summary"
      resizable = true

    val content = new BorderPane:
      center = table
      bottom = buttonBox
      padding = Insets(10)
      prefWidth = 400
      prefHeight = 500

    dialog.dialogPane().content = content
    dialog.dialogPane().buttonTypes = Seq(ButtonType.Close)
    
    dialog.showAndWait()
