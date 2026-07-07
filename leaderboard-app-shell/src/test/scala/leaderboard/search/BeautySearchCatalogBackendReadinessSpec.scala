package leaderboard.search

import leaderboard.model.QueryFailure
import leaderboard.plugins.BeautySearchCatalogBackendFactory
import leaderboard.search.document.{BeautyQSearchCatalogSnapshot, BeautyQVariantSearchDocumentMaterialization, BeautySearchReadyCatalogDocuments}
import leaderboard.search.dsl.BeautySearchSpecV1
import leaderboard.search.inmemory.InMemorySearchBackend
import leaderboard.search.parser.BeautySearchIntentParser
import leaderboard.seed.{BeautyQSeedData, BeautyQSeedLoader}
import org.scalatest.wordspec.AnyWordSpec
import zio.{IO, Runtime, Unsafe, ZIO}

final class BeautySearchCatalogBackendReadinessSpec extends AnyWordSpec {
  "Catalog snapshot readiness for the in-memory Beauty search backend" should {
    "make seed snapshot documents explicit before backend construction" in {
      val ready = loadReadyCatalogDocuments()

      assert(ready.source == SeedResourceLoaderSource)
      assert(ready.documents.nonEmpty)
    }

    "construct the in-memory backend and BeautySearchService.Impl from ready documents" in {
      val ready   = loadReadyCatalogDocuments()
      val backend = buildBackend(ready)
      val service = new BeautySearchService.Impl[IO](new BeautySearchIntentParser(BeautySearchSpecV1.spec), backend)

      val response = runIO(
        service.search(
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

    "reject empty ready documents before backend construction" in {
      val result = BeautySearchReadyCatalogDocuments.from(SeedResourceLoaderSource, Nil)

      assert(result == Left(QueryFailure.domain(s"$SeedResourceLoaderSource catalog documents are empty")))
    }
  }

  private def loadReadyCatalogDocuments(): BeautySearchReadyCatalogDocuments = {
    val seed      = loadSeedData()
    val snapshot  = BeautyQSearchCatalogSnapshot(
      categories                 = seed.categories,
      services                   = seed.services,
      serviceVariantSchemas      = seed.serviceVariantSchemas,
      masters                    = seed.masters,
      masterLocations            = seed.masterLocations,
      masterServiceOffers        = seed.masterServiceOffers,
      masterServiceOfferVariants = seed.masterServiceOfferVariants,
    )
    val documents = BeautyQVariantSearchDocumentMaterialization.project(snapshot) match {
      case Right(value) => value
      case Left(error) => throw new RuntimeException(error.message)
    }

    BeautySearchReadyCatalogDocuments.from(SeedResourceLoaderSource, documents) match {
      case Right(value) => value
      case Left(error) => throw new RuntimeException(error.message)
    }
  }

  private def loadSeedData(): BeautyQSeedData =
    new BeautyQSeedLoader.ResourceLoader().load() match {
      case Right(value) => value
      case Left(error) => throw new RuntimeException(error.message)
    }

  private def buildBackend(ready: BeautySearchReadyCatalogDocuments): BeautySearchBackend[IO] =
    new InMemorySearchBackend[IO](BeautySearchSpecV1.spec, ready.documents)

  private val SeedResourceLoaderSource = BeautySearchCatalogBackendFactory.SeedResourceLoaderSource

  private def runIO[E, A](effect: ZIO[Any, E, A]): A =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
    }
}
