package leaderboard.search

import leaderboard.search.eval.{
  CandidateSource,
  CatalogSnapshotId,
  EvalDatasetId,
  ExperimentId,
  FusionPolicy,
  M9OfflineEvalDataset,
  M9OfflineEvalDatasetQuery,
  M9OfflineEvalExpectedResult,
  M9OfflineEvalReportRenderer,
  M9OfflineEvalReportRow,
  M9OfflineEvalStaticRunError,
  M9OfflineEvalStaticRunInput,
  M9OfflineEvalStaticRunner,
  M9OfflineEvalStaticRunnerConfig,
  MetricWindow,
  OfflineEvalMetric,
  OfflineEvalMetricName,
  OfflineEvalMetricValue,
  OfflineEvalRunMetadata,
  QueryClass,
  RerankerPolicy,
  RoutingPolicyId,
  ServingMode,
}
import org.scalatest.wordspec.AnyWordSpec

final class M9OfflineEvalStaticRunnerSpec extends AnyWordSpec {

  "M9OfflineEvalStaticRunner.run" should {

    "assemble the same markdown artifact as the direct renderer for equivalent input" in {
      val input = representativeInput
      val result = M9OfflineEvalStaticRunner.run(input, config)

      result match {
        case Right(staticRun) =>
          assert(staticRun.markdownArtifact.filename == "m9-static-run.md")
          assert(staticRun.markdownArtifact.contentType == "text/markdown; charset=utf-8")
          assert(staticRun.markdownArtifact.contents == M9OfflineEvalReportRenderer.renderMarkdown(staticRun.report))
          assert(staticRun.markdownArtifact == M9OfflineEvalReportRenderer.markdownArtifact("m9-static-run.md", staticRun.report))
        case Left(failure) =>
          fail(s"expected static run report, got validation failure $failure")
      }
    }

    "preserve caller-provided row order" in {
      val input = representativeInput.copy(rows = List(row("q_negative_001"), row("q_exact_001"), row("q_semantic_001")))
      val result = M9OfflineEvalStaticRunner.run(input, config)

      result match {
        case Right(staticRun) =>
          assert(staticRun.report.rows.map(_.queryId) == List("q_negative_001", "q_exact_001", "q_semantic_001"))
        case Left(failure) =>
          fail(s"expected static run report, got validation failure $failure")
      }
    }

    "report dataset and catalog mismatches deterministically" in {
      val input = representativeInput.copy(
        metadata = metadata(
          evalDatasetId = EvalDatasetId("metadata-dataset"),
          catalogSnapshotId = CatalogSnapshotId("metadata-catalog"),
        ),
        reportEvalDatasetId = EvalDatasetId("report-dataset"),
        reportCatalogSnapshotId = CatalogSnapshotId("report-catalog"),
      )

      assertValidationReasons(input, List(
        "metadata eval dataset id metadata-dataset does not match dataset eval dataset id beautyq-m9-static-v1",
        "report eval dataset id report-dataset does not match dataset eval dataset id beautyq-m9-static-v1",
        "metadata catalog snapshot id metadata-catalog does not match dataset catalog snapshot id seed-resource-catalog",
        "report catalog snapshot id report-catalog does not match dataset catalog snapshot id seed-resource-catalog",
      ))
    }

    "report duplicate dataset and row query ids deterministically" in {
      val duplicateDatasetQuery = datasetQuery("q_exact_001", QueryClass.Category)
      val input = representativeInput.copy(
        dataset = representativeInput.dataset.copy(queries = representativeInput.dataset.queries :+ duplicateDatasetQuery),
        rows = representativeInput.rows :+ row("q_exact_001"),
      )

      assertValidationReasons(input, List(
        "duplicate dataset query id: q_exact_001",
        "duplicate row query id: q_exact_001",
      ))
    }

    "report unknown row query ids deterministically" in {
      val input = representativeInput.copy(rows = representativeInput.rows :+ row("q_unknown_001"))

      assertValidationReasons(input, List("row references unknown dataset query id: q_unknown_001"))
    }

    "summarize supplied statuses, warnings, empty top-k rows, and negative query presence without scoring" in {
      val input = representativeInput.copy(
        rows = List(
          row("q_exact_001", status = "passed", topKResultIds = List("variant-1"), warnings = Nil),
          row("q_semantic_001", status = "review", topKResultIds = Nil, warnings = List("needs judgment")),
          row("q_negative_001", status = "failed", topKResultIds = Nil, warnings = List("unexpected empty row check")),
        ),
        qualityGateDecision = "review_required",
      )
      val result = M9OfflineEvalStaticRunner.run(input, config)

      result match {
        case Right(staticRun) =>
          assert(staticRun.qualityGateSummary.totalRows == 3)
          assert(staticRun.qualityGateSummary.passedRows == 1)
          assert(staticRun.qualityGateSummary.reviewRows == 1)
          assert(staticRun.qualityGateSummary.failedRows == 1)
          assert(staticRun.qualityGateSummary.warningRows == 2)
          assert(staticRun.qualityGateSummary.emptyTopKRows == 2)
          assert(staticRun.qualityGateSummary.qualityGateDecision == "review_required")
          assert(staticRun.qualityGateSummary.hasNegativeOutOfCatalogQuery)
        case Left(failure) =>
          fail(s"expected static run report, got validation failure $failure")
      }
    }

    "represent no ES, Qdrant, backend, route, Distage, Docker, or HTTP execution requirement" in {
      val input = representativeInput
      val result = M9OfflineEvalStaticRunner.run(input, config)

      result match {
        case Right(staticRun) =>
          val runInputFields = input.productElementNames.toSet
          val resultFields = staticRun.productElementNames.toSet
          val allFields = runInputFields ++ resultFields
          val forbiddenTerms = List("backend", "route", "distage", "docker", "http", "client", "execute")

          assert(!allFields.exists(field => forbiddenTerms.exists(term => field.toLowerCase.contains(term))))
          assert(staticRun.markdownArtifact.contents.contains("Backend execution is not represented by this artifact."))
          assert(!staticRun.markdownArtifact.contents.contains("Elasticsearch query executed"))
          assert(!staticRun.markdownArtifact.contents.contains("Qdrant query executed"))
        case Left(failure) =>
          fail(s"expected static run report, got validation failure $failure")
      }
    }
  }

