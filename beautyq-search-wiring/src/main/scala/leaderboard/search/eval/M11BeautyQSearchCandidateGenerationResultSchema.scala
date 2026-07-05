package leaderboard.search.eval

/** Status of one offline M11 candidate-generation result leg.
  *
  * A result leg is a *saved report placeholder* for a planned offline backend request leg. It is never an
  * execution: no ES or Qdrant client is created and neither backend is run. The only status this schema
  * carries is `PendingNotExecuted` — the leg is reserved in the saved report shape but has not been
  * executed and carries no candidate ids, scores, ranks, provider ids, or backend responses.
  */
enum M11BeautyQSearchCandidateGenerationResultLegStatus {
  case PendingNotExecuted

  def render: String =
    this match {
      case M11BeautyQSearchCandidateGenerationResultLegStatus.PendingNotExecuted => "pending_not_executed"
    }
}

/** One offline M11 candidate-generation result leg: a pending/not-executed placeholder for a single
  * planned backend request leg.
  *
  * This carries the backend identity and a pending status only. It deliberately holds no candidate ids,
  * scores, ranks, provider ids, or backend responses: nothing here is fabricated and nothing is executed.
  */
final case class M11BeautyQSearchCandidateGenerationResultLeg(
  backend: M11BeautyQSearchCandidateGenerationBackend,
  status: M11BeautyQSearchCandidateGenerationResultLegStatus,
) {
  def render: String = s"${backend.render}:${status.render}"
}

object M11BeautyQSearchCandidateGenerationResultLeg {

  /** A pending/not-executed result placeholder for the given backend request leg. */
  def pending(backend: M11BeautyQSearchCandidateGenerationBackend): M11BeautyQSearchCandidateGenerationResultLeg =
    M11BeautyQSearchCandidateGenerationResultLeg(
      backend = backend,
      status = M11BeautyQSearchCandidateGenerationResultLegStatus.PendingNotExecuted,
    )
}

/** Saved disposition of one M11 candidate-generation result row.
  *
  * These are offline saved-report dispositions only. A `*Pending` disposition reserves a saved report
  * shape whose backend legs are pending/not-executed placeholders; the three non-backend dispositions
  * (accepted negative-control exclusion, manual-review exclusion, no-op/noise skip) carry no backend
  * result legs at all. None of these is a production route, a backend execution, or a hybrid-serving
  * decision.
  */
enum M11BeautyQSearchCandidateGenerationResultDisposition {
  case EsOnlyPending
  case QdrantOnlyPending
  case CombinedComparisonPending
  case AcceptedNegativeControlExcluded
  case ManualReviewExcluded
  case NoOpNoiseSkipped

  def render: String =
    this match {
      case M11BeautyQSearchCandidateGenerationResultDisposition.EsOnlyPending                   => "es_only_pending_not_executed"
      case M11BeautyQSearchCandidateGenerationResultDisposition.QdrantOnlyPending               => "qdrant_only_pending_not_executed"
      case M11BeautyQSearchCandidateGenerationResultDisposition.CombinedComparisonPending       => "combined_comparison_pending_not_executed"
      case M11BeautyQSearchCandidateGenerationResultDisposition.AcceptedNegativeControlExcluded => "accepted_negative_control_excluded"
      case M11BeautyQSearchCandidateGenerationResultDisposition.ManualReviewExcluded            => "manual_review_excluded"
      case M11BeautyQSearchCandidateGenerationResultDisposition.NoOpNoiseSkipped                => "no_op_noise_skipped"
    }

  /** Whether this disposition reserves pending/not-executed backend result legs. */
  def hasBackendResultLegs: Boolean =
    this match {
      case M11BeautyQSearchCandidateGenerationResultDisposition.EsOnlyPending                   => true
      case M11BeautyQSearchCandidateGenerationResultDisposition.QdrantOnlyPending               => true
      case M11BeautyQSearchCandidateGenerationResultDisposition.CombinedComparisonPending       => true
      case M11BeautyQSearchCandidateGenerationResultDisposition.AcceptedNegativeControlExcluded => false
      case M11BeautyQSearchCandidateGenerationResultDisposition.ManualReviewExcluded            => false
      case M11BeautyQSearchCandidateGenerationResultDisposition.NoOpNoiseSkipped                => false
    }
}

object M11BeautyQSearchCandidateGenerationResultDisposition {

