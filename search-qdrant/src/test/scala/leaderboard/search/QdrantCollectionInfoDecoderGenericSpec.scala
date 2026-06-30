package leaderboard.search

import io.circe.Json
import io.circe.syntax.*
import leaderboard.model.QueryFailure
import leaderboard.search.dsl.VectorDistance
import leaderboard.search.qdrant.{ObservedQdrantVectorConfig, QdrantCollectionInfoDecoder}
import org.scalatest.wordspec.AnyWordSpec

final class QdrantCollectionInfoDecoderGenericSpec extends AnyWordSpec {
  "QdrantCollectionInfoDecoder" should {
    "decode collection vector size and distance from result config params vectors" in {
      val result = QdrantCollectionInfoDecoder.decode(collectionName, vectorName, collectionInfoJson(VectorLocation.ResultConfigParams))

      assert(result == Right(expectedConfig))
    }

    "decode collection vector size and distance from config params vectors" in {
      val result = QdrantCollectionInfoDecoder.decode(collectionName, vectorName, collectionInfoJson(VectorLocation.ConfigParams))

      assert(result == Right(expectedConfig))
    }

    "decode collection vector size and distance from params vectors" in {
      val result = QdrantCollectionInfoDecoder.decode(collectionName, vectorName, collectionInfoJson(VectorLocation.Params))

      assert(result == Right(expectedConfig))
    }

    "extract metadata from result metadata" in {
      val json = metadataJson(MetadataLocation.ResultMetadata)

      assert(QdrantCollectionInfoDecoder.metadataValue(json, metadataKey).contains(Json.fromString(metadataValue)))
    }

    "extract metadata from result config metadata" in {
      val json = metadataJson(MetadataLocation.ResultConfigMetadata)

      assert(QdrantCollectionInfoDecoder.metadataValue(json, metadataKey).contains(Json.fromString(metadataValue)))
    }

    "extract metadata from top-level metadata" in {
      val json = metadataJson(MetadataLocation.TopLevelMetadata)

      assert(QdrantCollectionInfoDecoder.metadataValue(json, metadataKey).contains(Json.fromString(metadataValue)))
    }

    "return None for missing metadata key" in {
      val json = metadataJson(MetadataLocation.ResultMetadata)

      assert(QdrantCollectionInfoDecoder.metadataValue(json, "missing-key").isEmpty)
    }

    "report missing vectors" in {
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

    "report missing selected vector" in {
      val result = QdrantCollectionInfoDecoder.decode(collectionName, "missing-vector", collectionInfoJson(VectorLocation.ResultConfigParams))

      assertFailure(result, "Missing selected vector 'missing-vector' in Qdrant collection info")
    }

    "report unknown distance" in {
      val result = QdrantCollectionInfoDecoder.decode(
        collectionName,
        vectorName,
        collectionInfoJson(VectorLocation.ResultConfigParams, distance = "Manhattan"),
      )

      assertFailure(result, "Unknown Qdrant vector distance 'Manhattan'")
    }
  }

  private val collectionName = "generic_document_v1_local_generic_embedding_document_embedding_1024_cosine"
  private val vectorName = "document-embedding"
  private val metadataKey = "genericMetadataKey"
  private val metadataValue = "generic-metadata-value"

  private val expectedConfig =
    ObservedQdrantVectorConfig(
      collectionName = collectionName,
      vectorName = vectorName,
      dimension = 1024,
      distance = VectorDistance.Cosine,
      embeddingModelName = None,
    )

  private enum VectorLocation {
    case ResultConfigParams
    case ConfigParams
    case Params
  }

  private enum MetadataLocation {
    case ResultMetadata
    case ResultConfigMetadata
    case TopLevelMetadata
  }

  private def collectionInfoJson(
    location: VectorLocation,
    distance: String = "Cosine",
  ): Json = {
    val vectorsJson = Json.obj(
      vectorName -> Json.obj(
        "size" -> 1024.asJson,
        "distance" -> distance.asJson,
      )
    )

    location match {
      case VectorLocation.ResultConfigParams =>
        Json.obj(
          "result" -> Json.obj(
            "config" -> Json.obj(
              "params" -> Json.obj(
                "vectors" -> vectorsJson
              )
            )
          )
        )
      case VectorLocation.ConfigParams =>
        Json.obj(
          "config" -> Json.obj(
            "params" -> Json.obj(
              "vectors" -> vectorsJson
            )
          )
        )
      case VectorLocation.Params =>
        Json.obj(
          "params" -> Json.obj(
            "vectors" -> vectorsJson
          )
        )
    }
  }

  private def metadataJson(location: MetadataLocation): Json = {
    val metadata = Json.obj(metadataKey -> metadataValue.asJson)

    location match {
      case MetadataLocation.ResultMetadata =>
        Json.obj(
          "result" -> Json.obj(
            "metadata" -> metadata
          )
        )
      case MetadataLocation.ResultConfigMetadata =>
        Json.obj(
          "result" -> Json.obj(
            "config" -> Json.obj(
              "metadata" -> metadata
            )
          )
        )
      case MetadataLocation.TopLevelMetadata =>
        Json.obj(
          "metadata" -> metadata
        )
    }
  }

  private def assertFailure(result: Either[QueryFailure, ObservedQdrantVectorConfig], expectedMessage: String): Unit =
    result match {
      case Left(QueryFailure.OperationFailure("decode-qdrant-collection-info", message)) =>
        assert(message == expectedMessage): Unit
      case other =>
        fail(s"Expected decode-qdrant-collection-info failure '$expectedMessage', got $other")
    }
}
