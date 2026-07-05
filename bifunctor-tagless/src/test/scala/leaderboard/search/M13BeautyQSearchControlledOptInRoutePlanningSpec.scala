package leaderboard.search

import leaderboard.search.eval.{
  BeautyQSearchEvaluationMetricNames,
  M12BeautyQSearchFusionRerankingBoundaryFailureMatrix,
  M12BeautyQSearchFusionRerankingExperimentPlan,
  M12BeautyQSearchFusionRerankingPolicyCatalog,
  M12BeautyQSearchFusionRerankingSavedOutputSchema,
  M13BeautyQSearchControlledOptInRouteDeniedDriftCase,
  M13BeautyQSearchControlledOptInRoutePlanning,
  M13BeautyQSearchControlledOptInRoutePlanningMetric,
  M13BeautyQSearchControlledOptInRoutePlanningRenderer,
  M13BeautyQSearchControlledOptInRoutePlanningState,
}
import org.scalatest.wordspec.AnyWordSpec

import java.nio.charset.StandardCharsets
import scala.io.Source
import scala.util.Using

final class M13BeautyQSearchControlledOptInRoutePlanningSpec extends AnyWordSpec {

  private val artifactPath: String =
    "/leaderboard/search/eval/m13-beautyq-controlled-opt-in-route-planning.md"

  // Marketing/readiness tokens that must never appear as positive claims in the rendered artifact.
  private val forbiddenRenderedTokens: List[String] = List(
    "production_ready",
    "qdrant_ready",
    "hybrid_ready",
    "is production ready",
    "production-ready",
    "quality is green",
    "serving approval granted",
    "route activated",
    "route switched",
    "backend execution ready",
    "execution ready",
    "hybrid serving enabled",
    "fallback enabled",
    "score fusion enabled",
    "reranking enabled",
    "fusion enabled",
    "scoring enabled",
    "retrieval quality verified",
  )

  // Tokens that would betray a fabricated candidate/result/fusion/reranking payload. None may appear.
  private val fabricationTokens: List[String] = List(
    "took_ms",
    "\"hits\"",
    "\"_id\"",
    "\"results\"",
    "\"payload\"",
    "doc_id",
    "cosine_score",
    "embedding",
    "fused_score:",
    "reranked_position:",
    "quality_label:",
  )

  "M13BeautyQSearchControlledOptInRoutePlanning inputs" should {

    "consume the M12 closeout verdicts as planning input only" in {
      val summary = M13BeautyQSearchControlledOptInRoutePlanning.DefaultSummary

      assert(summary.consumedM12PolicyCatalogVerdict ==
        "m12_fusion_reranking_policy_catalog_ready_schema_only")
      assert(summary.consumedM12PolicyCatalogVerdict ==
        M12BeautyQSearchFusionRerankingPolicyCatalog.Verdict)
      assert(summary.consumedM12ExperimentPlanVerdict ==
        "m12_fusion_reranking_experiment_plan_ready_schema_only")
      assert(summary.consumedM12ExperimentPlanVerdict ==
        M12BeautyQSearchFusionRerankingExperimentPlan.Verdict)
      assert(summary.consumedM12BoundaryFailureMatrixVerdict ==
        "m12_fusion_reranking_boundary_failure_matrix_ready_schema_only")
      assert(summary.consumedM12BoundaryFailureMatrixVerdict ==
        M12BeautyQSearchFusionRerankingBoundaryFailureMatrix.Verdict)
      assert(summary.consumedM12SavedOutputSchemaVerdict ==
        "m12_fusion_reranking_saved_output_schema_ready_placeholder_only")
      assert(summary.consumedM12SavedOutputSchemaVerdict ==
        M12BeautyQSearchFusionRerankingSavedOutputSchema.Verdict)
    }

    "treat M12 closeout source truth as planning input, never quality evidence" in {
      val metrics = M13BeautyQSearchControlledOptInRoutePlanning.DefaultSummary.metrics

      assert(metricValue(metrics, "m13_consumes_m12_closeout_as_planning_input_only") == "true")
      assert(metricValue(metrics, "m13_does_not_treat_m12_placeholder_rows_as_quality_evidence") == "true")
      assert(metricValue(metrics, "m13_fabricates_no_evidence_from_m12_placeholder_rows") == "true")
    }
  }

