package leaderboard.search

import io.circe.Json
import leaderboard.search.beautyq.gen2.eval.{
  BeautyQEvaluationCorpus,
  BeautyQEvaluationPolicy,
  BeautyQProtectedAcceptancePolicy,
  BeautyQProtectedInputAudit,
}
import org.scalatest.wordspec.AnyWordSpec

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}

final class BeautyQProtectedInputFreezeMainSpec extends AnyWordSpec {
  "BeautyQProtectedInputFreezeMain" should {
    "parse the exact nine options" in {
      parse(validArguments()) match {
        case Right(arguments) =>
          assert(arguments.protectedCorpus.toString == "target/codex-sbt/freeze-corpus.json")
          assert(arguments.protectedPolicy.toString == "target/codex-sbt/freeze-policy.json")
          assert(arguments.authorDraft.toString == "target/codex-sbt/freeze-author.json")
          assert(arguments.judgedDraft.toString == "target/codex-sbt/freeze-judged.json")
          assert(arguments.sourceRevision == "a" * 40)
        case Left(error) => fail(s"expected valid arguments, got $error")
      }
    }

    "reject missing, duplicate, unknown, positional, blank and padded arguments" in {
      assert(parse(validArguments().dropRight(2)).isLeft)
      assert(parse(validArguments().updated(2, "--protected-corpus")).isLeft)
      assert(parse(validArguments().updated(0, "--unknown")).isLeft)
      assert(parse(validArguments().updated(0, "positional")).isLeft)
      assert(parse(validArguments().updated(1, "")).isLeft)
      assert(parse(validArguments().updated(1, " padded ")).isLeft)
    }

    "reject invalid revision and identical pass identities" in {
      assert(parse(replaceValue(validArguments(), "--source-revision", "bad")).isLeft)
      assert(parse(replaceValue(validArguments(), "--judge-pass-id", "author-pass")).isLeft)
    }

    "normalize query text with NFKC, root lowercase and collapsed whitespace" in {
      val composed = "  ＧＥＬ\tNAILS  "
      assert(BeautyQProtectedInputFreezeMain.normalizeQuery(composed) == "gel nails")
    }

    "count protected duplicate cases after the first occurrence" in {
      assert(BeautyQProtectedInputFreezeMain.internalDuplicateCountForTest(Vector("A", "A", "A"), normalized = false) == 2)
      assert(BeautyQProtectedInputFreezeMain.internalDuplicateCountForTest(Vector("  Ａ", "a", "Ａ  "), normalized = true) == 2)
      assert(BeautyQProtectedInputFreezeMain.internalDuplicateCountForTest(Vector("A", "B"), normalized = false) == 0)
    }

    "reject an existing audit output" in {
      val fixture = fixturePaths("existing")
      prepareFixture(fixture)
      Files.writeString(fixture.audit, "existing", StandardCharsets.UTF_8)
      try {
        val arguments = parseOrFail(argumentsFor(fixture))
        assert(BeautyQProtectedInputFreezeMain.freezeAt(arguments, fixture.audit, () => throw new AssertionError("revision reader must not run")) == Left("audit_output_already_exists"))
      } finally cleanup(fixture)
    }

    "write one strict aggregate-only audit without executing search resources" in {
      val fixture = fixturePaths("success")
      prepareFixture(fixture)
      try {
        val arguments = parseOrFail(argumentsFor(fixture))
        BeautyQProtectedInputFreezeMain.freezeAt(arguments, fixture.audit, () => Right(arguments.sourceRevision)) match {
          case Right(summary) =>
            assert(Files.isRegularFile(fixture.audit))
            assert(summary.protectedCaseCount == 1)
            assert(summary.successLine.startsWith("PROTECTED_INPUTS_FROZEN"))
            assert(!summary.successLine.contains("private-freeze-sentinel-query"))
            val encoded = Files.readString(fixture.audit, StandardCharsets.UTF_8)
            BeautyQProtectedInputAudit.decodeString(encoded) match {
              case Right(audit) =>
                assert(audit.frozen)
                assert(audit.exactVisibleQueryDuplicateCount == 0)
                assert(audit.normalizedVisibleQueryDuplicateCount == 0)
                assert(audit.visibleCaseIdOverlapCount == 0)
              case Left(error) => fail(s"expected strict audit, got ${error.stableCode}")
            }
            assert(!encoded.contains("private-freeze-sentinel-query"))
            assert(!encoded.contains("private-freeze-sentinel-id"))
          case Left(error) => fail(s"expected successful freeze, got $error")
        }
      } finally cleanup(fixture)
    }

    "reject a missing or empty author/judge draft" in {
      val missing = fixturePaths("missing-draft")
      prepareFixture(missing)
      try {
        Files.deleteIfExists(missing.authorDraft): Unit
        val arguments = parseOrFail(argumentsFor(missing))
        assert(BeautyQProtectedInputFreezeMain.freezeAt(arguments, missing.audit, () => Right(arguments.sourceRevision)) == Left("author_draft_invalid"))
      } finally cleanup(missing)

      val empty = fixturePaths("empty-draft")
      prepareFixture(empty)
      try {
        Files.writeString(empty.judgedDraft, "", StandardCharsets.UTF_8)
        val arguments = parseOrFail(argumentsFor(empty))
        assert(BeautyQProtectedInputFreezeMain.freezeAt(arguments, empty.audit, () => Right(arguments.sourceRevision)) == Left("judged_draft_invalid"))
      } finally cleanup(empty)
    }

    "require the declared revision to match the supplied current-revision seam" in {
      val fixture = fixturePaths("revision")
      prepareFixture(fixture)
      try {
        val arguments = parseOrFail(argumentsFor(fixture))
        assert(BeautyQProtectedInputFreezeMain.freezeAt(arguments, fixture.audit, () => Right("b" * 40)) == Left("source_revision_mismatch"))
        assert(BeautyQProtectedInputFreezeMain.freezeAt(arguments, fixture.audit, () => Right("not-a-revision")) == Left("invalid_current_revision"))
        assert(BeautyQProtectedInputFreezeMain.freezeAt(arguments, fixture.audit, () => Left("source_revision_unavailable")) == Left("source_revision_unavailable"))
      } finally cleanup(fixture)
    }

    "bind each judgment surface to the canonical typed catalog" in {
      val valid = decodeCorpus(protectedCorpusJson())
      BeautyQProtectedInputFreezeMain.catalogValidationForTest(valid) match {
        case Right(counts) =>
          assert(counts.invalidVariantJudgmentIdentityCount == 0)
          assert(counts.invalidProviderJudgmentIdentityCount == 0)
          assert(counts.invalidServiceIntentJudgmentCount == 0)
          assert(counts.exactIntentCaseCount == 1)
          assert(counts.exactIntentWithoutAcceptableVariantCount == 0)
        case Left(error) => fail(s"expected canonical catalog, got $error")
      }

      val unknownVariant = decodeCorpus(protectedCorpusJson(variantId = Some("ffffffff-ffff-ffff-ffff-ffffffffffff")))
      val crossSurface = decodeCorpus(protectedCorpusJson(providerId = Some("1fcd6e17-c6bb-5901-9f63-205668897659")))
      val unknownService = decodeCorpus(protectedCorpusJson(serviceIntentId = Some("ffffffff-ffff-ffff-ffff-ffffffffffff")))
      BeautyQProtectedInputFreezeMain.catalogValidationForTest(unknownVariant) match {
        case Right(counts) => assert(counts.invalidVariantJudgmentIdentityCount == 1)
        case Left(error) => fail(s"expected canonical catalog, got $error")
      }
      BeautyQProtectedInputFreezeMain.catalogValidationForTest(crossSurface) match {
        case Right(counts) => assert(counts.invalidProviderJudgmentIdentityCount == 1)
        case Left(error) => fail(s"expected canonical catalog, got $error")
      }
      BeautyQProtectedInputFreezeMain.catalogValidationForTest(unknownService) match {
        case Right(counts) => assert(counts.invalidServiceIntentJudgmentCount == 1)
        case Left(error) => fail(s"expected canonical catalog, got $error")
      }
      val missingVariant = decodeCorpus(protectedCorpusJson(variantId = None))
      BeautyQProtectedInputFreezeMain.catalogValidationForTest(missingVariant) match {
        case Right(counts) => assert(counts.exactIntentWithoutAcceptableVariantCount == 1)
        case Left(error) => fail(s"expected canonical catalog, got $error")
      }
    }

    "accept valid provider and service identities only on their declared surfaces" in {
      BeautyQCanonicalSeedEvaluationCatalog.load() match {
        case Right(catalog) =>
          val providerId = firstResultId(catalog.providerResultIds)
          val serviceId = firstResultId(catalog.serviceIntentResultIds)
          val masterId = catalog.snapshot.masters.map(_.id.value.toString).sortBy(identity).headOption match {
            case Some(value) => value
            case None => fail("expected a non-empty master inventory")
          }
          BeautyQProtectedInputFreezeMain.catalogValidationForTest(decodeCorpus(protectedCorpusJson(providerId = Some(providerId)))) match {
            case Right(counts) =>
              assert(counts.invalidProviderJudgmentIdentityCount == 0)
              assert(counts.exactIntentWithoutAcceptableVariantCount == 0)
            case Left(error) => fail(s"expected valid provider identity, got $error")
          }
          BeautyQProtectedInputFreezeMain.catalogValidationForTest(decodeCorpus(protectedCorpusJson(serviceIntentId = Some(serviceId)))) match {
            case Right(counts) =>
              assert(counts.invalidServiceIntentJudgmentCount == 0)
              assert(counts.exactIntentWithoutAcceptableVariantCount == 0)
            case Left(error) => fail(s"expected valid service identity, got $error")
          }
          BeautyQProtectedInputFreezeMain.catalogValidationForTest(decodeCorpus(protectedCorpusJson(providerId = Some(masterId)))) match {
            case Right(counts) => assert(counts.invalidProviderJudgmentIdentityCount == 1)
            case Left(error) => fail(s"expected cross-surface master identity failure, got $error")
          }
        case Left(error) => fail(s"expected canonical catalog, got $error")
      }
    }
  }

