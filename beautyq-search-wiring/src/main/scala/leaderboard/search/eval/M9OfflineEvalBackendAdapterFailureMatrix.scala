package leaderboard.search.eval

enum M9OfflineEvalBackendAdapterFailureCategory {
  case Connectivity
  case Attribution
  case ExecutionMode
  case DuplicateRows
  case DatasetAttribution
  case CatalogAttribution
  case QueryAttribution
  case Latency
  case BackendFailure
  case RequiredMetadata
  case HybridVocabulary
  case HiddenFallback
  case ProductionActivation
}

enum M9OfflineEvalBackendAdapterFailureSeverity {
  case Failure
  case Warning
  case Guardrail
}

enum M9OfflineEvalBackendAdapterFailureDisposition {
  case RightFailureRowsAsData
  case RightRowsWithWarnings
  case LeftValidationError
  case VocabularyOnly
}

enum M9OfflineEvalBackendAdapterFailureKind {
  case EsSkeleton
  case QdrantSkeleton

  def expectedMode: M9OfflineEvalBackendExecutionMode =
    this match {
      case EsSkeleton     => M9OfflineEvalBackendExecutionMode.EsOnlyOffline
      case QdrantSkeleton => M9OfflineEvalBackendExecutionMode.QdrantOnlyOffline
    }

  def expectedSource: CandidateSource =
    this match {
      case EsSkeleton     => CandidateSource.Es
      case QdrantSkeleton => CandidateSource.Qdrant
    }
}

final case class M9OfflineEvalBackendAdapterFailureExpectation(
  category: M9OfflineEvalBackendAdapterFailureCategory,
  severity: M9OfflineEvalBackendAdapterFailureSeverity,
  codeOrPhrase: String,
  disposition: M9OfflineEvalBackendAdapterFailureDisposition,
  staticRunnerCompatible: Boolean,
  productionActivationNotApproved: Boolean,
)

final case class M9OfflineEvalBackendAdapterFailureCase(
  caseId: String,
  adapterKind: M9OfflineEvalBackendAdapterFailureKind,
  queryIdOrMutation: String,
  request: M9OfflineEvalBackendRunRequest,
  config: M9OfflineEvalBackendAdapterSkeletonConfig,
  expectation: M9OfflineEvalBackendAdapterFailureExpectation,
) {
  def adapter: M9OfflineEvalRealBackendAdapterSkeleton =
    adapterKind match {
      case M9OfflineEvalBackendAdapterFailureKind.EsSkeleton =>
        M9OfflineEvalEsOfflineAdapterSkeleton(config)
      case M9OfflineEvalBackendAdapterFailureKind.QdrantSkeleton =>
        M9OfflineEvalQdrantOfflineAdapterSkeleton(config)
    }
}

object M9OfflineEvalBackendAdapterFailureMatrix {
  val DatasetId: EvalDatasetId = EvalDatasetId("beautyq-m9-adapter-failure-matrix-v1")
  val CatalogId: CatalogSnapshotId = CatalogSnapshotId("seed-resource-catalog-failure-matrix")
  val GeneratedAt: String = "2026-06-20T00:00:00Z"
  val StaticRunnerFilename: String = "m9-backend-adapter-failure-matrix.md"

