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

import fdswarm.bands.BandModeBuilder
import fdswarm.fx.contest.ContestType.WFD
import fdswarm.fx.station.StationConfig
import fdswarm.model.*
import fdswarm.util.{CallsignGenerator, NodeIdentityManager}
import jakarta.inject.*

import java.time.Instant

@Singleton
final class BigQsosGenerator @Inject()(qsoStore: QsoStore, bandModeBuilder: BandModeBuilder, nodeIdentityManager: NodeIdentityManager):

  /** Generate synthetic QSOs and *immediately* add them to QsoStore.
   *
   * IMPORTANT: Iterator#map is lazy; we must consume the iterator or nothing happens.
   */
  private val random = new scala.util.Random()
  private val operators = Seq("WA9NNN", "N9VTB", "W9SWW", "AA9KK", "W9POL", "KD9BYW", "WB9HWE")
  private val wfdClasses = Seq('H', 'I', 'O', 'M')

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
    val intervalMillis = (numberOfHours.toLong * 3600L * 1000L) / howMany
    val generatedCallsigns = CallsignGenerator.callsignIterator(prefix)
      .take(howMany)
      .zipWithIndex
    val batchOfQsos: Seq[Qso] = (for
      (callsign, index) <- generatedCallsigns
    yield
      val stamp = now.minusMillis(index * intervalMillis)
      val contestBands = Band.values.filterNot(_ == Band.OutOfBand)
      val randomBand = contestBands(random.nextInt(contestBands.length))
      val randomMode = Mode.values(random.nextInt(Mode.values.length))
      val bandMode = bandModeBuilder(randomBand, randomMode)

      val randomOperator = operators(random.nextInt(operators.size))
      val randomClassLetter = wfdClasses(random.nextInt(wfdClasses.size))
      val randomTransmitters = random.nextInt(20) + 1

      val qsoMetadata = QsoMetadata(
        station = StationConfig(operator = Callsign(randomOperator)),
        contest = WFD,
        node = NodeIdentityManager.nodeIdentity)
      val exchange = Exchange(FdClass(randomTransmitters, randomClassLetter))
      Qso(callsign = Callsign(callsign),
        exchange = exchange,
        bandMode = bandMode,
        qsoMetadata = qsoMetadata,
        stamp = stamp)
      ).toSeq
    qsoStore.add(batchOfQsos)
