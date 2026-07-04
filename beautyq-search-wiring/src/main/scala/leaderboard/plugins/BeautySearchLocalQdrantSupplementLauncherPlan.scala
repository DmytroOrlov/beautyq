package leaderboard.plugins

import leaderboard.api.BeautySearchServingGate
import leaderboard.search.dsl.VectorSearchSpec

// Pure description of the managed-local Qdrant supplement launcher defaults
// `BeautySearchLocalQdrantSupplementLauncherModule` interprets into `distage.ModuleDef`/concrete
// clients/bootstrap resources, without depending on `distage`, concrete ES/Qdrant/embedding clients,
// or bootstrap/startup code. Interpreting a plan into the corresponding module/clients/bootstrap
// stays in `BeautySearchLocalQdrantSupplementLauncherModule` in `bifunctor-tagless`.
final case class BeautySearchLocalQdrantSupplementLauncherPlan(
  vectorSearchSpec: VectorSearchSpec,
  servingGate: BeautySearchServingGate,
  runtimeBindingPlan: BeautySearchQdrantSupplementRuntimeBindingPlan,
)

object BeautySearchLocalQdrantSupplementLauncherPlan {
  val VectorSpec: VectorSearchSpec =
    VectorSearchSpec(
      collectionName = "beauty_variant_v1_local_llama_cpp_embedding_variant_embedding_1024_cosine",
      vectorName = "variant-embedding",
      topK = 100,
      scoreThreshold = None,
    )

  val default: BeautySearchLocalQdrantSupplementLauncherPlan =
    BeautySearchLocalQdrantSupplementLauncherPlan(
      vectorSearchSpec = VectorSpec,
      servingGate = BeautySearchServingGate.enabledReady,
      runtimeBindingPlan = BeautySearchQdrantSupplementRuntimeBindingPlan.fromVectorSearchSpec(VectorSpec),
    )
}
