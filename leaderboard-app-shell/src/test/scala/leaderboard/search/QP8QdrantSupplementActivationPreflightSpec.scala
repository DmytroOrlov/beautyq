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
import leaderboard.plugins.BeautySearchQdrantSupplementActivationConfig
import leaderboard.plugins.BeautySearchQdrantSupplementActivationModuleSelector
import leaderboard.plugins.BeautySearchQdrantSupplementActivationPreflight
import leaderboard.plugins.BeautySearchQdrantSupplementActivationPreflightStatus.{Blocked, ReadyToEnable}
import leaderboard.plugins.BeautySearchQdrantSupplementRuntimeBindingPlan
import leaderboard.search.document.VariantSearchDocument
import leaderboard.search.dsl.{EmbeddingSpec, SearchGeoPoint, VectorDistance, VectorSearchSpec}
import leaderboard.search.elasticsearch.ElasticsearchJsonClient
import leaderboard.search.qdrant.{
  ObservedQdrantVectorConfig,
  QdrantCollectionCompatibilityExpectation,
  QdrantCollectionCompatibilityMismatch,
  QdrantCollectionReadinessConfig,
  QdrantCollectionReadinessInput,
}
import leaderboard.search.semantic.{SemanticCandidateBackend, SemanticCandidateHit, VariantSearchDocumentLookup}
import leaderboard.{HttpContractTestSupport, ObservedResponse}
import org.http4s.{HttpApp, Request, Status}
import org.scalatest.wordspec.AnyWordSpec
import zio.interop.catz.*
import zio.{IO, Runtime, Task, Unsafe, ZIO}

import java.util.UUID

/**
 * QP8: operator-facing preflight/probe for the no-worsening Qdrant supplement activation path, proved
 * via the pure `BeautySearchQdrantSupplementActivationPreflight.preflight(...)` helper plus focused
 * QP6/QP7-style route probes.
 *
 * Proves:
 *   - absent/explicit-ES-only/explicit-not-ready/invalid operator config all preflight as `Blocked`,
 *     with the matching reason label, and invalid config never selects `QdrantSupplementReady`;
 *   - explicit `qdrant-supplement-ready` preflights as `ReadyToEnable` only when the supplied readiness
 *     expectation/observed pair is compatible, and as `Blocked` / `READINESS_MISMATCH` for every single
 *     mismatch axis (collection name, vector name, dimension, distance, embedding model);
 *   - the not-ready route still returns 503 and the ready route still caps at
 *     `ExplicitConstraintsFilterPlusTop1`, not `AppendAll`;
 *   - the default ES-backed route is unaffected and requires no Qdrant semantic backend / document
 *     lookup binding.
 *
 * Proof-only: this is a preflight/probe, not production activation, role/launcher integration, a
 * default-route switch, or a public API/route JSON change. No real ES/Qdrant/Llama.
 */
final class QP8QdrantSupplementActivationPreflightSpec extends AnyWordSpec with HttpContractTestSupport {

  private val validRequestBody: String     = """{"query":"zzqx vvbb wwyy qqzz nonsense gibberish impossible token soup","limit":10}"""
  private val malformedRequestBody: String = """{"query":"","limit":10}"""

  // ============================================================================================
  // Pure preflight contract: rollback / not-ready / invalid config -> Blocked, never ready.
  // ============================================================================================

  "BeautySearchQdrantSupplementActivationPreflight.preflight" should {
    "report Blocked / ES_ONLY_ROLLBACK_SELECTED for absent operator config" in {
      val result = BeautySearchQdrantSupplementActivationPreflight.preflight(None, expectation, compatibleObserved)
      assert(result.status == Blocked)
      assert(result.selectedActivation == Some(EsOnlyRollback))
      assert(result.reason == BeautySearchQdrantSupplementActivationPreflight.EsOnlyRollbackSelectedReason)
      assert(result.mismatchDetail.isEmpty)
    }

    "report Blocked / ES_ONLY_ROLLBACK_SELECTED for the explicit ES-only operator value" in {
      val result = BeautySearchQdrantSupplementActivationPreflight.preflight(
        Some(BeautySearchQdrantSupplementActivationConfig.EsOnlyRollbackOperatorValue),
        expectation,
        compatibleObserved,
      )
      assert(result.status == Blocked)
      assert(result.selectedActivation == Some(EsOnlyRollback))
      assert(result.reason == BeautySearchQdrantSupplementActivationPreflight.EsOnlyRollbackSelectedReason)
    }

    "report Blocked / QDRANT_SUPPLEMENT_NOT_READY_SELECTED for the explicit not-ready operator value" in {
      val result = BeautySearchQdrantSupplementActivationPreflight.preflight(
        Some(BeautySearchQdrantSupplementActivationConfig.QdrantSupplementNotReadyOperatorValue),
        expectation,
        compatibleObserved,
      )
      assert(result.status == Blocked)
      assert(result.selectedActivation == Some(QdrantSupplementNotReady))
      assert(result.reason == BeautySearchQdrantSupplementActivationPreflight.QdrantSupplementNotReadySelectedReason)
    }

    "report Blocked / INVALID_OPERATOR_CONFIG for an unrecognized operator value, never selecting QdrantSupplementReady" in {
      val result = BeautySearchQdrantSupplementActivationPreflight.preflight(Some("totally-unrecognized"), expectation, compatibleObserved)
      assert(result.status == Blocked)
      assert(result.selectedActivation.isEmpty)
      assert(result.reason == BeautySearchQdrantSupplementActivationPreflight.InvalidOperatorConfigReason)
      assert(result.selectedActivation != Some(QdrantSupplementReady))
    }
  }

