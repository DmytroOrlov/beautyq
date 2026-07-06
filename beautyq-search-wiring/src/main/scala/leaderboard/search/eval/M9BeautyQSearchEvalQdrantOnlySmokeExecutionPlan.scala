package leaderboard.search.eval

/** Pure Qdrant-only resource-gated smoke execution PLAN contract.
  *
  * This is planning-only. It consumes the accepted real-call checkpoint decision and decides whether
  * the eval state is allowed to *plan* a future offline, resource-gated Qdrant-only smoke. It only
  * produces a plan when the checkpoint decision is
  * `eligible_for_qdrant_only_resource_gated_smoke`.
  *
  * It never runs Qdrant, never creates a Qdrant client, never calls production `/beauty-search`, and
  * never touches a route, plugin, DI, or HTTP source. It is offline/resource-gated only. It preserves
  * that Qdrant production activation is not approved, that default `/beauty-search` remains ES-backed,
  * and that no hybrid, fallback, fusion, reranking, production telemetry, or route switch is
  * implemented.
  */
final case class M9BeautyQSearchEvalQdrantOnlySmokePrerequisite(
  key: String,
  description: String,
)

final case class M9BeautyQSearchEvalQdrantOnlySmokeSelectedQuery(
  queryId: String,
  queryClass: QueryClass,
)

final case class M9BeautyQSearchEvalQdrantOnlySmokeArtifactShape(
  evalDatasetId: String,
  catalogSnapshotId: String,
  executionMode: M9OfflineEvalBackendExecutionMode,
  candidateSource: CandidateSource,
  servingMode: ServingMode,
  markdownFilename: String,
)

enum M9BeautyQSearchEvalQdrantOnlySmokeSkipReason {
  case ResourceConfigAbsent
  case GateDisabledByDefault
  case RealBackendCallNotImplemented

  def render: String =
    this match {
      case ResourceConfigAbsent          => "qdrant_resource_config_absent_skip"
      case GateDisabledByDefault         => "real_backend_gate_disabled_by_default_skip"
      case RealBackendCallNotImplemented => "real_backend_call_not_implemented_skip"
    }
}

final case class M9BeautyQSearchEvalQdrantOnlySmokePlan(
  requiredCheckpointDecision: M9BeautyQSearchEvalRealCallCheckpointDecision,
  prerequisites: List[M9BeautyQSearchEvalQdrantOnlySmokePrerequisite],
  selectedQueries: List[M9BeautyQSearchEvalQdrantOnlySmokeSelectedQuery],
  artifactShape: M9BeautyQSearchEvalQdrantOnlySmokeArtifactShape,
  skipReasons: List[M9BeautyQSearchEvalQdrantOnlySmokeSkipReason],
  validationBoundaries: List[String],
) {

  /** This plan never performs or requires a real backend call now or as a planning side effect. */
  def realBackendCallImplemented: Boolean = false

  def realBackendCallRequired: Boolean = false

  def qdrantClientCreated: Boolean = false

  def productionBeautySearchCalled: Boolean = false

  def routePluginDiHttpInvolved: Boolean = false

  def offlineResourceGatedSmokeOnly: Boolean = true

  def productionActivationApproved: Boolean = false

  def qdrantProductionActivationApproved: Boolean = false

  def defaultBeautySearchEsBacked: Boolean = true

  def hybridFallbackFusionRerankingTelemetryRouteSwitchImplemented: Boolean = false
}

enum M9BeautyQSearchEvalQdrantOnlySmokePlanStatus {
  case Planned
  case NotPlanned

  def render: String =
    this match {
      case Planned    => "qdrant_only_offline_smoke_planned"
      case NotPlanned => "qdrant_only_offline_smoke_not_planned"
    }
}

enum M9BeautyQSearchEvalQdrantOnlySmokePlanReason {
  case CheckpointEligibleForQdrantOnlyResourceGatedSmoke
  case OfflineResourceGatedSmokeOnly
  case CheckpointNotQdrantOnlyEligible
  case CheckpointBlockedStaticScorecardNotReady
  case CheckpointNotEligibleNoExplicitResourceConfig
  case CheckpointNotEligibleOperatorApprovalRequired
  case CheckpointEligibleForEsOnlyNotQdrantOnly
  case CheckpointEligibleForEsQdrantComparisonNotQdrantOnly
  case ProductionActivationNotApproved
  case QdrantProductionActivationNotApproved
  case DefaultBeautySearchEsBacked
  case NoProductionRouteChange
  case RealBackendCallNotImplemented
  case NoHybridFallbackFusionRerankingTelemetryRouteSwitch

