# ES Operator Visibility Endpoint Policy — Design A

Status: **Design A endpoint implemented as explicit opt-in/internal operator visibility endpoint.** Endpoint path `GET /ops/beauty-search/lifecycle` is implemented. Endpoint is NOT included in the default ES route graph (`seedCatalogElasticsearch`, `apiElasticsearch`). Endpoint is available only through the explicit opt-in modules `BeautySearchRouteModules.seedCatalogElasticsearchWithOperatorVisibility` and `BeautySearchRouteModules.apiElasticsearchWithOperatorVisibility`. Default `LeaderboardPlugin.modules.apiBase[IO] + BeautySearchRouteModules.apiElasticsearch` does NOT expose the endpoint. Response shape is `ElasticsearchStartupReadinessStatusResponse` (always `Prepared` variant) with nested `ElasticsearchLifecycleStatusResponse`. Successful status retrieval returns `200 OK`. No new Elasticsearch calls. No `/beauty-search` behavior change. No runtime route gate or HTTP 503 behavior. Module-level opt-in/internal routing is the implemented exposure policy. Config-level disabled-by-default, local/dev fallback, Design B, Design C, dashboard integration, and full ES production lifecycle remain future. The runtime serving-gate track is separate and currently recommended to stay deferred behind a runtime readiness source or replacement/freshness/rollback policy.

## Purpose

Draft the endpoint path, auth/exposure, HTTP status code, and response shape policy for Design A of the ES operator visibility track. Design A exposes current successful prepared/seed-only status from a constructed route graph using existing DI-bound models, with no new Elasticsearch calls.

This document records the implemented Design A policy boundary plus the remaining future gaps. It does not implement an endpoint, approve a route path, change any source or test file, or claim production lifecycle completion.

## Scope

### In scope (Design A)

- Expose current `ElasticsearchStartupReadinessStatusResponse.Prepared` (with nested `ElasticsearchLifecycleStatusResponse`) through an operator-visible endpoint.
- Use existing DI-bound `ElasticsearchStartupReadinessTransition` (always `Prepared` from successful route graph).
- Require no new Elasticsearch calls.
- Return seed-only status values: `NotEnforced`, `NotConfigured`, `NotTracked`, `EagerSeedPreparationOnly`, `NotExposed`, `productionLifecycleComplete = false`.

### Out of scope (non-goals)

- **Design B**: startup failure via bootstrap-level state outside route graph.
- **Design C**: richer lifecycle status after replacement/freshness/rollback policies exist.
- Runtime route gate (HTTP 503 serving-gate behavior).
- Operator override/dashboard integration.
- Production lifecycle completion claim.
- Replacement/versioned-index state beyond `NotConfigured`.
- Freshness timestamp/age beyond `NotTracked`.
- Refresh trigger state beyond `EagerSeedPreparationOnly`.
- Rollback state beyond `NotConfigured`.
- Dashboard/operator override state beyond `NotExposed`.

## Endpoint path candidates

### Candidate 1: `GET /beauty-search/lifecycle/status`

**Pros:**
- Co-located with the existing `/beauty-search` route, making discoverability natural.
- Clearly scoped to Beauty search lifecycle.

**Cons:**
- Nested under a product-facing API path, which may imply public product API.
- Could be confused with a product feature endpoint rather than an operator/internal surface.
- Future versioning is awkward if lifecycle semantics change.

**Compatibility:** Compatible with existing Tapir/http4s routing. No conflict with `POST /beauty-search` (different method and path).

**Auth/operator exposure implications:** Path structure suggests public API surface; requires explicit auth policy to restrict access.

### Candidate 2: `GET /ops/beauty-search/lifecycle`

**Pros:**
- Clearly operator/internal namespace (`/ops/` prefix).
- Does not imply public product API.
- Groups all operator surfaces under a common prefix.
- Allows future operator endpoints (e.g., `/ops/beauty-search/config`, `/ops/beauty-search/diagnostics`) under the same namespace.

**Cons:**
- New top-level path namespace; no existing `/ops/` routes in the codebase.
- Requires operator routing convention to be established.

