package leaderboard.search.hybrid

import leaderboard.model.{MasterServiceOfferVariantId, QueryFailure}
import leaderboard.search.document.VariantSearchDocument
import leaderboard.search.lexical.LexicalDocumentBackend
import leaderboard.search.qdrant.{QdrantNonProductionExperimentComposition, QdrantSnapshotIndexingResult}
import leaderboard.search.semantic.SemanticDocumentLookup
import leaderboard.search.{ParsedSearchIntent, UserSearchInput}
import zio.IO

final class BeautyQNonProductionHybridRunnerManualHandle(
  qdrantComposition: QdrantNonProductionExperimentComposition,
  runner: BeautyQNonProductionHybridExperimentRunner[IO],
) {
  def indexSnapshot(): IO[QueryFailure, QdrantSnapshotIndexingResult] =
    qdrantComposition.indexSnapshot()

  def run(
    input: UserSearchInput,
    intent: ParsedSearchIntent,
  ): IO[QueryFailure, BeautyQNonProductionHybridResponseExperimentResult] =
    runner.run(input, intent)
}

object BeautyQNonProductionHybridRunnerManualHandle {
  def fromQdrantComposition(
    lexicalBackend: LexicalDocumentBackend[IO, MasterServiceOfferVariantId],
    qdrantComposition: QdrantNonProductionExperimentComposition,
    documentLookup: SemanticDocumentLookup[IO, MasterServiceOfferVariantId, VariantSearchDocument],
  ): BeautyQNonProductionHybridRunnerManualHandle =
    new BeautyQNonProductionHybridRunnerManualHandle(
      qdrantComposition = qdrantComposition,
      runner = BeautyQNonProductionHybridRunnerComposition.fromQdrantComposition(
        lexicalBackend = lexicalBackend,
        qdrantComposition = qdrantComposition,
        documentLookup = documentLookup,
      ),
    )
}
