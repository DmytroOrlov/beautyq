package leaderboard.search.eval

/** Pure ES-only resource-gated smoke execution PLAN contract.
  *
  * This is planning-only. It consumes the accepted real-call checkpoint decision and decides whether
  * the eval state is allowed to *plan* a future offline, resource-gated ES-only smoke. It only
  * produces a plan when the checkpoint decision is
  * `eligible_for_es_only_resource_gated_smoke`.
  *
  * It never runs Elasticsearch, never creates an ES client, never calls production `/beauty-search`,
  * and never touches a route, plugin, DI, or HTTP source. It is offline/resource-gated only. It
  * preserves that Qdrant production activation is not approved and that no hybrid, fallback, fusion,
  * reranking, production telemetry, or route switch is implemented.
  */
final case class M9BeautyQSearchEvalEsOnlySmokePrerequisite(
  key: String,
  description: String,
)

final case class M9BeautyQSearchEvalEsOnlySmokeSelectedQuery(
  queryId: String,
  queryClass: QueryClass,
)

final case class M9BeautyQSearchEvalEsOnlySmokeArtifactShape(
  evalDatasetId: String,
  catalogSnapshotId: String,
  executionMode: M9OfflineEvalBackendExecutionMode,
  candidateSource: CandidateSource,
  servingMode: ServingMode,
  markdownFilename: String,
)

enum M9BeautyQSearchEvalEsOnlySmokeSkipReason {
  case ResourceConfigAbsent
  case GateDisabledByDefault
  case RealBackendCallNotImplemented

  def render: String =
    this match {
      case ResourceConfigAbsent          => "es_resource_config_absent_skip"
      case GateDisabledByDefault         => "real_backend_gate_disabled_by_default_skip"
      case RealBackendCallNotImplemented => "real_backend_call_not_implemented_skip"
    }
}

final case class M9BeautyQSearchEvalEsOnlySmokePlan(
  requiredCheckpointDecision: M9BeautyQSearchEvalRealCallCheckpointDecision,
  prerequisites: List[M9BeautyQSearchEvalEsOnlySmokePrerequisite],
  selectedQueries: List[M9BeautyQSearchEvalEsOnlySmokeSelectedQuery],
  artifactShape: M9BeautyQSearchEvalEsOnlySmokeArtifactShape,
  skipReasons: List[M9BeautyQSearchEvalEsOnlySmokeSkipReason],
  validationBoundaries: List[String],
) {

  /** This plan never performs or requires a real backend call now or as a planning side effect. */
  def realBackendCallImplemented: Boolean = false

  def realBackendCallRequired: Boolean = false

  def esClientCreated: Boolean = false

  def productionBeautySearchCalled: Boolean = false

  def routePluginDiHttpInvolved: Boolean = false

  def offlineResourceGatedSmokeOnly: Boolean = true

  def productionActivationApproved: Boolean = false

  def qdrantProductionActivationApproved: Boolean = false

  def hybridFallbackFusionRerankingTelemetryRouteSwitchImplemented: Boolean = false
}

enum M9BeautyQSearchEvalEsOnlySmokePlanStatus {
  case Planned
  case NotPlanned

  def render: String =
    this match {
      case Planned    => "es_only_offline_smoke_planned"
      case NotPlanned => "es_only_offline_smoke_not_planned"
    }
}

enum M9BeautyQSearchEvalEsOnlySmokePlanReason {
  case CheckpointEligibleForEsOnlyResourceGatedSmoke
  case OfflineResourceGatedSmokeOnly
  case CheckpointNotEsOnlyEligible
  case CheckpointBlockedStaticScorecardNotReady
  case CheckpointNotEligibleNoExplicitResourceConfig
  case CheckpointNotEligibleOperatorApprovalRequired
  case CheckpointEligibleForQdrantOnlyNotEsOnly
  case CheckpointEligibleForEsQdrantComparisonNotEsOnly
  case ProductionActivationNotApproved
  case QdrantProductionActivationNotApproved
  case NoProductionRouteChange
  case RealBackendCallNotImplemented
  case NoHybridFallbackFusionRerankingTelemetryRouteSwitch

