package leaderboard.search.eval

/** One stable M13 controlled explicit opt-in route-planning state.
  *
  * These are planning/contract states only, never an activated route, a route switch, a serving
  * approval, a production-readiness claim, or a real backend execution. Each state records a stance the
  * M13A planning package preserves about a future explicit opt-in route/module path; none of them
  * activates, switches, or serves anything.
  */
enum M13BeautyQSearchControlledOptInRoutePlanningState {
  case CurrentEsDefaultPreserved
  case ExplicitQdrantOptInDisabledByDefault
  case ExperimentRoutePlanningOnly
  case ProductionActivationNotApproved
  case ServingApprovalNotGranted

  def render: String =
    this match {
      case M13BeautyQSearchControlledOptInRoutePlanningState.CurrentEsDefaultPreserved            => "current_es_default_preserved"
      case M13BeautyQSearchControlledOptInRoutePlanningState.ExplicitQdrantOptInDisabledByDefault => "explicit_qdrant_opt_in_disabled_by_default"
      case M13BeautyQSearchControlledOptInRoutePlanningState.ExperimentRoutePlanningOnly          => "experiment_route_planning_only"
      case M13BeautyQSearchControlledOptInRoutePlanningState.ProductionActivationNotApproved      => "production_activation_not_approved"
      case M13BeautyQSearchControlledOptInRoutePlanningState.ServingApprovalNotGranted            => "serving_approval_not_granted"
    }

  /** Whether the consumed standing boundary confirms this planning state holds. */
  def holds(boundary: M10BeautyQSearchOfflineRoutingBoundary): Boolean =
    this match {
      case M13BeautyQSearchControlledOptInRoutePlanningState.CurrentEsDefaultPreserved =>
        boundary.defaultBeautySearchEsBacked && !boundary.defaultRouteSwitched
      case M13BeautyQSearchControlledOptInRoutePlanningState.ExplicitQdrantOptInDisabledByDefault =>
        boundary.qdrantOptInDisabledByDefault && !boundary.qdrantExecuted
      case M13BeautyQSearchControlledOptInRoutePlanningState.ExperimentRoutePlanningOnly =>
        boundary.offlinePlanningOnly && !boundary.routePluginDiHttpInvolved && !boundary.productionRouteActivated
      case M13BeautyQSearchControlledOptInRoutePlanningState.ProductionActivationNotApproved =>
        !boundary.qdrantProductionActivationApproved && !boundary.productionRouteActivated
      case M13BeautyQSearchControlledOptInRoutePlanningState.ServingApprovalNotGranted =>
        !boundary.servingApprovalClaimed && !boundary.routeActivationClaimed
    }
}

object M13BeautyQSearchControlledOptInRoutePlanningState {

  /** Stable state order: preserved ES default, disabled Qdrant opt-in, planning-only, then the two
    * not-approved/not-granted stances.
    */
  val stableOrder: List[M13BeautyQSearchControlledOptInRoutePlanningState] = List(
    CurrentEsDefaultPreserved,
    ExplicitQdrantOptInDisabledByDefault,
    ExperimentRoutePlanningOnly,
    ProductionActivationNotApproved,
    ServingApprovalNotGranted,
  )

  /** Deterministic planning rationale for a state; planning stance only, never an activation. */
  def rationale(state: M13BeautyQSearchControlledOptInRoutePlanningState): String =
    state match {
      case CurrentEsDefaultPreserved =>
        "Planning stance only: production /beauty-search stays ES-backed; M13A preserves the current default route and switches nothing."
      case ExplicitQdrantOptInDisabledByDefault =>
        "Planning stance only: the explicit Qdrant opt-in path stays disabled by default; M13A neither enables nor implicitly activates it."
      case ExperimentRoutePlanningOnly =>
        "Planning stance only: a future controlled experiment route/module is planned on paper; no route, plugin, DI, or HTTP path is required or implemented."
      case ProductionActivationNotApproved =>
        "Planning stance only: Qdrant production activation is not approved; M13A is not a production route activation."
      case ServingApprovalNotGranted =>
        "Planning stance only: serving approval is not granted; M13A claims no production readiness and grants no serving approval."
    }
}

/** One stable M13 denied route-drift case.
  *
  * Each denied case names a drift the M13A planning package explicitly refuses: activating, switching,
  * serving, executing a backend, or claiming quality/readiness/approval. Every case is `decision =
  * denied`; the standing boundary confirms the corresponding posture remains off.
  */
