package leaderboard.search.eval

enum ServingMode {
  case EsOnly
  case QdrantOnly
  case Hybrid
  case Unknown

  def render: String =
    this match {
      case ServingMode.EsOnly     => "es_only"
      case ServingMode.QdrantOnly => "qdrant_only"
      case ServingMode.Hybrid     => "hybrid"
      case ServingMode.Unknown    => "unknown"
    }
}

object ServingMode {
  val stableOrder: List[ServingMode] = List(EsOnly, QdrantOnly, Hybrid, Unknown)
}

enum CandidateSource {
  case Es
  case Qdrant
  case Manual
  case Unknown

  def render: String =
    this match {
      case CandidateSource.Es      => "es"
      case CandidateSource.Qdrant  => "qdrant"
      case CandidateSource.Manual  => "manual"
      case CandidateSource.Unknown => "unknown"
    }
}

object CandidateSource {
  val stableOrder: List[CandidateSource] = List(Es, Qdrant, Manual, Unknown)
}

final case class RoutingPolicyId(value: String) extends AnyVal {
  def render: String = value
}

enum FusionPolicy {
  case None
  case Weighted
  case ReciprocalRankFusion
  case Reranker
  case Future

  def render: String =
    this match {
      case FusionPolicy.None                 => "none"
      case FusionPolicy.Weighted             => "weighted"
      case FusionPolicy.ReciprocalRankFusion => "rrf"
      case FusionPolicy.Reranker             => "reranker"
      case FusionPolicy.Future               => "future"
    }
}

object FusionPolicy {
  val stableOrder: List[FusionPolicy] = List(None, Weighted, ReciprocalRankFusion, Reranker, Future)
}

enum RerankerPolicy {
  case None
  case RuleBased
  case Learned
  case Future

  def render: String =
    this match {
      case RerankerPolicy.None      => "none"
      case RerankerPolicy.RuleBased => "rule_based"
      case RerankerPolicy.Learned   => "learned"
      case RerankerPolicy.Future    => "future"
    }
}

object RerankerPolicy {
  val stableOrder: List[RerankerPolicy] = List(None, RuleBased, Learned, Future)
}

final case class ExperimentId(value: String) extends AnyVal {
  def render: String = value
}

enum QueryClass {
  case ExactProductNameBrand
  case Category
  case IngredientAttribute
  case SemanticDescriptive
  case TypoNoisy
  case FilterHeavy
  case BroadDiscovery
  case Ambiguous
  case NegativeOutOfCatalog

  def render: String =
    this match {
      case QueryClass.ExactProductNameBrand => "exact_product_name_brand"
      case QueryClass.Category              => "category"
      case QueryClass.IngredientAttribute   => "ingredient_attribute"
      case QueryClass.SemanticDescriptive   => "semantic_descriptive"
      case QueryClass.TypoNoisy             => "typo_noisy"
      case QueryClass.FilterHeavy           => "filter_heavy"
      case QueryClass.BroadDiscovery        => "broad_discovery"
      case QueryClass.Ambiguous             => "ambiguous"
      case QueryClass.NegativeOutOfCatalog  => "negative_out_of_catalog"
    }
}

object QueryClass {
  val stableOrder: List[QueryClass] = List(
    ExactProductNameBrand,
    Category,
    IngredientAttribute,
    SemanticDescriptive,
    TypoNoisy,
    FilterHeavy,
    BroadDiscovery,
    Ambiguous,
    NegativeOutOfCatalog,
  )
}

final case class CatalogSnapshotId(value: String) extends AnyVal {
  def render: String = value
}

final case class EvalDatasetId(value: String) extends AnyVal {
  def render: String = value
}

final case class MetricWindow(value: String) extends AnyVal {
  def render: String = value
}

final case class RequestId(value: String) extends AnyVal {
  def render: String = value
}

final case class OfflineEvalRunMetadata(
  servingMode: ServingMode,
  candidateSource: CandidateSource,
  routingPolicyId: RoutingPolicyId,
  fusionPolicy: FusionPolicy,
  rerankerPolicy: RerankerPolicy,
  experimentId: ExperimentId,
  catalogSnapshotId: CatalogSnapshotId,
  evalDatasetId: EvalDatasetId,
  metricWindow: MetricWindow,
)

enum OfflineEvalMetricName {
  case RecallAtK
  case Mrr
  case NdcgAtK
  case ZeroResultRate
  case LowResultRate
  case TopKOverlap
  case BackendContributionRatio
  case Latency
  case FailureCount
  case RegressionPassFail
  case QualityGateDecision

  def render: String =
    this match {
      case OfflineEvalMetricName.RecallAtK                => "recall@k"
      case OfflineEvalMetricName.Mrr                      => "mrr"
      case OfflineEvalMetricName.NdcgAtK                  => "ndcg@k"
      case OfflineEvalMetricName.ZeroResultRate           => "zero_result_rate"
      case OfflineEvalMetricName.LowResultRate            => "low_result_rate"
      case OfflineEvalMetricName.TopKOverlap              => "top_k_overlap"
      case OfflineEvalMetricName.BackendContributionRatio => "backend_contribution_ratio"
      case OfflineEvalMetricName.Latency                  => "latency"
      case OfflineEvalMetricName.FailureCount             => "failure_count"
      case OfflineEvalMetricName.RegressionPassFail       => "regression_pass_fail"
      case OfflineEvalMetricName.QualityGateDecision      => "quality_gate_decision"
    }
}

