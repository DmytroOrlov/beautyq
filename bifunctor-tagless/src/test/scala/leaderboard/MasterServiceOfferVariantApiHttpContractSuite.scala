package leaderboard

import izumi.distage.testkit.scalatest.{AssertZIO, SpecZIO}
import leaderboard.api.MasterServiceOfferVariantApi
import leaderboard.http.tapir.{MasterServiceOfferVariantTapirEndpoints, TapirHttpSupport}
import leaderboard.model.{MasterServiceOfferVariant, MasterServiceOfferVariantId, QueryFailure}
import leaderboard.repo.MasterServiceOfferVariants
import org.http4s.Status
import zio.interop.catz.*
import zio.{IO, Ref, UIO, ZIO}

import java.util.UUID

class MasterServiceOfferVariantApiHttpContractSuite extends SpecZIO with AssertZIO with HttpContractTestSupport {
  private def masterServiceOfferVariantApi(
    state: MasterServiceOfferVariantApiContractState
  ): MasterServiceOfferVariantApi[IO] =
    new MasterServiceOfferVariantApi[IO](
      state.masterServiceOfferVariants,
      MasterServiceOfferVariantTapirEndpoints,
      new TapirHttpSupport[IO],
    )

  "MasterServiceOfferVariantApi current http4s contracts" should {
    "return 200 and exact variant json for an existing entity" in {
      val variant = variantOf(
        UUID.fromString("11111111-2222-3333-4444-555555555555"),
        UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"),
        UUID.fromString("99999999-8888-7777-6666-555555555555"),
        10,
        15,
        45,
      )

      for {
        state <- MasterServiceOfferVariantApiContractState.make
        _ <- state.setGetMasterServiceOfferVariantResult(Right(Some(variant)))
        response <- observe(combineApis(masterServiceOfferVariantApi(state)), get(s"/master-service-offer-variant/${variant.id}"))
        _ <- assertIO(response.status === Status.Ok)
        _ <- assertIO(
          response.body === s"""{"id":"${variant.id}","masterServiceOfferId":"${variant.masterServiceOfferId}","masterLocationId":"${variant.masterLocationId}","priceFrom":10,"priceTo":15,"durationMin":45}"""
        )
      } yield ()
    }

    "return 200 and null body for a missing variant" in {
      val variantId = UUID.fromString("66666666-7777-8888-9999-aaaaaaaaaaaa")

      for {
        state <- MasterServiceOfferVariantApiContractState.make
        _ <- state.setGetMasterServiceOfferVariantResult(Right(None))
        response <- observe(combineApis(masterServiceOfferVariantApi(state)), get(s"/master-service-offer-variant/$variantId"))
        _ <- assertIO(response.status === Status.Ok)
        _ <- assertIO(response.body === "null")
      } yield ()
    }

    "return 200 and exact json array for the offer endpoint" in {
      val offerId = UUID.fromString("12345678-1234-1234-1234-123456789abc")
      val first = variantOf(UUID.fromString("00000000-0000-0000-0000-000000000001"), offerId, UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001"), 1, 2, 30)
      val second = variantOf(UUID.fromString("00000000-0000-0000-0000-000000000002"), offerId, UUID.fromString("aaaaaaaa-0000-0000-0000-000000000002"), 3, 4, 45)

      for {
        state <- MasterServiceOfferVariantApiContractState.make
        _ <- state.setVariantsByOfferResult(offerId, Right(List(first, second)))
        response <- observe(combineApis(masterServiceOfferVariantApi(state)), get(s"/master-service-offer-variant/offer/$offerId"))
        _ <- assertIO(response.status === Status.Ok)
        _ <- assertIO(
          response.body === s"""[{"id":"${first.id}","masterServiceOfferId":"$offerId","masterLocationId":"${first.masterLocationId}","priceFrom":1,"priceTo":2,"durationMin":30},{"id":"${second.id}","masterServiceOfferId":"$offerId","masterLocationId":"${second.masterLocationId}","priceFrom":3,"priceTo":4,"durationMin":45}]"""
        )
      } yield ()
    }

    "return 200 and exact json array for the location endpoint" in {
      val locationId = UUID.fromString("12345678-0000-0000-0000-123456789abc")
      val first = variantOf(UUID.fromString("00000000-0000-0000-0000-000000000003"), UUID.fromString("bbbbbbbb-0000-0000-0000-000000000001"), locationId, 5, 6, 60)
      val second = variantOf(UUID.fromString("00000000-0000-0000-0000-000000000004"), UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002"), locationId, 7, 8, 75)

      for {
        state <- MasterServiceOfferVariantApiContractState.make
        _ <- state.setVariantsByLocationResult(locationId, Right(List(first, second)))
        response <- observe(combineApis(masterServiceOfferVariantApi(state)), get(s"/master-service-offer-variant/location/$locationId"))
        _ <- assertIO(response.status === Status.Ok)
        _ <- assertIO(
          response.body === s"""[{"id":"${first.id}","masterServiceOfferId":"${first.masterServiceOfferId}","masterLocationId":"$locationId","priceFrom":5,"priceTo":6,"durationMin":60},{"id":"${second.id}","masterServiceOfferId":"${second.masterServiceOfferId}","masterLocationId":"$locationId","priceFrom":7,"priceTo":8,"durationMin":75}]"""
        )
      } yield ()
    }

    "return 200 with empty body and capture the posted variant payload" in {
      val variant = variantOf(
        UUID.fromString("bbbbbbbb-cccc-dddd-eeee-ffffffffffff"),
        UUID.fromString("01010101-0202-0303-0404-050505050505"),
        UUID.fromString("06060606-0707-0808-0909-101010101010"),
        20,
        25,
        90,
      )
      val payload =
        s"""{"id":"${variant.id}","masterServiceOfferId":"${variant.masterServiceOfferId}","masterLocationId":"${variant.masterLocationId}","priceFrom":20,"priceTo":25,"durationMin":90}"""

      for {
        state <- MasterServiceOfferVariantApiContractState.make
        _ <- state.setUpsertMasterServiceOfferVariantResult(Right(()))
        response <- observe(combineApis(masterServiceOfferVariantApi(state)), postJson("/master-service-offer-variant", payload))
        upserts <- state.upserts
        _ <- assertIO(response.status === Status.Ok)
        _ <- assertIO(response.body === "")
        _ <- assertIO(upserts === Vector(variant))
      } yield ()
    }

    "return current 404 semantics for malformed UUID path params" in {
      for {
        state <- MasterServiceOfferVariantApiContractState.make
        response <- observe(combineApis(masterServiceOfferVariantApi(state)), get("/master-service-offer-variant/not-a-uuid"))
        _ <- assertIO(response.status === Status.NotFound)
        _ <- assertIO(response.body === "Not found")
      } yield ()
    }

    "return current malformed-json semantics and do not hit the repo on malformed JSON body" in {
      for {
        state <- MasterServiceOfferVariantApiContractState.make
        response <- observe(combineApis(masterServiceOfferVariantApi(state)), postJson("/master-service-offer-variant", """{"id":"abc""""))
        upserts <- state.upserts
        _ <- assertIO(response.status === Status.InternalServerError)
        _ <- assertIO(response.body === "")
        _ <- assertIO(upserts.isEmpty)
      } yield ()
    }

    "return current server failure semantics when the location endpoint fails" in {
      val locationId = UUID.fromString("99999999-0000-0000-0000-000000000000")

      for {
        state <- MasterServiceOfferVariantApiContractState.make
        _ <- state.setVariantsByLocationResult(locationId, Left(QueryFailure("get-master-service-offer-variants-by-location", new RuntimeException("variants-boom"))))
        response <- observe(combineApis(masterServiceOfferVariantApi(state)), get(s"/master-service-offer-variant/location/$locationId"))
        _ <- assertIO(response.status === Status.InternalServerError)
        _ <- assertIO(response.body === "")
      } yield ()
    }
  }

  private def variantOf(
    id: leaderboard.model.MasterServiceOfferVariantId,
    masterServiceOfferId: leaderboard.model.MasterServiceOfferId,
    masterLocationId: leaderboard.model.MasterLocationId,
    priceFrom: BigDecimal,
    priceTo: BigDecimal,
    durationMin: Int,
  ): MasterServiceOfferVariant =
    MasterServiceOfferVariant
      .make(id, masterServiceOfferId, masterLocationId, priceFrom, priceTo, durationMin)
      .fold(error => throw new IllegalArgumentException(error.message), identity)
}

class MasterServiceOfferVariantApiContractState private (
  private val upsertsRef: Ref[Vector[MasterServiceOfferVariant]],
  private val getMasterServiceOfferVariantResultRef: Ref[Either[QueryFailure, Option[MasterServiceOfferVariant]]],
  private val variantsByOfferResultsRef: Ref[Map[leaderboard.model.MasterServiceOfferId, Either[QueryFailure, List[MasterServiceOfferVariant]]]],
  private val variantsByLocationResultsRef: Ref[Map[leaderboard.model.MasterLocationId, Either[QueryFailure, List[MasterServiceOfferVariant]]]],
  private val upsertMasterServiceOfferVariantResultRef: Ref[Either[QueryFailure, Unit]],
) {
  val masterServiceOfferVariants: MasterServiceOfferVariants[IO] = new MasterServiceOfferVariants[IO] {
    def upsertMasterServiceOfferVariant(variant: MasterServiceOfferVariant): IO[QueryFailure, Unit] =
      upsertMasterServiceOfferVariantResultRef.get.flatMap {
        case Right(_) =>
          upsertsRef.update(_ :+ variant)
        case Left(error) =>
          ZIO.fail(error)
      }

    def getMasterServiceOfferVariant(id: MasterServiceOfferVariantId): IO[QueryFailure, Option[MasterServiceOfferVariant]] =
      getMasterServiceOfferVariantResultRef.get.flatMap(ZIO.fromEither(_))

    def getMasterServiceOfferVariantsByOffer(
      masterServiceOfferId: leaderboard.model.MasterServiceOfferId
    ): IO[QueryFailure, List[MasterServiceOfferVariant]] =
      variantsByOfferResultsRef.get.flatMap { current =>
        ZIO.fromEither(current.getOrElse(masterServiceOfferId, Right(Nil)))
      }

    def getMasterServiceOfferVariantsByLocation(
      masterLocationId: leaderboard.model.MasterLocationId
    ): IO[QueryFailure, List[MasterServiceOfferVariant]] =
      variantsByLocationResultsRef.get.flatMap { current =>
        ZIO.fromEither(current.getOrElse(masterLocationId, Right(Nil)))
      }
  }

  def upserts: UIO[Vector[MasterServiceOfferVariant]] =
    upsertsRef.get

  def setGetMasterServiceOfferVariantResult(
    result: Either[QueryFailure, Option[MasterServiceOfferVariant]]
  ): UIO[Unit] =
    getMasterServiceOfferVariantResultRef.set(result)

  def setVariantsByOfferResult(
    masterServiceOfferId: leaderboard.model.MasterServiceOfferId,
    result: Either[QueryFailure, List[MasterServiceOfferVariant]],
  ): UIO[Unit] =
    variantsByOfferResultsRef.update(_ + (masterServiceOfferId -> result))

  def setVariantsByLocationResult(
    masterLocationId: leaderboard.model.MasterLocationId,
    result: Either[QueryFailure, List[MasterServiceOfferVariant]],
  ): UIO[Unit] =
    variantsByLocationResultsRef.update(_ + (masterLocationId -> result))

  def setUpsertMasterServiceOfferVariantResult(result: Either[QueryFailure, Unit]): UIO[Unit] =
    upsertMasterServiceOfferVariantResultRef.set(result)
}

object MasterServiceOfferVariantApiContractState {
  def make: UIO[MasterServiceOfferVariantApiContractState] =
    for {
      upserts <- Ref.make(Vector.empty[MasterServiceOfferVariant])
      getMasterServiceOfferVariantResult <- Ref.make[Either[QueryFailure, Option[MasterServiceOfferVariant]]](Right(None))
      variantsByOfferResults <- Ref.make(
        Map.empty[leaderboard.model.MasterServiceOfferId, Either[QueryFailure, List[MasterServiceOfferVariant]]]
      )
      variantsByLocationResults <- Ref.make(
        Map.empty[leaderboard.model.MasterLocationId, Either[QueryFailure, List[MasterServiceOfferVariant]]]
      )
      upsertMasterServiceOfferVariantResult <- Ref.make[Either[QueryFailure, Unit]](Right(()))
    } yield new MasterServiceOfferVariantApiContractState(
      upserts,
      getMasterServiceOfferVariantResult,
      variantsByOfferResults,
      variantsByLocationResults,
      upsertMasterServiceOfferVariantResult,
    )
}