  "M13BeautyQSearchControlledOptInRoutePlanning states" should {

    "define exactly the five stable route-planning states in stable order" in {
      val summary = M13BeautyQSearchControlledOptInRoutePlanning.DefaultSummary

      assert(summary.routePlanningStateCount == 5)
      assert(summary.routePlanningStates.map(_.render) == List(
        "current_es_default_preserved",
        "explicit_qdrant_opt_in_disabled_by_default",
        "experiment_route_planning_only",
        "production_activation_not_approved",
        "serving_approval_not_granted",
      ))
      assert(summary.routePlanningStates.map(_.render).distinct.size == 5)
      assert(summary.routePlanningStates.forall(_.boundaryHolds))
      assert(metricValue(summary.metrics, "route_planning_state_count") == "5")
      assert(metricValue(summary.metrics, "route_planning_states_boundary_holds") == "5")
    }

    "preserve the current ES-backed default route boundary" in {
      val row = M13BeautyQSearchControlledOptInRoutePlanning
        .stateRowFor(M13BeautyQSearchControlledOptInRoutePlanningState.CurrentEsDefaultPreserved)
        .getOrElse(fail("missing current_es_default_preserved state"))
      val b = M13BeautyQSearchControlledOptInRoutePlanning.DefaultSummary.boundary

      assert(row.boundaryHolds)
      assert(b.defaultBeautySearchEsBacked)
      assert(!b.defaultRouteSwitched)
    }

    "keep the explicit Qdrant opt-in disabled by default" in {
      val row = M13BeautyQSearchControlledOptInRoutePlanning
        .stateRowFor(M13BeautyQSearchControlledOptInRoutePlanningState.ExplicitQdrantOptInDisabledByDefault)
        .getOrElse(fail("missing explicit_qdrant_opt_in_disabled_by_default state"))
      val b = M13BeautyQSearchControlledOptInRoutePlanning.DefaultSummary.boundary

      assert(row.boundaryHolds)
      assert(b.qdrantOptInDisabledByDefault)
      assert(!b.qdrantExecuted)
    }

    "keep Qdrant production activation not approved" in {
      val row = M13BeautyQSearchControlledOptInRoutePlanning
        .stateRowFor(M13BeautyQSearchControlledOptInRoutePlanningState.ProductionActivationNotApproved)
        .getOrElse(fail("missing production_activation_not_approved state"))
      val b = M13BeautyQSearchControlledOptInRoutePlanning.DefaultSummary.boundary

      assert(row.boundaryHolds)
      assert(!b.qdrantProductionActivationApproved)
      assert(!b.productionRouteActivated)
    }

    "keep M13A planning-only and non-serving by default" in {
      val row = M13BeautyQSearchControlledOptInRoutePlanning
        .stateRowFor(M13BeautyQSearchControlledOptInRoutePlanningState.ExperimentRoutePlanningOnly)
        .getOrElse(fail("missing experiment_route_planning_only state"))
      val metrics = M13BeautyQSearchControlledOptInRoutePlanning.DefaultSummary.metrics

      assert(row.boundaryHolds)
      assert(metricValue(metrics, "m13_is_planning_only_not_route_activation") == "true")
      assert(metricValue(metrics, "m13_is_planning_only_not_route_switch") == "true")
      assert(metricValue(metrics, "m13_is_planning_only_not_serving_approval") == "true")
      assert(metricValue(metrics, "m13_is_planning_only_not_production_readiness") == "true")
      assert(metricValue(metrics, "m13_is_non_serving_by_default") == "true")
      assert(metricValue(metrics, "m13_does_not_alter_production_beauty_search") == "true")
    }

    "separate the four route concerns" in {
      val metrics = M13BeautyQSearchControlledOptInRoutePlanning.DefaultSummary.metrics

      assert(metricValue(metrics, "m13_separates_current_es_default_route") == "true")
      assert(metricValue(metrics, "m13_separates_disabled_qdrant_opt_in_path") == "true")
      assert(metricValue(metrics, "m13_separates_future_experiment_route_planning") == "true")
      assert(metricValue(metrics, "m13_separates_non_approved_production_activation") == "true")
    }
  }

