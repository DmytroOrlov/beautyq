package leaderboard.search.eval

/** One stable M14 route-gate / serving-readiness design state.
  *
  * These are design/contract states only, never an activated route gate, a runtime HTTP 503 path, a
  * serving approval, a production-readiness claim, or a real backend execution. Each state records a
  * stance the M14 design contract holds about route-gate / serving-readiness *design*; none of them
  * gates, serves, fails, switches, or activates anything at runtime.
  */
enum M14BeautyQSearchRouteGateReadinessDesignState {
  case CurrentNoGateProductionBehavior
  case FutureRouteGateDesignRequirement
  case FutureServingReadinessDecisionInputs
  case FutureHttpFailureSemantics
  case ProductionActivationNotApproved
  case ExplicitOptInDisabledByDefault

  def render: String =
    this match {
      case M14BeautyQSearchRouteGateReadinessDesignState.CurrentNoGateProductionBehavior    => "current_no_gate_production_behavior"
      case M14BeautyQSearchRouteGateReadinessDesignState.FutureRouteGateDesignRequirement   => "future_route_gate_design_requirement"
      case M14BeautyQSearchRouteGateReadinessDesignState.FutureServingReadinessDecisionInputs => "future_serving_readiness_decision_inputs"
      case M14BeautyQSearchRouteGateReadinessDesignState.FutureHttpFailureSemantics         => "future_http_failure_semantics"
      case M14BeautyQSearchRouteGateReadinessDesignState.ProductionActivationNotApproved    => "production_activation_not_approved"
      case M14BeautyQSearchRouteGateReadinessDesignState.ExplicitOptInDisabledByDefault     => "explicit_opt_in_disabled_by_default"
    }

  /** Whether the consumed standing boundary confirms this design state holds. */
  def holds(boundary: M10BeautyQSearchOfflineRoutingBoundary): Boolean =
    this match {
      case M14BeautyQSearchRouteGateReadinessDesignState.CurrentNoGateProductionBehavior =>
        boundary.defaultBeautySearchEsBacked && !boundary.defaultRouteSwitched && !boundary.productionRouteActivated
      case M14BeautyQSearchRouteGateReadinessDesignState.FutureRouteGateDesignRequirement =>
        boundary.offlinePlanningOnly && !boundary.routePluginDiHttpInvolved && !boundary.productionRouteActivated
      case M14BeautyQSearchRouteGateReadinessDesignState.FutureServingReadinessDecisionInputs =>
        boundary.offlinePlanningOnly && !boundary.realBackendCallRequired && !boundary.realBackendCallImplemented
      case M14BeautyQSearchRouteGateReadinessDesignState.FutureHttpFailureSemantics =>
        !boundary.routePluginDiHttpInvolved && !boundary.productionRouteActivated && !boundary.productionBeautySearchCalled
      case M14BeautyQSearchRouteGateReadinessDesignState.ProductionActivationNotApproved =>
        !boundary.qdrantProductionActivationApproved && !boundary.productionRouteActivated
      case M14BeautyQSearchRouteGateReadinessDesignState.ExplicitOptInDisabledByDefault =>
        boundary.qdrantOptInDisabledByDefault && !boundary.qdrantExecuted
    }
}

object M14BeautyQSearchRouteGateReadinessDesignState {

  /** Stable state order: current no-gate behavior, future gate design requirement, future serving
    * readiness decision inputs, future HTTP failure semantics, then the non-approved activation and the
    * disabled-by-default opt-in stances.
    */
  val stableOrder: List[M14BeautyQSearchRouteGateReadinessDesignState] = List(
    CurrentNoGateProductionBehavior,
    FutureRouteGateDesignRequirement,
    FutureServingReadinessDecisionInputs,
    FutureHttpFailureSemantics,
    ProductionActivationNotApproved,
    ExplicitOptInDisabledByDefault,
  )