**Compatibility:** Compatible with existing Tapir/http4s routing. No conflict with existing endpoints.

**Auth/operator exposure implications:** Path structure signals internal/operator intent. Auth policy can be applied uniformly to `/ops/*` prefix.

### Candidate 3: `GET /internal/beauty-search/elasticsearch/status`

**Pros:**
- Clearly internal namespace (`/internal/` prefix).
- Explicit about the technology layer (Elasticsearch).
- Does not imply public product API.

**Cons:**
- Long path. Technology-specific path may become stale if backend changes.
- No existing `/internal/` routes in the codebase.
- Overly specific to Elasticsearch; future Qdrant/hybrid status would need a separate path.

**Compatibility:** Compatible with existing Tapir/http4s routing. No conflict with existing endpoints.

**Auth/operator exposure implications:** Path structure signals internal intent. Auth policy can be applied to `/internal/*` prefix.

### Candidate 4: `GET /ops/es-lifecycle`

**Pros:**
- Short, operator-scoped path.
- Does not tie to product route path.
- Allows future `/ops/*` operator endpoints.

**Cons:**
- Abbreviation `es` may be unclear without context.
- New namespace.

**Compatibility:** Compatible with existing Tapir/http4s routing.

**Auth/operator exposure implications:** Path structure signals operator intent.

## Path recommendation and current implementation

**Recommend Candidate 2: `GET /ops/beauty-search/lifecycle`**

Rationale:
- The `/ops/` prefix clearly signals operator/internal intent and does not imply public product API.
- Co-locating under `/ops/beauty-search/` groups Beauty-specific operator surfaces.
- Allows future operator endpoints under the same namespace.
- Does not tie to a specific technology (ES vs Qdrant) in the path.

Historical recommendation. The current Design A implementation uses this path for the explicit opt-in/internal endpoint only.

## Auth/exposure policy options

### Option A: Internal-only, no public exposure

The endpoint is served only on internal/private network interfaces. No authentication required because the network boundary provides access control.

**Pros:** Simple; no auth implementation needed.
**Cons:** Requires network-level configuration; not verifiable from application code alone.

### Option B: Operator-authenticated

The endpoint requires explicit operator authentication (e.g., API key, token, or operator role).

**Pros:** Application-level access control; auditable.
**Cons:** Requires auth implementation; operator credential management.

### Option C: Local/dev-only

The endpoint is served only in local/dev mode (e.g., gated by config flag or environment).

**Pros:** Simple gating; no production exposure risk.
**Cons:** Not available in production for real operator use.

### Option D: Disabled unless explicitly enabled

The endpoint is disabled by default and enabled via explicit configuration.

**Pros:** Safe default; operator must opt in.
**Cons:** Requires configuration surface; operator must know to enable.

## Auth/exposure policy and current implementation

**Recommend Option D: disabled unless explicitly enabled, with Option C as fallback for dev/local.**

Rationale:
- The endpoint exposes internal lifecycle state that should not be publicly visible.
- Disabled-by-default is consistent with the existing `NotExposed` operator visibility state.
- Explicit enablement forces operators to acknowledge the endpoint's purpose.
- For dev/local, a simpler config gate (Option C) may suffice.

Historical recommendation. The only implemented exposure policy today is module-level opt-in/internal routing; broader auth/config policy is still future work.

## HTTP status code policy options

### Option 1: Always 200 OK for successful operator status retrieval

The endpoint returns `200 OK` with the lifecycle status in the response body whenever the operator status is successfully retrieved. Lifecycle status is communicated through the response body fields, not through HTTP status codes.

**Pros:** Simple; consistent with REST conventions for resource retrieval; no ambiguity between transport status and domain status.
**Cons:** Operators cannot filter by HTTP status code alone; must inspect response body.

### Option 2: 200 OK for prepared, 503 for non-prepared

The endpoint returns `200 OK` when the transition is `Prepared` and `503 Service Unavailable` when the transition is not prepared.

