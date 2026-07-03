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
      ),
      pendingDecisions = List(
        BeautyQSearchDomainSpecPendingDecision(
          id = "intent-languages",
          section = "intent",
          reason = "IntentSection requires explicit SearchLanguage values; BeautyQ vocabulary has multilingual tokens but no declared supported-language list yet.",
        ),
        BeautyQSearchDomainSpecPendingDecision(
          id = "document-field-kind-mapping",
          section = "document",
          reason = "BeautyQ document fields use search-core SearchFieldKind; no source-owned mapping to search-contract-core SearchFieldKind exists yet.",
        ),
        BeautyQSearchDomainSpecPendingDecision(
          id = "runtime-capabilities",
          section = "runtime",
          reason = "RuntimeSection requires pure SearchBackendCapabilities declarations; BeautyQ runtime currently exposes search-core runtime specs, not generic capability declarations.",
        ),
        BeautyQSearchDomainSpecPendingDecision(
          id = "response-policy",
          section = "response",
          reason = "ResponseSection requires generic grouping/carousel/facet/inferred-filter/debug policies; BeautyQ currently exposes search-core CarouselSpec and FacetSpec without a source-owned generic mapping.",
        ),
      ),
    )
}
