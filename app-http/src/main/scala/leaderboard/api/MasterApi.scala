package leaderboard.api

import cats.effect.Async
import izumi.functional.bio.Error2
import leaderboard.http.HttpApiFailure
import leaderboard.http.tapir.MasterTapirEndpoints
import leaderboard.repo.Masters
import org.http4s.HttpRoutes
import sttp.tapir.server.http4s.Http4sServerInterpreter

class MasterApi[F[+_, +_]: Error2](
  masters: Masters[F],
  tapirEndpoints: MasterTapirEndpoints,
)(implicit
  async: Async[F[Throwable, _]]
) extends HttpApi[F] {
  def http: HttpRoutes[F[Throwable, _]] =
    Http4sServerInterpreter[F[Throwable, _]]().toRoutes {
      import tapirEndpoints.*
      List(
        getMaster.serverLogic[F[Throwable, _]](
          masterId => async.map(HttpApiFailure.fromQueryEffect(masters.getMaster(masterId))) {
            _.flatMap(_.toRight(HttpApiFailure.NotFound.master(masterId)))
          }
        ),
        getMasters.serverLogic[F[Throwable, _]](_ => HttpApiFailure.fromQueryEffect(masters.getMasters())),
        upsertMaster.serverLogic[F[Throwable, _]](master => HttpApiFailure.fromQueryEffect(masters.upsertMaster(master))),
      )
    }
}
