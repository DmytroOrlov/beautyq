package leaderboard.search.beautyq.gen2.materialization

import leaderboard.search.beautyq.gen2.contract.{BeautyQSearchDeclarations, VariantSearchDocumentGen2}
import leaderboard.search.gen2.core.materialization.{ProjectedDocumentsFingerprint, SearchProjectedDocumentsFingerprint}

/** Thin BeautyQ binding over the generic [[SearchProjectedDocumentsFingerprint]]: supplies BeautyQ's
  * encoding version, projection format version and document declaration; the field traversal, ordering
  * and hashing mechanics all live in the generic kernel.
  */
object BeautyQProjectedDocumentsFingerprint {
  val EncodingVersion: String = "beautyq-projected-documents-v1"

  def compute(documents: Vector[VariantSearchDocumentGen2]): ProjectedDocumentsFingerprint =
    SearchProjectedDocumentsFingerprint.compute(
      encodingVersion = EncodingVersion,
      projectionFormatVersion = BeautyQVariantProjectionGen2.projectionFormatVersion,
      declaration = BeautyQSearchDeclarations.variants.document,
      documents = documents,
    )
}
