package leaderboard

import izumi.distage.testkit.scalatest.{AssertZIO, SpecZIO}
import leaderboard.api.MasterLocationApi
import leaderboard.http.tapir.{MasterLocationTapirEndpoints, TapirHttpSupport}
import leaderboard.model.{MasterLocation, QueryFailure}
import leaderboard.repo.MasterLocations
import org.http4s.Status
import zio.interop.catz.*
import zio.{IO, Ref, UIO, ZIO}

import java.util.UUID

class LegacySingleEntityGetHttpContractSuite extends SpecZIO with AssertZIO with HttpContractTestSupport {
  private def masterLocationApi(state: LegacySingleEntityGetContractState): MasterLocationApi[IO] =
    new MasterLocationApi[IO](state.masterLocations, MasterLocationTapirEndpoints, new TapirHttpSupport[IO])

  "Legacy single entity GET contracts" should {
    "pin 200 and null for a missing legacy master location" in {
      val locationId = UUID.fromString("66666666-7777-8888-9999-aaaaaaaaaaaa")

      for {
        state    <- LegacySingleEntityGetContractState.make
        _        <- state.setGetMasterLocationResult(Right(None))
        response <- observe(combineApis(masterLocationApi(state)), get(s"/master-location/$locationId"))
        _        <- assertIO(response.status === Status.Ok)
        _        <- assertIO(response.body === "null")
      } yield ()
    }

    "pin 200 and exact json for an existing legacy master location" in {
      val locationId = UUID.fromString("11111111-2222-3333-4444-555555555555")
      val masterId   = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")
      val location   = MasterLocation(locationId, masterId, "Studio", "Main street", 55, 37)

      for {
        state    <- LegacySingleEntityGetContractState.make
        _        <- state.setGetMasterLocationResult(Right(Some(location)))
        response <- observe(combineApis(masterLocationApi(state)), get(s"/master-location/$locationId"))
        _        <- assertIO(response.status === Status.Ok)
        _        <- assertIO(
          response.body === s"""{"id":"$locationId","masterId":"$masterId","name":"Studio","address":"Main street","lat":55,"lon":37}"""
        )
      } yield ()
    }
  }
}

class LegacySingleEntityGetContractState private (
  private val getMasterLocationResultRef: Ref[Either[QueryFailure, Option[MasterLocation]]],
) {
  val masterLocations: MasterLocations[IO] = new MasterLocations[IO] {
    def upsertMasterLocation(location: MasterLocation): IO[QueryFailure, Unit] =
      ZIO.unit

    def getMasterLocation(id: leaderboard.model.MasterLocationId): IO[QueryFailure, Option[MasterLocation]] =
      getMasterLocationResultRef.get.flatMap(ZIO.fromEither(_))

    def getMasterLocationsByMaster(masterId: leaderboard.model.MasterId): IO[QueryFailure, List[MasterLocation]] =
      ZIO.succeed(Nil)
  }

  def setGetMasterLocationResult(result: Either[QueryFailure, Option[MasterLocation]]): UIO[Unit] =
    getMasterLocationResultRef.set(result)
}

object LegacySingleEntityGetContractState {
  def make: UIO[LegacySingleEntityGetContractState] =
    for {
      getMasterLocationResult <- Ref.make[Either[QueryFailure, Option[MasterLocation]]](Right(None))
    } yield new LegacySingleEntityGetContractState(getMasterLocationResult)
}
