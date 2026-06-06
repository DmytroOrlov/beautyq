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
    for {
      _ <- validateCandidateCount(plan)
      queryResultsByCandidateId <- ZIO.foreach(plan.candidates) { candidate =>
        executor.runCandidate(candidate, queries).map(candidate.candidateId -> _)
      }.map(_.toMap)
    } yield QdrantEmbeddingBenchmark.report(
      plan = plan,
      queryResultsByCandidateId = queryResultsByCandidateId,
      expectationsByQueryId = expectationsByQueryId(queries),
    )

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

  private def expectationsByQueryId(queries: List[BeautySearchEvalQuery]): Map[String, QdrantEmbeddingBenchmarkExpected] =
    queries.map { query =>
      query.id -> QdrantEmbeddingBenchmarkExpected(
        acceptableVariantIds = query.expectedVariantCarousel.acceptableVariantIds,
        acceptableProviderIds = query.expectedProviderCarousel.acceptableProviderLocationIds,
        acceptableServiceIds = query.expectedServiceIntentCarousel.acceptableServiceIds,
      )
    }.toMap
}
