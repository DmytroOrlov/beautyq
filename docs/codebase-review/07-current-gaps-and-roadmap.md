# Current Gaps And Roadmap

This file separates current gaps from future recommendations. Do not read recommendations as current implementation.

## Confirmed Current Gaps

### Search HTTP Exposure

Full current state is in `docs/BEAUTYQ_CURRENT_STATE_AND_HANDOFF.md`.

Current status:

- Production `POST /beauty-search` is ES-backed seed route: seed catalog → ES index preparation → ES retrieval → Beauty search response projection.
- `POST /beauty-search` is production-included through `LeaderboardPlugin` top-level via `modules.apiBase[IO]` plus `BeautySearchRouteModules.apiElasticsearch`.
- `ElasticsearchPortCfg` is loaded from config section `"elasticsearch"`.
- `seedCatalogInMemory` remains available as rollback/non-default.
- This closes the ES seed-route exposure gap. Production freshness/refresh/staleness, runtime replacement, observability, and kill-switch behavior remain gaps.

Strategic gap:

- The next strategic gate is ES production lifecycle design, not Qdrant shadow implementation.
- Future production hardening still needs explicit decisions for typed `4xx` error responses, structured error bodies, request validation, query length limits, lat/lon range validation, freshness/staleness, observability, kill-switch, and lifecycle/source-of-truth policy.

### BeautySearchService Wiring

Current status:

- `BeautySearchService.Impl` is production-bound through `BeautySearchRouteModules.seedCatalogElasticsearchPortConfigured` → `seedCatalogElasticsearch`.
- The bound backend is `ElasticsearchSearchBackend[F]` over seed-resource ready catalog documents.
- The remaining gap is not service binding; it is freshness, refresh/replacement, observability, kill switch, and production source-of-truth policy.

Acceptance criteria for future implementation:

- Future production hardening must define freshness/staleness reporting and runtime replacement behavior.
- It must define stale-catalog observability and kill-switch behavior.
- It must decide whether seed-resource startup snapshot readiness remains acceptable as product behavior.
- It must define parser/backend failure representation.
- It must define diagnostics and observability exposure.

### Elasticsearch Runtime Backend

Reached checkpoint (ES seed route, default):

- `BeautySearchRouteModules.seedCatalogElasticsearchPortConfigured` composes ES client module + ES seed route.
- `ElasticsearchClientModules.portConfigured` binds `ElasticsearchJsonClient` from `ElasticsearchPortCfg`.
- `BeautySearchRouteModules.seedCatalogElasticsearch` → `BeautySearchCatalogBackendModules.seedResourceElasticsearch` → seed catalog → ES index → ES retrieval.
- `BeautySearchElasticsearchRouteModuleSpec` proves the hidden ES route module can serve `POST /beauty-search` with a scripted ES client; zero-hit ES responses can still carry non-empty facets/inferred filters from catalog/spec/intent metadata.
- Focused ES route/module graph specs now prove the bound `ElasticsearchSeedLifecycleMetadata` is available with eager seed preparation and `SeedOnlyNotProductionLifecycle`.
- Full verification after default switch.

Evidence:

- Interpreters live in `leaderboard.search.elasticsearch`.
- `BeautySearchElasticsearchIntegrationSpec.scala` creates indexes and executes search through `ElasticsearchTestClient.scala`.
- `LeaderboardPlugin` top-level includes `modules.apiBase[IO]` plus `BeautySearchRouteModules.apiElasticsearch` as default.

Remaining gaps:

- No approved production lifecycle policy for source of truth, freshness/staleness, startup behavior, runtime refresh/replacement, rollback, or stale-catalog observability.
- No repository-backed indexing or live catalog freshness.
- No startup reindex policy, aliases/blue-green, Qdrant shadowing, hybrid serving, fallback, score fusion, reranking, or production lifecycle.

### Qdrant / Hybrid Production Boundary

Milestone reached:

- `non-production real-resource Qdrant/hybrid manual runner` is achieved.
- Manual runner layers exist: runner composition boundary, manual lifecycle handle, manual input boundary, adapter-input boundary, Qdrant-client input boundary, real-client input boundary, targeted Distage module-shape proof.
- Resource-backed real Qdrant smokes cover explicit indexing (`indexSnapshot()`) and explicit retrieval (`run(...)`) with real Qdrant; they auto-run when Qdrant is available and cancel with reason when unavailable.
- User-verified plain `sbt test` reported 963 succeeded, 0 failed, 1 canceled.
- No env gates are required for a passing full run. Llama endpoint and Qdrant benchmark env vars are optional overrides.

Remaining gap:

- Qdrant and hybrid are non-production/manual/local/test boundaries, not production wiring.

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

ES seed route default is the current nearest checkpoint. B-lite eval comparison continues as eval-only work.

Production serving:

