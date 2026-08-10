package leaderboard.search

import io.circe.Json
import leaderboard.search.beautyq.gen2.eval.BeautyQAcceptedEvaluationBaselineResource
import leaderboard.search.beautyq.gen2.eval.BeautyQProtectedAcceptancePolicy
import leaderboard.search.gen2.eval.{AcceptedBaselineCodec, AcceptedEvaluationBaseline, EvaluationProvenanceId, ProvenanceComponent}
import org.scalatest.wordspec.AnyWordSpec

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}

final class BeautyQAcceptedBaselineMainSpec extends AnyWordSpec {
  "BeautyQAcceptedBaselineMain" should {
    "accept each valid mode and all four required input values" in {
      val parsed = BeautyQAcceptedBaselineMain.parseArguments(Vector(
        "--mode", "bootstrap",
        "--protected-corpus", "private/corpus.json",
        "--protected-policy", "private/policy.json",
        "--output-dir", "target/out",
        "--application-revision", "commit-visible-123",
      ))
      parsed match {
        case Right(arguments) =>
          assert(arguments.mode == "bootstrap")
          assert(arguments.protectedCorpus.toString == "private/corpus.json")
          assert(arguments.protectedPolicy.toString == "private/policy.json")
          assert(arguments.outputDir.toString == "target/out")
          assert(arguments.applicationRevision == "commit-visible-123")
        case Left(error) => fail(s"expected valid arguments, got $error")
      }
      val verify = BeautyQAcceptedBaselineMain.parseArguments(Vector(
        "--mode", "verify",
        "--protected-corpus", "private/corpus.json",
        "--protected-policy", "private/policy.json",
        "--output-dir", "target/out",
        "--application-revision", "commit-visible-123",
      ))
      verify match {
        case Right(arguments) => assert(arguments.mode == "verify")
        case Left(error) => fail(s"expected valid verify arguments, got $error")
      }
    }

    "reject unknown, duplicate, missing and unsupported mode arguments" in {
      assert(BeautyQAcceptedBaselineMain.parseArguments(Vector.empty).isLeft)
      assert(BeautyQAcceptedBaselineMain.parseArguments(Vector(
        "--mode", "other",
        "--protected-corpus", "c",
        "--protected-policy", "p",
        "--output-dir", "o",
        "--application-revision", "commit-visible-123",
      )).isLeft)
      assert(BeautyQAcceptedBaselineMain.parseArguments(Vector(
        "--mode", "verify",
        "--mode", "bootstrap",
        "--protected-corpus", "c",
        "--protected-policy", "p",
        "--output-dir", "o",
      )).isLeft)
      assert(BeautyQAcceptedBaselineMain.parseArguments(Vector(
        "--mode", "verify",
        "--protected-corpus", "c",
        "--protected-policy", "p",
        "--output-dir", "o",
        "--canonical-manifest", "manifest.json",
        "--application-revision", "commit-visible-123",
      )).isLeft)
      assert(BeautyQAcceptedBaselineMain.parseArguments(Vector(
        "--mode", "bootstrap",
        "--protected-corpus", "c",
        "--protected-policy", "p",
        "--output-dir", "o",
      )).isLeft)
      assert(BeautyQAcceptedBaselineMain.parseArguments(Vector(
        "--mode", "bootstrap",
        "--protected-corpus", "c",
        "--protected-policy", "p",
        "--output-dir", "o",
        "--application-revision", " ",
      )).isLeft)
      assert(BeautyQAcceptedBaselineMain.parseArguments(Vector(
        "--mode", "bootstrap",
        "--protected-corpus", "c",
        "--protected-policy", "p",
        "--output-dir", "o",
        "--application-revision", " commit-visible-123",
      )).isLeft)
      assert(BeautyQAcceptedBaselineMain.parseArguments(Vector(
        "--mode", "bootstrap",
        "--protected-corpus", "c",
        "--protected-policy", "p",
        "--output-dir", "o",
        "--application-revision", "working-tree",
      )).isLeft)
      assert(BeautyQAcceptedBaselineMain.parseArguments(Vector(
        "--mode", "bootstrap",
        "--protected-corpus", "c",
        "--protected-policy", "p",
        "--output-dir", "o",
        "--application-revision", "commit-visible-123",
        "--application-revision", "commit-visible-456",
      )).isLeft)
    }

    "use one canonical classpath resource path" in {
      assert(BeautyQAcceptedEvaluationBaselineResource.ResourcePath ==
        "leaderboard/search/beautyq/gen2/eval/beautyq_accepted_evaluation_baseline_v1.json")
    }

    "write a red verification artifact before returning a compatibility failure" in {
      val outputDir = Paths.get("target/codex-sbt/accepted-baseline-main-spec")
      Files.createDirectories(outputDir)
      val candidate = fixture()
      val policy = policyFixture()
      Files.deleteIfExists(outputDir.resolve("beautyq-accepted-baseline-candidate.json"))
      Files.deleteIfExists(outputDir.resolve("beautyq-accepted-baseline-verification.json"))
      val result = BeautyQAcceptedBaselineMain.postExecution(
        "verify",
        candidate,
        policy,
        outputDir,
        () => Right(candidate),
        (path: Path, json: Json) => { Files.writeString(path, json.noSpaces, StandardCharsets.UTF_8); () },
      )
      result match {
        case Left(error) => assert(error.startsWith("ACCEPTED_BASELINE_VERIFICATION_FAILED"))
        case Right(message) => fail(s"expected red compatibility result, got $message")
      }
      assert(Files.exists(outputDir.resolve("beautyq-accepted-baseline-candidate.json")))
      assert(Files.exists(outputDir.resolve("beautyq-accepted-baseline-verification.json")))
      val verification = io.circe.parser.parse(Files.readString(outputDir.resolve("beautyq-accepted-baseline-verification.json"))).fold(error => fail(error), identity)
      val checks = verification.hcursor.downField("checks").focus.flatMap(_.asArray).getOrElse(Vector.empty)
      assert(checks.exists(_.hcursor.get[Boolean]("passed").contains(false)))
      assert(!Files.exists(outputDir.resolve("beautyq-accepted-evaluation-baseline-v1.json")))
    }

    "never fabricate verification success when the canonical loader fails" in {
      val outputDir = Paths.get("target/codex-sbt/accepted-baseline-main-loader-failure-spec")
      Files.createDirectories(outputDir)
      Files.deleteIfExists(outputDir.resolve("beautyq-accepted-baseline-candidate.json"))
      Files.deleteIfExists(outputDir.resolve("beautyq-accepted-baseline-verification.json"))
      val policy = policyFixture()
      val result = BeautyQAcceptedBaselineMain.postExecution(
        "verify",
        completeFixture(policy, "0.800000000000"),
        policy,
        outputDir,
        () => Left(leaderboard.search.beautyq.gen2.eval.BeautyQAcceptedEvaluationBaselineResourceError.Missing),
        (path: Path, json: Json) => { Files.writeString(path, json.noSpaces, StandardCharsets.UTF_8); () },
      )
      result match {
        case Left(error) => assert(error == "BASELINE_PROVENANCE_INVALID: canonical_resource_missing")
        case Right(message) => fail(s"expected canonical-loader failure, got $message")
      }
      assert(Files.exists(outputDir.resolve("beautyq-accepted-baseline-candidate.json")))
      assert(!Files.exists(outputDir.resolve("beautyq-accepted-baseline-verification.json")))
    }

    "verify compatible non-zero deltas without treating them as a failure" in {
      val outputDir = Paths.get("target/codex-sbt/accepted-baseline-main-compatible-spec")
      Files.createDirectories(outputDir)
      Files.deleteIfExists(outputDir.resolve("beautyq-accepted-baseline-candidate.json"))
      Files.deleteIfExists(outputDir.resolve("beautyq-accepted-baseline-verification.json"))
      val policy = policyFixture()
      val candidate = completeFixture(policy, "0.812345678901")
      val canonical = completeFixture(policy, "0.800000000000")
      val result = BeautyQAcceptedBaselineMain.postExecution(
        "verify",
        candidate,
        policy,
        outputDir,
        () => Right(canonical),
        (path: Path, json: Json) => { Files.writeString(path, json.noSpaces, StandardCharsets.UTF_8); () },
      )
      result match {
        case Right(message) => assert(message.startsWith("ACCEPTED_BASELINE_VERIFIED"))
        case Left(error) => fail(s"expected compatible verification, got $error")
      }
      val verification = io.circe.parser.parse(Files.readString(outputDir.resolve("beautyq-accepted-baseline-verification.json"))).fold(error => fail(error), identity)
      assert(verification.hcursor.downField("passed").as[Boolean].contains(true))
      assert(verification.hcursor.downField("orderedDeltas").downArray.get[String]("delta").contains("0.012345678901"))
    }

    "include policy fingerprint in bootstrap output without touching the canonical resource" in {
      val outputDir = Paths.get("target/codex-sbt/accepted-baseline-bootstrap-spec")
      Files.createDirectories(outputDir)
      val policy = policyFixture()
      val candidate = completeFixture(policy, "0.800000000000")
      val result = BeautyQAcceptedBaselineMain.postExecution(
        "bootstrap",
        candidate,
        policy,
        outputDir,
        () => fail("bootstrap must not load the canonical resource"),
        (path: Path, json: Json) => { Files.writeString(path, json.noSpaces, StandardCharsets.UTF_8); () },
      )
      result match {
        case Right(message) =>
          assert(message.startsWith("ACCEPTED_BASELINE_CANDIDATE_READY"))
          assert(message.contains(s"digest=${leaderboard.search.beautyq.gen2.eval.BeautyQAcceptedEvaluationBaseline.candidateDigest(candidate)}"))
          assert(message.contains(s"applicationRevision=${candidate.applicationRevision}"))
          assert(message.contains(s"corpusFingerprint=${candidate.corpusFingerprint}"))
          assert(message.contains(s"protectedPolicyFingerprint=${policy.fingerprint}"))
          assert(message.contains("protectedReportDigest=" + ("f" * 64)))
        case Left(error) => fail(s"expected bootstrap candidate, got $error")
      }
      assert(Files.exists(outputDir.resolve("beautyq-accepted-baseline-candidate.json")))
    }

    "resolve arguments from the repository root with mode preserved" in {
      val root = BeautyQProtectedPathResolution.locateRepositoryRoot(Paths.get(".").toAbsolutePath.normalize)
      BeautyQAcceptedBaselineMain.parseArguments(Vector(
        "--mode", "bootstrap",
        "--protected-policy",
        "beautyq-search-gen2-eval/src/test/resources/leaderboard/search/beautyq/gen2/eval/protected/beautyq-protected-acceptance-policy-v1.json",
        "--protected-corpus",
        "beautyq-search-gen2-eval/src/test/resources/leaderboard/search/beautyq/gen2/eval/protected/beautyq-protected-holdout-v1.json",
        "--output-dir", "target/search-gen2/protected",
        "--application-revision", "commit-visible-123",
      )) match {
        case Right(arguments) =>
          val resolved = BeautyQAcceptedBaselineMain.resolveArgumentsFrom(root, arguments)
          assert(resolved.mode == "bootstrap")
          assert(resolved.protectedPolicy.toString.endsWith(
            "beautyq-search-gen2-eval/src/test/resources/leaderboard/search/beautyq/gen2/eval/protected/beautyq-protected-acceptance-policy-v1.json",
          ))
          assert(!resolved.protectedPolicy.toString.contains("leaderboard-app-shell"))
          assert(resolved.protectedCorpus.toString.endsWith(
            "beautyq-search-gen2-eval/src/test/resources/leaderboard/search/beautyq/gen2/eval/protected/beautyq-protected-holdout-v1.json",
          ))
          assert(!resolved.protectedCorpus.toString.contains("leaderboard-app-shell"))
          assert(resolved.outputDir.toString.endsWith("target/search-gen2/protected"))
          assert(!resolved.outputDir.toString.contains("leaderboard-app-shell"))
          assert(resolved.applicationRevision == "commit-visible-123")
        case Left(error) => fail(s"expected valid arguments, got $error")
      }
    }

    "preserve absolute paths in resolved arguments" in {
      val root = BeautyQProtectedPathResolution.locateRepositoryRoot(Paths.get(".").toAbsolutePath.normalize)
      val absoluteDir = Paths.get("/tmp/absolute-baseline-dir").toAbsolutePath.normalize.toString
      BeautyQAcceptedBaselineMain.parseArguments(Vector(
        "--mode", "verify",
        "--protected-policy", absoluteDir + "/policy.json",
        "--protected-corpus", absoluteDir + "/corpus.json",
        "--output-dir", absoluteDir + "/output",
        "--application-revision", "commit-visible-123",
      )) match {
        case Right(arguments) =>
          val resolved = BeautyQAcceptedBaselineMain.resolveArgumentsFrom(root, arguments)
          assert(resolved.protectedPolicy.isAbsolute)
          assert(resolved.protectedCorpus.isAbsolute)
          assert(resolved.outputDir.isAbsolute)
          assert(resolved.applicationRevision == "commit-visible-123")
          assert(resolved.mode == "verify")
        case Left(error) => fail(s"expected valid arguments, got $error")
      }
    }

    "throw IllegalArgumentException from main for invalid arguments with relative paths" in {
      intercept[IllegalArgumentException] {
        BeautyQAcceptedBaselineMain.main(Array.empty)
      }
    }

    "write post-execution artifacts to the repository-root-resolved output directory, not the forked cwd" in {
      val root = BeautyQProtectedPathResolution.locateRepositoryRoot(Paths.get(".").toAbsolutePath.normalize)
      val policy = policyFixture()
      val candidate = completeFixture(policy, "0.800000000000")
      val canonical = completeFixture(policy, "0.812345678901")
      BeautyQAcceptedBaselineMain.parseArguments(Vector(
        "--mode", "verify",
        "--protected-corpus",
        "beautyq-search-gen2-eval/src/test/resources/leaderboard/search/beautyq/gen2/eval/protected/beautyq-protected-holdout-v1.json",
        "--protected-policy",
        "beautyq-search-gen2-eval/src/test/resources/leaderboard/search/beautyq/gen2/eval/protected/beautyq-protected-acceptance-policy-v1.json",
        "--output-dir", "target/search-gen2/protected",
        "--application-revision", "commit-visible-123",
      )) match {
        case Right(arguments) =>
          val resolved = BeautyQAcceptedBaselineMain.resolveArgumentsFrom(root, arguments)
          var capturedCandidate: Path = null
          var capturedVerification: Path = null
          val result = BeautyQAcceptedBaselineMain.postExecution(
            resolved.mode,
            candidate,
            policy,
            resolved.outputDir,
            () => Right(canonical),
            (path: Path, _: Json) => {
              if (path.endsWith("beautyq-accepted-baseline-candidate.json")) capturedCandidate = path
              else if (path.endsWith("beautyq-accepted-baseline-verification.json")) capturedVerification = path
              (): Unit
            },
          )
          assert(
            resolved.outputDir.toString.endsWith("target/search-gen2/protected"),
            s"expected resolved output dir to end with target/search-gen2/protected, got ${resolved.outputDir}",
          )
          assert(
            !resolved.outputDir.toString.contains("leaderboard-app-shell"),
            s"resolved output dir must not contain leaderboard-app-shell, got ${resolved.outputDir}",
          )
          assert(
            capturedCandidate.toString.endsWith("target/search-gen2/protected/beautyq-accepted-baseline-candidate.json"),
            s"candidate artifact not under repo-root target/search-gen2/protected, got $capturedCandidate",
          )
          assert(
            !capturedCandidate.toString.contains("leaderboard-app-shell"),
            s"candidate path must not contain leaderboard-app-shell, got $capturedCandidate",
          )
          assert(
            capturedVerification.toString.endsWith("target/search-gen2/protected/beautyq-accepted-baseline-verification.json"),
            s"verification artifact not under repo-root target/search-gen2/protected, got $capturedVerification",
          )
          assert(
            !capturedVerification.toString.contains("leaderboard-app-shell"),
            s"verification path must not contain leaderboard-app-shell, got $capturedVerification",
          )
          result match {
            case Left(error) => assert(error.startsWith("ACCEPTED_BASELINE_VERIFICATION_FAILED"))
            case Right(message) => assert(message.startsWith("ACCEPTED_BASELINE_VERIFIED"))
          }
        case Left(error) => fail(s"expected valid arguments, got $error")
      }
    }
  }

