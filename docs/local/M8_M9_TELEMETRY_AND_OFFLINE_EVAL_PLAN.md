# M8/M9 Telemetry and Offline Eval Plan

## 1. Scope

This document is planning plus the first pure shared-contract slice.

It does not approve or implement:

- production activation;
- a default-route switch;
- hybrid serving;
- fallback;
- score fusion;
- reranking;
- `HybridServe`;
- Qdrant auto-supplement;
- shadow serving;
- traffic mirroring;
- production telemetry code;
- real ES/Qdrant backend eval runner execution.

Implemented pure slices:

- `leaderboard.search.eval.M8M9EvalContracts` defines shared vocabulary, offline eval report metadata/summary contracts, and telemetry schema-plan contracts.
- `leaderboard.search.M8M9EvalContractsSpec` locks stable string rendering, query-class taxonomy coverage, planned M9 metric names, planned M8 event families, and the vocabulary-only status of hybrid/fusion/reranker terms.
- `leaderboard.search.eval.M9OfflineEvalSavedReport` defines the pure M9 saved dataset/report format, stable format version, markdown artifact wrapper, and deterministic markdown renderer.
- `leaderboard.search.M9OfflineEvalSavedReportSpec` locks representative markdown output, metadata/metric/row attribution, negative/out-of-catalog query representation, and the non-serving boundary.
- `leaderboard.search.eval.M9OfflineEvalStaticRunner` defines a pure static/in-memory runner skeleton that assembles caller-supplied dataset, metadata, rows, aggregate metrics, quality-gate decision, notes, warnings, and generated-at text into a saved report plus markdown artifact.
- `leaderboard.search.M9OfflineEvalStaticRunnerSpec` locks validation, input-order preservation, markdown artifact parity with the direct renderer, static quality-gate summary counts, and the no-backend-execution boundary.
- `leaderboard.search.eval.M9OfflineEvalStaticFixtures` and `M9OfflineEvalExampleArtifacts` provide canonical static fixture data plus a generated example markdown artifact shape for exact, semantic, ambiguous, and negative/out-of-catalog query classes.
- `leaderboard.search.M9OfflineEvalStaticFixturesSpec` locks fixture query-class coverage, stable order, static-runner assembly, checked-in resource parity, non-activation quality decision, and the non-serving boundary. The checked-in artifact is `bifunctor-tagless/src/test/resources/leaderboard/search/eval/m9-static-example-report.md`.
- `leaderboard.search.eval.M9OfflineEvalBackendRunner` defines the first pure backend-runner interface slice: execution modes, execution plans, run/query requests, candidates, query results, failures-as-data, run responses, a static/manual adapter, and conversion to `M9OfflineEvalStaticRunInput`.
- `leaderboard.search.M9OfflineEvalBackendRunnerSpec` locks the vocabulary-only ES-only/Qdrant-only/future-hybrid modes, manual/static adapter boundary, deterministic missing-id validation, failure-as-data rows, static-runner compatibility, no hidden fallback field, and no route/plugin/DI/HTTP/Docker/client surface.
- `leaderboard.search.eval.M9OfflineEvalRealBackendAdapterSkeleton` defines pure ES-only and Qdrant-only offline adapter skeletons behind the backend-runner interface. They accept caller-supplied static rows or return not-connected/backend-failure data, enforce source/mode attribution, keep latency absence visible as warnings, and do not connect to ES, Qdrant, HTTP, routes, Distage, plugins, Docker, or metrics clients.
- `leaderboard.search.M9OfflineEvalRealBackendAdapterSkeletonSpec` locks ES-only/Qdrant-only attribution, not-connected failures-as-data, source/mode mismatch rejection, missing attribution validation, static-runner compatibility, latency-missing warnings, production-activation non-approval, and the no-hybrid/fallback/fusion/reranking/serving boundary.
- `leaderboard.search.eval.M9OfflineEvalBackendAdapterFailureMatrix` defines pure backend-adapter failure-matrix fixtures for ES/Qdrant not-connected cases, source/mode mismatches, duplicate supplied rows, missing dataset/catalog/query attribution, latency warnings, backend failures as data, Qdrant required metadata warnings, future-hybrid vocabulary-only behavior, hidden-fallback non-representation, and production-activation non-approval.
- `leaderboard.search.M9OfflineEvalBackendAdapterFailureMatrixSpec` locks those matrix expectations and proves static-runner compatibility where expected without ES clients, Qdrant clients, network, HTTP, Distage, Docker, metrics clients, route hooks, or production telemetry.
- `leaderboard.search.eval.M9OfflineEvalRealBackendResourceGate` defines the first resource-gated real-backend spike scaffold: explicit enablement, required input checks, backend/source/mode attribution, not-configured/not-connected failures as data, and evidence values that feed the existing M9 backend-runner/static-runner path. Real successful ES/Qdrant execution is still not implemented and remains disabled by default.
- `leaderboard.search.eval.M9BeautyQSearchEvalRealCallCheckpoint` adds the resource-gated real-call checkpoint contract: a pure decision over the static scorecard readiness, ES/Qdrant resource config presence, and operator approval. The default decision blocks without explicit resource config (`not_eligible_no_explicit_resource_config`); eligible decisions are offline/resource-gated only (ES-only, Qdrant-only, or both with operator approval). A missing or non-ready static scorecard blocks; production activation remains not approved and is never output as ready; no real backend call and no route/plugin/DI/HTTP source is involved. The default `/beauty-search` route remains ES-backed and the Qdrant opt-in route stays disabled by default. Real backend calls remain disabled by default unless an explicit future resource-smoke task runs them.
- `leaderboard.search.M9OfflineEvalRealBackendResourceGateSpec` locks default-disabled behavior, missing-input denial, production-activation non-approval confirmation, allowed-without-resource not-configured data, ES/Qdrant attribution preservation, and static-runner compatibility without production route/plugin/DI/HTTP/backend-client execution.
- `leaderboard.search.eval.M9OfflineEvalRealBackendSpikeArtifactCapture` defines pure saved-report artifact capture for the gated real-backend spike: deterministic capture of gate decision, resource config presence/absence, production non-approval, backend kind/source/mode attribution, warnings/failures as data, and byte-for-byte stable checked-in example artifacts. Default-disabled and not-configured outcomes render as failure/warning rows, not fake success. Real backend calls remain unimplemented and disabled by default.
- `leaderboard.search.M9OfflineEvalRealBackendSpikeArtifactCaptureSpec` locks default-disabled artifact rendering, not-configured artifact rendering, configured artifact rendering, gate decision recording, resource config presence/absence recording, production non-approval recording, ES/Qdrant source and mode attribution preservation, warnings/failures as data, byte-for-byte stable artifacts, static-runner compatibility, and no real backend call requirement.
- `leaderboard.search.eval.M9OfflineEvalEsOnlyGatedSmokeEvidence` defines ES-only gated smoke evidence artifact support: deterministic ES-only smoke artifact capture reusing the spike artifact path, with ES source/mode attribution, production non-approval, no fake success, gate decision/resource config presence recording, and byte-for-byte stable checked-in not-configured artifact. Real ES calls remain unimplemented and disabled by default.
- `leaderboard.search.M9OfflineEvalEsOnlyGatedSmokeEvidenceSpec` locks default-disabled ES smoke evidence rendering, not-configured ES smoke evidence rendering, ES source/mode attribution preservation, production non-approval recording, no fake success, warnings/failures as data, byte-for-byte stable artifact, no real ES call requirement, denied artifact on invalid resource config, and no route/plugin/DI/http surface.
- `leaderboard.search.eval.M9OfflineEvalQdrantOnlyGatedSmokeEvidence` defines Qdrant-only gated smoke evidence artifact support: deterministic Qdrant-only smoke artifact capture reusing the spike artifact path, with Qdrant source/mode attribution, production non-approval, no fake success, embedding/vector prerequisite absence as warning data, gate decision/resource config presence recording, and byte-for-byte stable checked-in not-configured artifact. Real Qdrant calls remain unimplemented and disabled by default.
- `leaderboard.search.M9OfflineEvalQdrantOnlyGatedSmokeEvidenceSpec` locks default-disabled Qdrant smoke evidence rendering, not-configured Qdrant smoke evidence rendering, Qdrant source/mode attribution preservation, production non-approval recording, no fake success, warnings/failures as data, embedding/vector prerequisite absence as warning data, byte-for-byte stable artifact, no real Qdrant call requirement, denied artifact on invalid resource config, and no route/plugin/DI/http surface.
- `leaderboard.search.eval.M9OfflineEvalCombinedGatedSmokeEvidenceIndex` defines the combined ES+Qdrant gated smoke evidence index: a deterministic offline-eval reporting/index layer that summarizes the accepted ES-only and Qdrant-only skipped/not-configured artifacts, preserves ES and Qdrant source/mode attribution, records both gate decisions and resource-config absence, records production activation non-approval, records that no real backend call is implemented, treats warnings/failures as data, and points to the checked-in per-backend artifacts by file name. It adds no hybrid/fallback/fusion/reranking and no route/plugin/DI/http source change; byte-for-byte stable checked-in combined index artifact `m9-combined-gated-smoke-evidence-index.md` exists.
- `leaderboard.search.M9OfflineEvalCombinedGatedSmokeEvidenceIndexSpec` locks combined ES and Qdrant entry rendering, both checked-in artifact filenames, ES and Qdrant source/mode attribution preservation, both resource configs recorded as absent/not-configured, production activation non-approval recording, no real backend call recorded, warnings/failures as data, no fake success, no hybrid/fallback/fusion/reranking claim, byte-for-byte stable rendered artifact, no route/plugin/DI/http source involved, default-disabled index build, and stable execution-mode preservation.
- `M9BeautyQSearchEvalEsOnlySmokeExecutionPlan`, `M9BeautyQSearchEvalQdrantOnlySmokeExecutionPlan`, `M9BeautyQSearchEvalCombinedSmokeComparisonPlan`, `M9BeautyQSearchEvalRealResourcePrerequisitesAudit`, `M9BeautyQSearchEvalRealResourceSmokeEvidenceSchema`, and `M9BeautyQSearchEvalRealResourceSmokeEvidenceRenderer` (+ `...Spec`s) form the M9 real-resource smoke evidence chain (options 138–141), accepted as offline planning/reporting contracts only. The prerequisites audit separately audits ES-only, Qdrant-only, and combined ES/Qdrant comparison prerequisites; the evidence schema defines separate ES-only, Qdrant-only, and combined evidence shapes; the renderer produces a deterministic checked-in default/no-config markdown artifact `bifunctor-tagless/src/test/resources/leaderboard/search/eval/m9-beautyq-real-resource-smoke-evidence-default.md` that renders blocked/skip evidence (complete prerequisites render pending-execution evidence only), never success. No real ES/Qdrant execution, route activation, route switch, hybrid serving, fallback, score fusion, reranking, or production telemetry is implemented or accepted; default `/beauty-search` stays ES-backed, the Qdrant opt-in route stays disabled by default, and Qdrant production activation remains not approved.
- `M9BeautyQSearchEvalQueryDataset` (+ `...Spec`) registers the uploaded 63-query `wandsbek_hamburg_beauty_services_seed_ready` dataset as a deterministic offline-eval/test resource at `leaderboard/search/eval/beautyq_search_eval_queries_v1.json`, with pure metadata (version, query/language counts, target carousels, service/attribute coverage, Wandsbek Markt location label) and the offline-only/no-route-plugin-DI-http boundary. Full JSON parsing remains deferred; no new JSON dependency is added.
- `M9BeautyQSearchEvalQueryDatasetStaticRows` (+ `...Spec`) maps all 63 fixture query ids into manual/unknown-serving M9 static/saved-report rows feeding `M9OfflineEvalStaticRunner`, validating the checked-in static id list against bounded resource anchors instead of parsing full JSON, preserving stronger `q_nails_001`/`q_nails_003`/`q_noise_005` top-k anchors, and keeping the other 60 as placeholders. Artifact: `bifunctor-tagless/src/test/resources/leaderboard/search/eval/m9-beautyq-eval-dataset-static-rows-report.md`.
- `M9BeautyQSearchEvalStaticScorecard` (+ `...Spec`) reports dataset/static readiness over those rows (verdict `dataset_static_rows_ready`): 63 dataset queries, 63 mapped/accepted rows, 3 representative anchors, 60 placeholder-only rows, manual/static + unknown-serving attribution only, zero real/ES/Qdrant backend evidence rows, no route/plugin/DI/http involvement, and deferred full JSON parsing. It is not backend quality evidence or activation approval. Artifact: `bifunctor-tagless/src/test/resources/leaderboard/search/eval/m9-beautyq-eval-static-scorecard.md`.
- `leaderboard.search.eval.M8TelemetrySchemaRenderer` defines the first pure M8 telemetry schema renderer/static schema adapter slice: stable schema format version, deterministic markdown and compact text renderers, planned event-family field schemas, metric schemas, and explicit conditional-future marking for fusion/rerank/fallback/interaction metrics.
- `leaderboard.search.M8TelemetrySchemaRendererSpec` locks event-family order, stable format version, rendered planned field/metric names, conditional-future metric marking, non-emission boundary, no route/plugin/DI/HTTP/backend-client surface, and byte-for-byte repeat rendering.
- This slice is pure/non-serving. It adds no telemetry emission, metrics client, route wiring, plugin wiring, HTTP behavior, real ES client adapter, real Qdrant client adapter, backend client integration, hybrid serving, fusion, or reranking.

