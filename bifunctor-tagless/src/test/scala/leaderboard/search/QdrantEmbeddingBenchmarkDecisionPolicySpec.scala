package leaderboard.search

import leaderboard.search.qdrant.{
  QdrantEmbeddingBenchmarkAggregate,
  QdrantEmbeddingBenchmarkComparison,
  QdrantEmbeddingBenchmarkDecision,
  QdrantEmbeddingBenchmarkDecisionPolicy,
  QdrantEmbeddingBenchmarkDecisionThresholds,
  QdrantEmbeddingBenchmarkDecisionVerdict,
}
import org.scalatest.wordspec.AnyWordSpec

final class QdrantEmbeddingBenchmarkDecisionPolicySpec extends AnyWordSpec {
  "QdrantEmbeddingBenchmarkDecisionPolicy" should {
    "keep baseline when quality is neutral and latency is worse" in {
      val decision = decide(
        comparison(
          variantRecallAtKDelta = 0.0,
          meanReciprocalRankAtKDelta = 0.0,
          providerHitRateAtKDelta = 0.0,
          serviceHitRateAtKDelta = 0.0,
          meanQueryLatencyMsDelta = Some(12.0),
        )
      )

      assert(decision.verdict == QdrantEmbeddingBenchmarkDecisionVerdict.KeepBaseline)
      assert(decision.reasons.exists(_.contains("meanQueryLatencyMsDelta=+12.0000")))
      assert(decision.reasons.exists(_.contains("variantRecallAtKDelta=+0.0000")))
    }

    "mark candidate worth switching when recall improves above threshold with acceptable latency" in {
      val decision = decide(
        comparison(
          variantRecallAtKDelta = 0.08,
          meanReciprocalRankAtKDelta = 0.01,
          providerHitRateAtKDelta = 0.0,
          serviceHitRateAtKDelta = 0.0,
          meanQueryLatencyMsDelta = Some(4.0),
        )
      )

      assert(decision.verdict == QdrantEmbeddingBenchmarkDecisionVerdict.CandidateWorthSwitching)
      assert(decision.reasons.exists(_.contains("variantRecallAtKDelta=+0.0800")))
      assert(decision.reasons.exists(_.contains("candidate-large")))
    }

    "mark candidate worth switching when MRR improves above threshold with acceptable latency" in {
      val decision = decide(
        comparison(
          variantRecallAtKDelta = 0.01,
          meanReciprocalRankAtKDelta = 0.07,
          providerHitRateAtKDelta = 0.0,
          serviceHitRateAtKDelta = 0.0,
          meanQueryLatencyMsDelta = Some(0.0),
        )
      )

      assert(decision.verdict == QdrantEmbeddingBenchmarkDecisionVerdict.CandidateWorthSwitching)
      assert(decision.reasons.exists(_.contains("meanReciprocalRankAtKDelta=+0.0700")))
    }

    "mark candidate worth further evaluation when quality improves and latency is missing" in {
      val decision = decide(
        comparison(
          variantRecallAtKDelta = 0.06,
          meanReciprocalRankAtKDelta = 0.0,
          providerHitRateAtKDelta = 0.0,
          serviceHitRateAtKDelta = 0.0,
          meanQueryLatencyMsDelta = None,
        )
      )

      assert(decision.verdict == QdrantEmbeddingBenchmarkDecisionVerdict.CandidateWorthFurtherEvaluation)
      assert(decision.reasons.exists(_.contains("Latency unknown")))
      assert(decision.reasons.exists(_.contains("improves variantRecallAtKDelta to +0.0600")))
    }

    "reject candidate when provider or service hit rate regresses" in {
      val decision = decide(
        comparison(
          variantRecallAtKDelta = 0.10,
          meanReciprocalRankAtKDelta = 0.10,
          providerHitRateAtKDelta = -0.01,
          serviceHitRateAtKDelta = 0.0,
          meanQueryLatencyMsDelta = Some(1.0),
        )
      )

      assert(decision.verdict == QdrantEmbeddingBenchmarkDecisionVerdict.CandidateRejected)
      assert(decision.reasons.exists(_.contains("providerHitRateAtKDelta=-0.0100")))
    }

    "keep baseline when improvement is below switching threshold" in {
      val decision = decide(
        comparison(
          variantRecallAtKDelta = 0.04,
          meanReciprocalRankAtKDelta = 0.02,
          providerHitRateAtKDelta = 0.0,
          serviceHitRateAtKDelta = 0.0,
          meanQueryLatencyMsDelta = Some(1.0),
        )
      )

      assert(decision.verdict == QdrantEmbeddingBenchmarkDecisionVerdict.KeepBaseline)
      assert(decision.reasons.exists(_.contains("do not meet switch thresholds")))
    }

    "reject candidate when recall or MRR regresses" in {
      val recallDecision = decide(
        comparison(
          variantRecallAtKDelta = -0.05,
          meanReciprocalRankAtKDelta = 0.0,
          providerHitRateAtKDelta = 0.0,
          serviceHitRateAtKDelta = 0.0,
          meanQueryLatencyMsDelta = Some(-3.0),
        )
      )
      val mrrDecision = decide(
        comparison(
          variantRecallAtKDelta = 0.0,
          meanReciprocalRankAtKDelta = -0.02,
          providerHitRateAtKDelta = 0.0,
          serviceHitRateAtKDelta = 0.0,
          meanQueryLatencyMsDelta = Some(-3.0),
        )
      )

      assert(recallDecision.verdict == QdrantEmbeddingBenchmarkDecisionVerdict.CandidateRejected)
      assert(mrrDecision.verdict == QdrantEmbeddingBenchmarkDecisionVerdict.CandidateRejected)
      assert(recallDecision.reasons.exists(_.contains("variantRecallAtKDelta=-0.0500")))
      assert(mrrDecision.reasons.exists(_.contains("meanReciprocalRankAtKDelta=-0.0200")))
    }

    "expose conservative default thresholds" in {
      val defaults = QdrantEmbeddingBenchmarkDecisionPolicy.ConservativeDefaultThresholds

      assert(defaults.minVariantRecallAtKDelta == 0.05)
      assert(defaults.minMeanReciprocalRankAtKDelta == 0.05)
      assert(defaults.minProviderHitRateAtKDelta == 0.0)
      assert(defaults.minServiceHitRateAtKDelta == 0.0)
      assert(defaults.maxAllowedLatencyIncreaseMs.contains(5.0))
    }

    "include useful metric names and candidate ids in reasons" in {
      val decision = decide(
        comparison(
          variantRecallAtKDelta = 0.06,
          meanReciprocalRankAtKDelta = 0.0,
          providerHitRateAtKDelta = 0.0,
          serviceHitRateAtKDelta = 0.0,
          meanQueryLatencyMsDelta = Some(9.0),
        )
      )

      assert(decision.verdict == QdrantEmbeddingBenchmarkDecisionVerdict.CandidateWorthFurtherEvaluation)
      assert(decision.reasons.exists(reason => reason.contains("baseline-small") && reason.contains("candidate-large")))
      assert(decision.reasons.exists(_.contains("meanQueryLatencyMsDelta=+9.0000")))
      assert(decision.reasons.exists(_.contains("maxAllowedLatencyIncreaseMs=+5.0000")))
    }
  }

