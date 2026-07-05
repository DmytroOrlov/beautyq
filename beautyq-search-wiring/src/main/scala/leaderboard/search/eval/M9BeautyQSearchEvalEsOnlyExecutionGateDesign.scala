package leaderboard.search.eval

/** Pure ES-only future execution gate DESIGN contract.
  *
  * This is planning/design only. It decides whether a future, separately-approved ES-only resource
  * smoke *execution task* may even be considered yet. It consumes the accepted real-resource
  * prerequisites audit and the accepted ES-only smoke plan contract, and references the accepted saved
  * evidence schema as the required artifact-capture path. It only reaches `pending_explicit_execution_task`
  * when the ES-only prerequisites are complete (which requires the ES-only resource config and a ready
  * static scorecard/checkpoint, and excludes Qdrant-only and combined-only states).
  *
  * It never runs Elasticsearch, never creates an Elasticsearch client, never calls production
  * `/beauty-search`, and never touches a route, plugin, DI, or HTTP source. It keeps production
  * activation not approved, keeps Qdrant production activation not approved, and keeps default
  * `/beauty-search` ES-backed. Reaching `pending_explicit_execution_task` is not approval to run: a
  * future execution still requires a separate explicit operator-approved task and saved evidence
  * artifact capture via the accepted schema/renderer.
  */
enum M9BeautyQSearchEvalEsOnlyExecutionGateDecision {
  case PendingExplicitExecutionTask
  case BlockedEsOnlyPrerequisitesIncomplete
  case BlockedNotEsOnlyTarget

  def render: String =
    this match {
      case PendingExplicitExecutionTask          => "pending_explicit_execution_task"
      case BlockedEsOnlyPrerequisitesIncomplete  => "blocked_es_only_prerequisites_incomplete"
      case BlockedNotEsOnlyTarget                => "blocked_not_es_only_target"
    }

  /** Whether the gate has reached the pending-explicit-execution-task design state. This is never a
    * real call, execution approval, or production activation.
    */
  def pendingExplicitExecutionTask: Boolean = this == PendingExplicitExecutionTask
}

/** Why the gate decided as it did. These are never positive activation claims; they explain why the
  * ES-only path is pending design-consideration or which prerequisite/target boundary blocks it.
  */
enum M9BeautyQSearchEvalEsOnlyExecutionGateReason {
  case EsOnlyPrerequisitesComplete
  case StaticScorecardOrCheckpointNotReady
  case EsResourceConfigMissing
  case CheckpointTargetsQdrantOnlyNotEsOnly
  case CheckpointTargetsCombinedComparisonNotEsOnly
  case RequiresSeparateExplicitOperatorApprovedTask
  case RequiresSavedEvidenceArtifactCapture
  case ProductionActivationNotApproved
  case QdrantProductionActivationNotApproved
  case DefaultBeautySearchRemainsEsBacked
  case NoRealBackendCallImplemented

  def render: String =
    this match {
      case EsOnlyPrerequisitesComplete                 => "es_only_prerequisites_complete"
      case StaticScorecardOrCheckpointNotReady         => "static_scorecard_or_checkpoint_not_ready"
      case EsResourceConfigMissing                     => "es_resource_config_missing"
      case CheckpointTargetsQdrantOnlyNotEsOnly        => "checkpoint_targets_qdrant_only_not_es_only"
      case CheckpointTargetsCombinedComparisonNotEsOnly => "checkpoint_targets_combined_comparison_not_es_only"
      case RequiresSeparateExplicitOperatorApprovedTask => "requires_separate_explicit_operator_approved_task"
      case RequiresSavedEvidenceArtifactCapture        => "requires_saved_evidence_artifact_capture"
      case ProductionActivationNotApproved             => "production_activation_not_approved"
      case QdrantProductionActivationNotApproved       => "qdrant_production_activation_not_approved"
      case DefaultBeautySearchRemainsEsBacked          => "default_beauty_search_remains_es_backed"
      case NoRealBackendCallImplemented                => "no_real_backend_call_implemented"
    }
}

final case class M9BeautyQSearchEvalEsOnlyExecutionGateResult(
  decision: M9BeautyQSearchEvalEsOnlyExecutionGateDecision,
  reasons: List[M9BeautyQSearchEvalEsOnlyExecutionGateReason],
  checkpointDecision: M9BeautyQSearchEvalRealCallCheckpointDecision,
  esOnlyPrerequisitesComplete: Boolean,
  esOnlyPlanned: Boolean,
  requiredEvidenceSchemaKind: M9BeautyQSearchEvalRealResourceSmokeEvidenceKind,
  requiredEvidenceCaptureArtifact: String,
) {
  def pendingExplicitExecutionTask: Boolean = decision.pendingExplicitExecutionTask

  def blocked: Boolean = !pendingExplicitExecutionTask

  /** This gate is design/planning only. It never performs or requires a real backend call, never
    * creates an ES client, never calls production `/beauty-search`, and never touches a route, plugin,
    * DI, or HTTP source.
    */
  def designOnly: Boolean = true

  def realBackendCallImplemented: Boolean = false

  def realBackendCallRequired: Boolean = false

  def elasticsearchRun: Boolean = false

  def esClientCreated: Boolean = false

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

  def hybridFallbackFusionRerankingTelemetryRouteSwitchImplemented: Boolean = false
}

object M9BeautyQSearchEvalEsOnlyExecutionGateDesign {
  import M9BeautyQSearchEvalEsOnlyExecutionGateReason.*
  import M9BeautyQSearchEvalRealCallCheckpointDecision.*

