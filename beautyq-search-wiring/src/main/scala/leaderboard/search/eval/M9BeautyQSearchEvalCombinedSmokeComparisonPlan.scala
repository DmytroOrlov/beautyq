package leaderboard.search.eval

/** Pure combined ES/Qdrant resource-gated comparison PLAN contract.
  *
  * This is planning-only. It consumes the accepted real-call checkpoint decision and decides whether
  * the eval state is allowed to *plan* a future offline, resource-gated ES vs Qdrant comparison. It
  * only produces a plan when the checkpoint decision is
  * `eligible_for_es_qdrant_resource_gated_comparison`, which itself requires both explicit ES and
  * Qdrant resource configs plus operator approval.
  *
  * It never runs Elasticsearch or Qdrant, never creates an ES or Qdrant client, never calls
  * production `/beauty-search`, and never touches a route, plugin, DI, or HTTP source. It is
  * offline/resource-gated comparison only. It preserves that Qdrant production activation is not
  * approved, that default `/beauty-search` remains ES-backed, and that no hybrid serving, fallback,
  * score fusion, reranking, production telemetry, or route switch is implemented. The comparison may
  * describe ES candidate ids, Qdrant candidate ids, overlap, misses, unexpected candidates, and
  * missing lookup rate, but it never claims quality green or production readiness.
  */
final case class M9BeautyQSearchEvalCombinedComparisonPrerequisite(
  key: String,
  description: String,
)

final case class M9BeautyQSearchEvalCombinedComparisonSelectedQuery(
  queryId: String,
  queryClass: QueryClass,
)

final case class M9BeautyQSearchEvalCombinedComparisonArtifactShape(
  evalDatasetId: String,
  catalogSnapshotId: String,
  esExecutionMode: M9OfflineEvalBackendExecutionMode,
  qdrantExecutionMode: M9OfflineEvalBackendExecutionMode,
  candidateSources: List[CandidateSource],
  markdownFilename: String,
)

enum M9BeautyQSearchEvalCombinedComparisonDimension {
  case EsCandidateIds
  case QdrantCandidateIds
  case Overlap
  case Misses
  case UnexpectedCandidates
  case MissingLookupRate

  def render: String =
    this match {
      case EsCandidateIds       => "es_candidate_ids"
      case QdrantCandidateIds   => "qdrant_candidate_ids"
      case Overlap              => "overlap"
      case Misses               => "misses"
      case UnexpectedCandidates => "unexpected_candidates"
      case MissingLookupRate    => "missing_lookup_rate"
    }
}

enum M9BeautyQSearchEvalCombinedComparisonSkipReason {
  case EsResourceConfigAbsent
  case QdrantResourceConfigAbsent
  case OperatorApprovalAbsent
  case GateDisabledByDefault
  case RealBackendCallNotImplemented

  def render: String =
    this match {
      case EsResourceConfigAbsent        => "es_resource_config_absent_skip"
      case QdrantResourceConfigAbsent    => "qdrant_resource_config_absent_skip"
      case OperatorApprovalAbsent        => "operator_approval_absent_skip"
      case GateDisabledByDefault         => "real_backend_gate_disabled_by_default_skip"
      case RealBackendCallNotImplemented => "real_backend_call_not_implemented_skip"
    }
}

final case class M9BeautyQSearchEvalCombinedComparisonPlan(
  requiredCheckpointDecision: M9BeautyQSearchEvalRealCallCheckpointDecision,
  prerequisites: List[M9BeautyQSearchEvalCombinedComparisonPrerequisite],
  selectedQueries: List[M9BeautyQSearchEvalCombinedComparisonSelectedQuery],
  artifactShape: M9BeautyQSearchEvalCombinedComparisonArtifactShape,
  comparisonDimensions: List[M9BeautyQSearchEvalCombinedComparisonDimension],
  skipReasons: List[M9BeautyQSearchEvalCombinedComparisonSkipReason],
  validationBoundaries: List[String],
) {

  /** This plan never performs or requires a real backend call now or as a planning side effect. */
  def realBackendCallImplemented: Boolean = false

  def realBackendCallRequired: Boolean = false

  def esClientCreated: Boolean = false

  def qdrantClientCreated: Boolean = false

  def productionBeautySearchCalled: Boolean = false

  def routePluginDiHttpInvolved: Boolean = false

  def offlineResourceGatedComparisonOnly: Boolean = true

  def productionActivationApproved: Boolean = false

  def qdrantProductionActivationApproved: Boolean = false

  def defaultBeautySearchEsBacked: Boolean = true

  def qualityGreenClaimed: Boolean = false

  def productionReadinessClaimed: Boolean = false

  def hybridFallbackFusionRerankingTelemetryRouteSwitchImplemented: Boolean = false
}

