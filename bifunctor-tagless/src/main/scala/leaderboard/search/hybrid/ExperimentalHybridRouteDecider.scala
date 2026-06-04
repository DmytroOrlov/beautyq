package leaderboard.search.hybrid

import leaderboard.search.routing.{SearchBackendRoute, SearchBackendRouter, SearchRoutingMetadata}
import leaderboard.search.{ParsedSearchIntent, UserSearchInput}

final class ExperimentalHybridRouteDecider(
  router: SearchBackendRouter,
  metadataProvider: (UserSearchInput, ParsedSearchIntent) => SearchRoutingMetadata,
) {
  def decide(input: UserSearchInput, intent: ParsedSearchIntent): SearchBackendRoute =
    router.decide(input, intent, metadataProvider(input, intent)).route

  def toRouteDecision: (UserSearchInput, ParsedSearchIntent) => SearchBackendRoute =
    decide
}
