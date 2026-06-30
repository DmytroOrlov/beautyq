package leaderboard.search

import io.circe.Json
import io.circe.syntax.*
import leaderboard.search.dsl.{EmbeddingSpec, VectorDistance, VectorSearchSpec}
import leaderboard.search.qdrant.QdrantJsonInterpreter
import org.scalatest.wordspec.AnyWordSpec

final class QdrantJsonInterpreterSpec extends AnyWordSpec {
  "QdrantJsonInterpreter" should {
    "keep create collection JSON unchanged by default" in {
      val result = QdrantJsonInterpreter.createCollectionJson(vectorSpec, embeddingSpec)

      assert(result == Json.obj(
        "vectors" -> Json.obj(
          "document-embedding" -> Json.obj(
            "size" -> 1024.asJson,
            "distance" -> "Cosine".asJson,
          )
        )
      ))
    }

    "include collection metadata only when explicitly supplied" in {
      val result = QdrantJsonInterpreter.createCollectionJson(
        vectorSpec,
        embeddingSpec,
        io.circe.JsonObject(
          "managedBootstrapFingerprint" -> "abc123".asJson,
          "managedBootstrapFingerprintVersion" -> "generic-bootstrap-fingerprint-v1".asJson,
        ),
      )

      assert(result == Json.obj(
        "vectors" -> Json.obj(
          "document-embedding" -> Json.obj(
            "size" -> 1024.asJson,
            "distance" -> "Cosine".asJson,
          )
        ),
        "metadata" -> Json.obj(
          "managedBootstrapFingerprint" -> "abc123".asJson,
          "managedBootstrapFingerprintVersion" -> "generic-bootstrap-fingerprint-v1".asJson,
        ),
      ))
    }
  }

  private val vectorSpec = VectorSearchSpec(
    collectionName = "generic_document_v1_local_generic_embedding_document_embedding_1024_cosine",
    vectorName = "document-embedding",
    topK = 10,
    scoreThreshold = None,
  )

  private val embeddingSpec = EmbeddingSpec[Any](
    vectorName = "document-embedding",
    modelName = "local-generic-embedding",
    dimension = 1024,
    distance = VectorDistance.Cosine,
    sourceTextFields = Nil,
  )
}
