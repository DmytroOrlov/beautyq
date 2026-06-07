# Current Gaps And Roadmap

This file separates current gaps from future recommendations. Do not read recommendations as current implementation.

## Confirmed Current Gaps

### Search HTTP Exposure

Current status:

- `POST /beauty-search` is now production-included through `LeaderboardPlugin.modules.api`.
- The included route stack is `BeautySearchRouteModules.seedCatalogInMemory[F]`.
- The backend is startup seed-resource catalog snapshot readiness plus `InMemorySearchBackend[F]`.
- This closes the route exposure gap only. Production freshness/refresh/staleness, runtime replacement, observability, and kill-switch behavior remain gaps.
- `BeautySearchProductionRouteLimitSpec` characterizes the limit parameter behavior: limit 3 returns non-empty `variantCarousel` size <= 3; limit 0 and limit -5 return `200 OK` with empty `variantCarousel`; limit 100000 returns `variantCarousel` size capped by `BeautySearchSpecV1.spec.carouselSpec.variantSize`. Zero and negative limits currently return `200 OK` with empty `variantCarousel`. Huge limits are capped by `BeautySearchSpecV1.spec.carouselSpec.variantSize`. This is current behavior, not a full validation or error policy.

Evidence:

- `BeautySearchTapirEndpoints.scala` defines an unwired `POST /beauty-search` contract.
- `BeautySearchApi.scala` bridges the pure endpoint to `BeautySearchService[F]` without backend creation or production wiring.
- `BeautySearchApiHttpContractSuite.scala` tests the adapter route through a fake service only.
- `BeautySearchServiceBindingSpec.scala` proves a focused test-only Distage module can assemble `BeautySearchService.Impl[IO]` with `BeautySearchSpecV1.spec`, `BeautySearchIntentParser`, and a fake in-memory `BeautySearchBackend[IO]`.
- `BeautySearchReadyCatalogDocuments` provides a src/main explicit ready-catalog document helper with source and non-empty document validation, but no freshness or refresh semantics.
- `BeautySearchCatalogBackendModules.seedResourceInMemory[F]` provides an opt-in catalog/in-memory backend/service module that can bind `BeautySearchService.Impl[F]` with `InMemorySearchBackend[F]` from seed-resource ready documents when explicitly included with a `BeautyQSeedLoader`.
- `BeautySearchCatalogBackendReadinessSpec.scala` proves a focused catalog snapshot/in-memory backend readiness boundary with explicit ready documents before `InMemorySearchBackend` construction.
- `BeautySearchCatalogBackendModuleSpec.scala` proves the opt-in catalog/in-memory module can materialize ready documents, backend, and service and answer a simple search without API, HTTP, `LeaderboardPlugin`, Elasticsearch, Qdrant, hybrid, Docker, repository snapshots, or startup indexing.
- `BeautySearchAppGraphBoundarySpec.scala` proves a focused test-only explicit app-graph boundary can assemble `BeautySearchTapirEndpoints`, `TapirHttpSupport[IO]`, `BeautySearchApi[IO]`, `BeautySearchService.Impl[IO]`, `BeautySearchIntentParser`, `BeautySearchSpecV1.spec`, and a fake `BeautySearchBackend[IO]`.
- `BeautySearchProductionInclusionActivation` and `BeautySearchProductionInclusionHandle` provide a disabled-by-default activation/handle boundary; Disabled avoids evaluating/constructing the API/service/backend graph, and Enabled can explicitly assemble the stack in a test proof.
- `BeautySearchProductionIncludeModuleSpec.scala` proves an include-module shape at a src/main `BeautySearchProductionIncludedApis` API aggregation boundary: Disabled contributes no Beauty search API, and Enabled explicitly contributes exactly one `BeautySearchApi`.
- The include-module proof uses a src/main `BeautySearchProductionIncludedApis` helper that converts an enabled handle to a local `HttpApi` list, not production `many[HttpApi[F]]`.
- `BeautySearchPluginModules.api[F]` is a src/main opt-in helper module that binds `BeautySearchTapirEndpoints`, `BeautySearchApi[F]`, and contributes `BeautySearchApi[F]` to a real `many[HttpApi[F]].weak[...]` set when explicitly included.
- `BeautySearchOptInHttpApiModuleSpec.scala` proves that explicit helper contributes exactly one `BeautySearchApi[IO]` through the same `Set[HttpApi[IO]]` aggregation shape consumed by `HttpServer.Impl`, with the repo's role-style concrete API retention edge and a fake service that is not called during graph construction.
- `BeautySearchRouteModules.seedCatalogInMemory[F]` is a src/main explicit opt-in end-to-end route module that composes the opt-in seed-resource catalog/in-memory backend/service module with the opt-in HttpApi module.
- `BeautySearchOptInRouteModuleSpec.scala` proves that explicit composed module contributes exactly one `BeautySearchApi[IO]` through the same real `Set[HttpApi[IO]]` shape and serves one successful non-empty `POST /beauty-search` smoke response without `HttpServer`, `LeaderboardPlugin`, Elasticsearch, Qdrant, hybrid, Docker, repository snapshots, or startup indexing.
- `LeaderboardPlugin.modules.api` now includes `BeautySearchRouteModules.seedCatalogInMemory[F]`, so `POST /beauty-search` is production-included in the default API graph.
- `BeautySearchProductionRouteExposureSpec.scala` proves that default graph contributes exactly one `BeautySearchApi[IO]` through the same real `Set[HttpApi[IO]]` shape and serves one successful non-empty `POST /beauty-search` response without `HttpServer`, Elasticsearch, Qdrant, hybrid, Docker, repository snapshots, or startup indexing.
- The production-included backend source is the startup seed-resource catalog snapshot plus `InMemorySearchBackend[F]`.
- This is lexical/simple/catalog-first. It is not Elasticsearch, not Qdrant, and not hybrid.
- It does not add repository snapshot wiring, startup indexing, fallback, reranking, score fusion, or benchmark-driven routing.
- `LeaderboardPlugin.modules.api` is the production API aggregation point: it binds Tapir endpoint singletons, binds API adapters, contributes them to `many[HttpApi[F]]`, and `HttpServer.Impl` serves the combined set.
- `BeautySearchApi[F]` is now contributed there by `BeautySearchRouteModules.seedCatalogInMemory[F]`, so `/beauty-search` is production-exposed.
- `LeaderboardRole.scala` has no search role.
- Production freshness/refresh/staleness policy, runtime catalog replacement, source-of-truth reconciliation, stale-catalog observability, and kill-switch behavior remain unresolved.
- There is no runtime refresh or replacement policy yet.
- Benchmark decisions do not affect routing.
- Qdrant/hybrid remain non-production/manual-local/experimental and are not default.
- Backend freshness/staleness policy and production hardening remain future work.

