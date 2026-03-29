package leaderboard.api

import cats.effect.Async
import io.circe.Json
import io.circe.syntax.*
import leaderboard.http.tapir.{MasterServiceOfferTapirEndpoints, TapirHttpSupport}
import leaderboard.repo.MasterServiceOffers
import org.http4s.HttpRoutes

class MasterServiceOfferApi[F[+_, +_]](
  masterServiceOffers: MasterServiceOffers[F],
  tapirEndpoints: MasterServiceOfferTapirEndpoints,
  tapirHttpSupport: TapirHttpSupport[F],
)(implicit
  async: Async[F[Throwable, _]]
) extends HttpApi[F] {
  def http: HttpRoutes[F[Throwable, _]] =
    tapirHttpSupport.toRoutes {
      import tapirEndpoints.*
      List(
        getMasterServiceOffer.serverLogicSuccess[F[Throwable, _]](
          masterServiceOfferId => async.map(masterServiceOffers.getMasterServiceOffer(masterServiceOfferId))(_.fold[Json](Json.Null)(_.asJson))
        ),
        upsertMasterServiceOffer.serverLogicSuccess[F[Throwable, _]](masterServiceOffers.upsertMasterServiceOffer),
        getMasterServiceOffersByMaster.serverLogicSuccess[F[Throwable, _]](masterId => masterServiceOffers.getMasterServiceOffersByMaster(masterId)),
        getMasterServiceOffersByService.serverLogicSuccess[F[Throwable, _]](serviceId => masterServiceOffers.getMasterServiceOffersByService(serviceId)),
      )
    }
}