  private def decide(
    comparison: QdrantEmbeddingBenchmarkComparison,
    thresholds: QdrantEmbeddingBenchmarkDecisionThresholds = QdrantEmbeddingBenchmarkDecisionPolicy.ConservativeDefaultThresholds,
  ): QdrantEmbeddingBenchmarkDecision =
    QdrantEmbeddingBenchmarkDecisionPolicy.decide(
      comparison = comparison,
      thresholds = thresholds,
    )

  private def comparison(
    variantRecallAtKDelta: Double,
    meanReciprocalRankAtKDelta: Double,
    providerHitRateAtKDelta: Double,
    serviceHitRateAtKDelta: Double,
    meanQueryLatencyMsDelta: Option[Double],
  ): QdrantEmbeddingBenchmarkComparison =
    QdrantEmbeddingBenchmarkComparison(
      left = aggregate("baseline-small", meanQueryLatencyMs = Some(20.0)),
      right = aggregate("candidate-large", meanQueryLatencyMs = meanQueryLatencyMsDelta.map(20.0 + _)),
      variantRecallAtKDelta = variantRecallAtKDelta,
      meanReciprocalRankAtKDelta = meanReciprocalRankAtKDelta,
      providerHitRateAtKDelta = providerHitRateAtKDelta,
      serviceHitRateAtKDelta = serviceHitRateAtKDelta,
      meanQueryLatencyMsDelta = meanQueryLatencyMsDelta,
    )

  private def aggregate(
    candidateId: String,
    meanQueryLatencyMs: Option[Double],
  ): QdrantEmbeddingBenchmarkAggregate =
    QdrantEmbeddingBenchmarkAggregate(
      candidateId = candidateId,
      queryCount = 10,
      variantRecallAtK = 0.5,
      meanReciprocalRankAtK = 0.5,
      providerHitRateAtK = 0.5,
      serviceHitRateAtK = 0.5,
      meanQueryLatencyMs = meanQueryLatencyMs,
    )
}
