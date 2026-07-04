package leaderboard.plugins

import leaderboard.api.BeautySearchServingGate
import leaderboard.model.QueryFailure
import leaderboard.plugins.BeautySearchQdrantSupplementActivation.{EsOnlyRollback, QdrantSupplementNotReady, QdrantSupplementReady}

// Pure activation-to-route-selection policy for BeautyQ search module selection. Names the explicit
// route selections an activation state maps to, without depending on `distage.ModuleDef` or the app
// graph:
//
//   - EsOnlyRollbackRoute           -> no serving gate, does not select the Qdrant supplement route.
//   - QdrantSupplementNotReadyRoute -> selects the Qdrant supplement route with
//                                      `BeautySearchServingGate.enabledNotReady`.
//   - QdrantSupplementReadyRoute    -> selects the Qdrant supplement route with
//                                      `BeautySearchServingGate.enabledReady`.
//
// Interpreting a selection into the corresponding `distage.ModuleDef` lives outside this module, in
// `BeautySearchQdrantSupplementActivationModuleSelector` in `bifunctor-tagless`, since that mapping
// depends on `BeautySearchRouteModules` and the app graph.
sealed trait BeautySearchQdrantSupplementRouteSelection extends Product with Serializable {
  def qdrantSupplementRouteSelected: Boolean
  def servingGate: Option[BeautySearchServingGate]
}

object BeautySearchQdrantSupplementRouteSelection {
  case object EsOnlyRollbackRoute extends BeautySearchQdrantSupplementRouteSelection {
    override val qdrantSupplementRouteSelected: Boolean = false
    override val servingGate: Option[BeautySearchServingGate] = None
  }

  case object QdrantSupplementNotReadyRoute extends BeautySearchQdrantSupplementRouteSelection {
    override val qdrantSupplementRouteSelected: Boolean = true
    override val servingGate: Option[BeautySearchServingGate] = Some(BeautySearchServingGate.enabledNotReady)
  }

  case object QdrantSupplementReadyRoute extends BeautySearchQdrantSupplementRouteSelection {
    override val qdrantSupplementRouteSelected: Boolean = true
    override val servingGate: Option[BeautySearchServingGate] = Some(BeautySearchServingGate.enabledReady)
  }
}

object BeautySearchQdrantSupplementRouteSelectionPolicy {
  import BeautySearchQdrantSupplementRouteSelection._

  // Pure mapping from activation state to route selection.
  def selectionFor(activation: BeautySearchQdrantSupplementActivation): BeautySearchQdrantSupplementRouteSelection =
    activation match {
      case EsOnlyRollback =>
        EsOnlyRollbackRoute
      case QdrantSupplementNotReady =>
        QdrantSupplementNotReadyRoute
      case QdrantSupplementReady =>
        QdrantSupplementReadyRoute
    }

  // Convenience selector composing the pure operator-value parser with the activation -> route-selection mapping.
  def selectionForOperatorValue(operatorValue: Option[String]): Either[QueryFailure, BeautySearchQdrantSupplementRouteSelection] =
    BeautySearchQdrantSupplementActivationConfig.fromOperatorValue(operatorValue).map(selectionFor)
}
