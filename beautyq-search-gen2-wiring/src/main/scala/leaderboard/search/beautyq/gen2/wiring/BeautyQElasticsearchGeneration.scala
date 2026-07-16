package leaderboard.search.beautyq.gen2.wiring

import leaderboard.model.MasterServiceOfferVariantId
import leaderboard.search.beautyq.gen2.contract.VariantSearchDocumentGen2
import leaderboard.search.beautyq.gen2.materialization.MaterializedBeautyQVariantDocuments
import leaderboard.search.gen2.elasticsearch.*

type CompiledBeautyQElasticsearchGeneration = CompiledElasticsearchGeneration[VariantSearchDocumentGen2, MasterServiceOfferVariantId]

/** Thin BeautyQ binding over the generic [[ElasticsearchGenerationCompiler]]: supplies
  * [[BeautyQElasticsearchPolicy.value]] (the complete policy, not just its `index` component) and the
  * materialized BeautyQ variant documents. Never inspects `VariantSearchDocumentGen2` fields manually,
  * rebuilds mapping/source, or reconstructs generation identity - the returned result is exactly the
  * generic compiler's own artifact type, and its `contractFingerprint` is the same complete fingerprint
  * `BeautyQSearchPlanCompiler` and `BeautyQElasticsearchBaseline` consume. */
object BeautyQElasticsearchGeneration {
  def compile(materialized: MaterializedBeautyQVariantDocuments): Either[ElasticsearchGenerationCompileError, CompiledBeautyQElasticsearchGeneration] =
    ElasticsearchGenerationCompiler.compile(BeautyQElasticsearchPolicy.value, materialized)
}
