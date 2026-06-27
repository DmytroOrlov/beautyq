[![Build Status](https://github.com/7mind/distage-example/workflows/Build/badge.svg)](https://github.com/7mind/distage-example/actions/workflows/build.yml)
[![License](https://img.shields.io/github/license/7mind/distage-example.svg)](https://github.com/7mind/distage-example/blob/develop/LICENSE)

# distage example

This repository currently includes BeautyQ search work inside the broader distage example project.

Start here:

* `AGENTS.md` for repo rules
* `docs/README.md` for the docs map
* `docs/BEAUTYQ_CURRENT_STATE_AND_HANDOFF.md` for current BeautyQ route truth and stop-state
* `docs/BEAUTYQ_QDRANT_SUPPLEMENT_LOCAL_GATE.md` for the locked BeautyQ local/test Qdrant supplement gate
* `docs/SEARCH_SUPPLEMENT_ARCHITECTURE.md` for the reusable baseline-plus-supplement architecture

Current BeautyQ route truth:

* Local managed launcher `POST /beauty-search` is ES-backed with the constrained Qdrant supplement.
* Qdrant remains a constrained supplement only.
* There is no fallback, fusion, or rerank.

Run the local managed launcher:

```bash
./launcher -u scene:managed :leaderboard
```

Local managed startup prepares all local data `/beauty-search` needs before the HTTP server serves:
BeautyQ seed is loaded into SQL/Postgres, the Elasticsearch baseline index is built, and the Qdrant
supplement collection/vectors are indexed. Repeated starts skip the ES/Qdrant rebuild when the local
bootstrap fingerprint still matches the seed/search/vector/embedding inputs and the prepared resources
are present and compatible; changed inputs, a missing ES index, a missing Qdrant collection, or an
incompatible Qdrant vector spec forces rebuild or fails fast before bind. No user-facing Qdrant
activation env flag is required, and operators never create or index the Qdrant collection by hand —
startup does it automatically. The launcher HTTP server binds to source-confirmed port `8080`.

The local embedding endpoint is configured at `llama-cpp-embedding` in
`bifunctor-tagless/src/main/resources/common-reference.conf` (default base URL
`http://localhost:8081`, endpoint path `/v1/embeddings`; base URL override
`M18_QDRANT_EMBEDDING_ENDPOINT`). The managed launcher reads that value through Distage config, and
the Scala constructors do not carry runtime endpoint defaults. The endpoint is a hard startup
prerequisite: the managed bootstrap runs an embedding preflight on every startup before readiness. If
the endpoint is unavailable, returns an empty embedding, or returns the wrong vector dimension
(expected `1024`), startup fails before binding `127.0.0.1:8080` and never serves `/beauty-search`,
with a diagnostic naming the bootstrap, the endpoint, the expected dimension, and the actual reason.
Startup does not silently fall back to ES-only.

Qdrant append probe:

```bash
curl -sS -X POST 'http://localhost:8080/beauty-search' \
  -H 'Content-Type: application/json' \
  -d '{"query":"beauty near Wandsbek Markt","limit":10}' \
| jq '{executionMode, qdrantSupplement, qdrantVariants: [.variantCarousel[] | select(.resultOrigin == "qdrant_supplement") | {variantId, resultOrigin}]}'
```

Expected: `executionMode` is `es_plus_qdrant_supplement`, `qdrantSupplement.status` is `used_with_append`, `qdrantSupplement.contribution` is `qdrant_only_variant_append`, and exactly one returned variant has `resultOrigin` equal to `qdrant_supplement`.

Qdrant used with no append:

```bash
curl -sS -X POST 'http://localhost:8080/beauty-search' \
  -H 'Content-Type: application/json' \
  -d '{"query":"маникюр","limit":10}' \
| jq '{executionMode, qdrantSupplement, origins: ([.variantCarousel[].resultOrigin] | unique)}'
```

Expected: `executionMode` is `es_plus_qdrant_supplement`, `qdrantSupplement.status` is `used_no_append`, `qdrantSupplement.contribution` is `none`, `qdrantSupplement.appendedVariantIds` is empty, and `origins` is only `["es_baseline"]`.

The rest of the repository remains the upstream distage example with multiple implementation variants under `bifunctor-tagless`, `monofunctor-tagless`, and `monomorphic-cats`.
