package leaderboard.api

import cats.effect.Async
import io.circe.Json
import io.circe.syntax.*
import izumi.functional.bio.Error2
import leaderboard.http.HttpApiFailure
import leaderboard.http.tapir.{MasterLocationTapirEndpoints, TapirHttpSupport}
import leaderboard.repo.MasterLocations
import org.http4s.HttpRoutes

class MasterLocationApi[F[+_, +_]: Error2](
  masterLocations: MasterLocations[F],
  tapirEndpoints: MasterLocationTapirEndpoints,
  tapirHttpSupport: TapirHttpSupport[F],
)(implicit
  async: Async[F[Throwable, _]]
) extends HttpApi[F] {
  def http: HttpRoutes[F[Throwable, _]] =
    tapirHttpSupport.toRoutes {
      import tapirEndpoints.*
      List(
        getMasterLocation.serverLogic[F[Throwable, _]](
          masterLocationId =>
            async.map(HttpApiFailure.fromQueryEffect(masterLocations.getMasterLocation(masterLocationId)))(_.map(_.fold[Json](Json.Null)(_.asJson)))
        ),
        upsertMasterLocation.serverLogic[F[Throwable, _]](
          location => HttpApiFailure.fromQueryEffect(masterLocations.upsertMasterLocation(location))
        ),
        getMasterLocationsByMaster.serverLogic[F[Throwable, _]](
          masterId => HttpApiFailure.fromQueryEffect(masterLocations.getMasterLocationsByMaster(masterId))
        ),
      )
    }
}
