package leaderboard

import izumi.distage.testkit.scalatest.{AssertZIO, SpecZIO}
import leaderboard.api.MasterServiceOfferApi
import leaderboard.http.tapir.{MasterServiceOfferTapirEndpoints, TapirHttpSupport}
import leaderboard.model.{MasterServiceOffer, QueryFailure}
import leaderboard.repo.MasterServiceOffers
import org.http4s.Status
import zio.interop.catz.*
import zio.{IO, Ref, UIO, ZIO}

import java.util.UUID

class LegacySingleEntityGetHttpContractSuite extends SpecZIO with AssertZIO with HttpContractTestSupport {
  private def masterServiceOfferApi(state: LegacySingleEntityGetContractState): MasterServiceOfferApi[IO] =
    new MasterServiceOfferApi[IO](state.masterServiceOffers, MasterServiceOfferTapirEndpoints, new TapirHttpSupport[IO])

  "Legacy single entity GET contracts" should {
    "pin 200 and null for a missing legacy master service offer" in {
      val offerId = UUID.fromString("66666666-7777-8888-9999-aaaaaaaaaaaa")

      for {
        state    <- LegacySingleEntityGetContractState.make
        _        <- state.setGetMasterServiceOfferResult(Right(None))
        response <- observe(combineApis(masterServiceOfferApi(state)), get(s"/master-service-offer/$offerId"))
        _        <- assertIO(response.status === Status.Ok)
        _        <- assertIO(response.body === "null")
      } yield ()
    }

    "pin 200 and exact json for an existing legacy master service offer" in {
      val offerId   = UUID.fromString("11111111-2222-3333-4444-555555555555")
      val masterId  = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")
      val serviceId = UUID.fromString("99999999-8888-7777-6666-555555555555")
      val offer     = MasterServiceOffer(offerId, masterId, serviceId)

      for {
        state    <- LegacySingleEntityGetContractState.make
        _        <- state.setGetMasterServiceOfferResult(Right(Some(offer)))
        response <- observe(combineApis(masterServiceOfferApi(state)), get(s"/master-service-offer/$offerId"))
        _        <- assertIO(response.status === Status.Ok)
        _        <- assertIO(response.body === s"""{"id":"$offerId","masterId":"$masterId","serviceId":"$serviceId"}""")
      } yield ()
    }
  }
}

class LegacySingleEntityGetContractState private (
  private val getMasterServiceOfferResultRef: Ref[Either[QueryFailure, Option[MasterServiceOffer]]],
) {
  val masterServiceOffers: MasterServiceOffers[IO] = new MasterServiceOffers[IO] {
    def upsertMasterServiceOffer(offer: MasterServiceOffer): IO[QueryFailure, Unit] =
      ZIO.unit

    def getMasterServiceOffer(id: leaderboard.model.MasterServiceOfferId): IO[QueryFailure, Option[MasterServiceOffer]] =
      getMasterServiceOfferResultRef.get.flatMap(ZIO.fromEither(_))

    def getMasterServiceOffersByMaster(masterId: leaderboard.model.MasterId): IO[QueryFailure, List[MasterServiceOffer]] =
      ZIO.succeed(Nil)

    def getMasterServiceOffersByService(serviceId: leaderboard.model.ServiceId): IO[QueryFailure, List[MasterServiceOffer]] =
      ZIO.succeed(Nil)
  }

  def setGetMasterServiceOfferResult(result: Either[QueryFailure, Option[MasterServiceOffer]]): UIO[Unit] =
    getMasterServiceOfferResultRef.set(result)
}

object LegacySingleEntityGetContractState {
  def make: UIO[LegacySingleEntityGetContractState] =
    for {
      getMasterServiceOfferResult <- Ref.make[Either[QueryFailure, Option[MasterServiceOffer]]](Right(None))
    } yield new LegacySingleEntityGetContractState(getMasterServiceOfferResult)
}
