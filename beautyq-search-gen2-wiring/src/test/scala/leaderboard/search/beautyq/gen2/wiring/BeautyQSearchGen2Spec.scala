package leaderboard.search.beautyq.gen2.wiring

import leaderboard.search.beautyq.gen2.contract.{BeautyQSearchDeclarations, BeautyQSearchPlanPolicy, BeautySearchRequestGen2}
import leaderboard.search.beautyq.gen2.materialization.{BeautyQSearchSnapshotSource, BeautyQSnapshotCanonicalRows, BeautyQVariantMaterializer, BeautyQVariantProjectionGen2}
import org.scalatest.wordspec.AnyWordSpec

final class BeautyQSearchGen2Spec extends AnyWordSpec {

  "BeautyQSearchGen2.contract" should {
    "be direct reference to BeautyQSearchDeclarations" in {
      assert(BeautyQSearchGen2.contract eq BeautyQSearchDeclarations)
    }
  }

  "BeautyQSearchGen2.input" should {
    "expose request as direct reference to BeautySearchRequestGen2" in {
      assert(BeautyQSearchGen2.input.request eq BeautySearchRequestGen2)
    }

    "expose intentParser as direct reference to BeautyQIntentParserGen2" in {
      assert(BeautyQSearchGen2.input.intentParser eq BeautyQIntentParserGen2)
    }
  }

  "BeautyQSearchGen2.materialization" should {
    "expose snapshot as direct reference to BeautyQSearchSnapshotSource" in {
      assert(BeautyQSearchGen2.materialization.snapshot eq BeautyQSearchSnapshotSource)
    }

    "expose canonicalRows as direct reference to BeautyQSnapshotCanonicalRows" in {
      assert(BeautyQSearchGen2.materialization.canonicalRows eq BeautyQSnapshotCanonicalRows)
    }

    "expose projection as direct reference to BeautyQVariantProjectionGen2" in {
      assert(BeautyQSearchGen2.materialization.projection eq BeautyQVariantProjectionGen2)
    }

    "expose materializer as direct reference to BeautyQVariantMaterializer" in {
      assert(BeautyQSearchGen2.materialization.materializer eq BeautyQVariantMaterializer)
    }
  }

  "BeautyQSearchGen2.plan" should {
    "expose policy as direct reference to BeautyQSearchPlanPolicy" in {
      assert(BeautyQSearchGen2.plan.policy eq BeautyQSearchPlanPolicy)
    }

    "expose groups as direct reference to BeautyQSearchPlanPolicy.groups" in {
      assert(BeautyQSearchGen2.plan.groups eq BeautyQSearchPlanPolicy.groups)
    }

    "expose compiler as direct reference to BeautyQSearchPlanCompiler" in {
      assert(BeautyQSearchGen2.plan.compiler eq BeautyQSearchPlanCompiler)
    }

    "expose candidateCompiler as direct reference to BeautyQCandidatePlanCompiler" in {
      assert(BeautyQSearchGen2.plan.candidateCompiler eq BeautyQCandidatePlanCompiler)
    }
  }

  "BeautyQSearchGen2.elasticsearch" should {
    "expose policy as direct reference to BeautyQElasticsearchPolicy" in {
      assert(BeautyQSearchGen2.elasticsearch.policy eq BeautyQElasticsearchPolicy)
    }

    "expose resources as direct reference to BeautyQSearchGen2ResourceNames" in {
      assert(BeautyQSearchGen2.elasticsearch.resources eq BeautyQSearchGen2ResourceNames)
    }

    "expose generation as direct reference to BeautyQElasticsearchGeneration" in {
      assert(BeautyQSearchGen2.elasticsearch.generation eq BeautyQElasticsearchGeneration)
    }

    "expose baseline as direct reference to BeautyQElasticsearchBaseline" in {
      assert(BeautyQSearchGen2.elasticsearch.baseline eq BeautyQElasticsearchBaseline)
    }

    "expose service as direct reference to BeautyQElasticsearchBaselineService" in {
      assert(BeautyQSearchGen2.elasticsearch.service eq BeautyQElasticsearchBaselineService)
    }
  }

