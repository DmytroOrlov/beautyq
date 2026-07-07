package leaderboard.search

import QdrantEmbeddingBenchmarkLiveCandidateSupport.ProbeOutcome

// Pure, deterministic selection of the strongest available real benchmark comparison.
//
// Given the two live-candidate probe outcomes and the saved single-candidate reports discovered from
// source-confirmed env inputs, `select` decides which real comparison path the M3 orchestrator runs.
// It performs no IO: the orchestrator interprets the returned `Selection` by running live candidates,
// decoding saved JSON, comparing, or cancelling/failing. Keeping it pure lets the selection matrix be
// exercised deterministically without starting/stopping live embedding servers for every branch.
//
// There is intentionally no fixture/auto-pass case in `Selection`: when no sufficient real pair exists
// the result is `Cancel`, never a fabricated comparison.
object QdrantEmbeddingBenchmarkComparisonSelection {

  sealed trait Side
  object Side {
    case object Left extends Side
    case object Right extends Side
  }

  sealed trait Selection
  object Selection {
    // Both live candidates reachable and valid: run both, compare fresh-vs-fresh.
    case object FreshVsFresh extends Selection
    // Exactly one live candidate reachable: run the live side, compare against the saved counterpart.
    // `liveSide` is the reachable side; `savedJson` is the missing side's saved report content.
    final case class FreshVsSaved(liveSide: Side, savedJson: String) extends Selection
    // No live candidate reachable: compare the two saved single-candidate reports.
    final case class SavedVsSaved(leftJson: String, rightJson: String) extends Selection
    // A live endpoint is reachable but violated the embedding/benchmark contract. Fail red, no downgrade.
    final case class FailRed(reason: String) extends Selection
    // No sufficient real comparison pair exists. Cancel with an actionable reason.
    final case class Cancel(reason: String) extends Selection
  }

  final case class Inputs(
    leftUrl: String,
    rightUrl: String,
    leftProbe: ProbeOutcome,
    rightProbe: ProbeOutcome,
    savedLeft: Option[String],
    savedRight: Option[String],
    leftSavedEnv: String,
    rightSavedEnv: String,
  )

  def select(inputs: Inputs): Selection =
    (inputs.leftProbe, inputs.rightProbe) match {
      // Reachable-but-broken must fail red even if the other side is reachable or a saved report exists:
      // a contract-broken live endpoint is never silently downgraded to a weaker comparison.
      case (ProbeOutcome.ContractViolation(reason), _) =>
        Selection.FailRed(brokenLiveMessage(inputs.leftUrl, reason))
      case (_, ProbeOutcome.ContractViolation(reason)) =>
        Selection.FailRed(brokenLiveMessage(inputs.rightUrl, reason))

      case (ProbeOutcome.Reachable(_), ProbeOutcome.Reachable(_)) =>
        Selection.FreshVsFresh

      case (ProbeOutcome.Reachable(_), _) =>
        inputs.savedRight match {
          case Some(rightJson) => Selection.FreshVsSaved(Side.Left, rightJson)
          case None            => Selection.Cancel(missingCounterpartMessage("right", inputs.rightUrl, inputs.rightSavedEnv))
        }

      case (_, ProbeOutcome.Reachable(_)) =>
        inputs.savedLeft match {
          case Some(leftJson) => Selection.FreshVsSaved(Side.Right, leftJson)
          case None           => Selection.Cancel(missingCounterpartMessage("left", inputs.leftUrl, inputs.leftSavedEnv))
        }

      case _ =>
        (inputs.savedLeft, inputs.savedRight) match {
          case (Some(leftJson), Some(rightJson)) => Selection.SavedVsSaved(leftJson, rightJson)
          case _ =>
            Selection.Cancel(
              missingPairMessage(inputs, leftSavedProvided = inputs.savedLeft.isDefined, rightSavedProvided = inputs.savedRight.isDefined)
            )
        }
    }

  def provenanceLabel(selection: Selection): String =
    selection match {
      case Selection.FreshVsFresh             => "fresh-vs-fresh"
      case Selection.FreshVsSaved(_, _)       => "fresh-vs-saved"
      case Selection.SavedVsSaved(_, _)       => "saved-vs-saved"
      case Selection.FailRed(_)               => "fail-red"
      case Selection.Cancel(_)                => "cancel"
    }

  def selectionReason(selection: Selection): String =
    selection match {
      case Selection.FreshVsFresh               => "fresh-vs-fresh: both live candidates reachable, no saved reports required"
      case Selection.FreshVsSaved(Side.Left, _) => "fresh-vs-saved: only the left live candidate is reachable, using the saved right counterpart"
      case Selection.FreshVsSaved(Side.Right, _) => "fresh-vs-saved: only the right live candidate is reachable, using the saved left counterpart"
      case Selection.SavedVsSaved(_, _)         => "saved-vs-saved: no live candidate reachable, using both saved reports"
      case Selection.FailRed(reason)            => s"fail-red: $reason"
      case Selection.Cancel(reason)             => s"cancel: $reason"
    }

  def brokenLiveMessage(url: String, reason: String): String =
    s"Embedding endpoint $url is reachable but violated the embedding/benchmark contract: $reason"

  def missingCounterpartMessage(side: String, missingUrl: String, missingEnv: String): String =
    s"Embedding benchmark comparison skipped: one live candidate is available but its $side counterpart is missing. " +
      s"Start a live embedding endpoint at $missingUrl, or provide a saved single-candidate $side report via $missingEnv."

  def missingPairMessage(inputs: Inputs, leftSavedProvided: Boolean, rightSavedProvided: Boolean): String =
    s"Embedding benchmark comparison skipped: no live embedding candidates reachable and fewer than two saved reports provided. " +
      s"Start live endpoints at ${inputs.leftUrl} / ${inputs.rightUrl}, or set both ${inputs.leftSavedEnv} and ${inputs.rightSavedEnv} " +
      s"saved single-candidate reports. Saved provided: left=$leftSavedProvided, right=$rightSavedProvided."
}
