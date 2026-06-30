package leaderboard.search.eval

/** Group a single M12 fusion/reranking experiment input envelope belongs to.
  *
  * These are offline experiment input groups only, derived 1:1 from the accepted M11B result-row
  * dispositions. A backend placeholder group (ES-only, Qdrant-only, combined comparison) carries pending,
  * not-executed result-leg placeholders forwarded verbatim from M11B; the two exclusion groups (accepted
  * negative control, manual/no-op) carry no input legs at all. None of these is a production route, a
  * backend execution, a scoring/fusion/reranking step, or a hybrid-serving decision.
  */
enum M12BeautyQSearchFusionRerankingInputGroup {
  case EsOnlyPlaceholderInput
  case QdrantOnlyPlaceholderInput
  case CombinedComparisonPlaceholderInput
  case AcceptedNegativeControlExclusionInput
  case ManualOrNoOpExclusionInput

  def render: String =
    this match {
      case M12BeautyQSearchFusionRerankingInputGroup.EsOnlyPlaceholderInput                => "es_only_placeholder_input"
      case M12BeautyQSearchFusionRerankingInputGroup.QdrantOnlyPlaceholderInput            => "qdrant_only_placeholder_input"
      case M12BeautyQSearchFusionRerankingInputGroup.CombinedComparisonPlaceholderInput    => "combined_comparison_placeholder_input"
      case M12BeautyQSearchFusionRerankingInputGroup.AcceptedNegativeControlExclusionInput => "accepted_negative_control_exclusion_input"
      case M12BeautyQSearchFusionRerankingInputGroup.ManualOrNoOpExclusionInput            => "manual_or_no_op_exclusion_input"
    }

  /** Whether this input group forwards pending/not-executed backend candidate result legs. */
  def hasBackendCandidateLegs: Boolean =
    this match {
      case M12BeautyQSearchFusionRerankingInputGroup.EsOnlyPlaceholderInput                => true
      case M12BeautyQSearchFusionRerankingInputGroup.QdrantOnlyPlaceholderInput            => true
      case M12BeautyQSearchFusionRerankingInputGroup.CombinedComparisonPlaceholderInput    => true
      case M12BeautyQSearchFusionRerankingInputGroup.AcceptedNegativeControlExclusionInput => false
      case M12BeautyQSearchFusionRerankingInputGroup.ManualOrNoOpExclusionInput            => false
    }
}

object M12BeautyQSearchFusionRerankingInputGroup {

  /** Stable group order: the three backend placeholder input groups first, then the two exclusion groups. */
  val stableOrder: List[M12BeautyQSearchFusionRerankingInputGroup] = List(
    EsOnlyPlaceholderInput,
    QdrantOnlyPlaceholderInput,
    CombinedComparisonPlaceholderInput,
    AcceptedNegativeControlExclusionInput,
    ManualOrNoOpExclusionInput,
  )

  /** Total, deterministic mapping from an accepted M11B result disposition to its M12 input group.
    *
    * The two M11 exclusion/skip dispositions (manual review, no-op noise) collapse into a single
    * manual/no-op exclusion input group; neither forwards a backend candidate leg.
    */
  def fromDisposition(
    disposition: M11BeautyQSearchCandidateGenerationResultDisposition
  ): M12BeautyQSearchFusionRerankingInputGroup =
    disposition match {
      case M11BeautyQSearchCandidateGenerationResultDisposition.EsOnlyPending                   => EsOnlyPlaceholderInput
      case M11BeautyQSearchCandidateGenerationResultDisposition.QdrantOnlyPending               => QdrantOnlyPlaceholderInput
      case M11BeautyQSearchCandidateGenerationResultDisposition.CombinedComparisonPending       => CombinedComparisonPlaceholderInput
      case M11BeautyQSearchCandidateGenerationResultDisposition.AcceptedNegativeControlExcluded => AcceptedNegativeControlExclusionInput
      case M11BeautyQSearchCandidateGenerationResultDisposition.ManualReviewExcluded            => ManualOrNoOpExclusionInput
      case M11BeautyQSearchCandidateGenerationResultDisposition.NoOpNoiseSkipped                => ManualOrNoOpExclusionInput
    }
}

