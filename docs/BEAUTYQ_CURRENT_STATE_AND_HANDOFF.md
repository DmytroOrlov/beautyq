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
* Future M8 production-telemetry planning and M9 offline-eval-harness planning are paired in `docs/local/M8_M9_TELEMETRY_AND_OFFLINE_EVAL_PLAN.md`. All implemented M8/M9 slices are pure/non-serving: shared vocabulary/reporting contracts (`M8M9EvalContracts`), the M8 telemetry schema renderer/static adapter (`M8TelemetrySchemaRenderer`), the M9 saved dataset/report format and markdown renderer (`M9OfflineEvalSavedReport`), the static runner skeleton plus canonical fixtures/example artifact (`M9OfflineEvalStaticRunner`, `M9OfflineEvalStaticFixtures`), the backend-runner interface (`M9OfflineEvalBackendRunner`), the ES/Qdrant offline adapter skeletons (`M9OfflineEvalRealBackendAdapterSkeleton`), the backend-adapter failure matrix (`M9OfflineEvalBackendAdapterFailureMatrix`), and the resource-gated real-backend spike scaffold with saved-report artifact capture and ES-only/Qdrant-only/combined gated smoke evidence. Telemetry emission, metrics-client integration, real ES/Qdrant backend runners, route changes, hybrid serving, fusion, reranking, and production activation remain unimplemented. Gate details live in `docs/local/M8_M9_TELEMETRY_AND_OFFLINE_EVAL_PLAN.md` section 6.1.
* M9 BeautyQ eval evidence is offline eval contracts only and consists of:
  * a 63-query seed-ready dataset fixture `leaderboard/search/eval/beautyq_search_eval_queries_v1.json` with pure metadata in `M9BeautyQSearchEvalQueryDataset` (full JSON parsing deferred);
  * full 63-query static rows in `M9BeautyQSearchEvalQueryDatasetStaticRows` feeding `M9OfflineEvalStaticRunner`, where `q_nails_001`, `q_nails_003`, and `q_noise_005` keep stronger top-k anchors and the other 60 are static placeholders (artifact `bifunctor-tagless/src/test/resources/leaderboard/search/eval/m9-beautyq-eval-dataset-static-rows-report.md`);
  * a static scorecard `M9BeautyQSearchEvalStaticScorecard` (verdict `dataset_static_rows_ready`) recording 63 dataset queries, 63 mapped/accepted rows, 3 representative anchors, 60 placeholder-only rows, manual/static + unknown-serving attribution, and 0 real/ES/Qdrant backend evidence rows (artifact `bifunctor-tagless/src/test/resources/leaderboard/search/eval/m9-beautyq-eval-static-scorecard.md`);
  * a real-call checkpoint `M9BeautyQSearchEvalRealCallCheckpoint` defaulting to `not_eligible_no_explicit_resource_config`, where ES-only/Qdrant-only/both-config eligibility is offline/resource-gated only, both-config requires operator approval, and a missing or non-ready scorecard blocks (artifact `bifunctor-tagless/src/test/resources/leaderboard/search/eval/m9-beautyq-real-call-checkpoint.md`).
  * Across all of the above: no route/plugin/DI/HTTP source is involved, default `/beauty-search` remains ES-backed, the Qdrant opt-in route stays disabled by default, real backend calls remain disabled by default, and Qdrant production activation remains not approved.
