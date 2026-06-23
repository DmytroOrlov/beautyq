package leaderboard.search.eval

import leaderboard.search.eval.M19CBeautyQResponseComponentTaxonomy.BeautyResponseComponentId
import leaderboard.search.eval.M19DBeautyQCoverageGapModel.{
  BeautyQCoverageGapModel,
  ComponentCoverageGap,
  PolicyBlockedDecision,
  PolicyEvidenceState,
}

/**
 * M19E: pure, source-confirmed proposal for expanding the BeautyQ eval dataset so that future
 * component-level hybrid-policy decisions can be made from evidence rather than inferred from
 * candidate ids alone.
 *
 * M19D already aggregates per-query coverage into a coverage-gap view and lists which
 * component-level policy decisions must remain `NeedsMoreEvidence`. M19E reads that M19D model
 * and turns each undercovered component into a concrete, source-confirmed proposal: what eval
 * evidence is missing, which JSON field(s) would have to be added (only if the existing response
 * model confirms their shape), what expectation shape is source-confirmed, which query
 * classes should be covered, whether the gap blocks policy or only improves confidence, and
 * which evidence dimension the gap concerns.
 *
 * Boundaries:
 *   - pure data only; no DI, no effects, no backend calls, no HTTP, no plugin, no production
 *     `/beauty-search` route;
 *   - does NOT edit the eval JSON. It only describes what fields a later, separately-approved
 *     dataset task could add. No seed expectations are invented here;
 *   - proposed JSON fields and expectation shapes are recorded only where source-confirmed by the
 *     listed response model (`BeautySearchModels`) and spec (`BeautySearchSpecV1`). Exact
 *     provider/service/facet/filter values are never invented;
 *   - never chooses a hybrid policy and never claims Qdrant can own facets or inferred filters.
 *     The M19D source-confirmed facts (`qdrantDoesNotOwnFacets`,
 *     `qdrantDoesNotOwnInferredFilters`) are carried through unchanged;
 *   - this is an offline/eval-only surface. It is not a serving approval and does not grant or
 *     encode any production behavior.
 */
object M19EBeautyQEvalDatasetExpansionProposal {

  /**
   * Which evidence dimension a coverage gap concerns. These map onto the M19C/M19D component
   * vocabulary: variant recall, provider grouping, service grouping, facets, inferred filters,
   * and the projection/hydration step that turns a candidate id set into a grouped carousel.
   */
  enum MissingEvidenceDimension {
    case VariantRecall
    case ProviderGrouping
    case ServiceGrouping
    case Facets
    case InferredFilters
    case ProjectionHydration
  }

  /** Whether a proposal item must be satisfied before policy, or only raises confidence. */
  enum ProposalImpact {
    case BlocksPolicy
    case ImprovesConfidenceOnly
  }

  /**
   * A single proposed eval JSON field. `jsonPath` is the dotted path under a query's `expected`
   * object. `sourceConfirmedBy` cites the listed source file that confirms the field's shape;
   * `alreadyPresentInSchema` records whether the decoder already reads the key (true) or whether
   * the field would be new (false). No values are carried — only the field's source-confirmed
   * existence/shape.
   */
  final case class ProposedJsonField(
    jsonPath: String,
    sourceConfirmedBy: String,
    alreadyPresentInSchema: Boolean,
  )

  /**
   * The M19D `NeedsMoreEvidence` reason carried verbatim for a blocking proposal. Every blocking
   * proposal item must reference exactly one of these so the chain back to M19D stays explicit.
   */
  final case class NeedsMoreEvidence(
    decisionId: String,
    reason: String,
  )

