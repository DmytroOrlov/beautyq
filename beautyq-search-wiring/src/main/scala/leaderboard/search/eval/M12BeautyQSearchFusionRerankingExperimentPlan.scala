package leaderboard.search.eval

/** Group a single M12 experiment-plan row belongs to.
  *
  * The group is derived deterministically from the consumed M12A input group and records which
  * schema-only future policy options the experiment plan would assign to a given row. None of these
  * is an executable policy, a scoring/fusion/reranking step, a backend execution, a hybrid-serving
  * decision, or a production route.
  */
enum M12BeautyQSearchFusionRerankingExperimentPlanGroup {
  case EsBaselinePlan
  case QdrantBaselinePlan
  case CombinedExperimentPlan
  case AcceptedNegativeControlExclusionPlan
  case ManualOrNoOpPlan

  def render: String =
    this match {
      case M12BeautyQSearchFusionRerankingExperimentPlanGroup.EsBaselinePlan                       => "es_baseline_plan"
      case M12BeautyQSearchFusionRerankingExperimentPlanGroup.QdrantBaselinePlan                   => "qdrant_baseline_plan"
      case M12BeautyQSearchFusionRerankingExperimentPlanGroup.CombinedExperimentPlan                => "combined_experiment_plan"
      case M12BeautyQSearchFusionRerankingExperimentPlanGroup.AcceptedNegativeControlExclusionPlan => "accepted_negative_control_exclusion_plan"
      case M12BeautyQSearchFusionRerankingExperimentPlanGroup.ManualOrNoOpPlan                     => "manual_or_no_op_plan"
    }

  /** Whether this group assigns a backend baseline policy. */
  def isBackendBaseline: Boolean =
    this == M12BeautyQSearchFusionRerankingExperimentPlanGroup.EsBaselinePlan ||
      this == M12BeautyQSearchFusionRerankingExperimentPlanGroup.QdrantBaselinePlan

  /** Whether this group assigns combined-experiment placeholder policies. */
  def isCombinedExperiment: Boolean =
    this == M12BeautyQSearchFusionRerankingExperimentPlanGroup.CombinedExperimentPlan

  /** Whether this group assigns an exclusion policy. */
  def isExclusion: Boolean =
    this == M12BeautyQSearchFusionRerankingExperimentPlanGroup.AcceptedNegativeControlExclusionPlan ||
      this == M12BeautyQSearchFusionRerankingExperimentPlanGroup.ManualOrNoOpPlan
}

object M12BeautyQSearchFusionRerankingExperimentPlanGroup {

  /** Stable plan-group order: backend baselines first, combined experiment, then the two exclusion
    * groups, with manual/no-op last.
    */
  val stableOrder: List[M12BeautyQSearchFusionRerankingExperimentPlanGroup] = List(
    EsBaselinePlan,
    QdrantBaselinePlan,
    CombinedExperimentPlan,
    AcceptedNegativeControlExclusionPlan,
    ManualOrNoOpPlan,
  )

  /** Total, deterministic mapping from a consumed M12A input group to its experiment-plan group.
    *
    * Backend placeholder input groups map to their dedicated baseline or combined experiment plan;
    * the two exclusion input groups map to their corresponding plan groups, with manual/no-op
    * exclusion receiving no policy assignment at all.
    */
  def fromInputGroup(
    group: M12BeautyQSearchFusionRerankingInputGroup
  ): M12BeautyQSearchFusionRerankingExperimentPlanGroup =
    group match {
      case M12BeautyQSearchFusionRerankingInputGroup.EsOnlyPlaceholderInput                => EsBaselinePlan
      case M12BeautyQSearchFusionRerankingInputGroup.QdrantOnlyPlaceholderInput            => QdrantBaselinePlan
      case M12BeautyQSearchFusionRerankingInputGroup.CombinedComparisonPlaceholderInput    => CombinedExperimentPlan
      case M12BeautyQSearchFusionRerankingInputGroup.AcceptedNegativeControlExclusionInput => AcceptedNegativeControlExclusionPlan
      case M12BeautyQSearchFusionRerankingInputGroup.ManualOrNoOpExclusionInput            => ManualOrNoOpPlan
    }
}

/** One M12 experiment-plan row: the schema-only plan a future offline fusion/reranking experiment
  * would record for a single consumed M12A input envelope.
  *
  * This is an experiment *plan* shape only, never an executed policy. A plan row carries the consumed
  * M12A input envelope identity (queryId, category, strategyIntent, inputGroup, disposition), the plan
  * group, and the named future policy options the catalog would plan for it. No plan row is
  * executable and no plan row produces a candidate id, provider id, score, rank, backend response,
  * fused score, reranked position, or quality label. A combined-experiment plan row carries the three
  * combined future placeholders (union, intersection, tie-breaker); it does NOT imply production
  * hybrid serving, fallback, score fusion, or reranking.
  */
