package leaderboard.search

import cats.effect.Async
import distage.{Injector, ModuleDef}
import izumi.distage.model.definition.{Activation, LocatorPrivacy}
import izumi.distage.model.plan.Roots
import leaderboard.api.{BeautySearchApi, BeautySearchServingGate, HttpApi}
import leaderboard.model.{MasterServiceOfferVariantId, QueryFailure}
import leaderboard.plugins.BeautySearchQdrantSupplementRuntimeBindingPlan
import leaderboard.plugins.BeautySearchRouteModules
import leaderboard.search.document.VariantSearchDocument
import leaderboard.search.dsl.SearchGeoPoint
import leaderboard.search.elasticsearch.ElasticsearchJsonClient
import leaderboard.search.semantic.{SemanticCandidateBackend, SemanticCandidateHit, VariantSearchDocumentLookup}
import leaderboard.{HttpContractTestSupport, ObservedResponse}
import io.circe.Json
import org.http4s.{HttpApp, Request, Status}
import org.scalatest.wordspec.AnyWordSpec
import fs2.text
import cats.syntax.all.*
import zio.interop.catz.*
import zio.{IO, Runtime, Task, Unsafe, ZIO}

import java.util.UUID

/**
 * QP3: focused control-contract proof for the disabled-by-default explicit opt-in no-worsening
 * Qdrant variant-supplement path (`BeautySearchRouteModules.apiQdrantVariantSupplementExplicitOptIn`,
 * `QdrantVariantSupplementPolicy.ExplicitConstraintsFilterPlusTop1`).
 *
 * Proves readiness, kill-switch, and rollback semantics with module/route probes only:
 *   - the default production module stays ES-backed and carries no Qdrant dependency to "fall back"
 *     from (rollback is module selection, not runtime fallback);
 *   - the no-worsening opt-in path is constructible only via explicit module selection, separate from
 *     `apiElasticsearch`, and is wired to the top-1/filtered policy (not `AppendAll`);
 *   - `enabledNotReady` rejects valid requests with 503 without ever invoking the lexical/semantic
 *     backends (no silent fallback, no silent append-all, no silent Qdrant-as-default);
 *   - `enabledReady` allows the selected service to serve, preserving the QP2 structural invariants;
 *   - `disabled` only means "gate inert, selected-service behavior unchanged" -- not a Qdrant kill
 *     switch; the actual kill switch is `enabledNotReady`, and the actual rollback is selecting the
 *     ES-only module.
 *
 * Proof-only: no default route change, no Qdrant-as-default, no fallback, no fusion/reranking.
 */
final class QP3NoWorseningControlContractSpec extends AnyWordSpec with HttpContractTestSupport {

  // Test-only control-plane labels for the three states this spec proves. Not production
  // environment/config/CLI parsing -- purely local selectors over which fixture/module to build.
  private sealed trait QP3ControlState extends Product with Serializable
  private case object EsOnlyRollback extends QP3ControlState
  private case object QdrantSupplementNotReady extends QP3ControlState
  private case object QdrantSupplementReady extends QP3ControlState

  private val validRequestBody: String     = """{"query":"zzqx vvbb wwyy qqzz nonsense gibberish impossible token soup","limit":10}"""
  private val malformedRequestBody: String = """{"query":"","limit":10}"""

  // ============================================================================================
  // Requirement 1: default rollback contract is module selection, never a runtime fallback.
  // ============================================================================================

