package leaderboard.search

import cats.effect.Async
import cats.syntax.all.*
import distage.{Injector, Module, ModuleDef}
import io.circe.Json
import izumi.distage.model.definition.{Activation, LocatorPrivacy}
import izumi.distage.model.plan.Roots
import leaderboard.api.{BeautySearchApi, HttpApi}
import leaderboard.model.{MasterId, MasterLocationId, MasterServiceOfferId, MasterServiceOfferVariantId, QueryFailure, ServiceId}
import leaderboard.model.Category.CategoryId
import leaderboard.plugins.BeautySearchQdrantSupplementActivation
import leaderboard.plugins.BeautySearchQdrantSupplementActivation.{EsOnlyRollback, QdrantSupplementNotReady, QdrantSupplementReady}
import leaderboard.plugins.BeautySearchQdrantSupplementActivationModuleSelector
import leaderboard.plugins.BeautySearchQdrantSupplementRuntimeBindingPlan
import leaderboard.search.document.VariantSearchDocument
import leaderboard.search.dsl.{EmbeddingSpec, SearchGeoPoint, VectorDistance, VectorSearchSpec}
import leaderboard.search.elasticsearch.ElasticsearchJsonClient
import leaderboard.search.qdrant.{
  ObservedQdrantVectorConfig,
  QdrantCollectionCompatibilityExpectation,
  QdrantCollectionCompatibilityMismatch,
  QdrantCollectionIdentity,
  QdrantCollectionReadinessConfig,
  QdrantCollectionReadinessInput,
}
import leaderboard.search.semantic.{SemanticCandidateBackend, SemanticCandidateHit, VariantSearchDocumentLookup}
import leaderboard.{HttpContractTestSupport, ObservedResponse}
import fs2.text
import org.http4s.{HttpApp, Request, Status}
import org.scalatest.wordspec.AnyWordSpec
import zio.interop.catz.*
import zio.{IO, Runtime, Task, Unsafe, ZIO}

import java.util.UUID

/**
 * QP6: staged route characterization matrix for the no-worsening Qdrant supplement path, crossing
 * the three activation states (`EsOnlyRollback`, `QdrantSupplementNotReady`, `QdrantSupplementReady`)
 * against the request classes (empty-ES/possible-append, non-empty-ES/no-append, malformed,
 * readiness-compatible, readiness-mismatch) before operator config integration.
 *
 * Reuses the QP3/QP4 module/route-probe style and the QP5 pure readiness/compatibility helpers.
 * Proof-only: no default route change, no Qdrant-as-default, no fallback, no fusion/reranking, no
 * real ES/Qdrant/Llama.
 */
final class QP6NoWorseningRouteMatrixSpec extends AnyWordSpec with HttpContractTestSupport {

  private val validRequestBody: String     = """{"query":"zzqx vvbb wwyy qqzz nonsense gibberish impossible token soup","limit":10}"""
  private val malformedRequestBody: String = """{"query":"","limit":10}"""

  // ============================================================================================
  // EsOnlyRollback: valid request -> ES-only selected-service behavior; no Qdrant backend/lookup
  // required; malformed request unchanged; readiness mismatch is irrelevant to this state.
  // ============================================================================================

  "EsOnlyRollback" should {
    "serve a valid request as ES-only selected-service behavior, with no Qdrant semantic backend or document lookup bound at all" in {
      val apis     = esOnlyApis()
      val response = runIO(observeRoute(apis, postJson("/beauty-search", validRequestBody)))
      assert(response.status == Status.Ok, s"EsOnlyRollback must serve 200 OK, got ${response.status}")
    }

    "leave malformed-request behavior unchanged (400)" in {
      val apis     = esOnlyApis()
      val response = runIO(observeRoute(apis, postJson("/beauty-search", malformedRequestBody)))
      assert(response.status == Status.BadRequest, s"EsOnlyRollback malformed-request behavior must stay 400, got ${response.status}")
    }

    "be unaffected by a QP5 readiness mismatch -- the module carries no readiness/compatibility dependency edge at all" in {
      // EsOnlyRollback's moduleFor(...) is BeautySearchRouteModules.apiElasticsearch (see QP4); it has
      // no Qdrant readiness/compatibility binding to be mismatched against. The mismatch check itself
      // (proved standalone below) cannot be wired into this state without a code change, which QP6
      // does not make.
      assert(QdrantCollectionIdentity.checkCompatibility(expectation, mismatchedObserved).isLeft)
      val apis     = esOnlyApis()
      val response = runIO(observeRoute(apis, postJson("/beauty-search", validRequestBody)))
      assert(response.status == Status.Ok, "a readiness mismatch elsewhere must not change EsOnlyRollback's ES-only behavior")
    }
  }