enum M9BeautyQSearchEvalCombinedComparisonPlanStatus {
  case Planned
  case NotPlanned

  def render: String =
    this match {
      case Planned    => "es_qdrant_offline_comparison_planned"
      case NotPlanned => "es_qdrant_offline_comparison_not_planned"
    }
}

enum M9BeautyQSearchEvalCombinedComparisonPlanReason {
  case CheckpointEligibleForEsQdrantResourceGatedComparison
  case OfflineResourceGatedComparisonOnly
  case CheckpointNotEsQdrantComparisonEligible
  case CheckpointBlockedStaticScorecardNotReady
  case CheckpointNotEligibleNoExplicitResourceConfig
  case CheckpointNotEligibleOperatorApprovalRequired
  case CheckpointEligibleForEsOnlyNotComparison
  case CheckpointEligibleForQdrantOnlyNotComparison
  case ProductionActivationNotApproved
  case QdrantProductionActivationNotApproved
  case DefaultBeautySearchEsBacked
  case NoProductionRouteChange
  case RealBackendCallNotImplemented
  case NoQualityGreenOrProductionReadinessClaim
  case NoHybridFallbackFusionRerankingTelemetryRouteSwitch

  def render: String =
    this match {
      case CheckpointEligibleForEsQdrantResourceGatedComparison =>
        "checkpoint_eligible_for_es_qdrant_resource_gated_comparison"
      case OfflineResourceGatedComparisonOnly              => "offline_resource_gated_comparison_only"
      case CheckpointNotEsQdrantComparisonEligible         => "checkpoint_not_es_qdrant_comparison_eligible"
      case CheckpointBlockedStaticScorecardNotReady        => "checkpoint_blocked_static_scorecard_not_ready"
      case CheckpointNotEligibleNoExplicitResourceConfig   => "checkpoint_not_eligible_no_explicit_resource_config"
      case CheckpointNotEligibleOperatorApprovalRequired   => "checkpoint_not_eligible_operator_approval_required"
      case CheckpointEligibleForEsOnlyNotComparison        => "checkpoint_eligible_for_es_only_not_comparison"
      case CheckpointEligibleForQdrantOnlyNotComparison    => "checkpoint_eligible_for_qdrant_only_not_comparison"
      case ProductionActivationNotApproved                 => "production_activation_not_approved"
      case QdrantProductionActivationNotApproved           => "qdrant_production_activation_not_approved"
      case DefaultBeautySearchEsBacked                     =>
        BeautyQSearchEvaluationMetricNames.SharedProductionPosture.DefaultBeautySearchEsBacked
      case NoProductionRouteChange                         => "no_production_route_change"
      case RealBackendCallNotImplemented                   => "real_backend_call_not_implemented"
      case NoQualityGreenOrProductionReadinessClaim        => "no_quality_green_or_production_readiness_claim"
      case NoHybridFallbackFusionRerankingTelemetryRouteSwitch =>
        "no_hybrid_fallback_fusion_reranking_telemetry_route_switch"
    }
}

final case class M9BeautyQSearchEvalCombinedComparisonPlanResult(
  status: M9BeautyQSearchEvalCombinedComparisonPlanStatus,
  reasons: List[M9BeautyQSearchEvalCombinedComparisonPlanReason],
  plan: Option[M9BeautyQSearchEvalCombinedComparisonPlan],
  checkpointDecision: M9BeautyQSearchEvalRealCallCheckpointDecision,
) {
  def planned: Boolean = status == M9BeautyQSearchEvalCombinedComparisonPlanStatus.Planned

  /** Planning never performs or requires a real ES/Qdrant backend call. */
  def realBackendCallImplemented: Boolean = false

  def realBackendCallRequired: Boolean = false

  def productionActivationApproved: Boolean = false

  def qdrantProductionActivationApproved: Boolean = false

  def defaultBeautySearchEsBacked: Boolean = true

  def qualityGreenClaimed: Boolean = false

  def productionReadinessClaimed: Boolean = false

  def routePluginDiHttpInvolved: Boolean = false
}

object M9BeautyQSearchEvalCombinedSmokeComparisonPlan {
  import M9BeautyQSearchEvalRealCallCheckpointDecision.*
  import M9BeautyQSearchEvalCombinedComparisonPlanReason.*

