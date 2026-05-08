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

import fdswarm.fx.contest.{ContestConfig, ContestType}
import fdswarm.model.{BandMode, BandModeOperator, Callsign}
import fdswarm.replication.{StoreStats, NodeStatus, StatusMessage}
import fdswarm.util.NodeIdentity
import munit.FunSuite

import java.time.Instant

class SwarmDataTest extends FunSuite:
  test("static status fields include qsoCount, ourQsoCount, hash, and exchange columns"):
    assert(
      NodeDataField.staticFields.contains(
        NodeDataField.QsoCount
      )
    )
    assert(
      NodeDataField.staticFields.contains(
        NodeDataField.Hash
      )
    )
    assert(
      NodeDataField.staticFields.contains(
        NodeDataField.OurQsoCount
      )
    )
    assert(!NodeDataField.OurQsoCount.colorDeffCells)
    assert(
      NodeDataField.staticFields.contains(
        NodeDataField.Exchange
      )
    )

  test("row cell difference coloring is enabled for contest comparison fields"):
    assert(NodeDataField.QsoCount.colorDeffCells)
    assert(NodeDataField.Hash.colorDeffCells)
    assert(NodeDataField.ContestType.colorDeffCells)
    assert(NodeDataField.ContestCallsign.colorDeffCells)
    assert(NodeDataField.ContestTransmitters.colorDeffCells)
    assert(NodeDataField.ContestClass.colorDeffCells)
    assert(NodeDataField.ContestSection.colorDeffCells)
    assert(NodeDataField.Exchange.colorDeffCells)
    assert(NodeDataField.ContestStart.colorDeffCells)
    assertEquals(
      obtained = SwarmData.rowCellDifferenceValueColorFields.toSet,
      expected = Set(
        NodeDataField.QsoCount,
        NodeDataField.Hash,
        NodeDataField.ContestType,
        NodeDataField.ContestCallsign,
        NodeDataField.ContestTransmitters,
        NodeDataField.ContestClass,
        NodeDataField.ContestSection,
        NodeDataField.Exchange,
        NodeDataField.ContestStart
      )
    )

  test("contest config styles are empty when all nodes match"):
    val styles = SwarmData.contestConfigFieldStyles(
      statuses = Seq(
        nodeStatus(
          hostName = "alpha",
          contestConfig = contestConfig(
            contestType = ContestType.WFD,
            callsign = "W9AAA",
            transmitters = 1,
            stationClass = "A",
            section = "IL"
          )
        ),
        nodeStatus(
          hostName = "beta",
          contestConfig = contestConfig(
            contestType = ContestType.WFD,
            callsign = "W9AAA",
            transmitters = 1,
            stationClass = "A",
            section = "IL"
          )
        )
      )
    )
    assertEquals(
      obtained = styles,
      expected = Map.empty[(NodeIdentity, NodeDataField), String]
    )

  test("contest config styles mark majority green and minority as variants"):
    val styles = SwarmData.contestConfigFieldStyles(
      statuses = Seq(
        nodeStatus(
          hostName = "alpha",
          contestConfig = contestConfig(
            contestType = ContestType.WFD,
            callsign = "W9AAA",
            transmitters = 1,
            stationClass = "A",
            section = "IL"
          )
        ),
        nodeStatus(
          hostName = "beta",
          contestConfig = contestConfig(
            contestType = ContestType.ARRL,
            callsign = "W9BBB",
            transmitters = 2,
            stationClass = "A",
            section = "IL"
          )
        ),
        nodeStatus(
          hostName = "gamma",
          contestConfig = contestConfig(
            contestType = ContestType.WFD,
            callsign = "W9AAA",
            transmitters = 1,
            stationClass = "A",
            section = "IL"
          )
        )
      )
    )
    assert(
      styles(
        (
          NodeIdentity(
            hostIp = "10.0.0.1",
            port = 8090,
            hostName = "alpha",
            instanceId = "alpha-id"
          ),
          NodeDataField.Exchange
        )
      )
      == "contestConfigMajority"
    )
    assert(
      styles(
        (
          NodeIdentity(
            hostIp = "10.0.0.1",
            port = 8090,
            hostName = "beta",
            instanceId = "beta-id"
          ),
          NodeDataField.Exchange
        )
      )
      != "contestConfigMajority"
    )
    assert(
      styles(
        (
          NodeIdentity(
            hostIp = "10.0.0.1",
            port = 8090,
            hostName = "beta",
            instanceId = "beta-id"
          ),
          NodeDataField.ContestType
        )
      )
      != "contestConfigMajority"
    )

  test("qso count and hash mismatches receive difference coloring"):
    val styles = SwarmData.contestConfigFieldStyles(
      statuses = Seq(
        nodeStatus(
          hostName = "alpha",
          contestConfig = contestConfig(
            contestType = ContestType.WFD,
            callsign = "W9AAA",
            transmitters = 1,
            stationClass = "A",
            section = "IL"
          ),
          hash = "abc123",
          qsoCount = 100
        ),
        nodeStatus(
          hostName = "beta",
          contestConfig = contestConfig(
            contestType = ContestType.WFD,
            callsign = "W9AAA",
            transmitters = 1,
            stationClass = "A",
            section = "IL"
          ),
          hash = "zzz999",
          qsoCount = 90
        )
      )
    )
    assert(
      styles(
        (
          NodeIdentity(
            hostIp = "10.0.0.1",
            port = 8090,
            hostName = "alpha",
            instanceId = "alpha-id"
          ),
          NodeDataField.QsoCount
        )
      )
      != "contestConfigMajority"
    )
    assert(
      styles(
        (
          NodeIdentity(
            hostIp = "10.0.0.1",
            port = 8090,
            hostName = "alpha",
            instanceId = "alpha-id"
          ),
          NodeDataField.Hash
        )
      )
      != "contestConfigMajority"
    )
  test("majority qso count and hash values are styled as majority"):
    val styles = SwarmData.contestConfigFieldStyles(
      statuses = Seq(
        nodeStatus(
          hostName = "alpha",
          contestConfig = contestConfig(
            contestType = ContestType.WFD,
            callsign = "W9AAA",
            transmitters = 1,
            stationClass = "A",
            section = "IL"
          ),
          hash = "abc123",
          qsoCount = 100
        ),
        nodeStatus(
          hostName = "beta",
          contestConfig = contestConfig(
            contestType = ContestType.WFD,
            callsign = "W9AAA",
            transmitters = 1,
            stationClass = "A",
            section = "IL"
          ),
          hash = "abc123",
          qsoCount = 100
        ),
        nodeStatus(
          hostName = "gamma",
          contestConfig = contestConfig(
            contestType = ContestType.WFD,
            callsign = "W9AAA",
            transmitters = 1,
            stationClass = "A",
            section = "IL"
          ),
          hash = "zzz999",
          qsoCount = 90
        )
      )
    )
    assertEquals(
      obtained = styles(
        (
          NodeIdentity(
            hostIp = "10.0.0.1",
            port = 8090,
            hostName = "alpha",
            instanceId = "alpha-id"
          ),
          NodeDataField.QsoCount
        )
      ),
      expected = "contestConfigMajority"
    )
    assertEquals(
      obtained = styles(
        (
          NodeIdentity(
            hostIp = "10.0.0.1",
            port = 8090,
            hostName = "alpha",
            instanceId = "alpha-id"
          ),
          NodeDataField.Hash
        )
      ),
      expected = "contestConfigMajority"
    )

  test("local status is detected by instance id even when received through UDP"):
    val localIdentity = NodeIdentity(
      hostIp = "192.168.1.10",
      port = 8090,
      hostName = "local",
      instanceId = "same-instance"
    )
    val udpIdentity = localIdentity.copy(
      hostIp = "10.0.0.44",
      hostName = "udp-source"
    )
    val remoteLookingStatus = nodeStatus(
      hostName = "udp-source",
      contestConfig = contestConfig(
        contestType = ContestType.WFD,
        callsign = "W9AAA",
        transmitters = 1,
        stationClass = "A",
        section = "IL"
      )
    ).copy(
      nodeIdentity = udpIdentity,
      isLocal = false
    )

    val normalized = SwarmData.normalizeLocalNodeStatus(
      nodeStatus = remoteLookingStatus,
      localNodeIdentity = localIdentity
    )

    assert(normalized.isLocal)
    assertEquals(normalized.nodeIdentity, localIdentity)
    assertEquals(normalized.nodeIdentity.hostIp, localIdentity.hostIp)
    assertEquals(normalized.nodeIdentity.hostName, localIdentity.hostName)

  private def contestConfig(
    contestType: ContestType,
    callsign: String,
    transmitters: Int,
    stationClass: String,
    section: String
  ): ContestConfig =
    ContestConfig(
      contestType = contestType,
      ourCallsign = Callsign(
        callsign
      ),
      transmitters = transmitters,
      ourClass = stationClass,
      ourSection = section
    )

  private def nodeStatus(
    hostName: String,
    contestConfig: ContestConfig,
    hash: String = "",
    qsoCount: Int = 0
  ): NodeStatus =
    NodeStatus(
      statusMessage = StatusMessage(
        storeStats = StoreStats(hash = hash, qsoCount = qsoCount),
        bandNodeOperator = BandModeOperator(
          operator = Callsign("N0CALL"),
          bandMode = BandMode("20M SSB")
        ),
        contestConfig = contestConfig,
        contestStart = Instant.parse("2026-01-01T00:00:00Z"),
        metrics = Seq.empty
      ),
      nodeIdentity = NodeIdentity(
        hostIp = "10.0.0.1",
        port = 8090,
        hostName = hostName,
        instanceId = s"$hostName-id"
      ),
      isLocal = false
    )
