package leaderboard.search.beautyq.contract

import leaderboard.search.contract.CatalogSection

/** Wraps [[BeautyQCatalogDeclaration.declaration]] in the generic
  * `CatalogSection` from `search-contract-core`.
  *
  * [[label]] states explicitly, so callers/tests can assert it rather than
  * only read it in a comment: this is the BeautyQ catalog topology section
  * only, not the complete BeautyQ search contract. Document, intent,
  * runtime, response, and evaluation sections do not exist yet - this object
  * must not be treated as, or described as, a full `SearchDomainSpec`.
  */
object BeautyQCatalogSection {

  val label: String = "catalog topology section, not complete search contract"

  val catalogTopology: CatalogSection[BeautyQCatalogDeclaration.declaration.type] =
    CatalogSection(BeautyQCatalogDeclaration.declaration)

  /** Alias for [[catalogTopology]] for call sites that read more naturally as
    * "the catalog section of a not-yet-assembled BeautyQ search contract".
    */
  def section: CatalogSection[BeautyQCatalogDeclaration.declaration.type] = catalogTopology
}