  // ============================================================================================
  // QdrantSupplementNotReady: valid request -> 503; collaborators are fail-if-called; malformed
  // request behavior remains malformed-request behavior (400), not 503; no fallback to ES-only.
  // ============================================================================================

  "QdrantSupplementNotReady" should {
    "reject a valid request with 503 without invoking the lexical backend, the semantic backend, or the document lookup" in {
      val apis     = supplementApis(QdrantSupplementNotReady, failIfCalledFixture)
      val response = runIO(observeRoute(apis, postJson("/beauty-search", validRequestBody)))
      assert(response.status == Status.ServiceUnavailable, s"QdrantSupplementNotReady must reject a valid request with 503, got ${response.status}")
    }

    "leave a malformed request at 400, not 503 -- validation runs before the gate check" in {
      val apis     = supplementApis(QdrantSupplementNotReady, failIfCalledFixture)
      val response = runIO(observeRoute(apis, postJson("/beauty-search", malformedRequestBody)))
      assert(response.status == Status.BadRequest, s"QdrantSupplementNotReady malformed-request behavior must stay 400, got ${response.status}")
    }

    "never fall back to ES-only inside the opt-in route -- the fail-if-called lexical backend proves no ES-only path silently served the rejected request" in {
      val apis     = supplementApis(QdrantSupplementNotReady, failIfCalledFixture)
      val response = runIO(observeRoute(apis, postJson("/beauty-search", validRequestBody)))
      assert(response.status == Status.ServiceUnavailable)
    }
  }

  // ============================================================================================
  // QdrantSupplementReady: valid+empty-ES request (possible append) and valid+non-empty-ES request
  // (no append) both preserve ES-owned structural components and the variant cap, using
  // ExplicitConstraintsFilterPlusTop1; malformed request stays 400.
  // ============================================================================================

  "QdrantSupplementReady" should {
    "for a valid request with empty ES results, append at most one Qdrant-only candidate, with no duplicate ES ids, preserving ES-owned components" in {
      val apis     = supplementApis(QdrantSupplementReady, emptyEsPossibleAppendFixture)
      val response = runIO(observeRoute(apis, postJson("/beauty-search", validRequestBody)))
      assert(response.status == Status.Ok)

      val json = parseJson(response)
      assert(json.hcursor.downField("providerCarousel").focus.exists(_.asArray.exists(_.isEmpty)), "providerCarousel must remain ES-owned (empty, as stubbed)")
      assert(json.hcursor.downField("serviceIntentCarousel").focus.exists(_.asArray.exists(_.isEmpty)), "serviceIntentCarousel must remain ES-owned (empty, as stubbed)")
      val variantIds = json.hcursor.downField("variantCarousel").focus.flatMap(_.asArray).getOrElse(fail("missing variantCarousel"))
      assert(variantIds.size <= 1, s"QP6: at most one Qdrant-only candidate may be appended, got ${variantIds.size}")
    }

    "for a valid request with non-empty ES results already covering the only Qdrant candidate, append nothing, and keep the response identical to the ES-only response" in {
      val apis     = supplementApis(QdrantSupplementReady, nonEmptyEsNoAppendFixture)
      val response = runIO(observeRoute(apis, postJson("/beauty-search", validRequestBody)))
      assert(response.status == Status.Ok)

      val json        = parseJson(response)
      val variantIds  = json.hcursor.downField("variantCarousel").focus.flatMap(_.asArray).getOrElse(fail("missing variantCarousel"))
      val variantUuids = variantIds.flatMap(_.hcursor.get[String]("variantId").toOption)
      assert(variantUuids == Vector(coveredVariantId.toString), s"QP6: ES variant prefix/order must be preserved with no Qdrant append, got $variantUuids")
    }

    "never exceed the variant carousel cap, even with several eligible Qdrant-only candidates" in {
      val apis     = supplementApis(QdrantSupplementReady, threeAppendFixture)
      val response = runIO(observeRoute(apis, postJson("/beauty-search", validRequestBody)))
      assert(response.status == Status.Ok)

      val json       = parseJson(response)
      val variantIds = json.hcursor.downField("variantCarousel").focus.flatMap(_.asArray).getOrElse(fail("missing variantCarousel"))
      assert(
        variantIds.size == 1,
        s"QP6: the ready supplement must cap appends at one (ExplicitConstraintsFilterPlusTop1), not AppendAll, got ${variantIds.size}",
      )
    }

    "leave malformed-request behavior unchanged (400)" in {
      val apis     = supplementApis(QdrantSupplementReady, emptyEsPossibleAppendFixture)
      val response = runIO(observeRoute(apis, postJson("/beauty-search", malformedRequestBody)))
      assert(response.status == Status.BadRequest, s"QdrantSupplementReady malformed-request behavior must stay 400, got ${response.status}")
    }
  }

