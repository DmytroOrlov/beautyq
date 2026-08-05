[![Build Status](https://github.com/7mind/distage-example/workflows/Build/badge.svg)](https://github.com/7mind/distage-example/actions/workflows/build.yml)
[![License](https://img.shields.io/github/license/7mind/distage-example.svg)](https://github.com/7mind/distage-example/blob/develop/LICENSE)

# distage example

This repository currently includes BeautyQ search work inside the broader distage example project.

Start here:

* `AGENTS.md` for agent rules in the repository
* [Domain authoring principles](docs/search/DOMAIN_AUTHORING_PRINCIPLES.md) — repository-wide domain/search ownership principles
* [NEW_DOMAIN_ONBOARDING.md](docs/search/NEW_DOMAIN_ONBOARDING.md) — practical domain authoring, lifecycle composition, proof selection, and focused validation
* [BeautyQ Search Gen2 technical specification](docs/gen2/BEAUTYQ_SEARCH_GEN2_TECHNICAL_SPEC.md) — current implemented Gen2 architecture, runtime ownership, supported shapes, accepted limits, verification ownership, and delivery closure
* [BeautyQ Search Gen2 post-cutover plan](docs/gen2/BEAUTYQ_SEARCH_GEN2_POST_CUTOVER_PLAN.md) — remaining approved delivery milestones
* [BeautyQ Search Gen2 operations runbook](docs/gen2/BEAUTYQ_SEARCH_GEN2_OPERATIONS.md) — Required/Preferred/Disabled launcher modes, operator status, response warnings, restart-only recovery, and partial-activation procedures
* `docs/local/COORDINATOR_WORKFLOW_AND_PROMPTING.md` — coordinator workflow and anti-scope-drift rules

**Historical material:** [BeautyQ Search Gen2 architecture review](docs/gen2/BEAUTYQ_SEARCH_GEN2_REVIEW.md) — historical Gen1 evidence and architectural motivation. This is not current implementation guidance.

BeautyQ Search Gen2 is the native serving architecture. Its current architecture and invariants are documented in the technical specification; operator procedures are documented in the operations runbook; remaining delivery work is owned only by the post-cutover plan.

For local startup, status inspection and request probes, use the BeautyQ Search Gen2 operations runbook.

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
