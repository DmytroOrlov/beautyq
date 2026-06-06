package leaderboard.search

import leaderboard.model.{MasterLocationId, MasterServiceOfferVariantId, QueryFailure, ServiceId}
import leaderboard.search.eval.{
  BeautySearchEvalQuery,
  EvalCarouselWeights,
  EvalProviderExpectation,
  EvalScoring,
  EvalServiceExpectation,
  EvalVariantExpectation,
}
import leaderboard.search.qdrant.{
  QdrantEmbeddingBenchmarkCandidate,
  QdrantEmbeddingBenchmarkCandidateExecutor,
  QdrantEmbeddingBenchmarkPlan,
  QdrantEmbeddingBenchmarkQueryResult,
  QdrantEmbeddingBenchmarkRunMode,
  QdrantEmbeddingBenchmarkRunner,
}
import org.scalatest.wordspec.AnyWordSpec
import zio.{IO, Ref, Runtime, Unsafe, ZIO}

import java.util.UUID

final class QdrantEmbeddingBenchmarkRunnerSpec extends AnyWordSpec {
  "QdrantEmbeddingBenchmarkRunner" should {
    "accept single-endpoint mode with exactly one candidate and produce one candidate report" in {
      val candidate = benchmarkCandidate("candidate-a")
      val plan = QdrantEmbeddingBenchmarkPlan(QdrantEmbeddingBenchmarkRunMode.SingleEndpointManualRestart, List(candidate), k = 1)
      val query = evalQuery("q1", acceptableVariantIds = List(variantId(1)))
      val executor = new StaticExecutor(candidate.candidateId -> List(queryResult(candidate.candidateId, query.id, topVariantIds = List(variantId(1)))))

      val report = run(new QdrantEmbeddingBenchmarkRunner(executor).run(plan, List(query)))

      assert(report.plan.runMode == QdrantEmbeddingBenchmarkRunMode.SingleEndpointManualRestart)
      assert(report.candidateReports.map(_.candidate) == List(candidate))
      assert(report.candidateReports.size == 1)
      assert(report.comparisons.isEmpty)
    }

    "accept dual-endpoint mode with exactly two candidates and produce two reports plus one comparison" in {
      val left = benchmarkCandidate("candidate-a")
      val right = benchmarkCandidate("candidate-b")
      val plan = QdrantEmbeddingBenchmarkPlan(QdrantEmbeddingBenchmarkRunMode.DualEndpointParallel, List(left, right), k = 1)
      val query = evalQuery("q1", acceptableVariantIds = List(variantId(2)))
      val executor = new StaticExecutor(
        left.candidateId -> List(queryResult(left.candidateId, query.id, topVariantIds = List(variantId(1)))),
        right.candidateId -> List(queryResult(right.candidateId, query.id, topVariantIds = List(variantId(2)))),
      )

      val report = run(new QdrantEmbeddingBenchmarkRunner(executor).run(plan, List(query)))

      assert(report.candidateReports.map(_.candidate) == List(left, right))
      assert(report.comparisons.size == 1)
      assert(report.comparisons.head.variantRecallAtKDelta == 1.0)
    }

    "fail wrong candidate count with QueryFailure.operation" in {
      val first = benchmarkCandidate("candidate-a")
      val second = benchmarkCandidate("candidate-b")
      val plan = QdrantEmbeddingBenchmarkPlan(QdrantEmbeddingBenchmarkRunMode.SingleEndpointManualRestart, List(first, second), k = 1)

      val failure = runFail(new QdrantEmbeddingBenchmarkRunner(new StaticExecutor()).run(plan, Nil))

      failure match {
        case QueryFailure.OperationFailure(operationName, message) =>
          assert(operationName == "qdrant-embedding-benchmark-runner")
          assert(message.contains("requires exactly 1 candidate(s), got 2"))
        case other =>
          fail(s"expected OperationFailure, got $other")
      }
    }

    "convert eval acceptable ids into benchmark expectations" in {
      val candidate = benchmarkCandidate("candidate-a")
      val plan = QdrantEmbeddingBenchmarkPlan(QdrantEmbeddingBenchmarkRunMode.SingleEndpointManualRestart, List(candidate), k = 2)
      val query = evalQuery(
        id = "q1",
        acceptableVariantIds = List(variantId(2)),
        acceptableProviderIds = List(providerId(3)),
        acceptableServiceIds = List(serviceId(4)),
      )
      val executor = new StaticExecutor(
        candidate.candidateId -> List(
          queryResult(
            candidateId = candidate.candidateId,
            queryId = query.id,
            topVariantIds = List(variantId(1), variantId(2)),
            topProviderIds = List(providerId(3)),
            topServiceIds = List(serviceId(4)),
          )
        )
      )

      val report = run(new QdrantEmbeddingBenchmarkRunner(executor).run(plan, List(query)))
      val metrics = report.candidateReports.head.queryMetrics.head

      assert(metrics.variantHitAtK)
      assert(metrics.variantHitRank.contains(2))
      assert(metrics.providerHitAtK)
      assert(metrics.serviceHitAtK)
    }

    "call the executor once per candidate in plan order" in {
      val first = benchmarkCandidate("candidate-a")
      val second = benchmarkCandidate("candidate-b")
      val callsRef = runUio(Ref.make(List.empty[String]))
      val plan = QdrantEmbeddingBenchmarkPlan(QdrantEmbeddingBenchmarkRunMode.DualEndpointParallel, List(first, second), k = 1)
      val query = evalQuery("q1", acceptableVariantIds = List(variantId(1)))
      val executor = new RecordingExecutor(
        callsRef = callsRef,
        resultsByCandidateId = Map(
          first.candidateId -> List(queryResult(first.candidateId, query.id, topVariantIds = List(variantId(1)))),
          second.candidateId -> List(queryResult(second.candidateId, query.id, topVariantIds = List(variantId(1)))),
        ),
      )

      run(new QdrantEmbeddingBenchmarkRunner(executor).run(plan, List(query)))

      assert(runUio(callsRef.get) == List(first.candidateId, second.candidateId))
    }

    "preserve candidate order in the report" in {
      val first = benchmarkCandidate("candidate-b")
      val second = benchmarkCandidate("candidate-a")
      val plan = QdrantEmbeddingBenchmarkPlan(QdrantEmbeddingBenchmarkRunMode.DualEndpointParallel, List(first, second), k = 1)
      val query = evalQuery("q1", acceptableVariantIds = List(variantId(1)))
      val executor = new StaticExecutor(
        first.candidateId -> List(queryResult(first.candidateId, query.id, topVariantIds = List(variantId(1)))),
        second.candidateId -> List(queryResult(second.candidateId, query.id, topVariantIds = List(variantId(1)))),
      )

      val report = run(new QdrantEmbeddingBenchmarkRunner(executor).run(plan, List(query)))

      assert(report.candidateReports.map(_.candidate) == List(first, second))
    }

    "run with only an injected fake executor and no Qdrant, llama, or file IO dependencies" in {
      val candidate = benchmarkCandidate("candidate-a")
      val plan = QdrantEmbeddingBenchmarkPlan(QdrantEmbeddingBenchmarkRunMode.SingleEndpointManualRestart, List(candidate), k = 1)
      val query = evalQuery("q1", acceptableVariantIds = List(variantId(1)))
      val executor = new StaticExecutor(candidate.candidateId -> List(queryResult(candidate.candidateId, query.id, topVariantIds = List(variantId(1)))))

      val report = run(new QdrantEmbeddingBenchmarkRunner(executor).run(plan, List(query)))

      assert(report.candidateReports.head.aggregate.variantRecallAtK == 1.0)
    }
  }

