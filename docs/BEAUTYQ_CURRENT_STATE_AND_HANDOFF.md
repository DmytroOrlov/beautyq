# BeautyQ Current State and Handoff

Canonical handoff for new chats. Read this first, then see linked docs for deeper detail.

For doc ownership by topic, duplication hotspots, and future keep/merge/link decisions, use `docs/local/BEAUTYQ_DOCS_INVENTORY_AND_CONSOLIDATION_MAP.md`. This handoff remains the first read for current truth; the inventory decides which companion doc is canonical for each repeated topic.

## 0. Coordinator source truth

Use this section as the load-bearing source truth for coordination. Other docs should point here instead of restating the same status block.

* Full verification was already run by the user after option93/96: `1189` tests run, `1189` succeeded, `0` failed, `0` aborted, `1` canceled, `2` pending; `All tests passed`.
* The previous full-suite blocker was Distage `IncludesDSL$Include.interpret` NPE. Before the root fix, the suite had 55 aborted suites; the visible casualties included `QdrantEmbeddingBenchmarkExecutorIntegrationSpec` and `QdrantSemanticCandidateEvalSpec`.
* Known hazard: ad-hoc test-local composition through the broader `LeaderboardPlugin` route path, such as `include(LeaderboardPlugin.modules.api[IO])` or equivalent broad `apiBase` composition, can trigger that NPE in focused specs.
* Safe replacement pattern: test-local route probes now include source-confirmed route modules directly, using `BeautySearchRouteModules.apiElasticsearch` and, when operator visibility is needed, `BeautySearchRouteModules.apiElasticsearchWithOperatorVisibility`.
* Additional test-only stabilization: Qdrant resource specs snapshot `super.config` once before adding `Mode.Test` activation and `QdrantPortCfg` memoization roots.
* The NPE fix changed only test files. No production route, API, plugin, DI, or HTTP source changed. No route behavior changed. `/beauty-search` behavior did not change.
* Current production `POST /beauty-search` remains ES-backed through `LeaderboardPlugin.modules.apiBase[IO]` plus `BeautySearchRouteModules.apiElasticsearch`.
* Current production backend remains seed-resource catalog snapshot + `ElasticsearchSearchBackend`.
* M5 is closed as a bounded ES startup-readiness lifecycle checkpoint.
* M6 is closed as the Qdrant production-candidate readiness foundation.
* M7 is closed as the activation/source-confirmation and serving-policy planning foundation.
* ES post-M5 future-track planning is closed as planning only.
* Qdrant implementation approval is now granted only for disabled-by-default explicit opt-in route wiring.
* `BeautySearchRouteModules.apiQdrantExplicitOptIn` / `seedCatalogQdrantExplicitOptIn` implement that explicit route path. It is not included by `apiElasticsearch` or `LeaderboardPlugin`.
* Explicit opt-in Qdrant route construction requires a ready M6 production-candidate report, ready M7 activation-policy report, enabled config gate, approved no-regression evidence, observability/status evidence, rollback/disable control, and separate route/serving approval. These are offline/evidence prerequisites, not production traffic telemetry.
* Production route activation remains not approved.
* The activation decision boundary is documented separately in `docs/local/QDRANT_PRODUCTION_ACTIVATION_DECISION_CRITERIA.md`. Existing green evidence is sufficient for the disabled-by-default opt-in route only; it does not approve any future default `/beauty-search` switch.
* The broader ES + Qdrant + hybrid retrieval end-state roadmap is documented separately in `docs/local/BEAUTYQ_ES_QDRANT_HYBRID_RETRIEVAL_ROADMAP.md`.
* Future M8 production-telemetry planning and M9 offline-eval-harness planning are paired in `docs/local/M8_M9_TELEMETRY_AND_OFFLINE_EVAL_PLAN.md`; pure shared vocabulary/reporting contracts exist in `leaderboard.search.eval.M8M9EvalContracts`, the pure M9 saved dataset/report format plus deterministic markdown renderer exists in `leaderboard.search.eval.M9OfflineEvalSavedReport`, the pure static/in-memory M9 runner skeleton exists in `leaderboard.search.eval.M9OfflineEvalStaticRunner`, and canonical static fixtures plus a checked-in example markdown artifact exist in `leaderboard.search.eval.M9OfflineEvalStaticFixtures`, but telemetry emission, ES/Qdrant backend runners, route changes, hybrid serving, fusion, reranking, and production activation remain unimplemented.
* There is no real production traffic in this project context. Shadow serving and production traffic mirroring are therefore future-only, non-blocking, and not useful as current readiness evidence.
* Pending expectations map:
  * The former `5` pending expectations in `BeautySearchOptInRouteModuleSpec` are now active tests for the explicit opt-in route module and prerequisite gate.
  * The former `1` pending expectation in `BeautySearchProductionRouteExposureSpec` is now active coverage preserving that `POST /beauty-search` stays ES-backed until separate production-route activation approval exists.
  * `2` pending expectations in `ElasticsearchOperatorVisibilityEndpointPolicySpec` preserve future-only ES local/dev fallback and runtime route-gate / HTTP 503 work.
  * The canonical owner, per-expectation owners, approval conditions, activation conditions, and removal conditions live in `docs/codebase-review/06-tests-and-contracts.md`.
