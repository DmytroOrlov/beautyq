package leaderboard.search.gen2.elasticsearch

import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import io.circe.Json
import org.scalatest.wordspec.AnyWordSpec

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.time.Duration

final class ElasticsearchGen2JsonClientSpec extends AnyWordSpec {
  "ElasticsearchGen2JsonClient" should {
    "validate endpoint ports and timeouts before transport" in {
      assert(ElasticsearchGen2Endpoint.fromString("http://localhost").isRight)
      assert(ElasticsearchGen2Endpoint.fromString("http://localhost:1").isRight)
      assert(ElasticsearchGen2Endpoint.fromString("http://localhost:65535").isRight)
      assert(ElasticsearchGen2Endpoint.fromString("http://localhost:0") ==
        Left(ElasticsearchGen2TransportConfigError.InvalidEndpointPort("http://localhost:0", 0)))
      assert(ElasticsearchGen2Endpoint.fromString("http://localhost:65536") ==
        Left(ElasticsearchGen2TransportConfigError.InvalidEndpointPort("http://localhost:65536", 65536)))
      assert(ElasticsearchGen2Endpoint.fromString("http://localhost:99999") ==
        Left(ElasticsearchGen2TransportConfigError.InvalidEndpointPort("http://localhost:99999", 99999)))
      assert(ElasticsearchGen2Endpoint.fromString("http://user@localhost:9200").isLeft)
      val endpoint = ElasticsearchGen2Endpoint.fromString("http://localhost:9200").getOrElse(fail("expected endpoint"))
      assert(ElasticsearchGen2TransportConfig.create(endpoint, Duration.ZERO, Duration.ofSeconds(1)).isLeft)
    }

    "reject every non-confined request path with exact method and path" in {
      val client = clientForPort(9200)
      Vector(
        "https://other-host/index",
        "//other-host/index",
        "relative/path",
        "/index?x=1",
        "/index#fragment",
        "/../index",
      ).foreach { path =>
        client.getJson(path) match {
          case Left(ElasticsearchGen2TransportError.InvalidRequestPath("GET", actual, _)) => assert(actual == path)
          case other => fail(s"expected confined GET path error for '$path', got $other")
        }
      }
    }

    "preserve exact method, path, body and content type for every operation" in withServer { client =>
      assertEcho(client.putJson("/put", Json.obj("value" -> Json.fromInt(1))), "PUT", "/put", "application/json", "{\"value\":1}")
      assertEcho(client.postJson("/post-json", Json.obj("value" -> Json.fromInt(2))), "POST", "/post-json", "application/json", "{\"value\":2}")
      assertEcho(client.post("/post-empty"), "POST", "/post-empty", "", "")
      assertEcho(client.postNdjson("/bulk", "{}\n{}\n"), "POST", "/bulk", "application/x-ndjson", "{}\n{}\n")
      assertEcho(client.getJson("/get"), "GET", "/get", "", "")
      assert(client.delete("/delete") == Right(()))
      (): Unit
    }

    "return a contextual typed error for a real connection failure" in {
      val server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
      server.start()
      val client = clientForPort(server.getAddress.getPort)
      server.stop(0)

      client.getJson("/unavailable") match {
        case Left(ElasticsearchGen2TransportError.ConnectionFailed("GET", "/unavailable", message)) => assert(message.nonEmpty)
        case other => fail(s"expected contextual connection failure, got $other")
      }
    }

    "not follow redirects and preserve non-2xx and invalid-JSON context" in withServer { client =>
      client.getJson("/redirect") match {
        case Left(ElasticsearchGen2TransportError.HttpFailure("GET", "/redirect", 302, _)) => succeed
        case other => fail(s"expected unfollowed redirect, got $other")
      }
      client.postJson("/failure", Json.obj()) match {
        case Left(ElasticsearchGen2TransportError.HttpFailure("POST", "/failure", 409, "conflict")) => succeed
        case other => fail(s"expected contextual HTTP failure, got $other")
      }
      client.getJson("/invalid-json") match {
        case Left(ElasticsearchGen2TransportError.InvalidJsonResponse("GET", "/invalid-json", 200, "not-json", _)) => (): Unit
        case other => fail(s"expected contextual JSON failure, got $other")
      }
      (): Unit
    }
  }

  private def assertEcho(
    result: Either[ElasticsearchGen2TransportError, Json],
    method: String,
    path: String,
    contentType: String,
    body: String,
  ): Unit = {
    val json = result.getOrElse(fail(s"expected echoed response, got $result"))
    assert(json.hcursor.get[String]("method").toOption.contains(method))
    assert(json.hcursor.get[String]("path").toOption.contains(path))
    assert(json.hcursor.get[String]("contentType").toOption.contains(contentType))
    assert(json.hcursor.get[String]("body").toOption.contains(body))
    (): Unit
  }

  private def withServer(test: ElasticsearchGen2JsonClient => Unit): Unit = {
    val server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext("/", new EchoHandler)
    server.start()
    try test(clientForPort(server.getAddress.getPort))
    finally server.stop(0)
  }

  private def clientForPort(port: Int): ElasticsearchGen2JsonClient = {
    val endpoint = ElasticsearchGen2Endpoint.fromString(s"http://127.0.0.1:$port").getOrElse(fail("expected endpoint"))
    val config = ElasticsearchGen2TransportConfig.create(endpoint, Duration.ofSeconds(1), Duration.ofSeconds(2)).getOrElse(fail("expected config"))
    ElasticsearchGen2JsonClient.jdk(config)
  }

  private final class EchoHandler extends HttpHandler {
    def handle(exchange: HttpExchange): Unit = {
      val path = exchange.getRequestURI.getPath
      val body = String(exchange.getRequestBody.readAllBytes(), StandardCharsets.UTF_8)
      val (status, contentType, response) = path match {
        case "/redirect" =>
          exchange.getResponseHeaders.add("Location", "/get")
          (302, "text/plain", "redirect")
        case "/failure" => (409, "text/plain", "conflict")
        case "/invalid-json" => (200, "text/plain", "not-json")
        case "/delete" if exchange.getRequestMethod == "DELETE" && body.isEmpty => (200, "text/plain", "deleted")
        case "/delete" => (405, "text/plain", "expected bodyless DELETE")
        case _ =>
          val requestContentType = exchange.getRequestHeaders.getFirst("Content-Type") match {
            case null => ""
            case value => value
          }
          val json = Json.obj(
            "method" -> Json.fromString(exchange.getRequestMethod),
            "path" -> Json.fromString(path),
            "contentType" -> Json.fromString(requestContentType),
            "body" -> Json.fromString(body),
          ).noSpaces
          (200, "application/json", json)
      }
      val bytes = response.getBytes(StandardCharsets.UTF_8)
      exchange.getResponseHeaders.add("Content-Type", contentType)
      exchange.sendResponseHeaders(status, bytes.length.toLong)
      exchange.getResponseBody.write(bytes)
      exchange.close()
      (): Unit
    }
  }
}
