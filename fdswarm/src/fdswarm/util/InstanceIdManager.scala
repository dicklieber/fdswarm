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

import fdswarm.StartupInfo
import fdswarm.io.FileHelper
import fdswarm.logging.LazyStructuredLogging
import fdswarm.util.Ids.Id
import io.circe.*
import io.circe.generic.auto.deriveDecoder
import jakarta.inject.{Inject, Singleton}

@Singleton
class InstanceIdManager @Inject()(fileHelper: FileHelper,
                                  startupInfo: StartupInfo) extends LazyStructuredLogging:
  private val file =  "instance.json"

  var ourInstanceId: Id = startupInfo.info match
    case Some(config) =>
      logger.info(s"Using instance ID from StartupInfo: ${config.id}")
      config.id
    case None =>
      loadOrCreate()

  private def loadOrCreate(): Id = {
    val instanceConfig = InstanceConfig(Ids.generateInstanceId())
    val loadedConfig = fileHelper.loadOrDefault(file)(instanceConfig)
    fileHelper.save(file, loadedConfig)
    loadedConfig.instanceId
  }



case class InstanceConfig(instanceId: Id) derives Codec.AsObject