  /** The saved-evidence schema kind a future ES-only execution must capture into. */
  val RequiredEvidenceSchemaKind: M9BeautyQSearchEvalRealResourceSmokeEvidenceKind =
    M9BeautyQSearchEvalRealResourceSmokeEvidenceKind.EsOnlySmoke

  /** The deterministic checked-in artifact shape a future ES-only execution must capture evidence into. */
  val RequiredEvidenceCaptureArtifact: String =
    M9BeautyQSearchEvalRealResourceSmokeEvidenceRenderer.DefaultExampleFilename

  /** The checkpoint decision the ES-only path requires before it may become pending-execution-task. */
  val RequiredCheckpointDecision: M9BeautyQSearchEvalRealCallCheckpointDecision =
    M9BeautyQSearchEvalEsOnlySmokeExecutionPlan.RequiredCheckpointDecision

  /** Standing boundaries appended to every gate result. These never become positive activation claims. */
  private val StandingBoundaries: List[M9BeautyQSearchEvalEsOnlyExecutionGateReason] =
    List(
      RequiresSeparateExplicitOperatorApprovedTask,
      RequiresSavedEvidenceArtifactCapture,
      ProductionActivationNotApproved,
      QdrantProductionActivationNotApproved,
      DefaultBeautySearchRemainsEsBacked,
      NoRealBackendCallImplemented,
    )

  /** Decide the ES-only execution gate from a real-call checkpoint result.
    *
    * The gate consumes the accepted prerequisites audit (ES-only entry) and the accepted ES-only plan
    * contract. It only reaches pending-explicit-execution-task when BOTH the ES-only prerequisites are
    * complete AND the checkpoint targets ES-only (which excludes Qdrant-only and combined states).
    */
  def gate(checkpoint: M9BeautyQSearchEvalRealCallCheckpointResult): M9BeautyQSearchEvalEsOnlyExecutionGateResult = {
    val audit = M9BeautyQSearchEvalRealResourcePrerequisitesAudit.audit(checkpoint)
    val esOnlyComplete = audit.esOnly.prerequisitesComplete
    val esOnlyPlanned = M9BeautyQSearchEvalEsOnlySmokeExecutionPlan.plan(checkpoint).planned

    val (decision, leadReasons) =
      (esOnlyComplete, esOnlyPlanned) match {
        case (true, true) =>
          (
            M9BeautyQSearchEvalEsOnlyExecutionGateDecision.PendingExplicitExecutionTask,
            List(EsOnlyPrerequisitesComplete),
          )
        case (true, false) =>
          // ES-only prerequisites are complete but the checkpoint targets a combined state (both ES and
          // Qdrant config present): this is the combined-only path and is not an ES-only execution.
          (
            M9BeautyQSearchEvalEsOnlyExecutionGateDecision.BlockedNotEsOnlyTarget,
            List(CheckpointTargetsCombinedComparisonNotEsOnly),
          )
        case (false, _) =>
          (
            M9BeautyQSearchEvalEsOnlyExecutionGateDecision.BlockedEsOnlyPrerequisitesIncomplete,
            incompleteReasons(checkpoint),
          )
      }

    M9BeautyQSearchEvalEsOnlyExecutionGateResult(
      decision = decision,
      reasons = leadReasons ++ StandingBoundaries,
      checkpointDecision = checkpoint.decision,
      esOnlyPrerequisitesComplete = esOnlyComplete,
      esOnlyPlanned = esOnlyPlanned,
      requiredEvidenceSchemaKind = RequiredEvidenceSchemaKind,
      requiredEvidenceCaptureArtifact = RequiredEvidenceCaptureArtifact,
    )
  }

  private def incompleteReasons(
    checkpoint: M9BeautyQSearchEvalRealCallCheckpointResult
  ): List[M9BeautyQSearchEvalEsOnlyExecutionGateReason] =
    checkpoint.decision match {
      case BlockedStaticScorecardNotReady =>
        List(StaticScorecardOrCheckpointNotReady)
      case EligibleForQdrantOnlyResourceGatedSmoke =>
        List(CheckpointTargetsQdrantOnlyNotEsOnly, EsResourceConfigMissing)
      case _ =>
        List(EsResourceConfigMissing)
    }

  /** Gate for the accepted default/no-config checkpoint: blocked (no ES resource config). */
  val DefaultResult: M9BeautyQSearchEvalEsOnlyExecutionGateResult =
    gate(M9BeautyQSearchEvalRealCallCheckpoint.DefaultResult)

  /** Gate for an ES-only-config checkpoint: the only state that reaches pending-explicit-execution-task. */
  def gateForEsOnlyConfig: M9BeautyQSearchEvalEsOnlyExecutionGateResult =
    gate(
      M9BeautyQSearchEvalRealCallCheckpoint.decide(
        M9BeautyQSearchEvalRealCallCheckpointInput.Default.copy(esResourceConfigPresent = true)
      )
    )

  /** Gate for a Qdrant-only-config checkpoint: blocked (ES-only path is not the target). */
  def gateForQdrantOnlyConfig: M9BeautyQSearchEvalEsOnlyExecutionGateResult =
    gate(
      M9BeautyQSearchEvalRealCallCheckpoint.decide(
        M9BeautyQSearchEvalRealCallCheckpointInput.Default.copy(qdrantResourceConfigPresent = true)
      )
    )

  /** Gate for the both-config, operator-approved (combined) checkpoint: blocked as not an ES-only
    * target.
    */
  def gateForCombinedConfig: M9BeautyQSearchEvalEsOnlyExecutionGateResult =
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
