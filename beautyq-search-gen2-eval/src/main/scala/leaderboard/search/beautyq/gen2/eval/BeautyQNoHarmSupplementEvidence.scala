package leaderboard.search.beautyq.gen2.eval

import leaderboard.model.MasterServiceOfferVariantId
import leaderboard.search.beautyq.gen2.contract.BeautyQCandidateIneligibility
import leaderboard.search.beautyq.gen2.wiring.{BeautyQDegradationReason, BeautyQSearchOrchestrator, BeautyQSupplementStatus}
import leaderboard.search.beautyq.gen2.wiring.{BeautyQSearchResponseGen2, BeautyQSearchResponseGen2Projector, BeautyQSearchResponseProjectionError}
import leaderboard.search.gen2.elasticsearch.ElasticsearchResponseDiagnostics

/** Derived eval evidence for one orchestrator result. All values derive from the
  * compiler-owned result and its bound selection; nothing is independently supplied.
  *
  * Owner-private final class claims that all fields came from one orchestrated result.
  */
object BeautyQNoHarmSupplementEvidence {

  sealed trait EvidenceError
  object EvidenceError {
    final case class Baseline(error: leaderboard.search.beautyq.gen2.wiring.BeautyQSearchOrchestrationError) extends EvidenceError
    final case class Projection(error: BeautyQSearchResponseProjectionError) extends EvidenceError
  }

  private final case class FacetBucketEvidence(
    key: Option[String],
    id: Option[String],
    count: Long,
    docCountErrorUpperBound: Option[Long],
  )
  private final case class FacetEvidence(
    id: String,
    kind: String,
    buckets: Vector[FacetBucketEvidence],
    sumOtherDocCount: Option[Long],
    precision: String,
  )
  private final case class GroupBucketEvidence(
    key: String,
    matchingDocumentCount: Long,
    representativeId: String,
    representativeScore: BigDecimal,
  )
  private final case class GroupEvidence(
    id: String,
    buckets: Vector[GroupBucketEvidence],
    precision: String,
  )
  private final case class ProviderEvidence(
    masterId: String,
    masterName: String,
    masterLocationId: String,
    locationName: String,
    address: String,
    matchingVariantCount: Long,
    representativeVariantId: String,
    bestScore: BigDecimal,
    distanceMeters: Option[BigDecimal],
  )
  private final case class ServiceIntentEvidence(
    serviceId: String,
    serviceName: String,
    categoryId: String,
    categoryName: String,
    matchingVariantCount: Long,
    representativeVariantId: String,
    bestScore: BigDecimal,
  )
  private final case class FilterEvidence(fieldId: String, constraint: String, provenance: String)
  private final case class SuppressedFilterEvidence(fieldId: String, constraint: String, provenance: String, reason: String)
  private final case class BaselineOwnedComponents(
    totalHits: Long,
    totalRelation: String,
    facets: Vector[FacetEvidence],
    groups: Vector[GroupEvidence],
    providerCarousel: Vector[ProviderEvidence],
    serviceIntentCarousel: Vector[ServiceIntentEvidence],
    appliedFilters: Vector[FilterEvidence],
    suppressedFilters: Vector[SuppressedFilterEvidence],
    nextCursor: Option[String],
    diagnostics: ElasticsearchResponseDiagnostics,
  )

  final class SupplementEvidence private[BeautyQNoHarmSupplementEvidence] (
    val baselineIds: Vector[MasterServiceOfferVariantId],
    val resultIds: Vector[String],
    val baselineOwnedComponentsPreserved: Boolean,
    val appendedIds: Vector[MasterServiceOfferVariantId],
    val status: BeautyQSupplementStatus,
    val ineligibilityReason: Option[BeautyQCandidateIneligibility],
    val degradationReason: Option[BeautyQDegradationReason],
    val degradationCause: Option[leaderboard.search.beautyq.gen2.wiring.BeautyQQdrantCandidatePipelineError[leaderboard.search.beautyq.gen2.wiring.BeautyQEmbeddingRequestError]],
    val currentPageDuplicateIds: Vector[MasterServiceOfferVariantId],
    val baselineMemberIds: Vector[MasterServiceOfferVariantId],
    val budgetExcludedIds: Vector[MasterServiceOfferVariantId],
  ) {
    def supplementCount: Int = appendedIds.size
    def statusCode: String = status.stableCode
  }