  /**
   * One proposal item for one response component.
   *
   *   - `componentId`              : the response component the proposal is about;
   *   - `currentCoverageState`     : human-readable summary derived from the M19D gap rollup;
   *   - `whyInsufficient`          : why the current evidence cannot decide policy;
   *   - `proposedJsonFields`       : source-confirmed eval JSON fields to add (may be empty);
   *   - `proposedExpectationShape` : source-confirmed expectation shape, only where confirmed;
   *   - `proposedQueryClasses`     : query classes the new evidence should cover;
   *   - `impact`                   : blocks policy vs. only improves confidence;
   *   - `missingEvidenceDimension` : which evidence dimension the gap concerns;
   *   - `needsMoreEvidence`        : the M19D blocking reason (present iff `impact` is
   *                                  `BlocksPolicy`);
   *   - `sourceCitations`          : the listed source files backing the proposal.
   */
  final case class EvalExpansionProposalItem(
    componentId: BeautyResponseComponentId,
    currentCoverageState: String,
    whyInsufficient: String,
    proposedJsonFields: List[ProposedJsonField],
    proposedExpectationShape: Option[String],
    proposedQueryClasses: List[EngineEvalQueryClass],
    impact: ProposalImpact,
    missingEvidenceDimension: MissingEvidenceDimension,
    needsMoreEvidence: Option[NeedsMoreEvidence],
    sourceCitations: List[String],
  )

  /**
   * Full M19E proposal: the per-component items plus the carried-through M19D honesty flags. The
   * proposal is an offline/eval-only surface; it does not approve a hybrid policy and does not
   * claim Qdrant can own facets or inferred filters.
   */
  final case class BeautyQEvalExpansionProposal(
    items: List[EvalExpansionProposalItem],
    offlineEvalOnly: Boolean,
    notServingPolicy: Boolean,
    doesNotApproveHybrid: Boolean,
    qdrantDoesNotOwnFacets: Boolean,
    qdrantDoesNotOwnInferredFilters: Boolean,
  ) {

    /** The components for which the proposal records a policy-blocking gap. */
    def blockingComponents: List[BeautyResponseComponentId] =
      items.collect { case item if item.impact == ProposalImpact.BlocksPolicy => item.componentId }
  }

  object BeautyQEvalExpansionProposal {

    /**
     * Build the proposal from an M19D coverage-gap model. Blocking proposal items are generated
     * for every M19D undercovered component, in the M19D stable order. The variant carousel is
     * added as a confidence-only item iff M19D does NOT list it as undercovered; it is never
     * treated as policy-blocked.
     */
    def fromCoverageGapModel(model: BeautyQCoverageGapModel): BeautyQEvalExpansionProposal = {
      val blockingItems: List[EvalExpansionProposalItem] =
        model.undercoveredComponents.map { componentId =>
          buildBlockingItem(componentId, model)
        }

      val variantItem: List[EvalExpansionProposalItem] =
        if (model.undercoveredComponents.contains(BeautyResponseComponentId.VariantCarousel)) Nil
        else List(buildVariantConfidenceItem(model))

      BeautyQEvalExpansionProposal(
        items = blockingItems ++ variantItem,
        offlineEvalOnly = model.offlineEvalOnly,
        notServingPolicy = model.notServingPolicy,
        doesNotApproveHybrid = model.doesNotApproveHybrid,
        qdrantDoesNotOwnFacets = model.qdrantDoesNotOwnFacets,
        qdrantDoesNotOwnInferredFilters = model.qdrantDoesNotOwnInferredFilters,
      )
    }

    private def gapFor(
      componentId: BeautyResponseComponentId,
      model: BeautyQCoverageGapModel,
    ): ComponentCoverageGap =
      model.componentGaps.find(_.componentId == componentId).getOrElse(
        throw new IllegalStateException(
          s"Missing M19D component gap for ${componentId.render}; cannot build M19E proposal."
        )
      )

    private def needsMoreEvidenceFor(
      componentId: BeautyResponseComponentId,
      model: BeautyQCoverageGapModel,
    ): NeedsMoreEvidence =
      model.policyBlockedDecisions.find { decision =>
        decision.componentId == componentId && decision.state == PolicyEvidenceState.NeedsMoreEvidence
      } match {
        case Some(decision: PolicyBlockedDecision) =>
          NeedsMoreEvidence(decisionId = decision.decisionId, reason = decision.reason)
        case None =>
          throw new IllegalStateException(
            s"M19D reports ${componentId.render} undercovered but no NeedsMoreEvidence decision was found; " +
              "M19E refuses to invent a blocking reason."
          )
      }

