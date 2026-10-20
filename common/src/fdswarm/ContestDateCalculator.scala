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

package fdswarm

import com.typesafe.config.{Config, ConfigFactory}

import java.time.*
import scala.jdk.CollectionConverters.*

final case class ContestSetup(earliestStartUtc: ZonedDateTime, maxCumulativeDuration: Duration)

/** End times identify the final operational minute, inclusive. */
final case class ContestDates(
    startUtc: ZonedDateTime,
    endUtc: ZonedDateTime,
    setup: Option[ContestSetup] = None
)

object ContestDateCalculator:
  val Epoch: ContestDates =
    ContestDates(
      startUtc = Instant.EPOCH.atZone(ZoneOffset.UTC),
      endUtc = Instant.EPOCH.atZone(ZoneOffset.UTC)
    )

  def none(year: Int): ContestDates = Epoch

  def wfd(year: Int): ContestDates = configuredDates(year, "WFD", ConfigFactory.load())

  def arrl(year: Int): ContestDates = configuredDates(year, "ARRL", ConfigFactory.load())

  // Retain the existing entry points for callers; the configured rules determine the dates.
  def lastFull(year: Int): ContestDates = wfd(year)

  def forthFullWeekend(year: Int): ContestDates = arrl(year)

  private[fdswarm] def configuredDates(year: Int, contest: String, config: Config): ContestDates =
    val rule = config.getConfigList("fdswarm.contests").asScala
      .find(_.getString("name") == contest)
      .getOrElse(throw new IllegalArgumentException(s"Unknown contest: $contest"))
      .getConfig("dates")
    fromRule(year, rule)

  private[fdswarm] def fromRule(year: Int, rule: Config): ContestDates =
    val (saturday, sunday) = nthFullWeekend(
      year,
      Month.of(rule.getInt("month")),
      rule.getInt("fullWeekend")
    )
    val setup = Option.when(rule.hasPath("setup")) {
      val setupRule = rule.getConfig("setup")
      ContestSetup(
        earliestStartUtc = saturday.plusDays(setupRule.getInt("daysFromSaturday").toLong)
          .atTime(LocalTime.parse(setupRule.getString("startUtc"))).atZone(ZoneOffset.UTC),
        maxCumulativeDuration = setupRule.getDuration("maxCumulativeDuration")
      )
    }
    ContestDates(
      startUtc = saturday.atTime(LocalTime.parse(rule.getString("startUtc"))).atZone(ZoneOffset.UTC),
      endUtc = sunday.atTime(LocalTime.parse(rule.getString("endUtc"))).atZone(ZoneOffset.UTC),
      setup = setup
    )

  // ---- Helpers --------------------------------------------------------------

  /** Nth full weekend (Sat/Sun both in the same month), n is 1-based. */
  def nthFullWeekend(year: Int, month: Month, n: Int): (LocalDate, LocalDate) =
    require(n >= 1, s"n must be >= 1, got $n")

    val (firstSat, firstSun) = firstFullWeekend(year, month)
    (firstSat.plusWeeks((n - 1).toLong), firstSun.plusWeeks((n - 1).toLong))

  /** First full weekend (Sat/Sun both in the same month). */
  private def firstFullWeekend(year: Int, month: Month): (LocalDate, LocalDate) =
    val firstDay = LocalDate.of(year, month, 1)

    val daysToSat =
      (DayOfWeek.SATURDAY.getValue - firstDay.getDayOfWeek.getValue + 7) % 7

    val sat = firstDay.plusDays(daysToSat.toLong)
    val sun = sat.plusDays(1)

    (sat, sun)

  /** Last full weekend (Sat/Sun both in the same month). */
  def lastFullWeekend(year: Int, month: Month): (LocalDate, LocalDate) =
    val lastDay = LocalDate.of(year, month, month.length(Year.isLeap(year.toLong)))

    val daysBackToSat =
      (lastDay.getDayOfWeek.getValue - DayOfWeek.SATURDAY.getValue + 7) % 7

    var sat = lastDay.minusDays(daysBackToSat.toLong)
    var sun = sat.plusDays(1)

    // If Sunday spills into the next month, go back a week
    if sun.getMonth != month then
      sat = sat.minusWeeks(1)
      sun = sat.plusDays(1)

    (sat, sun)
