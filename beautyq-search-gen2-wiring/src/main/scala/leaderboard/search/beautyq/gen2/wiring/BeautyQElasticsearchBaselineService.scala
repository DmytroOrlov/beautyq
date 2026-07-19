package leaderboard.search.beautyq.gen2.wiring

import leaderboard.search.gen2.elasticsearch.*
import leaderboard.search.gen2.elasticsearch.lifecycle.*

import java.time.Clock

sealed trait BeautyQElasticsearchBaselineServiceError
object BeautyQElasticsearchBaselineServiceError {
  final case class Lifecycle(error: ElasticsearchGenerationLifecycleError) extends BeautyQElasticsearchBaselineServiceError
  final case class RequestCompile(error: ElasticsearchSearchRequestCompileError) extends BeautyQElasticsearchBaselineServiceError
  final case class Search(error: ElasticsearchBaselineServiceError) extends BeautyQElasticsearchBaselineServiceError
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
    BeautyQElasticsearchBaseline.compileRequest(compiledPlan)
      .left.map(BeautyQElasticsearchBaselineServiceError.RequestCompile.apply)
      .flatMap(prepared => baseline.search(prepared).left.map(BeautyQElasticsearchBaselineServiceError.Search.apply))
      .flatMap(result => BeautyQElasticsearchSearchResult.project(result).left.map(BeautyQElasticsearchBaselineServiceError.Projection.apply))
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
