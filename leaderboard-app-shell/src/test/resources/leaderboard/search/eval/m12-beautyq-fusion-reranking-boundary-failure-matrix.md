# M12 BeautyQ Fusion/Reranking Boundary/Failure Matrix

Offline fusion/reranking boundary/failure matrix over the accepted M12B fusion/reranking policy
catalog and experiment-plan schema. This is an offline planning/eval artifact only. It is a
boundary/failure matrix only and is NOT scoring, NOT fusion execution, NOT reranking execution,
NOT candidate retrieval, NOT backend execution, and NOT production routing: no ES or Qdrant
client is created and neither backend is run. Each row is a data-only accepted/denied/skipped
decision with an explicit reason code about how the M12B policy catalog and experiment-plan
schema must handle one policy-handling case. Policies and plan rows remain non-executable and
no candidate ids, provider ids, scores, ranks, backend responses, fused scores, reranked
positions, quality labels, fusion outputs, or reranking outputs are fabricated. This artifact
reports M12 offline boundary-matrix readiness only (schema-only): it is not scoring readiness,
not fusion/reranking execution readiness, not retrieval quality, not production readiness, not
route activation, not serving approval, and not actual execution readiness.

## Artifact identity

- artifact_id: m12-beautyq-fusion-reranking-boundary-failure-matrix
- artifact_version: v1
- dataset_id: wandsbek_hamburg_beauty_services_seed_ready

## Summary

- verdict: m12_fusion_reranking_boundary_failure_matrix_ready_schema_only
- consumed_m12b_policy_catalog_verdict: m12_fusion_reranking_policy_catalog_ready_schema_only
- consumed_m12b_experiment_plan_verdict: m12_fusion_reranking_experiment_plan_ready_schema_only
- consumed_m12a_input_scaffold_verdict: m12_fusion_reranking_input_scaffold_ready_schema_only
- consumed_m11b_result_schema_verdict: m11_candidate_generation_result_schema_ready
- consumed_m11c_boundary_failure_matrix_verdict: m11_candidate_generation_boundary_failure_matrix_ready
- consumed_policy_catalog_name_count: 6
- consumed_policy_catalog_names_unique: true
- consumed_experiment_plan_row_count: 89
- consumed_experiment_plan_es_baseline_rows: 15
- consumed_experiment_plan_qdrant_baseline_rows: 1
- consumed_experiment_plan_combined_rows: 72
- consumed_experiment_plan_accepted_negative_control_rows: 1
- consumed_experiment_plan_manual_or_no_op_rows: 0
- consumed_experiment_plan_executable_rows: 0
- consumed_experiment_plan_real_scored_or_reranked_rows: 0
- matrix_row_count: 38
- accepted_row_count: 9
- denied_row_count: 29
- skipped_row_count: 0
- m12_boundary_failure_matrix_ready: true

## Reason-code counts

Reason codes are structured metric keys, preferred over long negated prose. Codes with no rows report zero.

| reason_code | count |
|---|---|
| es_baseline_passthrough_placeholder_accepted | 1 |
| qdrant_baseline_passthrough_placeholder_accepted | 1 |
| combined_union_placeholder_policy_accepted | 2 |
| combined_intersection_placeholder_policy_accepted | 1 |
| tie_breaker_placeholder_policy_accepted | 1 |
| accepted_negative_control_exclusion_policy_accepted | 2 |
| manual_or_no_op_zero_count_handling_accepted | 1 |
| executable_policy_drift_denied | 1 |
| real_scored_or_reranked_row_drift_denied | 1 |
| fabricated_candidate_id_denied | 1 |
| fabricated_provider_id_denied | 1 |
| fabricated_score_denied | 1 |
| fabricated_rank_denied | 1 |
| fabricated_backend_response_denied | 1 |
| fabricated_fused_score_denied | 1 |
| fabricated_reranked_position_denied | 1 |
| fabricated_quality_label_denied | 1 |
| fabricated_fusion_output_denied | 1 |
| fabricated_reranking_output_denied | 1 |
| backend_result_leg_executed_denied | 1 |
| es_client_created_denied | 1 |
| qdrant_client_created_denied | 1 |
| es_executed_denied | 1 |
| qdrant_executed_denied | 1 |
| production_route_or_beauty_search_called_denied | 1 |
| route_plugin_di_http_involved_denied | 1 |
| production_route_switch_or_default_switch_denied | 1 |
| qdrant_production_activation_approved_denied | 1 |
| hybrid_serving_implemented_or_claimed_denied | 1 |
| fallback_implemented_or_claimed_denied | 1 |
| production_telemetry_implemented_or_claimed_denied | 1 |
| quality_green_claimed_denied | 1 |
| retrieval_quality_claimed_denied | 1 |
| production_readiness_claimed_denied | 1 |
| route_activation_claimed_denied | 1 |
| serving_approval_claimed_denied | 1 |

