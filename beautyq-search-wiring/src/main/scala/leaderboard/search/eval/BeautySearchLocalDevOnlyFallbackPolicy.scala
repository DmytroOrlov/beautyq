package leaderboard.search.eval

/** One stable clause of the BeautyQ local/dev-only fallback policy.
  *
  * This is a pure data-only policy/contract, never a runtime fallback path. Exactly one clause permits a
  * local/dev-only fallback, and only as an explicit FUTURE local/dev choice; every other clause denies a
  * production or default-runtime fallback. No clause gates, serves, falls back, fuses, reranks, or
  * activates anything at runtime, and none introduces a backend client or changes `/beauty-search`.
  */
enum BeautySearchLocalDevOnlyFallbackPolicyClause {
  case AllowLocalDevOnlyFallbackAsExplicitFutureChoice
  case ProductionFallbackNotEnabled
  case RuntimeServingFallbackNotEnabledByDefault
  case DefaultBeautySearchStaysEsBacked
  case QdrantDisabledByDefaultNotAFallbackTarget
  case M14RouteGateSeparateFromFallback
  case NoHybridFusionRerankingTelemetryProductionBehavior

  def render: String =
    this match {
      case BeautySearchLocalDevOnlyFallbackPolicyClause.AllowLocalDevOnlyFallbackAsExplicitFutureChoice =>
        "allow_local_dev_only_fallback_as_explicit_future_choice"
      case BeautySearchLocalDevOnlyFallbackPolicyClause.ProductionFallbackNotEnabled =>
        "production_fallback_not_enabled"
      case BeautySearchLocalDevOnlyFallbackPolicyClause.RuntimeServingFallbackNotEnabledByDefault =>
        "runtime_serving_fallback_not_enabled_by_default"
      case BeautySearchLocalDevOnlyFallbackPolicyClause.DefaultBeautySearchStaysEsBacked =>
        "default_beauty_search_stays_es_backed"
      case BeautySearchLocalDevOnlyFallbackPolicyClause.QdrantDisabledByDefaultNotAFallbackTarget =>
        "qdrant_disabled_by_default_not_a_fallback_target"
      case BeautySearchLocalDevOnlyFallbackPolicyClause.M14RouteGateSeparateFromFallback =>
        "m14_route_gate_separate_from_fallback"
      case BeautySearchLocalDevOnlyFallbackPolicyClause.NoHybridFusionRerankingTelemetryProductionBehavior =>
        "no_hybrid_fusion_reranking_telemetry_production_behavior"
    }

  /** The only allowed clause is the explicit-future-local/dev-choice allowance; every other clause is denied. */
  def decision: String =
    this match {
      case BeautySearchLocalDevOnlyFallbackPolicyClause.AllowLocalDevOnlyFallbackAsExplicitFutureChoice =>
        "allowed_local_dev_only_explicit_future_choice"
      case _ =>
        "denied"
    }

  /** Whether the consumed standing boundary confirms this clause holds. The single allowed clause holds as
    * a design-only allowance backed by the boundary keeping all serving/fallback postures off; every denied
    * clause holds when the corresponding boundary posture stays off.
    */
  def holds(boundary: M10BeautyQSearchOfflineRoutingBoundary): Boolean =
    this match {
      case BeautySearchLocalDevOnlyFallbackPolicyClause.AllowLocalDevOnlyFallbackAsExplicitFutureChoice =>
        // Allowed only as a future local/dev choice: it stays off today, so no fallback/serving posture is on.
        !boundary.fallbackImplied && !boundary.productionRouteActivated && !boundary.defaultRouteSwitched &&
          boundary.defaultBeautySearchEsBacked
      case BeautySearchLocalDevOnlyFallbackPolicyClause.ProductionFallbackNotEnabled =>
        !boundary.fallbackImplied && !boundary.productionRouteActivated && !boundary.productionBeautySearchCalled
      case BeautySearchLocalDevOnlyFallbackPolicyClause.RuntimeServingFallbackNotEnabledByDefault =>
        !boundary.fallbackImplied && !boundary.routePluginDiHttpInvolved && !boundary.realBackendCallImplemented
      case BeautySearchLocalDevOnlyFallbackPolicyClause.DefaultBeautySearchStaysEsBacked =>
        boundary.defaultBeautySearchEsBacked && !boundary.defaultRouteSwitched
      case BeautySearchLocalDevOnlyFallbackPolicyClause.QdrantDisabledByDefaultNotAFallbackTarget =>
        boundary.qdrantOptInDisabledByDefault && !boundary.qdrantExecuted && !boundary.qdrantClientCreated &&
          !boundary.fallbackImplied
      case BeautySearchLocalDevOnlyFallbackPolicyClause.M14RouteGateSeparateFromFallback =>
        // The route gate is a separate runtime concern; the fallback policy adds no gate behavior.
        !boundary.fallbackImplied && !boundary.routeActivationClaimed && !boundary.servingApprovalClaimed
      case BeautySearchLocalDevOnlyFallbackPolicyClause.NoHybridFusionRerankingTelemetryProductionBehavior =>
        !boundary.hybridServingImplied && !boundary.fallbackImplied && !boundary.scoreFusionImplied &&
          !boundary.rerankingImplied && !boundary.productionTelemetryImplied
    }
}