  /** Deterministic design rationale for a state; design stance only, never a runtime gate. */
  def rationale(state: M14BeautyQSearchRouteGateReadinessDesignState): String =
    state match {
      case CurrentNoGateProductionBehavior =>
        "Design stance only: current production /beauty-search has no route gate and stays ES-backed; M14 changes nothing at runtime."
      case FutureRouteGateDesignRequirement =>
        "Design stance only: a future route gate is a design requirement on paper; no runtime gate, route, plugin, DI, or HTTP path is required or implemented."
      case FutureServingReadinessDecisionInputs =>
        "Design stance only: future serving-readiness decision inputs are enumerated as data-only planning inputs; none is evaluated, served, or executed at runtime."
      case FutureHttpFailureSemantics =>
        "Design stance only: future HTTP failure semantics (e.g. a 503-when-not-ready shape) are described on paper; no HTTP 503 behavior is implemented."
      case ProductionActivationNotApproved =>
        "Design stance only: Qdrant production activation is not approved; M14 is not a production route activation."
      case ExplicitOptInDisabledByDefault =>
        "Design stance only: the explicit Qdrant opt-in path stays disabled by default; M14 neither enables nor implicitly activates it."
    }
}

/** One stable M14 design-only serving-readiness input.
  *
  * Each readiness input is a data-only planning input that a *future* route gate would consult; it is
  * never evaluated, served, or executed at runtime by M14. The design contract enumerates the inputs
  * without implementing any decision logic.
  */
enum M14BeautyQSearchServingReadinessInput {
  case LifecycleReadiness
  case BackendAvailability
  case SeedResourceReadiness
  case ActivationApproval
  case RollbackAvailability
  case OperatorVisibility

  def render: String =
    this match {
      case M14BeautyQSearchServingReadinessInput.LifecycleReadiness    => "lifecycle_readiness"
      case M14BeautyQSearchServingReadinessInput.BackendAvailability   => "backend_availability"
      case M14BeautyQSearchServingReadinessInput.SeedResourceReadiness => "seed_resource_readiness"
      case M14BeautyQSearchServingReadinessInput.ActivationApproval    => "activation_approval"
      case M14BeautyQSearchServingReadinessInput.RollbackAvailability  => "rollback_availability"
      case M14BeautyQSearchServingReadinessInput.OperatorVisibility    => "operator_visibility"
    }

  /** Every readiness input is a design-only data-only planning input. */
  def designOnly: Boolean = true

  /** No readiness input is evaluated at runtime by M14. */
  def runtimeEvaluated: Boolean = false
}

object M14BeautyQSearchServingReadinessInput {

  /** Stable readiness-input order: lifecycle, backend availability, seed/resource, activation approval,
    * rollback availability, then operator visibility.
    */
  val stableOrder: List[M14BeautyQSearchServingReadinessInput] = List(
    LifecycleReadiness,
    BackendAvailability,
    SeedResourceReadiness,
    ActivationApproval,
    RollbackAvailability,
    OperatorVisibility,
  )

  /** Deterministic design rationale for a readiness input; data-only planning input, never a runtime check. */
  def rationale(input: M14BeautyQSearchServingReadinessInput): String =
    input match {
      case LifecycleReadiness =>
        "Design-only input: whether the backend lifecycle has reached a ready phase; enumerated as a data-only planning input, not evaluated at runtime."
      case BackendAvailability =>
        "Design-only input: whether the target backend is reachable/available; enumerated as a data-only planning input, not probed or executed at runtime."
      case SeedResourceReadiness =>
        "Design-only input: whether seed data and required resources are present; enumerated as a data-only planning input, not loaded or checked at runtime."
      case ActivationApproval =>
        "Design-only input: whether explicit activation approval has been granted; enumerated as a data-only planning input, not granted or asserted by M14."
      case RollbackAvailability =>
        "Design-only input: whether a rollback path is available; enumerated as a data-only planning input, not wired or executed at runtime."
      case OperatorVisibility =>
        "Design-only input: whether operator visibility (status/health surface) is in place; enumerated as a data-only planning input, not emitted at runtime."
    }
}

