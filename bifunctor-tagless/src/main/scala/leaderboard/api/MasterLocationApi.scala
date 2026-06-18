package leaderboard.api

import cats.effect.Async
import izumi.functional.bio.Error2
import leaderboard.http.HttpApiFailure
import leaderboard.http.tapir.MasterLocationTapirEndpoints
import leaderboard.repo.MasterLocations
import org.http4s.HttpRoutes
import sttp.tapir.server.http4s.Http4sServerInterpreter

class MasterLocationApi[F[+_, +_]: Error2](
  masterLocations: MasterLocations[F],
  tapirEndpoints: MasterLocationTapirEndpoints,
)(implicit
  async: Async[F[Throwable, _]]
) extends HttpApi[F] {
  def http: HttpRoutes[F[Throwable, _]] =
    Http4sServerInterpreter[F[Throwable, _]]().toRoutes {
      import tapirEndpoints.*
      List(
        getMasterLocation.serverLogic[F[Throwable, _]](
          masterLocationId =>
            async.map(HttpApiFailure.fromQueryEffect(masterLocations.getMasterLocation(masterLocationId))) {
              _.flatMap(_.toRight(HttpApiFailure.NotFound.masterLocation(masterLocationId)))
            }
        ),
        upsertMasterLocation.serverLogic[F[Throwable, _]](location => HttpApiFailure.fromQueryEffect(masterLocations.upsertMasterLocation(location))),
        getMasterLocationsByMaster.serverLogic[F[Throwable, _]](masterId => HttpApiFailure.fromQueryEffect(masterLocations.getMasterLocationsByMaster(masterId))),
      )
    }
}
