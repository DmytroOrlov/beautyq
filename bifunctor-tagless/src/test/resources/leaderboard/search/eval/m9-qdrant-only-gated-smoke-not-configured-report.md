# M9 Offline Eval Saved Report

This artifact is a saved offline-eval format/rendering record. Backend execution is not represented by this artifact.

## Format

- format_version: m9-offline-eval-saved-report-v1
- generated_at: 2026-06-20T15:00:00Z
- eval_dataset_id: beautyq-m9-qdrant-only-gated-smoke-v1
- catalog_snapshot_id: seed-resource-catalog
- quality_gate_decision: not_evaluated_not_for_activation

## Metadata

- serving_mode: qdrant_only
- candidate_source: qdrant
- routing_policy_id: m9-qdrant-only-gated-smoke-qdrant-v1
- fusion_policy: none
- reranker_policy: none
- experiment_id: m9-qdrant-only-gated-smoke-spec
- metadata_catalog_snapshot_id: seed-resource-catalog
- metadata_eval_dataset_id: beautyq-m9-qdrant-only-gated-smoke-v1
- metric_window: offline-qdrant-smoke

## Notes

- m9-qdrant-only-gated-smoke-spec
- Offline eval evidence only; not production telemetry; not production activation approval.

## Warnings

- Qdrant-only gated smoke offline eval only; production activation not approved
- qdrant-only smoke requires embedding model and vector config; embedding/vector prerequisites are not configured
- backend resource config is absent; adapter must emit not-configured data
- gate_status=allowed_without_resource_config
- resource_config_present=false
- production_activation_not_approved_confirmed=true
- real_backend_call_implemented=false
- artifact_capture_gate_status=allowed_without_resource_config
- artifact_capture_resource_config_present=false
- artifact_capture_real_backend_call_implemented=false
- Offline eval evidence only; not production telemetry; not production activation approval.

## Aggregate Metrics

| metric | value |
|---|---|
| quality_gate_decision | not_evaluated_not_for_activation |

## Query Rows

| query_id | query_class | serving_mode | candidate_source | top_k_result_ids | metrics | regression_status | warnings |
|---|---|---|---|---|---|---|---|
| q_qdrant_smoke_semantic_001 | semantic_descriptive | qdrant_only | qdrant | - | failure_count=1 | failed | gate_status=allowed_without_resource_config, backend failure: qdrant offline adapter resource config is not configured; no real backend call attempted |
| q_qdrant_smoke_broad_001 | broad_discovery | qdrant_only | qdrant | - | failure_count=1 | failed | gate_status=allowed_without_resource_config, backend failure: qdrant offline adapter resource config is not configured; no real backend call attempted |