  def render: String =
    this match {
      case CheckpointEligibleForEsOnlyResourceGatedSmoke   => "checkpoint_eligible_for_es_only_resource_gated_smoke"
      case OfflineResourceGatedSmokeOnly                   => "offline_resource_gated_smoke_only"
      case CheckpointNotEsOnlyEligible                     => "checkpoint_not_es_only_eligible"
      case CheckpointBlockedStaticScorecardNotReady        => "checkpoint_blocked_static_scorecard_not_ready"
      case CheckpointNotEligibleNoExplicitResourceConfig   => "checkpoint_not_eligible_no_explicit_resource_config"
      case CheckpointNotEligibleOperatorApprovalRequired   => "checkpoint_not_eligible_operator_approval_required"
      case CheckpointEligibleForQdrantOnlyNotEsOnly        => "checkpoint_eligible_for_qdrant_only_not_es_only"
      case CheckpointEligibleForEsQdrantComparisonNotEsOnly => "checkpoint_eligible_for_es_qdrant_comparison_not_es_only"
      case ProductionActivationNotApproved                 => "production_activation_not_approved"
      case QdrantProductionActivationNotApproved           => "qdrant_production_activation_not_approved"
      case NoProductionRouteChange                         => "no_production_route_change"
      case RealBackendCallNotImplemented                   => "real_backend_call_not_implemented"
      case NoHybridFallbackFusionRerankingTelemetryRouteSwitch =>
        "no_hybrid_fallback_fusion_reranking_telemetry_route_switch"
    }
}

final case class M9BeautyQSearchEvalEsOnlySmokePlanResult(
  status: M9BeautyQSearchEvalEsOnlySmokePlanStatus,
  reasons: List[M9BeautyQSearchEvalEsOnlySmokePlanReason],
  plan: Option[M9BeautyQSearchEvalEsOnlySmokePlan],
  checkpointDecision: M9BeautyQSearchEvalRealCallCheckpointDecision,
) {
  def planned: Boolean = status == M9BeautyQSearchEvalEsOnlySmokePlanStatus.Planned

  /** Planning never performs or requires a real ES/Qdrant backend call. */
  def realBackendCallImplemented: Boolean = false

  def realBackendCallRequired: Boolean = false

  def productionActivationApproved: Boolean = false

  def qdrantProductionActivationApproved: Boolean = false

  def routePluginDiHttpInvolved: Boolean = false
}

object M9BeautyQSearchEvalEsOnlySmokeExecutionPlan {
  import M9BeautyQSearchEvalRealCallCheckpointDecision.*
  import M9BeautyQSearchEvalEsOnlySmokePlanReason.*

  /** The checkpoint decision this plan requires before it will produce an ES-only offline plan. */
  val RequiredCheckpointDecision: M9BeautyQSearchEvalRealCallCheckpointDecision =
    EligibleForEsOnlyResourceGatedSmoke

  val EvalDatasetId: String = "beautyq-m9-es-only-resource-gated-smoke-plan-v1"
  val CatalogSnapshotId: String = "seed-resource-catalog"
  val MarkdownFilename: String = "m9-es-only-resource-gated-smoke-execution-plan-report.md"

  val EsExecutionMode: M9OfflineEvalBackendExecutionMode =
    M9OfflineEvalBackendExecutionMode.EsOnlyOffline
  val EsCandidateSource: CandidateSource = CandidateSource.Es
  val EsServingMode: ServingMode = ServingMode.EsOnly

  /** Selected query subset: the representative static anchors only. Planning references checked-in
    * static rows; it does not load, index, or query any backend.
    */
  val SelectedQueries: List[M9BeautyQSearchEvalEsOnlySmokeSelectedQuery] = {
    val representativeIds = M9BeautyQSearchEvalQueryDatasetStaticRows.RepresentativeQueryIds.toSet
    M9BeautyQSearchEvalQueryDatasetStaticRows.StaticRun.report.rows
      .filter(row => representativeIds.contains(row.queryId))
      .map(row => M9BeautyQSearchEvalEsOnlySmokeSelectedQuery(row.queryId, row.queryClass))
  }

  val Prerequisites: List[M9BeautyQSearchEvalEsOnlySmokePrerequisite] =
    List(
      M9BeautyQSearchEvalEsOnlySmokePrerequisite(
        key = "checkpoint_decision_es_only_eligible",
        description = "real-call checkpoint decision must be eligible_for_es_only_resource_gated_smoke",
      ),
      M9BeautyQSearchEvalEsOnlySmokePrerequisite(
        key = "es_resource_config_present",
        description = "an explicit ES offline resource config must be supplied for the future smoke",
      ),
      M9BeautyQSearchEvalEsOnlySmokePrerequisite(
        key = "real_backend_gate_enabled",
        description = s"${M9OfflineEvalRealBackendResourceGate.EnablementEnvVar}=1 must be set for the future smoke run",
      ),
      M9BeautyQSearchEvalEsOnlySmokePrerequisite(
        key = "production_activation_not_approved_confirmed",
        description = "future smoke must confirm production activation remains not approved",
      ),
    )

