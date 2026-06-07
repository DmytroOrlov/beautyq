# Search And Retrieval Architecture

## A. Product-Level Search Goal

Implemented/current models:

- User input: `UserSearchInput(query, userLat, userLon, limit)` in `search/BeautySearchModels.scala`.
- Location/geo: `userLat`/`userLon` in input and `SearchGeoPoint`, `lat`, `lon`, `distanceKm` fields in search projections.
- Variant carousel: `variantCarousel: List[VariantSearchResult]` in `BeautySearchResponse`.
- Provider carousel: `providerCarousel: List[ProviderSearchResult]`, using `masterId`, `masterLocationId`, address, matching variant ids, and distance.
- Service intent carousel: `serviceIntentCarousel: List[ServiceIntentSearchResult]`.
- Primary buyable unit: `MasterServiceOfferVariant`, explicitly named in `beautyq_search_eval_plan_v1.md` and flattened into `VariantSearchDocument`.

Documented intent:

- `beautyq_search_eval_plan_v1.md` describes 63 grounded queries over the Wandsbek/Hamburg seed dataset and expects exactly three carousels.
- `docs/beautyq-search-dsl-v1.md` says search semantics should live in immutable DSL/spec values and be interpreted into in-memory/Elasticsearch paths.

## B. Current Implemented Search Model

Implemented/current:

