package leaderboard.search

import leaderboard.search.eval.{
  M12BeautyQSearchFusionRerankingBoundaryFailureMatrix,
  M12BeautyQSearchFusionRerankingExperimentPlan,
  M12BeautyQSearchFusionRerankingPolicyCatalog,
  M12BeautyQSearchFusionRerankingSavedOutputKind,
  M12BeautyQSearchFusionRerankingSavedOutputSchema,
  M12BeautyQSearchFusionRerankingSavedOutputSchemaMetric,
  M12BeautyQSearchFusionRerankingSavedOutputSchemaRenderer,
}
import org.scalatest.wordspec.AnyWordSpec

import java.nio.charset.StandardCharsets
import scala.io.Source
import scala.util.Using

final class M12BeautyQSearchFusionRerankingSavedOutputSchemaSpec extends AnyWordSpec {

  private val artifactPath: String =
    "/leaderboard/search/eval/m12-beautyq-fusion-reranking-saved-output-schema.md"

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

  "M12BeautyQSearchFusionRerankingSavedOutputSchema inputs" should {

    "consume the accepted M12B experiment-plan verdict and still total 89 plan rows" in {
      val summary = M12BeautyQSearchFusionRerankingSavedOutputSchema.DefaultSummary

      assert(summary.consumedM12ExperimentPlanVerdict == "m12_fusion_reranking_experiment_plan_ready_schema_only")
      assert(
        summary.consumedM12ExperimentPlanVerdict == M12BeautyQSearchFusionRerankingExperimentPlan.Verdict,
      )
      assert(summary.consumedExperimentPlanRowCount == 89)
      assert(M12BeautyQSearchFusionRerankingExperimentPlan.PlanRows.size == 89)
      assert(metricValue(summary.metrics, "consumed_experiment_plan_row_count") == "89")
    }

    "consume the accepted M12B policy catalog verdict" in {
      val summary = M12BeautyQSearchFusionRerankingSavedOutputSchema.DefaultSummary

      assert(summary.consumedM12PolicyCatalogVerdict == "m12_fusion_reranking_policy_catalog_ready_schema_only")
      assert(
        summary.consumedM12PolicyCatalogVerdict == M12BeautyQSearchFusionRerankingPolicyCatalog.Verdict,
      )
      assert(metricValue(summary.metrics, "consumed_m12b_policy_catalog_verdict") ==
        "m12_fusion_reranking_policy_catalog_ready_schema_only")
    }

    "consume the accepted M12C boundary/failure matrix with 38 rows, 9 accepted, 29 denied, 0 skipped" in {
      val summary = M12BeautyQSearchFusionRerankingSavedOutputSchema.DefaultSummary

      assert(summary.consumedM12BoundaryFailureMatrixVerdict ==
        "m12_fusion_reranking_boundary_failure_matrix_ready_schema_only")
      assert(
        summary.consumedM12BoundaryFailureMatrixVerdict ==
          M12BeautyQSearchFusionRerankingBoundaryFailureMatrix.Verdict,
      )
      assert(summary.consumedMatrixRowCount == 38)
      assert(summary.consumedMatrixAcceptedRowCount == 9)
      assert(summary.consumedMatrixDeniedRowCount == 29)
      assert(summary.consumedMatrixSkippedRowCount == 0)
      assert(M12BeautyQSearchFusionRerankingBoundaryFailureMatrix.MatrixRows.size == 38)
      assert(metricValue(summary.metrics, "consumed_matrix_row_count") == "38")
      assert(metricValue(summary.metrics, "consumed_matrix_accepted_row_count") == "9")
      assert(metricValue(summary.metrics, "consumed_matrix_denied_row_count") == "29")
      assert(metricValue(summary.metrics, "consumed_matrix_skipped_row_count") == "0")
    }
  }

