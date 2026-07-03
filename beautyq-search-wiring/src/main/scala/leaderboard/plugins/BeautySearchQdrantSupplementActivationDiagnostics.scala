package leaderboard.plugins

import leaderboard.plugins.BeautySearchQdrantSupplementActivation.{EsOnlyRollback, QdrantSupplementNotReady, QdrantSupplementReady}
import leaderboard.search.qdrant.QdrantCollectionCompatibilityMismatch

// Operator-visible diagnostics for the no-worsening Qdrant supplement activation path (QP14). This is
// a pure summary value object built from an already-computed preflight result
// (`BeautySearchQdrantSupplementActivationPreflightResult`, QP8) plus the original operator value: it
// restates the selected activation mode, the operator config parse outcome, the preflight
// status/reason, a machine-readable readiness-mismatch summary, and a single decision summary.
//
// No runtime logging seam is source-confirmed in this path (the activation/preflight code performs no
// `LogIO`/`IzLogger` logging), so this helper is a pure formatter only -- mirroring the existing pure
// diagnostic value object `ExperimentalHybridRouteDiagnostics`. It does no I/O, no Qdrant readiness
// HTTP call, no route switching, no activation, and produces no route JSON / API output: only stable
// key/value lines for an operator to read.
//
// The readiness-mismatch summary reports only the mismatch *type* tokens from the existing
// `QdrantCollectionCompatibilityMismatch` ADT; it never renders expected/observed payload values, so
// no raw HTTP payload or secret is exposed.
final case class BeautySearchQdrantSupplementActivationDiagnostics(
  activationMode: String,
  operatorValue: String,
  activationParse: String,
  preflightStatus: String,
  preflightReason: String,
  mismatches: List[String],
  decisionSummary: String,
) {
  import BeautySearchQdrantSupplementActivationDiagnostics.*

  // Stable ordered key/value lines (no route JSON / API codec): the documented operator-visible shape.
  def lines: List[(String, String)] =
    List(
      ActivationModeKey      -> activationMode,
      ActivationOperatorKey  -> operatorValue,
      ActivationParseKey     -> activationParse,
      PreflightStatusKey     -> preflightStatus,
      PreflightReasonKey     -> preflightReason,
      PreflightMismatchesKey -> renderMismatches,
      DecisionSummaryKey     -> decisionSummary,
    )

  // Operator-readable `key=value` lines (still not route JSON / API output).
  def renderLines: List[String] = lines.map { case (key, value) => s"$key=$value" }

  private def renderMismatches: String =
    if (mismatches.isEmpty) NoMismatches else mismatches.mkString(",")
}

object BeautySearchQdrantSupplementActivationDiagnostics {
  // Output keys (stable operator-visible labels; not API field names).
  val ActivationModeKey: String      = "activation.mode"
  val ActivationOperatorKey: String  = "activation.operatorValue"
  val ActivationParseKey: String     = "activation.parse"
  val PreflightStatusKey: String     = "preflight.status"
  val PreflightReasonKey: String     = "preflight.reason"
  val PreflightMismatchesKey: String = "preflight.mismatches"
  val DecisionSummaryKey: String     = "decision.summary"

  // Operator value rendering.
  val AbsentOperatorValueLabel: String = "<absent>"

  // Parse outcome labels (fail closed: a rejected parse selects no activation).
  val ParseAccepted: String = "ACCEPTED"
  val ParseRejected: String = "REJECTED"

  // Activation mode labels.
  val EsOnlyRollbackMode: String           = "EsOnlyRollback"
  val QdrantSupplementNotReadyMode: String = "QdrantSupplementNotReady"
  val QdrantSupplementReadyMode: String    = "QdrantSupplementReady"
  val InvalidConfigMode: String            = "InvalidConfig"

  // Mismatch type tokens (type only; never expected/observed values).
  val CollectionNameMismatchToken: String = "COLLECTION_NAME"
  val VectorNameMismatchToken: String     = "VECTOR_NAME"
  val DimensionMismatchToken: String      = "DIMENSION"
  val DistanceMismatchToken: String       = "DISTANCE"
  val EmbeddingModelMismatchToken: String = "EMBEDDING_MODEL"
  val NoMismatches: String                = "none"

  // Decision summary labels.
  val DecisionReadyToEnable: String           = "READY_TO_ENABLE"
  val DecisionDefaultEsOnly: String           = "DEFAULT_ES_ONLY"
  val DecisionBlockedNotReady: String         = "BLOCKED_NOT_READY"
  val DecisionBlockedReadinessMismatch: String = "BLOCKED_READINESS_MISMATCH"
  val DecisionBlockedInvalidConfig: String    = "BLOCKED_INVALID_CONFIG"

  def from(
    operatorValue: Option[String],
    result: BeautySearchQdrantSupplementActivationPreflightResult,
  ): BeautySearchQdrantSupplementActivationDiagnostics =
    BeautySearchQdrantSupplementActivationDiagnostics(
      activationMode  = activationModeLabel(result.selectedActivation),
      operatorValue   = operatorValue.getOrElse(AbsentOperatorValueLabel),
      activationParse = parseLabel(result.selectedActivation),
      preflightStatus = result.status.label,
      preflightReason = result.reason,
      mismatches      = mismatchTokens(result.mismatchDetail),
      decisionSummary = decisionSummaryLabel(result),
    )

  // Fail-closed: no activation selected <=> the operator value was rejected by the pure parser.
  private def parseLabel(selectedActivation: Option[BeautySearchQdrantSupplementActivation]): String =
    selectedActivation match {
      case Some(_) => ParseAccepted
      case None    => ParseRejected
    }

  private def activationModeLabel(selectedActivation: Option[BeautySearchQdrantSupplementActivation]): String =
    selectedActivation match {
      case Some(EsOnlyRollback)           => EsOnlyRollbackMode
      case Some(QdrantSupplementNotReady) => QdrantSupplementNotReadyMode
      case Some(QdrantSupplementReady)    => QdrantSupplementReadyMode
      case None                           => InvalidConfigMode
    }

  private def mismatchTokens(mismatchDetail: Option[List[QdrantCollectionCompatibilityMismatch]]): List[String] =
    mismatchDetail.getOrElse(Nil).map {
      case _: QdrantCollectionCompatibilityMismatch.CollectionNameMismatch => CollectionNameMismatchToken
      case _: QdrantCollectionCompatibilityMismatch.VectorNameMismatch     => VectorNameMismatchToken
      case _: QdrantCollectionCompatibilityMismatch.DimensionMismatch      => DimensionMismatchToken
      case _: QdrantCollectionCompatibilityMismatch.DistanceMismatch       => DistanceMismatchToken
      case _: QdrantCollectionCompatibilityMismatch.EmbeddingModelMismatch => EmbeddingModelMismatchToken
    }

  private def decisionSummaryLabel(result: BeautySearchQdrantSupplementActivationPreflightResult): String =
    result.status match {
      case BeautySearchQdrantSupplementActivationPreflightStatus.ReadyToEnable =>
        DecisionReadyToEnable
      case BeautySearchQdrantSupplementActivationPreflightStatus.Blocked =>
        result.selectedActivation match {
          case Some(EsOnlyRollback)           => DecisionDefaultEsOnly
          case Some(QdrantSupplementNotReady) => DecisionBlockedNotReady
          case Some(QdrantSupplementReady)    => DecisionBlockedReadinessMismatch
          case None                           => DecisionBlockedInvalidConfig
        }
    }
}
