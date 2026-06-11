package leaderboard.search.qdrant

import leaderboard.model.QueryFailure
import leaderboard.search.eval.BeautySearchEvalQuery
import zio.{IO, ZIO}

trait QdrantEmbeddingBenchmarkCandidateExecutor {
  def runCandidate(
    candidate: QdrantEmbeddingBenchmarkCandidate,
    queries: List[BeautySearchEvalQuery],
  ): IO[QueryFailure, List[QdrantEmbeddingBenchmarkQueryResult]]
}

final class QdrantEmbeddingBenchmarkRunner(
  executor: QdrantEmbeddingBenchmarkCandidateExecutor,
) {
  def run(
    plan: QdrantEmbeddingBenchmarkPlan,
    queries: List[BeautySearchEvalQuery],
  ): IO[QueryFailure, QdrantEmbeddingBenchmarkReport] =
    runWithQueryResults(plan, queries).map(_.report)

  def runWithQueryResults(
    plan: QdrantEmbeddingBenchmarkPlan,
    queries: List[BeautySearchEvalQuery],
  ): IO[QueryFailure, QdrantEmbeddingBenchmarkRunOutput] =
    for {
      _ <- validatePlan(plan)
      expectedByQueryId = expectationsByQueryId(queries)
      candidateResults <- ZIO.foreach(plan.candidates) { candidate =>
        for {
          results <- executor.runCandidate(candidate, queries)
          _       <- validateCandidateResults(candidate, results, expectedByQueryId)
        } yield candidate.candidateId -> results
      }
    } yield {
      val queryResultsByCandidateId = candidateResults.toMap
      QdrantEmbeddingBenchmarkRunOutput(
        report = QdrantEmbeddingBenchmark.report(
          plan = plan,
          queryResultsByCandidateId = queryResultsByCandidateId,
          expectationsByQueryId = expectedByQueryId,
        ),
        queryResultsByCandidateId = queryResultsByCandidateId,
      )
    }

  private def validatePlan(plan: QdrantEmbeddingBenchmarkPlan): IO[QueryFailure, Unit] =
    validateCandidateCount(plan) *> validateDistinctCandidateIds(plan)

  private def validateCandidateCount(plan: QdrantEmbeddingBenchmarkPlan): IO[QueryFailure, Unit] = {
    val expected = plan.runMode.expectedCandidateCount
    val actual = plan.candidates.size

    ZIO.fail(
      QueryFailure.operation(
        "qdrant-embedding-benchmark-runner",
        s"${plan.runMode} requires exactly $expected candidate(s), got $actual",
      )
    ).when(actual != expected).unit
  }

  private def validateDistinctCandidateIds(plan: QdrantEmbeddingBenchmarkPlan): IO[QueryFailure, Unit] = {
    val duplicateIds = plan.candidates
      .groupBy(_.candidateId)
      .collect {
        case (candidateId, candidates) if candidates.size > 1 => candidateId
      }
      .toList
      .sorted

    ZIO.fail(
      QueryFailure.operation(
        "qdrant-embedding-benchmark-runner",
        s"Benchmark plan contains duplicate candidate id(s): ${duplicateIds.mkString(", ")}",
      )
    ).when(duplicateIds.nonEmpty).unit
  }

  private def validateCandidateResults(
    candidate: QdrantEmbeddingBenchmarkCandidate,
    results: List[QdrantEmbeddingBenchmarkQueryResult],
    expectationsByQueryId: Map[String, QdrantEmbeddingBenchmarkExpected],
  ): IO[QueryFailure, Unit] =
    ZIO.foreachDiscard(results)(validateResult(candidate, _, expectationsByQueryId)) *>
      validateDistinctResultQueryIds(candidate, results) *>
      validateResultCoverage(candidate, results, expectationsByQueryId)

  private def validateResult(
    candidate: QdrantEmbeddingBenchmarkCandidate,
    result: QdrantEmbeddingBenchmarkQueryResult,
    expectationsByQueryId: Map[String, QdrantEmbeddingBenchmarkExpected],
  ): IO[QueryFailure, Unit] =
    validateResultCandidateId(candidate, result) *> validateExpectedQueryId(candidate, result, expectationsByQueryId)

  private def validateResultCandidateId(
    candidate: QdrantEmbeddingBenchmarkCandidate,
    result: QdrantEmbeddingBenchmarkQueryResult,
  ): IO[QueryFailure, Unit] =
    ZIO.fail(
      QueryFailure.operation(
        "qdrant-embedding-benchmark-runner",
        s"Candidate ${candidate.candidateId} executor returned result for candidate ${result.candidateId} on query ${result.queryId}",
      )
    ).when(result.candidateId != candidate.candidateId).unit

  private def validateExpectedQueryId(
    candidate: QdrantEmbeddingBenchmarkCandidate,
    result: QdrantEmbeddingBenchmarkQueryResult,
    expectationsByQueryId: Map[String, QdrantEmbeddingBenchmarkExpected],
  ): IO[QueryFailure, Unit] =
    ZIO.fail(
      QueryFailure.operation(
        "qdrant-embedding-benchmark-runner",
        s"Candidate ${candidate.candidateId} returned benchmark result for query ${result.queryId} without an expectation",
      )
    ).when(!expectationsByQueryId.contains(result.queryId)).unit

  private def validateDistinctResultQueryIds(
    candidate: QdrantEmbeddingBenchmarkCandidate,
    results: List[QdrantEmbeddingBenchmarkQueryResult],
  ): IO[QueryFailure, Unit] = {
    val duplicateQueryIds = results
      .groupBy(_.queryId)
      .collect {
        case (queryId, matchingResults) if matchingResults.size > 1 => queryId
      }
      .toList
      .sorted

    ZIO.fail(
      QueryFailure.operation(
        "qdrant-embedding-benchmark-runner",
        s"Candidate ${candidate.candidateId} returned duplicate benchmark result query id(s): ${duplicateQueryIds.mkString(", ")}",
      )
    ).when(duplicateQueryIds.nonEmpty).unit
  }

  private def validateResultCoverage(
    candidate: QdrantEmbeddingBenchmarkCandidate,
    results: List[QdrantEmbeddingBenchmarkQueryResult],
    expectationsByQueryId: Map[String, QdrantEmbeddingBenchmarkExpected],
  ): IO[QueryFailure, Unit] = {
    val missingQueryIds = (expectationsByQueryId.keySet -- results.iterator.map(_.queryId).toSet).toList.sorted

    ZIO.fail(
      QueryFailure.operation(
        "qdrant-embedding-benchmark-runner",
        s"Candidate ${candidate.candidateId} did not return benchmark result(s) for expected query id(s): ${missingQueryIds.mkString(", ")}",
      )
    ).when(missingQueryIds.nonEmpty).unit
  }

  private def expectationsByQueryId(queries: List[BeautySearchEvalQuery]): Map[String, QdrantEmbeddingBenchmarkExpected] =
    queries.map { query =>
      query.id -> QdrantEmbeddingBenchmarkExpected(
        acceptableVariantIds = query.expectedVariantCarousel.acceptableVariantIds,
        acceptableProviderIds = query.expectedProviderCarousel.acceptableProviderLocationIds,
        acceptableServiceIds = query.expectedServiceIntentCarousel.acceptableServiceIds,
      )
    }.toMap
}
