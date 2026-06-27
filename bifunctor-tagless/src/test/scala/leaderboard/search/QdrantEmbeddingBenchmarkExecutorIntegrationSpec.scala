package leaderboard.search

import distage.{DIKey, Mode}
import izumi.distage.model.definition.Activation
import leaderboard.{LeaderboardTest, ProdTest}
import leaderboard.config.QdrantPortCfg
import leaderboard.model.QueryFailure
import leaderboard.repo.{Categories, MasterLocations, MasterServiceOfferVariants, MasterServiceOffers, Masters, ServiceVariantSchemas, Services}
import leaderboard.search.document.{BeautySearchCatalogSnapshotLoader, VariantSearchDocument, VariantSearchDocumentBuilder, VariantSearchDocumentSnapshotProvider}
import leaderboard.search.eval.BeautySearchEvalQuery
import leaderboard.search.qdrant.{
  QdrantClient,
  QdrantEmbeddingBenchmarkCandidate,
  QdrantEmbeddingBenchmarkExecutorConfig,
  QdrantEmbeddingBenchmarkPlan,
  QdrantEmbeddingBenchmarkQuerySubset,
  QdrantEmbeddingBenchmarkQdrantCandidateExecutor,
  QdrantEmbeddingBenchmarkReportFormatter,
  QdrantEmbeddingBenchmarkReportJson,
  QdrantEmbeddingBenchmarkRunMode,
  QdrantEmbeddingBenchmarkRunner,
}
import leaderboard.seed.{BeautyQSeedLoader, BeautyQSeedReady}
import zio.{IO, ZIO}

import scala.annotation.unused
import scala.util.control.NonFatal
import java.util.UUID

final class QdrantEmbeddingBenchmarkExecutorIntegrationSpec extends LeaderboardTest with ProdTest {
  override def config = {
    val base = super.config
    base.copy(
      activation = base.activation ++ Activation(Mode -> Mode.Test),
      memoizationRoots = base.memoizationRoots + DIKey[QdrantPortCfg],
    )
  }

  private val seed = new BeautyQSeedLoader.ResourceLoader().load() match {
    case Right(value) => value
    case Left(error)  => throw new RuntimeException(error.message)
  }

  "Qdrant embedding benchmark executor integration" should {
    "run one single-endpoint candidate through the benchmark runner" in {
      (
        portCfg: QdrantPortCfg,
        categories: Categories[IO],
        services: Services[IO],
        serviceVariantSchemas: ServiceVariantSchemas[IO],
        masters: Masters[IO],
        masterLocations: MasterLocations[IO],
        masterServiceOffers: MasterServiceOffers[IO],
        masterServiceOfferVariants: MasterServiceOfferVariants[IO],
        seedReady: BeautyQSeedReady,
      ) =>
        val endpoint = sys.env.get("QDRANT_EMBEDDING_BENCHMARK_ENDPOINT").getOrElse(LlamaCppEmbeddingTestConfig.default.baseUrl)
        probeEndpoint(endpoint) match {
          case ProbeOutcome.Reachable(_) =>
            runPlan(
              portCfg = portCfg,
              snapshotProvider = snapshotProvider(
                categories,
                services,
                serviceVariantSchemas,
                masters,
                masterLocations,
                masterServiceOffers,
                masterServiceOfferVariants,
                seedReady,
              ),
              plan = QdrantEmbeddingBenchmarkPlan(
                runMode = QdrantEmbeddingBenchmarkRunMode.SingleEndpointManualRestart,
                candidates = List(candidate("single", endpoint)),
                k = 5,
              ),
            )
          case ProbeOutcome.Unreachable(reason) =>
            cancel(
              s"Embedding candidate comparison skipped: candidate endpoint $endpoint is unreachable ($reason). " +
                s"Start the local embedding endpoint at $endpoint or override QDRANT_EMBEDDING_BENCHMARK_ENDPOINT."
            )
          case ProbeOutcome.ContractViolation(reason) =>
            fail(s"Embedding endpoint $endpoint is reachable but violated the embedding API contract: $reason")
        }
    }

    "run two dual-endpoint candidates through the benchmark runner when both endpoints are available" in {
      (
        portCfg: QdrantPortCfg,
        categories: Categories[IO],
        services: Services[IO],
        serviceVariantSchemas: ServiceVariantSchemas[IO],
        masters: Masters[IO],
        masterLocations: MasterLocations[IO],
        masterServiceOffers: MasterServiceOffers[IO],
        masterServiceOfferVariants: MasterServiceOfferVariants[IO],
        seedReady: BeautyQSeedReady,
      ) =>
        val smallUrl = sys.env.get("QDRANT_EMBEDDING_SMALL_URL").getOrElse(LlamaCppEmbeddingTestConfig.default.baseUrl)
        val largeUrl = sys.env.get("QDRANT_EMBEDDING_LARGE_URL").getOrElse("http://localhost:8082")
        val smallProbe = probeEndpoint(smallUrl)
        val largeProbe = probeEndpoint(largeUrl)
        (smallProbe, largeProbe) match {
          case (ProbeOutcome.ContractViolation(reason), _) =>
            fail(s"Embedding endpoint $smallUrl is reachable but violated the embedding API contract: $reason")
          case (_, ProbeOutcome.ContractViolation(reason)) =>
            fail(s"Embedding endpoint $largeUrl is reachable but violated the embedding API contract: $reason")
          case (ProbeOutcome.Reachable(_), ProbeOutcome.Reachable(_)) =>
            runPlan(
              portCfg = portCfg,
              snapshotProvider = snapshotProvider(
                categories,
                services,
                serviceVariantSchemas,
                masters,
                masterLocations,
                masterServiceOffers,
                masterServiceOfferVariants,
                seedReady,
              ),
              plan = QdrantEmbeddingBenchmarkPlan(
                runMode = QdrantEmbeddingBenchmarkRunMode.DualEndpointParallel,
                candidates = List(candidate("small", smallUrl), candidate("large", largeUrl)),
                k = 5,
              ),
            )
          case _ =>
            val unavailable = List(
              Option.when(!smallProbe.isReachable)(smallUrl),
              Option.when(!largeProbe.isReachable)(largeUrl),
            ).flatten.mkString(", ")
            cancel(
              s"Embedding candidate comparison skipped: both candidate endpoints are required, pairwise comparison resource-gated. " +
                s"Start small at $smallUrl and large at $largeUrl, or override QDRANT_EMBEDDING_SMALL_URL / QDRANT_EMBEDDING_LARGE_URL. " +
                s"Unavailable: $unavailable."
            )
        }
    }
  }

