package leaderboard.search

import io.circe.Json
import leaderboard.search.beautyq.gen2.wiring.BeautyQEmbeddingRequestError
import leaderboard.search.gen2.qdrant.*
import leaderboard.search.gen2.transport.*
import leaderboard.search.gen2.BeautyQGen2EmbeddingClient
import org.scalatest.wordspec.AnyWordSpec

/** Focused proofs for the BeautyQ Gen2 embedding adapter. Every success path
  * asserts the exact request path/JSON and the response-shape contract; every
  * failure path is asserted as the typed error and never silently downgraded
  * to a transport/connectivity error. */
final class BeautyQGen2EmbeddingClientSpec extends AnyWordSpec {

  "BeautyQGen2EmbeddingClient" should {
    "issue the exact path/JSON request and accept a model-matching embedding" in {
      val client = scriptedClient(
        Response(
          status = 200,
          body = matchingEmbeddingJson(Seq(0.1, 0.2, 0.3)),
          captureRequest = Some(req => {
            assert(req.path == "/v1/embeddings")
            assert(req.body.hcursor.get[String]("input").toOption.contains("search this"))
            assert(req.body.hcursor.get[String]("model").toOption.contains("qwen-embed"))
            (): Unit
          }),
        ),
      )
      val input = sampleInput("search this")
      client.embed(input) match {
        case Right(result) =>
          assert(result.model.model == "qwen-embed")
          assert(result.values == Vector(0.1, 0.2, 0.3))
        case Left(error) => fail(s"expected embedding success, got $error")
      }
    }

    "reject a response missing the model field as InvalidResult.ModelMismatch" in {
      val client = scriptedClient(
        Response(
          status = 200,
          body = Json.arr(Json.obj("embedding" -> Json.fromValues(Vector(0.1, 0.2, 0.3).map(v => Json.fromDoubleOrNull(v))))),
          captureRequest = Some(_ => ()),
        ),
      )
      client.embed(sampleInput("any text")) match {
        case Left(BeautyQEmbeddingRequestError.InvalidResult(error)) =>
          error match {
            case mm: QdrantEmbeddingError.ModelMismatch =>
              assert(mm.expected == sampleModel)
              assert(mm.actual == mm.expected)
            case other => fail(s"expected ModelMismatch, got $other")
          }
        case other => fail(s"expected ModelMismatch InvalidResult, got $other")
      }
    }

    "reject a non-string model value as InvalidResult.ModelMismatch" in {
      val client = scriptedClient(
        Response(
          status = 200,
          body = Json.obj(
            "model" -> Json.fromInt(42),
            "data" -> Json.arr(Json.obj("embedding" -> Json.fromValues(Vector(0.1, 0.2, 0.3).map(v => Json.fromDoubleOrNull(v))))),
          ),
          captureRequest = Some(_ => ()),
        ),
      )
      client.embed(sampleInput("any text")) match {
        case Left(BeautyQEmbeddingRequestError.InvalidResult(error)) =>
          error match {
            case mm: QdrantEmbeddingError.ModelMismatch =>
              assert(mm.expected == sampleModel)
              assert(mm.actual == mm.expected)
            case other => fail(s"expected ModelMismatch, got $other")
          }
        case other => fail(s"expected ModelMismatch InvalidResult, got $other")
      }
    }

    "reject a wrong model as InvalidResult.ModelMismatch with the returned name" in {
      val client = scriptedClient(
        Response(
          status = 200,
          body = Json.obj(
            "model" -> Json.fromString("different-embed"),
            "data" -> Json.arr(Json.obj("embedding" -> Json.fromValues(Vector(0.1, 0.2, 0.3).map(v => Json.fromDoubleOrNull(v))))),
          ),
          captureRequest = Some(_ => ()),
        ),
      )
      client.embed(sampleInput("any text")) match {
        case Left(BeautyQEmbeddingRequestError.InvalidResult(error)) =>
          error match {
            case mm: QdrantEmbeddingError.ModelMismatch =>
              assert(mm.expected == sampleModel)
              assert(mm.actual.model == "different-embed")
              assert(mm.actual.provider == mm.expected.provider)
              assert(mm.actual.revision == mm.expected.revision)
              assert(mm.actual.dimension == mm.expected.dimension)
            case other => fail(s"expected ModelMismatch, got $other")
          }
        case other => fail(s"expected ModelMismatch InvalidResult, got $other")
      }
    }

    "reject empty data as a typed Transport error" in {
      val client = scriptedClient(
        Response(
          status = 200,
          body = Json.obj("model" -> Json.fromString("qwen-embed"), "data" -> Json.arr()),
          captureRequest = Some(_ => ()),
        ),
      )
      client.embed(sampleInput("any text")) match {
        case Left(BeautyQEmbeddingRequestError.Transport(message)) =>
          assert(message.contains("embedding response data is empty"))
        case other => fail(s"expected Transport, got $other")
      }
    }

    "reject wrong dimension as InvalidResult.DimensionMismatch" in {
      val client = scriptedClient(
        Response(
          status = 200,
          body = matchingEmbeddingJson(Seq(0.1, 0.2)),
          captureRequest = Some(_ => ()),
        ),
      )
      client.embed(sampleInput("any text")) match {
        case Left(BeautyQEmbeddingRequestError.InvalidResult(QdrantEmbeddingError.DimensionMismatch(expected, actual))) =>
          assert(expected == 3)
          assert(actual == 2)
        case other => fail(s"expected DimensionMismatch, got $other")
      }
    }

    "reject non-finite values as InvalidResult.NonFinite" in {
      val client = scriptedClient(
        Response(
          status = 200,
          body = matchingEmbeddingJson(Seq(0.1, Double.NaN, 0.3)),
          captureRequest = Some(_ => ()),
        ),
      )
      client.embed(sampleInput("any text")) match {
        case Left(BeautyQEmbeddingRequestError.InvalidResult(QdrantEmbeddingError.NonFinite(index, value))) =>
          assert(index == 1)
          assert(value.isNaN)
        case other => fail(s"expected NonFinite, got $other")
      }
    }

    "map a connection failure to Unavailable (never InvalidResult)" in {
      val client = scriptedClient(
        Response(transportError = Some(Gen2HttpTransportError.ConnectionFailed("POST", "/v1/embeddings", "connect refused"))),
      )
      client.embed(sampleInput("any text")) match {
        case Left(BeautyQEmbeddingRequestError.Unavailable(message)) =>
          assert(message.contains("connect refused"))
        case other => fail(s"expected Unavailable, got $other")
      }
    }

    "map an HTTP 408 response to Timeout" in {
      val client = scriptedClient(
        Response(
          status = 408,
          body = Json.fromString("{\"error\":\"upstream timeout\"}"),
        ),
      )
      client.embed(sampleInput("any text")) match {
        case Left(BeautyQEmbeddingRequestError.Timeout(body)) =>
          assert(body.contains("upstream timeout"))
        case other => fail(s"expected Timeout, got $other")
      }
    }
  }

