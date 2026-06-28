package leaderboard

import cats.effect.Async
import cats.syntax.all.*
import com.comcast.ip4s.{Ipv4Address, Port}
import distage.Lifecycle
import distage.StandardAxis.Repo
import distage.{DIKey, Injector, ModuleDef, Scene}
import fs2.io.net.Network
import io.circe.parser.{decode, parse}
import io.circe.syntax.*
import izumi.distage.model.definition.{Activation, LocatorPrivacy}
import izumi.distage.model.plan.Roots
import izumi.logstage.api.IzLogger
import izumi.logstage.distage.LogIO2Module
import leaderboard.api.{
  BeautySearchApi,
  CategoryApi,
  EsLifecycleStatusApi,
  HttpApi,
  LadderApi,
  MasterApi,
  MasterLocationApi,
  MasterServiceOfferApi,
  MasterServiceOfferVariantApi,
  ProfileApi,
  ServiceApi,
}
import leaderboard.config.{ElasticsearchPortCfg, PostgresCfg, PostgresPortCfg, QdrantPortCfg}
import leaderboard.http.HttpServer
import leaderboard.http.tapir.{
  CategoryTapirEndpoints,
  LadderTapirEndpoints,
  MasterLocationTapirEndpoints,
  MasterServiceOfferTapirEndpoints,
  MasterServiceOfferVariantTapirEndpoints,
  MasterTapirEndpoints,
  ProfileTapirEndpoints,
  ServiceTapirEndpoints,
}
import leaderboard.model.QueryFailure
import leaderboard.plugins.{BeautySearchLocalQdrantSupplementLauncherModule, LeaderboardPlugin}
import leaderboard.search.document.BeautySearchReadyCatalogDocuments
import leaderboard.search.dsl.{BeautySearchSpec, BeautySearchSpecV1}
import leaderboard.search.elasticsearch.ElasticsearchJsonClient
import leaderboard.search.embedding.{EmbeddingClient, LlamaCppEmbeddingClient, LlamaCppEmbeddingClientConfig}
import leaderboard.search.qdrant.{QdrantClient, QdrantSearchClient, QdrantSemanticCandidateBackend, QdrantSemanticCandidateSearch}
import leaderboard.search.semantic.SemanticCandidateBackend
import leaderboard.search.startup.BeautyQManagedLocalSearchDataReady
import leaderboard.search.{
  BeautySearchExecutionMode,
  BeautySearchResponse,
  QdrantSupplementContribution,
  QdrantSupplementStatus,
  UserSearchInput,
  VariantResultOrigin,
}
import leaderboard.seed.BeautyQSeedReady
import leaderboard.services.Ranks
import logstage.LogIO2
import org.http4s.Status
import org.http4s.ember.server.EmberServerBuilder
import zio.interop.catz.*
import zio.{IO, Runtime, Task, Unsafe, ZIO}

import java.util.UUID

/**
 * Permanent managed-launcher route mount smoke. It mirrors the local managed launcher retention shape:
 * readiness markers, launcher role roots, and `Set[HttpApi[IO]]`; it does not root individual API
 * classes to keep weak set members alive.
 */
