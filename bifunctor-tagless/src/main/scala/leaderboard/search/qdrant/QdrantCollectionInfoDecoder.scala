package leaderboard.search.qdrant

import io.circe.{ACursor, Json, JsonObject}
import leaderboard.model.QueryFailure
import leaderboard.search.dsl.VectorDistance

object QdrantCollectionInfoDecoder {
  private val OperationName = "decode-qdrant-collection-info"

  def decode(
    collectionName: String,
    vectorName: String,
    json: Json,
  ): Either[QueryFailure, ObservedQdrantVectorConfig] =
    for {
      vectors <- vectorsCursor(json).toRight(failure("Missing params.vectors in Qdrant collection info"))
      vector <- vectors.downField(vectorName).success.toRight(failure(s"Missing selected vector '$vectorName' in Qdrant collection info"))
      dimension <- vector.downField("size").as[Int].left.map(_ => failure(s"Missing size for Qdrant vector '$vectorName'"))
      distanceText <- vector.downField("distance").as[String].left.map(_ => failure(s"Missing distance for Qdrant vector '$vectorName'"))
      distance <- decodeDistance(distanceText)
    } yield ObservedQdrantVectorConfig(
      collectionName = collectionName,
      vectorName = vectorName,
      dimension = dimension,
      distance = distance,
      embeddingModelName = None,
    )

  def metadata(json: Json): Option[JsonObject] =
    List(
      json.hcursor.downField("result").downField("metadata").focus,
      json.hcursor.downField("result").downField("config").downField("metadata").focus,
      json.hcursor.downField("metadata").focus,
    ).flatten.flatMap(_.asObject).headOption

  private def vectorsCursor(json: Json): Option[ACursor] = {
    val root = json.hcursor
    List(
      root.downField("result").downField("config").downField("params").downField("vectors"),
      root.downField("config").downField("params").downField("vectors"),
      root.downField("params").downField("vectors"),
    ).find(_.success.exists(_.focus.exists(_.isObject)))
  }

  private def decodeDistance(value: String): Either[QueryFailure, VectorDistance] =
    value match {
      case "Cosine" => Right(VectorDistance.Cosine)
      case "Dot" => Right(VectorDistance.Dot)
      case "Euclid" => Right(VectorDistance.Euclidean)
      case other => Left(failure(s"Unknown Qdrant vector distance '$other'"))
    }

  private def failure(message: String): QueryFailure =
    QueryFailure.operation(OperationName, message)
}
