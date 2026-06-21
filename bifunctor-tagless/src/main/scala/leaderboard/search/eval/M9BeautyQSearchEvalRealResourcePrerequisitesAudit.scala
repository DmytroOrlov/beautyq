package leaderboard.search.eval

/** Pure real-resource execution prerequisites AUDIT contract.
  *
  * This is planning-only. It consumes the accepted real-call checkpoint decision and the accepted
  * ES-only smoke, Qdrant-only smoke, and combined ES/Qdrant comparison PLAN contracts, and audits
  * whether the prerequisites for each future offline, resource-gated *execution* are complete. It does
  * not execute anything: it never runs Elasticsearch or Qdrant, never creates an ES or Qdrant client,
  * never calls production `/beauty-search`, and never touches a route, plugin, DI, or HTTP source.
  *
  * For each backend the audit refuses to mark future-execution prerequisites complete unless that
  * backend's explicit resource config is present (and the static scorecard/checkpoint is ready). The
  * combined ES/Qdrant comparison additionally requires operator approval. The audit preserves that
  * production activation is not approved, that Qdrant production activation is not approved, that
  * default `/beauty-search` remains ES-backed, and that no hybrid serving, fallback, score fusion,
  * reranking, production telemetry, or route switch is implemented. It may describe expected future
  * evidence artifacts and the minimum validation evidence fields a future run would have to record,
  * but it never claims quality green or production readiness.
  */
enum M9BeautyQSearchEvalRealResourcePrerequisiteTarget {
  case EsOnlySmokeExecution
  case QdrantOnlySmokeExecution
  case EsQdrantComparisonExecution

  def render: String =
    this match {
      case EsOnlySmokeExecution        => "es_only_smoke_execution"
      case QdrantOnlySmokeExecution    => "qdrant_only_smoke_execution"
      case EsQdrantComparisonExecution => "es_qdrant_comparison_execution"
    }
}

enum M9BeautyQSearchEvalRealResourcePrerequisiteStatus {
  case PrerequisitesComplete
  case PrerequisitesBlocked

  def render: String =
    this match {
      case PrerequisitesComplete => "real_resource_prerequisites_complete"
      case PrerequisitesBlocked  => "real_resource_prerequisites_blocked"
    }

  def complete: Boolean = this == PrerequisitesComplete
}

/** Block/skip reasons for a future real-resource execution. These are never positive activation
  * claims; they explain why a future execution's prerequisites are blocked or which standing boundary
  * the audit preserves.
  */
enum M9BeautyQSearchEvalRealResourcePrerequisiteReason {
  case StaticScorecardOrCheckpointNotReady
  case EsResourceConfigMissing
  case QdrantResourceConfigMissing
  case OperatorApprovalMissing
  case RealBackendGateDisabledByDefault
  case ProductionActivationBoundaryNotApproved

  def render: String =
    this match {
      case StaticScorecardOrCheckpointNotReady   => "static_scorecard_or_checkpoint_not_ready_block"
      case EsResourceConfigMissing               => "es_resource_config_missing_block"
      case QdrantResourceConfigMissing           => "qdrant_resource_config_missing_block"
      case OperatorApprovalMissing               => "operator_approval_missing_block"
      case RealBackendGateDisabledByDefault      => "real_backend_gate_disabled_by_default_standing_boundary"
      case ProductionActivationBoundaryNotApproved =>
        "production_activation_boundary_not_approved_standing_boundary"
    }
}

/** Per-target prerequisites audit entry. `blockReasons` are the prerequisites that are not yet
  * satisfied (empty when complete). `standingBoundaries` are always present and record the boundaries
  * the audit preserves regardless of completeness.
  */
final case class M9BeautyQSearchEvalRealResourcePrerequisiteAuditEntry(
  target: M9BeautyQSearchEvalRealResourcePrerequisiteTarget,
  status: M9BeautyQSearchEvalRealResourcePrerequisiteStatus,
  requiredCheckpointDecision: M9BeautyQSearchEvalRealCallCheckpointDecision,
  blockReasons: List[M9BeautyQSearchEvalRealResourcePrerequisiteReason],
  standingBoundaries: List[M9BeautyQSearchEvalRealResourcePrerequisiteReason],
  expectedEvidenceArtifacts: List[String],
  minimumValidationEvidenceFields: List[String],
) {
  def prerequisitesComplete: Boolean = status.complete

  /** The audit never performs or requires a real backend call as a side effect. */
  def realBackendCallImplemented: Boolean = false

  def realBackendCallRequired: Boolean = false

  def qualityGreenClaimed: Boolean = false

  def productionReadinessClaimed: Boolean = false
}

