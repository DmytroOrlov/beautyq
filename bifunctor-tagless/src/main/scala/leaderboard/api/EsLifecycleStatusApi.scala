package leaderboard.api

import cats.effect.Async
import leaderboard.http.tapir.EsLifecycleStatusTapirEndpoints
import leaderboard.search.elasticsearch.{ElasticsearchStartupReadinessStatusResponse, ElasticsearchStartupReadinessTransition}
import org.http4s.HttpRoutes
import sttp.tapir.server.http4s.Http4sServerInterpreter

class EsLifecycleStatusApi[F[+_, +_]](
  transition: ElasticsearchStartupReadinessTransition,
  tapirEndpoints: EsLifecycleStatusTapirEndpoints,
)(implicit
  async: Async[F[Throwable, _]]
) extends HttpApi[F] {
  def http: HttpRoutes[F[Throwable, _]] =
    Http4sServerInterpreter[F[Throwable, _]]().toRoutes {
      import tapirEndpoints.*
      List(
        lifecycleStatus.serverLogicSuccess[F[Throwable, _]] {
          _ =>
            async.pure(ElasticsearchStartupReadinessStatusResponse.from(transition))
        }
      )
    }
}
