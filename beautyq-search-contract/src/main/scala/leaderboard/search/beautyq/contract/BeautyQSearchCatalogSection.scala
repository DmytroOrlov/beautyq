package leaderboard.search.beautyq.contract

import leaderboard.search.contract.CatalogSection

/** Wraps [[BeautyQSearchDeclarations.catalog]] in the generic
  * `CatalogSection` from `search-contract-core`.
  *
  * [[label]] states explicitly, so callers/tests can assert it rather than
  * only read it in a comment: this is still only the BeautyQ catalog
  * topology section, not the whole business search contract by itself. The
  * document, intent, runtime, response, and evaluation sections now exist
  * too, each in its own sibling contract-owned object, and are assembled
  * together with this one into the full generic `SearchDomainSpec` by
  * [[BeautyQSearchDomainContract]]. This object stays catalog-only on
  * purpose - it must not be treated as, or described as, that full
  * contract.
  */
object BeautyQCatalogSection {

  val label: String = "catalog topology section, not complete search contract"

  val catalogTopology: CatalogSection[BeautyQSearchDeclarations.catalog.type] =
    CatalogSection(BeautyQSearchDeclarations.catalog)

  /** Alias for [[catalogTopology]] for call sites that read more naturally as
    * "the catalog section of the BeautyQ search contract".
    */
  def section: CatalogSection[BeautyQSearchDeclarations.catalog.type] = catalogTopology
}
