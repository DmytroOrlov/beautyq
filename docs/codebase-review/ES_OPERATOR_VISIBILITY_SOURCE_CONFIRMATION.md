# ES Operator Visibility Source Confirmation

Status: source-confirmed; operator visibility is the next future track after M5 closeout. M5 is closed as a bounded startup-readiness lifecycle checkpoint. Full ES production lifecycle remains incomplete. No production source, endpoint, or serving behavior was changed. No endpoint is implemented; no route path is approved.

## Purpose

Source-confirm the ES operator visibility track before any endpoint implementation. Identify exact current source models that could feed an operator-visible status. Identify exact missing policy decisions before endpoint work.

## Source-confirmed models available for operator visibility

### Models that can be exposed later without new ES calls

The following pure/non-serving models are implemented, test-covered, and available for future operator-visible status exposure. None require new Elasticsearch calls.

1. **`ElasticsearchLifecycleStatusResponse`** (`ElasticsearchLifecycleStatusResponse.scala`)
   - Pure case class with 12 fields: `indexName`, `source`, `documentCount`, `preparationMode`, `lifecycleStatus`, `servingReadiness`, `replacement`, `freshness`, `refresh`, `rollback`, `operatorVisibility`, `productionLifecycleComplete`.
   - Companion provides `from(state: ElasticsearchProductionReadinessState)` derivation and local Circe `Encoder.AsObject`.
   - Not DI-bound; not HTTP-exposed.
   - Test coverage: `ElasticsearchLifecycleStatusResponseSpec` pins exact field values, Circe field names, and `productionLifecycleComplete = false`.

2. **`ElasticsearchStartupReadinessStatusResponse`** (`ElasticsearchStartupReadinessStatusResponse.scala`)
   - Sealed trait with two variants:
     - `Prepared(lifecycleStatus: ElasticsearchLifecycleStatusResponse)` — includes nested lifecycle status.
     - `PreparationFailed(operationName: String, message: String)` — operation/message only, no lifecycle metadata.
   - Common fields: `transitionStatus`, `servingDecision`, `productionLifecycleComplete`.
   - Companion provides `from(transition: ElasticsearchStartupReadinessTransition)` and local Circe `Encoder`.
   - Not DI-bound; not HTTP-exposed.
   - Test coverage: `ElasticsearchStartupReadinessStatusResponseSpec` pins prepared/failed projection derivation, JSON shapes, and values.

3. **`ElasticsearchStartupReadinessTransition`** (`ElasticsearchStartupReadinessTransition.scala`)
   - Sealed trait with two variants:
     - `Prepared(state, servingDecision)` — preserves `ElasticsearchProductionReadinessState`, exposes `lifecycleMetadata` and `lifecycleStatusResponse`.
     - `PreparationFailed(operationName, message, servingDecision)` — operation/message only, no lifecycle metadata or status response.
   - Both record `ElasticsearchStartupServingDecision.NotEnforced`.
   - `preparationFailed(failure: QueryFailure)` classifies `OperationFailure` into `PreparationFailed`; non-`OperationFailure` produces `UnsupportedFailure`.
   - DI-bound via `BeautySearchCatalogBackendModules.seedResourceElasticsearch` from `ElasticsearchSeedSearchComposition.startupReadinessTransition`.
   - Test coverage: `ElasticsearchStartupReadinessTransitionSpec` pins prepared/failure classification, `NotEnforced`, and unsupported failure handling.

4. **`ElasticsearchProductionReadinessState`** (`ElasticsearchProductionReadinessState.scala`)
   - Pure case class with fields: `lifecycleMetadata`, `servingReadiness`, `replacement`, `freshness`, `refresh`, `rollback`, `operatorVisibility`.
   - `seedOnly(lifecycleMetadata)` derives current non-serving values: `NotEnforced`, `NotConfigured`, `NotTracked`, `EagerSeedPreparationOnly`, `NotConfigured`, `NotExposed`.
   - DI-bound via `BeautySearchCatalogBackendModules.seedResourceElasticsearch`.
   - Test coverage: `ElasticsearchSeedIndexReadinessSpec` and `ElasticsearchReadinessConsistencySpec`.

