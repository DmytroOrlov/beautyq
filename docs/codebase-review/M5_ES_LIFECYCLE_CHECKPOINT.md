# M5 Startup-Readiness Lifecycle Checkpoint — Closeout

Status: M5 is closed as a bounded startup-readiness lifecycle checkpoint. Full ES production lifecycle remains incomplete and moves to named future tracks. Those future tracks are separate post-M5 planning, not an unaccepted M5 remainder. M5 itself did not change default production serving behavior; post-M5, Design A operator visibility landed and was hardened as explicit opt-in/internal endpoint exposure.

## Closeout decision

M5 is closed as a bounded startup-readiness lifecycle checkpoint. Source truth supports closure: all non-serving lifecycle seams are implemented and test-covered, app-start fail-closed and prepared-serving behavior are proven, and the remaining production lifecycle work is clearly bounded and unstarted.

## Bounded M5 definition

M5 covers:

- App-start fail-closed behavior (implicit from eager composition, test-covered by `ElasticsearchAppStartServingGateSpec`).
- Prepared-serving behavior (successful composition allows `POST /beauty-search`, test-covered by `ElasticsearchAppStartServingGateSpec`).
- Non-serving lifecycle metadata: `ElasticsearchSeedLifecycleMetadata` exposes seed-only index name, source, document count, preparation mode, and lifecycle status through `ElasticsearchSeedIndexReadiness.lifecycleMetadata` and `ElasticsearchSeedSearchComposition.lifecycleMetadata`.
- Non-serving readiness state: `ElasticsearchProductionReadinessState.seedOnly` derives a pure internal state recording `NotEnforced` serving readiness, `NotConfigured` replacement, `NotTracked` freshness, `EagerSeedPreparationOnly` refresh, `NotConfigured` rollback, and `NotExposed` operator visibility.
- Non-serving lifecycle status response model/encoder: `ElasticsearchLifecycleStatusResponse.from(state)` projects the state into a pure response model with local Circe encoding and `productionLifecycleComplete = false`. Not DI-bound or HTTP-exposed.
- Non-serving startup transition shape: `ElasticsearchStartupReadinessTransition` provides a pure prepared/operation-failure classification. Prepared values preserve readiness state and derive lifecycle status response; failures retain source-backed `QueryFailure.OperationFailure` details without lifecycle metadata or a status response. Both record `NotEnforced` serving decision.
- Non-serving startup status projection: `ElasticsearchStartupReadinessStatusResponse` provides a pure non-serving startup status projection from transitions. Prepared projections include the nested `ElasticsearchLifecycleStatusResponse`; failed projections expose operation/message only. Not DI-bound or HTTP-exposed.
- DI/rooting of prepared transition through ES seed route graphs: `ElasticsearchSeedSearchComposition.startupReadinessTransition` exposes a prepared transition derived from composition readiness state. `BeautySearchCatalogBackendModules.seedResourceElasticsearch` binds it through DI. The binding is non-serving and does not gate startup or route behavior.
- Source-backed failure classification: `ElasticsearchStartupReadinessTransition.preparationFailed(...)` classifies `QueryFailure.OperationFailure` failures into `PreparationFailed` with operation name/message. Non-`OperationFailure` failures produce `UnsupportedFailure`. Classification is pure and does not change initializer behavior.
- Cross-model consistency coverage: `ElasticsearchReadinessConsistencySpec` proves field-level agreement across readiness state, lifecycle response, startup transition, startup status projection, and composition-derived projections. This is non-serving test coverage, not serving-gate enforcement.
- Startup serving-gate design documented in `ES_STARTUP_SERVING_GATE_DESIGN.md`.
- Source-confirmed implementation slice analysis in `ES_STARTUP_SERVING_GATE_SOURCE_CONFIRMATION.md`.

M5 does not include:

- Runtime HTTP gate (route returns HTTP 503 on non-prepared state).
- Operator-visible endpoint/path/auth policy.
- Replacement/versioned-index/alias policy.
- Freshness tracking.
- Refresh trigger semantics.
- Rollback policy.
- Full lifecycle operations: runtime rebuild/refresh, replacement activation, rollback, disable/kill-switch, stale/current/previous catalog operation state, operator-triggered commands, and broader lifecycle auth/config/visibility policy beyond explicit opt-in/internal Design A.
- Dashboard/operator integration.
- Full production lifecycle verification.