M8 and M9 are paired because they need the same vocabulary for backend/source attribution, policy naming, query-class reporting, and report artifacts. Offline eval should establish that vocabulary first so future production telemetry can reuse the same terms and metric names where possible. Seed/eval evidence must remain separate from production telemetry, and there is no real production traffic in this project context yet.

## 2. Current baseline

Current route truth is owned by `docs/BEAUTYQ_CURRENT_STATE_AND_HANDOFF.md`, not by this plan. The only baseline facts this doc needs are:

- default production `/beauty-search` remains ES-backed;
- Qdrant explicit opt-in exists and stays disabled by default;
- production activation is not approved;
- production telemetry emission, metrics client integration, route hooks, and real offline ES/Qdrant backend-runner execution are not implemented;
- there is no real production traffic in this project context yet.

For exact route/module names, verification counts, and activation boundary wording, use the handoff plus `docs/local/QDRANT_PRODUCTION_ACTIVATION_DECISION_CRITERIA.md`.

## 3. Shared vocabulary

The following terms are now represented by pure shared contracts under `leaderboard.search.eval` and should stay stable across future M8 telemetry and M9 offline eval work:

- `serving_mode`: `es_only`, `qdrant_only`, `hybrid`, `unknown`
- `candidate_source`: `es`, `qdrant`, `manual`, `unknown`
- `routing_policy`: stable policy name + version, not free text
- `fusion_policy`: `none`, `weighted`, `rrf`, `reranker`, `future`
- `reranker_policy`: `none`, `rule_based`, `learned`, `future`
- `experiment_id`: explicit run or experiment label
- `query_class`: explicit taxonomy label from the approved query-class list
- `catalog_snapshot_id`: seed/eval or future serving snapshot identifier
- `eval_dataset_id`: named offline dataset or fixture bundle identifier
- `metric_window`: the aggregation window or run boundary for a metric/report
- `request_id`: request-level correlation id

