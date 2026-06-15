# BeautyQ Current State and Handoff

Canonical handoff for new chats. Read this first, then see linked docs for deeper detail.

## 1. Current production `/beauty-search`

* `POST /beauty-search` is production-exposed by `LeaderboardPlugin` through `modules.apiBase[IO]` plus `BeautySearchRouteModules.apiElasticsearch`.
* `modules.apiBase[IO]` provides base/core non-Beauty APIs, `HttpServer`, `Ranks`, and old disabled Beauty inclusion helper bindings (not the active route gate).
* `BeautySearchRouteModules.apiElasticsearch` provides the ES-backed Beauty route.
* `modules.api[F]` is retained as rollback/legacy composition (`apiBase[F]` + `seedCatalogInMemory[F]`).
* Current exposed stack:

  ```text
  LeaderboardPlugin
  → modules.apiBase[IO] for base/core APIs
  → BeautySearchRouteModules.apiElasticsearch
  → BeautySearchRouteModules.seedCatalogElasticsearchPortConfigured
  → ElasticsearchClientModules.portConfigured
  → BeautySearchRouteModules.seedCatalogElasticsearch
  → BeautySearchCatalogBackendModules.seedResourceElasticsearch
  → BeautyQSeedLoader.ResourceLoader
  → BeautySearchReadyCatalogDocuments
  → ElasticsearchSeedIndexInitializer
  → ElasticsearchSearchBackend
  → BeautySearchService.Impl
  → BeautySearchApi
  ```

* Backend is seed-resource catalog snapshot + `ElasticsearchSearchBackend`.
* It is lexical/simple/catalog-first.
* `ElasticsearchPortCfg` is loaded from config section `"elasticsearch"`.
* It is not Qdrant, not hybrid.
* It is not fresh/repository-backed production catalog lifecycle.
* Route exposure exists; production-grade search lifecycle remains incomplete.
* `seedCatalogInMemory` remains available as rollback/non-default.

## 2. Current route behavior characterization

Documented as characterized, not as desired final contract:

* Positive limit returns `200 OK` capped by requested limit.
* Zero/negative limit returns `200 OK` with empty variant carousel.
* Huge limit is capped by `BeautySearchSpecV1.spec.carouselSpec.variantSize`.
* Malformed JSON / empty body / wrong limit type / missing required fields currently return `500` with empty body.
* Coordinates are not range-validated.
* Query text is not length-validated.
* `BeautySearchReadyCatalogDocuments` rejects blank source / empty document list.

## 3. Search backend roles

* `ElasticsearchSearchBackend` is the current production lexical retrieval backend (seed-backed).
* `InMemorySearchBackend` is a rollback/regression/pure backend, not current production default.
* It is not an in-memory Elasticsearch.
* It must not be treated as ES scoring/order/analyzer oracle.
* Elasticsearch is the intended lexical retrieval baseline for text search, structured filters, facets, exact/range/geo constraints.
* Qdrant is semantic recall/complement candidate only.
* Product response assembly is projection over engine-native results.

## 4. Reached milestones

* **A**: non-production real-resource Qdrant/hybrid manual runner — reached.
* **A→B**: production-hybrid control-plane v0 — reached.
* **B1/B2**: production-hidden activation/handle + targeted control-plane module proof — reached.
* First B-lite pure `EngineEval` model — implemented.

## 5. Current priority: ES seed route stabilization

* Default production `/beauty-search` is now ES-backed over the seed catalog.
* ES seed-route checkpoint is reached. Plain `sbt test` is the canonical full verification command.
* Latest user-verified plain `sbt test` (Jun 13, 2026, 12:19:23 PM): 963 succeeded, 0 failed, 1 canceled. Resource-backed Qdrant/Llama specs auto-run when local resources are available and cancel with reason when unavailable. The remaining expected canceled spec is `QdrantEmbeddingBenchmarkSavedReportComparisonManualSpec`, which cancels by default when its saved-report env vars are absent. `EngineEvalSavedReportAssemblyManualSpec` and `EngineEvalSavedReportComparisonManualSpec` run deterministic default fixture mode by default (no cancel); real-artifacts mode is available behind existing env gates.
* Business demo ready: runbook, query inventory, and smoke spec all in place.
* B-lite = ES-native + Qdrant-native benchmark/eval comparison continues as eval-only work.
* Runtime hybrid expansion is paused.
* Resource-backed hidden Qdrant/hybrid module expansion is paused.
* ES and Qdrant may advance together only in eval/benchmark.
* Production serving remains:

  ```text
  current ES seed route (default)
  → Qdrant shadow only if eval proves complement
  → controlled hybrid only after readiness/kill-switch/policy
  ```

* `seedCatalogInMemory` remains available as rollback/non-default.
* Simulated hybrid is offline benchmark/eval only.

