package leaderboard.search.qdrant

import leaderboard.model.{MasterLocationId, MasterServiceOfferVariantId, ServiceId}
import leaderboard.search.document.VariantSearchDocument

final case class QdrantCandidateHit(
  variantId: MasterServiceOfferVariantId,
  score: Double,
)

final case class QdrantVariantCandidate(
  document: VariantSearchDocument,
  score: Double,
)

final case class QdrantProviderCandidateGroup(
  masterLocationId: MasterLocationId,
  variants: List[QdrantVariantCandidate],
  bestScore: Double,
  count: Int,
)

final case class QdrantServiceCandidateGroup(
  serviceId: ServiceId,
  variants: List[QdrantVariantCandidate],
  bestScore: Double,
  count: Int,
)

final case class QdrantCandidateAssembly(
  variantCandidates: List[QdrantVariantCandidate],
  providerCandidates: List[QdrantProviderCandidateGroup],
  serviceCandidates: List[QdrantServiceCandidateGroup],
)

object QdrantCandidateAssembler {
  def assemble(hits: List[QdrantCandidateHit], documents: List[VariantSearchDocument]): QdrantCandidateAssembly = {
    val documentsById = documents.iterator.map(document => document.variantId -> document).toMap
    val variantCandidates = hits.flatMap { hit =>
      documentsById.get(hit.variantId).map(document => QdrantVariantCandidate(document, hit.score))
    }

    QdrantCandidateAssembly(
      variantCandidates = variantCandidates,
      providerCandidates = groupByMasterLocationId(variantCandidates),
      serviceCandidates = groupByServiceId(variantCandidates),
    )
  }

  private def groupByMasterLocationId(
    candidates: List[QdrantVariantCandidate],
  ): List[QdrantProviderCandidateGroup] =
    candidates
      .groupBy(_.document.masterLocationId)
      .iterator
      .map {
        case (masterLocationId, group) =>
          QdrantProviderCandidateGroup(
            masterLocationId = masterLocationId,
            variants = group,
            bestScore = group.map(_.score).max,
            count = group.size,
          )
      }
      .toList
      .sortBy(group => (-group.bestScore, -group.count, group.masterLocationId.toString))

  private def groupByServiceId(
    candidates: List[QdrantVariantCandidate],
  ): List[QdrantServiceCandidateGroup] =
    candidates
      .groupBy(_.document.serviceId)
      .iterator
      .map {
        case (serviceId, group) =>
          QdrantServiceCandidateGroup(
            serviceId = serviceId,
            variants = group,
            bestScore = group.map(_.score).max,
            count = group.size,
          )
      }
      .toList
      .sortBy(group => (-group.bestScore, -group.count, group.serviceId.toString))
}
