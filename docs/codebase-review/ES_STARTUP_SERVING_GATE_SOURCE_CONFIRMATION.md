# ES Startup Serving Gate Source Confirmation

Status: source-confirmed; app-start fail-closed and prepared-serving are now test-covered. M5 is closed as a bounded startup-readiness lifecycle checkpoint. This source confirmation completed the bounded M5 decision. Runtime route-gate work is a separate future track, not an unaccepted M5 remainder, and is recommended to stay deferred until a runtime readiness source or replacement/freshness/rollback policy exists. No production source, endpoint, or serving behavior was changed.

## Purpose

Source-confirm the first possible implementation slice for startup serving-gate enforcement. Identify the exact source seam, required tests, and whether policy is resolved enough for spec-only route tests.

## Source-confirmed production route construction path

The current `/beauty-search` route is constructed through:

```text
LeaderboardPlugin
  include(modules.apiBase[IO])         // base APIs, HttpServer, Ranks
  include(BeautySearchRouteModules.apiElasticsearch)
    include(seedCatalogElasticsearchPortConfigured)
      include(ElasticsearchClientModules.portConfigured)
      include(seedCatalogElasticsearch)
        include(BeautySearchCatalogBackendModules.seedResourceElasticsearch)
          make[ElasticsearchSeedSearchComposition].from { ... build ... }  // EAGER, runs in DI
        include(BeautySearchPluginModules.api[IO])
          make[BeautySearchApi[F]]               // consumes BeautySearchService[F]
          many[HttpApi[F]].weak[BeautySearchApi[F]]
        make[BeautyQSeedLoader].from[BeautyQSeedLoader.ResourceLoader]
```

Key source facts:

- `BeautySearchCatalogBackendModules.seedResourceElasticsearch` (`BeautySearchCatalogBackendModules.scala:54-63`) eagerly runs `ElasticsearchSeedSearchComposition.build(spec, client, ready)` inside `Runtime.default.unsafe.run(...).getOrThrowFiberFailure()`.
- `ElasticsearchSeedSearchComposition.build` (`ElasticsearchSeedSearchComposition.scala:25-39`) runs `initializer.prepare(ready)` which performs PUT mapping, bulk ingest, and refresh against Elasticsearch.
- If `prepare` fails, the `IO[QueryFailure, ...]` effect fails, `unsafe.run` throws, and Distage graph construction fails.
- If `prepare` succeeds, the composition is constructed with `readiness`, `backend`, and `service`.
- `startupReadinessTransition` (`ElasticsearchSeedSearchComposition.scala:20-21`) always returns `Prepared(state)` because it is only reachable when composition succeeded.
- `BeautySearchCatalogBackendModules.seedResourceElasticsearch` binds `ElasticsearchStartupReadinessTransition` from composition (`line 77-79`), which is always `Prepared`.
- `BeautySearchPluginModules.api[IO]` (`BeautySearchPluginModules.scala:11-23`) creates `BeautySearchApi[F]` from `BeautySearchService[F]` and contributes it to `many[HttpApi[F]]`.
- `BeautySearchApi` (`BeautySearchApi.scala:11-52`) delegates directly to `beautySearchService.search(validInput)` without any readiness check.

## Source-confirmed enforcement seam analysis

### Seams inspected

1. **`ElasticsearchSeedSearchComposition.build`** — eager effect that either succeeds (producing `Prepared` transition) or fails (preventing composition entirely). No intermediate "prepared but not serving" state exists.
2. **`BeautySearchCatalogBackendModules.seedResourceElasticsearch`** — DI module that eagerly runs composition. Failure prevents DI graph construction. Success always produces `Prepared` transition.
3. **`BeautySearchApi`** — thin Tapir adapter. Delegates to `BeautySearchService.search`. No readiness check. No access to `ElasticsearchStartupReadinessTransition`.
4. **`BeautySearchTapirEndpoints`** — pure endpoint contract. No readiness logic. `searchBeauty` endpoint shape is fixed.
5. **`BeautySearchPluginModules.api`** — creates `BeautySearchApi` from service. No readiness check.
6. **`ElasticsearchSearchBackend`** — delegates to ES client. No readiness check.

### Smallest candidate enforcement seam

The smallest candidate seam for enforcement is **`BeautySearchApi`** (`BeautySearchApi.scala:21-29`).

Current code:

```scala
searchBeauty.serverLogic[F[Throwable, _]] {
  input =>
    validate(input) match {
      case Left(failure) =>
        async.pure(Left(failure))
      case Right(validInput) =>
        HttpApiFailure.fromQueryEffect(beautySearchService.search(validInput))
    }
}
```

A route-level gate could inject `ElasticsearchStartupReadinessTransition` into `BeautySearchApi` and check it before delegating to the service:

```scala
// Hypothetical enforcement shape (NOT IMPLEMENTED):
searchBeauty.serverLogic[F[Throwable, _]] {
  input =>
    validate(input) match {
      case Left(failure) => async.pure(Left(failure))
      case Right(validInput) =>
        transition match {
          case _: ElasticsearchStartupReadinessTransition.Prepared =>
            HttpApiFailure.fromQueryEffect(beautySearchService.search(validInput))
          case _: ElasticsearchStartupReadinessTransition.PreparationFailed =>
            async.pure(Left(HttpApiFailure.ServiceUnavailable("startup preparation failed")))
        }
    }
}
```

However, this enforcement is **currently impossible at runtime** because:

- `BeautySearchCatalogBackendModules.seedResourceElasticsearch` always produces `Prepared` transition (composition succeeded).
- `PreparationFailed` transitions are only classifiable in pure tests, not from the composition path.
- If composition fails, no `BeautySearchApi` instance is constructed.

### Can enforcement proceed without new seams?

**For the "fail closed until prepared" policy:** The current architecture already implicitly implements app-start fail-closed behavior. If ES preparation fails during eager composition, `unsafe.run` throws, Distage graph construction fails, and no route is constructed. The app cannot serve.

**For a runtime route gate returning HTTP 503:** A different seam is needed. The current eager composition means the route either exists (prepared) or does not exist (composition failed). There is no "route exists but transition is not prepared" state. To create this state, composition would need to be changed from eager (`unsafe.run`) to effectful (deferred into the route handler or wrapped in a resource).

**Conclusion:** The current transition binding is enough for **app-start fail-closed** enforcement (the existing behavior). It is **not enough** for a **runtime route gate** that returns HTTP 503, because the `PreparationFailed` variant is unreachable from the DI-bound transition.

## Policy gap analysis

### Three distinct policy choices

| Policy | Meaning | Current status |
|--------|---------|----------------|
| App-start fail-closed | If composition fails, no route exists; app fails to start | Implicitly implemented by eager composition; test-covered by `ElasticsearchAppStartServingGateSpec` (composition-level and DI-graph-level); not formally approved as policy |
| Runtime route gate | Route exists but returns HTTP error (e.g., 503) on non-prepared state | Not implemented; requires new source seam |
| Operator status visibility | Endpoint exposes startup/health status to operators | Not implemented; requires endpoint/path/auth policy |

### Critical distinction

**App-start failure ≠ runtime route gate.** These are different source seams with different test requirements:

- App-start failure: proven by showing that composition failure prevents route construction. No HTTP status code involved because no HTTP request is served.
- Runtime route gate: proven by showing that a route instance returns an approved HTTP error when transition is not prepared. Requires a route instance to exist with a non-prepared transition.

The current architecture supports app-start failure but not runtime route gate.

### Recommended policy decision

**Candidate A: keep app-start fail-closed only.**

That decision is source-backed because:

- a successful ES route graph always binds `ElasticsearchStartupReadinessTransition.Prepared`;
- `PreparationFailed` is unreachable from the DI-bound transition in a successfully constructed route graph;
- if preparation fails during eager composition, no route instance exists to gate at runtime;
- there is no stale/previous index state and no replacement/freshness/rollback policy to anchor a runtime gate.

The runtime serving-gate track should remain deferred until a later runtime readiness source or replacement/freshness/rollback policy is approved.

## Candidate implementation slices

### Slice A: app-start fail-closed documentation/tests only

**Description:** Document and test the existing implicit behavior: composition failure prevents route construction.

**Source files likely touched:** none (docs-only).

**Test files likely touched:** new spec proving that DI graph construction failure from ES preparation failure prevents `BeautySearchApi` from being available.

**Behavior change risk:** none. Documents existing behavior.

**Endpoint/path/status policy required:** no.

**Can be done without replacement/freshness/rollback:** yes.

### Slice B: route-level gate

**Description:** Add a runtime route gate where the route carries a transition state and returns an approved HTTP error on non-prepared state.

**Source files likely touched:**
- `BeautySearchCatalogBackendModules.scala` — change composition from eager to effectful or wrap transition differently.
- `BeautySearchApi.scala` — inject transition and check before serving.
- Possibly `ElasticsearchSeedSearchComposition.scala` — expose failed transition through composition.
- Possibly `ElasticsearchStartupServingDecision` — add `EnforceClosed` variant.

**Test files likely touched:**
- New route-level spec proving `Prepared` allows serving.
- New route-level spec proving `PreparationFailed` returns approved error.
- New spec proving no Qdrant/hybrid fallback during failure.
- New spec proving no extra ES calls.

**Behavior change risk:** medium. Changes route construction and error behavior.

**Endpoint/path/status policy required:** HTTP status code for non-prepared state (e.g., 503) must be approved.

**Can be done without replacement/freshness/rollback:** yes.

### Slice C: operator status visibility endpoint

**Description:** Expose startup/health status through an operator-visible endpoint.

**Source files likely touched:** new endpoint definition, new API adapter, new route wiring.

**Test files likely touched:** new endpoint contract tests.

**Behavior change risk:** low (additive), but requires policy decisions.

**Endpoint/path/status policy required:** yes. Endpoint path, HTTP status, auth/operator policy all required.

