package leaderboard.search.qdrant

import leaderboard.model.{MasterLocationId, MasterServiceOfferVariantId, ServiceId}

final case class QdrantEmbeddingBenchmarkCandidate(
  candidateId: String,
  modelName: String,
  endpointLabel: String,
  vectorDimension: Int,
  notes: Option[String] = None,
)

sealed trait QdrantEmbeddingBenchmarkRunMode {
  def expectedCandidateCount: Int
}

object QdrantEmbeddingBenchmarkRunMode {
  case object SingleEndpointManualRestart extends QdrantEmbeddingBenchmarkRunMode {
    override val expectedCandidateCount: Int = 1
  }

  case object DualEndpointParallel extends QdrantEmbeddingBenchmarkRunMode {
    override val expectedCandidateCount: Int = 2
  }
}

final case class QdrantEmbeddingBenchmarkPlan(
  runMode: QdrantEmbeddingBenchmarkRunMode,
  candidates: List[QdrantEmbeddingBenchmarkCandidate],
  k: Int,
)

final case class QdrantEmbeddingBenchmarkExpected(
  acceptableVariantIds: List[MasterServiceOfferVariantId],
  acceptableProviderIds: List[MasterLocationId],
  acceptableServiceIds: List[ServiceId],
)

final case class QdrantEmbeddingBenchmarkQueryResult(
  candidateId: String,
  queryId: String,
  queryText: String,
  topVariantIds: List[MasterServiceOfferVariantId],
  topProviderIds: List[MasterLocationId],
  topServiceIds: List[ServiceId],
  scores: List[Double],
  queryLatencyMs: Option[Long] = None,
)

final case class QdrantEmbeddingBenchmarkQueryMetrics(
  queryId: String,
  candidateId: String,
  variantHitAtK: Boolean,
  variantHitRank: Option[Int],
  reciprocalRank: Double,
  providerHitAtK: Boolean,
  serviceHitAtK: Boolean,
)

final case class QdrantEmbeddingBenchmarkAggregate(
  candidateId: String,
  queryCount: Int,
  variantRecallAtK: Double,
  meanReciprocalRankAtK: Double,
  providerHitRateAtK: Double,
  serviceHitRateAtK: Double,
  meanQueryLatencyMs: Option[Double],
)

final case class QdrantEmbeddingBenchmarkCandidateReport(
  candidate: QdrantEmbeddingBenchmarkCandidate,
  queryMetrics: List[QdrantEmbeddingBenchmarkQueryMetrics],
  aggregate: QdrantEmbeddingBenchmarkAggregate,
)

final case class QdrantEmbeddingBenchmarkComparison(
  left: QdrantEmbeddingBenchmarkAggregate,
  right: QdrantEmbeddingBenchmarkAggregate,
  variantRecallAtKDelta: Double,
  meanReciprocalRankAtKDelta: Double,
  providerHitRateAtKDelta: Double,
  serviceHitRateAtKDelta: Double,
  meanQueryLatencyMsDelta: Option[Double],
)

final case class QdrantEmbeddingBenchmarkReport(
  plan: QdrantEmbeddingBenchmarkPlan,
  candidateReports: List[QdrantEmbeddingBenchmarkCandidateReport],
  comparisons: List[QdrantEmbeddingBenchmarkComparison],
)

object QdrantEmbeddingBenchmark {
  def metricsFor(
    result: QdrantEmbeddingBenchmarkQueryResult,
    expected: QdrantEmbeddingBenchmarkExpected,
    k: Int,
  ): QdrantEmbeddingBenchmarkQueryMetrics = {
    val variantHitRank = firstHitRank(result.topVariantIds, expected.acceptableVariantIds.toSet, k)

    QdrantEmbeddingBenchmarkQueryMetrics(
      queryId = result.queryId,
      candidateId = result.candidateId,
      variantHitAtK = variantHitRank.nonEmpty,
      variantHitRank = variantHitRank,
      reciprocalRank = variantHitRank.fold(0.0)(rank => 1.0 / rank.toDouble),
      providerHitAtK = hasHitAtK(result.topProviderIds, expected.acceptableProviderIds.toSet, k),
      serviceHitAtK = hasHitAtK(result.topServiceIds, expected.acceptableServiceIds.toSet, k),
    )
  }

  def metricsFor(
    results: List[QdrantEmbeddingBenchmarkQueryResult],
    expectationsByQueryId: Map[String, QdrantEmbeddingBenchmarkExpected],
    k: Int,
  ): List[QdrantEmbeddingBenchmarkQueryMetrics] =
    results.flatMap(result => expectationsByQueryId.get(result.queryId).map(expected => metricsFor(result, expected, k)))

