package leaderboard.search.beautyq.gen2.eval

import io.circe.Json
import leaderboard.search.gen2.eval.*
import org.scalatest.wordspec.AnyWordSpec

final class BeautyQProtectedAcceptanceGateSpec extends AnyWordSpec {
  "BeautyQ protected acceptance gate" should {
    "accept a green protected report only when the visible prerequisite and minima pass" in {
      val fixture = syntheticFixture()
      val result = BeautyQProtectedAcceptanceGate.evaluate(fixture.visible, fixture.protectedRun, fixture.protectedCorpus, fixture.policy)
      assert(result.passed)
      assert(result.protectedCaseCount == 1)
      assert(result.checks.nonEmpty)
    }

    "reject each visible prerequisite independently" in {
      val base = syntheticFixture()
      val noCorrection = base.copy(visible = gateVisible(correctionPassed = false))
      val r1 = BeautyQProtectedAcceptanceGate.evaluate(noCorrection.visible, base.protectedRun, base.protectedCorpus, base.policy)
      assert(!r1.passed)
      assert(r1.checks.exists(check => check.code == "visible-correction-gate" && !check.passed))

      val forbidden = base.copy(visible = gateVisible(forbiddenHitCount = 1))
      val r2 = BeautyQProtectedAcceptanceGate.evaluate(forbidden.visible, base.protectedRun, base.protectedCorpus, base.policy)
      assert(!r2.passed)
      assert(r2.checks.exists(check => check.code == "visible-forbidden-hits" && !check.passed))
    }

    "reject a red protected correction gate" in {
      val fixture = syntheticFixture(protectedCorrectionPassed = false)
      val result = BeautyQProtectedAcceptanceGate.evaluate(fixture.visible, fixture.protectedRun, fixture.protectedCorpus, fixture.policy)
      assert(!result.passed)
      assert(result.checks.exists(check => check.code == "protected-hard-no-harm" && !check.passed))
    }

    "report actual threshold status correctly" in {
      val fixture = syntheticFixture()
      val baseResult = BeautyQProtectedAcceptanceGate.evaluate(fixture.visible, fixture.protectedRun, fixture.protectedCorpus, fixture.policy)
      val thresholdCheck = baseResult.checks.find(_.code == "visible-quality-threshold") match {
        case Some(check) => check
        case None => fail("expected visible-quality-threshold check")
      }
      assert(thresholdCheck.observed == "not_required")
      assert(thresholdCheck.expected == "not_required")
      assert(thresholdCheck.passed)

      val sep = fixture.visible.correctionGate.supplementScoreSeparation
      assert(sep.forbiddenCount == 0)
      assert(BeautyQProtectedAcceptanceGate.actualThresholdStatus(sep) == "not_required")
    }

    "reject case-count inventory failure" in {
      val fixture = syntheticFixture()
      val wrongCount = fixture.copy(protectedCorpus = protectedCorpusWithCaseCount(3))
      val r2 = BeautyQProtectedAcceptanceGate.evaluate(fixture.visible, fixture.protectedRun, wrongCount.protectedCorpus, fixture.policy)
      assert(r2.checks.exists(check => check.code == "protected-case-count" && !check.passed))
    }

    "reject missing observation key" in {
      val fixture = syntheticFixture()
      val policy = decodePolicy(policyJson(metricMinimums = Vector(
        metricObj("missing-key", "variants", "success", 1, "0.500000000000"),
      )))
      val result = BeautyQProtectedAcceptanceGate.evaluate(fixture.visible, fixture.protectedRun, fixture.protectedCorpus, policy)
      assert(result.checks.exists(check => check.observed == "missing-observation-key" && !check.passed))
    }

    "reject missing scope" in {
      val fixture = syntheticFixture()
      val policy = decodePolicy(policyJson(metricMinimums = Vector(
        metricObj("protected-global", "providers", "mrr", 3, "0.250000000000"),
      )))
      val result = BeautyQProtectedAcceptanceGate.evaluate(fixture.visible, fixture.protectedRun, fixture.protectedCorpus, policy)
      assert(result.checks.exists(check => check.observed == "missing-scope" && !check.passed))
    }

    "reject not-applicable metric" in {
      val fixture = syntheticFixture(acceptableIds = Vector.empty)
      val policy = decodePolicy(policyJson(metricMinimums = Vector(
        metricObj("protected-global", "variants", "success", 1, "0.500000000000"),
      )))
      val result = BeautyQProtectedAcceptanceGate.evaluate(fixture.visible, fixture.protectedRun, fixture.protectedCorpus, policy)
      assert(result.checks.exists(check => check.observed == "not-applicable" && !check.passed))
    }

    "reject below-threshold metric" in {
      val fixture = syntheticFixture()
      val policy = decodePolicy(policyJson(metricMinimums = Vector(
        metricObj("protected-global", "variants", "success", 1, "0.000000000000"),
        metricObj("protected-global", "providers", "mrr", 1, "0.750000000000"),
      )))
      val result = BeautyQProtectedAcceptanceGate.evaluate(fixture.visible, fixture.protectedRun, fixture.protectedCorpus, policy)
      assert(result.checks.exists(check => check.code.startsWith("metric-protected-global-providers/mrr/1") && check.observed == "missing-scope"))
    }

    "accept exact equality and above threshold" in {
      val fixture = syntheticFixture()
      val policy = decodePolicy(policyJson(metricMinimums = Vector(
        metricObj("protected-global", "variants", "success", 1, "0.000000000000"),
        metricObj("protected-global", "variants", "mrr", 1, "0.000000000000"),
      )))
      val result = BeautyQProtectedAcceptanceGate.evaluate(fixture.visible, fixture.protectedRun, fixture.protectedCorpus, policy)
      val metricChecks = result.checks.filter(_.code.startsWith("metric-"))
      assert(metricChecks.forall(_.passed))
    }

    "preserve metric check order from policy declaration order" in {
      val fixture = syntheticFixture()
      val policy = decodePolicy(policyJson(metricMinimums = Vector(
        metricObj("protected-global", "variants", "mrr", 3, "0.250000000000"),
        metricObj("protected-global", "variants", "success", 1, "0.500000000000"),
      )))
      val result = BeautyQProtectedAcceptanceGate.evaluate(fixture.visible, fixture.protectedRun, fixture.protectedCorpus, policy)
      val metricChecks = result.checks.filter(_.code.startsWith("metric-"))
      assert(metricChecks.map(_.code) == Vector("metric-protected-global-variants/mrr/3", "metric-protected-global-variants/success/1"))
    }

    "own exact policy/corpus/report identities in the result" in {
      val fixture = syntheticFixture()
      val result = BeautyQProtectedAcceptanceGate.evaluate(fixture.visible, fixture.protectedRun, fixture.protectedCorpus, fixture.policy)
      assert(result.evaluationPolicyVersion == fixture.policy.evaluationPolicyVersion)
      assert(result.protectedAcceptancePolicyVersion == fixture.policy.protectedAcceptancePolicyVersion)
      assert(result.protectedCaseCount == fixture.protectedCorpus.caseCount)
    }

    "exclude protected sentinels from gate JSON" in {
      val fixture = syntheticFixture()
      val result = BeautyQProtectedAcceptanceGate.evaluate(fixture.visible, fixture.protectedRun, fixture.protectedCorpus, fixture.policy)
      val json = result.toJson
      def strings(value: Json): Vector[String] = value.fold(
        jsonNull = Vector.empty, jsonBoolean = _ => Vector.empty, jsonNumber = _ => Vector.empty,
        jsonString = text => Vector(text),
        jsonArray = values => values.toVector.flatMap(strings),
        jsonObject = values => values.values.toVector.flatMap(strings),
      )
      val all = strings(json)
      assert(!all.contains("protected-case"))
    }
  }

