package leaderboard.search

import leaderboard.model.{MasterServiceOfferVariantId, QueryFailure}
import leaderboard.search.eval.{
  BeautySearchEvalQuery,
  BeautySearchEvalReport,
  EngineEvalReportAssembly,
  EngineExpectedRole,
  EvalCarouselWeights,
  EvalProviderExpectation,
  EvalScoring,
  EvalServiceExpectation,
  EvalVariantExpectation,
}
import leaderboard.search.qdrant.{
  QdrantEmbeddingBenchmark,
  QdrantEmbeddingBenchmarkCandidate,
  QdrantEmbeddingBenchmarkPlan,
  QdrantEmbeddingBenchmarkQueryResult,
  QdrantEmbeddingBenchmarkRunMode,
  QdrantEmbeddingBenchmarkRunOutput,
}
import org.scalatest.wordspec.AnyWordSpec

import java.util.UUID

final class EngineEvalReportAssemblySpec extends AnyWordSpec {

  "EngineEvalReportAssembly.fromOutputs" should {

    "assemble reports in eval query order from ES and Qdrant outputs" in {
      val v1 = variantId(1)
      val v2 = variantId(2)
      val v3 = variantId(3)
      val v4 = variantId(4)
      val v5 = variantId(5)
      val q1 = evalQuery("q_asm_1", acceptableVariantIds = List(v1, v2))
      val q2 = evalQuery("q_asm_2", acceptableVariantIds = List(v3, v4, v5))
      val queries = List(q2, q1)

      val esReports = List(
        esReport("q_asm_1", List(v1, v3)),
        esReport("q_asm_2", List(v3, v4, v5)),
      )
      val qdrantResults = List(
        qdrantResult("q_asm_1", List(v2, v4)),
        qdrantResult("q_asm_2", List(v3, v5, v1)),
      )
      val roles = Map(
        "q_asm_1" -> EngineExpectedRole.HybridMayImprove,
        "q_asm_2" -> EngineExpectedRole.EsShouldHandle,
      )

      val result = EngineEvalReportAssembly.fromOutputs(queries, roles, esReports, qdrantResults)

      result match {
        case Right(report) =>
          assert(report.queryReports.map(_.queryId) == List("q_asm_2", "q_asm_1"))
          assert(report.aggregate.queryCount == 2)
          assert(report.aggregate.esRecallCount > 0)
          assert(report.aggregate.qdrantComplementCount > 0)
          assert(report.aggregate.simulatedHybridGainCount > 0)
        case Left(failure) =>
          fail(s"expected Right, got Left($failure)")
      }
    }

    "use caller-provided expected role and acceptable variant ids" in {
      val v1 = variantId(1)
      val v2 = variantId(2)
      val v3 = variantId(3)
      val v4 = variantId(4)
      val v5 = variantId(5)
      val q = evalQuery("q_role", acceptableVariantIds = List(v1, v2, v3))
      val queries = List(q)

      val esReports = List(esReport("q_role", List(v1, v2, v3)))
      val qdrantResults = List(qdrantResult("q_role", List(v4, v4, v5)))
      val roles = Map("q_role" -> EngineExpectedRole.QdrantShouldStaySilent)

      val result = EngineEvalReportAssembly.fromOutputs(queries, roles, esReports, qdrantResults)

      result match {
        case Right(report) =>
          val queryReport = report.queryReports.head
          assert(queryReport.expectedRole == EngineExpectedRole.QdrantShouldStaySilent)
          assert(queryReport.expectedVariantIds == Set(v1, v2, v3))
          assert(queryReport.metrics.qdrantNoiseCount == 2)
        case Left(failure) =>
          fail(s"expected Right, got Left($failure)")
      }
    }

    "fail clearly on duplicate input query ids" in {
      val v1 = variantId(1)
      val q1 = evalQuery("q_dup", acceptableVariantIds = List(v1))
      val queries = List(q1, q1)

      val result = EngineEvalReportAssembly.fromOutputs(
        queries,
        Map("q_dup" -> EngineExpectedRole.EsShouldHandle),
        List(esReport("q_dup", List(v1))),
        List(qdrantResult("q_dup", List(v1))),
      )

      result match {
        case Left(QueryFailure.OperationFailure(operationName, message)) =>
          assert(operationName == "engine-eval-report-assembly")
          assert(message.contains("duplicate query id"))
          assert(message.contains("q_dup"))
        case other =>
          fail(s"expected OperationFailure, got $other")
      }
    }

    "fail clearly on missing ES report" in {
      val v1 = variantId(1)
      val q = evalQuery("q_missing_es", acceptableVariantIds = List(v1))
      val queries = List(q)

      val result = EngineEvalReportAssembly.fromOutputs(
        queries,
        Map("q_missing_es" -> EngineExpectedRole.EsShouldHandle),
        Nil,
        List(qdrantResult("q_missing_es", List(v1))),
      )

      result match {
        case Left(QueryFailure.OperationFailure(operationName, message)) =>
          assert(operationName == "engine-eval-report-assembly")
          assert(message.contains("q_missing_es"))
          assert(message.contains("missing ES report"))
        case other =>
          fail(s"expected OperationFailure, got $other")
      }
    }

    "fail clearly on missing Qdrant result" in {
      val v1 = variantId(1)
      val q = evalQuery("q_missing_q", acceptableVariantIds = List(v1))
      val queries = List(q)

      val result = EngineEvalReportAssembly.fromOutputs(
        queries,
        Map("q_missing_q" -> EngineExpectedRole.EsShouldHandle),
        List(esReport("q_missing_q", List(v1))),
        Nil,
      )

      result match {
        case Left(QueryFailure.OperationFailure(operationName, message)) =>
          assert(operationName == "engine-eval-report-assembly")
          assert(message.contains("q_missing_q"))
          assert(message.contains("missing Qdrant result"))
        case other =>
          fail(s"expected OperationFailure, got $other")
      }
    }

    "fail clearly on missing expected role" in {
      val v1 = variantId(1)
      val q = evalQuery("q_missing_role", acceptableVariantIds = List(v1))
      val queries = List(q)

      val result = EngineEvalReportAssembly.fromOutputs(
        queries,
        Map.empty,
        List(esReport("q_missing_role", List(v1))),
        List(qdrantResult("q_missing_role", List(v1))),
      )

      result match {
        case Left(QueryFailure.OperationFailure(operationName, message)) =>
          assert(operationName == "engine-eval-report-assembly")
          assert(message.contains("q_missing_role"))
          assert(message.contains("missing expected role"))
        case other =>
          fail(s"expected OperationFailure, got $other")
      }
    }

    "ignore extra keys in expectedRolesByQueryId, esReports, and qdrantResults" in {
      val v1 = variantId(1)
      val v2 = variantId(2)
      val q = evalQuery("q_extra", acceptableVariantIds = List(v1))
      val queries = List(q)

      val result = EngineEvalReportAssembly.fromOutputs(
        queries,
        Map(
          "q_extra" -> EngineExpectedRole.EsShouldHandle,
          "q_other" -> EngineExpectedRole.QdrantMayComplement,
        ),
        List(
          esReport("q_extra", List(v1)),
          esReport("q_other", List(v2)),
        ),
        List(
          qdrantResult("q_extra", List(v1)),
          qdrantResult("q_other", List(v2)),
        ),
      )

      result match {
        case Right(report) =>
          assert(report.queryReports.map(_.queryId) == List("q_extra"))
          assert(report.aggregate.queryCount == 1)
        case Left(failure) =>
          fail(s"expected Right, got Left($failure)")
      }
    }
  }