  private def fixture(): AcceptedEvaluationBaseline = {
    val id = EvaluationProvenanceId.from("fixture").fold(error => fail(error), identity)
    val provenance = ProvenanceComponent.from(id, "value").fold(error => fail(error), identity)
    AcceptedEvaluationBaseline.create("a" * 64, "metric-v1", "evaluation-v1", "commit-a", Vector(provenance), "b" * 64, Vector.empty)
      .fold(error => fail(error), identity)
  }

  private def completeFixture(policy: BeautyQProtectedAcceptancePolicy, average: String): AcceptedEvaluationBaseline = {
    val values = Vector(
      "visible-corpus-fingerprint" -> ("b" * 64),
      "protected-corpus-fingerprint" -> ("a" * 64),
      "protected-policy-fingerprint" -> policy.fingerprint,
      "source-content-fingerprint" -> ("d" * 64),
      "projected-documents-fingerprint" -> ("f" * 64),
      "embedding-provider" -> "provider",
      "embedding-model" -> "model",
      "embedding-revision" -> "revision",
      "embedding-dimension" -> "384",
      "embedding-text-format-version" -> "v1",
      "elasticsearch-version" -> "8",
      "qdrant-version" -> "1.18.3",
      "application-revision" -> "commit-a",
      "application-revision-source" -> "system-property",
      "elasticsearch-generation-reference" -> "es-a",
      "qdrant-generation-id" -> "q-a",
      "visible-report-digest" -> ("e" * 64),
      "protected-report-digest" -> ("f" * 64),
    )
    val provenance = values.map { case (name, value) =>
      val id = EvaluationProvenanceId.from(name).fold(error => fail(error), identity)
      ProvenanceComponent.from(id, value).fold(error => fail(error), identity)
    }
    val json = Json.obj(
      "schemaVersion" -> Json.fromString(AcceptedEvaluationBaseline.CurrentSchemaVersion),
      "corpusFingerprint" -> Json.fromString("a" * 64),
      "metricSchemaVersion" -> Json.fromString("metric-v1"),
      "evaluationPolicyVersion" -> Json.fromString("evaluation-v1"),
      "applicationRevision" -> Json.fromString("commit-a"),
      "provenance" -> Json.fromValues(provenance.map(component => Json.obj(
        "id" -> Json.fromString(component.id.value),
        "value" -> Json.fromString(component.value),
      ))),
      "reportDigest" -> Json.fromString("b" * 64),
      "aggregateObservations" -> Json.arr(Json.obj(
        "key" -> Json.fromString("protected-global"),
        "section" -> Json.obj(
          "structuralInvalidCount" -> Json.fromInt(0),
          "duplicateIdentityCount" -> Json.fromInt(0),
          "zeroResultCount" -> Json.fromInt(0),
          "forbiddenHitCount" -> Json.fromInt(0),
          "applicableMetricCount" -> Json.fromInt(1),
          "notApplicableMetricCount" -> Json.fromInt(0),
          "metricObservations" -> Json.arr(Json.obj(
            "surface" -> Json.fromString("variants"),
            "metric" -> Json.fromString("success"),
            "cutoff" -> Json.fromInt(1),
            "average" -> Json.fromBigDecimal(BigDecimal(average)),
            "applicableCount" -> Json.fromInt(1),
            "notApplicableCount" -> Json.fromInt(0),
          )),
        ),
      )),
    )
    AcceptedBaselineCodec.decode(json).fold(error => fail(error.toString), identity)
  }

  private def policyFixture(): BeautyQProtectedAcceptancePolicy = {
    val json = Json.obj(
      "schemaVersion" -> Json.fromString(BeautyQProtectedAcceptancePolicy.CurrentSchemaVersion),
      "evaluationPolicyVersion" -> Json.fromString("evaluation-v1"),
      "protectedAcceptancePolicyVersion" -> Json.fromString("protected-v1"),
      "expectedCorpusFingerprint" -> Json.fromString("a" * 64),
      "expectedCaseCount" -> Json.fromInt(1),
      "requiredSliceMinimums" -> Json.arr(Json.obj("sliceId" -> Json.fromString("smoke"), "minimumCaseCount" -> Json.fromInt(1))),
      "requiredMetricMinimums" -> Json.arr(Json.obj(
        "observationKey" -> Json.fromString("protected-global"),
        "surface" -> Json.fromString("variants"),
        "metric" -> Json.fromString("success"),
        "cutoff" -> Json.fromInt(1),
        "minimum" -> Json.fromString("0.000000000000"),
      )),
    )
    BeautyQProtectedAcceptancePolicy.fromJson(json).fold(error => fail(error.toString), identity)
  }
}
