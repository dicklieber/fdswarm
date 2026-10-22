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

import fdswarm.fx.GridBuilder
import fdswarm.fx.utils.{BootstrapIcons, IntLabel}
import fdswarm.replication.NodeStatus
import fdswarm.util.{AgeStyleService, DurationFormat, NodeIdentity}
import scalafx.beans.property.LongProperty
import scalafx.scene.control.{Button, Label, Tooltip}
import scalafx.scene.layout.HBox

import java.time.Instant

class SwarmStatusGrid(
  allNodes: Seq[NodeStatus],
  nowProperty: LongProperty,
  ageStyleService: AgeStyleService,
  ourInstanceId: String,
  removeNode: NodeIdentity => Unit
):

  def populate(builder: GridBuilder, rowStyleCallback: Seq[IntLabel] => String): Unit =
    // Header rows
    builder("InstanceId", allNodes.map { node =>
      val label = new Label(node.nodeIdentity.instanceId)
      if (node.nodeIdentity.instanceId == ourInstanceId) {
        label
      } else {
        val removeButton = new Button {
          graphic = BootstrapIcons.svgPath("trash-fill", size = 12, color = scalafx.scene.paint.Color.White)
          tooltip = Tooltip("Remove")
          styleClass += "delete-button"
          onAction = _ => removeNode(node.nodeIdentity)
        }
        new HBox {
          spacing = 5
          children = Seq(label, removeButton)
        }
      }
    }*)
    builder("Host", allNodes.map(_.nodeIdentity.hostIp)*)
    builder("Contest", allNodes.map(node => node.contestConfig.map(_.display)
      .getOrElse(if node.statusMessage.contestConfig.isDefined then "Details pending" else "Not configured"))*)
    builder("Age", allNodes.map { receivedNodeStatus =>
      if (receivedNodeStatus.nodeIdentity.instanceId == ourInstanceId) {
        new scalafx.scene.control.Label {
          text = "Our Node"
          tooltip = Tooltip("Our, local, node is always current")
          styleClass.addAll("grid-value", "ourNode")
        }
      } else {
        val binding = scalafx.beans.binding.Bindings.createStringBinding(
          () => {
            val now = Instant.ofEpochMilli(nowProperty.value)
            val styleAndAge = ageStyleService.calc("node", receivedNodeStatus.received, now)
            DurationFormat(styleAndAge.age)
          },
          nowProperty
        )
        new scalafx.scene.control.Label {
          text <== binding
          styleClass.addAll("grid-value")
          def updateStyle(): Unit = {
            val now = Instant.ofEpochMilli(nowProperty.value)
            val styleAndAge = ageStyleService.calc("node", receivedNodeStatus.received, now)
            styleClass.removeAll("fresh", "recent", "stale")
            styleClass.add(styleAndAge.style)
          }
          updateStyle()
          nowProperty.onChange { (_, _, _) =>
            updateStyle()
          }
        }
      }
    }*)
    builder("Qso Count", allNodes.map(receivedNodeStatus =>
      receivedNodeStatus.statusMessage.storeStats.qsoCount.toString)*)
    builder("Our QSOs", allNodes.map(receivedNodeStatus =>
      receivedNodeStatus.statusMessage.storeStats.ourQsoCount.toString)*)
    builder(
      "Hash",
      allNodes.map(
        receivedNodeStatus =>
          val fullHash = receivedNodeStatus.statusMessage.storeStats.hash
          new Label {
            text = fullHash.take(5)
            tooltip = Tooltip(
              fullHash
            )
          }
      )*
    )
    builder("Operator", allNodes.map(receivedNodeStatus =>
      receivedNodeStatus.statusMessage.bandNodeOperator.operator.toString)*)
    builder("Band/Mode", allNodes.map(receivedNodeStatus =>
      receivedNodeStatus.statusMessage.bandNodeOperator.bandMode.toString)*)
