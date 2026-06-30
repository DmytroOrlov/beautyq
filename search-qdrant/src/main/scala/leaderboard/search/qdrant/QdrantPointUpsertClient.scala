package leaderboard.search.qdrant

import io.circe.Json
import leaderboard.model.QueryFailure
import zio.IO

trait QdrantPointUpsertClient {
  def upsertPoint(path: String, json: Json): IO[QueryFailure, Json]
}

final class QdrantClientPointUpsertAdapter(qdrantClient: QdrantClient) extends QdrantPointUpsertClient {
  override def upsertPoint(path: String, json: Json): IO[QueryFailure, Json] =
    qdrantClient.upsertPoint(path, json)
}
