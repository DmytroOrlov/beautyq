# Search domain onboarding

## Purpose

Use this before building a new domain supplement. The goal is to avoid a long BeautyQ-style chain.

## Three-step method

```text
1. Baseline map
2. Measured local gate
3. Lock or stop
```

## Step 1: Baseline map

Require:

* baseline backend
* response id model
* baseline-owned components
* supplement candidate source
* source-confirmed query inventory
* one expected improvement query
* one baseline-preservation query

## Step 2: Measured local gate

Require metrics:

* `testedQueries`
* `improvedQueries`
* `unchangedQueries`
* `worsenedQueries`
* `totalSupplementOnlyAppends`
* `duplicateBaselineIds`
* `lostBaselineIds`
* `prefixOrderRegressions`
* `baselineOwnedComponentChanges`
* `appendBudgetViolations`

Pass conditions:

* `testedQueries >= 2`
* `improvedQueries >= 1`
* `worsenedQueries == 0`
* `lostBaselineIds == 0`
* `duplicateBaselineIds == 0`
* `prefixOrderRegressions == 0`
* `baselineOwnedComponentChanges == 0`
* `appendBudgetViolations == 0`

## Step 3: Lock or stop

* if green: lock exact query set, counts, failure markers
* if no improvement: stop
* if regression: stop or ask user for explicit budget before implementation
* if source inventory insufficient: report source-incomplete, do not invent queries

## Anti-patterns

Bad:
`Let's wire Qdrant into the route and then see if it helps.`

Good:
`First prove local measured gate: N queries, >=1 improved, 0 worsened.`

Bad:
`Almost no worsening.`

Good:
`worsenedQueries=0`, or explicit user-approved budget such as `worsenedQueries<=1` with named query/field.

Bad:
`Use BeautyQ thresholds in another domain.`

Good:
`Reuse the method, not BeautyQ thresholds or query text.`

## Current reusable architecture

New domains should use the measured-gate method from this doc. New domain architecture and module
boundaries should follow `docs/search/BEAUTYQ_SEARCH_CONTRACT_MODULE_SPLIT_PLAN.md` — it defines
the target search-contract/module split, dependency DAG, and forbidden dependencies.

Do not start a new domain by copying `BeautyQRepoGraph` or `bifunctor-tagless` app-side ownership.
`BeautyQRepoGraph` and `BeautyQCatalogGraph` are legacy/current BeautyQ implementation surfaces
(full detail in `docs/beautyq-search-dsl-v1.md`), not the target pattern for a new domain.

Generic, reusable layers that a new domain may build on:

* **Generic ES / Qdrant modules** — `search-elasticsearch` and `search-qdrant` are reusable and
  contain no domain-specific logic. Generic interpreters consume `SearchDocumentSpec` /
  `SearchRuntimeSpec` / resolved constraints.
* **Generic search-core primitives** — `SearchRuntimeSpec` aggregation and fingerprinting, generic
  field/document spec types.

When starting a new domain: reuse the method and generic modules, not BeautyQ thresholds, query
text, `BeautyQRepoGraph`/`BeautyQCatalogGraph` ownership, or app-side adapter classes.

## Links

* `docs/search/BEAUTYQ_SEARCH_CONTRACT_MODULE_SPLIT_PLAN.md` — target module split and boundaries for new domains
* `docs/beautyq-search-dsl-v1.md` — current/legacy BeautyQ implementation notes
* `docs/SEARCH_SUPPLEMENT_ARCHITECTURE.md`
* `docs/SEARCH_SUPPLEMENT_FUTURE_DOMAIN_GATE_TEMPLATE.md`
* `docs/BEAUTYQ_QDRANT_SUPPLEMENT_LOCAL_GATE.md`
