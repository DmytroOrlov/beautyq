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
import leaderboard.http.tapir.BeautySearchTapirEndpoints
import leaderboard.plugins.BeautySearchRouteModules
import leaderboard.search.elasticsearch.{ElasticsearchProductionReadinessState, ElasticsearchSeedLifecycleMetadata, ElasticsearchStartupReadinessTransition}
import leaderboard.{HttpContractTestSupport, ObservedResponse}
import org.http4s.{HttpApp, Request, Status}
import org.scalatest.wordspec.AnyWordSpec
import zio.interop.catz.*
import zio.{IO, Runtime, Task, Unsafe, ZIO}

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import scala.io.Source
import scala.util.Using

final class BeautySearchElasticsearchDefaultReadyRouteSpec extends AnyWordSpec with HttpContractTestSupport {

  "BeautySearchRouteModules.seedCatalogElasticsearchPortConfigured" should {
    "serve POST /beauty-search with only ElasticsearchPortCfg supplied" in {
      val recordedRequests = scala.collection.mutable.ListBuffer.empty[RecordedEsRequest]
      val server           = HttpServer.create(new InetSocketAddress(0), 0)
      try {
        server.createContext(
          "/",
          (exchange: HttpExchange) => {
            val method      = exchange.getRequestMethod
            val path        = exchange.getRequestURI.getPath
            val contentType = Option(exchange.getRequestHeaders.getFirst("Content-Type"))
            val body        = Using.resource(exchange.getRequestBody)(in => Source.fromInputStream(in, "UTF-8").mkString)
            recordedRequests.synchronized {
              recordedRequests += RecordedEsRequest(method, path, contentType, body)
            }

            val responseBody =
              if (path.endsWith("_search")) """{"hits":{"hits":[]}}"""
              else """{"acknowledged":true}"""
            val bytes = responseBody.getBytes(StandardCharsets.UTF_8)
            exchange.sendResponseHeaders(200, bytes.length)
            Using.resource(exchange.getResponseBody)(_.write(bytes))
          }: Unit
        )
        server.start()

        val port  = server.getAddress.getPort
        val probe = buildProbe(port)
        val apis  = probe.allHttpApis

        assert(apis.collect { case _: BeautySearchApi[IO] => () }.size == 1)
        BeautySearchProductionRouteSpecSupport.assertOperatorVisibilityEndpointAbsent(apis)
        BeautySearchProductionRouteSpecSupport.assertSeedOnlyLifecycleMetadata(probe.lifecycleMetadata)
        BeautySearchProductionRouteSpecSupport.assertSeedOnlyProductionReadinessState(probe.productionReadinessState)
        BeautySearchProductionRouteSpecSupport.assertPreparedStartupTransition(probe.startupTransition)

        val response = runIO(
          observeRoute(apis, postJson("/beauty-search", """{"query":"nails","userLat":53.58,"userLon":10.08,"limit":3}"""))
        )

        assert(response.status == Status.Ok)

        val json = parseJsonOrFail(response.body)
        assert(json.hcursor.downField("variantCarousel").focus.exists(_.asArray.exists(_.isEmpty)))
        assert(json.hcursor.downField("providerCarousel").focus.exists(_.asArray.exists(_.isEmpty)))
        assert(json.hcursor.downField("serviceIntentCarousel").focus.exists(_.asArray.exists(_.isEmpty)))
        assert(json.hcursor.downField("facets").focus.isDefined)
        assert(json.hcursor.downField("inferredFilters").focus.isDefined)

        val methods = recordedRequests.synchronized(recordedRequests.map(_.method).toList)
        assert(methods.contains("PUT"))
        assert(recordedRequests.synchronized(recordedRequests.map(_.path).toList).exists(_.contains("_bulk")))
        assert(recordedRequests.synchronized(recordedRequests.map(_.path).toList).exists(_.contains("_refresh")))
        assert(recordedRequests.synchronized(recordedRequests.map(_.path).toList).exists(_.contains("_search")))
      } finally {
        server.stop(0)
      }
    }
  }

  private def buildProbe(port: Int): DefaultReadyProbe = {
    val module = new distage.ModuleDef {
      include(BeautySearchRouteModules.seedCatalogElasticsearchPortConfigured)
      make[ElasticsearchPortCfg].fromValue(ElasticsearchPortCfg("localhost", port))
      make[BeautySearchTapirEndpoints].fromValue(BeautySearchTapirEndpoints)
      make[Async[Task]].fromValue(Async[Task])
      make[DefaultReadyProbe].from {
        (
          beautySearchApi: BeautySearchApi[IO],
          allHttpApis: Set[HttpApi[IO]],
          lifecycleMetadata: ElasticsearchSeedLifecycleMetadata,
          productionReadinessState: ElasticsearchProductionReadinessState,
          startupTransition: ElasticsearchStartupReadinessTransition,
        ) =>
          val _ = beautySearchApi
          DefaultReadyProbe(beautySearchApi, allHttpApis, lifecycleMetadata, productionReadinessState, startupTransition)
      }
    }

    val locator = Injector().produce(
      bindings = module,
      roots = Roots.target[DefaultReadyProbe],
      activation = Activation.empty,
      locatorPrivacy = LocatorPrivacy.PublicByDefault,
    ).unsafeGet()

    locator.get[DefaultReadyProbe]
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

  private final case class DefaultReadyProbe(
    beautySearchApi: BeautySearchApi[IO],
    allHttpApis: Set[HttpApi[IO]],
    lifecycleMetadata: ElasticsearchSeedLifecycleMetadata,
    productionReadinessState: ElasticsearchProductionReadinessState,
    startupTransition: ElasticsearchStartupReadinessTransition,
  )

  private final case class RecordedEsRequest(
    method: String,
    path: String,
    contentType: Option[String],
    body: String,
  )

  private def parseJsonOrFail(value: String): Json =
    parse(value) match {
      case Right(json) => json
      case Left(error) => fail(s"invalid JSON: ${error.getMessage}")
    }

  private def runIO[E, A](effect: ZIO[Any, E, A]): A =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
    }
}