  /** Stable disposition order: the three pending backend dispositions first, then the three non-backend
    * excluded/skipped dispositions.
    */
  val stableOrder: List[M11BeautyQSearchCandidateGenerationResultDisposition] = List(
    EsOnlyPending,
    QdrantOnlyPending,
    CombinedComparisonPending,
    AcceptedNegativeControlExcluded,
    ManualReviewExcluded,
    NoOpNoiseSkipped,
  )

  /** Total, deterministic mapping from an accepted M10C/M11A input group to its saved result disposition. */
  def fromInputGroup(
    group: M10BeautyQSearchM11CandidateGenerationInputGroup
  ): M11BeautyQSearchCandidateGenerationResultDisposition =
    group match {
      case M10BeautyQSearchM11CandidateGenerationInputGroup.EsCandidateGenerationStudyInput       => EsOnlyPending
      case M10BeautyQSearchM11CandidateGenerationInputGroup.QdrantCandidateGenerationStudyInput   => QdrantOnlyPending
      case M10BeautyQSearchM11CandidateGenerationInputGroup.CombinedEsQdrantComparisonStudyInput  => CombinedComparisonPending
      case M10BeautyQSearchM11CandidateGenerationInputGroup.AcceptedNegativeControlExclusionInput => AcceptedNegativeControlExcluded
      case M10BeautyQSearchM11CandidateGenerationInputGroup.ManualReviewBlockedInput              => ManualReviewExcluded
      case M10BeautyQSearchM11CandidateGenerationInputGroup.NoOpNoiseInput                        => NoOpNoiseSkipped
    }
}

/** One M11 candidate-generation result row: the saved report shape for a single accepted M11A request
  * shape.
  *
  * This is a saved result/report shape only, never a backend execution. A backend request leg is rendered
  * as a pending/not-executed result placeholder; accepted negative-control exclusions and manual/no-op
  * rows render as excluded/skipped with no backend result legs. A combined-comparison row carries a
  * separate pending ES-leg and Qdrant-leg placeholder as offline report shapes; it does NOT imply
  * production hybrid serving, fallback, score fusion, or reranking.
  */
final case class M11BeautyQSearchCandidateGenerationResultRow(
  queryId: String,
  category: M10BeautyQSearchQueryCategory,
  strategyIntent: M10BeautyQSearchOfflineRetrievalStrategyIntent,
  inputGroup: M10BeautyQSearchM11CandidateGenerationInputGroup,
  disposition: M11BeautyQSearchCandidateGenerationResultDisposition,
  resultLegs: List[M11BeautyQSearchCandidateGenerationResultLeg],
) {

  /** Whether this row reserves a pending/not-executed ES result-leg placeholder. */
  def hasEsResultLeg: Boolean =
    resultLegs.exists(_.backend == M11BeautyQSearchCandidateGenerationBackend.Es)

  /** Whether this row reserves a pending/not-executed Qdrant result-leg placeholder. */
  def hasQdrantResultLeg: Boolean =
    resultLegs.exists(_.backend == M11BeautyQSearchCandidateGenerationBackend.Qdrant)

  /** Whether this row is a combined ES/Qdrant comparison report shape (both pending legs present). */
  def isCombinedComparison: Boolean = hasEsResultLeg && hasQdrantResultLeg

  /** Whether this row reserves any backend result-leg placeholder at all. */
  def hasAnyResultLeg: Boolean = resultLegs.nonEmpty

  /** Whether every reserved result leg is a pending/not-executed placeholder (no execution, no results). */
  def allLegsPendingNotExecuted: Boolean =
    resultLegs.forall(_.status == M11BeautyQSearchCandidateGenerationResultLegStatus.PendingNotExecuted)
}

object M11BeautyQSearchCandidateGenerationResultRow {

  /** Derive the saved result row for an accepted M11A request shape: each request leg becomes a
    * pending/not-executed result placeholder; non-backend dispositions get no result legs.
    */
  def fromRequestShape(
    shape: M11BeautyQSearchCandidateGenerationRequestShape
  ): M11BeautyQSearchCandidateGenerationResultRow =
    M11BeautyQSearchCandidateGenerationResultRow(
      queryId = shape.queryId,
      category = shape.category,
      strategyIntent = shape.strategyIntent,
      inputGroup = shape.inputGroup,
      disposition = M11BeautyQSearchCandidateGenerationResultDisposition.fromInputGroup(shape.inputGroup),
      resultLegs = shape.requestLegs.map(M11BeautyQSearchCandidateGenerationResultLeg.pending),
    )
}

