# M10 BeautyQ Full Query Classification Coverage Scorecard

Offline classification coverage report over the full accepted 64-query BeautyQ eval dataset. This is an
offline planning/eval artifact only: it is not backend quality evidence, not production readiness, not route
activation, and not serving approval. `strategy_intent` values are offline study intents only and are NOT
production routes; offline strategy intent is distinct from production routing.

## Summary

- dataset_id: wandsbek_hamburg_beauty_services_seed_ready
- verdict: full_query_classification_coverage_ready_with_negative_control_exclusion
- total_query_count: 64
- mapped_row_count: 64
- mixed_intent_count: 48
- noisy_row_count: 1
- unresolved_manual_review_row_count: 0
- accepted_negative_control_exclusion_count: 1
- no_op_row_count: 0
- backend_candidate_study_intent_count: 63
- m11_backend_candidate_inputs_ready: true

## Category counts

| category | count |
|---|---|
| provider_lookup | 0 |
| service_intent | 7 |
| attribute_filter_intent | 5 |
| location_intent | 1 |
| price_budget_intent | 1 |
| availability_time_intent | 0 |
| comparison_exploration_intent | 1 |
| noisy_ambiguous_non_beauty_intent | 1 |
| mixed_intent | 48 |

## Offline strategy intent counts

These are offline study intents only, not production routes.

| offline_strategy_intent | count |
|---|---|
| es_only_candidate_retrieval | 14 |
| qdrant_only_candidate_retrieval | 1 |
| combined_es_qdrant_comparison | 48 |
| manual_review_blocked | 0 |
| no_op_noise | 0 |
| accepted_negative_control_excluded | 1 |

## Representative anchors

| query_id | category | offline_strategy_intent |
|---|---|---|
| q_nails_001 | mixed_intent | combined_es_qdrant_comparison |
| q_nails_003 | attribute_filter_intent | es_only_candidate_retrieval |
| q_noise_005 | noisy_ambiguous_non_beauty_intent | accepted_negative_control_excluded |

## Accepted negative-control rows

Noisy/ambiguous anchors deliberately excluded from backend-candidate study; resolved, not pending.

- q_noise_005

## Unresolved manual-review rows

Rows still requiring future manual resolution; empty for the accepted dataset.

(none)

## Metrics

| metric | value |
|---|---|
| total_query_count | 64 |
| mapped_row_count | 64 |
| category_count_sum | 64 |
| strategy_intent_count_sum | 64 |
| mixed_intent_count | 48 |
| noisy_row_count | 1 |
| unresolved_manual_review_row_count | 0 |
| accepted_negative_control_exclusion_count | 1 |
| no_op_row_count | 0 |
| backend_candidate_study_intent_count | 63 |
| m11_backend_candidate_inputs_ready | true |
| offline_strategy_intent_is_not_production_routing | true |
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
| verdict | full_query_classification_coverage_ready_with_negative_control_exclusion |

## Boundary

Boundary posture is reported as structured booleans in the Metrics table above, not as prose negations.
Offline strategy intent is distinct from production routing.
