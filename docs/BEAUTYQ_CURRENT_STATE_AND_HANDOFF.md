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
* Route exposure exists. M5 is closed as a bounded startup-readiness lifecycle checkpoint; runtime route-gate, replacement/freshness/rollback, and full lifecycle operations remain intentionally separate future tracks.
* `seedCatalogInMemory` remains available as rollback/non-default.

## 2. Current route behavior characterization

Documented as characterized, not as desired final contract:

* The public request contract is named `BeautySearchRequestContract`: `query` must be non-blank; `limit` must be within `1..BeautySearchRequestContract.MaxLimit` (`MinLimit` is `1`); optional latitude must be within `BeautySearchRequestContract.MinLatitude..BeautySearchRequestContract.MaxLatitude`; and optional longitude must be within `BeautySearchRequestContract.MinLongitude..BeautySearchRequestContract.MaxLongitude`.
* `BeautySearchRequestContract.MaxLimit` is source-backed by `BeautySearchSpecV1.spec.carouselSpec.variantSize`; API code, tests, and docs use the request-contract name as the public surface.
* Empty/whitespace-only queries, non-positive limits, limits above the carousel maximum, and out-of-range coordinates return structured JSON `400 BadRequest` failures with stable `code` and `message` fields before BeautySearch service or Elasticsearch calls.
* Malformed JSON / empty body / wrong limit type / missing required fields also use Tapir defaults and return `400 BadRequest` before BeautySearch service or Elasticsearch calls.
* Backend/query failures remain separate endpoint-domain failures and return `500 InternalServerError` with an empty body.
* Public `POST /beauty-search` request/response examples now live in [docs/codebase-review/04-api-and-http-contracts.md](codebase-review/04-api-and-http-contracts.md).
* The request contract is `BeautySearchRequestContract`. Semantic failures use structured JSON. Decode failures remain Tapir defaults.
* No maximum query length is enforced in this slice.
* Coordinates remain independently optional; the endpoint does not require a latitude/longitude pair.
* `BeautySearchReadyCatalogDocuments` rejects blank source / empty document list.
* BeautySearch public contract is now documented/frozen in `04-api-and-http-contracts.md`; the regression checklist lives in `06-tests-and-contracts.md`; no serving/backend behavior changed.

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

## 5. Current priority: closed M6 foundation and post-M6 planning

* Default production `/beauty-search` is now ES-backed over the seed catalog.
* ES seed-route checkpoint is reached. Plain `sbt test` is the canonical full verification command.
* Latest user-verified plain `sbt test` (Jun 13, 2026): 963 succeeded, 0 failed, 1 canceled.
* Business demo ready: runbook, query inventory, and smoke spec all in place.
* ES-native + Qdrant-native benchmark/eval comparison remains offline/eval-only.
* ES and Qdrant may advance together only in eval/benchmark, not serving.
* Production serving remains:

  ```text
  current ES seed route (default)
  → Qdrant production-candidate readiness only if contract parity, indexing/search readiness, quality/eval gates, observability, rollback/disable controls, and explicit activation policy are satisfied
  → controlled hybrid only after direct production-candidate readiness and explicit serving policy approval
  ```

