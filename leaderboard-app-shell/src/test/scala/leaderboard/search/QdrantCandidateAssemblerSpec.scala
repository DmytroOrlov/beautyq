package leaderboard.search

import leaderboard.model.MasterServiceOfferVariantId
import leaderboard.search.document.{BeautyQSearchCatalogSnapshot, BeautyQVariantSearchDocumentMaterialization}
import leaderboard.search.qdrant.QdrantCandidateAssembler
import leaderboard.search.semantic.SemanticCandidateHit
import leaderboard.seed.BeautyQSeedLoader
import org.scalatest.wordspec.AnyWordSpec

import java.util.UUID

final class QdrantCandidateAssemblerSpec extends AnyWordSpec {
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

  private val unknownVariantId: MasterServiceOfferVariantId = MasterServiceOfferVariantId(UUID.fromString("00000000-0000-0000-0000-000000000001"))

  "Qdrant candidate assembly" should {
    "preserve variant hit order" in {
      val docs = documents.take(3)
      val hits = List(
        SemanticCandidateHit(docs(1).variantId, 0.2),
        SemanticCandidateHit(docs(0).variantId, 0.8),
        SemanticCandidateHit(docs(2).variantId, 0.5),
      )

      val assembly = assembleQdrantCandidates(hits)

      assert(assembly.variantCandidates.map(_.document.variantId) == List(docs(1).variantId, docs(0).variantId, docs(2).variantId))
    }

    "ignore unknown variant ids" in {
      val document = documents.head
      val hits = List(
        SemanticCandidateHit(unknownVariantId, 0.9),
        SemanticCandidateHit(document.variantId, 0.7),
      )

      val assembly = assembleQdrantCandidates(hits)

      assert(assembly.variantCandidates.map(_.document.variantId) == List(document.variantId))
    }

    "deduplicate duplicate variant hits by variantId before joining documents" in {
      val docs = documents.take(3)
      val hits = List(
        SemanticCandidateHit(docs(1).variantId, 0.91),
        SemanticCandidateHit(docs(0).variantId, 0.81),
        SemanticCandidateHit(docs(1).variantId, 0.31),
        SemanticCandidateHit(docs(2).variantId, 0.71),
        SemanticCandidateHit(docs(0).variantId, 0.21),
      )

      val assembly = assembleQdrantCandidates(hits)

      assert(assembly.variantCandidates.map(_.document.variantId) == List(docs(1).variantId, docs(0).variantId, docs(2).variantId))
      assert(assembly.variantCandidates.map(_.score) == List(0.91, 0.81, 0.71))
    }

    "group providers by masterLocationId" in {
      val groupDocuments = providerGroupDocuments
      val hits = List(
        SemanticCandidateHit(groupDocuments(0).variantId, 0.4),
        SemanticCandidateHit(groupDocuments(1).variantId, 0.9),
      )

      val assembly = assembleQdrantCandidates(hits)

      assert(assembly.providerCandidates.size == 1)
      val providerGroup = assembly.providerCandidates.head
      assert(providerGroup.masterLocationId == groupDocuments.head.masterLocationId)
      assert(providerGroup.count == 2)
      assert(providerGroup.bestScore == 0.9)
      assert(providerGroup.variants.map(_.document.variantId) == List(groupDocuments(0).variantId, groupDocuments(1).variantId))
    }

    "count deduplicated variants in provider groups" in {
      val groupDocuments = providerGroupDocuments
      val hits = List(
        SemanticCandidateHit(groupDocuments(0).variantId, 0.4),
        SemanticCandidateHit(groupDocuments(1).variantId, 0.7),
        SemanticCandidateHit(groupDocuments(0).variantId, 0.9),
      )

      val assembly = assembleQdrantCandidates(hits)

      assert(assembly.providerCandidates.size == 1)
      val providerGroup = assembly.providerCandidates.head
      assert(providerGroup.masterLocationId == groupDocuments.head.masterLocationId)
      assert(providerGroup.count == 2)
      assert(providerGroup.bestScore == 0.7)
      assert(providerGroup.variants.map(_.document.variantId) == List(groupDocuments(0).variantId, groupDocuments(1).variantId))
      assert(providerGroup.variants.map(_.score) == List(0.4, 0.7))
    }

    "group services by serviceId" in {
      val groupDocuments = serviceGroupDocuments
      val hits = List(
        SemanticCandidateHit(groupDocuments(0).variantId, 0.1),
        SemanticCandidateHit(groupDocuments(1).variantId, 0.6),
      )

      val assembly = assembleQdrantCandidates(hits)

      assert(assembly.serviceCandidates.size == 1)
      val serviceGroup = assembly.serviceCandidates.head
      assert(serviceGroup.serviceId == groupDocuments.head.serviceId)
      assert(serviceGroup.count == 2)
      assert(serviceGroup.bestScore == 0.6)
      assert(serviceGroup.variants.map(_.document.variantId) == List(groupDocuments(0).variantId, groupDocuments(1).variantId))
    }

    "count deduplicated variants in service groups" in {
      val groupDocuments = serviceGroupDocuments
      val hits = List(
        SemanticCandidateHit(groupDocuments(0).variantId, 0.1),
        SemanticCandidateHit(groupDocuments(1).variantId, 0.6),
        SemanticCandidateHit(groupDocuments(0).variantId, 0.8),
      )

      val assembly = assembleQdrantCandidates(hits)

      assert(assembly.serviceCandidates.size == 1)
      val serviceGroup = assembly.serviceCandidates.head
      assert(serviceGroup.serviceId == groupDocuments.head.serviceId)
      assert(serviceGroup.count == 2)
      assert(serviceGroup.bestScore == 0.6)
      assert(serviceGroup.variants.map(_.document.variantId) == List(groupDocuments(0).variantId, groupDocuments(1).variantId))
      assert(serviceGroup.variants.map(_.score) == List(0.1, 0.6))
    }

    "rank service groups by bestScore descending" in {
      val serviceGroups = documents.groupBy(_.serviceId).values.filter(_.nonEmpty).toList.sortBy(_.head.serviceId.toString)
      assert(serviceGroups.size >= 2)

      val lowGroup = serviceGroups.head
      val highGroup = serviceGroups(1)
      val hits = List(
        SemanticCandidateHit(lowGroup.head.variantId, 0.2),
        SemanticCandidateHit(highGroup.head.variantId, 0.8),
      )

      val assembly = assembleQdrantCandidates(hits)

      assert(assembly.serviceCandidates.head.serviceId == highGroup.head.serviceId)
      assert(assembly.serviceCandidates.head.bestScore == 0.8)
    }

    "rank tied service groups by count descending" in {
      val serviceGroups = documents.groupBy(_.serviceId).values.filter(_.size >= 2).toList.sortBy(_.head.serviceId.toString)
      assert(serviceGroups.size >= 2)

      val firstGroup = serviceGroups.head
      val secondGroup = serviceGroups(1)
      val hits = List(
        SemanticCandidateHit(firstGroup.head.variantId, 0.7),
        SemanticCandidateHit(firstGroup(1).variantId, 0.7),
        SemanticCandidateHit(secondGroup.head.variantId, 0.7),
      )

      val assembly = assembleQdrantCandidates(hits)

      assert(assembly.serviceCandidates.head.serviceId == firstGroup.head.serviceId)
      assert(assembly.serviceCandidates.head.count == 2)
      assert(assembly.serviceCandidates(1).serviceId == secondGroup.head.serviceId)
      assert(assembly.serviceCandidates(1).count == 1)
    }

    "rank provider groups by bestScore descending" in {
      val providerGroups = documents.groupBy(_.masterLocationId).values.filter(_.nonEmpty).toList.sortBy(_.head.masterLocationId.toString)
      assert(providerGroups.size >= 2)

      val lowGroup = providerGroups.head
      val highGroup = providerGroups(1)
      val hits = List(
        SemanticCandidateHit(lowGroup.head.variantId, 0.2),
        SemanticCandidateHit(highGroup.head.variantId, 0.8),
      )

      val assembly = assembleQdrantCandidates(hits)

      assert(assembly.providerCandidates.head.masterLocationId == highGroup.head.masterLocationId)
      assert(assembly.providerCandidates.head.bestScore == 0.8)
    }

    "rank tied provider groups by count descending" in {
      val providerGroups = documents.groupBy(_.masterLocationId).values.filter(_.size >= 2).toList.sortBy(_.head.masterLocationId.toString)
      assert(providerGroups.size >= 2)

      val firstGroup = providerGroups.head
      val secondGroup = providerGroups(1)
      val hits = List(
        SemanticCandidateHit(firstGroup.head.variantId, 0.7),
        SemanticCandidateHit(firstGroup(1).variantId, 0.7),
        SemanticCandidateHit(secondGroup.head.variantId, 0.7),
      )

      val assembly = assembleQdrantCandidates(hits)

      assert(assembly.providerCandidates.head.masterLocationId == firstGroup.head.masterLocationId)
      assert(assembly.providerCandidates.head.count == 2)
      assert(assembly.providerCandidates(1).masterLocationId == secondGroup.head.masterLocationId)
      assert(assembly.providerCandidates(1).count == 1)
    }
  }

  private def assembleQdrantCandidates(hits: List[SemanticCandidateHit]) =
    QdrantCandidateAssembler.assemble(hits, documents)

  private def providerGroupDocuments =
    documents.groupBy(_.masterLocationId).values.find(_.size >= 2).get

  private def serviceGroupDocuments =
    documents.groupBy(_.serviceId).values.find(_.size >= 2).get
}
