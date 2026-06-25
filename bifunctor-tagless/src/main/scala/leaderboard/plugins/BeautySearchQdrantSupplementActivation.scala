package leaderboard.plugins

import distage.ModuleDef
import leaderboard.api.BeautySearchServingGate

// Production-usable activation selector for BeautyQ search module selection. Maps an explicit
// activation state to the search module that should be assembled for it:
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
// NOT included by `LeaderboardPlugin`, does NOT parse environment/config/CLI, creates no runtime role,
// wires no Qdrant client/lifecycle/startup indexing, and never silently falls back from Qdrant to ES.
// Rollback is the explicit `EsOnlyRollback` module selection back to the ES-only route, not a runtime
// fallback. The Qdrant supplement states always select `ExplicitConstraintsFilterPlusTop1` (never
// `AppendAll`), inherited from `apiQdrantVariantSupplementExplicitOptIn`.
sealed trait BeautySearchQdrantSupplementActivation extends Product with Serializable

object BeautySearchQdrantSupplementActivation {
  case object EsOnlyRollback extends BeautySearchQdrantSupplementActivation
  case object QdrantSupplementNotReady extends BeautySearchQdrantSupplementActivation
  case object QdrantSupplementReady extends BeautySearchQdrantSupplementActivation

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
}
