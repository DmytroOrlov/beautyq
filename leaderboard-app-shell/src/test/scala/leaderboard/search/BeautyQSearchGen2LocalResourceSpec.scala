package leaderboard.search

import io.circe.Json
import com.typesafe.config.ConfigFactory
import distage.{Injector, ModuleDef, Scene}
import izumi.distage.model.definition.{Activation, LocatorPrivacy}
import izumi.distage.model.plan.Roots
import izumi.distage.config.model.AppConfig
import izumi.logstage.api.IzLogger
import izumi.logstage.distage.LogIO2Module
import leaderboard.HttpContractTestSupport
import leaderboard.config.{ElasticsearchPortCfg, QdrantGen2PortCfg}
import leaderboard.http.tapir.BeautySearchGen2TapirEndpoints
import leaderboard.search.embedding.LlamaCppEmbeddingClientConfig
import leaderboard.plugins.{ElasticsearchDockerPlugin, QdrantGen2DockerPlugin}
import leaderboard.search.beautyq.gen2.materialization.*
import leaderboard.search.beautyq.gen2.wiring.*
import leaderboard.search.beautyq.gen2.wiring.BeautyQElasticsearchTestFixtures
import leaderboard.search.beautyq.gen2.wiring.testkit.BeautyQOrchestrationTestKit
import leaderboard.search.gen2.core.materialization.{SearchSnapshotSource, SourceRevision, VersionedSnapshot}
import leaderboard.search.gen2.core.hydration.CandidateHydrationError
import leaderboard.search.gen2.elasticsearch.*
import leaderboard.search.gen2.qdrant.*
import leaderboard.search.gen2.transport.*
import leaderboard.search.gen2.{BeautyQGen2EmbeddingClient, BeautyQSearchGen2Bootstrap, BeautyQSearchGen2HttpService, BeautyQSearchGen2Startup}
import zio.{IO, Runtime, Task, Unsafe, ZIO}
import zio.interop.catz.*

import java.time.{Clock, Duration, Instant}
import java.net.{HttpURLConnection, URI, URL}

import BeautyQSearchGen2ResourceSupport.*

/** Atomic resource-gate proofs plus the managed BeautyQ Gen2 communication proof. */
final class BeautyQSearchGen2LocalResourceSpec extends org.scalatest.wordspec.AnyWordSpec with HttpContractTestSupport {
  "managed BeautyQ Gen2" should {
    "activate and query two real BeautyQ-shaped generations when resources serve" in {
      withManagedPorts { (elasticsearchPort, qdrantPort) =>
          val esEndpoint = ElasticsearchGen2Endpoint.fromString(s"http://${elasticsearchPort.host}:${elasticsearchPort.port}").getOrElse(fail("expected managed Elasticsearch endpoint"))
          val esTransport = ElasticsearchGen2TransportConfig.create(esEndpoint, Duration.ofSeconds(5), Duration.ofSeconds(60)).getOrElse(fail("expected Elasticsearch transport config"))
          val esClient = ElasticsearchGen2JsonClient.jdk(esTransport)
          val qdrantEndpoint = Gen2HttpEndpoint.fromString(s"http://${qdrantPort.host}:${qdrantPort.port}").getOrElse(fail("expected managed Gen2 Qdrant endpoint"))
          val qdrantTransport = Gen2HttpTransportConfig.create(qdrantEndpoint, Duration.ofSeconds(5), Duration.ofSeconds(60)).getOrElse(fail("expected Qdrant transport config"))
          val qdrantHttp = Gen2JsonHttpClient.jdk(qdrantTransport)
          runScenario(esClient, qdrantHttp, deterministicEmbedding)
      }
    }

    "run the complete default native Gen2 application through the real Elasticsearch, Qdrant, and embedding communication paths and POST /beauty-search" in {
      withManagedPorts { (elasticsearchPort, qdrantPort) =>
        val embeddingConfig = LlamaCppEmbeddingClientConfig(
          baseUrl = sys.env.getOrElse("M18_QDRANT_EMBEDDING_ENDPOINT", "http://localhost:8081"),
          endpointPath = "/v1/embeddings",
        )
        val embeddingEndpoint = URI.create(s"${embeddingConfig.baseUrl}${embeddingConfig.endpointPath}").toURL
        val embeddingReachable = probeEmbeddingReachability(embeddingEndpoint)
        if (!embeddingReachable) {
          fail(s"VERIFICATION BLOCKED: real BeautyQ Gen2 embedding endpoint is unreachable at $embeddingEndpoint; cannot prove embedding communication")
        }
        val (embedding, _) = buildRealEmbedding(embeddingConfig)
        runScenarioWithEmbedding(elasticsearchPort, qdrantPort, embedding)
      }
    }
  }

