package leaderboard.search.eval

/** Shared BeautyQ eval production-posture metric names: the metric keys repeated across the M9/M10
  * static/offline scorecards (`beautyq-search-contract`) and the M10-M14 offline planning/design
  * scorecards (`beautyq-search-wiring`) to prove "not production serving / not route activation / no
  * real backend execution / no hidden Qdrant activation" boundaries. This is a name-only declaration:
  * it centralizes the repeated string keys, not any metric value, order, or renderer.
  */
object BeautyQSearchEvaluationMetricNames {

  object SharedProductionPosture {
    val DefaultBeautySearchEsBacked = "default_beauty_search_es_backed"
    val QdrantOptInDisabledByDefault = "qdrant_opt_in_disabled_by_default"
    val QdrantProductionActivationApproved = "qdrant_production_activation_approved"
    val ProductionRouteActivated = "production_route_activated"
    val DefaultRouteSwitched = "default_route_switched"
    val ProductionBeautySearchCalled = "production_beauty_search_called"
    val EsClientCreated = "es_client_created"
    val QdrantClientCreated = "qdrant_client_created"
    val EsExecuted = "es_executed"
    val QdrantExecuted = "qdrant_executed"
    val RoutePluginDiHttpInvolved = "route_plugin_di_http_involved"
    val RealBackendCallRequired = "real_backend_call_required"
    val RealBackendCallImplemented = "real_backend_call_implemented"
    val HybridServingImplied = "hybrid_serving_implied"
    val FallbackImplied = "fallback_implied"
    val ScoreFusionImplied = "score_fusion_implied"
    val RerankingImplied = "reranking_implied"
    val ProductionTelemetryImplied = "production_telemetry_implied"
    val QualityGreenClaimed = "quality_green_claimed"
    val ProductionReadinessClaimed = "production_readiness_claimed"
    val RouteActivationClaimed = "route_activation_claimed"
    val ServingApprovalClaimed = "serving_approval_claimed"

    val All: List[String] = List(
      DefaultBeautySearchEsBacked,
      QdrantOptInDisabledByDefault,
      QdrantProductionActivationApproved,
      ProductionRouteActivated,
      DefaultRouteSwitched,
      ProductionBeautySearchCalled,
      EsClientCreated,
      QdrantClientCreated,
      EsExecuted,
      QdrantExecuted,
      RoutePluginDiHttpInvolved,
      RealBackendCallRequired,
      RealBackendCallImplemented,
      HybridServingImplied,
      FallbackImplied,
      ScoreFusionImplied,
      RerankingImplied,
      ProductionTelemetryImplied,
      QualityGreenClaimed,
      ProductionReadinessClaimed,
      RouteActivationClaimed,
      ServingApprovalClaimed,
    )
  }
}
