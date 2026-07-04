package leaderboard.plugins

import leaderboard.search.document.{BeautyQVariantSearchDocumentContract, VariantSearchDocument}
import leaderboard.search.dsl.{SearchField, VectorSearchSpec}

// Pure description of the Qdrant supplement runtime bindings
// `BeautySearchQdrantSupplementRuntimeBindingModules` interprets into `distage.ModuleDef`, without
// depending on `distage`, concrete ES/Qdrant/embedding clients, or the app graph. Interpreting a plan
// into the corresponding runtime bindings stays in `BeautySearchQdrantSupplementRuntimeBindingModules`
// in `bifunctor-tagless`.
final case class BeautySearchQdrantSupplementRuntimeBindingPlan(
  vectorSearchSpec: VectorSearchSpec,
  lexicalBackendBindingName: String,
  variantIdField: SearchField[VariantSearchDocument],
)

object BeautySearchQdrantSupplementRuntimeBindingPlan {
  val LexicalBackendBindingName: String =
    "qdrantSupplementLexicalElasticsearch"

  def fromVectorSearchSpec(vectorSearchSpec: VectorSearchSpec): BeautySearchQdrantSupplementRuntimeBindingPlan =
    BeautySearchQdrantSupplementRuntimeBindingPlan(
      vectorSearchSpec = vectorSearchSpec,
      lexicalBackendBindingName = LexicalBackendBindingName,
      variantIdField = BeautyQVariantSearchDocumentContract.Fields.variantId,
    )
}