  private final case class Fixture(
    visible: BeautyQMeasuredEvaluationResult,
    protectedRun: BeautyQMeasuredEvaluationResult,
    protectedCorpus: BeautyQProtectedEvaluationCorpus,
    policy: BeautyQProtectedAcceptancePolicy,
  )

  private def syntheticFixture(
    acceptableIds: Vector[EvaluationResultId] = Vector(rid("result-a")),
    protectedCorrectionPassed: Boolean = true,
  ): Fixture = {
    val caseId = cid("protected-case")
    val surface = BeautyQEvaluationPolicy.Variants
    val smokeSlice = sid("smoke")
    val allSlices = Vector(smokeSlice)
    val noResults: Vector[EvaluationResultId] = Vector.empty
    val noGains: Vector[GradedGain] = Vector.empty
    val judgments = RankingJudgments.from(
      JudgmentMode.Partial, acceptableIds, noResults, noResults, noGains,
    ) match {
      case Right(value) => value
      case Left(error) => fail(error.toString)
    }
    val ranking = RankingEvaluationInput.from(
      caseId, EvaluationPartition.ProtectedHoldout, surface, allSlices, judgments,
      acceptableIds, BeautyQEvaluationPolicy.cutoffs,
    ) match {
      case Right(value) => RankingEvaluator.evaluate(value)
      case Left(error) => fail(error.toString)
    }
    val provenance = Vector(
      "evaluation-policy-version" -> BeautyQEvaluationPolicy.CurrentVersion,
      "metric-schema-version" -> RankingEvaluator.MetricSchemaVersion,
    ).foldLeft[Vector[ProvenanceComponent]](Vector.empty) { case (done, (idText, value)) =>
      val id = EvaluationProvenanceId.from(idText) match {
        case Right(actual) => actual
        case Left(error) => fail(error.toString)
      }
      ProvenanceComponent.from(id, value) match {
        case Right(actual) => done :+ actual
        case Left(error) => fail(error.toString)
      }
    }
    val reportInput = EvaluationReportCaseInput.from(
      caseId, EvaluationPartition.ProtectedHoldout, JudgmentMode.Partial,
      Some(allSlices), Vector(surface -> ranking),
    ) match {
      case Right(value) => value
      case Left(error) => fail(error.toString)
    }
    val report = EvaluationReportBuilder.build(provenance, Vector(reportInput))
    val corpusCase = new CorpusCase(
      caseId, EvaluationPartition.ProtectedHoldout, JudgmentMode.Partial,
      "protected query", "en", allSlices, "protected", Vector.empty,
      judgments, judgments, judgments,
    )
    val corpus = new BeautyQEvaluationCorpus(
      "beautyq-evaluation-corpus-v2", "protected", "test", 1,
      UserLocation.from("test", 53.55, 10.0),
      Vector(corpusCase),
    )
    val protectedCorpusVal = new BeautyQProtectedEvaluationCorpus(corpus, Vector(smokeSlice -> 1))
    val policy = decodePolicy(policyJson())
    val scoreSeparation = BeautyQSupplementScoreSeparation.from(Vector.empty)
    val checkCodes = Vector(
      "required-full-search", "complete-warmup", "complete-measured-passes",
      "deterministic-measured-rankings", "no-request-degradation",
      "no-public-identity-duplicates", "no-forbidden-hits",
      "baseline-prefix-preserved", "baseline-owned-components-preserved",
      "append-budget-preserved",
    )
    val passedChecks = checkCodes.map(code => BeautyQEvaluationCorrectionCheck.create(code, true, "true", "true"))
    val failedChecks = checkCodes.map(code => BeautyQEvaluationCorrectionCheck.create(code, false, "false", "true"))
    val finalChecks = if (protectedCorrectionPassed) passedChecks else failedChecks
    val protectedRun = BeautyQMeasuredEvaluationResult.create(
      report,
      EvaluationReport.encodeDetailed(report),
      EvaluationReport.encodeProtected(report),
      Json.obj(), Json.obj(),
      BeautyQEvaluationCorrectionGateResult.create(finalChecks, 1, 1, 3, 0, 0, 0, 0, 0, 0, scoreSeparation),
      1, 3, BeautyQEvaluationPolicy.CurrentVersion,
    )
    val visible = gateVisible()
    Fixture(visible, protectedRun, protectedCorpusVal, policy)
  }