* The production route does eager seed index preparation during route composition, but that is still not a production-grade ES lifecycle policy.
* `ElasticsearchSeedLifecycleMetadata` is a non-serving DI/readiness metadata seam exposed via `ElasticsearchSeedIndexReadiness.lifecycleMetadata` and `ElasticsearchSeedSearchComposition.lifecycleMetadata`; it is not a production lifecycle implementation.
* `ElasticsearchProductionReadinessState` is an internal non-serving state derived from that metadata. It records serving readiness `NotEnforced`, replacement `NotConfigured`, freshness `NotTracked`, refresh `EagerSeedPreparationOnly`, rollback `NotConfigured`, and operator visibility `NotExposed`.
* `ElasticsearchLifecycleStatusResponse` is a pure non-serving model/encoder derived from that state. It is not DI-bound or exposed through HTTP.
* `ElasticsearchStartupReadinessTransition` is a pure, unbound startup-preparation result model. Prepared transitions derive the same non-serving status response as direct readiness-state projection; failed transitions retain source-backed `QueryFailure.OperationFailure` details without lifecycle metadata or a status response. Both record `ElasticsearchStartupServingDecision.NotEnforced`; neither gates serving or changes preparation/composition behavior. Successful compositions now expose a prepared transition through `ElasticsearchSeedSearchComposition.startupReadinessTransition`, and `BeautySearchCatalogBackendModules.seedResourceElasticsearch` binds it through DI. Source-backed initializer failure paths are classifiable into `PreparationFailed` in pure tests without changing initializer behavior. No serving enforcement exists.
* `ElasticsearchStartupReadinessStatusResponse` is a pure non-serving startup status projection from `ElasticsearchStartupReadinessTransition`. Prepared projections include the nested `ElasticsearchLifecycleStatusResponse`; failed projections expose operation/message only, without lifecycle metadata or status response fields. It is HTTP-exposed only through the explicit opt-in/internal operator module `BeautySearchRouteModules.apiElasticsearchWithOperatorVisibility`; it is not exposed by the default ES graph.
* Cross-model consistency coverage (`ElasticsearchReadinessConsistencySpec`) proves field-level agreement across readiness state, lifecycle response, startup transition, startup status projection, and composition-derived projections. This is non-serving test coverage, not serving-gate enforcement.
* Focused ES-backed route/module graph specs prove that lifecycle metadata and the derived readiness state are materialized through the current route graphs. M5 is closed as a bounded startup-readiness lifecycle checkpoint; runtime route-gate, replacement/freshness/rollback, and full lifecycle operations are intentionally separate future tracks.
* Current route-module state coverage documents the seed-only gaps; it does not enforce readiness or change serving behavior.
* The lifecycle status field shape and encoder are implemented as non-serving code and documented in `docs/codebase-review/ES_LIFECYCLE_STATUS_DESIGN.md`, but no endpoint, route path, HTTP status policy, or operator policy is implemented.
* Detailed milestone status and priority order for lifecycle/eval/direct-Qdrant-candidate/hybrid work live in [docs/codebase-review/07-current-gaps-and-roadmap.md](codebase-review/07-current-gaps-and-roadmap.md).
* **M4 is closed.** HTTP/BeautySearch contract stabilized and frozen; production route exposure documented/tested; bad-input/default decode/semantic structured errors documented/tested; typed-GET/legacy JSON docs pruned or canonicalized; public examples/freeze/checklist recorded. Startup serving-gate design documented in `ES_STARTUP_SERVING_GATE_DESIGN.md`.
* **M5 is closed as a bounded startup-readiness lifecycle checkpoint.** App-start fail-closed, prepared-serving, non-serving lifecycle metadata/readiness/status/transition/projection seams, DI/rooting, failure classification, and consistency coverage. Startup serving-gate design documented. Source-confirmed implementation slice analysis completed. `ElasticsearchAppStartServingGateSpec` covers composition-level and DI-graph-level fail-closed and prepared-serving; these tests document current implicit behavior only and are not runtime HTTP 503 gate tests. Remaining ES production-lifecycle work is intentionally split into runtime route-gate, replacement/freshness/rollback, and full lifecycle operations tracks. Post-M5, the ES operator visibility track Design A is implemented and hardened as an explicit opt-in/internal module: `GET /ops/beauty-search/lifecycle` is available only through `BeautySearchRouteModules.seedCatalogElasticsearchWithOperatorVisibility` / `apiElasticsearchWithOperatorVisibility`, returns `ElasticsearchStartupReadinessStatusResponse.Prepared` with nested `ElasticsearchLifecycleStatusResponse`, uses `200 OK`, requires no new ES calls, and does not change `/beauty-search`. Default `LeaderboardPlugin.modules.apiBase[IO] + BeautySearchRouteModules.apiElasticsearch` does NOT expose the endpoint, and neither does the in-memory graph. Design B (bootstrap failure status) and Design C (replacement/freshness/rollback-rich status) remain future. The runtime route-gate policy chose Candidate A: keep app-start fail-closed and defer runtime HTTP gating until a runtime readiness source or replacement/freshness/rollback policy exists. See `docs/codebase-review/M5_ES_LIFECYCLE_CHECKPOINT.md` for the closed checkpoint.
* Full verification is separate from focused validation; full `sbt test` was not run from this docs pass.
* **M6 closed: Qdrant production-candidate readiness foundation.** The five accepted source-backed pure chunks are the readiness foundation, quality/parity gate, activation-policy foundation, indexing/search readiness adapters, and observability plus rollback/disable readiness adapters. The closeout source-confirmation coverage is `QdrantProductionCandidateM6CloseoutSpec`.
* M6 closure means a source-backed pure readiness foundation with eight represented categories: collection/identity, contract parity, indexing, search, quality/eval, observability, rollback/disable, and activation policy. It includes conservative defaults, the quality/eval gate foundation, activation-policy foundation, indexing/search adapters, and observability plus rollback/disable adapters.
* The conservative default records Qdrant as active but not production-candidate-ready: quality/eval is `NotEvaluated`, observability and rollback/disable are `NotConfigured`, activation policy is `NotApproved`, and other unproven categories are `Unknown`. `productionCandidateReady` becomes true only when Qdrant is active and every required category is explicitly `Ready`.
* `QdrantProductionCandidateIndexingReadiness` records expected, prepared, and optional indexed document counts, collection-identity readiness, embedding/vector evidence, and deterministic blocking reasons. `QdrantProductionCandidateSearchReadiness` records semantic backend/search contracts, candidate assembly, response projection, BeautySearch contract parity, and deterministic blocking reasons. Missing reports map to `Unknown`; only complete evidence maps to `Ready`.
* `QdrantProductionCandidateObservabilityReadiness` requires readiness/status, quality/eval, and activation-decision reports. `QdrantProductionCandidateRollbackReadiness` requires a disable control, rollback path, and no-regression evidence. Missing reports map to `NotConfigured`; incomplete reports map to `NotReady` with deterministic reasons; complete reports map to `Ready`. Neither adapter requires shadow telemetry or traffic mirroring, and rollback readiness explicitly does not approve production-route activation.
* The quality gate adapts `EngineEvalAggregateReport` into evaluated-query count, ES baseline recall, Qdrant candidate recall, and Qdrant noise evidence. Its explicit rule requires minimum query coverage, bounded recall deficit, and bounded noise, and records an explicit parity outcome. Missing reports map to `NotEvaluated`, failed gates to `NotReady`, incomplete evidence to `Unknown`, and passed gates to `Ready`.
* The activation policy makes explicit approval, scope, route/serving approval, rollback/disable controls, no-regression evidence, observability, and blocking reasons testable. Its scopes are no activation, candidate readiness only, future explicit opt-in route only, and future production route not approved here. Missing policy maps to `NotApproved`; satisfied candidate-only controls can map activation readiness to `Ready`; production-route activation remains blocked here.
* The foundation adapts existing collection compatibility and eval metrics rather than duplicating them. M6 closure is not production serving, a route switch, fallback, score fusion, reranking, `HybridServe`, Qdrant auto-supplement, shadow serving, production traffic mirroring, or production-route activation approval. No Qdrant serving route, hybrid serving, or `/beauty-search` behavior change was added.
* Coordinator progress snapshot: current milestone M6 is closed; maximum mapped milestone is M8; accepted M6 code chunks are 5; rough M6 implementation remaining is 0, with only coordinator/user full verification if desired; rough remaining to M8/project closeout is about 15–20 large commits, depending on activation and hybrid decisions.
* M7 remains future-only and conditional, likely activation/source-confirmation and policy work before any serving. M8 remains future-only and conditional controlled hybrid serving; it is not automatic. Runtime serving-gate work stays deferred until a runtime readiness source or replacement/freshness/rollback policy exists.
* Recommended next large chunk: define the bounded M7 activation/source-confirmation and serving-policy decision slice without adding a route or changing production serving.
* `seedCatalogInMemory` remains available as rollback/non-default.
* Simulated hybrid is offline benchmark/eval only.

