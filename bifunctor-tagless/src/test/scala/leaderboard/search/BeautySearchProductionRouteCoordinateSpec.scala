package leaderboard.search

import cats.syntax.all.*
import cats.effect.Async
import com.sun.net.httpserver.{HttpExchange, HttpServer}
import distage.Injector
import fs2.text
import io.circe.parser.parse
import izumi.distage.model.definition.{Activation, LocatorPrivacy}
import izumi.distage.model.plan.Roots
import leaderboard.api.{BeautySearchApi, HttpApi}
import leaderboard.config.ElasticsearchPortCfg
import leaderboard.http.tapir.{BeautySearchTapirEndpoints, TapirHttpSupport}
import leaderboard.plugins.BeautySearchRouteModules
import leaderboard.{HttpContractTestSupport, ObservedResponse}
import org.http4s.{HttpApp, Request, Status}
import org.scalatest.wordspec.AnyWordSpec
import zio.interop.catz.*
import zio.{IO, Runtime, Task, Unsafe, ZIO}

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import scala.io.Source
import scala.util.Using

final class BeautySearchProductionRouteCoordinateSpec extends AnyWordSpec with HttpContractTestSupport {
  "POST /beauty-search coordinate inputs" should {
    "return current behavior for normal Hamburg coordinates" in {
      withEsServer { port =>
        val probe = buildProbe(port)
        val apis  = probe.allHttpApis

        assert(apis.collect { case api: BeautySearchApi[IO] => api }.size == 1)

        val response = runIO(
          observeRoute(
            apis,
            postJson("/beauty-search", """{"query":"","userLat":53.57532,"userLon":10.07672,"limit":3}"""),
          )
        )

        assert(response.status == Status.Ok)

        val json = parse(response.body).getOrElse(fail(s"invalid Beauty search response JSON: ${response.body}"))
        val carousel = json.hcursor.downField("variantCarousel").focus.getOrElse(fail("missing variantCarousel"))
        assert(carousel.asArray.exists(_.isEmpty), "ES zero-hit: carousel should be empty"): Unit
      }
    }

    "return current behavior for latitude too high (999.0)" in {
      withEsServer { port =>
        val probe = buildProbe(port)
        val apis  = probe.allHttpApis

        val response = runIO(
          observeRoute(
            apis,
            postJson("/beauty-search", """{"query":"","userLat":999.0,"userLon":10.07672,"limit":3}"""),
          )
        )

        assert(response.status == Status.Ok)

        val json = parse(response.body).getOrElse(fail(s"invalid Beauty search response JSON: ${response.body}"))
        val carousel = json.hcursor.downField("variantCarousel").focus.getOrElse(fail("missing variantCarousel"))
        assert(carousel.asArray.exists(_.isEmpty)): Unit
      }
    }

    "return current behavior for longitude too high (999.0)" in {
      withEsServer { port =>
        val probe = buildProbe(port)
        val apis  = probe.allHttpApis

        val response = runIO(
          observeRoute(
            apis,
            postJson("/beauty-search", """{"query":"","userLat":53.57532,"userLon":999.0,"limit":3}"""),
          )
        )

        assert(response.status == Status.Ok)

        val json = parse(response.body).getOrElse(fail(s"invalid Beauty search response JSON: ${response.body}"))
        val carousel = json.hcursor.downField("variantCarousel").focus.getOrElse(fail("missing variantCarousel"))
        assert(carousel.asArray.exists(_.isEmpty)): Unit
      }
    }

    "return current behavior for very large finite coordinates (1e9, -1e9)" in {
      withEsServer { port =>
        val probe = buildProbe(port)
        val apis  = probe.allHttpApis

        val response = runIO(
          observeRoute(
            apis,
            postJson("/beauty-search", """{"query":"","userLat":1.0e9,"userLon":-1.0e9,"limit":3}"""),
          )
        )

        assert(response.status == Status.Ok)

        val json = parse(response.body).getOrElse(fail(s"invalid Beauty search response JSON: ${response.body}"))
        val carousel = json.hcursor.downField("variantCarousel").focus.getOrElse(fail("missing variantCarousel"))
        assert(carousel.asArray.exists(_.isEmpty)): Unit
      }
    }
  }

  private def withEsServer(f: Int => Unit): Unit = {
    val server = HttpServer.create(new InetSocketAddress(0), 0)
    try {
      server.createContext(
        "/",
        (exchange: HttpExchange) => {
          val path = exchange.getRequestURI.getPath
          val body = Using.resource(exchange.getRequestBody)(in => Source.fromInputStream(in, "UTF-8").mkString)
          val _    = body
          val responseBody =
            if (path.endsWith("_search")) """{"hits":{"hits":[]}}"""
            else """{"acknowledged":true}"""
          val bytes = responseBody.getBytes(StandardCharsets.UTF_8)
          exchange.sendResponseHeaders(200, bytes.length)
          Using.resource(exchange.getResponseBody)(_.write(bytes))
        }: Unit
      )
      server.start()
      f(server.getAddress.getPort)
    } finally {
      server.stop(0)
    }
  }

  private def buildProbe(port: Int): BeautySearchProductionRouteCoordinateProbe = {
    val module = new distage.ModuleDef {
      include(BeautySearchRouteModules.apiElasticsearch)
      make[ElasticsearchPortCfg].fromValue(ElasticsearchPortCfg("localhost", port))
      make[TapirHttpSupport[IO]].from(new TapirHttpSupport[IO])
      make[BeautySearchTapirEndpoints].fromValue(BeautySearchTapirEndpoints)
      make[Async[Task]].fromValue(Async[Task])
      make[BeautySearchProductionRouteCoordinateProbe].from {
        (
          beautySearchApi: BeautySearchApi[IO],
          allHttpApis: Set[HttpApi[IO]],
        ) =>
          BeautySearchProductionRouteCoordinateProbe(beautySearchApi, allHttpApis)
      }
    }

    val locator = Injector().produce(
      bindings = module,
      roots = Roots.target[BeautySearchProductionRouteCoordinateProbe],
      activation = Activation.empty,
      locatorPrivacy = LocatorPrivacy.PublicByDefault,
    ).unsafeGet()

    locator.get[BeautySearchProductionRouteCoordinateProbe]
  }

  private def observeRoute(
    apis: Set[HttpApi[IO]],
    request: Request[Task],
  ): Task[ObservedResponse] = {
    val app: HttpApp[Task] = apis.map(_.http).toList.foldK.orNotFound

    app.run(request).flatMap {
      response =>
        response.body
          .through(text.utf8.decode)
          .compile
          .string
          .map(body => ObservedResponse(response.status, body))
    }
  }

  private final case class BeautySearchProductionRouteCoordinateProbe(
    beautySearchApi: BeautySearchApi[IO],
    allHttpApis: Set[HttpApi[IO]],
  )

  private def runIO[E, A](effect: ZIO[Any, E, A]): A =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
    }
}
