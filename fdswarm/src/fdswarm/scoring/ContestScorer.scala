package fdswarm.scoring

import fdswarm.model.{Mode, Qso}

object ContestScorer:
  def score(qsos: Seq[Qso]): ScoreResult =
    val byMode = Mode.values.map { mode =>
      mode.toString -> qsos.count(_.bandMode.mode == mode)
    }.toMap
    val totalScore = qsos.map { qso =>
      qso.bandMode.mode match
        case Mode.PH => 1
        case Mode.CW | Mode.DIGI => 2
    }.sum

    ScoreResult(
      totalScore = totalScore,
      totalQsos = qsos.size,
      byMode = byMode,
      byBand = qsos.groupBy(_.bandMode.band.name).view.mapValues(_.size).toMap
    )

case class ScoreResult(
    totalScore: Int,
    totalQsos: Int,
    byMode: Map[String, Int],
    byBand: Map[String, Int]
)
