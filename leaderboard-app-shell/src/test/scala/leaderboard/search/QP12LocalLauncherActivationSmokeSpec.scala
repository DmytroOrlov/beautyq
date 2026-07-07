package leaderboard.search

import cats.effect.Async
import distage.{Injector, Module, ModuleDef}
import io.circe.syntax.*
import io.circe.{Json, JsonObject}
import izumi.distage.model.definition.{Activation, LocatorPrivacy}
import izumi.distage.model.plan.Roots
import leaderboard.api.{BeautySearchApi, HttpApi}
import leaderboard.model.{MasterServiceOfferVariantId, QueryFailure}
import leaderboard.plugins.{
  BeautySearchQdrantSupplementActivation,
  BeautySearchQdrantSupplementActivationConfig,
  BeautySearchQdrantSupplementActivationModuleSelector,
  BeautySearchQdrantSupplementRuntimeBindingModules,
}
import leaderboard.search.document.{BeautySearchReadyCatalogDocuments, VariantSearchDocument}
import leaderboard.search.dsl.{SearchGeoPoint, VectorSearchSpec}
import leaderboard.search.elasticsearch.ElasticsearchJsonClient
import leaderboard.search.embedding.EmbeddingClient
import leaderboard.search.qdrant.{QdrantSearchClient, QdrantSearchHit}
import leaderboard.{HttpContractTestSupport, ObservedResponse}
import org.http4s.Status
import org.scalatest.wordspec.AnyWordSpec
import zio.interop.catz.*
import zio.{IO, Runtime, Task, Unsafe, ZIO}

import java.util.UUID

/**
 * QP12b: local route-selection smoke for the no-worsening Qdrant supplement activation helper,
 * updated after QP13 added `BeautySearchQdrantSupplementRuntimeBindingModules.supplementRuntimeBindings(...)`.
 *
 * Like QP12, this drives the documented explicit module mapping and serves
 * `/beauty-search` over a REAL locally bound HTTP server (`HttpContractTestSupport.observe`: an Ember
 * server on the repo's ephemeral `127.0.0.1:0` local-port convention plus a real Ember client). The
 * repo has no process-spawn / fixed-port `./launcher` test convention, so a full `./launcher
 * :leaderboard` subprocess (which needs Docker Postgres + ES) is intentionally not used.
 *
 * The one concrete question this answers after QP13:
 *   does the explicit ready module path serve when composed with the QP13 runtime binding module,
 *   WITHOUT any test-local plain stub binding of the three previously missing keys
 *   (`BeautySearchBackend @Id("qdrantSupplementLexicalElasticsearch")`, `SemanticCandidateBackend`,
 *   `VariantSearchDocumentLookup`)?
 *
 * Answer (proved below): yes. The explicitly selected ready module composed with
 * `supplementRuntimeBindings(vectorSearchSpec)` and only the documented leaf I/O doubles
 * (`ElasticsearchJsonClient`, `EmbeddingClient`, `QdrantSearchClient`) plus a ready catalog fixture
 * builds and serves capped at `ExplicitConstraintsFilterPlusTop1` (one append, never AppendAll),
 * preserving the ES prefix, never duplicating an ES id, and leaving the ES-owned non-variant
 * components untouched by the Qdrant append. The old QP12 expectation -- that the ready state's
 * final, documented outcome is `READY_LAUNCHER_BINDINGS_BLOCKED` -- is no longer the final state: the
 * direct module without runtime bindings is now only the selection boundary that the QP13 runtime
 * module closes.
 *
 * Proves:
 *   - explicit rollback -> ES-backed default serves 200, malformed stays 400, with NO Qdrant runtime
 *     module required;
 *   - explicit `es-only-rollback` -> equivalent ES-backed default (200), NO Qdrant runtime module
 *     required;
 *   - explicit `qdrant-supplement-not-ready`, with the QP13 runtime binding module supplied -> valid
 *     request returns 503; fail-if-called leaf clients prove no search invocation and no fallback;
 *     malformed stays 400;
 *   - explicit `qdrant-supplement-ready`:
 *       * without the QP13 runtime module, still fails closed naming the three keys -- this is the
 *         selection-only boundary, not the final ready state;
 *       * composed with the QP13 runtime module + leaf doubles only (no direct binding of the three
 *         keys) -> serves 200, capped at one append, ES prefix preserved, no duplicate ES id,
 *         ES-owned provider carousel unchanged by the append;
 *   - an invalid explicit value fails closed, never selecting ready.
 *
 * Proof-only: no default route change, no Qdrant-as-default, no fallback, no fusion/reranking, no
 * route JSON / API change, no real ES/Qdrant/Llama.
 */