final case class M11BeautyQSearchCandidateGenerationResultSchemaMetric(
  name: String,
  value: String,
)

final case class M11BeautyQSearchCandidateGenerationResultSchemaSummary(
  datasetId: String,
  totalRowCount: Int,
  consumedM11RequestSkeletonVerdict: String,
  consumedM10ReadinessVerdict: String,
  rowGroupCounts: List[(M10BeautyQSearchM11CandidateGenerationInputGroup, Int)],
  dispositionCounts: List[(M11BeautyQSearchCandidateGenerationResultDisposition, Int)],
  esOnlyRowCount: Int,
  qdrantOnlyRowCount: Int,
  combinedComparisonRowCount: Int,
  acceptedNegativeControlExclusionRowCount: Int,
  manualReviewBlockedRowCount: Int,
  noOpNoiseRowCount: Int,
  esResultLegPlaceholderCount: Int,
  qdrantResultLegPlaceholderCount: Int,
  combinedComparisonPairPlaceholderCount: Int,
  anchorRows: List[M11BeautyQSearchCandidateGenerationResultRow],
  noiseProbeRows: List[M11BeautyQSearchCandidateGenerationResultRow],
  m11CandidateGenerationResultSchemaReady: Boolean,
  boundary: M10BeautyQSearchOfflineRoutingBoundary,
  verdict: String,
  metrics: List[M11BeautyQSearchCandidateGenerationResultSchemaMetric],
)

/** M11 candidate-generation result schema contract: turns the accepted M11A candidate-generation request
  * skeleton into deterministic *offline* saved result/report shapes.
  *
  * This is pure offline eval/planning work only. It consumes the accepted M11A request-shape skeleton
  * ([[M11BeautyQSearchCandidateGenerationInputSkeleton]]) — including its verdict and standing boundary —
  * and derives one saved result row per accepted request shape. It defines saved result/report shapes
  * only, never backend execution: it never calls production `/beauty-search`, never creates an ES or
  * Qdrant client, never runs Elasticsearch or Qdrant, and never touches a route, plugin, DI, or HTTP
  * source. It implements no candidate retrieval, scoring, fusion, reranking, fallback, or telemetry, and
  * fabricates no candidate ids, scores, ranks, provider ids, or backend responses.
  *
  * Backend request legs render as pending/not-executed result placeholders; accepted negative-control
  * exclusions and manual/no-op rows render as excluded/skipped with no backend result legs. The readiness
  * it reports is M11 *offline result-schema* readiness only. It claims no backend quality green, retrieval
  * quality, production readiness, route activation, serving approval, or execution readiness. A
  * combined-comparison row carries separate pending ES and Qdrant result legs as offline report shapes,
  * which is NOT production hybrid serving. Default `/beauty-search` stays ES-backed, the Qdrant opt-in
  * route stays disabled by default, and Qdrant production activation stays not approved.
  */
object M11BeautyQSearchCandidateGenerationResultSchema {

  val MarkdownFilename: String =
    "m11-beautyq-candidate-generation-result-schema.md"

  /** Readiness verdict: M11 offline result-schema readiness only. Deliberately carries no quality,
    * retrieval-quality, production-readiness, route-activation, serving-approval, or execution-readiness
    * claim.
    */
  val Verdict: String =
    "m11_candidate_generation_result_schema_ready"

  /** Accepted M11A request-skeleton verdict this result schema consumes. */
  val ConsumedM11RequestSkeletonVerdict: String =
    M11BeautyQSearchCandidateGenerationInputSkeleton.Verdict

  /** Accepted M10C readiness verdict carried forward through the consumed M11A skeleton. */
  val ConsumedM10ReadinessVerdict: String =
    M11BeautyQSearchCandidateGenerationInputSkeleton.ConsumedM10ReadinessVerdict

  /** Representative M9 anchors carried forward from the accepted M11A request shapes. */
  val AnchorQueryIds: List[String] =
    M11BeautyQSearchCandidateGenerationInputSkeleton.AnchorQueryIds

  /** The two q_noise_* probe ids whose explicit result rows prove the id prefix is not classification. */
  val NoiseProbeQueryIds: List[String] =
    M11BeautyQSearchCandidateGenerationInputSkeleton.NoiseProbeQueryIds

