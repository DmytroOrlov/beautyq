# API And HTTP Contracts

## API Layer Shape

Implemented/current:

```text
Role class
  -> *Api adapter
    -> *TapirEndpoints pure contract
    -> repositories/services
    -> TapirHttpSupport -> HttpApi -> HttpServer
```

Evidence:

- Roles: `LeaderboardRole.scala`.
- API adapters: `bifunctor-tagless/src/main/scala/leaderboard/api/*.scala`.
- Tapir contracts: `bifunctor-tagless/src/main/scala/leaderboard/http/tapir/*TapirEndpoints.scala`.
- DI wiring: `LeaderboardPlugin` top-level (`modules.apiBase[IO]` plus `BeautySearchRouteModules.apiElasticsearch`; `modules.api[F]` is rollback/legacy).
- HTTP composition: `HttpServer.scala` and `many[HttpApi[F]]` in `LeaderboardPlugin.scala`.

## Role-Backed APIs

Implemented/current:

- `LadderApi` via `LadderRole`.
- `ProfileApi` via `ProfileRole`.
- `CategoryApi` via `CategoryRole`.
- `ServiceApi` via `ServiceRole`.
- `MasterApi` via `MasterRole`.
- `MasterLocationApi` via `MasterLocationRole`.
- `MasterServiceOfferApi` via `MasterServiceOfferRole`.
- `MasterServiceOfferVariantApi` via `MasterServiceOfferVariantRole`.

## Tapir Migration Status

Implemented/current based on source:

- Tapir endpoint files exist for all listed API slices: `Category`, `Service`, `Master`, `MasterLocation`, `MasterServiceOffer`, `MasterServiceOfferVariant`, `Ladder`, and `Profile`.
- `LeaderboardPlugin.modules.api` (rollback/legacy) and `modules.apiBase[IO]` (current default) each bind `*TapirEndpoints` singletons and `*Api` adapters.

Documentation drift:

- `docs/LOCAL_LLM_TAPIR_HTTP_REFERENCE.md` says the current migrated slices are only `LadderApi`, `MasterApi`, and `ProfileApi`.
- Current source shows more Tapir endpoint slices than that doc lists.

## Error And Legacy Helpers

Implemented/current:

- `HttpApiFailure.scala` defines typed HTTP failures used by Beauty single-entity GET adapters.
- `HttpApiFailureTapirSupport.scala` provides Tapir failure output support.
- `LegacyJsonResponse.scala` centralizes optional-as-JSON compatibility behavior.
- `TapirHttpSupport.scala` centralizes Tapir/http4s route interpretation and preserves project-specific malformed path/body/exception behavior according to `docs/LOCAL_LLM_TAPIR_HTTP_REFERENCE.md`.

## Single-Entity GET Contracts

Implemented/current based on docs and source searches:

- `docs/http-legacy-json-contracts.md` says no Beauty single-entity GET endpoints remain on the legacy `200 OK` plus JSON `null` path.
- It lists `MasterApi`, `CategoryApi`, `ServiceApi`, `MasterLocationApi`, `MasterServiceOfferApi`, and `MasterServiceOfferVariantApi` as migrated to typed entity JSON with `404` typed error JSON for missing entity.
- Source searches found `HttpApiFailure.NotFound.*` usage in Beauty API adapters.

Stale-doc warning:

- `docs/http-master-service-offer-variant-typed-get-plan.md` says `MasterServiceOfferVariantApi` is the last remaining Beauty legacy single-entity GET endpoint and still uses raw `Json` with `200 + null` missing-entity behavior.
- That appears stale relative to `docs/http-legacy-json-contracts.md` and current source/tests. Treat it as historical migration plan, not current architecture, unless a future line-level audit proves otherwise.

## Beauty Search HTTP Route

Contract skeleton/current:

- `BeautySearchTapirEndpoints.scala` defines a pure, unwired Beauty search Tapir contract.
- Contract path/method: `POST /beauty-search`.
- Request body: `UserSearchInput` JSON with `query`, `userLat`, `userLon`, and `limit`.
- Response body: `BeautySearchResponse` JSON preserving the existing variant carousel, provider carousel, service intent carousel, facets, and inferred filters model.
- Error output uses the existing `HttpApiFailureTapirSupport.endpointBase` non-single-entity behavior; route contract tests currently pin backend/query failure as `500` with an empty body through existing support.
- Empty search responses are explicit arrays for all carousel/facet/filter fields, not `null`.
- Diagnostics and routing metadata are not exposed by this contract skeleton; any diagnostics API remains future design.
- `BeautySearchApi.scala` is a thin, unwired API adapter that bridges `BeautySearchTapirEndpoints` to `BeautySearchService[F]`.
- `BeautySearchApiHttpContractSuite.scala` exercises the adapter route through a test-local fake `BeautySearchService` only.
- `BeautySearchServiceBindingSpec.scala` proves a focused test-only Distage module can assemble `BeautySearchService.Impl[IO]` with `BeautySearchSpecV1.spec`, `BeautySearchIntentParser`, and a fake in-memory `BeautySearchBackend[IO]`.
- The binding proof exercises parser handoff, successful backend response pass-through, and backend `QueryFailure` pass-through without using `BeautySearchApi`, `LeaderboardPlugin`, HTTP routes, Qdrant, hybrid search, Elasticsearch, repository snapshots, or Docker.
- `BeautySearchCatalogBackendReadinessSpec.scala` proves a test-only catalog snapshot/in-memory backend readiness boundary: seed data is loaded through `BeautyQSeedLoader.ResourceLoader`, converted with `BeautySearchCatalogSnapshot.fromSeedData`, flattened with `VariantSearchDocumentBuilder.build`, wrapped in an explicit ready-document handle, and only then used to construct `InMemorySearchBackend` and `BeautySearchService.Impl`.
- `BeautySearchReadyCatalogDocuments` is now the src/main ready-catalog document helper; it carries an explicit source label and a document list, rejects empty source labels, rejects empty document lists, preserves documents unchanged, and makes no timestamp/freshness/refresh claim.
- `BeautySearchCatalogBackendModules.seedResourceInMemory[F]` is a small src/main opt-in module that builds `BeautySearchService.Impl[F]` with `InMemorySearchBackend[F]` from explicit ready catalog documents produced from a provided `BeautyQSeedLoader`.
- `BeautySearchCatalogBackendFactory.fromSeedLoader` keeps the seed resource to snapshot to document to ready-document assembly as a typed `Either[QueryFailure, BeautySearchReadyCatalogDocuments]`; the Distage module is only a thin explicit adapter over that factory.
- `BeautySearchCatalogBackendModuleSpec.scala` proves the opt-in module can materialize ready documents, `InMemorySearchBackend[IO]`, and `BeautySearchService.Impl[IO]` and answer a simple search when the module and a seed resource loader are explicitly included.
- The catalog backend readiness proof rejects empty ready documents before backend construction and uses the explicit source label `seed-resource-loader`.
- The readiness proof does not use `BeautySearchApi`, `LeaderboardPlugin`, HTTP routes, production DI modules, Qdrant, hybrid search, Elasticsearch, repository snapshots, Docker, startup indexing, or production backend bindings.
- `BeautySearchAppGraphBoundarySpec.scala` proves the complete Beauty search API/service/backend stack can be assembled only through a test-local explicit Distage module/composition.
- That test-local module binds `BeautySearchTapirEndpoints`, `TapirHttpSupport[IO]`, `BeautySearchApi[IO]`, `BeautySearchSpecV1.spec`, `BeautySearchIntentParser`, `BeautySearchService.Impl[IO]`, and a recording fake `BeautySearchBackend[IO]`; it targets a test-local stack root and issues one request through the assembled API.
- The app-graph boundary proof does not use `LeaderboardPlugin`, production app graph wiring, production DI modules, Qdrant, hybrid search, Elasticsearch, repository snapshots, seed loaders, Docker, startup indexing, or production backend bindings.
- `BeautySearchProductionInclusionActivation` and `BeautySearchProductionInclusionHandle` provide a disabled-by-default production inclusion boundary. This is a staging/helper boundary that still exists in src/main, but it is NOT the active production gate for the currently exposed `/beauty-search` route.
- `BeautySearchProductionInclusionBoundarySpec.scala` proves default Disabled, Disabled by-name API thunk safety, Disabled module exclusion of API/service/backend construction, and Enabled explicit test-local assembly with a fake backend.
- `BeautySearchProductionIncludeModuleSpec.scala` proves the include-module shape at a `BeautySearchProductionIncludedApis[F](apis: List[HttpApi[F]])` aggregation boundary: Disabled contributes no Beauty search API, while Enabled can explicitly contribute one `BeautySearchApi` through the inclusion handle.
- `BeautySearchProductionIncludedApis` is a src/main helper that converts an enabled `BeautySearchProductionInclusionHandle` to a local `HttpApi` list; it does not implement `HttpApi`, does not expose routes by itself, and `BeautySearchApi` was not added to production `many[HttpApi[F]]`.
- The old disabled inclusion boundary is a staging/helper boundary. It is not the active production gate for `/beauty-search`. The route is exposed through `LeaderboardPlugin` top-level via `modules.apiBase[IO]` plus `BeautySearchRouteModules.apiElasticsearch`, not by toggling the old inclusion handle. `modules.api[F]` / `seedCatalogInMemory[F]` / `InMemorySearchBackend` are rollback/legacy/non-default. A real kill switch / enable-disable route gate remains future hardening.
- `BeautySearchPluginModules.api[F]` is a src/main opt-in helper module that binds `BeautySearchTapirEndpoints`, `BeautySearchApi[F]`, and contributes `BeautySearchApi[F]` to a real `many[HttpApi[F]].weak[...]` set when explicitly included.
- `BeautySearchOptInHttpApiModuleSpec.scala` proves the opt-in module can contribute exactly one `BeautySearchApi[IO]` to the real `Set[HttpApi[IO]]` aggregation shape consumed by `HttpServer.Impl`, using the repo's role-style concrete API retention edge, a fake `BeautySearchService[IO]`, and no server startup.
- `BeautySearchRouteModules.seedCatalogInMemory[F]` is a src/main explicit opt-in end-to-end route module that composes `BeautySearchCatalogBackendModules.seedResourceInMemory[F]` with `BeautySearchPluginModules.api[F]` and supplies `BeautyQSeedLoader.ResourceLoader`.
- `BeautySearchOptInRouteModuleSpec.scala` proves that this composed module contributes exactly one `BeautySearchApi[IO]` to the same real `Set[HttpApi[IO]]` shape and can answer one `POST /beauty-search` smoke request from seed-resource catalog data without starting `HttpServer`.
- `LeaderboardPlugin` top-level now includes `modules.apiBase[IO]` plus `BeautySearchRouteModules.apiElasticsearch`, so `POST /beauty-search` is production-exposed in the default API graph via the ES-backed seed route.
- `BeautySearchProductionRouteExposureSpec.scala` proves the production API graph fragment (`LeaderboardPlugin.modules.apiBase[IO]` plus `BeautySearchRouteModules.apiElasticsearch`) contributes `BeautySearchApi[IO]` through the real `Set[HttpApi[IO]]` shape consumed by `HttpServer.Impl` and can answer one `POST /beauty-search` response through that assembled route set. It is not a full plugin-discovery proof.
- `BeautySearchProductionRouteLimitSpec.scala` is a targeted ES-backed route-module characterization through `BeautySearchRouteModules.apiElasticsearch` with local `TapirHttpSupport[IO]`, `BeautySearchTapirEndpoints`, `Async[Task]`, and a local Elasticsearch HTTP zero-hit stub:
  - `limit` 3 → `200 OK`, empty `variantCarousel`.
  - `limit` 0 → `200 OK`, empty `variantCarousel`.
  - `limit` -5 → `200 OK`, empty `variantCarousel`.
  - `limit` 100000 → `200 OK`, empty `variantCarousel` in the zero-hit stub.