/** One stable M14 denied design-drift case.
  *
  * Each denied case names a drift the M14 design contract explicitly refuses: implementing runtime gate
  * / HTTP 503 behavior now, changing production behavior now, adding route/plugin/DI/HTTP or backend
  * execution, activating/switching routes, or claiming readiness/approval/quality. Every case is
  * `decision = denied`; the standing boundary confirms the corresponding posture remains off.
  */
enum M14BeautyQSearchRouteGateReadinessDeniedDriftCase {
  case ImplementHttp503Now
  case ChangeBeautySearchBehaviorNow
  case RoutePluginDiHttpChange
  case RealBackendExecution
  case QdrantProductionActivation
  case RouteActivation
  case DefaultRouteSwitch
  case ProductionReadinessClaim
  case ServingApprovalClaim
  case TreatPlanningArtifactsAsQualityEvidence

  def render: String =
    this match {
      case M14BeautyQSearchRouteGateReadinessDeniedDriftCase.ImplementHttp503Now                  => "implement_http_503_now"
      case M14BeautyQSearchRouteGateReadinessDeniedDriftCase.ChangeBeautySearchBehaviorNow        => "change_beauty_search_behavior_now"
      case M14BeautyQSearchRouteGateReadinessDeniedDriftCase.RoutePluginDiHttpChange              => "route_plugin_di_http_change"
      case M14BeautyQSearchRouteGateReadinessDeniedDriftCase.RealBackendExecution                 => "real_backend_execution"
      case M14BeautyQSearchRouteGateReadinessDeniedDriftCase.QdrantProductionActivation           => "qdrant_production_activation"
      case M14BeautyQSearchRouteGateReadinessDeniedDriftCase.RouteActivation                      => "route_activation"
      case M14BeautyQSearchRouteGateReadinessDeniedDriftCase.DefaultRouteSwitch                   => "default_route_switch"
      case M14BeautyQSearchRouteGateReadinessDeniedDriftCase.ProductionReadinessClaim             => "production_readiness_claim"
      case M14BeautyQSearchRouteGateReadinessDeniedDriftCase.ServingApprovalClaim                 => "serving_approval_claim"
      case M14BeautyQSearchRouteGateReadinessDeniedDriftCase.TreatPlanningArtifactsAsQualityEvidence => "treat_planning_artifacts_as_quality_evidence"
    }

  /** The deterministic decision for every drift case is `denied`. */
  def decision: String = "denied"

  /** Whether the consumed standing boundary confirms this drift is denied (its posture stays off). */
  def denialHolds(boundary: M10BeautyQSearchOfflineRoutingBoundary): Boolean =
    this match {
      case M14BeautyQSearchRouteGateReadinessDeniedDriftCase.ImplementHttp503Now =>
        !boundary.routePluginDiHttpInvolved && !boundary.productionRouteActivated && !boundary.productionBeautySearchCalled
      case M14BeautyQSearchRouteGateReadinessDeniedDriftCase.ChangeBeautySearchBehaviorNow =>
        !boundary.productionBeautySearchCalled && boundary.defaultBeautySearchEsBacked && !boundary.defaultRouteSwitched
      case M14BeautyQSearchRouteGateReadinessDeniedDriftCase.RoutePluginDiHttpChange =>
        !boundary.routePluginDiHttpInvolved
      case M14BeautyQSearchRouteGateReadinessDeniedDriftCase.RealBackendExecution =>
        !boundary.esExecuted && !boundary.qdrantExecuted &&
          !boundary.realBackendCallRequired && !boundary.realBackendCallImplemented &&
          !boundary.esClientCreated && !boundary.qdrantClientCreated &&
          !boundary.productionBeautySearchCalled
      case M14BeautyQSearchRouteGateReadinessDeniedDriftCase.QdrantProductionActivation =>
        !boundary.qdrantProductionActivationApproved && !boundary.qdrantExecuted && boundary.qdrantOptInDisabledByDefault
      case M14BeautyQSearchRouteGateReadinessDeniedDriftCase.RouteActivation =>
        !boundary.productionRouteActivated && !boundary.routeActivationClaimed
      case M14BeautyQSearchRouteGateReadinessDeniedDriftCase.DefaultRouteSwitch =>
        !boundary.defaultRouteSwitched && boundary.defaultBeautySearchEsBacked
      case M14BeautyQSearchRouteGateReadinessDeniedDriftCase.ProductionReadinessClaim =>
        !boundary.productionReadinessClaimed
      case M14BeautyQSearchRouteGateReadinessDeniedDriftCase.ServingApprovalClaim =>
        !boundary.servingApprovalClaimed
      case M14BeautyQSearchRouteGateReadinessDeniedDriftCase.TreatPlanningArtifactsAsQualityEvidence =>
        !boundary.qualityGreenClaimed && !boundary.productionReadinessClaimed
    }
}

