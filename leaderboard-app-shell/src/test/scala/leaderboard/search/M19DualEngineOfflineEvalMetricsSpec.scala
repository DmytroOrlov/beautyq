package leaderboard.search

import leaderboard.search.eval.*
import org.scalatest.wordspec.AnyWordSpec

final class M19DualEngineOfflineEvalMetricsSpec extends AnyWordSpec {

  "M19DualEngineOfflineEvalMetrics.queryMetrics" should {

    "count overlap as only candidate ids present in both ES and Qdrant" in {
      val result = dualResult(
        es = List(row("q1", M18OfflineEvalBackend.Es, "v1", 1), row("q1", M18OfflineEvalBackend.Es, "v2", 2)),
        qdrant = List(row("q1", M18OfflineEvalBackend.Qdrant, "v2", 1), row("q1", M18OfflineEvalBackend.Qdrant, "v3", 2)),
      )

      val metrics = M19DualEngineOfflineEvalMetrics.queryMetrics(result)

      assert(metrics.map(_.overlapCount) == List(1))
    }

    "count Qdrant complement as expected ids found by Qdrant but missed by ES, only when expectations are available" in {
      val result = dualResult(
        es = List(row("q1", M18OfflineEvalBackend.Es, "v1", 1, M18ExpectedMatch.Matched)),
        qdrant = List(
          row("q1", M18OfflineEvalBackend.Qdrant, "v1", 1, M18ExpectedMatch.Matched),
          row("q1", M18OfflineEvalBackend.Qdrant, "v2", 2, M18ExpectedMatch.Matched),
        ),
      )

      val metrics = onlyQueryMetrics(M19DualEngineOfflineEvalMetrics.queryMetrics(result))

      assert(metrics.expectationsAvailable)
      assert(metrics.qdrantComplementCount == 1)
    }

    "leave Qdrant complement at zero and expectations unavailable when no row carries expectation evidence" in {
      val result = dualResult(
        es = List(row("q1", M18OfflineEvalBackend.Es, "v1", 1)),
        qdrant = List(row("q1", M18OfflineEvalBackend.Qdrant, "v2", 1)),
      )

      val metrics = onlyQueryMetrics(M19DualEngineOfflineEvalMetrics.queryMetrics(result))

      assert(!metrics.expectationsAvailable)
      assert(metrics.qdrantComplementCount == 0)
      assert(metrics.qdrantNoiseCount == 0)
    }

    "count Qdrant noise as non-expected Qdrant candidates only when expectations are available" in {
      val result = dualResult(
        es = List(row("q1", M18OfflineEvalBackend.Es, "v1", 1, M18ExpectedMatch.Matched)),
        qdrant = List(
          row("q1", M18OfflineEvalBackend.Qdrant, "v1", 1, M18ExpectedMatch.Matched),
          row("q1", M18OfflineEvalBackend.Qdrant, "v9", 2, M18ExpectedMatch.NotExpected),
        ),
      )

      val metrics = onlyQueryMetrics(M19DualEngineOfflineEvalMetrics.queryMetrics(result))

      assert(metrics.qdrantNoiseCount == 1)
    }

    "count missing lookup by backend and preserve lookup-not-evaluated separately" in {
      val result = dualResult(
        es = List(
          row("q1", M18OfflineEvalBackend.Es, "v1", 1, missingLookup = M18MissingLookup.Hydrated),
          row("q1", M18OfflineEvalBackend.Es, "v2", 2, missingLookup = M18MissingLookup.MissingLookup),
        ),
        qdrant = List(
          row("q1", M18OfflineEvalBackend.Qdrant, "v3", 1, missingLookup = M18MissingLookup.LookupNotEvaluated)
        ),
      )

      val metrics = onlyQueryMetrics(M19DualEngineOfflineEvalMetrics.queryMetrics(result))
      val esLookup = lookupCountsFor(metrics.lookupByBackend, M18OfflineEvalBackend.Es)
      val qdrantLookup = lookupCountsFor(metrics.lookupByBackend, M18OfflineEvalBackend.Qdrant)

      assert(esLookup.hydratedCount == 1)
      assert(esLookup.missingLookupCount == 1)
      assert(esLookup.lookupNotEvaluatedCount == 0)
      assert(qdrantLookup.lookupNotEvaluatedCount == 1)
      assert(qdrantLookup.hydratedCount == 0)
      assert(qdrantLookup.missingLookupCount == 0)
    }

    "represent latency presence/absence by backend, distinct from a leg that never executed" in {
      val result = M18DualEngineOfflineEvalResult(
        evalDatasetId = EvalDatasetId("m19-test"),
        catalogSnapshotId = CatalogSnapshotId("m19-test"),
        es = M18BackendLegOutcome.Executed(
          M18OfflineEvalBackend.Es,
          List(legResult("q1", M18OfflineEvalBackend.Es, Nil, latencyNanos = Some(42L))),
        ),
        qdrant = M18BackendLegOutcome.Skipped(
          M18OfflineEvalBackend.Qdrant,
          M18LegSkipKind.ResourceGated,
          "resource-gated",
          List("missing prerequisite"),
        ),
      )

      val metrics = onlyQueryMetrics(M19DualEngineOfflineEvalMetrics.queryMetrics(result))
      val esLatency = latencyFor(metrics.latencyByBackend, M18OfflineEvalBackend.Es)
      val qdrantLatency = latencyFor(metrics.latencyByBackend, M18OfflineEvalBackend.Qdrant)

      assert(esLatency.availability == M19LatencyAvailability.Present)
      assert(qdrantLatency.availability == M19LatencyAvailability.NotExecuted)
    }

    "mark latency absent when a backend executed but no clock measured it" in {
      val result = M18DualEngineOfflineEvalResult(
        evalDatasetId = EvalDatasetId("m19-test"),
        catalogSnapshotId = CatalogSnapshotId("m19-test"),
        es = M18BackendLegOutcome.Executed(
          M18OfflineEvalBackend.Es,
          List(legResult("q1", M18OfflineEvalBackend.Es, Nil, latencyNanos = None)),
        ),
        qdrant = M18BackendLegOutcome.Executed(
          M18OfflineEvalBackend.Qdrant,
          List(legResult("q1", M18OfflineEvalBackend.Qdrant, Nil, latencyNanos = None)),
        ),
      )

      val metrics = onlyQueryMetrics(M19DualEngineOfflineEvalMetrics.queryMetrics(result))

      assert(metrics.latencyByBackend.forall(_.availability == M19LatencyAvailability.Absent))
    }

    "build simulated-hybrid ids as ES-first plus Qdrant-only supplement, with no score fusion or reranking" in {
      val result = dualResult(
        es = List(row("q1", M18OfflineEvalBackend.Es, "v1", 1), row("q1", M18OfflineEvalBackend.Es, "v2", 2)),
        qdrant = List(
          row("q1", M18OfflineEvalBackend.Qdrant, "v2", 1),
          row("q1", M18OfflineEvalBackend.Qdrant, "v3", 2),
          row("q1", M18OfflineEvalBackend.Qdrant, "v4", 3),
        ),
      )

      val metrics = onlyQueryMetrics(M19DualEngineOfflineEvalMetrics.queryMetrics(result))

      assert(metrics.candidateIds.esCandidateIds == List("v1", "v2"))
      assert(metrics.candidateIds.qdrantCandidateIds == List("v2", "v3", "v4"))
      assert(metrics.candidateIds.simulatedHybridCandidateIds == List("v1", "v2", "v3", "v4"))
    }
  }

