package leaderboard.search

import io.circe.Json
import leaderboard.model.*
import leaderboard.search.dsl.*
import leaderboard.search.document.{BeautySearchCatalogSnapshot, VariantSearchDocument, VariantSearchDocumentBuilder}
import leaderboard.search.elasticsearch.{ElasticsearchIngestionInterpreter, ElasticsearchMappingInterpreter, ElasticsearchSearchRequestInterpreter}
import leaderboard.search.eval.BeautySearchEvalScorer
import leaderboard.search.inmemory.InMemorySearchBackend
import leaderboard.search.interpreter.SearchEmbeddingTextExtractor
import leaderboard.search.parser.BeautySearchIntentParser
import leaderboard.search.qdrant.{QdrantCandidateAssembler, QdrantCandidateHit, QdrantCandidateResponseProjector, QdrantJsonInterpreter}
import leaderboard.search.routing.{SearchBackendRoute, SearchBackendRouter, SearchRoutingMetadata, SearchRoutingReason, SearchRoutingSignal}
import leaderboard.search.semantic.{SemanticCandidateBackend, VariantSearchDocumentLookup}
import leaderboard.seed.BeautyQSeedLoader
import org.scalatest.wordspec.AnyWordSpec
import zio.{IO, Runtime, Unsafe, ZIO}

import java.util.UUID

final class BeautySearchPureSpec extends AnyWordSpec {
  private val seedData = new BeautyQSeedLoader.ResourceLoader().load() match {
    case Right(value) => value
    case Left(error) => throw new RuntimeException(error.message)
  }

  private val snapshot = BeautySearchCatalogSnapshot.fromSeedData(seedData)
  private val documents = VariantSearchDocumentBuilder.build(snapshot) match {
    case Right(value) => value
    case Left(error) => throw new RuntimeException(error.message)
  }

  private val parser = new BeautySearchIntentParser(BeautySearchSpecV1.spec)

  private val evalSuite = BeautySearchEvalInventory.evalSuite
  private val unknownVariantId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")

  "BeautySearchSpecV1" should {
    "have vector backend disabled by default" in {
      assert(BeautySearchSpecV1.spec.embeddingSpec.isEmpty)
      assert(BeautySearchSpecV1.spec.vectorSearchSpec.isEmpty)
    }

    "include dynamic fields for all AttributeDefinition.all entries" in {
      val paths = BeautySearchSpecV1.spec.variantDocument.fields.map(_.path).toSet

      AttributeDefinition.intDefinitions.foreach { definition =>
        assert(paths.contains(s"intAttributes.${definition.code}"))
      }
      AttributeDefinition.bigDecimalDefinitions.foreach { definition =>
        assert(paths.contains(s"bigDecimalAttributes.${definition.code}"))
      }
      AttributeDefinition.enumDefinitions.foreach { definition =>
        assert(paths.contains(s"enumAttributes.${definition.code}"))
      }
      AttributeDefinition.booleanDefinitions.foreach { definition =>
        assert(paths.contains(s"booleanAttributes.${definition.code}"))
      }
    }

    "keep enum attributes on stringCode values in built documents" in {
      seedData.masterServiceOfferVariants.foreach { variant =>
        val built = documents.find(_.variantId == variant.id).getOrElse(sys.error(s"Missing document for ${variant.id}"))
        val expected = variant.enumAttributes.iterator.collect {
          case (definition: EnumAttributeDefinition[?], value) => definition.code -> value.stringCode
        }.toMap
        assert(built.enumAttributes == expected)
      }
    }
  }

  "vector search DSL" should {
    "construct VectorSearchSpec" in {
      val spec = VectorSearchSpec(
        collectionName = "variants",
        vectorName = "variant-embedding",
        topK = 10,
        scoreThreshold = Some(0.8),
      )

      assert(spec.collectionName == "variants")
      assert(spec.vectorName == "variant-embedding")
      assert(spec.topK == 10)
      assert(spec.scoreThreshold.contains(0.8))
    }

    "construct EmbeddingSpec[VariantSearchDocument]" in {
      val spec = EmbeddingSpec[VariantSearchDocument](
        vectorName = "variant-embedding",
        modelName = "test-model",
        dimension = 384,
        distance = VectorDistance.Cosine,
        sourceTextFieldPaths = List("serviceName", "allText"),
      )

      assert(spec.vectorName == "variant-embedding")
      assert(spec.modelName == "test-model")
      assert(spec.dimension == 384)
      assert(spec.distance == VectorDistance.Cosine)
      assert(spec.sourceTextFieldPaths == List("serviceName", "allText"))
    }
  }

  "SearchEmbeddingTextExtractor" should {
    "extract source text from matching fields" in {
      val document = documents.head
      val spec = BeautySearchSpecV1.spec.copy(
        variantDocument = SearchDocumentSpec[VariantSearchDocument](
          indexName = "synthetic-embedding",
          id = _.variantId.toString,
          fields = List(
            SearchField[VariantSearchDocument](
              path = "first",
              kind = SearchFieldKind.Text,
              extract = doc => Some(SearchValue.Text(doc.serviceName)),
            ),
            SearchField[VariantSearchDocument](
              path = "second",
              kind = SearchFieldKind.Text,
              extract = doc => Some(SearchValue.Text(doc.categoryName)),
            ),
          ),
        ),
        embeddingSpec = Some(EmbeddingSpec[VariantSearchDocument](
          vectorName = "variant-embedding",
          modelName = "test-model",
          dimension = 384,
          distance = VectorDistance.Cosine,
          sourceTextFieldPaths = List("first", "second"),
        )),
      )

      val text = SearchEmbeddingTextExtractor.extract(spec.variantDocument, spec.embeddingSpec.get, document)
      assert(text == s"${document.serviceName} ${document.categoryName}")
    }

    "ignore missing source text fields" in {
      val document = documents.head
      val documentSpec = SearchDocumentSpec[VariantSearchDocument](
        indexName = "synthetic-embedding-missing",
        id = _.variantId.toString,
        fields = List(
          SearchField[VariantSearchDocument](
            path = "present",
            kind = SearchFieldKind.Text,
            extract = doc => Some(SearchValue.Text(doc.serviceName)),
          ),
        ),
      )
      val embeddingSpec = EmbeddingSpec[VariantSearchDocument](
        vectorName = "variant-embedding",
        modelName = "test-model",
        dimension = 384,
        distance = VectorDistance.Cosine,
        sourceTextFieldPaths = List("missing", "present", "also-missing"),
      )

      val text = SearchEmbeddingTextExtractor.extract(documentSpec, embeddingSpec, document)
      assert(text == document.serviceName)
    }

    "ignore None extractor values" in {
      val document = documents.head
      val documentSpec = SearchDocumentSpec[VariantSearchDocument](
        indexName = "synthetic-embedding-none",
        id = _.variantId.toString,
        fields = List(
          SearchField[VariantSearchDocument](
            path = "empty",
            kind = SearchFieldKind.Text,
            extract = _ => None,
          ),
          SearchField[VariantSearchDocument](
            path = "present",
            kind = SearchFieldKind.Text,
            extract = doc => Some(SearchValue.Text(doc.categoryName)),
          ),
        ),
      )
      val embeddingSpec = EmbeddingSpec[VariantSearchDocument](
        vectorName = "variant-embedding",
        modelName = "test-model",
        dimension = 384,
        distance = VectorDistance.Cosine,
        sourceTextFieldPaths = List("empty", "present"),
      )

      val text = SearchEmbeddingTextExtractor.extract(documentSpec, embeddingSpec, document)
      assert(text == document.categoryName)
    }

    "work with BeautySearchSpecV1.variantDocument" in {
      val document = documents.head
      val embeddingSpec = EmbeddingSpec[VariantSearchDocument](
        vectorName = "variant-embedding",
        modelName = "test-model",
        dimension = 384,
        distance = VectorDistance.Cosine,
        sourceTextFieldPaths = List("serviceName", "categoryName"),
      )

      val text = SearchEmbeddingTextExtractor.extract(BeautySearchSpecV1.spec.variantDocument, embeddingSpec, document)
      assert(text == s"${document.serviceName} ${document.categoryName}")
    }
  }

