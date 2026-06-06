package leaderboard.search

import leaderboard.model.{MasterLocationId, MasterServiceOfferVariantId, ServiceId}
import leaderboard.search.qdrant.{
  QdrantEmbeddingBenchmarkAggregate,
  QdrantEmbeddingBenchmark,
  QdrantEmbeddingBenchmarkCandidate,
  QdrantEmbeddingBenchmarkExpected,
  QdrantEmbeddingBenchmarkPlan,
  QdrantEmbeddingBenchmarkQueryResult,
  QdrantEmbeddingBenchmarkRunMode,
}
import org.scalatest.wordspec.AnyWordSpec

import java.util.UUID

final class QdrantEmbeddingBenchmarkSpec extends AnyWordSpec {
  "QdrantEmbeddingBenchmark" should {
    "calculate variant Hit@K when an acceptable variant is in top K" in {
      val result = queryResult(topVariantIds = List(variantId(1), variantId(2), variantId(3)))
      val expected = expectedResult(acceptableVariantIds = List(variantId(2)))

      val metrics = QdrantEmbeddingBenchmark.metricsFor(result, expected, k = 2)

      assert(metrics.variantHitAtK)
      assert(metrics.variantHitRank.contains(2))
    }

    "fail variant Hit@K when an acceptable variant is outside top K" in {
      val result = queryResult(topVariantIds = List(variantId(1), variantId(2), variantId(3)))
      val expected = expectedResult(acceptableVariantIds = List(variantId(3)))

      val metrics = QdrantEmbeddingBenchmark.metricsFor(result, expected, k = 2)

      assert(!metrics.variantHitAtK)
      assert(metrics.variantHitRank.isEmpty)
      assert(metrics.reciprocalRank == 0.0)
    }

    "calculate reciprocal rank from the first acceptable variant rank" in {
      val result = queryResult(topVariantIds = List(variantId(1), variantId(2), variantId(3), variantId(4)))
      val expected = expectedResult(acceptableVariantIds = List(variantId(4), variantId(2)))

      val metrics = QdrantEmbeddingBenchmark.metricsFor(result, expected, k = 4)

      assert(metrics.variantHitRank.contains(2))
      assert(metrics.reciprocalRank == 0.5)
    }

    "calculate provider Hit@K" in {
      val result = queryResult(topProviderIds = List(providerId(1), providerId(2), providerId(3)))
      val expected = expectedResult(acceptableProviderIds = List(providerId(2)))

      val metrics = QdrantEmbeddingBenchmark.metricsFor(result, expected, k = 2)

      assert(metrics.providerHitAtK)
    }

    "calculate service Hit@K" in {
      val result = queryResult(topServiceIds = List(serviceId(1), serviceId(2), serviceId(3)))
      val expected = expectedResult(acceptableServiceIds = List(serviceId(2)))

      val metrics = QdrantEmbeddingBenchmark.metricsFor(result, expected, k = 2)

      assert(metrics.serviceHitAtK)
    }

    "aggregate recall, MRR, and hit rates" in {
      val first = QdrantEmbeddingBenchmark.metricsFor(
        queryResult(queryId = "q1", topVariantIds = List(variantId(1)), topProviderIds = List(providerId(1)), topServiceIds = List(serviceId(1))),
        expectedResult(acceptableVariantIds = List(variantId(1)), acceptableProviderIds = List(providerId(1)), acceptableServiceIds = List(serviceId(9))),
        k = 3,
      )
      val second = QdrantEmbeddingBenchmark.metricsFor(
        queryResult(queryId = "q2", topVariantIds = List(variantId(2), variantId(3)), topProviderIds = List(providerId(2)), topServiceIds = List(serviceId(2))),
        expectedResult(acceptableVariantIds = List(variantId(3)), acceptableProviderIds = List(providerId(9)), acceptableServiceIds = List(serviceId(2))),
        k = 3,
      )
      val third = QdrantEmbeddingBenchmark.metricsFor(
        queryResult(queryId = "q3", topVariantIds = List(variantId(4)), topProviderIds = List(providerId(4)), topServiceIds = List(serviceId(4))),
        expectedResult(acceptableVariantIds = List(variantId(9)), acceptableProviderIds = List(providerId(4)), acceptableServiceIds = List(serviceId(9))),
        k = 3,
      )

      val aggregate = QdrantEmbeddingBenchmark.aggregate(List(first, second, third), Nil)

      assert(aggregate.candidateId == candidateId)
      assert(aggregate.queryCount == 3)
      assert(aggregate.variantRecallAtK == 2.0 / 3.0)
      assert(aggregate.meanReciprocalRankAtK == 0.5)
      assert(aggregate.providerHitRateAtK == 2.0 / 3.0)
      assert(aggregate.serviceHitRateAtK == 1.0 / 3.0)
    }

    "calculate mean latency from present latency values only" in {
      val results = List(
        queryResult(queryId = "q1", queryLatencyMs = Some(10)),
        queryResult(queryId = "q2", queryLatencyMs = None),
        queryResult(queryId = "q3", queryLatencyMs = Some(30)),
      )
      val metrics = results.map(result => QdrantEmbeddingBenchmark.metricsFor(result, expectedResult(), k = 3))

      val aggregate = QdrantEmbeddingBenchmark.aggregate(metrics, results)

      assert(aggregate.meanQueryLatencyMs.contains(20.0))
    }

    "candidateReport computes per-query metrics and aggregate for one candidate" in {
      val candidate = QdrantEmbeddingBenchmarkCandidate("qwen3-0_6b", "Qwen3-Embedding-0.6B", "http://localhost:8081", 1024)
      val queryResults = List(
        queryResult(
          candidateId = candidate.candidateId,
          queryId = "q1",
          topVariantIds = List(variantId(1), variantId(2)),
          topProviderIds = List(providerId(1), providerId(2)),
          topServiceIds = List(serviceId(1), serviceId(2)),
          queryLatencyMs = Some(12),
        ),
        queryResult(
          candidateId = candidate.candidateId,
          queryId = "q2",
          topVariantIds = List(variantId(3), variantId(4)),
          topProviderIds = List(providerId(3), providerId(4)),
          topServiceIds = List(serviceId(3), serviceId(4)),
          queryLatencyMs = Some(24),
        ),
      )
      val expectations = Map(
        "q1" -> expectedResult(acceptableVariantIds = List(variantId(2)), acceptableProviderIds = List(providerId(2)), acceptableServiceIds = List(serviceId(2))),
        "q2" -> expectedResult(acceptableVariantIds = List(variantId(4)), acceptableProviderIds = List(providerId(4)), acceptableServiceIds = List(serviceId(4))),
      )

      val report = QdrantEmbeddingBenchmark.candidateReport(candidate, queryResults, expectations, k = 2)

      assert(report.candidate == candidate)
      assert(report.queryMetrics.map(_.queryId) == List("q1", "q2"))
      assert(report.queryMetrics.forall(_.variantHitAtK))
      assert(report.aggregate.candidateId == candidate.candidateId)
      assert(report.aggregate.queryCount == 2)
      assert(report.aggregate.variantRecallAtK == 1.0)
      assert(report.aggregate.meanReciprocalRankAtK == 0.5)
      assert(report.aggregate.providerHitRateAtK == 1.0)
      assert(report.aggregate.serviceHitRateAtK == 1.0)
      assert(report.aggregate.meanQueryLatencyMs.contains(18.0))
    }

    "report preserves SingleEndpointManualRestart mode and one candidate report" in {
      val candidate = QdrantEmbeddingBenchmarkCandidate("qwen3-0_6b", "Qwen3-Embedding-0.6B", "http://localhost:8081", 1024)
      val plan = QdrantEmbeddingBenchmarkPlan(QdrantEmbeddingBenchmarkRunMode.SingleEndpointManualRestart, List(candidate), k = 3)
      val queryResultsByCandidateId = Map(
        candidate.candidateId -> List(
          queryResult(
            candidateId = candidate.candidateId,
            queryId = "q1",
            topVariantIds = List(variantId(1)),
            topProviderIds = List(providerId(1)),
            topServiceIds = List(serviceId(1)),
            queryLatencyMs = Some(5),
          )
        )
      )
      val expectations = Map(
        "q1" -> expectedResult(acceptableVariantIds = List(variantId(1)), acceptableProviderIds = List(providerId(1)), acceptableServiceIds = List(serviceId(1)))
      )

      val report = QdrantEmbeddingBenchmark.report(plan, queryResultsByCandidateId, expectations)

      assert(report.plan.runMode == QdrantEmbeddingBenchmarkRunMode.SingleEndpointManualRestart)
      assert(report.plan.k == 3)
      assert(report.candidateReports.size == 1)
      assert(report.candidateReports.head.candidate == candidate)
      assert(report.comparisons.isEmpty)
    }

    "report preserves DualEndpointParallel mode and creates one comparison for two candidates" in {
      val leftCandidate = QdrantEmbeddingBenchmarkCandidate("qwen3-0_6b", "Qwen3-Embedding-0.6B", "http://localhost:8081", 1024)
      val rightCandidate = QdrantEmbeddingBenchmarkCandidate("qwen3-4b", "Qwen3-Embedding-4B", "http://localhost:8082", 2560)
      val plan = QdrantEmbeddingBenchmarkPlan(QdrantEmbeddingBenchmarkRunMode.DualEndpointParallel, List(leftCandidate, rightCandidate), k = 2)
      val queryResultsByCandidateId = Map(
        leftCandidate.candidateId -> List(
          queryResult(
            candidateId = leftCandidate.candidateId,
            queryId = "q1",
            topVariantIds = List(variantId(9), variantId(8)),
            topProviderIds = List(providerId(9)),
            topServiceIds = List(serviceId(9)),
            queryLatencyMs = Some(40),
          )
        ),
        rightCandidate.candidateId -> List(
          queryResult(
            candidateId = rightCandidate.candidateId,
            queryId = "q1",
            topVariantIds = List(variantId(1), variantId(9)),
            topProviderIds = List(providerId(1)),
            topServiceIds = List(serviceId(1)),
            queryLatencyMs = Some(20),
          )
        ),
      )
      val expectations = Map(
        "q1" -> expectedResult(acceptableVariantIds = List(variantId(1)), acceptableProviderIds = List(providerId(1)), acceptableServiceIds = List(serviceId(1)))
      )

      val report = QdrantEmbeddingBenchmark.report(plan, queryResultsByCandidateId, expectations)

      assert(report.plan.runMode == QdrantEmbeddingBenchmarkRunMode.DualEndpointParallel)
      assert(report.candidateReports.map(_.candidate) == List(leftCandidate, rightCandidate))
      assert(report.comparisons.size == 1)
    }

    "report keeps candidate reports in plan.candidates order" in {
      val firstCandidate = QdrantEmbeddingBenchmarkCandidate("qwen3-4b", "Qwen3-Embedding-4B", "http://localhost:8082", 2560)
      val secondCandidate = QdrantEmbeddingBenchmarkCandidate("qwen3-0_6b", "Qwen3-Embedding-0.6B", "http://localhost:8081", 1024)
      val plan = QdrantEmbeddingBenchmarkPlan(QdrantEmbeddingBenchmarkRunMode.DualEndpointParallel, List(firstCandidate, secondCandidate), k = 1)
      val queryResultsByCandidateId = Map(
        firstCandidate.candidateId -> List(queryResult(candidateId = firstCandidate.candidateId, queryId = "q1", topVariantIds = List(variantId(1)))),
        secondCandidate.candidateId -> List(queryResult(candidateId = secondCandidate.candidateId, queryId = "q1", topVariantIds = List(variantId(1)))),
      )
      val expectations = Map("q1" -> expectedResult(acceptableVariantIds = List(variantId(1))))

      val report = QdrantEmbeddingBenchmark.report(plan, queryResultsByCandidateId, expectations)

      assert(report.candidateReports.map(_.candidate) == List(firstCandidate, secondCandidate))
    }

    "report normalizes negative k to 0" in {
      val candidate = QdrantEmbeddingBenchmarkCandidate("qwen3-0_6b", "Qwen3-Embedding-0.6B", "http://localhost:8081", 1024)
      val plan = QdrantEmbeddingBenchmarkPlan(QdrantEmbeddingBenchmarkRunMode.SingleEndpointManualRestart, List(candidate), k = -7)
      val queryResultsByCandidateId = Map(
        candidate.candidateId -> List(queryResult(candidateId = candidate.candidateId, queryId = "q1", topVariantIds = List(variantId(1))))
      )
      val expectations = Map("q1" -> expectedResult(acceptableVariantIds = List(variantId(1))))

      val report = QdrantEmbeddingBenchmark.report(plan, queryResultsByCandidateId, expectations)

      assert(report.plan.k == 0)
      assert(report.candidateReports.head.queryMetrics.head.variantHitAtK == false)
      assert(report.candidateReports.head.aggregate.variantRecallAtK == 0.0)
    }

    "report comparison returns positive quality deltas when the right candidate is better" in {
      val leftAggregate = QdrantEmbeddingBenchmarkAggregate(
        candidateId = "left",
        queryCount = 2,
        variantRecallAtK = 0.0,
        meanReciprocalRankAtK = 0.0,
        providerHitRateAtK = 0.0,
        serviceHitRateAtK = 0.0,
        meanQueryLatencyMs = Some(50.0),
      )
      val rightAggregate = QdrantEmbeddingBenchmarkAggregate(
        candidateId = "right",
        queryCount = 2,
        variantRecallAtK = 1.0,
        meanReciprocalRankAtK = 1.0,
        providerHitRateAtK = 1.0,
        serviceHitRateAtK = 1.0,
        meanQueryLatencyMs = Some(30.0),
      )

      val comparison = QdrantEmbeddingBenchmark.compare(leftAggregate, rightAggregate)

      assert(comparison.variantRecallAtKDelta > 0.0)
      assert(comparison.meanReciprocalRankAtKDelta > 0.0)
      assert(comparison.providerHitRateAtKDelta > 0.0)
      assert(comparison.serviceHitRateAtKDelta > 0.0)
      assert(comparison.meanQueryLatencyMsDelta.contains(-20.0))
    }

    "report comparison latency delta is None when one side has no mean latency" in {
      val leftAggregate = QdrantEmbeddingBenchmarkAggregate(
        candidateId = "left",
        queryCount = 1,
        variantRecallAtK = 0.0,
        meanReciprocalRankAtK = 0.0,
        providerHitRateAtK = 0.0,
        serviceHitRateAtK = 0.0,
        meanQueryLatencyMs = None,
      )
      val rightAggregate = leftAggregate.copy(candidateId = "right", meanQueryLatencyMs = Some(10.0))

      val comparison = QdrantEmbeddingBenchmark.compare(leftAggregate, rightAggregate)

      assert(comparison.meanQueryLatencyMsDelta.isEmpty)
    }

    "batch metricsFor skips missing expected query ids" in {
      val queryResults = List(
        queryResult(candidateId = "qwen3-0_6b", queryId = "q1", topVariantIds = List(variantId(1))),
        queryResult(candidateId = "qwen3-0_6b", queryId = "q2", topVariantIds = List(variantId(2))),
      )
      val expectations = Map(
        "q1" -> expectedResult(acceptableVariantIds = List(variantId(1))),
      )

      val metrics = QdrantEmbeddingBenchmark.metricsFor(queryResults, expectations, k = 1)

      assert(metrics.map(_.queryId) == List("q1"))
      assert(metrics.size == 1)
    }

    "represent single-endpoint manual restart mode" in {
      val candidate = QdrantEmbeddingBenchmarkCandidate(
        candidateId = "qwen3-0_6b",
        modelName = "Qwen3-Embedding-0.6B",
        endpointLabel = "http://localhost:8081",
        vectorDimension = 1024,
        notes = Some("manual restart candidate"),
      )
      val mode = QdrantEmbeddingBenchmarkRunMode.SingleEndpointManualRestart

      assert(candidate.modelName == "Qwen3-Embedding-0.6B")
      assert(candidate.endpointLabel == "http://localhost:8081")
      assert(mode == QdrantEmbeddingBenchmarkRunMode.SingleEndpointManualRestart)
    }

    "represent dual-endpoint parallel comparison mode" in {
      val small = QdrantEmbeddingBenchmarkCandidate("qwen3-0_6b", "Qwen3-Embedding-0.6B", "http://localhost:8081", 1024)
      val large = QdrantEmbeddingBenchmarkCandidate("qwen3-4b", "Qwen3-Embedding-4B", "http://localhost:8082", 2560)
      val mode = QdrantEmbeddingBenchmarkRunMode.DualEndpointParallel

      assert(small.endpointLabel == "http://localhost:8081")
      assert(large.endpointLabel == "http://localhost:8082")
      assert(mode == QdrantEmbeddingBenchmarkRunMode.DualEndpointParallel)
    }

    "calculate metrics without Qdrant or llama calls" in {
      val result = queryResult(topVariantIds = List(variantId(1)), topProviderIds = List(providerId(1)), topServiceIds = List(serviceId(1)))
      val expected = expectedResult(acceptableVariantIds = List(variantId(1)), acceptableProviderIds = List(providerId(1)), acceptableServiceIds = List(serviceId(1)))

      val metrics = QdrantEmbeddingBenchmark.metricsFor(result, expected, k = 1)
      val aggregate = QdrantEmbeddingBenchmark.aggregate(List(metrics), List(result))

      assert(metrics.variantHitAtK)
      assert(aggregate.variantRecallAtK == 1.0)
    }
  }

