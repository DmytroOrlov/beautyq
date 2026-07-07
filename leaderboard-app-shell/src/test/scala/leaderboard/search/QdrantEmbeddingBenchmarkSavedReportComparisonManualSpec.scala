package leaderboard.search

import distage.{DIKey, Mode}
import izumi.distage.model.definition.Activation
import leaderboard.{LeaderboardTest, ProdTest}
import leaderboard.config.QdrantPortCfg
import leaderboard.model.QueryFailure
import leaderboard.repo.{Categories, MasterLocations, MasterServiceOfferVariants, MasterServiceOffers, Masters, ServiceVariantSchemas, Services}
import leaderboard.search.document.VariantSearchDocumentSnapshotProvider
import leaderboard.search.qdrant.{
  QdrantEmbeddingBenchmarkReport,
  QdrantEmbeddingBenchmarkReportJson,
  QdrantEmbeddingBenchmarkSavedReportComparison,
}
import leaderboard.seed.BeautyQSeedReady
import zio.{IO, ZIO}

import QdrantEmbeddingBenchmarkComparisonSelection.{Selection, Side}
import QdrantEmbeddingBenchmarkLiveCandidateSupport.ProbeOutcome

// Automatic best-available real benchmark comparison (M3 orchestrator).
//
// This spec no longer requires a manual env-flag mode switch. On the normal local/full-suite path it
// probes the two live embedding candidates and, via the pure
// QdrantEmbeddingBenchmarkComparisonSelection.select, automatically picks the strongest real comparison
// available from live candidates and/or saved single-candidate reports:
//
//   1. both live candidates reachable -> fresh-vs-fresh (no saved reports required)
//   2. exactly one live candidate     -> fresh-vs-saved (requires the missing side's saved report)
//   3. no live candidates             -> saved-vs-saved (requires both saved reports)
//
// A reachable-but-broken live endpoint fails red rather than being downgraded to a saved-only
// comparison. There is intentionally no fixture-vs-fixture fallback here; fixture/unit coverage for the
// comparator lives in QdrantEmbeddingBenchmarkSavedReportComparisonSpec, and the deterministic selection
// matrix lives in QdrantEmbeddingBenchmarkComparisonSelectionSpec. This spec keeps the real
// resource-backed coverage for the actual live happy path.
final class QdrantEmbeddingBenchmarkSavedReportComparisonManualSpec extends LeaderboardTest with ProdTest {
  import QdrantEmbeddingBenchmarkSavedReportComparisonManualSpec.*

  override def config = {
    val base = super.config
    base.copy(
      activation = base.activation ++ Activation(Mode -> Mode.Test),
      memoizationRoots = base.memoizationRoots + DIKey[QdrantPortCfg],
    )
  }

  "QdrantEmbeddingBenchmarkSavedReportComparison automatic best-available comparison" should {
    "select and run the strongest real embedding benchmark comparison available" in {
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
        val provider = QdrantEmbeddingBenchmarkLiveCandidateSupport.snapshotProvider(
          categories,
          services,
          serviceVariantSchemas,
          masters,
          masterLocations,
          masterServiceOffers,
          masterServiceOfferVariants,
          seedReady,
        )

        val leftUrl  = sys.env.get(EnvLeftUrl).getOrElse(LlamaCppEmbeddingTestConfig.default.baseUrl)
        val rightUrl = sys.env.get(EnvRightUrl).getOrElse(DefaultRightUrl)
        val inputs = QdrantEmbeddingBenchmarkComparisonSelection.Inputs(
          leftUrl = leftUrl,
          rightUrl = rightUrl,
          leftProbe = QdrantEmbeddingBenchmarkLiveCandidateSupport.probeEndpoint(leftUrl),
          rightProbe = QdrantEmbeddingBenchmarkLiveCandidateSupport.probeEndpoint(rightUrl),
          savedLeft = savedReport(EnvLeftJson),
          savedRight = savedReport(EnvRightJson),
          leftSavedEnv = EnvLeftJson,
          rightSavedEnv = EnvRightJson,
        )

        val selection = QdrantEmbeddingBenchmarkComparisonSelection.select(inputs)
        printDiagnostics(inputs, selection)

        interpret(portCfg, provider, inputs, selection)
    }
  }