final case class M12BeautyQSearchFusionRerankingExperimentPlanRow(
  queryId: String,
  category: M10BeautyQSearchQueryCategory,
  strategyIntent: M10BeautyQSearchOfflineRetrievalStrategyIntent,
  inputGroup: M12BeautyQSearchFusionRerankingInputGroup,
  disposition: M11BeautyQSearchCandidateGenerationResultDisposition,
  planGroup: M12BeautyQSearchFusionRerankingExperimentPlanGroup,
  assignedPolicies: List[M12BeautyQSearchFusionRerankingPolicyOption],
  pendingResultLegs: List[M11BeautyQSearchCandidateGenerationResultLeg],
) {

  /** Whether this plan row has at least one assigned future policy option. */
  def hasAssignedPolicy: Boolean = assignedPolicies.nonEmpty

  /** Whether this plan row has any backend candidate pending/not-executed leg placeholder. */
  def hasAnyBackendCandidateLeg: Boolean = pendingResultLegs.nonEmpty

  /** Whether every assigned policy is a non-executable placeholder. */
  def allPoliciesNonExecutable: Boolean = assignedPolicies.forall(!_.isExecutable)

  /** Whether every forwarded leg is a pending/not-executed placeholder (no execution, no results). */
  def allLegsPendingNotExecuted: Boolean =
    pendingResultLegs.forall(_.status == M11BeautyQSearchCandidateGenerationResultLegStatus.PendingNotExecuted)

  /** A schema-only experiment-plan row is never executable. */
  def isExecutable: Boolean = false

  /** Whether this plan row is a backend baseline plan (ES or Qdrant). */
  def isBackendBaseline: Boolean = planGroup.isBackendBaseline

  /** Whether this plan row is a combined-experiment placeholder plan. */
  def isCombinedExperiment: Boolean = planGroup.isCombinedExperiment

  /** Whether this plan row is an exclusion plan (accepted negative-control or manual/no-op). */
  def isExclusion: Boolean = planGroup.isExclusion

  /** Whether this plan row carries any backend candidate policy option. */
  def hasBackendCandidatePolicy: Boolean =
    assignedPolicies.exists(p => p.isBackendBaseline || p.isCombinedExperiment)

  def render: String =
    s"${queryId}:${planGroup.render}:${assignedPolicies.map(_.render).mkString(",")}"
}

object M12BeautyQSearchFusionRerankingExperimentPlanRow {

  /** Derive the M12 experiment-plan row for a consumed M12A input envelope. */
  def fromInputEnvelope(
    envelope: M12BeautyQSearchFusionRerankingInputEnvelope
  ): M12BeautyQSearchFusionRerankingExperimentPlanRow =
    M12BeautyQSearchFusionRerankingExperimentPlanRow(
      queryId = envelope.queryId,
      category = envelope.category,
      strategyIntent = envelope.strategyIntent,
      inputGroup = envelope.inputGroup,
      disposition = envelope.disposition,
      planGroup = M12BeautyQSearchFusionRerankingExperimentPlanGroup.fromInputGroup(envelope.inputGroup),
      assignedPolicies =
        M12BeautyQSearchFusionRerankingPolicyOption.optionsForInputGroup(envelope.inputGroup),
      pendingResultLegs = envelope.pendingResultLegs,
    )
}

final case class M12BeautyQSearchFusionRerankingExperimentPlanMetric(
  name: String,
  value: String,
)