    private def coverageSummary(gap: ComponentCoverageGap): String =
      s"M19D: sourceConfirmed=${gap.sourceConfirmedQueryCount}/${gap.totalQueryCount}, " +
        s"notInformed=${gap.notInformedByQueryCount}, fieldAbsent=${gap.fieldAbsentInSourceCount}, " +
        s"candidateLevelOnly=${gap.candidateLevelOnly}, policyBlocked=${gap.policyBlocked}"

    private def buildBlockingItem(
      componentId: BeautyResponseComponentId,
      model: BeautyQCoverageGapModel,
    ): EvalExpansionProposalItem = {
      val gap = gapFor(componentId, model)
      val nme = needsMoreEvidenceFor(componentId, model)

      componentId match {
        case BeautyResponseComponentId.ProviderCarousel =>
          EvalExpansionProposalItem(
            componentId = componentId,
            currentCoverageState = coverageSummary(gap),
            whyInsufficient =
              "Provider expectations exist as acceptable masterLocationId sets, but candidate ids alone do " +
                "not prove the provider-grouping projection. There is no source-confirmed evidence that " +
                "grouping variant candidates by masterLocationId yields the expected provider carousel, so a " +
                "provider ownership policy cannot be chosen from candidate ids alone.",
            proposedJsonFields = List(
              ProposedJsonField(
                jsonPath = "expected.providerCarousel.acceptableProviderLocationIds",
                sourceConfirmedBy = "BeautyQEvalCarouselJson.acceptableProviderLocationIds (M19C decoder)",
                alreadyPresentInSchema = true,
              ),
              ProposedJsonField(
                jsonPath = "expected.providerCarousel.expectedGroupingField",
                sourceConfirmedBy = "BeautySearchSpecV1.carouselSpec.providerGroupField = \"masterLocationId\"",
                alreadyPresentInSchema = false,
              ),
              ProposedJsonField(
                jsonPath = "expected.providerCarousel.expectedMatchingVariantCounts",
                sourceConfirmedBy = "BeautySearchModels.ProviderSearchResult.matchingVariantCount / sampleMatchingVariantIds",
                alreadyPresentInSchema = false,
              ),
            ),
            proposedExpectationShape = Some(
              "ProviderSearchResult rows grouped by masterLocationId, each carrying matchingVariantCount and " +
                "sampleMatchingVariantIds (source-confirmed: BeautySearchModels.ProviderSearchResult; " +
                "BeautySearchSpecV1.carouselSpec.providerGroupField = masterLocationId). No specific " +
                "location ids or counts are proposed here."
            ),
            proposedQueryClasses = List(
              EngineEvalQueryClass.GeoLocal,
              EngineEvalQueryClass.BroadIntent,
              EngineEvalQueryClass.ExactService,
            ),
            impact = ProposalImpact.BlocksPolicy,
            missingEvidenceDimension = MissingEvidenceDimension.ProviderGrouping,
            needsMoreEvidence = Some(nme),
            sourceCitations = List(
              "BeautySearchModels.scala (ProviderSearchResult, providerCarousel field)",
              "BeautySearchSpecV1.scala (carouselSpec.providerGroupField = masterLocationId)",
              "M19DBeautyQCoverageGapModel (provider_carousel_projection_ownership NeedsMoreEvidence)",
            ),
          )

        case BeautyResponseComponentId.ServiceIntentCarousel =>
          EvalExpansionProposalItem(
            componentId = componentId,
            currentCoverageState = coverageSummary(gap),
            whyInsufficient =
              "Service-intent expectations exist as acceptable serviceId sets, but candidate ids alone do not " +
                "prove the service-grouping projection. There is no source-confirmed evidence that grouping " +
                "variant candidates by serviceId yields the expected service-intent carousel, so a service " +
                "ownership policy cannot be chosen from candidate ids alone.",
            proposedJsonFields = List(
              ProposedJsonField(
                jsonPath = "expected.serviceIntentCarousel.acceptableServiceIds",
                sourceConfirmedBy = "BeautyQEvalCarouselJson.acceptableServiceIds (M19C decoder)",
                alreadyPresentInSchema = true,
              ),
              ProposedJsonField(
                jsonPath = "expected.serviceIntentCarousel.expectedGroupingField",
                sourceConfirmedBy = "BeautySearchSpecV1.carouselSpec.serviceIntentGroupField = \"serviceId\"",
                alreadyPresentInSchema = false,
              ),
              ProposedJsonField(
                jsonPath = "expected.serviceIntentCarousel.expectedMatchingVariantCounts",
                sourceConfirmedBy = "BeautySearchModels.ServiceIntentSearchResult.matchingVariantCount",
                alreadyPresentInSchema = false,
              ),
            ),
            proposedExpectationShape = Some(
              "ServiceIntentSearchResult rows grouped by serviceId, each carrying matchingVariantCount " +
                "(source-confirmed: BeautySearchModels.ServiceIntentSearchResult; " +
                "BeautySearchSpecV1.carouselSpec.serviceIntentGroupField = serviceId). No specific service " +
                "ids or counts are proposed here."
            ),
            proposedQueryClasses = List(
              EngineEvalQueryClass.Category,
              EngineEvalQueryClass.BroadIntent,
              EngineEvalQueryClass.SemanticVague,
            ),
            impact = ProposalImpact.BlocksPolicy,
            missingEvidenceDimension = MissingEvidenceDimension.ServiceGrouping,
            needsMoreEvidence = Some(nme),
            sourceCitations = List(
              "BeautySearchModels.scala (ServiceIntentSearchResult, serviceIntentCarousel field)",
              "BeautySearchSpecV1.scala (carouselSpec.serviceIntentGroupField = serviceId)",
              "M19DBeautyQCoverageGapModel (service_intent_carousel_projection_ownership NeedsMoreEvidence)",
            ),
          )

        case BeautyResponseComponentId.Facets =>
          EvalExpansionProposalItem(
            componentId = componentId,
            currentCoverageState = coverageSummary(gap),
            whyInsufficient =
              "The listed eval JSON carries no facets expectations at all, so facet correctness cannot be " +
                "evaluated and a facets policy cannot be chosen. The gap is an absent JSON field, not a " +
                "wrong expectation. Qdrant does not own facets in the listed source files, so a Qdrant " +
                "candidate set cannot stand in as facets evidence.",
            proposedJsonFields = List(
              ProposedJsonField(
                jsonPath = "expected.facets",
                sourceConfirmedBy = "BeautySearchModels.BeautySearchFacet { fieldPath, values: [{ value, count }] }",
                alreadyPresentInSchema = false,
              )
            ),
            proposedExpectationShape = Some(
              "List of BeautySearchFacet { fieldPath, values: [{ value, count }] } over the FacetSpec fields " +
                "(serviceName, categoryName, priceFrom ranges, durationMin ranges, enumAttributes.<code>, " +
                "booleanAttributes.<code>) — source-confirmed: BeautySearchModels.BeautySearchFacet; " +
                "BeautySearchSpecV1.facetSpec.fields. No specific facet values or counts are proposed here; " +
                "seed expectations are not invented."
            ),
            proposedQueryClasses = List(
              EngineEvalQueryClass.StructuredFilter,
              EngineEvalQueryClass.PriceDuration,
              EngineEvalQueryClass.Category,
            ),
            impact = ProposalImpact.BlocksPolicy,
            missingEvidenceDimension = MissingEvidenceDimension.Facets,
            needsMoreEvidence = Some(nme),
            sourceCitations = List(
              "BeautySearchModels.scala (BeautySearchFacet, BeautySearchFacetValue, facets field)",
              "BeautySearchSpecV1.scala (facetSpec.fields)",
              "M19DBeautyQCoverageGapModel (facets_ownership / facets_facet_field_coverage NeedsMoreEvidence)",
            ),
          )

        case BeautyResponseComponentId.InferredFilters =>
          EvalExpansionProposalItem(
            componentId = componentId,
            currentCoverageState = coverageSummary(gap),
            whyInsufficient =
              "The listed eval JSON carries no inferredFilters expectations at all, so inferred-filter " +
                "correctness and the FacetSpec dominance threshold cannot be evaluated, and an " +
                "inferred-filters policy cannot be chosen. The gap is an absent JSON field, not a wrong " +
                "expectation. Qdrant does not own inferred filters in the listed source files, so a Qdrant " +
                "candidate set cannot stand in as inferred-filters evidence.",
            proposedJsonFields = List(
              ProposedJsonField(
                jsonPath = "expected.inferredFilters",
                sourceConfirmedBy = "BeautySearchModels.BeautySearchAppliedFilter { constraint, explicit }",
                alreadyPresentInSchema = false,
              )
            ),
            proposedExpectationShape = Some(
              "List of BeautySearchAppliedFilter { constraint, explicit } whose values would be reduced via " +
                "BeautySearchSpecV1.facetSpec.inferredFilterDominanceThreshold = 0.70 and inferredFilterMinCount = 2 " +
                "— source-confirmed: BeautySearchModels.BeautySearchAppliedFilter; BeautySearchSpecV1.facetSpec. " +
                "No specific constraint field/value/confidence is proposed here; seed expectations are not " +
                "invented."
            ),
            proposedQueryClasses = List(
              EngineEvalQueryClass.StructuredFilter,
              EngineEvalQueryClass.ExactService,
              EngineEvalQueryClass.PriceDuration,
            ),
            impact = ProposalImpact.BlocksPolicy,
            missingEvidenceDimension = MissingEvidenceDimension.InferredFilters,
            needsMoreEvidence = Some(nme),
            sourceCitations = List(
              "BeautySearchModels.scala (BeautySearchAppliedFilter, inferredFilters field)",
              "BeautySearchSpecV1.scala (facetSpec.inferredFilterDominanceThreshold = 0.70, inferredFilterMinCount = 2)",
              "M19DBeautyQCoverageGapModel (inferred_filter_ownership / inferred_filter_threshold_evidence NeedsMoreEvidence)",
            ),
          )

        case BeautyResponseComponentId.VariantCarousel =>
          // M19D never lists the variant carousel as undercovered. If this branch is reached the
          // upstream model is inconsistent; M19E refuses to fabricate a variant blocker.
          throw new IllegalStateException(
            "M19D listed the variant carousel as undercovered, which contradicts the M19D source-confirmed " +
              "fact that the variant carousel is not policy-blocked; M19E will not invent a variant blocker."
          )
      }
    }