  "Qdrant candidate assembly" should {
    "preserve variant hit order" in {
      val docs = documents.take(3)
      val hits = List(
        QdrantCandidateHit(docs(1).variantId, 0.2),
        QdrantCandidateHit(docs(0).variantId, 0.8),
        QdrantCandidateHit(docs(2).variantId, 0.5),
      )

      val assembly = assembleQdrantCandidates(hits)

      assert(assembly.variantCandidates.map(_.document.variantId) == List(docs(1).variantId, docs(0).variantId, docs(2).variantId))
    }

    "ignore unknown variant ids" in {
      val document = documents.head
      val hits = List(
        QdrantCandidateHit(unknownVariantId, 0.9),
        QdrantCandidateHit(document.variantId, 0.7),
      )

      val assembly = assembleQdrantCandidates(hits)

      assert(assembly.variantCandidates.map(_.document.variantId) == List(document.variantId))
    }

    "group providers by masterLocationId" in {
      val groupDocuments = providerGroupDocuments
      val hits = List(
        QdrantCandidateHit(groupDocuments(0).variantId, 0.4),
        QdrantCandidateHit(groupDocuments(1).variantId, 0.9),
      )

      val assembly = assembleQdrantCandidates(hits)

      assert(assembly.providerCandidates.size == 1)
      val providerGroup = assembly.providerCandidates.head
      assert(providerGroup.masterLocationId == groupDocuments.head.masterLocationId)
      assert(providerGroup.count == 2)
      assert(providerGroup.bestScore == 0.9)
      assert(providerGroup.variants.map(_.document.variantId) == List(groupDocuments(0).variantId, groupDocuments(1).variantId))
    }

    "group services by serviceId" in {
      val groupDocuments = serviceGroupDocuments
      val hits = List(
        QdrantCandidateHit(groupDocuments(0).variantId, 0.1),
        QdrantCandidateHit(groupDocuments(1).variantId, 0.6),
      )

      val assembly = assembleQdrantCandidates(hits)

      assert(assembly.serviceCandidates.size == 1)
      val serviceGroup = assembly.serviceCandidates.head
      assert(serviceGroup.serviceId == groupDocuments.head.serviceId)
      assert(serviceGroup.count == 2)
      assert(serviceGroup.bestScore == 0.6)
      assert(serviceGroup.variants.map(_.document.variantId) == List(groupDocuments(0).variantId, groupDocuments(1).variantId))
    }

    "rank service groups by bestScore descending" in {
      val serviceGroups = documents.groupBy(_.serviceId).values.filter(_.nonEmpty).toList.sortBy(_.head.serviceId.toString)
      assert(serviceGroups.size >= 2)

      val lowGroup = serviceGroups.head
      val highGroup = serviceGroups(1)
      val hits = List(
        QdrantCandidateHit(lowGroup.head.variantId, 0.2),
        QdrantCandidateHit(highGroup.head.variantId, 0.8),
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
        QdrantCandidateHit(firstGroup.head.variantId, 0.7),
        QdrantCandidateHit(firstGroup(1).variantId, 0.7),
        QdrantCandidateHit(secondGroup.head.variantId, 0.7),
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
        QdrantCandidateHit(lowGroup.head.variantId, 0.2),
        QdrantCandidateHit(highGroup.head.variantId, 0.8),
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
        QdrantCandidateHit(firstGroup.head.variantId, 0.7),
        QdrantCandidateHit(firstGroup(1).variantId, 0.7),
        QdrantCandidateHit(secondGroup.head.variantId, 0.7),
      )

      val assembly = assembleQdrantCandidates(hits)

      assert(assembly.providerCandidates.head.masterLocationId == firstGroup.head.masterLocationId)
      assert(assembly.providerCandidates.head.count == 2)
      assert(assembly.providerCandidates(1).masterLocationId == secondGroup.head.masterLocationId)
      assert(assembly.providerCandidates(1).count == 1)
    }
  }

  "Qdrant candidate response projector" should {
    "preserve Qdrant candidate order and scores in variant carousel" in {
      val docs = documents.take(3)
      val hits = List(
        QdrantCandidateHit(docs(1).variantId, 0.21),
        QdrantCandidateHit(docs(0).variantId, 0.84),
        QdrantCandidateHit(docs(2).variantId, 0.53),
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
        assembleQdrantCandidates(List(QdrantCandidateHit(document.variantId, 0.77))),
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
        QdrantCandidateHit(providerGroup(1).variantId, 0.95),
        QdrantCandidateHit(outsideProviderGroup.variantId, 0.90),
        QdrantCandidateHit(providerGroup.head.variantId, 0.85),
        QdrantCandidateHit(serviceGroup.head.variantId, 0.80),
        QdrantCandidateHit(outsideServiceGroup.variantId, 0.70),
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
        QdrantCandidateHit(docs(0).variantId, 0.91),
        QdrantCandidateHit(docs(1).variantId, 0.81),
        QdrantCandidateHit(docs(2).variantId, 0.71),
        QdrantCandidateHit(docs(3).variantId, 0.61),
      )
      val spec = BeautySearchSpecV1.spec.copy(
        carouselSpec = BeautySearchSpecV1.spec.carouselSpec.copy(
          variantSize = 2,
          providerSize = 1,
          serviceIntentSize = 1,
        )
      )

      val response = QdrantCandidateResponseProjector.project(
        spec,
        UserSearchInput(query = "test", userLat = None, userLon = None, limit = 3),
        assembleQdrantCandidates(hits),
      )

      assert(response.variantCarousel.size == 2)
      assert(response.providerCarousel.size == 1)
      assert(response.serviceIntentCarousel.size == 1)
      assert(response.facets.isEmpty)
      assert(response.inferredFilters.isEmpty)
    }
  }

