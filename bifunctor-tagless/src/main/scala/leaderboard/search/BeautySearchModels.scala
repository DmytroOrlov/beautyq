package leaderboard.search

import izumi.functional.bio.Error2
import leaderboard.model.*
import leaderboard.search.parser.BeautySearchIntentParser
import leaderboard.search.dsl.SearchConstraint

final case class UserSearchInput(
  query: String,
  userLat: Option[BigDecimal],
  userLon: Option[BigDecimal],
  limit: Int = 10,
)

final case class ParsedSearchIntent(
  originalQuery: String,
  normalizedTokens: List[String],
  explicitConstraints: List[SearchConstraint],
  softBoosts: List[SearchConstraint],
  remainingText: String,
)

final case class BeautySearchAppliedFilter(
  constraint: SearchConstraint,
  explicit: Boolean,
)

final case class BeautySearchFacetValue(
  value: String,
  count: Int,
)

final case class BeautySearchFacet(
  fieldPath: String,
  values: List[BeautySearchFacetValue],
)

final case class VariantSearchResult(
  variantId: MasterServiceOfferVariantId,
  masterServiceOfferId: MasterServiceOfferId,
  masterLocationId: MasterLocationId,
  masterId: MasterId,
  serviceId: ServiceId,
  categoryId: Category.CategoryId,
  serviceName: String,
  categoryName: String,
  masterName: String,
  locationName: String,
  address: String,
  lat: BigDecimal,
  lon: BigDecimal,
  priceFrom: BigDecimal,
  priceTo: BigDecimal,
  durationMin: Int,
  enumAttributes: Map[String, String],
  booleanAttributes: Map[String, Boolean],
  intAttributes: Map[String, Int],
  bigDecimalAttributes: Map[String, BigDecimal],
  score: Double,
  distanceKm: Option[BigDecimal],
)

final case class ProviderSearchResult(
  masterId: MasterId,
  masterName: String,
  masterLocationId: MasterLocationId,
  locationName: String,
  address: String,
  matchingVariantCount: Int,
  sampleMatchingVariantIds: List[MasterServiceOfferVariantId],
  bestScore: Double,
  distanceKm: Option[BigDecimal],
)

final case class ServiceIntentSearchResult(
  serviceId: ServiceId,
  serviceName: String,
  categoryId: Category.CategoryId,
  categoryName: String,
  matchingVariantCount: Int,
  bestScore: Double,
)

final case class BeautySearchResponse(
  variantCarousel: List[VariantSearchResult],
  providerCarousel: List[ProviderSearchResult],
  serviceIntentCarousel: List[ServiceIntentSearchResult],
  facets: List[BeautySearchFacet],
  inferredFilters: List[BeautySearchAppliedFilter],
)

trait BeautySearchBackend[F[_, _]] {
  def search(input: UserSearchInput, intent: ParsedSearchIntent): F[QueryFailure, BeautySearchResponse]
}

trait BeautySearchService[F[_, _]] {
  def search(input: UserSearchInput): F[QueryFailure, BeautySearchResponse]
}

object BeautySearchService {
  final class Impl[F[+_, +_]: Error2](
    parser: BeautySearchIntentParser,
    backend: BeautySearchBackend[F],
  ) extends BeautySearchService[F] {

    override def search(input: UserSearchInput): F[QueryFailure, BeautySearchResponse] =
      backend.search(input, parser.parse(input))
  }
}
