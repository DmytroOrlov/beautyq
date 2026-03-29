package leaderboard.api

import io.circe.syntax.*
import izumi.functional.bio.{Async2, Fork2, Primitives2}
import izumi.functional.bio.catz.*
import leaderboard.repo.Services
import org.http4s.HttpRoutes
import org.http4s.circe.*
import org.http4s.dsl.Http4sDsl

// AI-NOTE: For izumi/distage/BIO typeclasses used here, see docs/LOCAL_LLM_IZUMI_DISTAGE_BIO_REFERENCE.md
final class ServiceApi[F[+_, +_]: Async2: Fork2: Primitives2](
  dsl: Http4sDsl[F[Throwable, _]],
  services: Services[F],
) extends HttpApi[F] {

  import dsl.*

  def http: HttpRoutes[F[Throwable, _]] = {
    HttpRoutes.of {
      case GET -> Root / "service" / UUIDVar(serviceId) =>
        Ok(services.getService(serviceId).map(_.asJson))

      case rq @ POST -> Root / "service" =>
        Ok(for {
          service <- rq.decodeJson[leaderboard.model.Service]
          _       <- services.upsertService(service)
        } yield ())

      case GET -> Root / "service" / "category" / UUIDVar(categoryId) =>
        Ok(services.getServicesByCategory(categoryId).map(_.asJson))
    }
  }
}
