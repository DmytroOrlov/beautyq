# Current Gaps And Roadmap

This file separates current gaps from future recommendations. Do not read recommendations as current implementation.

## Confirmed Current Gaps

### Search HTTP Exposure

Full current state is in `docs/BEAUTYQ_CURRENT_STATE_AND_HANDOFF.md`.

Current status:

- Production `POST /beauty-search` is seed-resource catalog snapshot + `InMemorySearchBackend[F]`. It is not hybrid, not Qdrant, and not Elasticsearch.
- `POST /beauty-search` is production-included through `LeaderboardPlugin.modules.api` via `BeautySearchRouteModules.seedCatalogInMemory[F]`.
- This closes the route exposure gap only. Production freshness/refresh/staleness, runtime replacement, observability, and kill-switch behavior remain gaps.

Future implementation:

- The next code patch should design or implement observability, freshness/staleness reporting, runtime refresh/replacement, and kill-switch behavior for the included seed-resource/in-memory route.
- Future production hardening still needs explicit decisions for typed `4xx` error responses, structured error bodies, request validation, query length limits, lat/lon range validation, freshness/staleness, observability, and kill-switch.

### BeautySearchService Wiring

Current status:

- `BeautySearchService.Impl` is production-bound through `BeautySearchRouteModules.seedCatalogInMemory[F]`.
- The bound backend is `InMemorySearchBackend[F]` over seed-resource ready catalog documents.
- The remaining gap is not service binding; it is freshness, refresh/replacement, observability, kill switch, and production source-of-truth policy.

Acceptance criteria for future implementation:

- Future production hardening must define freshness/staleness reporting and runtime replacement behavior.
- It must define stale-catalog observability and kill-switch behavior.
- It must decide whether seed-resource startup snapshot readiness remains acceptable as product behavior.
- It must define parser/backend failure representation.
- It must define diagnostics and observability exposure.

### Elasticsearch Runtime Backend

Gap:

- Elasticsearch interpreters and integration tests exist, but no production Elasticsearch `BeautySearchBackend` binding or indexing lifecycle was found.
- A production-hidden `BeautySearchRouteModules.seedCatalogElasticsearch` route module now exists, composable by any including graph that provides an `ElasticsearchJsonClient` binding.
- `BeautySearchElasticsearchRouteModuleSpec` proves the hidden ES route module can serve `POST /beauty-search` with a scripted ES client; zero-hit ES responses can still carry non-empty facets/inferred filters from catalog/spec/intent metadata.

Evidence:

- Interpreters live in `leaderboard.search.elasticsearch`.
- `BeautySearchElasticsearchIntegrationSpec.scala` creates indexes and executes search through `ElasticsearchTestClient.scala`.
- `LeaderboardPlugin.modules.api` still includes `BeautySearchRouteModules.seedCatalogInMemory[F]`, not the ES route module.

Future implementation boundary:

- The first production backend should stay lexical/simple-first.
- A fake, catalog snapshot, or in-memory backend is a valid first controlled route-contract backend.
- If Elasticsearch is selected later, it requires explicit index lifecycle, readiness, freshness, and failure-behavior design before production binding.

### Qdrant / Hybrid Production Boundary

Milestone reached:

- `non-production real-resource Qdrant/hybrid manual runner` is achieved.
- Manual runner layers exist: runner composition boundary, manual lifecycle handle, manual input boundary, adapter-input boundary, Qdrant-client input boundary, real-client input boundary, targeted Distage module-shape proof.
- Env-gated real Qdrant smokes cover explicit indexing (`indexSnapshot()`) and explicit retrieval (`run(...)`) with real Qdrant.
- User-verified external-enabled full validation run was reported green.
- Env gates used: `BEAUTYQ_MANUAL_HYBRID_REAL_QDRANT_INDEXING_SMOKE=true`, `BEAUTYQ_MANUAL_HYBRID_REAL_QDRANT_RETRIEVAL_SMOKE=true`, plus Qdrant/Llama benchmark/integration gates.

Remaining gap:

