package leaderboard.search

import cats.effect.Async
import cats.syntax.all.*
import distage.Injector
import fs2.text
import io.circe.Json
import izumi.distage.model.definition.{Activation, LocatorPrivacy}
import izumi.distage.model.plan.Roots
import leaderboard.api.{BeautySearchApi, EsLifecycleStatusApi, HttpApi}
import leaderboard.http.tapir.BeautySearchTapirEndpoints
import leaderboard.model.QueryFailure
import leaderboard.plugins.BeautySearchRouteModules
import leaderboard.search.elasticsearch.{
  ElasticsearchJsonClient,
  ElasticsearchProductionReadinessState,
  ElasticsearchSeedLifecycleMetadata,
  ElasticsearchStartupReadinessTransition,
}
import leaderboard.{HttpContractTestSupport, ObservedResponse}
import org.http4s.{HttpApp, Request, Status}
import org.scalatest.wordspec.AnyWordSpec
import zio.interop.catz.*
import zio.{IO, Runtime, Task, Unsafe, ZIO}

final class BeautySearchElasticsearchRouteModuleSpec extends AnyWordSpec with HttpContractTestSupport {
  "BeautySearchRouteModules.seedCatalogElasticsearch" should {
    "serve POST /beauty-search through the explicit ES seed route module" in {
      val probe = buildProbe()
      val apis  = probe.allHttpApis

      assert(apis.size == 1)
      assert(apis.collect { case api: BeautySearchApi[IO] => api }.size == 1)
      assert(apis.collect { case api: EsLifecycleStatusApi[IO] => api }.isEmpty)
      BeautySearchProductionRouteSpecSupport.assertSeedOnlyLifecycleMetadata(probe.lifecycleMetadata)
      BeautySearchProductionRouteSpecSupport.assertSeedOnlyProductionReadinessState(probe.productionReadinessState)
      BeautySearchProductionRouteSpecSupport.assertPreparedStartupTransition(probe.startupTransition)

      val response = runIO(
        observeRoute(
          apis,
          postJson("/beauty-search", """{"query":"nails","userLat":53.58,"userLon":10.08,"limit":3}"""),
        )
      )

      assert(response.status == Status.Ok)

      val json = io.circe.parser.parse(response.body).getOrElse(fail(s"invalid JSON: ${response.body}"))
      assert(json.hcursor.downField("variantCarousel").focus.exists(_.asArray.exists(_.isEmpty)))
      assert(json.hcursor.downField("providerCarousel").focus.exists(_.asArray.exists(_.isEmpty)))
      assert(json.hcursor.downField("serviceIntentCarousel").focus.exists(_.asArray.exists(_.isEmpty)))
      assert(json.hcursor.downField("facets").focus.exists(_.asArray.exists(_.nonEmpty)))
      assert(json.hcursor.downField("inferredFilters").focus.exists(_.asArray.exists(_.nonEmpty)))
    }

    "not serve GET /ops/beauty-search/lifecycle through the default module" in {
      val probe = buildProbe()
      val apis  = probe.allHttpApis

      assert(apis.collect { case _: EsLifecycleStatusApi[IO] => () }.isEmpty)

      val response = runIO(
        observeRoute(
          apis,
          get("/ops/beauty-search/lifecycle"),
        )
      )

      assert(response.status == Status.NotFound)
    }
  }

  "BeautySearchRouteModules.seedCatalogElasticsearchWithOperatorVisibility" should {
    "serve POST /beauty-search and GET /ops/beauty-search/lifecycle" in {
      val probe = buildOptInProbe()
      val apis  = probe.allHttpApis

      assert(apis.size == 2)
      assert(apis.collect { case api: BeautySearchApi[IO] => api }.size == 1)
      assert(apis.collect { case api: EsLifecycleStatusApi[IO] => api }.size == 1)
      BeautySearchProductionRouteSpecSupport.assertSeedOnlyLifecycleMetadata(probe.lifecycleMetadata)
      BeautySearchProductionRouteSpecSupport.assertSeedOnlyProductionReadinessState(probe.productionReadinessState)
      BeautySearchProductionRouteSpecSupport.assertPreparedStartupTransition(probe.startupTransition)

      val response = runIO(
        observeRoute(
          apis,
          postJson("/beauty-search", """{"query":"nails","userLat":53.58,"userLon":10.08,"limit":3}"""),
        )
      )

      assert(response.status == Status.Ok)

      val lifecycleResponse = runIO(
        observeRoute(
          apis,
          get("/ops/beauty-search/lifecycle"),
        )
      )

      assert(lifecycleResponse.status == Status.Ok)

      val json = io.circe.parser.parse(lifecycleResponse.body).getOrElse(fail(s"invalid JSON: ${lifecycleResponse.body}"))
      assert(json.hcursor.get[String]("transitionStatus") == Right("prepared"))
      assert(json.hcursor.get[String]("servingDecision") == Right("not_enforced"))
      assert(json.hcursor.get[Boolean]("productionLifecycleComplete") == Right(false))
      assert(json.hcursor.downField("lifecycleStatus").get[String]("lifecycleStatus") == Right("seed_only_not_production_lifecycle"))
      assert(json.hcursor.downField("lifecycleStatus").get[String]("source") == Right("seed-resource-loader"))
    }
  }

