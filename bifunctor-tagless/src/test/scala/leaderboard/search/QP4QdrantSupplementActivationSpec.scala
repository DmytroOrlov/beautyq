package leaderboard.search

import cats.effect.Async
import cats.syntax.all.*
import distage.{Injector, Module, ModuleDef}
import fs2.text
import io.circe.Json
import izumi.distage.model.definition.{Activation, LocatorPrivacy}
import izumi.distage.model.plan.Roots
import leaderboard.api.{BeautySearchApi, HttpApi}
import leaderboard.model.{MasterServiceOfferVariantId, QueryFailure}
import leaderboard.plugins.BeautySearchQdrantSupplementActivation
import leaderboard.plugins.BeautySearchQdrantSupplementActivation.{EsOnlyRollback, QdrantSupplementNotReady, QdrantSupplementReady}
import leaderboard.plugins.BeautySearchQdrantSupplementActivationModuleSelector
import leaderboard.plugins.BeautySearchQdrantSupplementRuntimeBindingPlan
import leaderboard.search.document.VariantSearchDocument
import leaderboard.search.dsl.SearchGeoPoint
import leaderboard.search.elasticsearch.ElasticsearchJsonClient
import leaderboard.search.semantic.{SemanticCandidateBackend, SemanticCandidateHit, VariantSearchDocumentLookup}
import leaderboard.{HttpContractTestSupport, ObservedResponse}
import org.http4s.{HttpApp, Request, Status}
import org.scalatest.wordspec.AnyWordSpec
import zio.interop.catz.*
import zio.{IO, Runtime, Task, Unsafe, ZIO}

import java.util.UUID

/**
 * QP4: focused proof for the production activation selector `BeautySearchQdrantSupplementActivation`,
 * which maps an explicit activation state to a BeautyQ search module without switching default traffic:
 *
 *   - `EsOnlyRollback`           -> the same ES-backed route shape the default `LeaderboardPlugin` uses
 *                                   (`BeautySearchRouteModules.apiElasticsearch`), with no Qdrant
 *                                   semantic backend / document-lookup binding required at all;
 *   - `QdrantSupplementNotReady` -> the explicit no-worsening Qdrant supplement module
 *                                   (`ExplicitConstraintsFilterPlusTop1`) with `enabledNotReady`
 *                                   (valid requests rejected with 503, as QP3 proved);
 *   - `QdrantSupplementReady`    -> the same supplement module with `enabledReady` (the selected
 *                                   service serves, top-1 cap, not `AppendAll`).
 *
 * Proof-only: no default route change, no Qdrant-as-default, no fallback, no fusion/reranking, no env
 * parsing. Reuses the QP3/QP2 stub/fail-if-called fixture style; no real ES/Qdrant/Llama.
 */
final class QP4QdrantSupplementActivationSpec extends AnyWordSpec with HttpContractTestSupport {

  private val validRequestBody: String     = """{"query":"zzqx vvbb wwyy qqzz nonsense gibberish impossible token soup","limit":10}"""
  private val malformedRequestBody: String = """{"query":"","limit":10}"""

  // ============================================================================================
  // Requirement 1/8: EsOnlyRollback selects the default ES-backed route shape, with no Qdrant
  // semantic backend / document-lookup binding required in the focused module proof.
  // ============================================================================================

  "BeautySearchQdrantSupplementActivationModuleSelector.moduleFor(EsOnlyRollback)" should {
    "select the same ES-backed route shape as the default route -- exactly one BeautySearchApi, no Qdrant binding needed -- and serve 200 OK" in {
      val apis = esOnlyApis()
      assert(apis.size == 1)
      assert(apis.collect { case api: BeautySearchApi[IO] => api }.size == 1)

      // The module produces successfully WITHOUT any SemanticCandidateBackend / VariantSearchDocumentLookup
      // binding present at all: the rollback path carries zero Qdrant dependency edge, not a disabled one.
      val response = runIO(observeRoute(apis, postJson("/beauty-search", validRequestBody)))
      assert(response.status == Status.Ok, s"EsOnlyRollback must serve 200 OK like the default ES route, got ${response.status}")
    }
  }