- This is current route characterization, not a full validation or error policy. Zero and negative limits return `200 OK` with empty `variantCarousel`. The broader current route still caps huge limits by `BeautySearchSpecV1.spec.carouselSpec.variantSize`.

## Beauty Search Production Route Coordinate Behavior

Current targeted ES-backed characterization:

- `BeautySearchProductionRouteCoordinateSpec.scala` characterizes `POST /beauty-search` coordinate parameter behavior through `BeautySearchRouteModules.apiElasticsearch` with local `TapirHttpSupport[IO]`, `BeautySearchTapirEndpoints`, `Async[Task]`, and a local Elasticsearch HTTP zero-hit stub.
- Normal Hamburg coordinates (lat 53.57532, lon 10.07672) → `200 OK`, empty `variantCarousel`.
- Latitude 999.0 → `200 OK`, empty `variantCarousel`.
- Longitude 999.0 → `200 OK`, empty `variantCarousel`.
- Huge finite coordinates (lat 1e9, lon -1e9) → `200 OK`, empty `variantCarousel`.
- The current route does not validate coordinate ranges; all finite numeric values are accepted.

This is current behavior, not the desired final validation contract. The route currently lacks:

- Latitude/longitude range validation (e.g., lat must be -90..90, lon must be -180..180).
- Typed `4xx` error responses for invalid coordinate values.
- Structured error bodies describing coordinate validation failures.