  val StableCases: List[M9OfflineEvalBackendAdapterFailureCase] =
    List(
      failureDataCase(
        caseId = "es_not_connected",
        adapterKind = M9OfflineEvalBackendAdapterFailureKind.EsSkeleton,
        queryIdOrMutation = "q_matrix_exact_001",
        config = esConfig(),
        category = M9OfflineEvalBackendAdapterFailureCategory.Connectivity,
        codeOrPhrase = "no Elasticsearch client is connected",
      ),
      failureDataCase(
        caseId = "qdrant_not_connected",
        adapterKind = M9OfflineEvalBackendAdapterFailureKind.QdrantSkeleton,
        queryIdOrMutation = "q_matrix_semantic_001",
        config = qdrantConfig(),
        category = M9OfflineEvalBackendAdapterFailureCategory.Connectivity,
        codeOrPhrase = "no Qdrant client or embedding/vector execution is connected",
      ),
      failureDataCase(
        caseId = "es_candidate_source_mismatch",
        adapterKind = M9OfflineEvalBackendAdapterFailureKind.EsSkeleton,
        queryIdOrMutation = "q_matrix_exact_001",
        config = esConfig(row("q_matrix_exact_001", CandidateSource.Qdrant, ServingMode.EsOnly)),
        category = M9OfflineEvalBackendAdapterFailureCategory.Attribution,
        codeOrPhrase = "candidate source qdrant does not match adapter candidate source es",
      ),
      failureDataCase(
        caseId = "qdrant_candidate_source_mismatch",
        adapterKind = M9OfflineEvalBackendAdapterFailureKind.QdrantSkeleton,
        queryIdOrMutation = "q_matrix_semantic_001",
        config = qdrantConfig(row("q_matrix_semantic_001", CandidateSource.Es, ServingMode.QdrantOnly)),
        category = M9OfflineEvalBackendAdapterFailureCategory.Attribution,
        codeOrPhrase = "candidate source es does not match adapter candidate source qdrant",
      ),
      failureDataCase(
        caseId = "es_execution_mode_mismatch",
        adapterKind = M9OfflineEvalBackendAdapterFailureKind.EsSkeleton,
        queryIdOrMutation = "config.executionMode=qdrant_only_offline",
        config = esConfig().copy(executionMode = M9OfflineEvalBackendExecutionMode.QdrantOnlyOffline),
        category = M9OfflineEvalBackendAdapterFailureCategory.ExecutionMode,
        codeOrPhrase = "execution mode qdrant_only_offline does not match adapter execution mode es_only_offline",
      ),
      failureDataCase(
        caseId = "qdrant_execution_mode_mismatch",
        adapterKind = M9OfflineEvalBackendAdapterFailureKind.QdrantSkeleton,
        queryIdOrMutation = "config.executionMode=es_only_offline",
        config = qdrantConfig().copy(executionMode = M9OfflineEvalBackendExecutionMode.EsOnlyOffline),
        category = M9OfflineEvalBackendAdapterFailureCategory.ExecutionMode,
        codeOrPhrase = "execution mode es_only_offline does not match adapter execution mode qdrant_only_offline",
      ),
      failureDataCase(
        caseId = "duplicate_supplied_row_for_same_query_id",
        adapterKind = M9OfflineEvalBackendAdapterFailureKind.EsSkeleton,
        queryIdOrMutation = "q_matrix_exact_001 duplicated in supplied rows",
        config = esConfig(
          row("q_matrix_exact_001", CandidateSource.Es, ServingMode.EsOnly),
          row("q_matrix_exact_001", CandidateSource.Es, ServingMode.EsOnly),
        ),
        category = M9OfflineEvalBackendAdapterFailureCategory.DuplicateRows,
        codeOrPhrase = "multiple supplied skeleton rows for query q_matrix_exact_001",
      ),
      validationCase(
        caseId = "missing_dataset_id",
        adapterKind = M9OfflineEvalBackendAdapterFailureKind.EsSkeleton,
        queryIdOrMutation = "plan.evalDatasetId is blank",
        request = esRequest.copy(plan = esPlan.copy(evalDatasetId = EvalDatasetId(""))),
        category = M9OfflineEvalBackendAdapterFailureCategory.DatasetAttribution,
        codeOrPhrase = "execution plan eval dataset id is required",
      ),
      validationCase(
        caseId = "missing_catalog_snapshot_id",
        adapterKind = M9OfflineEvalBackendAdapterFailureKind.EsSkeleton,
        queryIdOrMutation = "plan.catalogSnapshotId is blank",
        request = esRequest.copy(plan = esPlan.copy(catalogSnapshotId = CatalogSnapshotId(" "))),
        category = M9OfflineEvalBackendAdapterFailureCategory.CatalogAttribution,
        codeOrPhrase = "execution plan catalog snapshot id is required",
      ),
      validationCase(
        caseId = "missing_query_id",
        adapterKind = M9OfflineEvalBackendAdapterFailureKind.EsSkeleton,
        queryIdOrMutation = "dataset query id is blank",
        request = esRequest.copy(dataset = dataset.copy(
          queries = dataset.queries.map {
            case query if query.queryId == "q_matrix_exact_001" => query.copy(queryId = "")
            case query                                          => query
          }
        )),
        category = M9OfflineEvalBackendAdapterFailureCategory.QueryAttribution,
        codeOrPhrase = "dataset query id is required",
      ),
      warningCase(
        caseId = "missing_latency_metric",
        adapterKind = M9OfflineEvalBackendAdapterFailureKind.EsSkeleton,
        queryIdOrMutation = "q_matrix_exact_001 latency metric omitted",
        config = esConfig(row("q_matrix_exact_001", CandidateSource.Es, ServingMode.EsOnly, includeLatency = false)),
        category = M9OfflineEvalBackendAdapterFailureCategory.Latency,
        codeOrPhrase = "latency_missing",
      ),
      failureDataCase(
        caseId = "backend_failure_row_as_data",
        adapterKind = M9OfflineEvalBackendAdapterFailureKind.EsSkeleton,
        queryIdOrMutation = "q_matrix_exact_001 supplied backend failure row",
        config = esConfig(row("q_matrix_exact_001", CandidateSource.Es, ServingMode.EsOnly).copy(
          candidates = Nil,
          regressionStatus = "failed",
          failure = Some(M9OfflineEvalBackendFailure(
            queryId = "q_matrix_exact_001",
            candidateSource = CandidateSource.Es,
            message = "scripted ES backend failure",
            retryable = false,
          )),
        )),
        category = M9OfflineEvalBackendAdapterFailureCategory.BackendFailure,
        codeOrPhrase = "scripted ES backend failure",
      ),
      warningCase(
        caseId = "qdrant_required_metadata_warning",
        adapterKind = M9OfflineEvalBackendAdapterFailureKind.QdrantSkeleton,
        queryIdOrMutation = "required metadata listed, not executed",
        config = qdrantConfig(
          row("q_matrix_exact_001", CandidateSource.Qdrant, ServingMode.QdrantOnly),
          row("q_matrix_semantic_001", CandidateSource.Qdrant, ServingMode.QdrantOnly),
        ),
        category = M9OfflineEvalBackendAdapterFailureCategory.RequiredMetadata,
        codeOrPhrase = "required metadata only, not executed: embedding_model_identity",
      ),
      vocabularyOnlyCase(
        caseId = "future_hybrid_comparison_vocabulary_only",
        adapterKind = M9OfflineEvalBackendAdapterFailureKind.EsSkeleton,
        queryIdOrMutation = "config.executionMode=future_hybrid_comparison",
        config = esConfig().copy(executionMode = M9OfflineEvalBackendExecutionMode.FutureHybridComparison),
        category = M9OfflineEvalBackendAdapterFailureCategory.HybridVocabulary,
        codeOrPhrase = "future hybrid comparison is vocabulary only",
      ),
      failureDataCase(
        caseId = "hidden_fallback_not_representable",
        adapterKind = M9OfflineEvalBackendAdapterFailureKind.EsSkeleton,
        queryIdOrMutation = "ES row carries Qdrant source instead of a fallback field",
        config = esConfig(row("q_matrix_exact_001", CandidateSource.Qdrant, ServingMode.EsOnly)),
        category = M9OfflineEvalBackendAdapterFailureCategory.HiddenFallback,
        codeOrPhrase = "candidate source qdrant does not match adapter candidate source es",
      ).copy(expectation = expectation(
        category = M9OfflineEvalBackendAdapterFailureCategory.HiddenFallback,
        severity = M9OfflineEvalBackendAdapterFailureSeverity.Guardrail,
        codeOrPhrase = "candidate source qdrant does not match adapter candidate source es",
        disposition = M9OfflineEvalBackendAdapterFailureDisposition.RightFailureRowsAsData,
        staticRunnerCompatible = true,
      )),
      warningCase(
        caseId = "output_must_not_approve_production_activation",
        adapterKind = M9OfflineEvalBackendAdapterFailureKind.EsSkeleton,
        queryIdOrMutation = "response warnings",
        config = esConfig(row("q_matrix_exact_001", CandidateSource.Es, ServingMode.EsOnly)),
        category = M9OfflineEvalBackendAdapterFailureCategory.ProductionActivation,
        codeOrPhrase = "production activation not approved",
      ).copy(expectation = expectation(
        category = M9OfflineEvalBackendAdapterFailureCategory.ProductionActivation,
        severity = M9OfflineEvalBackendAdapterFailureSeverity.Guardrail,
        codeOrPhrase = "production_activation_not_approved",
        disposition = M9OfflineEvalBackendAdapterFailureDisposition.RightRowsWithWarnings,
        staticRunnerCompatible = true,
      )),
    )

