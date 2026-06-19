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
- `BeautySearchAppGraphBoundarySpec`: focused test-only Distage app-graph boundary proof that assembles `BeautySearchTapirEndpoints`, `BeautySearchApi[IO]`, `BeautySearchService.Impl[IO]`, `BeautySearchIntentParser`, `BeautySearchSpecV1.spec`, and a fake `BeautySearchBackend[IO]` only through a spec-local module/composition.
- `BeautySearchProductionInclusionActivation`: disabled-by-default production inclusion activation boundary. This is a staging/helper boundary; it is NOT the active production gate for `/beauty-search`.
- `BeautySearchProductionInclusionHandle`: optional API handle that does not implement `HttpApi` and does not expose routes by itself.
- `BeautySearchProductionInclusionBoundarySpec`: focused proof that Disabled avoids evaluating/constructing the API/service/backend graph and Enabled can explicitly assemble the stack in a test-local module.
- `BeautySearchProductionIncludeModuleSpec`: focused test-only proof that an include module can remain disabled by default at an API aggregation boundary.
- The include-module proof uses a src/main `BeautySearchProductionIncludedApis[F](apis: List[HttpApi[F]])` helper that converts an enabled `BeautySearchProductionInclusionHandle` to a local `HttpApi` list; Disabled contributes no Beauty search API, and Enabled can explicitly contribute one `BeautySearchApi`. This helper does not implement `HttpApi`, does not expose routes by itself, and `BeautySearchApi` was not added to production `many[HttpApi[F]]`.
- The old disabled inclusion boundary is a staging/helper boundary. It is not the active production gate for `/beauty-search`. The route is exposed directly by `LeaderboardPlugin` top-level through `modules.apiBase[IO]` plus `BeautySearchRouteModules.apiElasticsearch`. `seedCatalogInMemory` remains available as rollback/non-default. A real kill switch / enable-disable route gate remains future hardening.
- `BeautySearchPluginModules.api[F]`: opt-in src/main helper that binds `BeautySearchTapirEndpoints`, `BeautySearchApi[F]`, and contributes `BeautySearchApi[F]` to a real `many[HttpApi[F]].weak[...]` set only when explicitly included.
- `BeautySearchOptInHttpApiModuleSpec`: focused proof that the opt-in module contributes exactly one `BeautySearchApi[IO]` through the real `Set[HttpApi[IO]]` aggregation shape consumed by `HttpServer.Impl`, with the repo's role-style concrete API retention edge and a fake `BeautySearchService[IO]` that is not called during graph construction.
- `BeautySearchRouteModules.seedCatalogInMemory[F]`: explicit opt-in end-to-end route module that composes `BeautySearchCatalogBackendModules.seedResourceInMemory[F]` with `BeautySearchPluginModules.api[F]` and supplies the seed resource loader.
- `BeautySearchOptInRouteModuleSpec`: focused proof that the composed opt-in route module contributes exactly one `BeautySearchApi[IO]` through the real `Set[HttpApi[IO]]` shape and serves one successful non-empty `POST /beauty-search` response from the seed-resource catalog/in-memory backend without `HttpServer`, `LeaderboardPlugin`, Elasticsearch, Qdrant, hybrid, Docker, repository snapshots, or startup indexing.
- `LeaderboardPlugin` top-level includes `modules.apiBase[IO]` plus `BeautySearchRouteModules.apiElasticsearch` (ES-backed seed route, default), so `POST /beauty-search` is production-included in the default API graph.
- `BeautySearchProductionRouteExposureSpec`: focused proof that the default plugin API graph contributes `BeautySearchApi[IO]` through the real `Set[HttpApi[IO]]` shape and serves one successful non-empty `POST /beauty-search` response without `HttpServer`, Elasticsearch, Qdrant, hybrid, Docker, repository snapshots, or startup indexing.
- `BeautySearchProductionRouteLimitSpec`: characterizes `POST /beauty-search` limit validation through the ES-backed production route: accepted positive limits return `200 OK`; zero, negative, and above-carousel-maximum limits return Tapir's default `400 BadRequest`.
- `BeautySearchProductionRouteErrorSpec`: characterizes malformed or incomplete `POST /beauty-search` requests through the ES-backed production route: malformed JSON, empty body, wrong field types, and missing required fields return Tapir's default `400 BadRequest`.
- `BeautySearchProductionRouteCoordinateSpec`: characterizes coordinate validation through the ES-backed production route: coordinates within the endpoint bounds are accepted; latitude or longitude outside the configured ranges return Tapir's default `400 BadRequest`.
- `BeautySearchProductionRouteQuerySpec`: characterizes query validation through the ES-backed production route: non-blank query text is accepted; empty and whitespace-only queries return Tapir's default `400 BadRequest`. No maximum query-length validation is currently defined.