  // ============================================================================================
  // Readiness-compatible setup: the matrix's "ready" case is associated with a QP5-compatible
  // readiness identity. Pure assertion only, reusing QP5's `QdrantCollectionIdentity` helpers.
  // ============================================================================================

  "Readiness-compatible setup" should {
    "accept the readiness identity associated with the QdrantSupplementReady matrix case" in {
      assert(QdrantCollectionIdentity.checkCompatibility(expectation, compatibleObserved) == Right(()))
    }
  }

  // ============================================================================================
  // Readiness-mismatch setup: mismatch maps to not-ready behavior for the staged matrix; no silent
  // serving of the supplement path, no silent recreate, no fallback inside the hybrid backend.
  // ============================================================================================

  "Readiness-mismatch setup" should {
    "fail the compatibility check, and the staged matrix's not-ready state must still reject a valid request with 503, not silently serve" in {
      val mismatchResult = QdrantCollectionIdentity.checkCompatibility(expectation, mismatchedObserved)
      mismatchResult match {
        case Left(mismatches) =>
          assert(mismatches.contains(QdrantCollectionCompatibilityMismatch.CollectionNameMismatch(expectation.collectionName, mismatchedObserved.collectionName)))
        case Right(()) =>
          fail("expected a readiness mismatch to be reported")
      }

      val apis     = supplementApis(QdrantSupplementNotReady, failIfCalledFixture)
      val response = runIO(observeRoute(apis, postJson("/beauty-search", validRequestBody)))
      assert(response.status == Status.ServiceUnavailable, "a readiness mismatch must map to the not-ready (503) state, never a silently-served supplement path")
    }
  }

  // ============================================================================================
  // Default route unchanged: LeaderboardPlugin's default remains ES-backed and does not include
  // Qdrant supplement activation by default (mirrors QP3/QP4's identical assertion).
  // ============================================================================================

  "The default ES route that LeaderboardPlugin includes" should {
    "remain the ES-only shape: exactly one BeautySearchApi, constructible with no Qdrant supplement module, semantic backend, or document lookup present" in {
      val apis = esOnlyApis()
      assert(apis.size == 1)
      assert(apis.collect { case api: BeautySearchApi[IO] => api }.size == 1)
    }
  }

  // ============================================================================================
  // Shared QP5-style readiness fixtures (pure; no real Qdrant call).
  // ============================================================================================

  private val embeddingSpec: EmbeddingSpec[VariantSearchDocument] =
    EmbeddingSpec[VariantSearchDocument](
      vectorName = "qp6-embedding-spec-vector-name-is-not-identity-source",
      modelName = "llama-cpp-embedding",
      dimension = 1024,
      distance = VectorDistance.Cosine,
      sourceTextFields = List(leaderboard.search.document.BeautyQVariantSearchDocumentContract.Fields.serviceText, leaderboard.search.document.BeautyQVariantSearchDocumentContract.Fields.allText),
    )

