package leaderboard.search

import cats.effect.Async
import distage.{DIKey, Injector, ModuleDef, Scene}
import io.circe.parser.decode
import io.circe.syntax.*
import izumi.distage.model.definition.{Activation, LocatorPrivacy}
import izumi.distage.model.plan.Roots
import izumi.logstage.api.IzLogger
import izumi.logstage.distage.LogIO2Module
import leaderboard.api.{BeautySearchApi, HttpApi}
import leaderboard.config.{ElasticsearchPortCfg, QdrantPortCfg}
import leaderboard.model.QueryFailure
import leaderboard.plugins.{BeautySearchLocalQdrantSupplementLauncherModule, LeaderboardPlugin}
import leaderboard.search.dsl.{BeautySearchSpec, BeautySearchSpecV1}
import leaderboard.search.elasticsearch.ElasticsearchJsonClient
import leaderboard.search.embedding.{EmbeddingClient, LlamaCppEmbeddingClient, LlamaCppEmbeddingClientConfig}
import leaderboard.search.qdrant.QdrantClient
import leaderboard.search.startup.BeautyQManagedLocalSearchDataReady
import leaderboard.{HttpContractTestSupport, LeaderboardTest, ObservedResponse, ProdTest}
import org.http4s.Status
import zio.interop.catz.*
import zio.{IO, Runtime, Task, Unsafe, ZIO}

import java.util.UUID

/**
 * QP27: regression proof for the real managed-local launcher route mount. QP26 proved the route with
 * a probe that depended on `BeautySearchApi[IO]` directly; the launcher only roots the HTTP server,
 * whose dependencies are readiness markers plus `Set[HttpApi[IO]]`. This spec mirrors that retention
 * shape by rooting `BeautyQManagedLocalSearchDataReady` and `Set[HttpApi[IO]]`, never
 * `BeautySearchApi[IO]`, then issuing real `POST /beauty-search` requests against the graph-wired
 * HTTP API set.
 */
