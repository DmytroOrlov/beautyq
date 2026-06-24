package leaderboard.search.routing

import leaderboard.search.{ParsedSearchIntent, UserSearchInput}

sealed trait SearchBackendRoute extends Product with Serializable
object SearchBackendRoute {
  case object ElasticsearchOnly extends SearchBackendRoute
  case object QdrantCandidateRoute extends SearchBackendRoute
  case object ElasticsearchThenQdrantFallback extends SearchBackendRoute
  case object ElasticsearchWithQdrantVariantSupplement extends SearchBackendRoute
}

sealed trait SearchRoutingReason extends Product with Serializable
object SearchRoutingReason {
  case object ExplicitConstraints extends SearchRoutingReason
  case object LexicalIntent extends SearchRoutingReason
  case object HardNegativeOrNoiseGuard extends SearchRoutingReason
  case object BroadSemanticCandidate extends SearchRoutingReason
  case object FallbackNotEnabled extends SearchRoutingReason
}

sealed trait SearchRoutingSignal extends Product with Serializable
object SearchRoutingSignal {
  case object BroadSemanticCandidate extends SearchRoutingSignal
  case object HardNegativeOrNoiseGuard extends SearchRoutingSignal
}

final case class SearchRoutingMetadata(
  signal: Option[SearchRoutingSignal] = None,
)

final case class SearchRoutingDecision(
  route: SearchBackendRoute,
  reason: SearchRoutingReason,
  diagnostic: Option[String] = None,
)

final class SearchBackendRouter {
  import SearchBackendRoute.*
  import SearchRoutingReason.*

  def decide(
    input: UserSearchInput,
    intent: ParsedSearchIntent,
    metadata: SearchRoutingMetadata = SearchRoutingMetadata(),
  ): SearchRoutingDecision = {
    val lexicalRecognized = intent.explicitConstraints.nonEmpty || intent.softBoosts.nonEmpty

    if (intent.explicitConstraints.nonEmpty) {
      SearchRoutingDecision(
        route = ElasticsearchOnly,
        reason = ExplicitConstraints,
        diagnostic = Some(s"Route '${input.query}' to Elasticsearch because explicit constraints are present"),
      )
    } else if (intent.softBoosts.nonEmpty) {
      SearchRoutingDecision(
        route = ElasticsearchOnly,
        reason = LexicalIntent,
        diagnostic = Some(s"Route '${input.query}' to Elasticsearch because lexical intent was recognized"),
      )
    } else {
      metadata.signal match {
        case Some(SearchRoutingSignal.HardNegativeOrNoiseGuard) =>
          SearchRoutingDecision(
            route = ElasticsearchOnly,
            reason = HardNegativeOrNoiseGuard,
            diagnostic = Some(s"Route '${input.query}' to Elasticsearch because hard-negative or noise fallback is guarded"),
          )
        case Some(SearchRoutingSignal.BroadSemanticCandidate) if !lexicalRecognized =>
          SearchRoutingDecision(
            route = QdrantCandidateRoute,
            reason = BroadSemanticCandidate,
            diagnostic = Some(s"Route '${input.query}' to Qdrant candidate retrieval because it is marked as a broad semantic candidate"),
          )
        case _ =>
          SearchRoutingDecision(
            route = ElasticsearchOnly,
            reason = FallbackNotEnabled,
            diagnostic = Some(s"Route '${input.query}' to Elasticsearch because residual text alone does not enable fallback routing"),
          )
      }
    }
  }
}

object SearchBackendRouter {
  val default: SearchBackendRouter = new SearchBackendRouter
}