Future implementation:

- The route contract skeleton has chosen `POST /beauty-search`, `UserSearchInput` request JSON, `BeautySearchResponse` response JSON, existing coarse non-single-entity error behavior, explicit empty-array responses, and no diagnostics exposure.
- Limit behavior is characterized by `BeautySearchProductionRouteLimitSpec` (zero/negative → 200 OK empty carousel; huge → capped by `variantSize`). This is current behavior, not a full validation or error policy.
- The next code patch should design or implement observability, freshness/staleness reporting, runtime refresh/replacement, and kill-switch behavior for the included seed-resource/in-memory route.
- Future production hardening still needs explicit decisions for timeout behavior, diagnostics visibility, source-of-truth reconciliation, stale-catalog handling, bad JSON handling, typed error responses, max query length, lat/lon validation, freshness/staleness, observability, and kill-switch.

### BeautySearchService Wiring

Current status:

- `BeautySearchService.Impl` is production-bound through `BeautySearchRouteModules.seedCatalogInMemory[F]`, included by `LeaderboardPlugin.modules.api`.
- The bound backend is `InMemorySearchBackend[F]` over seed-resource ready catalog documents.
- The remaining gap is not service binding; it is freshness, refresh/replacement, observability, kill switch, and production source-of-truth policy.

Evidence:

- `BeautySearchService.Impl` exists in `search/BeautySearchModels.scala`.
- Targeted searches found test-local construction in `BeautySearchPureSpec.scala`, not bindings in `LeaderboardPlugin.scala`.
- `BeautySearchServiceBindingSpec.scala` binds `BeautySearchService[IO]` to `BeautySearchService.Impl[IO]` with a fake `BeautySearchBackend[IO]` in `src/test` only and verifies parser handoff, successful backend response pass-through, and backend failure pass-through.
- `BeautySearchCatalogBackendReadinessSpec.scala` constructs `BeautySearchService.Impl[IO]` with `InMemorySearchBackend[IO]` from the src/main `BeautySearchReadyCatalogDocuments` handle.
- `BeautySearchCatalogBackendModules.seedResourceInMemory[F]` binds `BeautySearchService.Impl[F]` with `InMemorySearchBackend[F]` and is now included in the default API graph through `BeautySearchRouteModules.seedCatalogInMemory[F]`.
- `BeautySearchAppGraphBoundarySpec.scala` assembles `BeautySearchApi[IO]` and `BeautySearchService.Impl[IO]` through a test-local `ModuleDef` and fake `BeautySearchBackend[IO]` in `src/test` only.
- `BeautySearchProductionIncludeModuleSpec.scala` assembles `BeautySearchApi[IO]` and `BeautySearchService.Impl[IO]` through a src/main-backed enabled include module only; the disabled include module contributes no API and constructs no API/service/backend.
- `UserSearchInput`, `ParsedSearchIntent`, `BeautySearchResponse`, `BeautySearchBackend[F]`, `BeautySearchService[F]`, `BeautySearchService.Impl`, and `BeautySearchSpecV1` exist as implemented model/service pieces.
- `BeautySearchProductionRouteExposureSpec.scala` proves the default API graph exposes the route through `Set[HttpApi[IO]]`.

