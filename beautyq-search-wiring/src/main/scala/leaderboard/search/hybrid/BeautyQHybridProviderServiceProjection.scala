package leaderboard.search.hybrid

import leaderboard.model.Category.CategoryId
import leaderboard.model.{MasterId, MasterLocationId, MasterServiceOfferVariantId, ServiceId}

final case class BeautyQHybridProviderCandidate(
  masterId: MasterId,
  masterName: String,
  masterLocationId: MasterLocationId,
  locationName: String,
  address: String,
  matchingVariantCount: Int,
  sampleMatchingVariantIds: List[MasterServiceOfferVariantId],
  representativeDisplayScore: Double,
  sources: Set[BeautyQHybridCandidateSource],
)

final case class BeautyQHybridServiceIntentCandidate(
  serviceId: ServiceId,
  serviceName: String,
  categoryId: CategoryId,
  categoryName: String,
  matchingVariantCount: Int,
  representativeDisplayScore: Double,
  sources: Set[BeautyQHybridCandidateSource],
)

final case class BeautyQHybridProviderServiceProjectionDiagnostics(
  inputCandidateCount: Int,
  providerCandidateCount: Int,
  serviceIntentCandidateCount: Int,
)

final case class BeautyQHybridProviderServiceProjectionResult(
  providerCandidates: List[BeautyQHybridProviderCandidate],
  serviceIntentCandidates: List[BeautyQHybridServiceIntentCandidate],
  diagnostics: BeautyQHybridProviderServiceProjectionDiagnostics,
)

object BeautyQHybridProviderServiceProjection {
  def project(
    projection: BeautyQHybridVariantProjectionResult,
    displayScorePolicy: BeautyQHybridDisplayScorePolicy =
      BeautyQHybridDisplayScorePolicy.LexicalThenSemantic,
  ): BeautyQHybridProviderServiceProjectionResult = {
    val providerGroups = groupsInFirstOccurrenceOrder(projection.candidates)(_.document.masterLocationId)
    val serviceIntentGroups = groupsInFirstOccurrenceOrder(projection.candidates)(_.document.serviceId)

    BeautyQHybridProviderServiceProjectionResult(
      providerCandidates = providerGroups.map { group =>
        val first = group.head
        BeautyQHybridProviderCandidate(
          masterId = first.document.masterId,
          masterName = first.document.masterName,
          masterLocationId = first.document.masterLocationId,
          locationName = first.document.locationName,
          address = first.document.address,
          matchingVariantCount = distinctVariantIdsInPolicyOrder(group).size,
          sampleMatchingVariantIds = distinctVariantIdsInPolicyOrder(group),
          representativeDisplayScore = displayScore(first, displayScorePolicy),
          sources = group.flatMap(_.sources).toSet,
        )
      },
      serviceIntentCandidates = serviceIntentGroups.map { group =>
        val first = group.head
        BeautyQHybridServiceIntentCandidate(
          serviceId = first.document.serviceId,
          serviceName = first.document.serviceName,
          categoryId = first.document.categoryId,
          categoryName = first.document.categoryName,
          matchingVariantCount = distinctVariantIdsInPolicyOrder(group).size,
          representativeDisplayScore = displayScore(first, displayScorePolicy),
          sources = group.flatMap(_.sources).toSet,
        )
      },
      diagnostics = BeautyQHybridProviderServiceProjectionDiagnostics(
        inputCandidateCount = projection.candidates.size,
        providerCandidateCount = providerGroups.size,
        serviceIntentCandidateCount = serviceIntentGroups.size,
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

  private def groupsInFirstOccurrenceOrder[Key](
    candidates: List[BeautyQHybridProjectedVariantCandidate]
  )(
    keyOf: BeautyQHybridProjectedVariantCandidate => Key
  ): List[List[BeautyQHybridProjectedVariantCandidate]] =
    candidates.foldLeft(List.empty[(Key, List[BeautyQHybridProjectedVariantCandidate])]) {
      case (groups, candidate) =>
        val key = keyOf(candidate)
        groups.span(_._1 != key) match {
          case (_, Nil) =>
            groups :+ (key -> List(candidate))
          case (before, (existingKey, existingCandidates) :: after) =>
            before ::: (existingKey -> (existingCandidates :+ candidate)) :: after
        }
    }.map(_._2)

  private def distinctVariantIdsInPolicyOrder(
    candidates: List[BeautyQHybridProjectedVariantCandidate]
  ): List[MasterServiceOfferVariantId] =
    candidates.foldLeft((Set.empty[MasterServiceOfferVariantId], List.empty[MasterServiceOfferVariantId])) {
      case ((seenIds, orderedIdsReverse), candidate) if seenIds(candidate.document.variantId) =>
        (seenIds, orderedIdsReverse)
      case ((seenIds, orderedIdsReverse), candidate) =>
        (seenIds + candidate.document.variantId, candidate.document.variantId :: orderedIdsReverse)
    }._2.reverse
}