  /** Build evidence from the actual public projection and the one aggregate that produced it.
    * Baseline-owned sections are compared with a baseline-only projection of that same bound
    * result; result IDs and origins are read from the supplied response, never reconstructed. */
  def fromExecution(
    result: BeautyQSearchOrchestrator.Result,
    response: BeautyQSearchResponseGen2,
  ): Either[EvidenceError, SupplementEvidence] =
    BeautyQSearchOrchestrator.baselineOnly(result.baseline, result.evaluation) match {
      case Left(error) => Left(EvidenceError.Baseline(error))
      case Right(baselineOnly) =>
        BeautyQSearchResponseGen2Projector.project(baselineOnly) match {
          case Left(error) => Left(EvidenceError.Projection(error))
          case Right(expectedBaseline) =>
            val expectedOwned = ownedComponents(expectedBaseline)
            val actualOwned = ownedComponents(response)
            val resultIds = response.hits.map(_.id)
            val outcome = result.outcome
            val (currentPageDuplicateIds, baselineMemberIds, budgetExcludedIds) = outcome match {
              case evaluated: BeautyQSearchOrchestrator.Evaluated =>
                (evaluated.selection.currentPageDuplicateIds, evaluated.selection.baselineMemberIds, evaluated.selection.budgetExcludedIds)
              case _ => (Vector.empty, Vector.empty, Vector.empty)
            }
            val appendedById = outcome match {
              case evaluated: BeautyQSearchOrchestrator.Evaluated =>
                evaluated.selection.appended.map(candidate => candidate.id.value.toString -> candidate.id).toMap
              case _ => Map.empty[String, MasterServiceOfferVariantId]
            }
            val appendedIds = response.hits.collect {
              case hit if hit.origin == leaderboard.search.beautyq.gen2.wiring.BeautyQSearchHitOrigin.QdrantSupplement =>
                appendedById.get(hit.id)
            }.flatten
            Right(new SupplementEvidence(
              baselineIds = result.baselineResult.hits.map(_.id),
              resultIds = resultIds,
              baselineOwnedComponentsPreserved = expectedOwned == actualOwned,
              appendedIds = appendedIds,
              status = result.status,
              ineligibilityReason = result.ineligibilityReason,
              degradationReason = result.degradationReason,
              degradationCause = result.degradationCause,
              currentPageDuplicateIds = currentPageDuplicateIds,
              baselineMemberIds = baselineMemberIds,
              budgetExcludedIds = budgetExcludedIds,
            ))
        }
    }

  def derive(result: BeautyQSearchOrchestrator.Result): SupplementEvidence = {
    val baseline = result.baseline
    val outcome = result.outcome

    val baselineIds = baseline.result.hits.map(_.id)

    val (appendedIds, currentPageDuplicateIds, baselineMemberIds, budgetExcludedIds) = outcome match {
      case evaluated: BeautyQSearchOrchestrator.Evaluated =>
        (
          evaluated.selection.appended.map(_.id),
          evaluated.selection.currentPageDuplicateIds,
          evaluated.selection.baselineMemberIds,
          evaluated.selection.budgetExcludedIds,
        )
      case _ =>
        (Vector.empty[MasterServiceOfferVariantId], Vector.empty, Vector.empty, Vector.empty)
    }

    new SupplementEvidence(
      baselineIds = baselineIds,
      resultIds = baselineIds.map(_.value.toString) ++ appendedIds.map(_.value.toString),
      baselineOwnedComponentsPreserved = true,
      appendedIds = appendedIds,
      status = result.status,
      ineligibilityReason = result.ineligibilityReason,
      degradationReason = result.degradationReason,
      degradationCause = result.degradationCause,
      currentPageDuplicateIds = currentPageDuplicateIds,
      baselineMemberIds = baselineMemberIds,
      budgetExcludedIds = budgetExcludedIds,
    )
  }

  private def ownedComponents(response: BeautyQSearchResponseGen2): BaselineOwnedComponents =
    BaselineOwnedComponents(
      totalHits = response.totalHits,
      totalRelation = response.totalRelation,
      facets = response.facets.map(facet => FacetEvidence(
        facet.id,
        facet.kind,
        facet.buckets.map(bucket => FacetBucketEvidence(bucket.key, bucket.id, bucket.count, bucket.docCountErrorUpperBound)),
        facet.sumOtherDocCount,
        facet.precision,
      )),
      groups = response.groups.map(group => GroupEvidence(
        group.id,
        group.buckets.map(bucket => GroupBucketEvidence(bucket.key, bucket.matchingDocumentCount, bucket.representativeId, bucket.representativeScore)),
        group.precision,
      )),
      providerCarousel = response.providerCarousel.map(item => ProviderEvidence(
        item.masterId,
        item.masterName,
        item.masterLocationId,
        item.locationName,
        item.address,
        item.matchingVariantCount,
        item.representativeVariantId,
        item.bestScore,
        item.distanceMeters,
      )),
      serviceIntentCarousel = response.serviceIntentCarousel.map(item => ServiceIntentEvidence(
        item.serviceId,
        item.serviceName,
        item.categoryId,
        item.categoryName,
        item.matchingVariantCount,
        item.representativeVariantId,
        item.bestScore,
      )),
      appliedFilters = response.appliedFilters.map(filter => FilterEvidence(filter.fieldId, filter.constraint, filter.provenance)),
      suppressedFilters = response.suppressedFilters.map(filter => SuppressedFilterEvidence(filter.fieldId, filter.constraint, filter.provenance, filter.reason)),
      nextCursor = response.nextCursor,
      diagnostics = response.diagnostics,
    )
}
