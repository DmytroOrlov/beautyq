package leaderboard.search.beautyq.gen2.wiring

import leaderboard.search.beautyq.gen2.contract.*
import leaderboard.search.beautyq.gen2.wiring.testkit.BeautyQOrchestrationTestKit
import leaderboard.search.gen2.contract.{PageRequest, PageSize, PublicFieldName, PublicFilterInput, PublicFilterValue, PublicOperator}
import org.scalatest.wordspec.AnyWordSpec

final class BeautyQSearchApplicationSpec extends AnyWordSpec {
  "BeautyQSearchApplication" should {
    "execute the eligible request through validation, plan, baseline and orchestration" in {
      val context = BeautyQOrchestrationTestKit.eligible()
      val application = BeautyQSearchApplication.make(
        BeautyQOrchestrationTestKit.materialized,
        context.baselineService,
        context.embedding,
        context.qdrant,
      )

      application.execute(request(Some("relaxing appointment"))) match {
        case Right(result) =>
          assert(result.baseline.boundPlan eq result.evaluation.compiled.boundPlan)
          assert(result.baseline.boundPlan.identityHash == result.evaluation.compiled.boundPlan.identityHash)
          assert(result.status == BeautyQSupplementStatus.Supplemented)
        case Left(error) => fail(s"expected canonical application success, got $error")
      }
    }

    "stop before candidate execution for an ineligible request" in {
      val context = BeautyQOrchestrationTestKit.ineligible
      val application = BeautyQSearchApplication.make(
        BeautyQOrchestrationTestKit.materialized,
        context.baselineService,
        context.embedding,
        context.qdrant,
      )

      application.execute(request(None)) match {
        case Right(result) =>
          assert(result.status == BeautyQSupplementStatus.Ineligible)
          assert(result.appendedCandidates.isEmpty)
          assert(result.ineligibilityReason.contains(BeautyQCandidateIneligibility.NoSemanticQueryText))
        case Left(error) => fail(s"expected ineligible result, got $error")
      }
    }
  }

  private def request(semanticText: Option[String]): BeautySearchRequestGen2 =
    BeautySearchRequestGen2(
      semanticText,
      Vector(PublicFilterInput(PublicFieldName("service"), PublicOperator.Equal, PublicFilterValue.Scalar("manicure"), None)),
      Vector.empty,
      Vector.empty,
      PageRequest(None, PageSize.from(20).getOrElse(fail("expected valid page size"))),
      None,
    )
}
