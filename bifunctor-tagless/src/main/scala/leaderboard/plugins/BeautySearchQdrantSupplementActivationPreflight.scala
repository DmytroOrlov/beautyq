package leaderboard.plugins

import leaderboard.plugins.BeautySearchQdrantSupplementActivation.{EsOnlyRollback, QdrantSupplementNotReady, QdrantSupplementReady}
import leaderboard.search.qdrant.{ObservedQdrantVectorConfig, QdrantCollectionCompatibilityExpectation, QdrantCollectionCompatibilityMismatch, QdrantCollectionIdentity}

// Operator-facing preflight/probe for the no-worsening Qdrant supplement activation path. Answers
// whether an operator-selected activation is `ReadyToEnable`, using only the existing pure
// `BeautySearchQdrantSupplementActivationConfig` parser and `QdrantCollectionIdentity.checkCompatibility`
// readiness check -- both already source-confirmed (QP5/QP7).
//
// This is a preflight/probe only, not activation: it never selects, switches, or enables anything by
// itself, performs no Qdrant readiness HTTP call, and never creates/deletes a collection. Callers
// supply the already-derived readiness expectation/observed values; this helper does no I/O.
sealed trait BeautySearchQdrantSupplementActivationPreflightStatus extends Product with Serializable {
  def label: String
}

object BeautySearchQdrantSupplementActivationPreflightStatus {
  case object ReadyToEnable extends BeautySearchQdrantSupplementActivationPreflightStatus {
    val label: String = "READY_TO_ENABLE"
  }
  case object Blocked extends BeautySearchQdrantSupplementActivationPreflightStatus {
    val label: String = "BLOCKED"
  }
}

final case class BeautySearchQdrantSupplementActivationPreflightResult(
  status: BeautySearchQdrantSupplementActivationPreflightStatus,
  selectedActivation: Option[BeautySearchQdrantSupplementActivation],
  reason: String,
  mismatchDetail: Option[List[QdrantCollectionCompatibilityMismatch]],
)

object BeautySearchQdrantSupplementActivationPreflight {
  val ReadyToEnableReason: String                  = "READY_TO_ENABLE"
  val EsOnlyRollbackSelectedReason: String         = "ES_ONLY_ROLLBACK_SELECTED"
  val QdrantSupplementNotReadySelectedReason: String = "QDRANT_SUPPLEMENT_NOT_READY_SELECTED"
  val InvalidOperatorConfigReason: String          = "INVALID_OPERATOR_CONFIG"
  val ReadinessMismatchReason: String              = "READINESS_MISMATCH"

  // Pure preflight: parses the operator value, then -- only for the explicit `QdrantSupplementReady`
  // selection -- checks the supplied readiness expectation/observed pair. `expectedReadiness` /
  // `observedReadiness` are ignored for every other selection (no readiness call is even attempted).
  def preflight(
    operatorValue: Option[String],
    expectedReadiness: QdrantCollectionCompatibilityExpectation,
    observedReadiness: ObservedQdrantVectorConfig,
  ): BeautySearchQdrantSupplementActivationPreflightResult =
    BeautySearchQdrantSupplementActivationConfig.fromOperatorValue(operatorValue) match {
      case Left(_) =>
        BeautySearchQdrantSupplementActivationPreflightResult(
          status = BeautySearchQdrantSupplementActivationPreflightStatus.Blocked,
          selectedActivation = None,
          reason = InvalidOperatorConfigReason,
          mismatchDetail = None,
        )

      case Right(EsOnlyRollback) =>
        BeautySearchQdrantSupplementActivationPreflightResult(
          status = BeautySearchQdrantSupplementActivationPreflightStatus.Blocked,
          selectedActivation = Some(EsOnlyRollback),
          reason = EsOnlyRollbackSelectedReason,
          mismatchDetail = None,
        )

      case Right(QdrantSupplementNotReady) =>
        BeautySearchQdrantSupplementActivationPreflightResult(
          status = BeautySearchQdrantSupplementActivationPreflightStatus.Blocked,
          selectedActivation = Some(QdrantSupplementNotReady),
          reason = QdrantSupplementNotReadySelectedReason,
          mismatchDetail = None,
        )

      case Right(QdrantSupplementReady) =>
        QdrantCollectionIdentity.checkCompatibility(expectedReadiness, observedReadiness) match {
          case Right(()) =>
            BeautySearchQdrantSupplementActivationPreflightResult(
              status = BeautySearchQdrantSupplementActivationPreflightStatus.ReadyToEnable,
              selectedActivation = Some(QdrantSupplementReady),
              reason = ReadyToEnableReason,
              mismatchDetail = None,
            )
          case Left(mismatches) =>
            BeautySearchQdrantSupplementActivationPreflightResult(
              status = BeautySearchQdrantSupplementActivationPreflightStatus.Blocked,
              selectedActivation = Some(QdrantSupplementReady),
              reason = ReadinessMismatchReason,
              mismatchDetail = Some(mismatches),
            )
        }
    }
}
