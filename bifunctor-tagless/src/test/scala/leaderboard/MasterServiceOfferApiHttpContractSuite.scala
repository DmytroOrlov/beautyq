package leaderboard

import izumi.distage.testkit.scalatest.{AssertZIO, SpecZIO}
import leaderboard.api.MasterServiceOfferApi
import leaderboard.http.tapir.{MasterServiceOfferTapirEndpoints, TapirHttpSupport}
import leaderboard.model.{MasterServiceOffer, MasterServiceOfferId, QueryFailure}
import leaderboard.repo.MasterServiceOffers
import org.http4s.Status
import zio.interop.catz.*
import zio.{IO, Ref, UIO, ZIO}

import java.util.UUID

final class MasterServiceOfferApiHttpContractSuite extends SpecZIO with AssertZIO with HttpContractTestSupport {
  private def masterServiceOfferApi(state: MasterServiceOfferApiContractState): MasterServiceOfferApi[IO] =
    new MasterServiceOfferApi[IO](state.masterServiceOffers, MasterServiceOfferTapirEndpoints, new TapirHttpSupport[IO])

  "MasterServiceOfferApi current http4s contracts" should {
    "return 200 and exact offer json for an existing entity" in {
      val offerId = UUID.fromString("11111111-2222-3333-4444-555555555555")
      val masterId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")
      val serviceId = UUID.fromString("99999999-8888-7777-6666-555555555555")
      val offer = MasterServiceOffer(offerId, masterId, serviceId)

      for {
        state <- MasterServiceOfferApiContractState.make
        _ <- state.setGetMasterServiceOfferResult(Right(Some(offer)))
        response <- observe(combineApis(masterServiceOfferApi(state)), get(s"/master-service-offer/$offerId"))
        _ <- assertIO(response.status === Status.Ok)
        _ <- assertIO(response.body === s"""{"id":"$offerId","masterId":"$masterId","serviceId":"$serviceId"}""")
      } yield ()
    }

    "return 200 and null body for a missing offer" in {
      val offerId = UUID.fromString("66666666-7777-8888-9999-aaaaaaaaaaaa")

      for {
        state <- MasterServiceOfferApiContractState.make
        _ <- state.setGetMasterServiceOfferResult(Right(None))
        response <- observe(combineApis(masterServiceOfferApi(state)), get(s"/master-service-offer/$offerId"))
        _ <- assertIO(response.status === Status.Ok)
        _ <- assertIO(response.body === "null")
      } yield ()
    }

    "return 200 and exact json array for the master endpoint" in {
      val masterId = UUID.fromString("12345678-1234-1234-1234-123456789abc")
      val first = MasterServiceOffer(UUID.fromString("00000000-0000-0000-0000-000000000001"), masterId, UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001"))
      val second = MasterServiceOffer(UUID.fromString("00000000-0000-0000-0000-000000000002"), masterId, UUID.fromString("aaaaaaaa-0000-0000-0000-000000000002"))

      for {
        state <- MasterServiceOfferApiContractState.make
        _ <- state.setOffersByMasterResult(masterId, Right(List(first, second)))
        response <- observe(combineApis(masterServiceOfferApi(state)), get(s"/master-service-offer/master/$masterId"))
        _ <- assertIO(response.status === Status.Ok)
        _ <- assertIO(
          response.body === s"""[{"id":"${first.id}","masterId":"$masterId","serviceId":"${first.serviceId}"},{"id":"${second.id}","masterId":"$masterId","serviceId":"${second.serviceId}"}]"""
        )
      } yield ()
    }

    "return 200 and exact json array for the service endpoint" in {
      val serviceId = UUID.fromString("12345678-0000-0000-0000-123456789abc")
      val first = MasterServiceOffer(UUID.fromString("00000000-0000-0000-0000-000000000003"), UUID.fromString("bbbbbbbb-0000-0000-0000-000000000001"), serviceId)
      val second = MasterServiceOffer(UUID.fromString("00000000-0000-0000-0000-000000000004"), UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002"), serviceId)

      for {
        state <- MasterServiceOfferApiContractState.make
        _ <- state.setOffersByServiceResult(serviceId, Right(List(first, second)))
        response <- observe(combineApis(masterServiceOfferApi(state)), get(s"/master-service-offer/service/$serviceId"))
        _ <- assertIO(response.status === Status.Ok)
        _ <- assertIO(
          response.body === s"""[{"id":"${first.id}","masterId":"${first.masterId}","serviceId":"$serviceId"},{"id":"${second.id}","masterId":"${second.masterId}","serviceId":"$serviceId"}]"""
        )
      } yield ()
    }

    "return 200 with empty body and capture the posted offer payload" in {
      val offerId = UUID.fromString("bbbbbbbb-cccc-dddd-eeee-ffffffffffff")
      val masterId = UUID.fromString("01010101-0202-0303-0404-050505050505")
      val serviceId = UUID.fromString("06060606-0707-0808-0909-101010101010")
      val payload = s"""{"id":"$offerId","masterId":"$masterId","serviceId":"$serviceId"}"""

      for {
        state <- MasterServiceOfferApiContractState.make
        _ <- state.setUpsertMasterServiceOfferResult(Right(()))
        response <- observe(combineApis(masterServiceOfferApi(state)), postJson("/master-service-offer", payload))
        upserts <- state.upserts
        _ <- assertIO(response.status === Status.Ok)
        _ <- assertIO(response.body === "")
        _ <- assertIO(upserts === Vector(MasterServiceOffer(offerId, masterId, serviceId)))
      } yield ()
    }

    "return current 404 semantics for malformed UUID path params" in {
      for {
        state <- MasterServiceOfferApiContractState.make
        response <- observe(combineApis(masterServiceOfferApi(state)), get("/master-service-offer/not-a-uuid"))
        _ <- assertIO(response.status === Status.NotFound)
        _ <- assertIO(response.body === "Not found")
      } yield ()
    }

    "return current malformed-json semantics and do not hit the repo on malformed JSON body" in {
      for {
        state <- MasterServiceOfferApiContractState.make
        response <- observe(combineApis(masterServiceOfferApi(state)), postJson("/master-service-offer", """{"id":"abc""""))
        upserts <- state.upserts
        _ <- assertIO(response.status === Status.InternalServerError)
        _ <- assertIO(response.body === "")
        _ <- assertIO(upserts.isEmpty)
      } yield ()
    }

    "return current server failure semantics when the service endpoint fails" in {
      val serviceId = UUID.fromString("99999999-0000-0000-0000-000000000000")

      for {
        state <- MasterServiceOfferApiContractState.make
        _ <- state.setOffersByServiceResult(serviceId, Left(QueryFailure("get-master-service-offers-by-service", new RuntimeException("offers-boom"))))
        response <- observe(combineApis(masterServiceOfferApi(state)), get(s"/master-service-offer/service/$serviceId"))
        _ <- assertIO(response.status === Status.InternalServerError)
        _ <- assertIO(response.body === "")
      } yield ()
    }
  }
}

final class MasterServiceOfferApiContractState private (
  private val upsertsRef: Ref[Vector[MasterServiceOffer]],
  private val getMasterServiceOfferResultRef: Ref[Either[QueryFailure, Option[MasterServiceOffer]]],
  private val offersByMasterResultsRef: Ref[Map[leaderboard.model.MasterId, Either[QueryFailure, List[MasterServiceOffer]]]],
  private val offersByServiceResultsRef: Ref[Map[leaderboard.model.ServiceId, Either[QueryFailure, List[MasterServiceOffer]]]],
  private val upsertMasterServiceOfferResultRef: Ref[Either[QueryFailure, Unit]],
) {
  val masterServiceOffers: MasterServiceOffers[IO] = new MasterServiceOffers[IO] {
    def upsertMasterServiceOffer(offer: MasterServiceOffer): IO[QueryFailure, Unit] =
      upsertMasterServiceOfferResultRef.get.flatMap {
        case Right(_) =>
          upsertsRef.update(_ :+ offer)
        case Left(error) =>
          ZIO.fail(error)
      }

    def getMasterServiceOffer(id: MasterServiceOfferId): IO[QueryFailure, Option[MasterServiceOffer]] =
      getMasterServiceOfferResultRef.get.flatMap(ZIO.fromEither(_))

    def getMasterServiceOffersByMaster(masterId: leaderboard.model.MasterId): IO[QueryFailure, List[MasterServiceOffer]] =
      offersByMasterResultsRef.get.flatMap { current =>
        ZIO.fromEither(current.getOrElse(masterId, Right(Nil)))
      }

    def getMasterServiceOffersByService(serviceId: leaderboard.model.ServiceId): IO[QueryFailure, List[MasterServiceOffer]] =
      offersByServiceResultsRef.get.flatMap { current =>
        ZIO.fromEither(current.getOrElse(serviceId, Right(Nil)))
      }
  }

  def upserts: UIO[Vector[MasterServiceOffer]] =
    upsertsRef.get

  def setGetMasterServiceOfferResult(result: Either[QueryFailure, Option[MasterServiceOffer]]): UIO[Unit] =
    getMasterServiceOfferResultRef.set(result)

  def setOffersByMasterResult(
    masterId: leaderboard.model.MasterId,
    result: Either[QueryFailure, List[MasterServiceOffer]],
  ): UIO[Unit] =
    offersByMasterResultsRef.update(_ + (masterId -> result))

  def setOffersByServiceResult(
    serviceId: leaderboard.model.ServiceId,
    result: Either[QueryFailure, List[MasterServiceOffer]],
  ): UIO[Unit] =
    offersByServiceResultsRef.update(_ + (serviceId -> result))

  def setUpsertMasterServiceOfferResult(result: Either[QueryFailure, Unit]): UIO[Unit] =
    upsertMasterServiceOfferResultRef.set(result)
}

object MasterServiceOfferApiContractState {
  def make: UIO[MasterServiceOfferApiContractState] =
    for {
      upserts <- Ref.make(Vector.empty[MasterServiceOffer])
      getMasterServiceOfferResult <- Ref.make[Either[QueryFailure, Option[MasterServiceOffer]]](Right(None))
      offersByMasterResults <- Ref.make(Map.empty[leaderboard.model.MasterId, Either[QueryFailure, List[MasterServiceOffer]]])
      offersByServiceResults <- Ref.make(Map.empty[leaderboard.model.ServiceId, Either[QueryFailure, List[MasterServiceOffer]]])
      upsertMasterServiceOfferResult <- Ref.make[Either[QueryFailure, Unit]](Right(()))
    } yield new MasterServiceOfferApiContractState(
      upserts,
      getMasterServiceOfferResult,
      offersByMasterResults,
      offersByServiceResults,
      upsertMasterServiceOfferResult,
    )
}