enum M13BeautyQSearchControlledOptInRouteDeniedDriftCase {
  case DefaultRouteSwitch
  case ProductionRouteActivation
  case ImplicitQdrantActivation
  case RealBackendExecution
  case RoutePluginDiHttpChange
  case HybridServing
  case Fallback
  case ScoreFusion
  case RerankingExecution
  case ProductionTelemetry
  case QualityGreenClaim
  case RetrievalQualityClaim
  case ProductionReadinessClaim
  case RouteActivationClaim
  case ServingApprovalClaim

  def render: String =
    this match {
      case M13BeautyQSearchControlledOptInRouteDeniedDriftCase.DefaultRouteSwitch        => "default_route_switch"
      case M13BeautyQSearchControlledOptInRouteDeniedDriftCase.ProductionRouteActivation => "production_route_activation"
      case M13BeautyQSearchControlledOptInRouteDeniedDriftCase.ImplicitQdrantActivation  => "implicit_qdrant_activation"
      case M13BeautyQSearchControlledOptInRouteDeniedDriftCase.RealBackendExecution      => "real_backend_execution"
      case M13BeautyQSearchControlledOptInRouteDeniedDriftCase.RoutePluginDiHttpChange   => "route_plugin_di_http_change"
      case M13BeautyQSearchControlledOptInRouteDeniedDriftCase.HybridServing             => "hybrid_serving"
      case M13BeautyQSearchControlledOptInRouteDeniedDriftCase.Fallback                  => "fallback"
      case M13BeautyQSearchControlledOptInRouteDeniedDriftCase.ScoreFusion               => "score_fusion"
      case M13BeautyQSearchControlledOptInRouteDeniedDriftCase.RerankingExecution        => "reranking_execution"
      case M13BeautyQSearchControlledOptInRouteDeniedDriftCase.ProductionTelemetry       => "production_telemetry"
      case M13BeautyQSearchControlledOptInRouteDeniedDriftCase.QualityGreenClaim         => "quality_green_claim"
      case M13BeautyQSearchControlledOptInRouteDeniedDriftCase.RetrievalQualityClaim     => "retrieval_quality_claim"
      case M13BeautyQSearchControlledOptInRouteDeniedDriftCase.ProductionReadinessClaim  => "production_readiness_claim"
      case M13BeautyQSearchControlledOptInRouteDeniedDriftCase.RouteActivationClaim      => "route_activation_claim"
      case M13BeautyQSearchControlledOptInRouteDeniedDriftCase.ServingApprovalClaim      => "serving_approval_claim"
    }

  /** The deterministic decision for every drift case is `denied`. */
  def decision: String = "denied"

  /** Whether the consumed standing boundary confirms this drift is denied (its posture stays off). */
  def denialHolds(boundary: M10BeautyQSearchOfflineRoutingBoundary): Boolean =
    this match {
      case M13BeautyQSearchControlledOptInRouteDeniedDriftCase.DefaultRouteSwitch =>
        !boundary.defaultRouteSwitched && boundary.defaultBeautySearchEsBacked
      case M13BeautyQSearchControlledOptInRouteDeniedDriftCase.ProductionRouteActivation =>
        !boundary.productionRouteActivated
      case M13BeautyQSearchControlledOptInRouteDeniedDriftCase.ImplicitQdrantActivation =>
        boundary.qdrantOptInDisabledByDefault && !boundary.qdrantProductionActivationApproved && !boundary.qdrantExecuted
      case M13BeautyQSearchControlledOptInRouteDeniedDriftCase.RealBackendExecution =>
        !boundary.esExecuted && !boundary.qdrantExecuted &&
          !boundary.realBackendCallRequired && !boundary.realBackendCallImplemented &&
          !boundary.esClientCreated && !boundary.qdrantClientCreated &&
          !boundary.productionBeautySearchCalled
      case M13BeautyQSearchControlledOptInRouteDeniedDriftCase.RoutePluginDiHttpChange =>
        !boundary.routePluginDiHttpInvolved
      case M13BeautyQSearchControlledOptInRouteDeniedDriftCase.HybridServing =>
        !boundary.hybridServingImplied
      case M13BeautyQSearchControlledOptInRouteDeniedDriftCase.Fallback =>
        !boundary.fallbackImplied
      case M13BeautyQSearchControlledOptInRouteDeniedDriftCase.ScoreFusion =>
        !boundary.scoreFusionImplied
      case M13BeautyQSearchControlledOptInRouteDeniedDriftCase.RerankingExecution =>
        !boundary.rerankingImplied
      case M13BeautyQSearchControlledOptInRouteDeniedDriftCase.ProductionTelemetry =>
        !boundary.productionTelemetryImplied
      case M13BeautyQSearchControlledOptInRouteDeniedDriftCase.QualityGreenClaim =>
        !boundary.qualityGreenClaimed
      case M13BeautyQSearchControlledOptInRouteDeniedDriftCase.RetrievalQualityClaim =>
        !boundary.qualityGreenClaimed
      case M13BeautyQSearchControlledOptInRouteDeniedDriftCase.ProductionReadinessClaim =>
        !boundary.productionReadinessClaimed
      case M13BeautyQSearchControlledOptInRouteDeniedDriftCase.RouteActivationClaim =>
        !boundary.routeActivationClaimed
      case M13BeautyQSearchControlledOptInRouteDeniedDriftCase.ServingApprovalClaim =>
        !boundary.servingApprovalClaimed
    }
}

