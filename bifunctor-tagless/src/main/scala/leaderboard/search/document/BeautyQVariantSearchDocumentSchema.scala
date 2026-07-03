package leaderboard.search.document

import leaderboard.model.QueryFailure
import leaderboard.search.dsl.*

/** Materialization/projection compatibility surface for the BeautyQ variant
  * document. The contract-shaped declarations (`Fields`, `documentSpec`,
  * `qdrantPayloadSpec`, `querySchema`) are owned by
  * [[BeautyQVariantSearchDocumentContract]] in `beautyq-search-contract` and
  * only delegated to here for source compatibility. The actual projection
  * engine - node handles, joins, schema validation, and text normalization -
  * is owned by [[BeautyQVariantSearchDocumentMaterialization]] in
  * `beautyq-search-materialization`; `project`/`projection` here only adapt
  * the seed-coupled [[BeautySearchCatalogSnapshot]] shape to that engine.
  * This object remains only a source-compatibility facade for existing
  * callers.
  */
object BeautyQVariantSearchDocumentSchema {

  val Fields = BeautyQVariantSearchDocumentContract.Fields

  lazy val documentSpec: SearchDocumentSpec[VariantSearchDocument] = BeautyQVariantSearchDocumentContract.documentSpec
  lazy val qdrantPayloadSpec: SearchDocumentPayloadSpec[VariantSearchDocument] = BeautyQVariantSearchDocumentContract.qdrantPayloadSpec
  lazy val querySchema: SearchQuerySchema[VariantSearchDocument, SearchConstraint] = BeautyQVariantSearchDocumentContract.querySchema

  lazy val projection: SearchDocumentProjection[BeautySearchCatalogSnapshot, VariantSearchDocument] =
    SearchDocumentProjection(
      documentSpec = documentSpec,
      project = project,
    )

  def project(snapshot: BeautySearchCatalogSnapshot): Either[QueryFailure, List[VariantSearchDocument]] =
    BeautyQVariantSearchDocumentMaterialization.project(BeautySearchCatalogSnapshot.toMaterializationSnapshot(snapshot))
}
