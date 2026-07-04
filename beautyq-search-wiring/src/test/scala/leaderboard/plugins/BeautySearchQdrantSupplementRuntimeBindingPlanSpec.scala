package leaderboard.plugins

import leaderboard.search.document.BeautyQVariantSearchDocumentContract
import leaderboard.search.dsl.VectorSearchSpec
import org.scalatest.wordspec.AnyWordSpec

final class BeautySearchQdrantSupplementRuntimeBindingPlanSpec extends AnyWordSpec {
  private val testSpec: VectorSearchSpec =
    VectorSearchSpec(
      collectionName = "test-collection",
      vectorName = "test-vector",
      topK = 5,
      scoreThreshold = Some(0.5d),
    )

  "BeautySearchQdrantSupplementRuntimeBindingPlan" should {
    "declare the exact lexical backend binding name" in {
      assert(BeautySearchQdrantSupplementRuntimeBindingPlan.LexicalBackendBindingName == "qdrantSupplementLexicalElasticsearch")
    }

    "carry the supplied vector search spec unchanged" in {
      assert(BeautySearchQdrantSupplementRuntimeBindingPlan.fromVectorSearchSpec(testSpec).vectorSearchSpec == testSpec)
    }

    "carry the exact lexical backend binding name" in {
      assert(
        BeautySearchQdrantSupplementRuntimeBindingPlan.fromVectorSearchSpec(testSpec).lexicalBackendBindingName ==
          BeautySearchQdrantSupplementRuntimeBindingPlan.LexicalBackendBindingName
      )
    }

    "reference the same variant id field as BeautyQVariantSearchDocumentContract" in {
      assert(BeautySearchQdrantSupplementRuntimeBindingPlan.fromVectorSearchSpec(testSpec).variantIdField eq BeautyQVariantSearchDocumentContract.Fields.variantId)
    }
  }
}
