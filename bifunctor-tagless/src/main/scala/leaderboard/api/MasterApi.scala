package leaderboard.api

import cats.effect.Async
import io.circe.Json
import io.circe.syntax.*
import leaderboard.http.tapir.MasterTapirEndpoints.{getMaster, getMasters, upsertMaster}
import leaderboard.http.tapir.TapirHttpSupport
import leaderboard.repo.Masters
import org.http4s.HttpRoutes
import sttp.capabilities.fs2.Fs2Streams
import sttp.tapir.server.ServerEndpoint

final class MasterApi[F[+_, +_]](
  masters: Masters[F],
  tapirHttpSupport: TapirHttpSupport[F],
)(implicit
  async: Async[F[Throwable, _]],
) extends HttpApi[F] {
  def http: HttpRoutes[F[Throwable, _]] =
    tapirHttpSupport.toRoutes(all)

  private def all: List[ServerEndpoint[Fs2Streams[F[Throwable, _]], F[Throwable, _]]] =
    List(
      getMasters.serverLogicSuccess[F[Throwable, _]](_ => masters.getMasters()),
      upsertMaster.serverLogicSuccess[F[Throwable, _]](masters.upsertMaster),
      getMaster.serverLogicSuccess[F[Throwable, _]](masterId =>
        async.map(masters.getMaster(masterId))(_.fold[Json](Json.Null)(_.asJson))
      ),
    )
}
