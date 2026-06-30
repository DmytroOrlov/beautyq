package leaderboard.search.eval

import leaderboard.search.eval.M19CBeautyQResponseComponentTaxonomy.{
  BeautyQResponseComponentTaxonomy,
  BeautyResponseComponentId,
  EvalQueryCoverage,
}

/**
 * M19D: pure, source-confirmed eval-query coverage-gap model and report for BeautyQ
 * response components.
 *
 * The M19C taxonomy already lists the five response components and the per-component
 * fact sheet, and the per-query `EvalQueryCoverage` row already records, for each query,
 * which `expected.*` JSON keys are present and which `inform*` flags are derivable.
 *
 * M19D aggregates that per-query evidence into a coverage-gap view: for each response
 * component, how many of the listed 74 eval queries can inform policy from their JSON
 * expectations (not from candidate ids alone), how many are unknown, and which
 * component-level policy questions must therefore remain `NeedsMoreEvidence` until
 * the JSON grows new fields or until a separate ES-aggregation evidence surface is
 * built.
 *
 * Boundaries:
 *   - pure data only; no DI, no effects, no backend calls, no HTTP, no plugin, no
 *     production `/beauty-search` route;
 *   - derives strictly from the M19C `BeautyQResponseComponentTaxonomy` plus the
 *     `BeautyQEvalQueryJson` rows in the listed eval-query JSON. No field is
 *     invented; missing JSON fields stay `Unknown` / `NotAvailable`;
 *   - candidate-level evidence (M18 per-backend candidate id sets) is reported as
 *     candidate-level evidence, not as policy authorization. Per the AGENTS.md
 *     "BeautyQ search principles" section, facets and inferred filters are not
 *     Qdrant-owned, and a backend candidate set does not, by itself, prove a
 *     response part;
 *   - the model never chooses a hybrid policy. It only enumerates which
 *     component-level policy decisions cannot be made yet from the listed dataset.
 */
object M19DBeautyQCoverageGapModel {

  /**
   * Per-query row carrying only the source-confirmed coverage flags used by M19D
   * aggregation. The flags here are derived from `EvalQueryCoverage`; if a flag is
   * `Unknown` in M19C, M19D keeps the same `Unknown` state rather than guessing.
   */
  final case class QueryCoverageRow(
    queryId: String,
    queryText: Option[String],
    queryClass: Option[EngineEvalQueryClass],
    rawQueryTypes: List[String],
    informVariantRecall: ComponentCoverageState,
    informProviderCarousel: ComponentCoverageState,
    informServiceIntentCarousel: ComponentCoverageState,
    informFacets: ComponentCoverageState,
    informInferredFilters: ComponentCoverageState,
    candidateLevelOnly: Boolean,
  )

  object QueryCoverageRow {

    /**
     * Build a coverage row from the M19C `EvalQueryCoverage`. The `queryClass`
     * collapses the multi-class list to a single representative class for
     * per-query reporting; the full list is preserved in `rawQueryTypes` and in
     * M19C's `queryClasses` field. None of the flags are invented: each one mirrors
     * the `inform*` field on `EvalQueryCoverage` exactly.
     */
    def fromEvalCoverage(coverage: EvalQueryCoverage): QueryCoverageRow =
      QueryCoverageRow(
        queryId = coverage.queryId,
        queryText = coverage.queryText,
        queryClass = coverage.queryClasses.headOption,
        rawQueryTypes = coverage.rawQueryTypes,
        informVariantRecall = boolToState(coverage.informVariantRecall),
        informProviderCarousel = boolToState(coverage.informProviderCarousel),
        informServiceIntentCarousel = boolToState(coverage.informServiceIntentCarousel),
        // The M19C coverage model marks facets and inferred filters as `inform=false`
        // because the listed JSON carries no expectations for them. The honest state is
        // therefore "field absent / unknown", not "false evidence" or "covered".
        informFacets = ComponentCoverageState.FieldAbsentInSource,
        informInferredFilters = ComponentCoverageState.FieldAbsentInSource,
        candidateLevelOnly = coverage.candidateLevelOnly,
      )

    private def boolToState(value: Boolean): ComponentCoverageState =
      if (value) ComponentCoverageState.SourceConfirmed
      else ComponentCoverageState.NotInformedByQuery
  }

  /** Honest tri-state for per-query per-component coverage. */
  enum ComponentCoverageState {
    /** The query's JSON carries expectations that can inform this component. */
    case SourceConfirmed
    /** The query's JSON does not carry expectations for this component. */
    case NotInformedByQuery
    /** The source-of-truth JSON has no field for this component at all. */
    case FieldAbsentInSource
  }

