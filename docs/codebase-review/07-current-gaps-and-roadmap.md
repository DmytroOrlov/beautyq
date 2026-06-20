# Current Gaps And Roadmap

This file separates current gaps from future recommendations. Do not read recommendations as current implementation.

Status ownership:

- M5 closed as a bounded ES startup-readiness lifecycle checkpoint.
- M6 closed as the Qdrant production-candidate readiness foundation.
- M7 closed as the activation/source-confirmation and serving-policy planning foundation.
- ES post-M5 planning aggregate closed as planning only; runtime route-gate, replacement/freshness/rollback, and full lifecycle operations remain future and unimplemented.
- Qdrant implementation approval is granted only for disabled-by-default explicit opt-in route wiring. `BeautySearchRouteModules.apiQdrantExplicitOptIn` is implemented and remains outside the default ES route.
- Production route activation remains absent.
- Production `POST /beauty-search` remains ES-backed through `LeaderboardPlugin.modules.apiBase[IO]` plus `BeautySearchRouteModules.apiElasticsearch`.
- Full-suite verification status, the Distage include-path NPE closeout, and pending/canceled meanings live in `docs/BEAUTYQ_CURRENT_STATE_AND_HANDOFF.md` and `06-tests-and-contracts.md`. The canonical 8-entry pending-expectation map also lives in `06-tests-and-contracts.md`.

## Confirmed Current Gaps

## ES post-M5 planning aggregate

Closed as planning only:

- M5 remains closed as a bounded startup-readiness lifecycle checkpoint.
- Remaining ES production-lifecycle work is split into three separate future tracks:
  - runtime route-gate deferred under Candidate A;
  - replacement/freshness/rollback future;
  - full lifecycle operations future.
- This aggregate closeout does not implement runtime gating, HTTP 503 behavior, replacement/freshness/rollback, lifecycle operations, route changes, or serving changes.
- Qdrant approval-request status remains separate and does not change any ES lifecycle track.

### Search HTTP Exposure

Full current state is in `docs/BEAUTYQ_CURRENT_STATE_AND_HANDOFF.md`.

Current status:

- Production `POST /beauty-search` is ES-backed seed route: seed catalog → ES index preparation → ES retrieval → Beauty search response projection.
- `POST /beauty-search` is production-included through `LeaderboardPlugin` top-level via `modules.apiBase[IO]` plus `BeautySearchRouteModules.apiElasticsearch`.
- `ElasticsearchPortCfg` is loaded from config section `"elasticsearch"`.
- `seedCatalogInMemory` remains available as rollback/non-default.
- This closes the ES seed-route exposure gap. Production freshness/refresh/staleness, runtime replacement, alias/versioned-index policy, rollback, observability, and kill-switch behavior remain gaps.

Strategic gap:

- M6 is closed as the Qdrant production-candidate readiness foundation. The M7 activation planning/source-confirmation foundation is closed without serving implementation. M5 remains closed as a bounded startup-readiness lifecycle checkpoint; runtime route-gate, replacement/freshness/rollback, and full lifecycle operations are named future tracks, not an unaccepted M5 remainder. Runtime serving-gate work is currently deferred until a runtime readiness source or replacement/freshness/rollback policy exists.
- The full lifecycle operations track is still unstarted. It separately requires command surface/ownership, authorization policy, runtime rebuild/refresh semantics, replacement activation semantics, rollback semantics, disable/kill-switch semantics, stale/current/previous catalog state modeling, progress/failure status fields, and interaction with any later runtime route-gate.
- Future production hardening still needs explicit decisions for typed `4xx` error responses, structured error bodies, request validation, query length limits, lat/lon range validation, catalog source-of-truth policy, freshness/staleness definition, replacement/index-identity policy, refresh trigger semantics, rollback/disable policy, observability/status requirements, kill-switch behavior, and any interaction with a future runtime route-gate.

### BeautySearchService Wiring

Current status:

- `BeautySearchService.Impl` is production-bound through `BeautySearchRouteModules.seedCatalogElasticsearchPortConfigured` → `seedCatalogElasticsearch`.
- The bound backend is `ElasticsearchSearchBackend[F]` over seed-resource ready catalog documents.
- The remaining gap is not service binding; it is catalog source-of-truth, freshness, refresh/replacement, rollback, observability, kill switch, and production lifecycle policy.

Acceptance criteria for future implementation:

- Future production hardening must define catalog replacement source-of-truth, freshness/staleness reporting, runtime replacement behavior, and versioned-index or alias policy if adopted.
- It must define stale/current/previous catalog observability, refresh trigger semantics, rollback/disable behavior, and any interaction with a future runtime route-gate.
- It must decide whether seed-resource startup snapshot readiness remains acceptable as product behavior.
- It must define parser/backend failure representation.
- It must define diagnostics and observability exposure.

### Elasticsearch Runtime Backend

Reached checkpoint (ES seed route, default):

