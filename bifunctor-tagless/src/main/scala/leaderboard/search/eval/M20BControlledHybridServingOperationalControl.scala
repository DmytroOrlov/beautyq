package leaderboard.search.eval

import leaderboard.search.eval.M20ControlledHybridServingSkeleton.M20HybridServingControl

/**
 * M20B: a pure, offline/data-only *operational safety layer* around the M20A controlled hybrid
 * serving skeleton.
 *
 * M20A added [[M20HybridServingControl]] as the smallest disabled-by-default *control surface* for
 * future hybrid serving work — pure data that approves no serving and activates no Qdrant. M20B does
 * not change that boundary. It adds the operational handles a reviewer/operator would expect around
 * a disabled skeleton, still strictly as policy/control data:
 *
 *   - readiness state, as data inputs that can never by themselves enable serving;
 *   - a kill switch that forces effective serving disabled regardless of readiness;
 *   - a source-confirmed rollback target;
 *   - an operator-visible status surface that lists every important blocker and safety flag;
 *   - an honest module-proof scope, so no full production-graph coverage is overclaimed.
 *
 * Boundaries (unchanged from M20A): pure data only; no effects, no backend calls, no HTTP, no plugin
 * contribution, no production `/beauty-search` route change, no fallback, no reranking, no score
 * fusion, no shadow/mirror traffic, no automatic Qdrant supplement, and no Qdrant production
 * activation. The wrapped [[M20HybridServingControl]] (and through it the M19I policy) is consumed as
 * *evidence only*; M20B never turns that evidence into a serving approval.
 *
 * Crucially, [[M20BOperationalControl.servingApproved]] and
 * [[M20BOperationalControl.qdrantProductionActivationApproved]] are taken from the wrapped M20A
 * skeleton, which fixes both to `false`. This task cannot flip either.
 */
object M20BControlledHybridServingOperationalControl {

  /**
   * The kill switch. When [[Engaged]] it forces effective serving disabled, independent of any
   * readiness input. The default operational state is [[NotEngaged]] — serving is already disabled
   * because no serving is approved; the kill switch is the explicit additional forcing control.
   */
  enum KillSwitchState {
    case NotEngaged
    case Engaged

    def engaged: Boolean = this == KillSwitchState.Engaged
  }

  /**
   * The rollback target the operator would return to. Both targets are source-confirmed in
   * `BeautySearchRouteModules`:
   *
   *   - [[EsBackedDefaultBeautySearchRoute]]   : the current default ES-backed `/beauty-search`
   *     route (`seedCatalogElasticsearch`);
   *   - [[InMemoryNonDefaultSeedRoute]]        : the existing non-default in-memory seed-catalog
   *     route (`seedCatalogInMemory`).
   *
   * No new route is introduced; rollback simply names an already-existing, source-confirmed module.
   */
  enum RollbackTarget {
    case EsBackedDefaultBeautySearchRoute
    case InMemoryNonDefaultSeedRoute

    def render: String = this match {
      case EsBackedDefaultBeautySearchRoute => "es_backed_default_beauty_search_route"
      case InMemoryNonDefaultSeedRoute      => "in_memory_non_default_seed_route"
    }

    /** Both targets correspond to a source-confirmed module in `BeautySearchRouteModules`. */
    def sourceConfirmed: Boolean = true
  }

  /**
   * How far the *route* safety of this layer has actually been proven by the listed source/spec
   * truth. M20A proved module-safety against the in-memory route graph only, so the only honest
   * default is [[InMemoryOnly]]. [[FullProductionGraph]] must not be claimed unless a full
   * production ES graph proof is source-confirmed.
   */
  enum ModuleProofScope {
    case InMemoryOnly
    case FullProductionGraph

    def render: String = this match {
      case InMemoryOnly       => "in_memory_only"
      case FullProductionGraph => "full_production_graph"
    }
  }

  /**
   * Readiness recorded purely as data inputs. None of these can enable serving on its own: serving
   * stays disabled while serving is unapproved and/or the kill switch is engaged.
   *
   *   - [[skeletonSelected]]             : the M20A skeleton has been explicitly selected;
   *   - [[policyEvidenceConsumed]]       : the M19I/M20A policy passed the evidence-only gate;
   *   - [[operatorVisibilityPrepared]]   : operator-visible status is prepared.
   */
  final case class ReadinessInputs(
    skeletonSelected: Boolean,
    policyEvidenceConsumed: Boolean,
    operatorVisibilityPrepared: Boolean,
  ) {
    def anyPositive: Boolean = skeletonSelected || policyEvidenceConsumed || operatorVisibilityPrepared
    def allPositive: Boolean = skeletonSelected && policyEvidenceConsumed && operatorVisibilityPrepared
  }

  object ReadinessInputs {
    /** The default: no readiness input is positive. */
    val none: ReadinessInputs =
      ReadinessInputs(skeletonSelected = false, policyEvidenceConsumed = false, operatorVisibilityPrepared = false)

    /** Every readiness input positive — used to prove it still cannot enable serving. */
    val allPositive: ReadinessInputs =
      ReadinessInputs(skeletonSelected = true, policyEvidenceConsumed = true, operatorVisibilityPrepared = true)
  }

  /**
   * The operator-visible status surface, recorded as data. It lists every important blocker and
   * safety flag so the disabled, non-serving boundary can be reviewed without re-deriving anything.
   * Pure data — building it performs no I/O and calls nothing.
   */
  final case class OperatorStatus(
    effectiveServing: String,
    servingApproved: Boolean,
    qdrantProductionActivationApproved: Boolean,
    killSwitchEngaged: Boolean,
    readinessAllPositive: Boolean,
    defaultBeautySearchRouteUnchanged: Boolean,
    fallbackEnabled: Boolean,
    scoreFusionEnabled: Boolean,
    rerankingEnabled: Boolean,
    automaticQdrantSupplementEnabled: Boolean,
    routeSwitchEnabled: Boolean,
    policyConsumedAsEvidenceOnly: Boolean,
    rollbackTarget: String,
    rollbackTargetSourceConfirmed: Boolean,
    moduleProofScope: String,
    blockers: List[String],
  )

