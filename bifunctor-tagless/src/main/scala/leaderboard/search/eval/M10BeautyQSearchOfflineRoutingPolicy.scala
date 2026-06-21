package leaderboard.search.eval

/** M10 pure offline routing policy foundation contract.
  *
  * This maps offline query classification output to future *offline* retrieval strategy intent. It is a
  * planning contract only: outputs are strategy intents to study offline, never production routes and
  * never an instruction to execute a backend. It is a total pure function of a classification result:
  * it never calls production `/beauty-search`, never creates an ES or Qdrant client, never runs
  * Elasticsearch or Qdrant, and never touches a route, plugin, DI, or HTTP source.
  *
  * It preserves the standing facts: default `/beauty-search` remains ES-backed, the Qdrant opt-in route
  * remains disabled-by-default, and Qdrant production activation remains not approved. It claims no
  * hybrid serving, fallback, score fusion, reranking, production telemetry, route switch, quality green,
  * production readiness, route activation, or serving approval. In particular, a `MixedIntent` query maps
  * to an offline `CombinedEsQdrantComparison` study intent, which is NOT production hybrid serving.
  */
enum M10BeautyQSearchOfflineRetrievalStrategyIntent {
  case EsOnlyCandidateRetrieval
  case QdrantOnlyCandidateRetrieval
  case CombinedEsQdrantComparison
  case ManualReviewBlocked
  case NoOpNoise

  def render: String =
    this match {
      case M10BeautyQSearchOfflineRetrievalStrategyIntent.EsOnlyCandidateRetrieval     => "es_only_candidate_retrieval"
      case M10BeautyQSearchOfflineRetrievalStrategyIntent.QdrantOnlyCandidateRetrieval => "qdrant_only_candidate_retrieval"
      case M10BeautyQSearchOfflineRetrievalStrategyIntent.CombinedEsQdrantComparison   => "combined_es_qdrant_comparison"
      case M10BeautyQSearchOfflineRetrievalStrategyIntent.ManualReviewBlocked          => "manual_review_blocked"
      case M10BeautyQSearchOfflineRetrievalStrategyIntent.NoOpNoise                    => "no_op_noise"
    }

  /** Whether this intent describes offline candidate-retrieval study (vs manual review / no-op). */
  def isBackendCandidateRetrievalIntent: Boolean =
    this match {
      case M10BeautyQSearchOfflineRetrievalStrategyIntent.EsOnlyCandidateRetrieval     => true
      case M10BeautyQSearchOfflineRetrievalStrategyIntent.QdrantOnlyCandidateRetrieval => true
      case M10BeautyQSearchOfflineRetrievalStrategyIntent.CombinedEsQdrantComparison   => true
      case M10BeautyQSearchOfflineRetrievalStrategyIntent.ManualReviewBlocked          => false
      case M10BeautyQSearchOfflineRetrievalStrategyIntent.NoOpNoise                    => false
    }
}

object M10BeautyQSearchOfflineRetrievalStrategyIntent {
  val stableOrder: List[M10BeautyQSearchOfflineRetrievalStrategyIntent] = List(
    EsOnlyCandidateRetrieval,
    QdrantOnlyCandidateRetrieval,
    CombinedEsQdrantComparison,
    ManualReviewBlocked,
    NoOpNoise,
  )
}

/** Standing offline boundary asserted by every routing decision. Every claim-bearing field is `false`;
  * the only `true` fields describe offline-planning posture and the unchanged ES-backed default.
  */
final case class M10BeautyQSearchOfflineRoutingBoundary(
  offlinePlanningOnly: Boolean,
  defaultBeautySearchEsBacked: Boolean,
  qdrantOptInDisabledByDefault: Boolean,
  qdrantProductionActivationApproved: Boolean,
  productionRouteActivated: Boolean,
  defaultRouteSwitched: Boolean,
  productionBeautySearchCalled: Boolean,
  esClientCreated: Boolean,
  qdrantClientCreated: Boolean,
  esExecuted: Boolean,
  qdrantExecuted: Boolean,
  routePluginDiHttpInvolved: Boolean,
  realBackendCallImplemented: Boolean,
  realBackendCallRequired: Boolean,
  hybridServingImplied: Boolean,
  fallbackImplied: Boolean,
  scoreFusionImplied: Boolean,
  rerankingImplied: Boolean,
  productionTelemetryImplied: Boolean,
  qualityGreenClaimed: Boolean,
  productionReadinessClaimed: Boolean,
  routeActivationClaimed: Boolean,
  servingApprovalClaimed: Boolean,
)

object M10BeautyQSearchOfflineRoutingBoundary {
  val Standing: M10BeautyQSearchOfflineRoutingBoundary =
    M10BeautyQSearchOfflineRoutingBoundary(
      offlinePlanningOnly = true,
      defaultBeautySearchEsBacked = true,
      qdrantOptInDisabledByDefault = true,
      qdrantProductionActivationApproved = false,
      productionRouteActivated = false,
      defaultRouteSwitched = false,
      productionBeautySearchCalled = false,
      esClientCreated = false,
      qdrantClientCreated = false,
      esExecuted = false,
      qdrantExecuted = false,
      routePluginDiHttpInvolved = false,
      realBackendCallImplemented = false,
      realBackendCallRequired = false,
      hybridServingImplied = false,
      fallbackImplied = false,
      scoreFusionImplied = false,
      rerankingImplied = false,
      productionTelemetryImplied = false,
      qualityGreenClaimed = false,
      productionReadinessClaimed = false,
      routeActivationClaimed = false,
      servingApprovalClaimed = false,
    )
}

