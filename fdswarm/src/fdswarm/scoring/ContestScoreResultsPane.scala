package fdswarm.scoring

import jakarta.inject.*
import scalafx.geometry.{Insets, Pos}
import scalafx.scene.control.Label
import scalafx.scene.layout.{GridPane, Priority, VBox}

import java.text.NumberFormat
import java.util.Locale

@Singleton
class ContestScoreResultsPane @Inject() (
                                          contestScoringService: ContestScoringService
                                        ):

  private val intFormat = NumberFormat.getIntegerInstance(
    Locale.US
  )

  private val scoreGrid = new GridPane:
    hgap = 8
    vgap = 2

  private val pointsLabel = new Label("Phone: 1 point per QSO. CW and digital: 2 points per QSO."):
    wrapText = true
    maxWidth = Double.MaxValue

  private val rootPane = new VBox:
    padding = Insets(12)
    spacing = 8
    children = Seq(pointsLabel, scoreGrid)

  def pane: VBox =
    rootPane

  private def formatInt(
                         value: Int
                       ): String =
    intFormat.format(
      value
    )

  private def newSectionHeader(
                                text: String
                              ): Label =
    new Label(
      text
    ):
      styleClass += "grid-header"
      alignment = Pos.Center
      maxWidth = Double.MaxValue
      wrapText = true

  private def newNameCell(
                           text: String
                         ): Label =
    new Label(
      text
    ):
      styleClass += "grid-row-label"
      maxWidth = Double.MaxValue
      wrapText = true

  private def newValueCell(
                            text: String
                          ): Label =
    new Label(
      text
    ):
      styleClass += "grid-value"
      alignment = Pos.CenterRight
      maxWidth = Double.MaxValue
      wrapText = true

  private def addSectionHeaderRow(
                                   row: Int,
                                   sectionName: String
                                 ): Int =
    val sectionHeader = newSectionHeader(
      sectionName
    )
    GridPane.setHgrow(
      sectionHeader,
      Priority.Always
    )
    scoreGrid.add(
      sectionHeader,
      0,
      row,
      2,
      1
    )
    row + 1

  private def addNameValueRow(
                               row: Int,
                               name: String,
                               value: String
                             ): Int =
    val nameCell = newNameCell(
      name
    )
    GridPane.setHgrow(
      nameCell,
      Priority.Always
    )
    scoreGrid.add(
      nameCell,
      0,
      row
    )
    val valueCell = newValueCell(
      value
    )
    GridPane.setHgrow(
      valueCell,
      Priority.Always
    )
    scoreGrid.add(
      valueCell,
      1,
      row
    )
    row + 1

  def refresh(): Unit =
    val result = contestScoringService.current

    scoreGrid.children.clear()

    var row = 0

    row = addSectionHeaderRow(
      row = row,
      sectionName = "Totals"
    )
    row = addNameValueRow(
      row = row,
      name = "Total Score",
      value = formatInt(
        result.totalScore
      )
    )
    row = addNameValueRow(
      row = row,
      name = "Total QSOs",
      value = formatInt(
        result.totalQsos
      )
    )

    row = addSectionHeaderRow(
      row = row,
      sectionName = "By Mode"
    )
    if result.byMode.isEmpty then
      row = addNameValueRow(
        row = row,
        name = "(none)",
        value = ""
      )
    else
      result.byMode.toSeq.sortBy(_._1).foreach:
        case (mode, count) =>
          row = addNameValueRow(
            row = row,
            name = mode,
            value = formatInt(
              count
            )
          )

    row = addSectionHeaderRow(
      row = row,
      sectionName = "By Band"
    )
    if result.byBand.isEmpty then
      row = addNameValueRow(
        row = row,
        name = "(none)",
        value = ""
      )
    else
      result.byBand.toSeq.sortBy(_._1).foreach:
        case (band, count) =>
          row = addNameValueRow(
            row = row,
            name = band,
            value = formatInt(
              count
            )
          )
