package leaderboard.search

import leaderboard.search.eval.{
  M12BeautyQSearchFusionRerankingBoundaryFailureMatrix,
  M12BeautyQSearchFusionRerankingBoundaryFailureMatrixMetric,
  M12BeautyQSearchFusionRerankingBoundaryFailureMatrixRenderer,
  M12BeautyQSearchFusionRerankingExperimentPlan,
  M12BeautyQSearchFusionRerankingMatrixDecision,
  M12BeautyQSearchFusionRerankingMatrixGroup,
  M12BeautyQSearchFusionRerankingMatrixReasonCode,
  M12BeautyQSearchFusionRerankingPolicyCatalog,
  M12BeautyQSearchFusionRerankingPolicyOption,
}
import org.scalatest.wordspec.AnyWordSpec

import java.nio.charset.StandardCharsets
import scala.io.Source
import scala.util.Using

final class M12BeautyQSearchFusionRerankingBoundaryFailureMatrixSpec extends AnyWordSpec {

  private val artifactPath: String =
    "/leaderboard/search/eval/m12-beautyq-fusion-reranking-boundary-failure-matrix.md"

  // Marketing/readiness tokens that must never appear as positive claims in the rendered artifact.
  private val forbiddenRenderedTokens: List[String] = List(
    "production_ready",
    "qdrant_ready",
    "hybrid_ready",
    "is production ready",
    "production-ready",
    "quality is green",
    "serving approval granted",
    "route activated",
    "backend execution ready",
    "execution ready",
    "hybrid serving enabled",
    "fallback enabled",
    "score fusion enabled",
    "reranking enabled",
    "fusion enabled",
    "scoring enabled",
  )

  // Tokens that would betray a fabricated candidate/result/fusion/reranking payload. None may appear.
  private val fabricationTokens: List[String] = List(
    "took_ms",
    "\"hits\"",
    "\"_id\"",
    "\"results\"",
    "\"payload\"",
    "doc_id",
    "cosine_score",
    "embedding",
    "fused_score:",
    "reranked_position:",
    "quality_label:",
    "fusion_output:",
    "reranking_output:",
  )

  "M12BeautyQSearchFusionRerankingBoundaryFailureMatrix inputs" should {

    "consume the accepted M12B policy catalog verdict" in {
      val summary = M12BeautyQSearchFusionRerankingBoundaryFailureMatrix.DefaultSummary

      assert(summary.consumedM12PolicyCatalogVerdict == "m12_fusion_reranking_policy_catalog_ready_schema_only")
      assert(
        summary.consumedM12PolicyCatalogVerdict == M12BeautyQSearchFusionRerankingPolicyCatalog.Verdict,
      )
      assert(metricValue(summary.metrics, "consumed_m12b_policy_catalog_verdict") ==
        "m12_fusion_reranking_policy_catalog_ready_schema_only")
    }

    "consume the accepted M12B experiment-plan schema and still total 64 plan rows" in {
      val summary = M12BeautyQSearchFusionRerankingBoundaryFailureMatrix.DefaultSummary

      assert(summary.consumedM12ExperimentPlanVerdict == "m12_fusion_reranking_experiment_plan_ready_schema_only")
      assert(
        summary.consumedM12ExperimentPlanVerdict == M12BeautyQSearchFusionRerankingExperimentPlan.Verdict,
      )
      assert(summary.consumedExperimentPlanRowCount == 64)
      assert(M12BeautyQSearchFusionRerankingExperimentPlan.PlanRows.size == 64)
      assert(metricValue(summary.metrics, "consumed_experiment_plan_row_count") == "64")
    }

    "preserve the M12B catalog names as stable, unique, non-executable policies" in {
      val summary = M12BeautyQSearchFusionRerankingBoundaryFailureMatrix.DefaultSummary

      val expected = List(
        "es_baseline_passthrough",
        "qdrant_baseline_passthrough",
        "combined_union_placeholder",
        "combined_intersection_placeholder",
        "tie_breaker_placeholder",
        "accepted_negative_control_exclusion_policy",
      )

      assert(summary.consumedPolicyCatalogNameCount == 6)
      assert(summary.consumedPolicyCatalogNamesUnique)
      assert(
        M12BeautyQSearchFusionRerankingPolicyCatalog.Policies.map(_.render) == expected,
      )
      assert(
        M12BeautyQSearchFusionRerankingPolicyCatalog.Policies.map(_.render).distinct.size == expected.size,
      )
      assert(M12BeautyQSearchFusionRerankingPolicyCatalog.Policies.forall(!_.isExecutable))
      assert(metricValue(summary.metrics, "consumed_policy_catalog_name_count") == "6")
      assert(metricValue(summary.metrics, "consumed_policy_catalog_names_unique") == "true")
    }
  }

