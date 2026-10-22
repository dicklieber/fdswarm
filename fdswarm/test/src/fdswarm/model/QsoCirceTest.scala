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

import com.organization.BuildInfo
import fdswarm.fx.station.StationConfig
import io.circe.parser.decode
import io.circe.syntax.*
import munit.FunSuite

import java.time.Instant

class QsoCirceTest extends FunSuite:

  test("Qso round trip via Circe"):
    val station = StationConfig(operator = Callsign("WA9NNN"), rig = "FT-891", antenna = "End Fed")
    val bandMode = BandMode("40M", "CW")
    val qso = Qso(
      callsign = Callsign("K1ABC"),
      exchange = Exchange(FdClass("1A"), "CT"),
      bandMode = bandMode,
      qsoMetadata = fdswarm.model.QsoMetadata.testQsoMetadata,
      stamp = Instant.EPOCH.truncatedTo(java.time.temporal.ChronoUnit.SECONDS),
      uuid = "unique-id-123"
    )

    // Encode to JSON
    val json = qso.asJson.noSpaces
    val withSpaces = qso.asJson.spaces2
    assertEquals(withSpaces, s"""{
                               |  "callsign" : "K1ABC",
                               |  "exchange" : "1A CT",
                               |  "bandMode" : "40m CW",
                               |  "qsoMetadata" : {
                               |    "station" : {
                               |      "operator" : "",
                               |      "rig" : "",
                               |      "antenna" : ""
                               |    },
                               |    "node" : {
                               |      "hostIp" : "44.0.0.1",
                               |      "port" : 8888,
                               |      "hostName" : "testHost",
                               |      "instanceId" : "qO-"
                               |    },
                               |    "contest" : {
                               |      "name" : "WFD",
                               |      "displayName" : "Winter Field Day",
                               |      "compute" : {
                               |        "endUtc" : "21:59",
                               |        "fullWeekend" : 4,
                               |        "month" : 1,
                               |        "setup" : {
                               |          "daysFromSaturday" : -1,
                               |          "maxCumulativeDuration" : "12h",
                               |          "startUtc" : "16:00"
                               |        },
                               |        "startUtc" : "16:00"
                               |      },
                               |      "cabrilloContestName" : "WFD"
                               |    },
                               |    "v" : "${BuildInfo.version}"
                               |  },
                               |  "stamp" : "1970-01-01T00:00:00Z",
                               |  "uuid" : "unique-id-123"
                               |}""".stripMargin)
    // Decode back to Qso
    val decoded = decode[Qso](json)
    
    assert(decoded.isRight, s"Failed to decode: ${decoded.left.map(_.getMessage)}")
    assertEquals(decoded.toOption.get, qso)

  test("Callsign Circe round trip"):
    val callsign = Callsign("wa9nnn")
    val json = callsign.asJson.noSpaces
    assertEquals(json, "\"WA9NNN\"")
    val decoded = decode[Callsign](json).getOrElse(fail("failed to decode"))
    assertEquals(decoded, Callsign("WA9NNN"))

  test("BandMode Circe round trip"):
    val bandMode = BandMode("20M", "PH")
    val json = bandMode.asJson.noSpaces
    val decoded = decode[BandMode](json).getOrElse(fail("failed to decode"))
    assertEquals(decoded, bandMode)

  test("QsoMetadata Circe round trip"):
    val station = StationConfig(operator = Callsign("N9VTB"), rig = "IC-7300", antenna = "Dipole")
    val metadata = fdswarm.model.QsoMetadata.testQsoMetadata
    val json = metadata.asJson.noSpaces
    val decoded = decode[QsoMetadata](json).getOrElse(fail("failed to decode"))
    assertEquals(decoded, metadata)
