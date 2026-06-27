package leaderboard.search.eval

import leaderboard.search.eval.M19CBeautyQResponseComponentTaxonomy.BeautyResponseComponentId
import leaderboard.search.eval.M19DBeautyQCoverageGapModel.{BeautyQCoverageGapModel, PolicyBlockedDecision}
import leaderboard.search.eval.M19FBeautyQEvalComponentExpectationSchema.{
  ComponentExpectationCoverage,
  EvalQueryComponentExpectations,
}

/**
 * M19H: pure offline/eval coverage evidence over the now-populated component expectations.
 *
 * M19F made the eval dataset *capable of carrying* component-level expectations and gave a
 * tolerant decoder + tri-state coverage view. M19G then populated the checked-in 64-query BeautyQ
 * dataset with the source-confirmed grouping fields:
 *   - provider carousel       : `expectedGroupingField = "masterLocationId"`
 *                               (BeautySearchSpecV1.carouselSpec.providerGroupField);
 *   - service intent carousel  : `expectedGroupingField = "serviceId"`
 *                               (BeautySearchSpecV1.carouselSpec.serviceIntentGroupField).
 *
 * M19H refreshes the *evidence* so a reviewer can see, at a glance, what is now source-confirmed
 * and what is still a gap before any component-level hybrid policy scaffold. It joins:
 *   - the M19F per-query component-expectation coverage (provider / service / facets /
 *     inferred filters), now reading the populated grouping fields; and
 *   - the M19D coverage-gap model, which keeps the per-component policy blockers explicit and
 *     leaves the variant carousel covered by existing candidate/variant expectations.
 *
 * It does NOT invent values, NOT choose a hybrid policy, NOT claim a component-level policy is
 * ready, and NOT claim Qdrant can own facets or inferred filters. The whole point of the
 * `metadataSourceConfirmed` vs `componentPolicyApproved` split below is to make the distinction
 * between "source-confirmed metadata" and "policy approval" unmissable: the grouping fields are
 * source-confirmed metadata; no component-level policy is approved here.
 *
 * Boundaries: pure data + rendering only; no DI, no effects, no backend calls, no HTTP, no plugin,
 * no production `/beauty-search` route, no fallback/fusion/reranking, no Qdrant activation.
 */
object M19HBeautyQComponentCoverageEvidenceReport {

  /** Honest availability of a component in the checked-in dataset, distinct from policy approval. */
  enum ComponentDatasetAvailability {
    /** The schema supports the component AND the checked-in dataset carries expectations for it. */
    case SourceConfirmedInDataset
    /** The schema supports the component but no checked-in query carries it (e.g. facets). */
    case SupportedButAbsentFromDataset
  }

  /**
   * Grouping-field coverage for a grouped carousel (provider / service intent). Records that the
   * field is schema-supported, that the checked-in dataset carries expectations, and the single
   * source-confirmed grouping field value — never a guess.
   */
  final case class GroupingFieldCoverage(
    componentId: BeautyResponseComponentId,
    schemaSupported: Boolean,
    datasetHasExpectations: Boolean,
    availability: ComponentDatasetAvailability,
    expectedGroupingField: Option[String],
    groupingFieldConsistent: Boolean,
    expectationQueryCount: Int,
    totalQueryCount: Int,
  )

  /**
   * Availability evidence for a component the dataset does NOT cover (facets, inferred filters).
   * `expectedValuesPresent` stays `false`: M19H adds no facet or inferred-filter expected values.
   */
  final case class AbsentComponentCoverage(
    componentId: BeautyResponseComponentId,
    schemaSupported: Boolean,
    datasetHasExpectations: Boolean,
    availability: ComponentDatasetAvailability,
    expectedValuesPresent: Boolean,
  )

  /**
   * Variant carousel coverage. The variant carousel is not part of the M19F component-expectation
   * schema; it remains covered by the existing candidate/variant expectations recorded in
   * M19C/M19D. `policyBlocked` is read straight from the M19D model so this stays source-true.
   */
  final case class VariantCarouselCoverage(
    componentId: BeautyResponseComponentId,
    coveredByExistingCandidateVariantExpectations: Boolean,
    policyBlocked: Boolean,
  )

