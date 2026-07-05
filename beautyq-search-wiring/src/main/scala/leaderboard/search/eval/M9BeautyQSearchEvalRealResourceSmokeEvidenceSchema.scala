package leaderboard.search.eval

/** Pure saved-evidence artifact SCHEMA for future real-resource smoke audit results.
  *
  * This is a saved-evidence shape only. It defines the stable artifact shape a *future* offline,
  * resource-gated ES-only smoke, Qdrant-only smoke, or combined ES/Qdrant comparison run would record
  * its evidence into. It does not execute anything: it never runs Elasticsearch or Qdrant, never
  * creates an ES or Qdrant client, never calls production `/beauty-search`, and never touches a route,
  * plugin, DI, or HTTP source. Real backend call execution is explicitly not part of this schema task.
  *
  * The schema consumes the accepted real-resource prerequisites audit and the accepted plan/checkpoint
  * contracts rather than duplicating production route logic. When a target's audited prerequisites are
  * blocked, the schema renders blocked/skip evidence; when complete it renders pending-execution
  * evidence. There is no success/quality-green/production-readiness state: the schema never claims
  * quality green, production readiness, route activation, or serving approval. It preserves that
  * production activation is not approved, that Qdrant production activation is not approved, that
  * default `/beauty-search` remains ES-backed, and that no hybrid serving, fallback, score fusion,
  * reranking, production telemetry, or route switch is implemented.
  */
enum M9BeautyQSearchEvalRealResourceSmokeEvidenceKind {
  case EsOnlySmoke
  case QdrantOnlySmoke
  case EsQdrantComparison

  def render: String =
    this match {
      case EsOnlySmoke       => "es_only_smoke_saved_evidence"
      case QdrantOnlySmoke   => "qdrant_only_smoke_saved_evidence"
      case EsQdrantComparison => "es_qdrant_comparison_saved_evidence"
    }
}

/** Evidence state. There is no success state: blocked prerequisites render blocked/skip evidence and
  * complete prerequisites render pending-execution evidence (a future run has not happened).
  */
enum M9BeautyQSearchEvalRealResourceSmokeEvidenceState {
  case PrerequisitesBlockedSkip
  case PrerequisitesCompletePendingExecution

  def render: String =
    this match {
      case PrerequisitesBlockedSkip              => "prerequisites_blocked_skip_evidence"
      case PrerequisitesCompletePendingExecution => "prerequisites_complete_pending_execution_evidence"
    }

  def blockedOrSkipped: Boolean = this == PrerequisitesBlockedSkip
}

/** A selected query anchor recorded in the saved evidence. References checked-in static rows; it does
  * not load, index, or query any backend.
  */
final case class M9BeautyQSearchEvalRealResourceSmokeEvidenceQueryAnchor(
  queryId: String,
  queryClass: QueryClass,
)

/** Comparison dimensions of the future combined ES/Qdrant evidence. All collections default to empty
  * and the rate to `None`: this is a shape for a future run to fill, never observed backend data.
  */
final case class M9BeautyQSearchEvalRealResourceComparisonEvidenceShape(
  esCandidateIds: List[String],
  qdrantCandidateIds: List[String],
  overlapCandidateIds: List[String],
  missCandidateIds: List[String],
  unexpectedCandidateIds: List[String],
  missingLookupRate: Option[BigDecimal],
  comparisonNotes: List[String],
)

object M9BeautyQSearchEvalRealResourceComparisonEvidenceShape {

  /** Empty pending shape: no real ES/Qdrant candidate evidence has been recorded. */
  val Pending: M9BeautyQSearchEvalRealResourceComparisonEvidenceShape =
    M9BeautyQSearchEvalRealResourceComparisonEvidenceShape(
      esCandidateIds = Nil,
      qdrantCandidateIds = Nil,
      overlapCandidateIds = Nil,
      missCandidateIds = Nil,
      unexpectedCandidateIds = Nil,
      missingLookupRate = None,
      comparisonNotes = Nil,
    )
}

/** Shared saved-evidence shape. Every evidence kind records the same boundary fields proving the
  * schema is saved-evidence-only and that no real call, production activation, or route change is part
  * of it.
  */
sealed trait M9BeautyQSearchEvalRealResourceSmokeEvidence {
  def kind: M9BeautyQSearchEvalRealResourceSmokeEvidenceKind
  def state: M9BeautyQSearchEvalRealResourceSmokeEvidenceState
  def prerequisitesStatus: M9BeautyQSearchEvalRealResourcePrerequisiteStatus
  def requiredCheckpointDecision: M9BeautyQSearchEvalRealCallCheckpointDecision
  def backendModes: List[M9OfflineEvalBackendExecutionMode]
  def artifactKind: String
  def selectedQueryAnchors: List[M9BeautyQSearchEvalRealResourceSmokeEvidenceQueryAnchor]
  def expectedEvidenceFilesOrSections: List[String]
  def skipOrBlockReasons: List[M9BeautyQSearchEvalRealResourcePrerequisiteReason]
  def standingBoundaries: List[M9BeautyQSearchEvalRealResourcePrerequisiteReason]
  def validationSummary: List[String]

