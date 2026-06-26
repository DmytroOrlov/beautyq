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
  BeautySearchQdrantSupplementActivationConfig,
  BeautySearchQdrantSupplementActivationLauncherSeam,
  BeautySearchQdrantSupplementActivationPreflightCommand,
  BeautySearchQdrantSupplementRuntimeBindingModules,
}
import leaderboard.search.document.{BeautySearchCatalogSnapshot, BeautySearchReadyCatalogDocuments, VariantSearchDocument, VariantSearchDocumentBuilder}
import leaderboard.search.dsl.{BeautySearchSpec, BeautySearchSpecV1, EmbeddingSpec, VectorDistance, VectorSearchSpec}
import leaderboard.search.elasticsearch.ElasticsearchJsonClient
import leaderboard.search.elasticsearch.{ElasticsearchIngestionInterpreter, ElasticsearchMappingInterpreter}
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

final class QP19QdrantSupplementMeasuredAcceptanceGateSpec
    extends LeaderboardTest
    with ProdTest
    with HttpContractTestSupport {

  override def config = super.config.copy(
    activation = super.config.activation ++ Activation(Mode -> Mode.Test),
    memoizationRoots = super.config.memoizationRoots + DIKey[ElasticsearchPortCfg] + DIKey[QdrantPortCfg],
  )

  private val QuerySetLimitedMarker        = "QP22_QUERY_SET_WIDENED_TO_CANONICAL_QBROAD_SOURCE"
  private val ResourceGatedMarker          = "QP19_RESOURCE_GATED"
  private val NoImprovementMarker          = "QP19_NO_IMPROVEMENT_SIGNAL"
  private val WorseningMarker              = "QP19_WORSENING_DETECTED"
  private val ControlBoundaryMarker        = "QP19_CONTROL_BOUNDARY_FAILED"
  private val LostEsIdsMarker              = "QP19_LOST_ES_IDS_DETECTED"
  private val DuplicateEsIdsMarker         = "QP19_DUPLICATE_ES_IDS_DETECTED"
  private val PrefixOrderRegressionMarker  = "QP19_PREFIX_ORDER_REGRESSION_DETECTED"
  private val EsOwnedComponentChangeMarker = "QP19_ES_OWNED_COMPONENT_CHANGED"
  private val AppendBudgetViolationMarker  = "QP19_APPEND_BUDGET_VIOLATION_DETECTED"
  private val BaselineDriftMarker          = "QP23_WIDENED_BASELINE_DRIFT"

  // QP23: locks the QP22 widened measured outcome as the reusable BeautyQ acceptance baseline.
  // Any deviation from these exact counts on the source-confirmed 4-query set is reported via
  // BaselineDriftMarker rather than silently passing under the looser `>=`/`==0` gate conditions.
  private val ExpectedWidenedBaseline = AcceptanceMetrics(
    testedQueries = 4,
    improvedQueries = 1,
    unchangedQueries = 3,
    worsenedQueries = 0,
    totalQdrantOnlyAppends = 1,
    duplicateEsIds = 0,
    lostEsIds = 0,
    prefixOrderRegressions = 0,
    esOwnedComponentChanges = 0,
    appendBudgetViolations = 0,
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

  // QP22 widening candidates: same canonical 63-query BeautyQ eval inventory family as
  // q_broad_006 (bifunctor-tagless/src/test/resources/leaderboard/search/eval/beautyq_search_eval_queries_v1.json),
  // not invented text.
  private val broadSalonNailsQuery = QueryCase(
    label = "q_broad_001_widened_probe",
    input = UserSearchInput(
      query = "салон красоты wandsbek ногти",
      userLat = None,
      userLon = None,
      limit = 10,
    ),
  )

  private val broadFaceNearbyQuery = QueryCase(
    label = "q_broad_003_widened_probe",
    input = UserSearchInput(
      query = "что-то для лица рядом",
      userLat = None,
      userLon = None,
      limit = 10,
    ),
  )

  private val queryCases = List(broadComplementProbeQuery, manicureProbeQuery, broadSalonNailsQuery, broadFaceNearbyQuery)

  private val controlVectorSearchSpec: VectorSearchSpec =
    VectorSearchSpec(
      collectionName = "qp19_control_collection",
      vectorName = "qp19-control-vector",
      topK = 10,
      scoreThreshold = None,
    )

  "QP19 measured local acceptance gate for the no-worsening Qdrant supplement path" should {
    "measure the source-confirmed local real-resource route harness and enforce no worsening" in {
      (esPortCfg: ElasticsearchPortCfg, qdrantPortCfg: QdrantPortCfg) =>
        val esClient          = new ElasticsearchTestClient(esPortCfg.host, esPortCfg.port)
        val qdrantClient      = new QdrantClient(qdrantPortCfg.host, qdrantPortCfg.port)
        val embeddingEndpoint = sys.env.getOrElse("M18_QDRANT_EMBEDDING_ENDPOINT", "http://localhost:8081")
        val embeddingClient   = new LlamaCppEmbeddingClient(LlamaCppEmbeddingClientConfig(baseUrl = embeddingEndpoint))

        val resourceProbe = runIO(
          for {
            esResult        <- esClient.getJson("/").either
            embeddingResult <- embeddingClient.embed("qp19 measured local acceptance probe").either
            qdrantResult    <- qdrantClient.collectionInfo("/collections").either
          } yield (esResult, embeddingResult, qdrantResult)
        )

        resourceProbe match {
          case (Right(_), Right(vector), Right(_)) if vector.nonEmpty =>
            val outcome = runIO(runMeasuredGate(esClient, qdrantClient, embeddingClient, vector.length))
            assertAcceptanceGate(outcome)
          case (esResult, embeddingResult, qdrantResult) =>
            cancel(
              s"$ResourceGatedMarker: esReachable=${esResult.isRight}, " +
                s"embeddingReachable=${embeddingResult.exists(_.nonEmpty)}, " +
                s"qdrantReachable=${qdrantResult.isRight}, embeddingEndpoint=$embeddingEndpoint"
            )
        }
    }
  }

  private final case class QueryCase(label: String, input: UserSearchInput)

  private final case class ControlBoundaryResults(
    defaultStatus: Status,
    rollbackStatus: Status,
    notReadyStatus: Status,
    invalidActivationRejected: Boolean,
    defaultRollbackEquivalent: Boolean,
    routeReadyStatuses: List[(String, Status)],
    routeMatchesService: Boolean,
  ) {
    def render: String =
      s"defaultStatus=$defaultStatus rollbackStatus=$rollbackStatus notReadyStatus=$notReadyStatus " +
        s"invalidActivationRejected=$invalidActivationRejected defaultRollbackEquivalent=$defaultRollbackEquivalent " +
        s"routeReadyStatuses=${routeReadyStatuses.map { case (label, status) => s"$label:$status" }.mkString("[", ",", "]")} " +
        s"routeMatchesService=$routeMatchesService"
  }

  private final case class QueryMeasurement(
    query: QueryCase,
    baselineVariantIds: List[String],
    supplementVariantIds: List[String],
    appendedQdrantOnlyIds: List[String],
    lostEsIds: Set[String],
    duplicateEsIds: Set[String],
    prefixOrderRegression: Boolean,
    esOwnedComponentChanges: List[String],
    appendBudgetViolation: Boolean,
    improved: Boolean,
    worsenedFields: List[String],
  ) {
    def unchanged: Boolean = !improved && worsenedFields.isEmpty

    def render: String =
      s"query=${query.label} baseline=$baselineVariantIds supplement=$supplementVariantIds appended=$appendedQdrantOnlyIds " +
        s"lostEsIds=${lostEsIds.toList.sorted} duplicateEsIds=${duplicateEsIds.toList.sorted} " +
        s"prefixOrderRegression=$prefixOrderRegression esOwnedComponentChanges=$esOwnedComponentChanges " +
        s"appendBudgetViolation=$appendBudgetViolation improved=$improved worsenedFields=$worsenedFields"
  }

  private final case class AcceptanceMetrics(
    testedQueries: Int,
    improvedQueries: Int,
    unchangedQueries: Int,
    worsenedQueries: Int,
    totalQdrantOnlyAppends: Int,
    duplicateEsIds: Int,
    lostEsIds: Int,
    prefixOrderRegressions: Int,
    esOwnedComponentChanges: Int,
    appendBudgetViolations: Int,
  ) {
    def render: String =
      s"testedQueries=$testedQueries improvedQueries=$improvedQueries unchangedQueries=$unchangedQueries " +
        s"worsenedQueries=$worsenedQueries totalQdrantOnlyAppends=$totalQdrantOnlyAppends duplicateEsIds=$duplicateEsIds " +
        s"lostEsIds=$lostEsIds prefixOrderRegressions=$prefixOrderRegressions " +
        s"esOwnedComponentChanges=$esOwnedComponentChanges appendBudgetViolations=$appendBudgetViolations"
  }

  private final case class MeasuredGateOutcome(
    preflightStatus: String,
    querySetMarker: Option[String],
    controlBoundaries: ControlBoundaryResults,
    measurements: List[QueryMeasurement],
    metrics: AcceptanceMetrics,
  ) {
    def render: String =
      s"preflightStatus=$preflightStatus querySetMarker=${querySetMarker.getOrElse("none")} " +
        s"${metrics.render} control={${controlBoundaries.render}} perQuery=${measurements.map(_.render).mkString("[", " | ", "]")}"
  }

  private final case class QueryResponses(
    query: QueryCase,
    defaultBaseline: BeautySearchResponse,
    rollbackBaseline: BeautySearchResponse,
    readyService: BeautySearchResponse,
    readyRoute: BeautySearchResponse,
  )

  private final case class ReadyGraphProbe(
    beautySearchService: BeautySearchService[IO],
    allHttpApis: Set[HttpApi[IO]],
  )

  private final case class RealLeaves(
    esClient: ElasticsearchJsonClient,
    embeddingClient: EmbeddingClient,
    qdrantSearchClient: QdrantSearchClient,
    readyCatalog: BeautySearchReadyCatalogDocuments,
  )

  private final case class ApisProbe(allHttpApis: Set[HttpApi[IO]])

  private def runMeasuredGate(
    esClient: ElasticsearchTestClient,
    qdrantClient: QdrantClient,
    embeddingClient: LlamaCppEmbeddingClient,
    vectorDimension: Int,
  ): IO[QueryFailure, MeasuredGateOutcome] = {
    val embeddingSpec = EmbeddingSpec[VariantSearchDocument](
      vectorName = "llama-cpp-embedding",
      modelName = "qp19-test-local-gate",
      dimension = vectorDimension,
      distance = VectorDistance.Cosine,
      sourceTextFieldPaths = List("serviceText", "attributeText", "allText", "categoryName"),
    )
    val readinessConfig = QdrantCollectionReadinessConfig.derive(
      QdrantCollectionReadinessInput(
        domainName = "beautyq",
        searchSpecVersion = "v1",
        purpose = s"qp19-test-local-gate-${UUID.randomUUID().toString.replace('-', '_')}",
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
        indexName = s"${baseSpec.variantDocument.indexName}_qp19_${UUID.randomUUID().toString.replace('-', '_')}"
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
    val readyCatalog = BeautySearchReadyCatalogDocuments("qp19-ready-catalog", canonicalDocuments)
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
        defaultApis = esOnlyApis(moduleWithTestSpec(BeautySearchQdrantSupplementActivationLauncherSeam.selectedModuleOrThrow(None), defaultRouteSpec), esJsonClient)
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
        controlBoundaries <- ZIO.succeed(controlBoundaryResults(defaultApis, rollbackApis, readyProbe.allHttpApis))
        queryResponses <- ZIO.foreach(queryCases) { query =>
          for {
            defaultObserved <- ZIO.succeed(serve(defaultApis, query.input))
            rollbackObserved <- ZIO.succeed(serve(rollbackApis, query.input))
            _ <- ZIO.succeed {
              assert(defaultObserved.status == Status.Ok, s"QP19: default ES baseline must serve 200 for ${query.label}, got ${defaultObserved.status}")
              assert(rollbackObserved.status == Status.Ok, s"QP19: rollback ES baseline must serve 200 for ${query.label}, got ${rollbackObserved.status}")
            }
            defaultResponse <- decodeRouteResponse(defaultObserved, query.label, "default")
            rollbackResponse <- decodeRouteResponse(rollbackObserved, query.label, "rollback")
            _ <- ZIO.succeed {
              assert(defaultResponse == rollbackResponse, s"QP19: default and rollback ES baselines must match for ${query.label}")
            }
            readyServiceEither <- readyProbe.beautySearchService.search(query.input).either
            readyServiceResponse <- ZIO.fromEither(
              readyServiceEither.left.map(failure =>
                QueryFailure.operation(
                  "qp19-ready-service-failed",
                  s"QP19_READY_SERVICE_FAILED: query=${query.label} input=${query.input.query} failure=$failure",
                )
              )
            )
            readyObserved <- ZIO.succeed(serve(readyProbe.allHttpApis, query.input))
            _ <- ZIO.succeed {
              assert(
                readyObserved.status == Status.Ok,
                s"QP19_ROUTE_BOUNDARY_FAILED: query=${query.label} routeStatus=${readyObserved.status} routeBody=${readyObserved.body}",
              )
            }
            readyRouteResponse <- decodeRouteResponse(readyObserved, query.label, "ready")
            _ <- ZIO.succeed {
              assertRouteServiceContract(readyRouteResponse, readyServiceResponse, query.label)
            }
          } yield QueryResponses(query, defaultResponse, rollbackResponse, readyServiceResponse, readyRouteResponse)
        }
      } yield {
        val measurements = queryResponses.map(measureQuery)
        val metrics      = aggregateMetrics(measurements)
        MeasuredGateOutcome(
          preflightStatus = preflight.status.label,
          querySetMarker = Some(QuerySetLimitedMarker),
          controlBoundaries = controlBoundaries.copy(
            routeReadyStatuses = queryResponses.map(response => response.query.label -> Status.Ok),
            routeMatchesService = true,
          ),
          measurements = measurements,
          metrics = metrics,
        )
      }
    ).ensuring(qdrantClient.deleteCollection(collectionPath).either.unit)
      .ensuring(esClient.deleteIndex(defaultRouteSpec.variantDocument.indexName).either.unit)
      .ensuring(esClient.deleteIndex(rollbackRouteSpec.variantDocument.indexName).either.unit)
      .ensuring(esClient.deleteIndex(readyRouteSpec.variantDocument.indexName).either.unit)
  }

  private def controlBoundaryResults(
    defaultApis: Set[HttpApi[IO]],
    rollbackApis: Set[HttpApi[IO]],
    readyApis: Set[HttpApi[IO]],
  ): ControlBoundaryResults = {
    val defaultResponse  = serve(defaultApis, broadComplementProbeQuery.input)
    val rollbackResponse = serve(rollbackApis, broadComplementProbeQuery.input)
    val notReadyResponse = serve(
      runtimeBoundApis(
        explicitModule(BeautySearchQdrantSupplementActivationConfig.QdrantSupplementNotReadyOperatorValue),
        controlLeaves,
        controlVectorSearchSpec,
      ),
      broadComplementProbeQuery.input,
    )
    val invalidRejected = try {
      BeautySearchQdrantSupplementActivationLauncherSeam.selectedModuleOrThrow(Some("totally-unrecognized"))
      false
    } catch {
      case _: IllegalArgumentException => true
    }

    ControlBoundaryResults(
      defaultStatus = defaultResponse.status,
      rollbackStatus = rollbackResponse.status,
      notReadyStatus = notReadyResponse.status,
      invalidActivationRejected = invalidRejected,
      defaultRollbackEquivalent = defaultResponse.body == rollbackResponse.body,
      routeReadyStatuses = readyApis.toList.collect { case _: BeautySearchApi[IO] => broadComplementProbeQuery.label -> Status.Ok },
      routeMatchesService = true,
    )
  }

  private def measureQuery(responses: QueryResponses): QueryMeasurement = {
    val baselineIds         = responses.defaultBaseline.variantCarousel.map(_.variantId.toString)
    val baselineIdSet       = baselineIds.toSet
    val supplementIds       = responses.readyRoute.variantCarousel.map(_.variantId.toString)
    val appendedQdrantOnly  = supplementIds.filterNot(baselineIdSet)
    val lostEsIds           = baselineIdSet.diff(supplementIds.toSet)
    val duplicateEsIds      = baselineIdSet.filter(id => supplementIds.count(_ == id) > 1)
    val prefixOrderRegression = supplementIds.take(baselineIds.size) != baselineIds
    val esOwnedComponentChanges =
      List(
        Option.when(responses.readyRoute.providerCarousel != responses.defaultBaseline.providerCarousel)("providerCarousel"),
        Option.when(responses.readyRoute.serviceIntentCarousel != responses.defaultBaseline.serviceIntentCarousel)("serviceIntentCarousel"),
        Option.when(responses.readyRoute.facets != responses.defaultBaseline.facets)("facets"),
        Option.when(responses.readyRoute.inferredFilters != responses.defaultBaseline.inferredFilters)("inferredFilters"),
      ).flatten
    val appendBudgetViolation = appendedQdrantOnly.size > 1
    val worsenedFields =
      List(
        Option.when(prefixOrderRegression)("variantCarousel.prefix"),
        Option.when(lostEsIds.nonEmpty)("variantCarousel.loss"),
        Option.when(duplicateEsIds.nonEmpty)("variantCarousel.duplicate_es_id"),
        Option.when(appendBudgetViolation)("variantCarousel.append_budget"),
      ).flatten ++ esOwnedComponentChanges.map(component => s"esOwned.$component")

    QueryMeasurement(
      query = responses.query,
      baselineVariantIds = baselineIds,
      supplementVariantIds = supplementIds,
      appendedQdrantOnlyIds = appendedQdrantOnly,
      lostEsIds = lostEsIds,
      duplicateEsIds = duplicateEsIds,
      prefixOrderRegression = prefixOrderRegression,
      esOwnedComponentChanges = esOwnedComponentChanges,
      appendBudgetViolation = appendBudgetViolation,
      improved = appendedQdrantOnly.size == 1 && worsenedFields.isEmpty,
      worsenedFields = worsenedFields,
    )
  }

  private def aggregateMetrics(measurements: List[QueryMeasurement]): AcceptanceMetrics =
    AcceptanceMetrics(
      testedQueries = measurements.size,
      improvedQueries = measurements.count(_.improved),
      unchangedQueries = measurements.count(_.unchanged),
      worsenedQueries = measurements.count(_.worsenedFields.nonEmpty),
      totalQdrantOnlyAppends = measurements.flatMap(_.appendedQdrantOnlyIds).toSet.size,
      duplicateEsIds = measurements.flatMap(_.duplicateEsIds).toSet.size,
      lostEsIds = measurements.flatMap(_.lostEsIds).toSet.size,
      prefixOrderRegressions = measurements.count(_.prefixOrderRegression),
      esOwnedComponentChanges = measurements.flatMap(measurement => measurement.esOwnedComponentChanges.map(component => s"${measurement.query.label}:$component")).distinct.size,
      appendBudgetViolations = measurements.count(_.appendBudgetViolation),
    )

  private def assertAcceptanceGate(outcome: MeasuredGateOutcome): Unit = {
    println(s"QP22_MEASURED_GATE_OUTCOME: ${outcome.render}")
    assert(
      outcome.preflightStatus == ReadyToEnable.label,
      s"QP19: real-resource preflight must report ${ReadyToEnable.label}, got ${outcome.render}",
    )
    assert(
      outcome.controlBoundaries.defaultStatus == Status.Ok &&
        outcome.controlBoundaries.rollbackStatus == Status.Ok &&
        outcome.controlBoundaries.notReadyStatus == Status.ServiceUnavailable &&
        outcome.controlBoundaries.invalidActivationRejected &&
        outcome.controlBoundaries.defaultRollbackEquivalent &&
        outcome.controlBoundaries.routeMatchesService,
      s"$ControlBoundaryMarker: default/rollback ES-backed, not-ready 503, or invalid-activation fail-closed control boundary broke. ${outcome.render}",
    )

    if (outcome.metrics.improvedQueries == 0) {
      fail(s"$NoImprovementMarker: no query showed a measured improvement; the supplement added no Qdrant-only candidate without worsening ES. ${outcome.render}")
    }
    if (
      outcome.metrics.worsenedQueries != 0 ||
      outcome.metrics.lostEsIds != 0 ||
      outcome.metrics.duplicateEsIds != 0 ||
      outcome.metrics.prefixOrderRegressions != 0 ||
      outcome.metrics.esOwnedComponentChanges != 0 ||
      outcome.metrics.appendBudgetViolations != 0
    ) {
      fail(s"$WorseningMarker: at least one no-worsening condition was violated; see the specific metric markers below for which one. ${outcome.render}")
    }

    assert(outcome.metrics.testedQueries > 2, s"QP19_ACCEPTANCE_GATE_FAILED: testedQueries must be > 2, got ${outcome.render}")
    assert(outcome.metrics.improvedQueries >= 1, s"QP19_ACCEPTANCE_GATE_FAILED: improvedQueries must be >= 1, got ${outcome.render}")
    assert(outcome.metrics.worsenedQueries == 0, s"QP19_ACCEPTANCE_GATE_FAILED: worsenedQueries must be 0, got ${outcome.render}")
    assert(outcome.metrics.lostEsIds == 0, s"$LostEsIdsMarker: an ES-baseline variant id was missing from the ready-route response. ${outcome.render}")
    assert(outcome.metrics.duplicateEsIds == 0, s"$DuplicateEsIdsMarker: an ES-baseline variant id appeared more than once in the ready-route response. ${outcome.render}")
    assert(outcome.metrics.prefixOrderRegressions == 0, s"$PrefixOrderRegressionMarker: the ready-route response reordered the ES-baseline prefix instead of only appending. ${outcome.render}")
    assert(outcome.metrics.esOwnedComponentChanges == 0, s"$EsOwnedComponentChangeMarker: providerCarousel/serviceIntentCarousel/facets/inferredFilters changed between default and ready, but those fields are ES-owned. ${outcome.render}")
    assert(outcome.metrics.appendBudgetViolations == 0, s"$AppendBudgetViolationMarker: a query appended more than one Qdrant-only candidate. ${outcome.render}")
    assert(
      outcome.measurements.forall(_.appendedQdrantOnlyIds.size <= 1),
      s"$AppendBudgetViolationMarker: each query must append at most one Qdrant-only candidate, got ${outcome.render}",
    )

    assert(
      outcome.metrics == ExpectedWidenedBaseline,
      s"$BaselineDriftMarker: the widened QP22 measured baseline drifted from the locked QP23 expectation. " +
        s"expected=${ExpectedWidenedBaseline.render} actual=${outcome.metrics.render} full=${outcome.render}",
    )
    ()
  }

  private val controlLeaves = RealLeaves(
    esClient = failIfCalledEsClient,
    embeddingClient = new FailIfCalledEmbeddingClient,
    qdrantSearchClient = new FailIfCalledQdrantSearchClient,
    readyCatalog = BeautySearchReadyCatalogDocuments("qp19-control-catalog", canonicalDocuments.take(1)),
  )

  private def explicitModule(operatorValue: String): ModuleDef =
    BeautySearchQdrantSupplementActivationLauncherSeam.selectedModuleOrThrow(Some(operatorValue))

  private def runtimeBoundApis(
    selectedModule: Module,
    leaves: RealLeaves,
    vectorSearchSpec: VectorSearchSpec,
  ): Set[HttpApi[IO]] =
    runtimeBoundProbe(selectedModule, leaves, vectorSearchSpec).allHttpApis

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
    esClient: ElasticsearchJsonClient,
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
      _ <- client.putJson(s"/${testSpec.variantDocument.indexName}", ElasticsearchMappingInterpreter.mapping(testSpec))
      _ <- client.postNdjson(
             s"/${testSpec.variantDocument.indexName}/_bulk",
             ElasticsearchIngestionInterpreter.bulkPayload(testSpec, documents),
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
        .map(error => QueryFailure.operation("decode-qp19-route-response", s"query=$queryLabel mode=$modeLabel error=${error.getMessage} body=${observed.body}"))
    )

  private def assertRouteServiceContract(
    routeResponse: BeautySearchResponse,
    serviceResponse: BeautySearchResponse,
    queryLabel: String,
  ): Unit = {
    val routeVariantIds   = routeResponse.variantCarousel.map(_.variantId.toString)
    val serviceVariantIds = serviceResponse.variantCarousel.map(_.variantId.toString)
    assert(
      routeVariantIds == serviceVariantIds,
      s"QP19_ROUTE_SERVICE_MISMATCH: query=$queryLabel field=variantCarousel.ids routeIds=$routeVariantIds serviceIds=$serviceVariantIds",
    )
    assert(
      routeResponse.variantCarousel.map(_.resultOrigin) == serviceResponse.variantCarousel.map(_.resultOrigin),
      s"QP19_ROUTE_SERVICE_MISMATCH: query=$queryLabel field=variantCarousel.resultOrigin " +
        s"route=${routeResponse.variantCarousel.map(_.resultOrigin)} service=${serviceResponse.variantCarousel.map(_.resultOrigin)}",
    )
    assert(
      routeResponse.executionMode == serviceResponse.executionMode,
      s"QP19_ROUTE_SERVICE_MISMATCH: query=$queryLabel field=executionMode route=${routeResponse.executionMode} service=${serviceResponse.executionMode}",
    )
    assert(
      routeResponse.qdrantSupplement == serviceResponse.qdrantSupplement,
      s"QP19_ROUTE_SERVICE_MISMATCH: query=$queryLabel field=qdrantSupplement route=${routeResponse.qdrantSupplement} service=${serviceResponse.qdrantSupplement}",
    )
    assert(
      routeResponse.providerCarousel == serviceResponse.providerCarousel,
      s"QP19_ROUTE_SERVICE_MISMATCH: query=$queryLabel field=providerCarousel route=${routeResponse.providerCarousel} service=${serviceResponse.providerCarousel}",
    )
    assert(
      routeResponse.serviceIntentCarousel == serviceResponse.serviceIntentCarousel,
      s"QP19_ROUTE_SERVICE_MISMATCH: query=$queryLabel field=serviceIntentCarousel route=${routeResponse.serviceIntentCarousel} service=${serviceResponse.serviceIntentCarousel}",
    )
    assert(
      routeResponse.facets == serviceResponse.facets,
      s"QP19_ROUTE_SERVICE_MISMATCH: query=$queryLabel field=facets route=${routeResponse.facets} service=${serviceResponse.facets}",
    )
    assert(
      routeResponse.inferredFilters == serviceResponse.inferredFilters,
      s"QP19_ROUTE_SERVICE_MISMATCH: query=$queryLabel field=inferredFilters route=${routeResponse.inferredFilters} service=${serviceResponse.inferredFilters}",
    )
    ()
  }

  private val failIfCalledEsClient: ElasticsearchJsonClient = new ElasticsearchJsonClient {
    override def putJson(path: String, json: io.circe.Json): IO[QueryFailure, io.circe.Json] =
      ZIO.dieMessage(s"QP19: ES client must not be invoked when the not-ready gate rejects: putJson($path)")
    override def post(path: String): IO[QueryFailure, io.circe.Json] =
      ZIO.dieMessage(s"QP19: ES client must not be invoked when the not-ready gate rejects: post($path)")
    override def postJson(path: String, json: io.circe.Json): IO[QueryFailure, io.circe.Json] =
      ZIO.dieMessage(s"QP19: ES client must not be invoked when the not-ready gate rejects: postJson($path)")
    override def postNdjson(path: String, payload: String): IO[QueryFailure, io.circe.Json] =
      ZIO.dieMessage(s"QP19: ES client must not be invoked when the not-ready gate rejects: postNdjson($path)")
    override def getJson(path: String): IO[QueryFailure, io.circe.Json] =
      ZIO.dieMessage(s"QP19: ES client must not be invoked when the not-ready gate rejects: getJson($path)")
    override def delete(path: String): IO[QueryFailure, Unit] =
      ZIO.dieMessage(s"QP19: ES client must not be invoked when the not-ready gate rejects: delete($path)")
  }

  private final class FailIfCalledEmbeddingClient extends EmbeddingClient {
    override def embed(text: String): IO[QueryFailure, Vector[Double]] =
      ZIO.dieMessage("QP19: embedding client must not be invoked when the not-ready gate rejects")
  }

  private final class FailIfCalledQdrantSearchClient extends QdrantSearchClient {
    override def search(path: String, json: io.circe.Json): IO[QueryFailure, List[QdrantSearchHit]] =
      ZIO.dieMessage("QP19: Qdrant search client must not be invoked when the not-ready gate rejects")
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
      ZIO.dieMessage(s"QP19: route-bound ES adapter must not delete indexes via getJson client path: delete($path)")
  }

  private def runIO[E, A](effect: ZIO[Any, E, A]): A =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
    }
}
