package leaderboard.search.gen2.eval

import org.scalatest.wordspec.AnyWordSpec
import scala.math.BigDecimal.RoundingMode

final class RankingEvaluationSpec extends AnyWordSpec {
  "RankingEvaluator metric schema" should {
    "expose one stable version" in {
      assert(RankingEvaluator.MetricSchemaVersion == "search-gen2-ranking-metrics-v1")
    }
  }


  private def resultId(raw: String): EvaluationResultId =
    EvaluationResultId.from(raw).getOrElse(fail(s"invalid result id: $raw"))

  private def caseId(raw: String): EvaluationCaseId =
    EvaluationCaseId.from(raw).getOrElse(fail(s"invalid case id: $raw"))

  private def surfaceId(raw: String): EvaluationSurfaceId =
    EvaluationSurfaceId.from(raw).getOrElse(fail(s"invalid surface id: $raw"))

  private def sliceId(raw: String): EvaluationSliceId =
    EvaluationSliceId.from(raw).getOrElse(fail(s"invalid slice id: $raw"))

  private def evaluate(input: RankingEvaluationInput): RankingEvaluationResult =
    RankingEvaluator.evaluate(input)

  private def makeInput(
    acceptable: Vector[String],
    mode: JudgmentMode,
    rawRanked: Vector[String],
    cutoffs: Vector[Int] = Vector(1, 3, 5, 10),
  ): RankingEvaluationInput = {
    val accIds = acceptable.map(resultId)
    val judgments = RankingJudgments.from(mode, accIds, Vector.empty, Vector.empty, Vector.empty)
      .getOrElse(fail("expected valid judgments"))
    RankingEvaluationInput.from(
      caseId("test_case"),
      EvaluationPartition.Regression,
      surfaceId("variants"),
      Vector(sliceId("direct")),
      judgments,
      rawRanked.map(resultId),
      EvaluationCutoffs.from(cutoffs).getOrElse(fail("invalid cutoffs")),
    ).getOrElse(fail("expected valid input"))
  }

  "Success@K" should {
    "return applicable true when topK contains acceptable" in {
      val input = makeInput(Vector("doc-a", "doc-b"), JudgmentMode.Exhaustive, Vector("doc-a", "doc-c", "doc-d"), Vector(3))
      val result = evaluate(input)
      assert(result.metricRows.size == 1)
      val row = result.metricRows(0)
      row.success match {
        case MetricValue.Applicable(v) =>
          assert(v == BigDecimal("1.000000000000"))
        case _ => fail("expected applicable success")
      }
    }
    "return applicable false when topK contains no acceptable" in {
      val input = makeInput(Vector("doc-a", "doc-b"), JudgmentMode.Exhaustive, Vector("doc-x", "doc-y"), Vector(3))
      val result = evaluate(input)
      val row = result.metricRows(0)
      row.success match {
        case MetricValue.Applicable(v) =>
          assert(v == BigDecimal("0.000000000000"))
        case _ => fail("expected applicable")
      }
    }
    "return not applicable when acceptable set is empty" in {
      val accIds: Vector[EvaluationResultId] = Vector.empty
      val judgments = RankingJudgments.from(JudgmentMode.Exhaustive, accIds, Vector.empty, Vector.empty, Vector.empty)
        .getOrElse(fail("expected valid"))
      val input = RankingEvaluationInput.from(
        caseId("test_case"), EvaluationPartition.Regression, surfaceId("variants"),
        Vector(sliceId("direct")), judgments,
        Vector(resultId("doc-a")),
        EvaluationCutoffs.from(Vector(3)).getOrElse(fail("invalid")),
      ).getOrElse(fail("expected valid input"))
      val result = evaluate(input)
      val row = result.metricRows(0)
      row.success match {
        case MetricValue.NotApplicable(_) => // OK
        case _ => fail("expected not applicable")
      }
    }
  }

