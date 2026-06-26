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

* Local managed launcher `POST /beauty-search` is ES-backed with the constrained Qdrant supplement when local resources are available.
* Qdrant remains a constrained supplement only.
* There is no fallback, fusion, or rerank.

Run the local managed launcher:

```bash
./launcher -u scene:managed :leaderboard
```

The launcher HTTP server binds to source-confirmed port `8080`.

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
