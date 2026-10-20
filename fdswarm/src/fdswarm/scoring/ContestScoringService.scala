package fdswarm.scoring

import fdswarm.fx.contest.{ContestConfigManager, ContestType}
import fdswarm.logging.LazyStructuredLogging
import fdswarm.model.Qso
import jakarta.inject.*

import java.util.concurrent.atomic.{AtomicInteger, AtomicLong, AtomicReference}

@Singleton
class ContestScoringService @Inject() (
                                        contestConfigManager: ContestConfigManager,
                                        contestScoringConfigManager: ContestScoringConfigManager,
                                        contestScorerRegistry: ContestScorerRegistry
                                      )
  extends LazyStructuredLogging() :

  private var scorer: ContestScorer =
    scorerFor(
      contestConfigManager.contestConfigProperty.value.map(_.contestType)
    )

  private val finalScoreValue = new AtomicLong(0L)
  private val rawPointsValue = new AtomicLong(0L)
  private val totalQsosValue = new AtomicLong(0L)
  private val multiplierValue = new AtomicReference[Double](1.0)
  private val contestTypeValue = new AtomicInteger(
    toContestTypeMetricValue(
      contestConfigManager.contestConfigProperty.value.map(_.contestType)
    )
  )
  private val lastResult =
    new java.util.concurrent.atomic.AtomicReference[ScoreResult](
      ScoreResult(0, 0, 1.0, 0, Map.empty, Map.empty)
    )
  private val latestQsos =
    new java.util.concurrent.atomic.AtomicReference[Seq[Qso]](
      Seq.empty
    )

  contestConfigManager.contestConfigProperty.onChange { (_, oldConfig, newConfig) =>
    val oldType = oldConfig.map(_.contestType)
    val newType = newConfig.map(_.contestType)

    if oldType != newType then
      scorer = scorerFor(newType)
      contestTypeValue.set(toContestTypeMetricValue(newType))
      logger.info(
        "contestTypeChanged" -> s"$oldType->$newType",
        "scorer" -> scorer.name
      )
      recompute()
  }

  contestScoringConfigManager.contestScoringConfigProperty.onChange { (_, _, newConfig) =>
    logger.debug(
      "contestScoringConfigChanged" -> true,
      "powerWatts" -> newConfig.powerWatts,
      "powerSource" -> newConfig.powerSource.toString,
      "enabledObjectives" -> newConfig.claimedObjectives.toSeq.sorted.mkString(","),
      "includeBonusesInLiveScore" -> newConfig.includeBonusesInLiveScore
    )
    recompute()
  }

  recompute()

  def refresh(
      qsos: Seq[Qso]
    ): Unit =
    latestQsos.set(qsos)
    recompute()

  private def recompute(): Unit =
    val qsos = latestQsos.get()
    val scoringConfig = contestScoringConfigManager.current

    val result =
      scorer.score(
        qsos = qsos,
        scoringConfig = scoringConfig
      )
    lastResult.set(result)
    finalScoreValue.set(result.totalScore.toLong)
    rawPointsValue.set(result.rawPoints.toLong)
    totalQsosValue.set(result.totalQsos.toLong)
    multiplierValue.set(result.multiplier)

    logger.debug(
      "contestScoreRecomputed" -> true,
      "contestType" -> contestConfigManager.contestConfigProperty.value.map(_.contestType).toString,
      "scorer" -> scorer.name,
      "qsos" -> result.totalQsos,
      "rawPoints" -> result.rawPoints,
      "multiplier" -> result.multiplier,
      "finalScore" -> result.totalScore
    )

  private def scorerFor(contestType: Option[ContestType]): ContestScorer =
    contestType.map(contestScorerRegistry.forType).getOrElse(NoopScorer)

  private def toContestTypeMetricValue(contestType: Option[ContestType]): Int =
    contestType.map(_.name) match
      case Some("WFD")  => 1
      case Some("ARRL") => 2
      case _ => 0

  def current: ScoreResult =
    lastResult.get()
