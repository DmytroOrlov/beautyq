package leaderboard.search.qdrant

import io.circe.Json
import leaderboard.model.QueryFailure

trait QdrantDocumentPointBuilder[A] {
  def qdrantPointId(document: A): Either[QueryFailure, QdrantPointId]

  def payload(document: A): Map[String, Json]

  final def validatedUpsertPointJson(document: A, vectorName: String, vector: List[Double]): Either[QueryFailure, Json] =
    qdrantPointId(document).map { pointId =>
      QdrantJsonInterpreter.upsertPointJson(pointId, vectorName, vector, payload(document))
    }
}
