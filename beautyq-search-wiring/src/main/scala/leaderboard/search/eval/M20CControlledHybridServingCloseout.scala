package leaderboard.search.eval

import leaderboard.search.eval.M20BControlledHybridServingOperationalControl.{
  KillSwitchState,
  M20BOperationalControl,
  ModuleProofScope,
  ReadinessInputs,
}

/**
 * M20C: the closeout evidence that ties M20A and M20B together and proves the controlled boundary is
 * safe enough to close M20 as a hidden/disabled-by-default *controlled surface* — without activating
 * hybrid serving.
 *
 * M20A added [[M20ControlledHybridServingSkeleton.M20HybridServingControl]] as the smallest
 * disabled-by-default control surface (pure data, approves no serving, activates no Qdrant). M20B
 * added the operational safety layer around it: readiness as data that cannot by itself enable
 * serving, a kill switch, a source-confirmed rollback target, an operator-visible status surface, and
 * an honest in-memory-only module-proof scope.
 *
 * M20C adds no new behavior. It is a pure evidence model that *reads* the accepted M20A/M20B data and
 * records, as data, whether every safety invariant required to close M20 holds. The closeout may be
 * [[CloseoutStatus.ClosedAsDisabledControlledSurface]] only if all of those invariants hold and the
 * evidence does not claim any production activation; otherwise it is
 * [[CloseoutStatus.IncompleteNeedsMoreEvidence]].
 *
 * Boundaries (unchanged from M20A/M20B): pure data only; no effects, no backend calls, no HTTP, no
 * plugin contribution, no production `/beauty-search` route change, no fallback, no reranking, no
 * score fusion, no shadow/mirror traffic, no automatic Qdrant supplement, and no Qdrant production
 * activation. The wrapped M20B control (and through it the M20A skeleton and the M19I policy) is
 * consumed strictly as *evidence*; M20C never turns that evidence into a serving approval.
 *
 * Honesty: the only route proof source-confirmed by M20A/M20B is the in-memory route graph, so the
 * recorded module-proof scope stays [[ModuleProofScope.InMemoryOnly]]. M20C records it as
 * in-memory-only and refuses to close if the scope is overstated to a full production graph. M20C
 * does not add a production route switch to make the proof stronger.
 */
object M20CControlledHybridServingCloseout {

  /**
   * The closeout status ADT. M20 closes only as a disabled controlled surface; anything short of the
   * full safety evidence leaves the closeout [[IncompleteNeedsMoreEvidence]].
   *
   *   - [[ClosedAsDisabledControlledSurface]] : every M20A/M20B safety invariant holds and no
   *     production activation is claimed; M20 is closed as a hidden/disabled-by-default surface;
   *   - [[IncompleteNeedsMoreEvidence]]        : at least one safety invariant is missing, or the
   *     proof scope is overstated; M20 cannot be closed.
   */
  enum CloseoutStatus {
    case ClosedAsDisabledControlledSurface
    case IncompleteNeedsMoreEvidence

    def render: String = this match {
      case ClosedAsDisabledControlledSurface => "closed_as_disabled_controlled_surface"
      case IncompleteNeedsMoreEvidence       => "incomplete_needs_more_evidence"
    }

    def closed: Boolean = this == CloseoutStatus.ClosedAsDisabledControlledSurface
  }

  /**
   * The operator/reviewer-visible closeout report, recorded as data. Mirrors the M20B operator status
   * surface but at the closeout level: it states the closeout status, restates the safety flags, and
   * makes the two reviewer-facing claims explicit — M20 is *not* a production activation and the
   * default `/beauty-search` route remains unchanged. Pure data; building it performs no I/O.
   */
  final case class CloseoutReport(
    closeoutStatus: String,
    skeletonDisabledByDefault: Boolean,
    operationalControlNotServing: Boolean,
    readinessCannotEnableServing: Boolean,
    killSwitchDisablesServing: Boolean,
    rollbackTargetSourceConfirmed: Boolean,
    operatorStatusExposesBlockersAndSafetyFlags: Boolean,
    defaultBeautySearchRouteUnchanged: Boolean,
    servingApproved: Boolean,
    qdrantProductionActivationApproved: Boolean,
    fallbackEnabled: Boolean,
    scoreFusionEnabled: Boolean,
    rerankingEnabled: Boolean,
    automaticQdrantSupplementEnabled: Boolean,
    routeSwitchEnabled: Boolean,
    m19IPolicyConsumedAsEvidenceOnly: Boolean,
    moduleProofScope: String,
    moduleProofScopeOverstated: Boolean,
    notProductionActivation: Boolean,
    missingEvidence: List[String],
  )