Acceptance criteria for future implementation:

- Future production hardening must define freshness/staleness reporting and runtime replacement behavior.
- It must define stale-catalog observability and kill-switch behavior.
- It must decide whether seed-resource startup snapshot readiness remains acceptable as product behavior.
- It must define parser/backend failure representation.
- It must define diagnostics and observability exposure.

### Elasticsearch Runtime Backend

Gap:

- Elasticsearch interpreters and integration tests exist, but no production Elasticsearch `BeautySearchBackend` binding or indexing lifecycle was found.

Evidence:

- Interpreters live in `leaderboard.search.elasticsearch`.
- `BeautySearchElasticsearchIntegrationSpec.scala` creates indexes and executes search through `ElasticsearchTestClient.scala`.
- `LeaderboardPlugin.scala` has no Elasticsearch-backed search binding.

Future implementation boundary:

- The first production backend should stay lexical/simple-first.
- A fake, catalog snapshot, or in-memory backend is a valid first controlled route-contract backend.
- If Elasticsearch is selected later, it requires explicit index lifecycle, readiness, freshness, and failure-behavior design before production binding.

### Qdrant / Hybrid Production Boundary

Gap:

- Qdrant and hybrid are non-production/manual/local/test boundaries, not production wiring.

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

Implemented proof and opt-in module:

- `BeautySearchReadyCatalogDocuments` is a src/main helper that wraps a source label plus `VariantSearchDocument` list, rejects empty source labels, rejects empty document lists, and preserves documents unchanged.
- `BeautySearchCatalogBackendFactory.fromSeedLoader` loads seed data through a provided `BeautyQSeedLoader`, creates `BeautySearchCatalogSnapshot.fromSeedData(seedData)`, builds documents with `VariantSearchDocumentBuilder.build(snapshot)`, and wraps them in `BeautySearchReadyCatalogDocuments(source, documents)`.
- `BeautySearchCatalogBackendModules.seedResourceInMemory[F]` binds ready documents, `InMemorySearchBackend[F]`, and `BeautySearchService.Impl[F]`; it is included in production through `BeautySearchRouteModules.seedCatalogInMemory[F]`.
- `BeautySearchRouteModules.seedCatalogInMemory[F]` composes that backend/service module with the opt-in `BeautySearchApi` contribution; it is included by `LeaderboardPlugin.modules.api`.
- `BeautySearchCatalogBackendReadinessSpec.scala` still proves the direct seed-resource to snapshot to documents to ready-handle path before constructing `InMemorySearchBackend`.
- `BeautySearchCatalogBackendModuleSpec.scala` proves the opt-in module materializes ready documents, backend, and service and can answer a simple search.
- The proof asserts the ready source label is explicit (`seed-resource-loader`), documents are non-empty, empty ready documents are rejected before backend construction, and `BeautySearchService.Impl[IO]` returns non-empty variant/provider/service carousels through the in-memory backend.

Boundary:

- This is now production route exposure through `LeaderboardPlugin.modules.api`.
- It binds `BeautySearchService` and `BeautySearchBackend` in the default API graph only through the seed-resource/in-memory module.
- It does not add a search role.
- It does not add repository-backed production snapshot wiring, startup indexing, Elasticsearch lifecycle, Qdrant/hybrid routing, fallback, reranking, score fusion, or benchmark-driven model switching.
- Production backend selection and freshness/refresh/staleness policy remain future work.
- The seed-resource/catalog/in-memory path is startup snapshot readiness only. It does not solve production freshness, staleness bounds, runtime catalog replacement, repository-vs-seed source-of-truth choice, or stale-catalog observability.

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

- Search route exposure exists through `LeaderboardPlugin.modules.api`.
- Default production `BeautySearchService` binding exists through `BeautySearchRouteModules.seedCatalogInMemory[F]`.
- Default production lexical/simple backend binding exists as seed-resource ready catalog documents plus `InMemorySearchBackend[F]`.
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
3. Keep Qdrant/hybrid out of this production hardening path unless a separate production design approves it: no Qdrant/hybrid default, no fallback, no reranking, no score fusion, no benchmark-driven routing.
4. If Elasticsearch is chosen later, add explicit backend/client/index lifecycle and freshness design before production binding.
5. Verify future seed-json plus repository snapshot helpers keep a direct `BeautyQSeedReady` edge when they read shared Postgres state by seed-scoped ids.
6. Reconcile stale docs before relying on them in future implementation passes.
