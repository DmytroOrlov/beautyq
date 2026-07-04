package leaderboard.plugins

import leaderboard.api.BeautySearchServingGate
import org.scalatest.wordspec.AnyWordSpec

final class BeautySearchLocalQdrantSupplementLauncherPlanSpec extends AnyWordSpec {

  "BeautySearchLocalQdrantSupplementLauncherPlan.VectorSpec" should {
    "declare the exact managed-local vector search spec" in {
      val spec = BeautySearchLocalQdrantSupplementLauncherPlan.VectorSpec
      assert(spec.collectionName == "beauty_variant_v1_local_llama_cpp_embedding_variant_embedding_1024_cosine")
      assert(spec.vectorName == "variant-embedding")
      assert(spec.topK == 100)
      assert(spec.scoreThreshold == None)
    }
  }

  "BeautySearchLocalQdrantSupplementLauncherPlan.default" should {
    "carry the managed-local vector search spec" in {
      assert(BeautySearchLocalQdrantSupplementLauncherPlan.default.vectorSearchSpec == BeautySearchLocalQdrantSupplementLauncherPlan.VectorSpec)
    }

    "carry the enabled-ready serving gate" in {
      assert(BeautySearchLocalQdrantSupplementLauncherPlan.default.servingGate == BeautySearchServingGate.enabledReady)
    }

    "carry a runtime binding plan built from the same vector search spec" in {
      assert(BeautySearchLocalQdrantSupplementLauncherPlan.default.runtimeBindingPlan.vectorSearchSpec == BeautySearchLocalQdrantSupplementLauncherPlan.VectorSpec)
    }

    "carry a runtime binding plan with the exact lexical backend binding name" in {
      assert(
        BeautySearchLocalQdrantSupplementLauncherPlan.default.runtimeBindingPlan.lexicalBackendBindingName ==
          BeautySearchQdrantSupplementRuntimeBindingPlan.LexicalBackendBindingName
      )
    }
  }
}
