# ES Lifecycle Status Design

Status: **Design A operator visibility endpoint implemented as explicit opt-in module.** Response model/encoder implemented and HTTP-exposed through `GET /ops/beauty-search/lifecycle`. Endpoint is NOT in the default ES route graph; available only through explicit opt-in modules (`seedCatalogElasticsearchWithOperatorVisibility`, `apiElasticsearchWithOperatorVisibility`). Response shape: `ElasticsearchStartupReadinessStatusResponse` (always `Prepared` variant) with nested `ElasticsearchLifecycleStatusResponse`. No new Elasticsearch calls. No `/beauty-search` behavior change. No runtime route gate or HTTP 503 behavior. Design B/C remain future. Full ES production lifecycle remains incomplete.

- Endpoint `GET /ops/beauty-search/lifecycle` is implemented as explicit opt-in/internal operator visibility endpoint.
- Endpoint is NOT in the default `apiElasticsearch` graph or `LeaderboardPlugin` production graph.
- No runtime serving behavior is changed.
- No production lifecycle completion is claimed.
- M5 is closed as a bounded startup-readiness lifecycle checkpoint. Full ES production lifecycle remains incomplete.

## Purpose

This document defines the planned operator-facing Elasticsearch lifecycle status shape for the current ES-backed `/beauty-search` route.

The purpose is to:

- make ES seed lifecycle/readiness visible to operators;
- distinguish seed-only readiness from production lifecycle readiness;
- support future startup readiness, replacement, freshness, refresh, rollback, and operator-visibility decisions.

This document does not approve an endpoint path, HTTP status policy, auth policy, polling policy, caching policy, or dashboard integration.

## Current source-backed internal inputs

The future operator-facing status shape should be assembled from current internal/source-backed inputs plus reserved future fields.

Current internal inputs:

- `ElasticsearchSeedLifecycleMetadata`
- `ElasticsearchProductionReadinessState`
- prepared `ElasticsearchStartupReadinessTransition` values
- `BeautySearchSpecV1.spec.variantDocument.indexName`
- `seed-resource-loader`
- `EagerSeedIndexPreparation`
- `SeedOnlyNotProductionLifecycle`
- `NotEnforced`
- `NotConfigured`
- `NotTracked`
- `EagerSeedPreparationOnly`
- `NotExposed`

Current route boundary:

```text
LeaderboardPlugin.modules.apiBase[IO]
+ BeautySearchRouteModules.apiElasticsearch
+ ES-backed seed route graph
```

Current lifecycle meaning:

- the route performs eager seed index preparation during composition;
- the route is serving today;
- the lifecycle state is still seed-only and non-serving;
- prepared `ElasticsearchStartupReadinessTransition` values derive the same non-serving status response as `ElasticsearchLifecycleStatusResponse.from(state)`;
- successful ES seed compositions now expose a prepared startup transition through `ElasticsearchSeedSearchComposition.startupReadinessTransition`, and it is bound through DI via `BeautySearchCatalogBackendModules.seedResourceElasticsearch`;
- source-backed preparation failures remain classifiable into `PreparationFailed` in pure tests without changing initializer behavior;
- failed transitions retain source-backed `QueryFailure.OperationFailure` data but expose no lifecycle metadata or lifecycle status response;
- the binding remains non-serving and its serving decision stays `NotEnforced`;
- startup serving gate, replacement, freshness tracking, refresh trigger policy, rollback, and operator-visible status are not implemented.

## Implemented non-serving JSON response shape

`ElasticsearchLifecycleStatusResponse` implements the planned field shape as a
pure, non-serving model derived from `ElasticsearchProductionReadinessState`.
Its companion provides the Circe encoder. The implemented shape is:

```json
{
  "indexName": "beautyq_variant_v1",
  "source": "seed-resource-loader",
  "documentCount": 66,
  "preparationMode": "eager_seed_index_preparation",
  "lifecycleStatus": "seed_only_not_production_lifecycle",
  "servingReadiness": "not_enforced",
  "replacement": "not_configured",
  "freshness": "not_tracked",
  "refresh": "eager_seed_preparation_only",
  "rollback": "not_configured",
  "operatorVisibility": "not_exposed",
  "productionLifecycleComplete": false
}
```

Notes:

- `documentCount` above is an example placeholder only, not an approved hardcoded production value.
- `productionLifecycleComplete` must be `false` for the current state.
- The model and encoder do not expose or approve an endpoint.

## Field semantics

### `indexName`

- Current/source-backed.
- Source: `BeautySearchSpecV1.spec.variantDocument.indexName`, currently `beautyq_variant_v1`.
- Meaning: the ES index name targeted by the current ES-backed seed route.

### `source`

- Current/source-backed.
- Source: `ElasticsearchSeedLifecycleMetadata.source`, currently `seed-resource-loader`.
- Meaning: the catalog document source label used to build the current ready-document snapshot.

### `documentCount`