* The single canceled test remains expected manual/resource-gated coverage, not a blocker.

## 1. Current production `/beauty-search`

Current coordinator load-in:

* Production `POST /beauty-search` remains ES-backed.
* Current production route is exposed by `LeaderboardPlugin` through `modules.apiBase[IO]` plus `BeautySearchRouteModules.apiElasticsearch`.
* Current production backend remains seed-resource catalog snapshot + `ElasticsearchSearchBackend`.
* `seedCatalogInMemory` remains rollback/non-default support, not the active production graph.
* M5 is closed as a bounded ES startup-readiness lifecycle checkpoint.
* M6 is closed as the Qdrant production-candidate readiness foundation.
* M7 is closed as the activation/source-confirmation and serving-policy planning foundation.
* ES post-M5 future-track planning is closed as planning only and remains unimplemented.
* Disabled-by-default explicit opt-in Qdrant route implementation approval is granted and implemented through `BeautySearchRouteModules.apiQdrantExplicitOptIn`.
* Production route activation for Qdrant remains not approved.
* Any future default `/beauty-search` switch requires separate explicit production-route activation approval, a default graph exposure plan, rollback/disable plan, observability/status evidence, route exposure tests, and full-suite verification after implementation. No hidden fallback, fusion, reranking, shadow serving, or mirroring is approved as part of that activation boundary.

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
* Route exposure exists. M5 is closed as a bounded startup-readiness lifecycle checkpoint. The ES post-M5 planning aggregate is also closed: runtime route-gate remains deferred under Candidate A, replacement/freshness/rollback remains future, and full lifecycle operations remain future. Those are separate future tracks, not an unaccepted M5 remainder.
* Option77 clarified the current ES split: app-start behavior is fail-closed only; successfully constructed ES route graphs are prepared-serving; `BeautySearchApi` has no runtime readiness gate today; there is no runtime HTTP 503 gate, no stale/previous index state, no alias/versioned-index replacement policy, no freshness/staleness policy, no refresh semantics beyond eager seed preparation, no rollback policy, and no runtime lifecycle operations implementation.
* Full lifecycle operations remain future-only. No runtime rebuild/refresh operation, replacement activation operation, rollback operation, disable/kill-switch operation, stale/current/previous catalog operation state, operator-triggered lifecycle command surface, or auth/config/visibility policy beyond explicit opt-in/internal Design A exists today.
* `seedCatalogInMemory` remains available as rollback/non-default.

## 1.5. ES post-M5 planning aggregate

Planning closeout only:

* M5 stays closed as the bounded ES startup-readiness lifecycle checkpoint.
* Runtime route-gate remains deferred under Candidate A. Current behavior is app-start fail-closed only; successfully constructed ES route graphs are prepared-serving; `BeautySearchApi` has no runtime readiness gate; there is no runtime HTTP 503 gate today.
* Replacement/freshness/rollback remains future. There is no stale/previous index state, alias/versioned-index replacement policy, freshness/staleness policy, runtime refresh trigger semantics beyond eager seed preparation, or rollback policy today.
* Full lifecycle operations remain future. There is no runtime rebuild/refresh, replacement activation, rollback, disable/kill-switch, stale/current/previous catalog operation state, operator-triggered lifecycle command surface, or broader lifecycle auth/config/visibility policy beyond explicit opt-in/internal Design A today.
* This closeout does not implement route changes, serving changes, or `/beauty-search` behavior changes.

