package leaderboard.search

import leaderboard.search.eval.{
  CandidateSource,
  CatalogSnapshotId,
  EvalDatasetId,
  ExperimentId,
  M9OfflineEvalBackendAdapterSkeletonConfig,
  M9OfflineEvalBackendAdapterSkeletonValidation,
  M9OfflineEvalBackendCandidate,
  M9OfflineEvalBackendExecutionMode,
  M9OfflineEvalBackendExecutionPlan,
  M9OfflineEvalBackendFailure,
  M9OfflineEvalBackendQueryResult,
  M9OfflineEvalBackendRunError,
  M9OfflineEvalBackendRunRequest,
  M9OfflineEvalBackendRunner,
  M9OfflineEvalDataset,
  M9OfflineEvalDatasetQuery,
  M9OfflineEvalEsOfflineAdapterSkeleton,
  M9OfflineEvalExpectedResult,
  M9OfflineEvalQdrantOfflineAdapterSkeleton,
  M9OfflineEvalStaticRunner,
  M9OfflineEvalStaticRunnerConfig,
  MetricWindow,
  OfflineEvalMetric,
  OfflineEvalMetricName,
  OfflineEvalMetricValue,
  QueryClass,
  RequestId,
  RoutingPolicyId,
  ServingMode,
}
import org.scalatest.wordspec.AnyWordSpec

final class M9OfflineEvalRealBackendAdapterSkeletonSpec extends AnyWordSpec {

  "M9OfflineEvalEsOfflineAdapterSkeleton" should {

    "use ES-only offline mode and ES candidate source" in {
      val adapter = M9OfflineEvalEsOfflineAdapterSkeleton(esConfig(row("q_exact_001", CandidateSource.Es, ServingMode.EsOnly)))
      val result = M9OfflineEvalBackendRunner.run(esRequest, adapter)

      result match {
        case Right(response) =>
          assert(adapter.executionMode == M9OfflineEvalBackendExecutionMode.EsOnlyOffline)
          assert(adapter.candidateSource == CandidateSource.Es)
          assert(response.queryResults.map(_.servingMode) == List(ServingMode.EsOnly, ServingMode.EsOnly))
          assert(response.queryResults.map(_.candidateSource) == List(CandidateSource.Es, CandidateSource.Es))
          assert(response.queryResults.flatMap(_.candidates.map(_.candidateSource)) == List(CandidateSource.Es))
        case Left(error) =>
          fail(s"expected ES skeleton response, got $error")
      }
    }

    "represent not-connected ES execution as backend failure data" in {
      val adapter = M9OfflineEvalEsOfflineAdapterSkeleton(esConfig())
      val result = M9OfflineEvalBackendRunner.run(esRequest, adapter)

      result match {
        case Right(response) =>
          assert(response.queryResults.map(_.failure.map(_.message)) == List(
            Some("Elasticsearch offline adapter skeleton only; no Elasticsearch client is connected"),
            Some("Elasticsearch offline adapter skeleton only; no Elasticsearch client is connected"),
          ))
          assert(response.queryResults.flatMap(_.metrics.map(_.name)) == List(
            OfflineEvalMetricName.FailureCount,
            OfflineEvalMetricName.FailureCount,
          ))
        case Left(error) =>
          fail(s"expected not-connected failures as data, got $error")
      }
    }
  }

