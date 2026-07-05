package leaderboard.search

import leaderboard.search.eval.{
  BeautyQSearchEvaluationMetricNames,
  M10BeautyQSearchFullClassificationCoverageAnchorRow,
  M10BeautyQSearchFullClassificationCoverageMetric,
  M10BeautyQSearchFullQueryClassification,
  M10BeautyQSearchFullQueryClassificationCoverageScorecard,
  M10BeautyQSearchFullQueryClassificationCoverageScorecardRenderer,
  M10BeautyQSearchOfflineRetrievalStrategyIntent,
  M10BeautyQSearchQueryCategory,
  M9BeautyQSearchEvalQueryDatasetStaticRows,
}
import org.scalatest.wordspec.AnyWordSpec

import java.nio.charset.StandardCharsets
import scala.io.Source
import scala.util.Using

final class M10BeautyQSearchFullQueryClassificationCoverageSpec extends AnyWordSpec {

  private val artifactPath: String =
    "/leaderboard/search/eval/m10-beautyq-full-query-classification-coverage-scorecard.md"

  // Marketing/readiness tokens that must never appear in the rendered scorecard, even as substrings.
  // Boundary posture is asserted via structured boolean metrics, not by scanning for negated prose.
  private val forbiddenRenderedTokens: List[String] = List(
    "production_ready",
    "qdrant_ready",
    "hybrid_ready",
    "is production ready",
    "production-ready",
  )