final class ManagedLauncherHttpRouteMountSpec
    extends LeaderboardTest
    with ProdTest
    with HttpContractTestSupport {

  override def config = super.config.copy(
    memoizationRoots = super.config.memoizationRoots + DIKey[ElasticsearchPortCfg] + DIKey[QdrantPortCfg] + DIKey[LlamaCppEmbeddingClientConfig],
  )

  private val baseSpec: BeautySearchSpec = BeautySearchSpecV1.spec
  private val vectorSpec = BeautySearchLocalQdrantSupplementLauncherModule.VectorSpec

  private val noAppendProbe =
    UserSearchInput("маникюр", None, None, limit = 10)

  private val appendProbe =
    UserSearchInput("beauty near Wandsbek Markt", None, None, limit = 10)

  private val mountedRouteProbes = List(
    MountedRouteProbe("CategoryApi", get("/category/not-a-uuid"), Status.BadRequest),
    MountedRouteProbe("ServiceApi", get("/service/not-a-uuid"), Status.BadRequest),
    MountedRouteProbe("MasterApi", get("/master/not-a-uuid"), Status.BadRequest),
    MountedRouteProbe("MasterLocationApi", get("/master-location/not-a-uuid"), Status.BadRequest),
    MountedRouteProbe("MasterServiceOfferApi", get("/master-service-offer/not-a-uuid"), Status.BadRequest),
    MountedRouteProbe("MasterServiceOfferVariantApi", get("/master-service-offer-variant/not-a-uuid"), Status.BadRequest),
    MountedRouteProbe("ProfileApi", get("/profile/not-a-uuid"), Status.BadRequest),
    MountedRouteProbe("LadderApi", postJson("/ladder/not-a-uuid/15", ""), Status.BadRequest),
  )

  "managed launcher public HTTP API set" should {
    "mount every public route expected by the local managed leaderboard launcher" in {
      (esPortCfg: ElasticsearchPortCfg, postgresPortCfg: PostgresPortCfg, qdrantPortCfg: QdrantPortCfg, embeddingConfig: LlamaCppEmbeddingClientConfig) =>
        val esClient          = new leaderboard.search.ElasticsearchTestClient(esPortCfg.host, esPortCfg.port)
        val qdrantClient      = new QdrantClient(qdrantPortCfg.host, qdrantPortCfg.port)
        val embeddingClient   = new LlamaCppEmbeddingClient(embeddingConfig)

        val resourceProbe = runIO(
          for {
            esResult        <- esClient.getJson("/").either
            embeddingResult <- embeddingClient.embed("managed launcher route mount probe").either
            qdrantResult    <- qdrantClient.collectionInfo("/collections").either
          } yield (esResult, embeddingResult, qdrantResult)
        )

        resourceProbe match {
          case (Right(_), Right(vector), Right(_)) if vector.nonEmpty =>
            runIO(runRouteMountProof(esClient, postgresPortCfg, qdrantClient, embeddingClient, embeddingConfig))
          case (esResult, embeddingResult, qdrantResult) =>
            cancel(
                s"MANAGED_LAUNCHER_ROUTE_MOUNT_RESOURCE_GATED: esReachable=${esResult.isRight}, " +
                s"embeddingReachable=${embeddingResult.exists(_.nonEmpty)}, " +
                s"qdrantReachable=${qdrantResult.isRight}, embeddingEndpoint=${embeddingConfig.baseUrl}"
            )
        }
    }
  }

  private def runRouteMountProof(
    esClient: leaderboard.search.ElasticsearchTestClient,
    postgresPortCfg: PostgresPortCfg,
    qdrantClient: QdrantClient,
    embeddingClient: EmbeddingClient,
    embeddingConfig: LlamaCppEmbeddingClientConfig,
  ): IO[QueryFailure, Unit] = {
    val routeVectorSpec = vectorSpec.copy(
      collectionName = s"${vectorSpec.collectionName}_route_mount_${UUID.randomUUID().toString.replace('-', '_')}"
    )
    val routeSpec = baseSpec.copy(
      variantDocument = baseSpec.variantDocument.copy(
        indexName = s"${baseSpec.variantDocument.indexName}_managed_route_mount_${UUID.randomUUID().toString.replace('-', '_')}"
      ),
      vectorSearchSpec = Some(routeVectorSpec),
    )
    val collectionPath = s"/collections/${routeVectorSpec.collectionName}"
    val esJsonClient = new ElasticsearchJsonClientAdapter(esClient)

    (
      for {
        probe <- buildManagedLauncherProbe(esJsonClient, postgresPortCfg, qdrantClient, embeddingClient, embeddingConfig, routeSpec, routeVectorSpec).mapError(toQueryFailure)
        apis = probe.allHttpApis
        _ <- ZIO.succeed(assertApiInventory(apis))
        app = combineApis(apis.toSeq*)
        _ <- ZIO.foreachDiscard(mountedRouteProbes)(probeMountedRoute(app)).mapError(toRouteQueryFailure("probe-public-route"))
        noAppendObserved <- observe(app, postJson("/beauty-search", noAppendProbe.asJson.noSpaces)).mapError(toRouteQueryFailure("observe-no-append"))
        appendObserved   <- observe(app, postJson("/beauty-search", appendProbe.asJson.noSpaces)).mapError(toRouteQueryFailure("observe-append"))
        _ <- ZIO.succeed {
          assert(noAppendObserved.status != Status.NotFound, s"BeautySearch no-append route must not be 404, body=${noAppendObserved.body}")
          assert(appendObserved.status != Status.NotFound, s"BeautySearch append route must not be 404, body=${appendObserved.body}")
          assert(noAppendObserved.status == Status.Ok, s"BeautySearch no-append route must serve 200, got ${noAppendObserved.status}")
          assert(appendObserved.status == Status.Ok, s"BeautySearch append route must serve 200, got ${appendObserved.status}")
        }
        _ <- ZIO.fromEither(parse(noAppendObserved.body).left.map(error => QueryFailure.operation("parse-no-append-json", error.message)))
        _ <- ZIO.fromEither(parse(appendObserved.body).left.map(error => QueryFailure.operation("parse-append-json", error.message)))
        noAppend <- decodeRouteResponse(noAppendObserved, "manicure_real_route_probe")
        append   <- decodeRouteResponse(appendObserved, "q_broad_006_ready_append_probe")
        _ <- ZIO.succeed {
          assertNoAppend(noAppend)
          assertAppend(append)
        }
      } yield ()
    ).ensuring(qdrantClient.deleteCollection(collectionPath).either.unit)
      .ensuring(esClient.deleteIndex(routeSpec.variantDocument.indexName).either.unit)
  }

  // Test-local mirror of only the non-search public HTTP API portion of
  // `LeaderboardPlugin.modules.apiBase[IO]`: the eight non-search APIs, their Tapir endpoint
  // singletons, their weak `Set[HttpApi[IO]]` contributions, and `Ranks[IO]`. The managed
  // `/beauty-search` route and its `BeautySearchApi[IO]` contribution still come from
  // `BeautySearchLocalQdrantSupplementLauncherModule.managedLocalDefault`. This avoids an ad-hoc
  // whole/broad plugin API include inside the focused spec graph.
  private def managedLauncherNonSearchPublicApiModule: ModuleDef = new ModuleDef {
    // The `ladder` API
    make[LadderTapirEndpoints].fromValue(LadderTapirEndpoints)
    make[LadderApi[IO]]
    // The `category` API
    make[CategoryTapirEndpoints].fromValue(CategoryTapirEndpoints)
    make[CategoryApi[IO]]
    // The `service` API
    make[ServiceTapirEndpoints].fromValue(ServiceTapirEndpoints)
    make[ServiceApi[IO]]
    // The `master` API
    make[MasterTapirEndpoints].fromValue(MasterTapirEndpoints)
    make[MasterApi[IO]]
    // The `master-location` API
    make[MasterLocationTapirEndpoints].fromValue(MasterLocationTapirEndpoints)
    make[MasterLocationApi[IO]]
    // The `master-service-offer` API
    make[MasterServiceOfferTapirEndpoints].fromValue(MasterServiceOfferTapirEndpoints)
    make[MasterServiceOfferApi[IO]]
    // The `master-service-offer-variant` API
    make[MasterServiceOfferVariantTapirEndpoints].fromValue(MasterServiceOfferVariantTapirEndpoints)
    make[MasterServiceOfferVariantApi[IO]]
    // The `profile` API
    make[ProfileTapirEndpoints].fromValue(ProfileTapirEndpoints)
    make[ProfileApi[IO]]

    // The eight non-search public APIs as weak `Set[HttpApi[IO]]` members
    many[HttpApi[IO]]
      .weak[LadderApi[IO]]
      .weak[CategoryApi[IO]]
      .weak[ServiceApi[IO]]
      .weak[MasterApi[IO]]
      .weak[MasterLocationApi[IO]]
      .weak[MasterServiceOfferApi[IO]]
      .weak[MasterServiceOfferVariantApi[IO]]
      .weak[ProfileApi[IO]]

    make[Ranks[IO]].from[Ranks.Impl[IO]]
  }

  private def buildManagedLauncherProbe(
    esClient: ElasticsearchJsonClient,
    postgresPortCfg: PostgresPortCfg,
    qdrantClient: QdrantClient,
    embeddingClient: EmbeddingClient,
    embeddingConfig: LlamaCppEmbeddingClientConfig,
    routeSpec: BeautySearchSpec,
    routeVectorSpec: leaderboard.search.dsl.VectorSearchSpec,
  ): Task[ManagedLauncherRouteSetProbe] = {
    val module = new ModuleDef {
      include(managedLauncherNonSearchPublicApiModule)
      include(LeaderboardPlugin.modules.repoProd[IO])
      include(LeaderboardPlugin.modules.seed[IO])
      include(LeaderboardPlugin.modules.seedManaged[IO])
      include(BeautySearchLocalQdrantSupplementLauncherModule.managedLocalDefault)
      include(LogIO2Module[IO]())
      make[IzLogger].fromValue(IzLogger())
      make[Async[Task]].fromValue(Async[Task])
      make[PostgresPortCfg].fromValue(postgresPortCfg)
      make[PostgresCfg].from {
        () =>
          PostgresCfg(
            jdbcDriver = "org.postgresql.Driver",
            url = "jdbc:postgresql://{host}:{port}/postgres",
            user = "postgres",
            password = "postgres",
          )
      }
      make[LadderRole[IO]]
      make[CategoryRole[IO]]
      make[ServiceRole[IO]]
      make[MasterRole[IO]]
      make[MasterLocationRole[IO]]
      make[MasterServiceOfferRole[IO]]
      make[MasterServiceOfferVariantRole[IO]]
      make[ProfileRole[IO]]
      make[ManagedLauncherRouteSetProbe].from {
        (
          seedReady: BeautyQSeedReady,
          searchDataReady: BeautyQManagedLocalSearchDataReady,
          ladderRole: LadderRole[IO],
          categoryRole: CategoryRole[IO],
          serviceRole: ServiceRole[IO],
          masterRole: MasterRole[IO],
          masterLocationRole: MasterLocationRole[IO],
          masterServiceOfferRole: MasterServiceOfferRole[IO],
          masterServiceOfferVariantRole: MasterServiceOfferVariantRole[IO],
          profileRole: ProfileRole[IO],
          allHttpApis: Set[HttpApi[IO]],
        ) =>
          ManagedLauncherRouteSetProbe(
            seedReady,
            searchDataReady,
            List(
              ladderRole,
              categoryRole,
              serviceRole,
              masterRole,
              masterLocationRole,
              masterServiceOfferRole,
              masterServiceOfferVariantRole,
              profileRole,
            ),
            allHttpApis,
          )
      }
    }.overriddenBy(new ModuleDef {
      make[BeautySearchSpec].fromValue(routeSpec)
      make[ElasticsearchJsonClient].fromValue(esClient)
      make[EmbeddingClient].fromValue(embeddingClient)
      make[LlamaCppEmbeddingClientConfig].fromValue(embeddingConfig)
      make[QdrantClient].fromValue(qdrantClient)
      make[SemanticCandidateBackend[IO]].from {
        (embeddingClient: EmbeddingClient, qdrantSearchClient: QdrantSearchClient) =>
          new QdrantSemanticCandidateBackend(
            new QdrantSemanticCandidateSearch(embeddingClient, qdrantSearchClient),
            routeVectorSpec,
          )
      }
      make[BeautyQManagedLocalSearchDataReady].fromResource {
        (
          esClient: ElasticsearchJsonClient,
          qdrantClient: QdrantClient,
          embeddingClient: EmbeddingClient,
          embeddingConfig: LlamaCppEmbeddingClientConfig,
          spec: BeautySearchSpec,
          catalog: BeautySearchReadyCatalogDocuments,
          log: LogIO2[IO],
        ) =>
          new BeautyQManagedLocalSearchDataReady.Bootstrap(
            esClient,
            qdrantClient,
            embeddingClient,
            spec,
            catalog,
            routeVectorSpec,
            embeddingConfig.baseUrl,
            log,
          )
      }
      make[HttpServer].fromResource {
        (
          seedReady: BeautyQSeedReady,
          searchDataReady: BeautyQManagedLocalSearchDataReady,
          allHttpApis: Set[HttpApi[IO]],
        ) =>
          val _ = seedReady
          val _ = searchDataReady
          Lifecycle.fromCats {
            val combinedApis = allHttpApis.map(_.http).toList.foldK
            EmberServerBuilder
              .default[Task](using Async[Task], Network.forAsync[Task])
              .withHost(Ipv4Address.fromString("127.0.0.1").fold(throw new IllegalArgumentException("invalid loopback host"))(identity))
              .withPort(Port.fromInt(0).fold(throw new IllegalArgumentException("invalid ephemeral port"))(identity))
              .withHttpApp(combinedApis.orNotFound)
              .build
              .map(HttpServer(_))
          }
      }
    })

    Injector[Task]().produce(
        bindings = module,
        roots = Roots.target[ManagedLauncherRouteSetProbe],
        activation = Activation(Scene -> Scene.Managed, Repo -> Repo.Prod),
        locatorPrivacy = LocatorPrivacy.PublicByDefault,
      )
      .use(locator => ZIO.succeed(locator.get[ManagedLauncherRouteSetProbe]))
  }

  private def assertApiInventory(apis: Set[HttpApi[IO]]): Unit = {
    assert(apis.collect { case api: CategoryApi[IO] => api }.size == 1, "expected exactly one CategoryApi in Set[HttpApi]")
    assert(apis.collect { case api: ServiceApi[IO] => api }.size == 1, "expected exactly one ServiceApi in Set[HttpApi]")
    assert(apis.collect { case api: MasterApi[IO] => api }.size == 1, "expected exactly one MasterApi in Set[HttpApi]")
    assert(apis.collect { case api: MasterLocationApi[IO] => api }.size == 1, "expected exactly one MasterLocationApi in Set[HttpApi]")
    assert(apis.collect { case api: MasterServiceOfferApi[IO] => api }.size == 1, "expected exactly one MasterServiceOfferApi in Set[HttpApi]")
    assert(apis.collect { case api: MasterServiceOfferVariantApi[IO] => api }.size == 1, "expected exactly one MasterServiceOfferVariantApi in Set[HttpApi]")
    assert(apis.collect { case api: ProfileApi[IO] => api }.size == 1, "expected exactly one ProfileApi in Set[HttpApi]")
    assert(apis.collect { case api: LadderApi[IO] => api }.size == 1, "expected exactly one LadderApi in Set[HttpApi]")
    assert(apis.collect { case api: BeautySearchApi[IO] => api }.size == 1, "expected exactly one BeautySearchApi in Set[HttpApi]")
    assert(apis.collect { case _: EsLifecycleStatusApi[IO] => () }.isEmpty, "EsLifecycleStatusApi is not part of the managed leaderboard launcher graph")
    assert(apis.size == 9, s"expected exactly 9 public HttpApi instances, got ${apis.map(_.getClass.getName).toList.sorted.mkString(", ")}")
    ()
  }

  private def probeMountedRoute(app: org.http4s.HttpApp[Task])(probe: MountedRouteProbe): Task[Unit] =
    observe(app, probe.request).map {
      response =>
        assert(response.status != Status.NotFound, s"${probe.apiLabel} route must not return 404, body=${response.body}")
        assert(response.body != "Not found", s"${probe.apiLabel} route returned plain Not found")
        assert(response.status == probe.expectedStatus, s"${probe.apiLabel} expected ${probe.expectedStatus}, got ${response.status}, body=${response.body}")
        ()
    }

  private def decodeRouteResponse(observed: ObservedResponse, queryLabel: String): IO[QueryFailure, BeautySearchResponse] =
    ZIO.fromEither(
      decode[BeautySearchResponse](observed.body).left.map(error =>
        QueryFailure.operation("decode-managed-route-response", s"query=$queryLabel error=${error.getMessage} body=${observed.body}")
      )
    )

  private def assertNoAppend(response: BeautySearchResponse): Unit = {
    assert(response.executionMode == BeautySearchExecutionMode.EsPlusQdrantSupplement)
    assert(response.qdrantSupplement.status == QdrantSupplementStatus.UsedNoAppend, s"no-append status=${response.qdrantSupplement.status}")
    assert(response.qdrantSupplement.contribution == QdrantSupplementContribution.None, s"no-append contribution=${response.qdrantSupplement.contribution}")
    assert(response.variantCarousel.nonEmpty, "no-append must still have ES baseline results")
    assert(response.variantCarousel.forall(_.resultOrigin == VariantResultOrigin.EsBaseline), "no-append origins must all be es_baseline")
    ()
  }

  private def assertAppend(response: BeautySearchResponse): Unit = {
    val qdrantVariants = response.variantCarousel.filter(_.resultOrigin == VariantResultOrigin.QdrantSupplement)
    assert(response.executionMode == BeautySearchExecutionMode.EsPlusQdrantSupplement)
    assert(response.qdrantSupplement.status == QdrantSupplementStatus.UsedWithAppend, s"append status=${response.qdrantSupplement.status}")
    assert(response.qdrantSupplement.contribution == QdrantSupplementContribution.QdrantOnlyVariantAppend, s"append contribution=${response.qdrantSupplement.contribution}")
    assert(qdrantVariants.size == 1, s"append must mark exactly one variant qdrant_supplement, got ${qdrantVariants.size}")
    ()
  }

  private def toQueryFailure(error: Throwable): QueryFailure =
    QueryFailure.operation("managed-launcher-route-graph", error.getMessage)

  private def toRouteQueryFailure(operation: String)(error: Throwable): QueryFailure =
    QueryFailure.operation(operation, error.getMessage)

  private final case class MountedRouteProbe(
    apiLabel: String,
    request: org.http4s.Request[Task],
    expectedStatus: Status,
  )

  private final case class ManagedLauncherRouteSetProbe(
    seedReady: BeautyQSeedReady,
    searchDataReady: BeautyQManagedLocalSearchDataReady,
    retainedRoles: List[Any],
    allHttpApis: Set[HttpApi[IO]],
  )

  private final class ElasticsearchJsonClientAdapter(delegate: leaderboard.search.ElasticsearchTestClient) extends ElasticsearchJsonClient {
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
