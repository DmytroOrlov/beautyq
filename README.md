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

BeautyQ Search Gen2 is the native serving architecture. Its current architecture and invariants are documented in the technical specification; operator procedures are documented in the operations runbook; remaining delivery work is owned only by the post-cutover plan.

For local startup, status inspection and request probes, use the BeautyQ Search Gen2 operations runbook.

## Eval and measurement guardrails

BeautyQ eval query changes are not simple JSON-only edits. Accepted query additions must update
the known eval/count-lock chain together and must not tune ES/Qdrant, parser, vocabulary, seed data,
routes, or production search behavior unless that is the explicit objective.

Dirty catalog profiles in runtime scorecard tests are measurement-only. They may show worse coverage
without failing the test and do not by themselves justify production Qdrant activation, fallback,
fusion, rerank, or ES/Qdrant tuning.

The repository also contains upstream distage example implementation variants under `monofunctor-tagless` and `monomorphic-cats`.