**Can be done without replacement/freshness/rollback:** yes.

## Recommended next implementation-prep step

**Recommend route-level pending/spec-only tests first.**

Rationale:

- The recommended policy is "fail closed until prepared."
- App-start fail-closed is already implicitly implemented by eager composition.
- A runtime route gate is not possible with the current source seam (composition always produces `Prepared`).
- Before implementing Slice B, route-level policy tests must prove the expected behavior.
- Before implementing Slice A, app-start failure tests must prove the implicit behavior.
- Slice C is blocked on endpoint/path/auth policy.

**Recommended exact next step:** keep the current app-start fail-closed tests as the source of truth and defer runtime gate implementation. If the track resumes, add spec-only route-level tests that prove:

1. **App-start fail-closed:** Composition failure from ES preparation failure prevents `BeautySearchApi` from being constructed. This proves the existing implicit behavior. — **Done.** `ElasticsearchAppStartServingGateSpec` covers composition-level and DI-graph-level fail-closed behavior.
2. **Prepared startup allows serving:** When composition succeeds, `POST /beauty-search` returns 200 OK. This is already proven by `BeautySearchProductionRouteExposureSpec` but should be restated in serving-gate context. — **Done.** `ElasticsearchAppStartServingGateSpec` includes a prepared-serving test.
3. **No Qdrant/hybrid fallback:** During startup failure, no Qdrant or hybrid serving occurs. — **Not yet covered by dedicated test.** Fail-closed proves no route is constructed; by implication no fallback occurs.

These tests can be written as spec-only (expected behavior) before any enforcement code.

## Policy resolution status

- **Fail closed until prepared:** recommended but not formally approved. Source-backed implicit behavior exists (app-start fail-closed). Runtime route gate requires new seam.
- **Candidate A / app-start fail-closed only:** recommended and source-backed today. Runtime route gate remains deferred until a later runtime readiness source or replacement/freshness/rollback policy exists.
- **Fail fast on preparation failure:** not approved. Current eager composition already fails fast (throws), but this is an implementation fact, not an approved policy.
- **Endpoint/path/auth policy:** not resolved. Blocks Slice C.
- **HTTP status for non-prepared state:** not resolved. Blocks Slice B.
- **Replacement/freshness/rollback:** not resolved. Orthogonal to serving gate.

## Source-confirmed facts

- `ElasticsearchSeedSearchComposition.build` runs eagerly during DI graph construction (`BeautySearchCatalogBackendModules.scala:60-62`).
- Eager composition failure prevents DI graph construction entirely; no route instance is created.
- Successful composition always produces `ElasticsearchStartupReadinessTransition.Prepared` (`ElasticsearchSeedSearchComposition.scala:20-21`).
- `PreparationFailed` transitions are classifiable in pure tests only (`ElasticsearchStartupReadinessTransition.preparationFailed`), not from the composition path.
- `BeautySearchApi` has no access to `ElasticsearchStartupReadinessTransition` and performs no readiness check (`BeautySearchApi.scala:21-29`).
- `BeautySearchPluginModules.api` does not inject `ElasticsearchStartupReadinessTransition` (`BeautySearchPluginModules.scala:14-21`).
- The current `ElasticsearchStartupReadinessTransition` DI binding is always `Prepared`; `PreparationFailed` is unreachable from the bound value.
- No endpoint, route path, HTTP status policy, auth/operator policy, or serving-gate enforcement exists.
- `ElasticsearchAppStartServingGateSpec` proves app-start fail-closed behavior at both composition level (blank source, empty documents, ES client failure all prevent composition) and DI-graph level (ES client failure prevents `BeautySearchApi` construction).
- `ElasticsearchAppStartServingGateSpec` proves prepared-serving behavior: successful composition produces `Prepared` transition, `BeautySearchApi` is constructible, and `POST /beauty-search` returns `200 OK`.
- These tests are not runtime HTTP 503 gate tests. They document current implicit behavior only.

## Non-goals

This source confirmation does not:

- implement enforcement;
- approve an endpoint or route path;
- approve an HTTP status policy;
- change any source or test file;
- claim production lifecycle completion.

This source confirmation completed the bounded M5 closeout decision. M5 is closed as a bounded startup-readiness lifecycle checkpoint. Runtime route-gate work moves to the ES runtime serving-gate future track.

## References

- Serving-gate design: `docs/codebase-review/ES_STARTUP_SERVING_GATE_DESIGN.md`
- Operator visibility source confirmation: `docs/codebase-review/ES_OPERATOR_VISIBILITY_SOURCE_CONFIRMATION.md`
- M5 checkpoint: `docs/codebase-review/M5_ES_LIFECYCLE_CHECKPOINT.md`
- Lifecycle status design: `docs/codebase-review/ES_LIFECYCLE_STATUS_DESIGN.md`
- Tests and contracts: `docs/codebase-review/06-tests-and-contracts.md`
- Current gaps: `docs/codebase-review/07-current-gaps-and-roadmap.md`
