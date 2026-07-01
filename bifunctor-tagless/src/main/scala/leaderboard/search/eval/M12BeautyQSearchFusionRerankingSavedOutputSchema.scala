package leaderboard.search.eval

/** Kind of a single M12 fusion/reranking saved-output row.
  *
  * The kind is derived deterministically from the consumed M12B experiment-plan group and records what
  * shape of placeholder saved output the report would persist for a given plan row. None of these is an
  * executed policy, a scoring/fusion/reranking step, a backend response, or a production route. Placeholder
  * kinds (ES baseline, Qdrant baseline, combined) carry assigned placeholder policy names only; excluded
  * kinds (accepted negative-control, manual/no-op) carry no backend candidate policy output at all.
  */
enum M12BeautyQSearchFusionRerankingSavedOutputKind {
  case EsBaselinePlaceholderOutput
  case QdrantBaselinePlaceholderOutput
  case CombinedPlaceholderOutput
  case AcceptedNegativeControlExcludedOutput
  case ManualOrNoOpOutput

  def render: String =
    this match {
      case M12BeautyQSearchFusionRerankingSavedOutputKind.EsBaselinePlaceholderOutput          => "es_baseline_placeholder_output"
      case M12BeautyQSearchFusionRerankingSavedOutputKind.QdrantBaselinePlaceholderOutput      => "qdrant_baseline_placeholder_output"
      case M12BeautyQSearchFusionRerankingSavedOutputKind.CombinedPlaceholderOutput            => "combined_placeholder_output"
      case M12BeautyQSearchFusionRerankingSavedOutputKind.AcceptedNegativeControlExcludedOutput => "accepted_negative_control_excluded_output"
      case M12BeautyQSearchFusionRerankingSavedOutputKind.ManualOrNoOpOutput                   => "manual_or_no_op_output"
    }

  /** Whether this kind persists a placeholder saved output row (ES, Qdrant, or combined). */
  def isPlaceholderOutput: Boolean =
    this match {
      case M12BeautyQSearchFusionRerankingSavedOutputKind.EsBaselinePlaceholderOutput     => true
      case M12BeautyQSearchFusionRerankingSavedOutputKind.QdrantBaselinePlaceholderOutput => true
      case M12BeautyQSearchFusionRerankingSavedOutputKind.CombinedPlaceholderOutput       => true
      case _                                                                              => false
    }

  /** Whether this kind persists an excluded saved output row (negative-control or manual/no-op). */
  def isExcludedOutput: Boolean = !isPlaceholderOutput

  /** Whether this kind is the combined placeholder output that preserves the three combined policy names. */
  def isCombinedPlaceholder: Boolean =
    this == M12BeautyQSearchFusionRerankingSavedOutputKind.CombinedPlaceholderOutput

  /** Whether this kind is the accepted negative-control excluded output. */
  def isAcceptedNegativeControl: Boolean =
    this == M12BeautyQSearchFusionRerankingSavedOutputKind.AcceptedNegativeControlExcludedOutput

  /** Whether this kind is a manual/no-op output (explicitly supported even at zero current count). */
  def isManualOrNoOp: Boolean =
    this == M12BeautyQSearchFusionRerankingSavedOutputKind.ManualOrNoOpOutput
}

object M12BeautyQSearchFusionRerankingSavedOutputKind {

  /** Stable kind order: backend baselines first, combined placeholder, then the two excluded kinds. */
  val stableOrder: List[M12BeautyQSearchFusionRerankingSavedOutputKind] = List(
    EsBaselinePlaceholderOutput,
    QdrantBaselinePlaceholderOutput,
    CombinedPlaceholderOutput,
    AcceptedNegativeControlExcludedOutput,
    ManualOrNoOpOutput,
  )

  /** Total, deterministic mapping from a consumed M12B experiment-plan group to its saved-output kind. */
  def fromPlanGroup(
    group: M12BeautyQSearchFusionRerankingExperimentPlanGroup
  ): M12BeautyQSearchFusionRerankingSavedOutputKind =
    group match {
      case M12BeautyQSearchFusionRerankingExperimentPlanGroup.EsBaselinePlan                       => EsBaselinePlaceholderOutput
      case M12BeautyQSearchFusionRerankingExperimentPlanGroup.QdrantBaselinePlan                   => QdrantBaselinePlaceholderOutput
      case M12BeautyQSearchFusionRerankingExperimentPlanGroup.CombinedExperimentPlan               => CombinedPlaceholderOutput
      case M12BeautyQSearchFusionRerankingExperimentPlanGroup.AcceptedNegativeControlExclusionPlan => AcceptedNegativeControlExcludedOutput
      case M12BeautyQSearchFusionRerankingExperimentPlanGroup.ManualOrNoOpPlan                     => ManualOrNoOpOutput
    }
}

