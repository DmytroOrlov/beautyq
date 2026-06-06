package leaderboard.search.qdrant

import leaderboard.model.{MasterServiceOfferVariantId, QueryFailure}
import leaderboard.search.document.VariantSearchDocumentSnapshotProvider
import zio.{IO, ZIO}

final case class QdrantSnapshotIndexingResult(
  totalDocumentsLoaded: Int,
  totalDocumentsIndexed: Int,
  indexedVariantIds: List[MasterServiceOfferVariantId],
)

final class QdrantVariantDocumentSnapshotIndexer(
  snapshotProvider: VariantSearchDocumentSnapshotProvider[IO],
  documentIndexer: QdrantVariantDocumentUpsert,
  compatibilityGuard: Option[(QdrantCollectionCompatibilityExpectation, QdrantCollectionCompatibilityGuard)] = None,
) {
  def indexSnapshot(collectionName: String): IO[QueryFailure, QdrantSnapshotIndexingResult] =
    for {
      _ <- compatibilityGuard.fold[IO[QueryFailure, Unit]](ZIO.unit) {
        case (expectation, guard) => guard.requireCompatible(expectation)
      }
      documents <- snapshotProvider.loadSnapshot()
      indexedVariantIds <- ZIO.foreach(documents) {
        document =>
          documentIndexer.upsertDocument(collectionName, document).as(document.variantId)
      }
    } yield QdrantSnapshotIndexingResult(
      totalDocumentsLoaded = documents.size,
      totalDocumentsIndexed = indexedVariantIds.size,
      indexedVariantIds = indexedVariantIds,
    )
}
