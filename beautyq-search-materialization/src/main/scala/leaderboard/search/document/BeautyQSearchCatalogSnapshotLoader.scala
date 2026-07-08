package leaderboard.search.document

import izumi.functional.bio.Error2
import leaderboard.model.*
import leaderboard.repo.{BeautyQCatalogGraph, Categories, GraphLoading, MasterLocations, MasterServiceOfferVariants, MasterServiceOffers, Masters, Relation, ServiceVariantSchemas, Services}

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

    private transparent inline def relation[A]: A =
      BeautyQCatalogGraph.graph[F]
        .relationAs[BeautyQCatalogGraph.Repositories[F] => A]
        .apply(repositories)

    private val categoryTree: Relation.SelfTree[F, Category, Category.CategoryId] =
      relation[Relation.SelfTree[F, Category, Category.CategoryId]]

    private val categoryServices: Relation.HasMany[F, Category, Category.CategoryId, Service, ServiceId] =
      relation[Relation.HasMany[F, Category, Category.CategoryId, Service, ServiceId]]

    private val serviceSchemas: Relation.HasValue[F, Service, ServiceId, ServiceVariantSchema, ServiceId, ServiceVariantSchemaItem] =
      relation[Relation.HasValue[F, Service, ServiceId, ServiceVariantSchema, ServiceId, ServiceVariantSchemaItem]]

    private val allMasters: Relation.All[F, Master, MasterId] =
      relation[Relation.All[F, Master, MasterId]]

    private val masterLocationsByMaster: Relation.HasMany[F, Master, MasterId, MasterLocation, MasterLocationId] =
      relation[Relation.HasMany[F, Master, MasterId, MasterLocation, MasterLocationId]]

    private val masterOffersByMaster: Relation.HasMany[F, Master, MasterId, MasterServiceOffer, MasterServiceOfferId] =
      relation[Relation.HasMany[F, Master, MasterId, MasterServiceOffer, MasterServiceOfferId]]

    private val offerVariants: Relation.HasMany[F, MasterServiceOffer, MasterServiceOfferId, MasterServiceOfferVariant, MasterServiceOfferVariantId] =
      relation[Relation.HasMany[F, MasterServiceOffer, MasterServiceOfferId, MasterServiceOfferVariant, MasterServiceOfferVariantId]]

    override def load(): F[QueryFailure, BeautyQSearchCatalogSnapshot] =
      for {
        loadedCategories <- GraphLoading.selfTreeFrom(categoryTree)
        loadedServices   <- GraphLoading.manyFor(categoryServices, loadedCategories)
        loadedSchemas    <- GraphLoading.valueFor(serviceSchemas, loadedServices)
        loadedMasters    <- GraphLoading.allOf(allMasters)
        loadedLocations  <- GraphLoading.manyFor(masterLocationsByMaster, loadedMasters)
        loadedOffers     <- GraphLoading.manyFor(masterOffersByMaster, loadedMasters)
        loadedVariants   <- GraphLoading.manyFor(offerVariants, loadedOffers)
      } yield BeautyQSearchCatalogSnapshot(
        categories                 = loadedCategories,
        services                   = GraphLoading.distinctByKey(loadedServices)(_.id),
        serviceVariantSchemas      = GraphLoading.distinctByKey(loadedSchemas)(_.serviceId),
        masters                    = GraphLoading.distinctByKey(loadedMasters)(_.id),
        masterLocations            = GraphLoading.distinctByKey(loadedLocations)(_.id),
        masterServiceOffers        = GraphLoading.distinctByKey(loadedOffers)(_.id),
        masterServiceOfferVariants = GraphLoading.distinctByKey(loadedVariants)(_.id),
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