  // Interprets the pure selection by running real live candidates and/or decoding saved reports. The
  // selection itself never depends on the contents/validity of saved JSON or fresh execution, only on
  // probe reachability and saved-report presence, so the live/IO concerns stay isolated here.
  private def interpret(
    portCfg: QdrantPortCfg,
    provider: VariantSearchDocumentSnapshotProvider[IO],
    inputs: QdrantEmbeddingBenchmarkComparisonSelection.Inputs,
    selection: Selection,
  ): IO[QueryFailure, Unit] =
    selection match {
      case Selection.FreshVsFresh =>
        for {
          left  <- runLive(portCfg, provider, "small", inputs.leftUrl)
          right <- runLive(portCfg, provider, "large", inputs.rightUrl)
          _     <- emitComparison(QdrantEmbeddingBenchmarkComparisonSelection.provenanceLabel(selection), left, right)
        } yield ()

      case Selection.FreshVsSaved(Side.Left, savedRightJson) =>
        for {
          left     <- runLive(portCfg, provider, "small", inputs.leftUrl)
          rightRep <- decodeSaved(savedRightJson)
          _        <- emitComparison(QdrantEmbeddingBenchmarkComparisonSelection.provenanceLabel(selection), left, rightRep)
        } yield ()

      case Selection.FreshVsSaved(Side.Right, savedLeftJson) =>
        for {
          leftRep <- decodeSaved(savedLeftJson)
          right   <- runLive(portCfg, provider, "large", inputs.rightUrl)
          _       <- emitComparison(QdrantEmbeddingBenchmarkComparisonSelection.provenanceLabel(selection), leftRep, right)
        } yield ()

      case Selection.SavedVsSaved(leftJson, rightJson) =>
        for {
          leftRep  <- decodeSaved(leftJson)
          rightRep <- decodeSaved(rightJson)
          _        <- emitComparison(QdrantEmbeddingBenchmarkComparisonSelection.provenanceLabel(selection), leftRep, rightRep)
        } yield ()

      // Reachable-but-broken live endpoint: fail red, never downgraded to a saved comparison.
      case Selection.FailRed(reason) =>
        fail(reason)

      // No sufficient real comparison pair: cancel (not fail), with an actionable reason.
      case Selection.Cancel(reason) =>
        cancel(reason)
    }

  // Runs a single live candidate into a one-candidate report and prints that fresh report JSON. The
  // print is the only export: it is bounded (one report per candidate, no file IO) and exists purely so
  // a fresh report can be captured by hand and replayed later as a saved counterpart. It does not change
  // selection behavior.
  private def runLive(
    portCfg: QdrantPortCfg,
    provider: VariantSearchDocumentSnapshotProvider[IO],
    candidateId: String,
    endpoint: String,
  ): IO[QueryFailure, QdrantEmbeddingBenchmarkReport] =
    for {
      report <- QdrantEmbeddingBenchmarkLiveCandidateSupport.runSingleCandidateReport(portCfg, provider, candidateId, endpoint)
      _      <- ZIO.succeed(printFreshReport(candidateId, report))
    } yield report

  // Decodes a saved single-candidate report. Present-but-invalid JSON fails red (the IO fails), which is
  // distinct from a missing saved report (which cancels in the pure selection before reaching here).
  private def decodeSaved(json: String): IO[QueryFailure, QdrantEmbeddingBenchmarkReport] =
    ZIO.fromEither(QdrantEmbeddingBenchmarkReportJson.decodeReportString(json))