  "MRR@K" should {
    "return reciprocal rank" in {
      val input = makeInput(Vector("doc-b"), JudgmentMode.Exhaustive, Vector("doc-a", "doc-b", "doc-c"), Vector(3))
      val result = evaluate(input)
      val row = result.metricRows(0)
      row.mrr match {
        case MetricValue.Applicable(v) =>
          val expected = (BigDecimal(1) / BigDecimal(2)).setScale(12, RoundingMode.HALF_UP)
          assert(v == expected)
        case _ => fail("expected applicable mrr")
      }
    }
    "return zero when no acceptable" in {
      val input = makeInput(Vector("doc-z"), JudgmentMode.Exhaustive, Vector("doc-a", "doc-b"), Vector(3))
      val result = evaluate(input)
      val row = result.metricRows(0)
      row.mrr match {
        case MetricValue.Applicable(v) =>
          assert(v == BigDecimal("0.000000000000"))
        case _ => fail("expected applicable")
      }
    }
  }

  "Precision@K" should {
    "be exact denominator K for exhaustive" in {
      val input = makeInput(Vector("doc-a", "doc-b"), JudgmentMode.Exhaustive, Vector("doc-a", "doc-c", "doc-d", "doc-e", "doc-f"), Vector(5))
      val result = evaluate(input)
      val row = result.metricRows(0)
      row.precision match {
        case MetricValue.Applicable(v) =>
          assert(v == (BigDecimal(1) / BigDecimal(5)).setScale(12, RoundingMode.HALF_UP))
        case _ => fail("expected applicable precision")
      }
    }
    "be not applicable for partial" in {
      val input = makeInput(Vector("doc-a"), JudgmentMode.Partial, Vector("doc-a", "doc-b"), Vector(3))
      val result = evaluate(input)
      val row = result.metricRows(0)
      row.precision match {
        case MetricValue.NotApplicable(_) => // OK
        case _ => fail("expected not applicable for partial precision")
      }
    }
  }

  "Recall@K" should {
    "return ratio" in {
      val input = makeInput(Vector("doc-a", "doc-b", "doc-c"), JudgmentMode.Exhaustive, Vector("doc-a", "doc-x", "doc-b"), Vector(5))
      val result = evaluate(input)
      val row = result.metricRows(0)
      row.recall match {
        case MetricValue.Applicable(v) =>
          assert(v == (BigDecimal(2) / BigDecimal(3)).setScale(12, RoundingMode.HALF_UP))
        case _ => fail("expected applicable recall")
      }
    }
    "be not applicable for partial" in {
      val input = makeInput(Vector("doc-a"), JudgmentMode.Partial, Vector("doc-a", "doc-b"), Vector(3))
      val result = evaluate(input)
      val row = result.metricRows(0)
      row.recall match {
        case MetricValue.NotApplicable(_) => // OK
        case _ => fail("expected not applicable recall for partial")
      }
    }
  }

  "JudgedPrecision@K" should {
    "work for partial when judged IDs exist in topK" in {
      val acceptable = Vector(resultId("doc-a"), resultId("doc-b"))
      val forbidden = Vector(resultId("doc-x"))
      val judgments = RankingJudgments.from(JudgmentMode.Partial, acceptable, forbidden, Vector.empty, Vector.empty)
        .getOrElse(fail("expected valid"))
      val input = RankingEvaluationInput.from(
        caseId("test_case"), EvaluationPartition.Regression, surfaceId("variants"),
        Vector(sliceId("direct")), judgments,
        Vector(resultId("doc-a"), resultId("doc-x"), resultId("doc-y"), resultId("doc-u")),
        EvaluationCutoffs.from(Vector(4)).getOrElse(fail("invalid")),
      ).getOrElse(fail("expected valid input"))
      val result = evaluate(input)
      val row = result.metricRows(0)
      row.judgedPrecision match {
        case MetricValue.Applicable(v) =>
          assert(v == (BigDecimal(1) / BigDecimal(2)).setScale(12, RoundingMode.HALF_UP))
        case _ => fail("expected applicable judged precision")
      }
    }
  }

