# BeautyQ ES + Qdrant + Hybrid Retrieval Roadmap

This document records the desired end-state for BeautyQ retrieval as a roadmap, not as current implementation.
Current production `/beauty-search` remains ES-backed; Qdrant explicit opt-in exists; hybrid serving is not implemented.

## 1. User Goals in Precise Technical Terms

The user-facing goal translates to the following engineering target:

- keep the default production `/beauty-search` route under explicit approval control;
- allow ES, Qdrant, or hybrid serving to be selected intentionally rather than assumed;
- measure where ES is better, where Qdrant is better, and where a hybrid candidate union is beneficial;
- keep offline seed/eval metrics as decision support only;
- once real production traffic exists, collect production telemetry and online quality signals;
- make routing and fusion policies evidence-based, rollbackable, observable, and separately approved.

Rough wording translated to technical terms:

- "по прод-ручке" means the default production `/beauty-search` route.
- "квадрант" means Qdrant-backed semantic/vector retrieval.
- "гибрид намешан" means hybrid retrieval: ES candidates + Qdrant candidates + explicit candidate fusion/reranking/routing policy.
- "метрики на сид запросах к сид данным" means offline eval over curated canonical seed queries and representative seed/eval fixture queries against the seed-resource catalog snapshot.
- "запустив прод собрать прод метрики" means collecting online production telemetry after controlled production activation.
- "часть данных искать в Qdrant, часть лучше наоборот в ES" means query/source routing policy based on query class, intent, field, category, confidence, or similar evidence.

## 2. Current Accepted Baseline

Keep the current-state block short here:

- Default production `/beauty-search` remains ES-backed.
- Qdrant explicit opt-in exists and stays disabled by default.
- Production activation remains not approved.
- Hybrid serving, fallback, fusion, reranking, telemetry emission, and real ES/Qdrant backend-runner execution remain future work.
- Pure ES/Qdrant offline adapter skeletons and the resource-gated real-backend spike scaffold for M9 exist under `docs/local/M8_M9_TELEMETRY_AND_OFFLINE_EVAL_PLAN.md`; they are offline eval foundations only and do not imply route activation. Successful real offline adapter execution remains future/resource-gated and is not default.
- M10 query classification, offline routing policy, full classification coverage, and retrieval-policy readiness are accepted as closed offline planning/reporting contracts (see M10 closeout below and `docs/BEAUTYQ_CURRENT_STATE_AND_HANDOFF.md` section 0). M10 prepares offline M11 candidate-generation inputs only; it does not execute ES/Qdrant and does not change production routing.
- M11 candidate-generation input/request skeleton, result schema, and boundary/failure matrix are accepted as closed offline eval/planning/reporting contracts (see M11 closeout below and `docs/BEAUTYQ_CURRENT_STATE_AND_HANDOFF.md` section 0). M11 does not execute ES/Qdrant, does not implement candidate retrieval/fusion/reranking, and does not change production routing.
- M12 fusion/reranking input scaffold, policy catalog/experiment-plan schema, boundary/failure matrix, and saved-output schema are accepted as closed offline eval/planning/reporting contracts (see M12 closeout below and `docs/BEAUTYQ_CURRENT_STATE_AND_HANDOFF.md` section 0). M12 prepares placeholder-only planning/reporting surfaces only; it does not implement scoring/fusion execution/reranking execution/candidate retrieval/backend execution and does not change production routing.

For exact route/module truth and verification counts, use `docs/BEAUTYQ_CURRENT_STATE_AND_HANDOFF.md`. For the separate activation gate, use `docs/local/QDRANT_PRODUCTION_ACTIVATION_DECISION_CRITERIA.md`. For M8/M9 implementation status, use `docs/local/M8_M9_TELEMETRY_AND_OFFLINE_EVAL_PLAN.md`.

## 3. What Is Done vs Not Done

| Area | Status | Notes |
|---|---|---|
| ES production route | Done | Current default production `/beauty-search` is ES-backed. |
| Qdrant explicit opt-in route | Done | Disabled by default, separate from the default graph. |
| Qdrant default production activation | Not done | Not approved. |
| Hybrid candidate generation | Not done | No ES+Qdrant candidate union in production serving. |
| Score fusion / reranking | Not done | No implemented fusion policy. |
| M8/M9 shared contracts | Started | Pure vocabulary, M8 telemetry schema renderer/static adapter, saved-report, static-runner, fixture, backend-runner interface, ES/Qdrant offline adapter skeleton, backend-adapter failure-matrix, and resource-gated adapter-spike scaffold slices exist; telemetry emission and successful real ES/Qdrant backend-runner execution stay future/resource-gated under `docs/local/M8_M9_TELEMETRY_AND_OFFLINE_EVAL_PLAN.md`. |
| Production telemetry loop | Not done | No real production traffic yet. |
| Online ES vs Qdrant routing policy | Not done | No production routing policy. |
| Docs/checklists/decision criteria | Done | Existing opt-in and activation criteria docs are in place. |