/** One deterministic M12 fusion/reranking saved-output row.
  *
  * A saved-output row is a pure data record of what placeholder report output would be persisted for a
  * single consumed M12B experiment-plan row. It carries the consumed query id, the output kind, the
  * assigned placeholder policy names (empty for excluded rows), and a rendered placeholder output string.
  * It is never executable, never computes a fused order or reranked order, and fabricates no candidate ids,
  * provider ids, scores, ranks, backend responses, fused scores, reranked positions, quality labels,
  * fusion outputs, or reranking outputs. Combined rows preserve their three assigned placeholder policy
  * names but do NOT compute a fused or reranked order. Excluded rows render excluded output with no backend
  * candidate policy output.
  */
final case class M12BeautyQSearchFusionRerankingSavedOutputRow(
  queryId: String,
  outputKind: M12BeautyQSearchFusionRerankingSavedOutputKind,
  placeholderPolicyNames: List[String],
  renderedOutput: String,
) {

  /** Whether this row is a placeholder saved output row. */
  def isPlaceholderOutput: Boolean = outputKind.isPlaceholderOutput

  /** Whether this row is an excluded saved output row. */
  def isExcludedOutput: Boolean = outputKind.isExcludedOutput

  /** Whether this row is the combined placeholder output preserving the three combined policy names. */
  def isCombinedPlaceholder: Boolean = outputKind.isCombinedPlaceholder

  /** Whether this row is the accepted negative-control excluded output. */
  def isAcceptedNegativeControl: Boolean = outputKind.isAcceptedNegativeControl

  /** A saved-output row is never an executable output. */
  def isExecutableOutput: Boolean = false

  /** A saved-output row never records a real scored or reranked output. */
  def isRealScoredOrRerankedOutput: Boolean = false

  /** A saved-output row never carries a fabricated candidate payload. */
  def hasFabricatedCandidatePayload: Boolean = false

  /** A saved-output row never computes a fused order or a reranked order. */
  def computesFusedOrRerankedOrder: Boolean = false

  /** Whether this row carries any backend candidate policy output. Excluded rows must report false. */
  def hasBackendCandidatePolicyOutput: Boolean =
    placeholderPolicyNames.exists(
      M12BeautyQSearchFusionRerankingSavedOutputRow.BackendCandidatePolicyNames.contains,
    )

  def render: String = s"$queryId:${outputKind.render}:${placeholderPolicyNames.mkString(",")}"
}

object M12BeautyQSearchFusionRerankingSavedOutputRow {

  /** Rendered names of the backend candidate (baseline + combined) placeholder policies. */
  val BackendCandidatePolicyNames: Set[String] = Set(
    "es_baseline_passthrough",
    "qdrant_baseline_passthrough",
    "combined_union_placeholder",
    "combined_intersection_placeholder",
    "tie_breaker_placeholder",
  )

  /** Derive the M12 saved-output row for a consumed M12B experiment-plan row. */
  def fromPlanRow(
    planRow: M12BeautyQSearchFusionRerankingExperimentPlanRow
  ): M12BeautyQSearchFusionRerankingSavedOutputRow = {
    val kind = M12BeautyQSearchFusionRerankingSavedOutputKind.fromPlanGroup(planRow.planGroup)
    // Excluded rows render no backend candidate policy output; placeholder rows preserve their
    // assigned placeholder policy names verbatim, but never compute a fused or reranked order.
    val policyNames =
      if (kind.isExcludedOutput) Nil
      else planRow.assignedPolicies.map(_.render)
    val renderedOutput =
      kind match {
        case M12BeautyQSearchFusionRerankingSavedOutputKind.EsBaselinePlaceholderOutput =>
          s"es_baseline_placeholder_output(${policyNames.mkString(", ")})"
        case M12BeautyQSearchFusionRerankingSavedOutputKind.QdrantBaselinePlaceholderOutput =>
          s"qdrant_baseline_placeholder_output(${policyNames.mkString(", ")})"
        case M12BeautyQSearchFusionRerankingSavedOutputKind.CombinedPlaceholderOutput =>
          s"combined_placeholder_output(${policyNames.mkString(", ")})"
        case M12BeautyQSearchFusionRerankingSavedOutputKind.AcceptedNegativeControlExcludedOutput =>
          "excluded_output(no_backend_candidate_policy)"
        case M12BeautyQSearchFusionRerankingSavedOutputKind.ManualOrNoOpOutput =>
          "manual_or_no_op_output(no_policy)"
      }
    M12BeautyQSearchFusionRerankingSavedOutputRow(
      queryId = planRow.queryId,
      outputKind = kind,
      placeholderPolicyNames = policyNames,
      renderedOutput = renderedOutput,
    )
  }
}