object M13BeautyQSearchControlledOptInRouteDeniedDriftCase {

  /** Stable denied-case order: route switch/activation/implicit activation, real backend execution,
    * route/plugin/DI/http change, then serving behaviors and finally the five denied claims.
    */
  val stableOrder: List[M13BeautyQSearchControlledOptInRouteDeniedDriftCase] = List(
    DefaultRouteSwitch,
    ProductionRouteActivation,
    ImplicitQdrantActivation,
    RealBackendExecution,
    RoutePluginDiHttpChange,
    HybridServing,
    Fallback,
    ScoreFusion,
    RerankingExecution,
    ProductionTelemetry,
    QualityGreenClaim,
    RetrievalQualityClaim,
    ProductionReadinessClaim,
    RouteActivationClaim,
    ServingApprovalClaim,
  )

  /** Deterministic planning rationale for a denied drift case. */
  def rationale(deniedCase: M13BeautyQSearchControlledOptInRouteDeniedDriftCase): String =
    deniedCase match {
      case DefaultRouteSwitch =>
        "Denied: M13A does not switch the default route; production /beauty-search stays ES-backed."
      case ProductionRouteActivation =>
        "Denied: M13A does not activate any production route; route activation is out of scope."
      case ImplicitQdrantActivation =>
        "Denied: the Qdrant opt-in stays disabled by default; M13A never implicitly activates Qdrant."
      case RealBackendExecution =>
        "Denied: M13A requires and implements no real ES/Qdrant/backend client execution."
      case RoutePluginDiHttpChange =>
        "Denied: M13A introduces no route, plugin, DI, or HTTP behavior change."
      case HybridServing =>
        "Denied: M13A introduces no hybrid serving."
      case Fallback =>
        "Denied: M13A introduces no fallback."
      case ScoreFusion =>
        "Denied: M13A introduces no score fusion."
      case RerankingExecution =>
        "Denied: M13A introduces no reranking execution."
      case ProductionTelemetry =>
        "Denied: M13A introduces no production telemetry."
      case QualityGreenClaim =>
        "Denied: M13A makes no quality-green claim."
      case RetrievalQualityClaim =>
        "Denied: M13A makes no retrieval-quality claim."
      case ProductionReadinessClaim =>
        "Denied: M13A makes no production-readiness claim."
      case RouteActivationClaim =>
        "Denied: M13A makes no route-activation claim."
      case ServingApprovalClaim =>
        "Denied: M13A makes no serving-approval claim."
    }
}

final case class M13BeautyQSearchControlledOptInRoutePlanningStateRow(
  state: M13BeautyQSearchControlledOptInRoutePlanningState,
  rationale: String,
  boundaryHolds: Boolean,
) {
  def render: String = state.render
}

final case class M13BeautyQSearchControlledOptInRouteDeniedDriftRow(
  deniedCase: M13BeautyQSearchControlledOptInRouteDeniedDriftCase,
  decision: String,
  rationale: String,
  denialHolds: Boolean,
) {
  def render: String = deniedCase.render
}

final case class M13BeautyQSearchControlledOptInRoutePlanningMetric(
  name: String,
  value: String,
)

