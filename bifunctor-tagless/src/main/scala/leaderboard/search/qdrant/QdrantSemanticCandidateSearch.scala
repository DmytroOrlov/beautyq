package leaderboard.search.qdrant

import leaderboard.model.QueryFailure
import leaderboard.search.dsl.VectorSearchSpec
import leaderboard.search.embedding.EmbeddingClient
import zio.{IO, ZIO}

final class QdrantSemanticCandidateSearch(
  embeddingClient: EmbeddingClient,
  qdrantSearchClient: QdrantSearchClient,
) {
  def search(queryText: String, spec: VectorSearchSpec): IO[QueryFailure, List[QdrantCandidateHit]] =
    for {
      vector <- embeddingClient.embed(queryText)
      requestJson = QdrantJsonInterpreter.searchRequestJson(spec, vector.toList)
      hits <- qdrantSearchClient.search(s"/collections/${spec.collectionName}/points/search", requestJson)
      decoded <- ZIO.fromEither(QdrantCandidateHitDecoder.decode(hits))
    } yield decoded
}
