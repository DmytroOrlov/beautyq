package leaderboard.search.eval

/**
 * M19A: pure metrics over [[M18DualEngineOfflineEvalResult]].
 *
 * Compares ES-only, Qdrant-only, and offline simulated-hybrid candidate id sets that the M18
 * dual-engine offline-eval surface already produced. This surface performs no retrieval, no fusion,
 * no reranking, and never calls the production `/beauty-search` route or any backend: it only reads
 * the M18 result data structures.
 *
 * [[EngineEvalComparisonMetrics]] is not reused here: it has no place to carry M18's missing-lookup
 * or latency-availability evidence, and forcing M18 rows into it would silently drop that evidence.
 */

/** Per-query candidate id sets, kept structurally separate by backend. The simulated-hybrid ids are
  * an offline supplement view only: ES ids first, then Qdrant ids not already present in ES, with no
  * score fusion or reranking. */
final case class M19QueryCandidateSets(
  queryId: String,
  esCandidateIds: List[String],
  qdrantCandidateIds: List[String],
  simulatedHybridCandidateIds: List[String],
)

object M19QueryCandidateSets {
  def of(queryId: String, esCandidateIds: List[String], qdrantCandidateIds: List[String]): M19QueryCandidateSets =
    M19QueryCandidateSets(
      queryId = queryId,
      esCandidateIds = esCandidateIds,
      qdrantCandidateIds = qdrantCandidateIds,
      simulatedHybridCandidateIds = simulatedHybrid(esCandidateIds, qdrantCandidateIds),
    )

  private def simulatedHybrid(esIds: List[String], qdrantIds: List[String]): List[String] = {
    val esSet = esIds.toSet
    esIds ++ distinctInOrder(qdrantIds.filterNot(esSet.contains))
  }

  private def distinctInOrder(ids: List[String]): List[String] =
    ids.foldLeft((Set.empty[String], List.empty[String])) {
      case ((seen, acc), id) =>
        if (seen.contains(id)) (seen, acc)
        else (seen + id, id :: acc)
    }._2.reverse
}

/** Per-backend missing-lookup evidence, counted only from actual M18 lookup evidence.
  * [[M18MissingLookup.LookupNotEvaluated]] is preserved as its own count and is never folded into
  * either hydrated or missing-lookup. */
final case class M19BackendLookupCounts(
  backend: M18OfflineEvalBackend,
  hydratedCount: Int,
  missingLookupCount: Int,
  lookupNotEvaluatedCount: Int,
) {
  def evaluatedCount: Int = hydratedCount + missingLookupCount

  def missingLookupRate: Option[BigDecimal] =
    if (evaluatedCount == 0) None else Some(BigDecimal(missingLookupCount) / BigDecimal(evaluatedCount))
}

object M19BackendLookupCounts {
  def empty(backend: M18OfflineEvalBackend): M19BackendLookupCounts =
    M19BackendLookupCounts(backend, hydratedCount = 0, missingLookupCount = 0, lookupNotEvaluatedCount = 0)

  def fromRows(backend: M18OfflineEvalBackend, rows: List[M18BackendCandidateRow]): M19BackendLookupCounts =
    M19BackendLookupCounts(
      backend = backend,
      hydratedCount = rows.count(_.missingLookup == M18MissingLookup.Hydrated),
      missingLookupCount = rows.count(_.missingLookup == M18MissingLookup.MissingLookup),
      lookupNotEvaluatedCount = rows.count(_.missingLookup == M18MissingLookup.LookupNotEvaluated),
    )

  def sum(counts: List[M19BackendLookupCounts], backend: M18OfflineEvalBackend): M19BackendLookupCounts =
    counts.filter(_.backend == backend) match {
      case Nil => empty(backend)
      case matching =>
        M19BackendLookupCounts(
          backend = backend,
          hydratedCount = matching.map(_.hydratedCount).sum,
          missingLookupCount = matching.map(_.missingLookupCount).sum,
          lookupNotEvaluatedCount = matching.map(_.lookupNotEvaluatedCount).sum,
        )
    }
}

/** Honest tri-state latency availability for a single backend on a single query. [[NotExecuted]] is
  * distinct from [[Absent]]: it means the backend leg never ran for this eval at all (e.g. Qdrant was
  * resource-gated), not that a clock was attached but returned nothing. */
enum M19LatencyAvailability {
  case Present
  case Absent
  case NotExecuted
}

final case class M19BackendLatency(
  backend: M18OfflineEvalBackend,
  availability: M19LatencyAvailability,
)

final case class M19BackendLatencyAggregate(
  backend: M18OfflineEvalBackend,
  presentCount: Int,
  absentCount: Int,
  notExecutedCount: Int,
)

object M19BackendLatencyAggregate {
  def sum(perQuery: List[M19BackendLatency], backend: M18OfflineEvalBackend): M19BackendLatencyAggregate = {
    val matching = perQuery.filter(_.backend == backend)
    M19BackendLatencyAggregate(
      backend = backend,
      presentCount = matching.count(_.availability == M19LatencyAvailability.Present),
      absentCount = matching.count(_.availability == M19LatencyAvailability.Absent),
      notExecutedCount = matching.count(_.availability == M19LatencyAvailability.NotExecuted),
    )
  }
}

/** Per-query M19 metrics. Expected/complement/noise counts are populated only when at least one M18
  * row for this query carries expectation evidence (i.e. is not [[M18ExpectedMatch.ExpectationsUnavailable]]);
  * otherwise [[expectationsAvailable]] is false and those counts are left at zero rather than invented. */
