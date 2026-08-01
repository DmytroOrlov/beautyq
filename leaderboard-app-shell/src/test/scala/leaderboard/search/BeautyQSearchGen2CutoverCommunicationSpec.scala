package leaderboard.search

import io.circe.Json
import izumi.logstage.api.IzLogger
import logstage.LogIO2
import leaderboard.config.{ElasticsearchPortCfg, QdrantGen2PortCfg}
import leaderboard.search.beautyq.gen2.eval.{BeautyQCutoverGate, BeautyQCutoverQueryFixture, BeautyQCutoverQueryObservation, BeautyQEvaluationEnvironment, BeautyQEvaluationExecutionError, BeautyQGen1SearchDeletionInventory, BeautyQMeasuredEvaluation, BeautyQNoHarmSupplementEvidence}
import leaderboard.search.beautyq.gen2.materialization.{BeautyQSearchSnapshot, BeautyQSnapshotFingerprint, BeautyQVariantMaterializer, SnapshotLoadError}
import leaderboard.seed.BeautyQSeedLoader
import leaderboard.search.beautyq.gen2.wiring.*
import leaderboard.search.embedding.LlamaCppEmbeddingClientConfig
import leaderboard.search.gen2.core.materialization.{SearchSnapshotSource, SourceRevision, VersionedSnapshot}
import leaderboard.search.gen2.elasticsearch.*
import leaderboard.search.gen2.qdrant.*
import leaderboard.search.gen2.transport.*
import leaderboard.search.gen2.{BeautyQGen2EmbeddingClient, BeautyQSearchGen2Bootstrap, BeautyQSearchGen2Startup}
import zio.{IO, Runtime, Unsafe, ZIO}

import java.net.{HttpURLConnection, URI, URL}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import java.time.{Clock, Duration, Instant}

/** Real four-query BeautyQ Gen2 cutover communication spec.
  *
  * The runner acquires one Distage-managed Elasticsearch and one shared Distage-managed Qdrant
  * 1.18.3 process, executes the real `BeautyQGen2EmbeddingClient` preflight against the
  * configured `LlamaCppEmbeddingClientConfig`, acquires one `BeautyQSearchGen2Startup`
  * exactly once, requires its `startup.status` to be `FullSearch`, then projects
  * the four typed fixtures through the same application in declared order, derives
  * each observation via `fromExecution`, evaluates the readiness-aware gate, and
  * writes the deterministic cutover and deletion-inventory reports to the
  * ignored `target/search-gen2/` tree.
  *
  * The spec never invokes the HTTP route, never synthesises IDs, never rebuilds
  * result IDs and never executes a second baseline search for comparison. */
final class BeautyQSearchGen2CutoverCommunicationSpec extends org.scalatest.wordspec.AnyWordSpec {

