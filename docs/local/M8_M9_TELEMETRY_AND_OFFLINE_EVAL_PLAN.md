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

This is a harness/reporting plan. The shared run metadata, metric-name, metric-value, query-slice, report-summary contracts, saved dataset/report format, stable saved-report format version, deterministic markdown renderer, static/in-memory runner skeleton, canonical static fixtures, checked-in example markdown artifact, first pure backend-runner interface/value-contract slice, pure ES/Qdrant offline adapter skeletons, and pure backend-adapter failure-matrix fixtures/specs are implemented. Real ES/Qdrant backend runner execution remains unimplemented.

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

These are handoff candidates only. This document does not approve code changes, tests, route wiring changes, plugin changes, DI changes, or HTTP changes.
