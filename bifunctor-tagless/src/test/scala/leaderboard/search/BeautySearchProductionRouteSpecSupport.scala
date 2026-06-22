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
import leaderboard.api.{BeautySearchApi, EsLifecycleStatusApi, HttpApi}
import leaderboard.config.ElasticsearchPortCfg
import leaderboard.http.tapir.BeautySearchTapirEndpoints
import leaderboard.plugins.BeautySearchRouteModules
import leaderboard.search.dsl.BeautySearchSpecV1
import leaderboard.search.elasticsearch.{
  ElasticsearchFreshnessReadiness,
  ElasticsearchOperatorVisibility,
  ElasticsearchProductionReadinessState,
  ElasticsearchRefreshReadiness,
  ElasticsearchReplacementReadiness,
  ElasticsearchRollbackReadiness,
  ElasticsearchServingReadiness,
  ElasticsearchSeedLifecycleMetadata,
  ElasticsearchSeedLifecycleStatus,
  ElasticsearchSeedPreparationMode,
  ElasticsearchStartupReadinessTransition,
  ElasticsearchStartupServingDecision,
}
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
    lifecycleMetadata: ElasticsearchSeedLifecycleMetadata,
    productionReadinessState: ElasticsearchProductionReadinessState,
    startupTransition: ElasticsearchStartupReadinessTransition,
  )

  protected final case class BeautySearchProductionRouteWithOperatorVisibilityProbe(
    beautySearchApi: BeautySearchApi[IO],
    esLifecycleApi: EsLifecycleStatusApi[IO],
    allHttpApis: Set[HttpApi[IO]],
    lifecycleMetadata: ElasticsearchSeedLifecycleMetadata,
    productionReadinessState: ElasticsearchProductionReadinessState,
    startupTransition: ElasticsearchStartupReadinessTransition,
  )

  protected final def withRecordingZeroHitEsServer(f: BeautySearchProductionRouteSpecSupport.RecordedEsServer => Unit): Unit =
    BeautySearchProductionRouteSpecSupport.withRecordingZeroHitEsServer(f)

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
      include(BeautySearchRouteModules.apiElasticsearch)
      make[ElasticsearchPortCfg].fromValue(ElasticsearchPortCfg("localhost", port))
      make[Async[Task]].fromValue(Async[Task])
      make[BeautySearchProductionRouteProbe].from {
        (
          beautySearchApi: BeautySearchApi[IO],
          allHttpApis: Set[HttpApi[IO]],
          lifecycleMetadata: ElasticsearchSeedLifecycleMetadata,
          productionReadinessState: ElasticsearchProductionReadinessState,
          startupTransition: ElasticsearchStartupReadinessTransition,
        ) =>
          BeautySearchProductionRouteProbe(beautySearchApi, allHttpApis, lifecycleMetadata, productionReadinessState, startupTransition)
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

  protected final def buildProductionApiGraphWithOperatorVisibilityRouteProbe(port: Int): BeautySearchProductionRouteWithOperatorVisibilityProbe = {
    val module = new distage.ModuleDef {
      include(BeautySearchRouteModules.apiElasticsearchWithOperatorVisibility)
      make[ElasticsearchPortCfg].fromValue(ElasticsearchPortCfg("localhost", port))
      make[Async[Task]].fromValue(Async[Task])
      make[BeautySearchProductionRouteWithOperatorVisibilityProbe].from {
        (
          beautySearchApi: BeautySearchApi[IO],
          esLifecycleApi: EsLifecycleStatusApi[IO],
          allHttpApis: Set[HttpApi[IO]],
          lifecycleMetadata: ElasticsearchSeedLifecycleMetadata,
          productionReadinessState: ElasticsearchProductionReadinessState,
          startupTransition: ElasticsearchStartupReadinessTransition,
        ) =>
          BeautySearchProductionRouteWithOperatorVisibilityProbe(beautySearchApi, esLifecycleApi, allHttpApis, lifecycleMetadata, productionReadinessState, startupTransition)
      }
    }

    val locator = Injector().produce(
      bindings = module,
      roots = Roots.target[BeautySearchProductionRouteWithOperatorVisibilityProbe],
      activation = Activation.empty,
      locatorPrivacy = LocatorPrivacy.PublicByDefault,
    ).unsafeGet()

    locator.get[BeautySearchProductionRouteWithOperatorVisibilityProbe]
  }

  protected final def buildServingGateEsRouteProbe(
    port: Int,
    servingGate: leaderboard.api.BeautySearchServingGate,
  ): BeautySearchProductionRouteProbe = {
    val module = new distage.ModuleDef {
      include(leaderboard.plugins.ElasticsearchClientModules.portConfigured)
      include(BeautySearchRouteModules.seedCatalogElasticsearchWithServingGate(servingGate))
      make[ElasticsearchPortCfg].fromValue(ElasticsearchPortCfg("localhost", port))
      make[Async[Task]].fromValue(Async[Task])
      make[BeautySearchProductionRouteProbe].from {
        (
          beautySearchApi: BeautySearchApi[IO],
          allHttpApis: Set[HttpApi[IO]],
          lifecycleMetadata: ElasticsearchSeedLifecycleMetadata,
          productionReadinessState: ElasticsearchProductionReadinessState,
          startupTransition: ElasticsearchStartupReadinessTransition,
        ) =>
          BeautySearchProductionRouteProbe(beautySearchApi, allHttpApis, lifecycleMetadata, productionReadinessState, startupTransition)
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
      make[BeautySearchTapirEndpoints].fromValue(BeautySearchTapirEndpoints)
      make[Async[Task]].fromValue(Async[Task])
      make[BeautySearchProductionRouteProbe].from {
        (
          beautySearchApi: BeautySearchApi[IO],
          allHttpApis: Set[HttpApi[IO]],
          lifecycleMetadata: ElasticsearchSeedLifecycleMetadata,
          productionReadinessState: ElasticsearchProductionReadinessState,
          startupTransition: ElasticsearchStartupReadinessTransition,
        ) =>
          BeautySearchProductionRouteProbe(beautySearchApi, allHttpApis, lifecycleMetadata, productionReadinessState, startupTransition)
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

  // Reusable executable smoke/evidence harness for the accepted serving-gate route states.
  //
  // Each case pins a stable id, an explicit serving gate, a request body, the expected HTTP status,
  // and a behavior assertion reused from the existing ES-backed route assertions. The harness builds
  // an ES-backed route probe via the accepted M16A explicit selector surface, observes the route, and
  // asserts both the expected status and the behavior. It introduces no new route/plugin/DI/http
  // behavior beyond the accepted selector surface and activates no Qdrant/fallback/hybrid path.
  protected final case class ServingGateEvidenceCase(
    id: String,
    gate: leaderboard.api.BeautySearchServingGate,
    requestBody: String,
    expectedStatus: Status,
    assertBehavior: ObservedResponse => Unit,
  )

  protected final def runServingGateEvidenceCase(evidenceCase: ServingGateEvidenceCase): Status = {
    var observedStatus: Status = null
    withZeroHitEsServer { port =>
      val probe = buildServingGateEsRouteProbe(port, evidenceCase.gate)

      val response = runIO(
        observeRoute(
          probe.allHttpApis,
          postJson("/beauty-search", evidenceCase.requestBody),
        )
      )

      assert(
        response.status == evidenceCase.expectedStatus,
        s"[${evidenceCase.id}] expected ${evidenceCase.expectedStatus}, got ${response.status}",
      )
      evidenceCase.assertBehavior(response)
      observedStatus = response.status
    }
    observedStatus
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

  protected final def assertSeedOnlyLifecycleMetadata(metadata: ElasticsearchSeedLifecycleMetadata): Unit =
    BeautySearchProductionRouteSpecSupport.assertSeedOnlyLifecycleMetadata(metadata)

  protected final def assertSeedOnlyProductionReadinessState(state: ElasticsearchProductionReadinessState): Unit =
    BeautySearchProductionRouteSpecSupport.assertSeedOnlyProductionReadinessState(state)

  protected final def assertPreparedStartupTransition(transition: ElasticsearchStartupReadinessTransition): Unit =
    BeautySearchProductionRouteSpecSupport.assertPreparedStartupTransition(transition)

  protected final def assertOperatorVisibilityEndpointPresent(apis: Set[HttpApi[IO]]): Unit =
    BeautySearchProductionRouteSpecSupport.assertOperatorVisibilityEndpointPresent(apis)

  protected final def assertOperatorVisibilityEndpointAbsent(apis: Set[HttpApi[IO]]): Unit =
    BeautySearchProductionRouteSpecSupport.assertOperatorVisibilityEndpointAbsent(apis)

  protected final def assertPreparedLifecycleStatusResponse(response: ObservedResponse): Unit =
    BeautySearchProductionRouteSpecSupport.assertPreparedLifecycleStatusResponse(response, parseResponseJson)

  protected final def assertDefaultBadRequest(response: ObservedResponse): Unit = {
    assert(
      response.status == Status.BadRequest,
      s"Expected 400 Bad Request, got ${response.status}",
    )
    (): Unit
  }

  protected final def assertStructuredBadRequest(
    response: ObservedResponse,
    error: BeautySearchRequestContract.SemanticError,
  ): Unit = {
    assert(
      response.status == Status.BadRequest,
      s"Expected 400 Bad Request, got ${response.status}",
    )
    assert(
      parseResponseJson(response) == Json.obj(
        "code" -> Json.fromString(error.code),
        "message" -> Json.fromString(error.message),
      ),
      s"Unexpected structured bad-request body: ${response.body}",
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

private[search] object BeautySearchProductionRouteSpecSupport {
  final case class RecordedEsRequest(
    method: String,
    path: String,
    contentType: Option[String],
    body: String,
  )

  final class RecordedEsServer(
    val port: Int,
    private val recordedRequests: scala.collection.mutable.ListBuffer[RecordedEsRequest],
    private val stopServer: () => Unit,
  ) {
    def requestCount: Int =
      recordedRequests.synchronized(recordedRequests.size)

    def requestPaths: List[String] =
      recordedRequests.synchronized(recordedRequests.map(_.path).toList)

    def stop(): Unit =
      stopServer()
  }

  def withRecordingZeroHitEsServer(f: RecordedEsServer => Unit): Unit = {
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
      f(new RecordedEsServer(server.getAddress.getPort, recordedRequests, () => server.stop(0)))
    } finally {
      server.stop(0)
    }
  }

  def assertSeedOnlyLifecycleMetadata(metadata: ElasticsearchSeedLifecycleMetadata): Unit = {
    assert(metadata.indexName == BeautySearchSpecV1.spec.variantDocument.indexName)
    assert(metadata.source == "seed-resource-loader")
    assert(metadata.documentCount > 0)
    assert(metadata.preparationMode == ElasticsearchSeedPreparationMode.EagerSeedIndexPreparation)
    assert(metadata.lifecycleStatus == ElasticsearchSeedLifecycleStatus.SeedOnlyNotProductionLifecycle)
    (): Unit
  }

  def assertSeedOnlyProductionReadinessState(state: ElasticsearchProductionReadinessState): Unit = {
    assertSeedOnlyLifecycleMetadata(state.lifecycleMetadata)
    assert(state.servingReadiness == ElasticsearchServingReadiness.NotEnforced)
    assert(state.replacement == ElasticsearchReplacementReadiness.NotConfigured)
    assert(state.freshness == ElasticsearchFreshnessReadiness.NotTracked)
    assert(state.refresh == ElasticsearchRefreshReadiness.EagerSeedPreparationOnly)
    assert(state.rollback == ElasticsearchRollbackReadiness.NotConfigured)
    assert(state.operatorVisibility == ElasticsearchOperatorVisibility.NotExposed)
    (): Unit
  }

  def assertPreparedStartupTransition(transition: ElasticsearchStartupReadinessTransition): Unit = {
    transition match {
      case prepared: ElasticsearchStartupReadinessTransition.Prepared =>
        assert(prepared.servingDecision == ElasticsearchStartupServingDecision.NotEnforced)
        assert(prepared.lifecycleMetadata.isDefined)
        assert(prepared.lifecycleStatusResponse.isDefined)
        prepared.lifecycleStatusResponse.foreach { response =>
          assert(response.productionLifecycleComplete == false)
        }
      case other =>
        fail(s"Expected Prepared startup transition, got $other")
    }
    (): Unit
  }

  def assertOperatorVisibilityEndpointPresent(apis: Set[HttpApi[IO]]): Unit = {
    assert(apis.collect { case _: EsLifecycleStatusApi[IO] => () }.size == 1, "Expected exactly one EsLifecycleStatusApi in the HttpApi set")
    (): Unit
  }

  def assertOperatorVisibilityEndpointAbsent(apis: Set[HttpApi[IO]]): Unit = {
    assert(apis.collect { case _: EsLifecycleStatusApi[IO] => () }.isEmpty, "Expected no EsLifecycleStatusApi in the default HttpApi set")
    (): Unit
  }

  def assertPreparedLifecycleStatusResponse(
    response: ObservedResponse,
    parseResponseJson: ObservedResponse => Json,
  ): Unit = {
    assert(response.status == Status.Ok, s"Expected 200 OK, got ${response.status}")

    val json            = parseResponseJson(response)
    val lifecycleCursor = json.hcursor.downField("lifecycleStatus")

    assert(json.hcursor.get[String]("transitionStatus") == Right("prepared"))
    assert(json.hcursor.get[String]("servingDecision") == Right("not_enforced"))
    assert(json.hcursor.get[Boolean]("productionLifecycleComplete") == Right(false))
    assert(lifecycleCursor.get[String]("indexName") == Right(BeautySearchSpecV1.spec.variantDocument.indexName))
    assert(lifecycleCursor.get[String]("source") == Right("seed-resource-loader"))
    assert(lifecycleCursor.get[String]("lifecycleStatus") == Right("seed_only_not_production_lifecycle"))
    assert(lifecycleCursor.get[String]("preparationMode") == Right("eager_seed_index_preparation"))
    assert(lifecycleCursor.get[String]("servingReadiness") == Right("not_enforced"))
    assert(lifecycleCursor.get[String]("replacement") == Right("not_configured"))
    assert(lifecycleCursor.get[String]("freshness") == Right("not_tracked"))
    assert(lifecycleCursor.get[String]("refresh") == Right("eager_seed_preparation_only"))
    assert(lifecycleCursor.get[String]("rollback") == Right("not_configured"))
    assert(lifecycleCursor.get[String]("operatorVisibility") == Right("not_exposed"))
    assert(lifecycleCursor.get[Boolean]("productionLifecycleComplete") == Right(false))
    assert(lifecycleCursor.get[Int]("documentCount").exists(_ > 0))
    assert(json.hcursor.downField("operationName").focus.isEmpty)
    assert(json.hcursor.downField("message").focus.isEmpty)
    (): Unit
  }
}
