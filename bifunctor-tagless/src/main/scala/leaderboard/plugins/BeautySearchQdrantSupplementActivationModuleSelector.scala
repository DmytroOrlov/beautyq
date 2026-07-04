package leaderboard.plugins

import distage.ModuleDef
import leaderboard.api.BeautySearchServingGate
import leaderboard.model.QueryFailure
import leaderboard.plugins.BeautySearchQdrantSupplementRouteSelection.{EsOnlyRollbackRoute, QdrantSupplementNotReadyRoute, QdrantSupplementReadyRoute}

// Production-usable `RouteSelection -> ModuleDef` interpreter for BeautyQ search module selection.
// The pure activation-to-route-selection policy lives in
// `BeautySearchQdrantSupplementRouteSelectionPolicy` (`beautyq-search-wiring`); this object only
// interprets that pure `BeautySearchQdrantSupplementRouteSelection` into the search module that
// should be assembled for it:
//
//   - EsOnlyRollbackRoute           -> the default ES-only route (`BeautySearchRouteModules.apiElasticsearch`),
//                                      i.e. the exact module the production `LeaderboardPlugin` already includes.
//   - QdrantSupplementNotReadyRoute -> the explicit no-worsening Qdrant variant-supplement module
//                                      (`QdrantVariantSupplementPolicy.ExplicitConstraintsFilterPlusTop1`)
//                                      wired with `BeautySearchServingGate.enabledNotReady`: the kill-switch /
//                                      not-ready state, where valid requests are rejected with HTTP 503.
//   - QdrantSupplementReadyRoute    -> the same explicit no-worsening supplement module wired with
//                                      `BeautySearchServingGate.enabledReady`: the selected service serves.
//
// This is an explicit module interpreter only. It does NOT change the default `/beauty-search` route, is
// NOT included by `LeaderboardPlugin`, does NOT parse environment/config/CLI itself, creates no
// runtime role, wires no Qdrant client/lifecycle/startup indexing, and never silently falls back from
// Qdrant to ES. Rollback is the explicit `EsOnlyRollbackRoute` module selection back to the ES-only route,
// not a runtime fallback. The Qdrant supplement selections always select `ExplicitConstraintsFilterPlusTop1`
// (never `AppendAll`), inherited from `apiQdrantVariantSupplementExplicitOptIn`.
object BeautySearchQdrantSupplementActivationModuleSelector {

  // Pure interpretation from route selection to the corresponding search module.
  def moduleForRouteSelection(selection: BeautySearchQdrantSupplementRouteSelection): ModuleDef =
    selection match {
      case EsOnlyRollbackRoute =>
        BeautySearchRouteModules.apiElasticsearch
      case QdrantSupplementNotReadyRoute =>
        BeautySearchRouteModules.apiQdrantVariantSupplementExplicitOptIn(BeautySearchServingGate.enabledNotReady)
      case QdrantSupplementReadyRoute =>
        BeautySearchRouteModules.apiQdrantVariantSupplementExplicitOptIn(BeautySearchServingGate.enabledReady)
    }

  // Convenience selector composing the pure activation -> route-selection policy with the route-selection interpreter.
  def moduleFor(activation: BeautySearchQdrantSupplementActivation): ModuleDef =
    moduleForRouteSelection(BeautySearchQdrantSupplementRouteSelectionPolicy.selectionFor(activation))

  // Convenience selector composing the pure operator-value -> route-selection policy with the route-selection interpreter.
  def moduleForOperatorValue(operatorValue: Option[String]): Either[QueryFailure, ModuleDef] =
    BeautySearchQdrantSupplementRouteSelectionPolicy.selectionForOperatorValue(operatorValue).map(moduleForRouteSelection)
}