object M14BeautyQSearchRouteGateReadinessDeniedDriftCase {

  /** Stable denied-case order: implement-now drifts, route/plugin/DI/HTTP and backend execution,
    * activation/switch, then the claim drifts and the quality-evidence misuse.
    */
  val stableOrder: List[M14BeautyQSearchRouteGateReadinessDeniedDriftCase] = List(
    ImplementHttp503Now,
    ChangeBeautySearchBehaviorNow,
    RoutePluginDiHttpChange,
    RealBackendExecution,
    QdrantProductionActivation,
    RouteActivation,
    DefaultRouteSwitch,
    ProductionReadinessClaim,
    ServingApprovalClaim,
    TreatPlanningArtifactsAsQualityEvidence,
  )

  /** Deterministic design rationale for a denied drift case. */
  def rationale(deniedCase: M14BeautyQSearchRouteGateReadinessDeniedDriftCase): String =
    deniedCase match {
      case ImplementHttp503Now =>
        "Denied: M14 does not implement HTTP 503 (or any runtime route-gate failure) behavior now; the failure semantics are design-only."
      case ChangeBeautySearchBehaviorNow =>
        "Denied: M14 does not change /beauty-search behavior now; production stays ES-backed and ungated."
      case RoutePluginDiHttpChange =>
        "Denied: M14 introduces no route, plugin, DI, or HTTP behavior change."
      case RealBackendExecution =>
        "Denied: M14 requires and implements no real ES/Qdrant/backend client execution."
      case QdrantProductionActivation =>
        "Denied: M14 does not enable Qdrant production activation; the opt-in stays disabled by default and unapproved."
      case RouteActivation =>
        "Denied: M14 enables no route activation; route activation is out of scope."
      case DefaultRouteSwitch =>
        "Denied: M14 enables no default route switch; the default route is unchanged."
      case ProductionReadinessClaim =>
        "Denied: M14 makes no production-readiness claim."
      case ServingApprovalClaim =>
        "Denied: M14 makes no serving-approval claim."
      case TreatPlanningArtifactsAsQualityEvidence =>
        "Denied: M14 treats M12/M13 planning artifacts as planning input only, never as quality evidence."
    }
}

final case class M14BeautyQSearchRouteGateReadinessDesignStateRow(
  state: M14BeautyQSearchRouteGateReadinessDesignState,
  rationale: String,
  boundaryHolds: Boolean,
) {
  def render: String = state.render
}

final case class M14BeautyQSearchServingReadinessInputRow(
  input: M14BeautyQSearchServingReadinessInput,
  rationale: String,
  designOnly: Boolean,
  runtimeEvaluated: Boolean,
) {
  def render: String = input.render
}

final case class M14BeautyQSearchRouteGateReadinessDeniedDriftRow(
  deniedCase: M14BeautyQSearchRouteGateReadinessDeniedDriftCase,
  decision: String,
  rationale: String,
  denialHolds: Boolean,
) {
  def render: String = deniedCase.render
}

final case class M14BeautyQSearchRouteGateReadinessDesignMetric(
  name: String,
  value: String,
)