- `BeautySearchRouteModules.seedCatalogElasticsearchPortConfigured` composes ES client module + ES seed route.
- `ElasticsearchClientModules.portConfigured` binds `ElasticsearchJsonClient` from `ElasticsearchPortCfg`.
- `BeautySearchRouteModules.seedCatalogElasticsearch` → `BeautySearchCatalogBackendModules.seedResourceElasticsearch` → seed catalog → ES index → ES retrieval.
- `BeautySearchElasticsearchRouteModuleSpec` proves the hidden ES route module can serve `POST /beauty-search` with a scripted ES client; zero-hit ES responses can still carry non-empty facets/inferred filters from catalog/spec/intent metadata.
- Focused ES route/module graph specs prove the bound `ElasticsearchSeedLifecycleMetadata` and derived `ElasticsearchProductionReadinessState` are available. The state explicitly records `NotEnforced` serving readiness, `NotConfigured` replacement, `NotTracked` freshness, `EagerSeedPreparationOnly` refresh, `NotConfigured` rollback, and `NotExposed` operator visibility.
- `ElasticsearchLifecycleStatusResponse` now provides a pure non-serving projection and Circe encoder for those current values. It is HTTP-exposed only through the explicit opt-in/internal operator endpoint `GET /ops/beauty-search/lifecycle`; it is not exposed by the default ES route graph.
- `ElasticsearchStartupReadinessTransition` now represents pure startup preparation success and source-backed `OperationFailure` failure shape. Prepared transitions derive the current non-serving status response; failed transitions expose no lifecycle metadata or status response. Both outcomes record serving decision `NotEnforced`; the model is now bound through DI from composition but remains non-serving and does not enforce readiness.
- M5 is closed as a bounded startup-readiness lifecycle checkpoint. Runtime route-gate, replacement/freshness/rollback, and full lifecycle operations are intentionally separate future tracks.
- Full verification after default switch.

Evidence:

- Interpreters live in `leaderboard.search.elasticsearch`.
- `BeautySearchElasticsearchIntegrationSpec.scala` creates indexes and executes search through `ElasticsearchTestClient.scala`.
- `LeaderboardPlugin` top-level includes `modules.apiBase[IO]` plus `BeautySearchRouteModules.apiElasticsearch` as default.

Remaining gaps:

- No approved production lifecycle policy for catalog source of truth, freshness/staleness, startup behavior beyond app-start fail-closed, runtime refresh/replacement, alias/versioned-index management, rollback, or stale/current/previous catalog observability.
- No runtime lifecycle operations implementation: no operator-triggered rebuild/refresh command, replacement activation command, rollback command, disable/kill-switch command, or broader lifecycle operator policy beyond explicit opt-in/internal Design A status visibility.
- No repository-backed indexing or live catalog freshness.
- No startup reindex policy, aliases/blue-green, Qdrant production serving, hybrid serving, fallback, score fusion, reranking, or full production lifecycle.

### Qdrant / Hybrid Production Candidate Boundary

Milestone reached:

- `non-production real-resource Qdrant/hybrid manual runner` is achieved.
- Manual runner layers exist: runner composition boundary, manual lifecycle handle, manual input boundary, adapter-input boundary, Qdrant-client input boundary, real-client input boundary, targeted Distage module-shape proof.
- Resource-backed real Qdrant smokes cover explicit indexing (`indexSnapshot()`) and explicit retrieval (`run(...)`) with real Qdrant; they auto-run when Qdrant is available and cancel with reason when unavailable.
- Full-suite verification is already user-reported green after the NPE fix; keep the exact counts in `06-tests-and-contracts.md` to avoid duplicating them here.
- No env gates are required for a passing full run. Llama endpoint and Qdrant benchmark env vars are optional overrides.

Remaining gap:

- Qdrant and hybrid are non-production/manual/local/test boundaries, not production wiring.

Coordinator decision:

- Qdrant remains an active target.
- Shadow-first is not required.
- Real shadow serving and production traffic mirroring are not active objectives.
- Future Qdrant work should move toward direct production-candidate readiness, not shadow machinery, unless a later business decision explicitly re-approves shadow traffic.

### Current phase: ES seed route reached, B-lite eval continues

The current phase is between:

- A: non-production real-resource Qdrant/hybrid manual runner — reached.
- A→B: production-hybrid control-plane v0 — reached.
- B1: production-hidden hybrid activation/handle — reached.
- B2: production-hidden hybrid control-plane modules — reached.
- ES seed route default: reached.
- B-lite: ES-native + Qdrant-native benchmark comparison — eval continues.
- resource-backed hidden Qdrant/hybrid module expansion — paused.
- C: production `/beauty-search` hybrid backend — future.

ES seed route default is the current production state. B-lite eval comparison continues as eval-only work. M5 is closed as a bounded startup-readiness lifecycle checkpoint; runtime route-gate, replacement/freshness/rollback, and full lifecycle operations are intentionally separate future tracks.

Production serving:

```text
current ES seed route (default)
  -> Qdrant production-candidate readiness only if contract parity, indexing/search readiness, quality/eval gates, observability, rollback/disable controls, and explicit activation policy are satisfied
  -> controlled hybrid only after direct production-candidate readiness and explicit serving policy approval
```

Eval/benchmark advances in parallel:

```text
ES-native eval and Qdrant-native eval appear early and together
  -> compare ES-alone, Qdrant-alone, simulated hybrid
  -> decide from metrics, not from architecture enthusiasm
```

Runtime hybrid module expansion is paused after the hidden control-plane module proof.
The active eval checkpoint is expanded M3 / B-lite / M-ESQ-EVAL evidence consolidation: ES-native + Qdrant-native benchmark comparison remains in progress, with evidence interpretation expanded inside the same milestone.

Rationale for pausing runtime hybrid:

* ES seed route is now default, but full production lifecycle is not solved.
* ES-native eval/baseline is not complete.
* Continuing resource-backed hybrid before ES-native + Qdrant-native comparison would optimize the wrong layer.
* The pure `EngineEval` comparison/report/assembly layer is implemented. Remaining work is operational/demo-facing: collect concrete ES + selected Qdrant benchmark reports, compare saved reports, and use the results to guide later Qdrant production-candidate readiness and any later hybrid design.

The codebase contains a hidden/disabled control-plane foundation in
`leaderboard/search/hybrid/control/BeautySearchHybridControlPlane.scala`; the
detailed architecture and type inventory are documented in
`docs/codebase-review/05-search-and-retrieval-architecture.md` and covered by
`BeautySearchHybridControlPlaneSpec.scala`.