  /** The checkpoint decision this plan requires before it will produce a combined comparison plan. */
  val RequiredCheckpointDecision: M9BeautyQSearchEvalRealCallCheckpointDecision =
    EligibleForEsQdrantResourceGatedComparison

  val EvalDatasetId: String = "beautyq-m9-es-qdrant-resource-gated-comparison-plan-v1"
  val CatalogSnapshotId: String = "seed-resource-catalog"
  val MarkdownFilename: String = "m9-es-qdrant-resource-gated-comparison-execution-plan-report.md"

  val EsExecutionMode: M9OfflineEvalBackendExecutionMode =
    M9OfflineEvalBackendExecutionMode.EsOnlyOffline
  val QdrantExecutionMode: M9OfflineEvalBackendExecutionMode =
    M9OfflineEvalBackendExecutionMode.QdrantOnlyOffline
  val CandidateSources: List[CandidateSource] = List(CandidateSource.Es, CandidateSource.Qdrant)

  /** Selected query subset: the representative static anchors only. Planning references checked-in
    * static rows; it does not load, index, or query any backend.
    */
  val SelectedQueries: List[M9BeautyQSearchEvalCombinedComparisonSelectedQuery] = {
    val representativeIds = M9BeautyQSearchEvalQueryDatasetStaticRows.RepresentativeQueryIds.toSet
    M9BeautyQSearchEvalQueryDatasetStaticRows.StaticRun.report.rows
      .filter(row => representativeIds.contains(row.queryId))
      .map(row => M9BeautyQSearchEvalCombinedComparisonSelectedQuery(row.queryId, row.queryClass))
  }

  /** Offline comparison dimensions only. These compare candidate id sets between ES and Qdrant; they
    * never imply quality green or production readiness.
    */
  val ComparisonDimensions: List[M9BeautyQSearchEvalCombinedComparisonDimension] =
    List(
      M9BeautyQSearchEvalCombinedComparisonDimension.EsCandidateIds,
      M9BeautyQSearchEvalCombinedComparisonDimension.QdrantCandidateIds,
      M9BeautyQSearchEvalCombinedComparisonDimension.Overlap,
      M9BeautyQSearchEvalCombinedComparisonDimension.Misses,
      M9BeautyQSearchEvalCombinedComparisonDimension.UnexpectedCandidates,
      M9BeautyQSearchEvalCombinedComparisonDimension.MissingLookupRate,
    )

  val Prerequisites: List[M9BeautyQSearchEvalCombinedComparisonPrerequisite] =
    List(
      M9BeautyQSearchEvalCombinedComparisonPrerequisite(
        key = "checkpoint_decision_es_qdrant_comparison_eligible",
        description = "real-call checkpoint decision must be eligible_for_es_qdrant_resource_gated_comparison",
      ),
      M9BeautyQSearchEvalCombinedComparisonPrerequisite(
        key = "es_resource_config_present",
        description = "an explicit ES offline resource config must be supplied for the future comparison",
      ),
      M9BeautyQSearchEvalCombinedComparisonPrerequisite(
        key = "qdrant_resource_config_present",
        description = "an explicit Qdrant offline resource config must be supplied for the future comparison",
      ),
      M9BeautyQSearchEvalCombinedComparisonPrerequisite(
        key = "operator_approval_granted",
        description = "operator approval must be granted for the both-config offline comparison",
      ),
      M9BeautyQSearchEvalCombinedComparisonPrerequisite(
        key = "real_backend_gate_enabled",
        description = s"${M9OfflineEvalRealBackendResourceGate.EnablementEnvVar}=1 must be set for the future comparison run",
      ),
      M9BeautyQSearchEvalCombinedComparisonPrerequisite(
        key = "qdrant_production_activation_not_approved_confirmed",
        description = "future comparison must confirm Qdrant production activation remains not approved",
      ),
    )

  val SkipReasons: List[M9BeautyQSearchEvalCombinedComparisonSkipReason] =
    List(
      M9BeautyQSearchEvalCombinedComparisonSkipReason.EsResourceConfigAbsent,
      M9BeautyQSearchEvalCombinedComparisonSkipReason.QdrantResourceConfigAbsent,
      M9BeautyQSearchEvalCombinedComparisonSkipReason.OperatorApprovalAbsent,
      M9BeautyQSearchEvalCombinedComparisonSkipReason.GateDisabledByDefault,
      M9BeautyQSearchEvalCombinedComparisonSkipReason.RealBackendCallNotImplemented,
    )