Additional usage rules:

- Backend/source attribution should use the same field names in offline reports and any later telemetry events.
- Policy names should be versioned so evidence can be compared across runs without free-text interpretation.
- `catalog_snapshot_id` and `eval_dataset_id` should stay distinct so seed/eval evidence is not confused with future production traffic observations.
- `serving_mode = hybrid` is planned vocabulary only; it must not be read as implemented serving behavior.

## 4. M8 telemetry event schema

This is a schema-plan contract, not telemetry emission. The first pure renderer/static adapter exists as `M8TelemetrySchemaRenderer` / `M8TelemetrySchemaAdapter.defaultPlannedSchema`; it renders deterministic schema artifacts only and does not read requests, routes, clocks, metrics clients, ES, Qdrant, or production traffic.

Planned event families:

### Search request event

Intended fields:

- `timestamp`
- `request_id`
- `query_text` or privacy-safe query hash if required later
- `normalized_query`
- request filters/categories/geo inputs
- requested `limit`
- `serving_mode`
- `routing_policy`
- `fusion_policy`
- `reranker_policy`
- `experiment_id`
- `catalog_snapshot_id`
- latency summary
- final status: success, zero-result, timeout, failure

### Backend candidate event

Intended fields:

- `timestamp`
- `request_id`
- `candidate_source`
- backend label/version
- `serving_mode`
- candidate count
- top-k result ids
- backend latency
- optional embedding latency for Qdrant-like paths
- policy versions
- `experiment_id`
- `catalog_snapshot_id`
- timeout/failure status if applicable