  "BeautyQSearchGen2.qdrant" should {
    "expose policy as direct reference to BeautyQQdrantPolicy.policy" in {
      assert(BeautyQSearchGen2.qdrant.policy eq BeautyQQdrantPolicy.policy)
    }

    "expose resources as direct reference to BeautyQSearchGen2ResourceNames" in {
      assert(BeautyQSearchGen2.qdrant.resources eq BeautyQSearchGen2ResourceNames)
    }

    "expose runtime as direct reference to BeautyQQdrantRuntime" in {
      assert(BeautyQSearchGen2.qdrant.runtime eq BeautyQQdrantRuntime)
    }

    "expose hydrationPolicy as direct reference to BeautyQQdrantHydrationPolicy.policy" in {
      assert(BeautyQSearchGen2.qdrant.hydrationPolicy eq BeautyQQdrantHydrationPolicy.policy)
    }

    "expose candidates as direct reference to BeautyQQdrantCandidatePipeline" in {
      assert(BeautyQSearchGen2.qdrant.candidates eq BeautyQQdrantCandidatePipeline)
    }
  }

  "BeautyQSearchGen2.supplement" should {
    "expose policy as direct reference to BeautyQSupplementPolicy" in {
      assert(BeautyQSearchGen2.supplement.policy eq BeautyQSupplementPolicy)
    }

    "expose startupPolicy as direct reference to SupplementStartupPolicy" in {
      assert(BeautyQSearchGen2.supplement.startupPolicy eq SupplementStartupPolicy)
    }

    "expose startupServingStatus as direct reference to StartupServingStatus" in {
      assert(BeautyQSearchGen2.supplement.startupServingStatus eq StartupServingStatus)
    }

    "expose orchestrator as direct reference to BeautyQSearchOrchestrator" in {
      assert(BeautyQSearchGen2.supplement.orchestrator eq BeautyQSearchOrchestrator)
    }
  }

  "BeautyQSearchGen2.application" should {
    "expose the one executable application owner" in {
      assert(BeautyQSearchGen2.application eq BeautyQSearchApplication)
      assert(BeautyQSearchGen2.elasticsearch.generationApplication eq BeautyQSearchGenerationApplication)
    }

    "execute the native request through the one application path" in {
      val context = leaderboard.search.beautyq.gen2.wiring.testkit.BeautyQOrchestrationTestKit.eligible()
      val application = BeautyQSearchApplication.make(
        leaderboard.search.beautyq.gen2.wiring.testkit.BeautyQOrchestrationTestKit.materialized,
        context.baselineService,
        context.embedding,
        context.qdrant,
      )
      val request = leaderboard.search.beautyq.gen2.contract.BeautySearchRequestGen2(
        Some("relaxing appointment"),
        Vector(leaderboard.search.gen2.contract.PublicFilterInput(
          leaderboard.search.gen2.contract.PublicFieldName("service"),
          leaderboard.search.gen2.contract.PublicOperator.Equal,
          leaderboard.search.gen2.contract.PublicFilterValue.Scalar("manicure"),
          None,
        )),
        Vector.empty,
        Vector.empty,
        leaderboard.search.beautyq.gen2.wiring.testkit.BeautyQOrchestrationTestKit.page,
        None,
      )

      application.execute(request) match {
        case Right(result) =>
          assert(result.status == BeautyQSupplementStatus.Supplemented)
          BeautyQSearchResponseGen2Projector.projectWithoutStatus(result) match {
            case Right(response) => assert(response.supplementCount == result.appendedCandidates.size)
            case Left(error) => fail(s"expected projected Gen2 response, got $error")
          }
        case Left(error) => fail(s"expected application result, got $error")
      }
    }
  }
}
