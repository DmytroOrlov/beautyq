package leaderboard.search

import leaderboard.search.qdrant.{
  QdrantEmbeddingBenchmarkAggregate,
  QdrantEmbeddingBenchmarkCandidate,
  QdrantEmbeddingBenchmarkCandidateReport,
  QdrantEmbeddingBenchmarkPlan,
  QdrantEmbeddingBenchmarkReport,
  QdrantEmbeddingBenchmarkReportJson,
  QdrantEmbeddingBenchmarkRunMode,
  QdrantEmbeddingBenchmarkSavedReportComparison,
}
import org.scalatest.wordspec.AnyWordSpec

import QdrantEmbeddingBenchmarkComparisonSelection.{Inputs, Selection, Side}
import QdrantEmbeddingBenchmarkLiveCandidateSupport.ProbeOutcome

// Deterministic selection-matrix coverage for the M3 orchestrator.
//
// This proves which real comparison path the orchestrator selects for every probe/saved combination
// without starting or stopping live embedding servers: it drives the pure
// QdrantEmbeddingBenchmarkComparisonSelection.select with controlled probe outcomes and fake saved JSON.
// The real resource-backed live happy path stays covered by
// QdrantEmbeddingBenchmarkSavedReportComparisonManualSpec.
final class QdrantEmbeddingBenchmarkComparisonSelectionSpec extends AnyWordSpec {

  private val LeftUrl = "http://localhost:8081"
  private val RightUrl = "http://localhost:8082"
  private val LeftSavedEnv = "QDRANT_EMBEDDING_BENCHMARK_LEFT_JSON"
  private val RightSavedEnv = "QDRANT_EMBEDDING_BENCHMARK_RIGHT_JSON"

  private def reachable: ProbeOutcome = ProbeOutcome.Reachable(Vector(0.1, 0.2, 0.3))
  private def unreachable: ProbeOutcome = ProbeOutcome.Unreachable("connection refused")
  private def broken: ProbeOutcome = ProbeOutcome.ContractViolation("returned an empty embedding")

  private def inputs(
    leftProbe: ProbeOutcome,
    rightProbe: ProbeOutcome,
    savedLeft: Option[String] = None,
    savedRight: Option[String] = None,
  ): Inputs =
    Inputs(
      leftUrl = LeftUrl,
      rightUrl = RightUrl,
      leftProbe = leftProbe,
      rightProbe = rightProbe,
      savedLeft = savedLeft,
      savedRight = savedRight,
      leftSavedEnv = LeftSavedEnv,
      rightSavedEnv = RightSavedEnv,
    )

  "QdrantEmbeddingBenchmarkComparisonSelection.select" should {
    "select fresh-vs-fresh when both live candidates are reachable" in {
      assert(QdrantEmbeddingBenchmarkComparisonSelection.select(inputs(reachable, reachable)) == Selection.FreshVsFresh)
    }

    "select fresh-vs-fresh when both live candidates are reachable even if saved reports are also present" in {
      val selection = QdrantEmbeddingBenchmarkComparisonSelection.select(
        inputs(reachable, reachable, savedLeft = Some("{}"), savedRight = Some("{}"))
      )
      assert(selection == Selection.FreshVsFresh)
    }

    "select fresh-vs-saved (right counterpart) when only the left live candidate is reachable and a saved right report exists" in {
      val selection = QdrantEmbeddingBenchmarkComparisonSelection.select(
        inputs(reachable, unreachable, savedRight = Some("saved-right-json"))
      )
      assert(selection == Selection.FreshVsSaved(Side.Left, "saved-right-json"))
    }

    "select fresh-vs-saved (left counterpart) when only the right live candidate is reachable and a saved left report exists" in {
      val selection = QdrantEmbeddingBenchmarkComparisonSelection.select(
        inputs(unreachable, reachable, savedLeft = Some("saved-left-json"))
      )
      assert(selection == Selection.FreshVsSaved(Side.Right, "saved-left-json"))
    }

    "cancel when the left live candidate is reachable but its saved right counterpart is missing" in {
      val selection = QdrantEmbeddingBenchmarkComparisonSelection.select(inputs(reachable, unreachable))
      selection match {
        case Selection.Cancel(reason) =>
          assert(reason.contains(RightUrl))
          assert(reason.contains(RightSavedEnv))
        case other => fail(s"expected Cancel, got $other")
      }
    }

    "cancel when the right live candidate is reachable but its saved left counterpart is missing" in {
      val selection = QdrantEmbeddingBenchmarkComparisonSelection.select(inputs(unreachable, reachable))
      selection match {
        case Selection.Cancel(reason) =>
          assert(reason.contains(LeftUrl))
          assert(reason.contains(LeftSavedEnv))
        case other => fail(s"expected Cancel, got $other")
      }
    }

    "select saved-vs-saved when no live candidate is reachable and two saved reports exist" in {
      val selection = QdrantEmbeddingBenchmarkComparisonSelection.select(
        inputs(unreachable, unreachable, savedLeft = Some("left-json"), savedRight = Some("right-json"))
      )
      assert(selection == Selection.SavedVsSaved("left-json", "right-json"))
    }

    "cancel when no live candidate is reachable and the saved pair is incomplete (only left)" in {
      val selection = QdrantEmbeddingBenchmarkComparisonSelection.select(
        inputs(unreachable, unreachable, savedLeft = Some("left-json"))
      )
      selection match {
        case Selection.Cancel(reason) =>
          assert(reason.contains(LeftSavedEnv))
          assert(reason.contains(RightSavedEnv))
        case other => fail(s"expected Cancel, got $other")
      }
    }

    "cancel when no live candidate is reachable and no saved reports exist (no fixture fallback)" in {
      val selection = QdrantEmbeddingBenchmarkComparisonSelection.select(inputs(unreachable, unreachable))
      // Critically: an absent real pair produces Cancel, never a fabricated/fixture comparison path.
      assert(selection.isInstanceOf[Selection.Cancel])
    }

    "fail red when the left live candidate is reachable but contract-broken" in {
      val selection = QdrantEmbeddingBenchmarkComparisonSelection.select(inputs(broken, reachable))
      selection match {
        case Selection.FailRed(reason) => assert(reason.contains(LeftUrl))
        case other                     => fail(s"expected FailRed, got $other")
      }
    }

    "fail red when the right live candidate is reachable but contract-broken" in {
      val selection = QdrantEmbeddingBenchmarkComparisonSelection.select(inputs(reachable, broken))
      selection match {
        case Selection.FailRed(reason) => assert(reason.contains(RightUrl))
        case other                     => fail(s"expected FailRed, got $other")
      }
    }

    "fail red on a contract-broken live candidate and never downgrade, even when a saved counterpart is present" in {
      val selection = QdrantEmbeddingBenchmarkComparisonSelection.select(
        inputs(broken, unreachable, savedRight = Some("saved-right-json"))
      )
      // No fallback to fresh-vs-saved / saved-vs-saved: a reachable-but-broken endpoint stays red.
      assert(selection.isInstanceOf[Selection.FailRed])
    }
  }

