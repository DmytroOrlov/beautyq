package leaderboard.search.eval

import leaderboard.search.eval.M20BControlledHybridServingOperationalControl.ModuleProofScope
import leaderboard.search.eval.M20CControlledHybridServingCloseout.CloseoutEvidence

/**
 * M21: the explicit *production activation decision package* — pure data only.
 *
 * M18 produced real ES and real Qdrant candidate evidence. M19 closed as offline/eval component-level
 * policy-as-data (the only Qdrant-positive decision being an offline variant-carousel semantic
 * supplement; provider/service remain evidence-limited; facets/inferred filters stay current-owner /
 * parser / ES-only). M20 closed only as a hidden, disabled-by-default *controlled surface*
 * ([[M20CControlledHybridServingCloseout.CloseoutStatus.ClosedAsDisabledControlledSurface]]) — not a
 * production activation. Serving approval is false. Qdrant production activation is false. The default
 * `/beauty-search` route is unchanged. The only source-confirmed route proof is the in-memory route
 * graph, so the closeout's route-proof scope stays [[ModuleProofScope.InMemoryOnly]].
 *
 * M21 adds no new behavior. It is a pure decision model that *reads* the accepted M20C closeout
 * evidence (and through it M20B/M20A and the M19I policy) and records, as data, the current
 * production-activation verdict. The current evidence is sufficient to close M20 as a disabled
 * controlled surface, but NOT sufficient to approve production activation, so the verdict is
 * [[ActivationDecision.DeferProductionActivation]]. The package records why activation is deferred and
 * the explicit blockers that would have to be cleared before approval can be reconsidered.
 *
 * Boundaries (unchanged from M18/M19/M20): pure data only; no effects, no backend calls, no HTTP, no
 * plugin contribution, no `LeaderboardPlugin` change, no production route-module change, no
 * `/beauty-search` call, no Qdrant call, no fallback, no score fusion, no reranking, no automatic
 * Qdrant supplement, no shadow traffic, no mirroring, no route switch, and no production hybrid route
 * activation. The wrapped closeout is consumed strictly as *evidence*; M21 never turns it into an
 * activation approval.
 *
 * This is a *defer* decision, not a permanent rejection: a forward path exists, gated on the explicit
 * blockers recorded below.
 */
object M21ProductionActivationDecisionPackage {

  /**
   * The production-activation decision ADT.
   *
   *   - [[ApproveProductionActivation]] : every activation gate is satisfied (including a real serving
   *     approval and a real Qdrant production activation approval) and production hybrid serving may be
   *     turned on. Unreachable from the current M20 closeout evidence, which fixes both approvals to
   *     `false`.
   *   - [[RejectProductionActivation]]  : activation is permanently rejected. Not the current decision.
   *   - [[DeferProductionActivation]]   : the evidence is sufficient to close M20 as a disabled
   *     controlled surface but not to approve production activation; activation is deferred pending the
   *     recorded blockers. This is the current verdict.
   */
  enum ActivationDecision {
    case ApproveProductionActivation
    case RejectProductionActivation
    case DeferProductionActivation

    def render: String = this match {
      case ApproveProductionActivation => "approve_production_activation"
      case RejectProductionActivation  => "reject_production_activation"
      case DeferProductionActivation   => "defer_production_activation"
    }

    def approved: Boolean = this == ActivationDecision.ApproveProductionActivation
    def rejected: Boolean = this == ActivationDecision.RejectProductionActivation
    def deferred: Boolean = this == ActivationDecision.DeferProductionActivation
  }