Production-wired/current:

- `LeaderboardPlugin` top-level includes `modules.apiBase[IO]` plus `BeautySearchRouteModules.apiElasticsearch` (ES-backed seed route, default).
- The backend source is seed-resource catalog snapshot readiness: `BeautyQSeedLoader.ResourceLoader` → `BeautySearchReadyCatalogDocuments` → `ElasticsearchSeedIndexInitializer` → `ElasticsearchSearchBackend[F]`.
- `ElasticsearchPortCfg` is loaded from config section `"elasticsearch"`.
- `seedCatalogInMemory` remains available as rollback/non-default.
- No repository snapshot wiring, startup indexing, fallback, reranking, score fusion, or benchmark-driven routing.
- `LeaderboardPlugin.modules.apiBase[IO]` plus `BeautySearchRouteModules.apiElasticsearch` is the real production API aggregation: `apiBase[IO]` binds base Tapir endpoint singletons, base API adapters, contributes them to `many[HttpApi[F]]`, and `HttpServer.Impl` serves the combined API set; `apiElasticsearch` adds the ES-backed Beauty route.

Design boundary:

- The implemented model/service/contract/adapter boundary is now production-exposed only through the ES-backed seed route.
- Do not infer Qdrant, hybrid, fallback, reranking, score fusion, or benchmark-driven routing from production route availability.
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
- `BeautySearchAppGraphBoundarySpec.scala` binds the complete Beauty search API/service/backend stack through a spec-local `ModuleDef` and targets a test-local stack root. The expecting backend validates the parser-produced input/intent and returns a scripted empty response; the spec also issues one request through the assembled `BeautySearchApi`. It does not use `LeaderboardPlugin`, production app graph magic, Qdrant, hybrid search, Elasticsearch, repository snapshots, seed loaders, Docker, startup indexing, or production DI modules.
- `BeautySearchProductionInclusionBoundarySpec.scala` binds only `BeautySearchProductionInclusionHandle.disabled[IO]` in the Disabled module and does not bind fake `BeautySearchApi`, `BeautySearchService`, or `BeautySearchBackend`; its Enabled module uses a fake backend and proves construction without backend calls. It does not use `LeaderboardPlugin`, production app graph wiring, Qdrant, hybrid search, Elasticsearch, repository snapshots, Docker, startup indexing, or production backend bindings.
- `BeautySearchProductionIncludeModuleSpec.scala` uses a src/main `BeautySearchProductionIncludedApis[IO]` helper to build an include result from `BeautySearchProductionInclusionHandle[IO]`. Its Disabled module binds only the disabled handle and contributes no APIs. Its Enabled module explicitly constructs `BeautySearchTapirEndpoints`, `BeautySearchApi[IO]`, `BeautySearchService.Impl[IO]`, `BeautySearchIntentParser`, `BeautySearchSpecV1.spec`, and a fake `BeautySearchBackend[IO]`, contributes exactly one `BeautySearchApi`, and proves backend construction does not call the backend. It does not use production `many[HttpApi[F]]`, `LeaderboardPlugin`, production app graph wiring, Qdrant, hybrid search, Elasticsearch, repository snapshots, Docker, startup indexing, or production backend bindings.
- `BeautySearchOptInHttpApiModuleSpec.scala` includes `BeautySearchPluginModules.api[IO]` in a test-local graph, supplies a fake `BeautySearchService[IO]`, roots a probe with a concrete `BeautySearchApi[IO]` retention dependency and a `Set[HttpApi[IO]]` aggregation dependency, and proves the real weak set contains exactly one `BeautySearchApi[IO]`. It does not use `LeaderboardPlugin`, `HttpServer`, Qdrant, hybrid search, Elasticsearch, seed loaders, repository snapshots, Docker, resources, startup indexing, or production backend bindings.

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
- That proof originally characterized startup readiness only.

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

