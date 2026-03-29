package leaderboard.api

import cats.effect.Async
import io.circe.Json
import io.circe.syntax.*
import leaderboard.http.tapir.{MasterServiceOfferVariantTapirEndpoints, TapirHttpSupport}
import leaderboard.model.MasterServiceOfferVariant
import leaderboard.repo.MasterServiceOfferVariants
import org.http4s.HttpRoutes

class MasterServiceOfferVariantApi[F[+_, +_]](
  masterServiceOfferVariants: MasterServiceOfferVariants[F],
  tapirEndpoints: MasterServiceOfferVariantTapirEndpoints,
  tapirHttpSupport: TapirHttpSupport[F],
)(implicit
  async: Async[F[Throwable, _]]
) extends HttpApi[F] {
  def http: HttpRoutes[F[Throwable, _]] =
    tapirHttpSupport.toRoutes {
      import tapirEndpoints.*
      List(
        getMasterServiceOfferVariant.serverLogicSuccess[F[Throwable, _]](
          masterServiceOfferVariantId =>
            async.map(masterServiceOfferVariants.getMasterServiceOfferVariant(masterServiceOfferVariantId))(_.fold[Json](Json.Null)(_.asJson))
        ),
        upsertMasterServiceOfferVariant.serverLogicSuccess[F[Throwable, _]] { json =>
          async.flatMap(async.fromEither(json.as[MasterServiceOfferVariant]))(masterServiceOfferVariants.upsertMasterServiceOfferVariant)
        },
        getMasterServiceOfferVariantsByOffer.serverLogicSuccess[F[Throwable, _]](
          masterServiceOfferId => async.map(masterServiceOfferVariants.getMasterServiceOfferVariantsByOffer(masterServiceOfferId))(_.asJson)
        ),
        getMasterServiceOfferVariantsByLocation.serverLogicSuccess[F[Throwable, _]](
          masterLocationId => async.map(masterServiceOfferVariants.getMasterServiceOfferVariantsByLocation(masterLocationId))(_.asJson)
        ),
      )
    }
}
