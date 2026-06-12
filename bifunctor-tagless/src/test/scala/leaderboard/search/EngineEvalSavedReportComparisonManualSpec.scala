package leaderboard.search

import leaderboard.search.eval.EngineEvalSavedReportComparison
import org.scalatest.wordspec.AnyWordSpec

final class EngineEvalSavedReportComparisonManualSpec extends AnyWordSpec {
  "EngineEvalSavedReportComparison manual comparison" should {
    "compare two saved JSON reports from environment variables" in {
      if (!envFlag(EngineEvalSavedReportComparisonManualSpec.EnvCompareSavedReports)) {
        cancel(
          s"Set ${EngineEvalSavedReportComparisonManualSpec.EnvCompareSavedReports}=true, " +
            s"${EngineEvalSavedReportComparisonManualSpec.EnvLeftJson}=<json>, and " +
            s"${EngineEvalSavedReportComparisonManualSpec.EnvRightJson}=<json> to run the saved-report comparison manual spec"
        )
      } else {
        (
          sys.env.get(EngineEvalSavedReportComparisonManualSpec.EnvLeftJson),
          sys.env.get(EngineEvalSavedReportComparisonManualSpec.EnvRightJson),
        ) match {
          case (Some(leftJson), Some(rightJson)) =>
            EngineEvalSavedReportComparison.compareReportJsonStrings(leftJson, rightJson) match {
              case Left(failure) =>
                fail(s"unexpected saved-report comparison failure: $failure")
              case Right(comparison) =>
                val formatted = EngineEvalSavedReportComparison.formatComparison(comparison)
                println("BEGIN_ENGINE_EVAL_SAVED_REPORT_COMPARISON")
                println(formatted)
                println("END_ENGINE_EVAL_SAVED_REPORT_COMPARISON")
            }
          case _ =>
            cancel(
              s"Set ${EngineEvalSavedReportComparisonManualSpec.EnvCompareSavedReports}=true, " +
                s"${EngineEvalSavedReportComparisonManualSpec.EnvLeftJson}=<json>, and " +
                s"${EngineEvalSavedReportComparisonManualSpec.EnvRightJson}=<json> to run the saved-report comparison manual spec"
            )
        }
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
}