/** One M12 fusion/reranking experiment input envelope: the schema-only input shape a future offline
  * fusion/reranking experiment would consume for a single accepted M11B result row.
  *
  * This is an experiment *input* shape only, never a fusion/reranking execution and never a backend call.
  * The backend candidate legs it carries are the pending/not-executed result-leg placeholders forwarded
  * verbatim from the consumed M11B result row: nothing here holds a candidate id, provider id, score,
  * rank, backend response, fused score, reranked position, or quality label, and nothing is executed. A
  * combined-comparison envelope carries separate pending ES and Qdrant placeholders as an offline
  * experiment input; it does NOT imply production hybrid serving, fallback, score fusion, or reranking.
  */
final case class M12BeautyQSearchFusionRerankingInputEnvelope(
  queryId: String,
  category: M10BeautyQSearchQueryCategory,
  strategyIntent: M10BeautyQSearchOfflineRetrievalStrategyIntent,
  inputGroup: M12BeautyQSearchFusionRerankingInputGroup,
  disposition: M11BeautyQSearchCandidateGenerationResultDisposition,
  pendingResultLegs: List[M11BeautyQSearchCandidateGenerationResultLeg],
) {

  /** Whether this envelope forwards a pending/not-executed ES candidate result-leg placeholder. */
  def hasEsPendingLeg: Boolean =
    pendingResultLegs.exists(_.backend == M11BeautyQSearchCandidateGenerationBackend.Es)

  /** Whether this envelope forwards a pending/not-executed Qdrant candidate result-leg placeholder. */
  def hasQdrantPendingLeg: Boolean =
    pendingResultLegs.exists(_.backend == M11BeautyQSearchCandidateGenerationBackend.Qdrant)

  /** Whether this envelope is a combined ES/Qdrant comparison input (both pending legs present). */
  def isCombinedComparison: Boolean = hasEsPendingLeg && hasQdrantPendingLeg

  /** Whether this envelope forwards any backend candidate result-leg placeholder at all. */
  def hasAnyBackendCandidateLeg: Boolean = pendingResultLegs.nonEmpty

  /** Whether every forwarded leg is a pending/not-executed placeholder (no execution, no results). */
  def allLegsPendingNotExecuted: Boolean =
    pendingResultLegs.forall(_.status == M11BeautyQSearchCandidateGenerationResultLegStatus.PendingNotExecuted)

  /** A schema-only experiment input is never executable: it produces no fusion/reranking results. */
  def isExecutable: Boolean = false
}

object M12BeautyQSearchFusionRerankingInputEnvelope {

  /** Derive the M12 input envelope for an accepted M11B result row: the row's disposition picks the input
    * group and its pending/not-executed result legs are forwarded verbatim as the candidate placeholders.
    */
  def fromResultRow(
    row: M11BeautyQSearchCandidateGenerationResultRow
  ): M12BeautyQSearchFusionRerankingInputEnvelope =
    M12BeautyQSearchFusionRerankingInputEnvelope(
      queryId = row.queryId,
      category = row.category,
      strategyIntent = row.strategyIntent,
      inputGroup = M12BeautyQSearchFusionRerankingInputGroup.fromDisposition(row.disposition),
      disposition = row.disposition,
      pendingResultLegs = row.resultLegs,
    )
}

final case class M12BeautyQSearchFusionRerankingInputScaffoldMetric(
  name: String,
  value: String,
)

final case class M12BeautyQSearchFusionRerankingInputScaffoldSummary(
  datasetId: String,
  consumedM11ResultSchemaVerdict: String,
  consumedM11BoundaryFailureMatrixVerdict: String,
  consumedResultRowCount: Int,
  consumedMatrixRowCount: Int,
  consumedMatrixAcceptedRowCount: Int,
  consumedMatrixDeniedRowCount: Int,
  consumedMatrixSkippedRowCount: Int,
  inputGroupCounts: List[(M12BeautyQSearchFusionRerankingInputGroup, Int)],
  fusionRerankingInputRows: Int,
  fusionRerankingBackendCandidateRows: Int,
  fusionRerankingExecutableRows: Int,
  realCandidateResultRows: Int,
  pendingNotExecutedResultLegRows: Int,
  esPendingResultLegPlaceholderRows: Int,
  qdrantPendingResultLegPlaceholderRows: Int,
  combinedComparisonPairPlaceholders: Int,
  acceptedNegativeControlExclusions: Int,
  manualOrNoOpExclusionRows: Int,
  anchorEnvelopes: List[M12BeautyQSearchFusionRerankingInputEnvelope],
  noiseProbeEnvelopes: List[M12BeautyQSearchFusionRerankingInputEnvelope],
  m12FusionRerankingInputScaffoldReady: Boolean,
  boundary: M10BeautyQSearchOfflineRoutingBoundary,
  verdict: String,
  metrics: List[M12BeautyQSearchFusionRerankingInputScaffoldMetric],
)