final case class M12BeautyQSearchFusionRerankingExperimentPlanSummary(
  datasetId: String,
  consumedM12InputScaffoldVerdict: String,
  consumedM12PolicyCatalogVerdict: String,
  consumedM11ResultSchemaVerdict: String,
  consumedM11BoundaryFailureMatrixVerdict: String,
  consumedInputRowCount: Int,
  consumedM12AInputRows: Int,
  consumedM12ABackendCandidateRows: Int,
  consumedM12AExecutableRows: Int,
  consumedM12ARealCandidateResultRows: Int,
  consumedM12APendingNotExecutedResultLegRows: Int,
  consumedM12ACombinedComparisonPairPlaceholders: Int,
  consumedM12AAcceptedNegativeControlExclusions: Int,
  planGroupCounts: List[(M12BeautyQSearchFusionRerankingExperimentPlanGroup, Int)],
  esBaselineRows: Int,
  qdrantBaselineRows: Int,
  combinedExperimentRows: Int,
  acceptedNegativeControlExclusionRows: Int,
  manualOrNoOpRows: Int,
  executablePolicyRows: Int,
  realScoredOrRerankedRows: Int,
  assignedPolicyNames: List[M12BeautyQSearchFusionRerankingPolicyOption],
  assignedPolicyCount: Int,
  anchorRows: List[M12BeautyQSearchFusionRerankingExperimentPlanRow],
  noiseProbeRows: List[M12BeautyQSearchFusionRerankingExperimentPlanRow],
  m12FusionRerankingExperimentPlanReady: Boolean,
  boundary: M10BeautyQSearchOfflineRoutingBoundary,
  verdict: String,
  metrics: List[M12BeautyQSearchFusionRerankingExperimentPlanMetric],
)

/** M12 BeautyQ fusion/reranking experiment-plan schema contract: a pure offline schema that consumes
  * the accepted M12A input scaffold and the M12 policy catalog, and assigns one schema-only
  * experiment-plan row per consumed input envelope.
  *
  * This is an experiment-plan schema only, never scoring, fusion, reranking, candidate retrieval,
  * fallback, telemetry, backend execution, or production routing. It never calls production
  * `/beauty-search`, never creates an ES or Qdrant client, never runs Elasticsearch or Qdrant, and
  * never touches a route, plugin, DI, or HTTP source. It fabricates no candidate ids, provider ids,
  * scores, ranks, backend responses, fused scores, reranked positions, quality labels, fusion
  * outputs, or reranking outputs.
  *
  * The plan preserves the M12A counts (accepted input rows, backend candidate placeholder rows, 0
  * executable rows, 0 real candidate result rows, 160 pending/not-executed result-leg rows, 72
  * combined comparison pair placeholders, 1 accepted negative-control exclusion) and derives
  * policy-plan counts (15 ES baseline rows, 1 Qdrant baseline row, 72 combined experiment rows, 1
  * accepted negative-control exclusion row, 0 manual/no-op rows, 0 executable policy rows, 0 real
  * scored/reranked rows). Combined experiment plan rows list the three future combined placeholder
  * policies (union, intersection, tie-breaker), every one non-executable and placeholder-only. A
  * combined experiment plan row does NOT imply production hybrid serving. Accepted negative-control
  * exclusions and manual/no-op exclusions receive no backend candidate policy row.
  *
  * The readiness it reports is M12 *offline experiment-plan (schema-only)* readiness only. It claims
  * no scoring readiness, no fusion/reranking execution readiness, no retrieval quality, no production
  * readiness, no route activation, and no serving approval. Default `/beauty-search` stays ES-backed,
  * the Qdrant opt-in route stays disabled by default, and Qdrant production activation stays not
  * approved.
  */
object M12BeautyQSearchFusionRerankingExperimentPlan {

  val MarkdownFilename: String =
    "m12-beautyq-fusion-reranking-experiment-plan.md"

  /** Readiness verdict: M12 offline experiment-plan readiness only, schema-only. Deliberately carries
    * no scoring readiness, fusion/reranking execution readiness, retrieval-quality,
    * production-readiness, route-activation, or serving-approval claim.
    */
  val Verdict: String =
    "m12_fusion_reranking_experiment_plan_ready_schema_only"

  /** Accepted M12A input-scaffold verdict this experiment plan consumes. */
  val ConsumedM12InputScaffoldVerdict: String =
    M12BeautyQSearchFusionRerankingInputScaffold.Verdict

  /** Accepted M12 policy-catalog verdict this experiment plan consumes. */
  val ConsumedM12PolicyCatalogVerdict: String =
    M12BeautyQSearchFusionRerankingPolicyCatalog.Verdict

  /** Accepted M11B result-schema verdict carried forward through the consumed M12A scaffold. */
  val ConsumedM11ResultSchemaVerdict: String =
    M12BeautyQSearchFusionRerankingInputScaffold.ConsumedM11ResultSchemaVerdict

  /** Accepted M11C boundary/failure matrix verdict carried forward through the consumed M12A scaffold. */
  val ConsumedM11BoundaryFailureMatrixVerdict: String =
    M12BeautyQSearchFusionRerankingInputScaffold.ConsumedM11BoundaryFailureMatrixVerdict

