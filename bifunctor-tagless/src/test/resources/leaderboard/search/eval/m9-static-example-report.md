# M9 Offline Eval Saved Report

This artifact is a saved offline-eval format/rendering record. Backend execution is not represented by this artifact.

## Format

- format_version: m9-offline-eval-saved-report-v1
- generated_at: 2026-06-20T00:00:00Z
- eval_dataset_id: beautyq-m9-static-fixture-v1
- catalog_snapshot_id: seed-resource-catalog-static-fixture
- quality_gate_decision: sample_not_for_activation

## Metadata

- serving_mode: unknown
- candidate_source: manual
- routing_policy_id: m9-static-fixture-no-serving-v1
- fusion_policy: none
- reranker_policy: none
- experiment_id: m9-static-fixture-example
- metadata_catalog_snapshot_id: seed-resource-catalog-static-fixture
- metadata_eval_dataset_id: beautyq-m9-static-fixture-v1
- metric_window: static-fixture

## Notes

- Canonical static M9 fixture for saved-report artifact shape.
- All rows and expected results are fixture/sample values, not production metrics or real relevance judgments.
- Non-serving boundary: this artifact was generated from static fixture rows only; it did not query Elasticsearch, Qdrant, HTTP, routes, Distage, Docker, or production backends.

## Warnings

- Example-only artifact; do not use for activation approval.
- Offline ES/Qdrant backend runner remains future work.
- Hybrid serving, score fusion, reranking, and production telemetry emission are not represented.

## Aggregate Metrics

| metric | value |
|---|---|
| recall@k | 0.75 |
| zero_result_rate | 0.25 |
| failure_count | 0 |
| quality_gate_decision | sample_not_for_activation |

## Query Rows

| query_id | query_class | serving_mode | candidate_source | top_k_result_ids | metrics | regression_status | warnings |
|---|---|---|---|---|---|---|---|
| m9_static_exact_001 | exact_product_name_brand | unknown | manual | fixture-variant-aveda-botanical-repair | recall@k=1.0; regression_pass_fail=sample_pass | sample_pass | - |
| m9_static_semantic_001 | semantic_descriptive | unknown | manual | fixture-variant-calming-sensitive-skin | recall@k=1.0; mrr=1.0; regression_pass_fail=sample_review | sample_review | Fixture/sample relevance only. |
| m9_static_ambiguous_001 | ambiguous | unknown | manual | fixture-variant-glow-facial, fixture-variant-glow-serum | recall@k=1.0; low_result_rate=0.0; regression_pass_fail=sample_review | sample_review | Ambiguous fixture row; no real adjudication. |
| m9_static_negative_001 | negative_out_of_catalog | unknown | manual | - | zero_result_rate=1.0; regression_pass_fail=sample_pass | sample_pass | Negative/out-of-catalog fixture row. |
