package leaderboard.search

import org.scalatest.wordspec.AnyWordSpec
import java.nio.file.{Files, Paths}
import java.nio.charset.StandardCharsets

final class BeautyQProtectedAcceptanceMainSpec extends AnyWordSpec {
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


  }
}