## Accepted baseline rows

Valid M12B policy-handling cases the catalog and experiment-plan schema permit: ES baseline
passthrough placeholder, Qdrant baseline passthrough placeholder, the three combined placeholder
policies (union, intersection, tie-breaker), the accepted negative-control exclusion policy, and
the current zero-count manual/no-op handling case. Every accepted policy is non-executable.

| case_id | decision | reason_code | detail |
|---|---|---|---|
| case_es_baseline_passthrough_placeholder_accepted | accepted | es_baseline_passthrough_placeholder_accepted | An ES baseline plan row carrying the es_baseline_passthrough placeholder policy is an accepted policy-handling case in the M12B catalog. |
| case_qdrant_baseline_passthrough_placeholder_accepted | accepted | qdrant_baseline_passthrough_placeholder_accepted | A Qdrant baseline plan row carrying the qdrant_baseline_passthrough placeholder policy is an accepted policy-handling case in the M12B catalog. |
| case_combined_union_placeholder_policy_accepted | accepted | combined_union_placeholder_policy_accepted | A combined experiment plan row carrying the combined_union_placeholder placeholder policy is an accepted policy-handling case in the M12B catalog. |
| case_combined_intersection_placeholder_policy_accepted | accepted | combined_intersection_placeholder_policy_accepted | A combined experiment plan row carrying the combined_intersection_placeholder placeholder policy is an accepted policy-handling case in the M12B catalog. |
| case_tie_breaker_placeholder_policy_accepted | accepted | tie_breaker_placeholder_policy_accepted | A combined experiment plan row carrying the tie_breaker_placeholder placeholder policy is an accepted policy-handling case in the M12B catalog. |
| case_accepted_negative_control_exclusion_policy_accepted | accepted | accepted_negative_control_exclusion_policy_accepted | An accepted negative-control exclusion plan row carrying the accepted_negative_control_exclusion_policy and no backend candidate policy is an accepted policy-handling case in the M12B catalog. |
| case_manual_or_no_op_zero_count_handling_accepted | accepted | manual_or_no_op_zero_count_handling_accepted | The current zero-count manual/no-op exclusion plan rows retain a policy-handling case even though their current count is zero; no backend candidate policy is assigned. |

## q_noise_004 and q_noise_005 handling rows

Both ids share the q_noise_* prefix yet land in different accepted handling rows: q_noise_004 is
a combined placeholder experiment-plan row with non-executable combined policy options,
q_noise_005 is the accepted_negative_control_exclusion_policy row with no backend candidate policy.

| case_id | decision | reason_code | detail |
|---|---|---|---|
| case_q_noise_004_combined_placeholder_plan | accepted | combined_union_placeholder_policy_accepted | q_noise_004 = gel removal maps to a combined placeholder experiment-plan row with non-executable combined policy options: combined_union_placeholder, combined_intersection_placeholder, tie_breaker_placeholder. |
| case_q_noise_005_negative_control_exclusion_plan | accepted | accepted_negative_control_exclusion_policy_accepted | q_noise_005 = lifting maps to the accepted_negative_control_exclusion_policy plan row with no backend candidate policy: accepted_negative_control_exclusion_policy. |

## Denied executable / scored / reranked drift rows

Any M12B policy that becomes executable, any plan row that records a real scored or reranked
result, and any code path that constructs a real policy algorithm are denied.

| case_id | decision | reason_code | detail |
|---|---|---|---|
| case_executable_policy_drift | denied | executable_policy_drift_denied | Any M12B policy option marked executable, any plan row that produces an executable policy, and any code path that constructs a real policy algorithm are denied. |
| case_real_scored_or_reranked_row_drift | denied | real_scored_or_reranked_row_drift_denied | Any plan row that records a real scored result or a real reranked result is denied; the M12B catalog reports 0 executable policy rows and 0 real scored/reranked rows. |

