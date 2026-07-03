package leaderboard.search.beautyq.contract

import leaderboard.search.document.BeautyQVariantSearchDocumentContract
import leaderboard.search.dsl.{BeautyQSearchIntentVocabulary, BeautySearchSpecV1}

/** A single, coordinator-readable aggregate over the BeautyQ contract slices
  * that already exist, so the catalog declaration is no longer visually
  * isolated from the document/intent/runtime/response/evaluation declarations
  * that sit beside it in this module.
  *
  * [[label]] states explicitly, so callers/tests can assert it rather than
  * only read it in a comment: this is an aggregate over existing slices, NOT
  * a complete [[leaderboard.search.contract.SearchDomainSpec]]. It does not
  * construct a generic `SearchDomainSpec[...]` value - the current BeautyQ
  * slices are still expressed through `search-core`'s `SearchDocumentSpec`/
  * `SearchQuerySchema`/`SearchRuntimeSpec`/`CarouselSpec`/`FacetSpec`, not the
  * generic `search-contract-core` ADTs (the evaluation section is the one
  * exception, expressed directly as `search-contract-core`'s `EvalSection`).
  * Every value below is a direct reference to an already-owned section; this
  * object adds no new contract content and no repository, materialization,
  * ES/Qdrant client, route, or runtime service import.
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

  val evaluation = BeautyQSearchEvaluationContract.section

  /** Evaluation declarations now exist in `beautyq-search-contract`. */
  val evaluationDeclared: Boolean = true

  /** Explicit, testable record of what still blocks a full
    * `SearchDomainSpec[...]` assembly. See [[BeautyQSearchDomainSpecReadiness]].
    */
  val searchDomainSpecReadiness: BeautyQSearchDomainSpecReadiness = BeautyQSearchDomainSpecReadiness.current

  /** This aggregate is not a generic `SearchDomainSpec[...]` value - backed by
    * the non-empty pending decisions in [[searchDomainSpecReadiness]].
    */
  val fullSearchDomainSpecDeclared: Boolean = searchDomainSpecReadiness.fullSearchDomainSpecDeclared
}
