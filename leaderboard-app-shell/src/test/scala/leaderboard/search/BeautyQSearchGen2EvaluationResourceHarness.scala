package leaderboard.search

import com.typesafe.config.ConfigFactory
import distage.{Injector, ModuleDef, Scene}
import io.circe.Json
import izumi.distage.config.model.AppConfig
import izumi.distage.model.definition.{Activation, LocatorPrivacy}
import izumi.distage.model.plan.Roots
import izumi.logstage.api.IzLogger
import izumi.logstage.distage.LogIO2Module
import logstage.LogIO2
import leaderboard.config.{ElasticsearchPortCfg, QdrantGen2PortCfg}
import leaderboard.plugins.{ElasticsearchDockerPlugin, QdrantGen2DockerPlugin}
import leaderboard.seed.BeautyQSeedLoader
import leaderboard.search.beautyq.gen2.eval.*
import leaderboard.search.beautyq.gen2.materialization.{BeautyQSearchSnapshot, BeautyQSnapshotFingerprint, BeautyQVariantMaterializer, SnapshotLoadError}
import leaderboard.search.beautyq.gen2.wiring.*
import leaderboard.search.embedding.LlamaCppEmbeddingClientConfig
import leaderboard.search.gen2.core.materialization.{SearchSnapshotSource, SourceRevision, VersionedSnapshot}
import leaderboard.search.gen2.elasticsearch.*
import leaderboard.search.gen2.qdrant.*
import leaderboard.search.gen2.transport.*
import leaderboard.search.gen2.{BeautyQGen2EmbeddingClient, BeautyQSearchGen2Bootstrap, BeautyQSearchGen2Startup}
import zio.{IO, Runtime, Task, Unsafe, ZIO}

import java.net.{HttpURLConnection, URI, URL}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.time.{Clock, Duration, Instant}

/** Test-owned resource mechanics shared by visible and protected evaluation runners. */
object BeautyQSearchGen2EvaluationResourceHarness {
  final case class ManagedPorts(elasticsearch: ElasticsearchPortCfg, qdrant: QdrantGen2PortCfg)
  final case class Prepared(versioned: VersionedSnapshot[BeautyQSearchSnapshot], expectedElasticsearchTarget: String)