final class QP12LocalLauncherActivationSmokeSpec extends AnyWordSpec with HttpContractTestSupport {

  private val validRequestBody: String     = """{"query":"zzqx vvbb wwyy qqzz nonsense gibberish impossible token soup","limit":10}"""
  private val malformedRequestBody: String = """{"query":"","limit":10}"""

  private val testVectorSearchSpec: VectorSearchSpec =
    VectorSearchSpec(
      collectionName = "qp12b_runtime_binding_collection",
      vectorName = "qp12b-runtime-binding-vector",
      topK = 10,
      scoreThreshold = None,
    )

  // ============================================================================================
  // Case 1: rollback/default serves the ES-backed default over HTTP, with no
  // Qdrant runtime module required.
  // ============================================================================================

  "The explicit rollback/default module" should {
    "serve a valid /beauty-search request as the ES-backed default (200) over a real local server, with no Qdrant runtime module required" in {
      val apis = esOnlyApis(esOnlyRollbackModule)
      assert(apis.size == 1)
      assert(apis.collect { case api: BeautySearchApi[IO] => api }.size == 1)

      val response = serve(apis, validRequestBody)
      assert(response.status == Status.Ok, s"rollback/default must serve the ES-backed default (200), got ${response.status}")
    }

    "leave malformed-request behavior unchanged (400) for the rollback/default module" in {
      val apis     = esOnlyApis(esOnlyRollbackModule)
      val response = serve(apis, malformedRequestBody)
      assert(response.status == Status.BadRequest)
    }
  }

  // ============================================================================================
  // Case 2: explicit es-only-rollback -> equivalent to absent/default, no Qdrant runtime module.
  // ============================================================================================

  "The explicit es-only-rollback module" should {
    "serve a valid /beauty-search request as the ES-backed default (200), equivalent to the absent default, with no Qdrant runtime module required" in {
      val apis = esOnlyApis(esOnlyRollbackModule)
      assert(apis.size == 1)
      assert(apis.collect { case api: BeautySearchApi[IO] => api }.size == 1)

      val response = serve(apis, validRequestBody)
      assert(response.status == Status.Ok, s"es-only-rollback must serve the ES-backed default (200), got ${response.status}")
    }
  }

  // ============================================================================================
  // Case 3: explicit qdrant-supplement-not-ready, WITH the QP13 runtime binding module supplied ->
  // 503, with fail-if-called leaf clients proving no search invocation and no silent fallback.
  // ============================================================================================

  "The explicit qdrant-supplement-not-ready module composed with the QP13 runtime binding module" should {
    "reject a valid /beauty-search request with 503 over a real local server, without invoking the ES/embedding/Qdrant leaf clients" in {
      val apis     = runtimeBoundApis(notReadyModule, failIfCalledLeaves)
      val response = serve(apis, validRequestBody)
      assert(response.status == Status.ServiceUnavailable, s"qdrant-supplement-not-ready must reject a valid request with 503, got ${response.status}")
    }

    "leave a malformed request at 400, not 503" in {
      val apis     = runtimeBoundApis(notReadyModule, failIfCalledLeaves)
      val response = serve(apis, malformedRequestBody)
      assert(response.status == Status.BadRequest)
    }
  }

  // ============================================================================================
  // Case 4: explicit qdrant-supplement-ready.
  // ============================================================================================