- Qdrant and hybrid are non-production/manual/local/test boundaries, not production wiring.

### Current phase: A -> B-lite, ES/Qdrant eval comparison

The current phase is between:

- A: non-production real-resource Qdrant/hybrid manual runner — reached.
- A→B: production-hybrid control-plane v0 — reached.
- B1: production-hidden hybrid activation/handle — reached.
- B2: production-hidden hybrid control-plane modules — reached.
- B-lite: ES-native + Qdrant-native benchmark comparison — current target.
- resource-backed hidden Qdrant/hybrid module expansion — paused.
- C: production `/beauty-search` hybrid backend — future.

B-lite is the current strategic direction after B2.

Production serving stays sequential and safe:

```text
current seed/in-memory route
  -> ES lexical baseline
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

* Current production search is still seed/in-memory.
* ES-native eval/baseline is not complete.
* Continuing resource-backed hybrid before ES-native + Qdrant-native comparison would optimize the wrong layer.
* The pure `EngineEval` comparison model is implemented; the next step is connecting ES and Qdrant executor outputs to normalized `EngineEvalResult`.

The codebase now contains pure control-plane value/decision types:
`BeautySearchHybridSnapshotIdentity`,
`BeautySearchHybridCollectionIdentity`,
`BeautySearchHybridFreshnessPolicy`,
`BeautySearchHybridRuntimeMode` (`SeedCatalogOnly`, `HybridShadow`,
`HybridServe`), `BeautySearchHybridServingPolicy`,
`BeautySearchHybridReadinessStatus`, `BeautySearchHybridServingDecision`,
`BeautySearchHybridReadiness[F]`, `BeautySearchHybridDiagnosticsSink[F]`,
`BeautySearchHybridDiagnosticsEvent.DecisionEvaluated`, and
`BeautySearchHybridDecisionEvaluator[F]`.
They live in
`leaderboard/search/hybrid/control/BeautySearchHybridControlPlane.scala` and are
covered by `BeautySearchHybridControlPlaneSpec.scala`.

Semantics:

- `SeedCatalogOnly` keeps the existing seed-catalog production path.
- `HybridShadow` is diagnostics/shadow only and must not affect user response.
- `HybridServe` can serve only when readiness is `Ready`.
- `NotReady` conservatively resolves to `UseSeedCatalogOnly` in v0.
- `BeautySearchHybridDecisionEvaluator[F]` only reads readiness, computes
  decision, reports diagnostics via `DecisionEvaluated`, and returns decision.
  It does not run hybrid retrieval, indexing, Qdrant, Llama, HTTP, or route
  behavior.

This control-plane layer is intentional preparation for B, not production route
wiring. It is not a deviation from the roadmap. It is required before B so that
the future hidden module has an explicit activation mode, readiness gate,
collection identity/version surface, freshness/staleness policy, kill-switch
integration point, observability/readiness surface, and conservative default
behavior.

B has not started as production module wiring. The control-plane types are not
wired into `LeaderboardPlugin.modules.api`, not used by the production
`/beauty-search` route, and do not construct Qdrant/Llama resources.

Evidence:

- Docs `search-dsl-qdrant-vector-backend.md` and `search-dsl-hybrid-v1-plan.md` explicitly say production hybrid is not implemented.
- Classes are named `QdrantNonProductionExperiment*`, `BeautyQNonProductionHybrid*`, and `Experimental*`.
- `LeaderboardPlugin.scala` has no Qdrant/hybrid search binding.

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

Target milestone:

```text
M-ESQ-EVAL: ES-native + Qdrant-native benchmark comparison
```

Status:

* Started by the pure `EngineEval` comparison model.
* Not complete.
* Next work: connect ES and Qdrant eval/executor outputs to normalized `EngineEvalResult`.
* Still offline/eval only.
* Production route wiring exists for `POST /beauty-search` via seed-resource catalog + `InMemorySearchBackend`. Elasticsearch, Qdrant, and hybrid remain not production-wired.

Goal:

* Build ES-native eval and Qdrant-native eval comparison layer.
* Compare ES-alone, Qdrant-alone, and simulated hybrid (offline only).
* Decide from metrics, not from architecture enthusiasm.
* Keep production serving unchanged during eval development.

## Current nearest search checkpoint

```text
current production /beauty-search:
  seed resource catalog + InMemorySearchBackend

