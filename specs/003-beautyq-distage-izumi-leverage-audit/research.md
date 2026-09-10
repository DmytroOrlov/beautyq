# Phase 0 Research: BeautyQ Distage/Izumi Leverage Audit — Planning Decisions

**Feature**: `specs/003-beautyq-distage-izumi-leverage-audit`

**Scope**: This file records the decisions needed to *plan* the read-only leverage audit. It is not the audit and contains no findings. The audit's own findings live in `research/00..05` and are produced only when the human invokes execution.

This phase resolves the plan's open questions (source attribution, framework-version resolution, artifact set, parallelism, staleness, validation scope, candidate handling, negative-claim standard).

---

## D1 — Source-state attribution method

- **Decision**: Attribute findings to the evaluated state using an explicit `HEAD` SHA plus `git status --short` (and `git diff --stat` / `git diff --cached --stat` when dirty), captured at run start and stored in `research/00-selected-state.md` as a lightweight `stateId`.
- **Rationale**: Constitution Principle V requires evidence to bind to the exact evaluated state; it also explicitly forbids forcing agents to invent corpus/policy machinery. A commit-or-worktree identity is sufficient and truthful. The spec forbids recreating `AcceptedEvaluationBaseline`, a promoted baseline, `sourceRevision`, or digest ceremony.
- **Alternatives considered**:
  - Commit an evidence-anchor revision before audit — rejected: agents cannot commit (Principle I) and the audit must not require a commit.
  - Cryptographic digest chain over sources — rejected: evidence bureaucracy (FR-011, Principle IX).
  - Rely on `HEAD` alone when dirty — rejected: Principle V requires distinguishing index/worktree from `HEAD`.

## D2 — Framework-version resolution order

- **Decision**: Resolve and verify the pinned framework version in this order: (1) `build.sbt` + `project/*.sbt` + `project/build.properties`; (2) locally available `io.7mind.izumi` artifacts/source attachments for that exact version; (3) matching upstream release/tag source/docs/examples; (4) only if ambiguous, one focused read-only sbt dependency query. Currently `build.sbt` declares `V.distage = "1.2.25"` and `V.logstage = V.distage` with no project override.
- **Rationale**: FR-008/SC-005 require the actually-resolved dependency, not latest upstream. The cheapest trustworthy evidence is the build definition; escalation is only for genuine ambiguity.
- **Alternatives considered**:
  - Use `latest`/`master` docs — rejected: not the resolved version.
  - Assume from memory — rejected: unauthenticated framework claim.
  - Run a broad dependency/compile task unconditionally — rejected: disproportionate (this is research, not a build campaign).

## D3 — Durable artifact set and location

- **Decision**: Six purpose-separated durable artifacts under `specs/003-beautyq-distage-izumi-leverage-audit/research/` (selected-state; candidate map; capability map; comparison+discoverability; classified findings+calibration; synthesis+recommendations+validation).
- **Rationale**: FR-011 requires structured research memory in feature artifacts so a later empty-context session resumes without conversation history. Six artifacts map cleanly to distinct questions and phase ownership, matching the spec's suggested set without one-file-per-stage bookkeeping.
- **Alternatives considered**:
  - A single monolithic research document — rejected: forces whole-audit context into one pass and blocks parallel cheap-context mapping.
  - One artifact per candidate or per sub-step — rejected: evidence bureaucracy; harder to consume.
  - Store in conversation/checkpoint only — rejected: violates FR-011.

## D4 — Parallel / multi-context execution design

- **Decision**: Phase A (BeautyQ source mapping) and Phase B0 (broad pinned-framework capability inventory by theme) run as independent bounded read-only slices and may execute concurrently in separate cheap contexts, each returning a compact artifact. B0 is seeded by the spec's Candidate Research Areas and known framework themes, not by A's candidate IDs. After A completes, a small bounded B1 gap-fill reconciles A's actual needs against B0 and appends only missing focused lookups to the same `02-framework-capability-map.md`. Phase C joins A's needs to the B0/B1 surfaces; Phase E (adversarial classification) and Phase F (synthesis/calibration) are reserved for the stronger adjudication pass.
- **Rationale**: The audit must not require one expensive context to hold the entire repository/framework audit (FR-010). A and B0 have no dependency on each other, so they genuinely parallelize; B1 is bounded and small; classification and synthesis need judgment.
- **Alternatives considered**:
  - Make B fully sequential after A (B truly depends on A) — rejected: destroys the two-cheap-context parallelism the design depends on.
  - Fully sequential single-context audit — rejected: expensive, context-fragile.
  - Parallelize classification too — rejected: classification needs cross-candidate consistency and the genericity filter; splitting it loses calibration.