Next coordinator decision boundary:

* keep current production route ES-backed;
* request or decline a future Qdrant opt-in route implementation approval;
* do not treat closed planning as approved implementation.

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
* First pure M8/M9 shared vocabulary/reporting contract slice — implemented.
* Pure M9 saved dataset/report format and deterministic markdown renderer — implemented.
* Pure M9 static/in-memory runner skeleton for saved report and markdown artifact assembly — implemented.
* Pure M9 canonical static fixtures and example saved report artifact — implemented.

## 5. Current priority: post-M7 serving decision remains separate

* Default production `/beauty-search` is now ES-backed over the seed catalog.
* ES seed-route checkpoint is reached. Plain `sbt test` is the canonical full verification command.
* Latest user-verified full `sbt test` after option93/96: 1189 tests run, 1189 succeeded, 0 failed, 0 aborted, 1 canceled, 2 pending; all tests passed.
* Business demo ready: runbook, query inventory, and smoke spec all in place.
* ES-native + Qdrant-native benchmark/eval comparison remains offline/eval-only.
* ES and Qdrant may advance together only in eval/benchmark, not serving.
* Production serving remains:

  ```text
  current ES seed route (default)
  → Qdrant production-candidate readiness only if contract parity, indexing/search readiness, quality/eval gates, observability, rollback/disable controls, and explicit activation policy are satisfied
  → controlled hybrid only after direct production-candidate readiness and explicit serving policy approval
  ```

