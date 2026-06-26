# Search supplement architecture

## Purpose

Baseline search remains the owner of constraints, filters, facets, exact/range/geo logic, and response components.

Supplement search may add recall candidates only after a measured local proof. This pattern exists to improve broad or vague semantic recall without destabilizing baseline behavior.

## Architecture shape

```text
request
→ domain parser / search spec
→ baseline backend
→ baseline response
→ optional supplement candidate source
→ constrained append-only merge
→ response with baseline-owned components preserved
```

## Roles

### Baseline backend

* owns lexical precision
* owns hard constraints
* owns facets/provider/service/filter/inferred components
* owns default route behavior

### Supplement backend

* candidate source only
* may append at most one candidate by default
* must not remove/reorder baseline ids
* must not own default route behavior

### Gate

* measured local query set
* at least one improvement
* zero baseline regressions unless explicit user-approved metrics budget exists

## BeautyQ concrete example

* ES baseline
* Qdrant supplement
* `ExplicitConstraintsFilterPlusTop1`
* 4 queries, 1 improved, 3 unchanged, 0 worsened
* details live in `docs/BEAUTYQ_QDRANT_SUPPLEMENT_LOCAL_GATE.md`

## Non-goals

* no supplement-as-default
* no fallback
* no score fusion/rerank
* no startup indexing
* no production collection lifecycle
* no route JSON/API change
* no benchmark output as automatic rollout signal

## Failure policy

* if no improvement, stop
* if any baseline regression, stop or require explicit user-approved budget before implementation
* if only docs/reports grow, stop
