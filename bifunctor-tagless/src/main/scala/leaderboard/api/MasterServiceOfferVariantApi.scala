package leaderboard.api

import cats.effect.Async
import io.circe.syntax.*
import izumi.functional.bio.Error2
import leaderboard.http.HttpApiFailure
import leaderboard.http.tapir.MasterServiceOfferVariantTapirEndpoints
import leaderboard.model.MasterServiceOfferVariant
import leaderboard.repo.MasterServiceOfferVariants
import org.http4s.HttpRoutes
import sttp.tapir.server.http4s.Http4sServerInterpreter

class MasterServiceOfferVariantApi[F[+_, +_]: Error2](
  masterServiceOfferVariants: MasterServiceOfferVariants[F],
  tapirEndpoints: MasterServiceOfferVariantTapirEndpoints,
)(implicit
  async: Async[F[Throwable, _]]
) extends HttpApi[F] {
  def http: HttpRoutes[F[Throwable, _]] =
    Http4sServerInterpreter[F[Throwable, _]]().toRoutes {
      import tapirEndpoints.*
      List(
        getMasterServiceOfferVariant.serverLogic[F[Throwable, _]](
          masterServiceOfferVariantId =>
            async.map(HttpApiFailure.fromQueryEffect(masterServiceOfferVariants.getMasterServiceOfferVariant(masterServiceOfferVariantId)))(
              _.flatMap(_.toRight(HttpApiFailure.NotFound.masterServiceOfferVariant(masterServiceOfferVariantId)))
            )
        ),
        upsertMasterServiceOfferVariant.serverLogic[F[Throwable, _]] {
          json =>
            HttpApiFailure.fromEither(json.as[MasterServiceOfferVariant]) match {
              case Left(error) =>
                async.pure(Left(error))
              case Right(variant) =>
                HttpApiFailure.fromQueryEffect(masterServiceOfferVariants.upsertMasterServiceOfferVariant(variant))
            }
        },
        getMasterServiceOfferVariantsByOffer.serverLogic[F[Throwable, _]](
          masterServiceOfferId =>
            async.map(HttpApiFailure.fromQueryEffect(masterServiceOfferVariants.getMasterServiceOfferVariantsByOffer(masterServiceOfferId)))(_.map(_.asJson))
        ),
        getMasterServiceOfferVariantsByLocation.serverLogic[F[Throwable, _]](
          masterLocationId =>
            async.map(HttpApiFailure.fromQueryEffect(masterServiceOfferVariants.getMasterServiceOfferVariantsByLocation(masterLocationId)))(_.map(_.asJson))
        ),
      )
    }
}
