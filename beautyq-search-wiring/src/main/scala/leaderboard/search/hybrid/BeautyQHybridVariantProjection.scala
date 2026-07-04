package leaderboard.search.hybrid

import leaderboard.model.{MasterServiceOfferVariantId, QueryFailure}
import leaderboard.search.document.VariantSearchDocument

final case class BeautyQHybridProjectedVariantCandidate(
  document: VariantSearchDocument,
  lexicalScore: Option[Double],
  semanticScore: Option[Double],
  sources: Set[BeautyQHybridCandidateSource],
)

final case class BeautyQHybridVariantProjectionDiagnostics(
  inputCandidateCount: Int,
  projectedCandidateCount: Int,
  missingDocumentIds: List[MasterServiceOfferVariantId],
)

final case class BeautyQHybridVariantProjectionResult(
  candidates: List[BeautyQHybridProjectedVariantCandidate],
  diagnostics: BeautyQHybridVariantProjectionDiagnostics,
)

object BeautyQHybridVariantProjection {
  private val OperationName = "beautyq-hybrid-variant-projection"

  def project(
    policyResult: BeautyQHybridProjectionPolicyResult,
    documentsByVariantId: Map[MasterServiceOfferVariantId, VariantSearchDocument],
  ): Either[QueryFailure, BeautyQHybridVariantProjectionResult] = {
    val missingDocumentIds = distinctInPolicyOrder(
      policyResult.candidates.collect {
        case candidate if !documentsByVariantId.contains(candidate.variantId) =>
          candidate.variantId
      }
    )

    if (missingDocumentIds.nonEmpty) {
      Left(
        QueryFailure.operation(
          OperationName,
          s"Missing VariantSearchDocument for hybrid candidate variant id(s): ${missingDocumentIds.mkString(", ")}",
        )
      )
    } else {
      val projectedCandidates = policyResult.candidates.map { candidate =>
        BeautyQHybridProjectedVariantCandidate(
          document = documentsByVariantId(candidate.variantId),
          lexicalScore = candidate.lexicalScore,
          semanticScore = candidate.semanticScore,
          sources = candidate.sources,
        )
      }

      Right(
        BeautyQHybridVariantProjectionResult(
          candidates = projectedCandidates,
          diagnostics = BeautyQHybridVariantProjectionDiagnostics(
            inputCandidateCount = policyResult.candidates.size,
            projectedCandidateCount = projectedCandidates.size,
            missingDocumentIds = Nil,
          ),
        )
      )
    }
  }

  private def distinctInPolicyOrder(ids: List[MasterServiceOfferVariantId]): List[MasterServiceOfferVariantId] =
    ids.foldLeft((Set.empty[MasterServiceOfferVariantId], List.empty[MasterServiceOfferVariantId])) {
      case ((seenIds, orderedIdsReverse), id) if seenIds(id) =>
        (seenIds, orderedIdsReverse)
      case ((seenIds, orderedIdsReverse), id) =>
        (seenIds + id, id :: orderedIdsReverse)
    }._2.reverse
}
