package leaderboard.search.hybrid

import leaderboard.search.routing.{SearchBackendRoute, SearchRoutingMetadata, SearchRoutingSignal}

final case class ExperimentalHybridRouteDiagnostics(
  route: SearchBackendRoute,
  routingSignal: Option[SearchRoutingSignal],
  usesLexicalBackend: Boolean,
  usesSemanticBackend: Boolean,
  fallbackRequested: Boolean,
  fallbackImplemented: Boolean,
  reasonCategory: String,
)

object ExperimentalHybridRouteDiagnostics {
  def from(
    route: SearchBackendRoute,
    metadata: SearchRoutingMetadata,
  ): ExperimentalHybridRouteDiagnostics =
    route match {
      case SearchBackendRoute.ElasticsearchOnly =>
        ExperimentalHybridRouteDiagnostics(
          route = route,
          routingSignal = metadata.signal,
          usesLexicalBackend = true,
          usesSemanticBackend = false,
          fallbackRequested = false,
          fallbackImplemented = false,
          reasonCategory = "lexical-only",
        )

      case SearchBackendRoute.QdrantCandidateRoute =>
        ExperimentalHybridRouteDiagnostics(
          route = route,
          routingSignal = metadata.signal,
          usesLexicalBackend = false,
          usesSemanticBackend = true,
          fallbackRequested = false,
          fallbackImplemented = false,
          reasonCategory = "semantic-candidates",
        )

      case SearchBackendRoute.ElasticsearchThenQdrantFallback =>
        ExperimentalHybridRouteDiagnostics(
          route = route,
          routingSignal = metadata.signal,
          usesLexicalBackend = true,
          usesSemanticBackend = false,
          fallbackRequested = true,
          fallbackImplemented = false,
          reasonCategory = "fallback-not-implemented",
        )
    }
}
