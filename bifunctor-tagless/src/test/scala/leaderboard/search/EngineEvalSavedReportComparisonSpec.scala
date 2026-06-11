package leaderboard.search

import leaderboard.model.{MasterServiceOfferVariantId, QueryFailure}
import leaderboard.search.eval.{
  EngineEvalAggregateReport,
  EngineEvalEngine,
  EngineEvalQueryReport,
  EngineEvalReportJson,
  EngineEvalSavedReportComparison,
  EngineEvalResult,
  EngineExpectedRole,
}
import org.scalatest.wordspec.AnyWordSpec

import java.util.UUID

final class EngineEvalSavedReportComparisonSpec extends AnyWordSpec {

  private def variantId(slot: Int): MasterServiceOfferVariantId =
    UUID.fromString(f"00000000-0000-0000-0000-00000000${slot}%04x")

  private def queryReport(
    queryId: String,
    expectedRole: EngineExpectedRole,
    expectedIds: Set[MasterServiceOfferVariantId],
    esIds: List[MasterServiceOfferVariantId],
    qdrantIds: List[MasterServiceOfferVariantId],
  ): EngineEvalQueryReport =
    EngineEvalQueryReport.from(
      expectedRole = expectedRole,
      expectedVariantIds = expectedIds,
      es = EngineEvalResult(EngineEvalEngine.Elasticsearch, queryId, esIds),
      qdrant = EngineEvalResult(EngineEvalEngine.Qdrant, queryId, qdrantIds),
    )

  "EngineEvalSavedReportComparison" should {

    "return positive deltas when the right report has higher aggregate counts" in {
      val v1 = variantId(1)
      val v2 = variantId(2)
      val v3 = variantId(3)
      val v4 = variantId(4)
      val v5 = variantId(5)

      val left = EngineEvalAggregateReport.from(List(
        queryReport("q_l_1", EngineExpectedRole.EsShouldHandle, Set(v1, v2), List(v1), List(v2)),
      ))

      val right = EngineEvalAggregateReport.from(List(
        queryReport("q_r_1", EngineExpectedRole.HybridMayImprove, Set(v1, v2, v3, v4, v5), List(v1, v2, v3), List(v2, v3, v4, v5)),
        queryReport("q_r_2", EngineExpectedRole.EsShouldHandle, Set(v1, v4), List(v1, v4), List(v1)),
      ))

      val comparison = EngineEvalSavedReportComparison.compareReports(left, right)

      assert(comparison.left == left.aggregate)
      assert(comparison.right == right.aggregate)
      assert(comparison.queryCountDelta == 1)
      assert(comparison.expectedVariantCountDelta > 0)
      assert(comparison.esRecallCountDelta > 0)
      assert(comparison.qdrantComplementCountDelta > 0)
      assert(comparison.simulatedHybridGainCountDelta > 0)
    }

    "return negative and zero deltas" in {
      val v1 = variantId(1)
      val v2 = variantId(2)
      val v3 = variantId(3)

      val left = EngineEvalAggregateReport.from(List(
        queryReport("q_l_a", EngineExpectedRole.HybridMayImprove, Set(v1, v2, v3), List(v1, v2, v3), List(v3)),
        queryReport("q_l_b", EngineExpectedRole.EsShouldHandle, Set(v1), List(v1), List(v1)),
      ))

      val right = EngineEvalAggregateReport.from(List(
        queryReport("q_r_a", EngineExpectedRole.EsShouldHandle, Set(v1, v2), List(v1), List(v1, v2)),
      ))

      val comparison = EngineEvalSavedReportComparison.compareReports(left, right)

      assert(comparison.queryCountDelta == -1)
      assert(comparison.esRecallCountDelta < 0)
      assert(comparison.qdrantRecallCountDelta == 0)
    }

    "compare encoded aggregate reports via compareReportJsonStrings" in {
      val v1 = variantId(1)
      val v2 = variantId(2)
      val v3 = variantId(3)

      val left = EngineEvalAggregateReport.from(List(
        queryReport("q_jl", EngineExpectedRole.EsShouldHandle, Set(v1), List(v1), List(v1)),
      ))

      val right = EngineEvalAggregateReport.from(List(
        queryReport("q_jr", EngineExpectedRole.HybridMayImprove, Set(v1, v2, v3), List(v1, v2), List(v2, v3)),
      ))

      val leftJson = EngineEvalReportJson.encodeReportString(left)
      val rightJson = EngineEvalReportJson.encodeReportString(right)

      val result = EngineEvalSavedReportComparison.compareReportJsonStrings(leftJson, rightJson)

      result match {
        case Right(comparison) =>
          assert(comparison.queryCountDelta == 0)
          assert(comparison.esRecallCountDelta == 1)
        case Left(failure) =>
          fail(s"unexpected failure: $failure")
      }
    }

    "fail clearly on invalid JSON string" in {
      val v1 = variantId(1)

      val right = EngineEvalAggregateReport.from(List(
        queryReport("q_valid", EngineExpectedRole.EsShouldHandle, Set(v1), List(v1), List(v1)),
      ))
      val validRightJson = EngineEvalReportJson.encodeReportString(right)

      val result = EngineEvalSavedReportComparison.compareReportJsonStrings("{", validRightJson)

      result match {
        case Left(QueryFailure.OperationFailure(operationName, message)) =>
          assert(operationName == "engine-eval-report-json")
          assert(message.contains("Invalid EngineEval report JSON"))
        case other =>
          fail(s"expected OperationFailure, got $other")
      }
    }

    "format comparison with title and signed delta labels" in {
      val v1 = variantId(1)
      val v2 = variantId(2)
      val v3 = variantId(3)

      val left = EngineEvalAggregateReport.from(List(
        queryReport("q_f_l", EngineExpectedRole.HybridMayImprove, Set(v1, v2, v3), List(v1, v2, v3), List(v2, v3)),
      ))

      val right = EngineEvalAggregateReport.from(List(
        queryReport("q_f_r", EngineExpectedRole.EsShouldHandle, Set(v1), List(v1), List(v1)),
      ))

      val comparison = EngineEvalSavedReportComparison.compareReports(left, right)
      val formatted = EngineEvalSavedReportComparison.formatComparison(comparison)

      assert(formatted.contains("EngineEval saved-report comparison"))
      assert(formatted.contains("queryCountDelta: "))
      assert(formatted.contains("esRecallCountDelta:"))
      assert(formatted.contains("qdrantComplementCountDelta:"))
      assert(formatted.contains("simulatedHybridGainCountDelta:"))
      assert(formatted.contains("-1"))
    }
  }
}