  "M19DualEngineOfflineEvalMetrics.aggregate" should {

    "equal the sum of per-query metrics across overlap, complement, noise, lookup, and latency" in {
      val result = M18DualEngineOfflineEvalResult(
        evalDatasetId = EvalDatasetId("m19-test"),
        catalogSnapshotId = CatalogSnapshotId("m19-test"),
        es = M18BackendLegOutcome.Executed(
          M18OfflineEvalBackend.Es,
          List(
            legResult(
              "q1",
              M18OfflineEvalBackend.Es,
              List(row("q1", M18OfflineEvalBackend.Es, "v1", 1, M18ExpectedMatch.Matched, M18MissingLookup.Hydrated)),
              latencyNanos = Some(10L),
            ),
            legResult(
              "q2",
              M18OfflineEvalBackend.Es,
              List(row("q2", M18OfflineEvalBackend.Es, "v5", 1, M18ExpectedMatch.ExpectationsUnavailable, M18MissingLookup.MissingLookup)),
              latencyNanos = None,
            ),
          ),
        ),
        qdrant = M18BackendLegOutcome.Executed(
          M18OfflineEvalBackend.Qdrant,
          List(
            legResult(
              "q1",
              M18OfflineEvalBackend.Qdrant,
              List(
                row("q1", M18OfflineEvalBackend.Qdrant, "v1", 1, M18ExpectedMatch.Matched, M18MissingLookup.Hydrated),
                row("q1", M18OfflineEvalBackend.Qdrant, "v9", 2, M18ExpectedMatch.NotExpected, M18MissingLookup.LookupNotEvaluated),
              ),
              latencyNanos = Some(20L),
            ),
            legResult(
              "q2",
              M18OfflineEvalBackend.Qdrant,
              List(row("q2", M18OfflineEvalBackend.Qdrant, "v6", 1, M18ExpectedMatch.ExpectationsUnavailable, M18MissingLookup.LookupNotEvaluated)),
              latencyNanos = Some(30L),
            ),
          ),
        ),
      )

      val perQuery = M19DualEngineOfflineEvalMetrics.queryMetrics(result)
      val aggregate = M19DualEngineOfflineEvalMetrics.aggregate(perQuery)

      assert(aggregate.queryCount == perQuery.size)
      assert(aggregate.overlapCount == perQuery.map(_.overlapCount).sum)
      assert(aggregate.qdrantComplementCount == perQuery.map(_.qdrantComplementCount).sum)
      assert(aggregate.qdrantNoiseCount == perQuery.map(_.qdrantNoiseCount).sum)
      assert(aggregate.expectationsAvailableQueryCount == perQuery.count(_.expectationsAvailable))
      assert(aggregate.expectationsUnavailableQueryCount == perQuery.count(!_.expectationsAvailable))

      val esLookupSum = perQuery.flatMap(_.lookupByBackend).filter(_.backend == M18OfflineEvalBackend.Es)
      val aggregateEsLookup = lookupCountsFor(aggregate.lookupByBackend, M18OfflineEvalBackend.Es)
      assert(aggregateEsLookup.hydratedCount == esLookupSum.map(_.hydratedCount).sum)
      assert(aggregateEsLookup.missingLookupCount == esLookupSum.map(_.missingLookupCount).sum)
      assert(aggregateEsLookup.lookupNotEvaluatedCount == esLookupSum.map(_.lookupNotEvaluatedCount).sum)

      val qdrantLatencySum = perQuery.flatMap(_.latencyByBackend).filter(_.backend == M18OfflineEvalBackend.Qdrant)
      val aggregateQdrantLatency = aggregateLatencyFor(aggregate.latencyByBackend, M18OfflineEvalBackend.Qdrant)
      assert(aggregateQdrantLatency.presentCount == qdrantLatencySum.count(_.availability == M19LatencyAvailability.Present))
      assert(aggregateQdrantLatency.absentCount == qdrantLatencySum.count(_.availability == M19LatencyAvailability.Absent))
      assert(aggregateQdrantLatency.notExecutedCount == qdrantLatencySum.count(_.availability == M19LatencyAvailability.NotExecuted))
    }

    "return zero counts for empty input" in {
      val aggregate = M19DualEngineOfflineEvalMetrics.aggregate(Nil)

      assert(aggregate.queryCount == 0)
      assert(aggregate.overlapCount == 0)
      assert(aggregate.qdrantComplementCount == 0)
      assert(aggregate.qdrantNoiseCount == 0)
      assert(aggregate.expectationsAvailableQueryCount == 0)
      assert(aggregate.expectationsUnavailableQueryCount == 0)
    }
  }

