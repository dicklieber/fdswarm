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
import io.circe.Codec

import java.time.*

/** The name is used in persisted configuration and replication messages. */
case class ContestType(
    name: String,
    displayName: String,
    compute: ContestDateRule,
    cabrilloContestName: String):
  def dates(zdt: ZonedDateTime = ZonedDateTime.now()): ContestDates = compute(zdt.getYear)
  override def toString: String = name

/** A value-based date rule keeps independently loaded contest types equal. */
case class ContestDateRule(rule: com.typesafe.config.Config) extends (Int => ContestDates):
  override def apply(year: Int): ContestDates =
    ContestDateCalculator.fromRule(year, rule)

object ContestType:
  given sttp.tapir.Schema[ContestType] = sttp.tapir.Schema.schemaForString.map(
    name => ContestTypes.configured.get(name)
  )(_.name)

  given Codec[ContestType] = ContestTypes.configured.codec

case class ContestTimes(start: ZonedDateTime, end: ZonedDateTime)