  "M9OfflineEvalQdrantOfflineAdapterSkeleton" should {

    "use Qdrant-only offline mode and Qdrant candidate source" in {
      val adapter = M9OfflineEvalQdrantOfflineAdapterSkeleton(qdrantConfig(
        row("q_semantic_001", CandidateSource.Qdrant, ServingMode.QdrantOnly)
      ))
      val result = M9OfflineEvalBackendRunner.run(qdrantRequest, adapter)

      result match {
        case Right(response) =>
          assert(adapter.executionMode == M9OfflineEvalBackendExecutionMode.QdrantOnlyOffline)
          assert(adapter.candidateSource == CandidateSource.Qdrant)
          assert(response.queryResults.map(_.servingMode) == List(ServingMode.QdrantOnly, ServingMode.QdrantOnly))
          assert(response.queryResults.map(_.candidateSource) == List(CandidateSource.Qdrant, CandidateSource.Qdrant))
          assert(response.queryResults.flatMap(_.candidates.map(_.candidateSource)) == List(CandidateSource.Qdrant))
          assert(response.warnings.contains(
            "offline_skeleton_only: required metadata only, not executed: embedding_model_identity"
          ))
        case Left(error) =>
          fail(s"expected Qdrant skeleton response, got $error")
      }
    }

    "represent not-connected Qdrant execution as backend failure data" in {
      val adapter = M9OfflineEvalQdrantOfflineAdapterSkeleton(qdrantConfig())
      val result = M9OfflineEvalBackendRunner.run(qdrantRequest, adapter)

      result match {
        case Right(response) =>
          assert(response.queryResults.map(_.failure.map(_.message)) == List(
            Some("Qdrant offline adapter skeleton only; no Qdrant client or embedding/vector execution is connected"),
            Some("Qdrant offline adapter skeleton only; no Qdrant client or embedding/vector execution is connected"),
          ))
          assert(response.queryResults.flatMap(_.metrics.map(_.name)) == List(
            OfflineEvalMetricName.FailureCount,
            OfflineEvalMetricName.FailureCount,
          ))
        case Left(error) =>
          fail(s"expected not-connected failures as data, got $error")
      }
    }
  }

