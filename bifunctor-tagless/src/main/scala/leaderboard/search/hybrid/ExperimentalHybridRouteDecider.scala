package leaderboard.search.hybrid

import leaderboard.search.routing.{SearchBackendRoute, SearchBackendRouter, SearchRoutingMetadata}
import leaderboard.search.{ParsedSearchIntent, UserSearchInput}

final class ExperimentalHybridRouteDecider(
  router: SearchBackendRouter,
  metadataProvider: (UserSearchInput, ParsedSearchIntent) => SearchRoutingMetadata,
) {
  def decide(input: UserSearchInput, intent: ParsedSearchIntent): SearchBackendRoute =
    decideWithDiagnostics(input, intent).route

  def decideWithDiagnostics(input: UserSearchInput, intent: ParsedSearchIntent): ExperimentalHybridRouteDiagnostics = {
    val metadata = metadataProvider(input, intent)
    val route = router.decide(input, intent, metadata).route

    ExperimentalHybridRouteDiagnostics.from(route, metadata)
  }

  def toRouteDecision: (UserSearchInput, ParsedSearchIntent) => SearchBackendRoute =
    decide
}
