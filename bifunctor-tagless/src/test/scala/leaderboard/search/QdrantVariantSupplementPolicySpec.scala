package leaderboard.search

import cats.effect.Async
import cats.syntax.all.*
import distage.{Injector, ModuleDef}
import fs2.text
import io.circe.Json
import izumi.distage.model.definition.{Activation, LocatorPrivacy}
import izumi.distage.model.plan.Roots
import leaderboard.api.{BeautySearchApi, BeautySearchServingGate, HttpApi}
import leaderboard.model.{MasterServiceOfferVariantId, QueryFailure}
import leaderboard.plugins.BeautySearchRouteModules
import leaderboard.search.document.VariantSearchDocument
import leaderboard.search.dsl.{BeautySearchSpecV1, SearchConstraint, SearchGeoPoint}
import leaderboard.search.elasticsearch.ElasticsearchJsonClient
import leaderboard.search.hybrid.{ExperimentalHybridSearchBackend, QdrantVariantSupplementPolicy}
import leaderboard.search.routing.SearchBackendRoute
import leaderboard.search.semantic.{SemanticCandidateBackend, SemanticCandidateHit, VariantSearchDocumentLookup}
import leaderboard.{HttpContractTestSupport, ObservedResponse}
import org.http4s.{HttpApp, Request, Status}
import org.scalatest.wordspec.AnyWordSpec
import zio.interop.catz.*
import zio.{IO, Runtime, Task, Unsafe, ZIO}

import java.util.UUID

final class QdrantVariantSupplementPolicySpec extends AnyWordSpec with HttpContractTestSupport {
  private val spec = BeautySearchSpecV1.spec

