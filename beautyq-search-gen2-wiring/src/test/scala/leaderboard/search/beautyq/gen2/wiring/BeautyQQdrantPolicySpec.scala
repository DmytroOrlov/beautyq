package leaderboard.search.beautyq.gen2.wiring

import leaderboard.search.beautyq.gen2.contract.BeautyQSearchDeclarations
import leaderboard.search.gen2.qdrant.{QdrantDistance, QdrantPayloadFieldSchema}
import org.scalatest.wordspec.AnyWordSpec

final class BeautyQQdrantPolicySpec extends AnyWordSpec {
  "BeautyQQdrantPolicy" should {
    "expose the canonical variant declaration and derived retrieval contract" in {
      val policy = BeautyQQdrantPolicy.policy
      val declarations = BeautyQSearchDeclarations.variants
      val fields = declarations.Fields

      assert(policy.declaration eq declarations.document)
      assert(policy.identity eq fields.variantId)
      assert(policy.embeddingField eq fields.allText)
      assert(policy.distance == QdrantDistance.Cosine)
      assert(policy.retrieval.topK == 20)
      assert(policy.retrieval.oversamplingFactor == 1)
      assert(policy.payloadIndexes.forall(index => index.schema != QdrantPayloadFieldSchema.Geo || index.path.value == fields.location.path.value))
    }
  }
}