final case class M19QueryMetrics(
  queryId: String,
  candidateIds: M19QueryCandidateSets,
  overlapCount: Int,
  expectationsAvailable: Boolean,
  qdrantComplementCount: Int,
  qdrantNoiseCount: Int,
  lookupByBackend: List[M19BackendLookupCounts],
  latencyByBackend: List[M19BackendLatency],
)

final case class M19AggregateMetrics(
  queryCount: Int,
  overlapCount: Int,
  expectationsAvailableQueryCount: Int,
  expectationsUnavailableQueryCount: Int,
  qdrantComplementCount: Int,
  qdrantNoiseCount: Int,
  lookupByBackend: List[M19BackendLookupCounts],
  latencyByBackend: List[M19BackendLatencyAggregate],
)

object M19DualEngineOfflineEvalMetrics {

  def queryMetrics(result: M18DualEngineOfflineEvalResult): List[M19QueryMetrics] = {
    val esResults = executedQueryResults(result.es)
    val qdrantResults = executedQueryResults(result.qdrant)
    val esById = esResults.map(r => r.queryId -> r).toMap
    val qdrantById = qdrantResults.map(r => r.queryId -> r).toMap
    val queryIds = stableQueryIdOrder(esResults.map(_.queryId), qdrantResults.map(_.queryId))

    queryIds.map { queryId =>
      forQuery(
        queryId = queryId,
        esExecuted = result.esExecuted,
        qdrantExecuted = result.qdrantExecuted,
        esLeg = esById.get(queryId),
        qdrantLeg = qdrantById.get(queryId),
      )
    }
  }

  def aggregate(perQuery: List[M19QueryMetrics]): M19AggregateMetrics = {
    val allLookups = perQuery.flatMap(_.lookupByBackend)
    val allLatencies = perQuery.flatMap(_.latencyByBackend)

    M19AggregateMetrics(
      queryCount = perQuery.size,
      overlapCount = perQuery.map(_.overlapCount).sum,
      expectationsAvailableQueryCount = perQuery.count(_.expectationsAvailable),
      expectationsUnavailableQueryCount = perQuery.count(!_.expectationsAvailable),
      qdrantComplementCount = perQuery.map(_.qdrantComplementCount).sum,
      qdrantNoiseCount = perQuery.map(_.qdrantNoiseCount).sum,
      lookupByBackend = M18OfflineEvalBackend.stableOrder.map(backend => M19BackendLookupCounts.sum(allLookups, backend)),
      latencyByBackend = M18OfflineEvalBackend.stableOrder.map(backend => M19BackendLatencyAggregate.sum(allLatencies, backend)),
    )
  }

  private def forQuery(
    queryId: String,
    esExecuted: Boolean,
    qdrantExecuted: Boolean,
    esLeg: Option[M18BackendQueryLegResult],
    qdrantLeg: Option[M18BackendQueryLegResult],
  ): M19QueryMetrics = {
    val esRows = esLeg.map(_.candidates).getOrElse(Nil).sortBy(_.rank)
    val qdrantRows = qdrantLeg.map(_.candidates).getOrElse(Nil).sortBy(_.rank)
    val esIds = esRows.map(_.candidateId)
    val qdrantIds = qdrantRows.map(_.candidateId)
    val esIdSet = esIds.toSet

    val expectationsAvailable =
      (esRows ++ qdrantRows).exists(_.expectedMatch != M18ExpectedMatch.ExpectationsUnavailable)

    val qdrantComplementCount =
      if (!expectationsAvailable) 0
      else
        qdrantRows
          .filter(_.expectedMatch == M18ExpectedMatch.Matched)
          .map(_.candidateId)
          .toSet
          .diff(esIdSet)
          .size

    val qdrantNoiseCount =
      if (!expectationsAvailable) 0
      else qdrantRows.count(_.expectedMatch == M18ExpectedMatch.NotExpected)

    M19QueryMetrics(
      queryId = queryId,
      candidateIds = M19QueryCandidateSets.of(queryId, esIds, qdrantIds),
      overlapCount = esIdSet.intersect(qdrantIds.toSet).size,
      expectationsAvailable = expectationsAvailable,
      qdrantComplementCount = qdrantComplementCount,
      qdrantNoiseCount = qdrantNoiseCount,
      lookupByBackend = List(
        M19BackendLookupCounts.fromRows(M18OfflineEvalBackend.Es, esRows),
        M19BackendLookupCounts.fromRows(M18OfflineEvalBackend.Qdrant, qdrantRows),
      ),
      latencyByBackend = List(
        latency(M18OfflineEvalBackend.Es, esExecuted, esLeg),
        latency(M18OfflineEvalBackend.Qdrant, qdrantExecuted, qdrantLeg),
      ),
    )
  }

  private def latency(
    backend: M18OfflineEvalBackend,
    legExecuted: Boolean,
    legResult: Option[M18BackendQueryLegResult],
  ): M19BackendLatency =
    if (!legExecuted) M19BackendLatency(backend, M19LatencyAvailability.NotExecuted)
    else
      legResult.flatMap(_.latencyNanos) match {
        case Some(_) => M19BackendLatency(backend, M19LatencyAvailability.Present)
        case None    => M19BackendLatency(backend, M19LatencyAvailability.Absent)
      }

  private def executedQueryResults(outcome: M18BackendLegOutcome): List[M18BackendQueryLegResult] =
    outcome match {
      case executed: M18BackendLegOutcome.Executed => executed.queryResults
      case _: M18BackendLegOutcome.Skipped         => Nil
    }

  private def stableQueryIdOrder(esQueryIds: List[String], qdrantQueryIds: List[String]): List[String] = {
    val seen = scala.collection.mutable.LinkedHashSet.empty[String]
    esQueryIds.foreach(seen.add)
    qdrantQueryIds.foreach(seen.add)
    seen.toList
  }
}
