package leaderboard.search

import leaderboard.search.eval.{
  BeautyQSearchEvaluationMetricNames,
  M10BeautyQSearchM11CandidateGenerationInputGroup,
  M10BeautyQSearchOfflineRetrievalStrategyIntent,
  M10BeautyQSearchQueryCategory,
  M10BeautyQSearchRetrievalPolicyReadiness,
  M11BeautyQSearchCandidateGenerationBackend,
  M11BeautyQSearchCandidateGenerationInputSkeleton,
  M11BeautyQSearchCandidateGenerationInputSkeletonMetric,
  M11BeautyQSearchCandidateGenerationInputSkeletonRenderer,
}
import org.scalatest.wordspec.AnyWordSpec

import java.nio.charset.StandardCharsets
import scala.io.Source
import scala.util.Using

final class M11BeautyQSearchCandidateGenerationInputSkeletonSpec extends AnyWordSpec {

  private val artifactPath: String =
    "/leaderboard/search/eval/m11-beautyq-candidate-generation-input-skeleton.md"

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
  )

  private def groupCount(
    counts: List[(M10BeautyQSearchM11CandidateGenerationInputGroup, Int)],
    group: M10BeautyQSearchM11CandidateGenerationInputGroup,
  ): Int =
    counts.collectFirst { case (`group`, count) => count }
      .getOrElse(fail(s"Missing M11 input group count: ${group.render}"))

  "M11BeautyQSearchCandidateGenerationInputSkeleton inputs" should {

    "consume the accepted M10C readiness and still total 89 rows" in {
      val shapes = M11BeautyQSearchCandidateGenerationInputSkeleton.RequestShapes
      val summary = M11BeautyQSearchCandidateGenerationInputSkeleton.DefaultSummary

      assert(M10BeautyQSearchRetrievalPolicyReadiness.InputRows.size == 89)
      assert(shapes.size == 89)
      assert(shapes.map(_.queryId).distinct.size == 89)
      assert(shapes.map(_.queryId) == M10BeautyQSearchRetrievalPolicyReadiness.InputRows.map(_.queryId))
      assert(summary.totalQueryCount == 89)
      assert(
        summary.consumedM10ReadinessVerdict ==
          "m11_candidate_generation_inputs_ready_with_negative_control_exclusion",
      )
      assert(summary.rowGroupCounts == M10BeautyQSearchRetrievalPolicyReadiness.InputGroupCounts)
    }

    "preserve M11 row-group counts that sum to 89 and match the accepted distribution" in {
      val counts = M11BeautyQSearchCandidateGenerationInputSkeleton.RowGroupCounts

      assert(counts.map(_._2).sum == 89)
      assert(groupCount(counts, M10BeautyQSearchM11CandidateGenerationInputGroup.EsCandidateGenerationStudyInput) == 15)
      assert(groupCount(counts, M10BeautyQSearchM11CandidateGenerationInputGroup.QdrantCandidateGenerationStudyInput) == 1)
      assert(
        groupCount(counts, M10BeautyQSearchM11CandidateGenerationInputGroup.CombinedEsQdrantComparisonStudyInput) == 72,
      )
      assert(groupCount(counts, M10BeautyQSearchM11CandidateGenerationInputGroup.AcceptedNegativeControlExclusionInput) == 1)
      assert(groupCount(counts, M10BeautyQSearchM11CandidateGenerationInputGroup.ManualReviewBlockedInput) == 0)
      assert(groupCount(counts, M10BeautyQSearchM11CandidateGenerationInputGroup.NoOpNoiseInput) == 0)

      val summary = M11BeautyQSearchCandidateGenerationInputSkeleton.DefaultSummary
      assert(summary.esOnlyRowCount == 15)
      assert(summary.qdrantOnlyRowCount == 1)
      assert(summary.combinedComparisonRowCount == 72)
      assert(summary.acceptedNegativeControlExclusionRowCount == 1)
      assert(summary.manualReviewBlockedRowCount == 0)
      assert(summary.noOpNoiseRowCount == 0)
    }

    "derive an ES request-leg count of 87 (15 ES-only + 72 combined ES legs)" in {
      val summary = M11BeautyQSearchCandidateGenerationInputSkeleton.DefaultSummary
      val shapes = M11BeautyQSearchCandidateGenerationInputSkeleton.RequestShapes

      assert(summary.esRequestLegRowCount == 87)
      assert(shapes.count(_.hasEsLeg) == 87)
      assert(shapes.count(_.hasEsLeg) == summary.esOnlyRowCount + summary.combinedComparisonRowCount)
      assert(metricValue(summary.metrics, "es_request_leg_row_count") == "87")
    }

    "derive a Qdrant request-leg count of 73 (1 Qdrant-only + 72 combined Qdrant legs)" in {
      val summary = M11BeautyQSearchCandidateGenerationInputSkeleton.DefaultSummary
      val shapes = M11BeautyQSearchCandidateGenerationInputSkeleton.RequestShapes

      assert(summary.qdrantRequestLegRowCount == 73)
      assert(shapes.count(_.hasQdrantLeg) == 73)
      assert(shapes.count(_.hasQdrantLeg) == summary.qdrantOnlyRowCount + summary.combinedComparisonRowCount)
      assert(metricValue(summary.metrics, "qdrant_request_leg_row_count") == "73")
    }

    "derive a combined comparison pair count of 72" in {
      val summary = M11BeautyQSearchCandidateGenerationInputSkeleton.DefaultSummary
      val shapes = M11BeautyQSearchCandidateGenerationInputSkeleton.RequestShapes

      assert(summary.combinedComparisonPairRowCount == 72)
      assert(shapes.count(_.isCombinedComparison) == 72)
      shapes.filter(_.isCombinedComparison).foreach { shape =>
        assert(shape.hasEsLeg && shape.hasQdrantLeg, s"combined shape missing a leg: ${shape.queryId}")
        assert(
          shape.requestLegs == List(
            M11BeautyQSearchCandidateGenerationBackend.Es,
            M11BeautyQSearchCandidateGenerationBackend.Qdrant,
          ),
        )
      }
      assert(metricValue(summary.metrics, "combined_comparison_pair_row_count") == "72")
    }

    "keep accepted negative-control exclusions out of every backend request leg" in {
      val negativeControlShapes = M11BeautyQSearchCandidateGenerationInputSkeleton.RequestShapes
        .filter(_.inputGroup == M10BeautyQSearchM11CandidateGenerationInputGroup.AcceptedNegativeControlExclusionInput)

      assert(negativeControlShapes.size == 1)
      negativeControlShapes.foreach { shape =>
        assert(shape.requestLegs.isEmpty, s"negative-control shape has request legs: ${shape.queryId}")
        assert(!shape.hasEsLeg)
        assert(!shape.hasQdrantLeg)
        assert(!shape.hasAnyRequestLeg)
      }
      assert(
        metricValue(
          M11BeautyQSearchCandidateGenerationInputSkeleton.DefaultSummary.metrics,
          "accepted_negative_control_has_no_backend_request_leg",
        ) == "true",
      )
    }

    "keep manual-review and no-op/noise groups out of every backend request leg" in {
      val nonBackendShapes = M11BeautyQSearchCandidateGenerationInputSkeleton.RequestShapes
        .filter(shape =>
          shape.inputGroup == M10BeautyQSearchM11CandidateGenerationInputGroup.ManualReviewBlockedInput ||
            shape.inputGroup == M10BeautyQSearchM11CandidateGenerationInputGroup.NoOpNoiseInput,
        )

      // Both groups are empty in the accepted dataset, but no such row may ever carry a backend leg.
      nonBackendShapes.foreach { shape =>
        assert(shape.requestLegs.isEmpty, s"manual/no-op shape has request legs: ${shape.queryId}")
      }
      // Defensive: the input-group -> legs mapping yields no legs for either group.
      assert(
        M11BeautyQSearchCandidateGenerationBackend
          .requestLegsFor(M10BeautyQSearchM11CandidateGenerationInputGroup.ManualReviewBlockedInput)
          .isEmpty,
      )
      assert(
        M11BeautyQSearchCandidateGenerationBackend
          .requestLegsFor(M10BeautyQSearchM11CandidateGenerationInputGroup.NoOpNoiseInput)
          .isEmpty,
      )
      assert(
        metricValue(
          M11BeautyQSearchCandidateGenerationInputSkeleton.DefaultSummary.metrics,
          "manual_and_no_op_have_no_backend_request_leg",
        ) == "true",
      )
    }

    "map q_noise_004 to a combined comparison shape with both ES and Qdrant offline legs" in {
      val shape = M11BeautyQSearchCandidateGenerationInputSkeleton
        .requestShapeFor("q_noise_004")
        .getOrElse(fail("missing q_noise_004"))

      assert(shape.category == M10BeautyQSearchQueryCategory.MixedIntent)
      assert(shape.strategyIntent == M10BeautyQSearchOfflineRetrievalStrategyIntent.CombinedEsQdrantComparison)
      assert(shape.inputGroup == M10BeautyQSearchM11CandidateGenerationInputGroup.CombinedEsQdrantComparisonStudyInput)
      assert(shape.isCombinedComparison)
      assert(shape.hasEsLeg && shape.hasQdrantLeg)
      assert(
        shape.requestLegs == List(
          M11BeautyQSearchCandidateGenerationBackend.Es,
          M11BeautyQSearchCandidateGenerationBackend.Qdrant,
        ),
      )
    }

    "map q_noise_005 to an accepted negative-control exclusion with no backend legs" in {
      val shape = M11BeautyQSearchCandidateGenerationInputSkeleton
        .requestShapeFor("q_noise_005")
        .getOrElse(fail("missing q_noise_005"))

      assert(shape.category == M10BeautyQSearchQueryCategory.NoisyAmbiguousNonBeautyIntent)
      assert(shape.strategyIntent == M10BeautyQSearchOfflineRetrievalStrategyIntent.AcceptedNegativeControlExcluded)
      assert(shape.inputGroup == M10BeautyQSearchM11CandidateGenerationInputGroup.AcceptedNegativeControlExclusionInput)
      assert(!shape.isCombinedComparison)
      assert(!shape.hasEsLeg)
      assert(!shape.hasQdrantLeg)
      assert(shape.requestLegs.isEmpty)
    }

    "keep combined request shapes as offline comparison study inputs only, not production hybrid serving" in {
      val combinedShapes = M11BeautyQSearchCandidateGenerationInputSkeleton.RequestShapes
        .filter(_.isCombinedComparison)
      val summary = M11BeautyQSearchCandidateGenerationInputSkeleton.DefaultSummary

      assert(combinedShapes.nonEmpty)
      combinedShapes.foreach { shape =>
        assert(shape.strategyIntent == M10BeautyQSearchOfflineRetrievalStrategyIntent.CombinedEsQdrantComparison)
        assert(shape.inputGroup == M10BeautyQSearchM11CandidateGenerationInputGroup.CombinedEsQdrantComparisonStudyInput)
      }
      assert(!summary.boundary.hybridServingImplied)
      assert(metricValue(summary.metrics, "combined_comparison_is_offline_study_input_not_hybrid_serving") == "true")
      assert(metricValue(summary.metrics, BeautyQSearchEvaluationMetricNames.SharedProductionPosture.HybridServingImplied) == "false")
    }
  }

  "M11BeautyQSearchCandidateGenerationInputSkeleton verdict and boundary" should {

    "carry the offline input-skeleton readiness verdict only, not quality or execution readiness" in {
      val summary = M11BeautyQSearchCandidateGenerationInputSkeleton.DefaultSummary

      assert(summary.verdict == "m11_candidate_generation_input_skeleton_ready")
      assert(summary.m11CandidateGenerationInputSkeletonReady)
      assert(metricValue(summary.metrics, "m11_candidate_generation_input_skeleton_ready") == "true")
      assert(
        metricValue(summary.metrics, "m11_request_shapes_are_offline_study_inputs_not_production_routes") == "true",
      )
      assert(metricValue(summary.metrics, "m11_request_shapes_are_request_shapes_not_backend_execution") == "true")
      assert(!summary.boundary.qualityGreenClaimed)
      assert(!summary.boundary.productionReadinessClaimed)
      assert(!summary.boundary.routeActivationClaimed)
      assert(!summary.boundary.servingApprovalClaimed)
    }

    "require and implement no real backend/client/route/plugin/DI/HTTP path" in {
      val b = M11BeautyQSearchCandidateGenerationInputSkeleton.DefaultSummary.boundary
      val metrics = M11BeautyQSearchCandidateGenerationInputSkeleton.DefaultSummary.metrics

      assert(!b.productionBeautySearchCalled)
      assert(!b.esClientCreated)
      assert(!b.qdrantClientCreated)
      assert(!b.esExecuted)
      assert(!b.qdrantExecuted)
      assert(!b.routePluginDiHttpInvolved)
      assert(!b.realBackendCallRequired)
      assert(!b.realBackendCallImplemented)
      assert(metricValue(metrics, BeautyQSearchEvaluationMetricNames.SharedProductionPosture.ProductionBeautySearchCalled) == "false")
      assert(metricValue(metrics, BeautyQSearchEvaluationMetricNames.SharedProductionPosture.EsClientCreated) == "false")
      assert(metricValue(metrics, BeautyQSearchEvaluationMetricNames.SharedProductionPosture.QdrantClientCreated) == "false")
      assert(metricValue(metrics, BeautyQSearchEvaluationMetricNames.SharedProductionPosture.EsExecuted) == "false")
      assert(metricValue(metrics, BeautyQSearchEvaluationMetricNames.SharedProductionPosture.QdrantExecuted) == "false")
      assert(metricValue(metrics, BeautyQSearchEvaluationMetricNames.SharedProductionPosture.RoutePluginDiHttpInvolved) == "false")
      assert(metricValue(metrics, BeautyQSearchEvaluationMetricNames.SharedProductionPosture.RealBackendCallRequired) == "false")
      assert(metricValue(metrics, BeautyQSearchEvaluationMetricNames.SharedProductionPosture.RealBackendCallImplemented) == "false")
    }

    "keep all production and Qdrant activation boundaries false/not approved" in {
      val b = M11BeautyQSearchCandidateGenerationInputSkeleton.DefaultSummary.boundary
      val metrics = M11BeautyQSearchCandidateGenerationInputSkeleton.DefaultSummary.metrics

      assert(b.defaultBeautySearchEsBacked)
      assert(b.qdrantOptInDisabledByDefault)
      assert(!b.qdrantProductionActivationApproved)
      assert(!b.productionRouteActivated)
      assert(!b.defaultRouteSwitched)
      assert(metricValue(metrics, BeautyQSearchEvaluationMetricNames.SharedProductionPosture.QdrantProductionActivationApproved) == "false")
      assert(metricValue(metrics, BeautyQSearchEvaluationMetricNames.SharedProductionPosture.ProductionRouteActivated) == "false")
      assert(metricValue(metrics, BeautyQSearchEvaluationMetricNames.SharedProductionPosture.DefaultRouteSwitched) == "false")
      assert(metricValue(metrics, BeautyQSearchEvaluationMetricNames.SharedProductionPosture.DefaultBeautySearchEsBacked) == "true")
      assert(metricValue(metrics, BeautyQSearchEvaluationMetricNames.SharedProductionPosture.QdrantOptInDisabledByDefault) == "true")
    }

    "keep forbidden production/hybrid/fallback/fusion/reranking/telemetry boundaries false" in {
      val b = M11BeautyQSearchCandidateGenerationInputSkeleton.DefaultSummary.boundary
      val metrics = M11BeautyQSearchCandidateGenerationInputSkeleton.DefaultSummary.metrics

      assert(!b.hybridServingImplied)
      assert(!b.fallbackImplied)
      assert(!b.scoreFusionImplied)
      assert(!b.rerankingImplied)
      assert(!b.productionTelemetryImplied)
      assert(metricValue(metrics, BeautyQSearchEvaluationMetricNames.SharedProductionPosture.FallbackImplied) == "false")
      assert(metricValue(metrics, BeautyQSearchEvaluationMetricNames.SharedProductionPosture.ScoreFusionImplied) == "false")
      assert(metricValue(metrics, BeautyQSearchEvaluationMetricNames.SharedProductionPosture.RerankingImplied) == "false")
      assert(metricValue(metrics, BeautyQSearchEvaluationMetricNames.SharedProductionPosture.ProductionTelemetryImplied) == "false")
    }
  }

  "M11BeautyQSearchCandidateGenerationInputSkeleton artifact" should {

    "include every required section and offline-study disclaimer" in {
      val rendered = M11BeautyQSearchCandidateGenerationInputSkeleton.MarkdownArtifact.contents

      assert(rendered.contains("## M11 row-group counts"))
      assert(rendered.contains("## Planned offline request-leg counts"))
      assert(rendered.contains("## Representative anchors"))
      assert(rendered.contains("## Noise-probe request shapes"))
      assert(rendered.contains("## Metrics"))
      assert(rendered.contains("## Boundary"))
      assert(rendered.contains("total_query_count: 89"))
      assert(rendered.contains("consumed_m10_readiness_verdict: m11_candidate_generation_inputs_ready_with_negative_control_exclusion"))
      assert(rendered.contains("offline study inputs"))
      assert(rendered.contains("not production routes"))
      assert(rendered.contains("not backend execution"))
    }

    "map q_noise_004 and q_noise_005 explicitly in the noise-probe section" in {
      val rendered = M11BeautyQSearchCandidateGenerationInputSkeleton.MarkdownArtifact.contents
      val probe = section(rendered, "## Noise-probe request shapes")

      assert(probe.contains("| q_noise_004 | mixed_intent | combined_es_qdrant_comparison | combined_es_qdrant_comparison_study_input | es, qdrant |"))
      assert(probe.contains("| q_noise_005 | noisy_ambiguous_non_beauty_intent | accepted_negative_control_excluded | accepted_negative_control_exclusion_input | (none) |"))
    }

    "carry no marketing readiness tokens as positive claims" in {
      val rendered = M11BeautyQSearchCandidateGenerationInputSkeleton.MarkdownArtifact.contents.toLowerCase

      forbiddenRenderedTokens.foreach { token =>
        assert(!rendered.contains(token), s"forbidden token present: $token")
      }
    }

    "render a byte-for-byte deterministic checked-in artifact" in {
      val artifact = M11BeautyQSearchCandidateGenerationInputSkeleton.MarkdownArtifact
      val expected = readResource(artifactPath)

      assert(artifact.filename == "m11-beautyq-candidate-generation-input-skeleton.md")
      assert(artifact.contentType == "text/markdown; charset=utf-8")
      assert(
        artifact.contents == M11BeautyQSearchCandidateGenerationInputSkeletonRenderer
          .renderMarkdown(M11BeautyQSearchCandidateGenerationInputSkeleton.DefaultSummary),
      )
      assert(artifact.contents == expected)
      assert(
        M11BeautyQSearchCandidateGenerationInputSkeletonRenderer
          .renderMarkdown(M11BeautyQSearchCandidateGenerationInputSkeleton.build()) == expected,
      )
    }
  }

  private def metricValue(
    metrics: List[M11BeautyQSearchCandidateGenerationInputSkeletonMetric],
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
