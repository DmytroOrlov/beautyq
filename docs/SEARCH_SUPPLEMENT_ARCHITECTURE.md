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
→ response with baseline-owned components and supplement provenance
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

### Frontend provenance

Successful responses expose which path served the request and whether the supplement changed the returned variants:

* response execution modes: `es_only`, `es_plus_qdrant_supplement`
* Qdrant supplement statuses: `not_used`, `used_no_append`, `used_with_append`
* Qdrant contribution values: `none`, `qdrant_only_variant_append`
* per-variant origins: `es_baseline`, `qdrant_supplement`

Only appended Qdrant-only variants use `qdrant_supplement`. ES baseline variants remain `es_baseline`, including when a duplicate Qdrant candidate is dropped. Frontend code should treat provenance as explanation, debug, and status metadata, not as a ranking contract.

### Gate

* measured local query set
* at least one improvement
* zero baseline regressions unless explicit user-approved metrics budget exists

## BeautyQ concrete example

* ES baseline
* Qdrant supplement
* `ExplicitConstraintsFilterPlusTop1`
* local managed launcher selects the supplement route with `./launcher -u scene:managed :leaderboard`
* 4 queries, 1 improved, 3 unchanged, 0 worsened
* details live in `docs/BEAUTYQ_QDRANT_SUPPLEMENT_LOCAL_GATE.md`

## Non-goals

* no Qdrant-only search
* no fallback
* no score fusion/rerank
* no startup indexing
* no production collection lifecycle
* no production route/default behavior change from frontend provenance alone
* no benchmark output as automatic rollout signal

Qdrant does not replace ES. The supplement path has no fallback, no score fusion, and no rerank; the locked measured gate remains the acceptance source.

## Failure policy

* if no improvement, stop
* if any baseline regression, stop or require explicit user-approved budget before implementation
* if only docs/reports grow, stop
