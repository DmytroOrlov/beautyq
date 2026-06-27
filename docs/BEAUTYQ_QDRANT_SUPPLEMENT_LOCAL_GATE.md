# BeautyQ Qdrant supplement local gate

## Current status

* QP25 enables test/local `/beauty-search` provenance.
* QP25b makes the local managed launcher select the Qdrant supplement route by default when local resources are available.
* QP26 makes the local managed launcher prepare all local data the route needs at startup (SQL/Postgres seed, Elasticsearch baseline index, Qdrant supplement collection/vectors), with no user-facing activation env flag and without any by-hand Qdrant collection-creation or indexing step.
* QP31 keeps that local managed preparation self-starting while skipping unchanged ES/Qdrant rebuilds when the managed bootstrap fingerprint matches.
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

The local managed launcher path selects the ES baseline plus Qdrant supplement route directly and, on
startup, prepares all local data the route needs before the HTTP server serves `/beauty-search`:

* BeautyQ seed is loaded into SQL/Postgres (`BeautyQSeedReady`).
* the Elasticsearch baseline index is (re)created and indexed from the BeautyQ catalog;
* the Qdrant supplement collection
  (`beauty_variant_v1_local_llama_cpp_embedding_variant_embedding_1024_cosine`, vector
  `variant-embedding`, dimension `1024`, cosine) is (re)created and the BeautyQ variant vectors are
  upserted (`BeautyQManagedLocalSearchDataReady` / `BeautyQManagedLocalSearchBootstrap`).

This needs the dockerized Elasticsearch/Qdrant containers (started by the managed scene) and the local
embedding endpoint (default `http://localhost:8081`). No user-facing Qdrant activation env flag is
required, and operators never create or index the Qdrant collection by hand — startup does it
automatically.

The bootstrap is idempotent. It writes a local managed bootstrap fingerprint for the prepared data and,
on repeated starts, skips ES/Qdrant rebuild/indexing when the fingerprint still matches the
seed/catalog/search/vector/embedding inputs and live resource checks pass. The embedding preflight still
runs every startup before readiness. Changed seed/search/vector/embedding inputs, a missing fingerprint,
a missing ES index, a missing Qdrant collection, an incompatible Qdrant vector spec, or an insufficient
Qdrant point count causes the bootstrap to rebuild the local ES baseline and Qdrant collection/vectors,
or to fail fast before HTTP bind if the required resource cannot be prepared. The route does not fall
back, fuse scores, or rerank. In short: no fallback, no fusion, no rerank, and no production startup
indexing.

### Embedding endpoint is a hard startup prerequisite (fail-fast, no ES-only fallback)

The local embedding endpoint (default `http://localhost:8081`) is required: the Qdrant collection holds
embedding vectors, so the managed bootstrap runs a named embedding preflight as the earliest step on
every startup, before either rebuild or reuse readiness. The preflight calls the configured endpoint once
and proves it is reachable, returns a non-empty vector, and returns exactly dimension `1024`.

If the endpoint is unavailable, returns an empty embedding, or returns the wrong dimension, the managed
local startup **fails before binding `127.0.0.1:8080`** and never serves `/beauty-search`. `HttpServer`
depends on `BeautyQManagedLocalSearchDataReady`, so a failing preflight prevents the HTTP bind. The
diagnostic names the BeautyQ managed local search Qdrant bootstrap, the embedding endpoint URL, the
expected dimension `1024`, and the actual reason (connection failure, empty embedding, or wrong
dimension).

Startup does **not** silently degrade to ES-only: there is no ES-only fallback, no `executionMode=es_only`
fallback response, and no skipping of Qdrant indexing while claiming readiness. Either the full SQL + ES +
Qdrant data is prepared and the server binds, or startup fails with the diagnostic above.

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
* no production startup indexing (local managed startup prepares local ES/Qdrant data only)
* no production collection lifecycle
* no benchmark output as automatic rollout signal

## Reuse for next domain

* `docs/SEARCH_SUPPLEMENT_FUTURE_DOMAIN_GATE_TEMPLATE.md`

Future domains should start from the measured gate method, not from BeautyQ commit history.