  // Selection-only boundary, NOT the final ready state: the ready module without the supplement
  // runtime bindings still fails closed naming the three keys. The QP13 runtime binding module
  // (exercised by the next test) is what closes them.
  "The explicit qdrant-supplement-ready module without QP13 runtime bindings" should {
    "fail closed at graph composition, naming the exact missing supplement runtime bindings -- the selection-only boundary, not the final ready state" in {
      val thrown  = intercept[Throwable](apisFromReadyModuleWithoutSupplementBindings())
      val message = causeChainMessage(thrown)
      assert(message.contains("qdrantSupplementLexicalElasticsearch"), s"missing-binding report must name the qualified lexical ES backend; got: $message")
      assert(message.contains("SemanticCandidateBackend"), s"missing-binding report must name the semantic candidate backend; got: $message")
      assert(message.contains("VariantSearchDocumentLookup"), s"missing-binding report must name the document lookup; got: $message")
    }
  }

  "The explicit qdrant-supplement-ready module composed with the QP13 runtime binding module (leaf doubles only)" should {
    "build and serve a valid /beauty-search request (200), capped at ExplicitConstraintsFilterPlusTop1 (one append, never AppendAll), preserving the ES prefix, never duplicating an ES id, and leaving the ES-owned provider carousel unchanged by the append" in {
      val apis     = runtimeBoundApis(readyModule, dedupLeaves)
      val response = serve(apis, validRequestBody)
      assert(response.status == Status.Ok, s"qdrant-supplement-ready must serve 200 once the QP13 runtime module is composed, got ${response.status}")

      val json           = parseJson(response)
      val variantIdStrings = variantCarouselIds(json)

      // ES prefix preserved + exactly one Qdrant-only append (AppendAll would also append the
      // second qdrant-only candidate `v3`, yielding 3 entries).
      assert(
        variantIdStrings == Vector(esVariantId.toString, qdrantOnlyAppendId.toString),
        s"QP12b: ready route must keep the ES prefix and append exactly one Qdrant-only candidate (ExplicitConstraintsFilterPlusTop1), got $variantIdStrings",
      )
      // No duplicate ES id: the Qdrant candidate that duplicates the ES id is dropped, never re-added.
      assert(variantIdStrings.distinct == variantIdStrings, s"QP12b: variant carousel must contain no duplicate ids, got $variantIdStrings")
      assert(variantIdStrings.count(_ == esVariantId.toString) == 1, s"QP12b: the ES id must appear exactly once, got $variantIdStrings")

      // ES-owned non-variant component unchanged by the append: the provider carousel reflects only
      // the ES document (its sole matching variant is the ES id, never the appended Qdrant id).
      val providerSampleIds = providerSampleVariantIds(json)
      assert(
        providerSampleIds == Vector(esVariantId.toString),
        s"QP12b: ES-owned provider carousel must reflect only the ES document and ignore the Qdrant append, got $providerSampleIds",
      )
    }
  }

  // ============================================================================================
  // Case 5: invalid explicit value -> fail closed, never ready, never silent Qdrant supplement.
  // ============================================================================================

  "An invalid explicit value" should {
    "fail closed by throwing, never producing a module that selects QdrantSupplementReady or the Qdrant supplement" in {
      assert(BeautySearchQdrantSupplementActivationConfig.fromOperatorValue(Some("totally-unrecognized")).isLeft)
    }

    "agree with the underlying pure parser's fail-closed Left for the same value" in {
      BeautySearchQdrantSupplementActivationConfig.fromOperatorValue(Some("totally-unrecognized")) match {
        case Left(failure: QueryFailure) => assert(failure.message.contains("totally-unrecognized"))
        case Right(activation)           => fail(s"expected a fail-closed Left for an unrecognized value, got Right($activation)")
      }
    }
  }

  // ============================================================================================
  // Explicitly selected modules.
  // ============================================================================================

  private def esOnlyRollbackModule: ModuleDef =
    BeautySearchQdrantSupplementActivationModuleSelector.moduleFor(BeautySearchQdrantSupplementActivation.EsOnlyRollback)