## Current route/behavior status

- `/beauty-search` behavior is unchanged.
- No serving gate exists.
- Design A operator visibility endpoint `GET /ops/beauty-search/lifecycle` is now implemented and hardened as explicit opt-in/internal endpoint (post-M5). NOT in the default ES route graph; available only through `BeautySearchRouteModules.seedCatalogElasticsearchWithOperatorVisibility` / `apiElasticsearchWithOperatorVisibility`. Default `apiElasticsearch`, `LeaderboardPlugin` production graph, and the in-memory graph do NOT expose the endpoint. Returns `ElasticsearchStartupReadinessStatusResponse.Prepared` with nested `ElasticsearchLifecycleStatusResponse`. No new ES calls. No `/beauty-search` behavior change. No runtime route gate or HTTP 503 behavior. Broader auth/config seam, local/dev fallback, Design B, and Design C remain future.
- No additional Elasticsearch calls are introduced by the non-serving status models or the operator visibility endpoint.
- Production route is exposed through `LeaderboardPlugin.modules.apiBase[IO]` plus `BeautySearchRouteModules.apiElasticsearch`.
- The route performs eager seed index preparation during composition; this is an implementation fact, not an approved production lifecycle policy.
- Source-confirmed current absence remains explicit: no stale/previous index state, no alias/versioned-index replacement policy, no freshness/staleness policy, no refresh trigger semantics beyond eager seed preparation, no rollback policy, and no runtime lifecycle operations implementation.

## Source/test evidence justifying closure

The following tests justify the M5 closeout decision:

1. **`ElasticsearchAppStartServingGateSpec`** — proves app-start fail-closed behavior at composition level (blank source, empty documents, ES client failure all prevent composition) and DI-graph level (ES client failure prevents `BeautySearchApi` construction; no route instance is produced). Also proves prepared-serving: successful composition produces `Prepared` transition and `POST /beauty-search` returns `200 OK`. These tests document current implicit behavior only; they are not runtime HTTP 503 gate tests.

2. **`ElasticsearchReadinessConsistencySpec`** — proves cross-model field-level agreement across `ElasticsearchProductionReadinessState`, `ElasticsearchLifecycleStatusResponse`, `ElasticsearchStartupReadinessTransition`, `ElasticsearchStartupReadinessStatusResponse`, and `ElasticsearchSeedSearchComposition.startupReadinessTransition`.

3. **`ElasticsearchSeedIndexReadinessSpec`** — pins pure `ElasticsearchProductionReadinessState.seedOnly` derivation and source-backed failure classification.

4. **`ElasticsearchSeedSearchCompositionSpec`** — pins `productionReadinessState` derivation from composition lifecycle metadata and exposes a prepared startup transition.

5. **`ElasticsearchLifecycleStatusResponseSpec`** — pins exact state-to-response mapping, metadata-sourced document counts, exact Circe field names/current string values, and `productionLifecycleComplete = false`.

6. **`ElasticsearchStartupReadinessTransitionSpec`** — pins prepared-state preservation, transition-to-status mapping, JSON equality, failure classification, and `NotEnforced` serving decision.

7. **`ElasticsearchStartupReadinessStatusResponseSpec`** — pins prepared and failed projection derivation, JSON shapes, and `transitionStatus`/`servingDecision`/`productionLifecycleComplete` values.

8. **`BeautySearchProductionRouteExposureSpec`** — proves production API graph exposes the ES-backed route and roots the prepared transition.

9. **`BeautySearchElasticsearchRouteModuleSpec`**, **`BeautySearchElasticsearchHttpRouteModuleSpec`**, **`BeautySearchElasticsearchDefaultReadyRouteSpec`** — prove lifecycle metadata, readiness state, and prepared transition are materialized through ES route graphs.

## Future ES lifecycle tracks

Remaining ES production lifecycle work moves to named future tracks. These tracks are independent of the Qdrant/hybrid roadmap milestones (M6/M7/M8) and do not overwrite those milestone meanings.

