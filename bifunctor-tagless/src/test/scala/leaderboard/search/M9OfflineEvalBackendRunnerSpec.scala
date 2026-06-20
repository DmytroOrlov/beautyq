package leaderboard.search

import leaderboard.search.eval.{
  CandidateSource,
  CatalogSnapshotId,
  EvalDatasetId,
  ExperimentId,
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
  M9OfflineEvalExpectedResult,
  M9OfflineEvalStaticBackendAdapter,
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

final class M9OfflineEvalBackendRunnerSpec extends AnyWordSpec {

  "M9OfflineEvalBackendExecutionMode" should {

    "define ES-only, Qdrant-only, future hybrid, manual/static, and unknown vocabulary only" in {
      assert(M9OfflineEvalBackendExecutionMode.plannedVocabulary.map(_.render) == List(
        "es_only_offline",
        "qdrant_only_offline",
        "future_hybrid_comparison",
        "manual_static_sample",
        "unknown",
      ))
      assert(M9OfflineEvalBackendExecutionMode.EsOnlyOffline.servingMode == ServingMode.EsOnly)
      assert(M9OfflineEvalBackendExecutionMode.QdrantOnlyOffline.servingMode == ServingMode.QdrantOnly)
      assert(M9OfflineEvalBackendExecutionMode.FutureHybridComparison.servingMode == ServingMode.Hybrid)
      assert(M9OfflineEvalBackendExecutionMode.ManualStaticSample.servingMode == ServingMode.Unknown)
    }
  }

  "M9OfflineEvalStaticBackendAdapter" should {

    "declare manual/static execution without claiming ES, Qdrant, or hybrid execution" in {
      val adapter = staticAdapter(staticResult("q_semantic_001"))

      assert(adapter.executionMode == M9OfflineEvalBackendExecutionMode.ManualStaticSample)
      assert(adapter.executionMode != M9OfflineEvalBackendExecutionMode.EsOnlyOffline)
      assert(adapter.executionMode != M9OfflineEvalBackendExecutionMode.QdrantOnlyOffline)
      assert(adapter.executionMode != M9OfflineEvalBackendExecutionMode.FutureHybridComparison)
    }

    "preserve dataset query order while returning caller-supplied candidate data" in {
      val request = runRequest
      val adapter = staticAdapter(
        staticResult("q_negative_001", queryClass = QueryClass.NegativeOutOfCatalog, topK = Nil),
        staticResult("q_exact_001", queryClass = QueryClass.ExactProductNameBrand, topK = List("variant-exact")),
        staticResult("q_semantic_001", queryClass = QueryClass.SemanticDescriptive, topK = List("variant-semantic")),
      )

      val result = M9OfflineEvalBackendRunner.run(request, adapter)

      result match {
        case Right(response) =>
          assert(response.queryResults.map(_.queryId) == List("q_exact_001", "q_semantic_001", "q_negative_001"))
          assert(response.rows.map(_.queryId) == List("q_exact_001", "q_semantic_001", "q_negative_001"))
          assert(response.rows.flatMap(_.topKResultIds) == List("variant-exact", "variant-semantic"))
        case Left(error) =>
          fail(s"expected static backend response, got validation error $error")
      }
    }

    "represent backend failures as data and convert them into report-row warnings and metrics" in {
      val request = runRequest
      val failure = M9OfflineEvalBackendFailure(
        queryId = "q_semantic_001",
        candidateSource = CandidateSource.Manual,
        message = "scripted failure",
        retryable = false,
      )
      val adapter = staticAdapter(
        staticResult("q_exact_001", queryClass = QueryClass.ExactProductNameBrand),
        staticResult("q_semantic_001", queryClass = QueryClass.SemanticDescriptive, topK = Nil).copy(
          regressionStatus = "failed",
          failure = Some(failure),
        ),
        staticResult("q_negative_001", queryClass = QueryClass.NegativeOutOfCatalog, topK = Nil),
      )

      val result = M9OfflineEvalBackendRunner.run(request, adapter)

      result match {
        case Right(response) =>
          val semanticRows = response.rows.filter(_.queryId == "q_semantic_001")

          semanticRows match {
            case row :: Nil =>
              assert(row.topKResultIds == Nil)
              assert(row.warnings == List("backend failure: scripted failure"))
              assert(row.metrics.map(_.name).contains(OfflineEvalMetricName.FailureCount))
            case other =>
              fail(s"expected one semantic row, got $other")
          }
        case Left(error) =>
          fail(s"expected static backend response, got validation error $error")
      }
    }

    "produce rows that can feed M9OfflineEvalStaticRunner" in {
      val adapter = staticAdapter(
        staticResult("q_exact_001", queryClass = QueryClass.ExactProductNameBrand, topK = List("variant-exact")),
        staticResult("q_semantic_001", queryClass = QueryClass.SemanticDescriptive, topK = List("variant-semantic")),
        staticResult("q_negative_001", queryClass = QueryClass.NegativeOutOfCatalog, topK = Nil),
      )

      val backendResult = M9OfflineEvalBackendRunner.run(runRequest, adapter)

      backendResult match {
        case Right(response) =>
          val staticResult = M9OfflineEvalStaticRunner.run(
            input = response.toStaticRunInput,
            config = M9OfflineEvalStaticRunnerConfig(markdownFilename = "m9-backend-interface-static.md"),
          )

          staticResult match {
            case Right(staticRun) =>
              assert(staticRun.report.rows.map(_.queryId) == List("q_exact_001", "q_semantic_001", "q_negative_001"))
              assert(staticRun.report.metadata.servingMode == ServingMode.Unknown)
              assert(staticRun.report.metadata.candidateSource == CandidateSource.Manual)
              assert(staticRun.markdownArtifact.contents.contains("m9-backend-interface-spec"))
            case Left(error) =>
              fail(s"expected static runner to accept backend rows, got $error")
          }
        case Left(error) =>
          fail(s"expected static backend response, got validation error $error")
      }
    }
  }

  "M9OfflineEvalBackendRunner.run" should {

    "report missing dataset and catalog ids deterministically" in {
      val request = runRequest.copy(
        plan = plan.copy(
          evalDatasetId = EvalDatasetId(""),
          catalogSnapshotId = CatalogSnapshotId(" "),
        )
      )
      val result = M9OfflineEvalBackendRunner.run(request, staticAdapter())

      result match {
        case Left(M9OfflineEvalBackendRunError(reasons)) =>
          assert(reasons == List(
            "execution plan eval dataset id is required",
            "execution plan catalog snapshot id is required",
            "execution plan eval dataset id  does not match dataset eval dataset id beautyq-m9-backend-interface-v1",
            "execution plan catalog snapshot id   does not match dataset catalog snapshot id seed-resource-catalog",
          ))
        case other =>
          fail(s"expected deterministic validation errors, got $other")
      }
    }

    "report a plan/adapter execution-mode mismatch without falling back" in {
      val request = runRequest.copy(plan = plan.copy(executionMode = M9OfflineEvalBackendExecutionMode.EsOnlyOffline))
      val result = M9OfflineEvalBackendRunner.run(request, staticAdapter())

      result match {
        case Left(M9OfflineEvalBackendRunError(reasons)) =>
          assert(reasons == List(
            "adapter execution mode manual_static_sample does not match plan execution mode es_only_offline"
          ))
        case other =>
          fail(s"expected mode mismatch validation error, got $other")
      }
    }

    "validate query result ids and explicit backend attribution as data contracts" in {
      val invalid = staticResult(" ").copy(
        servingMode = ServingMode.Unknown,
        candidateSource = CandidateSource.Unknown,
      )

      assert(M9OfflineEvalBackendQueryResult.validate(invalid) == List(
        "backend query result query id is required",
        "backend query result candidate source must be explicit",
      ))
    }

    "make hidden fallback unrepresentable as a runner field" in {
      val fields =
        plan.productElementNames.toList ++
          runRequest.productElementNames.toList ++
          staticResult("q_exact_001").productElementNames.toList

      assert(!fields.exists(_.toLowerCase.contains("fallback")))
      assert(M9OfflineEvalBackendExecutionPlan.validate(plan.copy(
        executionMode = M9OfflineEvalBackendExecutionMode.Unknown
      )) == List("execution plan mode must be explicit"))
      assert(M9OfflineEvalBackendQueryResult.validate(staticResult("q_exact_001").copy(
        servingMode = ServingMode.EsOnly,
        candidateSource = CandidateSource.Unknown,
      )) == List("backend query result candidate source must be explicit"))
    }

    "avoid route, plugin, DI, HTTP, Docker, and backend client source surfaces" in {
      val fields =
        plan.productElementNames.toList ++
          runRequest.productElementNames.toList ++
          staticAdapter().productElementNames.toList
      val forbiddenTerms = List("route", "plugin", "distage", "module", "http", "docker", "client")

      assert(!fields.exists(field => forbiddenTerms.exists(term => field.toLowerCase.contains(term))))
    }
  }

  private def staticAdapter(
    results: M9OfflineEvalBackendQueryResult*
  ): M9OfflineEvalStaticBackendAdapter =
    M9OfflineEvalStaticBackendAdapter(
      scriptedResults = results.toList,
      aggregateMetrics = List(metric(OfflineEvalMetricName.QualityGateDecision, OfflineEvalMetricValue.Text("not_evaluated"))),
      warnings = List("manual/static interface test; no backend execution"),
    )

  private def runRequest: M9OfflineEvalBackendRunRequest =
    M9OfflineEvalBackendRunRequest(
      plan = plan,
      dataset = dataset,
    )

  private def plan: M9OfflineEvalBackendExecutionPlan =
    M9OfflineEvalBackendExecutionPlan(
      executionMode = M9OfflineEvalBackendExecutionMode.ManualStaticSample,
      evalDatasetId = EvalDatasetId("beautyq-m9-backend-interface-v1"),
      catalogSnapshotId = CatalogSnapshotId("seed-resource-catalog"),
      experimentId = ExperimentId("m9-backend-interface-spec"),
      routingPolicyId = RoutingPolicyId("manual-static-interface-v1"),
      metricWindow = MetricWindow("offline-run"),
      requestId = Some(RequestId("request-m9-backend-interface-001")),
      qualityGateDecision = "not_evaluated",
      generatedAt = "2026-06-20T10:15:30Z",
      notes = List("m9-backend-interface-spec"),
      warnings = List("interface-only; no ES/Qdrant/backend client execution"),
    )

  private def dataset: M9OfflineEvalDataset =
    M9OfflineEvalDataset(
      evalDatasetId = EvalDatasetId("beautyq-m9-backend-interface-v1"),
      catalogSnapshotId = CatalogSnapshotId("seed-resource-catalog"),
      queries = List(
        datasetQuery("q_exact_001", QueryClass.ExactProductNameBrand),
        datasetQuery("q_semantic_001", QueryClass.SemanticDescriptive),
        datasetQuery("q_negative_001", QueryClass.NegativeOutOfCatalog, negativeOutOfCatalog = true),
      ),
    )

  private def datasetQuery(
    queryId: String,
    queryClass: QueryClass,
    negativeOutOfCatalog: Boolean = false,
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
      negativeOutOfCatalog = negativeOutOfCatalog,
    )

  private def staticResult(
    queryId: String,
    queryClass: QueryClass = QueryClass.SemanticDescriptive,
    topK: List[String] = List("variant-1"),
  ): M9OfflineEvalBackendQueryResult =
    M9OfflineEvalBackendQueryResult(
      queryId = queryId,
      queryClass = queryClass,
      servingMode = ServingMode.Unknown,
      candidateSource = CandidateSource.Manual,
      candidates = topK.zipWithIndex.map { case (resultId, index) =>
        M9OfflineEvalBackendCandidate(
          resultId = resultId,
          candidateSource = CandidateSource.Manual,
          rank = index + 1,
          score = None,
          notes = Nil,
        )
      },
      metrics = List(metric(OfflineEvalMetricName.RegressionPassFail, OfflineEvalMetricValue.Text("sample"))),
      regressionStatus = "sample",
      warnings = Nil,
      failure = None,
    )

  private def metric(
    name: OfflineEvalMetricName,
    value: OfflineEvalMetricValue,
  ): OfflineEvalMetric =
    OfflineEvalMetric(name, value)
}
