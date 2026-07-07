# M13 BeautyQ Controlled Explicit Opt-In Route Planning

Offline planning/contract artifact for a FUTURE explicit opt-in route/module path. This is
planning/contract work only: it is non-serving by default and does NOT alter existing production
/beauty-search. It defines stable route-planning states and denied route-drift cases. It is
NOT a route activation, NOT a route switch, NOT a serving approval, NOT a production-readiness claim,
and NOT a real backend execution: no ES or Qdrant client is created and neither backend is run, and
no route, plugin, DI, or HTTP behavior is required or implemented. It introduces no hybrid serving,
no fallback, no score fusion, no reranking execution, and no production telemetry.

It separates the current production ES-backed default route, the disabled-by-default explicit
Qdrant opt-in path, the future controlled experiment route/module planning, and the non-approved
production activation. It consumes the M12 closeout source truth (offline eval/planning/reporting
only) as planning input via accepted verdicts only; it never treats M12 placeholder rows as
quality evidence and fabricates no evidence from them. This artifact reports M13 route-planning
readiness only (planning-only): it is not quality-green, not retrieval-quality, not production
readiness, not route activation, and not serving approval.

## Artifact identity

- artifact_id: m13-beautyq-controlled-opt-in-route-planning
- artifact_version: v1
- dataset_id: wandsbek_hamburg_beauty_services_seed_ready

## Summary

- verdict: m13_controlled_opt_in_route_planning_ready_planning_only
- consumed_m12b_policy_catalog_verdict: m12_fusion_reranking_policy_catalog_ready_schema_only
- consumed_m12b_experiment_plan_verdict: m12_fusion_reranking_experiment_plan_ready_schema_only
- consumed_m12c_boundary_failure_matrix_verdict: m12_fusion_reranking_boundary_failure_matrix_ready_schema_only
- consumed_m12d_saved_output_schema_verdict: m12_fusion_reranking_saved_output_schema_ready_placeholder_only
- route_planning_state_count: 5
- denied_drift_case_count: 15
- m13_route_planning_ready: true

## Route planning states

Each state is a planning stance only, never an activated route, a route switch, a serving approval,
or a real backend execution. The boundary_holds column reports that the standing offline boundary
confirms the stance.

| route_planning_state | boundary_holds | rationale |
|---|---|---|
| current_es_default_preserved | true | Planning stance only: production /beauty-search stays ES-backed; M13A preserves the current default route and switches nothing. |
| explicit_qdrant_opt_in_disabled_by_default | true | Planning stance only: the explicit Qdrant opt-in path stays disabled by default; M13A neither enables nor implicitly activates it. |
| experiment_route_planning_only | true | Planning stance only: a future controlled experiment route/module is planned on paper; no route, plugin, DI, or HTTP path is required or implemented. |
| production_activation_not_approved | true | Planning stance only: Qdrant production activation is not approved; M13A is not a production route activation. |
| serving_approval_not_granted | true | Planning stance only: serving approval is not granted; M13A claims no production readiness and grants no serving approval. |

## Denied route-drift cases

Each drift case is explicitly denied. The denial_holds column reports that the standing offline
boundary confirms the corresponding posture stays off. No denied case is ever permitted by M13A.

| denied_drift_case | decision | denial_holds | rationale |
|---|---|---|---|
| default_route_switch | denied | true | Denied: M13A does not switch the default route; production /beauty-search stays ES-backed. |
| production_route_activation | denied | true | Denied: M13A does not activate any production route; route activation is out of scope. |
| implicit_qdrant_activation | denied | true | Denied: the Qdrant opt-in stays disabled by default; M13A never implicitly activates Qdrant. |
| real_backend_execution | denied | true | Denied: M13A requires and implements no real ES/Qdrant/backend client execution. |
| route_plugin_di_http_change | denied | true | Denied: M13A introduces no route, plugin, DI, or HTTP behavior change. |
| hybrid_serving | denied | true | Denied: M13A introduces no hybrid serving. |
| fallback | denied | true | Denied: M13A introduces no fallback. |
| score_fusion | denied | true | Denied: M13A introduces no score fusion. |
| reranking_execution | denied | true | Denied: M13A introduces no reranking execution. |
| production_telemetry | denied | true | Denied: M13A introduces no production telemetry. |
| quality_green_claim | denied | true | Denied: M13A makes no quality-green claim. |
| retrieval_quality_claim | denied | true | Denied: M13A makes no retrieval-quality claim. |
| production_readiness_claim | denied | true | Denied: M13A makes no production-readiness claim. |
| route_activation_claim | denied | true | Denied: M13A makes no route-activation claim. |
| serving_approval_claim | denied | true | Denied: M13A makes no serving-approval claim. |

