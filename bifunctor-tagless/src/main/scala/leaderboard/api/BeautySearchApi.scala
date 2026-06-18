package leaderboard.api

import cats.effect.Async
import izumi.functional.bio.Error2
import leaderboard.http.HttpApiFailure
import leaderboard.http.tapir.BeautySearchTapirEndpoints
import leaderboard.search.BeautySearchService
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
        searchBeauty.serverLogic[F[Throwable, _]](input => HttpApiFailure.fromQueryEffect(beautySearchService.search(input)))
      )
    }
}
