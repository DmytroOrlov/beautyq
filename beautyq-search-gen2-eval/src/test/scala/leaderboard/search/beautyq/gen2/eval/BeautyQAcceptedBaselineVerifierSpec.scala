package leaderboard.search.beautyq.gen2.eval

import io.circe.Json
import leaderboard.search.gen2.eval.{AcceptedBaselineCodec, AcceptedEvaluationBaseline, EvaluationProvenanceId, ProvenanceComponent}
import org.scalatest.wordspec.AnyWordSpec

final class BeautyQAcceptedBaselineVerifierSpec extends AnyWordSpec {
  "BeautyQAcceptedBaselineVerifier" should {
    "encode a working-tree candidate as a red compatibility result" in {
      val policy = policyFixture()
      val baseline = fixture("commit-a", "system-property", policy.fingerprint)
      val workingTree = fixture("working-tree", "system-property", policy.fingerprint)
      val result = BeautyQAcceptedBaselineVerifier.verify(workingTree, baseline, policy)
      result match {
        case Right(value) =>
          assert(!value.passed)
          assert(value.checks.exists(check => check.code == "candidate-application-revision" && !check.passed))
        case Left(error) => fail(s"expected encoded compatibility result, got ${error.code}")
      }
    }

    "encode a stable provenance mismatch as a failed stable check" in {
      val policy = policyFixture()
      val baseline = fixture("commit-a", "system-property", policy.fingerprint)
      val candidate = fixture("commit-b", "system-property", policy.fingerprint, sourceContent = "e" * 64)
      val result = BeautyQAcceptedBaselineVerifier.verify(candidate, baseline, policy)
      result match {
        case Right(value) =>
          assert(!value.passed)
          value.checks.find(_.code == "stable-provenance-source-content-fingerprint") match {
            case Some(check) =>
              assert(!check.passed)
              assert(check.observed == "e" * 64)
              assert(check.expected == "d" * 64)
            case None => fail("expected source-content provenance mismatch")
          }
        case Left(error) => fail(s"expected encoded compatibility result, got ${error.code}")
      }
    }

    "accept equal manifests and emit normalized zero deltas as decimal strings" in {
      val policy = policyFixture()
      val baseline = fixture("commit-a", "system-property", policy.fingerprint)
      val result = BeautyQAcceptedBaselineVerifier.verify(baseline, baseline, policy) match {
        case Right(value) => value
        case Left(error) => fail(s"expected verification result, got ${error.code}")
      }
      assert(result.passed)
      assert(result.orderedDeltas.forall(_.delta == BigDecimal("0.000000000000")))
      val deltaJson = result.toJson.hcursor.downField("orderedDeltas").downArray
      assert(deltaJson.get[String]("canonical").contains("0.800000000000"))
      assert(deltaJson.get[String]("candidate").contains("0.800000000000"))
      assert(deltaJson.get[String]("delta").contains("0.000000000000"))
    }

    "treat a non-zero delta as informational evidence" in {
      val policy = policyFixture()
      val baseline = fixture("commit-a", "system-property", policy.fingerprint, average = "0.800000000000")
      val candidate = fixture("commit-b", "system-property", policy.fingerprint, average = "0.812345678901")
      val result = BeautyQAcceptedBaselineVerifier.verify(candidate, baseline, policy) match {
        case Right(value) => value
        case Left(error) => fail(s"expected compatible comparison, got ${error.code}")
      }
      assert(result.passed)
      assert(result.orderedDeltas.map(_.delta) == Vector(BigDecimal("0.012345678901")))
      assert(result.toJson.hcursor.downField("orderedDeltas").downArray.get[String]("delta").contains("0.012345678901"))
    }

    "keep run-specific provenance differences outside the quality gate" in {
      val policy = policyFixture()
      val baseline = fixture("commit-a", "system-property", policy.fingerprint, generation = "a", report = "d" * 64)
      val candidate = fixture("commit-b", "system-property", policy.fingerprint, generation = "b", report = "e" * 64)
      val result = BeautyQAcceptedBaselineVerifier.verify(candidate, baseline, policy) match {
        case Right(value) => value
        case Left(error) => fail(s"expected run-specific differences to be informational, got ${error.code}")
      }
      assert(result.passed)
    }

    "not collapse duplicate provenance at the strict codec boundary" in {
      val policy = policyFixture()
      val json = AcceptedBaselineCodec.encode(fixture("commit-a", "system-property", policy.fingerprint))
      val duplicate = json.hcursor.downField("provenance").focus match {
        case Some(value) => json.mapObject(_.add("provenance", value.asArray.map(values => io.circe.Json.fromValues(values ++ values.take(1))).getOrElse(value)))
        case None => fail("fixture did not encode provenance")
      }
      assert(AcceptedBaselineCodec.decode(duplicate).isLeft)
    }

    "reject metric, evaluation and protected-policy mismatches" in {
      val policy = policyFixture()
      val baseline = fixture("commit-a", "system-property", policy.fingerprint)
      val metric = fixture("commit-b", "system-property", policy.fingerprint, metricSchema = "metric-v2")
      val evaluation = fixture("commit-b", "system-property", policy.fingerprint, evaluationPolicy = "eval-v2")
      val protectedPolicy = fixture("commit-b", "system-property", "c" * 64)
      val metricResult = verify(metric, baseline, policy)
      val evaluationResult = verify(evaluation, baseline, policy)
      val protectedResult = verify(protectedPolicy, baseline, policy)
      assert(failed(metricResult, "metric-schema-version"))
      assert(failed(evaluationResult, "evaluation-policy-version"))
      assert(failed(evaluationResult, "policy-evaluation-version"))
      assert(failed(protectedResult, "stable-provenance-protected-policy-fingerprint"))
      assert(failed(protectedResult, "protected-policy-fingerprint"))
    }

    "report every stable provenance identity as a distinct compatibility check" in {
      val policy = policyFixture()
      val baseline = fixture("commit-a", "system-property", policy.fingerprint)
      val stableValues = Vector(
        "visible-corpus-fingerprint" -> ("c" * 64),
        "protected-corpus-fingerprint" -> ("c" * 64),
        "protected-policy-fingerprint" -> ("c" * 64),
        "source-content-fingerprint" -> ("c" * 64),
        "projected-documents-fingerprint" -> ("c" * 64),
        "embedding-provider" -> "other-provider",
        "embedding-model" -> "other-model",
        "embedding-revision" -> "other-revision",
        "embedding-dimension" -> "385",
        "embedding-text-format-version" -> "v2",
        "elasticsearch-version" -> "9",
        "qdrant-version" -> "1.19.0",
      )
      stableValues.foreach { case (id, value) =>
        val candidate = fixture("commit-b", "system-property", policy.fingerprint, provenanceOverrides = Map(id -> value))
        assert(failed(verify(candidate, baseline, policy), s"stable-provenance-$id"), s"missing mismatch for $id")
      }
    }

    "reject missing run-specific provenance and preserve compatible run identities" in {
      val policy = policyFixture()
      val baseline = fixture("commit-a", "system-property", policy.fingerprint)
      val missing = fixture("commit-b", "system-property", policy.fingerprint, omitProvenance = Set("qdrant-generation-id"))
      val missingResult = verify(missing, baseline, policy)
      assert(failed(missingResult, "run-provenance-qdrant-generation-id"))
      val changed = fixture("commit-b", "system-property", policy.fingerprint, generation = "different", report = "f" * 64)
      assert(verify(changed, baseline, policy).exists(_.passed))
    }

    "reject aggregate order, missing and unexpected aggregate observations" in {
      val policy = policyFixture(Vector("first", "second"))
      val baseline = fixture("commit-a", "system-property", policy.fingerprint, aggregateKeys = Vector("first", "second"))
      val reordered = fixture("commit-b", "system-property", policy.fingerprint, aggregateKeys = Vector("second", "first"))
      val missing = fixture("commit-b", "system-property", policy.fingerprint, aggregateKeys = Vector("first"))
      val unexpected = fixture("commit-b", "system-property", policy.fingerprint, aggregateKeys = Vector("first", "third"))
      assert(failed(verify(reordered, baseline, policy), "aggregate-key-order"))
      assert(failed(verify(missing, baseline, policy), "missing-aggregate-keys"))
      assert(failed(verify(unexpected, baseline, policy), "unexpected-aggregate-keys"))
    }

    "reject metric scope order, missing and unexpected observations" in {
      val policy = policyFixture()
      val baseline = fixture("commit-a", "system-property", policy.fingerprint,
        metricObservations = Vector(("variants", "success", 1, "0.800000000000"), ("variants", "mrr", 1, "0.700000000000")))
      val reordered = fixture("commit-b", "system-property", policy.fingerprint,
        metricObservations = Vector(("variants", "mrr", 1, "0.700000000000"), ("variants", "success", 1, "0.800000000000")))
      val missing = fixture("commit-b", "system-property", policy.fingerprint,
        metricObservations = Vector(("variants", "success", 1, "0.800000000000")))
      val unexpected = fixture("commit-b", "system-property", policy.fingerprint,
        metricObservations = Vector(("variants", "success", 1, "0.800000000000"), ("variants", "recall", 1, "0.600000000000")))
      assert(failed(verify(reordered, baseline, policy), "metric-scope-order-0"))
      assert(failed(verify(missing, baseline, policy), "missing-metric-scopes-0"))
      assert(failed(verify(unexpected, baseline, policy), "unexpected-metric-scopes-0"))
    }

    "reject aggregate and per-metric count mismatches" in {
      val policy = policyFixture()
      val baseline = fixture("commit-a", "system-property", policy.fingerprint)
      val structural = fixture("commit-b", "system-property", policy.fingerprint, structuralInvalidCount = 1)
      val applicable = fixture("commit-b", "system-property", policy.fingerprint, metricApplicableCount = 2)
      val notApplicable = fixture("commit-b", "system-property", policy.fingerprint, metricNotApplicableCount = 1)
      assert(failed(verify(structural, baseline, policy), "quality-counts-protected-global"))
      assert(failed(verify(applicable, baseline, policy), "applicable-count-0-0"))
      assert(failed(verify(notApplicable, baseline, policy), "not-applicable-count-0-0"))
    }

    "serialize failed checks without protected vocabulary and retain informational deltas" in {
      val policy = policyFixture()
      val baseline = fixture("commit-a", "system-property", policy.fingerprint, average = "0.800000000000")
      val candidate = fixture("commit-b", "system-property", policy.fingerprint, average = "0.812345678901", sourceContent = "e" * 64)
      val result = verify(candidate, baseline, policy) match {
        case Right(value) => value
        case Left(error) => fail(error.code)
      }
      assert(!result.passed)
      val checks = result.toJson.hcursor.downField("checks").focus.flatMap(_.asArray).getOrElse(Vector.empty)
      assert(checks.exists(_.hcursor.get[Boolean]("passed").contains(false)))
      val text = result.toJson.noSpaces
      assert(!text.contains("caseId"))
      assert(!text.contains("rawRanking"))
      assert(!text.contains("resultId"))
      assert(!text.contains("query"))
      val compatible = verify(fixture("commit-b", "system-property", policy.fingerprint, average = "0.812345678901"), baseline, policy) match {
        case Right(value) => value
        case Left(error) => fail(error.code)
      }
      assert(compatible.passed)
      assert(compatible.orderedDeltas.map(_.delta) == Vector(BigDecimal("0.012345678901")))
    }

    "keep the verification result construction boundary closed" in {
      assertDoesNotCompile(
        """new leaderboard.search.beautyq.gen2.eval.BeautyQAcceptedBaselineVerificationResult(true, "a" * 64, "b" * 64, "commit", "commit", "a" * 64, "eval", "b" * 64, Vector.empty, Vector.empty)""",
      )
      assertDoesNotCompile(
        """final class Forged extends leaderboard.search.beautyq.gen2.eval.BeautyQAcceptedBaselineVerificationResult(true, "a" * 64, "b" * 64, "commit", "commit", "a" * 64, "eval", "b" * 64, Vector.empty, Vector.empty)""",
      )
    }
  }

