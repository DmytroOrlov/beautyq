package leaderboard.api

import cats.effect.Async
import io.circe.Json
import izumi.functional.bio.Error2
import leaderboard.http.{BeautySearchGen2Json, HttpApiFailure}
import leaderboard.http.tapir.BeautySearchGen2TapirEndpoints
import leaderboard.search.beautyq.gen2.contract.BeautySearchRequestGen2
import leaderboard.search.beautyq.gen2.wiring.BeautyQSearchResponseGen2
import org.http4s.HttpRoutes
import sttp.tapir.server.http4s.Http4sServerInterpreter

trait BeautySearchGen2Service[F[+_, +_]] {
  def execute(request: BeautySearchRequestGen2): F[HttpApiFailure, BeautyQSearchResponseGen2]
  def status: F[HttpApiFailure, Json]
}

final class BeautySearchGen2Api[F[+_, +_]: Error2](
  service: BeautySearchGen2Service[F],
  endpoints: BeautySearchGen2TapirEndpoints,
) (implicit async: Async[F[Throwable, _]]) extends HttpApi[F] {
  def http: HttpRoutes[F[Throwable, _]] =
    Http4sServerInterpreter[F[Throwable, _]]().toRoutes {
      import endpoints.*
      List(
        searchBeautyGen2.serverLogic[F[Throwable, _]] { raw =>
          BeautySearchGen2Json.decodeTransportBody(raw) match {
            case Left(error @ (_: BeautySearchGen2Json.RequestDecodeError.BodyTooLarge | _: BeautySearchGen2Json.RequestDecodeError.CursorTooLarge)) =>
              async.pure(Left(HttpApiFailure.BadRequest("request_budget_exceeded", error.message)))
            case Left(error) => async.pure(Left(HttpApiFailure.BadRequest("invalid_gen2_request", error.message)))
            case Right(request) =>
              async.map(service.execute(request).attempt) {
                case Left(error) => Left(error)
                case Right(response) => Right(BeautySearchGen2Json.encodeResponse(response))
              }
          }
        },
        statusBeautyGen2.serverLogic[F[Throwable, _]] { _ =>
          async.map(service.status.attempt) {
            case Left(error) => Left(error)
            case Right(json) => Right(json)
          }
        },
      )
    }
}
