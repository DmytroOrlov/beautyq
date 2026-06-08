package leaderboard.search.hybrid

import leaderboard.model.MasterServiceOfferVariantId
import leaderboard.search.document.VariantSearchDocument
import leaderboard.search.document.VariantSearchDocumentSnapshotProvider
import leaderboard.search.dsl.{EmbeddingSpec, SearchDocumentSpec}
import leaderboard.search.embedding.EmbeddingClient
import leaderboard.search.lexical.LexicalDocumentBackend
import leaderboard.search.qdrant.{
  QdrantCollectionCompatibilityChecker,
  QdrantCollectionCompatibilityGuard,
  QdrantCollectionInfoClient,
  QdrantCollectionReadinessConfig,
  QdrantPointUpsertClient,
  QdrantSearchClient,
  QdrantSemanticCandidateSearch,
  QdrantVariantDocumentIndexer,
}
import leaderboard.search.semantic.SemanticDocumentLookup
import zio.IO

final case class BeautyQNonProductionHybridRunnerManualAdapterInputs(
  lexicalBackend: LexicalDocumentBackend[IO, MasterServiceOfferVariantId],
  readinessConfig: QdrantCollectionReadinessConfig,
  collectionInfoClient: QdrantCollectionInfoClient,
  snapshotProvider: VariantSearchDocumentSnapshotProvider[IO],
  pointUpsertClient: QdrantPointUpsertClient,
  embeddingClient: EmbeddingClient,
  qdrantSearchClient: QdrantSearchClient,
  documentLookup: SemanticDocumentLookup[IO, MasterServiceOfferVariantId, VariantSearchDocument],
  documentSpec: SearchDocumentSpec[VariantSearchDocument],
  embeddingSpec: EmbeddingSpec[VariantSearchDocument],
) {
  def toManualInputs(): BeautyQNonProductionHybridRunnerManualInputs = {
    val compatibilityGuard =
      new QdrantCollectionCompatibilityGuard(
        new QdrantCollectionCompatibilityChecker(collectionInfoClient)
      )

    val documentUpsert =
      new QdrantVariantDocumentIndexer(
        embeddingClient = embeddingClient,
        upsertClient = pointUpsertClient,
        documentSpec = documentSpec,
        embeddingSpec = embeddingSpec,
      )

    val semanticCandidateSearch =
      new QdrantSemanticCandidateSearch(
        embeddingClient = embeddingClient,
        qdrantSearchClient = qdrantSearchClient,
      )

    BeautyQNonProductionHybridRunnerManualInputs(
      lexicalBackend = lexicalBackend,
      readinessConfig = readinessConfig,
      compatibilityGuard = compatibilityGuard,
      snapshotProvider = snapshotProvider,
      documentUpsert = documentUpsert,
      semanticCandidateSearch = semanticCandidateSearch,
      documentLookup = documentLookup,
    )
  }

  def buildHandle(): BeautyQNonProductionHybridRunnerManualHandle =
    toManualInputs().buildHandle()
}