  // ============================================================================================
  // Pure preflight contract: explicit ready, gated on readiness compatibility.
  // ============================================================================================

  "BeautySearchQdrantSupplementActivationPreflight.preflight for the explicit ready operator value" should {
    "report ReadyToEnable / READY_TO_ENABLE when the supplied readiness expectation/observed pair is compatible" in {
      val result = BeautySearchQdrantSupplementActivationPreflight.preflight(readyOperatorValue, expectation, compatibleObserved)
      assert(result.status == ReadyToEnable)
      assert(result.selectedActivation == Some(QdrantSupplementReady))
      assert(result.reason == BeautySearchQdrantSupplementActivationPreflight.ReadyToEnableReason)
      assert(result.mismatchDetail.isEmpty)
    }

    "report Blocked / READINESS_MISMATCH, never ReadyToEnable, on a collection name mismatch" in {
      val mismatchedObserved = compatibleObserved.copy(collectionName = "other_collection")
      val result              = BeautySearchQdrantSupplementActivationPreflight.preflight(readyOperatorValue, expectation, mismatchedObserved)
      assert(result.status == Blocked)
      assert(result.reason == BeautySearchQdrantSupplementActivationPreflight.ReadinessMismatchReason)
      assert(result.mismatchDetail.exists(_.contains(QdrantCollectionCompatibilityMismatch.CollectionNameMismatch(expectation.collectionName, "other_collection"))))
    }

    "report Blocked / READINESS_MISMATCH, never ReadyToEnable, on a vector name mismatch" in {
      val mismatchedObserved = compatibleObserved.copy(vectorName = "other-vector")
      val result              = BeautySearchQdrantSupplementActivationPreflight.preflight(readyOperatorValue, expectation, mismatchedObserved)
      assert(result.status == Blocked)
      assert(result.reason == BeautySearchQdrantSupplementActivationPreflight.ReadinessMismatchReason)
      assert(result.mismatchDetail.exists(_.contains(QdrantCollectionCompatibilityMismatch.VectorNameMismatch(expectation.vectorName, "other-vector"))))
    }

    "report Blocked / READINESS_MISMATCH, never ReadyToEnable, on a dimension mismatch" in {
      val mismatchedObserved = compatibleObserved.copy(dimension = 768)
      val result              = BeautySearchQdrantSupplementActivationPreflight.preflight(readyOperatorValue, expectation, mismatchedObserved)
      assert(result.status == Blocked)
      assert(result.reason == BeautySearchQdrantSupplementActivationPreflight.ReadinessMismatchReason)
      assert(result.mismatchDetail.exists(_.contains(QdrantCollectionCompatibilityMismatch.DimensionMismatch(expectation.expectedDimension, 768))))
    }

    "report Blocked / READINESS_MISMATCH, never ReadyToEnable, on a distance mismatch" in {
      val mismatchedObserved = compatibleObserved.copy(distance = VectorDistance.Euclidean)
      val result              = BeautySearchQdrantSupplementActivationPreflight.preflight(readyOperatorValue, expectation, mismatchedObserved)
      assert(result.status == Blocked)
      assert(result.reason == BeautySearchQdrantSupplementActivationPreflight.ReadinessMismatchReason)
      assert(result.mismatchDetail.exists(_.contains(QdrantCollectionCompatibilityMismatch.DistanceMismatch(expectation.expectedDistance, VectorDistance.Euclidean))))
    }

    "report Blocked / READINESS_MISMATCH, never ReadyToEnable, on an embedding model mismatch" in {
      val mismatchedObserved = compatibleObserved.copy(embeddingModelName = Some("other-model"))
      val result              = BeautySearchQdrantSupplementActivationPreflight.preflight(readyOperatorValue, expectation, mismatchedObserved)
      assert(result.status == Blocked)
      assert(result.reason == BeautySearchQdrantSupplementActivationPreflight.ReadinessMismatchReason)
      assert(result.mismatchDetail.exists(_.contains(QdrantCollectionCompatibilityMismatch.EmbeddingModelMismatch(expectation.embeddingModelName, "other-model"))))
    }
  }