/** M12 BeautyQ fusion/reranking experiment input scaffold contract: turns the accepted M11B result schema
  * and M11C boundary/failure matrix into deterministic *offline* fusion/reranking experiment input
  * envelopes.
  *
  * This is pure offline eval/planning scaffolding only. It consumes the accepted M11B result schema
  * ([[M11BeautyQSearchCandidateGenerationResultSchema]]) and the accepted M11C boundary/failure matrix
  * ([[M11BeautyQSearchCandidateGenerationBoundaryFailureMatrix]]) — including their verdicts, the accepted-row
  * total, and the standing boundary — and derives one schema-only input envelope per accepted M11B result
  * row. It defines experiment input shapes only, never fusion/reranking execution: it implements no
  * scoring, fusion, reranking, candidate retrieval, fallback, telemetry, or backend execution. It never
  * calls production `/beauty-search`, never creates an ES or Qdrant client, never runs Elasticsearch or
  * Qdrant, and never touches a route, plugin, DI, or HTTP source. It fabricates no candidate ids, provider
  * ids, scores, ranks, backend responses, fused scores, reranked positions, or quality labels.
  *
  * Backend candidate legs are the pending/not-executed result-leg placeholders forwarded verbatim from
  * M11B; exclusion envelopes (accepted negative control, manual/no-op) carry no input legs. The readiness
  * it reports is M12 *offline input-scaffold (schema-only)* readiness only. It claims no backend quality
  * green, retrieval quality, production readiness, route activation, serving approval, or execution
  * readiness. A combined-comparison envelope carries separate pending ES and Qdrant placeholders as an
  * offline experiment input, which is NOT production hybrid serving. Default `/beauty-search` stays
  * ES-backed, the Qdrant opt-in route stays disabled by default, and Qdrant production activation stays
  * not approved.
  */
object M12BeautyQSearchFusionRerankingInputScaffold {

  val MarkdownFilename: String =
    "m12-beautyq-fusion-reranking-input-scaffold.md"

  /** Readiness verdict: M12 offline input-scaffold readiness only, schema-only. Deliberately carries no
    * quality, retrieval-quality, production-readiness, route-activation, serving-approval, or
    * execution-readiness claim.
    */
  val Verdict: String =
    "m12_fusion_reranking_input_scaffold_ready_schema_only"

  /** Accepted M11B result-schema verdict this scaffold consumes. */
  val ConsumedM11ResultSchemaVerdict: String =
    M11BeautyQSearchCandidateGenerationResultSchema.Verdict

  /** Accepted M11C boundary/failure matrix verdict this scaffold consumes. */
  val ConsumedM11BoundaryFailureMatrixVerdict: String =
    M11BeautyQSearchCandidateGenerationBoundaryFailureMatrix.Verdict

  /** Representative M9 anchors carried forward from the consumed M11B result rows. */
  val AnchorQueryIds: List[String] =
    M11BeautyQSearchCandidateGenerationResultSchema.AnchorQueryIds

  /** The two q_noise_* probe ids whose explicit input envelopes prove the id prefix is not classification. */
  val NoiseProbeQueryIds: List[String] =
    M11BeautyQSearchCandidateGenerationResultSchema.NoiseProbeQueryIds

  /** One schema-only input envelope per accepted M11B result row, in dataset order. */
  val InputEnvelopes: List[M12BeautyQSearchFusionRerankingInputEnvelope] =
    M11BeautyQSearchCandidateGenerationResultSchema.ResultRows
      .map(M12BeautyQSearchFusionRerankingInputEnvelope.fromResultRow)

