[![Build Status](https://github.com/7mind/distage-example/workflows/Build/badge.svg)](https://github.com/7mind/distage-example/actions/workflows/build.yml)
[![License](https://img.shields.io/github/license/7mind/distage-example.svg)](https://github.com/7mind/distage-example/blob/develop/LICENSE)

# distage example

This repository currently includes BeautyQ search work inside the broader distage example project.

Start here:

* `AGENTS.md` for repository execution rules for agents
* [BeautyQ Search Gen2 technical specification](docs/gen2/BEAUTYQ_SEARCH_GEN2_TECHNICAL_SPEC.md) — current implemented Gen2 architecture, runtime ownership, supported shapes, accepted limits, and verification ownership
* [BeautyQ Search Gen2 operations runbook](docs/gen2/BEAUTYQ_SEARCH_GEN2_OPERATIONS.md) — launcher modes, operator status, response warnings, recovery, protected/evidence procedures, and partial-activation operations
* [Domain authoring principles](docs/search/DOMAIN_AUTHORING_PRINCIPLES.md) — repository-wide reusable domain/search ownership principles
* [NEW_DOMAIN_ONBOARDING.md](docs/search/NEW_DOMAIN_ONBOARDING.md) — practical reusable guidance for domain authoring, lifecycle composition, proof selection, and focused validation
* `docs/local/COORDINATOR_WORKFLOW_AND_PROMPTING.md` — coordinator workflow, phase-boundary, review, and anti-scope-drift rules
* `specs/<feature>/` — Spec Kit feature/change delivery records; use the relevant active feature for change-specific requirements, gates, plans, and tasks

BeautyQ Search Gen2 is the native serving architecture.

`/docs` owns the human-readable description of how the system is structured, operated, and extended now. `specs/<feature>/` owns feature-specific change/delivery state while that feature is active; after closeout, those artifacts are historical delivery records rather than the current architecture contract. When a feature changes durable architecture, operations, or reusable authoring guidance, closeout must reflect the accepted result back into the corresponding `/docs` owner.

Do not reconstruct the current system by reading feature specs in chronological order. Source code and focused tests remain executable truth for implemented behavior; exact Git/evidence identities describe the precise states that were evaluated.

For local startup, status inspection, recovery, protected evidence procedures, and request probes, use the BeautyQ Search Gen2 operations runbook.

## Eval and measurement guardrails

BeautyQ eval query changes are not simple JSON-only edits. Accepted query additions must update
the known eval/count-lock chain together and must not tune ES/Qdrant, parser, vocabulary, seed data,
routes, or production search behavior unless that is the explicit objective.

Dirty catalog profiles in runtime scorecard tests are measurement-only. They may show worse coverage
without failing the test and do not by themselves justify production Qdrant activation, fallback,
fusion, rerank, or ES/Qdrant tuning.

The agent-facing execution form of these guardrails belongs in `AGENTS.md`; this README keeps the human-facing summary.

The repository also contains upstream distage example implementation variants under `monofunctor-tagless` and `monomorphic-cats`.