  private final class FixturePaths(val corpus: Path, val policy: Path, val authorDraft: Path, val judgedDraft: Path, val audit: Path)

  private def fixturePaths(name: String): FixturePaths = {
    val directory = Paths.get("target/codex-sbt", s"protected-freeze-$name")
    new FixturePaths(
      directory.resolve("corpus.json"),
      directory.resolve("policy.json"),
      directory.resolve("author-draft.json"),
      directory.resolve("judged-draft.json"),
      directory.resolve("audit.json"),
    )
  }

  private def prepareFixture(paths: FixturePaths): Unit = {
    Files.createDirectories(paths.corpus.getParent)
    val corpusJson = protectedCorpusJson()
    val corpus = BeautyQEvaluationCorpus.decodeFromJson(corpusJson) match {
      case Right(value) => value
      case Left(error) => fail(s"expected valid protected fixture, got $error")
    }
    val policyJson = Json.obj(
      "schemaVersion" -> Json.fromString(BeautyQProtectedAcceptancePolicy.CurrentSchemaVersion),
      "evaluationPolicyVersion" -> Json.fromString(BeautyQEvaluationPolicy.CurrentVersion),
      "protectedAcceptancePolicyVersion" -> Json.fromString("beautyq-protected-acceptance-policy-v1"),
      "expectedCorpusFingerprint" -> Json.fromString(corpus.corpusFingerprint),
      "expectedCaseCount" -> Json.fromInt(1),
      "requiredSliceMinimums" -> Json.arr(Json.obj(
        "sliceId" -> Json.fromString("exact-intent"),
        "minimumCaseCount" -> Json.fromInt(1),
      )),
      "requiredMetricMinimums" -> Json.arr(Json.obj(
        "observationKey" -> Json.fromString("protected-slice:exact-intent"),
        "surface" -> Json.fromString("variants"),
        "metric" -> Json.fromString("success"),
        "cutoff" -> Json.fromInt(10),
        "minimum" -> Json.fromString("1.000000000000"),
      )),
    )
    Files.writeString(paths.corpus, BeautyQEvaluationCorpus.canonicalJson(corpus).noSpaces + "\n", StandardCharsets.UTF_8)
    val policy = BeautyQProtectedAcceptancePolicy.fromJson(policyJson) match {
      case Right(value) => value
      case Left(error) => fail(s"expected valid policy fixture, got $error")
    }
    Files.writeString(paths.policy, policy.canonicalJson.noSpaces + "\n", StandardCharsets.UTF_8)
    Files.writeString(paths.authorDraft, "author draft", StandardCharsets.UTF_8)
    Files.writeString(paths.judgedDraft, "judged draft", StandardCharsets.UTF_8)
    Files.deleteIfExists(paths.audit): Unit
  }

