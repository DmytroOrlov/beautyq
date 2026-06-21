package leaderboard.search.eval

/** Pure static runbook/evidence consistency contract.
  *
  * This contract names the future-only real-resource smoke runbook and verifies, as a total pure
  * function of the runbook text, that it stays consistent with the accepted saved-evidence schema and
  * the accepted default/no-config artifact shape. It is static only: it never parses production routes,
  * never inspects the runtime environment, never runs Elasticsearch or Qdrant, never creates a client,
  * never calls production `/beauty-search`, and never touches a route, plugin, DI, or HTTP source.
  *
  * It proves two things about the runbook:
  *   1. the runbook mentions the three evidence modes (ES-only, Qdrant-only, combined ES/Qdrant) and the
  *      required planning-boundary language; and
  *   2. the runbook does not make any forbidden positive claim (success, production readiness, route
  *      activation, serving approval, quality green, Qdrant production activation, hybrid serving,
  *      fallback, score fusion, reranking, production telemetry, or route switch).
  *
  * The expected mode count is cross-checked against the accepted evidence schema, and the contract
  * confirms the accepted default/no-config artifact renders blocked/skip (never success) evidence.
  */
object M9BeautyQSearchEvalRealResourceRunbookConsistency {

  /** Repository-relative path of the future-only runbook stub. */
  val RunbookPath: String = "docs/local/BEAUTYQ_M9_REAL_RESOURCE_SMOKE_RUNBOOK.md"

  /** Section headers the runbook must contain. */
  val RequiredSections: List[String] =
    List(
      "## 0. Status and scope",
      "## 1. Future modes",
      "## 2. Required prerequisites",
      "## 3. Default / no-config behavior",
      "## 4. Future evidence capture",
      "## 5. Preserved production boundaries",
      "## 6. Not allowed by this runbook",
    )

  /** The three evidence modes the runbook must mention. One human-readable token per accepted evidence
    * schema kind; the count is cross-checked against the schema below.
    */
  val RequiredEvidenceModeTokens: List[String] =
    List(
      "ES-only",
      "Qdrant-only",
      "Combined ES/Qdrant",
    )

  /** Planning-boundary language the runbook must carry. */
  val RequiredBoundaryTokens: List[String] =
    List(
      "future-only",
      "does not approve",
      "remains ES-backed",
      "disabled by default",
      "not approved",
      "blocked/skip",
      "pending execution only",
      "real-call checkpoint",
      "prerequisites audit",
      "saved evidence schema",
      "deterministic",
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
