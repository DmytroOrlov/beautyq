package leaderboard.search

import leaderboard.search.beautyq.gen2.eval.BeautyQProtectedBreakGlassDisclosure
import org.scalatest.wordspec.AnyWordSpec

import java.nio.file.{Files, Paths}

final class BeautyQProtectedBreakGlassMainSpec extends AnyWordSpec {
  "BeautyQProtectedBreakGlassMain" should {
    "parse the exact authorization and derive both outputs from one run root" in {
      parse(validArguments) match {
        case arguments =>
          assert(arguments.applicationRevision == "655ebd9d21920d0b03c08df487acc8b4bd0db590")
          assert(arguments.expectedFailedCheck == BeautyQProtectedBreakGlassDisclosure.AuthorizedCheckCode)
          assert(arguments.authorizationId == BeautyQProtectedBreakGlassDisclosure.Cycle2AuthorizationId)
          assert(arguments.aggregateOutput == arguments.runRoot.resolve("aggregate-safe"))
          assert(arguments.disclosureOutput == arguments.runRoot.resolve("beautyq-protected-break-glass-disclosure.json"))
      }
    }

    "reject missing, duplicate, blank, unknown and unapproved arguments" in {
      assert(BeautyQProtectedBreakGlassMain.parseArguments(Vector.empty).isLeft)
      assert(BeautyQProtectedBreakGlassMain.parseArguments(validArguments.dropRight(2)).isLeft)
      assert(BeautyQProtectedBreakGlassMain.parseArguments(validArguments ++ Vector("--run-root", "target/duplicate")).isLeft)
      assert(BeautyQProtectedBreakGlassMain.parseArguments(validArguments.updated(0, "--unknown")).isLeft)
      assert(BeautyQProtectedBreakGlassMain.parseArguments(validArguments.updated(1, " ")).isLeft)
      assert(BeautyQProtectedBreakGlassMain.parseArguments(replaceValue("--application-revision", "working-tree")).isLeft)
      assert(BeautyQProtectedBreakGlassMain.parseArguments(replaceValue("--authorization-id", "another-authorization")).isLeft)
      assert(BeautyQProtectedBreakGlassMain.parseArguments(replaceValue("--expected-failed-check", "metric-other")).isLeft)
      assert(BeautyQProtectedBreakGlassMain.parseArguments(replaceValue("--authorization-id", BeautyQProtectedBreakGlassDisclosure.AuthorizationId)).isLeft)
    }

    "retain the historical authorization only with its historical revision" in {
      val historical = replaceValue(
        replaceValue(validArguments, "--authorization-id", BeautyQProtectedBreakGlassDisclosure.AuthorizationId),
        "--application-revision",
        "89811d5f2ad5327b24b2aac4641f4716d781000e",
      )
      val parsed = BeautyQProtectedBreakGlassMain.parseArguments(historical).fold(error => fail(error), identity)
      assert(parsed.authorizationId == BeautyQProtectedBreakGlassDisclosure.AuthorizationId)
      assert(parsed.applicationRevision == "89811d5f2ad5327b24b2aac4641f4716d781000e")
    }

    "accept only one fresh ignored run root and never overwrite it" in {
      val repositoryRoot = Paths.get("").toAbsolutePath.normalize
      val fresh = parse(validArguments)
      assert(BeautyQProtectedBreakGlassMain.validateDestinations(fresh, repositoryRoot).isRight)

      val existingPath = repositoryRoot.resolve("target/search-gen2/q2-break-glass/existing-owner-spec")
      Files.createDirectories(existingPath)
      try {
        val existing = parse(replaceValue("--run-root", "target/search-gen2/q2-break-glass/existing-owner-spec"))
        BeautyQProtectedBreakGlassMain.validateDestinations(existing, repositoryRoot) match {
          case Left(value) =>
            assert(value == "break_glass_run_root_already_exists")
            (): Unit
          case Right(()) => fail("expected existing run root rejection")
        }
      } finally {
        Files.deleteIfExists(existingPath): Unit
      }
    }

    "reject tracked, protected-input, canonical-baseline and traversing destinations before execution" in {
      val repositoryRoot = Paths.get("").toAbsolutePath.normalize
      val rejected = Vector(
        "docs/q2-break-glass",
        "beautyq-search-gen2-eval/src/test/resources/leaderboard/search/beautyq/gen2/eval/protected/run",
        "beautyq-search-gen2-eval/src/main/resources/leaderboard/search/beautyq/gen2/eval/run",
        "target/search-gen2/q2-break-glass/../escaped",
      )
      rejected.foreach { path =>
        val arguments = parse(replaceValue("--run-root", path))
        assert(BeautyQProtectedBreakGlassMain.validateDestinations(arguments, repositoryRoot).isLeft)
      }
    }

    "locate the repository root from the forked app-shell working directory" in {
      val repositoryRoot = Paths.get("").toAbsolutePath.normalize
      val nested = repositoryRoot.resolve("leaderboard-app-shell/target")
      BeautyQProtectedBreakGlassMain.locateRepositoryRoot(nested) match {
        case Right(actual) =>
          assert(actual == repositoryRoot)
          (): Unit
        case Left(error) => fail(s"expected repository root, got $error")
      }
    }

    "retain a red execution only for the explicit break-glass owner" in {
      assert(BeautyQSearchGen2EvaluationResourceHarness.retainProtectedExecution(acceptancePassed = true, retainRedExecution = false))
      assert(BeautyQSearchGen2EvaluationResourceHarness.retainProtectedExecution(acceptancePassed = true, retainRedExecution = true))
      assert(!BeautyQSearchGen2EvaluationResourceHarness.retainProtectedExecution(acceptancePassed = false, retainRedExecution = false))
      assert(BeautyQSearchGen2EvaluationResourceHarness.retainProtectedExecution(acceptancePassed = false, retainRedExecution = true))
    }
  }

  private val validArguments = Vector(
    "--protected-corpus", "protected.json",
    "--protected-policy", "policy.json",
    "--run-root", "target/search-gen2/q2-break-glass/fresh-owner-spec",
    "--application-revision", "655ebd9d21920d0b03c08df487acc8b4bd0db590",
    "--expected-failed-check", BeautyQProtectedBreakGlassDisclosure.AuthorizedCheckCode,
    "--authorization-id", BeautyQProtectedBreakGlassDisclosure.Cycle2AuthorizationId,
  )

  private def replaceValue(option: String, value: String): Vector[String] = {
    replaceValue(validArguments, option, value)
  }

  private def replaceValue(values: Vector[String], option: String, value: String): Vector[String] = {
    val index = values.indexOf(option)
    if (index < 0) fail(s"missing option $option")
    else values.updated(index + 1, value)
  }

  private def parse(values: Vector[String]): BeautyQProtectedBreakGlassMain.Arguments =
    BeautyQProtectedBreakGlassMain.parseArguments(values).fold(error => fail(error), identity)
}