  /** One saved result row per accepted M11A request shape, in dataset order. */
  val ResultRows: List[M11BeautyQSearchCandidateGenerationResultRow] =
    M11BeautyQSearchCandidateGenerationInputSkeleton.RequestShapes
      .map(M11BeautyQSearchCandidateGenerationResultRow.fromRequestShape)

  /** Row-group counts in stable group order, carried forward verbatim from the consumed M11A skeleton. */
  val RowGroupCounts: List[(M10BeautyQSearchM11CandidateGenerationInputGroup, Int)] =
    M11BeautyQSearchCandidateGenerationInputSkeleton.RowGroupCounts

  /** Result-disposition counts in stable disposition order; dispositions with no rows report zero. */
  val DispositionCounts: List[(M11BeautyQSearchCandidateGenerationResultDisposition, Int)] =
    M11BeautyQSearchCandidateGenerationResultDisposition.stableOrder.map { disposition =>
      disposition -> ResultRows.count(_.disposition == disposition)
    }

  def resultRowFor(queryId: String): Option[M11BeautyQSearchCandidateGenerationResultRow] =
    ResultRows.find(_.queryId == queryId)

  val DefaultSummary: M11BeautyQSearchCandidateGenerationResultSchemaSummary =
    build()

  val MarkdownArtifact: M9OfflineEvalReportArtifact =
    M11BeautyQSearchCandidateGenerationResultSchemaRenderer.markdownArtifact(
      filename = MarkdownFilename,
      summary = DefaultSummary,
    )

  def build(): M11BeautyQSearchCandidateGenerationResultSchemaSummary = {
    val byGroup = RowGroupCounts.toMap

    def groupCount(group: M10BeautyQSearchM11CandidateGenerationInputGroup): Int =
      byGroup.getOrElse(group, 0)

    val esResultLegPlaceholderCount =
      ResultRows.count(_.hasEsResultLeg)
    val qdrantResultLegPlaceholderCount =
      ResultRows.count(_.hasQdrantResultLeg)
    val combinedComparisonPairPlaceholderCount =
      ResultRows.count(_.isCombinedComparison)

    val anchorRows = AnchorQueryIds.flatMap(resultRowFor)
    val noiseProbeRows = NoiseProbeQueryIds.flatMap(resultRowFor)

    // The result schema is ready when no accepted row is left as an unresolved manual-review exclusion.
    // Accepted negative-control exclusions are a deliberate, resolved disposition with no result legs.
    val m11CandidateGenerationResultSchemaReady =
      groupCount(M10BeautyQSearchM11CandidateGenerationInputGroup.ManualReviewBlockedInput) == 0

    val summaryWithoutMetrics = M11BeautyQSearchCandidateGenerationResultSchemaSummary(
      datasetId = M9BeautyQSearchEvalQueryDataset.Metadata.datasetId,
      totalRowCount = ResultRows.size,
      consumedM11RequestSkeletonVerdict = ConsumedM11RequestSkeletonVerdict,
      consumedM10ReadinessVerdict = ConsumedM10ReadinessVerdict,
      rowGroupCounts = RowGroupCounts,
      dispositionCounts = DispositionCounts,
      esOnlyRowCount = groupCount(M10BeautyQSearchM11CandidateGenerationInputGroup.EsCandidateGenerationStudyInput),
      qdrantOnlyRowCount = groupCount(M10BeautyQSearchM11CandidateGenerationInputGroup.QdrantCandidateGenerationStudyInput),
      combinedComparisonRowCount =
        groupCount(M10BeautyQSearchM11CandidateGenerationInputGroup.CombinedEsQdrantComparisonStudyInput),
      acceptedNegativeControlExclusionRowCount =
        groupCount(M10BeautyQSearchM11CandidateGenerationInputGroup.AcceptedNegativeControlExclusionInput),
      manualReviewBlockedRowCount =
        groupCount(M10BeautyQSearchM11CandidateGenerationInputGroup.ManualReviewBlockedInput),
      noOpNoiseRowCount = groupCount(M10BeautyQSearchM11CandidateGenerationInputGroup.NoOpNoiseInput),
      esResultLegPlaceholderCount = esResultLegPlaceholderCount,
      qdrantResultLegPlaceholderCount = qdrantResultLegPlaceholderCount,
      combinedComparisonPairPlaceholderCount = combinedComparisonPairPlaceholderCount,
      anchorRows = anchorRows,
      noiseProbeRows = noiseProbeRows,
      m11CandidateGenerationResultSchemaReady = m11CandidateGenerationResultSchemaReady,
      boundary = M11BeautyQSearchCandidateGenerationInputSkeleton.DefaultSummary.boundary,
      verdict = Verdict,
      metrics = Nil,
    )

    summaryWithoutMetrics.copy(metrics = metrics(summaryWithoutMetrics))
  }