final class QP27ManagedLocalLauncherBeautySearchRouteMountSpec
    extends LeaderboardTest
    with ProdTest
    with HttpContractTestSupport {

  override def config = super.config.copy(
    memoizationRoots = super.config.memoizationRoots + DIKey[ElasticsearchPortCfg] + DIKey[QdrantPortCfg],
  )

  private val baseSpec: BeautySearchSpec = BeautySearchSpecV1.spec
  private val vectorSpec = BeautySearchLocalQdrantSupplementLauncherModule.VectorSpec

  private val noAppendProbe =
    UserSearchInput("маникюр", None, None, limit = 10)

  private val appendProbe =
    UserSearchInput("beauty near Wandsbek Markt", None, None, limit = 10)

  "QP27 managed local launcher HTTP API set" should {
    "mount POST /beauty-search without rooting BeautySearchApi directly" in {
      (esPortCfg: ElasticsearchPortCfg, qdrantPortCfg: QdrantPortCfg) =>
        val esClient          = new ElasticsearchTestClient(esPortCfg.host, esPortCfg.port)
        val qdrantClient      = new QdrantClient(qdrantPortCfg.host, qdrantPortCfg.port)
        val embeddingEndpoint = sys.env.getOrElse("M18_QDRANT_EMBEDDING_ENDPOINT", "http://localhost:8081")
        val embeddingClient   = new LlamaCppEmbeddingClient(LlamaCppEmbeddingClientConfig(baseUrl = embeddingEndpoint))

        val resourceProbe = runIO(
          for {
            esResult        <- esClient.getJson("/").either
            embeddingResult <- embeddingClient.embed("qp27 managed local route mount probe").either
            qdrantResult    <- qdrantClient.collectionInfo("/collections").either
          } yield (esResult, embeddingResult, qdrantResult)
        )

        resourceProbe match {
          case (Right(_), Right(vector), Right(_)) if vector.nonEmpty =>
            runIO(runRouteMountProof(esClient, qdrantClient, embeddingClient))
          case (esResult, embeddingResult, qdrantResult) =>
            cancel(
              s"QP27_RESOURCE_GATED: esReachable=${esResult.isRight}, " +
                s"embeddingReachable=${embeddingResult.exists(_.nonEmpty)}, " +
                s"qdrantReachable=${qdrantResult.isRight}, embeddingEndpoint=$embeddingEndpoint"
            )
        }
    }
  }

  private def runRouteMountProof(
    esClient: ElasticsearchTestClient,
    qdrantClient: QdrantClient,
    embeddingClient: EmbeddingClient,
  ): IO[QueryFailure, Unit] = {
    val routeSpec = baseSpec.copy(
      variantDocument = baseSpec.variantDocument.copy(
        indexName = s"${baseSpec.variantDocument.indexName}_qp27_${UUID.randomUUID().toString.replace('-', '_')}"
      ),
      vectorSearchSpec = Some(vectorSpec),
    )
    val esJsonClient = new ElasticsearchJsonClientAdapter(esClient)

    (
      for {
        probe <- buildManagedLocalProbe(esJsonClient, qdrantClient, embeddingClient, routeSpec).mapError(toQueryFailure)
        apis = probe.allHttpApis
        _ <- ZIO.succeed {
          val beautyApis = apis.collect { case api: BeautySearchApi[IO] => api }
          assert(beautyApis.size == 1, s"QP27: expected exactly one BeautySearchApi in Set[HttpApi], got ${beautyApis.size}")
        }
        noAppendObserved = serve(apis, noAppendProbe)
        appendObserved   = serve(apis, appendProbe)
        _ <- ZIO.succeed {
          assert(noAppendObserved.status != Status.NotFound, s"QP27 no-append route must not be 404, body=${noAppendObserved.body}")
          assert(appendObserved.status != Status.NotFound, s"QP27 append route must not be 404, body=${appendObserved.body}")
          assert(noAppendObserved.status == Status.Ok, s"QP27 no-append route must serve 200, got ${noAppendObserved.status}")
          assert(appendObserved.status == Status.Ok, s"QP27 append route must serve 200, got ${appendObserved.status}")
        }
        noAppend <- decodeRouteResponse(noAppendObserved, "manicure_real_route_probe")
        append   <- decodeRouteResponse(appendObserved, "q_broad_006_ready_append_probe")
        _ <- ZIO.succeed {
          assertNoAppend(noAppend)
          assertAppend(append)
        }
      } yield ()
    ).ensuring(esClient.deleteIndex(routeSpec.variantDocument.indexName).either.unit)
  }

  private def buildManagedLocalProbe(
    esClient: ElasticsearchJsonClient,
    qdrantClient: QdrantClient,
    embeddingClient: EmbeddingClient,
    routeSpec: BeautySearchSpec,
  ): Task[ManagedLocalRouteSetProbe] = {
    val module = new ModuleDef {
      include(LeaderboardPlugin.modules.apiBase[IO])
      include(LeaderboardPlugin.modules.seed[IO])
      include(BeautySearchLocalQdrantSupplementLauncherModule.managedLocalDefault)
      include(LogIO2Module[IO]())
      make[IzLogger].fromValue(IzLogger())
      make[Async[Task]].fromValue(Async[Task])
      make[ManagedLocalRouteSetProbe].from {
        (
          searchDataReady: BeautyQManagedLocalSearchDataReady,
          allHttpApis: Set[HttpApi[IO]],
        ) =>
          ManagedLocalRouteSetProbe(searchDataReady, allHttpApis)
      }
    }.overriddenBy(new ModuleDef {
      make[BeautySearchSpec].fromValue(routeSpec)
      make[ElasticsearchJsonClient].fromValue(esClient)
      make[EmbeddingClient].fromValue(embeddingClient)
      make[QdrantClient].fromValue(qdrantClient)
    })

    Injector[Task]().produce(
        bindings = module,
        roots = Roots.target[ManagedLocalRouteSetProbe],
        activation = Activation(Scene -> Scene.Managed),
        locatorPrivacy = LocatorPrivacy.PublicByDefault,
      )
      .use(locator => ZIO.succeed(locator.get[ManagedLocalRouteSetProbe]))
  }

  private def serve(apis: Set[HttpApi[IO]], input: UserSearchInput): ObservedResponse =
    runIO(observe(combineApis(apis.toSeq*), postJson("/beauty-search", input.asJson.noSpaces)))

  private def decodeRouteResponse(observed: ObservedResponse, queryLabel: String): IO[QueryFailure, BeautySearchResponse] =
    ZIO.fromEither(
      decode[BeautySearchResponse](observed.body).left.map(error =>
        QueryFailure.operation("decode-qp27-route-response", s"query=$queryLabel error=${error.getMessage} body=${observed.body}")
      )
    )

  private def assertNoAppend(response: BeautySearchResponse): Unit = {
    assert(response.executionMode == BeautySearchExecutionMode.EsPlusQdrantSupplement)
    assert(response.qdrantSupplement.status == QdrantSupplementStatus.UsedNoAppend, s"QP27 no-append status=${response.qdrantSupplement.status}")
    assert(response.qdrantSupplement.contribution == QdrantSupplementContribution.None, s"QP27 no-append contribution=${response.qdrantSupplement.contribution}")
    assert(response.variantCarousel.nonEmpty, "QP27 no-append must still have ES baseline results")
    assert(response.variantCarousel.forall(_.resultOrigin == VariantResultOrigin.EsBaseline), "QP27 no-append origins must all be es_baseline")
    ()
  }

  private def assertAppend(response: BeautySearchResponse): Unit = {
    val qdrantVariants = response.variantCarousel.filter(_.resultOrigin == VariantResultOrigin.QdrantSupplement)
    assert(response.executionMode == BeautySearchExecutionMode.EsPlusQdrantSupplement)
    assert(response.qdrantSupplement.status == QdrantSupplementStatus.UsedWithAppend, s"QP27 append status=${response.qdrantSupplement.status}")
    assert(response.qdrantSupplement.contribution == QdrantSupplementContribution.QdrantOnlyVariantAppend, s"QP27 append contribution=${response.qdrantSupplement.contribution}")
    assert(qdrantVariants.size == 1, s"QP27 append must mark exactly one variant qdrant_supplement, got ${qdrantVariants.size}")
    ()
  }

  private def toQueryFailure(error: Throwable): QueryFailure =
    QueryFailure.operation("qp27-managed-local-route-graph", error.getMessage)

  private final case class ManagedLocalRouteSetProbe(
    searchDataReady: BeautyQManagedLocalSearchDataReady,
    allHttpApis: Set[HttpApi[IO]],
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
