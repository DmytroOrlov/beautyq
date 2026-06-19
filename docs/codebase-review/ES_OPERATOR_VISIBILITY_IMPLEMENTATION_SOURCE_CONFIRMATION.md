# ES Operator Visibility — Design A Implementation Slice Source Confirmation

Status: **Design A implemented as explicit opt-in module.** Endpoint path `GET /ops/beauty-search/lifecycle` is implemented. Endpoint is NOT in the default ES route graph; available only through explicit opt-in modules (`BeautySearchRouteModules.seedCatalogElasticsearchWithOperatorVisibility`, `BeautySearchRouteModules.apiElasticsearchWithOperatorVisibility`). Default `seedCatalogElasticsearch` and `apiElasticsearch` do NOT include the operator visibility endpoint. Response shape is `ElasticsearchStartupReadinessStatusResponse` (always `Prepared` variant) with nested `ElasticsearchLifecycleStatusResponse`. Successful status retrieval returns `200 OK`. No new Elasticsearch calls. No `/beauty-search` behavior change. No runtime route gate or HTTP 503 behavior. Design B/C remain future. Full ES production lifecycle remains incomplete.

## Purpose

Source-confirm the smallest safe implementation slice for Design A operator visibility. Identify exact production files, route/API/Tapir/DI seams, and unresolved decisions before implementation can begin.

This document is a source-confirmation note only. It does not implement an endpoint, approve a route path, change any source or test file, or claim production lifecycle completion.

## Source-confirmed implementation slice

### Smallest safe implementation files

The following files are the smallest safe implementation set for Design A:

**New files (to be created):**

1. `leaderboard/http/tapir/EsLifecycleStatusTapirEndpoints.scala`
   - Pure endpoint contract following `BeautySearchTapirEndpoints` pattern.
   - Defines `GET` endpoint with approved path (draft: `/ops/beauty-search/lifecycle`).
   - Output: `ElasticsearchStartupReadinessStatusResponse` JSON.
   - Uses `HttpApiFailureTapirSupport.endpointBase` for consistent error outputs.

2. `leaderboard/api/EsLifecycleStatusApi.scala`
   - Thin Tapir adapter following `BeautySearchApi` pattern.
   - Takes DI-bound `ElasticsearchStartupReadinessTransition`.
   - Projects to `ElasticsearchStartupReadinessStatusResponse` via `ElasticsearchStartupReadinessStatusResponse.from(transition)`.
   - Returns via `Http4sServerInterpreter`.
   - No new ES calls; reads from DI-bound model only.

**Existing files to be modified:**

3. `leaderboard/plugins/BeautySearchCatalogBackendModules.scala` or `leaderboard/plugins/BeautySearchRouteModules.scala`
   - Add DI wiring for new `EsLifecycleStatusApi[F]`.
   - Contribute to `many[HttpApi[F]].weak[EsLifecycleStatusApi[F]]`.
   - Pattern: follow `BeautySearchPluginModules.api[F]` — create API adapter from DI-bound transition and contribute to weak set.

**Test files (to be created or updated):**

4. New `EsLifecycleStatusApiHttpContractSuite.scala` or similar
   - Endpoint contract tests: `GET` returns `200 OK` with expected JSON shape.
   - Rooted only in ES route graph, not `seedCatalogInMemory`.
   - Does not alter `POST /beauty-search`.
   - No new ES calls.

5. `ElasticsearchOperatorVisibilityEndpointPolicySpec.scala`
   - Pending specs become active when endpoint is implemented.
   - 28 pending tests encode Design A draft expectations.

### DI binding source

`ElasticsearchStartupReadinessTransition` is already DI-bound via `BeautySearchCatalogBackendModules.seedResourceElasticsearch` from `ElasticsearchSeedSearchComposition.startupReadinessTransition` (`BeautySearchCatalogBackendModules.scala:77-79`).

No new DI binding is needed for the status source. The new API class takes the existing bound transition as a constructor parameter.

### Projection source

`ElasticsearchStartupReadinessStatusResponse.from(transition)` is a pure function (`ElasticsearchStartupReadinessStatusResponse.scala:31-42`). It projects the DI-bound transition into the response model. No new ES calls.

### Route wiring pattern

The existing pattern for contributing an API to the route graph:

```scala
// BeautySearchPluginModules.scala pattern:
make[BeautySearchApi[F]].from { ... }
many[HttpApi[F]].weak[BeautySearchApi[F]]
```

The new operator visibility API follows the same pattern:

```scala
// Future pattern (NOT IMPLEMENTED):
make[EsLifecycleStatusApi[F]].from {
  (transition: ElasticsearchStartupReadinessTransition, async: Async[F[Throwable, _]]) =>
    new EsLifecycleStatusApi[F](transition)(implicitly[Error2[F]], async)
}
many[HttpApi[F]].weak[EsLifecycleStatusApi[F]]
```

The new module should be included only in ES route graphs (e.g., `BeautySearchRouteModules.seedCatalogElasticsearch` or a new dedicated module), not in `seedCatalogInMemory`.

## Can Design A be implemented without new ES calls?

**Yes.** The endpoint reads from DI-bound `ElasticsearchStartupReadinessTransition` and projects to `ElasticsearchStartupReadinessStatusResponse` via a pure function. No Elasticsearch calls are triggered by the endpoint.

Source evidence:
- `ElasticsearchStartupReadinessTransition` is bound at `BeautySearchCatalogBackendModules.scala:77-79` from composition.
- `ElasticsearchStartupReadinessStatusResponse.from` is a pure projection at `ElasticsearchStartupReadinessStatusResponse.scala:31-42`.
- `ElasticsearchLifecycleStatusResponse.from` is a pure projection at `ElasticsearchLifecycleStatusResponse.scala:24-38`.
- Both use existing Circe encoders; no new encoding logic needed.

