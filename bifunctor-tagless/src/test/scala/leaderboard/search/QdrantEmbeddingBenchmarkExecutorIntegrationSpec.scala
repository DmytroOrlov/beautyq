package leaderboard.search

import distage.{DIKey, Mode}
import izumi.distage.model.definition.Activation
import leaderboard.{LeaderboardTest, ProdTest}
import leaderboard.config.QdrantPortCfg
import leaderboard.model.QueryFailure
import leaderboard.repo.{Categories, MasterLocations, MasterServiceOfferVariants, MasterServiceOffers, Masters, ServiceVariantSchemas, Services}
import leaderboard.search.document.VariantSearchDocumentSnapshotProvider
import leaderboard.search.qdrant.{
  QdrantEmbeddingBenchmarkPlan,
  QdrantEmbeddingBenchmarkReportFormatter,
  QdrantEmbeddingBenchmarkReportJson,
  QdrantEmbeddingBenchmarkRunMode,
  QdrantEmbeddingBenchmarkRunner,
}
import leaderboard.seed.BeautyQSeedReady
import zio.{IO, ZIO}

import QdrantEmbeddingBenchmarkLiveCandidateSupport.ProbeOutcome

final class QdrantEmbeddingBenchmarkExecutorIntegrationSpec extends LeaderboardTest with ProdTest {
  override def config = {
    val base = super.config
    base.copy(
      activation = base.activation ++ Activation(Mode -> Mode.Test),
      memoizationRoots = base.memoizationRoots + DIKey[QdrantPortCfg],
    )
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
        QdrantEmbeddingBenchmarkLiveCandidateSupport.probeEndpoint(endpoint) match {
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
                candidates = List(QdrantEmbeddingBenchmarkLiveCandidateSupport.candidate("single", endpoint)),
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
        val smallProbe = QdrantEmbeddingBenchmarkLiveCandidateSupport.probeEndpoint(smallUrl)
        val largeProbe = QdrantEmbeddingBenchmarkLiveCandidateSupport.probeEndpoint(largeUrl)
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
                candidates = List(
                  QdrantEmbeddingBenchmarkLiveCandidateSupport.candidate("small", smallUrl),
                  QdrantEmbeddingBenchmarkLiveCandidateSupport.candidate("large", largeUrl),
                ),
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
      candidates <- ZIO.foreach(plan.candidates)(QdrantEmbeddingBenchmarkLiveCandidateSupport.probeCandidateDimension)
      executor = QdrantEmbeddingBenchmarkLiveCandidateSupport.executor(portCfg, snapshotProvider)
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

  private def tinyQueries = QdrantEmbeddingBenchmarkLiveCandidateSupport.tinyQueries

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
    QdrantEmbeddingBenchmarkLiveCandidateSupport.snapshotProvider(
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
