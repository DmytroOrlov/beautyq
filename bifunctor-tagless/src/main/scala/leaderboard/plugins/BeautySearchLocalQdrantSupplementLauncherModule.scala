package leaderboard.plugins

import distage.{ModuleDef, Scene}
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
    BeautySearchLocalQdrantSupplementLauncherPlan.VectorSpec

  val DefaultPlan: BeautySearchLocalQdrantSupplementLauncherPlan =
    BeautySearchLocalQdrantSupplementLauncherPlan.default

  def managedLocalDefault: ModuleDef = managedLocal(DefaultPlan)

  // `LauncherPlan -> ModuleDef`/client/bootstrap interpreter: assembles the same includes/bindings as
  // before, driven by the plan instead of hardcoded values.
  def managedLocal(plan: BeautySearchLocalQdrantSupplementLauncherPlan): ModuleDef = new ModuleDef {
    tag(Scene.Managed)

    include(ElasticsearchClientModules.portConfigured)
    include(BeautySearchRouteModules.apiQdrantVariantSupplementExplicitOptIn(plan.servingGate))
    include(BeautySearchQdrantSupplementRuntimeBindingModules.supplementRuntimeBindings(plan.runtimeBindingPlan))

    make[EmbeddingClient].from {
      (config: LlamaCppEmbeddingClientConfig) =>
        new LlamaCppEmbeddingClient(config)
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
        embeddingConfig: LlamaCppEmbeddingClientConfig,
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
          plan.vectorSearchSpec,
          embeddingConfig.baseUrl,
          log,
        )
    }
  }
}