  "M12BeautyQSearchFusionRerankingBoundaryFailureMatrix" should {

    "carry a deterministic 38-row matrix whose decisions match their groups" in {
      val rows = M12BeautyQSearchFusionRerankingBoundaryFailureMatrix.MatrixRows
      val summary = M12BeautyQSearchFusionRerankingBoundaryFailureMatrix.DefaultSummary

      assert(rows.size == 38)
      assert(rows.map(_.caseId).distinct.size == 38)
      assert(rows.forall(_.decisionMatchesGroup))
      assert(summary.matrixRowCount == 38)
      assert(summary.acceptedRowCount == 9)
      assert(summary.deniedRowCount == 29)
      assert(summary.skippedRowCount == 0)
      assert(summary.acceptedRowCount + summary.deniedRowCount + summary.skippedRowCount == 38)
      assert(summary.m12BoundaryFailureMatrixReady)
      assert(metricValue(summary.metrics, "matrix_row_count") == "38")
      assert(metricValue(summary.metrics, "accepted_row_count") == "9")
      assert(metricValue(summary.metrics, "denied_row_count") == "29")
      assert(metricValue(summary.metrics, "skipped_row_count") == "0")
      assert(metricValue(summary.metrics, "m12_boundary_failure_matrix_ready") == "true")
    }

    "report reason-code counts in stable order that sum to the matrix row count" in {
      val summary = M12BeautyQSearchFusionRerankingBoundaryFailureMatrix.DefaultSummary

      assert(summary.reasonCodeCounts.map(_._1) ==
        M12BeautyQSearchFusionRerankingMatrixReasonCode.stableOrder)
      assert(summary.reasonCodeCounts.map(_._2).sum == summary.matrixRowCount)
      assert(metricValue(summary.metrics, "reason_code_count_sum") == "38")
    }
  }

  "M12BeautyQSearchFusionRerankingBoundaryFailureMatrix accepted baselines" should {

    "accept ES baseline, Qdrant baseline, the three combined placeholder policies, and the accepted negative-control exclusion policy" in {
      val baseline = M12BeautyQSearchFusionRerankingBoundaryFailureMatrix.DefaultSummary.acceptedBaselineRows
      val byReason = baseline.map(_.reasonCode).toSet

      assert(baseline.size == 7)
      assert(baseline.forall(_.decision == M12BeautyQSearchFusionRerankingMatrixDecision.Accepted))
      assert(byReason.contains(
        M12BeautyQSearchFusionRerankingMatrixReasonCode.EsBaselinePassthroughPlaceholderAccepted))
      assert(byReason.contains(
        M12BeautyQSearchFusionRerankingMatrixReasonCode.QdrantBaselinePassthroughPlaceholderAccepted))
      assert(byReason.contains(
        M12BeautyQSearchFusionRerankingMatrixReasonCode.CombinedUnionPlaceholderPolicyAccepted))
      assert(byReason.contains(
        M12BeautyQSearchFusionRerankingMatrixReasonCode.CombinedIntersectionPlaceholderPolicyAccepted))
      assert(byReason.contains(
        M12BeautyQSearchFusionRerankingMatrixReasonCode.TieBreakerPlaceholderPolicyAccepted))
      assert(byReason.contains(
        M12BeautyQSearchFusionRerankingMatrixReasonCode.AcceptedNegativeControlExclusionPolicyAccepted))
    }

    "retain an accepted handling case for the current zero-count manual/no-op exclusion group" in {
      val baseline = M12BeautyQSearchFusionRerankingBoundaryFailureMatrix.DefaultSummary.acceptedBaselineRows
      val byReason = baseline.map(_.reasonCode).toSet

      assert(byReason.contains(
        M12BeautyQSearchFusionRerankingMatrixReasonCode.ManualOrNoOpZeroCountHandlingAccepted))
      assert(metricValue(summaryMetrics(), "zero_count_manual_or_no_op_still_has_handling_case") == "true")
    }
  }