This control-plane layer is intentional preparation for B, not production route
wiring. It is required before B so that the future hidden module has an
explicit activation mode, readiness gate, collection identity/version surface,
freshness/staleness policy, kill-switch integration point, observability/readiness
surface, and conservative default behavior.

B has not started as production module wiring. Production route composition
excludes these control-plane types; they remain outside serving behavior, are
not used by the production `/beauty-search` route, and do not construct
Qdrant/Llama resources.

Preserved boundary:

- No Qdrant-as-default.
- No hybrid-as-default.
- No residual-text semantic route.
- No fallback-on-zero-results.
- No score fusion/reranking.
- No benchmark-driven runtime model switching.
- No production collection lifecycle.
- No startup indexing.
- No HTTP routing metadata for hybrid without separate design.
- No production inclusion boundary should default to Qdrant/hybrid or use them as fallback/rollback.

### M-ESQ-EVAL: ES-native + Qdrant-native benchmark comparison

Detailed contracts and metric semantics live in `docs/codebase-review/06-tests-and-contracts.md`. The handoff stays compact and carries only current status and boundaries.

Target milestone: `M-ESQ-EVAL: ES-native + Qdrant-native benchmark comparison`

Status:

* M-ESQ-EVAL pure/report/assembly layer is implemented.
* M3 / B-lite remains the current expanded in-progress checkpoint.
* Current expanded M3 interpretability includes expected-role refinement, `roleDeltas:`, `queryDeltas:`, query-class classification, query-class sidecars, `classDeltas:`, and validated class-sidecar replay for `benchmark-small -> benchmark-large`.
* Remaining work is evidence consolidation around concrete ES + selected Qdrant benchmark reports and using those results to guide later ES lifecycle tracks and future activation/hybrid decisions.
* Still offline/eval only. Production route wiring remains ES-backed seed route.
* M6 is closed as a source-backed pure Qdrant production-candidate readiness foundation. It is readiness groundwork, not real production shadow traffic, production serving, or route activation approval. The current B-lite / M-ESQ-EVAL lane remains offline ES vs Qdrant vs simulated-hybrid comparison.
* M4 is closed. M5 is closed as a bounded startup-readiness lifecycle checkpoint. Runtime route-gate, replacement/freshness/rollback, and full lifecycle operations are intentionally separate future tracks. The M7 activation planning/source-confirmation foundation is closed without route implementation. M8 controlled hybrid serving remains future-only and conditional. Runtime serving-gate work is still deferred behind a runtime readiness source or replacement/freshness/rollback policy.

Goal: Build ES-native + Qdrant-native eval comparison. Compare ES-alone, Qdrant-alone, simulated hybrid (offline only). Decide from metrics. Keep production serving unchanged during eval development.

### Expanded roadmap lanes and milestone gates

Current production search route is ES-backed seed route. InMemory is rollback/regression/pure/non-default support, not current production. Qdrant and hybrid are not production serving. B-lite / M-ESQ-EVAL remains offline/eval-only. Runtime hybrid expansion and hidden Qdrant/hybrid production expansion remain paused. Benchmark output is decision support, not production automation. No Qdrant auto-supplement, no HybridServe, no fallback, no score fusion/reranking. The route already does eager seed index preparation during route composition, but that is not a production-grade ES lifecycle policy.

#### Production / eval split

Production lane and eval/research lane must stay separate until evidence and production safety gates justify joining them.

* Production lane: stabilize and harden ES seed route.
* Eval/research lane: continue B-lite / M-ESQ-EVAL offline evidence.
* Joining requires explicit evidence that ES lifecycle is production-grade, Qdrant direct-candidate readiness is proven, and hybrid policy is validated offline.

#### Lane A: ES-backed production route stabilization

| Step | Checkpoint | Why | Skip risk |
|------|-----------|-----|-----------|
| A0 | Route truth/docs sync | Docs and source must agree before patching | Stale docs lead to wrong hardening targets |
| A1 | ES route characterization | Current behavior is characterized, not validated | Fixing wrong contract wastes cycles |
| A2 | Demo readiness | Business can evaluate query/result quality | Building hardening without feedback |
| A3 | Typed validation / error contract | 4xx errors, structured bodies, request bounds | Silent 500s in production |
| A4 | Observability / kill-switch | Operational safety for first production exposure | Cannot detect or halt degraded serving |
| A5 | Freshness / index lifecycle design | Seed snapshot alone is not durable production behavior | Serving stale data without bounds |
| A6 | ES replacement/freshness/rollback implementation | Source of truth, replacement, freshness, rollback, observability | Serving stale or unmanaged index state |
| A7 | ES full lifecycle operations implementation | Operator command surface, runtime rebuild/refresh, activation, rollback, disable, state transitions | No production-grade operating boundary |

#### Lane B: B-lite / M-ESQ-EVAL offline evidence

| Step | Checkpoint | Why | Skip risk |
|------|-----------|-----|-----------|
| B0 | Pure EngineEval model | Metric semantics must be deterministic before runtime use | Runtime metrics without contract |
| B1 | Saved report comparison support | Must compare across runs, not only single-run snapshots | Cannot track eval progress |
| B2 | Operational/demo-facing run collection | Concrete ES + Qdrant benchmark outputs needed | Eval remains theoretical |
| B3 | Query inventory / expected-role refinement | Metric meaning depends on correct expected roles | Wrong recall/noise classification |
| B4 | ES vs Qdrant vs simulated hybrid evidence report | Decision input for Qdrant direct-candidate and hybrid gates | No evidence to justify next lane |
| B4a | Role and query delta interpretability | Need `roleDeltas:` and `queryDeltas:` to explain changes between reports | Aggregate-only deltas hide where behavior moved |
| B4b | Query-class interpretability and replay | Need query-class classification, sidecars, `classDeltas:`, and validated replay on saved reports | No grounded answer for which classes benefit or should stay silent |

