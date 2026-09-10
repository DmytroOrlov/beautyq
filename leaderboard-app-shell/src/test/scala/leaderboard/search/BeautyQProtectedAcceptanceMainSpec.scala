package leaderboard.search

import org.scalatest.wordspec.AnyWordSpec
import java.nio.file.{Files, Paths}
import java.nio.charset.StandardCharsets

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
    "parse exactly the three required option/value pairs" in {
      val parsed = BeautyQProtectedAcceptanceMain.parseArguments(Vector(
        "--protected-policy", "policy.json",
        "--protected-corpus", "corpus.json",
        "--output-dir", "out",
      ))
      parsed match {
        case Right(arguments) =>
          assert(arguments.protectedPolicy.toString == "policy.json")
          assert(arguments.protectedCorpus.toString == "corpus.json")
          assert(arguments.outputDir.toString == "out")
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
      )).isLeft)
      assert(BeautyQProtectedAcceptanceMain.parseArguments(Vector(
        "--protected-corpus", "c",
        "--protected-policy", "p",
      )).isLeft)
    }

    "report missing product inputs before resource execution" in {
      val result = BeautyQSearchGen2EvaluationResourceHarness.runProtectedAcceptance(
        Paths.get("target/search-gen2/private/missing-protected-corpus.json"),
        Paths.get("target/search-gen2/private/missing-protected-policy.json"),
        Paths.get("target/search-gen2/protected-test-output"),
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

    "resolve arguments from the repository root instead of the forked working directory" in {
      val root = BeautyQProtectedPathResolution.locateRepositoryRoot(Paths.get(".").toAbsolutePath.normalize)
      BeautyQProtectedAcceptanceMain.parseArguments(Vector(
        "--protected-policy",
        "beautyq-search-gen2-eval/src/test/resources/leaderboard/search/beautyq/gen2/eval/protected/beautyq-protected-acceptance-policy-v1.json",
        "--protected-corpus",
        "beautyq-search-gen2-eval/src/test/resources/leaderboard/search/beautyq/gen2/eval/protected/beautyq-protected-holdout-v1.json",
        "--output-dir", "target/search-gen2/protected",
      )) match {
        case Right(arguments) =>
          val resolved = BeautyQProtectedAcceptanceMain.resolveArgumentsFrom(root, arguments)
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
        case Left(error) => fail(s"expected valid arguments, got $error")
      }
    }

    "preserve absolute paths during argument resolution" in {
      val root = BeautyQProtectedPathResolution.locateRepositoryRoot(Paths.get(".").toAbsolutePath.normalize)
      val absoluteDir = Paths.get("/tmp/absolute-dir").toAbsolutePath.normalize.toString
      BeautyQProtectedAcceptanceMain.parseArguments(Vector(
        "--protected-policy", absoluteDir + "/policy.json",
        "--protected-corpus", absoluteDir + "/corpus.json",
        "--output-dir", absoluteDir + "/output",
      )) match {
        case Right(arguments) =>
          val resolved = BeautyQProtectedAcceptanceMain.resolveArgumentsFrom(root, arguments)
          assert(resolved.protectedPolicy.isAbsolute)
          assert(resolved.protectedCorpus.isAbsolute)
          assert(resolved.outputDir.isAbsolute)
        case Left(error) => fail(s"expected valid arguments, got $error")
      }
    }

    "throw IllegalStateException from main for missing product inputs without leaking path names" in {
      val exception = intercept[IllegalStateException] {
        BeautyQProtectedAcceptanceMain.main(Array(
          "--protected-corpus", "target/codex-sbt/absent-protected-input.json",
          "--protected-policy", "target/codex-sbt/absent-protected-input.json",
          "--output-dir", "target/codex-sbt/protected-main-test-output",
        ))
      }
      val message = exception.getMessage
      assert(message.startsWith("PRODUCT_INPUT_REQUIRED"))
      assert(!message.contains("absent"))
    }

  }
}
