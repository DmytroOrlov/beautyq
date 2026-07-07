package leaderboard.search.eval

/** Pure static local-gate/evidence consistency contract.
  *
  * This contract names the current local Qdrant supplement gate doc and verifies, as a total pure
  * function of the doc text, that it stays consistent with the accepted saved-evidence schema and
  * the accepted local/test artifact shape. It is static only: it never parses production routes,
  * never inspects the runtime environment, never runs Elasticsearch or Qdrant, never creates a client,
  * never calls production `/beauty-search`, and never touches a route, plugin, DI, or HTTP source.
  *
  * It proves two things about the doc:
  *   1. the doc mentions the local launcher command, curl contract, frontend provenance fields, locked
  *      gate counts, and local/test-only boundaries; and
  *   2. the doc does not make any forbidden positive claim (production readiness, production route
  *      activation, serving approval, quality green, Qdrant production activation, hybrid serving,
  *      fallback, score fusion, reranking, production telemetry, or route switch).
  *
  * The legacy M9 evidence mode count is still cross-checked against the accepted evidence schema, and
  * the contract confirms the accepted default/no-config artifact renders blocked/skip (never success)
  * evidence.
  */
object M9BeautyQSearchEvalRealResourceRunbookConsistency {
  import leaderboard.search.beautyq.contract.BeautyQSearchResponseProvenanceContract.{ExecutionModes, JsonFields, ResultOrigins}

  /** Repository-relative path of the current local/test supplement gate doc. */
  val RunbookPath: String = "docs/BEAUTYQ_QDRANT_SUPPLEMENT_LOCAL_GATE.md"

  /** Section headers the doc must contain. */
  val RequiredSections: List[String] =
    List(
      "## Current status",
      "## Local launcher",
      "## Frontend provenance contract",
      "## Locked measured gate",
      "## Internal rollback/debug values",
      "## Preflight and smoke commands",
      "## Non-goals",
    )

  /** Three response/provenance mode tokens the doc must mention. The count remains cross-checked
    * against the legacy M9 schema below.
    */
  val RequiredEvidenceModeTokens: List[String] =
    List(
      ExecutionModes.EsOnly,
      ExecutionModes.EsPlusQdrantSupplement,
      ResultOrigins.QdrantSupplement,
    )

  /** Local/test boundary language the doc must carry. */
  val RequiredBoundaryTokens: List[String] =
    List(
      "./launcher -u scene:managed :leaderboard",
      "http://localhost:8080/beauty-search",
      "does not require an activation value",
      "used_with_append",
      "used_no_append",
      JsonFields.ResultOrigin,
      "testedQueries=4",
      "totalQdrantOnlyAppends=1",
      "no fallback",
      "no fusion",
      "no rerank",
    )

  /** Forbidden positive-claim tokens (compared case-insensitively). Each affirmative phrase maps to a
    * forbidden claim concept and must be ABSENT from the runbook. The runbook's negated boundary
    * language (e.g. "no route switch", "never success evidence") does not match these affirmative forms.
    */
  val ForbiddenPositiveClaimTokens: List[String] =
    List(
      // success
      "successfully executed",
      "execution succeeded",
      "real call succeeded",
      "real backend call succeeded",
      // production readiness
      "production ready",
      "production-ready",
      "is production ready",
      // route activation
      "route activated",
      "route is activated",
      "production route activated",
      // serving approval
      "serving approved",
      "approved for serving",
      // quality green
      "quality is green",
      "quality green confirmed",
      // Qdrant production activation
      "qdrant production activated",
      "qdrant production activation approved",
      // hybrid serving / fallback / fusion / reranking / production telemetry
      "hybrid serving enabled",
      "fallback enabled",
      "score fusion enabled",
      "reranking enabled",
      "production telemetry enabled",
      // route switch
      "route switched to",
    )

  /** Number of evidence modes the accepted schema defines. The runbook mode tokens are cross-checked
    * against this so the runbook cannot drift away from the schema's mode set.
    */
  val SchemaEvidenceModeCount: Int =
    M9BeautyQSearchEvalRealResourceSmokeEvidenceKind.values.length

  /** The accepted default/no-config artifact renders blocked/skip evidence for every mode, never
    * success evidence. The runbook claims exactly this; the contract confirms it against the schema.
    */
  val DefaultArtifactRendersBlockedSkipOnly: Boolean =
    M9BeautyQSearchEvalRealResourceSmokeEvidenceSchema.DefaultArtifact.evidences.forall(_.blockedOrSkipped)

  final case class M9BeautyQSearchEvalRealResourceRunbookConsistencyResult(
    runbookPath: String,
    missingSections: List[String],
    missingModeTokens: List[String],
    missingBoundaryTokens: List[String],
    forbiddenClaimTokensPresent: List[String],
    modeTokenCountMatchesSchema: Boolean,
    defaultArtifactBlockedSkipOnly: Boolean,
  ) {
    def allRequiredSectionsPresent: Boolean = missingSections.isEmpty
    def allModeTokensPresent: Boolean = missingModeTokens.isEmpty
    def allBoundaryTokensPresent: Boolean = missingBoundaryTokens.isEmpty
    def noForbiddenPositiveClaims: Boolean = forbiddenClaimTokensPresent.isEmpty

    /** The runbook is consistent with the accepted evidence schema and default artifact shape and makes
      * no forbidden positive claim.
      */
    def consistent: Boolean =
      allRequiredSectionsPresent &&
        allModeTokensPresent &&
        allBoundaryTokensPresent &&
        noForbiddenPositiveClaims &&
        modeTokenCountMatchesSchema &&
        defaultArtifactBlockedSkipOnly

    // Static-contract boundary fields: this never executes or inspects anything.
    def staticContractOnly: Boolean = true
    def parsesProductionRoutes: Boolean = false
    def inspectsRuntimeEnvironment: Boolean = false
    def realBackendCallImplemented: Boolean = false
    def routePluginDiHttpInvolved: Boolean = false
  }

  /** Verify the runbook text against the expected sections/tokens and the accepted schema. Pure: the
    * caller supplies the runbook contents; this contract performs no IO.
    */
  def verify(runbookText: String): M9BeautyQSearchEvalRealResourceRunbookConsistencyResult = {
    val lowered = runbookText.toLowerCase

    M9BeautyQSearchEvalRealResourceRunbookConsistencyResult(
      runbookPath = RunbookPath,
      missingSections = RequiredSections.filterNot(runbookText.contains),
      missingModeTokens = RequiredEvidenceModeTokens.filterNot(runbookText.contains),
      missingBoundaryTokens = RequiredBoundaryTokens.filterNot(runbookText.contains),
      forbiddenClaimTokensPresent = ForbiddenPositiveClaimTokens.filter(token => lowered.contains(token.toLowerCase)),
      modeTokenCountMatchesSchema = RequiredEvidenceModeTokens.length == SchemaEvidenceModeCount,
      defaultArtifactBlockedSkipOnly = DefaultArtifactRendersBlockedSkipOnly,
    )
  }
}