  def render: String =
    this match {
      case CheckpointEligibleForQdrantOnlyResourceGatedSmoke =>
        "checkpoint_eligible_for_qdrant_only_resource_gated_smoke"
      case OfflineResourceGatedSmokeOnly                     => "offline_resource_gated_smoke_only"
      case CheckpointNotQdrantOnlyEligible                   => "checkpoint_not_qdrant_only_eligible"
      case CheckpointBlockedStaticScorecardNotReady          => "checkpoint_blocked_static_scorecard_not_ready"
      case CheckpointNotEligibleNoExplicitResourceConfig     => "checkpoint_not_eligible_no_explicit_resource_config"
      case CheckpointNotEligibleOperatorApprovalRequired     => "checkpoint_not_eligible_operator_approval_required"
      case CheckpointEligibleForEsOnlyNotQdrantOnly          => "checkpoint_eligible_for_es_only_not_qdrant_only"
      case CheckpointEligibleForEsQdrantComparisonNotQdrantOnly =>
        "checkpoint_eligible_for_es_qdrant_comparison_not_qdrant_only"
      case ProductionActivationNotApproved                   => "production_activation_not_approved"
      case QdrantProductionActivationNotApproved             => "qdrant_production_activation_not_approved"
      case DefaultBeautySearchEsBacked                       =>
        BeautyQSearchEvaluationMetricNames.SharedProductionPosture.DefaultBeautySearchEsBacked
      case NoProductionRouteChange                           => "no_production_route_change"
      case RealBackendCallNotImplemented                     => "real_backend_call_not_implemented"
      case NoHybridFallbackFusionRerankingTelemetryRouteSwitch =>
        "no_hybrid_fallback_fusion_reranking_telemetry_route_switch"
    }
}

final case class M9BeautyQSearchEvalQdrantOnlySmokePlanResult(
  status: M9BeautyQSearchEvalQdrantOnlySmokePlanStatus,
  reasons: List[M9BeautyQSearchEvalQdrantOnlySmokePlanReason],
  plan: Option[M9BeautyQSearchEvalQdrantOnlySmokePlan],
  checkpointDecision: M9BeautyQSearchEvalRealCallCheckpointDecision,
) {
  def planned: Boolean = status == M9BeautyQSearchEvalQdrantOnlySmokePlanStatus.Planned

  /** Planning never performs or requires a real ES/Qdrant backend call. */
  def realBackendCallImplemented: Boolean = false

  def realBackendCallRequired: Boolean = false

  def productionActivationApproved: Boolean = false

  def qdrantProductionActivationApproved: Boolean = false

  def defaultBeautySearchEsBacked: Boolean = true

  def routePluginDiHttpInvolved: Boolean = false
}

object M9BeautyQSearchEvalQdrantOnlySmokeExecutionPlan {
  import M9BeautyQSearchEvalRealCallCheckpointDecision.*
  import M9BeautyQSearchEvalQdrantOnlySmokePlanReason.*

  /** The checkpoint decision this plan requires before it will produce a Qdrant-only offline plan. */
  val RequiredCheckpointDecision: M9BeautyQSearchEvalRealCallCheckpointDecision =
    EligibleForQdrantOnlyResourceGatedSmoke

  val EvalDatasetId: String = "beautyq-m9-qdrant-only-resource-gated-smoke-plan-v1"
  val CatalogSnapshotId: String = "seed-resource-catalog"
  val MarkdownFilename: String = "m9-qdrant-only-resource-gated-smoke-execution-plan-report.md"

  val QdrantExecutionMode: M9OfflineEvalBackendExecutionMode =
    M9OfflineEvalBackendExecutionMode.QdrantOnlyOffline
  val QdrantCandidateSource: CandidateSource = CandidateSource.Qdrant
  val QdrantServingMode: ServingMode = ServingMode.QdrantOnly

  /** Selected query subset: the representative static anchors only. Planning references checked-in
    * static rows; it does not load, index, or query any backend.
    */
  val SelectedQueries: List[M9BeautyQSearchEvalQdrantOnlySmokeSelectedQuery] = {
    val representativeIds = M9BeautyQSearchEvalQueryDatasetStaticRows.RepresentativeQueryIds.toSet
    M9BeautyQSearchEvalQueryDatasetStaticRows.StaticRun.report.rows
      .filter(row => representativeIds.contains(row.queryId))
      .map(row => M9BeautyQSearchEvalQdrantOnlySmokeSelectedQuery(row.queryId, row.queryClass))
  }

  val Prerequisites: List[M9BeautyQSearchEvalQdrantOnlySmokePrerequisite] =
    List(
      M9BeautyQSearchEvalQdrantOnlySmokePrerequisite(
        key = "checkpoint_decision_qdrant_only_eligible",
        description = "real-call checkpoint decision must be eligible_for_qdrant_only_resource_gated_smoke",
      ),
      M9BeautyQSearchEvalQdrantOnlySmokePrerequisite(
        key = "qdrant_resource_config_present",
        description = "an explicit Qdrant offline resource config must be supplied for the future smoke",
      ),
      M9BeautyQSearchEvalQdrantOnlySmokePrerequisite(
        key = "real_backend_gate_enabled",
        description = s"${M9OfflineEvalRealBackendResourceGate.EnablementEnvVar}=1 must be set for the future smoke run",
      ),
      M9BeautyQSearchEvalQdrantOnlySmokePrerequisite(
        key = "qdrant_production_activation_not_approved_confirmed",
        description = "future smoke must confirm Qdrant production activation remains not approved",
      ),
    )