  /** Input-group counts in stable group order; groups with no rows report zero explicitly. */
  val InputGroupCounts: List[(M12BeautyQSearchFusionRerankingInputGroup, Int)] =
    M12BeautyQSearchFusionRerankingInputGroup.stableOrder.map { group =>
      group -> InputEnvelopes.count(_.inputGroup == group)
    }

  def inputEnvelopeFor(queryId: String): Option[M12BeautyQSearchFusionRerankingInputEnvelope] =
    InputEnvelopes.find(_.queryId == queryId)

  val DefaultSummary: M12BeautyQSearchFusionRerankingInputScaffoldSummary =
    build()

  val MarkdownArtifact: M9OfflineEvalReportArtifact =
    M12BeautyQSearchFusionRerankingInputScaffoldRenderer.markdownArtifact(
      filename = MarkdownFilename,
      summary = DefaultSummary,
    )

  def build(): M12BeautyQSearchFusionRerankingInputScaffoldSummary = {
    val byGroup = InputGroupCounts.toMap

    def groupCount(group: M12BeautyQSearchFusionRerankingInputGroup): Int =
      byGroup.getOrElse(group, 0)

    val matrixSummary = M11BeautyQSearchCandidateGenerationBoundaryFailureMatrix.DefaultSummary

    val fusionRerankingInputRows = InputEnvelopes.size
    val fusionRerankingBackendCandidateRows = InputEnvelopes.count(_.hasAnyBackendCandidateLeg)
    // A schema-only input scaffold has no executable input and produces no candidate results.
    val fusionRerankingExecutableRows = InputEnvelopes.count(_.isExecutable)
    val realCandidateResultRows = 0
    val pendingNotExecutedResultLegRows = InputEnvelopes.map(_.pendingResultLegs.size).sum
    val esPendingResultLegPlaceholderRows = InputEnvelopes.count(_.hasEsPendingLeg)
    val qdrantPendingResultLegPlaceholderRows = InputEnvelopes.count(_.hasQdrantPendingLeg)
    val combinedComparisonPairPlaceholders = InputEnvelopes.count(_.isCombinedComparison)

    val anchorEnvelopes = AnchorQueryIds.flatMap(inputEnvelopeFor)
    val noiseProbeEnvelopes = NoiseProbeQueryIds.flatMap(inputEnvelopeFor)

    // The scaffold is ready when it consumes the accepted M11B schema and the ready 20-row M11C
    // matrix, every envelope forwards only pending/not-executed legs, no input is executable, and no real
    // candidate result row exists.
    val m12FusionRerankingInputScaffoldReady =
      M11BeautyQSearchCandidateGenerationResultSchema.DefaultSummary.totalRowCount == M9BeautyQSearchEvalQueryDataset.Metadata.queryCount &&
        matrixSummary.m11BoundaryFailureMatrixReady &&
        InputEnvelopes.nonEmpty &&
        InputEnvelopes.forall(_.allLegsPendingNotExecuted) &&
        fusionRerankingExecutableRows == 0 &&
        realCandidateResultRows == 0

    val summaryWithoutMetrics = M12BeautyQSearchFusionRerankingInputScaffoldSummary(
      datasetId = M9BeautyQSearchEvalQueryDataset.Metadata.datasetId,
      consumedM11ResultSchemaVerdict = ConsumedM11ResultSchemaVerdict,
      consumedM11BoundaryFailureMatrixVerdict = ConsumedM11BoundaryFailureMatrixVerdict,
      consumedResultRowCount = M11BeautyQSearchCandidateGenerationResultSchema.DefaultSummary.totalRowCount,
      consumedMatrixRowCount = matrixSummary.matrixRowCount,
      consumedMatrixAcceptedRowCount = matrixSummary.acceptedRowCount,
      consumedMatrixDeniedRowCount = matrixSummary.deniedRowCount,
      consumedMatrixSkippedRowCount = matrixSummary.skippedRowCount,
      inputGroupCounts = InputGroupCounts,
      fusionRerankingInputRows = fusionRerankingInputRows,
      fusionRerankingBackendCandidateRows = fusionRerankingBackendCandidateRows,
      fusionRerankingExecutableRows = fusionRerankingExecutableRows,
      realCandidateResultRows = realCandidateResultRows,
      pendingNotExecutedResultLegRows = pendingNotExecutedResultLegRows,
      esPendingResultLegPlaceholderRows = esPendingResultLegPlaceholderRows,
      qdrantPendingResultLegPlaceholderRows = qdrantPendingResultLegPlaceholderRows,
      combinedComparisonPairPlaceholders = combinedComparisonPairPlaceholders,
      acceptedNegativeControlExclusions =
        groupCount(M12BeautyQSearchFusionRerankingInputGroup.AcceptedNegativeControlExclusionInput),
      manualOrNoOpExclusionRows =
        groupCount(M12BeautyQSearchFusionRerankingInputGroup.ManualOrNoOpExclusionInput),
      anchorEnvelopes = anchorEnvelopes,
      noiseProbeEnvelopes = noiseProbeEnvelopes,
      m12FusionRerankingInputScaffoldReady = m12FusionRerankingInputScaffoldReady,
      boundary = matrixSummary.boundary,
      verdict = Verdict,
      metrics = Nil,
    )

    summaryWithoutMetrics.copy(metrics = metrics(summaryWithoutMetrics))
  }