  "M12BeautyQSearchFusionRerankingBoundaryFailureMatrix denials" should {

    "deny executable policy drift and real scored/reranked row drift" in {
      val drift = M12BeautyQSearchFusionRerankingBoundaryFailureMatrix.DefaultSummary
        .deniedExecutableScoredRerankedRows
      val byReason = drift.map(_.reasonCode).toSet

      assert(drift.size == 2)
      assert(drift.forall(_.decision == M12BeautyQSearchFusionRerankingMatrixDecision.Denied))
      assert(byReason.contains(M12BeautyQSearchFusionRerankingMatrixReasonCode.ExecutablePolicyDriftDenied))
      assert(byReason.contains(M12BeautyQSearchFusionRerankingMatrixReasonCode.RealScoredOrRerankedRowDriftDenied))
      assert(metricValue(summaryMetrics(), "executable_scored_reranked_policy_drift_denied") == "true")
    }

    "deny every fabricated candidate id, provider id, score, rank, backend response, fused score, reranked position, quality label, fusion output, and reranking output" in {
      val fabrication = M12BeautyQSearchFusionRerankingBoundaryFailureMatrix.DefaultSummary.deniedFabricationRows
      val byReason = fabrication.map(_.reasonCode).toSet

      assert(fabrication.size == 10)
      assert(fabrication.forall(_.decision == M12BeautyQSearchFusionRerankingMatrixDecision.Denied))
      assert(byReason.contains(M12BeautyQSearchFusionRerankingMatrixReasonCode.FabricatedCandidateIdDenied))
      assert(byReason.contains(M12BeautyQSearchFusionRerankingMatrixReasonCode.FabricatedProviderIdDenied))
      assert(byReason.contains(M12BeautyQSearchFusionRerankingMatrixReasonCode.FabricatedScoreDenied))
      assert(byReason.contains(M12BeautyQSearchFusionRerankingMatrixReasonCode.FabricatedRankDenied))
      assert(byReason.contains(M12BeautyQSearchFusionRerankingMatrixReasonCode.FabricatedBackendResponseDenied))
      assert(byReason.contains(M12BeautyQSearchFusionRerankingMatrixReasonCode.FabricatedFusedScoreDenied))
      assert(byReason.contains(M12BeautyQSearchFusionRerankingMatrixReasonCode.FabricatedRerankedPositionDenied))
      assert(byReason.contains(M12BeautyQSearchFusionRerankingMatrixReasonCode.FabricatedQualityLabelDenied))
      assert(byReason.contains(M12BeautyQSearchFusionRerankingMatrixReasonCode.FabricatedFusionOutputDenied))
      assert(byReason.contains(M12BeautyQSearchFusionRerankingMatrixReasonCode.FabricatedRerankingOutputDenied))
      assert(metricValue(summaryMetrics(), "fabricated_policy_handling_artifacts_denied") == "true")
    }

    "deny backend execution, ES/Qdrant client creation, production /beauty-search, and route/plugin/DI/HTTP involvement" in {
      val boundary = M12BeautyQSearchFusionRerankingBoundaryFailureMatrix.DefaultSummary
        .deniedExecutionClientRouteRows
      val byReason = boundary.map(_.reasonCode).toSet

      assert(boundary.size == 7)
      assert(boundary.forall(_.decision == M12BeautyQSearchFusionRerankingMatrixDecision.Denied))
      assert(byReason.contains(M12BeautyQSearchFusionRerankingMatrixReasonCode.BackendResultLegExecutedDenied))
      assert(byReason.contains(M12BeautyQSearchFusionRerankingMatrixReasonCode.EsClientCreatedDenied))
      assert(byReason.contains(M12BeautyQSearchFusionRerankingMatrixReasonCode.QdrantClientCreatedDenied))
      assert(byReason.contains(M12BeautyQSearchFusionRerankingMatrixReasonCode.EsExecutedDenied))
      assert(byReason.contains(M12BeautyQSearchFusionRerankingMatrixReasonCode.QdrantExecutedDenied))
      assert(byReason.contains(
        M12BeautyQSearchFusionRerankingMatrixReasonCode.ProductionRouteOrBeautySearchCalledDenied))
      assert(byReason.contains(M12BeautyQSearchFusionRerankingMatrixReasonCode.RoutePluginDiHttpInvolvedDenied))
      assert(metricValue(summaryMetrics(), "execution_client_route_boundary_drift_denied") == "true")
    }

    "deny production route switch, Qdrant production activation, hybrid serving, fallback, telemetry, and readiness/approval claims" in {
      val claims = M12BeautyQSearchFusionRerankingBoundaryFailureMatrix.DefaultSummary
        .deniedProductionActivationServingRows
      val byReason = claims.map(_.reasonCode).toSet

      assert(claims.size == 10)
      assert(claims.forall(_.decision == M12BeautyQSearchFusionRerankingMatrixDecision.Denied))
      assert(byReason.contains(
        M12BeautyQSearchFusionRerankingMatrixReasonCode.ProductionRouteSwitchOrDefaultSwitchDenied))
      assert(byReason.contains(
        M12BeautyQSearchFusionRerankingMatrixReasonCode.QdrantProductionActivationApprovedDenied))
      assert(byReason.contains(
        M12BeautyQSearchFusionRerankingMatrixReasonCode.HybridServingImplementedOrClaimedDenied))
      assert(byReason.contains(
        M12BeautyQSearchFusionRerankingMatrixReasonCode.FallbackImplementedOrClaimedDenied))
      assert(byReason.contains(
        M12BeautyQSearchFusionRerankingMatrixReasonCode.ProductionTelemetryImplementedOrClaimedDenied))
      assert(byReason.contains(M12BeautyQSearchFusionRerankingMatrixReasonCode.QualityGreenClaimedDenied))
      assert(byReason.contains(M12BeautyQSearchFusionRerankingMatrixReasonCode.RetrievalQualityClaimedDenied))
      assert(byReason.contains(M12BeautyQSearchFusionRerankingMatrixReasonCode.ProductionReadinessClaimedDenied))
      assert(byReason.contains(M12BeautyQSearchFusionRerankingMatrixReasonCode.RouteActivationClaimedDenied))
      assert(byReason.contains(M12BeautyQSearchFusionRerankingMatrixReasonCode.ServingApprovalClaimedDenied))
      assert(metricValue(summaryMetrics(), "production_activation_serving_claim_drift_denied") == "true")
    }
  }