## Can Design A be implemented without changing `/beauty-search`?

**Yes.** The operator endpoint is additive. It uses a different HTTP method (`GET` vs `POST`) and a different path. The existing `POST /beauty-search` behavior, including `BeautySearchApi.serverLogic`, is unchanged.

Source evidence:
- `BeautySearchApi.scala:21-29` — `searchBeauty.serverLogic` delegates to `beautySearchService.search`; no readiness check.
- `BeautySearchTapirEndpoints.scala:18-21` — `searchBeauty` is `POST /beauty-search`.
- The new endpoint uses `GET` on a different path; no conflict.

## Unresolved decisions before implementation

The following decisions must be explicitly approved before any operator visibility endpoint implementation:

1. **Endpoint path** — Draft recommends `GET /ops/beauty-search/lifecycle`. Not approved.
2. **Auth/exposure mode** — Draft recommends disabled unless explicitly enabled, with local/dev-only fallback. Not approved.
3. **Response shape** — Draft recommends `ElasticsearchStartupReadinessStatusResponse` (always `Prepared` variant) with nested `ElasticsearchLifecycleStatusResponse`. Not approved.
4. **HTTP status code** — Draft recommends always `200 OK` for successful retrieval. Not approved.
5. **Exact route graph rooting** — Whether the endpoint module is included in `seedCatalogElasticsearch`, `seedCatalogElasticsearchPortConfigured`, or a new dedicated module.
6. **Whether pending specs become active in same patch or later** — `ElasticsearchOperatorVisibilityEndpointPolicySpec.scala` has 28 pending tests that encode Design A expectations. They can be activated when the endpoint is implemented, or kept pending as regression guardrails.
7. **Enabled/disabled flag semantics** — How the disabled-by-default flag is implemented (config flag, environment, activation axis).
8. **Exact response wrapper versus direct status projection** — Whether to return `ElasticsearchStartupReadinessStatusResponse` directly or wrap it in additional metadata.

## Future source files/seams confirmed

| File | Role | Status |
|------|------|--------|
| `leaderboard/http/tapir/EsLifecycleStatusTapirEndpoints.scala` | Pure endpoint contract | Implemented |
| `leaderboard/api/EsLifecycleStatusApi.scala` | Thin Tapir adapter | Implemented |
| `leaderboard/plugins/BeautySearchPluginModules.scala` | API module wiring (modification) | Modified: added `operatorVisibilityApi` |
| `leaderboard/plugins/BeautySearchRouteModules.scala` | Route module inclusion (modification) | Modified: includes `operatorVisibilityApi` in ES route |
| `leaderboard/plugins/BeautySearchPluginModules.scala` | API module pattern (reference) | Existing; pattern reference |
| `leaderboard/http/tapir/BeautySearchTapirEndpoints.scala` | Endpoint pattern (reference) | Existing; pattern reference |
| `leaderboard/api/BeautySearchApi.scala` | API adapter pattern (reference) | Existing; pattern reference |
| `leaderboard/search/elasticsearch/ElasticsearchStartupReadinessTransition.scala` | DI-bound status source | Existing; no changes needed |
| `leaderboard/search/elasticsearch/ElasticsearchStartupReadinessStatusResponse.scala` | Response projection | Modified: added Decoder for Tapir jsonBody |
| `leaderboard/search/elasticsearch/ElasticsearchLifecycleStatusResponse.scala` | Nested lifecycle status | Modified: added Decoder for Tapir jsonBody |

## Pending specs as current expectations

`ElasticsearchOperatorVisibilityEndpointPolicySpec.scala` encodes 28 pending tests for Design A. These are pending expectations, not implementation proof. When implementation starts, these specs should be activated or replaced by active endpoint contract tests.

Pending test groups:
- **Response shape** (6 tests): `200 OK` with `Prepared` variant, nested lifecycle status, `transitionStatus`, `servingDecision`, `productionLifecycleComplete`.
- **Seed-only status values** (9 tests): all current seed-only field values and full JSON encoding.
- **Route graph / rooting** (3 tests): rooted in ES route graph only, not `seedCatalogInMemory`, additive to `POST /beauty-search`.
- **No-new-ES-calls behavior** (3 tests): reads from DI-bound only, no ES calls at request time.
- **Exposure / auth policy** (3 tests): disabled unless enabled, local/dev fallback, not public product API.
- **Limitations** (4 tests): no `PreparationFailed` variant, no startup failure, no replacement/freshness/rollback claims, no runtime route-gate claims.

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

- Operator visibility source confirmation: `docs/codebase-review/ES_OPERATOR_VISIBILITY_SOURCE_CONFIRMATION.md`
- Operator visibility endpoint policy: `docs/codebase-review/ES_OPERATOR_VISIBILITY_ENDPOINT_POLICY.md`
- Lifecycle status design: `docs/codebase-review/ES_LIFECYCLE_STATUS_DESIGN.md`
- Serving-gate design: `docs/codebase-review/ES_STARTUP_SERVING_GATE_DESIGN.md`
- M5 checkpoint: `docs/codebase-review/M5_ES_LIFECYCLE_CHECKPOINT.md`
- Current gaps and roadmap: `docs/codebase-review/07-current-gaps-and-roadmap.md`
- Tests and contracts: `docs/codebase-review/06-tests-and-contracts.md`
- Architecture decisions: `docs/codebase-review/ARCHITECTURE_DECISIONS_OBSERVED.md`