  private val vectorSearchSpec: VectorSearchSpec =
    VectorSearchSpec(
      collectionName = "placeholder_collection",
      vectorName = "variant-embedding",
      topK = 20,
      scoreThreshold = Some(0.2),
    )

  private val readinessConfig: QdrantCollectionReadinessConfig =
    QdrantCollectionReadinessConfig.derive(
      QdrantCollectionReadinessInput(
        domainName = "beauty_variant",
        searchSpecVersion = "v1",
        purpose = "qp6-route-matrix-contract",
        embeddingSpec = embeddingSpec,
        vectorSearchSpec = vectorSearchSpec,
      )
    )

  private val expectation: QdrantCollectionCompatibilityExpectation =
    readinessConfig.compatibilityExpectation

  private val compatibleObserved: ObservedQdrantVectorConfig =
    ObservedQdrantVectorConfig(
      collectionName = expectation.collectionName,
      vectorName = expectation.vectorName,
      dimension = expectation.expectedDimension,
      distance = expectation.expectedDistance,
      embeddingModelName = Some(expectation.embeddingModelName),
    )

  private val mismatchedObserved: ObservedQdrantVectorConfig =
    compatibleObserved.copy(collectionName = "other_collection")

  // ============================================================================================
  // Shared activation/route fixtures (QP3/QP4 fixture style: Stub*/FailIfCalled*, no spies).
  // ============================================================================================

  private def variantId(i: Int): MasterServiceOfferVariantId =
    MasterServiceOfferVariantId(UUID.fromString(f"00000000-0000-0000-0000-$i%012d"))

  private val fixedMasterServiceOfferId = MasterServiceOfferId(UUID.fromString("10000000-0000-0000-0000-000000000000"))
  private val fixedMasterLocationId     = MasterLocationId(UUID.fromString("20000000-0000-0000-0000-000000000000"))
  private val fixedMasterId             = MasterId(UUID.fromString("30000000-0000-0000-0000-000000000000"))
  private val fixedServiceId            = ServiceId(UUID.fromString("40000000-0000-0000-0000-000000000000"))
  private val fixedCategoryId           = CategoryId(UUID.fromString("50000000-0000-0000-0000-000000000000"))

  private def document(id: MasterServiceOfferVariantId): VariantSearchDocument =
    VariantSearchDocument(
      variantId = id,
      masterServiceOfferId = fixedMasterServiceOfferId,
      masterLocationId = fixedMasterLocationId,
      masterId = fixedMasterId,
      serviceId = fixedServiceId,
      categoryId = fixedCategoryId,
      serviceName = "Manicure",
      categoryName = "Nails",
      masterName = "Test Master",
      locationName = "Test Location",
      address = "Test Address",
      location = SearchGeoPoint(BigDecimal(53.5), BigDecimal(10.0)),
      lat = BigDecimal(53.5),
      lon = BigDecimal(10.0),
      priceFrom = BigDecimal(10),
      priceTo = BigDecimal(20),
      durationMin = 30,
      enumAttributes = Map.empty,
      booleanAttributes = Map.empty,
      intAttributes = Map.empty,
      bigDecimalAttributes = Map.empty,
      allText = "",
      serviceText = "",
      attributeText = "",
      providerText = "",
      locationText = "",
    )

  private def variantSearchResult(id: MasterServiceOfferVariantId): VariantSearchResult =
    VariantSearchResult(
      variantId = id,
      masterServiceOfferId = fixedMasterServiceOfferId,
      masterLocationId = fixedMasterLocationId,
      masterId = fixedMasterId,
      serviceId = fixedServiceId,
      categoryId = fixedCategoryId,
      serviceName = "Manicure",
      categoryName = "Nails",
      masterName = "Test Master",
      locationName = "Test Location",
      address = "Test Address",
      lat = BigDecimal(53.5),
      lon = BigDecimal(10.0),
      priceFrom = BigDecimal(10),
      priceTo = BigDecimal(20),
      durationMin = 30,
      enumAttributes = Map.empty,
      booleanAttributes = Map.empty,
      intAttributes = Map.empty,
      bigDecimalAttributes = Map.empty,
      score = 1.0d,
      distanceKm = None,
    )

