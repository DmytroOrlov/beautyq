package leaderboard.search

import leaderboard.search.eval.{
  M10BeautyQSearchFullQueryClassification,
  M10BeautyQSearchM11CandidateGenerationInputGroup,
  M10BeautyQSearchOfflineRetrievalStrategyIntent,
  M10BeautyQSearchQueryCategory,
  M10BeautyQSearchRetrievalPolicyReadiness,
  M10BeautyQSearchRetrievalPolicyReadinessMetric,
  M10BeautyQSearchRetrievalPolicyReadinessRenderer,
}
import org.scalatest.wordspec.AnyWordSpec

import java.nio.charset.StandardCharsets
import scala.io.Source
import scala.util.Using

final class M10BeautyQSearchRetrievalPolicyReadinessSpec extends AnyWordSpec {

  private val artifactPath: String =
    "/leaderboard/search/eval/m10-beautyq-retrieval-policy-readiness.md"

  // Marketing/readiness tokens that must never appear as positive claims in the rendered artifact.
  // The disclaimer prose negates phrases like "not backend quality green"; the artifact must not assert
  // these as positive claims, so only unambiguously-positive marketing tokens are screened here.
  private val forbiddenRenderedTokens: List[String] = List(
    "production_ready",
    "qdrant_ready",
    "hybrid_ready",
    "is production ready",
    "production-ready",
    "quality is green",
    "serving approval granted",
    "route activated",
  )

  "M10BeautyQSearchRetrievalPolicyReadiness inputs" should {

    "consume the hardened M10B coverage and still total 64 rows" in {
      val rows = M10BeautyQSearchRetrievalPolicyReadiness.InputRows

      assert(M10BeautyQSearchFullQueryClassification.FullResults.size == 64)
      assert(rows.size == 64)
      assert(rows.map(_.queryId).distinct.size == 64)
      assert(rows.map(_.queryId) == M10BeautyQSearchFullQueryClassification.FullDecisions.map(_.queryId))
    }

    "have M11 input group counts that sum to 64 and match the expected distribution" in {
      val counts = M10BeautyQSearchRetrievalPolicyReadiness.InputGroupCounts
      val byGroup = counts.toMap

      assert(counts.map(_._2).sum == 64)
      M10BeautyQSearchM11CandidateGenerationInputGroup.stableOrder.foreach { group =>
        assert(counts.exists(_._1 == group), s"input group not reported: ${group.render}")
      }
      assert(byGroup(M10BeautyQSearchM11CandidateGenerationInputGroup.EsCandidateGenerationStudyInput) == 14)
      assert(byGroup(M10BeautyQSearchM11CandidateGenerationInputGroup.QdrantCandidateGenerationStudyInput) == 1)
      assert(byGroup(M10BeautyQSearchM11CandidateGenerationInputGroup.CombinedEsQdrantComparisonStudyInput) == 48)
      assert(byGroup(M10BeautyQSearchM11CandidateGenerationInputGroup.AcceptedNegativeControlExclusionInput) == 1)
      assert(byGroup(M10BeautyQSearchM11CandidateGenerationInputGroup.ManualReviewBlockedInput) == 0)
      assert(byGroup(M10BeautyQSearchM11CandidateGenerationInputGroup.NoOpNoiseInput) == 0)
    }

    "count exactly 63 backend candidate-generation study inputs, 1 negative control, 0 unresolved manual review" in {
      val summary = M10BeautyQSearchRetrievalPolicyReadiness.DefaultSummary

      assert(summary.backendCandidateGenerationInputCount == 63)
      assert(summary.acceptedNegativeControlExclusionInputCount == 1)
      assert(summary.unresolvedManualReviewInputCount == 0)
      assert(summary.noOpNoiseInputCount == 0)
    }

    "map q_noise_004 to the combined ES/Qdrant comparison study input" in {
      val row = M10BeautyQSearchRetrievalPolicyReadiness.inputRowFor("q_noise_004").getOrElse(fail("missing q_noise_004"))

      assert(row.strategyIntent == M10BeautyQSearchOfflineRetrievalStrategyIntent.CombinedEsQdrantComparison)
      assert(row.inputGroup == M10BeautyQSearchM11CandidateGenerationInputGroup.CombinedEsQdrantComparisonStudyInput)
      assert(row.inputGroup.isBackendCandidateGenerationInput)
      assert(!row.inputGroup.isUnresolvedManualReviewInput)
    }

    "map q_noise_005 to the accepted negative-control exclusion input" in {
      val row = M10BeautyQSearchRetrievalPolicyReadiness.inputRowFor("q_noise_005").getOrElse(fail("missing q_noise_005"))

      assert(row.category == M10BeautyQSearchQueryCategory.NoisyAmbiguousNonBeautyIntent)
      assert(row.strategyIntent == M10BeautyQSearchOfflineRetrievalStrategyIntent.AcceptedNegativeControlExcluded)
      assert(row.inputGroup == M10BeautyQSearchM11CandidateGenerationInputGroup.AcceptedNegativeControlExclusionInput)
      assert(!row.inputGroup.isBackendCandidateGenerationInput)
      assert(!row.inputGroup.isUnresolvedManualReviewInput)
    }

    "keep noisy/ambiguous/non-beauty accepted negative controls out of backend candidate-generation inputs" in {
      val noisyRows = M10BeautyQSearchRetrievalPolicyReadiness.InputRows
        .filter(_.category == M10BeautyQSearchQueryCategory.NoisyAmbiguousNonBeautyIntent)

      assert(noisyRows.nonEmpty)
      noisyRows.foreach { row =>
        assert(!row.inputGroup.isBackendCandidateGenerationInput, s"noisy row in backend inputs: ${row.queryId}")
      }
    }

    "keep mixed intent as offline combined comparison input only, not production hybrid serving" in {
      val mixedRows = M10BeautyQSearchRetrievalPolicyReadiness.InputRows
        .filter(_.category == M10BeautyQSearchQueryCategory.MixedIntent)

      assert(mixedRows.nonEmpty)
      mixedRows.foreach { row =>
        assert(row.strategyIntent == M10BeautyQSearchOfflineRetrievalStrategyIntent.CombinedEsQdrantComparison)
        assert(row.inputGroup == M10BeautyQSearchM11CandidateGenerationInputGroup.CombinedEsQdrantComparisonStudyInput)
      }
      val summary = M10BeautyQSearchRetrievalPolicyReadiness.DefaultSummary
      assert(!summary.boundary.hybridServingImplied)
      assert(metricValue(summary.metrics, "mixed_intent_is_offline_combined_comparison_only") == "true")
      assert(metricValue(summary.metrics, "hybrid_serving_implied") == "false")
    }

    "map representative anchor rows to their expected M11 input groups" in {
      val anchors = M10BeautyQSearchRetrievalPolicyReadiness.DefaultSummary.anchorRows
      val byId = anchors.map(row => row.queryId -> row).toMap

      assert(anchors.map(_.queryId) == List("q_nails_001", "q_nails_003", "q_noise_005"))
      assert(byId("q_nails_001").inputGroup == M10BeautyQSearchM11CandidateGenerationInputGroup.CombinedEsQdrantComparisonStudyInput)
      assert(byId("q_nails_003").inputGroup == M10BeautyQSearchM11CandidateGenerationInputGroup.EsCandidateGenerationStudyInput)
      assert(byId("q_noise_005").inputGroup == M10BeautyQSearchM11CandidateGenerationInputGroup.AcceptedNegativeControlExclusionInput)
    }
  }

