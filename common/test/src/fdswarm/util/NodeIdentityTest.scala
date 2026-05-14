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

package fdswarm.util

import io.circe.parser.decode
import io.circe.syntax.*
import munit.FunSuite

import java.net.InetAddress

class NodeIdentityTest extends FunSuite:

  test("udp header piece direct round trip"):
    val nodeIdentity = NodeIdentity.mockNodeIdentity
    val udpPiece = nodeIdentity.udpHeaderPiece
    val backAgain = NodeIdentity(udpPiece)
    assertEquals(backAgain, nodeIdentity)

  test("udpPiece round trip"):
    val nodeIdentity = NodeIdentity.mockNodeIdentity
    val udpPiece = nodeIdentity.udpHeaderPiece
    // The UDPHeader mechanism doesn't send the IP address in the UDPHeader.
    // Instead, it uses the IP address of remote socket.
    val inetAddress = InetAddress.getByName(nodeIdentity.hostIp)
    val backAgain = NodeIdentity.fromUdpHeader(inetAddress, udpPiece)
    assertEquals(backAgain, nodeIdentity)

  test("circe round trip"):
    val nodeIdentity = NodeIdentity.mockNodeIdentity
    val sJson = nodeIdentity.asJson.spaces2
    assertEquals(sJson, """{
                         |  "hostIp" : "44.0.0.1",
                         |  "port" : 42,
                         |  "hostName" : "testHost",
                         |  "instanceId" : "=id"
                         |}""".stripMargin)
    val decoded = decode[NodeIdentity](sJson)
      .getOrElse(fail("failed to decode"))
    assertEquals(decoded, nodeIdentity)
