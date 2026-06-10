# BeautyQ Current State and Handoff

Canonical handoff for new chats. Read this first, then see linked docs for deeper detail.

## 1. Current production `/beauty-search`

* `POST /beauty-search` is production-exposed through `LeaderboardPlugin.modules.api`.
* Code source truth: `include(BeautySearchRouteModules.seedCatalogInMemory[F])`.
* Current exposed stack:

  ```text
  BeautySearchRouteModules.seedCatalogInMemory
  → BeautySearchCatalogBackendModules.seedResourceInMemory
  → BeautyQSeedLoader.ResourceLoader
  → BeautySearchCatalogSnapshot
  → BeautySearchReadyCatalogDocuments
  → InMemorySearchBackend
  → BeautySearchService.Impl
  → BeautySearchApi
  ```

* Backend is seed-resource catalog snapshot + `InMemorySearchBackend`.
* It is lexical/simple/catalog-first.
* It is not Elasticsearch, not Qdrant, not hybrid.
* It is not fresh/repository-backed production catalog lifecycle.
* Route exposure exists; production-grade search lifecycle remains incomplete.

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

* `InMemorySearchBackend` is a seed-backed MVP/product-contract stabilizer.
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

## 5. Current priority: B-lite

* B-lite = ES-native + Qdrant-native benchmark/eval comparison.
* Runtime hybrid expansion is paused after B2.
* Resource-backed hidden Qdrant/hybrid module expansion is paused.
* ES and Qdrant may advance together only in eval/benchmark.
* Production serving remains sequential:

  ```text
  current seed/in-memory route
  → future ES lexical retrieval baseline
  → Qdrant shadow only if eval proves complement
  → controlled hybrid only after readiness/kill-switch/policy
  ```

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

### Next work

* Normalize ES eval/executor output to `EngineEvalResult`.
* Normalize Qdrant eval/executor output to `EngineEvalResult`.
* Compute ES-alone / Qdrant-alone / offline simulated-hybrid reports.
* Keep it offline/eval-only.

## 7. Forbidden paths

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
* No forcing ES/Qdrant to mimic `InMemorySearchBackend`.

## 8. Verification and testing protocol

* Use Constructive test taxonomy.
* Pure model/metric logic: `Contractual + Blackbox + Atomic`.
* In-process service/module seams: `Contractual + Blackbox + Group`.
* Real ES/Qdrant/Llama/Docker/HTTP: `Communication`, env-gated/cancelable.
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
