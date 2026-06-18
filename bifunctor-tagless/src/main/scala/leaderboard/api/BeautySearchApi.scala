package leaderboard.api

import cats.effect.Async
import izumi.functional.bio.Error2
import leaderboard.http.HttpApiFailure
import leaderboard.http.tapir.BeautySearchTapirEndpoints
import leaderboard.search.{BeautySearchService, UserSearchInput}
import leaderboard.search.dsl.BeautySearchSpecV1
import org.http4s.HttpRoutes
import sttp.tapir.server.http4s.Http4sServerInterpreter

class BeautySearchApi[F[+_, +_]: Error2](
  beautySearchService: BeautySearchService[F],
  tapirEndpoints: BeautySearchTapirEndpoints,
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
              case Right(validInput) =>
                HttpApiFailure.fromQueryEffect(beautySearchService.search(validInput))
            }
        }
      )
    }

  private val maxLimit = BeautySearchSpecV1.spec.carouselSpec.variantSize

  private def validate(input: UserSearchInput): Either[HttpApiFailure.BadRequest, UserSearchInput] =
    if (input.query.trim.isEmpty) {
      Left(HttpApiFailure.BadRequest("invalid_query", "query must not be blank"))
    } else if (input.limit < 1 || input.limit > maxLimit) {
      Left(HttpApiFailure.BadRequest("invalid_limit", s"limit must be between 1 and $maxLimit"))
    } else if (input.userLat.exists(latitude => latitude < BigDecimal(-90) || latitude > BigDecimal(90))) {
      Left(HttpApiFailure.BadRequest("invalid_latitude", "userLat must be between -90 and 90"))
    } else if (input.userLon.exists(longitude => longitude < BigDecimal(-180) || longitude > BigDecimal(180))) {
      Left(HttpApiFailure.BadRequest("invalid_longitude", "userLon must be between -180 and 180"))
    } else {
      Right(input)
    }
}