  private def gateVisible(
    correctionPassed: Boolean = true,
    forbiddenHitCount: Int = 0,
  ): BeautyQMeasuredEvaluationResult = {
    val checkCodes = Vector(
      "required-full-search", "complete-warmup", "complete-measured-passes",
      "deterministic-measured-rankings", "no-request-degradation",
      "no-public-identity-duplicates", "no-forbidden-hits",
      "baseline-prefix-preserved", "baseline-owned-components-preserved",
      "append-budget-preserved",
    )
    val passedChecks = checkCodes.map(code => BeautyQEvaluationCorrectionCheck.create(code, correctionPassed, if (correctionPassed) "true" else "false", "true"))
    val scoreSep = if (forbiddenHitCount > 0)
      new BeautyQSupplementScoreSeparation(forbiddenHitCount, 0, Some(BigDecimal("0.10")), Some(BigDecimal("0.10")), None, None, None)
    else BeautyQSupplementScoreSeparation.from(Vector.empty)
    val caseId = cid("visible-case")
    val rid1 = rid("result-a")
    val judgments = RankingJudgments.from(JudgmentMode.Partial, Vector(rid1), Vector.empty, Vector.empty, Vector.empty) match {
      case Right(value) => value
      case Left(error) => fail(error.toString)
    }
    val provenance = Vector(
      "evaluation-policy-version" -> BeautyQEvaluationPolicy.CurrentVersion,
      "metric-schema-version" -> RankingEvaluator.MetricSchemaVersion,
    ).foldLeft[Vector[ProvenanceComponent]](Vector.empty) { case (done, (idText, value)) =>
      val id = EvaluationProvenanceId.from(idText) match {
        case Right(actual) => actual
        case Left(error) => fail(error.toString)
      }
      ProvenanceComponent.from(id, value) match {
        case Right(actual) => done :+ actual
        case Left(error) => fail(error.toString)
      }
    }
    val ranking = RankingEvaluationInput.from(
      caseId, EvaluationPartition.Regression, BeautyQEvaluationPolicy.Variants,
      Vector.empty, judgments, Vector(rid1), BeautyQEvaluationPolicy.cutoffs,
    ) match {
      case Right(value) => RankingEvaluator.evaluate(value)
      case Left(error) => fail(error.toString)
    }
    val reportInput = EvaluationReportCaseInput.from(
      caseId, EvaluationPartition.Regression, JudgmentMode.Partial, None,
      Vector(BeautyQEvaluationPolicy.Variants -> ranking),
    ) match {
      case Right(value) => value
      case Left(error) => fail(error.toString)
    }
    val report = EvaluationReportBuilder.build(provenance, Vector(reportInput))
    BeautyQMeasuredEvaluationResult.create(
      report,
      EvaluationReport.encodeDetailed(report),
      EvaluationReport.encodeProtected(report),
      Json.obj(), Json.obj(),
      BeautyQEvaluationCorrectionGateResult.create(passedChecks, 1, 1, 1, 0, forbiddenHitCount, 0, 0, 0, 0, scoreSep),
      1, 3, BeautyQEvaluationPolicy.CurrentVersion,
    )
  }

