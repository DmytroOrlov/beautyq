# Current Gaps And Roadmap

This file separates current gaps from future recommendations. Do not read recommendations as current implementation.

## Confirmed Current Gaps

### Search HTTP Exposure

Gap:

- A pure Beauty search route contract skeleton exists, but no production Beauty search HTTP route was found.
- A thin unwired Beauty search API adapter exists, but it is not included in the production app graph.
- The production Beauty search API boundary now has a disabled-by-default activation/handle proof, but it remains unused by production route wiring.
- The current proof set is present, but production route inclusion is still absent: pure `POST /beauty-search`, thin unwired `BeautySearchApi`, fake-service route contract suite, fake-backend `BeautySearchService.Impl` binding proof, test-only catalog snapshot/in-memory backend readiness proof, test-only complete app-graph boundary proof, disabled-by-default production inclusion boundary proof, test-only disabled-by-default include-module aggregation proof, and an explicit opt-in HttpApi module proof.

Evidence:

- `BeautySearchTapirEndpoints.scala` defines an unwired `POST /beauty-search` contract.
- `BeautySearchApi.scala` bridges the pure endpoint to `BeautySearchService[F]` without backend creation or production wiring.
- `BeautySearchApiHttpContractSuite.scala` tests the adapter route through a fake service only.
- `BeautySearchServiceBindingSpec.scala` proves a focused test-only Distage module can assemble `BeautySearchService.Impl[IO]` with `BeautySearchSpecV1.spec`, `BeautySearchIntentParser`, and a fake in-memory `BeautySearchBackend[IO]`.
- `BeautySearchCatalogBackendReadinessSpec.scala` proves a focused test-only catalog snapshot/in-memory backend readiness boundary with explicit ready documents before `InMemorySearchBackend` construction.
- `BeautySearchAppGraphBoundarySpec.scala` proves a focused test-only explicit app-graph boundary can assemble `BeautySearchTapirEndpoints`, `TapirHttpSupport[IO]`, `BeautySearchApi[IO]`, `BeautySearchService.Impl[IO]`, `BeautySearchIntentParser`, `BeautySearchSpecV1.spec`, and a fake `BeautySearchBackend[IO]`.
- `BeautySearchProductionInclusionActivation` and `BeautySearchProductionInclusionHandle` provide a disabled-by-default activation/handle boundary; Disabled avoids evaluating/constructing the API/service/backend graph, and Enabled can explicitly assemble the stack in a test proof.
- `BeautySearchProductionIncludeModuleSpec.scala` proves an include-module shape at a src/main `BeautySearchProductionIncludedApis` API aggregation boundary: Disabled contributes no Beauty search API, and Enabled explicitly contributes exactly one `BeautySearchApi`.
- The include-module proof uses a src/main `BeautySearchProductionIncludedApis` helper that converts an enabled handle to a local `HttpApi` list, not production `many[HttpApi[F]]`.
- `BeautySearchPluginModules.api[F]` is a src/main opt-in helper module that binds `BeautySearchTapirEndpoints`, `BeautySearchApi[F]`, and contributes `BeautySearchApi[F]` to a real `many[HttpApi[F]].weak[...]` set when explicitly included.
- `BeautySearchOptInHttpApiModuleSpec.scala` proves that explicit helper contributes exactly one `BeautySearchApi[IO]` through the same `Set[HttpApi[IO]]` aggregation shape consumed by `HttpServer.Impl`, with the repo's role-style concrete API retention edge and a fake service that is not called during graph construction.
- `LeaderboardPlugin.modules.api` now binds the disabled Beauty search inclusion boundary only; `BeautySearchApi` is still not part of production `many[HttpApi[F]]`, `/beauty-search` is still not production-exposed, and the production `BeautySearchService`/`BeautySearchBackend` binding remains absent.
- `LeaderboardPlugin.modules.api` does not bind search services/endpoints.
- `LeaderboardPlugin` was not changed for the unwired adapter or fake-backend service binding proof.
- `LeaderboardPlugin` was not changed for the catalog snapshot/in-memory backend readiness proof.
- `LeaderboardPlugin` was not changed for the app-graph boundary proof.
- `LeaderboardPlugin` was not changed for the production inclusion boundary proof.
- `LeaderboardPlugin` was not changed for the include-module shape proof.
- `LeaderboardPlugin` was not changed for the opt-in HttpApi module proof, and `BeautySearchPluginModules.api[F]` is not included by default modules.
- `BeautySearchApi` was not added to `many[HttpApi[F]]`.
- The default plugin still contributes no `BeautySearchApi` to the production `many[HttpApi[F]]` set.
- `LeaderboardPlugin.modules.api` is the production API aggregation point: it binds Tapir endpoint singletons, binds API adapters, contributes them to `many[HttpApi[F]]`, and `HttpServer.Impl` serves the combined set.
- Adding `BeautySearchApi` there would expose `POST /beauty-search`; that is the production inclusion boundary.
- `LeaderboardRole.scala` has no search role.
- No `BeautySearchService` or `BeautySearchBackend` production binding was added.
- No production `BeautySearchBackend` choice, freshness/refresh/staleness policy, Elasticsearch lifecycle, Qdrant lifecycle, hybrid routing, repository snapshot wiring, startup indexing, fallback, reranking, or score fusion was added.
- Qdrant/hybrid remain non-production/manual-local/experimental and are not default.
- Production app inclusion remains absent; backend freshness/staleness policy and Elasticsearch lifecycle remain future work.

Future implementation:

