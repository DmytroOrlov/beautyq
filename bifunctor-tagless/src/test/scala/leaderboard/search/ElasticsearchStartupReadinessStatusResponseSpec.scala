package leaderboard.search

import io.circe.Json
import io.circe.syntax._
import leaderboard.model.QueryFailure
import leaderboard.search.elasticsearch.{
  ElasticsearchLifecycleStatusResponse,
  ElasticsearchProductionReadinessState,
  ElasticsearchSeedIndexReadiness,
  ElasticsearchStartupReadinessStatusResponse,
  ElasticsearchStartupReadinessTransition,
}
import org.scalatest.wordspec.AnyWordSpec

final class ElasticsearchStartupReadinessStatusResponseSpec extends AnyWordSpec {

  "ElasticsearchStartupReadinessStatusResponse.from" should {
    "derive a prepared projection from a Prepared transition" in {
      val state = seedOnlyState()
      val transition = ElasticsearchStartupReadinessTransition.prepared(state)
      val projection = ElasticsearchStartupReadinessStatusResponse.from(transition)

      projection match {
        case prepared: ElasticsearchStartupReadinessStatusResponse.Prepared =>
          assert(prepared.transitionStatus == "prepared")
          assert(prepared.servingDecision == "not_enforced")
          assert(prepared.productionLifecycleComplete == false)
          assert(prepared.lifecycleStatus == ElasticsearchLifecycleStatusResponse.from(state))
        case other =>
          fail(s"Expected Prepared projection, got $other")
      }
    }

    "derive a prepared projection with the same lifecycle status response as direct state projection" in {
      val state = seedOnlyState()
      val transition = ElasticsearchStartupReadinessTransition.prepared(state)
      val projection = ElasticsearchStartupReadinessStatusResponse.from(transition)
      val expectedLifecycle = ElasticsearchLifecycleStatusResponse.from(state)

      projection match {
        case prepared: ElasticsearchStartupReadinessStatusResponse.Prepared =>
          assert(prepared.lifecycleStatus == expectedLifecycle)
          assert(prepared.lifecycleStatus.indexName == "beautyq_variant_v1")
          assert(prepared.lifecycleStatus.source == "seed-resource-loader")
          assert(prepared.lifecycleStatus.documentCount == 37)
          assert(prepared.lifecycleStatus.preparationMode == "eager_seed_index_preparation")
          assert(prepared.lifecycleStatus.lifecycleStatus == "seed_only_not_production_lifecycle")
          assert(prepared.lifecycleStatus.servingReadiness == "not_enforced")
          assert(prepared.lifecycleStatus.replacement == "not_configured")
          assert(prepared.lifecycleStatus.freshness == "not_tracked")
          assert(prepared.lifecycleStatus.refresh == "eager_seed_preparation_only")
          assert(prepared.lifecycleStatus.rollback == "not_configured")
          assert(prepared.lifecycleStatus.operatorVisibility == "not_exposed")
          assert(prepared.lifecycleStatus.productionLifecycleComplete == false)
        case other =>
          fail(s"Expected Prepared projection, got $other")
      }
    }

    "derive a failed projection from a PreparationFailed transition" in {
      val failure = QueryFailure.operation(
        operationName = "elasticsearch-json-client",
        message = "refresh failed",
      )

      ElasticsearchStartupReadinessTransition.preparationFailed(failure) match {
        case Right(transition) =>
          val projection = ElasticsearchStartupReadinessStatusResponse.from(transition)

          projection match {
            case failed: ElasticsearchStartupReadinessStatusResponse.PreparationFailed =>
              assert(failed.transitionStatus == "preparation_failed")
              assert(failed.servingDecision == "not_enforced")
              assert(failed.operationName == "elasticsearch-json-client")
              assert(failed.message == "refresh failed")
              assert(failed.productionLifecycleComplete == false)
            case other =>
              fail(s"Expected PreparationFailed projection, got $other")
          }
        case Left(unsupported) =>
          fail(s"Expected supported OperationFailure, got $unsupported")
      }
    }
  }