* The production route does eager seed index preparation during route composition. M5 is closed as a bounded startup-readiness lifecycle checkpoint; runtime route-gate, replacement/freshness/rollback, and full lifecycle operations remain separate future tracks.
* `ElasticsearchSeedLifecycleMetadata` is a non-serving DI/readiness metadata seam exposed via `ElasticsearchSeedIndexReadiness.lifecycleMetadata` and `ElasticsearchSeedSearchComposition.lifecycleMetadata`; it is not a production lifecycle implementation.
* `ElasticsearchProductionReadinessState` is an internal non-serving state derived from that metadata. It records serving readiness `NotEnforced`, replacement `NotConfigured`, freshness `NotTracked`, refresh `EagerSeedPreparationOnly`, rollback `NotConfigured`, and operator visibility `NotExposed`.
* `ElasticsearchLifecycleStatusResponse` is a pure non-serving model/encoder derived from that state. It is not DI-bound or exposed through HTTP.
* `ElasticsearchStartupReadinessTransition` is a pure, unbound startup-preparation result model. Prepared transitions derive the same non-serving status response as direct readiness-state projection; failed transitions retain source-backed `QueryFailure.OperationFailure` details without lifecycle metadata or a status response. Both record `ElasticsearchStartupServingDecision.NotEnforced`; neither gates serving or changes preparation/composition behavior. Successful compositions now expose a prepared transition through `ElasticsearchSeedSearchComposition.startupReadinessTransition`, and `BeautySearchCatalogBackendModules.seedResourceElasticsearch` binds it through DI. Source-backed initializer failure paths are classifiable into `PreparationFailed` in pure tests without changing initializer behavior. No serving enforcement exists, and a successfully constructed ES route graph remains prepared-serving.
* `ElasticsearchStartupReadinessStatusResponse` is a pure non-serving startup status projection from `ElasticsearchStartupReadinessTransition`. Prepared projections include the nested `ElasticsearchLifecycleStatusResponse`; failed projections expose operation/message only, without lifecycle metadata or status response fields. It is HTTP-exposed only through the explicit opt-in/internal operator module `BeautySearchRouteModules.apiElasticsearchWithOperatorVisibility`; it is not exposed by the default ES graph.
* Cross-model consistency coverage (`ElasticsearchReadinessConsistencySpec`) proves field-level agreement across readiness state, lifecycle response, startup transition, startup status projection, and composition-derived projections. This is non-serving test coverage, not serving-gate enforcement.
* Focused ES-backed route/module graph specs prove that lifecycle metadata and the derived readiness state are materialized through the current route graphs. M5 is closed as a bounded startup-readiness lifecycle checkpoint; runtime route-gate, replacement/freshness/rollback, and full lifecycle operations are intentionally separate future tracks.
* Current route-module state coverage documents the seed-only gaps; it does not enforce readiness or change serving behavior.
* The lifecycle status field shape and encoder are implemented as non-serving code and documented in `docs/codebase-review/ES_LIFECYCLE_STATUS_DESIGN.md`. Design A operator visibility is implemented only through the explicit opt-in/internal operator module; it is absent from the default graph.
* Detailed milestone status and priority order for lifecycle/eval/direct-Qdrant-candidate/hybrid work live in [docs/codebase-review/07-current-gaps-and-roadmap.md](codebase-review/07-current-gaps-and-roadmap.md).
* **M4 is closed.** HTTP/BeautySearch contract stabilized and frozen; production route exposure documented/tested; bad-input/default decode/semantic structured errors documented/tested; typed-GET/legacy JSON docs pruned or canonicalized; public examples/freeze/checklist recorded. Startup serving-gate design documented in `ES_STARTUP_SERVING_GATE_DESIGN.md`.
* **M5 is closed as a bounded startup-readiness lifecycle checkpoint.** App-start fail-closed and prepared-serving are covered; non-serving lifecycle metadata, readiness, status, transition, DI/rooting, failure classification, and consistency seams are implemented and tested. Remaining ES work is intentionally split into separate future tracks: runtime route-gate under Candidate A, replacement/freshness/rollback, and full lifecycle operations. Design A operator visibility is implemented as explicit opt-in/internal only and does not change `/beauty-search`.
* Full verification is separate from focused validation; full `sbt test` was not run from this docs pass.
* **M6 is closed as the Qdrant production-candidate readiness foundation.** The pure readiness foundation covers collection/identity, contract parity, indexing, search, quality/eval, observability, rollback/disable, and activation-policy categories. It is not serving approval, route wiring, or production activation.
* **M7 is closed as the activation/source-confirmation and serving-policy planning foundation.** The pure planning/config/no-regression layers are now consumed by the disabled-by-default explicit opt-in Qdrant route gate. They still do not approve production route activation, route switching, shadow serving, traffic mirroring, fallback, score fusion, reranking, or hybrid serving.
* Source-confirmed route seams remain separated: `BeautySearchApi` and `BeautySearchTapirEndpoints` remain backend-agnostic; `BeautySearchRouteModules.apiElasticsearch` selects the current production ES seed composition; `LeaderboardPlugin` includes that ES route directly. `BeautySearchRouteModules.apiQdrantExplicitOptIn` is a separate explicit module and is not included by the default production graph.
* `QdrantProductionCandidateServingApprovalRequest` remains the historical approval-request boundary. Implementation approval now exists only for disabled-by-default explicit opt-in route wiring, and `/beauty-search` remains ES-backed until separate production-route activation approval.
* Explicit opt-in route readiness is judged without real production traffic by combining curated canonical seed queries over the seed-resource catalog snapshot, saved/offline eval evidence, no-regression approval, quality-gate evidence, prerequisite-gate construction evidence, focused route/module safety specs, and full verification by the coordinator/user. The seed/eval query set must include representative seed/eval fixture queries plus regression, edge, and negative cases to reduce overfitting.
* Operator/developer smoke for the explicit opt-in route lives in `docs/local/QDRANT_EXPLICIT_OPTIN_ROUTE_SMOKE_CHECKLIST.md`; it is optional, resource-gated, and does not change the default production route.
* The production activation decision criteria live in `docs/local/QDRANT_PRODUCTION_ACTIVATION_DECISION_CRITERIA.md`; those criteria are stricter than the opt-in route prerequisites and remain unsatisfied until a separate production-route decision is approved and implemented.
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
* Shadow serving and production traffic mirroring are not readiness prerequisites. The M7 activation planning/source-confirmation foundation is closed without serving implementation. M8 controlled hybrid serving remains future-only and conditional on separate serving approval.
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
