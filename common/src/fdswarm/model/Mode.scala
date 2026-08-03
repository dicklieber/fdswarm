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

enum Mode:
  case CW, PH, DIGI

  override def toString: String = productPrefix

object Mode:

  /** Maps only the canonical serialised values (CW / PH / DIGI) back to the enum.
    * Raw radio / WSJT-X mode strings (e.g. "FT8", "CWR") must be routed through
    * `fdswarm.rigctl.ModeMappingManager#fromString` in the fdswarm module.
    */
  def fromString(mode: String): Mode =
    mode.trim.toUpperCase match
      case "CW"   => Mode.CW
      case "PH"   => Mode.PH
      case "DIGI" => Mode.DIGI
      case _      => Mode.PH

  given Schema[Mode] = Schema.string
  given Encoder[Mode] = Encoder.encodeString.contramap(_.toString)
  given Decoder[Mode] = Decoder.decodeString.emap(value =>
    scala.util.Try(Mode.fromString(value)).toEither.left.map(_.getMessage)
  )
