package leaderboard.search.eval

/**
 * M19B: pure, readable evidence report over the M19A metrics
 * ([[M19QueryMetrics]] / [[M19AggregateMetrics]]).
 *
 * This is an offline/eval-only rendering surface. It performs no retrieval, no fusion, no reranking,
 * no fallback, and never calls the production `/beauty-search` route or activates Qdrant in
 * production. It only formats the candidate-set, overlap, missing-lookup, and latency-availability
 * evidence that M19A already derived from the M18 dual-engine offline-eval result.
 *
 * The "combination evidence inputs" section deliberately contains questions, not decisions: it
 * summarizes what a future hybrid policy would have to decide, and does not approve, choose, or
 * encode any production serving behavior.
 *
 * A small M19-specific artifact type is used rather than [[M9OfflineEvalReportArtifact]] only as a
 * thin filename/content-type wrapper; the M9 saved-report model itself cannot carry M19's per-backend
 * missing-lookup or latency-availability evidence without dropping it.
 */
final case class M19EvidenceReportArtifact(
  filename: String,
  contentType: String,
  contents: String,
)

object M19DualEngineOfflineEvalEvidenceReport {

  def markdownArtifact(
    filename: String,
    perQuery: List[M19QueryMetrics],
    aggregate: M19AggregateMetrics,
  ): M19EvidenceReportArtifact =
    M19EvidenceReportArtifact(
      filename = filename,
      contentType = "text/markdown; charset=utf-8",
      contents = renderMarkdown(perQuery, aggregate),
    )

  def renderMarkdown(
    perQuery: List[M19QueryMetrics],
    aggregate: M19AggregateMetrics,
  ): String = {
    val builder = new StringBuilder

    line(builder, "# M19 Dual-Engine Offline Eval Evidence Report")
    line(builder, "")
    line(
      builder,
      "This report is offline/eval evidence only. It is NOT production activation: it performs no " +
        "retrieval, no fusion, no reranking, no fallback, does not call `/beauty-search`, and does not " +
        "activate Qdrant in production. It only renders the M19A metrics derived from the M18 " +
        "dual-engine offline-eval result.",
    )

    renderPerQuery(builder, perQuery)
    renderAggregate(builder, aggregate)
    renderCombinationEvidenceInputs(builder)

    builder.result()
  }

  private def renderPerQuery(builder: StringBuilder, perQuery: List[M19QueryMetrics]): Unit = {
    line(builder, "")
    line(builder, "## Per-Query Evidence")
    perQuery match {
      case Nil =>
        line(builder, "")
        line(builder, "- (no queries)")
      case _ =>
        perQuery.foreach(renderQuery(builder, _))
    }
  }

  private def renderQuery(builder: StringBuilder, query: M19QueryMetrics): Unit = {
    line(builder, "")
    line(builder, s"### query_id: ${renderText(query.queryId)}")
    line(builder, "")
    line(builder, s"- es_candidate_ids: ${renderIds(query.candidateIds.esCandidateIds)}")
    line(builder, s"- qdrant_candidate_ids: ${renderIds(query.candidateIds.qdrantCandidateIds)}")
    line(
      builder,
      s"- simulated_hybrid_candidate_ids (offline supplement, no fusion/reranking): " +
        s"${renderIds(query.candidateIds.simulatedHybridCandidateIds)}",
    )
    line(builder, s"- es_qdrant_overlap_count: ${query.overlapCount}")
    line(builder, s"- qdrant_complement_count: ${query.qdrantComplementCount}")
    line(builder, s"- qdrant_noise_count: ${query.qdrantNoiseCount}")
    line(builder, s"- expectations_available: ${query.expectationsAvailable}")
    line(builder, "- lookup_by_backend:")
    M18OfflineEvalBackend.stableOrder.foreach { backend =>
      val counts = query.lookupByBackend.find(_.backend == backend).getOrElse(M19BackendLookupCounts.empty(backend))
      line(builder, s"  - ${counts.backend.render}: ${renderLookup(counts)}")
    }
    line(builder, "- latency_by_backend:")
    M18OfflineEvalBackend.stableOrder.foreach { backend =>
      val availability =
        query.latencyByBackend.find(_.backend == backend).map(_.availability).getOrElse(M19LatencyAvailability.NotExecuted)
      line(builder, s"  - ${backend.render}: ${renderLatency(availability)}")
    }
  }