  def validationCodes(
    failureCase: M9OfflineEvalBackendAdapterFailureCase
  ): List[M9OfflineEvalBackendAdapterSkeletonValidation] =
    M9OfflineEvalBackendAdapterSkeletonValidation.validateConfig(
      config = failureCase.config,
      expectedMode = failureCase.adapterKind.expectedMode,
      expectedSource = failureCase.adapterKind.expectedSource,
    ) ++ M9OfflineEvalBackendAdapterSkeletonValidation.validateRequest(failureCase.request)

  private def failureDataCase(
    caseId: String,
    adapterKind: M9OfflineEvalBackendAdapterFailureKind,
    queryIdOrMutation: String,
    config: M9OfflineEvalBackendAdapterSkeletonConfig,
    category: M9OfflineEvalBackendAdapterFailureCategory,
    codeOrPhrase: String,
  ): M9OfflineEvalBackendAdapterFailureCase =
    M9OfflineEvalBackendAdapterFailureCase(
      caseId = caseId,
      adapterKind = adapterKind,
      queryIdOrMutation = queryIdOrMutation,
      request = requestFor(adapterKind),
      config = config,
      expectation = expectation(
        category = category,
        severity = M9OfflineEvalBackendAdapterFailureSeverity.Failure,
        codeOrPhrase = codeOrPhrase,
        disposition = M9OfflineEvalBackendAdapterFailureDisposition.RightFailureRowsAsData,
        staticRunnerCompatible = true,
      ),
    )

