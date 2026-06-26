# BeautyQ Qdrant supplement local gate

## Current status

* QP25 enables test/local `/beauty-search` provenance.
* QP25b makes the local managed launcher select the Qdrant supplement route by default when local resources are available.
* Commit: `805edaa48724e16a36dca2ab8955f11c52cef3b2`.
* This is local/test acceptance evidence. It is not approval to switch production/default behavior.
* Not production rollout.
* ES remains primary/default.
* Qdrant remains constrained supplement only.

## What exists

* Internal activation selector exists for ES-only rollback, supplement not-ready, and supplement ready debug probes.
* Readiness/preflight exists and fail-closes on invalid or mismatched ready selection.
* Local route smoke exists for the selected supplement states.
* Measured gate exists and locks the current accepted local/test outcome.
* QP24 stabilized route/service comparison by checking ids, order, and ES-owned components instead of raw floating scores from independent real-resource calls.
* QP25 adds frontend-visible execution and provenance fields to the successful route response.

## Local launcher

Run the local managed launcher:

```bash
./launcher -u scene:managed :leaderboard
```

The launcher HTTP server binds to source-confirmed port `8080` in `leaderboard.http.HttpServer`.

The local managed launcher path selects the ES baseline plus Qdrant supplement route directly. It still needs the normal local resources: Elasticsearch, Qdrant with a compatible indexed BeautyQ collection, and the embedding endpoint used by the Qdrant collection. The route does not create Qdrant collections, index Qdrant at startup, fall back, fuse scores, or rerank. In short: no fallback, no fusion, no rerank.

Qdrant append probe:

```bash
curl -sS -X POST 'http://localhost:8080/beauty-search' \
  -H 'Content-Type: application/json' \
  -d '{"query":"beauty near Wandsbek Markt","limit":10}' \
| jq '{executionMode, qdrantSupplement, qdrantVariants: [.variantCarousel[] | select(.resultOrigin == "qdrant_supplement") | {variantId, resultOrigin}]}'
```

Expected:

* `executionMode` is `es_plus_qdrant_supplement`
* `qdrantSupplement.status` is `used_with_append`
* `qdrantSupplement.contribution` is `qdrant_only_variant_append`
* `qdrantSupplement.appendedVariantIds` has exactly one id
* `qdrantVariants` has exactly one item

Qdrant used with no append:

```bash
curl -sS -X POST 'http://localhost:8080/beauty-search' \
  -H 'Content-Type: application/json' \
  -d '{"query":"маникюр","limit":10}' \
| jq '{executionMode, qdrantSupplement, origins: ([.variantCarousel[].resultOrigin] | unique)}'
```

Expected:

* `executionMode` is `es_plus_qdrant_supplement`
* `qdrantSupplement.status` is `used_no_append`
* `qdrantSupplement.contribution` is `none`
* `qdrantSupplement.appendedVariantIds` is empty
* `origins` is only `["es_baseline"]`

## Frontend provenance contract

Response-level execution modes:

* `es_only`
* `es_plus_qdrant_supplement`

Response-level Qdrant supplement statuses:

* `not_used`
* `used_no_append`
* `used_with_append`

Per-variant origins:

* `es_baseline`
* `qdrant_supplement`

`qdrantSupplement.appendedVariantIds` contains only Qdrant-only variants actually appended to the returned list. `qdrantSupplement.contribution` is `qdrant_only_variant_append` only when that append happened; duplicate-dropped Qdrant candidates are not frontend-visible contribution.

Frontend should treat these fields as explanation, debug, and status metadata. They are not a new ranking contract, and they do not expose raw Qdrant score as an improvement explanation.

## Locked measured gate

Command:

```bash
sbt 'bifunctor-tagless/Test/compile' 'bifunctor-tagless/testOnly leaderboard.search.QP19QdrantSupplementMeasuredAcceptanceGateSpec'
```

Expected counts:

* `testedQueries=4`
* `improvedQueries=1`
* `unchangedQueries=3`
* `worsenedQueries=0`
* `totalQdrantOnlyAppends=1`
* `duplicateEsIds=0`
* `lostEsIds=0`
* `prefixOrderRegressions=0`
* `esOwnedComponentChanges=0`
* `appendBudgetViolations=0`

## Query set

* `q_broad_006_ready_append_probe` — `beauty near Wandsbek Markt`
* `manicure_real_route_probe` — `маникюр`
* `q_broad_001_widened_probe` — `салон красоты wandsbek ногти`
* `q_broad_003_widened_probe` — `что-то для лица рядом`

Do not add arbitrary query text just to increase N. Widen only from source-confirmed inventory.

## Internal rollback/debug values

The local managed launcher does not require an activation value for normal supplement behavior. Internal rollback/debug probes can still select ES-only rollback or not-ready states.

Rules:

* Invalid value fails closed.
* Not-ready returns `503` with no fallback.
* Rollback/debug selection is not the user-facing local launcher path.

## Preflight and smoke commands

* QP11 preflight:
  `sbt 'bifunctor-tagless/Test/compile' 'bifunctor-tagless/testOnly leaderboard.search.QP11QdrantSupplementRealResourcePreflightSpec'`
* QP18 local proof:
  `sbt 'bifunctor-tagless/Test/compile' 'bifunctor-tagless/testOnly leaderboard.search.QP18QdrantSupplementImprovementNoWorseningSpec'`
* QP19 measured gate:
  `sbt 'bifunctor-tagless/Test/compile' 'bifunctor-tagless/testOnly leaderboard.search.QP19QdrantSupplementMeasuredAcceptanceGateSpec'`

## Failure meanings

* `QP19_NO_IMPROVEMENT_SIGNAL`: no source-confirmed query improved.
* `QP19_WORSENING_DETECTED`: at least one tested query worsened.
* `QP19_LOST_ES_IDS_DETECTED`: ES baseline id disappeared.
* `QP19_DUPLICATE_ES_IDS_DETECTED`: duplicate ES id appeared.
* `QP19_PREFIX_ORDER_REGRESSION_DETECTED`: ES prefix/order changed.
* `QP19_ES_OWNED_COMPONENT_CHANGED`: provider/service/facet/filter/inferred component changed.
* `QP19_APPEND_BUDGET_VIOLATION_DETECTED`: more than one Qdrant-only append.
* `QP23_WIDENED_BASELINE_DRIFT`: measured counts no longer match locked baseline.
* `QP18_ROUTE_SERVICE_CONTRACT_MISMATCH`: HTTP route and direct service disagree on ids/order/components.

Any failure means do not claim the local/test supplement proof is green.

## Non-goals

* no production route switch
* no Qdrant-as-default
* no fallback
* no score fusion/rerank
* no traffic shadowing/mirroring
* no startup indexing
* no production collection lifecycle
* no benchmark output as automatic rollout signal

## Reuse for next domain

* `docs/SEARCH_SUPPLEMENT_FUTURE_DOMAIN_GATE_TEMPLATE.md`

Future domains should start from the measured gate method, not from BeautyQ commit history.
