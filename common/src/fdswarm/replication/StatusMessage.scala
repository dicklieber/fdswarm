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

package fdswarm.replication

import fdswarm.fx.contest.ContestConfig
import fdswarm.metric.MetricStat
import fdswarm.model.BandModeOperator
import fdswarm.util.Gzip
import io.circe.Codec
import io.circe.generic.auto.{deriveDecoder, deriveEncoder}
import io.circe.parser.decode
import io.circe.syntax.EncoderOps
import sttp.tapir.Schema

import java.nio.charset.StandardCharsets
import java.time.Instant

/** Represents a status message containing information about the current state of a contest communication system.
  *
  * @param storeStats
  *   A SHA-512 hash of all the Qsos that have been logged and how many QSOs.
  * @param bandNodeOperator
  *   An instance of BandModeOperator containing information about the operator, frequency band, and timestamp.
  * @param contestConfig
  *   Configuration details of the contest, such as callsign, class, section,
  *   and other metadata.
  *   @param contestStart
  *   When we think the contest was started.
  */
case class StatusMessage(
    storeStats: StoreStats,
    bandNodeOperator: BandModeOperator,
    contestConfig: ContestConfig,
    contestStart: Instant,
    metrics: Seq[MetricStat]
)
    derives Codec.AsObject, Schema:
  def toPacket: Array[Byte] =
    val jsonBytes = this.asJson.noSpaces.getBytes(StandardCharsets.UTF_8)
    Gzip.compress(jsonBytes)

object StatusMessage:
  val MaxPacketJsonBytes: Int = 1024 * 1024

  def apply(gzipped: Array[Byte]): StatusMessage =
    val jsonBytes = Gzip.decompress(
      gzipped,
      MaxPacketJsonBytes
    )
    val json = new String(jsonBytes, StandardCharsets.UTF_8)
    decode[StatusMessage](json) match
      case Right(streamMessage) => streamMessage
      case Left(error) =>
        throw new RuntimeException(s"Failed to decode StatusMessage from JSON: ${error.getMessage}", error)

case class StoreStats(
    hash: String = "",
    qsoCount: Int = 0,
    ourQsoCount: Int = 0
) derives Codec.AsObject, sttp.tapir.Schema:
  def needsUpdate(other: StoreStats): Boolean =
    hash != other.hash || qsoCount != other.qsoCount
