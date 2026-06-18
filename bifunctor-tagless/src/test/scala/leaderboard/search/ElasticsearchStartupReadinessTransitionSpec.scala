package leaderboard.search

import io.circe.syntax.*
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
      val expectedResponse = ElasticsearchLifecycleStatusResponse.from(state)

      assert(transition.state == state)
      assert(transition.lifecycleMetadata.contains(state.lifecycleMetadata))
      assert(transition.lifecycleStatusResponse.contains(expectedResponse))

      transition.lifecycleStatusResponse match {
        case Some(response) =>
          assert(response.indexName == "beautyq_variant_v1")
          assert(response.source == "seed-resource-loader")
          assert(response.documentCount == 37)
          assert(response.preparationMode == "eager_seed_index_preparation")
          assert(response.lifecycleStatus == "seed_only_not_production_lifecycle")
          assert(response.servingReadiness == "not_enforced")
          assert(response.replacement == "not_configured")
          assert(response.freshness == "not_tracked")
          assert(response.refresh == "eager_seed_preparation_only")
          assert(response.rollback == "not_configured")
          assert(response.operatorVisibility == "not_exposed")
          assert(response.productionLifecycleComplete == false)
        case None =>
          fail("Expected prepared transition lifecycle status response")
      }
    }

    "encode the same current status values as direct readiness-state projection" in {
      val state = seedOnlyState()
      val transition = ElasticsearchStartupReadinessTransition.prepared(state)

      transition.lifecycleStatusResponse match {
        case Some(response) =>
          val transitionJson = response.asJson
          val directJson = ElasticsearchLifecycleStatusResponse.from(state).asJson
          val cursor = transitionJson.hcursor

          assert(transitionJson == directJson)
          assert(cursor.get[String]("servingReadiness") == Right("not_enforced"))
          assert(cursor.get[String]("replacement") == Right("not_configured"))
          assert(cursor.get[String]("freshness") == Right("not_tracked"))
          assert(cursor.get[String]("refresh") == Right("eager_seed_preparation_only"))
          assert(cursor.get[String]("rollback") == Right("not_configured"))
          assert(cursor.get[String]("operatorVisibility") == Right("not_exposed"))
          assert(cursor.get[String]("lifecycleStatus") == Right("seed_only_not_production_lifecycle"))
          assert(cursor.get[String]("preparationMode") == Right("eager_seed_index_preparation"))
          assert(cursor.get[Boolean]("productionLifecycleComplete") == Right(false))
        case None =>
          fail("Expected prepared transition lifecycle status response")
      }
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
          assert(transition.lifecycleStatusResponse.map(_.asJson).isEmpty)
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