- ES seed route is default: `BeautySearchRouteModules.seedCatalogElasticsearchPortConfigured` → `seedCatalogElasticsearch` → `ElasticsearchSearchBackend`.
- `ElasticsearchPortCfg` is loaded from config section `"elasticsearch"`.
- `ElasticsearchSeedIndexInitializer` prepares seed catalog in ES.
- `_refresh` is bodyless: `ElasticsearchSeedIndexInitializer` calls `ElasticsearchJsonClient.post(path)` (no body), because real Elasticsearch rejects `_refresh` with a JSON body.
- No production indexing lifecycle (repository-backed/live), startup reindex, aliases/blue-green, or production collection manager was found.

Architecture note:

- The current route performs eager seed index preparation during route composition. That is an implementation fact of the current ES-backed seed route, not an approved production lifecycle policy.
- Lifecycle design surfaces currently visible in the architecture are: seed loader / ready-document path, `BeautySearchReadyCatalogDocuments`, `ElasticsearchSeedIndexReadiness`, `ElasticsearchSeedSearchComposition`, `ElasticsearchHttpJsonClient`, and the route composition boundary that currently wires eager seed preparation into the exposed route.
- Those surfaces define where future production lifecycle decisions may land: catalog source of truth, lifecycle manager boundaries, alias/replacement policy, rollback, freshness/staleness metadata, runtime replacement triggers, and observability.
- The current ready-document path and ES readiness/init path are useful boundaries, but they do not by themselves define a production policy for refresh, replacement, stale-catalog handling, or route enable-disable behavior.
- `ElasticsearchSeedIndexReadiness.lifecycleMetadata` exposes the non-serving metadata surface for index name, source, document count, `EagerSeedIndexPreparation`, and `SeedOnlyNotProductionLifecycle`.
- `ElasticsearchSeedSearchComposition.lifecycleMetadata` forwards that readiness metadata through the composition boundary.
- `BeautySearchCatalogBackendModules.seedResourceElasticsearch` binds `ElasticsearchSeedLifecycleMetadata` from the composition so the metadata handle is available through DI without changing serving behavior.
- `ElasticsearchProductionReadinessState.seedOnly` derives a pure internal non-serving state from the lifecycle metadata. Its current values explicitly say serving readiness is `NotEnforced`, replacement is `NotConfigured`, freshness is `NotTracked`, refresh is `EagerSeedPreparationOnly`, rollback is `NotConfigured`, and operator visibility is `NotExposed`.
- `ElasticsearchSeedSearchComposition.productionReadinessState` exposes that derived value, and `BeautySearchCatalogBackendModules.seedResourceElasticsearch` binds it through the same ES seed route graphs.
- `ElasticsearchLifecycleStatusResponse.from` projects that state into the current pure response fields, with local Circe encoding and `productionLifecycleComplete = false`. The response model is HTTP-exposed only through the explicit opt-in/internal operator endpoint `GET /ops/beauty-search/lifecycle`; it is not exposed by the default ES graph or the in-memory graph.
- `ElasticsearchStartupReadinessTransition` is a pure, non-serving model for current startup preparation outcomes. `Prepared` preserves `ElasticsearchProductionReadinessState` and derives its lifecycle status response; `PreparationFailed` preserves source-backed `QueryFailure.OperationFailure` operation/message data without lifecycle metadata or a status response. Both record `ElasticsearchStartupServingDecision.NotEnforced`.
- `ElasticsearchStartupReadinessStatusResponse` is a pure, non-serving startup status projection from `ElasticsearchStartupReadinessTransition`. Prepared projections include the nested `ElasticsearchLifecycleStatusResponse`; failed projections expose operation/message only, without lifecycle metadata or status response fields. Local Circe encoding is provided. The projection is HTTP-exposed only through the explicit opt-in/internal operator endpoint `GET /ops/beauty-search/lifecycle`; it is not exposed by the default ES graph or the in-memory graph.
- `ElasticsearchSeedSearchComposition.startupReadinessTransition` exposes a prepared transition derived from the composition's production readiness state. This does not change how the composition is built and does not catch or change preparation failures.
- `BeautySearchCatalogBackendModules.seedResourceElasticsearch` binds `ElasticsearchStartupReadinessTransition` from the composition through the ES seed route graphs. The binding is non-serving and does not gate serving.
- Source-backed initializer failure paths (blank source, empty documents, ES client failure) are classifiable into `PreparationFailed` through pure `ElasticsearchStartupReadinessTransition.preparationFailed(...)` without changing initializer behavior.
- The transition model is now wired into `ElasticsearchSeedSearchComposition` and bound through DI, but it does not enforce startup readiness and does not change initializer, composition, route, or serving behavior.
- `ElasticsearchAppStartServingGateSpec` proves app-start fail-closed behavior at composition level (blank source, empty documents, ES client failure) and DI-graph level (ES client failure prevents `BeautySearchApi` construction). Also proves prepared-serving: successful composition produces `Prepared` transition and `POST /beauty-search` returns `200 OK`. These tests document current implicit behavior only; they are not runtime HTTP 503 gate tests.
- Focused route/module specs prove that both values are materialized through the targeted ES seed route, explicit ES seed route module, real HTTP-client ES route, port-configured default route, and production API graph. The same specs now also root the prepared `ElasticsearchStartupReadinessTransition` bound through DI.
- This state is seed-only, internal, and non-serving; it documents absent production lifecycle capabilities without completing or enforcing them.
- The lifecycle status response shape is documented in `docs/codebase-review/ES_LIFECYCLE_STATUS_DESIGN.md`. The non-serving model/encoder is implemented and HTTP-exposed only through the explicit opt-in/internal operator endpoint `GET /ops/beauty-search/lifecycle`; no serving behavior changes, runtime route gate, or HTTP 503 behavior are implemented.
- These are architecture surfaces only; this document does not assign milestone status or production lifecycle readiness.

