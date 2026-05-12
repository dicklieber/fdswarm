
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

package fdswarm.model

import com.organization.BuildInfo
import fdswarm.fx.contest.ContestType
import fdswarm.fx.station.StationConfig
import fdswarm.util.NodeIdentity
import io.circe.Codec

/**
 * Stuff about a QSO. i.e. not entered as a part of a QSO itself
 *
 * @param station can be edited by the user.
 * @param node    what node, in the cluster this came from.
 *                // * @param contestId so old data can't by accident be mixed with current.
 * @param v       FdSwarm Version that built this so we can detect mismatched versions.
 */

case class QsoMetadata(station: StationConfig,
                       node: NodeIdentity,
                       contest: ContestType,
                       v: String = BuildInfo.version) derives Codec.AsObject, sttp.tapir.Schema

object QsoMetadata:
  val testQsoMetadata: QsoMetadata = QsoMetadata(station = StationConfig(),
    node = NodeIdentity(hostIp = "44.0.0.1", port = 8888, hostName = "testHost", instanceId = "qO-"),
    contest = ContestType.WFD)