  private def fixture(
    revision: String,
    source: String,
    policyFingerprint: String,
    average: String = "0.800000000000",
    generation: String = "a",
    report: String = "d" * 64,
    sourceContent: String = "d" * 64,
    provenanceOverrides: Map[String, String] = Map.empty,
    metricSchema: String = "metric-v1",
    evaluationPolicy: String = "eval-v1",
    omitProvenance: Set[String] = Set.empty,
    aggregateKeys: Vector[String] = Vector("protected-global"),
    metricObservations: Vector[(String, String, Int, String)] = Vector(("variants", "success", 1, "0.800000000000")),
    structuralInvalidCount: Int = 0,
    metricApplicableCount: Int = 1,
    metricNotApplicableCount: Int = 0,
  ): AcceptedEvaluationBaseline = {
    val effectiveMetricObservations = if (metricObservations == Vector(("variants", "success", 1, "0.800000000000")))
      Vector(("variants", "success", 1, average))
    else metricObservations
    val values = Vector(
      "corpus-fingerprint" -> ("a" * 64),
      "evaluation-policy-version" -> evaluationPolicy,
      "metric-schema-version" -> metricSchema,
      "application-revision" -> revision,
      "application-revision-source" -> source,
      "visible-corpus-fingerprint" -> ("b" * 64),
      "protected-corpus-fingerprint" -> ("a" * 64),
      "protected-policy-fingerprint" -> policyFingerprint,
      "source-content-fingerprint" -> sourceContent,
      "projected-documents-fingerprint" -> ("f" * 64),
      "embedding-provider" -> "provider",
      "embedding-model" -> "model",
      "embedding-revision" -> "revision",
      "embedding-dimension" -> "384",
      "embedding-text-format-version" -> "v1",
      "elasticsearch-version" -> "8",
      "qdrant-version" -> "1.18.3",
      "elasticsearch-generation-reference" -> s"es-$generation",
      "qdrant-generation-id" -> s"q-$generation",
      "visible-report-digest" -> report,
      "protected-report-digest" -> (if (report == "d" * 64) "e" * 64 else "f" * 64),
    ).map { case (id, value) => id -> provenanceOverrides.getOrElse(id, value) }
    val provenance = values.filterNot { case (idText, _) => omitProvenance.contains(idText) }.map { case (idText, value) =>
      val id = EvaluationProvenanceId.from(idText).fold(error => fail(error), identity)
      ProvenanceComponent.from(id, value).fold(error => fail(error), identity)
    }
    val json = Json.obj(
      "schemaVersion" -> Json.fromString(AcceptedEvaluationBaseline.CurrentSchemaVersion),
      "corpusFingerprint" -> Json.fromString("a" * 64),
      "metricSchemaVersion" -> Json.fromString(metricSchema),
      "evaluationPolicyVersion" -> Json.fromString(evaluationPolicy),
      "applicationRevision" -> Json.fromString(revision),
      "provenance" -> Json.fromValues(provenance.map(component => Json.obj(
        "id" -> Json.fromString(component.id.value),
        "value" -> Json.fromString(component.value),
      ))),
      "reportDigest" -> Json.fromString("b" * 64),
      "aggregateObservations" -> Json.fromValues(aggregateKeys.map { key => Json.obj(
        "key" -> Json.fromString(key),
        "section" -> Json.obj(
          "structuralInvalidCount" -> Json.fromInt(structuralInvalidCount),
          "duplicateIdentityCount" -> Json.fromInt(0),
          "zeroResultCount" -> Json.fromInt(0),
          "forbiddenHitCount" -> Json.fromInt(0),
          "applicableMetricCount" -> Json.fromInt(metricApplicableCount),
          "notApplicableMetricCount" -> Json.fromInt(metricNotApplicableCount),
          "metricObservations" -> Json.fromValues(effectiveMetricObservations.map { case (surface, metric, cutoff, value) => Json.obj(
            "surface" -> Json.fromString(surface),
            "metric" -> Json.fromString(metric),
            "cutoff" -> Json.fromInt(cutoff),
            "average" -> Json.fromBigDecimal(BigDecimal(value)),
            "applicableCount" -> Json.fromInt(metricApplicableCount),
            "notApplicableCount" -> Json.fromInt(metricNotApplicableCount),
          ) }),
        ),
      ) }),
    )
    AcceptedBaselineCodec.decode(json).fold(error => fail(error.toString), identity)
  }