  private def metrics(
    summary: M11BeautyQSearchCandidateGenerationResultSchemaSummary
  ): List[M11BeautyQSearchCandidateGenerationResultSchemaMetric] = {
    val b = summary.boundary
    List(
      metric("total_row_count", summary.totalRowCount.toString),
      metric("consumed_m11_request_skeleton_verdict", summary.consumedM11RequestSkeletonVerdict),
      metric("consumed_m10_readiness_verdict", summary.consumedM10ReadinessVerdict),
      metric("disposition_count_sum", summary.dispositionCounts.map(_._2).sum.toString),
      metric("row_group_count_sum", summary.rowGroupCounts.map(_._2).sum.toString),
      metric("es_only_row_count", summary.esOnlyRowCount.toString),
      metric("qdrant_only_row_count", summary.qdrantOnlyRowCount.toString),
      metric("combined_comparison_row_count", summary.combinedComparisonRowCount.toString),
      metric("accepted_negative_control_exclusion_row_count", summary.acceptedNegativeControlExclusionRowCount.toString),
      metric("manual_review_blocked_row_count", summary.manualReviewBlockedRowCount.toString),
      metric("no_op_noise_row_count", summary.noOpNoiseRowCount.toString),
      metric("es_result_leg_placeholder_count", summary.esResultLegPlaceholderCount.toString),
      metric("qdrant_result_leg_placeholder_count", summary.qdrantResultLegPlaceholderCount.toString),
      metric("combined_comparison_pair_placeholder_count", summary.combinedComparisonPairPlaceholderCount.toString),
      metric("m11_candidate_generation_result_schema_ready", summary.m11CandidateGenerationResultSchemaReady.toString),
      metric("m11_result_rows_are_saved_report_shapes_not_backend_execution", true.toString),
      metric("m11_result_rows_are_offline_report_shapes_not_production_routes", true.toString),
      metric("backend_result_legs_are_pending_not_executed_placeholders", true.toString),
      metric("no_real_candidate_ids_scores_ranks_provider_ids_fabricated", true.toString),
      metric("no_real_backend_responses_fabricated", true.toString),
      metric("combined_comparison_is_offline_report_shape_not_hybrid_serving", true.toString),
      metric("accepted_negative_control_has_no_backend_result_leg", true.toString),
      metric("manual_and_no_op_have_no_backend_result_leg", true.toString),
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
  ): M11BeautyQSearchCandidateGenerationResultSchemaMetric =
    M11BeautyQSearchCandidateGenerationResultSchemaMetric(name, value)
}

object M11BeautyQSearchCandidateGenerationResultSchemaRenderer {

