package leaderboard.search.document

import leaderboard.model.*

/** Seed-free, materialization-owned BeautyQ catalog snapshot: the same seven
  * repo-backed lists as the legacy `BeautySearchCatalogSnapshot` in
  * `bifunctor-tagless`, without that type's `fromSeedData` seed coupling.
  * `beautyq-search-materialization` must not depend on seed data, so this is
  * a distinct type rather than a relocation of the legacy one.
  */
final case class BeautyQSearchCatalogSnapshot(
  categories: List[Category],
  services: List[Service],
  serviceVariantSchemas: List[ServiceVariantSchema],
  masters: List[Master],
  masterLocations: List[MasterLocation],
  masterServiceOffers: List[MasterServiceOffer],
  masterServiceOfferVariants: List[MasterServiceOfferVariant],
)
