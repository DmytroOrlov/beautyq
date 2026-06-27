# M11 BeautyQ Candidate-Generation Input Skeleton

Offline candidate-generation request-shape skeleton over the accepted M10C retrieval-policy readiness
rows. This is an offline planning/eval artifact only. The request shapes below are offline study inputs
only and are NOT production routes and NOT backend execution: no ES or Qdrant client is created and
neither backend is run. This artifact reports M11 offline input-skeleton readiness only: it is not backend
quality green, not retrieval quality, not production readiness, not route activation, not serving approval,
and not actual execution readiness.

## Summary

- dataset_id: wandsbek_hamburg_beauty_services_seed_ready
- verdict: m11_candidate_generation_input_skeleton_ready
- consumed_m10_readiness_verdict: m11_candidate_generation_inputs_ready_with_negative_control_exclusion
- total_query_count: 64
- es_request_leg_row_count: 62
- qdrant_request_leg_row_count: 49
- combined_comparison_pair_row_count: 48
- accepted_negative_control_exclusion_row_count: 1
- m11_candidate_generation_input_skeleton_ready: true

## M11 row-group counts

These are offline study input groups only, not production routes.

| m11_input_group | count |
|---|---|
| es_candidate_generation_study_input | 14 |
| qdrant_candidate_generation_study_input | 1 |
| combined_es_qdrant_comparison_study_input | 48 |
| accepted_negative_control_exclusion_input | 1 |
| manual_review_blocked_input | 0 |
| no_op_noise_input | 0 |

## Planned offline request-leg counts

Request legs are offline study request shapes only, not backend execution. ES legs = ES-only rows +
combined-comparison ES legs; Qdrant legs = Qdrant-only rows + combined-comparison Qdrant legs.

| metric | value |
|---|---|
| es_request_leg_row_count | 62 |
| qdrant_request_leg_row_count | 49 |
| combined_comparison_pair_row_count | 48 |

## Representative anchors

| query_id | category | offline_strategy_intent | m11_input_group | request_legs |
|---|---|---|---|---|
| q_nails_001 | mixed_intent | combined_es_qdrant_comparison | combined_es_qdrant_comparison_study_input | es, qdrant |
| q_nails_003 | attribute_filter_intent | es_only_candidate_retrieval | es_candidate_generation_study_input | es |
| q_noise_005 | noisy_ambiguous_non_beauty_intent | accepted_negative_control_excluded | accepted_negative_control_exclusion_input | (none) |

## Noise-probe request shapes

Both ids share the q_noise_* prefix yet land in different M11 request shapes: q_noise_004 is a combined
ES/Qdrant offline comparison shape, q_noise_005 is an accepted negative-control exclusion with no legs.

| query_id | category | offline_strategy_intent | m11_input_group | request_legs |
|---|---|---|---|---|
| q_noise_004 | mixed_intent | combined_es_qdrant_comparison | combined_es_qdrant_comparison_study_input | es, qdrant |
| q_noise_005 | noisy_ambiguous_non_beauty_intent | accepted_negative_control_excluded | accepted_negative_control_exclusion_input | (none) |

## Metrics

| metric | value |
|---|---|
| total_query_count | 64 |
| consumed_m10_readiness_verdict | m11_candidate_generation_inputs_ready_with_negative_control_exclusion |
| row_group_count_sum | 64 |
| es_only_row_count | 14 |
| qdrant_only_row_count | 1 |
| combined_comparison_row_count | 48 |
| accepted_negative_control_exclusion_row_count | 1 |
| manual_review_blocked_row_count | 0 |
| no_op_noise_row_count | 0 |
| es_request_leg_row_count | 62 |
| qdrant_request_leg_row_count | 49 |
| combined_comparison_pair_row_count | 48 |
| m11_candidate_generation_input_skeleton_ready | true |
| m11_request_shapes_are_offline_study_inputs_not_production_routes | true |
| m11_request_shapes_are_request_shapes_not_backend_execution | true |
| combined_comparison_is_offline_study_input_not_hybrid_serving | true |
| accepted_negative_control_has_no_backend_request_leg | true |
| manual_and_no_op_have_no_backend_request_leg | true |
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
| verdict | m11_candidate_generation_input_skeleton_ready |

## Boundary

Boundary posture is reported as structured booleans in the Metrics table above, not as prose negations.
M11 request shapes are offline study inputs, not production routes and not backend execution; a combined
comparison shape is an offline study input and does not imply production hybrid serving.
