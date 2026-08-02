package leaderboard.search.beautyq.gen2.eval

import io.circe.Json
import org.scalatest.wordspec.AnyWordSpec

final class BeautyQProtectedInputAuditSpec extends AnyWordSpec {
  "BeautyQProtectedInputAudit" should {
    "construct exact validated evidence and preserve deterministic ordered JSON" in {
      val fixture = validFixture()
      assert(fixture.audit.schemaVersion == BeautyQProtectedInputAudit.CurrentSchemaVersion)
      assert(fixture.audit.protectedCaseCount == 2)
      assert(fixture.audit.authorDraftSha256 == "d" * 64)
      assert(fixture.audit.judgedDraftSha256 == "e" * 64)
      assert(fixture.audit.canonicalSourceFingerprint == "f" * 64)
      assert(fixture.audit.exactIntentCaseCount == 2)
      assert(fixture.audit.orderedRequiredSliceCounts.map { case (slice, count) => slice.value -> count } ==
        Vector("exact-intent" -> 2, "multilingual" -> 1))
      val first = fixture.audit.toJson.noSpaces
      val second = fixture.audit.toJson.noSpaces
      assert(first == second)
      BeautyQProtectedInputAudit.decode(fixture.audit.toJson) match {
        case Right(decoded) => assert(decoded == fixture.audit)
        case Left(error) => fail(s"expected strict round trip, got ${error.stableCode}")
      }
    }

    "reject unknown and missing fields" in {
      val json = validFixture().audit.toJson
      assert(BeautyQProtectedInputAudit.decode(json.mapObject(_.add("unexpected", Json.fromString("x")))).isLeft)
      assert(BeautyQProtectedInputAudit.decode(json.mapObject(_.remove("sourceRevision"))).isLeft)
      assert(BeautyQProtectedInputAudit.decode(json.mapObject(_.add("schemaVersion", Json.fromString("beautyq-protected-input-audit-v1")))).isLeft)
    }

    "require exact-intent aggregate count to match its ordered slice entry" in {
      val json = validFixture().audit.toJson
      val mismatchedCount = json.mapObject(_.add("exactIntentCaseCount", Json.fromInt(1)))
      assert(BeautyQProtectedInputAudit.decode(mismatchedCount).left.exists(_.stableCode == "exact_intent_inventory_mismatch"))
      val missingSlice = json.mapObject(_.add("orderedRequiredSliceCounts", Json.arr(Json.obj(
        "sliceId" -> Json.fromString("multilingual"),
        "caseCount" -> Json.fromInt(1),
      ))))
      assert(BeautyQProtectedInputAudit.decode(missingSlice).left.exists(_.stableCode == "exact_intent_inventory_mismatch"))
      val tooMany = json.mapObject(_.add("exactIntentCaseCount", Json.fromInt(3)))
      assert(BeautyQProtectedInputAudit.decode(tooMany).left.exists(_.stableCode == "exact_intent_inventory_mismatch"))
    }

    "reject invalid revision, hashes, pass identities and visible leakage" in {
      val fixture = validFixture()
      assert(create(fixture, sourceRevision = "not-a-revision").isLeft)
      assert(create(fixture, corpusHash = "bad").isLeft)
      assert(create(fixture, policyHash = "bad").isLeft)
      assert(create(fixture, authorPassId = " ").isLeft)
      assert(create(fixture, judgePassId = "q2i-author-v1").isLeft)
      assert(create(fixture, exactDuplicates = 1).isLeft)
      assert(create(fixture, normalizedDuplicates = 1).isLeft)
      assert(create(fixture, caseIdOverlap = 1).isLeft)
      assert(create(fixture, invalidVariantIdentities = 1).isLeft)
      assert(create(fixture, invalidProviderIdentities = 1).isLeft)
      assert(create(fixture, invalidServiceIntentIdentities = 1).isLeft)
      assert(create(fixture, internalExactDuplicates = 1).isLeft)
      assert(create(fixture, internalNormalizedDuplicates = 1).isLeft)
      assert(create(fixture, exactIntentWithoutVariant = 1).isLeft)
    }

    "reject zero case count and failed validation flags during strict decode" in {
      val json = validFixture().audit.toJson
      val zeroCount = json.mapObject(_.add("protectedCaseCount", Json.fromInt(0)))
      val failedValidation = json.mapObject(_.add("strictCorpusValidationPassed", Json.fromBoolean(false)))
      assert(BeautyQProtectedInputAudit.decode(zeroCount).isLeft)
      assert(BeautyQProtectedInputAudit.decode(failedValidation).isLeft)
    }

    "reject malformed draft and catalog fingerprints" in {
      val fixture = validFixture()
      assert(create(fixture, authorDraftHash = "bad").isLeft)
      assert(create(fixture, judgedDraftHash = "bad").isLeft)
      assert(create(fixture, canonicalSourceFingerprint = "bad").isLeft)
    }

    "exclude private identity vocabulary from the audit protocol" in {
      val encoded = validFixture().audit.toJson.noSpaces
      val forbidden = Vector("caseId", "query", "acceptableIds", "forbiddenIds", "resultId", "judgments", "notes")
      assert(forbidden.forall(value => !encoded.contains(value)))
    }

    "close direct construction, copy and subclassing" in {
      assertDoesNotCompile("""
        new leaderboard.search.beautyq.gen2.eval.BeautyQProtectedInputAudit(
          "beautyq-protected-input-audit-v2", "a" * 40,
          "model-assisted-separated-passes-v1", "author", "judge", "audit",
          "b" * 64, "c" * 64, "d" * 64, "e" * 64,
          "f" * 64, "g" * 64, "h" * 64, 2,
          Vector.empty[(leaderboard.search.gen2.eval.EvaluationSliceId, Int)],
          0, 0, 0, 0, 0, 2, 0, 0, 0, 0,
          true, true, true, true, true
        )
      """)
      assertDoesNotCompile("""
        val value: leaderboard.search.beautyq.gen2.eval.BeautyQProtectedInputAudit = ???
        value.copy()
      """)
      assertDoesNotCompile("""
        final class Forged extends leaderboard.search.beautyq.gen2.eval.BeautyQProtectedInputAudit(
          "beautyq-protected-input-audit-v2", "a" * 40,
          "model-assisted-separated-passes-v1", "author", "judge", "audit",
          "b" * 64, "c" * 64, "d" * 64, "e" * 64,
          "f" * 64, "g" * 64, "h" * 64, 2,
          Vector.empty[(leaderboard.search.gen2.eval.EvaluationSliceId, Int)],
          0, 0, 0, 0, 0, 2, 0, 0, 0, 0,
          true, true, true, true, true
        )
      """)
    }
  }

