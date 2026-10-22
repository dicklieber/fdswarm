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

package fdswarm.replication

import fdswarm.util.NodeIdentity
import fdswarm.fx.contest.ContestConfig

import java.time.Instant

/** This is what we get from a remote node.
  *
  * @param statusMessage
  *   the actual Node Status as sent by a node.
  * @param nodeIdentity
  *   the node that sent it.
  * @param received
  *   when we got it.
  * @param isLocal
  *   true if this is a local node, i.e. not from another node.
  */
case class NodeStatus(
    statusMessage: StatusMessage,
    nodeIdentity: NodeIdentity,
    received: Instant = Instant.now,
    isLocal: Boolean,
    contestConfig: Option[ContestConfig] = None
) extends Ordered[NodeStatus]:

  override def compare(that: NodeStatus): Int =
    val localOrder = java.lang.Boolean.compare(that.isLocal, this.isLocal)
    if localOrder != 0 then localOrder
    else
      val hostOrder = this.nodeIdentity.hostName.compareTo(that.nodeIdentity.hostName)
      if hostOrder != 0 then hostOrder
      else this.nodeIdentity.instanceId.compareTo(that.nodeIdentity.instanceId)
