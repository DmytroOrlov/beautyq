package leaderboard.search.gen2

import io.circe.Json
import leaderboard.api.BeautySearchGen2Service
import leaderboard.http.HttpApiFailure
import leaderboard.search.beautyq.gen2.contract.BeautySearchRequestGen2
import leaderboard.search.beautyq.gen2.wiring.{BeautyQSearchApplicationError, BeautyQSearchResponseGen2, StartupServingStatus}
import leaderboard.search.beautyq.gen2.wiring.BeautyQElasticsearchBaselineServiceError
import leaderboard.search.gen2.elasticsearch.lifecycle.ElasticsearchGenerationLifecycleError
import zio.{IO, ZIO}

import java.time.{Clock, Duration, Instant}

final class BeautyQSearchGen2HttpService(
  runtime: BeautyQSearchGen2Runtime,
  clock: Clock = Clock.systemUTC(),
) extends BeautySearchGen2Service[IO] {
  def execute(request: BeautySearchRequestGen2): IO[HttpApiFailure, BeautyQSearchResponseGen2] =
    runtime.execute(request).mapError(BeautyQSearchGen2HttpService.toHttpApiFailure)

  def status: IO[HttpApiFailure, Json] =
    ZIO.succeed(BeautyQSearchGen2HttpService.encodeStatus(runtime.startupStatus, runtime.startupEvidence, clock.instant()))
}

object BeautyQSearchGen2HttpService {
  def encodeStatus(
    status: StartupServingStatus,
    evidence: Option[BeautyQSearchStartupEvidence] = None,
    now: Instant = Instant.now(),
  ): Json = {
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

    val snapshotJson = evidence match {
      case Some(value) => Json.obj(
        "capturedAt" -> Json.fromString(value.snapshotCapturedAt.toString),
        "sourceRevision" -> value.sourceRevision.map(Json.fromString).getOrElse(Json.Null),
        "sourceContentFingerprint" -> Json.fromString(status.sourceContentFingerprint),
        "projectedDocumentsFingerprint" -> Json.fromString(status.projectedDocumentsFingerprint),
        "ageSeconds" -> Json.fromLong(nonNegativeSeconds(value.snapshotCapturedAt, now)),
      )
      case None => Json.obj(
        "capturedAt" -> Json.Null,
        "sourceRevision" -> Json.Null,
        "sourceContentFingerprint" -> Json.fromString(status.sourceContentFingerprint),
        "projectedDocumentsFingerprint" -> Json.fromString(status.projectedDocumentsFingerprint),
        "ageSeconds" -> Json.Null,
      )
    }

    val startupDurationsJson = evidence.map { value =>
      Json.obj(
        "materializationNanos" -> Json.fromLong(value.materializationDurationNanos),
        "activationNanos" -> Json.fromLong(value.activationDurationNanos),
      )
    }.getOrElse(Json.Null)

    val activeGenerationsJson = evidence.map { value =>
      Json.obj(
        "activatedAt" -> Json.fromString(value.activatedAt.toString),
        "ageSeconds" -> Json.fromLong(nonNegativeSeconds(value.activatedAt, now)),
        "elasticsearch" -> Json.obj(
          "reference" -> Json.fromString(status.elasticsearchReference),
          "physicalTarget" -> Json.fromString(status.elasticsearchPhysicalTarget),
        ),
        "qdrant" -> qdrantJson,
      )
    }.getOrElse(Json.obj(
      "activatedAt" -> Json.Null,
      "ageSeconds" -> Json.Null,
      "elasticsearch" -> Json.obj(
        "reference" -> Json.fromString(status.elasticsearchReference),
        "physicalTarget" -> Json.fromString(status.elasticsearchPhysicalTarget),
      ),
      "qdrant" -> qdrantJson,
    ))

    Json.obj(
      "live" -> Json.fromBoolean(true),
      "ready" -> Json.fromBoolean(true),
      "condition" -> Json.fromString(status.condition),
      "startupPolicy" -> Json.fromString(status.policy.stableCode),
      "servingMode" -> Json.fromString(status.servingMode.modeCode),
      "restartRequired" -> Json.fromBoolean(status.restartRequired),
      "reason" -> reasonJson,
      "observedAt" -> Json.fromString(now.toString),
      "snapshot" -> snapshotJson,
      "startupDurations" -> startupDurationsJson,
      "activeGenerations" -> activeGenerationsJson,
    )
  }

  private def nonNegativeSeconds(from: Instant, to: Instant): Long =
    math.max(0L, Duration.between(from, to).getSeconds)

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
      case BeautyQSearchGen2RuntimeError.Application(BeautyQSearchApplicationError.Input(errors)) =>
        val budgetExceeded = errors.toVector.exists {
          case _: leaderboard.search.beautyq.gen2.contract.BeautySearchRequestError.QueryTooLong => true
          case _: leaderboard.search.beautyq.gen2.contract.BeautySearchRequestError.TooManyFilters => true
          case _: leaderboard.search.beautyq.gen2.contract.BeautySearchRequestError.TooManyRequestedFacets => true
          case _: leaderboard.search.beautyq.gen2.contract.BeautySearchRequestError.TooManySorts => true
          case _: leaderboard.search.beautyq.gen2.contract.BeautySearchRequestError.PageSizeTooLarge => true
          case _ => false
        }
        HttpApiFailure.BadRequest(
          if (budgetExceeded) "request_budget_exceeded" else "invalid_gen2_request",
          errors.toVector.mkString("; "),
        )
      case BeautyQSearchGen2RuntimeError.Application(_) =>
        HttpApiFailure.InternalServerError
      case BeautyQSearchGen2RuntimeError.Projection(_) =>
        HttpApiFailure.InternalServerError
      case BeautyQSearchGen2RuntimeError.BlockingFailure(_) =>
        HttpApiFailure.InternalServerError
    }
}
