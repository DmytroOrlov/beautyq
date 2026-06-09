package leaderboard.search.hybrid

import leaderboard.config.QdrantPortCfg
import leaderboard.model.MasterServiceOfferVariantId
import leaderboard.search.document.VariantSearchDocument
import leaderboard.search.document.VariantSearchDocumentSnapshotProvider
import leaderboard.search.dsl.{EmbeddingSpec, SearchDocumentSpec}
import leaderboard.search.embedding.{LlamaCppEmbeddingClient, LlamaCppEmbeddingClientConfig}
import leaderboard.search.lexical.LexicalDocumentBackend
import leaderboard.search.qdrant.{
  QdrantClient,
  QdrantCollectionReadinessConfig,
}
import leaderboard.search.semantic.SemanticDocumentLookup
import zio.IO

final case class BeautyQNonProductionHybridRunnerRealClientInputs(
  lexicalBackend: LexicalDocumentBackend[IO, MasterServiceOfferVariantId],
  readinessConfig: QdrantCollectionReadinessConfig,
  qdrantPortCfg: QdrantPortCfg,
  llamaConfig: LlamaCppEmbeddingClientConfig,
  snapshotProvider: VariantSearchDocumentSnapshotProvider[IO],
  documentLookup: SemanticDocumentLookup[IO, MasterServiceOfferVariantId, VariantSearchDocument],
  documentSpec: SearchDocumentSpec[VariantSearchDocument],
  embeddingSpec: EmbeddingSpec[VariantSearchDocument],
) {
  def toQdrantClientInputs(): BeautyQNonProductionHybridRunnerQdrantClientInputs =
    BeautyQNonProductionHybridRunnerQdrantClientInputs(
      lexicalBackend = lexicalBackend,
      readinessConfig = readinessConfig,
      qdrantClient = new QdrantClient(qdrantPortCfg.host, qdrantPortCfg.port),
      snapshotProvider = snapshotProvider,
      embeddingClient = new LlamaCppEmbeddingClient(llamaConfig),
      documentLookup = documentLookup,
      documentSpec = documentSpec,
      embeddingSpec = embeddingSpec,
    )

  def toAdapterInputs(): BeautyQNonProductionHybridRunnerManualAdapterInputs =
    toQdrantClientInputs().toAdapterInputs()

  def toManualInputs(): BeautyQNonProductionHybridRunnerManualInputs =
    toQdrantClientInputs().toManualInputs()

  def buildHandle(): BeautyQNonProductionHybridRunnerManualHandle =
    toQdrantClientInputs().buildHandle()
}