hidden ES seed route module (exists, not default):
  seed resource catalog + Elasticsearch retrieval behind seedCatalogElasticsearch
```

The hidden ES route module is proven by `BeautySearchElasticsearchRouteModuleSpec`.
Default production `/beauty-search` has not switched from `seedCatalogInMemory`.

Next steps after this checkpoint:
* route parity specs for the explicit ES module;
* default graph switch only after parity;
* full verification after default switch.

The goal is not yet full production search lifecycle. The goal is to demonstrate
the first ES-backed production route over controlled seed data before adding
freshness, repository-backed indexing, Qdrant shadowing, hybrid serving, score
fusion, fallback, or production collection lifecycle.

Current benchmark pieces (exist but not yet unified):

* `BeautySearchEval` / `BeautySearchEvalInventory` — common eval dataset / expectations.
* `BeautySearchElasticsearchIntegrationSpec` — ES Docker/integration eval subsets.
* `QdrantSemanticCandidateEvalSpec` — Qdrant semantic candidate eval.
* `QdrantEmbeddingBenchmark*` — embedding endpoint/model benchmark.
* `HybridDocumentRetrieval` / hybrid response specs — merge/dedup/diagnostics mechanics.

Problem:

These pieces are useful but not yet a single ES-native + Qdrant-native engine comparison layer.

Simulated hybrid:

* Simulated hybrid belongs in benchmark/eval only.
* Simulated hybrid should combine ES EngineEvalResult + Qdrant EngineEvalResult offline.
* It must not imply route wiring, HybridServe, or auto-supplement production responses.

Readiness criteria:

* Eval queries have `queryClass` / `expectedEngineRole`.
* ES executor can produce normalized `EngineEvalResult`.
* Qdrant executor can produce normalized `EngineEvalResult`.
* Simulated hybrid report computes overlap/complement/noise.
* Benchmark report does not change production behavior.

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
- `BeautySearchCatalogBackendModules.seedResourceInMemory[F]` binds ready documents, `InMemorySearchBackend[F]`, and `BeautySearchService.Impl[F]`; it is included in production through `BeautySearchRouteModules.seedCatalogInMemory[F]`.
- This is now production route exposure through `LeaderboardPlugin.modules.api`.
- The seed-resource/catalog/in-memory path is startup snapshot readiness only. It does not solve production freshness, staleness bounds, runtime catalog replacement, or stale-catalog observability.
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

- Search route exposure exists through `LeaderboardPlugin.modules.api` including `BeautySearchRouteModules.seedCatalogInMemory[F]`.
- Default production `BeautySearchService` binding exists through `BeautySearchRouteModules.seedCatalogInMemory[F]`.
- Default production lexical/simple backend binding exists as seed-resource ready catalog documents plus `InMemorySearchBackend[F]`.
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

1. Design observability, freshness/staleness reporting, runtime refresh/replacement, and kill-switch behavior for the seed-resource/in-memory production route.
2. Document and implement the production freshness contract before treating seed-resource startup snapshot readiness as a durable product behavior.
3. Build M-ESQ-EVAL: ES-native + Qdrant-native benchmark comparison. Compare ES-alone, Qdrant-alone, and simulated hybrid (offline only). Decide from metrics.
4. Pause runtime hybrid module expansion until ES/Qdrant eval comparison is improved.
5. Keep Qdrant/hybrid out of this production hardening path unless a separate production design approves it: no Qdrant/hybrid default, no fallback, no reranking, no score fusion, no benchmark-driven routing.
6. Before Elasticsearch production binding, add explicit backend/client/index lifecycle and freshness design.
7. Verify future seed-json plus repository snapshot helpers keep a direct `BeautyQSeedReady` edge when they read shared Postgres state by seed-scoped ids.
8. Reconcile stale docs before relying on them in future implementation passes.