  "semantic candidate runtime seams" should {
    "compose fake semantic hits and fake document lookup through assembler and projector" in {
      val knownDocuments = documents.take(3)
      val hits = List(
        QdrantCandidateHit(knownDocuments(1).variantId, 0.91),
        QdrantCandidateHit(unknownVariantId, 0.88),
        QdrantCandidateHit(knownDocuments(0).variantId, 0.81),
        QdrantCandidateHit(knownDocuments(2).variantId, 0.71),
      )
      val lookup = new FakeVariantSearchDocumentLookup(knownDocuments)
      val input = UserSearchInput(query = "broad semantic test", userLat = None, userLon = None, limit = 10)
      val intent = parser.parse(input)
      val backend = new FakeSemanticCandidateBackend(input, intent, hits)

      val candidateHits = runIO(backend.candidates(input, intent))
      val lookupResult = runIO(lookup.lookup(candidateHits.map(_.variantId)))
      val resolvedDocuments = candidateHits.flatMap(hit => lookupResult.get(hit.variantId))
      val assembly = QdrantCandidateAssembler.assemble(candidateHits, resolvedDocuments)
      val response = QdrantCandidateResponseProjector.project(BeautySearchSpecV1.spec, input, assembly)

      assert(candidateHits == hits)
      assert(lookupResult.keySet == knownDocuments.map(_.variantId).toSet)
      assert(!lookupResult.contains(unknownVariantId))
      assert(assembly.variantCandidates.map(_.document.variantId) == List(knownDocuments(1).variantId, knownDocuments(0).variantId, knownDocuments(2).variantId))
      assert(response.variantCarousel.map(_.variantId) == assembly.variantCandidates.map(_.document.variantId))
      assert(response.variantCarousel.map(_.score) == List(0.91, 0.81, 0.71))
    }
  }

  "BeautySearchEvalInventory" should {
    "report current coverage without failing uncovered ids" in {
      val allIds = evalSuite.queries.map(_.id).toSet
      val covered = BeautySearchEvalInventory.firstMilestoneQueryIds ++
        BeautySearchEvalInventory.secondMilestoneQueryIds ++
        BeautySearchEvalInventory.hardNegativeQueryIds ++
        BeautySearchEvalInventory.browsLashesQueryIds ++
        BeautySearchEvalInventory.pmuQueryIds ++
        BeautySearchEvalInventory.faceQueryIds ++
        BeautySearchEvalInventory.nailsQueryIds ++
        BeautySearchEvalInventory.hairRemainingQueryIds ++
        BeautySearchEvalInventory.homeVisitQueryIds ++
        BeautySearchEvalInventory.lexicalRemainderQueryIds

      assert(BeautySearchEvalInventory.firstMilestoneQueryIds.subsetOf(allIds))
      assert(BeautySearchEvalInventory.secondMilestoneQueryIds.subsetOf(allIds))
      assert(BeautySearchEvalInventory.hardNegativeQueryIds.subsetOf(allIds))
      assert(BeautySearchEvalInventory.browsLashesQueryIds.subsetOf(allIds))
      assert(BeautySearchEvalInventory.pmuQueryIds.subsetOf(allIds))
      assert(BeautySearchEvalInventory.faceQueryIds.subsetOf(allIds))
      assert(BeautySearchEvalInventory.nailsQueryIds.subsetOf(allIds))
      assert(BeautySearchEvalInventory.hairRemainingQueryIds.subsetOf(allIds))
      assert(BeautySearchEvalInventory.homeVisitQueryIds.subsetOf(allIds))
      assert(BeautySearchEvalInventory.lexicalRemainderQueryIds.subsetOf(allIds))
      val allSets = List(
        BeautySearchEvalInventory.firstMilestoneQueryIds,
        BeautySearchEvalInventory.secondMilestoneQueryIds,
        BeautySearchEvalInventory.hardNegativeQueryIds,
        BeautySearchEvalInventory.browsLashesQueryIds,
        BeautySearchEvalInventory.pmuQueryIds,
        BeautySearchEvalInventory.faceQueryIds,
        BeautySearchEvalInventory.nailsQueryIds,
        BeautySearchEvalInventory.hairRemainingQueryIds,
        BeautySearchEvalInventory.homeVisitQueryIds,
        BeautySearchEvalInventory.lexicalRemainderQueryIds,
      )
      for {
        (a, i) <- allSets.zipWithIndex
        (b, j) <- allSets.zipWithIndex if i < j
      } assert(a.intersect(b).isEmpty, s"Overlap between set $i and set $j: ${a.intersect(b)}")

      println(BeautySearchEvalInventory.inventorySummary)
      assert(covered.size == 61)
      assert((allIds -- covered).nonEmpty)
    }
  }

  "hair removal pure eval" should {
    "cover remaining hair-removal queries" in {
      val backend = new InMemorySearchBackend[IO](BeautySearchSpecV1.spec, documents)
      val service = new BeautySearchService.Impl[IO](parser, backend)
      val queries = evalSuite.queries.filter(query => BeautySearchEvalInventory.hairRemainingQueryIds.contains(query.id))
      assert(queries.map(_.id).toSet == BeautySearchEvalInventory.hairRemainingQueryIds)

      queries.foreach { query =>
        val response = runIO(
          service.search(
            UserSearchInput(
              query = query.query,
              userLat = Some(evalSuite.testUserLocation.lat),
              userLon = Some(evalSuite.testUserLocation.lon),
            )
          )
        )
        val report = BeautySearchEvalScorer.score(query, response)
        runIO(BeautySearchEvalTestSupport.requireEvalOutcome(query, response, report, "pure"))
      }
    }
  }

  "home visit pure eval" should {
    "cover home-visit queries" in {
      val backend = new InMemorySearchBackend[IO](BeautySearchSpecV1.spec, documents)
      val service = new BeautySearchService.Impl[IO](parser, backend)
      val queries = evalSuite.queries.filter(query => BeautySearchEvalInventory.homeVisitQueryIds.contains(query.id))
      assert(queries.map(_.id).toSet == BeautySearchEvalInventory.homeVisitQueryIds)

      queries.foreach { query =>
        val response = runIO(
          service.search(
            UserSearchInput(
              query = query.query,
              userLat = Some(evalSuite.testUserLocation.lat),
              userLon = Some(evalSuite.testUserLocation.lon),
            )
          )
        )
        val report = BeautySearchEvalScorer.score(query, response)
        runIO(BeautySearchEvalTestSupport.requireEvalOutcome(query, response, report, "pure"))
      }
    }
  }

  "lexical remainder pure eval" should {
    "cover the safe lexical broad/noise remainder queries" in {
      val backend = new InMemorySearchBackend[IO](BeautySearchSpecV1.spec, documents)
      val service = new BeautySearchService.Impl[IO](parser, backend)
      val queries = evalSuite.queries.filter(query => BeautySearchEvalInventory.lexicalRemainderQueryIds.contains(query.id))
      assert(queries.map(_.id).toSet == BeautySearchEvalInventory.lexicalRemainderQueryIds)

      queries.foreach { query =>
        val response = runIO(
          service.search(
            UserSearchInput(
              query = query.query,
              userLat = Some(evalSuite.testUserLocation.lat),
              userLon = Some(evalSuite.testUserLocation.lon),
            )
          )
        )
        val report = BeautySearchEvalScorer.score(query, response)
        runIO(BeautySearchEvalTestSupport.requireEvalOutcome(query, response, report, "pure"))
      }
    }
  }

  "VariantSearchDocumentBuilder" should {
    "fail when joins are broken" in {
      val brokenServiceId = snapshot.services.head.id
      val brokenSnapshot = snapshot.copy(services = snapshot.services.filterNot(_.id == brokenServiceId))
      val result = VariantSearchDocumentBuilder.build(brokenSnapshot)
      assert(result.isLeft)
      assert(result.left.exists(_.message.contains("missing Service")))
    }
  }