  val SkipReasons: List[M9BeautyQSearchEvalQdrantOnlySmokeSkipReason] =
    List(
      M9BeautyQSearchEvalQdrantOnlySmokeSkipReason.ResourceConfigAbsent,
      M9BeautyQSearchEvalQdrantOnlySmokeSkipReason.GateDisabledByDefault,
      M9BeautyQSearchEvalQdrantOnlySmokeSkipReason.RealBackendCallNotImplemented,
    )

  val ValidationBoundaries: List[String] =
    List(
      "plan is offline/resource-gated only; it never runs Qdrant",
      "no Qdrant client is created and no production /beauty-search call is made",
      "no route, plugin, DI, or HTTP source is involved",
      "default /beauty-search remains ES-backed; Qdrant opt-in route stays disabled by default",
      "Qdrant production activation remains not approved",
      "no hybrid, fallback, fusion, reranking, production telemetry, or route switch is implemented",
    )

  def plan(checkpoint: M9BeautyQSearchEvalRealCallCheckpointResult): M9BeautyQSearchEvalQdrantOnlySmokePlanResult = {
    val sharedTailReasons =
      List(
        ProductionActivationNotApproved,
        QdrantProductionActivationNotApproved,
        DefaultBeautySearchEsBacked,
        NoProductionRouteChange,
        RealBackendCallNotImplemented,
        NoHybridFallbackFusionRerankingTelemetryRouteSwitch,
      )

    checkpoint.decision match {
      case EligibleForQdrantOnlyResourceGatedSmoke =>
        val plannedReasons =
          List(CheckpointEligibleForQdrantOnlyResourceGatedSmoke, OfflineResourceGatedSmokeOnly) ++ sharedTailReasons
        M9BeautyQSearchEvalQdrantOnlySmokePlanResult(
          status = M9BeautyQSearchEvalQdrantOnlySmokePlanStatus.Planned,
          reasons = plannedReasons,
          plan = Some(buildPlan),
          checkpointDecision = checkpoint.decision,
        )
      case other =>
        val blockedReasons =
          List(CheckpointNotQdrantOnlyEligible, decisionSpecificReason(other)) ++ sharedTailReasons
        M9BeautyQSearchEvalQdrantOnlySmokePlanResult(
          status = M9BeautyQSearchEvalQdrantOnlySmokePlanStatus.NotPlanned,
          reasons = blockedReasons,
          plan = None,
          checkpointDecision = other,
        )
    }
  }

  private def decisionSpecificReason(
    decision: M9BeautyQSearchEvalRealCallCheckpointDecision,
  ): M9BeautyQSearchEvalQdrantOnlySmokePlanReason =
    decision match {
      case BlockedStaticScorecardNotReady             => CheckpointBlockedStaticScorecardNotReady
      case NotEligibleNoExplicitResourceConfig        => CheckpointNotEligibleNoExplicitResourceConfig
      case NotEligibleOperatorApprovalRequired        => CheckpointNotEligibleOperatorApprovalRequired
      case EligibleForEsOnlyResourceGatedSmoke        => CheckpointEligibleForEsOnlyNotQdrantOnly
      case EligibleForEsQdrantResourceGatedComparison => CheckpointEligibleForEsQdrantComparisonNotQdrantOnly
      case EligibleForQdrantOnlyResourceGatedSmoke    => CheckpointEligibleForQdrantOnlyResourceGatedSmoke
    }

  private def buildPlan: M9BeautyQSearchEvalQdrantOnlySmokePlan =
    M9BeautyQSearchEvalQdrantOnlySmokePlan(
      requiredCheckpointDecision = RequiredCheckpointDecision,
      prerequisites = Prerequisites,
      selectedQueries = SelectedQueries,
      artifactShape = M9BeautyQSearchEvalQdrantOnlySmokeArtifactShape(
        evalDatasetId = EvalDatasetId,
        catalogSnapshotId = CatalogSnapshotId,
        executionMode = QdrantExecutionMode,
        candidateSource = QdrantCandidateSource,
        servingMode = QdrantServingMode,
        markdownFilename = MarkdownFilename,
      ),
      skipReasons = SkipReasons,
      validationBoundaries = ValidationBoundaries,
    )

  /** The Qdrant-only plan for a Qdrant-only eligible checkpoint (explicit Qdrant resource config). */
  def planForQdrantOnlyEligibleCheckpoint: M9BeautyQSearchEvalQdrantOnlySmokePlanResult =
    plan(
      M9BeautyQSearchEvalRealCallCheckpoint.decide(
        M9BeautyQSearchEvalRealCallCheckpointInput.Default.copy(qdrantResourceConfigPresent = true)
      )
    )
}