  "M9OfflineEval real-backend adapter skeleton validation" should {

    "reject candidate source mismatch deterministically" in {
      val config = esConfig(row("q_exact_001", CandidateSource.Qdrant, ServingMode.EsOnly))
      val reasons = M9OfflineEvalBackendAdapterSkeletonValidation
        .validateConfig(config, M9OfflineEvalBackendExecutionMode.EsOnlyOffline, CandidateSource.Es)
        .map(_.message)

      assert(reasons == List(
        "row q_exact_001 candidate source qdrant does not match adapter candidate source es",
        "row q_exact_001 candidate 1 source qdrant does not match adapter candidate source es",
      ))

      val result = M9OfflineEvalBackendRunner.run(esRequest, M9OfflineEvalEsOfflineAdapterSkeleton(config))
      result match {
        case Right(response) =>
          assert(response.queryResults.forall(_.candidates.isEmpty))
          assert(response.queryResults.map(_.failure.map(_.message)) == List(
            Some(reasons.mkString("; ")),
            Some(reasons.mkString("; ")),
          ))
        case Left(error) =>
          fail(s"expected mismatch rejection as failure data, got $error")
      }
    }

    "reject execution mode mismatch deterministically" in {
      val config = esConfig().copy(executionMode = M9OfflineEvalBackendExecutionMode.QdrantOnlyOffline)
      val reasons = M9OfflineEvalBackendAdapterSkeletonValidation
        .validateConfig(config, M9OfflineEvalBackendExecutionMode.EsOnlyOffline, CandidateSource.Es)
        .map(_.message)

      assert(reasons == List(
        "skeleton config execution mode qdrant_only_offline does not match adapter execution mode es_only_offline"
      ))

      val result = M9OfflineEvalBackendRunner.run(esRequest, M9OfflineEvalEsOfflineAdapterSkeleton(config))
      result match {
        case Right(response) =>
          assert(response.queryResults.map(_.failure.map(_.message)) == List(
            Some("skeleton config execution mode qdrant_only_offline does not match adapter execution mode es_only_offline"),
            Some("skeleton config execution mode qdrant_only_offline does not match adapter execution mode es_only_offline"),
          ))
        case Left(error) =>
          fail(s"expected mode mismatch rejection as failure data, got $error")
      }
    }

    "report missing catalog, dataset, and query attribution deterministically" in {
      val invalidRequest = esRequest.copy(
        plan = esPlan.copy(evalDatasetId = EvalDatasetId(""), catalogSnapshotId = CatalogSnapshotId(" ")),
        dataset = dataset.copy(
          evalDatasetId = EvalDatasetId(""),
          catalogSnapshotId = CatalogSnapshotId(" "),
          queries = dataset.queries.map {
            case query if query.queryId == "q_exact_001" => query.copy(queryId = "")
            case query                                   => query
          },
        ),
      )

      assert(M9OfflineEvalBackendAdapterSkeletonValidation.validateRequest(invalidRequest).map(_.message) == List(
        "eval dataset id is required",
        "catalog snapshot id is required",
        "query 1 id is required",
      ))

      val result = M9OfflineEvalBackendRunner.run(invalidRequest, M9OfflineEvalEsOfflineAdapterSkeleton(esConfig()))
      result match {
        case Left(M9OfflineEvalBackendRunError(reasons)) =>
          assert(reasons == List(
            "execution plan eval dataset id is required",
            "execution plan catalog snapshot id is required",
            "dataset query id is required",
          ))
        case other =>
          fail(s"expected shared runner attribution validation failure, got $other")
      }
    }

    "represent latency absence with a warning" in {
      val adapter = M9OfflineEvalEsOfflineAdapterSkeleton(esConfig(
        row("q_exact_001", CandidateSource.Es, ServingMode.EsOnly, includeLatency = false)
      ))
      val result = M9OfflineEvalBackendRunner.run(esRequest, adapter)

      result match {
        case Right(response) =>
          val exactRows = response.queryResults.filter(_.queryId == "q_exact_001")

          exactRows match {
            case exact :: Nil =>
              assert(exact.warnings == List("latency_missing: latency metric is absent for query q_exact_001"))
            case other =>
              fail(s"expected one exact row, got $other")
          }
        case Left(error) =>
          fail(s"expected latency warning response, got $error")
      }
    }

    "produce rows compatible with M9OfflineEvalStaticRunner" in {
      val adapter = M9OfflineEvalEsOfflineAdapterSkeleton(esConfig(
        row("q_exact_001", CandidateSource.Es, ServingMode.EsOnly),
        row("q_semantic_001", CandidateSource.Es, ServingMode.EsOnly),
      ))
      val backendResult = M9OfflineEvalBackendRunner.run(esRequest, adapter)

      backendResult match {
        case Right(response) =>
          val staticResult = M9OfflineEvalStaticRunner.run(
            input = response.toStaticRunInput,
            config = M9OfflineEvalStaticRunnerConfig(markdownFilename = "m9-es-skeleton.md"),
          )

          staticResult match {
            case Right(staticRun) =>
              assert(staticRun.report.metadata.servingMode == ServingMode.EsOnly)
              assert(staticRun.report.metadata.candidateSource == CandidateSource.Es)
              assert(staticRun.report.rows.map(_.queryId) == List("q_exact_001", "q_semantic_001"))
              assert(staticRun.report.warnings.contains(
                "production_activation_not_approved: offline adapter skeleton output does not approve production activation"
              ))
            case Left(error) =>
              fail(s"expected static runner to accept skeleton rows, got $error")
          }
        case Left(error) =>
          fail(s"expected skeleton backend response, got $error")
      }
    }

    "keep route, plugin, DI, HTTP, hybrid, fallback, fusion, and reranking out of the skeleton API" in {
      val adapter = M9OfflineEvalEsOfflineAdapterSkeleton(esConfig())
      val fields =
        adapter.productElementNames.toList ++
          adapter.config.productElementNames.toList ++
          row("q_exact_001", CandidateSource.Es, ServingMode.EsOnly).productElementNames.toList
      val executionTerms = List("route", "plugin", "distage", "module", "http", "docker", "client")

      assert(!fields.exists(field => executionTerms.exists(term => field.toLowerCase.contains(term))))
      assert(!fields.exists(_.toLowerCase.contains("fallback")))
      assert(adapter.config.aggregateMetrics.map(_.name).forall(_ != OfflineEvalMetricName.BackendContributionRatio))
      assert(esRequest.plan.routingPolicyId.render == "m9-es-offline-skeleton-v1")
      assert(esRequest.plan.qualityGateDecision == "not_evaluated")
      assert(esRequest.plan.executionMode != M9OfflineEvalBackendExecutionMode.FutureHybridComparison)
      assert(esRequest.plan.executionMode.servingMode != ServingMode.Hybrid)
    }

    "keep future hybrid comparison as vocabulary only" in {
      val config = esConfig().copy(executionMode = M9OfflineEvalBackendExecutionMode.FutureHybridComparison)
      val reasons = M9OfflineEvalBackendAdapterSkeletonValidation
        .validateConfig(config, M9OfflineEvalBackendExecutionMode.EsOnlyOffline, CandidateSource.Es)
        .map(_.message)

      assert(reasons == List(
        "skeleton config execution mode future_hybrid_comparison does not match adapter execution mode es_only_offline",
        "future hybrid comparison is vocabulary only; no hybrid adapter skeleton is implemented",
      ))
      assert(M9OfflineEvalBackendExecutionMode.FutureHybridComparison.servingMode == ServingMode.Hybrid)
    }
  }

