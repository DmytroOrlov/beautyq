package leaderboard.search.eval

/** Pure Qdrant-only future execution gate DESIGN contract.
  *
  * This is planning/design only. It decides whether a future, separately-approved Qdrant-only resource
  * smoke *execution task* may even be considered yet. It consumes the accepted real-resource
  * prerequisites audit and the accepted Qdrant-only smoke plan contract, and references the accepted
  * saved evidence schema/renderer as the required artifact-capture path. It only reaches
  * `pending_explicit_execution_task` when the Qdrant-only prerequisites are complete (which requires the
  * Qdrant-only resource config and a ready static scorecard/checkpoint, and excludes ES-only and
  * combined-only states).
  *
  * It never runs Qdrant, never creates a Qdrant client, never calls production `/beauty-search`, and
  * never touches a route, plugin, DI, or HTTP source. It keeps Qdrant production activation not
  * approved, keeps production activation not approved, and keeps default `/beauty-search` ES-backed.
  * Reaching `pending_explicit_execution_task` is not approval to run: a future execution still requires
  * a separate explicit operator-approved task and saved evidence artifact capture via the accepted
  * schema/renderer.
  */
enum M9BeautyQSearchEvalQdrantOnlyExecutionGateDecision {
  case PendingExplicitExecutionTask
  case BlockedQdrantOnlyPrerequisitesIncomplete
  case BlockedNotQdrantOnlyTarget

  def render: String =
    this match {
      case PendingExplicitExecutionTask              => "pending_explicit_execution_task"
      case BlockedQdrantOnlyPrerequisitesIncomplete  => "blocked_qdrant_only_prerequisites_incomplete"
      case BlockedNotQdrantOnlyTarget                => "blocked_not_qdrant_only_target"
    }

  /** Whether the gate has reached the pending-explicit-execution-task design state. This is never a
    * real call, execution approval, or production activation.
    */
  def pendingExplicitExecutionTask: Boolean = this == PendingExplicitExecutionTask
}

/** Why the gate decided as it did. These are never positive activation claims; they explain why the
  * Qdrant-only path is pending design-consideration or which prerequisite/target boundary blocks it.
  */
enum M9BeautyQSearchEvalQdrantOnlyExecutionGateReason {
  case QdrantOnlyPrerequisitesComplete
  case StaticScorecardOrCheckpointNotReady
  case QdrantResourceConfigMissing
  case CheckpointTargetsEsOnlyNotQdrantOnly
  case CheckpointTargetsCombinedComparisonNotQdrantOnly
  case RequiresSeparateExplicitOperatorApprovedTask
  case RequiresSavedEvidenceArtifactCapture
  case ProductionActivationNotApproved
  case QdrantProductionActivationNotApproved
  case DefaultBeautySearchRemainsEsBacked
  case NoRealBackendCallImplemented

  def render: String =
    this match {
      case QdrantOnlyPrerequisitesComplete               => "qdrant_only_prerequisites_complete"
      case StaticScorecardOrCheckpointNotReady           => "static_scorecard_or_checkpoint_not_ready"
      case QdrantResourceConfigMissing                   => "qdrant_resource_config_missing"
      case CheckpointTargetsEsOnlyNotQdrantOnly          => "checkpoint_targets_es_only_not_qdrant_only"
      case CheckpointTargetsCombinedComparisonNotQdrantOnly =>
        "checkpoint_targets_combined_comparison_not_qdrant_only"
      case RequiresSeparateExplicitOperatorApprovedTask  => "requires_separate_explicit_operator_approved_task"
      case RequiresSavedEvidenceArtifactCapture          => "requires_saved_evidence_artifact_capture"
      case ProductionActivationNotApproved               => "production_activation_not_approved"
      case QdrantProductionActivationNotApproved         => "qdrant_production_activation_not_approved"
      case DefaultBeautySearchRemainsEsBacked            => "default_beauty_search_remains_es_backed"
      case NoRealBackendCallImplemented                  => "no_real_backend_call_implemented"
    }
}

