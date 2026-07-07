# M11 BeautyQ Candidate-Generation Boundary/Failure Matrix

Offline candidate-generation boundary/failure matrix over the accepted M11B candidate-generation result
schema. This is an offline planning/eval artifact only. It is a boundary/failure matrix only and is NOT
backend execution and NOT production routing: no ES or Qdrant client is created and neither backend is run.
Each row is a data-only accepted/denied/skipped decision with an explicit reason code about how the M11B
result schema must handle one result-shape case. Backend legs remain pending/not-executed placeholders and
no candidate ids, scores, ranks, provider ids, or backend responses are fabricated. This artifact reports M11
offline boundary-matrix readiness only: it is not backend quality green, not retrieval quality, not production
readiness, not route activation, not serving approval, and not actual execution readiness.

## Summary

- dataset_id: wandsbek_hamburg_beauty_services_seed_ready
- verdict: m11_candidate_generation_boundary_failure_matrix_ready
- consumed_m11_result_schema_verdict: m11_candidate_generation_result_schema_ready
- consumed_result_row_count: 89
- matrix_row_count: 20
- accepted_row_count: 8
- denied_row_count: 12
- skipped_row_count: 0
- m11_boundary_failure_matrix_ready: true

## Reason-code counts

Reason codes are structured metric keys, preferred over long negated prose. Codes with no rows report zero.

| reason_code | count |
|---|---|
| es_only_pending_placeholder_accepted | 1 |
| qdrant_only_pending_placeholder_accepted | 1 |
| combined_pending_placeholders_accepted | 2 |
| accepted_negative_control_no_backend_leg_accepted | 2 |
| manual_review_exclusion_no_backend_leg_accepted | 1 |
| no_op_noise_exclusion_no_backend_leg_accepted | 1 |
| es_only_with_qdrant_leg_denied | 1 |
| qdrant_only_with_es_leg_denied | 1 |
| combined_missing_es_leg_denied | 1 |
| combined_missing_qdrant_leg_denied | 1 |
| negative_control_with_backend_leg_denied | 1 |
| manual_or_no_op_with_backend_leg_denied | 1 |
| fabricated_candidate_id_denied | 1 |
| fabricated_score_or_rank_denied | 1 |
| fabricated_provider_id_denied | 1 |
| fabricated_backend_response_denied | 1 |
| production_route_or_beauty_search_or_di_http_denied | 1 |
| production_activation_or_serving_claim_denied | 1 |

## Accepted baseline rows

Valid pending/not-executed placeholder shapes and exclusion shapes with no backend legs. The manual-review
and no-op/noise rows retain handling cases even though their current counts are zero.

| case_id | decision | reason_code | detail |
|---|---|---|---|
| case_es_only_pending_accepted | accepted | es_only_pending_placeholder_accepted | ES-only row with a single pending/not-executed ES result-leg placeholder is an accepted saved-report shape. |
| case_qdrant_only_pending_accepted | accepted | qdrant_only_pending_placeholder_accepted | Qdrant-only row with a single pending/not-executed Qdrant result-leg placeholder is an accepted saved-report shape. |
| case_combined_pending_accepted | accepted | combined_pending_placeholders_accepted | Combined row with separate pending/not-executed ES and Qdrant result-leg placeholders is an accepted offline comparison report shape. |
| case_accepted_negative_control_no_leg_accepted | accepted | accepted_negative_control_no_backend_leg_accepted | Accepted negative-control exclusion with no backend result legs is an accepted saved-report shape. |
| case_manual_review_no_leg_accepted | accepted | manual_review_exclusion_no_backend_leg_accepted | Manual-review exclusion with no backend result legs is an accepted handling case even though its current count is zero. |
| case_no_op_noise_no_leg_accepted | accepted | no_op_noise_exclusion_no_backend_leg_accepted | No-op/noise exclusion with no backend result legs is an accepted handling case even though its current count is zero. |

## q_noise_004 and q_noise_005 handling rows

Both ids share the q_noise_* prefix yet land in different accepted handling rows: q_noise_004 is a combined
pending ES + Qdrant placeholder shape, q_noise_005 is an accepted negative-control exclusion with no legs.