  // ============================================================================================
  // Route behavior probe: not-ready route still 503; ready route still capped at
  // ExplicitConstraintsFilterPlusTop1, not AppendAll. Reuses the QP6/QP7 module/route-probe style.
  // ============================================================================================

  "Route behavior probe for a ReadyToEnable-preflighted selection" should {
    "still reject a valid request with 503 for QdrantSupplementNotReady, without invoking the lexical backend, the semantic backend, or the document lookup" in {
      val apis     = supplementApis(QdrantSupplementNotReady, failIfCalledFixture)
      val response = runIO(observeRoute(apis, postJson("/beauty-search", validRequestBody)))
      assert(response.status == Status.ServiceUnavailable, s"QdrantSupplementNotReady must reject a valid request with 503, got ${response.status}")
    }

    "still cap the QdrantSupplementReady route at ExplicitConstraintsFilterPlusTop1 (one append), not AppendAll, even with several eligible Qdrant-only candidates" in {
      val apis     = supplementApis(QdrantSupplementReady, threeAppendFixture)
      val response = runIO(observeRoute(apis, postJson("/beauty-search", validRequestBody)))
      assert(response.status == Status.Ok)

      val json       = parseJson(response)
      val variantIds = json.hcursor.downField("variantCarousel").focus.flatMap(_.asArray).getOrElse(fail("missing variantCarousel"))
      assert(
        variantIds.size == 1,
        s"QP8: the ready route must stay capped at one append (ExplicitConstraintsFilterPlusTop1), not AppendAll, got ${variantIds.size}",
      )
    }
  }

  "The default ES route that LeaderboardPlugin includes" should {
    "remain unchanged and ES-backed: exactly one BeautySearchApi, constructible with no Qdrant semantic backend or document lookup binding at all" in {
      val apis = esOnlyApis()
      assert(apis.size == 1)
      assert(apis.collect { case api: BeautySearchApi[IO] => api }.size == 1)

      val response = runIO(observeRoute(apis, postJson("/beauty-search", validRequestBody)))
      assert(response.status == Status.Ok, s"default route must stay ES-backed (200 OK), got ${response.status}")
    }

    "leave malformed-request behavior unchanged (400)" in {
      val apis     = esOnlyApis()
      val response = runIO(observeRoute(apis, postJson("/beauty-search", malformedRequestBody)))
      assert(response.status == Status.BadRequest)
    }
  }

  // ============================================================================================
  // Shared QP5/QP6-style readiness fixtures (pure; no real Qdrant call).
  // ============================================================================================

  private val readyOperatorValue: Option[String] =
    Some(BeautySearchQdrantSupplementActivationConfig.QdrantSupplementReadyOperatorValue)

  private val embeddingSpec: EmbeddingSpec[VariantSearchDocument] =
    EmbeddingSpec[VariantSearchDocument](
      vectorName = "qp8-embedding-spec-vector-name-is-not-identity-source",
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
        purpose = "qp8-activation-preflight",
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

  // ============================================================================================
  // Shared activation/route fixtures (QP6/QP7 fixture style: Stub*/FailIfCalled*, no spies).
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
      ZIO.dieMessage("QP8: lexical backend must not be invoked when the serving gate rejects (enabledNotReady)")
  }

  private final class FailIfCalledSemanticCandidateBackend extends SemanticCandidateBackend[IO] {
    override def candidates(input: UserSearchInput, intent: ParsedSearchIntent): IO[QueryFailure, List[SemanticCandidateHit]] =
      ZIO.dieMessage("QP8: semantic candidate backend must not be invoked when the serving gate rejects (enabledNotReady)")
  }

  private final class FailIfCalledVariantSearchDocumentLookup extends VariantSearchDocumentLookup[IO] {
    override def lookup(variantIds: List[MasterServiceOfferVariantId]): IO[QueryFailure, Map[MasterServiceOfferVariantId, VariantSearchDocument]] =
      ZIO.dieMessage("QP8: document lookup must not be invoked when the serving gate rejects (enabledNotReady)")
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
