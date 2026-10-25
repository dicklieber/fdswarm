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

import fdswarm.StartupInfo
import fdswarm.bands.BandModeBuilder
import fdswarm.io.FileHelper
import fdswarm.logging.LazyStructuredLogging
import fdswarm.model.BandMode
import jakarta.inject.{Inject, Singleton}
import scalafx.beans.property.ObjectProperty

@Singleton
final class SelectedBandModeManager @Inject()(fileHelper: FileHelper,
                                              bandModeBuilder: BandModeBuilder,
                                              startupInfo: StartupInfo) extends LazyStructuredLogging:


  private val fileName = "selected-bandmode.json"

  val selected: ObjectProperty[BandMode] = ObjectProperty(startupInfo.info.map(_.bandMode).getOrElse(load()))

  selected.onChange { (_, _, newValue) =>
    persist(newValue)
  }

  def save(value: BandMode): Unit =
    selected.value = value

  private def load(): BandMode =
    fileHelper.loadOrDefault(fileName)(BandMode("20m", "PH"))

  private def persist(value: BandMode): Unit =
    fileHelper.save(fileName, value)
