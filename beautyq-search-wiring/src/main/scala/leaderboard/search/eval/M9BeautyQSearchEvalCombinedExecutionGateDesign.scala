package leaderboard.search.eval

/** Pure combined ES/Qdrant future comparison execution gate DESIGN contract.
  *
  * This is planning/design only. It decides whether a future, separately-approved combined ES/Qdrant
  * comparison *execution task* may even be considered yet. It consumes the accepted real-resource
  * prerequisites audit and the accepted combined ES/Qdrant comparison plan contract, and references the
  * accepted saved evidence schema/renderer as the required artifact-capture path. It only reaches
  * `pending_explicit_execution_task` when the combined prerequisites are complete — which requires
  * explicit resource configs for BOTH backends, operator approval, and a ready static
  * scorecard/checkpoint — and excludes ES-only, Qdrant-only, and both-config-without-operator-approval
  * states.
  *
  * It never runs Elasticsearch or Qdrant, never creates an ES or Qdrant client, never calls production
  * `/beauty-search`, and never touches a route, plugin, DI, or HTTP source. It keeps production
  * activation not approved, keeps Qdrant production activation not approved, and keeps default
  * `/beauty-search` ES-backed. Reaching `pending_explicit_execution_task` is not approval to run: a
  * future execution still requires a separate explicit operator-approved task and saved evidence
  * artifact capture via the accepted schema/renderer. No hybrid serving, fallback, score fusion,
  * reranking, production telemetry, or route switch is implied.
  */
enum M9BeautyQSearchEvalCombinedExecutionGateDecision {
  case PendingExplicitExecutionTask
  case BlockedCombinedPrerequisitesIncomplete

  def render: String =
    this match {
      case PendingExplicitExecutionTask           => "pending_explicit_execution_task"
      case BlockedCombinedPrerequisitesIncomplete => "blocked_combined_prerequisites_incomplete"
    }

  /** Whether the gate has reached the pending-explicit-execution-task design state. This is never a
    * real call, execution approval, or production activation.
    */
  def pendingExplicitExecutionTask: Boolean = this == PendingExplicitExecutionTask
}

/** Why the gate decided as it did. These are never positive activation claims; they explain why the
  * combined path is pending design-consideration or which prerequisite/target boundary blocks it.
  */
enum M9BeautyQSearchEvalCombinedExecutionGateReason {
  case CombinedPrerequisitesComplete
  case StaticScorecardOrCheckpointNotReady
  case EsResourceConfigMissing
  case QdrantResourceConfigMissing
  case OperatorApprovalMissing
  case CheckpointTargetsEsOnlyNotCombined
  case CheckpointTargetsQdrantOnlyNotCombined
  case RequiresSeparateExplicitOperatorApprovedTask
  case RequiresSavedEvidenceArtifactCapture
  case ProductionActivationNotApproved
  case QdrantProductionActivationNotApproved
  case DefaultBeautySearchRemainsEsBacked
  case NoRealBackendCallImplemented
  case NoHybridFallbackFusionRerankingTelemetryRouteSwitch

  def render: String =
    this match {
      case CombinedPrerequisitesComplete                 => "combined_prerequisites_complete"
      case StaticScorecardOrCheckpointNotReady           => "static_scorecard_or_checkpoint_not_ready"
      case EsResourceConfigMissing                       => "es_resource_config_missing"
      case QdrantResourceConfigMissing                   => "qdrant_resource_config_missing"
      case OperatorApprovalMissing                       => "operator_approval_missing"
      case CheckpointTargetsEsOnlyNotCombined            => "checkpoint_targets_es_only_not_combined"
      case CheckpointTargetsQdrantOnlyNotCombined        => "checkpoint_targets_qdrant_only_not_combined"
      case RequiresSeparateExplicitOperatorApprovedTask  => "requires_separate_explicit_operator_approved_task"
      case RequiresSavedEvidenceArtifactCapture          => "requires_saved_evidence_artifact_capture"
      case ProductionActivationNotApproved               => "production_activation_not_approved"
      case QdrantProductionActivationNotApproved         => "qdrant_production_activation_not_approved"
      case DefaultBeautySearchRemainsEsBacked            => "default_beauty_search_remains_es_backed"
      case NoRealBackendCallImplemented                  => "no_real_backend_call_implemented"
      case NoHybridFallbackFusionRerankingTelemetryRouteSwitch =>
        "no_hybrid_fallback_fusion_reranking_telemetry_route_switch"
    }
}

