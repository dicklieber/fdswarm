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

package fdswarm.fx.sections

import io.circe.Codec
import scalafx.beans.binding.BooleanBinding
import scalafx.beans.property.StringProperty
import scalafx.geometry.Insets
import scalafx.scene.control.{Label, OverrunStyle, Tooltip}

case class SectionGroup(name: String, sections: Seq[Section]) derives Codec.AsObject, sttp.tapir.Schema

case class Section(code: String, name: String) extends Label derives Codec.AsObject, sttp.tapir.Schema:
  text = code
  tooltip = Tooltip(name)
  padding = Insets(2, 4, 2, 4)
  minWidth = 0
  textOverrun = OverrunStyle.Clip

  def onSelect(
      sectionField: StringProperty,
      onSelected: () => Unit,
      canSubmit: BooleanBinding
  ): Unit =
    applyHandlers(this, sectionField, onSelected, canSubmit)

  def createLabel(
      sectionField: StringProperty,
      onSelected: () => Unit,
      canSubmit: BooleanBinding
  ): Label =
    val label = new Label(code):
      tooltip = Tooltip(name)
      padding = Insets(2, 4, 2, 4)
      minWidth = 0
      textOverrun = OverrunStyle.Clip
    applyHandlers(label, sectionField, onSelected, canSubmit)
    label

  private def applyHandlers(
      label: Label,
      sectionField: StringProperty,
      onSelected: () => Unit,
      canSubmit: BooleanBinding
  ): Unit =
    label.onMouseClicked = _ => {
      if canSubmit.value then
        sectionField.value = code
        onSelected()
    }
    label.onMouseEntered = _ => {
      if (canSubmit.value) {
        label.style = "-fx-background-color: lightgray; -fx-cursor: hand;"
      }
    }
    label.onMouseExited = _ => label.style = "-fx-background-color: transparent; -fx-cursor: default;"

    canSubmit.onChange { (_, _, nv) =>
      if nv then label.styleClass.remove("section-disabled")
      else if !label.styleClass.contains("section-disabled") then label.styleClass.add("section-disabled")
    }
    // Initial state
    if !canSubmit.value && !label.styleClass.contains("section-disabled") then label.styleClass.add("section-disabled")

    sectionField.onChange { (_, _, newValue) =>
      val input = if newValue == null then "" else newValue.trim.toUpperCase
      if input.nonEmpty && code.toUpperCase.startsWith(input) then
        if !label.styleClass.contains("section-match") then label.styleClass.add("section-match")
      else
        label.styleClass.remove("section-match")
    }

import jakarta.inject.{Inject, Singleton}
@Singleton
class Sections @Inject()(sectionsProvider: SectionsProvider):
  val all: Seq[Section] = sectionsProvider.allSections