  private def protectedCorpusJson(
    variantId: Option[String] = Some("1fcd6e17-c6bb-5901-9f63-205668897659"),
    providerId: Option[String] = None,
    serviceIntentId: Option[String] = None,
  ): Json = Json.obj(
    "schemaVersion" -> Json.fromString("beautyq-evaluation-corpus-v2"),
    "corpusId" -> Json.fromString("freeze-fixture"),
    "dataset" -> Json.fromString("fixture"),
    "version" -> Json.fromInt(1),
    "defaultUserLocation" -> Json.obj(
      "label" -> Json.fromString("fixture"),
      "lat" -> Json.fromDoubleOrNull(53.57),
      "lon" -> Json.fromDoubleOrNull(10.06),
    ),
    "cases" -> Json.arr(Json.obj(
      "id" -> Json.fromString("private-freeze-sentinel-id"),
      "partition" -> Json.fromString("protected-holdout"),
      "judgmentMode" -> Json.fromString("partial"),
      "query" -> Json.fromString("private-freeze-sentinel-query"),
      "language" -> Json.fromString("en"),
      "slices" -> Json.arr(Json.fromString("exact-intent")),
      "userIntent" -> Json.fromString("fixture"),
      "notes" -> Json.arr(),
      "judgments" -> Json.obj(
      "variants" -> variantId.map(variantJudgments).getOrElse(emptyJudgments),
      "providers" -> providerId.map(singleJudgment).getOrElse(emptyJudgments),
      "serviceIntents" -> serviceIntentId.map(singleJudgment).getOrElse(emptyJudgments),
      ),
    )),
  )

