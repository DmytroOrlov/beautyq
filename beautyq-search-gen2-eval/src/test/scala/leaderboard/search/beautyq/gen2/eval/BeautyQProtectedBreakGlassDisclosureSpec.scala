package leaderboard.search.beautyq.gen2.eval

import io.circe.Json
import leaderboard.search.gen2.eval.*
import org.scalatest.wordspec.AnyWordSpec

final class BeautyQProtectedBreakGlassDisclosureSpec extends AnyWordSpec {
  "BeautyQ protected break-glass disclosure" should {
    "select only zero-success contributors in the authorised slice and preserve surface and metric identities" in {
      val fixture = syntheticFixture()
      val disclosure = derive(fixture, failedResult())

      assert(disclosure.disclosedCases.map(_.corpusCase.caseId.value) == Vector("protected-failing"))
      assert(disclosure.disclosedCases.map(_.relevantSurface) == Vector("variants"))
      assert(disclosure.disclosedCases.map(_.relevantMetric) == Vector("success"))
      assert(disclosure.disclosedCases.map(_.cutoff) == Vector(10))
      assert(disclosure.applicationRevision == "commit-visible-123")
      assert(disclosure.applicationRevisionSource == "system-property")
      assert(disclosure.authorizationId == BeautyQProtectedBreakGlassDisclosure.AuthorizationId)

      val encoded = disclosure.toJson.noSpaces
      assert(encoded.contains("protected-failing"))
      assert(encoded.contains("failing-public-result"))
      assert(!encoded.contains("protected-passing"))
      assert(!encoded.contains("passing-protected-sentinel"))
      assert(!encoded.contains("protected-other-slice"))
      assert(!encoded.contains("other-slice-sentinel"))
      assert(!encoded.contains("rawRanking"))
      assert(!encoded.contains("uniqueRanking"))
      assert(!encoded.contains("minimum"))
      assert(!encoded.contains("metric.success"))
    }

    "reject green, changed and additional failed-check sets" in {
      val fixture = syntheticFixture()
      val green = acceptance(Vector(check(BeautyQProtectedBreakGlassDisclosure.AuthorizedCheckCode, passed = true)))
      assertLeft(deriveAttempt(fixture, green), "break_glass_evidence_changed")

      val changed = acceptance(Vector(check("metric-protected-slice:other-variants/success/10", passed = false)))
      assertLeft(deriveAttempt(fixture, changed), "break_glass_evidence_changed")

      val additional = acceptance(Vector(
        check(BeautyQProtectedBreakGlassDisclosure.AuthorizedCheckCode, passed = false),
        check("protected-no-forbidden", passed = false),
      ))
      assertLeft(deriveAttempt(fixture, additional), "break_glass_evidence_changed")
    }

    "reject unapproved authorization and working-tree provenance" in {
      val fixture = syntheticFixture()
      assertLeft(BeautyQProtectedBreakGlassDisclosure.derive(
        failedResult(), fixture.report, fixture.corpus, fixture.policy,
        "working-tree", BeautyQProtectedBreakGlassDisclosure.AuthorizationId,
        BeautyQProtectedBreakGlassDisclosure.AuthorizedCheckCode,
      ), "invalid_application_revision")
      assertLeft(BeautyQProtectedBreakGlassDisclosure.derive(
        failedResult(), fixture.report, fixture.corpus, fixture.policy,
        "commit-visible-123", "another-authorization",
        BeautyQProtectedBreakGlassDisclosure.AuthorizedCheckCode,
      ), "invalid_authorization_id")
    }

    "encode deterministically while the ordinary protected report remains aggregate-only" in {
      val fixture = syntheticFixture()
      val first = derive(fixture, failedResult()).toJson.noSpaces
      val second = derive(fixture, failedResult()).toJson.noSpaces
      assert(first == second)
      assert(EvaluationReport.encodeProtected(fixture.report).hcursor.downField("caseResults").focus.isEmpty)
    }
  }

  private final case class Fixture(
    corpus: BeautyQEvaluationCorpus,
    policy: BeautyQProtectedAcceptancePolicy,
    report: EvaluationReport,
  )

  private def syntheticFixture(): Fixture = {
    val exact = slice("exact-intent")
    val other = slice("other")
    val failing = corpusCase(
      "protected-failing", "failing query", exact,
      acceptable = "acceptable-not-returned", ranking = Vector("failing-public-result"),
    )
    val passing = corpusCase(
      "protected-passing", "passing query", exact,
      acceptable = "passing-protected-sentinel", ranking = Vector("passing-protected-sentinel"),
    )
    val unrelated = corpusCase(
      "protected-other-slice", "other query", other,
      acceptable = "acceptable-other", ranking = Vector("other-slice-sentinel"),
    )
    val cases = Vector(failing._1, passing._1, unrelated._1)
    val corpus = new BeautyQEvaluationCorpus(
      "beautyq-evaluation-corpus-v2", "protected-synthetic", "test", 1,
      UserLocation.from("test", 53.55, 10.0), cases, "a" * 64,
    )
    val report = EvaluationReportBuilder.build(Vector.empty, Vector(failing._2, passing._2, unrelated._2))
    Fixture(corpus, policy(), report)
  }