object OfflineEvalMetricName {
  val plannedM9Metrics: List[OfflineEvalMetricName] = List(
    RecallAtK,
    Mrr,
    NdcgAtK,
    ZeroResultRate,
    LowResultRate,
    TopKOverlap,
    BackendContributionRatio,
    Latency,
    FailureCount,
    RegressionPassFail,
    QualityGateDecision,
  )
}

enum OfflineEvalMetricValue {
  case Numeric(value: BigDecimal)
  case Text(value: String)
  case BooleanValue(value: Boolean)

  def render: String =
    this match {
      case OfflineEvalMetricValue.Numeric(value)      => value.bigDecimal.toPlainString
      case OfflineEvalMetricValue.Text(value)         => value
      case OfflineEvalMetricValue.BooleanValue(value) => value.toString
    }
}

final case class OfflineEvalMetric(
  name: OfflineEvalMetricName,
  value: OfflineEvalMetricValue,
)

final case class OfflineEvalQuerySlice(
  queryClass: QueryClass,
  metrics: List[OfflineEvalMetric],
)

final case class OfflineEvalReportSummary(
  metadata: OfflineEvalRunMetadata,
  slices: List[OfflineEvalQuerySlice],
  aggregateMetrics: List[OfflineEvalMetric],
)

enum TelemetryEventFamily {
  case SearchRequest
  case BackendCandidate
  case ResultExposure
  case OptionalInteraction
  case FailureTimeout

  def render: String =
    this match {
      case TelemetryEventFamily.SearchRequest       => "search_request"
      case TelemetryEventFamily.BackendCandidate    => "backend_candidate"
      case TelemetryEventFamily.ResultExposure      => "result_exposure"
      case TelemetryEventFamily.OptionalInteraction => "optional_interaction"
      case TelemetryEventFamily.FailureTimeout      => "failure_timeout"
    }
}

object TelemetryEventFamily {
  val plannedM8Families: List[TelemetryEventFamily] = List(
    SearchRequest,
    BackendCandidate,
    ResultExposure,
    OptionalInteraction,
    FailureTimeout,
  )
}

final case class TelemetryFieldName(value: String) extends AnyVal {
  def render: String = value
}

enum TelemetryMetricName {
  case RequestCount
  case ZeroResultRate
  case LowResultRate
  case TopKCoverage
  case LatencyP50
  case LatencyP95
  case LatencyP99
  case EsLatency
  case QdrantLatency
  case EmbeddingLatency
  case FusionLatency
  case RerankLatency
  case BackendFailureRate
  case TimeoutRate
  case FallbackUsedRate
  case InteractionProxy
  case ManualRelevanceJudgment

  def render: String =
    this match {
      case TelemetryMetricName.RequestCount            => "request_count"
      case TelemetryMetricName.ZeroResultRate          => "zero_result_rate"
      case TelemetryMetricName.LowResultRate           => "low_result_rate"
      case TelemetryMetricName.TopKCoverage            => "top_k_coverage"
      case TelemetryMetricName.LatencyP50              => "latency_p50"
      case TelemetryMetricName.LatencyP95              => "latency_p95"
      case TelemetryMetricName.LatencyP99              => "latency_p99"
      case TelemetryMetricName.EsLatency               => "es_latency"
      case TelemetryMetricName.QdrantLatency           => "qdrant_latency"
      case TelemetryMetricName.EmbeddingLatency        => "embedding_latency"
      case TelemetryMetricName.FusionLatency           => "fusion_latency"
      case TelemetryMetricName.RerankLatency           => "rerank_latency"
      case TelemetryMetricName.BackendFailureRate      => "backend_failure_rate"
      case TelemetryMetricName.TimeoutRate             => "timeout_rate"
      case TelemetryMetricName.FallbackUsedRate        => "fallback_used_rate"
      case TelemetryMetricName.InteractionProxy        => "interaction_proxy"
      case TelemetryMetricName.ManualRelevanceJudgment => "manual_relevance_judgment"
    }
}

object TelemetryMetricName {
  val plannedM8Metrics: List[TelemetryMetricName] = List(
    RequestCount,
    ZeroResultRate,
    LowResultRate,
    TopKCoverage,
    LatencyP50,
    LatencyP95,
    LatencyP99,
    EsLatency,
    QdrantLatency,
    EmbeddingLatency,
    FusionLatency,
    RerankLatency,
    BackendFailureRate,
    TimeoutRate,
    FallbackUsedRate,
    InteractionProxy,
    ManualRelevanceJudgment,
  )
}

final case class TelemetrySchemaPlan(
  eventFamilies: List[TelemetryEventFamily],
  fields: List[TelemetryFieldName],
  metrics: List[TelemetryMetricName],
)

final case class TelemetrySchemaSummary(
  eventFamilyCount: Int,
  fieldCount: Int,
  metricCount: Int,
)

object TelemetrySchemaSummary {
  def from(plan: TelemetrySchemaPlan): TelemetrySchemaSummary =
    TelemetrySchemaSummary(
      eventFamilyCount = plan.eventFamilies.size,
      fieldCount = plan.fields.size,
      metricCount = plan.metrics.size,
    )
}
