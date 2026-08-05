package leaderboard.search.beautyq.gen2.eval

import io.circe.Json
import org.scalatest.wordspec.AnyWordSpec

import java.security.MessageDigest
import scala.io.Source

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

    "keep the post-recovery author draft strict, author-only, and separate from judged bytes" in {
      val authorRaw = readResource(AuthorDraftResource)
      val judgedRaw = readResource(JudgedDraftResource)
      val protectedCorpus = BeautyQProtectedEvaluationCorpus.load(
        resourcePath(ProtectedCorpusResource),
        BeautyQEvaluationCorpus.loadCanonical().fold(error => fail(error.toString), identity),
        BeautyQProtectedAcceptancePolicy.load(resourcePath(PolicyResource)).fold(error => fail(error.toString), identity),
      ).fold(error => fail(error.toString), identity)

      val author = BeautyQProtectedAuthorDraft.decodeString(authorRaw).fold(error => fail(error), identity)
      assert(author.schemaVersion == BeautyQProtectedAuthorDraft.CurrentSchemaVersion)
      assert(author.sourceRevision == "440fdf2827a880ea02c36fb3044c18d1b1874c23")
      assert(author.authorPassId == "q2-exact-intent-recovery-rotation-2-author-v1")
      assert(author.cases.size == 24)
      assert(BeautyQProtectedAuthorDraft.correspondsTo(author, protectedCorpus).isRight)
      assert(authorRaw != judgedRaw)
      Vector("judgments", "acceptableIds", "forbiddenIds", "neutralIds", "gradedGains", "resultId", "providerId", "serviceIntentId")
        .foreach(field => assert(!authorRaw.contains(field), s"author draft leaked $field"))
      assert(judgedRaw == readResource(ProtectedCorpusResource))
    }

    "bind the tracked audit to both independent loads and exact resource hashes" in {
      val audit = BeautyQProtectedInputAudit.decodeString(readResource(AuditResource)).fold(error => fail(error.stableCode), identity)
      val corpus = BeautyQEvaluationCorpus.loadCanonical().fold(error => fail(error.toString), identity)
      val policy = BeautyQProtectedAcceptancePolicy.load(resourcePath(PolicyResource)).fold(error => fail(error.toString), identity)
      assert(audit.protectedCorpusFingerprint == BeautyQProtectedEvaluationCorpus.load(
        resourcePath(ProtectedCorpusResource), corpus, policy,
      ).fold(error => fail(error.toString), identity).corpusFingerprint)
      assert(audit.protectedPolicyFingerprint == policy.fingerprint)
      assert(audit.protectedCorpusSha256 == sha256(resourcePath(ProtectedCorpusResource)))
      assert(audit.protectedPolicySha256 == sha256(resourcePath(PolicyResource)))
      assert(audit.judgedDraftSha256 == sha256(resourcePath(JudgedDraftResource)))
      assert(audit.authorDraftSha256 == sha256(resourcePath(AuthorDraftResource)))
      assert(audit.authorDraftSha256 != audit.judgedDraftSha256)
      assert(audit.protectedCorpusFingerprint.length == 64)
      assert(audit.protectedPolicyFingerprint.length == 64)
      assert(audit.protectedCorpusFingerprint ==
        "f531e287027595a602fe97f44cae7d7cfbe2759be8f1b18d594d21ecbdb83f6e")
      assert(audit.protectedPolicyFingerprint ==
        "c9c0677f25b45310376d0e2aa4c678a1e579a984f0dc8cd2e8ef0bba6990289b")
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

  private def resourcePath(name: String): java.nio.file.Path =
    Option(getClass.getClassLoader.getResource(name)) match {
      case Some(resource) => java.nio.file.Paths.get(resource.toURI)
      case None => fail(s"missing resource: $name")
    }

  private def readResource(name: String): String = {
    val stream = Option(getClass.getClassLoader.getResourceAsStream(name)).getOrElse(fail(s"missing resource: $name"))
    try Source.fromInputStream(stream, "UTF-8").mkString
    finally stream.close()
  }

  private val AuthorDraftResource = "leaderboard/search/beautyq/gen2/eval/protected/provenance/beautyq-protected-author-draft-v1.json"
  private val JudgedDraftResource = "leaderboard/search/beautyq/gen2/eval/protected/provenance/beautyq-protected-judged-draft-v1.json"
  private val ProtectedCorpusResource = "leaderboard/search/beautyq/gen2/eval/protected/beautyq-protected-holdout-v1.json"
  private val PolicyResource = "leaderboard/search/beautyq/gen2/eval/protected/beautyq-protected-acceptance-policy-v1.json"
  private val AuditResource = "leaderboard/search/beautyq/gen2/eval/protected/beautyq-protected-input-audit-v2.json"

  private def sha256(path: java.nio.file.Path): String =
    MessageDigest.getInstance("SHA-256").digest(java.nio.file.Files.readAllBytes(path)).map(byte => f"${byte & 0xff}%02x").mkString

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
