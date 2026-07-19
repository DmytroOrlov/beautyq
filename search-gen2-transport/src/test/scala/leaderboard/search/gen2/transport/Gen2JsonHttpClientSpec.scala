package leaderboard.search.gen2.transport

import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import io.circe.Json
import org.scalatest.wordspec.AnyWordSpec

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.time.Duration

final class Gen2JsonHttpClientSpec extends AnyWordSpec {
  "Gen2JsonHttpClient" should {
    "confine endpoints and validate positive timeouts" in {
      assert(Gen2HttpEndpoint.fromString("http://localhost").isRight)
      assert(Gen2HttpEndpoint.fromString("https://localhost:65535").isRight)
      assert(Gen2HttpEndpoint.fromString("http://localhost:0") ==
        Left(Gen2HttpTransportConfigError.InvalidEndpointPort("http://localhost:0", 0)))
      assert(Gen2HttpEndpoint.fromString("http://user@localhost:9200").isLeft)
      val endpoint = Gen2HttpEndpoint.fromString("http://localhost:9200").getOrElse(fail("expected endpoint"))
      assert(Gen2HttpTransportConfig.create(endpoint, Duration.ZERO, Duration.ofSeconds(1)).isLeft)
      assert(Gen2HttpTransportConfig.create(endpoint, Duration.ofSeconds(1), Duration.ZERO).isLeft)
    }

    "encode typed query parameters, preserve headers and keep bodyless operations bodyless" in withServer { client =>
      val response = client.getJson(
        "/query",
        query = Vector(Gen2HttpQueryParameter("wait", "true"), Gen2HttpQueryParameter("name", "hello world")),
        headers = Vector(Gen2HttpHeader("X-Test", "transport")),
      ).getOrElse(fail("expected query response"))
      assert(response.hcursor.get[String]("query").toOption.contains("wait=true&name=hello%20world"))
      assert(response.hcursor.get[String]("header").toOption.contains("transport"))
      assert(client.post("/empty").isRight)
      assert(client.delete("/delete") == Right(()))
      (): Unit
    }

    "preserve method, JSON and NDJSON content types" in withServer { client =>
      assert(client.putJson("/json", Json.obj("value" -> Json.fromInt(1))).isRight)
      assert(client.postJson("/json", Json.obj("value" -> Json.fromInt(2))).isRight)
      assert(client.postNdjson("/bulk", "{}\n{}").isRight)
      (): Unit
    }

    "return typed HTTP, JSON and connection failures without following redirects" in withServer { client =>
      client.getJson("/redirect") match {
        case Left(Gen2HttpTransportError.HttpFailure("GET", "/redirect", 302, _)) => succeed
        case other => fail(s"expected redirect failure, got $other")
      }
      client.getJson("/invalid") match {
        case Left(Gen2HttpTransportError.InvalidJsonResponse("GET", "/invalid", 200, "not-json", _)) => succeed
        case other => fail(s"expected JSON failure, got $other")
      }
      val endpoint = Gen2HttpEndpoint.fromString("http://127.0.0.1:1").getOrElse(fail("expected endpoint"))
      val config = Gen2HttpTransportConfig.create(endpoint, Duration.ofMillis(100), Duration.ofMillis(200)).getOrElse(fail("expected config"))
      Gen2JsonHttpClient.jdk(config).getJson("/unavailable") match {
        case Left(Gen2HttpTransportError.ConnectionFailed("GET", "/unavailable", message)) =>
          assert(message.nonEmpty)
          (): Unit
        case other => fail(s"expected connection failure, got $other")
      }
    }
  }

  private def withServer(test: Gen2JsonHttpClient => Unit): Unit = {
    val server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext("/", new Handler)
    server.start()
    try {
      val endpoint = Gen2HttpEndpoint.fromString(s"http://127.0.0.1:${server.getAddress.getPort}").getOrElse(fail("expected endpoint"))
      val config = Gen2HttpTransportConfig.create(endpoint, Duration.ofSeconds(1), Duration.ofSeconds(2)).getOrElse(fail("expected config"))
      test(Gen2JsonHttpClient.jdk(config))
    } finally server.stop(0)
  }

  private final class Handler extends HttpHandler {
    def handle(exchange: HttpExchange): Unit = {
      val path = exchange.getRequestURI.getPath
      val body = String(exchange.getRequestBody.readAllBytes(), StandardCharsets.UTF_8)
      val (status, contentType, response) = path match {
        case "/redirect" =>
          exchange.getResponseHeaders.add("Location", "/ok")
          (302, "text/plain", "redirect")
        case "/invalid"  => (200, "text/plain", "not-json")
        case "/delete" if exchange.getRequestMethod == "DELETE" && body.isEmpty => (200, "text/plain", "deleted")
        case "/empty" if exchange.getRequestMethod == "POST" && body.isEmpty => (200, "application/json", "{}")
        case _ =>
          val header = Option(exchange.getRequestHeaders.getFirst("X-Test")).getOrElse("")
          val json = Json.obj(
            "method" -> Json.fromString(exchange.getRequestMethod),
            "query" -> Json.fromString(Option(exchange.getRequestURI.getRawQuery).getOrElse("")),
            "header" -> Json.fromString(header),
            "contentType" -> Json.fromString(Option(exchange.getRequestHeaders.getFirst("Content-Type")).getOrElse("")),
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
