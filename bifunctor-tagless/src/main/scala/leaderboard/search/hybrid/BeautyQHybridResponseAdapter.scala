package leaderboard.search.hybrid

import leaderboard.search.{BeautySearchResponse, VariantSearchResult}
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
    val variantResults = projection.candidates.map(candidate =>
      toVariantResult(
        document = candidate.document,
        score = displayScore(candidate, displayScorePolicy),
      )
    )

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
}
