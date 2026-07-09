package leaderboard.search

import cats.effect.Async
import distage.{DIKey, Injector, ModuleDef, Scene}
import io.circe.parser.decode
import io.circe.syntax.*
import izumi.distage.model.definition.{Activation, LocatorPrivacy}
import izumi.distage.model.plan.Roots
import leaderboard.api.{BeautySearchApi, HttpApi}
import leaderboard.config.{ElasticsearchPortCfg, QdrantPortCfg}
import leaderboard.model.{MasterId, MasterServiceOfferVariantId, QueryFailure}
import leaderboard.plugins.BeautySearchLocalQdrantSupplementLauncherModule
import leaderboard.repo.{Masters, MasterServiceOfferVariants}
import leaderboard.search.document.{BeautyQSearchCatalogSnapshot, BeautyQVariantSearchDocumentMaterialization, BeautySearchReadyCatalogDocuments, VariantSearchDocument}
import leaderboard.search.dsl.{BeautySearchSpec, BeautySearchSpecV1, VectorSearchSpec}
import leaderboard.search.elasticsearch.ElasticsearchJsonClient
import leaderboard.search.embedding.{EmbeddingClient, LlamaCppEmbeddingClient, LlamaCppEmbeddingClientConfig}
import leaderboard.search.qdrant.{
  QdrantClient,
  QdrantClientCollectionInfoAdapter,
  QdrantClientSearchAdapter,
  QdrantCollectionCompatibilityChecker,
  QdrantJsonInterpreter,
  QdrantSearchClient,
}
import leaderboard.search.startup.{BeautyQManagedLocalSearchBootstrap, BeautyQManagedLocalSearchBootstrapAction, BeautyQManagedLocalSearchBootstrapFingerprint}
import leaderboard.seed.{BeautyQSeedLoader, BeautyQSeedReady}
import leaderboard.{HttpContractTestSupport, LeaderboardTest, ObservedResponse, ProdTest}
import org.http4s.Status
import zio.interop.catz.*
import zio.{IO, Runtime, Task, Unsafe, ZIO}

import java.util.UUID

/**
 * Permanent contract: proof that the local managed launcher path (`./launcher -u scene:managed :leaderboard`,
 * i.e. `Scene.Managed` + `Mode.Prod`) prepares all local data `/beauty-search` needs at startup,
 * with no user-facing activation env flag and no manual Qdrant collection/index step:
 *
 *   - SQL/Postgres: `BeautyQSeedReady` (the managed `LoadAndInsert`) seeds the repositories.
 *   - Elasticsearch baseline: the managed startup bootstrap (re)creates and indexes the seed index.
 *   - Qdrant supplement: the same bootstrap (re)creates the fixed local collection and upserts vectors.
 *   - The constrained supplement route then serves append / no-append provenance over real resources.
 *
 * The bootstrap reuses the production helper `BeautyQManagedLocalSearchBootstrap`, the exact code the
 * managed launcher's `BeautyQManagedLocalSearchDataReady.Bootstrap` resource runs before the HTTP
 * server serves. No fallback, fusion, rerank, or Qdrant-only route is introduced.
 */