  "M13BeautyQSearchControlledOptInRoutePlanning denied drift cases" should {

    "define exactly the fifteen stable denied drift cases in stable order, all denied" in {
      val summary = M13BeautyQSearchControlledOptInRoutePlanning.DefaultSummary

      assert(summary.deniedDriftCaseCount == 15)
      assert(summary.deniedDriftCases.map(_.render) == List(
        "default_route_switch",
        "production_route_activation",
        "implicit_qdrant_activation",
        "real_backend_execution",
        "route_plugin_di_http_change",
        "hybrid_serving",
        "fallback",
        "score_fusion",
        "reranking_execution",
        "production_telemetry",
        "quality_green_claim",
        "retrieval_quality_claim",
        "production_readiness_claim",
        "route_activation_claim",
        "serving_approval_claim",
      ))
      assert(summary.deniedDriftCases.map(_.render).distinct.size == 15)
      assert(summary.deniedDriftCases.forall(_.decision == "denied"))
      assert(summary.deniedDriftCases.forall(_.denialHolds))
      assert(metricValue(summary.metrics, "denied_drift_case_count") == "15")
      assert(metricValue(summary.metrics, "denied_drift_cases_all_denied") == "true")
      assert(metricValue(summary.metrics, "denied_drift_cases_denial_holds") == "15")
    }

    "deny default route switch, production activation, and implicit Qdrant activation" in {
      def denied(c: M13BeautyQSearchControlledOptInRouteDeniedDriftCase): Boolean =
        M13BeautyQSearchControlledOptInRoutePlanning
          .deniedDriftRowFor(c)
          .exists(row => row.decision == "denied" && row.denialHolds)

      assert(denied(M13BeautyQSearchControlledOptInRouteDeniedDriftCase.DefaultRouteSwitch))
      assert(denied(M13BeautyQSearchControlledOptInRouteDeniedDriftCase.ProductionRouteActivation))
      assert(denied(M13BeautyQSearchControlledOptInRouteDeniedDriftCase.ImplicitQdrantActivation))
    }

    "deny real backend execution and route/plugin/DI/HTTP change" in {
      def denied(c: M13BeautyQSearchControlledOptInRouteDeniedDriftCase): Boolean =
        M13BeautyQSearchControlledOptInRoutePlanning
          .deniedDriftRowFor(c)
          .exists(row => row.decision == "denied" && row.denialHolds)

      assert(denied(M13BeautyQSearchControlledOptInRouteDeniedDriftCase.RealBackendExecution))
      assert(denied(M13BeautyQSearchControlledOptInRouteDeniedDriftCase.RoutePluginDiHttpChange))
    }

    "deny hybrid serving, fallback, score fusion, reranking execution, and production telemetry" in {
      def denied(c: M13BeautyQSearchControlledOptInRouteDeniedDriftCase): Boolean =
        M13BeautyQSearchControlledOptInRoutePlanning
          .deniedDriftRowFor(c)
          .exists(row => row.decision == "denied" && row.denialHolds)

      assert(denied(M13BeautyQSearchControlledOptInRouteDeniedDriftCase.HybridServing))
      assert(denied(M13BeautyQSearchControlledOptInRouteDeniedDriftCase.Fallback))
      assert(denied(M13BeautyQSearchControlledOptInRouteDeniedDriftCase.ScoreFusion))
      assert(denied(M13BeautyQSearchControlledOptInRouteDeniedDriftCase.RerankingExecution))
      assert(denied(M13BeautyQSearchControlledOptInRouteDeniedDriftCase.ProductionTelemetry))
    }

    "deny quality-green, retrieval-quality, production-readiness, route-activation, and serving-approval claims" in {
      def denied(c: M13BeautyQSearchControlledOptInRouteDeniedDriftCase): Boolean =
        M13BeautyQSearchControlledOptInRoutePlanning
          .deniedDriftRowFor(c)
          .exists(row => row.decision == "denied" && row.denialHolds)

      assert(denied(M13BeautyQSearchControlledOptInRouteDeniedDriftCase.QualityGreenClaim))
      assert(denied(M13BeautyQSearchControlledOptInRouteDeniedDriftCase.RetrievalQualityClaim))
      assert(denied(M13BeautyQSearchControlledOptInRouteDeniedDriftCase.ProductionReadinessClaim))
      assert(denied(M13BeautyQSearchControlledOptInRouteDeniedDriftCase.RouteActivationClaim))
      assert(denied(M13BeautyQSearchControlledOptInRouteDeniedDriftCase.ServingApprovalClaim))
    }
  }

