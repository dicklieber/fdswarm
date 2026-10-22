package fdswarm

import fdswarm.fx.contest.{ContestConfig, ContestConfigVersion, ContestTypes}
import fdswarm.model.Callsign
import io.circe.parser.decode
import io.circe.syntax.*
import munit.FunSuite
import java.time.Instant
import java.util.Base64

class ContestConfigAdvertisementTest extends FunSuite:
  private def config(at: Instant) =
    ContestConfig(ContestTypes.configured("WFD"), Callsign("W1AW"), 1, "O", "CT").updated(at)

  test("compact timestamps encode epoch nanoseconds without losing precision"):
    Seq(Instant.EPOCH, Instant.parse("1969-12-31T23:59:59.999999999Z"),
      Instant.parse("2026-10-22T12:00:00.123456789Z"), Instant.MIN, Instant.MAX).foreach { stamp =>
      val value = config(stamp)
      assertEquals(BigInt(Base64.getUrlDecoder.decode(value.timeStampBase64)),
        BigInt(stamp.getEpochSecond) * 1000000000 + stamp.getNano)
    }
    assertEquals(config(Instant.parse("2026-10-22T12:00:00Z")).timeStampBase64.length, 11)

  test("versions compare decoded timestamps, including integer byte length boundaries"):
    val older = ContestConfigVersion(config(Instant.EPOCH.plusNanos(127)))
    val newer = ContestConfigVersion(config(Instant.EPOCH.plusNanos(128)))
    assert(newer > older)
    assert(newer.isNewerThan(Some(older)))
    assert(!older.isNewerThan(Some(newer)))
    assert(!newer.isNewerThan(Some(newer)))
    assert(newer.isNewerThan(None))

  test("equal timestamps have a deterministic fingerprint tie breaker"):
    val first = ContestConfigVersion("a", config(Instant.EPOCH).timeStampBase64)
    val second = first.copy(fingerPrint = "b")
    assert(second.isNewerThan(Some(first)))
    assert(!first.isNewerThan(Some(second)))

  test("version JSON round trips with only the two advertisement fields"):
    val version = ContestConfigVersion(config(Instant.EPOCH))
    assertEquals(version.asJson.asObject.get.keys.toSet, Set("fingerPrint", "timeStamp"))
    assertEquals(decode[ContestConfigVersion](version.asJson.noSpaces), Right(version))