final case class M12BeautyQSearchFusionRerankingSavedOutputSchemaMetric(
  name: String,
  value: String,
)

final case class M12BeautyQSearchFusionRerankingSavedOutputSchemaSummary(
  datasetId: String,
  artifactId: String,
  artifactVersion: String,
  consumedM12PolicyCatalogVerdict: String,
  consumedM12ExperimentPlanVerdict: String,
  consumedM12BoundaryFailureMatrixVerdict: String,
  consumedExperimentPlanRowCount: Int,
  consumedMatrixRowCount: Int,
  consumedMatrixAcceptedRowCount: Int,
  consumedMatrixDeniedRowCount: Int,
  consumedMatrixSkippedRowCount: Int,
  savedOutputRowCount: Int,
  esBaselinePlaceholderOutputRows: Int,
  qdrantBaselinePlaceholderOutputRows: Int,
  combinedPlaceholderOutputRows: Int,
  acceptedNegativeControlOutputRows: Int,
  manualOrNoOpOutputRows: Int,
  placeholderOutputRows: Int,
  excludedOutputRows: Int,
  executableOutputRows: Int,
  realScoredOrRerankedOutputRows: Int,
  fabricatedCandidatePayloadRows: Int,
  kindCounts: List[(M12BeautyQSearchFusionRerankingSavedOutputKind, Int)],
  qNoise004OutputRow: Option[M12BeautyQSearchFusionRerankingSavedOutputRow],
  qNoise005OutputRow: Option[M12BeautyQSearchFusionRerankingSavedOutputRow],
  m12SavedOutputSchemaReady: Boolean,
  boundary: M10BeautyQSearchOfflineRoutingBoundary,
  verdict: String,
  metrics: List[M12BeautyQSearchFusionRerankingSavedOutputSchemaMetric],
)

/** M12 BeautyQ fusion/reranking saved-output schema contract: a pure offline report schema that consumes
  * the accepted M12B experiment-plan rows and the accepted M12C boundary/failure matrix, and defines one
  * deterministic placeholder saved-output row per consumed experiment-plan row.
  *
  * This is a saved-output/report schema only, never scoring, fusion execution, reranking execution,
  * candidate retrieval, backend execution, fallback, telemetry, or production routing. It never calls
  * production `/beauty-search`, never creates an ES or Qdrant client, never runs Elasticsearch or Qdrant,
  * and never touches a route, plugin, DI, or HTTP source. Saved-output rows are placeholder-only: they
  * contain no candidate ids, provider ids, scores, ranks, backend responses, fused scores, reranked
  * positions, quality labels, fusion outputs, reranking outputs, or executable policy output. Combined
  * rows preserve their three assigned placeholder policy names but compute no fused or reranked order;
  * accepted negative-control rows render excluded output with no backend candidate policy output;
  * manual/no-op rows stay explicitly supported even though their current count is zero.
  *
  * The schema preserves the M12B plan counts (accepted saved-output rows: ES baseline placeholder, 1 Qdrant
  * baseline placeholder, 72 combined placeholder, 1 accepted negative-control exclusion, 0 manual/no-op, 0
  * executable, 0 real scored/reranked) and the M12C matrix counts (38 rows, 9 accepted, 29 denied, 0
  * skipped). It derives saved-output metrics (88 placeholder rows, 1 excluded row, 0 executable rows, 0
  * real scored/reranked rows, 0 fabricated candidate payload rows, 72 combined placeholder rows, 1
  * accepted negative-control row).
  *
  * The readiness it reports is M12 *saved-output schema* readiness only. It claims no scoring readiness, no
  * fusion/reranking execution readiness, no retrieval quality, no production readiness, no route activation,
  * and no serving approval. Default `/beauty-search` stays ES-backed, the Qdrant opt-in route stays
  * disabled by default, and Qdrant production activation stays not approved.
  */
