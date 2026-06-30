package leaderboard.search

import io.circe.Json
import leaderboard.model.QueryFailure
import leaderboard.search.dsl.BeautySearchSpecV1
import leaderboard.search.elasticsearch.{ElasticsearchJsonClient, ElasticsearchSearchBackend, ElasticsearchSearchInput, ElasticsearchSearchRequestInterpreter}
import org.scalatest.wordspec.AnyWordSpec
import zio.{IO, Runtime, Unsafe, ZIO}

final class ElasticsearchSearchBackendSpec extends AnyWordSpec {

  private val spec = BeautySearchSpecV1.spec
  private val input = UserSearchInput("nails", Some(BigDecimal("53.57532")), Some(BigDecimal("10.07672")), limit = 3)
  private val intent = ParsedSearchIntent("nails", Nil, Nil, Nil, "nails")

  private val emptySearchResponse: Json =
    Json.obj("hits" -> Json.obj("hits" -> Json.arr()))

  private def run[A](effect: IO[QueryFailure, A]): A =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
    }

  private def runEither[A](effect: IO[QueryFailure, A]): Either[QueryFailure, A] =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(effect.either).getOrThrowFiberFailure()
    }

  private def expectedRequest: Json =
    ElasticsearchSearchRequestInterpreter.request(spec.runtimeSpec(Map.empty), elasticsearchInput(input, intent)) match {
      case Right(json)  => json
      case Left(failure) => fail(s"request interpreter failed unexpectedly: ${failure.message}")
    }

  private def elasticsearchInput(input: UserSearchInput, intent: ParsedSearchIntent): ElasticsearchSearchInput =
    ElasticsearchSearchInput(
      remainingText = intent.remainingText,
      explicitConstraints = intent.explicitConstraints,
      softBoosts = intent.softBoosts,
      userLat = input.userLat,
      userLon = input.userLon,
      limit = input.limit,
    )

  private final class ExpectingElasticsearchJsonClient(
    expectedPath: String,
    expectedRequest: Json,
    postJsonResult: IO[QueryFailure, Json],
  ) extends ElasticsearchJsonClient {
    override def putJson(path: String, json: Json): IO[QueryFailure, Json] =
      ZIO.dieMessage(s"unexpected putJson($path)")
    override def post(path: String): IO[QueryFailure, Json] =
      ZIO.dieMessage(s"unexpected post($path)")
    override def postJson(path: String, json: Json): IO[QueryFailure, Json] = {
      assert(path == expectedPath, s"expected path $expectedPath, got $path")
      assert(json == expectedRequest, "request JSON mismatch")
      postJsonResult
    }
    override def postNdjson(path: String, payload: String): IO[QueryFailure, Json] =
      ZIO.dieMessage(s"unexpected postNdjson($path)")
    override def getJson(path: String): IO[QueryFailure, Json] =
      ZIO.dieMessage(s"unexpected getJson($path)")
    override def delete(path: String): IO[QueryFailure, Unit] =
      ZIO.dieMessage(s"unexpected delete($path)")
  }

  "ElasticsearchSearchBackend.search" should {
    "post the interpreted request to the configured ES index and return interpreted response" in {
      val expectedPath = s"/${spec.variantDocument.indexName}/_search"
      val client = new ExpectingElasticsearchJsonClient(
        expectedPath = expectedPath,
        expectedRequest = expectedRequest,
        postJsonResult = ZIO.succeed(emptySearchResponse),
      )
      val backend = new ElasticsearchSearchBackend(spec, client)
      val response = run(backend.search(input, intent))
      assert(response.variantCarousel.isEmpty)
      assert(response.providerCarousel.isEmpty)
      assert(response.serviceIntentCarousel.isEmpty)
    }

    "propagate Elasticsearch client failures" in {
      val client = new ExpectingElasticsearchJsonClient(
        expectedPath = s"/${spec.variantDocument.indexName}/_search",
        expectedRequest = expectedRequest,
        postJsonResult = ZIO.fail(ElasticsearchJsonClient.failure("boom")),
      )
      val backend = new ElasticsearchSearchBackend(spec, client)
      val result = runEither(backend.search(input, intent))
      result match {
        case Left(QueryFailure.OperationFailure(operationName, message)) =>
          assert(operationName == "elasticsearch-json-client")
          assert(message == "boom")
        case other =>
          fail(s"Expected OperationFailure(elasticsearch-json-client, boom), got $other")
      }
    }

    "propagate response decode failures" in {
      val client = new ExpectingElasticsearchJsonClient(
        expectedPath = s"/${spec.variantDocument.indexName}/_search",
        expectedRequest = expectedRequest,
        postJsonResult = ZIO.succeed(Json.obj()),
      )
      val backend = new ElasticsearchSearchBackend(spec, client)
      val result = runEither(backend.search(input, intent))
      result match {
        case Left(QueryFailure.OperationFailure(operationName, _)) =>
          assert(operationName == "decode-elasticsearch-search-response")
        case other =>
          fail(s"Expected OperationFailure(decode-elasticsearch-search-response, ...), got $other")
      }
    }
  }
}
