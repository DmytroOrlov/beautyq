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
import leaderboard.seed.BeautyQSeedLoader
import org.scalatest.wordspec.AnyWordSpec
import zio.{IO, Runtime, Unsafe}

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

  private def runIO[A](effect: IO[QueryFailure, A]): A =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
    }

}
