# ES Startup Serving Gate Design

Status: design-only. M5 is closed as a bounded startup-readiness lifecycle checkpoint. Runtime route-gate work is source-confirmed as a separate future track and is currently recommended to stay deferred behind a runtime readiness source or replacement/freshness/rollback policy.

- No endpoint implemented.
- No route path approved.
- No serving gate implemented.
- No startup readiness enforcement.
- No production lifecycle completion.
- App-start fail-closed behavior is test-covered by `ElasticsearchAppStartServingGateSpec`. These tests document current implicit behavior only; they are not runtime HTTP 503 gate tests.
- Prepared-serving behavior is test-covered by `ElasticsearchAppStartServingGateSpec`. These tests document that successful composition allows serving; serving behavior is unchanged.
- Runtime route gate (HTTP 503 on non-prepared state) remains not implemented and requires a new source seam. Current source truth supports app-start fail-closed only; runtime serving-gate work stays deferred. See `docs/codebase-review/ES_RUNTIME_ROUTE_GATE_POLICY.md`.
- See `docs/codebase-review/M5_ES_LIFECYCLE_CHECKPOINT.md` for closeout checkpoint.

## Purpose

This document defines the startup serving-gate policy design for the ES-backed `/beauty-search` route. It is a prerequisite for any enforcement implementation. It does not implement any code, change any route behavior, or approve any endpoint or path.

## Current state

### Successful composition exposes `Prepared`

When `ElasticsearchSeedSearchComposition.build` succeeds:

- `ElasticsearchSeedSearchComposition.startupReadinessTransition` returns `ElasticsearchStartupReadinessTransition.Prepared(state)`.
- The prepared state preserves `ElasticsearchProductionReadinessState` with current seed-only values.
- `BeautySearchCatalogBackendModules.seedResourceElasticsearch` binds the prepared transition through DI.
- The bound transition is non-serving: its `servingDecision` is `NotEnforced`.
- The route continues to serve `/beauty-search` without gating.

Source-backed evidence:

- `ElasticsearchSeedSearchComposition.startupReadinessTransition` in `ElasticsearchSeedSearchComposition.scala:20-21`.
- `ElasticsearchStartupReadinessTransition.prepared(state)` preserves state and sets `NotEnforced` in `ElasticsearchStartupReadinessTransition.scala:34-38`.
- DI binding in `BeautySearchCatalogBackendModules.seedResourceElasticsearch` at `BeautySearchCatalogBackendModules.scala:77-79`.
- Focused route/module specs root and assert the prepared transition through the ES seed route, explicit ES seed route module, real HTTP-client ES route, port-configured default route, and production API graph.

### Failures can be classified as `PreparationFailed`

Source-backed initializer failure paths (blank source, empty documents, ES client failure) are classifiable into `PreparationFailed` through `ElasticsearchStartupReadinessTransition.preparationFailed(failure)`:

- `QueryFailure.OperationFailure` failures produce `PreparationFailed` with operation name and message.
- Non-`OperationFailure` failures produce `UnsupportedFailure` and are not classified.
- Both prepared and failed transitions record `ElasticsearchStartupServingDecision.NotEnforced`.

Source-backed evidence:

- `ElasticsearchStartupReadinessTransition.preparationFailed` in `ElasticsearchStartupReadinessTransition.scala:40-54`.
- `ElasticsearchStartupReadinessTransitionSpec.scala` pins prepared/failure classification, `NotEnforced`, and unsupported failure handling.

### Prepared and failed transitions both use `NotEnforced`

Both `Prepared` and `PreparationFailed` transitions record `servingDecision = ElasticsearchStartupServingDecision.NotEnforced`. Neither outcome gates or changes serving behavior.

Source-backed evidence:

- `ElasticsearchStartupReadinessTransition.scala:37` (prepared) and `ElasticsearchStartupReadinessTransition.scala:49` (failure).

### Startup status projection exists but is non-serving

`ElasticsearchStartupReadinessStatusResponse` is a pure, non-serving startup status projection from `ElasticsearchStartupReadinessTransition`:

- Prepared projections include the nested `ElasticsearchLifecycleStatusResponse`.
- Failed projections expose operation/message only, without lifecycle metadata or status response fields.
- Local Circe encoding is provided.
- The projection is not DI-bound or HTTP-exposed.

Source-backed evidence:

- `ElasticsearchStartupReadinessStatusResponse.scala` implements the pure projection and Circe encoder.
- `ElasticsearchStartupReadinessStatusResponseSpec.scala` pins prepared/failed projection shapes, JSON equality, and values.
- `ElasticsearchReadinessConsistencySpec.scala` proves cross-model field-level consistency across readiness state, lifecycle response, transition, status projection, and composition-derived projections. These are non-serving consistency tests, not serving-gate tests.