  /**
   * The activation gate: the evidence that would have to be present *before* production activation can
   * be approved. Every field is the affirmative form of a blocker; production activation can only be
   * approved when every gate is satisfied. The current package leaves them all `false`, because none
   * of this evidence exists yet.
   *
   *   - [[fullProductionRouteOrAcceptedReplacementProof]] : a full production route/module proof, or an
   *     explicitly accepted replacement evidence, exists (the current route proof is in-memory-only);
   *   - [[runtimeHybridExecutionProof]]                   : runtime hybrid execution has been proven
   *     (no runtime hybrid execution exists; M18/M19/M20 are offline/eval and pure-data only);
   *   - [[operatorRolloutRollbackProofSufficientForActivation]] : operator rollout/rollback proof is
   *     sufficient for activation (M20B records only an in-memory rollback target, not an
   *     activation-grade rollout/rollback proof);
   *   - [[componentPolicyGapsResolved]]                   : the component-level policy gaps for
   *     provider/service/facets/inferred filters are resolved (they remain evidence-limited /
   *     current-owner-only);
   *   - [[productionLatencyFailureModeEvidence]]          : production latency and failure-mode
   *     evidence for hybrid serving exists (none does).
   */
  final case class ActivationGate(
    fullProductionRouteOrAcceptedReplacementProof: Boolean,
    runtimeHybridExecutionProof: Boolean,
    operatorRolloutRollbackProofSufficientForActivation: Boolean,
    componentPolicyGapsResolved: Boolean,
    productionLatencyFailureModeEvidence: Boolean,
  ) {
    def allSatisfied: Boolean =
      fullProductionRouteOrAcceptedReplacementProof &&
        runtimeHybridExecutionProof &&
        operatorRolloutRollbackProofSufficientForActivation &&
        componentPolicyGapsResolved &&
        productionLatencyFailureModeEvidence
  }

  object ActivationGate {

    /**
     * The current activation gate: nothing required for production activation is present yet. Every
     * gate is `false`, which is why activation is deferred.
     */
    val currentlyNoneSatisfied: ActivationGate =
      ActivationGate(
        fullProductionRouteOrAcceptedReplacementProof = false,
        runtimeHybridExecutionProof = false,
        operatorRolloutRollbackProofSufficientForActivation = false,
        componentPolicyGapsResolved = false,
        productionLatencyFailureModeEvidence = false,
      )
  }

  /**
   * The reviewer-visible activation decision report, recorded as data. It states the verdict, restates
   * the accepted-state evidence facts, restates the safety flags, and makes the two reviewer-facing
   * claims explicit — production activation is *deferred* (not approved), and the default
   * `/beauty-search` route remains unchanged. Pure data; building it performs no I/O.
   */
  final case class ActivationDecisionReport(
    decision: String,
    productionActivationApproved: Boolean,
    productionActivationDeferred: Boolean,
    productionActivationRejected: Boolean,
    // accepted-state evidence facts
    m18RealEsAndQdrantEvidenceExists: Boolean,
    m19PolicyRemainsOfflineEvalOnly: Boolean,
    onlyVariantCarouselQdrantPositiveOfflineSupplement: Boolean,
    providerServiceRemainEvidenceLimited: Boolean,
    facetsInferredFiltersCurrentOwnerOnly: Boolean,
    m20ClosedAsDisabledControlledSurface: Boolean,
    // safety flags (read from the wrapped M20C closeout)
    servingApproved: Boolean,
    qdrantProductionActivationApproved: Boolean,
    defaultBeautySearchRouteUnchanged: Boolean,
    routeProofScopeInMemoryOnly: Boolean,
    routeProofScope: String,
    fallbackEnabled: Boolean,
    scoreFusionEnabled: Boolean,
    rerankingEnabled: Boolean,
    automaticQdrantSupplementEnabled: Boolean,
    shadowTrafficEnabled: Boolean,
    mirroringEnabled: Boolean,
    routeSwitchEnabled: Boolean,
    // the gate + blockers
    activationGateAllSatisfied: Boolean,
    blockers: List[String],
  )