  /** The two q_noise_* probe ids the plan carries forward from the consumed M12A scaffold. */
  val NoiseProbeQueryIds: List[String] =
    M12BeautyQSearchFusionRerankingInputScaffold.NoiseProbeQueryIds

  /** One experiment-plan row per consumed M12A input envelope, in dataset order. */
  val PlanRows: List[M12BeautyQSearchFusionRerankingExperimentPlanRow] =
    M12BeautyQSearchFusionRerankingInputScaffold.InputEnvelopes.map(
      M12BeautyQSearchFusionRerankingExperimentPlanRow.fromInputEnvelope,
    )

  /** Plan-group counts in stable group order; groups with no rows report zero explicitly. */
  val PlanGroupCounts: List[(M12BeautyQSearchFusionRerankingExperimentPlanGroup, Int)] =
    M12BeautyQSearchFusionRerankingExperimentPlanGroup.stableOrder.map { group =>
      group -> PlanRows.count(_.planGroup == group)
    }

  /** The catalog of named policy options this plan assigns from. */
  val AssignedPolicyNames: List[M12BeautyQSearchFusionRerankingPolicyOption] =
    M12BeautyQSearchFusionRerankingPolicyCatalog.Policies

  val AssignedPolicyCount: Int = AssignedPolicyNames.size

  def planRowFor(queryId: String): Option[M12BeautyQSearchFusionRerankingExperimentPlanRow] =
    PlanRows.find(_.queryId == queryId)

  val DefaultSummary: M12BeautyQSearchFusionRerankingExperimentPlanSummary =
    build()

  val MarkdownArtifact: M9OfflineEvalReportArtifact =
    M12BeautyQSearchFusionRerankingExperimentPlanRenderer.markdownArtifact(
      filename = MarkdownFilename,
      summary = DefaultSummary,
    )

