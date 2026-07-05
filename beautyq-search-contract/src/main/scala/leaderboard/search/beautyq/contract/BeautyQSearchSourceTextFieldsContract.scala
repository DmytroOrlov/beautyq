package leaderboard.search.beautyq.contract

import leaderboard.search.document.{BeautyQVariantSearchDocumentContract, VariantSearchDocument}
import leaderboard.search.dsl.SearchField

/** The canonical BeautyQ Qdrant semantic source-text field order: the exact fields (and order)
  * embedded into the vector at index time (see `EmbeddingSpec.sourceTextFields`, consumed by
  * `SearchEmbeddingTextExtractor.extract`). Runtime/benchmark consumers
  * ([[leaderboard.search.startup.BeautyQManagedLocalSearchBootstrapPlan]],
  * `QdrantEmbeddingBenchmarkExecutorConfig`) previously declared this same four-field list locally;
  * this object is the single contract-owned source of truth they and their tests now reference.
  */
object BeautyQSearchSourceTextFieldsContract {
  val qdrantSourceTextFields: List[SearchField[VariantSearchDocument]] =
    List(
      BeautyQVariantSearchDocumentContract.Fields.serviceText,
      BeautyQVariantSearchDocumentContract.Fields.attributeText,
      BeautyQVariantSearchDocumentContract.Fields.allText,
      BeautyQVariantSearchDocumentContract.Fields.categoryName,
    )

  val qdrantSourceTextFieldPaths: List[String] = qdrantSourceTextFields.map(_.path)
}