### Result exposure event

Intended fields:

- `timestamp`
- `request_id`
- `serving_mode`
- final top-k result ids
- result count
- per-result source attribution where available
- query class if available
- policy versions
- `experiment_id`
- `catalog_snapshot_id`

### Optional interaction event

Only relevant if the product later has clicks, saves, orders, or another downstream interaction signal.

Intended fields:

- `timestamp`
- `request_id`
- exposed result id
- interaction type
- position/rank
- `serving_mode`
- source attribution
- policy versions
- `experiment_id`

### Failure/timeout event

Intended fields:

- `timestamp`
- `request_id`
- `serving_mode`
- backend/source if known
- failure class
- timeout flag
- error code/message family
- latency until failure
- policy versions
- `experiment_id`
- `catalog_snapshot_id`

The event-family vocabulary is implemented as `TelemetryEventFamily`; field names and metric names are modeled as schema-plan contracts only. No logging hooks, route changes, metrics clients, or runtime production telemetry loop exist.

## 5. M8 metrics

Planned metrics:

- request count
- zero-result rate
- low-result rate
- top-k coverage
- latency `p50` / `p95` / `p99`
- ES latency
- Qdrant latency
- embedding latency
- fusion latency if fusion is implemented later
- rerank latency if reranking is implemented later
- backend failure rate
- timeout rate
- fallback-used rate only if fallback is later explicitly implemented
- click/order proxy metrics only if such data exists later
- manual relevance judgments if behavioral data does not exist

The planned telemetry metric-name vocabulary is implemented as `TelemetryMetricName`. Future metrics emission remains unimplemented.

Metric naming guidance:

- Reuse offline metric names in future telemetry where the meaning is the same.
- Do not invent production-only names for concepts already measured offline unless semantics materially differ.
- Keep seed/eval evidence metrics separate from any future online telemetry rollups.

## 6. M9 offline eval harness

This is a harness/reporting plan. The shared run metadata, metric-name, metric-value, query-slice, report-summary contracts, saved dataset/report format, stable saved-report format version, deterministic markdown renderer, static/in-memory runner skeleton, canonical static fixtures, checked-in example markdown artifact, first pure backend-runner interface/value-contract slice, pure ES/Qdrant offline adapter skeletons, pure backend-adapter failure-matrix fixtures/specs, and the resource-gated real-backend spike scaffold are implemented. Real successful ES/Qdrant backend runner execution remains unimplemented and disabled by default.

The uploaded 63-query BeautyQ seed-ready eval dataset, its full 63-query static rows, the `dataset_static_rows_ready` static scorecard, and the `not_eligible_no_explicit_resource_config` real-call checkpoint are implemented as offline eval contracts only (see section 1 for counts, anchors, and artifact paths). Full JSON parsing remains deferred. They are dataset/offline evidence only, not backend quality or activation approval: real backend calls stay disabled by default, default `/beauty-search` remains ES-backed, the Qdrant opt-in route stays disabled by default, and Qdrant production activation remains not approved.