5. **`ElasticsearchSeedLifecycleMetadata`** (`ElasticsearchSeedIndexReadiness.scala`)
   - Non-serving metadata with `indexName`, `source`, `documentCount`, `preparationMode`, `lifecycleStatus`.
   - DI-bound via `BeautySearchCatalogBackendModules.seedResourceElasticsearch`.
   - Test coverage: `ElasticsearchSeedIndexReadinessSpec`, route/module specs.

6. **App-start fail-closed evidence** — Implicit from eager composition pattern. If `ElasticsearchSeedSearchComposition.build` fails, `unsafe.run` throws, Distage graph construction fails, no route is constructed. Test-covered by `ElasticsearchAppStartServingGateSpec` at composition level and DI-graph level.

7. **Failed transition projection shape** — Available from pure tests. `PreparationFailed` projections expose `transitionStatus = "preparation_failed"`, `servingDecision = "not_enforced"`, `operationName`, `message`, `productionLifecycleComplete = false`. No lifecycle metadata, no `ElasticsearchLifecycleStatusResponse`, no nested `lifecycleStatus` field. Currently unreachable from DI-bound transition (always `Prepared`).

### Cross-model consistency

`ElasticsearchReadinessConsistencySpec` proves field-level agreement across:
- `ElasticsearchProductionReadinessState`
- `ElasticsearchLifecycleStatusResponse`
- `ElasticsearchStartupReadinessTransition`
- `ElasticsearchStartupReadinessStatusResponse`
- `ElasticsearchSeedSearchComposition.startupReadinessTransition`

This is non-serving test coverage, not serving-gate enforcement.

## Data that cannot yet be exposed from current runtime graph

The following data is not available from the current source models or runtime graph:

1. **Failed startup transition from a successfully constructed route graph** — The DI-bound `ElasticsearchStartupReadinessTransition` is always `Prepared` because eager composition either succeeds (producing `Prepared`) or fails (preventing route construction entirely). `PreparationFailed` is unreachable from the bound value.

2. **Replacement/versioned-index state** — `ElasticsearchReplacementReadiness.NotConfigured` is the only variant. No replacement strategy, versioned index names, or alias behavior exists.

3. **Freshness timestamp/age** — `ElasticsearchFreshnessReadiness.NotTracked` is the only variant. No timestamp, version, or age tracking exists.

4. **Refresh trigger state** — `ElasticsearchRefreshReadiness.EagerSeedPreparationOnly` is the only variant. No manual, scheduled, or external trigger exists.

5. **Rollback state** — `ElasticsearchRollbackReadiness.NotConfigured` is the only variant. No rollback mechanism exists.

6. **Dashboard/operator override state** — `ElasticsearchOperatorVisibility.NotExposed` is the only variant. No override surface exists.

## Candidate endpoint seam

### Current API patterns inspected

- Pure Tapir endpoint contracts live in `leaderboard/http/tapir/*TapirEndpoints.scala`.
- Thin API adapters live in `leaderboard.api.*Api`.
- Each adapter uses `Http4sServerInterpreter` directly.
- `HttpApiFailureTapirSupport` provides `errorOutput` (400 BadRequest, 500 InternalServerError) and `endpointBase`.
- `LeaderboardPlugin.modules.apiBase[F]` binds Tapir endpoint singletons, API adapters, and contributes to `many[HttpApi[F]]`.
- `BeautySearchPluginModules.api[F]` creates `BeautySearchApi[F]` from `BeautySearchService[F]` and contributes to `many[HttpApi[F]].weak[...]`.

### Likely future file/module seam

Based on the existing pattern:

- **Endpoint contract**: new file `leaderboard/http/tapir/EsLifecycleStatusTapirEndpoints.scala` (or similar) defining a pure endpoint contract for the operator status endpoint.
- **API adapter**: new file `leaderboard/api/EsLifecycleStatusApi.scala` (or similar) as a thin Tapir adapter.
- **DI wiring**: new module or addition to existing `BeautySearchCatalogBackendModules` or `BeautySearchRouteModules` to bind the new API and contribute to `many[HttpApi[F]]`.
- **Existing enforcement seam**: `BeautySearchApi.serverLogic` (`BeautySearchApi.scala:21-29`) remains the smallest candidate seam for serving-gate enforcement, but that is the runtime serving-gate track, not the operator visibility track.