final case class M9BeautyQSearchEvalRealResourcePrerequisitesAuditResult(
  checkpointDecision: M9BeautyQSearchEvalRealCallCheckpointDecision,
  staticScorecardReady: Boolean,
  esResourceConfigPresent: Boolean,
  qdrantResourceConfigPresent: Boolean,
  operatorApprovalGranted: Boolean,
  esOnly: M9BeautyQSearchEvalRealResourcePrerequisiteAuditEntry,
  qdrantOnly: M9BeautyQSearchEvalRealResourcePrerequisiteAuditEntry,
  esQdrantComparison: M9BeautyQSearchEvalRealResourcePrerequisiteAuditEntry,
) {
  def entries: List[M9BeautyQSearchEvalRealResourcePrerequisiteAuditEntry] =
    List(esOnly, qdrantOnly, esQdrantComparison)

  /** This audit is planning-only. It never performs or requires a real backend call, never creates an
    * ES or Qdrant client, never calls production `/beauty-search`, and never touches a route, plugin,
    * DI, or HTTP source.
    */
  def planningOnly: Boolean = true

  def realBackendCallImplemented: Boolean = false

  def realBackendCallRequired: Boolean = false

  def esClientCreated: Boolean = false

  def qdrantClientCreated: Boolean = false

  def productionBeautySearchCalled: Boolean = false

  def routePluginDiHttpInvolved: Boolean = false

  def productionActivationApproved: Boolean = false

  def qdrantProductionActivationApproved: Boolean = false

  def defaultBeautySearchEsBacked: Boolean = true

  def qualityGreenClaimed: Boolean = false

  def productionReadinessClaimed: Boolean = false

  def hybridFallbackFusionRerankingTelemetryRouteSwitchImplemented: Boolean = false
}

object M9BeautyQSearchEvalRealResourcePrerequisitesAudit {
  import M9BeautyQSearchEvalRealResourcePrerequisiteReason.*

  /** Standing boundaries recorded on every entry: the real backend gate is disabled by default and
    * production activation is not approved. These never become positive activation claims.
    */
  val StandingBoundaries: List[M9BeautyQSearchEvalRealResourcePrerequisiteReason] =
    List(RealBackendGateDisabledByDefault, ProductionActivationBoundaryNotApproved)

  /** Minimum validation evidence fields a future resource-gated run must record. These reference the
    * existing real-backend spike evidence shape; recording them is never a quality-green or
    * production-readiness claim.
    */
  val MinimumValidationEvidenceFields: List[String] =
    List(
      "gate_status",
      "resource_config_present",
      "production_activation_not_approved_confirmed",
      "real_backend_call_implemented",
      "candidate_source",
      "execution_mode",
    )

