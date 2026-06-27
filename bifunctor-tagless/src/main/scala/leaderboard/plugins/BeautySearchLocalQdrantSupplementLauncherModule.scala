package leaderboard.plugins

import distage.{ModuleDef, Scene}
import leaderboard.api.BeautySearchServingGate
import leaderboard.config.QdrantPortCfg
import leaderboard.search.document.BeautySearchReadyCatalogDocuments
import leaderboard.search.dsl.{BeautySearchSpec, VectorSearchSpec}
import leaderboard.search.elasticsearch.ElasticsearchJsonClient
import leaderboard.search.embedding.{EmbeddingClient, LlamaCppEmbeddingClient, LlamaCppEmbeddingClientConfig}
import leaderboard.search.qdrant.{QdrantClient, QdrantClientSearchAdapter, QdrantSearchClient}
import leaderboard.search.startup.BeautyQManagedLocalSearchDataReady
import logstage.LogIO2
import zio.IO

object BeautySearchLocalQdrantSupplementLauncherModule {
  val VectorSpec: VectorSearchSpec =
    VectorSearchSpec(
      collectionName = "beauty_variant_v1_local_llama_cpp_embedding_variant_embedding_1024_cosine",
      vectorName = "variant-embedding",
      topK = 100,
      scoreThreshold = None,
    )

  /** Default local embedding endpoint required by the managed BeautyQ Qdrant bootstrap. */
  val DefaultEmbeddingEndpoint: String = "http://localhost:8081"

  /** Configured local embedding endpoint (env override, default `http://localhost:8081`). */
  def embeddingEndpoint: String =
    sys.env.getOrElse("M18_QDRANT_EMBEDDING_ENDPOINT", DefaultEmbeddingEndpoint)

  def managedLocalDefault: ModuleDef = new ModuleDef {
    tag(Scene.Managed)

    include(ElasticsearchClientModules.portConfigured)
    include(BeautySearchRouteModules.apiQdrantVariantSupplementExplicitOptIn(BeautySearchServingGate.enabledReady))
    include(BeautySearchQdrantSupplementRuntimeBindingModules.supplementRuntimeBindings(VectorSpec))

    make[EmbeddingClient].from {
      () =>
        new LlamaCppEmbeddingClient(
          LlamaCppEmbeddingClientConfig(baseUrl = embeddingEndpoint)
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

    // Local managed startup prepares the ES baseline index and the Qdrant supplement collection/vectors
    // before the HTTP server serves `/beauty-search`. No user-facing activation env flag, no manual
    // Qdrant collection/index step. Only bound under `Scene.Managed`.
    make[BeautyQManagedLocalSearchDataReady].fromResource {
      (
        esClient: ElasticsearchJsonClient,
        qdrantClient: QdrantClient,
        embeddingClient: EmbeddingClient,
        spec: BeautySearchSpec,
        catalog: BeautySearchReadyCatalogDocuments,
        log: LogIO2[IO],
      ) =>
        new BeautyQManagedLocalSearchDataReady.Bootstrap(
          esClient,
          qdrantClient,
          embeddingClient,
          spec,
          catalog,
          VectorSpec,
          embeddingEndpoint,
          log,
        )
    }
  }
}
