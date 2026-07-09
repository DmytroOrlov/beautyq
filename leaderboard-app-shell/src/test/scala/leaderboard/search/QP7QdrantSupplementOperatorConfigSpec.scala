package leaderboard.search

import cats.effect.Async
import cats.syntax.all.*
import distage.{Injector, Module, ModuleDef}
import fs2.text
import io.circe.Json
import izumi.distage.model.definition.{Activation, LocatorPrivacy}
import izumi.distage.model.plan.Roots
import leaderboard.api.{BeautySearchApi, HttpApi}
import leaderboard.model.{MasterId, MasterLocationId, MasterServiceOfferId, MasterServiceOfferVariantId, QueryFailure, ServiceId}
import leaderboard.model.Category.CategoryId
import leaderboard.plugins.BeautySearchQdrantSupplementActivation.{EsOnlyRollback, QdrantSupplementNotReady, QdrantSupplementReady}
import leaderboard.plugins.BeautySearchQdrantSupplementActivationConfig
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
 * QP7: operator-facing config/selector contract for `BeautySearchQdrantSupplementActivation`, proved
 * via the pure `BeautySearchQdrantSupplementActivationConfig` parser plus focused module probes.
 *
 * Proves:
 *   - absent/unset operator config selects the safe default (`EsOnlyRollback`);
 *   - explicit operator values select the matching activation state;
 *   - an unrecognized operator value fails closed (never selects `QdrantSupplementReady`);
 *   - the selected activation maps through the existing `BeautySearchQdrantSupplementActivationModuleSelector.moduleFor(...)`;
 *   - default config-selected behavior remains ES-only, with no Qdrant semantic backend / document
 *     lookup binding required;
 *   - not-ready stays a 503 kill-switch; ready stays capped at `ExplicitConstraintsFilterPlusTop1`,
 *     never `AppendAll`.
 *
 * Proof-only: no HOCON/env/CLI parsing, no Qdrant readiness HTTP calls or lifecycle checks, no default
 * route change. Reuses the QP4/QP6 Stub-style / FailIfCalled-style fixture style; no real ES/Qdrant/Llama.
 */
final class QP7QdrantSupplementOperatorConfigSpec extends AnyWordSpec with HttpContractTestSupport {

  private val validRequestBody: String     = """{"query":"zzqx vvbb wwyy qqzz nonsense gibberish impossible token soup","limit":10}"""
  private val malformedRequestBody: String = """{"query":"","limit":10}"""

  // ============================================================================================
  // Pure parser contract.
  // ============================================================================================

  "BeautySearchQdrantSupplementActivationConfig.fromOperatorValue" should {
    "select EsOnlyRollback when operator config is absent" in {
      assert(BeautySearchQdrantSupplementActivationConfig.fromOperatorValue(None) == Right(EsOnlyRollback))
    }

    "select EsOnlyRollback for the explicit ES-only operator value" in {
      assert(
        BeautySearchQdrantSupplementActivationConfig.fromOperatorValue(
          Some(BeautySearchQdrantSupplementActivationConfig.EsOnlyRollbackOperatorValue)
        ) == Right(EsOnlyRollback)
      )
    }

    "select QdrantSupplementNotReady for the explicit not-ready operator value" in {
      assert(
        BeautySearchQdrantSupplementActivationConfig.fromOperatorValue(
          Some(BeautySearchQdrantSupplementActivationConfig.QdrantSupplementNotReadyOperatorValue)
        ) == Right(QdrantSupplementNotReady)
      )
    }

    "select QdrantSupplementReady only for the explicit ready operator value" in {
      assert(
        BeautySearchQdrantSupplementActivationConfig.fromOperatorValue(
          Some(BeautySearchQdrantSupplementActivationConfig.QdrantSupplementReadyOperatorValue)
        ) == Right(QdrantSupplementReady)
      )
    }

    "fail closed on an unrecognized operator value, never selecting QdrantSupplementReady" in {
      val result = BeautySearchQdrantSupplementActivationConfig.fromOperatorValue(Some("totally-unrecognized"))
      result match {
        case Left(failure: QueryFailure) =>
          assert(failure.message.contains("totally-unrecognized"))
        case Right(activation) =>
          fail(s"expected a fail-closed Left for an unrecognized operator value, got Right($activation)")
      }
      assert(result != Right(QdrantSupplementReady))
    }
  }

  // ============================================================================================
  // The config-selected activation maps through the existing moduleFor(...) selector.
  // ============================================================================================