  private def runPlan(
    portCfg: QdrantPortCfg,
    snapshotProvider: VariantSearchDocumentSnapshotProvider[IO],
    plan: QdrantEmbeddingBenchmarkPlan,
  ): IO[QueryFailure, Unit] =
    for {
      candidates <- ZIO.foreach(plan.candidates)(probeCandidateDimension)
      qdrantClient = new QdrantClient(portCfg.host, portCfg.port)
      executor = QdrantEmbeddingBenchmarkQdrantCandidateExecutor.fromQdrantClient(
        snapshotProvider = snapshotProvider,
        qdrantClient = qdrantClient,
        embeddingConfig = LlamaCppEmbeddingTestConfig.default,
        config = QdrantEmbeddingBenchmarkExecutorConfig(
          collectionRunId = UUID.randomUUID().toString,
          topK = 5,
        ),
      )
      output <- new QdrantEmbeddingBenchmarkRunner(executor).runWithQueryResults(plan.copy(candidates = candidates), tinyQueries)
      report = output.report
      jsonReport = QdrantEmbeddingBenchmarkReportJson.encodeReportString(report)
      jsonRunOutput = QdrantEmbeddingBenchmarkReportJson.encodeRunOutputString(output)
      _ <- ZIO.succeed(println(QdrantEmbeddingBenchmarkReportFormatter.formatWithDecisions(report)))
      _ <- ZIO.succeed(println("BEGIN_QDRANT_EMBEDDING_BENCHMARK_JSON"))
      _ <- ZIO.succeed(println(jsonReport))
      _ <- ZIO.succeed(println("END_QDRANT_EMBEDDING_BENCHMARK_JSON"))
      _ <- ZIO.succeed(println("BEGIN_QDRANT_EMBEDDING_BENCHMARK_RUN_OUTPUT_JSON"))
      _ <- ZIO.succeed(println(jsonRunOutput))
      _ <- ZIO.succeed(println("END_QDRANT_EMBEDDING_BENCHMARK_RUN_OUTPUT_JSON"))
      _ <- assertIO(report.candidateReports.size == plan.runMode.expectedCandidateCount)
      _ <- assertIO(report.candidateReports.forall(_.aggregate.queryCount == tinyQueries.size))
      _ <- assertIO(report.candidateReports.forall(_.queryMetrics.size == tinyQueries.size))
      _ <- assertIO(report.candidateReports.forall(_.aggregate.candidateId.nonEmpty))
      _ <- assertIO(output.queryResultsByCandidateId.keySet == plan.candidates.map(_.candidateId).toSet)
      _ <- assertIO(output.queryResultsByCandidateId.values.forall(_.size == tinyQueries.size))
    } yield ()

  private sealed trait ProbeOutcome {
    def isReachable: Boolean = this.isInstanceOf[ProbeOutcome.Reachable]
  }
  private object ProbeOutcome {
    final case class Reachable(vector: Vector[Double]) extends ProbeOutcome
    final case class Unreachable(reason: String) extends ProbeOutcome
    final case class ContractViolation(reason: String) extends ProbeOutcome
  }

