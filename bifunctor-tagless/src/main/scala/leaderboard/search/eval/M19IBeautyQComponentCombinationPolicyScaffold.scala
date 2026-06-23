package leaderboard.search.eval

import leaderboard.search.eval.M19CBeautyQResponseComponentTaxonomy.{
  BeautyResponseComponentId,
  ComponentEvidence,
  ResponseComponentFact,
}
import leaderboard.search.eval.M19HBeautyQComponentCoverageEvidenceReport.ComponentCoverageEvidence

/**
 * M19I: pure offline/eval-only component-level combination policy scaffold for BeautyQ response
 * parts.
 *
 * The M19C taxonomy enumerated the five response components, M19D rolled up the coverage gaps,
 * M19F made the eval dataset capable of carrying component expectations, M19G populated the
 * grouping fields, and M19H rendered the source-confirmed coverage evidence. M19I is the first
 * surface that records, *as data*, a per-component combination policy decision: for each response
 * component, what is allowed, blocked, or still evidence-limited, and whether Qdrant may contribute.
 *
 * This is policy-as-data, not behavior. It chooses no serving path, fuses no scores, runs no
 * retrieval, and approves no production activation. The whole point of the
 * [[ComponentCombinationPolicy.servingApproved]] flag (always `false`) and the per-row
 * [[PolicyState]] ADT is to make the offline/eval-only boundary unmissable:
 *
 *   - `variantCarousel`        : ES-primary with an *offline* Qdrant semantic candidate supplement.
 *                                This is the ONLY Qdrant-positive component decision, and it rests
 *                                on the real ES/Qdrant candidate evidence (M18) and M19 metrics. It
 *                                is still offline/eval-only and NOT serving approval.
 *   - `providerCarousel`       : `NeedsMoreEvidence`. Source-confirmed grouping metadata
 *                                (masterLocationId) is metadata, NOT a component-level hybrid policy
 *                                approval. Qdrant contribution is not allowed.
 *   - `serviceIntentCarousel`  : `NeedsMoreEvidence`. Source-confirmed grouping metadata (serviceId)
 *                                is metadata, NOT a component-level hybrid policy approval. Qdrant
 *                                contribution is not allowed.
 *   - `facets`                 : current lexical/ES/parser-owned only (`EsOrCurrentOwnerOnly`).
 *                                Qdrant contribution is not approved; Qdrant does not own facets.
 *   - `inferredFilters`        : current parser/DSL-owned only (`EsOrCurrentOwnerOnly`). Qdrant
 *                                contribution is not approved; Qdrant does not own inferred filters.
 *
 * Boundaries: pure data + rendering only; no DI, no effects, no backend calls, no HTTP, no plugin,
 * no production `/beauty-search` route, no fallback, no reranking, no score fusion, and no Qdrant
 * production activation. Components are never collapsed into one global hybrid on/off flag.
 */
object M19IBeautyQComponentCombinationPolicyScaffold {

  /**
   * Explicit ADT for a component-level combination policy state. Exactly one state may permit a
   * Qdrant contribution, and even then only as an offline/eval supplement:
   *
   *   - [[EsOrCurrentOwnerOnly]]                     : the current ES / parser / lexical owner is
   *     the only source; no Qdrant contribution is approved (facets, inferred filters);
   *   - [[EsPrimaryWithQdrantSemanticSupplement]]    : ES is primary and a Qdrant semantic candidate
   *     set may *offline* supplement recall, with no fusion or reranking (variant carousel only);
   *   - [[NeedsMoreEvidence]]                        : the listed evidence cannot yet authorize a
   *     component-level policy (provider / service intent carousels);
   *   - [[NotApproved]]                              : a component-level hybrid policy is explicitly
   *     not approved.
   */
  enum PolicyState {
    case EsOrCurrentOwnerOnly
    case EsPrimaryWithQdrantSemanticSupplement
    case NeedsMoreEvidence
    case NotApproved

    /** Only the offline ES-primary + Qdrant semantic supplement state permits a Qdrant contribution. */
    def permitsQdrantContribution: Boolean =
      this == PolicyState.EsPrimaryWithQdrantSemanticSupplement
  }

  object PolicyState {
    val stableOrder: List[PolicyState] =
      List(EsOrCurrentOwnerOnly, EsPrimaryWithQdrantSemanticSupplement, NeedsMoreEvidence, NotApproved)
  }

  /**
   * One component-level combination policy row, recorded as data. Every field is explicit so the
   * scaffold can be reviewed without re-deriving anything:
   *
   *   - [[currentOwnerLayer]]                : the source-confirmed current owner/source layer, when
   *     the listed source files identify one; `None` otherwise;
   *   - [[candidateEvidenceAvailable]]       : whether a real per-backend Qdrant candidate set exists
   *     for this component in the listed M18/M19 evidence (BackendCandidateEvidence);
   *   - [[expectationCoverageAvailable]]     : whether the checked-in eval dataset carries component
   *     expectation coverage for this component;
   *   - [[state]]                            : the explicit [[PolicyState]];
   *   - [[reason]]                           : a non-empty rationale for the state;
   *   - [[qdrantContributionAllowed]]        : whether a Qdrant contribution is allowed (offline);
   *   - [[servingApproved]]                  : always `false`; this scaffold approves no serving.
   */
  final case class ComponentCombinationPolicyRow(
    componentId: BeautyResponseComponentId,
    currentOwnerLayer: Option[String],
    candidateEvidenceAvailable: Boolean,
    expectationCoverageAvailable: Boolean,
    state: PolicyState,
    reason: String,
    qdrantContributionAllowed: Boolean,
    servingApproved: Boolean,
  )