## Beauty Search Production Route Query Behavior

Current targeted ES-backed characterization:

- `BeautySearchProductionRouteQuerySpec.scala` characterizes `POST /beauty-search` query text parameter behavior through `BeautySearchRouteModules.apiElasticsearch` with local `TapirHttpSupport[IO]`, `BeautySearchTapirEndpoints`, `Async[Task]`, and a local Elasticsearch HTTP zero-hit stub.
- Empty query string → `200 OK`, empty `variantCarousel`.
- Whitespace-only query string → `200 OK`, empty `variantCarousel`.
- Normal query text ("nails") → `200 OK`, empty `variantCarousel`.
- Very long query string (e.g., "nails " repeated 1000 times) → `200 OK`, empty `variantCarousel`.
- The current route does not enforce query length validation.

This is current behavior, not the desired final validation contract. The route currently lacks:

- Maximum query length enforcement.
- Typed `4xx` error responses for excessively long queries.
- Structured error bodies describing query validation failures.

Production-wired/current:

- No search role was found in `LeaderboardRole.scala`.
- `LeaderboardPlugin` top-level includes `modules.apiBase[IO]` plus `BeautySearchRouteModules.apiElasticsearch`.
- The ES-backed route module binds `BeautySearchService.Impl[F]` and `ElasticsearchSearchBackend[F]` through `BeautySearchCatalogBackendModules.seedResourceElasticsearch[F]`.
- The backend source is the startup seed-resource catalog snapshot: `BeautyQSeedLoader.ResourceLoader` loads seed JSON, `BeautySearchCatalogSnapshot.fromSeedData` builds the catalog snapshot, `VariantSearchDocumentBuilder.build` flattens it, `BeautySearchReadyCatalogDocuments` marks it ready, and `ElasticsearchSearchBackend[F]` serves it.
- This production exposure is lexical/simple/catalog-first over Elasticsearch. It is not Qdrant and not hybrid.
- `modules.api[F]` / `seedCatalogInMemory[F]` / `InMemorySearchBackend` are retained as rollback/legacy/non-default composition.
- No repository-backed production snapshot wiring, Qdrant lifecycle, hybrid routing, fallback, reranking, score fusion, startup indexing, or benchmark-driven routing policy is added.
- No production route switch, no `HybridServe`, and no Qdrant auto-supplement are added.
- Startup seed-resource snapshot readiness is the only readiness behavior in this include.
- Production freshness, refresh, staleness bounds, runtime catalog replacement, source-of-truth reconciliation, stale-catalog observability, and kill-switch behavior remain unresolved.
- There is no runtime refresh or replacement policy yet.
- Benchmark decisions do not affect routing.
- `LeaderboardPlugin` top-level is the real production API aggregation point: `modules.apiBase[IO]` binds base/core API adapters and `HttpServer`, and `BeautySearchRouteModules.apiElasticsearch` contributes the ES-backed Beauty route to the combined `HttpApi` set.
- Because `BeautySearchRouteModules.apiElasticsearch` contributes `BeautySearchApi[F]` to that set, `POST /beauty-search` is now production-exposed through the default API graph.
- Route availability must not infer Qdrant, hybrid, fallback, reranking, or score fusion.