### Remaining production ES lifecycle contract

Current route/module metadata coverage proves only:

- seed index metadata is available through DI via `ElasticsearchSeedLifecycleMetadata`;
- current production-readiness gaps are available through DI via `ElasticsearchProductionReadinessState`;
- the pure non-serving response can be derived from that state without DI or HTTP wiring;
- pure startup preparation success/failure shape can be represented without DI, effects, or serving enforcement;
- successful ES seed compositions expose a prepared startup transition derived from the readiness state;
- source-backed preparation failures are classifiable into `PreparationFailed` in pure tests without changing initializer behavior;
- no serving enforcement exists for startup readiness;
- eager seed preparation is wired through `ElasticsearchSeedIndexReadiness` / `ElasticsearchSeedSearchComposition`;
- lifecycle status is explicitly `SeedOnlyNotProductionLifecycle`;
- serving readiness is not enforced, replacement and rollback are not configured, freshness is not tracked, refresh is eager seed preparation only, and operator visibility is not exposed;
- route behavior is unchanged.

What this does not prove:

- startup readiness is enforced before serving;
- preparation failure handling is decided;
- replacement, freshness, refresh, rollback, or operator-visible production lifecycle status exists;
- any route switch, fallback, score fusion, reranking, `HybridServe`, or Qdrant auto-supplement behavior exists.