  "M12BeautyQSearchFusionRerankingSavedOutputSchema counts" should {

    "carry 89 saved output rows whose kind counts sum to 89" in {
      val summary = M12BeautyQSearchFusionRerankingSavedOutputSchema.DefaultSummary
      val rows = M12BeautyQSearchFusionRerankingSavedOutputSchema.SavedOutputRows

      assert(rows.size == 89)
      assert(summary.savedOutputRowCount == 89)
      assert(summary.kindCounts.map(_._2).sum == 89)
      assert(summary.esBaselinePlaceholderOutputRows == 15)
      assert(summary.qdrantBaselinePlaceholderOutputRows == 1)
      assert(summary.combinedPlaceholderOutputRows == 72)
      assert(summary.acceptedNegativeControlOutputRows == 1)
      assert(summary.manualOrNoOpOutputRows == 0)
      assert(summary.esBaselinePlaceholderOutputRows + summary.qdrantBaselinePlaceholderOutputRows +
        summary.combinedPlaceholderOutputRows + summary.acceptedNegativeControlOutputRows +
        summary.manualOrNoOpOutputRows == 89)
      assert(summary.m12SavedOutputSchemaReady)
      assert(metricValue(summary.metrics, "saved_output_rows") == "89")
      assert(metricValue(summary.metrics, "saved_output_row_count_sum") == "89")
      assert(metricValue(summary.metrics, "m12_saved_output_schema_ready") == "true")
    }

    "report 88 placeholder output rows and 1 excluded output row" in {
      val summary = M12BeautyQSearchFusionRerankingSavedOutputSchema.DefaultSummary

      assert(summary.placeholderOutputRows == 88)
      assert(summary.excludedOutputRows == 1)
      assert(summary.placeholderOutputRows + summary.excludedOutputRows == 89)
      assert(metricValue(summary.metrics, "placeholder_output_rows") == "88")
      assert(metricValue(summary.metrics, "excluded_output_rows") == "1")
      assert(metricValue(summary.metrics, "placeholder_plus_excluded_output_rows") == "89")
    }

    "report 0 executable, 0 real scored/reranked, 0 fabricated candidate payload rows" in {
      val summary = M12BeautyQSearchFusionRerankingSavedOutputSchema.DefaultSummary

      assert(summary.executableOutputRows == 0)
      assert(summary.realScoredOrRerankedOutputRows == 0)
      assert(summary.fabricatedCandidatePayloadRows == 0)
      assert(metricValue(summary.metrics, "executable_output_rows") == "0")
      assert(metricValue(summary.metrics, "real_scored_or_reranked_output_rows") == "0")
      assert(metricValue(summary.metrics, "fabricated_candidate_payload_rows") == "0")
    }

    "report 72 combined placeholder output rows and 1 accepted negative-control output row" in {
      val summary = M12BeautyQSearchFusionRerankingSavedOutputSchema.DefaultSummary

      assert(summary.combinedPlaceholderOutputRows == 72)
      assert(summary.acceptedNegativeControlOutputRows == 1)
      assert(metricValue(summary.metrics, "combined_placeholder_output_rows") == "72")
      assert(metricValue(summary.metrics, "accepted_negative_control_output_rows") == "1")
    }

    "keep every saved output row placeholder-only with no executable, scored/reranked, fabricated, or fused/reranked order" in {
      val rows = M12BeautyQSearchFusionRerankingSavedOutputSchema.SavedOutputRows

      assert(rows.forall(!_.isExecutableOutput))
      assert(rows.forall(!_.isRealScoredOrRerankedOutput))
      assert(rows.forall(!_.hasFabricatedCandidatePayload))
      assert(rows.forall(!_.computesFusedOrRerankedOrder))
      // Excluded rows must carry no backend candidate policy output.
      assert(rows.filter(_.isExcludedOutput).forall(!_.hasBackendCandidatePolicyOutput))
    }
  }

  "M12BeautyQSearchFusionRerankingSavedOutputSchema noise probes" should {

    "render q_noise_004 as a combined placeholder saved output with the three non-executable combined policy names" in {
      val row = M12BeautyQSearchFusionRerankingSavedOutputSchema
        .savedOutputRowFor("q_noise_004")
        .getOrElse(fail("missing q_noise_004 saved output row"))

      assert(row.outputKind == M12BeautyQSearchFusionRerankingSavedOutputKind.CombinedPlaceholderOutput)
      assert(row.isPlaceholderOutput)
      assert(row.isCombinedPlaceholder)
      assert(row.placeholderPolicyNames.toSet == Set(
        "combined_union_placeholder",
        "combined_intersection_placeholder",
        "tie_breaker_placeholder",
      ))
      assert(row.renderedOutput.contains("combined_union_placeholder"))
      assert(row.renderedOutput.contains("combined_intersection_placeholder"))
      assert(row.renderedOutput.contains("tie_breaker_placeholder"))
      assert(!row.isExecutableOutput)
      assert(!row.computesFusedOrRerankedOrder)
    }

    "render q_noise_005 as accepted negative-control excluded saved output with no backend candidate policy output" in {
      val row = M12BeautyQSearchFusionRerankingSavedOutputSchema
        .savedOutputRowFor("q_noise_005")
        .getOrElse(fail("missing q_noise_005 saved output row"))

      assert(row.outputKind ==
        M12BeautyQSearchFusionRerankingSavedOutputKind.AcceptedNegativeControlExcludedOutput)
      assert(row.isExcludedOutput)
      assert(row.isAcceptedNegativeControl)
      assert(row.placeholderPolicyNames.isEmpty)
      assert(!row.hasBackendCandidatePolicyOutput)
      assert(row.renderedOutput.contains("excluded_output"))
      assert(row.renderedOutput.contains("no_backend_candidate_policy"))
      assert(!row.renderedOutput.contains("combined"))
    }
  }

