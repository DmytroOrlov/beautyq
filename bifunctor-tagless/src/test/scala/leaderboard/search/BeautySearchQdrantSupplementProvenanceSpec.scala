package leaderboard.search

import cats.effect.Async
import distage.{DIKey, Injector, Mode, Module, ModuleDef, Scene}
import io.circe.parser.decode
import io.circe.syntax.*
import io.circe.{Json, JsonObject}
import izumi.distage.model.definition.{Activation, LocatorPrivacy}
import izumi.distage.model.plan.Roots
import leaderboard.api.{BeautySearchApi, HttpApi}
import leaderboard.config.{ElasticsearchPortCfg, QdrantPortCfg}
import leaderboard.model.{MasterServiceOfferVariantId, QueryFailure}
import leaderboard.plugins.BeautySearchQdrantSupplementActivationConfig
import leaderboard.plugins.BeautySearchQdrantSupplementActivation
import leaderboard.plugins.BeautySearchQdrantSupplementActivationPreflightCommand
import leaderboard.plugins.BeautySearchQdrantSupplementActivationPreflightStatus.ReadyToEnable
import leaderboard.plugins.BeautySearchLocalQdrantSupplementLauncherModule
import leaderboard.plugins.BeautySearchQdrantSupplementRuntimeBindingModules
import leaderboard.search.document.{BeautySearchCatalogSnapshot, BeautySearchReadyCatalogDocuments, InMemoryVariantSearchDocumentSnapshotProvider, VariantSearchDocument, VariantSearchDocumentBuilder}
import leaderboard.search.dsl.{BeautySearchSpec, BeautySearchSpecV1, EmbeddingSpec, SearchGeoPoint, VectorDistance, VectorSearchSpec}
import leaderboard.search.elasticsearch.{ElasticsearchIngestionInterpreter, ElasticsearchJsonClient, ElasticsearchMappingInterpreter}
import leaderboard.search.embedding.{EmbeddingClient, LlamaCppEmbeddingClient, LlamaCppEmbeddingClientConfig}
import leaderboard.search.qdrant.{
  QdrantClient,
  QdrantClientCollectionInfoAdapter,
  QdrantClientSearchAdapter,
  QdrantCollectionCompatibilityChecker,
  QdrantCollectionReadinessConfig,
  QdrantCollectionReadinessInput,
  QdrantEmbeddingBenchmarkDefaultCompositionFactory,
  QdrantJsonInterpreter,
  QdrantSearchClient,
  QdrantSearchHit,
}
import leaderboard.seed.BeautyQSeedLoader
import leaderboard.{HttpContractTestSupport, LeaderboardTest, ObservedResponse, ProdTest}
import org.http4s.Status
import zio.interop.catz.*
import zio.{IO, Runtime, Task, Unsafe, ZIO}

import java.util.UUID

/**
 * Permanent contract for `/beauty-search` response provenance: ES-only rollback responses expose
 * ES-only provenance, ready responses expose append / no-append Qdrant-supplement provenance without
 * disturbing ES-owned response components, not-ready fails closed at 503, and the managed local default
 * serves supplement provenance. Both deterministic control routes and a real-resource ES + Qdrant route
 * harness are exercised.
 */
