/*
 * Copyright (c) 2026. Dick Lieber, WA9NNN
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later.
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

package fdswarm.store

import fdswarm.bands.{AvailableBandsManager, AvailableModesManager, BandModeBuilder}
import fdswarm.fx.contest.{ContestCatalog, ContestConfigManager}
import fdswarm.fx.station.StationConfig
import fdswarm.model.*
import fdswarm.util.{CallsignGenerator, NodeIdentityManager}
import jakarta.inject.*

import java.time.Instant

@Singleton
final class BigQsosGenerator @Inject()(qsoStore: QsoStore, bandModeBuilder: BandModeBuilder,
    nodeIdentityManager: NodeIdentityManager, contestManager: ContestConfigManager,
    contestCatalog: ContestCatalog, availableBands: AvailableBandsManager, availableModes: AvailableModesManager):

  /** Generate synthetic QSOs and *immediately* add them to QsoStore.
   *
   * IMPORTANT: Iterator#map is lazy; we must consume the iterator or nothing happens.
   */
  private val random = new scala.util.Random()
  private val operators = Seq("WA9NNN", "N9VTB", "W9SWW", "AA9KK", "W9POL", "KD9BYW", "WB9HWE")

  /**
   *
   * @param howMany       qsos to generate.
   * @param numberOfHours spread howMany qsos across this many hours.
   * @param prefix         for the generated callsigs.
   * @param now            allow unit test to set the starting time, so we get repeatable results.
   */
  def qsos(howMany: Int, numberOfHours: Int, prefix: String, now: Instant = java.time.Instant.now()): Unit =
    require(howMany > 0, "howMany must be greater than 0")
    require(numberOfHours >= 1 && numberOfHours <= 25, "numberOfHours must be between 1 and 25")
    val contest = contestManager.current.contestType
    val classChoices = contestCatalog.getContest(contest).toSeq.flatMap(_.classChoices).map(_.value)
    val contestBands = availableBands.bands.toVector.filterNot(_ == Band.OutOfBand)
    val modes = availableModes.modes.toVector
    require(classChoices.nonEmpty, s"No class choices for ${contest.name}")
    require(contestBands.nonEmpty, "Select at least one band before generating QSOs")
    require(modes.nonEmpty, "Select at least one mode before generating QSOs")
    val batchOfQsos: Seq[Qso] = (for
      intervalMillis = (numberOfHours.toLong * 3600L * 1000L) / howMany
      generatedCallsigns = CallsignGenerator.callsignIterator(prefix)
        .take(howMany)
        .zipWithIndex
      (callsign, index) <- generatedCallsigns
      stamp = now.minusMillis(index * intervalMillis)
      randomBand = contestBands(random.nextInt(contestBands.length))
      randomMode = modes(random.nextInt(modes.length))
      bandMode = bandModeBuilder(randomBand, randomMode)
      randomOperator = operators(random.nextInt(operators.size))
      randomClassLetter = classChoices(random.nextInt(classChoices.size))
      randomTransmitters = random.nextInt(20) + 1
      qsoMetadata = QsoMetadata(
        station = StationConfig(operator = Callsign(randomOperator)),
        contest = contest.name,
        node = nodeIdentityManager.current)
      exchange = Exchange(FdClass(randomTransmitters, randomClassLetter))
    yield Qso(callsign = Callsign(callsign),
        exchange = exchange,
        bandMode = bandMode,
        qsoMetadata = qsoMetadata,
        stamp = stamp)
      ).toSeq
    qsoStore.add(batchOfQsos)