  def audit(
    checkpoint: M9BeautyQSearchEvalRealCallCheckpointResult
  ): M9BeautyQSearchEvalRealResourcePrerequisitesAuditResult = {
    val ready = checkpoint.staticScorecardReady
    val esConfig = checkpoint.esResourceConfigPresent
    val qdrantConfig = checkpoint.qdrantResourceConfigPresent
    val operatorApproval = checkpoint.operatorApprovalGranted

    val esOnly = entry(
      target = M9BeautyQSearchEvalRealResourcePrerequisiteTarget.EsOnlySmokeExecution,
      requiredCheckpointDecision = M9BeautyQSearchEvalEsOnlySmokeExecutionPlan.RequiredCheckpointDecision,
      blockReasons = List(
        Option.when(!ready)(StaticScorecardOrCheckpointNotReady),
        Option.when(!esConfig)(EsResourceConfigMissing),
      ).flatten,
      expectedEvidenceArtifacts = List(M9BeautyQSearchEvalEsOnlySmokeExecutionPlan.MarkdownFilename),
    )

    val qdrantOnly = entry(
      target = M9BeautyQSearchEvalRealResourcePrerequisiteTarget.QdrantOnlySmokeExecution,
      requiredCheckpointDecision = M9BeautyQSearchEvalQdrantOnlySmokeExecutionPlan.RequiredCheckpointDecision,
      blockReasons = List(
        Option.when(!ready)(StaticScorecardOrCheckpointNotReady),
        Option.when(!qdrantConfig)(QdrantResourceConfigMissing),
      ).flatten,
      expectedEvidenceArtifacts = List(M9BeautyQSearchEvalQdrantOnlySmokeExecutionPlan.MarkdownFilename),
    )

    val esQdrantComparison = entry(
      target = M9BeautyQSearchEvalRealResourcePrerequisiteTarget.EsQdrantComparisonExecution,
      requiredCheckpointDecision = M9BeautyQSearchEvalCombinedSmokeComparisonPlan.RequiredCheckpointDecision,
      blockReasons = List(
        Option.when(!ready)(StaticScorecardOrCheckpointNotReady),
        Option.when(!esConfig)(EsResourceConfigMissing),
        Option.when(!qdrantConfig)(QdrantResourceConfigMissing),
        Option.when(!operatorApproval)(OperatorApprovalMissing),
      ).flatten,
      expectedEvidenceArtifacts = List(M9BeautyQSearchEvalCombinedSmokeComparisonPlan.MarkdownFilename),
    )

    M9BeautyQSearchEvalRealResourcePrerequisitesAuditResult(
      checkpointDecision = checkpoint.decision,
      staticScorecardReady = ready,
      esResourceConfigPresent = esConfig,
      qdrantResourceConfigPresent = qdrantConfig,
      operatorApprovalGranted = operatorApproval,
      esOnly = esOnly,
      qdrantOnly = qdrantOnly,
      esQdrantComparison = esQdrantComparison,
    )
  }

  private def entry(
    target: M9BeautyQSearchEvalRealResourcePrerequisiteTarget,
    requiredCheckpointDecision: M9BeautyQSearchEvalRealCallCheckpointDecision,
    blockReasons: List[M9BeautyQSearchEvalRealResourcePrerequisiteReason],
    expectedEvidenceArtifacts: List[String],
  ): M9BeautyQSearchEvalRealResourcePrerequisiteAuditEntry = {
    val status =
      if (blockReasons.isEmpty) M9BeautyQSearchEvalRealResourcePrerequisiteStatus.PrerequisitesComplete
      else M9BeautyQSearchEvalRealResourcePrerequisiteStatus.PrerequisitesBlocked
    M9BeautyQSearchEvalRealResourcePrerequisiteAuditEntry(
      target = target,
      status = status,
      requiredCheckpointDecision = requiredCheckpointDecision,
      blockReasons = blockReasons,
      standingBoundaries = StandingBoundaries,
      expectedEvidenceArtifacts = expectedEvidenceArtifacts,
      minimumValidationEvidenceFields = MinimumValidationEvidenceFields,
    )
  }

  /** Audit for the accepted default checkpoint: static scorecard ready, no explicit resource config,
    * no operator approval. All future executions are blocked.
    */
  val DefaultResult: M9BeautyQSearchEvalRealResourcePrerequisitesAuditResult =
    audit(M9BeautyQSearchEvalRealCallCheckpoint.DefaultResult)

  /** Audit for an ES-config-present checkpoint: only ES-only future execution prerequisites complete. */
  def auditForEsOnlyConfig: M9BeautyQSearchEvalRealResourcePrerequisitesAuditResult =
    audit(
      M9BeautyQSearchEvalRealCallCheckpoint.decide(
        M9BeautyQSearchEvalRealCallCheckpointInput.Default.copy(esResourceConfigPresent = true)
      )
    )

  /** Audit for a Qdrant-config-present checkpoint: only Qdrant-only future execution prerequisites
    * complete.
    */
  def auditForQdrantOnlyConfig: M9BeautyQSearchEvalRealResourcePrerequisitesAuditResult =
    audit(
      M9BeautyQSearchEvalRealCallCheckpoint.decide(
        M9BeautyQSearchEvalRealCallCheckpointInput.Default.copy(qdrantResourceConfigPresent = true)
      )
    )

  /** Audit for the both-config, operator-approved checkpoint: combined comparison prerequisites
    * complete.
    */
  def auditForEsQdrantComparison: M9BeautyQSearchEvalRealResourcePrerequisitesAuditResult =
    audit(
      M9BeautyQSearchEvalRealCallCheckpoint.decide(
        M9BeautyQSearchEvalRealCallCheckpointInput.Default.copy(
          esResourceConfigPresent = true,
          qdrantResourceConfigPresent = true,
          operatorApprovalGranted = true,
        )
      )
    )
}
