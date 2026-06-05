package leaderboard.search

import leaderboard.search.dsl.{EmbeddingSpec, VectorDistance, VectorSearchSpec}
import leaderboard.search.qdrant.{ObservedQdrantVectorConfig, QdrantCollectionCompatibilityExpectation, QdrantCollectionCompatibilityMismatch, QdrantCollectionIdentity, QdrantCollectionIdentityInput}
import org.scalatest.wordspec.AnyWordSpec

final class QdrantCollectionIdentitySpec extends AnyWordSpec {
  "QdrantCollectionIdentity" should {
    "render deterministic collection names" in {
      val first = identityInput.renderedCollectionName
      val second = identityInput.renderedCollectionName

      assert(first == second)
      assert(first == "beauty_variant_v1_local_llama_cpp_embedding_variant_embedding_1024_cosine")
    }

    "change rendered collection name when embedding model changes" in {
      val changed = identityInput.copy(embeddingModelName = "qwen3-embedding-0.6b").renderedCollectionName

      assert(changed != identityInput.renderedCollectionName)
    }

    "change rendered collection name when dimension changes" in {
      val changed = identityInput.copy(vectorDimension = 768).renderedCollectionName

      assert(changed != identityInput.renderedCollectionName)
    }

    "change rendered collection name when distance changes" in {
      val changed = identityInput.copy(distance = VectorDistance.Dot).renderedCollectionName

      assert(changed != identityInput.renderedCollectionName)
    }

    "normalize unsafe model vector and purpose characters" in {
      val rendered = identityInput.copy(
        purpose = "Local / Exp!",
        embeddingModelName = "LLaMA.cpp Embedding@Q8_0",
        vectorName = "Variant-Embedding:Text",
      ).renderedCollectionName

      assert(rendered == "beauty_variant_v1_local_exp_llama_cpp_embedding_q8_0_variant_embedding_text_1024_cosine")
      assert(rendered.forall(character => character.isLower || character.isDigit || character == '_'))
    }

    "derive expected compatibility from EmbeddingSpec and VectorSearchSpec" in {
      val expected = QdrantCollectionIdentity.compatibilityExpectation(embeddingSpec, vectorSearchSpec)

      assert(expected == QdrantCollectionCompatibilityExpectation(
        collectionName = "beauty_variant_v1_local_llama_cpp_embedding_variant_embedding_1024_cosine",
        vectorName = "variant-embedding",
        expectedDimension = 1024,
        expectedDistance = VectorDistance.Cosine,
        embeddingModelName = "llama-cpp-embedding",
      ))
    }

    "succeed compatibility for exact observed match" in {
      val result = QdrantCollectionIdentity.checkCompatibility(expectation, observedConfig)

      assert(result == Right(()))
    }

    "fail compatibility for dimension mismatch" in {
      val result = QdrantCollectionIdentity.checkCompatibility(expectation, observedConfig.copy(dimension = 768))

      assert(result == Left(List(QdrantCollectionCompatibilityMismatch.DimensionMismatch(1024, 768))))
    }

    "fail compatibility for vector name mismatch" in {
      val result = QdrantCollectionIdentity.checkCompatibility(expectation, observedConfig.copy(vectorName = "other-vector"))

      assert(result == Left(List(QdrantCollectionCompatibilityMismatch.VectorNameMismatch("variant-embedding", "other-vector"))))
    }

    "fail compatibility for distance mismatch" in {
      val result = QdrantCollectionIdentity.checkCompatibility(expectation, observedConfig.copy(distance = VectorDistance.Euclidean))

      assert(result == Left(List(QdrantCollectionCompatibilityMismatch.DistanceMismatch(VectorDistance.Cosine, VectorDistance.Euclidean))))
    }

    "fail compatibility for collection name mismatch" in {
      val result = QdrantCollectionIdentity.checkCompatibility(expectation, observedConfig.copy(collectionName = "other_collection"))

      assert(result == Left(List(QdrantCollectionCompatibilityMismatch.CollectionNameMismatch(expectation.collectionName, "other_collection"))))
    }

    "fail compatibility for observed embedding model mismatch" in {
      val result = QdrantCollectionIdentity.checkCompatibility(expectation, observedConfig.copy(embeddingModelName = Some("other-model")))

      assert(result == Left(List(QdrantCollectionCompatibilityMismatch.EmbeddingModelMismatch("llama-cpp-embedding", "other-model"))))
    }

    "not require observed embedding model metadata from Qdrant vector config" in {
      val result = QdrantCollectionIdentity.checkCompatibility(expectation, observedConfig.copy(embeddingModelName = None))

      assert(result == Right(()))
    }
  }

  private val identityInput: QdrantCollectionIdentityInput =
    QdrantCollectionIdentityInput(
      domainName = "beauty_variant",
      searchSpecVersion = "v1",
      purpose = "local",
      embeddingModelName = "llama-cpp-embedding",
      vectorName = "variant-embedding",
      vectorDimension = 1024,
      distance = VectorDistance.Cosine,
    )

  private val embeddingSpec: EmbeddingSpec[Any] =
    EmbeddingSpec[Any](
      vectorName = "variant-embedding",
      modelName = "llama-cpp-embedding",
      dimension = 1024,
      distance = VectorDistance.Cosine,
      sourceTextFieldPaths = List("serviceName", "allText"),
    )

  private val vectorSearchSpec: VectorSearchSpec =
    VectorSearchSpec(
      collectionName = identityInput.renderedCollectionName,
      vectorName = "variant-embedding",
      topK = 20,
      scoreThreshold = Some(0.2),
    )

  private val expectation: QdrantCollectionCompatibilityExpectation =
    QdrantCollectionIdentity.compatibilityExpectation(embeddingSpec, vectorSearchSpec)

  private val observedConfig: ObservedQdrantVectorConfig =
    ObservedQdrantVectorConfig(
      collectionName = expectation.collectionName,
      vectorName = expectation.vectorName,
      dimension = expectation.expectedDimension,
      distance = expectation.expectedDistance,
      embeddingModelName = Some(expectation.embeddingModelName),
    )
}
