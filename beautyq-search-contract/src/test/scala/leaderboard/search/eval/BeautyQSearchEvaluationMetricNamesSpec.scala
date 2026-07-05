package leaderboard.search.eval

import org.scalatest.wordspec.AnyWordSpec

final class BeautyQSearchEvaluationMetricNamesSpec extends AnyWordSpec {

  import BeautyQSearchEvaluationMetricNames.SharedProductionPosture

  "BeautyQSearchEvaluationMetricNames.SharedProductionPosture" should {
    "declare the exact shared production-posture metric name constants" in {
      assert(SharedProductionPosture.DefaultBeautySearchEsBacked == "default_beauty_search_es_backed")
      assert(SharedProductionPosture.QdrantOptInDisabledByDefault == "qdrant_opt_in_disabled_by_default")
      assert(SharedProductionPosture.QdrantProductionActivationApproved == "qdrant_production_activation_approved")
      assert(SharedProductionPosture.ProductionRouteActivated == "production_route_activated")
      assert(SharedProductionPosture.DefaultRouteSwitched == "default_route_switched")
      assert(SharedProductionPosture.ProductionBeautySearchCalled == "production_beauty_search_called")
      assert(SharedProductionPosture.EsClientCreated == "es_client_created")
      assert(SharedProductionPosture.QdrantClientCreated == "qdrant_client_created")
      assert(SharedProductionPosture.EsExecuted == "es_executed")
      assert(SharedProductionPosture.QdrantExecuted == "qdrant_executed")
      assert(SharedProductionPosture.RoutePluginDiHttpInvolved == "route_plugin_di_http_involved")
      assert(SharedProductionPosture.RealBackendCallRequired == "real_backend_call_required")
      assert(SharedProductionPosture.RealBackendCallImplemented == "real_backend_call_implemented")
      assert(SharedProductionPosture.HybridServingImplied == "hybrid_serving_implied")
      assert(SharedProductionPosture.FallbackImplied == "fallback_implied")
      assert(SharedProductionPosture.ScoreFusionImplied == "score_fusion_implied")
      assert(SharedProductionPosture.RerankingImplied == "reranking_implied")
      assert(SharedProductionPosture.ProductionTelemetryImplied == "production_telemetry_implied")
      assert(SharedProductionPosture.QualityGreenClaimed == "quality_green_claimed")
      assert(SharedProductionPosture.ProductionReadinessClaimed == "production_readiness_claimed")
      assert(SharedProductionPosture.RouteActivationClaimed == "route_activation_claimed")
      assert(SharedProductionPosture.ServingApprovalClaimed == "serving_approval_claimed")
    }

    "declare All in the exact source order" in {
      assert(
        SharedProductionPosture.All ==
          List(
            "default_beauty_search_es_backed",
            "qdrant_opt_in_disabled_by_default",
            "qdrant_production_activation_approved",
            "production_route_activated",
            "default_route_switched",
            "production_beauty_search_called",
            "es_client_created",
            "qdrant_client_created",
            "es_executed",
            "qdrant_executed",
            "route_plugin_di_http_involved",
            "real_backend_call_required",
            "real_backend_call_implemented",
            "hybrid_serving_implied",
            "fallback_implied",
            "score_fusion_implied",
            "reranking_implied",
            "production_telemetry_implied",
            "quality_green_claimed",
            "production_readiness_claimed",
            "route_activation_claimed",
            "serving_approval_claimed",
          )
      )
    }

    "declare All with no duplicate names" in {
      assert(SharedProductionPosture.All.distinct == SharedProductionPosture.All)
    }
  }
}
