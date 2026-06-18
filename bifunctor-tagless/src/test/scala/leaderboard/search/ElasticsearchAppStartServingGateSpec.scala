package leaderboard.search

import cats.effect.Async
import distage.Injector
import io.circe.Json
import izumi.distage.model.definition.{Activation, LocatorPrivacy}
import izumi.distage.model.plan.Roots
import leaderboard.api.{BeautySearchApi, HttpApi}
import leaderboard.http.tapir.BeautySearchTapirEndpoints
import leaderboard.model.QueryFailure
import leaderboard.plugins.BeautySearchRouteModules
import leaderboard.search.document.{BeautySearchCatalogSnapshot, BeautySearchReadyCatalogDocuments, VariantSearchDocumentBuilder}
import leaderboard.search.dsl.BeautySearchSpecV1
import leaderboard.search.elasticsearch._
import leaderboard.seed.BeautyQSeedLoader
import org.scalatest.wordspec.AnyWordSpec
import zio.interop.catz.*
import zio.{IO, Runtime, Task, Unsafe, ZIO}

final class ElasticsearchAppStartServingGateSpec extends AnyWordSpec with BeautySearchProductionRouteSpecSupport {

  private val spec = BeautySearchSpecV1.spec

  private val seedData = loadSeedData()
  private val snapshot = BeautySearchCatalogSnapshot.fromSeedData(seedData)
  private val allDocuments = VariantSearchDocumentBuilder.build(snapshot) match {
    case Right(value) => value
    case Left(error)  => throw new RuntimeException(error.message)
  }
  private val subset = allDocuments.take(2)

  private def loadSeedData() =
    new BeautyQSeedLoader.ResourceLoader().load() match {
      case Right(value) => value
      case Left(error)  => throw new RuntimeException(error.message)
    }

  private val emptySearchResponse: Json =
    Json.obj("hits" -> Json.obj("hits" -> Json.arr()))

  private def succeedingClient: ElasticsearchJsonClient = new ElasticsearchJsonClient {
    override def putJson(path: String, json: Json): IO[QueryFailure, Json]         = ZIO.succeed(Json.obj())
    override def post(path: String): IO[QueryFailure, Json]                        = ZIO.succeed(Json.obj())
    override def postJson(path: String, json: Json): IO[QueryFailure, Json]        =
      if (path.contains("_search")) ZIO.succeed(emptySearchResponse)
      else ZIO.dieMessage(s"unexpected postJson($path)")
    override def postNdjson(path: String, payload: String): IO[QueryFailure, Json]  = ZIO.succeed(Json.obj())
    override def getJson(path: String): IO[QueryFailure, Json]                     = ZIO.dieMessage(s"unexpected getJson($path)")
    override def delete(path: String): IO[QueryFailure, Unit]                      = ZIO.dieMessage(s"unexpected delete($path)")
  }

  private def failingClient: ElasticsearchJsonClient = new ElasticsearchJsonClient {
    override def putJson(path: String, json: Json): IO[QueryFailure, Json]         = ZIO.fail(ElasticsearchJsonClient.failure("index creation failed"))
    override def post(path: String): IO[QueryFailure, Json]                        = ZIO.dieMessage(s"unexpected post($path)")
    override def postJson(path: String, json: Json): IO[QueryFailure, Json]        = ZIO.dieMessage(s"unexpected postJson($path)")
    override def postNdjson(path: String, payload: String): IO[QueryFailure, Json]  = ZIO.dieMessage(s"unexpected postNdjson($path)")
    override def getJson(path: String): IO[QueryFailure, Json]                     = ZIO.dieMessage(s"unexpected getJson($path)")
    override def delete(path: String): IO[QueryFailure, Unit]                      = ZIO.dieMessage(s"unexpected delete($path)")
  }

  private def runEither[A](effect: IO[QueryFailure, A]): Either[QueryFailure, A] =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(effect.either).getOrThrowFiberFailure()
    }

  "Elasticsearch app-start fail-closed serving gate" should {

    "reject blank source before producing a usable composition" in {
      val result = runEither(
        ElasticsearchSeedSearchComposition.build(
          spec,
          succeedingClient,
          BeautySearchReadyCatalogDocuments(source = "   ", documents = subset),
        )
      )
      result match {
        case Left(QueryFailure.OperationFailure(operationName, message)) =>
          assert(operationName == "elasticsearch-seed-index-readiness")
          assert(message.contains("source is empty"), s"Expected message to contain 'source is empty': $message")
        case other =>
          fail(s"Expected OperationFailure with source empty, got $other")
      }
    }

    "reject empty documents before producing a usable composition" in {
      val result = runEither(
        ElasticsearchSeedSearchComposition.build(
          spec,
          succeedingClient,
          BeautySearchReadyCatalogDocuments(source = "seed-resource-loader", documents = Nil),
        )
      )
      result match {
        case Left(QueryFailure.OperationFailure(operationName, message)) =>
          assert(operationName == "elasticsearch-seed-index-readiness")
          assert(message.contains("documents are empty"), s"Expected message to contain 'documents are empty': $message")
        case other =>
          fail(s"Expected OperationFailure with documents empty, got $other")
      }
    }

    "reject ES client failure before producing a usable composition" in {
      val result = runEither(
        ElasticsearchSeedSearchComposition.build(
          spec,
          failingClient,
          BeautySearchReadyCatalogDocuments(source = "seed-resource-loader", documents = subset),
        )
      )
      result match {
        case Left(QueryFailure.OperationFailure(operationName, message)) =>
          assert(operationName == "elasticsearch-json-client")
          assert(message == "index creation failed")
        case other =>
          fail(s"Expected OperationFailure from client failure, got $other")
      }
    }

    "prevent BeautySearchApi construction when ES client fails during DI graph construction" in {
      intercept[Throwable] {
        buildModuleProbeWithFailingEsClient()
      }
      (): Unit
    }

    "allow BeautySearchApi construction and serve POST /beauty-search when composition succeeds" in {
      withZeroHitEsServer { port =>
        val probe = buildProductionApiGraphRouteProbe(port)
        val apis  = probe.allHttpApis

        assert(apis.collect { case api: BeautySearchApi[IO] => api }.size == 1)
        assertSeedOnlyLifecycleMetadata(probe.lifecycleMetadata)
        assertSeedOnlyProductionReadinessState(probe.productionReadinessState)
        assertPreparedStartupTransition(probe.startupTransition)

        val response = runIO(
          observeRoute(
            apis,
            postJson("/beauty-search", """{"query":"haircut","userLat":53.58,"userLon":10.08,"limit":3}"""),
          )
        )

        assertOkWithEmptyBeautySearchResponseShape(response)
      }
    }
  }

  private def buildModuleProbeWithFailingEsClient(): BeautySearchProductionRouteProbe = {
    val module = new distage.ModuleDef {
      include(BeautySearchRouteModules.seedCatalogElasticsearch)
      make[BeautySearchTapirEndpoints].fromValue(BeautySearchTapirEndpoints)
      make[Async[Task]].fromValue(Async[Task])
      make[ElasticsearchJsonClient].from(failingClient)
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
}