M3/B-lite remains an offline evidence lane. The current expanded checkpoint is usable for interpretability, but it is not closure of the lane and not a readiness signal for Qdrant direct-candidate or hybrid serving.

#### Lane C: Qdrant production-candidate readiness

Qdrant remains active as a direct production-candidate target. M6 is closed as a pure, route-independent readiness foundation covering quality/parity, activation policy, indexing/search, observability, and rollback/disable adapters. M7 is closed as the matching activation/source-confirmation and serving-policy planning foundation. Neither milestone enables production serving or route activation.

| Gate | Why | Skip risk |
|------|-----|-----------|
| Stable production-serving baseline | Readiness metrics need a real baseline to compare against | Readiness against a seed-backed, lifecycle-incomplete route |
| Qdrant candidate quality evidence | Must prove complement over ES misses | Direct-candidate serving without measured value |
| Collection/identity readiness | Reuse versioned identity and strict compatibility results | Silent collection drift |
| Contract parity, indexing, and search readiness | Candidate contracts and operational paths must be explicitly proven | Incomplete candidate behavior |
| Observability and rollback/disable readiness | Operation and disable controls must be configured before approval | Candidate cannot be diagnosed or safely disabled |
| Explicit activation policy | Serving requires a separate approved policy | Readiness state accidentally treated as routing approval |

Closed M6 foundation:

- `QdrantProductionCandidateReadinessStatus`: `Ready`, `NotReady`, `NotEvaluated`, `NotConfigured`, `NotApproved`, `Unknown`.
- `QdrantProductionCandidateReadinessState`: Qdrant active state plus collection/identity, contract parity, indexing, search, quality/eval, observability, rollback/disable, and activation-policy categories.
- `QdrantProductionCandidateReadinessReport`: state plus derived `productionCandidateReady`.
- `QdrantProductionCandidateReadiness`: conservative default, all-required-categories policy, and adapter from the existing collection compatibility result.
- `QdrantProductionCandidateQualityGate`: explicit minimum-query, maximum-recall-deficit, and maximum-noise policy over `QdrantProductionCandidateParityReport`, with an adapter from `EngineEvalAggregateReport`.
- Quality status mapping: no report -> `NotEvaluated`; passed -> `Ready`; failed -> `NotReady(reasons)`; incomplete -> `Unknown`.
- `QdrantProductionCandidateActivationPolicy`: explicit approval, activation scope, route/serving approval, rollback/disable controls, no-regression evidence, observability, and human-readable blocking reasons.
- `QdrantProductionCandidateIndexingReadiness`: expected/prepared/indexed document counts, collection-identity readiness, embedding/vector evidence, deterministic reasons, and readiness-status mapping.
- `QdrantProductionCandidateSearchReadiness`: semantic backend/search contracts, candidate assembly, response projection, BeautySearch contract parity, deterministic reasons, and readiness-status mapping.
- `QdrantProductionCandidateObservabilityReadiness`: readiness/status, quality/eval, and activation-decision report evidence with deterministic reasons and no shadow/traffic-mirroring prerequisite.
- `QdrantProductionCandidateRollbackReadiness`: disable-control, rollback-path, and no-regression evidence with deterministic reasons; its decision never approves production-route activation.
- Indexing/search status mapping: no report -> `Unknown`; complete evidence -> `Ready`; missing/incomplete evidence -> `NotReady(reasons)`.
- Observability and rollback/disable status mapping: no report -> `NotConfigured`; complete evidence -> `Ready`; incomplete evidence -> `NotReady(reasons)`.
- Activation scopes: no activation; candidate readiness only; future explicit opt-in route only; future production route not approved here.
- Activation status mapping: no policy or approval -> `NotApproved`; missing controls/evidence -> `NotReady(reasons)`; approved candidate-only or separately approved future opt-in scope with every required control -> `Ready`. Production-route activation remains blocked by this policy.
- Conservative default: active, but not ready. Quality/eval is not evaluated, rollback/disable is not configured, activation is not approved, and unproven categories remain unknown.
- `QdrantProductionCandidateM6CloseoutSpec` source-confirms all eight categories, every category blocker, all adapter composition, the all-ready active invariant, and the inactive-Qdrant blocker.
- M6 closure does not mean production serving, route switch, fallback, score fusion, reranking, `HybridServe`, Qdrant auto-supplement, shadow serving, production traffic mirroring, or production-route activation approval.

Closed M7 activation planning/source-confirmation foundation:

- `QdrantProductionCandidateActivationPlanning` evaluates pre-wiring prerequisites only; it cannot bind or enable a route.
- `QdrantProductionCandidateActivationConfigApproval` models a disabled-by-default gate plus separate no-regression evidence and evidence approval. It maps only complete approved evidence into the existing planning prerequisites and cannot approve route serving.
- Its pure quality-evidence adapter maps passed `QdrantProductionCandidateQualityReport` evidence to `Satisfied`, failed evidence to `Missing`, and absent/incomplete/unevaluated evidence to `Unknown`; this preserves a distinct approval step.
- Target scopes are candidate readiness only, explicit opt-in route, production route activation, and hybrid serving.
- M6 `productionCandidateReady = true` and a `Ready` activation-policy report are mandatory.
- Any serving scope additionally requires a config gate, no-regression evidence, observability/status evidence, rollback/disable control, and separate route/serving approval.
- The explicit opt-in Qdrant route boundary is source-confirmed as pending only: it must remain a separate module outside default `apiElasticsearch` and consume the pure config/no-regression report before any route wiring. Production route activation is not approved. The per-expectation owner/approval/activation/removal map lives in `06-tests-and-contracts.md`.
- Default `apiElasticsearch` and `LeaderboardPlugin` remain ES-backed. A disabled-by-default explicit Qdrant opt-in route was added as `BeautySearchRouteModules.apiQdrantExplicitOptIn`; no default route switch or hybrid serving was added.
- No production-route behavior was added.
- `QdrantProductionCandidateM7CloseoutSpec` aggregates the accepted pure prerequisites and route-boundary spec evidence while keeping production activation blocked and hybrid serving conditional.
- `QdrantProductionCandidatePostM7NoServingGuardrailSpec` records the post-M7 boundary: M7 closeout, offline eval/no-regression evidence, and the accepted capture-only Option72 decision bundle still do not approve implementation, production route activation, a Qdrant serving route, opt-in Qdrant serving route, route switch, `/beauty-search` behavior change, hybrid serving, shadow serving, or production traffic mirroring.
- `QdrantProductionCandidateServingApprovalRequest` remains the historical approval-request closeout boundary. The current slice grants implementation approval only for disabled-by-default explicit opt-in route wiring; it does not approve production activation, default route switching, hybrid serving, fallback, score fusion, reranking, shadow serving, or traffic mirroring.

#### Lane D: Hybrid policy and serving

| Gate | Why | Skip risk |
|------|-----|-----------|
| Policy design | Serving policy, routing rules, conservative defaults | Uncontrolled hybrid behavior |
| Offline policy simulation | Simulated hybrid over saved eval results | Deploying unproven policy |
| Production-hidden control plane | Activation, readiness, kill-switch, diagnostics | No operational surface for hybrid |
| Controlled serving only after gates | Readiness verified, kill-switch active, fallback policy decided | Hybrid serving without safety net |

#### Lane E: Production-grade search platform

| Gate | Why | Skip risk |
|------|-----|-----------|
| Quality dashboard | Continuous eval visibility | Quality regression undetected |
| Catalog freshness / business lifecycle | Staleness bounds, reindex triggers | Serving stale data indefinitely |
| Ranking / business policy separated from retrieval eval | Ranking changes must not break retrieval contracts | Coupled changes cause regressions |

#### Milestone summary

| Milestone | Description | Current status |
|-----------|-------------|----------------|
| M0 | Current truth locked | Reached |
| M1 | ES seed route demo-stable | Reached |
| M2 | ES route contract hardened | Future |
| M3 | B-lite comparison pipeline usable | In progress / expanded (M-ESQ-EVAL evidence) |
| M4 | ES production lifecycle designed | Closed. HTTP/BeautySearch contract stabilized and frozen; production route exposure documented/tested; bad-input/default decode/semantic structured errors documented/tested; typed-GET/legacy JSON docs pruned or canonicalized; public examples/freeze/checklist recorded. Startup serving-gate design documented in `ES_STARTUP_SERVING_GATE_DESIGN.md`. Full verification is separate from focused validation. |
| M5 | Startup-readiness lifecycle checkpoint | Closed as a bounded startup-readiness lifecycle checkpoint: app-start fail-closed, prepared-serving, non-serving lifecycle metadata/readiness/status/transition/projection seams, DI/rooting, failure classification, and consistency coverage. Remaining ES production-lifecycle tracks are intentionally separate post-M5 future tracks: runtime route-gate, replacement/freshness/rollback, and full lifecycle operations. See `docs/codebase-review/M5_ES_LIFECYCLE_CHECKPOINT.md`. |
| M6 | Qdrant production-candidate readiness: contract parity, indexing/search readiness, quality/eval gates, observability, rollback/disable controls, and explicit activation policy. | Closed: Qdrant production-candidate readiness foundation. Five accepted chunks plus source-confirmation closeout; no serving approval |
| M7 | Activation/source-confirmation and serving-policy planning before any serving, conditional on separate approval | Closed foundation: pure prerequisite/config aggregate plus route-seam source confirmation; no serving implementation or activation approval |
| M8 | Controlled hybrid serving experiment, conditional on explicit business/serving policy and activation approval | Future-only and conditional; not automatic |

#### M4 ES lifecycle design gate — closed

M4 is closed. Its remaining documented scope is source-backed by current docs and tests:

- HTTP/BeautySearch contract stabilized and frozen (`04-api-and-http-contracts.md`).
- Production route exposure documented/tested (`BeautySearchProductionRouteExposureSpec`).
- Bad-input/default decode/semantic structured errors documented/tested (`BeautySearchProductionRouteLimitSpec`, `BeautySearchProductionRouteQuerySpec`, `BeautySearchProductionRouteCoordinateSpec`, `BeautySearchProductionRouteErrorSpec`).
- Typed-GET/legacy JSON docs pruned or canonicalized (`04-api-and-http-contracts.md`, `06-tests-and-contracts.md`).
- Public examples/freeze/checklist recorded (`04-api-and-http-contracts.md`).
- Non-serving lifecycle status shape, startup transition shape, and startup status projection exist with pure model/encoder and focused tests.
- Route-graph state coverage proves lifecycle metadata and readiness state are materialized through ES-backed route graphs.
- Startup serving-gate design documented in `ES_STARTUP_SERVING_GATE_DESIGN.md`.

Full verification is separate from focused validation. M4 was not verified by a full `sbt test` run from this docs pass.

#### M5 startup-readiness lifecycle checkpoint — closed

M5 is closed as a bounded startup-readiness lifecycle checkpoint. The closeout checkpoint is in `docs/codebase-review/M5_ES_LIFECYCLE_CHECKPOINT.md`. Runtime route-gate, replacement/freshness/rollback, and full lifecycle operations are intentionally separate future tracks. Full verification is separate from focused validation.

