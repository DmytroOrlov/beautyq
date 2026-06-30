package leaderboard.search

import io.circe.{Json, JsonObject}
import leaderboard.model.*
import leaderboard.search.dsl.*
import leaderboard.search.document.{BeautyQVariantSearchDocumentSchema, BeautySearchCatalogSnapshot, VariantSearchDocument, VariantSearchDocumentBuilder}
import leaderboard.search.elasticsearch.BeautyQElasticsearchInterpreterAdapter
import leaderboard.search.eval.BeautySearchEvalScorer
import leaderboard.search.embedding.EmbeddingClient
import leaderboard.search.hybrid.{ExperimentalBeautySearchService, ExperimentalHybridRouteDecider, ExperimentalHybridRouteDiagnostics, ExperimentalHybridSearchBackend}
import leaderboard.search.inmemory.InMemorySearchBackend
import leaderboard.search.interpreter.SearchEmbeddingTextExtractor
import leaderboard.search.parser.BeautySearchIntentParser
import leaderboard.search.qdrant.{QdrantCandidateAssembler, QdrantCandidateHitDecoder, QdrantCandidateResponseProjector, QdrantJsonInterpreter, QdrantSearchClient, QdrantSearchHit, QdrantSemanticCandidateBackend, QdrantSemanticCandidateSearch}
import leaderboard.search.routing.{SearchBackendRoute, SearchBackendRouter, SearchRoutingMetadata, SearchRoutingReason, SearchRoutingSignal}
import leaderboard.search.semantic.{InMemoryVariantSearchDocumentLookup, SemanticCandidateBackend, SemanticCandidateHit, SemanticDocumentHit, VariantSearchDocumentLookup}
import leaderboard.seed.BeautyQSeedLoader
import org.scalatest.wordspec.AnyWordSpec
import zio.{IO, Ref, Runtime, Unsafe, ZIO}

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
        sourceTextFields = List(
          BeautyQVariantSearchDocumentSchema.Fields.serviceName,
          BeautyQVariantSearchDocumentSchema.Fields.allText,
        ),
      )

      assert(spec.vectorName == "variant-embedding")
      assert(spec.modelName == "test-model")
      assert(spec.dimension == 384)
      assert(spec.distance == VectorDistance.Cosine)
      assert(spec.sourceTextFieldPaths == List("serviceName", "allText"))
    }
  }

  "Qdrant candidate hit decoder" should {
    "decode payload.variantId into candidate hits, preserving order and score" in {
      documents.take(2) match {
        case List(firstDocument, secondDocument) =>
          val firstVariantId = firstDocument.variantId
          val secondVariantId = secondDocument.variantId
          val hits = List(
            QdrantSearchHit(
              id = UUID.randomUUID().toString,
              payload = JsonObject.fromMap(Map("variantId" -> Json.fromString(secondVariantId.toString))),
              score = 0.25,
            ),
            QdrantSearchHit(
              id = UUID.randomUUID().toString,
              payload = JsonObject.fromMap(Map("variantId" -> Json.fromString(firstVariantId.toString))),
              score = 0.75,
            ),
          )

          val decoded = QdrantCandidateHitDecoder.decode[VariantSearchDocument, MasterServiceOfferVariantId](hits, BeautyQVariantSearchDocumentSchema.Fields.variantId)

          assert(decoded == Right(List(
            SemanticDocumentHit(secondVariantId, 0.25),
            SemanticDocumentHit(firstVariantId, 0.75),
          )))
        case other =>
          fail(s"Expected at least 2 documents, got ${other.size}")
      }
    }

    "fail when payload.variantId is missing" in {
      val hit = QdrantSearchHit(
        id = UUID.randomUUID().toString,
        payload = JsonObject.empty,
        score = 0.5,
      )

      val decoded = QdrantCandidateHitDecoder.decode[VariantSearchDocument, MasterServiceOfferVariantId](List(hit), BeautyQVariantSearchDocumentSchema.Fields.variantId)

      assert(decoded.isLeft)
      assert(decoded.left.exists(_.message.contains("Missing payload.variantId")))
    }

    "fail when payload.variantId is invalid" in {
      val hit = QdrantSearchHit(
        id = UUID.randomUUID().toString,
        payload = JsonObject.fromMap(Map("variantId" -> Json.fromString("not-a-uuid"))),
        score = 0.5,
      )

      val decoded = QdrantCandidateHitDecoder.decode[VariantSearchDocument, MasterServiceOfferVariantId](List(hit), BeautyQVariantSearchDocumentSchema.Fields.variantId)

      assert(decoded.isLeft)
      assert(decoded.left.exists(_.message.contains("Invalid payload.variantId")))
    }

    "not fall back to hit.id when payload.variantId is missing" in {
      val hit = QdrantSearchHit(
        id = UUID.fromString("00000000-0000-0000-0000-000000000123").toString,
        payload = JsonObject.empty,
        score = 0.5,
      )

      val decoded = QdrantCandidateHitDecoder.decode[VariantSearchDocument, MasterServiceOfferVariantId](List(hit), BeautyQVariantSearchDocumentSchema.Fields.variantId)

      assert(decoded.isLeft)
    }

    "return the first invalid input hit before later invalid hits" in {
      val first = QdrantSearchHit(
        id = "first-missing-payload-variant-id",
        payload = JsonObject.empty,
        score = 0.5,
      )
      val second = QdrantSearchHit(
        id = "second-invalid-payload-variant-id",
        payload = JsonObject.fromMap(Map("variantId" -> Json.fromString("not-a-uuid"))),
        score = 0.4,
      )

      val decoded = QdrantCandidateHitDecoder.decode[VariantSearchDocument, MasterServiceOfferVariantId](List(first, second), BeautyQVariantSearchDocumentSchema.Fields.variantId)

      assert(decoded.isLeft)
      assert(decoded.left.exists(failure => failure.message.contains("first-missing-payload-variant-id")))
      assert(!decoded.left.exists(failure => failure.message.contains("second-invalid-payload-variant-id")))
    }
  }

  "SearchEmbeddingTextExtractor" should {
    "extract source text from matching fields" in {
      val document = documents.head
      val firstField = SearchField[VariantSearchDocument](
        path = "first",
        kind = SearchFieldKind.Text,
        extract = doc => Some(SearchValue.Text(doc.serviceName)),
      )
      val secondField = SearchField[VariantSearchDocument](
        path = "second",
        kind = SearchFieldKind.Text,
        extract = doc => Some(SearchValue.Text(doc.categoryName)),
      )
      val documentSpec = SearchDocumentSpec[VariantSearchDocument](
        indexName = "synthetic-embedding",
        id = _.variantId.toString,
        fields = List(firstField, secondField),
      )
      val embeddingSpec = EmbeddingSpec[VariantSearchDocument](
        vectorName = "variant-embedding",
        modelName = "test-model",
        dimension = 384,
        distance = VectorDistance.Cosine,
        sourceTextFields = List(firstField, secondField),
      )
      val spec = BeautySearchSpecV1.spec.copy(
        variantDocument = documentSpec,
        embeddingSpec = Some(embeddingSpec),
      )

      val text = spec.embeddingSpec match {
        case Some(value) => SearchEmbeddingTextExtractor.extract(spec.variantDocument, value, document)
        case None        => fail("Expected embedding spec")
      }
      assert(text == s"${document.serviceName} ${document.categoryName}")
    }

    "extract configured source text fields" in {
      val document = documents.head
      val presentField = SearchField[VariantSearchDocument](
        path = "present",
        kind = SearchFieldKind.Text,
        extract = doc => Some(SearchValue.Text(doc.serviceName)),
      )
      val documentSpec = SearchDocumentSpec[VariantSearchDocument](
        indexName = "synthetic-embedding-missing",
        id = _.variantId.toString,
        fields = List(presentField),
      )
      val embeddingSpec = EmbeddingSpec[VariantSearchDocument](
        vectorName = "variant-embedding",
        modelName = "test-model",
        dimension = 384,
        distance = VectorDistance.Cosine,
        sourceTextFields = List(presentField),
      )

      val text = SearchEmbeddingTextExtractor.extract(documentSpec, embeddingSpec, document)
      assert(text == document.serviceName)
    }

    "ignore None extractor values" in {
      val document = documents.head
      val emptyField = SearchField[VariantSearchDocument](
        path = "empty",
        kind = SearchFieldKind.Text,
        extract = _ => None,
      )
      val presentField = SearchField[VariantSearchDocument](
        path = "present",
        kind = SearchFieldKind.Text,
        extract = doc => Some(SearchValue.Text(doc.categoryName)),
      )
      val documentSpec = SearchDocumentSpec[VariantSearchDocument](
        indexName = "synthetic-embedding-none",
        id = _.variantId.toString,
        fields = List(emptyField, presentField),
      )
      val embeddingSpec = EmbeddingSpec[VariantSearchDocument](
        vectorName = "variant-embedding",
        modelName = "test-model",
        dimension = 384,
        distance = VectorDistance.Cosine,
        sourceTextFields = List(emptyField, presentField),
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
        sourceTextFields = List(
          BeautyQVariantSearchDocumentSchema.Fields.serviceName,
          BeautyQVariantSearchDocumentSchema.Fields.categoryName,
        ),
      )

      val text = SearchEmbeddingTextExtractor.extract(BeautySearchSpecV1.spec.variantDocument, embeddingSpec, document)
      assert(text == s"${document.serviceName} ${document.categoryName}")
    }
  }
  "semantic candidate runtime seams" should {
    "resolve known variant ids with the in-memory document lookup" in {
      val knownDocuments = documents.take(3)
      val lookup = new InMemoryVariantSearchDocumentLookup[IO](knownDocuments)

      val result = runIO(lookup.lookup(List(knownDocuments(2).variantId, knownDocuments(0).variantId)))

      assert(result == Map(
        knownDocuments(2).variantId -> knownDocuments(2),
        knownDocuments(0).variantId -> knownDocuments(0),
      ))
    }

    "ignore unknown variant ids with the in-memory document lookup" in {
      val knownDocument = documents.head
      val lookup = new InMemoryVariantSearchDocumentLookup[IO](List(knownDocument))

      val result = runIO(lookup.lookup(List(unknownVariantId, knownDocument.variantId)))

      assert(result == Map(knownDocument.variantId -> knownDocument))
      assert(!result.contains(unknownVariantId))
    }

    "keep the first document for duplicate ids in the in-memory document lookup" in {
      val first = documents.head
      val duplicate = first.copy(allText = s"duplicate ${first.allText}")
      val lookup = new InMemoryVariantSearchDocumentLookup[IO](List(first, duplicate))

      val result = runIO(lookup.lookup(List(first.variantId)))

      assert(result == Map(first.variantId -> first))
      assert(result(first.variantId) != duplicate)
    }

    "compose fake semantic hits and fake document lookup through assembler and projector" in {
      val knownDocuments = documents.take(3)
      val hits = List(
        SemanticCandidateHit(knownDocuments(1).variantId, 0.91),
        SemanticCandidateHit(unknownVariantId, 0.88),
        SemanticCandidateHit(knownDocuments(0).variantId, 0.81),
        SemanticCandidateHit(knownDocuments(2).variantId, 0.71),
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

  "ExperimentalHybridSearchBackend" should {
    "delegate ElasticsearchOnly to the lexical backend without semantic calls or lookup" in {
      val input = UserSearchInput("маникюр", None, None)
      val intent = parser.parse(input)
      val lexicalResponse = emptyResponse.copy(facets = List(BeautySearchFacet("lexical", Nil)))
      val lexical = new FakeBeautySearchBackend(lexicalResponse)
      val semantic = new FailingSemanticCandidateBackend
      val lookup = new FailingVariantSearchDocumentLookup
      val backend = experimentalBackend(SearchBackendRoute.ElasticsearchOnly, lexical, semantic, lookup)

      val response = runIO(backend.search(input, intent))

      assert(response == lexicalResponse)
      assert(lexical.calls == 1)
    }

    "project QdrantCandidateRoute through semantic backend, in-memory lookup, assembler and projector" in {
      val knownDocuments = documents.take(3)
      val hits = List(
        SemanticCandidateHit(knownDocuments(1).variantId, 0.91),
        SemanticCandidateHit(knownDocuments(0).variantId, 0.81),
        SemanticCandidateHit(knownDocuments(2).variantId, 0.71),
      )
      val input = UserSearchInput("synthetic semantic route", None, None, limit = 10)
      val intent = parser.parse(input)
      val lexical = new FakeBeautySearchBackend(emptyResponse)
      val semantic = new CountingSemanticCandidateBackend(hits)
      val lookup = new InMemoryVariantSearchDocumentLookup[IO](knownDocuments)
      val backend = experimentalBackend(SearchBackendRoute.QdrantCandidateRoute, lexical, semantic, lookup)

      val response = runIO(backend.search(input, intent))

      assert(lexical.calls == 0)
      assert(semantic.calls == 1)
      assert(semantic.lastInput.contains(input))
      assert(semantic.lastIntent.contains(intent))
      assert(response.variantCarousel.map(_.variantId) == List(knownDocuments(1).variantId, knownDocuments(0).variantId, knownDocuments(2).variantId))
      assert(response.variantCarousel.map(_.score) == List(0.91, 0.81, 0.71))
      assert(response.facets.isEmpty)
      assert(response.inferredFilters.isEmpty)
    }

    "ignore unknown semantic hit ids through the assembler path" in {
      val knownDocument = documents.head
      val hits = List(
        SemanticCandidateHit(unknownVariantId, 0.99),
        SemanticCandidateHit(knownDocument.variantId, 0.77),
      )
      val lexical = new FakeBeautySearchBackend(emptyResponse)
      val semantic = new CountingSemanticCandidateBackend(hits)
      val lookup = new CountingVariantSearchDocumentLookup(List(knownDocument))
      val backend = experimentalBackend(SearchBackendRoute.QdrantCandidateRoute, lexical, semantic, lookup)

      val response = runIO(backend.search(UserSearchInput("synthetic unknown id", None, None), ParsedSearchIntent("synthetic unknown id", Nil, Nil, Nil, "synthetic unknown id")))

      assert(response.variantCarousel.map(_.variantId) == List(knownDocument.variantId))
      assert(response.variantCarousel.map(_.score) == List(0.77))
      assert(!response.variantCarousel.exists(_.variantId == unknownVariantId))
    }

    "return an empty candidate response when all semantic hits are unknown" in {
      val hits = List(
        SemanticCandidateHit(unknownVariantId, 0.99),
        SemanticCandidateHit(UUID.fromString("00000000-0000-0000-0000-000000000124"), 0.88),
      )
      val lexical = new FakeBeautySearchBackend(emptyResponse)
      val semantic = new CountingSemanticCandidateBackend(hits)
      val lookup = new CountingVariantSearchDocumentLookup(Nil)
      val backend = experimentalBackend(SearchBackendRoute.QdrantCandidateRoute, lexical, semantic, lookup)

      val response = runIO(backend.search(UserSearchInput("synthetic all unknown ids", None, None), ParsedSearchIntent("synthetic all unknown ids", Nil, Nil, Nil, "synthetic all unknown ids")))

      assert(response.variantCarousel.isEmpty)
      assert(response.providerCarousel.isEmpty)
      assert(response.serviceIntentCarousel.isEmpty)
      assert(response.facets.isEmpty)
      assert(response.inferredFilters.isEmpty)
      assert(lexical.calls == 0)
      assert(semantic.calls == 1)
      assert(lookup.calls == 1)
    }

    "delegate ElasticsearchThenQdrantFallback lexically without fallback behavior" in {
      val input = UserSearchInput("synthetic fallback route", None, None)
      val intent = parser.parse(input)
      val lexicalResponse = emptyResponse.copy(facets = List(BeautySearchFacet("fallback-lexical", Nil)))
      val lexical = new FakeBeautySearchBackend(lexicalResponse)
      val semantic = new FailingSemanticCandidateBackend
      val lookup = new FailingVariantSearchDocumentLookup
      val backend = experimentalBackend(SearchBackendRoute.ElasticsearchThenQdrantFallback, lexical, semantic, lookup)

      val response = runIO(backend.search(input, intent))

      assert(response == lexicalResponse)
      assert(lexical.calls == 1)
    }

    "ElasticsearchWithQdrantVariantSupplement appends Qdrant supplement variants absent from ES after ES variants preserving ES order" in {
      val esDocuments = documents.take(2)
      val supplementDocuments = documents.slice(2, 4)
      val esVariants = variantResultsFor(List(
        SemanticCandidateHit(esDocuments(0).variantId, 0.95),
        SemanticCandidateHit(esDocuments(1).variantId, 0.85),
      ))
      val lexical = new FakeBeautySearchBackend(emptyResponse.copy(variantCarousel = esVariants))
      val semantic = new CountingSemanticCandidateBackend(List(
        SemanticCandidateHit(supplementDocuments(0).variantId, 0.72),
        SemanticCandidateHit(supplementDocuments(1).variantId, 0.61),
      ))
      val lookup = new CountingVariantSearchDocumentLookup(documents)
      val backend = experimentalBackend(SearchBackendRoute.ElasticsearchWithQdrantVariantSupplement, lexical, semantic, lookup)

      val response = runIO(backend.search(UserSearchInput("synthetic supplement", None, None, limit = 10), ParsedSearchIntent("synthetic supplement", Nil, Nil, Nil, "synthetic supplement")))

      assert(response.variantCarousel.map(_.variantId) == List(
        esDocuments(0).variantId,
        esDocuments(1).variantId,
        supplementDocuments(0).variantId,
        supplementDocuments(1).variantId,
      ))
      assert(response.variantCarousel.take(2) == esVariants)
      assert(response.executionMode == BeautySearchExecutionMode.EsPlusQdrantSupplement)
      assert(response.qdrantSupplement.appendedVariantIds == supplementDocuments.map(_.variantId))
      assert(response.variantCarousel.drop(2).forall(_.resultOrigin == VariantResultOrigin.QdrantSupplement))
      assert(lexical.calls == 1)
      assert(semantic.calls == 1)
      assert(lookup.calls == 1)
    }

    "ElasticsearchWithQdrantVariantSupplement does not duplicate ES variants returned by Qdrant" in {
      val esDocuments = documents.take(2)
      val newDocument = documents(2)
      val esVariants = variantResultsFor(List(
        SemanticCandidateHit(esDocuments(0).variantId, 0.95),
        SemanticCandidateHit(esDocuments(1).variantId, 0.85),
      ))
      val lexical = new FakeBeautySearchBackend(emptyResponse.copy(variantCarousel = esVariants))
      val semantic = new CountingSemanticCandidateBackend(List(
        SemanticCandidateHit(esDocuments(1).variantId, 0.99),
        SemanticCandidateHit(newDocument.variantId, 0.60),
      ))
      val lookup = new CountingVariantSearchDocumentLookup(documents)
      val backend = experimentalBackend(SearchBackendRoute.ElasticsearchWithQdrantVariantSupplement, lexical, semantic, lookup)

      val response = runIO(backend.search(UserSearchInput("synthetic dedup", None, None, limit = 10), ParsedSearchIntent("synthetic dedup", Nil, Nil, Nil, "synthetic dedup")))

      assert(response.variantCarousel.map(_.variantId) == List(
        esDocuments(0).variantId,
        esDocuments(1).variantId,
        newDocument.variantId,
      ))
      assert(response.variantCarousel.take(2) == esVariants)
      assert(response.executionMode == BeautySearchExecutionMode.EsPlusQdrantSupplement)
      assert(response.qdrantSupplement.appendedVariantIds == List(newDocument.variantId))
    }

    "ElasticsearchWithQdrantVariantSupplement preserves ES provider, service, facet and filter fields exactly" in {
      val esDocuments = documents.take(2)
      val supplementDocuments = documents.slice(2, 4)
      val esVariants = variantResultsFor(List(SemanticCandidateHit(esDocuments(0).variantId, 0.9)))
      val esResponse = BeautySearchResponse(
        variantCarousel = esVariants,
        providerCarousel = Nil,
        serviceIntentCarousel = Nil,
        facets = List(BeautySearchFacet("es-facet", Nil)),
        inferredFilters = List(BeautySearchAppliedFilter(SearchConstraint.ServiceAny(Set("Маникюр")), explicit = true)),
      )
      val lexical = new FakeBeautySearchBackend(esResponse)
      val semantic = new CountingSemanticCandidateBackend(List(
        SemanticCandidateHit(supplementDocuments(0).variantId, 0.72),
        SemanticCandidateHit(supplementDocuments(1).variantId, 0.61),
      ))
      val lookup = new CountingVariantSearchDocumentLookup(documents)
      val backend = experimentalBackend(SearchBackendRoute.ElasticsearchWithQdrantVariantSupplement, lexical, semantic, lookup)

      val response = runIO(backend.search(UserSearchInput("synthetic preserve", None, None, limit = 10), ParsedSearchIntent("synthetic preserve", Nil, Nil, Nil, "synthetic preserve")))

      assert(response.variantCarousel.size > esVariants.size)
      assert(response.providerCarousel == esResponse.providerCarousel)
      assert(response.serviceIntentCarousel == esResponse.serviceIntentCarousel)
      assert(response.facets == esResponse.facets)
      assert(response.inferredFilters == esResponse.inferredFilters)
    }

    "ElasticsearchWithQdrantVariantSupplement returns the ES response unchanged when all Qdrant candidates already exist in ES" in {
      val esDocuments = documents.take(2)
      val esVariants = variantResultsFor(List(
        SemanticCandidateHit(esDocuments(0).variantId, 0.95),
        SemanticCandidateHit(esDocuments(1).variantId, 0.85),
      ))
      val esResponse = emptyResponse.copy(
        variantCarousel = esVariants,
        facets = List(BeautySearchFacet("es-facet", Nil)),
      )
      val lexical = new FakeBeautySearchBackend(esResponse)
      val semantic = new CountingSemanticCandidateBackend(List(
        SemanticCandidateHit(esDocuments(0).variantId, 0.50),
        SemanticCandidateHit(esDocuments(1).variantId, 0.40),
      ))
      val lookup = new CountingVariantSearchDocumentLookup(documents)
      val backend = experimentalBackend(SearchBackendRoute.ElasticsearchWithQdrantVariantSupplement, lexical, semantic, lookup)

      val response = runIO(backend.search(UserSearchInput("synthetic all dup", None, None, limit = 10), ParsedSearchIntent("synthetic all dup", Nil, Nil, Nil, "synthetic all dup")))

      assert(response == esResponse.copy(
        executionMode = BeautySearchExecutionMode.EsPlusQdrantSupplement,
        qdrantSupplement = QdrantSupplementSummary.usedNoAppend(QdrantSupplementPolicyName.None),
      ))
    }

    "ElasticsearchWithQdrantVariantSupplement returns the ES response unchanged when Qdrant returns no candidates" in {
      val esVariants = variantResultsFor(List(SemanticCandidateHit(documents(0).variantId, 0.9)))
      val esResponse = emptyResponse.copy(variantCarousel = esVariants)
      val lexical = new FakeBeautySearchBackend(esResponse)
      val semantic = new CountingSemanticCandidateBackend(Nil)
      val lookup = new CountingVariantSearchDocumentLookup(documents)
      val backend = experimentalBackend(SearchBackendRoute.ElasticsearchWithQdrantVariantSupplement, lexical, semantic, lookup)

      val response = runIO(backend.search(UserSearchInput("synthetic no candidates", None, None, limit = 10), ParsedSearchIntent("synthetic no candidates", Nil, Nil, Nil, "synthetic no candidates")))

      assert(response == esResponse.copy(
        executionMode = BeautySearchExecutionMode.EsPlusQdrantSupplement,
        qdrantSupplement = QdrantSupplementSummary.usedNoAppend(QdrantSupplementPolicyName.None),
      ))
      assert(lexical.calls == 1)
      assert(semantic.calls == 1)
      assert(lookup.calls == 1)
    }
  }

  "ExperimentalBeautySearchService" should {
    "delegate lexically with empty metadata" in {
      val input = UserSearchInput("synthetic residual service probe", None, None)
      val lexicalResponse = emptyResponse.copy(facets = List(BeautySearchFacet("experimental-lexical", Nil)))
      val lexical = new FakeBeautySearchBackend(lexicalResponse)
      val semantic = new FailingSemanticCandidateBackend
      val lookup = new FailingVariantSearchDocumentLookup
      val service = experimentalService(lexical, semantic, lookup)

      val response = runIO(service.search(input, SearchRoutingMetadata()))

      assert(response == lexicalResponse)
      assert(lexical.calls == 1)
    }

    "route through semantic candidate path with broad semantic metadata" in {
      val knownDocuments = documents.take(2)
      val hits = List(
        SemanticCandidateHit(knownDocuments(1).variantId, 0.92),
        SemanticCandidateHit(knownDocuments(0).variantId, 0.83),
      )
      val input = UserSearchInput("synthetic semantic service probe", None, None, limit = 10)
      val lexical = new FakeBeautySearchBackend(emptyResponse)
      val semantic = new CountingSemanticCandidateBackend(hits)
      val lookup = new CountingVariantSearchDocumentLookup(knownDocuments)
      val service = experimentalService(lexical, semantic, lookup)

      val response = runIO(service.search(input, SearchRoutingMetadata(signal = Some(SearchRoutingSignal.BroadSemanticCandidate))))

      assert(lexical.calls == 0)
      assert(semantic.calls == 1)
      assert(lookup.calls == 1)
      assert(response.variantCarousel.map(_.variantId) == List(knownDocuments(1).variantId, knownDocuments(0).variantId))
      assert(response.variantCarousel.map(_.score) == List(0.92, 0.83))
    }

    "parse input before backend search" in {
      val input = UserSearchInput("маникюр", None, None)
      val expectedIntent = parser.parse(input)
      val lexical = new FakeBeautySearchBackend(emptyResponse)
      val semantic = new CountingSemanticCandidateBackend(Nil)
      val lookup = new CountingVariantSearchDocumentLookup(Nil)
      val service = experimentalService(lexical, semantic, lookup)

      runIO(service.search(input, SearchRoutingMetadata()))

      assert(lexical.lastInput.contains(input))
      assert(lexical.lastIntent.contains(expectedIntent))
      assert(expectedIntent.explicitConstraints.nonEmpty)
    }

    "diagnose broad semantic metadata as QdrantCandidateRoute without backend calls" in {
      val input = UserSearchInput("synthetic semantic diagnostics probe", None, None, limit = 10)
      val lexical = new ThrowingBeautySearchBackend
      val semantic = new ThrowingSemanticCandidateBackend
      val lookup = new ThrowingVariantSearchDocumentLookup
      val service = experimentalService(lexical, semantic, lookup)

      val diagnostics = service.diagnose(input, SearchRoutingMetadata(signal = Some(SearchRoutingSignal.BroadSemanticCandidate)))

      assert(diagnostics == ExperimentalHybridRouteDiagnostics(
        route = SearchBackendRoute.QdrantCandidateRoute,
        routingSignal = Some(SearchRoutingSignal.BroadSemanticCandidate),
        usesLexicalBackend = false,
        usesSemanticBackend = true,
        fallbackRequested = false,
        fallbackImplemented = false,
        reasonCategory = "semantic-candidates",
      ))
    }

    "diagnose preserves the routing signal" in {
      val input = UserSearchInput("synthetic noisy diagnostics probe", None, None)
      val service = experimentalService(
        new ThrowingBeautySearchBackend,
        new ThrowingSemanticCandidateBackend,
        new ThrowingVariantSearchDocumentLookup,
      )

      val diagnostics = service.diagnose(input, SearchRoutingMetadata(signal = Some(SearchRoutingSignal.HardNegativeOrNoiseGuard)))

      assert(diagnostics.route == SearchBackendRoute.ElasticsearchOnly)
      assert(diagnostics.routingSignal.contains(SearchRoutingSignal.HardNegativeOrNoiseGuard))
    }

    "stay separate from the existing BeautySearchService and BeautySearchBackend contracts" in {
      val serviceSearchMethods = classOf[BeautySearchService[IO]].getMethods.filter(_.getName == "search").map(_.getParameterCount).toSet
      val backendSearchMethods = classOf[BeautySearchBackend[IO]].getMethods.filter(_.getName == "search").map(_.getParameterCount).toSet

      assert(serviceSearchMethods == Set(1))
      assert(backendSearchMethods == Set(2))
      assert(!classOf[BeautySearchService[IO]].isAssignableFrom(classOf[ExperimentalBeautySearchService[IO]]))
    }

    "keep residual text on lexical path unless explicit metadata is passed" in {
      val input = UserSearchInput("synthetic residual explicit metadata probe", None, None, limit = 10)
      val intent = parser.parse(input)
      val knownDocument = documents.head
      val lexical = new FakeBeautySearchBackend(emptyResponse.copy(facets = List(BeautySearchFacet("residual-lexical", Nil))))
      val semantic = new CountingSemanticCandidateBackend(List(SemanticCandidateHit(knownDocument.variantId, 0.94)))
      val lookup = new CountingVariantSearchDocumentLookup(List(knownDocument))
      val service = experimentalService(lexical, semantic, lookup)

      val defaultResponse = runIO(service.search(input, SearchRoutingMetadata()))
      val explicitResponse = runIO(service.search(input, SearchRoutingMetadata(signal = Some(SearchRoutingSignal.BroadSemanticCandidate))))

      assert(intent.explicitConstraints.isEmpty)
      assert(intent.softBoosts.isEmpty)
      assert(intent.remainingText.nonEmpty)
      assert(defaultResponse.facets.map(_.fieldPath) == List("residual-lexical"))
      assert(explicitResponse.variantCarousel.map(_.variantId) == List(knownDocument.variantId))
      assert(lexical.calls == 1)
      assert(semantic.calls == 1)
      assert(lookup.calls == 1)
    }

    "keep hard-negative metadata on lexical path without semantic calls" in {
      val input = UserSearchInput("synthetic hard negative service probe", None, None, limit = 10)
      val lexicalResponse = emptyResponse.copy(facets = List(BeautySearchFacet("hard-negative-lexical", Nil)))
      val lexical = new FakeBeautySearchBackend(lexicalResponse)
      val semantic = new FailingSemanticCandidateBackend
      val lookup = new FailingVariantSearchDocumentLookup
      val service = experimentalService(lexical, semantic, lookup)

      val response = runIO(service.search(input, SearchRoutingMetadata(signal = Some(SearchRoutingSignal.HardNegativeOrNoiseGuard))))

      assert(response == lexicalResponse)
      assert(lexical.calls == 1)
    }

    "compose explicit broad semantic metadata through fake-only Qdrant dependencies end-to-end" in {
      val knownDocuments = documents.take(2)
      val vectorSpec = VectorSearchSpec(
        collectionName = "beauty-semantic-test",
        vectorName = "variant-embedding",
        topK = 2,
        scoreThreshold = Some(0.5),
      )
      val spec = BeautySearchSpecV1.spec.copy(vectorSearchSpec = Some(vectorSpec))
      val input = UserSearchInput("synthetic explicit broad semantic service probe", None, None, limit = 10)
      val intent = parser.parse(input)
      val vector = Vector(0.2, 0.4, 0.6)
      val hits = List(
        QdrantSearchHit(
          id = "point-1",
          payload = JsonObject.fromMap(Map("variantId" -> Json.fromString(knownDocuments(1).variantId.toString))),
          score = 0.94,
        ),
        QdrantSearchHit(
          id = "point-2",
          payload = JsonObject.fromMap(Map("variantId" -> Json.fromString(knownDocuments(0).variantId.toString))),
          score = 0.87,
        ),
      )
      val embeddingQueryRef = runZIO(Ref.make(Option.empty[String]))
      val qdrantPathRef = runZIO(Ref.make(Option.empty[String]))
      val lexical = new FailingBeautySearchBackend
      val embeddingClient = new RecordingEmbeddingClient(embeddingQueryRef, vector)
      val qdrantClient = new RecordingQdrantSearchClient(qdrantPathRef, hits)
      val semantic = new QdrantSemanticCandidateBackend(
        new QdrantSemanticCandidateSearch(embeddingClient, qdrantClient, BeautyQVariantSearchDocumentSchema.Fields.variantId),
        vectorSpec,
      )
      val lookup = new InMemoryVariantSearchDocumentLookup[IO](knownDocuments)
      val service = new ExperimentalBeautySearchService[IO](
        parser,
        spec,
        lexical,
        SearchBackendRouter.default,
        semantic,
        lookup,
      )
      val metadata = SearchRoutingMetadata(signal = Some(SearchRoutingSignal.BroadSemanticCandidate))

      val route = SearchBackendRouter.default.decide(input, intent, metadata)
      val response = runIO(service.search(input, metadata))

      assert(route.route == SearchBackendRoute.QdrantCandidateRoute)
      assert(runZIO(embeddingQueryRef.get).contains(input.query))
      assert(runZIO(qdrantPathRef.get).contains("/collections/beauty-semantic-test/points/search"))
      assert(response.variantCarousel.map(_.variantId) == List(knownDocuments(1).variantId, knownDocuments(0).variantId))
      assert(response.variantCarousel.map(_.score) == List(0.94, 0.87))
      assert(response.facets == Nil)
      assert(response.inferredFilters == Nil)
    }
  }

  "ExperimentalHybridRouteDecider" should {
    "return ElasticsearchOnly with default metadata" in {
      val decider = new ExperimentalHybridRouteDecider(
        SearchBackendRouter.default,
        (_, _) => SearchRoutingMetadata(),
      )
      val input = UserSearchInput("synthetic residual probe", None, None)
      val intent = parser.parse(input)

      assert(intent.explicitConstraints.isEmpty)
      assert(intent.softBoosts.isEmpty)
      assert(intent.remainingText.nonEmpty)
      assert(decider.decide(input, intent) == SearchBackendRoute.ElasticsearchOnly)
    }

    "return QdrantCandidateRoute with broad semantic metadata" in {
      val decider = new ExperimentalHybridRouteDecider(
        SearchBackendRouter.default,
        (_, _) => SearchRoutingMetadata(signal = Some(SearchRoutingSignal.BroadSemanticCandidate)),
      )
      val input = UserSearchInput("synthetic semantic discovery", None, None)
      val intent = parser.parse(input)

      assert(intent.explicitConstraints.isEmpty)
      assert(intent.softBoosts.isEmpty)
      assert(intent.remainingText.nonEmpty)
      assert(decider.decide(input, intent) == SearchBackendRoute.QdrantCandidateRoute)
    }

    "keep residual text alone on ElasticsearchOnly" in {
      val decider = new ExperimentalHybridRouteDecider(
        SearchBackendRouter.default,
        (_, _) => SearchRoutingMetadata(),
      )
      val input = UserSearchInput("beauty near Wandsbek Markt", None, None)
      val intent = parser.parse(input)

      assert(intent.explicitConstraints.isEmpty)
      assert(intent.softBoosts.isEmpty)
      assert(intent.remainingText.nonEmpty)
      assert(decider.decide(input, intent) == SearchBackendRoute.ElasticsearchOnly)
    }

    "keep hard-negative or noise metadata on ElasticsearchOnly" in {
      val decider = new ExperimentalHybridRouteDecider(
        SearchBackendRouter.default,
        (_, _) => SearchRoutingMetadata(signal = Some(SearchRoutingSignal.HardNegativeOrNoiseGuard)),
      )
      val input = UserSearchInput("synthetic noisy probe", None, None)
      val intent = parser.parse(input)

      assert(intent.explicitConstraints.isEmpty)
      assert(intent.softBoosts.isEmpty)
      assert(intent.remainingText.nonEmpty)
      assert(decider.decide(input, intent) == SearchBackendRoute.ElasticsearchOnly)
    }

    "provide the route decision function accepted by ExperimentalHybridSearchBackend" in {
      val knownDocuments = documents.take(1)
      val hits = List(SemanticCandidateHit(knownDocuments.head.variantId, 0.93))
      val input = UserSearchInput("synthetic semantic backend adapter", None, None, limit = 10)
      val intent = parser.parse(input)
      val lexical = new FakeBeautySearchBackend(emptyResponse)
      val semantic = new CountingSemanticCandidateBackend(hits)
      val lookup = new CountingVariantSearchDocumentLookup(knownDocuments)
      val decider = new ExperimentalHybridRouteDecider(
        SearchBackendRouter.default,
        (_, _) => SearchRoutingMetadata(signal = Some(SearchRoutingSignal.BroadSemanticCandidate)),
      )
      val backend = new ExperimentalHybridSearchBackend[IO](
        BeautySearchSpecV1.spec,
        lexical,
        decider.toRouteDecision,
        semantic,
        lookup,
      )

      val response = runIO(backend.search(input, intent))

      assert(lexical.calls == 0)
      assert(semantic.calls == 1)
      assert(lookup.calls == 1)
      assert(response.variantCarousel.map(_.variantId) == knownDocuments.map(_.variantId))
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
      val mapping = BeautyQElasticsearchInterpreterAdapter.mapping(BeautySearchSpecV1.spec)
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

      val mapping = BeautyQElasticsearchInterpreterAdapter.mapping(syntheticSpec)
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

      val source = BeautyQElasticsearchInterpreterAdapter.sourceJson(syntheticSpec, documents.head)
      assert(source.hcursor.downField("testSyntheticKeyword").as[String] == Right("synthetic-value"))
    }
  }

  "ElasticsearchSearchRequestInterpreter" should {
    "derive filters, searchable fields and facets from the provided spec" in {
      val serviceNameField = SearchField[VariantSearchDocument](
        path = "serviceName",
        kind = SearchFieldKind.Keyword,
        extract = document => Some(SearchValue.Keyword(document.serviceName)),
        semantic = Some(SearchFieldSemantic.ServiceName),
        filterable = true,
        facetable = true,
      )
      val customTextField = SearchField[VariantSearchDocument](
        path = "customText",
        kind = SearchFieldKind.Text,
        extract = document => Some(SearchValue.Text(document.serviceText)),
        searchable = true,
        boost = 7.0,
      )
      val providerKeyField = SearchField[VariantSearchDocument](
        path = "providerKey",
        kind = SearchFieldKind.Keyword,
        extract = document => Some(SearchValue.Keyword(document.masterLocationId.toString)),
        filterable = true,
      )
      val serviceKeyField = SearchField[VariantSearchDocument](
        path = "serviceKey",
        kind = SearchFieldKind.Keyword,
        extract = document => Some(SearchValue.Keyword(document.serviceId.toString)),
        filterable = true,
      )
      val customSpec = BeautySearchSpec(
        variantDocument = SearchDocumentSpec(
          indexName = "custom",
          id = _.variantId.toString,
          fields = List(serviceNameField, customTextField, providerKeyField, serviceKeyField),
        ),
        intentVocabulary = SearchIntentVocabulary(Nil),
        carouselSpec = CarouselSpec(providerGroupField = providerKeyField, serviceIntentGroupField = serviceKeyField),
        facetSpec = FacetSpec(enabled = true, fields = List(FacetField(serviceNameField, FacetFieldMode.Terms))),
        querySchema = querySchemaFor(serviceNameField),
      )

      val request = BeautyQElasticsearchInterpreterAdapter.request(
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
      val serviceNameField = SearchField[VariantSearchDocument](
        path = "serviceName2",
        kind = SearchFieldKind.Keyword,
        extract = document => Some(SearchValue.Keyword(document.serviceName)),
        semantic = Some(SearchFieldSemantic.ServiceName),
        filterable = true,
      )
      val syntheticSpec = BeautySearchSpec(
        variantDocument = SearchDocumentSpec(
          indexName = "custom-semantic",
          id = _.variantId.toString,
          fields = List(serviceNameField),
        ),
        intentVocabulary = SearchIntentVocabulary(Nil),
        carouselSpec = CarouselSpec(providerGroupField = serviceNameField, serviceIntentGroupField = serviceNameField),
        facetSpec = FacetSpec(enabled = false, fields = Nil),
        querySchema = querySchemaFor(serviceNameField),
      )

      val request = BeautyQElasticsearchInterpreterAdapter.request(
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
      val syntheticFacet = FacetField(BeautyQVariantSearchDocumentSchema.Fields.serviceName, FacetFieldMode.Terms, limit = 3)
      val specWithFacet = BeautySearchSpecV1.spec.copy(
        facetSpec = BeautySearchSpecV1.spec.facetSpec.copy(fields = BeautySearchSpecV1.spec.facetSpec.fields :+ syntheticFacet)
      )
      val specWithoutFacet = BeautySearchSpecV1.spec.copy(
        facetSpec = BeautySearchSpecV1.spec.facetSpec.copy(fields = BeautySearchSpecV1.spec.facetSpec.fields.filterNot(_.path == "serviceName"))
      )

      val input = UserSearchInput("query", None, None)
      val intent = ParsedSearchIntent("query", List("query"), Nil, Nil, "")
      val withFacet = BeautyQElasticsearchInterpreterAdapter.request(specWithFacet, input, intent) match {
        case Right(j) => j
        case Left(e)  => fail(s"expected Right, got: $e")
      }
      val withoutFacet = BeautyQElasticsearchInterpreterAdapter.request(specWithoutFacet, input, intent) match {
        case Right(j) => j
        case Left(e)  => fail(s"expected Right, got: $e")
      }

      assert(withFacet.hcursor.downField("aggs").downField("agg_serviceName").focus.nonEmpty)
      assert(withoutFacet.hcursor.downField("aggs").downField("agg_serviceName").focus.isEmpty)
    }

    "use requestSpec.hitWindowSize for request size" in {
      val customSpec = BeautySearchSpecV1.spec.copy(
        requestSpec = BeautySearchSpecV1.spec.requestSpec.copy(hitWindowSize = 7)
      )

      val request = BeautyQElasticsearchInterpreterAdapter.request(
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
        facetSpec = BeautySearchSpecV1.spec.facetSpec.copy(fields = List(FacetField(BeautyQVariantSearchDocumentSchema.Fields.serviceName, FacetFieldMode.Terms, limit = 3))),
        requestSpec = BeautySearchSpecV1.spec.requestSpec.copy(aggregationSize = 11),
      )

      val request = BeautyQElasticsearchInterpreterAdapter.request(
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

      val request = BeautyQElasticsearchInterpreterAdapter.request(
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

      val request = BeautyQElasticsearchInterpreterAdapter.request(
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
        sourceTextFields = List(BeautyQVariantSearchDocumentSchema.Fields.serviceName),
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

    "use intent vocabulary data from the spec" in {
      val syntheticSpec = BeautySearchSpec(
        variantDocument = BeautySearchSpecV1.spec.variantDocument,
        intentVocabulary = BeautySearchSpecV1.spec.intentVocabulary.copy(
          rules = BeautySearchSpecV1.spec.intentVocabulary.rules :+ SearchIntentRule.StructuredAlias(
            tokens = Set("synthetic keyword"),
            constraints = List(SearchConstraint.ServiceAny(Set("Маникюр"))),
            matchMode = IntentMatchMode.Phrase,
          )
        ),
        carouselSpec = BeautySearchSpecV1.spec.carouselSpec,
        facetSpec = BeautySearchSpecV1.spec.facetSpec,
        querySchema = BeautySearchSpecV1.spec.querySchema,
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
        SemanticCandidateHit(candidateDocuments(1).variantId, 0.82),
        SemanticCandidateHit(candidateDocuments(0).variantId, 0.91),
      )

      def routeAndMaybeAssemble(
        input: UserSearchInput,
        metadata: SearchRoutingMetadata = SearchRoutingMetadata(),
        hits: List[SemanticCandidateHit] = Nil,
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

    "never select ElasticsearchWithQdrantVariantSupplement by default" in {
      val router = SearchBackendRouter.default
      val inputs = List(
        UserSearchInput("маникюр", None, None),
        UserSearchInput("beauty near Wandsbek Markt", None, None),
        UserSearchInput("synthetic semantic probe", None, None),
      )
      val metadatas = List(
        SearchRoutingMetadata(),
        SearchRoutingMetadata(signal = Some(SearchRoutingSignal.BroadSemanticCandidate)),
        SearchRoutingMetadata(signal = Some(SearchRoutingSignal.HardNegativeOrNoiseGuard)),
      )

      for {
        input <- inputs
        metadata <- metadatas
      } {
        val decision = router.decide(input, parser.parse(input), metadata)
        assert(decision.route != SearchBackendRoute.ElasticsearchWithQdrantVariantSupplement, s"${input.query}/$metadata must not select the supplement route by default")
      }
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

  private def assembleQdrantCandidates(hits: List[SemanticCandidateHit]) =
    QdrantCandidateAssembler.assemble(hits, documents)

  private def variantResultsFor(hits: List[SemanticCandidateHit]): List[VariantSearchResult] =
    QdrantCandidateResponseProjector
      .project(
        BeautySearchSpecV1.spec,
        UserSearchInput("synthetic es fixture", None, None, limit = 10),
        QdrantCandidateAssembler.assemble(hits, documents),
      )
      .variantCarousel

  private def emptyResponse =
    BeautySearchResponse(
      variantCarousel = Nil,
      providerCarousel = Nil,
      serviceIntentCarousel = Nil,
      facets = Nil,
      inferredFilters = Nil,
    )

  private def experimentalBackend(
    route: SearchBackendRoute,
    lexical: FakeBeautySearchBackend,
    semantic: SemanticCandidateBackend[IO],
    lookup: VariantSearchDocumentLookup[IO],
  ) =
    new ExperimentalHybridSearchBackend[IO](
      BeautySearchSpecV1.spec,
      lexical,
      (_, _) => route,
      semantic,
      lookup,
    )

  private def experimentalService(
    lexical: BeautySearchBackend[IO],
    semantic: SemanticCandidateBackend[IO],
    lookup: VariantSearchDocumentLookup[IO],
  ) =
    new ExperimentalBeautySearchService[IO](
      parser,
      BeautySearchSpecV1.spec,
      lexical,
      SearchBackendRouter.default,
      semantic,
      lookup,
    )

  private final case class BackendProbe(
    calls: Int,
    lastInput: Option[UserSearchInput],
    lastIntent: Option[ParsedSearchIntent],
  )

  private object BackendProbe {
    val empty: BackendProbe = BackendProbe(calls = 0, lastInput = None, lastIntent = None)
  }

  private final class FakeBeautySearchBackend(response: BeautySearchResponse) extends BeautySearchBackend[IO] {
    private val probe = runZIO(Ref.make(BackendProbe.empty))

    def calls: Int = runZIO(probe.get.map(_.calls))
    def lastInput: Option[UserSearchInput] = runZIO(probe.get.map(_.lastInput))
    def lastIntent: Option[ParsedSearchIntent] = runZIO(probe.get.map(_.lastIntent))

    override def search(input: UserSearchInput, intent: ParsedSearchIntent): IO[QueryFailure, BeautySearchResponse] =
      for {
        _ <- probe.update { state =>
          state.copy(
            calls = state.calls + 1,
            lastInput = Some(input),
            lastIntent = Some(intent),
          )
      }
      } yield response
  }

  private final class FailingBeautySearchBackend extends BeautySearchBackend[IO] {
    override def search(input: UserSearchInput, intent: ParsedSearchIntent): IO[QueryFailure, BeautySearchResponse] =
      ZIO.dieMessage(s"Lexical backend must not be called for ${input.query}: $intent")
  }

  private final class ThrowingBeautySearchBackend extends BeautySearchBackend[IO] {
    override def search(input: UserSearchInput, intent: ParsedSearchIntent): IO[QueryFailure, BeautySearchResponse] =
      throw new IllegalStateException(s"Lexical backend must not be invoked for ${input.query}: $intent")
  }

  private final case class SemanticProbe(
    calls: Int,
    lastInput: Option[UserSearchInput],
    lastIntent: Option[ParsedSearchIntent],
  )

  private object SemanticProbe {
    val empty: SemanticProbe = SemanticProbe(calls = 0, lastInput = None, lastIntent = None)
  }

  private final class CountingSemanticCandidateBackend(hits: List[SemanticCandidateHit]) extends SemanticCandidateBackend[IO] {
    private val probe = runZIO(Ref.make(SemanticProbe.empty))

    def calls: Int = runZIO(probe.get.map(_.calls))
    def lastInput: Option[UserSearchInput] = runZIO(probe.get.map(_.lastInput))
    def lastIntent: Option[ParsedSearchIntent] = runZIO(probe.get.map(_.lastIntent))

    override def candidates(input: UserSearchInput, intent: ParsedSearchIntent): IO[QueryFailure, List[SemanticCandidateHit]] =
      for {
        _ <- probe.update { state =>
          state.copy(
            calls = state.calls + 1,
            lastInput = Some(input),
            lastIntent = Some(intent),
          )
        }
      } yield hits
  }

  private final class FailingSemanticCandidateBackend extends SemanticCandidateBackend[IO] {
    override def candidates(input: UserSearchInput, intent: ParsedSearchIntent): IO[QueryFailure, List[SemanticCandidateHit]] =
      ZIO.dieMessage(s"Semantic backend must not be called for ${input.query}: $intent")
  }

  private final class ThrowingSemanticCandidateBackend extends SemanticCandidateBackend[IO] {
    override def candidates(input: UserSearchInput, intent: ParsedSearchIntent): IO[QueryFailure, List[SemanticCandidateHit]] =
      throw new IllegalStateException(s"Semantic backend must not be invoked for ${input.query}: $intent")
  }

  private final case class LookupProbe(
    calls: Int,
    lastVariantIds: Option[List[MasterServiceOfferVariantId]],
  )

  private object LookupProbe {
    val empty: LookupProbe = LookupProbe(calls = 0, lastVariantIds = None)
  }

  private final class CountingVariantSearchDocumentLookup(documents: List[VariantSearchDocument]) extends VariantSearchDocumentLookup[IO] {
    private val documentsById = documents.iterator.map(document => document.variantId -> document).toMap
    private val probe = runZIO(Ref.make(LookupProbe.empty))

    def calls: Int = runZIO(probe.get.map(_.calls))
    def lastVariantIds: Option[List[MasterServiceOfferVariantId]] = runZIO(probe.get.map(_.lastVariantIds))

    override def lookup(variantIds: List[MasterServiceOfferVariantId]): IO[QueryFailure, Map[MasterServiceOfferVariantId, VariantSearchDocument]] =
      for {
        _ <- probe.update { state =>
          state.copy(
            calls = state.calls + 1,
            lastVariantIds = Some(variantIds),
          )
        }
      } yield {
        variantIds.iterator.flatMap(variantId => documentsById.get(variantId).map(document => variantId -> document)).toMap
      }
  }

  private final class FailingVariantSearchDocumentLookup extends VariantSearchDocumentLookup[IO] {
    override def lookup(variantIds: List[MasterServiceOfferVariantId]): IO[QueryFailure, Map[MasterServiceOfferVariantId, VariantSearchDocument]] =
      ZIO.dieMessage(s"Variant lookup must not be called for $variantIds")
  }

  private final class ThrowingVariantSearchDocumentLookup extends VariantSearchDocumentLookup[IO] {
    override def lookup(variantIds: List[MasterServiceOfferVariantId]): IO[QueryFailure, Map[MasterServiceOfferVariantId, VariantSearchDocument]] =
      throw new IllegalStateException(s"Variant lookup must not be invoked for $variantIds")
  }

  private final class FakeSemanticCandidateBackend(
    expectedInput: UserSearchInput,
    expectedIntent: ParsedSearchIntent,
    hits: List[SemanticCandidateHit],
  ) extends SemanticCandidateBackend[IO] {
    override def candidates(input: UserSearchInput, intent: ParsedSearchIntent): IO[QueryFailure, List[SemanticCandidateHit]] = ZIO.succeed {
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

  private final class RecordingEmbeddingClient(
    queryRef: Ref[Option[String]],
    vector: Vector[Double],
  ) extends EmbeddingClient {
    override def embed(text: String): IO[QueryFailure, Vector[Double]] =
      queryRef.set(Some(text)) *> ZIO.succeed(vector)
  }

  private final class RecordingQdrantSearchClient(
    pathRef: Ref[Option[String]],
    hits: List[QdrantSearchHit],
  ) extends QdrantSearchClient {
    override def search(path: String, json: Json): IO[QueryFailure, List[QdrantSearchHit]] =
      pathRef.set(Some(path)) *> ZIO.succeed(hits)
  }

  private def querySchemaFor(serviceNameField: SearchField[VariantSearchDocument]): SearchQuerySchema[VariantSearchDocument] =
    SearchQuerySchema(
      serviceName = serviceNameField,
      categoryName = serviceNameField,
      priceFrom = serviceNameField,
      durationMin = serviceNameField,
      location = serviceNameField,
      enumAttribute = missingAttributeField,
      booleanAttribute = missingAttributeField,
      intAttribute = missingAttributeField,
      decimalAttribute = missingAttributeField,
    )

  private def missingAttributeField(code: String): Either[QueryFailure, SearchField[VariantSearchDocument]] =
    Left(QueryFailure.domain(s"Missing synthetic attribute field '$code'"))

  private def runIO[A](effect: IO[QueryFailure, A]): A =
    runZIO(effect)

  private def runZIO[E, A](effect: ZIO[Any, E, A]): A =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
    }

}