  private def validationCase(
    caseId: String,
    adapterKind: M9OfflineEvalBackendAdapterFailureKind,
    queryIdOrMutation: String,
    request: M9OfflineEvalBackendRunRequest,
    category: M9OfflineEvalBackendAdapterFailureCategory,
    codeOrPhrase: String,
  ): M9OfflineEvalBackendAdapterFailureCase =
    M9OfflineEvalBackendAdapterFailureCase(
      caseId = caseId,
      adapterKind = adapterKind,
      queryIdOrMutation = queryIdOrMutation,
      request = request,
      config = configFor(adapterKind),
      expectation = expectation(
        category = category,
        severity = M9OfflineEvalBackendAdapterFailureSeverity.Failure,
        codeOrPhrase = codeOrPhrase,
        disposition = M9OfflineEvalBackendAdapterFailureDisposition.LeftValidationError,
        staticRunnerCompatible = false,
      ),
    )

  private def warningCase(
    caseId: String,
    adapterKind: M9OfflineEvalBackendAdapterFailureKind,
    queryIdOrMutation: String,
    config: M9OfflineEvalBackendAdapterSkeletonConfig,
    category: M9OfflineEvalBackendAdapterFailureCategory,
    codeOrPhrase: String,
  ): M9OfflineEvalBackendAdapterFailureCase =
    M9OfflineEvalBackendAdapterFailureCase(
      caseId = caseId,
      adapterKind = adapterKind,
      queryIdOrMutation = queryIdOrMutation,
      request = requestFor(adapterKind),
      config = config,
      expectation = expectation(
        category = category,
        severity = M9OfflineEvalBackendAdapterFailureSeverity.Warning,
        codeOrPhrase = codeOrPhrase,
        disposition = M9OfflineEvalBackendAdapterFailureDisposition.RightRowsWithWarnings,
        staticRunnerCompatible = true,
      ),
    )