  def runProtectedAcceptance(
    protectedCorpusPath: Path,
    protectedPolicyPath: Path,
    outputDir: Path,
  ): String = {
    val visibleCorpus = BeautyQEvaluationCorpus.loadCanonical() match {
      case Right(value) => value
      case Left(error) => return s"PRODUCT_INPUT_REQUIRED: canonical visible corpus unavailable: $error"
    }
    val protectedPolicy = BeautyQProtectedAcceptancePolicy.load(protectedPolicyPath) match {
      case Right(value) => value
      case Left(_) => return "PRODUCT_INPUT_REQUIRED: protected acceptance policy input is missing or invalid"
    }
    val protectedCorpus = BeautyQProtectedEvaluationCorpus.load(protectedCorpusPath, visibleCorpus, protectedPolicy) match {
      case Right(value) => value
      case Left(_) => return "PRODUCT_INPUT_REQUIRED: protected corpus input is missing or invalid"
    }
    val embeddingConfig = LlamaCppEmbeddingClientConfig(
      baseUrl = sys.env.getOrElse("M18_QDRANT_EMBEDDING_ENDPOINT", "http://localhost:8081"),
      endpointPath = "/v1/embeddings",
    )
    val embeddingEndpoint = URI.create(s"${embeddingConfig.baseUrl}${embeddingConfig.endpointPath}").toURL
    if (!probeEmbeddingReachability(embeddingEndpoint))
      return s"VERIFICATION BLOCKED: real embedding endpoint is unreachable at $embeddingEndpoint"

    val prepared = prepareCanonicalSeedEvaluation()
    withManagedPorts { (elasticsearchPort, qdrantPort) =>
      val esClient = buildEsClient(elasticsearchPort)
      val qdrantHttp = buildQdrantHttp(qdrantPort)
      val qdrantClient = QdrantGen2Client.fromTransport(qdrantHttp)
      val embeddingClient = preflightEmbeddingClient(embeddingConfig) match {
        case Right(value) => value
        case Left(error) => throw new IllegalStateException(s"real embedding preflight failed: $error")
      }
      try {
        ensureIsolatedNamespace(esClient, qdrantClient, qdrantHttp)
        val startup = buildStartupFromSnapshot(elasticsearchPort, qdrantClient, embeddingClient, prepared.versioned)
        val elasticsearchVersion = readElasticsearchVersion(esClient.getJson("/") match {
          case Right(value) => value
          case Left(error) => throw new IllegalStateException(s"failed to read Elasticsearch version: $error")
        })
        val qdrantVersion = readQdrantVersion(qdrantHttp.getJson("/") match {
          case Right(value) => value
          case Left(error) => throw new IllegalStateException(s"failed to read Qdrant version: $error")
        })
        val environment = BeautyQEvaluationEnvironment.fromSystem(
          prepared.versioned.capturedAt,
          prepared.versioned.sourceRevision.map(_.value).getOrElse(BeautyQSeedLoader.DefaultResourcePath),
          elasticsearchVersion,
          qdrantVersion,
        ) match {
          case Right(value) => value
          case Left(error) => throw new IllegalStateException(s"invalid evaluation environment: $error")
        }
        val visible = BeautyQMeasuredEvaluation.executeCanonical(startup.application, startup.status, environment) match {
          case Right(value) => value
          case Left(error) => throw new IllegalStateException(s"visible evaluation failed: $error")
        }
        if (!visible.correctionGate.passed)
          throw new IllegalStateException("VISIBLE_ACCEPTANCE_RED: protected execution is not permitted")
        val protectedRun = BeautyQMeasuredEvaluation.executeProtected(
          startup.application,
          startup.status,
          environment,
          protectedCorpus.corpus,
          protectedPolicy.evaluationPolicyVersion,
        ) match {
          case Right(value) => value
          case Left(error) => throw new IllegalStateException(s"protected evaluation failed: ${sanitizedProtectedError(error)}")
        }
        val acceptance = BeautyQProtectedAcceptanceGate.evaluate(visible, protectedRun, protectedCorpus, protectedPolicy)
        Files.createDirectories(outputDir)
        writeArtifact(outputDir.resolve("beautyq-protected-aggregate.json"), protectedRun.protectedReportJson)
        writeArtifact(outputDir.resolve("beautyq-protected-measurement.json"), protectedRun.measurementJson)
        writeArtifact(outputDir.resolve("beautyq-protected-acceptance-gate.json"), acceptance.toJson)
        if (acceptance.passed) "PROTECTED_ACCEPTANCE_EVALUATION_GREEN" else "PROTECTED_ACCEPTANCE_RED"
      } finally {
        cleanupExactResources(esClient, qdrantHttp, Vector(prepared.expectedElasticsearchTarget))
      }
    }
  }