object M12BeautyQSearchFusionRerankingSavedOutputSchema {

  val MarkdownFilename: String =
    "m12-beautyq-fusion-reranking-saved-output-schema.md"

  val ArtifactId: String = "m12-beautyq-fusion-reranking-saved-output-schema"

  val ArtifactVersion: String = "v1"

  /** Readiness verdict: M12 saved-output schema readiness only, placeholder-only. Deliberately carries no
    * scoring readiness, no fusion/reranking execution readiness, no retrieval-quality,
    * production-readiness, route-activation, or serving-approval claim.
    */
  val Verdict: String =
    "m12_fusion_reranking_saved_output_schema_ready_placeholder_only"

  /** Accepted M12B policy-catalog verdict carried forward through the consumed M12B experiment plan. */
  val ConsumedM12PolicyCatalogVerdict: String =
    M12BeautyQSearchFusionRerankingPolicyCatalog.Verdict

  /** Accepted M12B experiment-plan verdict this saved-output schema consumes. */
  val ConsumedM12ExperimentPlanVerdict: String =
    M12BeautyQSearchFusionRerankingExperimentPlan.Verdict

  /** Accepted M12C boundary/failure matrix verdict this saved-output schema consumes. */
  val ConsumedM12BoundaryFailureMatrixVerdict: String =
    M12BeautyQSearchFusionRerankingBoundaryFailureMatrix.Verdict

  /** One deterministic placeholder saved-output row per consumed M12B experiment-plan row, in plan order. */
  val SavedOutputRows: List[M12BeautyQSearchFusionRerankingSavedOutputRow] =
    M12BeautyQSearchFusionRerankingExperimentPlan.PlanRows.map(
      M12BeautyQSearchFusionRerankingSavedOutputRow.fromPlanRow,
    )

  /** Saved-output kind counts in stable kind order; kinds with no rows report zero explicitly. */
  val KindCounts: List[(M12BeautyQSearchFusionRerankingSavedOutputKind, Int)] =
    M12BeautyQSearchFusionRerankingSavedOutputKind.stableOrder.map { kind =>
      kind -> SavedOutputRows.count(_.outputKind == kind)
    }

  def savedOutputRowFor(queryId: String): Option[M12BeautyQSearchFusionRerankingSavedOutputRow] =
    SavedOutputRows.find(_.queryId == queryId)

  val DefaultSummary: M12BeautyQSearchFusionRerankingSavedOutputSchemaSummary =
    build()

  val MarkdownArtifact: M9OfflineEvalReportArtifact =
    M12BeautyQSearchFusionRerankingSavedOutputSchemaRenderer.markdownArtifact(
      filename = MarkdownFilename,
      summary = DefaultSummary,
    )

