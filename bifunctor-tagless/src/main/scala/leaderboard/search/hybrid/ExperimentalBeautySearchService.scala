package leaderboard.search.hybrid

import izumi.functional.bio.Error2
import leaderboard.model.QueryFailure
import leaderboard.search.dsl.BeautySearchSpec
import leaderboard.search.parser.BeautySearchIntentParser
import leaderboard.search.routing.{SearchBackendRouter, SearchRoutingMetadata}
import leaderboard.search.semantic.{SemanticCandidateBackend, VariantSearchDocumentLookup}
import leaderboard.search.{BeautySearchBackend, BeautySearchResponse, UserSearchInput}

final class ExperimentalBeautySearchService[F[+_, +_]: Error2](
  parser: BeautySearchIntentParser,
  spec: BeautySearchSpec,
  lexicalBackend: BeautySearchBackend[F],
  router: SearchBackendRouter,
  semanticBackend: SemanticCandidateBackend[F],
  documentLookup: VariantSearchDocumentLookup[F],
) {

  def search(input: UserSearchInput, metadata: SearchRoutingMetadata): F[QueryFailure, BeautySearchResponse] = {
    val intent = parser.parse(input)
    val routeDecider = new ExperimentalHybridRouteDecider(router, (_, _) => metadata)
    val backend = new ExperimentalHybridSearchBackend[F](
      spec,
      lexicalBackend,
      routeDecider.toRouteDecision,
      semanticBackend,
      documentLookup,
    )

    backend.search(input, intent)
  }
}