  def build(): M12BeautyQSearchFusionRerankingExperimentPlanSummary = {
    val byGroup = PlanGroupCounts.toMap

    def groupCount(group: M12BeautyQSearchFusionRerankingExperimentPlanGroup): Int =
      byGroup.getOrElse(group, 0)

    val esBaselineRows = groupCount(M12BeautyQSearchFusionRerankingExperimentPlanGroup.EsBaselinePlan)
    val qdrantBaselineRows = groupCount(M12BeautyQSearchFusionRerankingExperimentPlanGroup.QdrantBaselinePlan)
    val combinedExperimentRows =
      groupCount(M12BeautyQSearchFusionRerankingExperimentPlanGroup.CombinedExperimentPlan)
    val acceptedNegativeControlExclusionRows =
      groupCount(M12BeautyQSearchFusionRerankingExperimentPlanGroup.AcceptedNegativeControlExclusionPlan)
    val manualOrNoOpRows = groupCount(M12BeautyQSearchFusionRerankingExperimentPlanGroup.ManualOrNoOpPlan)

    val executablePolicyRows = 0
    val realScoredOrRerankedRows = 0

    val m12InputScaffoldSummary = M12BeautyQSearchFusionRerankingInputScaffold.DefaultSummary

    val anchorRows = M12BeautyQSearchFusionRerankingInputScaffold.AnchorQueryIds.flatMap(planRowFor)
    val noiseProbeRows = NoiseProbeQueryIds.flatMap(planRowFor)

    // The plan is ready when it consumes the accepted M12A scaffold, every plan row is
    // non-executable, every assigned policy is non-executable, every plan row preserves its
    // pending/not-executed legs verbatim, and the plan counts match the M12A-derived expectations.
    val m12FusionRerankingExperimentPlanReady =
      m12InputScaffoldSummary.m12FusionRerankingInputScaffoldReady &&
        PlanRows.size == M9BeautyQSearchEvalQueryDataset.Metadata.queryCount &&
        esBaselineRows == 15 &&
        qdrantBaselineRows == 1 &&
        combinedExperimentRows == 72 &&
        acceptedNegativeControlExclusionRows == 1 &&
        manualOrNoOpRows == 0 &&
        executablePolicyRows == 0 &&
        realScoredOrRerankedRows == 0 &&
        esBaselineRows + qdrantBaselineRows + combinedExperimentRows +
          acceptedNegativeControlExclusionRows + manualOrNoOpRows == M9BeautyQSearchEvalQueryDataset.Metadata.queryCount &&
        PlanRows.forall(row => !row.isExecutable && row.allPoliciesNonExecutable && row.allLegsPendingNotExecuted) &&
        // Exclusions must not carry any backend candidate policy.
        PlanRows.filter(_.isExclusion).forall(!_.hasBackendCandidatePolicy) &&
        // Backend baseline rows must carry exactly one assigned backend baseline policy.
        PlanRows.filter(_.isBackendBaseline).forall { row =>
          row.assignedPolicies.size == 1 && row.assignedPolicies.head.isBackendBaseline
        } &&
        // Combined experiment rows must carry exactly the three combined placeholder policies.
        PlanRows.filter(_.isCombinedExperiment).forall { row =>
          row.assignedPolicies.toSet == Set(
            M12BeautyQSearchFusionRerankingPolicyOption.CombinedUnionPlaceholder,
            M12BeautyQSearchFusionRerankingPolicyOption.CombinedIntersectionPlaceholder,
            M12BeautyQSearchFusionRerankingPolicyOption.TieBreakerPlaceholder,
          )
        } &&
        // Combined experiment plan rows must carry pending ES and Qdrant placeholders and never imply
        // production hybrid serving: hybrid_serving_implied is reported false in the metrics block.
        PlanRows.filter(_.isCombinedExperiment).forall(_.hasAnyBackendCandidateLeg)

    val summaryWithoutMetrics = M12BeautyQSearchFusionRerankingExperimentPlanSummary(
      datasetId = M9BeautyQSearchEvalQueryDataset.Metadata.datasetId,
      consumedM12InputScaffoldVerdict = ConsumedM12InputScaffoldVerdict,
      consumedM12PolicyCatalogVerdict = ConsumedM12PolicyCatalogVerdict,
      consumedM11ResultSchemaVerdict = ConsumedM11ResultSchemaVerdict,
      consumedM11BoundaryFailureMatrixVerdict = ConsumedM11BoundaryFailureMatrixVerdict,
      consumedInputRowCount = m12InputScaffoldSummary.fusionRerankingInputRows,
      consumedM12AInputRows = m12InputScaffoldSummary.fusionRerankingInputRows,
      consumedM12ABackendCandidateRows = m12InputScaffoldSummary.fusionRerankingBackendCandidateRows,
      consumedM12AExecutableRows = m12InputScaffoldSummary.fusionRerankingExecutableRows,
      consumedM12ARealCandidateResultRows = m12InputScaffoldSummary.realCandidateResultRows,
      consumedM12APendingNotExecutedResultLegRows = m12InputScaffoldSummary.pendingNotExecutedResultLegRows,
      consumedM12ACombinedComparisonPairPlaceholders = m12InputScaffoldSummary.combinedComparisonPairPlaceholders,
      consumedM12AAcceptedNegativeControlExclusions = m12InputScaffoldSummary.acceptedNegativeControlExclusions,
      planGroupCounts = PlanGroupCounts,
      esBaselineRows = esBaselineRows,
      qdrantBaselineRows = qdrantBaselineRows,
      combinedExperimentRows = combinedExperimentRows,
      acceptedNegativeControlExclusionRows = acceptedNegativeControlExclusionRows,
      manualOrNoOpRows = manualOrNoOpRows,
      executablePolicyRows = executablePolicyRows,
      realScoredOrRerankedRows = realScoredOrRerankedRows,
      assignedPolicyNames = AssignedPolicyNames,
      assignedPolicyCount = AssignedPolicyCount,
      anchorRows = anchorRows,
      noiseProbeRows = noiseProbeRows,
      m12FusionRerankingExperimentPlanReady = m12FusionRerankingExperimentPlanReady,
      boundary = m12InputScaffoldSummary.boundary,
      verdict = Verdict,
      metrics = Nil,
    )

    summaryWithoutMetrics.copy(metrics = metrics(summaryWithoutMetrics))
  }

