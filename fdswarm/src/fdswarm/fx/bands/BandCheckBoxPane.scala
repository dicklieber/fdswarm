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

package fdswarm.fx.bands

import fdswarm.bands.AvailableBandsManager
import fdswarm.fx.GridColumns
import fdswarm.model.{Band, BandClass}
import jakarta.inject.{Inject, Singleton}
import scalafx.scene.Node
import scalafx.scene.control.{CheckBox, Label, Tooltip}
import scalafx.scene.layout.GridPane

@Singleton
final class BandCheckBoxPane @Inject()(
                                        availableBandsManager: AvailableBandsManager
                                      ):

  private val grid = new GridPane:
    hgap = 12.0
    vgap = 6.0

  private val checkBoxes: Seq[BandCheckBox] =
    Band.values.toIndexedSeq.filterNot(_ == Band.OutOfBand).map(BandCheckBox(_))

  private val byBandClass: Map[BandClass, Seq[BandCheckBox]] =
    checkBoxes.groupBy(_.band.bandClass)

  // layout
  for
    (bandClass, row) <- BandClass.values.zipWithIndex
    if byBandClass.contains(bandClass)
    _ = {
      val label = new Label(bandClass.toString) {
        style = "-fx-cursor: hand; -fx-text-fill: derive(-fx-accent, -20%); -fx-underline: true;"
        tooltip = Tooltip("Click to toggle all in row")
        onMouseClicked = _ => {
          val rowBoxes = byBandClass(bandClass)
          val allChecked = rowBoxes.forall(_.selected.value)
          val newState = !allChecked
          rowBoxes.foreach(_.selected.value = newState)
          // availableBandsManager.bands.setAll(checked*) is called by each checkbox's onChange,
          // but we can call it once here to be sure, although Platform.runLater or batching might be better
          // if there are many. Since it's UI thread, it should be fine.
          availableBandsManager.bands.setAll(checked*)
        }
      }
      grid.addRow(row, label)
    }
    (bandCheckBox, col) <- byBandClass(bandClass).zipWithIndex
  do
    grid.add(bandCheckBox, col + 1, row)

  private val headerLabel = new Label("Ham bands") {
    style = "-fx-cursor: hand; -fx-text-fill: derive(-fx-accent, -20%); -fx-underline: true;"
    tooltip = Tooltip("Click to toggle all bands")
    onMouseClicked = _ => {
      val allChecked = checkBoxes.forall(_.selected.value)
      val newState = !allChecked
      checkBoxes.foreach(_.selected.value = newState)
      availableBandsManager.bands.setAll(checked*)
    }
  }

  val node: Node =
    GridColumns.fieldSet(headerLabel, grid)

  private def checked: Seq[Band] =
    checkBoxes.iterator.filter(_.selected.value).map(_.band).toSeq

  final case class BandCheckBox(band: Band) extends CheckBox:
    text = band.name

    // initialize selection from persisted bands
    selected = availableBandsManager.bands.contains(band)

    tooltip = new Tooltip(
      s"${band.bandClass}  ${band.startFrequencyHz}–${band.endFrequencyHz} Hz"
    )

    selected.onChange { (_, _, _) =>
      // replace everything in the store from current UI state
      availableBandsManager.bands.setAll(checked*)
    }