    /**
     * Confidence-only proposal for the variant carousel. M19D reports the variant carousel as
     * already covered (per-backend candidate evidence plus per-query expectations), so this item
     * is `ImprovesConfidenceOnly` and carries no `NeedsMoreEvidence`. It proposes broadening
     * variant recall coverage, not unblocking a policy.
     */
    private def buildVariantConfidenceItem(
      model: BeautyQCoverageGapModel
    ): EvalExpansionProposalItem = {
      val gap = gapFor(BeautyResponseComponentId.VariantCarousel, model)

      EvalExpansionProposalItem(
        componentId = BeautyResponseComponentId.VariantCarousel,
        currentCoverageState = coverageSummary(gap),
        whyInsufficient =
          "Not a policy blocker: M19D already reports the variant carousel as source-confirmed for every " +
            "query and backed by per-backend candidate evidence. Expanding semantic/typo/hard-negative " +
            "recall coverage would only raise confidence in a future variant-recall policy; it is not " +
            "required to choose one.",
        proposedJsonFields = List(
          ProposedJsonField(
            jsonPath = "expected.variantCarousel.acceptableVariantIds",
            sourceConfirmedBy = "BeautyQEvalCarouselJson.acceptableVariantIds (M19C decoder)",
            alreadyPresentInSchema = true,
          )
        ),
        proposedExpectationShape = Some(
          "Existing acceptableVariantIds expectation shape, broadened to more semantic/typo/hard-negative " +
            "queries (source-confirmed: BeautyQEvalCarouselJson.acceptableVariantIds). No new field and no " +
            "specific variant ids are proposed here."
        ),
        proposedQueryClasses = List(
          EngineEvalQueryClass.SemanticVague,
          EngineEvalQueryClass.Mixed,
          EngineEvalQueryClass.HardNegative,
        ),
        impact = ProposalImpact.ImprovesConfidenceOnly,
        missingEvidenceDimension = MissingEvidenceDimension.VariantRecall,
        needsMoreEvidence = None,
        sourceCitations = List(
          "BeautySearchModels.scala (VariantSearchResult, variantCarousel field)",
          "M19DBeautyQCoverageGapModel (variant carousel not undercovered)",
        ),
      )
    }
  }
}
