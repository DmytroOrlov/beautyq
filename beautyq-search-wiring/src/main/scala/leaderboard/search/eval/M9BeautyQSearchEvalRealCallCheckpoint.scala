package leaderboard.search.eval

/** Resource-gated real-call checkpoint contract.
  *
  * This is a pure planning/contract gate. It decides whether the current eval state is eligible to
  * *attempt* real ES/Qdrant offline calls in some future explicit resource-smoke task. It never
  * performs a real backend call, never touches a production route/plugin/DI/HTTP source, and never
  * claims production activation, Qdrant activation, hybrid serving, fallback, fusion, reranking, or
  * production telemetry readiness.
  */
final case class M9BeautyQSearchEvalRealCallCheckpointInput(
  scorecard: M9BeautyQSearchEvalStaticScorecardSummary,
  esResourceConfigPresent: Boolean,
  qdrantResourceConfigPresent: Boolean,
  operatorApprovalGranted: Boolean,
  productionActivationApproved: Boolean,
)

object M9BeautyQSearchEvalRealCallCheckpointInput {

  /** Default checkpoint input: the static scorecard is ready, but no explicit resource config and no
    * operator approval is present, and production activation approval defaults to false.
    */
  val Default: M9BeautyQSearchEvalRealCallCheckpointInput =
    M9BeautyQSearchEvalRealCallCheckpointInput(
      scorecard = M9BeautyQSearchEvalStaticScorecard.DefaultSummary,
      esResourceConfigPresent = false,
      qdrantResourceConfigPresent = false,
      operatorApprovalGranted = false,
      productionActivationApproved = false,
    )
}

enum M9BeautyQSearchEvalRealCallCheckpointReason {
  case StaticScorecardNotReady
  case NoExplicitResourceConfig
  case EsResourceConfigPresent
  case QdrantResourceConfigPresent
  case BothResourceConfigsPresent
  case OperatorApprovalRequiredForComparison
  case OperatorApprovalGranted
  case ProductionActivationNotApproved
  case OfflineResourceGatedSmokeOnly
  case NoProductionRouteChange
  case RealBackendCallNotImplemented

  def render: String =
    this match {
      case StaticScorecardNotReady                => "static_scorecard_not_ready"
      case NoExplicitResourceConfig               => "no_explicit_resource_config"
      case EsResourceConfigPresent                => "es_resource_config_present"
      case QdrantResourceConfigPresent            => "qdrant_resource_config_present"
      case BothResourceConfigsPresent             => "both_resource_configs_present"
      case OperatorApprovalRequiredForComparison  => "operator_approval_required_for_comparison"
      case OperatorApprovalGranted                => "operator_approval_granted"
      case ProductionActivationNotApproved        => "production_activation_not_approved"
      case OfflineResourceGatedSmokeOnly          => "offline_resource_gated_smoke_only"
      case NoProductionRouteChange                => "no_production_route_change"
      case RealBackendCallNotImplemented          => "real_backend_call_not_implemented"
    }
}

enum M9BeautyQSearchEvalRealCallCheckpointDecision {
  case BlockedStaticScorecardNotReady
  case NotEligibleNoExplicitResourceConfig
  case NotEligibleOperatorApprovalRequired
  case EligibleForEsOnlyResourceGatedSmoke
  case EligibleForQdrantOnlyResourceGatedSmoke
  case EligibleForEsQdrantResourceGatedComparison

  def render: String =
    this match {
      case BlockedStaticScorecardNotReady            => "blocked_static_scorecard_not_ready"
      case NotEligibleNoExplicitResourceConfig       => "not_eligible_no_explicit_resource_config"
      case NotEligibleOperatorApprovalRequired       => "not_eligible_operator_approval_required"
      case EligibleForEsOnlyResourceGatedSmoke       => "eligible_for_es_only_resource_gated_smoke"
      case EligibleForQdrantOnlyResourceGatedSmoke   => "eligible_for_qdrant_only_resource_gated_smoke"
      case EligibleForEsQdrantResourceGatedComparison => "eligible_for_es_qdrant_resource_gated_comparison"
    }

  /** Whether this decision marks the state as eligible to attempt a future offline, resource-gated
    * real call. This is never production activation and never implies a real call happens now.
    */
  def eligibleForResourceGatedSmoke: Boolean =
    this match {
      case EligibleForEsOnlyResourceGatedSmoke |
          EligibleForQdrantOnlyResourceGatedSmoke |
          EligibleForEsQdrantResourceGatedComparison =>
        true
      case BlockedStaticScorecardNotReady |
          NotEligibleNoExplicitResourceConfig |
          NotEligibleOperatorApprovalRequired =>
        false
    }
}

final case class M9BeautyQSearchEvalRealCallCheckpointResult(
  decision: M9BeautyQSearchEvalRealCallCheckpointDecision,
  reasons: List[M9BeautyQSearchEvalRealCallCheckpointReason],
  staticScorecardReady: Boolean,
  esResourceConfigPresent: Boolean,
  qdrantResourceConfigPresent: Boolean,
  operatorApprovalGranted: Boolean,
  productionActivationApproved: Boolean,
) {

  /** This checkpoint is offline/resource-gated only. It never permits or represents a real backend
    * call, production activation, or any production route change.
    */
  def realBackendCallImplemented: Boolean = false

  def productionActivationReady: Boolean = false

  def eligibleForResourceGatedSmoke: Boolean = decision.eligibleForResourceGatedSmoke
}

