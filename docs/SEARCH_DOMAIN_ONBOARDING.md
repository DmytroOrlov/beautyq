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

## Links

* `docs/SEARCH_SUPPLEMENT_ARCHITECTURE.md`
* `docs/SEARCH_SUPPLEMENT_FUTURE_DOMAIN_GATE_TEMPLATE.md`
* `docs/BEAUTYQ_QDRANT_SUPPLEMENT_LOCAL_GATE.md`