  "ForbiddenHits" should {
    "report exact forbidden IDs and count" in {
      val acceptable = Vector(resultId("doc-a"))
      val forbidden = Vector(resultId("bad-1"), resultId("bad-2"))
      val judgments = RankingJudgments.from(JudgmentMode.Exhaustive, acceptable, forbidden, Vector.empty, Vector.empty)
        .getOrElse(fail("expected valid"))
      val rawRanked = Vector(resultId("doc-a"), resultId("bad-1"), resultId("bad-2"))
      val input = RankingEvaluationInput.from(
        caseId("test_case"), EvaluationPartition.Regression, surfaceId("variants"),
        Vector(sliceId("direct")), judgments, rawRanked,
        EvaluationCutoffs.from(Vector(5)).getOrElse(fail("invalid")),
      ).getOrElse(fail("expected valid input"))
      val result = evaluate(input)
      val row = result.metricRows(0)
      assert(row.forbiddenHits.count == 2)
      assert(row.forbiddenHits.ids.map(_.value) == Vector("bad-1", "bad-2"))
    }
  }

  "Duplicate handling" should {
    "preserve raw duplicate ranking" in {
      val input = makeInput(Vector("doc-a"), JudgmentMode.Exhaustive, Vector("doc-a", "doc-a", "doc-b", "doc-c"))
      val result = evaluate(input)
      assert(result.rawRanking.map(_.value) == Vector("doc-a", "doc-a", "doc-b", "doc-c"))
    }
    "report duplicates once in encounter order" in {
      val input = makeInput(Vector("doc-a"), JudgmentMode.Exhaustive, Vector("doc-a", "doc-a", "doc-b", "doc-c", "doc-b", "doc-a"))
      val result = evaluate(input)
      assert(result.duplicateIds.map(d => d.id.value -> d.encounterOrder) == Vector("doc-a" -> 2, "doc-b" -> 5))
    }
    "use first-occurrence for metrics" in {
      val input = makeInput(Vector("doc-a", "doc-b"), JudgmentMode.Exhaustive, Vector("doc-a", "doc-a", "doc-b", "doc-c"))
      val result = evaluate(input)
      assert(result.uniqueRanking.map(_.value) == Vector("doc-a", "doc-b", "doc-c"))
    }
    "be structurally invalid when duplicates exist" in {
      val input = makeInput(Vector("doc-a"), JudgmentMode.Exhaustive, Vector("doc-a", "doc-a"))
      val result = evaluate(input)
      assert(!result.structurallyValid)
    }
    "be structurally valid without duplicates" in {
      val input = makeInput(Vector("doc-a"), JudgmentMode.Exhaustive, Vector("doc-a", "doc-b"))
      val result = evaluate(input)
      assert(result.structurallyValid)
    }
  }

  "NDCG@K" should {
    "compute exact simple exhaustive" in {
      val gain = RelevanceGain.from(2).getOrElse(fail("expected gain"))
      val acceptable = Vector(resultId("doc-a"), resultId("doc-b"))
      val graded = Vector(GradedGain.from(resultId("doc-a"), gain))
      val judgments = RankingJudgments.from(JudgmentMode.Exhaustive, acceptable, Vector.empty, Vector.empty, graded)
        .getOrElse(fail("expected valid"))
      val input = RankingEvaluationInput.from(
        caseId("test_case"), EvaluationPartition.Regression, surfaceId("variants"),
        Vector(sliceId("direct")), judgments,
        Vector(resultId("doc-a"), resultId("doc-b")),
        EvaluationCutoffs.from(Vector(2)).getOrElse(fail("invalid")),
      ).getOrElse(fail("expected valid input"))
      val result = evaluate(input)
      val row = result.metricRows(0)
      row.ndcg match {
        case MetricValue.Applicable(v) =>
          assert(v == BigDecimal("1.000000000000"))
        case _ => fail("expected applicable ndcg")
      }
    }

    "use the cutoff-limited ideal ranking for a non-trivial score" in {
      val ids = Vector(resultId("doc-a"), resultId("doc-b"), resultId("doc-c"))
      val gains = Vector(GradedGain.from(ids(0), RelevanceGain.from(3).getOrElse(fail("valid gain"))), GradedGain.from(ids(1), RelevanceGain.from(2).getOrElse(fail("valid gain"))), GradedGain.from(ids(2), RelevanceGain.from(1).getOrElse(fail("valid gain"))))
      val judgments = RankingJudgments.from(JudgmentMode.Exhaustive, ids, Vector.empty, Vector.empty, gains).getOrElse(fail("valid judgments"))
      val input = RankingEvaluationInput.from(caseId("test_case"), EvaluationPartition.Regression, surfaceId("variants"), Vector(sliceId("direct")), judgments, Vector(ids(2), ids(0)), EvaluationCutoffs.from(Vector(2)).getOrElse(fail("valid cutoff"))).getOrElse(fail("valid input"))
      evaluate(input).metricRows match {
        case Vector(row) => row.ndcg match {
          case MetricValue.Applicable(value) => assert(value == BigDecimal("0.678762229460"))
          case other => fail(s"expected exact applicable ndcg, got $other")
        }
        case other => fail(s"expected one metric row, got $other")
      }
    }

    "return one for a perfect top one even when the positive pool is larger" in {
      val judgments = RankingJudgments.from(JudgmentMode.Exhaustive, Vector(resultId("doc-a"), resultId("doc-b")), Vector.empty, Vector.empty, Vector.empty).getOrElse(fail("valid judgments"))
      val input = RankingEvaluationInput.from(caseId("test_case"), EvaluationPartition.Regression, surfaceId("variants"), Vector(sliceId("direct")), judgments, Vector(resultId("doc-a")), EvaluationCutoffs.from(Vector(1)).getOrElse(fail("valid cutoff"))).getOrElse(fail("valid input"))
      evaluate(input).metricRows match {
        case Vector(row) => row.ndcg match {
          case MetricValue.Applicable(value) => assert(value == BigDecimal("1.000000000000"))
          case other => fail(s"expected exact perfect ndcg, got $other")
        }
        case other => fail(s"expected one metric row, got $other")
      }
    }
  }

