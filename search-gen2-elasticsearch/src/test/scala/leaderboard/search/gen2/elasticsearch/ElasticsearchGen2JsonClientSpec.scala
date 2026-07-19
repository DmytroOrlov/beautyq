package leaderboard.search.gen2.elasticsearch

import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import io.circe.Json
import org.scalatest.wordspec.AnyWordSpec

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.time.Duration

/** The neutral transport owns the full HTTP matrix; this suite proves only the ES compatibility edge. */
final class ElasticsearchGen2JsonClientSpec extends AnyWordSpec {
  "ElasticsearchGen2JsonClient" should {
    "retain the existing endpoint/configuration surface" in {
      assert(ElasticsearchGen2Endpoint.fromString("http://localhost").isRight)
      assert(ElasticsearchGen2Endpoint.fromString("http://localhost:0") ==
        Left(ElasticsearchGen2TransportConfigError.InvalidEndpointPort("http://localhost:0", 0)))
      val endpoint = ElasticsearchGen2Endpoint.fromString("http://localhost:9200").getOrElse(fail("expected endpoint"))
      assert(ElasticsearchGen2TransportConfig.create(endpoint, Duration.ZERO, Duration.ofSeconds(1)).isLeft)
    }

    "delegate an Elasticsearch JSON operation to the neutral transport" in withServer { client =>
      client.putJson("/adapter", Json.obj("value" -> Json.fromInt(1))) match {
        case Right(json) =>
          assert(json.hcursor.get[String]("method").toOption.contains("PUT"))
          assert(json.hcursor.get[String]("path").toOption.contains("/adapter"))
          assert(json.hcursor.get[String]("body").toOption.contains("{\"value\":1}"))
          (): Unit
        case Left(error) => fail(s"expected adapter response, got $error")
      }
    }
  }

  private def withServer(test: ElasticsearchGen2JsonClient => Unit): Unit = {
    val server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext("/", new Handler)
    server.start()
    try {
      val endpoint = ElasticsearchGen2Endpoint.fromString(s"http://127.0.0.1:${server.getAddress.getPort}").getOrElse(fail("expected endpoint"))
      val config = ElasticsearchGen2TransportConfig.create(endpoint, Duration.ofSeconds(1), Duration.ofSeconds(2)).getOrElse(fail("expected config"))
      test(ElasticsearchGen2JsonClient.jdk(config))
    } finally server.stop(0)
  }

  private final class Handler extends HttpHandler {
    def handle(exchange: HttpExchange): Unit = {
      val body = String(exchange.getRequestBody.readAllBytes(), StandardCharsets.UTF_8)
      val json = Json.obj(
        "method" -> Json.fromString(exchange.getRequestMethod),
        "path" -> Json.fromString(exchange.getRequestURI.getPath),
        "body" -> Json.fromString(body),
      ).noSpaces
      val bytes = json.getBytes(StandardCharsets.UTF_8)
      exchange.getResponseHeaders.add("Content-Type", "application/json")
      exchange.sendResponseHeaders(200, bytes.length.toLong)
      exchange.getResponseBody.write(bytes)
      exchange.close()
      (): Unit
    }
  }
}