  "ElasticsearchMappingInterpreter" should {
    "derive nested mapping from the search document fields" in {
      val mapping = ElasticsearchMappingInterpreter.mapping(BeautySearchSpecV1.spec)
      val cursor = mapping.hcursor
      assert(cursor.downField("mappings").downField("properties").downField("serviceName").get[String]("type") == Right("keyword"))
      assert(
        cursor
          .downField("mappings")
          .downField("properties")
          .downField("enumAttributes")
          .downField("properties")
          .downField(AttributeDefinition.NailCoatingTypeAttribute.code)
          .get[String]("type") == Right("keyword")
      )
      assert(cursor.downField("mappings").downField("properties").downField("location").get[String]("type") == Right("geo_point"))
    }

    "be derived from SearchDocumentSpec fields" in {
      val syntheticSpec = BeautySearchSpecV1.spec.copy(
        variantDocument = BeautySearchSpecV1.spec.variantDocument.copy(
          fields = BeautySearchSpecV1.spec.variantDocument.fields :+ SearchField[VariantSearchDocument](
            path = "testSyntheticKeyword",
            kind = SearchFieldKind.Keyword,
            extract = _ => None,
            filterable = true,
            facetable = true,
          )
        )
      )

      val mapping = ElasticsearchMappingInterpreter.mapping(syntheticSpec)
      assert(mapping.hcursor.downField("mappings").downField("properties").downField("testSyntheticKeyword").get[String]("type") == Right("keyword"))
    }
  }

  "ElasticsearchIngestionInterpreter" should {
    "be derived from SearchField.extract" in {
      val syntheticSpec = BeautySearchSpecV1.spec.copy(
        variantDocument = BeautySearchSpecV1.spec.variantDocument.copy(
          fields = BeautySearchSpecV1.spec.variantDocument.fields :+ SearchField[VariantSearchDocument](
            path = "testSyntheticKeyword",
            kind = SearchFieldKind.Keyword,
            extract = _ => Some(SearchValue.Keyword("synthetic-value")),
            filterable = true,
            facetable = true,
          )
        )
      )

      val source = ElasticsearchIngestionInterpreter.sourceJson(syntheticSpec, documents.head)
      assert(source.hcursor.downField("testSyntheticKeyword").as[String] == Right("synthetic-value"))
    }
  }

  "ElasticsearchSearchRequestInterpreter" should {
    "derive filters, searchable fields and facets from the provided spec" in {
      val customSpec = BeautySearchSpec(
        variantDocument = SearchDocumentSpec(
          indexName = "custom",
          id = _.variantId.toString,
          fields = List(
            SearchField[VariantSearchDocument](
              path = "serviceName",
              kind = SearchFieldKind.Keyword,
              extract = document => Some(SearchValue.Keyword(document.serviceName)),
              semantic = Some(SearchFieldSemantic.ServiceName),
              filterable = true,
              facetable = true,
            ),
            SearchField[VariantSearchDocument](
              path = "customText",
              kind = SearchFieldKind.Text,
              extract = document => Some(SearchValue.Text(document.serviceText)),
              searchable = true,
              boost = 7.0,
            ),
            SearchField[VariantSearchDocument](
              path = "providerKey",
              kind = SearchFieldKind.Keyword,
              extract = document => Some(SearchValue.Keyword(document.masterLocationId.toString)),
              filterable = true,
            ),
            SearchField[VariantSearchDocument](
              path = "serviceKey",
              kind = SearchFieldKind.Keyword,
              extract = document => Some(SearchValue.Keyword(document.serviceId.toString)),
              filterable = true,
            ),
          ),
        ),
        synonyms = Nil,
        carouselSpec = CarouselSpec(providerGroupField = "providerKey", serviceIntentGroupField = "serviceKey"),
        facetSpec = FacetSpec(enabled = true, fields = List(FacetField("serviceName", FacetFieldMode.Terms))),
      )

      val request = ElasticsearchSearchRequestInterpreter.request(
        customSpec,
        UserSearchInput("query", None, None),
        ParsedSearchIntent(
          originalQuery = "query",
          normalizedTokens = List("query"),
          explicitConstraints = List(SearchConstraint.ServiceAny(Set("Маникюр"))),
          softBoosts = Nil,
          remainingText = "query",
        ),
      ) match {
        case Right(value) => value
        case Left(error) => throw new RuntimeException(error.message)
      }

      val cursor = request.hcursor
      val multiMatchFields = cursor.downField("query").downField("bool").downField("must").downN(0).downField("multi_match").get[List[String]]("fields")
      assert(multiMatchFields == Right(List("customText^7.0")))
      assert(cursor.downField("query").downField("bool").downField("filter").focus.exists(jsonContainsString(_, "serviceName")))
      assert(cursor.downField("aggs").downField("agg_serviceName").focus.nonEmpty)
      assert(cursor.downField("aggs").downField("agg_providerKey").focus.nonEmpty)
      assert(cursor.downField("aggs").downField("agg_serviceKey").focus.nonEmpty)
    }

    "use SearchFieldSemantic lookup instead of hardcoded paths for constraints" in {
      val syntheticSpec = BeautySearchSpec(
        variantDocument = SearchDocumentSpec(
          indexName = "custom-semantic",
          id = _.variantId.toString,
          fields = List(
            SearchField[VariantSearchDocument](
              path = "serviceName2",
              kind = SearchFieldKind.Keyword,
              extract = document => Some(SearchValue.Keyword(document.serviceName)),
              semantic = Some(SearchFieldSemantic.ServiceName),
              filterable = true,
            ),
          ),
        ),
        synonyms = Nil,
        carouselSpec = CarouselSpec(),
        facetSpec = FacetSpec(enabled = false, fields = Nil),
      )

      val request = ElasticsearchSearchRequestInterpreter.request(
        syntheticSpec,
        UserSearchInput("query", None, None),
        ParsedSearchIntent(
          originalQuery = "query",
          normalizedTokens = List("query"),
          explicitConstraints = List(SearchConstraint.ServiceAny(Set("Маникюр"))),
          softBoosts = Nil,
          remainingText = "",
        ),
      ) match {
        case Right(value) => value
        case Left(error) => throw new RuntimeException(error.message)
      }

      assert(jsonContainsString(request, "serviceName2"))
      assert(!jsonContainsString(request, "serviceName\""))
    }

    "derive facets from FacetSpec and remove them when absent" in {
      val syntheticFacet = FacetField("serviceName", FacetFieldMode.Terms, limit = 3)
      val specWithFacet = BeautySearchSpecV1.spec.copy(
        facetSpec = BeautySearchSpecV1.spec.facetSpec.copy(fields = BeautySearchSpecV1.spec.facetSpec.fields :+ syntheticFacet)
      )
      val specWithoutFacet = BeautySearchSpecV1.spec.copy(
        facetSpec = BeautySearchSpecV1.spec.facetSpec.copy(fields = BeautySearchSpecV1.spec.facetSpec.fields.filterNot(_.path == "serviceName"))
      )

      val input = UserSearchInput("query", None, None)
      val intent = ParsedSearchIntent("query", List("query"), Nil, Nil, "")
      val withFacet = ElasticsearchSearchRequestInterpreter.request(specWithFacet, input, intent).toOption.get
      val withoutFacet = ElasticsearchSearchRequestInterpreter.request(specWithoutFacet, input, intent).toOption.get

      assert(withFacet.hcursor.downField("aggs").downField("agg_serviceName").focus.nonEmpty)
      assert(withoutFacet.hcursor.downField("aggs").downField("agg_serviceName").focus.isEmpty)
    }

    "use requestSpec.hitWindowSize for request size" in {
      val customSpec = BeautySearchSpecV1.spec.copy(
        requestSpec = BeautySearchSpecV1.spec.requestSpec.copy(hitWindowSize = 7)
      )

      val request = ElasticsearchSearchRequestInterpreter.request(
        customSpec,
        UserSearchInput("synthetic", None, None, limit = 1),
        ParsedSearchIntent("synthetic", List("synthetic"), Nil, Nil, "synthetic"),
      ) match {
        case Right(value) => value
        case Left(error) => throw new RuntimeException(error.message)
      }

      assert(request.hcursor.get[Int]("size") == Right(7))
    }

    "use requestSpec.aggregationSize for facet aggregations" in {
      val customSpec = BeautySearchSpecV1.spec.copy(
        facetSpec = BeautySearchSpecV1.spec.facetSpec.copy(fields = List(FacetField("serviceName", FacetFieldMode.Terms, limit = 3))),
        requestSpec = BeautySearchSpecV1.spec.requestSpec.copy(aggregationSize = 11),
      )

      val request = ElasticsearchSearchRequestInterpreter.request(
        customSpec,
        UserSearchInput("synthetic", None, None, limit = 1),
        ParsedSearchIntent("synthetic", List("synthetic"), Nil, Nil, "synthetic"),
      ) match {
        case Right(value) => value
        case Left(error) => throw new RuntimeException(error.message)
      }

      val facetAgg = request.hcursor.downField("aggs").downField("agg_serviceName").downField("terms")
      assert(facetAgg.get[Int]("size") == Right(11))
    }

    "use requestSpec.textOperator for multi_match" in {
      val customSpec = BeautySearchSpecV1.spec.copy(
        variantDocument = BeautySearchSpecV1.spec.variantDocument.copy(
          fields = BeautySearchSpecV1.spec.variantDocument.fields :+ SearchField[VariantSearchDocument](
            path = "testSyntheticText",
            kind = SearchFieldKind.Text,
            extract = _ => Some(SearchValue.Text("synthetic text")),
            searchable = true,
            boost = 9.0,
          )
        ),
        requestSpec = BeautySearchSpecV1.spec.requestSpec.copy(textOperator = TextOperator.Or),
      )

      val request = ElasticsearchSearchRequestInterpreter.request(
        customSpec,
        UserSearchInput("synthetic", None, None),
        ParsedSearchIntent("synthetic", List("synthetic"), Nil, Nil, "synthetic"),
      ) match {
        case Right(value) => value
        case Left(error) => throw new RuntimeException(error.message)
      }

      val multiMatch = request.hcursor.downField("query").downField("bool").downField("must").downN(0).downField("multi_match")
      assert(multiMatch.get[String]("operator") == Right("or"))
    }

    "use searchable fields and boosts from SearchField" in {
      val syntheticSpec = BeautySearchSpecV1.spec.copy(
        variantDocument = BeautySearchSpecV1.spec.variantDocument.copy(
          fields = BeautySearchSpecV1.spec.variantDocument.fields :+ SearchField[VariantSearchDocument](
            path = "testSyntheticText",
            kind = SearchFieldKind.Text,
            extract = _ => Some(SearchValue.Text("synthetic text")),
            searchable = true,
            boost = 9.0,
          )
        )
      )

      val request = ElasticsearchSearchRequestInterpreter.request(
        syntheticSpec,
        UserSearchInput("synthetic", None, None),
        ParsedSearchIntent(
          originalQuery = "synthetic",
          normalizedTokens = List("synthetic"),
          explicitConstraints = Nil,
          softBoosts = Nil,
          remainingText = "synthetic",
        ),
      ) match {
        case Right(value) => value
        case Left(error) => throw new RuntimeException(error.message)
      }

      assert(jsonContainsString(request, "testSyntheticText^9.0"))
    }
  }

