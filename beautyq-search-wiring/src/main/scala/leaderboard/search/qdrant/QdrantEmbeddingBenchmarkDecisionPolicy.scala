package leaderboard.search.qdrant

import java.util.Locale

final case class QdrantEmbeddingBenchmarkDecisionThresholds(
  minVariantRecallAtKDelta: Double,
  minMeanReciprocalRankAtKDelta: Double,
  minProviderHitRateAtKDelta: Double,
  minServiceHitRateAtKDelta: Double,
  maxAllowedLatencyIncreaseMs: Option[Double],
)

sealed trait QdrantEmbeddingBenchmarkDecisionVerdict

object QdrantEmbeddingBenchmarkDecisionVerdict {
  case object KeepBaseline extends QdrantEmbeddingBenchmarkDecisionVerdict
  case object CandidateWorthFurtherEvaluation extends QdrantEmbeddingBenchmarkDecisionVerdict
  case object CandidateWorthSwitching extends QdrantEmbeddingBenchmarkDecisionVerdict
  case object CandidateRejected extends QdrantEmbeddingBenchmarkDecisionVerdict
}

final case class QdrantEmbeddingBenchmarkDecision(
  verdict: QdrantEmbeddingBenchmarkDecisionVerdict,
  reasons: List[String],
  comparison: QdrantEmbeddingBenchmarkComparison,
)

object QdrantEmbeddingBenchmarkDecisionPolicy {
  val ConservativeDefaultThresholds: QdrantEmbeddingBenchmarkDecisionThresholds =
    QdrantEmbeddingBenchmarkDecisionThresholds(
      minVariantRecallAtKDelta = 0.05,
      minMeanReciprocalRankAtKDelta = 0.05,
      minProviderHitRateAtKDelta = 0.0,
      minServiceHitRateAtKDelta = 0.0,
      maxAllowedLatencyIncreaseMs = Some(5.0),
    )

  def decide(
    comparison: QdrantEmbeddingBenchmarkComparison,
    thresholds: QdrantEmbeddingBenchmarkDecisionThresholds = ConservativeDefaultThresholds,
  ): QdrantEmbeddingBenchmarkDecision = {
    val baselineId = comparison.left.candidateId
    val candidateId = comparison.right.candidateId

    val qualityRegressionReasons =
      List(
        Option.when(comparison.variantRecallAtKDelta < 0.0)(
          s"Reject $candidateId against baseline $baselineId: variantRecallAtKDelta=${format(comparison.variantRecallAtKDelta)} is negative"
        ),
        Option.when(comparison.meanReciprocalRankAtKDelta < 0.0)(
          s"Reject $candidateId against baseline $baselineId: meanReciprocalRankAtKDelta=${format(comparison.meanReciprocalRankAtKDelta)} is negative"
        ),
        Option.when(comparison.providerHitRateAtKDelta < thresholds.minProviderHitRateAtKDelta)(
          s"Reject $candidateId against baseline $baselineId: providerHitRateAtKDelta=${format(comparison.providerHitRateAtKDelta)} is below threshold ${format(thresholds.minProviderHitRateAtKDelta)}"
        ),
        Option.when(comparison.serviceHitRateAtKDelta < thresholds.minServiceHitRateAtKDelta)(
          s"Reject $candidateId against baseline $baselineId: serviceHitRateAtKDelta=${format(comparison.serviceHitRateAtKDelta)} is below threshold ${format(thresholds.minServiceHitRateAtKDelta)}"
        ),
      ).flatten

    if (qualityRegressionReasons.nonEmpty) {
      QdrantEmbeddingBenchmarkDecision(
        verdict = QdrantEmbeddingBenchmarkDecisionVerdict.CandidateRejected,
        reasons = qualityRegressionReasons,
        comparison = comparison,
      )
    } else {
      val recallImprovedEnough = comparison.variantRecallAtKDelta >= thresholds.minVariantRecallAtKDelta
      val mrrImprovedEnough = comparison.meanReciprocalRankAtKDelta >= thresholds.minMeanReciprocalRankAtKDelta
      val materiallyImproved = recallImprovedEnough || mrrImprovedEnough
      val anyQualityImprovement =
        comparison.variantRecallAtKDelta > 0.0 ||
          comparison.meanReciprocalRankAtKDelta > 0.0 ||
          comparison.providerHitRateAtKDelta > 0.0 ||
          comparison.serviceHitRateAtKDelta > 0.0

      val latencyReasons = latencyDecisionReasons(comparison, thresholds, baselineId, candidateId)

      if (materiallyImproved && latencyReasons.isEmpty) {
        QdrantEmbeddingBenchmarkDecision(
          verdict = QdrantEmbeddingBenchmarkDecisionVerdict.CandidateWorthSwitching,
          reasons = List(switchingThresholdReason(comparison, baselineId, candidateId, recallImprovedEnough, mrrImprovedEnough)) :::
            List(s"Latency is acceptable relative to baseline $baselineId: meanQueryLatencyMsDelta=${comparison.meanQueryLatencyMsDelta.fold("n/a")(format)}"),
          comparison = comparison,
        )
      } else if (materiallyImproved) {
        QdrantEmbeddingBenchmarkDecision(
          verdict = QdrantEmbeddingBenchmarkDecisionVerdict.CandidateWorthFurtherEvaluation,
          reasons = switchingSignalReasons(comparison, thresholds, baselineId, candidateId) ++ latencyReasons,
          comparison = comparison,
        )
      } else if (anyQualityImprovement && latencyReasons.nonEmpty) {
        QdrantEmbeddingBenchmarkDecision(
          verdict = QdrantEmbeddingBenchmarkDecisionVerdict.CandidateWorthFurtherEvaluation,
          reasons = switchingSignalReasons(comparison, thresholds, baselineId, candidateId) ++ latencyReasons,
          comparison = comparison,
        )
      } else {
        QdrantEmbeddingBenchmarkDecision(
          verdict = QdrantEmbeddingBenchmarkDecisionVerdict.KeepBaseline,
          reasons = keepBaselineReasons(comparison, thresholds, baselineId, candidateId),
          comparison = comparison,
        )
      }
    }
  }

