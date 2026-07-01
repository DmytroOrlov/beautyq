# M12 BeautyQ Fusion/Reranking Input Scaffold

Offline fusion/reranking experiment input scaffold over the accepted M11B candidate-generation result
schema and M11C boundary/failure matrix. This is an offline planning/eval artifact only. The input
envelopes below are schema-only experiment inputs and are NOT scoring, NOT fusion, NOT reranking, NOT backend execution,
and NOT production routing: no ES or Qdrant client is created and neither backend is run. Backend candidate legs
are pending/not-executed placeholders forwarded verbatim from M11B and carry
no candidate ids, provider ids, scores, ranks, backend responses, fused scores, reranked positions, or
quality labels. This artifact reports M12 offline input-scaffold readiness only (schema-only): it is not
backend quality green, not retrieval quality, not production readiness, not route activation, not serving
approval, and not actual execution readiness.

## Summary

- dataset_id: wandsbek_hamburg_beauty_services_seed_ready
- verdict: m12_fusion_reranking_input_scaffold_ready_schema_only
- consumed_m11b_result_schema_verdict: m11_candidate_generation_result_schema_ready
- consumed_m11c_boundary_failure_matrix_verdict: m11_candidate_generation_boundary_failure_matrix_ready
- consumed_result_row_count: 75
- consumed_matrix_row_count: 20
- consumed_matrix_accepted_row_count: 8
- consumed_matrix_denied_row_count: 12
- consumed_matrix_skipped_row_count: 0
- fusion_reranking_input_rows: 75
- fusion_reranking_backend_candidate_rows: 74
- fusion_reranking_executable_rows: 0
- real_candidate_result_rows: 0
- pending_not_executed_result_leg_rows: 132
- combined_comparison_pair_placeholders: 58
- accepted_negative_control_exclusions: 1
- m12_fusion_reranking_input_scaffold_ready: true

## M12 input group counts

These are offline experiment input groups only, not production routes. Backend placeholder groups forward
pending/not-executed candidate legs; the exclusion groups carry no fusion/reranking input legs.

| m12_input_group | count |
|---|---|
| es_only_placeholder_input | 15 |
| qdrant_only_placeholder_input | 1 |
| combined_comparison_placeholder_input | 58 |
| accepted_negative_control_exclusion_input | 1 |
| manual_or_no_op_exclusion_input | 0 |

## Pending/not-executed candidate-leg counts

Candidate legs are pending/not-executed placeholders forwarded from M11B, not backend execution. ES legs =
ES-only inputs + combined-comparison ES legs; Qdrant legs = Qdrant-only inputs + combined-comparison Qdrant
legs; the sum is the total pending/not-executed result-leg rows.

| metric | value |
|---|---|
| es_pending_result_leg_placeholder_rows | 73 |
| qdrant_pending_result_leg_placeholder_rows | 59 |
| pending_not_executed_result_leg_rows | 132 |
| combined_comparison_pair_placeholders | 58 |

## Representative anchors

| query_id | category | offline_strategy_intent | m12_input_group | candidate_legs |
|---|---|---|---|---|
| q_nails_001 | mixed_intent | combined_es_qdrant_comparison | combined_comparison_placeholder_input | es:pending_not_executed, qdrant:pending_not_executed |
| q_nails_003 | attribute_filter_intent | es_only_candidate_retrieval | es_only_placeholder_input | es:pending_not_executed |
| q_noise_005 | noisy_ambiguous_non_beauty_intent | accepted_negative_control_excluded | accepted_negative_control_exclusion_input | (none) |

## Noise-probe input envelopes

Both ids share the q_noise_* prefix yet land in different M12 input envelopes: q_noise_004 is a combined
ES/Qdrant schema-only input with pending ES and Qdrant placeholders, q_noise_005 is an accepted
negative-control exclusion with no fusion/reranking input legs.

| query_id | category | offline_strategy_intent | m12_input_group | candidate_legs |
|---|---|---|---|---|
| q_noise_004 | mixed_intent | combined_es_qdrant_comparison | combined_comparison_placeholder_input | es:pending_not_executed, qdrant:pending_not_executed |
| q_noise_005 | noisy_ambiguous_non_beauty_intent | accepted_negative_control_excluded | accepted_negative_control_exclusion_input | (none) |

## Metrics

| metric | value |
|---|---|
| consumed_m11b_result_schema_verdict | m11_candidate_generation_result_schema_ready |
| consumed_m11c_boundary_failure_matrix_verdict | m11_candidate_generation_boundary_failure_matrix_ready |
| consumed_result_row_count | 75 |
| consumed_matrix_row_count | 20 |
| consumed_matrix_accepted_row_count | 8 |
| consumed_matrix_denied_row_count | 12 |
| consumed_matrix_skipped_row_count | 0 |
| input_group_count_sum | 75 |
| es_only_placeholder_input_rows | 15 |
| qdrant_only_placeholder_input_rows | 1 |
| combined_comparison_placeholder_input_rows | 58 |
| accepted_negative_control_exclusion_input_rows | 1 |
| manual_or_no_op_exclusion_input_rows | 0 |
| fusion_reranking_input_rows | 75 |
| fusion_reranking_backend_candidate_rows | 74 |
| fusion_reranking_executable_rows | 0 |
| real_candidate_result_rows | 0 |
| pending_not_executed_result_leg_rows | 132 |
| es_pending_result_leg_placeholder_rows | 73 |
| qdrant_pending_result_leg_placeholder_rows | 59 |
| combined_comparison_pair_placeholders | 58 |
| accepted_negative_control_exclusions | 1 |
| m12_fusion_reranking_input_scaffold_ready | true |
| m12_inputs_are_schema_only_not_scoring | true |
| m12_inputs_are_schema_only_not_fusion | true |
| m12_inputs_are_schema_only_not_reranking | true |
| m12_inputs_are_schema_only_not_candidate_retrieval | true |
| m12_inputs_are_schema_only_not_backend_execution | true |
| m12_inputs_are_offline_inputs_not_production_routing | true |
| m12_backend_candidate_legs_are_pending_not_executed_placeholders | true |
| combined_comparison_input_is_offline_not_hybrid_serving | true |
| accepted_negative_control_has_no_fusion_reranking_input_leg | true |
| manual_and_no_op_have_no_fusion_reranking_input_leg | true |
| no_real_candidate_ids_provider_ids_scores_ranks_fabricated | true |
| no_real_backend_responses_fabricated | true |
| no_fused_scores_reranked_positions_quality_labels_fabricated | true |
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
| verdict | m12_fusion_reranking_input_scaffold_ready_schema_only |

## Boundary summary

Boundary posture is reported as structured booleans in the Metrics table above, not as prose negations.
M12 input envelopes are schema-only fusion/reranking experiment inputs, not scoring, not reranking, not
backend execution, and not production routing; candidate legs are pending/not-executed placeholders with no
fabricated candidate ids, provider ids, scores, ranks, backend responses, fused scores, reranked positions,
or quality labels, and a combined comparison input does not imply production hybrid serving.