  val ValidationBoundaries: List[String] =
    List(
      "plan is offline/resource-gated comparison only; it never runs Elasticsearch or Qdrant",
      "no ES or Qdrant client is created and no production /beauty-search call is made",
      "no route, plugin, DI, or HTTP source is involved",
      "default /beauty-search remains ES-backed; Qdrant opt-in route stays disabled by default",
      "Qdrant production activation remains not approved",
      "comparison dimensions describe candidate id overlap only; no quality green or production readiness is claimed",
      "no hybrid serving, fallback, score fusion, reranking, production telemetry, or route switch is implemented",
    )

  def plan(checkpoint: M9BeautyQSearchEvalRealCallCheckpointResult): M9BeautyQSearchEvalCombinedComparisonPlanResult = {
    val sharedTailReasons =
      List(
        ProductionActivationNotApproved,
        QdrantProductionActivationNotApproved,
        DefaultBeautySearchEsBacked,
        NoProductionRouteChange,
        RealBackendCallNotImplemented,
        NoQualityGreenOrProductionReadinessClaim,
        NoHybridFallbackFusionRerankingTelemetryRouteSwitch,
      )

    checkpoint.decision match {
      case EligibleForEsQdrantResourceGatedComparison =>
        val plannedReasons =
          List(CheckpointEligibleForEsQdrantResourceGatedComparison, OfflineResourceGatedComparisonOnly) ++ sharedTailReasons
        M9BeautyQSearchEvalCombinedComparisonPlanResult(
          status = M9BeautyQSearchEvalCombinedComparisonPlanStatus.Planned,
          reasons = plannedReasons,
          plan = Some(buildPlan),
          checkpointDecision = checkpoint.decision,
        )
      case other =>
        val blockedReasons =
          List(CheckpointNotEsQdrantComparisonEligible, decisionSpecificReason(other)) ++ sharedTailReasons
        M9BeautyQSearchEvalCombinedComparisonPlanResult(
          status = M9BeautyQSearchEvalCombinedComparisonPlanStatus.NotPlanned,
          reasons = blockedReasons,
          plan = None,
          checkpointDecision = other,
        )
    }
  }

  private def decisionSpecificReason(
    decision: M9BeautyQSearchEvalRealCallCheckpointDecision,
  ): M9BeautyQSearchEvalCombinedComparisonPlanReason =
    decision match {
      case BlockedStaticScorecardNotReady             => CheckpointBlockedStaticScorecardNotReady
      case NotEligibleNoExplicitResourceConfig        => CheckpointNotEligibleNoExplicitResourceConfig
      case NotEligibleOperatorApprovalRequired        => CheckpointNotEligibleOperatorApprovalRequired
      case EligibleForEsOnlyResourceGatedSmoke        => CheckpointEligibleForEsOnlyNotComparison
      case EligibleForQdrantOnlyResourceGatedSmoke    => CheckpointEligibleForQdrantOnlyNotComparison
      case EligibleForEsQdrantResourceGatedComparison => CheckpointEligibleForEsQdrantResourceGatedComparison
    }

  private def buildPlan: M9BeautyQSearchEvalCombinedComparisonPlan =
    M9BeautyQSearchEvalCombinedComparisonPlan(
      requiredCheckpointDecision = RequiredCheckpointDecision,
      prerequisites = Prerequisites,
      selectedQueries = SelectedQueries,
      artifactShape = M9BeautyQSearchEvalCombinedComparisonArtifactShape(
        evalDatasetId = EvalDatasetId,
        catalogSnapshotId = CatalogSnapshotId,
        esExecutionMode = EsExecutionMode,
        qdrantExecutionMode = QdrantExecutionMode,
        candidateSources = CandidateSources,
        markdownFilename = MarkdownFilename,
      ),
      comparisonDimensions = ComparisonDimensions,
      skipReasons = SkipReasons,
      validationBoundaries = ValidationBoundaries,
    )

  /** The combined comparison plan for the both-config, operator-approved eligible checkpoint. */
  def planForEsQdrantComparisonEligibleCheckpoint: M9BeautyQSearchEvalCombinedComparisonPlanResult =
    plan(
      M9BeautyQSearchEvalRealCallCheckpoint.decide(
        M9BeautyQSearchEvalRealCallCheckpointInput.Default.copy(
          esResourceConfigPresent = true,
          qdrantResourceConfigPresent = true,
          operatorApprovalGranted = true,
        )
      )
    )
}