  "M10BeautyQSearchFullQueryClassification" should {

    "map every accepted dataset query id exactly once" in {
      val ids = M10BeautyQSearchFullQueryClassification.FullResults.map(_.queryId)
      val expected = M9BeautyQSearchEvalQueryDatasetStaticRows.StaticQueryIds

      assert(expected.size == 89)
      assert(ids.size == 89)
      assert(ids.distinct.size == 89)
      assert(ids.toSet == expected.toSet)
      // Exactly-once: every dataset id resolves to a single classification result.
      expected.foreach { id =>
        assert(M10BeautyQSearchFullQueryClassification.resultFor(id).isDefined, s"missing classification $id")
        assert(ids.count(_ == id) == 1, s"duplicate classification $id")
      }
    }

    "have category counts that sum to 89 and match the accepted distribution" in {
      val counts = M10BeautyQSearchFullQueryClassification.CategoryCounts

      assert(counts.map(_._2).sum == 89)
      M10BeautyQSearchQueryCategory.stableOrder.foreach { category =>
        assert(counts.exists(_._1 == category), s"category not reported: ${category.render}")
      }
      assert(categoryCount(counts, M10BeautyQSearchQueryCategory.ProviderLookup) == 0)
      assert(categoryCount(counts, M10BeautyQSearchQueryCategory.ServiceIntent) == 7)
      assert(categoryCount(counts, M10BeautyQSearchQueryCategory.AttributeFilterIntent) == 6)
      assert(categoryCount(counts, M10BeautyQSearchQueryCategory.LocationIntent) == 1)
      assert(categoryCount(counts, M10BeautyQSearchQueryCategory.PriceBudgetIntent) == 1)
      assert(categoryCount(counts, M10BeautyQSearchQueryCategory.AvailabilityTimeIntent) == 0)
      assert(categoryCount(counts, M10BeautyQSearchQueryCategory.ComparisonExplorationIntent) == 1)
      assert(categoryCount(counts, M10BeautyQSearchQueryCategory.NoisyAmbiguousNonBeautyIntent) == 1)
      assert(categoryCount(counts, M10BeautyQSearchQueryCategory.MixedIntent) == 72)
    }

    "have strategy intent counts that sum to 89 and match the accepted distribution" in {
      val counts = M10BeautyQSearchFullQueryClassification.StrategyIntentCounts

      assert(counts.map(_._2).sum == 89)
      M10BeautyQSearchOfflineRetrievalStrategyIntent.fullCoverageStableOrder.foreach { intent =>
        assert(counts.exists(_._1 == intent), s"strategy intent not reported: ${intent.render}")
      }
      assert(strategyIntentCount(counts, M10BeautyQSearchOfflineRetrievalStrategyIntent.EsOnlyCandidateRetrieval) == 15)
      assert(strategyIntentCount(counts, M10BeautyQSearchOfflineRetrievalStrategyIntent.QdrantOnlyCandidateRetrieval) == 1)
      assert(strategyIntentCount(counts, M10BeautyQSearchOfflineRetrievalStrategyIntent.CombinedEsQdrantComparison) == 72)
      assert(strategyIntentCount(counts, M10BeautyQSearchOfflineRetrievalStrategyIntent.ManualReviewBlocked) == 0)
      assert(strategyIntentCount(counts, M10BeautyQSearchOfflineRetrievalStrategyIntent.NoOpNoise) == 0)
      assert(strategyIntentCount(counts, M10BeautyQSearchOfflineRetrievalStrategyIntent.AcceptedNegativeControlExcluded) == 1)
    }

    "classify q_noise_004 = gel removal as a beauty-domain mixed-intent candidate study input" in {
      val result = M10BeautyQSearchFullQueryClassification.resultFor("q_noise_004").getOrElse(fail("missing q_noise_004"))
      val decision = M10BeautyQSearchFullQueryClassification.decisionFor("q_noise_004").getOrElse(fail("missing q_noise_004"))

      assert(!result.isNoise)
      assert(!result.acceptedNegativeControl)
      assert(!result.manualReviewEligible)
      assert(result.category == M10BeautyQSearchQueryCategory.MixedIntent)
      assert(decision.strategyIntent == M10BeautyQSearchOfflineRetrievalStrategyIntent.CombinedEsQdrantComparison)
      assert(decision.strategyIntent.isBackendCandidateRetrievalIntent)
      assert(!decision.strategyIntent.isUnresolvedManualReview)
    }

    "classify q_price_003 = under 50 as a pure price-budget intent routed to ES-only candidate retrieval" in {
      val result = M10BeautyQSearchFullQueryClassification.resultFor("q_price_003").getOrElse(fail("missing q_price_003"))
      val decision = M10BeautyQSearchFullQueryClassification.decisionFor("q_price_003").getOrElse(fail("missing q_price_003"))

      assert(!result.isNoise)
      assert(!result.acceptedNegativeControl)
      assert(!result.manualReviewEligible)
      assert(result.category == M10BeautyQSearchQueryCategory.PriceBudgetIntent)
      assert(decision.strategyIntent == M10BeautyQSearchOfflineRetrievalStrategyIntent.EsOnlyCandidateRetrieval)
      assert(decision.strategyIntent.isBackendCandidateRetrievalIntent)
      assert(!decision.strategyIntent.isUnresolvedManualReview)
    }

    "keep q_noise_005 = lifting as the accepted noisy/ambiguous negative-control anchor" in {
      val result = M10BeautyQSearchFullQueryClassification.resultFor("q_noise_005").getOrElse(fail("missing q_noise_005"))
      val decision = M10BeautyQSearchFullQueryClassification.decisionFor("q_noise_005").getOrElse(fail("missing q_noise_005"))

      assert(result.isNoise)
      assert(result.category == M10BeautyQSearchQueryCategory.NoisyAmbiguousNonBeautyIntent)
      assert(result.acceptedNegativeControl)
      assert(!result.manualReviewEligible)
      assert(decision.strategyIntent == M10BeautyQSearchOfflineRetrievalStrategyIntent.AcceptedNegativeControlExcluded)
      assert(!decision.strategyIntent.isBackendCandidateRetrievalIntent)
      assert(!decision.strategyIntent.isUnresolvedManualReview)
    }

    "leave no full-dataset row as unresolved manual review" in {
      val unresolved = M10BeautyQSearchFullQueryClassification.FullDecisions
        .filter(_.strategyIntent.isUnresolvedManualReview)

      assert(unresolved.isEmpty)
      assert(M10BeautyQSearchFullQueryClassification.UnresolvedManualReviewQueryIds.isEmpty)
      assert(
        !M10BeautyQSearchFullQueryClassification.FullDecisions
          .exists(_.strategyIntent == M10BeautyQSearchOfflineRetrievalStrategyIntent.ManualReviewBlocked),
      )
    }

    "expose exactly q_noise_005 as the accepted negative-control row" in {
      assert(M10BeautyQSearchFullQueryClassification.AcceptedNegativeControlQueryIds == List("q_noise_005"))
    }

    "decide category from raw offline signals, not from the query id prefix" in {
      // Both ids share the q_noise_* prefix yet land in different categories: the prefix carries no
      // classification meaning; the deterministic offline signals do.
      val noise004 = M10BeautyQSearchFullQueryClassification.resultFor("q_noise_004").getOrElse(fail("missing q_noise_004"))
      val noise005 = M10BeautyQSearchFullQueryClassification.resultFor("q_noise_005").getOrElse(fail("missing q_noise_005"))

      assert(noise004.queryId.startsWith("q_noise_"))
      assert(noise005.queryId.startsWith("q_noise_"))
      assert(noise004.category != noise005.category)
      assert(noise004.category == M10BeautyQSearchQueryCategory.MixedIntent)
      assert(noise005.category == M10BeautyQSearchQueryCategory.NoisyAmbiguousNonBeautyIntent)
      // A non-noise-prefixed beauty row stays beauty; prefixes do not gate either direction.
      val nails001 = M10BeautyQSearchFullQueryClassification.resultFor("q_nails_001").getOrElse(fail("missing q_nails_001"))
      assert(!nails001.isNoise)
    }

    "count exactly 88 backend candidate study inputs and 1 accepted negative-control exclusion" in {
      val backend = M10BeautyQSearchFullQueryClassification.FullDecisions
        .count(_.strategyIntent.isBackendCandidateRetrievalIntent)
      val negativeControls = M10BeautyQSearchFullQueryClassification.AcceptedNegativeControlQueryIds.size

      assert(backend == 88)
      assert(negativeControls == 1)
    }

    "keep mixed intent as offline combined comparison without implying production hybrid serving" in {
      val mixed = M10BeautyQSearchFullQueryClassification.FullResults
        .filter(_.category == M10BeautyQSearchQueryCategory.MixedIntent)

      assert(mixed.nonEmpty)
      mixed.foreach { result =>
        val decision = M10BeautyQSearchFullQueryClassification.decisionFor(result.queryId).getOrElse(fail(result.queryId))
        assert(decision.strategyIntent == M10BeautyQSearchOfflineRetrievalStrategyIntent.CombinedEsQdrantComparison)
        assert(!decision.boundary.hybridServingImplied)
        assert(!decision.rationale.toLowerCase.contains("hybrid"))
      }
    }

    "never route noisy/ambiguous/non-beauty rows to backend-candidate study intents" in {
      val noisy = M10BeautyQSearchFullQueryClassification.FullResults
        .filter(_.category == M10BeautyQSearchQueryCategory.NoisyAmbiguousNonBeautyIntent)

      assert(noisy.nonEmpty)
      noisy.foreach { result =>
        assert(result.isNoise)
        val decision = M10BeautyQSearchFullQueryClassification.decisionFor(result.queryId).getOrElse(fail(result.queryId))
        assert(!decision.strategyIntent.isBackendCandidateRetrievalIntent, s"noisy routed to backend: ${result.queryId}")
      }
    }

    "require and implement no real backend/client/route/plugin/DI/HTTP path" in {
      M10BeautyQSearchFullQueryClassification.FullDecisions.foreach { decision =>
        val b = decision.boundary
        assert(!b.productionBeautySearchCalled)
        assert(!b.esClientCreated)
        assert(!b.qdrantClientCreated)
        assert(!b.esExecuted)
        assert(!b.qdrantExecuted)
        assert(!b.routePluginDiHttpInvolved)
        assert(!b.realBackendCallImplemented)
        assert(!b.realBackendCallRequired)
      }
    }
  }