  def prerequisitesComplete: Boolean = prerequisitesStatus.complete
  def blockedOrSkipped: Boolean = state.blockedOrSkipped

  // Boundary fields. This schema is saved-evidence-only; it never executes or requires a real call.
  def savedEvidenceSchemaOnly: Boolean = true
  def realBackendCallExecutionPartOfThisSchemaTask: Boolean = false
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
  def routeActivationClaimed: Boolean = false
  def servingApprovalClaimed: Boolean = false
  def hybridFallbackFusionRerankingTelemetryRouteSwitchImplemented: Boolean = false
}

/** ES-only smoke saved evidence. Distinct shape from Qdrant-only: fixed ES candidate source and
  * ES-only serving mode.
  */
final case class M9BeautyQSearchEvalRealResourceEsOnlySmokeEvidence(
  state: M9BeautyQSearchEvalRealResourceSmokeEvidenceState,
  prerequisitesStatus: M9BeautyQSearchEvalRealResourcePrerequisiteStatus,
  requiredCheckpointDecision: M9BeautyQSearchEvalRealCallCheckpointDecision,
  candidateSource: CandidateSource,
  servingMode: ServingMode,
  selectedQueryAnchors: List[M9BeautyQSearchEvalRealResourceSmokeEvidenceQueryAnchor],
  expectedEvidenceFilesOrSections: List[String],
  skipOrBlockReasons: List[M9BeautyQSearchEvalRealResourcePrerequisiteReason],
  standingBoundaries: List[M9BeautyQSearchEvalRealResourcePrerequisiteReason],
  validationSummary: List[String],
) extends M9BeautyQSearchEvalRealResourceSmokeEvidence {
  def kind: M9BeautyQSearchEvalRealResourceSmokeEvidenceKind =
    M9BeautyQSearchEvalRealResourceSmokeEvidenceKind.EsOnlySmoke
  def artifactKind: String = kind.render
  def backendModes: List[M9OfflineEvalBackendExecutionMode] =
    List(M9OfflineEvalBackendExecutionMode.EsOnlyOffline)
}

/** Qdrant-only smoke saved evidence. Distinct shape from ES-only: fixed Qdrant candidate source and
  * Qdrant-only serving mode.
  */
final case class M9BeautyQSearchEvalRealResourceQdrantOnlySmokeEvidence(
  state: M9BeautyQSearchEvalRealResourceSmokeEvidenceState,
  prerequisitesStatus: M9BeautyQSearchEvalRealResourcePrerequisiteStatus,
  requiredCheckpointDecision: M9BeautyQSearchEvalRealCallCheckpointDecision,
  candidateSource: CandidateSource,
  servingMode: ServingMode,
  selectedQueryAnchors: List[M9BeautyQSearchEvalRealResourceSmokeEvidenceQueryAnchor],
  expectedEvidenceFilesOrSections: List[String],
  skipOrBlockReasons: List[M9BeautyQSearchEvalRealResourcePrerequisiteReason],
  standingBoundaries: List[M9BeautyQSearchEvalRealResourcePrerequisiteReason],
  validationSummary: List[String],
) extends M9BeautyQSearchEvalRealResourceSmokeEvidence {
  def kind: M9BeautyQSearchEvalRealResourceSmokeEvidenceKind =
    M9BeautyQSearchEvalRealResourceSmokeEvidenceKind.QdrantOnlySmoke
  def artifactKind: String = kind.render
  def backendModes: List[M9OfflineEvalBackendExecutionMode] =
    List(M9OfflineEvalBackendExecutionMode.QdrantOnlyOffline)
}

/** Combined ES/Qdrant comparison saved evidence. Carries the offline comparison shape (candidate id
  * sets, overlap, misses, unexpected candidates, missing lookup rate, notes) for a future run to fill.
  */