  /**
   * Full M19H evidence. The two boolean summaries at the bottom are the crux: grouping metadata is
   * source-confirmed (`metadataSourceConfirmed = true`), but no component-level policy is approved
   * (`componentPolicyApproved = false`).
   */
  final case class ComponentCoverageEvidence(
    provider: GroupingFieldCoverage,
    serviceIntent: GroupingFieldCoverage,
    variant: VariantCarouselCoverage,
    facets: AbsentComponentCoverage,
    inferredFilters: AbsentComponentCoverage,
    remainingPolicyBlockers: List[PolicyBlockedDecision],
    metadataSourceConfirmed: Boolean,
    componentPolicyApproved: Boolean,
    offlineEvalOnly: Boolean,
    notServingPolicy: Boolean,
    doesNotApproveHybrid: Boolean,
    qdrantDoesNotOwnFacets: Boolean,
    qdrantDoesNotOwnInferredFilters: Boolean,
  )

  object ComponentCoverageEvidence {

    /**
     * Build the evidence from the populated M19F component-expectation queries and the M19D
     * coverage-gap model. Grouping-field values are derived strictly from the decoded queries; the
     * single confirmed value is exposed only when every expectation agrees on it.
     */
    def build(
      componentQueries: List[EvalQueryComponentExpectations],
      coverageGapModel: BeautyQCoverageGapModel,
    ): ComponentCoverageEvidence = {
      val coverage = ComponentExpectationCoverage.fromQueries(componentQueries)
      val total    = componentQueries.size

      val providerGroupings =
        componentQueries.flatMap(_.expectations.provider).flatMap(_.expectedGroupingField).distinct
      val serviceGroupings =
        componentQueries.flatMap(_.expectations.serviceIntent).flatMap(_.expectedGroupingField).distinct

      val provider = GroupingFieldCoverage(
        componentId = BeautyResponseComponentId.ProviderCarousel,
        schemaSupported = true,
        datasetHasExpectations = coverage.providerPresentInDataset,
        availability = availabilityOf(coverage.providerPresentInDataset),
        expectedGroupingField = singleOrNone(providerGroupings),
        groupingFieldConsistent = providerGroupings.sizeIs <= 1,
        expectationQueryCount = componentQueries.count(_.expectations.provider.isDefined),
        totalQueryCount = total,
      )

      val serviceIntent = GroupingFieldCoverage(
        componentId = BeautyResponseComponentId.ServiceIntentCarousel,
        schemaSupported = true,
        datasetHasExpectations = coverage.serviceIntentPresentInDataset,
        availability = availabilityOf(coverage.serviceIntentPresentInDataset),
        expectedGroupingField = singleOrNone(serviceGroupings),
        groupingFieldConsistent = serviceGroupings.sizeIs <= 1,
        expectationQueryCount = componentQueries.count(_.expectations.serviceIntent.isDefined),
        totalQueryCount = total,
      )

      val facets = AbsentComponentCoverage(
        componentId = BeautyResponseComponentId.Facets,
        schemaSupported = true,
        datasetHasExpectations = coverage.facetsPresentInDataset,
        availability = availabilityOf(coverage.facetsPresentInDataset),
        expectedValuesPresent = false,
      )

      val inferredFilters = AbsentComponentCoverage(
        componentId = BeautyResponseComponentId.InferredFilters,
        schemaSupported = true,
        datasetHasExpectations = coverage.inferredFiltersPresentInDataset,
        availability = availabilityOf(coverage.inferredFiltersPresentInDataset),
        expectedValuesPresent = false,
      )

      val variantPolicyBlocked =
        coverageGapModel.undercoveredComponents.contains(BeautyResponseComponentId.VariantCarousel)
      val variant = VariantCarouselCoverage(
        componentId = BeautyResponseComponentId.VariantCarousel,
        coveredByExistingCandidateVariantExpectations = !variantPolicyBlocked,
        policyBlocked = variantPolicyBlocked,
      )

      ComponentCoverageEvidence(
        provider = provider,
        serviceIntent = serviceIntent,
        variant = variant,
        facets = facets,
        inferredFilters = inferredFilters,
        remainingPolicyBlockers = coverageGapModel.policyBlockedDecisions,
        // Grouping metadata is now source-confirmed in the dataset...
        metadataSourceConfirmed =
          provider.expectedGroupingField.contains("masterLocationId") &&
            serviceIntent.expectedGroupingField.contains("serviceId"),
        // ...but that is metadata, NOT policy approval: no component-level policy is approved here.
        componentPolicyApproved = false,
        offlineEvalOnly = coverage.offlineEvalOnly && coverageGapModel.offlineEvalOnly,
        notServingPolicy = coverage.notServingPolicy && coverageGapModel.notServingPolicy,
        doesNotApproveHybrid = coverage.doesNotApproveHybrid && coverageGapModel.doesNotApproveHybrid,
        qdrantDoesNotOwnFacets = coverage.qdrantDoesNotOwnFacets && coverageGapModel.qdrantDoesNotOwnFacets,
        qdrantDoesNotOwnInferredFilters =
          coverage.qdrantDoesNotOwnInferredFilters && coverageGapModel.qdrantDoesNotOwnInferredFilters,
      )
    }

