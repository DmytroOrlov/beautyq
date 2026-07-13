[![Build Status](https://github.com/7mind/distage-example/workflows/Build/badge.svg)](https://github.com/7mind/distage-example/actions/workflows/build.yml)
[![License](https://img.shields.io/github/license/7mind/distage-example.svg)](https://github.com/7mind/distage-example/blob/develop/LICENSE)

# distage example

This repository currently includes BeautyQ search work inside the broader distage example project.

Start here:

* `AGENTS.md` for agent rules in the repository
* [Documentation map](docs/README.md)
* [Domain authoring principles](docs/search/DOMAIN_AUTHORING_PRINCIPLES.md) for the normative
  business-policy, reuse, and executable-source-of-truth contract
* [BeautyQ Search Gen2 implementation plan](docs/gen2/BEAUTYQ_SEARCH_GEN2_IMPLEMENTATION_PLAN.md) for the current plan and authoritative live implementation state
* `docs/BEAUTYQ_QDRANT_SUPPLEMENT_LOCAL_GATE.md` for the locked BeautyQ local/test Qdrant supplement gate
* `docs/SEARCH_SUPPLEMENT_ARCHITECTURE.md` for the reusable baseline-plus-supplement architecture
* `docs/local/COORDINATOR_WORKFLOW_AND_PROMPTING.md` for coordinator workflow and anti-scope-drift rules

Current coordinator focus:

BeautyQ Search Framework Gen2 is the active project. Gen2 will be built side by side in a separate
module DAG, followed by one final cutover and removal of Gen1 search modules.

Current search-framework work is tracked in the
[BeautyQ Search Gen2 implementation plan](docs/gen2/BEAUTYQ_SEARCH_GEN2_IMPLEMENTATION_PLAN.md).

The new-domain onboarding document remains the practical authoring guide; the domain-authoring
principles document owns the repository-wide contract, and the Gen2 implementation plan owns current
delivery status.

The route/Qdrant sections below are current operational truth.

Longer-term direction: a reusable domain-agnostic search contract/materialization DSL that can
onboard a new domain with explicit topology, documented metrics, and transparent ES/Qdrant
configuration guidance.

Current BeautyQ route truth:

* Production / non-managed default `POST /beauty-search` remains ES-backed.
* Local managed launcher `POST /beauty-search` is ES-backed with the constrained Qdrant supplement.
* Qdrant supplement remains local/test constrained supplement only.
* Local/test provenance, measured gates, and benchmark reports are local/test evidence only; they do not approve a production/default route switch.
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
`leaderboard-app-shell/src/main/resources/common-reference.conf` (default base URL
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

## Eval and measurement guardrails

BeautyQ eval query changes are not simple JSON-only edits. Accepted query additions must update
the known eval/count-lock chain together and must not tune ES/Qdrant, parser, vocabulary, seed data,
routes, or production search behavior unless that is the explicit objective.

Dirty catalog profiles in runtime scorecard tests are measurement-only. They may show worse coverage
without failing the test and do not by themselves justify production Qdrant activation, fallback,
fusion, rerank, or ES/Qdrant tuning.

## Module map

This is the current physical module map. See `docs/beautyq-search-dsl-v1.md` for detailed BeautyQ
search ownership, `docs/local/COORDINATOR_WORKFLOW_AND_PROMPTING.md` for coordinator/source-truth
workflow, and the boundary guardrail specs for enforced import/build-DAG constraints.

| Module | Responsibility |
|---|---|
| `leaderboard-core` | generic failure types (`QueryFailure` and siblings) |
| `search-core` | generic search framework: fields, document spec, runtime spec, fingerprinting, document JSON, generic semantic candidate assembly, generic semantic supplement policy |
| `search-contract-core` | generic search-contract ADTs (`SearchDomainSpec`, `SearchField`, `SearchRuntimeDeclaration`) |
| `repo-core` | generic repo/catalog graph-loading primitives, independent of BeautyQ |
| `beautyq-model` | BeautyQ domain model (attributes, service-variant schema, master-service-offer-variant, user profile) |
| `beautyq-search-contract` | pure BeautyQ search contract declarations (catalog/document/intent/runtime/response/evaluation slices, `BeautyQSearchDomainContract`); no repo/client/HTTP imports |
| `beautyq-search-repositories` | BeautyQ repository interfaces and their `Dummy`/`Postgres` implementations |
| `beautyq-search-materialization` | BeautyQ catalog/document materialization: catalog snapshots, snapshot loaders, and variant document projection |
| `search-elasticsearch` | reusable ES client/interpreter code (mapping, ingestion, request, response); no BeautyQ-specific logic |
| `search-qdrant` | reusable Qdrant client/interpreter/indexing/semantic-search/compatibility code; no BeautyQ-specific logic |
| `beautyq-search-wiring` | BeautyQ runtime/search/backend/routing/policy/eval-design/helper layer that consumes the contract - the largest BeautyQ-specific module |
| `app-services` | app-level service boundaries over repository interfaces, e.g. `leaderboard.services.Ranks` |
| `app-http` | HTTP/Tapir API layer for the whole app, including `BeautySearchApi` and every other API/endpoint class |
| `leaderboard-app-shell` | **app shell module**: config, Distage/module composition and plugin wiring, real clients/resources, and startup/bootstrap/seed/eval shell execution code. No longer the conceptual owner of the BeautyQ search contract, materialization, runtime/wiring, or HTTP layers. Previously named `bifunctor-tagless`. |

The repository also contains upstream distage example implementation variants under `monofunctor-tagless` and `monomorphic-cats`.
