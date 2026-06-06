package leaderboard.search.qdrant

import leaderboard.model.QueryFailure
import leaderboard.search.dsl.BeautySearchSpec
import leaderboard.search.hybrid.{ExperimentalBeautySearchService, ExperimentalHybridRouteDiagnostics}
import leaderboard.search.parser.BeautySearchIntentParser
import leaderboard.search.routing.{SearchBackendRouter, SearchRoutingMetadata}
import leaderboard.search.semantic.VariantSearchDocumentLookup
import leaderboard.search.{BeautySearchBackend, BeautySearchResponse, UserSearchInput}
import zio.IO

final case class QdrantNonProductionHybridExperiment(
  composition: QdrantNonProductionExperimentComposition,
  service: ExperimentalBeautySearchService[IO],
) {
  def indexSnapshot(): IO[QueryFailure, QdrantSnapshotIndexingResult] =
    composition.indexSnapshot()

  def search(
    input: UserSearchInput,
    metadata: SearchRoutingMetadata,
  ): IO[QueryFailure, BeautySearchResponse] =
    service.search(input, metadata)

  def diagnose(
    input: UserSearchInput,
    metadata: SearchRoutingMetadata,
  ): ExperimentalHybridRouteDiagnostics =
    service.diagnose(input, metadata)
}

object QdrantNonProductionHybridExperiment {
  def build(
    composition: QdrantNonProductionExperimentComposition,
    parser: BeautySearchIntentParser,
    spec: BeautySearchSpec,
    lexicalBackend: BeautySearchBackend[IO],
    router: SearchBackendRouter,
    documentLookup: VariantSearchDocumentLookup[IO],
  ): QdrantNonProductionHybridExperiment = {
    val experimentSpec = spec.copy(vectorSearchSpec = Some(composition.readinessConfig.vectorSearchSpec))
    val service = new ExperimentalBeautySearchService[IO](
      parser,
      experimentSpec,
      lexicalBackend,
      router,
      composition.semanticBackend,
      documentLookup,
    )

    QdrantNonProductionHybridExperiment(
      composition = composition,
      service = service,
    )
  }
}
