# BeautyQ architecture review docs

## Start here

This folder is architecture reference. Active current-state, next-step, and Qdrant-hybrid decisions live in [`../BEAUTYQ_CURRENT_STATE_AND_HANDOFF.md`](../BEAUTYQ_CURRENT_STATE_AND_HANDOFF.md). Do not treat generated reports or roadmaps in this folder as more current than the handoff.

Read these docs in order:

1. [README.md](README.md): this index and summary.
2. [01-system-map.md](01-system-map.md): repository layout, runtime wiring, and layer dependencies.
3. [02-domain-model-guide.md](02-domain-model-guide.md): BeautyQ product/domain concepts and code model.
4. [03-repositories-and-persistence.md](03-repositories-and-persistence.md): repositories, Postgres schema, seed loading, and FK startup rules.
5. [04-api-and-http-contracts.md](04-api-and-http-contracts.md): HTTP roles, Tapir endpoint contracts, and route tests.
6. [05-search-and-retrieval-architecture.md](05-search-and-retrieval-architecture.md): search DSL, Elasticsearch, Qdrant, hybrid, eval, and benchmark boundaries.
7. [06-tests-and-contracts.md](06-tests-and-contracts.md): tests that define contracts and resource requirements.
8. [07-current-gaps-and-roadmap.md](07-current-gaps-and-roadmap.md): current gaps, stale docs, and next work.
9. [ARCHITECTURE_DECISIONS_OBSERVED.md](ARCHITECTURE_DECISIONS_OBSERVED.md): decisions visible in code/docs/tests.

[INVENTORY.md](INVENTORY.md) is the exhaustive factual index from pass 1. Use it when you need symbol-level lookup; use the pass-2 docs when you need a navigable architecture explanation.

## Current Architecture In 10 Bullets

1. The active source module is `bifunctor-tagless`, with main code under `bifunctor-tagless/src/main/scala/leaderboard` and tests under `bifunctor-tagless/src/test/scala/leaderboard`.
2. Runtime entrypoints are Distage roles and launcher objects in `bifunctor-tagless/src/main/scala/leaderboard/LeaderboardRole.scala`.
3. DI wiring is centralized in `bifunctor-tagless/src/main/scala/leaderboard/plugins/LeaderboardPlugin.scala`, which binds roles, API adapters, dummy/Postgres repositories, seed loaders, and config.
4. BeautyQ domain data is modeled as `Category`, `Service`, `Master`, `MasterLocation`, `MasterServiceOffer`, `MasterServiceOfferVariant`, service schemas, and typed variant attributes in `leaderboard.model`.
5. `MasterServiceOfferVariant` is the primary purchasable/search-result unit according to `beautyq_search_eval_plan_v1.md` and `VariantSearchDocument` in `leaderboard.search.document`.
6. Repositories have trait interfaces plus `Dummy` and `Postgres` implementations in `leaderboard.repo`; Postgres DDL lives in repository resource constructors, not standalone migrations.
7. Seed data comes from `bifunctor-tagless/src/main/resources/seed/wandsbek_hamburg_beauty_services_seed_ready.json`, decoded by `BeautyQSeedLoader.ResourceLoader`, inserted by `BeautyQSeedInserter.Impl`, and represented in DI by `BeautyQSeedReady`.
8. HTTP APIs are role-backed `*Api` adapters in `leaderboard.api` using pure `*TapirEndpoints` definitions in `leaderboard.http.tapir`.
9. Search code is implemented as models, DSL/spec, document snapshots, parser, in-memory backend, Elasticsearch interpreters, Qdrant/vector components, generic retrieval seams, hybrid experiments, and benchmark/eval code.
10. `POST /beauty-search` is production-exposed through `LeaderboardPlugin` top-level via `modules.apiBase[IO]` plus `BeautySearchRouteModules.apiElasticsearch`. The exposed backend is seed-resource catalog snapshot + `ElasticsearchSearchBackend`. `seedCatalogInMemory` / `InMemorySearchBackend` remain rollback/non-default. Qdrant and hybrid remain not production-wired.

For the full current BeautyQ search state (route behavior, backend roles, milestones, B-lite status, M-ESQ-EVAL, forbidden paths), see `docs/BEAUTYQ_CURRENT_STATE_AND_HANDOFF.md`.

## What Is Definitely Not Production-Ready Yet

- Qdrant/hybrid is not production-wired: docs `search-dsl-qdrant-vector-backend.md` and `search-dsl-hybrid-v1-plan.md` call it non-production/manual/local/experimental, and `LeaderboardPlugin.scala` does not bind Qdrant or hybrid search services.
- `ExperimentalBeautySearchService` is separate from `BeautySearchService` and tests assert that separation in `BeautySearchPureSpec`.
- Benchmark decision policy is decision support, not production automation, per `docs/search-dsl-qdrant-vector-backend.md`, `docs/search-dsl-hybrid-v1-plan.md`, and `QdrantEmbeddingBenchmarkDecisionPolicy` tests.
- Resource-backed Qdrant/Llama tests auto-run when local resources are available and cancel with reason when unavailable; true manual saved-artifact specs (e.g. `QdrantEmbeddingBenchmarkSavedReportComparisonManualSpec`) cancel by default when their saved-report env vars are absent. None of these prove production lifecycle.

## Generated Docs

- [01-system-map.md](01-system-map.md)
- [02-domain-model-guide.md](02-domain-model-guide.md)
- [03-repositories-and-persistence.md](03-repositories-and-persistence.md)
- [04-api-and-http-contracts.md](04-api-and-http-contracts.md)
- [05-search-and-retrieval-architecture.md](05-search-and-retrieval-architecture.md)
- [06-tests-and-contracts.md](06-tests-and-contracts.md)
- [07-current-gaps-and-roadmap.md](07-current-gaps-and-roadmap.md)
- [ARCHITECTURE_DECISIONS_OBSERVED.md](ARCHITECTURE_DECISIONS_OBSERVED.md)
- [INVENTORY.md](INVENTORY.md)
- [BEAUTYQ_ES_QDRANT_HYBRID_RETRIEVAL_ROADMAP.md](BEAUTYQ_ES_QDRANT_HYBRID_RETRIEVAL_ROADMAP.md)
- [M8_M9_TELEMETRY_AND_OFFLINE_EVAL_PLAN.md](M8_M9_TELEMETRY_AND_OFFLINE_EVAL_PLAN.md)
- [QDRANT_PRODUCTION_ACTIVATION_DECISION_CRITERIA.md](QDRANT_PRODUCTION_ACTIVATION_DECISION_CRITERIA.md)