final case class M13BeautyQSearchControlledOptInRoutePlanningSummary(
  datasetId: String,
  artifactId: String,
  artifactVersion: String,
  consumedM12PolicyCatalogVerdict: String,
  consumedM12ExperimentPlanVerdict: String,
  consumedM12BoundaryFailureMatrixVerdict: String,
  consumedM12SavedOutputSchemaVerdict: String,
  routePlanningStates: List[M13BeautyQSearchControlledOptInRoutePlanningStateRow],
  routePlanningStateCount: Int,
  deniedDriftCases: List[M13BeautyQSearchControlledOptInRouteDeniedDriftRow],
  deniedDriftCaseCount: Int,
  m13RoutePlanningReady: Boolean,
  boundary: M10BeautyQSearchOfflineRoutingBoundary,
  verdict: String,
  metrics: List[M13BeautyQSearchControlledOptInRoutePlanningMetric],
)

/** M13 BeautyQ controlled explicit opt-in route/module planning contract: a pure offline
  * planning/contract artifact that defines the stable route-planning states and denied route-drift
  * cases for a *future* explicit opt-in route/module path.
  *
  * This is planning/contract work only. It is non-serving by default and does not alter existing
  * production `/beauty-search`. It defines planning contracts, readiness states, guardrail decisions,
  * and a documentation/reporting artifact; it is NOT a route activation, NOT a route switch, NOT a
  * serving approval, NOT a production-readiness claim, and NOT a real backend execution. It introduces
  * no hybrid serving, no fallback, no score fusion, no reranking execution, no production telemetry, no
  * backend clients, and no route/plugin/DI/HTTP behavior changes. It never calls production
  * `/beauty-search`, never creates an ES or Qdrant client, and never runs Elasticsearch or Qdrant.
  *
  * It separates four concerns: the current production ES-backed default route; the disabled-by-default
  * explicit Qdrant opt-in path; the future controlled experiment route/module planning; and the
  * non-approved production activation.
  *
  * It consumes the M12 closeout source truth (offline eval/planning/reporting only) as *planning input*
  * via the accepted M12 verdicts; it never treats M12 placeholder rows as quality evidence and
  * fabricates no evidence from them. The readiness it reports is M13 *route-planning* readiness only: it
  * claims no quality-green, no retrieval-quality, no production-readiness, no route-activation, and no
  * serving-approval.
  */
object M13BeautyQSearchControlledOptInRoutePlanning {

  val MarkdownFilename: String =
    "m13-beautyq-controlled-opt-in-route-planning.md"

  val ArtifactId: String = "m13-beautyq-controlled-opt-in-route-planning"

  val ArtifactVersion: String = "v1"

  /** Readiness verdict: M13 route-planning readiness only, planning-only. Deliberately carries no
    * quality-green, retrieval-quality, production-readiness, route-activation, or serving-approval claim.
    */
  val Verdict: String =
    "m13_controlled_opt_in_route_planning_ready_planning_only"

  /** Accepted M12B policy-catalog closeout verdict consumed as planning input only. */
  val ConsumedM12PolicyCatalogVerdict: String =
    M12BeautyQSearchFusionRerankingPolicyCatalog.Verdict

  /** Accepted M12B experiment-plan closeout verdict consumed as planning input only. */
  val ConsumedM12ExperimentPlanVerdict: String =
    M12BeautyQSearchFusionRerankingExperimentPlan.Verdict

  /** Accepted M12C boundary/failure matrix closeout verdict consumed as planning input only. */
  val ConsumedM12BoundaryFailureMatrixVerdict: String =
    M12BeautyQSearchFusionRerankingBoundaryFailureMatrix.Verdict

  /** Accepted M12D saved-output schema closeout verdict consumed as planning input only. */
  val ConsumedM12SavedOutputSchemaVerdict: String =
    M12BeautyQSearchFusionRerankingSavedOutputSchema.Verdict

  /** The standing offline boundary M13A planning consumes; every claim-bearing field stays false. */
  val Boundary: M10BeautyQSearchOfflineRoutingBoundary =
    M10BeautyQSearchOfflineRoutingBoundary.Standing

  /** The stable route-planning state rows in stable order. */
  val RoutePlanningStates: List[M13BeautyQSearchControlledOptInRoutePlanningStateRow] =
    M13BeautyQSearchControlledOptInRoutePlanningState.stableOrder.map { state =>
      M13BeautyQSearchControlledOptInRoutePlanningStateRow(
        state = state,
        rationale = M13BeautyQSearchControlledOptInRoutePlanningState.rationale(state),
        boundaryHolds = state.holds(Boundary),
      )
    }

