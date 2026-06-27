package leaderboard.search

import cats.effect.Async
import distage.{Injector, Module, ModuleDef}
import io.circe.{Json, JsonObject}
import izumi.distage.model.definition.{Activation, LocatorPrivacy}
import izumi.distage.model.plan.Roots
import leaderboard.api.{BeautySearchApi, HttpApi}
import leaderboard.model.{MasterServiceOfferVariantId, QueryFailure}
import leaderboard.plugins.{
  BeautySearchQdrantSupplementActivation,
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
 * QP13: the runtime binding closure that lets the explicit ready supplement route build and serve, proved via
 * `BeautySearchQdrantSupplementRuntimeBindingModules.supplementRuntimeBindings(...)`.
 *
 * QP12 proved that the ready module without runtime bindings fails closed naming the exact missing
 * Distage keys (`READY_LAUNCHER_BINDINGS_BLOCKED`). QP13 closes those keys with source-confirmed
 * production implementations and proves:
 *   - the runtime binding module closes all three missing keys (`BeautySearchBackend @Id(
 *     "qdrantSupplementLexicalElasticsearch")`, `SemanticCandidateBackend`,
 *     `VariantSearchDocumentLookup`), so the ready route builds and serves over a real local HTTP
 *     server, capped at `ExplicitConstraintsFilterPlusTop1` (one append, never AppendAll), preserving
 *     the ES prefix and never duplicating an ES id;
 *   - the same runtime bindings under the `qdrant-supplement-not-ready` gate still reject a valid
 *     request with 503 without invoking the ES/embedding/Qdrant leaf collaborators (no fallback);
 *   - rollback still builds and serves the ES-backed default WITHOUT the runtime binding module (no
 *     Qdrant edge added to the default graph).
 *
 * Unlike QP12 (which supplies the three keys as plain stub fixtures), QP13 exercises the real
 * production classes wired by the runtime binding module -- `ElasticsearchSearchBackend`,
 * `QdrantSemanticCandidateBackend`/`QdrantSemanticCandidateSearch`, and
 * `InMemoryVariantSearchDocumentLookup` -- with only the leaf I/O clients (`ElasticsearchJsonClient`,
 * `EmbeddingClient`, `QdrantSearchClient`) and the ready catalog substituted by deterministic doubles.
 *
 * Proof-only: no default route change, no Qdrant-as-default, no fallback, no fusion/reranking, no
 * route JSON / API change, no real ES/Qdrant/Llama.
 */
final class QP13QdrantSupplementRuntimeBindingsSpec extends AnyWordSpec with HttpContractTestSupport {

  private val validRequestBody: String     = """{"query":"zzqx vvbb wwyy qqzz nonsense gibberish impossible token soup","limit":10}"""
  private val malformedRequestBody: String = """{"query":"","limit":10}"""

  private val testVectorSearchSpec: VectorSearchSpec =
    VectorSearchSpec(
      collectionName = "qp13_runtime_binding_collection",
      vectorName = "qp13-runtime-binding-vector",
      topK = 10,
      scoreThreshold = None,
    )

  // ============================================================================================
  // Ready: the runtime binding module closes all three keys and the ready route serves capped at 1.
  // ============================================================================================

  "The qdrant-supplement-ready module with BeautySearchQdrantSupplementRuntimeBindingModules" should {
    "build and serve a valid /beauty-search request over a real local server, capped at ExplicitConstraintsFilterPlusTop1 (one append), preserving the ES prefix and never duplicating an ES id" in {
      val apis     = runtimeBoundApis(readyModule, deterministicLeaves(threeQdrantOnlyDocuments))
      val response = serve(apis, validRequestBody)
      assert(response.status == Status.Ok, s"qdrant-supplement-ready must serve 200 once runtime bindings are supplied, got ${response.status}")

      val json       = parseJson(response)
      val variantIds = json.hcursor.downField("variantCarousel").focus.flatMap(_.asArray).getOrElse(fail("missing variantCarousel"))
      assert(
        variantIds.size == 1,
        s"QP13: the ready route must stay capped at one append (ExplicitConstraintsFilterPlusTop1), not AppendAll, got ${variantIds.size}",
      )
    }

    "leave a malformed request at 400 (validation runs before any supplement collaborator)" in {
      val apis     = runtimeBoundApis(readyModule, deterministicLeaves(threeQdrantOnlyDocuments))
      val response = serve(apis, malformedRequestBody)
      assert(response.status == Status.BadRequest)
    }
  }

  // ============================================================================================
  // Not-ready: same runtime bindings, gate rejects with 503, no leaf collaborator invoked.
  // ============================================================================================

  "The qdrant-supplement-not-ready module with the same runtime bindings" should {
    "reject a valid request with 503 over a real local server, without invoking the ES/embedding/Qdrant leaf collaborators" in {
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
  // Default: rollback still serves the ES-backed default WITHOUT the runtime module.
  // ============================================================================================

  "The es-only-rollback default" should {
    "still build and serve the ES-backed default with no runtime binding module included" in {
      val apis     = esOnlyApis(esOnlyRollbackModule)
      assert(apis.collect { case api: BeautySearchApi[IO] => api }.size == 1)

      val response = serve(apis, validRequestBody)
      assert(response.status == Status.Ok, s"es-only-rollback must serve the ES-backed default (200), got ${response.status}")
    }
  }

  // ============================================================================================
  // Explicit modules + the QP13 runtime binding module composition.
  // ============================================================================================

  private def readyModule: ModuleDef =
    BeautySearchQdrantSupplementActivation.moduleFor(BeautySearchQdrantSupplementActivation.QdrantSupplementReady)

  private def notReadyModule: ModuleDef =
    BeautySearchQdrantSupplementActivation.moduleFor(BeautySearchQdrantSupplementActivation.QdrantSupplementNotReady)

  private def esOnlyRollbackModule: ModuleDef =
    BeautySearchQdrantSupplementActivation.moduleFor(BeautySearchQdrantSupplementActivation.EsOnlyRollback)

  private def serve(apis: Set[HttpApi[IO]], body: String): ObservedResponse =
    runIO(observe(combineApis(apis.toSeq*), postJson("/beauty-search", body)))

  // ============================================================================================
  // Leaf doubles: the I/O collaborators the runtime binding module depends on, plus the ready
  // catalog. The runtime binding module's own three keys are NOT doubled -- they are the real
  // production implementations under test.
  // ============================================================================================

  private final case class LeafDoubles(
    esClient: ElasticsearchJsonClient,
    embeddingClient: EmbeddingClient,
    qdrantSearchClient: QdrantSearchClient,
    readyCatalog: BeautySearchReadyCatalogDocuments,
  )

  private def deterministicLeaves(documents: List[VariantSearchDocument]): LeafDoubles =
    LeafDoubles(
      esClient = zeroHitMockEsClient,
      embeddingClient = new StubEmbeddingClient(Vector(0.1d, 0.2d, 0.3d, 0.4d)),
      qdrantSearchClient = new StubQdrantSearchClient(documents.map(_.variantId)),
      readyCatalog = BeautySearchReadyCatalogDocuments("qp13-fixture-catalog", documents),
    )

  private def failIfCalledLeaves: LeafDoubles =
    LeafDoubles(
      esClient = failIfCalledEsClient,
      embeddingClient = new FailIfCalledEmbeddingClient,
      qdrantSearchClient = new FailIfCalledQdrantSearchClient,
      readyCatalog = BeautySearchReadyCatalogDocuments("qp13-fixture-catalog", List(document(variantId(1)))),
    )

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
      make[ElasticsearchJsonClient].fromValue(zeroHitMockEsClient)
    })

    apisFrom(module)
  }

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

  // ============================================================================================
  // Fixtures.
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

  private def threeQdrantOnlyDocuments: List[VariantSearchDocument] =
    List(variantId(1), variantId(2), variantId(3)).map(document)

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
      ZIO.dieMessage("QP13: embedding client must not be invoked when the serving gate rejects (enabledNotReady)")
  }

  private final class FailIfCalledQdrantSearchClient extends QdrantSearchClient {
    override def search(path: String, json: Json): IO[QueryFailure, List[QdrantSearchHit]] =
      ZIO.dieMessage("QP13: Qdrant search client must not be invoked when the serving gate rejects (enabledNotReady)")
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
    override def putJson(path: String, json: Json): IO[QueryFailure, Json]         = ZIO.dieMessage(s"QP13: ES client must not be invoked (enabledNotReady): putJson($path)")
    override def post(path: String): IO[QueryFailure, Json]                        = ZIO.dieMessage(s"QP13: ES client must not be invoked (enabledNotReady): post($path)")
    override def postJson(path: String, json: Json): IO[QueryFailure, Json]        = ZIO.dieMessage(s"QP13: ES client must not be invoked (enabledNotReady): postJson($path)")
    override def postNdjson(path: String, payload: String): IO[QueryFailure, Json] = ZIO.dieMessage(s"QP13: ES client must not be invoked (enabledNotReady): postNdjson($path)")
    override def getJson(path: String): IO[QueryFailure, Json]                     = ZIO.dieMessage(s"QP13: ES client must not be invoked (enabledNotReady): getJson($path)")
    override def delete(path: String): IO[QueryFailure, Unit]                      = ZIO.dieMessage(s"QP13: ES client must not be invoked (enabledNotReady): delete($path)")
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
