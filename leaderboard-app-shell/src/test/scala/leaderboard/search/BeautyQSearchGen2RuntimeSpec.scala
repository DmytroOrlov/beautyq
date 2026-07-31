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

  private val fullSearchStatus: StartupServingStatus = new StartupServingStatus(
    policy = SupplementStartupPolicy.Required,
    servingMode = BeautyQServingMode.FullSearch,
    condition = "healthy",
    reason = None,
    restartRequired = false,
    sourceContentFingerprint = "test-source-fp",
    projectedDocumentsFingerprint = "test-projected-fp",
    elasticsearchReference = "test-es-ref",
    elasticsearchPhysicalTarget = "test-es-target",
    qdrantGenerationId = Some("test-qdrant-gen"),
    qdrantPhysicalCollection = Some("test-qdrant-col"),
  )

  private val limitedStatus: StartupServingStatus = new StartupServingStatus(
    policy = SupplementStartupPolicy.Disabled,
    servingMode = BeautyQServingMode.BaselineOnly,
    condition = "limited",
    reason = Some(new StartupServingStatus.Reason("qdrant_supplement_operator_disabled", "Qdrant supplement was disabled by operator policy; the complete Elasticsearch baseline was returned; restart is required", "supplement disabled by operator startup policy", None)),
    restartRequired = true,
    sourceContentFingerprint = "test-source-fp",
    projectedDocumentsFingerprint = "test-projected-fp",
    elasticsearchReference = "test-es-ref",
    elasticsearchPhysicalTarget = "test-es-target",
    qdrantGenerationId = None,
    qdrantPhysicalCollection = None,
  )

  private val request: BeautySearchRequestGen2 = BeautySearchRequestGen2(
    Some("relaxing appointment"),
    Vector.empty,
    Vector.empty,
    Vector.empty,
    BeautyQOrchestrationTestKit.page,
    None,
  )
}
