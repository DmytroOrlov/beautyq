package leaderboard.search.document

import izumi.functional.bio.Error2
import leaderboard.model.*
import leaderboard.repo.{BeautyQCatalogGraph, Categories, GraphLoading, MasterLocations, MasterServiceOfferVariants, MasterServiceOffers, Masters, ServiceVariantSchemas, Services}
import leaderboard.seed.{BeautyQSeedData, BeautyQSeedReady}

import scala.annotation.unused

final case class BeautySearchCatalogSnapshot(
  categories: List[Category],
  services: List[Service],
  serviceVariantSchemas: List[ServiceVariantSchema],
  masters: List[Master],
  masterLocations: List[MasterLocation],
  masterServiceOffers: List[MasterServiceOffer],
  masterServiceOfferVariants: List[MasterServiceOfferVariant],
)

object BeautySearchCatalogSnapshot {
  def fromSeedData(seed: BeautyQSeedData): BeautySearchCatalogSnapshot =
    BeautySearchCatalogSnapshot(
      categories = seed.categories,
      services = seed.services,
      serviceVariantSchemas = seed.serviceVariantSchemas,
      masters = seed.masters,
      masterLocations = seed.masterLocations,
      masterServiceOffers = seed.masterServiceOffers,
      masterServiceOfferVariants = seed.masterServiceOfferVariants,
    )
}

object VariantSearchDocumentBuilder {
  def build(snapshot: BeautySearchCatalogSnapshot): Either[QueryFailure, List[VariantSearchDocument]] =
    BeautyQVariantSearchDocumentSchema.project(snapshot)
}

trait BeautySearchCatalogSnapshotLoader[F[_, _]] {
  def load(): F[QueryFailure, BeautySearchCatalogSnapshot]
}

object BeautySearchCatalogSnapshotLoader {

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
  ) extends BeautySearchCatalogSnapshotLoader[F] {

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

    override def load(): F[QueryFailure, BeautySearchCatalogSnapshot] =
      for {
        loadedCategories <- GraphLoading.selfTreeFrom(relations.categoryTree, Category.rootCategoryId)
        loadedServices   <- GraphLoading.manyFor(relations.categoryServices, loadedCategories)
        loadedSchemas    <- GraphLoading.valueFor(relations.serviceSchemas, loadedServices)
        loadedMasters    <- GraphLoading.allOf(relations.allMasters)
        loadedLocations  <- GraphLoading.manyFor(relations.masterLocationsByMaster, loadedMasters)
        loadedOffers     <- GraphLoading.manyFor(relations.masterOffersByMaster, loadedMasters)
        loadedVariants   <- GraphLoading.manyFor(relations.offerVariants, loadedOffers)
      } yield BeautySearchCatalogSnapshot(
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
    * layer, failing with the canonical missing-entity message when a seed item
    * is absent.
    */
  final class SeedScopedFromRepositories[F[+_, +_]: Error2](
    @unused seedReady: BeautyQSeedReady,
    seed: BeautyQSeedData,
    categories: Categories[F],
    services: Services[F],
    serviceVariantSchemas: ServiceVariantSchemas[F],
    masters: Masters[F],
    masterLocations: MasterLocations[F],
    masterServiceOffers: MasterServiceOffers[F],
    masterServiceOfferVariants: MasterServiceOfferVariants[F],
  ) extends BeautySearchCatalogSnapshotLoader[F] {

    override def load(): F[QueryFailure, BeautySearchCatalogSnapshot] =
      for {
        loadedCategories <- GraphLoading.seedRequired(seed.nonRootCategories, Categories.entity.modelName, (_: Category).id, Categories.byId(categories))
        loadedServices   <- GraphLoading.seedRequired(seed.services, Services.entity.modelName, (_: Service).id, Services.byId(services))
        loadedSchemas    <- GraphLoading.seedValues(seed.services.map(_.id), ServiceVariantSchemas.byService(serviceVariantSchemas))
        loadedMasters    <- GraphLoading.seedRequired(seed.masters, Masters.entity.modelName, (_: Master).id, Masters.byId(masters))
        loadedLocations  <- GraphLoading.seedRequired(seed.masterLocations, MasterLocations.entity.modelName, (_: MasterLocation).id, MasterLocations.byId(masterLocations))
        loadedOffers     <- GraphLoading.seedRequired(seed.masterServiceOffers, MasterServiceOffers.entity.modelName, (_: MasterServiceOffer).id, MasterServiceOffers.byId(masterServiceOffers))
        loadedVariants   <- GraphLoading.seedRequired(seed.masterServiceOfferVariants, MasterServiceOfferVariants.entity.modelName, (_: MasterServiceOfferVariant).id, MasterServiceOfferVariants.byId(masterServiceOfferVariants))
      } yield BeautySearchCatalogSnapshot(
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