  "EngineEvalReportAssembly.fromOutputsForQdrantCandidate" should {

    "assemble using selected candidate query results from QdrantEmbeddingBenchmarkRunOutput" in {
      val v1 = variantId(1)
      val v2 = variantId(2)
      val v3 = variantId(3)
      val v4 = variantId(4)
      val v5 = variantId(5)
      val q1 = evalQuery("q_out_1", acceptableVariantIds = List(v1, v2))
      val q2 = evalQuery("q_out_2", acceptableVariantIds = List(v3, v4, v5))
      val queries = List(q2, q1)

      val esReports = List(
        esReport("q_out_1", List(v1, v3)),
        esReport("q_out_2", List(v3, v4, v5)),
      )
      val candidateAResults = List(
        qdrantResultWithCandidate("candidate-a", "q_out_1", List(v2, v4)),
        qdrantResultWithCandidate("candidate-a", "q_out_2", List(v3, v5, v1)),
      )
      val candidateBResults = List(
        qdrantResultWithCandidate("candidate-b", "q_out_1", List(v5)),
        qdrantResultWithCandidate("candidate-b", "q_out_2", List(v4)),
      )
      val runOutput = qdrantRunOutput(
        "candidate-a" -> candidateAResults,
        "candidate-b" -> candidateBResults,
      )
      val roles = Map(
        "q_out_1" -> EngineExpectedRole.HybridMayImprove,
        "q_out_2" -> EngineExpectedRole.EsShouldHandle,
      )

      val result = EngineEvalReportAssembly.fromOutputsForQdrantCandidate(queries, roles, esReports, runOutput, "candidate-a")

      result match {
        case Right(report) =>
          assert(report.queryReports.map(_.queryId) == List("q_out_2", "q_out_1"))
          val q1Report = report.queryReports.find(_.queryId == "q_out_1").get
          val q2Report = report.queryReports.find(_.queryId == "q_out_2").get
          assert(q1Report.qdrant.variantIds == List(v2, v4))
          assert(q2Report.qdrant.variantIds == List(v3, v5, v1))
        case Left(failure) =>
          fail(s"expected Right, got Left($failure)")
      }
    }

    "fail clearly when selected candidate id is missing" in {
      val v1 = variantId(1)
      val q = evalQuery("q_miss_cand", acceptableVariantIds = List(v1))
      val queries = List(q)

      val runOutput = qdrantRunOutput(
        "candidate-a" -> List(qdrantResultWithCandidate("candidate-a", "q_miss_cand", List(v1))),
      )

      val result = EngineEvalReportAssembly.fromOutputsForQdrantCandidate(
        queries,
        Map("q_miss_cand" -> EngineExpectedRole.EsShouldHandle),
        List(esReport("q_miss_cand", List(v1))),
        runOutput,
        "candidate-missing",
      )

      result match {
        case Left(QueryFailure.OperationFailure(operationName, message)) =>
          assert(operationName == "engine-eval-report-assembly")
          assert(message.contains("missing Qdrant benchmark results for candidate id"))
          assert(message.contains("candidate-missing"))
        case other =>
          fail(s"expected OperationFailure, got $other")
      }
    }

    "propagate existing assembly validation for selected candidate results" in {
      val v1 = variantId(1)
      val v2 = variantId(2)
      val q1 = evalQuery("q_val_1", acceptableVariantIds = List(v1))
      val q2 = evalQuery("q_val_2", acceptableVariantIds = List(v2))
      val queries = List(q1, q2)

      val esReports = List(
        esReport("q_val_1", List(v1)),
        esReport("q_val_2", List(v2)),
      )
      val runOutput = qdrantRunOutput(
        "candidate-a" -> List(qdrantResultWithCandidate("candidate-a", "q_val_1", List(v1))),
      )

      val result = EngineEvalReportAssembly.fromOutputsForQdrantCandidate(
        queries,
        Map(
          "q_val_1" -> EngineExpectedRole.EsShouldHandle,
          "q_val_2" -> EngineExpectedRole.EsShouldHandle,
        ),
        esReports,
        runOutput,
        "candidate-a",
      )

      result match {
        case Left(QueryFailure.OperationFailure(operationName, message)) =>
          assert(operationName == "engine-eval-report-assembly")
          assert(message.contains("q_val_2"))
          assert(message.contains("missing Qdrant result"))
        case other =>
          fail(s"expected OperationFailure, got $other")
      }
    }
  }

