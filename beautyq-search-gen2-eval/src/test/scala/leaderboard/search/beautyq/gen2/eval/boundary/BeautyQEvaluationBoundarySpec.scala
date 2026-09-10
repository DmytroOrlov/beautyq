package leaderboard.search.beautyq.gen2.eval.boundary

import org.scalatest.wordspec.AnyWordSpec

final class BeautyQEvaluationBoundarySpec extends AnyWordSpec {
  "BeautyQ measured evaluation" should {
    "keep report and correction-gate construction outside their companions closed" in {
      assertDoesNotCompile(
        """new leaderboard.search.beautyq.gen2.eval.BeautyQMeasuredEvaluationResult(
          |  ???, ???, ???, ???, ???, ???, 89, 267, ???,
          |)""".stripMargin
      )
      assertDoesNotCompile(
        """new leaderboard.search.beautyq.gen2.eval.BeautyQEvaluationCorrectionGateResult(
          |  Vector.empty, 89, 89, 267, 0, 0, 0, 0, 0, 0,
          |)""".stripMargin
      )
      assertDoesNotCompile(
        """new leaderboard.search.beautyq.gen2.eval.BeautyQEvaluationCorrectionCheck(
          |  "check", true, "0", "0",
          |)""".stripMargin
      )
      assertDoesNotCompile(
        """final class Forged(
          |  report: leaderboard.search.gen2.eval.EvaluationReport,
          |  json: io.circe.Json,
          |  gate: leaderboard.search.beautyq.gen2.eval.BeautyQEvaluationCorrectionGateResult,
          |) extends leaderboard.search.beautyq.gen2.eval.BeautyQMeasuredEvaluationResult(
          |  report, json, json, json, json, gate, 89, 267, "policy",
          |)""".stripMargin
      )
    }
  }
}
