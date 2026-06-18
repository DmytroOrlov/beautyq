package leaderboard.http.tapir

import cats.effect.Async
import org.http4s.HttpRoutes
import sttp.capabilities.fs2.Fs2Streams
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.http4s.Http4sServerInterpreter

class TapirHttpSupport[F[+_, +_]](implicit async: Async[F[Throwable, _]]) {
  def toRoutes(
    endpoints: List[ServerEndpoint[Fs2Streams[F[Throwable, _]], F[Throwable, _]]]
  ): HttpRoutes[F[Throwable, _]] =
    Http4sServerInterpreter[F[Throwable, _]]().toRoutes(endpoints)
}
