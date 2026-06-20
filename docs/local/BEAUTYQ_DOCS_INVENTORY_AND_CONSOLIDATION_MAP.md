# BeautyQ Docs Inventory And Consolidation Map

Purpose: map current BeautyQ/Search docs by role so future cleanup can remove duplication without losing the current source of truth.

Patch rules for this pass:

- Do not delete docs.
- Do not move docs.
- Do not rename docs.
- Do not archive docs.
- Reduce future duplication by preferring one canonical owner per topic and replacing repeats with links.

## First read and authority order

Read order for current truth:

1. `docs/BEAUTYQ_CURRENT_STATE_AND_HANDOFF.md`
2. `docs/codebase-review/05-search-and-retrieval-architecture.md`
3. `docs/codebase-review/06-tests-and-contracts.md`
4. Topic-specific local docs below when the question is about activation, roadmap, telemetry/eval planning, or operator runbooks.

Current authority by repeated topic:

- Current production route truth: `docs/BEAUTYQ_CURRENT_STATE_AND_HANDOFF.md`
- ES-default production route wiring/details: `docs/BEAUTYQ_CURRENT_STATE_AND_HANDOFF.md` plus source `LeaderboardPlugin.scala`, `BeautySearchRouteModules.scala`, `BeautySearchCatalogBackendModules.scala`
- Qdrant explicit opt-in state: `docs/search-dsl-qdrant-vector-backend.md`
- Qdrant production activation criteria: `docs/local/QDRANT_PRODUCTION_ACTIVATION_DECISION_CRITERIA.md`
- ES/Qdrant/hybrid roadmap: `docs/local/BEAUTYQ_ES_QDRANT_HYBRID_RETRIEVAL_ROADMAP.md`
- M8/M9 telemetry and offline eval planning: `docs/local/M8_M9_TELEMETRY_AND_OFFLINE_EVAL_PLAN.md`
- Current M9 static eval artifact shape: `docs/local/M8_M9_TELEMETRY_AND_OFFLINE_EVAL_PLAN.md` plus source/tests `M9OfflineEvalSavedReport`, `M9OfflineEvalStaticRunner`, `M9OfflineEvalStaticFixtures`, and `bifunctor-tagless/src/test/resources/leaderboard/search/eval/m9-static-example-report.md`
- Evidence templates and local runbooks: `docs/local/M_ESQ_EVAL_EVIDENCE_RUN_TEMPLATE.md` and `docs/local/QDRANT_EXPLICIT_OPTIN_ROUTE_SMOKE_CHECKLIST.md`

## Group classification

### 1. Canonical current-state source truth

- `docs/BEAUTYQ_CURRENT_STATE_AND_HANDOFF.md`

### 2. Architecture overview

- `docs/codebase-review/05-search-and-retrieval-architecture.md`
- `docs/search-dsl-qdrant-vector-backend.md`
- `docs/search-dsl-hybrid-v1-plan.md`

### 3. Test/contract evidence

- `docs/codebase-review/06-tests-and-contracts.md`
- `bifunctor-tagless/src/test/scala/leaderboard/search/M8M9EvalContractsSpec.scala`
- `bifunctor-tagless/src/test/scala/leaderboard/search/M9OfflineEvalSavedReportSpec.scala`
- `bifunctor-tagless/src/test/scala/leaderboard/search/M9OfflineEvalStaticRunnerSpec.scala`
- `bifunctor-tagless/src/test/scala/leaderboard/search/M9OfflineEvalStaticFixturesSpec.scala`

### 4. Roadmap/future-track planning

- `docs/codebase-review/07-current-gaps-and-roadmap.md`
- `docs/local/BEAUTYQ_ES_QDRANT_HYBRID_RETRIEVAL_ROADMAP.md`
- `docs/local/M8_M9_TELEMETRY_AND_OFFLINE_EVAL_PLAN.md`
- `docs/search-dsl-hybrid-v1-plan.md`

### 5. Local runbooks/checklists/templates

- `docs/local/M_ESQ_EVAL_EVIDENCE_RUN_TEMPLATE.md`
- `docs/local/QDRANT_EXPLICIT_OPTIN_ROUTE_SMOKE_CHECKLIST.md`

### 6. Historical or superseded planning docs

- `docs/search-dsl-hybrid-v1-plan.md` for older hybrid-step planning now partially superseded by the broader local roadmap
- Parts of `docs/search-dsl-qdrant-vector-backend.md` that still describe approval-request history rather than only present opt-in state

These remain useful context and must stay for now.

### 7. Candidate docs for future consolidation

