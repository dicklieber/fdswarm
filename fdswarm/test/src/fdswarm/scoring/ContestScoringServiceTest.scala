package fdswarm.scoring

import fdswarm.fx.contest.ContestTypes
import fdswarm.model.{BandMode, Callsign, Exchange, FdClass, Mode, Qso, QsoMetadata}
import munit.FunSuite

import java.time.Instant

class ContestScoringServiceTest extends FunSuite:
  private def qso(mode: Mode, band: String = "40m"): Qso =
    Qso(
      callsign = Callsign("W1AW"),
      exchange = Exchange(FdClass(1, 'O'), "CT"),
      bandMode = BandMode(band, mode.toString),
      qsoMetadata = QsoMetadata.testQsoMetadata,
      stamp = Instant.EPOCH
    )

  test("empty dataset has zero score and counts"):
    val result = ContestScorer.score(Seq.empty)
    assertEquals(result.totalScore, 0)
    assertEquals(result.totalQsos, 0)
    assertEquals(result.byMode, Map("CW" -> 0, "PH" -> 0, "DIGI" -> 0))
    assertEquals(result.byBand, Map.empty[String, Int])

  test("phone counts once and CW and digital count twice"):
    for (mode, points) <- Seq(Mode.PH -> 1, Mode.CW -> 2, Mode.DIGI -> 2) do
      val result = ContestScorer.score(Seq.fill(3)(qso(mode)))
      assertEquals(result.totalScore, 3 * points)
      assertEquals(result.totalQsos, 3)
      assertEquals(result.byMode(mode.toString), 3)

  test("mixed modes sum points and retain counts by mode and band"):
    val result = ContestScorer.score(Seq(
      qso(Mode.PH), qso(Mode.CW), qso(Mode.DIGI, "20m"), qso(Mode.PH, "20m")
    ))
    assertEquals(result.totalScore, 6)
    assertEquals(result.totalQsos, 4)
    assertEquals(result.byMode, Map("CW" -> 1, "PH" -> 2, "DIGI" -> 1))
    assertEquals(result.byBand, Map("40m" -> 2, "20m" -> 2))

  test("score is independent of the contest in QSO metadata"):
    val qsos = Seq(qso(Mode.PH), qso(Mode.CW), qso(Mode.DIGI))
    val arrlQsos = qsos.map(q =>
      q.copy(qsoMetadata = q.qsoMetadata.copy(contest = ContestTypes.configured("ARRL").name))
    )
    assertEquals(ContestScorer.score(qsos).totalScore, 5)
    assertEquals(ContestScorer.score(arrlQsos), ContestScorer.score(qsos))

  test("refresh replaces the score after corrections, removals and clearing"):
    val service = new ContestScoringService()
    assertEquals(service.current.totalScore, 0)
    val phone = qso(Mode.PH)
    val cw = qso(Mode.CW)
    service.refresh(Seq(phone, cw))
    assertEquals(service.current.totalScore, 3)
    val corrected = phone.copy(bandMode = BandMode("40m", "DIGI"))
    service.refresh(Seq(corrected, cw))
    assertEquals(service.current.totalScore, 4)
    service.refresh(Seq(cw))
    assertEquals(service.current.totalScore, 2)
    assertEquals(service.current.totalQsos, 1)
    service.refresh(Seq.empty)
    assertEquals(service.current, ContestScorer.score(Seq.empty))
