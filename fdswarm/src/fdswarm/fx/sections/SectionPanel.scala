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

import fdswarm.fx.GridColumns
import fdswarm.fx.qso.QsoEntryPanel
import fdswarm.logging.LazyStructuredLogging
import jakarta.inject.Inject
import scalafx.beans.binding.{Bindings, BooleanBinding}
import scalafx.beans.property.StringProperty
import scalafx.geometry.Insets
import scalafx.scene.Node
import scalafx.scene.control.Label
import scalafx.scene.layout.{HBox, Priority, Region, VBox}

class SectionPanel @Inject()(sectionsProvider: SectionsProvider, 
                           qsoEntryPanel: QsoEntryPanel
                          ) extends LazyStructuredLogging:

  private val _node = new VBox()
  def node: Node = _node

  private var uiBuilt = false

  def buildUi(): Unit =
    if uiBuilt then return
    _node.children = Seq(buildNode(
      qsoEntryPanel.sectionFieldProperty,
      () => qsoEntryPanel.submit(),
      Bindings.createBooleanBinding(
        () => qsoEntryPanel.callsignValidProperty.value && qsoEntryPanel.contestClassValidProperty.value,
        qsoEntryPanel.callsignValidProperty,
        qsoEntryPanel.contestClassValidProperty
      ),
      "Sections"
    ))
    uiBuilt = true

  def buildNode(
      sectionField: StringProperty,
      onSelected: () => Unit,
      canSubmit: BooleanBinding,
      title: String
  ): Node =
    val mainVBox: VBox = new VBox():
      spacing = 10
      padding = Insets(5)

    canSubmit.onChange { (_, _, nv) =>
      if nv then mainVBox.styleClass.remove("sections-panel-disabled")
      else if !mainVBox.styleClass.contains("sections-panel-disabled") then
        mainVBox.styleClass.add("sections-panel-disabled")
    }
    if !canSubmit.value then mainVBox.styleClass.add("sections-panel-disabled")

    for
      case (sectionGroup, row) <- sectionsProvider.sectionGroups.zipWithIndex
    do
      val nameLabel = new Label(sectionGroup.name):
        minWidth = Region.USE_PREF_SIZE
        padding = Insets(0, 10, 0, 0)
        style = "-fx-font-weight: bold;"

      val sectionLabels = sectionGroup.sections.map(
        _.createLabel(sectionField, onSelected, canSubmit)
      )
      val groupGrid = GridColumns.toGrid(sectionLabels, 11)
      groupGrid.maxWidth = Double.MaxValue
      HBox.setHgrow(groupGrid, Priority.Always)

      val rowContainer = new HBox():
        children = Seq(nameLabel, groupGrid)
        padding = Insets(5)
        style = "-fx-background-color: #f4f4f4; -fx-background-radius: 5; -fx-border-color: #cccccc; -fx-border-radius: 5; -fx-border-width: 1;"
        alignment = scalafx.geometry.Pos.CenterLeft

      mainVBox.children.add(rowContainer)

    GridColumns.fieldSet(title, mainVBox)