## Denied fabrication rows

Any fabricated candidate id, provider id, score, rank, backend response, fused score, reranked
position, quality label, fusion output, or reranking output is denied.

| case_id | decision | reason_code | detail |
|---|---|---|---|
| case_fabricated_candidate_id | denied | fabricated_candidate_id_denied | Any fabricated candidate id in a plan row, in a probe detail, or anywhere else in the artifact is denied. |
| case_fabricated_provider_id | denied | fabricated_provider_id_denied | Any fabricated provider id is denied. |
| case_fabricated_score | denied | fabricated_score_denied | Any fabricated score is denied. |
| case_fabricated_rank | denied | fabricated_rank_denied | Any fabricated rank is denied. |
| case_fabricated_backend_response | denied | fabricated_backend_response_denied | Any fabricated backend response payload is denied. |
| case_fabricated_fused_score | denied | fabricated_fused_score_denied | Any fabricated fused score is denied. |
| case_fabricated_reranked_position | denied | fabricated_reranked_position_denied | Any fabricated reranked position is denied. |
| case_fabricated_quality_label | denied | fabricated_quality_label_denied | Any fabricated quality label is denied. |
| case_fabricated_fusion_output | denied | fabricated_fusion_output_denied | Any fabricated fusion output is denied. |
| case_fabricated_reranking_output | denied | fabricated_reranking_output_denied | Any fabricated reranking output is denied. |

## Denied execution / client / route boundary rows

Any backend execution, ES/Qdrant client creation, production /beauty-search call, or
route/plugin/DI/HTTP involvement is denied.

| case_id | decision | reason_code | detail |
|---|---|---|---|
| case_backend_result_leg_executed | denied | backend_result_leg_executed_denied | Any plan row whose forwarded M12A result leg becomes executed, or any code path that flips a pending/not-executed placeholder into an executed leg, is denied. |
| case_es_client_created | denied | es_client_created_denied | Creating an Elasticsearch client in this matrix is denied. |
| case_qdrant_client_created | denied | qdrant_client_created_denied | Creating a Qdrant client in this matrix is denied. |
| case_es_executed | denied | es_executed_denied | Executing Elasticsearch in this matrix is denied. |
| case_qdrant_executed | denied | qdrant_executed_denied | Executing Qdrant in this matrix is denied. |
| case_production_route_or_beauty_search_called | denied | production_route_or_beauty_search_called_denied | Calling production /beauty-search or invoking any production route from this matrix is denied. |
| case_route_plugin_di_http_involved | denied | route_plugin_di_http_involved_denied | Any route, plugin, DI, or HTTP source involvement in this matrix is denied. |

## Denied production activation / serving claim rows

Any production route switch, Qdrant production activation, hybrid serving, fallback, production
telemetry, quality-green, retrieval-quality, production-readiness, route-activation, or
serving-approval claim is denied.

| case_id | decision | reason_code | detail |
|---|---|---|---|
| case_production_route_switch_or_default_switch | denied | production_route_switch_or_default_switch_denied | Any production route switch, default route switch, or hybrid-serving-driven route switch is denied. |
| case_qdrant_production_activation_approved | denied | qdrant_production_activation_approved_denied | Any approval, claim, or implication that Qdrant production activation is approved is denied. |
| case_hybrid_serving_implemented_or_claimed | denied | hybrid_serving_implemented_or_claimed_denied | Any hybrid serving implementation, hybrid serving implied claim, or combined experiment placeholder row treated as production hybrid serving is denied. |
| case_fallback_implemented_or_claimed | denied | fallback_implemented_or_claimed_denied | Any fallback implementation, fallback implied claim, or fallback drift is denied. |
| case_production_telemetry_implemented_or_claimed | denied | production_telemetry_implemented_or_claimed_denied | Any production telemetry implementation, metrics client creation, or production telemetry claim is denied. |
| case_quality_green_claimed | denied | quality_green_claimed_denied | Any quality-green claim about M12 fusion/reranking, backend execution, or candidate retrieval is denied. |
| case_retrieval_quality_claimed | denied | retrieval_quality_claimed_denied | Any retrieval-quality claim about M12 fusion/reranking, backend execution, or candidate retrieval is denied. |
| case_production_readiness_claimed | denied | production_readiness_claimed_denied | Any production-readiness claim about M12 fusion/reranking is denied. |
| case_route_activation_claimed | denied | route_activation_claimed_denied | Any route-activation claim about /beauty-search, the Qdrant opt-in route, or any production route is denied. |
| case_serving_approval_claimed | denied | serving_approval_claimed_denied | Any serving-approval claim about M12 fusion/reranking, hybrid serving, fallback, fusion, or reranking is denied. |

