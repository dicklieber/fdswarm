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

import fdswarm.fx.UserConfig
import fdswarm.fx.components.{AnyComboBox, CountComboBox, OptionTextField}
import fdswarm.fx.contest.*
import fdswarm.fx.utils.{IconButton, MultiChangeWatcher}
import fdswarm.logging.{LazyStructuredLogging, Locus}
import fdswarm.model.{Band, ChoiceItem, Mode, Qso}
import fdswarm.store.QsoStore
import fdswarm.util.{NodeIdentity, StatsSource}
import io.circe.syntax.*
import jakarta.inject.Inject
import javafx.collections.ListChangeListener
import scalafx.Includes.*
import scalafx.beans.property.BooleanProperty
import scalafx.collections.ObservableBuffer
import scalafx.scene.control.*
import scalafx.scene.layout.{FlowPane, HBox, VBox}
import scalafx.scene.paint.Color
import scalafx.stage.FileChooser

import java.io.PrintWriter

class QsoSearchPane @Inject()(
                               contestManager: ContestConfigManager,
                               contestCatalog: ContestCatalog,
                               userConfig: UserConfig,
                               qsoStore: QsoStore,
                               qsoTablePane: QsoTablePane
) extends  LazyStructuredLogging with StatsSource(Locus.Search):
  private val searchTimer = addTimer("duration")
  val callsignFilter = new OptionTextField {
    promptText = "Callsign"
  }
  val bandFilter = new AnyComboBox[Band](Band.values.toIndexedSeq.map(ChoiceItem(_)))
  val modeFilter = new AnyComboBox[Mode](Mode.values.toIndexedSeq.map(ChoiceItem(_)))
  val classFilter = new AnyComboBox[Char](Seq.empty)
  val operatorFilter = new AnyComboBox[String](Seq.empty)
  val transmittersFilter = new CountComboBox()
  val nodeFilter = new AnyComboBox[NodeIdentity](Seq.empty)
  /**
   * is the pane expanded?
   */
  val expandedProperty = scalafx.beans.property.BooleanProperty(false)
  // Initialize anyChange after defining all filters
  val anyChange: BooleanProperty = MultiChangeWatcher(callsignFilter.optionValueProperty,
    bandFilter.value,
    modeFilter.value,
    transmittersFilter.value,
    classFilter.value,
    operatorFilter.value,
    nodeFilter.value,
    expandedProperty)
  val node: VBox = new VBox()

  private def isSearching: Boolean =
    expandedProperty.value && (callsignFilter.value.isDefined ||
      comboSelection(
        bandFilter
      ).isDefined ||
      comboSelection(
        modeFilter
      ).isDefined ||
      transmittersFilter.value.value.isDefined ||
      comboSelection(
        classFilter
      ).isDefined ||
      comboSelection(
        operatorFilter
      ).isDefined ||
      comboSelection(
        nodeFilter
      ).isDefined)

  anyChange.onChange((_, _, newVal) =>
    if contestManager.hasConfiguration.value then
      val searchTimerContext = searchTimer.time()
      val searchResult =
        try
          qsoStore.qsoCollection.filter(qso => filter(qso))
        finally
          searchTimerContext.stop()

      if isSearching then
        qsoTablePane.showSearchResults(searchResult)
      else
        qsoTablePane.restoreQsoCollection()
  )
  private val exportButton = new Button("Export..."):
    onAction = _ => showExportMenu()
  private val resetButton = {
    val btn = IconButton("x-octagon", size = 20, tooltipText = "Reset Filters", color = Color.Red)
    btn.onAction = _ =>
      callsignFilter.text = ""
      bandFilter.value = None
      modeFilter.value = None
      classFilter.value = None
      transmittersFilter.value = None
      operatorFilter.value = None
      nodeFilter.value = None
    btn
  }
  private var uiBuilt = false

  def filter(qso: Qso): Boolean = {
    if !contestManager.hasConfiguration.value then return true
    val callSignFilterVal = callsignFilter.value.getOrElse("").toUpperCase
    val bandFilterVal = comboSelection(
      bandFilter
    )
    val modeFilterVal = comboSelection(
      modeFilter
    )
    val classFilterVal = comboSelection(
      classFilter
    )
    val nodeFilterVal = comboSelection(
      nodeFilter
    )
    val operatorFilterVal = comboSelection(
      operatorFilter
    )

    val matchesCallsign = callSignFilterVal.isEmpty || qso.callsign.value.toUpperCase.contains(callSignFilterVal)
    val matchesBand = bandFilterVal.isEmpty || qso.bandMode.band == bandFilterVal.get
    val matchesMode = modeFilterVal.isEmpty || qso.bandMode.mode == modeFilterVal.get
    val matchesClass = classFilterVal.isEmpty || qso.exchange.fdClass.classLetter.toString.toUpperCase == classFilterVal.get.toString.toUpperCase
    val matchesTransmitters = transmittersFilter.check(qso.exchange.fdClass.transmitters)
    val matchesOperator = operatorFilterVal.isEmpty || qso.qsoMetadata.station.operator.value == operatorFilterVal.get
    val matchesNode = nodeFilterVal.isEmpty || qso.qsoMetadata.node == nodeFilterVal.get

    matchesCallsign && matchesBand && matchesMode && matchesClass && matchesTransmitters && matchesOperator && matchesNode
  }

  private def comboSelection[T](
                                combo: AnyComboBox[T]
                              ): Option[T] =
    Option(
      combo.value.value
    ).flatten

  def showExportMenu(): Unit =
    val menu = new ContextMenu()
    val jsonItem = new MenuItem("Export as JSON")
    jsonItem.onAction = _ => exportData(true)
    val csvItem = new MenuItem("Export as CSV")
    csvItem.onAction = _ => exportData(false)
    menu.items ++= Seq(jsonItem, csvItem)
    val bounds = exportButton.localToScreen(exportButton.boundsInLocal.value)
    if bounds != null then
      menu.show(exportButton, bounds.getMinX, bounds.getMaxY)
    else
      // Fallback if not visible or similar
      menu.show(node.getScene.getWindow)

  private def exportData(asJson: Boolean): Unit =
    val fileChooser = new FileChooser()
    fileChooser.title = if asJson then "Export QSOs as JSON" else "Export QSOs as CSV"
    fileChooser.extensionFilters.add(
      if asJson then new FileChooser.ExtensionFilter("JSON Files", "*.json")
      else new FileChooser.ExtensionFilter("CSV Files", "*.csv")
    )
    val file = fileChooser.showSaveDialog(node.getScene.getWindow)
    if file != null then
      val filteredQsos = qsoTablePane.displayedQsos
      val writer = new PrintWriter(file)
      try
        if asJson then
          writer.print(filteredQsos.asJson.spaces2)
        else
          writer.println(Qso.csvHeader)
          filteredQsos.foreach { qso =>
            val flat = qso.flatten
            val row = Seq("Time", "Their Call", "Class", "Section", "Band", "Mode", "Operator", "Rig", "Antenna", "Node", "Version")
              .map(k => flat.getOrElse(k, ""))
              .map(v => if v.contains(",") then s"\"$v\"" else v)
              .mkString(",")
            writer.println(row)
          }
      finally
        writer.close()

  def focusSearch(): Unit =
    callsignFilter.requestFocus()

  def buildUi(): Unit =
    if uiBuilt then return
    updateClassChoices(
      contestManager.contestConfigProperty.value.map(_.contestType)
    )
    updateQsoChoiceFilters()

    val titledPane = new TitledPane {
      text = "Search"
      content = new VBox {
        spacing = 10
        children = Seq(
          new FlowPane {
            hgap = 10
            vgap = 10
            alignment = scalafx.geometry.Pos.CenterLeft
            children = Seq(
              new VBox { children = Seq(new Label("Callsign"), callsignFilter) },
              new VBox { children = Seq(new Label("Band"), bandFilter) },
              new VBox { children = Seq(new Label("Mode"), modeFilter) },
              new VBox { children = Seq(new Label("Class"), classFilter) },
              new VBox { children = Seq(new Label("Transmitters"), transmittersFilter) },
              new VBox { children = Seq(new Label("Operator"), operatorFilter) },
              new VBox { children = Seq(new Label("Node"), nodeFilter) },
              new VBox {
                alignment = scalafx.geometry.Pos.BottomLeft
                spacing = 10
                children = Seq(
                  new HBox {
                    spacing = 10
                    children = Seq(resetButton, exportButton)
                  }
                )
              }
            )
          }
        )
      }
      expanded <==> expandedProperty
      collapsible = true
    }
    node.children = Seq(titledPane)
    uiBuilt = true

  private val qsoCollectionListener: ListChangeListener[Qso] =
    (_: ListChangeListener.Change[? <: Qso]) => updateQsoChoiceFilters()
  qsoStore.qsoCollection.delegate.addListener(qsoCollectionListener)

  contestManager.contestConfigProperty.onChange(
    (_, _, newConfig) =>
      updateClassChoices(
        newConfig.map(_.contestType)
      )
  )

  private def updateClassChoices(
                                  contestType: Option[ContestType]
                                ): Unit =
    contestType.flatMap(contestCatalog.getContest) match
      case Some(contestDefinition) =>
        val classChoices: Seq[ClassChoice] = contestDefinition.classChoices
        classFilter.setChoices(classChoices*)
      case None =>
        contestType.foreach { value =>
          logger.warn(s"Missing contest definition for $value; clearing class choices")
        }
        classFilter.setChoices()
        classFilter.value = None

  private def updateQsoChoiceFilters(): Unit =
    updateOperatorChoices()
    updateNodeChoices()

  private def updateOperatorChoices(): Unit =
    val selected = comboSelection(operatorFilter)
    val operators = qsoStore.qsoCollection
      .map(_.qsoMetadata.station.operator.value)
      .filter(_.nonEmpty)
      .toSet
      .toSeq
      .sorted

    operatorFilter.setChoices(operators.map(ChoiceItem(_))*)
    operatorFilter.value = selected.filter(operators.contains)

  private def updateNodeChoices(): Unit =
    val selected = comboSelection(nodeFilter)
    val nodes = qsoStore.qsoCollection
      .map(_.qsoMetadata.node)
      .toSet
      .toSeq
      .sortBy(node => (node.hostName, node.port, node.instanceId))

    nodeFilter.setChoices(nodes.map(ChoiceItem(_))*)
    nodeFilter.value = selected.filter(nodes.contains)
