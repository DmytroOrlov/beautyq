package leaderboard.search.beautyq.gen2.eval

import io.circe.Json
import leaderboard.search.gen2.eval.*
import org.scalatest.wordspec.AnyWordSpec

final class BeautyQAcceptedEvaluationBaselineSpec extends AnyWordSpec {
  "BeautyQ accepted baseline" should {
    "set top-level corpusFingerprint to the protected fingerprint" in {
      val fixture = greenFixture()
      val baseline = fixture.baseline
      assert(baseline.corpusFingerprint == fixture.protectedCorpus.corpusFingerprint)
      assert(baseline.corpusFingerprint == "a" * 64)
    }

    "keep the visible fingerprint only in provenance" in {
      val fixture = greenFixture()
      val baseline = fixture.baseline
      val visibleFingerprint = baseline.provenanceComponents.find(pc =>
        pc.id.value == "visible-corpus-fingerprint" && pc.value == fixture.visibleCorpusFingerprint
      )
      assert(visibleFingerprint.nonEmpty)
      assert(baseline.corpusFingerprint != fixture.visibleCorpusFingerprint)
    }

    "own reportDigest as the protected report digest" in {
      val fixture = greenFixture()
      val baseline = fixture.baseline
      assert(baseline.reportDigest == fixture.protectedRun.protectedReportDigest)
    }

    "use exact distinct policy observation keys for aggregate entries" in {
      val fixture = greenFixture()
      val baseline = fixture.baseline
      val keys = baseline.aggregateObservations.map(_._1)
      val expectedKeys = fixture.policy.requiredMetricMinimums.map(_.observationKey).distinct
      assert(keys == expectedKeys)
    }

    "produce one aggregate entry when multiple metric scopes share one observation key" in {
      val fixture = greenFixture()
      val baseline = fixture.baseline
      val protectedGlobalEntries = baseline.aggregateObservations.filter(_._1 == "protected-global")
      assert(protectedGlobalEntries.size == 1)
    }

    "reject red acceptance" in {
      val fixture = greenFixture()
      val redAcceptance = BeautyQProtectedAcceptanceResult.create(
        passed = false,
        fixture.policy.evaluationPolicyVersion,
        fixture.policy.protectedAcceptancePolicyVersion,
        fixture.policy.fingerprint,
        fixture.protectedCorpus.corpusFingerprint,
        fixture.protectedCorpus.caseCount,
        fixture.protectedRun.protectedReportDigest,
        Vector.empty,
      )
      val result = BeautyQAcceptedEvaluationBaseline.fromAcceptedProtectedRun(
        fixture.visible, fixture.protectedRun, fixture.protectedCorpus, fixture.policy, redAcceptance,
      )
      assert(result == Left(BeautyQAcceptedEvaluationBaselineError.GateNotGreen))
    }

    "reject red protected correction gate directly" in {
      val fixture = greenFixture()
      val redCorrectionRun = measuredResult(fixture.report, correctionPassed = false)
      val result = BeautyQAcceptedEvaluationBaseline.fromAcceptedProtectedRun(
        fixture.visible, redCorrectionRun, fixture.protectedCorpus, fixture.policy, fixture.acceptance,
      )
      assert(result == Left(BeautyQAcceptedEvaluationBaselineError.GateNotGreen))
    }

    "reject mismatched evaluation-policy version" in {
      val fixture = greenFixture()
      val wrongAcceptance = BeautyQProtectedAcceptanceResult.create(
        passed = true, "wrong-version",
        fixture.policy.protectedAcceptancePolicyVersion,
        fixture.policy.fingerprint,
        fixture.protectedCorpus.corpusFingerprint,
        fixture.protectedCorpus.caseCount,
        fixture.protectedRun.protectedReportDigest,
        Vector.empty,
      )
      val result = BeautyQAcceptedEvaluationBaseline.fromAcceptedProtectedRun(
        fixture.visible, fixture.protectedRun, fixture.protectedCorpus, fixture.policy, wrongAcceptance,
      )
      result match {
        case Left(BeautyQAcceptedEvaluationBaselineError.InvalidBaseline(msg)) => assert(msg.contains("evaluation policy"))
        case other => fail(s"expected evaluation policy version rejection, got $other")
      }
    }

    "reject mismatched protected-policy version" in {
      val fixture = greenFixture()
      val wrongAcceptance = BeautyQProtectedAcceptanceResult.create(
        passed = true,
        fixture.policy.evaluationPolicyVersion,
        "wrong-protected-policy",
        fixture.policy.fingerprint,
        fixture.protectedCorpus.corpusFingerprint,
        fixture.protectedCorpus.caseCount,
        fixture.protectedRun.protectedReportDigest,
        Vector.empty,
      )
      val result = BeautyQAcceptedEvaluationBaseline.fromAcceptedProtectedRun(
        fixture.visible, fixture.protectedRun, fixture.protectedCorpus, fixture.policy, wrongAcceptance,
      )
      result match {
        case Left(BeautyQAcceptedEvaluationBaselineError.InvalidBaseline(msg)) => assert(msg.contains("protected policy"))
        case other => fail(s"expected protected policy version rejection, got $other")
      }
    }

    "reject mismatched policy fingerprint" in {
      val fixture = greenFixture()
      val wrongAcceptance = BeautyQProtectedAcceptanceResult.create(
        passed = true,
        fixture.policy.evaluationPolicyVersion,
        fixture.policy.protectedAcceptancePolicyVersion,
        "a" * 64,
        fixture.protectedCorpus.corpusFingerprint,
        fixture.protectedCorpus.caseCount,
        fixture.protectedRun.protectedReportDigest,
        Vector.empty,
      )
      val result = BeautyQAcceptedEvaluationBaseline.fromAcceptedProtectedRun(
        fixture.visible, fixture.protectedRun, fixture.protectedCorpus, fixture.policy, wrongAcceptance,
      )
      result match {
        case Left(BeautyQAcceptedEvaluationBaselineError.InvalidBaseline(msg)) => assert(msg.contains("policy fingerprint"))
        case other => fail(s"expected policy fingerprint rejection, got $other")
      }
    }

    "reject mismatched protected corpus fingerprint" in {
      val fixture = greenFixture()
      val wrongAcceptance = BeautyQProtectedAcceptanceResult.create(
        passed = true,
        fixture.policy.evaluationPolicyVersion,
        fixture.policy.protectedAcceptancePolicyVersion,
        fixture.policy.fingerprint,
        "b" * 64,
        fixture.protectedCorpus.caseCount,
        fixture.protectedRun.protectedReportDigest,
        Vector.empty,
      )
      val result = BeautyQAcceptedEvaluationBaseline.fromAcceptedProtectedRun(
        fixture.visible, fixture.protectedRun, fixture.protectedCorpus, fixture.policy, wrongAcceptance,
      )
      result match {
        case Left(BeautyQAcceptedEvaluationBaselineError.InvalidBaseline(msg)) => assert(msg.contains("protected corpus fingerprint"))
        case other => fail(s"expected protected corpus fingerprint rejection, got $other")
      }
    }

    "reject mismatched protected case count" in {
      val fixture = greenFixture()
      val wrongAcceptance = BeautyQProtectedAcceptanceResult.create(
        passed = true,
        fixture.policy.evaluationPolicyVersion,
        fixture.policy.protectedAcceptancePolicyVersion,
        fixture.policy.fingerprint,
        fixture.protectedCorpus.corpusFingerprint,
        99,
        fixture.protectedRun.protectedReportDigest,
        Vector.empty,
      )
      val result = BeautyQAcceptedEvaluationBaseline.fromAcceptedProtectedRun(
        fixture.visible, fixture.protectedRun, fixture.protectedCorpus, fixture.policy, wrongAcceptance,
      )
      result match {
        case Left(BeautyQAcceptedEvaluationBaselineError.InvalidBaseline(msg)) => assert(msg.contains("protected case count"))
        case other => fail(s"expected case count rejection, got $other")
      }
    }

    "reject mismatched protected report digest" in {
      val fixture = greenFixture()
      val wrongAcceptance = BeautyQProtectedAcceptanceResult.create(
        passed = true,
        fixture.policy.evaluationPolicyVersion,
        fixture.policy.protectedAcceptancePolicyVersion,
        fixture.policy.fingerprint,
        fixture.protectedCorpus.corpusFingerprint,
        fixture.protectedCorpus.caseCount,
        "c" * 64,
        Vector.empty,
      )
      val result = BeautyQAcceptedEvaluationBaseline.fromAcceptedProtectedRun(
        fixture.visible, fixture.protectedRun, fixture.protectedCorpus, fixture.policy, wrongAcceptance,
      )
      result match {
        case Left(BeautyQAcceptedEvaluationBaselineError.InvalidBaseline(msg)) => assert(msg.contains("protected report digest"))
        case other => fail(s"expected report digest rejection, got $other")
      }
    }

    "produce deterministic candidate JSON and digest" in {
      val fixture = greenFixture()
      val json1 = BeautyQAcceptedEvaluationBaseline.encodeCandidate(fixture.baseline)
      val json2 = BeautyQAcceptedEvaluationBaseline.encodeCandidate(fixture.baseline)
      assert(json1 == json2)
      val digest1 = BeautyQAcceptedEvaluationBaseline.candidateDigest(fixture.baseline)
      val digest2 = BeautyQAcceptedEvaluationBaseline.candidateDigest(fixture.baseline)
      assert(digest1 == digest2)
      assert(digest1.matches("[0-9a-f]{64}"))
    }

    "round-trip a validated ordered baseline" in {
      val baseline = genericCodecFixture()
      val decoded = AcceptedBaselineCodec.decode(AcceptedBaselineCodec.encode(baseline)) match {
        case Right(value) => value
        case Left(error) => fail(s"expected valid baseline, got $error")
      }
      assert(decoded == baseline)
    }

    "reject missing, wrong and unknown root or nested fields" in {
      val json = AcceptedBaselineCodec.encode(genericCodecFixture())
      assert(AcceptedBaselineCodec.decode(json.mapObject(_.remove("corpusFingerprint"))).isLeft)
      assert(AcceptedBaselineCodec.decode(json.mapObject(_.add("schemaVersion", Json.fromString("other")))).isLeft)
      assert(AcceptedBaselineCodec.decode(json.mapObject(_.add("reportDigest", Json.fromString("bad")))).isLeft)
      val provenance = json.hcursor.downField("provenance").values match {
        case Some(values) => values
        case None => fail("expected encoded provenance")
      }
      val alteredProvenance = provenance.map { value => value.mapObject(_.add("unexpected", Json.fromString("x"))) }
      val nested = json.mapObject(_.add("provenance", Json.fromValues(alteredProvenance)))
      assert(AcceptedBaselineCodec.decode(nested).isLeft)
    }

    "reject duplicate provenance identities and whitespace versions" in {
      val json = AcceptedBaselineCodec.encode(genericCodecFixture())
      val provenance = json.hcursor.downField("provenance").values match {
        case Some(values) => values
        case None => fail("expected encoded provenance")
      }
      val duplicate = json.mapObject(_.add("provenance", Json.fromValues(provenance ++ provenance)))
      assert(AcceptedBaselineCodec.decode(duplicate).isLeft)
      val whitespace = json.mapObject(_.add("evaluationPolicyVersion", Json.fromString(" commit")))
      assert(AcceptedBaselineCodec.decode(whitespace).isLeft)
    }
  }

