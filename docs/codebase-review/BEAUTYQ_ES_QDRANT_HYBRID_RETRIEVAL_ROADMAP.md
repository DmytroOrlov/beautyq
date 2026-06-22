# BeautyQ ES + Qdrant + Hybrid Retrieval Roadmap

This document records the desired end-state for BeautyQ retrieval as a roadmap, not as current implementation.
Current production `/beauty-search` remains ES-backed; Qdrant explicit opt-in exists; hybrid serving is not implemented.

**M17 scope correction:** M17 (see `docs/BEAUTYQ_CURRENT_STATE_AND_HANDOFF.md` section 1) closed foundation/gate/evidence work, not production hybrid search. The hybrid-production phases below were originally numbered M14–M17, which collided with the unrelated gate-and-evidence track's own M14/M16 numbering. They are renumbered M18–M21 here to remove that collision.

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
- Pure ES/Qdrant offline adapter skeletons and the resource-gated real-backend spike scaffold for M9 exist under `docs/codebase-review/M8_M9_TELEMETRY_AND_OFFLINE_EVAL_PLAN.md`; they are offline eval foundations only and do not imply route activation. Successful real offline adapter execution remains future/resource-gated and is not default.
- M10–M13 (query classification, candidate generation, fusion/reranking, and controlled opt-in route planning) are accepted as closed offline planning/reporting contracts; see the M10–M13 closeout below and `docs/BEAUTYQ_CURRENT_STATE_AND_HANDOFF.md` section 2. None of them executes ES/Qdrant, implements candidate retrieval/fusion/reranking, or changes production routing.

For exact route/module truth and verification counts, use `docs/BEAUTYQ_CURRENT_STATE_AND_HANDOFF.md`. For the separate activation gate, use `docs/codebase-review/QDRANT_PRODUCTION_ACTIVATION_DECISION_CRITERIA.md`. For M8/M9 implementation status, use `docs/codebase-review/M8_M9_TELEMETRY_AND_OFFLINE_EVAL_PLAN.md`.

## 3. What Is Done vs Not Done

| Area | Status | Notes |
|---|---|---|
| ES production route | Done | Current default production `/beauty-search` is ES-backed. |
| Qdrant explicit opt-in route | Done | Disabled by default, separate from the default graph. |
| Qdrant default production activation | Not done | Not approved. |
| Hybrid candidate generation | Not done | No ES+Qdrant candidate union in production serving. |
| Score fusion / reranking | Not done | No implemented fusion policy. |
| M8/M9 shared contracts | Started | Pure vocabulary, M8 telemetry schema renderer/static adapter, saved-report, static-runner, fixture, backend-runner interface, ES/Qdrant offline adapter skeleton, backend-adapter failure-matrix, and resource-gated adapter-spike scaffold slices exist; telemetry emission and successful real ES/Qdrant backend-runner execution stay future/resource-gated under `docs/codebase-review/M8_M9_TELEMETRY_AND_OFFLINE_EVAL_PLAN.md`. |
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

Shared M8/M9 vocabulary, schema boundaries, dataset coverage, stop conditions, implemented pure slices, and the pure backend-runner interface seam are owned by `docs/codebase-review/M8_M9_TELEMETRY_AND_OFFLINE_EVAL_PLAN.md`.

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

Use the shared vocabulary and reporting boundaries from `docs/codebase-review/M8_M9_TELEMETRY_AND_OFFLINE_EVAL_PLAN.md` and `leaderboard.search.eval.M8M9EvalContracts` so offline eval and any later telemetry do not drift apart.

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

### M10–M13. Query classification, candidate generation, fusion/reranking, and opt-in route planning — closed as offline planning/reporting only

M10 (query classification and retrieval-policy readiness), M11 (candidate-generation input/result schema), M12 (fusion/reranking input scaffold and policy catalog), and M13 (controlled opt-in route/module planning) are all closed as offline planning/reporting/contract work only, each consuming the prior milestone's output as its own planning input. None of them implements candidate retrieval, scoring, fusion, reranking, backend execution, or production routing; M13 additionally denies drift into default route switch, production route activation, or hybrid serving. Default `/beauty-search` stays ES-backed and the Qdrant opt-in route stays disabled by default throughout. See `docs/BEAUTYQ_CURRENT_STATE_AND_HANDOFF.md` section 2 for the consolidated milestone table.

Future real work in this lane — actual candidate generation, real fusion/reranking experiments, and a disabled-by-default hybrid route/module — remains unimplemented and opt-in only, not default production.

**M14A/M14B/M14C are accepted**: route-gate / serving-readiness design contract, the disabled-by-default `BeautySearchServingGate` runtime route gate, and the `BeautySearchLocalDevOnlyFallbackPolicy` local/dev-only fallback contract — gate/fallback contract work only, not production activation. See `docs/BEAUTYQ_CURRENT_STATE_AND_HANDOFF.md` section 2. This gate-and-evidence track's M14/M16 numbering belongs to a separate, unrelated track from this roadmap's own milestones; the hybrid-production phases that used to collide with it (M14–M17 below) are renumbered M18–M21 below.

**M15A/M15B/M16A/M16B are accepted and closed**: the M15 manual smoke checklist and its removal once superseded, the M16A explicit serving-gate selector surface, and the M16B executable serving-gate evidence harness. See `docs/BEAUTYQ_CURRENT_STATE_AND_HANDOFF.md` section 2. This "M16" belongs to the same gate-and-evidence track noted above, unrelated to this roadmap's M18–M21.

### M18. Real ES+Qdrant fusion engine / dual-engine retrieval execution

- implement real ES-native retrieval and real Qdrant-native retrieval as dual-engine execution, not simulated/offline-only;
- define the candidate-union/fusion engine that combines ES-native and Qdrant-native results into one maximally accurate combined response;
- keep this engine out of the default production route until M20/M21 approve a serving path;
- no production wiring, no default route switch, no fallback.

### M19. Metrics and combination policy decision

- define the metrics used to compare and combine ES-native and Qdrant-native results (per-query-class and aggregate);
- decide the combination/fusion policy from evidence, not from a fixed heuristic;
- keep this decision-support only; it does not authorize serving.

### M20. Hidden/disabled-by-default controlled hybrid serving route

- implement a hybrid serving route gated disabled-by-default, mirroring the existing `BeautySearchServingGate` pattern;
- keep it outside the default `apiElasticsearch` graph, same as the Qdrant explicit opt-in route;
- route-exposure tests and full-suite verification required before this milestone is accepted;
- does not change the production default.

### M21. Activation decision / default-route switch approval or rejection

- explicit approval or rejection of switching the default `/beauty-search` route to hybrid (or narrow Qdrant);
- require rollback/disable plan, observability evidence, and full-suite verification as part of the decision record;
- this is the only milestone that can approve a default-route switch; absence of approval here means the ES-backed default remains unchanged.

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