  /**
   * The M20C closeout evidence, recorded as data. It reads the accepted M20B operational control
   * (which wraps the M20A skeleton and the M19I policy) and derives every closeout invariant from it.
   * Every dangerous flag is read from the wrapped control, which fixes them to the safe value; M20C
   * cannot flip any of them.
   */
  final case class CloseoutEvidence(operationalControl: M20BOperationalControl) {

    private def skeleton = operationalControl.servingControl

    /** M20A exists and is disabled by default: the wrapped skeleton is in the disabled state. */
    def skeletonDisabledByDefault: Boolean = skeleton.disabled

    /** M20B exists and is disabled / not serving. */
    def operationalControlNotServing: Boolean =
      operationalControl.effectiveServingDisabled && !operationalControl.servingApproved

    /**
     * Readiness cannot enable serving while approval is false: even with every readiness input
     * positive, serving stays disabled. Proven by reading the control with full readiness.
     */
    def readinessCannotEnableServing: Boolean =
      operationalControl.copy(readiness = ReadinessInputs.allPositive).readinessCannotEnableServing

    /**
     * The kill switch disables serving: engaging it (even with full readiness) forces effective
     * serving disabled and surfaces the explicit blocker. Proven by reading a kill-switched copy.
     */
    def killSwitchDisablesServing: Boolean = {
      val killed =
        operationalControl.copy(
          readiness = ReadinessInputs.allPositive,
          killSwitch = KillSwitchState.Engaged,
        )
      killed.effectiveServingDisabled &&
        killed.disabledByKillSwitchOrReadiness &&
        killed.blockers.contains("kill_switch_engaged")
    }

    /** The rollback target is source-confirmed in `BeautySearchRouteModules`. */
    def rollbackTargetSourceConfirmed: Boolean = operationalControl.rollbackTarget.sourceConfirmed

    /**
     * The operator status surface exposes the important blockers and safety flags: there is at least
     * one standing blocker and the dangerous approvals read false through the surface.
     */
    def operatorStatusExposesBlockersAndSafetyFlags: Boolean = {
      val status = operationalControl.operatorStatus
      status.blockers.nonEmpty &&
        !status.servingApproved &&
        !status.qdrantProductionActivationApproved &&
        status.defaultBeautySearchRouteUnchanged &&
        status.policyConsumedAsEvidenceOnly
    }

    /** The default `/beauty-search` route is unchanged. */
    def defaultBeautySearchRouteUnchanged: Boolean = skeleton.defaultBeautySearchRouteUnchanged

    /** Always false: read from the wrapped M20B control. */
    def servingApproved: Boolean = operationalControl.servingApproved

    /** Always false: read from the wrapped M20B control. */
    def qdrantProductionActivationApproved: Boolean = operationalControl.qdrantProductionActivationApproved

    /** Always false: read from the wrapped M20A skeleton. */
    def fallbackEnabled: Boolean                  = skeleton.fallbackEnabled
    def scoreFusionEnabled: Boolean               = skeleton.scoreFusionEnabled
    def rerankingEnabled: Boolean                 = skeleton.rerankingEnabled
    def automaticQdrantSupplementEnabled: Boolean = skeleton.automaticQdrantSupplementEnabled
    def routeSwitchEnabled: Boolean               = skeleton.routeSwitchEnabled

    /** The M19I policy is consumed as evidence only, never as a serving approval. */
    def m19IPolicyConsumedAsEvidenceOnly: Boolean = operationalControl.consumesPolicyAsEvidenceOnly

    /** The recorded module-proof scope, restated honestly from M20B. */
    def moduleProofScope: ModuleProofScope = operationalControl.moduleProofScope

    /**
     * The proof scope is overstated if it claims a full production graph. The only source-confirmed
     * proof is the in-memory route graph, so an honest closeout records [[ModuleProofScope.InMemoryOnly]].
     */
    def moduleProofScopeOverstated: Boolean =
      moduleProofScope == ModuleProofScope.FullProductionGraph

    /** Honest scope is recorded as in-memory-only — not renamed to full production proof. */
    def moduleProofScopeInMemoryOnly: Boolean =
      moduleProofScope == ModuleProofScope.InMemoryOnly

    /**
     * No production activation is claimed anywhere: serving unapproved, Qdrant production activation
     * unapproved, route switch off, and the wrapped control executes no hybrid serving.
     */
    def claimsNoProductionActivation: Boolean =
      !servingApproved &&
        !qdrantProductionActivationApproved &&
        !routeSwitchEnabled &&
        operationalControl.executesNoHybridServing

    /** Every closeout safety invariant required to close M20 as a disabled controlled surface. */
    def allSafetyEvidenceHolds: Boolean =
      skeletonDisabledByDefault &&
        operationalControlNotServing &&
        readinessCannotEnableServing &&
        killSwitchDisablesServing &&
        rollbackTargetSourceConfirmed &&
        operatorStatusExposesBlockersAndSafetyFlags &&
        defaultBeautySearchRouteUnchanged &&
        !servingApproved &&
        !qdrantProductionActivationApproved &&
        !fallbackEnabled &&
        !scoreFusionEnabled &&
        !rerankingEnabled &&
        !automaticQdrantSupplementEnabled &&
        !routeSwitchEnabled &&
        m19IPolicyConsumedAsEvidenceOnly &&
        moduleProofScopeInMemoryOnly &&
        !moduleProofScopeOverstated

    /** The ordered list of missing safety evidence keeping M20 from closing; empty when closed. */
    def missingEvidence: List[String] =
      List(
        Option.when(!skeletonDisabledByDefault)("skeleton_not_disabled_by_default"),
        Option.when(!operationalControlNotServing)("operational_control_serving"),
        Option.when(!readinessCannotEnableServing)("readiness_can_enable_serving"),
        Option.when(!killSwitchDisablesServing)("kill_switch_does_not_disable_serving"),
        Option.when(!rollbackTargetSourceConfirmed)("rollback_target_not_source_confirmed"),
        Option.when(!operatorStatusExposesBlockersAndSafetyFlags)("operator_status_incomplete"),
        Option.when(!defaultBeautySearchRouteUnchanged)("default_beauty_search_route_changed"),
        Option.when(servingApproved)("serving_approved"),
        Option.when(qdrantProductionActivationApproved)("qdrant_production_activation_approved"),
        Option.when(fallbackEnabled)("fallback_enabled"),
        Option.when(scoreFusionEnabled)("score_fusion_enabled"),
        Option.when(rerankingEnabled)("reranking_enabled"),
        Option.when(automaticQdrantSupplementEnabled)("automatic_qdrant_supplement_enabled"),
        Option.when(routeSwitchEnabled)("route_switch_enabled"),
        Option.when(!m19IPolicyConsumedAsEvidenceOnly)("m19i_policy_not_evidence_only"),
        Option.when(moduleProofScopeOverstated)("module_proof_scope_overstated"),
      ).flatten

    /**
     * M20 closes as a disabled controlled surface only if every safety invariant holds AND no
     * production activation is claimed; otherwise more evidence is needed.
     */
    def closeoutStatus: CloseoutStatus =
      if (allSafetyEvidenceHolds && claimsNoProductionActivation)
        CloseoutStatus.ClosedAsDisabledControlledSurface
      else
        CloseoutStatus.IncompleteNeedsMoreEvidence

    /** Project the full reviewer-visible closeout report. Pure; no I/O. */
    def report: CloseoutReport =
      CloseoutReport(
        closeoutStatus = closeoutStatus.render,
        skeletonDisabledByDefault = skeletonDisabledByDefault,
        operationalControlNotServing = operationalControlNotServing,
        readinessCannotEnableServing = readinessCannotEnableServing,
        killSwitchDisablesServing = killSwitchDisablesServing,
        rollbackTargetSourceConfirmed = rollbackTargetSourceConfirmed,
        operatorStatusExposesBlockersAndSafetyFlags = operatorStatusExposesBlockersAndSafetyFlags,
        defaultBeautySearchRouteUnchanged = defaultBeautySearchRouteUnchanged,
        servingApproved = servingApproved,
        qdrantProductionActivationApproved = qdrantProductionActivationApproved,
        fallbackEnabled = fallbackEnabled,
        scoreFusionEnabled = scoreFusionEnabled,
        rerankingEnabled = rerankingEnabled,
        automaticQdrantSupplementEnabled = automaticQdrantSupplementEnabled,
        routeSwitchEnabled = routeSwitchEnabled,
        m19IPolicyConsumedAsEvidenceOnly = m19IPolicyConsumedAsEvidenceOnly,
        moduleProofScope = moduleProofScope.render,
        moduleProofScopeOverstated = moduleProofScopeOverstated,
        notProductionActivation = claimsNoProductionActivation,
        missingEvidence = missingEvidence,
      )

    /**
     * A stable, human-readable closeout line. It states the closeout status and makes the two
     * reviewer-facing claims explicit: M20 is not a production activation and the default
     * `/beauty-search` route remains unchanged. Pure; no I/O.
     */
    def render: String =
      List(
        s"m20_closeout=${closeoutStatus.render}",
        "m20_is_production_activation=false",
        "default_beauty_search_route=unchanged",
        s"module_proof_scope=${moduleProofScope.render}",
        s"serving_approved=$servingApproved",
        s"${BeautyQSearchEvaluationMetricNames.SharedProductionPosture.QdrantProductionActivationApproved}=$qdrantProductionActivationApproved",
      ).mkString("; ")
  }

  object CloseoutEvidence {

    /** Build closeout evidence over the accepted M20B operational control. */
    def of(operationalControl: M20BOperationalControl): CloseoutEvidence =
      CloseoutEvidence(operationalControl)
  }
}