final case class M14BeautyQSearchRouteGateReadinessDesignSummary(
  datasetId: String,
  artifactId: String,
  artifactVersion: String,
  consumedM13RoutePlanningVerdict: String,
  designStates: List[M14BeautyQSearchRouteGateReadinessDesignStateRow],
  designStateCount: Int,
  readinessInputs: List[M14BeautyQSearchServingReadinessInputRow],
  readinessInputCount: Int,
  deniedDriftCases: List[M14BeautyQSearchRouteGateReadinessDeniedDriftRow],
  deniedDriftCaseCount: Int,
  m14RouteGateReadinessDesignReady: Boolean,
  boundary: M10BeautyQSearchOfflineRoutingBoundary,
  verdict: String,
  metrics: List[M14BeautyQSearchRouteGateReadinessDesignMetric],
)

/** M14 BeautyQ route-gate / serving-readiness design contract: a pure offline design/contract artifact
  * that defines the stable route-gate / serving-readiness design states, the design-only serving
  * readiness inputs, and the denied design-drift cases for a *future* route gate.
  *
  * This is design/contract work only. It defines a design contract, readiness states, design-only
  * readiness inputs, guardrail decisions, and a documentation/reporting artifact; it is NOT a runtime
  * route gate, NOT an HTTP 503 implementation, NOT a production route activation, NOT a default route
  * switch, NOT a serving approval, NOT a production-readiness claim, and NOT a real backend execution.
  * It introduces no route, plugin, DI, or HTTP behavior change, no backend clients, and no real ES or
  * Qdrant execution. It never calls production `/beauty-search`, never creates an ES or Qdrant client,
  * and never runs Elasticsearch or Qdrant.
  *
  * It separates six concerns: the current no-gate ES-backed production behavior; the future route-gate
  * design requirement; the future serving-readiness decision inputs; the future HTTP failure semantics;
  * the non-approved production activation; and the disabled-by-default explicit opt-in state.
  *
  * It consumes the accepted M13A controlled opt-in route-planning closeout (offline planning/contract
  * only) as *planning input* via its accepted verdict; it never treats M12 or M13 planning artifacts as
  * quality evidence and fabricates no evidence from them. The readiness it reports is M14 *design-contract*
  * readiness only: it is not runtime gate readiness and not production readiness, and it claims no
  * quality-green, no retrieval-quality, no route-activation, and no serving-approval.
  */
object M14BeautyQSearchRouteGateReadinessDesign {

  val MarkdownFilename: String =
    "m14-beautyq-route-gate-readiness-design.md"

  val ArtifactId: String = "m14-beautyq-route-gate-readiness-design"

  val ArtifactVersion: String = "v1"

  /** Readiness verdict: M14 design-contract readiness only, design-only. Deliberately carries no runtime
    * gate readiness, production readiness, route-activation, or serving-approval claim.
    */
  val Verdict: String =
    "m14_route_gate_readiness_design_contract_ready_design_only"

  /** Accepted M13A controlled opt-in route-planning closeout verdict consumed as planning input only. */
  val ConsumedM13RoutePlanningVerdict: String =
    M13BeautyQSearchControlledOptInRoutePlanning.Verdict

  /** The standing offline boundary M14 design consumes; every claim-bearing field stays false. */
  val Boundary: M10BeautyQSearchOfflineRoutingBoundary =
    M10BeautyQSearchOfflineRoutingBoundary.Standing

  /** The stable design-state rows in stable order. */
  val DesignStates: List[M14BeautyQSearchRouteGateReadinessDesignStateRow] =
    M14BeautyQSearchRouteGateReadinessDesignState.stableOrder.map { state =>
      M14BeautyQSearchRouteGateReadinessDesignStateRow(
        state = state,
        rationale = M14BeautyQSearchRouteGateReadinessDesignState.rationale(state),
        boundaryHolds = state.holds(Boundary),
      )
    }

  /** The stable serving-readiness input rows in stable order. */
  val ReadinessInputs: List[M14BeautyQSearchServingReadinessInputRow] =
    M14BeautyQSearchServingReadinessInput.stableOrder.map { input =>
      M14BeautyQSearchServingReadinessInputRow(
        input = input,
        rationale = M14BeautyQSearchServingReadinessInput.rationale(input),
        designOnly = input.designOnly,
        runtimeEvaluated = input.runtimeEvaluated,
      )
    }