### `/beauty-search` serving behavior is unchanged

The ES-backed `/beauty-search` route continues to serve without any startup readiness gate:

- Production route is exposed through `LeaderboardPlugin.modules.apiBase[IO]` plus `BeautySearchRouteModules.apiElasticsearch`.
- Route behavior, response shape, and error behavior are unchanged.
- No startup readiness enforcement exists.
- No endpoint, path, HTTP status policy, or operator policy is implemented.

## Policy choices that must be approved before enforcement

Each choice below must be explicitly approved before any implementation. None are implemented today.

### Choice 1: Fail closed until prepared

**Definition:** If the startup transition is not `Prepared`, the route returns an error response (e.g., `503 Service Unavailable`) instead of serving search results.

**Current implementation status:** Not implemented as enforced policy. The current route serves regardless of startup transition state. However, app-start fail-closed behavior is implicitly implemented by eager composition: if ES preparation fails, no route is constructed. This implicit behavior is test-covered by `ElasticsearchAppStartServingGateSpec`. Runtime route gate (route returns HTTP 503 on non-prepared state) requires a new source seam.

**Required source changes before implementation:**

- Route composition must read `ElasticsearchStartupReadinessTransition` from DI and check its state before serving.
- A new failure response model for the "not ready" case must be defined.
- The route must short-circuit with the failure response when the transition is not `Prepared`.

**Required tests before implementation:**

- `Prepared` transition allows serving.
- `PreparationFailed` transition blocks serving with approved error response.
- `UnsupportedFailure` transition behavior (fail closed or ignore).
- No accidental Qdrant/hybrid fallback during startup failure.
- No extra Elasticsearch calls beyond approved lifecycle behavior.
- Serving behavior is unchanged for `Prepared` transitions.

**Operator-visible status impact:**

- Operators see `503 Service Unavailable` (or approved alternative) when startup preparation has not succeeded.
- Operators can distinguish "not yet ready" from "prepared and serving."

**Rollback/freshness implications:**

- Rollback to seed-only/in-memory backend would bypass this gate.
- Freshness behavior is orthogonal; this gate only checks prepared/not-prepared state.

### Choice 2: Fail fast on preparation failure

**Definition:** If the startup transition is `PreparationFailed`, the route returns an error response and remains in a failed state until restarted or manually recovered.

**Current implementation status:** Not implemented. Preparation failures are classifiable in pure tests but do not change route behavior.

**Required source changes before implementation:**

- Route composition must read the transition and check for `PreparationFailed`.
- A distinct failure response for preparation failure must be defined (separate from "not yet prepared").
- The route must persist the failed state and refuse serving.

**Required tests before implementation:**

- `PreparationFailed` transition blocks serving with the approved failure response.
- Failure response is operator-visible if an endpoint is approved.
- No accidental Qdrant/hybrid fallback during preparation failure.
- Recovery path (restart, manual recovery) is documented.

**Operator-visible status impact:**

- Operators see a distinct error for preparation failure versus "not yet prepared."
- The failure is persistent until manual or restart recovery.

**Rollback/freshness implications:**

- Rollback to seed-only/in-memory backend would bypass this gate.
- Preparation failure does not imply stale data; it implies the index could not be prepared.

### Choice 3: Continue serving with seed-only/not-enforced status

**Definition:** The route continues to serve search results regardless of startup transition state. The startup status is exposed for operator visibility only, not for serving enforcement.

**Current implementation status:** This is the current behavior. The route serves regardless of transition state, and the transition is bound but non-serving (`NotEnforced`).

**Required source changes before implementation:**

- Minimal: expose the startup status through an operator-visible endpoint (if approved).
- No route behavior changes.

**Required tests before implementation:**

- Route continues to serve with `Prepared` and `PreparationFailed` transitions.
- Startup status projection is operator-visible if an endpoint is approved.
- Serving behavior is unchanged.

**Operator-visible status impact:**

- Operators can see the startup status for monitoring.
- No serving behavior change.

**Rollback/freshness implications:**

- No rollback behavior change.
- Freshness behavior is orthogonal.

### Choice 4: Serve stale/previous index if replacement exists

**Definition:** If a new index is being prepared and the old index is still valid, the route continues to serve from the old index until the new index is ready.

**Current implementation status:** Not implemented. No replacement strategy exists. `ElasticsearchProductionReadinessState.replacement` is `NotConfigured`.

**Required source changes before implementation:**

- Index replacement strategy must be designed (alias, versioned index, or direct replacement).
- The route must track which index is currently serving.
- The startup gate must check whether a replacement is in progress and whether the old index is still valid.

**Required tests before implementation:**

- Old index continues to serve during replacement.
- New index takes over when preparation completes.
- Rollback to old index if new index preparation fails.
- No accidental dual-serving or index drift.

**Operator-visible status impact:**

