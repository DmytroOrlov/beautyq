# M9 Combined ES+Qdrant Gated Smoke Evidence Index

This artifact is an offline eval reporting/index layer only.
It summarizes ES-only and Qdrant-only gated smoke evidence artifacts.
No real backend calls are represented; no backend execution branch is added.

Offline eval evidence only; not production telemetry; not production activation approval.

## Boundaries

- offline_eval_evidence_only: true
- not_production_telemetry: true
- not_activation_approval: true
- real_backend_call_implemented: false
- production_activation_not_approved_confirmed: true
- no_route_or_plugin_or_di_or_http_source_change: true
- no_hybrid_or_fallback_or_fusion_or_reranking: true

## ES-only gated smoke evidence

- artifact_filename: m9-es-only-gated-smoke-not-configured-report.md
- es_source: es
- es_execution_mode: es_only_offline
- es_serving_mode: es_only
- gate_status: allowed_without_resource_config
- es_resource_config_present: false
- real_backend_call_implemented: false
- production_activation_not_approved_confirmed: true
- offline_eval_evidence_only: true
- not_production_telemetry: true
- not_activation_approval: true
- es_warnings:
  - ES-only gated smoke offline eval only; production activation not approved
  - backend resource config is absent; adapter must emit not-configured data
  - gate_status=allowed_without_resource_config
  - resource_config_present=false
  - production_activation_not_approved_confirmed=true
  - real_backend_call_implemented=false
  - artifact_capture_gate_status=allowed_without_resource_config
  - artifact_capture_resource_config_present=false
  - artifact_capture_real_backend_call_implemented=false
  - Offline eval evidence only; not production telemetry; not production activation approval.
- es_notes:
  - m9-es-only-gated-smoke-spec
  - Offline eval evidence only; not production telemetry; not production activation approval.

## Qdrant-only gated smoke evidence

- artifact_filename: m9-qdrant-only-gated-smoke-not-configured-report.md
- qdrant_source: qdrant
- qdrant_execution_mode: qdrant_only_offline
- qdrant_serving_mode: qdrant_only
- gate_status: allowed_without_resource_config
- qdrant_resource_config_present: false
- real_backend_call_implemented: false
- production_activation_not_approved_confirmed: true
- offline_eval_evidence_only: true
- not_production_telemetry: true
- not_activation_approval: true
- qdrant_warnings:
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
- qdrant_notes:
  - m9-qdrant-only-gated-smoke-spec
  - Offline eval evidence only; not production telemetry; not production activation approval.

## Pointers to checked-in per-backend artifacts

- es_artifact_filename: m9-es-only-gated-smoke-not-configured-report.md
- qdrant_artifact_filename: m9-qdrant-only-gated-smoke-not-configured-report.md

## Notes

- reporting/index layer only
- no real backend call
- no production telemetry
- no activation claim
- explicit not-implemented policy declared in Boundaries
