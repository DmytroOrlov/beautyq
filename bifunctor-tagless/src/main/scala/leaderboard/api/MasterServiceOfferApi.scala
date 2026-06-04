package leaderboard.api

import cats.effect.Async
import izumi.functional.bio.Error2
import leaderboard.http.HttpApiFailure
import leaderboard.http.tapir.{MasterServiceOfferTapirEndpoints, TapirHttpSupport}
import leaderboard.repo.MasterServiceOffers
import org.http4s.HttpRoutes

class MasterServiceOfferApi[F[+_, +_]: Error2](
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
        getMasterServiceOffer.serverLogic[F[Throwable, _]](
          masterServiceOfferId =>
            async.map(HttpApiFailure.fromQueryEffect(masterServiceOffers.getMasterServiceOffer(masterServiceOfferId))) {
              _.flatMap(_.toRight(HttpApiFailure.NotFound.masterServiceOffer(masterServiceOfferId)))
            }
        ),
        upsertMasterServiceOffer.serverLogic[F[Throwable, _]](offer => HttpApiFailure.fromQueryEffect(masterServiceOffers.upsertMasterServiceOffer(offer))),
        getMasterServiceOffersByMaster.serverLogic[F[Throwable, _]](
          masterId => HttpApiFailure.fromQueryEffect(masterServiceOffers.getMasterServiceOffersByMaster(masterId))
        ),
        getMasterServiceOffersByService.serverLogic[F[Throwable, _]](
          serviceId => HttpApiFailure.fromQueryEffect(masterServiceOffers.getMasterServiceOffersByService(serviceId))
        ),
      )
    }
}