  "QdrantJsonInterpreter" should {
    "generate collection creation JSON from vector specs" in {
      val spec = VectorSearchSpec(
        collectionName = "synthetic_collection",
        vectorName = "synthetic_vector",
        topK = 7,
        scoreThreshold = None,
      )
      val embeddingSpec = EmbeddingSpec[VariantSearchDocument](
        vectorName = "synthetic_vector",
        modelName = "synthetic-model",
        dimension = 384,
        distance = VectorDistance.Euclidean,
        sourceTextFieldPaths = List("serviceName"),
      )

      val json = QdrantJsonInterpreter.createCollectionJson(spec, embeddingSpec)
      val cursor = json.hcursor
      assert(cursor.downField("vectors").downField("synthetic_vector").get[Int]("size") == Right(384))
      assert(cursor.downField("vectors").downField("synthetic_vector").get[String]("distance") == Right("Euclid"))
    }

    "generate search JSON with topK and without score_threshold when absent" in {
      val spec = VectorSearchSpec(
        collectionName = "synthetic_collection",
        vectorName = "synthetic_vector",
        topK = 7,
        scoreThreshold = None,
      )

      val json = QdrantJsonInterpreter.searchRequestJson(spec, List(0.1, 0.2, 0.3))
      val cursor = json.hcursor
      assert(cursor.downField("vector").downField("name").as[String] == Right("synthetic_vector"))
      assert(cursor.downField("vector").downField("vector").as[List[Double]] == Right(List(0.1, 0.2, 0.3)))
      assert(cursor.get[Int]("limit") == Right(7))
      assert(cursor.downField("score_threshold").focus.isEmpty)
      assert(cursor.get[Boolean]("with_payload") == Right(true))
    }

    "generate search JSON with score_threshold when present" in {
      val spec = VectorSearchSpec(
        collectionName = "synthetic_collection",
        vectorName = "synthetic_vector",
        topK = 7,
        scoreThreshold = Some(0.42),
      )

      val json = QdrantJsonInterpreter.searchRequestJson(spec, List(0.9, 0.8))
      val cursor = json.hcursor
      assert(cursor.downField("vector").downField("name").as[String] == Right("synthetic_vector"))
      assert(cursor.downField("vector").downField("vector").as[List[Double]] == Right(List(0.9, 0.8)))
      assert(cursor.get[Int]("limit") == Right(7))
      assert(cursor.get[Boolean]("with_payload") == Right(true))
      assert(cursor.get[Double]("score_threshold") == Right(0.42))
    }
  }

