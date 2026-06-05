package leaderboard.search.qdrant

import io.circe.Json
import leaderboard.model.{MasterLocationId, MasterServiceOfferVariantId, ServiceId}
import leaderboard.model.QueryFailure
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

object QdrantCandidateHitDecoder {
  private val OperationName = "decode-qdrant-candidate-hits"

  def decode(hits: List[QdrantSearchHit]): Either[QueryFailure, List[QdrantCandidateHit]] =
    hits.foldRight[Either[QueryFailure, List[QdrantCandidateHit]]](Right(Nil)) { (hit, acc) =>
      for {
        tail <- acc
        variantId <- decodeVariantId(hit)
      } yield QdrantCandidateHit(variantId = variantId, score = hit.score) :: tail
    }

  private def decodeVariantId(hit: QdrantSearchHit): Either[QueryFailure, MasterServiceOfferVariantId] =
    hit.payload("variantId")
      .toRight(missingVariantId(hit))
      .flatMap(json => json.as[MasterServiceOfferVariantId].left.map(_ => invalidVariantId(hit, json)))

  private def missingVariantId(hit: QdrantSearchHit): QueryFailure =
    QueryFailure.operation(OperationName, s"Missing payload.variantId for Qdrant hit ${hit.id}")

  private def invalidVariantId(hit: QdrantSearchHit, json: Json): QueryFailure =
    QueryFailure.operation(OperationName, s"Invalid payload.variantId for Qdrant hit ${hit.id}: ${json.noSpaces}")
}