  "PooledNDCG@K" should {
    "work for partial with all judged topK" in {
      val acceptable = Vector(resultId("doc-a"), resultId("doc-b"), resultId("doc-c"))
      val graded = Vector(
        GradedGain.from(resultId("doc-a"), RelevanceGain.from(3).getOrElse(fail("valid gain"))),
        GradedGain.from(resultId("doc-b"), RelevanceGain.from(2).getOrElse(fail("valid gain"))),
        GradedGain.from(resultId("doc-c"), RelevanceGain.from(1).getOrElse(fail("valid gain"))),
      )
      val judgments = RankingJudgments.from(JudgmentMode.Partial, acceptable, Vector.empty, Vector.empty, graded)
        .getOrElse(fail("expected valid"))
      val input = RankingEvaluationInput.from(
        caseId("test_case"), EvaluationPartition.Regression, surfaceId("variants"),
        Vector(sliceId("direct")), judgments,
        Vector(resultId("doc-c"), resultId("doc-a")),
        EvaluationCutoffs.from(Vector(2)).getOrElse(fail("invalid")),
      ).getOrElse(fail("expected valid input"))
      val result = evaluate(input)
      val row = result.metricRows(0)
      row.pooledNdcg match {
        case MetricValue.Applicable(v) =>
          assert(v == BigDecimal("0.678762229460"))
          row.pooledFingerprint match {
            case Some(fingerprint) => assert(fingerprint.value.matches("^[0-9a-f]{64}$"))
            case None => fail("expected pooled fingerprint")
          }
        case _ => fail("expected applicable pooled ndcg")
      }
    }
    "reject when topK contains unjudged" in {
      val acceptable = Vector(resultId("doc-a"))
      val judgments = RankingJudgments.from(JudgmentMode.Partial, acceptable, Vector.empty, Vector.empty, Vector.empty)
        .getOrElse(fail("expected valid"))
      val input = RankingEvaluationInput.from(
        caseId("test_case"), EvaluationPartition.Regression, surfaceId("variants"),
        Vector(sliceId("direct")), judgments,
        Vector(resultId("doc-a"), resultId("doc-u")),
        EvaluationCutoffs.from(Vector(2)).getOrElse(fail("invalid")),
      ).getOrElse(fail("expected valid input"))
      val result = evaluate(input)
      val row = result.metricRows(0)
      row.pooledNdcg match {
        case MetricValue.NotApplicable(NotApplicableReason.UnjudgedTopK) =>
        case _ => fail("expected not applicable pooledNDCG for unjudged topK")
      }
      assert(row.unjudgedRate == BigDecimal("0.500000000000"))
    }
  }
}