  "BeautySearchQdrantSupplementActivationConfig.moduleForOperatorValue" should {
    "select the default ES-only route module for absent operator config, with no Qdrant semantic backend or document lookup bound at all" in {
      val module = moduleForOperatorValueOrFail(None)
      val apis   = esOnlyApis(module)
      assert(apis.size == 1)
      assert(apis.collect { case api: BeautySearchApi[IO] => api }.size == 1)

      val response = runIO(observeRoute(apis, postJson("/beauty-search", validRequestBody)))
      assert(response.status == Status.Ok, s"config-selected EsOnlyRollback must serve 200 OK, got ${response.status}")
    }

    "leave malformed-request behavior unchanged (400) for the default config-selected route" in {
      val module    = moduleForOperatorValueOrFail(None)
      val apis      = esOnlyApis(module)
      val response  = runIO(observeRoute(apis, postJson("/beauty-search", malformedRequestBody)))
      assert(response.status == Status.BadRequest)
    }

    "reject a valid request with 503 for the explicit not-ready operator value, without invoking the lexical backend, the semantic backend, or the document lookup" in {
      val module   = moduleForOperatorValueOrFail(Some(BeautySearchQdrantSupplementActivationConfig.QdrantSupplementNotReadyOperatorValue))
      val apis     = supplementApis(module, failIfCalledFixture)
      val response = runIO(observeRoute(apis, postJson("/beauty-search", validRequestBody)))
      assert(response.status == Status.ServiceUnavailable, s"config-selected QdrantSupplementNotReady must reject a valid request with 503, got ${response.status}")
    }

    "cap the ready operator value's response at ExplicitConstraintsFilterPlusTop1 (one append), not AppendAll, even with several eligible Qdrant-only candidates" in {
      val module   = moduleForOperatorValueOrFail(Some(BeautySearchQdrantSupplementActivationConfig.QdrantSupplementReadyOperatorValue))
      val apis     = supplementApis(module, threeAppendFixture)
      val response = runIO(observeRoute(apis, postJson("/beauty-search", validRequestBody)))
      assert(response.status == Status.Ok)

      val json       = parseJson(response)
      val variantIds = json.hcursor.downField("variantCarousel").focus.flatMap(_.asArray).getOrElse(fail("missing variantCarousel"))
      assert(
        variantIds.size == 1,
        s"QP7: the config-selected ready supplement must cap appends at one (ExplicitConstraintsFilterPlusTop1), not AppendAll, got ${variantIds.size}",
      )
    }
  }

  private def moduleForOperatorValueOrFail(operatorValue: Option[String]): ModuleDef =
    BeautySearchQdrantSupplementActivationModuleSelector.moduleForOperatorValue(operatorValue) match {
      case Right(module) => module
      case Left(failure)  => fail(s"expected a selected module, got failure: ${failure.message}")
    }

  // ============================================================================================
  // Shared fixtures and probe-building helpers (QP4/QP6 style: Stub*/FailIfCalled*, no spies).
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

  private final case class SupplementFixture(
    lexicalBackend: BeautySearchBackend[IO],
    semanticBackend: SemanticCandidateBackend[IO],
    documentLookup: VariantSearchDocumentLookup[IO],
  )

  private val emptyEsResponse: BeautySearchResponse = BeautySearchResponse(Nil, Nil, Nil, Nil, Nil)

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

  private def esOnlyApis(selectedModule: ModuleDef): Set[HttpApi[IO]] = {
    val module = new ModuleDef {
      include(selectedModule)
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

  private def supplementApis(selectedModule: ModuleDef, fixture: SupplementFixture): Set[HttpApi[IO]] = {
    val module = new ModuleDef {
      include(selectedModule)
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
      ZIO.dieMessage("QP7: lexical backend must not be invoked when the serving gate rejects (enabledNotReady)")
  }

  private final class FailIfCalledSemanticCandidateBackend extends SemanticCandidateBackend[IO] {
    override def candidates(input: UserSearchInput, intent: ParsedSearchIntent): IO[QueryFailure, List[SemanticCandidateHit]] =
      ZIO.dieMessage("QP7: semantic candidate backend must not be invoked when the serving gate rejects (enabledNotReady)")
  }

  private final class FailIfCalledVariantSearchDocumentLookup extends VariantSearchDocumentLookup[IO] {
    override def lookup(variantIds: List[MasterServiceOfferVariantId]): IO[QueryFailure, Map[MasterServiceOfferVariantId, VariantSearchDocument]] =
      ZIO.dieMessage("QP7: document lookup must not be invoked when the serving gate rejects (enabledNotReady)")
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