  "M10BeautyQSearchFullQueryClassificationCoverageScorecard" should {

    "report semantic readiness counts including negative-control exclusion and unresolved manual review" in {
      val summary = M10BeautyQSearchFullQueryClassificationCoverageScorecard.DefaultSummary

      assert(summary.totalQueryCount == 89)
      assert(summary.mappedRowCount == 89)
      assert(summary.mixedIntentCount == 72)
      assert(summary.noisyRowCount == 1)
      assert(summary.unresolvedManualReviewRowCount == 0)
      assert(summary.acceptedNegativeControlExclusionCount == 1)
      assert(summary.noOpRowCount == 0)
      assert(summary.backendCandidateStudyIntentCount == 88)
      assert(summary.acceptedNegativeControlRows == List("q_noise_005"))
      assert(summary.unresolvedManualReviewRows.isEmpty)
      assert(summary.categoryCounts.map(_._2).sum == 89)
      assert(summary.strategyIntentCounts.map(_._2).sum == 89)
    }

    "prove M11 backend candidate inputs are ready via negative-control exclusion, not blocked by manual review" in {
      val summary = M10BeautyQSearchFullQueryClassificationCoverageScorecard.DefaultSummary

      assert(summary.m11BackendCandidateInputsReady)
      assert(summary.unresolvedManualReviewRowCount == 0)
      assert(summary.acceptedNegativeControlExclusionCount == 1)
      assert(summary.backendCandidateStudyIntentCount == 88)
      assert(metricValue(summary.metrics, "m11_backend_candidate_inputs_ready") == "true")
      assert(metricValue(summary.metrics, "unresolved_manual_review_row_count") == "0")
      assert(metricValue(summary.metrics, "accepted_negative_control_exclusion_count") == "1")
      assert(metricValue(summary.metrics, "backend_candidate_study_intent_count") == "88")
    }

    "expose semantic metric keys exactly and drop the aggregate manual_review_row_count proxy" in {
      val summary = M10BeautyQSearchFullQueryClassificationCoverageScorecard.DefaultSummary
      val keys = summary.metrics.map(_.name).toSet

      assert(keys.contains("unresolved_manual_review_row_count"))
      assert(keys.contains("accepted_negative_control_exclusion_count"))
      assert(keys.contains("m11_backend_candidate_inputs_ready"))
      assert(!keys.contains("manual_review_row_count"))
      assert(metricValue(summary.metrics, "total_query_count") == "89")
      assert(metricValue(summary.metrics, "mapped_row_count") == "89")
      assert(metricValue(summary.metrics, "mixed_intent_count") == "72")
      assert(metricValue(summary.metrics, "noisy_row_count") == "1")
      assert(metricValue(summary.metrics, "no_op_row_count") == "0")
    }

    "expose representative anchor rows including the negative-control anchor" in {
      val summary = M10BeautyQSearchFullQueryClassificationCoverageScorecard.DefaultSummary
      val ids = summary.anchorRows.map(_.queryId)

      assert(ids == List("q_nails_001", "q_nails_003", "q_noise_005"))
      assert(anchorRow(summary.anchorRows, "q_nails_001").category == M10BeautyQSearchQueryCategory.MixedIntent)
      assert(anchorRow(summary.anchorRows, "q_nails_003").category == M10BeautyQSearchQueryCategory.AttributeFilterIntent)
      assert(anchorRow(summary.anchorRows, "q_noise_005").category == M10BeautyQSearchQueryCategory.NoisyAmbiguousNonBeautyIntent)
      assert(
        anchorRow(summary.anchorRows, "q_noise_005").strategyIntent ==
          M10BeautyQSearchOfflineRetrievalStrategyIntent.AcceptedNegativeControlExcluded,
      )
    }

    "list accepted negative-control rows explicitly and render (none) for unresolved manual review" in {
      val rendered = M10BeautyQSearchFullQueryClassificationCoverageScorecard.MarkdownArtifact.contents

      val negSection = section(rendered, "## Accepted negative-control rows")
      val manualSection = section(rendered, "## Unresolved manual-review rows")
      assert(negSection.contains("- q_noise_005"))
      assert(manualSection.contains("(none)"))
      assert(!manualSection.contains("- q_"))
    }

    "keep all production and Qdrant activation boundaries false via structured boolean metrics" in {
      val summary = M10BeautyQSearchFullQueryClassificationCoverageScorecard.DefaultSummary
      val b = summary.boundary

      assert(b.defaultBeautySearchEsBacked)
      assert(b.qdrantOptInDisabledByDefault)
      assert(!b.qdrantProductionActivationApproved)
      assert(!b.productionRouteActivated)
      assert(!b.defaultRouteSwitched)
      assert(!b.routeActivationClaimed)
      assert(!b.servingApprovalClaimed)
      assert(!b.qualityGreenClaimed)
      assert(!b.productionReadinessClaimed)
      assert(!b.hybridServingImplied)
      assert(!b.fallbackImplied)
      assert(!b.scoreFusionImplied)
      assert(!b.rerankingImplied)
      assert(!b.productionTelemetryImplied)

      assert(metricValue(summary.metrics, "offline_strategy_intent_is_not_production_routing") == "true")
      assert(metricValue(summary.metrics, BeautyQSearchEvaluationMetricNames.SharedProductionPosture.DefaultBeautySearchEsBacked) == "true")
      assert(metricValue(summary.metrics, BeautyQSearchEvaluationMetricNames.SharedProductionPosture.QdrantOptInDisabledByDefault) == "true")
      assert(metricValue(summary.metrics, BeautyQSearchEvaluationMetricNames.SharedProductionPosture.QdrantProductionActivationApproved) == "false")
      assert(metricValue(summary.metrics, BeautyQSearchEvaluationMetricNames.SharedProductionPosture.ProductionRouteActivated) == "false")
      assert(metricValue(summary.metrics, BeautyQSearchEvaluationMetricNames.SharedProductionPosture.DefaultRouteSwitched) == "false")
      assert(metricValue(summary.metrics, BeautyQSearchEvaluationMetricNames.SharedProductionPosture.ProductionBeautySearchCalled) == "false")
      assert(metricValue(summary.metrics, BeautyQSearchEvaluationMetricNames.SharedProductionPosture.EsExecuted) == "false")
      assert(metricValue(summary.metrics, BeautyQSearchEvaluationMetricNames.SharedProductionPosture.QdrantExecuted) == "false")
      assert(metricValue(summary.metrics, BeautyQSearchEvaluationMetricNames.SharedProductionPosture.RoutePluginDiHttpInvolved) == "false")
      assert(metricValue(summary.metrics, BeautyQSearchEvaluationMetricNames.SharedProductionPosture.RealBackendCallRequired) == "false")
      assert(metricValue(summary.metrics, BeautyQSearchEvaluationMetricNames.SharedProductionPosture.RealBackendCallImplemented) == "false")
      assert(metricValue(summary.metrics, BeautyQSearchEvaluationMetricNames.SharedProductionPosture.HybridServingImplied) == "false")
      assert(metricValue(summary.metrics, BeautyQSearchEvaluationMetricNames.SharedProductionPosture.FallbackImplied) == "false")
      assert(metricValue(summary.metrics, BeautyQSearchEvaluationMetricNames.SharedProductionPosture.ScoreFusionImplied) == "false")
      assert(metricValue(summary.metrics, BeautyQSearchEvaluationMetricNames.SharedProductionPosture.RerankingImplied) == "false")
      assert(metricValue(summary.metrics, BeautyQSearchEvaluationMetricNames.SharedProductionPosture.ProductionTelemetryImplied) == "false")
      assert(metricValue(summary.metrics, BeautyQSearchEvaluationMetricNames.SharedProductionPosture.QualityGreenClaimed) == "false")
      assert(metricValue(summary.metrics, BeautyQSearchEvaluationMetricNames.SharedProductionPosture.ProductionReadinessClaimed) == "false")
      assert(metricValue(summary.metrics, BeautyQSearchEvaluationMetricNames.SharedProductionPosture.RouteActivationClaimed) == "false")
      assert(metricValue(summary.metrics, BeautyQSearchEvaluationMetricNames.SharedProductionPosture.ServingApprovalClaimed) == "false")
    }

    "carry the readiness-safe verdict and no marketing readiness tokens" in {
      val summary = M10BeautyQSearchFullQueryClassificationCoverageScorecard.DefaultSummary
      val rendered = M10BeautyQSearchFullQueryClassificationCoverageScorecard.MarkdownArtifact.contents.toLowerCase

      assert(summary.verdict == "full_query_classification_coverage_ready_with_negative_control_exclusion")
      assert(metricValue(summary.metrics, "verdict") == summary.verdict)
      forbiddenRenderedTokens.foreach { token =>
        assert(summary.verdict != token)
        assert(!rendered.contains(token), s"forbidden token present: $token")
      }
    }

    "render a byte-for-byte deterministic checked-in artifact" in {
      val artifact = M10BeautyQSearchFullQueryClassificationCoverageScorecard.MarkdownArtifact
      val expected = readResource(artifactPath)

      assert(artifact.filename == "m10-beautyq-full-query-classification-coverage-scorecard.md")
      assert(artifact.contentType == "text/markdown; charset=utf-8")
      assert(
        artifact.contents == M10BeautyQSearchFullQueryClassificationCoverageScorecardRenderer
          .renderMarkdown(M10BeautyQSearchFullQueryClassificationCoverageScorecard.DefaultSummary),
      )
      assert(artifact.contents == expected)
      // Determinism: rebuilding the summary renders identical bytes.
      assert(
        M10BeautyQSearchFullQueryClassificationCoverageScorecardRenderer
          .renderMarkdown(M10BeautyQSearchFullQueryClassificationCoverageScorecard.build()) == expected,
      )
    }
  }