Planned dataset coverage:

- curated canonical seed queries
- representative seed/eval fixture queries
- regression queries
- edge cases
- negative queries
- ambiguous queries
- exact-match queries
- semantic/fuzzy queries
- typo/noisy queries
- filter-heavy queries
- broad discovery queries

Planned evaluation modes:

- ES-only
- Qdrant-only
- future hybrid candidate union
- future weighted fusion
- future reciprocal rank fusion
- future reranker

Future hybrid/fusion/reranker modes are comparison slots only. They are not implemented serving behavior and must not be described as current production or opt-in behavior.

Dataset/reporting requirements:

- Every run should record `eval_dataset_id`.
- Every run should record `catalog_snapshot_id`.
- Query-class labels should be present before aggregate conclusions are accepted.
- The harness should support explicit regression subsets in addition to aggregate runs.

### M9 backend-runner execution seam

Pure interfaces and skeletons only. `M9OfflineEvalBackendRunner` models the future execution seam as value contracts and a manual/static adapter. `M9OfflineEvalRealBackendAdapterSkeleton` adds ES-only and Qdrant-only offline skeleton adapters that accept caller-supplied rows or return typed not-connected failures as data. They must not be described as production telemetry, production activation, route approval, serving approval, or real ES/Qdrant execution.
`M9OfflineEvalBackendAdapterFailureMatrix` adds the current pure failure/warning fixture matrix for those skeletons. It hardens the expected behavior before real ES/Qdrant clients exist and remains offline-only.

Backend-runner purpose:

- provide the seam for later turning the current M9 static report contracts into real offline eval execution over explicit offline inputs;
- connect future offline eval runs to real ES-only and Qdrant-only execution paths through explicit offline adapters only after real execution adapters are separately implemented;
- feed later backend results into the existing `M9OfflineEvalBackendRunner` interfaces;
- run against explicit offline inputs only;
- produce the existing `M9OfflineEvalSavedReport` artifact shape through the current saved-report model and renderer and the static runner/report-renderer path;
- never imply production activation.

Implemented pure interface vocabulary:

- `es_only_offline`;
- `qdrant_only_offline`;
- `future_hybrid_comparison`;
- `manual_static_sample`;
- `unknown`.

Implemented skeleton seams:

- ES-only offline adapter skeleton:
  - records `serving_mode = es_only` and `candidate_source = es`;
  - accepts caller-supplied static rows or returns typed not-connected/backend-failure data;
  - rejects source/mode mismatches deterministically;
  - warns when latency metrics are absent;
  - does not instantiate an ES client, call `/beauty-search`, or reuse production route/module/plugin surfaces.
- Qdrant-only offline adapter skeleton:
  - records `serving_mode = qdrant_only` and `candidate_source = qdrant`;
  - accepts caller-supplied static rows or returns typed not-connected/backend-failure data;
  - represents embedding/vector prerequisites as metadata warnings only;
  - rejects source/mode mismatches deterministically;
  - warns when latency metrics are absent;
  - does not instantiate a Qdrant client, activate the explicit opt-in route, or imply Qdrant is default production.

Future backend execution seams:

- ES-only offline execution seam:
  - runs explicit offline dataset queries against an approved offline ES adapter;
  - expected input: dataset query, filters/categories if supported, `catalog_snapshot_id`, `experiment_id`, `request_id`;
  - expected output: explicit `CandidateSource.Es`, top-k ids, candidate metadata if available, latency if available, warnings/failures as data;
  - records `serving_mode = es_only` and `candidate_source = es`;
  - must not reuse the production route path as an implicit execution surface;
  - must not make the default `POST /beauty-search` route a test harness;
  - must not hide ES failures as Qdrant success or fallback success.
- Qdrant-only offline execution seam:
  - runs explicit offline dataset queries against an approved offline Qdrant adapter;
  - expected input: dataset query, `query_class`, `catalog_snapshot_id`, `experiment_id`, `request_id`, embedding/vector prerequisites if required;
  - expected output: explicit `CandidateSource.Qdrant`, top-k ids, candidate metadata if available, latency if available, warnings/failures as data;
  - records `serving_mode = qdrant_only` and `candidate_source = qdrant`;
  - must remain separated from the default production route;
  - must not activate Qdrant production serving;
  - must not imply the explicit opt-in route is default production.
- future hybrid comparison seam:
  - planned comparison only;
  - compares ES-only and Qdrant-only results and may later materialize planned hybrid comparison rows;
  - may combine ES and Qdrant rows/candidates only in offline report space;
  - records explicit `serving_mode` and `candidate_source` values for every row;
  - must preserve per-candidate source attribution;
  - fusion/reranking policies must remain explicit values, never hidden behavior;
  - must not hide fallback, auto-supplement, fusion, reranking, or serving changes inside comparison logic;
  - does not implement route switch, fallback, or hybrid serving.

