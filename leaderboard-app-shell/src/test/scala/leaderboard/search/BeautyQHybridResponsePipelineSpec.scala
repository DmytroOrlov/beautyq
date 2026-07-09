package leaderboard.search

import java.util.UUID

import leaderboard.model.{MasterId, MasterLocationId, MasterServiceOfferId, MasterServiceOfferVariantId, ServiceId}
import leaderboard.model.Category.CategoryId
import leaderboard.search.document.VariantSearchDocument
import leaderboard.search.dsl.SearchGeoPoint
import leaderboard.search.hybrid.{BeautyQHybridResponseCarouselLimits, BeautyQHybridResponsePipeline, HybridDocumentRetrievalResult}
import leaderboard.search.lexical.LexicalDocumentHit
import leaderboard.search.semantic.SemanticDocumentHit
import org.scalatest.wordspec.AnyWordSpec

final class BeautyQHybridResponsePipelineSpec extends AnyWordSpec {
  "BeautyQ hybrid response pipeline" should {
    "build a BeautySearchResponse from lexical and semantic hits" in {
      val lexical = variantDocument(1)
      val semantic = variantDocument(2)

      val result = project(
        lexicalHits = List(LexicalDocumentHit(lexical.variantId, 12.0)),
        semanticHits = List(SemanticDocumentHit(semantic.variantId, 0.82)),
        documents = List(lexical, semantic),
      )

      assert(result.response.variantCarousel.map(_.variantId) == List(lexical.variantId, semantic.variantId))
      assert(result.response.variantCarousel.map(_.score) == List(12.0, 0.82))
    }

    "preserve lexical-first order in variant carousel" in {
      val firstLexical = variantDocument(3)
      val secondLexical = variantDocument(4)
      val firstSemantic = variantDocument(5)

      val result = project(
        lexicalHits = List(
          LexicalDocumentHit(firstLexical.variantId, 1.0),
          LexicalDocumentHit(secondLexical.variantId, 99.0),
        ),
        semanticHits = List(SemanticDocumentHit(firstSemantic.variantId, 0.95)),
        documents = List(firstLexical, secondLexical, firstSemantic),
      )

      assert(result.response.variantCarousel.map(_.variantId) == List(firstLexical.variantId, secondLexical.variantId, firstSemantic.variantId))
    }

    "append semantic-only candidates after lexical candidates" in {
      val lexical = variantDocument(6)
      val semanticFirst = variantDocument(7)
      val semanticSecond = variantDocument(8)

      val result = project(
        lexicalHits = List(LexicalDocumentHit(lexical.variantId, 3.0)),
        semanticHits = List(
          SemanticDocumentHit(semanticFirst.variantId, 0.9),
          SemanticDocumentHit(semanticSecond.variantId, 0.8),
        ),
        documents = List(lexical, semanticFirst, semanticSecond),
      )

      assert(result.response.variantCarousel.map(_.variantId) == List(lexical.variantId, semanticFirst.variantId, semanticSecond.variantId))
    }

    "represent overlap once" in {
      val overlap = variantDocument(9)

      val result = project(
        lexicalHits = List(LexicalDocumentHit(overlap.variantId, 8.0)),
        semanticHits = List(SemanticDocumentHit(overlap.variantId, 0.91)),
        documents = List(overlap),
      )

      assert(result.response.variantCarousel.map(_.variantId) == List(overlap.variantId))
      assert(result.response.variantCarousel.head.score == 8.0)
      assert(result.diagnostics.policy.overlapCount == 1)
    }

    "populate provider carousel through existing group projection" in {
      val first = variantDocument(10, masterLocationIndex = 1000)
      val second = variantDocument(11, masterLocationIndex = 1000)
      val other = variantDocument(12, masterLocationIndex = 1001)

      val result = project(
        lexicalHits = List(
          LexicalDocumentHit(first.variantId, 7.0),
          LexicalDocumentHit(second.variantId, 6.0),
        ),
        semanticHits = List(SemanticDocumentHit(other.variantId, 0.85)),
        documents = List(first, second, other),
      )

      assert(result.response.providerCarousel.map(_.masterLocationId) == List(first.masterLocationId, other.masterLocationId))
      assert(result.response.providerCarousel.map(_.matchingVariantCount) == List(2, 1))
      assert(result.response.providerCarousel.head.sampleMatchingVariantIds == List(first.variantId, second.variantId))
    }

    "populate service intent carousel through existing group projection" in {
      val first = variantDocument(13, serviceIndex = 2000)
      val second = variantDocument(14, serviceIndex = 2001)
      val third = variantDocument(15, serviceIndex = 2000)

      val result = project(
        lexicalHits = List(
          LexicalDocumentHit(first.variantId, 5.0),
          LexicalDocumentHit(second.variantId, 4.0),
        ),
        semanticHits = List(SemanticDocumentHit(third.variantId, 0.77)),
        documents = List(first, second, third),
      )

      assert(result.response.serviceIntentCarousel.map(_.serviceId) == List(first.serviceId, second.serviceId))
      assert(result.response.serviceIntentCarousel.map(_.matchingVariantCount) == List(2, 1))
    }

    "keep facets empty" in {
      val document = variantDocument(16)

      val result = project(
        lexicalHits = List(LexicalDocumentHit(document.variantId, 1.0)),
        semanticHits = Nil,
        documents = List(document),
      )

      assert(result.response.facets.isEmpty)
    }

    "keep inferred filters empty" in {
      val document = variantDocument(17)

      val result = project(
        lexicalHits = List(LexicalDocumentHit(document.variantId, 1.0)),
        semanticHits = Nil,
        documents = List(document),
      )

      assert(result.response.inferredFilters.isEmpty)
    }

    "propagate missing document failure" in {
      val missingId = MasterServiceOfferVariantId(indexUuid(18))

      val result = BeautyQHybridResponsePipeline.projectResponse(
        retrieval = HybridDocumentRetrievalResult.fromHits(
          lexicalHits = List(LexicalDocumentHit(missingId, 1.0)),
          semanticHits = Nil,
        ),
        documentsByVariantId = Map.empty,
        limits = defaultLimits,
      )

      assert(result.isLeft)
      assert(result.left.exists(_.message.contains("Missing VariantSearchDocument")))
      assert(result.left.exists(_.message.contains(missingId.toString)))
    }

    "prevent response construction when a document is missing" in {
      val present = variantDocument(19)
      val missingId = MasterServiceOfferVariantId(indexUuid(20))

      val result = BeautyQHybridResponsePipeline.projectResponse(
        retrieval = HybridDocumentRetrievalResult.fromHits(
          lexicalHits = List(LexicalDocumentHit(present.variantId, 3.0)),
          semanticHits = List(SemanticDocumentHit(missingId, 0.92)),
        ),
        documentsByVariantId = Map(present.variantId -> present),
        limits = defaultLimits,
      )

      assert(result.isLeft)
    }

    "include policy variant provider service and response adapter diagnostics" in {
      val lexical = variantDocument(21)
      val semantic = variantDocument(22)

      val result = project(
        lexicalHits = List(LexicalDocumentHit(lexical.variantId, 2.0)),
        semanticHits = List(SemanticDocumentHit(semantic.variantId, 0.72)),
        documents = List(lexical, semantic),
      )

      assert(result.diagnostics.policy.lexicalInputCount == 1)
      assert(result.diagnostics.policy.semanticInputCount == 1)
      assert(result.diagnostics.variantProjection.inputCandidateCount == 2)
      assert(result.diagnostics.variantProjection.projectedCandidateCount == 2)
      assert(result.diagnostics.variantProjection.missingDocumentIds == Nil)
      assert(result.diagnostics.providerServiceProjection.inputCandidateCount == 2)
      assert(result.diagnostics.providerServiceProjection.providerCandidateCount == 2)
      assert(result.diagnostics.providerServiceProjection.serviceIntentCandidateCount == 2)
      assert(result.diagnostics.responseAdapter.inputCandidateCount == 2)
      assert(result.diagnostics.responseAdapter.variantResultCount == 2)
      assert(!result.diagnostics.responseAdapter.providerCarouselSuppressed)
      assert(!result.diagnostics.responseAdapter.serviceIntentCarouselSuppressed)
      assert(result.diagnostics.responseAdapter.facetsSuppressed)
      assert(result.diagnostics.responseAdapter.inferredFiltersSuppressed)
    }

    "keep display scores component-defined without reordering results" in {
      val lowLexical = variantDocument(23)
      val highSemantic = variantDocument(24)
      val highLexical = variantDocument(25)

      val result = project(
        lexicalHits = List(
          LexicalDocumentHit(lowLexical.variantId, 0.1),
          LexicalDocumentHit(highLexical.variantId, 50.0),
        ),
        semanticHits = List(SemanticDocumentHit(highSemantic.variantId, 0.99)),
        documents = List(lowLexical, highSemantic, highLexical),
      )

      assert(result.response.variantCarousel.map(_.variantId) == List(lowLexical.variantId, highLexical.variantId, highSemantic.variantId))
      assert(result.response.variantCarousel.map(_.score) == List(0.1, 50.0, 0.99))
      assert(result.response.providerCarousel.map(_.bestScore) == List(0.1, 50.0, 0.99))
      assert(result.response.serviceIntentCarousel.map(_.bestScore) == List(0.1, 50.0, 0.99))
    }

    "pass explicit carousel limits through to the response adapter after policy order is established" in {
      val firstLexical = variantDocument(26, masterLocationIndex = 1000, serviceIndex = 2000)
      val secondLexical = variantDocument(27, masterLocationIndex = 1001, serviceIndex = 2001)
      val semantic = variantDocument(28, masterLocationIndex = 1002, serviceIndex = 2002)

      val result = project(
        lexicalHits = List(
          LexicalDocumentHit(firstLexical.variantId, 0.1),
          LexicalDocumentHit(secondLexical.variantId, 99.0),
        ),
        semanticHits = List(SemanticDocumentHit(semantic.variantId, 0.95)),
        documents = List(firstLexical, secondLexical, semantic),
        limits = BeautyQHybridResponseCarouselLimits(variantSize = 2, providerSize = 1, serviceIntentSize = 2),
      )

      assert(result.response.variantCarousel.map(_.variantId) == List(firstLexical.variantId, secondLexical.variantId))
      assert(result.response.variantCarousel.map(_.score) == List(0.1, 99.0))
      assert(result.response.providerCarousel.map(_.masterLocationId) == List(firstLexical.masterLocationId))
      assert(result.response.providerCarousel.map(_.bestScore) == List(0.1))
      assert(result.response.serviceIntentCarousel.map(_.serviceId) == List(firstLexical.serviceId, secondLexical.serviceId))
      assert(result.response.serviceIntentCarousel.map(_.bestScore) == List(0.1, 99.0))
    }

    "return empty carousels when explicit pipeline limits are zero" in {
      val document = variantDocument(29)

      val result = project(
        lexicalHits = List(LexicalDocumentHit(document.variantId, 1.0)),
        semanticHits = Nil,
        documents = List(document),
        limits = BeautyQHybridResponseCarouselLimits(variantSize = 0, providerSize = 0, serviceIntentSize = 0),
      )

      assert(result.response.variantCarousel.isEmpty)
      assert(result.response.providerCarousel.isEmpty)
      assert(result.response.serviceIntentCarousel.isEmpty)
    }

    "fail with missing document for overlapping lexical+semantic id" in {
      val missingId = MasterServiceOfferVariantId(indexUuid(30))

      val result = BeautyQHybridResponsePipeline.projectResponse(
        retrieval = HybridDocumentRetrievalResult.fromHits(
          lexicalHits = List(LexicalDocumentHit(missingId, 5.0)),
          semanticHits = List(SemanticDocumentHit(missingId, 0.85)),
        ),
        documentsByVariantId = Map.empty,
        limits = defaultLimits,
      )

      assert(result.isLeft)
      assert(result.left.exists(_.message.contains("Missing VariantSearchDocument")))
      assert(result.left.exists(_.message.contains(missingId.toString)))
    }

    "return empty carousels for empty retrieval result" in {
      val result = BeautyQHybridResponsePipeline.projectResponse(
        retrieval = HybridDocumentRetrievalResult.fromHits(
          lexicalHits = Nil,
          semanticHits = Nil,
        ),
        documentsByVariantId = Map.empty,
        limits = defaultLimits,
      )

      result match {
        case Right(r) =>
          assert(r.response.variantCarousel.isEmpty)
          assert(r.response.providerCarousel.isEmpty)
          assert(r.response.serviceIntentCarousel.isEmpty)
          assert(r.response.facets.isEmpty)
          assert(r.response.inferredFilters.isEmpty)
          assert(r.diagnostics.policy.lexicalInputCount == 0)
          assert(r.diagnostics.policy.semanticInputCount == 0)
          assert(r.diagnostics.policy.overlapCount == 0)
          assert(r.diagnostics.variantProjection.inputCandidateCount == 0)
          assert(r.diagnostics.variantProjection.projectedCandidateCount == 0)
          assert(r.diagnostics.variantProjection.missingDocumentIds.isEmpty)
        case Left(error) => fail(error.message)
      }
    }

    "truncate variants after lexical-first/semantic-supplement order" in {
      val lexical1 = variantDocument(31, masterLocationIndex = 1000, serviceIndex = 2000)
      val lexical2 = variantDocument(32, masterLocationIndex = 1001, serviceIndex = 2001)
      val semantic1 = variantDocument(33, masterLocationIndex = 1002, serviceIndex = 2002)
      val semantic2 = variantDocument(34, masterLocationIndex = 1003, serviceIndex = 2003)

      val result = project(
        lexicalHits = List(
          LexicalDocumentHit(lexical1.variantId, 1.0),
          LexicalDocumentHit(lexical2.variantId, 2.0),
        ),
        semanticHits = List(
          SemanticDocumentHit(semantic1.variantId, 0.9),
          SemanticDocumentHit(semantic2.variantId, 0.8),
        ),
        documents = List(lexical1, lexical2, semantic1, semantic2),
        limits = BeautyQHybridResponseCarouselLimits(variantSize = 3, providerSize = 10, serviceIntentSize = 10),
      )

      assert(result.response.variantCarousel.map(_.variantId) == List(lexical1.variantId, lexical2.variantId, semantic1.variantId))
      assert(result.response.variantCarousel.map(_.score) == List(1.0, 2.0, 0.9))
    }

    "truncate provider/service carousels with explicit limits" in {
      val lexical1 = variantDocument(35, masterLocationIndex = 1000, serviceIndex = 2000)
      val lexical2 = variantDocument(36, masterLocationIndex = 1001, serviceIndex = 2001)
      val semantic1 = variantDocument(37, masterLocationIndex = 1002, serviceIndex = 2002)

      val result = project(
        lexicalHits = List(
          LexicalDocumentHit(lexical1.variantId, 1.0),
          LexicalDocumentHit(lexical2.variantId, 2.0),
        ),
        semanticHits = List(
          SemanticDocumentHit(semantic1.variantId, 0.9),
        ),
        documents = List(lexical1, lexical2, semantic1),
        limits = BeautyQHybridResponseCarouselLimits(variantSize = 3, providerSize = 1, serviceIntentSize = 2),
      )

      assert(result.response.variantCarousel.map(_.variantId) == List(lexical1.variantId, lexical2.variantId, semantic1.variantId))
      assert(result.response.providerCarousel.map(_.masterLocationId) == List(lexical1.masterLocationId))
      assert(result.response.serviceIntentCarousel.map(_.serviceId) == List(lexical1.serviceId, lexical2.serviceId))
    }
  }

