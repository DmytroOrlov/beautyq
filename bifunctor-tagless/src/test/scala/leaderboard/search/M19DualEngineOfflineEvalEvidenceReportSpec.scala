package leaderboard.search

import leaderboard.search.eval.*
import org.scalatest.wordspec.AnyWordSpec

final class M19DualEngineOfflineEvalEvidenceReportSpec extends AnyWordSpec {

  "M19DualEngineOfflineEvalEvidenceReport.renderMarkdown" should {

    "render per-query ES, Qdrant, and simulated-hybrid candidate ids" in {
      val markdown = render(sampleResult())

      assert(markdown.contains("query_id: q1"))
      assert(markdown.contains("es_candidate_ids: v1, v2"))
      assert(markdown.contains("qdrant_candidate_ids: v2, v3"))
      assert(markdown.contains("simulated_hybrid_candidate_ids"))
      assert(markdown.contains("v1, v2, v3"))
    }

    "render overlap, complement, and noise counts per query" in {
      val markdown = render(sampleResult())

      assert(markdown.contains("es_qdrant_overlap_count: 1"))
      assert(markdown.contains("qdrant_complement_count:"))
      assert(markdown.contains("qdrant_noise_count:"))
    }

    "render aggregate totals across all queries" in {
      val markdown = render(sampleResult())

      assert(markdown.contains("## Aggregate Totals (all queries)"))
      assert(markdown.contains("query_count: 1"))
    }

    "make expectation-unavailable state visible" in {
      val markdown = render(
        dualResult(
          es = List(row("q1", M18OfflineEvalBackend.Es, "v1", 1)),
          qdrant = List(row("q1", M18OfflineEvalBackend.Qdrant, "v2", 1)),
        )
      )

      assert(markdown.contains("expectations_available: false"))
      assert(markdown.contains("expectations_unavailable_query_count: 1"))
    }

    "render missing-lookup and lookup-not-evaluated as separate counts" in {
      val markdown = render(
        dualResult(
          es = List(
            row("q1", M18OfflineEvalBackend.Es, "v1", 1, missingLookup = M18MissingLookup.Hydrated),
            row("q1", M18OfflineEvalBackend.Es, "v2", 2, missingLookup = M18MissingLookup.MissingLookup),
          ),
          qdrant = List(
            row("q1", M18OfflineEvalBackend.Qdrant, "v3", 1, missingLookup = M18MissingLookup.LookupNotEvaluated)
          ),
        )
      )

      assert(markdown.contains("es: hydrated=1, missing_lookup=1, lookup_not_evaluated=0"))
      assert(markdown.contains("qdrant: hydrated=0, missing_lookup=0, lookup_not_evaluated=1"))
    }

    "render latency present, absent, and not-executed as separate states" in {
      val present = render(
        M18DualEngineOfflineEvalResult(
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
      )

      assert(present.contains("es: present"))
      assert(present.contains("qdrant: not_executed"))

      val absent = render(
        M18DualEngineOfflineEvalResult(
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
      )

      assert(absent.contains("es: absent"))
      assert(absent.contains("qdrant: absent"))
    }

    "state explicitly that it is offline/eval evidence and not production activation" in {
      val markdown = render(sampleResult())

      assert(markdown.contains("offline/eval evidence only"))
      assert(markdown.contains("NOT production activation"))
    }

    "present combination evidence inputs as questions/inputs, not serving approval" in {
      val markdown = render(sampleResult())

      assert(markdown.contains("## Combination Evidence Inputs (offline, open questions only)"))
      assert(markdown.contains("questions, not decisions"))
      assert(markdown.contains("candidate recall policy"))
      assert(markdown.contains("carousel/result composition policy"))
      assert(markdown.contains("facet/aggregation ownership"))
      assert(markdown.contains("applied-filter ownership"))
      assert(markdown.contains("backend-score display/debug ownership"))
      assert(markdown.contains("missing-lookup handling"))
      assert(markdown.contains("latency/readiness gating"))
      // each appears as an unchecked checkbox input, and the section explicitly grants no approval
      assert(markdown.contains("- [ ] candidate recall policy"))
      assert(markdown.contains("nothing below grants serving approval or activates any behavior in production"))
    }
  }

  private def render(result: M18DualEngineOfflineEvalResult): String = {
    val perQuery = M19DualEngineOfflineEvalMetrics.queryMetrics(result)
    val aggregate = M19DualEngineOfflineEvalMetrics.aggregate(perQuery)
    M19DualEngineOfflineEvalEvidenceReport.renderMarkdown(perQuery, aggregate)
  }

  private def sampleResult(): M18DualEngineOfflineEvalResult =
    dualResult(
      es = List(
        row("q1", M18OfflineEvalBackend.Es, "v1", 1, M18ExpectedMatch.Matched),
        row("q1", M18OfflineEvalBackend.Es, "v2", 2, M18ExpectedMatch.Matched),
      ),
      qdrant = List(
        row("q1", M18OfflineEvalBackend.Qdrant, "v2", 1, M18ExpectedMatch.Matched),
        row("q1", M18OfflineEvalBackend.Qdrant, "v3", 2, M18ExpectedMatch.NotExpected),
      ),
    )

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
