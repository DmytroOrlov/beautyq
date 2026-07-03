package leaderboard.plugins

import distage.ModuleDef
import leaderboard.api.BeautySearchServingGate
import leaderboard.model.QueryFailure
import leaderboard.plugins.BeautySearchQdrantSupplementActivation.{EsOnlyRollback, QdrantSupplementNotReady, QdrantSupplementReady}

// Production-usable route/module selector for BeautyQ search module selection. Maps the pure
// `BeautySearchQdrantSupplementActivation` state (`beautyq-search-wiring`) to the search module that
// should be assembled for it:
//
//   - EsOnlyRollback           -> the default ES-only route (`BeautySearchRouteModules.apiElasticsearch`),
//                                 i.e. the exact module the production `LeaderboardPlugin` already includes.
//   - QdrantSupplementNotReady -> the explicit no-worsening Qdrant variant-supplement module
//                                 (`QdrantVariantSupplementPolicy.ExplicitConstraintsFilterPlusTop1`)
//                                 wired with `BeautySearchServingGate.enabledNotReady`: the kill-switch /
//                                 not-ready state, where valid requests are rejected with HTTP 503.
//   - QdrantSupplementReady    -> the same explicit no-worsening supplement module wired with
//                                 `BeautySearchServingGate.enabledReady`: the selected service serves.
//
// This is an explicit module selector only. It does NOT change the default `/beauty-search` route, is
// NOT included by `LeaderboardPlugin`, does NOT parse environment/config/CLI itself, creates no
// runtime role, wires no Qdrant client/lifecycle/startup indexing, and never silently falls back from
// Qdrant to ES. Rollback is the explicit `EsOnlyRollback` module selection back to the ES-only route,
// not a runtime fallback. The Qdrant supplement states always select `ExplicitConstraintsFilterPlusTop1`
// (never `AppendAll`), inherited from `apiQdrantVariantSupplementExplicitOptIn`.
object BeautySearchQdrantSupplementActivationModuleSelector {

  // Pure mapping from activation state to the corresponding search module.
  def moduleFor(activation: BeautySearchQdrantSupplementActivation): ModuleDef =
    activation match {
      case EsOnlyRollback =>
        BeautySearchRouteModules.apiElasticsearch
      case QdrantSupplementNotReady =>
        BeautySearchRouteModules.apiQdrantVariantSupplementExplicitOptIn(BeautySearchServingGate.enabledNotReady)
      case QdrantSupplementReady =>
        BeautySearchRouteModules.apiQdrantVariantSupplementExplicitOptIn(BeautySearchServingGate.enabledReady)
    }

  // Convenience selector composing the pure operator-value parser with the activation -> module mapping.
  def moduleForOperatorValue(operatorValue: Option[String]): Either[QueryFailure, ModuleDef] =
    BeautySearchQdrantSupplementActivationConfig.fromOperatorValue(operatorValue).map(moduleFor)
}