  val SkipReasons: List[M9BeautyQSearchEvalEsOnlySmokeSkipReason] =
    List(
      M9BeautyQSearchEvalEsOnlySmokeSkipReason.ResourceConfigAbsent,
      M9BeautyQSearchEvalEsOnlySmokeSkipReason.GateDisabledByDefault,
      M9BeautyQSearchEvalEsOnlySmokeSkipReason.RealBackendCallNotImplemented,
    )

  val ValidationBoundaries: List[String] =
    List(
      "plan is offline/resource-gated only; it never runs Elasticsearch",
      "no ES client is created and no production /beauty-search call is made",
      "no route, plugin, DI, or HTTP source is involved",
      "default /beauty-search remains ES-backed; Qdrant opt-in route stays disabled by default",
      "Qdrant production activation remains not approved",
      "no hybrid, fallback, fusion, reranking, production telemetry, or route switch is implemented",
    )

  def plan(checkpoint: M9BeautyQSearchEvalRealCallCheckpointResult): M9BeautyQSearchEvalEsOnlySmokePlanResult = {
    val sharedTailReasons =
      List(
        ProductionActivationNotApproved,
        QdrantProductionActivationNotApproved,
        NoProductionRouteChange,
        RealBackendCallNotImplemented,
        NoHybridFallbackFusionRerankingTelemetryRouteSwitch,
      )

    checkpoint.decision match {
      case EligibleForEsOnlyResourceGatedSmoke =>
        val plannedReasons =
          List(CheckpointEligibleForEsOnlyResourceGatedSmoke, OfflineResourceGatedSmokeOnly) ++ sharedTailReasons
        M9BeautyQSearchEvalEsOnlySmokePlanResult(
          status = M9BeautyQSearchEvalEsOnlySmokePlanStatus.Planned,
          reasons = plannedReasons,
          plan = Some(buildPlan),
          checkpointDecision = checkpoint.decision,
        )
      case other =>
        val blockedReasons =
          List(CheckpointNotEsOnlyEligible, decisionSpecificReason(other)) ++ sharedTailReasons
        M9BeautyQSearchEvalEsOnlySmokePlanResult(
          status = M9BeautyQSearchEvalEsOnlySmokePlanStatus.NotPlanned,
          reasons = blockedReasons,
          plan = None,
          checkpointDecision = other,
        )
    }
  }

  private def decisionSpecificReason(
    decision: M9BeautyQSearchEvalRealCallCheckpointDecision,
  ): M9BeautyQSearchEvalEsOnlySmokePlanReason =
    decision match {
      case BlockedStaticScorecardNotReady             => CheckpointBlockedStaticScorecardNotReady
      case NotEligibleNoExplicitResourceConfig        => CheckpointNotEligibleNoExplicitResourceConfig
      case NotEligibleOperatorApprovalRequired        => CheckpointNotEligibleOperatorApprovalRequired
      case EligibleForQdrantOnlyResourceGatedSmoke    => CheckpointEligibleForQdrantOnlyNotEsOnly
      case EligibleForEsQdrantResourceGatedComparison => CheckpointEligibleForEsQdrantComparisonNotEsOnly
      case EligibleForEsOnlyResourceGatedSmoke        => CheckpointEligibleForEsOnlyResourceGatedSmoke
    }

  private def buildPlan: M9BeautyQSearchEvalEsOnlySmokePlan =
    M9BeautyQSearchEvalEsOnlySmokePlan(
      requiredCheckpointDecision = RequiredCheckpointDecision,
      prerequisites = Prerequisites,
      selectedQueries = SelectedQueries,
      artifactShape = M9BeautyQSearchEvalEsOnlySmokeArtifactShape(
        evalDatasetId = EvalDatasetId,
        catalogSnapshotId = CatalogSnapshotId,
        executionMode = EsExecutionMode,
        candidateSource = EsCandidateSource,
        servingMode = EsServingMode,
        markdownFilename = MarkdownFilename,
      ),
      skipReasons = SkipReasons,
      validationBoundaries = ValidationBoundaries,
    )

  /** The ES-only plan for the accepted default checkpoint (ES-only eligible). */
  def planForEsOnlyEligibleCheckpoint: M9BeautyQSearchEvalEsOnlySmokePlanResult =
    plan(
      M9BeautyQSearchEvalRealCallCheckpoint.decide(
        M9BeautyQSearchEvalRealCallCheckpointInput.Default.copy(esResourceConfigPresent = true)
      )
    )
}