  /** The stable denied design-drift rows in stable order. */
  val DeniedDriftCases: List[M14BeautyQSearchRouteGateReadinessDeniedDriftRow] =
    M14BeautyQSearchRouteGateReadinessDeniedDriftCase.stableOrder.map { deniedCase =>
      M14BeautyQSearchRouteGateReadinessDeniedDriftRow(
        deniedCase = deniedCase,
        decision = deniedCase.decision,
        rationale = M14BeautyQSearchRouteGateReadinessDeniedDriftCase.rationale(deniedCase),
        denialHolds = deniedCase.denialHolds(Boundary),
      )
    }

  val DesignStateCount: Int = DesignStates.size
  val ReadinessInputCount: Int = ReadinessInputs.size
  val DeniedDriftCaseCount: Int = DeniedDriftCases.size

  val DefaultSummary: M14BeautyQSearchRouteGateReadinessDesignSummary =
    build()

  val MarkdownArtifact: M9OfflineEvalReportArtifact =
    M14BeautyQSearchRouteGateReadinessDesignRenderer.markdownArtifact(
      filename = MarkdownFilename,
      summary = DefaultSummary,
    )

  def stateRowFor(
    state: M14BeautyQSearchRouteGateReadinessDesignState
  ): Option[M14BeautyQSearchRouteGateReadinessDesignStateRow] =
    DesignStates.find(_.state == state)

  def readinessInputRowFor(
    input: M14BeautyQSearchServingReadinessInput
  ): Option[M14BeautyQSearchServingReadinessInputRow] =
    ReadinessInputs.find(_.input == input)

  def deniedDriftRowFor(
    deniedCase: M14BeautyQSearchRouteGateReadinessDeniedDriftCase
  ): Option[M14BeautyQSearchRouteGateReadinessDeniedDriftRow] =
    DeniedDriftCases.find(_.deniedCase == deniedCase)

  def build(): M14BeautyQSearchRouteGateReadinessDesignSummary = {
    val b = Boundary

    // The package is ready when it defines exactly the six stable design states, the six design-only
    // readiness inputs, and the ten denied design-drift cases, every state's boundary stance holds,
    // every readiness input is design-only and not runtime-evaluated, every denied case's denial holds,
    // all denied decisions are `denied`, and the standing boundary keeps the current ungated ES-backed
    // default while every gate/activation/serving/execution/claim posture stays off.
    val m14RouteGateReadinessDesignReady =
      DesignStates.map(_.render).distinct.size == DesignStateCount &&
        DesignStateCount == 6 &&
        ReadinessInputs.map(_.render).distinct.size == ReadinessInputCount &&
        ReadinessInputCount == 6 &&
        DeniedDriftCases.map(_.render).distinct.size == DeniedDriftCaseCount &&
        DeniedDriftCaseCount == 10 &&
        DesignStates.forall(_.boundaryHolds) &&
        ReadinessInputs.forall(row => row.designOnly && !row.runtimeEvaluated) &&
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

    val summaryWithoutMetrics = M14BeautyQSearchRouteGateReadinessDesignSummary(
      datasetId = M9BeautyQSearchEvalQueryDataset.Metadata.datasetId,
      artifactId = ArtifactId,
      artifactVersion = ArtifactVersion,
      consumedM13RoutePlanningVerdict = ConsumedM13RoutePlanningVerdict,
      designStates = DesignStates,
      designStateCount = DesignStateCount,
      readinessInputs = ReadinessInputs,
      readinessInputCount = ReadinessInputCount,
      deniedDriftCases = DeniedDriftCases,
      deniedDriftCaseCount = DeniedDriftCaseCount,
      m14RouteGateReadinessDesignReady = m14RouteGateReadinessDesignReady,
      boundary = b,
      verdict = Verdict,
      metrics = Nil,
    )

    summaryWithoutMetrics.copy(metrics = metrics(summaryWithoutMetrics))
  }