- Current/source-backed.
- Source: `ElasticsearchSeedLifecycleMetadata.documentCount`.
- Meaning: the number of documents prepared for the current seed index readiness snapshot.
- Constraint: docs/examples may show a placeholder number only when marked as an example.

### `preparationMode`

- Current/source-backed.
- Source: `ElasticsearchSeedLifecycleMetadata.preparationMode`.
- Current value: `eager_seed_index_preparation`.
- Meaning: current preparation happens eagerly during seed route composition.
- Non-implication: this does not mean a startup readiness gate or refresh policy exists.

### `lifecycleStatus`

- Current/source-backed.
- Source: `ElasticsearchSeedLifecycleMetadata.lifecycleStatus`.
- Current value: `seed_only_not_production_lifecycle`.
- Meaning: the current route has seed-only lifecycle metadata, not a completed production lifecycle.

### `servingReadiness`

- Current/source-backed.
- Source: `ElasticsearchProductionReadinessState.servingReadiness`.
- Current value: `not_enforced`.
- Meaning: no startup serving-readiness gate is implemented today.

### `replacement`

- Current/source-backed as a gap value; future/reserved for behavior.
- Source: `ElasticsearchProductionReadinessState.replacement`.
- Current value: `not_configured`.
- Meaning: no replacement strategy is implemented today.

### `freshness`

- Current/source-backed as a gap value; future/reserved for behavior.
- Source: `ElasticsearchProductionReadinessState.freshness`.
- Current value: `not_tracked`.
- Meaning: no freshness timestamp/version/state is tracked today.

### `refresh`

- Current/source-backed as a gap value; future/reserved for behavior.
- Source: `ElasticsearchProductionReadinessState.refresh`.
- Current value: `eager_seed_preparation_only`.
- Meaning: the current route prepares the seed index eagerly, but no approved runtime refresh trigger exists.

### `rollback`

- Current/source-backed as a gap value; future/reserved for behavior.
- Source: `ElasticsearchProductionReadinessState.rollback`.
- Current value: `not_configured`.
- Meaning: no rollback mechanism is implemented today.

### `operatorVisibility`

- Current/source-backed as a gap value; future/reserved for exposure behavior.
- Source: `ElasticsearchProductionReadinessState.operatorVisibility`.
- Current value: `not_exposed`.
- Meaning: the status is not operator-visible today.

### `productionLifecycleComplete`

- Future response field with current fixed documented value.
- Current value: `false`.
- Meaning: summarizes whether startup readiness, replacement, freshness, refresh, rollback, and operator visibility are approved and implemented as a production lifecycle.
- Current rule: must remain `false` until the lifecycle contract is approved and implemented.

## Current documented values

Current values to preserve in any future operator-facing status implementation:

- `preparationMode: "eager_seed_index_preparation"`
- `lifecycleStatus: "seed_only_not_production_lifecycle"`
- `servingReadiness: "not_enforced"`
- `replacement: "not_configured"`
- `freshness: "not_tracked"`
- `refresh: "eager_seed_preparation_only"`
- `rollback: "not_configured"`
- `operatorVisibility: "not_exposed"`
- `productionLifecycleComplete: false`

## Non-serving startup status projection

`ElasticsearchStartupReadinessStatusResponse` is a pure non-serving startup status projection derived from `ElasticsearchStartupReadinessTransition`. It provides a unified JSON shape for both prepared and preparation-failed startup transitions.

Prepared projection:

- `transitionStatus = "prepared"`
- `servingDecision = "not_enforced"`
- `lifecycleStatus`: nested `ElasticsearchLifecycleStatusResponse` with the same fields as direct readiness-state projection
- `productionLifecycleComplete = false`

Failed projection:

- `transitionStatus = "preparation_failed"`
- `servingDecision = "not_enforced"`
- `operationName`: source-backed operation name from `QueryFailure.OperationFailure`
- `message`: source-backed failure message from `QueryFailure.OperationFailure`
- `productionLifecycleComplete = false`
- No lifecycle metadata, no `ElasticsearchLifecycleStatusResponse`, no nested `lifecycleStatus` field

JSON encoding is local to the model companion. The projection is not DI-bound, not HTTP-exposed, and does not implement an endpoint, route path, or operator policy.

## Implemented boundary

Implemented:

- pure `ElasticsearchLifecycleStatusResponse` data model;
- `ElasticsearchLifecycleStatusResponse.from(state)` derivation;
- local Circe JSON encoding;
- focused pure mapping and exact-JSON tests;
- pure `ElasticsearchStartupReadinessTransition` success/failure classification, with prepared transition-to-status mapping and JSON alignment, no failure metadata/status response, and no serving enforcement;
- pure `ElasticsearchStartupReadinessStatusResponse` non-serving startup status projection from prepared and failed transitions, with local Circe JSON encoding and focused pure tests;
- cross-model consistency coverage (`ElasticsearchReadinessConsistencySpec`) proving field-level agreement across readiness state, lifecycle response, startup transition, startup status projection, and composition-derived projections.

