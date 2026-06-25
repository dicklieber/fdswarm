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

package fdswarm.bands

import fdswarm.io.FileHelper
import fdswarm.model.Mode
import jakarta.inject.{Inject, Singleton}
import javafx.collections.ListChangeListener
import scalafx.collections.ObservableBuffer

@Singleton
final class AvailableModesManager @Inject()(fileHelper: FileHelper):
  private val file = "modes.json"

  /** Single source of truth */
  val modes: ObservableBuffer[Mode] =
    ObservableBuffer.from(loadFromDisk())

  // Persist automatically when the buffer changes
  modes.delegate.addListener(
    new ListChangeListener[Mode]:
      override def onChanged(c: ListChangeListener.Change[? <: Mode]): Unit =
        persist()
  )

  private def persist(): Unit =
    fileHelper.save(file, modes.toSeq)

  private def loadFromDisk(): Seq[Mode] =
    fileHelper.loadOrDefault(file)(Modes.modes)
