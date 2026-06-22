package leaderboard.search

import leaderboard.search.eval.{
  M13BeautyQSearchControlledOptInRoutePlanning,
  M14BeautyQSearchRouteGateReadinessDeniedDriftCase,
  M14BeautyQSearchRouteGateReadinessDesign,
  M14BeautyQSearchRouteGateReadinessDesignMetric,
  M14BeautyQSearchRouteGateReadinessDesignRenderer,
  M14BeautyQSearchRouteGateReadinessDesignState,
  M14BeautyQSearchServingReadinessInput,
}
import org.scalatest.wordspec.AnyWordSpec

import java.nio.charset.StandardCharsets
import scala.io.Source
import scala.util.Using

final class M14BeautyQSearchRouteGateReadinessDesignSpec extends AnyWordSpec {

  private val artifactPath: String =
    "/leaderboard/search/eval/m14-beautyq-route-gate-readiness-design.md"

  // Marketing/readiness tokens that must never appear as positive claims in the rendered artifact.
  private val forbiddenRenderedTokens: List[String] = List(
    "production_ready",
    "qdrant_ready",
    "runtime gate ready",
    "gate ready",
    "is production ready",
    "production-ready",
    "quality is green",
    "serving approval granted",
    "route activated",
    "route switched",
    "503 implemented",
    "http 503 enabled",
    "backend execution ready",
    "execution ready",
    "retrieval quality verified",
  )

  // Tokens that would betray a fabricated runtime gate / HTTP / backend payload. None may appear.
  private val fabricationTokens: List[String] = List(
    "took_ms",
    "\"hits\"",
    "\"_id\"",
    "\"results\"",
    "\"payload\"",
    "status_code:",
    "http/1.1 503",
    "retry-after:",
    "doc_id",
    "cosine_score",
    "embedding",
  )

  "M14BeautyQSearchRouteGateReadinessDesign inputs" should {

    "consume the M13A closeout verdict as planning input only" in {
      val summary = M14BeautyQSearchRouteGateReadinessDesign.DefaultSummary

      assert(summary.consumedM13RoutePlanningVerdict ==
        "m13_controlled_opt_in_route_planning_ready_planning_only")
      assert(summary.consumedM13RoutePlanningVerdict ==
        M13BeautyQSearchControlledOptInRoutePlanning.Verdict)
    }

    "treat M12/M13 planning artifacts as planning input, never quality evidence" in {
      val metrics = M14BeautyQSearchRouteGateReadinessDesign.DefaultSummary.metrics

      assert(metricValue(metrics, "m14_consumes_m13_closeout_as_planning_input_only") == "true")
      assert(metricValue(metrics, "m14_does_not_treat_m12_m13_planning_artifacts_as_quality_evidence") == "true")
      assert(metricValue(metrics, "m14_fabricates_no_evidence_from_planning_artifacts") == "true")
    }
  }