  "M12BeautyQSearchFusionRerankingBoundaryFailureMatrix noise probes" should {

    "keep q_noise_004 as a combined placeholder experiment-plan handling row" in {
      val row = M12BeautyQSearchFusionRerankingBoundaryFailureMatrix
        .matrixRowFor("case_q_noise_004_combined_placeholder_plan")
        .getOrElse(fail("missing q_noise_004 handling row"))

      assert(row.group == M12BeautyQSearchFusionRerankingMatrixGroup.NoiseProbeHandling)
      assert(row.decision == M12BeautyQSearchFusionRerankingMatrixDecision.Accepted)
      assert(row.reasonCode ==
        M12BeautyQSearchFusionRerankingMatrixReasonCode.CombinedUnionPlaceholderPolicyAccepted)
      assert(row.detail.contains("combined_union_placeholder"))
      assert(row.detail.contains("combined_intersection_placeholder"))
      assert(row.detail.contains("tie_breaker_placeholder"))

      // The handling row is derived from the consumed M12B experiment plan row.
      val planRow = M12BeautyQSearchFusionRerankingExperimentPlan
        .planRowFor("q_noise_004")
        .getOrElse(fail("missing q_noise_004 plan row"))
      assert(planRow.isCombinedExperiment)
      assert(planRow.assignedPolicies.toSet == Set(
        M12BeautyQSearchFusionRerankingPolicyOption.CombinedUnionPlaceholder,
        M12BeautyQSearchFusionRerankingPolicyOption.CombinedIntersectionPlaceholder,
        M12BeautyQSearchFusionRerankingPolicyOption.TieBreakerPlaceholder,
      ))
      assert(planRow.assignedPolicies.forall(!_.isExecutable))
    }

    "keep q_noise_005 as the accepted negative-control exclusion handling row with no backend candidate policy" in {
      val row = M12BeautyQSearchFusionRerankingBoundaryFailureMatrix
        .matrixRowFor("case_q_noise_005_negative_control_exclusion_plan")
        .getOrElse(fail("missing q_noise_005 handling row"))

      assert(row.group == M12BeautyQSearchFusionRerankingMatrixGroup.NoiseProbeHandling)
      assert(row.decision == M12BeautyQSearchFusionRerankingMatrixDecision.Accepted)
      assert(row.reasonCode ==
        M12BeautyQSearchFusionRerankingMatrixReasonCode.AcceptedNegativeControlExclusionPolicyAccepted)
      assert(row.detail.contains("accepted_negative_control_exclusion_policy"))
      assert(!row.detail.contains("combined"))

      val planRow = M12BeautyQSearchFusionRerankingExperimentPlan
        .planRowFor("q_noise_005")
        .getOrElse(fail("missing q_noise_005 plan row"))
      assert(!planRow.hasBackendCandidatePolicy)
      assert(planRow.assignedPolicies == List(
        M12BeautyQSearchFusionRerankingPolicyOption.AcceptedNegativeControlExclusionPolicy,
      ))
      assert(!planRow.isExecutable)
      assert(planRow.allLegsPendingNotExecuted)
    }
  }