  /** The stable denied route-drift rows in stable order. */
  val DeniedDriftCases: List[M13BeautyQSearchControlledOptInRouteDeniedDriftRow] =
    M13BeautyQSearchControlledOptInRouteDeniedDriftCase.stableOrder.map { deniedCase =>
      M13BeautyQSearchControlledOptInRouteDeniedDriftRow(
        deniedCase = deniedCase,
        decision = deniedCase.decision,
        rationale = M13BeautyQSearchControlledOptInRouteDeniedDriftCase.rationale(deniedCase),
        denialHolds = deniedCase.denialHolds(Boundary),
      )
    }

  val RoutePlanningStateCount: Int = RoutePlanningStates.size
  val DeniedDriftCaseCount: Int = DeniedDriftCases.size

  val DefaultSummary: M13BeautyQSearchControlledOptInRoutePlanningSummary =
    build()

  val MarkdownArtifact: M9OfflineEvalReportArtifact =
    M13BeautyQSearchControlledOptInRoutePlanningRenderer.markdownArtifact(
      filename = MarkdownFilename,
      summary = DefaultSummary,
    )

  def stateRowFor(
    state: M13BeautyQSearchControlledOptInRoutePlanningState
  ): Option[M13BeautyQSearchControlledOptInRoutePlanningStateRow] =
    RoutePlanningStates.find(_.state == state)

  def deniedDriftRowFor(
    deniedCase: M13BeautyQSearchControlledOptInRouteDeniedDriftCase
  ): Option[M13BeautyQSearchControlledOptInRouteDeniedDriftRow] =
    DeniedDriftCases.find(_.deniedCase == deniedCase)

  def build(): M13BeautyQSearchControlledOptInRoutePlanningSummary = {
    val b = Boundary

    // The package is ready when it defines exactly the five stable route-planning states and the
    // fifteen denied route-drift cases, every state's boundary stance holds, every denied case's
    // denial holds, all denied decisions are `denied`, and the standing boundary keeps the current
    // ES-backed default while every activation/serving/execution/claim posture stays off.
    val m13RoutePlanningReady =
      RoutePlanningStates.map(_.render).distinct.size == RoutePlanningStateCount &&
        RoutePlanningStateCount == 5 &&
        DeniedDriftCases.map(_.render).distinct.size == DeniedDriftCaseCount &&
        DeniedDriftCaseCount == 15 &&
        RoutePlanningStates.forall(_.boundaryHolds) &&
        DeniedDriftCases.forall(_.denialHolds) &&
        DeniedDriftCases.forall(_.decision == "denied") &&
        b.offlinePlanningOnly &&
        b.defaultBeautySearchEsBacked &&
        b.qdrantOptInDisabledByDefault &&
        !b.qdrantProductionActivationApproved &&
        !b.productionRouteActivated &&
        !b.defaultRouteSwitched &&
        !b.productionBeautySearchCalled &&
        !b.esClientCreated &&
        !b.qdrantClientCreated &&
        !b.esExecuted &&
        !b.qdrantExecuted &&
        !b.routePluginDiHttpInvolved &&
        !b.realBackendCallRequired &&
        !b.realBackendCallImplemented &&
        !b.hybridServingImplied &&
        !b.fallbackImplied &&
        !b.scoreFusionImplied &&
        !b.rerankingImplied &&
        !b.productionTelemetryImplied &&
        !b.qualityGreenClaimed &&
        !b.productionReadinessClaimed &&
        !b.routeActivationClaimed &&
        !b.servingApprovalClaimed

    val summaryWithoutMetrics = M13BeautyQSearchControlledOptInRoutePlanningSummary(
      datasetId = M9BeautyQSearchEvalQueryDataset.Metadata.datasetId,
      artifactId = ArtifactId,
      artifactVersion = ArtifactVersion,
      consumedM12PolicyCatalogVerdict = ConsumedM12PolicyCatalogVerdict,
      consumedM12ExperimentPlanVerdict = ConsumedM12ExperimentPlanVerdict,
      consumedM12BoundaryFailureMatrixVerdict = ConsumedM12BoundaryFailureMatrixVerdict,
      consumedM12SavedOutputSchemaVerdict = ConsumedM12SavedOutputSchemaVerdict,
      routePlanningStates = RoutePlanningStates,
      routePlanningStateCount = RoutePlanningStateCount,
      deniedDriftCases = DeniedDriftCases,
      deniedDriftCaseCount = DeniedDriftCaseCount,
      m13RoutePlanningReady = m13RoutePlanningReady,
      boundary = b,
      verdict = Verdict,
      metrics = Nil,
    )

    summaryWithoutMetrics.copy(metrics = metrics(summaryWithoutMetrics))
  }

