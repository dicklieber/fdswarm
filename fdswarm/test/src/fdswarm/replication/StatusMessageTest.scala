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

package fdswarm.replication

import fdswarm.fx.contest.ContestTypes

import fdswarm.fx.contest.{ContestConfig, ContestType}
import fdswarm.model.{BandMode, BandModeOperator, Callsign}
import io.circe.syntax.*
import munit.FunSuite

import java.time.Instant

class StatusMessageTest extends FunSuite:
  private val dummyBno = BandModeOperator(Callsign("WA9NNN"), BandMode("40M", "CW"), Instant.parse("2026-03-16T20:11:04Z"))
  private val dummyContestConfig = ContestConfig(ContestTypes.configured("ARRL"),
    Callsign("WA9NNN"),
    1,
    "A",
    "IL")


  test("gzip round trip") {
//    val hp = NodeIdentity("localhost", 8080, name =)
    val sm = StatusMessage(storeStats = StoreStats(),
      bandNodeOperator = dummyBno,
      contestConfig = Some(fdswarm.fx.contest.ContestConfigVersion(dummyContestConfig)),
      contestStart = Instant.parse("2026-03-16T20:11:04Z"),
      metrics = Seq.empty)
    
    val packet = sm.toPacket

    val backAgain = StatusMessage(packet)

    assertEquals(backAgain, sm)
  }

  test("fromPacket should deserialize from gzipped packet") {
    val sm = StatusMessage(storeStats = StoreStats(),
      bandNodeOperator = dummyBno,
      contestConfig = Some(fdswarm.fx.contest.ContestConfigVersion(dummyContestConfig)),
      contestStart = Instant.parse("2026-03-16T20:11:04Z"),
      metrics = Seq.empty)
    
    val packet = sm.toPacket
    val readSm = StatusMessage.apply(packet)
    
    assertEquals(readSm, sm)
  }

  test("fromPacket should throw if not a gzip") {
    intercept[java.io.IOException] {
      StatusMessage.apply("not a gzip".getBytes("UTF-8"))
    }
  }

  test("unconfigured status round trip"):
    val status = StatusMessage(
      storeStats = StoreStats(),
      bandNodeOperator = dummyBno,
      contestConfig = None,
      contestStart = Instant.EPOCH,
      metrics = Seq.empty
    )
    assertEquals(status.asJson.hcursor.downField("contestConfig").focus, Some(io.circe.Json.Null))
    assertEquals(StatusMessage(status.toPacket), status)

  test("status packets advertise versions without the full contest definition"):
    val config = dummyContestConfig.updated(Instant.parse("2026-10-22T12:00:00Z"))
    val status = StatusMessage(StoreStats(), dummyBno,
      Some(fdswarm.fx.contest.ContestConfigVersion(config)), Instant.EPOCH, Seq.empty)
    val advertised = status.asJson.hcursor.downField("contestConfig").focus.get
    assertEquals(advertised.asObject.get.keys.toSet, Set("fingerPrint", "timeStamp"))
    assert(!status.asJson.noSpaces.contains("compute"))
    assertEquals(StatusMessage(status.toPacket), status)

  test("traffic metrics travel in existing status packets"):
    val traffic = new fdswarm.metric.NetworkTraffic(new io.dropwizard.metrics5.MetricRegistry)
    traffic.udp(Service.Status, "sent", 200)
    traffic.qsoFetchApplied(0)
    val status = StatusMessage(StoreStats(), dummyBno, None, Instant.EPOCH, traffic.snapshot)
    val decoded = StatusMessage(status.toPacket)
    assertEquals(fdswarm.metric.NetworkTraffic.report(decoded.metrics),
      fdswarm.metric.NetworkTraffic.report(status.metrics))