  private def buildProbe(): BeautySearchElasticsearchRouteModuleProbe = {
    val module = new distage.ModuleDef {
      include(BeautySearchRouteModules.seedCatalogElasticsearch)
      make[BeautySearchTapirEndpoints].fromValue(BeautySearchTapirEndpoints)
      make[Async[Task]].fromValue(Async[Task])
      make[ElasticsearchJsonClient].from(mockEsClient)
      make[BeautySearchElasticsearchRouteModuleProbe].from {
        (
          beautySearchApi: BeautySearchApi[IO],
          allHttpApis: Set[HttpApi[IO]],
          lifecycleMetadata: ElasticsearchSeedLifecycleMetadata,
          productionReadinessState: ElasticsearchProductionReadinessState,
          startupTransition: ElasticsearchStartupReadinessTransition,
        ) =>
          val _ = beautySearchApi
          BeautySearchElasticsearchRouteModuleProbe(allHttpApis, lifecycleMetadata, productionReadinessState, startupTransition)
      }
    }

    val locator = Injector().produce(
      bindings = module,
      roots = Roots.target[BeautySearchElasticsearchRouteModuleProbe],
      activation = Activation.empty,
      locatorPrivacy = LocatorPrivacy.PublicByDefault,
    ).unsafeGet()

    locator.get[BeautySearchElasticsearchRouteModuleProbe]
  }

  private def buildOptInProbe(): BeautySearchElasticsearchRouteModuleWithOptInProbe = {
    val module = new distage.ModuleDef {
      include(BeautySearchRouteModules.seedCatalogElasticsearchWithOperatorVisibility)
      make[BeautySearchTapirEndpoints].fromValue(BeautySearchTapirEndpoints)
      make[Async[Task]].fromValue(Async[Task])
      make[ElasticsearchJsonClient].from(mockEsClient)
      make[BeautySearchElasticsearchRouteModuleWithOptInProbe].from {
        (
          beautySearchApi: BeautySearchApi[IO],
          esLifecycleApi: EsLifecycleStatusApi[IO],
          allHttpApis: Set[HttpApi[IO]],
          lifecycleMetadata: ElasticsearchSeedLifecycleMetadata,
          productionReadinessState: ElasticsearchProductionReadinessState,
          startupTransition: ElasticsearchStartupReadinessTransition,
        ) =>
          val _ = (beautySearchApi, esLifecycleApi)
          BeautySearchElasticsearchRouteModuleWithOptInProbe(allHttpApis, lifecycleMetadata, productionReadinessState, startupTransition)
      }
    }

    val locator = Injector().produce(
      bindings = module,
      roots = Roots.target[BeautySearchElasticsearchRouteModuleWithOptInProbe],
      activation = Activation.empty,
      locatorPrivacy = LocatorPrivacy.PublicByDefault,
    ).unsafeGet()

    locator.get[BeautySearchElasticsearchRouteModuleWithOptInProbe]
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

  private final case class BeautySearchElasticsearchRouteModuleProbe(
    allHttpApis: Set[HttpApi[IO]],
    lifecycleMetadata: ElasticsearchSeedLifecycleMetadata,
    productionReadinessState: ElasticsearchProductionReadinessState,
    startupTransition: ElasticsearchStartupReadinessTransition,
  )

  private final case class BeautySearchElasticsearchRouteModuleWithOptInProbe(
    allHttpApis: Set[HttpApi[IO]],
    lifecycleMetadata: ElasticsearchSeedLifecycleMetadata,
    productionReadinessState: ElasticsearchProductionReadinessState,
    startupTransition: ElasticsearchStartupReadinessTransition,
  )

  private def runIO[E, A](effect: ZIO[Any, E, A]): A =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
    }
}
