package leaderboard.search

import java.util.UUID

import leaderboard.model.MasterServiceOfferVariantId
import leaderboard.search.document.VariantSearchDocument
import leaderboard.search.dsl.SearchGeoPoint
import leaderboard.search.hybrid.BeautyQHybridCandidateSource.{Lexical, Semantic}
import leaderboard.search.hybrid.{
  BeautyQHybridProjectedVariantCandidate,
  BeautyQHybridResponseAdapter,
  BeautyQHybridVariantProjectionDiagnostics,
  BeautyQHybridVariantProjectionResult,
}
import org.scalatest.wordspec.AnyWordSpec

final class BeautyQHybridResponseAdapterSpec extends AnyWordSpec {
  "BeautyQ hybrid response adapter" should {
    "build variant carousel from hydrated hybrid candidates" in {
      val first = variantDocument(1)
      val second = variantDocument(2)

      val response = adapt(
        candidate(first, lexicalScore = Some(12.0), semanticScore = None),
        candidate(second, lexicalScore = None, semanticScore = Some(0.84)),
      ).response

      assert(response.variantCarousel.map(_.variantId) == List(first.variantId, second.variantId))
    }

    "preserve candidate order" in {
      val first = variantDocument(3)
      val second = variantDocument(4)

      val response = adapt(
        candidate(second, lexicalScore = None, semanticScore = Some(0.78)),
        candidate(first, lexicalScore = Some(7.0), semanticScore = None),
      ).response

      assert(response.variantCarousel.map(_.variantId) == List(second.variantId, first.variantId))
    }

    "use lexical score as display score when present" in {
      val document = variantDocument(5)

      val response = adapt(candidate(document, lexicalScore = Some(18.5), semanticScore = None)).response

      assert(response.variantCarousel.head.score == 18.5)
    }

    "use semantic score as display score when lexical score is absent" in {
      val document = variantDocument(6)

      val response = adapt(candidate(document, lexicalScore = None, semanticScore = Some(0.91))).response

      assert(response.variantCarousel.head.score == 0.91)
    }

    "use zero display score when lexical and semantic scores are absent" in {
      val document = variantDocument(7)

      val response = adapt(candidate(document, lexicalScore = None, semanticScore = None)).response

      assert(response.variantCarousel.head.score == 0.0)
    }

    "not reorder candidates by display score" in {
      val first = variantDocument(8)
      val second = variantDocument(9)
      val third = variantDocument(10)

      val response = adapt(
        candidate(first, lexicalScore = Some(0.1), semanticScore = None),
        candidate(second, lexicalScore = None, semanticScore = Some(0.9)),
        candidate(third, lexicalScore = Some(0.5), semanticScore = Some(0.99)),
      ).response

      assert(response.variantCarousel.map(_.variantId) == List(first.variantId, second.variantId, third.variantId))
      assert(response.variantCarousel.map(_.score) == List(0.1, 0.9, 0.5))
    }

    "use lexical score for overlap candidates under LexicalThenSemantic" in {
      val document = variantDocument(11)

      val response = adapt(candidate(document, lexicalScore = Some(11.0), semanticScore = Some(0.88))).response

      assert(response.variantCarousel.head.score == 11.0)
    }

    "keep provider carousel empty" in {
      val response = adapt(candidate(variantDocument(12), lexicalScore = Some(3.0), semanticScore = None)).response

      assert(response.providerCarousel.isEmpty)
    }

    "keep service intent carousel empty" in {
      val response = adapt(candidate(variantDocument(13), lexicalScore = Some(3.0), semanticScore = None)).response

      assert(response.serviceIntentCarousel.isEmpty)
    }

    "keep facets empty" in {
      val response = adapt(candidate(variantDocument(14), lexicalScore = Some(3.0), semanticScore = None)).response

      assert(response.facets.isEmpty)
    }

    "keep inferred filters empty" in {
      val response = adapt(candidate(variantDocument(15), lexicalScore = Some(3.0), semanticScore = None)).response

      assert(response.inferredFilters.isEmpty)
    }

    "mark provider service facets and inferred filters suppressed in diagnostics" in {
      val result = adapt(
        candidate(variantDocument(16), lexicalScore = Some(3.0), semanticScore = None),
        candidate(variantDocument(17), lexicalScore = None, semanticScore = Some(0.67)),
      )

      assert(result.diagnostics.inputCandidateCount == 2)
      assert(result.diagnostics.variantResultCount == 2)
      assert(result.diagnostics.providerCarouselSuppressed)
      assert(result.diagnostics.serviceIntentCarouselSuppressed)
      assert(result.diagnostics.facetsSuppressed)
      assert(result.diagnostics.inferredFiltersSuppressed)
    }

    "copy safe variant fields from VariantSearchDocument" in {
      val document = variantDocument(18)

      val result = adapt(candidate(document, lexicalScore = Some(4.2), semanticScore = Some(0.7))).response.variantCarousel.head

      assert(result.variantId == document.variantId)
      assert(result.masterServiceOfferId == document.masterServiceOfferId)
      assert(result.masterLocationId == document.masterLocationId)
      assert(result.masterId == document.masterId)
      assert(result.serviceId == document.serviceId)
      assert(result.categoryId == document.categoryId)
      assert(result.serviceName == document.serviceName)
      assert(result.categoryName == document.categoryName)
      assert(result.masterName == document.masterName)
      assert(result.locationName == document.locationName)
      assert(result.address == document.address)
      assert(result.lat == document.lat)
      assert(result.lon == document.lon)
      assert(result.priceFrom == document.priceFrom)
      assert(result.priceTo == document.priceTo)
      assert(result.durationMin == document.durationMin)
      assert(result.enumAttributes == document.enumAttributes)
      assert(result.booleanAttributes == document.booleanAttributes)
      assert(result.intAttributes == document.intAttributes)
      assert(result.bigDecimalAttributes == document.bigDecimalAttributes)
      assert(result.distanceKm.isEmpty)
    }
  }

