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
import leaderboard.search.qdrant.QdrantEmbeddingBenchmarkQueryResult
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
}
