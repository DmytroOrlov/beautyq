package leaderboard.search

import io.circe.Json
import leaderboard.model.*
import leaderboard.search.dsl.*
import leaderboard.search.document.{BeautySearchCatalogSnapshot, VariantSearchDocument, VariantSearchDocumentBuilder}
import leaderboard.search.elasticsearch.{ElasticsearchMappingInterpreter, ElasticsearchSearchRequestInterpreter}
import leaderboard.search.eval.{BeautySearchEvalLoader, BeautySearchEvalScorer}
import leaderboard.search.inmemory.InMemorySearchBackend
import leaderboard.search.parser.BeautySearchIntentParser
import leaderboard.seed.BeautyQSeedLoader
import org.scalatest.wordspec.AnyWordSpec
import zio.{IO, Runtime, Unsafe}

import java.nio.file.Paths

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

  private val evalSuite = BeautySearchEvalLoader.load(Paths.get("beautyq_search_eval_queries_v1.json")) match {
    case Right(value) => value
    case Left(error) => throw new RuntimeException(error.message)
  }

  private val firstMilestoneQueryIds = Set(
    "q_nails_001",
    "q_nails_006",
    "q_nails_009",
    "q_lashes_001",
    "q_lashes_002",
    "q_brows_005",
    "q_pmu_001",
    "q_pmu_005",
    "q_face_001",
    "q_face_002",
    "q_face_004",
    "q_face_008",
  )

  "BeautySearchSpecV1" should {
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
  }

  "InMemorySearchBackend" should {
    "return acceptable variant, provider and service ids for the first milestone eval subset" in {
      val backend = new InMemorySearchBackend[IO](BeautySearchSpecV1.spec, documents)
      val service = new BeautySearchService.Impl[IO](parser, backend)

      evalSuite.queries.filter(query => firstMilestoneQueryIds.contains(query.id)).foreach { query =>
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
  }

  private def jsonContainsString(json: Json, needle: String): Boolean =
    json.noSpaces.contains(needle)

  private def runIO[A](effect: IO[QueryFailure, A]): A =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
    }
}
