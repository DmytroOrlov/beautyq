# M5 ES Lifecycle Checkpoint

Status: M5 is active/incomplete. This is a docs-only checkpoint. No production source, test, endpoint, or serving behavior was changed.

## Current status

- M4 is closed.
- M5 is active/incomplete.
- ES lifecycle/readiness work is currently non-serving only.
- Full verification remains separate from focused validation.

## Implemented non-serving seams

The following non-serving seams exist in source and are covered by focused tests:

- **Lifecycle metadata**: `ElasticsearchSeedLifecycleMetadata` exposes seed-only index name, source, document count, preparation mode, and lifecycle status through `ElasticsearchSeedIndexReadiness.lifecycleMetadata` and `ElasticsearchSeedSearchComposition.lifecycleMetadata`.
- **Readiness state**: `ElasticsearchProductionReadinessState.seedOnly` derives a pure internal state recording `NotEnforced` serving readiness, `NotConfigured` replacement, `NotTracked` freshness, `EagerSeedPreparationOnly` refresh, `NotConfigured` rollback, and `NotExposed` operator visibility.
- **Lifecycle status response**: `ElasticsearchLifecycleStatusResponse.from(state)` projects the state into a pure response model with local Circe encoding and `productionLifecycleComplete = false`. Not DI-bound or HTTP-exposed.
- **Startup transition**: `ElasticsearchStartupReadinessTransition` provides a pure prepared/operation-failure classification. Prepared values preserve readiness state and derive lifecycle status response; failures retain source-backed `QueryFailure.OperationFailure` data without lifecycle metadata or status response. Both record `NotEnforced` serving decision.
- **Startup status projection**: `ElasticsearchStartupReadinessStatusResponse` provides a pure non-serving startup status projection from transitions. Prepared projections include nested `ElasticsearchLifecycleStatusResponse`; failed projections expose operation/message only. Not DI-bound or HTTP-exposed.
- **Prepared transition bound through ES route graphs**: `ElasticsearchSeedSearchComposition.startupReadinessTransition` exposes a prepared transition derived from composition readiness state. `BeautySearchCatalogBackendModules.seedResourceElasticsearch` binds it through DI from the composition. The binding is non-serving and does not gate startup or route behavior.
- **Source-backed preparation failure classification**: `ElasticsearchStartupReadinessTransition.preparationFailed(...)` classifies `QueryFailure.OperationFailure` failures into `PreparationFailed` with operation name/message. Non-`OperationFailure` failures produce `UnsupportedFailure`. Classification is pure and does not change initializer behavior.
- **Cross-model consistency coverage**: `ElasticsearchReadinessConsistencySpec` proves field-level agreement across `ElasticsearchProductionReadinessState`, `ElasticsearchLifecycleStatusResponse`, `ElasticsearchStartupReadinessTransition`, `ElasticsearchStartupReadinessStatusResponse`, and `ElasticsearchSeedSearchComposition.startupReadinessTransition`. This is non-serving test coverage, not serving-gate enforcement.

## Current route/behavior status

- `/beauty-search` behavior is unchanged.
- No serving gate exists.
- No endpoint, route path, HTTP status policy, or operator policy is approved.
- No additional Elasticsearch calls are introduced by the non-serving status models.
- Production route is exposed through `LeaderboardPlugin.modules.apiBase[IO]` plus `BeautySearchRouteModules.apiElasticsearch`.
- The route performs eager seed index preparation during composition; this is an implementation fact, not an approved production lifecycle policy.

## Remaining production lifecycle gaps

The following production lifecycle capabilities are not implemented:

- **Serving-gate enforcement**: no startup readiness gate blocks or allows serving.
- **Startup failure HTTP/status policy**: no defined behavior for how startup failure is exposed to callers or operators.
- **Operator-visible endpoint/path/auth policy**: no endpoint exists, no route path is approved, no auth/operator visibility policy is defined.
- **Replacement/versioned-index or alias policy**: no index replacement strategy exists. `ElasticsearchProductionReadinessState.replacement` is `NotConfigured`.
- **Freshness tracking**: no freshness timestamp, version, or staleness detection exists. `ElasticsearchProductionReadinessState.freshness` is `NotTracked`.
- **Refresh trigger semantics**: no approved refresh trigger exists. `ElasticsearchProductionReadinessState.refresh` is `EagerSeedPreparationOnly`.
- **Rollback policy**: no rollback mechanism or retained rollback-supporting state exists. `ElasticsearchProductionReadinessState.rollback` is `NotConfigured`.
- **Dashboard/operator integration**: no operator-visible status surface is exposed. `ElasticsearchProductionReadinessState.operatorVisibility` is `NotExposed`.
- **Full production lifecycle verification**: no production lifecycle tests exist. Current focused tests cover seed-only metadata, non-serving state, status projection, and pure startup transition shape only.

