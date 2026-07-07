package leaderboard.search

import leaderboard.search.beautyq.contract.BeautyQSearchResponseProvenanceContract.{ExecutionModes, JsonFields, ResultOrigins}
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

    "name the current local gate doc path" in {
      assert(
        M9BeautyQSearchEvalRealResourceRunbookConsistency.RunbookPath ==
          "docs/BEAUTYQ_QDRANT_SUPPLEMENT_LOCAL_GATE.md"
      )
      assert(result.runbookPath == M9BeautyQSearchEvalRealResourceRunbookConsistency.RunbookPath)
    }

    "accept the current local gate doc as consistent with the accepted evidence schema and default artifact" in {
      assert(result.missingSections == Nil)
      assert(result.missingModeTokens == Nil)
      assert(result.missingBoundaryTokens == Nil)
      assert(result.modeTokenCountMatchesSchema)
      assert(result.defaultArtifactBlockedSkipOnly)
      assert(result.consistent)
    }

    "prove the doc mentions the required response and provenance modes" in {
      assert(result.allModeTokensPresent)
      assert(runbookText.contains(ExecutionModes.EsOnly))
      assert(runbookText.contains(ExecutionModes.EsPlusQdrantSupplement))
      assert(runbookText.contains(ResultOrigins.QdrantSupplement))
      assert(M9BeautyQSearchEvalRealResourceRunbookConsistency.RequiredEvidenceModeTokens.length == 3)
    }

    "prove the doc carries the required launcher, curl, and boundary language" in {
      assert(result.allBoundaryTokensPresent)
      assert(runbookText.contains("./launcher -u scene:managed :leaderboard"))
      assert(runbookText.contains("http://localhost:8080/beauty-search"))
      assert(runbookText.contains("does not require an activation value"))
    }

    "prove the doc makes no forbidden positive claim" in {
      assert(
        result.forbiddenClaimTokensPresent == Nil,
        s"doc contains forbidden positive-claim tokens: ${result.forbiddenClaimTokensPresent}",
      )
      assert(result.noForbiddenPositiveClaims)
    }

    "forbid each forbidden-claim token as a positive claim" in {
      val lowered = runbookText.toLowerCase
      M9BeautyQSearchEvalRealResourceRunbookConsistency.ForbiddenPositiveClaimTokens.foreach { token =>
        assert(
          !lowered.contains(token.toLowerCase),
          s"doc makes forbidden positive claim: $token",
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

    "name the locked gate counts and frontend provenance as the capture path" in {
      assert(runbookText.contains("testedQueries=4"))
      assert(runbookText.contains("totalQdrantOnlyAppends=1"))
      assert(runbookText.contains(JsonFields.ResultOrigin))
    }
  }

  private def readRunbook(): String = {
    val relative = M9BeautyQSearchEvalRealResourceRunbookConsistency.RunbookPath
    resolveUpwards(relative) match {
      case Some(file) =>
        Using.resource(Source.fromFile(file, StandardCharsets.UTF_8.name()))(_.mkString)
      case None =>
        fail(s"missing doc file $relative (searched upward from ${new File(".").getCanonicalPath})")
    }
  }

  /** Resolve a repository-relative path by walking up from the test working directory. The doc
    * lives under the repo root `docs`, which is not on the test classpath, so the spec reads it
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