Conclusion:

- Beauty search now has production route exposure through `LeaderboardPlugin` top-level via `modules.apiBase[IO]` plus `BeautySearchRouteModules.apiElasticsearch`.
- The exposed backend is seed-resource catalog snapshot + `ElasticsearchSearchBackend`; it is not Qdrant or hybrid.
- `modules.api[F]` / `seedCatalogInMemory[F]` / `InMemorySearchBackend` are rollback/legacy/non-default.
- The route JSON contract and search semantics remain the existing `BeautySearchTapirEndpoints`/`BeautySearchSpecV1` behavior.
- The next production hardening step is observability, freshness/staleness, runtime refresh/replacement, and kill-switch design, not Qdrant/hybrid work.

Future implementation boundary:

- Future production work still needs explicit decisions for freshness behavior, timeout behavior, observability, diagnostics visibility, runtime refresh/replacement, and rollback/disable behavior.
- Qdrant/hybrid remain non-production/manual-local/experimental and are not selected by this production include.

## Beauty Search Route Error Behavior

Current targeted ES-backed characterization:

- `BeautySearchProductionRouteErrorSpec.scala` characterizes invalid `POST /beauty-search` behavior through `BeautySearchRouteModules.apiElasticsearch` with local `TapirHttpSupport[IO]`, `BeautySearchTapirEndpoints`, `Async[Task]`, and a local Elasticsearch HTTP stub that is not used for body decode failures.
- Malformed JSON body → `500 InternalServerError`, empty body.
- Empty body → `500 InternalServerError`, empty body.
- Wrong `limit` type (string instead of integer) → `500 InternalServerError`, empty body.
- Missing required field (`query`) → `500 InternalServerError`, empty body.

This is current behavior, not the desired final validation contract. The route currently lacks:

- Typed `4xx` error responses (e.g., `400 BadRequest` for malformed JSON, missing fields, type mismatches).
- Structured error bodies with error codes and messages.
- Request validation (query length limits, lat/lon range validation).
- Freshness/staleness reporting at the route boundary.
- Observability surface (logging, metrics, tracing for bad requests).
- Kill-switch behavior that would short-circuit the route with a structured response.
- Qdrant/hybrid serving behavior, fallback, score fusion, reranking, `HybridServe`, or Qdrant auto-supplement.

## Contract Tests

Architectural source of truth:

- `CategoryApiHttpContractSuite.scala`
- `ServiceApiHttpContractSuite.scala`
- `MasterApiHttpContractSuite.scala`
- `MasterLocationApiHttpContractSuite.scala`
- `MasterServiceOfferApiHttpContractSuite.scala`
- `MasterServiceOfferVariantApiHttpContractSuite.scala`
- `LadderApiHttpContractSuite.scala`
- `ProfileApiHttpContractSuite.scala`
- `LegacySingleEntityGetHttpContractSuite.scala`
- `TapirHttpSupportContractSuite.scala`
- `BeautySearchApiHttpContractSuite.scala`

What they protect:

- Exact path behavior.
- JSON body shape.
- Missing-entity status/body behavior.
- Malformed path/body behavior where pinned by Tapir support tests.
- Adapter delegation behavior through controlled contract state objects.

Rule for future docs/edits:

- For HTTP behavior, route-level contract tests are the source of truth. Do not update docs based only on adapter intuition.
- Beauty search HTTP behavior is defined by the pure Tapir endpoint, route-level contract tests, and the default `LeaderboardPlugin` top-level include of `modules.apiBase[IO]` plus `BeautySearchRouteModules.apiElasticsearch`.
- Production runtime exposure is seed-resource catalog snapshot + `ElasticsearchSearchBackend`; `modules.api[F]` / `seedCatalogInMemory[F]` / `InMemorySearchBackend` are rollback/legacy/non-default; do not infer Qdrant, hybrid, fallback, reranking, or score fusion from route availability.