- `docs/search-dsl-qdrant-vector-backend.md`
- `docs/search-dsl-hybrid-v1-plan.md`
- `docs/local/BEAUTYQ_ES_QDRANT_HYBRID_RETRIEVAL_ROADMAP.md`
- `docs/local/M8_M9_TELEMETRY_AND_OFFLINE_EVAL_PLAN.md`
- `docs/local/M_ESQ_EVAL_EVIDENCE_RUN_TEMPLATE.md`
- `docs/local/QDRANT_EXPLICIT_OPTIN_ROUTE_SMOKE_CHECKLIST.md`
- `docs/codebase-review/07-current-gaps-and-roadmap.md`

### 8. Docs that must not be deleted yet

- `docs/BEAUTYQ_CURRENT_STATE_AND_HANDOFF.md`
- `docs/codebase-review/05-search-and-retrieval-architecture.md`
- `docs/codebase-review/06-tests-and-contracts.md`
- `docs/codebase-review/07-current-gaps-and-roadmap.md`
- `docs/search-dsl-qdrant-vector-backend.md`
- `docs/search-dsl-hybrid-v1-plan.md`
- `docs/local/BEAUTYQ_ES_QDRANT_HYBRID_RETRIEVAL_ROADMAP.md`
- `docs/local/M8_M9_TELEMETRY_AND_OFFLINE_EVAL_PLAN.md`
- `docs/local/M_ESQ_EVAL_EVIDENCE_RUN_TEMPLATE.md`
- `docs/local/QDRANT_EXPLICIT_OPTIN_ROUTE_SMOKE_CHECKLIST.md`
- `docs/local/QDRANT_PRODUCTION_ACTIVATION_DECISION_CRITERIA.md`

Reason: each still owns either current truth, approval criteria, future-track planning, test evidence references, or operator workflow detail that is not yet fully absorbed elsewhere.

## Topic owner map

| Topic | Canonical owner | Secondary docs that should link instead of restating |
|---|---|---|
| current production route truth | `docs/BEAUTYQ_CURRENT_STATE_AND_HANDOFF.md` | `07-current-gaps-and-roadmap.md`, `05-search-and-retrieval-architecture.md`, local roadmap docs |
| Qdrant explicit opt-in route | `docs/search-dsl-qdrant-vector-backend.md` | handoff, `07-current-gaps-and-roadmap.md`, smoke checklist |
| Qdrant production activation criteria | `docs/local/QDRANT_PRODUCTION_ACTIVATION_DECISION_CRITERIA.md` | handoff, Qdrant backend doc, roadmap docs, smoke checklist |
| ES/Qdrant/hybrid roadmap | `docs/local/BEAUTYQ_ES_QDRANT_HYBRID_RETRIEVAL_ROADMAP.md` | `07-current-gaps-and-roadmap.md`, `search-dsl-hybrid-v1-plan.md` |
| M8/M9 telemetry and offline eval plan | `docs/local/M8_M9_TELEMETRY_AND_OFFLINE_EVAL_PLAN.md` | handoff, roadmap docs, evidence template |
| M9 static eval contracts/fixtures | `docs/local/M8_M9_TELEMETRY_AND_OFFLINE_EVAL_PLAN.md` plus source/tests | evidence template, roadmap docs |
| smoke checklist | `docs/local/QDRANT_EXPLICIT_OPTIN_ROUTE_SMOKE_CHECKLIST.md` | handoff, activation criteria doc |
| eval evidence template | `docs/local/M_ESQ_EVAL_EVIDENCE_RUN_TEMPLATE.md` | M8/M9 plan, roadmap docs |
| future ES replacement/freshness/rollback | `docs/codebase-review/07-current-gaps-and-roadmap.md` | handoff, architecture doc |
| remaining pending placeholders | `docs/codebase-review/06-tests-and-contracts.md` | handoff, roadmap doc |

## Inventory table

