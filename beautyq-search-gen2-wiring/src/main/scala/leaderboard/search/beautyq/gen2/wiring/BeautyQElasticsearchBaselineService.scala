package leaderboard.search.beautyq.gen2.wiring

import leaderboard.model.MasterServiceOfferVariantId
import leaderboard.search.beautyq.gen2.contract.VariantSearchDocumentGen2
import leaderboard.search.gen2.elasticsearch.*
import leaderboard.search.gen2.elasticsearch.lifecycle.*
import leaderboard.search.gen2.core.supplement.BaselineMembershipResult

import java.time.Clock

sealed trait BeautyQElasticsearchBaselineServiceError
object BeautyQElasticsearchBaselineServiceError {
  final case class Lifecycle(error: ElasticsearchGenerationLifecycleError) extends BeautyQElasticsearchBaselineServiceError
  final case class RequestCompile(error: ElasticsearchSearchRequestCompileError) extends BeautyQElasticsearchBaselineServiceError
  final case class Search(error: ElasticsearchBaselineServiceError) extends BeautyQElasticsearchBaselineServiceError
  final case class Membership(error: ElasticsearchBaselineMembershipError) extends BeautyQElasticsearchBaselineServiceError
  final case class Projection(error: BeautyQCarouselProjectionError) extends BeautyQElasticsearchBaselineServiceError
}

sealed trait BeautyQElasticsearchBaselineServiceMakeError
object BeautyQElasticsearchBaselineServiceMakeError {
  final case class LifecycleConfig(error: ElasticsearchGenerationLifecycleConfigError) extends BeautyQElasticsearchBaselineServiceMakeError
}

/** Thin BeautyQ composition over the generic generation lifecycle and baseline service. Repository
  * snapshot loading/materialization remains the Brick 8 runtime composition owner's responsibility. */
final class BeautyQElasticsearchBaselineService private (
  lifecycle: ElasticsearchGenerationLifecycle,
  baseline: ElasticsearchBaselineService,
) {
  def activate(
    generation: CompiledBeautyQElasticsearchGeneration,
  ): Either[BeautyQElasticsearchBaselineServiceError, LifecycleResolvedElasticsearchGeneration] =
    lifecycle.activate(generation).left.map(BeautyQElasticsearchBaselineServiceError.Lifecycle.apply)

  def search(
    compiledPlan: CompiledBeautyQSearchPlan,
  ): Either[BeautyQElasticsearchBaselineServiceError, BeautyQElasticsearchSearchResult] =
    searchBound(compiledPlan)
      .flatMap(result => BeautyQElasticsearchSearchResult.project(result.result).left.map(BeautyQElasticsearchBaselineServiceError.Projection.apply))

  /** The one BeautyQ-owned baseline execution path. The bound result carries the
    * lifecycle-authorized target and generation evidence into orchestration. */
  def searchBound(
    compiledPlan: CompiledBeautyQSearchPlan,
  ): Either[
    BeautyQElasticsearchBaselineServiceError,
    BoundElasticsearchBaselineResult[VariantSearchDocumentGen2, MasterServiceOfferVariantId],
  ] =
    BeautyQElasticsearchBaseline.compileRequest(compiledPlan)
      .left.map(BeautyQElasticsearchBaselineServiceError.RequestCompile.apply)
      .flatMap(prepared => baseline.searchBound(prepared).left.map(BeautyQElasticsearchBaselineServiceError.Search.apply))

  /** Membership is always evaluated against the already executed bound baseline. */
  def membership(
    bound: BoundElasticsearchBaselineResult[VariantSearchDocumentGen2, MasterServiceOfferVariantId],
    candidateIds: Vector[MasterServiceOfferVariantId],
  ): Either[BeautyQElasticsearchBaselineServiceError, BaselineMembershipResult[MasterServiceOfferVariantId]] =
    baseline.membership(bound, candidateIds).left.map(BeautyQElasticsearchBaselineServiceError.Membership.apply)
}

object BeautyQElasticsearchBaselineService {
  def make(
    client: ElasticsearchGen2JsonClient,
    clock: Clock,
    batching: ElasticsearchBulkBatchingPolicy,
  ): Either[BeautyQElasticsearchBaselineServiceMakeError, BeautyQElasticsearchBaselineService] =
    ElasticsearchGenerationLifecycleConfig.create(
      alias = BeautyQSearchGen2ResourceNames.ElasticsearchAlias,
      physicalIndexPrefix = BeautyQSearchGen2ResourceNames.ElasticsearchPhysicalIndexPrefix,
      batching = batching,
    ).left.map(BeautyQElasticsearchBaselineServiceMakeError.LifecycleConfig.apply).map { config =>
      val lifecycle = new ElasticsearchGenerationLifecycle(client, config, clock)
      new BeautyQElasticsearchBaselineService(lifecycle, new ElasticsearchBaselineService(lifecycle))
    }
}