  private val candidateId = "qwen3-0_6b"

  private def queryResult(
    candidateId: String = candidateId,
    queryId: String = "q1",
    queryText: String = "query",
    topVariantIds: List[MasterServiceOfferVariantId] = Nil,
    topProviderIds: List[MasterLocationId] = Nil,
    topServiceIds: List[ServiceId] = Nil,
    scores: List[Double] = Nil,
    queryLatencyMs: Option[Long] = None,
  ): QdrantEmbeddingBenchmarkQueryResult =
    QdrantEmbeddingBenchmarkQueryResult(
      candidateId = candidateId,
      queryId = queryId,
      queryText = queryText,
      topVariantIds = topVariantIds,
      topProviderIds = topProviderIds,
      topServiceIds = topServiceIds,
      scores = scores,
      queryLatencyMs = queryLatencyMs,
    )

  private def expectedResult(
    acceptableVariantIds: List[MasterServiceOfferVariantId] = Nil,
    acceptableProviderIds: List[MasterLocationId] = Nil,
    acceptableServiceIds: List[ServiceId] = Nil,
  ): QdrantEmbeddingBenchmarkExpected =
    QdrantEmbeddingBenchmarkExpected(
      acceptableVariantIds = acceptableVariantIds,
      acceptableProviderIds = acceptableProviderIds,
      acceptableServiceIds = acceptableServiceIds,
    )

  private def variantId(value: Int): MasterServiceOfferVariantId = id(value)

  private def providerId(value: Int): MasterLocationId = id(value + 100)

  private def serviceId(value: Int): ServiceId = id(value + 200)

  private def id(value: Int): UUID =
    UUID.fromString(f"00000000-0000-0000-0000-$value%012d")
}
