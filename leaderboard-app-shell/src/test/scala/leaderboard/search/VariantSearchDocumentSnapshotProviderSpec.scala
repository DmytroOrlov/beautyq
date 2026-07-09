package leaderboard.search

import leaderboard.model.{MasterId, MasterLocationId, MasterServiceOfferId, MasterServiceOfferVariantId, QueryFailure, ServiceId}
import leaderboard.model.Category.CategoryId
import leaderboard.search.document.{InMemoryVariantSearchDocumentSnapshotProvider, VariantSearchDocument}
import leaderboard.search.dsl.SearchGeoPoint
import org.scalatest.wordspec.AnyWordSpec
import zio.{IO, Runtime, Unsafe}

import java.util.UUID

final class VariantSearchDocumentSnapshotProviderSpec extends AnyWordSpec {
  "InMemoryVariantSearchDocumentSnapshotProvider" should {
    "return the supplied documents" in {
      val documents = List(variantDocument(1), variantDocument(2))
      val provider = new InMemoryVariantSearchDocumentSnapshotProvider[IO](documents)

      val result = run(provider.loadSnapshot())

      assert(result == documents)
    }

    "preserve input order" in {
      val first = variantDocument(1)
      val second = variantDocument(2)
      val third = variantDocument(3)
      val provider = new InMemoryVariantSearchDocumentSnapshotProvider[IO](List(second, first, third))

      val result = run(provider.loadSnapshot())

      assert(result.map(_.variantId) == List(second.variantId, first.variantId, third.variantId))
    }

    "return an empty list for empty input" in {
      val provider = new InMemoryVariantSearchDocumentSnapshotProvider[IO](Nil)

      val result = run(provider.loadSnapshot())

      assert(result.isEmpty)
    }

    "not mutate or deduplicate documents" in {
      val duplicate = variantDocument(1)
      val other = variantDocument(2)
      val documents = List(duplicate, other, duplicate)
      val provider = new InMemoryVariantSearchDocumentSnapshotProvider[IO](documents)

      val result = run(provider.loadSnapshot())

      assert(result == documents)
      assert(result.count(_ == duplicate) == 2)
    }
  }

  private def variantDocument(index: Int): VariantSearchDocument =
    VariantSearchDocument(
      variantId = MasterServiceOfferVariantId(uuid(index, 1)),
      masterServiceOfferId = MasterServiceOfferId(uuid(index, 2)),
      masterLocationId = MasterLocationId(uuid(index, 3)),
      masterId = MasterId(uuid(index, 4)),
      serviceId = ServiceId(uuid(index, 5)),
      categoryId = CategoryId(uuid(index, 6)),
      serviceName = s"Service $index",
      categoryName = "Category",
      masterName = s"Master $index",
      locationName = s"Location $index",
      address = s"Address $index",
      location = SearchGeoPoint(lat = BigDecimal("52.5200"), lon = BigDecimal("13.4050")),
      lat = BigDecimal("52.5200"),
      lon = BigDecimal("13.4050"),
      priceFrom = BigDecimal("25.00"),
      priceTo = BigDecimal("40.00"),
      durationMin = 45,
      enumAttributes = Map("coverage" -> "gel"),
      booleanAttributes = Map("with_removal" -> true),
      intAttributes = Map("nails_count" -> 10),
      bigDecimalAttributes = Map("rating" -> BigDecimal("4.8")),
      allText = s"service $index category master location",
      serviceText = s"service $index category",
      attributeText = "coverage gel with removal",
      providerText = s"master $index location $index",
      locationText = s"location $index address $index category",
    )

  private def uuid(index: Int, suffix: Int): UUID =
    UUID.fromString(f"00000000-0000-0000-0000-${index * 100 + suffix}%012d")

  private def run[A](effect: IO[QueryFailure, A]): A =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
    }
}
