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

New contributors and new domains should be aware of the following layers (full detail in
`docs/beautyq-search-dsl-v1.md`):

* **Repo graph / model-first loading** — repo entity metadata derives from Scala case-class models
  through Mirror-derived metadata; repo field metadata is selector-derived through typed `RepoField`
  handles; catalog graph is declared in a domain-specific repo graph class (`BeautyQRepoGraph` for
  BeautyQ).
* **Schema-owned document projection** — `SearchDocumentProjection` / domain schema class (e.g.
  `BeautyQVariantSearchDocumentSchema`) owns projection and `SearchDocumentSpec`; field handles live
  in the schema's `Fields` object.
* **SearchRuntimeSpec / fingerprint** — `SearchRuntimeSpec` is the generic runtime truth aggregating
  doc schema, query schema, request/facet/carousel config, payload specs, embedding/vector config, and
  runtime metadata. `SearchRuntimeFingerprint` derives from runtime schema/config and gates
  managed bootstrap reuse.
* **Generic ES / Qdrant modules** — `search-elasticsearch` and `search-qdrant` are reusable and
  contain no domain-specific logic. Generic interpreters consume `SearchDocumentSpec` /
  `SearchRuntimeSpec` / resolved constraints.
* **BeautyQ app-side adapters** — domain query schema resolution, intent vocabulary, ES adapter,
  Qdrant wrapper/backend, hybrid policy, response assembly, routes, and startup wiring belong in
  `bifunctor-tagless`. Generic modules must stay domain-free.

When starting a new domain: reuse the method and generic modules, not BeautyQ thresholds, query
text, or app-side adapter classes.

## Links

* `docs/beautyq-search-dsl-v1.md`
* `docs/SEARCH_SUPPLEMENT_ARCHITECTURE.md`
* `docs/SEARCH_SUPPLEMENT_FUTURE_DOMAIN_GATE_TEMPLATE.md`
* `docs/BEAUTYQ_QDRANT_SUPPLEMENT_LOCAL_GATE.md`
