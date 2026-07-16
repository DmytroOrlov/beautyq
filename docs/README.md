# Documentation index

## Start here

* [Repository README](../README.md) — coordinator entry point, current project focus, and BeautyQ route truth.
* [Domain authoring principles](search/DOMAIN_AUTHORING_PRINCIPLES.md) — normative contract for
  domain-facing declarations, framework derivation, cross-domain reuse, and executable source-of-truth
  ownership, including the enum-text rule.
* [BeautyQ Search Gen2 implementation plan](gen2/BEAUTYQ_SEARCH_GEN2_IMPLEMENTATION_PLAN.md) — current plan and the single authoritative live implementation state.
* `BEAUTYQ_QDRANT_SUPPLEMENT_LOCAL_GATE.md` — locked BeautyQ local/test Qdrant supplement gate.
* `SEARCH_SUPPLEMENT_ARCHITECTURE.md` — reusable baseline-plus-supplement architecture.
* `search/NEW_DOMAIN_ONBOARDING.md` — new-domain onboarding owner.
* `DISTAGE_HTTP_TESTING.md` — focused local route/service/real-resource testing model.

## BeautyQ Search Gen2

* [Implementation plan](gen2/BEAUTYQ_SEARCH_GEN2_IMPLEMENTATION_PLAN.md) — active task owner: Brick 0–9 plan,
  current brick, open reusable-framework gaps, blockers, and next action. Its `Current implementation
  state` section is authoritative; summaries elsewhere must point here rather than becoming
  independent status owners.
* [Technical specification](gen2/BEAUTYQ_SEARCH_GEN2_TECHNICAL_SPEC.md) — accepted side-by-side architecture, module DAG,
  exact generic APIs and supported technical shapes, backend roles, lifecycle, fingerprinting, and cutover requirements.
* [Semantics ADR](gen2/BEAUTYQ_SEARCH_GEN2_SEMANTICS_ADR.md) — accepted search semantics and policy decisions.
* [Gen1 architecture review](gen2/BEAUTYQ_SEARCH_GEN2_REVIEW.md) — evidence-backed review and the gaps addressed by Gen2.
* [Gen2 promise audit](gen2/BEAUTYQ_SEARCH_GEN2_PROMISE_AUDIT.md) — the cross-cutting checkpoint between
  Bricks 5C and 5D: Gen1→Gen2 evidence matrix, ownership map, invariant ledger, deviations and required
  decisions. Live sequencing remains owned by the implementation plan.

## BeautyQ search architecture

* `beautyq-search-dsl-v1.md` — current BeautyQ search implementation notes: layer map,
  module ownership, repo graph loading, schema-owned document projection, SearchDocumentSpec /
  SearchField handles, intent vocabulary, SearchRuntimeSpec / fingerprint, generic ES/Qdrant
  interpreters, BeautyQ app-side adapters, and testing standard. This is the current architecture
  and module ownership owner; see the new-domain onboarding and coordinator workflow entries above
  for their respective owners.

## Coordinator workflow

* `local/COORDINATOR_WORKFLOW_AND_PROMPTING.md`

## Removed historical layers

Old review/report layers, activation runbooks, platform reference dumps, macOS metadata, the
historical BeautyQ module-split phase log, the closed catalog-derivation handoff, and the standalone
future-domain supplement gate template were removed. Current catalog/materialization invariants were
retained in new-domain onboarding; use the current docs above instead of looking for removed history.