| case_id | decision | reason_code | detail |
|---|---|---|---|
| case_q_noise_004_combined_pending | accepted | combined_pending_placeholders_accepted | q_noise_004 = gel removal maps to combined pending ES + Qdrant placeholders: es:pending_not_executed, qdrant:pending_not_executed. |
| case_q_noise_005_negative_control | accepted | accepted_negative_control_no_backend_leg_accepted | q_noise_005 = lifting maps to an accepted negative-control exclusion with no backend result legs: (none). |

## Denied boundary rows

Invalid backend-leg combinations and any production route / serving involvement or claim.

| case_id | decision | reason_code | detail |
|---|---|---|---|
| case_es_only_with_qdrant_leg | denied | es_only_with_qdrant_leg_denied | ES-only row carrying a Qdrant result leg is a denied invalid backend-leg combination. |
| case_qdrant_only_with_es_leg | denied | qdrant_only_with_es_leg_denied | Qdrant-only row carrying an ES result leg is a denied invalid backend-leg combination. |
| case_combined_missing_es_leg | denied | combined_missing_es_leg_denied | Combined row missing its ES result leg is a denied invalid backend-leg combination. |
| case_combined_missing_qdrant_leg | denied | combined_missing_qdrant_leg_denied | Combined row missing its Qdrant result leg is a denied invalid backend-leg combination. |
| case_negative_control_with_backend_leg | denied | negative_control_with_backend_leg_denied | Accepted negative-control row carrying any backend result leg is a denied invalid backend-leg combination. |
| case_manual_or_no_op_with_backend_leg | denied | manual_or_no_op_with_backend_leg_denied | Manual-review or no-op/noise row carrying any backend result leg is a denied invalid backend-leg combination. |
| case_production_route_or_beauty_search | denied | production_route_or_beauty_search_or_di_http_denied | Any production route, /beauty-search call, or route/plugin/DI/HTTP involvement is denied. |
| case_production_activation_or_serving_claim | denied | production_activation_or_serving_claim_denied | Any production activation, route switch, Qdrant production activation, hybrid serving, fallback, fusion, reranking, production telemetry, quality-green, retrieval-quality, production-readiness, route-activation, or serving-approval claim is denied. |

## Denied fabrication rows

Any fabricated candidate id, score, rank, provider id, or backend response.

| case_id | decision | reason_code | detail |
|---|---|---|---|
| case_fabricated_candidate_id | denied | fabricated_candidate_id_denied | Any fabricated candidate id is denied. |
| case_fabricated_score_or_rank | denied | fabricated_score_or_rank_denied | Any fabricated score or rank is denied. |
| case_fabricated_provider_id | denied | fabricated_provider_id_denied | Any fabricated provider id is denied. |
| case_fabricated_backend_response | denied | fabricated_backend_response_denied | Any fabricated backend response payload is denied. |

## Metrics

| metric | value |
|---|---|
| consumed_m11_result_schema_verdict | m11_candidate_generation_result_schema_ready |
| consumed_result_row_count | 89 |
| matrix_row_count | 20 |
| accepted_row_count | 8 |
| denied_row_count | 12 |
| skipped_row_count | 0 |
| reason_code_count_sum | 20 |
| accepted_baseline_row_count | 6 |
| noise_probe_row_count | 2 |
| denied_boundary_row_count | 8 |
| denied_fabrication_row_count | 4 |
| m11_boundary_failure_matrix_ready | true |
| matrix_is_boundary_failure_matrix_only_not_backend_execution | true |
| matrix_is_offline_report_not_production_routing | true |
| matrix_does_not_execute_es_or_qdrant | true |
| matrix_creates_no_es_or_qdrant_client | true |
| matrix_calls_no_production_beauty_search | true |
| matrix_involves_no_route_plugin_di_http | true |
| matrix_fabricates_no_candidate_ids_scores_ranks_provider_ids_backend_responses | true |
| invalid_backend_leg_combinations_denied | true |
| fabricated_candidate_artifacts_denied | true |
| production_route_and_serving_claims_denied | true |
| zero_count_manual_and_no_op_still_have_handling_cases | true |
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
| verdict | m11_candidate_generation_boundary_failure_matrix_ready |

## Boundary summary

Boundary posture is reported as structured booleans in the Metrics table above, not as prose negations.
This is a boundary/failure matrix only, not backend execution and not production routing; backend legs are
pending/not-executed placeholders with no fabricated candidate ids, scores, ranks, provider ids, or backend
responses, and no production route, hybrid serving, fallback, fusion, reranking, telemetry, or activation is
implemented or claimed.
