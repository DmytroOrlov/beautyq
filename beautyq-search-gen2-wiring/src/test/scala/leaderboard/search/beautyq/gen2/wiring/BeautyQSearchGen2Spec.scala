package leaderboard.search.beautyq.gen2.wiring

import leaderboard.search.beautyq.gen2.contract.{BeautyQSearchDeclarations, BeautyQSearchPlanPolicy, BeautySearchRequestGen2}
import leaderboard.search.beautyq.gen2.materialization.{
  BeautyQSearchSnapshotSource,
  BeautyQSnapshotCanonicalRows,
  BeautyQVariantMaterializer,
  BeautyQVariantProjectionGen2,
}
import org.scalatest.wordspec.AnyWordSpec

final class BeautyQSearchGen2Spec extends AnyWordSpec {
  "BeautyQSearchGen2" should {
    "reference every implemented composition owner without recreating policy" in {
      assert(BeautyQSearchGen2.contract eq BeautyQSearchDeclarations)

      assert(BeautyQSearchGen2.input.request eq BeautySearchRequestGen2)
      assert(BeautyQSearchGen2.input.intentParser eq BeautyQIntentParserGen2)

      assert(BeautyQSearchGen2.materialization.snapshot eq BeautyQSearchSnapshotSource)
      assert(BeautyQSearchGen2.materialization.canonicalRows eq BeautyQSnapshotCanonicalRows)
      assert(BeautyQSearchGen2.materialization.projection eq BeautyQVariantProjectionGen2)
      assert(BeautyQSearchGen2.materialization.materializer eq BeautyQVariantMaterializer)

      assert(BeautyQSearchGen2.plan.policy eq BeautyQSearchPlanPolicy)
      assert(BeautyQSearchGen2.plan.groups eq BeautyQSearchPlanPolicy.groups)
      assert(BeautyQSearchGen2.plan.compiler eq BeautyQSearchPlanCompiler)
      assert(BeautyQSearchGen2.plan.candidateCompiler eq BeautyQCandidatePlanCompiler)

      assert(BeautyQSearchGen2.elasticsearch.policy eq BeautyQElasticsearchPolicy)
      assert(BeautyQSearchGen2.elasticsearch.resources eq BeautyQSearchGen2ResourceNames)
      assert(BeautyQSearchGen2.elasticsearch.generation eq BeautyQElasticsearchGeneration)
      assert(BeautyQSearchGen2.elasticsearch.baseline eq BeautyQElasticsearchBaseline)
      assert(BeautyQSearchGen2.elasticsearch.service eq BeautyQElasticsearchBaselineService)

      assert(BeautyQSearchGen2.qdrant.hydrationPolicy eq BeautyQQdrantHydrationPolicy.policy)
      assert(BeautyQSearchGen2.qdrant.candidates eq BeautyQQdrantCandidatePipeline)
      assert(BeautyQSearchGen2.qdrant.policy eq BeautyQQdrantPolicy.policy)
      assert(BeautyQSearchGen2.qdrant.resources eq BeautyQSearchGen2ResourceNames)
      assert(BeautyQSearchGen2.qdrant.runtime eq BeautyQQdrantRuntime)
    }
  }
}
