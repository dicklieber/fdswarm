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
import com.typesafe.config.{ConfigFactory, ConfigRenderOptions}
import io.circe.{Codec, Decoder, Encoder}
import scala.util.Try

import java.time.*

/** A complete, editable contest definition, embedded in configuration and messages. */
case class ContestType(
    name: String,
    displayName: String,
    compute: ContestDateRule,
    cabrilloContestName: String) derives Codec.AsObject, sttp.tapir.Schema:
  require(name.trim.nonEmpty, "Contest name must not be empty")
  require(displayName.trim.nonEmpty, "Contest display name must not be empty")
  require(cabrilloContestName.trim.nonEmpty, "Cabrillo contest name must not be empty")
  def dates(zdt: ZonedDateTime = ZonedDateTime.now()): ContestDates = compute(zdt.getYear)
  override def toString: String = name

/** A value-based date rule keeps independently loaded contest types equal. */
case class ContestDateRule(rule: com.typesafe.config.Config) extends (Int => ContestDates):
  require((1 to 5).contains(rule.getInt("fullWeekend")), "Full weekend must be between 1 and 5")
  ContestDateCalculator.fromRule(2027, rule)

  override def apply(year: Int): ContestDates =
    ContestDateCalculator.fromRule(year, rule)

object ContestDateRule:
  given Codec[ContestDateRule] = Codec.from(
    Decoder.decodeJson.emap { json =>
      if !json.isObject then Left("Contest date rule must be an object")
      else Try(ContestDateRule(ConfigFactory.parseString(json.noSpaces)))
        .toEither.left.map(_.getMessage)
    },
    Encoder.instance { value =>
      io.circe.parser.parse(value.rule.root().render(ConfigRenderOptions.concise()))
        .fold(error => throw new IllegalArgumentException(error.message), identity)
    }
  )
  given sttp.tapir.Schema[ContestDateRule] = sttp.tapir.Schema.anyObject

case class ContestTimes(start: ZonedDateTime, end: ZonedDateTime)