  private def decodeCorpus(json: Json): BeautyQEvaluationCorpus =
    BeautyQEvaluationCorpus.decodeFromJson(json) match {
      case Right(value) => value
      case Left(error) => fail(s"expected valid corpus fixture, got $error")
    }

  private def firstResultId(values: Set[leaderboard.search.gen2.eval.EvaluationResultId]): String =
    values.toVector.sortBy(_.value).headOption match {
      case Some(value) => value.value
      case None => fail("expected a non-empty canonical identity inventory")
    }

  private def emptyJudgments: Json = Json.obj(
    "acceptableIds" -> Json.arr(),
    "forbiddenIds" -> Json.arr(),
    "neutralIds" -> Json.arr(),
    "gradedGains" -> Json.arr(),
  )

  private def variantJudgments(id: String): Json = Json.obj(
    "acceptableIds" -> Json.arr(Json.fromString(id)),
    "forbiddenIds" -> Json.arr(),
    "neutralIds" -> Json.arr(),
    "gradedGains" -> Json.arr(),
  )

  private def singleJudgment(id: String): Json = Json.obj(
    "acceptableIds" -> Json.arr(Json.fromString(id)),
    "forbiddenIds" -> Json.arr(),
    "neutralIds" -> Json.arr(),
    "gradedGains" -> Json.arr(),
  )

  private def validArguments(): Vector[String] = Vector(
    "--protected-corpus", "target/codex-sbt/freeze-corpus.json",
    "--protected-policy", "target/codex-sbt/freeze-policy.json",
    "--author-draft", "target/codex-sbt/freeze-author.json",
    "--judged-draft", "target/codex-sbt/freeze-judged.json",
    "--audit-output", "target/codex-sbt/freeze-audit.json",
    "--source-revision", "a" * 40,
    "--author-pass-id", "author-pass",
    "--judge-pass-id", "judge-pass",
    "--audit-pass-id", "audit-pass",
  )

  private def argumentsFor(paths: FixturePaths): Vector[String] = Vector(
    "--protected-corpus", paths.corpus.toString,
    "--protected-policy", paths.policy.toString,
    "--author-draft", paths.authorDraft.toString,
    "--judged-draft", paths.judgedDraft.toString,
    "--audit-output", paths.audit.toString,
    "--source-revision", "a" * 40,
    "--author-pass-id", "author-pass",
    "--judge-pass-id", "judge-pass",
    "--audit-pass-id", "audit-pass",
  )

  private def replaceValue(arguments: Vector[String], key: String, replacement: String): Vector[String] =
    arguments.grouped(2).flatMap {
      case Vector(actualKey, _) if actualKey == key => Vector(actualKey, replacement)
      case pair => pair
    }.toVector

  private def parse(arguments: Vector[String]): Either[String, BeautyQProtectedInputFreezeMain.Arguments] =
    BeautyQProtectedInputFreezeMain.parseArguments(arguments)

  private def parseOrFail(arguments: Vector[String]): BeautyQProtectedInputFreezeMain.Arguments =
    parse(arguments) match {
      case Right(value) => value
      case Left(error) => fail(s"expected valid arguments, got $error")
    }

  private def cleanup(paths: FixturePaths): Unit = {
    Files.deleteIfExists(paths.audit): Unit
    Files.deleteIfExists(paths.policy): Unit
    Files.deleteIfExists(paths.authorDraft): Unit
    Files.deleteIfExists(paths.judgedDraft): Unit
    Files.deleteIfExists(paths.corpus): Unit
    Files.deleteIfExists(paths.corpus.getParent): Unit
  }
}
