package leaderboard.search

import leaderboard.search.eval.{
  M11BeautyQSearchCandidateGenerationBoundaryFailureMatrix,
  M11BeautyQSearchCandidateGenerationBoundaryFailureMatrixMetric,
  M11BeautyQSearchCandidateGenerationBoundaryFailureMatrixRenderer,
  M11BeautyQSearchCandidateGenerationMatrixDecision,
  M11BeautyQSearchCandidateGenerationMatrixGroup,
  M11BeautyQSearchCandidateGenerationMatrixReasonCode,
  M11BeautyQSearchCandidateGenerationResultSchema,
}
import org.scalatest.wordspec.AnyWordSpec

import java.nio.charset.StandardCharsets
import scala.io.Source
import scala.util.Using

final class M11BeautyQSearchCandidateGenerationBoundaryFailureMatrixSpec extends AnyWordSpec {

  private val artifactPath: String =
    "/leaderboard/search/eval/m11-beautyq-candidate-generation-boundary-failure-matrix.md"

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

  // Tokens that would betray a fabricated candidate/result payload. None may appear in the artifact.
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

  "M11BeautyQSearchCandidateGenerationBoundaryFailureMatrix inputs" should {

    "consume the accepted M11B result schema and still total 74 result rows" in {
      val summary = M11BeautyQSearchCandidateGenerationBoundaryFailureMatrix.DefaultSummary

      assert(M11BeautyQSearchCandidateGenerationResultSchema.ResultRows.size == 74)
      assert(summary.consumedResultRowCount == 74)
      assert(summary.consumedM11ResultSchemaVerdict == "m11_candidate_generation_result_schema_ready")
      assert(
        summary.consumedM11ResultSchemaVerdict == M11BeautyQSearchCandidateGenerationResultSchema.Verdict,
      )
      assert(metricValue(summary.metrics, "consumed_result_row_count") == "74")
    }

    "carry a deterministic 20-row matrix whose decisions match their groups" in {
      val rows = M11BeautyQSearchCandidateGenerationBoundaryFailureMatrix.MatrixRows
      val summary = M11BeautyQSearchCandidateGenerationBoundaryFailureMatrix.DefaultSummary

      assert(rows.size == 20)
      assert(rows.map(_.caseId).distinct.size == 20)
      assert(rows.forall(_.decisionMatchesGroup))
      assert(summary.matrixRowCount == 20)
      assert(summary.acceptedRowCount == 8)
      assert(summary.deniedRowCount == 12)
      assert(summary.skippedRowCount == 0)
      assert(summary.acceptedRowCount + summary.deniedRowCount + summary.skippedRowCount == 20)
      assert(summary.m11BoundaryFailureMatrixReady)
      assert(metricValue(summary.metrics, "matrix_row_count") == "20")
      assert(metricValue(summary.metrics, "accepted_row_count") == "8")
      assert(metricValue(summary.metrics, "denied_row_count") == "12")
      assert(metricValue(summary.metrics, "skipped_row_count") == "0")
    }

    "report reason-code counts in stable order that sum to the matrix row count" in {
      val summary = M11BeautyQSearchCandidateGenerationBoundaryFailureMatrix.DefaultSummary

      assert(summary.reasonCodeCounts.map(_._1) == M11BeautyQSearchCandidateGenerationMatrixReasonCode.stableOrder)
      assert(summary.reasonCodeCounts.map(_._2).sum == summary.matrixRowCount)
      assert(metricValue(summary.metrics, "reason_code_count_sum") == "20")
    }
  }

