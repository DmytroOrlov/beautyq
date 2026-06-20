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
- offline eval harness code.

Implemented first slice:

- `leaderboard.search.eval.M8M9EvalContracts` defines shared vocabulary, offline eval report metadata/summary contracts, and telemetry schema-plan contracts.
- `leaderboard.search.M8M9EvalContractsSpec` locks stable string rendering, query-class taxonomy coverage, planned M9 metric names, planned M8 event families, and the vocabulary-only status of hybrid/fusion/reranker terms.
- This slice is pure/non-serving. It adds no telemetry emission, metrics client, route wiring, plugin wiring, HTTP behavior, ES/Qdrant runner, hybrid serving, fusion, or reranking.

M8 and M9 are paired because they need the same vocabulary for backend/source attribution, policy naming, query-class reporting, and report artifacts. Offline eval should establish that vocabulary first so future production telemetry can reuse the same terms and metric names where possible. Seed/eval evidence must remain separate from production telemetry, and there is no real production traffic in this project context yet.

## 2. Current baseline

Current source-backed baseline:

- Default production `POST /beauty-search` remains ES-backed.
- Default production route exposure remains `LeaderboardPlugin.modules.apiBase[IO]` plus `BeautySearchRouteModules.apiElasticsearch`.
- Current production backend remains seed-resource catalog snapshot + `ElasticsearchSearchBackend`.
- `BeautySearchRouteModules.apiQdrantExplicitOptIn` exists and is disabled by default.
- Matching explicit opt-in Qdrant module names remain:
  - `BeautySearchRouteModules.seedCatalogQdrantExplicitOptIn`
  - `BeautySearchCatalogBackendModules.seedResourceQdrantExplicitOptIn`
- Qdrant explicit opt-in support classes remain:
  - `QdrantExplicitOptInBeautySearchBackend`
  - `QdrantExplicitOptInRoutePrerequisites`
- Full suite was previously reported green: `1189` run, `1189` succeeded, `0` failed, `0` aborted, `1` canceled, `2` pending.
- Deterministic explicit opt-in smoke was previously reported green: `40` succeeded, `0` failed, `0` aborted, `0` pending.
- Real resource-gated Qdrant smoke was previously reported green: `7` succeeded, `0` failed, `0` aborted, `0` pending.
- Production activation is not approved.
- Hybrid serving is not implemented.
- Route switch is not implemented.
- ES<->Qdrant fallback is not implemented.
- Score fusion is not implemented.
- Reranking is not implemented.
- `HybridServe` is not implemented.
- Qdrant auto-supplement is not implemented.
- Shadow serving is not implemented.
- Traffic mirroring is not implemented.
- Production telemetry loop is not implemented.
- There is no real production traffic in this project context yet.

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

This is a schema-plan contract, not telemetry emission.

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

This is a harness/reporting plan. The shared run metadata, metric-name, metric-value, query-slice, and report-summary contracts are implemented; the offline runner/harness remains unimplemented.

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

These metric names are implemented as `OfflineEvalMetricName.plannedM9Metrics`. Report summaries can carry metadata, query-class slices, and aggregate metrics, but no ES/Qdrant querying runner or report writer is implemented by this slice.

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
- M9 offline eval dataset format
- M9 ES/Qdrant comparison runner
- M9 report writer
- M9 quality gate update

These are handoff candidates only. This document does not approve code changes, tests, route wiring changes, plugin changes, DI changes, or HTTP changes.