## 6. M-ESQ-EVAL

**M-ESQ-EVAL** = measured Elasticsearch-native + Qdrant-native evaluation comparison.

### Status

* This remains offline/eval-only.
* `roleDeltas:`, `queryDeltas:`, query-class classification, query-class sidecars, and `classDeltas:` are part of the current expanded checkpoint.
* Validated class-sidecar replay exists for `benchmark-small -> benchmark-large`.
* Saved aggregate JSON schema remains unchanged.
* `EngineEvalAggregateReport` remains unchanged.
* `EngineEvalReportJson` remains unchanged.
* Detailed contracts and evidence stay in [docs/codebase-review/06-tests-and-contracts.md](codebase-review/06-tests-and-contracts.md).
* Detailed milestone positioning stays in [docs/codebase-review/07-current-gaps-and-roadmap.md](codebase-review/07-current-gaps-and-roadmap.md).

### Boundaries

* This is offline/eval-only. The production `/beauty-search` route remains ES seed route.
* The route already performs eager seed index preparation during route composition, but that is not a production-grade ES lifecycle policy.
* `ElasticsearchSeedLifecycleMetadata` and `ElasticsearchProductionReadinessState` remain non-serving internal seams, not lifecycle completion.
* M6 is closed as the pure source-backed Qdrant production-candidate readiness foundation. The conservative default is not ready; candidate-readiness-only activation can become `Ready` without approving production serving.
* Shadow serving and production traffic mirroring are not readiness prerequisites. M7 activation/policy work and M8 controlled hybrid serving remain future-only and conditional on separate serving approval.
* No route switch, fallback, score fusion, reranking, HybridServe, or Qdrant auto-supplement from benchmark results.
* Benchmark output is decision support, not production automation.
* Qdrant runtime and hybrid experiments remain non-production/manual/local/test boundaries; the closed pure Qdrant readiness foundation is not production wiring.
* This is not production readiness and not routing approval.

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