object BeautySearchLocalDevOnlyFallbackPolicyClause {

  /** Stable clause order: the single local/dev-only allowance first, then the production-fallback and
    * runtime-serving-fallback denials, the ES-backed default, the Qdrant non-fallback stance, the route-gate
    * separation, and the no-hybrid/fusion/reranking/telemetry denial.
    */
  val stableOrder: List[BeautySearchLocalDevOnlyFallbackPolicyClause] = List(
    AllowLocalDevOnlyFallbackAsExplicitFutureChoice,
    ProductionFallbackNotEnabled,
    RuntimeServingFallbackNotEnabledByDefault,
    DefaultBeautySearchStaysEsBacked,
    QdrantDisabledByDefaultNotAFallbackTarget,
    M14RouteGateSeparateFromFallback,
    NoHybridFusionRerankingTelemetryProductionBehavior,
  )

  /** Deterministic rationale for a clause; data-only policy stance, never a runtime fallback. */
  def rationale(clause: BeautySearchLocalDevOnlyFallbackPolicyClause): String =
    clause match {
      case AllowLocalDevOnlyFallbackAsExplicitFutureChoice =>
        "Allowed only as an explicit future local/dev choice: a local/dev-only fallback MAY be chosen later for local/dev, never enabled today and never in production."
      case ProductionFallbackNotEnabled =>
        "Denied: production fallback is not enabled; this policy adds no production fallback binding, route, or behavior."
      case RuntimeServingFallbackNotEnabledByDefault =>
        "Denied: runtime serving fallback is not enabled by default; no default DI wiring, route, or backend client serves a fallback."
      case DefaultBeautySearchStaysEsBacked =>
        "Default /beauty-search stays ES-backed and unchanged; this policy alters no production route behavior."
      case QdrantDisabledByDefaultNotAFallbackTarget =>
        "Qdrant stays disabled by default and is not a fallback target; the explicit Qdrant opt-in is unchanged and unactivated."
      case M14RouteGateSeparateFromFallback =>
        "The M14B runtime route gate is a separate runtime concern; this fallback policy neither uses nor extends the gate."
      case NoHybridFusionRerankingTelemetryProductionBehavior =>
        "Denied: no hybrid/fallback/fusion/reranking/production-telemetry behavior is introduced; this is a data-only policy stance."
    }
}

final case class BeautySearchLocalDevOnlyFallbackPolicyClauseRow(
  clause: BeautySearchLocalDevOnlyFallbackPolicyClause,
  decision: String,
  rationale: String,
  boundaryHolds: Boolean,
) {
  def render: String = clause.render
}

/** Pure local/dev-only fallback policy/contract for BeautyQ search.
  *
  * This closes the previously-pending "allow local / dev-only fallback if chosen later" expectation with a
  * concrete contract. It is a pure data-only policy stance: it implements NO fallback serving, adds NO
  * fallback DI wiring to production modules, adds NO backend clients, changes `/beauty-search` not at all,
  * and changes no Qdrant modules. It records that:
  *
  *   - a local/dev-only fallback is allowed only as an explicit FUTURE local/dev choice;
  *   - production fallback is not enabled;
  *   - runtime serving fallback is not enabled by default;
  *   - the default `/beauty-search` remains ES-backed;
  *   - Qdrant remains disabled by default and is not a fallback target;
  *   - the M14B runtime route gate remains a separate concern from fallback;
  *   - no hybrid/fallback/fusion/reranking/production-telemetry behavior is introduced.
  *
  * It consumes the standing offline routing boundary, whose serving/fallback/claim postures all stay off, to
  * confirm each clause; it makes no production-readiness, route-activation, serving-approval, quality-green,
  * or retrieval-quality claim.
  */
object BeautySearchLocalDevOnlyFallbackPolicy {

  val PolicyId: String = "beautyq-local-dev-only-fallback-policy"

  val PolicyVersion: String = "v1"

