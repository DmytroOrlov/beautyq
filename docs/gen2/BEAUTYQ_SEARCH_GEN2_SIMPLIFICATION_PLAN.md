# BeautyQ Search Gen2 — simplification proposal

**Status:** non-normative proposal and research input; **not** current milestone/task authority.

This document is a forward-looking hypothesis set about simplification work that remains genuinely
unresolved. Completed cleanup is not recorded here; Git history owns completed work. Nothing in this
document pre-decides a disposition or authorizes execution.

**Feature ownership:** `specs/003-beautyq-distage-izumi-leverage-audit` is the current optional
framework-leverage research contract, and
`specs/004-beautyq-post-closeout-simplification-decision` is the current decision-contract owner for the
still-unresolved simplification disposition/decision if that explicit decision process is invoked. 003
may use this document as audit/input context. Neither feature authorizes implementation, and neither
prevents the human from explicitly authorizing a separately scoped task through another appropriate
workflow.

**Execution boundary:** this proposal authorizes nothing by itself. The
[technical specification](BEAUTYQ_SEARCH_GEN2_TECHNICAL_SPEC.md),
[operations runbook](BEAUTYQ_SEARCH_GEN2_OPERATIONS.md), and
[repository-wide domain-authoring principles](../search/DOMAIN_AUTHORING_PRINCIPLES.md) remain the
normative current owners. Each accepted change must update its canonical owner after code and focused
proofs stabilize. Until that update is merged, the existing canonical document wins.

## 1. Purpose

Reduce the maintenance and proof burden of the single-production-domain Search Gen2 stack while
preserving its product behavior and the genuinely reusable typed kernel.

The problem is not that neutral fixtures exist. The domain-authoring principles explicitly allow a
neutral or adversarial fixture to prove a reusable representation before a second production domain
exists. The candidates below are the places where one production consumer still carries cost whose
current value is genuinely unresolved.

## 2. Unresolved candidate areas

### 2.1 Qdrant has proved operation and no-harm, not marginal product value

The managed Qdrant/embedding path exists and the current cutover gate proves Required/FullSearch
readiness, append-only behavior, baseline preservation, ordering, component preservation, and an append
budget. It is therefore incorrect to classify Qdrant as non-working.

The cutover `improvement-observed` condition means that a non-worsening execution appended at least one
supplement-only identity. The correction gate additionally covers deterministic execution, forbidden
hits, duplicates, degradation, baseline prefix/components, and append budget. These are meaningful
safety and integrity proofs, but neither is a baseline-versus-full relevance delta.

Qdrant is therefore conditional, not a deletion candidate by footprint alone. Its long-term ownership
must be decided by a separate, predeclared product-value comparison:

- name the user-visible metric or ordered metric vector supplementation is intended to improve;
- name cutoffs and slices, the minimum accepted improvement, and the maximum regression budget;
- name forbidden-hit, hard-constraint, baseline-preservation, and degradation stop conditions;
- run the same application revision, source snapshot, Elasticsearch generation, corpus, requests, and
  page policy in baseline-only mode and in Required/FullSearch mode, and report the declared deltas.

`KEEP`, `DELETE`, and `BLOCKED` (missing inputs/thresholds/ownership) are all valid outcomes; append-only
activity is never reinterpreted as product value. This is a 004 decision, not a change authorized here.

### 2.2 Trace-only and duplicated presentation owners

A trace/renderer/ledger may be removed only when reachability proves it has no operator, response,
artifact, or diagnostic consumer outside its own tests and documentation. Candidates include:

- `PlanIdentityTrace` and its spec;
- `BeautyQSearchPlanCompilationTrace` and its golden spec;
- `BeautyQCandidatePlanTrace` and its golden spec;
- request/intent trace views made unreachable by those removals.

`PlannedAlgebraTrace` remains while response projection consumes it. Fingerprints, typed identities, and
observable error diagnostics are not traces for this purpose and remain protected contracts.

### 2.3 Project consolidation

Consolidating the ten Gen2 sbt projects is a separate, still-unresolved structural question. It must not
change package names or behavior in the same patch. A plausible target shape is a modular monolith in
which sbt projects exist only where they provide a current compilation, dependency, runtime, or
evaluation boundary; `search-gen2-transport` need not remain standalone while two backends use it.

Because the former exact-DAG/module-firewall machinery has already been collapsed, consolidation no
longer has to sequence around preserving a build.sbt mirror. Ordinary sbt dependency direction and
compilation enforce the remaining coupling; any retained negative check stays small and source-owned.

### 2.4 Test consolidation around observable contracts

Create a coverage map before deleting tests. Each invariant has one primary owner, and only genuinely
different proof layers remain separate. Merge or delete tests that differ only by rendering layer,
private-wrapper construction boundary, duplicated fixture inventory, module-source scanning, or
historical delivery phase. Do not reduce meaningful corpus cases to hit a line target.

### 2.5 Documentation reconciliation

Rewrite current-state documentation to describe the resulting system after code and proofs settle:
technical specification, operations, domain authoring/onboarding, and no chronology of cleanup. Historical
delivery narrative and exact old project graphs belong to Git history.

## 3. Verification and reporting rules

For every source change:

1. Name generic and BeautyQ-specific owners before editing.
2. Record the exact observable contracts expected to stay unchanged.
3. Run focused owning-project tests; do not use an unrelated root suite as the first diagnostic.
4. Run route/communication/evaluation proof only when the change crosses that boundary.
5. Report command, result, useful counts, external-resource blocks, and remaining uncertainty.
6. Scan touched Scala specs for unsafe extraction before closeout.
7. Update canonical documentation only after implementation and proofs stabilize.

## 4. Non-goals

This proposal does not:

- weaken typed IDs, validation, cursor/generation binding, or fail-closed backend behavior;
- move BeautyQ policy into generic code;
- remove Qdrant without a product decision;
- claim that neutral fixtures are invalid reuse proof;
- begin a second production domain;
- add CDC, hot refresh, automatic promotion, generic rate limiting, arbitrary analyzers, or another
  supplement framework;
- optimize for a fixed LOC target at the expense of route, projection, intent, Elasticsearch,
  lifecycle, or evaluation confidence.