  private val deterministicEmbedding: QdrantQueryEmbeddingPort[BeautyQEmbeddingRequestError] =
    new QdrantQueryEmbeddingPort[BeautyQEmbeddingRequestError] {
      def embed(input: QdrantEmbeddingInput): Either[BeautyQEmbeddingRequestError, QdrantEmbeddingResult] =
        QdrantEmbeddingResult
          .from(input, Vector.fill(input.modelValue.dimension)(0.1))
          .left
          .map(BeautyQEmbeddingRequestError.InvalidResult.apply)
    }

  private def probeEmbeddingReachability(endpoint: URL): Boolean = {
    val connection = endpoint.openConnection()
    connection match {
      case http: HttpURLConnection =>
        http.setConnectTimeout(2000)
        http.setReadTimeout(2000)
        http.setRequestMethod("GET")
        try {
          http.connect()
          val code = http.getResponseCode
          code >= 200 && code < 500
        } catch {
          case _: java.io.IOException => false
        } finally {
          http.disconnect()
        }
      case _ => false
    }
  }

  private def buildRealEmbedding(
    config: LlamaCppEmbeddingClientConfig,
  ): (QdrantQueryEmbeddingPort[BeautyQEmbeddingRequestError], Int) = {
    val baseUrl = s"${config.baseUrl}"
    val httpEndpoint = Gen2HttpEndpoint.fromString(baseUrl).getOrElse(fail(s"real embedding endpoint must be valid: $baseUrl"))
    val transport = Gen2HttpTransportConfig
      .create(httpEndpoint, Duration.ofSeconds(5), Duration.ofSeconds(60))
      .getOrElse(fail("real embedding transport config must be valid"))
    val http = Gen2JsonHttpClient.jdk(transport)
    val client = BeautyQGen2EmbeddingClient.fromTransport(http, config.endpointPath)
    val probeInput = QdrantEmbeddingInput.from(
      QdrantEmbeddingPurpose.CandidateQuery,
      "beauty-search-gen2-managed-preflight",
      "beauty-search-gen2-managed-preflight",
      BeautyQQdrantPolicy.policy.embeddingModel,
    ).getOrElse(fail("expected preflight embedding input"))
    val probeResult = client.embed(probeInput) match {
      case Right(value) => value
      case Left(error)  => fail(s"real embedding preflight failed: $error")
    }
    val dimension = probeResult.model.dimension
    assert(dimension == BeautyQQdrantPolicy.policy.embeddingModel.dimension, s"real embedding dimension must match policy: actual=$dimension expected=${BeautyQQdrantPolicy.policy.embeddingModel.dimension}")
    (client, dimension)
  }

