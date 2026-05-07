package monitor

import fdswarm.DirectoryProvider
import fdswarm.logging.LazyStructuredLogging
import fdswarm.util.NodeIdentity
import fdswarm.util.Ids.Id
import _root_.io.circe.Printer
import _root_.io.circe.parser.decode
import _root_.io.circe.syntax.*
import jakarta.inject.{Inject, Singleton}
import scalafx.collections.ObservableBuffer

import scala.collection.concurrent.TrieMap

/**
  * What the monitor knows.
  */
@Singleton
class NodeStore @Inject() (directoryProvider: DirectoryProvider) extends LazyStructuredLogging:
  private val _nodes = TrieMap[NodeIdentity, NodeData]()
  private val offsetFile = directoryProvider() / "last-index-offsets.json"
  private val printer = Printer.spaces2.copy(dropNullValues = true)
  private val lastIndexOffsets = TrieMap.from(loadOffsets())

  val observableNodes: ObservableBuffer[NodeData] = ObservableBuffer[NodeData]()

  def statusReceived(nodeIdentity: NodeIdentity): Unit = _nodes.get(nodeIdentity) match
    case Some(existingNodeData) => existingNodeData.updateLastStatus()
    case None                   =>
      val nodeData = NodeData(
        nodeIdentity,
        lastIndexOffsets.getOrElse(nodeIdentity.instanceId, IndexOperation.Never.offset)
      )
      _nodes.put(nodeIdentity, nodeData)
      observableNodes += nodeData

  def updateNodeData(nodeIdentity: NodeIdentity, indexOperation: IndexOperation): Unit =
    val nodeData = _nodes(nodeIdentity) // throws if we have none, should never happen.
    nodeData.updateLastIndexOp(indexOperation)
    persistOffset(nodeIdentity.instanceId, indexOperation.offset)

  def nodes: Seq[NodeData] =
    _nodes.values.toSeq

  def clear(): Unit = synchronized:
    _nodes.clear()
    observableNodes.clear()
    lastIndexOffsets.clear()
    if os.exists(offsetFile) then os.remove(offsetFile)
    logger.info(s"NodeStore cleared and $offsetFile deleted")

  private def persistOffset(instanceId: Id, offset: Long): Unit = synchronized:
    lastIndexOffsets.update(instanceId, offset)
    os.write.over(offsetFile, printer.print(lastIndexOffsets.toMap.asJson), createFolders = true)

  private def loadOffsets(): Map[Id, Long] =
    try
      if os.exists(offsetFile) then
        decode[Map[Id, Long]](os.read(offsetFile)) match
          case Right(offsets) => offsets
          case Left(err) =>
            logger.error(s"Failed to decode monitor index offsets from $offsetFile: $err")
            Map.empty
      else Map.empty
    catch
      case e: Exception =>
        logger.error(s"Failed to load monitor index offsets from $offsetFile: ${e.getMessage}")
        Map.empty