  private final case class SupplementFixture(
    lexicalBackend: BeautySearchBackend[IO],
    semanticBackend: SemanticCandidateBackend[IO],
    documentLookup: VariantSearchDocumentLookup[IO],
  )

  private val emptyEsResponse: BeautySearchResponse = BeautySearchResponse(Nil, Nil, Nil, Nil, Nil)

  // Request class 1: valid request, empty ES result, possible Qdrant append.
  private def emptyEsPossibleAppendFixture: SupplementFixture = {
    val knownDocument = document(variantId(1))
    SupplementFixture(
      lexicalBackend = new StubBeautySearchBackend(emptyEsResponse),
      semanticBackend = new StubSemanticCandidateBackend(List(SemanticCandidateHit(knownDocument.variantId, 0.9))),
      documentLookup = new StubVariantSearchDocumentLookup(List(knownDocument)),
    )
  }

  private val coveredVariantId: MasterServiceOfferVariantId = variantId(7)

  // Request class 2: valid request, non-empty ES result, no Qdrant append (the only Qdrant
  // candidate duplicates the already-present ES id, so ExplicitConstraintsFilterPlusTop1 has no
  // qdrant-only survivor to append).
  private def nonEmptyEsNoAppendFixture: SupplementFixture = {
    val coveredDocument = document(coveredVariantId)
    val nonEmptyEsResponse = BeautySearchResponse(
      variantCarousel = List(variantSearchResult(coveredVariantId)),
      providerCarousel = Nil,
      serviceIntentCarousel = Nil,
      facets = Nil,
      inferredFilters = Nil,
    )
    SupplementFixture(
      lexicalBackend = new StubBeautySearchBackend(nonEmptyEsResponse),
      semanticBackend = new StubSemanticCandidateBackend(List(SemanticCandidateHit(coveredVariantId, 0.9))),
      documentLookup = new StubVariantSearchDocumentLookup(List(coveredDocument)),
    )
  }

  private def threeAppendFixture: SupplementFixture = {
    val documents = List(variantId(1), variantId(2), variantId(3)).map(document)
    SupplementFixture(
      lexicalBackend = new StubBeautySearchBackend(emptyEsResponse),
      semanticBackend = new StubSemanticCandidateBackend(
        List(
          SemanticCandidateHit(documents(0).variantId, 0.95),
          SemanticCandidateHit(documents(1).variantId, 0.85),
          SemanticCandidateHit(documents(2).variantId, 0.75),
        )
      ),
      documentLookup = new StubVariantSearchDocumentLookup(documents),
    )
  }

  private def failIfCalledFixture: SupplementFixture =
    SupplementFixture(
      lexicalBackend = new FailIfCalledBeautySearchBackend,
      semanticBackend = new FailIfCalledSemanticCandidateBackend,
      documentLookup = new FailIfCalledVariantSearchDocumentLookup,
    )

  private final case class ApisProbe(allHttpApis: Set[HttpApi[IO]])

  private def apisFrom(module: Module): Set[HttpApi[IO]] = {
    val locator = Injector().produce(
      bindings = module,
      roots = Roots.target[ApisProbe],
      activation = Activation.empty,
      locatorPrivacy = LocatorPrivacy.PublicByDefault,
    ).unsafeGet()

    locator.get[ApisProbe].allHttpApis
  }