  def build(): M12BeautyQSearchFusionRerankingSavedOutputSchemaSummary = {
    val consumedPlan = M12BeautyQSearchFusionRerankingExperimentPlan.DefaultSummary
    val consumedMatrix = M12BeautyQSearchFusionRerankingBoundaryFailureMatrix.DefaultSummary

    val consumedExperimentPlanRowCount = consumedPlan.planGroupCounts.map(_._2).sum

    val byKind = KindCounts.toMap
    def kindCount(kind: M12BeautyQSearchFusionRerankingSavedOutputKind): Int = byKind.getOrElse(kind, 0)

    val esBaselinePlaceholderOutputRows =
      kindCount(M12BeautyQSearchFusionRerankingSavedOutputKind.EsBaselinePlaceholderOutput)
    val qdrantBaselinePlaceholderOutputRows =
      kindCount(M12BeautyQSearchFusionRerankingSavedOutputKind.QdrantBaselinePlaceholderOutput)
    val combinedPlaceholderOutputRows =
      kindCount(M12BeautyQSearchFusionRerankingSavedOutputKind.CombinedPlaceholderOutput)
    val acceptedNegativeControlOutputRows =
      kindCount(M12BeautyQSearchFusionRerankingSavedOutputKind.AcceptedNegativeControlExcludedOutput)
    val manualOrNoOpOutputRows =
      kindCount(M12BeautyQSearchFusionRerankingSavedOutputKind.ManualOrNoOpOutput)

    val savedOutputRowCount = SavedOutputRows.size
    val placeholderOutputRows = SavedOutputRows.count(_.isPlaceholderOutput)
    val excludedOutputRows = SavedOutputRows.count(_.isExcludedOutput)
    val executableOutputRows = SavedOutputRows.count(_.isExecutableOutput)
    val realScoredOrRerankedOutputRows = SavedOutputRows.count(_.isRealScoredOrRerankedOutput)
    val fabricatedCandidatePayloadRows = SavedOutputRows.count(_.hasFabricatedCandidatePayload)

    val qNoise004OutputRow = savedOutputRowFor("q_noise_004")
    val qNoise005OutputRow = savedOutputRowFor("q_noise_005")

    // The schema is ready when it consumes the accepted M12B plan and the accepted 38-row M12C
    // matrix (9 accepted / 29 denied / 0 skipped), the saved-output rows preserve the M12B plan counts,
    // and every row is placeholder-only: non-executable, never scored/reranked, never fabricated, never
    // computing a fused or reranked order, with excluded rows carrying no backend candidate policy output.
    val m12SavedOutputSchemaReady =
      consumedPlan.m12FusionRerankingExperimentPlanReady &&
        consumedExperimentPlanRowCount == M9BeautyQSearchEvalQueryDataset.Metadata.queryCount &&
        consumedMatrix.m12BoundaryFailureMatrixReady &&
        consumedMatrix.matrixRowCount == 38 &&
        consumedMatrix.acceptedRowCount == 9 &&
        consumedMatrix.deniedRowCount == 29 &&
        consumedMatrix.skippedRowCount == 0 &&
        savedOutputRowCount == M9BeautyQSearchEvalQueryDataset.Metadata.queryCount &&
        esBaselinePlaceholderOutputRows == 15 &&
        qdrantBaselinePlaceholderOutputRows == 1 &&
        combinedPlaceholderOutputRows == 72 &&
        acceptedNegativeControlOutputRows == 1 &&
        manualOrNoOpOutputRows == 0 &&
        placeholderOutputRows == 88 &&
        excludedOutputRows == 1 &&
        placeholderOutputRows + excludedOutputRows == M9BeautyQSearchEvalQueryDataset.Metadata.queryCount &&
        esBaselinePlaceholderOutputRows + qdrantBaselinePlaceholderOutputRows +
          combinedPlaceholderOutputRows + acceptedNegativeControlOutputRows +
          manualOrNoOpOutputRows == M9BeautyQSearchEvalQueryDataset.Metadata.queryCount &&
        executableOutputRows == 0 &&
        realScoredOrRerankedOutputRows == 0 &&
        fabricatedCandidatePayloadRows == 0 &&
        SavedOutputRows.forall(row =>
          !row.isExecutableOutput && !row.isRealScoredOrRerankedOutput &&
            !row.hasFabricatedCandidatePayload && !row.computesFusedOrRerankedOrder,
        ) &&
        // Excluded rows must carry no backend candidate policy output.
        SavedOutputRows.filter(_.isExcludedOutput).forall(!_.hasBackendCandidatePolicyOutput) &&
        // Combined rows must preserve exactly the three combined placeholder policy names.
        SavedOutputRows.filter(_.isCombinedPlaceholder).forall { row =>
          row.placeholderPolicyNames.toSet == Set(
            "combined_union_placeholder",
            "combined_intersection_placeholder",
            "tie_breaker_placeholder",
          )
        }

    val summaryWithoutMetrics = M12BeautyQSearchFusionRerankingSavedOutputSchemaSummary(
      datasetId = M9BeautyQSearchEvalQueryDataset.Metadata.datasetId,
      artifactId = ArtifactId,
      artifactVersion = ArtifactVersion,
      consumedM12PolicyCatalogVerdict = ConsumedM12PolicyCatalogVerdict,
      consumedM12ExperimentPlanVerdict = ConsumedM12ExperimentPlanVerdict,
      consumedM12BoundaryFailureMatrixVerdict = ConsumedM12BoundaryFailureMatrixVerdict,
      consumedExperimentPlanRowCount = consumedExperimentPlanRowCount,
      consumedMatrixRowCount = consumedMatrix.matrixRowCount,
      consumedMatrixAcceptedRowCount = consumedMatrix.acceptedRowCount,
      consumedMatrixDeniedRowCount = consumedMatrix.deniedRowCount,
      consumedMatrixSkippedRowCount = consumedMatrix.skippedRowCount,
      savedOutputRowCount = savedOutputRowCount,
      esBaselinePlaceholderOutputRows = esBaselinePlaceholderOutputRows,
      qdrantBaselinePlaceholderOutputRows = qdrantBaselinePlaceholderOutputRows,
      combinedPlaceholderOutputRows = combinedPlaceholderOutputRows,
      acceptedNegativeControlOutputRows = acceptedNegativeControlOutputRows,
      manualOrNoOpOutputRows = manualOrNoOpOutputRows,
      placeholderOutputRows = placeholderOutputRows,
      excludedOutputRows = excludedOutputRows,
      executableOutputRows = executableOutputRows,
      realScoredOrRerankedOutputRows = realScoredOrRerankedOutputRows,
      fabricatedCandidatePayloadRows = fabricatedCandidatePayloadRows,
      kindCounts = KindCounts,
      qNoise004OutputRow = qNoise004OutputRow,
      qNoise005OutputRow = qNoise005OutputRow,
      m12SavedOutputSchemaReady = m12SavedOutputSchemaReady,
      boundary = consumedPlan.boundary,
      verdict = Verdict,
      metrics = Nil,
    )

    summaryWithoutMetrics.copy(metrics = metrics(summaryWithoutMetrics))
  }

