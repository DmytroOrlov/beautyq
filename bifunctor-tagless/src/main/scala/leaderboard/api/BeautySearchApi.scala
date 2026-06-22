package leaderboard.api

import cats.effect.Async
import izumi.functional.bio.Error2
import leaderboard.http.HttpApiFailure
import leaderboard.http.tapir.BeautySearchTapirEndpoints
import leaderboard.search.{BeautySearchRequestContract, BeautySearchService, UserSearchInput}
import org.http4s.HttpRoutes
import sttp.tapir.server.http4s.Http4sServerInterpreter

class BeautySearchApi[F[+_, +_]: Error2](
  beautySearchService: BeautySearchService[F],
  tapirEndpoints: BeautySearchTapirEndpoints,
  servingGate: BeautySearchServingGate = BeautySearchServingGate.disabled,
)(implicit
  async: Async[F[Throwable, _]]
) extends HttpApi[F] {
  def http: HttpRoutes[F[Throwable, _]] =
    Http4sServerInterpreter[F[Throwable, _]]().toRoutes {
      import tapirEndpoints.*
      List(
        searchBeauty.serverLogic[F[Throwable, _]] {
          input =>
            validate(input) match {
              case Left(failure) =>
                async.pure(Left(failure))
              case Right(_) if servingGate.rejectsServing =>
                // Gate explicitly enabled but serving readiness not satisfied: reject with HTTP 503.
                // Validation still runs first, so invalid requests keep returning 400.
                async.pure(Left(HttpApiFailure.ServiceUnavailable.beautySearchNotReady))
              case Right(validInput) =>
                HttpApiFailure.fromQueryEffect(beautySearchService.search(validInput))
            }
        }
      )
    }

  private def validate(input: UserSearchInput): Either[HttpApiFailure.BadRequest, UserSearchInput] =
    if (input.query.trim.isEmpty) {
      Left(badRequest(BeautySearchRequestContract.InvalidQuery))
    } else if (input.limit < BeautySearchRequestContract.MinLimit || input.limit > BeautySearchRequestContract.MaxLimit) {
      Left(badRequest(BeautySearchRequestContract.InvalidLimit))
    } else if (input.userLat.exists(latitude =>
        latitude < BeautySearchRequestContract.MinLatitude || latitude > BeautySearchRequestContract.MaxLatitude
      )) {
      Left(badRequest(BeautySearchRequestContract.InvalidLatitude))
    } else if (input.userLon.exists(longitude =>
        longitude < BeautySearchRequestContract.MinLongitude || longitude > BeautySearchRequestContract.MaxLongitude
      )) {
      Left(badRequest(BeautySearchRequestContract.InvalidLongitude))
    } else {
      Right(input)
    }

  private def badRequest(error: BeautySearchRequestContract.SemanticError): HttpApiFailure.BadRequest =
    HttpApiFailure.BadRequest(error.code, error.message)
}
