package leaderboard.search

import io.circe.Json
import io.circe.syntax.*
import leaderboard.search.elasticsearch.{
  ElasticsearchLifecycleStatusResponse,
  ElasticsearchProductionReadinessState,
  ElasticsearchSeedIndexReadiness,
}
import org.scalatest.wordspec.AnyWordSpec

final class ElasticsearchLifecycleStatusResponseSpec extends AnyWordSpec {

  "ElasticsearchLifecycleStatusResponse.from" should {
    "map the complete current seed-only readiness state" in {
      val response = ElasticsearchLifecycleStatusResponse.from(seedOnlyState(documentCount = 37))

      assert(
        response == ElasticsearchLifecycleStatusResponse(
          indexName = "beautyq_variant_v1",
          source = "seed-resource-loader",
          documentCount = 37,
          preparationMode = "eager_seed_index_preparation",
          lifecycleStatus = "seed_only_not_production_lifecycle",
          servingReadiness = "not_enforced",
          replacement = "not_configured",
          freshness = "not_tracked",
          refresh = "eager_seed_preparation_only",
          rollback = "not_configured",
          operatorVisibility = "not_exposed",
          productionLifecycleComplete = false,
        )
      )
    }

    "take documentCount from lifecycle metadata" in {
      val first = ElasticsearchLifecycleStatusResponse.from(seedOnlyState(documentCount = 2))
      val second = ElasticsearchLifecycleStatusResponse.from(seedOnlyState(documentCount = 41))

      assert(first.documentCount == 2)
      assert(second.documentCount == 41)
    }
  }

  "ElasticsearchLifecycleStatusResponse JSON encoding" should {
    "encode the exact planned field names and current values" in {
      val json = ElasticsearchLifecycleStatusResponse.from(seedOnlyState(documentCount = 37)).asJson

      assert(
        json == Json.obj(
          "indexName" -> Json.fromString("beautyq_variant_v1"),
          "source" -> Json.fromString("seed-resource-loader"),
          "documentCount" -> Json.fromInt(37),
          "preparationMode" -> Json.fromString("eager_seed_index_preparation"),
          "lifecycleStatus" -> Json.fromString("seed_only_not_production_lifecycle"),
          "servingReadiness" -> Json.fromString("not_enforced"),
          "replacement" -> Json.fromString("not_configured"),
          "freshness" -> Json.fromString("not_tracked"),
          "refresh" -> Json.fromString("eager_seed_preparation_only"),
          "rollback" -> Json.fromString("not_configured"),
          "operatorVisibility" -> Json.fromString("not_exposed"),
          "productionLifecycleComplete" -> Json.False,
        )
      )
    }
  }

  private def seedOnlyState(documentCount: Int): ElasticsearchProductionReadinessState =
    ElasticsearchProductionReadinessState.seedOnly(
      ElasticsearchSeedIndexReadiness(
        indexName = "beautyq_variant_v1",
        source = "seed-resource-loader",
        documentCount = documentCount,
      ).lifecycleMetadata
    )
}
