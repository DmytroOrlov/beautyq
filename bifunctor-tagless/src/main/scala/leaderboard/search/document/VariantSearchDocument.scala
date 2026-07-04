package leaderboard.search.document

import izumi.functional.bio.Error2
import leaderboard.model.*
import leaderboard.repo.{Categories, MasterLocations, MasterServiceOfferVariants, MasterServiceOffers, Masters, ServiceVariantSchemas, Services}
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
    fromMaterializationSnapshot(
      BeautyQSearchCatalogSnapshot(
        categories                 = seed.categories,
        services                   = seed.services,
        serviceVariantSchemas      = seed.serviceVariantSchemas,
        masters                    = seed.masters,
        masterLocations            = seed.masterLocations,
        masterServiceOffers        = seed.masterServiceOffers,
        masterServiceOfferVariants = seed.masterServiceOfferVariants,
      )
    )

  /** Converts a materialization-owned [[BeautyQSearchCatalogSnapshot]] into
    * the legacy shape for existing callers.
    */
  def fromMaterializationSnapshot(
    snapshot: BeautyQSearchCatalogSnapshot
  ): BeautySearchCatalogSnapshot =
    BeautySearchCatalogSnapshot(
      categories                 = snapshot.categories,
      services                   = snapshot.services,
      serviceVariantSchemas      = snapshot.serviceVariantSchemas,
      masters                    = snapshot.masters,
      masterLocations            = snapshot.masterLocations,
      masterServiceOffers        = snapshot.masterServiceOffers,
      masterServiceOfferVariants = snapshot.masterServiceOfferVariants,
    )

  /** Converts the legacy snapshot into the materialization-owned shape, for
    * callers that need to hand data to `beautyq-search-materialization` APIs.
    */
  def toMaterializationSnapshot(
    snapshot: BeautySearchCatalogSnapshot
  ): BeautyQSearchCatalogSnapshot =
    BeautyQSearchCatalogSnapshot(
      categories                 = snapshot.categories,
      services                   = snapshot.services,
      serviceVariantSchemas      = snapshot.serviceVariantSchemas,
      masters                    = snapshot.masters,
      masterLocations            = snapshot.masterLocations,
      masterServiceOffers        = snapshot.masterServiceOffers,
      masterServiceOfferVariants = snapshot.masterServiceOfferVariants,
    )
}

object VariantSearchDocumentBuilder {
  def build(snapshot: BeautySearchCatalogSnapshot): Either[QueryFailure, List[VariantSearchDocument]] =
    BeautyQVariantSearchDocumentMaterialization.project(BeautySearchCatalogSnapshot.toMaterializationSnapshot(snapshot))
}

trait BeautySearchCatalogSnapshotLoader[F[_, _]] {
  def load(): F[QueryFailure, BeautySearchCatalogSnapshot]
}

object BeautySearchCatalogSnapshotLoader {

  /** Compatibility facade over the materialization-owned, seed-free
    * [[BeautyQSearchCatalogSnapshotLoader.FromRepositories]] in
    * `beautyq-search-materialization`, which owns the actual traversal
    * (order, dedup) via `leaderboard.repo.BeautyQCatalogGraph`/`leaderboard.repo.GraphLoading`.
    * This class only adapts the materialization loader's
    * [[BeautyQSearchCatalogSnapshot]] result into the legacy
    * [[BeautySearchCatalogSnapshot]] shape for existing callers.
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

    private val delegate =
      new BeautyQSearchCatalogSnapshotLoader.FromRepositories[F](
        categories                 = categories,
        services                   = services,
        serviceVariantSchemas      = serviceVariantSchemas,
        masters                    = masters,
        masterLocations            = masterLocations,
        masterServiceOffers        = masterServiceOffers,
        masterServiceOfferVariants = masterServiceOfferVariants,
      )

    override def load(): F[QueryFailure, BeautySearchCatalogSnapshot] =
      for {
        snapshot <- delegate.load()
      } yield BeautySearchCatalogSnapshot.fromMaterializationSnapshot(snapshot)
  }

  /** Compatibility facade over the materialization-owned, seed-free
    * [[BeautyQSearchCatalogSnapshotLoader.SeedScopedFromRepositories]] in
    * `beautyq-search-materialization`, which owns the actual seed-scoped
    * repo loading (same `GraphLoading.seedRequired`/`seedValues` calls,
    * model names, id selectors, and missing-entity messages). This class
    * only converts `BeautyQSeedData` into a seed-free
    * [[BeautyQSearchCatalogSeedScope]] and adapts the materialization
    * loader's [[BeautyQSearchCatalogSnapshot]] result into the legacy
    * [[BeautySearchCatalogSnapshot]] shape for existing callers.
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

    private val delegate =
      new BeautyQSearchCatalogSnapshotLoader.SeedScopedFromRepositories[F](
        seedScope                  = seedScopeFromSeedData(seed),
        categories                 = categories,
        services                   = services,
        serviceVariantSchemas      = serviceVariantSchemas,
        masters                    = masters,
        masterLocations            = masterLocations,
        masterServiceOffers        = masterServiceOffers,
        masterServiceOfferVariants = masterServiceOfferVariants,
      )

    override def load(): F[QueryFailure, BeautySearchCatalogSnapshot] =
      for {
        snapshot <- delegate.load()
      } yield BeautySearchCatalogSnapshot.fromMaterializationSnapshot(snapshot)
  }

  private def seedScopeFromSeedData(seed: BeautyQSeedData): BeautyQSearchCatalogSeedScope =
    BeautyQSearchCatalogSeedScope(
      categories                 = seed.categories,
      services                   = seed.services,
      masters                    = seed.masters,
      masterLocations            = seed.masterLocations,
      masterServiceOffers        = seed.masterServiceOffers,
      masterServiceOfferVariants = seed.masterServiceOfferVariants,
    )
}