  "M13BeautyQSearchControlledOptInRoutePlanning verdict and boundary" should {

    "carry the planning-only route-planning readiness verdict, not activation or serving readiness" in {
      val summary = M13BeautyQSearchControlledOptInRoutePlanning.DefaultSummary

      assert(summary.verdict == "m13_controlled_opt_in_route_planning_ready_planning_only")
      assert(summary.m13RoutePlanningReady)
      assert(metricValue(summary.metrics, "m13_route_planning_ready") == "true")
      assert(!summary.boundary.qualityGreenClaimed)
      assert(!summary.boundary.productionReadinessClaimed)
      assert(!summary.boundary.routeActivationClaimed)
      assert(!summary.boundary.servingApprovalClaimed)
    }

    "require and implement no route/plugin/DI/HTTP or real backend client path" in {
      val b = M13BeautyQSearchControlledOptInRoutePlanning.DefaultSummary.boundary
      val metrics = M13BeautyQSearchControlledOptInRoutePlanning.DefaultSummary.metrics

      assert(!b.productionBeautySearchCalled)
      assert(!b.esClientCreated)
      assert(!b.qdrantClientCreated)
      assert(!b.esExecuted)
      assert(!b.qdrantExecuted)
      assert(!b.routePluginDiHttpInvolved)
      assert(!b.realBackendCallRequired)
      assert(!b.realBackendCallImplemented)
      assert(metricValue(metrics, "m13_requires_no_route_plugin_di_http_path") == "true")
      assert(metricValue(metrics, "m13_implements_no_route_plugin_di_http_path") == "true")
      assert(metricValue(metrics, "m13_requires_no_real_es_qdrant_backend_client") == "true")
      assert(metricValue(metrics, "m13_implements_no_real_es_qdrant_backend_client") == "true")
      assert(metricValue(metrics, BeautyQSearchEvaluationMetricNames.SharedProductionPosture.EsExecuted) == "false")
      assert(metricValue(metrics, BeautyQSearchEvaluationMetricNames.SharedProductionPosture.QdrantExecuted) == "false")
      assert(metricValue(metrics, BeautyQSearchEvaluationMetricNames.SharedProductionPosture.RoutePluginDiHttpInvolved) == "false")
      assert(metricValue(metrics, BeautyQSearchEvaluationMetricNames.SharedProductionPosture.RealBackendCallRequired) == "false")
      assert(metricValue(metrics, BeautyQSearchEvaluationMetricNames.SharedProductionPosture.RealBackendCallImplemented) == "false")
    }

    "keep all production and Qdrant activation boundaries false/not approved" in {
      val b = M13BeautyQSearchControlledOptInRoutePlanning.DefaultSummary.boundary
      val metrics = M13BeautyQSearchControlledOptInRoutePlanning.DefaultSummary.metrics

      assert(b.defaultBeautySearchEsBacked)
      assert(b.qdrantOptInDisabledByDefault)
      assert(!b.qdrantProductionActivationApproved)
      assert(!b.productionRouteActivated)
      assert(!b.defaultRouteSwitched)
      assert(metricValue(metrics, BeautyQSearchEvaluationMetricNames.SharedProductionPosture.QdrantProductionActivationApproved) == "false")
      assert(metricValue(metrics, BeautyQSearchEvaluationMetricNames.SharedProductionPosture.ProductionRouteActivated) == "false")
      assert(metricValue(metrics, BeautyQSearchEvaluationMetricNames.SharedProductionPosture.DefaultRouteSwitched) == "false")
      assert(metricValue(metrics, BeautyQSearchEvaluationMetricNames.SharedProductionPosture.DefaultBeautySearchEsBacked) == "true")
      assert(metricValue(metrics, BeautyQSearchEvaluationMetricNames.SharedProductionPosture.QdrantOptInDisabledByDefault) == "true")
    }

    "introduce no hybrid/fallback/fusion/reranking/production telemetry behavior" in {
      val b = M13BeautyQSearchControlledOptInRoutePlanning.DefaultSummary.boundary
      val metrics = M13BeautyQSearchControlledOptInRoutePlanning.DefaultSummary.metrics

      assert(!b.hybridServingImplied)
      assert(!b.fallbackImplied)
      assert(!b.scoreFusionImplied)
      assert(!b.rerankingImplied)
      assert(!b.productionTelemetryImplied)
      assert(metricValue(metrics, "m13_introduces_no_hybrid_serving") == "true")
      assert(metricValue(metrics, "m13_introduces_no_fallback") == "true")
      assert(metricValue(metrics, "m13_introduces_no_score_fusion") == "true")
      assert(metricValue(metrics, "m13_introduces_no_reranking_execution") == "true")
      assert(metricValue(metrics, "m13_introduces_no_production_telemetry") == "true")
      assert(metricValue(metrics, BeautyQSearchEvaluationMetricNames.SharedProductionPosture.HybridServingImplied) == "false")
      assert(metricValue(metrics, BeautyQSearchEvaluationMetricNames.SharedProductionPosture.FallbackImplied) == "false")
      assert(metricValue(metrics, BeautyQSearchEvaluationMetricNames.SharedProductionPosture.ScoreFusionImplied) == "false")
      assert(metricValue(metrics, BeautyQSearchEvaluationMetricNames.SharedProductionPosture.RerankingImplied) == "false")
      assert(metricValue(metrics, BeautyQSearchEvaluationMetricNames.SharedProductionPosture.ProductionTelemetryImplied) == "false")
    }
  }