  // Distinguishes an absent candidate resource from a broken-but-reachable one so the
  // live comparison can resource-gate (cancel) the former while still failing red on the latter.
  // Transport-level failures (connection refused, timeout, unknown host) surface from the HTTP
  // send as QueryExecutionFailure -> Unreachable. Embedding API contract violations (non-2xx,
  // non-JSON, missing data, empty embedding) surface as an OperationFailure or an empty vector
  // -> ContractViolation.
  private def probeEndpoint(url: String): ProbeOutcome = {
    val probe =
      try
        unsafeRun(
          LlamaCppEmbeddingTestConfig
            .client(LlamaCppEmbeddingTestConfig.withBaseUrl(url))
            .embed("embedding candidate comparison probe")
            .either
        )
      catch { case NonFatal(error) => Left(QueryFailure.fromThrowable("execute-llama-cpp-http", error)) }
    probe match {
      case Right(vector) if vector.nonEmpty                        => ProbeOutcome.Reachable(vector)
      case Right(_)                                                => ProbeOutcome.ContractViolation(s"$url returned an empty embedding")
      case Left(QueryFailure.QueryExecutionFailure(_, message, _)) => ProbeOutcome.Unreachable(s"$url: $message")
      case Left(other)                                             => ProbeOutcome.ContractViolation(s"$url: ${other.message}")
    }
  }

  private def probeCandidateDimension(candidate: QdrantEmbeddingBenchmarkCandidate): IO[QueryFailure, QdrantEmbeddingBenchmarkCandidate] =
    LlamaCppEmbeddingTestConfig.client(LlamaCppEmbeddingTestConfig.withBaseUrl(candidate.endpointLabel))
      .embed("qdrant embedding benchmark dimension probe")
      .flatMap { vector =>
        if (vector.nonEmpty) ZIO.succeed(candidate.copy(vectorDimension = vector.length))
        else ZIO.fail(QueryFailure.operation("qdrant-embedding-benchmark-executor", s"Candidate ${candidate.candidateId} returned an empty embedding"))
      }

  private def candidate(candidateId: String, endpoint: String): QdrantEmbeddingBenchmarkCandidate =
    QdrantEmbeddingBenchmarkCandidate(
      candidateId = s"benchmark-$candidateId",
      modelName = s"benchmark-$candidateId",
      endpointLabel = endpoint,
      vectorDimension = 1,
    )

  private def tinyQueries: List[BeautySearchEvalQuery] = {
    QdrantEmbeddingBenchmarkQuerySubset
      .select(
        QdrantEmbeddingBenchmarkQuerySubset.SemanticBroadSmoke,
        BeautySearchEvalInventory.evalSuite.queries,
      )
      .fold(error => throw new RuntimeException(error.message), identity)
  }

  private def snapshotProvider(
    categories: Categories[IO],
    services: Services[IO],
    serviceVariantSchemas: ServiceVariantSchemas[IO],
    masters: Masters[IO],
    masterLocations: MasterLocations[IO],
    masterServiceOffers: MasterServiceOffers[IO],
    masterServiceOfferVariants: MasterServiceOfferVariants[IO],
    seedReady: BeautyQSeedReady,
  ): VariantSearchDocumentSnapshotProvider[IO] =
    new VariantSearchDocumentSnapshotProvider[IO] {
      override def loadSnapshot(): IO[QueryFailure, List[VariantSearchDocument]] =
        loadDocuments(
          categories,
          services,
          serviceVariantSchemas,
          masters,
          masterLocations,
          masterServiceOffers,
          masterServiceOfferVariants,
          seedReady,
        )
    }

  private def loadDocuments(
    categories: Categories[IO],
    services: Services[IO],
    serviceVariantSchemas: ServiceVariantSchemas[IO],
    masters: Masters[IO],
    masterLocations: MasterLocations[IO],
    masterServiceOffers: MasterServiceOffers[IO],
    masterServiceOfferVariants: MasterServiceOfferVariants[IO],
    @unused seedReady: BeautyQSeedReady,
  ): IO[QueryFailure, List[VariantSearchDocument]] = {
    val loader = new BeautySearchCatalogSnapshotLoader.SeedScopedFromRepositories[IO](
      seedReady,
      seed,
      categories,
      services,
      serviceVariantSchemas,
      masters,
      masterLocations,
      masterServiceOffers,
      masterServiceOfferVariants,
    )

    for {
      snapshot <- loader.load()
      documents <- ZIO.fromEither(VariantSearchDocumentBuilder.build(snapshot))
    } yield documents
  }

  private def unsafeRun[A](effect: zio.IO[QueryFailure, A]): A =
    zio.Unsafe.unsafe { implicit unsafe =>
      zio.Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
    }
}