No endpoint, route path, HTTP status policy, or auth/operator policy is approved. The seam analysis above is structural observation only.

## Policy decisions required before endpoint work

The following policy decisions must be explicitly approved before any operator visibility endpoint implementation:

1. **Endpoint path** — e.g., `GET /es-lifecycle-status`, `GET /internal/es-status`, or alternative.
2. **Public/private/internal exposure** — Whether the endpoint is public, private (internal network only), or requires explicit operator access.
3. **Auth/operator access model** — Whether the endpoint requires authentication, authorization, operator role, or is open.
4. **Response status code policy** — HTTP 200 with status body, or alternative codes for different states.
5. **Prepared versus failed response shape** — Whether the endpoint returns the same shape for prepared and failed states, or uses different HTTP status codes.
6. **Whether startup failure is visible when graph construction fails** — Currently, if composition fails, no route is constructed. Making startup failure visible requires a different seam (Design B below).
7. **Whether status should be served from app-start captured state, runtime state, or static DI-bound prepared state** — Current DI-bound transition is always `Prepared` from app-start captured state.
8. **Whether failed startup status requires a separate application bootstrap state outside successful route construction** — To expose startup failure, a bootstrap-level state must exist outside the route graph.

## Operator visibility designs

### Design A: Expose only current successful prepared/seed-only status from constructed route graph

**Definition:** Expose the current `ElasticsearchStartupReadinessStatusResponse` (always `Prepared` variant with nested `ElasticsearchLifecycleStatusResponse`) through an operator-visible endpoint. No startup failure visibility.

**Source files likely touched later:**
- New `leaderboard/http/tapir/EsLifecycleStatusTapirEndpoints.scala`
- New `leaderboard/api/EsLifecycleStatusApi.scala`
- `leaderboard/plugins/BeautySearchCatalogBackendModules.scala` or `BeautySearchRouteModules.scala` — DI wiring for new API

**Test files likely touched later:**
- New `EsLifecycleStatusApiHttpContractSuite.scala` — endpoint contract tests
- New or updated route/module specs proving status endpoint is materialized through route graphs

**Policy decisions required:**
- Endpoint path
- Public/private/internal exposure
- Auth/operator access model
- Response status code policy
- Whether to return `Prepared` status only (always the case with current seam)

**Behavior risk:** Low (additive). Does not change `/beauty-search` serving behavior.

**Changes `/beauty-search`:** No.

**Needs new ES calls:** No. Uses existing DI-bound models.

**Source-backed readiness:** Current source models (`ElasticsearchStartupReadinessStatusResponse`, `ElasticsearchLifecycleStatusResponse`) are implemented with Circe encoders. The `Prepared` variant is always reachable from the DI-bound transition. This design requires only endpoint/path/auth policy approval.

### Design B: Expose startup failure status via app/bootstrap-level state outside route graph

**Definition:** Capture startup preparation result (including `PreparationFailed`) at application bootstrap time, before route graph construction, and expose it through an operator-visible endpoint. This requires a new source seam where the bootstrap captures failure state and makes it available even when route construction fails.

**Source files likely touched later:**
- New bootstrap-level state capture (e.g., `ElasticsearchBootstrapState` or similar)
- `BeautySearchCatalogBackendModules.seedResourceElasticsearch` — change from eager to deferred or add bootstrap state capture
- New `leaderboard/http/tapir/EsLifecycleStatusTapirEndpoints.scala`
- New `leaderboard/api/EsLifecycleStatusApi.scala`
- Possibly `ElasticsearchSeedSearchComposition.scala` — expose failed transition through composition

**Test files likely touched later:**
- New bootstrap state capture tests
- New endpoint contract tests covering both prepared and failed states
- Updated route/module specs

**Policy decisions required:**
- All Design A decisions
- Whether to change from eager to deferred composition
- How to capture and persist bootstrap failure state
- Whether the endpoint serves stale failure state after restart
- Whether failed startup status requires a separate HTTP status code

**Behavior risk:** Medium. Changes composition pattern and introduces bootstrap state.

**Changes `/beauty-search`:** Potentially, if composition changes from eager to deferred.

**Needs new ES calls:** No new ES calls, but changes when and how composition runs.

