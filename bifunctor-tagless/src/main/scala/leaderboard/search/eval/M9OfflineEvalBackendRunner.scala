package leaderboard.search.eval

enum M9OfflineEvalBackendExecutionMode {
  case EsOnlyOffline
  case QdrantOnlyOffline
  case FutureHybridComparison
  case ManualStaticSample
  case Unknown

  def render: String =
    this match {
      case M9OfflineEvalBackendExecutionMode.EsOnlyOffline          => "es_only_offline"
      case M9OfflineEvalBackendExecutionMode.QdrantOnlyOffline      => "qdrant_only_offline"
      case M9OfflineEvalBackendExecutionMode.FutureHybridComparison => "future_hybrid_comparison"
      case M9OfflineEvalBackendExecutionMode.ManualStaticSample     => "manual_static_sample"
      case M9OfflineEvalBackendExecutionMode.Unknown                => "unknown"
    }

  def servingMode: ServingMode =
    this match {
      case M9OfflineEvalBackendExecutionMode.EsOnlyOffline          => ServingMode.EsOnly
      case M9OfflineEvalBackendExecutionMode.QdrantOnlyOffline      => ServingMode.QdrantOnly
      case M9OfflineEvalBackendExecutionMode.FutureHybridComparison => ServingMode.Hybrid
      case M9OfflineEvalBackendExecutionMode.ManualStaticSample     => ServingMode.Unknown
      case M9OfflineEvalBackendExecutionMode.Unknown                => ServingMode.Unknown
    }
}

object M9OfflineEvalBackendExecutionMode {
  val plannedVocabulary: List[M9OfflineEvalBackendExecutionMode] = List(
    EsOnlyOffline,
    QdrantOnlyOffline,
    FutureHybridComparison,
    ManualStaticSample,
    Unknown,
  )
}

final case class M9OfflineEvalBackendExecutionPlan(
  executionMode: M9OfflineEvalBackendExecutionMode,
  evalDatasetId: EvalDatasetId,
  catalogSnapshotId: CatalogSnapshotId,
  experimentId: ExperimentId,
  routingPolicyId: RoutingPolicyId,
  metricWindow: MetricWindow,
  requestId: Option[RequestId],
  qualityGateDecision: String,
  generatedAt: String,
  notes: List[String],
  warnings: List[String],
)

object M9OfflineEvalBackendExecutionPlan {
  def validate(plan: M9OfflineEvalBackendExecutionPlan): List[String] =
    List(
      Option.when(plan.executionMode == M9OfflineEvalBackendExecutionMode.Unknown)("execution plan mode must be explicit"),
      Option.when(plan.evalDatasetId.value.trim.isEmpty)("execution plan eval dataset id is required"),
      Option.when(plan.catalogSnapshotId.value.trim.isEmpty)("execution plan catalog snapshot id is required"),
    ).flatten
}

final case class M9OfflineEvalBackendRunRequest(
  plan: M9OfflineEvalBackendExecutionPlan,
  dataset: M9OfflineEvalDataset,
)

final case class M9OfflineEvalBackendQueryRequest(
  requestId: Option[RequestId],
  queryId: String,
  rawQueryText: String,
  normalizedQueryText: Option[String],
  queryClass: QueryClass,
  filters: List[String],
  categories: List[String],
  expectedResults: List[M9OfflineEvalExpectedResult],
  negativeOutOfCatalog: Boolean,
)

object M9OfflineEvalBackendQueryRequest {
  def from(
    requestId: Option[RequestId],
    query: M9OfflineEvalDatasetQuery,
  ): M9OfflineEvalBackendQueryRequest =
    M9OfflineEvalBackendQueryRequest(
      requestId = requestId,
      queryId = query.queryId,
      rawQueryText = query.rawQueryText,
      normalizedQueryText = query.normalizedQueryText,
      queryClass = query.queryClass,
      filters = query.filters,
      categories = query.categories,
      expectedResults = query.expectedResults,
      negativeOutOfCatalog = query.negativeOutOfCatalog,
    )
}

