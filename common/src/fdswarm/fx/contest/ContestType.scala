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

package fdswarm.fx.contest

import fdswarm.{ContestDateCalculator, ContestDates}
import io.circe.{Codec, Decoder, Encoder}

import java.time.*

enum ContestType(
                  val name: String,
                  val compute: Int => ContestDates,
                  val cabrilloContestName: String,
                  val allowChosing: Boolean = true)
    derives sttp.tapir.Schema:
  /** @param zdt
    *   lets plugin in a differnet date, for testing
    * @return
    */
  def dates(
      zdt: ZonedDateTime = ZonedDateTime.now()
    ): ContestDates = compute(zdt.getYear)
  case NONE extends ContestType("None", ContestDateCalculator.none, "", false)

  case WFD
      extends ContestType(
        "Winter Field Day",
        ContestDateCalculator.lastFull,
        "WFD"
      )
  case ARRL
      extends ContestType(
        "ARRL Field Day",
        ContestDateCalculator.forthFullWeekend,
        "ARRL-FD"
      )

object ContestType:
  given Codec[ContestType] = Codec.from(
    Decoder.decodeString.emap(s =>
      try Right(ContestType.valueOf(s))
      catch case _: IllegalArgumentException => Left(s"Invalid ContestType: $s")
    ),
    Encoder.encodeString.contramap(_.toString)
  )

case class ContestTimes(
    start: ZonedDateTime,
    end: ZonedDateTime)
