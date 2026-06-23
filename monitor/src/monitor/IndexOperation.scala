package monitor

import fdswarm.util.NodeIdentity
import scalafx.beans.property.ObjectProperty

import java.time.Instant

/**
  * @param nodeIdentity which node.
  */
class NodeData(val nodeIdentity: NodeIdentity):
  val lastStatus = ObjectProperty[Instant](this, "lastStatus", Instant.now())

  def updateLastStatus(): Unit = lastStatus.value = Instant.now()