  private final class Fixture(
    val audit: BeautyQProtectedInputAudit,
    val protectedCorpus: BeautyQProtectedEvaluationCorpus,
    val policy: BeautyQProtectedAcceptancePolicy,
  )

  private def validFixture(): Fixture = {
    val visible = decodeCorpus(corpusJson(Vector(caseJson("visible-case", "visible query", "regression", Vector("visible")))))
    val rawProtected = corpusJson(Vector(
      caseJson("private-case-1", "private query one", "protected-holdout", Vector("exact-intent", "multilingual")),
      caseJson("private-case-2", "private query two", "protected-holdout", Vector("exact-intent")),
    ))
    val decodedProtected = decodeCorpus(rawProtected)
    val policy = decodePolicy(policyJson(decodedProtected.corpusFingerprint))
    val protectedCorpus = BeautyQProtectedEvaluationCorpus.fromJson(rawProtected, visible, policy) match {
      case Right(value) => value
      case Left(error) => fail(s"expected protected fixture, got $error")
    }
    val audit = BeautyQProtectedInputAudit.create(
      "a" * 40,
      BeautyQProtectedInputAudit.CurrentAuthoringMethod,
      "q2i-author-v1",
      "q2i-judge-v1",
      "q2i-audit-v1",
      "b" * 64,
      "c" * 64,
      "d" * 64,
      "e" * 64,
      protectedCorpus,
      policy,
      "f" * 64,
      0,
      0,
      0,
      0,
      0,
      2,
      0,
      0,
      0,
      0,
    ) match {
      case Right(value) => value
      case Left(error) => fail(s"expected valid audit, got ${error.stableCode}")
    }
    new Fixture(audit, protectedCorpus, policy)
  }

