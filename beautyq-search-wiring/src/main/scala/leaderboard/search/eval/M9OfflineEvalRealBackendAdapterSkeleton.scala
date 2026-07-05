package leaderboard.search.eval

final case class M9OfflineEvalBackendAdapterSkeletonConfig(
  executionMode: M9OfflineEvalBackendExecutionMode,
  candidateSource: CandidateSource,
  suppliedRows: List[M9OfflineEvalBackendQueryResult],
  aggregateMetrics: List[OfflineEvalMetric],
  warnings: List[M9OfflineEvalBackendAdapterSkeletonWarning],
  notConnectedMessage: String,
  requiredMetadata: List[String],
)

object M9OfflineEvalBackendAdapterSkeletonConfig {
  def es(
    suppliedRows: List[M9OfflineEvalBackendQueryResult] = Nil,
    aggregateMetrics: List[OfflineEvalMetric] = Nil,
    warnings: List[M9OfflineEvalBackendAdapterSkeletonWarning] = Nil,
    requiredMetadata: List[String] = Nil,
  ): M9OfflineEvalBackendAdapterSkeletonConfig =
    M9OfflineEvalBackendAdapterSkeletonConfig(
      executionMode = M9OfflineEvalBackendExecutionMode.EsOnlyOffline,
      candidateSource = CandidateSource.Es,
      suppliedRows = suppliedRows,
      aggregateMetrics = aggregateMetrics,
      warnings = warnings,
      notConnectedMessage = "Elasticsearch offline adapter skeleton only; no Elasticsearch client is connected",
      requiredMetadata = requiredMetadata,
    )

  def qdrant(
    suppliedRows: List[M9OfflineEvalBackendQueryResult] = Nil,
    aggregateMetrics: List[OfflineEvalMetric] = Nil,
    warnings: List[M9OfflineEvalBackendAdapterSkeletonWarning] = Nil,
    requiredMetadata: List[String] = Nil,
  ): M9OfflineEvalBackendAdapterSkeletonConfig =
    M9OfflineEvalBackendAdapterSkeletonConfig(
      executionMode = M9OfflineEvalBackendExecutionMode.QdrantOnlyOffline,
      candidateSource = CandidateSource.Qdrant,
      suppliedRows = suppliedRows,
      aggregateMetrics = aggregateMetrics,
      warnings = warnings,
      notConnectedMessage = "Qdrant offline adapter skeleton only; no Qdrant client or embedding/vector execution is connected",
      requiredMetadata = requiredMetadata,
    )
}

final case class M9OfflineEvalBackendAdapterSkeletonWarning(
  code: String,
  message: String,
) {
  def render: String = s"$code: $message"
}

object M9OfflineEvalBackendAdapterSkeletonWarning {
  def latencyMissing(queryId: String): M9OfflineEvalBackendAdapterSkeletonWarning =
    M9OfflineEvalBackendAdapterSkeletonWarning(
      code = "latency_missing",
      message = s"latency metric is absent for query $queryId",
    )

  def offlineOnly(message: String): M9OfflineEvalBackendAdapterSkeletonWarning =
    M9OfflineEvalBackendAdapterSkeletonWarning(
      code = "offline_skeleton_only",
      message = message,
    )

  val ProductionActivationNotApproved: M9OfflineEvalBackendAdapterSkeletonWarning =
    M9OfflineEvalBackendAdapterSkeletonWarning(
      code = "production_activation_not_approved",
      message = "offline adapter skeleton output does not approve production activation",
    )
}

final case class M9OfflineEvalBackendAdapterSkeletonValidation(
  code: String,
  message: String,
)

