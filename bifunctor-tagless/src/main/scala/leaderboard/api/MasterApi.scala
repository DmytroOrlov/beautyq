package leaderboard.api

import cats.effect.Async
import io.circe.Json
import io.circe.syntax.*
import leaderboard.http.tapir.{MasterTapirEndpoints, TapirHttpSupport}
import leaderboard.repo.Masters
import org.http4s.HttpRoutes

class MasterApi[F[+_, +_]](
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
        getMaster.serverLogicSuccess[F[Throwable, _]](masterId => async.map(masters.getMaster(masterId))(_.fold[Json](Json.Null)(_.asJson))),
        getMasters.serverLogicSuccess[F[Throwable, _]](_ => masters.getMasters()),
        upsertMaster.serverLogicSuccess[F[Throwable, _]](masters.upsertMaster),
      )
    }
}
