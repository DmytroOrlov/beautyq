package leaderboard.plugins

import distage.{ModuleDef, Scene}
import leaderboard.config.QdrantPortCfg
import leaderboard.search.dsl.VectorSearchSpec
import leaderboard.search.embedding.{EmbeddingClient, LlamaCppEmbeddingClient, LlamaCppEmbeddingClientConfig}
import leaderboard.search.qdrant.{QdrantClient, QdrantClientSearchAdapter, QdrantSearchClient}

object BeautySearchLocalQdrantSupplementLauncherModule {
  val VectorSpec: VectorSearchSpec =
    VectorSearchSpec(
      collectionName = "beauty_variant_v1_local_llama_cpp_embedding_variant_embedding_1024_cosine",
      vectorName = "variant-embedding",
      topK = 100,
      scoreThreshold = None,
    )

  def managedLocalDefault: ModuleDef = new ModuleDef {
    tag(Scene.Managed)

    include(ElasticsearchClientModules.portConfigured)
    include(BeautySearchQdrantSupplementActivation.moduleFor(BeautySearchQdrantSupplementActivation.QdrantSupplementReady))
    include(BeautySearchQdrantSupplementRuntimeBindingModules.supplementRuntimeBindings(VectorSpec))

    make[EmbeddingClient].from {
      () =>
        new LlamaCppEmbeddingClient(
          LlamaCppEmbeddingClientConfig(baseUrl = sys.env.getOrElse("M18_QDRANT_EMBEDDING_ENDPOINT", "http://localhost:8081"))
        )
    }

    make[QdrantClient].from {
      (cfg: QdrantPortCfg) =>
        new QdrantClient(cfg.host, cfg.port)
    }

    make[QdrantSearchClient].from {
      (client: QdrantClient) =>
        new QdrantClientSearchAdapter(client)
    }
  }
}