  "M14BeautyQSearchRouteGateReadinessDesign states" should {

    "define exactly the six stable design states in stable order" in {
      val summary = M14BeautyQSearchRouteGateReadinessDesign.DefaultSummary

      assert(summary.designStateCount == 6)
      assert(summary.designStates.map(_.render) == List(
        "current_no_gate_production_behavior",
        "future_route_gate_design_requirement",
        "future_serving_readiness_decision_inputs",
        "future_http_failure_semantics",
        "production_activation_not_approved",
        "explicit_opt_in_disabled_by_default",
      ))
      assert(summary.designStates.map(_.render).distinct.size == 6)
      assert(summary.designStates.forall(_.boundaryHolds))
      assert(metricValue(summary.metrics, "design_state_count") == "6")
      assert(metricValue(summary.metrics, "design_states_boundary_holds") == "6")
    }

    "preserve the current no-gate ES-backed production behavior" in {
      val row = M14BeautyQSearchRouteGateReadinessDesign
        .stateRowFor(M14BeautyQSearchRouteGateReadinessDesignState.CurrentNoGateProductionBehavior)
        .getOrElse(fail("missing current_no_gate_production_behavior state"))
      val b = M14BeautyQSearchRouteGateReadinessDesign.DefaultSummary.boundary

      assert(row.boundaryHolds)
      assert(b.defaultBeautySearchEsBacked)
      assert(!b.defaultRouteSwitched)
      assert(!b.productionRouteActivated)
      assert(metricValue(M14BeautyQSearchRouteGateReadinessDesign.DefaultSummary.metrics,
        "m14_preserves_current_no_gate_production_behavior") == "true")
    }

    "keep the future route gate design-only and not implemented" in {
      val row = M14BeautyQSearchRouteGateReadinessDesign
        .stateRowFor(M14BeautyQSearchRouteGateReadinessDesignState.FutureRouteGateDesignRequirement)
        .getOrElse(fail("missing future_route_gate_design_requirement state"))
      val b = M14BeautyQSearchRouteGateReadinessDesign.DefaultSummary.boundary
      val metrics = M14BeautyQSearchRouteGateReadinessDesign.DefaultSummary.metrics

      assert(row.boundaryHolds)
      assert(b.offlinePlanningOnly)
      assert(!b.routePluginDiHttpInvolved)
      assert(!b.productionRouteActivated)
      assert(metricValue(metrics, "m14_is_design_only_not_runtime_route_gate") == "true")
      assert(metricValue(metrics, "m14_implements_no_runtime_route_gate") == "true")
    }

    "keep the future HTTP failure semantics design-only and not implemented" in {
      val row = M14BeautyQSearchRouteGateReadinessDesign
        .stateRowFor(M14BeautyQSearchRouteGateReadinessDesignState.FutureHttpFailureSemantics)
        .getOrElse(fail("missing future_http_failure_semantics state"))
      val b = M14BeautyQSearchRouteGateReadinessDesign.DefaultSummary.boundary
      val metrics = M14BeautyQSearchRouteGateReadinessDesign.DefaultSummary.metrics

      assert(row.boundaryHolds)
      assert(!b.routePluginDiHttpInvolved)
      assert(!b.productionBeautySearchCalled)
      assert(metricValue(metrics, "m14_is_design_only_not_http_503_behavior") == "true")
      assert(metricValue(metrics, "m14_implements_no_http_503_behavior") == "true")
    }

    "keep Qdrant production activation not approved and opt-in disabled by default" in {
      val activationRow = M14BeautyQSearchRouteGateReadinessDesign
        .stateRowFor(M14BeautyQSearchRouteGateReadinessDesignState.ProductionActivationNotApproved)
        .getOrElse(fail("missing production_activation_not_approved state"))
      val optInRow = M14BeautyQSearchRouteGateReadinessDesign
        .stateRowFor(M14BeautyQSearchRouteGateReadinessDesignState.ExplicitOptInDisabledByDefault)
        .getOrElse(fail("missing explicit_opt_in_disabled_by_default state"))
      val b = M14BeautyQSearchRouteGateReadinessDesign.DefaultSummary.boundary

      assert(activationRow.boundaryHolds)
      assert(optInRow.boundaryHolds)
      assert(!b.qdrantProductionActivationApproved)
      assert(!b.productionRouteActivated)
      assert(b.qdrantOptInDisabledByDefault)
      assert(!b.qdrantExecuted)
    }

    "separate the six route-gate / serving-readiness concerns" in {
      val metrics = M14BeautyQSearchRouteGateReadinessDesign.DefaultSummary.metrics

      assert(metricValue(metrics, "m14_separates_current_no_gate_production_behavior") == "true")
      assert(metricValue(metrics, "m14_separates_future_route_gate_design_requirement") == "true")
      assert(metricValue(metrics, "m14_separates_future_serving_readiness_decision_inputs") == "true")
      assert(metricValue(metrics, "m14_separates_future_http_failure_semantics") == "true")
      assert(metricValue(metrics, "m14_separates_non_approved_production_activation") == "true")
      assert(metricValue(metrics, "m14_separates_explicit_opt_in_disabled_by_default") == "true")
    }
  }

