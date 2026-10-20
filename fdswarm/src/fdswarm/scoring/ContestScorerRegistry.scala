package fdswarm.scoring

import fdswarm.fx.contest.ContestType
import jakarta.inject.{Inject, Singleton}

@Singleton
class ContestScorerRegistry @Inject() (
                                        wfdScorer: WfdScorer,
                                        arrlFdScorer: ArrlFdScorer
                                      ):

  def forType(ct: ContestType): ContestScorer =
    ct.name match
      case "WFD"  => wfdScorer
      case "ARRL" => arrlFdScorer
      case _ => NoopScorer