  private def renderAggregate(builder: StringBuilder, aggregate: M19AggregateMetrics): Unit = {
    line(builder, "")
    line(builder, "## Aggregate Totals (all queries)")
    line(builder, "")
    line(builder, s"- query_count: ${aggregate.queryCount}")
    line(builder, s"- es_qdrant_overlap_count: ${aggregate.overlapCount}")
    line(builder, s"- qdrant_complement_count: ${aggregate.qdrantComplementCount}")
    line(builder, s"- qdrant_noise_count: ${aggregate.qdrantNoiseCount}")
    line(builder, s"- expectations_available_query_count: ${aggregate.expectationsAvailableQueryCount}")
    line(builder, s"- expectations_unavailable_query_count: ${aggregate.expectationsUnavailableQueryCount}")
    line(builder, "- lookup_by_backend:")
    M18OfflineEvalBackend.stableOrder.foreach { backend =>
      val counts = aggregate.lookupByBackend.find(_.backend == backend).getOrElse(M19BackendLookupCounts.empty(backend))
      line(builder, s"  - ${counts.backend.render}: ${renderLookup(counts)}")
    }
    line(builder, "- latency_by_backend:")
    M18OfflineEvalBackend.stableOrder.foreach { backend =>
      val agg = aggregate.latencyByBackend
        .find(_.backend == backend)
        .getOrElse(M19BackendLatencyAggregate(backend, presentCount = 0, absentCount = 0, notExecutedCount = 0))
      line(
        builder,
        s"  - ${agg.backend.render}: present=${agg.presentCount}, absent=${agg.absentCount}, " +
          s"not_executed=${agg.notExecutedCount}",
      )
    }
  }

  private def renderCombinationEvidenceInputs(builder: StringBuilder): Unit = {
    line(builder, "")
    line(builder, "## Combination Evidence Inputs (offline, open questions only)")
    line(builder, "")
    line(
      builder,
      "The following are open inputs a future hybrid policy would have to decide. They are questions, " +
        "not decisions: nothing below grants serving approval or activates any behavior in production.",
    )
    line(builder, "")
    combinationQuestions.foreach(question => line(builder, s"- [ ] $question"))
  }

  private val combinationQuestions: List[String] = List(
    "candidate recall policy: how would ES and Qdrant candidate sets be combined for recall, if at all?",
    "carousel/result composition policy: how would results/carousels be composed across backends?",
    "facet/aggregation ownership: which backend (if any) would own facets/aggregations?",
    "applied-filter ownership: which backend (if any) would own applied filters?",
    "backend-score display/debug ownership: which backend score (if any) would be displayed or exposed for debug?",
    "missing-lookup handling: how would missing-lookup candidates be handled before any serving?",
    "latency/readiness gating: what latency/readiness gating would be required before any activation?",
  )

  private def renderLookup(counts: M19BackendLookupCounts): String =
    s"hydrated=${counts.hydratedCount}, missing_lookup=${counts.missingLookupCount}, " +
      s"lookup_not_evaluated=${counts.lookupNotEvaluatedCount}"

  private def renderLatency(availability: M19LatencyAvailability): String =
    availability match {
      case M19LatencyAvailability.Present     => "present"
      case M19LatencyAvailability.Absent      => "absent"
      case M19LatencyAvailability.NotExecuted => "not_executed"
    }

  private def renderIds(ids: List[String]): String =
    ids match {
      case Nil => "-"
      case _   => ids.map(renderText).mkString(", ")
    }

  private def renderText(value: String): String =
    value
      .replace("\r\n", " ")
      .replace('\n', ' ')
      .replace('\r', ' ')

  private def line(builder: StringBuilder, value: String): Unit = {
    builder.append(value)
    builder.append('\n')
  }
}
