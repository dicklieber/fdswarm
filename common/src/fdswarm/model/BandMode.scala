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

import scala.language.strictEquality

import io.circe.{Decoder, Encoder}
import sttp.tapir.Schema

/**
 * Allows storing band and mode in a compact why in a [[Qso]]
 */
case class BandMode private[fdswarm] (band: Band, mode: Mode) derives CanEqual:
  def cabMode: Mode =
    mode match
      case Mode.CW => Mode.CW
      case Mode.PH => Mode.PH
      case Mode.DIGI => Mode.DIGI

  override def toString: String =
    s"${band.name} $mode"


object BandMode:
  /**
   * Use when we don't have an explicit frequency
   */
  val bandFreqMap: Map[Band, String] = {
    Seq(
      Band.Band_160m -> "1810",
      Band.Band_80m -> "3530",
      Band.Band_40m -> "7030",
      Band.Band_20m -> "14035",
      Band.Band_15m -> "21030",
      Band.Band_10m -> "28030",
      Band.Band_6m -> "28030",
      Band.Band_2m -> "144000",
      Band.Band_1_25m -> "224000",
      Band.Band_70cm -> "442000",
    ).toMap
  }
  private val Parse = """\s*([\d.]+[a-zA-Z]+)\s+([a-zA-Z]{1,5})\s*""".r

  def apply(band: String, mode: String): BandMode =
    new BandMode(Band.fromString(band), Mode.fromString(mode))

  def apply(s: String): BandMode =
    s match
      case Parse(band, mode) =>
        new BandMode(Band.fromString(band), Mode.fromString(mode))
      case _ => throw new IllegalArgumentException(s"Can't parse $s")

  def bandToFreq(band: Band): String =
    bandFreqMap.getOrElse(band, "")

  def bandToFreq(band: String): String =
    Band.fromStringOption(band).flatMap(bandFreqMap.get).getOrElse("")

  given Schema[BandMode] = Schema.string
  given Encoder[BandMode] = Encoder.encodeString.contramap(_.toString)
  given Decoder[BandMode] = Decoder.decodeString.map(BandMode.apply)
