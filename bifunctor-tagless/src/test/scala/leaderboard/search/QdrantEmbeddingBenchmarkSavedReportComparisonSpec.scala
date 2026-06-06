package leaderboard.search

import leaderboard.model.QueryFailure
import leaderboard.runtime.QueryFailureToThrowable
import leaderboard.search.qdrant.{
  QdrantEmbeddingBenchmarkAggregate,
  QdrantEmbeddingBenchmarkCandidate,
  QdrantEmbeddingBenchmarkCandidateReport,
  QdrantEmbeddingBenchmarkComparison,
  QdrantEmbeddingBenchmarkPlan,
  QdrantEmbeddingBenchmarkQueryMetrics,
  QdrantEmbeddingBenchmarkReport,
  QdrantEmbeddingBenchmarkReportJson,
  QdrantEmbeddingBenchmarkRunMode,
  QdrantEmbeddingBenchmarkSavedReportComparison,
}
import org.scalatest.wordspec.AnyWordSpec

final class QdrantEmbeddingBenchmarkSavedReportComparisonSpec extends AnyWordSpec {
  "QdrantEmbeddingBenchmarkSavedReportComparison" should {
    "compare two single-candidate reports" in {
      val left = report(singleCandidateReport(candidate("qwen3-0_6b"), aggregate("qwen3-0_6b", variantRecallAtK = 0.5, meanReciprocalRankAtK = 0.5)))
      val right = report(singleCandidateReport(candidate("qwen3-4b"), aggregate("qwen3-4b", variantRecallAtK = 1.0, meanReciprocalRankAtK = 1.0)))

      val comparison = compare(left, right)

      assert(comparison.left.candidateId == "qwen3-0_6b")
      assert(comparison.right.candidateId == "qwen3-4b")
    }

    "return positive quality deltas when the right report is better" in {
      val comparison = compare(
        report(singleCandidateReport(candidate("left"), aggregate("left", variantRecallAtK = 0.0, meanReciprocalRankAtK = 0.0, providerHitRateAtK = 0.0, serviceHitRateAtK = 0.0))),
        report(singleCandidateReport(candidate("right"), aggregate("right", variantRecallAtK = 1.0, meanReciprocalRankAtK = 1.0, providerHitRateAtK = 1.0, serviceHitRateAtK = 1.0))),
      )

      assert(comparison.variantRecallAtKDelta > 0.0)
      assert(comparison.meanReciprocalRankAtKDelta > 0.0)
      assert(comparison.providerHitRateAtKDelta > 0.0)
      assert(comparison.serviceHitRateAtKDelta > 0.0)
    }

    "preserve latency delta when both sides have latency" in {
      val comparison = compare(
        report(singleCandidateReport(candidate("left"), aggregate("left", meanQueryLatencyMs = Some(40.0)))),
        report(singleCandidateReport(candidate("right"), aggregate("right", meanQueryLatencyMs = Some(25.5)))),
      )

      assert(comparison.meanQueryLatencyMsDelta.contains(-14.5))
    }

    "return latency delta None when one side lacks latency" in {
      val comparison = compare(
        report(singleCandidateReport(candidate("left"), aggregate("left", meanQueryLatencyMs = None))),
        report(singleCandidateReport(candidate("right"), aggregate("right", meanQueryLatencyMs = Some(25.5)))),
      )

      assert(comparison.meanQueryLatencyMsDelta.isEmpty)
    }

    "fail when left report has zero or multiple candidate reports" in {
      val right = report(singleCandidateReport(candidate("right"), aggregate("right")))
      val zeroFailure = QdrantEmbeddingBenchmarkSavedReportComparison.compareReports(report(), right).swap.toOption.getOrElse(fail("expected failure"))
      val multipleFailure = QdrantEmbeddingBenchmarkSavedReportComparison.compareReports(
        report(
          singleCandidateReport(candidate("left-1"), aggregate("left-1")),
          singleCandidateReport(candidate("left-2"), aggregate("left-2")),
        ),
        right,
      ).swap.toOption.getOrElse(fail("expected failure"))

      assertOperationFailure(zeroFailure, expectedSide = "left", expectedCount = 0)
      assertOperationFailure(multipleFailure, expectedSide = "left", expectedCount = 2)
    }

    "fail when right report has zero or multiple candidate reports" in {
      val left = report(singleCandidateReport(candidate("left"), aggregate("left")))
      val zeroFailure = QdrantEmbeddingBenchmarkSavedReportComparison.compareReports(left, report()).swap.toOption.getOrElse(fail("expected failure"))
      val multipleFailure = QdrantEmbeddingBenchmarkSavedReportComparison.compareReports(
        left,
        report(
          singleCandidateReport(candidate("right-1"), aggregate("right-1")),
          singleCandidateReport(candidate("right-2"), aggregate("right-2")),
        ),
      ).swap.toOption.getOrElse(fail("expected failure"))

      assertOperationFailure(zeroFailure, expectedSide = "right", expectedCount = 0)
      assertOperationFailure(multipleFailure, expectedSide = "right", expectedCount = 2)
    }

    "compare from JSON strings" in {
      val leftJson = QdrantEmbeddingBenchmarkReportJson.encodeReportString(
        report(singleCandidateReport(candidate("qwen3-0_6b"), aggregate("qwen3-0_6b", variantRecallAtK = 0.0)))
      )
      val rightJson = QdrantEmbeddingBenchmarkReportJson.encodeReportString(
        report(singleCandidateReport(candidate("qwen3-4b"), aggregate("qwen3-4b", variantRecallAtK = 1.0)))
      )

      val comparison = QdrantEmbeddingBenchmarkSavedReportComparison.compareReportJsonStrings(leftJson, rightJson).fold(
        failure => fail(s"unexpected comparison failure: $failure", QueryFailureToThrowable(failure)),
        identity,
      )

      assert(comparison.left.candidateId == "qwen3-0_6b")
      assert(comparison.right.candidateId == "qwen3-4b")
      assert(comparison.variantRecallAtKDelta == 1.0)
    }

    "fail clearly on invalid JSON string" in {
      val failure = QdrantEmbeddingBenchmarkSavedReportComparison
        .compareReportJsonStrings("{", QdrantEmbeddingBenchmarkReportJson.encodeReportString(report(singleCandidateReport(candidate("right"), aggregate("right")))))
        .swap
        .toOption
        .getOrElse(fail("expected failure"))

      failure match {
        case QueryFailure.OperationFailure(operationName, message) =>
          assert(operationName == "qdrant-embedding-benchmark-report-json")
          assert(message.contains("Invalid Qdrant embedding benchmark report JSON"))
        case other =>
          fail(s"expected OperationFailure, got $other")
      }
    }

    "format comparison with candidate ids and deltas" in {
      val formatted = QdrantEmbeddingBenchmarkSavedReportComparison.formatComparison(
        QdrantEmbeddingBenchmarkComparison(
          left = aggregate("qwen3-0_6b", meanQueryLatencyMs = Some(40.0)),
          right = aggregate("qwen3-4b", meanQueryLatencyMs = Some(25.0)),
          variantRecallAtKDelta = 1.0,
          meanReciprocalRankAtKDelta = 0.5,
          providerHitRateAtKDelta = 1.0,
          serviceHitRateAtKDelta = 0.0,
          meanQueryLatencyMsDelta = Some(-15.0),
        )
      )

      assert(formatted.contains("leftCandidateId: qwen3-0_6b"))
      assert(formatted.contains("rightCandidateId: qwen3-4b"))
      assert(formatted.contains("variantRecallAtKDelta: +1.0000"))
      assert(formatted.contains("meanReciprocalRankAtKDelta: +0.5000"))
      assert(formatted.contains("providerHitRateAtKDelta: +1.0000"))
      assert(formatted.contains("serviceHitRateAtKDelta: +0.0000"))
      assert(formatted.contains("meanQueryLatencyMsDelta: -15.0000"))
    }

    "compare reports without Qdrant llama or file IO" in {
      val comparison = compare(
        report(singleCandidateReport(candidate("left"), aggregate("left", variantRecallAtK = 0.25))),
        report(singleCandidateReport(candidate("right"), aggregate("right", variantRecallAtK = 0.75))),
      )

      assert(comparison.variantRecallAtKDelta == 0.5)
    }
  }