  "QdrantVariantSupplementPolicy.ExplicitConstraintsFilterPlusTop1" should {
    "append at most one Qdrant-only candidate even with several survivors and no constraints" in {
      val hits = List(
        SemanticCandidateHit(variantId(1), 0.9),
        SemanticCandidateHit(variantId(2), 0.8),
        SemanticCandidateHit(variantId(3), 0.7),
      )
      val documentsById = hits.map(hit => hit.variantId -> document(hit.variantId)).toMap

      val selected = QdrantVariantSupplementPolicy.ExplicitConstraintsFilterPlusTop1.select(
        intent = intentWithConstraints(Nil),
        esVariantIds = Set.empty,
        qdrantHits = hits,
        documentsById = documentsById,
        capRoom = 3,
      )

      assert(selected == List(hits.head))
    }

    "filter by explicit constraints before selecting the top survivor" in {
      val matchingId = variantId(1)
      val nonMatchingId = variantId(2)
      val hits = List(
        SemanticCandidateHit(nonMatchingId, 0.95), // highest score, but wrong service
        SemanticCandidateHit(matchingId, 0.80), // lower score, but satisfies the constraint
      )
      val documentsById = Map(
        nonMatchingId -> document(nonMatchingId, serviceName = "Massage"),
        matchingId -> document(matchingId, serviceName = "Manicure"),
      )

      val selected = QdrantVariantSupplementPolicy.ExplicitConstraintsFilterPlusTop1.select(
        intent = intentWithConstraints(List(SearchConstraint.ServiceAny(Set("Manicure")))),
        esVariantIds = Set.empty,
        qdrantHits = hits,
        documentsById = documentsById,
        capRoom = 3,
      )

      assert(selected == List(SemanticCandidateHit(matchingId, 0.80)))
    }

    "never append a hit whose id is already present in the ES variant carousel" in {
      val esOwnedId = variantId(1)
      val qdrantOnlyId = variantId(2)
      val hits = List(
        SemanticCandidateHit(esOwnedId, 0.99),
        SemanticCandidateHit(qdrantOnlyId, 0.50),
      )
      val documentsById = Map(
        esOwnedId -> document(esOwnedId),
        qdrantOnlyId -> document(qdrantOnlyId),
      )

      val selected = QdrantVariantSupplementPolicy.ExplicitConstraintsFilterPlusTop1.select(
        intent = intentWithConstraints(Nil),
        esVariantIds = Set(esOwnedId),
        qdrantHits = hits,
        documentsById = documentsById,
        capRoom = 3,
      )

      assert(selected == List(SemanticCandidateHit(qdrantOnlyId, 0.50)))
    }

    "never exceed the cap-room granted by the caller" in {
      val hits = List(SemanticCandidateHit(variantId(1), 0.9))
      val documentsById = Map(variantId(1) -> document(variantId(1)))

      val selected = QdrantVariantSupplementPolicy.ExplicitConstraintsFilterPlusTop1.select(
        intent = intentWithConstraints(Nil),
        esVariantIds = Set.empty,
        qdrantHits = hits,
        documentsById = documentsById,
        capRoom = 0,
      )

      assert(selected == Nil)
    }

    "not silently approve candidates that fail any supported constraint type" in {
      val id = variantId(1)
      val baseDocument = document(
        id,
        serviceName = "Manicure",
        categoryName = "Nails",
        enumAttributes = Map("finish" -> "matte"),
        booleanAttributes = Map("organic" -> false),
        intAttributes = Map("sessions" -> 1),
        bigDecimalAttributes = Map("rating" -> BigDecimal(4.0)),
        priceFrom = BigDecimal(10),
        priceTo = BigDecimal(20),
        durationMin = 30,
      )
      val documentsById = Map(id -> baseDocument)
      val hits = List(SemanticCandidateHit(id, 0.9))

      val failingConstraints = List(
        SearchConstraint.ServiceAny(Set("Massage")),
        SearchConstraint.CategoryAny(Set("Hair")),
        SearchConstraint.EnumAttr("finish", Set("glossy")),
        SearchConstraint.BoolAttr("organic", true),
        SearchConstraint.IntRange("sessions", Some(2), None),
        SearchConstraint.DecimalRange("rating", Some(BigDecimal(4.5)), None),
        SearchConstraint.PriceRange(Some(BigDecimal(30)), None),
        SearchConstraint.DurationRange(Some(60), None),
      )

      failingConstraints.foreach { constraint =>
        val selected = QdrantVariantSupplementPolicy.ExplicitConstraintsFilterPlusTop1.select(
          intent = intentWithConstraints(List(constraint)),
          esVariantIds = Set.empty,
          qdrantHits = hits,
          documentsById = documentsById,
          capRoom = 3,
        )
        assert(selected == Nil, s"expected $constraint to reject the candidate, but it was appended")
      }

      // NearUser must not gate candidate quality by itself.
      val withNearUser = QdrantVariantSupplementPolicy.ExplicitConstraintsFilterPlusTop1.select(
        intent = intentWithConstraints(List(SearchConstraint.NearUser)),
        esVariantIds = Set.empty,
        qdrantHits = hits,
        documentsById = documentsById,
        capRoom = 3,
      )
      assert(withNearUser == hits)
    }
  }