  def renderMarkdown(summary: M11BeautyQSearchCandidateGenerationResultSchemaSummary): String = {
    val builder = new StringBuilder

    line(builder, "# M11 BeautyQ Candidate-Generation Result Schema")
    line(builder, "")
    line(builder, "Offline candidate-generation saved result/report-shape schema over the accepted M11A candidate-generation")
    line(builder, "request skeleton. This is an offline planning/eval artifact only. The result rows below are saved report")
    line(builder, "shapes only and are NOT production routes and NOT backend execution: no ES or Qdrant client is created and")
    line(builder, "neither backend is run. Backend legs render as pending/not-executed placeholders and carry no candidate")
    line(builder, "ids, scores, ranks, provider ids, or backend responses. This artifact reports M11 offline result-schema")
    line(builder, "readiness only: it is not backend quality green, not retrieval quality, not production readiness, not route")
    line(builder, "activation, not serving approval, and not actual execution readiness.")
    line(builder, "")
    line(builder, "## Summary")
    line(builder, "")
    line(builder, s"- dataset_id: ${renderText(summary.datasetId)}")
    line(builder, s"- verdict: ${renderText(summary.verdict)}")
    line(builder, s"- consumed_m11_request_skeleton_verdict: ${renderText(summary.consumedM11RequestSkeletonVerdict)}")
    line(builder, s"- consumed_m10_readiness_verdict: ${renderText(summary.consumedM10ReadinessVerdict)}")
    line(builder, s"- total_row_count: ${summary.totalRowCount}")
    line(builder, s"- es_result_leg_placeholder_count: ${summary.esResultLegPlaceholderCount}")
    line(builder, s"- qdrant_result_leg_placeholder_count: ${summary.qdrantResultLegPlaceholderCount}")
    line(builder, s"- combined_comparison_pair_placeholder_count: ${summary.combinedComparisonPairPlaceholderCount}")
    line(builder, s"- accepted_negative_control_exclusion_row_count: ${summary.acceptedNegativeControlExclusionRowCount}")
    line(builder, s"- m11_candidate_generation_result_schema_ready: ${summary.m11CandidateGenerationResultSchemaReady}")
    line(builder, "")
    line(builder, "## M11 result disposition counts")
    line(builder, "")
    line(builder, "These are offline saved-report dispositions only, not production routes. Backend dispositions reserve")
    line(builder, "pending/not-executed result legs; the excluded/skipped dispositions carry no backend result legs.")
    line(builder, "")
    line(builder, "| result_disposition | count |")
    line(builder, "|---|---|")
    summary.dispositionCounts.foreach { case (disposition, count) =>
      line(builder, s"| ${renderText(disposition.render)} | $count |")
    }
    line(builder, "")
    line(builder, "## Pending/not-executed result-leg counts")
    line(builder, "")
    line(builder, "Result legs are pending/not-executed saved placeholders only, not backend execution. ES legs = ES-only")
    line(builder, "rows + combined-comparison ES legs; Qdrant legs = Qdrant-only rows + combined-comparison Qdrant legs.")
    line(builder, "")
    line(builder, "| metric | value |")
    line(builder, "|---|---|")
    line(builder, s"| es_result_leg_placeholder_count | ${summary.esResultLegPlaceholderCount} |")
    line(builder, s"| qdrant_result_leg_placeholder_count | ${summary.qdrantResultLegPlaceholderCount} |")
    line(builder, s"| combined_comparison_pair_placeholder_count | ${summary.combinedComparisonPairPlaceholderCount} |")
    line(builder, "")
    line(builder, "## Representative anchors")
    line(builder, "")
    line(builder, "| query_id | category | offline_strategy_intent | result_disposition | result_legs |")
    line(builder, "|---|---|---|---|---|")
    summary.anchorRows.foreach(row => line(builder, renderRow(row)))
    line(builder, "")
    line(builder, "## Noise-probe result rows")
    line(builder, "")
    line(builder, "Both ids share the q_noise_* prefix yet land in different M11 result rows: q_noise_004 is a combined")
    line(builder, "ES/Qdrant pending/not-executed comparison shape, q_noise_005 is an accepted negative-control exclusion")
    line(builder, "with no backend result legs.")
    line(builder, "")
    line(builder, "| query_id | category | offline_strategy_intent | result_disposition | result_legs |")
    line(builder, "|---|---|---|---|---|")
    summary.noiseProbeRows.foreach(row => line(builder, renderRow(row)))
    line(builder, "")
    line(builder, "## Metrics")
    line(builder, "")
    line(builder, "| metric | value |")
    line(builder, "|---|---|")
    summary.metrics.foreach(metric => line(builder, s"| ${renderText(metric.name)} | ${renderText(metric.value)} |"))
    line(builder, "")
    line(builder, "## Boundary")
    line(builder, "")
    line(builder, "Boundary posture is reported as structured booleans in the Metrics table above, not as prose negations.")
    line(builder, "M11 result rows are saved report shapes, not production routes and not backend execution; backend legs are")
    line(builder, "pending/not-executed placeholders with no fabricated candidate ids, scores, ranks, provider ids, or backend")
    line(builder, "responses, and a combined comparison row does not imply production hybrid serving.")

    builder.result()
  }

  private def renderRow(row: M11BeautyQSearchCandidateGenerationResultRow): String = {
    val legs =
      if (row.resultLegs.isEmpty) "(none)"
      else row.resultLegs.map(_.render).mkString(", ")
    s"| ${renderText(row.queryId)} | ${renderText(row.category.render)} | ${renderText(row.strategyIntent.render)} | ${renderText(row.disposition.render)} | ${renderText(legs)} |"
  }

  def markdownArtifact(
    filename: String,
    summary: M11BeautyQSearchCandidateGenerationResultSchemaSummary,
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
