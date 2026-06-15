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
* Latest user-verified plain `sbt test` (Jun 13, 2026): 963 succeeded, 0 failed, 1 canceled.
* Business demo ready: runbook, query inventory, and smoke spec all in place.
* Current checkpoint is expanded M3 / B-lite evidence: ES-native + Qdrant-native benchmark/eval comparison remains offline/eval-only.
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

* Current checkpoint is expanded M3 / B-lite evidence, not a completed milestone.
* Validated class-sidecar replay evidence exists for saved-report comparison.
* Detailed contracts and evidence stay in [docs/codebase-review/06-tests-and-contracts.md](codebase-review/06-tests-and-contracts.md).
* Roadmap and milestone positioning stay in [docs/codebase-review/07-current-gaps-and-roadmap.md](codebase-review/07-current-gaps-and-roadmap.md).

### Compact checkpoint summary

* Query inventory / expected-role refinement is part of the active M3 evidence lane.
* `roleDeltas:`, `queryDeltas:`, query-class classification, query-class sidecars, and `classDeltas:` are now part of the current interpretability checkpoint.
* Saved-report replay for `benchmark-small -> benchmark-large` is validated at the checkpoint level.
* Saved aggregate JSON schema remains unchanged.
* `EngineEvalAggregateReport` remains unchanged.
* `EngineEvalReportJson` remains unchanged.
* Detailed contracts, evidence shape, sidecar behavior, replay evidence, and operator workflow remain in [docs/codebase-review/06-tests-and-contracts.md](codebase-review/06-tests-and-contracts.md).

### Boundaries

* This is offline/eval-only. The production `/beauty-search` route remains ES seed route.
* No route switch, fallback, score fusion, reranking, HybridServe, or Qdrant auto-supplement from benchmark results.
* Benchmark output is decision support, not production automation.
* Qdrant/hybrid are non-production/manual/local/test boundaries, not production wiring.
* This is not production readiness and not routing approval.

### First M-ESQ-EVAL evidence summary (SemanticBroadSmoke)

Compact summary:

* First real offline evidence exists for `SemanticBroadSmoke` (`q_broad_001`–`q_broad_006`).
* `benchmark-single` and `benchmark-small` are zero-delta against each other on this slice.
* `benchmark-large` is the strongest result in this first evidence slice.
* Full metric tables, exact deltas, and detailed interpretation live in [docs/codebase-review/06-tests-and-contracts.md](codebase-review/06-tests-and-contracts.md) under "First M-ESQ-EVAL evidence summary (SemanticBroadSmoke)".

**Boundaries:** This is offline/eval-only evidence. It is not production readiness. It is not routing approval. It does not imply Qdrant/hybrid production readiness, route switch, fallback, score fusion, reranking, `HybridServe`, or Qdrant auto-supplement.

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