  private def metrics(
    summary: M13BeautyQSearchControlledOptInRoutePlanningSummary
  ): List[M13BeautyQSearchControlledOptInRoutePlanningMetric] = {
    val b = summary.boundary
    List(
      metric("artifact_id", summary.artifactId),
      metric("artifact_version", summary.artifactVersion),
      metric("consumed_m12b_policy_catalog_verdict", summary.consumedM12PolicyCatalogVerdict),
      metric("consumed_m12b_experiment_plan_verdict", summary.consumedM12ExperimentPlanVerdict),
      metric("consumed_m12c_boundary_failure_matrix_verdict", summary.consumedM12BoundaryFailureMatrixVerdict),
      metric("consumed_m12d_saved_output_schema_verdict", summary.consumedM12SavedOutputSchemaVerdict),
      metric("route_planning_state_count", summary.routePlanningStateCount.toString),
      metric("route_planning_states_unique", summary.routePlanningStates.map(_.render).distinct.size.toString),
      metric("route_planning_states_boundary_holds", summary.routePlanningStates.count(_.boundaryHolds).toString),
      metric("denied_drift_case_count", summary.deniedDriftCaseCount.toString),
      metric("denied_drift_cases_unique", summary.deniedDriftCases.map(_.render).distinct.size.toString),
      metric("denied_drift_cases_denial_holds", summary.deniedDriftCases.count(_.denialHolds).toString),
      metric("denied_drift_cases_all_denied", summary.deniedDriftCases.forall(_.decision == "denied").toString),
      metric("m13_route_planning_ready", summary.m13RoutePlanningReady.toString),
      metric("m13_is_planning_only_not_route_activation", true.toString),
      metric("m13_is_planning_only_not_route_switch", true.toString),
      metric("m13_is_planning_only_not_serving_approval", true.toString),
      metric("m13_is_planning_only_not_production_readiness", true.toString),
      metric("m13_is_planning_only_not_real_backend_execution", true.toString),
      metric("m13_is_non_serving_by_default", true.toString),
      metric("m13_does_not_alter_production_beauty_search", true.toString),
      metric("m13_separates_current_es_default_route", true.toString),
      metric("m13_separates_disabled_qdrant_opt_in_path", true.toString),
      metric("m13_separates_future_experiment_route_planning", true.toString),
      metric("m13_separates_non_approved_production_activation", true.toString),
      metric("m13_consumes_m12_closeout_as_planning_input_only", true.toString),
      metric("m13_does_not_treat_m12_placeholder_rows_as_quality_evidence", true.toString),
      metric("m13_fabricates_no_evidence_from_m12_placeholder_rows", true.toString),
      metric("m13_requires_no_route_plugin_di_http_path", true.toString),
      metric("m13_implements_no_route_plugin_di_http_path", true.toString),
      metric("m13_requires_no_real_es_qdrant_backend_client", true.toString),
      metric("m13_implements_no_real_es_qdrant_backend_client", true.toString),
      metric("m13_introduces_no_hybrid_serving", true.toString),
      metric("m13_introduces_no_fallback", true.toString),
      metric("m13_introduces_no_score_fusion", true.toString),
      metric("m13_introduces_no_reranking_execution", true.toString),
      metric("m13_introduces_no_production_telemetry", true.toString),
      metric(BeautyQSearchEvaluationMetricNames.SharedProductionPosture.DefaultBeautySearchEsBacked, b.defaultBeautySearchEsBacked.toString),
      metric(BeautyQSearchEvaluationMetricNames.SharedProductionPosture.QdrantOptInDisabledByDefault, b.qdrantOptInDisabledByDefault.toString),
      metric(BeautyQSearchEvaluationMetricNames.SharedProductionPosture.QdrantProductionActivationApproved, b.qdrantProductionActivationApproved.toString),
      metric(BeautyQSearchEvaluationMetricNames.SharedProductionPosture.ProductionRouteActivated, b.productionRouteActivated.toString),
      metric(BeautyQSearchEvaluationMetricNames.SharedProductionPosture.DefaultRouteSwitched, b.defaultRouteSwitched.toString),
      metric(BeautyQSearchEvaluationMetricNames.SharedProductionPosture.ProductionBeautySearchCalled, b.productionBeautySearchCalled.toString),
      metric(BeautyQSearchEvaluationMetricNames.SharedProductionPosture.EsClientCreated, b.esClientCreated.toString),
      metric(BeautyQSearchEvaluationMetricNames.SharedProductionPosture.QdrantClientCreated, b.qdrantClientCreated.toString),
      metric(BeautyQSearchEvaluationMetricNames.SharedProductionPosture.EsExecuted, b.esExecuted.toString),
      metric(BeautyQSearchEvaluationMetricNames.SharedProductionPosture.QdrantExecuted, b.qdrantExecuted.toString),
      metric(BeautyQSearchEvaluationMetricNames.SharedProductionPosture.RoutePluginDiHttpInvolved, b.routePluginDiHttpInvolved.toString),
      metric(BeautyQSearchEvaluationMetricNames.SharedProductionPosture.RealBackendCallRequired, b.realBackendCallRequired.toString),
      metric(BeautyQSearchEvaluationMetricNames.SharedProductionPosture.RealBackendCallImplemented, b.realBackendCallImplemented.toString),
      metric(BeautyQSearchEvaluationMetricNames.SharedProductionPosture.HybridServingImplied, b.hybridServingImplied.toString),
      metric(BeautyQSearchEvaluationMetricNames.SharedProductionPosture.FallbackImplied, b.fallbackImplied.toString),
      metric(BeautyQSearchEvaluationMetricNames.SharedProductionPosture.ScoreFusionImplied, b.scoreFusionImplied.toString),
      metric(BeautyQSearchEvaluationMetricNames.SharedProductionPosture.RerankingImplied, b.rerankingImplied.toString),
      metric(BeautyQSearchEvaluationMetricNames.SharedProductionPosture.ProductionTelemetryImplied, b.productionTelemetryImplied.toString),
      metric(BeautyQSearchEvaluationMetricNames.SharedProductionPosture.QualityGreenClaimed, b.qualityGreenClaimed.toString),
      metric(BeautyQSearchEvaluationMetricNames.SharedProductionPosture.ProductionReadinessClaimed, b.productionReadinessClaimed.toString),
      metric(BeautyQSearchEvaluationMetricNames.SharedProductionPosture.RouteActivationClaimed, b.routeActivationClaimed.toString),
      metric(BeautyQSearchEvaluationMetricNames.SharedProductionPosture.ServingApprovalClaimed, b.servingApprovalClaimed.toString),
      metric("verdict", summary.verdict),
    )
  }

