package leaderboard.search

import io.circe.Json
import io.circe.syntax.*
import leaderboard.model.QueryFailure
import leaderboard.search.dsl.VectorDistance
import leaderboard.search.qdrant.{ObservedQdrantVectorConfig, QdrantCollectionInfoDecoder}
import org.scalatest.wordspec.AnyWordSpec

final class QdrantCollectionInfoDecoderSpec extends AnyWordSpec {
  "QdrantCollectionInfoDecoder" should {
    "decode named vector config with size and distance" in {
      val result = QdrantCollectionInfoDecoder.decode(collectionName, vectorName, collectionInfoJson(distance = "Cosine"))

      assert(result == Right(ObservedQdrantVectorConfig(
        collectionName = collectionName,
        vectorName = vectorName,
        dimension = 1024,
        distance = VectorDistance.Cosine,
        embeddingModelName = None,
      )))
    }

    "decode cosine dot and euclidean distance spelling used by Qdrant" in {
      val cases = List(
        "Cosine" -> VectorDistance.Cosine,
        "Dot" -> VectorDistance.Dot,
        "Euclid" -> VectorDistance.Euclidean,
      )

      cases.foreach { case (qdrantDistance, expectedDistance) =>
        val result = QdrantCollectionInfoDecoder.decode(collectionName, vectorName, collectionInfoJson(distance = qdrantDistance))

        assert(result.map(_.distance) == Right(expectedDistance))
      }
    }

    "fail when vectors are missing" in {
      val json = Json.obj(
        "result" -> Json.obj(
          "config" -> Json.obj(
            "params" -> Json.obj()
          )
        )
      )

      assertFailure(
        QdrantCollectionInfoDecoder.decode(collectionName, vectorName, json),
        "Missing params.vectors in Qdrant collection info",
      )
    }

    "fail when selected vector is missing" in {
      val result = QdrantCollectionInfoDecoder.decode(collectionName, "missing-vector", collectionInfoJson(distance = "Cosine"))

      assertFailure(result, "Missing selected vector 'missing-vector' in Qdrant collection info")
    }

    "fail when size is missing" in {
      val result = QdrantCollectionInfoDecoder.decode(
        collectionName,
        vectorName,
        collectionInfoJson(distance = "Cosine", includeSize = false),
      )

      assertFailure(result, s"Missing size for Qdrant vector '$vectorName'")
    }

    "fail when distance is missing" in {
      val result = QdrantCollectionInfoDecoder.decode(
        collectionName,
        vectorName,
        collectionInfoJson(distance = "Cosine", includeDistance = false),
      )

      assertFailure(result, s"Missing distance for Qdrant vector '$vectorName'")
    }

    "fail on unknown distance" in {
      val result = QdrantCollectionInfoDecoder.decode(collectionName, vectorName, collectionInfoJson(distance = "Manhattan"))

      assertFailure(result, "Unknown Qdrant vector distance 'Manhattan'")
    }

    "return embedding model name as None" in {
      val json = collectionInfoJson(distance = "Cosine").deepMerge(
        Json.obj(
          "result" -> Json.obj(
            "payload_schema" -> Json.obj(
              "embeddingModelName" -> Json.obj(
                "data_type" -> "keyword".asJson
              )
            )
          )
        )
      )

      val result = QdrantCollectionInfoDecoder.decode(collectionName, vectorName, json)

      assert(result.map(_.embeddingModelName) == Right(None))
    }
  }

  private val collectionName = "beauty_variant_v1_local_llama_cpp_embedding_variant_embedding_1024_cosine"
  private val vectorName = "variant-embedding"

  private def collectionInfoJson(
    distance: String,
    includeSize: Boolean = true,
    includeDistance: Boolean = true,
  ): Json = {
    val vectorFields = List(
      Option.when(includeSize)("size" -> 1024.asJson),
      Option.when(includeDistance)("distance" -> distance.asJson),
    ).flatten

    Json.obj(
      "result" -> Json.obj(
        "status" -> "green".asJson,
        "config" -> Json.obj(
          "params" -> Json.obj(
            "vectors" -> Json.obj(
              vectorName -> Json.obj(vectorFields: _*)
            )
          )
        ),
      )
    )
  }

  private def assertFailure(result: Either[QueryFailure, ObservedQdrantVectorConfig], expectedMessage: String): Unit =
    result match {
      case Left(QueryFailure.OperationFailure("decode-qdrant-collection-info", message)) =>
        assert(message == expectedMessage): Unit
      case other =>
        fail(s"Expected decode-qdrant-collection-info failure '$expectedMessage', got $other")
    }
}
