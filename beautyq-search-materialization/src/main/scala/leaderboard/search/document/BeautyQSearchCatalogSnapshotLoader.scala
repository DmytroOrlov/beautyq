package leaderboard.search.document

import izumi.functional.bio.Error2
import leaderboard.model.{Category, QueryFailure}
import leaderboard.repo.{BeautyQCatalogGraph, Categories, GraphLoading, MasterLocations, MasterServiceOfferVariants, MasterServiceOffers, Masters, ServiceVariantSchemas, Services}

/** Seed-free, materialization-owned loader for [[BeautyQSearchCatalogSnapshot]].
  * `beautyq-search-materialization` must not depend on seed data, so this
  * only covers the seed-free traversal; the seed-scoped loader stays in
  * `bifunctor-tagless`, coupled to `BeautyQSeedData`/`BeautyQSeedReady`.
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

    private val relations = new BeautyQCatalogGraph.Relations[F](
      BeautyQCatalogGraph.Repositories(
        categories                 = categories,
        services                   = services,
        serviceVariantSchemas      = serviceVariantSchemas,
        masters                    = masters,
        masterLocations            = masterLocations,
        masterServiceOffers        = masterServiceOffers,
        masterServiceOfferVariants = masterServiceOfferVariants,
      )
    )

    override def load(): F[QueryFailure, BeautyQSearchCatalogSnapshot] =
      for {
        loadedCategories <- GraphLoading.selfTreeFrom(relations.categoryTree, Category.rootCategoryId)
        loadedServices   <- GraphLoading.manyFor(relations.categoryServices, loadedCategories)
        loadedSchemas    <- GraphLoading.valueFor(relations.serviceSchemas, loadedServices)
        loadedMasters    <- GraphLoading.allOf(relations.allMasters)
        loadedLocations  <- GraphLoading.manyFor(relations.masterLocationsByMaster, loadedMasters)
        loadedOffers     <- GraphLoading.manyFor(relations.masterOffersByMaster, loadedMasters)
        loadedVariants   <- GraphLoading.manyFor(relations.offerVariants, loadedOffers)
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
}
