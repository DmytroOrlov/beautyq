package leaderboard.search.qdrant

import io.circe.Json
import leaderboard.model.{MasterServiceOfferVariantId, QueryFailure}

object QdrantCandidateHitDecoder {
  private val OperationName = "decode-qdrant-candidate-hits"

  def decode(hits: List[QdrantSearchHit]): Either[QueryFailure, List[QdrantCandidateHit]] =
    hits.foldLeft[Either[QueryFailure, List[QdrantCandidateHit]]](Right(Nil)) {
      case (Right(decoded), hit) =>
        decodeVariantId(hit).map(variantId => QdrantCandidateHit(variantId = variantId, score = hit.score) :: decoded)
      case (left @ Left(_), _) =>
        left
    }.map(_.reverse)

  private def decodeVariantId(hit: QdrantSearchHit): Either[QueryFailure, MasterServiceOfferVariantId] =
    hit.payload("variantId")
      .toRight(missingVariantId(hit))
      .flatMap(json => json.as[MasterServiceOfferVariantId].left.map(_ => invalidVariantId(hit, json)))

  private def missingVariantId(hit: QdrantSearchHit): QueryFailure =
    QueryFailure.operation(OperationName, s"Missing payload.variantId for Qdrant hit ${hit.id}")

  private def invalidVariantId(hit: QdrantSearchHit, json: Json): QueryFailure =
    QueryFailure.operation(OperationName, s"Invalid payload.variantId for Qdrant hit ${hit.id}: ${json.noSpaces}")
}