  "M14BeautyQSearchRouteGateReadinessDesign readiness inputs" should {

    "define exactly the six design-only readiness inputs in stable order" in {
      val summary = M14BeautyQSearchRouteGateReadinessDesign.DefaultSummary

      assert(summary.readinessInputCount == 6)
      assert(summary.readinessInputs.map(_.render) == List(
        "lifecycle_readiness",
        "backend_availability",
        "seed_resource_readiness",
        "activation_approval",
        "rollback_availability",
        "operator_visibility",
      ))
      assert(summary.readinessInputs.map(_.render).distinct.size == 6)
      assert(metricValue(summary.metrics, "readiness_input_count") == "6")
    }

    "keep every readiness input a data-only planning input, never runtime-evaluated" in {
      val summary = M14BeautyQSearchRouteGateReadinessDesign.DefaultSummary

      assert(summary.readinessInputs.forall(_.designOnly))
      assert(summary.readinessInputs.forall(!_.runtimeEvaluated))
      assert(metricValue(summary.metrics, "readiness_inputs_design_only") == "6")
      assert(metricValue(summary.metrics, "readiness_inputs_runtime_evaluated") == "0")
      assert(metricValue(summary.metrics, "m14_readiness_inputs_are_data_only_planning_inputs") == "true")
    }

    "expose each named design-only readiness input" in {
      M14BeautyQSearchServingReadinessInput.stableOrder.foreach { input =>
        val row = M14BeautyQSearchRouteGateReadinessDesign
          .readinessInputRowFor(input)
          .getOrElse(fail(s"missing readiness input ${input.render}"))
        assert(row.designOnly)
        assert(!row.runtimeEvaluated)
      }
    }
  }

  "M14BeautyQSearchRouteGateReadinessDesign denied drift cases" should {

    "define exactly the ten stable denied drift cases in stable order, all denied" in {
      val summary = M14BeautyQSearchRouteGateReadinessDesign.DefaultSummary

      assert(summary.deniedDriftCaseCount == 10)
      assert(summary.deniedDriftCases.map(_.render) == List(
        "implement_http_503_now",
        "change_beauty_search_behavior_now",
        "route_plugin_di_http_change",
        "real_backend_execution",
        "qdrant_production_activation",
        "route_activation",
        "default_route_switch",
        "production_readiness_claim",
        "serving_approval_claim",
        "treat_planning_artifacts_as_quality_evidence",
      ))
      assert(summary.deniedDriftCases.map(_.render).distinct.size == 10)
      assert(summary.deniedDriftCases.forall(_.decision == "denied"))
      assert(summary.deniedDriftCases.forall(_.denialHolds))
      assert(metricValue(summary.metrics, "denied_drift_case_count") == "10")
      assert(metricValue(summary.metrics, "denied_drift_cases_all_denied") == "true")
      assert(metricValue(summary.metrics, "denied_drift_cases_denial_holds") == "10")
    }

    "deny implementing HTTP 503 now and changing /beauty-search behavior now" in {
      assert(denied(M14BeautyQSearchRouteGateReadinessDeniedDriftCase.ImplementHttp503Now))
      assert(denied(M14BeautyQSearchRouteGateReadinessDeniedDriftCase.ChangeBeautySearchBehaviorNow))
    }

    "deny route/plugin/DI/HTTP change and real backend execution" in {
      assert(denied(M14BeautyQSearchRouteGateReadinessDeniedDriftCase.RoutePluginDiHttpChange))
      assert(denied(M14BeautyQSearchRouteGateReadinessDeniedDriftCase.RealBackendExecution))
    }

    "deny Qdrant production activation, route activation, and default route switch" in {
      assert(denied(M14BeautyQSearchRouteGateReadinessDeniedDriftCase.QdrantProductionActivation))
      assert(denied(M14BeautyQSearchRouteGateReadinessDeniedDriftCase.RouteActivation))
      assert(denied(M14BeautyQSearchRouteGateReadinessDeniedDriftCase.DefaultRouteSwitch))
    }

    "deny production-readiness, serving-approval, and quality-evidence misuse claims" in {
      assert(denied(M14BeautyQSearchRouteGateReadinessDeniedDriftCase.ProductionReadinessClaim))
      assert(denied(M14BeautyQSearchRouteGateReadinessDeniedDriftCase.ServingApprovalClaim))
      assert(denied(M14BeautyQSearchRouteGateReadinessDeniedDriftCase.TreatPlanningArtifactsAsQualityEvidence))
    }
  }