  "managed BeautyQ Gen2 cutover" should {
    "execute the four typed fixtures through one startup and pass the readiness-aware gate" in {
      val embeddingConfig = LlamaCppEmbeddingClientConfig(
        baseUrl = sys.env.getOrElse("M18_QDRANT_EMBEDDING_ENDPOINT", "http://localhost:8081"),
        endpointPath = "/v1/embeddings",
      )
      val embeddingEndpoint = URI.create(s"${embeddingConfig.baseUrl}${embeddingConfig.endpointPath}").toURL
      if (!probeEmbeddingReachability(embeddingEndpoint)) {
        cancel(
          s"VERIFICATION BLOCKED: real BeautyQ Gen2 embedding endpoint is unreachable at $embeddingEndpoint; " +
            s"cannot execute the four-query cutover gate."
        )
      }

      withManagedPorts { (elasticsearchPort, qdrantPort) =>
        val esClient = buildEsClient(elasticsearchPort)
        val qdrantHttp = buildQdrantHttp(qdrantPort)
        val qdrantClient = QdrantGen2Client.fromTransport(qdrantHttp)
        val embeddingClient: BeautyQGen2EmbeddingClient = preflightEmbeddingClient(embeddingConfig) match {
          case Right(value) => value
          case Left(error)  => fail(s"real embedding preflight failed: $error")
        }

        val materialized = BeautyQElasticsearchTestFixtures.materialized
        val generation: CompiledBeautyQElasticsearchGeneration = BeautyQElasticsearchGeneration.compile(materialized) match {
          case Right(value) => value
          case Left(error)  => fail(s"expected ES generation, got $error")
        }
        val expectedEsTargets = ElasticsearchGenerationNaming
          .physicalIndexName(BeautyQSearchGen2ResourceNames.ElasticsearchPhysicalIndexPrefix, generation.identity)
          .map(_.value)
          .getOrElse(fail("expected deterministic ES target for cleanup"))

        try {
          ensureIsolatedNamespace(esClient, qdrantClient, qdrantHttp)
          val startup = buildStartup(elasticsearchPort, qdrantClient, embeddingClient)
          startup.status.servingMode match {
            case BeautyQServingMode.FullSearch =>
              ()
            case other => fail(s"expected FullSearch readiness, got $other")
          }

          val (gate, report) = runCutover(startup)
          writeArtifact("target/search-gen2/beautyq-cutover-report.json", report)
          writeArtifact(
            "target/search-gen2/beautyq-gen1-deletion-inventory.json",
            BeautyQGen1SearchDeletionInventory.toJson,
          )
          if (!gate.passed) {
            val failed = gate.checks.filterNot(_.passed).map(check => s"${check.id} observed=${check.observed} expected=${check.expected}")
            fail(s"cutover gate failed: ${failed.mkString("; ")}")
          }
        } finally {
          cleanupExactResources(esClient, qdrantHttp, Vector(expectedEsTargets))
        }
      }
    }

    "execute all 89 canonical regression cases through one Required startup and write measured correction evidence" in {
      val embeddingConfig = LlamaCppEmbeddingClientConfig(
        baseUrl = sys.env.getOrElse("M18_QDRANT_EMBEDDING_ENDPOINT", "http://localhost:8081"),
        endpointPath = "/v1/embeddings",
      )
      val embeddingEndpoint = URI.create(s"${embeddingConfig.baseUrl}${embeddingConfig.endpointPath}").toURL
      if (!probeEmbeddingReachability(embeddingEndpoint)) {
        cancel(
          s"VERIFICATION BLOCKED: real BeautyQ Gen2 embedding endpoint is unreachable at $embeddingEndpoint; " +
            s"cannot execute the 89-case Q2 evaluation."
        )
      }

      val prepared = prepareCanonicalSeedEvaluation()
      withManagedPorts { (elasticsearchPort, qdrantPort) =>
        val esClient = buildEsClient(elasticsearchPort)
        val qdrantHttp = buildQdrantHttp(qdrantPort)
        val qdrantClient = QdrantGen2Client.fromTransport(qdrantHttp)
        val embeddingClient = preflightEmbeddingClient(embeddingConfig) match {
          case Right(value) => value
          case Left(error)  => fail(s"real embedding preflight failed: $error")
        }

        try {
          ensureIsolatedNamespace(esClient, qdrantClient, qdrantHttp)
          val startup = buildStartupFromSnapshot(
            elasticsearchPort,
            qdrantClient,
            embeddingClient,
            prepared.versioned,
          )
          assert(startup.status.policy == SupplementStartupPolicy.Required)
          assert(startup.status.servingMode == BeautyQServingMode.FullSearch)
          assert(startup.status.supplementReady)
          assert(!startup.status.restartRequired)

          val elasticsearchVersion = decodeElasticsearchVersion(esClient.getJson("/") match {
            case Right(value) => value
            case Left(error)  => fail(s"failed to read Elasticsearch version: $error")
          })
          val qdrantVersion = decodeQdrantVersion(qdrantHttp.getJson("/") match {
            case Right(value) => value
            case Left(error)  => fail(s"failed to read Qdrant version: $error")
          })
          assert(qdrantVersion == "1.18.3")

          val environment = BeautyQEvaluationEnvironment.fromSystem(
            prepared.versioned.capturedAt,
            prepared.versioned.sourceRevision.map(_.value).getOrElse(BeautyQSeedLoader.DefaultResourcePath),
            elasticsearchVersion,
            qdrantVersion,
          ) match {
            case Right(value) => value
            case Left(error)  => fail(s"invalid Q2 evaluation environment: $error")
          }
          val measured = BeautyQMeasuredEvaluation.executeCanonical(
            startup.application,
            startup.status,
            environment,
          ) match {
            case Right(value) => value
            case Left(error)  => fail(renderEvaluationError(error))
          }

          writeArtifact("target/search-gen2/beautyq-evaluation-detailed.json", measured.detailedJson)
          writeArtifact("target/search-gen2/beautyq-evaluation-measurement.json", measured.measurementJson)
          writeArtifact("target/search-gen2/beautyq-evaluation-score-separation.json", measured.scoreSeparationJson)
          writeArtifact("target/search-gen2/beautyq-evaluation-correction-gate.json", measured.correctionGate.toJson)

          assert(measured.warmupExecutions == 89)
          assert(measured.measuredExecutions == 267)
          if (!measured.correctionGate.passed) {
            val failed = measured.correctionGate.checks.filterNot(_.passed)
            val separation = measured.correctionGate.supplementScoreSeparation
            val rendered = failed.map { check =>
              s"${check.stableCode} observed=${check.observed} expected=${check.expected}"
            }
            if (
              failed.map(_.stableCode) == Vector("no-forbidden-hits") &&
              separation.forbiddenCount > 0 &&
              separation.nonForbiddenCount > 0 &&
              !separation.strictlySeparable
            ) {
              val observations = scoreObservations(measured.scoreSeparationJson)
              val maximumForbidden = stableMaximum(
                observations.filter(_.judgment == "forbidden"),
              ).getOrElse(fail("QUALITY_CORRECTION_DECISION_REQUIRED: missing forbidden witness"))
              val minimumNonForbidden = stableMinimum(
                observations.filter(value => Set("acceptable", "neutral", "unjudged").contains(value.judgment)),
              ).getOrElse(fail("QUALITY_CORRECTION_DECISION_REQUIRED: missing non-forbidden witness"))
              fail(
                s"QUALITY_CORRECTION_DECISION_REQUIRED: ${rendered.mkString("; ")}; " +
                  s"forbiddenScoreRange=${separation.minimumForbidden}..${separation.maximumForbidden}; " +
                  s"nonForbiddenScoreRange=${separation.minimumNonForbidden}..${separation.maximumNonForbidden}; " +
                  s"maximumForbidden:\n  caseId=${maximumForbidden.caseId}\n  resultId=${maximumForbidden.resultId}\n  " +
                  s"origin=${maximumForbidden.origin}\n  score=${maximumForbidden.score}; " +
                  s"minimumNonForbidden:\n  caseId=${minimumNonForbidden.caseId}\n  " +
                  s"resultId=${minimumNonForbidden.resultId}\n  origin=${minimumNonForbidden.origin}\n  " +
                  s"score=${minimumNonForbidden.score}; " +
                  s"inequality=${maximumForbidden.score} >= ${minimumNonForbidden.score}; " +
                  "overlap=true; no single threshold is safe"
              )
            } else {
              fail(s"QUALITY_GATE_RED: ${rendered.mkString("; ")}")
            }
          }
        } finally {
          cleanupExactResources(esClient, qdrantHttp, Vector(prepared.expectedElasticsearchTarget))
        }
      }
    }
  }

