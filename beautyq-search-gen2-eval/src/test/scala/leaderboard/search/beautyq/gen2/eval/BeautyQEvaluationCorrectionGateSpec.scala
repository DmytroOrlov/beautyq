package leaderboard.search.beautyq.gen2.eval

import leaderboard.search.beautyq.gen2.wiring.*
import leaderboard.search.beautyq.gen2.wiring.testkit.BeautyQOrchestrationTestKit
import org.scalatest.wordspec.AnyWordSpec

final class BeautyQEvaluationCorrectionGateSpec extends AnyWordSpec {
  "BeautyQEvaluationCorrectionGate" should {
    "accept complete deterministic hard and no-harm evidence" in {
      val observed = measuredCase()
      val gate = evaluate(observed)

      assert(gate.passed)
      assert(gate.checks.map(_.stableCode) == Vector(
        "required-full-search",
        "complete-warmup",
        "complete-measured-passes",
        "deterministic-measured-rankings",
        "no-request-degradation",
        "no-public-identity-duplicates",
        "no-forbidden-hits",
        "baseline-prefix-preserved",
        "baseline-owned-components-preserved",
        "append-budget-preserved",
      ))
      assert(gate.toJson.hcursor.get[String]("qualityThresholdStatus").contains("not_required"))
      assert(gate.toJson.hcursor.get[String]("protectedHoldoutStatus").contains("not_configured"))
      assert(gate.toJson.hcursor.get[String]("acceptedBaselineStatus").contains("not_generated"))
    }

    "derive a threshold candidate only from strictly separated supplement scores" in {
      def observation(score: String, judgment: String) =
        new BeautyQSupplementScoreObservation("case", "query", "result", "qdrant_supplement", BigDecimal(score), judgment, "supplemented", None, Vector.empty, Vector("result"))

      val separated = BeautyQSupplementScoreSeparation.from(Vector(
        observation("0.40", "forbidden"),
        observation("0.60", "acceptable"),
      ))
      assert(separated.strictlySeparable)
      assert(separated.candidateThreshold.contains(BigDecimal("0.50")))

      val overlapping = BeautyQSupplementScoreSeparation.from(Vector(
        observation("0.55", "forbidden"),
        observation("0.45", "acceptable"),
      ))
      assert(!overlapping.strictlySeparable)
      assert(overlapping.candidateThreshold.isEmpty)
    }

    "reject every hard and no-harm violation without a relevance threshold" in {
      val base = measuredCase()
      val cases = Vector(
        "no-request-degradation" -> BeautyQMeasuredCase.syntheticForGate(base, requestDegraded = Some(true)),
        "no-public-identity-duplicates" -> BeautyQMeasuredCase.syntheticForGate(base, duplicateIdentityCount = Some(1)),
        "no-forbidden-hits" -> BeautyQMeasuredCase.syntheticForGate(base, forbiddenHitCount = Some(1)),
        "baseline-prefix-preserved" -> BeautyQMeasuredCase.syntheticForGate(base, baselinePrefixPreserved = Some(false)),
        "baseline-owned-components-preserved" -> BeautyQMeasuredCase.syntheticForGate(base, baselineOwnedComponentsPreserved = Some(false)),
        "append-budget-preserved" -> BeautyQMeasuredCase.syntheticForGate(base, appendBudgetPreserved = Some(false)),
      )
      cases.foreach { case (expectedFailure, observed) =>
        val gate = evaluate(observed)
        assert(!gate.passed)
        assert(gate.checks.exists(check => check.stableCode == expectedFailure && !check.passed))
      }
    }

    "reject non-required mode and incomplete pass counts" in {
      val observed = measuredCase()
      val baselineOnly = BeautyQEvaluationCorrectionGate.evaluate(
        SupplementStartupPolicy.Disabled,
        BeautyQServingMode.BaselineOnly,
        1,
        Vector(observed),
        Vector(Vector(observed), Vector(observed), Vector(observed)),
        deterministic = true,
      )
      assert(!baselineOnly.passed)
      assert(baselineOnly.checks.exists(check => check.stableCode == "required-full-search" && !check.passed))

      val incomplete = BeautyQEvaluationCorrectionGate.evaluate(
        SupplementStartupPolicy.Required,
        BeautyQServingMode.FullSearch,
        2,
        Vector(observed),
        Vector(Vector(observed)),
        deterministic = false,
      )
      assert(!incomplete.passed)
      assert(incomplete.checks.exists(check => check.stableCode == "complete-warmup" && !check.passed))
      assert(incomplete.checks.exists(check => check.stableCode == "complete-measured-passes" && !check.passed))
      assert(incomplete.checks.exists(check => check.stableCode == "deterministic-measured-rankings" && !check.passed))
    }

  }

  private def evaluate(observed: BeautyQMeasuredCase): BeautyQEvaluationCorrectionGateResult =
    BeautyQEvaluationCorrectionGate.evaluate(
      SupplementStartupPolicy.Required,
      BeautyQServingMode.FullSearch,
      1,
      Vector(observed),
      Vector(Vector(observed), Vector(observed), Vector(observed)),
      deterministic = true,
    )

  private def measuredCase(): BeautyQMeasuredCase = {
    val corpus = BeautyQEvaluationCorpus.loadCanonical() match {
      case Right(value) => value
      case Left(error)  => fail(s"expected corpus, got $error")
    }
    val current = corpus.cases match {
      case value +: _ => value
      case _          => fail("expected corpus case")
    }
    val context = BeautyQOrchestrationTestKit.eligible()
    val application = BeautyQSearchApplication.make(
      BeautyQOrchestrationTestKit.materialized,
      context.baselineService,
      context.embedding,
      context.qdrant,
    )
    val result = application.execute(context.request) match {
      case Right(value) => value
      case Left(error)  => fail(s"expected application result, got $error")
    }
    val response = BeautyQSearchResponseGen2Projector.projectWithoutStatus(result) match {
      case Right(value) => value
      case Left(error)  => fail(s"expected response, got $error")
    }
    BeautyQMeasuredCase.fromExecution(current, result, response, 10L, "measured[1]") match {
      case Right(value) => value
      case Left(error)  => fail(s"expected measured case, got $error")
    }
  }
}
