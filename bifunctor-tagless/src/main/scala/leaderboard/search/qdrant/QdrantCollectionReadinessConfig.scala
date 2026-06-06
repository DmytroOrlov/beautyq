package leaderboard.search.qdrant

import leaderboard.search.dsl.{EmbeddingSpec, VectorSearchSpec}

final case class QdrantCollectionReadinessInput(
  domainName: String,
  searchSpecVersion: String,
  purpose: String,
  embeddingSpec: EmbeddingSpec[?],
  vectorSearchSpec: VectorSearchSpec,
)

final case class QdrantCollectionReadinessConfig(
  collectionName: String,
  vectorSearchSpec: VectorSearchSpec,
  compatibilityExpectation: QdrantCollectionCompatibilityExpectation,
)

object QdrantCollectionReadinessConfig {
  def derive(input: QdrantCollectionReadinessInput): QdrantCollectionReadinessConfig = {
    val collectionName = QdrantCollectionIdentity.renderCollectionName(QdrantCollectionIdentityInput(
      domainName = input.domainName,
      searchSpecVersion = input.searchSpecVersion,
      purpose = input.purpose,
      embeddingModelName = input.embeddingSpec.modelName,
      vectorName = input.vectorSearchSpec.vectorName,
      vectorDimension = input.embeddingSpec.dimension,
      distance = input.embeddingSpec.distance,
    ))
    val vectorSearchSpec = input.vectorSearchSpec.copy(collectionName = collectionName)

    QdrantCollectionReadinessConfig(
      collectionName = collectionName,
      vectorSearchSpec = vectorSearchSpec,
      compatibilityExpectation = QdrantCollectionIdentity.compatibilityExpectation(
        embeddingSpec = input.embeddingSpec,
        vectorSearchSpec = vectorSearchSpec,
      ),
    )
  }
}