  private def compare(
    left: QdrantEmbeddingBenchmarkReport,
    right: QdrantEmbeddingBenchmarkReport,
  ): QdrantEmbeddingBenchmarkComparison =
    QdrantEmbeddingBenchmarkSavedReportComparison.compareReports(left, right).fold(
      failure => fail(s"unexpected comparison failure: $failure", QueryFailureToThrowable(failure)),
      identity,
    )

  private def report(candidateReports: QdrantEmbeddingBenchmarkCandidateReport*): QdrantEmbeddingBenchmarkReport =
    QdrantEmbeddingBenchmarkReport(
      plan = QdrantEmbeddingBenchmarkPlan(
        runMode = QdrantEmbeddingBenchmarkRunMode.SingleEndpointManualRestart,
        candidates = candidateReports.toList.map(_.candidate),
        k = 5,
      ),
      candidateReports = candidateReports.toList,
      comparisons = Nil,
    )

  private def singleCandidateReport(
    candidate: QdrantEmbeddingBenchmarkCandidate,
    aggregate: QdrantEmbeddingBenchmarkAggregate,
  ): QdrantEmbeddingBenchmarkCandidateReport =
    QdrantEmbeddingBenchmarkCandidateReport(
      candidate = candidate,
      queryMetrics = List(
        QdrantEmbeddingBenchmarkQueryMetrics(
          queryId = "q-1",
          candidateId = candidate.candidateId,
          variantHitAtK = aggregate.variantRecallAtK > 0.0,
          variantHitRank = if (aggregate.variantRecallAtK > 0.0) Some(1) else None,
          reciprocalRank = aggregate.meanReciprocalRankAtK,
          providerHitAtK = aggregate.providerHitRateAtK > 0.0,
          serviceHitAtK = aggregate.serviceHitRateAtK > 0.0,
        )
      ),
      aggregate = aggregate,
    )