object M9OfflineEvalBackendAdapterSkeletonValidation {
  def validateConfig(
    config: M9OfflineEvalBackendAdapterSkeletonConfig,
    expectedMode: M9OfflineEvalBackendExecutionMode,
    expectedSource: CandidateSource,
  ): List[M9OfflineEvalBackendAdapterSkeletonValidation] =
    List(
      Option.when(config.executionMode != expectedMode)(validation(
        "execution_mode_mismatch",
        s"skeleton config execution mode ${config.executionMode.render} does not match adapter execution mode ${expectedMode.render}",
      )),
      Option.when(config.candidateSource != expectedSource)(validation(
        "candidate_source_mismatch",
        s"skeleton config candidate source ${config.candidateSource.render} does not match adapter candidate source ${expectedSource.render}",
      )),
      Option.when(config.candidateSource == CandidateSource.Unknown)(validation(
        "candidate_source_missing",
        "skeleton config candidate source must be explicit",
      )),
      Option.when(config.executionMode == M9OfflineEvalBackendExecutionMode.Unknown)(validation(
        "execution_mode_missing",
        "skeleton config execution mode must be explicit",
      )),
      Option.when(config.executionMode == M9OfflineEvalBackendExecutionMode.FutureHybridComparison)(validation(
        "hybrid_adapter_not_implemented",
        "future hybrid comparison is vocabulary only; no hybrid adapter skeleton is implemented",
      )),
    ).flatten ++ validateRows(config, expectedSource)

  def validateRequest(request: M9OfflineEvalBackendRunRequest): List[M9OfflineEvalBackendAdapterSkeletonValidation] =
    List(
      Option.when(request.plan.evalDatasetId.value.trim.isEmpty)(validation(
        "dataset_id_missing",
        "eval dataset id is required",
      )),
      Option.when(request.plan.catalogSnapshotId.value.trim.isEmpty)(validation(
        "catalog_snapshot_id_missing",
        "catalog snapshot id is required",
      )),
    ).flatten ++ request.dataset.queries.zipWithIndex.flatMap { case (query, index) =>
      List(
        Option.when(query.queryId.trim.isEmpty)(validation(
          "query_id_missing",
          s"query ${index + 1} id is required",
        )),
        Option.when(Option(query.queryClass).isEmpty)(validation(
          "query_class_missing",
          s"query ${index + 1} class is required",
        )),
      ).flatten
    }

  private def validateRows(
    config: M9OfflineEvalBackendAdapterSkeletonConfig,
    expectedSource: CandidateSource,
  ): List[M9OfflineEvalBackendAdapterSkeletonValidation] =
    config.suppliedRows.flatMap { row =>
      List(
        Option.when(row.candidateSource != expectedSource)(validation(
          "row_candidate_source_mismatch",
          s"row ${row.queryId} candidate source ${row.candidateSource.render} does not match adapter candidate source ${expectedSource.render}",
        )),
        Option.when(row.servingMode != config.executionMode.servingMode)(validation(
          "row_serving_mode_mismatch",
          s"row ${row.queryId} serving mode ${row.servingMode.render} does not match execution mode ${config.executionMode.render}",
        )),
      ).flatten ++ row.candidates.zipWithIndex.flatMap { case (candidate, index) =>
        Option.when(candidate.candidateSource != expectedSource)(validation(
          "candidate_source_mismatch",
          s"row ${row.queryId} candidate ${index + 1} source ${candidate.candidateSource.render} does not match adapter candidate source ${expectedSource.render}",
        ))
      }
    }

  private def validation(
    code: String,
    message: String,
  ): M9OfflineEvalBackendAdapterSkeletonValidation =
    M9OfflineEvalBackendAdapterSkeletonValidation(code, message)
}

sealed trait M9OfflineEvalRealBackendAdapterSkeleton extends M9OfflineEvalBackendAdapter {
  def candidateSource: CandidateSource

  def config: M9OfflineEvalBackendAdapterSkeletonConfig

