package leaderboard.search.eval

final case class M9OfflineEvalReportFormatVersion(value: String) extends AnyVal {
  def render: String = value
}

object M9OfflineEvalReportFormatVersion {
  val Current: M9OfflineEvalReportFormatVersion = M9OfflineEvalReportFormatVersion("m9-offline-eval-saved-report-v1")
}

final case class M9OfflineEvalExpectedResult(
  resultId: String,
  note: Option[String],
)

final case class M9OfflineEvalDatasetQuery(
  queryId: String,
  rawQueryText: String,
  normalizedQueryText: Option[String],
  queryClass: QueryClass,
  filters: List[String],
  categories: List[String],
  expectedResults: List[M9OfflineEvalExpectedResult],
  expectedNotes: List[String],
  negativeOutOfCatalog: Boolean,
)

final case class M9OfflineEvalDataset(
  evalDatasetId: EvalDatasetId,
  catalogSnapshotId: CatalogSnapshotId,
  queries: List[M9OfflineEvalDatasetQuery],
)

final case class M9OfflineEvalReportRow(
  queryId: String,
  queryClass: QueryClass,
  servingMode: ServingMode,
  candidateSource: CandidateSource,
  topKResultIds: List[String],
  metrics: List[OfflineEvalMetric],
  regressionStatus: String,
  warnings: List[String],
)

final case class M9OfflineEvalSavedReport(
  formatVersion: M9OfflineEvalReportFormatVersion,
  metadata: OfflineEvalRunMetadata,
  evalDatasetId: EvalDatasetId,
  catalogSnapshotId: CatalogSnapshotId,
  generatedAt: String,
  rows: List[M9OfflineEvalReportRow],
  aggregateMetrics: List[OfflineEvalMetric],
  qualityGateDecision: String,
  notes: List[String],
  warnings: List[String],
)

final case class M9OfflineEvalReportArtifact(
  filename: String,
  contentType: String,
  contents: String,
)

object M9OfflineEvalReportRenderer {
  def renderMarkdown(report: M9OfflineEvalSavedReport): String = {
    val builder = new StringBuilder

    line(builder, "# M9 Offline Eval Saved Report")
    line(builder, "")
    line(builder, "This artifact is a saved offline-eval format/rendering record. Backend execution is not represented by this artifact.")
    line(builder, "")
    line(builder, "## Format")
    line(builder, "")
    line(builder, s"- format_version: ${report.formatVersion.render}")
    line(builder, s"- generated_at: ${renderText(report.generatedAt)}")
    line(builder, s"- eval_dataset_id: ${report.evalDatasetId.render}")
    line(builder, s"- catalog_snapshot_id: ${report.catalogSnapshotId.render}")
    line(builder, s"- quality_gate_decision: ${renderText(report.qualityGateDecision)}")
    line(builder, "")
    line(builder, "## Metadata")
    line(builder, "")
    line(builder, s"- serving_mode: ${report.metadata.servingMode.render}")
    line(builder, s"- candidate_source: ${report.metadata.candidateSource.render}")
    line(builder, s"- routing_policy_id: ${report.metadata.routingPolicyId.render}")
    line(builder, s"- fusion_policy: ${report.metadata.fusionPolicy.render}")
    line(builder, s"- reranker_policy: ${report.metadata.rerankerPolicy.render}")
    line(builder, s"- experiment_id: ${report.metadata.experimentId.render}")
    line(builder, s"- metadata_catalog_snapshot_id: ${report.metadata.catalogSnapshotId.render}")
    line(builder, s"- metadata_eval_dataset_id: ${report.metadata.evalDatasetId.render}")
    line(builder, s"- metric_window: ${report.metadata.metricWindow.render}")
    renderListSection(builder, "Notes", report.notes)
    renderListSection(builder, "Warnings", report.warnings)
    renderMetricsSection(builder, "Aggregate Metrics", report.aggregateMetrics)
    renderRows(builder, report.rows)

    builder.result()
  }

  def markdownArtifact(
    filename: String,
    report: M9OfflineEvalSavedReport,
  ): M9OfflineEvalReportArtifact =
    M9OfflineEvalReportArtifact(
      filename = filename,
      contentType = "text/markdown; charset=utf-8",
      contents = renderMarkdown(report),
    )

  private def renderListSection(
    builder: StringBuilder,
    title: String,
    values: List[String],
  ): Unit = {
    line(builder, "")
    line(builder, s"## $title")
    line(builder, "")
    values match {
      case Nil => line(builder, "- -")
      case _   => values.foreach(value => line(builder, s"- ${renderText(value)}"))
    }
  }

  private def renderMetricsSection(
    builder: StringBuilder,
    title: String,
    metrics: List[OfflineEvalMetric],
  ): Unit = {
    line(builder, "")
    line(builder, s"## $title")
    line(builder, "")
    line(builder, "| metric | value |")
    line(builder, "|---|---|")
    metrics match {
      case Nil =>
        line(builder, "| - | - |")
      case _ =>
        metrics.foreach { metric =>
          line(builder, s"| ${metric.name.render} | ${renderText(metric.value.render)} |")
        }
    }
  }

  private def renderRows(
    builder: StringBuilder,
    rows: List[M9OfflineEvalReportRow],
  ): Unit = {
    line(builder, "")
    line(builder, "## Query Rows")
    line(builder, "")
    line(builder, "| query_id | query_class | serving_mode | candidate_source | top_k_result_ids | metrics | regression_status | warnings |")
    line(builder, "|---|---|---|---|---|---|---|---|")
    rows match {
      case Nil =>
        line(builder, "| - | - | - | - | - | - | - | - |")
      case _ =>
        rows.foreach { row =>
          line(
            builder,
            s"| ${renderText(row.queryId)} | ${row.queryClass.render} | ${row.servingMode.render} | ${row.candidateSource.render} | ${renderValues(row.topKResultIds)} | ${renderMetrics(row.metrics)} | ${renderText(row.regressionStatus)} | ${renderValues(row.warnings)} |",
          )
        }
    }
  }

  private def renderMetrics(metrics: List[OfflineEvalMetric]): String =
    metrics match {
      case Nil => "-"
      case _   => metrics.map(metric => s"${metric.name.render}=${renderText(metric.value.render)}").mkString("; ")
    }

  private def renderValues(values: List[String]): String =
    values match {
      case Nil => "-"
      case _   => values.map(renderText).mkString(", ")
    }

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