  "M14BeautyQSearchRouteGateReadinessDesign verdict and boundary" should {

    "carry the design-only design-contract readiness verdict, not runtime gate or production readiness" in {
      val summary = M14BeautyQSearchRouteGateReadinessDesign.DefaultSummary

      assert(summary.verdict == "m14_route_gate_readiness_design_contract_ready_design_only")
      assert(summary.m14RouteGateReadinessDesignReady)
      assert(metricValue(summary.metrics, "m14_route_gate_readiness_design_ready") == "true")
      assert(metricValue(summary.metrics, "m14_is_design_contract_readiness_only") == "true")
      assert(metricValue(summary.metrics, "m14_is_not_runtime_gate_readiness") == "true")
      assert(metricValue(summary.metrics, "m14_is_not_production_readiness") == "true")
      assert(!summary.boundary.qualityGreenClaimed)
      assert(!summary.boundary.productionReadinessClaimed)
      assert(!summary.boundary.routeActivationClaimed)
      assert(!summary.boundary.servingApprovalClaimed)
    }

    "require and implement no route/plugin/DI/HTTP or real backend client path" in {
      val b = M14BeautyQSearchRouteGateReadinessDesign.DefaultSummary.boundary
      val metrics = M14BeautyQSearchRouteGateReadinessDesign.DefaultSummary.metrics

      assert(!b.productionBeautySearchCalled)
      assert(!b.esClientCreated)
      assert(!b.qdrantClientCreated)
      assert(!b.esExecuted)
      assert(!b.qdrantExecuted)
      assert(!b.routePluginDiHttpInvolved)
      assert(!b.realBackendCallRequired)
      assert(!b.realBackendCallImplemented)
      assert(metricValue(metrics, "m14_requires_no_route_plugin_di_http_path") == "true")
      assert(metricValue(metrics, "m14_implements_no_route_plugin_di_http_path") == "true")
      assert(metricValue(metrics, "m14_requires_no_real_es_qdrant_backend_client") == "true")
      assert(metricValue(metrics, "m14_implements_no_real_es_qdrant_backend_client") == "true")
      assert(metricValue(metrics, "es_executed") == "false")
      assert(metricValue(metrics, "qdrant_executed") == "false")
      assert(metricValue(metrics, "route_plugin_di_http_involved") == "false")
      assert(metricValue(metrics, "real_backend_call_required") == "false")
      assert(metricValue(metrics, "real_backend_call_implemented") == "false")
    }

    "implement no runtime route gate, HTTP 503, activation, or default switch" in {
      val metrics = M14BeautyQSearchRouteGateReadinessDesign.DefaultSummary.metrics

      assert(metricValue(metrics, "m14_implements_no_runtime_route_gate") == "true")
      assert(metricValue(metrics, "m14_implements_no_http_503_behavior") == "true")
      assert(metricValue(metrics, "m14_enables_no_production_route_activation") == "true")
      assert(metricValue(metrics, "m14_enables_no_default_route_switch") == "true")
      assert(metricValue(metrics, "m14_does_not_change_beauty_search_behavior_now") == "true")
    }

    "keep all production and Qdrant activation boundaries false/not approved" in {
      val b = M14BeautyQSearchRouteGateReadinessDesign.DefaultSummary.boundary
      val metrics = M14BeautyQSearchRouteGateReadinessDesign.DefaultSummary.metrics

      assert(b.defaultBeautySearchEsBacked)
      assert(b.qdrantOptInDisabledByDefault)
      assert(!b.qdrantProductionActivationApproved)
      assert(!b.productionRouteActivated)
      assert(!b.defaultRouteSwitched)
      assert(metricValue(metrics, "qdrant_production_activation_approved") == "false")
      assert(metricValue(metrics, "production_route_activated") == "false")
      assert(metricValue(metrics, "default_route_switched") == "false")
      assert(metricValue(metrics, "default_beauty_search_es_backed") == "true")
      assert(metricValue(metrics, "qdrant_opt_in_disabled_by_default") == "true")
    }
  }

