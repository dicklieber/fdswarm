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

package fdswarm.replication.status

enum NodeDataField(val label: String, val colorDeffCells: Boolean = false):
  case HostIp extends NodeDataField("hostIp")
  case Port extends NodeDataField("port")
  case HostName extends NodeDataField("hostName")
  case InstanceId extends NodeDataField("instanceId")
  case Received extends NodeDataField("received")
  case IsLocal extends NodeDataField("isLocal")
  case QsoCount extends NodeDataField("qsoCount", colorDeffCells = true)
  case OurQsoCount extends NodeDataField("ourQsoCount")
  case Hash extends NodeDataField("hash", colorDeffCells = true)
  case Operator extends NodeDataField("operator")
  case Band extends NodeDataField("band")
  case Mode extends NodeDataField("mode")
  case BandMode extends NodeDataField("bandMode")
  case BandModeStamp extends NodeDataField("bandModeStamp")
  case ContestType extends NodeDataField("contestType", colorDeffCells = true)
  case ContestCallsign extends NodeDataField("contestCallsign", colorDeffCells = true)
  case ContestTransmitters extends NodeDataField("contestTransmitters", colorDeffCells = true)
  case ContestClass extends NodeDataField("contestClass", colorDeffCells = true)
  case ContestSection extends NodeDataField("contestSection", colorDeffCells = true)
  case Exchange extends NodeDataField("exchange", colorDeffCells = true)
  case ContestStart extends NodeDataField("contestStart", colorDeffCells = true)

object NodeDataField:
  val bandOpFields: Seq[NodeDataField] = Seq(
    NodeDataField.OurQsoCount,
    NodeDataField.HostName,
    NodeDataField.Operator,
    NodeDataField.BandMode
  )

  val staticFields: Seq[NodeDataField] = Seq(
    NodeDataField.HostIp,
    NodeDataField.Port,
    NodeDataField.HostName,
    NodeDataField.InstanceId,
    NodeDataField.Received,
    NodeDataField.IsLocal,
    NodeDataField.QsoCount,
    NodeDataField.OurQsoCount,
    NodeDataField.Hash,
    NodeDataField.Operator,
    NodeDataField.Band,
    NodeDataField.Mode,
    NodeDataField.BandMode,
    NodeDataField.BandModeStamp,
    NodeDataField.ContestType,
    NodeDataField.ContestCallsign,
    NodeDataField.ContestTransmitters,
    NodeDataField.ContestClass,
    NodeDataField.ContestSection,
    NodeDataField.Exchange,
    NodeDataField.ContestStart
  )