**Source-backed readiness:** Not ready. Requires new source seam design. Current eager composition prevents failed transition from being reachable.

### Design C: Expose richer lifecycle status only after replacement/freshness/rollback policies exist

**Definition:** Defer operator visibility endpoint until replacement, freshness, refresh, and rollback policies are designed and implemented. The endpoint would expose the full `ElasticsearchLifecycleStatusResponse` with non-trivial values for all fields.

**Source files likely touched later:**
- All Design A files
- Plus: replacement, freshness, refresh, rollback implementation files (not yet designed)

**Test files likely touched later:**
- All Design A tests
- Plus: replacement, freshness, refresh, rollback tests

**Policy decisions required:**
- All Design A decisions
- Replacement/versioned-index/alias policy
- Freshness tracking policy
- Refresh trigger semantics
- Rollback policy
- Dashboard/operator integration policy

**Behavior risk:** Low for the endpoint itself (additive), but blocked on upstream policy work.

**Changes `/beauty-search`:** No for the endpoint; potentially for upstream lifecycle policies.

**Needs new ES calls:** Potentially, depending on replacement/freshness/refresh policies.

**Source-backed readiness:** Not ready. Blocked on upstream policy designs that are not yet started.

## Recommended next step

**Recommend Design A: design-only endpoint/path/auth policy if current source truth is sufficient.**

Rationale:

- Current source models (`ElasticsearchStartupReadinessStatusResponse`, `ElasticsearchLifecycleStatusResponse`) are implemented with Circe encoders.
- The `Prepared` variant is always reachable from the DI-bound transition.
- Cross-model consistency is proven by `ElasticsearchReadinessConsistencySpec`.
- Design A is additive and does not change `/beauty-search` serving behavior.
- Design A requires only policy approval (endpoint path, auth, HTTP status) before implementation.
- Design B requires a new source seam and is blocked on composition pattern changes.
- Design C is blocked on upstream replacement/freshness/rollback policies.

**Recommended exact next step:** Resolve endpoint path, auth/operator access model, and response status code policy for Design A. Then implement the endpoint with the current `Prepared`-only status. Startup failure visibility (Design B) and richer lifecycle status (Design C) remain separate future work.

## Design A endpoint policy draft

Design A endpoint/path/auth/status/response-shape policy is now drafted in `docs/codebase-review/ES_OPERATOR_VISIBILITY_ENDPOINT_POLICY.md`. The policy draft covers:

- Endpoint path candidates (4 candidates) with draft recommendation (`GET /ops/beauty-search/lifecycle`)
- Auth/exposure policy options (4 options) with draft recommendation (disabled unless explicitly enabled)
- HTTP status code policy options (3 options) with draft recommendation (always `200 OK`)
- Response shape candidates (3 candidates) with draft recommendation (`ElasticsearchStartupReadinessStatusResponse`)
- Implementation prerequisites
- Future tests required before implementation
- Future implementation seams (source-confirmed, not approved)

Draft endpoint expectations are now captured in pending specs: `ElasticsearchOperatorVisibilityEndpointPolicySpec.scala` encodes Design A draft expectations as 28 pending tests using ScalaTest `pending` mechanism. These are pending expectations and not implementation proof.

All draft recommendations remain unapproved. No endpoint is implemented. No route path is approved.

## Non-goals

This source confirmation does not:

- implement an endpoint;
- approve a route path;
- approve an HTTP status policy;
- approve an auth/operator policy;
- change any source or test file;
- claim production lifecycle completion;
- claim operator visibility is implemented.

## References

- Lifecycle status design: `docs/codebase-review/ES_LIFECYCLE_STATUS_DESIGN.md`
- Serving-gate design: `docs/codebase-review/ES_STARTUP_SERVING_GATE_DESIGN.md`
- Serving-gate source confirmation: `docs/codebase-review/ES_STARTUP_SERVING_GATE_SOURCE_CONFIRMATION.md`
- M5 checkpoint: `docs/codebase-review/M5_ES_LIFECYCLE_CHECKPOINT.md`
- Current gaps and roadmap: `docs/codebase-review/07-current-gaps-and-roadmap.md`
- Tests and contracts: `docs/codebase-review/06-tests-and-contracts.md`
