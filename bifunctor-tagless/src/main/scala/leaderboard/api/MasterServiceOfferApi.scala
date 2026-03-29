package leaderboard.api

import io.circe.syntax.*
import izumi.functional.bio.{Async2, Fork2, Primitives2}
import izumi.functional.bio.catz.*
import leaderboard.model.MasterServiceOffer
import leaderboard.repo.MasterServiceOffers
import org.http4s.HttpRoutes
import org.http4s.circe.*
import org.http4s.dsl.Http4sDsl

final class MasterServiceOfferApi[F[+_, +_]: Async2: Fork2: Primitives2](
  dsl: Http4sDsl[F[Throwable, _]],
  masterServiceOffers: MasterServiceOffers[F],
) extends HttpApi[F] {

  import dsl.*

  def http: HttpRoutes[F[Throwable, _]] = {
    HttpRoutes.of {
      case GET -> Root / "master-service-offer" / UUIDVar(masterServiceOfferId) =>
        Ok(masterServiceOffers.getMasterServiceOffer(masterServiceOfferId).map(_.asJson))

      case rq @ POST -> Root / "master-service-offer" =>
        Ok(for {
          offer <- rq.decodeJson[MasterServiceOffer]
          _     <- masterServiceOffers.upsertMasterServiceOffer(offer)
        } yield ())

      case GET -> Root / "master-service-offer" / "master" / UUIDVar(masterId) =>
        Ok(masterServiceOffers.getMasterServiceOffersByMaster(masterId).map(_.asJson))

      case GET -> Root / "master-service-offer" / "service" / UUIDVar(serviceId) =>
        Ok(masterServiceOffers.getMasterServiceOffersByService(serviceId).map(_.asJson))
    }
  }
}