  private def runCutover(
    startup: BeautyQSearchGen2Startup,
  ): (leaderboard.search.beautyq.gen2.eval.BeautyQCutoverGateResult, Json) = {
    val application = startup.application
    val observations = BeautyQCutoverQueryFixture.required.map { fixture =>
      val request = fixture.request
      val result: BeautyQSearchOrchestrator.Result = application.execute(request) match {
        case Right(value) => value
        case Left(error)  => failCutover(fixture, s"application execution failed: $error")
      }
      val response: BeautyQSearchResponseGen2 = BeautyQSearchResponseGen2Projector.project(result, startup.status) match {
        case Right(value) => value
        case Left(error)  => failCutover(fixture, s"projection failed: $error")
      }
      val evidence: BeautyQNoHarmSupplementEvidence.SupplementEvidence = BeautyQNoHarmSupplementEvidence.fromExecution(result, response) match {
        case Right(value) => value
        case Left(error)  => failCutover(fixture, s"evidence derivation failed: $error")
      }
      BeautyQCutoverQueryObservation.fromEvidence(fixture, evidence)
    }
    val gate = BeautyQCutoverGate.evaluate(startup.status.servingMode, observations)
    (gate, gate.toJson)
  }

  private final case class ScoreWitness(
    caseId: String,
    resultId: String,
    origin: String,
    score: BigDecimal,
    judgment: String,
  )

