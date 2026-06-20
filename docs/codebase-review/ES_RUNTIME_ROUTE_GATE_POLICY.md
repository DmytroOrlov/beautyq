# ES Runtime Route Gate Policy

Status: docs-only policy note. Runtime route-gate code is not implemented. This future track is closed for planning only under Candidate A and remains deferred. M5 remains closed; this note does not reopen it.

This note source-confirms the runtime serving-gate track after the current startup-readiness closeout:

- app-start fail-closed is the current behavior when composition fails;
- a successfully constructed ES route graph always binds `ElasticsearchStartupReadinessTransition.Prepared`;
- `BeautySearchApi` does not receive a runtime readiness gate today;
- there is no stale/previous index state, no alias/versioned-index replacement policy, no freshness/staleness policy, no refresh trigger semantics beyond eager seed preparation, no rollback policy, no runtime lifecycle operations implementation, and no HTTP 503 serving gate.
- full lifecycle operations are a separate future track: no runtime rebuild/refresh operation, replacement activation operation, rollback operation, disable/kill-switch operation, stale/current/previous catalog operation state, operator-triggered lifecycle commands, or broader lifecycle auth/config/visibility policy beyond explicit opt-in/internal Design A exists today.
- this is separate future-track planning after closed M5, not an unaccepted M5 remainder.

## Policy candidates

### Candidate A: keep app-start fail-closed only

Meaning:

- keep the current behavior;
- do not add a runtime HTTP gate yet;
- preserve `POST /beauty-search` unchanged for successfully constructed route graphs;
- defer runtime serving-gate work until there is a runtime readiness source or a replacement/freshness/rollback policy to attach it to.

Source seams later:

- none for serving enforcement;
- docs/tests only around current fail-closed behavior.

Test files later:

- existing `ElasticsearchAppStartServingGateSpec.scala` remains the source of truth for current behavior;
- any new tests would be characterization only.

Required policy decisions:

- none beyond current app-start fail-closed behavior.
- future replacement/freshness/rollback policy remains separate and optional under this candidate.
- future full lifecycle operations policy remains separate and optional under this candidate.

Route instance exists on startup failure:

- no; startup failure prevents route construction.

`/beauty-search` behavior changes:

- no.

HTTP 503 appropriate:

- no runtime 503 gate exists under this candidate.

Production baseline or replacement/freshness/rollback required:

- no for current behavior;
- yes if a later runtime gate is desired without changing the app-start fail-closed model.

Recommendation:

- this is the preferred current decision.

### Candidate B: runtime gate on the constructed route

Meaning:

- inject a runtime readiness handle into `BeautySearchApi`;
- keep prepared behavior unchanged;
- return an approved non-ready HTTP error when the route exists but readiness is non-prepared.

Source seams later:

- `BeautySearchApi.scala`;
- `BeautySearchRouteModules.scala` / `BeautySearchCatalogBackendModules.scala` for wiring a runtime readiness handle;
- a new readiness source seam that can represent non-prepared runtime state.

Test files later:

- route-level contract tests for prepared and non-prepared states;
- DI/route-module tests proving the handle is rooted correctly;
- failure-path tests proving no accidental fallback.

Required policy decisions:

- HTTP status for non-prepared;
- error body shape;
- where runtime readiness state lives;
- whether startup failure should still fail app startup or create a degraded route.
- whether the gate can exist before source-of-truth, freshness/staleness, replacement, and rollback policy are defined.

Route instance exists on startup failure:

- only if the startup model is changed to allow degraded construction.

`/beauty-search` behavior changes:

- yes, for non-prepared constructed routes.

HTTP 503 appropriate:

- possible, but only after an explicit policy decision.

Production baseline or replacement/freshness/rollback required:

- a runtime readiness source is required;
- replacement/freshness/rollback is not strictly required for a narrow gate, but the current source truth does not yet provide a non-prepared runtime seam.

### Candidate C: bootstrap-captured operator status, no startup route on failure

Meaning:

- capture startup preparation outcome at bootstrap time;
- expose operator visibility for that state;
- still do not guarantee a `/beauty-search` route on startup failure.

Source seams later:

- bootstrap-state capture outside the route graph;
- operator visibility endpoint wiring;
- possibly `BeautySearchCatalogBackendModules.seedResourceElasticsearch` or a wrapper around it.

Test files later:

- bootstrap-state capture tests;
- operator visibility tests for both prepared and failed startup states;
- route-construction failure tests proving the app still fails closed when it should.

Required policy decisions:

- whether startup failure is only operator-visible or also changes serving;
- whether the failure state is persisted only for process lifetime.

Route instance exists on startup failure:

- not necessarily; this candidate can keep fail-closed startup.

`/beauty-search` behavior changes:

- not necessarily; operator visibility can be additive.

HTTP 503 appropriate:

- optional; depends on whether the bootstrap state is used for serving or only status.

Production baseline or replacement/freshness/rollback required:

- not strictly for visibility;
- still required before a durable production serving policy should rely on this state.

### Candidate D: replacement-aware stale/previous-index gate

Meaning:

- only after replacement/versioned-index/alias policy exists;
- allow serving previous/stale index or return a policy-specific error when replacement is incomplete;
- fold freshness/rollback into the serving policy.

Source seams later:

- replacement/index lifecycle implementation;
- catalog replacement source-of-truth policy;
- freshness tracking;
- rollback implementation;
- runtime gate wiring in `BeautySearchApi` or a dedicated serving seam.

Test files later:

- replacement/freshness/rollback contract tests;
- stale-versus-fresh serving tests;
- rollback tests;
- operator visibility tests for stale/rollback states.

Required policy decisions:

- replacement/index identity policy;
- catalog source-of-truth policy;
- freshness bounds;
- refresh trigger semantics;
- rollback behavior;
- whether stale serving is allowed at all.

Route instance exists on startup failure:

- depends on the replacement model; this is a later production-lifecycle policy.

`/beauty-search` behavior changes:

- yes, if stale/previous serving is allowed.

HTTP 503 appropriate:

- possibly, when replacement is not safe to serve.

Production baseline or replacement/freshness/rollback required:

- yes.

## Recommendation

Keep Candidate A for now: app-start fail-closed only, with no runtime HTTP gate until a runtime readiness source or replacement/freshness/rollback policy exists.

Current boundary to preserve:

- no runtime route-gate implementation;
- no `/beauty-search` behavior change;
- no operator command surface;
- no replacement/freshness/rollback implementation.

## Unresolved decisions before implementation

- HTTP status for non-prepared runtime serving, if a later gate is approved;
- response error body shape;
- where runtime readiness state lives;
- whether startup failure should fail app startup or create a degraded route;
- whether default production routes should ever expose degraded serving state;
- interaction with operator visibility endpoint;
- whether replacement/freshness/rollback must come first for a runtime gate.
- whether full lifecycle operations should remain separate from runtime gating even after replacement/freshness/rollback exists.
