/*
 * Copyright (C) 2022 Dick Lieber, WA9NNN
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
 */

package fdswarm.util

import fdswarm.Ports
import fdswarm.util.Ids.Id
import io.circe.*
import sttp.tapir.Schema

import java.net.InetAddress

/**
  * Represents the identity of a network node, encapsulating details such as hostip, hostName, port,
  * and an instance ID.
  *
  * @param hostIp The hostname or IP address of the node. This will default to "local". When
  *   received host will be replaced with the source of the UDP message.
  *
  * @param port The port number on which the node is reachable.
  * @param hostName The hostname of the node.
  * @param instanceId A unique identifier for the instance of the node.
  *
  * Extends the `Ordered` trait to allow comparison of `NodeIdentity` instances based on host and
  * port.
  *
  * Methods:
  *   - `toString`: Returns a string representation of the node in the format
  *     `host:port-instanceId`.
  *   - `shortHost`: A lazily evaluated property that provides a compact host and port.
  *   - `toURL`: Converts the node's information into a URL string.
  *   - `toURI`: Converts the node's information into a URI instance using the scheme "http".
  *   - `compare`: Compares two `NodeIdentity` instances first by host, then by port.
  */
case class NodeIdentity( hostIp: String, port: Int, hostName: String, instanceId: Id)
    extends Ordered[NodeIdentity] derives Codec.AsObject, Schema:

  lazy val shortHost: String = s"$hostName:$port"
    /**
      * String representation. This is the complement to the [[NodeIdentity.apply(s:String)]]
      * method.
      */
  val udpHeaderPiece: String = f"${hostIp}_${port}_${hostName}_$instanceId"

//  /** UDP header piece is used to identify the node. */
//  val udpHeaderPiece: String =
//    toString
//  val external: String = s"$hostName:$instanceId"

  override def toString: String =
    val sPort = if Ports.hasMultiplePorts then
      s":$port"
    else
      ""
    s"$hostName$sPort"

  override def compare(that: NodeIdentity): Int =
    this.hostName.compareTo(that.hostName)

  override def equals(that: Any): Boolean = that match
    case other: NodeIdentity => this.instanceId == other.instanceId
    case _                   => false

  override def hashCode: Int = instanceId.hashCode

  def hostPort: String = s"$hostIp:$port"

object NodeIdentity:
  val mockNodeIdentity = NodeIdentity(
    hostIp = "44.0.0.1",
    port = 42,
    hostName = "testHost",
    instanceId = "=id"
  )

  /**
    * @param address from packet.getAddress as received from UDP.
    * @param udpPiece for header.
    */
  def fromUdpHeader(
      address: InetAddress,
      udpPiece: String
  ): NodeIdentity =
    apply(
      udpPiece
    )
      .copy(
        hostIp = address.getHostAddress
      )

  /**
    * @param s from [[NodeIdentity.udpHeaderPiece]]
    * @return
    */
  def apply(s: String): NodeIdentity =
    val parts = s.split("_", 4)
    if parts.length != 4 then
      throw new IllegalArgumentException(s"Invalid NodeIdentity: $s")

    val Array(hostIp, sPort, hostName, instanceId) = parts
    NodeIdentity(
      hostIp = hostIp,
      port = sPort.toInt,
      hostName = hostName,
      instanceId = instanceId
    )
