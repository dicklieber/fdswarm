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

import fdswarm.logging.LazyStructuredLogging
import fdswarm.util.NodeIdentityManager._ourNodeIdentity
import jakarta.inject.{Inject, Named, Singleton}

import java.net.{Inet4Address, NetworkInterface}
import scala.compiletime.uninitialized
import scala.jdk.CollectionConverters.*

/**
 * Exposes the identity of the local node.
 */
@Singleton
class NodeIdentityManager @Inject()(@Named("fdswarm.httpPort") httpPort: Int,
                                    instanceIdManager: InstanceIdManager) extends LazyStructuredLogging:

  def currentIp: String =LocalAddressPicker.bestIp
  def current: NodeIdentity = NodeIdentityManager.nodeIdentity
//  def currentIp: String = LocalAddressPicker.best()

  // Override port with PORT env var if present
  val port: Int = sys.env.get("PORT").map { sPort =>
    sPort.toInt
  }.getOrElse(httpPort)

  private val ourHostName: String = java.net.InetAddress.getLocalHost.getHostName.split('.').head

  _ourNodeIdentity = NodeIdentity(
    hostIp = currentIp,
    port = port,
    hostName = ourHostName,
    instanceId = instanceIdManager.ourInstanceId)

object NodeIdentityManager:
  var _ourNodeIdentity: NodeIdentity = uninitialized
  lazy val nodeIdentity: NodeIdentity = _ourNodeIdentity
  def isUs(nodeIdentity: NodeIdentity): Boolean =
    nodeIdentity == NodeIdentityManager.nodeIdentity