  "M11BeautyQSearchCandidateGenerationBoundaryFailureMatrix accepted baselines" should {

    "accept ES-only, Qdrant-only, combined, and accepted negative-control handling cases" in {
      val baseline = M11BeautyQSearchCandidateGenerationBoundaryFailureMatrix.DefaultSummary.acceptedBaselineRows

      assert(baseline.size == 6)
      assert(baseline.forall(_.decision == M11BeautyQSearchCandidateGenerationMatrixDecision.Accepted))
      val byReason = baseline.map(_.reasonCode).toSet
      assert(byReason.contains(M11BeautyQSearchCandidateGenerationMatrixReasonCode.EsOnlyPendingPlaceholderAccepted))
      assert(byReason.contains(M11BeautyQSearchCandidateGenerationMatrixReasonCode.QdrantOnlyPendingPlaceholderAccepted))
      assert(byReason.contains(M11BeautyQSearchCandidateGenerationMatrixReasonCode.CombinedPendingPlaceholdersAccepted))
      assert(byReason.contains(M11BeautyQSearchCandidateGenerationMatrixReasonCode.AcceptedNegativeControlNoBackendLegAccepted))
    }

    "retain accepted handling cases for the current zero-count manual-review and no-op/noise groups" in {
      val baseline = M11BeautyQSearchCandidateGenerationBoundaryFailureMatrix.DefaultSummary.acceptedBaselineRows
      val byReason = baseline.map(_.reasonCode).toSet

      assert(byReason.contains(M11BeautyQSearchCandidateGenerationMatrixReasonCode.ManualReviewExclusionNoBackendLegAccepted))
      assert(byReason.contains(M11BeautyQSearchCandidateGenerationMatrixReasonCode.NoOpNoiseExclusionNoBackendLegAccepted))
      assert(
        metricValue(
          M11BeautyQSearchCandidateGenerationBoundaryFailureMatrix.DefaultSummary.metrics,
          "zero_count_manual_and_no_op_still_have_handling_cases",
        ) == "true",
      )
    }
  }

  "M11BeautyQSearchCandidateGenerationBoundaryFailureMatrix denials" should {

    "deny every invalid backend-leg combination" in {
      val boundaryRows = M11BeautyQSearchCandidateGenerationBoundaryFailureMatrix.DefaultSummary.deniedBoundaryRows
      val byReason = boundaryRows.map(_.reasonCode).toSet

      assert(boundaryRows.forall(_.decision == M11BeautyQSearchCandidateGenerationMatrixDecision.Denied))
      assert(byReason.contains(M11BeautyQSearchCandidateGenerationMatrixReasonCode.EsOnlyWithQdrantLegDenied))
      assert(byReason.contains(M11BeautyQSearchCandidateGenerationMatrixReasonCode.QdrantOnlyWithEsLegDenied))
      assert(byReason.contains(M11BeautyQSearchCandidateGenerationMatrixReasonCode.CombinedMissingEsLegDenied))
      assert(byReason.contains(M11BeautyQSearchCandidateGenerationMatrixReasonCode.CombinedMissingQdrantLegDenied))
      assert(byReason.contains(M11BeautyQSearchCandidateGenerationMatrixReasonCode.NegativeControlWithBackendLegDenied))
      assert(byReason.contains(M11BeautyQSearchCandidateGenerationMatrixReasonCode.ManualOrNoOpWithBackendLegDenied))
      assert(
        metricValue(
          M11BeautyQSearchCandidateGenerationBoundaryFailureMatrix.DefaultSummary.metrics,
          "invalid_backend_leg_combinations_denied",
        ) == "true",
      )
    }

    "deny every fabricated candidate id, score, rank, provider id, and backend response" in {
      val fabricationRows = M11BeautyQSearchCandidateGenerationBoundaryFailureMatrix.DefaultSummary.deniedFabricationRows
      val byReason = fabricationRows.map(_.reasonCode).toSet

      assert(fabricationRows.size == 4)
      assert(fabricationRows.forall(_.decision == M11BeautyQSearchCandidateGenerationMatrixDecision.Denied))
      assert(byReason.contains(M11BeautyQSearchCandidateGenerationMatrixReasonCode.FabricatedCandidateIdDenied))
      assert(byReason.contains(M11BeautyQSearchCandidateGenerationMatrixReasonCode.FabricatedScoreOrRankDenied))
      assert(byReason.contains(M11BeautyQSearchCandidateGenerationMatrixReasonCode.FabricatedProviderIdDenied))
      assert(byReason.contains(M11BeautyQSearchCandidateGenerationMatrixReasonCode.FabricatedBackendResponseDenied))
      assert(
        metricValue(
          M11BeautyQSearchCandidateGenerationBoundaryFailureMatrix.DefaultSummary.metrics,
          "fabricated_candidate_artifacts_denied",
        ) == "true",
      )
    }

    "deny any production route, /beauty-search, or route-plugin-DI-HTTP involvement" in {
      val row = M11BeautyQSearchCandidateGenerationBoundaryFailureMatrix
        .matrixRowFor("case_production_route_or_beauty_search")
        .getOrElse(fail("missing production-route denial row"))

      assert(row.decision == M11BeautyQSearchCandidateGenerationMatrixDecision.Denied)
      assert(row.reasonCode == M11BeautyQSearchCandidateGenerationMatrixReasonCode.ProductionRouteOrBeautySearchOrDiHttpDenied)
      assert(row.group == M11BeautyQSearchCandidateGenerationMatrixGroup.DeniedBoundaryViolation)
    }

    "deny any activation/route-switch/hybrid/fallback/fusion/reranking/telemetry/readiness/approval claim" in {
      val row = M11BeautyQSearchCandidateGenerationBoundaryFailureMatrix
        .matrixRowFor("case_production_activation_or_serving_claim")
        .getOrElse(fail("missing production-activation denial row"))

      assert(row.decision == M11BeautyQSearchCandidateGenerationMatrixDecision.Denied)
      assert(row.reasonCode == M11BeautyQSearchCandidateGenerationMatrixReasonCode.ProductionActivationOrServingClaimDenied)
      assert(
        metricValue(
          M11BeautyQSearchCandidateGenerationBoundaryFailureMatrix.DefaultSummary.metrics,
          "production_route_and_serving_claims_denied",
        ) == "true",
      )
    }
  }

