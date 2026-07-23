# Documentation index

## Start here

* [Repository README](../README.md) — coordinator entry point, current project focus, and BeautyQ route truth.
* [Domain authoring principles](search/DOMAIN_AUTHORING_PRINCIPLES.md) — normative contract for
  domain-facing declarations, framework derivation, cross-domain reuse, and executable source-of-truth
  ownership, including the enum-text rule.
* `search/NEW_DOMAIN_ONBOARDING.md` — new-domain onboarding owner.
* `DISTAGE_HTTP_TESTING.md` — focused local route/service/real-resource testing model.

## BeautyQ Search Gen2

* [Technical specification](gen2/BEAUTYQ_SEARCH_GEN2_TECHNICAL_SPEC.md) — current implemented Gen2 architecture, module DAG,
  exact generic APIs and supported technical shapes, backend roles, lifecycle, fingerprinting, cutover rules,
  accepted initial limits, and completed delivery status.
* [Semantics ADR](gen2/BEAUTYQ_SEARCH_GEN2_SEMANTICS_ADR.md) — accepted search semantics and policy decisions.
* [Gen1 architecture review](gen2/BEAUTYQ_SEARCH_GEN2_REVIEW.md) — historical Gen1 evidence and the gaps addressed by Gen2.

## BeautyQ search architecture

* [Gen2 technical specification](gen2/BEAUTYQ_SEARCH_GEN2_TECHNICAL_SPEC.md) — current implemented Gen2 architecture: layer map,
  module ownership, repo graph loading, schema-owned document projection, search-field handles,
  intent vocabulary, runtime spec / fingerprint, generic ES/Qdrant
  interpreters, BeautyQ app-side adapters, and testing standard. This owns the current architecture
  and module ownership; see the new-domain onboarding and coordinator workflow entries above
  for their respective owners.

## Coordinator workflow

* `local/COORDINATOR_WORKFLOW_AND_PROMPTING.md`

## Removed historical layers

Old review/report layers, activation runbooks, platform reference dumps, macOS metadata, the
historical BeautyQ module-split phase log, the closed catalog-derivation handoff, and the standalone
future-domain supplement gate template were removed. Current catalog/materialization invariants were
retained in new-domain onboarding; use the current docs above instead of looking for removed history.