**Pros:** HTTP status code immediately signals readiness.
**Cons:** Design A always returns `Prepared` from the DI-bound transition, so `503` is unreachable. This option is only useful with Design B's bootstrap-level state.

### Option 3: 200 OK with readiness field

The endpoint returns `200 OK` with a `readiness` field in the body that indicates the current state. HTTP status is always 200 for successful retrieval.

**Pros:** Domain semantics stay in the body; HTTP status is transport-level only.
**Cons:** Similar to Option 1 but with an explicit readiness field.

## HTTP status policy and current implementation

**Recommend Option 1: always `200 OK` for successful operator status retrieval.**

Rationale:
- Design A always returns `Prepared` from the DI-bound transition.
- `503 Service Unavailable` is a Design B concern (bootstrap-level failure state).
- `500 Internal Server Error` is reserved for unexpected handler failures only.
- Lifecycle status is communicated through response body fields, not HTTP status codes.

Historical recommendation. The current Design A implementation returns `200 OK` for successful opt-in/internal status retrieval.

## Response shape candidates

### Candidate 1: `ElasticsearchLifecycleStatusResponse` only

Return the `ElasticsearchLifecycleStatusResponse` directly as the response body.

**JSON shape:**
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

**Pros:**
- Simplest shape; directly uses existing model.
- Already has Circe encoder.
- Flat structure; easy to inspect.

**Cons:**
- Does not include `transitionStatus` or `servingDecision` from the startup status projection.
- Loses the startup transition context (prepared vs failed).
- Future Design B would need a different response shape.

**Future compatibility:** Limited. Adding startup failure status (Design B) would require wrapping or replacing this shape.

**Current source coverage:** Fully covered by `ElasticsearchLifecycleStatusResponse.from(state)`.

### Candidate 2: `ElasticsearchStartupReadinessStatusResponse.Prepared` with nested lifecycle status

Return the `ElasticsearchStartupReadinessStatusResponse` (always `Prepared` variant) with nested `ElasticsearchLifecycleStatusResponse`.

**JSON shape:**
```json
{
  "transitionStatus": "prepared",
  "servingDecision": "not_enforced",
  "lifecycleStatus": {
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
  },
  "productionLifecycleComplete": false
}
```

**Pros:**
- Uses existing `ElasticsearchStartupReadinessStatusResponse` model and Circe encoder.
- Includes `transitionStatus` and `servingDecision` context.
- Natural extension point for Design B (would add `PreparationFailed` variant).
- Cross-model consistency already proven by `ElasticsearchReadinessConsistencySpec`.

**Cons:**
- Nested structure; slightly more complex than flat.
- `transitionStatus` and `servingDecision` are always the same value in Design A.

**Future compatibility:** Good. Design B would add the `PreparationFailed` variant to the same sealed trait. The endpoint response shape would not change; only the variant returned would differ.

**Current source coverage:** Fully covered by `ElasticsearchStartupReadinessStatusResponse.from(transition)`.

### Candidate 3: Wrapper object with metadata

Return a wrapper object with `track`, `status`, `startup`, `lifecycle`, and `limitations` fields.

**JSON shape:**
```json
{
  "track": "es_operator_visibility",
  "design": "A",
  "status": "prepared",
  "startup": {
    "transitionStatus": "prepared",
    "servingDecision": "not_enforced",
    "productionLifecycleComplete": false
  },
  "lifecycle": {
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
  },
  "limitations": [
    "startup_failure_not_visible",
    "replacement_not_configured",
    "freshness_not_tracked",
    "refresh_eager_only",
    "rollback_not_configured",
    "operator_override_not_exposed"
  ]
}
```

**Pros:**
- Self-documenting; includes design identity and explicit limitations.
- Future-proof; can add fields without breaking existing consumers.
- Makes current gaps explicit in the response.

**Cons:**
- Requires a new model and Circe encoder; not backed by existing source.
- More complex than needed for Design A's simple status exposure.
- `limitations` field is static and would need updating as gaps are closed.

**Future compatibility:** Excellent. New fields can be added without breaking changes.

**Current source coverage:** Partially covered. `startup` and `lifecycle` can use existing models. `track`, `design`, `status`, and `limitations` require new code.

