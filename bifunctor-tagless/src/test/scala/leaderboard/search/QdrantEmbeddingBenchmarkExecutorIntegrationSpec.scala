package leaderboard.search

import distage.{DIKey, Mode}
import izumi.distage.model.definition.Activation
import leaderboard.{LeaderboardTest, ProdTest}
import leaderboard.config.QdrantPortCfg
import leaderboard.model.QueryFailure
import leaderboard.repo.{Categories, MasterLocations, MasterServiceOfferVariants, MasterServiceOffers, Masters, ServiceVariantSchemas, Services}
import leaderboard.search.document.{BeautySearchCatalogSnapshotLoader, VariantSearchDocument, VariantSearchDocumentBuilder, VariantSearchDocumentSnapshotProvider}
import leaderboard.search.embedding.{LlamaCppEmbeddingClient, LlamaCppEmbeddingClientConfig}
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
import java.util.UUID

final class QdrantEmbeddingBenchmarkExecutorIntegrationSpec extends LeaderboardTest with ProdTest {
  override def config = super.config.copy(
    activation = super.config.activation ++ Activation(Mode -> Mode.Test),
    memoizationRoots = super.config.memoizationRoots + DIKey[QdrantPortCfg],
  )

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
        val endpoint = sys.env.get("QDRANT_EMBEDDING_BENCHMARK_ENDPOINT").getOrElse("http://localhost:8081")
        val probeResult = try {
          unsafeRun(new LlamaCppEmbeddingClient(LlamaCppEmbeddingClientConfig(baseUrl = endpoint)).embed("benchmark single endpoint probe").either)
        } catch {
          case _: Exception => Left(leaderboard.model.QueryFailure.operation("benchmark-probe", "endpoint unavailable"))
        }
        probeResult match {
          case Right(vector) if vector.nonEmpty =>
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
          case _ =>
            cancel(s"Embedding endpoint $endpoint is unavailable; canceling single-endpoint benchmark executor integration")
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
        val smallUrl = sys.env.get("QDRANT_EMBEDDING_SMALL_URL").getOrElse("http://localhost:8081")
        val largeUrl = sys.env.get("QDRANT_EMBEDDING_LARGE_URL").getOrElse("http://localhost:8082")
        def probeOne(url: String): Either[leaderboard.model.QueryFailure, Vector[Double]] = {
          try {
            unsafeRun(new LlamaCppEmbeddingClient(LlamaCppEmbeddingClientConfig(baseUrl = url)).embed("benchmark dual endpoint probe").either)
          } catch {
            case _: Exception => Left(leaderboard.model.QueryFailure.operation("benchmark-probe", s"endpoint $url unavailable"))
          }
        }
        val smallProbe = probeOne(smallUrl)
        val largeProbe = probeOne(largeUrl)
        val smallOk = smallProbe.exists(_.nonEmpty)
        val largeOk = largeProbe.exists(_.nonEmpty)
        if (smallOk && largeOk) {
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
        } else {
          val unavailable = List(
            if (!smallOk) Some(smallUrl) else None,
            if (!largeOk) Some(largeUrl) else None,
          ).flatten.mkString(", ")
          cancel(s"Embedding endpoint(s) unavailable: $unavailable; canceling dual-endpoint benchmark executor integration")
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

  private def probeCandidateDimension(candidate: QdrantEmbeddingBenchmarkCandidate): IO[QueryFailure, QdrantEmbeddingBenchmarkCandidate] =
    new LlamaCppEmbeddingClient(LlamaCppEmbeddingClientConfig(baseUrl = candidate.endpointLabel))
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
