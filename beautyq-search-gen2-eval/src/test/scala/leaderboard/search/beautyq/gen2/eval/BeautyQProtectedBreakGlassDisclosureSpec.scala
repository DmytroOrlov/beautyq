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
      assert(disclosure.authorizationId == "synthetic-authorization")

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

    "reject an unapproved authorization id" in {
      val fixture = syntheticFixture()
      assertLeft(BeautyQProtectedBreakGlassDisclosure.derive(
        failedResult(), fixture.report, fixture.corpus, fixture.policy,
        "another-authorization",
        BeautyQProtectedBreakGlassDisclosure.AuthorizedCheckCode,
      ), "invalid_authorization_id")
    }

    "bind historical authorizations to actual content and reject cross-cycle provenance" in {
      val first = BeautyQProtectedBreakGlassDisclosure.FirstAuthorization
      val second = BeautyQProtectedBreakGlassDisclosure.Cycle2Authorization
      val third = BeautyQProtectedBreakGlassDisclosure.FullSliceCycle3Authorization
      val convergence = BeautyQProtectedBreakGlassDisclosure.ConvergenceAuthorization
      assert(first.id == BeautyQProtectedBreakGlassDisclosure.AuthorizationId)
      assert(first.protectedCorpusFingerprint == "825ca2862ad99b61002bcf04bfe000168eccc61760d9a1d091c0c4320afc9bb0")
      assert(first.policyFingerprint == "0f86960495e64b430e2ba55eac012bf00a8e80f0eb8d1500c967d582cd673098")
      assert(second.id == BeautyQProtectedBreakGlassDisclosure.Cycle2AuthorizationId)
      assert(second.protectedCorpusFingerprint == "7a654c7323f822f26bccc6d5ae7adde3faf4bfd790984ae63fb25c34978c9188")
      assert(second.policyFingerprint == "905894412cb68ed8447ecc9c99ffe1ac9ee94e22001f6a8c206cdd81c9a165ce")
      assert(first.failedCheckCode == second.failedCheckCode)
      assert(third.id == BeautyQProtectedBreakGlassDisclosure.FullSliceCycle3AuthorizationId)
      assert(third.protectedCorpusFingerprint == "23fe801f0b1c48b77847a94d0eb260ee5e2faac5018c67bab5debce382b5f2d0")
      assert(third.policyFingerprint == "c80f15f5cb7beb8ad5f01bec82146c67e17d8a3bc0a31e4e88a49fd115e9b360")
      assert(third.failedCheckCode == BeautyQProtectedBreakGlassDisclosure.AuthorizedCheckCode)
      assert(convergence.id == BeautyQProtectedBreakGlassDisclosure.ConvergenceAuthorizationId)
      assert(convergence.protectedCorpusFingerprint == "d72b29d6d9e13e21b34722fa4c8219975aae7613ba0c003326baefc5da056a6e")
      assert(convergence.policyFingerprint == "6a8a4f68f69d85467db941284dfc5181f48bfb3f7d3d7631d87fd9c17261c622")
      assert(convergence.failedCheckCode == BeautyQProtectedBreakGlassDisclosure.AuthorizedCheckCode)
      convergence.disclosureScope match {
        case BeautyQProtectedBreakGlassDisclosure.DisclosureScope.CompleteSlice(sliceId, expectedCount) =>
          assert(sliceId == "exact-intent")
          assert(expectedCount == 8)
        case other => fail(s"expected convergence full-slice authorization, got $other")
      }

      val fifth = BeautyQProtectedBreakGlassDisclosure.PostRecoveryAuthorization
      assert(fifth.id == BeautyQProtectedBreakGlassDisclosure.PostRecoveryAuthorizationId)
      assert(fifth.protectedCorpusFingerprint == "bd821a976622ad0157ec4c818f92187c60e08b2b5123f6306dea7878ef0aad17")
      assert(fifth.policyFingerprint == "5422773856685e4ff781b04c39b266c6ac5de06c0fadfb63f8d4f6bea3929755")
      assert(fifth.failedCheckCode == BeautyQProtectedBreakGlassDisclosure.AuthorizedCheckCode)
      fifth.disclosureScope match {
        case BeautyQProtectedBreakGlassDisclosure.DisclosureScope.CompleteSlice(sliceId, expectedCount) =>
          assert(sliceId == "exact-intent")
          assert(expectedCount == 8)
        case other => fail(s"expected post-recovery full-slice authorization, got $other")
      }

      val sixth = BeautyQProtectedBreakGlassDisclosure.RecoveryRotation2Authorization
      assert(sixth.id == BeautyQProtectedBreakGlassDisclosure.RecoveryRotation2AuthorizationId)
      assert(sixth.protectedCorpusFingerprint == "87002a0e79984365320b40f31f8cf4c76c7a4757d86ab3a4e1674819514651af")
      assert(sixth.policyFingerprint == "14781a4c2832374ee0f46540c8a532fc853d291841715ed816f12d0fa23c8045")
      assert(sixth.failedCheckCode == BeautyQProtectedBreakGlassDisclosure.AuthorizedCheckCode)
      sixth.disclosureScope match {
        case BeautyQProtectedBreakGlassDisclosure.DisclosureScope.CompleteSlice(sliceId, expectedCount) =>
          assert(sliceId == "exact-intent")
          assert(expectedCount == 8)
        case other => fail(s"expected recovery-rotation-2 full-slice authorization, got $other")
      }

      val seventh = BeautyQProtectedBreakGlassDisclosure.RecoveryRotation3Authorization
      assert(seventh.id == BeautyQProtectedBreakGlassDisclosure.RecoveryRotation3AuthorizationId)
      assert(seventh.protectedCorpusFingerprint == "f531e287027595a602fe97f44cae7d7cfbe2759be8f1b18d594d21ecbdb83f6e")
      assert(seventh.policyFingerprint == "c9c0677f25b45310376d0e2aa4c678a1e579a984f0dc8cd2e8ef0bba6990289b")
      assert(seventh.failedCheckCode == BeautyQProtectedBreakGlassDisclosure.AuthorizedCheckCode)
      seventh.disclosureScope match {
        case BeautyQProtectedBreakGlassDisclosure.DisclosureScope.CompleteSlice(sliceId, expectedCount) =>
          assert(sliceId == "exact-intent")
          assert(expectedCount == 8)
        case other => fail(s"expected recovery-rotation-3 full-slice authorization, got $other")
      }

      val eighth = BeautyQProtectedBreakGlassDisclosure.RecoveryRotation4Authorization
      assert(eighth.id == BeautyQProtectedBreakGlassDisclosure.RecoveryRotation4AuthorizationId)
      assert(eighth.protectedCorpusFingerprint == "2cf8cf77085faf5ab84d2eafd8b68e0020f64bf89df794f0419d8ce55684ef2f")
      assert(eighth.policyFingerprint == "30ab025069e175400dfdbd5f3c9e0a49dff23c1ac005537c3dc2114713c3fdb8")
      assert(eighth.failedCheckCode == BeautyQProtectedBreakGlassDisclosure.AuthorizedCheckCode)
      eighth.disclosureScope match {
        case BeautyQProtectedBreakGlassDisclosure.DisclosureScope.CompleteSlice(sliceId, expectedCount) =>
          assert(sliceId == "exact-intent")
          assert(expectedCount == 8)
        case other => fail(s"expected recovery-rotation-4 full-slice authorization, got $other")
      }

      val ninth = BeautyQProtectedBreakGlassDisclosure.RecoveryRotation5Authorization
      assert(ninth.id == BeautyQProtectedBreakGlassDisclosure.RecoveryRotation5AuthorizationId)
      assert(ninth.protectedCorpusFingerprint == "3893ad32b8bb8dc4a77df85ae40dd0537a32090111ae2ee2528e42090f9c1874")
      assert(ninth.policyFingerprint == "5a47394dcc6eb0e483c9998a9e481083522b3876c1ad8a084d683961a16d38b7")
      assert(ninth.failedCheckCode == BeautyQProtectedBreakGlassDisclosure.AuthorizedCheckCode)
      ninth.disclosureScope match {
        case BeautyQProtectedBreakGlassDisclosure.DisclosureScope.CompleteSlice(sliceId, expectedCount) =>
          assert(sliceId == "exact-intent")
          assert(expectedCount == 8)
        case other => fail(s"expected recovery-rotation-5 full-slice authorization, got $other")
      }

      val tenth = BeautyQProtectedBreakGlassDisclosure.RecoveryRotation6Authorization
      assert(tenth.id == BeautyQProtectedBreakGlassDisclosure.RecoveryRotation6AuthorizationId)
      assert(tenth.protectedCorpusFingerprint == "2f8757514fcc49d7c93db013717cb9cc093b0c28e20632cde94e7c698bfa48b8")
      assert(tenth.policyFingerprint == "40493e9fd4928fdb58b129c93e2460c15b681438ff9a89731acff87ecbcb3e24")
      assert(tenth.failedCheckCode == BeautyQProtectedBreakGlassDisclosure.AuthorizedCheckCode)
      tenth.disclosureScope match {
        case BeautyQProtectedBreakGlassDisclosure.DisclosureScope.CompleteSlice(sliceId, expectedCount) =>
          assert(sliceId == "exact-intent")
          assert(expectedCount == 8)
        case other => fail(s"expected recovery-rotation-6 full-slice authorization, got $other")
      }

      val eleventh = BeautyQProtectedBreakGlassDisclosure.RecoveryRotation7Authorization
      assert(eleventh.id == BeautyQProtectedBreakGlassDisclosure.RecoveryRotation7AuthorizationId)
      assert(eleventh.protectedCorpusFingerprint == "f6f99a1e3ef5c44a06b0e0253ad6871e039197bd065cee8b00454e0c39e0a1c2")
      assert(eleventh.policyFingerprint == "75a03540f448fecd2a620872fb01fde772996d37822f81a447ab93b48e0c7ee8")
      assert(eleventh.failedCheckCode == "metric-protected-slice:exact-intent-variants/success/10")
      eleventh.disclosureScope match {
        case BeautyQProtectedBreakGlassDisclosure.DisclosureScope.CompleteSlice(sliceId, expectedCount) =>
          assert(sliceId == "exact-intent")
          assert(expectedCount == 8)
        case other => fail(s"expected recovery-rotation-7 full-slice authorization, got $other")
      }

      val twelfth = BeautyQProtectedBreakGlassDisclosure.RecoveryRotation8Authorization
      assert(twelfth.id == BeautyQProtectedBreakGlassDisclosure.RecoveryRotation8AuthorizationId)
      assert(twelfth.id == "q2-break-glass-exact-intent-recovery-rotation-8-v1")
      assert(twelfth.protectedCorpusFingerprint == "ce020b49d28d4c1c45ee6abfc0959678a920e2fb6e2dc676e7b72525a272ac50")
      assert(twelfth.policyFingerprint == "89189e9400b870a86b50acfeb90a10b8cd31e66b913867ba05427cb826619c73")
      assert(twelfth.failedCheckCode == BeautyQProtectedBreakGlassDisclosure.AuthorizedCheckCode)
      twelfth.disclosureScope match {
        case BeautyQProtectedBreakGlassDisclosure.DisclosureScope.CompleteSlice(sliceId, expectedCount) =>
          assert(sliceId == "exact-intent")
          assert(expectedCount == 8)
        case other => fail(s"expected recovery-rotation-8 full-slice authorization, got $other")
      }

      assert(BeautyQProtectedBreakGlassDisclosure.Authorizations.size == 12)
      assert(BeautyQProtectedBreakGlassDisclosure.Authorizations.map(_.id).distinct.size == 12)

      assert(BeautyQProtectedBreakGlassDisclosure.authorizationById(ninth.id).contains(ninth))
      assert(BeautyQProtectedBreakGlassDisclosure.authorizationById(tenth.id).contains(tenth))
      assert(BeautyQProtectedBreakGlassDisclosure.authorizationById(eleventh.id).contains(eleventh))
      assert(BeautyQProtectedBreakGlassDisclosure.authorizationById(twelfth.id).contains(twelfth))
      assert(ninth.protectedCorpusFingerprint != tenth.protectedCorpusFingerprint)
      assert(ninth.policyFingerprint != tenth.policyFingerprint)
      assert(tenth.protectedCorpusFingerprint != eleventh.protectedCorpusFingerprint)
      assert(tenth.policyFingerprint != eleventh.policyFingerprint)
      assert(eleventh.protectedCorpusFingerprint != twelfth.protectedCorpusFingerprint)
      assert(eleventh.policyFingerprint != twelfth.policyFingerprint)

      val historical = BeautyQProtectedBreakGlassDisclosure.Authorizations.take(11).map(authorizationSnapshot)
      assert(historical == Vector(
        (BeautyQProtectedBreakGlassDisclosure.AuthorizationId, "825ca2862ad99b61002bcf04bfe000168eccc61760d9a1d091c0c4320afc9bb0", "0f86960495e64b430e2ba55eac012bf00a8e80f0eb8d1500c967d582cd673098", BeautyQProtectedBreakGlassDisclosure.AuthorizedCheckCode, "minimal", 0),
        (BeautyQProtectedBreakGlassDisclosure.Cycle2AuthorizationId, "7a654c7323f822f26bccc6d5ae7adde3faf4bfd790984ae63fb25c34978c9188", "905894412cb68ed8447ecc9c99ffe1ac9ee94e22001f6a8c206cdd81c9a165ce", BeautyQProtectedBreakGlassDisclosure.AuthorizedCheckCode, "minimal", 0),
        (BeautyQProtectedBreakGlassDisclosure.FullSliceCycle3AuthorizationId, "23fe801f0b1c48b77847a94d0eb260ee5e2faac5018c67bab5debce382b5f2d0", "c80f15f5cb7beb8ad5f01bec82146c67e17d8a3bc0a31e4e88a49fd115e9b360", BeautyQProtectedBreakGlassDisclosure.AuthorizedCheckCode, "exact-intent", 8),
        (BeautyQProtectedBreakGlassDisclosure.ConvergenceAuthorizationId, "d72b29d6d9e13e21b34722fa4c8219975aae7613ba0c003326baefc5da056a6e", "6a8a4f68f69d85467db941284dfc5181f48bfb3f7d3d7631d87fd9c17261c622", BeautyQProtectedBreakGlassDisclosure.AuthorizedCheckCode, "exact-intent", 8),
        (BeautyQProtectedBreakGlassDisclosure.PostRecoveryAuthorizationId, "bd821a976622ad0157ec4c818f92187c60e08b2b5123f6306dea7878ef0aad17", "5422773856685e4ff781b04c39b266c6ac5de06c0fadfb63f8d4f6bea3929755", BeautyQProtectedBreakGlassDisclosure.AuthorizedCheckCode, "exact-intent", 8),
        (BeautyQProtectedBreakGlassDisclosure.RecoveryRotation2AuthorizationId, "87002a0e79984365320b40f31f8cf4c76c7a4757d86ab3a4e1674819514651af", "14781a4c2832374ee0f46540c8a532fc853d291841715ed816f12d0fa23c8045", BeautyQProtectedBreakGlassDisclosure.AuthorizedCheckCode, "exact-intent", 8),
        (BeautyQProtectedBreakGlassDisclosure.RecoveryRotation3AuthorizationId, "f531e287027595a602fe97f44cae7d7cfbe2759be8f1b18d594d21ecbdb83f6e", "c9c0677f25b45310376d0e2aa4c678a1e579a984f0dc8cd2e8ef0bba6990289b", BeautyQProtectedBreakGlassDisclosure.AuthorizedCheckCode, "exact-intent", 8),
        (BeautyQProtectedBreakGlassDisclosure.RecoveryRotation4AuthorizationId, "2cf8cf77085faf5ab84d2eafd8b68e0020f64bf89df794f0419d8ce55684ef2f", "30ab025069e175400dfdbd5f3c9e0a49dff23c1ac005537c3dc2114713c3fdb8", BeautyQProtectedBreakGlassDisclosure.AuthorizedCheckCode, "exact-intent", 8),
        (BeautyQProtectedBreakGlassDisclosure.RecoveryRotation5AuthorizationId, "3893ad32b8bb8dc4a77df85ae40dd0537a32090111ae2ee2528e42090f9c1874", "5a47394dcc6eb0e483c9998a9e481083522b3876c1ad8a084d683961a16d38b7", BeautyQProtectedBreakGlassDisclosure.AuthorizedCheckCode, "exact-intent", 8),
        (BeautyQProtectedBreakGlassDisclosure.RecoveryRotation6AuthorizationId, "2f8757514fcc49d7c93db013717cb9cc093b0c28e20632cde94e7c698bfa48b8", "40493e9fd4928fdb58b129c93e2460c15b681438ff9a89731acff87ecbcb3e24", BeautyQProtectedBreakGlassDisclosure.AuthorizedCheckCode, "exact-intent", 8),
        (BeautyQProtectedBreakGlassDisclosure.RecoveryRotation7AuthorizationId, "f6f99a1e3ef5c44a06b0e0253ad6871e039197bd065cee8b00454e0c39e0a1c2", "75a03540f448fecd2a620872fb01fde772996d37822f81a447ab93b48e0c7ee8", BeautyQProtectedBreakGlassDisclosure.AuthorizedCheckCode, "exact-intent", 8),
      ))

      val fixture = syntheticFixture()
      assertLeft(BeautyQProtectedBreakGlassDisclosure.derive(
        failedResult(), fixture.report, fixture.corpus, fixture.policy,
        BeautyQProtectedBreakGlassDisclosure.Cycle2AuthorizationId,
        BeautyQProtectedBreakGlassDisclosure.AuthorizedCheckCode,
      ), "break_glass_authorization_corpus_mismatch")
      assertLeft(BeautyQProtectedBreakGlassDisclosure.derive(
        failedResult(), fixture.report, fixture.corpus, fixture.policy,
        seventh.id, BeautyQProtectedBreakGlassDisclosure.AuthorizedCheckCode,
      ), "break_glass_authorization_corpus_mismatch")
      assertLeft(BeautyQProtectedBreakGlassDisclosure.derive(
        failedResult(), fixture.report, fixture.corpus, fixture.policy,
        eleventh.id, BeautyQProtectedBreakGlassDisclosure.AuthorizedCheckCode,
      ), "break_glass_authorization_corpus_mismatch")
      assertLeft(BeautyQProtectedBreakGlassDisclosure.derive(
        failedResult(), fixture.report, fixture.corpus, fixture.policy,
        twelfth.id, BeautyQProtectedBreakGlassDisclosure.AuthorizedCheckCode,
      ), "break_glass_authorization_corpus_mismatch")
    }

    "disclose the complete authorized slice while recursively excluding every other slice" in {
      val fixture = syntheticFixture()
      val authorization = new BeautyQProtectedBreakGlassDisclosure.AuthorizationRecord(
        "synthetic-full-slice",
        fixture.corpus.corpusFingerprint,
        fixture.policy.fingerprint,
        BeautyQProtectedBreakGlassDisclosure.AuthorizedCheckCode,
        BeautyQProtectedBreakGlassDisclosure.DisclosureScope.CompleteSlice("exact-intent", expectedCount = 2),
      )
      val disclosure = BeautyQProtectedBreakGlassDisclosure.deriveAuthorized(
        failedResult(), fixture.report, fixture.corpus, fixture.policy,
        authorization, BeautyQProtectedBreakGlassDisclosure.AuthorizedCheckCode,
      ).fold(error => fail(error), identity)

      assert(disclosure.disclosedCases.map(_.corpusCase.caseId.value) == Vector("protected-failing", "protected-passing"))
      val encoded = disclosure.toJson.noSpaces
      assert(encoded.contains("protected-failing"))
      assert(encoded.contains("protected-passing"))
      assert(!encoded.contains("protected-other-slice"))
      assert(!encoded.contains("other-slice-sentinel"))
      assert(!encoded.contains("accepted-baseline"))
      assert(!encoded.contains("canonical"))
    }

    "reject a full-slice authorization whose expected disclosure count or slice differs" in {
      val fixture = syntheticFixture()
      val wrongCount = new BeautyQProtectedBreakGlassDisclosure.AuthorizationRecord(
        "wrong-count", fixture.corpus.corpusFingerprint, fixture.policy.fingerprint,
        BeautyQProtectedBreakGlassDisclosure.AuthorizedCheckCode,
        BeautyQProtectedBreakGlassDisclosure.DisclosureScope.CompleteSlice("exact-intent", expectedCount = 8),
      )
      val wrongSlice = new BeautyQProtectedBreakGlassDisclosure.AuthorizationRecord(
        "wrong-slice", fixture.corpus.corpusFingerprint, fixture.policy.fingerprint,
        BeautyQProtectedBreakGlassDisclosure.AuthorizedCheckCode,
        BeautyQProtectedBreakGlassDisclosure.DisclosureScope.CompleteSlice("other", expectedCount = 2),
      )

      assertLeft(BeautyQProtectedBreakGlassDisclosure.deriveAuthorized(
        failedResult(), fixture.report, fixture.corpus, fixture.policy,
        wrongCount, BeautyQProtectedBreakGlassDisclosure.AuthorizedCheckCode,
      ), "break_glass_authorization_disclosed_count_mismatch")
      assertLeft(BeautyQProtectedBreakGlassDisclosure.deriveAuthorized(
        failedResult(), fixture.report, fixture.corpus, fixture.policy,
        wrongSlice, BeautyQProtectedBreakGlassDisclosure.AuthorizedCheckCode,
      ), "break_glass_authorization_slice_mismatch")
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
    BeautyQProtectedBreakGlassDisclosure.deriveAuthorized(
      acceptance,
      fixture.report,
      fixture.corpus,
      fixture.policy,
      new BeautyQProtectedBreakGlassDisclosure.AuthorizationRecord(
        "synthetic-authorization",
        fixture.corpus.corpusFingerprint,
        fixture.policy.fingerprint,
        BeautyQProtectedBreakGlassDisclosure.AuthorizedCheckCode,
        BeautyQProtectedBreakGlassDisclosure.DisclosureScope.MinimalContributors,
      ),
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

  private def authorizationSnapshot(
    authorization: BeautyQProtectedBreakGlassDisclosure.AuthorizationRecord,
  ): (String, String, String, String, String, Int) = {
    authorization.disclosureScope match {
      case BeautyQProtectedBreakGlassDisclosure.DisclosureScope.MinimalContributors =>
        (authorization.id, authorization.protectedCorpusFingerprint, authorization.policyFingerprint, authorization.failedCheckCode, "minimal", 0)
      case BeautyQProtectedBreakGlassDisclosure.DisclosureScope.CompleteSlice(sliceId, expectedCount) =>
        (authorization.id, authorization.protectedCorpusFingerprint, authorization.policyFingerprint, authorization.failedCheckCode, sliceId, expectedCount)
    }
  }
}
