package leaderboard.api

import cats.effect.Async
import izumi.functional.bio.Error2
import leaderboard.http.HttpApiFailure
import leaderboard.http.tapir.{LegacyJsonResponse, ServiceTapirEndpoints, TapirHttpSupport}
import leaderboard.repo.Services
import org.http4s.HttpRoutes

class ServiceApi[F[+_, +_]: Error2](
  services: Services[F],
  tapirEndpoints: ServiceTapirEndpoints,
  tapirHttpSupport: TapirHttpSupport[F],
)(implicit
  async: Async[F[Throwable, _]]
) extends HttpApi[F] {
  def http: HttpRoutes[F[Throwable, _]] =
    tapirHttpSupport.toRoutes {
      import tapirEndpoints.*
      List(
        getService.serverLogic[F[Throwable, _]](
          serviceId => async.map(HttpApiFailure.fromQueryEffect(services.getService(serviceId)))(_.map(LegacyJsonResponse.optionalAsJson))
        ),
        upsertService.serverLogic[F[Throwable, _]](service => HttpApiFailure.fromQueryEffect(services.upsertService(service))),
        getServicesByCategory.serverLogic[F[Throwable, _]](categoryId => HttpApiFailure.fromQueryEffect(services.getServicesByCategory(categoryId))),
      )
    }
}