## 6. M-ESQ-EVAL

**M-ESQ-EVAL** = measured Elasticsearch-native + Qdrant-native evaluation comparison.

### Status

* Started by pure `EngineEval` comparison model.
* Not complete.

### Implemented

* `EngineEvalEngine` (Elasticsearch, Qdrant, SimulatedHybrid)
* `EngineEvalQueryClass` (taxonomy metadata for future eval inventory classification)
* `EngineExpectedRole` (drives first-pass metrics)
* `EngineEvalResult`
* `EngineEvalComparisonMetrics`
* `EngineEvalComparisonMetrics.from(...)`

### Semantics

* Metrics count distinct variant ids by default.
* Duplicate ids must not inflate recall/complement/overlap/gain/noise metrics.
* Duplicate ids should be a separate validation failure or separate duplicate-count metric if needed later.
* `qdrantNoiseCount` counts distinct Qdrant ids only when expected role is `QdrantShouldStaySilent`; otherwise it is 0.

### Query-class classification contract

`EngineEvalQueryClass.fromQueryTypes` maps BeautySearch eval `queryTypes` strings to `EngineEvalQueryClass` values for offline/eval metadata only. `EngineEvalQueryClassSpec` locks the current inventory coverage.

Contract facts:

* Output class order is stable: `ExactService`, `Category`, `StructuredFilter`, `PriceDuration`, `GeoLocal`, `SemanticVague`, `BroadIntent`, `HardNegative`, `Mixed`.
* `english` and `german` are language modifier tags and are ignored.
* Unknown non-language query type tags fail with `QueryFailure.operation`; they are not silently ignored.
* Empty `queryTypes` returns no classes for existing fixture compatibility.
* Current observed query type tags are `ambiguous`, `attribute`, `attribute_heavy`, `broad`, `conversational`, `direct`, `english`, `german`, `hard_negative`, `home_visit`, `location`, `mixed_language`, `multi_intent`, `negative_attribute`, `numeric`, `price`, `synonym`, `technical_token`, and `typo`.
* Real `SemanticBroadSmoke` example: `q_broad_005` classifies to `PriceDuration` then `BroadIntent`.

Boundary:

* This is offline/eval-only metadata classification.
* It is not production readiness.
* It is not routing approval.
* Saved aggregate JSON schema remains unchanged.
* `EngineEvalReportJson` remains unchanged.

### EngineEval query-class breakdown sidecar contract

`EngineEvalQueryClassBreakdown.from` aggregates `EngineEvalQueryReport` metrics by `EngineEvalQueryClass` for offline/eval use only. It requires an explicit `queryId -> List[EngineEvalQueryClass]` sidecar map and does not read `queryTypes` or `queryClasses` from saved `EngineEvalQueryReport` data.

Contract facts:

* A query may contribute to multiple class buckets.
* Repeated classes for a single query are deduplicated.
* Empty class lists contribute no bucket.
* Missing sidecar entries fail with `QueryFailure.operation` naming the missing `queryId`.
* Output buckets follow `EngineEvalQueryClass.stableOrder`.
* Saved aggregate JSON schema remains unchanged.
* `EngineEvalAggregateReport` remains unchanged.
* `EngineEvalReportJson` remains unchanged.
* The helper does not wire class breakdowns into saved-report comparison yet.

Boundary:

* This is offline/eval-only helper behavior.
* It is not production readiness.
* It is not routing approval.
* It does not imply Qdrant/hybrid production readiness, route switch, fallback, score fusion, reranking, `HybridServe`, or Qdrant auto-supplement.

### EngineEval class-delta comparison contract

`EngineEvalSavedReportComparison.compareReports(left, right)` remains compatible with the existing aggregate/role/query comparison behavior and no class sidecars. `EngineEvalSavedReportComparison.compareReportsWithQueryClasses(...)` adds explicit left/right `queryId -> List[EngineEvalQueryClass]` sidecar maps for offline/eval class-delta reporting.

Contract facts:

* Sidecars are validated through `EngineEvalQueryClassBreakdown`.
* Missing sidecar query ids fail with `QueryFailure.operation` naming the missing `queryId`.
* A missing class bucket on one side is compared against zero metrics.
* `classDeltas:` is omitted when class comparisons are empty or when all class-level deltas are zero.
* Existing aggregate deltas, `roleDeltas:`, and `queryDeltas:` behavior is preserved.
* Saved aggregate JSON schema remains unchanged.
* `EngineEvalAggregateReport` remains unchanged.
* `EngineEvalReportJson` remains unchanged.
* Env/manual real-artifact sidecar wiring is not added yet.

Boundary:

* This is offline/eval-only reporting behavior.
* It is not production readiness.
* It is not routing approval.
* It does not imply Qdrant/hybrid production readiness, route switch, fallback, score fusion, reranking, `HybridServe`, or Qdrant auto-supplement.