- `UserSearchInput`: raw query and optional user location.
- `ParsedSearchIntent`: normalized tokens, explicit constraints, soft boosts, and remaining text.
- `BeautySearchResponse`: variant/provider/service carousels, facets, and inferred filters.
- `BeautySearchBackend[F]`: backend interface with `search(input, intent)`.
- `BeautySearchService[F]`: service interface with `search(input)`.
- `BeautySearchService.Impl`: parses input with `BeautySearchIntentParser` and delegates to a `BeautySearchBackend[F]`.
- `BeautySearchTapirEndpoints`: pure unwired contract skeleton for `POST /beauty-search`.
- `BeautySearchApi`: thin unwired API adapter from `BeautySearchTapirEndpoints` to `BeautySearchService[F]`.
- `BeautySearchApiHttpContractSuite`: route-level adapter contract using a test-local fake `BeautySearchService`.
- `BeautySearchServiceBindingSpec`: focused test-only Distage module proof that assembles `BeautySearchService.Impl[IO]` from `BeautySearchSpecV1.spec`, `BeautySearchIntentParser`, and a fake in-memory `BeautySearchBackend[IO]`.
- `BeautySearchReadyCatalogDocuments`: src/main ready-catalog document helper with an explicit source label and validated non-empty document list. It has no timestamp, freshness, refresh, or production replacement semantics.
- `BeautySearchCatalogBackendFactory.fromSeedLoader`: typed helper that converts `BeautyQSeedLoader` data to `BeautySearchCatalogSnapshot`, flattens it through `VariantSearchDocumentBuilder`, and validates `BeautySearchReadyCatalogDocuments`.
- `BeautySearchCatalogBackendModules.seedResourceInMemory[F]`: opt-in src/main module that binds `BeautySearchSpecV1.spec`, `BeautySearchIntentParser`, `BeautySearchReadyCatalogDocuments`, `InMemorySearchBackend[F]` as `BeautySearchBackend[F]`, and `BeautySearchService.Impl[F]` as `BeautySearchService[F]` when explicitly included with a `BeautyQSeedLoader`.
- `BeautySearchReadyCatalogDocumentsSpec`: focused spec that characterizes `BeautySearchReadyCatalogDocuments` validation behavior: non-empty source + non-empty documents yields `Right` preserving source/documents; empty source yields `Left QueryFailure`; blank source yields `Left QueryFailure`; non-empty source + `Nil` documents yields `Left QueryFailure`. This is startup snapshot readiness validation only, not freshness/staleness/refresh policy.
- `BeautySearchCatalogBackendReadinessSpec`: focused proof that seed resource data is converted to a `BeautySearchCatalogSnapshot`, flattened into `VariantSearchDocument` rows, wrapped in the src/main ready-document handle, and then used to construct `InMemorySearchBackend` and `BeautySearchService.Impl[IO]`.
- `BeautySearchCatalogBackendModuleSpec`: focused proof that the opt-in catalog/in-memory backend module materializes ready documents, backend, and service and answers a simple search without API, HTTP, `LeaderboardPlugin`, Elasticsearch, Qdrant, hybrid, Docker, repository snapshots, or startup indexing.
- `BeautySearchAppGraphBoundarySpec`: focused test-only Distage app-graph boundary proof that assembles `BeautySearchTapirEndpoints`, `TapirHttpSupport[IO]`, `BeautySearchApi[IO]`, `BeautySearchService.Impl[IO]`, `BeautySearchIntentParser`, `BeautySearchSpecV1.spec`, and a fake `BeautySearchBackend[IO]` only through a spec-local module/composition.
- `BeautySearchProductionInclusionActivation`: disabled-by-default production inclusion activation boundary. This is a staging/helper boundary; it is NOT the active production gate for `/beauty-search`.
- `BeautySearchProductionInclusionHandle`: optional API handle that does not implement `HttpApi` and does not expose routes by itself.
- `BeautySearchProductionInclusionBoundarySpec`: focused proof that Disabled avoids evaluating/constructing the API/service/backend graph and Enabled can explicitly assemble the stack in a test-local module.
- `BeautySearchProductionIncludeModuleSpec`: focused test-only proof that an include module can remain disabled by default at an API aggregation boundary.
- The include-module proof uses a src/main `BeautySearchProductionIncludedApis[F](apis: List[HttpApi[F]])` helper that converts an enabled `BeautySearchProductionInclusionHandle` to a local `HttpApi` list; Disabled contributes no Beauty search API, and Enabled can explicitly contribute one `BeautySearchApi`. This helper does not implement `HttpApi`, does not expose routes by itself, and `BeautySearchApi` was not added to production `many[HttpApi[F]]`.
- The old disabled inclusion boundary is a staging/helper boundary. It is not the active production gate for `/beauty-search`. The route is exposed directly by `LeaderboardPlugin.modules.api` including `BeautySearchRouteModules.seedCatalogInMemory[F]`, not by toggling the old inclusion handle. A real kill switch / enable-disable route gate remains future hardening.
- `BeautySearchPluginModules.api[F]`: opt-in src/main helper that binds `BeautySearchTapirEndpoints`, `BeautySearchApi[F]`, and contributes `BeautySearchApi[F]` to a real `many[HttpApi[F]].weak[...]` set only when explicitly included.
- `BeautySearchOptInHttpApiModuleSpec`: focused proof that the opt-in module contributes exactly one `BeautySearchApi[IO]` through the real `Set[HttpApi[IO]]` aggregation shape consumed by `HttpServer.Impl`, with the repo's role-style concrete API retention edge and a fake `BeautySearchService[IO]` that is not called during graph construction.
- `BeautySearchRouteModules.seedCatalogInMemory[F]`: explicit opt-in end-to-end route module that composes `BeautySearchCatalogBackendModules.seedResourceInMemory[F]` with `BeautySearchPluginModules.api[F]` and supplies the seed resource loader.
- `BeautySearchOptInRouteModuleSpec`: focused proof that the composed opt-in route module contributes exactly one `BeautySearchApi[IO]` through the real `Set[HttpApi[IO]]` shape and serves one successful non-empty `POST /beauty-search` response from the seed-resource catalog/in-memory backend without `HttpServer`, `LeaderboardPlugin`, Elasticsearch, Qdrant, hybrid, Docker, repository snapshots, or startup indexing.
- `LeaderboardPlugin.modules.api` now includes `BeautySearchRouteModules.seedCatalogInMemory[F]`, so `POST /beauty-search` is production-included in the default API graph.
- `BeautySearchProductionRouteExposureSpec`: focused proof that the default plugin API graph contributes `BeautySearchApi[IO]` through the real `Set[HttpApi[IO]]` shape and serves one successful non-empty `POST /beauty-search` response without `HttpServer`, Elasticsearch, Qdrant, hybrid, Docker, repository snapshots, or startup indexing.
- `BeautySearchProductionRouteLimitSpec`: characterizes `POST /beauty-search` limit parameter behavior through `LeaderboardPlugin.modules.api` with seed-resource catalog snapshot + `InMemorySearchBackend`: limit 3 returns non-empty `variantCarousel` with size <= 3; limit 0 and limit -5 return `200 OK` with empty `variantCarousel`; limit 100000 returns `variantCarousel` size capped by `BeautySearchSpecV1.spec.carouselSpec.variantSize`. This is current production route behavior, not a full validation or error policy. Zero and negative limits return `200 OK` with empty `variantCarousel`. Huge limits are capped by `BeautySearchSpecV1.spec.carouselSpec.variantSize`.
- `BeautySearchProductionRouteErrorSpec`: characterizes invalid `POST /beauty-search` request behavior through `LeaderboardPlugin.modules.api` with seed-resource catalog snapshot + `InMemorySearchBackend`: malformed JSON body, empty body, wrong `limit` type (string instead of integer), and missing required field (`query`) all return `500 InternalServerError` with empty body. This is current behavior, not the desired final validation contract. Typed `4xx` errors, structured error bodies, request validation, query length limits, lat/lon validation, freshness/staleness, observability, and kill-switch remain future hardening.

