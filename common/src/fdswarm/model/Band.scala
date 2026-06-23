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

import io.circe.{Decoder, Encoder}
import sttp.tapir.Schema

enum BandClass:
  case LF, VLF, MF, HF, VHF, UHF, SHF, EHF

enum BandRegion:
  case ALL, R1, R2, R3

enum Band(val name: String, val startFrequencyHz: Long, val endFrequencyHz: Long, val bandClass: BandClass, val region: BandRegion):
  case Band_160m extends Band("160m", 1800000L, 2000000L, BandClass.MF, BandRegion.ALL)
  case Band_80m extends Band("80m", 3500000L, 4000000L, BandClass.HF, BandRegion.ALL)
  case Band_60m extends Band("60m", 5250000L, 5450000L, BandClass.HF, BandRegion.ALL)
  case Band_40m extends Band("40m", 7000000L, 7300000L, BandClass.HF, BandRegion.ALL)
  case Band_30m extends Band("30m", 10100000L, 10150000L, BandClass.HF, BandRegion.ALL)
  case Band_20m extends Band("20m", 14000000L, 14350000L, BandClass.HF, BandRegion.ALL)
  case Band_15m extends Band("15m", 21000000L, 21450000L, BandClass.HF, BandRegion.ALL)
  case Band_10m extends Band("10m", 28000000L, 29700000L, BandClass.HF, BandRegion.ALL)
  case Band_6m extends Band("6m", 50000000L, 54000000L, BandClass.VHF, BandRegion.ALL)
  case Band_4m extends Band("4m", 70000000L, 70500000L, BandClass.VHF, BandRegion.R1)
  case Band_2m extends Band("2m", 144000000L, 148000000L, BandClass.VHF, BandRegion.ALL)
  case Band_1_25m extends Band("1.25m", 219000000L, 225000000L, BandClass.VHF, BandRegion.R2)
  case Band_70cm extends Band("70cm", 420000000L, 450000000L, BandClass.UHF, BandRegion.ALL)
  case Band_33cm extends Band("33cm", 902000000L, 928000000L, BandClass.UHF, BandRegion.R2)
  case Band_23cm extends Band("23cm", 1240000000L, 1300000000L, BandClass.UHF, BandRegion.ALL)
  case Band_13cm extends Band("13cm", 2300000000L, 2450000000L, BandClass.SHF, BandRegion.ALL)
  case Band_9cm extends Band("9cm", 3300000000L, 3500000000L, BandClass.SHF, BandRegion.ALL)
  case Band_6cm extends Band("6cm", 5650000000L, 5925000000L, BandClass.SHF, BandRegion.ALL)
  case Band_3cm extends Band("3cm", 10000000000L, 10500000000L, BandClass.SHF, BandRegion.ALL)

  override def toString: String = name

object Band:
  private val byName: Map[String, Band] =
    Band.values.map(b => b.name.toLowerCase -> b).toMap

  def fromString(band: String): Band =
    val normalized = band.trim.toLowerCase
    byName.getOrElse(normalized, throw new IllegalArgumentException(s"Unknown band: '$band'"))

  def fromStringOption(band: String): Option[Band] =
    byName.get(band.trim.toLowerCase)

  def fromFrequency(hz: Long): Option[Band] =
    val maybeBand = Band.values.find(b => hz >= b.startFrequencyHz && hz <= b.endFrequencyHz)

    maybeBand

  given Schema[Band] = Schema.string
  given Encoder[Band] = Encoder.encodeString.contramap(_.name)
  given Decoder[Band] = Decoder.decodeString.emap(value =>
    scala.util.Try(Band.fromString(value)).toEither.left.map(_.getMessage)
  )

object BandClass:
  given Encoder[BandClass] = Encoder.encodeString.contramap(_.toString)
  given Decoder[BandClass] = Decoder.decodeString.emap(s =>
    scala.util.Try(BandClass.valueOf(s)).toEither.left.map(_ => s"Invalid BandClass: $s")
  )

object BandRegion:
  given Encoder[BandRegion] = Encoder.encodeString.contramap(_.toString)
  given Decoder[BandRegion] = Decoder.decodeString.emap(s =>
    scala.util.Try(BandRegion.valueOf(s)).toEither.left.map(_ => s"Invalid BandRegion: $s")
  )