  private def verify(
    candidate: AcceptedEvaluationBaseline,
    canonical: AcceptedEvaluationBaseline,
    policy: BeautyQProtectedAcceptancePolicy,
  ): Either[BeautyQAcceptedBaselineVerificationError, BeautyQAcceptedBaselineVerificationResult] =
    BeautyQAcceptedBaselineVerifier.verify(candidate, canonical, policy)

  private def failed(
    result: Either[BeautyQAcceptedBaselineVerificationError, BeautyQAcceptedBaselineVerificationResult],
    code: String,
  ): Boolean = result match {
    case Right(value) => value.checks.exists(check => check.code == code && !check.passed)
    case Left(_) => false
  }

  private def policyFixture(keys: Vector[String] = Vector("protected-global")): BeautyQProtectedAcceptancePolicy =
    BeautyQProtectedAcceptancePolicy.fromJson(policyJson(keys)) match {
      case Right(value) => value
      case Left(error) => fail(s"expected policy fixture, got $error")
    }

  private def policyJson(keys: Vector[String]): Json = Json.obj(
    "schemaVersion" -> Json.fromString(BeautyQProtectedAcceptancePolicy.CurrentSchemaVersion),
    "evaluationPolicyVersion" -> Json.fromString("eval-v1"),
    "protectedAcceptancePolicyVersion" -> Json.fromString("protected-v1"),
    "expectedCorpusFingerprint" -> Json.fromString("a" * 64),
    "expectedCaseCount" -> Json.fromInt(1),
    "requiredSliceMinimums" -> Json.arr(Json.obj("sliceId" -> Json.fromString("smoke"), "minimumCaseCount" -> Json.fromInt(1))),
    "requiredMetricMinimums" -> Json.fromValues(keys.map(key => Json.obj(
      "observationKey" -> Json.fromString(key),
      "surface" -> Json.fromString("variants"),
      "metric" -> Json.fromString("success"),
      "cutoff" -> Json.fromInt(1),
      "minimum" -> Json.fromString("0.000000000000"),
    ))),
  )
}