  "M13BeautyQSearchControlledOptInRoutePlanning artifact" should {

    "carry no marketing readiness tokens as positive claims" in {
      val rendered = M13BeautyQSearchControlledOptInRoutePlanning.MarkdownArtifact.contents.toLowerCase

      forbiddenRenderedTokens.foreach { token =>
        assert(!rendered.contains(token), s"forbidden token present: $token")
      }
    }

    "fabricate no candidate/result/fusion/reranking payload tokens" in {
      val rendered = M13BeautyQSearchControlledOptInRoutePlanning.MarkdownArtifact.contents.toLowerCase

      fabricationTokens.foreach { token =>
        assert(!rendered.contains(token.toLowerCase), s"fabrication token present: $token")
      }
    }

    "include every required section and planning-only disclaimer" in {
      val rendered = M13BeautyQSearchControlledOptInRoutePlanning.MarkdownArtifact.contents

      assert(rendered.contains("## Artifact identity"))
      assert(rendered.contains("## Summary"))
      assert(rendered.contains("## Route planning states"))
      assert(rendered.contains("## Denied route-drift cases"))
      assert(rendered.contains("## Metrics"))
      assert(rendered.contains("## Boundary summary"))
      assert(rendered.contains("consumed_m12b_policy_catalog_verdict: m12_fusion_reranking_policy_catalog_ready_schema_only"))
      assert(rendered.contains("consumed_m12d_saved_output_schema_verdict: m12_fusion_reranking_saved_output_schema_ready_placeholder_only"))
      assert(rendered.contains("route_planning_state_count: 5"))
      assert(rendered.contains("denied_drift_case_count: 15"))
      assert(rendered.contains("planning/contract"))
      assert(rendered.contains("NOT a route activation"))
      assert(rendered.contains("NOT a route switch"))
      assert(rendered.contains("NOT a serving approval"))
      assert(rendered.contains("NOT a production-readiness claim"))
      assert(rendered.contains("NOT a real backend execution"))
    }

    "render a byte-for-byte deterministic checked-in artifact" in {
      val artifact = M13BeautyQSearchControlledOptInRoutePlanning.MarkdownArtifact
      val expected = readResource(artifactPath)

      assert(artifact.filename == "m13-beautyq-controlled-opt-in-route-planning.md")
      assert(artifact.contentType == "text/markdown; charset=utf-8")
      assert(
        artifact.contents == M13BeautyQSearchControlledOptInRoutePlanningRenderer
          .renderMarkdown(M13BeautyQSearchControlledOptInRoutePlanning.DefaultSummary),
      )
      assert(artifact.contents == expected)
      assert(
        M13BeautyQSearchControlledOptInRoutePlanningRenderer
          .renderMarkdown(M13BeautyQSearchControlledOptInRoutePlanning.build()) == expected,
      )
    }
  }

  private def metricValue(
    metrics: List[M13BeautyQSearchControlledOptInRoutePlanningMetric],
    name: String,
  ): String =
    metrics.find(_.name == name) match {
      case Some(metric) => metric.value
      case None         => fail(s"missing route-planning metric $name")
    }

  private def readResource(path: String): String =
    Option(getClass.getResourceAsStream(path)) match {
      case Some(value) =>
        Using.resource(Source.fromInputStream(value, StandardCharsets.UTF_8.name()))(_.mkString)
      case None =>
        fail(s"missing checked-in resource $path")
    }
}
