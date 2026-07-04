package leaderboard.search.beautyq.contract

/** Records why [[BeautyQSearchDomainContract.fullSearchDomainSpecDeclared]]
  * is still `false`, as explicit, testable data rather than only a comment.
  * Each pending decision names the still-`search-core`-shaped area that
  * blocks assembling a generic
  * `leaderboard.search.contract.SearchDomainSpec[Catalog, Document,
  * ResultUnit]` value. This type does not build any generic section itself -
  * it only records which ones are ready and which are still pending.
  */
final case class BeautyQSearchDomainSpecPendingDecision(
  id: String,
  section: String,
  reason: String,
)

final case class BeautyQSearchDomainSpecReadiness(
  readySections: List[String],
  pendingDecisions: List[BeautyQSearchDomainSpecPendingDecision],
) {
  def fullSearchDomainSpecDeclared: Boolean =
    pendingDecisions.isEmpty
}

object BeautyQSearchDomainSpecReadiness {
  val current: BeautyQSearchDomainSpecReadiness =
    BeautyQSearchDomainSpecReadiness(
      readySections = List(
        "catalog",
        "evaluation",
        "document-result-unit",
        "intent-languages",
        "document-field-kind-mapping",
        "runtime-capabilities",
        "response-policy",
      ),
      pendingDecisions = List(
        BeautyQSearchDomainSpecPendingDecision(
          id = "intent-section-mapping",
          section = "intent",
          reason = "IntentSection requires generic vocabularies, vocabularyGroups, and noiseControls; BeautyQ currently exposes supported languages plus a search-core SearchIntentVocabulary, but no source-owned generic IntentSection mapping yet.",
        ),
      ),
    )
}