  "M11BeautyQSearchCandidateGenerationBoundaryFailureMatrix noise probes" should {

    "keep q_noise_004 baseline handling as combined pending ES + Qdrant placeholders" in {
      val row = M11BeautyQSearchCandidateGenerationBoundaryFailureMatrix
        .matrixRowFor("case_q_noise_004_combined_pending")
        .getOrElse(fail("missing q_noise_004 handling row"))

      assert(row.group == M11BeautyQSearchCandidateGenerationMatrixGroup.NoiseProbeHandling)
      assert(row.decision == M11BeautyQSearchCandidateGenerationMatrixDecision.Accepted)
      assert(row.reasonCode == M11BeautyQSearchCandidateGenerationMatrixReasonCode.CombinedPendingPlaceholdersAccepted)
      assert(row.detail.contains("es:pending_not_executed, qdrant:pending_not_executed"))

      // The handling row is derived from the consumed M11B result row, which stays a combined pair.
      val resultRow = M11BeautyQSearchCandidateGenerationResultSchema
        .resultRowFor("q_noise_004")
        .getOrElse(fail("missing q_noise_004 result row"))
      assert(resultRow.isCombinedComparison)
      assert(resultRow.resultLegs.map(_.render) == List("es:pending_not_executed", "qdrant:pending_not_executed"))
    }

    "keep q_noise_005 baseline handling as an accepted negative-control exclusion with no backend legs" in {
      val row = M11BeautyQSearchCandidateGenerationBoundaryFailureMatrix
        .matrixRowFor("case_q_noise_005_negative_control")
        .getOrElse(fail("missing q_noise_005 handling row"))

      assert(row.group == M11BeautyQSearchCandidateGenerationMatrixGroup.NoiseProbeHandling)
      assert(row.decision == M11BeautyQSearchCandidateGenerationMatrixDecision.Accepted)
      assert(row.reasonCode == M11BeautyQSearchCandidateGenerationMatrixReasonCode.AcceptedNegativeControlNoBackendLegAccepted)
      assert(row.detail.contains("(none)"))

      val resultRow = M11BeautyQSearchCandidateGenerationResultSchema
        .resultRowFor("q_noise_005")
        .getOrElse(fail("missing q_noise_005 result row"))
      assert(resultRow.resultLegs.isEmpty)
    }
  }

