package leaderboard.search

import leaderboard.model.{MasterServiceOfferVariantId, QueryFailure}
import leaderboard.search.eval.{
  EngineEvalAggregateReport,
  EngineEvalEngine,
  EngineEvalQueryReport,
  EngineEvalReportJson,
  EngineEvalResult,
  EngineExpectedRole,
}
import org.scalatest.wordspec.AnyWordSpec

import java.util.UUID

final class EngineEvalReportJsonSpec extends AnyWordSpec {

  private def variantId(slot: Int): MasterServiceOfferVariantId =
    UUID.fromString(f"00000000-0000-0000-0000-00000000${slot}%04x")

  private val v1: MasterServiceOfferVariantId = variantId(1)
  private val v2: MasterServiceOfferVariantId = variantId(2)
  private val v3: MasterServiceOfferVariantId = variantId(3)
  private val v4: MasterServiceOfferVariantId = variantId(4)
  private val v5: MasterServiceOfferVariantId = variantId(5)

  "EngineEvalReportJson" should {

    "round-trip an aggregate report with two query reports" in {
      val es1 = EngineEvalResult(EngineEvalEngine.Elasticsearch, "q_rt_1", List(v1, v2))
      val qdrant1 = EngineEvalResult(EngineEvalEngine.Qdrant, "q_rt_1", List(v2, v3))
      val report1 = EngineEvalQueryReport.from(
        expectedRole = EngineExpectedRole.HybridMayImprove,
        expectedVariantIds = Set(v1, v2, v3),
        es = es1,
        qdrant = qdrant1,
      )

      val es2 = EngineEvalResult(EngineEvalEngine.Elasticsearch, "q_rt_2", List(v4))
      val qdrant2 = EngineEvalResult(EngineEvalEngine.Qdrant, "q_rt_2", List(v5))
      val report2 = EngineEvalQueryReport.from(
        expectedRole = EngineExpectedRole.EsShouldHandle,
        expectedVariantIds = Set(v4, v5),
        es = es2,
        qdrant = qdrant2,
      )

      val original = EngineEvalAggregateReport.from(List(report1, report2))
      val decoded = EngineEvalReportJson
        .decodeReportString(EngineEvalReportJson.encodeReportString(original))
        .fold(failure => fail(s"unexpected decode failure: $failure"), identity)

      assert(decoded == original)
      assert(decoded.queryReports.map(_.queryId) == List("q_rt_1", "q_rt_2"))
      assert(decoded.aggregate.queryCount == 2)
    }

    "not include roleBreakdown or roleAggregates in encoded JSON" in {
      val es1 = EngineEvalResult(EngineEvalEngine.Elasticsearch, "q_rb_1", List(v1))
      val qdrant1 = EngineEvalResult(EngineEvalEngine.Qdrant, "q_rb_1", List(v2))
      val report1 = EngineEvalQueryReport.from(
        expectedRole = EngineExpectedRole.EsShouldHandle,
        expectedVariantIds = Set(v1, v2),
        es = es1,
        qdrant = qdrant1,
      )

      val report = EngineEvalAggregateReport.from(List(report1))
      val encoded = EngineEvalReportJson.encodeReportString(report)

      assert(!encoded.contains("roleBreakdown"))
      assert(!encoded.contains("roleAggregates"))
    }

    "preserve engine labels, expected roles, UUID variant ids, and duplicate/order in variantIds" in {
      val es = EngineEvalResult(EngineEvalEngine.Elasticsearch, "q_dup", List(v1, v1, v2))
      val qdrant = EngineEvalResult(EngineEvalEngine.Qdrant, "q_dup", List(v3))
      val report = EngineEvalQueryReport.from(
        expectedRole = EngineExpectedRole.QdrantMayComplement,
        expectedVariantIds = Set(v1, v2, v3),
        es = es,
        qdrant = qdrant,
      )

      val original = EngineEvalAggregateReport.from(List(report))
      val decoded = EngineEvalReportJson
        .decodeReportString(EngineEvalReportJson.encodeReportString(original))
        .fold(failure => fail(s"unexpected decode failure: $failure"), identity)

      decoded.queryReports match {
        case queryReport :: Nil =>
          assert(queryReport.es.variantIds == List(v1, v1, v2))
          assert(queryReport.expectedRole == EngineExpectedRole.QdrantMayComplement)
          assert(queryReport.simulatedHybrid.engine == EngineEvalEngine.SimulatedHybrid)
        case other =>
          fail(s"expected one query report, got ${other.size}: $other")
      }
    }

    "fail clearly on invalid JSON string" in {
      val result = EngineEvalReportJson.decodeReportString("{")

      result match {
        case Left(QueryFailure.OperationFailure(operationName, message)) =>
          assert(operationName == "engine-eval-report-json")
          assert(message.contains("Invalid EngineEval report JSON"))
        case other =>
          fail(s"expected OperationFailure, got $other")
      }
    }
  }
}