| Track | Scope | Dependencies | Current status |
|-------|-------|-------------|----------------|
| ES operator visibility track | Operator-visible lifecycle/status endpoint design and policy: endpoint path, HTTP status, auth/operator policy, status fields distinguishing seed-only/preparing/ready/failed/stale/rollback/disabled states | M5 closeout; Design A closed/implemented; module-level opt-in/internal exposure is the only realized policy | Design A is implemented as explicit opt-in/internal endpoint. `GET /ops/beauty-search/lifecycle` is available only through `BeautySearchRouteModules.seedCatalogElasticsearchWithOperatorVisibility` / `apiElasticsearchWithOperatorVisibility`. Default `seedCatalogElasticsearch`, default `apiElasticsearch`, `LeaderboardPlugin.modules.apiBase[IO] + BeautySearchRouteModules.apiElasticsearch`, and the in-memory graph do NOT expose it. Response shape is `ElasticsearchStartupReadinessStatusResponse.Prepared` with nested `ElasticsearchLifecycleStatusResponse`. `200 OK`, no new ES calls, no `/beauty-search` behavior change. No runtime route gate. Broader auth/config seam, local/dev fallback, Design B, and Design C remain future. |
| ES runtime serving-gate track | Runtime route-gate policy/implementation: route returns approved HTTP error (e.g., 503) on non-prepared state; requires new source seam because current DI-bound transition is always `Prepared` | M5 closeout; serving-gate policy approval if/when a runtime readiness source exists; later replacement/freshness/rollback policy may also be required | Not started; source-confirmed as requiring different seam in `ES_STARTUP_SERVING_GATE_SOURCE_CONFIRMATION.md`; recommended to stay deferred for Candidate A |
| ES replacement/freshness/rollback track | Catalog replacement source-of-truth policy, replacement/versioned-index/alias policy, freshness tracking, refresh trigger semantics, rollback policy, stale/current/previous state observability | M5 closeout; individual policy designs approved | Not started; `ElasticsearchProductionReadinessState` records `NotConfigured`/`NotTracked`/`EagerSeedPreparationOnly`; there is no stale/previous index state today |
| ES full lifecycle operations track | Runtime operator command surface and ownership, authorization/config policy, rebuild/refresh operation semantics, replacement activation semantics, rollback semantics, disable/kill-switch semantics, current/previous/stale state transitions, and operation progress/failure status | M5 closeout; replacement/freshness/rollback and any serving-gate interaction clarified first or alongside | Not started; no runtime lifecycle operation implementation exists today |

These tracks can proceed independently. The ES operator visibility track is already implemented as Design A. The ES runtime serving-gate track remains future work and is currently recommended to stay deferred behind a runtime readiness source or replacement/freshness/rollback policy. The ES replacement/freshness/rollback track requires individual policy designs. The ES full lifecycle operations track remains entirely future and unimplemented.

Dashboard/operator integration and full production lifecycle verification remain downstream of these tracks.

## Non-goals

This closeout does not imply or implement:

- No default/public endpoint exposure.
- No runtime auth/config policy beyond module-level opt-in.
- No serving enforcement.
- No production lifecycle completion claim.
- No Qdrant/hybrid serving fallback.
- No route switch.
- No fallback.
- No score fusion.
- No reranking.
- No `HybridServe`.
- No Qdrant auto-supplement.
- No runtime HTTP gate implementation.
- No Design B startup-failure endpoint.
- No Design C replacement/freshness/rollback-rich endpoint.
- No replacement/freshness/rollback implementation.

## References

- Implementation-slice source confirmation: `docs/codebase-review/ES_OPERATOR_VISIBILITY_IMPLEMENTATION_SOURCE_CONFIRMATION.md`
- Operator visibility source confirmation: `docs/codebase-review/ES_OPERATOR_VISIBILITY_SOURCE_CONFIRMATION.md`
- Serving-gate design: `docs/codebase-review/ES_STARTUP_SERVING_GATE_DESIGN.md`
- Lifecycle status design: `docs/codebase-review/ES_LIFECYCLE_STATUS_DESIGN.md`
- Source confirmation: `docs/codebase-review/ES_STARTUP_SERVING_GATE_SOURCE_CONFIRMATION.md`
- Current gaps and roadmap: `docs/codebase-review/07-current-gaps-and-roadmap.md`
- Architecture decisions: `docs/codebase-review/ARCHITECTURE_DECISIONS_OBSERVED.md`
- Tests and contracts: `docs/codebase-review/06-tests-and-contracts.md`