Not implemented:

- DI binding;
- HTTP route or endpoint;
- route path;
- HTTP status policy;
- auth/operator policy;
- dashboard integration.

## Non-approved items

The following items are explicitly not approved by this design document:

- endpoint path;
- HTTP status policy;
- authentication/authorization policy;
- polling/caching policy;
- operator dashboard integration.

## Startup serving-gate design status

The startup serving-gate design is documented separately in `docs/codebase-review/ES_STARTUP_SERVING_GATE_DESIGN.md`. Key facts:

- The serving-gate design is design-only; no runtime enforcement code is implemented.
- Design A endpoint `GET /ops/beauty-search/lifecycle` is implemented as explicit opt-in/internal operator visibility only; default ES graphs do not expose it.
- No serving-readiness enforcement exists.
- No preparation failure policy is implemented.
- No production lifecycle completion is claimed.
- M5 is closed as a bounded startup-readiness lifecycle checkpoint. Full ES production lifecycle remains incomplete.
- The serving-gate design defines five policy choices (fail closed until prepared, fail fast on preparation failure, continue serving with seed-only status, serve stale/previous index, operator override) that must be approved before any enforcement implementation.
- The recommended default is `fail closed until prepared`, but it is not implemented.
- Required future tests before any serving-gate implementation are documented in the design doc.

## Non-goals

This design does not imply or approve:

- route switch;
- fallback;
- score fusion;
- reranking;
- `HybridServe`;
- Qdrant auto-supplement;
- serving-readiness enforcement;
- preparation failure policy;
- replacement implementation;
- alias/versioned-index behavior;
- freshness timestamp implementation;
- refresh trigger implementation;
- rollback implementation.

## Implementation boundary

The implemented opt-in/internal endpoint reports the current seed-only state and does not claim production lifecycle completion. Broader operator policy, auth/config seams, and richer lifecycle states remain future work in the ES operator visibility track.

Until that later task is approved and implemented:

- no endpoint exists;
- no route path is approved;
- the source model/encoder remains non-serving and unbound from route graphs;
- no auth/operator policy exists;
- no serving behavior changes;
- no production lifecycle completion claim is valid.

## Operator visibility source-confirmation boundary

The operator visibility track is now source-confirmed. See `docs/codebase-review/ES_OPERATOR_VISIBILITY_SOURCE_CONFIRMATION.md` for the full analysis.

Source-confirmed facts:

- `ElasticsearchLifecycleStatusResponse` and `ElasticsearchStartupReadinessStatusResponse` are implemented as pure non-serving models with Circe encoders. They are now exposed through the explicit opt-in/internal Design A endpoint without new ES calls.
- The `Prepared` variant of `ElasticsearchStartupReadinessStatusResponse` (with nested `ElasticsearchLifecycleStatusResponse`) is always reachable from the DI-bound transition.
- Cross-model consistency is proven by `ElasticsearchReadinessConsistencySpec`.
- Failed transition projection shape is available from pure tests but unreachable from the DI-bound transition (always `Prepared`).
- Replacement, freshness, refresh, rollback, and operator override data are not available from current source models.
- The likely future endpoint seam follows existing Tapir/http4s patterns: new `*TapirEndpoints.scala` + new `*Api.scala` + DI wiring.
- Design A (expose current prepared/seed-only status) is the recommended next step. Design B (startup failure visibility) and Design C (richer status) remain future.

Policy decisions required before endpoint implementation:

- Endpoint path
- Public/private/internal exposure
- Auth/operator access model
- Response status code policy
- Prepared versus failed response shape
- Whether startup failure is visible when graph construction fails
- Whether status should be served from app-start captured state, runtime state, or static DI-bound prepared state
- Whether failed startup status requires a separate application bootstrap state outside successful route construction

## Design A endpoint policy draft

Design A endpoint/path/auth/status/response-shape policy is now drafted in `docs/codebase-review/ES_OPERATOR_VISIBILITY_ENDPOINT_POLICY.md`. Draft recommendations:

- Endpoint path: `GET /ops/beauty-search/lifecycle`
- Auth: disabled unless explicitly enabled
- HTTP status: always `200 OK` for successful retrieval
- Response shape: `ElasticsearchStartupReadinessStatusResponse` (always `Prepared` variant) with nested `ElasticsearchLifecycleStatusResponse`

Design A is implemented as explicit opt-in/internal operator visibility. Default ES graphs do not expose it. Module-level opt-in is the only implemented exposure policy; broader auth/config policy remains future. `ElasticsearchOperatorVisibilityEndpointPolicySpec.scala` now provides active default-absence, opt-in-presence, exact response-shape, in-memory-absence, and no-new-ES-calls coverage. Implementation slice remains source-confirmed in `ES_OPERATOR_VISIBILITY_IMPLEMENTATION_SOURCE_CONFIRMATION.md`.
