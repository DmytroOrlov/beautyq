package leaderboard

import izumi.distage.testkit.scalatest.{AssertZIO, SpecZIO}
import leaderboard.api.{MasterApi, ServiceApi}
import leaderboard.http.tapir.{MasterTapirEndpoints, ServiceTapirEndpoints, TapirHttpSupport}
import leaderboard.model.{Master, QueryFailure, Service}
import org.http4s.Status
import zio.interop.catz.*
import zio.IO

import java.util.UUID

class LegacySingleEntityGetHttpContractSuite extends SpecZIO with AssertZIO with HttpContractTestSupport {
  private def masterApi(state: MasterApiContractState): MasterApi[IO] =
    new MasterApi[IO](state.masters, MasterTapirEndpoints, new TapirHttpSupport[IO])

  private def serviceApi(state: ServiceApiContractState): ServiceApi[IO] =
    new ServiceApi[IO](state.services, ServiceTapirEndpoints, new TapirHttpSupport[IO])

  "Legacy single-entity GET http4s contracts" should {
    "preserve legacy 200+null behavior for missing master GET responses" in {
      val masterId = UUID.fromString("66666666-7777-8888-9999-aaaaaaaaaaaa")

      for {
        state    <- MasterApiContractState.make
        _        <- state.setGetMasterResult(Right(None))
        response <- observe(combineApis(masterApi(state)), get(s"/master/$masterId"))
        _        <- assertIO(response.status === Status.Ok)
        _        <- assertIO(response.body === "null")
      } yield ()
    }

    "preserve legacy 200+object-body behavior for existing master GET responses" in {
      val masterId = UUID.fromString("11111111-2222-3333-4444-555555555555")
      val master   = Master(masterId, "Kai")

      for {
        state    <- MasterApiContractState.make
        _        <- state.setGetMasterResult(Right(Some(master)))
        response <- observe(combineApis(masterApi(state)), get(s"/master/$masterId"))
        _        <- assertIO(response.status === Status.Ok)
        _        <- assertIO(response.body === s"""{"id":"$masterId","name":"Kai"}""")
      } yield ()
    }

    "preserve legacy 200+null behavior for missing service GET responses" in {
      val serviceId = UUID.fromString("22222222-2222-2222-2222-222222222222")

      for {
        state    <- ServiceApiContractState.make
        _        <- state.setGetServiceResult(Right(None))
        response <- observe(combineApis(serviceApi(state)), get(s"/service/$serviceId"))
        _        <- assertIO(response.status === Status.Ok)
        _        <- assertIO(response.body === "null")
      } yield ()
    }

    "preserve legacy 200+object-body behavior for existing service GET responses" in {
      val serviceId  = UUID.fromString("11111111-1111-1111-1111-111111111111")
      val categoryId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
      val service    = Service(serviceId, categoryId, "Cut")

      for {
        state    <- ServiceApiContractState.make
        _        <- state.setGetServiceResult(Right(Some(service)))
        response <- observe(combineApis(serviceApi(state)), get(s"/service/$serviceId"))
        _        <- assertIO(response.status === Status.Ok)
        _        <- assertIO(response.body === s"""{"id":"$serviceId","categoryId":"$categoryId","name":"Cut"}""")
      } yield ()
    }
  }
}
