package fdswarm

import com.typesafe.config.ConfigFactory
import fdswarm.fx.contest.{ContestType, ContestTypes}
import io.circe.parser.decode
import io.circe.syntax.*
import munit.FunSuite
import java.time.ZonedDateTime

class ContestTypesTest extends FunSuite:
  private val config = ConfigFactory.load()

  test("default definitions retain names, IDs and Cabrillo values"):
    val types = new ContestTypes(config)
    assertEquals(types.values.map(_.id), Seq("ARRL", "WFD"))
    assertEquals(types("WFD").name, "Winter Field Day")
    assertEquals(types("WFD").cabrilloContestName, "WFD")
    assertEquals(types("ARRL").cabrilloContestName, "ARRL-FD")
    assertEquals(types("NONE"), ContestType.NONE)
    assertEquals(types("WFD"), new ContestTypes(config)("WFD"))
    assertEquals(types("WFD").asJson.noSpaces, "\"WFD\"")
    assertEquals(decode[ContestType]("\"WFD\""), Right(types("WFD")))
    assert(decode[ContestType]("\"unknown\"").isLeft)

  private def customConfig(id: String = "CUSTOM") = ConfigFactory.parseString(s"""
    fdswarm.contests = [{
      name = "$id"
      displayName = "Local Contest"
      cabrilloContestName = "LOCAL"
      allowChosing = false
      dates {
        month = 2
        fullWeekend = 2
        startUtc = "15:00"
        endUtc = "20:59"
      }
    }]
  """)

  test("injected configuration supplies custom types and date rules"):
    val types = new ContestTypes(customConfig())
    val contest = types("CUSTOM")
    assertEquals(contest.name, "Local Contest")
    assert(!contest.allowChosing)
    assertEquals(contest.dates(ZonedDateTime.parse("2027-01-01T00:00Z")).startUtc,
      ZonedDateTime.parse("2027-02-13T15:00Z"))
    assertEquals(decode[ContestType](contest.asJson.noSpaces), Right(contest))
    assertEquals(types.codec.decodeJson(io.circe.Json.fromString("CUSTOM")), Right(contest))

  test("overridden built-in definitions survive serialization"):
    val contest = new ContestTypes(customConfig("WFD"))("WFD")
    assertEquals(decode[ContestType](contest.asJson.noSpaces), Right(contest))

  test("duplicate and reserved IDs fail at startup"):
    intercept[IllegalArgumentException](new ContestTypes(customConfig("NONE")))
    val entry = customConfig().getConfigList("fdswarm.contests").get(0).root().render()
    intercept[IllegalArgumentException] {
      new ContestTypes(ConfigFactory.parseString(s"fdswarm.contests = [$entry, $entry]"))
    }