final class ManagedLocalSearchBootstrapSpec
    extends LeaderboardTest
    with ProdTest
    with HttpContractTestSupport {

  // No `Activation(Mode -> Mode.Test)`: this stays at the launcher-equivalent `Scene.Managed` + `Mode.Prod`.
  override def config = super.config.copy(
    memoizationRoots = super.config.memoizationRoots + DIKey[ElasticsearchPortCfg] + DIKey[QdrantPortCfg] + DIKey[LlamaCppEmbeddingClientConfig],
  )

  private val baseSpec: BeautySearchSpec = BeautySearchSpecV1.spec
  private val vectorSpec: VectorSearchSpec = BeautySearchLocalQdrantSupplementLauncherModule.VectorSpec

  // Source-confirmed seed records (proof that BeautyQ data is reachable through the repositories).
  private val knownSeedMasterId: MasterId =
    MasterId(UUID.fromString("6bbb7472-d382-541e-a007-c842ebf3c25b"))
  private val knownSeedVariantId: MasterServiceOfferVariantId =
    MasterServiceOfferVariantId(UUID.fromString("1fcd6e17-c6bb-5901-9f63-205668897659"))

  private val canonicalSeed = new BeautyQSeedLoader.ResourceLoader().load() match {
    case Right(value) => value
    case Left(error)  => throw new RuntimeException(error.message)
  }

  private val canonicalMaterializationSnapshot = BeautyQSearchCatalogSnapshot(
    categories                 = canonicalSeed.categories,
    services                   = canonicalSeed.services,
    serviceVariantSchemas      = canonicalSeed.serviceVariantSchemas,
    masters                    = canonicalSeed.masters,
    masterLocations            = canonicalSeed.masterLocations,
    masterServiceOffers        = canonicalSeed.masterServiceOffers,
    masterServiceOfferVariants = canonicalSeed.masterServiceOfferVariants,
  )
  private val canonicalDocuments: List[VariantSearchDocument] =
    BeautyQVariantSearchDocumentMaterialization.project(canonicalMaterializationSnapshot) match {
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

  "QP26 managed local launcher SQL seed startup" should {
    "seed BeautyQ data into the repositories so known seed records are readable" in {
      (masters: Masters[IO], variants: MasterServiceOfferVariants[IO], seedReady: BeautyQSeedReady) =>
        val _ = seedReady
        for {
          master  <- masters.getMaster(knownSeedMasterId)
          variant <- variants.getMasterServiceOfferVariant(knownSeedVariantId)
          _ <- ZIO.succeed {
            assert(master.exists(_.name == "Studio Manana"), s"QP26: known BeautyQ seed master must be readable, got $master")
            assert(variant.isDefined, s"QP26: known BeautyQ seed variant must be readable, got $variant")
          }
        } yield ()
    }
  }

  "QP26 managed local launcher ES + Qdrant startup bootstrap" should {
    "prepare the ES baseline and Qdrant supplement and serve append / no-append provenance" in {
      (esPortCfg: ElasticsearchPortCfg, qdrantPortCfg: QdrantPortCfg, embeddingConfig: LlamaCppEmbeddingClientConfig) =>
        val esClient          = new ElasticsearchTestClient(esPortCfg.host, esPortCfg.port)
        val qdrantClient      = new QdrantClient(qdrantPortCfg.host, qdrantPortCfg.port)
        val embeddingClient   = new LlamaCppEmbeddingClient(embeddingConfig)

        val resourceProbe = runIO(
          for {
            esResult        <- esClient.getJson("/").either
            embeddingResult <- embeddingClient.embed("qp26 managed local bootstrap probe").either
            qdrantResult    <- qdrantClient.collectionInfo("/collections").either
          } yield (esResult, embeddingResult, qdrantResult)
        )

        resourceProbe match {
          case (Right(_), Right(vector), Right(_)) if vector.nonEmpty =>
            runIO(runBootstrapProof(esClient, qdrantClient, embeddingClient, embeddingConfig))
          case (esResult, embeddingResult, qdrantResult) =>
            cancel(
                s"QP26_RESOURCE_GATED: esReachable=${esResult.isRight}, " +
                s"embeddingReachable=${embeddingResult.exists(_.nonEmpty)}, " +
                s"qdrantReachable=${qdrantResult.isRight}, embeddingEndpoint=${embeddingConfig.baseUrl}"
            )
        }
    }
  }

  private def runBootstrapProof(
    esClient: ElasticsearchTestClient,
    qdrantClient: QdrantClient,
    embeddingClient: LlamaCppEmbeddingClient,
    embeddingConfig: LlamaCppEmbeddingClientConfig,
  ): IO[QueryFailure, Unit] = {
    // Unique ES index so the suite never clobbers other real-ES suites; the Qdrant collection is the
    // fixed launcher collection (only this suite touches it, and the bootstrap drops+recreates it).
    val routeSpec = baseSpec.copy(
      variantDocument = baseSpec.variantDocument.copy(
        indexName = s"${baseSpec.variantDocument.indexName}_qp26_${UUID.randomUUID().toString.replace('-', '_')}"
      ),
      vectorSearchSpec = Some(vectorSpec),
    )
    val collectionPath = s"/collections/${vectorSpec.collectionName}"
    val esJsonClient = new ElasticsearchJsonClientAdapter(esClient)
    val catalog = BeautySearchReadyCatalogDocuments("qp26-managed-local-catalog", canonicalDocuments)
    val checker = new QdrantCollectionCompatibilityChecker(new QdrantClientCollectionInfoAdapter(qdrantClient))

    def bootstrap(): IO[QueryFailure, leaderboard.search.startup.BeautyQManagedLocalSearchBootstrapResult] =
      BeautyQManagedLocalSearchBootstrap.run(esJsonClient, qdrantClient, embeddingClient, routeSpec, catalog, vectorSpec, embeddingConfig.baseUrl)

    (
      for {
        firstResult  <- bootstrap()
        // Idempotence: a repeated managed start reuses matching prepared resources without resource_already_exists.
        secondResult <- bootstrap()
        metadataInfo <- qdrantClient.collectionInfo(collectionPath)
        _ <- ZIO.succeed {
          assert(firstResult.qdrantIndexedCount == canonicalDocuments.size, s"QP26: bootstrap must index all seed vectors, got ${firstResult.qdrantIndexedCount}")
          assert(firstResult.esDocumentCount == canonicalDocuments.size, s"QP26: bootstrap must index all ES docs, got ${firstResult.esDocumentCount}")
          assert(firstResult.esAction == BeautyQManagedLocalSearchBootstrapAction.Rebuilt, s"QP31: first ES bootstrap action=${firstResult.esAction}")
          assert(firstResult.qdrantAction == BeautyQManagedLocalSearchBootstrapAction.Rebuilt, s"QP31: first Qdrant bootstrap action=${firstResult.qdrantAction}")
          assert(firstResult.qdrantCollectionName == vectorSpec.collectionName)
          assert(firstResult.vectorDimension == 1024, s"QP26: managed local Qdrant collection must use dimension 1024, got ${firstResult.vectorDimension}")
          assert(secondResult.qdrantIndexedCount == firstResult.qdrantIndexedCount, "QP26: repeated bootstrap must be idempotent")
          assert(secondResult.esAction == BeautyQManagedLocalSearchBootstrapAction.Reused, s"QP31: unchanged ES bootstrap action=${secondResult.esAction}")
          assert(secondResult.qdrantAction == BeautyQManagedLocalSearchBootstrapAction.Reused, s"QP31: unchanged Qdrant bootstrap action=${secondResult.qdrantAction}")
          assert(secondResult.fingerprint == firstResult.fingerprint, "QP31: unchanged bootstrap must keep the same fingerprint")
          assert(
            BeautyQManagedLocalSearchBootstrapFingerprint.decodeCollectionMetadataValue(metadataInfo).contains(firstResult.fingerprint),
            s"QP31: live Qdrant collection metadata must expose managed bootstrap fingerprint, got $metadataInfo",
          )
        }
        // Missing Qdrant collection and missing ES index both make the next managed start rebuild.
        _ <- qdrantClient.deleteCollection(collectionPath).either
        missingQdrantResult <- bootstrap()
        _ <- esClient.deleteIndex(routeSpec.variantDocument.indexName).either
        missingEsResult <- bootstrap()
        _ <- qdrantClient.deleteCollection(collectionPath).either
        incompatibleEmbeddingSpec = BeautyQManagedLocalSearchBootstrap.embeddingSpec(vectorSpec, 512)
        _ <- qdrantClient.createCollection(collectionPath, QdrantJsonInterpreter.createCollectionJson(vectorSpec, incompatibleEmbeddingSpec))
        incompatibleQdrantResult <- bootstrap()
        _ <- ZIO.succeed {
          assert(missingQdrantResult.qdrantAction == BeautyQManagedLocalSearchBootstrapAction.Rebuilt, s"QP31: missing Qdrant collection action=${missingQdrantResult.qdrantAction}")
          assert(missingQdrantResult.esAction == BeautyQManagedLocalSearchBootstrapAction.Rebuilt, s"QP31: missing Qdrant collection should rebuild both resources, esAction=${missingQdrantResult.esAction}")
          assert(missingEsResult.esAction == BeautyQManagedLocalSearchBootstrapAction.Rebuilt, s"QP31: missing ES index action=${missingEsResult.esAction}")
          assert(missingEsResult.qdrantAction == BeautyQManagedLocalSearchBootstrapAction.Rebuilt, s"QP31: missing ES index should rebuild both resources, qdrantAction=${missingEsResult.qdrantAction}")
          assert(incompatibleQdrantResult.qdrantAction == BeautyQManagedLocalSearchBootstrapAction.Rebuilt, s"QP31: incompatible Qdrant collection action=${incompatibleQdrantResult.qdrantAction}")
        }
        // Qdrant collection exists with the expected vector name / dimension / distance.
        compatibility <- checker.check(BeautyQManagedLocalSearchBootstrap.readinessConfig(vectorSpec, 1024).compatibilityExpectation)
        _ <- ZIO.succeed {
          assert(compatibility == Right(()), s"QP26: bootstrapped Qdrant collection must be compatible (name/vector/dim/distance), got $compatibility")
        }
        apis = managedLocalLauncherApis(esJsonClient, embeddingClient, new QdrantClientSearchAdapter(qdrantClient), catalog, routeSpec)
        noAppendObserved = serve(apis, noAppendProbe.input)
        appendObserved   = serve(apis, appendProbe.input)
        _ <- ZIO.succeed {
          assert(noAppendObserved.status == Status.Ok, s"QP26 no-append route must serve 200, got ${noAppendObserved.status}")
          assert(appendObserved.status == Status.Ok, s"QP26 append route must serve 200, got ${appendObserved.status}")
        }
        noAppend <- decodeRouteResponse(noAppendObserved, noAppendProbe.label)
        append   <- decodeRouteResponse(appendObserved, appendProbe.label)
        _ <- ZIO.succeed {
          assertNoAppend(noAppend)
          assertAppend(append)
        }
      } yield ()
    ).ensuring(qdrantClient.deleteCollection(collectionPath).either.unit)
      .ensuring(esClient.deleteIndex(routeSpec.variantDocument.indexName).either.unit)
  }

  private def assertNoAppend(response: BeautySearchResponse): Unit = {
    assert(response.executionMode == BeautySearchExecutionMode.EsPlusQdrantSupplement)
    assert(response.qdrantSupplement.status == QdrantSupplementStatus.UsedNoAppend, s"QP26 no-append status=${response.qdrantSupplement.status}")
    assert(response.qdrantSupplement.contribution == QdrantSupplementContribution.None, s"QP26 no-append contribution=${response.qdrantSupplement.contribution}")
    assert(response.qdrantSupplement.appendedVariantIds.isEmpty, s"QP26 no-append appendedVariantIds=${response.qdrantSupplement.appendedVariantIds}")
    assert(response.variantCarousel.nonEmpty, "QP26 no-append must still have ES baseline results")
    assert(response.variantCarousel.forall(_.resultOrigin == VariantResultOrigin.EsBaseline), "QP26 no-append origins must all be es_baseline")
    ()
  }

  private def assertAppend(response: BeautySearchResponse): Unit = {
    val qdrantVariants = response.variantCarousel.filter(_.resultOrigin == VariantResultOrigin.QdrantSupplement)
    assert(response.executionMode == BeautySearchExecutionMode.EsPlusQdrantSupplement)
    assert(response.qdrantSupplement.status == QdrantSupplementStatus.UsedWithAppend, s"QP26 append status=${response.qdrantSupplement.status}")
    assert(response.qdrantSupplement.contribution == QdrantSupplementContribution.QdrantOnlyVariantAppend, s"QP26 append contribution=${response.qdrantSupplement.contribution}")
    assert(response.qdrantSupplement.appendedVariantIds.size == 1, s"QP26 append must append exactly one Qdrant-only id, got ${response.qdrantSupplement.appendedVariantIds}")
    assert(qdrantVariants.size == 1, s"QP26 append must mark exactly one variant qdrant_supplement, got ${qdrantVariants.size}")
    assert(response.qdrantSupplement.appendedVariantIds == qdrantVariants.map(_.variantId), "QP26 appendedVariantIds must match qdrant_supplement variants")
    val baselineIds = response.variantCarousel.filter(_.resultOrigin == VariantResultOrigin.EsBaseline).map(_.variantId)
    assert(response.variantCarousel.map(_.variantId).take(baselineIds.size) == baselineIds, "QP26 ES baseline prefix/order must be preserved")
    ()
  }

  private final case class QueryCase(label: String, input: UserSearchInput)
  private final case class ReadyGraphProbe(allHttpApis: Set[HttpApi[IO]])

  private def managedLocalLauncherApis(
    esClient: ElasticsearchJsonClient,
    embeddingClient: EmbeddingClient,
    qdrantSearchClient: QdrantSearchClient,
    catalog: BeautySearchReadyCatalogDocuments,
    routeSpec: BeautySearchSpec,
  ): Set[HttpApi[IO]] = {
    val module = new ModuleDef {
      include(BeautySearchLocalQdrantSupplementLauncherModule.managedLocalDefault)
      make[Async[Task]].fromValue(Async[Task])
      make[ReadyGraphProbe].from {
        (beautySearchApi: BeautySearchApi[IO], allHttpApis: Set[HttpApi[IO]]) =>
          val _ = beautySearchApi
          ReadyGraphProbe(allHttpApis)
      }
    }.overriddenBy(new ModuleDef {
      make[BeautySearchSpec].fromValue(routeSpec)
      make[ElasticsearchJsonClient].fromValue(esClient)
      make[EmbeddingClient].fromValue(embeddingClient)
      make[QdrantSearchClient].fromValue(qdrantSearchClient)
      make[BeautySearchReadyCatalogDocuments].fromValue(catalog)
    })

    val locator = Injector().produce(
      bindings = module,
      roots = Roots.target[ReadyGraphProbe],
      activation = Activation(Scene -> Scene.Managed),
      locatorPrivacy = LocatorPrivacy.PublicByDefault,
    ).unsafeGet()

    locator.get[ReadyGraphProbe].allHttpApis
  }

  private def serve(apis: Set[HttpApi[IO]], input: UserSearchInput): ObservedResponse =
    runIO(observe(combineApis(apis.toSeq*), postJson("/beauty-search", input.asJson.noSpaces)))

  private def decodeRouteResponse(observed: ObservedResponse, queryLabel: String): IO[QueryFailure, BeautySearchResponse] =
    ZIO.fromEither(
      decode[BeautySearchResponse](observed.body).left.map(error =>
        QueryFailure.operation("decode-qp26-route-response", s"query=$queryLabel error=${error.getMessage} body=${observed.body}")
      )
    )

  private final class ElasticsearchJsonClientAdapter(delegate: ElasticsearchTestClient) extends ElasticsearchJsonClient {
    override def putJson(path: String, json: io.circe.Json): IO[QueryFailure, io.circe.Json] = delegate.putJson(path, json)
    override def post(path: String): IO[QueryFailure, io.circe.Json]                         = delegate.post(path)
    override def postJson(path: String, json: io.circe.Json): IO[QueryFailure, io.circe.Json] = delegate.postJson(path, json)
    override def postNdjson(path: String, payload: String): IO[QueryFailure, io.circe.Json]   = delegate.postNdjson(path, payload)
    override def getJson(path: String): IO[QueryFailure, io.circe.Json]                       = delegate.getJson(path)
    override def delete(path: String): IO[QueryFailure, Unit]                                  = delegate.deleteIndex(path.stripPrefix("/"))
  }

  private def runIO[E, A](effect: ZIO[Any, E, A]): A =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
    }
}