  "M14BeautyQSearchRouteGateReadinessDesign artifact" should {

    "carry no marketing readiness tokens as positive claims" in {
      val rendered = M14BeautyQSearchRouteGateReadinessDesign.MarkdownArtifact.contents.toLowerCase

      forbiddenRenderedTokens.foreach { token =>
        assert(!rendered.contains(token), s"forbidden token present: $token")
      }
    }

    "fabricate no runtime gate / HTTP / backend payload tokens" in {
      val rendered = M14BeautyQSearchRouteGateReadinessDesign.MarkdownArtifact.contents.toLowerCase

      fabricationTokens.foreach { token =>
        assert(!rendered.contains(token.toLowerCase), s"fabrication token present: $token")
      }
    }

    "include every required section and design-only disclaimer" in {
      val rendered = M14BeautyQSearchRouteGateReadinessDesign.MarkdownArtifact.contents

      assert(rendered.contains("## Artifact identity"))
      assert(rendered.contains("## Summary"))
      assert(rendered.contains("## Route-gate design states"))
      assert(rendered.contains("## Serving-readiness design inputs"))
      assert(rendered.contains("## Denied design-drift cases"))
      assert(rendered.contains("## Metrics"))
      assert(rendered.contains("## Boundary summary"))
      assert(rendered.contains("consumed_m13_route_planning_verdict: m13_controlled_opt_in_route_planning_ready_planning_only"))
      assert(rendered.contains("design_state_count: 6"))
      assert(rendered.contains("readiness_input_count: 6"))
      assert(rendered.contains("denied_drift_case_count: 10"))
      assert(rendered.contains("design/contract"))
      assert(rendered.contains("NOT a runtime route gate"))
      assert(rendered.contains("NOT an HTTP 503"))
      assert(rendered.contains("NOT a route activation"))
      assert(rendered.contains("NOT a default route switch"))
      assert(rendered.contains("NOT a serving approval"))
      assert(rendered.contains("NOT a production-readiness claim"))
      assert(rendered.contains("NOT a real backend execution"))
    }

    "render a byte-for-byte deterministic checked-in artifact" in {
      val artifact = M14BeautyQSearchRouteGateReadinessDesign.MarkdownArtifact
      val expected = readResource(artifactPath)

      assert(artifact.filename == "m14-beautyq-route-gate-readiness-design.md")
      assert(artifact.contentType == "text/markdown; charset=utf-8")
      assert(
        artifact.contents == M14BeautyQSearchRouteGateReadinessDesignRenderer
          .renderMarkdown(M14BeautyQSearchRouteGateReadinessDesign.DefaultSummary),
      )
      assert(artifact.contents == expected)
      assert(
        M14BeautyQSearchRouteGateReadinessDesignRenderer
          .renderMarkdown(M14BeautyQSearchRouteGateReadinessDesign.build()) == expected,
      )
    }
  }

  private def denied(c: M14BeautyQSearchRouteGateReadinessDeniedDriftCase): Boolean =
    M14BeautyQSearchRouteGateReadinessDesign
      .deniedDriftRowFor(c)
      .exists(row => row.decision == "denied" && row.denialHolds)

  private def metricValue(
    metrics: List[M14BeautyQSearchRouteGateReadinessDesignMetric],
    name: String,
  ): String =
    metrics.find(_.name == name) match {
      case Some(metric) => metric.value
      case None         => fail(s"missing route-gate design metric $name")
    }

  private def readResource(path: String): String =
    Option(getClass.getResourceAsStream(path)) match {
      case Some(value) =>
        Using.resource(Source.fromInputStream(value, StandardCharsets.UTF_8.name()))(_.mkString)
      case None =>
        fail(s"missing checked-in resource $path")
    }
}
