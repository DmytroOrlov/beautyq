package leaderboard.search

import leaderboard.search.eval.{
  M10BeautyQSearchM11CandidateGenerationInputGroup,
  M10BeautyQSearchOfflineRetrievalStrategyIntent,
  M10BeautyQSearchQueryCategory,
  M11BeautyQSearchCandidateGenerationBackend,
  M11BeautyQSearchCandidateGenerationInputSkeleton,
  M11BeautyQSearchCandidateGenerationResultDisposition,
  M11BeautyQSearchCandidateGenerationResultLegStatus,
  M11BeautyQSearchCandidateGenerationResultSchema,
  M11BeautyQSearchCandidateGenerationResultSchemaMetric,
  M11BeautyQSearchCandidateGenerationResultSchemaRenderer,
}
import org.scalatest.wordspec.AnyWordSpec

import java.nio.charset.StandardCharsets
import scala.io.Source
import scala.util.Using

final class M11BeautyQSearchCandidateGenerationResultSchemaSpec extends AnyWordSpec {

  private val artifactPath: String =
    "/leaderboard/search/eval/m11-beautyq-candidate-generation-result-schema.md"

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
  )

  // Tokens that would betray fabricated candidate/result payload content. None may appear in the
  // artifact. (Negation metric keys legitimately mention candidate/provider ids, so those substrings
  // are deliberately excluded here; the structural leg-render assertions cover them instead.)
  private val fabricationTokens: List[String] = List(
    "took_ms",
    "\"hits\"",
    "\"_id\"",
    "\"results\"",
    "\"payload\"",
    "doc_id",
    "cosine_score",
    "embedding",
  )

  "M11BeautyQSearchCandidateGenerationResultSchema inputs" should {

    "consume the accepted M11A request skeleton and still total 64 rows" in {
      val rows = M11BeautyQSearchCandidateGenerationResultSchema.ResultRows
      val shapes = M11BeautyQSearchCandidateGenerationInputSkeleton.RequestShapes
      val summary = M11BeautyQSearchCandidateGenerationResultSchema.DefaultSummary

      assert(shapes.size == 64)
      assert(rows.size == 64)
      assert(rows.map(_.queryId).distinct.size == 64)
      assert(rows.map(_.queryId) == shapes.map(_.queryId))
      assert(summary.totalRowCount == 64)
      assert(summary.consumedM11RequestSkeletonVerdict == "m11_candidate_generation_input_skeleton_ready")
      assert(
        summary.consumedM10ReadinessVerdict ==
          "m11_candidate_generation_inputs_ready_with_negative_control_exclusion",
      )
      assert(summary.rowGroupCounts == M11BeautyQSearchCandidateGenerationInputSkeleton.RowGroupCounts)
    }

    "preserve M11A row-group counts and derive result disposition counts that sum to 64" in {
      val summary = M11BeautyQSearchCandidateGenerationResultSchema.DefaultSummary
      val byGroup = summary.rowGroupCounts.toMap
      val byDisposition = summary.dispositionCounts.toMap

      assert(summary.rowGroupCounts.map(_._2).sum == 64)
      assert(byGroup(M10BeautyQSearchM11CandidateGenerationInputGroup.EsCandidateGenerationStudyInput) == 14)
      assert(byGroup(M10BeautyQSearchM11CandidateGenerationInputGroup.QdrantCandidateGenerationStudyInput) == 1)
      assert(byGroup(M10BeautyQSearchM11CandidateGenerationInputGroup.CombinedEsQdrantComparisonStudyInput) == 48)
      assert(byGroup(M10BeautyQSearchM11CandidateGenerationInputGroup.AcceptedNegativeControlExclusionInput) == 1)
      assert(byGroup(M10BeautyQSearchM11CandidateGenerationInputGroup.ManualReviewBlockedInput) == 0)
      assert(byGroup(M10BeautyQSearchM11CandidateGenerationInputGroup.NoOpNoiseInput) == 0)

      assert(summary.dispositionCounts.map(_._2).sum == 64)
      assert(byDisposition(M11BeautyQSearchCandidateGenerationResultDisposition.EsOnlyPending) == 14)
      assert(byDisposition(M11BeautyQSearchCandidateGenerationResultDisposition.QdrantOnlyPending) == 1)
      assert(byDisposition(M11BeautyQSearchCandidateGenerationResultDisposition.CombinedComparisonPending) == 48)
      assert(byDisposition(M11BeautyQSearchCandidateGenerationResultDisposition.AcceptedNegativeControlExcluded) == 1)
      assert(byDisposition(M11BeautyQSearchCandidateGenerationResultDisposition.ManualReviewExcluded) == 0)
      assert(byDisposition(M11BeautyQSearchCandidateGenerationResultDisposition.NoOpNoiseSkipped) == 0)

      assert(summary.esOnlyRowCount == 14)
      assert(summary.qdrantOnlyRowCount == 1)
      assert(summary.combinedComparisonRowCount == 48)
      assert(summary.acceptedNegativeControlExclusionRowCount == 1)
      assert(summary.manualReviewBlockedRowCount == 0)
      assert(summary.noOpNoiseRowCount == 0)
    }

    "derive an ES pending/not-executed result-leg placeholder count of 62" in {
      val summary = M11BeautyQSearchCandidateGenerationResultSchema.DefaultSummary
      val rows = M11BeautyQSearchCandidateGenerationResultSchema.ResultRows

      assert(summary.esResultLegPlaceholderCount == 62)
      assert(rows.count(_.hasEsResultLeg) == 62)
      assert(rows.count(_.hasEsResultLeg) == summary.esOnlyRowCount + summary.combinedComparisonRowCount)
      assert(metricValue(summary.metrics, "es_result_leg_placeholder_count") == "62")
    }

    "derive a Qdrant pending/not-executed result-leg placeholder count of 49" in {
      val summary = M11BeautyQSearchCandidateGenerationResultSchema.DefaultSummary
      val rows = M11BeautyQSearchCandidateGenerationResultSchema.ResultRows

      assert(summary.qdrantResultLegPlaceholderCount == 49)
      assert(rows.count(_.hasQdrantResultLeg) == 49)
      assert(rows.count(_.hasQdrantResultLeg) == summary.qdrantOnlyRowCount + summary.combinedComparisonRowCount)
      assert(metricValue(summary.metrics, "qdrant_result_leg_placeholder_count") == "49")
    }

    "derive a combined comparison pair placeholder count of 48 with separate ES and Qdrant legs" in {
      val summary = M11BeautyQSearchCandidateGenerationResultSchema.DefaultSummary
      val rows = M11BeautyQSearchCandidateGenerationResultSchema.ResultRows

      assert(summary.combinedComparisonPairPlaceholderCount == 48)
      assert(rows.count(_.isCombinedComparison) == 48)
      rows.filter(_.isCombinedComparison).foreach { row =>
        assert(row.hasEsResultLeg && row.hasQdrantResultLeg, s"combined row missing a leg: ${row.queryId}")
        assert(row.resultLegs.size == 2)
        assert(row.resultLegs.map(_.backend) == List(
          M11BeautyQSearchCandidateGenerationBackend.Es,
          M11BeautyQSearchCandidateGenerationBackend.Qdrant,
        ))
        assert(row.allLegsPendingNotExecuted)
      }
      assert(metricValue(summary.metrics, "combined_comparison_pair_placeholder_count") == "48")
    }

    "render every backend result leg as a pending/not-executed placeholder, never an execution" in {
      val rows = M11BeautyQSearchCandidateGenerationResultSchema.ResultRows

      rows.foreach { row =>
        row.resultLegs.foreach { leg =>
          assert(
            leg.status == M11BeautyQSearchCandidateGenerationResultLegStatus.PendingNotExecuted,
            s"non-pending leg in ${row.queryId}",
          )
          assert(leg.render.endsWith(":pending_not_executed"))
        }
      }
      assert(
        metricValue(
          M11BeautyQSearchCandidateGenerationResultSchema.DefaultSummary.metrics,
          "backend_result_legs_are_pending_not_executed_placeholders",
        ) == "true",
      )
    }

    "fabricate no real candidate ids, scores, ranks, provider ids, or backend responses" in {
      val summary = M11BeautyQSearchCandidateGenerationResultSchema.DefaultSummary
      val rows = M11BeautyQSearchCandidateGenerationResultSchema.ResultRows

      // The result-leg type carries only a backend and a pending status: no field can hold a candidate
      // id, score, rank, provider id, or backend response. Every rendered leg is exactly backend:status.
      rows.flatMap(_.resultLegs).foreach { leg =>
        assert(leg.render == s"${leg.backend.render}:pending_not_executed")
      }
      val rendered = M11BeautyQSearchCandidateGenerationResultSchema.MarkdownArtifact.contents.toLowerCase
      fabricationTokens.foreach { token =>
        assert(!rendered.contains(token.toLowerCase), s"fabrication token present: $token")
      }
      assert(metricValue(summary.metrics, "no_real_candidate_ids_scores_ranks_provider_ids_fabricated") == "true")
      assert(metricValue(summary.metrics, "no_real_backend_responses_fabricated") == "true")
    }

    "keep accepted negative-control exclusions out of every backend result leg" in {
      val negativeControlRows = M11BeautyQSearchCandidateGenerationResultSchema.ResultRows
        .filter(_.inputGroup == M10BeautyQSearchM11CandidateGenerationInputGroup.AcceptedNegativeControlExclusionInput)

      assert(negativeControlRows.size == 1)
      negativeControlRows.foreach { row =>
        assert(row.disposition == M11BeautyQSearchCandidateGenerationResultDisposition.AcceptedNegativeControlExcluded)
        assert(row.resultLegs.isEmpty, s"negative-control row has result legs: ${row.queryId}")
        assert(!row.hasEsResultLeg)
        assert(!row.hasQdrantResultLeg)
        assert(!row.hasAnyResultLeg)
      }
      assert(
        metricValue(
          M11BeautyQSearchCandidateGenerationResultSchema.DefaultSummary.metrics,
          "accepted_negative_control_has_no_backend_result_leg",
        ) == "true",
      )
    }

    "keep manual-review and no-op/noise groups out of every backend result leg" in {
      val nonBackendRows = M11BeautyQSearchCandidateGenerationResultSchema.ResultRows
        .filter(row =>
          row.inputGroup == M10BeautyQSearchM11CandidateGenerationInputGroup.ManualReviewBlockedInput ||
            row.inputGroup == M10BeautyQSearchM11CandidateGenerationInputGroup.NoOpNoiseInput,
        )

      nonBackendRows.foreach { row =>
        assert(row.resultLegs.isEmpty, s"manual/no-op row has result legs: ${row.queryId}")
      }
      // Defensive: the manual-review and no-op dispositions never reserve backend result legs.
      assert(!M11BeautyQSearchCandidateGenerationResultDisposition.ManualReviewExcluded.hasBackendResultLegs)
      assert(!M11BeautyQSearchCandidateGenerationResultDisposition.NoOpNoiseSkipped.hasBackendResultLegs)
      assert(
        metricValue(
          M11BeautyQSearchCandidateGenerationResultSchema.DefaultSummary.metrics,
          "manual_and_no_op_have_no_backend_result_leg",
        ) == "true",
      )
    }

    "render q_noise_004 as a combined ES/Qdrant pending/not-executed result row" in {
      val row = M11BeautyQSearchCandidateGenerationResultSchema
        .resultRowFor("q_noise_004")
        .getOrElse(fail("missing q_noise_004"))

      assert(row.category == M10BeautyQSearchQueryCategory.MixedIntent)
      assert(row.strategyIntent == M10BeautyQSearchOfflineRetrievalStrategyIntent.CombinedEsQdrantComparison)
      assert(row.disposition == M11BeautyQSearchCandidateGenerationResultDisposition.CombinedComparisonPending)
      assert(row.isCombinedComparison)
      assert(row.hasEsResultLeg && row.hasQdrantResultLeg)
      assert(row.allLegsPendingNotExecuted)
      assert(row.resultLegs.map(_.render) == List("es:pending_not_executed", "qdrant:pending_not_executed"))
    }

    "render q_noise_005 as an accepted negative-control exclusion with no backend result legs" in {
      val row = M11BeautyQSearchCandidateGenerationResultSchema
        .resultRowFor("q_noise_005")
        .getOrElse(fail("missing q_noise_005"))

      assert(row.category == M10BeautyQSearchQueryCategory.NoisyAmbiguousNonBeautyIntent)
      assert(row.strategyIntent == M10BeautyQSearchOfflineRetrievalStrategyIntent.AcceptedNegativeControlExcluded)
      assert(row.disposition == M11BeautyQSearchCandidateGenerationResultDisposition.AcceptedNegativeControlExcluded)
      assert(!row.isCombinedComparison)
      assert(!row.hasEsResultLeg)
      assert(!row.hasQdrantResultLeg)
      assert(row.resultLegs.isEmpty)
    }

    "keep combined result rows as offline comparison report shapes only, not production hybrid serving" in {
      val combinedRows = M11BeautyQSearchCandidateGenerationResultSchema.ResultRows
        .filter(_.isCombinedComparison)
      val summary = M11BeautyQSearchCandidateGenerationResultSchema.DefaultSummary

      assert(combinedRows.nonEmpty)
      combinedRows.foreach { row =>
        assert(row.strategyIntent == M10BeautyQSearchOfflineRetrievalStrategyIntent.CombinedEsQdrantComparison)
        assert(row.disposition == M11BeautyQSearchCandidateGenerationResultDisposition.CombinedComparisonPending)
      }
      assert(!summary.boundary.hybridServingImplied)
      assert(metricValue(summary.metrics, "combined_comparison_is_offline_report_shape_not_hybrid_serving") == "true")
      assert(metricValue(summary.metrics, "hybrid_serving_implied") == "false")
    }
  }

  "M11BeautyQSearchCandidateGenerationResultSchema verdict and boundary" should {

    "carry the offline result-schema readiness verdict only, not quality or execution readiness" in {
      val summary = M11BeautyQSearchCandidateGenerationResultSchema.DefaultSummary

      assert(summary.verdict == "m11_candidate_generation_result_schema_ready")
      assert(summary.m11CandidateGenerationResultSchemaReady)
      assert(metricValue(summary.metrics, "m11_candidate_generation_result_schema_ready") == "true")
      assert(
        metricValue(summary.metrics, "m11_result_rows_are_saved_report_shapes_not_backend_execution") == "true",
      )
      assert(
        metricValue(summary.metrics, "m11_result_rows_are_offline_report_shapes_not_production_routes") == "true",
      )
      assert(!summary.boundary.qualityGreenClaimed)
      assert(!summary.boundary.productionReadinessClaimed)
      assert(!summary.boundary.routeActivationClaimed)
      assert(!summary.boundary.servingApprovalClaimed)
    }

    "require and implement no real backend/client/route/plugin/DI/HTTP path" in {
      val b = M11BeautyQSearchCandidateGenerationResultSchema.DefaultSummary.boundary
      val metrics = M11BeautyQSearchCandidateGenerationResultSchema.DefaultSummary.metrics

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
      val b = M11BeautyQSearchCandidateGenerationResultSchema.DefaultSummary.boundary
      val metrics = M11BeautyQSearchCandidateGenerationResultSchema.DefaultSummary.metrics

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
      val b = M11BeautyQSearchCandidateGenerationResultSchema.DefaultSummary.boundary
      val metrics = M11BeautyQSearchCandidateGenerationResultSchema.DefaultSummary.metrics

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

  "M11BeautyQSearchCandidateGenerationResultSchema artifact" should {

    "include every required section and offline-report disclaimer" in {
      val rendered = M11BeautyQSearchCandidateGenerationResultSchema.MarkdownArtifact.contents

      assert(rendered.contains("## M11 result disposition counts"))
      assert(rendered.contains("## Pending/not-executed result-leg counts"))
      assert(rendered.contains("## Representative anchors"))
      assert(rendered.contains("## Noise-probe result rows"))
      assert(rendered.contains("## Metrics"))
      assert(rendered.contains("## Boundary"))
      assert(rendered.contains("total_row_count: 64"))
      assert(rendered.contains("consumed_m11_request_skeleton_verdict: m11_candidate_generation_input_skeleton_ready"))
      assert(rendered.contains("consumed_m10_readiness_verdict: m11_candidate_generation_inputs_ready_with_negative_control_exclusion"))
      assert(rendered.contains("saved report"))
      assert(rendered.contains("not production routes"))
      assert(rendered.contains("not backend execution"))
      assert(rendered.contains("pending/not-executed"))
    }

    "map q_noise_004 and q_noise_005 explicitly in the noise-probe section" in {
      val rendered = M11BeautyQSearchCandidateGenerationResultSchema.MarkdownArtifact.contents
      val probe = section(rendered, "## Noise-probe result rows")

      assert(probe.contains("| q_noise_004 | mixed_intent | combined_es_qdrant_comparison | combined_comparison_pending_not_executed | es:pending_not_executed, qdrant:pending_not_executed |"))
      assert(probe.contains("| q_noise_005 | noisy_ambiguous_non_beauty_intent | accepted_negative_control_excluded | accepted_negative_control_excluded | (none) |"))
    }

    "carry no marketing readiness tokens as positive claims" in {
      val rendered = M11BeautyQSearchCandidateGenerationResultSchema.MarkdownArtifact.contents.toLowerCase

      forbiddenRenderedTokens.foreach { token =>
        assert(!rendered.contains(token), s"forbidden token present: $token")
      }
    }

    "render a byte-for-byte deterministic checked-in artifact" in {
      val artifact = M11BeautyQSearchCandidateGenerationResultSchema.MarkdownArtifact
      val expected = readResource(artifactPath)

      assert(artifact.filename == "m11-beautyq-candidate-generation-result-schema.md")
      assert(artifact.contentType == "text/markdown; charset=utf-8")
      assert(
        artifact.contents == M11BeautyQSearchCandidateGenerationResultSchemaRenderer
          .renderMarkdown(M11BeautyQSearchCandidateGenerationResultSchema.DefaultSummary),
      )
      assert(artifact.contents == expected)
      assert(
        M11BeautyQSearchCandidateGenerationResultSchemaRenderer
          .renderMarkdown(M11BeautyQSearchCandidateGenerationResultSchema.build()) == expected,
      )
    }
  }

  private def metricValue(
    metrics: List[M11BeautyQSearchCandidateGenerationResultSchemaMetric],
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