## Enforcement prerequisites

Before any serving-gate or lifecycle enforcement can be implemented:

1. **Approve serving-gate policy** from `ES_STARTUP_SERVING_GATE_DESIGN.md`. Five choices are documented (fail closed until prepared, fail fast on preparation failure, continue serving with seed-only status, serve stale/previous index, operator override). The recommended default is fail closed until prepared.
2. **Approve endpoint/path/auth/operator visibility policy** or explicitly defer endpoint work. No endpoint exists; no route path is approved.
3. **Define prepared/failure route behavior** before code changes. What happens when startup succeeds vs fails must be decided before implementation.
4. **Define rollback/freshness/replacement boundaries** before claiming production lifecycle. These are separate design decisions, not implementation details.
5. **Add tests before enforcement**. Required tests are documented in `ES_STARTUP_SERVING_GATE_DESIGN.md`: prepared startup allows serving, failed startup blocks/fails per policy, no accidental Qdrant/hybrid fallback, no extra ES calls.
6. **Preserve no Qdrant/hybrid fallback**. Serving-gate enforcement must not introduce Qdrant or hybrid serving behavior.

## Source-confirmed next step

The first implementation slice is source-confirmed. Full analysis is in `docs/codebase-review/ES_STARTUP_SERVING_GATE_SOURCE_CONFIRMATION.md`.

Key findings:

- App-start fail-closed behavior is implicitly implemented by the eager composition pattern in `BeautySearchCatalogBackendModules.seedResourceElasticsearch`. If ES preparation fails, `unsafe.run` throws, DI graph construction fails, and no route is constructed.
- A runtime route gate (HTTP 503 on non-prepared state) requires a different source seam. The current DI-bound `ElasticsearchStartupReadinessTransition` is always `Prepared`; `PreparationFailed` is unreachable from the bound value.
- The smallest candidate enforcement seam is `BeautySearchApi.serverLogic`, but enforcement is currently impossible because the transition is always `Prepared`.
- The recommended next step is spec-only route-level tests proving app-start fail-closed and prepared-serving behavior before any enforcement code.

Policy gap: "fail closed until prepared" is recommended but not formally approved. App-start fail-closed is implicitly implemented; runtime route gate requires new source seam. Endpoint/path/auth/operator status policy remains unresolved.

## Safe implementation slices

Ordered and conservative:

1. **Source-confirm serving-gate implementation slice**: done. See `docs/codebase-review/ES_STARTUP_SERVING_GATE_SOURCE_CONFIRMATION.md`.
2. **Add spec-only route-level tests**: write route-level tests proving app-start fail-closed behavior (composition failure prevents route construction) and prepared-serving behavior. Do not implement enforcement yet.
3. **Implement serving gate only after tests/policy are approved**: add the serving gate to route composition only after tests prove the expected behavior and policy is formally approved.
4. **Add operator endpoint only after path/auth/status policy is approved**: implement an operator-visible status endpoint only after endpoint path, auth, and status policy are explicitly approved.
5. **Defer replacement/freshness/rollback until their policies are designed**: these are separate design decisions that must be made independently before implementation.

## Non-goals

This checkpoint does not imply or implement:

- No endpoint implementation.
- No route path approval.
- No serving enforcement in this docs task.
- No production lifecycle completion claim.
- No Qdrant/hybrid serving fallback.
- No route switch.
- No fallback.
- No score fusion.
- No reranking.
- No `HybridServe`.
- No Qdrant auto-supplement.

## References

- Serving-gate design: `docs/codebase-review/ES_STARTUP_SERVING_GATE_DESIGN.md`
- Lifecycle status design: `docs/codebase-review/ES_LIFECYCLE_STATUS_DESIGN.md`
- Current gaps and roadmap: `docs/codebase-review/07-current-gaps-and-roadmap.md`
- Architecture decisions: `docs/codebase-review/ARCHITECTURE_DECISIONS_OBSERVED.md`
- Tests and contracts: `docs/codebase-review/06-tests-and-contracts.md`
