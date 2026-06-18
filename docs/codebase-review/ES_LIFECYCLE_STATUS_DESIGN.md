# ES Lifecycle Status Design

Status: response model/encoder implemented; exposure design remains incomplete.

- No endpoint is implemented.
- No route path is approved.
- No serving behavior is changed.
- No production lifecycle completion is claimed.
- M5 remains incomplete.

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
- failed transitions retain source-backed `QueryFailure.OperationFailure` data but expose no lifecycle metadata or lifecycle status response;
- the transition is not wired into composition and its serving decision remains `NotEnforced`;
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

## Implemented boundary

Implemented:

- pure `ElasticsearchLifecycleStatusResponse` data model;
- `ElasticsearchLifecycleStatusResponse.from(state)` derivation;
- local Circe JSON encoding;
- focused pure mapping and exact-JSON tests;
- pure `ElasticsearchStartupReadinessTransition` success/failure classification, with prepared transition-to-status mapping and JSON alignment, no failure metadata/status response, and no serving enforcement.

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

If a future endpoint is approved, it should report the current seed-only state accurately before introducing any claim of production lifecycle completion.

Until that later task is approved and implemented:

- no endpoint exists;
- no route path is approved;
- the source model/encoder remains non-serving and unbound from route graphs;
- no auth/operator policy exists;
- no serving behavior changes;
- no production lifecycle completion claim is valid.
