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

    "compute role deltas with missing role on one side" in {
      val v1 = variantId(1)
      val v2 = variantId(2)

      val left = EngineEvalAggregateReport.from(List(
        queryReport("q_l_1", EngineExpectedRole.EsShouldHandle, Set(v1), List(v1), List()),
      ))

      val right = EngineEvalAggregateReport.from(List(
        queryReport("q_r_1", EngineExpectedRole.QdrantMayComplement, Set(v2), List(), List(v2)),
      ))

      val comparison = EngineEvalSavedReportComparison.compareReports(left, right)

      assert(comparison.roleComparisons.size == 2)

      val esRole = comparison.roleComparisons.find(_.role == EngineExpectedRole.EsShouldHandle).get
      assert(esRole.left.queryCount == 1)
      assert(esRole.right.queryCount == 0)
      assert(esRole.queryCountDelta == -1)

      val qdrantRole = comparison.roleComparisons.find(_.role == EngineExpectedRole.QdrantMayComplement).get
      assert(qdrantRole.left.queryCount == 0)
      assert(qdrantRole.right.queryCount == 1)
      assert(qdrantRole.queryCountDelta == 1)
    }

    "format role deltas in stable role order" in {
      val v1 = variantId(1)
      val v2 = variantId(2)
      val v3 = variantId(3)
      val v4 = variantId(4)

      val left = EngineEvalAggregateReport.from(List(
        queryReport("q_l_1", EngineExpectedRole.EsShouldHandle, Set(v1), List(v1), List()),
        queryReport("q_l_2", EngineExpectedRole.QdrantShouldStaySilent, Set(v2), List(), List(v2)),
      ))

      val right = EngineEvalAggregateReport.from(List(
        queryReport("q_r_1", EngineExpectedRole.QdrantMayComplement, Set(v3), List(), List(v3)),
        queryReport("q_r_2", EngineExpectedRole.HybridMayImprove, Set(v4), List(v4), List()),
      ))

      val comparison = EngineEvalSavedReportComparison.compareReports(left, right)
      val formatted = EngineEvalSavedReportComparison.formatComparison(comparison)

      assert(formatted.contains("roleDeltas:"))

      val rolesInOrder = List(
        EngineExpectedRole.EsShouldHandle,
        EngineExpectedRole.QdrantMayComplement,
        EngineExpectedRole.QdrantShouldStaySilent,
        EngineExpectedRole.HybridMayImprove,
      )

      val indices = rolesInOrder.map(r => formatted.indexOf(s"${r} |"))
      assert(indices(0) >= 0)
      assert(indices(1) >= 0)
      assert(indices(2) >= 0)
      assert(indices(3) >= 0)
      assert(indices(0) < indices(1))
      assert(indices(1) < indices(2))
      assert(indices(2) < indices(3))
    }

    "omit roleDeltas section when role deltas are zero" in {
      val v1 = variantId(1)

      val left = EngineEvalAggregateReport.from(List(
        queryReport("q_l_1", EngineExpectedRole.EsShouldHandle, Set(v1), List(v1), List()),
      ))

      val right = EngineEvalAggregateReport.from(List(
        queryReport("q_r_1", EngineExpectedRole.EsShouldHandle, Set(v1), List(v1), List()),
      ))

      val comparison = EngineEvalSavedReportComparison.compareReports(left, right)
      val formatted = EngineEvalSavedReportComparison.formatComparison(comparison)

      assert(!formatted.contains("roleDeltas:"))
    }
  }
}