  // Compares two single-candidate reports through the saved-report comparator contract. A Left (invalid
  // shape: zero or multiple candidate reports, or a contract failure) fails red; a Right decision passes
  // and is printed with its selected provenance.
  private def emitComparison(
    provenance: String,
    left: QdrantEmbeddingBenchmarkReport,
    right: QdrantEmbeddingBenchmarkReport,
  ): IO[QueryFailure, Unit] =
    QdrantEmbeddingBenchmarkSavedReportComparison.compareReportsWithDecision(left, right) match {
      case Left(failure) =>
        ZIO.fail(failure)
      case Right(decision) =>
        ZIO.succeed {
          println(s"QDRANT_EMBEDDING_BENCHMARK_COMPARISON_PROVENANCE: $provenance")
          println("BEGIN_QDRANT_EMBEDDING_BENCHMARK_COMPARISON")
          println(QdrantEmbeddingBenchmarkSavedReportComparison.formatDecision(decision))
          println("END_QDRANT_EMBEDDING_BENCHMARK_COMPARISON")
        }
    }

  private def printFreshReport(candidateId: String, report: QdrantEmbeddingBenchmarkReport): Unit = {
    println(s"BEGIN_QDRANT_EMBEDDING_BENCHMARK_FRESH_REPORT_JSON ($candidateId)")
    println(QdrantEmbeddingBenchmarkReportJson.encodeReportString(report))
    println(s"END_QDRANT_EMBEDDING_BENCHMARK_FRESH_REPORT_JSON ($candidateId)")
  }

  private def savedReport(envName: String): Option[String] =
    sys.env.get(envName).map(_.trim).filter(_.nonEmpty)

  private def printDiagnostics(
    inputs: QdrantEmbeddingBenchmarkComparisonSelection.Inputs,
    selection: Selection,
  ): Unit = {
    println("BEGIN_QDRANT_EMBEDDING_BENCHMARK_COMPARISON_DIAGNOSTICS")
    println(s"liveCandidatesDiscovered: left=${inputs.leftUrl} ($EnvLeftUrl), right=${inputs.rightUrl} ($EnvRightUrl)")
    println(s"liveCandidateLeft: ${describeProbe(inputs.leftProbe)}")
    println(s"liveCandidateRight: ${describeProbe(inputs.rightProbe)}")
    println(s"savedReportLeft: ${describeSaved(inputs.savedLeft, EnvLeftJson)}")
    println(s"savedReportRight: ${describeSaved(inputs.savedRight, EnvRightJson)}")
    println(s"selectedProvenance: ${QdrantEmbeddingBenchmarkComparisonSelection.provenanceLabel(selection)}")
    println(s"selectionReason: ${QdrantEmbeddingBenchmarkComparisonSelection.selectionReason(selection)}")
    println("END_QDRANT_EMBEDDING_BENCHMARK_COMPARISON_DIAGNOSTICS")
  }

  private def describeProbe(probe: ProbeOutcome): String =
    probe match {
      case ProbeOutcome.Reachable(vector)         => s"reachable (embedding dimension ${vector.length})"
      case ProbeOutcome.Unreachable(reason)       => s"unreachable ($reason)"
      case ProbeOutcome.ContractViolation(reason) => s"reachable-but-broken ($reason)"
    }

  private def describeSaved(saved: Option[String], envName: String): String =
    saved.fold(s"absent ($envName not set)")(_ => s"provided ($envName)")
}

object QdrantEmbeddingBenchmarkSavedReportComparisonManualSpec {
  // Preserved saved-report env vars. EnvCompareSavedReports is retained for backward compatibility but is
  // no longer a default gate: saved reports are sourced directly from EnvLeftJson / EnvRightJson when set.
  // Saved reports are discovered only from these source-confirmed env inputs; there is no directory scan
  // or hidden file discovery.
  val EnvCompareSavedReports = "QDRANT_EMBEDDING_BENCHMARK_COMPARE_SAVED_REPORTS"
  val EnvLeftJson = "QDRANT_EMBEDDING_BENCHMARK_LEFT_JSON"
  val EnvRightJson = "QDRANT_EMBEDDING_BENCHMARK_RIGHT_JSON"

  // Live candidate endpoints, shared with QdrantEmbeddingBenchmarkExecutorIntegrationSpec's dual run.
  val EnvLeftUrl = "QDRANT_EMBEDDING_SMALL_URL"
  val EnvRightUrl = "QDRANT_EMBEDDING_LARGE_URL"
  val DefaultRightUrl = "http://localhost:8082"
}
