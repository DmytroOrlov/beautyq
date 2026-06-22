package leaderboard.search

import leaderboard.search.eval.{
  M10BeautyQSearchOfflineRetrievalStrategyIntent,
  M10BeautyQSearchQueryCategory,
  M11BeautyQSearchCandidateGenerationBackend,
  M11BeautyQSearchCandidateGenerationBoundaryFailureMatrix,
  M11BeautyQSearchCandidateGenerationResultDisposition,
  M11BeautyQSearchCandidateGenerationResultLegStatus,
  M11BeautyQSearchCandidateGenerationResultSchema,
  M12BeautyQSearchFusionRerankingInputGroup,
  M12BeautyQSearchFusionRerankingInputScaffold,
  M12BeautyQSearchFusionRerankingInputScaffoldMetric,
  M12BeautyQSearchFusionRerankingInputScaffoldRenderer,
}
import org.scalatest.wordspec.AnyWordSpec

import java.nio.charset.StandardCharsets
import scala.io.Source
import scala.util.Using

final class M12BeautyQSearchFusionRerankingInputScaffoldSpec extends AnyWordSpec {

  private val artifactPath: String =
    "/leaderboard/search/eval/m12-beautyq-fusion-reranking-input-scaffold.md"

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
  )

  // Tokens that would betray fabricated candidate/result/fusion payload content. None may appear in the
  // artifact. (Negation metric keys legitimately mention candidate/provider ids and fused scores, so those
  // substrings are deliberately excluded here; the structural leg-render assertions cover them instead.)
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
  )

  "M12BeautyQSearchFusionRerankingInputScaffold inputs" should {

    "consume the accepted M11B result schema and still total 63 rows" in {
      val envelopes = M12BeautyQSearchFusionRerankingInputScaffold.InputEnvelopes
      val rows = M11BeautyQSearchCandidateGenerationResultSchema.ResultRows
      val summary = M12BeautyQSearchFusionRerankingInputScaffold.DefaultSummary

      assert(rows.size == 63)
      assert(envelopes.size == 63)
      assert(envelopes.map(_.queryId).distinct.size == 63)
      assert(envelopes.map(_.queryId) == rows.map(_.queryId))
      assert(summary.fusionRerankingInputRows == 63)
      assert(summary.consumedResultRowCount == 63)
      assert(summary.consumedM11ResultSchemaVerdict == "m11_candidate_generation_result_schema_ready")
      assert(metricValue(summary.metrics, "consumed_result_row_count") == "63")
    }

    "consume the accepted M11C boundary/failure matrix with 20 rows, 8 accepted, 12 denied, 0 skipped" in {
      val summary = M12BeautyQSearchFusionRerankingInputScaffold.DefaultSummary
      val matrix = M11BeautyQSearchCandidateGenerationBoundaryFailureMatrix.DefaultSummary

      assert(summary.consumedM11BoundaryFailureMatrixVerdict ==
        "m11_candidate_generation_boundary_failure_matrix_ready")
      assert(summary.consumedMatrixRowCount == 20)
      assert(summary.consumedMatrixAcceptedRowCount == 8)
      assert(summary.consumedMatrixDeniedRowCount == 12)
      assert(summary.consumedMatrixSkippedRowCount == 0)
      assert(matrix.matrixRowCount == 20)
      assert(matrix.acceptedRowCount == 8)
      assert(matrix.deniedRowCount == 12)
      assert(matrix.skippedRowCount == 0)
      assert(metricValue(summary.metrics, "consumed_matrix_row_count") == "20")
      assert(metricValue(summary.metrics, "consumed_matrix_accepted_row_count") == "8")
      assert(metricValue(summary.metrics, "consumed_matrix_denied_row_count") == "12")
      assert(metricValue(summary.metrics, "consumed_matrix_skipped_row_count") == "0")
    }

    "derive M12 input group counts that sum to 63" in {
      val summary = M12BeautyQSearchFusionRerankingInputScaffold.DefaultSummary
      val byGroup = summary.inputGroupCounts.toMap

      assert(summary.inputGroupCounts.map(_._2).sum == 63)
      assert(byGroup(M12BeautyQSearchFusionRerankingInputGroup.EsOnlyPlaceholderInput) == 13)
      assert(byGroup(M12BeautyQSearchFusionRerankingInputGroup.QdrantOnlyPlaceholderInput) == 1)
      assert(byGroup(M12BeautyQSearchFusionRerankingInputGroup.CombinedComparisonPlaceholderInput) == 48)
      assert(byGroup(M12BeautyQSearchFusionRerankingInputGroup.AcceptedNegativeControlExclusionInput) == 1)
      assert(byGroup(M12BeautyQSearchFusionRerankingInputGroup.ManualOrNoOpExclusionInput) == 0)
      assert(metricValue(summary.metrics, "input_group_count_sum") == "63")
      assert(metricValue(summary.metrics, "es_only_placeholder_input_rows") == "13")
      assert(metricValue(summary.metrics, "qdrant_only_placeholder_input_rows") == "1")
      assert(metricValue(summary.metrics, "combined_comparison_placeholder_input_rows") == "48")
      assert(metricValue(summary.metrics, "accepted_negative_control_exclusion_input_rows") == "1")
      assert(metricValue(summary.metrics, "manual_or_no_op_exclusion_input_rows") == "0")
    }

    "derive a backend candidate placeholder row count of 62" in {
      val summary = M12BeautyQSearchFusionRerankingInputScaffold.DefaultSummary
      val envelopes = M12BeautyQSearchFusionRerankingInputScaffold.InputEnvelopes

      assert(summary.fusionRerankingBackendCandidateRows == 62)
      assert(envelopes.count(_.hasAnyBackendCandidateLeg) == 62)
      assert(
        summary.fusionRerankingBackendCandidateRows ==
          13 + 1 + 48,
      )
      assert(metricValue(summary.metrics, "fusion_reranking_backend_candidate_rows") == "62")
    }

    "derive an executable fusion/reranking row count of 0" in {
      val summary = M12BeautyQSearchFusionRerankingInputScaffold.DefaultSummary
      val envelopes = M12BeautyQSearchFusionRerankingInputScaffold.InputEnvelopes

      assert(summary.fusionRerankingExecutableRows == 0)
      assert(envelopes.forall(!_.isExecutable))
      assert(metricValue(summary.metrics, "fusion_reranking_executable_rows") == "0")
    }

    "derive a real candidate result row count of 0" in {
      val summary = M12BeautyQSearchFusionRerankingInputScaffold.DefaultSummary

      assert(summary.realCandidateResultRows == 0)
      assert(metricValue(summary.metrics, "real_candidate_result_rows") == "0")
    }

    "derive a pending/not-executed result-leg row count of 110" in {
      val summary = M12BeautyQSearchFusionRerankingInputScaffold.DefaultSummary
      val envelopes = M12BeautyQSearchFusionRerankingInputScaffold.InputEnvelopes

      assert(summary.pendingNotExecutedResultLegRows == 110)
      assert(envelopes.map(_.pendingResultLegs.size).sum == 110)
      assert(summary.esPendingResultLegPlaceholderRows == 61)
      assert(summary.qdrantPendingResultLegPlaceholderRows == 49)
      assert(summary.esPendingResultLegPlaceholderRows + summary.qdrantPendingResultLegPlaceholderRows == 110)
      assert(metricValue(summary.metrics, "pending_not_executed_result_leg_rows") == "110")
      assert(metricValue(summary.metrics, "es_pending_result_leg_placeholder_rows") == "61")
      assert(metricValue(summary.metrics, "qdrant_pending_result_leg_placeholder_rows") == "49")
    }

    "derive a combined comparison pair placeholder count of 48 with separate ES and Qdrant legs" in {
      val summary = M12BeautyQSearchFusionRerankingInputScaffold.DefaultSummary
      val envelopes = M12BeautyQSearchFusionRerankingInputScaffold.InputEnvelopes

      assert(summary.combinedComparisonPairPlaceholders == 48)
      assert(envelopes.count(_.isCombinedComparison) == 48)
      envelopes.filter(_.isCombinedComparison).foreach { envelope =>
        assert(envelope.hasEsPendingLeg && envelope.hasQdrantPendingLeg)
        assert(envelope.pendingResultLegs.size == 2)
        assert(envelope.pendingResultLegs.map(_.backend) == List(
          M11BeautyQSearchCandidateGenerationBackend.Es,
          M11BeautyQSearchCandidateGenerationBackend.Qdrant,
        ))
        assert(envelope.allLegsPendingNotExecuted)
      }
      assert(metricValue(summary.metrics, "combined_comparison_pair_placeholders") == "48")
    }

    "derive an accepted negative-control exclusion count of 1 with no input legs" in {
      val summary = M12BeautyQSearchFusionRerankingInputScaffold.DefaultSummary
      val negativeControl = M12BeautyQSearchFusionRerankingInputScaffold.InputEnvelopes
        .filter(_.inputGroup == M12BeautyQSearchFusionRerankingInputGroup.AcceptedNegativeControlExclusionInput)

      assert(summary.acceptedNegativeControlExclusions == 1)
      assert(negativeControl.size == 1)
      negativeControl.foreach { envelope =>
        assert(envelope.disposition ==
          M11BeautyQSearchCandidateGenerationResultDisposition.AcceptedNegativeControlExcluded)
        assert(envelope.pendingResultLegs.isEmpty)
        assert(!envelope.hasAnyBackendCandidateLeg)
      }
      assert(metricValue(summary.metrics, "accepted_negative_control_exclusions") == "1")
    }

    "forward every backend candidate leg as a pending/not-executed placeholder, never an execution" in {
      val envelopes = M12BeautyQSearchFusionRerankingInputScaffold.InputEnvelopes

      envelopes.foreach { envelope =>
        envelope.pendingResultLegs.foreach { leg =>
          assert(leg.status == M11BeautyQSearchCandidateGenerationResultLegStatus.PendingNotExecuted)
          assert(leg.render.endsWith(":pending_not_executed"))
        }
        assert(envelope.allLegsPendingNotExecuted)
      }
      assert(
        metricValue(
          M12BeautyQSearchFusionRerankingInputScaffold.DefaultSummary.metrics,
          "m12_backend_candidate_legs_are_pending_not_executed_placeholders",
        ) == "true",
      )
    }

    "fabricate no candidate ids, provider ids, scores, ranks, backend responses, fused scores, reranked positions, or quality labels" in {
      val summary = M12BeautyQSearchFusionRerankingInputScaffold.DefaultSummary
      val envelopes = M12BeautyQSearchFusionRerankingInputScaffold.InputEnvelopes

      // The forwarded leg type carries only a backend and a pending status: no field can hold a candidate
      // id, provider id, score, rank, backend response, fused score, reranked position, or quality label.
      envelopes.flatMap(_.pendingResultLegs).foreach { leg =>
        assert(leg.render == s"${leg.backend.render}:pending_not_executed")
      }
      val rendered = M12BeautyQSearchFusionRerankingInputScaffold.MarkdownArtifact.contents.toLowerCase
      fabricationTokens.foreach { token =>
        assert(!rendered.contains(token.toLowerCase), s"fabrication token present: $token")
      }
      assert(metricValue(summary.metrics, "no_real_candidate_ids_provider_ids_scores_ranks_fabricated") == "true")
      assert(metricValue(summary.metrics, "no_real_backend_responses_fabricated") == "true")
      assert(metricValue(summary.metrics, "no_fused_scores_reranked_positions_quality_labels_fabricated") == "true")
    }

    "map q_noise_004 to a combined schema-only input with pending ES and Qdrant placeholders" in {
      val envelope = M12BeautyQSearchFusionRerankingInputScaffold
        .inputEnvelopeFor("q_noise_004")
        .getOrElse(fail("missing q_noise_004"))

      assert(envelope.category == M10BeautyQSearchQueryCategory.MixedIntent)
      assert(envelope.strategyIntent == M10BeautyQSearchOfflineRetrievalStrategyIntent.CombinedEsQdrantComparison)
      assert(envelope.inputGroup == M12BeautyQSearchFusionRerankingInputGroup.CombinedComparisonPlaceholderInput)
      assert(envelope.isCombinedComparison)
      assert(envelope.hasEsPendingLeg && envelope.hasQdrantPendingLeg)
      assert(envelope.allLegsPendingNotExecuted)
      assert(envelope.pendingResultLegs.map(_.render) ==
        List("es:pending_not_executed", "qdrant:pending_not_executed"))
    }

    "map q_noise_005 to an accepted negative-control exclusion with no input legs" in {
      val envelope = M12BeautyQSearchFusionRerankingInputScaffold
        .inputEnvelopeFor("q_noise_005")
        .getOrElse(fail("missing q_noise_005"))

      assert(envelope.category == M10BeautyQSearchQueryCategory.NoisyAmbiguousNonBeautyIntent)
      assert(envelope.strategyIntent == M10BeautyQSearchOfflineRetrievalStrategyIntent.AcceptedNegativeControlExcluded)
      assert(envelope.inputGroup == M12BeautyQSearchFusionRerankingInputGroup.AcceptedNegativeControlExclusionInput)
      assert(!envelope.isCombinedComparison)
      assert(!envelope.hasEsPendingLeg)
      assert(!envelope.hasQdrantPendingLeg)
      assert(envelope.pendingResultLegs.isEmpty)
    }

    "keep combined input envelopes as offline experiment inputs only, not production hybrid serving" in {
      val combined = M12BeautyQSearchFusionRerankingInputScaffold.InputEnvelopes
        .filter(_.isCombinedComparison)
      val summary = M12BeautyQSearchFusionRerankingInputScaffold.DefaultSummary

      assert(combined.nonEmpty)
      combined.foreach { envelope =>
        assert(envelope.strategyIntent == M10BeautyQSearchOfflineRetrievalStrategyIntent.CombinedEsQdrantComparison)
        assert(envelope.inputGroup == M12BeautyQSearchFusionRerankingInputGroup.CombinedComparisonPlaceholderInput)
      }
      assert(!summary.boundary.hybridServingImplied)
      assert(metricValue(summary.metrics, "combined_comparison_input_is_offline_not_hybrid_serving") == "true")
      assert(metricValue(summary.metrics, "hybrid_serving_implied") == "false")
    }
  }

  "M12BeautyQSearchFusionRerankingInputScaffold verdict and boundary" should {

    "carry the schema-only input-scaffold readiness verdict, not quality or execution readiness" in {
      val summary = M12BeautyQSearchFusionRerankingInputScaffold.DefaultSummary

      assert(summary.verdict == "m12_fusion_reranking_input_scaffold_ready_schema_only")
      assert(summary.m12FusionRerankingInputScaffoldReady)
      assert(metricValue(summary.metrics, "m12_fusion_reranking_input_scaffold_ready") == "true")
      assert(metricValue(summary.metrics, "m12_inputs_are_schema_only_not_scoring") == "true")
      assert(metricValue(summary.metrics, "m12_inputs_are_schema_only_not_fusion") == "true")
      assert(metricValue(summary.metrics, "m12_inputs_are_schema_only_not_reranking") == "true")
      assert(metricValue(summary.metrics, "m12_inputs_are_schema_only_not_candidate_retrieval") == "true")
      assert(metricValue(summary.metrics, "m12_inputs_are_schema_only_not_backend_execution") == "true")
      assert(metricValue(summary.metrics, "m12_inputs_are_offline_inputs_not_production_routing") == "true")
      assert(!summary.boundary.qualityGreenClaimed)
      assert(!summary.boundary.productionReadinessClaimed)
      assert(!summary.boundary.routeActivationClaimed)
      assert(!summary.boundary.servingApprovalClaimed)
    }

    "require and implement no real backend/client/route/plugin/DI/HTTP path" in {
      val b = M12BeautyQSearchFusionRerankingInputScaffold.DefaultSummary.boundary
      val metrics = M12BeautyQSearchFusionRerankingInputScaffold.DefaultSummary.metrics

      assert(!b.productionBeautySearchCalled)
      assert(!b.esClientCreated)
      assert(!b.qdrantClientCreated)
      assert(!b.esExecuted)
      assert(!b.qdrantExecuted)
      assert(!b.routePluginDiHttpInvolved)
      assert(!b.realBackendCallRequired)
      assert(!b.realBackendCallImplemented)
      assert(metricValue(metrics, "production_beauty_search_called") == "false")
      assert(metricValue(metrics, "es_client_created") == "false")
      assert(metricValue(metrics, "qdrant_client_created") == "false")
      assert(metricValue(metrics, "es_executed") == "false")
      assert(metricValue(metrics, "qdrant_executed") == "false")
      assert(metricValue(metrics, "route_plugin_di_http_involved") == "false")
      assert(metricValue(metrics, "real_backend_call_required") == "false")
      assert(metricValue(metrics, "real_backend_call_implemented") == "false")
    }

    "keep all production and Qdrant activation boundaries false/not approved" in {
      val b = M12BeautyQSearchFusionRerankingInputScaffold.DefaultSummary.boundary
      val metrics = M12BeautyQSearchFusionRerankingInputScaffold.DefaultSummary.metrics

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
      val b = M12BeautyQSearchFusionRerankingInputScaffold.DefaultSummary.boundary
      val metrics = M12BeautyQSearchFusionRerankingInputScaffold.DefaultSummary.metrics

      assert(!b.hybridServingImplied)
      assert(!b.fallbackImplied)
      assert(!b.scoreFusionImplied)
      assert(!b.rerankingImplied)
      assert(!b.productionTelemetryImplied)
      assert(metricValue(metrics, "fallback_implied") == "false")
      assert(metricValue(metrics, "score_fusion_implied") == "false")
      assert(metricValue(metrics, "reranking_implied") == "false")
      assert(metricValue(metrics, "production_telemetry_implied") == "false")
    }
  }

  "M12BeautyQSearchFusionRerankingInputScaffold artifact" should {

    "include every required section and schema-only disclaimer" in {
      val rendered = M12BeautyQSearchFusionRerankingInputScaffold.MarkdownArtifact.contents

      assert(rendered.contains("## M12 input group counts"))
      assert(rendered.contains("## Pending/not-executed candidate-leg counts"))
      assert(rendered.contains("## Representative anchors"))
      assert(rendered.contains("## Noise-probe input envelopes"))
      assert(rendered.contains("## Metrics"))
      assert(rendered.contains("## Boundary summary"))
      assert(rendered.contains("fusion_reranking_input_rows: 63"))
      assert(rendered.contains("fusion_reranking_backend_candidate_rows: 62"))
      assert(rendered.contains("fusion_reranking_executable_rows: 0"))
      assert(rendered.contains("real_candidate_result_rows: 0"))
      assert(rendered.contains("pending_not_executed_result_leg_rows: 110"))
      assert(rendered.contains("combined_comparison_pair_placeholders: 48"))
      assert(rendered.contains("accepted_negative_control_exclusions: 1"))
      assert(rendered.contains("consumed_m11b_result_schema_verdict: m11_candidate_generation_result_schema_ready"))
      assert(rendered.contains("consumed_m11c_boundary_failure_matrix_verdict: m11_candidate_generation_boundary_failure_matrix_ready"))
      assert(rendered.contains("schema-only"))
      assert(rendered.contains("NOT scoring"))
      assert(rendered.contains("NOT fusion"))
      assert(rendered.contains("NOT reranking"))
      assert(rendered.contains("NOT backend execution"))
      assert(rendered.contains("NOT production routing"))
    }

    "map q_noise_004 and q_noise_005 explicitly in the noise-probe section" in {
      val rendered = M12BeautyQSearchFusionRerankingInputScaffold.MarkdownArtifact.contents
      val probe = section(rendered, "## Noise-probe input envelopes")

      assert(probe.contains("| q_noise_004 | mixed_intent | combined_es_qdrant_comparison | combined_comparison_placeholder_input | es:pending_not_executed, qdrant:pending_not_executed |"))
      assert(probe.contains("| q_noise_005 | noisy_ambiguous_non_beauty_intent | accepted_negative_control_excluded | accepted_negative_control_exclusion_input | (none) |"))
    }

    "carry no marketing readiness tokens as positive claims" in {
      val rendered = M12BeautyQSearchFusionRerankingInputScaffold.MarkdownArtifact.contents.toLowerCase

      forbiddenRenderedTokens.foreach { token =>
        assert(!rendered.contains(token), s"forbidden token present: $token")
      }
    }

    "render a byte-for-byte deterministic checked-in artifact" in {
      val artifact = M12BeautyQSearchFusionRerankingInputScaffold.MarkdownArtifact
      val expected = readResource(artifactPath)

      assert(artifact.filename == "m12-beautyq-fusion-reranking-input-scaffold.md")
      assert(artifact.contentType == "text/markdown; charset=utf-8")
      assert(
        artifact.contents == M12BeautyQSearchFusionRerankingInputScaffoldRenderer
          .renderMarkdown(M12BeautyQSearchFusionRerankingInputScaffold.DefaultSummary),
      )
      assert(artifact.contents == expected)
      assert(
        M12BeautyQSearchFusionRerankingInputScaffoldRenderer
          .renderMarkdown(M12BeautyQSearchFusionRerankingInputScaffold.build()) == expected,
      )
    }
  }

  private def metricValue(
    metrics: List[M12BeautyQSearchFusionRerankingInputScaffoldMetric],
    name: String,
  ): String =
    metrics.find(_.name == name) match {
      case Some(metric) => metric.value
      case None         => fail(s"missing readiness metric $name")
    }

  private def section(contents: String, heading: String): String = {
    val lines = contents.linesIterator.toList
    val start = lines.indexWhere(_ == heading)
    assert(start >= 0, s"missing section $heading")
    val rest = lines.drop(start + 1)
    val end = rest.indexWhere(_.startsWith("## "))
    (if (end < 0) rest else rest.take(end)).mkString("\n")
  }

  private def readResource(path: String): String =
    Option(getClass.getResourceAsStream(path)) match {
      case Some(value) =>
        Using.resource(Source.fromInputStream(value, StandardCharsets.UTF_8.name()))(_.mkString)
      case None =>
        fail(s"missing checked-in resource $path")
    }
}
