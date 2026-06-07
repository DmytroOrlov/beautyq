package leaderboard.search

import distage.{Injector, ModuleDef}
import izumi.distage.model.definition.{Activation, LocatorPrivacy}
import izumi.distage.model.plan.Roots
import leaderboard.plugins.BeautySearchCatalogBackendModules
import leaderboard.search.document.BeautySearchReadyCatalogDocuments
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

  private final case class BeautySearchCatalogBackendModuleProbe(
    ready: BeautySearchReadyCatalogDocuments,
    backend: BeautySearchBackend[IO],
    service: BeautySearchService[IO],
  )

  private def runIO[E, A](effect: ZIO[Any, E, A]): A =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
    }
}
