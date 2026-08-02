package leaderboard.search

import leaderboard.search.beautyq.gen2.contract.BeautySearchRequestGen2
import leaderboard.search.beautyq.gen2.wiring.*
import leaderboard.search.beautyq.gen2.wiring.testkit.BeautyQOrchestrationTestKit
import org.scalatest.wordspec.AnyWordSpec
import zio.*

final class BeautyQSearchGen2RuntimeSpec extends AnyWordSpec {
  "BeautyQSearchGen2Runtime" should {
    "execute through the blocking boundary when all dependencies serve" in {
      val context = BeautyQOrchestrationTestKit.eligible()
      val application = BeautyQSearchApplication.make(BeautyQOrchestrationTestKit.materialized, context.baselineService, context.embedding, context.qdrant)
      val runtime = leaderboard.search.gen2.BeautyQSearchGen2Runtime.make(
        application,
        fullSearchStatus,
      )

      val observed = Unsafe.unsafe { implicit unsafe =>
        Runtime.default.unsafe.run(runtime.execute(request).either).getOrThrowFiberFailure()
      }
      observed match {
        case Right(response) => assert(response.supplementStatus == BeautyQSupplementStatus.Supplemented)
        case other => fail(s"expected runtime response, got $other")
      }
    }

    "serve the baseline-only mode without invoking supplement mechanics" in {
      val context = BeautyQOrchestrationTestKit.eligible()
      val application = BeautyQSearchApplication.makeBaselineOnly(BeautyQOrchestrationTestKit.materialized, context.baselineService)
      val runtime = leaderboard.search.gen2.BeautyQSearchGen2Runtime.make(
        application,
        limitedStatus,
      )

      val observed = Unsafe.unsafe { implicit unsafe =>
        Runtime.default.unsafe.run(runtime.execute(request).either).getOrThrowFiberFailure()
      }
      observed match {
        case Right(response) =>
          assert(response.supplementStatus == BeautyQSupplementStatus.NoAppend)
          assert(response.supplementCount == 0)
          assert(response.servingMode == BeautyQServingMode.BaselineOnly.modeCode)
          assert(response.restartRequired)
          assert(response.warnings.nonEmpty)
        case other => fail(s"expected baseline-only response, got $other")
      }
    }

    "hold the exact startup-status reference" in {
      val context = BeautyQOrchestrationTestKit.eligible()
      val application = BeautyQSearchApplication.make(BeautyQOrchestrationTestKit.materialized, context.baselineService, context.embedding, context.qdrant)
      val status = fullSearchStatus
      val runtime = leaderboard.search.gen2.BeautyQSearchGen2Runtime.make(application, status)
      assert(runtime.startupStatus eq status)
    }
  }

  private val fullSearchStatus: StartupServingStatus =
    BeautyQStartupServingStatusTestFixtures.fullSearch

  private val limitedStatus: StartupServingStatus =
    BeautyQStartupServingStatusTestFixtures.disabledBaseline

  private val request: BeautySearchRequestGen2 = BeautySearchRequestGen2(
    Some("relaxing appointment"),
    Vector.empty,
    Vector.empty,
    Vector.empty,
    BeautyQOrchestrationTestKit.page,
    None,
  )
}
