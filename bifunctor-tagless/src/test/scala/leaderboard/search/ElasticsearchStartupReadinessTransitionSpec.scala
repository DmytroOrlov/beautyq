package leaderboard.search

import leaderboard.model.QueryFailure
import leaderboard.search.elasticsearch.{
  ElasticsearchLifecycleStatusResponse,
  ElasticsearchProductionReadinessState,
  ElasticsearchSeedIndexReadiness,
  ElasticsearchStartupReadinessTransition,
  ElasticsearchStartupServingDecision,
}
import org.scalatest.wordspec.AnyWordSpec

final class ElasticsearchStartupReadinessTransitionSpec extends AnyWordSpec {

  "ElasticsearchStartupReadinessTransition.prepared" should {
    "preserve the readiness state and derive its lifecycle status response" in {
      val state = seedOnlyState()
      val transition = ElasticsearchStartupReadinessTransition.prepared(state)

      assert(transition.state == state)
      assert(transition.lifecycleMetadata.contains(state.lifecycleMetadata))
      assert(
        transition.lifecycleStatusResponse.contains(
          ElasticsearchLifecycleStatusResponse.from(state)
        )
      )
      assert(
        transition.lifecycleStatusResponse.exists(
          _.productionLifecycleComplete == false
        )
      )
    }

    "record that startup serving readiness is not enforced" in {
      val transition = ElasticsearchStartupReadinessTransition.prepared(seedOnlyState())

      assert(transition.servingDecision == ElasticsearchStartupServingDecision.NotEnforced)
    }
  }

  "ElasticsearchStartupReadinessTransition.preparationFailed" should {
    "record operation failure information without lifecycle metadata or a status response" in {
      val failure = QueryFailure.operation(
        operationName = "elasticsearch-json-client",
        message = "refresh failed",
      )

      ElasticsearchStartupReadinessTransition.preparationFailed(failure) match {
        case Right(transition) =>
          assert(transition.operationName == "elasticsearch-json-client")
          assert(transition.message == "refresh failed")
          assert(transition.servingDecision == ElasticsearchStartupServingDecision.NotEnforced)
          assert(transition.lifecycleMetadata.isEmpty)
          assert(transition.lifecycleStatusResponse.isEmpty)
        case Left(unsupported) =>
          fail(s"Expected supported OperationFailure, got $unsupported")
      }
    }

    "make non-operation QueryFailure cases explicit" in {
      val domainFailure = QueryFailure.domain("unsupported readiness failure")
      val executionFailure = QueryFailure.fromThrowable(
        queryName = "seed-index-preparation",
        cause = new RuntimeException("client failed"),
      )

      assert(
        ElasticsearchStartupReadinessTransition.preparationFailed(domainFailure) == Left(
          ElasticsearchStartupReadinessTransition.UnsupportedFailure(domainFailure)
        )
      )
      assert(
        ElasticsearchStartupReadinessTransition.preparationFailed(executionFailure) == Left(
          ElasticsearchStartupReadinessTransition.UnsupportedFailure(executionFailure)
        )
      )
    }
  }

  private def seedOnlyState(): ElasticsearchProductionReadinessState =
    ElasticsearchProductionReadinessState.seedOnly(
      ElasticsearchSeedIndexReadiness(
        indexName = "beautyq_variant_v1",
        source = "seed-resource-loader",
        documentCount = 37,
      ).lifecycleMetadata
    )
}