  /** Policy verdict: a data-only local/dev-only fallback contract; deliberately carries no production
    * fallback, runtime serving fallback, route-activation, or serving-approval claim.
    */
  val Verdict: String =
    "beautyq_local_dev_only_fallback_policy_contract_data_only"

  /** The standing offline boundary this policy consumes; every serving/fallback/claim posture stays off. */
  val Boundary: M10BeautyQSearchOfflineRoutingBoundary =
    M10BeautyQSearchOfflineRoutingBoundary.Standing

  /** A local/dev-only fallback is allowed ONLY as an explicit future local/dev choice — never today, never
    * in production, never by default.
    */
  val LocalDevOnlyFallbackAllowedAsExplicitFutureChoice: Boolean = true

  /** Production fallback is not enabled by this policy. */
  val ProductionFallbackEnabled: Boolean = false

  /** Runtime serving fallback is not enabled by default by this policy. */
  val RuntimeServingFallbackEnabledByDefault: Boolean = false

  /** The default `/beauty-search` remains ES-backed. */
  val DefaultBeautySearchEsBacked: Boolean = true

  /** Qdrant remains disabled by default. */
  val QdrantDisabledByDefault: Boolean = true

  /** Qdrant is not a fallback target. */
  val QdrantIsFallbackTarget: Boolean = false

  /** The M14B runtime route gate remains separate from this fallback policy. */
  val M14RouteGateSeparateFromFallback: Boolean = true

  /** No hybrid/fallback/fusion/reranking/production-telemetry production behavior is introduced. */
  val HybridFusionRerankingTelemetryProductionBehaviorIntroduced: Boolean = false

  /** The stable policy-clause rows in stable order, each confirmed against the standing boundary. */
  val Clauses: List[BeautySearchLocalDevOnlyFallbackPolicyClauseRow] =
    BeautySearchLocalDevOnlyFallbackPolicyClause.stableOrder.map { clause =>
      BeautySearchLocalDevOnlyFallbackPolicyClauseRow(
        clause = clause,
        decision = clause.decision,
        rationale = BeautySearchLocalDevOnlyFallbackPolicyClause.rationale(clause),
        boundaryHolds = clause.holds(Boundary),
      )
    }

  val ClauseCount: Int = Clauses.size

  def clauseRowFor(
    clause: BeautySearchLocalDevOnlyFallbackPolicyClause
  ): Option[BeautySearchLocalDevOnlyFallbackPolicyClauseRow] =
    Clauses.find(_.clause == clause)

  /** Exactly one clause is the local/dev-only allowance; every other clause is denied. */
  val AllowedClauseCount: Int =
    Clauses.count(_.clause == BeautySearchLocalDevOnlyFallbackPolicyClause.AllowLocalDevOnlyFallbackAsExplicitFutureChoice)

  val DeniedClauseCount: Int =
    Clauses.count(_.decision == "denied")

  /** The policy is well-formed when it defines exactly the seven stable clauses (one explicit local/dev-only
    * allowance, six denials), every clause's boundary stance holds, and the standing boundary keeps the
    * ES-backed default while every fallback/serving/activation/claim posture stays off.
    */
  val PolicyClosed: Boolean = {
    val b = Boundary
    Clauses.map(_.render).distinct.size == ClauseCount &&
    ClauseCount == 7 &&
    AllowedClauseCount == 1 &&
    DeniedClauseCount == 6 &&
    Clauses.forall(_.boundaryHolds) &&
    LocalDevOnlyFallbackAllowedAsExplicitFutureChoice &&
    !ProductionFallbackEnabled &&
    !RuntimeServingFallbackEnabledByDefault &&
    DefaultBeautySearchEsBacked &&
    QdrantDisabledByDefault &&
    !QdrantIsFallbackTarget &&
    M14RouteGateSeparateFromFallback &&
    !HybridFusionRerankingTelemetryProductionBehaviorIntroduced &&
    b.defaultBeautySearchEsBacked &&
    b.qdrantOptInDisabledByDefault &&
    !b.defaultRouteSwitched &&
    !b.productionRouteActivated &&
    !b.productionBeautySearchCalled &&
    !b.routePluginDiHttpInvolved &&
    !b.realBackendCallImplemented &&
    !b.realBackendCallRequired &&
    !b.esClientCreated &&
    !b.qdrantClientCreated &&
    !b.esExecuted &&
    !b.qdrantExecuted &&
    !b.hybridServingImplied &&
    !b.fallbackImplied &&
    !b.scoreFusionImplied &&
    !b.rerankingImplied &&
    !b.productionTelemetryImplied &&
    !b.qualityGreenClaimed &&
    !b.productionReadinessClaimed &&
    !b.routeActivationClaimed &&
    !b.servingApprovalClaimed
  }
}
