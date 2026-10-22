package fdswarm

import com.typesafe.config.ConfigFactory
import fdswarm.fx.contest.{ContestType, ContestTypes}
import io.circe.parser.decode
import io.circe.syntax.*
import munit.FunSuite

class ContestTypesTest extends FunSuite:
  test("default definitions retain names, display names and Cabrillo values"):
    val types = new ContestTypes()
    assertEquals(types.values.map(_.name), Seq("ARRL", "WFD"))
    assertEquals(types("WFD").displayName, "Winter Field Day")
    assertEquals(types("WFD").cabrilloContestName, "WFD")
    assertEquals(types("ARRL").cabrilloContestName, "ARRL-FD")
    assertEquals(types.get("NONE"), None)
    assertEquals(types("WFD"), new ContestTypes()("WFD"))
    assertEquals(types("WFD").asJson.hcursor.get[String]("name"), Right("WFD"))
    assertEquals(decode[ContestType](types("WFD").asJson.noSpaces), Right(types("WFD")))
    assert(decode[ContestType]("\"unknown\"").isLeft)

  test("modified definitions retain their complete contents"):
    val types = new ContestTypes()
    val contest = types("WFD").copy(displayName = "Local Contest")
    assertEquals(contest.asJson.hcursor.get[String]("displayName"), Right("Local Contest"))
    assertEquals(types.codec.decodeJson(contest.asJson), Right(contest))
    assertEquals(decode[ContestType](contest.asJson.noSpaces), Right(contest))

  test("NONE is not a configured contest"):
    assert(decode[ContestType]("\"NONE\"").isLeft)

  test("incomplete object definitions are rejected"):
    assert(decode[ContestType]("""{"name":"WFD"}""").isLeft)

  test("unknown contest lookup fails"):
    intercept[IllegalArgumentException] {
      new ContestTypes()("UNKNOWN")
    }

  test("definitions are bundled JSON resources rather than reference configuration"):
    assert(!ConfigFactory.defaultReference().hasPath("fdswarm.contests"))
    for name <- Seq("ARRL", "WFD") do
      val stream = Option(getClass.getResourceAsStream(s"/contestTypes/$name.json"))
        .getOrElse(fail(s"Missing contest resource: $name"))
      val json = scala.util.Using.resource(scala.io.Source.fromInputStream(stream, "UTF-8"))(_.mkString)
      val parsed = io.circe.parser.parse(json).fold(error => fail(error.message), identity)
      assertEquals(parsed.hcursor.get[String]("name"), Right(name))
