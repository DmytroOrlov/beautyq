package leaderboard

import izumi.distage.testkit.scalatest.{AssertZIO, SpecZIO}
import leaderboard.api.MasterApi
import leaderboard.http.tapir.{MasterTapirEndpoints, TapirHttpSupport}
import leaderboard.model.{Master, MasterId, QueryFailure}
import leaderboard.repo.Masters
import org.http4s.Status
import zio.interop.catz.*
import zio.{IO, Ref, UIO, ZIO}

import java.util.UUID

class MasterApiHttpContractSuite extends SpecZIO with AssertZIO with HttpContractTestSupport {
  private def masterApi(state: MasterApiContractState): MasterApi[IO] =
    new MasterApi[IO](state.masters, MasterTapirEndpoints, new TapirHttpSupport[IO])

  "MasterApi current http4s contracts" should {
    "return 200 and exact master json for an existing entity" in {
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

    "return 404 and typed error JSON for a missing master" in {
      val masterId = UUID.fromString("66666666-7777-8888-9999-aaaaaaaaaaaa")

      for {
        state    <- MasterApiContractState.make
        _        <- state.setGetMasterResult(Right(None))
        response <- observe(combineApis(masterApi(state)), get(s"/master/$masterId"))
        _        <- assertIO(response.status === Status.NotFound)
        _        <- assertIO(response.body === s"""{"code":"not_found","message":"Master '$masterId' was not found"}""")
      } yield ()
    }

    "return 200 and exact json array for the master list endpoint" in {
      val first  = Master(UUID.fromString("00000000-0000-0000-0000-000000000001"), "Alpha")
      val second = Master(UUID.fromString("00000000-0000-0000-0000-000000000002"), "Beta")

      for {
        state    <- MasterApiContractState.make
        _        <- state.setGetMastersResult(Right(List(first, second)))
        response <- observe(combineApis(masterApi(state)), get("/master"))
        _        <- assertIO(response.status === Status.Ok)
        _        <- assertIO(
          response.body === s"""[{"id":"${first.id}","name":"Alpha"},{"id":"${second.id}","name":"Beta"}]"""
        )
      } yield ()
    }

    "return 200 with empty body and capture the posted master payload" in {
      val masterId = UUID.fromString("bbbbbbbb-cccc-dddd-eeee-ffffffffffff")
      val payload  = s"""{"id":"$masterId","name":"Nori"}"""

      for {
        state    <- MasterApiContractState.make
        _        <- state.setUpsertMasterResult(Right(()))
        response <- observe(combineApis(masterApi(state)), postJson("/master", payload))
        upserts  <- state.upserts
        _        <- assertIO(response.status === Status.Ok)
        _        <- assertIO(response.body === "")
        _        <- assertIO(upserts === Vector(Master(masterId, "Nori")))
      } yield ()
    }

    "return current 404 semantics for malformed UUID path params" in {
      for {
        state    <- MasterApiContractState.make
        response <- observe(combineApis(masterApi(state)), get("/master/not-a-uuid"))
        _        <- assertIO(response.status === Status.NotFound)
        _        <- assertIO(response.body === "Not found")
      } yield ()
    }

    "return current malformed-json semantics and do not hit the repo on malformed JSON body" in {
      for {
        state    <- MasterApiContractState.make
        response <- observe(combineApis(masterApi(state)), postJson("/master", """{"id":"abc""""))
        upserts  <- state.upserts
        _        <- assertIO(response.status === Status.InternalServerError)
        _        <- assertIO(response.body === "")
        _        <- assertIO(upserts.isEmpty)
      } yield ()
    }

    "return current missing-field semantics and do not hit the repo when a required field is absent" in {
      for {
        state    <- MasterApiContractState.make
        response <- observe(combineApis(masterApi(state)), postJson("/master", """{"id":"11111111-1111-1111-1111-111111111111"}"""))
        upserts  <- state.upserts
        _        <- assertIO(response.status === Status.InternalServerError)
        _        <- assertIO(response.body === "")
        _        <- assertIO(upserts.isEmpty)
      } yield ()
    }

    "return current invalid-field-type semantics and do not hit the repo on wrong json field types" in {
      for {
        state    <- MasterApiContractState.make
        response <- observe(combineApis(masterApi(state)), postJson("/master", """{"id":"11111111-1111-1111-1111-111111111111","name":123}"""))
        upserts  <- state.upserts
        _        <- assertIO(response.status === Status.InternalServerError)
        _        <- assertIO(response.body === "")
        _        <- assertIO(upserts.isEmpty)
      } yield ()
    }

    "return current empty-body semantics and do not hit the repo on empty request bodies" in {
      for {
        state    <- MasterApiContractState.make
        response <- observe(combineApis(masterApi(state)), postJson("/master", ""))
        upserts  <- state.upserts
        _        <- assertIO(response.status === Status.InternalServerError)
        _        <- assertIO(response.body === "")
        _        <- assertIO(upserts.isEmpty)
      } yield ()
    }

    "return current server failure semantics when single-master lookup fails" in {
      val masterId = UUID.fromString("12345678-1234-1234-1234-123456789abc")

      for {
        state    <- MasterApiContractState.make
        _        <- state.setGetMasterResult(Left(QueryFailure.fromThrowable("get-master", new RuntimeException("get-master-boom"))))
        response <- observe(combineApis(masterApi(state)), get(s"/master/$masterId"))
        _        <- assertIO(response.status === Status.InternalServerError)
        _        <- assertIO(response.body === "")
      } yield ()
    }

    "return current server failure semantics when master list lookup fails" in {
      for {
        state    <- MasterApiContractState.make
        _        <- state.setGetMastersResult(Left(QueryFailure.fromThrowable("get-masters", new RuntimeException("get-masters-boom"))))
        response <- observe(combineApis(masterApi(state)), get("/master"))
        _        <- assertIO(response.status === Status.InternalServerError)
        _        <- assertIO(response.body === "")
      } yield ()
    }

    "return current server failure semantics when master upsert fails" in {
      val payload = """{"id":"99999999-9999-9999-9999-999999999999","name":"Fail"}"""

      for {
        state    <- MasterApiContractState.make
        _        <- state.setUpsertMasterResult(Left(QueryFailure.fromThrowable("upsert-master", new RuntimeException("upsert-master-boom"))))
        response <- observe(combineApis(masterApi(state)), postJson("/master", payload))
        upserts  <- state.upserts
        _        <- assertIO(response.status === Status.InternalServerError)
        _        <- assertIO(response.body === "")
        _        <- assertIO(upserts.isEmpty)
      } yield ()
    }
  }
}

class MasterApiContractState private (
  private val upsertsRef: Ref[Vector[Master]],
  private val getMasterResultRef: Ref[Either[QueryFailure, Option[Master]]],
  private val getMastersResultRef: Ref[Either[QueryFailure, List[Master]]],
  private val upsertMasterResultRef: Ref[Either[QueryFailure, Unit]],
) {
  val masters: Masters[IO] = new Masters[IO] {
    def upsertMaster(master: Master): IO[QueryFailure, Unit] =
      upsertMasterResultRef.get.flatMap {
        case Right(_) =>
          upsertsRef.update(_ :+ master)
        case Left(error) =>
          ZIO.fail(error)
      }

    def getMaster(id: MasterId): IO[QueryFailure, Option[Master]] =
      getMasterResultRef.get.flatMap(ZIO.fromEither(_))

    def getMasters(): IO[QueryFailure, List[Master]] =
      getMastersResultRef.get.flatMap(ZIO.fromEither(_))
  }

  def upserts: UIO[Vector[Master]] =
    upsertsRef.get

  def setGetMasterResult(result: Either[QueryFailure, Option[Master]]): UIO[Unit] =
    getMasterResultRef.set(result)

  def setGetMastersResult(result: Either[QueryFailure, List[Master]]): UIO[Unit] =
    getMastersResultRef.set(result)

  def setUpsertMasterResult(result: Either[QueryFailure, Unit]): UIO[Unit] =
    upsertMasterResultRef.set(result)
}

object MasterApiContractState {
  def make: UIO[MasterApiContractState] =
    for {
      upserts            <- Ref.make(Vector.empty[Master])
      getMasterResult    <- Ref.make[Either[QueryFailure, Option[Master]]](Right(None))
      getMastersResult   <- Ref.make[Either[QueryFailure, List[Master]]](Right(Nil))
      upsertMasterResult <- Ref.make[Either[QueryFailure, Unit]](Right(()))
    } yield new MasterApiContractState(upserts, getMasterResult, getMastersResult, upsertMasterResult)
}
