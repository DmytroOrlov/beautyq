package leaderboard.search

import java.util.UUID

import leaderboard.model.MasterServiceOfferVariantId
import leaderboard.search.document.VariantSearchDocument
import leaderboard.search.dsl.SearchGeoPoint
import leaderboard.search.hybrid.BeautyQHybridCandidateSource.{Lexical, Semantic}
import leaderboard.search.hybrid.{
  BeautyQHybridProjectedVariantCandidate,
  BeautyQHybridProviderServiceProjection,
  BeautyQHybridVariantProjectionDiagnostics,
  BeautyQHybridVariantProjectionResult,
}
import org.scalatest.wordspec.AnyWordSpec

final class BeautyQHybridProviderServiceProjectionSpec extends AnyWordSpec {
  "BeautyQ hybrid provider/service projection" should {
    "group providers by masterLocationId" in {
      val first = variantDocument(1, masterLocationIndex = 10)
      val second = variantDocument(2, masterLocationIndex = 10)
      val third = variantDocument(3, masterLocationIndex = 11)

      val result = project(candidate(first), candidate(second), candidate(third))

      assert(result.providerCandidates.map(_.masterLocationId) == List(first.masterLocationId, third.masterLocationId))
      assert(result.providerCandidates.map(_.matchingVariantCount) == List(2, 1))
    }

    "group service intents by serviceId" in {
      val first = variantDocument(4, serviceIndex = 20)
      val second = variantDocument(5, serviceIndex = 21)
      val third = variantDocument(6, serviceIndex = 20)

      val result = project(candidate(first), candidate(second), candidate(third))

      assert(result.serviceIntentCandidates.map(_.serviceId) == List(first.serviceId, second.serviceId))
      assert(result.serviceIntentCandidates.map(_.matchingVariantCount) == List(2, 1))
    }

    "keep provider group order from first variant occurrence" in {
      val first = variantDocument(7, masterLocationIndex = 31)
      val second = variantDocument(8, masterLocationIndex = 30)
      val third = variantDocument(9, masterLocationIndex = 31)

      val result = project(candidate(first), candidate(second), candidate(third))

      assert(result.providerCandidates.map(_.masterLocationId) == List(first.masterLocationId, second.masterLocationId))
    }

    "keep service group order from first variant occurrence" in {
      val first = variantDocument(10, serviceIndex = 41)
      val second = variantDocument(11, serviceIndex = 40)
      val third = variantDocument(12, serviceIndex = 41)

      val result = project(candidate(first), candidate(second), candidate(third))

      assert(result.serviceIntentCandidates.map(_.serviceId) == List(first.serviceId, second.serviceId))
    }

    "use the first group candidate for representative display score" in {
      val first = variantDocument(13, masterLocationIndex = 50, serviceIndex = 60)
      val second = variantDocument(14, masterLocationIndex = 50, serviceIndex = 60)

      val result = project(
        candidate(first, lexicalScore = Some(0.1), semanticScore = None),
        candidate(second, lexicalScore = Some(99.0), semanticScore = Some(0.99)),
      )

      assert(result.providerCandidates.head.representativeDisplayScore == 0.1)
      assert(result.serviceIntentCandidates.head.representativeDisplayScore == 0.1)
    }

    "use lexical score before semantic score for representative display score" in {
      val document = variantDocument(15)

      val result = project(candidate(document, lexicalScore = Some(12.0), semanticScore = Some(0.87)))

      assert(result.providerCandidates.head.representativeDisplayScore == 12.0)
      assert(result.serviceIntentCandidates.head.representativeDisplayScore == 12.0)
    }

    "fall back to semantic score when lexical score is absent" in {
      val document = variantDocument(16)

      val result = project(candidate(document, lexicalScore = None, semanticScore = Some(0.91)))

      assert(result.providerCandidates.head.representativeDisplayScore == 0.91)
      assert(result.serviceIntentCandidates.head.representativeDisplayScore == 0.91)
    }

    "not sort groups by score" in {
      val first = variantDocument(17, masterLocationIndex = 70, serviceIndex = 80)
      val second = variantDocument(18, masterLocationIndex = 71, serviceIndex = 81)
      val third = variantDocument(19, masterLocationIndex = 72, serviceIndex = 82)

      val result = project(
        candidate(first, lexicalScore = Some(0.2), semanticScore = None),
        candidate(second, lexicalScore = Some(99.0), semanticScore = None),
        candidate(third, lexicalScore = None, semanticScore = Some(0.9)),
      )

      assert(result.providerCandidates.map(_.masterLocationId) == List(first.masterLocationId, second.masterLocationId, third.masterLocationId))
      assert(result.serviceIntentCandidates.map(_.serviceId) == List(first.serviceId, second.serviceId, third.serviceId))
      assert(result.providerCandidates.map(_.representativeDisplayScore) == List(0.2, 99.0, 0.9))
    }

    "union sources across group candidates" in {
      val first = variantDocument(20, masterLocationIndex = 90, serviceIndex = 100)
      val second = variantDocument(21, masterLocationIndex = 90, serviceIndex = 100)

      val result = project(
        candidate(first, lexicalScore = Some(4.0), semanticScore = None),
        candidate(second, lexicalScore = None, semanticScore = Some(0.76)),
      )

      assert(result.providerCandidates.head.sources == Set(Lexical, Semantic))
      assert(result.serviceIntentCandidates.head.sources == Set(Lexical, Semantic))
    }

    "count unique matching variants" in {
      val first = variantDocument(22, masterLocationIndex = 110, serviceIndex = 120)
      val duplicate = first.copy(serviceName = "Updated duplicate fixture")
      val second = variantDocument(23, masterLocationIndex = 110, serviceIndex = 120)

      val result = project(candidate(first), candidate(duplicate), candidate(second))

      assert(result.providerCandidates.head.matchingVariantCount == 2)
      assert(result.serviceIntentCandidates.head.matchingVariantCount == 2)
    }

    "preserve policy order and dedupe sample matching variant ids" in {
      val first = variantDocument(24, masterLocationIndex = 130)
      val second = variantDocument(25, masterLocationIndex = 130)
      val duplicateFirst = first.copy(masterName = "Duplicate fixture")

      val result = project(candidate(first), candidate(second), candidate(duplicateFirst))

      assert(result.providerCandidates.head.sampleMatchingVariantIds == List(first.variantId, second.variantId))
    }

    "report input provider and service diagnostics" in {
      val first = variantDocument(26, masterLocationIndex = 140, serviceIndex = 150)
      val second = variantDocument(27, masterLocationIndex = 141, serviceIndex = 150)
      val third = variantDocument(28, masterLocationIndex = 140, serviceIndex = 151)

      val result = project(candidate(first), candidate(second), candidate(third))

      assert(result.diagnostics.inputCandidateCount == 3)
      assert(result.diagnostics.providerCandidateCount == 2)
      assert(result.diagnostics.serviceIntentCandidateCount == 2)
    }
  }

