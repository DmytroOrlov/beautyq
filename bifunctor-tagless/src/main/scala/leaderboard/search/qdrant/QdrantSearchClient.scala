package leaderboard.search.qdrant

import io.circe.Json
import leaderboard.model.QueryFailure
import zio.IO

trait QdrantSearchClient {
  def search(path: String, json: Json): IO[QueryFailure, List[QdrantSearchHit]]
}

final class QdrantClientSearchAdapter(qdrantClient: QdrantClient) extends QdrantSearchClient {
  override def search(path: String, json: Json): IO[QueryFailure, List[QdrantSearchHit]] =
    qdrantClient.search(path, json)
}