  private final class StaticExecutor(results: (String, List[QdrantEmbeddingBenchmarkQueryResult])*) extends QdrantEmbeddingBenchmarkCandidateExecutor {
    private val resultsByCandidateId = results.toMap

    override def runCandidate(
      candidate: QdrantEmbeddingBenchmarkCandidate,
      queries: List[BeautySearchEvalQuery],
    ): IO[QueryFailure, List[QdrantEmbeddingBenchmarkQueryResult]] =
      ZIO.succeed(resultsByCandidateId.getOrElse(candidate.candidateId, Nil))
  }

  private final class RecordingExecutor(
    callsRef: Ref[List[String]],
    resultsByCandidateId: Map[String, List[QdrantEmbeddingBenchmarkQueryResult]],
  ) extends QdrantEmbeddingBenchmarkCandidateExecutor {
    override def runCandidate(
      candidate: QdrantEmbeddingBenchmarkCandidate,
      queries: List[BeautySearchEvalQuery],
    ): IO[QueryFailure, List[QdrantEmbeddingBenchmarkQueryResult]] =
      callsRef.update(_ :+ candidate.candidateId) *> ZIO.succeed(resultsByCandidateId.getOrElse(candidate.candidateId, Nil))
  }

  private def benchmarkCandidate(candidateId: String): QdrantEmbeddingBenchmarkCandidate =
    QdrantEmbeddingBenchmarkCandidate(
      candidateId = candidateId,
      modelName = s"model-$candidateId",
      endpointLabel = s"endpoint-$candidateId",
      vectorDimension = 1024,
    )

