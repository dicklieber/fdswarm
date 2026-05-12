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

import fdswarm.logging.LogFields
import fdswarm.util.Ids
import fdswarm.util.Ids.Id
import io.circe.Codec
import io.circe.generic.auto.deriveEncoder

import java.time.Instant

/** This is what's in the QsoStore, broadcast to other nodes and qsoJournal.log.
  *
  * @param callsign
  *   of the worked station.
  * @param exchange
  *   exchange (class and section).
  * @param bandMode
  *   that was used.
  * @param stamp
  *   when QSO occurred.
  * @param uuid
  *   id unique QSO id in time & space.
  * @param qsoMetadata
  *   info about your station.
  */
case class Qso(
    callsign: Callsign,
    exchange: Exchange,
    bandMode: BandMode,
    qsoMetadata: QsoMetadata,
    stamp: Instant = Instant.now(),
    uuid: Id = Ids.generateId())

    extends  LogFields derives Codec.AsObject, sttp.tapir.Schema:

  lazy val rejectedMsg: String = s"Rejected duplicate Qso: $callsign $bandMode"
  val logFields: Seq[(String, Any)] =
    Seq(
      "callsign" -> callsign.value,
      "transmitters" -> exchange.fdClass.transmitters,
      "class" -> exchange.fdClass.classLetter,
      "section" -> exchange.sectionCode,
      "band" -> bandMode.band.name,
      "mode" -> bandMode.mode.toString,
      "operator" -> qsoMetadata.station.operator.value,
      "rig" -> qsoMetadata.station.rig,
      "antenna" -> qsoMetadata.station.antenna,
      "node" -> qsoMetadata.node.toString,
      "stamp" -> stamp
    )
  /** Defines a criterion used to identify duplicate QSOs. Combines the
    * `callsign` of the worked station and the `bandMode` to create a unique key
    * for comparison.
    */
  val dupCriterion: String = s"$callsign-$bandMode"

  def asJsonCompact: String =
    import io.circe.syntax.*
    this.asJson.noSpaces


  def flatten: Map[String, String] =
    Map(
      "Time" -> stamp.toString,
      "Their Call" -> callsign.value,
      "Class" -> exchange.fdClass.toString,
      "Section" -> exchange.sectionCode,
      "Band" -> bandMode.band.name,
      "Mode" -> bandMode.mode.toString,
      "Operator" -> qsoMetadata.station.operator.value,
      "Rig" -> qsoMetadata.station.rig,
      "Antenna" -> qsoMetadata.station.antenna,
      "Node" -> qsoMetadata.node.toString,
      "Version" -> qsoMetadata.v
    )

object Qso:
  val csvHeader: String =
    Seq(
      "Time",
      "Their Call",
      "Class",
      "Section",
      "Band",
      "Mode",
      "Operator",
      "Rig",
      "Antenna",
      "Node",
      "Version"
    )
      .mkString(",")

  given Ordering[Qso] =
    Ordering.by(_.stamp)

  def apply(
      callSign: Callsign,
      exchange: Exchange,
      bandMode: BandMode
    )(using qsoMetadata: QsoMetadata
    ): Qso =

    Qso(
      callsign = callSign,
      exchange = exchange,
      bandMode = bandMode,
      qsoMetadata = qsoMetadata
    )