object M9BeautyQSearchEvalRealCallCheckpoint {
  import M9BeautyQSearchEvalRealCallCheckpointDecision.*
  import M9BeautyQSearchEvalRealCallCheckpointReason.*

  val MarkdownFilename: String =
    "m9-beautyq-real-call-checkpoint.md"

  def decide(input: M9BeautyQSearchEvalRealCallCheckpointInput): M9BeautyQSearchEvalRealCallCheckpointResult = {
    val staticReady =
      input.scorecard.verdict == M9BeautyQSearchEvalStaticScorecard.VerdictDatasetStaticRowsReady &&
        !input.scorecard.realBackendCallRequired &&
        !input.scorecard.routePluginDiHttpInvolved

    // Production activation approval is never honored by this contract: it must always record that
    // production activation is not approved and must never output production activation ready.
    val productionActivationApproved = false

    val (decision, baseReasons): (M9BeautyQSearchEvalRealCallCheckpointDecision, List[M9BeautyQSearchEvalRealCallCheckpointReason]) =
      if (!staticReady) {
        (BlockedStaticScorecardNotReady, List(StaticScorecardNotReady))
      } else (input.esResourceConfigPresent, input.qdrantResourceConfigPresent) match {
        case (false, false) =>
          (NotEligibleNoExplicitResourceConfig, List(NoExplicitResourceConfig))
        case (true, false) =>
          (EligibleForEsOnlyResourceGatedSmoke, List(EsResourceConfigPresent, OfflineResourceGatedSmokeOnly))
        case (false, true) =>
          (EligibleForQdrantOnlyResourceGatedSmoke, List(QdrantResourceConfigPresent, OfflineResourceGatedSmokeOnly))
        case (true, true) =>
          if (input.operatorApprovalGranted)
            (
              EligibleForEsQdrantResourceGatedComparison,
              List(BothResourceConfigsPresent, OperatorApprovalGranted, OfflineResourceGatedSmokeOnly),
            )
          else
            (
              NotEligibleOperatorApprovalRequired,
              List(BothResourceConfigsPresent, OperatorApprovalRequiredForComparison),
            )
      }

    val reasons =
      baseReasons ++ List(
        ProductionActivationNotApproved,
        NoProductionRouteChange,
        RealBackendCallNotImplemented,
      )

    M9BeautyQSearchEvalRealCallCheckpointResult(
      decision = decision,
      reasons = reasons,
      staticScorecardReady = staticReady,
      esResourceConfigPresent = input.esResourceConfigPresent,
      qdrantResourceConfigPresent = input.qdrantResourceConfigPresent,
      operatorApprovalGranted = input.operatorApprovalGranted,
      productionActivationApproved = productionActivationApproved,
    )
  }

  val DefaultResult: M9BeautyQSearchEvalRealCallCheckpointResult =
    decide(M9BeautyQSearchEvalRealCallCheckpointInput.Default)

  val MarkdownArtifact: M9OfflineEvalReportArtifact =
    M9BeautyQSearchEvalRealCallCheckpointRenderer.markdownArtifact(MarkdownFilename, DefaultResult)
}

object M9BeautyQSearchEvalRealCallCheckpointRenderer {

  def renderMarkdown(result: M9BeautyQSearchEvalRealCallCheckpointResult): String = {
    val builder = new StringBuilder

    line(builder, "# M9 BeautyQ Real-Call Checkpoint")
    line(builder, "")
    line(builder, "This checkpoint is a resource-gated planning contract. It decides whether the current eval state is eligible to attempt a future offline, resource-gated real ES/Qdrant call. It is not a real backend call, not production activation, and not a route change.")
    line(builder, "")
    line(builder, "## Decision")
    line(builder, "")
    line(builder, s"- decision: ${result.decision.render}")
    line(builder, s"- eligible_for_resource_gated_smoke: ${result.eligibleForResourceGatedSmoke}")
    line(builder, s"- static_scorecard_ready: ${result.staticScorecardReady}")
    line(builder, "")
    line(builder, "## Inputs")
    line(builder, "")
    line(builder, s"- es_resource_config_present: ${result.esResourceConfigPresent}")
    line(builder, s"- qdrant_resource_config_present: ${result.qdrantResourceConfigPresent}")
    line(builder, s"- operator_approval_granted: ${result.operatorApprovalGranted}")
    line(builder, s"- production_activation_approved: ${result.productionActivationApproved}")
    line(builder, "")
    line(builder, "## Reasons")
    line(builder, "")
    result.reasons.foreach(reason => line(builder, s"- ${reason.render}"))
    line(builder, "")
    line(builder, "## Boundary")
    line(builder, "")
    line(builder, "- Eligible decisions are offline/resource-gated only; they are not production activation.")
    line(builder, "- Production activation remains not approved and is never output as ready.")
    line(builder, "- No real ES or Qdrant backend call is implemented or required by this checkpoint.")
    line(builder, "- No route, plugin, DI, or HTTP source is involved.")
    line(builder, "- Default /beauty-search remains ES-backed; the Qdrant opt-in route stays disabled by default.")

    builder.result()
  }

  def markdownArtifact(
    filename: String,
    result: M9BeautyQSearchEvalRealCallCheckpointResult,
  ): M9OfflineEvalReportArtifact =
    M9OfflineEvalReportArtifact(
      filename = filename,
      contentType = "text/markdown; charset=utf-8",
      contents = renderMarkdown(result),
    )

  private def line(builder: StringBuilder, value: String): Unit = {
    builder.append(value)
    builder.append('\n')
  }
}
