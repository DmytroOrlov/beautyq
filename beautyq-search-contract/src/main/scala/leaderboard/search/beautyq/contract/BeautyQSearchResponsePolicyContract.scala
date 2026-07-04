package leaderboard.search.beautyq.contract

import leaderboard.search.contract.{CarouselPolicy, DebugPolicy, FacetResponsePolicy, InferredFilterPolicy, PresentationMetadata, ResponseSection, SearchFieldName}
import leaderboard.search.dsl.{BeautyQSearchPresentation, BeautySearchSpecV1}

/** BeautyQ-specific response policy details not representable in the generic
  * `ResponseSection`: the provider/service-intent carousel limits and group
  * fields. Generic `GroupingPolicy` has only one grouping slot, but BeautyQ
  * has two component-specific grouping policies (provider carousel groups by
  * `masterLocationId`, service-intent carousel groups by `serviceId`), so
  * neither is chosen as the generic grouping policy - both are preserved here
  * instead, and the generic section's `grouping` stays `None`.
  */
final case class BeautyQSearchResponsePolicyDetails(
  variantCarouselLimitName: String,
  providerCarouselLimitName: String,
  serviceIntentCarouselLimitName: String,
  variantCarouselMaxItems: Int,
  providerCarouselMaxItems: Int,
  serviceIntentCarouselMaxItems: Int,
  providerGroupFieldName: String,
  serviceIntentGroupFieldName: String,
  facetFieldNames: List[String],
  inferredFilterDominanceThreshold: BigDecimal,
  inferredFilterMinCount: Int,
)

object BeautyQSearchResponsePolicyContract {
  private val carousel = BeautySearchSpecV1.spec.carouselSpec
  private val facets = BeautySearchSpecV1.spec.facetSpec

  val details: BeautyQSearchResponsePolicyDetails = {
    val limits = BeautyQSearchPresentation.requireCarouselLimitValues(carousel)
    BeautyQSearchResponsePolicyDetails(
      variantCarouselLimitName = BeautyQSearchPresentation.CarouselLimits.Variant,
      providerCarouselLimitName = BeautyQSearchPresentation.CarouselLimits.Provider,
      serviceIntentCarouselLimitName = BeautyQSearchPresentation.CarouselLimits.ServiceIntent,
      variantCarouselMaxItems = limits.variantSize,
      providerCarouselMaxItems = limits.providerSize,
      serviceIntentCarouselMaxItems = limits.serviceIntentSize,
      providerGroupFieldName = BeautyQSearchPresentation.providerGroupField(carousel).fold(error => throw new IllegalStateException(error.message), _.path),
      serviceIntentGroupFieldName = BeautyQSearchPresentation.serviceIntentGroupField(carousel).fold(error => throw new IllegalStateException(error.message), _.path),
      facetFieldNames = facets.fields.map(_.path),
      inferredFilterDominanceThreshold = facets.inferredFilterDominanceThreshold,
      inferredFilterMinCount = facets.inferredFilterMinCount,
    )
  }

  val section: ResponseSection =
    ResponseSection(
      grouping = None,
      carousel = Some(
        CarouselPolicy(
          enabled = true,
          maxItems = Some(details.variantCarouselMaxItems),
        )
      ),
      facets = Some(
        FacetResponsePolicy(
          fields = details.facetFieldNames.map(SearchFieldName.apply)
        )
      ),
      inferredFilters = Some(
        InferredFilterPolicy(
          enabled = facets.enabled,
          description =
            s"BeautyQ inferred filters use FacetSpec dominance threshold ${details.inferredFilterDominanceThreshold} and min count ${details.inferredFilterMinCount}.",
        )
      ),
      presentation = PresentationMetadata(Map.empty),
      debug = DebugPolicy(includeExplanation = false, includeScoreBreakdown = false),
    )
}