  private def project(
    lexicalHits: List[LexicalDocumentHit[MasterServiceOfferVariantId]],
    semanticHits: List[SemanticDocumentHit[MasterServiceOfferVariantId]],
    documents: List[VariantSearchDocument],
    limits: BeautyQHybridResponseCarouselLimits = defaultLimits,
  ) =
    BeautyQHybridResponsePipeline.projectResponse(
      retrieval = HybridDocumentRetrievalResult.fromHits(lexicalHits, semanticHits),
      documentsByVariantId = documents.map(document => document.variantId -> document).toMap,
      limits = limits,
    ) match {
      case Right(result) => result
      case Left(error) => fail(error.message)
    }

  private val defaultLimits = BeautyQHybridResponseCarouselLimits(100, 100, 100)

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
      variantId = MasterServiceOfferVariantId(indexUuid(index)),
      masterServiceOfferId = MasterServiceOfferId(indexUuid(index + 100)),
      masterLocationId = MasterLocationId(indexUuid(locationIndex)),
      masterId = MasterId(indexUuid(masterIndex)),
      serviceId = ServiceId(indexUuid(resolvedServiceIndex)),
      categoryId = CategoryId(indexUuid(categoryIndex)),
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

  private def indexUuid(value: Int): UUID =
    UUID.fromString(f"00000000-0000-0000-0000-$value%012d")
}