  "M12BeautyQSearchFusionRerankingBoundaryFailureMatrix verdict and boundary" should {

    "carry the schema-only boundary-matrix readiness verdict, not scoring or execution readiness" in {
      val summary = M12BeautyQSearchFusionRerankingBoundaryFailureMatrix.DefaultSummary

      assert(summary.verdict == "m12_fusion_reranking_boundary_failure_matrix_ready_schema_only")
      assert(summary.m12BoundaryFailureMatrixReady)
      assert(metricValue(summary.metrics, "matrix_is_boundary_failure_matrix_only_not_scoring") == "true")
      assert(metricValue(summary.metrics, "matrix_is_boundary_failure_matrix_only_not_fusion") == "true")
      assert(metricValue(summary.metrics, "matrix_is_boundary_failure_matrix_only_not_reranking") == "true")
      assert(metricValue(summary.metrics, "matrix_is_boundary_failure_matrix_only_not_candidate_retrieval") == "true")
      assert(metricValue(summary.metrics, "matrix_is_boundary_failure_matrix_only_not_backend_execution") == "true")
      assert(metricValue(summary.metrics, "matrix_is_offline_report_not_production_routing") == "true")
      assert(!summary.boundary.qualityGreenClaimed)
      assert(!summary.boundary.productionReadinessClaimed)
      assert(!summary.boundary.routeActivationClaimed)
      assert(!summary.boundary.servingApprovalClaimed)
    }

    "require and implement no real backend/client/route/plugin/DI/HTTP path" in {
      val b = M12BeautyQSearchFusionRerankingBoundaryFailureMatrix.DefaultSummary.boundary
      val metrics = M12BeautyQSearchFusionRerankingBoundaryFailureMatrix.DefaultSummary.metrics

      assert(!b.productionBeautySearchCalled)
      assert(!b.esClientCreated)
      assert(!b.qdrantClientCreated)
      assert(!b.esExecuted)
      assert(!b.qdrantExecuted)
      assert(!b.routePluginDiHttpInvolved)
      assert(!b.realBackendCallRequired)
      assert(!b.realBackendCallImplemented)
      assert(metricValue(metrics, "matrix_does_not_execute_es_or_qdrant") == "true")
      assert(metricValue(metrics, "matrix_creates_no_es_or_qdrant_client") == "true")
      assert(metricValue(metrics, "matrix_calls_no_production_beauty_search") == "true")
      assert(metricValue(metrics, "matrix_involves_no_route_plugin_di_http") == "true")
      assert(metricValue(metrics, "matrix_implements_no_fallback") == "true")
      assert(metricValue(metrics, "matrix_implements_no_hybrid_serving") == "true")
      assert(metricValue(metrics, "matrix_implements_no_production_telemetry") == "true")
      assert(metricValue(metrics, "es_executed") == "false")
      assert(metricValue(metrics, "qdrant_executed") == "false")
      assert(metricValue(metrics, "route_plugin_di_http_involved") == "false")
      assert(metricValue(metrics, "real_backend_call_required") == "false")
      assert(metricValue(metrics, "real_backend_call_implemented") == "false")
    }

    "keep all production and Qdrant activation boundaries false/not approved" in {
      val b = M12BeautyQSearchFusionRerankingBoundaryFailureMatrix.DefaultSummary.boundary
      val metrics = M12BeautyQSearchFusionRerankingBoundaryFailureMatrix.DefaultSummary.metrics

      assert(b.defaultBeautySearchEsBacked)
      assert(b.qdrantOptInDisabledByDefault)
      assert(!b.qdrantProductionActivationApproved)
      assert(!b.productionRouteActivated)
      assert(!b.defaultRouteSwitched)
      assert(metricValue(metrics, "qdrant_production_activation_approved") == "false")
      assert(metricValue(metrics, "production_route_activated") == "false")
      assert(metricValue(metrics, "default_route_switched") == "false")
      assert(metricValue(metrics, "default_beauty_search_es_backed") == "true")
      assert(metricValue(metrics, "qdrant_opt_in_disabled_by_default") == "true")
    }

    "keep forbidden production/hybrid/fallback/fusion/reranking/telemetry boundaries false" in {
      val b = M12BeautyQSearchFusionRerankingBoundaryFailureMatrix.DefaultSummary.boundary
      val metrics = M12BeautyQSearchFusionRerankingBoundaryFailureMatrix.DefaultSummary.metrics

      assert(!b.hybridServingImplied)
      assert(!b.fallbackImplied)
      assert(!b.scoreFusionImplied)
      assert(!b.rerankingImplied)
      assert(!b.productionTelemetryImplied)
      assert(metricValue(metrics, "hybrid_serving_implied") == "false")
      assert(metricValue(metrics, "fallback_implied") == "false")
      assert(metricValue(metrics, "score_fusion_implied") == "false")
      assert(metricValue(metrics, "reranking_implied") == "false")
      assert(metricValue(metrics, "production_telemetry_implied") == "false")
    }
  }

