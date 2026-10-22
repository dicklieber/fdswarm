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

import fdswarm.fx.contest.*
import fdswarm.replication.*
import fdswarm.util.NodeIdentity
import io.circe.generic.auto.{deriveDecoder, deriveEncoder}
import io.circe.parser.decode
import io.circe.syntax.*
import munit.FunSuite

import java.net.URI
import java.time.*

class CirceSupportTest extends FunSuite:

  test("Band round trip"):
    val band = Band.Band_40m
    val json = band.asJson.noSpaces
    val decoded = decode[Band](json).toOption.get
    assertEquals(decoded, band)

  test("ContestConfig round trip"):
    val config = ContestConfig(ContestTypes.configured("WFD"), Callsign("W1AW"), 1, "O", "CT")
    val json = config.asJson.noSpaces
    assertEquals(config.asJson.hcursor.downField("contestType").get[String]("name"), Right(config.contestType.name))
    val decoded = decode[ContestConfig](json).toOption.get
    // ZonedDateTime might lose some precision or change format slightly, but should be equivalent
    assertEquals(decoded.contestType, config.contestType)
    assertEquals(decoded.contestType.displayName, "Winter Field Day")
    assertEquals(decoded.ourCallsign, config.ourCallsign)
    assertEquals(decoded.transmitters, config.transmitters)
    assertEquals(decoded.ourClass, config.ourClass)
    assertEquals(decoded.ourSection, config.ourSection)

  test("ContestType displayName field"):
    assertEquals(ContestTypes.configured("WFD").displayName, "Winter Field Day")
    assertEquals(ContestTypes.configured("ARRL").displayName, "ARRL Field Day")

  test("Exchange round trip"):
    val exchange = Exchange(FdClass(2, 'A'))
    val json = exchange.asJson.noSpaces
    assertEquals(json, "\"2A IL\"")
    val decoded = decode[Exchange](json).toOption.get
    assertEquals(decoded, exchange)

  test("FdClass round trip"):
    val fdClass = FdClass(3, 'H')
    val json = fdClass.asJson.noSpaces
    assertEquals(json, "\"3H\"")
    val decoded = decode[FdClass](json).toOption.get
    assertEquals(decoded, fdClass)

  test("StatusMessage round trip"):
    val hostAndPort = NodeIdentity.mockNodeIdentity
    val bno = BandModeOperator(Callsign("WA9NNN"), BandMode("40M", "CW"), Instant.parse("2026-03-16T15:00:00Z"))
    val config = ContestConfig(ContestTypes.configured("ARRL"), Callsign("WA9NNN"), 1, "A", "IL")
    val status = StatusMessage(storeStats = fdswarm.replication.StoreStats(),
      bandNodeOperator = bno,
      contestConfig = Some(config),
      contestStart = Instant.parse("2026-03-16T15:00:00Z"),
      metrics = Seq.empty)
    val json = status.asJson.noSpaces
    val decoded = decode[StatusMessage](json).toOption.get
    assertEquals(decoded, status)

  test("Node round trip"):
    val config = ContestConfig(ContestTypes.configured("ARRL"), Callsign("W1AW"), 1, "O", "CT")
    val node = Node(URI.create("http://localhost:8080").toURL, config, Callsign("WA9NNN"))
    val json = node.asJson.noSpaces
    val decoded = decode[Node](json).toOption.get
    assertEquals(decoded.url.toString, node.url.toString)
    assertEquals(decoded.ourStation, node.ourStation)

  test("contest configuration can be absent without a placeholder type"):
    assertEquals(decode[Option[ContestConfig]]("null"), Right(None))
    val config = ContestConfig(ContestTypes.configured("WFD"), Callsign("W1AW"), 1, "O", "CT")
    assertEquals(decode[Option[ContestConfig]](config.asJson.noSpaces), Right(Some(config)))

  test("ContestConfig rejects name-only contest types when decoding"):
    val config = ContestConfig(ContestTypes.configured("WFD"), Callsign("W1AW"), 1, "O", "CT")
    val json = config.asJson.mapObject(_.add("contestType", io.circe.Json.fromString("Unknown")))
    assert(decode[ContestConfig](json.noSpaces).isLeft)
