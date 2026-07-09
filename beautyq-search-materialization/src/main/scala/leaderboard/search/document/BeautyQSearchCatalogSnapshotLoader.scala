package leaderboard.search.document

import izumi.functional.bio.Error2
import leaderboard.model.*
import leaderboard.model.Category.CategoryId
import leaderboard.repo.{BeautyQCatalogGraph, Categories, GraphLoading, LoadedCatalog, MasterLocations, MasterServiceOfferVariants, MasterServiceOffers, Masters, Relation, ServiceVariantSchemas, Services, loadAll, toRawSnapshot, toSnapshot}

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

    override def load(): F[QueryFailure, BeautyQSearchCatalogSnapshot] = {
      import BeautyQCatalogGraph.Evidence.given

      for {
        loaded <- BeautyQCatalogGraph.graph[F].loadAll(repositories)
      } yield loaded.toSnapshot[BeautyQSearchCatalogSnapshot]
    }
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

    private val repositories = BeautyQCatalogGraph.Repositories[F](
      categories                 = categories,
      services                   = services,
      serviceVariantSchemas      = serviceVariantSchemas,
      masters                    = masters,
      masterLocations            = masterLocations,
      masterServiceOffers        = masterServiceOffers,
      masterServiceOfferVariants = masterServiceOfferVariants,
    )

    /** The category self-tree's own root key, read from the materialized
      * catalog declaration/relation - not `Category.rootCategoryId` directly -
      * so seed-scoped root filtering can never silently drift from what the
      * catalog declaration itself declares as the category tree's root.
      */
    private val categoryRootKey: CategoryId =
      BeautyQCatalogGraph.graph[F]
        .relationAs[BeautyQCatalogGraph.Repositories[F] => Relation.SelfTree[F, Category, CategoryId]]
        .apply(repositories)
        .rootKey

    private val seedCategories: List[Category] =
      seedScope.categories.filterNot(_.id == categoryRootKey)

    override def load(): F[QueryFailure, BeautyQSearchCatalogSnapshot] =
      for {
        loadedCategories <- GraphLoading.seedRequiredById[F, Categories[F], Category](seedCategories, categories)
        loadedServices   <- GraphLoading.seedRequiredById[F, Services[F], Service](seedScope.services, services)
        loadedSchemas    <- GraphLoading.seedValuesByKey[F, ServiceVariantSchemas[F], ServiceId, ServiceVariantSchema](seedScope.services.map(_.id), serviceVariantSchemas)
        loadedMasters    <- GraphLoading.seedRequiredById[F, Masters[F], Master](seedScope.masters, masters)
        loadedLocations  <- GraphLoading.seedRequiredById[F, MasterLocations[F], MasterLocation](seedScope.masterLocations, masterLocations)
        loadedOffers     <- GraphLoading.seedRequiredById[F, MasterServiceOffers[F], MasterServiceOffer](seedScope.masterServiceOffers, masterServiceOffers)
        loadedVariants   <- GraphLoading.seedRequiredById[F, MasterServiceOfferVariants[F], MasterServiceOfferVariant](seedScope.masterServiceOfferVariants, masterServiceOfferVariants)
      } yield LoadedCatalog(
        loadedCategories *:
        loadedServices *:
        loadedSchemas *:
        loadedMasters *:
        loadedLocations *:
        loadedOffers *:
        loadedVariants *:
        EmptyTuple
      ).toRawSnapshot[BeautyQSearchCatalogSnapshot]
  }
}
