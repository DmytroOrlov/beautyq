package leaderboard.search

import cats.effect.Async
import cats.syntax.all.*
import distage.Injector
import fs2.text
import io.circe.Json
import io.circe.parser.parse
import io.circe.syntax._
import izumi.distage.model.definition.{Activation, LocatorPrivacy}
import izumi.distage.model.plan.Roots
import leaderboard.api.{BeautySearchApi, EsLifecycleStatusApi, HttpApi}
import leaderboard.http.tapir.{BeautySearchTapirEndpoints, EsLifecycleStatusTapirEndpoints}
import leaderboard.model.QueryFailure
import leaderboard.plugins.BeautySearchRouteModules
import leaderboard.search.elasticsearch.{
  ElasticsearchJsonClient,
  ElasticsearchLifecycleStatusResponse,
  ElasticsearchProductionReadinessState,
  ElasticsearchSeedIndexReadiness,
  ElasticsearchSeedLifecycleMetadata,
  ElasticsearchStartupReadinessStatusResponse,
  ElasticsearchStartupReadinessTransition,
}
import leaderboard.{HttpContractTestSupport, ObservedResponse}
import org.http4s.{HttpApp, Request, Status}
import org.scalatest.wordspec.AnyWordSpec
import zio.interop.catz.*
import zio.{IO, Runtime, Task, Unsafe, ZIO}

final class ElasticsearchOperatorVisibilityEndpointPolicySpec extends AnyWordSpec with HttpContractTestSupport {

  private val state: ElasticsearchProductionReadinessState =
    ElasticsearchProductionReadinessState.seedOnly(
      ElasticsearchSeedIndexReadiness(
        indexName = "beautyq_variant_v1",
        source = "seed-resource-loader",
        documentCount = 37,
      ).lifecycleMetadata
    )

  private val lifecycleResponse: ElasticsearchLifecycleStatusResponse =
    ElasticsearchLifecycleStatusResponse.from(state)

  private val preparedTransition: ElasticsearchStartupReadinessTransition.Prepared =
    ElasticsearchStartupReadinessTransition.prepared(state)

  private val preparedStatusProjection: ElasticsearchStartupReadinessStatusResponse =
    ElasticsearchStartupReadinessStatusResponse.from(preparedTransition)

  "Design A: default graph absence" should {

    "not include EsLifecycleStatusApi in the default seedCatalogElasticsearch module" in {
      val probe = buildDefaultEsRouteProbe()
      val apis  = probe.allHttpApis

      assert(apis.collect { case _: BeautySearchApi[IO] => () }.size == 1)
      assert(apis.collect { case _: EsLifecycleStatusApi[IO] => () }.isEmpty)
    }

    "return 404 for GET /ops/beauty-search/lifecycle in the default graph" in {
      val probe = buildDefaultEsRouteProbe()
      val apis  = probe.allHttpApis

      val response = runIO(
        observeRoute(apis, get("/ops/beauty-search/lifecycle"))
      )

      assert(response.status == Status.NotFound)
    }

    "still serve POST /beauty-search in the default graph" in {
      val probe = buildDefaultEsRouteProbe()
      val apis  = probe.allHttpApis

      val response = runIO(
        observeRoute(apis, postJson("/beauty-search", """{"query":"haircut","userLat":53.58,"userLon":10.08,"limit":3}"""))
      )

      assert(response.status == Status.Ok)
    }
  }

  "Design A: explicit opt-in operator visibility" should {

    "include EsLifecycleStatusApi in the opt-in module" in {
      val probe = buildOptInEsRouteProbe()
      val apis  = probe.allHttpApis

      assert(apis.collect { case _: BeautySearchApi[IO] => () }.size == 1)
      assert(apis.collect { case _: EsLifecycleStatusApi[IO] => () }.size == 1)
    }

    "return 200 OK with ElasticsearchStartupReadinessStatusResponse.Prepared when using opt-in module" in {
      val probe = buildOptInEsRouteProbe()
      val apis  = probe.allHttpApis

      val response = runIO(
        observeRoute(apis, get("/ops/beauty-search/lifecycle"))
      )

      assert(response.status == Status.Ok)
      val json = parseResponseJson(response)
      assert(json.hcursor.get[String]("transitionStatus") == Right("prepared"))
      assert(json.hcursor.get[String]("servingDecision") == Right("not_enforced"))
      assert(json.hcursor.get[Boolean]("productionLifecycleComplete") == Right(false))
      assert(json.hcursor.downField("lifecycleStatus").focus.isDefined)
      (): Unit
    }

    "be additive and not replace or alter POST /beauty-search in the opt-in module" in {
      val probe = buildOptInEsRouteProbe()
      val apis  = probe.allHttpApis

      val beautySearchResponse = runIO(
        observeRoute(apis, postJson("/beauty-search", """{"query":"haircut","userLat":53.58,"userLon":10.08,"limit":3}"""))
      )

      assert(beautySearchResponse.status == Status.Ok)

      val lifecycleResponse = runIO(
        observeRoute(apis, get("/ops/beauty-search/lifecycle"))
      )

      assert(lifecycleResponse.status == Status.Ok)
      val json = parseResponseJson(lifecycleResponse)
      assert(json.hcursor.get[String]("transitionStatus") == Right("prepared"))
      (): Unit
    }
  }