  "M10BeautyQSearchRetrievalPolicyReadiness verdict and boundary" should {

    "carry the offline input-preparation readiness verdict only, not quality green or production readiness" in {
      val summary = M10BeautyQSearchRetrievalPolicyReadiness.DefaultSummary

      assert(summary.verdict == "m11_candidate_generation_inputs_ready_with_negative_control_exclusion")
      assert(summary.m11CandidateGenerationInputsReady)
      assert(metricValue(summary.metrics, "m11_candidate_generation_inputs_ready") == "true")
      assert(metricValue(summary.metrics, "m11_inputs_are_offline_study_inputs_not_production_routes") == "true")
      assert(!summary.boundary.qualityGreenClaimed)
      assert(!summary.boundary.productionReadinessClaimed)
      assert(!summary.boundary.routeActivationClaimed)
      assert(!summary.boundary.servingApprovalClaimed)
    }

    "keep all production and Qdrant activation boundaries false/not approved" in {
      val summary = M10BeautyQSearchRetrievalPolicyReadiness.DefaultSummary
      val b = summary.boundary

      assert(b.defaultBeautySearchEsBacked)
      assert(b.qdrantOptInDisabledByDefault)
      assert(!b.qdrantProductionActivationApproved)
      assert(!b.productionRouteActivated)
      assert(!b.defaultRouteSwitched)
      assert(metricValue(summary.metrics, "qdrant_production_activation_approved") == "false")
      assert(metricValue(summary.metrics, "production_route_activated") == "false")
      assert(metricValue(summary.metrics, "default_route_switched") == "false")
      assert(metricValue(summary.metrics, "default_beauty_search_es_backed") == "true")
      assert(metricValue(summary.metrics, "qdrant_opt_in_disabled_by_default") == "true")
    }

    "require and implement no real backend/client/route/plugin/DI/HTTP path" in {
      val summary = M10BeautyQSearchRetrievalPolicyReadiness.DefaultSummary
      val b = summary.boundary

      assert(!b.productionBeautySearchCalled)
      assert(!b.esClientCreated)
      assert(!b.qdrantClientCreated)
      assert(!b.esExecuted)
      assert(!b.qdrantExecuted)
      assert(!b.routePluginDiHttpInvolved)
      assert(!b.realBackendCallRequired)
      assert(!b.realBackendCallImplemented)
      assert(metricValue(summary.metrics, "production_beauty_search_called") == "false")
      assert(metricValue(summary.metrics, "es_client_created") == "false")
      assert(metricValue(summary.metrics, "qdrant_client_created") == "false")
      assert(metricValue(summary.metrics, "es_executed") == "false")
      assert(metricValue(summary.metrics, "qdrant_executed") == "false")
      assert(metricValue(summary.metrics, "route_plugin_di_http_involved") == "false")
      assert(metricValue(summary.metrics, "real_backend_call_required") == "false")
      assert(metricValue(summary.metrics, "real_backend_call_implemented") == "false")
    }

    "keep forbidden production/hybrid/fallback/fusion/reranking/telemetry boundaries false" in {
      val summary = M10BeautyQSearchRetrievalPolicyReadiness.DefaultSummary
      val b = summary.boundary

      assert(!b.hybridServingImplied)
      assert(!b.fallbackImplied)
      assert(!b.scoreFusionImplied)
      assert(!b.rerankingImplied)
      assert(!b.productionTelemetryImplied)
      assert(metricValue(summary.metrics, "fallback_implied") == "false")
      assert(metricValue(summary.metrics, "score_fusion_implied") == "false")
      assert(metricValue(summary.metrics, "reranking_implied") == "false")
      assert(metricValue(summary.metrics, "production_telemetry_implied") == "false")
    }
  }

