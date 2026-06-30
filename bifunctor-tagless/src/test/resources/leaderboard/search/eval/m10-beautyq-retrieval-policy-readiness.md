# M10 BeautyQ Retrieval-Policy Readiness

Offline M11 candidate-generation input-preparation contract over the accepted hardened M10B full
74-query classification coverage. This is an offline planning/eval artifact only. The M11 input groups
below are offline study inputs only and are NOT production routes; offline strategy intent and offline
input groups are distinct from production routing. This artifact reports M11 offline input-preparation
readiness only: it is not backend quality green, not retrieval quality, not production readiness, not
route activation, not serving approval, and not actual execution readiness.

## Summary

- dataset_id: wandsbek_hamburg_beauty_services_seed_ready
- verdict: m11_candidate_generation_inputs_ready_with_negative_control_exclusion
- total_query_count: 74
- backend_candidate_generation_input_count: 73
- accepted_negative_control_exclusion_input_count: 1
- unresolved_manual_review_input_count: 0
- no_op_noise_input_count: 0
- m11_candidate_generation_inputs_ready: true

## M10 category counts

| category | count |
|---|---|
| provider_lookup | 0 |
| service_intent | 7 |
| attribute_filter_intent | 6 |
| location_intent | 1 |
| price_budget_intent | 1 |
| availability_time_intent | 0 |
| comparison_exploration_intent | 1 |
| noisy_ambiguous_non_beauty_intent | 1 |
| mixed_intent | 57 |

## M10 offline strategy intent counts

These are offline study intents only, not production routes.

| offline_strategy_intent | count |
|---|---|
| es_only_candidate_retrieval | 15 |
| qdrant_only_candidate_retrieval | 1 |
| combined_es_qdrant_comparison | 57 |
| manual_review_blocked | 0 |
| no_op_noise | 0 |
| accepted_negative_control_excluded | 1 |

## M11 candidate-generation input group counts

These are offline study input groups only, not production routes.

| m11_input_group | count |
|---|---|
| es_candidate_generation_study_input | 15 |
| qdrant_candidate_generation_study_input | 1 |
| combined_es_qdrant_comparison_study_input | 57 |
| accepted_negative_control_exclusion_input | 1 |
| manual_review_blocked_input | 0 |
| no_op_noise_input | 0 |

## Representative anchors

| query_id | category | offline_strategy_intent | m11_input_group |
|---|---|---|---|
| q_nails_001 | mixed_intent | combined_es_qdrant_comparison | combined_es_qdrant_comparison_study_input |
| q_nails_003 | attribute_filter_intent | es_only_candidate_retrieval | es_candidate_generation_study_input |
| q_noise_005 | noisy_ambiguous_non_beauty_intent | accepted_negative_control_excluded | accepted_negative_control_exclusion_input |

## Noise-probe row mappings

Both ids share the q_noise_* prefix yet land in different M11 input groups: the prefix carries no
classification meaning; the deterministic offline signals do.

| query_id | category | offline_strategy_intent | m11_input_group |
|---|---|---|---|
| q_noise_004 | mixed_intent | combined_es_qdrant_comparison | combined_es_qdrant_comparison_study_input |
| q_noise_005 | noisy_ambiguous_non_beauty_intent | accepted_negative_control_excluded | accepted_negative_control_exclusion_input |

## Metrics

| metric | value |
|---|---|
| total_query_count | 74 |
| input_group_count_sum | 74 |
| es_candidate_generation_study_input_count | 15 |
| qdrant_candidate_generation_study_input_count | 1 |
| combined_es_qdrant_comparison_study_input_count | 57 |
| accepted_negative_control_exclusion_input_count | 1 |
| manual_review_blocked_input_count | 0 |
| no_op_noise_input_count | 0 |
| backend_candidate_generation_input_count | 73 |
| unresolved_manual_review_input_count | 0 |
| m11_candidate_generation_inputs_ready | true |
| m11_inputs_are_offline_study_inputs_not_production_routes | true |
| offline_strategy_intent_is_not_production_routing | true |
| mixed_intent_is_offline_combined_comparison_only | true |
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
| verdict | m11_candidate_generation_inputs_ready_with_negative_control_exclusion |

## Boundary

Boundary posture is reported as structured booleans in the Metrics table above, not as prose negations.
M11 input groups are offline study inputs, not production routes; offline strategy intent and offline
input groups are distinct from production routing.
