package leaderboard.search.hybrid

import leaderboard.model.MasterServiceOfferVariantId
import leaderboard.search.document.VariantSearchDocument
import leaderboard.search.document.VariantSearchDocumentSnapshotProvider
import leaderboard.search.lexical.LexicalDocumentBackend
import leaderboard.search.qdrant.{
  QdrantCollectionReadinessConfig,
  QdrantCollectionCompatibilityGuard,
  QdrantNonProductionExperimentComposition,
  QdrantSemanticCandidateSearch,
  QdrantVariantDocumentUpsert,
}
import leaderboard.search.semantic.SemanticDocumentLookup
import zio.IO

final case class BeautyQNonProductionHybridRunnerManualInputs(
  lexicalBackend: LexicalDocumentBackend[IO, MasterServiceOfferVariantId],
  readinessConfig: QdrantCollectionReadinessConfig,
  compatibilityGuard: QdrantCollectionCompatibilityGuard,
  snapshotProvider: VariantSearchDocumentSnapshotProvider[IO],
  documentUpsert: QdrantVariantDocumentUpsert,
  semanticCandidateSearch: QdrantSemanticCandidateSearch,
  documentLookup: SemanticDocumentLookup[IO, MasterServiceOfferVariantId, VariantSearchDocument],
) {
  def buildHandle(): BeautyQNonProductionHybridRunnerManualHandle = {
    val qdrantComposition =
      QdrantNonProductionExperimentComposition.build(
        readinessConfig = readinessConfig,
        compatibilityGuard = compatibilityGuard,
        snapshotProvider = snapshotProvider,
        documentUpsert = documentUpsert,
        semanticCandidateSearch = semanticCandidateSearch,
      )

    BeautyQNonProductionHybridRunnerManualHandle.fromQdrantComposition(
      lexicalBackend = lexicalBackend,
      qdrantComposition = qdrantComposition,
      documentLookup = documentLookup,
    )
  }
}
