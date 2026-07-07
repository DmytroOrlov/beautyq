package leaderboard.search

import leaderboard.search.eval.{
  CandidateSource,
  CatalogSnapshotId,
  EvalDatasetId,
  ExperimentId,
  FusionPolicy,
  MetricWindow,
  OfflineEvalMetric,
  OfflineEvalMetricName,
  OfflineEvalMetricValue,
  OfflineEvalQuerySlice,
  OfflineEvalReportSummary,
  OfflineEvalRunMetadata,
  QueryClass,
  RerankerPolicy,
  RoutingPolicyId,
  ServingMode,
  TelemetryEventFamily,
  TelemetryFieldName,
  TelemetryMetricName,
  TelemetrySchemaPlan,
  TelemetrySchemaSummary,
}
import org.scalatest.wordspec.AnyWordSpec

final class M8M9EvalContractsSpec extends AnyWordSpec {

  "M8/M9 shared vocabulary" should {

    "render serving modes and candidate sources with stable strings" in {
      assert(ServingMode.stableOrder.map(_.render) == List("es_only", "qdrant_only", "hybrid", "unknown"))
      assert(CandidateSource.stableOrder.map(_.render) == List("es", "qdrant", "manual", "unknown"))
    }

    "render fusion and reranker policies as vocabulary only" in {
      assert(FusionPolicy.stableOrder.map(_.render) == List("none", "weighted", "rrf", "reranker", "future"))
      assert(RerankerPolicy.stableOrder.map(_.render) == List("none", "rule_based", "learned", "future"))

      val metadata = OfflineEvalRunMetadata(
        servingMode = ServingMode.Hybrid,
        candidateSource = CandidateSource.Manual,
        routingPolicyId = RoutingPolicyId("offline-comparison-v1"),
        fusionPolicy = FusionPolicy.ReciprocalRankFusion,
        rerankerPolicy = RerankerPolicy.RuleBased,
        experimentId = ExperimentId("m8-m9-contract-spec"),
        catalogSnapshotId = CatalogSnapshotId("seed-resource-catalog"),
        evalDatasetId = EvalDatasetId("beautyq-seed-eval"),
        metricWindow = MetricWindow("offline-run"),
      )

      assert(metadata.servingMode.render == "hybrid")
      assert(metadata.fusionPolicy.render == "rrf")
      assert(metadata.rerankerPolicy.render == "rule_based")
      assert(metadata.productElementNames.toList == List(
        "servingMode",
        "candidateSource",
        "routingPolicyId",
        "fusionPolicy",
        "rerankerPolicy",
        "experimentId",
        "catalogSnapshotId",
        "evalDatasetId",
        "metricWindow",
      ))
    }

    "cover the planned query-class taxonomy" in {
      assert(QueryClass.stableOrder.map(_.render) == List(
        "exact_product_name_brand",
        "category",
        "ingredient_attribute",
        "semantic_descriptive",
        "typo_noisy",
        "filter_heavy",
        "broad_discovery",
        "ambiguous",
        "negative_out_of_catalog",
      ))
    }
  }

  "M9 offline eval report contracts" should {

    "include the planned offline metric names" in {
      assert(OfflineEvalMetricName.plannedM9Metrics.map(_.render) == List(
        "recall@k",
        "mrr",
        "ndcg@k",
        "zero_result_rate",
        "low_result_rate",
        "top_k_overlap",
        "backend_contribution_ratio",
        "latency",
        "failure_count",
        "regression_pass_fail",
        "quality_gate_decision",
      ))
    }

    "render metric values deterministically without JSON support" in {
      assert(OfflineEvalMetricValue.Numeric(BigDecimal("0.7500")).render == "0.7500")
      assert(OfflineEvalMetricValue.Text("passed").render == "passed")
      assert(OfflineEvalMetricValue.BooleanValue(value = true).render == "true")
    }

    "carry shared metadata, query-class slices, and aggregate metrics" in {
      val recall = OfflineEvalMetric(
        name = OfflineEvalMetricName.RecallAtK,
        value = OfflineEvalMetricValue.Numeric(BigDecimal("0.75")),
      )
      val quality = OfflineEvalMetric(
        name = OfflineEvalMetricName.QualityGateDecision,
        value = OfflineEvalMetricValue.Text("not_evaluated"),
      )
      val summary = OfflineEvalReportSummary(
        metadata = OfflineEvalRunMetadata(
          servingMode = ServingMode.EsOnly,
          candidateSource = CandidateSource.Es,
          routingPolicyId = RoutingPolicyId("offline-es-baseline-v1"),
          fusionPolicy = FusionPolicy.None,
          rerankerPolicy = RerankerPolicy.None,
          experimentId = ExperimentId("m9-offline-foundation"),
          catalogSnapshotId = CatalogSnapshotId("seed-resource-catalog"),
          evalDatasetId = EvalDatasetId("beautyq-seed-eval"),
          metricWindow = MetricWindow("offline-run"),
        ),
        slices = List(OfflineEvalQuerySlice(QueryClass.Category, List(recall))),
        aggregateMetrics = List(quality),
      )

      assert(summary.metadata.servingMode == ServingMode.EsOnly)
      assert(summary.metadata.candidateSource == CandidateSource.Es)
      assert(summary.slices.map(_.queryClass) == List(QueryClass.Category))
      assert(summary.slices.flatMap(_.metrics.map(_.name)) == List(OfflineEvalMetricName.RecallAtK))
      assert(summary.aggregateMetrics.map(_.name) == List(OfflineEvalMetricName.QualityGateDecision))
    }
  }

  "M8 telemetry schema plan contracts" should {

    "include the planned telemetry event families" in {
      assert(TelemetryEventFamily.plannedM8Families.map(_.render) == List(
        "search_request",
        "backend_candidate",
        "result_exposure",
        "optional_interaction",
        "failure_timeout",
      ))
    }

    "include planned telemetry metric names without implying emission" in {
      assert(TelemetryMetricName.plannedM8Metrics.map(_.render) == List(
        "request_count",
        "zero_result_rate",
        "low_result_rate",
        "top_k_coverage",
        "latency_p50",
        "latency_p95",
        "latency_p99",
        "es_latency",
        "qdrant_latency",
        "embedding_latency",
        "fusion_latency",
        "rerank_latency",
        "backend_failure_rate",
        "timeout_rate",
        "fallback_used_rate",
        "interaction_proxy",
        "manual_relevance_judgment",
      ))
    }

    "summarize a schema plan as a pure contract object" in {
      val plan = TelemetrySchemaPlan(
        eventFamilies = TelemetryEventFamily.plannedM8Families,
        fields = List(
          TelemetryFieldName("request_id"),
          TelemetryFieldName("serving_mode"),
          TelemetryFieldName("candidate_source"),
          TelemetryFieldName("catalog_snapshot_id"),
        ),
        metrics = TelemetryMetricName.plannedM8Metrics,
      )

      assert(plan.fields.map(_.render) == List("request_id", "serving_mode", "candidate_source", "catalog_snapshot_id"))
      assert(TelemetrySchemaSummary.from(plan) == TelemetrySchemaSummary(eventFamilyCount = 5, fieldCount = 4, metricCount = 17))
    }
  }
}
