package leaderboard.search.eval

import leaderboard.model.MasterServiceOfferVariantId

enum EngineEvalEngine {
  case Elasticsearch
  case Qdrant
  case SimulatedHybrid
}

enum EngineEvalQueryClass {
  case ExactService
  case Category
  case StructuredFilter
  case PriceDuration
  case GeoLocal
  case SemanticVague
  case BroadIntent
  case HardNegative
  case Mixed
}

enum EngineExpectedRole {
  case EsShouldHandle
  case QdrantMayComplement
  case QdrantShouldStaySilent
  case HybridMayImprove
}

final case class EngineEvalResult(
  engine: EngineEvalEngine,
  queryId: String,
  variantIds: List[MasterServiceOfferVariantId],
)

final case class EngineEvalComparisonMetrics(
  esRecallCount: Int,
  qdrantRecallCount: Int,
  qdrantComplementCount: Int,
  qdrantNoiseCount: Int,
  overlapCount: Int,
  simulatedHybridGainCount: Int,
)

object EngineEvalComparisonMetrics {

  def from(
    expectedRole: EngineExpectedRole,
    expectedVariantIds: Set[MasterServiceOfferVariantId],
    es: EngineEvalResult,
    qdrant: EngineEvalResult,
    simulatedHybrid: EngineEvalResult,
  ): EngineEvalComparisonMetrics = {
    val esIds       = es.variantIds.toSet
    val qdrantIds   = qdrant.variantIds.toSet
    val hybridIds   = simulatedHybrid.variantIds.toSet

    val esRecall              = expectedVariantIds.intersect(esIds).size
    val qdrantRecall          = expectedVariantIds.intersect(qdrantIds).size
    val qdrantComplement      = expectedVariantIds.intersect(qdrantIds).diff(esIds).size
    val overlap               = esIds.intersect(qdrantIds).size
    val simulatedHybridGain   = expectedVariantIds.intersect(hybridIds).diff(esIds).size
    val qdrantNoise           = expectedRole match {
      case EngineExpectedRole.QdrantShouldStaySilent => qdrantIds.size
      case _                                        => 0
    }

    EngineEvalComparisonMetrics(
      esRecallCount = esRecall,
      qdrantRecallCount = qdrantRecall,
      qdrantComplementCount = qdrantComplement,
      qdrantNoiseCount = qdrantNoise,
      overlapCount = overlap,
      simulatedHybridGainCount = simulatedHybridGain,
    )
  }
}