Remaining production lifecycle contract areas before full ES production lifecycle can be considered implemented (these belong to named future ES lifecycle tracks, not the closed M5 checkpoint):

- Startup readiness:
  - how the service decides the ES index is ready before serving;
  - what happens when preparation fails;
  - whether serving is blocked, degraded, or fails fast.
- Replacement:
  - how a new seed index replaces an old one;
  - whether replacement is atomic;
  - whether aliases or versioned index names are required.
- Freshness:
  - how the running service knows seed data version/freshness;
  - what timestamp/version/count is operator-visible;
  - how stale data is detected.
- Refresh:
  - when refresh happens;
  - whether refresh is manual, startup-only, scheduled, or externally triggered.
- Rollback:
  - how to return to the previous known-good index/version;
  - what state is kept to support rollback.
- Operator-visible status:
  - which status fields are exposed;
  - where they are exposed;
  - how they distinguish seed-only, preparing, ready, failed, stale, rollback, or disabled states.

Until those areas are defined and implemented, the current ES-backed route remains a seed-only readiness seam with eager preparation, not a production lifecycle.

The startup serving-gate policy design is documented separately in `docs/codebase-review/ES_STARTUP_SERVING_GATE_DESIGN.md`. It defines the policy choices (fail closed until prepared, fail fast on preparation failure, continue serving with seed-only status, serve stale/previous index, operator override) that must be approved before any serving-gate enforcement is implemented. The serving-gate design remains design-only; no runtime enforcement code, runtime route gate, or HTTP 503 behavior exists. Source-confirmed implementation slice analysis is in `docs/codebase-review/ES_STARTUP_SERVING_GATE_SOURCE_CONFIRMATION.md`: app-start fail-closed is implicitly implemented by eager composition; runtime route gate requires a different source seam; `BeautySearchApi.serverLogic` is the smallest candidate enforcement seam but enforcement is currently impossible because the DI-bound transition is always `Prepared`. Current recommendation is Candidate A from `docs/codebase-review/ES_RUNTIME_ROUTE_GATE_POLICY.md`: keep app-start fail-closed only and defer runtime HTTP gate until a runtime readiness source or replacement/freshness/rollback policy exists. The ES operator visibility track is now implemented as explicit opt-in/internal Design A: `GET /ops/beauty-search/lifecycle` is available only through `BeautySearchRouteModules.seedCatalogElasticsearchWithOperatorVisibility` / `apiElasticsearchWithOperatorVisibility`, returns `ElasticsearchStartupReadinessStatusResponse.Prepared` with nested `ElasticsearchLifecycleStatusResponse`, uses `200 OK`, requires no new ES calls, and does not change `/beauty-search`. Default `seedCatalogElasticsearch`, default `apiElasticsearch`, `LeaderboardPlugin.modules.apiBase[IO] + BeautySearchRouteModules.apiElasticsearch`, and `seedCatalogInMemory` do not expose the endpoint. Design B and Design C remain future. M5 is closed as a bounded startup-readiness lifecycle checkpoint.

Classification:

- Interpreters: implemented/current pure code.
- Test client and Docker integration: integration-test-only.
- Production runtime Elasticsearch search path: ES seed route is default; full lifecycle remains future work.

Future implementation boundary:

- Repository-backed/live indexing requires explicit index lifecycle, readiness, failure behavior, freshness, and observability design.

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

Local endpoint / resource-backed:

