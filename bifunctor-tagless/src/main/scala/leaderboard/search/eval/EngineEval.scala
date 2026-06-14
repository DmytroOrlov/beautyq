package leaderboard.search.eval

import leaderboard.model.MasterServiceOfferVariantId
import leaderboard.model.QueryFailure
import leaderboard.search.qdrant.QdrantEmbeddingBenchmarkQueryResult

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

object EngineEvalQueryClass {
  val stableOrder: List[EngineEvalQueryClass] = List(
    ExactService,
    Category,
    StructuredFilter,
    PriceDuration,
    GeoLocal,
    SemanticVague,
    BroadIntent,
    HardNegative,
    Mixed,
  )

  private val ignoredQueryTypes: Set[String] = Set(
    "english",
    "german",
  )

  private val classByQueryType: Map[String, EngineEvalQueryClass] = Map(
    "direct"             -> ExactService,
    "synonym"            -> Category,
    "attribute"          -> StructuredFilter,
    "attribute_heavy"    -> StructuredFilter,
    "price"              -> PriceDuration,
    "numeric"            -> PriceDuration,
    "location"           -> GeoLocal,
    "home_visit"         -> GeoLocal,
    "conversational"     -> SemanticVague,
    "ambiguous"          -> SemanticVague,
    "typo"               -> SemanticVague,
    "broad"              -> BroadIntent,
    "multi_intent"       -> BroadIntent,
    "hard_negative"      -> HardNegative,
    "negative_attribute" -> HardNegative,
    "mixed_language"     -> Mixed,
    "technical_token"    -> Mixed,
  )

  def fromQueryTypes(queryTypes: List[String]): Either[QueryFailure, List[EngineEvalQueryClass]] = {
    val unknown = queryTypes.distinct.filterNot(queryType => ignoredQueryTypes.contains(queryType) || classByQueryType.contains(queryType))

    if (unknown.nonEmpty) {
      Left(
        QueryFailure.operation(
          "engine-eval-query-class",
          s"Unknown EngineEval query type tags: ${unknown.mkString(", ")}",
        )
      )
    } else {
      val classes = queryTypes.flatMap(classByQueryType.get).toSet
      Right(stableOrder.filter(classes.contains))
    }
  }
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

object EngineEvalResult {

  def fromElasticsearchEvalReport(report: BeautySearchEvalReport): EngineEvalResult =
    EngineEvalResult(
      engine = EngineEvalEngine.Elasticsearch,
      queryId = report.queryId,
      variantIds = report.topVariantIds,
    )

  def fromQdrantBenchmarkResult(result: QdrantEmbeddingBenchmarkQueryResult): EngineEvalResult =
    EngineEvalResult(
      engine = EngineEvalEngine.Qdrant,
      queryId = result.queryId,
      variantIds = result.topVariantIds,
    )

  def simulatedHybridFrom(
    es: EngineEvalResult,
    qdrant: EngineEvalResult,
  ): EngineEvalResult =
    EngineEvalResult(
      engine = EngineEvalEngine.SimulatedHybrid,
      queryId = es.queryId,
      variantIds = distinctInOrder(es.variantIds ++ qdrant.variantIds),
    )