* M9 real-resource smoke evidence chain (options 138–141) — accepted offline planning/reporting contracts only: a real-resource prerequisites audit separately auditing ES-only, Qdrant-only, and combined ES/Qdrant comparison prerequisites; a saved evidence schema defining separate ES-only, Qdrant-only, and combined evidence shapes; and a deterministic evidence renderer producing a checked-in default/no-config markdown artifact (`bifunctor-tagless/src/test/resources/leaderboard/search/eval/m9-beautyq-real-resource-smoke-evidence-default.md`). The default/no-config artifact renders blocked/skip evidence (complete prerequisites render pending-execution evidence only), never success. No real ES/Qdrant execution, route activation, route switch, hybrid serving, fallback, score fusion, reranking, or production telemetry is implemented or accepted; default `/beauty-search` stays ES-backed, the Qdrant opt-in route stays disabled by default, and Qdrant production activation remains not approved.
* **M9 real-resource execution gate milestone is closed as planning/reporting only** (options 142–145B): a runbook/evidence consistency contract statically checking the future-only runbook against the saved evidence schema and default/no-config artifact; separate ES-only, Qdrant-only, and combined ES/Qdrant execution gate designs that reach `pending_explicit_execution_task` only when their prerequisites are complete, and block on default/no-config or non-ready scorecard/checkpoint states; and a deterministic execution-gate renderer producing a checked-in default/no-config markdown artifact (`bifunctor-tagless/src/test/resources/leaderboard/search/eval/m9-beautyq-real-resource-execution-gate-default.md`) that renders blocked/skip evidence for all three gates, never success. No real ES/Qdrant execution, route/plugin/DI/HTTP involvement, production activation, route switch, hybrid serving, fallback, score fusion, reranking, or production telemetry is implemented or accepted by this milestone. Full suite after option145B: 1440 tests succeeded, 0 failed, 0 aborted, 1 canceled, 2 pending. See `docs/local/M8_M9_TELEMETRY_AND_OFFLINE_EVAL_PLAN.md` section 6.1 and `docs/local/BEAUTYQ_M9_REAL_RESOURCE_SMOKE_RUNBOOK.md` for detail.
* **M10 is closed as offline query-classification/routing-policy/retrieval-policy-readiness planning and reporting only.** Accepted M10 contracts: query classification foundation (`M10BeautyQSearchQueryClassification`), offline routing policy foundation (`M10BeautyQSearchOfflineRoutingPolicy`), full 63-query classification coverage scorecard (`M10BeautyQSearchFullQueryClassification` / `M10BeautyQSearchFullQueryClassificationCoverageScorecard`), semantic manual-review/negative-control hardening, and the retrieval-policy readiness contract / M11 input-preparation artifact (`M10BeautyQSearchRetrievalPolicyReadiness`, artifact `bifunctor-tagless/src/test/resources/leaderboard/search/eval/m10-beautyq-retrieval-policy-readiness.md`). M10B maps all 63 accepted query ids exactly once with category counts `provider_lookup=0`, `service_intent=7`, `attribute_filter_intent=5`, `location_intent=1`, `price_budget_intent=0`, `availability_time_intent=0`, `comparison_exploration_intent=1`, `noisy_ambiguous_non_beauty_intent=1`, `mixed_intent=48`, and offline strategy-intent counts `es_only_candidate_retrieval=13`, `qdrant_only_candidate_retrieval=1`, `combined_es_qdrant_comparison=48`, `manual_review_blocked=0`, `no_op_noise=0`, `accepted_negative_control_excluded=1`. M10C maps those into M11 input groups `es_candidate_generation_study_input=13`, `qdrant_candidate_generation_study_input=1`, `combined_es_qdrant_comparison_study_input=48`, `accepted_negative_control_exclusion_input=1`, `manual_review_blocked_input=0`, `no_op_noise_input=0`, total `63`; backend candidate-generation study inputs total `62`, accepted negative-control exclusions total `1`, unresolved manual-review inputs total `0`. `q_noise_004 = gel removal` maps to mixed intent / combined ES/Qdrant comparison study input; `q_noise_005 = lifting` maps to the accepted negative-control exclusion input. The M10 readiness verdict is `m11_candidate_generation_inputs_ready_with_negative_control_exclusion`. M10 prepares offline M11 candidate-generation inputs; it does not implement M11 candidate generation, does not execute real ES/Qdrant, and does not change production routing. This is offline input-preparation readiness only — not retrieval quality, not production readiness, not route activation, not serving approval, and not execution readiness. Default `/beauty-search` remains ES-backed, the Qdrant opt-in route stays disabled by default, Qdrant production activation remains not approved, and real backend calls remain disabled by default; no real ES/Qdrant execution, route switch, hybrid serving, fallback, score fusion, reranking, or production telemetry is implemented or accepted.
* **M11 is closed as offline candidate-generation eval/planning/reporting only.** Accepted M11 contracts, consuming M10's input-preparation output: candidate-generation input/request skeleton (`M11BeautyQSearchCandidateGenerationInputSkeleton`), candidate-generation result schema (`M11BeautyQSearchCandidateGenerationResultSchema`), and candidate-generation boundary/failure matrix (`M11BeautyQSearchCandidateGenerationBoundaryFailureMatrix`). M11A defines offline request shapes only (row groups: ES-only `13`, Qdrant-only `1`, combined comparison `48`, accepted negative-control exclusion `1`, manual-review blocked `0`, no-op/noise `0`, total `63`; request-leg counts: ES `61`, Qdrant `49`, combined comparison pairs `48`). M11B defines saved result/report placeholder shapes only — pending/not-executed, not real candidate results — with dispositions `es_only_pending_not_executed=13`, `qdrant_only_pending_not_executed=1`, `combined_comparison_pending_not_executed=48`, `accepted_negative_control_excluded=1`, `manual_review_excluded=0`, `no_op_noise_skipped=0`, total `63`, and pending result-leg placeholders ES `61` / Qdrant `49` / combined pairs `48`. M11C defines a boundary/failure matrix only — not production routing — with `20` total rows, `8` accepted, `12` denied, `0` skipped, and `18` stable reason codes. `q_noise_004 = gel removal` maps to combined ES/Qdrant pending placeholders; `q_noise_005 = lifting` maps to the accepted negative-control exclusion with no backend legs. M11 prepares scaffolding for M12 fusion/reranking input; it does not implement candidate retrieval, scoring, fusion, reranking, fallback, backend execution, production telemetry, or production routing, and it does not fabricate real candidate ids, scores, ranks, provider ids, or backend responses. Default `/beauty-search` remains ES-backed, the Qdrant opt-in route stays disabled by default, Qdrant production activation remains not approved, and real backend calls remain disabled by default; no real ES/Qdrant execution, route activation, route switch, hybrid serving, fallback, score fusion, reranking, or production telemetry is implemented or accepted.
* **M12 is closed as offline fusion/reranking eval/planning/reporting only.** Accepted M12 contracts, consuming M11's saved-result output: fusion/reranking input scaffold (`M12BeautyQSearchFusionRerankingInputScaffold`), fusion/reranking policy catalog and experiment-plan schema (`M12BeautyQSearchFusionRerankingPolicyCatalog` / `M12BeautyQSearchFusionRerankingExperimentPlan`), fusion/reranking boundary/failure matrix (`M12BeautyQSearchFusionRerankingBoundaryFailureMatrix`), and fusion/reranking saved-output schema (`M12BeautyQSearchFusionRerankingSavedOutputSchema`). M12A defines schema-only fusion/reranking experiment input envelopes. M12B defines stable non-executable policy names — `es_baseline_passthrough`, `qdrant_baseline_passthrough`, `combined_union_placeholder`, `combined_intersection_placeholder`, `tie_breaker_placeholder`, `accepted_negative_control_exclusion_policy` — and schema-only experiment-plan rows: total `63`, ES baseline `13`, Qdrant baseline `1`, combined experiment `48`, accepted negative-control exclusion `1`, manual/no-op `0`, executable policy `0`, real scored/reranked `0`. M12C defines a boundary/failure matrix — denying executable policy drift, fabricated candidate payloads, backend/client/route drift, and production activation/serving/readiness drift — with `38` total rows, `9` accepted, `29` denied, `0` skipped. M12D defines a placeholder-only saved-output/report schema with `63` saved output rows, `62` placeholder output rows, `1` excluded output row, `0` executable output rows, `0` real scored/reranked output rows, `0` fabricated candidate payload rows, `48` combined placeholder output rows, `1` accepted negative-control output row. `q_noise_004 = gel removal` maps to combined placeholder experiment and saved-output rows with the three non-executable combined policy names; `q_noise_005 = lifting` maps to the accepted negative-control excluded output with no backend candidate policy output. M12 prepares placeholder-only planning/reporting surfaces for later evidence work; it does not implement scoring, fusion execution, reranking execution, candidate retrieval, backend execution, backend clients, production routing, route/plugin/DI/HTTP, fallback, telemetry, or production serving, and it does not fabricate candidate ids, provider ids, scores, ranks, backend responses, fused scores, reranked positions, quality labels, fusion outputs, or reranking outputs. Default `/beauty-search` remains ES-backed, the Qdrant opt-in route stays disabled by default, Qdrant production activation remains not approved, and real backend calls remain disabled by default; no real ES/Qdrant execution, production activation, route switch, hybrid serving, fallback, score fusion, reranking, production telemetry, route activation, or serving approval is accepted.
* **M13A is closed as controlled explicit opt-in route/module planning and contract work only — not route activation.** Accepted M13A contract, consuming the M12 closeout only as planning input: `M13BeautyQSearchControlledOptInRoutePlanning`. M13A defines stable route-planning states `current_es_default_preserved`, `explicit_qdrant_opt_in_disabled_by_default`, `experiment_route_planning_only`, `production_activation_not_approved`, and `serving_approval_not_granted`. M13A defines denied drift cases for default route switch, production route activation, implicit Qdrant activation, real backend execution, route/plugin/DI/HTTP change, hybrid serving, fallback, score fusion, reranking execution, production telemetry, quality-green claim, retrieval-quality claim, production-readiness claim, route-activation claim, and serving-approval claim. M13A does not treat M12 placeholder rows as quality evidence and fabricates no evidence from M12 placeholder rows. M13A is planning/contract work only; it does not activate production routing, does not change the default `/beauty-search` path, does not enable Qdrant production activation, does not create backend clients, does not execute real ES or Qdrant, and does not add route/plugin/DI/HTTP behavior, hybrid serving, fallback, score fusion, reranking execution, or production telemetry. Local/dev-only fallback and runtime route-gate / HTTP 503 behavior remain future work and are not implemented or closed by M13A. Default `/beauty-search` remains ES-backed, the explicit Qdrant opt-in route stays disabled by default, and Qdrant production activation remains not approved.
* **M14A/M14B/M14C are accepted — route-gate / serving-readiness design contract, disabled-by-default runtime route gate, and local/dev-only fallback policy contract; not production activation.** M14A adds the design-contract readiness artifact `M14BeautyQSearchRouteGateReadinessDesign` (design-only — not runtime gate readiness and not production readiness). M14B adds `BeautySearchServingGate` with states `disabled` / `enabledNotReady` / `enabledReady` and `rejectsServing = enabled && !servingReady`; the default DI binding uses `disabled`, preserving current ES-backed `/beauty-search` behavior. With the gate disabled, valid `POST /beauty-search` requests follow the existing ES-backed route; with the gate enabled-not-ready, valid requests return HTTP 503; with the gate enabled-ready, valid requests again follow the existing ES-backed route; invalid requests still return 400 before the gate is evaluated. M14C adds `BeautySearchLocalDevOnlyFallbackPolicy`, allowing local/dev-only fallback only as an explicit future local/dev choice — production fallback is not enabled, runtime serving fallback is not enabled by default, default `/beauty-search` remains ES-backed, Qdrant remains disabled-by-default and is not a fallback target, the M14B route gate remains separate from fallback, and no hybrid/fusion/reranking/production-telemetry behavior is introduced. `ElasticsearchOperatorVisibilityEndpointPolicySpec` now has zero pending tests after M14C. The recursive `graal-resources/target/.../classes/target/...` directory observed during M14B validation was build-artifact cleanup only, not a source/config change. Default `/beauty-search` remains ES-backed, the explicit Qdrant opt-in route stays disabled by default, and Qdrant production activation remains not approved; no default route switch, Qdrant activation, production fallback, hybrid serving, score fusion, reranking, production telemetry, production-readiness claim, route-activation claim, or serving-approval claim is approved by M14A/M14B/M14C.
* **M15A/M15B/M16A/M16B are accepted as the serving-gate selector and executable evidence-harness track — closed; not production activation and not serving approval.** This "M16" is unrelated to the later, unimplemented "M16. Production activation implementation" milestone in `docs/local/BEAUTYQ_ES_QDRANT_HYBRID_RETRIEVAL_ROADMAP.md` (the same cross-track milestone-number collision already noted there for "M14"). M16A adds `BeautySearchPluginModules.apiWithServingGate[F](servingGate)` and `BeautySearchRouteModules.seedCatalogElasticsearchWithServingGate(servingGate)`, letting tests/local-dev select an explicit `BeautySearchServingGate` without changing `BeautySearchPluginModules.api[F]`'s production/default `disabled` gate. M16B adds `BeautySearchProductionRouteSpecSupport.ServingGateEvidenceCase` / `runServingGateEvidenceCase` and converts `BeautySearchProductionRouteExposureSpec`'s prior one-off M16A route assertions into a table-driven evidence set with four stable ids: `gate_disabled_valid_request` -> 200 OK, `gate_enabled_not_ready_valid_request` -> 503, `gate_enabled_ready_valid_request` -> 200 OK, `gate_enabled_not_ready_invalid_request` -> 400. Because this executable evidence fully supersedes the M15A manual smoke-case table (same gate-state/status facts), the M15A checklist doc `docs/local/BEAUTYQ_M15_SERVING_READINESS_MANUAL_SMOKE_CHECKLIST.md` is removed (M15B); this section is the sole source-truth for the M15/M16 gate-selector and evidence-harness track. No source, route/plugin/DI/HTTP, Qdrant module, or `LeaderboardPlugin` behavior changed; default `/beauty-search` remains ES-backed, the gate stays `disabled` by default, Qdrant explicit opt-in remains disabled by default, Qdrant production activation remains not approved, and no default route switch, production fallback, runtime serving fallback by default, hybrid serving, score fusion, reranking execution, production telemetry, production-readiness claim, route-activation claim, or serving-approval claim is approved. `ElasticsearchOperatorVisibilityEndpointPolicySpec` remains at zero pending tests.
* There is no real production traffic in this project context. Shadow serving and production traffic mirroring are therefore future-only, non-blocking, and not useful as current readiness evidence.
* Pending expectations map:
  * The former `5` pending expectations in `BeautySearchOptInRouteModuleSpec` are now active tests for the explicit opt-in route module and prerequisite gate.
  * The former `1` pending expectation in `BeautySearchProductionRouteExposureSpec` is now active coverage preserving that `POST /beauty-search` stays ES-backed until separate production-route activation approval exists.
  * The former `2` pending expectations in `ElasticsearchOperatorVisibilityEndpointPolicySpec` (future-only ES local/dev fallback and runtime route-gate / HTTP 503 work) are now active assertions following M14B's disabled/enabled-not-ready/enabled-ready route-gate coverage and M14C's local/dev-only fallback policy coverage; the spec has zero pending tests.
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
* Pure M8/M9 contract slices — implemented: shared vocabulary/reporting contracts, M8 telemetry schema renderer/static adapter, M9 saved dataset/report format and markdown renderer, static runner skeleton, canonical fixtures and example artifact, backend-runner interface and manual/static adapter, ES/Qdrant offline adapter skeletons, and backend-adapter failure matrix. All pure/offline; no backend execution.
* M9 resource-gated real-backend spike scaffold plus saved-report artifact capture and ES-only/Qdrant-only/combined gated smoke evidence — implemented as offline foundations only; real successful ES/Qdrant execution stays future/resource-gated and disabled by default.
* M9 BeautyQ eval evidence (dataset fixture, full 63-query static rows, `dataset_static_rows_ready` static scorecard, and `not_eligible_no_explicit_resource_config` real-call checkpoint) — implemented as offline eval contracts only; see section 0 for the consolidated counts, anchors, and artifact paths. No route/plugin/DI/HTTP source is involved, default `/beauty-search` remains ES-backed, real backend calls remain disabled by default, and Qdrant production activation remains not approved.
* **M9 real-resource execution gate milestone — closed as planning/reporting only.** Runbook/evidence consistency, ES-only/Qdrant-only/combined execution gate designs, and a deterministic execution-gate renderer with checked-in default/no-config artifact are accepted; see section 0 for detail. No real ES/Qdrant execution or production activation is implemented or accepted.
* **M10 query classification and retrieval-policy readiness milestone — closed as offline planning/reporting only.** Query classification foundation, offline routing policy foundation, full 63-query classification coverage scorecard, manual-review/negative-control hardening, and retrieval-policy readiness contract (M11 input-preparation artifact) are accepted; see section 0 for the consolidated counts and verdict. M10 prepares offline M11 candidate-generation inputs only; it does not implement M11 candidate generation, execute real ES/Qdrant, or change production routing.
* **M11 candidate-generation milestone — closed as offline eval/planning/reporting only.** Candidate-generation input/request skeleton, result schema (saved/pending placeholders), and boundary/failure matrix are accepted; see section 0 for the consolidated counts. M11 prepares scaffolding for M12 fusion/reranking input only; it does not implement candidate retrieval, scoring, fusion, reranking, backend execution, or production routing.
* **M12 fusion/reranking milestone — closed as offline eval/planning/reporting only.** Fusion/reranking input scaffold, policy catalog and experiment-plan schema, boundary/failure matrix, and saved-output schema are accepted; see section 0 for the consolidated counts. M12 prepares placeholder-only planning/reporting surfaces for later evidence work only; it does not implement scoring, fusion execution, reranking execution, candidate retrieval, backend execution, or production routing.
* **M13A controlled opt-in route/module planning milestone — closed as planning/contract work only, not route activation.** The route-planning contract and its five route-planning states and denied drift cases are accepted; see section 0 for the full statement. M13A consumes the M12 closeout only as planning input and treats no M12 placeholder row as quality evidence. Local/dev-only fallback and runtime route-gate / HTTP 503 remain future work, not implemented or closed by M13A.
* **M14 route-gate / serving-readiness and local/dev-only fallback milestone — closed as gate/fallback contract work only, not production activation.** M14A's design-contract readiness artifact, M14B's disabled-by-default `BeautySearchServingGate` runtime route gate (disabled / enabled-not-ready / enabled-ready, HTTP 503 when enabled-not-ready, 400 still returned for invalid requests before the gate), and M14C's `BeautySearchLocalDevOnlyFallbackPolicy` local/dev-only fallback contract are all accepted; see section 0 for the full statement. Default `/beauty-search` remains ES-backed and unchanged while the gate stays disabled by default. Local/dev-only fallback is future local/dev policy only — not production fallback and not runtime serving fallback by default. No default route switch, Qdrant production activation, production fallback, hybrid serving, score fusion, reranking, production telemetry, production-readiness claim, route-activation claim, or serving-approval claim is approved by M14.
* **M15A/M15B/M16A/M16B serving-gate selector and executable evidence-harness milestone — closed; not production activation and not serving approval.** See section 0 for the full statement: the M16A explicit selector surface, the M16B stable evidence-case ids/expected statuses, the M16-numbering disambiguation against the roadmap's unrelated future "M16. Production activation implementation" milestone, and confirmation that the M15A manual smoke checklist is removed (M15B) because M16B's executable evidence supersedes it.

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