final class BeautySearchQdrantSupplementProvenanceSpec
    extends LeaderboardTest
    with ProdTest
    with HttpContractTestSupport {

  override def config = super.config.copy(
    activation = super.config.activation ++ Activation(Mode -> Mode.Test),
    memoizationRoots = super.config.memoizationRoots + DIKey[ElasticsearchPortCfg] + DIKey[QdrantPortCfg],
  )

  private val baseSpec: BeautySearchSpec = BeautySearchSpecV1.spec

  private val canonicalSeed = new BeautyQSeedLoader.ResourceLoader().load() match {
    case Right(value) => value
    case Left(error)  => throw new RuntimeException(error.message)
  }

  private val canonicalDocuments: List[VariantSearchDocument] =
    VariantSearchDocumentBuilder.build(BeautySearchCatalogSnapshot.fromSeedData(canonicalSeed)) match {
      case Right(value) => value
      case Left(error)  => throw new RuntimeException(error.message)
    }

  private val appendProbe = QueryCase(
    label = "q_broad_006_ready_append_probe",
    input = UserSearchInput("beauty near Wandsbek Markt", None, None, limit = 10),
  )

  private val noAppendProbe = QueryCase(
    label = "manicure_real_route_probe",
    input = UserSearchInput("маникюр", None, None, limit = 10),
  )

  private val controlVectorSearchSpec: VectorSearchSpec =
    VectorSearchSpec(
      collectionName = "qp25_control_collection",
      vectorName = "qp25-control-vector",
      topK = 10,
      scoreThreshold = None,
    )

  "QP25 response provenance on deterministic route controls" should {
    "expose ES-only provenance for rollback/default responses" in {
      val response = serve(esOnlyApis(defaultModule, singleEsHitClient(esHitDocument(esVariantId))), UserSearchInput("manicure", None, None, limit = 10))
      assert(response.status == Status.Ok, s"ES-only route must serve 200, got ${response.status}")

      val decoded = decodeObserved(response, "deterministic-es-only", "default")
      assert(decoded.executionMode == BeautySearchExecutionMode.EsOnly)
      assert(decoded.qdrantSupplement == QdrantSupplementSummary.notUsed)
      assert(decoded.variantCarousel.nonEmpty)
      assert(decoded.variantCarousel.forall(_.resultOrigin == VariantResultOrigin.EsBaseline))
      assertJsonStringField(response, "executionMode", BeautySearchExecutionMode.EsOnly.label)
    }

    "keep duplicate-dropped Qdrant candidates out of frontend contribution provenance" in {
      val response = serve(runtimeBoundApis(readyModule, duplicateOnlyLeaves, controlVectorSearchSpec), UserSearchInput("manicure", None, None, limit = 10))
      assert(response.status == Status.Ok, s"ready duplicate-only route must serve 200, got ${response.status}")

      val decoded = decodeObserved(response, "deterministic-duplicate", "ready")
      assert(decoded.executionMode == BeautySearchExecutionMode.EsPlusQdrantSupplement)
      assert(decoded.qdrantSupplement == QdrantSupplementSummary.usedNoAppend(QdrantSupplementPolicyName.ExplicitConstraintsFilterPlusTop1))
      assert(decoded.variantCarousel.map(_.variantId) == List(esVariantId))
      assert(decoded.variantCarousel.forall(_.resultOrigin == VariantResultOrigin.EsBaseline))
    }

    "make the managed local launcher default use Qdrant supplement provenance without the ready env path" in {
      val appendResponse = serve(
        managedLocalLauncherApis(qdrantOnlyAppendLeaves, controlVectorSearchSpec),
        appendProbe.input,
      )
      assert(appendResponse.status == Status.Ok, s"managed local append route must serve 200, got ${appendResponse.status}")

      val appendDecoded = decodeObserved(appendResponse, "deterministic-managed-append", "managed-local-default")
      val qdrantVariants = appendDecoded.variantCarousel.filter(_.resultOrigin == VariantResultOrigin.QdrantSupplement)
      assert(appendDecoded.executionMode == BeautySearchExecutionMode.EsPlusQdrantSupplement)
      assert(appendDecoded.qdrantSupplement.status == QdrantSupplementStatus.UsedWithAppend)
      assert(appendDecoded.qdrantSupplement.contribution == QdrantSupplementContribution.QdrantOnlyVariantAppend)
      assert(qdrantVariants.size == 1)
      assert(appendDecoded.qdrantSupplement.appendedVariantIds == qdrantVariants.map(_.variantId))

      val noAppendResponse = serve(
        managedLocalLauncherApis(duplicateOnlyLeaves, controlVectorSearchSpec),
        noAppendProbe.input,
      )
      assert(noAppendResponse.status == Status.Ok, s"managed local no-append route must serve 200, got ${noAppendResponse.status}")

      val noAppendDecoded = decodeObserved(noAppendResponse, "deterministic-managed-no-append", "managed-local-default")
      assert(noAppendDecoded.executionMode == BeautySearchExecutionMode.EsPlusQdrantSupplement)
      assert(noAppendDecoded.qdrantSupplement == QdrantSupplementSummary.usedNoAppend(QdrantSupplementPolicyName.ExplicitConstraintsFilterPlusTop1))
      assert(noAppendDecoded.variantCarousel.nonEmpty)
      assert(noAppendDecoded.variantCarousel.forall(_.resultOrigin == VariantResultOrigin.EsBaseline))
    }

    "keep not-ready at 503 with no fake ES-only provenance, and fail closed on invalid activation" in {
      val notReady = serve(runtimeBoundApis(notReadyModule, failIfCalledLeaves, controlVectorSearchSpec), appendProbe.input)
      assert(notReady.status == Status.ServiceUnavailable, s"not-ready route must reject with 503, got ${notReady.status}")
      assert(
        decode[BeautySearchResponse](notReady.body).isLeft,
        s"not-ready response must not decode as a successful BeautySearchResponse with fake provenance: ${notReady.body}",
      )

      assert(BeautySearchQdrantSupplementActivationConfig.fromOperatorValue(Some("totally-unrecognized")).isLeft)
    }
  }

  "QP25 response provenance on the real-resource local/test route harness" should {
    "expose ready/no-append and ready/append provenance without changing ES-owned components" in {
      (esPortCfg: ElasticsearchPortCfg, qdrantPortCfg: QdrantPortCfg) =>
        val esClient          = new ElasticsearchTestClient(esPortCfg.host, esPortCfg.port)
        val qdrantClient      = new QdrantClient(qdrantPortCfg.host, qdrantPortCfg.port)
        val embeddingEndpoint = sys.env.getOrElse("M18_QDRANT_EMBEDDING_ENDPOINT", "http://localhost:8081")
        val embeddingClient   = new LlamaCppEmbeddingClient(LlamaCppEmbeddingClientConfig(baseUrl = embeddingEndpoint))

        val resourceProbe = runIO(
          for {
            esResult        <- esClient.getJson("/").either
            embeddingResult <- embeddingClient.embed("qp25 response provenance probe").either
            qdrantResult    <- qdrantClient.collectionInfo("/collections").either
          } yield (esResult, embeddingResult, qdrantResult)
        )

        resourceProbe match {
          case (Right(_), Right(vector), Right(_)) if vector.nonEmpty =>
            runIO(runRealRouteProvenance(esClient, qdrantClient, embeddingClient, vector.length))
          case (esResult, embeddingResult, qdrantResult) =>
            cancel(
              s"QP25_RESOURCE_GATED: esReachable=${esResult.isRight}, " +
                s"embeddingReachable=${embeddingResult.exists(_.nonEmpty)}, " +
                s"qdrantReachable=${qdrantResult.isRight}, embeddingEndpoint=$embeddingEndpoint"
            )
        }
    }
  }

  private final case class QueryCase(label: String, input: UserSearchInput)

  private final case class QueryResponses(
    query: QueryCase,
    baseline: BeautySearchResponse,
    ready: BeautySearchResponse,
  )

  private final case class RealLeaves(
    esClient: ElasticsearchJsonClient,
    embeddingClient: EmbeddingClient,
    qdrantSearchClient: QdrantSearchClient,
    readyCatalog: BeautySearchReadyCatalogDocuments,
  )

  private final case class ReadyGraphProbe(allHttpApis: Set[HttpApi[IO]])
  private final case class ApisProbe(allHttpApis: Set[HttpApi[IO]])

  private def runRealRouteProvenance(
    esClient: ElasticsearchTestClient,
    qdrantClient: QdrantClient,
    embeddingClient: LlamaCppEmbeddingClient,
    vectorDimension: Int,
  ): IO[QueryFailure, Unit] = {
    val embeddingSpec = EmbeddingSpec[VariantSearchDocument](
      vectorName = "llama-cpp-embedding",
      modelName = "qp25-test-local-provenance",
      dimension = vectorDimension,
      distance = VectorDistance.Cosine,
      sourceTextFieldPaths = List("serviceText", "attributeText", "allText", "categoryName"),
    )
    val readinessConfig = QdrantCollectionReadinessConfig.derive(
      QdrantCollectionReadinessInput(
        domainName = "beautyq",
        searchSpecVersion = "v1",
        purpose = s"qp25-test-local-provenance-${UUID.randomUUID().toString.replace('-', '_')}",
        embeddingSpec = embeddingSpec,
        vectorSearchSpec = VectorSearchSpec(
          collectionName = "placeholder",
          vectorName = "llama-cpp-embedding",
          topK = math.max(canonicalDocuments.size, 1),
          scoreThreshold = None,
        ),
      )
    )
    val collectionPath = s"/collections/${readinessConfig.collectionName}"
    val routeSpecBase = baseSpec.copy(
      variantDocument = baseSpec.variantDocument.copy(
        indexName = s"${baseSpec.variantDocument.indexName}_qp25_${UUID.randomUUID().toString.replace('-', '_')}"
      ),
      vectorSearchSpec = Some(readinessConfig.vectorSearchSpec),
    )
    val baselineRouteSpec = routeSpecBase.copy(
      variantDocument = routeSpecBase.variantDocument.copy(indexName = s"${routeSpecBase.variantDocument.indexName}_baseline")
    )
    val readyRouteSpec = routeSpecBase.copy(
      variantDocument = routeSpecBase.variantDocument.copy(indexName = s"${routeSpecBase.variantDocument.indexName}_ready")
    )
    val snapshotProvider = new InMemoryVariantSearchDocumentSnapshotProvider[IO](canonicalDocuments)
    val compositionFactory = new QdrantEmbeddingBenchmarkDefaultCompositionFactory(qdrantClient)
    val readyCatalog = BeautySearchReadyCatalogDocuments("qp25-ready-catalog", canonicalDocuments)
    val checker = new QdrantCollectionCompatibilityChecker(new QdrantClientCollectionInfoAdapter(qdrantClient))

    (
      for {
        composition <- compositionFactory.build(readinessConfig, embeddingClient, snapshotProvider, embeddingSpec)
        _ <- qdrantClient.createCollection(
          collectionPath,
          QdrantJsonInterpreter.createCollectionJson(readinessConfig.vectorSearchSpec, embeddingSpec),
        )
        _ <- composition.indexSnapshot()
        _ <- prepareEsIndexWith(readyRouteSpec, esClient, canonicalDocuments)
        preflight <- BeautySearchQdrantSupplementActivationPreflightCommand.run(
          Some(BeautySearchQdrantSupplementActivationConfig.QdrantSupplementReadyOperatorValue),
          readinessConfig.compatibilityExpectation,
          checker,
        )
        _ <- ZIO.succeed {
          assert(preflight.status == ReadyToEnable, s"QP25: expected ready preflight, got $preflight")
        }
        esJsonClient = new ElasticsearchJsonClientAdapter(esClient)
        baselineApis = esOnlyApis(moduleWithTestSpec(defaultModule, baselineRouteSpec), esJsonClient)
        readyApis = runtimeBoundApis(
          moduleWithTestSpec(readyModule, readyRouteSpec),
          RealLeaves(
            esClient = esJsonClient,
            embeddingClient = embeddingClient,
            qdrantSearchClient = new QdrantClientSearchAdapter(qdrantClient),
            readyCatalog = readyCatalog,
          ),
          readinessConfig.vectorSearchSpec,
        )
        noAppend <- observeQuery(baselineApis, readyApis, noAppendProbe)
        append   <- observeQuery(baselineApis, readyApis, appendProbe)
        _ <- ZIO.succeed {
          assertReadyNoAppend(noAppend)
          assertReadyAppend(append)
        }
      } yield ()
    ).ensuring(qdrantClient.deleteCollection(collectionPath).either.unit)
      .ensuring(esClient.deleteIndex(baselineRouteSpec.variantDocument.indexName).either.unit)
      .ensuring(esClient.deleteIndex(readyRouteSpec.variantDocument.indexName).either.unit)
  }

  private def observeQuery(
    baselineApis: Set[HttpApi[IO]],
    readyApis: Set[HttpApi[IO]],
    query: QueryCase,
  ): IO[QueryFailure, QueryResponses] =
    for {
      baselineObserved <- ZIO.succeed(serve(baselineApis, query.input))
      readyObserved    <- ZIO.succeed(serve(readyApis, query.input))
      _ <- ZIO.succeed {
        assert(baselineObserved.status == Status.Ok, s"QP25 baseline route must serve 200 for ${query.label}, got ${baselineObserved.status}")
        assert(readyObserved.status == Status.Ok, s"QP25 ready route must serve 200 for ${query.label}, got ${readyObserved.status}")
      }
      baseline <- decodeObservedZio(baselineObserved, query.label, "baseline")
      ready    <- decodeObservedZio(readyObserved, query.label, "ready")
    } yield QueryResponses(query, baseline, ready)

  private def assertReadyNoAppend(responses: QueryResponses): Unit = {
    assert(responses.query == noAppendProbe)
    assert(responses.ready.executionMode == BeautySearchExecutionMode.EsPlusQdrantSupplement)
    assert(responses.ready.qdrantSupplement == QdrantSupplementSummary.usedNoAppend(QdrantSupplementPolicyName.ExplicitConstraintsFilterPlusTop1))
    assert(responses.ready.variantCarousel.forall(_.resultOrigin == VariantResultOrigin.EsBaseline))
    assertEsOwnedComponentsUnchanged(responses)
    ()
  }

  private def assertReadyAppend(responses: QueryResponses): Unit = {
    assert(responses.query == appendProbe)
    val baselineIds = responses.baseline.variantCarousel.map(_.variantId)
    val readyIds    = responses.ready.variantCarousel.map(_.variantId)
    val appendedIds = readyIds.filterNot(baselineIds.toSet)

    assert(responses.ready.executionMode == BeautySearchExecutionMode.EsPlusQdrantSupplement)
    assert(responses.ready.qdrantSupplement.status == QdrantSupplementStatus.UsedWithAppend)
    assert(responses.ready.qdrantSupplement.policy == QdrantSupplementPolicyName.ExplicitConstraintsFilterPlusTop1)
    assert(responses.ready.qdrantSupplement.contribution == QdrantSupplementContribution.QdrantOnlyVariantAppend)
    assert(responses.ready.qdrantSupplement.appendedVariantIds == appendedIds)
    assert(appendedIds.size == 1, s"QP25 append probe must append exactly one Qdrant-only id, got $appendedIds")
    assert(appendedIds.forall(id => !baselineIds.contains(id)))
    assert(readyIds.take(baselineIds.size) == baselineIds, s"QP25 ES baseline prefix/order changed: baseline=$baselineIds ready=$readyIds")

    val qdrantVariants = responses.ready.variantCarousel.filter(_.resultOrigin == VariantResultOrigin.QdrantSupplement)
    assert(qdrantVariants.map(_.variantId) == appendedIds)
    assert(responses.ready.variantCarousel.filter(variant => baselineIds.contains(variant.variantId)).forall(_.resultOrigin == VariantResultOrigin.EsBaseline))
    assertEsOwnedComponentsUnchanged(responses)
    ()
  }

  private def assertEsOwnedComponentsUnchanged(responses: QueryResponses): Unit = {
    assert(responses.ready.providerCarousel == responses.baseline.providerCarousel, s"QP25 providerCarousel changed for ${responses.query.label}")
    assert(responses.ready.serviceIntentCarousel == responses.baseline.serviceIntentCarousel, s"QP25 serviceIntentCarousel changed for ${responses.query.label}")
    assert(responses.ready.facets == responses.baseline.facets, s"QP25 facets changed for ${responses.query.label}")
    assert(responses.ready.inferredFilters == responses.baseline.inferredFilters, s"QP25 inferredFilters changed for ${responses.query.label}")
    ()
  }

  private def defaultModule: ModuleDef =
    BeautySearchQdrantSupplementActivation.moduleFor(BeautySearchQdrantSupplementActivation.EsOnlyRollback)

  private def notReadyModule: ModuleDef =
    BeautySearchQdrantSupplementActivation.moduleFor(BeautySearchQdrantSupplementActivation.QdrantSupplementNotReady)

  private def readyModule: ModuleDef =
    BeautySearchQdrantSupplementActivation.moduleFor(BeautySearchQdrantSupplementActivation.QdrantSupplementReady)

  private def moduleWithTestSpec(selectedModule: ModuleDef, testSpec: BeautySearchSpec): Module =
    selectedModule.overriddenBy(new ModuleDef {
      make[BeautySearchSpec].fromValue(testSpec)
    })

  private def esOnlyApis(
    selectedModule: Module,
    esClient: ElasticsearchJsonClient,
  ): Set[HttpApi[IO]] = {
    val module = new ModuleDef {
      include(selectedModule)
      make[Async[Task]].fromValue(Async[Task])
      make[ApisProbe].from {
        (beautySearchApi: BeautySearchApi[IO], allHttpApis: Set[HttpApi[IO]]) =>
          val _ = beautySearchApi
          ApisProbe(allHttpApis)
      }
    }.overriddenBy(new ModuleDef {
      make[ElasticsearchJsonClient].fromValue(esClient)
    })

    apisFrom(module)
  }

  private def runtimeBoundApis(
    selectedModule: Module,
    leaves: RealLeaves,
    vectorSearchSpec: VectorSearchSpec,
  ): Set[HttpApi[IO]] = {
    val module = new ModuleDef {
      include(selectedModule)
      include(BeautySearchQdrantSupplementRuntimeBindingModules.supplementRuntimeBindings(vectorSearchSpec))
      make[Async[Task]].fromValue(Async[Task])
      make[ReadyGraphProbe].from {
        (beautySearchApi: BeautySearchApi[IO], allHttpApis: Set[HttpApi[IO]]) =>
          val _ = beautySearchApi
          ReadyGraphProbe(allHttpApis)
      }
    }.overriddenBy(new ModuleDef {
      make[ElasticsearchJsonClient].fromValue(leaves.esClient)
      make[EmbeddingClient].fromValue(leaves.embeddingClient)
      make[QdrantSearchClient].fromValue(leaves.qdrantSearchClient)
      make[BeautySearchReadyCatalogDocuments].fromValue(leaves.readyCatalog)
    })

    val locator = Injector().produce(
      bindings = module,
      roots = Roots.target[ReadyGraphProbe],
      activation = Activation.empty,
      locatorPrivacy = LocatorPrivacy.PublicByDefault,
    ).unsafeGet()

    locator.get[ReadyGraphProbe].allHttpApis
  }

  private def managedLocalLauncherApis(
    leaves: RealLeaves,
    vectorSearchSpec: VectorSearchSpec,
  ): Set[HttpApi[IO]] = {
    val module = new ModuleDef {
      include(moduleWithTestSpec(BeautySearchLocalQdrantSupplementLauncherModule.managedLocalDefault, baseSpec.copy(vectorSearchSpec = Some(vectorSearchSpec))))
      make[Async[Task]].fromValue(Async[Task])
      make[ReadyGraphProbe].from {
        (beautySearchApi: BeautySearchApi[IO], allHttpApis: Set[HttpApi[IO]]) =>
          val _ = beautySearchApi
          ReadyGraphProbe(allHttpApis)
      }
    }.overriddenBy(new ModuleDef {
      make[ElasticsearchJsonClient].fromValue(leaves.esClient)
      make[EmbeddingClient].fromValue(leaves.embeddingClient)
      make[QdrantSearchClient].fromValue(leaves.qdrantSearchClient)
      make[BeautySearchReadyCatalogDocuments].fromValue(leaves.readyCatalog)
    })

    val locator = Injector().produce(
      bindings = module,
      roots = Roots.target[ReadyGraphProbe],
      activation = Activation(Scene -> Scene.Managed),
      locatorPrivacy = LocatorPrivacy.PublicByDefault,
    ).unsafeGet()

    locator.get[ReadyGraphProbe].allHttpApis
  }

  private def apisFrom(module: Module): Set[HttpApi[IO]] = {
    val locator = Injector().produce(
      bindings = module,
      roots = Roots.target[ApisProbe],
      activation = Activation.empty,
      locatorPrivacy = LocatorPrivacy.PublicByDefault,
    ).unsafeGet()

    locator.get[ApisProbe].allHttpApis
  }

  private def prepareEsIndexWith(
    testSpec: BeautySearchSpec,
    client: ElasticsearchTestClient,
    documents: List[VariantSearchDocument],
  ): IO[QueryFailure, Unit] =
    for {
      _ <- client.putJson(s"/${testSpec.variantDocument.indexName}", ElasticsearchMappingInterpreter.mapping(testSpec))
      _ <- client.postNdjson(
             s"/${testSpec.variantDocument.indexName}/_bulk",
             ElasticsearchIngestionInterpreter.bulkPayload(testSpec, documents),
           )
      _ <- client.post(s"/${testSpec.variantDocument.indexName}/_refresh")
    } yield ()

  private def serve(apis: Set[HttpApi[IO]], input: UserSearchInput): ObservedResponse =
    runIO(observe(combineApis(apis.toSeq*), postJson("/beauty-search", input.asJson.noSpaces)))

  private def decodeObserved(
    observed: ObservedResponse,
    queryLabel: String,
    modeLabel: String,
  ): BeautySearchResponse =
    decode[BeautySearchResponse](observed.body) match {
      case Right(value) => value
      case Left(error)  => fail(s"QP25 response JSON must decode: query=$queryLabel mode=$modeLabel error=${error.getMessage} body=${observed.body}")
    }

  private def decodeObservedZio(
    observed: ObservedResponse,
    queryLabel: String,
    modeLabel: String,
  ): IO[QueryFailure, BeautySearchResponse] =
    ZIO.fromEither(
      decode[BeautySearchResponse](observed.body).left.map(error =>
        QueryFailure.operation("decode-qp25-route-response", s"query=$queryLabel mode=$modeLabel error=${error.getMessage} body=${observed.body}")
      )
    )

  private def assertJsonStringField(response: ObservedResponse, fieldName: String, expected: String): Unit =
    io.circe.parser.parse(response.body) match {
      case Right(json) =>
        val actual = json.hcursor.downField(fieldName).as[String]
        assert(actual == Right(expected), s"expected JSON field $fieldName=$expected, got $actual in ${response.body}")
        ()
      case Left(error) =>
        fail(s"invalid JSON: ${error.getMessage}; body: ${response.body}")
    }

  private val esVariantId: MasterServiceOfferVariantId = variantId(1)

  private def duplicateOnlyLeaves: RealLeaves = {
    val catalogDocuments = List(document(esVariantId))
    RealLeaves(
      esClient = singleEsHitClient(esHitDocument(esVariantId)),
      embeddingClient = new StubEmbeddingClient(Vector(0.1d, 0.2d, 0.3d, 0.4d)),
      qdrantSearchClient = new StubQdrantSearchClient(List(esVariantId)),
      readyCatalog = BeautySearchReadyCatalogDocuments("qp25-duplicate-only-catalog", catalogDocuments),
    )
  }

  private def qdrantOnlyAppendLeaves: RealLeaves = {
    val qdrantOnlyId = variantId(2)
    val catalogDocuments = List(document(esVariantId), document(qdrantOnlyId))
    RealLeaves(
      esClient = singleEsHitClient(esHitDocument(esVariantId)),
      embeddingClient = new StubEmbeddingClient(Vector(0.1d, 0.2d, 0.3d, 0.4d)),
      qdrantSearchClient = new StubQdrantSearchClient(List(qdrantOnlyId)),
      readyCatalog = BeautySearchReadyCatalogDocuments("qp25-qdrant-only-append-catalog", catalogDocuments),
    )
  }

  private def failIfCalledLeaves: RealLeaves =
    RealLeaves(
      esClient = failIfCalledEsClient,
      embeddingClient = new FailIfCalledEmbeddingClient,
      qdrantSearchClient = new FailIfCalledQdrantSearchClient,
      readyCatalog = BeautySearchReadyCatalogDocuments("qp25-fail-if-called-catalog", List(document(esVariantId))),
    )

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

  private def esHitDocument(id: MasterServiceOfferVariantId): VariantSearchDocument =
    document(id).copy(
      masterServiceOfferId = UUID.fromString("11000000-0000-0000-0000-000000000000"),
      masterLocationId = UUID.fromString("21000000-0000-0000-0000-000000000000"),
      masterId = UUID.fromString("31000000-0000-0000-0000-000000000000"),
      serviceId = UUID.fromString("41000000-0000-0000-0000-000000000000"),
      categoryId = UUID.fromString("51000000-0000-0000-0000-000000000000"),
      masterName = "Beauty Master",
      locationName = "Central Studio",
      address = "Main street 1",
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

  private final class StubEmbeddingClient(vector: Vector[Double]) extends EmbeddingClient {
    override def embed(text: String): IO[QueryFailure, Vector[Double]] = ZIO.succeed(vector)
  }

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
      ZIO.dieMessage("QP25: embedding client must not be invoked when not-ready rejects")
  }

  private final class FailIfCalledQdrantSearchClient extends QdrantSearchClient {
    override def search(path: String, json: Json): IO[QueryFailure, List[QdrantSearchHit]] =
      ZIO.dieMessage("QP25: Qdrant search client must not be invoked when not-ready rejects")
  }

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

  private val failIfCalledEsClient: ElasticsearchJsonClient = new ElasticsearchJsonClient {
    override def putJson(path: String, json: Json): IO[QueryFailure, Json] =
      ZIO.dieMessage(s"QP25: ES client must not be invoked when not-ready rejects: putJson($path)")
    override def post(path: String): IO[QueryFailure, Json] =
      ZIO.dieMessage(s"QP25: ES client must not be invoked when not-ready rejects: post($path)")
    override def postJson(path: String, json: Json): IO[QueryFailure, Json] =
      ZIO.dieMessage(s"QP25: ES client must not be invoked when not-ready rejects: postJson($path)")
    override def postNdjson(path: String, payload: String): IO[QueryFailure, Json] =
      ZIO.dieMessage(s"QP25: ES client must not be invoked when not-ready rejects: postNdjson($path)")
    override def getJson(path: String): IO[QueryFailure, Json] =
      ZIO.dieMessage(s"QP25: ES client must not be invoked when not-ready rejects: getJson($path)")
    override def delete(path: String): IO[QueryFailure, Unit] =
      ZIO.dieMessage(s"QP25: ES client must not be invoked when not-ready rejects: delete($path)")
  }

  private final class ElasticsearchJsonClientAdapter(delegate: ElasticsearchTestClient) extends ElasticsearchJsonClient {
    override def putJson(path: String, json: Json): IO[QueryFailure, Json] =
      delegate.putJson(path, json)
    override def post(path: String): IO[QueryFailure, Json] =
      delegate.post(path)
    override def postJson(path: String, json: Json): IO[QueryFailure, Json] =
      delegate.postJson(path, json)
    override def postNdjson(path: String, payload: String): IO[QueryFailure, Json] =
      delegate.postNdjson(path, payload)
    override def getJson(path: String): IO[QueryFailure, Json] =
      delegate.getJson(path)
    override def delete(path: String): IO[QueryFailure, Unit] =
      ZIO.dieMessage(s"QP25: route-bound ES adapter must not delete indexes via JSON client path: delete($path)")
  }

  private def runIO[E, A](effect: ZIO[Any, E, A]): A =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
    }
}