  /**
   * The full offline/eval-only combination policy scaffold: one row per BeautyQ response component,
   * plus the honesty flags carried through from the M19H evidence. The two crucial invariants are
   * encoded as data: no row approves serving, and exactly one row (variant carousel) permits an
   * offline Qdrant contribution.
   */
  final case class ComponentCombinationPolicy(
    rows: List[ComponentCombinationPolicyRow],
    offlineEvalOnly: Boolean,
    notServingPolicy: Boolean,
    doesNotApproveHybrid: Boolean,
    qdrantDoesNotOwnFacets: Boolean,
    qdrantDoesNotOwnInferredFilters: Boolean,
  ) {
    def rowFor(componentId: BeautyResponseComponentId): Option[ComponentCombinationPolicyRow] =
      rows.find(_.componentId == componentId)

    /** The only component permitted an offline Qdrant supplement, if any. */
    def qdrantPositiveComponents: List[BeautyResponseComponentId] =
      rows.filter(_.qdrantContributionAllowed).map(_.componentId)
  }

  object ComponentCombinationPolicy {

    /**
     * Build the policy scaffold from the M19H coverage evidence and the M19C per-component fact
     * sheet. Owner labels and candidate-evidence availability are read strictly from the
     * source-confirmed M19C facts; expectation coverage and the honesty flags come from the M19H
     * evidence. No policy is invented beyond the source-true mapping documented per component.
     */
    def build(
      evidence: ComponentCoverageEvidence,
      facts: List[ResponseComponentFact],
    ): ComponentCombinationPolicy = {
      val rows = BeautyResponseComponentId.stableOrder.map { componentId =>
        val fact = factFor(componentId, facts)
        buildRow(componentId, fact, evidence)
      }

      ComponentCombinationPolicy(
        rows = rows,
        offlineEvalOnly = evidence.offlineEvalOnly,
        notServingPolicy = evidence.notServingPolicy,
        doesNotApproveHybrid = evidence.doesNotApproveHybrid,
        qdrantDoesNotOwnFacets = evidence.qdrantDoesNotOwnFacets,
        qdrantDoesNotOwnInferredFilters = evidence.qdrantDoesNotOwnInferredFilters,
      )
    }

    private def factFor(
      componentId: BeautyResponseComponentId,
      facts: List[ResponseComponentFact],
    ): ResponseComponentFact =
      facts.find(_.componentId == componentId).getOrElse(
        throw new IllegalStateException(
          s"Missing M19C fact sheet entry for component ${componentId.render}; cannot build combination policy scaffold."
        )
      )