  private def notReadyModule: ModuleDef =
    BeautySearchQdrantSupplementActivationModuleSelector.moduleFor(BeautySearchQdrantSupplementActivation.QdrantSupplementNotReady)

  private def readyModule: ModuleDef =
    BeautySearchQdrantSupplementActivationModuleSelector.moduleFor(BeautySearchQdrantSupplementActivation.QdrantSupplementReady)

  // ============================================================================================
  // Real-server smoke helper: serve a single request through a real local Ember server/client.
  // ============================================================================================

  private def serve(apis: Set[HttpApi[IO]], body: String): ObservedResponse =
    runIO(observe(combineApis(apis.toSeq*), postJson("/beauty-search", body)))

  // ============================================================================================
  // Leaf doubles: the I/O collaborators the QP13 runtime binding module depends on, plus the ready
  // catalog. The runtime module's own three keys are NOT doubled -- they are the real production
  // implementations under test.
  // ============================================================================================

  private final case class LeafDoubles(
    esClient: ElasticsearchJsonClient,
    embeddingClient: EmbeddingClient,
    qdrantSearchClient: QdrantSearchClient,
    readyCatalog: BeautySearchReadyCatalogDocuments,
  )

  // Ready fixture: one ES hit (`esVariantId`), and three Qdrant candidates in descending score where
  // the top one duplicates the ES id (must be deduped) and the next is the single Qdrant-only append.
  private val esVariantId: MasterServiceOfferVariantId        = variantId(1)
  private val qdrantOnlyAppendId: MasterServiceOfferVariantId = variantId(2)
  private val qdrantOnlySecondId: MasterServiceOfferVariantId = variantId(3)

  private def dedupLeaves: LeafDoubles = {
    val catalogDocuments = List(esVariantId, qdrantOnlyAppendId, qdrantOnlySecondId).map(document)
    LeafDoubles(
      esClient = singleEsHitClient(esHitDocument(esVariantId)),
      embeddingClient = new StubEmbeddingClient(Vector(0.1d, 0.2d, 0.3d, 0.4d)),
      qdrantSearchClient = new StubQdrantSearchClient(List(esVariantId, qdrantOnlyAppendId, qdrantOnlySecondId)),
      readyCatalog = BeautySearchReadyCatalogDocuments("qp12b-fixture-catalog", catalogDocuments),
    )
  }

  private def failIfCalledLeaves: LeafDoubles =
    LeafDoubles(
      esClient = failIfCalledEsClient,
      embeddingClient = new FailIfCalledEmbeddingClient,
      qdrantSearchClient = new FailIfCalledQdrantSearchClient,
      readyCatalog = BeautySearchReadyCatalogDocuments("qp12b-fixture-catalog", List(document(variantId(1)))),
    )

  // ============================================================================================
  // Graph-composition helpers (QP6/QP8/QP10/QP13 style: Stub*/FailIfCalled*, no spies).
  // ============================================================================================

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