  private def adapt(candidates: BeautyQHybridProjectedVariantCandidate*) =
    BeautyQHybridResponseAdapter.variantOnlyResponse(
      BeautyQHybridVariantProjectionResult(
        candidates = candidates.toList,
        diagnostics = BeautyQHybridVariantProjectionDiagnostics(
          inputCandidateCount = candidates.size,
          projectedCandidateCount = candidates.size,
          missingDocumentIds = Nil,
        ),
      )
    )

  private def candidate(
    document: VariantSearchDocument,
    lexicalScore: Option[Double],
    semanticScore: Option[Double],
  ): BeautyQHybridProjectedVariantCandidate =
    BeautyQHybridProjectedVariantCandidate(
      document = document,
      lexicalScore = lexicalScore,
      semanticScore = semanticScore,
      sources = Set(Lexical, Semantic).filter {
        case Lexical => lexicalScore.nonEmpty
        case Semantic => semanticScore.nonEmpty
      },
    )

  private def variantDocument(index: Int): VariantSearchDocument =
    VariantSearchDocument(
      variantId = variantId(index),
      masterServiceOfferId = variantId(index + 100),
      masterLocationId = variantId(index + 200),
      masterId = variantId(index + 300),
      serviceId = variantId(index + 400),
      categoryId = variantId(index + 500),
      serviceName = s"Service $index",
      categoryName = s"Category $index",
      masterName = s"Master $index",
      locationName = s"Location $index",
      address = s"Main street $index",
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
      serviceText = s"service $index",
      attributeText = "coverage gel with removal",
      providerText = s"master $index location $index",
      locationText = s"location $index main street $index",
    )

  private def variantId(value: Int): MasterServiceOfferVariantId =
    UUID.fromString(f"00000000-0000-0000-0000-$value%012d")
}
