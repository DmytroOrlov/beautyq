package leaderboard.search.qdrant

import io.circe.Json
import leaderboard.model.QueryFailure
import zio.IO

trait QdrantCollectionInfoClient {
  def collectionInfo(path: String): IO[QueryFailure, Json]
}

final class QdrantClientCollectionInfoAdapter(qdrantClient: QdrantClient) extends QdrantCollectionInfoClient {
  override def collectionInfo(path: String): IO[QueryFailure, Json] =
    qdrantClient.collectionInfo(path)
}