  private def scoreObservations(artifact: Json): Vector[ScoreWitness] = {
    artifact.hcursor.downField("visibleCases").values.getOrElse(Vector.empty).flatMap { current =>
      val caseId = current.hcursor.get[String]("caseId").getOrElse(fail("score artifact caseId is malformed"))
      current.hcursor.downField("observations").values.getOrElse(Vector.empty).map { observation =>
        ScoreWitness(
          caseId,
          observation.hcursor.get[String]("resultId").getOrElse(fail("score artifact resultId is malformed")),
          observation.hcursor.get[String]("origin").getOrElse(fail("score artifact origin is malformed")),
          observation.hcursor.get[BigDecimal]("score").getOrElse(fail("score artifact score is malformed")),
          observation.hcursor.get[String]("judgment").getOrElse(fail("score artifact judgment is malformed")),
        )
      }
    }.filter(_.origin == "qdrant_supplement").toVector
  }

  private def stableMaximum(values: Vector[ScoreWitness]): Option[ScoreWitness] =
    values.foldLeft(Option.empty[ScoreWitness]) {
      case (None, value) => Some(value)
      case (Some(current), value) if value.score > current.score => Some(value)
      case (Some(current), _) => Some(current)
    }

  private def stableMinimum(values: Vector[ScoreWitness]): Option[ScoreWitness] =
    values.foldLeft(Option.empty[ScoreWitness]) {
      case (None, value) => Some(value)
      case (Some(current), value) if value.score < current.score => Some(value)
      case (Some(current), _) => Some(current)
    }

  private def failCutover(
    fixture: BeautyQCutoverQueryFixture,
    message: String,
  ): Nothing =
    fail(
      s"fixture=${fixture.stableId} query=${fixture.query} $message"
    )

  private def buildStartup(
    elasticsearchPort: ElasticsearchPortCfg,
    qdrantClient: QdrantGen2Client,
    embedding: QdrantQueryEmbeddingPort[BeautyQEmbeddingRequestError],
  ): BeautyQSearchGen2Startup = {
    val versioned = VersionedSnapshot(
      value = BeautyQElasticsearchTestFixtures.snapshot,
      contentFingerprint = BeautyQSnapshotFingerprint.compute(BeautyQElasticsearchTestFixtures.snapshot),
      sourceRevision = Some(SourceRevision("cutover-communication-spec")),
      capturedAt = Instant.now(),
    )
    buildStartupFromSnapshot(elasticsearchPort, qdrantClient, embedding, versioned)
  }

