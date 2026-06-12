package leaderboard.plugins

import distage.{ModuleDef, TagKK}
import izumi.functional.bio.Error2
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

  def seedCatalogElasticsearchPortConfigured: ModuleDef = new ModuleDef {
    include(ElasticsearchClientModules.portConfigured)
    include(seedCatalogElasticsearch)
  }

  def apiElasticsearch: ModuleDef = new ModuleDef {
    include(seedCatalogElasticsearchPortConfigured)
  }
}
