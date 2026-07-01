# M11 BeautyQ Candidate-Generation Result Schema

Offline candidate-generation saved result/report-shape schema over the accepted M11A candidate-generation
request skeleton. This is an offline planning/eval artifact only. The result rows below are saved report
shapes only and are NOT production routes and NOT backend execution: no ES or Qdrant client is created and
neither backend is run. Backend legs render as pending/not-executed placeholders and carry no candidate
ids, scores, ranks, provider ids, or backend responses. This artifact reports M11 offline result-schema
readiness only: it is not backend quality green, not retrieval quality, not production readiness, not route
activation, not serving approval, and not actual execution readiness.

## Summary

- dataset_id: wandsbek_hamburg_beauty_services_seed_ready
- verdict: m11_candidate_generation_result_schema_ready
- consumed_m11_request_skeleton_verdict: m11_candidate_generation_input_skeleton_ready
- consumed_m10_readiness_verdict: m11_candidate_generation_inputs_ready_with_negative_control_exclusion
- total_row_count: 75
- es_result_leg_placeholder_count: 73
- qdrant_result_leg_placeholder_count: 59
- combined_comparison_pair_placeholder_count: 58
- accepted_negative_control_exclusion_row_count: 1
- m11_candidate_generation_result_schema_ready: true

## M11 result disposition counts

These are offline saved-report dispositions only, not production routes. Backend dispositions reserve
pending/not-executed result legs; the excluded/skipped dispositions carry no backend result legs.

| result_disposition | count |
|---|---|
| es_only_pending_not_executed | 15 |
| qdrant_only_pending_not_executed | 1 |
| combined_comparison_pending_not_executed | 58 |
| accepted_negative_control_excluded | 1 |
| manual_review_excluded | 0 |
| no_op_noise_skipped | 0 |

## Pending/not-executed result-leg counts

Result legs are pending/not-executed saved placeholders only, not backend execution. ES legs = ES-only
rows + combined-comparison ES legs; Qdrant legs = Qdrant-only rows + combined-comparison Qdrant legs.

| metric | value |
|---|---|
| es_result_leg_placeholder_count | 73 |
| qdrant_result_leg_placeholder_count | 59 |
| combined_comparison_pair_placeholder_count | 58 |

## Representative anchors

| query_id | category | offline_strategy_intent | result_disposition | result_legs |
|---|---|---|---|---|
| q_nails_001 | mixed_intent | combined_es_qdrant_comparison | combined_comparison_pending_not_executed | es:pending_not_executed, qdrant:pending_not_executed |
| q_nails_003 | attribute_filter_intent | es_only_candidate_retrieval | es_only_pending_not_executed | es:pending_not_executed |
| q_noise_005 | noisy_ambiguous_non_beauty_intent | accepted_negative_control_excluded | accepted_negative_control_excluded | (none) |

## Noise-probe result rows

Both ids share the q_noise_* prefix yet land in different M11 result rows: q_noise_004 is a combined
ES/Qdrant pending/not-executed comparison shape, q_noise_005 is an accepted negative-control exclusion
with no backend result legs.

| query_id | category | offline_strategy_intent | result_disposition | result_legs |
|---|---|---|---|---|
| q_noise_004 | mixed_intent | combined_es_qdrant_comparison | combined_comparison_pending_not_executed | es:pending_not_executed, qdrant:pending_not_executed |
| q_noise_005 | noisy_ambiguous_non_beauty_intent | accepted_negative_control_excluded | accepted_negative_control_excluded | (none) |

## Metrics

| metric | value |
|---|---|
| total_row_count | 75 |
| consumed_m11_request_skeleton_verdict | m11_candidate_generation_input_skeleton_ready |
| consumed_m10_readiness_verdict | m11_candidate_generation_inputs_ready_with_negative_control_exclusion |
| disposition_count_sum | 75 |
| row_group_count_sum | 75 |
| es_only_row_count | 15 |
| qdrant_only_row_count | 1 |
| combined_comparison_row_count | 58 |
| accepted_negative_control_exclusion_row_count | 1 |
| manual_review_blocked_row_count | 0 |
| no_op_noise_row_count | 0 |
| es_result_leg_placeholder_count | 73 |
| qdrant_result_leg_placeholder_count | 59 |
| combined_comparison_pair_placeholder_count | 58 |
| m11_candidate_generation_result_schema_ready | true |
| m11_result_rows_are_saved_report_shapes_not_backend_execution | true |
| m11_result_rows_are_offline_report_shapes_not_production_routes | true |
| backend_result_legs_are_pending_not_executed_placeholders | true |
| no_real_candidate_ids_scores_ranks_provider_ids_fabricated | true |
| no_real_backend_responses_fabricated | true |
| combined_comparison_is_offline_report_shape_not_hybrid_serving | true |
| accepted_negative_control_has_no_backend_result_leg | true |
| manual_and_no_op_have_no_backend_result_leg | true |
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
| verdict | m11_candidate_generation_result_schema_ready |

## Boundary

Boundary posture is reported as structured booleans in the Metrics table above, not as prose negations.
M11 result rows are saved report shapes, not production routes and not backend execution; backend legs are
pending/not-executed placeholders with no fabricated candidate ids, scores, ranks, provider ids, or backend
responses, and a combined comparison row does not imply production hybrid serving.