  def withManagedPorts[A](f: (ElasticsearchPortCfg, QdrantGen2PortCfg) => A): A = {
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

  def buildEsClient(port: ElasticsearchPortCfg): ElasticsearchGen2JsonClient = {
    val endpoint = ElasticsearchGen2Endpoint.fromString(s"http://${port.host}:${port.port}").getOrElse(fail("expected managed Elasticsearch endpoint"))
    val transport = ElasticsearchGen2TransportConfig.create(endpoint, Duration.ofSeconds(5), Duration.ofSeconds(60)).getOrElse(fail("expected Elasticsearch transport"))
    ElasticsearchGen2JsonClient.jdk(transport)
  }

  def buildQdrantHttp(port: QdrantGen2PortCfg): Gen2JsonHttpClient = {
    val endpoint = Gen2HttpEndpoint.fromString(s"http://${port.host}:${port.port}").getOrElse(fail("expected managed Qdrant endpoint"))
    val transport = Gen2HttpTransportConfig.create(endpoint, Duration.ofSeconds(5), Duration.ofSeconds(60)).getOrElse(fail("expected Qdrant transport"))
    Gen2JsonHttpClient.jdk(transport)
  }

  def preflightEmbeddingClient(config: LlamaCppEmbeddingClientConfig): Either[String, BeautyQGen2EmbeddingClient] = {
    val endpoint = Gen2HttpEndpoint.fromString(config.baseUrl).left.map(error => s"embedding endpoint must be valid: $error")
    endpoint.flatMap { value =>
      Gen2HttpTransportConfig.create(value, Duration.ofSeconds(5), Duration.ofSeconds(60)) match {
        case Left(error) => Left(s"real embedding transport config must be valid: $error")
        case Right(transport) =>
          val client = BeautyQGen2EmbeddingClient.fromTransport(Gen2JsonHttpClient.jdk(transport), config.endpointPath)
          val input = QdrantEmbeddingInput.from(
            QdrantEmbeddingPurpose.CandidateQuery,
            "beauty-search-gen2-protected-preflight",
            "beauty-search-gen2-protected-preflight",
            BeautyQQdrantPolicy.policy.embeddingModel,
          ).getOrElse(fail("expected preflight embedding input"))
          client.embed(input) match {
            case Right(result) if result.model.dimension == BeautyQQdrantPolicy.policy.embeddingModel.dimension => Right(client)
            case Right(result) => Left(s"embedding dimension mismatch: ${result.model.dimension}")
            case Left(BeautyQEmbeddingRequestError.Unavailable(message)) => Left(s"embedding endpoint unavailable: $message")
            case Left(error) => Left(s"embedding preflight failed: $error")
          }
      }
    }
  }

  def probeEmbeddingReachability(endpoint: URL): Boolean = endpoint.openConnection() match {
    case http: HttpURLConnection =>
      http.setConnectTimeout(2000)
      http.setReadTimeout(2000)
      http.setRequestMethod("GET")
      try {
        http.connect()
        val code = http.getResponseCode
        code >= 200 && code < 500
      } catch { case _: java.io.IOException => false }
      finally http.disconnect()
    case _ => false
  }

  def buildStartupFromSnapshot(
    elasticsearchPort: ElasticsearchPortCfg,
    qdrantClient: QdrantGen2Client,
    embedding: QdrantQueryEmbeddingPort[BeautyQEmbeddingRequestError],
    versioned: VersionedSnapshot[BeautyQSearchSnapshot],
  ): BeautyQSearchGen2Startup = {
    val esClient = buildEsClient(elasticsearchPort)
    val batching = ElasticsearchBulkBatchingPolicy.create(100, 1024L * 1024L).getOrElse(fail("expected batching"))
    val elasticsearch = BeautyQElasticsearchBaselineService.make(esClient, Clock.systemUTC(), batching).getOrElse(fail("expected BeautyQ baseline service"))
    val qdrantLifecycle = BeautyQQdrantRuntime.lifecycle(qdrantClient).getOrElse(fail("expected BeautyQ Qdrant lifecycle"))
    val candidateService = BeautyQQdrantRuntime.candidateService(qdrantClient).getOrElse(fail("expected BeautyQ Qdrant candidate service"))
    val source = new SearchSnapshotSource[IO, SnapshotLoadError, BeautyQSearchSnapshot] {
      def load: IO[SnapshotLoadError, VersionedSnapshot[BeautyQSearchSnapshot]] = ZIO.succeed(versioned)
    }
    val materializer = new BeautyQVariantMaterializer.FromSnapshotSource[IO](source)
    val bootstrap = BeautyQSearchGen2Bootstrap.make(materializer, elasticsearch, SupplementStartupPolicy.Required, qdrantLifecycle, embedding)
    val log = LogIO2.fromLogger[IO](IzLogger())
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(BeautyQSearchGen2Startup.acquire(bootstrap, candidateService, log)).getOrThrowFiberFailure()
    }
  }

