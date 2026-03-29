package leaderboard.api

import io.circe.syntax.*
import izumi.functional.bio.{Async2, Fork2, Primitives2}
import izumi.functional.bio.catz.*
import leaderboard.model.MasterLocation
import leaderboard.repo.MasterLocations
import org.http4s.HttpRoutes
import org.http4s.circe.*
import org.http4s.dsl.Http4sDsl

final class MasterLocationApi[F[+_, +_]: Async2: Fork2: Primitives2](
  dsl: Http4sDsl[F[Throwable, _]],
  masterLocations: MasterLocations[F],
) extends HttpApi[F] {

  import dsl.*

  def http: HttpRoutes[F[Throwable, _]] = {
    HttpRoutes.of {
      case GET -> Root / "master-location" / UUIDVar(masterLocationId) =>
        Ok(masterLocations.getMasterLocation(masterLocationId).map(_.asJson))

      case rq @ POST -> Root / "master-location" =>
        Ok(for {
          location <- rq.decodeJson[MasterLocation]
          _        <- masterLocations.upsertMasterLocation(location)
        } yield ())

      case GET -> Root / "master-location" / "master" / UUIDVar(masterId) =>
        Ok(masterLocations.getMasterLocationsByMaster(masterId).map(_.asJson))
    }
  }
}
