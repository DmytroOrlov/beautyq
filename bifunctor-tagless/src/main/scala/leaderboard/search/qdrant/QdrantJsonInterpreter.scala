package leaderboard.search.qdrant

import io.circe.syntax.*
import io.circe.{Json, JsonObject}
import leaderboard.search.dsl.{EmbeddingSpec, VectorDistance, VectorSearchSpec}

object QdrantJsonInterpreter {
  def createCollectionJson(
    spec: VectorSearchSpec,
    embeddingSpec: EmbeddingSpec[?],
    collectionMetadata: JsonObject = JsonObject.empty,
  ): Json = {
    val base = JsonObject(
      "vectors" -> Json.obj(
        spec.vectorName -> Json.obj(
          "size" -> embeddingSpec.dimension.asJson,
          "distance" -> qdrantDistance(embeddingSpec.distance).asJson,
        )
      )
    )
    Json.fromJsonObject(
      if (collectionMetadata.isEmpty) base
      else base.add("metadata", Json.fromJsonObject(collectionMetadata))
    )
  }

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
    upsertPointJson(Json.fromString(id), vectorName, vector, payload)

  def upsertPointJson(id: QdrantPointId, vectorName: String, vector: List[Double], payload: Map[String, Json]): Json =
    upsertPointJson(id.asJson, vectorName, vector, payload)

  private def upsertPointJson(id: Json, vectorName: String, vector: List[Double], payload: Map[String, Json]): Json =
    Json.obj(
      "points" -> Json.arr(
        Json.obj(
          "id" -> id,
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