  private def protectedCorpusWithCaseCount(@scala.annotation.nowarn count: Int): BeautyQProtectedEvaluationCorpus = {
    val resultId = rid("result-a")
    val judgments = RankingJudgments.from(JudgmentMode.Partial, Vector(resultId), Vector.empty, Vector.empty, Vector.empty) match {
      case Right(value) => value
      case Left(error) => fail(error.toString)
    }
    val smokeSlice = sid("smoke")
    val cases = (1 to count).map(i => new CorpusCase(
      cid(s"protected-case-$i"), EvaluationPartition.ProtectedHoldout, JudgmentMode.Partial,
      s"protected query $i", "en", Vector(smokeSlice), "protected", Vector.empty,
      judgments, judgments, judgments,
    )).toVector
    val corpus = new BeautyQEvaluationCorpus("beautyq-evaluation-corpus-v2", "protected", "test", 1,
      UserLocation.from("test", 53.55, 10.0), cases)
    val smokeSliceId = sid("smoke")
    new BeautyQProtectedEvaluationCorpus(corpus, Vector(smokeSliceId -> count))
  }

  private def decodePolicy(json: Json): BeautyQProtectedAcceptancePolicy =
    BeautyQProtectedAcceptancePolicy.fromJson(json) match {
      case Right(value) => value
      case Left(error) => fail(error.toString)
    }

  private def policyJson(metricMinimums: Vector[Json] = Vector(
    metricObj("protected-global", "variants", "success", 1, "0.500000000000"),
    metricObj("protected-global", "variants", "mrr", 3, "0.250000000000"),
  )): Json = Json.obj(
    "schemaVersion" -> Json.fromString(BeautyQProtectedAcceptancePolicy.CurrentSchemaVersion),
    "evaluationPolicyVersion" -> Json.fromString(BeautyQEvaluationPolicy.CurrentVersion),
    "protectedAcceptancePolicyVersion" -> Json.fromString("protected-policy-v1"),
    "expectedCaseCount" -> Json.fromInt(1),
    "requiredSliceMinimums" -> Json.arr(Json.obj("sliceId" -> Json.fromString("smoke"), "minimumCaseCount" -> Json.fromInt(1))),
    "requiredMetricMinimums" -> Json.fromValues(metricMinimums),
  )

  private def metricObj(observationKey: String, surface: String, metric: String, cutoff: Int, minimum: String): Json = Json.obj(
    "observationKey" -> Json.fromString(observationKey),
    "surface" -> Json.fromString(surface),
    "metric" -> Json.fromString(metric),
    "cutoff" -> Json.fromInt(cutoff),
    "minimum" -> Json.fromString(minimum),
  )

  private def cid(id: String): EvaluationCaseId = EvaluationCaseId.from(id) match {
    case Right(value) => value
    case Left(error) => fail(error.toString)
  }
  private def rid(id: String): EvaluationResultId = EvaluationResultId.from(id) match {
    case Right(value) => value
    case Left(error) => fail(error.toString)
  }
  private def sid(id: String): EvaluationSliceId = EvaluationSliceId.from(id) match {
    case Right(value) => value
    case Left(error) => fail(error.toString)
  }
}
