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
* owns baseline response components such as facets, filters, inferred filters, and grouped/carousel sections
* owns default route behavior
* owns hard constraints resolved through app query schema; in BeautyQ, natural-language budget
  expressions (`under 3k`, `under 3000`, `below 3000`, `up to 3000`, `маникюр under 50`) are
  resolved by the BeautyQ query schema into hard filter constraints and applied by Elasticsearch as
  range filters. The supplement/Qdrant path never owns hard constraints. Domain onboarding should
  include constraint/budget examples in the eval query set.

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

* ES baseline; Qdrant supplement; `ExplicitConstraintsFilterPlusTop1`
* Baseline and supplement inputs are shaped by schema/runtime metadata: `SearchRuntimeSpec`,
  `SearchDocumentSpec`, and `SearchDocumentPayloadSpec` owned by `BeautyQVariantSearchDocumentSchema`.
* Local managed launcher selects the supplement route with `./launcher -u scene:managed :leaderboard`.
* Local managed startup prepares the BeautyQ data the route reads (SQL/Postgres seed, Elasticsearch
  baseline index, Qdrant supplement collection/vectors) before serving — no user-facing activation env
  flag, and without any by-hand Qdrant collection-creation or indexing step.
* Repeated local starts still run embedding preflight, then skip ES/Qdrant rebuild/indexing only when the
  managed bootstrap fingerprint matches and the ES/Qdrant resources are present and compatible; changed
  inputs or missing/incompatible resources force rebuild or fail before bind.
* 4 queries, 1 improved, 3 unchanged, 0 worsened.
* Details live in `docs/BEAUTYQ_QDRANT_SUPPLEMENT_LOCAL_GATE.md`.

## Non-goals

* no Qdrant-only search
* no fallback
* no score fusion/rerank
* no production startup indexing (local managed startup may prepare local ES/Qdrant data only)
* no production collection lifecycle
* no production route/default behavior change from frontend provenance alone
* no benchmark output as automatic rollout signal

Qdrant does not replace ES. The supplement path has no fallback, no score fusion, and no rerank; the locked measured gate remains the acceptance source.

## Failure policy

* if no improvement, stop
* if any baseline regression, stop or require explicit user-approved budget before implementation
* if only docs/reports grow, stop