  object ComponentCoverageState {
    val stableOrder: List[ComponentCoverageState] =
      List(SourceConfirmed, NotInformedByQuery, FieldAbsentInSource)
  }

  /**
   * Per-component coverage-gap rollup. Counts are derived only from the per-query
   * rows; `undercovered` is true when the component has at least one
   * policy-blocking condition.
   */
  final case class ComponentCoverageGap(
    componentId: BeautyResponseComponentId,
    sourceConfirmedQueryCount: Int,
    notInformedByQueryCount: Int,
    fieldAbsentInSourceCount: Int,
    totalQueryCount: Int,
    candidateLevelOnly: Boolean,
    hasCandidateLevelEvidence: Boolean,
    hasNonCandidateEvidence: Boolean,
    policyBlocked: Boolean,
    policyBlockingReasons: List[String],
  )

  /**
   * Full M19D coverage-gap model: per-query rows, per-component rollup, and a list
   * of policy questions that must remain `NeedsMoreEvidence`. The model is an
   * offline/eval-only surface; it does not grant or encode any production serving
   * behavior.
   */
  final case class BeautyQCoverageGapModel(
    queryRows: List[QueryCoverageRow],
    componentGaps: List[ComponentCoverageGap],
    undercoveredComponents: List[BeautyResponseComponentId],
    policyBlockedDecisions: List[PolicyBlockedDecision],
    totalQueryCount: Int,
    queryClassCounts: List[(Option[EngineEvalQueryClass], Int)],
    offlineEvalOnly: Boolean,
    notServingPolicy: Boolean,
    doesNotApproveHybrid: Boolean,
    qdrantDoesNotOwnFacets: Boolean,
    qdrantDoesNotOwnInferredFilters: Boolean,
  )

  object BeautyQCoverageGapModel {

    /**
     * Build a coverage-gap model from an M19C taxonomy. All aggregates are computed
     * strictly from the per-query `EvalQueryCoverage` rows; the component-level
     * `candidateLevelOnly` flag and the canonical ES/Qdrant evidence levels come
     * from the M19C fact sheet rather than from per-query state, so the M19C
     * source-confirmed "Qdrant does not produce facets" etc. is preserved.
     */
    def fromTaxonomy(taxonomy: BeautyQResponseComponentTaxonomy): BeautyQCoverageGapModel = {
      val rows       = taxonomy.evalQueryCoverage.map(QueryCoverageRow.fromEvalCoverage)
      val components = taxonomy.components
      val totalCount = rows.size

      val byClass: List[(Option[EngineEvalQueryClass], Int)] =
        EngineEvalQueryClass.stableOrder.map { cls =>
          Option(cls) -> rows.count(_.queryClass.contains(cls))
        }

      val componentGaps: List[ComponentCoverageGap] =
        BeautyResponseComponentId.stableOrder.map { componentId =>
          rollupComponent(componentId, components, rows, totalCount)
        }

      val undercovered: List[BeautyResponseComponentId] =
        componentGaps.collect { case gap if gap.policyBlocked => gap.componentId }

      val policyBlockedDecisions: List[PolicyBlockedDecision] =
        undercovered.flatMap(blockedDecisionsFor)

      BeautyQCoverageGapModel(
        queryRows = rows,
        componentGaps = componentGaps,
        undercoveredComponents = undercovered,
        policyBlockedDecisions = policyBlockedDecisions,
        totalQueryCount = totalCount,
        queryClassCounts = byClass,
        offlineEvalOnly = true,
        notServingPolicy = true,
        doesNotApproveHybrid = true,
        qdrantDoesNotOwnFacets = true,
        qdrantDoesNotOwnInferredFilters = true,
      )
    }