  private def evalQuery(
    id: String,
    acceptableVariantIds: List[MasterServiceOfferVariantId],
    acceptableProviderIds: List[MasterLocationId] = Nil,
    acceptableServiceIds: List[ServiceId] = Nil,
  ): BeautySearchEvalQuery =
    BeautySearchEvalQuery(
      id = id,
      query = s"query-$id",
      language = "ru",
      queryTypes = Nil,
      expectedVariantCarousel = EvalVariantExpectation(acceptableVariantIds = acceptableVariantIds),
      expectedProviderCarousel = EvalProviderExpectation(acceptableProviderLocationIds = acceptableProviderIds),
      expectedServiceIntentCarousel = EvalServiceExpectation(acceptableServiceIds = acceptableServiceIds),
      scoring = EvalScoring(
        variantCarousel = EvalCarouselWeights(),
        providerCarousel = EvalCarouselWeights(),
        serviceIntentCarousel = EvalCarouselWeights(),
      ),
    )

  private def queryResult(
    candidateId: String,
    queryId: String,
    topVariantIds: List[MasterServiceOfferVariantId],
    topProviderIds: List[MasterLocationId] = Nil,
    topServiceIds: List[ServiceId] = Nil,
  ): QdrantEmbeddingBenchmarkQueryResult =
    QdrantEmbeddingBenchmarkQueryResult(
      candidateId = candidateId,
      queryId = queryId,
      queryText = s"query-$queryId",
      topVariantIds = topVariantIds,
      topProviderIds = topProviderIds,
      topServiceIds = topServiceIds,
      scores = Nil,
    )

  private def run[A](effect: IO[QueryFailure, A]): A =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
    }

  private def runUio[A](effect: UIO[A]): A =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
    }

  private def runFail[A](effect: IO[QueryFailure, A]): QueryFailure =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(effect.either).getOrThrowFiberFailure().swap.getOrElse(sys.error("expected failure"))
    }

  private def variantId(value: Int): MasterServiceOfferVariantId = id(value)

  private def providerId(value: Int): MasterLocationId = id(value + 100)

  private def serviceId(value: Int): ServiceId = id(value + 200)

  private def id(value: Int): UUID =
    UUID.fromString(f"00000000-0000-0000-0000-$value%012d")

  private type UIO[A] = zio.UIO[A]
}