  "ExperimentalHybridSearchBackend with ExplicitConstraintsFilterPlusTop1" should {
    "preserve the ES prefix/order and non-variant ES-owned components" in {
      val esDocuments = List(document(variantId(1)), document(variantId(2)))
      val esVariants = esDocuments.map(toVariantResult(_, score = 0.9))
      val esResponse = BeautySearchResponse(
        variantCarousel = esVariants,
        providerCarousel = List(ProviderSearchResult(fixedMasterId, "p", fixedMasterLocationId, "loc", "addr", 1, Nil, 1.0, None)),
        serviceIntentCarousel = List(ServiceIntentSearchResult(fixedServiceId, "svc", fixedCategoryId, "cat", 1, 1.0)),
        facets = List(BeautySearchFacet("es-facet", Nil)),
        inferredFilters = List(BeautySearchAppliedFilter(SearchConstraint.ServiceAny(Set("Manicure")), explicit = true)),
      )
      val matchingId = variantId(3)
      val nonMatchingId = variantId(4)
      val matchingDocument = document(matchingId, serviceName = "Manicure")
      val nonMatchingDocument = document(nonMatchingId, serviceName = "Massage")

      val lexical = new StubBeautySearchBackend(esResponse)
      val semantic = new StubSemanticCandidateBackend(List(
        SemanticCandidateHit(nonMatchingId, 0.95),
        SemanticCandidateHit(matchingId, 0.70),
      ))
      val lookup = new StubVariantSearchDocumentLookup(List(matchingDocument, nonMatchingDocument))
      val intent = intentWithConstraints(List(SearchConstraint.ServiceAny(Set("Manicure"))))

      val backend = new ExperimentalHybridSearchBackend[IO](
        spec,
        lexical,
        (_, _) => SearchBackendRoute.ElasticsearchWithQdrantVariantSupplement,
        semantic,
        lookup,
        QdrantVariantSupplementPolicy.ExplicitConstraintsFilterPlusTop1,
      )

      val response = runIO(backend.search(UserSearchInput("synthetic supplement", None, None, limit = 10), intent))

      assert(response.variantCarousel.take(2) == esVariants)
      assert(response.variantCarousel.map(_.variantId) == List(esDocuments(0).variantId, esDocuments(1).variantId, matchingId))
      assert(response.providerCarousel == esResponse.providerCarousel)
      assert(response.serviceIntentCarousel == esResponse.serviceIntentCarousel)
      assert(response.facets == esResponse.facets)
      assert(response.inferredFilters == esResponse.inferredFilters)
    }

    "never exceed the carousel cap" in {
      val esDocuments = List(document(variantId(1)), document(variantId(2)))
      val esVariants = esDocuments.map(toVariantResult(_, score = 0.9))
      val esResponse = BeautySearchResponse(esVariants, Nil, Nil, Nil, Nil)
      val candidateId = variantId(3)
      val candidateDocument = document(candidateId)

      val lexical = new StubBeautySearchBackend(esResponse)
      val semantic = new StubSemanticCandidateBackend(List(SemanticCandidateHit(candidateId, 0.9)))
      val lookup = new StubVariantSearchDocumentLookup(List(candidateDocument))

      val backend = new ExperimentalHybridSearchBackend[IO](
        spec,
        lexical,
        (_, _) => SearchBackendRoute.ElasticsearchWithQdrantVariantSupplement,
        semantic,
        lookup,
        QdrantVariantSupplementPolicy.ExplicitConstraintsFilterPlusTop1,
      )

      // limit == ES carousel size already, so there is zero cap room left for the supplement.
      val response = runIO(backend.search(UserSearchInput("synthetic cap", None, None, limit = 2), intentWithConstraints(Nil)))

      assert(response == esResponse)
    }
  }

  "LeaderboardPlugin's default Elasticsearch route" should {
    "remain unchanged and not include the no-worsening opt-in supplement module" in {
      val probe = buildDefaultEsProbe()

      assert(probe.allHttpApis.size == 1)
      assert(probe.allHttpApis.collect { case api: BeautySearchApi[IO] => api }.size == 1)
    }
  }

  "BeautySearchRouteModules.apiQdrantVariantSupplementExplicitOptIn" should {
    "be constructible in a focused module proof without being selected by default, and serve the supplement" in {
      val probe = buildOptInProbe()
      val apis = probe.allHttpApis

      assert(apis.size == 1)
      assert(apis.collect { case api: BeautySearchApi[IO] => api }.size == 1)

      val response = runIO(
        observeRoute(apis, postJson("/beauty-search", """{"query":"маникюр","limit":10}"""))
      )

      assert(response.status == Status.Ok)
    }
  }

  private def variantId(i: Int): MasterServiceOfferVariantId =
    UUID.fromString(f"00000000-0000-0000-0000-$i%012d")

  private val fixedMasterServiceOfferId = UUID.fromString("10000000-0000-0000-0000-000000000000")
  private val fixedMasterLocationId = UUID.fromString("20000000-0000-0000-0000-000000000000")
  private val fixedMasterId = UUID.fromString("30000000-0000-0000-0000-000000000000")
  private val fixedServiceId = UUID.fromString("40000000-0000-0000-0000-000000000000")
  private val fixedCategoryId = UUID.fromString("50000000-0000-0000-0000-000000000000")