### Current M-ESQ-EVAL status / remaining work

**What exists now**

* Pure/report/assembly layer: `EngineEvalEngine`, `EngineEvalQueryClass`, `EngineExpectedRole`, `EngineEvalResult`, `EngineEvalComparisonMetrics`, normalizers, simulated hybrid, query/aggregate report, JSON/formatter/comparison, assembly from ES reports + Qdrant benchmark outputs, selected Qdrant candidate helper, ES integration proof.
* ES artifact emission for `SemanticBroadSmoke` (query ids `q_broad_001`–`q_broad_006`), gated by `ENGINE_EVAL_PRINT_ES_ARTIFACTS`. Emits `BeautySearchEvalReport` JSON and expected roles JSON between `BEGIN_*` / `END_*` markers.
* ES markers pair with Qdrant `BEGIN_QDRANT_EMBEDDING_BENCHMARK_RUN_OUTPUT_JSON` / `END_QDRANT_EMBEDDING_BENCHMARK_RUN_OUTPUT_JSON` markers for `EngineEvalSavedReportAssemblyManualSpec` real-artifact mode (4 env vars).
* Role breakdowns are derived from query reports, not persisted in saved aggregate JSON. Saved aggregate JSON schema remains unchanged. Text aggregate reports may include `roleAggregates`. Saved-report comparison output may include `roleDeltas` when role-level deltas are non-zero. Query-level deltas are derived from `EngineEvalAggregateReport.queryReports`; saved-report comparison output may include `queryDeltas` when query-level deltas are non-zero and omits `queryDeltas` when all query-level deltas are zero. Query ids are compared in stable order: ids from the left report in left order, then ids only present in the right report in right order. Missing queries are compared against zero metrics. Query-level deltas include `expectedVariantCountDelta`, `esRecallDelta`, `qdrantRecallDelta`, `qdrantComplementDelta`, `qdrantNoiseDelta`, `overlapDelta`, and `simulatedHybridGainDelta`. Replayed saved comparisons confirmed: `benchmark-small` → `benchmark-large` includes `queryDeltas` and preserves `roleDeltas`; `benchmark-single` → `benchmark-small` omits both `queryDeltas` and `roleDeltas` because that comparison is zero-delta at query and role levels. This remains offline/eval-only and does not imply production Qdrant/hybrid readiness, routing, fallback, score fusion, reranking, HybridServe, or Qdrant auto-supplement.

**What operators can now collect**

* Concrete ES eval reports + expected roles from the ES integration spec.
* Qdrant benchmark run-output JSON via the benchmark executor.
* EngineEval aggregate report via saved-report assembly spec.
* Optional saved comparison between two aggregate reports.
* Full operational workflow (marker extraction, artifact naming/manifest, sanity checklist) is in `docs/codebase-review/06-tests-and-contracts.md` under "Operational workflow (M-ESQ-EVAL evidence collection)". The six-query role map and extraction helpers are kept there to avoid handoff bloat.

**What remains explicitly non-production / not implied**

* This is offline/eval-only. The production `/beauty-search` route remains ES seed route.
* No route switch, fallback, score fusion, reranking, HybridServe, or Qdrant auto-supplement from benchmark results.
* Benchmark output is decision support, not production automation.
* Qdrant/hybrid are non-production/manual/local/test boundaries, not production wiring.

### First M-ESQ-EVAL evidence summary (SemanticBroadSmoke)

First real offline evidence for the `SemanticBroadSmoke` eval subset (query ids `q_broad_001`–`q_broad_006`). All numbers below are offline/eval-only, not production readiness or routing approval.

**benchmark-single:** qdrantRecall=6, qdrantComplement=4, qdrantNoise=0, overlap=2, simulatedHybridGain=4.

**benchmark-small:** qdrantRecall=6, qdrantComplement=4, qdrantNoise=0, overlap=2, simulatedHybridGain=4.

**benchmark-large:** qdrantRecall=22, qdrantComplement=7, qdrantNoise=0, overlap=15, simulatedHybridGain=7.

**Deltas:** `benchmark-single` → `benchmark-small` all deltas zero. `benchmark-small` → `benchmark-large`: qdrantRecall +16, qdrantComplement +3, qdrantNoise +0, overlap +13, simulatedHybridGain +3.

**Offline interpretation:** `benchmark-large` is strongest in this SemanticBroadSmoke evidence slice.

**Boundaries:** This is offline/eval-only evidence. It is not production readiness. It is not routing approval. It does not imply Qdrant/hybrid production readiness, route switch, fallback, score fusion, reranking, `HybridServe`, or Qdrant auto-supplement. Full metric tables and delta breakdowns are in `docs/codebase-review/06-tests-and-contracts.md` under "First M-ESQ-EVAL evidence summary (SemanticBroadSmoke)".

