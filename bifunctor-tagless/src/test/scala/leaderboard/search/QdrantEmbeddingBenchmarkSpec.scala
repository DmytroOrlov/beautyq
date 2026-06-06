package leaderboard.search

import leaderboard.model.{MasterLocationId, MasterServiceOfferVariantId, ServiceId}
import leaderboard.search.qdrant.{
  QdrantEmbeddingBenchmark,
  QdrantEmbeddingBenchmarkCandidate,
  QdrantEmbeddingBenchmarkExpected,
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