  private def runScenario(
    esClient: ElasticsearchGen2JsonClient,
    qdrantHttp: Gen2JsonHttpClient,
    embedding: QdrantQueryEmbeddingPort[BeautyQEmbeddingRequestError],
  ): Unit = {
    val materializedA = BeautyQOrchestrationTestKit.materialized
    val materializedB = materializeDerivedSecondSnapshot()
    val batching = ElasticsearchBulkBatchingPolicy.create(100, 1024L * 1024L).getOrElse(fail("expected batching"))
    val elasticsearch = BeautyQElasticsearchBaselineService
      .make(esClient, Clock.systemUTC(), batching)
      .getOrElse(fail("expected BeautyQ baseline service"))
    val qdrantClient = QdrantGen2Client.fromTransport(qdrantHttp)
    val qdrantLifecycle = BeautyQQdrantRuntime.lifecycle(qdrantClient).getOrElse(fail("expected BeautyQ Qdrant lifecycle"))
    val candidateService = BeautyQQdrantRuntime.candidateService(qdrantClient).getOrElse(fail("expected BeautyQ Qdrant candidate service"))
    val expectedEsTargets = Vector(materializedA, materializedB).map { materialized =>
      val generation = BeautyQElasticsearchGeneration.compile(materialized).getOrElse(fail("expected ES generation for resource cleanup"))
      ElasticsearchGenerationNaming
        .physicalIndexName(BeautyQSearchGen2ResourceNames.ElasticsearchPhysicalIndexPrefix, generation.identity)
        .getOrElse(fail("expected deterministic ES target for resource cleanup"))
        .value
    }
    BeautyQSearchGen2ResourceSupport.classifyPreflight(
      esClient.getJson("/"),
      qdrantHttp.getJson("/"),
      Right(preflightEmbedding),
    ) match {
      case PreflightReady => ()
      case PreflightBroken(resources) => fail(s"managed Gen2 resources are reachable but broken: ${resources.mkString(", ")}")
      case PreflightBlocked(resources) => fail(s"managed Gen2 resources are unexpectedly unavailable: ${resources.mkString(", ")}")
    }
    ensureIsolatedNamespace(esClient, qdrantClient, qdrantHttp)
    val request = BeautyQOrchestrationTestKit.eligible().request

    try {
      val application = BeautyQSearchApplication.make(materializedA, elasticsearch, embedding, candidateService)
      val generationA = BeautyQSearchGenerationApplication
        .activate(materializedA, elasticsearch, qdrantLifecycle, embedding) match {
          case Right(value) => value
          case Left(error) => fail(s"expected BeautyQ generation A activation, got $error")
        }
      val firstResult = application.execute(request).getOrElse(fail("expected request against active generation A"))
      val generationB = BeautyQSearchGenerationApplication
        .activate(materializedB, elasticsearch, qdrantLifecycle, embedding) match {
          case Right(value) => value
          case Left(error) => fail(s"expected BeautyQ generation B activation, got $error")
        }
      application.execute(request) match {
        case Left(BeautyQSearchApplicationError.Orchestration(
              BeautyQSearchOrchestrationError.CandidatePipeline(
                BeautyQQdrantCandidatePipelineError.Hydration(
                  CandidateHydrationError.SourceSnapshotMismatch(expected, actual)
                )
              )
            )) =>
          assert(expected == materializedB.sourceSnapshot.contentFingerprint.value)
          assert(actual == materializedA.sourceSnapshot.contentFingerprint.value)
        case other => fail(s"expected the same application to observe the fresh generation, got $other")
      }
      val applicationB = BeautyQSearchApplication.make(materializedB, elasticsearch, embedding, candidateService)
      val secondResult = applicationB.execute(request) match {
        case Right(value) => value
        case Left(error) => fail(s"expected request against fresh active generation B, got $error")
      }
      val activeEsTargets = readElasticsearchAliasTargets(esClient, BeautyQSearchGen2ResourceNames.ElasticsearchAlias) match {
        case Right(targets) => targets
        case Left(error) => fail(s"expected active ES alias, got $error")
      }
      assert(activeEsTargets == Vector(generationB.elasticsearchGeneration.physicalTarget.value))
      val activeQdrantTargets = readQdrantAliases(qdrantClient) match {
        case Right(entries) => entries.filter(_.alias == BeautyQSearchGen2ResourceNames.QdrantCollectionAlias).map(_.collection).distinct.sorted
        case Left(error) => fail(s"expected Qdrant alias response, got $error")
      }
      val activeQdrantTarget = generationB.qdrantGeneration match {
        case Some(value) => value.physicalCollection.value
        case None         => fail("expected Qdrant generation B to be ready")
      }
      assert(activeQdrantTargets == Vector(activeQdrantTarget))
      assert(generationA.qdrantGeneration.exists(value => value.physicalCollection.value != activeQdrantTarget))
      assert(firstResult.baseline.target.value != secondResult.baseline.target.value)
      (): Unit
    } finally {
      cleanupExactResources(esClient, qdrantHttp, expectedEsTargets)
    }
  }

