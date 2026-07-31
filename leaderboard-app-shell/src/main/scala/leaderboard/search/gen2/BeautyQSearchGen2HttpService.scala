package leaderboard.search.gen2

import io.circe.Json
import leaderboard.api.BeautySearchGen2Service
import leaderboard.http.HttpApiFailure
import leaderboard.search.beautyq.gen2.contract.BeautySearchRequestGen2
import leaderboard.search.beautyq.gen2.wiring.{BeautyQSearchApplicationError, BeautyQSearchResponseGen2, StartupServingStatus}
import leaderboard.search.beautyq.gen2.wiring.BeautyQElasticsearchBaselineServiceError
import leaderboard.search.gen2.elasticsearch.lifecycle.ElasticsearchGenerationLifecycleError
import zio.{IO, ZIO}

final class BeautyQSearchGen2HttpService(
  runtime: BeautyQSearchGen2Runtime,
) extends BeautySearchGen2Service[IO] {
  def execute(request: BeautySearchRequestGen2): IO[HttpApiFailure, BeautyQSearchResponseGen2] =
    runtime.execute(request).mapError(BeautyQSearchGen2HttpService.toHttpApiFailure)

  def status: IO[HttpApiFailure, Json] =
    ZIO.succeed(BeautyQSearchGen2HttpService.encodeStatus(runtime.startupStatus))
}

object BeautyQSearchGen2HttpService {
  def encodeStatus(status: StartupServingStatus): Json = {
    val reasonJson = status.reason.map { r =>
      Json.obj(
        "code" -> Json.fromString(r.code),
        "message" -> Json.fromString(r.message),
        "detail" -> Json.fromString(r.detail),
      )
    }.getOrElse(Json.Null)

    val qdrantJson = status.qdrantGenerationId.map { genId =>
      Json.obj(
        "generationId" -> Json.fromString(genId),
        "physicalCollection" -> Json.fromString(status.qdrantPhysicalCollection.getOrElse("")),
      )
    }.getOrElse(Json.Null)

    Json.obj(
      "live" -> Json.fromBoolean(true),
      "ready" -> Json.fromBoolean(true),
      "condition" -> Json.fromString(status.condition),
      "startupPolicy" -> Json.fromString(status.policy.stableCode),
      "servingMode" -> Json.fromString(status.servingMode.modeCode),
      "restartRequired" -> Json.fromBoolean(status.restartRequired),
      "reason" -> reasonJson,
      "snapshot" -> Json.obj(
        "sourceContentFingerprint" -> Json.fromString(status.sourceContentFingerprint),
        "projectedDocumentsFingerprint" -> Json.fromString(status.projectedDocumentsFingerprint),
      ),
      "activeGenerations" -> Json.obj(
        "elasticsearch" -> Json.obj(
          "reference" -> Json.fromString(status.elasticsearchReference),
          "physicalTarget" -> Json.fromString(status.elasticsearchPhysicalTarget),
        ),
        "qdrant" -> qdrantJson,
      ),
    )
  }

  private[search] def toHttpApiFailure(
    error: BeautyQSearchGen2RuntimeError,
  ): HttpApiFailure =
    error match {
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
