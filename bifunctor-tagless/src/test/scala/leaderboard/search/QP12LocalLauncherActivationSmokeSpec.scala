package leaderboard.search

import cats.effect.Async
import distage.{Injector, Module, ModuleDef}
import io.circe.Json
import izumi.distage.model.definition.{Activation, LocatorPrivacy}
import izumi.distage.model.plan.Roots
import leaderboard.api.{BeautySearchApi, HttpApi}
import leaderboard.model.{MasterServiceOfferVariantId, QueryFailure}
import leaderboard.plugins.{BeautySearchQdrantSupplementActivationConfig, BeautySearchQdrantSupplementActivationLauncherSeam}
import leaderboard.search.document.VariantSearchDocument
import leaderboard.search.dsl.SearchGeoPoint
import leaderboard.search.elasticsearch.ElasticsearchJsonClient
import leaderboard.search.semantic.{SemanticCandidateBackend, SemanticCandidateHit, VariantSearchDocumentLookup}
import leaderboard.{HttpContractTestSupport, ObservedResponse}
import org.http4s.Status
import org.scalatest.wordspec.AnyWordSpec
import zio.interop.catz.*
import zio.{IO, Runtime, Task, Unsafe, ZIO}

import java.util.UUID

/**
 * QP12: local launcher enablement smoke for the no-worsening Qdrant supplement activation seam.
 *
 * Unlike QP10 (which exercises the seam's pure value->module selector via an in-process http4s
 * `HttpApp.run`), QP12 drives the *launcher entry points* and serves `/beauty-search` over a REAL,
 * locally bound HTTP server (`HttpContractTestSupport.observe`: an Ember server on `127.0.0.1:0`, the
 * repo's ephemeral local-port convention, plus a real Ember client). This is the narrowest in-process
 * launcher-level smoke; the repo has no process-spawn / fixed-port `./launcher` test convention, so a
 * full `./launcher :leaderboard` subprocess (which would need Docker Postgres + ES and be
 * `QP12_RESOURCE_GATED`) is intentionally not used.
 *
 * Proves:
 *   - absent env (`BEAUTYQ_QDRANT_SUPPLEMENT_ACTIVATION` unset in this process) -> the real launcher
 *     entry point `selectedModuleFromEnvOrThrow()` selects the ES-backed default; a valid request
 *     serves 200 over the real server, malformed stays 400, and no Qdrant semantic backend / document
 *     lookup binding is required;
 *   - explicit `es-only-rollback` is equivalent to absent/default;
 *   - explicit `qdrant-supplement-not-ready` -> valid request returns 503 over the real server, with
 *     fail-if-called backends proving no silent fallback to ES-only or AppendAll;
 *   - explicit `qdrant-supplement-ready`:
 *       * through the launcher seam ALONE (the way `LeaderboardPlugin` includes it, with no other
 *         module supplying the supplement runtime bindings) it fails closed at graph composition,
 *         naming the exact missing Distage keys -> `READY_LAUNCHER_BINDINGS_BLOCKED`;
 *       * once the caller additionally supplies those documented bindings, the route serves capped at
 *         `ExplicitConstraintsFilterPlusTop1` (one append, never AppendAll), preserving the ES prefix
 *         and never duplicating an ES id;
 *   - an invalid env value fails closed at module composition, never selecting ready and never
 *     falling back to the Qdrant supplement.
 *
 * Proof-only: no default route change, no Qdrant-as-default, no fallback, no fusion/reranking, no
 * route JSON / API change, no real ES/Qdrant/Llama.
 */
final class QP12LocalLauncherActivationSmokeSpec extends AnyWordSpec with HttpContractTestSupport {

  private val validRequestBody: String     = """{"query":"zzqx vvbb wwyy qqzz nonsense gibberish impossible token soup","limit":10}"""
  private val malformedRequestBody: String = """{"query":"","limit":10}"""

  // The QP12 smoke for the absent-env launcher default is only meaningful if the env var really is
  // unset in this JVM; if an operator exported it, cancel rather than assert a false default.
  private val envOperatorValue: Option[String] =
    sys.env.get(BeautySearchQdrantSupplementActivationLauncherSeam.OperatorEnvVarName)

  // ============================================================================================
  // Case 1: absent env -> real launcher entry point serves the ES-backed default over HTTP.
  // ============================================================================================

