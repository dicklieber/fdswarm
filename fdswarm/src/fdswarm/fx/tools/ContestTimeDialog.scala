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

import fdswarm.fx.contest.ContestConfigManager
import fdswarm.fx.qso.ContestDetailPanel
import fdswarm.logging.LazyStructuredLogging
import jakarta.inject.{Inject, Singleton}
import scalafx.geometry.Insets
import scalafx.scene.Scene
import scalafx.scene.control.*
import scalafx.scene.layout.{GridPane, HBox, Region}
import scalafx.stage.{Stage, Window}

import java.time.ZonedDateTime

@Singleton
class ContestTimeDialog @Inject()(contestManager: ContestConfigManager,
                                  contestDetailPanel: ContestDetailPanel) extends LazyStructuredLogging{

  private var stage: Option[Stage] = None

  def show(ownerWindow: Window): Unit = {
    if !contestManager.hasConfiguration.value then
       new Alert(Alert.AlertType.Warning, "No contest configuration loaded. Please configure the contest first.") {
         initOwner(ownerWindow)
       }.showAndWait()
       return
    stage match {
      case Some(s) => s.requestFocus()
      case None =>
        val newStage = new Stage {
          title = "Contest Time"
          initOwner(ownerWindow)
          resizable = true
        }

        val useMockTimeCheckBox = new CheckBox("Use Mock time") {
          selected = false
          minWidth = Region.USE_PREF_SIZE
        }

        val now = ZonedDateTime.now()
        val mockTimeEditor = new ZonedDateTimeEditor(now, "Mock Time")

        def updatePanel(): Unit = {
          contestDetailPanel.setMockTime(useMockTimeCheckBox.selected.value, mockTimeEditor.value)
        }

        useMockTimeCheckBox.onAction = _ => updatePanel()
        mockTimeEditor.setOnAction(updatePanel())

        val mockTimeBox = new HBox(5, mockTimeEditor)
        mockTimeBox.disable <== useMockTimeCheckBox.selected.not()

        val root = new GridPane {
          hgap = 10
          vgap = 10
          padding = Insets(10)
          add(useMockTimeCheckBox, 0, 0, 2, 1)
          add(new Label("Mock Time:") { minWidth = Region.USE_PREF_SIZE }, 0, 1)
          add(mockTimeBox, 1, 1)

          add(new Label("Set the optional end time in Contest Configuration.") {
            wrapText = true
            minHeight = Region.USE_PREF_SIZE
          }, 0, 2, 2, 1)
        }

        newStage.scene = new Scene(root)
        newStage.onCloseRequest = _ => stage = None
        newStage.show()
        stage = Some(newStage)
    }
  }
}
