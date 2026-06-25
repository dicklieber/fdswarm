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

import fdswarm.bands.{AvailableBandsManager, AvailableModesManager, BandModeBuilder}
import fdswarm.fx.GridColumns
import fdswarm.fx.utils.IconButton
import fdswarm.logging.LazyStructuredLogging
import fdswarm.model.{Band, BandMode, Mode}
import fdswarm.rigctl.{RigCtlDialog, RigctlPollerIndicator, RigctldPoller}
import jakarta.inject.Inject
import scalafx.Includes.*
import scalafx.application.Platform
import scalafx.beans.property.BooleanProperty
import scalafx.geometry.Insets
import scalafx.scene.Node
import scalafx.scene.control.*
import scalafx.scene.layout.{ColumnConstraints, GridPane, HBox, Priority, Region, VBox}

import scala.jdk.CollectionConverters.*

/**
 * Mode × Band matrix.
 *
 * - One row per mode
 * - Each row contains all known bands (button label = band name)
 * - Exactly one cell selected (persisted via SelectedBandModeStore)
 * - Selection rendered as an outlined rounded rectangle
 * - Clicking any band in a row selects that (band, mode)
 * - If there is no persisted BandMode, the top-left cell is selected (or the first enabled one if available)
 */
final class BandModeMatrixPane @Inject()(availableBandsStore: AvailableBandsManager,
                                         availableModesManager: AvailableModesManager,
                                         selectedStore: SelectedBandModeManager,
                                         bandModeBuilder: BandModeBuilder,
                                         rigctldPoller: RigctldPoller,
                                         rigCtlDialog: RigCtlDialog
                                        ) extends  LazyStructuredLogging:

  val showConfigButton = BooleanProperty(true)
  var onConfigRequest: Option[() => Unit] = None
  private val tg = new ToggleGroup()

  private val container = new VBox(4)
  buildGrid()

  selectedStore.selected.onChange { (_, _, newValue) =>
    Platform.runLater {
      logger.debug(s"Selected BandMode changed to: $newValue. Updating UI toggles.")
      val toggle = tg.getToggles.iterator().asScala.find { t =>
        Option(t.getUserData).collect { case bm: BandMode => bm }.exists { bm =>
          val matchResult = bm.band == newValue.band && bm.mode == newValue.mode
          logger.trace(s"Checking toggle '${bm.band}'/'${bm.mode}' against '${newValue.band}'/'${newValue.mode}': $matchResult")
          matchResult
        }
      }

      toggle match {
        case Some(t: javafx.scene.control.ToggleButton) =>
          logger.debug(s"Selecting button for $newValue")
          t.setSelected(true)
        case Some(t) =>
          logger.debug(s"Selecting generic toggle for $newValue")
          tg.selectToggle(t)
        case None =>
          val allToggles = tg.getToggles.asScala.map { t =>
            Option(t.getUserData).collect { case bm: BandMode => bm } match {
              case Some(bm) => s"${bm.band}/${bm.mode}[${t.hashCode()}]"
              case None => s"UnknownToggle[${t.hashCode()}]"
            }
          }.mkString(", ")
          logger.warn(s"Could not find toggle for BandMode: $newValue. My hash: ${this.hashCode()}, TG hash: ${tg.hashCode()}, Toggles present [${tg.getToggles.size()}]: $allToggles")
      }
    }
  }

  availableBandsStore.bands.onChange {
    logger.debug("Available bands changed, rebuilding grid")
    buildGrid()
  }

  availableModesManager.modes.onChange {
    logger.debug("Available modes changed, rebuilding grid")
    buildGrid()
  }

  def buildGrid():Unit=
    logger.debug(s"Building grid for BandModeMatrixPane (tg: $tg)")
    tg.getToggles.clear()
    val grid = new GridPane():
      hgap = 2
      vgap = 2

    val nBands = availableBandsStore.bands.size
    val firstColCc = new ColumnConstraints() { hgrow = Priority.Never }
    val bandColCc = new ColumnConstraints() {
      hgrow = Priority.Never
    }
    grid.columnConstraints = Seq(firstColCc) ++ (1 to nBands).map(_ => bandColCc)

    for
      (mode,row) <- availableModesManager.modes.zipWithIndex
    do
      grid.add(new Label(mode.toString), 0, row)
      for (band, col) <- availableBandsStore.bands.zipWithIndex do
        val button = new ModeBandButton(band,mode, selectedStore.selected.value, bandModeBuilder, tg, selectedStore)
        logger.trace(s"Adding button $button (bandMode: ${button.bandMode}) to grid and ToggleGroup $tg")
        grid.add(button, col + 1, row)
    val configButton = IconButton("sliders2-vertical", 24, "Change available bands and modes")
    configButton.visible <== showConfigButton
    configButton.managed <== showConfigButton
    configButton.onAction = _ => onConfigRequest.foreach(_.apply())

    val indicator = new RigctlPollerIndicator(
      rigctldPoller,
      Some(() => Option(container.scene.value).map(_.getWindow).foreach(w => rigCtlDialog.show(w)))
    )
    val spacer = new Region()
    HBox.setHgrow(spacer, Priority.Always)
    val bottomBar = new HBox(4, indicator, spacer, configButton)

    container.children = Seq(
      GridColumns.fieldSet("Band & Mode", grid),
      bottomBar
    )

  val node:Node =
    container

class ModeBandButton(band:Band,
                     mode:Mode,
                     selectedHamBand:BandMode,
                     bandModeBuilder: BandModeBuilder,
                     tg: ToggleGroup,
                     selectedStore: SelectedBandModeManager
                    ) extends ToggleButton() with LazyStructuredLogging:
  val bandMode: BandMode = bandModeBuilder(band, mode)
  text = band.name
  padding = Insets(2, 4, 2, 4)
  minWidth = 0
  graphic = null
  graphicTextGap = 0
  toggleGroup = tg
  userData = bandMode
  styleClass += "custom-radio" // Apply custom CSS class
  selected.onChange { (_, _, isSelected) =>
    if isSelected then
      logger.debug(s"Button $bandMode [${this.hashCode()}] selected (tg: ${tg.hashCode()}). Saving to store.")
      selectedStore.save(bandMode)
  }
  if bandMode.band == selectedHamBand.band && bandMode.mode == selectedHamBand.mode then
    selected.value = true
