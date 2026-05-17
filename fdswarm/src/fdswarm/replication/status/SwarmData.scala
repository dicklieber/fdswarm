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

package fdswarm.replication.status

import fdswarm.{FdSwarmUi, Ports}
import fdswarm.fx.station.StationEditor
import fdswarm.fx.{GridBuilder}
import fdswarm.logging.LazyStructuredLogging
import fdswarm.replication.{NodeStatus, NodeStatusDispatcher, Service}
import fdswarm.util.{NodeIdentity, NodeIdentityManager}
import jakarta.inject.{Inject, Singleton}
import javafx.beans.value.ChangeListener
import javafx.collections.ListChangeListener
import javafx.event.EventHandler
import javafx.scene.input.MouseEvent
import scalafx.application.Platform
import scalafx.beans.binding.Bindings
import scalafx.beans.property.{BooleanProperty, IntegerProperty, StringProperty}
import scalafx.collections.ObservableBuffer
import scalafx.scene.control.{Label, ScrollPane, Tooltip}
import scalafx.scene.layout.{GridPane, StackPane}
import scalafx.scene.{Node, Parent}

import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicLong
import scala.collection.concurrent.TrieMap

/** Holds data about each node in the swarm.
  */
@Singleton
class SwarmData @Inject() (
    stationEditor: StationEditor,
    ageCellStyleRefresher: AgeCellStyleRefresher,
    nodeStatusDispatcher: NodeStatusDispatcher
) extends LazyStructuredLogging():
  type CellNodeListener = (NodeStatus, String, Node) => Unit
  val knownNodeIdentity: ObservableBuffer[NodeIdentity] = ObservableBuffer.empty[NodeIdentity]
  val nodeMap: TrieMap[NodeIdentity, NodeStatus] = TrieMap.empty[NodeIdentity, NodeStatus]
  val size: IntegerProperty = new IntegerProperty(
    this,
    "size",
    0
  )
  val contestConfigMismatchProperty: BooleanProperty = new BooleanProperty(
    this,
    "contestConfigMismatch",
    false
  )
  private val valueProperties = TrieMap.empty[(NodeIdentity, NodeDataField), StringProperty]
  private val renderedCellNodes = TrieMap.empty[(NodeIdentity, NodeDataField), Vector[Node]]
  private val nodeStatusListeners = TrieMap.empty[Long, Seq[NodeStatus] => Unit]
  private val cellNodeListeners = TrieMap.empty[Long, CellNodeListener]
  private val nodeStatusListenerId = AtomicLong(0L)
  private val cellNodeListenerId = AtomicLong(0L)
  private val ourNodeStyleClass = "ourNode"
  private val operatorLinkStyleClass = "operatorLink"
  private val contestConfigMajorityStyleClass = "contestConfigMajority"
  private val contestConfigVariantStyleClasses = Vector(
    "contestConfigVariant0",
    "contestConfigVariant1",
    "contestConfigVariant2",
    "contestConfigVariant3"
  )
  private val contestConfigAllColorStyleClasses =
    contestConfigMajorityStyleClass +: contestConfigVariantStyleClasses
  private val operatorFieldName = NodeDataField.Operator.label
  private val stampFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss")
    .withZone(ZoneId.systemDefault())
  private var contestConfigFieldStyleByNodeAndField =
    Map.empty[(NodeIdentity, NodeDataField), String]

  def addNodeStatusListener(listener: Seq[NodeStatus] => Unit): () => Unit =
    val id = nodeStatusListenerId.incrementAndGet()
    nodeStatusListeners.put(id, listener)
    listener(allNodeStatuses)
    () => nodeStatusListeners.remove(id)

  ageCellStyleRefresher.setPurgeCallback(nodeIdentity =>
    logger.debug("Purging", "Node" -> nodeIdentity.toString)
    remove(nodeIdentity)
  )
  nodeStatusDispatcher.addListener(
    service = Service.Status,
    singleListener = false
  )((nodeIdentity, statusMessage) =>
    update(
      NodeStatus(
        statusMessage = statusMessage,
        nodeIdentity = nodeIdentity,
        isLocal = false
      )
    )
  )

  def clear(): Unit =
    val localStatus = nodeMap.get(NodeIdentityManager.nodeIdentity)
    nodeMap.keys.foreach(nodeIdentity => ageCellStyleRefresher.remove(nodeIdentity))
    nodeMap.clear()
    localStatus.foreach(status => nodeMap.put(status.nodeIdentity, status))
    val retainedNodes = localStatus.map(_.nodeIdentity).toSet
    purgeStaleNodeCaches(
      retainedNodes = retainedNodes
    )
    updateKnownCollectionsFromNodeMap()
    refreshContestConfigDifferenceState()
    notifyNodeStatusListeners()
    logger.debug("Cleared swarm status data, retaining local node.")

  private def purgeStaleNodeCaches(
      retainedNodes: Set[NodeIdentity]
  ): Unit =
    val staleNodes = valueProperties.keysIterator
      .map(_._1)
      .filterNot(retainedNodes.contains)
      .toSet ++ renderedCellNodes.keysIterator
      .map(_._1)
      .filterNot(retainedNodes.contains)
      .toSet
    staleNodes.foreach(nodeIdentity =>
      purgeNodeCaches(
        nodeIdentity = nodeIdentity
      )
    )

  def remove(nodeIdentity: NodeIdentity): Unit = if nodeIdentity != NodeIdentityManager.nodeIdentity then
    nodeMap.remove(nodeIdentity).foreach(_ => ageCellStyleRefresher.remove(nodeIdentity))
    purgeNodeCaches(
      nodeIdentity = nodeIdentity
    )
    updateKnownCollectionsFromNodeMap()
    refreshContestConfigDifferenceState()
    notifyNodeStatusListeners()
    logger.debug(s"Removed node status for $nodeIdentity")

  private def updateKnownCollectionsFromNodeMap(): Unit =
    val nodeStatuses = nodeMap.values.toSeq
    val nodes = nodeStatuses.sorted.map(_.nodeIdentity).distinct
    updateOnFxThread {
      knownNodeIdentity.clear()
      knownNodeIdentity ++= nodes
      size.value = nodes.size
    }

  private def notifyNodeStatusListeners(): Unit =
    val statuses = allNodeStatuses
    nodeStatusListeners.values.foreach(listener => listener(statuses))

  def allNodeStatuses: Seq[NodeStatus] = nodeMap.values.toSeq

  private def refreshContestConfigDifferenceState(): Unit =
    val stylesByNodeAndField = SwarmData.contestConfigFieldStyles(
      statuses = allNodeStatuses
    )
    val changed = stylesByNodeAndField != contestConfigFieldStyleByNodeAndField
    contestConfigFieldStyleByNodeAndField = stylesByNodeAndField
    updateOnFxThread {
      contestConfigMismatchProperty.value = stylesByNodeAndField.nonEmpty
      if changed then
        refreshContestConfigFieldCellStyles()
    }

  private def updateOnFxThread(action: => Unit): Unit =
    if Platform.isFxApplicationThread then action
    else
      try Platform.runLater(() => action)
      catch case _: IllegalStateException => action

  private def refreshContestConfigFieldCellStyles(): Unit =
    val contestFields = SwarmData.rowCellDifferenceValueColorFields.toSet
    renderedCellNodes.foreach { case ((nodeIdentity, field), cells) =>
      if contestFields.contains(field) then
        nodeMap.get(nodeIdentity).foreach(nodeStatus =>
          notifyCellNodeListeners(
            nodeStatus = nodeStatus,
            field = field,
            targetCells = cells
          )
        )
    }

  private def purgeNodeCaches(
      nodeIdentity: NodeIdentity
  ): Unit =
    valueProperties.keysIterator
      .filter(_._1 == nodeIdentity)
      .toList
      .foreach(key => valueProperties.remove(key))
    renderedCellNodes.keysIterator
      .filter(_._1 == nodeIdentity)
      .toList
      .foreach(key => renderedCellNodes.remove(key))

  def update(nodeStatus: NodeStatus): Unit =
    val normalizedNodeStatus = SwarmData.normalizeLocalNodeStatus(
      nodeStatus = nodeStatus,
      localNodeIdentity = NodeIdentityManager.nodeIdentity
    )
    val nodeIdentity = normalizedNodeStatus.nodeIdentity
    if nodeMap.put(nodeIdentity, normalizedNodeStatus).isEmpty then Ports.port(nodeIdentity)
    ageCellStyleRefresher.track(nodeStatus = normalizedNodeStatus)

    updateOnFxThread {
      val staticValues = staticFieldValues(normalizedNodeStatus)
      staticValues.foreach { case (field, value) =>
        propertyFor(nodeIdentity, field).value = value
        notifyCellNodeListeners(normalizedNodeStatus, field)
      }
    }
    updateKnownCollectionsFromNodeMap()
    refreshContestConfigDifferenceState()
    notifyNodeStatusListeners()

  def buildGridPane(
      fields: Seq[NodeDataField]
  ): Parent =
    buildGridPane(
      fields = fields,
      bottomRow = None
    )

  def buildGridPane(
      fields: Seq[NodeDataField],
      bottomRow: Option[SwarmData.BottomRow]
  ): Parent =
    val gridContainer = new StackPane()
    val scrollPane = new ScrollPane:
      content = gridContainer
      hbarPolicy = ScrollPane.ScrollBarPolicy.Always
      vbarPolicy = ScrollPane.ScrollBarPolicy.Never
      fitToHeight = false
      fitToWidth = false
      pannable = true
    var renderedByThisPane = Map.empty[(NodeIdentity, NodeDataField), Seq[Node]]

    def rebuildGrid(): Unit =
      unregisterRenderedCells(renderedByThisPane)
      val result = buildGrid(
        fields = fields.distinct,
        bottomRow = bottomRow
      )
      renderedByThisPane = result.cellNodes
      registerRenderedCells(renderedByThisPane)
      gridContainer.children.setAll(result.grid)

    rebuildGrid()
    val nodeListener: ListChangeListener[NodeIdentity] =
      (_: ListChangeListener.Change[? <: NodeIdentity]) => rebuildGrid()
    knownNodeIdentity.delegate.addListener(nodeListener)

    // Remove listener once the wrapper leaves the scene graph.
    val sceneListener: ChangeListener[javafx.scene.Scene] = new ChangeListener[javafx.scene.Scene]:
      override def changed(
          observable: javafx.beans.value.ObservableValue[? <: javafx.scene.Scene],
          oldScene: javafx.scene.Scene,
          newScene: javafx.scene.Scene
      ): Unit = if newScene == null then
        knownNodeIdentity.delegate.removeListener(nodeListener)
        unregisterRenderedCells(renderedByThisPane)
        renderedByThisPane = Map.empty
        scrollPane.delegate.sceneProperty.removeListener(this)
    scrollPane.delegate.sceneProperty.addListener(sceneListener)

    scrollPane

  private def buildGrid(
      fields: Seq[NodeDataField],
      bottomRow: Option[SwarmData.BottomRow]
  ): GridBuildResult =
    val builder = GridBuilder()
    builder.hgap = 1
    builder.vgap = 1
    builder.padding = scalafx.geometry.Insets(1)
    builder.style = "-fx-background-color: #808080; -fx-background-insets: 0;"
    val nodes = knownNodeIdentity.toSeq
    val cellsByField =
      scala.collection.mutable.Map.empty[(NodeIdentity, NodeDataField), Vector[Node]]
    fields.foreach { field =>
      val values = nodes.map(node =>
        val cellNode = buildCellNode(node, field)
        val key = (node, field)
        val updated = cellsByField.getOrElse(key, Vector.empty) :+ cellNode
        cellsByField.update(key, updated)
        cellNode
      )
      builder(field.label, values*)
    }
    bottomRow.foreach(footer =>
      val values = nodes.map(node =>
        footer.cellBuilder(
          node
        )
      )
      builder(footer.label, values*)
    )
    val grid = builder.result
    grid.styleClass += "swarm-status-grid"
    GridBuildResult(grid, cellsByField.view.mapValues(_.toSeq).toMap)

  private def buildCellNode(node: NodeIdentity, field: NodeDataField): Node =
    val valueProperty = propertyFor(node, field)
    if field == NodeDataField.Hash then
      val shortHashBinding = Bindings
        .createStringBinding(() => Option(valueProperty.value).getOrElse("").take(5), valueProperty)
      new Label:
        text <== shortHashBinding
        tooltip = new Tooltip:
          text <== valueProperty
    else GridBuilder.valueToLabel(valueProperty)

  private def propertyFor(node: NodeIdentity, field: NodeDataField): StringProperty =
    valueProperties
      .getOrElseUpdate((node, field), StringProperty(""))

  private def registerRenderedCells(cellsByField: Map[(NodeIdentity, NodeDataField), Seq[Node]])
      : Unit = cellsByField
    .foreach { case (key, cells) =>
      val merged = renderedCellNodes.getOrElse(key, Vector.empty) ++ cells
      renderedCellNodes.put(key, merged)
      nodeMap.get(key._1).foreach(status =>
        cells.foreach(cell => notifyCellNodeListeners(status, key._2, Seq(cell)))
      )
    }

  private def notifyCellNodeListeners(
      nodeStatus: NodeStatus,
      field: NodeDataField,
      targetCells: Seq[Node]
  ): Unit =
    targetCells.foreach(cell =>
      doStyle(CellStyleContext(nodeStatus, field.label, cell))
      if cellNodeListeners.nonEmpty then
        cellNodeListeners.values.foreach(listener => listener(nodeStatus, field.label, cell))
    )

  /** Applies the appropriate styling and behavior to a given node based on its status and field
    * name.
    *
    * @param nodeStyler
    *   wraps the status, field, and node to which styling rules will be applied
    * @return
    *   Unit this method does not return a value; it modifies the node's style and event handlers
    *   directly
    */
  private def doStyle(nodeStyler: CellStyleContext): Unit =
    val nodeStatus = nodeStyler.nodeStatus
    val fieldName = nodeStyler.fieldName
    val field = NodeDataField.values.find(_.label == fieldName)
    val node = nodeStyler.node
    // Keep all current and future styling rules in one place.
    ensureStyleClass(
      node = node,
      styleClassName = ourNodeStyleClass,
      shouldHaveStyleClass = nodeStatus.isLocal
    )

    val isLocalOperatorField = nodeStatus.isLocal && fieldName == operatorFieldName
    ensureStyleClass(
      node = node,
      styleClassName = operatorLinkStyleClass,
      shouldHaveStyleClass = isLocalOperatorField
    )
    applyContestConfigColorStyle(
      nodeStatus = nodeStatus,
      field = field,
      node = node
    )
    if isLocalOperatorField then
      node.delegate.setOnMouseClicked(
        new EventHandler[MouseEvent]:
          override def handle(event: MouseEvent): Unit =
            stationEditor.show(FdSwarmUi.primaryStage)
      )
    else node.delegate.setOnMouseClicked(null)

    if fieldName == NodeDataField.Received.label then ageCellStyleRefresher.add(nodeStyler)

  private def applyContestConfigColorStyle(
      nodeStatus: NodeStatus,
      field: Option[NodeDataField],
      node: Node
  ): Unit =
    contestConfigAllColorStyleClasses.foreach(styleClassName =>
      ensureStyleClass(
        node = node,
        styleClassName = styleClassName,
        shouldHaveStyleClass = false
      )
    )
    field
      .filter(_.colorDeffCells)
      .flatMap(field =>
        contestConfigFieldStyleByNodeAndField.get(
          (nodeStatus.nodeIdentity, field)
        )
      )
      .foreach(styleClassName =>
        ensureStyleClass(
          node = node,
          styleClassName = styleClassName,
          shouldHaveStyleClass = true
        )
      )

  private def ensureStyleClass(
      node: Node,
      styleClassName: String,
      shouldHaveStyleClass: Boolean
  ): Unit =
    if shouldHaveStyleClass then
      if !node.styleClass.contains(styleClassName) then node.styleClass += styleClassName
    else
      while node.styleClass.contains(styleClassName) do node.styleClass -= styleClassName

  private def unregisterRenderedCells(cellsByField: Map[(NodeIdentity, NodeDataField), Seq[Node]])
      : Unit = cellsByField
    .foreach { case (key, removedCells) =>
      renderedCellNodes.get(key).foreach(existing =>
        val remaining = existing
          .filterNot(existingCell =>
            removedCells.exists(removed => removed.delegate eq existingCell.delegate)
          )
        if remaining.isEmpty then renderedCellNodes.remove(key)
        else renderedCellNodes.put(key, remaining)
      )
    }

  private def staticFieldValues(nodeStatus: NodeStatus): Map[NodeDataField, String] =
    val bno = nodeStatus.statusMessage.bandNodeOperator
    val contest = nodeStatus.statusMessage.contestConfig
    Map(
      NodeDataField.HostIp -> nodeStatus.nodeIdentity.hostIp,
      NodeDataField.Port -> nodeStatus.nodeIdentity.port.toString,
      NodeDataField.HostName -> nodeStatus.nodeIdentity.hostName,
      NodeDataField.InstanceId -> nodeStatus.nodeIdentity.instanceId,
      NodeDataField.Received -> stampFormatter.format(nodeStatus.received),
      NodeDataField.IsLocal -> nodeStatus.isLocal.toString,
      NodeDataField.QsoCount -> nodeStatus.statusMessage.storeStats.qsoCount.toString,
      NodeDataField.OurQsoCount -> nodeStatus.statusMessage.storeStats.ourQsoCount.toString,
      NodeDataField.Hash -> nodeStatus.statusMessage.storeStats.hash,
      NodeDataField.Operator -> bno.operator.toString,
      NodeDataField.Band -> bno.bandMode.band.name,
      NodeDataField.Mode -> bno.bandMode.mode.toString,
      NodeDataField.BandMode -> bno.bandMode.toString,
      NodeDataField.BandModeStamp -> stampFormatter.format(bno.stamp),
      NodeDataField.ContestType -> contest.contestType.toString,
      NodeDataField.ContestCallsign -> contest.ourCallsign.toString,
      NodeDataField.ContestTransmitters -> contest.transmitters.toString,
      NodeDataField.ContestClass -> contest.ourClass,
      NodeDataField.ContestSection -> contest.ourSection,
      NodeDataField.Exchange -> contest.exchange,
      NodeDataField.Host -> nodeStatus.nodeIdentity.toString,
      NodeDataField.ContestStart -> stampFormatter.format(
        nodeStatus.statusMessage.contestStart
      )
    )

  private def notifyCellNodeListeners(nodeStatus: NodeStatus, field: NodeDataField): Unit =
    val cells = renderedCellNodes.getOrElse((nodeStatus.nodeIdentity, field), Vector.empty)
    notifyCellNodeListeners(nodeStatus, field, cells)

  private case class GridBuildResult(
      grid: GridPane,
      cellNodes: Map[(NodeIdentity, NodeDataField), Seq[Node]]
  )

