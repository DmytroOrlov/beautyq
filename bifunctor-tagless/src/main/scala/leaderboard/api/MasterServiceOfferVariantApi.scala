package leaderboard.api

import io.circe.syntax.*
import izumi.functional.bio.{Async2, Fork2, Primitives2}
import izumi.functional.bio.catz.*
import leaderboard.model.MasterServiceOfferVariant
import leaderboard.repo.MasterServiceOfferVariants
import org.http4s.HttpRoutes
import org.http4s.circe.*
import org.http4s.dsl.Http4sDsl

final class MasterServiceOfferVariantApi[F[+_, +_]: Async2: Fork2: Primitives2](
  dsl: Http4sDsl[F[Throwable, _]],
  masterServiceOfferVariants: MasterServiceOfferVariants[F],
) extends HttpApi[F] {

  import dsl.*

  def http: HttpRoutes[F[Throwable, _]] = {
    HttpRoutes.of {
      case GET -> Root / "master-service-offer-variant" / UUIDVar(masterServiceOfferVariantId) =>
        Ok(masterServiceOfferVariants.getMasterServiceOfferVariant(masterServiceOfferVariantId).map(_.asJson))

      case rq @ POST -> Root / "master-service-offer-variant" =>
        Ok(for {
          variant <- rq.decodeJson[MasterServiceOfferVariant]
          _       <- masterServiceOfferVariants.upsertMasterServiceOfferVariant(variant)
        } yield ())

      case GET -> Root / "master-service-offer-variant" / "offer" / UUIDVar(masterServiceOfferId) =>
        Ok(masterServiceOfferVariants.getMasterServiceOfferVariantsByOffer(masterServiceOfferId).map(_.asJson))

      case GET -> Root / "master-service-offer-variant" / "location" / UUIDVar(masterLocationId) =>
        Ok(masterServiceOfferVariants.getMasterServiceOfferVariantsByLocation(masterLocationId).map(_.asJson))
    }
  }
}