  "M11BeautyQSearchCandidateGenerationBoundaryFailureMatrix verdict and boundary" should {

    "carry the offline boundary-matrix readiness verdict only, not quality or execution readiness" in {
      val summary = M11BeautyQSearchCandidateGenerationBoundaryFailureMatrix.DefaultSummary

      assert(summary.verdict == "m11_candidate_generation_boundary_failure_matrix_ready")
      assert(metricValue(summary.metrics, "m11_boundary_failure_matrix_ready") == "true")
      assert(metricValue(summary.metrics, "matrix_is_boundary_failure_matrix_only_not_backend_execution") == "true")
      assert(metricValue(summary.metrics, "matrix_is_offline_report_not_production_routing") == "true")
      assert(!summary.boundary.qualityGreenClaimed)
      assert(!summary.boundary.productionReadinessClaimed)
      assert(!summary.boundary.routeActivationClaimed)
      assert(!summary.boundary.servingApprovalClaimed)
    }

    "require and implement no real backend/client/route/plugin/DI/HTTP path" in {
      val b = M11BeautyQSearchCandidateGenerationBoundaryFailureMatrix.DefaultSummary.boundary
      val metrics = M11BeautyQSearchCandidateGenerationBoundaryFailureMatrix.DefaultSummary.metrics

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
      assert(metricValue(metrics, "es_executed") == "false")
      assert(metricValue(metrics, "qdrant_executed") == "false")
      assert(metricValue(metrics, "route_plugin_di_http_involved") == "false")
      assert(metricValue(metrics, "real_backend_call_required") == "false")
      assert(metricValue(metrics, "real_backend_call_implemented") == "false")
    }

    "keep all production and Qdrant activation boundaries false/not approved" in {
      val b = M11BeautyQSearchCandidateGenerationBoundaryFailureMatrix.DefaultSummary.boundary
      val metrics = M11BeautyQSearchCandidateGenerationBoundaryFailureMatrix.DefaultSummary.metrics

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
      val b = M11BeautyQSearchCandidateGenerationBoundaryFailureMatrix.DefaultSummary.boundary
      val metrics = M11BeautyQSearchCandidateGenerationBoundaryFailureMatrix.DefaultSummary.metrics

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

  "M11BeautyQSearchCandidateGenerationBoundaryFailureMatrix artifact" should {

    "include every required section and boundary-matrix-only disclaimer" in {
      val rendered = M11BeautyQSearchCandidateGenerationBoundaryFailureMatrix.MarkdownArtifact.contents

      assert(rendered.contains("## Reason-code counts"))
      assert(rendered.contains("## Accepted baseline rows"))
      assert(rendered.contains("## q_noise_004 and q_noise_005 handling rows"))
      assert(rendered.contains("## Denied boundary rows"))
      assert(rendered.contains("## Denied fabrication rows"))
      assert(rendered.contains("## Metrics"))
      assert(rendered.contains("## Boundary summary"))
      assert(rendered.contains("consumed_m11_result_schema_verdict: m11_candidate_generation_result_schema_ready"))
      assert(rendered.contains("consumed_result_row_count: 74"))
      assert(rendered.contains("matrix_row_count: 20"))
      assert(rendered.contains("boundary/failure matrix only"))
      assert(rendered.contains("not backend execution"))
      assert(rendered.contains("not production routing"))
    }

    "carry no marketing readiness tokens and no fabricated payload tokens" in {
      val rendered = M11BeautyQSearchCandidateGenerationBoundaryFailureMatrix.MarkdownArtifact.contents.toLowerCase

      forbiddenRenderedTokens.foreach { token =>
        assert(!rendered.contains(token), s"forbidden token present: $token")
      }
      fabricationTokens.foreach { token =>
        assert(!rendered.contains(token.toLowerCase), s"fabrication token present: $token")
      }
    }

    "render a byte-for-byte deterministic checked-in artifact" in {
      val artifact = M11BeautyQSearchCandidateGenerationBoundaryFailureMatrix.MarkdownArtifact
      val expected = readResource(artifactPath)

      assert(artifact.filename == "m11-beautyq-candidate-generation-boundary-failure-matrix.md")
      assert(artifact.contentType == "text/markdown; charset=utf-8")
      assert(
        artifact.contents == M11BeautyQSearchCandidateGenerationBoundaryFailureMatrixRenderer
          .renderMarkdown(M11BeautyQSearchCandidateGenerationBoundaryFailureMatrix.DefaultSummary),
      )
      assert(artifact.contents == expected)
      assert(
        M11BeautyQSearchCandidateGenerationBoundaryFailureMatrixRenderer
          .renderMarkdown(M11BeautyQSearchCandidateGenerationBoundaryFailureMatrix.build()) == expected,
      )
    }
  }

  private def metricValue(
    metrics: List[M11BeautyQSearchCandidateGenerationBoundaryFailureMatrixMetric],
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
