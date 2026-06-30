package leaderboard.search.qdrant

import leaderboard.model.QueryFailure
import zio.{IO, ZIO}

final class QdrantCollectionCompatibilityChecker(client: QdrantCollectionInfoClient) {
  def check(
    expected: QdrantCollectionCompatibilityExpectation
  ): IO[QueryFailure, Either[List[QdrantCollectionCompatibilityMismatch], Unit]] =
    client
      .collectionInfo(s"/collections/${expected.collectionName}")
      .flatMap(collectionInfoJson => ZIO.fromEither(QdrantCollectionCompatibilityValidator.validate(expected, collectionInfoJson)))
}