  def aggregate(
    metrics: List[QdrantEmbeddingBenchmarkQueryMetrics],
    queryResults: List[QdrantEmbeddingBenchmarkQueryResult],
  ): QdrantEmbeddingBenchmarkAggregate = {
    val candidateId = metrics.headOption.map(_.candidateId).orElse(queryResults.headOption.map(_.candidateId)).getOrElse("")
    val queryCount = metrics.size
    val latencies = queryResults.flatMap(_.queryLatencyMs).map(_.toDouble)

    QdrantEmbeddingBenchmarkAggregate(
      candidateId = candidateId,
      queryCount = queryCount,
      variantRecallAtK = fraction(queryCount, metrics.count(_.variantHitAtK)),
      meanReciprocalRankAtK = mean(queryCount, metrics.map(_.reciprocalRank).sum),
      providerHitRateAtK = fraction(queryCount, metrics.count(_.providerHitAtK)),
      serviceHitRateAtK = fraction(queryCount, metrics.count(_.serviceHitAtK)),
      meanQueryLatencyMs = nonEmptyMean(latencies),
    )
  }

  def candidateReport(
    candidate: QdrantEmbeddingBenchmarkCandidate,
    queryResults: List[QdrantEmbeddingBenchmarkQueryResult],
    expectationsByQueryId: Map[String, QdrantEmbeddingBenchmarkExpected],
    k: Int,
  ): QdrantEmbeddingBenchmarkCandidateReport = {
    val metrics = metricsFor(queryResults, expectationsByQueryId, k)
    val aggregateResult = aggregate(metrics, queryResults).copy(candidateId = candidate.candidateId)

    QdrantEmbeddingBenchmarkCandidateReport(
      candidate = candidate,
      queryMetrics = metrics,
      aggregate = aggregateResult,
    )
  }

  def compare(
    leftAggregate: QdrantEmbeddingBenchmarkAggregate,
    rightAggregate: QdrantEmbeddingBenchmarkAggregate,
  ): QdrantEmbeddingBenchmarkComparison =
    QdrantEmbeddingBenchmarkComparison(
      left = leftAggregate,
      right = rightAggregate,
      variantRecallAtKDelta = rightAggregate.variantRecallAtK - leftAggregate.variantRecallAtK,
      meanReciprocalRankAtKDelta = rightAggregate.meanReciprocalRankAtK - leftAggregate.meanReciprocalRankAtK,
      providerHitRateAtKDelta = rightAggregate.providerHitRateAtK - leftAggregate.providerHitRateAtK,
      serviceHitRateAtKDelta = rightAggregate.serviceHitRateAtK - leftAggregate.serviceHitRateAtK,
      meanQueryLatencyMsDelta = for {
        leftLatency  <- leftAggregate.meanQueryLatencyMs
        rightLatency <- rightAggregate.meanQueryLatencyMs
      } yield rightLatency - leftLatency,
    )

  def report(
    plan: QdrantEmbeddingBenchmarkPlan,
    queryResultsByCandidateId: Map[String, List[QdrantEmbeddingBenchmarkQueryResult]],
    expectationsByQueryId: Map[String, QdrantEmbeddingBenchmarkExpected],
  ): QdrantEmbeddingBenchmarkReport = {
    val normalizedK = nonNegativeK(plan.k)
    val candidateReports = plan.candidates.map { candidate =>
      candidateReport(
        candidate = candidate,
        queryResults = queryResultsByCandidateId.getOrElse(candidate.candidateId, Nil),
        expectationsByQueryId = expectationsByQueryId,
        k = normalizedK,
      )
    }

    val comparisons = candidateReports match {
      case left :: right :: Nil =>
        List(compare(left.aggregate, right.aggregate))
      case _ =>
        Nil
    }

    QdrantEmbeddingBenchmarkReport(
      plan = plan.copy(k = normalizedK),
      candidateReports = candidateReports,
      comparisons = comparisons,
    )
  }

  private def firstHitRank[A](rankedIds: List[A], acceptableIds: Set[A], k: Int): Option[Int] =
    rankedIds.take(nonNegativeK(k)).zipWithIndex.collectFirst {
      case (id, index) if acceptableIds(id) => index + 1
    }

  private def hasHitAtK[A](rankedIds: List[A], acceptableIds: Set[A], k: Int): Boolean =
    rankedIds.take(nonNegativeK(k)).exists(acceptableIds)

  private def nonNegativeK(k: Int): Int =
    math.max(k, 0)

  private def fraction(queryCount: Int, hits: Int): Double =
    mean(queryCount, hits.toDouble)

  private def mean(count: Int, total: Double): Double =
    if (count == 0) 0.0 else total / count.toDouble

  private def nonEmptyMean(values: List[Double]): Option[Double] =
    values match {
      case Nil => None
      case _   => Some(values.sum / values.size.toDouble)
    }
}
