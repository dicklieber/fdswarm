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

import fdswarm.StationConfigManager
import fdswarm.bandmodes.SelectedBandModeManager
import fdswarm.contestStart.ContestStartManager
import fdswarm.fx.contest.{ContestConfigManager, ContestConfigVersion}
import fdswarm.metric.{MetricSnapshotFactory, MetricStat}
import fdswarm.model.BandModeOperator
import fdswarm.replication.status.SwarmData
import fdswarm.util.NodeIdentityManager
import io.dropwizard.metrics5.{Metric, MetricRegistry, SharedMetricRegistries}
import jakarta.inject.{Inject, Singleton}
import javafx.beans.property.{ReadOnlyObjectProperty, ReadOnlyObjectWrapper}

import scala.jdk.CollectionConverters.*

/**
 * This class is responsible for building the [[StatusMessage]], sent to other nodes.
 * And [[NodeStatus]] that is [[fdswarm.replication.status.SwarmData]].
 */
@Singleton
final class LocalNodeStatus @Inject()(
                                       stationManager: StationConfigManager,
                                       selectedBandModeStore: SelectedBandModeManager,
                                       contestConfigManager: ContestConfigManager,
                                       contestStartManager: ContestStartManager,
                                       swarmData: SwarmData
                                     ) :

  private val metricRegistry: MetricRegistry = SharedMetricRegistries.getOrCreate(
    "default"
  )
  @volatile private var lastStoreStats: StoreStats = StoreStats()

  def statusMessage: StatusMessage =
    val contestConfig = contestConfigManager.snapshot
    val bandNodeOperator =
      BandModeOperator(
        stationManager.stationConfig.operator,
        selectedBandModeStore.selected.value
      )
    StatusMessage(storeStats = lastStoreStats,
      bandNodeOperator = bandNodeOperator,
      contestConfig = contestConfig.map(ContestConfigVersion.apply),
      contestStart = contestStartManager.contestStart.value.start,
      metrics = metricSnapshots)

  private def metricSnapshots: Seq[MetricStat] =
    metricRegistry.getMetrics.asScala.toSeq
      .sortBy(
        _._1.getKey
      )
      .flatMap {
        case (metricName, metric: Metric) =>
          MetricSnapshotFactory.fromMetric(
            metric = metric,
            metricName = metricName.getKey
          )
      }

  private val currentBuffer: ReadOnlyObjectWrapper[NodeStatus] = new ReadOnlyObjectWrapper[NodeStatus](null)
  val current: ReadOnlyObjectProperty[NodeStatus] = currentBuffer.getReadOnlyProperty

  stationManager.stationProperty.onChange { (_, _, _) =>
    rebuildAndNotify()
  }
  selectedBandModeStore.selected.onChange { (_, _, _) =>
    rebuildAndNotify()
  }
  contestConfigManager.contestConfigProperty.onChange { (_, _, _) =>
    rebuildAndNotify()
  }
  contestStartManager.contestStart.onChange { (_, _, _) =>
    rebuildAndNotify()
  }

  rebuildAndNotify()

  def updateStoreStats(storeStats: StoreStats): Unit =
    lastStoreStats = storeStats
    rebuildAndNotify()

  private def rebuildAndNotify(): Unit =
    val next =
      NodeStatus(
        statusMessage = statusMessage,
        nodeIdentity = NodeIdentityManager.nodeIdentity,
        isLocal = true,
        contestConfig = contestConfigManager.contestConfigProperty.value
      )
    swarmData.update(next)
