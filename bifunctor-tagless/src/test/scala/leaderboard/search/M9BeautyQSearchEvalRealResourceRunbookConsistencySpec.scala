package leaderboard.search

import leaderboard.search.eval.M9BeautyQSearchEvalRealResourceRunbookConsistency
import org.scalatest.wordspec.AnyWordSpec

import java.io.File
import java.nio.charset.StandardCharsets
import scala.io.Source
import scala.util.Using

final class M9BeautyQSearchEvalRealResourceRunbookConsistencySpec extends AnyWordSpec {

  private val runbookText: String = readRunbook()

  private val result =
    M9BeautyQSearchEvalRealResourceRunbookConsistency.verify(runbookText)

  "M9BeautyQSearchEvalRealResourceRunbookConsistency" should {

    "name the future-only runbook path" in {
      assert(
        M9BeautyQSearchEvalRealResourceRunbookConsistency.RunbookPath ==
          "docs/local/BEAUTYQ_M9_REAL_RESOURCE_SMOKE_RUNBOOK.md"
      )
      assert(result.runbookPath == M9BeautyQSearchEvalRealResourceRunbookConsistency.RunbookPath)
    }

    "accept the new runbook as consistent with the accepted evidence schema and default artifact" in {
      assert(result.missingSections == Nil)
      assert(result.missingModeTokens == Nil)
      assert(result.missingBoundaryTokens == Nil)
      assert(result.modeTokenCountMatchesSchema)
      assert(result.defaultArtifactBlockedSkipOnly)
      assert(result.consistent)
    }

    "prove the runbook mentions all three evidence modes" in {
      assert(result.allModeTokensPresent)
      assert(runbookText.contains("ES-only"))
      assert(runbookText.contains("Qdrant-only"))
      assert(runbookText.contains("Combined ES/Qdrant"))
      assert(M9BeautyQSearchEvalRealResourceRunbookConsistency.RequiredEvidenceModeTokens.length == 3)
    }

    "prove the runbook carries the required boundary language" in {
      assert(result.allBoundaryTokensPresent)
      assert(runbookText.contains("future-only"))
      assert(runbookText.contains("blocked/skip"))
      assert(runbookText.contains("pending execution only"))
    }

    "prove the runbook makes no forbidden positive claim" in {
      assert(
        result.forbiddenClaimTokensPresent == Nil,
        s"runbook contains forbidden positive-claim tokens: ${result.forbiddenClaimTokensPresent}",
      )
      assert(result.noForbiddenPositiveClaims)
    }

    "forbid each forbidden-claim token as a positive claim" in {
      val lowered = runbookText.toLowerCase
      M9BeautyQSearchEvalRealResourceRunbookConsistency.ForbiddenPositiveClaimTokens.foreach { token =>
        assert(
          !lowered.contains(token.toLowerCase),
          s"runbook makes forbidden positive claim: $token",
        )
      }
    }

    "remain a static contract that never executes or inspects runtime" in {
      assert(result.staticContractOnly)
      assert(!result.parsesProductionRoutes)
      assert(!result.inspectsRuntimeEnvironment)
      assert(!result.realBackendCallImplemented)
      assert(!result.routePluginDiHttpInvolved)
    }

    "name the saved evidence schema and deterministic renderer as the capture path" in {
      assert(runbookText.contains("saved evidence schema"))
      assert(runbookText.contains("deterministic"))
    }
  }

  private def readRunbook(): String = {
    val relative = M9BeautyQSearchEvalRealResourceRunbookConsistency.RunbookPath
    resolveUpwards(relative) match {
      case Some(file) =>
        Using.resource(Source.fromFile(file, StandardCharsets.UTF_8.name()))(_.mkString)
      case None =>
        fail(s"missing runbook file $relative (searched upward from ${new File(".").getCanonicalPath})")
    }
  }

  /** Resolve a repository-relative path by walking up from the test working directory. The runbook
    * lives under the repo root `docs/local`, which is not on the test classpath, so the spec reads it
    * from the filesystem regardless of which module base sbt launches the test from. */
  private def resolveUpwards(relative: String): Option[File] = {
    val start = new File(".").getCanonicalFile
    Iterator
      .iterate(Option(start))(_.flatMap(dir => Option(dir.getParentFile)))
      .takeWhile(_.isDefined)
      .take(8)
      .flatten
      .map(dir => new File(dir, relative))
      .find(_.isFile)
  }
}
