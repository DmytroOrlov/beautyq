package leaderboard.search.gen2.eval

import org.scalatest.wordspec.AnyWordSpec

final class EvaluationModelSpec extends AnyWordSpec {

  private def resultId(raw: String): EvaluationResultId =
    EvaluationResultId.from(raw).getOrElse(fail(s"invalid result id: $raw"))

  "EvaluationCaseId" should {
    "reject blank" in {
      assert(EvaluationCaseId.from("").isLeft)
    }
    "reject surrounding whitespace" in {
      assert(EvaluationCaseId.from(" abc").isLeft)
    }
    "accept valid lowercase" in {
      assert(EvaluationCaseId.from("q_nails_001").isRight)
    }
    "reject uppercase" in {
      assert(EvaluationCaseId.from("ABC_def").isLeft)
    }
    "reject invalid characters" in {
      assert(EvaluationCaseId.from("hello world").isLeft)
    }
  }

  "EvaluationSliceId" should {
    "accept valid id" in {
      assert(EvaluationSliceId.from("direct").isRight)
    }
    "reject blank" in {
      assert(EvaluationSliceId.from("").isLeft)
    }
  }

  "EvaluationSurfaceId" should {
    "accept valid id" in {
      assert(EvaluationSurfaceId.from("variants").isRight)
    }
    "reject blank" in {
      assert(EvaluationSurfaceId.from("").isLeft)
    }
  }

  "EvaluationCutoff" should {
    "accept positive value" in {
      val r = EvaluationCutoff.from(1)
      assert(r.isRight)
      assert(r.getOrElse(fail("expected Right")).value == 1)
    }
    "reject zero" in {
      assert(EvaluationCutoff.from(0).isLeft)
    }
    "reject negative" in {
      assert(EvaluationCutoff.from(-1).isLeft)
    }
  }

  "RelevanceGain" should {
    "accept positive value" in {
      val r = RelevanceGain.from(2)
      assert(r.isRight)
      assert(r.getOrElse(fail("expected Right")).value == 2)
    }
    "reject zero" in {
      assert(RelevanceGain.from(0).isLeft)
    }
    "reject negative" in {
      assert(RelevanceGain.from(-1).isLeft)
    }
  }

  "EvaluationCutoffs" should {
    "accept unique increasing values" in {
      val r = EvaluationCutoffs.from(Vector(1, 3, 5, 10))
      assert(r.isRight)
      val c = r.getOrElse(fail("expected Right"))
      assert(c.values.map(_.value) == Vector(1, 3, 5, 10))
    }
    "reject empty" in {
      assert(EvaluationCutoffs.from(Vector.empty).isLeft)
    }
    "reject non-unique" in {
      assert(EvaluationCutoffs.from(Vector(1, 1, 5)).isLeft)
    }
    "reject non-increasing" in {
      assert(EvaluationCutoffs.from(Vector(5, 1, 3)).isLeft)
    }
  }