  "BeautySearchIntentParser" should {
    "parse first milestone queries into expected explicit constraints" in {
      val expectations = Map(
        "маникюр гель лак" -> List(
          SearchConstraint.ServiceAny(Set("Маникюр")),
          SearchConstraint.EnumAttr("nail_service_type", Set("manicure")),
          SearchConstraint.EnumAttr("nail_coating_type", Set("gel_polish")),
        ),
        "педикюр без лака" -> List(
          SearchConstraint.ServiceAny(Set("Педикюр")),
          SearchConstraint.EnumAttr("nail_service_type", Set("pedicure")),
          SearchConstraint.EnumAttr("nail_coating_type", Set("no_coating")),
        ),
        "наращивание ногтей гель" -> List(
          SearchConstraint.ServiceAny(Set("Наращивание и моделирование ногтей")),
          SearchConstraint.EnumAttr("nail_service_type", Set("extension")),
          SearchConstraint.EnumAttr("nail_coating_type", Set("gel")),
        ),
        "ресницы классика" -> List(
          SearchConstraint.ServiceAny(Set("Ресницы")),
          SearchConstraint.EnumAttr("lash_service_type", Set("extension")),
          SearchConstraint.EnumAttr("lash_volume", Set("classic1_d")),
        ),
        "ресницы 2д" -> List(
          SearchConstraint.ServiceAny(Set("Ресницы")),
          SearchConstraint.EnumAttr("lash_service_type", Set("extension")),
          SearchConstraint.EnumAttr("lash_volume", Set("volume2_d")),
        ),
        "брови хна" -> List(
          SearchConstraint.ServiceAny(Set("Брови")),
          SearchConstraint.EnumAttr("brow_service_type", Set("henna")),
          SearchConstraint.BoolAttr("with_tinting", true),
        ),
        "перманент губы" -> List(
          SearchConstraint.ServiceAny(Set("Permanent Make-Up")),
          SearchConstraint.EnumAttr("pmu_area", Set("lips")),
        ),
        "eyeliner pmu" -> List(
          SearchConstraint.ServiceAny(Set("Permanent Make-Up")),
          SearchConstraint.EnumAttr("pmu_area", Set("eyeliner")),
        ),
        "aquafacial" -> List(
          SearchConstraint.ServiceAny(Set("Косметология лица")),
          SearchConstraint.EnumAttr("facial_treatment_type", Set("aquafacial")),
          SearchConstraint.EnumAttr("body_area", Set("face")),
        ),
        "microneedling face" -> List(
          SearchConstraint.ServiceAny(Set("Косметология лица")),
          SearchConstraint.EnumAttr("facial_treatment_type", Set("microneedling")),
          SearchConstraint.EnumAttr("body_area", Set("face")),
        ),
        "bb glow" -> List(
          SearchConstraint.ServiceAny(Set("Косметология лица")),
          SearchConstraint.EnumAttr("facial_treatment_type", Set("bb_glow")),
          SearchConstraint.EnumAttr("body_area", Set("face")),
        ),
        "классический уход лицо" -> List(
          SearchConstraint.ServiceAny(Set("Косметология лица")),
          SearchConstraint.EnumAttr("facial_treatment_type", Set("classic")),
          SearchConstraint.EnumAttr("body_area", Set("face")),
        ),
      )

      expectations.foreach { case (query, expectedConstraints) =>
        val intent = parser.parse(UserSearchInput(query, None, None))
        expectedConstraints.foreach { expectedConstraint =>
          assert(intent.explicitConstraints.contains(expectedConstraint), s"Missing $expectedConstraint for query '$query'")
        }
      }
    }

    "use synonym dictionary data from the spec" in {
      val syntheticSpec = BeautySearchSpec(
        variantDocument = BeautySearchSpecV1.spec.variantDocument,
        synonyms = BeautySearchSpecV1.spec.synonyms :+ SearchSynonym(
          tokens = Set("synthetic keyword"),
          constraints = List(SearchConstraint.ServiceAny(Set("Маникюр"))),
          matchMode = SynonymMatchMode.Phrase,
        ),
        carouselSpec = BeautySearchSpecV1.spec.carouselSpec,
        facetSpec = BeautySearchSpecV1.spec.facetSpec,
      )

      val syntheticParser = new BeautySearchIntentParser(syntheticSpec)
      val parsed = syntheticParser.parse(UserSearchInput("synthetic keyword", None, None))
      assert(parsed.explicitConstraints.contains(SearchConstraint.ServiceAny(Set("Маникюр"))))
    }
  }