  private def onlyQueryMetrics(perQuery: List[M19QueryMetrics]): M19QueryMetrics =
    perQuery match {
      case only :: Nil => only
      case other       => fail(s"expected exactly one query metrics row, got ${other.size}: $other")
    }

  private def lookupCountsFor(counts: List[M19BackendLookupCounts], backend: M18OfflineEvalBackend): M19BackendLookupCounts =
    counts.find(_.backend == backend) match {
      case Some(found) => found
      case None        => fail(s"expected lookup counts for backend $backend, got $counts")
    }

  private def latencyFor(latencies: List[M19BackendLatency], backend: M18OfflineEvalBackend): M19BackendLatency =
    latencies.find(_.backend == backend) match {
      case Some(found) => found
      case None        => fail(s"expected latency for backend $backend, got $latencies")
    }

  private def aggregateLatencyFor(latencies: List[M19BackendLatencyAggregate], backend: M18OfflineEvalBackend): M19BackendLatencyAggregate =
    latencies.find(_.backend == backend) match {
      case Some(found) => found
      case None        => fail(s"expected aggregate latency for backend $backend, got $latencies")
    }

  private def row(
    queryId: String,
    backend: M18OfflineEvalBackend,
    candidateId: String,
    rank: Int,
    expectedMatch: M18ExpectedMatch = M18ExpectedMatch.ExpectationsUnavailable,
    missingLookup: M18MissingLookup = M18MissingLookup.LookupNotEvaluated,
  ): M18BackendCandidateRow =
    M18BackendCandidateRow(
      queryId = queryId,
      backend = backend,
      candidateId = candidateId,
      rank = rank,
      backendScore = None,
      matchedFields = Nil,
      expectedMatch = expectedMatch,
      missingLookup = missingLookup,
    )

