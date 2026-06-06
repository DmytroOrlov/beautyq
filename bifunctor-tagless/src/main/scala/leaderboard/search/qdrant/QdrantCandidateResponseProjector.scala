package leaderboard.search.qdrant

import leaderboard.search.*
import leaderboard.search.dsl.BeautySearchSpec
import leaderboard.search.semantic.SemanticResponseProjector

final class QdrantBeautySearchResponseProjector(
  spec: BeautySearchSpec,
  input: UserSearchInput,
) extends SemanticResponseProjector[QdrantCandidateAssembly, BeautySearchResponse] {
  override def project(assembly: QdrantCandidateAssembly): BeautySearchResponse =
    QdrantCandidateResponseProjector.project(spec, input, assembly)
}

object QdrantCandidateResponseProjector {
  def project(
    spec: BeautySearchSpec,
    input: UserSearchInput,
    assembly: QdrantCandidateAssembly,
  ): BeautySearchResponse =
    BeautySearchResponse(
      variantCarousel = assembly.variantCandidates
        .take(math.min(input.limit, spec.carouselSpec.variantSize))
        .map(toVariantResult),
      providerCarousel = assembly.providerCandidates
        .take(spec.carouselSpec.providerSize)
        .map(toProviderResult),
      serviceIntentCarousel = assembly.serviceCandidates
        .take(spec.carouselSpec.serviceIntentSize)
        .map(toServiceIntentResult),
      facets = Nil,
      inferredFilters = Nil,
    )

  private def toVariantResult(candidate: QdrantVariantCandidate): VariantSearchResult = {
    val document = candidate.document
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
      score = candidate.score,
      distanceKm = None,
    )
  }

  private def toProviderResult(group: QdrantProviderCandidateGroup): ProviderSearchResult = {
    val head = group.variants.head.document
    ProviderSearchResult(
      masterId = head.masterId,
      masterName = head.masterName,
      masterLocationId = head.masterLocationId,
      locationName = head.locationName,
      address = head.address,
      matchingVariantCount = group.count,
      sampleMatchingVariantIds = group.variants.take(3).map(_.document.variantId),
      bestScore = group.bestScore,
      distanceKm = None,
    )
  }

  private def toServiceIntentResult(group: QdrantServiceCandidateGroup): ServiceIntentSearchResult = {
    val head = group.variants.head.document
    ServiceIntentSearchResult(
      serviceId = head.serviceId,
      serviceName = head.serviceName,
      categoryId = head.categoryId,
      categoryName = head.categoryName,
      matchingVariantCount = group.count,
      bestScore = group.bestScore,
    )
  }
}