  "ElasticsearchStartupReadinessStatusResponse JSON encoding" should {
    "encode a prepared projection with transitionStatus, servingDecision, lifecycleStatus, and productionLifecycleComplete" in {
      val state = seedOnlyState()
      val transition = ElasticsearchStartupReadinessTransition.prepared(state)
      val projection = ElasticsearchStartupReadinessStatusResponse.from(transition)
      val json = projection.asJson
      val cursor = json.hcursor

      assert(cursor.get[String]("transitionStatus") == Right("prepared"))
      assert(cursor.get[String]("servingDecision") == Right("not_enforced"))
      assert(cursor.get[Boolean]("productionLifecycleComplete") == Right(false))

      val lifecycleJson = cursor.downField("lifecycleStatus")
      assert(lifecycleJson.get[String]("indexName") == Right("beautyq_variant_v1"))
      assert(lifecycleJson.get[String]("source") == Right("seed-resource-loader"))
      assert(lifecycleJson.get[Int]("documentCount") == Right(37))
      assert(lifecycleJson.get[String]("preparationMode") == Right("eager_seed_index_preparation"))
      assert(lifecycleJson.get[String]("lifecycleStatus") == Right("seed_only_not_production_lifecycle"))
      assert(lifecycleJson.get[String]("servingReadiness") == Right("not_enforced"))
      assert(lifecycleJson.get[String]("replacement") == Right("not_configured"))
      assert(lifecycleJson.get[String]("freshness") == Right("not_tracked"))
      assert(lifecycleJson.get[String]("refresh") == Right("eager_seed_preparation_only"))
      assert(lifecycleJson.get[String]("rollback") == Right("not_configured"))
      assert(lifecycleJson.get[String]("operatorVisibility") == Right("not_exposed"))
      assert(lifecycleJson.get[Boolean]("productionLifecycleComplete") == Right(false))
    }

    "encode a prepared projection as exact JSON" in {
      val state = seedOnlyState()
      val transition = ElasticsearchStartupReadinessTransition.prepared(state)
      val projection = ElasticsearchStartupReadinessStatusResponse.from(transition)
      val json = projection.asJson
      val expectedLifecycle = ElasticsearchLifecycleStatusResponse.from(state).asJson

      assert(
        json == Json.obj(
          "transitionStatus" -> Json.fromString("prepared"),
          "servingDecision" -> Json.fromString("not_enforced"),
          "lifecycleStatus" -> expectedLifecycle,
          "productionLifecycleComplete" -> Json.False,
        )
      )
    }

    "encode a failed projection with transitionStatus, servingDecision, operationName, message, and productionLifecycleComplete" in {
      val failure = QueryFailure.operation(
        operationName = "elasticsearch-json-client",
        message = "refresh failed",
      )

      ElasticsearchStartupReadinessTransition.preparationFailed(failure) match {
        case Right(transition) =>
          val projection = ElasticsearchStartupReadinessStatusResponse.from(transition)
          val json = projection.asJson
          val cursor = json.hcursor

          assert(cursor.get[String]("transitionStatus") == Right("preparation_failed"))
          assert(cursor.get[String]("servingDecision") == Right("not_enforced"))
          assert(cursor.get[String]("operationName") == Right("elasticsearch-json-client"))
          assert(cursor.get[String]("message") == Right("refresh failed"))
          assert(cursor.get[Boolean]("productionLifecycleComplete") == Right(false))
        case Left(unsupported) =>
          fail(s"Expected supported OperationFailure, got $unsupported")
      }
    }

    "encode a failed projection as exact JSON without lifecycle metadata or lifecycleStatus" in {
      val failure = QueryFailure.operation(
        operationName = "elasticsearch-seed-index-readiness",
        message = "seed index readiness documents are empty",
      )

      ElasticsearchStartupReadinessTransition.preparationFailed(failure) match {
        case Right(transition) =>
          val projection = ElasticsearchStartupReadinessStatusResponse.from(transition)
          val json = projection.asJson

          assert(
            json == Json.obj(
              "transitionStatus" -> Json.fromString("preparation_failed"),
              "servingDecision" -> Json.fromString("not_enforced"),
              "operationName" -> Json.fromString("elasticsearch-seed-index-readiness"),
              "message" -> Json.fromString("seed index readiness documents are empty"),
              "productionLifecycleComplete" -> Json.False,
            )
          )

          val cursor = json.hcursor
          assert(cursor.get[String]("indexName").isLeft, "failed JSON must not contain indexName")
          assert(cursor.get[Int]("documentCount").isLeft, "failed JSON must not contain documentCount")
          assert(cursor.get[String]("preparationMode").isLeft, "failed JSON must not contain preparationMode")
          assert(cursor.downField("lifecycleStatus").focus.isEmpty, "failed JSON must not contain lifecycleStatus field")
        case Left(unsupported) =>
          fail(s"Expected supported OperationFailure, got $unsupported")
      }
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