  private def legResult(
    queryId: String,
    backend: M18OfflineEvalBackend,
    candidates: List[M18BackendCandidateRow],
    latencyNanos: Option[Long] = None,
  ): M18BackendQueryLegResult =
    M18BackendQueryLegResult(
      queryId = queryId,
      backend = backend,
      candidates = candidates,
      latencyNanos = latencyNanos,
      failure = None,
    )

  private def dualResult(
    es: List[M18BackendCandidateRow],
    qdrant: List[M18BackendCandidateRow],
  ): M18DualEngineOfflineEvalResult = {
    val esByQuery = es.groupBy(_.queryId)
    val qdrantByQuery = qdrant.groupBy(_.queryId)
    val queryIds = (esByQuery.keys ++ qdrantByQuery.keys).toList.distinct

    M18DualEngineOfflineEvalResult(
      evalDatasetId = EvalDatasetId("m19-test"),
      catalogSnapshotId = CatalogSnapshotId("m19-test"),
      es = M18BackendLegOutcome.Executed(
        M18OfflineEvalBackend.Es,
        queryIds.map(queryId => legResult(queryId, M18OfflineEvalBackend.Es, esByQuery.getOrElse(queryId, Nil))),
      ),
      qdrant = M18BackendLegOutcome.Executed(
        M18OfflineEvalBackend.Qdrant,
        queryIds.map(queryId => legResult(queryId, M18OfflineEvalBackend.Qdrant, qdrantByQuery.getOrElse(queryId, Nil))),
      ),
    )
  }
}
