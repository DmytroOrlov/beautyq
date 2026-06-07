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
- `BeautySearchApiHttpContractSuite`: route-level contract skeleton using a test-local fake `BeautySearchService`.

Production-wired/current:

- Pass-2 inspection found no binding for `BeautySearchService.Impl`, `BeautySearchService`, or `BeautySearchBackend` in `LeaderboardPlugin.scala`.
- No production `SearchApi`, search role, or `/beauty-search` route wiring was found.
- No production `BeautySearchBackend` binding was found.
- The pure Tapir endpoint is not included in `LeaderboardPlugin.modules.api` and does not expose search by itself.

Design boundary:

- The implemented model/service/contract boundary is real code, but future implementation still requires explicit adapter/role/binding before Beauty search can be considered production-exposed.
- Do not infer production availability from the existence of `BeautySearchService.Impl`, `BeautySearchBackend[F]`, or related search models.
- Do not infer production availability from `BeautySearchTapirEndpoints`; it is a pure contract skeleton only.

Test-only/fake-only:

- `BeautySearchPureSpec.scala` constructs `BeautySearchService.Impl` directly with fake or in-memory backends.
- `FakeBeautySearchBackend`, `FailingBeautySearchBackend`, and `ThrowingBeautySearchBackend` are test-local helpers inside `BeautySearchPureSpec.scala`.
- `InMemorySearchBackend` is the pure regression backend described in `docs/beautyq-search-dsl-v1.md` and used by pure tests.
- `BeautySearchApiHttpContractSuite.scala` constructs a test-local fake `BeautySearchService`; it does not use Qdrant, hybrid, Elasticsearch, repository snapshots, or production DI.

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

Important behavior:

- `VariantSearchDocumentBuilder` joins variant, offer, service, category, master, and location.
- It fails missing joins with `QueryFailure.domain` and validates variant attributes against service schema.
- It denormalizes service, category, provider, location, attribute, price, duration, and geo fields into one document.
- `BeautySearchSpecV1` generates dynamic attribute fields from `AttributeDefinition.all`.

Resolved mismatch:

- `SeedScopedFromRepositories` now depends directly on `BeautyQSeedReady`, resolving the previously documented repository-instruction mismatch for seed-json plus repository snapshot paths.
- This change addresses dependency expression only; the review distinction remains that the original finding was a rule mismatch, not a runtime failure proven by tests.
- The explicit `BeautyQSeedReady` edge does not by itself solve production search readiness. Future implementation still needs a source-of-truth and freshness design for repository snapshots, Elasticsearch indexes, and any production search-read model.

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

- No production Beauty search HTTP route found.
- No `BeautySearchService` binding found.
- No `BeautySearchBackend` binding found.
- No Elasticsearch search backend binding found.
- No Qdrant/hybrid production binding found.
- No `SearchApi`, search role, or production `/beauty-search` route found in inspected wiring.

Implemented/current but mostly test/experiment exercised:

- Pure `POST /beauty-search` Tapir contract skeleton and fake-service route contract tests.
- Search DSL/spec.
- Parser.
- Document builder/snapshot loaders.
- In-memory backend.
- Elasticsearch interpreters.
- Qdrant/vector components.
- Hybrid/generic retrieval seams.
- Eval/benchmark code.

Service binding acceptance criteria for future implementation:

- Future production binding must answer where `BeautySearchService.Impl` is bound.
- It must answer which `BeautySearchBackend` is bound first.
- It must define how catalog/index readiness is guaranteed.
- It must define how parser/backend failures are represented at the route boundary.
- It must show which route-level contract tests prove request/response/error behavior.
- It must define how diagnostics and observability are exposed.
