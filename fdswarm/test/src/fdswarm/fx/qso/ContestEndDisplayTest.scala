package fdswarm.fx.qso

import munit.FunSuite
import java.time.LocalDateTime

class ContestEndDisplayTest extends FunSuite:
  private val end = LocalDateTime.parse("2027-01-24T16:30:00")

  test("an unset end time produces no countdown"):
    assertEquals(ContestDetailPanel.endMessage("Winter Field Day", None, end), None)

  test("a future end time produces the green countdown"):
    val message = ContestDetailPanel.endMessage("Winter Field Day", Some(end), end.minusHours(1)).get
    assert(message._1.startsWith("Winter Field Day ends in "))
    assertEquals(message._2, "contest-during")

  test("a past end time produces the red ended message"):
    val message = ContestDetailPanel.endMessage("Winter Field Day", Some(end), end.plusHours(1)).get
    assert(message._1.startsWith("Winter Field Day ended "))
    assertEquals(message._2, "contest-after")

  test("the exact end time is treated as ended"):
    assertEquals(ContestDetailPanel.endMessage("ARRL Field Day", Some(end), end).get._2, "contest-after")