  private def project(candidates: BeautyQHybridProjectedVariantCandidate*) =
    BeautyQHybridProviderServiceProjection.project(
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
    lexicalScore: Option[Double] = Some(1.0),
    semanticScore: Option[Double] = None,
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

  private def variantDocument(
    index: Int,
    masterLocationIndex: Int = -1,
    serviceIndex: Int = -1,
  ): VariantSearchDocument = {
    val locationIndex = if (masterLocationIndex >= 0) masterLocationIndex else index + 200
    val resolvedServiceIndex = if (serviceIndex >= 0) serviceIndex else index + 400
    val categoryIndex = resolvedServiceIndex + 100
    val masterIndex = locationIndex + 1000

    VariantSearchDocument(
      variantId = variantId(index),
      masterServiceOfferId = variantId(index + 100),
      masterLocationId = variantId(locationIndex),
      masterId = variantId(masterIndex),
      serviceId = variantId(resolvedServiceIndex),
      categoryId = variantId(categoryIndex),
      serviceName = s"Service $resolvedServiceIndex",
      categoryName = s"Category $categoryIndex",
      masterName = s"Master $masterIndex",
      locationName = s"Location $locationIndex",
      address = s"Main street $locationIndex",
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
      allText = s"service $resolvedServiceIndex category master location",
      serviceText = s"service $resolvedServiceIndex",
      attributeText = "coverage gel with removal",
      providerText = s"master $masterIndex location $locationIndex",
      locationText = s"location $locationIndex main street $locationIndex",
    )
  }

  private def variantId(value: Int): MasterServiceOfferVariantId =
    UUID.fromString(f"00000000-0000-0000-0000-$value%012d")
}
