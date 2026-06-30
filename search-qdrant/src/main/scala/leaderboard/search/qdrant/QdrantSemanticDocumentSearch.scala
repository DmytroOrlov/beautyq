package leaderboard.search.qdrant

import io.circe.Decoder
import leaderboard.model.QueryFailure
import leaderboard.search.dsl.{SearchField, VectorSearchSpec}
import leaderboard.search.embedding.EmbeddingClient
import leaderboard.search.semantic.SemanticDocumentHit
import zio.{IO, ZIO}

final class QdrantSemanticDocumentSearch[A, Id: Decoder](
  embeddingClient: EmbeddingClient,
  qdrantSearchClient: QdrantSearchClient,
  documentIdPayloadField: SearchField[A],
) {
  def search(queryText: String, spec: VectorSearchSpec): IO[QueryFailure, List[SemanticDocumentHit[Id]]] =
    for {
      vector <- embeddingClient.embed(queryText)
      requestJson = QdrantJsonInterpreter.searchRequestJson(spec, vector.toList)
      hits <- qdrantSearchClient.search(s"/collections/${spec.collectionName}/points/search", requestJson)
      decoded <- ZIO.fromEither(QdrantCandidateHitDecoder.decode[A, Id](hits, documentIdPayloadField))
    } yield decoded
}
