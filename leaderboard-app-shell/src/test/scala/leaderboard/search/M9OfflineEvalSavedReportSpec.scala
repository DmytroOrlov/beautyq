package leaderboard.search

import leaderboard.search.eval.{
  CandidateSource,
  CatalogSnapshotId,
  EvalDatasetId,
  ExperimentId,
  FusionPolicy,
  M9OfflineEvalDataset,
  M9OfflineEvalDatasetQuery,
  M9OfflineEvalReportFormatVersion,
  M9OfflineEvalReportRenderer,
  M9OfflineEvalReportRow,
  M9OfflineEvalSavedReport,
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

final class M9OfflineEvalSavedReportSpec extends AnyWordSpec {

  "M9 offline eval saved report model" should {

    "keep a stable format version" in {
      assert(M9OfflineEvalReportFormatVersion.Current.render == "m9-offline-eval-saved-report-v1")
    }

    "represent negative and out-of-catalog dataset queries without retrieval" in {
      val dataset = M9OfflineEvalDataset(
        evalDatasetId = EvalDatasetId("beautyq-m9-seed-v1"),
        catalogSnapshotId = CatalogSnapshotId("seed-resource-catalog"),
        queries = List(
          M9OfflineEvalDatasetQuery(
            queryId = "q_negative_001",
            rawQueryText = "intergalactic haircut",
            normalizedQueryText = Some("intergalactic haircut"),
            queryClass = QueryClass.NegativeOutOfCatalog,
            filters = List("language=de"),
            categories = Nil,
            expectedResults = Nil,
            expectedNotes = List("Expected to stay empty; no catalog service should match."),
            negativeOutOfCatalog = true,
          )
        ),
      )

      assert(dataset.evalDatasetId.render == "beautyq-m9-seed-v1")
      assert(dataset.queries.map(_.queryClass) == List(QueryClass.NegativeOutOfCatalog))
      assert(dataset.queries.map(_.negativeOutOfCatalog) == List(true))
      assert(dataset.queries.flatMap(_.expectedResults) == Nil)
      assert(dataset.queries.flatMap(_.expectedNotes) == List("Expected to stay empty; no catalog service should match."))
    }
  }

  "M9OfflineEvalReportRenderer.renderMarkdown" should {

    "render a deterministic markdown report with metadata, rows, metrics, and non-serving boundary" in {
      val report = representativeReport
      val rendered = M9OfflineEvalReportRenderer.renderMarkdown(report)
      val renderedAgain = M9OfflineEvalReportRenderer.renderMarkdown(report)

      val expected =
        """# M9 Offline Eval Saved Report
          |
          |This artifact is a saved offline-eval format/rendering record. Backend execution is not represented by this artifact.
          |
          |## Format
          |
          |- format_version: m9-offline-eval-saved-report-v1
          |- generated_at: 2026-06-20T10:15:30Z
          |- eval_dataset_id: beautyq-m9-seed-v1
          |- catalog_snapshot_id: seed-resource-catalog
          |- quality_gate_decision: not_evaluated
          |
          |## Metadata
          |
          |- serving_mode: es_only
          |- candidate_source: es
          |- routing_policy_id: offline-es-baseline-v1
          |- fusion_policy: none
          |- reranker_policy: none
          |- experiment_id: m9-format-spec
          |- metadata_catalog_snapshot_id: seed-resource-catalog
          |- metadata_eval_dataset_id: beautyq-m9-seed-v1
          |- metric_window: offline-run
          |
          |## Notes
          |
          |- Format-only fixture; no backend runner is invoked.
          |
          |## Warnings
          |
          |- Runner/backend execution remains future work.
          |
          |## Aggregate Metrics
          |
          || metric | value |
          ||---|---|
          || recall@k | 0.50 |
          || quality_gate_decision | not_evaluated |
          |
          |## Query Rows
          |
          || query_id | query_class | serving_mode | candidate_source | top_k_result_ids | metrics | regression_status | warnings |
          ||---|---|---|---|---|---|---|---|
          || q_exact_001 | exact_product_name_brand | es_only | es | variant-1, variant-2 | recall@k=1.0; regression_pass_fail=true | passed | - |
          || q_semantic_001 | semantic_descriptive | qdrant_only | qdrant | variant-9 | recall@k=0.5; mrr=0.25 | review | semantic candidate evidence only |
          || q_negative_001 | negative_out_of_catalog | es_only | es | - | zero_result_rate=1.0 | passed | negative/out-of-catalog query represented |
          |""".stripMargin

      assert(rendered == renderedAgain)
      assert(rendered == expected)
      assert(rendered.contains("Backend execution is not represented by this artifact."))
      assert(!rendered.contains("Elasticsearch query executed"))
      assert(!rendered.contains("Qdrant query executed"))
      assert(!rendered.contains("hybrid execution"))
    }

    "preserve query class and candidate source attribution per row" in {
      val rendered = M9OfflineEvalReportRenderer.renderMarkdown(representativeReport)

      assert(rendered.contains("| q_exact_001 | exact_product_name_brand | es_only | es |"))
      assert(rendered.contains("| q_semantic_001 | semantic_descriptive | qdrant_only | qdrant |"))
      assert(rendered.contains("| q_negative_001 | negative_out_of_catalog | es_only | es |"))
    }

    "include planned aggregate metric names" in {
      val rendered = M9OfflineEvalReportRenderer.renderMarkdown(representativeReport)

      assert(rendered.contains("| recall@k | 0.50 |"))
      assert(rendered.contains("| quality_gate_decision | not_evaluated |"))
    }

    "create a deterministic markdown artifact wrapper" in {
      val artifact = M9OfflineEvalReportRenderer.markdownArtifact(
        filename = "m9-offline-eval.md",
        report = representativeReport,
      )

      assert(artifact.filename == "m9-offline-eval.md")
      assert(artifact.contentType == "text/markdown; charset=utf-8")
      assert(artifact.contents == M9OfflineEvalReportRenderer.renderMarkdown(representativeReport))
    }
  }

  private def representativeReport: M9OfflineEvalSavedReport =
    M9OfflineEvalSavedReport(
      formatVersion = M9OfflineEvalReportFormatVersion.Current,
      metadata = OfflineEvalRunMetadata(
        servingMode = ServingMode.EsOnly,
        candidateSource = CandidateSource.Es,
        routingPolicyId = RoutingPolicyId("offline-es-baseline-v1"),
        fusionPolicy = FusionPolicy.None,
        rerankerPolicy = RerankerPolicy.None,
        experimentId = ExperimentId("m9-format-spec"),
        catalogSnapshotId = CatalogSnapshotId("seed-resource-catalog"),
        evalDatasetId = EvalDatasetId("beautyq-m9-seed-v1"),
        metricWindow = MetricWindow("offline-run"),
      ),
      evalDatasetId = EvalDatasetId("beautyq-m9-seed-v1"),
      catalogSnapshotId = CatalogSnapshotId("seed-resource-catalog"),
      generatedAt = "2026-06-20T10:15:30Z",
      rows = List(
        M9OfflineEvalReportRow(
          queryId = "q_exact_001",
          queryClass = QueryClass.ExactProductNameBrand,
          servingMode = ServingMode.EsOnly,
          candidateSource = CandidateSource.Es,
          topKResultIds = List("variant-1", "variant-2"),
          metrics = List(
            metric(OfflineEvalMetricName.RecallAtK, OfflineEvalMetricValue.Numeric(BigDecimal("1.0"))),
            metric(OfflineEvalMetricName.RegressionPassFail, OfflineEvalMetricValue.BooleanValue(value = true)),
          ),
          regressionStatus = "passed",
          warnings = Nil,
        ),
        M9OfflineEvalReportRow(
          queryId = "q_semantic_001",
          queryClass = QueryClass.SemanticDescriptive,
          servingMode = ServingMode.QdrantOnly,
          candidateSource = CandidateSource.Qdrant,
          topKResultIds = List("variant-9"),
          metrics = List(
            metric(OfflineEvalMetricName.RecallAtK, OfflineEvalMetricValue.Numeric(BigDecimal("0.5"))),
            metric(OfflineEvalMetricName.Mrr, OfflineEvalMetricValue.Numeric(BigDecimal("0.25"))),
          ),
          regressionStatus = "review",
          warnings = List("semantic candidate evidence only"),
        ),
        M9OfflineEvalReportRow(
          queryId = "q_negative_001",
          queryClass = QueryClass.NegativeOutOfCatalog,
          servingMode = ServingMode.EsOnly,
          candidateSource = CandidateSource.Es,
          topKResultIds = Nil,
          metrics = List(metric(OfflineEvalMetricName.ZeroResultRate, OfflineEvalMetricValue.Numeric(BigDecimal("1.0")))),
          regressionStatus = "passed",
          warnings = List("negative/out-of-catalog query represented"),
        ),
      ),
      aggregateMetrics = List(
        metric(OfflineEvalMetricName.RecallAtK, OfflineEvalMetricValue.Numeric(BigDecimal("0.50"))),
        metric(OfflineEvalMetricName.QualityGateDecision, OfflineEvalMetricValue.Text("not_evaluated")),
      ),
      qualityGateDecision = "not_evaluated",
      notes = List("Format-only fixture; no backend runner is invoked."),
      warnings = List("Runner/backend execution remains future work."),
    )

  private def metric(
    name: OfflineEvalMetricName,
    value: OfflineEvalMetricValue,
  ): OfflineEvalMetric =
    OfflineEvalMetric(name, value)
}
