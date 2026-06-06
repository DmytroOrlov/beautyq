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
        if (!envFlag("QDRANT_EMBEDDING_BENCHMARK_SINGLE_ENDPOINT")) {
          cancel("Set QDRANT_EMBEDDING_BENCHMARK_SINGLE_ENDPOINT=true to run the single-endpoint Qdrant embedding benchmark executor integration")
        } else {
          sys.env.get("QDRANT_EMBEDDING_BENCHMARK_ENDPOINT") match {
            case None =>
              cancel("Set QDRANT_EMBEDDING_BENCHMARK_ENDPOINT=http://localhost:8081 to run the single-endpoint benchmark executor integration")
            case Some(endpoint) =>
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
          }
        }
    }

    "run two dual-endpoint candidates through the benchmark runner when both endpoints are explicitly enabled" in {
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
        if (!envFlag("QDRANT_EMBEDDING_BENCHMARK_DUAL_ENDPOINT")) {
          cancel("Set QDRANT_EMBEDDING_BENCHMARK_DUAL_ENDPOINT=true to run the dual-endpoint Qdrant embedding benchmark executor integration")
        } else {
          (sys.env.get("QDRANT_EMBEDDING_SMALL_URL"), sys.env.get("QDRANT_EMBEDDING_LARGE_URL")) match {
            case (Some(smallUrl), Some(largeUrl)) =>
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
              cancel("Set QDRANT_EMBEDDING_SMALL_URL and QDRANT_EMBEDDING_LARGE_URL to run the dual-endpoint benchmark executor integration")
          }
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
      report <- new QdrantEmbeddingBenchmarkRunner(executor).run(plan.copy(candidates = candidates), tinyQueries)
      jsonReport = QdrantEmbeddingBenchmarkReportJson.encodeReportString(report)
      _ <- ZIO.succeed(println(QdrantEmbeddingBenchmarkReportFormatter.formatWithDecisions(report)))
      _ <- ZIO.succeed(println("BEGIN_QDRANT_EMBEDDING_BENCHMARK_JSON"))
      _ <- ZIO.succeed(println(jsonReport))
      _ <- ZIO.succeed(println("END_QDRANT_EMBEDDING_BENCHMARK_JSON"))
      _ <- assertIO(report.candidateReports.size == plan.runMode.expectedCandidateCount)
      _ <- assertIO(report.candidateReports.forall(_.aggregate.queryCount == tinyQueries.size))
      _ <- assertIO(report.candidateReports.forall(_.queryMetrics.size == tinyQueries.size))
      _ <- assertIO(report.candidateReports.forall(_.aggregate.candidateId.nonEmpty))
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

  private def envFlag(name: String): Boolean =
    sys.env.get(name).exists { value =>
      val normalized = value.trim.toLowerCase
      normalized == "1" || normalized == "true" || normalized == "yes"
    }
}
