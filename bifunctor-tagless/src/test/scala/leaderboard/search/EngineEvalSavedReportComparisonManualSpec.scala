package leaderboard.search

import leaderboard.model.MasterServiceOfferVariantId
import leaderboard.search.eval._
import org.scalatest.wordspec.AnyWordSpec

import java.util.UUID

final class EngineEvalSavedReportComparisonManualSpec extends AnyWordSpec {
  "EngineEvalSavedReportComparison manual comparison" should {
    "compare two saved JSON reports from environment variables" in {
      if (envFlag(EngineEvalSavedReportComparisonManualSpec.EnvCompareSavedReports)) {
        println("ENGINE_EVAL_SAVED_REPORT_COMPARISON_MODE=REAL_ARTIFACTS")
        runRealArtifacts()
      } else {
        println("ENGINE_EVAL_SAVED_REPORT_COMPARISON_MODE=DEFAULT_FIXTURE")
        runDefaultFixture()
      }
    }
  }

  private def runDefaultFixture(): Unit = {
    val leftReport = EngineEvalSavedReportComparisonManualSpec.buildReport(
      queryId = "q_fixture_left",
      esIds = List(EngineEvalSavedReportComparisonManualSpec.v1, EngineEvalSavedReportComparisonManualSpec.v2),
      qdrantIds = List(EngineEvalSavedReportComparisonManualSpec.v2, EngineEvalSavedReportComparisonManualSpec.v3),
      expectedRole = EngineExpectedRole.QdrantMayComplement,
      expectedIds = Set(EngineEvalSavedReportComparisonManualSpec.v1, EngineEvalSavedReportComparisonManualSpec.v2, EngineEvalSavedReportComparisonManualSpec.v3),
    )

    val rightReport = EngineEvalSavedReportComparisonManualSpec.buildReport(
      queryId = "q_fixture_right",
      esIds = List(EngineEvalSavedReportComparisonManualSpec.v1),
      qdrantIds = List(EngineEvalSavedReportComparisonManualSpec.v1, EngineEvalSavedReportComparisonManualSpec.v3, EngineEvalSavedReportComparisonManualSpec.v4),
      expectedRole = EngineExpectedRole.HybridMayImprove,
      expectedIds = Set(EngineEvalSavedReportComparisonManualSpec.v1, EngineEvalSavedReportComparisonManualSpec.v3, EngineEvalSavedReportComparisonManualSpec.v4),
    )

    val leftJson = EngineEvalReportJson.encodeReportString(leftReport)
    val rightJson = EngineEvalReportJson.encodeReportString(rightReport)

    EngineEvalSavedReportComparison.compareReportJsonStrings(leftJson, rightJson) match {
      case Left(failure) =>
        fail(s"unexpected saved-report comparison failure: $failure")
      case Right(comparison) =>
        val formatted = EngineEvalSavedReportComparison.formatComparison(comparison)
        println("BEGIN_ENGINE_EVAL_SAVED_REPORT_COMPARISON")
        println(formatted)
        println("END_ENGINE_EVAL_SAVED_REPORT_COMPARISON")

        assert(comparison.queryCountDelta == 0, s"expected queryCountDelta 0, got ${comparison.queryCountDelta}")
        assert(
          comparison.esRecallCountDelta != 0 ||
            comparison.qdrantRecallCountDelta != 0 ||
            comparison.qdrantComplementCountDelta != 0 ||
            comparison.simulatedHybridGainCountDelta != 0,
          s"expected at least one non-zero delta, got left=${comparison.left} right=${comparison.right}"
        )
        (): Unit
    }
  }

  private def runRealArtifacts(): Unit = {
    val leftJson = sys.env.get(EngineEvalSavedReportComparisonManualSpec.EnvLeftJson)
    val rightJson = sys.env.get(EngineEvalSavedReportComparisonManualSpec.EnvRightJson)

    val missing = List(
      (EngineEvalSavedReportComparisonManualSpec.EnvLeftJson, leftJson),
      (EngineEvalSavedReportComparisonManualSpec.EnvRightJson, rightJson),
    ).collect { case (name, None) => name }

    if (missing.nonEmpty) {
      fail(s"ENGINE_EVAL_COMPARE_SAVED_REPORTS is enabled but missing required env var(s): ${missing.mkString(", ")}")
    } else {
      (leftJson, rightJson) match {
        case (Some(left), Some(right)) =>
          EngineEvalSavedReportComparison.compareReportJsonStrings(left, right) match {
            case Left(failure) =>
              fail(s"unexpected saved-report comparison failure: $failure")
            case Right(comparison) =>
              val formatted = EngineEvalSavedReportComparison.formatComparison(comparison)
              println("BEGIN_ENGINE_EVAL_SAVED_REPORT_COMPARISON")
              println(formatted)
              println("END_ENGINE_EVAL_SAVED_REPORT_COMPARISON")
          }
        case _ =>
          fail("unexpected env var extraction failure after missing-check")
      }
    }
  }

  private def envFlag(name: String): Boolean =
    sys.env.get(name).exists { value =>
      val normalized = value.trim.toLowerCase
      normalized == "1" || normalized == "true" || normalized == "yes"
    }
}

object EngineEvalSavedReportComparisonManualSpec {
  val EnvCompareSavedReports = "ENGINE_EVAL_COMPARE_SAVED_REPORTS"
  val EnvLeftJson = "ENGINE_EVAL_LEFT_JSON"
  val EnvRightJson = "ENGINE_EVAL_RIGHT_JSON"

  private def variantId(slot: Int): MasterServiceOfferVariantId =
    UUID.fromString(f"00000000-0000-0000-0000-00000000${slot}%04x")

  val v1: MasterServiceOfferVariantId = variantId(1)
  val v2: MasterServiceOfferVariantId = variantId(2)
  val v3: MasterServiceOfferVariantId = variantId(3)
  val v4: MasterServiceOfferVariantId = variantId(4)

  private def buildReport(
    queryId: String,
    esIds: List[MasterServiceOfferVariantId],
    qdrantIds: List[MasterServiceOfferVariantId],
    expectedRole: EngineExpectedRole,
    expectedIds: Set[MasterServiceOfferVariantId],
  ): EngineEvalAggregateReport = {
    val es = EngineEvalResult(EngineEvalEngine.Elasticsearch, queryId, esIds)
    val qdrant = EngineEvalResult(EngineEvalEngine.Qdrant, queryId, qdrantIds)
    val queryReport = EngineEvalQueryReport.from(expectedRole, expectedIds, es, qdrant)
    EngineEvalAggregateReport.from(List(queryReport))
  }
}