  private def vocabularyOnlyCase(
    caseId: String,
    adapterKind: M9OfflineEvalBackendAdapterFailureKind,
    queryIdOrMutation: String,
    config: M9OfflineEvalBackendAdapterSkeletonConfig,
    category: M9OfflineEvalBackendAdapterFailureCategory,
    codeOrPhrase: String,
  ): M9OfflineEvalBackendAdapterFailureCase =
    M9OfflineEvalBackendAdapterFailureCase(
      caseId = caseId,
      adapterKind = adapterKind,
      queryIdOrMutation = queryIdOrMutation,
      request = requestFor(adapterKind),
      config = config,
      expectation = expectation(
        category = category,
        severity = M9OfflineEvalBackendAdapterFailureSeverity.Guardrail,
        codeOrPhrase = codeOrPhrase,
        disposition = M9OfflineEvalBackendAdapterFailureDisposition.VocabularyOnly,
        staticRunnerCompatible = false,
      ),
    )

  private def expectation(
    category: M9OfflineEvalBackendAdapterFailureCategory,
    severity: M9OfflineEvalBackendAdapterFailureSeverity,
    codeOrPhrase: String,
    disposition: M9OfflineEvalBackendAdapterFailureDisposition,
    staticRunnerCompatible: Boolean,
  ): M9OfflineEvalBackendAdapterFailureExpectation =
    M9OfflineEvalBackendAdapterFailureExpectation(
      category = category,
      severity = severity,
      codeOrPhrase = codeOrPhrase,
      disposition = disposition,
      staticRunnerCompatible = staticRunnerCompatible,
      productionActivationNotApproved = true,
    )

  private def requestFor(kind: M9OfflineEvalBackendAdapterFailureKind): M9OfflineEvalBackendRunRequest =
    kind match {
      case M9OfflineEvalBackendAdapterFailureKind.EsSkeleton     => esRequest
      case M9OfflineEvalBackendAdapterFailureKind.QdrantSkeleton => qdrantRequest
    }

  private def configFor(kind: M9OfflineEvalBackendAdapterFailureKind): M9OfflineEvalBackendAdapterSkeletonConfig =
    kind match {
      case M9OfflineEvalBackendAdapterFailureKind.EsSkeleton     => esConfig()
      case M9OfflineEvalBackendAdapterFailureKind.QdrantSkeleton => qdrantConfig()
    }

  private def esRequest: M9OfflineEvalBackendRunRequest =
    M9OfflineEvalBackendRunRequest(esPlan, dataset)

  private def qdrantRequest: M9OfflineEvalBackendRunRequest =
    M9OfflineEvalBackendRunRequest(qdrantPlan, dataset)

  private def esPlan: M9OfflineEvalBackendExecutionPlan =
    plan(
      executionMode = M9OfflineEvalBackendExecutionMode.EsOnlyOffline,
      routingPolicyId = RoutingPolicyId("m9-es-adapter-failure-matrix-v1"),
    )

  private def qdrantPlan: M9OfflineEvalBackendExecutionPlan =
    plan(
      executionMode = M9OfflineEvalBackendExecutionMode.QdrantOnlyOffline,
      routingPolicyId = RoutingPolicyId("m9-qdrant-adapter-failure-matrix-v1"),
    )