## 4. Target Architecture

The desired end-state has several controlled modes:

- ES-only serving for query classes that are lexical, filter-heavy, exact, or otherwise better handled by ES primitives.
- Qdrant-only serving for query classes that are semantic or broad enough to benefit from vector retrieval.
- Hybrid serving for query classes where ES and Qdrant both contribute useful candidates.
- Per-query routing based on explicit, inspectable evidence rather than backend-local heuristics hidden inside interpreters.
- Candidate union with source attribution so downstream systems can see where candidates came from.
- Fusion or reranking only after it is separately implemented, tested, and approved.
- Observability and rollback so a production decision can be reversed without ambiguity.

This target is stricter than the current opt-in Qdrant readiness track. Opt-in readiness proves a separate route can be constructed and exercised; it does not prove that production should switch, that hybrid is safe, or that production telemetry exists.

## 5. Roadmap Phases

### M8. Production telemetry foundation

Shared M8/M9 vocabulary, schema boundaries, dataset coverage, stop conditions, implemented pure slices, and the pure backend-runner interface seam are owned by `docs/local/M8_M9_TELEMETRY_AND_OFFLINE_EVAL_PLAN.md`.

- define request/result event schema plan;
- render the planned M8 schema through pure deterministic schema artifacts;
- record backend attribution;
- record latency metrics;
- record quality proxy metrics;
- record failure metrics;
- keep the default route unchanged;
- do not add telemetry emission, metrics-client integration, route hooks, or production traffic observation in the schema-renderer slice;
- do not activate production hybrid yet.

### M9. Stronger offline eval harness

Use the shared vocabulary and reporting boundaries from `docs/local/M8_M9_TELEMETRY_AND_OFFLINE_EVAL_PLAN.md` and `leaderboard.search.eval.M8M9EvalContracts` so offline eval and any later telemetry do not drift apart.

- define curated canonical seed queries;
- define and render saved M9 dataset/report artifacts;
- keep representative seed/eval fixture queries;
- include regression, edge, negative, and ambiguous cases;
- compare ES-only, Qdrant-only, and simulated hybrid offline;
- track Recall@K, MRR, nDCG@K, zero-result rate, and latency;
- keep the overfitting warning explicit.

The saved-report format, renderer slice, static/in-memory runner skeleton, canonical static fixtures, checked-in example artifact, pure backend-runner interface seam, pure ES/Qdrant offline adapter skeletons, backend-adapter failure-matrix hardening, and resource-gated adapter-spike scaffold are tracked in the M8/M9 owner doc above. The stronger offline eval harness, successful real ES/Qdrant backend runner, report persistence workflow, and production-facing quality-gate update remain future work.
Backend-adapter failure-matrix hardening and the resource-gated adapter-spike scaffold are complete as offline eval foundations; they do not implement successful real ES/Qdrant execution or serving.
Future real ES/Qdrant offline execution belongs to that M8/M9 owner doc as planned offline-only work, not as production route or activation work.

### M10. Query classification and routing policy — closed as offline planning/reporting only

**M10 is closed.** Query classification foundation, offline routing policy foundation, full 63-query classification coverage scorecard, manual-review/negative-control hardening, and the retrieval-policy readiness contract (M11 input-preparation artifact) are accepted offline contracts. See `docs/BEAUTYQ_CURRENT_STATE_AND_HANDOFF.md` section 0 for the consolidated category/strategy-intent/M11-input-group counts and the readiness verdict `m11_candidate_generation_inputs_ready_with_negative_control_exclusion`.

M10 maps query classes to ES, Qdrant, or combined-comparison offline study-input groups for M11. It prepares offline M11 candidate-generation inputs only — it does not implement M11 candidate generation, does not execute real ES/Qdrant, and does not change production routing. Default `/beauty-search` remains ES-backed, the Qdrant opt-in route stays disabled by default, and no production activation, route switch, hybrid serving, fallback, score fusion, reranking, or production telemetry is implemented or accepted by this milestone.

### M11. Hybrid candidate generation — closed as offline eval/planning/reporting only

**M11 is closed.** Candidate-generation input/request skeleton, result schema (saved/pending placeholders), and boundary/failure matrix are accepted offline contracts, consuming M10's retrieval-policy readiness input-preparation output. See `docs/BEAUTYQ_CURRENT_STATE_AND_HANDOFF.md` section 0 for the consolidated row/disposition/matrix counts.

