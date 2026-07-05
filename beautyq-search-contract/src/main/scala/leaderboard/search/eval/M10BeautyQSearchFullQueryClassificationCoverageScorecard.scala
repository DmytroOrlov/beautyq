package leaderboard.search.eval

final case class M10BeautyQSearchFullClassificationCoverageAnchorRow(
  queryId: String,
  category: M10BeautyQSearchQueryCategory,
  strategyIntent: M10BeautyQSearchOfflineRetrievalStrategyIntent,
)

final case class M10BeautyQSearchFullClassificationCoverageMetric(
  name: String,
  value: String,
)

final case class M10BeautyQSearchFullClassificationCoverageSummary(
  datasetId: String,
  totalQueryCount: Int,
  mappedRowCount: Int,
  categoryCounts: List[(M10BeautyQSearchQueryCategory, Int)],
  strategyIntentCounts: List[(M10BeautyQSearchOfflineRetrievalStrategyIntent, Int)],
  anchorRows: List[M10BeautyQSearchFullClassificationCoverageAnchorRow],
  noisyRowCount: Int,
  unresolvedManualReviewRowCount: Int,
  acceptedNegativeControlExclusionCount: Int,
  noOpRowCount: Int,
  mixedIntentCount: Int,
  backendCandidateStudyIntentCount: Int,
  m11BackendCandidateInputsReady: Boolean,
  acceptedNegativeControlRows: List[String],
  unresolvedManualReviewRows: List[String],
  boundary: M10BeautyQSearchOfflineRoutingBoundary,
  verdict: String,
  metrics: List[M10BeautyQSearchFullClassificationCoverageMetric],
)

/** Deterministic full-dataset classification coverage scorecard over the accepted-query M10 mapping.
  *
  * This is an offline coverage/reporting contract only. It summarizes the full classification mapping and
  * its offline strategy intents; it never calls production `/beauty-search`, never creates an ES or
  * Qdrant client, never runs Elasticsearch or Qdrant, and never touches a route, plugin, DI, or HTTP
  * source. It claims no quality green, production readiness, route activation, serving approval, hybrid
  * serving, fallback, score fusion, reranking, production telemetry, or route switch. Offline strategy
  * intent is explicitly distinguished from production routing. Default `/beauty-search` remains ES-backed,
  * the Qdrant opt-in route remains disabled by default, and Qdrant production activation remains not
  * approved.
  */
object M10BeautyQSearchFullQueryClassificationCoverageScorecard {

  val MarkdownFilename: String =
    "m10-beautyq-full-query-classification-coverage-scorecard.md"

  val Verdict: String =
    "full_query_classification_coverage_ready_with_negative_control_exclusion"

  val DefaultSummary: M10BeautyQSearchFullClassificationCoverageSummary =
    build()

  val MarkdownArtifact: M9OfflineEvalReportArtifact =
    M10BeautyQSearchFullQueryClassificationCoverageScorecardRenderer.markdownArtifact(
      filename = MarkdownFilename,
      summary = DefaultSummary,
    )

  def build(): M10BeautyQSearchFullClassificationCoverageSummary = {
    val results = M10BeautyQSearchFullQueryClassification.FullResults
    val decisions = M10BeautyQSearchFullQueryClassification.FullDecisions
    val categoryCounts = M10BeautyQSearchFullQueryClassification.CategoryCounts
    val strategyCounts = M10BeautyQSearchFullQueryClassification.StrategyIntentCounts

    val anchorRows =
      M10BeautyQSearchFullQueryClassification.AnchorQueryIds.flatMap { queryId =>
        M10BeautyQSearchFullQueryClassification.decisionFor(queryId).map { decision =>
          M10BeautyQSearchFullClassificationCoverageAnchorRow(
            queryId = queryId,
            category = decision.category,
            strategyIntent = decision.strategyIntent,
          )
        }
      }

    val noisyRowCount =
      results.count(_.category == M10BeautyQSearchQueryCategory.NoisyAmbiguousNonBeautyIntent)
    val acceptedNegativeControlRows =
      M10BeautyQSearchFullQueryClassification.AcceptedNegativeControlQueryIds
    val unresolvedManualReviewRows =
      M10BeautyQSearchFullQueryClassification.UnresolvedManualReviewQueryIds
    val noOpRowCount =
      decisions.count(_.strategyIntent == M10BeautyQSearchOfflineRetrievalStrategyIntent.NoOpNoise)
    val mixedIntentCount =
      results.count(_.category == M10BeautyQSearchQueryCategory.MixedIntent)
    val backendCandidateStudyIntentCount =
      decisions.count(_.strategyIntent.isBackendCandidateRetrievalIntent)
    // M11 backend-candidate input preparation is ready when no row is left as unresolved manual review:
    // accepted negative-control exclusions are a deliberate, resolved outcome and do not block readiness.
    val m11BackendCandidateInputsReady = unresolvedManualReviewRows.isEmpty

    val summaryWithoutMetrics = M10BeautyQSearchFullClassificationCoverageSummary(
      datasetId = M9BeautyQSearchEvalQueryDataset.Metadata.datasetId,
      totalQueryCount = M10BeautyQSearchFullQueryClassification.DatasetQueryIds.size,
      mappedRowCount = results.size,
      categoryCounts = categoryCounts,
      strategyIntentCounts = strategyCounts,
      anchorRows = anchorRows,
      noisyRowCount = noisyRowCount,
      unresolvedManualReviewRowCount = unresolvedManualReviewRows.size,
      acceptedNegativeControlExclusionCount = acceptedNegativeControlRows.size,
      noOpRowCount = noOpRowCount,
      mixedIntentCount = mixedIntentCount,
      backendCandidateStudyIntentCount = backendCandidateStudyIntentCount,
      m11BackendCandidateInputsReady = m11BackendCandidateInputsReady,
      acceptedNegativeControlRows = acceptedNegativeControlRows,
      unresolvedManualReviewRows = unresolvedManualReviewRows,
      boundary = M10BeautyQSearchOfflineRoutingBoundary.Standing,
      verdict = Verdict,
      metrics = Nil,
    )

    summaryWithoutMetrics.copy(metrics = metrics(summaryWithoutMetrics))
  }