  def prepareCanonicalSeedEvaluation(): Prepared = {
    val seed = new BeautyQSeedLoader.ResourceLoader().load().getOrElse(fail("failed to load canonical BeautyQ seed"))
    val snapshot = BeautyQSearchSnapshot(
      categories = seed.categories.toVector,
      services = seed.services.toVector,
      serviceVariantSchemas = seed.serviceVariantSchemas.toVector,
      masters = seed.masters.toVector,
      masterLocations = seed.masterLocations.toVector,
      masterServiceOffers = seed.masterServiceOffers.toVector,
      masterServiceOfferVariants = seed.masterServiceOfferVariants.toVector,
    )
    val versioned = VersionedSnapshot(snapshot, BeautyQSnapshotFingerprint.compute(snapshot), Some(SourceRevision(BeautyQSeedLoader.DefaultResourcePath)), Instant.now())
    val source = new SearchSnapshotSource[IO, SnapshotLoadError, BeautyQSearchSnapshot] {
      def load: IO[SnapshotLoadError, VersionedSnapshot[BeautyQSearchSnapshot]] = ZIO.succeed(versioned)
    }
    val materialized = Unsafe.unsafe { implicit unsafe => Runtime.default.unsafe.run(new BeautyQVariantMaterializer.FromSnapshotSource[IO](source).load).getOrThrowFiberFailure() }
    val generation = BeautyQElasticsearchGeneration.compile(materialized).getOrElse(fail("failed to compile canonical seed ES generation"))
    val target = ElasticsearchGenerationNaming.physicalIndexName(BeautyQSearchGen2ResourceNames.ElasticsearchPhysicalIndexPrefix, generation.identity).map(_.value).getOrElse(fail("expected deterministic canonical seed target"))
    Prepared(versioned, target)
  }

  def readElasticsearchVersion(json: Json): String = json.hcursor.downField("version").get[String]("number").getOrElse(fail("malformed Elasticsearch version response"))
  def readQdrantVersion(json: Json): String = json.hcursor.get[String]("version").getOrElse(fail("malformed Qdrant version response"))

  def ensureIsolatedNamespace(esClient: ElasticsearchGen2JsonClient, qdrantClient: QdrantGen2Client, qdrantHttp: Gen2JsonHttpClient): Unit = {
    val aliases = Vector(BeautyQSearchGen2ResourceNames.ElasticsearchAlias, s"${BeautyQSearchGen2ResourceNames.ElasticsearchAlias}--superseded").flatMap { alias =>
      BeautyQSearchGen2ResourceSupport.readElasticsearchAliasTargets(esClient, alias).getOrElse(fail(s"could not establish Elasticsearch alias isolation for '$alias'"))
    }
    if (aliases.nonEmpty) fail("reserved Elasticsearch Gen2 aliases are not empty")
    esClient.getJson(s"/${BeautyQSearchGen2ResourceNames.ElasticsearchPhysicalIndexPrefix}*/_mapping") match {
      case Right(json) if json.asObject.exists(_.nonEmpty) => fail("reserved Elasticsearch Gen2 physical namespace is not empty")
      case Right(_) | Left(ElasticsearchGen2TransportError.HttpFailure(_, _, 404, _)) => ()
      case Left(_) => fail("could not establish Elasticsearch namespace isolation")
    }
    val qdrantAliases = BeautyQSearchGen2ResourceSupport.readQdrantAliases(qdrantClient).getOrElse(fail("could not establish Qdrant alias isolation"))
    if (BeautyQSearchGen2ResourceSupport.reservedQdrantAliasTargets(qdrantAliases).nonEmpty) fail("reserved Qdrant Gen2 alias is not empty")
    val names = qdrantHttp.getJson("/collections").flatMap(BeautyQSearchGen2ResourceSupport.decodeQdrantCollectionNames).getOrElse(fail("could not decode Qdrant collection inventory"))
    if (names.exists(_.startsWith(BeautyQSearchGen2ResourceNames.QdrantPhysicalCollectionPrefix))) fail("reserved Qdrant Gen2 physical namespace is not empty")
  }