    private def rollupComponent(
      componentId: BeautyResponseComponentId,
      components: List[M19CBeautyQResponseComponentTaxonomy.ResponseComponentFact],
      rows: List[QueryCoverageRow],
      totalCount: Int,
    ): ComponentCoverageGap = {
      val fact = components.find(_.componentId == componentId).getOrElse(
        throw new IllegalStateException(
          s"Missing M19C fact sheet entry for component ${componentId.render}; cannot build coverage-gap model."
        )
      )

      val coverageValues: List[ComponentCoverageState] = componentId match {
        case BeautyResponseComponentId.VariantCarousel        => rows.map(_.informVariantRecall)
        case BeautyResponseComponentId.ProviderCarousel       => rows.map(_.informProviderCarousel)
        case BeautyResponseComponentId.ServiceIntentCarousel  => rows.map(_.informServiceIntentCarousel)
        case BeautyResponseComponentId.Facets                 => rows.map(_.informFacets)
        case BeautyResponseComponentId.InferredFilters        => rows.map(_.informInferredFilters)
      }

      val sourceConfirmed = coverageValues.count(_ == ComponentCoverageState.SourceConfirmed)
      val notInformed     = coverageValues.count(_ == ComponentCoverageState.NotInformedByQuery)
      val fieldAbsent     = coverageValues.count(_ == ComponentCoverageState.FieldAbsentInSource)

      // A component has candidate-level evidence when either backend contributed
      // candidate ids for it. The "BackendCandidateEvidence" vs "CandidateLevelOnly"
      // distinction in M19C is preserved: BackendCandidateEvidence means both ES and
      // Qdrant produced candidate ids for the component; CandidateLevelOnly means the
      // candidate set is a meaningful id set but is not by itself a response part.
      val hasCandidateLevelEvidence =
        fact.esEvidence == M19CBeautyQResponseComponentTaxonomy.ComponentEvidence.BackendCandidateEvidence ||
          fact.qdrantEvidence == M19CBeautyQResponseComponentTaxonomy.ComponentEvidence.BackendCandidateEvidence

      // Non-candidate evidence requires something more than candidate ids: the M19C
      // fact sheet must mark the component as not candidate-only AND the component
      // must not be a grouped carousel whose JSON expectations still need a
      // projection step to be checked. Only the variant carousel currently satisfies
      // that bar in the listed source files.
      val hasNonCandidateEvidence = !fact.candidateLevelOnly && (componentId match {
        case BeautyResponseComponentId.VariantCarousel => true
        case _                                          => false
      })

      val policyBlockingReasons = policyBlockingReasonsFor(componentId, sourceConfirmed, totalCount, fieldAbsent)

      ComponentCoverageGap(
        componentId = componentId,
        sourceConfirmedQueryCount = sourceConfirmed,
        notInformedByQueryCount = notInformed,
        fieldAbsentInSourceCount = fieldAbsent,
        totalQueryCount = totalCount,
        candidateLevelOnly = fact.candidateLevelOnly,
        hasCandidateLevelEvidence = hasCandidateLevelEvidence,
        hasNonCandidateEvidence = hasNonCandidateEvidence,
        policyBlocked = policyBlockingReasons.nonEmpty,
        policyBlockingReasons = policyBlockingReasons,
      )
    }

    private def policyBlockingReasonsFor(
      componentId: BeautyResponseComponentId,
      sourceConfirmed: Int,
      totalCount: Int,
      fieldAbsent: Int,
    ): List[String] =
      componentId match {
        case BeautyResponseComponentId.Facets =>
          List(
            s"Facets coverage is `FieldAbsentInSource` for $fieldAbsent/$totalCount queries; the listed " +
              "JSON carries no facets expectations and there is no ES-aggregation evidence surface in M19, " +
              "so a facets policy decision must remain `NeedsMoreEvidence`.",
            "Qdrant does not own facets in the listed source files; a Qdrant candidate set is not " +
              "facets evidence and cannot authorize a facets policy.",
          )
        case BeautyResponseComponentId.InferredFilters =>
          List(
            s"InferredFilters coverage is `FieldAbsentInSource` for $fieldAbsent/$totalCount queries; the " +
              "listed JSON carries no inferredFilters expectations and there is no inferred-filters " +
              "evidence surface in M19, so an inferred-filters policy decision must remain " +
              "`NeedsMoreEvidence`.",
            "Qdrant does not own inferred filters in the listed source files; a Qdrant candidate set " +
              "is not inferred-filters evidence and cannot authorize an inferred-filters policy.",
          )
        case BeautyResponseComponentId.ProviderCarousel =>
          List(
            "ProviderCarousel has variant-candidate evidence only; there is no source-confirmed " +
              "provider-grouping projection over the listed JSON, so a provider-carousel policy " +
              "decision must remain `NeedsMoreEvidence` until a provider-grouping projection is " +
              "source-confirmed.",
            s"ProviderCarousel JSON expectations are present in $sourceConfirmed/$totalCount queries, " +
              "but they are not sufficient on their own to choose a hybrid policy without the " +
              "provider-grouping projection step.",
          )
        case BeautyResponseComponentId.ServiceIntentCarousel =>
          List(
            "ServiceIntentCarousel has variant-candidate evidence only; there is no source-confirmed " +
              "service-grouping projection over the listed JSON, so a service-intent-carousel policy " +
              "decision must remain `NeedsMoreEvidence` until a service-grouping projection is " +
              "source-confirmed.",
            s"ServiceIntentCarousel JSON expectations are present in $sourceConfirmed/$totalCount " +
              "queries, but they are not sufficient on their own to choose a hybrid policy without " +
              "the service-grouping projection step.",
          )
        case BeautyResponseComponentId.VariantCarousel =>
          // VariantCarousel has both backend candidate evidence and JSON expectations on every query.
          // A variant-carousel policy is not policy-blocked by M19D: it has source-confirmed expectations
          // and per-backend candidate evidence. Do not invent a blocker here.
          Nil
      }

