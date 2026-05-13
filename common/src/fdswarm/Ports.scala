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

import fdswarm.util.NodeIdentity

object Ports:
  private var _ports: Set[String] = Set.empty

  def port(nodeIdentity: NodeIdentity): Unit = synchronized {
    _ports += nodeIdentity.port.toString
  }

  def ports(): Seq[String] = synchronized {
    _ports.toSeq.sorted
  }

  def hasMultiplePorts: Boolean = synchronized {
    _ports.size > 1
  }