  private def create(
    fixture: Fixture,
    sourceRevision: String = "a" * 40,
    corpusHash: String = "b" * 64,
    policyHash: String = "c" * 64,
    authorPassId: String = "q2i-author-v1",
    judgePassId: String = "q2i-judge-v1",
    exactDuplicates: Int = 0,
    normalizedDuplicates: Int = 0,
    caseIdOverlap: Int = 0,
    internalExactDuplicates: Int = 0,
    internalNormalizedDuplicates: Int = 0,
    exactIntentWithoutVariant: Int = 0,
    invalidVariantIdentities: Int = 0,
    invalidProviderIdentities: Int = 0,
    invalidServiceIntentIdentities: Int = 0,
    authorDraftHash: String = "d" * 64,
    judgedDraftHash: String = "e" * 64,
    canonicalSourceFingerprint: String = "f" * 64,
  ): Either[BeautyQProtectedInputAuditError, BeautyQProtectedInputAudit] =
    BeautyQProtectedInputAudit.create(
      sourceRevision,
      BeautyQProtectedInputAudit.CurrentAuthoringMethod,
      authorPassId,
      judgePassId,
      "q2i-audit-v1",
      corpusHash,
      policyHash,
      authorDraftHash,
      judgedDraftHash,
      fixture.protectedCorpus,
      fixture.policy,
      canonicalSourceFingerprint,
      exactDuplicates,
      normalizedDuplicates,
      caseIdOverlap,
      internalExactDuplicates,
      internalNormalizedDuplicates,
      2,
      exactIntentWithoutVariant,
      invalidVariantIdentities,
      invalidProviderIdentities,
      invalidServiceIntentIdentities,
    )

  private def corpusJson(cases: Vector[Json]): Json = Json.obj(
    "schemaVersion" -> Json.fromString("beautyq-evaluation-corpus-v2"),
    "corpusId" -> Json.fromString("audit-fixture"),
    "dataset" -> Json.fromString("fixture"),
    "version" -> Json.fromInt(1),
    "defaultUserLocation" -> Json.obj(
      "label" -> Json.fromString("fixture"),
      "lat" -> Json.fromDoubleOrNull(53.57),
      "lon" -> Json.fromDoubleOrNull(10.06),
    ),
    "cases" -> Json.fromValues(cases),
  )

  private def caseJson(id: String, query: String, partition: String, slices: Vector[String]): Json = Json.obj(
    "id" -> Json.fromString(id),
    "partition" -> Json.fromString(partition),
    "judgmentMode" -> Json.fromString("partial"),
    "query" -> Json.fromString(query),
    "language" -> Json.fromString("en"),
    "slices" -> Json.fromValues(slices.map(Json.fromString)),
    "userIntent" -> Json.fromString("fixture intent"),
    "notes" -> Json.arr(),
    "judgments" -> Json.obj(
      "variants" -> variantJudgments,
      "providers" -> emptyJudgments,
      "serviceIntents" -> emptyJudgments,
    ),
  )

  private def emptyJudgments: Json = Json.obj(
    "acceptableIds" -> Json.arr(),
    "forbiddenIds" -> Json.arr(),
    "neutralIds" -> Json.arr(),
    "gradedGains" -> Json.arr(),
  )

  private def variantJudgments: Json = Json.obj(
    "acceptableIds" -> Json.arr(Json.fromString("1fcd6e17-c6bb-5901-9f63-205668897659")),
    "forbiddenIds" -> Json.arr(),
    "neutralIds" -> Json.arr(),
    "gradedGains" -> Json.arr(),
  )

  private def policyJson(corpusFingerprint: String): Json = Json.obj(
    "schemaVersion" -> Json.fromString(BeautyQProtectedAcceptancePolicy.CurrentSchemaVersion),
    "evaluationPolicyVersion" -> Json.fromString(BeautyQEvaluationPolicy.CurrentVersion),
    "protectedAcceptancePolicyVersion" -> Json.fromString("beautyq-protected-acceptance-policy-v1"),
    "expectedCorpusFingerprint" -> Json.fromString(corpusFingerprint),
    "expectedCaseCount" -> Json.fromInt(2),
    "requiredSliceMinimums" -> Json.arr(
      Json.obj("sliceId" -> Json.fromString("exact-intent"), "minimumCaseCount" -> Json.fromInt(2)),
      Json.obj("sliceId" -> Json.fromString("multilingual"), "minimumCaseCount" -> Json.fromInt(1)),
    ),
    "requiredMetricMinimums" -> Json.arr(Json.obj(
      "observationKey" -> Json.fromString("protected-slice:exact-intent"),
      "surface" -> Json.fromString("variants"),
      "metric" -> Json.fromString("success"),
      "cutoff" -> Json.fromInt(10),
      "minimum" -> Json.fromString("1.000000000000"),
    )),
  )

  private def decodeCorpus(json: Json): BeautyQEvaluationCorpus =
    BeautyQEvaluationCorpus.decodeFromJson(json) match {
      case Right(value) => value
      case Left(error) => fail(s"expected corpus fixture, got $error")
    }

  private def decodePolicy(json: Json): BeautyQProtectedAcceptancePolicy =
    BeautyQProtectedAcceptancePolicy.fromJson(json) match {
      case Right(value) => value
      case Left(error) => fail(s"expected policy fixture, got $error")
    }
}