  private def metrics(
    summary: M12BeautyQSearchFusionRerankingSavedOutputSchemaSummary
  ): List[M12BeautyQSearchFusionRerankingSavedOutputSchemaMetric] = {
    val b = summary.boundary
    List(
      metric("artifact_id", summary.artifactId),
      metric("artifact_version", summary.artifactVersion),
      metric("consumed_m12b_policy_catalog_verdict", summary.consumedM12PolicyCatalogVerdict),
      metric("consumed_m12b_experiment_plan_verdict", summary.consumedM12ExperimentPlanVerdict),
      metric("consumed_m12c_boundary_failure_matrix_verdict", summary.consumedM12BoundaryFailureMatrixVerdict),
      metric("consumed_experiment_plan_row_count", summary.consumedExperimentPlanRowCount.toString),
      metric("consumed_matrix_row_count", summary.consumedMatrixRowCount.toString),
      metric("consumed_matrix_accepted_row_count", summary.consumedMatrixAcceptedRowCount.toString),
      metric("consumed_matrix_denied_row_count", summary.consumedMatrixDeniedRowCount.toString),
      metric("consumed_matrix_skipped_row_count", summary.consumedMatrixSkippedRowCount.toString),
      metric("saved_output_rows", summary.savedOutputRowCount.toString),
      metric("es_baseline_placeholder_output_rows", summary.esBaselinePlaceholderOutputRows.toString),
      metric("qdrant_baseline_placeholder_output_rows", summary.qdrantBaselinePlaceholderOutputRows.toString),
      metric("combined_placeholder_output_rows", summary.combinedPlaceholderOutputRows.toString),
      metric("accepted_negative_control_output_rows", summary.acceptedNegativeControlOutputRows.toString),
      metric("manual_or_no_op_output_rows", summary.manualOrNoOpOutputRows.toString),
      metric("placeholder_output_rows", summary.placeholderOutputRows.toString),
      metric("excluded_output_rows", summary.excludedOutputRows.toString),
      metric("executable_output_rows", summary.executableOutputRows.toString),
      metric("real_scored_or_reranked_output_rows", summary.realScoredOrRerankedOutputRows.toString),
      metric("fabricated_candidate_payload_rows", summary.fabricatedCandidatePayloadRows.toString),
      metric("saved_output_row_count_sum", summary.kindCounts.map(_._2).sum.toString),
      metric(
        "placeholder_plus_excluded_output_rows",
        (summary.placeholderOutputRows + summary.excludedOutputRows).toString,
      ),
      metric("m12_saved_output_schema_ready", summary.m12SavedOutputSchemaReady.toString),
      metric("saved_output_is_placeholder_only_not_scoring", true.toString),
      metric("saved_output_is_placeholder_only_not_fusion_execution", true.toString),
      metric("saved_output_is_placeholder_only_not_reranking_execution", true.toString),
      metric("saved_output_is_placeholder_only_not_candidate_retrieval", true.toString),
      metric("saved_output_is_placeholder_only_not_backend_execution", true.toString),
      metric("saved_output_is_offline_report_not_production_routing", true.toString),
      metric("combined_rows_preserve_policy_names_without_fused_or_reranked_order", true.toString),
      metric("accepted_negative_control_rows_render_excluded_without_backend_candidate_policy", true.toString),
      metric("manual_or_no_op_rows_remain_explicitly_supported_at_zero_count", true.toString),
      metric("saved_output_does_not_execute_es_or_qdrant", true.toString),
      metric("saved_output_creates_no_es_or_qdrant_client", true.toString),
      metric("saved_output_calls_no_production_beauty_search", true.toString),
      metric("saved_output_involves_no_route_plugin_di_http", true.toString),
      metric("saved_output_implements_no_fallback", true.toString),
      metric("saved_output_implements_no_hybrid_serving", true.toString),
      metric("saved_output_implements_no_production_telemetry", true.toString),
      metric("saved_output_fabricates_no_candidate_ids_provider_ids_scores_ranks_backend_responses", true.toString),
      metric("saved_output_fabricates_no_fused_scores_reranked_positions_quality_labels", true.toString),
      metric("saved_output_fabricates_no_fusion_outputs_reranking_outputs", true.toString),
      metric("saved_output_contains_no_executable_policy_output", true.toString),
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
  ): M12BeautyQSearchFusionRerankingSavedOutputSchemaMetric =
    M12BeautyQSearchFusionRerankingSavedOutputSchemaMetric(name, value)
}

object M12BeautyQSearchFusionRerankingSavedOutputSchemaRenderer {

