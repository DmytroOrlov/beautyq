package leaderboard.search

import leaderboard.search.beautyq.gen2.eval.BeautyQEvaluationEnvironment
import org.scalatest.wordspec.AnyWordSpec
import java.nio.file.{Files, Paths}
import java.nio.charset.StandardCharsets
import java.time.Instant

final class BeautyQProtectedAcceptanceMainSpec extends AnyWordSpec {
  private final case class SyntheticRoot(
    first: Option[SyntheticBranch],
    repeated: List[SyntheticBranch],
    sentinel: String,
  )
  private final case class SyntheticBranch(leaf: SyntheticLeaf, sentinel: String)
  private final case class SyntheticLeaf(sentinel: String)

  private final case class Constructor1(next: Constructor2)
  private final case class Constructor2(next: Constructor3)
  private final case class Constructor3(next: Constructor4)
  private final case class Constructor4(next: Constructor5)
  private final case class Constructor5(next: Constructor6)
  private final case class Constructor6(next: Constructor7)
  private final case class Constructor7(next: Constructor8)
  private final case class Constructor8(next: Constructor9)
  private final case class Constructor9(sentinel: String)

  "BeautyQProtectedAcceptanceMain" should {
    "parse exactly the four required option/value pairs" in {
      val parsed = BeautyQProtectedAcceptanceMain.parseArguments(Vector(
        "--protected-policy", "policy.json",
        "--protected-corpus", "corpus.json",
        "--output-dir", "out",
        "--application-revision", "commit-visible-123",
      ))
      parsed match {
        case Right(arguments) =>
          assert(arguments.protectedPolicy.toString == "policy.json")
          assert(arguments.protectedCorpus.toString == "corpus.json")
          assert(arguments.outputDir.toString == "out")
          assert(arguments.applicationRevision == "commit-visible-123")
        case Left(error) => fail(s"expected valid arguments, got $error")
      }
    }

    "reject missing, duplicate, unknown and blank arguments" in {
      assert(BeautyQProtectedAcceptanceMain.parseArguments(Vector.empty).isLeft)
      assert(BeautyQProtectedAcceptanceMain.parseArguments(Vector(
        "--protected-corpus", "c",
        "--protected-corpus", "d",
        "--protected-policy", "p",
        "--output-dir", "o",
      )).isLeft)
      assert(BeautyQProtectedAcceptanceMain.parseArguments(Vector(
        "--unknown", "x",
        "--protected-policy", "p",
        "--output-dir", "o",
      )).isLeft)
      assert(BeautyQProtectedAcceptanceMain.parseArguments(Vector(
        "--protected-corpus", " ",
        "--protected-policy", "p",
        "--output-dir", "o",
        "--application-revision", "commit-visible-123",
      )).isLeft)
      assert(BeautyQProtectedAcceptanceMain.parseArguments(Vector(
        "--protected-corpus", "c",
        "--protected-policy", "p",
        "--output-dir", "o",
      )).isLeft)
      assert(BeautyQProtectedAcceptanceMain.parseArguments(Vector(
        "--protected-corpus", "c",
        "--protected-policy", "p",
        "--output-dir", "o",
        "--application-revision", " ",
      )).isLeft)
      assert(BeautyQProtectedAcceptanceMain.parseArguments(Vector(
        "--protected-corpus", "c",
        "--protected-policy", "p",
        "--output-dir", "o",
        "--application-revision", " commit-visible-123",
      )).isLeft)
      assert(BeautyQProtectedAcceptanceMain.parseArguments(Vector(
        "--protected-corpus", "c",
        "--protected-policy", "p",
        "--output-dir", "o",
        "--application-revision", "working-tree",
      )).isLeft)
      assert(BeautyQProtectedAcceptanceMain.parseArguments(Vector(
        "--protected-corpus", "c",
        "--protected-policy", "p",
        "--output-dir", "o",
        "--application-revision", "commit-visible-123",
        "--application-revision", "commit-visible-456",
      )).isLeft)
    }

    "report missing product inputs before resource execution" in {
      val result = BeautyQSearchGen2EvaluationResourceHarness.runProtectedAcceptance(
        Paths.get("target/search-gen2/private/missing-protected-corpus.json"),
        Paths.get("target/search-gen2/private/missing-protected-policy.json"),
        Paths.get("target/search-gen2/protected-test-output"),
        "commit-visible-123",
      )
      assert(result.startsWith("PRODUCT_INPUT_REQUIRED"))
    }

    "report malformed policy JSON without raw parser prose" in {
      val dir = Paths.get("target/codex-sbt")
      Files.createDirectories(dir)
      val policyPath = dir.resolve("malformed-policy.json")
      Files.writeString(policyPath, "{ not valid json", StandardCharsets.UTF_8)
      val result = BeautyQSearchGen2EvaluationResourceHarness.runProtectedAcceptance(
        Paths.get("target/search-gen2/private/missing-protected-corpus.json"),
        policyPath,
        Paths.get("target/search-gen2/protected-test-output"),
        "commit-visible-123",
      )
      val cleanup = () => {
        Files.deleteIfExists(policyPath): Unit
      }
      try {
        assert(result.contains("PRODUCT_INPUT_REQUIRED"))
        assert(!result.contains("not valid json"))
        assert(!result.contains("expected "))
      } finally cleanup()
    }

    "parse failures cause throw in main path" in {
      intercept[IllegalArgumentException] {
        BeautyQProtectedAcceptanceMain.main(Array.empty)
      }
    }

    "product-input failure causes IllegalStateException via main" in {
      import scala.util.Using
      val absentPath = Paths.get("target/codex-sbt/absent-protected-input.json")
      val outputDir = Paths.get("target/codex-sbt/protected-main-test-output")
      Files.deleteIfExists(absentPath): Unit
      if (Files.isDirectory(outputDir)) {
        Using.resource(Files.list(outputDir)) { stream =>
          stream.forEach(p => Files.deleteIfExists(p): Unit)
        }
        Files.deleteIfExists(outputDir): Unit
      }
      val exception = intercept[IllegalStateException] {
        BeautyQProtectedAcceptanceMain.main(Array(
          "--protected-corpus", absentPath.toString,
          "--protected-policy", absentPath.toString,
          "--output-dir", outputDir.toString,
          "--application-revision", "commit-visible-123",
        ))
      }
      val message = exception.getMessage
      assert(message.startsWith("PRODUCT_INPUT_REQUIRED"))
      assert(!message.contains("sentinel"))
      val protectedFiles = Vector(
        "beautyq-protected-aggregate.json",
        "beautyq-protected-measurement.json",
        "beautyq-protected-acceptance-gate.json",
        "beautyq-accepted-baseline-candidate.json",
      )
      if (Files.isDirectory(outputDir)) {
        val existing = protectedFiles.filter(name => Files.exists(outputDir.resolve(name)))
        assert(existing.isEmpty, s"unexpected protected artifacts in $outputDir: $existing"): Unit
      }
    }

    "report a sanitized protected execution error without identity sentinels" in {
      val error = leaderboard.search.beautyq.gen2.eval.BeautyQEvaluationExecutionError.Request("sentinel-case-id", "sentinel-query", "request failed")
      val sanitized = BeautyQSearchGen2EvaluationResourceHarness.sanitizedProtectedError(error)
      assert(sanitized == "protected_request_failed")
      assert(!sanitized.contains("sentinel-case-id"))
      assert(!sanitized.contains("sentinel-query"))

      val projectionError = leaderboard.search.beautyq.gen2.eval.BeautyQEvaluationExecutionError.Projection(
        "sentinel-case", "sentinel-query", "pass-1",
        leaderboard.search.beautyq.gen2.wiring.BeautyQSearchResponseProjectionError.MissingFacet("test")
      )
      val projSanitized = BeautyQSearchGen2EvaluationResourceHarness.sanitizedProtectedError(projectionError)
      assert(projSanitized == "protected_projection_failed")
      assert(!projSanitized.contains("sentinel"))
    }

    "report an application failure through constructor names without protected values" in {
      val error = leaderboard.search.beautyq.gen2.eval.BeautyQEvaluationExecutionError.Application(
        "sentinel-case-id",
        "sentinel-query",
        "sentinel-pass",
        leaderboard.search.beautyq.gen2.wiring.BeautyQSearchApplicationError.Orchestration(
          leaderboard.search.beautyq.gen2.wiring.BeautyQSearchOrchestrationError.BoundPlanMismatch(
            "sentinel-expected",
            "sentinel-actual",
          )
        ),
      )

      val sanitized = BeautyQSearchGen2EvaluationResourceHarness.sanitizedProtectedError(error)

      assert(sanitized == "protected_application_failed:Orchestration/BoundPlanMismatch")
      Vector("sentinel", "case-id", "query", "expected", "actual").foreach { forbidden =>
        assert(!sanitized.contains(forbidden))
      }
    }

    "traverse wrappers while preserving unique constructor encounter order" in {
      val value = SyntheticRoot(
        first = Some(SyntheticBranch(SyntheticLeaf("sentinel-first"), "sentinel-branch")),
        repeated = List(
          SyntheticBranch(SyntheticLeaf("sentinel-second"), "sentinel-repeated"),
        ),
        sentinel = "sentinel-root",
      )

      val path = BeautyQSearchGen2EvaluationResourceHarness.privacySafeConstructorPath(value)

      assert(path == Vector("SyntheticRoot", "SyntheticBranch", "SyntheticLeaf"))
      Vector("Some", "None", "Vector", "List", "::", "Nil").foreach { wrapper =>
        assert(!path.contains(wrapper))
      }
      assert(!path.exists(_.contains("sentinel")))
    }

    "bound a privacy-safe constructor path to eight names" in {
      val value = Constructor1(Constructor2(Constructor3(Constructor4(Constructor5(Constructor6(Constructor7(Constructor8(Constructor9("sentinel")))))))))

      val path = BeautyQSearchGen2EvaluationResourceHarness.privacySafeConstructorPath(value)

      assert(path == Vector("Constructor1", "Constructor2", "Constructor3", "Constructor4", "Constructor5", "Constructor6", "Constructor7", "Constructor8"))
      assert(path.size == 8)
      assert(!path.contains("Constructor9"))
    }

    "propagate and restore the application revision property around evaluation setup" in {
      val property = "search.gen2.eval.application-revision"
      val original = Option(System.getProperty(property))
      try {
        System.setProperty(property, "previous-visible-456")
        val environment = BeautyQSearchGen2EvaluationResourceHarness.withApplicationRevision("commit-visible-123") {
          BeautyQEvaluationEnvironment.fromSystem(Instant.EPOCH, "source", "elasticsearch", "qdrant") match {
            case Right(value) => value
            case Left(error) => fail(s"expected environment, got $error")
          }
        }
        assert(environment.applicationRevision == "commit-visible-123")
        assert(environment.applicationRevisionSource == "system-property")
        assert(Option(System.getProperty(property)).contains("previous-visible-456"))

        System.clearProperty(property)
        val absentEnvironment = BeautyQSearchGen2EvaluationResourceHarness.withApplicationRevision("commit-visible-123") {
          BeautyQEvaluationEnvironment.fromSystem(Instant.EPOCH, "source", "elasticsearch", "qdrant") match {
            case Right(value) => value
            case Left(error) => fail(s"expected environment, got $error")
          }
        }
        assert(absentEnvironment.applicationRevision == "commit-visible-123")
        assert(absentEnvironment.applicationRevisionSource == "system-property")
        assert(Option(System.getProperty(property)).isEmpty)

        System.setProperty(property, "previous-visible-456")
        intercept[IllegalStateException] {
          BeautyQSearchGen2EvaluationResourceHarness.withApplicationRevision("commit-visible-123") {
            throw new IllegalStateException("synthetic evaluation failure")
          }
        }
        assert(Option(System.getProperty(property)).contains("previous-visible-456"))
      } finally {
        original match {
          case Some(value) => System.setProperty(property, value): Unit
          case None        => System.clearProperty(property): Unit
        }
      }
    }

  }
}
