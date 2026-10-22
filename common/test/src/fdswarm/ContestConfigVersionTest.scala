package fdswarm

import com.typesafe.config.ConfigFactory
import fdswarm.fx.contest.{ContestConfig, ContestDateRule, ContestType, ContestTypes}
import fdswarm.model.Callsign
import io.circe.parser.decode
import io.circe.syntax.*
import munit.FunSuite

import java.time.{Instant, LocalDateTime}

class ContestConfigVersionTest extends FunSuite:
  private val stamp = Instant.parse("2026-10-22T12:00:00Z")
  private def config = ContestConfig(ContestTypes.configured("WFD"), Callsign("W1AW"), 1, "O", "CT")

  test("an edited definition and save metadata round trip without resolving bundled names"):
    val customType = ContestTypes.configured("WFD").copy(
      name = "CUSTOM", displayName = "Local Contest", cabrilloContestName = "LOCAL",
      compute = ContestDateRule(ConfigFactory.parseString("""
        month = 2
        fullWeekend = 2
        startUtc = "14:00"
        endUtc = "19:59"
      """))
    )
    val saved = config.copy(contestType = customType).updated(stamp)
    val json = saved.asJson
    assert(json.hcursor.downField("contestType").focus.exists(_.isObject))
    assertEquals(decode[ContestConfig](json.noSpaces), Right(saved))
    assertEquals(saved.contestType.compute(2027).startUtc.toInstant, Instant.parse("2027-02-13T14:00:00Z"))

  test("each save updates timestamp and fingerprint"):
    val first = config.updated(stamp)
    val second = first.updated(stamp.plusSeconds(1))
    assertEquals(first.timeStamp, stamp)
    assertEquals(second.timeStamp, stamp.plusSeconds(1))
    assertEquals(first.fingerPrint.length, 11)
    assertNotEquals(first.fingerPrint, second.fingerPrint)

  test("fingerprint excludes itself and survives round trips"):
    val saved = config.updated(stamp)
    assertEquals(saved.copy(fingerPrint = "stale").withFingerPrint, saved)
    val decoded = decode[ContestConfig](saved.asJson.noSpaces).toOption.get
    assertEquals(decoded.withFingerPrint, saved)

  test("all editable fields, including nested definition fields, affect the fingerprint"):
    val saved = config.updated(stamp)
    val contest = saved.contestType
    val changedRule = ContestDateRule(contest.compute.rule.withValue("month", ConfigFactory.parseString("value = 2").getValue("value")))
    val edits = Seq(
      saved.copy(ourCallsign = Callsign("K9DRS")),
      saved.copy(transmitters = 2), saved.copy(ourClass = "I"), saved.copy(ourSection = "IL"),
      saved.copy(club = "Club"), saved.copy(location = "Site"),
      saved.copy(endTime = Some(LocalDateTime.parse("2027-01-24T16:00:00"))),
      saved.copy(contestType = contest.copy(name = "CUSTOM")),
      saved.copy(contestType = contest.copy(displayName = "Changed")),
      saved.copy(contestType = contest.copy(cabrilloContestName = "Changed")),
      saved.copy(contestType = contest.copy(compute = changedRule))
    )
    edits.foreach(edit => assertNotEquals(edit.withFingerPrint.fingerPrint, saved.fingerPrint))

  test("date rule key order does not affect the fingerprint"):
    val saved = config.updated(stamp)
    val ruleJson = saved.contestType.asJson.hcursor.downField("compute").focus.get
    val reversed = ruleJson.mapObject(obj => io.circe.JsonObject.fromIterable(obj.toIterable.toSeq.reverse))
    val rule = summon[io.circe.Codec[ContestDateRule]].decodeJson(reversed).toOption.get
    assertEquals(saved.copy(contestType = saved.contestType.copy(compute = rule)).withFingerPrint, saved)

  test("name-only format is rejected"):
    val json = config.asJson.mapObject(_.add("contestType", io.circe.Json.fromString("WFD")))
    assert(decode[ContestConfig](json.noSpaces).isLeft)

  test("malformed date rules are rejected when decoding"):
    val json = config.contestType.asJson.mapObject(_.add("compute", io.circe.Json.obj()))
    assert(decode[ContestType](json.noSpaces).isLeft)

  test("optional local end time round trips, and missing end time means no countdown"):
    val noEnd = config.updated(stamp)
    assertEquals(noEnd.endTime, None)
    val legacyJson = noEnd.asJson.mapObject(_.remove("endTime"))
    assertEquals(decode[ContestConfig](legacyJson.noSpaces), Right(noEnd))
    val saved = config.copy(endTime = Some(LocalDateTime.parse("2027-01-24T16:30:00"))).updated(stamp)
    assertEquals(decode[ContestConfig](saved.asJson.noSpaces), Right(saved))
    assertEquals(saved.withFingerPrint, saved)
    assertNotEquals(saved.fingerPrint, noEnd.fingerPrint)
