package leaderboard.search.gen2.qdrant

import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import io.circe.{Json, parser}
import leaderboard.search.gen2.transport.*
import org.scalatest.wordspec.AnyWordSpec

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.time.Duration

final class QdrantGen2ClientSpec extends AnyWordSpec {
  "QdrantGen2Client" should {
    "validate one safe resource path segment" in {
      assert(QdrantResourceName.from("beautyq_variants_abc-123").isRight)
      assert(QdrantResourceName.from("bad/name").isLeft)
      assert(QdrantResourceName.from("../collection").isLeft)
      assert(QdrantApiKey.from(" ").isLeft)
    }

    "own exact 1.18.3 paths, bodies, wait parameters and API-key headers" in withServer { client =>
      val collection = QdrantResourceName.from("collection-1").getOrElse(fail("expected collection"))
      val body = Json.obj("vectors" -> Json.obj("size" -> Json.fromInt(3)))

      val collectionResponse = client.getCollection(collection).getOrElse(fail("expected collection response"))
      assert(collectionResponse.hcursor.get[String]("path").toOption.contains("/collections/collection-1"))
      assert(collectionResponse.hcursor.get[String]("apiKey").toOption.contains("test-key"))
      assert(client.listAliases().exists(_.hcursor.get[String]("path").toOption.contains("/aliases")))
      assert(client.createCollection(collection, body).exists(_.hcursor.get[String]("method").toOption.contains("PUT")))
      val index = client.createPayloadIndex(collection, Json.obj("field_name" -> Json.fromString("code"))).getOrElse(fail("expected index response"))
      assert(index.hcursor.get[String]("query").toOption.contains("wait=true"))
      val upsert = client.upsertPoints(collection, Json.obj("points" -> Json.arr())).getOrElse(fail("expected upsert response"))
      assert(upsert.hcursor.get[String]("path").toOption.contains("/collections/collection-1/points"))
      val count = client.countPoints(collection).getOrElse(fail("expected count response"))
      val countBody = count.hcursor.get[String]("body").flatMap(parser.parse)
      assert(countBody.flatMap(_.hcursor.get[Boolean]("exact")) == Right(true))
      val aliases = client.updateAliases(Json.obj("actions" -> Json.arr())).getOrElse(fail("expected aliases response"))
      assert(aliases.hcursor.get[String]("path").toOption.contains("/collections/aliases"))
      val query = client.queryPoints(collection, Json.obj("query" -> Json.arr())).getOrElse(fail("expected query response"))
      assert(query.hcursor.get[String]("path").toOption.contains("/collections/collection-1/points/query"))
      (): Unit
    }

    "not expose the API key in a transport error" in {
      val endpoint = Gen2HttpEndpoint.fromString("http://127.0.0.1:1").getOrElse(fail("expected endpoint"))
      val config = Gen2HttpTransportConfig.create(endpoint, Duration.ofMillis(100), Duration.ofMillis(200)).getOrElse(fail("expected config"))
      val key = QdrantApiKey.from("secret-key").getOrElse(fail("expected key"))
      QdrantGen2Client.jdk(config, Some(key)).listAliases() match {
        case Left(error) => assert(!error.toString.contains("secret-key"))
        case Right(value) => fail(s"expected transport failure, got $value")
      }
    }
  }

  private def withServer(test: QdrantGen2Client => Unit): Unit = {
    val server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext("/", new Handler)
    server.start()
    try {
      val endpoint = Gen2HttpEndpoint.fromString(s"http://127.0.0.1:${server.getAddress.getPort}").getOrElse(fail("expected endpoint"))
      val config = Gen2HttpTransportConfig.create(endpoint, Duration.ofSeconds(1), Duration.ofSeconds(2)).getOrElse(fail("expected config"))
      val key = QdrantApiKey.from("test-key").getOrElse(fail("expected key"))
      test(QdrantGen2Client.jdk(config, Some(key)))
    } finally server.stop(0)
  }

  private final class Handler extends HttpHandler {
    def handle(exchange: HttpExchange): Unit = {
      val path = exchange.getRequestURI.getPath
      val body = String(exchange.getRequestBody.readAllBytes(), StandardCharsets.UTF_8)
      val json = Json.obj(
        "method" -> Json.fromString(exchange.getRequestMethod),
        "path" -> Json.fromString(path),
        "query" -> Json.fromString(Option(exchange.getRequestURI.getRawQuery).getOrElse("")),
        "apiKey" -> Json.fromString(Option(exchange.getRequestHeaders.getFirst("api-key")).getOrElse("")),
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