Required attribution rules:

- explicit candidate-source attribution;
- explicit serving-mode attribution;
- explicit query-class attribution;
- no hidden fallback;
- no backend failure rewritten as fallback success;
- no production route reuse unless separately approved in a different task.

Future input requirements:

- `eval_dataset_id`;
- `catalog_snapshot_id`;
- curated canonical seed queries;
- representative seed/eval fixture queries;
- regression, edge, negative, and ambiguous query cases;
- backend execution config;
- run id / `experiment_id`;
- `generated_at` value;
- activation approval status kept separate from runner inputs and separate from saved report output.

Future output requirements:

- saved report artifact using the existing `M9OfflineEvalSavedReport` format and renderer;
- per-query rows;
- top-k result ids;
- metrics;
- warnings;
- quality-gate decision;
- backend/source attribution;
- serving-mode attribution;
- query-class attribution;
- latency fields or latency metrics when available;
- explicit offline-eval, not-production-telemetry boundary wording.

Stop conditions for any later implementation:

- backend runner cannot run if `eval_dataset_id` is missing;
- backend runner cannot run if `catalog_snapshot_id` is missing;
- backend runner cannot run if `query_class` is missing where the adapter contract requires it;
- ES/Qdrant comparison cannot proceed if backend attribution is missing;
- adapter output is invalid if a backend failure occurs without a failure row or warning;
- adapter output is invalid if latency is unavailable and no warning explains that absence;
- offline metrics cannot be described as production quality;
- offline metrics cannot be described as production metrics;
- runner output cannot approve production activation;
- adapter output cannot be treated as activation approval;
- backend failures cannot be hidden as fallback success;
- hidden fallback is forbidden;
- the dataset cannot be only happy-path seed queries.

Implemented offline foundation:

- adapter failure fixtures.

Implementation candidates for a later patch:

- real ES offline execution adapter;
- real Qdrant offline execution adapter;
- saved-report integration spec using the existing M9 saved-report shape;
- quality-gate integration spec;
- focused specs that pin the offline boundary, attribution requirements, failure handling, and no-serving guarantees;
- no production route integration unless separately approved.

### 6.1. Real ES/Qdrant offline adapter execution gate

This section records the gate now implemented as scaffold in `M9OfflineEvalRealBackendResourceGate`. The scaffold can evaluate explicit enablement and required inputs and can emit not-configured/not-connected evidence through the existing M9 backend-runner/static-runner path. It does not implement real successful ES/Qdrant execution, route changes, or production activation.

#### 6.1.1. Execution gate purpose

- allow a future resource-gated offline adapter spike;
- run real ES-only and Qdrant-only offline queries only when explicitly enabled;
- preserve the existing M9 backend-runner interfaces, failure matrix, static runner, and saved report artifact shape;
- prevent accidental production activation or route reuse.

#### 6.1.2. Explicit enablement

Real backend offline adapter execution must be gated by an explicit enablement mechanism:

- require an env flag such as `RUN_REAL_BACKEND_OFFLINE_EVAL=1` or an equivalent test tag;
- default must be disabled/skipped;
- no real backend calls in normal focused validation;
- no real backend calls in normal unit tests;
- no production route calls;
- the gate must be checked before any real ES/Qdrant client is constructed or any backend network call is made.

#### 6.1.3. Required inputs before a real adapter spike may run

A real adapter spike must not run unless every required input is explicitly provided and validated:

| Input | Purpose | Example |
|---|---|---|
| `eval_dataset_id` | names the offline dataset or fixture bundle | `beautyq-m9-es-offline-v1` |
| `catalog_snapshot_id` | identifies the catalog snapshot being evaluated | `seed-resource-catalog` |
| explicit backend kind | ES or Qdrant; no implicit selection | `es_only_offline` or `qdrant_only_offline` |
| explicit candidate source | must match backend kind | `es` or `qdrant` |
| `experiment_id` | labels the run for traceability | `m9-real-es-spike-001` |
| `request_id` policy | per-query correlation id strategy | caller-supplied or auto-generated |
| backend endpoint/resource config | ES endpoint or Qdrant endpoint if applicable | `ElasticsearchPortCfg` or `QdrantPortCfg` |
| `query_class` | explicit taxonomy label per query | `ExactProductNameBrand` |
| max top-k | caps candidate count per query | `20` |
| timeout budget | per-query and per-run timeout | `30s` per query |
| failure/warning handling policy | how failures become data or warnings | `RightFailureRowsAsData` |
| saved report output path | where the M9 artifact is written | `./.beautyq-evidence-runs/<run>/artifacts/` |
| production activation non-approval confirmation | explicit statement that this run does not approve production activation | required in every saved report warning |

#### 6.1.4. ES real adapter gate

The real ES offline adapter must satisfy all of the following:

- must use an explicit offline adapter surface, not `LeaderboardPlugin` or `/beauty-search`;
- must not rely on the default production route;
- must preserve `CandidateSource.Es` and `ServingMode.EsOnly` attribution;
- ES failures become data/warnings through the existing `M9OfflineEvalBackendRunner` failure-row mechanism;
- unavailable latency becomes a warning, not a hard failure;
- no fallback to Qdrant;
- no reuse of `BeautySearchRouteModules.apiElasticsearch` as an execution surface;
- no Distage module integration unless a separate explicit resource-gating design is approved;
- the adapter must be constructable without the production plugin graph.

#### 6.1.5. Qdrant real adapter gate

The real Qdrant offline adapter must satisfy all of the following:

- must use an explicit offline adapter surface, not the default production route;
- must not activate the explicit opt-in route as default;
- must preserve `CandidateSource.Qdrant` and `ServingMode.QdrantOnly` attribution;
- vector/embedding prerequisites must be explicit (embedding model identity, vector name, dimension, distance);
- Qdrant failures become data/warnings through the existing failure-row mechanism;
- unavailable latency becomes a warning, not a hard failure;
- no fallback to ES;
- no reuse of `BeautySearchRouteModules.apiQdrantExplicitOptIn` as an execution surface;
- no Distage module integration unless a separate explicit resource-gating design is approved;
- the adapter must be constructable without the production plugin graph or the explicit opt-in route.

#### 6.1.6. Saved report/evidence output

Real adapter output must satisfy all of the following:

- output must feed `M9OfflineEvalBackendRunner` through the existing `toStaticRunInput` path;
- output must pass the option121 failure matrix expectations;
- output must assemble through `M9OfflineEvalStaticRunner`;
- output must render through `M9OfflineEvalReportRenderer`;
- the saved report must state that it is offline eval only, not production telemetry;
- the saved report must not be used as production activation approval;
- every saved report must carry the `production_activation_not_approved` warning;
- the saved artifact must use the existing `M9OfflineEvalSavedReport` format version.

#### 6.1.7. Stop conditions

A real adapter spike must stop or not start if any of the following are true:

- missing explicit enablement flag or test tag;
- missing `eval_dataset_id`, `catalog_snapshot_id`, backend kind, candidate source, or `query_class`;
- backend endpoint not configured;
- hidden fallback attempted (e.g., ES failure silently replaced by Qdrant result);
- candidate source missing or mismatched with adapter kind;
- route/plugin/DI/HTTP production path used as execution surface;
- production telemetry emitted;
- report claims production activation is approved;
- normal unit/focused validation attempts a real backend call;
- the run is not explicitly marked as offline eval only.

#### 6.1.8. Future implementation slices

Resource-gated spike scaffolding exists; remaining planned slices are:

| Slice | Scope | Dependencies |
|---|---|---|
| 123 | resource-gated adapter spike scaffold | implemented: env flag, explicit inputs, not-configured data, attribution preservation |
| 124 | failure-matrix parity spec for successful real backend outputs | future real execution branch; option121 failure matrix |
| 125 | saved-report artifact capture for gated successful run | implemented: deterministic capture, gate/resource/non-approval recording, byte-for-byte stable checked-in artifacts |
| 126 | resource-gated ES-only smoke | future real execution branch; ES endpoint config |
| 127 | resource-gated Qdrant-only smoke | implemented: Qdrant-only gated smoke evidence, not-configured/default-disabled artifacts, embedding/vector prerequisite warnings |
| 128 | offline comparison report over real backend outputs | slices 126+127; existing M9 comparison path |
| 132 | resource-gated real-call checkpoint contract | implemented: pure eligibility decision over static scorecard + resource config + operator approval; default blocks without explicit resource config; ES-only/Qdrant-only/both-with-approval eligibility is offline/resource-gated only; production activation stays not approved; byte-for-byte checked-in artifact |
| later | no production route integration unless separately approved | separate approval required |

Implemented slice 123 does not implement real backend clients, route changes, Distage module integration, or production activation.

#### 6.1.9. Execution gate milestone — closed as planning/reporting only (options 142–145B)

This milestone adds the following accepted offline planning/reporting contracts on top of the gate design above, and is now closed:

- a runbook/evidence consistency contract (`M9BeautyQSearchEvalRealResourceRunbookConsistency`) that statically checks the future-only runbook in `docs/local/BEAUTYQ_M9_REAL_RESOURCE_SMOKE_RUNBOOK.md` against the saved evidence schema and the checked-in default/no-config evidence artifact;
- separate ES-only, Qdrant-only, and combined ES/Qdrant execution gate designs (`M9BeautyQSearchEvalEsOnlyExecutionGateDesign`, `M9BeautyQSearchEvalQdrantOnlyExecutionGateDesign`, `M9BeautyQSearchEvalCombinedExecutionGateDesign`) that consume the prerequisites audit and corresponding plan, reach `pending_explicit_execution_task` only when prerequisites are complete, and block on default/no-config or non-ready scorecard/checkpoint states;
- a deterministic execution-gate renderer (`M9BeautyQSearchEvalRealResourceExecutionGateRenderer`) and a checked-in default/no-config markdown artifact (`bifunctor-tagless/src/test/resources/leaderboard/search/eval/m9-beautyq-real-resource-execution-gate-default.md`) that renders blocked/skip evidence for all three gates, never success.