final case class M10BeautyQSearchOfflineRoutingDecision(
  queryId: String,
  category: M10BeautyQSearchQueryCategory,
  strategyIntent: M10BeautyQSearchOfflineRetrievalStrategyIntent,
  boundary: M10BeautyQSearchOfflineRoutingBoundary,
  rationale: String,
)

object M10BeautyQSearchOfflineRoutingPolicy {

  /** Pure, total, deterministic mapping from a classification result to an offline strategy intent. */
  def strategyIntentFor(result: M10BeautyQSearchQueryClassificationResult): M10BeautyQSearchOfflineRetrievalStrategyIntent =
    result.category match {
      case M10BeautyQSearchQueryCategory.ProviderLookup        => M10BeautyQSearchOfflineRetrievalStrategyIntent.EsOnlyCandidateRetrieval
      case M10BeautyQSearchQueryCategory.ServiceIntent         => M10BeautyQSearchOfflineRetrievalStrategyIntent.EsOnlyCandidateRetrieval
      case M10BeautyQSearchQueryCategory.AttributeFilterIntent => M10BeautyQSearchOfflineRetrievalStrategyIntent.EsOnlyCandidateRetrieval
      case M10BeautyQSearchQueryCategory.LocationIntent        => M10BeautyQSearchOfflineRetrievalStrategyIntent.EsOnlyCandidateRetrieval
      case M10BeautyQSearchQueryCategory.PriceBudgetIntent     => M10BeautyQSearchOfflineRetrievalStrategyIntent.EsOnlyCandidateRetrieval
      case M10BeautyQSearchQueryCategory.AvailabilityTimeIntent => M10BeautyQSearchOfflineRetrievalStrategyIntent.EsOnlyCandidateRetrieval
      case M10BeautyQSearchQueryCategory.ComparisonExplorationIntent => M10BeautyQSearchOfflineRetrievalStrategyIntent.QdrantOnlyCandidateRetrieval
      case M10BeautyQSearchQueryCategory.MixedIntent           => M10BeautyQSearchOfflineRetrievalStrategyIntent.CombinedEsQdrantComparison
      case M10BeautyQSearchQueryCategory.NoisyAmbiguousNonBeautyIntent =>
        if (result.manualReviewEligible) M10BeautyQSearchOfflineRetrievalStrategyIntent.ManualReviewBlocked
        else M10BeautyQSearchOfflineRetrievalStrategyIntent.NoOpNoise
    }

  def decide(result: M10BeautyQSearchQueryClassificationResult): M10BeautyQSearchOfflineRoutingDecision = {
    val intent = strategyIntentFor(result)
    M10BeautyQSearchOfflineRoutingDecision(
      queryId = result.queryId,
      category = result.category,
      strategyIntent = intent,
      boundary = M10BeautyQSearchOfflineRoutingBoundary.Standing,
      rationale = rationale(result.category, intent),
    )
  }

  private def rationale(
    category: M10BeautyQSearchQueryCategory,
    intent: M10BeautyQSearchOfflineRetrievalStrategyIntent,
  ): String =
    intent match {
      case M10BeautyQSearchOfflineRetrievalStrategyIntent.EsOnlyCandidateRetrieval =>
        s"Offline strategy intent only: ${category.render} studied via ES-only candidate retrieval planning; default search path unchanged."
      case M10BeautyQSearchOfflineRetrievalStrategyIntent.QdrantOnlyCandidateRetrieval =>
        s"Offline strategy intent only: ${category.render} studied via Qdrant-only candidate retrieval planning; Qdrant opt-in stays disabled by default."
      case M10BeautyQSearchOfflineRetrievalStrategyIntent.CombinedEsQdrantComparison =>
        s"Offline strategy intent only: ${category.render} studied via combined ES/Qdrant offline comparison; this is an offline study, not a single production serving path."
      case M10BeautyQSearchOfflineRetrievalStrategyIntent.ManualReviewBlocked =>
        s"Offline strategy intent only: ${category.render} held for manual review; not routed to any backend."
      case M10BeautyQSearchOfflineRetrievalStrategyIntent.NoOpNoise =>
        s"Offline strategy intent only: ${category.render} treated as no-op noise; not routed to any backend."
    }

  val RepresentativeDecisions: List[M10BeautyQSearchOfflineRoutingDecision] =
    M10BeautyQSearchQueryClassification.RepresentativeResults.map(decide)

  def decisionFor(queryId: String): Option[M10BeautyQSearchOfflineRoutingDecision] =
    RepresentativeDecisions.find(_.queryId == queryId)
}