final case class M9BeautyQSearchEvalRealResourceComparisonSmokeEvidence(
  state: M9BeautyQSearchEvalRealResourceSmokeEvidenceState,
  prerequisitesStatus: M9BeautyQSearchEvalRealResourcePrerequisiteStatus,
  requiredCheckpointDecision: M9BeautyQSearchEvalRealCallCheckpointDecision,
  esCandidateSource: CandidateSource,
  qdrantCandidateSource: CandidateSource,
  esExecutionMode: M9OfflineEvalBackendExecutionMode,
  qdrantExecutionMode: M9OfflineEvalBackendExecutionMode,
  selectedQueryAnchors: List[M9BeautyQSearchEvalRealResourceSmokeEvidenceQueryAnchor],
  expectedEvidenceFilesOrSections: List[String],
  comparisonShape: M9BeautyQSearchEvalRealResourceComparisonEvidenceShape,
  skipOrBlockReasons: List[M9BeautyQSearchEvalRealResourcePrerequisiteReason],
  standingBoundaries: List[M9BeautyQSearchEvalRealResourcePrerequisiteReason],
  validationSummary: List[String],
) extends M9BeautyQSearchEvalRealResourceSmokeEvidence {
  def kind: M9BeautyQSearchEvalRealResourceSmokeEvidenceKind =
    M9BeautyQSearchEvalRealResourceSmokeEvidenceKind.EsQdrantComparison
  def artifactKind: String = kind.render
  def backendModes: List[M9OfflineEvalBackendExecutionMode] =
    List(esExecutionMode, qdrantExecutionMode)
}

/** A saved-evidence artifact bundling the three evidence shapes derived from one prerequisites audit
  * result.
  */
final case class M9BeautyQSearchEvalRealResourceSmokeEvidenceArtifact(
  checkpointDecision: M9BeautyQSearchEvalRealCallCheckpointDecision,
  staticScorecardReady: Boolean,
  esOnly: M9BeautyQSearchEvalRealResourceEsOnlySmokeEvidence,
  qdrantOnly: M9BeautyQSearchEvalRealResourceQdrantOnlySmokeEvidence,
  esQdrantComparison: M9BeautyQSearchEvalRealResourceComparisonSmokeEvidence,
) {
  def evidences: List[M9BeautyQSearchEvalRealResourceSmokeEvidence] =
    List(esOnly, qdrantOnly, esQdrantComparison)

  def savedEvidenceSchemaOnly: Boolean = true
  def realBackendCallExecutionPartOfThisSchemaTask: Boolean = false
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
  def routeActivationClaimed: Boolean = false
  def servingApprovalClaimed: Boolean = false
  def hybridFallbackFusionRerankingTelemetryRouteSwitchImplemented: Boolean = false
}

object M9BeautyQSearchEvalRealResourceSmokeEvidenceSchema {

  /** Expected evidence sections every saved-evidence artifact must record. These are field names a
    * future run would fill; recording them is never a quality-green or production-readiness claim.
    */
  val ExpectedEvidenceSections: List[String] =
    List(
      "evidence_kind",
      "evidence_state",
      "prerequisites_status",
      "selected_query_anchors",
      "backend_mode",
      "skip_or_block_reasons",
      "standing_boundaries",
      "validation_summary",
    )

  /** Validation summary lines recorded on every saved-evidence shape. */
  val ValidationSummary: List[String] =
    List(
      "schema is saved-evidence-only; it never runs Elasticsearch or Qdrant",
      "no ES or Qdrant client is created and no production /beauty-search call is made",
      "no route, plugin, DI, or HTTP source is involved",
      "real backend call execution is not part of this schema task",
      "default /beauty-search remains ES-backed; Qdrant opt-in route stays disabled by default",
      "production activation and Qdrant production activation remain not approved",
      "blocked prerequisites render blocked/skip evidence, never success evidence",
      "no quality green, production readiness, route activation, or serving approval is claimed",
      "no hybrid serving, fallback, score fusion, reranking, production telemetry, or route switch is implemented",
    )

  /** Build a saved-evidence artifact from a prerequisites audit result. The audit is the source of
    * truth for each target's prerequisites status, block reasons, standing boundaries, and required
    * checkpoint decision; the plan contracts are the source of truth for selected query anchors.
    */
  def fromAudit(
    audit: M9BeautyQSearchEvalRealResourcePrerequisitesAuditResult
  ): M9BeautyQSearchEvalRealResourceSmokeEvidenceArtifact =
    M9BeautyQSearchEvalRealResourceSmokeEvidenceArtifact(
      checkpointDecision = audit.checkpointDecision,
      staticScorecardReady = audit.staticScorecardReady,
      esOnly = esOnlyEvidence(audit.esOnly),
      qdrantOnly = qdrantOnlyEvidence(audit.qdrantOnly),
      esQdrantComparison = comparisonEvidence(audit.esQdrantComparison),
    )

  /** Saved evidence for the accepted default audit: all targets blocked. */
  val DefaultArtifact: M9BeautyQSearchEvalRealResourceSmokeEvidenceArtifact =
    fromAudit(M9BeautyQSearchEvalRealResourcePrerequisitesAudit.DefaultResult)

