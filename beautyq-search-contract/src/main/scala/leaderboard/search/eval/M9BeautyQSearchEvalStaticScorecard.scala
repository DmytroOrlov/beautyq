package leaderboard.search.eval

final case class M9BeautyQSearchEvalStaticScorecardInput(
  metadata: M9BeautyQSearchEvalQueryDatasetMetadata,
  staticRowsResult: M9BeautyQSearchEvalQueryDatasetStaticRowsResult,
  staticRunResult: M9OfflineEvalStaticRunResult,
)

final case class M9BeautyQSearchEvalStaticScorecardMetric(
  name: String,
  value: String,
)

final case class M9BeautyQSearchEvalStaticScorecardSummary(
  datasetId: String,
  datasetQueryCount: Int,
  mappedRowCount: Int,
  staticRunnerAcceptedRowCount: Int,
  representativeAnchorRowCount: Int,
  placeholderOnlyRowCount: Int,
  candidateSources: List[CandidateSource],
  servingModes: List[ServingMode],
  manualStaticOnly: Boolean,
  unknownServingModeOnly: Boolean,
  realBackendEvidenceRowCount: Int,
  esBackendEvidenceRowCount: Int,
  qdrantBackendEvidenceRowCount: Int,
  productionActivationApproval: Boolean,
  routePluginDiHttpInvolved: Boolean,
  fullJsonParsingImplemented: Boolean,
  fullAcceptedQueryStaticExpansionImplemented: Boolean,
  realBackendCallRequired: Boolean,
  verdict: String,
  metrics: List[M9BeautyQSearchEvalStaticScorecardMetric],
)

object M9BeautyQSearchEvalStaticScorecard {
  val MarkdownFilename: String =
    "m9-beautyq-eval-static-scorecard.md"

  val VerdictDatasetStaticRowsReady: String =
    "dataset_static_rows_ready"

  val DefaultInput: M9BeautyQSearchEvalStaticScorecardInput =
    M9BeautyQSearchEvalStaticScorecardInput(
      metadata = M9BeautyQSearchEvalQueryDataset.Metadata,
      staticRowsResult = M9BeautyQSearchEvalQueryDatasetStaticRows.DefaultResult,
      staticRunResult = M9BeautyQSearchEvalQueryDatasetStaticRows.StaticRun,
    )

  val DefaultSummary: M9BeautyQSearchEvalStaticScorecardSummary =
    build(DefaultInput)

  val MarkdownArtifact: M9OfflineEvalReportArtifact =
    M9BeautyQSearchEvalStaticScorecardRenderer.markdownArtifact(
      filename = MarkdownFilename,
      summary = DefaultSummary,
    )

  def build(input: M9BeautyQSearchEvalStaticScorecardInput): M9BeautyQSearchEvalStaticScorecardSummary = {
    val rows = input.staticRowsResult.staticRunInput.rows
    val acceptedRows = input.staticRunResult.report.rows
    val candidateSources = distinctInOrder(acceptedRows.map(_.candidateSource))
    val servingModes = distinctInOrder(acceptedRows.map(_.servingMode))
    val representativeAnchorIds = input.staticRowsResult.summary.representativeQueryIds.toSet
    val representativeAnchorRowCount = acceptedRows.count(row => representativeAnchorIds.contains(row.queryId) && row.topKResultIds.nonEmpty)
    val realBackendEvidenceRows = acceptedRows.filter(row => row.candidateSource == CandidateSource.Es || row.candidateSource == CandidateSource.Qdrant)
    val esBackendEvidenceRowCount = acceptedRows.count(_.candidateSource == CandidateSource.Es)
    val qdrantBackendEvidenceRowCount = acceptedRows.count(_.candidateSource == CandidateSource.Qdrant)
    val manualStaticOnly = candidateSources == List(CandidateSource.Manual)
    val unknownServingModeOnly = servingModes == List(ServingMode.Unknown)

    val summaryWithoutMetrics = M9BeautyQSearchEvalStaticScorecardSummary(
      datasetId = input.metadata.datasetId,
      datasetQueryCount = input.metadata.queryCount,
      mappedRowCount = rows.size,
      staticRunnerAcceptedRowCount = acceptedRows.size,
      representativeAnchorRowCount = representativeAnchorRowCount,
      placeholderOnlyRowCount = acceptedRows.count(_.topKResultIds.isEmpty),
      candidateSources = candidateSources,
      servingModes = servingModes,
      manualStaticOnly = manualStaticOnly,
      unknownServingModeOnly = unknownServingModeOnly,
      realBackendEvidenceRowCount = realBackendEvidenceRows.size,
      esBackendEvidenceRowCount = esBackendEvidenceRowCount,
      qdrantBackendEvidenceRowCount = qdrantBackendEvidenceRowCount,
      productionActivationApproval = input.metadata.boundary.qdrantProductionActivationApproved,
      routePluginDiHttpInvolved = input.staticRowsResult.summary.routePluginDiHttpSourceInvolved,
      fullJsonParsingImplemented = input.staticRowsResult.summary.fullJsonParsingImplemented,
      fullAcceptedQueryStaticExpansionImplemented = input.staticRowsResult.summary.fullAcceptedQueryExpansionImplemented,
      realBackendCallRequired = input.staticRowsResult.summary.realBackendCallRequired,
      verdict = VerdictDatasetStaticRowsReady,
      metrics = Nil,
    )

    summaryWithoutMetrics.copy(metrics = metrics(summaryWithoutMetrics))
  }