- Operators see which index is currently serving.
- Operators see replacement progress.

**Rollback/freshness implications:**

- Rollback is part of this choice: return to previous index if replacement fails.
- Freshness tracking is required to know if the serving index is stale.

### Choice 5: Operator override/disable mode

**Definition:** An operator can manually override the startup gate to force serving or force non-serving, regardless of startup transition state.

**Current implementation status:** Not implemented. No operator override surface exists.

**Required source changes before implementation:**

- An operator-visible control surface (endpoint, config, or flag) must be defined.
- The startup gate must check the override before enforcing the gate.
- Override state must be persisted or documented as restart-time configuration.

**Required tests before implementation:**

- Override to "force serving" bypasses the startup gate.
- Override to "force non-serving" blocks serving regardless of transition.
- Override state is operator-visible.
- Override does not affect lifecycle metadata or freshness.

## Source-confirmed runtime route-gate policy

The current source truth distinguishes three separate policies:

- app-start fail-closed: if eager composition fails, no route instance exists;
- runtime route gate: a constructed route instance would need a non-prepared readiness source to return an approved HTTP error;
- operator visibility: separate status exposure, already implemented as Design A, without serving enforcement.

The current source does not support a runtime HTTP gate because a successful route graph always binds `ElasticsearchStartupReadinessTransition.Prepared`, and no stale/previous index or replacement/freshness/rollback state exists yet. The recommended policy is Candidate A from `ES_RUNTIME_ROUTE_GATE_POLICY.md`: keep app-start fail-closed only and defer runtime HTTP gate work until a later runtime readiness or replacement/freshness/rollback policy exists.

**Operator-visible status impact:**

- Operators can control serving behavior without code changes.
- Override state is visible in status responses.

**Rollback/freshness implications:**

- Override does not change freshness or replacement behavior.
- Override can be used to roll back to serving if a gate blocks incorrectly.

## Recommended future policy

**Recommended default:** Fail closed until prepared.

Rationale:

- The current ES-backed seed route performs eager seed index preparation during composition.
- If composition succeeds, the route serves with a prepared transition.
- If composition fails, the route should not serve with an unknown/unprepared index state.
- Fail closed provides the safest default: serve only when explicitly prepared.

**Status:** Not implemented. This recommendation is based on the current source-backed state of seed-only lifecycle, non-serving readiness, and unchanged route behavior. It must be explicitly approved before enforcement.

If enough source truth does not yet exist to recommend one policy, the policy remains unresolved. In the current state, the recommendation above is source-backed but not enforced.

## Required future tests before any serving-gate implementation

These tests are unimplemented and must be added alongside any enforcement code:

1. **Prepared startup allows serving.**
   - A `Prepared` transition allows the route to serve `/beauty-search` normally.
   - The prepared transition preserves the readiness state and derives the lifecycle status response.
   - Serving behavior is unchanged for prepared transitions.

2. **Failed startup blocks or fails according to approved policy.**
   - A `PreparationFailed` transition blocks or fails serving according to the approved policy.
   - The failure response is distinct from the current `500 InternalServerError` empty-body behavior.
   - The failure response is operator-visible if an endpoint is approved.

3. **Failed startup status projection is operator-visible if endpoint is approved.**
   - If an endpoint is approved, `ElasticsearchStartupReadinessStatusResponse` for `PreparationFailed` transitions is accessible.
   - Prepared projections include nested `ElasticsearchLifecycleStatusResponse`.
   - Failed projections expose operation/message only.

4. **No accidental Qdrant/hybrid fallback.**
   - During startup failure, no Qdrant or hybrid serving occurs.
   - The route does not fall back to Qdrant or hybrid when ES preparation fails.

5. **No extra Elasticsearch calls beyond approved lifecycle behavior.**
   - Startup gate enforcement does not introduce new ES calls.
   - Existing ES calls (PUT mapping, bulk ingest, refresh, search) remain unchanged.

6. **Rollback/freshness behavior only after those policies exist.**
   - Rollback behavior is tested only after an explicit rollback policy is approved.
   - Freshness behavior is tested only after an explicit freshness policy is approved.

## Source-confirmed implementation slice analysis

Source confirmation is documented separately in `docs/codebase-review/ES_STARTUP_SERVING_GATE_SOURCE_CONFIRMATION.md`. Key findings below.

The operator visibility track is separately source-confirmed in `docs/codebase-review/ES_OPERATOR_VISIBILITY_SOURCE_CONFIRMATION.md`. Design A (expose current prepared/seed-only status from constructed route graph) is the recommended next step for the operator visibility track and requires endpoint path, auth, and HTTP status policy approval before implementation.

### Production route construction path