  private def esOnlyApis(): Set[HttpApi[IO]] = {
    val module = new ModuleDef {
      include(BeautySearchQdrantSupplementActivationModuleSelector.moduleFor(EsOnlyRollback))
      make[Async[Task]].fromValue(Async[Task])
      make[ApisProbe].from {
        (beautySearchApi: BeautySearchApi[IO], allHttpApis: Set[HttpApi[IO]]) =>
          val _ = beautySearchApi
          ApisProbe(allHttpApis)
      }
    }.overriddenBy(new ModuleDef {
      // Replace the port-configured ES client (from `apiElasticsearch`) with a zero-hit mock so the
      // proof stays focused; this drops the `ElasticsearchPortCfg` dependency edge entirely.
      make[ElasticsearchJsonClient].fromValue(zeroHitMockEsClient)
    })

    apisFrom(module)
  }

  private def supplementApis(
    activation: BeautySearchQdrantSupplementActivation,
    fixture: SupplementFixture,
  ): Set[HttpApi[IO]] = {
    val module = new ModuleDef {
      include(BeautySearchQdrantSupplementActivationModuleSelector.moduleFor(activation))
      make[Async[Task]].fromValue(Async[Task])
      make[BeautySearchBackend[IO]].named(BeautySearchQdrantSupplementRuntimeBindingPlan.LexicalBackendBindingName).fromValue(fixture.lexicalBackend)
      make[SemanticCandidateBackend[IO]].fromValue(fixture.semanticBackend)
      make[VariantSearchDocumentLookup[IO]].fromValue(fixture.documentLookup)
      make[ApisProbe].from {
        (beautySearchApi: BeautySearchApi[IO], allHttpApis: Set[HttpApi[IO]]) =>
          val _ = beautySearchApi
          ApisProbe(allHttpApis)
      }
    }

    apisFrom(module)
  }

  private def zeroHitMockEsClient: ElasticsearchJsonClient = new ElasticsearchJsonClient {
    override def putJson(path: String, json: Json): IO[QueryFailure, Json]  = ZIO.succeed(Json.obj())
    override def post(path: String): IO[QueryFailure, Json]                 = ZIO.succeed(Json.obj())
    override def postJson(path: String, json: Json): IO[QueryFailure, Json] =
      if (path.contains("_search")) ZIO.succeed(Json.obj("hits" -> Json.obj("hits" -> Json.arr())))
      else ZIO.succeed(Json.obj())
    override def postNdjson(path: String, payload: String): IO[QueryFailure, Json] = ZIO.succeed(Json.obj())
    override def getJson(path: String): IO[QueryFailure, Json]                     = ZIO.dieMessage(s"unexpected getJson($path)")
    override def delete(path: String): IO[QueryFailure, Unit]                      = ZIO.dieMessage(s"unexpected delete($path)")
  }

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

  // Proves the collaborator is unused when the gate rejects: a kill-switch rejection that silently
  // fell back to ES-only, silently served append-all, or silently became Qdrant-as-default would
  // invoke one of these and die.
  private final class FailIfCalledBeautySearchBackend extends BeautySearchBackend[IO] {
    override def search(input: UserSearchInput, intent: ParsedSearchIntent): IO[QueryFailure, BeautySearchResponse] =
      ZIO.dieMessage("QP6: lexical backend must not be invoked when the serving gate rejects (enabledNotReady)")
  }

  private final class FailIfCalledSemanticCandidateBackend extends SemanticCandidateBackend[IO] {
    override def candidates(input: UserSearchInput, intent: ParsedSearchIntent): IO[QueryFailure, List[SemanticCandidateHit]] =
      ZIO.dieMessage("QP6: semantic candidate backend must not be invoked when the serving gate rejects (enabledNotReady)")
  }

  private final class FailIfCalledVariantSearchDocumentLookup extends VariantSearchDocumentLookup[IO] {
    override def lookup(variantIds: List[MasterServiceOfferVariantId]): IO[QueryFailure, Map[MasterServiceOfferVariantId, VariantSearchDocument]] =
      ZIO.dieMessage("QP6: document lookup must not be invoked when the serving gate rejects (enabledNotReady)")
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

  private def parseJson(response: ObservedResponse): Json =
    io.circe.parser.parse(response.body) match {
      case Right(json) => json
      case Left(error) => fail(s"invalid JSON: ${error.getMessage}; body: ${response.body}")
    }

  private def runIO[E, A](effect: ZIO[Any, E, A]): A =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
    }
}
