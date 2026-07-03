package leaderboard.plugins

import leaderboard.model.QueryFailure
import leaderboard.plugins.BeautySearchQdrantSupplementActivation.{EsOnlyRollback, QdrantSupplementNotReady, QdrantSupplementReady}

// Operator-facing config surface for `BeautySearchQdrantSupplementActivation`. Maps a plain explicit
// debug/preflight value to the activation state, with a safe default and fail-closed handling of
// unrecognized values:
//
//   - `None` (absent/unset operator config)         -> `EsOnlyRollback`
//   - `Some("es-only-rollback")`                     -> `EsOnlyRollback`
//   - `Some("qdrant-supplement-not-ready")`          -> `QdrantSupplementNotReady`
//   - `Some("qdrant-supplement-ready")`               -> `QdrantSupplementReady`
//   - any other `Some(value)`                        -> `Left(QueryFailure)`, never `QdrantSupplementReady`
//
// This is a pure parser only. It does not read HOCON/env/CLI itself, perform Qdrant readiness HTTP
// calls or lifecycle checks, or change the default `/beauty-search` route. Selecting
// `QdrantSupplementReady` requires an explicit, correctly-spelled operator value; there is no automatic
// or inferred readiness selection. Route/module selection composing this parser with a
// `distage.ModuleDef` mapping lives outside the parser, in
// `BeautySearchQdrantSupplementActivationModuleSelector` in `bifunctor-tagless`.
object BeautySearchQdrantSupplementActivationConfig {
  val EsOnlyRollbackOperatorValue: String           = "es-only-rollback"
  val QdrantSupplementNotReadyOperatorValue: String = "qdrant-supplement-not-ready"
  val QdrantSupplementReadyOperatorValue: String    = "qdrant-supplement-ready"

  // Pure parser: absent config selects the safe default; unrecognized values fail closed.
  def fromOperatorValue(operatorValue: Option[String]): Either[QueryFailure, BeautySearchQdrantSupplementActivation] =
    operatorValue match {
      case None                                              => Right(EsOnlyRollback)
      case Some(EsOnlyRollbackOperatorValue)                 => Right(EsOnlyRollback)
      case Some(QdrantSupplementNotReadyOperatorValue)       => Right(QdrantSupplementNotReady)
      case Some(QdrantSupplementReadyOperatorValue)          => Right(QdrantSupplementReady)
      case Some(other)                                       =>
        Left(QueryFailure.domain(
          s"Unrecognized BeautyQ Qdrant supplement activation operator value: '$other'. Expected one of: " +
            s"$EsOnlyRollbackOperatorValue, $QdrantSupplementNotReadyOperatorValue, $QdrantSupplementReadyOperatorValue."
        ))
    }
}