  private def metrics(
    summary: M10BeautyQSearchFullClassificationCoverageSummary
  ): List[M10BeautyQSearchFullClassificationCoverageMetric] = {
    val b = summary.boundary
    List(
      metric("total_query_count", summary.totalQueryCount.toString),
      metric("mapped_row_count", summary.mappedRowCount.toString),
      metric("category_count_sum", summary.categoryCounts.map(_._2).sum.toString),
      metric("strategy_intent_count_sum", summary.strategyIntentCounts.map(_._2).sum.toString),
      metric("mixed_intent_count", summary.mixedIntentCount.toString),
      metric("noisy_row_count", summary.noisyRowCount.toString),
      metric("unresolved_manual_review_row_count", summary.unresolvedManualReviewRowCount.toString),
      metric("accepted_negative_control_exclusion_count", summary.acceptedNegativeControlExclusionCount.toString),
      metric("no_op_row_count", summary.noOpRowCount.toString),
      metric("backend_candidate_study_intent_count", summary.backendCandidateStudyIntentCount.toString),
      metric("m11_backend_candidate_inputs_ready", summary.m11BackendCandidateInputsReady.toString),
      metric("offline_strategy_intent_is_not_production_routing", true.toString),
      metric(BeautyQSearchEvaluationMetricNames.SharedProductionPosture.DefaultBeautySearchEsBacked, b.defaultBeautySearchEsBacked.toString),
      metric(BeautyQSearchEvaluationMetricNames.SharedProductionPosture.QdrantOptInDisabledByDefault, b.qdrantOptInDisabledByDefault.toString),
      metric(BeautyQSearchEvaluationMetricNames.SharedProductionPosture.QdrantProductionActivationApproved, b.qdrantProductionActivationApproved.toString),
      metric(BeautyQSearchEvaluationMetricNames.SharedProductionPosture.ProductionRouteActivated, b.productionRouteActivated.toString),
      metric(BeautyQSearchEvaluationMetricNames.SharedProductionPosture.DefaultRouteSwitched, b.defaultRouteSwitched.toString),
      metric(BeautyQSearchEvaluationMetricNames.SharedProductionPosture.ProductionBeautySearchCalled, b.productionBeautySearchCalled.toString),
      metric(BeautyQSearchEvaluationMetricNames.SharedProductionPosture.EsClientCreated, b.esClientCreated.toString),
      metric(BeautyQSearchEvaluationMetricNames.SharedProductionPosture.QdrantClientCreated, b.qdrantClientCreated.toString),
      metric(BeautyQSearchEvaluationMetricNames.SharedProductionPosture.EsExecuted, b.esExecuted.toString),
      metric(BeautyQSearchEvaluationMetricNames.SharedProductionPosture.QdrantExecuted, b.qdrantExecuted.toString),
      metric(BeautyQSearchEvaluationMetricNames.SharedProductionPosture.RoutePluginDiHttpInvolved, b.routePluginDiHttpInvolved.toString),
      metric(BeautyQSearchEvaluationMetricNames.SharedProductionPosture.RealBackendCallRequired, b.realBackendCallRequired.toString),
      metric(BeautyQSearchEvaluationMetricNames.SharedProductionPosture.RealBackendCallImplemented, b.realBackendCallImplemented.toString),
      metric(BeautyQSearchEvaluationMetricNames.SharedProductionPosture.HybridServingImplied, b.hybridServingImplied.toString),
      metric(BeautyQSearchEvaluationMetricNames.SharedProductionPosture.FallbackImplied, b.fallbackImplied.toString),
      metric(BeautyQSearchEvaluationMetricNames.SharedProductionPosture.ScoreFusionImplied, b.scoreFusionImplied.toString),
      metric(BeautyQSearchEvaluationMetricNames.SharedProductionPosture.RerankingImplied, b.rerankingImplied.toString),
      metric(BeautyQSearchEvaluationMetricNames.SharedProductionPosture.ProductionTelemetryImplied, b.productionTelemetryImplied.toString),
      metric(BeautyQSearchEvaluationMetricNames.SharedProductionPosture.QualityGreenClaimed, b.qualityGreenClaimed.toString),
      metric(BeautyQSearchEvaluationMetricNames.SharedProductionPosture.ProductionReadinessClaimed, b.productionReadinessClaimed.toString),
      metric(BeautyQSearchEvaluationMetricNames.SharedProductionPosture.RouteActivationClaimed, b.routeActivationClaimed.toString),
      metric(BeautyQSearchEvaluationMetricNames.SharedProductionPosture.ServingApprovalClaimed, b.servingApprovalClaimed.toString),
      metric("verdict", summary.verdict),
    )
  }