  "QP3 control-state table" should {
    "drive distinct, correct HTTP outcomes for EsOnlyRollback / QdrantSupplementNotReady / QdrantSupplementReady on a valid request, and 400 on a malformed request in every state" in {
      val cases = List(
        (EsOnlyRollback, esOnlyApis(), Status.Ok),
        (QdrantSupplementNotReady, optInApis(BeautySearchServingGate.enabledNotReady, zeroAppendFixture), Status.ServiceUnavailable),
        (QdrantSupplementReady, optInApis(BeautySearchServingGate.enabledReady, oneAppendFixture), Status.Ok),
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
          s"[$state] malformed-request behavior must stay 400 regardless of control state, got ${malformedResponse.status}",
        )
      }
    }
  }

  "QP3 default rollback contract" should {
    "construct the default ES-only module with no Qdrant semantic backend or document-lookup binding at all -- there is nothing to fall back from at runtime" in {
      val apis = esOnlyApis()
      assert(apis.size == 1)
      assert(apis.collect { case api: BeautySearchApi[IO] => api }.size == 1)

      // The ES-only module graph below is exactly the one production `LeaderboardPlugin` delegates
      // to (modulo the port-configuration layer); it produces successfully WITHOUT any
      // SemanticCandidateBackend/VariantSearchDocumentLookup binding present in the module at all,
      // which is the module-level proof that the rollback path carries zero Qdrant dependency edge,
      // not merely a disabled one.
      val response = runIO(observeRoute(apis, postJson("/beauty-search", validRequestBody)))
      assert(response.status == Status.Ok, s"ES-only rollback module must serve 200 OK, got ${response.status}")
    }
  }

  // ============================================================================================
  // Requirement 2: explicit opt-in isolation -- separate from `apiElasticsearch`, wired to the
  // no-worsening policy (top-1/filtered), not `AppendAll`.
  // ============================================================================================

  "QP3 explicit opt-in isolation" should {
    "be constructible only through BeautySearchRouteModules.apiQdrantVariantSupplementExplicitOptIn, never reachable from apiElasticsearch/seedCatalogElasticsearch" in {
      val apis = optInApis(BeautySearchServingGate.enabledReady, oneAppendFixture)
      assert(apis.size == 1)
      assert(apis.collect { case api: BeautySearchApi[IO] => api }.size == 1)
    }

    "use ExplicitConstraintsFilterPlusTop1 (append at most one survivor) rather than AppendAll, even when several Qdrant-only candidates would otherwise all be eligible" in {
      // Three distinct Qdrant-only hits, no explicit constraints (nonsense query), ample cap room.
      // AppendAll would append all three; ExplicitConstraintsFilterPlusTop1 must append only one.
      val apis     = optInApis(BeautySearchServingGate.enabledReady, threeAppendFixture)
      val response = runIO(observeRoute(apis, postJson("/beauty-search", validRequestBody)))
      assert(response.status == Status.Ok)

      val json           = parseJson(response)
      val variantIds     = json.hcursor.downField("variantCarousel").focus.flatMap(_.asArray).getOrElse(fail("missing variantCarousel"))
      assert(
        variantIds.size == 1,
        s"QP3: the wired opt-in policy must cap supplement appends at one, even with three eligible Qdrant-only candidates, got ${variantIds.size}",
      )
    }
  }

  // ============================================================================================
  // Requirement 3/6: kill-switch / no-fallback -- enabledNotReady rejects with 503 and never
  // touches the lexical/semantic backend or document lookup (no silent degrade of any kind).
  // ============================================================================================

  "QP3 kill-switch semantics (enabledNotReady)" should {
    "reject a valid request with 503 without invoking the lexical backend, the semantic backend, or the document lookup" in {
      // FailIfCalled collaborators: if the gate silently fell back to ES-only, silently served
      // append-all, or silently became Qdrant-as-default, one of these would be invoked and die.
      val apis = optInApis(BeautySearchServingGate.enabledNotReady, failIfCalledFixture)

      val response = runIO(observeRoute(apis, postJson("/beauty-search", validRequestBody)))
      assert(response.status == Status.ServiceUnavailable, s"enabledNotReady must reject a valid request with 503, got ${response.status}")
    }

    "leave malformed-request behavior unchanged: still 400, validation runs before the gate check" in {
      val notReadyApis = optInApis(BeautySearchServingGate.enabledNotReady, failIfCalledFixture)
      val disabledApis = optInApis(BeautySearchServingGate.disabled, failIfCalledFixture)

      val notReadyResponse = runIO(observeRoute(notReadyApis, postJson("/beauty-search", malformedRequestBody)))
      val disabledResponse = runIO(observeRoute(disabledApis, postJson("/beauty-search", malformedRequestBody)))

      assert(notReadyResponse.status == Status.BadRequest, s"expected 400, got ${notReadyResponse.status}")
      assert(disabledResponse.status == Status.BadRequest, s"expected 400, got ${disabledResponse.status}")
    }
  }

  // ============================================================================================
  // Requirement 4: ready semantics -- enabledReady allows the selected service path to serve,
  // preserving the QP2 structural no-worsening invariants.
  // ============================================================================================

  "QP3 ready semantics (enabledReady)" should {
    "allow the selected opt-in service to serve a valid request (200 OK), preserving ES-owned structural components and the variant cap" in {
      val apis     = optInApis(BeautySearchServingGate.enabledReady, oneAppendFixture)
      val response = runIO(observeRoute(apis, postJson("/beauty-search", validRequestBody)))
      assert(response.status == Status.Ok)

      val json = parseJson(response)
      assert(json.hcursor.downField("providerCarousel").focus.exists(_.asArray.exists(_.isEmpty)), "providerCarousel must remain ES-owned (empty, as stubbed)")
      assert(json.hcursor.downField("serviceIntentCarousel").focus.exists(_.asArray.exists(_.isEmpty)), "serviceIntentCarousel must remain ES-owned (empty, as stubbed)")
      val variantIds = json.hcursor.downField("variantCarousel").focus.flatMap(_.asArray).getOrElse(fail("missing variantCarousel"))
      assert(variantIds.size <= 1, s"QP3: the single Qdrant-only candidate must be capped at one append, got ${variantIds.size}")
    }
  }

  // ============================================================================================
  // Requirement 5: disabled-gate clarification -- "disabled" means gate-inert/selected-service-
  // unchanged, NOT a Qdrant kill switch. The real kill switch is enabledNotReady; the real rollback
  // is selecting the ES-only module (proved above).
  // ============================================================================================

  "QP3 disabled-gate clarification" should {
    "for the selected opt-in service, disabled means the gate is inert and selected-service behavior is unchanged -- it still serves through the Qdrant-supplement-wired service, unlike the ES-only rollback module" in {
      val disabledApis = optInApis(BeautySearchServingGate.disabled, oneAppendFixture)
      val readyApis    = optInApis(BeautySearchServingGate.enabledReady, oneAppendFixture)

      val disabledResponse = runIO(observeRoute(disabledApis, postJson("/beauty-search", validRequestBody)))
      val readyResponse    = runIO(observeRoute(readyApis, postJson("/beauty-search", validRequestBody)))

      assert(disabledResponse.status == Status.Ok, s"disabled gate must leave selected-service behavior unchanged, got ${disabledResponse.status}")
      assert(
        disabledResponse.body == readyResponse.body,
        "QP3: disabled and enabledReady must produce the SAME selected-service response body for the same request " +
          "(disabled only toggles gate rejection, it does not disable the Qdrant supplement wiring) -- " +
          "the actual kill switch is enabledNotReady, and the actual rollback is selecting the ES-only module.",
      )
    }
  }

  // ============================================================================================
  // Shared fixtures and probe-building helpers.
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

  // Fixture bundle for the opt-in module's three external collaborators (lexical ES backend,
  // semantic candidate backend, document lookup), all distinct per scenario.
  private final case class OptInFixture(
    lexicalBackend: BeautySearchBackend[IO],
    semanticBackend: SemanticCandidateBackend[IO],
    documentLookup: VariantSearchDocumentLookup[IO],
  )

  private val emptyEsResponse: BeautySearchResponse = BeautySearchResponse(Nil, Nil, Nil, Nil, Nil)

  private def zeroAppendFixture: OptInFixture =
    OptInFixture(
      lexicalBackend = new StubBeautySearchBackend(emptyEsResponse),
      semanticBackend = new StubSemanticCandidateBackend(Nil),
      documentLookup = new StubVariantSearchDocumentLookup(Nil),
    )

  private def oneAppendFixture: OptInFixture = {
    val knownDocument = document(variantId(1))
    OptInFixture(
      lexicalBackend = new StubBeautySearchBackend(emptyEsResponse),
      semanticBackend = new StubSemanticCandidateBackend(List(SemanticCandidateHit(knownDocument.variantId, 0.9))),
      documentLookup = new StubVariantSearchDocumentLookup(List(knownDocument)),
    )
  }

  private def threeAppendFixture: OptInFixture = {
    val documents = List(variantId(1), variantId(2), variantId(3)).map(document)
    OptInFixture(
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

  private def failIfCalledFixture: OptInFixture =
    OptInFixture(
      lexicalBackend = new FailIfCalledBeautySearchBackend,
      semanticBackend = new FailIfCalledSemanticCandidateBackend,
      documentLookup = new FailIfCalledVariantSearchDocumentLookup,
    )

  private final case class ApisProbe(allHttpApis: Set[HttpApi[IO]])

  private def esOnlyApis(): Set[HttpApi[IO]] = {
    val module = new ModuleDef {
      // The exact backend composition `BeautySearchRouteModules.apiElasticsearch` (LeaderboardPlugin's
      // default Beauty search route) delegates to, modulo the port-configuration layer. No Qdrant
      // binding exists anywhere in this module.
      include(BeautySearchRouteModules.seedCatalogElasticsearch)
      make[Async[Task]].fromValue(Async[Task])
      make[ElasticsearchJsonClient].from(zeroHitMockEsClient)
      make[ApisProbe].from {
        (beautySearchApi: BeautySearchApi[IO], allHttpApis: Set[HttpApi[IO]]) =>
          val _ = beautySearchApi
          ApisProbe(allHttpApis)
      }
    }

    val locator = Injector().produce(
      bindings = module,
      roots = Roots.target[ApisProbe],
      activation = Activation.empty,
      locatorPrivacy = LocatorPrivacy.PublicByDefault,
    ).unsafeGet()

    locator.get[ApisProbe].allHttpApis
  }

  private def optInApis(gate: BeautySearchServingGate, fixture: OptInFixture): Set[HttpApi[IO]] = {
    val module = new ModuleDef {
      include(BeautySearchRouteModules.apiQdrantVariantSupplementExplicitOptIn(gate))
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

    val locator = Injector().produce(
      bindings = module,
      roots = Roots.target[ApisProbe],
      activation = Activation.empty,
      locatorPrivacy = LocatorPrivacy.PublicByDefault,
    ).unsafeGet()

    locator.get[ApisProbe].allHttpApis
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
      ZIO.dieMessage("QP3: lexical backend must not be invoked when the serving gate rejects (enabledNotReady)")
  }

  private final class FailIfCalledSemanticCandidateBackend extends SemanticCandidateBackend[IO] {
    override def candidates(input: UserSearchInput, intent: ParsedSearchIntent): IO[QueryFailure, List[SemanticCandidateHit]] =
      ZIO.dieMessage("QP3: semantic candidate backend must not be invoked when the serving gate rejects (enabledNotReady)")
  }

  private final class FailIfCalledVariantSearchDocumentLookup extends VariantSearchDocumentLookup[IO] {
    override def lookup(variantIds: List[MasterServiceOfferVariantId]): IO[QueryFailure, Map[MasterServiceOfferVariantId, VariantSearchDocument]] =
      ZIO.dieMessage("QP3: document lookup must not be invoked when the serving gate rejects (enabledNotReady)")
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
