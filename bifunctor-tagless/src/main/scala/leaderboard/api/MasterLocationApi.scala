package leaderboard.api

import cats.effect.Async
import io.circe.Json
import io.circe.syntax.*
import leaderboard.http.tapir.{MasterLocationTapirEndpoints, TapirHttpSupport}
import leaderboard.repo.MasterLocations
import org.http4s.HttpRoutes

final class MasterLocationApi[F[+_, +_]](
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
        getMasterLocation.serverLogicSuccess[F[Throwable, _]](
          masterLocationId => async.map(masterLocations.getMasterLocation(masterLocationId))(_.fold[Json](Json.Null)(_.asJson))
        ),
        upsertMasterLocation.serverLogicSuccess[F[Throwable, _]](masterLocations.upsertMasterLocation),
        getMasterLocationsByMaster.serverLogicSuccess[F[Throwable, _]](masterId => masterLocations.getMasterLocationsByMaster(masterId)),
      )
    }
}