  private def metric(
    name: String,
    value: String,
  ): M10BeautyQSearchFullClassificationCoverageMetric =
    M10BeautyQSearchFullClassificationCoverageMetric(name, value)
}

object M10BeautyQSearchFullQueryClassificationCoverageScorecardRenderer {

  def renderMarkdown(summary: M10BeautyQSearchFullClassificationCoverageSummary): String = {
    val builder = new StringBuilder

    line(builder, "# M10 BeautyQ Full Query Classification Coverage Scorecard")
    line(builder, "")
    line(builder, s"Offline classification coverage report over the full accepted ${summary.totalQueryCount}-query BeautyQ eval dataset. This is an")
    line(builder, "offline planning/eval artifact only: it is not backend quality evidence, not production readiness, not route")
    line(builder, "activation, and not serving approval. `strategy_intent` values are offline study intents only and are NOT")
    line(builder, "production routes; offline strategy intent is distinct from production routing.")
    line(builder, "")
    line(builder, "## Summary")
    line(builder, "")
    line(builder, s"- dataset_id: ${renderText(summary.datasetId)}")
    line(builder, s"- verdict: ${renderText(summary.verdict)}")
    line(builder, s"- total_query_count: ${summary.totalQueryCount}")
    line(builder, s"- mapped_row_count: ${summary.mappedRowCount}")
    line(builder, s"- mixed_intent_count: ${summary.mixedIntentCount}")
    line(builder, s"- noisy_row_count: ${summary.noisyRowCount}")
    line(builder, s"- unresolved_manual_review_row_count: ${summary.unresolvedManualReviewRowCount}")
    line(builder, s"- accepted_negative_control_exclusion_count: ${summary.acceptedNegativeControlExclusionCount}")
    line(builder, s"- no_op_row_count: ${summary.noOpRowCount}")
    line(builder, s"- backend_candidate_study_intent_count: ${summary.backendCandidateStudyIntentCount}")
    line(builder, s"- m11_backend_candidate_inputs_ready: ${summary.m11BackendCandidateInputsReady}")
    line(builder, "")
    line(builder, "## Category counts")
    line(builder, "")
    line(builder, "| category | count |")
    line(builder, "|---|---|")
    summary.categoryCounts.foreach { case (category, count) =>
      line(builder, s"| ${renderText(category.render)} | $count |")
    }
    line(builder, "")
    line(builder, "## Offline strategy intent counts")
    line(builder, "")
    line(builder, "These are offline study intents only, not production routes.")
    line(builder, "")
    line(builder, "| offline_strategy_intent | count |")
    line(builder, "|---|---|")
    summary.strategyIntentCounts.foreach { case (intent, count) =>
      line(builder, s"| ${renderText(intent.render)} | $count |")
    }
    line(builder, "")
    line(builder, "## Representative anchors")
    line(builder, "")
    line(builder, "| query_id | category | offline_strategy_intent |")
    line(builder, "|---|---|---|")
    summary.anchorRows.foreach { row =>
      line(builder, s"| ${renderText(row.queryId)} | ${renderText(row.category.render)} | ${renderText(row.strategyIntent.render)} |")
    }
    line(builder, "")
    line(builder, "## Accepted negative-control rows")
    line(builder, "")
    line(builder, "Noisy/ambiguous anchors deliberately excluded from backend-candidate study; resolved, not pending.")
    line(builder, "")
    renderRowList(builder, summary.acceptedNegativeControlRows)
    line(builder, "")
    line(builder, "## Unresolved manual-review rows")
    line(builder, "")
    line(builder, "Rows still requiring future manual resolution; empty for the accepted dataset.")
    line(builder, "")
    renderRowList(builder, summary.unresolvedManualReviewRows)
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
    line(builder, "Offline strategy intent is distinct from production routing.")

    builder.result()
  }

  private def renderRowList(builder: StringBuilder, queryIds: List[String]): Unit =
    if (queryIds.isEmpty) line(builder, "(none)")
    else queryIds.foreach(queryId => line(builder, s"- ${renderText(queryId)}"))

  def markdownArtifact(
    filename: String,
    summary: M10BeautyQSearchFullClassificationCoverageSummary,
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