  private def runScenarioWithEmbedding(
    elasticsearchPort: ElasticsearchPortCfg,
    qdrantPort: QdrantGen2PortCfg,
    embedding: QdrantQueryEmbeddingPort[BeautyQEmbeddingRequestError],
  ): Unit = {
    val esEndpoint = ElasticsearchGen2Endpoint.fromString(s"http://${elasticsearchPort.host}:${elasticsearchPort.port}").getOrElse(fail("expected managed Elasticsearch endpoint"))
    val esTransport = ElasticsearchGen2TransportConfig.create(esEndpoint, Duration.ofSeconds(5), Duration.ofSeconds(60)).getOrElse(fail("expected Elasticsearch transport config"))
    val esClient = ElasticsearchGen2JsonClient.jdk(esTransport)
    val qdrantEndpoint = Gen2HttpEndpoint.fromString(s"http://${qdrantPort.host}:${qdrantPort.port}").getOrElse(fail("expected managed Gen2 Qdrant endpoint"))
    val qdrantTransport = Gen2HttpTransportConfig.create(qdrantEndpoint, Duration.ofSeconds(5), Duration.ofSeconds(60)).getOrElse(fail("expected Qdrant transport config"))
    val qdrantHttp = Gen2JsonHttpClient.jdk(qdrantTransport)
    val materialized = BeautyQOrchestrationTestKit.materialized
    val batching = ElasticsearchBulkBatchingPolicy.create(100, 1024L * 1024L).getOrElse(fail("expected batching"))
    val elasticsearch = BeautyQElasticsearchBaselineService
      .make(esClient, Clock.systemUTC(), batching)
      .getOrElse(fail("expected BeautyQ baseline service"))
    val qdrantClient = QdrantGen2Client.fromTransport(qdrantHttp)
    val qdrantLifecycle = BeautyQQdrantRuntime.lifecycle(qdrantClient).getOrElse(fail("expected BeautyQ Qdrant lifecycle"))
    val candidateService = BeautyQQdrantRuntime.candidateService(qdrantClient).getOrElse(fail("expected BeautyQ Qdrant candidate service"))
    val expectedEsTargets = Vector(materialized).map { mat =>
      val generation = BeautyQElasticsearchGeneration.compile(mat).getOrElse(fail("expected ES generation for cleanup"))
      ElasticsearchGenerationNaming
        .physicalIndexName(BeautyQSearchGen2ResourceNames.ElasticsearchPhysicalIndexPrefix, generation.identity)
        .getOrElse(fail("expected deterministic ES target for cleanup"))
        .value
    }
    ensureIsolatedNamespace(esClient, qdrantClient, qdrantHttp)
    val requestBody =
      "{\"query\":\"relaxing appointment\",\"filters\":[{\"field\":\"service\",\"operator\":\"equal\",\"value\":\"manicure\"}],\"requestedFacets\":[],\"sort\":[],\"page\":{\"size\":20}}"

    try {
      val bootstrap = BeautyQSearchGen2Bootstrap.make(
        new BeautyQVariantMaterializer.FromSnapshotSource[IO](new SearchSnapshotSource[IO, SnapshotLoadError, BeautyQSearchSnapshot] {
          def load: IO[SnapshotLoadError, VersionedSnapshot[BeautyQSearchSnapshot]] =
            ZIO.succeed(VersionedSnapshot(
              value = BeautyQElasticsearchTestFixtures.snapshot,
              contentFingerprint = BeautyQSnapshotFingerprint.compute(BeautyQElasticsearchTestFixtures.snapshot),
              sourceRevision = Some(SourceRevision("real-embedding-preflight")),
              capturedAt = Instant.now(),
            ))
        }),
        elasticsearch,
        qdrantLifecycle,
        embedding,
      )
      val startup = BeautyQSearchGen2Startup.acquire(bootstrap, candidateService)
      val startupOrError = Unsafe.unsafe { implicit unsafe =>
        Runtime.default.unsafe.run(startup).getOrThrowFiberFailure()
      }
      val api = new leaderboard.api.BeautySearchGen2Api[IO](
        new BeautyQSearchGen2HttpService(startupOrError.runtime),
        BeautySearchGen2TapirEndpoints,
      )
      val response = runIO(observe(api.http.orNotFound, postJson("/beauty-search", requestBody)))
      assert(response.status == org.http4s.Status.Ok, s"expected HTTP 200 from POST /beauty-search, got ${response.status} body=${response.body}")
      assert(response.body.contains("\"supplementStatus\":\"supplemented\""), s"expected supplemented status in body, got ${response.body}")
      assert(response.body.contains("\"totalHits\""), s"expected totalHits in body, got ${response.body}")
      assert(response.body.contains("\"appliedFilters\""), s"expected appliedFilters in body, got ${response.body}")
      (): Unit
    } finally {
      cleanupExactResources(esClient, qdrantHttp, expectedEsTargets)
    }
  }

  private def preflightEmbedding: QdrantEmbeddingResult = {
    val input = QdrantEmbeddingInput.from(
      QdrantEmbeddingPurpose.CandidateQuery,
      "managed-resource-preflight",
      "managed-resource-preflight",
      BeautyQQdrantPolicy.policy.embeddingModel,
    ).getOrElse(fail("expected preflight embedding input"))
    QdrantEmbeddingResult.from(input, Vector.fill(input.modelValue.dimension)(0.0)).getOrElse(fail("expected preflight embedding result"))
  }