  "SearchBackendRouter" should {
    "route the covered lexical eval queries to ElasticsearchOnly and the broad semantic eval queries to QdrantCandidateRoute via metadata" in {
      val router = SearchBackendRouter.default

      val lexicalQueryIds =
        BeautySearchEvalInventory.firstMilestoneQueryIds ++
          BeautySearchEvalInventory.secondMilestoneQueryIds ++
          BeautySearchEvalInventory.hardNegativeQueryIds ++
          BeautySearchEvalInventory.browsLashesQueryIds ++
          BeautySearchEvalInventory.pmuQueryIds ++
          BeautySearchEvalInventory.faceQueryIds ++
          BeautySearchEvalInventory.nailsQueryIds ++
          BeautySearchEvalInventory.hairRemainingQueryIds ++
          BeautySearchEvalInventory.homeVisitQueryIds ++
          BeautySearchEvalInventory.lexicalRemainderQueryIds

      assert(lexicalQueryIds.size == 61)

      evalSuite.queries.filter(query => lexicalQueryIds.contains(query.id)).foreach { query =>
        val input = UserSearchInput(query.query, None, None)
        val parsed = parser.parse(input)
        val decision = router.decide(input, parsed)

        assert(decision.route == SearchBackendRoute.ElasticsearchOnly, s"${query.id} should route to ElasticsearchOnly")
      }

      val broadSemanticQueryIds = Set("q_broad_004", "q_broad_006")
      broadSemanticQueryIds.foreach { queryId =>
        val query = queryById(queryId)
        val input = UserSearchInput(query.query, None, None)
        val parsed = parser.parse(input)
        val decision = router.decide(
          input,
          parsed,
          SearchRoutingMetadata(signal = Some(SearchRoutingSignal.BroadSemanticCandidate)),
        )

        assert(decision.route == SearchBackendRoute.QdrantCandidateRoute, s"$queryId should route to QdrantCandidateRoute")
        assert(parsed.explicitConstraints.isEmpty)
        assert(parsed.softBoosts.isEmpty)
        assert(parsed.remainingText.nonEmpty)
      }
    }

    "route a direct lexical query to ElasticsearchOnly" in {
      val router = SearchBackendRouter.default
      val input = UserSearchInput("маникюр", None, None)
      val decision = router.decide(input, parser.parse(input))

      assert(decision.route == SearchBackendRoute.ElasticsearchOnly)
      assert(decision.reason == SearchRoutingReason.ExplicitConstraints)
    }

    "route a constrained attribute query to ElasticsearchOnly" in {
      val router = SearchBackendRouter.default
      val input = UserSearchInput("shellac entfernen und neu", None, None)
      val decision = router.decide(input, parser.parse(input))

      assert(decision.route == SearchBackendRoute.ElasticsearchOnly)
      assert(decision.reason == SearchRoutingReason.ExplicitConstraints)
    }

    "route a hard-negative query to ElasticsearchOnly" in {
      val router = SearchBackendRouter.default
      val query = queryById("q_noise_003")
      val input = UserSearchInput(query.query, None, None)
      val decision = router.decide(
        input,
        parser.parse(input),
        SearchRoutingMetadata(signal = Some(SearchRoutingSignal.HardNegativeOrNoiseGuard)),
      )

      assert(decision.route == SearchBackendRoute.ElasticsearchOnly)
      assert(decision.reason == SearchRoutingReason.ExplicitConstraints)
    }

    "route q_broad_004-like parsed input to QdrantCandidateRoute when marked as broad semantic" in {
      val router = SearchBackendRouter.default
      val query = queryById("q_broad_004")
      val input = UserSearchInput(query.query, None, None)
      val parsed = parser.parse(input)
      val decision = router.decide(
        input,
        parsed,
        SearchRoutingMetadata(signal = Some(SearchRoutingSignal.BroadSemanticCandidate)),
      )

      assert(parsed.explicitConstraints.isEmpty)
      assert(parsed.softBoosts.isEmpty)
      assert(parsed.remainingText.nonEmpty)
      assert(decision.route == SearchBackendRoute.QdrantCandidateRoute)
      assert(decision.reason == SearchRoutingReason.BroadSemanticCandidate)
    }

    "route q_broad_006-like parsed input to QdrantCandidateRoute when marked as broad semantic" in {
      val router = SearchBackendRouter.default
      val query = queryById("q_broad_006")
      val input = UserSearchInput(query.query, None, None)
      val parsed = parser.parse(input)
      val decision = router.decide(
        input,
        parsed,
        SearchRoutingMetadata(signal = Some(SearchRoutingSignal.BroadSemanticCandidate)),
      )

      assert(parsed.explicitConstraints.isEmpty)
      assert(parsed.softBoosts.isEmpty)
      assert(parsed.remainingText.nonEmpty)
      assert(decision.route == SearchBackendRoute.QdrantCandidateRoute)
      assert(decision.reason == SearchRoutingReason.BroadSemanticCandidate)
    }

    "keep residual text alone on ElasticsearchOnly when no broad signal is present" in {
      val router = SearchBackendRouter.default
      val input = UserSearchInput("beauty near Wandsbek Markt", None, None)
      val parsed = parser.parse(input)
      val decision = router.decide(input, parsed)

      assert(parsed.explicitConstraints.isEmpty)
      assert(parsed.softBoosts.isEmpty)
      assert(parsed.remainingText.nonEmpty)
      assert(decision.route == SearchBackendRoute.ElasticsearchOnly)
      assert(decision.reason == SearchRoutingReason.FallbackNotEnabled)
    }

    "combine the pure router with pure Qdrant candidate assembly without runtime hybrid behavior" in {
      val router = SearchBackendRouter.default
      val candidateDocuments = documents.take(2)
      val candidateHits = List(
        QdrantCandidateHit(candidateDocuments(1).variantId, 0.82),
        QdrantCandidateHit(candidateDocuments(0).variantId, 0.91),
      )

      def routeAndMaybeAssemble(
        input: UserSearchInput,
        metadata: SearchRoutingMetadata = SearchRoutingMetadata(),
        hits: List[QdrantCandidateHit] = Nil,
      ) = {
        val parsed = parser.parse(input)
        val decision = router.decide(input, parsed, metadata)
        val assembly =
          if (decision.route == SearchBackendRoute.QdrantCandidateRoute) Some(assembleQdrantCandidates(hits))
          else None

        (parsed, decision, assembly)
      }

      val lexicalQuery = evalSuite.queries
        .find(query => BeautySearchEvalInventory.firstMilestoneQueryIds.contains(query.id))
        .getOrElse(sys.error("Missing lexical eval query for router coverage"))
      val lexicalInput = UserSearchInput(lexicalQuery.query, None, None)
      val (lexicalParsed, lexicalDecision, lexicalAssembly) = routeAndMaybeAssemble(lexicalInput)

      assert(lexicalParsed.explicitConstraints.nonEmpty || lexicalParsed.softBoosts.nonEmpty)
      assert(lexicalDecision.route == SearchBackendRoute.ElasticsearchOnly)
      assert(lexicalAssembly.isEmpty)

      val broadSemanticInput = UserSearchInput("synthetic semantic probe", None, None)
      val (broadSemanticParsed, broadSemanticDecision, broadSemanticAssembly) = routeAndMaybeAssemble(
        broadSemanticInput,
        SearchRoutingMetadata(signal = Some(SearchRoutingSignal.BroadSemanticCandidate)),
        candidateHits,
      )

      assert(broadSemanticParsed.explicitConstraints.isEmpty)
      assert(broadSemanticParsed.softBoosts.isEmpty)
      assert(broadSemanticParsed.remainingText.nonEmpty)
      assert(broadSemanticDecision.route == SearchBackendRoute.QdrantCandidateRoute)
      assert(broadSemanticDecision.reason == SearchRoutingReason.BroadSemanticCandidate)
      assert(broadSemanticAssembly.exists(_.variantCandidates.map(_.document.variantId) == List(candidateDocuments(1).variantId, candidateDocuments(0).variantId)))

      val residualInput = UserSearchInput("synthetic residual probe", None, None)
      val (residualParsed, residualDecision, residualAssembly) = routeAndMaybeAssemble(residualInput)

      assert(residualParsed.explicitConstraints.isEmpty)
      assert(residualParsed.softBoosts.isEmpty)
      assert(residualParsed.remainingText.nonEmpty)
      assert(residualDecision.route == SearchBackendRoute.ElasticsearchOnly)
      assert(residualDecision.reason == SearchRoutingReason.FallbackNotEnabled)
      assert(residualAssembly.isEmpty)

      val hardNegativeInput = UserSearchInput("synthetic residual probe", None, None)
      val (hardNegativeParsed, hardNegativeDecision, hardNegativeAssembly) = routeAndMaybeAssemble(
        hardNegativeInput,
        SearchRoutingMetadata(signal = Some(SearchRoutingSignal.HardNegativeOrNoiseGuard)),
      )

      assert(hardNegativeParsed.explicitConstraints.isEmpty)
      assert(hardNegativeParsed.softBoosts.isEmpty)
      assert(hardNegativeParsed.remainingText.nonEmpty)
      assert(hardNegativeDecision.route == SearchBackendRoute.ElasticsearchOnly)
      assert(hardNegativeDecision.reason == SearchRoutingReason.HardNegativeOrNoiseGuard)
      assert(hardNegativeAssembly.isEmpty)
    }

    "not depend on query ids in production router logic" in {
      val decideMethod = classOf[SearchBackendRouter].getMethods.find { method =>
        method.getName == "decide" &&
        method.getParameterTypes.toList == List(classOf[UserSearchInput], classOf[ParsedSearchIntent], classOf[SearchRoutingMetadata])
      }.getOrElse(sys.error("Missing decide(UserSearchInput, ParsedSearchIntent, SearchRoutingMetadata) method"))

      assert(!decideMethod.getParameterTypes.exists(_ == classOf[String]))
    }
  }

