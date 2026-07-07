package leaderboard.search.beautyq.contract

/** Names one area, if any, still blocking assembly of a full generic
  * `leaderboard.search.contract.SearchDomainSpec[Catalog, Document,
  * ResultUnit]` value. For the current BeautyQ closeout state,
  * [[BeautyQSearchDomainSpecReadiness.current]] carries none of these - see
  * [[BeautyQSearchDomainSpecReadiness]].
  */
final case class BeautyQSearchDomainSpecPendingDecision(
  id: String,
  section: String,
  reason: String,
)

/** Records the current full-`SearchDomainSpec` readiness state as explicit,
  * testable data rather than only a comment. `pendingDecisions` is expected
  * to be empty for the current BeautyQ closeout state: every section needed
  * to assemble a generic `SearchDomainSpec` already exists and is referenced
  * by [[BeautyQSearchDomainContract]]. Keeping this data structure around
  * remains useful even so - future contract expansion can add an explicit
  * [[BeautyQSearchDomainSpecPendingDecision]] here instead of silently
  * flipping [[fullSearchDomainSpecDeclared]] back to `false`. This type does
  * not build any generic section itself, and it does not import
  * repositories, materialization, ES/Qdrant/HTTP clients, routes, or runtime
  * services.
  */
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
        "intent-section-mapping",
      ),
      pendingDecisions = Nil,
    )
}
