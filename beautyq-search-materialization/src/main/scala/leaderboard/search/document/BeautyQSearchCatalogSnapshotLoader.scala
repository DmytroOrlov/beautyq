package leaderboard.search.document

import izumi.functional.bio.Error2
import leaderboard.model.*
import leaderboard.repo.{BeautyQCatalogGraph, Categories, GraphLoading, MasterLocations, MasterServiceOfferVariants, MasterServiceOffers, Masters, ServiceVariantSchemas, Services, loadAll}

/** Materialization-owned loader for [[BeautyQSearchCatalogSnapshot]].
  * `beautyq-search-materialization` must not depend on seed data: the
  * seed-scoped variant below takes a seed-free [[BeautyQSearchCatalogSeedScope]]
  * rather than `BeautyQSeedData`/`BeautyQSeedReady`, which stay in
  * `bifunctor-tagless`.
  */
trait BeautyQSearchCatalogSnapshotLoader[F[_, _]] {
  def load(): F[QueryFailure, BeautyQSearchCatalogSnapshot]
}

object BeautyQSearchCatalogSnapshotLoader {

  /** Loads the full BeautyQ catalog by traversing the model-first catalog
    * graph.
    *
    * The traversal order, ordering guarantees and first-occurrence dedup are
    * defined by [[BeautyQCatalogGraph]] (structure) and [[GraphLoading]]
    * (generic interpreter); this loader only orchestrates the relations and
    * assembles the snapshot.
    */
  final class FromRepositories[F[+_, +_]: Error2](
    categories: Categories[F],
    services: Services[F],
    serviceVariantSchemas: ServiceVariantSchemas[F],
    masters: Masters[F],
    masterLocations: MasterLocations[F],
    masterServiceOffers: MasterServiceOffers[F],
    masterServiceOfferVariants: MasterServiceOfferVariants[F],
  ) extends BeautyQSearchCatalogSnapshotLoader[F] {

    private val repositories = BeautyQCatalogGraph.Repositories[F](
      categories                 = categories,
      services                   = services,
      serviceVariantSchemas      = serviceVariantSchemas,
      masters                    = masters,
      masterLocations            = masterLocations,
      masterServiceOffers        = masterServiceOffers,
      masterServiceOfferVariants = masterServiceOfferVariants,
    )

    override def load(): F[QueryFailure, BeautyQSearchCatalogSnapshot] =
      for {
        loaded <- BeautyQCatalogGraph.graph[F].loadAll(repositories)
      } yield BeautyQSearchCatalogSnapshot(
        categories                 = loaded.values[List[Category]],
        services                   = GraphLoading.distinctByKey(loaded.values[List[Service]])(_.id),
        serviceVariantSchemas      = GraphLoading.distinctByKey(loaded.values[List[ServiceVariantSchema]])(_.serviceId),
        masters                    = GraphLoading.distinctByKey(loaded.values[List[Master]])(_.id),
        masterLocations            = GraphLoading.distinctByKey(loaded.values[List[MasterLocation]])(_.id),
        masterServiceOffers        = GraphLoading.distinctByKey(loaded.values[List[MasterServiceOffer]])(_.id),
        masterServiceOfferVariants = GraphLoading.distinctByKey(loaded.values[List[MasterServiceOfferVariant]])(_.id),
      )
  }

  /** Loads exactly the seed-scoped catalog through the shared repo operation
    * layer, failing with the canonical missing-entity message when a seed
    * item is absent. Seed-free: takes a [[BeautyQSearchCatalogSeedScope]]
    * rather than `BeautyQSeedData`/`BeautyQSeedReady`.
    */
  final class SeedScopedFromRepositories[F[+_, +_]: Error2](
    seedScope: BeautyQSearchCatalogSeedScope,
    categories: Categories[F],
    services: Services[F],
    serviceVariantSchemas: ServiceVariantSchemas[F],
    masters: Masters[F],
    masterLocations: MasterLocations[F],
    masterServiceOffers: MasterServiceOffers[F],
    masterServiceOfferVariants: MasterServiceOfferVariants[F],
  ) extends BeautyQSearchCatalogSnapshotLoader[F] {

    override def load(): F[QueryFailure, BeautyQSearchCatalogSnapshot] =
      for {
        loadedCategories <- GraphLoading.seedRequired(seedScope.nonRootCategories, Categories.entity.modelName, (_: Category).id, Categories.byId(categories))
        loadedServices   <- GraphLoading.seedRequired(seedScope.services, Services.entity.modelName, (_: Service).id, Services.byId(services))
        loadedSchemas    <- GraphLoading.seedValues(seedScope.services.map(_.id), ServiceVariantSchemas.byService(serviceVariantSchemas))
        loadedMasters    <- GraphLoading.seedRequired(seedScope.masters, Masters.entity.modelName, (_: Master).id, Masters.byId(masters))
        loadedLocations  <- GraphLoading.seedRequired(seedScope.masterLocations, MasterLocations.entity.modelName, (_: MasterLocation).id, MasterLocations.byId(masterLocations))
        loadedOffers     <- GraphLoading.seedRequired(seedScope.masterServiceOffers, MasterServiceOffers.entity.modelName, (_: MasterServiceOffer).id, MasterServiceOffers.byId(masterServiceOffers))
        loadedVariants   <- GraphLoading.seedRequired(seedScope.masterServiceOfferVariants, MasterServiceOfferVariants.entity.modelName, (_: MasterServiceOfferVariant).id, MasterServiceOfferVariants.byId(masterServiceOfferVariants))
      } yield BeautyQSearchCatalogSnapshot(
        categories                 = loadedCategories,
        services                   = loadedServices,
        serviceVariantSchemas      = loadedSchemas,
        masters                    = loadedMasters,
        masterLocations            = loadedLocations,
        masterServiceOffers        = loadedOffers,
        masterServiceOfferVariants = loadedVariants,
      )
  }
}