## 6.6. Forbidden paths

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
* Full `sbt test` is user responsibility unless explicitly delegated.

## 9. Where to read next

* `AGENTS.md` — stable repo guardrails
* `docs/codebase-review/README.md` — architecture entrypoint
* `docs/codebase-review/05-search-and-retrieval-architecture.md` — search DSL, ES, Qdrant, hybrid, eval
* `docs/codebase-review/07-current-gaps-and-roadmap.md` — current gaps and roadmap
* `docs/codebase-review/INVENTORY.md` — active inventory, including docs pruning candidates
* `docs/search-dsl-hybrid-v1-plan.md` — hybrid V1 plan and B-lite strategy
* `docs/beautyq-search-dsl-v1.md` — search DSL V1 and eval coverage
* `docs/local/COORDINATOR_WORKFLOW_AND_PROMPTING.md` — coordinator workflow, prompt packaging, source-truth gating, closeout, docs ownership, and model recommendation guidance

Future work should move in larger code/test chunks that unlock downstream behavior; use 5.5-med/Codex for those slices, and update docs inside the same delivery rather than as standalone docs-only cleanup unless the roadmap is inconsistent.

Coordinator workflow rules, including source-truth gating, prompt packaging, closeout, documentation ownership, and model recommendation guidance, live in `docs/local/COORDINATOR_WORKFLOW_AND_PROMPTING.md`.