  private def metrics(summary: M9BeautyQSearchEvalStaticScorecardSummary): List[M9BeautyQSearchEvalStaticScorecardMetric] =
    List(
      metric("dataset_query_count", summary.datasetQueryCount.toString),
      metric("mapped_row_count", summary.mappedRowCount.toString),
      metric("static_runner_accepted_row_count", summary.staticRunnerAcceptedRowCount.toString),
      metric("representative_anchor_row_count", summary.representativeAnchorRowCount.toString),
      metric("placeholder_only_row_count", summary.placeholderOnlyRowCount.toString),
      metric("candidate_source", renderSources(summary.candidateSources)),
      metric("serving_mode", renderModes(summary.servingModes)),
      metric("manual_static_only", summary.manualStaticOnly.toString),
      metric("unknown_serving_mode_only", summary.unknownServingModeOnly.toString),
      metric("real_backend_evidence_row_count", summary.realBackendEvidenceRowCount.toString),
      metric("es_backend_evidence_row_count", summary.esBackendEvidenceRowCount.toString),
      metric("qdrant_backend_evidence_row_count", summary.qdrantBackendEvidenceRowCount.toString),
      metric("production_activation_approval", summary.productionActivationApproval.toString),
      metric("route_plugin_di_http_involved", summary.routePluginDiHttpInvolved.toString),
      metric("full_json_parsing_implemented", summary.fullJsonParsingImplemented.toString),
      metric("full_74_query_static_expansion_implemented", summary.fullAcceptedQueryStaticExpansionImplemented.toString),
      metric("real_backend_call_required", summary.realBackendCallRequired.toString),
      metric("verdict", summary.verdict),
    )

  private def metric(
    name: String,
    value: String,
  ): M9BeautyQSearchEvalStaticScorecardMetric =
    M9BeautyQSearchEvalStaticScorecardMetric(name, value)

  private def renderSources(sources: List[CandidateSource]): String =
    sources.map(_.render).mkString(",")

  private def renderModes(modes: List[ServingMode]): String =
    modes.map(_.render).mkString(",")

  private def distinctInOrder[A](values: List[A]): List[A] =
    values.foldLeft(List.empty[A]) {
      case (seen, value) if seen.contains(value) => seen
      case (seen, value)                        => seen :+ value
    }
}

object M9BeautyQSearchEvalStaticScorecardRenderer {

  def renderMarkdown(summary: M9BeautyQSearchEvalStaticScorecardSummary): String = {
    val builder = new StringBuilder

    line(builder, "# M9 BeautyQ Eval Static Scorecard")
    line(builder, "")
    line(builder, "This scorecard is a dataset/static readiness contract over checked-in BeautyQ eval rows. It is not backend quality evidence or activation approval.")
    line(builder, "")
    line(builder, "## Summary")
    line(builder, "")
    line(builder, s"- dataset_id: ${renderText(summary.datasetId)}")
    line(builder, s"- verdict: ${renderText(summary.verdict)}")
    line(builder, s"- candidate_sources: ${renderSources(summary.candidateSources)}")
    line(builder, s"- serving_modes: ${renderModes(summary.servingModes)}")
    line(builder, "")
    line(builder, "## Metrics")
    line(builder, "")
    line(builder, "| metric | value |")
    line(builder, "|---|---|")
    summary.metrics.foreach(metric => line(builder, s"| ${renderText(metric.name)} | ${renderText(metric.value)} |"))
    line(builder, "")
    line(builder, "## Boundary")
    line(builder, "")
    line(builder, "- No real ES or Qdrant backend call is required or represented.")
    line(builder, "- No route, plugin, DI, or HTTP source is involved.")
    line(builder, "- Full JSON parsing remains intentionally deferred; the 74-query static-row expansion is implemented.")
    line(builder, "- Default /beauty-search remains ES-backed; Qdrant production activation remains not approved.")

    builder.result()
  }

  def markdownArtifact(
    filename: String,
    summary: M9BeautyQSearchEvalStaticScorecardSummary,
  ): M9OfflineEvalReportArtifact =
    M9OfflineEvalReportArtifact(
      filename = filename,
      contentType = "text/markdown; charset=utf-8",
      contents = renderMarkdown(summary),
    )

  private def renderSources(sources: List[CandidateSource]): String =
    sources.map(_.render).mkString(",")

  private def renderModes(modes: List[ServingMode]): String =
    modes.map(_.render).mkString(",")

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
