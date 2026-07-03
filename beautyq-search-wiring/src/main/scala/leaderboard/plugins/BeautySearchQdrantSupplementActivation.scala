package leaderboard.plugins

// Pure activation state for BeautyQ search module selection. Names the explicit states an operator
// can select:
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
// This is a pure state ADT only. It carries no route/module mapping itself: mapping an activation
// state to the corresponding `distage.ModuleDef` lives in
// `BeautySearchQdrantSupplementActivationModuleSelector` in `bifunctor-tagless`, since that mapping
// depends on `BeautySearchRouteModules` and the app graph, which stay outside this contract/wiring
// module. This state ADT does NOT change the default `/beauty-search` route, is NOT included by
// `LeaderboardPlugin`, does NOT parse environment/config/CLI, creates no runtime role, wires no Qdrant
// client/lifecycle/startup indexing, and never silently falls back from Qdrant to ES. Rollback is the
// explicit `EsOnlyRollback` selection back to the ES-only route, not a runtime fallback.
sealed trait BeautySearchQdrantSupplementActivation extends Product with Serializable

object BeautySearchQdrantSupplementActivation {
  case object EsOnlyRollback extends BeautySearchQdrantSupplementActivation
  case object QdrantSupplementNotReady extends BeautySearchQdrantSupplementActivation
  case object QdrantSupplementReady extends BeautySearchQdrantSupplementActivation
}