  /**
   * The M21 production-activation decision evidence, recorded as data. It reads the accepted M20C
   * closeout (which wraps M20B/M20A and the M19I policy) for every safety fact, and records the
   * accepted-state evidence facts from the prior milestones. The verdict is derived: production
   * activation can only be approved when the closeout is closed as a disabled controlled surface, the
   * activation gate is fully satisfied, AND a real serving approval and a real Qdrant production
   * activation approval exist — none of which is true from the current M20 closeout, so the verdict is
   * [[ActivationDecision.DeferProductionActivation]].
   *
   * @param closeout                  the accepted M20C closeout evidence, consumed strictly as evidence;
   * @param gate                      the activation gate; currently all-unsatisfied;
   * @param m18RealEsAndQdrantEvidenceExists       recorded accepted-state fact: M18 produced real ES
   *                                  and real Qdrant candidate evidence;
   * @param onlyVariantCarouselQdrantPositiveOfflineSupplement recorded accepted-state fact from M19I:
   *                                  the variant carousel is the only Qdrant-positive offline supplement;
   * @param providerServiceRemainEvidenceLimited   recorded accepted-state fact: provider/service remain
   *                                  evidence-limited;
   * @param facetsInferredFiltersCurrentOwnerOnly  recorded accepted-state fact: facets/inferred filters
   *                                  remain current-owner / parser / ES-only, not approved for Qdrant;
   * @param permanentRejection        always `false`: this package never rejects activation forever.
   */
  final case class ActivationDecisionEvidence(
    closeout: CloseoutEvidence,
    gate: ActivationGate = ActivationGate.currentlyNoneSatisfied,
    m18RealEsAndQdrantEvidenceExists: Boolean = true,
    onlyVariantCarouselQdrantPositiveOfflineSupplement: Boolean = true,
    providerServiceRemainEvidenceLimited: Boolean = true,
    facetsInferredFiltersCurrentOwnerOnly: Boolean = true,
    permanentRejection: Boolean = false,
  ) {

    // --- Safety facts read from the wrapped M20C closeout (cannot be flipped here) -----------------

    /** M20 is closed only as a disabled controlled surface. */
    def m20ClosedAsDisabledControlledSurface: Boolean = closeout.closeoutStatus.closed

    /** Always false: read from the wrapped closeout. */
    def servingApproved: Boolean = closeout.servingApproved

    /** Always false: read from the wrapped closeout. */
    def qdrantProductionActivationApproved: Boolean = closeout.qdrantProductionActivationApproved

    /** Always true: read from the wrapped closeout. */
    def defaultBeautySearchRouteUnchanged: Boolean = closeout.defaultBeautySearchRouteUnchanged

    /** The route proof scope, restated honestly from the closeout. */
    def routeProofScope: ModuleProofScope = closeout.moduleProofScope

    /**
     * The route proof scope is in-memory-only (and not overstated to a full production graph). This is
     * the honest scope and on its own blocks activation: production activation needs a full production
     * route/module proof or an explicitly accepted replacement.
     */
    def routeProofScopeInMemoryOnly: Boolean =
      closeout.moduleProofScopeInMemoryOnly && !closeout.moduleProofScopeOverstated

    /** The M19I policy remains offline/eval-only — read from the closeout's evidence-only invariant. */
    def m19PolicyRemainsOfflineEvalOnly: Boolean = closeout.m19IPolicyConsumedAsEvidenceOnly

    /** Always false: read from the wrapped closeout. */
    def fallbackEnabled: Boolean                  = closeout.fallbackEnabled
    def scoreFusionEnabled: Boolean               = closeout.scoreFusionEnabled
    def rerankingEnabled: Boolean                 = closeout.rerankingEnabled
    def automaticQdrantSupplementEnabled: Boolean = closeout.automaticQdrantSupplementEnabled
    def routeSwitchEnabled: Boolean               = closeout.routeSwitchEnabled

    /**
     * Shadow traffic and mirroring are not part of any wrapped surface and are never approved by this
     * package; recorded as constant `false` so a reviewer can see they are explicitly off.
     */
    def shadowTrafficEnabled: Boolean = false
    def mirroringEnabled: Boolean     = false

    /** Every dangerous runtime flag is off. */
    def allDangerousRuntimeFlagsOff: Boolean =
      !fallbackEnabled &&
        !scoreFusionEnabled &&
        !rerankingEnabled &&
        !automaticQdrantSupplementEnabled &&
        !shadowTrafficEnabled &&
        !mirroringEnabled &&
        !routeSwitchEnabled

    // --- Decision ---------------------------------------------------------------------------------

    /**
     * Production activation may be approved only when the closeout is closed as a disabled controlled
     * surface, the activation gate is fully satisfied, AND a real serving approval and a real Qdrant
     * production activation approval exist. The wrapped closeout fixes both approvals to `false`, so
     * this is unreachable from the current M20 closeout evidence: approval is impossible here.
     */
    def approvalAllowed: Boolean =
      m20ClosedAsDisabledControlledSurface &&
        gate.allSatisfied &&
        servingApproved &&
        qdrantProductionActivationApproved

    /** The current production-activation verdict. This package never rejects forever, so it defers. */
    def decision: ActivationDecision =
      if (approvalAllowed) ActivationDecision.ApproveProductionActivation
      else if (permanentRejection) ActivationDecision.RejectProductionActivation
      else ActivationDecision.DeferProductionActivation

    /**
     * The ordered list of explicit blockers that must be cleared before production activation can be
     * reconsidered. Empty only if activation were approvable, which it is not here.
     */
    def blockers: List[String] =
      List(
        Option.when(!gate.fullProductionRouteOrAcceptedReplacementProof || routeProofScopeInMemoryOnly)(
          "full_production_route_module_proof_or_accepted_replacement_absent"
        ),
        Option.when(!servingApproved)("serving_approval_still_absent"),
        Option.when(!qdrantProductionActivationApproved)("qdrant_production_activation_approval_still_absent"),
        Option.when(!gate.runtimeHybridExecutionProof)("runtime_hybrid_execution_proof_absent"),
        Option.when(!gate.operatorRolloutRollbackProofSufficientForActivation)(
          "operator_rollout_rollback_proof_insufficient_for_activation"
        ),
        Option.when(!gate.componentPolicyGapsResolved)(
          "component_policy_gaps_remain_provider_service_facets_inferred_filters"
        ),
        Option.when(!gate.productionLatencyFailureModeEvidence)(
          "production_latency_failure_mode_evidence_absent"
        ),
      ).flatten

    /** Project the full reviewer-visible decision report. Pure; no I/O. */
    def report: ActivationDecisionReport =
      ActivationDecisionReport(
        decision = decision.render,
        productionActivationApproved = decision.approved,
        productionActivationDeferred = decision.deferred,
        productionActivationRejected = decision.rejected,
        m18RealEsAndQdrantEvidenceExists = m18RealEsAndQdrantEvidenceExists,
        m19PolicyRemainsOfflineEvalOnly = m19PolicyRemainsOfflineEvalOnly,
        onlyVariantCarouselQdrantPositiveOfflineSupplement = onlyVariantCarouselQdrantPositiveOfflineSupplement,
        providerServiceRemainEvidenceLimited = providerServiceRemainEvidenceLimited,
        facetsInferredFiltersCurrentOwnerOnly = facetsInferredFiltersCurrentOwnerOnly,
        m20ClosedAsDisabledControlledSurface = m20ClosedAsDisabledControlledSurface,
        servingApproved = servingApproved,
        qdrantProductionActivationApproved = qdrantProductionActivationApproved,
        defaultBeautySearchRouteUnchanged = defaultBeautySearchRouteUnchanged,
        routeProofScopeInMemoryOnly = routeProofScopeInMemoryOnly,
        routeProofScope = routeProofScope.render,
        fallbackEnabled = fallbackEnabled,
        scoreFusionEnabled = scoreFusionEnabled,
        rerankingEnabled = rerankingEnabled,
        automaticQdrantSupplementEnabled = automaticQdrantSupplementEnabled,
        shadowTrafficEnabled = shadowTrafficEnabled,
        mirroringEnabled = mirroringEnabled,
        routeSwitchEnabled = routeSwitchEnabled,
        activationGateAllSatisfied = gate.allSatisfied,
        blockers = blockers,
      )

    /**
     * A stable, human-readable decision line. It states the verdict and makes the two reviewer-facing
     * claims explicit: production activation is deferred (not approved) and the default `/beauty-search`
     * route remains unchanged. Pure; no I/O.
     */
    def render: String =
      List(
        s"m21_production_activation=${decision.render}",
        "production_activation_approved=false",
        "default_beauty_search_route=unchanged",
        s"m20_closeout=${closeout.closeoutStatus.render}",
        s"serving_approved=$servingApproved",
        s"qdrant_production_activation_approved=$qdrantProductionActivationApproved",
        s"route_proof_scope=${routeProofScope.render}",
      ).mkString("; ")
  }

  object ActivationDecisionEvidence {

    /**
     * Build the current M21 deferral decision over the accepted M20C closeout evidence: the activation
     * gate is all-unsatisfied, the accepted-state evidence facts are recorded, and the package never
     * rejects forever — so the verdict is [[ActivationDecision.DeferProductionActivation]].
     */
    def currentDeferral(closeout: CloseoutEvidence): ActivationDecisionEvidence =
      ActivationDecisionEvidence(closeout = closeout)
  }
}
