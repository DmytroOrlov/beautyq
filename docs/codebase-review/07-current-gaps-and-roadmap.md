# Current Gaps And Roadmap

This file separates current gaps from future recommendations. Do not read recommendations as current implementation.

## Confirmed Current Gaps

### Search HTTP Exposure

Gap:

- No production Beauty search HTTP route was found.

Evidence:

- No `SearchApi` or `SearchTapirEndpoints` under `bifunctor-tagless/src/main/scala/leaderboard/api` or `http/tapir`.
- `LeaderboardPlugin.modules.api` does not bind search services/endpoints.
- `LeaderboardRole.scala` has no search role.

### BeautySearchService Wiring

Gap:

- `BeautySearchService.Impl` is implemented but not production-bound in inspected DI wiring.

Evidence:

- `BeautySearchService.Impl` exists in `search/BeautySearchModels.scala`.
- Targeted searches found test-local construction in `BeautySearchPureSpec.scala`, not bindings in `LeaderboardPlugin.scala`.

### Elasticsearch Runtime Backend

Gap:

- Elasticsearch interpreters and integration tests exist, but no production Elasticsearch `BeautySearchBackend` binding or indexing lifecycle was found.

Evidence:

- Interpreters live in `leaderboard.search.elasticsearch`.
- `BeautySearchElasticsearchIntegrationSpec.scala` creates indexes and executes search through `ElasticsearchTestClient.scala`.
- `LeaderboardPlugin.scala` has no Elasticsearch-backed search binding.

### Qdrant / Hybrid Production Boundary

Gap:

- Qdrant and hybrid are non-production/manual/local/test boundaries, not production wiring.

Evidence:

- Docs `search-dsl-qdrant-vector-backend.md` and `search-dsl-hybrid-v1-plan.md` explicitly say production hybrid is not implemented.
- Classes are named `QdrantNonProductionExperiment*`, `BeautyQNonProductionHybrid*`, and `Experimental*`.
- `LeaderboardPlugin.scala` has no Qdrant/hybrid search binding.

### Seed-Scoped Repository Snapshot Readiness Edge

Gap:

- `BeautySearchCatalogSnapshotLoader.SeedScopedFromRepositories` does not directly depend on `BeautyQSeedReady`.

Evidence:

- Constructor in `search/document/VariantSearchDocument.scala` takes seed and repositories only.
- AGENTS instructions require seed-json plus shared-Postgres snapshot paths to depend directly on `BeautyQSeedReady` before repository reads.

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

- No search API contract.
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

## Repository / Persistence Risks

Risks:

- Schema lives in repository resources rather than migrations, so startup graph dependencies are critical.
- Removing `@unused` parent repo dependencies can break FK-backed table creation order.
- Seed insertion is sequential but not documented as one global transaction.
- Search snapshot loaders can fail if repository seed rows are not ready; the direct `BeautyQSeedReady` edge is missing in `SeedScopedFromRepositories`.

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

1. Add a direct `BeautyQSeedReady` dependency to `SeedScopedFromRepositories` if the seed-backed snapshot rule is still intended.
2. Decide whether Beauty search should have a production HTTP API; if yes, start with a typed route contract test before wiring runtime search.
3. If Elasticsearch is the intended production lexical backend, add explicit backend/client/index lifecycle design before implementation.
4. Keep Qdrant/hybrid behind explicit non-production activation until lifecycle, routing, freshness, observability, and kill-switch decisions are documented.
5. Reconcile stale docs before relying on them in future implementation passes.
