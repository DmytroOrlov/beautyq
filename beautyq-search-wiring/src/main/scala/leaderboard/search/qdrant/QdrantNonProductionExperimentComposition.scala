package leaderboard.search.qdrant

import leaderboard.model.QueryFailure
import leaderboard.search.document.VariantSearchDocumentSnapshotProvider
import zio.IO

final case class QdrantNonProductionExperimentComposition(
  readinessConfig: QdrantCollectionReadinessConfig,
  snapshotIndexer: QdrantVariantDocumentSnapshotIndexer,
  snapshotIndexingCompatibility: QdrantSnapshotIndexingCompatibilityGuard,
  semanticBackend: QdrantSemanticCandidateBackend,
) {
  def indexSnapshot(): IO[QueryFailure, QdrantSnapshotIndexingResult] =
    snapshotIndexer.indexCompatibleSnapshot(snapshotIndexingCompatibility)
}

object QdrantNonProductionExperimentComposition {
  def build(
    readinessConfig: QdrantCollectionReadinessConfig,
    compatibilityGuard: QdrantCollectionCompatibilityGuard,
    snapshotProvider: VariantSearchDocumentSnapshotProvider[IO],
    documentUpsert: QdrantVariantDocumentUpsert,
    semanticCandidateSearch: QdrantSemanticCandidateSearch,
  ): QdrantNonProductionExperimentComposition = {
    val snapshotIndexer = new QdrantVariantDocumentSnapshotIndexer(snapshotProvider, documentUpsert)
    val snapshotIndexingCompatibility = QdrantSnapshotIndexingCompatibilityGuard(
      expectation = readinessConfig.compatibilityExpectation,
      guard = compatibilityGuard,
    )
    val semanticBackend = new QdrantSemanticCandidateBackend(
      semanticCandidateSearch,
      readinessConfig.vectorSearchSpec,
    )

    QdrantNonProductionExperimentComposition(
      readinessConfig = readinessConfig,
      snapshotIndexer = snapshotIndexer,
      snapshotIndexingCompatibility = snapshotIndexingCompatibility,
      semanticBackend = semanticBackend,
    )
  }
}
