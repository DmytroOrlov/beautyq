package leaderboard.search.qdrant

import leaderboard.search.dsl.{EmbeddingSpec, VectorDistance, VectorSearchSpec}

final case class QdrantCollectionIdentityInput(
  domainName: String,
  searchSpecVersion: String,
  purpose: String,
  embeddingModelName: String,
  vectorName: String,
  vectorDimension: Int,
  distance: VectorDistance,
) {
  def renderedCollectionName: String =
    QdrantCollectionIdentity.renderCollectionName(this)
}

final case class QdrantCollectionCompatibilityExpectation(
  collectionName: String,
  vectorName: String,
  expectedDimension: Int,
  expectedDistance: VectorDistance,
  embeddingModelName: String,
)

final case class ObservedQdrantVectorConfig(
  collectionName: String,
  vectorName: String,
  dimension: Int,
  distance: VectorDistance,
  embeddingModelName: Option[String] = None,
)

sealed trait QdrantCollectionCompatibilityMismatch extends Product with Serializable
object QdrantCollectionCompatibilityMismatch {
  final case class CollectionNameMismatch(expected: String, observed: String) extends QdrantCollectionCompatibilityMismatch
  final case class VectorNameMismatch(expected: String, observed: String) extends QdrantCollectionCompatibilityMismatch
  final case class DimensionMismatch(expected: Int, observed: Int) extends QdrantCollectionCompatibilityMismatch
  final case class DistanceMismatch(expected: VectorDistance, observed: VectorDistance) extends QdrantCollectionCompatibilityMismatch
  final case class EmbeddingModelMismatch(expected: String, observed: String) extends QdrantCollectionCompatibilityMismatch
}

object QdrantCollectionIdentity {
  def renderCollectionName(input: QdrantCollectionIdentityInput): String =
    List(
      input.domainName,
      input.searchSpecVersion,
      input.purpose,
      input.embeddingModelName,
      input.vectorName,
      input.vectorDimension.toString,
      renderDistance(input.distance),
    ).map(safeToken).mkString("_")

  def compatibilityExpectation(
    embeddingSpec: EmbeddingSpec[?],
    vectorSearchSpec: VectorSearchSpec,
  ): QdrantCollectionCompatibilityExpectation =
    QdrantCollectionCompatibilityExpectation(
      collectionName = vectorSearchSpec.collectionName,
      vectorName = vectorSearchSpec.vectorName,
      expectedDimension = embeddingSpec.dimension,
      expectedDistance = embeddingSpec.distance,
      embeddingModelName = embeddingSpec.modelName,
    )

  def checkCompatibility(
    expected: QdrantCollectionCompatibilityExpectation,
    observed: ObservedQdrantVectorConfig,
  ): Either[List[QdrantCollectionCompatibilityMismatch], Unit] = {
    val mismatches = List(
      Option.when(expected.collectionName != observed.collectionName)(
        QdrantCollectionCompatibilityMismatch.CollectionNameMismatch(expected.collectionName, observed.collectionName)
      ),
      Option.when(expected.vectorName != observed.vectorName)(
        QdrantCollectionCompatibilityMismatch.VectorNameMismatch(expected.vectorName, observed.vectorName)
      ),
      Option.when(expected.expectedDimension != observed.dimension)(
        QdrantCollectionCompatibilityMismatch.DimensionMismatch(expected.expectedDimension, observed.dimension)
      ),
      Option.when(expected.expectedDistance != observed.distance)(
        QdrantCollectionCompatibilityMismatch.DistanceMismatch(expected.expectedDistance, observed.distance)
      ),
      observed.embeddingModelName.filter(_ != expected.embeddingModelName).map { observedModelName =>
        QdrantCollectionCompatibilityMismatch.EmbeddingModelMismatch(expected.embeddingModelName, observedModelName)
      },
    ).flatten

    if (mismatches.isEmpty) Right(()) else Left(mismatches)
  }

  private def safeToken(value: String): String = {
    val rendered = value.toLowerCase
      .replaceAll("[^a-z0-9]+", "_")
      .replaceAll("_+", "_")
      .stripPrefix("_")
      .stripSuffix("_")

    if (rendered.isEmpty) "x" else rendered
  }

  private def renderDistance(distance: VectorDistance): String =
    distance match {
      case VectorDistance.Cosine => "cosine"
      case VectorDistance.Dot => "dot"
      case VectorDistance.Euclidean => "euclidean"
    }
}
