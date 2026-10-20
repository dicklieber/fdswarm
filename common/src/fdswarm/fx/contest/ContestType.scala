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

/** Stable ID is used in persisted configuration and replication messages. */
case class ContestType(
    id: String,
    name: String,
    compute: ContestDateRule,
    cabrilloContestName: String,
    allowChosing: Boolean = true):
  def dates(zdt: ZonedDateTime = ZonedDateTime.now()): ContestDates = compute(zdt.getYear)
  override def toString: String = id

/** A value-based date rule keeps independently loaded contest types equal. */
case class ContestDateRule(rule: Option[com.typesafe.config.Config]) extends (Int => ContestDates):
  override def apply(year: Int): ContestDates =
    rule.fold(ContestDateCalculator.Epoch)(ContestDateCalculator.fromRule(year, _))

object ContestType:
  val NONE: ContestType = ContestType("NONE", "None", ContestDateRule(None), "", false)

  // Compatibility for non-injected callers and the string wire format.
  private lazy val defaults = new ContestTypes(com.typesafe.config.ConfigFactory.load())
  lazy val WFD: ContestType = defaults("WFD")
  lazy val ARRL: ContestType = defaults("ARRL")

  given sttp.tapir.Schema[ContestType] = sttp.tapir.Schema.schemaForString.map(
    id => defaults.get(id)
  )(_.id)

  given Codec[ContestType] = Codec.from(
    Decoder.instance { cursor =>
      cursor.as[String] match
        case Right(id) => defaults.codec.decodeJson(io.circe.Json.fromString(id))
        case Left(_) =>
          for
            id <- cursor.get[String]("id")
            name <- cursor.get[String]("name")
            cabrillo <- cursor.get[String]("cabrilloContestName")
            choosing <- cursor.get[Boolean]("allowChosing")
            dates <- cursor.get[io.circe.Json]("dates")
            contest <- scala.util.Try {
              val rule = com.typesafe.config.ConfigFactory.parseString(dates.noSpaces)
              val result = ContestType(id, name, ContestDateRule(Some(rule)), cabrillo, choosing)
              result.compute(2027)
              result
            }.toEither.left.map(e => io.circe.DecodingFailure(e.getMessage, cursor.history))
          yield contest
    },
    Encoder.instance { contest =>
      // Keep existing string messages; include definitions for custom or overridden types
      // so receivers and saved files do not depend on the sender's local configuration.
      if defaults.get(contest.id).contains(contest) then io.circe.Json.fromString(contest.id)
      else
        val dates = contest.compute.rule.map { rule =>
          io.circe.parser.parse(rule.root().render(com.typesafe.config.ConfigRenderOptions.concise())).toTry.get
        }.getOrElse(io.circe.Json.Null)
        io.circe.Json.obj(
          "id" -> io.circe.Json.fromString(contest.id),
          "name" -> io.circe.Json.fromString(contest.name),
          "cabrilloContestName" -> io.circe.Json.fromString(contest.cabrilloContestName),
          "allowChosing" -> io.circe.Json.fromBoolean(contest.allowChosing),
          "dates" -> dates
        )
    }
  )

case class ContestTimes(start: ZonedDateTime, end: ZonedDateTime)