```text
current ES seed route (default)
  -> Qdrant shadow only if eval proves complement
  -> controlled hybrid only after readiness/kill-switch/policy
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
* The pure `EngineEval` comparison/report/assembly layer is implemented. Remaining work is operational/demo-facing: collect concrete ES + selected Qdrant benchmark reports, compare saved reports, and use the results to guide later Qdrant shadow/hybrid design.

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
* Remaining work is evidence consolidation around concrete ES + selected Qdrant benchmark reports and using those results to guide later ES lifecycle design and any future Qdrant shadow design.
* Still offline/eval only. Production route wiring remains ES-backed seed route.
* The next strategic gate is ES lifecycle design before any Qdrant shadow implementation. Qdrant shadow is a future M6 readiness/design step, not the current implementation step, because shadowing should follow a stable serving baseline, explicit observability, and an operational kill-switch. The current B-lite / M-ESQ-EVAL lane already covers offline ES vs Qdrant vs simulated hybrid comparison; without a production-grade ES lifecycle baseline, shadow metrics would only compare against a seed-backed, lifecycle-incomplete route.
* M4/M5 lifecycle work is active but still below production lifecycle readiness. M6 Qdrant shadow readiness, M7 hybrid policy, and M8 controlled hybrid serving are not reached.

Goal: Build ES-native + Qdrant-native eval comparison. Compare ES-alone, Qdrant-alone, simulated hybrid (offline only). Decide from metrics. Keep production serving unchanged during eval development.

### Expanded roadmap lanes and milestone gates

Current production search route is ES-backed seed route. InMemory is rollback/regression/pure/non-default support, not current production. Qdrant and hybrid are not production serving. B-lite / M-ESQ-EVAL remains offline/eval-only. Runtime hybrid expansion and hidden Qdrant/hybrid production expansion remain paused. Benchmark output is decision support, not production automation. No Qdrant auto-supplement, no HybridServe, no fallback, no score fusion/reranking. The route already does eager seed index preparation during route composition, but that is not a production-grade ES lifecycle policy.

#### Production / eval split

Production lane and eval/research lane must stay separate until evidence and production safety gates justify joining them.

* Production lane: stabilize and harden ES seed route.
* Eval/research lane: continue B-lite / M-ESQ-EVAL offline evidence.
* Joining requires explicit evidence that ES lifecycle is production-grade, Qdrant shadow is proven, and hybrid policy is validated offline.

#### Lane A: ES-backed production route stabilization

| Step | Checkpoint | Why | Skip risk |
|------|-----------|-----|-----------|
| A0 | Route truth/docs sync | Docs and source must agree before patching | Stale docs lead to wrong hardening targets |
| A1 | ES route characterization | Current behavior is characterized, not validated | Fixing wrong contract wastes cycles |
| A2 | Demo readiness | Business can evaluate query/result quality | Building hardening without feedback |
| A3 | Typed validation / error contract | 4xx errors, structured bodies, request bounds | Silent 500s in production |
| A4 | Observability / kill-switch | Operational safety for first production exposure | Cannot detect or halt degraded serving |
| A5 | Freshness / index lifecycle design | Seed snapshot alone is not durable production behavior | Serving stale data without bounds |
| A6 | ES production lifecycle implementation | Index creation, update, alias, readiness | No production-grade ES lifecycle |

#### Lane B: B-lite / M-ESQ-EVAL offline evidence

| Step | Checkpoint | Why | Skip risk |
|------|-----------|-----|-----------|
| B0 | Pure EngineEval model | Metric semantics must be deterministic before runtime use | Runtime metrics without contract |
| B1 | Saved report comparison support | Must compare across runs, not only single-run snapshots | Cannot track eval progress |
| B2 | Operational/demo-facing run collection | Concrete ES + Qdrant benchmark outputs needed | Eval remains theoretical |
| B3 | Query inventory / expected-role refinement | Metric meaning depends on correct expected roles | Wrong recall/noise classification |
| B4 | ES vs Qdrant vs simulated hybrid evidence report | Decision input for Qdrant shadow and hybrid gates | No evidence to justify next lane |
| B4a | Role and query delta interpretability | Need `roleDeltas:` and `queryDeltas:` to explain changes between reports | Aggregate-only deltas hide where behavior moved |
| B4b | Query-class interpretability and replay | Need query-class classification, sidecars, `classDeltas:`, and validated replay on saved reports | No grounded answer for which classes benefit or should stay silent |

M3/B-lite remains an offline evidence lane. The current expanded checkpoint is usable for interpretability, but it is not closure of the lane and not a readiness signal for Qdrant shadow or hybrid serving.

#### Lane C: Qdrant shadow readiness

Qdrant remains eval-only until evidence and safety gates.

| Gate | Why | Skip risk |
|------|-----|-----------|
| Qdrant candidate quality evidence | Must prove complement over ES misses | Shadowing without measured value |
| Collection lifecycle design | Versioned names, compatibility, readiness | Silent collection drift |
| Shadow mode before serving | Must not affect user responses until proven | Degraded serving from unproven backend |

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
| M4 | ES production lifecycle designed | Design gate documented / active |
| M5 | ES production lifecycle implemented | Progress: non-serving `ElasticsearchSeedLifecycleMetadata` DI/readiness handle plus route-module metadata coverage only; production lifecycle incomplete |
| M6 | Qdrant shadow readiness | Future |
| M7 | Hybrid policy proven offline | Future |
| M8 | Controlled hybrid serving experiment | Future |

#### M4 ES lifecycle design gate

M4 is a design gate, not an implementation patch. It exists to prevent production lifecycle work from being implied by the current eager seed index preparation path.

Roadmap position:

* Expanded M3 / B-lite / M-ESQ-EVAL evidence remains the current in-progress checkpoint.
* M4 is the next strategic gate after that evidence checkpoint is expanded enough to support lifecycle decisions.
* M5 implementation now includes a non-serving `ElasticsearchSeedLifecycleMetadata` DI/readiness handle, but production lifecycle remains incomplete.
* Qdrant shadow remains a future M6 readiness/design step and should not precede ES lifecycle/baseline/observability/kill-switch decisions.
* Option 27 progress is route-module metadata coverage only: seed index metadata is available through DI, eager seed preparation is wired, lifecycle status is `SeedOnlyNotProductionLifecycle`, and serving behavior is unchanged.

Decisions to make at M4:

* production catalog source of truth for read models: keep seed resource, move to repository snapshot, or define the path to future repository-backed production indexing;
* index naming/versioning policy;
* alias / blue-green, direct replacement, or another explicit replacement strategy;
* startup behavior and readiness expectations for the currently exposed route;
* runtime refresh/replacement trigger and operator surface;
* rollback behavior and rollback trigger semantics;
* freshness/staleness metadata and the meaning of stale catalog state;
* observability events/metrics for readiness, replacement, freshness, and stale-catalog detection;
* production kill-switch or route enable-disable behavior.

Validation taxonomy for later M5 implementation:

* pure document-builder tests for catalog snapshot to search-document construction;
* pure lifecycle policy/model tests if explicit lifecycle policy types are introduced;
* module/DI wiring tests for lifecycle boundaries and route composition;
* focused ES integration/manual smoke only when source-confirmed;
* no plain `sbt test` by agents.

Strategic gate intent:

* M4 should close design ambiguity first, not blur into partial implementation.
* M5 should only implement an approved lifecycle shape, not discover it while coding.
* Qdrant shadow should remain after M4/M5 because shadow metrics are only operationally meaningful once the ES baseline has explicit lifecycle, observability, and kill-switch behavior.

Risks if M4 is skipped:

* eager seed index preparation gets mistaken for approved production lifecycle policy;
* shadow/hybrid work gets compared against a lifecycle-incomplete ES baseline;
* refresh, rollback, and stale-catalog behavior remain undefined at the route boundary.

#### M5 remaining work checklist

M5 remains incomplete. Remaining work is the production lifecycle contract plus implementation for:

- startup readiness: readiness decision before serving, preparation failure behavior, and whether serving blocks, degrades, or fails fast;
- replacement: explicit old/new index replacement policy, atomicity expectations, and alias or versioned-index policy if required;
- freshness: running-service version/freshness source of truth, operator-visible timestamp/version/count, and stale-data detection;
- refresh: approved trigger semantics such as manual, startup-only, scheduled, or external trigger;
- rollback: previous known-good index/version recovery path and retained rollback-supporting state;
- operator-visible status: explicit status fields/surface that distinguish seed-only, preparing, ready, failed, stale, rollback, or disabled states.

Non-goals remain unchanged here: no route switch, no fallback, no score fusion, no reranking, no `HybridServe`, and no Qdrant auto-supplement.

#### Movement rules

* Source truth before patch design.
* Eval evidence before hybrid serving.
* ES lifecycle before Qdrant/hybrid production.
* Shadow before serving.
* Kill-switch before risky serving.
* Keep retrieval eval separate from product response assembly.
* Do not jump from validated class-sidecar replay directly to Qdrant shadow or hybrid serving.

#### Near-term sequence

* Continue M-ESQ-EVAL evidence consolidation and checkpoint documentation.
* Next safe decisions are still: docs/evidence consolidation and ES lifecycle design; Qdrant shadow design remains a later M6 step only after source-confirmed evidence bundles and an approved ES lifecycle baseline.
* Current immediate next steps are not Qdrant shadow readiness and not production hybrid.
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
* Qdrant shadow/hybrid later (B-lite eval: ES-native + Qdrant-native benchmark comparison).

The goal is not yet full production search lifecycle. The goal is to demonstrate
the first ES-backed production route over controlled seed data before adding
freshness, repository-backed indexing, Qdrant shadowing, hybrid serving, score
fusion, fallback, or production collection lifecycle.

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

### MasterServiceOfferVariant Typed GET Plan

Drift:

- `docs/http-master-service-offer-variant-typed-get-plan.md` describes `MasterServiceOfferVariantApi` as still legacy.
- `docs/http-legacy-json-contracts.md` says no Beauty single-entity GET endpoints remain legacy and identifies `MasterServiceOfferVariantApi` as migrated.

Recommendation:

- Mark `http-master-service-offer-variant-typed-get-plan.md` as historical or replace it with a migration-completed note in a later docs-maintenance pass.

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
- Route-module metadata coverage now proves the seed-only seam only; it does not implement startup readiness, replacement, freshness, refresh, rollback, or operator-visible production lifecycle status.
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