  private def esConfig(
    rows: M9OfflineEvalBackendQueryResult*
  ): M9OfflineEvalBackendAdapterSkeletonConfig =
    M9OfflineEvalBackendAdapterSkeletonConfig.es(
      suppliedRows = rows.toList,
      aggregateMetrics = List(metric(OfflineEvalMetricName.QualityGateDecision, OfflineEvalMetricValue.Text("not_evaluated"))),
    )

  private def qdrantConfig(
    rows: M9OfflineEvalBackendQueryResult*
  ): M9OfflineEvalBackendAdapterSkeletonConfig =
    M9OfflineEvalBackendAdapterSkeletonConfig.qdrant(
      suppliedRows = rows.toList,
      aggregateMetrics = List(metric(OfflineEvalMetricName.QualityGateDecision, OfflineEvalMetricValue.Text("not_evaluated"))),
      requiredMetadata = List("embedding_model_identity", "vector_name", "dimension", "distance"),
    )

  private def esRequest: M9OfflineEvalBackendRunRequest =
    M9OfflineEvalBackendRunRequest(esPlan, dataset)

  private def qdrantRequest: M9OfflineEvalBackendRunRequest =
    M9OfflineEvalBackendRunRequest(qdrantPlan, dataset)

  private def esPlan: M9OfflineEvalBackendExecutionPlan =
    plan(
      executionMode = M9OfflineEvalBackendExecutionMode.EsOnlyOffline,
      routingPolicyId = RoutingPolicyId("m9-es-offline-skeleton-v1"),
    )

  private def qdrantPlan: M9OfflineEvalBackendExecutionPlan =
    plan(
      executionMode = M9OfflineEvalBackendExecutionMode.QdrantOnlyOffline,
      routingPolicyId = RoutingPolicyId("m9-qdrant-offline-skeleton-v1"),
    )

  private def plan(
    executionMode: M9OfflineEvalBackendExecutionMode,
    routingPolicyId: RoutingPolicyId,
  ): M9OfflineEvalBackendExecutionPlan =
    M9OfflineEvalBackendExecutionPlan(
      executionMode = executionMode,
      evalDatasetId = EvalDatasetId("beautyq-m9-real-backend-skeleton-v1"),
      catalogSnapshotId = CatalogSnapshotId("seed-resource-catalog"),
      experimentId = ExperimentId("m9-real-backend-skeleton-spec"),
      routingPolicyId = routingPolicyId,
      metricWindow = MetricWindow("offline-run"),
      requestId = Some(RequestId("request-m9-real-backend-skeleton-001")),
      qualityGateDecision = "not_evaluated",
      generatedAt = "2026-06-20T10:15:30Z",
      notes = List("m9-real-backend-skeleton-spec"),
      warnings = List("offline adapter skeleton only; no route or backend execution"),
    )

  private def dataset: M9OfflineEvalDataset =
    M9OfflineEvalDataset(
      evalDatasetId = EvalDatasetId("beautyq-m9-real-backend-skeleton-v1"),
      catalogSnapshotId = CatalogSnapshotId("seed-resource-catalog"),
      queries = List(
        datasetQuery("q_exact_001", QueryClass.ExactProductNameBrand),
        datasetQuery("q_semantic_001", QueryClass.SemanticDescriptive),
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
      failure = Option.empty[M9OfflineEvalBackendFailure],
    )

  private def metric(
    name: OfflineEvalMetricName,
    value: OfflineEvalMetricValue,
  ): OfflineEvalMetric =
    OfflineEvalMetric(name, value)
}