  private def metrics(
    summary: M12BeautyQSearchFusionRerankingInputScaffoldSummary
  ): List[M12BeautyQSearchFusionRerankingInputScaffoldMetric] = {
    val b = summary.boundary
    val byGroup = summary.inputGroupCounts.toMap

    def groupCount(group: M12BeautyQSearchFusionRerankingInputGroup): Int =
      byGroup.getOrElse(group, 0)

    List(
      metric("consumed_m11b_result_schema_verdict", summary.consumedM11ResultSchemaVerdict),
      metric("consumed_m11c_boundary_failure_matrix_verdict", summary.consumedM11BoundaryFailureMatrixVerdict),
      metric("consumed_result_row_count", summary.consumedResultRowCount.toString),
      metric("consumed_matrix_row_count", summary.consumedMatrixRowCount.toString),
      metric("consumed_matrix_accepted_row_count", summary.consumedMatrixAcceptedRowCount.toString),
      metric("consumed_matrix_denied_row_count", summary.consumedMatrixDeniedRowCount.toString),
      metric("consumed_matrix_skipped_row_count", summary.consumedMatrixSkippedRowCount.toString),
      metric("input_group_count_sum", summary.inputGroupCounts.map(_._2).sum.toString),
      metric(
        "es_only_placeholder_input_rows",
        groupCount(M12BeautyQSearchFusionRerankingInputGroup.EsOnlyPlaceholderInput).toString,
      ),
      metric(
        "qdrant_only_placeholder_input_rows",
        groupCount(M12BeautyQSearchFusionRerankingInputGroup.QdrantOnlyPlaceholderInput).toString,
      ),
      metric(
        "combined_comparison_placeholder_input_rows",
        groupCount(M12BeautyQSearchFusionRerankingInputGroup.CombinedComparisonPlaceholderInput).toString,
      ),
      metric("accepted_negative_control_exclusion_input_rows", summary.acceptedNegativeControlExclusions.toString),
      metric("manual_or_no_op_exclusion_input_rows", summary.manualOrNoOpExclusionRows.toString),
      metric("fusion_reranking_input_rows", summary.fusionRerankingInputRows.toString),
      metric("fusion_reranking_backend_candidate_rows", summary.fusionRerankingBackendCandidateRows.toString),
      metric("fusion_reranking_executable_rows", summary.fusionRerankingExecutableRows.toString),
      metric("real_candidate_result_rows", summary.realCandidateResultRows.toString),
      metric("pending_not_executed_result_leg_rows", summary.pendingNotExecutedResultLegRows.toString),
      metric("es_pending_result_leg_placeholder_rows", summary.esPendingResultLegPlaceholderRows.toString),
      metric("qdrant_pending_result_leg_placeholder_rows", summary.qdrantPendingResultLegPlaceholderRows.toString),
      metric("combined_comparison_pair_placeholders", summary.combinedComparisonPairPlaceholders.toString),
      metric("accepted_negative_control_exclusions", summary.acceptedNegativeControlExclusions.toString),
      metric("m12_fusion_reranking_input_scaffold_ready", summary.m12FusionRerankingInputScaffoldReady.toString),
      metric("m12_inputs_are_schema_only_not_scoring", true.toString),
      metric("m12_inputs_are_schema_only_not_fusion", true.toString),
      metric("m12_inputs_are_schema_only_not_reranking", true.toString),
      metric("m12_inputs_are_schema_only_not_candidate_retrieval", true.toString),
      metric("m12_inputs_are_schema_only_not_backend_execution", true.toString),
      metric("m12_inputs_are_offline_inputs_not_production_routing", true.toString),
      metric("m12_backend_candidate_legs_are_pending_not_executed_placeholders", true.toString),
      metric("combined_comparison_input_is_offline_not_hybrid_serving", true.toString),
      metric("accepted_negative_control_has_no_fusion_reranking_input_leg", true.toString),
      metric("manual_and_no_op_have_no_fusion_reranking_input_leg", true.toString),
      metric("no_real_candidate_ids_provider_ids_scores_ranks_fabricated", true.toString),
      metric("no_real_backend_responses_fabricated", true.toString),
      metric("no_fused_scores_reranked_positions_quality_labels_fabricated", true.toString),
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
  ): M12BeautyQSearchFusionRerankingInputScaffoldMetric =
    M12BeautyQSearchFusionRerankingInputScaffoldMetric(name, value)
}

object M12BeautyQSearchFusionRerankingInputScaffoldRenderer {

