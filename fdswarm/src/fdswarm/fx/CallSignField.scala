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

package fdswarm.fx

import _root_.scalafx.scene.control.{TextField, TextFormatter}
import fdswarm.bandmodes.SelectedBandModeManager
import fdswarm.model.Callsign
import fdswarm.store.QsoStore
import jakarta.inject.Inject

/** Callsign entry field sad or happy as validated while typing.
  */
class CallSignField @Inject() (
                                qsoStore: QsoStore,
                                selectedBsndModeStore: SelectedBandModeManager,
                                override val userConfig: UserConfig
) extends TextField
    with NextField:
  styleClass += "qsoCallSign"

  text.onChange((_, _, newText) =>
    validProperty.value = isValid(newText)
  )

  textFormatter = new TextFormatter[String]((change: TextFormatter.Change) =>
    if change.isContentChange then change.setText(change.getText.toUpperCase)
    val newText = change.controlNewText
    // Match partial strings during typing.
    // The target regex is ^(?=.{3,12}$)[A-Z0-9]{1,3}[0-9][A-Z0-9]{1,4}(?:/[A-Z0-9]{1,4})?$
    // Partial regex must allow building up to this.
    val typingPattern =
      """^(?:[A-Z0-9]{0,3}(?:[0-9][A-Z0-9]{0,4}(?:/?[A-Z0-9]{0,4})?)?)?$"""
    if newText.length <= 12 && newText.matches(typingPattern) then change
    else null
  )

  override def canAdvance: Boolean =
    !qsoStore.containsDuplicate(Callsign(text.value), selectedBsndModeStore.selected.value)

  def isValid(str: String): Boolean =
    Callsign.isValid(str)