M11 defines offline request shapes, saved result/report placeholders, and boundary/failure handling only. It prepares scaffolding for M12 fusion/reranking input; it does not implement candidate retrieval, scoring, fusion, reranking, fallback, backend execution, or production routing. Default `/beauty-search` remains ES-backed, the Qdrant opt-in route stays disabled by default, and no production activation, route switch, hybrid serving, fallback, score fusion, reranking, or production telemetry is implemented or accepted by this milestone.

Future real candidate generation — actually generating ES top-N / Qdrant top-M candidates, deduplicating, preserving source attribution, and defining stable empty/error behavior — remains unimplemented and opt-in only, not default production.

### M12. Fusion and reranking experiments — closed as offline eval/planning/reporting only

**M12 is closed.** Fusion/reranking input scaffold, policy catalog and experiment-plan schema, boundary/failure matrix, and saved-output schema are accepted offline contracts, consuming M11's saved-result output. See `docs/BEAUTYQ_CURRENT_STATE_AND_HANDOFF.md` section 0 for the consolidated row/policy/matrix counts.

M12 defines schema-only experiment input envelopes, stable non-executable policy names, schema-only experiment-plan rows, a boundary/failure matrix, and a placeholder-only saved-output/report schema. It prepares placeholder-only planning/reporting surfaces for later evidence work; it does not implement scoring, fusion execution, reranking execution, candidate retrieval, backend execution, backend clients, or production routing. Default `/beauty-search` remains ES-backed, the Qdrant opt-in route stays disabled by default, and no production activation, route switch, hybrid serving, fallback, score fusion, reranking, or production telemetry is implemented or accepted by this milestone.

Future real fusion/reranking experiments — testing ES-first, Qdrant-first, weighted-score, reciprocal rank fusion, rule-based reranking, and learned reranking policies, evaluated by query class — remain unimplemented and opt-in only, not default production.

### M13. Controlled hybrid explicit opt-in route

- add a disabled-by-default hybrid route/module;
- do not include it in the default `apiElasticsearch` graph;
- add route exposure tests;
- expose observability fields;
- add a smoke checklist;
- keep it separate from production activation.

### M14. Production telemetry dry-run

- if real production traffic exists, use controlled telemetry, shadowing, or replay only after explicit approval;
- if no real traffic exists, do not pretend shadow metrics are meaningful;
- keep offline/resource/manual evidence honest.

### M15. Production activation decision

- choose ES-only, narrow Qdrant, narrow hybrid, global hybrid, or no activation;
- require explicit approval;
- keep rollback and observability part of the decision.

### M16. Production activation implementation

- implement only after explicit approval;
- keep it config-gated;
- keep it rollbackable;
- keep it observable;
- keep route-exposure tests in place;
- rerun the full suite after implementation;
- do not add hidden hybrid, fallback, fusion, reranking, shadowing, or mirroring behavior.

### M17. Online optimization loop

- collect production metrics;
- identify ES winners, Qdrant winners, and hybrid winners;
- tune routing policy;
- tune fusion weights;
- tune candidate counts;
- tune ES analyzers if needed;
- tune embeddings if needed;
- add real failure cases to offline eval;
- repeat with explicit approvals and rollback paths.

## 6. Senior-Design Criteria

This architecture is only senior-grade if it keeps the following properties:

- evidence before activation;
- no fake production claims;
- rollback before rollout;
- source attribution for candidates;
- query-class metrics instead of a single vague score;
- offline and online metrics kept separate;
- full-suite and focused tests both used where appropriate;
- explicit approvals for production changes;
- no hidden serving behavior.

## 7. Stop Conditions

Do not activate or continue activation if any of the following are true:

- production activation approval is absent;
- the default graph exposure is unclear;
- rollback or disable is missing;
- observability is missing;
- the full suite is not green;
- route exposure tests fail;
- resource smoke fails;
- hybrid metrics only improve cherry-picked seed queries;
- latency budget is exceeded;
- online metrics regress after activation.

## 8. Final Target Definition of Done

The user’s full desired system is done only when all of the following are true:

- the ES production baseline is measured;
- the Qdrant opt-in path is green;
- the hybrid path exists behind a gate;
- offline eval compares ES, Qdrant, and hybrid;
- a query-class routing policy exists;
- fusion or reranking strategy is selected by evidence;
- production telemetry exists;
- activation decision is explicit;
- any activation is guarded and rollbackable;
- online metrics are collected after activation;
- docs describe current truth without overstating readiness.

This roadmap does not claim those conditions are already met.
