package leaderboard.api

import io.circe.syntax.*
import izumi.functional.bio.{Async2, Fork2, Primitives2}
import izumi.functional.bio.catz.*
import leaderboard.model.MasterServiceOfferLocation
import leaderboard.repo.MasterServiceOfferLocations
import org.http4s.HttpRoutes
import org.http4s.circe.*
import org.http4s.dsl.Http4sDsl

final class MasterServiceOfferLocationApi[F[+_, +_]: Async2: Fork2: Primitives2](
  dsl: Http4sDsl[F[Throwable, _]],
  masterServiceOfferLocations: MasterServiceOfferLocations[F],
) extends HttpApi[F] {

  import dsl.*

  override def http: HttpRoutes[F[Throwable, _]] = {
    HttpRoutes.of {
      case GET -> Root / "master-service-offer-location" / UUIDVar(masterServiceOfferLocationId) =>
        Ok(masterServiceOfferLocations.getMasterServiceOfferLocation(masterServiceOfferLocationId).map(_.asJson))

      case rq @ POST -> Root / "master-service-offer-location" =>
        Ok(for {
          link <- rq.decodeJson[MasterServiceOfferLocation]
          _    <- masterServiceOfferLocations.upsertMasterServiceOfferLocation(link)
        } yield ())

      case GET -> Root / "master-service-offer-location" / "offer" / UUIDVar(masterServiceOfferId) =>
        Ok(masterServiceOfferLocations.getMasterServiceOfferLocationsByOffer(masterServiceOfferId).map(_.asJson))

      case GET -> Root / "master-service-offer-location" / "location" / UUIDVar(masterLocationId) =>
        Ok(masterServiceOfferLocations.getMasterServiceOfferLocationsByLocation(masterLocationId).map(_.asJson))
    }
  }
}
