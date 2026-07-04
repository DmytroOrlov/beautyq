package leaderboard.search.document

import leaderboard.model.QueryFailure

/** Materialization/projection compatibility surface for the BeautyQ variant
  * document. This object no longer forwards the contract-shaped declarations
  * (`Fields`, `documentSpec`, `qdrantPayloadSpec`, `querySchema`) - those are
  * owned by [[BeautyQVariantSearchDocumentContract]] in
  * `beautyq-search-contract`, and callers must read them from there directly.
  * The actual projection engine - node handles, joins, schema validation, and
  * text normalization - is owned by [[BeautyQVariantSearchDocumentMaterialization]]
  * in `beautyq-search-materialization`; `project`/`projection` here only
  * adapt the seed-coupled [[BeautySearchCatalogSnapshot]] shape to that
  * engine. This object remains only a projection/materialization
  * compatibility facade for existing `project`/`projection` callers.
  */
object BeautyQVariantSearchDocumentSchema {

  lazy val projection: SearchDocumentProjection[BeautySearchCatalogSnapshot, VariantSearchDocument] =
    SearchDocumentProjection(
      documentSpec = BeautyQVariantSearchDocumentContract.documentSpec,
      project = project,
    )

  def project(snapshot: BeautySearchCatalogSnapshot): Either[QueryFailure, List[VariantSearchDocument]] =
    BeautyQVariantSearchDocumentMaterialization.project(BeautySearchCatalogSnapshot.toMaterializationSnapshot(snapshot))
}