  private def candidate(candidateId: String): QdrantEmbeddingBenchmarkCandidate =
    QdrantEmbeddingBenchmarkCandidate(
      candidateId = candidateId,
      modelName = s"model-$candidateId",
      endpointLabel = s"http://localhost/$candidateId",
      vectorDimension = 1024,
    )

  private def aggregate(
    candidateId: String,
    variantRecallAtK: Double = 0.5,
    meanReciprocalRankAtK: Double = 0.5,
    providerHitRateAtK: Double = 0.5,
    serviceHitRateAtK: Double = 0.5,
    meanQueryLatencyMs: Option[Double] = Some(30.0),
  ): QdrantEmbeddingBenchmarkAggregate =
    QdrantEmbeddingBenchmarkAggregate(
      candidateId = candidateId,
      queryCount = 1,
      variantRecallAtK = variantRecallAtK,
      meanReciprocalRankAtK = meanReciprocalRankAtK,
      providerHitRateAtK = providerHitRateAtK,
      serviceHitRateAtK = serviceHitRateAtK,
      meanQueryLatencyMs = meanQueryLatencyMs,
    )

  private def assertOperationFailure(failure: QueryFailure, expectedSide: String, expectedCount: Int): Unit =
    failure match {
      case QueryFailure.OperationFailure(operationName, message) =>
        assert(operationName == "qdrant-embedding-benchmark-saved-report-comparison")
        assert(message.contains(s"Expected exactly one candidate report in $expectedSide saved report, got $expectedCount"))
        ()
      case other =>
        fail(s"expected OperationFailure, got $other")
    }
}
