package leaderboard.search

import leaderboard.search.beautyq.gen2.eval.BeautyQProtectedBreakGlassDisclosure
import org.scalatest.wordspec.AnyWordSpec

import java.nio.file.{Files, Paths}

final class BeautyQProtectedBreakGlassMainSpec extends AnyWordSpec {
  "BeautyQProtectedBreakGlassMain" should {
    "parse the exact authorization and derive both outputs from one run root" in {
      parse(validArguments) match {
        case arguments =>
          assert(arguments.expectedFailedCheck == BeautyQProtectedBreakGlassDisclosure.AuthorizedCheckCode)
          assert(arguments.authorizationId == BeautyQProtectedBreakGlassDisclosure.RecoveryRotation6AuthorizationId)
          assert(arguments.aggregateOutput == arguments.runRoot.resolve("aggregate-safe"))
          assert(arguments.disclosureOutput == arguments.runRoot.resolve("beautyq-protected-break-glass-disclosure.json"))
      }
      assert(BeautyQProtectedBreakGlassMain.parseArguments(
        replaceValue(validArguments, "--expected-failed-check", "metric-other")
      ).isLeft)
      assert(BeautyQProtectedBreakGlassMain.parseArguments(
        replaceValue(validArguments, "--authorization-id", "another-authorization")
      ).isLeft)
    }

    "reject missing, duplicate, blank, unknown and unapproved arguments" in {
      assert(BeautyQProtectedBreakGlassMain.parseArguments(Vector.empty).isLeft)
      assert(BeautyQProtectedBreakGlassMain.parseArguments(validArguments.dropRight(2)).isLeft)
      assert(BeautyQProtectedBreakGlassMain.parseArguments(validArguments ++ Vector("--run-root", "target/duplicate")).isLeft)
      assert(BeautyQProtectedBreakGlassMain.parseArguments(validArguments.updated(0, "--unknown")).isLeft)
      assert(BeautyQProtectedBreakGlassMain.parseArguments(validArguments.updated(1, " ")).isLeft)
      assert(BeautyQProtectedBreakGlassMain.parseArguments(replaceValue("--authorization-id", "another-authorization")).isLeft)
      assert(BeautyQProtectedBreakGlassMain.parseArguments(replaceValue("--expected-failed-check", "metric-other")).isLeft)
    }

    "accept each historical authorization only with its authorized failed check" in {
      val historicalIds = Vector(
        BeautyQProtectedBreakGlassDisclosure.AuthorizationId,
        BeautyQProtectedBreakGlassDisclosure.Cycle2AuthorizationId,
        BeautyQProtectedBreakGlassDisclosure.FullSliceCycle3AuthorizationId,
        BeautyQProtectedBreakGlassDisclosure.ConvergenceAuthorizationId,
        BeautyQProtectedBreakGlassDisclosure.PostRecoveryAuthorizationId,
        BeautyQProtectedBreakGlassDisclosure.RecoveryRotation2AuthorizationId,
        BeautyQProtectedBreakGlassDisclosure.RecoveryRotation3AuthorizationId,
        BeautyQProtectedBreakGlassDisclosure.RecoveryRotation4AuthorizationId,
        BeautyQProtectedBreakGlassDisclosure.RecoveryRotation5AuthorizationId,
        BeautyQProtectedBreakGlassDisclosure.RecoveryRotation6AuthorizationId,
        BeautyQProtectedBreakGlassDisclosure.RecoveryRotation7AuthorizationId,
        BeautyQProtectedBreakGlassDisclosure.RecoveryRotation8AuthorizationId,
      )
      historicalIds.foreach { id =>
        val arguments = replaceValue(validArguments, "--authorization-id", id)
        val parsed = BeautyQProtectedBreakGlassMain.parseArguments(arguments).fold(error => fail(error), identity)
        assert(parsed.authorizationId == id)
        assert(parsed.expectedFailedCheck == BeautyQProtectedBreakGlassDisclosure.AuthorizedCheckCode)
        assert(BeautyQProtectedBreakGlassMain.parseArguments(
          replaceValue(arguments, "--expected-failed-check", "metric-other")
        ).isLeft)
      }
    }

    "accept only one fresh ignored run root and never overwrite it" in {
      val repositoryRoot = Paths.get("").toAbsolutePath.normalize
      val fresh = parse(validArguments)
      assert(BeautyQProtectedBreakGlassMain.validateDestinations(fresh, repositoryRoot).isRight)

      val existingPath = repositoryRoot.resolve(".evidence-runs/q2-break-glass/existing-owner-spec")
      Files.createDirectories(existingPath)
      try {
        val existing = parse(replaceValue("--run-root", ".evidence-runs/q2-break-glass/existing-owner-spec"))
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
        ".evidence-runs/q2-break-glass/../docs",
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
    "--run-root", ".evidence-runs/q2-break-glass/fresh-owner-spec",
    "--expected-failed-check", BeautyQProtectedBreakGlassDisclosure.AuthorizedCheckCode,
    "--authorization-id", BeautyQProtectedBreakGlassDisclosure.RecoveryRotation6AuthorizationId,
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
