package leaderboard.search.eval

final case class M9OfflineEvalStaticRunnerConfig(
  markdownFilename: String,
)

final case class M9OfflineEvalStaticRunInput(
  dataset: M9OfflineEvalDataset,
  metadata: OfflineEvalRunMetadata,
  reportEvalDatasetId: EvalDatasetId,
  reportCatalogSnapshotId: CatalogSnapshotId,
  rows: List[M9OfflineEvalReportRow],
  aggregateMetrics: List[OfflineEvalMetric],
  qualityGateDecision: String,
  notes: List[String],
  warnings: List[String],
  generatedAt: String,
)

final case class M9OfflineEvalQualityGateSummary(
  totalRows: Int,
  passedRows: Int,
  reviewRows: Int,
  failedRows: Int,
  warningRows: Int,
  emptyTopKRows: Int,
  qualityGateDecision: String,
  hasNegativeOutOfCatalogQuery: Boolean,
)

final case class M9OfflineEvalStaticRunResult(
  report: M9OfflineEvalSavedReport,
  markdownArtifact: M9OfflineEvalReportArtifact,
  qualityGateSummary: M9OfflineEvalQualityGateSummary,
)

final case class M9OfflineEvalStaticRunError(
  reasons: List[String],
)

object M9OfflineEvalStaticRunner {

  def run(
    input: M9OfflineEvalStaticRunInput,
    config: M9OfflineEvalStaticRunnerConfig,
  ): Either[M9OfflineEvalStaticRunError, M9OfflineEvalStaticRunResult] = {
    val validationReasons = validate(input)

    validationReasons match {
      case Nil =>
        val report = M9OfflineEvalSavedReport(
          formatVersion = M9OfflineEvalReportFormatVersion.Current,
          metadata = input.metadata,
          evalDatasetId = input.reportEvalDatasetId,
          catalogSnapshotId = input.reportCatalogSnapshotId,
          generatedAt = input.generatedAt,
          rows = input.rows,
          aggregateMetrics = input.aggregateMetrics,
          qualityGateDecision = input.qualityGateDecision,
          notes = input.notes,
          warnings = input.warnings,
        )
        Right(M9OfflineEvalStaticRunResult(
          report = report,
          markdownArtifact = M9OfflineEvalReportRenderer.markdownArtifact(config.markdownFilename, report),
          qualityGateSummary = summarize(input),
        ))
      case reasons =>
        Left(M9OfflineEvalStaticRunError(reasons))
    }
  }

  def summarize(input: M9OfflineEvalStaticRunInput): M9OfflineEvalQualityGateSummary =
    M9OfflineEvalQualityGateSummary(
      totalRows = input.rows.size,
      passedRows = countStatus(input.rows, Set("passed", "pass")),
      reviewRows = countStatus(input.rows, Set("review")),
      failedRows = countStatus(input.rows, Set("failed", "fail")),
      warningRows = input.rows.count(_.warnings.nonEmpty),
      emptyTopKRows = input.rows.count(_.topKResultIds.isEmpty),
      qualityGateDecision = input.qualityGateDecision,
      hasNegativeOutOfCatalogQuery =
        input.dataset.queries.exists(_.queryClass == QueryClass.NegativeOutOfCatalog) ||
          input.rows.exists(_.queryClass == QueryClass.NegativeOutOfCatalog),
    )

  private def validate(input: M9OfflineEvalStaticRunInput): List[String] = {
    val datasetIds = input.dataset.queries.map(_.queryId)
    val rowIds = input.rows.map(_.queryId)
    val knownDatasetIds = datasetIds.toSet

    val idReasons = List(
      Option.when(input.metadata.evalDatasetId != input.dataset.evalDatasetId)(
        s"metadata eval dataset id ${input.metadata.evalDatasetId.render} does not match dataset eval dataset id ${input.dataset.evalDatasetId.render}"
      ),
      Option.when(input.reportEvalDatasetId != input.dataset.evalDatasetId)(
        s"report eval dataset id ${input.reportEvalDatasetId.render} does not match dataset eval dataset id ${input.dataset.evalDatasetId.render}"
      ),
      Option.when(input.metadata.catalogSnapshotId != input.dataset.catalogSnapshotId)(
        s"metadata catalog snapshot id ${input.metadata.catalogSnapshotId.render} does not match dataset catalog snapshot id ${input.dataset.catalogSnapshotId.render}"
      ),
      Option.when(input.reportCatalogSnapshotId != input.dataset.catalogSnapshotId)(
        s"report catalog snapshot id ${input.reportCatalogSnapshotId.render} does not match dataset catalog snapshot id ${input.dataset.catalogSnapshotId.render}"
      ),
    ).flatten

    val duplicateDatasetReasons =
      duplicates(datasetIds).map(queryId => s"duplicate dataset query id: $queryId")

    val duplicateRowReasons =
      duplicates(rowIds).map(queryId => s"duplicate row query id: $queryId")

    val unknownRowReasons =
      rowIds.filterNot(knownDatasetIds.contains).distinct.map(queryId => s"row references unknown dataset query id: $queryId")

    idReasons ++ duplicateDatasetReasons ++ duplicateRowReasons ++ unknownRowReasons
  }

  private def countStatus(
    rows: List[M9OfflineEvalReportRow],
    expected: Set[String],
  ): Int =
    rows.count(row => expected.contains(row.regressionStatus.trim.toLowerCase))

  private def duplicates(values: List[String]): List[String] = {
    val counts = values.groupMapReduce(identity)(_ => 1)(_ + _)
    values.filter(value => counts.get(value).exists(_ > 1)).distinct
  }
}