  private def metrics(
    summary: M14BeautyQSearchRouteGateReadinessDesignSummary
  ): List[M14BeautyQSearchRouteGateReadinessDesignMetric] = {
    val b = summary.boundary
    List(
      metric("artifact_id", summary.artifactId),
      metric("artifact_version", summary.artifactVersion),
      metric("consumed_m13_route_planning_verdict", summary.consumedM13RoutePlanningVerdict),
      metric("design_state_count", summary.designStateCount.toString),
      metric("design_states_unique", summary.designStates.map(_.render).distinct.size.toString),
      metric("design_states_boundary_holds", summary.designStates.count(_.boundaryHolds).toString),
      metric("readiness_input_count", summary.readinessInputCount.toString),
      metric("readiness_inputs_unique", summary.readinessInputs.map(_.render).distinct.size.toString),
      metric("readiness_inputs_design_only", summary.readinessInputs.count(_.designOnly).toString),
      metric("readiness_inputs_runtime_evaluated", summary.readinessInputs.count(_.runtimeEvaluated).toString),
      metric("denied_drift_case_count", summary.deniedDriftCaseCount.toString),
      metric("denied_drift_cases_unique", summary.deniedDriftCases.map(_.render).distinct.size.toString),
      metric("denied_drift_cases_denial_holds", summary.deniedDriftCases.count(_.denialHolds).toString),
      metric("denied_drift_cases_all_denied", summary.deniedDriftCases.forall(_.decision == "denied").toString),
      metric("m14_route_gate_readiness_design_ready", summary.m14RouteGateReadinessDesignReady.toString),
      metric("m14_is_design_contract_readiness_only", true.toString),
      metric("m14_is_not_runtime_gate_readiness", true.toString),
      metric("m14_is_not_production_readiness", true.toString),
      metric("m14_is_design_only_not_runtime_route_gate", true.toString),
      metric("m14_is_design_only_not_http_503_behavior", true.toString),
      metric("m14_does_not_change_beauty_search_behavior_now", true.toString),
      metric("m14_preserves_current_no_gate_production_behavior", true.toString),
      metric("m14_separates_current_no_gate_production_behavior", true.toString),
      metric("m14_separates_future_route_gate_design_requirement", true.toString),
      metric("m14_separates_future_serving_readiness_decision_inputs", true.toString),
      metric("m14_separates_future_http_failure_semantics", true.toString),
      metric("m14_separates_non_approved_production_activation", true.toString),
      metric("m14_separates_explicit_opt_in_disabled_by_default", true.toString),
      metric("m14_readiness_inputs_are_data_only_planning_inputs", true.toString),
      metric("m14_consumes_m13_closeout_as_planning_input_only", true.toString),
      metric("m14_does_not_treat_m12_m13_planning_artifacts_as_quality_evidence", true.toString),
      metric("m14_fabricates_no_evidence_from_planning_artifacts", true.toString),
      metric("m14_requires_no_route_plugin_di_http_path", true.toString),
      metric("m14_implements_no_route_plugin_di_http_path", true.toString),
      metric("m14_requires_no_real_es_qdrant_backend_client", true.toString),
      metric("m14_implements_no_real_es_qdrant_backend_client", true.toString),
      metric("m14_implements_no_runtime_route_gate", true.toString),
      metric("m14_implements_no_http_503_behavior", true.toString),
      metric("m14_enables_no_production_route_activation", true.toString),
      metric("m14_enables_no_default_route_switch", true.toString),
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
  ): M14BeautyQSearchRouteGateReadinessDesignMetric =
    M14BeautyQSearchRouteGateReadinessDesignMetric(name, value)
}

object M14BeautyQSearchRouteGateReadinessDesignRenderer {

