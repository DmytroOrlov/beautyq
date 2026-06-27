# M12 BeautyQ Fusion/Reranking Policy Catalog

Offline fusion/reranking policy catalog and experiment-plan schema over the accepted M12A
fusion/reranking input scaffold. This is an offline planning/eval artifact only. The policy catalog
below defines named future policy options and the experiment plan assigns one schema-only
experiment-plan row per consumed M12A input envelope. It is a schema-only policy catalog and
experiment-plan artifact and is NOT scoring, NOT fusion, NOT reranking, NOT backend execution,
and NOT production routing: no ES or Qdrant client is created and neither backend is run, and
no candidate ids, provider ids, scores, ranks, backend responses, fused scores, reranked
positions, quality labels, fusion outputs, or reranking outputs are fabricated. This artifact
reports M12 offline policy-catalog and experiment-plan readiness only (schema-only): it is not
scoring readiness, not fusion/reranking execution readiness, not retrieval quality, not production
readiness, not route activation, and not serving approval.

## Summary

- dataset_id: wandsbek_hamburg_beauty_services_seed_ready
- verdict: m12_fusion_reranking_policy_catalog_ready_schema_only
- consumed_m12a_input_scaffold_verdict: m12_fusion_reranking_input_scaffold_ready_schema_only
- consumed_m11b_result_schema_verdict: m11_candidate_generation_result_schema_ready
- consumed_m11c_boundary_failure_matrix_verdict: m11_candidate_generation_boundary_failure_matrix_ready
- consumed_input_row_count: 64
- policy_count: 6
- es_baseline_rows: 14
- qdrant_baseline_rows: 1
- combined_experiment_rows: 48
- accepted_negative_control_exclusion_rows: 1
- manual_or_no_op_rows: 0
- executable_policy_rows: 0
- real_scored_or_reranked_rows: 0
- m12_fusion_reranking_policy_catalog_ready: true

## Policy catalog names

The catalog is names and constraints only, not implemented scoring, fusion, or reranking
algorithms. Every policy option is non-executable and produces no candidate ids, provider ids,
scores, ranks, backend responses, fused scores, reranked positions, or quality labels.

| policy_option | role |
|---|---|
| es_baseline_passthrough | backend_baseline |
| qdrant_baseline_passthrough | backend_baseline |
| combined_union_placeholder | combined_experiment_placeholder |
| combined_intersection_placeholder | combined_experiment_placeholder |
| tie_breaker_placeholder | combined_experiment_placeholder |
| accepted_negative_control_exclusion_policy | exclusion_policy |

## Experiment plan group counts

Each consumed M12A input envelope becomes one schema-only experiment-plan row. Backend baseline
rows plan a dedicated future ES or Qdrant baseline passthrough policy; combined experiment rows
plan the three combined future placeholders (union, intersection, tie-breaker); the accepted
negative-control exclusion plans the exclusion policy; manual/no-op rows plan no policy row at
all. No row is executable and no real scored/reranked result is produced.

| experiment_plan_group | count |
|---|---|
| es_baseline_rows | 14 |
| qdrant_baseline_rows | 1 |
| combined_experiment_rows | 48 |
| accepted_negative_control_exclusion_rows | 1 |
| manual_or_no_op_rows | 0 |
| executable_policy_rows | 0 |
| real_scored_or_reranked_rows | 0 |

## q_noise_004 and q_noise_005 mappings

Both ids share the q_noise_* prefix yet land in different M12 plan rows: q_noise_004 = gel removal
maps to a combined placeholder experiment-plan row (combined_union_placeholder,
combined_intersection_placeholder, tie_breaker_placeholder, all non-executable); q_noise_005 =
lifting maps to the accepted_negative_control_exclusion_policy row with no backend candidate
policy and no real scored/reranked output.

| query_id | experiment_plan_group | assigned_policies | executable | real_scored_or_reranked |
|---|---|---|---|---|
| q_noise_004 | combined_experiment_rows | combined_union_placeholder, combined_intersection_placeholder, tie_breaker_placeholder | false | false |
| q_noise_005 | accepted_negative_control_exclusion_rows | accepted_negative_control_exclusion_policy | false | false |

## Metrics

| metric | value |
|---|---|
| consumed_m12a_input_scaffold_verdict | m12_fusion_reranking_input_scaffold_ready_schema_only |
| consumed_m11b_result_schema_verdict | m11_candidate_generation_result_schema_ready |
| consumed_m11c_boundary_failure_matrix_verdict | m11_candidate_generation_boundary_failure_matrix_ready |
| consumed_input_row_count | 64 |
| policy_count | 6 |
| policy_names_unique | 6 |
| policy_backend_baseline_count | 2 |
| policy_combined_experiment_count | 3 |
| policy_exclusion_count | 1 |
| es_baseline_rows | 14 |
| qdrant_baseline_rows | 1 |
| combined_experiment_rows | 48 |
| accepted_negative_control_exclusion_rows | 1 |
| manual_or_no_op_rows | 0 |
| executable_policy_rows | 0 |
| real_scored_or_reranked_rows | 0 |
| policy_plan_count_sum | 64 |
| m12_fusion_reranking_policy_catalog_ready | true |
| m12_catalog_is_schema_only_not_scoring | true |
| m12_catalog_is_schema_only_not_fusion | true |
| m12_catalog_is_schema_only_not_reranking | true |
| m12_catalog_is_schema_only_not_candidate_retrieval | true |
| m12_catalog_is_schema_only_not_backend_execution | true |
| m12_catalog_is_offline_not_production_routing | true |
| m12_combined_experiment_policies_are_placeholders_only | true |
| m12_combined_experiment_policies_do_not_imply_hybrid_serving | true |
| accepted_negative_control_exclusion_has_no_backend_candidate_policy | true |
| manual_and_no_op_exclusions_have_no_backend_candidate_policy | true |
| no_real_candidate_ids_provider_ids_scores_ranks_fabricated | true |
| no_real_backend_responses_fabricated | true |
| no_fused_scores_reranked_positions_quality_labels_fabricated | true |
| no_fusion_outputs_reranking_outputs_fabricated | true |
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
| verdict | m12_fusion_reranking_policy_catalog_ready_schema_only |

## Boundary summary

Boundary posture is reported as structured booleans in the Metrics table above, not as prose negations.
This is a schema-only policy catalog and experiment-plan artifact, not scoring, not fusion
execution, not reranking execution, not backend execution, and not production routing: no
candidate ids, provider ids, scores, ranks, backend responses, fused scores, reranked positions,
quality labels, fusion outputs, or reranking outputs are fabricated, and combined experiment
placeholders do not imply production hybrid serving.
