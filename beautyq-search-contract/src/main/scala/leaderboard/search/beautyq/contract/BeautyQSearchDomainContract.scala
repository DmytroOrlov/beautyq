package leaderboard.search.beautyq.contract

import leaderboard.search.contract.{DocumentSection, SearchDomainId, SearchDomainSpec}
import leaderboard.search.document.BeautyQVariantSearchDocumentContract
import leaderboard.search.dsl.{BeautyQSearchIntentVocabulary, BeautySearchSpecV1}

/** A single, coordinator-readable aggregate over the BeautyQ contract slices,
  * assembling the full generic [[leaderboard.search.contract.SearchDomainSpec]]
  * from contract-owned sections (catalog/document/intent/runtime/response/
  * evaluation). Legacy `search-core`-shaped values (`intent`, `document`,
  * `runtime`, `carousel`, `facets`) are kept alongside for existing callers.
  * Every value below is a direct reference to an already-owned section; this
  * object adds no new contract content and no repository, materialization,
  * ES/Qdrant client, route, or runtime service import.
  */
object BeautyQSearchDomainContract {

  val domainId: String = "beautyq"

  val label: String =
    "BeautyQ search contract aggregate over catalog/document/intent/runtime/response slices; full generic SearchDomainSpec declared"

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

  /** The canonical BeautyQ Qdrant semantic source-text field order, owned by
    * [[BeautyQSearchSourceTextFieldsContract]].
    */
  val qdrantSourceTextFields = BeautyQSearchSourceTextFieldsContract.qdrantSourceTextFields

  val qdrantSourceTextFieldPaths = BeautyQSearchSourceTextFieldsContract.qdrantSourceTextFieldPaths

  /** The BeautyQ variant result-unit descriptor `DocumentSection` requires. */
  val resultUnit = BeautyQSearchResultUnitContract.variant

  /** The supported-language list `IntentSection` requires. */
  val languages = BeautyQSearchLanguageContract.supported

  /** The generic document field list `DocumentSection` requires. */
  val fields = BeautyQSearchDocumentFieldContract.fields

  /** The generic runtime backend declarations `RuntimeSection` requires. */
  val runtimeSection = BeautyQSearchRuntimeContract.section

  /** The generic response policy `ResponseSection` requires. */
  val response = BeautyQSearchResponsePolicyContract.section

  /** The generic intent section `SearchDomainSpec` requires. */
  val intentSection = BeautyQSearchIntentSectionContract.section

  /** The generic document section, assembled from the already-owned
    * document/result-unit/field slices.
    */
  val documentSection =
    DocumentSection(
      document = document,
      resultUnit = resultUnit,
      fields = fields,
    )

  /** The full generic `SearchDomainSpec`, assembled entirely from
    * contract-owned sections above.
    */
  val searchDomainSpec =
    SearchDomainSpec(
      id = SearchDomainId(domainId),
      catalog = catalog,
      document = documentSection,
      intent = intentSection,
      runtime = runtimeSection,
      response = response,
      evaluation = evaluation,
    )

  /** Evaluation declarations now exist in `beautyq-search-contract`. */
  val evaluationDeclared: Boolean = true

  /** Explicit, testable record of what still blocks a full
    * `SearchDomainSpec[...]` assembly. See [[BeautyQSearchDomainSpecReadiness]].
    */
  val searchDomainSpecReadiness: BeautyQSearchDomainSpecReadiness = BeautyQSearchDomainSpecReadiness.current

  /** Whether the full generic `SearchDomainSpec` above is declared - backed by
    * [[searchDomainSpecReadiness]] having no pending decisions.
    */
  val fullSearchDomainSpecDeclared: Boolean = searchDomainSpecReadiness.fullSearchDomainSpecDeclared
}