  "M12BeautyQSearchFusionRerankingBoundaryFailureMatrix fabrications" should {

    "fabricate no candidate ids, provider ids, scores, ranks, backend responses, fused scores, reranked positions, quality labels, fusion outputs, or reranking outputs" in {
      val metrics = M12BeautyQSearchFusionRerankingBoundaryFailureMatrix.DefaultSummary.metrics
      val rendered = M12BeautyQSearchFusionRerankingBoundaryFailureMatrix.MarkdownArtifact.contents.toLowerCase

      fabricationTokens.foreach { token =>
        assert(!rendered.contains(token.toLowerCase), s"fabrication token present: $token")
      }
      assert(metricValue(metrics, "matrix_fabricates_no_candidate_ids_provider_ids_scores_ranks_backend_responses") == "true")
      assert(metricValue(metrics, "matrix_fabricates_no_fused_scores_reranked_positions_quality_labels") == "true")
      assert(metricValue(metrics, "matrix_fabricates_no_fusion_outputs_reranking_outputs") == "true")
    }
  }

  "M12BeautyQSearchFusionRerankingBoundaryFailureMatrix artifact" should {

    "carry no marketing readiness tokens as positive claims" in {
      val rendered = M12BeautyQSearchFusionRerankingBoundaryFailureMatrix.MarkdownArtifact.contents.toLowerCase

      forbiddenRenderedTokens.foreach { token =>
        assert(!rendered.contains(token), s"forbidden token present: $token")
      }
    }

    "include every required section and boundary-matrix-only disclaimer" in {
      val rendered = M12BeautyQSearchFusionRerankingBoundaryFailureMatrix.MarkdownArtifact.contents

      assert(rendered.contains("## Artifact identity"))
      assert(rendered.contains("## Summary"))
      assert(rendered.contains("## Reason-code counts"))
      assert(rendered.contains("## Accepted baseline rows"))
      assert(rendered.contains("## q_noise_004 and q_noise_005 handling rows"))
      assert(rendered.contains("## Denied executable / scored / reranked drift rows"))
      assert(rendered.contains("## Denied fabrication rows"))
      assert(rendered.contains("## Denied execution / client / route boundary rows"))
      assert(rendered.contains("## Denied production activation / serving claim rows"))
      assert(rendered.contains("## Metrics"))
      assert(rendered.contains("## Boundary summary"))
      assert(rendered.contains("consumed_m12b_policy_catalog_verdict: m12_fusion_reranking_policy_catalog_ready_schema_only"))
      assert(rendered.contains("consumed_m12b_experiment_plan_verdict: m12_fusion_reranking_experiment_plan_ready_schema_only"))
      assert(rendered.contains("consumed_experiment_plan_row_count: 64"))
      assert(rendered.contains("matrix_row_count: 38"))
      assert(rendered.contains("boundary/failure matrix only"))
      assert(rendered.contains("NOT scoring"))
      assert(rendered.contains("NOT fusion execution"))
      assert(rendered.contains("NOT reranking execution"))
      assert(rendered.contains("NOT backend execution"))
      assert(rendered.contains("NOT production routing"))
    }

    "render a byte-for-byte deterministic checked-in artifact" in {
      val artifact = M12BeautyQSearchFusionRerankingBoundaryFailureMatrix.MarkdownArtifact
      val expected = readResource(artifactPath)

      assert(artifact.filename == "m12-beautyq-fusion-reranking-boundary-failure-matrix.md")
      assert(artifact.contentType == "text/markdown; charset=utf-8")
      assert(
        artifact.contents == M12BeautyQSearchFusionRerankingBoundaryFailureMatrixRenderer
          .renderMarkdown(M12BeautyQSearchFusionRerankingBoundaryFailureMatrix.DefaultSummary),
      )
      assert(artifact.contents == expected)
      assert(
        M12BeautyQSearchFusionRerankingBoundaryFailureMatrixRenderer
          .renderMarkdown(M12BeautyQSearchFusionRerankingBoundaryFailureMatrix.build()) == expected,
      )
    }
  }

  private def summaryMetrics(): List[M12BeautyQSearchFusionRerankingBoundaryFailureMatrixMetric] =
    M12BeautyQSearchFusionRerankingBoundaryFailureMatrix.DefaultSummary.metrics

  private def metricValue(
    metrics: List[M12BeautyQSearchFusionRerankingBoundaryFailureMatrixMetric],
    name: String,
  ): String =
    metrics.find(_.name == name) match {
      case Some(metric) => metric.value
      case None         => fail(s"missing matrix metric $name")
    }

  private def readResource(path: String): String =
    Option(getClass.getResourceAsStream(path)) match {
      case Some(value) =>
        Using.resource(Source.fromInputStream(value, StandardCharsets.UTF_8.name()))(_.mkString)
      case None =>
        fail(s"missing checked-in resource $path")
    }
}
