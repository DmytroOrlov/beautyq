# M9 Offline Eval Saved Report

This artifact is a saved offline-eval format/rendering record. Backend execution is not represented by this artifact.

## Format

- format_version: m9-offline-eval-saved-report-v1
- generated_at: 2026-06-21T00:00:00Z
- eval_dataset_id: wandsbek_hamburg_beauty_services_seed_ready
- catalog_snapshot_id: seed-resource-catalog
- quality_gate_decision: static_dataset_mapping_only

## Metadata

- serving_mode: unknown
- candidate_source: manual
- routing_policy_id: m9-beautyq-eval-dataset-static-rows-v1
- fusion_policy: none
- reranker_policy: none
- experiment_id: m9-beautyq-eval-dataset-static-rows
- metadata_catalog_snapshot_id: seed-resource-catalog
- metadata_eval_dataset_id: wandsbek_hamburg_beauty_services_seed_ready
- metric_window: static-dataset-fixture

## Notes

- Dataset fixture: wandsbek_hamburg_beauty_services_seed_ready; version=1; full_query_count=63.
- Language counts: ru=31, en=21, de=6, mixed=5.
- Target carousels carried from dataset metadata: variantCarousel, providerCarousel, serviceIntentCarousel.
- Representative static rows mapped: q_nails_001, q_nails_003, q_noise_005.
- Full 63-query row expansion remains future work; this slice validates resource anchors instead of parsing the complete JSON.
- Default /beauty-search remains ES-backed; Qdrant production activation remains not approved.

## Warnings

- Offline dataset fixture mapping only; not production telemetry and not activation approval.
- Real backend calls remain disabled by default and are not required to build this report.
- No JSON parser/dependency is used; full JSON parsing is intentionally deferred.
- Representative subset only: mapped_row_count=3; full_dataset_query_count=63.

## Aggregate Metrics

| metric | value |
|---|---|
| failure_count | 0 |
| quality_gate_decision | static_dataset_mapping_only |
| recall@k | not_measured |
| top_k_overlap | mapped_rows=3;full_dataset_queries=63 |

## Query Rows

| query_id | query_class | serving_mode | candidate_source | top_k_result_ids | metrics | regression_status | warnings |
|---|---|---|---|---|---|---|---|
| q_nails_001 | ingredient_attribute | unknown | manual | c82d90c3-d9e4-5f0b-8689-6476c5e7fe35, 989e0858-bc32-5b71-a355-6ce1e20b0cb1, a1085253-a9bf-517c-80c4-262b0bf9a5a4 | regression_pass_fail=unknown; quality_gate_decision=static_dataset_mapping_only | unknown | Static dataset fixture mapping only; top_k_result_ids are fixture anchors, not backend retrieval results., No ES, Qdrant, route, plugin, DI, HTTP, hybrid, fusion, or reranking execution is represented. |
| q_nails_003 | filter_heavy | unknown | manual | 798c4326-e081-59a9-b659-98671f1fd656, 78fdf5d2-0f92-5c2c-b20d-e5d2549d1c52, a1085253-a9bf-517c-80c4-262b0bf9a5a4 | regression_pass_fail=unknown; quality_gate_decision=static_dataset_mapping_only | unknown | Static dataset fixture mapping only; top_k_result_ids are fixture anchors, not backend retrieval results., No ES, Qdrant, route, plugin, DI, HTTP, hybrid, fusion, or reranking execution is represented. |
| q_noise_005 | ambiguous | unknown | manual | 4f5d8aa6-d826-50a5-bd06-f19eee2bd9c7, d658c194-38f7-5396-b8cb-cf155739c235, 504424ba-7d46-5cc9-a6b7-1ee064e610fd | regression_pass_fail=unknown; quality_gate_decision=static_dataset_mapping_only | unknown | Static dataset fixture mapping only; top_k_result_ids are fixture anchors, not backend retrieval results., No ES, Qdrant, route, plugin, DI, HTTP, hybrid, fusion, or reranking execution is represented. |