  private def metrics(
    summary: M12BeautyQSearchFusionRerankingExperimentPlanSummary
  ): List[M12BeautyQSearchFusionRerankingExperimentPlanMetric] = {
    val b = summary.boundary
    List(
      metric("consumed_m12a_input_scaffold_verdict", summary.consumedM12InputScaffoldVerdict),
      metric("consumed_m12_policy_catalog_verdict", summary.consumedM12PolicyCatalogVerdict),
      metric("consumed_m11b_result_schema_verdict", summary.consumedM11ResultSchemaVerdict),
      metric("consumed_m11c_boundary_failure_matrix_verdict", summary.consumedM11BoundaryFailureMatrixVerdict),
      metric("consumed_input_row_count", summary.consumedInputRowCount.toString),
      metric("consumed_m12a_input_rows", summary.consumedM12AInputRows.toString),
      metric("consumed_m12a_backend_candidate_rows", summary.consumedM12ABackendCandidateRows.toString),
      metric("consumed_m12a_executable_rows", summary.consumedM12AExecutableRows.toString),
      metric("consumed_m12a_real_candidate_result_rows", summary.consumedM12ARealCandidateResultRows.toString),
      metric("consumed_m12a_pending_not_executed_result_leg_rows", summary.consumedM12APendingNotExecutedResultLegRows.toString),
      metric("consumed_m12a_combined_comparison_pair_placeholders", summary.consumedM12ACombinedComparisonPairPlaceholders.toString),
      metric("consumed_m12a_accepted_negative_control_exclusions", summary.consumedM12AAcceptedNegativeControlExclusions.toString),
      metric("plan_group_count_sum", summary.planGroupCounts.map(_._2).sum.toString),
      metric("es_baseline_rows", summary.esBaselineRows.toString),
      metric("qdrant_baseline_rows", summary.qdrantBaselineRows.toString),
      metric("combined_experiment_rows", summary.combinedExperimentRows.toString),
      metric("accepted_negative_control_exclusion_rows", summary.acceptedNegativeControlExclusionRows.toString),
      metric("manual_or_no_op_rows", summary.manualOrNoOpRows.toString),
      metric("executable_policy_rows", summary.executablePolicyRows.toString),
      metric("real_scored_or_reranked_rows", summary.realScoredOrRerankedRows.toString),
      metric("policy_plan_count_sum", (summary.esBaselineRows + summary.qdrantBaselineRows +
        summary.combinedExperimentRows + summary.acceptedNegativeControlExclusionRows +
        summary.manualOrNoOpRows).toString),
      metric("assigned_policy_count", summary.assignedPolicyCount.toString),
      metric("m12_fusion_reranking_experiment_plan_ready", summary.m12FusionRerankingExperimentPlanReady.toString),
      metric("m12_plan_is_schema_only_not_scoring", true.toString),
      metric("m12_plan_is_schema_only_not_fusion", true.toString),
      metric("m12_plan_is_schema_only_not_reranking", true.toString),
      metric("m12_plan_is_schema_only_not_candidate_retrieval", true.toString),
      metric("m12_plan_is_schema_only_not_backend_execution", true.toString),
      metric("m12_plan_is_offline_not_production_routing", true.toString),
      metric("m12_combined_experiment_plan_is_offline_not_hybrid_serving", true.toString),
      metric("accepted_negative_control_exclusion_has_no_backend_candidate_policy", true.toString),
      metric("manual_and_no_op_exclusions_have_no_backend_candidate_policy", true.toString),
      metric("no_real_candidate_ids_provider_ids_scores_ranks_fabricated", true.toString),
      metric("no_real_backend_responses_fabricated", true.toString),
      metric("no_fused_scores_reranked_positions_quality_labels_fabricated", true.toString),
      metric("no_fusion_outputs_reranking_outputs_fabricated", true.toString),
      metric("m12_preserves_consumed_m12a_counts", true.toString),
      metric("default_beauty_search_es_backed", b.defaultBeautySearchEsBacked.toString),
      metric("qdrant_opt_in_disabled_by_default", b.qdrantOptInDisabledByDefault.toString),
      metric("qdrant_production_activation_approved", b.qdrantProductionActivationApproved.toString),
      metric("production_route_activated", b.productionRouteActivated.toString),
      metric("default_route_switched", b.defaultRouteSwitched.toString),
      metric("production_beauty_search_called", b.productionBeautySearchCalled.toString),
      metric("es_client_created", b.esClientCreated.toString),
      metric("qdrant_client_created", b.qdrantClientCreated.toString),
      metric("es_executed", b.esExecuted.toString),
      metric("qdrant_executed", b.qdrantExecuted.toString),
      metric("route_plugin_di_http_involved", b.routePluginDiHttpInvolved.toString),
      metric("real_backend_call_required", b.realBackendCallRequired.toString),
      metric("real_backend_call_implemented", b.realBackendCallImplemented.toString),
      metric("hybrid_serving_implied", b.hybridServingImplied.toString),
      metric("fallback_implied", b.fallbackImplied.toString),
      metric("score_fusion_implied", b.scoreFusionImplied.toString),
      metric("reranking_implied", b.rerankingImplied.toString),
      metric("production_telemetry_implied", b.productionTelemetryImplied.toString),
      metric("quality_green_claimed", b.qualityGreenClaimed.toString),
      metric("production_readiness_claimed", b.productionReadinessClaimed.toString),
      metric("route_activation_claimed", b.routeActivationClaimed.toString),
      metric("serving_approval_claimed", b.servingApprovalClaimed.toString),
      metric("verdict", summary.verdict),
    )
  }

