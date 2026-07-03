package leaderboard.plugins

import leaderboard.model.QueryFailure
import leaderboard.search.qdrant.{QdrantCollectionCompatibilityChecker, QdrantCollectionCompatibilityExpectation, QdrantCollectionCompatibilityMismatch}
import zio.{IO, ZIO}

// Operator-facing real-resource preflight command for the no-worsening Qdrant supplement activation
// path (QP11). Reads the selected operator activation value and -- only for the explicit
// `qdrant-supplement-ready` selection -- runs the existing read-only `QdrantCollectionCompatibilityChecker`
// (QP5 compatibility stack) against the real Qdrant collection, then reports the same stable
// `BeautySearchQdrantSupplementActivationPreflightResult` (QP8 result model) that the pure preflight
// helper reports for the equivalent operator/readiness inputs.
//
// This is a real-resource preflight/probe only: it never selects, switches, or enables anything by
// itself, performs only the checker's read-only collection-info GET, never creates/deletes/recreates a
// collection, and never starts indexing or serves a request. A missing/unreachable Qdrant collection
// (the checker fails with a `QueryFailure` rather than returning a mismatch list) is reported as
// `Blocked` / `READINESS_MISMATCH`, exactly like a readiness mismatch -- never as `ReadyToEnable`.
object BeautySearchQdrantSupplementActivationPreflightCommand {
  def run(
    operatorValue: Option[String],
    expectedReadiness: QdrantCollectionCompatibilityExpectation,
    checker: QdrantCollectionCompatibilityChecker,
  ): IO[QueryFailure, BeautySearchQdrantSupplementActivationPreflightResult] =
    BeautySearchQdrantSupplementActivationConfig.fromOperatorValue(operatorValue) match {
      case Left(_) =>
        ZIO.succeed(blockedResult(None, BeautySearchQdrantSupplementActivationPreflight.InvalidOperatorConfigReason))

      case Right(BeautySearchQdrantSupplementActivation.EsOnlyRollback) =>
        ZIO.succeed(blockedResult(
          Some(BeautySearchQdrantSupplementActivation.EsOnlyRollback),
          BeautySearchQdrantSupplementActivationPreflight.EsOnlyRollbackSelectedReason,
        ))

      case Right(BeautySearchQdrantSupplementActivation.QdrantSupplementNotReady) =>
        ZIO.succeed(blockedResult(
          Some(BeautySearchQdrantSupplementActivation.QdrantSupplementNotReady),
          BeautySearchQdrantSupplementActivationPreflight.QdrantSupplementNotReadySelectedReason,
        ))

      case Right(BeautySearchQdrantSupplementActivation.QdrantSupplementReady) =>
        checker.check(expectedReadiness)
          .map {
            case Right(()) =>
              BeautySearchQdrantSupplementActivationPreflightResult(
                status = BeautySearchQdrantSupplementActivationPreflightStatus.ReadyToEnable,
                selectedActivation = Some(BeautySearchQdrantSupplementActivation.QdrantSupplementReady),
                reason = BeautySearchQdrantSupplementActivationPreflight.ReadyToEnableReason,
                mismatchDetail = None,
              )
            case Left(mismatches) =>
              readinessMismatchResult(Some(mismatches))
          }
          .catchAll(_ => ZIO.succeed(readinessMismatchResult(None)))
    }

  private def blockedResult(
    selectedActivation: Option[BeautySearchQdrantSupplementActivation],
    reason: String,
  ): BeautySearchQdrantSupplementActivationPreflightResult =
    BeautySearchQdrantSupplementActivationPreflightResult(
      status = BeautySearchQdrantSupplementActivationPreflightStatus.Blocked,
      selectedActivation = selectedActivation,
      reason = reason,
      mismatchDetail = None,
    )

  private def readinessMismatchResult(
    mismatchDetail: Option[List[QdrantCollectionCompatibilityMismatch]]
  ): BeautySearchQdrantSupplementActivationPreflightResult =
    BeautySearchQdrantSupplementActivationPreflightResult(
      status = BeautySearchQdrantSupplementActivationPreflightStatus.Blocked,
      selectedActivation = Some(BeautySearchQdrantSupplementActivation.QdrantSupplementReady),
      reason = BeautySearchQdrantSupplementActivationPreflight.ReadinessMismatchReason,
      mismatchDetail = mismatchDetail,
    )
}