  private def variantId(slot: Int): MasterServiceOfferVariantId =
    UUID.fromString(f"00000000-0000-0000-0000-00000000${slot}%04x")

  private def evalQuery(
    id: String,
    acceptableVariantIds: List[MasterServiceOfferVariantId],
  ): BeautySearchEvalQuery =
    BeautySearchEvalQuery(
      id = id,
      query = s"query $id",
      language = "de",
      queryTypes = Nil,
      expectedVariantCarousel = EvalVariantExpectation(acceptableVariantIds = acceptableVariantIds),
      expectedProviderCarousel = EvalProviderExpectation(),
      expectedServiceIntentCarousel = EvalServiceExpectation(),
      scoring = EvalScoring(EvalCarouselWeights(), EvalCarouselWeights(), EvalCarouselWeights()),
    )

  private def esReport(
    queryId: String,
    topVariantIds: List[MasterServiceOfferVariantId],
  ): BeautySearchEvalReport =
    BeautySearchEvalReport(
      queryId = queryId,
      query = s"query $queryId",
      score = 0,
      failedAssertions = Nil,
      topVariantIds = topVariantIds,
      topProviderLocationIds = Nil,
      topServiceIds = Nil,
    )

  private def qdrantResult(
    queryId: String,
    topVariantIds: List[MasterServiceOfferVariantId],
  ): QdrantEmbeddingBenchmarkQueryResult =
    QdrantEmbeddingBenchmarkQueryResult(
      candidateId = "candidate-a",
      queryId = queryId,
      queryText = s"query $queryId",
      topVariantIds = topVariantIds,
      topProviderIds = Nil,
      topServiceIds = Nil,
      scores = Nil,
    )

  private def qdrantResultWithCandidate(
    candidateId: String,
    queryId: String,
    topVariantIds: List[MasterServiceOfferVariantId],
  ): QdrantEmbeddingBenchmarkQueryResult =
    QdrantEmbeddingBenchmarkQueryResult(
      candidateId = candidateId,
      queryId = queryId,
      queryText = s"query $queryId",
      topVariantIds = topVariantIds,
      topProviderIds = Nil,
      topServiceIds = Nil,
      scores = Nil,
    )

  private def qdrantRunOutput(
    results: (String, List[QdrantEmbeddingBenchmarkQueryResult])*
  ): QdrantEmbeddingBenchmarkRunOutput = {
    val candidateIds = results.map(_._1)
    val candidates = candidateIds.map(id =>
      QdrantEmbeddingBenchmarkCandidate(
        candidateId = id,
        modelName = s"model-$id",
        endpointLabel = s"endpoint-$id",
        vectorDimension = 1024,
      )
    )
    val plan = QdrantEmbeddingBenchmarkPlan(
      runMode = QdrantEmbeddingBenchmarkRunMode.SingleEndpointManualRestart,
      candidates = candidates.toList,
      k = 1,
    )
    val report = QdrantEmbeddingBenchmark.report(plan, results.toMap, Map.empty)
    QdrantEmbeddingBenchmarkRunOutput(report, results.toMap)
  }
}
