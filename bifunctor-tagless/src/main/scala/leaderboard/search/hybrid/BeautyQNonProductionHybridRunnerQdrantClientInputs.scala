package leaderboard.search.hybrid

import leaderboard.model.MasterServiceOfferVariantId
import leaderboard.search.document.VariantSearchDocument
import leaderboard.search.document.VariantSearchDocumentSnapshotProvider
import leaderboard.search.dsl.{EmbeddingSpec, SearchDocumentSpec}
import leaderboard.search.embedding.EmbeddingClient
import leaderboard.search.lexical.LexicalDocumentBackend
import leaderboard.search.qdrant.{
  QdrantClient,
  QdrantClientCollectionInfoAdapter,
  QdrantClientPointUpsertAdapter,
  QdrantClientSearchAdapter,
  QdrantCollectionReadinessConfig,
}
import leaderboard.search.semantic.SemanticDocumentLookup
import zio.IO

final case class BeautyQNonProductionHybridRunnerQdrantClientInputs(
  lexicalBackend: LexicalDocumentBackend[IO, MasterServiceOfferVariantId],
  readinessConfig: QdrantCollectionReadinessConfig,
  qdrantClient: QdrantClient,
  snapshotProvider: VariantSearchDocumentSnapshotProvider[IO],
  embeddingClient: EmbeddingClient,
  documentLookup: SemanticDocumentLookup[IO, MasterServiceOfferVariantId, VariantSearchDocument],
  documentSpec: SearchDocumentSpec[VariantSearchDocument],
  embeddingSpec: EmbeddingSpec[VariantSearchDocument],
) {
  def toAdapterInputs(): BeautyQNonProductionHybridRunnerManualAdapterInputs =
    BeautyQNonProductionHybridRunnerManualAdapterInputs(
      lexicalBackend = lexicalBackend,
      readinessConfig = readinessConfig,
      collectionInfoClient = new QdrantClientCollectionInfoAdapter(qdrantClient),
      snapshotProvider = snapshotProvider,
      pointUpsertClient = new QdrantClientPointUpsertAdapter(qdrantClient),
      embeddingClient = embeddingClient,
      qdrantSearchClient = new QdrantClientSearchAdapter(qdrantClient),
      documentLookup = documentLookup,
      documentSpec = documentSpec,
      embeddingSpec = embeddingSpec,
    )

  def toManualInputs(): BeautyQNonProductionHybridRunnerManualInputs =
    toAdapterInputs().toManualInputs()

  def buildHandle(): BeautyQNonProductionHybridRunnerManualHandle =
    toAdapterInputs().buildHandle()
}