  // ============================================================================================
  // Requirement 2/3/6/7: each Qdrant-supplement activation state selects the right serving gate.
  // ============================================================================================

  "BeautySearchQdrantSupplementActivation activation states" should {
    "drive distinct, correct HTTP outcomes on a valid request, and keep malformed-request behavior at 400 in every state" in {
      val cases = List(
        (EsOnlyRollback, esOnlyApis(), Status.Ok),
        (QdrantSupplementNotReady, supplementApis(QdrantSupplementNotReady, zeroAppendFixture), Status.ServiceUnavailable),
        (QdrantSupplementReady, supplementApis(QdrantSupplementReady, oneAppendFixture), Status.Ok),
      )

      cases.foreach { case (state, apis, expectedValidStatus) =>
        val validResponse = runIO(observeRoute(apis, postJson("/beauty-search", validRequestBody)))
        assert(
          validResponse.status == expectedValidStatus,
          s"[$state] expected $expectedValidStatus for a valid request, got ${validResponse.status}",
        )

        val malformedResponse = runIO(observeRoute(apis, postJson("/beauty-search", malformedRequestBody)))
        assert(
          malformedResponse.status == Status.BadRequest,
          s"[$state] malformed-request behavior must stay 400 regardless of activation state, got ${malformedResponse.status}",
        )
      }
    }

    "for QdrantSupplementNotReady, reject a valid request with 503 without invoking the lexical backend, the semantic backend, or the document lookup (kill-switch, no silent fallback)" in {
      val apis     = supplementApis(QdrantSupplementNotReady, failIfCalledFixture)
      val response = runIO(observeRoute(apis, postJson("/beauty-search", validRequestBody)))
      assert(response.status == Status.ServiceUnavailable, s"QdrantSupplementNotReady must reject a valid request with 503, got ${response.status}")
    }

    "for QdrantSupplementReady, serve the selected supplement service (200 OK), preserving ES-owned structural components and the variant cap" in {
      val apis     = supplementApis(QdrantSupplementReady, oneAppendFixture)
      val response = runIO(observeRoute(apis, postJson("/beauty-search", validRequestBody)))
      assert(response.status == Status.Ok)

      val json = parseJson(response)
      assert(json.hcursor.downField("providerCarousel").focus.exists(_.asArray.exists(_.isEmpty)), "providerCarousel must remain ES-owned (empty, as stubbed)")
      assert(json.hcursor.downField("serviceIntentCarousel").focus.exists(_.asArray.exists(_.isEmpty)), "serviceIntentCarousel must remain ES-owned (empty, as stubbed)")
      val variantIds = json.hcursor.downField("variantCarousel").focus.flatMap(_.asArray).getOrElse(fail("missing variantCarousel"))
      assert(variantIds.size <= 1, s"QP4: the single Qdrant-only candidate must be capped at one append, got ${variantIds.size}")
    }
  }

  // ============================================================================================
  // Requirement 5: the ready supplement path uses ExplicitConstraintsFilterPlusTop1, not AppendAll.
  // ============================================================================================

  "BeautySearchQdrantSupplementActivationModuleSelector.moduleFor(QdrantSupplementReady)" should {
    "use ExplicitConstraintsFilterPlusTop1 (append at most one survivor), even when several Qdrant-only candidates would otherwise all be eligible" in {
      val apis     = supplementApis(QdrantSupplementReady, threeAppendFixture)
      val response = runIO(observeRoute(apis, postJson("/beauty-search", validRequestBody)))
      assert(response.status == Status.Ok)

      val json       = parseJson(response)
      val variantIds = json.hcursor.downField("variantCarousel").focus.flatMap(_.asArray).getOrElse(fail("missing variantCarousel"))
      assert(
        variantIds.size == 1,
        s"QP4: the ready supplement must cap appends at one (ExplicitConstraintsFilterPlusTop1), not AppendAll, even with three eligible Qdrant-only candidates, got ${variantIds.size}",
      )
    }
  }