  private def buildStartupFromSnapshot(
    elasticsearchPort: ElasticsearchPortCfg,
    qdrantClient: QdrantGen2Client,
    embedding: QdrantQueryEmbeddingPort[BeautyQEmbeddingRequestError],
    versioned: VersionedSnapshot[BeautyQSearchSnapshot],
  ): BeautyQSearchGen2Startup = {
    val esClient = buildEsClient(elasticsearchPort)
    val batching = ElasticsearchBulkBatchingPolicy.create(100, 1024L * 1024L).getOrElse(fail("expected batching"))
    val elasticsearch = BeautyQElasticsearchBaselineService
      .make(esClient, Clock.systemUTC(), batching)
      .getOrElse(fail("expected BeautyQ baseline service"))
    val qdrantLifecycle = BeautyQQdrantRuntime.lifecycle(qdrantClient).getOrElse(fail("expected BeautyQ Qdrant lifecycle"))
    val candidateService = BeautyQQdrantRuntime.candidateService(qdrantClient).getOrElse(fail("expected BeautyQ Qdrant candidate service"))
    val source = new SearchSnapshotSource[IO, SnapshotLoadError, BeautyQSearchSnapshot] {
      def load: IO[SnapshotLoadError, VersionedSnapshot[BeautyQSearchSnapshot]] =
        ZIO.succeed(versioned)
    }
    val materializer = new BeautyQVariantMaterializer.FromSnapshotSource[IO](source)
    val bootstrap = BeautyQSearchGen2Bootstrap.make(materializer, elasticsearch, SupplementStartupPolicy.Required, qdrantLifecycle, embedding)
    val log = LogIO2.fromLogger[IO](IzLogger())
    val acquired: BeautyQSearchGen2Startup = Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(BeautyQSearchGen2Startup.acquire(bootstrap, candidateService, log)).getOrThrowFiberFailure()
    }
    acquired
  }

  private final case class PreparedCanonicalSeedEvaluation(
    versioned: VersionedSnapshot[BeautyQSearchSnapshot],
    expectedElasticsearchTarget: String,
  )

  private def prepareCanonicalSeedEvaluation(): PreparedCanonicalSeedEvaluation = {
    val seed = new BeautyQSeedLoader.ResourceLoader().load() match {
      case Right(value) => value
      case Left(error)  => fail(s"failed to load canonical BeautyQ seed: ${error.message}")
    }
    val snapshot = BeautyQSearchSnapshot(
      categories = seed.categories.toVector,
      services = seed.services.toVector,
      serviceVariantSchemas = seed.serviceVariantSchemas.toVector,
      masters = seed.masters.toVector,
      masterLocations = seed.masterLocations.toVector,
      masterServiceOffers = seed.masterServiceOffers.toVector,
      masterServiceOfferVariants = seed.masterServiceOfferVariants.toVector,
    )
    val versioned = VersionedSnapshot(
      value = snapshot,
      contentFingerprint = BeautyQSnapshotFingerprint.compute(snapshot),
      sourceRevision = Some(SourceRevision(BeautyQSeedLoader.DefaultResourcePath)),
      capturedAt = Instant.now(),
    )
    val source = new SearchSnapshotSource[IO, SnapshotLoadError, BeautyQSearchSnapshot] {
      def load: IO[SnapshotLoadError, VersionedSnapshot[BeautyQSearchSnapshot]] = ZIO.succeed(versioned)
    }
    val materialized = Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(new BeautyQVariantMaterializer.FromSnapshotSource[IO](source).load).getOrThrowFiberFailure()
    }
    val generation = BeautyQElasticsearchGeneration.compile(materialized) match {
      case Right(value) => value
      case Left(error)  => fail(s"failed to compile canonical seed ES generation: $error")
    }
    val expectedTarget = ElasticsearchGenerationNaming
      .physicalIndexName(BeautyQSearchGen2ResourceNames.ElasticsearchPhysicalIndexPrefix, generation.identity)
      .map(_.value)
      .getOrElse(fail("expected deterministic canonical seed ES target"))
    PreparedCanonicalSeedEvaluation(versioned, expectedTarget)
  }

  private def decodeElasticsearchVersion(json: Json): String =
    json.hcursor.downField("version").get[String]("number") match {
      case Right(value) if value.nonEmpty => value
      case Right(_) => fail("Elasticsearch version must be non-empty")
      case Left(error) => fail(s"malformed Elasticsearch version response: ${error.message}")
    }

  private def decodeQdrantVersion(json: Json): String =
    json.hcursor.get[String]("version") match {
      case Right(value) if value.nonEmpty => value
      case Right(_) => fail("Qdrant version must be non-empty")
      case Left(error) => fail(s"malformed Qdrant version response: ${error.message}")
    }

  private def renderEvaluationError(error: BeautyQEvaluationExecutionError): String = error match {
    case BeautyQEvaluationExecutionError.Application(caseId, query, pass, cause) =>
      s"case=$caseId query=$query pass=$pass phase=application error=$cause"
    case BeautyQEvaluationExecutionError.Projection(caseId, query, pass, cause) =>
      s"case=$caseId query=$query pass=$pass phase=projection error=$cause"
    case BeautyQEvaluationExecutionError.Evidence(caseId, query, pass, cause) =>
      s"case=$caseId query=$query pass=$pass phase=no-harm-evidence error=$cause"
    case BeautyQEvaluationExecutionError.Identity(caseId, query, pass, surface, value, message) =>
      s"case=$caseId query=$query pass=$pass phase=identity surface=$surface value=$value error=$message"
    case BeautyQEvaluationExecutionError.RankingInput(caseId, query, pass, surface, message) =>
      s"case=$caseId query=$query pass=$pass phase=ranking-input surface=$surface error=$message"
    case BeautyQEvaluationExecutionError.ReportInput(caseId, query, pass, message) =>
      s"case=$caseId query=$query pass=$pass phase=report-input error=$message"
    case BeautyQEvaluationExecutionError.Request(caseId, query, message) =>
      s"case=$caseId query=$query phase=request error=$message"
    case BeautyQEvaluationExecutionError.InvalidDuration(caseId, query, pass, nanos) =>
      s"case=$caseId query=$query pass=$pass phase=timing error=negative-duration-$nanos"
    case BeautyQEvaluationExecutionError.NonDeterministicObservation(caseId, query, component, expectedPass, actualPass, expected, actual) =>
      s"case=$caseId query=$query phase=determinism component=$component expectedPass=$expectedPass actualPass=$actualPass expected=${expected.mkString(",")} actual=${actual.mkString(",")}"
    case other => s"Q2 evaluation failed: $other"
  }

  private def preflightEmbeddingClient(
    config: LlamaCppEmbeddingClientConfig,
  ): Either[String, BeautyQGen2EmbeddingClient] = {
    val baseUrl = s"${config.baseUrl}"
    val httpEndpoint: Gen2HttpEndpoint = Gen2HttpEndpoint.fromString(baseUrl) match {
      case Right(value) => value
      case Left(error)  => return Left(s"embedding endpoint must be valid: $error")
    }
    val transport: Gen2HttpTransportConfig = Gen2HttpTransportConfig.create(httpEndpoint, Duration.ofSeconds(5), Duration.ofSeconds(60)) match {
      case Right(value) => value
      case Left(error)  => return Left(s"real embedding transport config must be valid: $error")
    }
    val http: Gen2JsonHttpClient = Gen2JsonHttpClient.jdk(transport)
    val client: BeautyQGen2EmbeddingClient = BeautyQGen2EmbeddingClient.fromTransport(http, config.endpointPath)
    val probeInput: QdrantEmbeddingInput = QdrantEmbeddingInput.from(
      QdrantEmbeddingPurpose.CandidateQuery,
      "beauty-search-gen2-cutover-preflight",
      "beauty-search-gen2-cutover-preflight",
      BeautyQQdrantPolicy.policy.embeddingModel,
    ).getOrElse(fail("expected preflight embedding input"))
    client.embed(probeInput) match {
      case Right(probeResult) =>
        if (probeResult.model.dimension != BeautyQQdrantPolicy.policy.embeddingModel.dimension) {
          Left(
            s"real embedding dimension must match policy: " +
              s"actual=${probeResult.model.dimension} expected=${BeautyQQdrantPolicy.policy.embeddingModel.dimension}"
          )
        } else Right(client)
      case Left(BeautyQEmbeddingRequestError.Unavailable(message)) =>
        Left(s"real BeautyQ Gen2 embedding endpoint unreachable: $message")
      case Left(error) =>
        Left(s"real BeautyQ Gen2 embedding preflight failed: $error")
    }
  }

  private def buildEsClient(port: ElasticsearchPortCfg): ElasticsearchGen2JsonClient = {
    val endpoint = ElasticsearchGen2Endpoint
      .fromString(s"http://${port.host}:${port.port}")
      .getOrElse(fail("expected managed Elasticsearch endpoint"))
    val transport = ElasticsearchGen2TransportConfig
      .create(endpoint, Duration.ofSeconds(5), Duration.ofSeconds(60))
      .getOrElse(fail("expected Elasticsearch transport"))
    ElasticsearchGen2JsonClient.jdk(transport)
  }

  private def buildQdrantHttp(port: QdrantGen2PortCfg): Gen2JsonHttpClient = {
    val endpoint = Gen2HttpEndpoint
      .fromString(s"http://${port.host}:${port.port}")
      .getOrElse(fail("expected managed Qdrant endpoint"))
    val transport = Gen2HttpTransportConfig
      .create(endpoint, Duration.ofSeconds(5), Duration.ofSeconds(60))
      .getOrElse(fail("expected Qdrant transport"))
    Gen2JsonHttpClient.jdk(transport)
  }

  private def probeEmbeddingReachability(endpoint: URL): Boolean = endpoint.openConnection() match {
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

  private def withManagedPorts[A](f: (ElasticsearchPortCfg, QdrantGen2PortCfg) => A): A = {
    BeautyQSearchGen2EvaluationResourceHarness.withManagedPorts(f)
  }

  private def ensureIsolatedNamespace(
    esClient: ElasticsearchGen2JsonClient,
    qdrantClient: QdrantGen2Client,
    qdrantHttp: Gen2JsonHttpClient,
  ): Unit = {
    val aliases = Vector(
      BeautyQSearchGen2ResourceNames.ElasticsearchAlias,
      s"${BeautyQSearchGen2ResourceNames.ElasticsearchAlias}--superseded",
    ).flatMap { alias =>
      BeautyQSearchGen2ResourceSupport.readElasticsearchAliasTargets(esClient, alias) match {
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
    BeautyQSearchGen2ResourceSupport.readQdrantAliases(qdrantClient) match {
      case Right(entries) =>
        val reservedTargets = BeautyQSearchGen2ResourceSupport.reservedQdrantAliasTargets(entries)
        if (reservedTargets.nonEmpty)
          fail(s"reserved Qdrant Gen2 alias is not empty: ${reservedTargets.mkString(",")}")
      case Left(error) => fail(s"could not establish Qdrant alias isolation: $error")
    }
    qdrantHttp.getJson("/collections") match {
      case Right(json) =>
        val names = BeautyQSearchGen2ResourceSupport.decodeQdrantCollectionNames(json) match {
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
      BeautyQSearchGen2ResourceSupport.readElasticsearchAliasTargets(esClient, alias) match {
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
    val qdrantAliases = BeautyQSearchGen2ResourceSupport.readQdrantAliases(qdrantClient) match {
      case Right(entries) => entries
      case Left(error) => fail(s"failed to read exact Qdrant aliases during teardown: $error")
    }
    val qdrantAliasTargets = BeautyQSearchGen2ResourceSupport.reservedQdrantAliasTargets(qdrantAliases)
    if (qdrantAliasTargets.exists(value => !value.startsWith(BeautyQSearchGen2ResourceNames.QdrantPhysicalCollectionPrefix)))
      fail(s"refusing to delete a foreign Qdrant alias target: ${qdrantAliasTargets.mkString(",")}")
    val qdrantTargetsFromCollections = qdrantHttp.getJson("/collections") match {
      case Right(json) => BeautyQSearchGen2ResourceSupport.decodeQdrantCollectionNames(json) match {
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

  private def writeArtifact(relative: String, body: Json): Unit = {
    val path: Path = Paths.get(relative).toAbsolutePath
    Option(path.getParent).foreach(parent => Files.createDirectories(parent))
    Files.writeString(path, body.spaces2, StandardCharsets.UTF_8)
    ()
  }
}