  private def metricValue(
    metrics: List[M10BeautyQSearchFullClassificationCoverageMetric],
    name: String,
  ): String =
    metrics.find(_.name == name) match {
      case Some(metric) => metric.value
      case None         => fail(s"missing scorecard metric $name")
    }

  private def categoryCount(
    counts: List[(M10BeautyQSearchQueryCategory, Int)],
    category: M10BeautyQSearchQueryCategory,
  ): Int =
    counts.find(_._1 == category) match {
      case Some((_, count)) => count
      case None            => fail(s"missing category count ${category.render}")
    }

  private def strategyIntentCount(
    counts: List[(M10BeautyQSearchOfflineRetrievalStrategyIntent, Int)],
    intent: M10BeautyQSearchOfflineRetrievalStrategyIntent,
  ): Int =
    counts.find(_._1 == intent) match {
      case Some((_, count)) => count
      case None             => fail(s"missing strategy intent count ${intent.render}")
    }

  private def anchorRow(
    rows: List[M10BeautyQSearchFullClassificationCoverageAnchorRow],
    queryId: String,
  ): M10BeautyQSearchFullClassificationCoverageAnchorRow =
    rows.find(_.queryId == queryId) match {
      case Some(row) => row
      case None      => fail(s"missing anchor row $queryId")
    }

  /** Extract a `##`-delimited section body so row lists are asserted within their own section. */
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