  private def corpusCase(
    id: String,
    query: String,
    sliceId: EvaluationSliceId,
    acceptable: String,
    ranking: Vector[String],
  ): (CorpusCase, EvaluationReportCaseInput) = {
    val caseId = EvaluationCaseId.from(id).fold(error => fail(error), identity)
    val judgments = RankingJudgments.from(
      JudgmentMode.Partial,
      Vector(resultId(acceptable)),
      Vector.empty,
      Vector.empty,
      Vector.empty,
    ).fold(error => fail(error), identity)
    val evaluated = RankingEvaluationInput.from(
      caseId,
      EvaluationPartition.ProtectedHoldout,
      BeautyQEvaluationPolicy.Variants,
      Vector(sliceId),
      judgments,
      ranking.map(resultId),
      BeautyQEvaluationPolicy.cutoffs,
    ).map(RankingEvaluator.evaluate).fold(error => fail(error), identity)
    val input = EvaluationReportCaseInput.from(
      caseId,
      EvaluationPartition.ProtectedHoldout,
      JudgmentMode.Partial,
      Some(Vector(sliceId)),
      Vector(BeautyQEvaluationPolicy.Variants -> evaluated),
    ).fold(error => fail(error), identity)
    val current = new CorpusCase(
      caseId,
      EvaluationPartition.ProtectedHoldout,
      JudgmentMode.Partial,
      query,
      "en",
      Vector(sliceId),
      "synthetic intent",
      Vector.empty,
      judgments,
      emptyJudgments,
      emptyJudgments,
    )
    current -> input
  }

  private def policy(): BeautyQProtectedAcceptancePolicy = {
    val json = Json.obj(
      "schemaVersion" -> Json.fromString(BeautyQProtectedAcceptancePolicy.CurrentSchemaVersion),
      "evaluationPolicyVersion" -> Json.fromString(BeautyQEvaluationPolicy.CurrentVersion),
      "protectedAcceptancePolicyVersion" -> Json.fromString("synthetic-v1"),
      "expectedCorpusFingerprint" -> Json.fromString("a" * 64),
      "expectedCaseCount" -> Json.fromInt(3),
      "requiredSliceMinimums" -> Json.arr(Json.obj(
        "sliceId" -> Json.fromString("exact-intent"),
        "minimumCaseCount" -> Json.fromInt(2),
      )),
      "requiredMetricMinimums" -> Json.arr(Json.obj(
        "observationKey" -> Json.fromString("protected-slice:exact-intent"),
        "surface" -> Json.fromString("variants"),
        "metric" -> Json.fromString("success"),
        "cutoff" -> Json.fromInt(10),
        "minimum" -> Json.fromString("1.000000000000"),
      )),
    )
    BeautyQProtectedAcceptancePolicy.fromJson(json).fold(error => fail(error.toString), identity)
  }

  private def failedResult(): BeautyQProtectedAcceptanceResult =
    acceptance(Vector(check(BeautyQProtectedBreakGlassDisclosure.AuthorizedCheckCode, passed = false)))

  private def acceptance(checks: Vector[BeautyQProtectedAcceptanceCheck]): BeautyQProtectedAcceptanceResult =
    BeautyQProtectedAcceptanceResult.create(
      checks.forall(_.passed),
      BeautyQEvaluationPolicy.CurrentVersion,
      "synthetic-v1",
      "b" * 64,
      "a" * 64,
      3,
      "c" * 64,
      checks,
    )

  private def check(code: String, passed: Boolean): BeautyQProtectedAcceptanceCheck =
    BeautyQProtectedAcceptanceCheck.create(code, passed, if (passed) "1.000000000000" else "0.500000000000", "1.000000000000")

  private def derive(fixture: Fixture, acceptance: BeautyQProtectedAcceptanceResult): BeautyQProtectedBreakGlassDisclosure =
    deriveAttempt(fixture, acceptance).fold(error => fail(error), identity)

  private def deriveAttempt(
    fixture: Fixture,
    acceptance: BeautyQProtectedAcceptanceResult,
  ): Either[String, BeautyQProtectedBreakGlassDisclosure] =
    BeautyQProtectedBreakGlassDisclosure.derive(
      acceptance,
      fixture.report,
      fixture.corpus,
      fixture.policy,
      "commit-visible-123",
      BeautyQProtectedBreakGlassDisclosure.AuthorizationId,
      BeautyQProtectedBreakGlassDisclosure.AuthorizedCheckCode,
    )

  private def emptyJudgments: RankingJudgments =
    RankingJudgments.from(JudgmentMode.Partial, Vector.empty, Vector.empty, Vector.empty, Vector.empty)
      .fold(error => fail(error), identity)

  private def resultId(value: String): EvaluationResultId =
    EvaluationResultId.from(value).fold(error => fail(error), identity)

  private def slice(value: String): EvaluationSliceId =
    EvaluationSliceId.from(value).fold(error => fail(error), identity)

  private def assertLeft[A](actual: Either[String, A], expected: String): Unit = actual match {
    case Left(value) =>
      assert(value == expected)
      (): Unit
    case Right(_) => fail(s"expected Left($expected)")
  }
}