Production-wired/current:

- `LeaderboardPlugin.modules.api` includes `BeautySearchRouteModules.seedCatalogInMemory[F]`.
- The default API graph now binds `BeautySearchService.Impl[F]`, `BeautySearchBackend[F]`, `BeautySearchApi[F]`, and `BeautySearchTapirEndpoints` through the included route/backend/API modules.
- The backend source is seed-resource catalog snapshot readiness: `BeautyQSeedLoader.ResourceLoader` to `BeautySearchCatalogSnapshot` to `VariantSearchDocumentBuilder` to `BeautySearchReadyCatalogDocuments` to `InMemorySearchBackend[F]`.
- This production exposure is lexical/simple/catalog-first.
- It is not Elasticsearch, not Qdrant, and not hybrid.
- It does not add repository snapshot wiring, startup indexing, fallback, reranking, score fusion, or benchmark-driven routing.
- Production freshness/refresh/staleness, runtime refresh/replacement, catalog source-of-truth, stale-catalog observability, and kill-switch behavior remain unresolved.
- There is no runtime refresh or replacement policy yet.
- Benchmark decisions do not affect routing.
- `LeaderboardPlugin.modules.api` is the real production API aggregation point: it binds Tapir endpoint singletons, binds API adapters, contributes them to `many[HttpApi[F]]`, and `HttpServer.Impl` serves the combined API set.
- Because the included route module contributes `BeautySearchApi[F]` to that set, `POST /beauty-search` is now production-exposed through the default API graph.

Design boundary:

- The implemented model/service/contract/adapter boundary is now production-exposed only through the seed-resource/in-memory include.
- Do not infer Elasticsearch, Qdrant, hybrid, fallback, reranking, score fusion, or benchmark-driven routing from production route availability.
- The current proof set covers a pure route contract, a thin unwired API adapter, a fake-service route contract suite, a fake-backend `BeautySearchService.Impl` binding proof, a src/main ready-catalog document helper, an opt-in catalog/in-memory backend/service module proof, an opt-in HttpApi module proof, an explicit opt-in end-to-end route module proof, a test-only complete app-graph boundary proof, a disabled-by-default production inclusion activation/handle proof, and a test-only disabled-by-default include-module aggregation proof.
- The production hardening path should address observability, freshness/staleness, runtime refresh/replacement, and kill-switch design before any Qdrant/hybrid work.

