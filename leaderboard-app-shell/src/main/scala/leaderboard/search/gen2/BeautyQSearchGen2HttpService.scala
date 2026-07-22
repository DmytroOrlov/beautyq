package leaderboard.search.gen2

import leaderboard.api.BeautySearchGen2Service
import leaderboard.http.HttpApiFailure
import leaderboard.search.beautyq.gen2.contract.BeautySearchRequestGen2
import leaderboard.search.beautyq.gen2.wiring.{BeautyQSearchApplicationError, BeautyQSearchResponseGen2}
import leaderboard.search.beautyq.gen2.wiring.BeautyQElasticsearchBaselineServiceError
import leaderboard.search.gen2.elasticsearch.lifecycle.ElasticsearchGenerationLifecycleError
import zio.IO

/** App-shell adapter for the default native Gen2 endpoint. The route sees only the
  * HTTP error algebra; Gen2 application and projection ownership stay below
  * this adapter. */
final class BeautyQSearchGen2HttpService(
  runtime: BeautyQSearchGen2Runtime,
) extends BeautySearchGen2Service[IO] {
  def execute(request: BeautySearchRequestGen2): IO[HttpApiFailure, BeautyQSearchResponseGen2] =
    runtime.execute(request).mapError(BeautyQSearchGen2HttpService.toHttpApiFailure)
}

object BeautyQSearchGen2HttpService {
  private[search] def toHttpApiFailure(
    error: BeautyQSearchGen2RuntimeError,
  ): HttpApiFailure =
    error match {
      case BeautyQSearchGen2RuntimeError.NotReady(_) =>
        HttpApiFailure.ServiceUnavailable("beauty_search_gen2_not_ready", "Beauty Search Gen2 is not ready")
      case BeautyQSearchGen2RuntimeError.Application(
            BeautyQSearchApplicationError.Baseline(
              BeautyQElasticsearchBaselineServiceError.Lifecycle(
                ElasticsearchGenerationLifecycleError.StaleGeneration(_)
              )
            )
          ) =>
        HttpApiFailure.Conflict(
          "stale_search_cursor",
          "Search cursor refers to a deleted generation; restart pagination without the cursor",
        )
      case BeautyQSearchGen2RuntimeError.Application(_) =>
        HttpApiFailure.InternalServerError
      case BeautyQSearchGen2RuntimeError.Projection(_) =>
        HttpApiFailure.InternalServerError
      case BeautyQSearchGen2RuntimeError.BlockingFailure(_) =>
        HttpApiFailure.InternalServerError
    }
}