  private final case class Fixture(
    visible: BeautyQMeasuredEvaluationResult,
    protectedRun: BeautyQMeasuredEvaluationResult,
    protectedCorpus: BeautyQProtectedEvaluationCorpus,
    policy: BeautyQProtectedAcceptancePolicy,
    acceptance: BeautyQProtectedAcceptanceResult,
    baseline: AcceptedEvaluationBaseline,
    report: EvaluationReport,
    visibleCorpusFingerprint: String,
  )

  private def greenFixture(): Fixture = {
    val caseId = cid("protected-case")
    val resultId = rid("result-a")
    val surface = BeautyQEvaluationPolicy.Variants
    val smokeSlice = sid("smoke")
    val judgments = RankingJudgments.from(JudgmentMode.Partial, Vector(resultId), Vector.empty, Vector.empty, Vector.empty) match {
      case Right(value) => value
      case Left(error) => fail(error.toString)
    }
    val ranking = RankingEvaluationInput.from(
      caseId, EvaluationPartition.ProtectedHoldout, surface, Vector(smokeSlice), judgments,
      Vector(resultId), BeautyQEvaluationPolicy.cutoffs,
    ) match {
      case Right(value) => RankingEvaluator.evaluate(value)
      case Left(error) => fail(error.toString)
    }
    val provenance = Vector(
      "corpus-fingerprint" -> ("a" * 64),
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
      Some(Vector(smokeSlice)), Vector(surface -> ranking),
    ) match {
      case Right(value) => value
      case Left(error) => fail(error.toString)
    }
    val report = EvaluationReportBuilder.build(provenance, Vector(reportInput))
    val corpusCase = new CorpusCase(caseId, EvaluationPartition.ProtectedHoldout, JudgmentMode.Partial,
      "protected query", "en", Vector(smokeSlice), "protected", Vector.empty, judgments, judgments, judgments)
    val corpus = new BeautyQEvaluationCorpus("beautyq-evaluation-corpus-v2", "protected", "test", 1,
      UserLocation.from("test", 53.55, 10.0), Vector(corpusCase), "a" * 64)
    val protectedCorpusVal = new BeautyQProtectedEvaluationCorpus(corpus, Vector(smokeSlice -> 1))
    val policy = decodePolicy(policyJson())
    val protectedRun = measuredResult(report)
    val visibleCaseId = cid("visible-case")
    val visibleJudgments = RankingJudgments.from(JudgmentMode.Partial, Vector(rid("result-b")), Vector.empty, Vector.empty, Vector.empty) match {
      case Right(value) => value
      case Left(error) => fail(error.toString)
    }
    val visibleRanking = RankingEvaluationInput.from(
      visibleCaseId, EvaluationPartition.Regression, BeautyQEvaluationPolicy.Variants, Vector.empty, visibleJudgments,
      Vector(rid("result-b")), BeautyQEvaluationPolicy.cutoffs,
    ) match {
      case Right(value) => RankingEvaluator.evaluate(value)
      case Left(error) => fail(error.toString)
    }
    val visibleProvenance = Vector(
      "corpus-fingerprint" -> ("v" * 64),
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
    val visibleReportInput = EvaluationReportCaseInput.from(
      visibleCaseId, EvaluationPartition.Regression, JudgmentMode.Partial, None,
      Vector(BeautyQEvaluationPolicy.Variants -> visibleRanking),
    ) match {
      case Right(value) => value
      case Left(error) => fail(error.toString)
    }
    val visibleReport = EvaluationReportBuilder.build(visibleProvenance, Vector(visibleReportInput))
    val visibleScoreSep = BeautyQSupplementScoreSeparation.from(Vector.empty)
    val visibleChecks = checkCodes.map(code => BeautyQEvaluationCorrectionCheck.create(code, true, "true", "true"))
    val visible = BeautyQMeasuredEvaluationResult.create(
      visibleReport,
      EvaluationReport.encodeDetailed(visibleReport),
      EvaluationReport.encodeProtected(visibleReport),
      EvaluationReportDigest.compute(EvaluationReport.encodeProtected(visibleReport)),
      EvaluationReportDigest.compute(EvaluationReport.encodeDetailed(visibleReport)),
      Json.obj(), Json.obj(),
      BeautyQEvaluationCorrectionGateResult.create(visibleChecks, 1, 1, 1, 0, 0, 0, 0, 0, 0, visibleScoreSep),
      1, 3, BeautyQEvaluationPolicy.CurrentVersion,
    )
    val acceptance = BeautyQProtectedAcceptanceResult.create(
      passed = true,
      BeautyQEvaluationPolicy.CurrentVersion,
      policy.protectedAcceptancePolicyVersion,
      policy.fingerprint,
      protectedCorpusVal.corpusFingerprint,
      protectedCorpusVal.caseCount,
      protectedRun.protectedReportDigest,
      Vector.empty,
    )
    val baseline = BeautyQAcceptedEvaluationBaseline.fromAcceptedProtectedRun(
      visible, protectedRun, protectedCorpusVal, policy, acceptance,
    ) match {
      case Right(value) => value
      case Left(error) => fail(s"expected accepted baseline, got $error")
    }
    Fixture(visible, protectedRun, protectedCorpusVal, policy, acceptance, baseline, report,
      visibleCorpusFingerprint = "v" * 64)
  }

  private val checkCodes = Vector(
    "required-full-search", "complete-warmup", "complete-measured-passes",
    "deterministic-measured-rankings", "no-request-degradation",
    "no-public-identity-duplicates", "no-forbidden-hits",
    "baseline-prefix-preserved", "baseline-owned-components-preserved",
    "append-budget-preserved",
  )

  private def measuredResult(report: EvaluationReport, correctionPassed: Boolean = true): BeautyQMeasuredEvaluationResult = {
    val passedChecks = checkCodes.map(code => BeautyQEvaluationCorrectionCheck.create(code, correctionPassed, "true", "true"))
    val scoreSep = BeautyQSupplementScoreSeparation.from(Vector.empty)
    BeautyQMeasuredEvaluationResult.create(
      report,
      EvaluationReport.encodeDetailed(report),
      EvaluationReport.encodeProtected(report),
      EvaluationReportDigest.compute(EvaluationReport.encodeProtected(report)),
      EvaluationReportDigest.compute(EvaluationReport.encodeDetailed(report)),
      Json.obj(), Json.obj(),
      BeautyQEvaluationCorrectionGateResult.create(passedChecks, 1, 1, 3, 0, 0, 0, 0, 0, 0, scoreSep),
      1, 3, BeautyQEvaluationPolicy.CurrentVersion,
    )
  }

  private def decodePolicy(json: Json): BeautyQProtectedAcceptancePolicy =
    BeautyQProtectedAcceptancePolicy.fromJson(json) match {
      case Right(value) => value
      case Left(error) => fail(error.toString)
    }

  private def policyJson(): Json = Json.obj(
    "schemaVersion" -> Json.fromString(BeautyQProtectedAcceptancePolicy.CurrentSchemaVersion),
    "evaluationPolicyVersion" -> Json.fromString(BeautyQEvaluationPolicy.CurrentVersion),
    "protectedAcceptancePolicyVersion" -> Json.fromString("protected-policy-v1"),
    "expectedCorpusFingerprint" -> Json.fromString("a" * 64),
    "expectedCaseCount" -> Json.fromInt(1),
    "requiredSliceMinimums" -> Json.arr(Json.obj(
      "sliceId" -> Json.fromString("smoke"), "minimumCaseCount" -> Json.fromInt(1),
    )),
    "requiredMetricMinimums" -> Json.arr(
      Json.obj("observationKey" -> Json.fromString("protected-global"), "surface" -> Json.fromString(BeautyQEvaluationPolicy.Variants.value), "metric" -> Json.fromString("success"), "cutoff" -> Json.fromInt(1), "minimum" -> Json.fromString("0.500000000000")),
      Json.obj("observationKey" -> Json.fromString("protected-global"), "surface" -> Json.fromString(BeautyQEvaluationPolicy.Variants.value), "metric" -> Json.fromString("mrr"), "cutoff" -> Json.fromInt(3), "minimum" -> Json.fromString("0.250000000000")),
    ),
  )

  private def genericCodecFixture(): AcceptedEvaluationBaseline = {
    val id = EvaluationProvenanceId.from("fixture") match {
      case Right(value) => value
      case Left(error) => fail(error)
    }
    val provenance = ProvenanceComponent.from(id, "value") match {
      case Right(value) => value
      case Left(error) => fail(error)
    }
    AcceptedEvaluationBaseline.create(
      "a" * 64, "metric-schema-v1", "evaluation-policy-v1",
      Vector(provenance), "b" * 64, Vector.empty,
    ) match {
      case Right(value) => value
      case Left(error) => fail(error)
    }
  }

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