## Draft response shape recommendation

**Recommend Candidate 2: `ElasticsearchStartupReadinessStatusResponse` (always `Prepared` variant) with nested `ElasticsearchLifecycleStatusResponse`.**

Rationale:
- Uses existing models and Circe encoders without new code.
- Includes `transitionStatus` and `servingDecision` context that Candidate 1 lacks.
- Natural extension point for Design B (would add `PreparationFailed` variant to the same sealed trait).
- Cross-model consistency already proven by `ElasticsearchReadinessConsistencySpec`.
- Does not expose fields that do not exist yet (unlike Candidate 3's `limitations`).
- Requires no new Elasticsearch calls.

Historical draft recommendation. The current Design A implementation returns `ElasticsearchStartupReadinessStatusResponse.Prepared` with nested `ElasticsearchLifecycleStatusResponse`.

## Implementation prerequisites

The following decisions were resolved for Design A implementation at the module boundary; remaining items below describe future policy work beyond the current opt-in/internal endpoint:

1. **Endpoint path decision** — Choose and approve one endpoint path from the candidates above.
2. **Auth/operator exposure decision** — Choose and approve one auth/exposure policy from the options above.
3. **Response shape decision** — Choose and approve one response shape from the candidates above.
4. **Status code policy decision** — Choose and approve one HTTP status code policy from the options above.
5. **Tapir endpoint/API/module seam decision** — Decide file naming, module placement, and DI wiring approach.
6. **Route graph/rooting tests** — Prove the endpoint is materialized through the intended ES route graph.
7. **No-new-ES-calls assertions** — Prove the endpoint does not introduce new Elasticsearch calls.
8. **Docs update** — Update all related docs to reflect approved decisions.

## Future tests required before implementation

The following tests define the current Design A hardening surface. Source-confirmed in `ES_OPERATOR_VISIBILITY_SOURCE_CONFIRMATION.md`:

**Current coverage:** `ElasticsearchOperatorVisibilityEndpointPolicySpec.scala` now contains active Design A assertions for default-graph absence, explicit opt-in presence, in-memory absence, exact `Prepared` response shape, and no-new-ES-calls behavior. Only future expectations without a current seam remain pending.

1. **Endpoint returns expected prepared/seed-only status shape.**
   - `GET` to approved path returns `200 OK` with `ElasticsearchStartupReadinessStatusResponse` JSON body.
   - The `Prepared` variant includes nested `ElasticsearchLifecycleStatusResponse` with all current seed-only values.
   - `transitionStatus` is `"prepared"`, `servingDecision` is `"not_enforced"`, `productionLifecycleComplete` is `false`.
   - Classification: `Contractual + Blackbox + Group` (in-process route seam).

2. **Endpoint is rooted only in intended ES route graph/module.**
   - The endpoint is materialized through the ES seed route graph, not through `seedCatalogInMemory` or other unrelated modules.
   - Classification: `Contractual + Blackbox + Group` (in-process DI seam).

3. **Endpoint does not alter `POST /beauty-search`.**
   - Existing `POST /beauty-search` behavior is unchanged.
   - The operator endpoint is additive and does not affect product serving.
   - Classification: `Contractual + Blackbox + Group` (existing route seam).

4. **Endpoint introduces no extra Elasticsearch calls.**
   - The endpoint reads from DI-bound models only.
   - No new ES calls are triggered by the endpoint.
   - Classification: `Contractual + Blackbox + Atomic` (pure assertion).

5. **Endpoint is internal/operator-only according to policy.**
   - The endpoint respects the approved auth/exposure policy.
   - If disabled-by-default, the endpoint is not served when not enabled.
   - Classification: `Contractual + Blackbox + Atomic` (policy assertion).

6. **No startup failure status is exposed in Design A.**
   - The endpoint always returns `Prepared` variant.
   - No `PreparationFailed` variant is reachable from the DI-bound transition.
   - Classification: `Contractual + Blackbox + Atomic` (pure assertion).

7. **No replacement/freshness/rollback fields claim implemented behavior.**
   - All gap fields (`replacement`, `freshness`, `refresh`, `rollback`, `operatorVisibility`) return their current seed-only values.
   - `productionLifecycleComplete` is `false`.
   - Classification: `Contractual + Blackbox + Atomic` (pure assertion).

## Future design seams beyond the current Design A implementation

The following implementation seams are source-confirmed as likely future candidates. None are approved.

### New operator visibility API class

Likely file: `leaderboard/api/EsLifecycleStatusApi.scala` (or similar).

Pattern: thin Tapir adapter following `BeautySearchApi` pattern. Takes DI-bound `ElasticsearchStartupReadinessTransition`, projects to `ElasticsearchStartupReadinessStatusResponse`, returns via Tapir endpoint.

### New Tapir endpoints class

Likely file: `leaderboard/http/tapir/EsLifecycleStatusTapirEndpoints.scala` (or similar).

Pattern: pure endpoint contract following `BeautySearchTapirEndpoints` pattern. Defines `GET` endpoint with approved path and response shape.

### Route module wiring location

Likely location: addition to `BeautySearchCatalogBackendModules` or `BeautySearchRouteModules`, or a new module contributing to `many[HttpApi[F]]`.

Pattern: follow existing `BeautySearchPluginModules.api[F]` pattern — create API adapter from DI-bound transition and contribute to `many[HttpApi[F]].weak[...]`.

### DI binding source for status/projection

Source: `ElasticsearchStartupReadinessTransition` is already DI-bound via `BeautySearchCatalogBackendModules.seedResourceElasticsearch` from `ElasticsearchSeedSearchComposition.startupReadinessTransition`.

Projection: `ElasticsearchStartupReadinessStatusResponse.from(transition)` is a pure function available from the DI-bound transition.

## Non-goals

This policy draft does not:

- implement an endpoint;
- approve a route path;
- approve an HTTP status policy;
- approve an auth/operator policy;
- change any source or test file;
- claim production lifecycle completion;
- claim operator visibility is implemented;
- imply Design B or Design C scope.

## Implementation-slice source confirmation

The smallest safe implementation slice for Design A is source-confirmed in `docs/codebase-review/ES_OPERATOR_VISIBILITY_IMPLEMENTATION_SOURCE_CONFIRMATION.md`. Key findings:

- **Can be implemented without new ES calls:** Yes. The endpoint reads from DI-bound `ElasticsearchStartupReadinessTransition` and projects via pure function.
- **Can be implemented without changing `/beauty-search`:** Yes. The endpoint is additive (different method and path).
- **Smallest future files:** new `EsLifecycleStatusTapirEndpoints.scala`, new `EsLifecycleStatusApi.scala`, modification to existing DI wiring module.
- **Resolved for current Design A implementation:** endpoint path, route graph rooting, response shape, HTTP status code, and request-time no-new-ES-calls behavior.
- **Still future:** auth/config seam beyond module-level opt-in, config-level disabled-by-default behavior, local/dev fallback policy, Design B bootstrap failure status, and Design C replacement/freshness/rollback-rich status.

## References

- Implementation-slice source confirmation: `docs/codebase-review/ES_OPERATOR_VISIBILITY_IMPLEMENTATION_SOURCE_CONFIRMATION.md`
- Operator visibility source confirmation: `docs/codebase-review/ES_OPERATOR_VISIBILITY_SOURCE_CONFIRMATION.md`
- Lifecycle status design: `docs/codebase-review/ES_LIFECYCLE_STATUS_DESIGN.md`
- Serving-gate design: `docs/codebase-review/ES_STARTUP_SERVING_GATE_DESIGN.md`
- M5 checkpoint: `docs/codebase-review/M5_ES_LIFECYCLE_CHECKPOINT.md`
- Current gaps and roadmap: `docs/codebase-review/07-current-gaps-and-roadmap.md`
- Tests and contracts: `docs/codebase-review/06-tests-and-contracts.md`
- Architecture decisions: `docs/codebase-review/ARCHITECTURE_DECISIONS_OBSERVED.md`