object SwarmData:
  private[status] def normalizeLocalNodeStatus(
      nodeStatus: NodeStatus,
      localNodeIdentity: NodeIdentity
  ): NodeStatus =
    if nodeStatus.nodeIdentity == localNodeIdentity then
      nodeStatus.copy(
        nodeIdentity = localNodeIdentity,
        isLocal = true
      )
    else nodeStatus

  private[status] val rowCellDifferenceValueColorFields: Seq[NodeDataField] =
    NodeDataField.values.filter(
      _.colorDeffCells
    ).toSeq

  private[status] def contestConfigFieldStyles(
      statuses: Seq[NodeStatus]
  ): Map[(NodeIdentity, NodeDataField), String] =
    val majorityStyleClass = "contestConfigMajority"
    val variantStyleClasses = Vector(
      "contestConfigVariant0",
      "contestConfigVariant1",
      "contestConfigVariant2",
      "contestConfigVariant3"
    )
    if statuses.size < 2 then
      Map.empty
    else
      rowCellDifferenceValueColorFields.foldLeft(
        Map.empty[(NodeIdentity, NodeDataField), String]
      )((acc, field) =>
        val valueByNode = statuses.flatMap(nodeStatus =>
          differenceColorFieldValue(
            nodeStatus = nodeStatus,
            field = field
          ).map(fieldValue => nodeStatus.nodeIdentity -> fieldValue)
        )
        if valueByNode.size != statuses.size then
          acc
        else
          val countsByValue = valueByNode
            .groupMap(_._2)(_ => 1)
            .view
            .mapValues(_.sum)
            .toMap
          val maxCount = countsByValue.values.maxOption.getOrElse(0)
          if countsByValue.size <= 1 || maxCount == 0 then
            acc
          else
            val topValues = countsByValue
              .collect { case (value, count) if count == maxCount => value }
              .toSet
            val majorityValues =
              if topValues.size == 1 then topValues else Set.empty[String]
            val nonMajorityValues = countsByValue.keys
              .filterNot(majorityValues.contains)
              .toSeq
              .sorted
            val nonMajorityStyleByValue = nonMajorityValues.zipWithIndex
              .map { case (value, idx) =>
                value -> variantStyleClasses(idx % variantStyleClasses.size)
              }
              .toMap
            val fieldStyles = valueByNode.map { case (nodeIdentity, value) =>
              val styleClass = if majorityValues.contains(value) then
                majorityStyleClass
              else
                nonMajorityStyleByValue(value)
              (nodeIdentity, field) -> styleClass
            }.toMap
            acc ++ fieldStyles
      )

  private def differenceColorFieldValue(
      nodeStatus: NodeStatus,
      field: NodeDataField
  ): Option[String] =
    field match
      case NodeDataField.QsoCount =>
        Some(nodeStatus.statusMessage.storeStats.qsoCount.toString)
      case NodeDataField.Hash =>
        Some(nodeStatus.statusMessage.storeStats.hash)
      case NodeDataField.ContestType =>
        Some(nodeStatus.statusMessage.contestConfig.contestType.toString)
      case NodeDataField.ContestCallsign =>
        Some(nodeStatus.statusMessage.contestConfig.ourCallsign.toString)
      case NodeDataField.ContestTransmitters =>
        Some(nodeStatus.statusMessage.contestConfig.transmitters.toString)
      case NodeDataField.ContestClass =>
        Some(nodeStatus.statusMessage.contestConfig.ourClass)
      case NodeDataField.ContestSection =>
        Some(nodeStatus.statusMessage.contestConfig.ourSection)
      case NodeDataField.Exchange =>
        Some(nodeStatus.statusMessage.contestConfig.exchange)
      case NodeDataField.ContestStart =>
        Some(nodeStatus.statusMessage.contestStart.toString)
      case _ =>
        None

  final case class BottomRow(
      label: String,
      cellBuilder: NodeIdentity => Node
  )