final case class M9OfflineEvalBackendCandidate(
  resultId: String,
  candidateSource: CandidateSource,
  rank: Int,
  score: Option[BigDecimal],
  notes: List[String],
)

final case class M9OfflineEvalBackendFailure(
  queryId: String,
  candidateSource: CandidateSource,
  message: String,
  retryable: Boolean,
)

final case class M9OfflineEvalBackendQueryResult(
  queryId: String,
  queryClass: QueryClass,
  servingMode: ServingMode,
  candidateSource: CandidateSource,
  candidates: List[M9OfflineEvalBackendCandidate],
  metrics: List[OfflineEvalMetric],
  regressionStatus: String,
  warnings: List[String],
  failure: Option[M9OfflineEvalBackendFailure],
) {
  def toReportRow: M9OfflineEvalReportRow =
    M9OfflineEvalReportRow(
      queryId = queryId,
      queryClass = queryClass,
      servingMode = servingMode,
      candidateSource = candidateSource,
      topKResultIds = candidates.sortBy(_.rank).map(_.resultId),
      metrics = metricsWithFailure,
      regressionStatus = regressionStatus,
      warnings = failure.fold(warnings)(value => warnings :+ s"backend failure: ${value.message}"),
    )

  private def metricsWithFailure: List[OfflineEvalMetric] =
    failure match {
      case Some(_) =>
        metrics :+ OfflineEvalMetric(OfflineEvalMetricName.FailureCount, OfflineEvalMetricValue.Numeric(BigDecimal("1")))
      case None =>
        metrics
    }
}

object M9OfflineEvalBackendQueryResult {
  def validate(result: M9OfflineEvalBackendQueryResult): List[String] =
    List(
      Option.when(result.queryId.trim.isEmpty)("backend query result query id is required"),
      Option.when(result.candidateSource == CandidateSource.Unknown)("backend query result candidate source must be explicit"),
    ).flatten ++ result.candidates.zipWithIndex.flatMap { case (candidate, index) =>
      List(
        Option.when(candidate.resultId.trim.isEmpty)(s"backend candidate ${index + 1} result id is required"),
        Option.when(candidate.candidateSource == CandidateSource.Unknown)(s"backend candidate ${index + 1} source must be explicit"),
      ).flatten
    }
}

final case class M9OfflineEvalBackendRunResponse(
  plan: M9OfflineEvalBackendExecutionPlan,
  dataset: M9OfflineEvalDataset,
  queryResults: List[M9OfflineEvalBackendQueryResult],
  aggregateMetrics: List[OfflineEvalMetric],
  warnings: List[String],
) {
  def rows: List[M9OfflineEvalReportRow] =
    queryResults.map(_.toReportRow)

  def metadata: OfflineEvalRunMetadata =
    OfflineEvalRunMetadata(
      servingMode = plan.executionMode.servingMode,
      candidateSource = dominantCandidateSource,
      routingPolicyId = plan.routingPolicyId,
      fusionPolicy = FusionPolicy.None,
      rerankerPolicy = RerankerPolicy.None,
      experimentId = plan.experimentId,
      catalogSnapshotId = plan.catalogSnapshotId,
      evalDatasetId = plan.evalDatasetId,
      metricWindow = plan.metricWindow,
    )

  def toStaticRunInput: M9OfflineEvalStaticRunInput =
    M9OfflineEvalStaticRunInput(
      dataset = dataset,
      metadata = metadata,
      reportEvalDatasetId = plan.evalDatasetId,
      reportCatalogSnapshotId = plan.catalogSnapshotId,
      rows = rows,
      aggregateMetrics = aggregateMetrics,
      qualityGateDecision = plan.qualityGateDecision,
      notes = plan.notes,
      warnings = plan.warnings ++ warnings,
      generatedAt = plan.generatedAt,
    )

  private def dominantCandidateSource: CandidateSource =
    queryResults.map(_.candidateSource).distinct match {
      case single :: Nil => single
      case Nil           => CandidateSource.Unknown
      case _             => CandidateSource.Unknown
    }
}

final case class M9OfflineEvalBackendRunError(
  reasons: List[String],
)