  "RankingJudgments" should {
    "create valid exhaustive judgments" in {
      val acceptable = Vector(resultId("doc-a"), resultId("doc-b"))
      val result = RankingJudgments.from(JudgmentMode.Exhaustive, acceptable, Vector.empty, Vector.empty, Vector.empty)
      assert(result.isRight)
    }
    "reject duplicate acceptableIds" in {
      val result = RankingJudgments.from(JudgmentMode.Exhaustive, Vector(resultId("doc-a"), resultId("doc-a")), Vector.empty, Vector.empty, Vector.empty)
      assert(result.isLeft)
    }
    "reject duplicate forbiddenIds" in {
      val result = RankingJudgments.from(JudgmentMode.Exhaustive, Vector(resultId("doc-a")), Vector(resultId("doc-x"), resultId("doc-x")), Vector.empty, Vector.empty)
      assert(result.isLeft)
    }
    "reject duplicate neutralIds" in {
      val result = RankingJudgments.from(JudgmentMode.Exhaustive, Vector(resultId("doc-a")), Vector.empty, Vector(resultId("doc-x"), resultId("doc-x")), Vector.empty)
      assert(result.isLeft)
    }
    "reject acceptable/forbidden intersection" in {
      val result = RankingJudgments.from(JudgmentMode.Exhaustive, Vector(resultId("doc-a")), Vector(resultId("doc-a")), Vector.empty, Vector.empty)
      assert(result.isLeft)
    }
    "reject acceptable/neutral intersection" in {
      val result = RankingJudgments.from(JudgmentMode.Exhaustive, Vector(resultId("doc-a")), Vector.empty, Vector(resultId("doc-a")), Vector.empty)
      assert(result.isLeft)
    }
    "reject forbidden/neutral intersection" in {
      val result = RankingJudgments.from(JudgmentMode.Exhaustive, Vector(resultId("doc-a")), Vector(resultId("doc-b")), Vector(resultId("doc-a")), Vector.empty)
      assert(result.isLeft)
    }
    "reject graded gain for non-acceptable ID" in {
      val gain = RelevanceGain.from(2).getOrElse(fail("expected gain"))
      val graded = Vector(GradedGain.from(resultId("doc-x"), gain))
      val result = RankingJudgments.from(JudgmentMode.Exhaustive, Vector(resultId("doc-a")), Vector.empty, Vector.empty, graded)
      assert(result.isLeft)
    }
    "reject duplicate graded IDs" in {
      val gain1 = RelevanceGain.from(1).getOrElse(fail("expected gain"))
      val gain2 = RelevanceGain.from(2).getOrElse(fail("expected gain"))
      val graded = Vector(GradedGain.from(resultId("doc-a"), gain1), GradedGain.from(resultId("doc-a"), gain2))
      val result = RankingJudgments.from(JudgmentMode.Exhaustive, Vector(resultId("doc-a")), Vector.empty, Vector.empty, graded)
      assert(result.isLeft)
    }
    "reject non-positive gain" in {
      val result = RelevanceGain.from(0)
      assert(result.isLeft)
    }
  }

  "JudgmentMode" should {
    "have correct stable codes" in {
      assert(JudgmentMode.Exhaustive.stableCode == "exhaustive")
      assert(JudgmentMode.Partial.stableCode == "partial")
    }
  }

  "EvaluationPartition" should {
    "have correct stable codes" in {
      assert(EvaluationPartition.Development.stableCode == "development")
      assert(EvaluationPartition.Regression.stableCode == "regression")
      assert(EvaluationPartition.ProtectedHoldout.stableCode == "protected-holdout")
    }
  }

  "JudgedPoolFingerprint" should {
    "produce 64-char lowercase hex" in {
      val acceptable = Vector(resultId("doc-a"), resultId("doc-b"))
      val forbidden = Vector(resultId("doc-c"))
      val neutral = Vector(resultId("doc-d"))
      val fp = JudgedPoolFingerprint.compute(acceptable, forbidden, neutral, Vector.empty)
      assert(fp.value.length == 64)
      assert(fp.value.matches("^[0-9a-f]{64}$"))
    }
    "be deterministic" in {
      val acceptable = Vector(resultId("doc-a"), resultId("doc-b"))
      val fp1 = JudgedPoolFingerprint.compute(acceptable, Vector.empty, Vector.empty, Vector.empty)
      val fp2 = JudgedPoolFingerprint.compute(acceptable, Vector.empty, Vector.empty, Vector.empty)
      assert(fp1.value == fp2.value)
    }
    "be independent of input order" in {
      val fp1 = JudgedPoolFingerprint.compute(Vector(resultId("doc-a"), resultId("doc-b")), Vector.empty, Vector.empty, Vector.empty)
      val fp2 = JudgedPoolFingerprint.compute(Vector(resultId("doc-b"), resultId("doc-a")), Vector.empty, Vector.empty, Vector.empty)
      assert(fp1.value == fp2.value)
    }
  }
}