  private def keepBaselineReasons(
    comparison: QdrantEmbeddingBenchmarkComparison,
    thresholds: QdrantEmbeddingBenchmarkDecisionThresholds,
    baselineId: String,
    candidateId: String,
  ): List[String] = {
    val qualityReason =
      s"Keep baseline $baselineId over $candidateId: variantRecallAtKDelta=${format(comparison.variantRecallAtKDelta)} and meanReciprocalRankAtKDelta=${format(comparison.meanReciprocalRankAtKDelta)} do not meet switch thresholds (${format(thresholds.minVariantRecallAtKDelta)}, ${format(thresholds.minMeanReciprocalRankAtKDelta)})"

    val latencyReason = comparison.meanQueryLatencyMsDelta match {
      case Some(delta) if delta > 0.0 =>
        s"Candidate $candidateId is slower than baseline $baselineId: meanQueryLatencyMsDelta=${format(delta)}"
      case Some(delta) =>
        s"Latency does not justify switching by itself: meanQueryLatencyMsDelta=${format(delta)}"
      case None =>
        s"Latency is unknown for candidate $candidateId relative to baseline $baselineId"
    }

    List(qualityReason, latencyReason)
  }

  private def switchingSignalReasons(
    comparison: QdrantEmbeddingBenchmarkComparison,
    thresholds: QdrantEmbeddingBenchmarkDecisionThresholds,
    baselineId: String,
    candidateId: String,
  ): List[String] = {
    val recallReason = Option.when(comparison.variantRecallAtKDelta > 0.0) {
      s"Candidate $candidateId improves variantRecallAtKDelta to ${format(comparison.variantRecallAtKDelta)} versus baseline $baselineId; switch threshold is ${format(thresholds.minVariantRecallAtKDelta)}"
    }
    val mrrReason = Option.when(comparison.meanReciprocalRankAtKDelta > 0.0) {
      s"Candidate $candidateId improves meanReciprocalRankAtKDelta to ${format(comparison.meanReciprocalRankAtKDelta)} versus baseline $baselineId; switch threshold is ${format(thresholds.minMeanReciprocalRankAtKDelta)}"
    }

    List(recallReason, mrrReason).flatten
  }

  private def switchingThresholdReason(
    comparison: QdrantEmbeddingBenchmarkComparison,
    baselineId: String,
    candidateId: String,
    recallImprovedEnough: Boolean,
    mrrImprovedEnough: Boolean,
  ): String =
    (recallImprovedEnough, mrrImprovedEnough) match {
      case (true, true) =>
        s"Candidate $candidateId exceeds switching thresholds versus baseline $baselineId: variantRecallAtKDelta=${format(comparison.variantRecallAtKDelta)}, meanReciprocalRankAtKDelta=${format(comparison.meanReciprocalRankAtKDelta)}"
      case (true, false) =>
        s"Candidate $candidateId exceeds switching threshold versus baseline $baselineId: variantRecallAtKDelta=${format(comparison.variantRecallAtKDelta)}"
      case (false, true) =>
        s"Candidate $candidateId exceeds switching threshold versus baseline $baselineId: meanReciprocalRankAtKDelta=${format(comparison.meanReciprocalRankAtKDelta)}"
      case (false, false) =>
        s"Candidate $candidateId does not exceed switching thresholds versus baseline $baselineId"
    }

  private def latencyDecisionReasons(
    comparison: QdrantEmbeddingBenchmarkComparison,
    thresholds: QdrantEmbeddingBenchmarkDecisionThresholds,
    baselineId: String,
    candidateId: String,
  ): List[String] =
    comparison.meanQueryLatencyMsDelta match {
      case None =>
        List(s"Latency unknown for candidate $candidateId relative to baseline $baselineId: meanQueryLatencyMsDelta=n/a")
      case Some(delta) if delta <= 0.0 =>
        Nil
      case Some(delta) =>
        thresholds.maxAllowedLatencyIncreaseMs match {
          case Some(limit) if delta > limit =>
            List(
              s"Latency increase for candidate $candidateId versus baseline $baselineId is above limit: meanQueryLatencyMsDelta=${format(delta)}, maxAllowedLatencyIncreaseMs=${format(limit)}"
            )
          case _ =>
            Nil
        }
    }

  private def format(value: Double): String =
    String.format(Locale.ROOT, "%+.4f", Double.box(value))
}
