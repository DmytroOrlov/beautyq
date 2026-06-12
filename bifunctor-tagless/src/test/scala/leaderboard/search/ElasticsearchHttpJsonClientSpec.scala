package leaderboard.search

import com.sun.net.httpserver.{HttpExchange, HttpServer}
import distage.{Injector, ModuleDef}
import izumi.distage.model.definition.{Activation, LocatorPrivacy}
import izumi.distage.model.plan.Roots
import io.circe.Json
import io.circe.parser.parse
import leaderboard.config.ElasticsearchPortCfg
import leaderboard.model.QueryFailure
import leaderboard.plugins.ElasticsearchClientModules
import leaderboard.search.elasticsearch.{ElasticsearchHttpJsonClient, ElasticsearchJsonClient}
import org.scalatest.wordspec.AnyWordSpec
import zio.{IO, Runtime, Unsafe}

import java.net.InetSocketAddress
import scala.io.Source
import scala.util.Using

final class ElasticsearchHttpJsonClientSpec extends AnyWordSpec {

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

  private def withServer(handler: HttpExchange => Unit)(f: HttpServer => Unit): Unit = {
    val server = HttpServer.create(new InetSocketAddress(0), 0)
    try {
      server.createContext("/", (exchange: HttpExchange) => handler(exchange))
      server.start()
      f(server)
    } finally {
      server.stop(0)
    }
  }

  private def readBody(exchange: HttpExchange): String =
    Using.resource(exchange.getRequestBody) { in =>
      val source = Source.fromInputStream(in, "UTF-8")
      try source.mkString
      finally source.close()
    }

  private def respond(exchange: HttpExchange, status: Int, body: String): Unit = {
    val bytes = body.getBytes("UTF-8")
    exchange.sendResponseHeaders(status, bytes.length)
    Using.resource(exchange.getResponseBody) { out =>
      out.write(bytes)
    }
  }

  "ElasticsearchHttpJsonClient" should {
    "send JSON and NDJSON requests with expected method, path, content type, and body" in {
      withServer { exchange =>
        val method        = exchange.getRequestMethod
        val path          = exchange.getRequestURI.getPath
        val contentType   = Option(exchange.getRequestHeaders.getFirst("Content-Type")).getOrElse("")
        val body          = readBody(exchange)
        val okBody        = """{"ok":true}"""
        val okBytes       = okBody.getBytes("UTF-8")

        path match {
          case "/index" if method == "PUT" =>
            assert(method == "PUT")
            assert(contentType == "application/json")
            val parsed = parse(body) match {
              case Right(j) => j
              case Left(e)  => fail(s"expected Right, got: $e")
            }
            assert(parsed.hcursor.get[Boolean]("flag").toOption.contains(true))
            exchange.sendResponseHeaders(200, okBytes.length)
            Using.resource(exchange.getResponseBody)(_.write(okBytes))

          case "/index/_search" if method == "POST" =>
            assert(method == "POST")
            assert(contentType == "application/json")
            val parsed = parse(body) match {
              case Right(j) => j
              case Left(e)  => fail(s"expected Right, got: $e")
            }
            assert(parsed.hcursor.get[String]("query").toOption.contains("test"))
            exchange.sendResponseHeaders(200, okBytes.length)
            Using.resource(exchange.getResponseBody)(_.write(okBytes))

          case "/index/_bulk" if method == "POST" =>
            assert(method == "POST")
            assert(contentType == "application/x-ndjson")
            assert(body.contains("\"index\""))
            exchange.sendResponseHeaders(200, okBytes.length)
            Using.resource(exchange.getResponseBody)(_.write(okBytes))

          case "/index" if method == "GET" =>
            assert(method == "GET")
            exchange.sendResponseHeaders(200, okBytes.length)
            Using.resource(exchange.getResponseBody)(_.write(okBytes))

          case "/index/_refresh" if method == "POST" =>
            assert(method == "POST")
            assert(body.isEmpty, s"expected empty body for refresh, got: '$body'")
            exchange.sendResponseHeaders(200, okBytes.length)
            Using.resource(exchange.getResponseBody)(_.write(okBytes))

          case "/index" if method == "DELETE" =>
            assert(method == "DELETE")
            exchange.sendResponseHeaders(200, okBytes.length)
            Using.resource(exchange.getResponseBody)(_.write(okBytes))

          case _ =>
            exchange.sendResponseHeaders(404, 0)
            exchange.close()
        }
      } { server =>
        val port   = server.getAddress.getPort
        val client = new ElasticsearchHttpJsonClient("localhost", port)

        val putResult = run(client.putJson("/index", Json.obj("flag" -> Json.fromBoolean(true))))
        assert(putResult == Json.obj("ok" -> Json.fromBoolean(true)))

        val postResult = run(client.postJson("/index/_search", Json.obj("query" -> Json.fromString("test"))))
        assert(postResult == Json.obj("ok" -> Json.fromBoolean(true)))

        val bulkPayload = """{"index":{"_index":"index"}}{"field":"value"}"""
        val ndjsonResult = run(client.postNdjson("/index/_bulk", bulkPayload))
        assert(ndjsonResult == Json.obj("ok" -> Json.fromBoolean(true)))

        val refreshResult = run(client.post("/index/_refresh"))
        assert(refreshResult == Json.obj("ok" -> Json.fromBoolean(true)))

        val getResult = run(client.getJson("/index"))
        assert(getResult == Json.obj("ok" -> Json.fromBoolean(true)))

        val deleteResult = run(client.delete("/index"))
        assert(deleteResult == ()): Unit
      }
    }

    "fail with elasticsearch-json-client operation name on non-2xx status" in {
      withServer { exchange =>
        respond(exchange, 500, "boom")
      } { server =>
        val port   = server.getAddress.getPort
        val client = new ElasticsearchHttpJsonClient("localhost", port)

        val failure = runFail(client.getJson("/index"))
        failure match {
          case QueryFailure.OperationFailure(operationName, message) =>
            assert(operationName == ElasticsearchJsonClient.OperationName)
            assert(message.contains("Unexpected status 500"))
            assert(message.contains("boom")): Unit
          case other =>
            fail(s"Expected OperationFailure, got $other")
        }
      }
    }

    "fail with elasticsearch-json-client operation name on invalid JSON response" in {
      withServer { exchange =>
        respond(exchange, 200, "not-json")
      } { server =>
        val port   = server.getAddress.getPort
        val client = new ElasticsearchHttpJsonClient("localhost", port)

        val failure = runFail(client.getJson("/index"))
        failure match {
          case QueryFailure.OperationFailure(operationName, _) =>
            assert(operationName == ElasticsearchJsonClient.OperationName): Unit
          case other =>
            fail(s"Expected OperationFailure, got $other")
        }
      }
    }

    "bind ElasticsearchJsonClient from ElasticsearchPortCfg through ElasticsearchClientModules" in {
      val module = new ModuleDef {
        include(ElasticsearchClientModules.portConfigured)
        make[ElasticsearchPortCfg].fromValue(ElasticsearchPortCfg("localhost", 9200))
      }

      val locator = Injector().produce(
        bindings = module,
        roots = Roots.target[ElasticsearchJsonClient],
        activation = Activation.empty,
        locatorPrivacy = LocatorPrivacy.PublicByDefault,
      ).unsafeGet()

      val client = locator.get[ElasticsearchJsonClient]
      assert(client.isInstanceOf[ElasticsearchHttpJsonClient])
    }
  }
}
