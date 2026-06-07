package leaderboard.plugins

import distage.{ModuleDef, TagKK}
import izumi.functional.bio.Error2
import leaderboard.seed.BeautyQSeedLoader

object BeautySearchRouteModules {
  def seedCatalogInMemory[F[+_, +_]: TagKK: Error2]: ModuleDef = new ModuleDef {
    include(BeautySearchCatalogBackendModules.seedResourceInMemory[F])
    include(BeautySearchPluginModules.api[F])
    make[BeautyQSeedLoader].from[BeautyQSeedLoader.ResourceLoader]
  }
}