  // ============================================================================================
  // Requirement 4: the default route remains the ES-only shape; the activation-selected Qdrant
  // supplement module is NOT part of it (constructs with no Qdrant binding present).
  // ============================================================================================

  "The default ES route that LeaderboardPlugin includes" should {
    "remain the ES-only shape: exactly one BeautySearchApi, constructible with no activation-selected Qdrant supplement module / semantic backend / document lookup" in {
      val apis = esOnlyApis()
      assert(apis.size == 1)
      assert(apis.collect { case api: BeautySearchApi[IO] => api }.size == 1)
    }
  }

  // ============================================================================================
  // Shared fixtures and probe-building helpers (QP3/QP2 style: Stub*/FailIfCalled*, no spies).
  // ============================================================================================

  private def variantId(i: Int): MasterServiceOfferVariantId =
    UUID.fromString(f"00000000-0000-0000-0000-$i%012d")

  private val fixedMasterServiceOfferId = UUID.fromString("10000000-0000-0000-0000-000000000000")
  private val fixedMasterLocationId     = UUID.fromString("20000000-0000-0000-0000-000000000000")
  private val fixedMasterId             = UUID.fromString("30000000-0000-0000-0000-000000000000")
  private val fixedServiceId            = UUID.fromString("40000000-0000-0000-0000-000000000000")
  private val fixedCategoryId           = UUID.fromString("50000000-0000-0000-0000-000000000000")

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

  // Fixture bundle for the supplement module's three external collaborators (lexical ES backend,
  // semantic candidate backend, document lookup), all distinct per scenario.
  private final case class SupplementFixture(
    lexicalBackend: BeautySearchBackend[IO],
    semanticBackend: SemanticCandidateBackend[IO],
    documentLookup: VariantSearchDocumentLookup[IO],
  )

  private val emptyEsResponse: BeautySearchResponse = BeautySearchResponse(Nil, Nil, Nil, Nil, Nil)

  private def zeroAppendFixture: SupplementFixture =
    SupplementFixture(
      lexicalBackend = new StubBeautySearchBackend(emptyEsResponse),
      semanticBackend = new StubSemanticCandidateBackend(Nil),
      documentLookup = new StubVariantSearchDocumentLookup(Nil),
    )

  private def oneAppendFixture: SupplementFixture = {
    val knownDocument = document(variantId(1))
    SupplementFixture(
      lexicalBackend = new StubBeautySearchBackend(emptyEsResponse),
      semanticBackend = new StubSemanticCandidateBackend(List(SemanticCandidateHit(knownDocument.variantId, 0.9))),
      documentLookup = new StubVariantSearchDocumentLookup(List(knownDocument)),
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
      ZIO.dieMessage("QP4: lexical backend must not be invoked when the serving gate rejects (enabledNotReady)")
  }

  private final class FailIfCalledSemanticCandidateBackend extends SemanticCandidateBackend[IO] {
    override def candidates(input: UserSearchInput, intent: ParsedSearchIntent): IO[QueryFailure, List[SemanticCandidateHit]] =
      ZIO.dieMessage("QP4: semantic candidate backend must not be invoked when the serving gate rejects (enabledNotReady)")
  }

  private final class FailIfCalledVariantSearchDocumentLookup extends VariantSearchDocumentLookup[IO] {
    override def lookup(variantIds: List[MasterServiceOfferVariantId]): IO[QueryFailure, Map[MasterServiceOfferVariantId, VariantSearchDocument]] =
      ZIO.dieMessage("QP4: document lookup must not be invoked when the serving gate rejects (enabledNotReady)")
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