Test-only/fake-only:

- `BeautySearchPureSpec.scala` constructs `BeautySearchService.Impl` directly with fake or in-memory backends.
- `FakeBeautySearchBackend`, `FailingBeautySearchBackend`, and `ThrowingBeautySearchBackend` are test-local helpers inside `BeautySearchPureSpec.scala`.
- `InMemorySearchBackend` is the pure regression backend described in `docs/beautyq-search-dsl-v1.md` and used by pure tests.
- `BeautySearchApiHttpContractSuite.scala` constructs a test-local fake `BeautySearchService` through `BeautySearchApi`; it does not use Qdrant, hybrid, Elasticsearch, repository snapshots, file IO, Docker, or production DI.
- `BeautySearchServiceBindingSpec.scala` binds a test-local fake `BeautySearchBackend[IO]` and `BeautySearchService.Impl[IO]` through Distage, calls `search(UserSearchInput(...))`, verifies the fake backend receives the parser-produced `ParsedSearchIntent`, verifies response pass-through, and verifies `QueryFailure` pass-through. It does not use `BeautySearchApi`, `LeaderboardPlugin`, real HTTP routes, Qdrant, hybrid search, Elasticsearch, repository snapshots, file IO, Docker, or production DI modules.
- `BeautySearchReadyCatalogDocumentsSpec.scala` characterizes `BeautySearchReadyCatalogDocuments` validation behavior: non-empty source + non-empty documents -> `Right` preserving source/documents; empty source -> `Left QueryFailure`; blank source -> `Left QueryFailure`; non-empty source + `Nil` documents -> `Left QueryFailure`. This is startup snapshot readiness validation only, not freshness/staleness/refresh policy. It does not use `BeautySearchApi`, `LeaderboardPlugin`, real HTTP routes, Qdrant, hybrid search, Elasticsearch, repository snapshots, Docker, startup indexing, or default production DI modules.
- `BeautySearchCatalogBackendReadinessSpec.scala` uses the src/main `BeautySearchReadyCatalogDocuments(source, documents)` handle with source `seed-resource-loader`; it rejects empty document readiness before backend construction, then constructs `InMemorySearchBackend[IO]` and `BeautySearchService.Impl[IO]` directly. It does not use `BeautySearchApi`, `LeaderboardPlugin`, real HTTP routes, Qdrant, hybrid search, Elasticsearch, repository snapshots, Docker, startup indexing, or default production DI modules.
- `BeautySearchCatalogBackendModuleSpec.scala` includes only `BeautySearchCatalogBackendModules.seedResourceInMemory[IO]` plus a `BeautyQSeedLoader.ResourceLoader` binding, roots a local probe, and proves the opt-in module assembles ready documents, `InMemorySearchBackend[IO]`, and `BeautySearchService.Impl[IO]`. It does not use `BeautySearchApi`, `HttpApi`, `LeaderboardPlugin`, Qdrant, hybrid search, Elasticsearch, repository snapshots, Docker, startup indexing, route exposure, or production default bindings.
- `BeautySearchAppGraphBoundarySpec.scala` binds the complete Beauty search API/service/backend stack through a spec-local `ModuleDef` and targets a test-local stack root. The fake backend records parser-produced intents and returns an empty response; the spec also issues one request through the assembled `BeautySearchApi`. It does not use `LeaderboardPlugin`, production app graph magic, Qdrant, hybrid search, Elasticsearch, repository snapshots, seed loaders, Docker, startup indexing, or production DI modules.
- `BeautySearchProductionInclusionBoundarySpec.scala` binds only `BeautySearchProductionInclusionHandle.disabled[IO]` in the Disabled module and does not bind fake `BeautySearchApi`, `BeautySearchService`, or `BeautySearchBackend`; its Enabled module uses a fake backend and proves construction without backend calls. It does not use `LeaderboardPlugin`, production app graph wiring, Qdrant, hybrid search, Elasticsearch, repository snapshots, Docker, startup indexing, or production backend bindings.
- `BeautySearchProductionIncludeModuleSpec.scala` uses a src/main `BeautySearchProductionIncludedApis[IO]` helper to build an include result from `BeautySearchProductionInclusionHandle[IO]`. Its Disabled module binds only the disabled handle and contributes no APIs. Its Enabled module explicitly constructs `BeautySearchTapirEndpoints`, `TapirHttpSupport[IO]`, `BeautySearchApi[IO]`, `BeautySearchService.Impl[IO]`, `BeautySearchIntentParser`, `BeautySearchSpecV1.spec`, and a fake `BeautySearchBackend[IO]`, contributes exactly one `BeautySearchApi`, and proves backend construction does not call the backend. It does not use production `many[HttpApi[F]]`, `LeaderboardPlugin`, production app graph wiring, Qdrant, hybrid search, Elasticsearch, repository snapshots, Docker, startup indexing, or production backend bindings.
- `BeautySearchOptInHttpApiModuleSpec.scala` includes `BeautySearchPluginModules.api[IO]` in a test-local graph, supplies a fake `BeautySearchService[IO]` and local `TapirHttpSupport[IO]`, roots a probe with a concrete `BeautySearchApi[IO]` retention dependency and a `Set[HttpApi[IO]]` aggregation dependency, and proves the real weak set contains exactly one `BeautySearchApi[IO]`. It does not use `LeaderboardPlugin`, `HttpServer`, Qdrant, hybrid search, Elasticsearch, seed loaders, repository snapshots, Docker, resources, startup indexing, or production backend bindings.