```text
LeaderboardPlugin
  include(modules.apiBase[IO])
  include(BeautySearchRouteModules.apiElasticsearch)
    include(seedCatalogElasticsearchPortConfigured)
      include(ElasticsearchClientModules.portConfigured)
      include(seedCatalogElasticsearch)
        include(BeautySearchCatalogBackendModules.seedResourceElasticsearch)
          make[ElasticsearchSeedSearchComposition].from { ... eager build ... }
        include(BeautySearchPluginModules.api[IO])
          make[BeautySearchApi[F]]
          many[HttpApi[F]].weak[BeautySearchApi[F]]
```

### Source-confirmed enforcement facts

- `BeautySearchCatalogBackendModules.seedResourceElasticsearch` eagerly runs `ElasticsearchSeedSearchComposition.build` via `Runtime.default.unsafe.run(...).getOrThrowFiberFailure()` (`BeautySearchCatalogBackendModules.scala:60-62`).
- If `build` fails, the `unsafe.run` throws and Distage graph construction fails. No route instance is created.
- If `build` succeeds, the composition is constructed and `startupReadinessTransition` returns `Prepared(state)` (`ElasticsearchSeedSearchComposition.scala:20-21`).
- `PreparationFailed` transitions are classifiable in pure tests only (`ElasticsearchStartupReadinessTransition.preparationFailed`), not from the composition path.
- `BeautySearchApi` has no access to `ElasticsearchStartupReadinessTransition` and performs no readiness check (`BeautySearchApi.scala:21-29`).
- The DI-bound `ElasticsearchStartupReadinessTransition` is always `Prepared`; `PreparationFailed` is unreachable from the bound value.

### Smallest candidate enforcement seam

The smallest candidate seam is `BeautySearchApi` (`BeautySearchApi.scala:21-29`), where `searchBeauty.serverLogic` could check transition state before delegating to the service. However, this enforcement is currently impossible at runtime because the DI-bound transition is always `Prepared`.

### App-start fail-closed is implicitly implemented

The current architecture already exhibits app-start fail-closed behavior: if ES preparation fails during eager composition, the `unsafe.run` throws, Distage graph construction fails, and no route is constructed. The app cannot serve. This is an implementation fact of the eager composition pattern, not an approved production lifecycle policy.

This behavior is now test-covered by `ElasticsearchAppStartServingGateSpec`:

- Composition-level: blank source, empty documents, and ES client failures all prevent `ElasticsearchSeedSearchComposition.build` from producing a usable composition.
- DI-graph-level: ES client failure during eager composition prevents Distage graph construction; `BeautySearchApi` cannot be obtained; no route instance is constructed.
- Prepared-serving: successful composition produces `Prepared` transition and allows `POST /beauty-search` to serve `200 OK`.

These tests document current implicit behavior. They are not runtime HTTP 503 gate tests. Runtime route gate requires a different source seam where the route instance can be constructed even when preparation has not succeeded.

### Runtime route gate requires a different seam

A runtime route gate (route returns HTTP 503 on non-prepared state) requires a source seam where:

1. The route instance can be constructed even when preparation has not succeeded.
2. The route handler can check transition state at request time.
3. A non-prepared transition is reachable from the DI-bound value.

The current eager composition pattern does not support this. To create a "route exists but transition is not prepared" state, composition would need to change from eager (`unsafe.run` in DI) to effectful (deferred into the route handler or wrapped in a resource).

### Candidate implementation slices

| Slice | Description | Requires new source seam | Requires endpoint/path policy | Behavior change risk |
|-------|-------------|-------------------------|------------------------------|---------------------|
| A | App-start fail-closed tests/docs only | no | no | none |
| B | Route-level gate (HTTP 503 on non-prepared) | yes | yes (HTTP status) | medium |
| C | Operator status visibility endpoint | yes | yes (endpoint, path, auth) | low (additive) |

### Recommended next step

Add spec-only route-level tests proving app-start fail-closed behavior (composition failure prevents route construction) and prepared-serving behavior (composition success allows serving). These tests can be written before any enforcement code. — **Done.** `ElasticsearchAppStartServingGateSpec` covers composition-level fail-closed (blank source, empty documents, ES client failure), DI-graph-level fail-closed (ES client failure prevents `BeautySearchApi` construction), and prepared-serving (successful composition allows `POST /beauty-search` serving with `Prepared` transition). These are not runtime HTTP 503 gate tests; they document current implicit behavior only.

See `docs/codebase-review/ES_STARTUP_SERVING_GATE_SOURCE_CONFIRMATION.md` for full analysis.

## Implementation boundary

This document does not implement:

- an endpoint;
- a route path;
- HTTP status policy;
- serving-readiness enforcement;
- preparation failure policy;
- replacement implementation;
- alias/versioned-index behavior;
- freshness timestamp implementation;
- refresh trigger implementation;
- rollback implementation;
- production lifecycle completion.

All of these remain future work pending explicit approval.