  "the orchestrator decode contract used after selection" should {
    "fail red on present-but-invalid saved JSON" in {
      assert(QdrantEmbeddingBenchmarkReportJson.decodeReportString("{").isLeft)
    }

    "decode a valid saved single-candidate report" in {
      val json = QdrantEmbeddingBenchmarkReportJson.encodeReportString(SelectionTestReports.singleCandidate("candidate"))
      assert(QdrantEmbeddingBenchmarkReportJson.decodeReportString(json).isRight)
    }

    "fail red when a decoded saved report has an unsupported shape (multiple candidate reports)" in {
      val failure = QdrantEmbeddingBenchmarkSavedReportComparison
        .compareReports(SelectionTestReports.dualCandidate, SelectionTestReports.singleCandidate("right"))
        .swap
        .toOption
        .getOrElse(fail("expected a comparator failure on the dual-candidate left report"))
      assert(failure.message.contains("Expected exactly one candidate report"))
    }
  }
}

// Minimal in-memory report builders for the post-selection decode/shape contract assertions. No live
// resources, no file IO: these are fake reports used only to prove the fail-red boundaries.
object SelectionTestReports {
  def singleCandidate(candidateId: String): QdrantEmbeddingBenchmarkReport =
    report(candidateReport(candidateId))

  def dualCandidate: QdrantEmbeddingBenchmarkReport =
    report(candidateReport("left-1"), candidateReport("left-2"))

  private def report(candidateReports: QdrantEmbeddingBenchmarkCandidateReport*): QdrantEmbeddingBenchmarkReport =
    QdrantEmbeddingBenchmarkReport(
      plan = QdrantEmbeddingBenchmarkPlan(
        runMode = QdrantEmbeddingBenchmarkRunMode.SingleEndpointManualRestart,
        candidates = candidateReports.toList.map(_.candidate),
        k = 5,
      ),
      candidateReports = candidateReports.toList,
      comparisons = Nil,
    )

  private def candidateReport(candidateId: String): QdrantEmbeddingBenchmarkCandidateReport =
    QdrantEmbeddingBenchmarkCandidateReport(
      candidate = QdrantEmbeddingBenchmarkCandidate(
        candidateId = candidateId,
        modelName = s"model-$candidateId",
        endpointLabel = s"http://localhost/$candidateId",
        vectorDimension = 1024,
      ),
      queryMetrics = Nil,
      aggregate = QdrantEmbeddingBenchmarkAggregate(
        candidateId = candidateId,
        queryCount = 0,
        variantRecallAtK = 0.0,
        meanReciprocalRankAtK = 0.0,
        providerHitRateAtK = 0.0,
        serviceHitRateAtK = 0.0,
        meanQueryLatencyMs = None,
      ),
    )
}
