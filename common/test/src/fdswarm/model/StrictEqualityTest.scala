package fdswarm.model

import fdswarm.util.NodeIdentity
import munit.FunSuite

import scala.compiletime.testing.typeChecks
import scala.language.strictEquality

class StrictEqualityTest extends FunSuite:
  test("domain values support same-type comparisons under strict equality"):
    summon[CanEqual[Callsign, Callsign]]
    summon[CanEqual[Band, Band]]
    summon[CanEqual[BandClass, BandClass]]
    summon[CanEqual[BandRegion, BandRegion]]
    summon[CanEqual[Mode, Mode]]
    summon[CanEqual[BandMode, BandMode]]
    summon[CanEqual[FdClass, FdClass]]
    summon[CanEqual[Exchange, Exchange]]
    summon[CanEqual[StationClass, StationClass]]
    summon[CanEqual[Qso, Qso]]
    summon[CanEqual[QsoMetadata, QsoMetadata]]
    summon[CanEqual[BandModeOperator, BandModeOperator]]
    summon[CanEqual[NodeIdentity, NodeIdentity]]
    assert(Callsign("w1aw") == Callsign("W1AW"))
    assert(Callsign("W1AW") != Callsign("K9DRS"))
    assert(BandMode("40m CW") == BandMode("40m", "CW"))
    assert(Exchange("1I IL") == Exchange(FdClass(1, 'I'), "IL"))

  test("node equality still uses instance identity rather than address"):
    val node = NodeIdentity("127.0.0.1", 8900, "station", "abc")
    assert(node == node.copy(hostIp = "192.168.1.2", port = 8901, hostName = "renamed"))
    assert(node != node.copy(instanceId = "def"))

  test("unrelated domain types cannot be compared under strict equality"):
    assert(!typeChecks("""
      import scala.language.strictEquality
      import fdswarm.model.*
      Callsign("W1AW") == "W1AW"
    """))
    assert(!typeChecks("""
      import scala.language.strictEquality
      import fdswarm.model.*
      Band.Band_40m == Mode.CW
    """))
    assert(!typeChecks("""
      import scala.language.strictEquality
      import fdswarm.model.*
      FdClass(1, 'I') == StationClass('I', "Indoor")
    """))
    assert(!typeChecks("""
      import scala.language.strictEquality
      import fdswarm.util.NodeIdentity
      NodeIdentity("127.0.0.1", 8900, "station", "abc") == "abc"
    """))

  test("enum pattern matching works under strict equality"):
    def points(mode: Mode): Int = mode match
      case Mode.PH => 1
      case Mode.CW | Mode.DIGI => 2
    assertEquals(points(Mode.PH), 1)
    assertEquals(points(Mode.CW), 2)
    assertEquals(points(Mode.DIGI), 2)