    private def buildRow(
      componentId: BeautyResponseComponentId,
      fact: ResponseComponentFact,
      evidence: ComponentCoverageEvidence,
    ): ComponentCombinationPolicyRow = {
      val owner =
        if (fact.productionOwnerSourceConfirmed) Some(fact.productionOwnerLabel) else None

      // "Candidate evidence available" means a *real per-backend Qdrant candidate set* exists for
      // this component in the listed M18/M19 evidence. Provider/service intent carry only
      // `CandidateLevelOnly` Qdrant evidence; facets/inferred carry `NotApplicable`. Only the
      // variant carousel has `BackendCandidateEvidence` on the Qdrant leg.
      val qdrantCandidateEvidence =
        fact.qdrantEvidence == ComponentEvidence.BackendCandidateEvidence

      val (state, qdrantAllowed, reason) = componentId match {
        case BeautyResponseComponentId.VariantCarousel =>
          (
            PolicyState.EsPrimaryWithQdrantSemanticSupplement,
            true,
            "ES-primary with an offline Qdrant semantic candidate supplement (ES ids first, then " +
              "Qdrant-only ids, no fusion or reranking). Rests on the real ES/Qdrant candidate " +
              "evidence (M18) and the M19 candidate metrics. This is the only Qdrant-positive " +
              "component decision and is offline/eval-only: it does not approve serving.",
          )

        case BeautyResponseComponentId.ProviderCarousel =>
          (
            PolicyState.NeedsMoreEvidence,
            false,
            "Source-confirmed provider grouping metadata (masterLocationId) is metadata, NOT a " +
              "component-level hybrid policy approval. There is no source-confirmed provider-grouping " +
              "projection turning a Qdrant candidate set into a provider carousel, so the policy stays " +
              "NeedsMoreEvidence and Qdrant contribution is not allowed.",
          )

        case BeautyResponseComponentId.ServiceIntentCarousel =>
          (
            PolicyState.NeedsMoreEvidence,
            false,
            "Source-confirmed service grouping metadata (serviceId) is metadata, NOT a " +
              "component-level hybrid policy approval. There is no source-confirmed service-grouping " +
              "projection turning a Qdrant candidate set into a service-intent carousel, so the policy " +
              "stays NeedsMoreEvidence and Qdrant contribution is not allowed.",
          )

        case BeautyResponseComponentId.Facets =>
          (
            PolicyState.EsOrCurrentOwnerOnly,
            false,
            "Facets remain lexical/ES-owned (ES aggregations over the FacetSpec fields). Qdrant does " +
              "not own facets in the listed source files and a Qdrant candidate set is not facets " +
              "evidence, so Qdrant contribution is not approved.",
          )

        case BeautyResponseComponentId.InferredFilters =>
          (
            PolicyState.EsOrCurrentOwnerOnly,
            false,
            "Inferred filters remain parser/DSL-owned (parser constraints plus the FacetSpec " +
              "dominance threshold). Qdrant does not own inferred filters in the listed source files, " +
              "so Qdrant contribution is not approved.",
          )
      }

      val expectationCoverageAvailable = componentId match {
        case BeautyResponseComponentId.VariantCarousel       =>
          evidence.variant.coveredByExistingCandidateVariantExpectations
        case BeautyResponseComponentId.ProviderCarousel      => evidence.provider.datasetHasExpectations
        case BeautyResponseComponentId.ServiceIntentCarousel => evidence.serviceIntent.datasetHasExpectations
        case BeautyResponseComponentId.Facets                => evidence.facets.datasetHasExpectations
        case BeautyResponseComponentId.InferredFilters       => evidence.inferredFilters.datasetHasExpectations
      }

      ComponentCombinationPolicyRow(
        componentId = componentId,
        currentOwnerLayer = owner,
        candidateEvidenceAvailable = qdrantCandidateEvidence,
        expectationCoverageAvailable = expectationCoverageAvailable,
        state = state,
        reason = reason,
        qdrantContributionAllowed = qdrantAllowed,
        // This scaffold approves no serving for any component, ever.
        servingApproved = false,
      )
    }
  }

  // --- Rendering --------------------------------------------------------------------------------

  /**
   * Render the policy scaffold as a readable markdown report. Pure string building; no I/O. The
   * report opens with the offline/eval-only banner so a reviewer cannot mistake the scaffold for
   * production activation.
   */
  def renderMarkdown(policy: ComponentCombinationPolicy): String = {
    val b = new StringBuilder

    line(b, "# M19I BeautyQ Component Combination Policy Scaffold")
    line(b, "")
    line(
      b,
      "Offline/eval-only policy-as-data. This records a per-component combination decision; it is " +
        "NOT production activation. It performs no retrieval, no fusion, no reranking, no fallback, " +
        "does not call `/beauty-search`, and does not activate Qdrant. No component approves serving, " +
        "and only the variant carousel permits an offline Qdrant semantic supplement.",
    )

    line(b, "")
    line(b, "## Per-component policy")
    policy.rows.foreach(renderRow(b, _))

    line(b, "")
    line(b, "## Scaffold-wide guarantees")
    line(b, s"- offline_eval_only: ${policy.offlineEvalOnly}")
    line(b, s"- not_serving_policy: ${policy.notServingPolicy}")
    line(b, s"- does_not_approve_hybrid: ${policy.doesNotApproveHybrid}")
    line(b, s"- qdrant_does_not_own_facets: ${policy.qdrantDoesNotOwnFacets}")
    line(b, s"- qdrant_does_not_own_inferred_filters: ${policy.qdrantDoesNotOwnInferredFilters}")
    line(b, s"- qdrant_positive_components: ${renderComponents(policy.qdrantPositiveComponents)}")
    line(b, s"- serving_approved_anywhere: ${policy.rows.exists(_.servingApproved)}")

    b.result()
  }

  private def renderRow(b: StringBuilder, row: ComponentCombinationPolicyRow): Unit = {
    line(b, "")
    line(b, s"### ${row.componentId.render}")
    line(b, s"- current_owner_layer: ${row.currentOwnerLayer.getOrElse("-")}")
    line(b, s"- candidate_evidence_available: ${row.candidateEvidenceAvailable}")
    line(b, s"- expectation_coverage_available: ${row.expectationCoverageAvailable}")
    line(b, s"- policy_state: ${row.state}")
    line(b, s"- qdrant_contribution_allowed: ${row.qdrantContributionAllowed}")
    line(b, s"- serving_approved: ${row.servingApproved}")
    line(b, s"- reason: ${row.reason}")
  }

  private def renderComponents(ids: List[BeautyResponseComponentId]): String =
    ids match {
      case Nil => "-"
      case _   => ids.map(_.render).mkString(", ")
    }

  private def line(b: StringBuilder, value: String): Unit = {
    b.append(value)
    b.append('\n')
  }
}