  private def materializeDerivedSecondSnapshot(): MaterializedBeautyQVariantDocuments = {
    val sourceSnapshot = BeautyQElasticsearchTestFixtures.snapshot.copy(
      services = BeautyQElasticsearchTestFixtures.snapshot.services.map(_.copy(name = "Changed Service")),
    )
    val versioned = VersionedSnapshot(
      value = sourceSnapshot,
      contentFingerprint = BeautyQSnapshotFingerprint.compute(sourceSnapshot),
      sourceRevision = Some(SourceRevision("resource-derived-second-snapshot")),
      capturedAt = Instant.parse("2026-07-22T00:00:00Z"),
    )
    val source = new SearchSnapshotSource[Either, SnapshotLoadError, BeautyQSearchSnapshot] {
      def load: Either[SnapshotLoadError, VersionedSnapshot[BeautyQSearchSnapshot]] = Right(versioned)
    }
    new BeautyQVariantMaterializer.FromSnapshotSource[Either](source).load match {
      case Right(value) => value
      case Left(error)   => fail(s"expected derived materialized snapshot, got $error")
    }
  }

  private final case class ManagedPorts(
    elasticsearch: ElasticsearchPortCfg,
    qdrant: QdrantGen2PortCfg,
  )

  private def runIO[A](effect: zio.Task[A]): A =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
    }

  private def withManagedPorts[A](f: (ElasticsearchPortCfg, QdrantGen2PortCfg) => A): A = {
    BeautyQSearchGen2ResourceSupport.withExclusiveCanonicalNamespace {
      val module = new ModuleDef {
        make[AppConfig].fromValue(AppConfig.provided(ConfigFactory.load("common-reference.conf").resolve()))
        include(LogIO2Module[IO]())
        make[IzLogger].fromValue(IzLogger())
        include(ElasticsearchDockerPlugin.dockerModule[IO])
        include(QdrantGen2DockerPlugin.dockerModule[IO])
        make[ManagedPorts].from { (elasticsearch: ElasticsearchPortCfg, qdrant: QdrantGen2PortCfg) =>
          ManagedPorts(elasticsearch, qdrant)
        }
      }
      val effect = Injector[Task]().produce(
        bindings = module,
        roots = Roots.target[ManagedPorts],
        activation = Activation(Scene -> Scene.Managed),
        locatorPrivacy = LocatorPrivacy.PublicByDefault,
      ).use { locator =>
        val ports = locator.get[ManagedPorts]
        ZIO.attemptBlocking(f(ports.elasticsearch, ports.qdrant))
      }
      Unsafe.unsafe { implicit unsafe => Runtime.default.unsafe.run(effect).getOrThrowFiberFailure() }
    }
  }

  /** The application owner currently reserves one canonical BeautyQ namespace
    * rather than accepting per-test aliases. Refuse to mutate it unless the
    * exact aliases and physical namespace are empty; this makes teardown
    * isolation explicit instead of deleting another run's resources. */
  private def ensureIsolatedNamespace(
    esClient: ElasticsearchGen2JsonClient,
    qdrantClient: QdrantGen2Client,
    qdrantHttp: Gen2JsonHttpClient,
  ): Unit = {
    val aliases = Vector(
      BeautyQSearchGen2ResourceNames.ElasticsearchAlias,
      s"${BeautyQSearchGen2ResourceNames.ElasticsearchAlias}--superseded",
    ).flatMap { alias =>
      readElasticsearchAliasTargets(esClient, alias) match {
        case Right(targets) => targets
        case Left(error) => fail(s"could not establish Elasticsearch alias isolation for '$alias': $error")
      }
    }
    if (aliases.nonEmpty) fail(s"reserved Elasticsearch Gen2 aliases are not empty: ${aliases.sorted.mkString(",")}")
    esClient.getJson(s"/${BeautyQSearchGen2ResourceNames.ElasticsearchPhysicalIndexPrefix}*/_mapping") match {
      case Right(json) if json.asObject.exists(_.nonEmpty) =>
        fail("reserved Elasticsearch Gen2 physical namespace is not empty")
      case Right(_) => ()
      case Left(ElasticsearchGen2TransportError.HttpFailure(_, _, 404, _)) => ()
      case Left(error) => fail(s"could not establish Elasticsearch namespace isolation: $error")
    }
    readQdrantAliases(qdrantClient) match {
      case Right(entries) =>
        val reservedTargets = reservedQdrantAliasTargets(entries)
        if (reservedTargets.nonEmpty)
          fail(s"reserved Qdrant Gen2 alias is not empty: ${reservedTargets.mkString(",")}")
      case Left(error) => fail(s"could not establish Qdrant alias isolation: $error")
    }
    qdrantHttp.getJson("/collections") match {
      case Right(json) =>
        val names = decodeQdrantCollectionNames(json) match {
          case Right(values) => values
          case Left(error) => fail(s"could not decode Qdrant collection inventory: $error")
        }
        if (names.exists(_.startsWith(BeautyQSearchGen2ResourceNames.QdrantPhysicalCollectionPrefix)))
          fail("reserved Qdrant Gen2 physical namespace is not empty")
      case Left(error) => fail(s"could not establish Qdrant namespace isolation: $error")
    }
  }

  private def cleanupExactResources(
    esClient: ElasticsearchGen2JsonClient,
    qdrantHttp: Gen2JsonHttpClient,
    expectedEsTargets: Vector[String],
  ): Unit = {
    val esTargets = Vector(
      BeautyQSearchGen2ResourceNames.ElasticsearchAlias,
      s"${BeautyQSearchGen2ResourceNames.ElasticsearchAlias}--superseded",
    ).flatMap { alias =>
      readElasticsearchAliasTargets(esClient, alias) match {
        case Right(targets) => targets
        case Left(error) => fail(s"failed to read exact ES alias '$alias' during teardown: $error")
      }
    }
      .filter(_.startsWith(BeautyQSearchGen2ResourceNames.ElasticsearchPhysicalIndexPrefix)) ++ expectedEsTargets
    val distinctEsTargets = esTargets.distinct.sorted
    distinctEsTargets.foreach { target =>
      esClient.delete(s"/$target") match {
        case Right(()) => ()
        case Left(ElasticsearchGen2TransportError.HttpFailure(_, _, 404, _)) => ()
        case Left(error) => fail(s"failed exact ES cleanup for '$target': $error")
      }
    }
    val qdrantClient = QdrantGen2Client.fromTransport(qdrantHttp)
    val qdrantAliases = readQdrantAliases(qdrantClient) match {
      case Right(entries) => entries
      case Left(error) => fail(s"failed to read exact Qdrant aliases during teardown: $error")
    }
    val qdrantAliasTargets = reservedQdrantAliasTargets(qdrantAliases)
    if (qdrantAliasTargets.exists(value => !value.startsWith(BeautyQSearchGen2ResourceNames.QdrantPhysicalCollectionPrefix)))
      fail(s"refusing to delete a foreign Qdrant alias target: ${qdrantAliasTargets.mkString(",")}")
    val qdrantTargetsFromCollections = qdrantHttp.getJson("/collections") match {
      case Right(json) => decodeQdrantCollectionNames(json) match {
        case Right(names) => names
        case Left(error) => fail(s"failed to decode Qdrant collections during teardown: $error")
      }
      case Left(error) => fail(s"failed to read Qdrant collections during teardown: $error")
    }
    val qdrantTargets = (qdrantAliasTargets ++ qdrantTargetsFromCollections)
      .filter(_.startsWith(BeautyQSearchGen2ResourceNames.QdrantPhysicalCollectionPrefix)).distinct.sorted
    if (qdrantAliasTargets.nonEmpty) {
      qdrantClient.updateAliases(Json.obj(
        "actions" -> Json.arr(Json.obj("delete_alias" -> Json.obj("alias_name" -> Json.fromString(BeautyQSearchGen2ResourceNames.QdrantCollectionAlias)))),
      )) match {
        case Right(_) => ()
        case Left(Gen2HttpTransportError.HttpFailure(_, _, 404, _)) => ()
        case Left(error) => fail(s"failed exact Qdrant alias cleanup: $error")
      }
    }
    qdrantTargets.foreach { target =>
      qdrantHttp.delete(s"/collections/$target") match {
        case Right(()) => ()
        case Left(Gen2HttpTransportError.HttpFailure(_, _, 404, _)) => ()
        case Left(error) => fail(s"failed exact Qdrant cleanup for '$target': $error")
      }
    }
  }
}