- `LlamaCppEmbeddingClient` is used by resource-backed specs that default to local endpoint `http://localhost:8081`; `LLAMA_CPP_EMBEDDING_URL` is an optional env override; specs cancel when the endpoint is unavailable.
- Manual local llama.cpp operation is documented with a `llama-server` command.

Integration-test-only / Docker-backed:

- `QdrantDockerSmokeSpec.scala` uses Distage `QdrantPortCfg` and a Docker-backed Qdrant container.
- `QdrantCollectionCompatibilityIntegrationSpec.scala` uses the distage-managed Qdrant Docker resource via `QdrantPortCfg` and runs automatically when that resource is available.
- `QdrantSnapshotIndexingCompatibilityIntegrationSpec.scala` uses the distage-managed Qdrant Docker resource via `QdrantPortCfg` and runs automatically when that resource is available.

Non-production Qdrant/hybrid runner status:

* Production `/beauty-search` is ES-backed seed route.
* Qdrant/hybrid is still non-production/manual-local.
* Manual runner layers now exist:

  * runner composition boundary;
  * manual lifecycle handle;
  * manual input boundary;
  * adapter-input boundary;
  * Qdrant-client input boundary;
  * real-client input boundary;
  * targeted Distage module-shape proof.
* Resource-backed real Qdrant smokes cover:

  * explicit indexing via `indexSnapshot()` with real Qdrant (no env gate; auto-runs when Qdrant is available);
  * explicit retrieval via `run(...)` with real Qdrant (no env gate; auto-runs when Qdrant is available).
* These smokes use real Qdrant, but not production route wiring.
* User-verified external-enabled full validation run was reported green.

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

## F2. Hybrid Control-Plane v0

Implemented:

- File: `leaderboard/search/hybrid/control/BeautySearchHybridControlPlane.scala`
- Value types: `BeautySearchHybridSnapshotIdentity`,
  `BeautySearchHybridCollectionIdentity`,
  `BeautySearchHybridFreshnessPolicy`,
  `BeautySearchHybridRuntimeMode` (`SeedCatalogOnly`, `HybridShadow`,
  `HybridServe`), `BeautySearchHybridServingPolicy`,
  `BeautySearchHybridReadinessStatus`,
  `BeautySearchHybridServingDecision`
- Interfaces: `BeautySearchHybridReadiness[F]`,
  `BeautySearchHybridDiagnosticsSink[F]`
- Diagnostics event:
  `BeautySearchHybridDiagnosticsEvent.DecisionEvaluated`
- Evaluator: `BeautySearchHybridDecisionEvaluator[F]`
- Spec: `BeautySearchHybridControlPlaneSpec.scala`

Conservative serving policy:

- Default: `SeedCatalogOnly` — keeps existing seed-catalog production path.
- `HybridShadow` — diagnostics/shadow only; must not affect user response.
- `HybridServe` — can serve only when readiness is `Ready`.
- `NotReady` — conservatively resolves to `UseSeedCatalogOnly` in v0.

Evaluator contract:

- `BeautySearchHybridDecisionEvaluator[F]` reads readiness, computes
  decision, reports diagnostics via `DecisionEvaluated`, returns decision.
- It does not run hybrid retrieval, indexing, Qdrant, Llama, HTTP, or
  route behavior.

Not production wiring:

- Control-plane types remain outside the production route (`modules.apiBase[IO]` + `apiElasticsearch`).
- Not route wiring.
- No Qdrant/Llama construction.

## F3. B-lite: ES-native + Qdrant-native Eval Architecture

Current architecture split:

```text
Production serving:
  current ES seed route (default)
  -> ES lexical baseline
  -> Qdrant shadow only if eval proves complement
  -> controlled hybrid only after readiness/kill-switch/policy

Eval/benchmark:
  ES-native eval and Qdrant-native eval appear early and together
  -> compare ES-alone, Qdrant-alone, simulated hybrid
  -> decide from metrics, not from architecture enthusiasm
```