| Path | Role | Status | Canonical owner topic | Recommendation | Reason |
|---|---|---|---|---|---|
| `docs/BEAUTYQ_CURRENT_STATE_AND_HANDOFF.md` | coordinator current-state handoff | current | current production route truth | keep | first read for current truth; anchor for ES-backed default route and accepted boundaries |
| `docs/codebase-review/05-search-and-retrieval-architecture.md` | architecture overview and source-confirmed seams | current | architecture overview | keep/link | useful architecture map, but repeated status blocks should link back to the handoff |
| `docs/codebase-review/06-tests-and-contracts.md` | test and contract authority | current | remaining pending placeholders | keep | canonical test/contract source, including pending map and M8/M9 contract evidence |
| `docs/codebase-review/07-current-gaps-and-roadmap.md` | gaps and future-track roadmap | current/future | future ES replacement/freshness/rollback | keep/link | active roadmap owner; should point to inventory for deletion/archive decisions |
| `docs/search-dsl-qdrant-vector-backend.md` | Qdrant backend status and boundaries | current with historical sections | Qdrant explicit opt-in route | keep/link/merge later | still best owner for Qdrant explicit opt-in state, but repeats activation and roadmap material |
| `docs/search-dsl-hybrid-v1-plan.md` | hybrid plan history and B-lite framing | historical/planning | ES/Qdrant/hybrid roadmap | keep/link/merge later | still useful for historical design intent; broader roadmap now lives in local roadmap doc |
| `docs/local/BEAUTYQ_ES_QDRANT_HYBRID_RETRIEVAL_ROADMAP.md` | broader retrieval roadmap | local/future | ES/Qdrant/hybrid roadmap | keep | best single owner for long-range ES/Qdrant/hybrid phases |
| `docs/local/M8_M9_TELEMETRY_AND_OFFLINE_EVAL_PLAN.md` | telemetry/offline eval plan and implemented pure slices | local/current/future | M8/M9 telemetry and offline eval plan | keep | canonical M8/M9 owner, including current pure slices and unimplemented boundaries |
| `docs/local/M_ESQ_EVAL_EVIDENCE_RUN_TEMPLATE.md` | evidence collection template | local/runbook | eval evidence template | keep/link | should stay a template, not a status source; link to M8/M9 plan for canonical artifact vocabulary |
| `docs/local/QDRANT_EXPLICIT_OPTIN_ROUTE_SMOKE_CHECKLIST.md` | bounded opt-in smoke checklist | local/runbook | smoke checklist | keep/link | operator checklist; should not own route status beyond checklist scope |
| `docs/local/QDRANT_PRODUCTION_ACTIVATION_DECISION_CRITERIA.md` | activation gate for any future default-route switch | local/current/future | Qdrant production activation criteria | keep | canonical approval criteria; must remain separate from opt-in readiness and roadmap docs |

## Duplicate topics and future owners

Repeated topic: current production ES-backed route.

- Future owner: `docs/BEAUTYQ_CURRENT_STATE_AND_HANDOFF.md`
- Duplicates now present in: `05-search-and-retrieval-architecture.md`, `07-current-gaps-and-roadmap.md`, `search-dsl-qdrant-vector-backend.md`, `BEAUTYQ_ES_QDRANT_HYBRID_RETRIEVAL_ROADMAP.md`, `M8_M9_TELEMETRY_AND_OFFLINE_EVAL_PLAN.md`
- Future action: keep one concise status line outside the handoff and link back.

Repeated topic: Qdrant explicit opt-in exists and is disabled by default.

- Future owner: `docs/search-dsl-qdrant-vector-backend.md`
- Duplicates now present in: handoff, `07-current-gaps-and-roadmap.md`, local roadmap, M8/M9 plan, smoke checklist, activation criteria
- Future action: keep operational mentions where needed, but move the state explanation to one owner and link.

Repeated topic: production activation remains not approved.

- Future owner: `docs/local/QDRANT_PRODUCTION_ACTIVATION_DECISION_CRITERIA.md`
- Duplicates now present in: handoff, Qdrant backend doc, local roadmap, M8/M9 plan, smoke checklist
- Future action: keep only short reminders elsewhere.

Repeated topic: hybrid/fusion/reranking remain unimplemented.

- Future owner: `docs/local/BEAUTYQ_ES_QDRANT_HYBRID_RETRIEVAL_ROADMAP.md`
- Duplicates now present in most BeautyQ docs
- Future action: reduce repeated non-goal lists and replace with links to the roadmap.

Repeated topic: M8/M9 pure slices exist but telemetry emission and backend runners do not.

- Future owner: `docs/local/M8_M9_TELEMETRY_AND_OFFLINE_EVAL_PLAN.md`
- Duplicates now present in: handoff, `07-current-gaps-and-roadmap.md`, local roadmap, evidence template
- Future action: retain only short implementation-status pointers outside the M8/M9 plan.

Repeated topic: M9 static artifact shape and checked-in example.

- Future owner: `docs/local/M8_M9_TELEMETRY_AND_OFFLINE_EVAL_PLAN.md` plus source/tests
- Duplicates now present in: evidence template and roadmap mentions
- Future action: point future readers to the plan doc and checked-in fixture path instead of repeating artifact-shape prose.

## Consolidation guidance for a later patch

- Keep this patch as inventory only.
- Do not delete, move, rename, or archive docs yet.
- Start future cleanup by replacing duplicated status paragraphs with one-line links to the canonical owner.
- Keep local runbooks as local runbooks; they should not become canonical status owners.
- Keep source-backed test evidence in `06-tests-and-contracts.md`; planning docs should link to it rather than restating test meaning.
- Keep approval criteria separate from readiness or roadmap prose; do not merge activation criteria into a broader planning doc unless the replacement preserves a strict approval section.
