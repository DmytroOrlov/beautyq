package leaderboard.search

import leaderboard.search.dsl.{EmbeddingSpec, VectorDistance, VectorSearchSpec}
import leaderboard.search.qdrant.{QdrantCollectionIdentity, QdrantCollectionIdentityInput, QdrantCollectionReadinessConfig, QdrantCollectionReadinessInput}
import org.scalatest.wordspec.AnyWordSpec

final class QdrantCollectionReadinessConfigSpec extends AnyWordSpec {
  "QdrantCollectionReadinessConfig" should {
    "derive deterministic collectionName" in {
      val first = QdrantCollectionReadinessConfig.derive(readinessInput)
      val second = QdrantCollectionReadinessConfig.derive(readinessInput)
      val expected = QdrantCollectionIdentity.renderCollectionName(identityInput)

      assert(first.collectionName == second.collectionName)
      assert(first.collectionName == expected)
      assert(first.collectionName == "generic_document_v1_local_generic_embedding_document_embedding_1024_cosine")
    }

    "set VectorSearchSpec collectionName to derived collectionName" in {
      val config = QdrantCollectionReadinessConfig.derive(readinessInput)

      assert(config.vectorSearchSpec.collectionName == config.collectionName)
    }

    "set compatibilityExpectation collectionName to derived collectionName" in {
      val config = QdrantCollectionReadinessConfig.derive(readinessInput)

      assert(config.compatibilityExpectation.collectionName == config.collectionName)
    }

    "derive compatibility expectation from vector search and embedding inputs" in {
      val config = QdrantCollectionReadinessConfig.derive(readinessInput)

      assert(config.compatibilityExpectation.vectorName == vectorSearchSpec.vectorName)
      assert(config.compatibilityExpectation.expectedDimension == embeddingSpec.dimension)
      assert(config.compatibilityExpectation.expectedDistance == embeddingSpec.distance)
      assert(config.compatibilityExpectation.embeddingModelName == embeddingSpec.modelName)
    }

    "change collectionName when embedding model changes" in {
      val changed = QdrantCollectionReadinessConfig.derive(readinessInput.copy(
        embeddingSpec = embeddingSpec.copy(modelName = "qwen3-embedding-0.6b"),
      ))

      assert(changed.collectionName != QdrantCollectionReadinessConfig.derive(readinessInput).collectionName)
    }

    "change collectionName when dimension changes" in {
      val changed = QdrantCollectionReadinessConfig.derive(readinessInput.copy(
        embeddingSpec = embeddingSpec.copy(dimension = 768),
      ))

      assert(changed.collectionName != QdrantCollectionReadinessConfig.derive(readinessInput).collectionName)
    }

    "change collectionName when distance changes" in {
      val changed = QdrantCollectionReadinessConfig.derive(readinessInput.copy(
        embeddingSpec = embeddingSpec.copy(distance = VectorDistance.Dot),
      ))

      assert(changed.collectionName != QdrantCollectionReadinessConfig.derive(readinessInput).collectionName)
    }

    "normalize purpose suffix through existing identity renderer" in {
      val config = QdrantCollectionReadinessConfig.derive(readinessInput.copy(purpose = "Local / Exp!"))

      assert(config.collectionName == "generic_document_v1_local_exp_generic_embedding_document_embedding_1024_cosine")
      assert(config.collectionName.forall(character => character.isLower || character.isDigit || character == '_'))
    }

    "derive without Qdrant or embedding calls" in {
      val config = QdrantCollectionReadinessConfig.derive(readinessInput)

      assert(config.vectorSearchSpec.topK == vectorSearchSpec.topK)
      assert(config.vectorSearchSpec.scoreThreshold == vectorSearchSpec.scoreThreshold)
      assert(config.compatibilityExpectation.expectedDimension == embeddingSpec.dimension)
    }
  }

  private val embeddingSpec: EmbeddingSpec[Any] =
    EmbeddingSpec[Any](
      vectorName = "embedding-spec-vector-name-is-not-identity-source",
      modelName = "generic-embedding",
      dimension = 1024,
      distance = VectorDistance.Cosine,
      sourceTextFields = Nil,
    )

  private val vectorSearchSpec: VectorSearchSpec =
    VectorSearchSpec(
      collectionName = "placeholder_collection",
      vectorName = "document-embedding",
      topK = 20,
      scoreThreshold = Some(0.2),
    )

  private val readinessInput: QdrantCollectionReadinessInput =
    QdrantCollectionReadinessInput(
      domainName = "generic_document",
      searchSpecVersion = "v1",
      purpose = "local",
      embeddingSpec = embeddingSpec,
      vectorSearchSpec = vectorSearchSpec,
    )

  private val identityInput: QdrantCollectionIdentityInput =
    QdrantCollectionIdentityInput(
      domainName = readinessInput.domainName,
      searchSpecVersion = readinessInput.searchSpecVersion,
      purpose = readinessInput.purpose,
      embeddingModelName = embeddingSpec.modelName,
      vectorName = vectorSearchSpec.vectorName,
      vectorDimension = embeddingSpec.dimension,
      distance = embeddingSpec.distance,
    )
}
