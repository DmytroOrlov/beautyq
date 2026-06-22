package leaderboard.plugins

import distage.{ModuleDef, TagKK}
import izumi.functional.bio.Error2
import leaderboard.api.BeautySearchServingGate
import leaderboard.seed.BeautyQSeedLoader
import zio.IO

object BeautySearchRouteModules {
  def seedCatalogInMemory[F[+_, +_]: TagKK: Error2]: ModuleDef = new ModuleDef {
    include(BeautySearchCatalogBackendModules.seedResourceInMemory[F])
    include(BeautySearchPluginModules.api[F])
    make[BeautyQSeedLoader].from[BeautyQSeedLoader.ResourceLoader]
  }

  def seedCatalogElasticsearch: ModuleDef = new ModuleDef {
    include(BeautySearchCatalogBackendModules.seedResourceElasticsearch)
    include(BeautySearchPluginModules.api[IO])
    make[BeautyQSeedLoader].from[BeautyQSeedLoader.ResourceLoader]
  }

  // Local/dev/test-only ES seed route with an explicit serving-gate selection.
  // Mirrors `seedCatalogElasticsearch` but binds the given gate instead of the disabled default.
  // Does not change the default route, Qdrant opt-in, or add fallback.
  def seedCatalogElasticsearchWithServingGate(servingGate: BeautySearchServingGate): ModuleDef = new ModuleDef {
    include(BeautySearchCatalogBackendModules.seedResourceElasticsearch)
    include(BeautySearchPluginModules.apiWithServingGate[IO](servingGate))
    make[BeautyQSeedLoader].from[BeautyQSeedLoader.ResourceLoader]
  }

  def seedCatalogElasticsearchWithOperatorVisibility: ModuleDef = new ModuleDef {
    include(seedCatalogElasticsearch)
    include(BeautySearchPluginModules.operatorVisibilityApi[IO])
  }

  def seedCatalogQdrantExplicitOptIn: ModuleDef = new ModuleDef {
    include(BeautySearchCatalogBackendModules.seedResourceQdrantExplicitOptIn)
    include(BeautySearchPluginModules.api[IO])
    make[BeautyQSeedLoader].from[BeautyQSeedLoader.ResourceLoader]
  }

  def seedCatalogElasticsearchPortConfigured: ModuleDef = new ModuleDef {
    include(ElasticsearchClientModules.portConfigured)
    include(seedCatalogElasticsearch)
  }

  def seedCatalogElasticsearchPortConfiguredWithOperatorVisibility: ModuleDef = new ModuleDef {
    include(ElasticsearchClientModules.portConfigured)
    include(seedCatalogElasticsearchWithOperatorVisibility)
  }

  def apiElasticsearch: ModuleDef = new ModuleDef {
    include(seedCatalogElasticsearchPortConfigured)
  }

  def apiElasticsearchWithOperatorVisibility: ModuleDef = new ModuleDef {
    include(seedCatalogElasticsearchPortConfiguredWithOperatorVisibility)
  }

  def apiQdrantExplicitOptIn: ModuleDef = new ModuleDef {
    include(seedCatalogQdrantExplicitOptIn)
  }
}
