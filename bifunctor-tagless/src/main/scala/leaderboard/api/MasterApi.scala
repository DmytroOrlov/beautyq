package leaderboard.api

import cats.effect.Async
import izumi.functional.bio.Error2
import leaderboard.http.HttpApiFailure
import leaderboard.http.tapir.{LegacyJsonResponse, MasterTapirEndpoints, TapirHttpSupport}
import leaderboard.repo.Masters
import org.http4s.HttpRoutes

class MasterApi[F[+_, +_]: Error2](
  masters: Masters[F],
  tapirEndpoints: MasterTapirEndpoints,
  tapirHttpSupport: TapirHttpSupport[F],
)(implicit
  async: Async[F[Throwable, _]]
) extends HttpApi[F] {
  def http: HttpRoutes[F[Throwable, _]] =
    tapirHttpSupport.toRoutes {
      import tapirEndpoints.*
      List(
        getMaster.serverLogic[F[Throwable, _]](
          masterId => async.map(HttpApiFailure.fromQueryEffect(masters.getMaster(masterId)))(_.map(LegacyJsonResponse.optionalAsJson))
        ),
        getMasters.serverLogic[F[Throwable, _]](_ => HttpApiFailure.fromQueryEffect(masters.getMasters())),
        upsertMaster.serverLogic[F[Throwable, _]](master => HttpApiFailure.fromQueryEffect(masters.upsertMaster(master))),
      )
    }
}
