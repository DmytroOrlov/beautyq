package leaderboard

import izumi.distage.testkit.scalatest.{AssertZIO, SpecZIO}
import leaderboard.api.MasterLocationApi
import leaderboard.http.tapir.MasterLocationTapirEndpoints
import leaderboard.model.{MasterId, MasterLocation, MasterLocationId, QueryFailure}
import leaderboard.repo.MasterLocations
import org.http4s.Status
import zio.interop.catz.*
import zio.{IO, Ref, UIO, ZIO}

class MasterLocationApiHttpContractSuite extends SpecZIO with AssertZIO with HttpContractTestSupport {
  private def masterLocationApi(state: MasterLocationApiContractState): MasterLocationApi[IO] =
    new MasterLocationApi[IO](state.masterLocations, MasterLocationTapirEndpoints)

  "MasterLocationApi current http4s contracts" should {
    "return 200 and exact location json for an existing entity" in {
      val locationId = MasterLocationId.fromString("11111111-2222-3333-4444-555555555555")
      val masterId   = MasterId.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")
      val location   = MasterLocation(locationId, masterId, "Studio", "Main street", 55, 37)

      for {
        state    <- MasterLocationApiContractState.make
        _        <- state.setGetMasterLocationResult(Right(Some(location)))
        response <- observe(combineApis(masterLocationApi(state)), get(s"/master-location/$locationId"))
        _        <- assertIO(response.status === Status.Ok)
        _        <- assertIO(
          response.body === s"""{"id":"$locationId","masterId":"$masterId","name":"Studio","address":"Main street","lat":55,"lon":37}"""
        )
      } yield ()
    }

    "return 404 and typed error json for a missing location" in {
      val locationId = MasterLocationId.fromString("66666666-7777-8888-9999-aaaaaaaaaaaa")

      for {
        state    <- MasterLocationApiContractState.make
        _        <- state.setGetMasterLocationResult(Right(None))
        response <- observe(combineApis(masterLocationApi(state)), get(s"/master-location/$locationId"))
        _        <- assertIO(response.status === Status.NotFound)
        _        <- assertIO(response.body === s"""{"code":"not_found","message":"Master location '$locationId' was not found"}""")
      } yield ()
    }

    "return 200 and exact json array for the master endpoint" in {
      val masterId = MasterId.fromString("12345678-1234-1234-1234-123456789abc")
      val first    = MasterLocation(MasterLocationId.fromString("00000000-0000-0000-0000-000000000001"), masterId, "Alpha", "A", 1, 2)
      val second   = MasterLocation(MasterLocationId.fromString("00000000-0000-0000-0000-000000000002"), masterId, "Beta", "B", 3, 4)

      for {
        state    <- MasterLocationApiContractState.make
        _        <- state.setLocationsByMasterResult(masterId, Right(List(first, second)))
        response <- observe(combineApis(masterLocationApi(state)), get(s"/master-location/master/$masterId"))
        _        <- assertIO(response.status === Status.Ok)
        _        <- assertIO(
          response.body === s"""[{"id":"${first.id}","masterId":"$masterId","name":"Alpha","address":"A","lat":1,"lon":2},{"id":"${second.id}","masterId":"$masterId","name":"Beta","address":"B","lat":3,"lon":4}]"""
        )
      } yield ()
    }

    "return 200 with empty body and capture the posted location payload" in {
      val locationId = MasterLocationId.fromString("bbbbbbbb-cccc-dddd-eeee-ffffffffffff")
      val masterId   = MasterId.fromString("01010101-0202-0303-0404-050505050505")
      val payload    = s"""{"id":"$locationId","masterId":"$masterId","name":"Office","address":"Center","lat":50,"lon":30}"""

      for {
        state    <- MasterLocationApiContractState.make
        _        <- state.setUpsertMasterLocationResult(Right(()))
        response <- observe(combineApis(masterLocationApi(state)), postJson("/master-location", payload))
        upserts  <- state.upserts
        _        <- assertIO(response.status === Status.Ok)
        _        <- assertIO(response.body === "")
        _        <- assertIO(upserts === Vector(MasterLocation(locationId, masterId, "Office", "Center", 50, 30)))
      } yield ()
    }

    "return Tapir default bad-input response for malformed UUID path params" in {
      for {
        state    <- MasterLocationApiContractState.make
        response <- observe(combineApis(masterLocationApi(state)), get("/master-location/not-a-uuid"))
        _        <- assertIO(response.status === Status.BadRequest)
      } yield ()
    }

    "return Tapir default bad-input response and do not hit the repo on malformed JSON body" in {
      for {
        state    <- MasterLocationApiContractState.make
        response <- observe(combineApis(masterLocationApi(state)), postJson("/master-location", """{"id":"abc""""))
        upserts  <- state.upserts
        _        <- assertIO(response.status === Status.BadRequest)
        _        <- assertIO(upserts.isEmpty)
      } yield ()
    }

    "return current server failure semantics when the master endpoint fails" in {
      val masterId = MasterId.fromString("99999999-0000-0000-0000-000000000000")

      for {
        state    <- MasterLocationApiContractState.make
        _        <- state.setLocationsByMasterResult(masterId, Left(QueryFailure.fromThrowable("get-master-locations-by-master", new RuntimeException("locations-boom"))))
        response <- observe(combineApis(masterLocationApi(state)), get(s"/master-location/master/$masterId"))
        _        <- assertIO(response.status === Status.InternalServerError)
        _        <- assertIO(response.body === "")
      } yield ()
    }
  }
}

class MasterLocationApiContractState private (
  private val upsertsRef: Ref[Vector[MasterLocation]],
  private val getMasterLocationResultRef: Ref[Either[QueryFailure, Option[MasterLocation]]],
  private val locationsByMasterResultsRef: Ref[Map[leaderboard.model.MasterId, Either[QueryFailure, List[MasterLocation]]]],
  private val upsertMasterLocationResultRef: Ref[Either[QueryFailure, Unit]],
) {
  val masterLocations: MasterLocations[IO] = new MasterLocations[IO] {
    def upsertMasterLocation(location: MasterLocation): IO[QueryFailure, Unit] =
      upsertMasterLocationResultRef.get.flatMap {
        case Right(_) =>
          upsertsRef.update(_ :+ location)
        case Left(error) =>
          ZIO.fail(error)
      }

    def getMasterLocation(id: MasterLocationId): IO[QueryFailure, Option[MasterLocation]] =
      getMasterLocationResultRef.get.flatMap(ZIO.fromEither(_))

    def getMasterLocationsByMaster(masterId: leaderboard.model.MasterId): IO[QueryFailure, List[MasterLocation]] =
      locationsByMasterResultsRef.get.flatMap {
        current =>
          ZIO.fromEither(current.getOrElse(masterId, Right(Nil)))
      }
  }

  def upserts: UIO[Vector[MasterLocation]] =
    upsertsRef.get

  def setGetMasterLocationResult(result: Either[QueryFailure, Option[MasterLocation]]): UIO[Unit] =
    getMasterLocationResultRef.set(result)

  def setLocationsByMasterResult(
    masterId: leaderboard.model.MasterId,
    result: Either[QueryFailure, List[MasterLocation]],
  ): UIO[Unit] =
    locationsByMasterResultsRef.update(_ + (masterId -> result))

  def setUpsertMasterLocationResult(result: Either[QueryFailure, Unit]): UIO[Unit] =
    upsertMasterLocationResultRef.set(result)
}

object MasterLocationApiContractState {
  def make: UIO[MasterLocationApiContractState] =
    for {
      upserts                    <- Ref.make(Vector.empty[MasterLocation])
      getMasterLocationResult    <- Ref.make[Either[QueryFailure, Option[MasterLocation]]](Right(None))
      locationsByMasterResults   <- Ref.make(Map.empty[leaderboard.model.MasterId, Either[QueryFailure, List[MasterLocation]]])
      upsertMasterLocationResult <- Ref.make[Either[QueryFailure, Unit]](Right(()))
    } yield new MasterLocationApiContractState(upserts, getMasterLocationResult, locationsByMasterResults, upsertMasterLocationResult)
}
