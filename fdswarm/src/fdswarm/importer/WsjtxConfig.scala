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

package fdswarm.importer

import fdswarm.fx.utils.editor.FieldHelp
import io.circe.Codec

/** Persistent configuration for the WSJT-X multicast listener.
  *
  * @param enabled        whether to listen for WSJT-X UDP messages at startup
  * @param multicastGroup UDP multicast group address WSJT-X broadcasts to
  * @param port           UDP port WSJT-X broadcasts on
  */
case class WsjtxConfig(
  @FieldHelp(value = "Enable WSJT-X logged QSO (type 12 ADIF) UDP multicast listener")
  enabled: Boolean = false,
  @FieldHelp(value = "WSJT-X UDP multicast group address (default: 224.0.0.1)")
  multicastGroup: String = "224.0.0.1",
  @FieldHelp(value = "WSJT-X UDP port (default: 2237)")
  port: Int = 2237
) derives Codec.AsObject