## 6.5. Product search north star

Final direction:
Build useful free-string BeautyQ search that turns user text into domain-aware
product results by combining:

* DSL/spec-owned domain semantics for fast adaptation to new domains;
* Elasticsearch strengths for lexical precision, filters, facets, exact/range/geo
  constraints, analyzers, aggregations, and deterministic baseline retrieval;
* Qdrant strengths for semantic recall/complement on broad, conversational, and
  vague queries;
* explicit eval/benchmark reporting before any production hybrid or routing
  decision.

## 6.6. ES seed-route checkpoint status (reached)

Reached:
* `LeaderboardPlugin` top-level includes `modules.apiBase[IO]` + `BeautySearchRouteModules.apiElasticsearch` (ES-backed Beauty route, default).
* `BeautySearchRouteModules.apiElasticsearch` includes `seedCatalogElasticsearchPortConfigured` → `seedCatalogElasticsearch`.
* `BeautySearchCatalogBackendModules.seedResourceElasticsearch` is the catalog backend module reached via `seedCatalogElasticsearch`.
* `ElasticsearchClientModules.portConfigured` binds `ElasticsearchJsonClient` from `ElasticsearchPortCfg`.
* `ElasticsearchSeedIndexInitializer` uses bodyless `POST /<index>/_refresh` via `ElasticsearchJsonClient.post(path)`, because real Elasticsearch rejects `_refresh` with a JSON body.
* default production `/beauty-search` switched from `seedCatalogInMemory` to ES-backed seed route.
* explicit ES route module proof for `POST /beauty-search` with scripted `ElasticsearchJsonClient`.
* demo query inventory exists in `docs/demo/beauty-search-es-seed-demo-queries.md`.
* `BeautySearchElasticsearchBusinessDemoSpec` covers the selected 12 demo queries through the ES-backed default route.
* previous full verification after ES seed-route/demo-doc updates (historical): 956 passed, 0 failed, 1 canceled.

Business-demo ready:

* Default route is ES-backed; demo runbook at `docs/demo/beauty-search-es-seed-demo.md`.
* Demo query inventory at `docs/demo/beauty-search-es-seed-demo-queries.md`.
* Business demo smoke spec: `BeautySearchElasticsearchBusinessDemoSpec` — 12 selected demo queries passed focused verification.

Important observed route behavior:
When Elasticsearch returns zero hits, the ES-backed route can still return
non-empty facets and inferred filters because those are derived from catalog,
spec, and parsed intent metadata rather than only from hit lists.

Full lifecycle gaps remain: repository freshness, live/repository indexing,
aliases/blue-green, Qdrant shadowing, hybrid serving, fallback, score fusion,
reranking, and production lifecycle management.

## 6.7. Forbidden paths

* No production hybrid.
* No route switch from benchmark alone.
* No Qdrant auto-supplement.
* No HybridServe from benchmark alone.
* No Qdrant-as-default.
* No startup indexing.
* No fallback.
* No score fusion/reranking.
* No production collection lifecycle manager / alias / blue-green.
* No production kill-switch integration yet.
* No freshness/reindex production policy yet.
* No public response schema changes unless explicitly requested.
* No forcing ES/Qdrant to mimic `InMemorySearchBackend` (rollback backend only).

## 8. Verification and testing protocol

* Use Constructive test taxonomy.
* Pure model/metric logic: `Contractual + Blackbox + Atomic`.
* In-process service/module seams: `Contractual + Blackbox + Group`.
* Real ES/Qdrant/Llama/Docker/HTTP: `Communication`, resource-backed auto-gated/cancelable (Qdrant uses distage-managed `QdrantPortCfg`; Llama defaults to local endpoints with optional env overrides; unavailable resources cancel with reason).
* Full `sbt test` is user/coordinator responsibility unless explicitly delegated.

## 9. Where to read next

* `AGENTS.md` — stable repo guardrails
* `docs/codebase-review/README.md` — architecture entrypoint
* `docs/codebase-review/05-search-and-retrieval-architecture.md` — search DSL, ES, Qdrant, hybrid, eval
* `docs/codebase-review/07-current-gaps-and-roadmap.md` — current gaps and roadmap
* `docs/search-dsl-hybrid-v1-plan.md` — hybrid V1 plan and B-lite strategy
* `docs/beautyq-search-dsl-v1.md` — search DSL V1 and eval coverage
* `docs/local/COORDINATOR_PROMPTING_REMINDER.md` — coordinator prompt-writing guidance
* `docs/LOCAL_LLM_MODEL_SELECTION_POLICY.md` — model selection policy

Coordinator workflow rules, including source-truth gating and documentation ownership, live in `docs/local/COORDINATOR_PROMPTING_REMINDER.md`.