    private def availabilityOf(presentInDataset: Boolean): ComponentDatasetAvailability =
      if (presentInDataset) ComponentDatasetAvailability.SourceConfirmedInDataset
      else ComponentDatasetAvailability.SupportedButAbsentFromDataset

    private def singleOrNone(values: List[String]): Option[String] =
      values match {
        case single :: Nil => Some(single)
        case _             => None
      }
  }

  // --- Rendering --------------------------------------------------------------------------------

  /**
   * Render the evidence as a readable markdown report for review output. Pure string building; no
   * I/O. The report opens with the boundary banner, then renders per-component coverage, then the
   * remaining policy blockers, so a reviewer cannot mistake source-confirmed metadata for approval.
   */
  def renderMarkdown(evidence: ComponentCoverageEvidence): String = {
    val b = new StringBuilder

    line(b, "# M19H BeautyQ Component Coverage Evidence")
    line(b, "")
    line(
      b,
      "Offline/eval evidence only. This refreshes component expectation coverage after the M19G " +
        "grouping-field population. It performs no retrieval, no fusion, no reranking, no fallback, " +
        "does not call `/beauty-search`, and does not activate Qdrant. Source-confirmed metadata is " +
        "NOT policy approval: no component-level hybrid policy is chosen or approved here.",
    )

    line(b, "")
    line(b, "## Source-confirmed in the checked-in dataset")
    renderGrouping(b, evidence.provider)
    renderGrouping(b, evidence.serviceIntent)
    line(
      b,
      s"- variantCarousel: covered_by_existing_candidate_variant_expectations=" +
        s"${evidence.variant.coveredByExistingCandidateVariantExpectations}, policy_blocked=${evidence.variant.policyBlocked}",
    )

    line(b, "")
    line(b, "## Still a gap (absent / unavailable in the checked-in dataset)")
    renderAbsent(b, evidence.facets)
    renderAbsent(b, evidence.inferredFilters)

    line(b, "")
    line(b, "## Metadata vs policy")
    line(b, s"- grouping_metadata_source_confirmed: ${evidence.metadataSourceConfirmed}")
    line(b, s"- component_policy_approved: ${evidence.componentPolicyApproved}")
    line(b, s"- does_not_approve_hybrid: ${evidence.doesNotApproveHybrid}")
    line(b, s"- qdrant_does_not_own_facets: ${evidence.qdrantDoesNotOwnFacets}")
    line(b, s"- qdrant_does_not_own_inferred_filters: ${evidence.qdrantDoesNotOwnInferredFilters}")

    line(b, "")
    line(b, "## Remaining component-policy blockers (NeedsMoreEvidence)")
    evidence.remainingPolicyBlockers match {
      case Nil => line(b, "- (none)")
      case blockers =>
        blockers.foreach { d =>
          line(b, s"- [ ] ${d.componentId.render} / ${d.decisionId} (${d.state}): ${d.reason}")
        }
    }

    b.result()
  }

  private def renderGrouping(b: StringBuilder, c: GroupingFieldCoverage): Unit =
    line(
      b,
      s"- ${c.componentId.render}: schema_supported=${c.schemaSupported}, " +
        s"dataset_has_expectations=${c.datasetHasExpectations}, " +
        s"expected_grouping_field=${c.expectedGroupingField.getOrElse("-")}, " +
        s"grouping_field_consistent=${c.groupingFieldConsistent}, " +
        s"expectations=${c.expectationQueryCount}/${c.totalQueryCount}, availability=${c.availability}",
    )

  private def renderAbsent(b: StringBuilder, c: AbsentComponentCoverage): Unit =
    line(
      b,
      s"- ${c.componentId.render}: schema_supported=${c.schemaSupported}, " +
        s"dataset_has_expectations=${c.datasetHasExpectations}, " +
        s"expected_values_present=${c.expectedValuesPresent}, availability=${c.availability}",
    )

  private def line(b: StringBuilder, value: String): Unit = {
    b.append(value)
    b.append('\n')
  }
}
