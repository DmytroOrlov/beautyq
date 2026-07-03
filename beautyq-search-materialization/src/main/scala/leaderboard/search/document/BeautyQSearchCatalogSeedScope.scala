package leaderboard.search.document

import leaderboard.model.*

/** Seed-free, materialization-owned scope for the seed-scoped repo loading
  * algorithm: only the lists that algorithm actually reads. `serviceVariantSchemas`
  * is intentionally not carried here - the existing algorithm loads schemas
  * by `services.map(_.id)`, never by a seed-supplied schema list.
  * `beautyq-search-materialization` must not depend on seed data, so this is
  * a distinct type rather than a reuse of `BeautyQSeedData` (which stays in
  * `bifunctor-tagless`).
  */
final case class BeautyQSearchCatalogSeedScope(
  categories: List[Category],
  services: List[Service],
  masters: List[Master],
  masterLocations: List[MasterLocation],
  masterServiceOffers: List[MasterServiceOffer],
  masterServiceOfferVariants: List[MasterServiceOfferVariant],
) {
  def nonRootCategories: List[Category] =
    categories.filterNot(_.id == Category.rootCategoryId)
}