final case class M9BeautyQSearchEvalQdrantOnlyExecutionGateResult(
  decision: M9BeautyQSearchEvalQdrantOnlyExecutionGateDecision,
  reasons: List[M9BeautyQSearchEvalQdrantOnlyExecutionGateReason],
  checkpointDecision: M9BeautyQSearchEvalRealCallCheckpointDecision,
  qdrantOnlyPrerequisitesComplete: Boolean,
  qdrantOnlyPlanned: Boolean,
  requiredEvidenceSchemaKind: M9BeautyQSearchEvalRealResourceSmokeEvidenceKind,
  requiredEvidenceCaptureArtifact: String,
) {
  def pendingExplicitExecutionTask: Boolean = decision.pendingExplicitExecutionTask

  def blocked: Boolean = !pendingExplicitExecutionTask

  /** This gate is design/planning only. It never performs or requires a real backend call, never
    * creates a Qdrant client, never calls production `/beauty-search`, and never touches a route,
    * plugin, DI, or HTTP source.
    */
  def designOnly: Boolean = true

  def realBackendCallImplemented: Boolean = false

  def realBackendCallRequired: Boolean = false

  def qdrantRun: Boolean = false

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

object M9BeautyQSearchEvalQdrantOnlyExecutionGateDesign {
  import M9BeautyQSearchEvalQdrantOnlyExecutionGateReason.*
  import M9BeautyQSearchEvalRealCallCheckpointDecision.*

  /** The saved-evidence schema kind a future Qdrant-only execution must capture into. */
  val RequiredEvidenceSchemaKind: M9BeautyQSearchEvalRealResourceSmokeEvidenceKind =
    M9BeautyQSearchEvalRealResourceSmokeEvidenceKind.QdrantOnlySmoke

  /** The deterministic checked-in artifact shape a future Qdrant-only execution must capture evidence
    * into.
    */
  val RequiredEvidenceCaptureArtifact: String =
    M9BeautyQSearchEvalRealResourceSmokeEvidenceRenderer.DefaultExampleFilename

  /** The checkpoint decision the Qdrant-only path requires before it may become pending-execution-task. */
  val RequiredCheckpointDecision: M9BeautyQSearchEvalRealCallCheckpointDecision =
    M9BeautyQSearchEvalQdrantOnlySmokeExecutionPlan.RequiredCheckpointDecision

  private val StandingBoundaries: List[M9BeautyQSearchEvalQdrantOnlyExecutionGateReason] =
    List(
      RequiresSeparateExplicitOperatorApprovedTask,
      RequiresSavedEvidenceArtifactCapture,
      ProductionActivationNotApproved,
      QdrantProductionActivationNotApproved,
      DefaultBeautySearchRemainsEsBacked,
      NoRealBackendCallImplemented,
    )

  /** Decide the Qdrant-only execution gate from a real-call checkpoint result.
    *
    * The gate consumes the accepted prerequisites audit (Qdrant-only entry) and the accepted
    * Qdrant-only plan contract. It only reaches pending-explicit-execution-task when BOTH the
    * Qdrant-only prerequisites are complete AND the checkpoint targets Qdrant-only (which excludes
    * ES-only and combined states).
    */
  def gate(checkpoint: M9BeautyQSearchEvalRealCallCheckpointResult): M9BeautyQSearchEvalQdrantOnlyExecutionGateResult = {
    val audit = M9BeautyQSearchEvalRealResourcePrerequisitesAudit.audit(checkpoint)
    val qdrantOnlyComplete = audit.qdrantOnly.prerequisitesComplete
    val qdrantOnlyPlanned = M9BeautyQSearchEvalQdrantOnlySmokeExecutionPlan.plan(checkpoint).planned

    val (decision, leadReasons) =
      (qdrantOnlyComplete, qdrantOnlyPlanned) match {
        case (true, true) =>
          (
            M9BeautyQSearchEvalQdrantOnlyExecutionGateDecision.PendingExplicitExecutionTask,
            List(QdrantOnlyPrerequisitesComplete),
          )
        case (true, false) =>
          // Qdrant-only prerequisites are complete but the checkpoint targets a combined state (both ES
          // and Qdrant config present): this is the combined-only path and is not a Qdrant-only run.
          (
            M9BeautyQSearchEvalQdrantOnlyExecutionGateDecision.BlockedNotQdrantOnlyTarget,
            List(CheckpointTargetsCombinedComparisonNotQdrantOnly),
          )
        case (false, _) =>
          (
            M9BeautyQSearchEvalQdrantOnlyExecutionGateDecision.BlockedQdrantOnlyPrerequisitesIncomplete,
            incompleteReasons(checkpoint),
          )
      }

    M9BeautyQSearchEvalQdrantOnlyExecutionGateResult(
      decision = decision,
      reasons = leadReasons ++ StandingBoundaries,
      checkpointDecision = checkpoint.decision,
      qdrantOnlyPrerequisitesComplete = qdrantOnlyComplete,
      qdrantOnlyPlanned = qdrantOnlyPlanned,
      requiredEvidenceSchemaKind = RequiredEvidenceSchemaKind,
      requiredEvidenceCaptureArtifact = RequiredEvidenceCaptureArtifact,
    )
  }

  private def incompleteReasons(
    checkpoint: M9BeautyQSearchEvalRealCallCheckpointResult
  ): List[M9BeautyQSearchEvalQdrantOnlyExecutionGateReason] =
    checkpoint.decision match {
      case BlockedStaticScorecardNotReady =>
        List(StaticScorecardOrCheckpointNotReady)
      case EligibleForEsOnlyResourceGatedSmoke =>
        List(CheckpointTargetsEsOnlyNotQdrantOnly, QdrantResourceConfigMissing)
      case _ =>
        List(QdrantResourceConfigMissing)
    }

  /** Gate for the accepted default/no-config checkpoint: blocked (no Qdrant resource config). */
  val DefaultResult: M9BeautyQSearchEvalQdrantOnlyExecutionGateResult =
    gate(M9BeautyQSearchEvalRealCallCheckpoint.DefaultResult)

  /** Gate for a Qdrant-only-config checkpoint: the only state that reaches
    * pending-explicit-execution-task.
    */
  def gateForQdrantOnlyConfig: M9BeautyQSearchEvalQdrantOnlyExecutionGateResult =
    gate(
      M9BeautyQSearchEvalRealCallCheckpoint.decide(
        M9BeautyQSearchEvalRealCallCheckpointInput.Default.copy(qdrantResourceConfigPresent = true)
      )
    )

  /** Gate for an ES-only-config checkpoint: blocked (Qdrant-only path is not the target). */
  def gateForEsOnlyConfig: M9BeautyQSearchEvalQdrantOnlyExecutionGateResult =
    gate(
      M9BeautyQSearchEvalRealCallCheckpoint.decide(
        M9BeautyQSearchEvalRealCallCheckpointInput.Default.copy(esResourceConfigPresent = true)
      )
    )

  /** Gate for the both-config, operator-approved (combined) checkpoint: blocked as not a Qdrant-only
    * target.
    */
  def gateForCombinedConfig: M9BeautyQSearchEvalQdrantOnlyExecutionGateResult =
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