Runtime hybrid module expansion is paused after the hidden control-plane module proof.
M-ESQ-EVAL remains offline/eval-only. Detailed milestone status and sequencing live in `docs/codebase-review/07-current-gaps-and-roadmap.md`.

### InMemorySearchBackend role

`InMemorySearchBackend` is a rollback/regression/pure backend, not current production default.

It is not:

* an in-memory Elasticsearch.
* the oracle for ES scoring/order/analyzer behavior.
* a semantic reference for Qdrant.

Elasticsearch should be designed from Elasticsearch primitives/capabilities:

* mappings, analyzers, bool/filter/range/geo queries, aggregations/facets, scoring/boosting, pagination/search_after, profile/debug where useful.

Qdrant should be designed from Qdrant primitives/capabilities:

* embedding text, model identity, dimension, distance, topK, scoreThreshold, payload filters, missing lookup handling, semantic complement/noise.

Product response projection adapts engine-native results into `BeautySearchResponse`.
Engines must not be forced to mimic the current in-memory backend.

### ES-native eval path

Current ES eval pieces include:

* `ElasticsearchSearchRequestInterpreter.request(spec, input, intent)` — ES request from spec.
* `ElasticsearchSearchResponseInterpreter.interpret` — ES response to `BeautySearchResponse`.
* `BeautySearchElasticsearchIntegrationSpec` — Docker-backed integration eval subsets.
* `ElasticsearchSearchResponseInterpreterSpec` — response interpreter unit tests.

The `EngineEval` comparison model and report/assembly layer are implemented. Detailed status and next-step sequencing belong to the roadmap.

ES-native eval measures:

* lexical recall by query class.
* filter/facet accuracy.
* price/duration constraint enforcement.
* geo constraint accuracy.
* exact attribute filtering.
* scoring/ranking sanity under fixed mapping/analyzer/query fixtures.

### Qdrant-native eval path

Qdrant-native eval uses:

* `QdrantSemanticCandidateBackend` — semantic candidate retrieval.
* `QdrantSemanticCandidateSearch` — query embedding + search.
* `QdrantEmbeddingBenchmark*` — embedding endpoint/model benchmarking.
* `QdrantSemanticCandidateEvalSpec` — semantic candidate eval.

Qdrant-native eval measures:

* semantic recall for broad/conversational queries.
* complement over ES misses.
* noise rate on hard negatives / structured queries.
* embedding quality by model/endpoint.

### Simulated hybrid (offline only)

Simulated hybrid belongs in benchmark/eval only.

Simulated hybrid:

* combines ES `EngineEvalResult` + Qdrant `EngineEvalResult` offline.
* must not imply route wiring.
* must not imply `HybridServe`.
* must not auto-supplement production responses.

Simulated hybrid computes:

* ES ∩ Qdrant overlap.
* Qdrant complement over ES misses.
* Qdrant noise rate on hard negatives.
* Simulated hybrid gain over ES-alone.

### Design concepts (implemented)

Implemented pure EngineEval model (full API and metric semantics in `docs/codebase-review/06-tests-and-contracts.md`):

* `EngineEval.scala`: `EngineEvalEngine` (Elasticsearch, Qdrant, SimulatedHybrid), `EngineEvalQueryClass`, `EngineExpectedRole`, `EngineEvalResult`, `EngineEvalComparisonMetrics`, `EngineEvalComparisonMetrics.from(...)`.
* `EngineEvalSpec.scala`: pure metric semantics including duplicate-id behavior.

M-ESQ-EVAL (= measured Elasticsearch-native + Qdrant-native evaluation comparison) remains offline/eval-only. The production route stays ES-backed seed route; Qdrant and hybrid remain non-serving. For milestone status and sequencing, use `docs/codebase-review/07-current-gaps-and-roadmap.md`.

### Test taxonomy for B-lite

