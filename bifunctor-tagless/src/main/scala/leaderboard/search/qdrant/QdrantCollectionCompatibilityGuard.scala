package leaderboard.search.qdrant

import leaderboard.model.QueryFailure
import zio.{IO, ZIO}

final class QdrantCollectionCompatibilityGuard(checker: QdrantCollectionCompatibilityChecker) {
  def requireCompatible(expected: QdrantCollectionCompatibilityExpectation): IO[QueryFailure, Unit] =
    checker.check(expected).flatMap {
      case Right(()) =>
        ZIO.unit
      case Left(mismatches) =>
        ZIO.fail(QueryFailure.operation(
          QdrantCollectionCompatibilityGuard.OperationName,
          renderFailureMessage(expected.collectionName, mismatches),
        ))
    }

  private def renderFailureMessage(
    collectionName: String,
    mismatches: List[QdrantCollectionCompatibilityMismatch],
  ): String =
    s"${QdrantCollectionCompatibilityGuard.OperationName} failed for collection $collectionName: ${mismatches.map(renderMismatch).mkString("; ")}"

  private def renderMismatch(mismatch: QdrantCollectionCompatibilityMismatch): String =
    mismatch match {
      case QdrantCollectionCompatibilityMismatch.CollectionNameMismatch(expected, observed) =>
        s"CollectionNameMismatch(expected=$expected, observed=$observed)"
      case QdrantCollectionCompatibilityMismatch.VectorNameMismatch(expected, observed) =>
        s"VectorNameMismatch(expected=$expected, observed=$observed)"
      case QdrantCollectionCompatibilityMismatch.DimensionMismatch(expected, observed) =>
        s"DimensionMismatch(expected=$expected, observed=$observed)"
      case QdrantCollectionCompatibilityMismatch.DistanceMismatch(expected, observed) =>
        s"DistanceMismatch(expected=$expected, observed=$observed)"
      case QdrantCollectionCompatibilityMismatch.EmbeddingModelMismatch(expected, observed) =>
        s"EmbeddingModelMismatch(expected=$expected, observed=$observed)"
    }
}

object QdrantCollectionCompatibilityGuard {
  private[qdrant] val OperationName = "qdrant-collection-compatibility"
}
