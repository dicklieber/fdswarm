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

package fdswarm.fx.discovery

import com.google.inject.name.Named
import fdswarm.contestStart.{ContestStart, ContestStartManager}
import fdswarm.fx.contest.{ContestConfig, ContestConfigManager, ContestType}
import fdswarm.logging.LazyStructuredLogging
import fdswarm.replication.status.SwarmData
import fdswarm.replication.{Service, Transport}
import jakarta.inject.Inject
import javafx.application.Platform

import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.TimeUnit

/** ContestDiscovery is responsible for determining the contest configuration
  * within a distributed swarm environment. It attempts to identify and select a
  * valid contest configuration by querying all nodes in the swarm and picking
  * the most recent configuration that is not set to NONE. If no such
  * configuration is found, it logs the absence of a valid contest
  * configuration.
  *
  * @constructor
  *   Creates a ContestDiscovery instance with the provided dependencies.
  * @param transport
  *   Facilitates communication within the swarm to exchange information related
  *   to contest discovery.
  * @param contestConfigManager
  *   Manages the configuration of the contest, allowing updates to the active
  *   contest configuration.
  * @param swarmData
  *   Stores node-level data required for contest discovery, including status
  *   information for each node.
  * @param timeoutSpecifies
  *   the discovery timeout duration in milliseconds, defining the period after
  *   which discovery will terminate.
  */
class ContestDiscovery @Inject() (
    val transport: Transport,
    contestConfigManager: ContestConfigManager,
    contestStartManager: ContestStartManager,
    swarmData: SwarmData,
    @Named("fdswarm.contestDiscoveryTimeout") val timeout: Duration)
    extends LazyStructuredLogging:

  private val predeleteContestConfig: Boolean =
    sys.env
      .get(
        "PREDELETE_CONTEST_CONFIG"
      )
      .exists(
        _.trim.equalsIgnoreCase(
          "true"
        )
      )

  def start(): Unit =
    if predeleteContestConfig then
      logger.info(
        "PREDELETE_CONTEST_CONFIG=true, removing local contest config before discovery"
      )
      contestConfigManager.clearContestConfig()

    val currentConfig = contestConfigManager.contestConfigProperty.value
    val currentStart = contestStartManager.contestStart.value
    if currentConfig.contestType != ContestType.NONE && currentStart.isStarted then
      logger.debug(
        s"Skipping contest discovery because contest config is already set to ${currentConfig.contestType} and contest is started"
      )
      return

    logger.info("Starting contest discovery", "Timeout" -> timeout)

    swarmData.clear()
    transport.send(
      Service.SendStatus,
      "{}".getBytes(
        StandardCharsets.UTF_8
      )
    )
    TimeUnit.MILLISECONDS.sleep(
      timeout.toMillis
    )
    val allNodeStatuses = swarmData.allNodeStatuses
    logger.debug(s"Discovered ${allNodeStatuses.size} node statuses")
    logger.whenTraceEnabled {
      allNodeStatuses.foreach(nodeStatus =>
        logger.trace(
          s"Node status: ${nodeStatus.nodeIdentity} ContestType: ${nodeStatus.statusMessage.contestConfig.contestType}"
        )
      )
    }

    val selectedStatus = allNodeStatuses
      .filter(nodeStatus =>
        nodeStatus.statusMessage.contestConfig.contestType != ContestType.NONE
      )
      .headOption
    selectedStatus.foreach(nodeStatus =>
      logger.debug(s"Selected status: $nodeStatus")
      val selectedConfig = nodeStatus.statusMessage.contestConfig
      val selectedStart = ContestStart(start = nodeStatus.statusMessage.contestStart)
      updateContestFromStatus(selectedConfig = selectedConfig, selectedStart = selectedStart)
      logger.info(
        s"Contest discovery selected config ${selectedConfig.contestType} and start ${selectedStart.start} from ${nodeStatus.nodeIdentity}"
      )
    )
    if selectedStatus.isEmpty then
      logger.info(
        "Contest discovery did not find a non-NONE contest config in swarm data."
      )

  private def updateContestFromStatus(selectedConfig: ContestConfig, selectedStart: ContestStart): Unit =
    def update(): Unit =
      contestConfigManager.setConfig(selectedConfig)
      contestStartManager.update(nextContestStart = selectedStart)
    if Platform.isFxApplicationThread then update()
    else Platform.runLater(() => update())