- Production Beauty search requires explicit route/binding.
- The next step is an explicit enabled include design/patch, not default route exposure.
- The new opt-in HttpApi module makes the weak-set contribution mechanically available, but actual `LeaderboardPlugin` inclusion still requires a separate enabled design/patch after readiness/freshness and production service/backend binding are decided.
- The route contract skeleton has chosen `POST /beauty-search`, `UserSearchInput` request JSON, `BeautySearchResponse` response JSON, existing coarse non-single-entity error behavior, explicit empty-array responses, and no diagnostics exposure.
- The next code patch should be `docs(search): design actual LeaderboardPlugin include patch` or `feat(search): include Beauty search route in LeaderboardPlugin behind disabled-by-default activation`.
- Future production wiring still needs explicit decisions for role inclusion, backend selection, readiness behavior, timeout behavior, observability, and diagnostics visibility.

### BeautySearchService Wiring

Gap:

- `BeautySearchService.Impl` is implemented and has fake-backend test-only Distage binding and app-graph boundary proofs, but it is not production-bound in inspected DI wiring.

Evidence:

- `BeautySearchService.Impl` exists in `search/BeautySearchModels.scala`.
- Targeted searches found test-local construction in `BeautySearchPureSpec.scala`, not bindings in `LeaderboardPlugin.scala`.
- `BeautySearchServiceBindingSpec.scala` binds `BeautySearchService[IO]` to `BeautySearchService.Impl[IO]` with a fake `BeautySearchBackend[IO]` in `src/test` only and verifies parser handoff, successful backend response pass-through, and backend failure pass-through.
- `BeautySearchCatalogBackendReadinessSpec.scala` constructs `BeautySearchService.Impl[IO]` with `InMemorySearchBackend[IO]` from an explicit test-local ready-document handle in `src/test` only.
- `BeautySearchAppGraphBoundarySpec.scala` assembles `BeautySearchApi[IO]` and `BeautySearchService.Impl[IO]` through a test-local `ModuleDef` and fake `BeautySearchBackend[IO]` in `src/test` only.
- `BeautySearchProductionIncludeModuleSpec.scala` assembles `BeautySearchApi[IO]` and `BeautySearchService.Impl[IO]` through a src/main-backed enabled include module only; the disabled include module contributes no API and constructs no API/service/backend.
- `UserSearchInput`, `ParsedSearchIntent`, `BeautySearchResponse`, `BeautySearchBackend[F]`, `BeautySearchService[F]`, `BeautySearchService.Impl`, and `BeautySearchSpecV1` exist as implemented model/service pieces.
- The proof set includes a complete app-graph boundary, but that remains test-local and not production-exposed yet.

Acceptance criteria for future implementation:

- Future production binding must answer where `BeautySearchService.Impl` is bound.
- It must answer which `BeautySearchBackend` is bound.
- It must guarantee catalog/index readiness explicitly.
- It must define parser/backend failure representation.
- It must show route-level contract tests that prove the HTTP contract.
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

### Catalog Snapshot / In-Memory Backend Readiness Proof

Implemented test-only proof:

- `BeautySearchCatalogBackendReadinessSpec.scala` loads seed data through `BeautyQSeedLoader.ResourceLoader`, creates `BeautySearchCatalogSnapshot.fromSeedData(seedData)`, builds documents with `VariantSearchDocumentBuilder.build(snapshot)`, and wraps them in a test-local `ReadyCatalogDocuments(source, documents)` handle before constructing `InMemorySearchBackend`.
- The proof asserts the ready source label is explicit (`seed-resource-loader`), documents are non-empty, empty ready documents are rejected before backend construction, and `BeautySearchService.Impl[IO]` returns non-empty variant/provider/service carousels through the in-memory backend.

Boundary:

- This is not production exposure.
- It does not bind `BeautySearchService` or `BeautySearchBackend` in production DI.
- It does not change `LeaderboardPlugin` or add a search role/API inclusion.
- It does not add repository-backed production snapshot wiring, startup indexing, Elasticsearch lifecycle, Qdrant/hybrid routing, fallback, reranking, score fusion, or benchmark-driven model switching.
- Production backend selection and freshness/refresh/staleness policy remain future work.
- The seed-resource/catalog/in-memory path proves a startup readiness shape only. It does not solve production freshness, staleness bounds, runtime catalog replacement, repository-vs-seed source-of-truth choice, or stale-catalog observability.

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

- Search API contract skeleton exists, but no production search API adapter/role/wiring exists.
- Disabled-by-default production inclusion activation/handle boundary exists, a src/main `BeautySearchProductionIncludedApis` include-module helper exists, and a test-local include-module aggregation proof exists, but no production include module or `LeaderboardPlugin` wiring uses it yet.
- An explicit opt-in `BeautySearchPluginModules.api[F]` helper exists for the real `many[HttpApi[F]]` weak-set contribution, but it is not included by `LeaderboardPlugin` default modules.
- No production `BeautySearchService` binding.
- No production lexical backend binding.
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

1. `docs(search): design explicit LeaderboardPlugin inclusion with activation`.
2. `test(search): prove production include module remains disabled by default`.
3. Keep Qdrant/hybrid out of first production exposure unless a separate production design approves it: no Qdrant/hybrid default, no fallback, no reranking, no score fusion, no benchmark-driven routing.
4. If Elasticsearch is chosen after that, add explicit backend/client/index lifecycle and freshness design before production binding.
5. Verify future seed-json plus repository snapshot helpers keep a direct `BeautyQSeedReady` edge when they read shared Postgres state by seed-scoped ids.
6. Reconcile stale docs before relying on them in future implementation passes.