  def renderMarkdown(summary: M12BeautyQSearchFusionRerankingInputScaffoldSummary): String = {
    val builder = new StringBuilder

    line(builder, "# M12 BeautyQ Fusion/Reranking Input Scaffold")
    line(builder, "")
    line(builder, "Offline fusion/reranking experiment input scaffold over the accepted M11B candidate-generation result")
    line(builder, "schema and M11C boundary/failure matrix. This is an offline planning/eval artifact only. The input")
    line(builder, "envelopes below are schema-only experiment inputs and are NOT scoring, NOT fusion, NOT reranking, NOT backend execution,")
    line(builder, "and NOT production routing: no ES or Qdrant client is created and neither backend is run. Backend candidate legs")
    line(builder, "are pending/not-executed placeholders forwarded verbatim from M11B and carry")
    line(builder, "no candidate ids, provider ids, scores, ranks, backend responses, fused scores, reranked positions, or")
    line(builder, "quality labels. This artifact reports M12 offline input-scaffold readiness only (schema-only): it is not")
    line(builder, "backend quality green, not retrieval quality, not production readiness, not route activation, not serving")
    line(builder, "approval, and not actual execution readiness.")
    line(builder, "")
    line(builder, "## Summary")
    line(builder, "")
    line(builder, s"- dataset_id: ${renderText(summary.datasetId)}")
    line(builder, s"- verdict: ${renderText(summary.verdict)}")
    line(builder, s"- consumed_m11b_result_schema_verdict: ${renderText(summary.consumedM11ResultSchemaVerdict)}")
    line(builder, s"- consumed_m11c_boundary_failure_matrix_verdict: ${renderText(summary.consumedM11BoundaryFailureMatrixVerdict)}")
    line(builder, s"- consumed_result_row_count: ${summary.consumedResultRowCount}")
    line(builder, s"- consumed_matrix_row_count: ${summary.consumedMatrixRowCount}")
    line(builder, s"- consumed_matrix_accepted_row_count: ${summary.consumedMatrixAcceptedRowCount}")
    line(builder, s"- consumed_matrix_denied_row_count: ${summary.consumedMatrixDeniedRowCount}")
    line(builder, s"- consumed_matrix_skipped_row_count: ${summary.consumedMatrixSkippedRowCount}")
    line(builder, s"- fusion_reranking_input_rows: ${summary.fusionRerankingInputRows}")
    line(builder, s"- fusion_reranking_backend_candidate_rows: ${summary.fusionRerankingBackendCandidateRows}")
    line(builder, s"- fusion_reranking_executable_rows: ${summary.fusionRerankingExecutableRows}")
    line(builder, s"- real_candidate_result_rows: ${summary.realCandidateResultRows}")
    line(builder, s"- pending_not_executed_result_leg_rows: ${summary.pendingNotExecutedResultLegRows}")
    line(builder, s"- combined_comparison_pair_placeholders: ${summary.combinedComparisonPairPlaceholders}")
    line(builder, s"- accepted_negative_control_exclusions: ${summary.acceptedNegativeControlExclusions}")
    line(builder, s"- m12_fusion_reranking_input_scaffold_ready: ${summary.m12FusionRerankingInputScaffoldReady}")
    line(builder, "")
    line(builder, "## M12 input group counts")
    line(builder, "")
    line(builder, "These are offline experiment input groups only, not production routes. Backend placeholder groups forward")
    line(builder, "pending/not-executed candidate legs; the exclusion groups carry no fusion/reranking input legs.")
    line(builder, "")
    line(builder, "| m12_input_group | count |")
    line(builder, "|---|---|")
    summary.inputGroupCounts.foreach { case (group, count) =>
      line(builder, s"| ${renderText(group.render)} | $count |")
    }
    line(builder, "")
    line(builder, "## Pending/not-executed candidate-leg counts")
    line(builder, "")
    line(builder, "Candidate legs are pending/not-executed placeholders forwarded from M11B, not backend execution. ES legs =")
    line(builder, "ES-only inputs + combined-comparison ES legs; Qdrant legs = Qdrant-only inputs + combined-comparison Qdrant")
    line(builder, "legs; the sum is the total pending/not-executed result-leg rows.")
    line(builder, "")
    line(builder, "| metric | value |")
    line(builder, "|---|---|")
    line(builder, s"| es_pending_result_leg_placeholder_rows | ${summary.esPendingResultLegPlaceholderRows} |")
    line(builder, s"| qdrant_pending_result_leg_placeholder_rows | ${summary.qdrantPendingResultLegPlaceholderRows} |")
    line(builder, s"| pending_not_executed_result_leg_rows | ${summary.pendingNotExecutedResultLegRows} |")
    line(builder, s"| combined_comparison_pair_placeholders | ${summary.combinedComparisonPairPlaceholders} |")
    line(builder, "")
    line(builder, "## Representative anchors")
    line(builder, "")
    line(builder, "| query_id | category | offline_strategy_intent | m12_input_group | candidate_legs |")
    line(builder, "|---|---|---|---|---|")
    summary.anchorEnvelopes.foreach(envelope => line(builder, renderRow(envelope)))
    line(builder, "")
    line(builder, "## Noise-probe input envelopes")
    line(builder, "")
    line(builder, "Both ids share the q_noise_* prefix yet land in different M12 input envelopes: q_noise_004 is a combined")
    line(builder, "ES/Qdrant schema-only input with pending ES and Qdrant placeholders, q_noise_005 is an accepted")
    line(builder, "negative-control exclusion with no fusion/reranking input legs.")
    line(builder, "")
    line(builder, "| query_id | category | offline_strategy_intent | m12_input_group | candidate_legs |")
    line(builder, "|---|---|---|---|---|")
    summary.noiseProbeEnvelopes.foreach(envelope => line(builder, renderRow(envelope)))
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
    line(builder, "M12 input envelopes are schema-only fusion/reranking experiment inputs, not scoring, not reranking, not")
    line(builder, "backend execution, and not production routing; candidate legs are pending/not-executed placeholders with no")
    line(builder, "fabricated candidate ids, provider ids, scores, ranks, backend responses, fused scores, reranked positions,")
    line(builder, "or quality labels, and a combined comparison input does not imply production hybrid serving.")

    builder.result()
  }

  private def renderRow(envelope: M12BeautyQSearchFusionRerankingInputEnvelope): String = {
    val legs =
      if (envelope.pendingResultLegs.isEmpty) "(none)"
      else envelope.pendingResultLegs.map(_.render).mkString(", ")
    s"| ${renderText(envelope.queryId)} | ${renderText(envelope.category.render)} | ${renderText(envelope.strategyIntent.render)} | ${renderText(envelope.inputGroup.render)} | ${renderText(legs)} |"
  }

  def markdownArtifact(
    filename: String,
    summary: M12BeautyQSearchFusionRerankingInputScaffoldSummary,
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