  "M12BeautyQSearchFusionRerankingSavedOutputSchema verdict and boundary" should {

    "carry the placeholder-only saved-output schema readiness verdict, not scoring or execution readiness" in {
      val summary = M12BeautyQSearchFusionRerankingSavedOutputSchema.DefaultSummary

      assert(summary.verdict == "m12_fusion_reranking_saved_output_schema_ready_placeholder_only")
      assert(summary.m12SavedOutputSchemaReady)
      assert(metricValue(summary.metrics, "saved_output_is_placeholder_only_not_scoring") == "true")
      assert(metricValue(summary.metrics, "saved_output_is_placeholder_only_not_fusion_execution") == "true")
      assert(metricValue(summary.metrics, "saved_output_is_placeholder_only_not_reranking_execution") == "true")
      assert(metricValue(summary.metrics, "saved_output_is_placeholder_only_not_candidate_retrieval") == "true")
      assert(metricValue(summary.metrics, "saved_output_is_placeholder_only_not_backend_execution") == "true")
      assert(metricValue(summary.metrics, "saved_output_is_offline_report_not_production_routing") == "true")
      assert(!summary.boundary.qualityGreenClaimed)
      assert(!summary.boundary.productionReadinessClaimed)
      assert(!summary.boundary.routeActivationClaimed)
      assert(!summary.boundary.servingApprovalClaimed)
    }

    "require and implement no real backend/client/route/plugin/DI/HTTP path" in {
      val b = M12BeautyQSearchFusionRerankingSavedOutputSchema.DefaultSummary.boundary
      val metrics = M12BeautyQSearchFusionRerankingSavedOutputSchema.DefaultSummary.metrics

      assert(!b.productionBeautySearchCalled)
      assert(!b.esClientCreated)
      assert(!b.qdrantClientCreated)
      assert(!b.esExecuted)
      assert(!b.qdrantExecuted)
      assert(!b.routePluginDiHttpInvolved)
      assert(!b.realBackendCallRequired)
      assert(!b.realBackendCallImplemented)
      assert(metricValue(metrics, "saved_output_does_not_execute_es_or_qdrant") == "true")
      assert(metricValue(metrics, "saved_output_creates_no_es_or_qdrant_client") == "true")
      assert(metricValue(metrics, "saved_output_calls_no_production_beauty_search") == "true")
      assert(metricValue(metrics, "saved_output_involves_no_route_plugin_di_http") == "true")
      assert(metricValue(metrics, "saved_output_implements_no_fallback") == "true")
      assert(metricValue(metrics, "saved_output_implements_no_hybrid_serving") == "true")
      assert(metricValue(metrics, "saved_output_implements_no_production_telemetry") == "true")
      assert(metricValue(metrics, "es_executed") == "false")
      assert(metricValue(metrics, "qdrant_executed") == "false")
      assert(metricValue(metrics, "route_plugin_di_http_involved") == "false")
      assert(metricValue(metrics, "real_backend_call_required") == "false")
      assert(metricValue(metrics, "real_backend_call_implemented") == "false")
    }

    "keep all production and Qdrant activation boundaries false/not approved" in {
      val b = M12BeautyQSearchFusionRerankingSavedOutputSchema.DefaultSummary.boundary
      val metrics = M12BeautyQSearchFusionRerankingSavedOutputSchema.DefaultSummary.metrics

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
      val b = M12BeautyQSearchFusionRerankingSavedOutputSchema.DefaultSummary.boundary
      val metrics = M12BeautyQSearchFusionRerankingSavedOutputSchema.DefaultSummary.metrics

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

  "M12BeautyQSearchFusionRerankingSavedOutputSchema fabrications" should {

    "fabricate no candidate ids, provider ids, scores, ranks, backend responses, fused scores, reranked positions, quality labels, fusion outputs, reranking outputs, or executable outputs" in {
      val metrics = M12BeautyQSearchFusionRerankingSavedOutputSchema.DefaultSummary.metrics
      val rendered = M12BeautyQSearchFusionRerankingSavedOutputSchema.MarkdownArtifact.contents.toLowerCase

      fabricationTokens.foreach { token =>
        assert(!rendered.contains(token.toLowerCase), s"fabrication token present: $token")
      }
      assert(metricValue(metrics, "saved_output_fabricates_no_candidate_ids_provider_ids_scores_ranks_backend_responses") == "true")
      assert(metricValue(metrics, "saved_output_fabricates_no_fused_scores_reranked_positions_quality_labels") == "true")
      assert(metricValue(metrics, "saved_output_fabricates_no_fusion_outputs_reranking_outputs") == "true")
      assert(metricValue(metrics, "saved_output_contains_no_executable_policy_output") == "true")
    }
  }

  "M12BeautyQSearchFusionRerankingSavedOutputSchema artifact" should {

    "carry no marketing readiness tokens as positive claims" in {
      val rendered = M12BeautyQSearchFusionRerankingSavedOutputSchema.MarkdownArtifact.contents.toLowerCase

      forbiddenRenderedTokens.foreach { token =>
        assert(!rendered.contains(token), s"forbidden token present: $token")
      }
    }

    "include every required section and placeholder-only disclaimer" in {
      val rendered = M12BeautyQSearchFusionRerankingSavedOutputSchema.MarkdownArtifact.contents

      assert(rendered.contains("## Artifact identity"))
      assert(rendered.contains("## Summary"))
      assert(rendered.contains("## Saved output kind counts"))
      assert(rendered.contains("## q_noise_004 and q_noise_005 saved output rows"))
      assert(rendered.contains("## Metrics"))
      assert(rendered.contains("## Boundary summary"))
      assert(rendered.contains("consumed_m12b_policy_catalog_verdict: m12_fusion_reranking_policy_catalog_ready_schema_only"))
      assert(rendered.contains("consumed_m12b_experiment_plan_verdict: m12_fusion_reranking_experiment_plan_ready_schema_only"))
      assert(rendered.contains("consumed_m12c_boundary_failure_matrix_verdict: m12_fusion_reranking_boundary_failure_matrix_ready_schema_only"))
      assert(rendered.contains("saved_output_rows: 89"))
      assert(rendered.contains("placeholder_output_rows: 88"))
      assert(rendered.contains("excluded_output_rows: 1"))
      assert(rendered.contains("executable_output_rows: 0"))
      assert(rendered.contains("fabricated_candidate_payload_rows: 0"))
      assert(rendered.contains("placeholder-only saved-output schema"))
      assert(rendered.contains("NOT scoring"))
      assert(rendered.contains("NOT fusion execution"))
      assert(rendered.contains("NOT reranking execution"))
      assert(rendered.contains("NOT backend execution"))
      assert(rendered.contains("NOT production routing"))
    }

    "render a byte-for-byte deterministic checked-in artifact" in {
      val artifact = M12BeautyQSearchFusionRerankingSavedOutputSchema.MarkdownArtifact
      val expected = readResource(artifactPath)

      assert(artifact.filename == "m12-beautyq-fusion-reranking-saved-output-schema.md")
      assert(artifact.contentType == "text/markdown; charset=utf-8")
      assert(
        artifact.contents == M12BeautyQSearchFusionRerankingSavedOutputSchemaRenderer
          .renderMarkdown(M12BeautyQSearchFusionRerankingSavedOutputSchema.DefaultSummary),
      )
      assert(artifact.contents == expected)
      assert(
        M12BeautyQSearchFusionRerankingSavedOutputSchemaRenderer
          .renderMarkdown(M12BeautyQSearchFusionRerankingSavedOutputSchema.build()) == expected,
      )
    }
  }

  private def metricValue(
    metrics: List[M12BeautyQSearchFusionRerankingSavedOutputSchemaMetric],
    name: String,
  ): String =
    metrics.find(_.name == name) match {
      case Some(metric) => metric.value
      case None         => fail(s"missing saved-output metric $name")
    }

  private def readResource(path: String): String =
    Option(getClass.getResourceAsStream(path)) match {
      case Some(value) =>
        Using.resource(Source.fromInputStream(value, StandardCharsets.UTF_8.name()))(_.mkString)
      case None =>
        fail(s"missing checked-in resource $path")
    }
}
