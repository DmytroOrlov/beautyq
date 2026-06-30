package leaderboard.search

import io.circe.Json
import leaderboard.model.QueryFailure
import leaderboard.search.elasticsearch.ElasticsearchJsonClient
import org.scalatest.wordspec.AnyWordSpec
import zio.{IO, Runtime, Unsafe, ZIO}

final class ElasticsearchJsonClientSpec extends AnyWordSpec {

  private def run[A](effect: IO[QueryFailure, A]): A =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
    }

  private def runFail[A](effect: IO[QueryFailure, A]): QueryFailure =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(effect.either).getOrThrowFiberFailure().swap.toOption match {
        case Some(error) => error
        case None        => fail("expected QueryFailure")
      }
    }

  private final class ScriptedElasticsearchJsonClient(
    putResult: IO[QueryFailure, Json] = ZIO.succeed(Json.obj()),
    postResult: IO[QueryFailure, Json] = ZIO.succeed(Json.obj()),
    postJsonResult: IO[QueryFailure, Json] = ZIO.succeed(Json.obj()),
    postNdjsonResult: IO[QueryFailure, Json] = ZIO.succeed(Json.obj()),
    getJsonResult: IO[QueryFailure, Json] = ZIO.succeed(Json.obj()),
    deleteResult: IO[QueryFailure, Unit] = ZIO.unit,
  ) extends ElasticsearchJsonClient {
    override def putJson(path: String, json: Json): IO[QueryFailure, Json]       = putResult
    override def post(path: String): IO[QueryFailure, Json]                      = postResult
    override def postJson(path: String, json: Json): IO[QueryFailure, Json]      = postJsonResult
    override def postNdjson(path: String, payload: String): IO[QueryFailure, Json] = postNdjsonResult
    override def getJson(path: String): IO[QueryFailure, Json]                   = getJsonResult
    override def delete(path: String): IO[QueryFailure, Unit]                    = deleteResult
  }

  "ElasticsearchJsonClient.failure" should {
    "return OperationFailure with the expected operation name" in {
      val failure = ElasticsearchJsonClient.failure("test-message")
      failure match {
        case QueryFailure.OperationFailure(operationName, message) =>
          assert(operationName == "elasticsearch-json-client")
          assert(message == "test-message")
        case other =>
          fail(s"Expected OperationFailure, got $other")
      }
    }
  }

  "ScriptedElasticsearchJsonClient" should {
    "return JSON from postJson" in {
      val expected = Json.obj("ok" -> Json.fromBoolean(true))
      val client = new ScriptedElasticsearchJsonClient(postJsonResult = ZIO.succeed(expected))
      val result = run(client.postJson("/test", Json.obj()))
      assert(result == expected)
    }

    "return JSON from postNdjson" in {
      val expected = Json.obj("items" -> Json.arr())
      val client = new ScriptedElasticsearchJsonClient(postNdjsonResult = ZIO.succeed(expected))
      val result = run(client.postNdjson("/test/_bulk", "{}"))
      assert(result == expected)
    }

    "fail through IO with the expected operation name" in {
      val client = new ScriptedElasticsearchJsonClient(
        postJsonResult = ZIO.fail(ElasticsearchJsonClient.failure("boom")),
      )
      val failure = runFail(client.postJson("/test", Json.obj()))
      failure match {
        case QueryFailure.OperationFailure(operationName, message) =>
          assert(operationName == "elasticsearch-json-client")
          assert(message == "boom")
        case other =>
          fail(s"Expected OperationFailure, got $other")
      }
    }
  }
}