Non-production experiment:

- `ExperimentalBeautySearchService` is not a subtype of `BeautySearchService`. `BeautySearchPureSpec` contains a test named “stay separate from the existing BeautySearchService and BeautySearchBackend contracts”.

## C. Search DSL And Document Snapshot

Implemented/current:

- DSL package: `leaderboard.search.dsl`.
- Canonical spec: `BeautySearchSpecV1.spec` in `search/dsl/BeautySearchSpecV1.scala`.
- Core DSL types: `SearchField`, `SearchDocumentSpec`, `SearchConstraint`, `SearchSynonym`, `FacetSpec`, `SearchRequestSpec`, `RankingSpec`, `CarouselSpec`, `BeautySearchSpec`.
- Vector DSL types: `EmbeddingSpec`, `VectorSearchSpec`, `VectorDistance`.
- Document model: `VariantSearchDocument` in `search/document/VariantSearchDocument.scala`.
- Snapshot model: `BeautySearchCatalogSnapshot`.
- Snapshot loaders: `FromRepositories` and `SeedScopedFromRepositories`.
- Document builder: `VariantSearchDocumentBuilder.build(snapshot)`.
- Test-only ready-document proof: `BeautySearchCatalogBackendReadinessSpec` wraps built documents in a test-local readiness handle before constructing the in-memory backend.

Important behavior:

- `VariantSearchDocumentBuilder` joins variant, offer, service, category, master, and location.
- It fails missing joins with `QueryFailure.domain` and validates variant attributes against service schema.
- It denormalizes service, category, provider, location, attribute, price, duration, and geo fields into one document.
- `BeautySearchSpecV1` generates dynamic attribute fields from `AttributeDefinition.all`.

Resolved mismatch:

- `SeedScopedFromRepositories` now depends directly on `BeautyQSeedReady`, resolving the previously documented repository-instruction mismatch for seed-json plus repository snapshot paths.
- This change addresses dependency expression only; the review distinction remains that the original finding was a rule mismatch, not a runtime failure proven by tests.
- The explicit `BeautyQSeedReady` edge does not by itself solve production search readiness. Future implementation still needs a source-of-truth and freshness design for repository snapshots, Elasticsearch indexes, and any production search-read model.
- The catalog snapshot/in-memory readiness proof only proves explicit test-local document readiness before backend construction. It does not define production freshness, refresh, staleness, repository snapshot ownership, index lifecycle, runtime catalog replacement, or production backend selection.
- That proof is a startup readiness shape only. It does not make the route production-exposed, and it leaves the production freshness/staleness problem unresolved.