final case class M9BeautyQSearchEvalCombinedExecutionGateResult(
  decision: M9BeautyQSearchEvalCombinedExecutionGateDecision,
  reasons: List[M9BeautyQSearchEvalCombinedExecutionGateReason],
  checkpointDecision: M9BeautyQSearchEvalRealCallCheckpointDecision,
  combinedPrerequisitesComplete: Boolean,
  combinedPlanned: Boolean,
  requiredEvidenceSchemaKind: M9BeautyQSearchEvalRealResourceSmokeEvidenceKind,
  requiredEvidenceCaptureArtifact: String,
) {
  def pendingExplicitExecutionTask: Boolean = decision.pendingExplicitExecutionTask

  def blocked: Boolean = !pendingExplicitExecutionTask

  /** This gate is design/planning only. It never performs or requires a real backend call, never
    * creates an ES or Qdrant client, never calls production `/beauty-search`, and never touches a
    * route, plugin, DI, or HTTP source.
    */
  def designOnly: Boolean = true

  def realBackendCallImplemented: Boolean = false

  def realBackendCallRequired: Boolean = false

  def elasticsearchRun: Boolean = false

  def qdrantRun: Boolean = false

  def esClientCreated: Boolean = false

  def qdrantClientCreated: Boolean = false

  def productionBeautySearchCalled: Boolean = false

  def routePluginDiHttpInvolved: Boolean = false

  def productionActivationApproved: Boolean = false

  def qdrantProductionActivationApproved: Boolean = false

  def defaultBeautySearchEsBacked: Boolean = true

  /** Even when the gate reaches pending-explicit-execution-task, a future execution still requires a
    * separate explicit operator-approved task and saved evidence artifact capture.
    */
  def requiresSeparateExplicitOperatorApprovedTask: Boolean = true

  def requiresSavedEvidenceArtifactCapture: Boolean = true

  def qualityGreenClaimed: Boolean = false

  def productionReadinessClaimed: Boolean = false

  def routeActivationClaimed: Boolean = false

  def servingApprovalClaimed: Boolean = false

  def hybridFallbackFusionRerankingTelemetryRouteSwitchImplemented: Boolean = false
}

object M9BeautyQSearchEvalCombinedExecutionGateDesign {
  import M9BeautyQSearchEvalCombinedExecutionGateReason.*
  import M9BeautyQSearchEvalRealCallCheckpointDecision.*

  /** The saved-evidence schema kind a future combined comparison execution must capture into. */
  val RequiredEvidenceSchemaKind: M9BeautyQSearchEvalRealResourceSmokeEvidenceKind =
    M9BeautyQSearchEvalRealResourceSmokeEvidenceKind.EsQdrantComparison

  /** The deterministic checked-in artifact shape a future combined execution must capture evidence into. */
  val RequiredEvidenceCaptureArtifact: String =
    M9BeautyQSearchEvalRealResourceSmokeEvidenceRenderer.DefaultExampleFilename

  /** The checkpoint decision the combined path requires before it may become pending-execution-task. */
  val RequiredCheckpointDecision: M9BeautyQSearchEvalRealCallCheckpointDecision =
    M9BeautyQSearchEvalCombinedSmokeComparisonPlan.RequiredCheckpointDecision

  private val StandingBoundaries: List[M9BeautyQSearchEvalCombinedExecutionGateReason] =
    List(
      RequiresSeparateExplicitOperatorApprovedTask,
      RequiresSavedEvidenceArtifactCapture,
      ProductionActivationNotApproved,
      QdrantProductionActivationNotApproved,
      DefaultBeautySearchRemainsEsBacked,
      NoRealBackendCallImplemented,
      NoHybridFallbackFusionRerankingTelemetryRouteSwitch,
    )