  "The launcher entry point BeautySearchQdrantSupplementActivationLauncherSeam.selectedModuleFromEnvOrThrow (absent env)" should {
    "serve a valid /beauty-search request as the ES-backed default (200) over a real local server, with no Qdrant binding required" in {
      envOperatorValue match {
        case Some(value) =>
          cancel(s"QP12: ${BeautySearchQdrantSupplementActivationLauncherSeam.OperatorEnvVarName} is set to '$value' in this process; absent-env default smoke is not applicable")
        case None =>
          val apis     = esOnlyApis(BeautySearchQdrantSupplementActivationLauncherSeam.selectedModuleFromEnvOrThrow())
          assert(apis.size == 1)
          assert(apis.collect { case api: BeautySearchApi[IO] => api }.size == 1)

          val response = serve(apis, validRequestBody)
          assert(response.status == Status.Ok, s"absent env must serve the ES-backed default (200), got ${response.status}")
      }
    }

    "leave malformed-request behavior unchanged (400) for the absent-env launcher default" in {
      envOperatorValue match {
        case Some(value) =>
          cancel(s"QP12: ${BeautySearchQdrantSupplementActivationLauncherSeam.OperatorEnvVarName} is set to '$value' in this process; absent-env default smoke is not applicable")
        case None =>
          val apis     = esOnlyApis(BeautySearchQdrantSupplementActivationLauncherSeam.selectedModuleFromEnvOrThrow())
          val response = serve(apis, malformedRequestBody)
          assert(response.status == Status.BadRequest)
      }
    }
  }

  // ============================================================================================
  // Case 2: explicit es-only-rollback -> equivalent to absent/default.
  // ============================================================================================

  "The explicit es-only-rollback launcher value" should {
    "serve a valid /beauty-search request as the ES-backed default (200), equivalent to the absent default" in {
      val apis     = esOnlyApis(esOnlyRollbackModule)
      assert(apis.size == 1)
      assert(apis.collect { case api: BeautySearchApi[IO] => api }.size == 1)

      val response = serve(apis, validRequestBody)
      assert(response.status == Status.Ok, s"es-only-rollback must serve the ES-backed default (200), got ${response.status}")
    }
  }

  // ============================================================================================
  // Case 3: explicit qdrant-supplement-not-ready -> 503, no silent fallback.
  // ============================================================================================

  "The explicit qdrant-supplement-not-ready launcher value" should {
    "reject a valid /beauty-search request with 503 over a real local server, without invoking the lexical/semantic/document-lookup backends" in {
      val apis     = supplementApis(notReadyModule, failIfCalledFixture)
      val response = serve(apis, validRequestBody)
      assert(response.status == Status.ServiceUnavailable, s"qdrant-supplement-not-ready must reject a valid request with 503, got ${response.status}")
    }

    "leave a malformed request at 400, not 503" in {
      val apis     = supplementApis(notReadyModule, failIfCalledFixture)
      val response = serve(apis, malformedRequestBody)
      assert(response.status == Status.BadRequest)
    }
  }

  // ============================================================================================
  // Case 4: explicit qdrant-supplement-ready.
  // ============================================================================================

  "The explicit qdrant-supplement-ready launcher value through the seam alone (no supplement bindings supplied)" should {
    "fail closed at graph composition, naming the exact missing supplement runtime bindings (READY_LAUNCHER_BINDINGS_BLOCKED)" in {
      val thrown  = intercept[Throwable](apisFromReadyModuleWithoutSupplementBindings())
      val message = causeChainMessage(thrown)
      assert(message.contains("qdrantSupplementLexicalElasticsearch"), s"missing-binding report must name the qualified lexical ES backend; got: $message")
      assert(message.contains("SemanticCandidateBackend"), s"missing-binding report must name the semantic candidate backend; got: $message")
      assert(message.contains("VariantSearchDocumentLookup"), s"missing-binding report must name the document lookup; got: $message")
    }
  }

  "The explicit qdrant-supplement-ready launcher value once the documented supplement bindings are supplied" should {
    "serve and stay capped at ExplicitConstraintsFilterPlusTop1 (one append), not AppendAll, preserving the ES prefix and never duplicating an ES id" in {
      val apis     = supplementApis(readyModule, threeAppendFixture)
      val response = serve(apis, validRequestBody)
      assert(response.status == Status.Ok)

      val json       = parseJson(response)
      val variantIds = json.hcursor.downField("variantCarousel").focus.flatMap(_.asArray).getOrElse(fail("missing variantCarousel"))
      assert(
        variantIds.size == 1,
        s"QP12: the ready route must stay capped at one append (ExplicitConstraintsFilterPlusTop1), not AppendAll, got ${variantIds.size}",
      )
    }
  }