    private def blockedDecisionsFor(
      componentId: BeautyResponseComponentId
    ): List[PolicyBlockedDecision] =
      componentId match {
        case BeautyResponseComponentId.Facets =>
          List(
            PolicyBlockedDecision(
              componentId = componentId,
              decisionId = "facets_ownership",
              state = PolicyEvidenceState.NeedsMoreEvidence,
              reason = "Cannot choose a facets owner (ES / Qdrant / both / neither) from the listed " +
                "eval dataset: facets expectations are absent from the JSON, and Qdrant does not " +
                "own facets in the listed source files.",
            ),
            PolicyBlockedDecision(
              componentId = componentId,
              decisionId = "facets_facet_field_coverage",
              state = PolicyEvidenceState.NeedsMoreEvidence,
              reason = "Cannot evaluate per-facet-field coverage of the listed eval queries: the JSON " +
                "carries no facets field expectations, so there is no per-facet-field evidence " +
                "surface to roll up.",
            ),
          )
        case BeautyResponseComponentId.InferredFilters =>
          List(
            PolicyBlockedDecision(
              componentId = componentId,
              decisionId = "inferred_filter_ownership",
              state = PolicyEvidenceState.NeedsMoreEvidence,
              reason = "Cannot choose an inferred-filters owner (ES / parser / Qdrant / none) from the " +
                "listed eval dataset: inferredFilters expectations are absent from the JSON, and " +
                "Qdrant does not own inferred filters in the listed source files.",
            ),
            PolicyBlockedDecision(
              componentId = componentId,
              decisionId = "inferred_filter_threshold_evidence",
              state = PolicyEvidenceState.NeedsMoreEvidence,
              reason = "Cannot evaluate inferred-filter threshold evidence (FacetSpec dominance) from " +
                "the listed eval dataset: the JSON carries no inferredFilters expectations, so the " +
                "threshold behaviour has no per-query evidence surface in the listed source files.",
            ),
          )
        case BeautyResponseComponentId.ProviderCarousel =>
          List(
            PolicyBlockedDecision(
              componentId = componentId,
              decisionId = "provider_carousel_projection_ownership",
              state = PolicyEvidenceState.NeedsMoreEvidence,
              reason = "Cannot choose a provider-carousel ownership policy from candidate evidence " +
                "alone: the listed eval dataset carries provider JSON expectations, but there is no " +
                "source-confirmed provider-grouping projection step that turns ES or Qdrant " +
                "candidate ids into a provider carousel.",
            )
          )
        case BeautyResponseComponentId.ServiceIntentCarousel =>
          List(
            PolicyBlockedDecision(
              componentId = componentId,
              decisionId = "service_intent_carousel_projection_ownership",
              state = PolicyEvidenceState.NeedsMoreEvidence,
              reason = "Cannot choose a service-intent-carousel ownership policy from candidate " +
                "evidence alone: the listed eval dataset carries service JSON expectations, but " +
                "there is no source-confirmed service-grouping projection step that turns ES or " +
                "Qdrant candidate ids into a service-intent carousel.",
            )
          )
        case BeautyResponseComponentId.VariantCarousel => Nil
      }
  }

  /**
   * A single policy decision that the listed dataset cannot yet authorize. The
   * `state` is always `NeedsMoreEvidence` for the M19D rollup; richer states can
   * be added later by a dedicated policy task once evidence is grown.
   */
  final case class PolicyBlockedDecision(
    componentId: BeautyResponseComponentId,
    decisionId: String,
    state: PolicyEvidenceState,
    reason: String,
  )

  /** Tri-state for an evidence-bound policy decision. */
  enum PolicyEvidenceState {
    case NeedsMoreEvidence
    case CandidateOnly
    case SourceConfirmed
  }
}
