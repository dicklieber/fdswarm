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

import fdswarm.fx.GridColumns
import fdswarm.fx.contest.{ClassChoice, ContestCatalog, ContestConfigManager}
import fdswarm.fx.sections.SectionsProvider
import jakarta.inject.{Inject, Singleton}
import scalafx.scene.Node
import scalafx.scene.control.Label
import scalafx.scene.layout.{GridPane, VBox}

@Singleton
class InfoPanel @Inject()(
    qsoEntryPanel: QsoEntryPanel,
    contestDetailPanel: ContestDetailPanel,
    dupPanel: DupPanel,
    contestManager: ContestConfigManager,
    contestCatalog: ContestCatalog,
    sectionsProvider: SectionsProvider
):
  private val _node = new VBox()
  private val dupNode = dupPanel.pane()
  private var contestConfigListenerRegistered = false

  qsoEntryPanel.callsignFocusedProperty.onChange((_, _, _) => refresh())
  qsoEntryPanel.contestClassFocusedProperty.onChange((_, _, _) => refresh())
  qsoEntryPanel.sectionFieldFocusedProperty.onChange((_, _, _) => refresh())
  qsoEntryPanel.sectionFieldProperty.onChange((_, _, _) => refresh())
  qsoEntryPanel.callsignValidProperty.onChange((_, _, _) => refresh())
  qsoEntryPanel.contestClassValidProperty.onChange((_, _, _) => refresh())
  dupPanel.showingPotentialDuplicatesProperty.onChange((_, _, _) => refresh())
  contestManager.hasConfiguration.onChange { (_, _, hasConfig) =>
    refresh()
    if hasConfig then
      registerContestConfigListenerIfNeeded()
  }
  if contestManager.hasConfiguration.value then
    registerContestConfigListenerIfNeeded()

  refresh()

  private def registerContestConfigListenerIfNeeded(): Unit =
    if !contestConfigListenerRegistered then
      contestManager.contestConfigProperty.onChange((_, _, _) => refresh())
      contestConfigListenerRegistered = true

  private def classChoices: Seq[ClassChoice] =
    contestManager.contestConfigProperty.value
      .flatMap(config => contestCatalog.getContest(config.contestType))
      .map(_.classChoices)
      .getOrElse(Seq.empty)

  private val tableGridStyle =
    "-fx-hgap: 0; -fx-vgap: 0; -fx-padding: 0;"

  private val codeCellStyle =
    "-fx-padding: 0 4 0 4; -fx-font-weight: bold;"

  private val nameCellStyle =
    "-fx-padding: 0 4 0 4;"

  private val codeCellHoverStyle =
    "-fx-padding: 0 4 0 4; -fx-font-weight: bold; -fx-background-color: lightgray; -fx-cursor: hand;"

  private val nameCellHoverStyle =
    "-fx-padding: 0 4 0 4; -fx-background-color: lightgray; -fx-cursor: hand;"

  private val headerCodeCellStyle =
    "-fx-padding: 0 4 0 4; -fx-font-weight: bold; -fx-background-color: #efefef;"

  private val headerNameCellStyle =
    "-fx-padding: 0 4 0 4; -fx-font-weight: bold; -fx-background-color: #efefef;"

  private def addHeaderRow(grid: GridPane): Unit =
    val codeHeader = new Label("Code"):
      style = headerCodeCellStyle
    val nameHeader = new Label("Name"):
      style = headerNameCellStyle
    grid.add(codeHeader, 0, 0)
    grid.add(nameHeader, 1, 0)

  private def addClickableRow(
      grid: GridPane,
      row: Int,
      code: String,
      name: String,
      canClick: => Boolean,
      onClick: () => Unit
  ): Unit =
    val codeLabel = new Label(code):
      style = codeCellStyle
      styleClass += "section-match"
    val nameLabel = new Label(name):
      style = nameCellStyle

    def setNormalStyle(): Unit =
      codeLabel.style = codeCellStyle
      nameLabel.style = nameCellStyle

    def setHoverStyle(): Unit =
      if canClick then
        codeLabel.style = codeCellHoverStyle
        nameLabel.style = nameCellHoverStyle

    def click(): Unit =
      if canClick then onClick()

    val hoverHandlers: Seq[Node] = Seq(codeLabel, nameLabel)
    hoverHandlers.foreach { n =>
      n.onMouseEntered = _ => setHoverStyle()
      n.onMouseExited = _ => setNormalStyle()
      n.onMouseClicked = _ => click()
    }

    grid.add(codeLabel, 0, row)
    grid.add(nameLabel, 1, row)

  private def classChoicesNode: Node =
    val classes = classChoices
    val grid = new GridPane:
      style = tableGridStyle
      gridLinesVisible = true
    addHeaderRow(grid)
    if classes.nonEmpty then
      classes.zipWithIndex.foreach { case (choice, row) =>
        addClickableRow(
          grid = grid,
          row = row + 1,
          code = choice.ch,
          name = choice.description,
          canClick = true,
          onClick = () => qsoEntryPanel.applyClassChoice(choice.ch)
        )
      }
      GridColumns.fieldSet("Class Choices", grid)
    else
      GridColumns.fieldSet("Class Choices", new Label("No class choices available for current contest."))

  private def sectionChoicesNode(prefix: String): Node =
    val grid = new GridPane:
      style = tableGridStyle
      gridLinesVisible = true
    addHeaderRow(grid)
    val matchingSections = sectionsProvider.allSections.filter(_.code.toUpperCase.startsWith(prefix))
    if matchingSections.nonEmpty then
      matchingSections.zipWithIndex.foreach { case (section, row) =>
        addClickableRow(
          grid = grid,
          row = row + 1,
          code = section.code,
          name = section.name,
          canClick = qsoEntryPanel.canSubmitProperty.value,
          onClick = () =>
            qsoEntryPanel.sectionFieldProperty.value = section.code
            qsoEntryPanel.submit()
        )
      }
    else
      grid.add(new Label(s"No matching sections for '$prefix'"), 0, 1, 2, 1)
    GridColumns.fieldSet("Sections", grid)

  private def refresh(): Unit =
    val sectionPrefix = Option(qsoEntryPanel.sectionFieldProperty.value).getOrElse("").trim.toUpperCase
    val hasSectionInput = sectionPrefix.nonEmpty
    val selectedNode =
      if qsoEntryPanel.callsignFocusedProperty.value && dupPanel.showingPotentialDuplicatesProperty.value then dupNode
      else if qsoEntryPanel.sectionFieldFocusedProperty.value && hasSectionInput then sectionChoicesNode(sectionPrefix)
      else if qsoEntryPanel.contestClassFocusedProperty.value && !qsoEntryPanel.contestClassValidProperty.value then classChoicesNode
      else contestDetailPanel.node
    _node.children = Seq(selectedNode)

  def node: Node = _node
