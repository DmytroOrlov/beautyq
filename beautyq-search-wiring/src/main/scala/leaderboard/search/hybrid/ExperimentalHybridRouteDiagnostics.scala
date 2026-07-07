package leaderboard.search.hybrid

import leaderboard.search.beautyq.contract.BeautyQSearchResponseProvenanceContract
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
  import BeautyQSearchResponseProvenanceContract.RouteDiagnosticReasonCategories

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
          reasonCategory = RouteDiagnosticReasonCategories.LexicalOnly,
        )

      case SearchBackendRoute.QdrantCandidateRoute =>
        ExperimentalHybridRouteDiagnostics(
          route = route,
          routingSignal = metadata.signal,
          usesLexicalBackend = false,
          usesSemanticBackend = true,
          fallbackRequested = false,
          fallbackImplemented = false,
          reasonCategory = RouteDiagnosticReasonCategories.SemanticCandidates,
        )

      case SearchBackendRoute.ElasticsearchThenQdrantFallback =>
        ExperimentalHybridRouteDiagnostics(
          route = route,
          routingSignal = metadata.signal,
          usesLexicalBackend = true,
          usesSemanticBackend = false,
          fallbackRequested = true,
          fallbackImplemented = false,
          reasonCategory = RouteDiagnosticReasonCategories.FallbackNotImplemented,
        )

      case SearchBackendRoute.ElasticsearchWithQdrantVariantSupplement =>
        ExperimentalHybridRouteDiagnostics(
          route = route,
          routingSignal = metadata.signal,
          usesLexicalBackend = true,
          usesSemanticBackend = true,
          fallbackRequested = false,
          fallbackImplemented = false,
          reasonCategory = RouteDiagnosticReasonCategories.LexicalWithQdrantVariantSupplement,
        )
    }
}
