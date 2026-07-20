package leaderboard.search.beautyq.gen2.eval

import leaderboard.model.MasterServiceOfferVariantId
import leaderboard.search.beautyq.gen2.contract.BeautyQCandidateIneligibility
import leaderboard.search.beautyq.gen2.wiring.{BeautyQDegradationReason, BeautyQSearchOrchestrator, BeautyQSupplementStatus}

/** Derived eval evidence for one orchestrator result. All values derive from the
  * compiler-owned result and its bound selection; nothing is independently supplied.
  *
  * Owner-private final class claims that all fields came from one orchestrated result.
  */
object BeautyQNoHarmSupplementEvidence {

  final class SupplementEvidence private[BeautyQNoHarmSupplementEvidence] (
    val baselineIds: Vector[MasterServiceOfferVariantId],
    val appendedIds: Vector[MasterServiceOfferVariantId],
    val supplementCount: Int,
    val status: BeautyQSupplementStatus,
    val statusCode: String,
    val ineligibilityReason: Option[BeautyQCandidateIneligibility],
    val degradationReason: Option[BeautyQDegradationReason],
    val degradationCause: Option[leaderboard.search.beautyq.gen2.wiring.BeautyQQdrantCandidatePipelineError[leaderboard.search.beautyq.gen2.wiring.BeautyQEmbeddingRequestError]],
    val currentPageDuplicateIds: Vector[MasterServiceOfferVariantId],
    val baselineMemberIds: Vector[MasterServiceOfferVariantId],
    val budgetExcludedIds: Vector[MasterServiceOfferVariantId],
  )

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
      appendedIds = appendedIds,
      supplementCount = result.supplementCount,
      status = result.status,
      statusCode = result.statusCode,
      ineligibilityReason = result.ineligibilityReason,
      degradationReason = result.degradationReason,
      degradationCause = result.degradationCause,
      currentPageDuplicateIds = currentPageDuplicateIds,
      baselineMemberIds = baselineMemberIds,
      budgetExcludedIds = budgetExcludedIds,
    )
  }
}