trait M9OfflineEvalBackendAdapter {
  def executionMode: M9OfflineEvalBackendExecutionMode

  def run(request: M9OfflineEvalBackendRunRequest): M9OfflineEvalBackendRunResponse
}

object M9OfflineEvalBackendRunner {
  def run(
    request: M9OfflineEvalBackendRunRequest,
    adapter: M9OfflineEvalBackendAdapter,
  ): Either[M9OfflineEvalBackendRunError, M9OfflineEvalBackendRunResponse] = {
    val reasons = validateRequest(request, adapter)

    reasons match {
      case Nil =>
        val response = adapter.run(request)
        validateResponse(response) match {
          case Nil     => Right(response)
          case reasons => Left(M9OfflineEvalBackendRunError(reasons))
        }
      case reasons => Left(M9OfflineEvalBackendRunError(reasons))
    }
  }

  private def validateRequest(
    request: M9OfflineEvalBackendRunRequest,
    adapter: M9OfflineEvalBackendAdapter,
  ): List[String] = {
    val datasetIds = request.dataset.queries.map(_.queryId)

    M9OfflineEvalBackendExecutionPlan.validate(request.plan) ++
      List(
        Option.when(request.plan.executionMode != adapter.executionMode)(
          s"adapter execution mode ${adapter.executionMode.render} does not match plan execution mode ${request.plan.executionMode.render}"
        ),
        Option.when(request.plan.evalDatasetId != request.dataset.evalDatasetId)(
          s"execution plan eval dataset id ${request.plan.evalDatasetId.render} does not match dataset eval dataset id ${request.dataset.evalDatasetId.render}"
        ),
        Option.when(request.plan.catalogSnapshotId != request.dataset.catalogSnapshotId)(
          s"execution plan catalog snapshot id ${request.plan.catalogSnapshotId.render} does not match dataset catalog snapshot id ${request.dataset.catalogSnapshotId.render}"
        ),
      ).flatten ++
      duplicates(datasetIds).map(queryId => s"duplicate dataset query id: $queryId") ++
      datasetIds.filter(_.trim.isEmpty).map(_ => "dataset query id is required")
  }

  private def validateResponse(response: M9OfflineEvalBackendRunResponse): List[String] =
    response.queryResults.flatMap(M9OfflineEvalBackendQueryResult.validate)

  private def duplicates(values: List[String]): List[String] = {
    val counts = values.groupMapReduce(identity)(_ => 1)(_ + _)
    values.filter(value => counts.get(value).exists(_ > 1)).distinct
  }
}

final case class M9OfflineEvalStaticBackendAdapter(
  scriptedResults: List[M9OfflineEvalBackendQueryResult],
  aggregateMetrics: List[OfflineEvalMetric],
  warnings: List[String],
) extends M9OfflineEvalBackendAdapter {
  override val executionMode: M9OfflineEvalBackendExecutionMode =
    M9OfflineEvalBackendExecutionMode.ManualStaticSample

  override def run(request: M9OfflineEvalBackendRunRequest): M9OfflineEvalBackendRunResponse = {
    val queryResults = request.dataset.queries.map { query =>
      scriptedResults.find(_.queryId == query.queryId) match {
        case Some(result) => result
        case None =>
          M9OfflineEvalBackendQueryResult(
            queryId = query.queryId,
            queryClass = query.queryClass,
            servingMode = ServingMode.Unknown,
            candidateSource = CandidateSource.Manual,
            candidates = Nil,
            metrics = Nil,
            regressionStatus = "failed",
            warnings = List("missing scripted static backend result"),
            failure = Some(M9OfflineEvalBackendFailure(
              queryId = query.queryId,
              candidateSource = CandidateSource.Manual,
              message = "missing scripted static backend result",
              retryable = false,
            )),
          )
      }
    }

    M9OfflineEvalBackendRunResponse(
      plan = request.plan,
      dataset = request.dataset,
      queryResults = queryResults,
      aggregateMetrics = aggregateMetrics,
      warnings = warnings,
    )
  }
}