## D. Elasticsearch Path

Implemented/current interpreters:

- `ElasticsearchMappingInterpreter.mapping(spec)`: spec-to-mapping JSON.
- `ElasticsearchIngestionInterpreter.bulkPayload` and `sourceJson`: document ingestion JSON.
- `ElasticsearchSearchRequestInterpreter.request(spec, input, intent)`: request JSON with text, filters, facets, geo, and boosts.
- `ElasticsearchSearchResponseInterpreter.interpret`: ES JSON to `BeautySearchResponse`.
- `ElasticsearchSearchResponseInterpreter.documentHits`: ES JSON to generic `LexicalDocumentHit[MasterServiceOfferVariantId]`.

Matched query diagnostics:

- `ElasticsearchSearchResponseInterpreter.SearchHit.decoder` reads `matched_queries` and falls back to `_matched_queries`.
- `ElasticsearchSearchResponseInterpreterSpec` tests preserving real matched query names, fallback to legacy `_matched_queries`, and preferring `matched_queries` when both fields exist.
- `LexicalDocumentHit.matchedFields` currently carries matched query names/lexical diagnostics, not full ES highlights.

Integration-test-only real-resource path:

- `BeautySearchElasticsearchIntegrationSpec.scala` uses `ElasticsearchPortCfg`, `ElasticsearchTestClient`, creates an index, ingests documents, runs eval subsets, and deletes test indexes.
- `ElasticsearchDockerPlugin.scala` binds the Docker container and `ElasticsearchPortCfg`.

Production-wired/current:

- No production Elasticsearch search backend binding was found in `LeaderboardPlugin.scala`.
- No production indexing lifecycle, search API, or `BeautySearchBackend` implementation backed by Elasticsearch was found outside tests/interpreters.

Classification:

- Interpreters: implemented/current pure code.
- Test client and Docker integration: integration-test-only.
- Production runtime Elasticsearch search path: unclear/absent in inspected wiring.

Future implementation boundary:

- If Elasticsearch is selected for future production search, the first production backend should stay lexical/simple-first.
- A controlled first route contract can use a catalog snapshot or in-memory backend before any Elasticsearch runtime lifecycle is introduced.
- Elasticsearch should only be considered for a production backend after index lifecycle, readiness, failure behavior, freshness, and observability are explicitly designed.

## E. Qdrant / Vector Path

Implemented/current non-production components:

- `QdrantClient`: HTTP client wrapper.
- `QdrantSearchClient`, `QdrantPointUpsertClient`, `QdrantCollectionInfoClient`: adapter traits and `QdrantClient` adapters.
- `EmbeddingClient` and `LlamaCppEmbeddingClient`: embedding interface and local llama.cpp HTTP client.
- `QdrantPointId`: Qdrant-compatible point id boundary.
- `QdrantJsonInterpreter`: create collection, search, and upsert JSON.
- `QdrantSearchDocumentIndexer`, `QdrantVariantDocumentIndexer`, `QdrantVariantDocumentSnapshotIndexer`: indexing stack.
- `QdrantCollectionIdentity`, `QdrantCollectionReadinessConfig`, `QdrantCollectionInfoDecoder`, `QdrantCollectionCompatibilityValidator`, `QdrantCollectionCompatibilityChecker`, `QdrantCollectionCompatibilityGuard`: collection identity/readiness/compatibility stack.
- `QdrantSemanticCandidateSearch` and `QdrantSemanticCandidateBackend`: semantic candidate retrieval.
- `QdrantCandidateAssembler` and `QdrantCandidateResponseProjector`: Qdrant candidate assembly/projection.
- `QdrantNonProductionExperimentActivation`, `QdrantNonProductionExperimentComposition`, `QdrantNonProductionHybridExperiment`: explicit non-production experiment boundaries.