  private def assertValidationReasons(
    input: M9OfflineEvalStaticRunInput,
    expectedReasons: List[String],
  ): Unit = {
    val result = M9OfflineEvalStaticRunner.run(input, config)

    result match {
      case Left(M9OfflineEvalStaticRunError(reasons)) =>
        assert(reasons == expectedReasons)
        (): Unit
      case other =>
        fail(s"expected validation reasons $expectedReasons, got $other")
    }
  }

  private val config =
    M9OfflineEvalStaticRunnerConfig(markdownFilename = "m9-static-run.md")

  private def representativeInput: M9OfflineEvalStaticRunInput = {
    val evalDatasetId = EvalDatasetId("beautyq-m9-static-v1")
    val catalogSnapshotId = CatalogSnapshotId("seed-resource-catalog")

    M9OfflineEvalStaticRunInput(
      dataset = M9OfflineEvalDataset(
        evalDatasetId = evalDatasetId,
        catalogSnapshotId = catalogSnapshotId,
        queries = List(
          datasetQuery("q_exact_001", QueryClass.ExactProductNameBrand),
          datasetQuery("q_semantic_001", QueryClass.SemanticDescriptive),
          datasetQuery("q_negative_001", QueryClass.NegativeOutOfCatalog, negativeOutOfCatalog = true),
        ),
      ),
      metadata = metadata(evalDatasetId, catalogSnapshotId),
      reportEvalDatasetId = evalDatasetId,
      reportCatalogSnapshotId = catalogSnapshotId,
      rows = List(
        row("q_exact_001", queryClass = QueryClass.ExactProductNameBrand, status = "passed", topKResultIds = List("variant-1")),
        row("q_semantic_001", queryClass = QueryClass.SemanticDescriptive, status = "review", topKResultIds = List("variant-9")),
        row("q_negative_001", queryClass = QueryClass.NegativeOutOfCatalog, status = "passed", topKResultIds = Nil),
      ),
      aggregateMetrics = List(
        metric(OfflineEvalMetricName.RecallAtK, OfflineEvalMetricValue.Numeric(BigDecimal("0.50"))),
        metric(OfflineEvalMetricName.QualityGateDecision, OfflineEvalMetricValue.Text("not_evaluated")),
      ),
      qualityGateDecision = "not_evaluated",
      notes = List("Static in-memory runner fixture."),
      warnings = List("No backend execution is performed."),
      generatedAt = "2026-06-20T10:15:30Z",
    )
  }

  private def metadata(
    evalDatasetId: EvalDatasetId,
    catalogSnapshotId: CatalogSnapshotId,
  ): OfflineEvalRunMetadata =
    OfflineEvalRunMetadata(
      servingMode = ServingMode.EsOnly,
      candidateSource = CandidateSource.Es,
      routingPolicyId = RoutingPolicyId("offline-static-v1"),
      fusionPolicy = FusionPolicy.None,
      rerankerPolicy = RerankerPolicy.None,
      experimentId = ExperimentId("m9-static-runner-spec"),
      catalogSnapshotId = catalogSnapshotId,
      evalDatasetId = evalDatasetId,
      metricWindow = MetricWindow("offline-run"),
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

  private def row(
    queryId: String,
    queryClass: QueryClass = QueryClass.Category,
    status: String = "passed",
    topKResultIds: List[String] = List("variant-1"),
    warnings: List[String] = Nil,
  ): M9OfflineEvalReportRow =
    M9OfflineEvalReportRow(
      queryId = queryId,
      queryClass = queryClass,
      servingMode = ServingMode.EsOnly,
      candidateSource = CandidateSource.Es,
      topKResultIds = topKResultIds,
      metrics = List(metric(OfflineEvalMetricName.RegressionPassFail, OfflineEvalMetricValue.Text(status))),
      regressionStatus = status,
      warnings = warnings,
    )

  private def metric(
    name: OfflineEvalMetricName,
    value: OfflineEvalMetricValue,
  ): OfflineEvalMetric =
    OfflineEvalMetric(name, value)
}