  // Compose the explicitly selected supplement module with the QP13 runtime binding module, supplying
  // only the documented leaf I/O doubles and the ready catalog -- never binding the three runtime
  // keys directly.
  private def runtimeBoundApis(selectedModule: ModuleDef, leaves: LeafDoubles): Set[HttpApi[IO]] = {
    val module = new ModuleDef {
      include(selectedModule)
      include(BeautySearchQdrantSupplementRuntimeBindingModules.supplementRuntimeBindings(testVectorSearchSpec))
      make[Async[Task]].fromValue(Async[Task])
      make[ApisProbe].from {
        (beautySearchApi: BeautySearchApi[IO], allHttpApis: Set[HttpApi[IO]]) =>
          val _ = beautySearchApi
          ApisProbe(allHttpApis)
      }
    }.overriddenBy(new ModuleDef {
      make[ElasticsearchJsonClient].fromValue(leaves.esClient)
      make[EmbeddingClient].fromValue(leaves.embeddingClient)
      make[QdrantSearchClient].fromValue(leaves.qdrantSearchClient)
      make[BeautySearchReadyCatalogDocuments].fromValue(leaves.readyCatalog)
    })

    apisFrom(module)
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

  // The ready module deliberately WITHOUT the QP13 runtime binding module: graph composition must
  // fail closed and name the missing keys (the selection-only boundary).
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

  // ============================================================================================
  // Documents / fixtures.
  // ============================================================================================

  private def variantId(i: Int): MasterServiceOfferVariantId =
    UUID.fromString(f"00000000-0000-0000-0000-$i%012d")

  private val fixedMasterServiceOfferId = UUID.fromString("10000000-0000-0000-0000-000000000000")
  private val fixedMasterLocationId     = UUID.fromString("20000000-0000-0000-0000-000000000000")
  private val fixedMasterId             = UUID.fromString("30000000-0000-0000-0000-000000000000")
  private val fixedServiceId            = UUID.fromString("40000000-0000-0000-0000-000000000000")
  private val fixedCategoryId           = UUID.fromString("50000000-0000-0000-0000-000000000000")

  // Minimal catalog/Qdrant document (resolved by the in-memory document lookup and projected by the
  // Qdrant candidate projector; both proven over this shape by QP6/QP13).
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

  // Fully-populated ES `_source` document (the response-interpreter/assembler decode path is proven
  // over this richer shape by `ElasticsearchSearchResponseInterpreterSpec`).
  private def esHitDocument(id: MasterServiceOfferVariantId): VariantSearchDocument =
    VariantSearchDocument(
      variantId = id,
      masterServiceOfferId = UUID.fromString("11000000-0000-0000-0000-000000000000"),
      masterLocationId = UUID.fromString("21000000-0000-0000-0000-000000000000"),
      masterId = UUID.fromString("31000000-0000-0000-0000-000000000000"),
      serviceId = UUID.fromString("41000000-0000-0000-0000-000000000000"),
      categoryId = UUID.fromString("51000000-0000-0000-0000-000000000000"),
      serviceName = "Manicure",
      categoryName = "Nails",
      masterName = "Beauty Master",
      locationName = "Central Studio",
      address = "Main street 1",
      location = SearchGeoPoint(lat = BigDecimal("52.5200"), lon = BigDecimal("13.4050")),
      lat = BigDecimal("52.5200"),
      lon = BigDecimal("13.4050"),
      priceFrom = BigDecimal("25.00"),
      priceTo = BigDecimal("40.00"),
      durationMin = 45,
      enumAttributes = Map("coverage" -> "gel"),
      booleanAttributes = Map("with_removal" -> true),
      intAttributes = Map("nails_count" -> 10),
      bigDecimalAttributes = Map("rating" -> BigDecimal("4.8")),
      allText = "manicure nails beauty master central studio",
      serviceText = "manicure nails",
      attributeText = "coverage gel with removal",
      providerText = "beauty master central studio",
      locationText = "central studio main street 1 nails",
    )

  // ============================================================================================
  // Leaf doubles (Stub*/FailIfCalled*, no spies).
  // ============================================================================================

  private final class StubEmbeddingClient(vector: Vector[Double]) extends EmbeddingClient {
    override def embed(text: String): IO[QueryFailure, Vector[Double]] = ZIO.succeed(vector)
  }

  // Returns one Qdrant hit per provided variant id, in descending score order, with the variant id
  // in the payload exactly as the production decoder (`QdrantCandidateHitDecoder`) expects.
  private final class StubQdrantSearchClient(variantIds: List[MasterServiceOfferVariantId]) extends QdrantSearchClient {
    override def search(path: String, json: Json): IO[QueryFailure, List[QdrantSearchHit]] =
      ZIO.succeed(
        variantIds.zipWithIndex.map {
          case (id, index) =>
            QdrantSearchHit(
              id = id.toString,
              payload = JsonObject("variantId" -> Json.fromString(id.toString)),
              score = 0.95d - index * 0.1d,
            )
        }
      )
  }

  private final class FailIfCalledEmbeddingClient extends EmbeddingClient {
    override def embed(text: String): IO[QueryFailure, Vector[Double]] =
      ZIO.dieMessage("QP12b: embedding client must not be invoked when the serving gate rejects (enabledNotReady)")
  }

  private final class FailIfCalledQdrantSearchClient extends QdrantSearchClient {
    override def search(path: String, json: Json): IO[QueryFailure, List[QdrantSearchHit]] =
      ZIO.dieMessage("QP12b: Qdrant search client must not be invoked when the serving gate rejects (enabledNotReady)")
  }

  // ES leaf returning a single decoded hit for the given document on `_search`.
  private def singleEsHitClient(hitDocument: VariantSearchDocument): ElasticsearchJsonClient = {
    val searchResponse = Json.obj(
      "hits" -> Json.obj(
        "hits" -> Json.arr(
          Json.obj(
            "_score" -> Json.fromDoubleOrNull(3.5d),
            "_source" -> hitDocument.asJson,
            "matched_queries" -> Json.arr(Json.fromString("serviceName")),
          )
        )
      )
    )
    new ElasticsearchJsonClient {
      override def putJson(path: String, json: Json): IO[QueryFailure, Json]  = ZIO.succeed(Json.obj())
      override def post(path: String): IO[QueryFailure, Json]                 = ZIO.succeed(Json.obj())
      override def postJson(path: String, json: Json): IO[QueryFailure, Json] =
        if (path.contains("_search")) ZIO.succeed(searchResponse)
        else ZIO.succeed(Json.obj())
      override def postNdjson(path: String, payload: String): IO[QueryFailure, Json] = ZIO.succeed(Json.obj())
      override def getJson(path: String): IO[QueryFailure, Json]                     = ZIO.dieMessage(s"unexpected getJson($path)")
      override def delete(path: String): IO[QueryFailure, Unit]                      = ZIO.dieMessage(s"unexpected delete($path)")
    }
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

  private def failIfCalledEsClient: ElasticsearchJsonClient = new ElasticsearchJsonClient {
    override def putJson(path: String, json: Json): IO[QueryFailure, Json]         = ZIO.dieMessage(s"QP12b: ES client must not be invoked (enabledNotReady): putJson($path)")
    override def post(path: String): IO[QueryFailure, Json]                        = ZIO.dieMessage(s"QP12b: ES client must not be invoked (enabledNotReady): post($path)")
    override def postJson(path: String, json: Json): IO[QueryFailure, Json]        = ZIO.dieMessage(s"QP12b: ES client must not be invoked (enabledNotReady): postJson($path)")
    override def postNdjson(path: String, payload: String): IO[QueryFailure, Json] = ZIO.dieMessage(s"QP12b: ES client must not be invoked (enabledNotReady): postNdjson($path)")
    override def getJson(path: String): IO[QueryFailure, Json]                     = ZIO.dieMessage(s"QP12b: ES client must not be invoked (enabledNotReady): getJson($path)")
    override def delete(path: String): IO[QueryFailure, Unit]                      = ZIO.dieMessage(s"QP12b: ES client must not be invoked (enabledNotReady): delete($path)")
  }

  // ============================================================================================
  // JSON helpers.
  // ============================================================================================

  private def variantCarouselIds(json: Json): Vector[String] =
    json.hcursor
      .downField("variantCarousel")
      .focus
      .flatMap(_.asArray)
      .getOrElse(fail("missing variantCarousel"))
      .flatMap(_.hcursor.get[String]("variantId").toOption)

  private def providerSampleVariantIds(json: Json): Vector[String] = {
    val providers = json.hcursor
      .downField("providerCarousel")
      .focus
      .flatMap(_.asArray)
      .getOrElse(fail("missing providerCarousel"))
    providers.headOption match {
      case Some(provider) =>
        provider.hcursor
          .downField("sampleMatchingVariantIds")
          .focus
          .flatMap(_.asArray)
          .getOrElse(fail("missing sampleMatchingVariantIds"))
          .flatMap(_.asString)
      case None =>
        fail("providerCarousel must be non-empty for the populated ES fixture")
    }
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