Manual/local:

- `LlamaCppEmbeddingClient` is used by env-gated tests requiring `LLAMA_CPP_EMBEDDING_URL`.
- AGENTS instructions include a manual `llama-server` command.

Integration-test-only / Docker-backed:

- `QdrantDockerSmokeSpec.scala` uses Distage `QdrantPortCfg` and a Docker-backed Qdrant container.
- `QdrantCollectionCompatibilityIntegrationSpec.scala` is env-gated by `QDRANT_COLLECTION_COMPATIBILITY_INTEGRATION`.
- `QdrantSnapshotIndexingCompatibilityIntegrationSpec.scala` is env-gated by `QDRANT_SNAPSHOT_INDEXING_COMPATIBILITY_INTEGRATION`.

Production-wired/current:

- No Qdrant production search binding was found in `LeaderboardPlugin.scala`.
- `docs/search-dsl-qdrant-vector-backend.md` states Qdrant is non-production experimental readiness foundation, not production lifecycle/routing/fallback/hybrid wiring.

What not to infer:

- Qdrant is not the production search backend.
- Qdrant is not a production fallback.
- Qdrant readiness tests do not prove production rollout readiness.

## F. Hybrid / Generic Retrieval Path

Implemented/current generic seams:

- `LexicalDocumentHit` and `LexicalDocumentBackend` in `search/lexical`.
- `SemanticDocumentHit`, `SemanticDocumentBackend`, `SemanticDocumentLookup`, `SemanticCandidateAssembler`, and `SemanticResponseProjector` in `search/semantic`.
- `HybridDocumentRetrievalResult` and diagnostics in `search/hybrid`.

Implemented/current BeautyQ-specific policy:

- `BeautyQHybridProjectionPolicy.lexicalFirstSemanticSupplement`.
- `BeautyQHybridVariantProjection`.
- `BeautyQHybridProviderServiceProjection`.
- `BeautyQHybridResponseAdapter`.
- `BeautyQHybridResponsePipeline`.

Non-production experiment:

- `ExperimentalHybridSearchBackend` routes between lexical backend, Qdrant semantic candidate route, and an intentionally unimplemented fallback branch that currently delegates to lexical.
- `ExperimentalBeautySearchService` accepts explicit `SearchRoutingMetadata` and is not the production `BeautySearchService`.
- `QdrantNonProductionHybridExperiment` composes non-production Qdrant composition and experimental service.

Routing:

- `SearchBackendRouter` can decide `ElasticsearchOnly`, `QdrantCandidateRoute`, or `ElasticsearchThenQdrantFallback`.
- `BeautySearchPureSpec` tests lexical, constrained, hard-negative, residual-text, and broad semantic routing decisions.
- `ExperimentalHybridRouteDiagnosticsSpec` documents diagnostics, including fallback requested but not implemented.

What is not implemented for production:

- No production hybrid role/API/binding.
- No production fallback-on-zero-results.
- No score fusion/reranking.
- No Qdrant-as-default.
- No hybrid-as-default.
- No residual-text semantic route for production.
- No production collection manager or startup auto-indexing.
- No API routing metadata contract.
- No benchmark-driven runtime model switching.

Production boundary:

- Qdrant and hybrid stay non-production/manual-local/experimental until lifecycle, routing, freshness, observability, kill-switch, and collection-management questions are resolved.
- Elasticsearch/Qdrant/hybrid implemented pieces do not by themselves imply production wiring.

## G. Benchmarks / Eval

Implemented/current:

- Eval model and scorer: `leaderboard.search.eval.BeautySearchEval`.
- Eval inventory: `BeautySearchEvalInventory.scala`.
- Pure eval tests: `BeautySearchPureSpec.scala`.
- Elasticsearch eval integration: `BeautySearchElasticsearchIntegrationSpec.scala`.
- Qdrant benchmark model/runner/report/decision code: `QdrantEmbeddingBenchmark*.scala`.
- Saved report comparison: `QdrantEmbeddingBenchmarkSavedReportComparison.scala` and tests.

