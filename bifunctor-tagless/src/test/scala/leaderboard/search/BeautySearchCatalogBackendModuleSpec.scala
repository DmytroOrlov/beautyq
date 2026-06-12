package leaderboard.search

import distage.{Injector, ModuleDef}
import io.circe.Json
import izumi.distage.model.definition.{Activation, LocatorPrivacy}
import izumi.distage.model.plan.Roots
import leaderboard.model.QueryFailure
import leaderboard.plugins.BeautySearchCatalogBackendModules
import leaderboard.search.document.BeautySearchReadyCatalogDocuments
import leaderboard.search.elasticsearch.{ElasticsearchJsonClient, ElasticsearchSearchBackend, ElasticsearchSeedIndexReadiness}
import leaderboard.search.inmemory.InMemorySearchBackend
import leaderboard.seed.BeautyQSeedLoader
import org.scalatest.wordspec.AnyWordSpec
import zio.{IO, Runtime, Unsafe, ZIO}

final class BeautySearchCatalogBackendModuleSpec extends AnyWordSpec {
  "BeautySearchCatalogBackendModules.seedResourceInMemory" should {
    "materialize ready documents, InMemorySearchBackend, and BeautySearchService.Impl when explicitly included" in {
      val probe = buildProbe()

      assert(probe.ready.documents.nonEmpty)
      assert(probe.backend.isInstanceOf[InMemorySearchBackend[IO]])
      assert(probe.service.isInstanceOf[BeautySearchService.Impl[IO]])

      val response = runIO(
        probe.service.search(
          UserSearchInput(
            query = "",
            userLat = Some(BigDecimal("53.58")),
            userLon = Some(BigDecimal("10.08")),
            limit = 3,
          )
        )
      )

      assert(response.variantCarousel.nonEmpty)
      assert(response.variantCarousel.size <= 3)
      assert(response.providerCarousel.nonEmpty)
      assert(response.serviceIntentCarousel.nonEmpty)
    }
  }

  "BeautySearchCatalogBackendModules.seedResourceElasticsearch" should {
    "materialize ready documents, ES readiness, ElasticsearchSearchBackend, and BeautySearchService.Impl when explicitly included" in {
      val probe = buildEsProbe()

      assert(probe.ready.documents.nonEmpty)
      assert(probe.readiness.indexName.nonEmpty)
      assert(probe.readiness.documentCount == probe.ready.documents.size)
      assert(probe.backend.isInstanceOf[ElasticsearchSearchBackend])
      assert(probe.service.isInstanceOf[BeautySearchService.Impl[IO]])

      val response = runIO(
        probe.service.search(
          UserSearchInput(
            query = "",
            userLat = Some(BigDecimal("53.58")),
            userLon = Some(BigDecimal("10.08")),
            limit = 3,
          )
        )
      )

      assert(response.variantCarousel.isEmpty)
      assert(response.providerCarousel.isEmpty)
      assert(response.serviceIntentCarousel.isEmpty)
    }
  }

  private def buildProbe(): BeautySearchCatalogBackendModuleProbe = {
    val module = new ModuleDef {
      include(BeautySearchCatalogBackendModules.seedResourceInMemory[IO])
      make[BeautyQSeedLoader].from[BeautyQSeedLoader.ResourceLoader]
      make[BeautySearchCatalogBackendModuleProbe].from {
        (
          ready: BeautySearchReadyCatalogDocuments,
          backend: BeautySearchBackend[IO],
          service: BeautySearchService[IO],
        ) =>
          BeautySearchCatalogBackendModuleProbe(ready, backend, service)
      }
    }

    val locator = Injector().produce(
      bindings = module,
      roots = Roots.target[BeautySearchCatalogBackendModuleProbe],
      activation = Activation.empty,
      locatorPrivacy = LocatorPrivacy.PublicByDefault,
    ).unsafeGet()

    locator.get[BeautySearchCatalogBackendModuleProbe]
  }

  private def buildEsProbe(): EsProbe = {
    val module = new ModuleDef {
      include(BeautySearchCatalogBackendModules.seedResourceElasticsearch)
      make[BeautyQSeedLoader].from[BeautyQSeedLoader.ResourceLoader]
      make[ElasticsearchJsonClient].from {
        new ElasticsearchJsonClient {
          override def putJson(path: String, json: Json): IO[QueryFailure, Json]       = ZIO.succeed(Json.obj())
          override def postJson(path: String, json: Json): IO[QueryFailure, Json]      =
            if (path.contains("_search")) ZIO.succeed(Json.obj("hits" -> Json.obj("hits" -> Json.arr())))
            else ZIO.succeed(Json.obj())
          override def postNdjson(path: String, payload: String): IO[QueryFailure, Json] = ZIO.succeed(Json.obj())
          override def getJson(path: String): IO[QueryFailure, Json]                   = ZIO.dieMessage(s"unexpected getJson($path)")
          override def delete(path: String): IO[QueryFailure, Unit]                    = ZIO.dieMessage(s"unexpected delete($path)")
        }
      }
      make[EsProbe].from {
        (
          ready: BeautySearchReadyCatalogDocuments,
          readiness: ElasticsearchSeedIndexReadiness,
          backend: BeautySearchBackend[IO],
          service: BeautySearchService[IO],
        ) =>
          EsProbe(ready, readiness, backend, service)
      }
    }

    val locator = Injector().produce(
      bindings = module,
      roots = Roots.target[EsProbe],
      activation = Activation.empty,
      locatorPrivacy = LocatorPrivacy.PublicByDefault,
    ).unsafeGet()

    locator.get[EsProbe]
  }

  private final case class BeautySearchCatalogBackendModuleProbe(
    ready: BeautySearchReadyCatalogDocuments,
    backend: BeautySearchBackend[IO],
    service: BeautySearchService[IO],
  )

  private final case class EsProbe(
    ready: BeautySearchReadyCatalogDocuments,
    readiness: ElasticsearchSeedIndexReadiness,
    backend: BeautySearchBackend[IO],
    service: BeautySearchService[IO],
  )

  private def runIO[E, A](effect: ZIO[Any, E, A]): A =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
    }
}
