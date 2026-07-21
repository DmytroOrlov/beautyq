package leaderboard.search

import leaderboard.search.beautyq.gen2.contract.BeautySearchRequestGen2
import leaderboard.search.beautyq.gen2.wiring.*
import leaderboard.search.beautyq.gen2.wiring.testkit.BeautyQOrchestrationTestKit
import org.scalatest.wordspec.AnyWordSpec
import zio.*

final class BeautyQSearchGen2RuntimeSpec extends AnyWordSpec {
  "BeautyQSearchGen2Runtime" should {
    "block serving when a required dependency is unavailable" in {
      val context = BeautyQOrchestrationTestKit.eligible()
      val application = BeautyQSearchApplication.make(BeautyQOrchestrationTestKit.materialized, context.baselineService, context.embedding, context.qdrant)
      val runtime = leaderboard.search.gen2.BeautyQSearchGen2Runtime.make(
        application,
        BeautyQSupplementReadinessPolicy.evaluate(Set(BeautyQSearchDependency.ElasticsearchBaseline)),
      )

      val observed = Unsafe.unsafe { implicit unsafe =>
        Runtime.default.unsafe.run(runtime.execute(request).either).getOrThrowFiberFailure()
      }
      observed match {
        case Left(leaderboard.search.gen2.BeautyQSearchGen2RuntimeError.NotReady(dependencies)) =>
          assert(dependencies.map(_.stableId) == Vector("elasticsearch-baseline"))
        case other => fail(s"expected typed readiness failure, got $other")
      }
    }

    "execute through the blocking boundary when all dependencies serve" in {
      val context = BeautyQOrchestrationTestKit.eligible()
      val application = BeautyQSearchApplication.make(BeautyQOrchestrationTestKit.materialized, context.baselineService, context.embedding, context.qdrant)
      val runtime = leaderboard.search.gen2.BeautyQSearchGen2Runtime.make(
        application,
        BeautyQSupplementReadinessPolicy.evaluate(Set.empty),
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
      val application = BeautyQSearchApplication.make(BeautyQOrchestrationTestKit.materialized, context.baselineService, context.embedding, context.qdrant)
      val runtime = leaderboard.search.gen2.BeautyQSearchGen2Runtime.make(
        application,
        BeautyQSupplementReadinessPolicy.evaluate(Set(BeautyQSearchDependency.QdrantSupplement)),
      )

      val observed = Unsafe.unsafe { implicit unsafe =>
        Runtime.default.unsafe.run(runtime.execute(request).either).getOrThrowFiberFailure()
      }
      observed match {
        case Right(response) =>
          assert(response.supplementStatus == BeautyQSupplementStatus.NoAppend)
          assert(response.supplementCount == 0)
        case other => fail(s"expected baseline-only response, got $other")
      }
    }
  }

  private val request: BeautySearchRequestGen2 = BeautySearchRequestGen2(
    Some("relaxing appointment"),
    Vector.empty,
    Vector.empty,
    Vector.empty,
    BeautyQOrchestrationTestKit.page,
    None,
  )
}
