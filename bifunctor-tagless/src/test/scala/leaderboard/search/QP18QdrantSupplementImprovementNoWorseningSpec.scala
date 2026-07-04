package leaderboard.search

import cats.effect.Async
import io.circe.parser.decode
import io.circe.syntax.*
import distage.{DIKey, Injector, Mode, Module, ModuleDef}
import izumi.distage.model.definition.{Activation, LocatorPrivacy}
import izumi.distage.model.plan.Roots
import leaderboard.api.{BeautySearchApi, HttpApi}
import leaderboard.config.{ElasticsearchPortCfg, QdrantPortCfg}
import leaderboard.model.QueryFailure
import leaderboard.plugins.BeautySearchQdrantSupplementActivationPreflightStatus.ReadyToEnable
import leaderboard.plugins.{
  BeautySearchQdrantSupplementActivation,
  BeautySearchQdrantSupplementActivationConfig,
  BeautySearchQdrantSupplementActivationModuleSelector,
  BeautySearchQdrantSupplementActivationPreflightCommand,
  BeautySearchQdrantSupplementRuntimeBindingModules,
}
import leaderboard.search.document.{BeautySearchCatalogSnapshot, BeautySearchReadyCatalogDocuments, VariantSearchDocument, VariantSearchDocumentBuilder}
import leaderboard.search.dsl.{BeautySearchSpec, BeautySearchSpecV1, EmbeddingSpec, VectorDistance, VectorSearchSpec}
import leaderboard.search.elasticsearch.ElasticsearchJsonClient
import leaderboard.search.elasticsearch.BeautyQElasticsearchInterpreterAdapter
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
import leaderboard.search.document.InMemoryVariantSearchDocumentSnapshotProvider
import leaderboard.{HttpContractTestSupport, LeaderboardTest, ObservedResponse, ProdTest}
import org.http4s.Status
import zio.interop.catz.*
import zio.{IO, Runtime, Task, Unsafe, ZIO}

import java.util.UUID

/**
 * QP18: narrow local/test proof for the no-worsening Qdrant supplement path.
 *
 * Proves over the existing source-confirmed local HTTP harness:
 *   - ES-only baseline can be served through explicit rollback selection;
 *   - ready mode plus the QP13 runtime binding module serves through the same
 *     real local route path and preserves ES-owned response components;
 *   - at least one tested query improves by appending exactly one Qdrant-only variant id;
 *   - no tested query loses or reorders ES variants, duplicates an ES id, or appends more than one
 *     Qdrant-only candidate;
 *   - not-ready remains 503 with no fallback, and invalid activation still fails closed.
 *
 * This is not production rollout, not Qdrant-as-default, not fallback, not score fusion, and not a
 * route JSON/API change.
 */