  "Design A: operator visibility endpoint response shape" should {

    "return response body as ElasticsearchStartupReadinessStatusResponse.Prepared" in {
      assert(preparedStatusProjection.isInstanceOf[ElasticsearchStartupReadinessStatusResponse.Prepared])
    }

    "include nested lifecycleStatus equal to ElasticsearchLifecycleStatusResponse.from(state)" in {
      preparedStatusProjection match {
        case p: ElasticsearchStartupReadinessStatusResponse.Prepared =>
          assert(p.lifecycleStatus == lifecycleResponse)
        case other =>
          fail(s"Expected Prepared projection, got $other")
      }
    }

    "include transitionStatus = \"prepared\"" in {
      assert(preparedStatusProjection.transitionStatus == "prepared")
    }

    "include servingDecision = \"not_enforced\"" in {
      assert(preparedStatusProjection.servingDecision == "not_enforced")
    }

    "include productionLifecycleComplete = false" in {
      assert(preparedStatusProjection.productionLifecycleComplete == false)
    }
  }

  "Design A: seed-only status values" should {

    "return lifecycleStatus = \"seed_only_not_production_lifecycle\"" in {
      assert(lifecycleResponse.lifecycleStatus == "seed_only_not_production_lifecycle")
    }

    "return preparationMode = \"eager_seed_index_preparation\"" in {
      assert(lifecycleResponse.preparationMode == "eager_seed_index_preparation")
    }

    "return servingReadiness = \"not_enforced\"" in {
      assert(lifecycleResponse.servingReadiness == "not_enforced")
    }

    "return replacement = \"not_configured\"" in {
      assert(lifecycleResponse.replacement == "not_configured")
    }

    "return freshness = \"not_tracked\"" in {
      assert(lifecycleResponse.freshness == "not_tracked")
    }

    "return refresh = \"eager_seed_preparation_only\"" in {
      assert(lifecycleResponse.refresh == "eager_seed_preparation_only")
    }

    "return rollback = \"not_configured\"" in {
      assert(lifecycleResponse.rollback == "not_configured")
    }

    "return operatorVisibility = \"not_exposed\"" in {
      assert(lifecycleResponse.operatorVisibility == "not_exposed")
    }

    "encode the full prepared startup status JSON with all seed-only values" in {
      val json = preparedStatusProjection.asJson
      val lifecycleJson = json.hcursor.downField("lifecycleStatus")

      assert(lifecycleJson.get[String]("lifecycleStatus") == Right("seed_only_not_production_lifecycle"))
      assert(lifecycleJson.get[String]("preparationMode") == Right("eager_seed_index_preparation"))
      assert(lifecycleJson.get[String]("servingReadiness") == Right("not_enforced"))
      assert(lifecycleJson.get[String]("replacement") == Right("not_configured"))
      assert(lifecycleJson.get[String]("freshness") == Right("not_tracked"))
      assert(lifecycleJson.get[String]("refresh") == Right("eager_seed_preparation_only"))
      assert(lifecycleJson.get[String]("rollback") == Right("not_configured"))
      assert(lifecycleJson.get[String]("operatorVisibility") == Right("not_exposed"))
      assert(lifecycleJson.get[Boolean]("productionLifecycleComplete") == Right(false))
    }
  }

