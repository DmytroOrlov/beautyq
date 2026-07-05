package leaderboard.search.eval

import leaderboard.search.eval.M19IBeautyQComponentCombinationPolicyScaffold.ComponentCombinationPolicy

/**
 * M20A: hidden, disabled-by-default *controlled hybrid serving* skeleton — as data only.
 *
 * M19 closed as offline/eval evidence and policy-as-data, and M19I recorded the per-component
 * combination policy (the only Qdrant-positive decision being an *offline* variant-carousel
 * semantic supplement). No serving approval exists. The default production `/beauty-search` route
 * remains ES-backed.
 *
 * M20A adds the smallest explicit *control surface* for future hybrid serving work without doing any
 * of it. It is a pure model: it chooses no serving path, switches no route, runs no retrieval, fuses
 * no scores, reranks nothing, falls back to nothing, and approves neither hybrid serving nor Qdrant
 * production activation. It exists so a reviewer has one named, disabled-by-default handle to reason
 * about — and so the absence of serving approval is encoded as data rather than left implicit.
 *
 * The M19I [[ComponentCombinationPolicy]] is carried as [[M20HybridServingControl.policySource]]
 * strictly as *policy evidence*. M20A re-checks the M19I offline/eval honesty flags via
 * [[M20HybridServingControl.consumesPolicyAsEvidenceOnly]]; it never treats that policy as an
 * execution approval. [[M20HybridServingControl.servingApproved]] is always `false`.
 *
 * Boundaries: pure data only; no DI wiring by default, no effects, no backend calls, no HTTP, no
 * plugin contribution, no production `/beauty-search` route change, no fallback, no reranking, no
 * score fusion, no shadow/mirror traffic, no automatic Qdrant supplement, and no Qdrant production
 * activation. This skeleton is not contributed by any default module; it is absent from the default
 * graph unless a caller explicitly selects it.
 */
object M20ControlledHybridServingSkeleton {

  /**
   * Whether the skeleton has been wired into a graph at all. The default production state is
   * [[DisabledByDefault]]; the skeleton is only ever [[ExplicitlySelectedSkeletonOnly]] when a
   * caller opts in, and even then it remains a non-serving skeleton.
   */
  enum HybridServingControlState {
    case DisabledByDefault
    case ExplicitlySelectedSkeletonOnly

    def explicitlySelected: Boolean = this == HybridServingControlState.ExplicitlySelectedSkeletonOnly
  }

  /**
   * The M20A control surface, recorded as data. Every safety field is explicit so the disabled,
   * non-serving boundary can be reviewed without re-deriving anything. All dangerous flags are
   * fixed to the safe value by the smart constructors below; none of them is ever turned on here.
   *
   *   - [[state]]                              : disabled by default, or an explicitly selected
   *     skeleton-only handle; never an executing serving path;
   *   - [[policySource]]                       : the M19I component policy scaffold, carried as
   *     offline/eval *policy evidence* only;
   *   - [[servingApproved]]                    : always `false`; this skeleton approves no serving;
   *   - [[qdrantProductionActivationApproved]] : always `false`; no Qdrant production activation;
   *   - [[defaultBeautySearchRouteUnchanged]]  : always `true`; the default ES route is untouched;
   *   - [[fallbackEnabled]]                    : always `false`; no fallback;
   *   - [[scoreFusionEnabled]]                 : always `false`; no score fusion;
   *   - [[rerankingEnabled]]                   : always `false`; no reranking;
   *   - [[automaticQdrantSupplementEnabled]]   : always `false`; no automatic Qdrant supplement;
   *   - [[routeSwitchEnabled]]                 : always `false`; no route switch.
   */
  final case class M20HybridServingControl(
    state: HybridServingControlState,
    policySource: ComponentCombinationPolicy,
    servingApproved: Boolean,
    qdrantProductionActivationApproved: Boolean,
    defaultBeautySearchRouteUnchanged: Boolean,
    fallbackEnabled: Boolean,
    scoreFusionEnabled: Boolean,
    rerankingEnabled: Boolean,
    automaticQdrantSupplementEnabled: Boolean,
    routeSwitchEnabled: Boolean,
  ) {

    /** Disabled unless a caller has explicitly selected the skeleton. */
    def disabled: Boolean = state == HybridServingControlState.DisabledByDefault

    /**
     * The M19I policy is consumed as offline/eval evidence only — never as an execution approval.
     * Requires the M19I honesty flags to hold and serving to remain unapproved.
     */
    def consumesPolicyAsEvidenceOnly: Boolean =
      policySource.offlineEvalOnly &&
        policySource.notServingPolicy &&
        policySource.doesNotApproveHybrid &&
        !servingApproved

    /**
     * No serving behavior is enabled anywhere by this skeleton: no serving approval, no Qdrant
     * production activation, no fallback/fusion/reranking, no automatic Qdrant supplement, no route
     * switch, and the default ES route stays unchanged.
     */
    def executesNoHybridServing: Boolean =
      !servingApproved &&
        !qdrantProductionActivationApproved &&
        !fallbackEnabled &&
        !scoreFusionEnabled &&
        !rerankingEnabled &&
        !automaticQdrantSupplementEnabled &&
        !routeSwitchEnabled &&
        defaultBeautySearchRouteUnchanged
  }

  object M20HybridServingControl {

    /** Default production handle: disabled, non-serving, every dangerous flag off. */
    def disabledByDefault(policySource: ComponentCombinationPolicy): M20HybridServingControl =
      safe(HybridServingControlState.DisabledByDefault, policySource)

    /**
     * The explicit opt-in handle. Still a non-serving skeleton: it merely records that a caller has
     * selected the skeleton into a graph. Every dangerous flag remains off.
     */
    def explicitlySelectedSkeleton(policySource: ComponentCombinationPolicy): M20HybridServingControl =
      safe(HybridServingControlState.ExplicitlySelectedSkeletonOnly, policySource)

    private def safe(
      state: HybridServingControlState,
      policySource: ComponentCombinationPolicy,
    ): M20HybridServingControl =
      M20HybridServingControl(
        state = state,
        policySource = policySource,
        servingApproved = false,
        qdrantProductionActivationApproved = false,
        defaultBeautySearchRouteUnchanged = true,
        fallbackEnabled = false,
        scoreFusionEnabled = false,
        rerankingEnabled = false,
        automaticQdrantSupplementEnabled = false,
        routeSwitchEnabled = false,
      )
  }
}
