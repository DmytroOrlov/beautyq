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

* `POST /beauty-search` remains ES-backed by default.
* Qdrant remains a constrained supplement only.
* There is no production/default route switch, fallback, fusion, or rerank.

The rest of the repository remains the upstream distage example with multiple implementation variants under `bifunctor-tagless`, `monofunctor-tagless`, and `monomorphic-cats`.
