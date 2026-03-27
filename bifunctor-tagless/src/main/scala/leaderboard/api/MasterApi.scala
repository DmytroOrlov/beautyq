package leaderboard.api

import io.circe.syntax.*
import izumi.functional.bio.{Async2, Fork2, Primitives2}
import izumi.functional.bio.catz.*
import leaderboard.model.Master
import leaderboard.repo.Masters
import org.http4s.HttpRoutes
import org.http4s.circe.*
import org.http4s.dsl.Http4sDsl

final class MasterApi[F[+_, +_]: Async2: Fork2: Primitives2](
  dsl: Http4sDsl[F[Throwable, _]],
  masters: Masters[F],
) extends HttpApi[F] {

  import dsl.*

  override def http: HttpRoutes[F[Throwable, _]] = {
    HttpRoutes.of {
      case GET -> Root / "master" / UUIDVar(masterId) =>
        Ok(masters.getMaster(masterId).map(_.asJson))

      case rq @ POST -> Root / "master" =>
        Ok(for {
          master <- rq.decodeJson[Master]
          _      <- masters.upsertMaster(master)
        } yield ())

      case GET -> Root / "master" =>
        Ok(masters.getMasters().map(_.asJson))
    }
  }
}
