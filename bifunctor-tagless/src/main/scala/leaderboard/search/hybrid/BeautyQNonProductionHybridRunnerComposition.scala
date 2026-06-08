package leaderboard.search.hybrid

import leaderboard.model.MasterServiceOfferVariantId
import leaderboard.search.document.VariantSearchDocument
import leaderboard.search.lexical.LexicalDocumentBackend
import leaderboard.search.qdrant.QdrantNonProductionExperimentComposition
import leaderboard.search.semantic.SemanticDocumentLookup
import zio.IO

object BeautyQNonProductionHybridRunnerComposition {
  def fromQdrantComposition(
    lexicalBackend: LexicalDocumentBackend[IO, MasterServiceOfferVariantId],
    qdrantComposition: QdrantNonProductionExperimentComposition,
    documentLookup: SemanticDocumentLookup[IO, MasterServiceOfferVariantId, VariantSearchDocument],
  ): BeautyQNonProductionHybridExperimentRunner[IO] =
    BeautyQNonProductionHybridExperimentRunner[IO](
      lexicalBackend = lexicalBackend,
      semanticBackend = qdrantComposition.semanticBackend,
      documentLookup = documentLookup,
    )
}
