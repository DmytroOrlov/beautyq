package leaderboard.api

import cats.effect.Async
import io.circe.Json
import io.circe.syntax.*
import leaderboard.http.tapir.{ServiceTapirEndpoints, TapirHttpSupport}
import leaderboard.repo.Services
import org.http4s.HttpRoutes

final class ServiceApi[F[+_, +_]](
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
        getService.serverLogicSuccess[F[Throwable, _]](serviceId => async.map(services.getService(serviceId))(_.fold[Json](Json.Null)(_.asJson))),
        upsertService.serverLogicSuccess[F[Throwable, _]](services.upsertService),
        getServicesByCategory.serverLogicSuccess[F[Throwable, _]](categoryId => services.getServicesByCategory(categoryId)),
      )
    }
}