  private def distinctInOrder(ids: List[MasterServiceOfferVariantId]): List[MasterServiceOfferVariantId] =
    ids.foldLeft((Set.empty[MasterServiceOfferVariantId], List.empty[MasterServiceOfferVariantId])) {
      case ((seen, acc), id) =>
        if (seen.contains(id)) (seen, acc)
        else (seen + id, id :: acc)
    }._2.reverse
}

final case class EngineEvalComparisonMetrics(
  esRecallCount: Int,
  qdrantRecallCount: Int,
  qdrantComplementCount: Int,
  qdrantNoiseCount: Int,
  overlapCount: Int,
  simulatedHybridGainCount: Int,
)

final case class EngineEvalQueryReport(
  queryId: String,
  expectedRole: EngineExpectedRole,
  expectedVariantIds: Set[MasterServiceOfferVariantId],
  es: EngineEvalResult,
  qdrant: EngineEvalResult,
  simulatedHybrid: EngineEvalResult,
  metrics: EngineEvalComparisonMetrics,
)

object EngineEvalQueryReport {
  def from(
    expectedRole: EngineExpectedRole,
    expectedVariantIds: Set[MasterServiceOfferVariantId],
    es: EngineEvalResult,
    qdrant: EngineEvalResult,
  ): EngineEvalQueryReport = {
    val simulatedHybrid = EngineEvalResult.simulatedHybridFrom(es, qdrant)
    val metrics = EngineEvalComparisonMetrics.from(
      expectedRole = expectedRole,
      expectedVariantIds = expectedVariantIds,
      es = es,
      qdrant = qdrant,
      simulatedHybrid = simulatedHybrid,
    )

    EngineEvalQueryReport(
      queryId = es.queryId,
      expectedRole = expectedRole,
      expectedVariantIds = expectedVariantIds,
      es = es,
      qdrant = qdrant,
      simulatedHybrid = simulatedHybrid,
      metrics = metrics,
    )
  }
}

final case class EngineEvalAggregateMetrics(
  queryCount: Int,
  expectedVariantCount: Int,
  esRecallCount: Int,
  qdrantRecallCount: Int,
  qdrantComplementCount: Int,
  qdrantNoiseCount: Int,
  overlapCount: Int,
  simulatedHybridGainCount: Int,
)

object EngineEvalAggregateMetrics {
  def from(queryReports: List[EngineEvalQueryReport]): EngineEvalAggregateMetrics =
    EngineEvalAggregateMetrics(
      queryCount = queryReports.size,
      expectedVariantCount = queryReports.map(_.expectedVariantIds.size).sum,
      esRecallCount = queryReports.map(_.metrics.esRecallCount).sum,
      qdrantRecallCount = queryReports.map(_.metrics.qdrantRecallCount).sum,
      qdrantComplementCount = queryReports.map(_.metrics.qdrantComplementCount).sum,
      qdrantNoiseCount = queryReports.map(_.metrics.qdrantNoiseCount).sum,
      overlapCount = queryReports.map(_.metrics.overlapCount).sum,
      simulatedHybridGainCount = queryReports.map(_.metrics.simulatedHybridGainCount).sum,
    )
}

final case class EngineEvalRoleAggregateMetrics(
  role: EngineExpectedRole,
  queryCount: Int,
  expectedVariantCount: Int,
  esRecallCount: Int,
  qdrantRecallCount: Int,
  qdrantComplementCount: Int,
  qdrantNoiseCount: Int,
  overlapCount: Int,
  simulatedHybridGainCount: Int,
)

object EngineExpectedRole {
  val stableOrder: List[EngineExpectedRole] = List(
    EsShouldHandle,
    QdrantMayComplement,
    QdrantShouldStaySilent,
    HybridMayImprove,
  )
}

final case class EngineEvalRoleBreakdown(
  byRole: List[EngineEvalRoleAggregateMetrics],
)

object EngineEvalRoleBreakdown {
  def from(queryReports: List[EngineEvalQueryReport]): EngineEvalRoleBreakdown = {
    val grouped = queryReports.groupBy(_.expectedRole)
    val sorted = EngineExpectedRole.stableOrder.flatMap { role =>
      grouped.get(role).map(reports =>
        EngineEvalRoleAggregateMetrics(
          role = role,
          queryCount = reports.size,
          expectedVariantCount = reports.map(_.expectedVariantIds.size).sum,
          esRecallCount = reports.map(_.metrics.esRecallCount).sum,
          qdrantRecallCount = reports.map(_.metrics.qdrantRecallCount).sum,
          qdrantComplementCount = reports.map(_.metrics.qdrantComplementCount).sum,
          qdrantNoiseCount = reports.map(_.metrics.qdrantNoiseCount).sum,
          overlapCount = reports.map(_.metrics.overlapCount).sum,
          simulatedHybridGainCount = reports.map(_.metrics.simulatedHybridGainCount).sum,
        )
      )
    }
    EngineEvalRoleBreakdown(sorted)
  }
}

final case class EngineEvalAggregateReport(
  queryReports: List[EngineEvalQueryReport],
  aggregate: EngineEvalAggregateMetrics,
)

object EngineEvalAggregateReport {
  def from(queryReports: List[EngineEvalQueryReport]): EngineEvalAggregateReport =
    EngineEvalAggregateReport(
      queryReports = queryReports,
      aggregate = EngineEvalAggregateMetrics.from(queryReports),
    )
}

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