  private def metric(
    name: String,
    value: String,
  ): M13BeautyQSearchControlledOptInRoutePlanningMetric =
    M13BeautyQSearchControlledOptInRoutePlanningMetric(name, value)
}

object M13BeautyQSearchControlledOptInRoutePlanningRenderer {

  def renderMarkdown(summary: M13BeautyQSearchControlledOptInRoutePlanningSummary): String = {
    val builder = new StringBuilder

    line(builder, "# M13 BeautyQ Controlled Explicit Opt-In Route Planning")
    line(builder, "")
    line(builder, "Offline planning/contract artifact for a FUTURE explicit opt-in route/module path. This is")
    line(builder, "planning/contract work only: it is non-serving by default and does NOT alter existing production")
    line(builder, "/beauty-search. It defines stable route-planning states and denied route-drift cases. It is")
    line(builder, "NOT a route activation, NOT a route switch, NOT a serving approval, NOT a production-readiness claim,")
    line(builder, "and NOT a real backend execution: no ES or Qdrant client is created and neither backend is run, and")
    line(builder, "no route, plugin, DI, or HTTP behavior is required or implemented. It introduces no hybrid serving,")
    line(builder, "no fallback, no score fusion, no reranking execution, and no production telemetry.")
    line(builder, "")
    line(builder, "It separates the current production ES-backed default route, the disabled-by-default explicit")
    line(builder, "Qdrant opt-in path, the future controlled experiment route/module planning, and the non-approved")
    line(builder, "production activation. It consumes the M12 closeout source truth (offline eval/planning/reporting")
    line(builder, "only) as planning input via accepted verdicts only; it never treats M12 placeholder rows as")
    line(builder, "quality evidence and fabricates no evidence from them. This artifact reports M13 route-planning")
    line(builder, "readiness only (planning-only): it is not quality-green, not retrieval-quality, not production")
    line(builder, "readiness, not route activation, and not serving approval.")
    line(builder, "")
    line(builder, "## Artifact identity")
    line(builder, "")
    line(builder, s"- artifact_id: ${renderText(summary.artifactId)}")
    line(builder, s"- artifact_version: ${renderText(summary.artifactVersion)}")
    line(builder, s"- dataset_id: ${renderText(summary.datasetId)}")
    line(builder, "")
    line(builder, "## Summary")
    line(builder, "")
    line(builder, s"- verdict: ${renderText(summary.verdict)}")
    line(builder, s"- consumed_m12b_policy_catalog_verdict: ${renderText(summary.consumedM12PolicyCatalogVerdict)}")
    line(builder, s"- consumed_m12b_experiment_plan_verdict: ${renderText(summary.consumedM12ExperimentPlanVerdict)}")
    line(builder, s"- consumed_m12c_boundary_failure_matrix_verdict: ${renderText(summary.consumedM12BoundaryFailureMatrixVerdict)}")
    line(builder, s"- consumed_m12d_saved_output_schema_verdict: ${renderText(summary.consumedM12SavedOutputSchemaVerdict)}")
    line(builder, s"- route_planning_state_count: ${summary.routePlanningStateCount}")
    line(builder, s"- denied_drift_case_count: ${summary.deniedDriftCaseCount}")
    line(builder, s"- m13_route_planning_ready: ${summary.m13RoutePlanningReady}")
    line(builder, "")
    line(builder, "## Route planning states")
    line(builder, "")
    line(builder, "Each state is a planning stance only, never an activated route, a route switch, a serving approval,")
    line(builder, "or a real backend execution. The boundary_holds column reports that the standing offline boundary")
    line(builder, "confirms the stance.")
    line(builder, "")
    line(builder, "| route_planning_state | boundary_holds | rationale |")
    line(builder, "|---|---|---|")
    summary.routePlanningStates.foreach { row =>
      line(builder, s"| ${renderText(row.render)} | ${row.boundaryHolds} | ${renderText(row.rationale)} |")
    }
    line(builder, "")
    line(builder, "## Denied route-drift cases")
    line(builder, "")
    line(builder, "Each drift case is explicitly denied. The denial_holds column reports that the standing offline")
    line(builder, "boundary confirms the corresponding posture stays off. No denied case is ever permitted by M13A.")
    line(builder, "")
    line(builder, "| denied_drift_case | decision | denial_holds | rationale |")
    line(builder, "|---|---|---|---|")
    summary.deniedDriftCases.foreach { row =>
      line(builder, s"| ${renderText(row.render)} | ${renderText(row.decision)} | ${row.denialHolds} | ${renderText(row.rationale)} |")
    }
    line(builder, "")
    line(builder, "## Metrics")
    line(builder, "")
    line(builder, "| metric | value |")
    line(builder, "|---|---|")
    summary.metrics.foreach(metric => line(builder, s"| ${renderText(metric.name)} | ${renderText(metric.value)} |"))
    line(builder, "")
    line(builder, "## Boundary summary")
    line(builder, "")
    line(builder, "Boundary posture is reported as structured booleans in the Metrics table above, not as prose negations.")
    line(builder, "This is a planning/contract artifact, not a route activation, not a route switch, not a serving")
    line(builder, "approval, not a production-readiness claim, and not a real backend execution: no production route,")
    line(builder, "hybrid serving, fallback, score fusion, reranking execution, production telemetry, backend client,")
    line(builder, "or route/plugin/DI/HTTP change is implemented or claimed, and the M12 closeout source truth is")
    line(builder, "consumed as planning input only, never as quality evidence.")

    builder.result()
  }

  def markdownArtifact(
    filename: String,
    summary: M13BeautyQSearchControlledOptInRoutePlanningSummary,
  ): M9OfflineEvalReportArtifact =
    M9OfflineEvalReportArtifact(
      filename = filename,
      contentType = "text/markdown; charset=utf-8",
      contents = renderMarkdown(summary),
    )

  private def renderText(value: String): String =
    value
      .replace("\r\n", " ")
      .replace('\n', ' ')
      .replace('\r', ' ')
      .replace("|", "\\|")

  private def line(builder: StringBuilder, value: String): Unit = {
    builder.append(value)
    builder.append('\n')
  }
}