  /** Decide the combined ES/Qdrant execution gate from a real-call checkpoint result.
    *
    * The gate consumes the accepted prerequisites audit (combined entry) and the accepted combined
    * comparison plan contract. It only reaches pending-explicit-execution-task when BOTH the combined
    * prerequisites are complete AND the checkpoint targets the combined comparison (which requires both
    * resource configs and operator approval, and excludes ES-only, Qdrant-only, and
    * both-config-without-operator-approval states).
    */
  def gate(checkpoint: M9BeautyQSearchEvalRealCallCheckpointResult): M9BeautyQSearchEvalCombinedExecutionGateResult = {
    val audit = M9BeautyQSearchEvalRealResourcePrerequisitesAudit.audit(checkpoint)
    val combinedComplete = audit.esQdrantComparison.prerequisitesComplete
    val combinedPlanned = M9BeautyQSearchEvalCombinedSmokeComparisonPlan.plan(checkpoint).planned

    val (decision, leadReasons) =
      if (combinedComplete && combinedPlanned)
        (
          M9BeautyQSearchEvalCombinedExecutionGateDecision.PendingExplicitExecutionTask,
          List(CombinedPrerequisitesComplete),
        )
      else
        (
          M9BeautyQSearchEvalCombinedExecutionGateDecision.BlockedCombinedPrerequisitesIncomplete,
          incompleteReasons(checkpoint),
        )

    M9BeautyQSearchEvalCombinedExecutionGateResult(
      decision = decision,
      reasons = leadReasons ++ StandingBoundaries,
      checkpointDecision = checkpoint.decision,
      combinedPrerequisitesComplete = combinedComplete,
      combinedPlanned = combinedPlanned,
      requiredEvidenceSchemaKind = RequiredEvidenceSchemaKind,
      requiredEvidenceCaptureArtifact = RequiredEvidenceCaptureArtifact,
    )
  }

  private def incompleteReasons(
    checkpoint: M9BeautyQSearchEvalRealCallCheckpointResult
  ): List[M9BeautyQSearchEvalCombinedExecutionGateReason] =
    checkpoint.decision match {
      case BlockedStaticScorecardNotReady =>
        List(StaticScorecardOrCheckpointNotReady)
      case NotEligibleNoExplicitResourceConfig =>
        List(EsResourceConfigMissing, QdrantResourceConfigMissing)
      case EligibleForEsOnlyResourceGatedSmoke =>
        List(CheckpointTargetsEsOnlyNotCombined, QdrantResourceConfigMissing)
      case EligibleForQdrantOnlyResourceGatedSmoke =>
        List(CheckpointTargetsQdrantOnlyNotCombined, EsResourceConfigMissing)
      case NotEligibleOperatorApprovalRequired =>
        List(OperatorApprovalMissing)
      case EligibleForEsQdrantResourceGatedComparison =>
        // Defensive: this branch is the pending case and is not reached as a block.
        List(OperatorApprovalMissing)
    }

  /** Gate for the accepted default/no-config checkpoint: blocked (no resource config). */
  val DefaultResult: M9BeautyQSearchEvalCombinedExecutionGateResult =
    gate(M9BeautyQSearchEvalRealCallCheckpoint.DefaultResult)

  /** Gate for an ES-only-config checkpoint: blocked (combined requires both configs and operator
    * approval).
    */
  def gateForEsOnlyConfig: M9BeautyQSearchEvalCombinedExecutionGateResult =
    gate(
      M9BeautyQSearchEvalRealCallCheckpoint.decide(
        M9BeautyQSearchEvalRealCallCheckpointInput.Default.copy(esResourceConfigPresent = true)
      )
    )

  /** Gate for a Qdrant-only-config checkpoint: blocked (combined requires both configs and operator
    * approval).
    */
  def gateForQdrantOnlyConfig: M9BeautyQSearchEvalCombinedExecutionGateResult =
    gate(
      M9BeautyQSearchEvalRealCallCheckpoint.decide(
        M9BeautyQSearchEvalRealCallCheckpointInput.Default.copy(qdrantResourceConfigPresent = true)
      )
    )

  /** Gate for a both-config checkpoint WITHOUT operator approval: blocked (operator approval missing). */
  def gateForBothConfigNoOperatorApproval: M9BeautyQSearchEvalCombinedExecutionGateResult =
    gate(
      M9BeautyQSearchEvalRealCallCheckpoint.decide(
        M9BeautyQSearchEvalRealCallCheckpointInput.Default.copy(
          esResourceConfigPresent = true,
          qdrantResourceConfigPresent = true,
        )
      )
    )

  /** Gate for the both-config, operator-approved (combined) checkpoint: the only state that reaches
    * pending-explicit-execution-task.
    */
  def gateForCombinedConfig: M9BeautyQSearchEvalCombinedExecutionGateResult =
    gate(
      M9BeautyQSearchEvalRealCallCheckpoint.decide(
        M9BeautyQSearchEvalRealCallCheckpointInput.Default.copy(
          esResourceConfigPresent = true,
          qdrantResourceConfigPresent = true,
          operatorApprovalGranted = true,
        )
      )
    )
}
