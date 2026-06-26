# BeautyQ Qdrant supplement local gate

## Current status

* Stopped at QP24.
* Commit: `805edaa48724e16a36dca2ab8955f11c52cef3b2`.
* This is local/test acceptance evidence. It is not approval to switch the default `/beauty-search` route.
* Not production rollout.
* ES remains primary/default.
* Qdrant remains constrained supplement only.

## What exists

* Activation selector exists for ES-only rollback, supplement not-ready, and supplement ready.
* Readiness/preflight exists and fail-closes on invalid or mismatched ready selection.
* Local route smoke exists for the selected supplement states.
* Measured gate exists and locks the current accepted local/test outcome.
* QP24 stabilized route/service comparison by checking ids, order, and ES-owned components instead of raw floating scores from independent real-resource calls.

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

## Activation values

* `BEAUTYQ_QDRANT_SUPPLEMENT_ACTIVATION=es-only-rollback`
* `BEAUTYQ_QDRANT_SUPPLEMENT_ACTIVATION=qdrant-supplement-not-ready`
* `BEAUTYQ_QDRANT_SUPPLEMENT_ACTIVATION=qdrant-supplement-ready`

Rules:

* Absent/default means ES-only rollback.
* Invalid value fails closed.
* Not-ready returns `503` with no fallback.
* Ready must not be selected unless preflight and measured gate are green.

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

Any failure means do not select `qdrant-supplement-ready` and do not switch the default route.

## Non-goals

* no production/default route switch
* no Qdrant-as-default
* no fallback
* no score fusion/rerank
* no traffic shadowing/mirroring
* no startup indexing
* no production collection lifecycle
* no route JSON/API change
* no benchmark output as automatic rollout signal

## Reuse for next domain

* `docs/SEARCH_SUPPLEMENT_FUTURE_DOMAIN_GATE_TEMPLATE.md`

Future domains should start from the measured gate method, not from BeautyQ commit history.
