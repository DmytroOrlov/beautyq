package leaderboard.search

import leaderboard.config.QdrantPortCfg
import leaderboard.model.QueryFailure
import leaderboard.repo.{Categories, MasterLocations, MasterServiceOfferVariants, MasterServiceOffers, Masters, ServiceVariantSchemas, Services}
import leaderboard.search.document.{BeautyQSearchCatalogSeedScope, BeautyQSearchCatalogSnapshotLoader, BeautyQVariantSearchDocumentMaterialization, VariantSearchDocument, VariantSearchDocumentSnapshotProvider}
import leaderboard.search.eval.BeautySearchEvalQuery
import leaderboard.search.qdrant.{
  QdrantClient,
  QdrantEmbeddingBenchmarkCandidate,
  QdrantEmbeddingBenchmarkExecutorConfig,
  QdrantEmbeddingBenchmarkPlan,
  QdrantEmbeddingBenchmarkQdrantCandidateExecutor,
  QdrantEmbeddingBenchmarkQuerySubset,
  QdrantEmbeddingBenchmarkReport,
  QdrantEmbeddingBenchmarkRunMode,
  QdrantEmbeddingBenchmarkRunner,
}
import leaderboard.seed.{BeautyQSeedLoader, BeautyQSeedReady}
import zio.{IO, ZIO}

import scala.annotation.unused
import scala.util.control.NonFatal
import java.util.UUID

// Narrow live-candidate benchmark support shared by the live executor integration spec and the
// automatic best-available comparison orchestrator. It owns the embedding endpoint probe, the
// repository-backed snapshot provider, the Qdrant candidate executor wiring, and the single-candidate
// benchmark run that produces a one-candidate report suitable for
// QdrantEmbeddingBenchmarkSavedReportComparison.
object QdrantEmbeddingBenchmarkLiveCandidateSupport {

  // Distinguishes an absent candidate resource from a broken-but-reachable one so a live comparison
  // can resource-gate (cancel) the former while still failing red on the latter. Transport-level
  // failures (connection refused, timeout, unknown host) surface from the HTTP send as
  // QueryExecutionFailure -> Unreachable. Embedding API contract violations (non-2xx, non-JSON,
  // missing data, empty embedding) surface as an OperationFailure or an empty vector -> ContractViolation.
  sealed trait ProbeOutcome {
    def isReachable: Boolean = this.isInstanceOf[ProbeOutcome.Reachable]
  }
  object ProbeOutcome {
    final case class Reachable(vector: Vector[Double]) extends ProbeOutcome
    final case class Unreachable(reason: String) extends ProbeOutcome
    final case class ContractViolation(reason: String) extends ProbeOutcome
  }

  private lazy val seed = new BeautyQSeedLoader.ResourceLoader().load() match {
    case Right(value) => value
    case Left(error)  => throw new RuntimeException(error.message)
  }

  def probeEndpoint(url: String): ProbeOutcome = {
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

  def candidate(candidateId: String, endpoint: String): QdrantEmbeddingBenchmarkCandidate =
    QdrantEmbeddingBenchmarkCandidate(
      candidateId = s"benchmark-$candidateId",
      modelName = s"benchmark-$candidateId",
      endpointLabel = endpoint,
      vectorDimension = 1,
    )

  def probeCandidateDimension(candidate: QdrantEmbeddingBenchmarkCandidate): IO[QueryFailure, QdrantEmbeddingBenchmarkCandidate] =
    LlamaCppEmbeddingTestConfig.client(LlamaCppEmbeddingTestConfig.withBaseUrl(candidate.endpointLabel))
      .embed("qdrant embedding benchmark dimension probe")
      .flatMap { vector =>
        if (vector.nonEmpty) ZIO.succeed(candidate.copy(vectorDimension = vector.length))
        else ZIO.fail(QueryFailure.operation("qdrant-embedding-benchmark-executor", s"Candidate ${candidate.candidateId} returned an empty embedding"))
      }

  def executor(
    portCfg: QdrantPortCfg,
    snapshotProvider: VariantSearchDocumentSnapshotProvider[IO],
  ): QdrantEmbeddingBenchmarkQdrantCandidateExecutor =
    QdrantEmbeddingBenchmarkQdrantCandidateExecutor.fromQdrantClient(
      snapshotProvider = snapshotProvider,
      qdrantClient = new QdrantClient(portCfg.host, portCfg.port),
      embeddingConfig = LlamaCppEmbeddingTestConfig.default,
      config = QdrantEmbeddingBenchmarkExecutorConfig(
        collectionRunId = UUID.randomUUID().toString,
        topK = 5,
      ),
    )

  // Runs a single reachable live candidate end-to-end and returns a one-candidate report. The fresh
  // report carries exactly one candidate report, matching what QdrantEmbeddingBenchmarkSavedReportComparison
  // expects per side, so fresh-vs-fresh / fresh-vs-saved comparisons never feed a dual-candidate report in.
  def runSingleCandidateReport(
    portCfg: QdrantPortCfg,
    snapshotProvider: VariantSearchDocumentSnapshotProvider[IO],
    candidateId: String,
    endpoint: String,
  ): IO[QueryFailure, QdrantEmbeddingBenchmarkReport] =
    for {
      probed <- probeCandidateDimension(candidate(candidateId, endpoint))
      report <- new QdrantEmbeddingBenchmarkRunner(executor(portCfg, snapshotProvider)).run(
        QdrantEmbeddingBenchmarkPlan(
          runMode = QdrantEmbeddingBenchmarkRunMode.SingleEndpointManualRestart,
          candidates = List(probed),
          k = 5,
        ),
        tinyQueries,
      )
    } yield report

  def tinyQueries: List[BeautySearchEvalQuery] =
    QdrantEmbeddingBenchmarkQuerySubset
      .select(
        QdrantEmbeddingBenchmarkQuerySubset.SemanticBroadSmoke,
        BeautySearchEvalInventory.evalSuite.queries,
      )
      .fold(error => throw new RuntimeException(error.message), identity)

  def snapshotProvider(
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
    val seedScope = BeautyQSearchCatalogSeedScope(
      categories                 = seed.categories,
      services                   = seed.services,
      masters                    = seed.masters,
      masterLocations            = seed.masterLocations,
      masterServiceOffers        = seed.masterServiceOffers,
      masterServiceOfferVariants = seed.masterServiceOfferVariants,
    )
    val loader = new BeautyQSearchCatalogSnapshotLoader.SeedScopedFromRepositories[IO](
      seedScope,
      categories,
      services,
      serviceVariantSchemas,
      masters,
      masterLocations,
      masterServiceOffers,
      masterServiceOfferVariants,
    )

    for {
      snapshot  <- loader.load()
      documents <- ZIO.fromEither(BeautyQVariantSearchDocumentMaterialization.project(snapshot))
    } yield documents
  }

  def unsafeRun[A](effect: IO[QueryFailure, A]): A =
    zio.Unsafe.unsafe { implicit unsafe =>
      zio.Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
    }
}