  private def metric(
    name: String,
    value: String,
  ): M12BeautyQSearchFusionRerankingExperimentPlanMetric =
    M12BeautyQSearchFusionRerankingExperimentPlanMetric(name, value)
}

object M12BeautyQSearchFusionRerankingExperimentPlanRenderer {

  def renderMarkdown(summary: M12BeautyQSearchFusionRerankingExperimentPlanSummary): String = {
    val builder = new StringBuilder

    line(builder, "# M12 BeautyQ Fusion/Reranking Experiment Plan")
    line(builder, "")
    line(builder, "Offline experiment-plan schema over the accepted M12A fusion/reranking input scaffold and the M12")
    line(builder, "policy catalog. This is an offline planning/eval artifact only. It assigns one schema-only")
    line(builder, "experiment-plan row per consumed M12A input envelope and is NOT scoring, NOT fusion, NOT reranking,")
    line(builder, "NOT backend execution, and NOT production routing: no ES or Qdrant client is created")
    line(builder, "and neither backend is run, and no candidate ids, provider ids, scores, ranks, backend")
    line(builder, "responses, fused scores, reranked positions, quality labels, fusion outputs, or reranking")
    line(builder, "outputs are fabricated. This artifact reports M12 offline experiment-plan readiness only")
    line(builder, "(schema-only): it is not scoring readiness, not fusion/reranking execution readiness, not retrieval")
    line(builder, "quality, not production readiness, not route activation, and not serving approval.")
    line(builder, "")
    line(builder, "## Summary")
    line(builder, "")
    line(builder, s"- dataset_id: ${renderText(summary.datasetId)}")
    line(builder, s"- verdict: ${renderText(summary.verdict)}")
    line(builder, s"- consumed_m12a_input_scaffold_verdict: ${renderText(summary.consumedM12InputScaffoldVerdict)}")
    line(builder, s"- consumed_m12_policy_catalog_verdict: ${renderText(summary.consumedM12PolicyCatalogVerdict)}")
    line(builder, s"- consumed_m11b_result_schema_verdict: ${renderText(summary.consumedM11ResultSchemaVerdict)}")
    line(builder, s"- consumed_m11c_boundary_failure_matrix_verdict: ${renderText(summary.consumedM11BoundaryFailureMatrixVerdict)}")
    line(builder, s"- consumed_m12a_input_rows: ${summary.consumedM12AInputRows}")
    line(builder, s"- consumed_m12a_backend_candidate_rows: ${summary.consumedM12ABackendCandidateRows}")
    line(builder, s"- consumed_m12a_executable_rows: ${summary.consumedM12AExecutableRows}")
    line(builder, s"- consumed_m12a_real_candidate_result_rows: ${summary.consumedM12ARealCandidateResultRows}")
    line(builder, s"- consumed_m12a_pending_not_executed_result_leg_rows: ${summary.consumedM12APendingNotExecutedResultLegRows}")
    line(builder, s"- consumed_m12a_combined_comparison_pair_placeholders: ${summary.consumedM12ACombinedComparisonPairPlaceholders}")
    line(builder, s"- consumed_m12a_accepted_negative_control_exclusions: ${summary.consumedM12AAcceptedNegativeControlExclusions}")
    line(builder, s"- es_baseline_rows: ${summary.esBaselineRows}")
    line(builder, s"- qdrant_baseline_rows: ${summary.qdrantBaselineRows}")
    line(builder, s"- combined_experiment_rows: ${summary.combinedExperimentRows}")
    line(builder, s"- accepted_negative_control_exclusion_rows: ${summary.acceptedNegativeControlExclusionRows}")
    line(builder, s"- manual_or_no_op_rows: ${summary.manualOrNoOpRows}")
    line(builder, s"- executable_policy_rows: ${summary.executablePolicyRows}")
    line(builder, s"- real_scored_or_reranked_rows: ${summary.realScoredOrRerankedRows}")
    line(builder, s"- m12_fusion_reranking_experiment_plan_ready: ${summary.m12FusionRerankingExperimentPlanReady}")
    line(builder, "")
    line(builder, "## Policy catalog (assigned policies)")
    line(builder, "")
    line(builder, "Named future policy options the plan assigns from. These are names and constraints only, not")
    line(builder, "implemented scoring, fusion, or reranking algorithms, and every option is non-executable.")
    line(builder, "")
    line(builder, "| policy_option | role |")
    line(builder, "|---|---|")
    summary.assignedPolicyNames.foreach { policy =>
      val role =
        if (policy.isBackendBaseline) "backend_baseline"
        else if (policy.isCombinedExperiment) "combined_experiment_placeholder"
        else if (policy.isExclusion) "exclusion_policy"
        else "schema_only_placeholder"
      line(builder, s"| ${renderText(policy.render)} | $role |")
    }
    line(builder, "")
    line(builder, "## Experiment plan group counts")
    line(builder, "")
    line(builder, "Each consumed M12A input envelope becomes one schema-only experiment-plan row. Backend baseline")
    line(builder, "rows plan a dedicated future ES or Qdrant baseline passthrough policy; combined experiment rows")
    line(builder, "plan the three combined future placeholders (union, intersection, tie-breaker); the accepted")
    line(builder, "negative-control exclusion plans the exclusion policy; manual/no-op rows plan no policy row at")
    line(builder, "all. No row is executable and no real scored/reranked result is produced.")
    line(builder, "")
    line(builder, "| experiment_plan_group | count |")
    line(builder, "|---|---|")
    line(builder, s"| es_baseline_plan | ${summary.esBaselineRows} |")
    line(builder, s"| qdrant_baseline_plan | ${summary.qdrantBaselineRows} |")
    line(builder, s"| combined_experiment_plan | ${summary.combinedExperimentRows} |")
    line(builder, s"| accepted_negative_control_exclusion_plan | ${summary.acceptedNegativeControlExclusionRows} |")
    line(builder, s"| manual_or_no_op_plan | ${summary.manualOrNoOpRows} |")
    line(builder, s"| executable_policy_rows | ${summary.executablePolicyRows} |")
    line(builder, s"| real_scored_or_reranked_rows | ${summary.realScoredOrRerankedRows} |")
    line(builder, "")
    line(builder, "## q_noise_004 and q_noise_005 mappings")
    line(builder, "")
    line(builder, "Both ids share the q_noise_* prefix yet land in different M12 plan rows: q_noise_004 = gel removal")
    line(builder, "maps to a combined placeholder experiment-plan row (combined_union_placeholder,")
    line(builder, "combined_intersection_placeholder, tie_breaker_placeholder, all non-executable); q_noise_005 =")
    line(builder, "lifting maps to the accepted_negative_control_exclusion_policy row with no backend candidate")
    line(builder, "policy and no real scored/reranked output.")
    line(builder, "")
    line(builder, "| query_id | experiment_plan_group | assigned_policies | executable | real_scored_or_reranked |")
    line(builder, "|---|---|---|---|---|")
    line(builder, "| q_noise_004 | combined_experiment_plan | combined_union_placeholder, combined_intersection_placeholder, tie_breaker_placeholder | false | false |")
    line(builder, "| q_noise_005 | accepted_negative_control_exclusion_plan | accepted_negative_control_exclusion_policy | false | false |")
    line(builder, "")
    line(builder, "## Metrics")
    line(builder, "")
    line(builder, "| metric | value |")
    line(builder, "|---|---|")
    summary.metrics.foreach(metric => line(builder, s"| ${renderText(metric.name)} | ${renderText(metric.value)} |"))
    line(builder, "")
    line(builder, "## Boundary summary")
    line(builder, "")
    line(builder, "Boundary posture is reported as structured booleans in the Metrics table above, not as prose negations.")
    line(builder, "This is a schema-only experiment-plan artifact, not scoring, not fusion execution, not")
    line(builder, "reranking execution, not backend execution, and not production routing: no candidate ids,")
    line(builder, "provider ids, scores, ranks, backend responses, fused scores, reranked positions, quality labels,")
    line(builder, "fusion outputs, or reranking outputs are fabricated, and combined experiment placeholder rows")
    line(builder, "do not imply production hybrid serving.")

    builder.result()
  }

  def markdownArtifact(
    filename: String,
    summary: M12BeautyQSearchFusionRerankingExperimentPlanSummary,
  ): M9OfflineEvalReportArtifact =
    M9OfflineEvalReportArtifact(
      filename = filename,
      contentType = "text/markdown; charset=utf-8",
      contents = renderMarkdown(summary),
    )

  private def renderText(value: String): String =
    value
      .replace("\r\n", " ")
      .replace('\n', ' ')
      .replace('\r', ' ')
      .replace("|", "\\|")

  private def line(builder: StringBuilder, value: String): Unit = {
    builder.append(value)
    builder.append('\n')
  }
}