  private def document(
    id: MasterServiceOfferVariantId,
    serviceName: String = "Manicure",
    categoryName: String = "Nails",
    enumAttributes: Map[String, String] = Map.empty,
    booleanAttributes: Map[String, Boolean] = Map.empty,
    intAttributes: Map[String, Int] = Map.empty,
    bigDecimalAttributes: Map[String, BigDecimal] = Map.empty,
    priceFrom: BigDecimal = BigDecimal(10),
    priceTo: BigDecimal = BigDecimal(20),
    durationMin: Int = 30,
  ): VariantSearchDocument =
    VariantSearchDocument(
      variantId = id,
      masterServiceOfferId = fixedMasterServiceOfferId,
      masterLocationId = fixedMasterLocationId,
      masterId = fixedMasterId,
      serviceId = fixedServiceId,
      categoryId = fixedCategoryId,
      serviceName = serviceName,
      categoryName = categoryName,
      masterName = "Test Master",
      locationName = "Test Location",
      address = "Test Address",
      location = SearchGeoPoint(BigDecimal(53.5), BigDecimal(10.0)),
      lat = BigDecimal(53.5),
      lon = BigDecimal(10.0),
      priceFrom = priceFrom,
      priceTo = priceTo,
      durationMin = durationMin,
      enumAttributes = enumAttributes,
      booleanAttributes = booleanAttributes,
      intAttributes = intAttributes,
      bigDecimalAttributes = bigDecimalAttributes,
      allText = "",
      serviceText = "",
      attributeText = "",
      providerText = "",
      locationText = "",
    )

  private def toVariantResult(document: VariantSearchDocument, score: Double): VariantSearchResult =
    VariantSearchResult(
      variantId = document.variantId,
      masterServiceOfferId = document.masterServiceOfferId,
      masterLocationId = document.masterLocationId,
      masterId = document.masterId,
      serviceId = document.serviceId,
      categoryId = document.categoryId,
      serviceName = document.serviceName,
      categoryName = document.categoryName,
      masterName = document.masterName,
      locationName = document.locationName,
      address = document.address,
      lat = document.lat,
      lon = document.lon,
      priceFrom = document.priceFrom,
      priceTo = document.priceTo,
      durationMin = document.durationMin,
      enumAttributes = document.enumAttributes,
      booleanAttributes = document.booleanAttributes,
      intAttributes = document.intAttributes,
      bigDecimalAttributes = document.bigDecimalAttributes,
      score = score,
      distanceKm = None,
    )

  private def intentWithConstraints(constraints: List[SearchConstraint]): ParsedSearchIntent =
    ParsedSearchIntent(
      originalQuery = "synthetic",
      normalizedTokens = Nil,
      explicitConstraints = constraints,
      softBoosts = Nil,
      remainingText = "",
    )

  private final class StubBeautySearchBackend(response: BeautySearchResponse) extends BeautySearchBackend[IO] {
    override def search(input: UserSearchInput, intent: ParsedSearchIntent): IO[QueryFailure, BeautySearchResponse] =
      ZIO.succeed(response)
  }

  private final class StubSemanticCandidateBackend(hits: List[SemanticCandidateHit]) extends SemanticCandidateBackend[IO] {
    override def candidates(input: UserSearchInput, intent: ParsedSearchIntent): IO[QueryFailure, List[SemanticCandidateHit]] =
      ZIO.succeed(hits)
  }

  private final class StubVariantSearchDocumentLookup(documents: List[VariantSearchDocument]) extends VariantSearchDocumentLookup[IO] {
    private val documentsById = documents.iterator.map(document => document.variantId -> document).toMap

    override def lookup(variantIds: List[MasterServiceOfferVariantId]): IO[QueryFailure, Map[MasterServiceOfferVariantId, VariantSearchDocument]] =
      ZIO.succeed(variantIds.iterator.flatMap(variantId => documentsById.get(variantId).map(variantId -> _)).toMap)
  }