  /**
   * The M20B operational control, recorded as data. Every safety field is either delegated to the
   * wrapped M20A skeleton (which fixes the dangerous flags to the safe value) or constrained by the
   * smart constructors below. Nothing here can turn serving on.
   *
   *   - [[servingControl]]    : the wrapped M20A skeleton, consumed as evidence only;
   *   - [[readiness]]         : readiness inputs as data; cannot enable serving;
   *   - [[killSwitch]]        : forces effective serving disabled when engaged;
   *   - [[rollbackTarget]]    : a source-confirmed rollback target;
   *   - [[moduleProofScope]]  : the honest route-proof scope (in-memory-only by default).
   */
  final case class M20BOperationalControl(
    servingControl: M20HybridServingControl,
    readiness: ReadinessInputs,
    killSwitch: KillSwitchState,
    rollbackTarget: RollbackTarget,
    moduleProofScope: ModuleProofScope,
  ) {

    /** Taken from the wrapped M20A skeleton: always `false`. M20B cannot approve serving. */
    def servingApproved: Boolean = servingControl.servingApproved

    /** Taken from the wrapped M20A skeleton: always `false`. M20B cannot activate Qdrant. */
    def qdrantProductionActivationApproved: Boolean = servingControl.qdrantProductionActivationApproved

    /**
     * Effective serving is disabled whenever serving is unapproved OR the kill switch is engaged.
     * Since [[servingApproved]] is always `false` here, this is always `true`; the kill switch is an
     * explicit additional forcing input that does not depend on serving approval.
     */
    def effectiveServingDisabled: Boolean =
      !servingApproved || killSwitch.engaged

    /** The default human-facing effective state string. */
    def effectiveServingLabel: String = "disabled_not_serving"

    /**
     * A serving-approval-independent disable signal: the kill switch, or incomplete readiness,
     * forces serving disabled. With all readiness inputs positive this reduces to exactly the kill
     * switch, which is how M20B proves the kill switch overrides positive readiness inputs.
     */
    def disabledByKillSwitchOrReadiness: Boolean =
      killSwitch.engaged || !readiness.allPositive

    /**
     * Readiness, even when fully positive, cannot enable serving while serving is unapproved.
     * Encoded as data so the invariant is reviewable, not implicit.
     */
    def readinessCannotEnableServing: Boolean =
      !servingApproved && effectiveServingDisabled

    /** The M19I/M20A policy is consumed as evidence only — delegated to the M20A skeleton. */
    def consumesPolicyAsEvidenceOnly: Boolean =
      servingControl.consumesPolicyAsEvidenceOnly

    /**
     * No serving behavior is enabled anywhere: delegates to the M20A skeleton's invariant and
     * additionally requires that policy evidence has not been turned into a serving approval.
     */
    def executesNoHybridServing: Boolean =
      servingControl.executesNoHybridServing && !servingApproved

    /** The important blockers keeping serving disabled, in a stable order. */
    def blockers: List[String] = {
      val base = List(
        Option.when(!servingApproved)("serving_not_approved"),
        Option.when(!qdrantProductionActivationApproved)("qdrant_production_activation_not_approved"),
        Option.when(killSwitch.engaged)("kill_switch_engaged"),
        Option.when(!readiness.allPositive)("readiness_incomplete"),
      )
      base.flatten
    }

    /** Project the full operator-visible status surface. Pure; no I/O. */
    def operatorStatus: OperatorStatus =
      OperatorStatus(
        effectiveServing = effectiveServingLabel,
        servingApproved = servingApproved,
        qdrantProductionActivationApproved = qdrantProductionActivationApproved,
        killSwitchEngaged = killSwitch.engaged,
        readinessAllPositive = readiness.allPositive,
        defaultBeautySearchRouteUnchanged = servingControl.defaultBeautySearchRouteUnchanged,
        fallbackEnabled = servingControl.fallbackEnabled,
        scoreFusionEnabled = servingControl.scoreFusionEnabled,
        rerankingEnabled = servingControl.rerankingEnabled,
        automaticQdrantSupplementEnabled = servingControl.automaticQdrantSupplementEnabled,
        routeSwitchEnabled = servingControl.routeSwitchEnabled,
        policyConsumedAsEvidenceOnly = consumesPolicyAsEvidenceOnly,
        rollbackTarget = rollbackTarget.render,
        rollbackTargetSourceConfirmed = rollbackTarget.sourceConfirmed,
        moduleProofScope = moduleProofScope.render,
        blockers = blockers,
      )
  }

  object M20BOperationalControl {

    /**
     * The default operational handle: disabled/not serving, kill switch not engaged, no readiness
     * input positive, rollback to the current ES-backed default route, and an honest in-memory-only
     * module-proof scope. Every dangerous flag stays off via the wrapped M20A skeleton.
     */
    def disabledByDefault(servingControl: M20HybridServingControl): M20BOperationalControl =
      M20BOperationalControl(
        servingControl = servingControl,
        readiness = ReadinessInputs.none,
        killSwitch = KillSwitchState.NotEngaged,
        rollbackTarget = RollbackTarget.EsBackedDefaultBeautySearchRoute,
        moduleProofScope = ModuleProofScope.InMemoryOnly,
      )
  }
}