  // ============================================================================================
  // Case 5: invalid env value -> fail closed, never ready, never silent Qdrant supplement.
  // ============================================================================================

  "An invalid launcher value" should {
    "fail closed by throwing, never producing a module that selects QdrantSupplementReady or the Qdrant supplement" in {
      val thrown = intercept[IllegalArgumentException] {
        BeautySearchQdrantSupplementActivationLauncherSeam.selectedModuleOrThrow(Some("totally-unrecognized"))
      }
      assert(thrown.getMessage.contains("totally-unrecognized"))
    }

    "agree with the underlying pure parser's fail-closed Left for the same value" in {
      BeautySearchQdrantSupplementActivationConfig.fromOperatorValue(Some("totally-unrecognized")) match {
        case Left(failure: QueryFailure) => assert(failure.message.contains("totally-unrecognized"))
        case Right(activation)           => fail(s"expected a fail-closed Left for an unrecognized value, got Right($activation)")
      }
    }
  }

  // ============================================================================================
  // Launcher-selected modules (the launcher's documented env-value -> module mapping).
  // ============================================================================================

  private def esOnlyRollbackModule: ModuleDef =
    BeautySearchQdrantSupplementActivationLauncherSeam.selectedModuleOrThrow(
      Some(BeautySearchQdrantSupplementActivationConfig.EsOnlyRollbackOperatorValue)
    )

  private def notReadyModule: ModuleDef =
    BeautySearchQdrantSupplementActivationLauncherSeam.selectedModuleOrThrow(
      Some(BeautySearchQdrantSupplementActivationConfig.QdrantSupplementNotReadyOperatorValue)
    )

  private def readyModule: ModuleDef =
    BeautySearchQdrantSupplementActivationLauncherSeam.selectedModuleOrThrow(
      Some(BeautySearchQdrantSupplementActivationConfig.QdrantSupplementReadyOperatorValue)
    )

  // ============================================================================================
  // Real-server smoke helper: serve a single request through a real local Ember server/client.
  // ============================================================================================

  private def serve(apis: Set[HttpApi[IO]], body: String): ObservedResponse =
    runIO(observe(combineApis(apis.toSeq*), postJson("/beauty-search", body)))

  // ============================================================================================
  // Fixtures and graph-composition helpers (QP6/QP8/QP10 style: Stub*/FailIfCalled*, no spies).
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
      make[BeautySearchBackend[IO]].named("qdrantSupplementLexicalElasticsearch").fromValue(fixture.lexicalBackend)
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

  // The ready module selected through the launcher seam alone, deliberately WITHOUT the supplement
  // runtime bindings: this is exactly how `LeaderboardPlugin` includes the selected module today, so
  // graph composition must fail closed and name the missing keys (READY_LAUNCHER_BINDINGS_BLOCKED).
  private def apisFromReadyModuleWithoutSupplementBindings(): Set[HttpApi[IO]] = {
    val module = new ModuleDef {
      include(readyModule)
      make[Async[Task]].fromValue(Async[Task])
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

  // Proves the collaborators are unused when the gate rejects: a 503 that silently fell back to
  // ES-only, served append-all, or became Qdrant-as-default would invoke one of these and die.
  private final class FailIfCalledBeautySearchBackend extends BeautySearchBackend[IO] {
    override def search(input: UserSearchInput, intent: ParsedSearchIntent): IO[QueryFailure, BeautySearchResponse] =
      ZIO.dieMessage("QP12: lexical backend must not be invoked when the serving gate rejects (enabledNotReady)")
  }

  private final class FailIfCalledSemanticCandidateBackend extends SemanticCandidateBackend[IO] {
    override def candidates(input: UserSearchInput, intent: ParsedSearchIntent): IO[QueryFailure, List[SemanticCandidateHit]] =
      ZIO.dieMessage("QP12: semantic candidate backend must not be invoked when the serving gate rejects (enabledNotReady)")
  }

  private final class FailIfCalledVariantSearchDocumentLookup extends VariantSearchDocumentLookup[IO] {
    override def lookup(variantIds: List[MasterServiceOfferVariantId]): IO[QueryFailure, Map[MasterServiceOfferVariantId, VariantSearchDocument]] =
      ZIO.dieMessage("QP12: document lookup must not be invoked when the serving gate rejects (enabledNotReady)")
  }

  private def causeChainMessage(throwable: Throwable): String =
    Iterator
      .iterate(throwable)(_.getCause)
      .takeWhile(_ != null)
      .map(error => Option(error.getMessage).getOrElse(error.toString))
      .mkString("\n")

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