  "InMemorySearchBackend" should {
    "return acceptable variant, provider and service ids for the first milestone eval subset" in {
      val backend = new InMemorySearchBackend[IO](BeautySearchSpecV1.spec, documents)
      val service = new BeautySearchService.Impl[IO](parser, backend)

      evalSuite.queries.filter(query => BeautySearchEvalInventory.firstMilestoneQueryIds.contains(query.id)).foreach { query =>
        val response = runIO(
          service.search(
            UserSearchInput(
              query = query.query,
              userLat = Some(evalSuite.testUserLocation.lat),
              userLon = Some(evalSuite.testUserLocation.lon),
            )
          )
        )
        val report = BeautySearchEvalScorer.score(query, response)

        assert(response.variantCarousel.take(3).exists(result => query.expectedVariantCarousel.acceptableVariantIds.contains(result.variantId)), s"variant top-3 failed for ${query.id}")
        assert(response.providerCarousel.take(5).exists(result => query.expectedProviderCarousel.acceptableProviderLocationIds.contains(result.masterLocationId)), s"provider top-5 failed for ${query.id}")
        assert(response.serviceIntentCarousel.take(3).exists(result => query.expectedServiceIntentCarousel.acceptableServiceIds.contains(result.serviceId)), s"service top-3 failed for ${query.id}")
        assert(report.failedAssertions.isEmpty, s"scorer failures for ${query.id}: ${report.failedAssertions.mkString(", ")}")
      }
    }

    "return acceptable variant, provider and service ids for the second milestone eval subset" in {
      val backend = new InMemorySearchBackend[IO](BeautySearchSpecV1.spec, documents)
      val service = new BeautySearchService.Impl[IO](parser, backend)

      evalSuite.queries.filter(query => BeautySearchEvalInventory.secondMilestoneQueryIds.contains(query.id)).foreach { query =>
        val response = runIO(
          service.search(
            UserSearchInput(
              query = query.query,
              userLat = Some(evalSuite.testUserLocation.lat),
              userLon = Some(evalSuite.testUserLocation.lon),
            )
          )
        )
        val report = BeautySearchEvalScorer.score(query, response)

        assert(response.variantCarousel.take(3).exists(result => query.expectedVariantCarousel.acceptableVariantIds.contains(result.variantId)), s"variant top-3 failed for ${query.id}")
        assert(response.providerCarousel.take(5).exists(result => query.expectedProviderCarousel.acceptableProviderLocationIds.contains(result.masterLocationId)), s"provider top-5 failed for ${query.id}")
        assert(response.serviceIntentCarousel.take(3).exists(result => query.expectedServiceIntentCarousel.acceptableServiceIds.contains(result.serviceId)), s"service top-3 failed for ${query.id}")
        assert(report.failedAssertions.isEmpty, s"scorer failures for ${query.id}: ${report.failedAssertions.mkString(", ")}")
      }
    }

    "return acceptable variant, provider and service ids for the hard-negative eval subset" in {
      val backend = new InMemorySearchBackend[IO](BeautySearchSpecV1.spec, documents)
      val service = new BeautySearchService.Impl[IO](parser, backend)

      evalSuite.queries.filter(query => BeautySearchEvalInventory.hardNegativeQueryIds.contains(query.id)).foreach { query =>
        val response = runIO(
          service.search(
            UserSearchInput(
              query = query.query,
              userLat = Some(evalSuite.testUserLocation.lat),
              userLon = Some(evalSuite.testUserLocation.lon),
            )
          )
        )
        val report = BeautySearchEvalScorer.score(query, response)

        BeautySearchEvalTestSupport.assertEvalOutcome(query, response, report, "eval")
      }
    }

    "return acceptable variant, provider and service ids for the brows and lashes eval subset" in {
      val backend = new InMemorySearchBackend[IO](BeautySearchSpecV1.spec, documents)
      val service = new BeautySearchService.Impl[IO](parser, backend)

      evalSuite.queries.filter(query => BeautySearchEvalInventory.browsLashesQueryIds.contains(query.id)).foreach { query =>
        val response = runIO(
          service.search(
            UserSearchInput(
              query = query.query,
              userLat = Some(evalSuite.testUserLocation.lat),
              userLon = Some(evalSuite.testUserLocation.lon),
            )
          )
        )
        val report = BeautySearchEvalScorer.score(query, response)

        BeautySearchEvalTestSupport.assertEvalOutcome(query, response, report, "eval")
      }
    }

    "return acceptable variant, provider and service ids for the PMU eval subset" in {
      val backend = new InMemorySearchBackend[IO](BeautySearchSpecV1.spec, documents)
      val service = new BeautySearchService.Impl[IO](parser, backend)

      evalSuite.queries.filter(query => BeautySearchEvalInventory.pmuQueryIds.contains(query.id)).foreach { query =>
        val response = runIO(
          service.search(
            UserSearchInput(
              query = query.query,
              userLat = Some(evalSuite.testUserLocation.lat),
              userLon = Some(evalSuite.testUserLocation.lon),
            )
          )
        )
        val report = BeautySearchEvalScorer.score(query, response)

        BeautySearchEvalTestSupport.assertEvalOutcome(query, response, report, "eval")
      }
    }

    "return acceptable variant, provider and service ids for the face eval subset" in {
      val backend = new InMemorySearchBackend[IO](BeautySearchSpecV1.spec, documents)
      val service = new BeautySearchService.Impl[IO](parser, backend)

      evalSuite.queries.filter(query => BeautySearchEvalInventory.faceQueryIds.contains(query.id)).foreach { query =>
        val response = runIO(
          service.search(
            UserSearchInput(
              query = query.query,
              userLat = Some(evalSuite.testUserLocation.lat),
              userLon = Some(evalSuite.testUserLocation.lon),
            )
          )
        )
        val report = BeautySearchEvalScorer.score(query, response)

        BeautySearchEvalTestSupport.assertEvalOutcome(query, response, report, "eval")
      }
    }

    "return acceptable variant, provider and service ids for the nails eval subset" in {
      val backend = new InMemorySearchBackend[IO](BeautySearchSpecV1.spec, documents)
      val service = new BeautySearchService.Impl[IO](parser, backend)

      evalSuite.queries.filter(query => BeautySearchEvalInventory.nailsQueryIds.contains(query.id)).foreach { query =>
        val response = runIO(
          service.search(
            UserSearchInput(
              query = query.query,
              userLat = Some(evalSuite.testUserLocation.lat),
              userLon = Some(evalSuite.testUserLocation.lon),
            )
          )
        )
        val report = BeautySearchEvalScorer.score(query, response)

        BeautySearchEvalTestSupport.assertEvalOutcome(query, response, report, "eval")
      }
    }
  }

  private def jsonContainsString(json: Json, needle: String): Boolean =
    json.noSpaces.contains(needle)

  private def queryById(id: String) =
    evalSuite.queries.find(_.id == id).getOrElse(sys.error(s"Missing eval query $id"))

  private def assembleQdrantCandidates(hits: List[QdrantCandidateHit]) =
    QdrantCandidateAssembler.assemble(hits, documents)

  private def providerGroupDocuments =
    documents.groupBy(_.masterLocationId).values.find(_.size >= 2).get

  private def serviceGroupDocuments =
    documents.groupBy(_.serviceId).values.find(_.size >= 2).get

  private final class FakeSemanticCandidateBackend(
    expectedInput: UserSearchInput,
    expectedIntent: ParsedSearchIntent,
    hits: List[QdrantCandidateHit],
  ) extends SemanticCandidateBackend[IO] {
    override def candidates(input: UserSearchInput, intent: ParsedSearchIntent): IO[QueryFailure, List[QdrantCandidateHit]] = ZIO.succeed {
      assert(input == expectedInput)
      assert(intent == expectedIntent)
      hits
    }
  }

  private final class FakeVariantSearchDocumentLookup(documents: List[VariantSearchDocument]) extends VariantSearchDocumentLookup[IO] {
    private val documentsById = documents.iterator.map(document => document.variantId -> document).toMap

    override def lookup(variantIds: List[MasterServiceOfferVariantId]): IO[QueryFailure, Map[MasterServiceOfferVariantId, VariantSearchDocument]] =
      ZIO.succeed(variantIds.iterator.flatMap(variantId => documentsById.get(variantId).map(document => variantId -> document)).toMap)
  }

  private def runIO[A](effect: IO[QueryFailure, A]): A =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
    }

}