  "M10BeautyQSearchRetrievalPolicyReadiness artifact" should {

    "include every required section" in {
      val rendered = M10BeautyQSearchRetrievalPolicyReadiness.MarkdownArtifact.contents

      assert(rendered.contains("## M10 category counts"))
      assert(rendered.contains("## M10 offline strategy intent counts"))
      assert(rendered.contains("## M11 candidate-generation input group counts"))
      assert(rendered.contains("## Representative anchors"))
      assert(rendered.contains("## Noise-probe row mappings"))
      assert(rendered.contains("## Metrics"))
      assert(rendered.contains("## Boundary"))
      assert(rendered.contains("offline study inputs"))
      assert(rendered.contains("not production routes"))
    }

    "map q_noise_004 and q_noise_005 explicitly in the noise-probe section" in {
      val rendered = M10BeautyQSearchRetrievalPolicyReadiness.MarkdownArtifact.contents
      val probe = section(rendered, "## Noise-probe row mappings")

      assert(probe.contains("| q_noise_004 | mixed_intent | combined_es_qdrant_comparison | combined_es_qdrant_comparison_study_input |"))
      assert(probe.contains("| q_noise_005 | noisy_ambiguous_non_beauty_intent | accepted_negative_control_excluded | accepted_negative_control_exclusion_input |"))
    }

    "carry no marketing readiness tokens as positive claims" in {
      val rendered = M10BeautyQSearchRetrievalPolicyReadiness.MarkdownArtifact.contents.toLowerCase

      forbiddenRenderedTokens.foreach { token =>
        assert(!rendered.contains(token), s"forbidden token present: $token")
      }
    }

    "render a byte-for-byte deterministic checked-in artifact" in {
      val artifact = M10BeautyQSearchRetrievalPolicyReadiness.MarkdownArtifact
      val expected = readResource(artifactPath)

      assert(artifact.filename == "m10-beautyq-retrieval-policy-readiness.md")
      assert(artifact.contentType == "text/markdown; charset=utf-8")
      assert(
        artifact.contents == M10BeautyQSearchRetrievalPolicyReadinessRenderer
          .renderMarkdown(M10BeautyQSearchRetrievalPolicyReadiness.DefaultSummary),
      )
      assert(artifact.contents == expected)
      assert(
        M10BeautyQSearchRetrievalPolicyReadinessRenderer
          .renderMarkdown(M10BeautyQSearchRetrievalPolicyReadiness.build()) == expected,
      )
    }
  }

  private def metricValue(
    metrics: List[M10BeautyQSearchRetrievalPolicyReadinessMetric],
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
