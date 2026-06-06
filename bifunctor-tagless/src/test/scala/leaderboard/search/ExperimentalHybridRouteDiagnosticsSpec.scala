package leaderboard.search

import leaderboard.search.dsl.BeautySearchSpecV1
import leaderboard.search.hybrid.{ExperimentalHybridRouteDecider, ExperimentalHybridRouteDiagnostics}
import leaderboard.search.parser.BeautySearchIntentParser
import leaderboard.search.routing.{SearchBackendRoute, SearchBackendRouter, SearchRoutingMetadata, SearchRoutingSignal}
import org.scalatest.wordspec.AnyWordSpec

final class ExperimentalHybridRouteDiagnosticsSpec extends AnyWordSpec {
  private val parser = new BeautySearchIntentParser(BeautySearchSpecV1.spec)

  "ExperimentalHybridRouteDiagnostics" should {
    "mark ElasticsearchOnly as lexical-only" in {
      val diagnostics = ExperimentalHybridRouteDiagnostics.from(
        SearchBackendRoute.ElasticsearchOnly,
        SearchRoutingMetadata(),
      )

      assert(diagnostics.route == SearchBackendRoute.ElasticsearchOnly)
      assert(diagnostics.usesLexicalBackend)
      assert(!diagnostics.usesSemanticBackend)
      assert(!diagnostics.fallbackRequested)
      assert(!diagnostics.fallbackImplemented)
      assert(diagnostics.reasonCategory == "lexical-only")
    }

    "mark QdrantCandidateRoute as semantic-only" in {
      val diagnostics = ExperimentalHybridRouteDiagnostics.from(
        SearchBackendRoute.QdrantCandidateRoute,
        SearchRoutingMetadata(),
      )

      assert(diagnostics.route == SearchBackendRoute.QdrantCandidateRoute)
      assert(!diagnostics.usesLexicalBackend)
      assert(diagnostics.usesSemanticBackend)
      assert(!diagnostics.fallbackRequested)
      assert(!diagnostics.fallbackImplemented)
      assert(diagnostics.reasonCategory == "semantic-candidates")
    }

    "mark ElasticsearchThenQdrantFallback as requested but not implemented" in {
      val diagnostics = ExperimentalHybridRouteDiagnostics.from(
        SearchBackendRoute.ElasticsearchThenQdrantFallback,
        SearchRoutingMetadata(),
      )

      assert(diagnostics.route == SearchBackendRoute.ElasticsearchThenQdrantFallback)
      assert(diagnostics.usesLexicalBackend)
      assert(!diagnostics.usesSemanticBackend)
      assert(diagnostics.fallbackRequested)
      assert(!diagnostics.fallbackImplemented)
      assert(diagnostics.reasonCategory == "fallback-not-implemented")
    }

    "preserve the routing signal from metadata" in {
      val diagnostics = ExperimentalHybridRouteDiagnostics.from(
        SearchBackendRoute.QdrantCandidateRoute,
        SearchRoutingMetadata(signal = Some(SearchRoutingSignal.BroadSemanticCandidate)),
      )

      assert(diagnostics.routingSignal.contains(SearchRoutingSignal.BroadSemanticCandidate))
    }
  }

  "ExperimentalHybridRouteDecider diagnostics" should {
    "keep decide route equal to decideWithDiagnostics route" in {
      val decider = new ExperimentalHybridRouteDecider(
        SearchBackendRouter.default,
        (_, _) => SearchRoutingMetadata(signal = Some(SearchRoutingSignal.BroadSemanticCandidate)),
      )
      val input = UserSearchInput("synthetic semantic discovery", None, None)
      val intent = parser.parse(input)
      val diagnostics = decider.decideWithDiagnostics(input, intent)

      assert(decider.decide(input, intent) == diagnostics.route)
    }

    "keep explicit BroadSemanticCandidate metadata route decisions unchanged" in {
      val metadata = SearchRoutingMetadata(signal = Some(SearchRoutingSignal.BroadSemanticCandidate))
      val decider = new ExperimentalHybridRouteDecider(
        SearchBackendRouter.default,
        (_, _) => metadata,
      )
      val input = UserSearchInput("synthetic semantic discovery", None, None)
      val intent = parser.parse(input)
      val routerDecision = SearchBackendRouter.default.decide(input, intent, metadata)
      val diagnostics = decider.decideWithDiagnostics(input, intent)

      assert(intent.explicitConstraints.isEmpty)
      assert(intent.softBoosts.isEmpty)
      assert(intent.remainingText.nonEmpty)
      assert(routerDecision.route == SearchBackendRoute.QdrantCandidateRoute)
      assert(diagnostics.route == routerDecision.route)
      assert(decider.decide(input, intent) == routerDecision.route)
    }
  }
}