## Metrics

| metric | value |
|---|---|
| artifact_id | m12-beautyq-fusion-reranking-boundary-failure-matrix |
| artifact_version | v1 |
| consumed_m12b_policy_catalog_verdict | m12_fusion_reranking_policy_catalog_ready_schema_only |
| consumed_m12b_experiment_plan_verdict | m12_fusion_reranking_experiment_plan_ready_schema_only |
| consumed_m12a_input_scaffold_verdict | m12_fusion_reranking_input_scaffold_ready_schema_only |
| consumed_m11b_result_schema_verdict | m11_candidate_generation_result_schema_ready |
| consumed_m11c_boundary_failure_matrix_verdict | m11_candidate_generation_boundary_failure_matrix_ready |
| consumed_policy_catalog_name_count | 6 |
| consumed_policy_catalog_names_unique | true |
| consumed_experiment_plan_row_count | 89 |
| consumed_experiment_plan_es_baseline_rows | 15 |
| consumed_experiment_plan_qdrant_baseline_rows | 1 |
| consumed_experiment_plan_combined_rows | 72 |
| consumed_experiment_plan_accepted_negative_control_rows | 1 |
| consumed_experiment_plan_manual_or_no_op_rows | 0 |
| consumed_experiment_plan_executable_rows | 0 |
| consumed_experiment_plan_real_scored_or_reranked_rows | 0 |
| matrix_row_count | 38 |
| accepted_row_count | 9 |
| denied_row_count | 29 |
| skipped_row_count | 0 |
| reason_code_count_sum | 38 |
| accepted_baseline_row_count | 7 |
| noise_probe_row_count | 2 |
| denied_executable_scored_reranked_row_count | 2 |
| denied_fabrication_row_count | 10 |
| denied_execution_client_route_row_count | 7 |
| denied_production_activation_serving_row_count | 10 |
| m12_boundary_failure_matrix_ready | true |
| matrix_is_boundary_failure_matrix_only_not_scoring | true |
| matrix_is_boundary_failure_matrix_only_not_fusion | true |
| matrix_is_boundary_failure_matrix_only_not_reranking | true |
| matrix_is_boundary_failure_matrix_only_not_candidate_retrieval | true |
| matrix_is_boundary_failure_matrix_only_not_backend_execution | true |
| matrix_is_offline_report_not_production_routing | true |
| matrix_does_not_execute_es_or_qdrant | true |
| matrix_creates_no_es_or_qdrant_client | true |
| matrix_calls_no_production_beauty_search | true |
| matrix_involves_no_route_plugin_di_http | true |
| matrix_implements_no_fallback | true |
| matrix_implements_no_hybrid_serving | true |
| matrix_implements_no_production_telemetry | true |
| matrix_fabricates_no_candidate_ids_provider_ids_scores_ranks_backend_responses | true |
| matrix_fabricates_no_fused_scores_reranked_positions_quality_labels | true |
| matrix_fabricates_no_fusion_outputs_reranking_outputs | true |
| executable_scored_reranked_policy_drift_denied | true |
| fabricated_policy_handling_artifacts_denied | true |
| execution_client_route_boundary_drift_denied | true |
| production_activation_serving_claim_drift_denied | true |
| zero_count_manual_or_no_op_still_has_handling_case | true |
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
| verdict | m12_fusion_reranking_boundary_failure_matrix_ready_schema_only |

## Boundary summary

Boundary posture is reported as structured booleans in the Metrics table above, not as prose negations.
This is a boundary/failure matrix only, not scoring, not fusion execution, not reranking execution,
not candidate retrieval, not backend execution, and not production routing. No candidate ids,
provider ids, scores, ranks, backend responses, fused scores, reranked positions, quality labels,
fusion outputs, or reranking outputs are fabricated, and no production route, hybrid serving,
fallback, fusion, reranking, telemetry, or activation is implemented or claimed.