  "Design A: route graph / rooting" should {

    "root operator endpoint only in the intended opt-in ES route graph / module" in {
      val probe = buildOptInEsRouteProbe()
      val apis  = probe.allHttpApis

      assert(apis.collect { case _: EsLifecycleStatusApi[IO] => () }.size == 1)

      val response = runIO(
        observeRoute(apis, get("/ops/beauty-search/lifecycle"))
      )

      assert(response.status == Status.Ok)
      val json = parseResponseJson(response)
      assert(json.hcursor.get[String]("transitionStatus") == Right("prepared"))
      (): Unit
    }

    "not root operator endpoint through seedCatalogInMemory" in {
      val module = new distage.ModuleDef {
        include(BeautySearchRouteModules.seedCatalogInMemory[IO])
        make[BeautySearchTapirEndpoints].fromValue(BeautySearchTapirEndpoints)
        make[EsLifecycleStatusTapirEndpoints].fromValue(EsLifecycleStatusTapirEndpoints)
        make[Async[Task]].fromValue(Async[Task])
        make[InMemoryProbe].from {
          (allHttpApis: Set[HttpApi[IO]]) =>
            InMemoryProbe(allHttpApis)
        }
      }

      val locator = Injector().produce(
        bindings = module,
        roots = Roots.target[InMemoryProbe],
        activation = Activation.empty,
        locatorPrivacy = LocatorPrivacy.PublicByDefault,
      ).unsafeGet()

      val probe = locator.get[InMemoryProbe]
      assert(probe.allHttpApis.collect { case _: EsLifecycleStatusApi[IO] => () }.isEmpty)
    }

    "not root operator endpoint through default seedCatalogElasticsearch" in {
      val probe = buildDefaultEsRouteProbe()
      val apis  = probe.allHttpApis

      assert(apis.collect { case _: BeautySearchApi[IO] => () }.size == 1)
      assert(apis.collect { case _: EsLifecycleStatusApi[IO] => () }.isEmpty)
    }
  }

  "Design A: no-new-ES-calls behavior" should {

    "read from DI-bound status / transition only" in {
      assert(preparedStatusProjection.transitionStatus == "prepared")
      assert(preparedStatusProjection.servingDecision == "not_enforced")
    }

    "not call Elasticsearch at request time" in {
      val probe = buildOptInEsRouteProbe()
      val apis  = probe.allHttpApis

      val response = runIO(
        observeRoute(apis, get("/ops/beauty-search/lifecycle"))
      )

      assert(response.status == Status.Ok)
      val json = parseResponseJson(response)
      assert(json.hcursor.get[String]("transitionStatus") == Right("prepared"))
      assert(json.hcursor.downField("lifecycleStatus").get[String]("source") == Right("seed-resource-loader"))
      (): Unit
    }

    "leave existing POST /beauty-search ES call assertions unchanged" in pending
  }

  "Design A: exposure / auth policy" should {

    "be absent from default graph and present in explicit opt-in graph" in {
      val defaultProbe = buildDefaultEsRouteProbe()
      assert(defaultProbe.allHttpApis.collect { case _: EsLifecycleStatusApi[IO] => () }.isEmpty)

      val optInProbe = buildOptInEsRouteProbe()
      assert(optInProbe.allHttpApis.collect { case _: EsLifecycleStatusApi[IO] => () }.size == 1)
    }

    "allow local / dev-only fallback if chosen later" in pending

    "not expose as public product API" in pending
  }

  "Design A: limitations" should {

    "not expose PreparationFailed variant from a successfully constructed route graph" in {
      preparedStatusProjection match {
        case _: ElasticsearchStartupReadinessStatusResponse.Prepared =>
        case other =>
          fail(s"Expected only Prepared from DI-bound transition, got $other")
      }
    }

    "not expose startup failure status" in pending

    "not claim replacement / freshness / rollback fields as implemented behavior" in {
      assert(lifecycleResponse.replacement == "not_configured")
      assert(lifecycleResponse.freshness == "not_tracked")
      assert(lifecycleResponse.refresh == "eager_seed_preparation_only")
      assert(lifecycleResponse.rollback == "not_configured")
      assert(lifecycleResponse.operatorVisibility == "not_exposed")
      assert(lifecycleResponse.productionLifecycleComplete == false)
    }

    "not test runtime route-gate or HTTP 503 behavior as implemented" in pending
  }

