package fdswarm.scoring

import fdswarm.logging.LazyStructuredLogging
import fdswarm.model.Qso
import jakarta.inject.{Inject, Singleton}

import java.util.concurrent.atomic.AtomicReference

@Singleton
class ContestScoringService @Inject() () extends LazyStructuredLogging():
  private val lastResult = new AtomicReference(ContestScorer.score(Seq.empty))

  def refresh(qsos: Seq[Qso]): Unit =
    val result = ContestScorer.score(qsos)
    lastResult.set(result)
    logger.debug(
      "contestScoreRecomputed" -> true,
      "qsos" -> result.totalQsos,
      "score" -> result.totalScore
    )

  def current: ScoreResult = lastResult.get()