## D5 — Staleness / supersession handling

- **Decision**: Each artifact and its findings carry the `stateId` and framework-version reference they were derived from. If `HEAD`/worktree changes materially mid-audit, affected findings are marked `SUPERSEDED` (retained, attributed to the old state) and re-checked against the new state before any recommendation consumes them. No silent reattribution.
- **Rationale**: FR-002/FR-011/edge cases and Principle V require attribution, not source immobility.
- **Alternatives considered**:
  - Re-run the whole audit on any edit — rejected: wasteful and not required.
  - Float findings to the new state — rejected: forbidden by Principle V.

## D6 — Validation scope

- **Decision**: Validate research-output properties only: source refs resolve at the recorded state; framework claims match the pinned version; every material candidate has an explicit current state and every `CLASSIFIED` candidate has exactly one evidence-backed label (`BLOCKED_NEED_EVIDENCE`/`INDETERMINATE` carry none); negative claims have search/counterexample coverage; genericity filter present; synthesis answers all eight standing questions; calibration present; Git/source unmutated. No implementation CI, no repository test campaign, no full-suite run.
- **Rationale**: 003 changes no product code (spec "Do Not Overplan Validation"). Validation must prove the audit is decision-grade and read-only, not that product behavior works.
- **Alternatives considered**:
  - Run focused product suites as "confidence" — rejected: irrelevant to a research output and out of scope.
  - Add a dedicated validation framework — rejected: Plan artifacts + a checklist suffice.

## D7 — Candidate-area handling

- **Decision**: Treat each spec Candidate Research Area as a hypothesis that may be `MAPPED`, `NO_CURRENT_MATERIAL_CANDIDATE`, or `REJECTED` with a short basis. Do not reconstruct deleted Wave-A/Wave-B mechanisms to evaluate an old candidate.
- **Rationale**: FR-013 and the cleanup-reconciliation state; the completed Wave A/B cleanup must not be reopened because an older audit file names deleted machinery.
- **Alternatives considered**:
  - Force every candidate into a positive finding — rejected: manufactures findings and quotas.
  - Treat the candidate list as a backlog — rejected by the spec.

## D8 — Negative-claim evidence standard

- **Decision**: A "framework cannot do X" claim requires pinned-version source/API/example evidence and a documented search basis; a matching capability that merely could not be found quickly is not `MISSING_GENERIC_PRIMITIVE`. The standard is per-pinned-version and per-candidate.
- **Rationale**: FR-005/FR-008/SC-005 and the edge-case rule that unresolved absence is `BLOCKED_NEED_EVIDENCE`, not "missing".
- **Alternatives considered**:
  - Accept absence on first search failure — rejected: weak negative evidence.
  - Require exhaustive framework-wide proof of absence — rejected: disproportionate; search-bounded per candidate is the accepted standard.

## D9 — Recommendation discipline

- **Decision**: Recommendations are emitted only in Phase F, each tagged `docs/examples` or `framework API/code`, graded by evidence, naming a second consumer for any primitive proposal, and always phrased as non-authoritative. No recommendation is an implementation task in this feature.
- **Rationale**: FR-007/FR-014/FR-017 and Principles IV/VIII.
- **Alternatives considered**:
  - Emit recommendations alongside classification — rejected: FR-009 puts recommendations after counterexamples (Phase F).
  - Phrase strong findings as decisions — rejected: proposals are not accepted architecture.

---

## Resolved unknowns

| Unknown in plan context | Resolution |
| --- | --- |
| How to anchor the evaluated BeautyQ state without baseline ceremony | D1 (`HEAD` + porcelain `stateId`) |
| Which framework version and how to verify it | D2 (`io.7mind.izumi` 1.2.25 from `build.sbt`, escalate only if ambiguous) |
| Where durable research state lives | D3 (`specs/003-.../research/`, six artifacts) |
| How to avoid one-context audit | D4 (A/B parallel cheap slices; E/F adjudication) |
| How to handle source drift | D5 (`SUPERSEDED`, never reattributed) |
| What "validation" means for research | D6 (output properties; no CI campaign) |
| What to do with inactive candidate areas | D7 (`NO_CURRENT_MATERIAL_CANDIDATE`/`REJECTED` with basis) |
| How strong must a negative claim be | D8 (pinned-version + search basis) |
| How recommendations are constrained | D9 (Phase F; kind-tagged; graded; non-authoritative) |

No `NEEDS CLARIFICATION` markers remain for planning.