  private def plan(
    executionMode: M9OfflineEvalBackendExecutionMode,
    routingPolicyId: RoutingPolicyId,
  ): M9OfflineEvalBackendExecutionPlan =
    M9OfflineEvalBackendExecutionPlan(
      executionMode = executionMode,
      evalDatasetId = DatasetId,
      catalogSnapshotId = CatalogId,
      experimentId = ExperimentId("m9-adapter-failure-matrix"),
      routingPolicyId = routingPolicyId,
      metricWindow = MetricWindow("offline-failure-matrix"),
      requestId = Some(RequestId("request-m9-adapter-failure-matrix-001")),
      qualityGateDecision = "not_evaluated_not_for_activation",
      generatedAt = GeneratedAt,
      notes = List("M9 adapter failure matrix fixture; no backend execution."),
      warnings = List("offline adapter failure matrix only; production activation not approved"),
    )

  private def dataset: M9OfflineEvalDataset =
    M9OfflineEvalDataset(
      evalDatasetId = DatasetId,
      catalogSnapshotId = CatalogId,
      queries = List(
        datasetQuery("q_matrix_exact_001", QueryClass.ExactProductNameBrand),
        datasetQuery("q_matrix_semantic_001", QueryClass.SemanticDescriptive),
      ),
    )

  private def datasetQuery(
    queryId: String,
    queryClass: QueryClass,
  ): M9OfflineEvalDatasetQuery =
    M9OfflineEvalDatasetQuery(
      queryId = queryId,
      rawQueryText = s"raw $queryId",
      normalizedQueryText = Some(s"raw $queryId"),
      queryClass = queryClass,
      filters = Nil,
      categories = Nil,
      expectedResults = List(M9OfflineEvalExpectedResult(resultId = s"expected-$queryId", note = None)),
      expectedNotes = Nil,
      negativeOutOfCatalog = false,
    )

  private def esConfig(
    rows: M9OfflineEvalBackendQueryResult*
  ): M9OfflineEvalBackendAdapterSkeletonConfig =
    M9OfflineEvalBackendAdapterSkeletonConfig.es(
      suppliedRows = rows.toList,
      aggregateMetrics = aggregateMetrics,
    )

  private def qdrantConfig(
    rows: M9OfflineEvalBackendQueryResult*
  ): M9OfflineEvalBackendAdapterSkeletonConfig =
    M9OfflineEvalBackendAdapterSkeletonConfig.qdrant(
      suppliedRows = rows.toList,
      aggregateMetrics = aggregateMetrics,
      requiredMetadata = List("embedding_model_identity", "vector_name", "dimension", "distance"),
    )

  private def row(
    queryId: String,
    candidateSource: CandidateSource,
    servingMode: ServingMode,
    includeLatency: Boolean = true,
  ): M9OfflineEvalBackendQueryResult =
    M9OfflineEvalBackendQueryResult(
      queryId = queryId,
      queryClass = QueryClass.SemanticDescriptive,
      servingMode = servingMode,
      candidateSource = candidateSource,
      candidates = List(M9OfflineEvalBackendCandidate(
        resultId = s"variant-$queryId",
        candidateSource = candidateSource,
        rank = 1,
        score = Some(BigDecimal("0.42")),
        notes = Nil,
      )),
      metrics =
        List(metric(OfflineEvalMetricName.RegressionPassFail, OfflineEvalMetricValue.Text("sample"))) ++
          Option.when(includeLatency)(metric(OfflineEvalMetricName.Latency, OfflineEvalMetricValue.Numeric(BigDecimal("12.5")))),
      regressionStatus = "sample",
      warnings = Nil,
      failure = None,
    )

  private def aggregateMetrics: List[OfflineEvalMetric] =
    List(metric(OfflineEvalMetricName.QualityGateDecision, OfflineEvalMetricValue.Text("not_evaluated_not_for_activation")))

  private def metric(
    name: OfflineEvalMetricName,
    value: OfflineEvalMetricValue,
  ): OfflineEvalMetric =
    OfflineEvalMetric(name, value)
}
