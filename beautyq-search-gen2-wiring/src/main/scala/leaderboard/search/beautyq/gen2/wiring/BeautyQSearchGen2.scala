package leaderboard.search.beautyq.gen2.wiring

import leaderboard.search.beautyq.gen2.contract.{BeautyQSearchDeclarations, BeautyQSearchPlanPolicy, BeautySearchRequestGen2}
import leaderboard.search.beautyq.gen2.materialization.{
  BeautyQSearchSnapshotSource,
  BeautyQSnapshotCanonicalRows,
  BeautyQVariantMaterializer,
  BeautyQVariantProjectionGen2,
}

/** Navigation facade for the complete implemented BeautyQ Search Gen2 composition.
  *
  * Every member is a direct reference to its owning declaration or executable composition. This
  * object owns no policy and grows only when another implementation branch becomes real.
  */
object BeautyQSearchGen2 {
  val contract = BeautyQSearchDeclarations

  object input {
    val request      = BeautySearchRequestGen2
    val intentParser = BeautyQIntentParserGen2
  }

  object materialization {
    val snapshot      = BeautyQSearchSnapshotSource
    val canonicalRows = BeautyQSnapshotCanonicalRows
    val projection    = BeautyQVariantProjectionGen2
    val materializer  = BeautyQVariantMaterializer
  }

  object plan {
    val policy           = BeautyQSearchPlanPolicy
    val groups           = BeautyQSearchPlanPolicy.groups
    val compiler         = BeautyQSearchPlanCompiler
    val candidateCompiler = BeautyQCandidatePlanCompiler
  }

  object elasticsearch {
    val policy     = BeautyQElasticsearchPolicy
    val resources  = BeautyQSearchGen2ResourceNames
    val generation = BeautyQElasticsearchGeneration
    val baseline   = BeautyQElasticsearchBaseline
    val service    = BeautyQElasticsearchBaselineService
  }

  object qdrant {
    val policy           = BeautyQQdrantPolicy.policy
    val resources        = BeautyQSearchGen2ResourceNames
    val runtime           = BeautyQQdrantRuntime
    val hydrationPolicy   = BeautyQQdrantHydrationPolicy.policy
    val candidates        = BeautyQQdrantCandidatePipeline
  }

  object supplement {
    val policy       = BeautyQSupplementPolicy
    val readiness    = BeautyQSupplementReadinessPolicy
    val orchestrator = BeautyQSearchOrchestrator
  }
}
