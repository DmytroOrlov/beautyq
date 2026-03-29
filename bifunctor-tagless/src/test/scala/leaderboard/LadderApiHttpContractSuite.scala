package leaderboard

import izumi.distage.testkit.scalatest.{AssertZIO, SpecZIO}
import leaderboard.api.LadderApi
import leaderboard.http.tapir.{LadderTapirEndpoints, TapirHttpSupport}
import leaderboard.model.{QueryFailure, Score, UserId}
import leaderboard.repo.Ladder
import org.http4s.Status
import zio.interop.catz.*
import zio.{IO, Ref, UIO, ZIO}

import java.util.UUID

final class LadderApiHttpContractSuite extends SpecZIO with AssertZIO with HttpContractTestSupport {
  private val tapirHttpSupport = new TapirHttpSupport[IO]

  private def ladderApi(state: LadderApiContractState): LadderApi[IO] =
    new LadderApi[IO](state.ladder, LadderTapirEndpoints, tapirHttpSupport)

  "LadderApi current http4s contracts" should {
    "return 200 and exact json array shape for leaderboard scores" in {
      val user1 = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
      val user2 = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb")
      val scores = List(user1 -> 100L, user2 -> 55L)

      for {
        state <- LadderApiContractState.make
        _ <- state.setGetScoresResult(Right(scores))
        response <- observe(combineApis(ladderApi(state)), get("/ladder"))
        _ <- assertIO(response.status === Status.Ok)
        _ <- assertIO(response.body === s"""[["$user1",100],["$user2",55]]""")
      } yield ()
    }

    "return 200 with empty body and capture submitted score on POST" in {
      val userId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc")
      val score = 77L

      for {
        state <- LadderApiContractState.make
        _ <- state.setSubmitScoreResult(Right(()))
        response <- observe(combineApis(ladderApi(state)), postJson(s"/ladder/$userId/$score", ""))
        submitted <- state.submittedScores
        _ <- assertIO(response.status === Status.Ok)
        _ <- assertIO(response.body === "")
        _ <- assertIO(submitted === Vector(userId -> score))
      } yield ()
    }

    "return current 404 semantics for malformed UUID path params" in {
      for {
        state <- LadderApiContractState.make
        response <- observe(combineApis(ladderApi(state)), postJson("/ladder/not-a-uuid/15", ""))
        _ <- assertIO(response.status === Status.NotFound)
        _ <- assertIO(response.body === "Not found")
      } yield ()
    }

    "return current 404 semantics for malformed Long path params" in {
      val userId = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd")

      for {
        state <- LadderApiContractState.make
        response <- observe(combineApis(ladderApi(state)), postJson(s"/ladder/$userId/not-a-long", ""))
        _ <- assertIO(response.status === Status.NotFound)
        _ <- assertIO(response.body === "Not found")
      } yield ()
    }

    "return current server failure semantics when leaderboard lookup fails" in {
      for {
        state <- LadderApiContractState.make
        _ <- state.setGetScoresResult(Left(QueryFailure("get-leaderboard", new RuntimeException("scores-boom"))))
        response <- observe(combineApis(ladderApi(state)), get("/ladder"))
        _ <- assertIO(response.status === Status.InternalServerError)
        _ <- assertIO(response.body === "")
      } yield ()
    }

    "return current server failure semantics when score submission fails" in {
      val userId = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee")

      for {
        state <- LadderApiContractState.make
        _ <- state.setSubmitScoreResult(Left(QueryFailure("submit-score", new RuntimeException("submit-boom"))))
        response <- observe(combineApis(ladderApi(state)), postJson(s"/ladder/$userId/90", ""))
        submitted <- state.submittedScores
        _ <- assertIO(response.status === Status.InternalServerError)
        _ <- assertIO(response.body === "")
        _ <- assertIO(submitted.isEmpty)
      } yield ()
    }

    "return current 404 semantics for an unknown route in the isolated ladder app" in {
      for {
        state <- LadderApiContractState.make
        response <- observe(combineApis(ladderApi(state)), get("/ladder/unknown"))
        _ <- assertIO(response.status === Status.NotFound)
        _ <- assertIO(response.body === "Not found")
      } yield ()
    }
  }
}

final class LadderApiContractState private (
  private val submittedScoresRef: Ref[Vector[(UserId, Score)]],
  private val getScoresResultRef: Ref[Either[QueryFailure, List[(UserId, Score)]]],
  private val submitScoreResultRef: Ref[Either[QueryFailure, Unit]],
) {
  val ladder: Ladder[IO] = new Ladder[IO] {
    def submitScore(userId: UserId, score: Score): IO[QueryFailure, Unit] =
      submitScoreResultRef.get.flatMap {
        case Right(_) =>
          submittedScoresRef.update(_ :+ (userId -> score))
        case Left(error) =>
          ZIO.fail(error)
      }

    def getScores: IO[QueryFailure, List[(UserId, Score)]] =
      getScoresResultRef.get.flatMap(ZIO.fromEither(_))
  }

  def submittedScores: UIO[Vector[(UserId, Score)]] =
    submittedScoresRef.get

  def setGetScoresResult(result: Either[QueryFailure, List[(UserId, Score)]]): UIO[Unit] =
    getScoresResultRef.set(result)

  def setSubmitScoreResult(result: Either[QueryFailure, Unit]): UIO[Unit] =
    submitScoreResultRef.set(result)
}

object LadderApiContractState {
  def make: UIO[LadderApiContractState] =
    for {
      submittedScores <- Ref.make(Vector.empty[(UserId, Score)])
      getScoresResult <- Ref.make[Either[QueryFailure, List[(UserId, Score)]]](Right(Nil))
      submitScoreResult <- Ref.make[Either[QueryFailure, Unit]](Right(()))
    } yield new LadderApiContractState(submittedScores, getScoresResult, submitScoreResult)
}