final class QP18QdrantSupplementImprovementNoWorseningSpec
    extends LeaderboardTest
    with ProdTest
    with HttpContractTestSupport {

  override def config = super.config.copy(
    activation = super.config.activation ++ Activation(Mode -> Mode.Test),
    memoizationRoots = super.config.memoizationRoots + DIKey[ElasticsearchPortCfg] + DIKey[QdrantPortCfg] + DIKey[LlamaCppEmbeddingClientConfig],
  )

  private val canonicalSeed = new BeautyQSeedLoader.ResourceLoader().load() match {
    case Right(value) => value
    case Left(error)  => throw new RuntimeException(error.message)
  }

  private val canonicalDocuments: List[VariantSearchDocument] =
    VariantSearchDocumentBuilder.build(BeautySearchCatalogSnapshot.fromSeedData(canonicalSeed)) match {
      case Right(value) => value
      case Left(error)  => throw new RuntimeException(error.message)
    }

  private val baseSpec: BeautySearchSpec = BeautySearchSpecV1.spec

  private val broadComplementProbeQuery = QueryCase(
    label = "q_broad_006_ready_append_probe",
    input = UserSearchInput(
      query = "beauty near Wandsbek Markt",
      userLat = None,
      userLon = None,
      limit = 10,
    ),
  )

  private val manicureProbeQuery = QueryCase(
    label = "manicure_real_route_probe",
    input = UserSearchInput(
      query = "маникюр",
      userLat = None,
      userLon = None,
      limit = 10,
    ),
  )

  private val queryCases = List(broadComplementProbeQuery, manicureProbeQuery)

  private val controlVectorSearchSpec: VectorSearchSpec =
    VectorSearchSpec(
      collectionName = "qp18_control_collection",
      vectorName = "qp18-control-vector",
      topK = 10,
      scoreThreshold = None,
    )

  "QP18 control behavior on the local launcher/route harness" should {
    "keep default and rollback ES-backed, keep not-ready at 503 with no fallback, and fail closed on invalid activation" in {
      val defaultResponse  = serve(esOnlyApis(BeautySearchQdrantSupplementActivationModuleSelector.moduleFor(BeautySearchQdrantSupplementActivation.EsOnlyRollback)), broadComplementProbeQuery.input)
      val rollbackResponse = serve(esOnlyApis(explicitModule(BeautySearchQdrantSupplementActivationConfig.EsOnlyRollbackOperatorValue)), broadComplementProbeQuery.input)
      assert(defaultResponse.status == Status.Ok, s"default launcher selection must remain ES-backed (200), got ${defaultResponse.status}")
      assert(rollbackResponse.status == Status.Ok, s"es-only-rollback must remain ES-backed (200), got ${rollbackResponse.status}")

      val notReadyResponse = serve(
        runtimeBoundApis(
          explicitModule(BeautySearchQdrantSupplementActivationConfig.QdrantSupplementNotReadyOperatorValue),
          controlLeaves,
          controlVectorSearchSpec,
        ),
        broadComplementProbeQuery.input,
      )
      assert(notReadyResponse.status == Status.ServiceUnavailable, s"qdrant-supplement-not-ready must reject with 503, got ${notReadyResponse.status}")

      assert(BeautySearchQdrantSupplementActivationConfig.fromOperatorValue(Some("totally-unrecognized")).isLeft)
    }
  }

  "QP18 real-resource improvement/no-worsening route proof" should {
    "use the source-confirmed local route harness to show one append-only improvement without worsening the ES baseline" in {
      (esPortCfg: ElasticsearchPortCfg, qdrantPortCfg: QdrantPortCfg, embeddingConfig: LlamaCppEmbeddingClientConfig) =>
        val esClient          = new ElasticsearchTestClient(esPortCfg.host, esPortCfg.port)
        val qdrantClient      = new QdrantClient(qdrantPortCfg.host, qdrantPortCfg.port)
        val embeddingClient   = new LlamaCppEmbeddingClient(embeddingConfig)

        val resourceProbe = runIO(
          for {
            esResult        <- esClient.getJson("/").either
            embeddingResult <- embeddingClient.embed("qp18 real-resource probe").either
            qdrantResult    <- qdrantClient.collectionInfo("/collections").either
          } yield (esResult, embeddingResult, qdrantResult)
        )

        resourceProbe match {
          case (Right(_), Right(vector), Right(_)) if vector.nonEmpty =>
            val outcome = runIO(runRealRouteProof(esClient, qdrantClient, embeddingClient, vector.length))
            assertRealRouteOutcome(outcome)
          case (esResult, embeddingResult, qdrantResult) =>
            cancel(
                s"QP18_RESOURCE_GATED: esReachable=${esResult.isRight}, " +
                s"embeddingReachable=${embeddingResult.exists(_.nonEmpty)}, " +
                s"qdrantReachable=${qdrantResult.isRight}, embeddingEndpoint=${embeddingConfig.baseUrl}"
            )
        }
    }
  }

  private final case class QueryCase(label: String, input: UserSearchInput)

  private final case class QueryObservation(
    query: QueryCase,
    defaultBaseline: BeautySearchResponse,
    rollbackBaseline: BeautySearchResponse,
    readySupplement: BeautySearchResponse,
  ) {
    val baselineVariantIds: List[String] = defaultBaseline.variantCarousel.map(_.variantId.toString)
    val supplementVariantIds: List[String] = readySupplement.variantCarousel.map(_.variantId.toString)
    val appendedQdrantOnlyIds: List[String] = supplementVariantIds.filterNot(baselineVariantIds.toSet)
  }

  private final case class QP18Outcome(
    preflightStatus: String,
    observations: List[QueryObservation],
  )

  private final case class ReadyGraphProbe(
    beautySearchService: BeautySearchService[IO],
    allHttpApis: Set[HttpApi[IO]],
  )

  private def runRealRouteProof(
    esClient: ElasticsearchTestClient,
    qdrantClient: QdrantClient,
    embeddingClient: LlamaCppEmbeddingClient,
    vectorDimension: Int,
  ): IO[QueryFailure, QP18Outcome] = {
    val embeddingSpec = EmbeddingSpec[VariantSearchDocument](
      vectorName = "llama-cpp-embedding",
      modelName = "qp18-test-local-proof",
      dimension = vectorDimension,
      distance = VectorDistance.Cosine,
      sourceTextFields = List(leaderboard.search.document.BeautyQVariantSearchDocumentContract.Fields.serviceText, leaderboard.search.document.BeautyQVariantSearchDocumentContract.Fields.attributeText, leaderboard.search.document.BeautyQVariantSearchDocumentContract.Fields.allText, leaderboard.search.document.BeautyQVariantSearchDocumentContract.Fields.categoryName),
    )
    val readinessConfig = QdrantCollectionReadinessConfig.derive(
      QdrantCollectionReadinessInput(
        domainName = "beautyq",
        searchSpecVersion = "v1",
        purpose = s"qp18-test-local-proof-${UUID.randomUUID().toString.replace('-', '_')}",
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
        indexName = s"${baseSpec.variantDocument.indexName}_qp18_${UUID.randomUUID().toString.replace('-', '_')}"
      ),
      vectorSearchSpec = Some(readinessConfig.vectorSearchSpec),
    )
    val defaultRouteSpec = routeSpecBase.copy(
      variantDocument = routeSpecBase.variantDocument.copy(indexName = s"${routeSpecBase.variantDocument.indexName}_default")
    )
    val rollbackRouteSpec = routeSpecBase.copy(
      variantDocument = routeSpecBase.variantDocument.copy(indexName = s"${routeSpecBase.variantDocument.indexName}_rollback")
    )
    val readyRouteSpec = routeSpecBase.copy(
      variantDocument = routeSpecBase.variantDocument.copy(indexName = s"${routeSpecBase.variantDocument.indexName}_ready")
    )
    val snapshotProvider = new InMemoryVariantSearchDocumentSnapshotProvider[IO](canonicalDocuments)
    val compositionFactory = new QdrantEmbeddingBenchmarkDefaultCompositionFactory(qdrantClient)
    val readyCatalog = BeautySearchReadyCatalogDocuments("qp18-ready-catalog", canonicalDocuments)
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
        esJsonClient = new ElasticsearchJsonClientAdapter(esClient)
        defaultApis = esOnlyApis(moduleWithTestSpec(BeautySearchQdrantSupplementActivationModuleSelector.moduleFor(BeautySearchQdrantSupplementActivation.EsOnlyRollback), defaultRouteSpec), esJsonClient)
        rollbackApis = esOnlyApis(moduleWithTestSpec(explicitModule(BeautySearchQdrantSupplementActivationConfig.EsOnlyRollbackOperatorValue), rollbackRouteSpec), esJsonClient)
        readyProbe = runtimeBoundProbe(
          moduleWithTestSpec(explicitModule(BeautySearchQdrantSupplementActivationConfig.QdrantSupplementReadyOperatorValue), readyRouteSpec),
          RealLeaves(
            esClient = esJsonClient,
            embeddingClient = embeddingClient,
            qdrantSearchClient = new QdrantClientSearchAdapter(qdrantClient),
            readyCatalog = readyCatalog,
          ),
          readinessConfig.vectorSearchSpec,
        )
        observations <- ZIO.foreach(queryCases) { query =>
          for {
            defaultObserved  <- ZIO.succeed(serve(defaultApis, query.input))
            rollbackObserved <- ZIO.succeed(serve(rollbackApis, query.input))
            readyServiceEither <- readyProbe.beautySearchService.search(query.input).either
            _ <- ZIO.succeed {
              assert(defaultObserved.status == Status.Ok, s"QP18: default ES baseline must serve 200 for ${query.label}, got ${defaultObserved.status}")
              assert(rollbackObserved.status == Status.Ok, s"QP18: rollback ES baseline must serve 200 for ${query.label}, got ${rollbackObserved.status}")
            }
            defaultResponse  <- decodeRouteResponse(defaultObserved, query.label, "default")
            rollbackResponse <- decodeRouteResponse(rollbackObserved, query.label, "rollback")
            _ <- ZIO.succeed {
              assert(defaultResponse == rollbackResponse, s"QP18: default and rollback ES baselines must match for ${query.label}")
            }
            readyServiceResponse <- ZIO.fromEither(
              readyServiceEither.left.map(failure =>
                QueryFailure.operation(
                  "qp18-ready-service-failed",
                  s"QP18_READY_SERVICE_FAILED: query=${query.label} input=${query.input.query} failure=$failure",
                )
              )
            )
            serviceObservation = QueryObservation(query, defaultResponse, rollbackResponse, readyServiceResponse)
            _ <- ZIO.succeed {
              assertObservationPreservesEs(serviceObservation)
              if (query == broadComplementProbeQuery) {
                assertSingleAppendImprovement(serviceObservation)
              }
            }
            readyObserved <- ZIO.succeed(serve(readyProbe.allHttpApis, query.input))
            _ <- ZIO.succeed {
              assert(
                readyObserved.status == Status.Ok,
                s"QP18_ROUTE_LAYER_BLOCKED_SERVICE_PROOF_GREEN: query=${query.label} routeStatus=${readyObserved.status} routeBody=${readyObserved.body} " +
                  s"serviceAppended=${serviceObservation.appendedQdrantOnlyIds} baseline=${serviceObservation.baselineVariantIds} supplement=${serviceObservation.supplementVariantIds}",
              )
            }
            readyResponse <- decodeRouteResponse(readyObserved, query.label, "ready")
            _ <- ZIO.succeed {
              assertRouteServiceContract(readyResponse, readyServiceResponse, query.label)
            }
          } yield QueryObservation(query, defaultResponse, rollbackResponse, readyResponse)
        }
      } yield QP18Outcome(preflight.status.label, observations)
    ).ensuring(qdrantClient.deleteCollection(collectionPath).either.unit)
      .ensuring(esClient.deleteIndex(defaultRouteSpec.variantDocument.indexName).either.unit)
      .ensuring(esClient.deleteIndex(rollbackRouteSpec.variantDocument.indexName).either.unit)
      .ensuring(esClient.deleteIndex(readyRouteSpec.variantDocument.indexName).either.unit)
  }

  private def assertRealRouteOutcome(outcome: QP18Outcome): Unit = {
    assert(
      outcome.preflightStatus == ReadyToEnable.label,
      s"QP18: real-resource preflight must report ${ReadyToEnable.label}, got ${outcome.preflightStatus}",
    )

    outcome.observations.foreach(assertObservationPreservesEs)

    val improvementSignals = outcome.observations.filter(_.appendedQdrantOnlyIds.size == 1)
    assert(
      improvementSignals.nonEmpty,
      s"QP18_NO_IMPROVEMENT_SIGNAL: queries=${outcome.observations.map(obs => s"${obs.query.label}:${obs.appendedQdrantOnlyIds}").mkString(",")}",
    )
    assert(
      improvementSignals.forall(observation =>
        observation.appendedQdrantOnlyIds.headOption.exists(id => !observation.baselineVariantIds.contains(id))
      ),
      s"QP18: appended improvement ids must be Qdrant-only, got ${improvementSignals.map(_.appendedQdrantOnlyIds)}",
    )
    ()
  }

  private final case class RealLeaves(
    esClient: ElasticsearchJsonClient,
    embeddingClient: EmbeddingClient,
    qdrantSearchClient: QdrantSearchClient,
    readyCatalog: BeautySearchReadyCatalogDocuments,
  )

  private final case class ApisProbe(allHttpApis: Set[HttpApi[IO]])

  private val controlLeaves = RealLeaves(
    esClient = failIfCalledEsClient,
    embeddingClient = new FailIfCalledEmbeddingClient,
    qdrantSearchClient = new FailIfCalledQdrantSearchClient,
    readyCatalog = BeautySearchReadyCatalogDocuments("qp18-control-catalog", canonicalDocuments.take(1)),
  )

  private def explicitModule(operatorValue: String): ModuleDef =
    BeautySearchQdrantSupplementActivationModuleSelector.moduleForOperatorValue(Some(operatorValue)) match {
      case Right(module) => module
      case Left(error)   => fail(s"expected valid activation value '$operatorValue', got ${error.message}")
    }

  private def runtimeBoundApis(
    selectedModule: Module,
    leaves: RealLeaves,
    vectorSearchSpec: VectorSearchSpec,
  ): Set[HttpApi[IO]] = {
    runtimeBoundProbe(selectedModule, leaves, vectorSearchSpec).allHttpApis
  }

  private def runtimeBoundProbe(
    selectedModule: Module,
    leaves: RealLeaves,
    vectorSearchSpec: VectorSearchSpec,
  ): ReadyGraphProbe = {
    val base = new ModuleDef {
      include(selectedModule)
      include(BeautySearchQdrantSupplementRuntimeBindingModules.supplementRuntimeBindings(vectorSearchSpec))
      make[Async[Task]].fromValue(Async[Task])
      make[ReadyGraphProbe].from {
        (beautySearchApi: BeautySearchApi[IO], beautySearchService: BeautySearchService[IO], allHttpApis: Set[HttpApi[IO]]) =>
          val _ = beautySearchApi
          ReadyGraphProbe(beautySearchService, allHttpApis)
      }
    }
    val module = base.overriddenBy(new ModuleDef {
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

    locator.get[ReadyGraphProbe]
  }

  private def esOnlyApis(
    selectedModule: Module,
    esClient: ElasticsearchJsonClient = zeroHitMockEsClient,
  ): Set[HttpApi[IO]] = {
    val base = new ModuleDef {
      include(selectedModule)
      make[Async[Task]].fromValue(Async[Task])
      make[ApisProbe].from {
        (beautySearchApi: BeautySearchApi[IO], allHttpApis: Set[HttpApi[IO]]) =>
          val _ = beautySearchApi
          ApisProbe(allHttpApis)
      }
    }
    val module = base.overriddenBy(new ModuleDef {
      make[ElasticsearchJsonClient].fromValue(esClient)
    })

    apisFrom(module)
  }

  private def moduleWithTestSpec(selectedModule: ModuleDef, testSpec: BeautySearchSpec): Module =
    selectedModule.overriddenBy(new ModuleDef {
      make[BeautySearchSpec].fromValue(testSpec)
    })

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
      _ <- client.putJson(s"/${testSpec.variantDocument.indexName}", BeautyQElasticsearchInterpreterAdapter.mapping(testSpec))
      _ <- client.postNdjson(
             s"/${testSpec.variantDocument.indexName}/_bulk",
             BeautyQElasticsearchInterpreterAdapter.bulkPayload(testSpec, documents),
           )
      _ <- client.post(s"/${testSpec.variantDocument.indexName}/_refresh")
    } yield ()

  private def serve(apis: Set[HttpApi[IO]], input: UserSearchInput): ObservedResponse =
    runIO(observe(combineApis(apis.toSeq*), postJson("/beauty-search", input.asJson.noSpaces)))

  private def decodeRouteResponse(
    observed: ObservedResponse,
    queryLabel: String,
    modeLabel: String,
  ): IO[QueryFailure, BeautySearchResponse] =
    ZIO.fromEither(
      decode[BeautySearchResponse](observed.body)
        .left
        .map(error => QueryFailure.operation("decode-qp18-route-response", s"query=$queryLabel mode=$modeLabel error=${error.getMessage} body=${observed.body}"))
    )

  private def assertObservationPreservesEs(observation: QueryObservation): Unit = {
    val baselineIds   = observation.baselineVariantIds
    val supplementIds = observation.supplementVariantIds
    val appendedIds   = observation.appendedQdrantOnlyIds

    assert(
      supplementIds.take(baselineIds.size) == baselineIds,
      s"QP18_WORSENING_DETECTED: query=${observation.query.label} field=variantCarousel.prefix baseline=$baselineIds supplement=$supplementIds",
    )
    assert(
      baselineIds.forall(id => supplementIds.contains(id)),
      s"QP18_WORSENING_DETECTED: query=${observation.query.label} field=variantCarousel.loss baseline=$baselineIds supplement=$supplementIds",
    )
    assert(
      baselineIds.forall(id => supplementIds.count(_ == id) == 1),
      s"QP18_WORSENING_DETECTED: query=${observation.query.label} field=variantCarousel.duplicate_es_id baseline=$baselineIds supplement=$supplementIds",
    )
    assert(
      appendedIds.size <= 1,
      s"QP18_WORSENING_DETECTED: query=${observation.query.label} field=variantCarousel.append_budget appended=$appendedIds",
    )
    assert(
      observation.readySupplement.providerCarousel == observation.defaultBaseline.providerCarousel,
      s"QP18_WORSENING_DETECTED: query=${observation.query.label} field=providerCarousel",
    )
    assert(
      observation.readySupplement.serviceIntentCarousel == observation.defaultBaseline.serviceIntentCarousel,
      s"QP18_WORSENING_DETECTED: query=${observation.query.label} field=serviceIntentCarousel",
    )
    assert(
      observation.readySupplement.facets == observation.defaultBaseline.facets,
      s"QP18_WORSENING_DETECTED: query=${observation.query.label} field=facets",
    )
    assert(
      observation.readySupplement.inferredFilters == observation.defaultBaseline.inferredFilters,
      s"QP18_WORSENING_DETECTED: query=${observation.query.label} field=inferredFilters",
    )
    ()
  }

  /**
   * QP24: route and direct-service responses come from two independent real-resource search
   * calls, so raw `score`/`bestScore` Double fields on `variantCarousel`/`providerCarousel`/etc.
   * can differ in low-order decimals from backend-native floating-point precision alone. The
   * actual route-vs-service contract is ids/order/components, not bit-identical scores, so this
   * compares the domain contract instead of full case-class equality.
   */
  private def assertRouteServiceContract(
    routeResponse: BeautySearchResponse,
    serviceResponse: BeautySearchResponse,
    queryLabel: String,
  ): Unit = {
    val routeVariantIds   = routeResponse.variantCarousel.map(_.variantId.toString)
    val serviceVariantIds = serviceResponse.variantCarousel.map(_.variantId.toString)
    assert(
      routeVariantIds == serviceVariantIds,
      s"QP18_ROUTE_SERVICE_CONTRACT_MISMATCH: query=$queryLabel field=variantCarousel.ids " +
        s"routeIds=$routeVariantIds serviceIds=$serviceVariantIds",
    )
    assert(
      routeResponse.providerCarousel == serviceResponse.providerCarousel,
      s"QP18_ROUTE_SERVICE_CONTRACT_MISMATCH: query=$queryLabel field=providerCarousel " +
        s"route=${routeResponse.providerCarousel} service=${serviceResponse.providerCarousel}",
    )
    assert(
      routeResponse.serviceIntentCarousel == serviceResponse.serviceIntentCarousel,
      s"QP18_ROUTE_SERVICE_CONTRACT_MISMATCH: query=$queryLabel field=serviceIntentCarousel " +
        s"route=${routeResponse.serviceIntentCarousel} service=${serviceResponse.serviceIntentCarousel}",
    )
    assert(
      routeResponse.facets == serviceResponse.facets,
      s"QP18_ROUTE_SERVICE_CONTRACT_MISMATCH: query=$queryLabel field=facets " +
        s"route=${routeResponse.facets} service=${serviceResponse.facets}",
    )
    assert(
      routeResponse.inferredFilters == serviceResponse.inferredFilters,
      s"QP18_ROUTE_SERVICE_CONTRACT_MISMATCH: query=$queryLabel field=inferredFilters " +
        s"route=${routeResponse.inferredFilters} service=${serviceResponse.inferredFilters}",
    )
    ()
  }

  private def assertSingleAppendImprovement(observation: QueryObservation): Unit = {
    assert(
      observation.appendedQdrantOnlyIds.size == 1,
      s"QP18_NO_IMPROVEMENT_SIGNAL: query=${observation.query.label} appended=${observation.appendedQdrantOnlyIds} baseline=${observation.baselineVariantIds} supplement=${observation.supplementVariantIds}",
    )
    assert(
      observation.appendedQdrantOnlyIds.headOption.exists(id => !observation.baselineVariantIds.contains(id)),
      s"QP18: appended improvement id must be Qdrant-only, got ${observation.appendedQdrantOnlyIds}",
    )
    ()
  }

  private val zeroHitMockEsClient: ElasticsearchJsonClient = new ElasticsearchJsonClient {
    override def putJson(path: String, json: io.circe.Json): IO[QueryFailure, io.circe.Json]  = ZIO.succeed(io.circe.Json.obj())
    override def post(path: String): IO[QueryFailure, io.circe.Json]                            = ZIO.succeed(io.circe.Json.obj())
    override def postJson(path: String, json: io.circe.Json): IO[QueryFailure, io.circe.Json] =
      if (path.contains("_search")) ZIO.succeed(io.circe.Json.obj("hits" -> io.circe.Json.obj("hits" -> io.circe.Json.arr())))
      else ZIO.succeed(io.circe.Json.obj())
    override def postNdjson(path: String, payload: String): IO[QueryFailure, io.circe.Json] = ZIO.succeed(io.circe.Json.obj())
    override def getJson(path: String): IO[QueryFailure, io.circe.Json]                      = ZIO.dieMessage(s"unexpected getJson($path)")
    override def delete(path: String): IO[QueryFailure, Unit]                                 = ZIO.dieMessage(s"unexpected delete($path)")
  }

  private val failIfCalledEsClient: ElasticsearchJsonClient = new ElasticsearchJsonClient {
    override def putJson(path: String, json: io.circe.Json): IO[QueryFailure, io.circe.Json] =
      ZIO.dieMessage(s"QP18: ES client must not be invoked when the not-ready gate rejects: putJson($path)")
    override def post(path: String): IO[QueryFailure, io.circe.Json] =
      ZIO.dieMessage(s"QP18: ES client must not be invoked when the not-ready gate rejects: post($path)")
    override def postJson(path: String, json: io.circe.Json): IO[QueryFailure, io.circe.Json] =
      ZIO.dieMessage(s"QP18: ES client must not be invoked when the not-ready gate rejects: postJson($path)")
    override def postNdjson(path: String, payload: String): IO[QueryFailure, io.circe.Json] =
      ZIO.dieMessage(s"QP18: ES client must not be invoked when the not-ready gate rejects: postNdjson($path)")
    override def getJson(path: String): IO[QueryFailure, io.circe.Json] =
      ZIO.dieMessage(s"QP18: ES client must not be invoked when the not-ready gate rejects: getJson($path)")
    override def delete(path: String): IO[QueryFailure, Unit] =
      ZIO.dieMessage(s"QP18: ES client must not be invoked when the not-ready gate rejects: delete($path)")
  }

  private final class FailIfCalledEmbeddingClient extends EmbeddingClient {
    override def embed(text: String): IO[QueryFailure, Vector[Double]] =
      ZIO.dieMessage("QP18: embedding client must not be invoked when the not-ready gate rejects")
  }

  private final class FailIfCalledQdrantSearchClient extends QdrantSearchClient {
    override def search(path: String, json: io.circe.Json): IO[QueryFailure, List[QdrantSearchHit]] =
      ZIO.dieMessage("QP18: Qdrant search client must not be invoked when the not-ready gate rejects")
  }

  private final class ElasticsearchJsonClientAdapter(delegate: ElasticsearchTestClient) extends ElasticsearchJsonClient {
    override def putJson(path: String, json: io.circe.Json): IO[QueryFailure, io.circe.Json] =
      delegate.putJson(path, json)
    override def post(path: String): IO[QueryFailure, io.circe.Json] =
      delegate.post(path)
    override def postJson(path: String, json: io.circe.Json): IO[QueryFailure, io.circe.Json] =
      delegate.postJson(path, json)
    override def postNdjson(path: String, payload: String): IO[QueryFailure, io.circe.Json] =
      delegate.postNdjson(path, payload)
    override def getJson(path: String): IO[QueryFailure, io.circe.Json] =
      delegate.getJson(path)
    override def delete(path: String): IO[QueryFailure, Unit] =
      ZIO.dieMessage(s"QP18: route-bound ES adapter must not delete indexes via getJson client path: delete($path)")
  }

  private def runIO[E, A](effect: ZIO[Any, E, A]): A =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
    }
}
