package leaderboard.search.beautyq.contract

import leaderboard.search.document.BeautyQVariantSearchDocumentContract
import leaderboard.search.dsl.{BeautyQSearchIntentVocabulary, BeautySearchSpecV1}

/** A single, coordinator-readable aggregate over the BeautyQ contract slices
  * that already exist, so the catalog declaration is no longer visually
  * isolated from the document/intent/runtime/response declarations that sit
  * beside it in this module.
  *
  * [[label]] states explicitly, so callers/tests can assert it rather than
  * only read it in a comment: this is an aggregate over existing slices, NOT
  * a complete [[leaderboard.search.contract.SearchDomainSpec]]. It declares
  * no evaluation section, and it does not construct a generic
  * `SearchDomainSpec[...]` value - the current BeautyQ slices are still
  * expressed through `search-core`'s `SearchDocumentSpec`/`SearchQuerySchema`/
  * `SearchRuntimeSpec`/`CarouselSpec`/`FacetSpec`, not the generic
  * `search-contract-core` ADTs, and evaluation declarations have not moved
  * into this module yet. Every value below is a direct reference to an
  * already-owned section; this object adds no new contract content and no
  * repository, materialization, ES/Qdrant client, route, or runtime service
  * import.
  */
object BeautyQSearchDomainContract {

  val domainId: String = "beautyq"

  val label: String =
    "BeautyQ search contract aggregate over catalog/document/intent/runtime/response slices; not complete SearchDomainSpec"

  /** The catalog topology section, owned by [[BeautyQCatalogDeclaration]] via
    * [[BeautyQCatalogSection]]. Unchanged by this aggregate.
    */
  val catalog = BeautyQCatalogSection.section

  val document = BeautyQVariantSearchDocumentContract.documentSpec

  val qdrantPayload = BeautyQVariantSearchDocumentContract.qdrantPayloadSpec

  val query = BeautyQVariantSearchDocumentContract.querySchema

  val intent = BeautyQSearchIntentVocabulary.vocabulary

  val searchSpec = BeautySearchSpecV1.spec

  val runtime = BeautySearchSpecV1.runtimeSpec

  val carousel = BeautySearchSpecV1.spec.carouselSpec

  val facets = BeautySearchSpecV1.spec.facetSpec

  /** No evaluation declarations exist in `beautyq-search-contract` yet. */
  val evaluationDeclared: Boolean = false

  /** This aggregate is not a generic `SearchDomainSpec[...]` value. */
  val fullSearchDomainSpecDeclared: Boolean = false
}