* Atomic / Contractual: eval query classification model, engine expected role model, `EngineEvalResult` model (implemented), ES result normalization, Qdrant result normalization, complement/noise/overlap metric calculations (implemented via `EngineEvalComparisonMetrics.from`), simulated hybrid merge.
* Group / Contractual: fake ES executor → benchmark report, fake Qdrant executor → benchmark report, fake ES + Qdrant results → simulated hybrid report.
* Communication: ES Docker benchmark smoke, Qdrant Docker/Llama benchmark smoke, resource-backed auto-gated.
* Benchmark: ES-alone report, Qdrant-alone report, simulated-hybrid report, saved report comparison.

## G. Benchmarks / Eval

Implemented/current:

- Eval model and scorer: `leaderboard.search.eval.BeautySearchEval`.
- Eval inventory: `BeautySearchEvalInventory.scala`.
- Pure eval tests: `BeautySearchPureSpec.scala`.
- Elasticsearch eval integration: `BeautySearchElasticsearchIntegrationSpec.scala`.
- Qdrant benchmark model/runner/report/decision code: `QdrantEmbeddingBenchmark*.scala`.
- Saved report comparison: `QdrantEmbeddingBenchmarkSavedReportComparison.scala` and tests.

Resource-backed auto-gated:

- `QdrantSemanticCandidateEvalSpec.scala`: defaults to local endpoint `http://localhost:8081`; cancels when unavailable; optional quality assertions use `QDRANT_SEMANTIC_QUALITY_ASSERTIONS`.
- `QdrantEmbeddingBenchmarkExecutorIntegrationSpec.scala`: single endpoint defaults to `http://localhost:8081`, dual endpoints default to `http://localhost:8081` + `http://localhost:8082`; cancels when endpoints are unavailable; env vars remain as optional overrides.

Manual saved-report comparison:

- `QdrantEmbeddingBenchmarkSavedReportComparisonManualSpec.scala` is gated by `QDRANT_EMBEDDING_BENCHMARK_COMPARE_SAVED_REPORTS`, `QDRANT_EMBEDDING_BENCHMARK_LEFT_JSON`, and `QDRANT_EMBEDDING_BENCHMARK_RIGHT_JSON`.

Decision policy boundary:

- `QdrantEmbeddingBenchmarkDecisionPolicy` outputs verdicts such as keep baseline, worth further evaluation, worth switching, or rejected.
- Benchmark output is decision support, not production automation.
- No production code was found that consumes benchmark verdicts to switch models or routing.

## Current Production Search Status Summary

For full current state, see `docs/BEAUTYQ_CURRENT_STATE_AND_HANDOFF.md`.

Production-wired/current:

- `POST /beauty-search` is production-exposed through `LeaderboardPlugin` top-level via `modules.apiBase[IO]` plus `BeautySearchRouteModules.apiElasticsearch` (ES-backed seed route, default).
- The backend is seed-resource catalog snapshot readiness plus `ElasticsearchSearchBackend[F]`.
- This is lexical/simple/catalog-first. It is not Qdrant, not hybrid.
- `seedCatalogInMemory` remains available as rollback/non-default.
- No Qdrant/hybrid production binding found.
- No search role in `LeaderboardRole.scala`.

Implemented but mostly test/experiment exercised:

- Search DSL/spec, parser, document builder/snapshot loaders.
- Elasticsearch interpreters.
- Qdrant/vector components, hybrid/generic retrieval seams.
- Eval/benchmark code.

Service binding acceptance criteria for future implementation:

- Future production-grade backend migration must answer where the replacement `BeautySearchService`/`BeautySearchBackend` binding is selected and how it replaces or coexists with the current ES seed route.
- It must answer which `BeautySearchBackend` is bound first.
- It must define how catalog/index readiness and freshness/staleness are guaranteed.
- It must define how parser/backend failures are represented at the route boundary.
- It must show which route-level contract tests prove request/response/error behavior.
- It must define how diagnostics and observability are exposed.