## Metrics

| metric | value |
|---|---|
| artifact_id | m13-beautyq-controlled-opt-in-route-planning |
| artifact_version | v1 |
| consumed_m12b_policy_catalog_verdict | m12_fusion_reranking_policy_catalog_ready_schema_only |
| consumed_m12b_experiment_plan_verdict | m12_fusion_reranking_experiment_plan_ready_schema_only |
| consumed_m12c_boundary_failure_matrix_verdict | m12_fusion_reranking_boundary_failure_matrix_ready_schema_only |
| consumed_m12d_saved_output_schema_verdict | m12_fusion_reranking_saved_output_schema_ready_placeholder_only |
| route_planning_state_count | 5 |
| route_planning_states_unique | 5 |
| route_planning_states_boundary_holds | 5 |
| denied_drift_case_count | 15 |
| denied_drift_cases_unique | 15 |
| denied_drift_cases_denial_holds | 15 |
| denied_drift_cases_all_denied | true |
| m13_route_planning_ready | true |
| m13_is_planning_only_not_route_activation | true |
| m13_is_planning_only_not_route_switch | true |
| m13_is_planning_only_not_serving_approval | true |
| m13_is_planning_only_not_production_readiness | true |
| m13_is_planning_only_not_real_backend_execution | true |
| m13_is_non_serving_by_default | true |
| m13_does_not_alter_production_beauty_search | true |
| m13_separates_current_es_default_route | true |
| m13_separates_disabled_qdrant_opt_in_path | true |
| m13_separates_future_experiment_route_planning | true |
| m13_separates_non_approved_production_activation | true |
| m13_consumes_m12_closeout_as_planning_input_only | true |
| m13_does_not_treat_m12_placeholder_rows_as_quality_evidence | true |
| m13_fabricates_no_evidence_from_m12_placeholder_rows | true |
| m13_requires_no_route_plugin_di_http_path | true |
| m13_implements_no_route_plugin_di_http_path | true |
| m13_requires_no_real_es_qdrant_backend_client | true |
| m13_implements_no_real_es_qdrant_backend_client | true |
| m13_introduces_no_hybrid_serving | true |
| m13_introduces_no_fallback | true |
| m13_introduces_no_score_fusion | true |
| m13_introduces_no_reranking_execution | true |
| m13_introduces_no_production_telemetry | true |
| default_beauty_search_es_backed | true |
| qdrant_opt_in_disabled_by_default | true |
| qdrant_production_activation_approved | false |
| production_route_activated | false |
| default_route_switched | false |
| production_beauty_search_called | false |
| es_client_created | false |
| qdrant_client_created | false |
| es_executed | false |
| qdrant_executed | false |
| route_plugin_di_http_involved | false |
| real_backend_call_required | false |
| real_backend_call_implemented | false |
| hybrid_serving_implied | false |
| fallback_implied | false |
| score_fusion_implied | false |
| reranking_implied | false |
| production_telemetry_implied | false |
| quality_green_claimed | false |
| production_readiness_claimed | false |
| route_activation_claimed | false |
| serving_approval_claimed | false |
| verdict | m13_controlled_opt_in_route_planning_ready_planning_only |

## Boundary summary

Boundary posture is reported as structured booleans in the Metrics table above, not as prose negations.
This is a planning/contract artifact, not a route activation, not a route switch, not a serving
approval, not a production-readiness claim, and not a real backend execution: no production route,
hybrid serving, fallback, score fusion, reranking execution, production telemetry, backend client,
or route/plugin/DI/HTTP change is implemented or claimed, and the M12 closeout source truth is
consumed as planning input only, never as quality evidence.