  final override def run(request: M9OfflineEvalBackendRunRequest): M9OfflineEvalBackendRunResponse = {
    val configFailures =
      M9OfflineEvalBackendAdapterSkeletonValidation.validateConfig(config, executionMode, candidateSource)
    val requestFailures =
      M9OfflineEvalBackendAdapterSkeletonValidation.validateRequest(request)
    val blockingFailures = configFailures ++ requestFailures
    val suppliedByQuery = config.suppliedRows.groupBy(_.queryId)

    val queryResults = request.dataset.queries.map { query =>
      blockingFailures match {
        case Nil =>
          suppliedByQuery.get(query.queryId) match {
            case Some(row :: Nil) => normalizeRow(row, query)
            case Some(_ :: _ :: _) =>
              failureResult(query, s"multiple supplied skeleton rows for query ${query.queryId}", retryable = false)
            case _ =>
              failureResult(query, config.notConnectedMessage, retryable = false)
          }
        case failures =>
          failureResult(query, failures.map(_.message).mkString("; "), retryable = false)
      }
    }

    M9OfflineEvalBackendRunResponse(
      plan = request.plan,
      dataset = request.dataset,
      queryResults = queryResults,
      aggregateMetrics = config.aggregateMetrics,
      warnings = renderedWarnings,
    )
  }

  private def normalizeRow(
    row: M9OfflineEvalBackendQueryResult,
    query: M9OfflineEvalDatasetQuery,
  ): M9OfflineEvalBackendQueryResult =
    row.copy(
      queryClass = query.queryClass,
      servingMode = executionMode.servingMode,
      candidateSource = candidateSource,
      candidates = row.candidates.map(candidate => candidate.copy(candidateSource = candidateSource)),
      warnings = row.warnings ++ latencyWarnings(row).map(_.render),
    )

  private def failureResult(
    query: M9OfflineEvalDatasetQuery,
    message: String,
    retryable: Boolean,
  ): M9OfflineEvalBackendQueryResult =
    M9OfflineEvalBackendQueryResult(
      queryId = query.queryId,
      queryClass = query.queryClass,
      servingMode = executionMode.servingMode,
      candidateSource = candidateSource,
      candidates = Nil,
      metrics = List(OfflineEvalMetric(
        name = OfflineEvalMetricName.FailureCount,
        value = OfflineEvalMetricValue.Numeric(BigDecimal("1")),
      )),
      regressionStatus = "failed",
      warnings = latencyWarnings(query.queryId).map(_.render),
      failure = Some(M9OfflineEvalBackendFailure(
        queryId = query.queryId,
        candidateSource = candidateSource,
        message = message,
        retryable = retryable,
      )),
    )

  private def latencyWarnings(row: M9OfflineEvalBackendQueryResult): List[M9OfflineEvalBackendAdapterSkeletonWarning] =
    if (row.metrics.exists(_.name == OfflineEvalMetricName.Latency)) {
      Nil
    } else {
      latencyWarnings(row.queryId)
    }

  private def latencyWarnings(queryId: String): List[M9OfflineEvalBackendAdapterSkeletonWarning] =
    List(M9OfflineEvalBackendAdapterSkeletonWarning.latencyMissing(queryId))

  private def renderedWarnings: List[String] =
    (config.warnings ++
      config.requiredMetadata.map(metadata =>
        M9OfflineEvalBackendAdapterSkeletonWarning.offlineOnly(s"required metadata only, not executed: $metadata")
      ) :+
      M9OfflineEvalBackendAdapterSkeletonWarning.ProductionActivationNotApproved).map(_.render)
}

final case class M9OfflineEvalEsOfflineAdapterSkeleton(
  config: M9OfflineEvalBackendAdapterSkeletonConfig,
) extends M9OfflineEvalRealBackendAdapterSkeleton {
  override val executionMode: M9OfflineEvalBackendExecutionMode =
    M9OfflineEvalBackendExecutionMode.EsOnlyOffline

  override val candidateSource: CandidateSource =
    CandidateSource.Es
}

final case class M9OfflineEvalQdrantOfflineAdapterSkeleton(
  config: M9OfflineEvalBackendAdapterSkeletonConfig,
) extends M9OfflineEvalRealBackendAdapterSkeleton {
  override val executionMode: M9OfflineEvalBackendExecutionMode =
    M9OfflineEvalBackendExecutionMode.QdrantOnlyOffline

  override val candidateSource: CandidateSource =
    CandidateSource.Qdrant
}