  private def buildDefaultEsProbe(): DefaultEsRouteProbe = {
    val module = new ModuleDef {
      // The exact backend composition `BeautySearchRouteModules.apiElasticsearch` (LeaderboardPlugin's
      // default Beauty search route) delegates to, modulo the port-configuration layer.
      include(BeautySearchRouteModules.seedCatalogElasticsearch)
      make[Async[Task]].fromValue(Async[Task])
      make[ElasticsearchJsonClient].from(mockEsClient)
      make[DefaultEsRouteProbe].from {
        (beautySearchApi: BeautySearchApi[IO], allHttpApis: Set[HttpApi[IO]]) =>
          val _ = beautySearchApi
          DefaultEsRouteProbe(allHttpApis)
      }
    }

    val locator = Injector().produce(
      bindings = module,
      roots = Roots.target[DefaultEsRouteProbe],
      activation = Activation.empty,
      locatorPrivacy = LocatorPrivacy.PublicByDefault,
    ).unsafeGet()

    locator.get[DefaultEsRouteProbe]
  }

  private def buildOptInProbe(): OptInRouteProbe = {
    val knownDocument = document(variantId(1), serviceName = "Маникюр")
    val module = new ModuleDef {
      include(BeautySearchRouteModules.apiQdrantVariantSupplementExplicitOptIn(BeautySearchServingGate.disabled))
      make[Async[Task]].fromValue(Async[Task])
      make[BeautySearchBackend[IO]].named("qdrantSupplementLexicalElasticsearch").fromValue(
        new StubBeautySearchBackend(BeautySearchResponse(Nil, Nil, Nil, Nil, Nil))
      )
      make[SemanticCandidateBackend[IO]].fromValue(new StubSemanticCandidateBackend(List(SemanticCandidateHit(knownDocument.variantId, 0.9))))
      make[VariantSearchDocumentLookup[IO]].fromValue(new StubVariantSearchDocumentLookup(List(knownDocument)))
      make[OptInRouteProbe].from {
        (beautySearchApi: BeautySearchApi[IO], allHttpApis: Set[HttpApi[IO]]) =>
          val _ = beautySearchApi
          OptInRouteProbe(allHttpApis)
      }
    }

    val locator = Injector().produce(
      bindings = module,
      roots = Roots.target[OptInRouteProbe],
      activation = Activation.empty,
      locatorPrivacy = LocatorPrivacy.PublicByDefault,
    ).unsafeGet()

    locator.get[OptInRouteProbe]
  }

  private def mockEsClient: ElasticsearchJsonClient = new ElasticsearchJsonClient {
    override def putJson(path: String, json: Json): IO[QueryFailure, Json] = ZIO.succeed(Json.obj())
    override def post(path: String): IO[QueryFailure, Json] = ZIO.succeed(Json.obj())
    override def postJson(path: String, json: Json): IO[QueryFailure, Json] =
      if (path.contains("_search")) ZIO.succeed(Json.obj("hits" -> Json.obj("hits" -> Json.arr())))
      else ZIO.succeed(Json.obj())
    override def postNdjson(path: String, payload: String): IO[QueryFailure, Json] = ZIO.succeed(Json.obj())
    override def getJson(path: String): IO[QueryFailure, Json] = ZIO.dieMessage(s"unexpected getJson($path)")
    override def delete(path: String): IO[QueryFailure, Unit] = ZIO.dieMessage(s"unexpected delete($path)")
  }

  private def observeRoute(apis: Set[HttpApi[IO]], request: Request[Task]): Task[ObservedResponse] = {
    val app: HttpApp[Task] = apis.map(_.http).toList.foldK.orNotFound

    app.run(request).flatMap {
      response =>
        response.body
          .through(text.utf8.decode)
          .compile
          .string
          .map(body => ObservedResponse(response.status, body))
    }
  }

  private final case class DefaultEsRouteProbe(allHttpApis: Set[HttpApi[IO]])
  private final case class OptInRouteProbe(allHttpApis: Set[HttpApi[IO]])

  private def runIO[E, A](effect: ZIO[Any, E, A]): A =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
    }
}
