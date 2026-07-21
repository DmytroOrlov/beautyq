package leaderboard.search.beautyq.gen2.wiring

import leaderboard.model.MasterServiceOfferVariantId
import leaderboard.search.beautyq.gen2.wiring.testkit.BeautyQOrchestrationTestKit
import leaderboard.search.gen2.contract.PlannedAlgebraTrace
import org.scalatest.wordspec.AnyWordSpec

import java.util.UUID

final class BeautyQSearchResponseGen2ProjectorSpec extends AnyWordSpec {
  "BeautyQSearchResponseGen2Projector" should {
    "derive one response from the compiler-owned orchestrator aggregate" in {
      val context = BeautyQOrchestrationTestKit.eligible(Vector(BeautyQOrchestrationTestKit.document.variantId))
      val result = BeautyQSearchOrchestrator.execute(
        context.baseline,
        context.evaluation,
        BeautyQOrchestrationTestKit.materialized,
        context.embedding,
        context.qdrant,
        context.baselineService,
      ) match {
        case Right(value) => value
        case Left(error) => fail(s"expected orchestrated result, got $error")
      }

      BeautyQSearchResponseGen2Projector.project(result) match {
        case Right(response) =>
          assert(response.supplementCount == result.supplementCount)
          assert(response.supplementStatus == result.status)
          assert(response.supplementStatusCode == result.statusCode)
          assert(response.hits.take(result.baselineResult.hits.size).forall(_.origin == BeautyQSearchHitOrigin.ElasticsearchBaseline))
          assert(response.totalRelation == result.baselineResult.totalRelation)
          assert(response.appliedFilters.map(_.constraint) == result.evaluation.compiled.plan.appliedFilters.map(filter => PlannedAlgebraTrace.constraint(filter.source.constraint)))
          assert(response.suppressedFilters.map(_.constraint) == result.evaluation.compiled.plan.diagnostics.suppressedFilters.map(filter => PlannedAlgebraTrace.constraint(filter.source.constraint)))
          assert(response.diagnostics == result.baselineResult.diagnostics)
        case Left(error) => fail(s"expected response projection, got $error")
      }

      val supplementContext = BeautyQOrchestrationTestKit.eligible()
      val supplemented = BeautyQSearchOrchestrator.execute(
        supplementContext.baseline,
        supplementContext.evaluation,
        BeautyQOrchestrationTestKit.materialized,
        supplementContext.embedding,
        supplementContext.qdrant,
        supplementContext.baselineService,
      ) match {
        case Right(value) => value
        case Left(error) => fail(s"expected supplement result, got $error")
      }
      BeautyQSearchResponseGen2Projector.project(supplemented) match {
        case Right(response) => assert(response.hits.forall(_.origin == BeautyQSearchHitOrigin.QdrantSupplement))
        case Left(error) => fail(s"expected supplement projection, got $error")
      }
    }

    "keep baseline hits as an ordered prefix before appended supplement hits" in {
      val baselineId = MasterServiceOfferVariantId(UUID.fromString("00000000-0000-0000-0000-000000000007"))
      val context = BeautyQOrchestrationTestKit.eligible(Vector(baselineId), membershipIds = Some(Vector.empty))
      val result = BeautyQSearchOrchestrator.execute(
        context.baseline,
        context.evaluation,
        BeautyQOrchestrationTestKit.materialized,
        context.embedding,
        context.qdrant,
        context.baselineService,
      ) match {
        case Right(value) => value
        case Left(error) => fail(s"expected mixed orchestrated result, got $error")
      }

      BeautyQSearchResponseGen2Projector.project(result) match {
        case Right(response) =>
          response.hits match {
            case Vector(first, rest @ _*) =>
              assert(first.id == baselineId.value.toString)
              assert(first.origin == BeautyQSearchHitOrigin.ElasticsearchBaseline)
              assert(rest.nonEmpty)
              assert(rest.forall(_.origin == BeautyQSearchHitOrigin.QdrantSupplement))
              assert(response.hits.map(_.id).headOption.contains(baselineId.value.toString))
            case other => fail(s"expected baseline prefix and appended hits, got $other")
          }
        case Left(error) => fail(s"expected mixed response projection, got $error")
      }
    }
  }
}
