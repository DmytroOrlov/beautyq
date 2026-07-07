# M10 BeautyQ Query Classification & Offline Routing Examples

Offline planning fixture only. These are checked-in expected rows for representative BeautyQ eval
queries. No production `/beauty-search` call, no ES or Qdrant client, no ES or Qdrant execution, and no
route/plugin/DI/HTTP path is represented. Default `/beauty-search` remains ES-backed; the Qdrant opt-in
route stays disabled by default; Qdrant production activation remains not approved. `strategy_intent`
values are offline study intents only, never production routes.

| query_id | anchor | category | strategy_intent |
|---|---|---|---|
| q_nails_001 | yes | mixed_intent | combined_es_qdrant_comparison |
| q_nails_003 | yes | attribute_filter_intent | es_only_candidate_retrieval |
| q_noise_005 | yes | noisy_ambiguous_non_beauty_intent | manual_review_blocked |
| q_provider_001 | no | provider_lookup | es_only_candidate_retrieval |
| q_service_001 | no | service_intent | es_only_candidate_retrieval |
| q_location_001 | no | location_intent | es_only_candidate_retrieval |
| q_price_001 | no | mixed_intent | combined_es_qdrant_comparison |
| q_price_002 | no | price_budget_intent | es_only_candidate_retrieval |
| q_time_001 | no | availability_time_intent | es_only_candidate_retrieval |
| q_explore_001 | no | comparison_exploration_intent | qdrant_only_candidate_retrieval |
| q_nonbeauty_001 | no | noisy_ambiguous_non_beauty_intent | no_op_noise |
| q_mixed_001 | no | mixed_intent | combined_es_qdrant_comparison |