  private val sampleModel: QdrantEmbeddingModelIdentity =
    QdrantEmbeddingModelIdentity(
      provider = "llama.cpp",
      model = "qwen-embed",
      revision = "v1",
      dimension = 3,
      textFormatVersion = "v1",
    )

  private def sampleInput(text: String): QdrantEmbeddingInput =
    QdrantEmbeddingInput
      .from(QdrantEmbeddingPurpose.CandidateQuery, "search-gen2-cutover", text, sampleModel)
      .getOrElse(fail("expected input"))

  private def matchingEmbeddingJson(values: Seq[Double]): Json =
    Json.obj(
      "model" -> Json.fromString("qwen-embed"),
      "data" -> Json.arr(Json.obj("embedding" -> Json.fromValues(values.map(v => Json.fromDoubleOrNull(v))))),
    )

  private final case class Response(
    status: Int = 200,
    body: Json = matchingEmbeddingJson(Seq(0.1, 0.2, 0.3)),
    transportError: Option[Gen2HttpTransportError] = None,
    captureRequest: Option[CapturedRequest => Unit] = None,
  )

  private final case class CapturedRequest(path: String, body: Json)

  private def scriptedClient(response: Response): BeautyQGen2EmbeddingClient = {
    val script = new Gen2JsonHttpClient {
      def putJson(
        path: String,
        body: Json,
        query: Vector[Gen2HttpQueryParameter],
        headers: Vector[Gen2HttpHeader],
      ): Either[Gen2HttpTransportError, Json] = fail("must not be called")

      def post(
        path: String,
        query: Vector[Gen2HttpQueryParameter],
        headers: Vector[Gen2HttpHeader],
      ): Either[Gen2HttpTransportError, Json] = fail("must not be called")

      def postJson(
        path: String,
        body: Json,
        query: Vector[Gen2HttpQueryParameter],
        headers: Vector[Gen2HttpHeader],
      ): Either[Gen2HttpTransportError, Json] = {
        response.captureRequest.foreach(_(CapturedRequest(path, body)))
        response.transportError match {
          case Some(error) => Left(error)
          case None if response.status < 200 || response.status >= 300 =>
            Left(Gen2HttpTransportError.HttpFailure("POST", path, response.status, response.body.noSpaces))
          case None => Right(response.body)
        }
      }

      def postNdjson(
        path: String,
        body: String,
        query: Vector[Gen2HttpQueryParameter],
        headers: Vector[Gen2HttpHeader],
      ): Either[Gen2HttpTransportError, Json] = fail("must not be called")

      def getJson(
        path: String,
        query: Vector[Gen2HttpQueryParameter],
        headers: Vector[Gen2HttpHeader],
      ): Either[Gen2HttpTransportError, Json] = fail("must not be called")

      def delete(
        path: String,
        query: Vector[Gen2HttpQueryParameter],
        headers: Vector[Gen2HttpHeader],
      ): Either[Gen2HttpTransportError, Unit] = fail("must not be called")
    }
    BeautyQGen2EmbeddingClient.fromTransport(script, "/v1/embeddings")
  }
}
