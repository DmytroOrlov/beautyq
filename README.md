[![Build Status](https://github.com/7mind/distage-example/workflows/Build/badge.svg)](https://github.com/7mind/distage-example/actions/workflows/build.yml)
[![License](https://img.shields.io/github/license/7mind/distage-example.svg)](https://github.com/7mind/distage-example/blob/develop/LICENSE)

# distage example

This repository currently includes BeautyQ search work inside the broader distage example project.

Start here:

* `AGENTS.md` for agent rules in the repository
* [Domain authoring principles](docs/search/DOMAIN_AUTHORING_PRINCIPLES.md) — repository-wide domain/search ownership principles
* [NEW_DOMAIN_ONBOARDING.md](docs/search/NEW_DOMAIN_ONBOARDING.md) — practical domain authoring, lifecycle composition, proof selection, and focused validation
* [BeautyQ Search Gen2 technical specification](docs/gen2/BEAUTYQ_SEARCH_GEN2_TECHNICAL_SPEC.md) — current implemented Gen2 architecture, runtime ownership, supported shapes, accepted limits, verification ownership, and delivery closure
* [BeautyQ Search Gen2 post-cutover plan](docs/gen2/BEAUTYQ_SEARCH_GEN2_POST_CUTOVER_PLAN.md) — approved evaluation, restart-only operations, request-budget, Qdrant-batching, and eval-first second-domain work that is not implemented yet
* [BeautyQ Search Gen2 architecture review](docs/gen2/BEAUTYQ_SEARCH_GEN2_REVIEW.md) — historical Gen1 evidence and architectural motivation
* `docs/local/COORDINATOR_WORKFLOW_AND_PROMPTING.md` — coordinator workflow and anti-scope-drift rules

BeautyQ Search Gen2 is the complete native serving architecture. Its declaration, baseline/supplement,
lifecycle-authorization, and route ownership are frozen; the bounded post-cutover quality and
operational work above is approved but not yet implemented.

The route/Qdrant sections below are current operational truth.

Current BeautyQ route truth:

* The default search route is native Gen2 `POST /beauty-search` with the full Elasticsearch + Qdrant + embedding graph.
* `/beauty-search-gen2` and the Gen1 request/response route are absent.
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
incompatible Qdrant vector spec forces rebuild or fails fast before bind. The current source has no
operator-owned supplement-startup mode and may automatically publish typed `baseline_only` readiness for
transport/unavailability; the approved post-cutover correction makes full supplement readiness
required by default and permits baseline-only startup only through explicit `preferred` or `disabled`
configuration. Operators
never create or index the Qdrant collection by hand — startup does it automatically. The launcher HTTP
server binds to source-confirmed port `8080`.

The Gen2 snapshot materialization branch depends on `BeautyQSeedReady`, an explicit Distage lifecycle
resource that proves all seven repository tables (including `master_service_offer_variant`) exist
and seed data is loaded. Distage cannot infer SQL table dependencies from query text; this DI edge
ensures the snapshot source never queries a missing table on first start. The HTTP route stays
unavailable until both seed readiness and Gen2 activation succeed.

The local embedding endpoint is configured at `llama-cpp-embedding` in
`leaderboard-app-shell/src/main/resources/common-reference.conf` (default base URL
`http://localhost:8081`, endpoint path `/v1/embeddings`; base URL override
`M18_QDRANT_EMBEDDING_ENDPOINT`). The managed launcher reads that value through Distage config, and
the Scala constructors do not carry runtime endpoint defaults. The managed bootstrap exercises the
embedding endpoint before full-search readiness. Malformed output, model mismatch, an empty embedding,
or the wrong vector dimension (expected `1024`) is a hard failure. Current transport/unavailability
handling may automatically publish typed `baseline_only`; it is not a hidden Gen1 fallback. The approved
post-cutover contract will fail startup by default, allow this mode only through explicit
`preferred` degradation or an intentional `disabled` kill switch, expose startup state through an
operator status/log and safe response warnings, keep request-time supplement failures separate, and
require restart before returning to full search. The disabled branch will not construct, probe or
activate Qdrant/embedding resources; it still requires the complete Elasticsearch baseline.

Gen2 append probe:

```bash
curl -sS -X POST 'http://localhost:8080/beauty-search' \
  -H 'Content-Type: application/json' \
  -d '{"query":"beauty near Wandsbek Markt","filters":[],"requestedFacets":[],"sort":[],"page":{"size":20}}' \
| jq '{hits: (.hits | length), supplementStatus, supplementStatusCode, supplementCount, origins: ([.hits[].origin] | unique)}'
```

Expected: `supplementStatus` is `supplemented`, `supplementCount` is at least 1, and one hit origin is `qdrant_supplement`.

Gen2 used with no append:

```bash
curl -sS -X POST 'http://localhost:8080/beauty-search' \
  -H 'Content-Type: application/json' \
  -d '{"query":"маникюр","filters":[],"requestedFacets":[],"sort":[],"page":{"size":20}}' \
| jq '{hits: (.hits | length), supplementStatus, supplementStatusCode, supplementCount, origins: ([.hits[].origin] | unique)}'
```

Expected: `supplementStatus` is `no_append`, `supplementCount` is 0, and origins is only `["es_baseline"]`.

## Eval and measurement guardrails

BeautyQ eval query changes are not simple JSON-only edits. Accepted query additions must update
the known eval/count-lock chain together and must not tune ES/Qdrant, parser, vocabulary, seed data,
routes, or production search behavior unless that is the explicit objective.

Dirty catalog profiles in runtime scorecard tests are measurement-only. They may show worse coverage
without failing the test and do not by themselves justify production Qdrant activation, fallback,
fusion, rerank, or ES/Qdrant tuning.

## Module map

This is the current physical module map. See the [Gen2 technical specification](docs/gen2/BEAUTYQ_SEARCH_GEN2_TECHNICAL_SPEC.md) for detailed current architecture, [NEW_DOMAIN_ONBOARDING.md](docs/search/NEW_DOMAIN_ONBOARDING.md) for practical authoring guidance, `docs/local/COORDINATOR_WORKFLOW_AND_PROMPTING.md` for coordinator/source-truth workflow, and the boundary guardrail specs for enforced import/build-DAG constraints.

| Module | Responsibility |
|---|---|
| `leaderboard-core` | generic failure types (`QueryFailure` and siblings) |
| `search-gen2-contract` | generic executable Gen2 search fields, facets, requests, plans, constraints, provenance, cursor, and suppression types |
| `search-gen2-core` | generic search framework: field/document spec, runtime spec, fingerprinting, document JSON, generic semantic candidate assembly |
| `repo-core` | generic repo/catalog graph-loading primitives, independent of BeautyQ |
| `beautyq-model` | BeautyQ domain model (attributes, service-variant schema, master-service-offer-variant, user profile) |
| `beautyq-search-gen2-contract` | pure BeautyQ Gen2 search contract declarations (catalog/document/intent/runtime/response/evaluation slices); no repo/client/HTTP imports |
| `beautyq-search-repositories` | BeautyQ repository interfaces and their `Dummy`/`Postgres` implementations |
| `beautyq-search-gen2-materialization` | BeautyQ Gen2 catalog/document materialization: consistent snapshots, snapshot loaders, and variant document projection |
| `search-gen2-elasticsearch` | reusable ES client/compiler code (mapping, ingestion, request, response); no BeautyQ-specific logic |
| `search-gen2-qdrant` | reusable Qdrant client/candidate compiler/indexing/semantic-search code; no BeautyQ-specific logic |
| `search-gen2-eval` | domain-neutral evaluation IDs, ranking mathematics, aggregation, comparison and deterministic report/manifest codecs |
| `beautyq-search-gen2-wiring` | BeautyQ Gen2 runtime/search/backend/routing/policy layer that consumes the Gen2 contract |
| `beautyq-search-gen2-eval` | BeautyQ Gen2 evaluation, cutover gate, deletion inventory, canonical BeautyQ corpus, domain policy and adapters; test-only dependency of app-shell |
| `app-services` | app-level service boundaries over repository interfaces, e.g. `leaderboard.services.Ranks` |
| `app-http` | HTTP/Tapir API layer for the whole app, including `BeautySearchGen2Api` and every other API/endpoint class |
| `leaderboard-app-shell` | **app shell module**: config, Distage/module composition and plugin wiring, real clients/resources, and startup/bootstrap/seed/eval shell execution code. No longer the conceptual owner of the BeautyQ search contract, materialization, runtime/wiring, or HTTP layers. |

The repository also contains upstream distage example implementation variants under `monofunctor-tagless` and `monomorphic-cats`.