#### M5 closeout — bounded startup-readiness lifecycle checkpoint

M5 is closed as a bounded startup-readiness lifecycle checkpoint. The full closeout checkpoint is in `docs/codebase-review/M5_ES_LIFECYCLE_CHECKPOINT.md`. Source-confirmed implementation slice analysis is in `docs/codebase-review/ES_STARTUP_SERVING_GATE_SOURCE_CONFIRMATION.md`.

M5 covers:

- app-start fail-closed behavior (implicit from eager composition, test-covered by `ElasticsearchAppStartServingGateSpec`);
- prepared-serving behavior (successful composition allows `POST /beauty-search`, test-covered by `ElasticsearchAppStartServingGateSpec`);
- non-serving lifecycle metadata (`ElasticsearchSeedLifecycleMetadata`);
- non-serving readiness state (`ElasticsearchProductionReadinessState`);
- non-serving lifecycle status response model/encoder (`ElasticsearchLifecycleStatusResponse`);
- non-serving startup transition shape (`ElasticsearchStartupReadinessTransition`);
- non-serving startup status projection (`ElasticsearchStartupReadinessStatusResponse`);
- DI/rooting of prepared transition through ES seed route graphs;
- source-backed failure classification (`preparationFailed`);
- cross-model consistency coverage (`ElasticsearchReadinessConsistencySpec`);
- startup serving-gate design documented in `ES_STARTUP_SERVING_GATE_DESIGN.md`;
- source-confirmed implementation slice analysis in `ES_STARTUP_SERVING_GATE_SOURCE_CONFIRMATION.md`.

M5 does not include:

- runtime HTTP gate (route returns HTTP 503 on non-prepared state);
- operator-visible endpoint/path/auth policy;
- replacement/versioned-index/alias policy;
- freshness tracking;
- refresh trigger semantics;
- rollback policy;
- dashboard/operator integration;
- full production lifecycle verification.

The current `ElasticsearchProductionReadinessState` does not implement these remaining items. It records that readiness is not enforced, replacement and rollback are not configured, freshness is not tracked, refresh is limited to eager seed preparation, and operator visibility is not exposed.

`ElasticsearchStartupReadinessTransition` records current preparation success/failure shape and `NotEnforced` serving decision only. It does not decide whether serving blocks, degrades, or fails fast. Successful compositions now expose a prepared transition through `ElasticsearchSeedSearchComposition.startupReadinessTransition` and it is bound through DI via `BeautySearchCatalogBackendModules.seedResourceElasticsearch`, but the binding remains non-serving and does not gate startup or route behavior.

The documented status shape now has a pure non-serving model/encoder. That is not endpoint exposure, not HTTP policy approval, and not lifecycle completion.

Non-goals remain unchanged here: no route switch, no fallback, no score fusion, no reranking, no `HybridServe`, and no Qdrant auto-supplement.

Remaining ES production-lifecycle work is intentionally split into the named future tracks below.

#### Future ES lifecycle tracks

Remaining ES production lifecycle work is split into named tracks. These tracks are independent of the Qdrant/hybrid roadmap milestones (M6/M7/M8) and do not overwrite those milestone meanings.

| Track | Scope | Dependencies | Current status |
|-------|-------|-------------|----------------|
| ES operator visibility track | Operator-visible lifecycle/status endpoint design and policy: endpoint path, HTTP status, auth/operator policy, status fields distinguishing seed-only/preparing/ready/failed/stale/rollback/disabled states | M5 closeout; Design A closed/implemented; module-level opt-in is the only realized exposure policy | **Design A implemented as explicit opt-in/internal module.** Endpoint path `GET /ops/beauty-search/lifecycle` is implemented. NOT in default ES route graph; available only through `BeautySearchRouteModules.seedCatalogElasticsearchWithOperatorVisibility` / `apiElasticsearchWithOperatorVisibility`. Default `seedCatalogElasticsearch`, default `apiElasticsearch`, `LeaderboardPlugin.modules.apiBase[IO] + BeautySearchRouteModules.apiElasticsearch`, and the in-memory graph do NOT expose the endpoint. Response shape: `ElasticsearchStartupReadinessStatusResponse.Prepared` with nested `ElasticsearchLifecycleStatusResponse`. `200 OK` for successful retrieval. No new ES calls. No `/beauty-search` behavior change. No runtime route gate. Design B (bootstrap failure status) and Design C (replacement/freshness/rollback-rich status) remain future. Tests: active default-absence, explicit-opt-in presence, in-memory absence, exact response-shape, and no-new-ES-calls coverage, with only 2 future expectations left pending. |
| ES runtime serving-gate track | Runtime route-gate policy/implementation: route returns approved HTTP error (e.g., 503) on non-prepared state; requires new source seam because current DI-bound transition is always `Prepared` | M5 closeout; serving-gate policy approval if a runtime readiness source later exists; possible later replacement/freshness/rollback policy | Deferred under Candidate A. Current source truth supports app-start fail-closed only. A successfully constructed ES route graph always binds `Prepared`; `BeautySearchApi` receives no runtime readiness gate; there is no runtime HTTP 503 behavior, stale/previous index state, or replacement/freshness/rollback policy yet. |
| ES replacement/freshness/rollback track | Replacement/versioned-index/alias policy, freshness tracking, refresh trigger semantics, rollback policy | M5 closeout; individual policy designs approved | Not started; `ElasticsearchProductionReadinessState` records `NotConfigured`/`NotTracked`/`EagerSeedPreparationOnly` |
| ES full lifecycle operations track | Runtime operator command surface/ownership, auth/config policy, rebuild/refresh semantics, replacement activation, rollback, disable/kill-switch, state transitions, operation progress/failure status | M5 closeout; replacement/freshness/rollback policy and any runtime gate interaction clarified first or alongside | Not started; no runtime lifecycle operation implementation exists today |

