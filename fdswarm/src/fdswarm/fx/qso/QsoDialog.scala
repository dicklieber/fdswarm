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

package fdswarm.fx.qso

import fdswarm.model.Qso
import scalafx.geometry.Insets
import scalafx.scene.control.*
import scalafx.scene.layout.GridPane

import java.time.ZoneId
import java.time.format.DateTimeFormatter

object QsoDialog:
  private val timeFmt =
    DateTimeFormatter.ofPattern("MMM dd, yyyy h:mm:ss a z")
      .withZone(ZoneId.systemDefault())

  def show(qso: Qso): Unit =
    val dialog = new Dialog[Unit]() {
      title = "QSO Details"
      headerText = s"Details for QSO with ${qso.callsign.value}"
    }

    val grid = new GridPane() {
      hgap = 10
      vgap = 10
      padding = Insets(20, 150, 10, 10)
    }

    def addRow(label: String, value: String, row: Int): Unit =
      grid.add(new Label(label) { style = "-fx-font-weight: bold" }, 0, row)
      grid.add(new Label(value), 1, row)

    addRow("Time:", timeFmt.format(qso.stamp), 0)
    addRow("Callsign:", qso.callsign.value, 1)
    addRow("Band:", qso.bandMode.band.name, 2)
    addRow("Mode:", qso.bandMode.mode.toString, 3)
    addRow("Class:", qso.exchange.fdClass.toString, 4)
    addRow("Section:", qso.exchange.sectionCode, 5)
    addRow("Operator:", qso.qsoMetadata.station.operator.value, 6)
    addRow("Node:", qso.qsoMetadata.node.toString, 7)
    addRow("Rig:", qso.qsoMetadata.station.rig, 8)
    addRow("Antenna:", qso.qsoMetadata.station.antenna, 9)
    addRow("Contest:", qso.qsoMetadata.contest, 10)
    addRow("UUID:", qso.uuid.toString, 11)

    dialog.dialogPane().setContent(grid)
    dialog.dialogPane().getButtonTypes.add(ButtonType.Close)

    dialog.showAndWait()