Manual/local/env-gated:

- `QdrantSemanticCandidateEvalSpec.scala` requires `LLAMA_CPP_EMBEDDING_URL`; optional assertions use `QDRANT_SEMANTIC_QUALITY_ASSERTIONS`.
- `QdrantEmbeddingBenchmarkExecutorIntegrationSpec.scala` is gated by `QDRANT_EMBEDDING_BENCHMARK_SINGLE_ENDPOINT`, `QDRANT_EMBEDDING_BENCHMARK_ENDPOINT`, `QDRANT_EMBEDDING_BENCHMARK_DUAL_ENDPOINT`, `QDRANT_EMBEDDING_SMALL_URL`, and `QDRANT_EMBEDDING_LARGE_URL`.
- `QdrantEmbeddingBenchmarkSavedReportComparisonManualSpec.scala` is gated by `QDRANT_EMBEDDING_BENCHMARK_COMPARE_SAVED_REPORTS`, `QDRANT_EMBEDDING_BENCHMARK_LEFT_JSON`, and `QDRANT_EMBEDDING_BENCHMARK_RIGHT_JSON`.

Decision policy boundary:

- `QdrantEmbeddingBenchmarkDecisionPolicy` outputs verdicts such as keep baseline, worth further evaluation, worth switching, or rejected.
- Docs and AGENTS instructions say benchmark output is decision support, not production automation.
- No production code was found that consumes benchmark verdicts to switch models or routing.

## Current Production Search Status Summary

Production-wired/current:

- `POST /beauty-search` is production-exposed through `LeaderboardPlugin.modules.api` including `BeautySearchRouteModules.seedCatalogInMemory[F]`.
- `BeautySearchService.Impl[F]` and `BeautySearchBackend[F]` are production-bound through the included route module.
- `BeautySearchApi[F]` is production-exposed through the same include.
- The backend is seed-resource catalog snapshot readiness plus `InMemorySearchBackend[F]`.
- This is lexical/simple/catalog-first. It is not Elasticsearch, not Qdrant, and not hybrid.
- No Elasticsearch search backend binding found.
- No Qdrant/hybrid production binding found.
- No search role in `LeaderboardRole.scala`.
- `BeautySearchProductionInclusionActivation`/`Handle`/`IncludedApis` still exist as a staging/helper boundary, but they are NOT the active production gate. The route is exposed directly via `LeaderboardPlugin.modules.api` include, not via the old inclusion handle. A real kill switch / enable-disable route gate remains future hardening.

Implemented/current but mostly test/experiment exercised:

- Pure `POST /beauty-search` Tapir contract skeleton and fake-service route contract tests.
- Thin unwired `BeautySearchApi`.
- Fake-backend `BeautySearchService.Impl` binding proof.
- Src/main ready-catalog document helper and opt-in catalog/in-memory backend/service module with focused proof.
- Src/main opt-in end-to-end seed-catalog/in-memory route module with focused smoke proof.
- Test-only explicit app-graph boundary proof for the Beauty search API/service/backend stack.
- Disabled-by-default production inclusion activation/handle proof.
- Search DSL/spec.
- Parser.
- Document builder/snapshot loaders.
- In-memory backend.
- Elasticsearch interpreters.
- Qdrant/vector components.
- Hybrid/generic retrieval seams.
- Eval/benchmark code.

Service binding acceptance criteria for future implementation:

- Future production binding must answer where `BeautySearchService.Impl` is bound or included.
- It must answer which `BeautySearchBackend` is bound first and whether the seed-catalog/in-memory route module is acceptable for initial enabled exposure.
- It must define how catalog/index readiness and freshness/staleness are guaranteed.
- It must define how parser/backend failures are represented at the route boundary.
- It must show which route-level contract tests prove request/response/error behavior.
- It must define how diagnostics and observability are exposed.
