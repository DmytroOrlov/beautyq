package leaderboard.search.beautyq.gen2.wiring

import leaderboard.search.beautyq.gen2.contract.{BeautyQSearchDeclarations, BeautyQSearchPlanPolicy, BeautyQSearchRequestBudget, BeautySearchRequestGen2}
import leaderboard.search.beautyq.gen2.materialization.{
  BeautyQSearchSnapshotSource,
  BeautyQSnapshotCanonicalRows,
  BeautyQVariantMaterializer,
  BeautyQVariantProjectionGen2,
}

object BeautyQSearchGen2 {
  val contract = BeautyQSearchDeclarations

  object input {
    val request      = BeautySearchRequestGen2
    val budget       = BeautyQSearchRequestBudget
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
    val generationApplication = BeautyQSearchGenerationApplication
  }

  object qdrant {
    val policy           = BeautyQQdrantPolicy.policy
    val resources        = BeautyQSearchGen2ResourceNames
    val runtime           = BeautyQQdrantRuntime
    val hydrationPolicy   = BeautyQQdrantHydrationPolicy.policy
    val candidates        = BeautyQQdrantCandidatePipeline
  }

  object supplement {
    val policy               = BeautyQSupplementPolicy
    val startupPolicy        = SupplementStartupPolicy
    val orchestrator         = BeautyQSearchOrchestrator
    val startupServingStatus = StartupServingStatus
  }

  val application = BeautyQSearchApplication
}
