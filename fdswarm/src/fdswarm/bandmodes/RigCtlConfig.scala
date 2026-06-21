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

package fdswarm.bandmodes

import fdswarm.fx.utils.editor.FieldHelp
import io.circe.Codec

/** Persistent configuration for the rigctld integration.
  *
  * @param enabled        whether rigctld polling is active at startup
  * @param rigModel       Hamlib rig model number (see `rigctl -l`); 1 = Dummy
  * @param rigDevice      rig device path passed to rigctld via `-r` (e.g. `/dev/ttyUSB0`); empty = omit flag
  * @param host           hostname where rigctld is running
  * @param port           TCP port rigctld listens on (default 4532)
  * @param pollIntervalMs how often to query the rig for band/mode changes (ms)
  * @param rigctldPath    filesystem path to the rigctld binary
  */
case class RigCtlConfig(
  @FieldHelp(value = "Enable rigctld polling at startup",
             url   = "rigctl/enabled")
  enabled: Boolean = false,
  @FieldHelp(value = "Filesystem path to the rigctld binary (e.g. /usr/bin/rigctld)",
             url   = "rigctl/rigctldPath")
  rigctldPath: String = "rigctld",
  @FieldHelp(value = "Hamlib rig model number — see rigctl -l; 1 = Dummy",
             url   = "rigctl/rigModel")
  rigModel: Int = 1,
  @FieldHelp(value = "How rigctld connects to the rig. Depends on the rig model and operating system.",
             url   = "rigctl/rigDevice")
  rigDevice: String = "",
  @FieldHelp(value = "Hostname where rigctld is running",
             url   = "rigctl/host")
  host: String = "localhost",
  @FieldHelp(value = "TCP port rigctld listens on (default 4532)",
             url   = "rigctl/port")
  port: Int = 4532,
  @FieldHelp(value = "How often to poll the rig for band/mode changes (milliseconds)",
             url   = "rigctl/pollIntervalMs")
  pollIntervalMs: Int = 1000
) derives Codec.AsObject

/** A single entry from `rigctl -l`, describing a supported Hamlib rig. */
case class RigInfo(modelNumber: Int, manufacturer: String, model: String, version: String, status: String):
  /** Display string used in ComboBox cells. */
  override def toString: String = f"$modelNumber%5d  $manufacturer%-22s  $model"

object RigInfo:
  /** Sort by manufacturer then model name (case-insensitive). */
  given Ordering[RigInfo] = Ordering.by(r => (r.manufacturer.toLowerCase, r.model.toLowerCase))
