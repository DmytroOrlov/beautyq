package leaderboard.api

import izumi.functional.bio.Error2
import leaderboard.http.HttpApiFailure
import leaderboard.http.tapir.{BeautySearchTapirEndpoints, TapirHttpSupport}
import leaderboard.search.BeautySearchService
import org.http4s.HttpRoutes

class BeautySearchApi[F[+_, +_]: Error2](
  beautySearchService: BeautySearchService[F],
  tapirEndpoints: BeautySearchTapirEndpoints,
  tapirHttpSupport: TapirHttpSupport[F],
) extends HttpApi[F] {
  def http: HttpRoutes[F[Throwable, _]] =
    tapirHttpSupport.toRoutes {
      import tapirEndpoints.*
      List(
        searchBeauty.serverLogic[F[Throwable, _]](input => HttpApiFailure.fromQueryEffect(beautySearchService.search(input)))
      )
    }
}