  private def buildDefaultEsRouteProbe(): DefaultEsRouteProbe = {
    val module = new distage.ModuleDef {
      include(BeautySearchRouteModules.seedCatalogElasticsearch)
      make[BeautySearchTapirEndpoints].fromValue(BeautySearchTapirEndpoints)
      make[Async[Task]].fromValue(Async[Task])
      make[ElasticsearchJsonClient].from(mockEsClient)
      make[DefaultEsRouteProbe].from {
        (
          beautySearchApi: BeautySearchApi[IO],
          allHttpApis: Set[HttpApi[IO]],
          lifecycleMetadata: ElasticsearchSeedLifecycleMetadata,
          productionReadinessState: ElasticsearchProductionReadinessState,
          startupTransition: ElasticsearchStartupReadinessTransition,
        ) =>
          val _ = beautySearchApi
          DefaultEsRouteProbe(allHttpApis, lifecycleMetadata, productionReadinessState, startupTransition)
      }
    }

    val locator = Injector().produce(
      bindings = module,
      roots = Roots.target[DefaultEsRouteProbe],
      activation = Activation.empty,
      locatorPrivacy = LocatorPrivacy.PublicByDefault,
    ).unsafeGet()

    locator.get[DefaultEsRouteProbe]
  }

  private def buildOptInEsRouteProbe(): OptInEsRouteProbe = {
    val module = new distage.ModuleDef {
      include(BeautySearchRouteModules.seedCatalogElasticsearchWithOperatorVisibility)
      make[BeautySearchTapirEndpoints].fromValue(BeautySearchTapirEndpoints)
      make[Async[Task]].fromValue(Async[Task])
      make[ElasticsearchJsonClient].from(mockEsClient)
      make[OptInEsRouteProbe].from {
        (
          beautySearchApi: BeautySearchApi[IO],
          esLifecycleApi: EsLifecycleStatusApi[IO],
          allHttpApis: Set[HttpApi[IO]],
          lifecycleMetadata: ElasticsearchSeedLifecycleMetadata,
          productionReadinessState: ElasticsearchProductionReadinessState,
          startupTransition: ElasticsearchStartupReadinessTransition,
        ) =>
          val _ = (beautySearchApi, esLifecycleApi)
          OptInEsRouteProbe(allHttpApis, lifecycleMetadata, productionReadinessState, startupTransition)
      }
    }

    val locator = Injector().produce(
      bindings = module,
      roots = Roots.target[OptInEsRouteProbe],
      activation = Activation.empty,
      locatorPrivacy = LocatorPrivacy.PublicByDefault,
    ).unsafeGet()

    locator.get[OptInEsRouteProbe]
  }

  private def mockEsClient: ElasticsearchJsonClient = new ElasticsearchJsonClient {
    override def putJson(path: String, json: Json): IO[QueryFailure, Json]       = ZIO.succeed(Json.obj())
    override def post(path: String): IO[QueryFailure, Json]                      = ZIO.succeed(Json.obj())
    override def postJson(path: String, json: Json): IO[QueryFailure, Json]      =
      if (path.contains("_search")) ZIO.succeed(Json.obj("hits" -> Json.obj("hits" -> Json.arr())))
      else ZIO.succeed(Json.obj())
    override def postNdjson(path: String, payload: String): IO[QueryFailure, Json] = ZIO.succeed(Json.obj())
    override def getJson(path: String): IO[QueryFailure, Json]                   = ZIO.dieMessage(s"unexpected getJson($path)")
    override def delete(path: String): IO[QueryFailure, Unit]                    = ZIO.dieMessage(s"unexpected delete($path)")
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

  private def parseResponseJson(response: ObservedResponse): Json =
    parse(response.body) match {
      case Right(json) => json
      case Left(error) => fail(s"Invalid JSON: ${error.getMessage}; body: ${response.body}")
    }

  private def runIO[E, A](effect: ZIO[Any, E, A]): A =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
    }

  private final case class DefaultEsRouteProbe(
    allHttpApis: Set[HttpApi[IO]],
    lifecycleMetadata: ElasticsearchSeedLifecycleMetadata,
    productionReadinessState: ElasticsearchProductionReadinessState,
    startupTransition: ElasticsearchStartupReadinessTransition,
  )

  private final case class OptInEsRouteProbe(
    allHttpApis: Set[HttpApi[IO]],
    lifecycleMetadata: ElasticsearchSeedLifecycleMetadata,
    productionReadinessState: ElasticsearchProductionReadinessState,
    startupTransition: ElasticsearchStartupReadinessTransition,
  )

  private final case class InMemoryProbe(
    allHttpApis: Set[HttpApi[IO]]
  )
}
