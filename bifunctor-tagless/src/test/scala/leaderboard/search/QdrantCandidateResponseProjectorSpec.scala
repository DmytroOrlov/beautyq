package leaderboard.search

import leaderboard.search.dsl.{BeautyQSearchPresentation, CarouselLimit, BeautySearchSpecV1}
import leaderboard.search.document.{BeautyQSearchCatalogSnapshot, BeautyQVariantSearchDocumentMaterialization}
import leaderboard.search.qdrant.{QdrantCandidateAssembler, QdrantCandidateAssembly, QdrantCandidateResponseProjector}
import leaderboard.search.semantic.SemanticCandidateHit
import leaderboard.seed.BeautyQSeedLoader
import org.scalatest.wordspec.AnyWordSpec

final class QdrantCandidateResponseProjectorSpec extends AnyWordSpec {
  private val seedData = new BeautyQSeedLoader.ResourceLoader().load() match {
    case Right(value) => value
    case Left(error) => throw new RuntimeException(error.message)
  }

  private val snapshot = BeautyQSearchCatalogSnapshot(
    categories                 = seedData.categories,
    services                   = seedData.services,
    serviceVariantSchemas      = seedData.serviceVariantSchemas,
    masters                    = seedData.masters,
    masterLocations            = seedData.masterLocations,
    masterServiceOffers        = seedData.masterServiceOffers,
    masterServiceOfferVariants = seedData.masterServiceOfferVariants,
  )
  private val documents = BeautyQVariantSearchDocumentMaterialization.project(snapshot) match {
    case Right(value) => value
    case Left(error) => throw new RuntimeException(error.message)
  }

  "Qdrant candidate response projector" should {
    "project empty candidate assembly to an empty safe response" in {
      val response = QdrantCandidateResponseProjector.project(
        BeautySearchSpecV1.spec,
        UserSearchInput(query = "test", userLat = None, userLon = None, limit = 10),
        QdrantCandidateAssembly(
          variantCandidates = Nil,
          providerCandidates = Nil,
          serviceCandidates = Nil,
        ),
      )

      assert(response.variantCarousel.isEmpty)
      assert(response.providerCarousel.isEmpty)
      assert(response.serviceIntentCarousel.isEmpty)
      assert(response.facets.isEmpty)
      assert(response.inferredFilters.isEmpty)
    }

    "preserve Qdrant candidate order and scores in variant carousel" in {
      val docs = documents.take(3)
      val hits = List(
        SemanticCandidateHit(docs(1).variantId, 0.21),
        SemanticCandidateHit(docs(0).variantId, 0.84),
        SemanticCandidateHit(docs(2).variantId, 0.53),
      )

      val response = QdrantCandidateResponseProjector.project(
        BeautySearchSpecV1.spec,
        UserSearchInput(query = "test", userLat = None, userLon = None, limit = 10),
        assembleQdrantCandidates(hits),
      )

      assert(response.variantCarousel.map(_.variantId) == List(docs(1).variantId, docs(0).variantId, docs(2).variantId))
      assert(response.variantCarousel.map(_.score) == List(0.21, 0.84, 0.53))
    }

    "copy safe variant fields from VariantSearchDocument" in {
      val document = documents.head

      val response = QdrantCandidateResponseProjector.project(
        BeautySearchSpecV1.spec,
        UserSearchInput(query = "test", userLat = None, userLon = None, limit = 10),
        assembleQdrantCandidates(List(SemanticCandidateHit(document.variantId, 0.77))),
      )

      val result = response.variantCarousel.head
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

    "project provider and service carousels from assembly groups" in {
      val providerGroup = providerGroupDocuments
      val serviceGroup = serviceGroupDocuments
      val outsideProviderGroup = documents.find(_.masterLocationId != providerGroup.head.masterLocationId).get
      val outsideServiceGroup = documents.find(_.serviceId != serviceGroup.head.serviceId).get
      val hits = List(
        SemanticCandidateHit(providerGroup(1).variantId, 0.95),
        SemanticCandidateHit(outsideProviderGroup.variantId, 0.90),
        SemanticCandidateHit(providerGroup.head.variantId, 0.85),
        SemanticCandidateHit(serviceGroup.head.variantId, 0.80),
        SemanticCandidateHit(outsideServiceGroup.variantId, 0.70),
      )

      val assembly = assembleQdrantCandidates(hits)
      val response = QdrantCandidateResponseProjector.project(
        BeautySearchSpecV1.spec,
        UserSearchInput(query = "test", userLat = None, userLon = None, limit = 10),
        assembly,
      )

      assert(response.providerCarousel.map(_.masterLocationId) == assembly.providerCandidates.map(_.masterLocationId))
      assert(response.providerCarousel.map(_.bestScore) == assembly.providerCandidates.map(_.bestScore))
      assert(response.providerCarousel.map(_.matchingVariantCount) == assembly.providerCandidates.map(_.count))
      assert(response.serviceIntentCarousel.map(_.serviceId) == assembly.serviceCandidates.map(_.serviceId))
      assert(response.serviceIntentCarousel.map(_.bestScore) == assembly.serviceCandidates.map(_.bestScore))
      assert(response.serviceIntentCarousel.map(_.matchingVariantCount) == assembly.serviceCandidates.map(_.count))
    }

    "respect carousel size limits and keep facets and inferred filters empty" in {
      val docs = documents.take(4)
      val hits = List(
        SemanticCandidateHit(docs(0).variantId, 0.91),
        SemanticCandidateHit(docs(1).variantId, 0.81),
        SemanticCandidateHit(docs(2).variantId, 0.71),
        SemanticCandidateHit(docs(3).variantId, 0.61),
      )
      val spec = BeautySearchSpecV1.spec.copy(
        carouselSpec = BeautySearchSpecV1.spec.carouselSpec.copy(
          limits = List(
            CarouselLimit(BeautyQSearchPresentation.CarouselLimits.Variant, 2),
            CarouselLimit(BeautyQSearchPresentation.CarouselLimits.Provider, 1),
            CarouselLimit(BeautyQSearchPresentation.CarouselLimits.ServiceIntent, 1),
          ),
        )
      )

      val response = QdrantCandidateResponseProjector.project(
        spec,
        UserSearchInput(query = "test", userLat = None, userLon = None, limit = 3),
        assembleQdrantCandidates(hits),
      )

      assert(response.variantCarousel.size == 2)
      assert(response.variantCarousel.size == math.min(3, BeautyQSearchPresentation.variantLimit(spec.carouselSpec).getOrElse(fail("expected variant limit"))))
      assert(response.providerCarousel.size == 1)
      assert(response.providerCarousel.size == BeautyQSearchPresentation.providerLimit(spec.carouselSpec).getOrElse(fail("expected provider limit")))
      assert(response.serviceIntentCarousel.size == 1)
      assert(response.serviceIntentCarousel.size == BeautyQSearchPresentation.serviceIntentLimit(spec.carouselSpec).getOrElse(fail("expected service-intent limit")))
      assert(response.facets.isEmpty)
      assert(response.inferredFilters.isEmpty)
      assert(response.variantCarousel.forall(_.distanceKm.isEmpty))
      assert(response.providerCarousel.forall(_.distanceKm.isEmpty))
    }
  }

  private def assembleQdrantCandidates(hits: List[SemanticCandidateHit]) =
    QdrantCandidateAssembler.assemble(hits, documents)

  private def providerGroupDocuments =
    documents.groupBy(_.masterLocationId).values.find(_.size >= 2).get

  private def serviceGroupDocuments =
    documents.groupBy(_.serviceId).values.find(_.size >= 2).get
}
