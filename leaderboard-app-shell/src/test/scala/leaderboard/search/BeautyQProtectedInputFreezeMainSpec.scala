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
  private lazy val testRoot = repositoryRoot(Paths.get(".").toAbsolutePath.normalize)

  "BeautyQProtectedInputFreezeMain" should {
    "parse the exact eight options" in {
      parse(validArguments()) match {
        case Right(arguments) =>
          assert(arguments.protectedCorpus.toString == "target/codex-sbt/freeze-corpus.json")
          assert(arguments.protectedPolicy.toString == "target/codex-sbt/freeze-policy.json")
          assert(arguments.authorDraft.toString == "target/codex-sbt/freeze-author.json")
          assert(arguments.judgedDraft.toString == "target/codex-sbt/freeze-judged.json")
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

    "reject identical pass identities" in {
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
        assert(BeautyQProtectedInputFreezeMain.freezeAt(arguments, testRoot) == Left("audit_output_already_exists"))
      } finally cleanup(fixture)
    }

    "write one strict aggregate-only audit without executing search resources" in {
      val fixture = fixturePaths("success")
      prepareFixture(fixture)
      try {
        val arguments = parseOrFail(argumentsFor(fixture))
        BeautyQProtectedInputFreezeMain.freezeAt(arguments, testRoot) match {
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
        assert(BeautyQProtectedInputFreezeMain.freezeAt(arguments, testRoot) == Left("author_draft_invalid"))
      } finally cleanup(missing)

      val empty = fixturePaths("empty-draft")
      prepareFixture(empty)
      try {
        Files.writeString(empty.judgedDraft, "", StandardCharsets.UTF_8)
        val arguments = parseOrFail(argumentsFor(empty))
        assert(BeautyQProtectedInputFreezeMain.freezeAt(arguments, testRoot) == Left("judged_draft_invalid"))
      } finally cleanup(empty)
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

    "validate a catalog-bound reserve candidate and reject cross-surface or invented identities" in {
      BeautyQCanonicalSeedEvaluationCatalog.load() match {
        case Right(catalog) =>
          val variantId = firstResultId(catalog.variantResultIds)
          val providerId = firstResultId(catalog.providerResultIds)
          val serviceIntentId = firstResultId(catalog.serviceIntentResultIds)

          val validVariantCorpus = decodeCorpus(protectedCorpusJson(variantId = Some(variantId)))
          val validProviderCorpus = decodeCorpus(protectedCorpusJson(providerId = Some(providerId)))
          val validServiceCorpus = decodeCorpus(protectedCorpusJson(serviceIntentId = Some(serviceIntentId)))
          assert(BeautyQProtectedInputFreezeMain.catalogValidationForTest(validVariantCorpus).isRight)
          assert(BeautyQProtectedInputFreezeMain.catalogValidationForTest(validProviderCorpus).isRight)
          assert(BeautyQProtectedInputFreezeMain.catalogValidationForTest(validServiceCorpus).isRight)

          val inventedVariant = decodeCorpus(protectedCorpusJson(variantId = Some("ffffffff-ffff-ffff-ffff-ffffffffffff")))
          BeautyQProtectedInputFreezeMain.catalogValidationForTest(inventedVariant) match {
            case Right(counts) => assert(counts.invalidVariantJudgmentIdentityCount == 1)
            case Left(error) => fail(s"expected catalog validation, got $error")
          }

          val providerOnVariant = decodeCorpus(protectedCorpusJson(variantId = Some(providerId)))
          BeautyQProtectedInputFreezeMain.catalogValidationForTest(providerOnVariant) match {
            case Right(counts) => assert(counts.invalidVariantJudgmentIdentityCount == 1)
            case Left(error) => fail(s"expected catalog validation, got $error")
          }

          val variantOnProvider = decodeCorpus(protectedCorpusJson(providerId = Some(variantId)))
          BeautyQProtectedInputFreezeMain.catalogValidationForTest(variantOnProvider) match {
            case Right(counts) => assert(counts.invalidProviderJudgmentIdentityCount == 1)
            case Left(error) => fail(s"expected catalog validation, got $error")
          }

        case Left(error) => fail(s"expected canonical catalog, got $error")
      }
    }

    "reproduce the tracked Q2-I freeze audit from canonical test resources" in {
      val root = repositoryRoot(Paths.get(".").toAbsolutePath.normalize)
      val resourceRoot = root.resolve(
        "beautyq-search-gen2-eval/src/test/resources/leaderboard/search/beautyq/gen2/eval/protected"
      )
      val corpusPath = resourceRoot.resolve("beautyq-protected-holdout-v1.json")
      val policyPath = resourceRoot.resolve("beautyq-protected-acceptance-policy-v1.json")
      val auditPath = resourceRoot.resolve("beautyq-protected-input-audit-v2.json")
      val authorDraftPath = resourceRoot.resolve("provenance/beautyq-protected-author-draft-v1.json")
      val judgedDraftPath = resourceRoot.resolve("provenance/beautyq-protected-judged-draft-v1.json")
      val canonicalFiles = Vector(corpusPath, policyPath, auditPath, authorDraftPath, judgedDraftPath)
      canonicalFiles.foreach { path =>
        assert(Files.isRegularFile(path), "expected canonical protected resource")
        assert(Files.size(path) > 0L, "expected non-empty canonical protected resource")
      }

      val corpus = decodeCanonicalCorpus(corpusPath)
      val policy = BeautyQProtectedAcceptancePolicy.load(policyPath) match {
        case Right(value) => value
        case Left(_) => fail("expected canonical protected policy to decode")
      }
      val audit = decodeCanonicalAudit(auditPath)
      assert(corpus.cases.size == 24)
      assert(policy.expectedCaseCount == corpus.cases.size)
      assert(audit.frozen)

      val temporaryDirectory = root.resolve(".evidence-runs/q2-freeze/protected-freeze-canonical")
      val temporaryAudit = temporaryDirectory.resolve("beautyq-protected-input-audit-v2.json")
      Files.createDirectories(temporaryDirectory)
      Files.deleteIfExists(temporaryAudit): Unit
      try {
        val arguments = new BeautyQProtectedInputFreezeMain.Arguments(
          corpusPath,
          policyPath,
          authorDraftPath,
          judgedDraftPath,
          temporaryAudit,
          audit.authorPassId,
          audit.judgePassId,
          audit.auditPassId,
        )
        BeautyQProtectedInputFreezeMain.freezeAt(
          arguments,
          root,
        ) match {
          case Right(summary) =>
            val expectedBytes = Files.readAllBytes(auditPath).toVector
            val actualBytes = Files.readAllBytes(temporaryAudit).toVector
            assert(actualBytes == expectedBytes, "tracked aggregate audit must be reproducible")
            assert(summary.protectedCaseCount == audit.protectedCaseCount)
          case Left(_) => fail("expected tracked protected inputs to reproduce their freeze audit")
        }
      } finally {
        Files.deleteIfExists(temporaryAudit): Unit
        Files.deleteIfExists(temporaryDirectory): Unit
      }
    }
  }

  private final class FixturePaths(val corpus: Path, val policy: Path, val authorDraft: Path, val judgedDraft: Path, val audit: Path)

  private def fixturePaths(name: String): FixturePaths = {
    val directory = Paths.get(".evidence-runs", "q2-freeze", name)
    new FixturePaths(
      directory.resolve("corpus.json"),
      directory.resolve("policy.json"),
      directory.resolve("author-draft.json"),
      directory.resolve("judged-draft.json"),
      directory.resolve("beautyq-protected-input-audit-v2.json"),
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
    Files.writeString(paths.authorDraft, authorDraftJson().noSpaces + "\n", StandardCharsets.UTF_8)
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

  private def authorDraftJson(): Json = Json.obj(
    "schemaVersion" -> Json.fromString("beautyq-protected-author-draft-v1"),
    "authorPassId" -> Json.fromString("author-pass"),
    "cases" -> Json.arr(Json.obj(
      "id" -> Json.fromString("private-freeze-sentinel-id"),
      "query" -> Json.fromString("private-freeze-sentinel-query"),
      "language" -> Json.fromString("en"),
      "primarySlice" -> Json.fromString("exact-intent"),
      "additionalSlices" -> Json.arr(),
      "userIntent" -> Json.fromString("fixture"),
      "notes" -> Json.arr(),
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

  private def repositoryRoot(start: Path): Path = {
    if (Files.isRegularFile(start.resolve("build.sbt"))) start
    else Option(start.getParent) match {
      case Some(parent) => repositoryRoot(parent)
      case None => fail("repository root with build.sbt was not found")
    }
  }

  private def decodeCanonicalCorpus(path: Path): BeautyQEvaluationCorpus = {
    val json = io.circe.parser.parse(Files.readString(path, StandardCharsets.UTF_8)) match {
      case Right(value) => value
      case Left(_) => fail("expected canonical protected corpus JSON")
    }
    BeautyQEvaluationCorpus.decodeFromJson(json) match {
      case Right(value) => value
      case Left(_) => fail("expected canonical protected corpus to decode")
    }
  }

  private def decodeCanonicalAudit(path: Path): BeautyQProtectedInputAudit = {
    BeautyQProtectedInputAudit.decodeString(Files.readString(path, StandardCharsets.UTF_8)) match {
      case Right(value) => value
      case Left(_) => fail("expected canonical protected audit to decode")
    }
  }
}
