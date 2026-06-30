package leaderboard.search.dsl

import leaderboard.model.QueryFailure
import leaderboard.search.document.VariantSearchDocument

object BeautyQSearchPresentation {
  object BoostRoles {
    val Service: SearchBoostRole = SearchBoostRole("service")
    val Attribute: SearchBoostRole = SearchBoostRole("attribute")
    val ProviderDistance: SearchBoostRole = SearchBoostRole("providerDistance")
  }

  object RankingWeights {
    val TextScore = "textScoreWeight"
    val ServiceBoost = "serviceBoostWeight"
    val AttributeBoost = "attributeBoostWeight"
    val ProviderDistance = "providerDistanceWeight"
    val ProviderMatchingVariantCount = "providerMatchingVariantCountWeight"
  }

  object CarouselLimits {
    val Variant = "variantSize"
    val Provider = "providerSize"
    val ServiceIntent = "serviceIntentSize"
  }

  object CarouselGroups {
    val Provider = "providerGroupField"
    val ServiceIntent = "serviceIntentGroupField"
  }

  def carouselSpec(
    providerGroupField: SearchField[VariantSearchDocument],
    serviceIntentGroupField: SearchField[VariantSearchDocument],
  ): CarouselSpec[VariantSearchDocument] =
    CarouselSpec(
      limits = List(
        CarouselLimit(CarouselLimits.Variant, 10),
        CarouselLimit(CarouselLimits.Provider, 10),
        CarouselLimit(CarouselLimits.ServiceIntent, 10),
      ),
      groups = List(
        CarouselGroup(CarouselGroups.Provider, providerGroupField),
        CarouselGroup(CarouselGroups.ServiceIntent, serviceIntentGroupField),
      ),
      ranking = RankingSpec(
        weights = List(
          RankingWeight(RankingWeights.TextScore, 1.0),
          RankingWeight(RankingWeights.ServiceBoost, 2.0, Set(BoostRoles.Service)),
          RankingWeight(RankingWeights.AttributeBoost, 1.5, Set(BoostRoles.Attribute)),
          RankingWeight(RankingWeights.ProviderDistance, 1.25, Set(BoostRoles.ProviderDistance)),
          RankingWeight(RankingWeights.ProviderMatchingVariantCount, 0.5),
        )
      ),
      geoScoringBoostRole = Some(BoostRoles.ProviderDistance),
    )

  def variantLimit(carousel: CarouselSpec[VariantSearchDocument]): Either[QueryFailure, Int] =
    carousel.limit(CarouselLimits.Variant)

  def providerLimit(carousel: CarouselSpec[VariantSearchDocument]): Either[QueryFailure, Int] =
    carousel.limit(CarouselLimits.Provider)

  def serviceIntentLimit(carousel: CarouselSpec[VariantSearchDocument]): Either[QueryFailure, Int] =
    carousel.limit(CarouselLimits.ServiceIntent)

  def providerGroupField(carousel: CarouselSpec[VariantSearchDocument]): Either[QueryFailure, SearchField[VariantSearchDocument]] =
    carousel.group(CarouselGroups.Provider)

  def serviceIntentGroupField(carousel: CarouselSpec[VariantSearchDocument]): Either[QueryFailure, SearchField[VariantSearchDocument]] =
    carousel.group(CarouselGroups.ServiceIntent)

  def textScoreWeight(ranking: RankingSpec): Either[QueryFailure, Double] =
    ranking.weight(RankingWeights.TextScore)

  def serviceBoostWeight(ranking: RankingSpec): Either[QueryFailure, Double] =
    ranking.weight(RankingWeights.ServiceBoost)

  def providerDistanceWeight(ranking: RankingSpec): Either[QueryFailure, Double] =
    ranking.weight(RankingWeights.ProviderDistance)

  def providerMatchingVariantCountWeight(ranking: RankingSpec): Either[QueryFailure, Double] =
    ranking.weight(RankingWeights.ProviderMatchingVariantCount)
}