All of the above is planning/design/reporting only: no real ES/Qdrant execution, no route/plugin/DI/HTTP involvement, no production activation, route switch, hybrid serving, fallback, score fusion, reranking, or production telemetry. Full suite after option145B: 1440 tests succeeded, 0 failed, 0 aborted, 1 canceled, 2 pending.

## 7. M9 offline metrics

Planned offline metrics:

- Recall@K
- MRR
- nDCG@K
- zero-result rate
- low-result rate
- top-k overlap
- backend contribution ratio
- latency
- failure count
- regression pass/fail
- quality gate decision

These metric names are implemented as `OfflineEvalMetricName.plannedM9Metrics`. Report summaries can carry metadata, query-class slices, and aggregate metrics. `M9OfflineEvalSavedReport` can also carry per-query rows, aggregate metrics, quality-gate decision text, notes, and warnings for deterministic markdown rendering, but no ES/Qdrant querying runner is implemented by this slice.

Reporting rule:

- Offline metrics must be reported by `query_class`, not only globally.
- Global aggregates alone are insufficient because they can hide regressions in exact, semantic, negative, or filter-heavy slices.

## 8. Query class taxonomy

Initial query classes:

- exact product/name/brand
- category
- ingredient/attribute
- semantic descriptive
- typo/noisy
- filter-heavy
- broad discovery
- ambiguous
- negative/out-of-catalog

Initial backend hypotheses to test:

- ES is likely stronger for exact and filter-heavy queries.
- Qdrant is likely stronger for semantic/fuzzy queries.
- Hybrid may help ambiguous or mixed-intent queries.
- Evidence can overturn these hypotheses.

This taxonomy is implemented as `QueryClass.stableOrder` and should drive both offline reporting and any future telemetry rollups. A future implementation should not rely only on overall averages.

## 9. Report artifacts

Planned saved artifacts:

- offline eval report in markdown and/or JSON
- metric table
- per-query result table
- regression failure list
- latency summary
- backend attribution summary
- quality gate decision
- comparison notes for ES vs Qdrant vs future hybrid

Artifact guidance:

- The M9 markdown saved-report artifact is implemented as `M9OfflineEvalReportRenderer.markdownArtifact`; it is deterministic and states that backend execution is not represented by the artifact.
- The canonical static example artifact is `bifunctor-tagless/src/test/resources/leaderboard/search/eval/m9-static-example-report.md`; it is fixture/sample evidence shape only and uses `sample_not_for_activation`.
- Saved artifacts should preserve the shared vocabulary fields from section 3.
- Backend attribution summaries should use `candidate_source` and `serving_mode`.
- Comparison artifacts should keep offline evidence separate from any future telemetry dashboards or online summaries.

## 10. Stop conditions

Stop or do not advance to implementation if any of the following is true:

- telemetry schema wording implies production activation;
- offline eval wording implies production telemetry;
- query classes are undefined;
- the seed/eval set is only happy-path;
- metrics are global only and hide regressions;
- latency is unmeasured;
- backend attribution is missing;
- rollback/disable path is undefined for any later activation shape.

Also stop if:

- metric names diverge between offline eval and future telemetry without a justified semantic reason;
- `catalog_snapshot_id` and `eval_dataset_id` are conflated;
- planned `serving_mode` or `candidate_source` values are free text instead of stable enumerated vocabulary.

## 11. Implementation handoff candidates

Future implementation slices may include:

- M8 telemetry emission hooks
- M8 metric emission hooks
- M9 offline eval runner that reads the saved dataset format
- M9 ES/Qdrant comparison runner after real offline execution adapters exist
- M9 report persistence/writer around the implemented renderer
- M9 quality gate update
- Real successful ES/Qdrant offline adapter execution branch behind the explicit resource gate defined in section 6.1

These are handoff candidates only. This document does not approve code changes, tests, route wiring changes, plugin changes, DI changes, or HTTP changes.

For the future-only operator view of ES-only, Qdrant-only, and combined ES/Qdrant resource-smoke execution, see `docs/local/BEAUTYQ_M9_REAL_RESOURCE_SMOKE_RUNBOOK.md`. That runbook is future-only and approves no real ES/Qdrant execution.

## 12. M10 closeout (downstream of this plan)

M10 (query classification, offline routing policy, full 63-query classification coverage, and retrieval-policy readiness) is closed as offline planning/reporting only, reusing the shared vocabulary and query-class taxonomy from sections 3 and 8 of this doc. See `docs/BEAUTYQ_CURRENT_STATE_AND_HANDOFF.md` section 0 for the consolidated counts, M11 input-group mapping, and readiness verdict. M10 prepares offline M11 candidate-generation inputs only; it does not implement M11 candidate generation, does not execute real ES/Qdrant, and does not change production routing.
