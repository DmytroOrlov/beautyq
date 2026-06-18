package leaderboard.search

import cats.effect.Async
import cats.syntax.all.*
import com.sun.net.httpserver.{HttpExchange, HttpServer}
import distage.Injector
import fs2.text
import io.circe.Json
import io.circe.parser.parse
import izumi.distage.model.definition.{Activation, LocatorPrivacy}
import izumi.distage.model.plan.Roots
import leaderboard.api.{BeautySearchApi, HttpApi}
import leaderboard.config.ElasticsearchPortCfg
import leaderboard.http.tapir.{BeautySearchTapirEndpoints, TapirHttpSupport}
import leaderboard.plugins.{BeautySearchRouteModules, LeaderboardPlugin}
import leaderboard.{HttpContractTestSupport, ObservedResponse}
import org.http4s.{HttpApp, Request, Status}
import org.scalatest.Assertions.fail
import zio.interop.catz.*
import zio.{IO, Runtime, Task, Unsafe, ZIO}

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import scala.io.Source
import scala.util.Using

trait BeautySearchProductionRouteSpecSupport extends HttpContractTestSupport {
  protected final case class BeautySearchProductionRouteProbe(
    beautySearchApi: BeautySearchApi[IO],
    allHttpApis: Set[HttpApi[IO]],
  )

  protected final def withZeroHitEsServer(f: Int => Unit): Unit = {
    val server = HttpServer.create(new InetSocketAddress(0), 0)
    try {
      server.createContext(
        "/",
        (exchange: HttpExchange) => {
          val path = exchange.getRequestURI.getPath
          Using.resource(exchange.getRequestBody)(in => Source.fromInputStream(in, "UTF-8").foreach(_ => ()))
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

  protected final def buildProductionApiGraphRouteProbe(port: Int): BeautySearchProductionRouteProbe = {
    val module = new distage.ModuleDef {
      include(LeaderboardPlugin.modules.apiBase[IO])
      include(BeautySearchRouteModules.apiElasticsearch)
      make[ElasticsearchPortCfg].fromValue(ElasticsearchPortCfg("localhost", port))
      make[Async[Task]].fromValue(Async[Task])
      make[BeautySearchProductionRouteProbe].from {
        (
          beautySearchApi: BeautySearchApi[IO],
          allHttpApis: Set[HttpApi[IO]],
        ) =>
          BeautySearchProductionRouteProbe(beautySearchApi, allHttpApis)
      }
    }

    val locator = Injector().produce(
      bindings = module,
      roots = Roots.target[BeautySearchProductionRouteProbe],
      activation = Activation.empty,
      locatorPrivacy = LocatorPrivacy.PublicByDefault,
    ).unsafeGet()

    locator.get[BeautySearchProductionRouteProbe]
  }

  protected final def buildTargetedEsRouteProbe(port: Int): BeautySearchProductionRouteProbe = {
    val module = new distage.ModuleDef {
      include(BeautySearchRouteModules.apiElasticsearch)
      make[ElasticsearchPortCfg].fromValue(ElasticsearchPortCfg("localhost", port))
      make[TapirHttpSupport[IO]].from(new TapirHttpSupport[IO])
      make[BeautySearchTapirEndpoints].fromValue(BeautySearchTapirEndpoints)
      make[Async[Task]].fromValue(Async[Task])
      make[BeautySearchProductionRouteProbe].from {
        (
          beautySearchApi: BeautySearchApi[IO],
          allHttpApis: Set[HttpApi[IO]],
        ) =>
          BeautySearchProductionRouteProbe(beautySearchApi, allHttpApis)
      }
    }

    val locator = Injector().produce(
      bindings = module,
      roots = Roots.target[BeautySearchProductionRouteProbe],
      activation = Activation.empty,
      locatorPrivacy = LocatorPrivacy.PublicByDefault,
    ).unsafeGet()

    locator.get[BeautySearchProductionRouteProbe]
  }

  protected final def observeRoute(
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

  protected final def parseResponseJson(response: ObservedResponse): Json =
    parse(response.body) match {
      case Right(json) =>
        json
      case Left(error) =>
        fail(s"Invalid Beauty search response JSON: ${error.getMessage}; body: ${response.body}")
    }

  protected final def assertOkWithEmptyVariantCarousel(response: ObservedResponse): Unit = {
    assert(response.status == Status.Ok, s"Expected 200 OK, got ${response.status}")
    val json = parseResponseJson(response)
    assertEmptyArrayField(json, "variantCarousel")
    (): Unit
  }

  protected final def assertOkWithEmptyBeautySearchResponseShape(response: ObservedResponse): Unit = {
    assert(response.status == Status.Ok, s"Expected 200 OK, got ${response.status}")
    val json = parseResponseJson(response)
    assertEmptyArrayField(json, "variantCarousel")
    assertEmptyArrayField(json, "providerCarousel")
    assertEmptyArrayField(json, "serviceIntentCarousel")
    assertFieldPresent(json, "facets")
    assertFieldPresent(json, "inferredFilters")
    (): Unit
  }

  protected final def assertDefaultBadRequest(response: ObservedResponse): Unit = {
    assert(
      response.status == Status.BadRequest,
      s"Expected 400 Bad Request, got ${response.status}",
    )
    (): Unit
  }

  private def assertEmptyArrayField(json: Json, fieldName: String): Unit =
    json.hcursor.downField(fieldName).focus match {
      case Some(value) =>
        value.asArray match {
          case Some(values) =>
            assert(values.isEmpty, s"Expected $fieldName to be empty, got: $value")
            (): Unit
          case None =>
            fail(s"Expected $fieldName to be an array, got: $value")
        }
      case None =>
        fail(s"Missing $fieldName in Beauty search response: $json")
    }

  private def assertFieldPresent(json: Json, fieldName: String): Unit =
    json.hcursor.downField(fieldName).focus match {
      case Some(_) =>
        (): Unit
      case None =>
        fail(s"Missing $fieldName in Beauty search response: $json")
    }

  protected final def runIO[E, A](effect: ZIO[Any, E, A]): A =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
    }
}