  private def stateOf(
    entry: M9BeautyQSearchEvalRealResourcePrerequisiteAuditEntry
  ): M9BeautyQSearchEvalRealResourceSmokeEvidenceState =
    if (entry.prerequisitesComplete)
      M9BeautyQSearchEvalRealResourceSmokeEvidenceState.PrerequisitesCompletePendingExecution
    else
      M9BeautyQSearchEvalRealResourceSmokeEvidenceState.PrerequisitesBlockedSkip

  private def anchors(
    selected: List[(String, QueryClass)]
  ): List[M9BeautyQSearchEvalRealResourceSmokeEvidenceQueryAnchor] =
    selected.map { case (queryId, queryClass) =>
      M9BeautyQSearchEvalRealResourceSmokeEvidenceQueryAnchor(queryId, queryClass)
    }

  private def sections(markdownFilenames: List[String]): List[String] =
    markdownFilenames ++ ExpectedEvidenceSections

  private def esOnlyEvidence(
    entry: M9BeautyQSearchEvalRealResourcePrerequisiteAuditEntry
  ): M9BeautyQSearchEvalRealResourceEsOnlySmokeEvidence =
    M9BeautyQSearchEvalRealResourceEsOnlySmokeEvidence(
      state = stateOf(entry),
      prerequisitesStatus = entry.status,
      requiredCheckpointDecision = entry.requiredCheckpointDecision,
      candidateSource = M9BeautyQSearchEvalEsOnlySmokeExecutionPlan.EsCandidateSource,
      servingMode = M9BeautyQSearchEvalEsOnlySmokeExecutionPlan.EsServingMode,
      selectedQueryAnchors = anchors(
        M9BeautyQSearchEvalEsOnlySmokeExecutionPlan.SelectedQueries.map(q => (q.queryId, q.queryClass))
      ),
      expectedEvidenceFilesOrSections = sections(entry.expectedEvidenceArtifacts),
      skipOrBlockReasons = entry.blockReasons,
      standingBoundaries = entry.standingBoundaries,
      validationSummary = ValidationSummary,
    )

  private def qdrantOnlyEvidence(
    entry: M9BeautyQSearchEvalRealResourcePrerequisiteAuditEntry
  ): M9BeautyQSearchEvalRealResourceQdrantOnlySmokeEvidence =
    M9BeautyQSearchEvalRealResourceQdrantOnlySmokeEvidence(
      state = stateOf(entry),
      prerequisitesStatus = entry.status,
      requiredCheckpointDecision = entry.requiredCheckpointDecision,
      candidateSource = M9BeautyQSearchEvalQdrantOnlySmokeExecutionPlan.QdrantCandidateSource,
      servingMode = M9BeautyQSearchEvalQdrantOnlySmokeExecutionPlan.QdrantServingMode,
      selectedQueryAnchors = anchors(
        M9BeautyQSearchEvalQdrantOnlySmokeExecutionPlan.SelectedQueries.map(q => (q.queryId, q.queryClass))
      ),
      expectedEvidenceFilesOrSections = sections(entry.expectedEvidenceArtifacts),
      skipOrBlockReasons = entry.blockReasons,
      standingBoundaries = entry.standingBoundaries,
      validationSummary = ValidationSummary,
    )

  private def comparisonEvidence(
    entry: M9BeautyQSearchEvalRealResourcePrerequisiteAuditEntry
  ): M9BeautyQSearchEvalRealResourceComparisonSmokeEvidence =
    M9BeautyQSearchEvalRealResourceComparisonSmokeEvidence(
      state = stateOf(entry),
      prerequisitesStatus = entry.status,
      requiredCheckpointDecision = entry.requiredCheckpointDecision,
      esCandidateSource = CandidateSource.Es,
      qdrantCandidateSource = CandidateSource.Qdrant,
      esExecutionMode = M9BeautyQSearchEvalCombinedSmokeComparisonPlan.EsExecutionMode,
      qdrantExecutionMode = M9BeautyQSearchEvalCombinedSmokeComparisonPlan.QdrantExecutionMode,
      selectedQueryAnchors = anchors(
        M9BeautyQSearchEvalCombinedSmokeComparisonPlan.SelectedQueries.map(q => (q.queryId, q.queryClass))
      ),
      expectedEvidenceFilesOrSections = sections(entry.expectedEvidenceArtifacts),
      comparisonShape = M9BeautyQSearchEvalRealResourceComparisonEvidenceShape.Pending,
      skipOrBlockReasons = entry.blockReasons,
      standingBoundaries = entry.standingBoundaries,
      validationSummary = ValidationSummary,
    )
}