These tracks can proceed independently. The ES operator visibility track is closed for Design A; the ES runtime serving-gate track is still recommended to stay deferred behind a runtime readiness source or replacement/freshness/rollback policy; the ES replacement/freshness/rollback track requires individual policy designs; the ES full lifecycle operations track remains entirely future. None of these future tracks re-open M5.

Dashboard/operator integration and full production lifecycle verification remain downstream of these tracks.

#### Movement rules

* Source truth before patch design.
* Eval evidence before hybrid serving.
* ES lifecycle before Qdrant/hybrid production.
* Shadow before serving is not a Qdrant prerequisite in the current roadmap.
* Kill-switch before risky serving.
* Keep retrieval eval separate from product response assembly.
* Do not jump from validated class-sidecar replay directly to Qdrant shadow or hybrid serving.

#### Near-term sequence

* Continue M-ESQ-EVAL evidence consolidation and checkpoint documentation.
* M6 remains closed and the M7 activation planning/source-confirmation foundation is closed. The disabled-by-default config/no-regression model and pending opt-in module contract are source-confirmed without treating candidate readiness as serving approval or adding a route.
* M5 remains closed as a bounded startup-readiness lifecycle checkpoint. Remaining ES production-lifecycle tracks are runtime route-gate, replacement/freshness/rollback, and full lifecycle operations. The runtime route-gate remains deferred until a runtime readiness source or replacement/freshness/rollback policy exists. This is future-track planning after M5, not an unaccepted checkpoint remainder.
* Current immediate next steps are not Qdrant shadow-first work and not production hybrid.
* Parallel production lane can handle low-risk ES route stabilization/docs/runbook tasks until focused production-hardening bundle exists.

## Current nearest search checkpoint

```text
current production /beauty-search (reached):
  seed resource catalog + ElasticsearchSearchBackend (ES seed route, default)

rollback module (available, non-default):
  seed resource catalog + InMemorySearchBackend
```

ES seed route default is reached.
`seedCatalogInMemory` remains available as rollback/non-default.

Business demo readiness reached: `BeautySearchElasticsearchBusinessDemoSpec` (12 queries passed focused verification), demo runbook at `docs/demo/beauty-search-es-seed-demo.md`, query inventory at `docs/demo/beauty-search-es-seed-demo-queries.md`.

Remaining next steps:

* Collect business feedback on query/result quality.
* Optional repeatable demo output snapshots.
* Repository freshness / live indexing.
* Operational readiness / observability.
* Qdrant direct-candidate/hybrid later (B-lite eval: ES-native + Qdrant-native benchmark comparison).

The goal is not yet full production search lifecycle. The current production
route remains the first ES-backed route over controlled seed data. M6 readiness
foundation closure does not add freshness, repository-backed indexing, Qdrant
production serving, hybrid serving, score fusion, fallback, or production
collection lifecycle.

Simulated hybrid belongs in benchmark/eval only: combines ES EngineEvalResult + Qdrant EngineEvalResult offline, must not imply route wiring, HybridServe, or auto-supplement production responses.

### Seed-Scoped Repository Snapshot Readiness Edge

Resolved mismatch:

- `BeautySearchCatalogSnapshotLoader.SeedScopedFromRepositories` now depends directly on `BeautyQSeedReady`.

Evidence:

- Constructor in `search/document/VariantSearchDocument.scala` now takes `BeautyQSeedReady`, seed data, and repository collaborators.
- Seed-json plus shared-Postgres snapshot paths should depend directly on `BeautyQSeedReady` before repository reads.

Boundary:

- This resolves the seed-scoped dependency-rule mismatch only.
- It does not make production Beauty search ready.
- Future production design still needs source-of-truth and freshness decisions for repository snapshots, Elasticsearch indexes, catalog/index freshness, and whether seed-scoped data is suitable for any production search path.

### Catalog Snapshot / In-Memory Backend Readiness

- `BeautySearchReadyCatalogDocuments` is a src/main helper that wraps a source label plus `VariantSearchDocument` list, rejects empty source labels and empty document lists.
- `BeautySearchCatalogBackendModules.seedResourceInMemory[F]` binds ready documents, `InMemorySearchBackend[F]`, and `BeautySearchService.Impl[F]`; it is available as rollback/non-default through `BeautySearchRouteModules.seedCatalogInMemory[F]`.
- The ES seed route is now the default production path through `seedCatalogElasticsearchPortConfigured` → `seedCatalogElasticsearch`.
- The seed-resource/catalog/in-memory path is rollback/regression readiness only. It does not solve production freshness, staleness bounds, runtime catalog replacement, or stale-catalog observability.
- Production backend selection and freshness/refresh/staleness policy remain future work.

### Salon / Availability Domain

Gap:

- No first-class `Salon` model was found.
- No availability/scheduling/calendar/slot/appointment model was found.

Evidence:

- Targeted search for `Salon|salon|availability|schedule|booking|calendar|slot|appointment` found no main model/API/repo symbols.
- Current provider/location concepts are `Master` and `MasterLocation`.

## Code / Docs Drift

### Tapir Migration Reference

Drift:

- `docs/LOCAL_LLM_TAPIR_HTTP_REFERENCE.md` says only `LadderApi`, `MasterApi`, and `ProfileApi` are migrated.
- Current source has Tapir endpoints and bindings for Category, Service, MasterLocation, MasterServiceOffer, and MasterServiceOfferVariant too.

Recommendation:

- Update the Tapir reference in a later docs-maintenance pass after confirming all current endpoint contracts.

### Incremental docs pruning

Status:

- Docs pruning is now tracked incrementally in `docs/codebase-review/INVENTORY.md` under `Docs pruning candidates`.
- No broad deletion is approved from this roadmap note alone.
- First limited pruning action is complete: the historical MasterServiceOfferVariant typed-GET plan file was removed after canonical references were replaced and current typed-GET truth was confirmed in contract suites plus `04-api-and-http-contracts.md`.
- Second limited cleanup step is complete: the former legacy JSON single-entity GET note was absorbed into canonical HTTP/test docs and then removed after the remaining `ProfileApi` / `LegacyJsonResponse` boundary was documented there.

Current rule:

- Clean touched areas gradually, keep unique source truth until canonical docs absorb it, and delete only after source/test/docs references are cleared.

### README Scope

Drift:

- `README.md` still describes upstream `distage-example`, while the codebase now includes BeautyQ domain/search architecture.

Recommendation:

- Add a BeautyQ-specific README or update the root README after architecture docs stabilize.

## Production Blockers For Search / Hybrid / Qdrant

Current blockers:

- Search route exposure exists through `LeaderboardPlugin` top-level via `modules.apiBase[IO]` plus `BeautySearchRouteModules.apiElasticsearch` (ES seed route, default).
- Default production `BeautySearchService` binding exists through the ES seed route.
- Default production lexical/simple backend binding exists as seed-resource ready catalog documents plus `ElasticsearchSearchBackend[F]`.
- `seedCatalogInMemory` remains available as rollback/non-default.
- The old `BeautySearchProductionInclusionActivation`/`Handle`/`IncludedApis` boundary still exists as a staging/helper boundary but is NOT the active production gate. The route is exposed directly via `LeaderboardPlugin` top-level through `modules.apiBase[IO]` plus `BeautySearchRouteModules.apiElasticsearch`. A real kill switch / enable-disable route gate remains future hardening.
- No production freshness/refresh/staleness policy.
- No runtime catalog replacement policy.
- No stale-catalog observability or kill switch.
- No production Elasticsearch client/indexing lifecycle.
- No explicit search index creation/update lifecycle.
- Route-module state coverage plus pure transition tests now prove seed-only metadata, the non-serving readiness model, and startup preparation result shape only; Design A operator visibility is implemented separately as an explicit opt-in/internal endpoint. M5 is closed as a bounded startup-readiness lifecycle checkpoint. Runtime route-gate, replacement/freshness/rollback, and full lifecycle operations remain intentionally separate future tracks.
- No production collection manager for Qdrant.
- No kill switch or production activation axis for hybrid/Qdrant.
- No production-safe freshness model between Postgres, Elasticsearch, and Qdrant.
- No production routing metadata/API contract.
- No production fallback semantics.
- No score fusion/reranking policy, and docs currently say not to add one implicitly.
- No decided request diagnostics contract.
- No decided parser/backend error shape.
- No decided timeout behavior.
- No decided logging/metrics/tracing surface.
- No decided production backend-readiness failure behavior.
- No decided rollout strategy for first production exposure.
- No rollback/disable story for future production inclusion.
- Bad JSON body handling and typed error responses are not yet designed.
- Max query length is not enforced.
- Lat/lon validation is not implemented.
- Freshness/staleness bounds are not defined.
- Observability surface is not designed.
- Kill-switch behavior is not implemented.
- Elasticsearch is the current ES seed-route backend for `/beauty-search`; Qdrant and hybrid are not production backends for this route. Elasticsearch production lifecycle, freshness, and readiness remain incomplete.

## Repository / Persistence Risks

Risks:

- Schema lives in repository resources rather than migrations, so startup graph dependencies are critical.
- Removing `@unused` parent repo dependencies can break FK-backed table creation order.
- Seed insertion is sequential but not documented as one global transaction.
- Search snapshot loaders still rely on repository seed rows being ready in shared-resource environments, but `SeedScopedFromRepositories` now expresses the direct `BeautyQSeedReady` edge required by repository instructions.

## Benchmark / Eval Gaps

Gaps:

- Eval coverage numbers in docs were not re-verified in pass 2.
- Qdrant benchmark subsets are intentionally small and should not support broad model-quality claims.
- Benchmark decision verdicts are not production automation and no production consumer was found.

## Recommended Next Documentation Pass

1. Line-audit each `*TapirEndpoints` and `*Api` adapter to update stale Tapir docs.
2. Produce a compact route matrix with method/path/request/response/missing-entity behavior.
3. Produce a search wiring decision document: whether to expose search API, which backend to bind first, and what lifecycle owns indexing.
4. Produce a persistence graph diagram with exact constructor dependencies and DDL table names.
5. Produce a test execution matrix with exact SBT commands and resource prerequisites.

## Recommended Next Implementation Steps

These are recommendations only, not current architecture:

1. Design observability, freshness/staleness reporting, runtime refresh/replacement, and kill-switch behavior for the ES seed route.
2. Document and implement the production freshness contract before treating seed-resource startup snapshot readiness as a durable product behavior.
3. Build M-ESQ-EVAL: ES-native + Qdrant-native benchmark comparison. Compare ES-alone, Qdrant-alone, and simulated hybrid (offline only). Decide from metrics.
4. Pause runtime hybrid module expansion until ES/Qdrant eval comparison is improved.
5. Keep Qdrant/hybrid out of this production hardening path unless a separate production design approves it: no Qdrant/hybrid default, no fallback, no reranking, no score fusion, no benchmark-driven routing.
6. Before repository-backed/live indexing, add explicit backend/client/index lifecycle and freshness design.
7. Verify future seed-json plus repository snapshot helpers keep a direct `BeautyQSeedReady` edge when they read shared Postgres state by seed-scoped ids.
8. Reconcile stale docs before relying on them in future implementation passes.
