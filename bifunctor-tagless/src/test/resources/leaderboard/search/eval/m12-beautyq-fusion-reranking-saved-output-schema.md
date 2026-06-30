# M12 BeautyQ Fusion/Reranking Saved Output Schema

Offline saved-output/report schema over the accepted M12B fusion/reranking experiment-plan rows
and the accepted M12C fusion/reranking boundary/failure matrix. This is an offline
planning/eval/reporting artifact only. It defines one deterministic placeholder saved-output row
per consumed M12B experiment-plan row and is NOT scoring, NOT fusion execution, NOT reranking execution,
NOT candidate retrieval, NOT backend execution, and NOT production routing: no ES or Qdrant
client is created and neither backend is run. Saved-output rows are placeholder-only and
contain no candidate ids, provider ids, scores, ranks, backend responses, fused scores, reranked
positions, quality labels, fusion outputs, reranking outputs, or executable policy output.
Combined rows preserve their three assigned placeholder policy names but compute no fused or
reranked order; accepted negative-control rows render excluded output with no backend candidate
policy output; manual/no-op rows stay explicitly supported even at zero current count. This
artifact reports M12 saved-output schema readiness only (placeholder-only): it is not scoring
readiness, not fusion/reranking execution readiness, not retrieval quality, not production
readiness, not route activation, and not serving approval.

## Artifact identity

- artifact_id: m12-beautyq-fusion-reranking-saved-output-schema
- artifact_version: v1
- dataset_id: wandsbek_hamburg_beauty_services_seed_ready

## Summary

- verdict: m12_fusion_reranking_saved_output_schema_ready_placeholder_only
- consumed_m12b_policy_catalog_verdict: m12_fusion_reranking_policy_catalog_ready_schema_only
- consumed_m12b_experiment_plan_verdict: m12_fusion_reranking_experiment_plan_ready_schema_only
- consumed_m12c_boundary_failure_matrix_verdict: m12_fusion_reranking_boundary_failure_matrix_ready_schema_only
- consumed_experiment_plan_row_count: 74
- consumed_matrix_row_count: 38
- consumed_matrix_accepted_row_count: 9
- consumed_matrix_denied_row_count: 29
- consumed_matrix_skipped_row_count: 0
- saved_output_rows: 74
- es_baseline_placeholder_output_rows: 15
- qdrant_baseline_placeholder_output_rows: 1
- combined_placeholder_output_rows: 57
- accepted_negative_control_output_rows: 1
- manual_or_no_op_output_rows: 0
- placeholder_output_rows: 73
- excluded_output_rows: 1
- executable_output_rows: 0
- real_scored_or_reranked_output_rows: 0
- fabricated_candidate_payload_rows: 0
- m12_saved_output_schema_ready: true

## Saved output kind counts

Each consumed M12B experiment-plan row becomes one placeholder saved-output row. Backend baseline
rows save an ES or Qdrant baseline placeholder output; combined rows save a combined placeholder
output preserving the three combined policy names (union, intersection, tie-breaker) without
computing a fused or reranked order; the accepted negative-control row saves excluded output with
no backend candidate policy output; manual/no-op rows save no policy output. No row is executable
and no real scored/reranked output is produced.

| saved_output_kind | count |
|---|---|
| es_baseline_placeholder_output | 15 |
| qdrant_baseline_placeholder_output | 1 |
| combined_placeholder_output | 57 |
| accepted_negative_control_excluded_output | 1 |
| manual_or_no_op_output | 0 |

## q_noise_004 and q_noise_005 saved output rows

Both ids share the q_noise_* prefix yet land in different saved-output rows: q_noise_004 = gel
removal saves a combined placeholder output preserving the three non-executable combined policy
names; q_noise_005 = lifting saves accepted negative-control excluded output with no backend
candidate policy output.

| query_id | saved_output_kind | placeholder_policy_names | rendered_output |
|---|---|---|---|
| q_noise_004 | combined_placeholder_output | combined_union_placeholder, combined_intersection_placeholder, tie_breaker_placeholder | combined_placeholder_output(combined_union_placeholder, combined_intersection_placeholder, tie_breaker_placeholder) |
| q_noise_005 | accepted_negative_control_excluded_output | (none) | excluded_output(no_backend_candidate_policy) |

## Metrics

| metric | value |
|---|---|
| artifact_id | m12-beautyq-fusion-reranking-saved-output-schema |
| artifact_version | v1 |
| consumed_m12b_policy_catalog_verdict | m12_fusion_reranking_policy_catalog_ready_schema_only |
| consumed_m12b_experiment_plan_verdict | m12_fusion_reranking_experiment_plan_ready_schema_only |
| consumed_m12c_boundary_failure_matrix_verdict | m12_fusion_reranking_boundary_failure_matrix_ready_schema_only |
| consumed_experiment_plan_row_count | 74 |
| consumed_matrix_row_count | 38 |
| consumed_matrix_accepted_row_count | 9 |
| consumed_matrix_denied_row_count | 29 |
| consumed_matrix_skipped_row_count | 0 |
| saved_output_rows | 74 |
| es_baseline_placeholder_output_rows | 15 |
| qdrant_baseline_placeholder_output_rows | 1 |
| combined_placeholder_output_rows | 57 |
| accepted_negative_control_output_rows | 1 |
| manual_or_no_op_output_rows | 0 |
| placeholder_output_rows | 73 |
| excluded_output_rows | 1 |
| executable_output_rows | 0 |
| real_scored_or_reranked_output_rows | 0 |
| fabricated_candidate_payload_rows | 0 |
| saved_output_row_count_sum | 74 |
| placeholder_plus_excluded_output_rows | 74 |
| m12_saved_output_schema_ready | true |
| saved_output_is_placeholder_only_not_scoring | true |
| saved_output_is_placeholder_only_not_fusion_execution | true |
| saved_output_is_placeholder_only_not_reranking_execution | true |
| saved_output_is_placeholder_only_not_candidate_retrieval | true |
| saved_output_is_placeholder_only_not_backend_execution | true |
| saved_output_is_offline_report_not_production_routing | true |
| combined_rows_preserve_policy_names_without_fused_or_reranked_order | true |
| accepted_negative_control_rows_render_excluded_without_backend_candidate_policy | true |
| manual_or_no_op_rows_remain_explicitly_supported_at_zero_count | true |
| saved_output_does_not_execute_es_or_qdrant | true |
| saved_output_creates_no_es_or_qdrant_client | true |
| saved_output_calls_no_production_beauty_search | true |
| saved_output_involves_no_route_plugin_di_http | true |
| saved_output_implements_no_fallback | true |
| saved_output_implements_no_hybrid_serving | true |
| saved_output_implements_no_production_telemetry | true |
| saved_output_fabricates_no_candidate_ids_provider_ids_scores_ranks_backend_responses | true |
| saved_output_fabricates_no_fused_scores_reranked_positions_quality_labels | true |
| saved_output_fabricates_no_fusion_outputs_reranking_outputs | true |
| saved_output_contains_no_executable_policy_output | true |
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
| verdict | m12_fusion_reranking_saved_output_schema_ready_placeholder_only |

## Boundary summary

Boundary posture is reported as structured booleans in the Metrics table above, not as prose negations.
This is a placeholder-only saved-output schema, not scoring, not fusion execution, not reranking
execution, not backend execution, and not production routing: no candidate ids, provider ids,
scores, ranks, backend responses, fused scores, reranked positions, quality labels, fusion
outputs, reranking outputs, or executable policy output are present, and no production route,
hybrid serving, fallback, fusion, reranking, telemetry, or activation is implemented or claimed.
