package leaderboard.search.qdrant

import io.circe.Decoder
import io.circe.Json
import leaderboard.model.QueryFailure
import leaderboard.search.dsl.SearchField
import leaderboard.search.semantic.SemanticDocumentHit

object QdrantCandidateHitDecoder {
  private val OperationName = "decode-qdrant-candidate-hits"

  def decode[A, Id: Decoder](
    hits: List[QdrantSearchHit],
    documentIdPayloadField: SearchField[A],
  ): Either[QueryFailure, List[SemanticDocumentHit[Id]]] =
    hits.foldLeft[Either[QueryFailure, List[SemanticDocumentHit[Id]]]](Right(Nil)) {
      case (Right(decoded), hit) =>
        decodeDocumentId(hit, documentIdPayloadField).map(documentId => SemanticDocumentHit(documentId = documentId, score = hit.score) :: decoded)
      case (left @ Left(_), _) =>
        left
    }.map(_.reverse)

  private def decodeDocumentId[A, Id: Decoder](
    hit: QdrantSearchHit,
    documentIdPayloadField: SearchField[A],
  ): Either[QueryFailure, Id] =
    hit.payload(documentIdPayloadField.path)
      .toRight(missingDocumentId(hit, documentIdPayloadField))
      .flatMap(json => json.as[Id].left.map(_ => invalidDocumentId(hit, json, documentIdPayloadField)))

  private def missingDocumentId[A](hit: QdrantSearchHit, documentIdPayloadField: SearchField[A]): QueryFailure =
    QueryFailure.operation(OperationName, s"Missing ${payloadFieldLabel(documentIdPayloadField)} for Qdrant hit ${hit.id}")

  private def invalidDocumentId[A](hit: QdrantSearchHit, json: Json, documentIdPayloadField: SearchField[A]): QueryFailure =
    QueryFailure.operation(OperationName, s"Invalid ${payloadFieldLabel(documentIdPayloadField)} for Qdrant hit ${hit.id}: ${json.noSpaces}")

  private def payloadFieldLabel[A](field: SearchField[A]): String =
    s"payload.${field.path}"
}
