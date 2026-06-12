# Current Gaps And Roadmap

This file separates current gaps from future recommendations. Do not read recommendations as current implementation.

## Confirmed Current Gaps

### Search HTTP Exposure

Full current state is in `docs/BEAUTYQ_CURRENT_STATE_AND_HANDOFF.md`.

Current status:

- Production `POST /beauty-search` is ES-backed seed route: seed catalog → ES index preparation → ES retrieval → Beauty search response projection.
- `POST /beauty-search` is production-included through `LeaderboardPlugin.modules.api` via `BeautySearchRouteModules.seedCatalogElasticsearchPortConfigured` → `seedCatalogElasticsearch`.
- `ElasticsearchPortCfg` is loaded from config section `"elasticsearch"`.
- `seedCatalogInMemory` remains available as rollback/non-default.
- This closes the ES seed-route exposure gap. Production freshness/refresh/staleness, runtime replacement, observability, and kill-switch behavior remain gaps.

Future implementation:

- The next code patch should design or implement observability, freshness/staleness reporting, runtime refresh/replacement, and kill-switch behavior for the ES seed route.
- Future production hardening still needs explicit decisions for typed `4xx` error responses, structured error bodies, request validation, query length limits, lat/lon range validation, freshness/staleness, observability, and kill-switch.

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
- Full verification after default switch.

Evidence:

- Interpreters live in `leaderboard.search.elasticsearch`.
- `BeautySearchElasticsearchIntegrationSpec.scala` creates indexes and executes search through `ElasticsearchTestClient.scala`.
- `LeaderboardPlugin.modules.api` now includes `BeautySearchRouteModules.seedCatalogElasticsearchPortConfigured` → `seedCatalogElasticsearch` as default.

Remaining gaps:

- No repository-backed indexing or live catalog freshness.
- No startup reindex policy, aliases/blue-green, Qdrant shadowing, hybrid serving, fallback, score fusion, reranking, or production lifecycle.

### Qdrant / Hybrid Production Boundary

Milestone reached:

- `non-production real-resource Qdrant/hybrid manual runner` is achieved.
- Manual runner layers exist: runner composition boundary, manual lifecycle handle, manual input boundary, adapter-input boundary, Qdrant-client input boundary, real-client input boundary, targeted Distage module-shape proof.
- Env-gated real Qdrant smokes cover explicit indexing (`indexSnapshot()`) and explicit retrieval (`run(...)`) with real Qdrant.
- User-verified external-enabled full validation run was reported green.
- Env gates used: `BEAUTYQ_MANUAL_HYBRID_REAL_QDRANT_INDEXING_SMOKE=true`, `BEAUTYQ_MANUAL_HYBRID_REAL_QDRANT_RETRIEVAL_SMOKE=true`, plus Qdrant/Llama benchmark/integration gates.

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
The next target is M-ESQ-EVAL (= measured Elasticsearch-native + Qdrant-native evaluation comparison): ES-native + Qdrant-native benchmark comparison.

Rationale for pausing runtime hybrid:

* ES seed route is now default, but full production lifecycle is not solved.
* ES-native eval/baseline is not complete.
* Continuing resource-backed hybrid before ES-native + Qdrant-native comparison would optimize the wrong layer.
* The pure `EngineEval` comparison/report/assembly layer is implemented. Remaining work is operational/demo-facing: collect concrete ES + selected Qdrant benchmark reports, compare saved reports, and use the results to guide later Qdrant shadow/hybrid design.

The codebase contains pure control-plane value/decision types in
`leaderboard/search/hybrid/control/BeautySearchHybridControlPlane.scala`
(`BeautySearchHybridSnapshotIdentity`, `BeautySearchHybridCollectionIdentity`,
`BeautySearchHybridFreshnessPolicy`, `BeautySearchHybridRuntimeMode`,
`BeautySearchHybridServingPolicy`, `BeautySearchHybridReadinessStatus`,
`BeautySearchHybridServingDecision`, `BeautySearchHybridReadiness[F]`,
`BeautySearchHybridDiagnosticsSink[F]`, `BeautySearchHybridDiagnosticsEvent.DecisionEvaluated`,
`BeautySearchHybridDecisionEvaluator[F]`), covered by
`BeautySearchHybridControlPlaneSpec.scala`.

This control-plane layer is intentional preparation for B, not production route
wiring. It is required before B so that the future hidden module has an
explicit activation mode, readiness gate, collection identity/version surface,
freshness/staleness policy, kill-switch integration point, observability/readiness
surface, and conservative default behavior.

B has not started as production module wiring. The control-plane types are not
wired into `LeaderboardPlugin.modules.api`, not used by the production
`/beauty-search` route, and do not construct Qdrant/Llama resources.

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

Full API, status, and metric semantics in `docs/BEAUTYQ_CURRENT_STATE_AND_HANDOFF.md`.

Target milestone: `M-ESQ-EVAL: ES-native + Qdrant-native benchmark comparison`

Status:

* M-ESQ-EVAL pure/report/assembly layer is implemented.
* Remaining work is operational/demo-facing use: run/collect concrete ES + selected Qdrant benchmark reports, compare saved reports, and use results to guide later Qdrant shadow/hybrid design.
* Still offline/eval only. Production route wiring is now ES-backed seed route.

Goal: Build ES-native + Qdrant-native eval comparison. Compare ES-alone, Qdrant-alone, simulated hybrid (offline only). Decide from metrics. Keep production serving unchanged during eval development.

## Current nearest search checkpoint

```text
current production /beauty-search (reached):
  seed resource catalog + ElasticsearchSearchBackend (ES seed route, default)

rollback module (available, non-default):
  seed resource catalog + InMemorySearchBackend
```

ES seed route default is reached.
`seedCatalogInMemory` remains available as rollback/non-default.

Next checkpoint: business demo readiness / runbook / repeatable demo over real ES environment.

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
- AGENTS instructions require seed-json plus shared-Postgres snapshot paths to depend directly on `BeautyQSeedReady` before repository reads.

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

- Search route exposure exists through `LeaderboardPlugin.modules.api` including `BeautySearchRouteModules.seedCatalogElasticsearchPortConfigured` → `seedCatalogElasticsearch` (ES seed route, default).
- Default production `BeautySearchService` binding exists through the ES seed route.
- Default production lexical/simple backend binding exists as seed-resource ready catalog documents plus `ElasticsearchSearchBackend[F]`.
- `seedCatalogInMemory` remains available as rollback/non-default.
- The old `BeautySearchProductionInclusionActivation`/`Handle`/`IncludedApis` boundary still exists as a staging/helper boundary but is NOT the active production gate. The route is exposed directly via `LeaderboardPlugin.modules.api` include. A real kill switch / enable-disable route gate remains future hardening.
- No production freshness/refresh/staleness policy.
- No runtime catalog replacement policy.
- No stale-catalog observability or kill switch.
- No production Elasticsearch client/indexing lifecycle.
- No explicit search index creation/update lifecycle.
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
- Qdrant/hybrid/Elasticsearch are not production backends for this route.

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
