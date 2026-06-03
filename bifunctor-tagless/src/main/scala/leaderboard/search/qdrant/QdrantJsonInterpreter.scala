package leaderboard.search.qdrant

import io.circe.syntax.*
import io.circe.{Json, JsonObject}
import leaderboard.search.dsl.{EmbeddingSpec, VectorDistance, VectorSearchSpec}

object QdrantJsonInterpreter {
  def createCollectionJson(spec: VectorSearchSpec, embeddingSpec: EmbeddingSpec[?]): Json =
    Json.obj(
      "vectors" -> Json.obj(
        spec.vectorName -> Json.obj(
          "size" -> embeddingSpec.dimension.asJson,
          "distance" -> qdrantDistance(embeddingSpec.distance).asJson,
        )
      )
    )

  def searchRequestJson(spec: VectorSearchSpec, queryVector: List[Double]): Json =
    Json.fromJsonObject(
      JsonObject.fromIterable(
        List(
          "vector" -> Json.obj(
            "name" -> Json.fromString(spec.vectorName),
            "vector" -> Json.arr(queryVector.map(Json.fromDoubleOrNull): _*),
          ),
          "limit" -> Json.fromInt(spec.topK),
          "with_payload" -> Json.True,
        ) ++ spec.scoreThreshold.toList.map(scoreThreshold => "score_threshold" -> Json.fromDoubleOrNull(scoreThreshold))
      )
    )

  def upsertPointJson(id: String, vectorName: String, vector: List[Double], payload: Map[String, Json]): Json =
    Json.obj(
      "points" -> Json.arr(
        Json.obj(
          "id" -> Json.fromString(id),
          "vector" -> Json.obj(
            vectorName -> Json.arr(vector.map(Json.fromDoubleOrNull): _*),
          ),
          "payload" -> Json.fromJsonObject(JsonObject.fromIterable(payload)),
        )
      ),
    )

  private def qdrantDistance(distance: VectorDistance): String =
    distance match {
      case VectorDistance.Cosine => "Cosine"
      case VectorDistance.Dot => "Dot"
      case VectorDistance.Euclidean => "Euclid"
    }
}
