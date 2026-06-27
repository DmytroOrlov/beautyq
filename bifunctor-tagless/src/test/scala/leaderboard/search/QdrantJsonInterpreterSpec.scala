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
          "variant-embedding" -> Json.obj(
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
          "managedBootstrapFingerprintVersion" -> "beautyq-managed-local-search-bootstrap-fingerprint-v1".asJson,
        ),
      )

      assert(result == Json.obj(
        "vectors" -> Json.obj(
          "variant-embedding" -> Json.obj(
            "size" -> 1024.asJson,
            "distance" -> "Cosine".asJson,
          )
        ),
        "metadata" -> Json.obj(
          "managedBootstrapFingerprint" -> "abc123".asJson,
          "managedBootstrapFingerprintVersion" -> "beautyq-managed-local-search-bootstrap-fingerprint-v1".asJson,
        ),
      ))
    }
  }

  private val vectorSpec = VectorSearchSpec(
    collectionName = "beauty_variant_v1_local_llama_cpp_embedding_variant_embedding_1024_cosine",
    vectorName = "variant-embedding",
    topK = 10,
    scoreThreshold = None,
  )

  private val embeddingSpec = EmbeddingSpec[Any](
    vectorName = "variant-embedding",
    modelName = "local-llama-cpp-embedding",
    dimension = 1024,
    distance = VectorDistance.Cosine,
    sourceTextFieldPaths = Nil,
  )
}
