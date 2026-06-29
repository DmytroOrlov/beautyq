package leaderboard.search.qdrant

import io.circe.Json
import leaderboard.model.{MasterServiceOfferVariantId, QueryFailure}
import leaderboard.search.document.VariantSearchDocument
import leaderboard.search.dsl.SearchField

object QdrantCandidateHitDecoder {
  private val OperationName = "decode-qdrant-candidate-hits"

  def decode(
    hits: List[QdrantSearchHit],
    variantIdPayloadField: SearchField[VariantSearchDocument],
  ): Either[QueryFailure, List[QdrantCandidateHit]] =
    hits.foldLeft[Either[QueryFailure, List[QdrantCandidateHit]]](Right(Nil)) {
      case (Right(decoded), hit) =>
        decodeVariantId(hit, variantIdPayloadField).map(variantId => QdrantCandidateHit(variantId = variantId, score = hit.score) :: decoded)
      case (left @ Left(_), _) =>
        left
    }.map(_.reverse)

  private def decodeVariantId(
    hit: QdrantSearchHit,
    variantIdPayloadField: SearchField[VariantSearchDocument],
  ): Either[QueryFailure, MasterServiceOfferVariantId] =
    hit.payload(variantIdPayloadField.path)
      .toRight(missingVariantId(hit, variantIdPayloadField))
      .flatMap(json => json.as[MasterServiceOfferVariantId].left.map(_ => invalidVariantId(hit, json, variantIdPayloadField)))

  private def missingVariantId(hit: QdrantSearchHit, variantIdPayloadField: SearchField[VariantSearchDocument]): QueryFailure =
    QueryFailure.operation(OperationName, s"Missing ${payloadFieldLabel(variantIdPayloadField)} for Qdrant hit ${hit.id}")

  private def invalidVariantId(hit: QdrantSearchHit, json: Json, variantIdPayloadField: SearchField[VariantSearchDocument]): QueryFailure =
    QueryFailure.operation(OperationName, s"Invalid ${payloadFieldLabel(variantIdPayloadField)} for Qdrant hit ${hit.id}: ${json.noSpaces}")

  private def payloadFieldLabel(field: SearchField[VariantSearchDocument]): String =
    s"payload.${field.path}"
}
