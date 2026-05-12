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

package fdswarm.store

import fdswarm.fx.contest.ContestType
import fdswarm.fx.station.StationConfig
import fdswarm.model.{BandMode, Callsign, Exchange, FdClass, Qso, QsoMetadata}
import fdswarm.util.NodeIdentity
import munit.FunSuite

class QsoStoreTest extends FunSuite:

  test("sameBandMode matches band and mode case-insensitively"):
    val left = BandMode("20M SSB")
    val right = BandMode("20m SSB")
    assert(QsoStore.sameBandMode(left, right))

  test("sameBandMode does not match different mode"):
    val left = BandMode("20M SSB")
    val right = BandMode("20m CW")
    assert(!QsoStore.sameBandMode(left, right))

  test("potentialDupCallsigns matches prefix case-insensitively across all callsigns"):
    val callsigns = Seq(Callsign("wa9zzz"), Callsign("WA9ABC"), Callsign("K1XYZ"))
    val result = QsoStore.potentialDupCallsigns(callsigns, "wa9")
    assertEquals(result.map(_.value), Seq("WA9ABC", "WA9ZZZ"))

  test("potentialDupCallsigns returns distinct callsigns"):
    val callsigns = Seq(Callsign("WA9ZZZ"), Callsign("wa9zzz"))
    val result = QsoStore.potentialDupCallsigns(callsigns, "WA9")
    assertEquals(result.map(_.value), Seq("WA9ZZZ"))

  test("isLocalNodeQso matches exact node identity"):
    val localNode = NodeIdentity("127.0.0.1", 8080, "host-a", "local-id")
    val qso = qsoForNode(localNode)
    assert(QsoStore.isLocalNodeQso(qso, localNode))

  test("isLocalNodeQso matches same host and port for older journal entries"):
    val localNode = NodeIdentity("127.0.0.1", 8080, "host-a", "local-id")
    val olderJournalNode = NodeIdentity("192.168.1.10", 8080, "host-a", "old-id")
    val qso = qsoForNode(olderJournalNode)
    assert(QsoStore.isLocalNodeQso(qso, localNode))

  test("isLocalNodeQso rejects different host or port"):
    val localNode = NodeIdentity("127.0.0.1", 8080, "host-a", "local-id")
    val otherNode = NodeIdentity("127.0.0.2", 8081, "host-b", "other-id")
    val qso = qsoForNode(otherNode)
    assert(!QsoStore.isLocalNodeQso(qso, localNode))

  private def qsoForNode(node: NodeIdentity): Qso =
    Qso(
      callsign = Callsign("WA9ABC"),
      exchange = Exchange(FdClass("1A"), "IL"),
      bandMode = BandMode("20m SSB"),
      qsoMetadata = QsoMetadata(
        station = StationConfig(),
        node = node,
        contest = ContestType.WFD
      )
    )
