package leaderboard.api

import cats.effect.Async
import izumi.functional.bio.Error2
import leaderboard.http.HttpApiFailure
import leaderboard.http.tapir.ServiceTapirEndpoints
import leaderboard.repo.Services
import org.http4s.HttpRoutes
import sttp.tapir.server.http4s.Http4sServerInterpreter

class ServiceApi[F[+_, +_]: Error2](
  services: Services[F],
  tapirEndpoints: ServiceTapirEndpoints,
)(implicit
  async: Async[F[Throwable, _]]
) extends HttpApi[F] {
  def http: HttpRoutes[F[Throwable, _]] =
    Http4sServerInterpreter[F[Throwable, _]]().toRoutes {
      import tapirEndpoints.*
      List(
        getService.serverLogic[F[Throwable, _]](serviceId =>
          async.map(HttpApiFailure.fromQueryEffect(services.getService(serviceId))) {
            _.flatMap(_.toRight(HttpApiFailure.NotFound.service(serviceId)))
          }
        ),
        upsertService.serverLogic[F[Throwable, _]](service => HttpApiFailure.fromQueryEffect(services.upsertService(service))),
        getServicesByCategory.serverLogic[F[Throwable, _]](categoryId => HttpApiFailure.fromQueryEffect(services.getServicesByCategory(categoryId))),
      )
    }
}