  def renderMarkdown(summary: M12BeautyQSearchFusionRerankingSavedOutputSchemaSummary): String = {
    val builder = new StringBuilder

    line(builder, "# M12 BeautyQ Fusion/Reranking Saved Output Schema")
    line(builder, "")
    line(builder, "Offline saved-output/report schema over the accepted M12B fusion/reranking experiment-plan rows")
    line(builder, "and the accepted M12C fusion/reranking boundary/failure matrix. This is an offline")
    line(builder, "planning/eval/reporting artifact only. It defines one deterministic placeholder saved-output row")
    line(builder, "per consumed M12B experiment-plan row and is NOT scoring, NOT fusion execution, NOT reranking execution,")
    line(builder, "NOT candidate retrieval, NOT backend execution, and NOT production routing: no ES or Qdrant")
    line(builder, "client is created and neither backend is run. Saved-output rows are placeholder-only and")
    line(builder, "contain no candidate ids, provider ids, scores, ranks, backend responses, fused scores, reranked")
    line(builder, "positions, quality labels, fusion outputs, reranking outputs, or executable policy output.")
    line(builder, "Combined rows preserve their three assigned placeholder policy names but compute no fused or")
    line(builder, "reranked order; accepted negative-control rows render excluded output with no backend candidate")
    line(builder, "policy output; manual/no-op rows stay explicitly supported even at zero current count. This")
    line(builder, "artifact reports M12 saved-output schema readiness only (placeholder-only): it is not scoring")
    line(builder, "readiness, not fusion/reranking execution readiness, not retrieval quality, not production")
    line(builder, "readiness, not route activation, and not serving approval.")
    line(builder, "")
    line(builder, "## Artifact identity")
    line(builder, "")
    line(builder, s"- artifact_id: ${renderText(summary.artifactId)}")
    line(builder, s"- artifact_version: ${renderText(summary.artifactVersion)}")
    line(builder, s"- dataset_id: ${renderText(summary.datasetId)}")
    line(builder, "")
    line(builder, "## Summary")
    line(builder, "")
    line(builder, s"- verdict: ${renderText(summary.verdict)}")
    line(builder, s"- consumed_m12b_policy_catalog_verdict: ${renderText(summary.consumedM12PolicyCatalogVerdict)}")
    line(builder, s"- consumed_m12b_experiment_plan_verdict: ${renderText(summary.consumedM12ExperimentPlanVerdict)}")
    line(builder, s"- consumed_m12c_boundary_failure_matrix_verdict: ${renderText(summary.consumedM12BoundaryFailureMatrixVerdict)}")
    line(builder, s"- consumed_experiment_plan_row_count: ${summary.consumedExperimentPlanRowCount}")
    line(builder, s"- consumed_matrix_row_count: ${summary.consumedMatrixRowCount}")
    line(builder, s"- consumed_matrix_accepted_row_count: ${summary.consumedMatrixAcceptedRowCount}")
    line(builder, s"- consumed_matrix_denied_row_count: ${summary.consumedMatrixDeniedRowCount}")
    line(builder, s"- consumed_matrix_skipped_row_count: ${summary.consumedMatrixSkippedRowCount}")
    line(builder, s"- saved_output_rows: ${summary.savedOutputRowCount}")
    line(builder, s"- es_baseline_placeholder_output_rows: ${summary.esBaselinePlaceholderOutputRows}")
    line(builder, s"- qdrant_baseline_placeholder_output_rows: ${summary.qdrantBaselinePlaceholderOutputRows}")
    line(builder, s"- combined_placeholder_output_rows: ${summary.combinedPlaceholderOutputRows}")
    line(builder, s"- accepted_negative_control_output_rows: ${summary.acceptedNegativeControlOutputRows}")
    line(builder, s"- manual_or_no_op_output_rows: ${summary.manualOrNoOpOutputRows}")
    line(builder, s"- placeholder_output_rows: ${summary.placeholderOutputRows}")
    line(builder, s"- excluded_output_rows: ${summary.excludedOutputRows}")
    line(builder, s"- executable_output_rows: ${summary.executableOutputRows}")
    line(builder, s"- real_scored_or_reranked_output_rows: ${summary.realScoredOrRerankedOutputRows}")
    line(builder, s"- fabricated_candidate_payload_rows: ${summary.fabricatedCandidatePayloadRows}")
    line(builder, s"- m12_saved_output_schema_ready: ${summary.m12SavedOutputSchemaReady}")
    line(builder, "")
    line(builder, "## Saved output kind counts")
    line(builder, "")
    line(builder, "Each consumed M12B experiment-plan row becomes one placeholder saved-output row. Backend baseline")
    line(builder, "rows save an ES or Qdrant baseline placeholder output; combined rows save a combined placeholder")
    line(builder, "output preserving the three combined policy names (union, intersection, tie-breaker) without")
    line(builder, "computing a fused or reranked order; the accepted negative-control row saves excluded output with")
    line(builder, "no backend candidate policy output; manual/no-op rows save no policy output. No row is executable")
    line(builder, "and no real scored/reranked output is produced.")
    line(builder, "")
    line(builder, "| saved_output_kind | count |")
    line(builder, "|---|---|")
    summary.kindCounts.foreach { case (kind, count) =>
      line(builder, s"| ${renderText(kind.render)} | $count |")
    }
    line(builder, "")
    line(builder, "## q_noise_004 and q_noise_005 saved output rows")
    line(builder, "")
    line(builder, "Both ids share the q_noise_* prefix yet land in different saved-output rows: q_noise_004 = gel")
    line(builder, "removal saves a combined placeholder output preserving the three non-executable combined policy")
    line(builder, "names; q_noise_005 = lifting saves accepted negative-control excluded output with no backend")
    line(builder, "candidate policy output.")
    line(builder, "")
    line(builder, "| query_id | saved_output_kind | placeholder_policy_names | rendered_output |")
    line(builder, "|---|---|---|---|")
    renderProbeRow(builder, summary.qNoise004OutputRow)
    renderProbeRow(builder, summary.qNoise005OutputRow)
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
    line(builder, "This is a placeholder-only saved-output schema, not scoring, not fusion execution, not reranking")
    line(builder, "execution, not backend execution, and not production routing: no candidate ids, provider ids,")
    line(builder, "scores, ranks, backend responses, fused scores, reranked positions, quality labels, fusion")
    line(builder, "outputs, reranking outputs, or executable policy output are present, and no production route,")
    line(builder, "hybrid serving, fallback, fusion, reranking, telemetry, or activation is implemented or claimed.")

    builder.result()
  }

  private def renderProbeRow(
    builder: StringBuilder,
    row: Option[M12BeautyQSearchFusionRerankingSavedOutputRow],
  ): Unit =
    row match {
      case Some(r) =>
        val policyNames =
          if (r.placeholderPolicyNames.isEmpty) "(none)"
          else r.placeholderPolicyNames.mkString(", ")
        line(
          builder,
          s"| ${renderText(r.queryId)} | ${renderText(r.outputKind.render)} | ${renderText(policyNames)} | ${renderText(r.renderedOutput)} |",
        )
      case None =>
        line(builder, "| (missing) | (missing) | (missing) | (missing) |")
    }

  def markdownArtifact(
    filename: String,
    summary: M12BeautyQSearchFusionRerankingSavedOutputSchemaSummary,
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