  def cleanupExactResources(esClient: ElasticsearchGen2JsonClient, qdrantHttp: Gen2JsonHttpClient, expectedEsTargets: Vector[String]): Unit = {
    val esTargets = Vector(BeautyQSearchGen2ResourceNames.ElasticsearchAlias, s"${BeautyQSearchGen2ResourceNames.ElasticsearchAlias}--superseded").flatMap { alias =>
      BeautyQSearchGen2ResourceSupport.readElasticsearchAliasTargets(esClient, alias).getOrElse(fail(s"failed to read exact ES alias '$alias' during teardown"))
    }.filter(_.startsWith(BeautyQSearchGen2ResourceNames.ElasticsearchPhysicalIndexPrefix)) ++ expectedEsTargets
    esTargets.distinct.sorted.foreach { target =>
      esClient.delete(s"/$target") match {
        case Right(()) | Left(ElasticsearchGen2TransportError.HttpFailure(_, _, 404, _)) => ()
        case Left(_) => fail(s"failed exact ES cleanup for '$target'")
      }
    }
    val qdrantClient = QdrantGen2Client.fromTransport(qdrantHttp)
    val aliases = BeautyQSearchGen2ResourceSupport.readQdrantAliases(qdrantClient).getOrElse(fail("failed to read exact Qdrant aliases during teardown"))
    val aliasTargets = BeautyQSearchGen2ResourceSupport.reservedQdrantAliasTargets(aliases)
    if (aliasTargets.exists(!_.startsWith(BeautyQSearchGen2ResourceNames.QdrantPhysicalCollectionPrefix))) fail("refusing to delete a foreign Qdrant alias target")
    val collections = qdrantHttp.getJson("/collections").flatMap(BeautyQSearchGen2ResourceSupport.decodeQdrantCollectionNames).getOrElse(fail("failed to read Qdrant collections during teardown"))
    if (aliasTargets.nonEmpty) qdrantClient.updateAliases(Json.obj("actions" -> Json.arr(Json.obj("delete_alias" -> Json.obj("alias_name" -> Json.fromString(BeautyQSearchGen2ResourceNames.QdrantCollectionAlias)))))) match {
      case Right(_) | Left(Gen2HttpTransportError.HttpFailure(_, _, 404, _)) => ()
      case Left(_) => fail("failed exact Qdrant alias cleanup")
    }
    (aliasTargets ++ collections.filter(_.startsWith(BeautyQSearchGen2ResourceNames.QdrantPhysicalCollectionPrefix))).distinct.sorted.foreach { target =>
      qdrantHttp.delete(s"/collections/$target") match {
        case Right(()) | Left(Gen2HttpTransportError.HttpFailure(_, _, 404, _)) => ()
        case Left(_) => fail(s"failed exact Qdrant cleanup for '$target'")
      }
    }
  }

  def writeArtifact(path: Path, body: Json): Unit = {
    Option(path.getParent).foreach(parent => Files.createDirectories(parent))
    Files.writeString(path, body.spaces2, StandardCharsets.UTF_8)
    ()
  }

  def sanitizedProtectedError(error: leaderboard.search.beautyq.gen2.eval.BeautyQEvaluationExecutionError): String = error match {
    case leaderboard.search.beautyq.gen2.eval.BeautyQEvaluationExecutionError.Request(_, _, _) => "protected_request_failed"
    case leaderboard.search.beautyq.gen2.eval.BeautyQEvaluationExecutionError.Application(_, _, _, _) => "protected_application_failed"
    case leaderboard.search.beautyq.gen2.eval.BeautyQEvaluationExecutionError.Projection(_, _, _, _) => "protected_projection_failed"
    case leaderboard.search.beautyq.gen2.eval.BeautyQEvaluationExecutionError.Evidence(_, _, _, _) => "protected_evidence_failed"
    case leaderboard.search.beautyq.gen2.eval.BeautyQEvaluationExecutionError.Identity(_, _, _, _, _, _) => "protected_identity_failed"
    case leaderboard.search.beautyq.gen2.eval.BeautyQEvaluationExecutionError.RankingInput(_, _, _, _, _) => "protected_ranking_input_failed"
    case leaderboard.search.beautyq.gen2.eval.BeautyQEvaluationExecutionError.ReportInput(_, _, _, _) => "protected_report_input_failed"
    case leaderboard.search.beautyq.gen2.eval.BeautyQEvaluationExecutionError.InvalidDuration(_, _, _, _) => "protected_duration_invalid"
    case leaderboard.search.beautyq.gen2.eval.BeautyQEvaluationExecutionError.NonDeterministicObservation(_, _, _, _, _, _, _) => "protected_determinism_failed"
    case leaderboard.search.beautyq.gen2.eval.BeautyQEvaluationExecutionError.Environment(_) => "protected_environment_failed"
    case leaderboard.search.beautyq.gen2.eval.BeautyQEvaluationExecutionError.Provenance(_) => "protected_provenance_failed"
    case leaderboard.search.beautyq.gen2.eval.BeautyQEvaluationExecutionError.Corpus(_) => "protected_provenance_failed"
    case leaderboard.search.beautyq.gen2.eval.BeautyQEvaluationExecutionError.Startup(_, _) => "protected_environment_failed"
  }

  private def fail(message: String): Nothing = throw new IllegalStateException(message)
}
