package leaderboard.search.qdrant

import leaderboard.model.{MasterServiceOfferVariantId, QueryFailure}
import leaderboard.search.document.VariantSearchDocumentSnapshotProvider
import zio.{IO, ZIO}

final case class QdrantSnapshotIndexingResult(
  totalDocumentsLoaded: Int,
  totalDocumentsIndexed: Int,
  indexedVariantIds: List[MasterServiceOfferVariantId],
)

final case class QdrantSnapshotIndexingCompatibilityGuard(
  expectation: QdrantCollectionCompatibilityExpectation,
  guard: QdrantCollectionCompatibilityGuard,
)

final class QdrantVariantDocumentSnapshotIndexer(
  snapshotProvider: VariantSearchDocumentSnapshotProvider[IO],
  documentIndexer: QdrantVariantDocumentUpsert,
) {
  def indexSnapshot(collectionName: String): IO[QueryFailure, QdrantSnapshotIndexingResult] =
    indexDocuments(collectionName)

  def indexCompatibleSnapshot(
    compatibility: QdrantSnapshotIndexingCompatibilityGuard,
  ): IO[QueryFailure, QdrantSnapshotIndexingResult] =
    for {
      _ <- compatibility.guard.requireCompatible(compatibility.expectation)
      result <- indexDocuments(compatibility.expectation.collectionName)
    } yield result

  private def indexDocuments(collectionName: String): IO[QueryFailure, QdrantSnapshotIndexingResult] =
    snapshotProvider.loadSnapshot().flatMap { documents =>
      ZIO.foreach(documents) { document =>
        documentIndexer.upsertDocument(collectionName, document).as(document.variantId)
      }.map { indexedVariantIds =>
        QdrantSnapshotIndexingResult(
          totalDocumentsLoaded = documents.size,
          totalDocumentsIndexed = indexedVariantIds.size,
          indexedVariantIds = indexedVariantIds,
        )
      }
    }
}
