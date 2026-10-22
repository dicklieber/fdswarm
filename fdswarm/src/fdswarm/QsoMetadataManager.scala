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
 */

package fdswarm

import fdswarm.fx.contest.ContestConfigManager
import fdswarm.model.QsoMetadata
import fdswarm.util.NodeIdentityManager
import jakarta.inject.{Inject, Singleton}
import scalafx.beans.property.ObjectProperty

@Singleton
class QsoMetadataManager @Inject() (
    stationConfigManager: StationConfigManager,
    nodeIdentityManager: NodeIdentityManager,
    contestConfigManager: ContestConfigManager
):

  private def build(): Option[QsoMetadata] = contestConfigManager.contestConfigProperty.value.map { config =>
    QsoMetadata(
      station = stationConfigManager.stationConfig,
      node    = NodeIdentityManager.nodeIdentity,
      contest = config.contestType.name
    )
  }

  def current: QsoMetadata =
    qsoMetadata.value.getOrElse(throw new IllegalStateException("No contest configured"))

  val qsoMetadata: ObjectProperty[Option[QsoMetadata]] = ObjectProperty(build())

  stationConfigManager.stationProperty.onChange { (_, _, _) =>
    qsoMetadata.value = build()
  }

  contestConfigManager.contestConfigProperty.onChange { (_, _, _) =>
    qsoMetadata.value = build()
  }
