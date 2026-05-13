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

package fdswarm

import _root_.io.circe.Codec
import fdswarm.model.{BandMode, Callsign}
import fdswarm.util.Ids
import fdswarm.util.Ids.Id

/**
 *
 * @param enable true to enable starting this instance.
 * @param operator who is the operator?
 * @param clearQsos delete qso journal file before loading the QSO store.
 * @param debugMode start with java debugger attached?
 * @param id of this instance. Also used to be the instanceId in the started instance.
 */
case class StartupConfig(enable: Boolean = true,
                         operator: Callsign,
                         bandMode: BandMode,
                         clearQsos: Boolean = false,
                         skipInitDiscover: Boolean = false,
                         debugMode: DebugMode = DebugMode.Off,
                         id: Id = Ids.generateInstanceId()) derives Codec.AsObject {
  override def toString: String =
    s"DebugConfig(operator=$operator, bandMode=$bandMode, clearQsos=$clearQsos, skipInitDiscover=$skipInitDiscover, id=$id)"
}
