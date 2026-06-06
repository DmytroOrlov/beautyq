package leaderboard.search.hybrid

import leaderboard.search.{BeautySearchResponse, ProviderSearchResult, ServiceIntentSearchResult, VariantSearchResult}
import leaderboard.search.document.VariantSearchDocument

sealed trait BeautyQHybridDisplayScorePolicy
object BeautyQHybridDisplayScorePolicy {
  case object LexicalThenSemantic extends BeautyQHybridDisplayScorePolicy
}

final case class BeautyQHybridResponseAdapterDiagnostics(
  inputCandidateCount: Int,
  variantResultCount: Int,
  providerCarouselSuppressed: Boolean,
  serviceIntentCarouselSuppressed: Boolean,
  facetsSuppressed: Boolean,
  inferredFiltersSuppressed: Boolean,
)

final case class BeautyQHybridResponseAdapterResult(
  response: BeautySearchResponse,
  diagnostics: BeautyQHybridResponseAdapterDiagnostics,
)

object BeautyQHybridResponseAdapter {
  def variantOnlyResponse(
    projection: BeautyQHybridVariantProjectionResult,
    displayScorePolicy: BeautyQHybridDisplayScorePolicy =
      BeautyQHybridDisplayScorePolicy.LexicalThenSemantic,
  ): BeautyQHybridResponseAdapterResult = {
    val variantResults = toVariantResults(projection, displayScorePolicy)

    BeautyQHybridResponseAdapterResult(
      response = BeautySearchResponse(
        variantCarousel = variantResults,
        providerCarousel = Nil,
        serviceIntentCarousel = Nil,
        facets = Nil,
        inferredFilters = Nil,
      ),
      diagnostics = BeautyQHybridResponseAdapterDiagnostics(
        inputCandidateCount = projection.candidates.size,
        variantResultCount = variantResults.size,
        providerCarouselSuppressed = true,
        serviceIntentCarouselSuppressed = true,
        facetsSuppressed = true,
        inferredFiltersSuppressed = true,
      ),
    )
  }

  def responseWithProviderServiceCarousels(
    variantProjection: BeautyQHybridVariantProjectionResult,
    providerServiceProjection: BeautyQHybridProviderServiceProjectionResult,
    displayScorePolicy: BeautyQHybridDisplayScorePolicy =
      BeautyQHybridDisplayScorePolicy.LexicalThenSemantic,
  ): BeautyQHybridResponseAdapterResult = {
    val variantResults = toVariantResults(variantProjection, displayScorePolicy)

    BeautyQHybridResponseAdapterResult(
      response = BeautySearchResponse(
        variantCarousel = variantResults,
        providerCarousel = providerServiceProjection.providerCandidates.map(toProviderResult),
        serviceIntentCarousel = providerServiceProjection.serviceIntentCandidates.map(toServiceIntentResult),
        facets = Nil,
        inferredFilters = Nil,
      ),
      diagnostics = BeautyQHybridResponseAdapterDiagnostics(
        inputCandidateCount = variantProjection.candidates.size,
        variantResultCount = variantResults.size,
        providerCarouselSuppressed = false,
        serviceIntentCarouselSuppressed = false,
        facetsSuppressed = true,
        inferredFiltersSuppressed = true,
      ),
    )
  }

  private def toVariantResults(
    projection: BeautyQHybridVariantProjectionResult,
    displayScorePolicy: BeautyQHybridDisplayScorePolicy,
  ): List[VariantSearchResult] =
    projection.candidates.map(candidate =>
      toVariantResult(
        document = candidate.document,
        score = displayScore(candidate, displayScorePolicy),
      )
    )

  private def displayScore(
    candidate: BeautyQHybridProjectedVariantCandidate,
    displayScorePolicy: BeautyQHybridDisplayScorePolicy,
  ): Double =
    displayScorePolicy match {
      case BeautyQHybridDisplayScorePolicy.LexicalThenSemantic =>
        candidate.lexicalScore.orElse(candidate.semanticScore).getOrElse(0.0)
    }

  private def toVariantResult(document: VariantSearchDocument, score: Double): VariantSearchResult =
    VariantSearchResult(
      variantId = document.variantId,
      masterServiceOfferId = document.masterServiceOfferId,
      masterLocationId = document.masterLocationId,
      masterId = document.masterId,
      serviceId = document.serviceId,
      categoryId = document.categoryId,
      serviceName = document.serviceName,
      categoryName = document.categoryName,
      masterName = document.masterName,
      locationName = document.locationName,
      address = document.address,
      lat = document.lat,
      lon = document.lon,
      priceFrom = document.priceFrom,
      priceTo = document.priceTo,
      durationMin = document.durationMin,
      enumAttributes = document.enumAttributes,
      booleanAttributes = document.booleanAttributes,
      intAttributes = document.intAttributes,
      bigDecimalAttributes = document.bigDecimalAttributes,
      score = score,
      distanceKm = None,
    )

  private def toProviderResult(candidate: BeautyQHybridProviderCandidate): ProviderSearchResult =
    ProviderSearchResult(
      masterId = candidate.masterId,
      masterName = candidate.masterName,
      masterLocationId = candidate.masterLocationId,
      locationName = candidate.locationName,
      address = candidate.address,
      matchingVariantCount = candidate.matchingVariantCount,
      sampleMatchingVariantIds = candidate.sampleMatchingVariantIds.take(3),
      bestScore = candidate.representativeDisplayScore,
      distanceKm = None,
    )

  private def toServiceIntentResult(candidate: BeautyQHybridServiceIntentCandidate): ServiceIntentSearchResult =
    ServiceIntentSearchResult(
      serviceId = candidate.serviceId,
      serviceName = candidate.serviceName,
      categoryId = candidate.categoryId,
      categoryName = candidate.categoryName,
      matchingVariantCount = candidate.matchingVariantCount,
      bestScore = candidate.representativeDisplayScore,
    )
}