  def renderMarkdown(summary: M14BeautyQSearchRouteGateReadinessDesignSummary): String = {
    val builder = new StringBuilder

    line(builder, "# M14 BeautyQ Route-Gate / Serving-Readiness Design Contract")
    line(builder, "")
    line(builder, "Offline design/contract artifact for a FUTURE route gate and serving-readiness decision. This is")
    line(builder, "design/contract work only: it is non-serving by default and does NOT alter existing production")
    line(builder, "/beauty-search. It defines stable route-gate / serving-readiness design states, design-only serving")
    line(builder, "readiness inputs, and denied design-drift cases. It is NOT a runtime route gate, NOT an HTTP 503")
    line(builder, "implementation, NOT a route activation, NOT a default route switch, and NOT a serving approval. It")
    line(builder, "is NOT a production-readiness claim and NOT a real backend execution: no ES or Qdrant client is")
    line(builder, "created and neither backend is run, and no route, plugin, DI, or HTTP behavior is required or")
    line(builder, "implemented.")
    line(builder, "")
    line(builder, "It separates the current no-gate ES-backed production behavior, the future route-gate design")
    line(builder, "requirement, the future serving-readiness decision inputs, the future HTTP failure semantics, the")
    line(builder, "non-approved production activation, and the disabled-by-default explicit opt-in state. The serving")
    line(builder, "readiness inputs are enumerated as data-only planning inputs; none is evaluated, served, or executed")
    line(builder, "at runtime. It consumes the accepted M13A controlled opt-in route-planning closeout (offline")
    line(builder, "planning/contract only) as planning input via its accepted verdict only; it never treats M12 or M13")
    line(builder, "planning artifacts as quality evidence and fabricates no evidence from them. This artifact reports")
    line(builder, "M14 design-contract readiness only (design-only): it is not runtime gate readiness and not production")
    line(builder, "readiness, and it is not route activation and not serving approval.")
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
    line(builder, s"- consumed_m13_route_planning_verdict: ${renderText(summary.consumedM13RoutePlanningVerdict)}")
    line(builder, s"- design_state_count: ${summary.designStateCount}")
    line(builder, s"- readiness_input_count: ${summary.readinessInputCount}")
    line(builder, s"- denied_drift_case_count: ${summary.deniedDriftCaseCount}")
    line(builder, s"- m14_route_gate_readiness_design_ready: ${summary.m14RouteGateReadinessDesignReady}")
    line(builder, "")
    line(builder, "## Route-gate design states")
    line(builder, "")
    line(builder, "Each state is a design stance only, never a runtime route gate, an HTTP 503 path, a serving approval,")
    line(builder, "or a real backend execution. The boundary_holds column reports that the standing offline boundary")
    line(builder, "confirms the stance.")
    line(builder, "")
    line(builder, "| design_state | boundary_holds | rationale |")
    line(builder, "|---|---|---|")
    summary.designStates.foreach { row =>
      line(builder, s"| ${renderText(row.render)} | ${row.boundaryHolds} | ${renderText(row.rationale)} |")
    }
    line(builder, "")
    line(builder, "## Serving-readiness design inputs")
    line(builder, "")
    line(builder, "Each readiness input is a data-only planning input a future route gate would consult. The design_only")
    line(builder, "column reports it is design-only and the runtime_evaluated column reports it is never evaluated at")
    line(builder, "runtime by M14.")
    line(builder, "")
    line(builder, "| readiness_input | design_only | runtime_evaluated | rationale |")
    line(builder, "|---|---|---|---|")
    summary.readinessInputs.foreach { row =>
      line(builder, s"| ${renderText(row.render)} | ${row.designOnly} | ${row.runtimeEvaluated} | ${renderText(row.rationale)} |")
    }
    line(builder, "")
    line(builder, "## Denied design-drift cases")
    line(builder, "")
    line(builder, "Each drift case is explicitly denied. The denial_holds column reports that the standing offline")
    line(builder, "boundary confirms the corresponding posture stays off. No denied case is ever permitted by M14.")
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
    line(builder, "This is a design/contract artifact, not a runtime route gate, not an HTTP 503 implementation, not a")
    line(builder, "route activation, not a default route switch, not a serving approval, not a production-readiness claim,")
    line(builder, "and not a real backend execution: no production route, runtime gate, HTTP 503 path, backend client, or")
    line(builder, "route/plugin/DI/HTTP change is implemented or claimed, and the M12/M13 planning artifacts are consumed")
    line(builder, "as planning input only, never as quality evidence.")

    builder.result()
  }

  def markdownArtifact(
    filename: String,
    summary: M14BeautyQSearchRouteGateReadinessDesignSummary,
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
