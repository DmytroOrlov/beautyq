# BeautyQ Search Gen2 — post-Q2 simplification proposal

**Status:** non-normative proposal and research input; **not** current milestone/task authority.

This document is a hypothesis set and proposed decomposition. G1–G7 (and the S1–S7 milestone table below)
are proposal structure, not permission to execute and not the current execution plan for any milestone.

**Feature ownership:** the framework leverage audit is
`specs/003-beautyq-distage-izumi-leverage-audit`, and the actual post-closeout simplification
disposition/decision is `specs/004-beautyq-post-closeout-simplification-decision`, dependent on
the 003 audit. 003 may use this document as audit/input
context but must classify evidence independently; 004 owns the simplification decision, and nothing here
pre-decides any 004 disposition. Accepted decisions must later be reflected into the durable `/docs`
current owners. Once its useful conclusions have been absorbed by the decision and closeout owners, this
proposal is intended to be retired rather than maintained as a second current-state plan.

**Execution boundary:** this proposal authorizes nothing by itself. Until an accepted decision updates the durable
owners, the [technical specification](BEAUTYQ_SEARCH_GEN2_TECHNICAL_SPEC.md),
[operations runbook](BEAUTYQ_SEARCH_GEN2_OPERATIONS.md), and
[repository-wide domain-authoring principles](../search/DOMAIN_AUTHORING_PRINCIPLES.md) remain the
normative current owners. Each accepted milestone must update its canonical owner after code and focused
proofs stabilize. Until that update is merged, the existing canonical document wins.

This document proposes later simplification work. It does not describe the current architecture, amend
Q2, authorize a Qdrant rollout/removal, start D1, or relax any current search contract.

**Relation to D1:** D1 remains deferred unless its product identity and source topology are supplied
and the milestone is explicitly activated. While D1 is deferred, post-Q2 simplification may proceed.
If D1 becomes actionable before simplification closes, sequencing requires an explicit coordinator
decision; neither plan silently preempts the other.

## 1. Purpose

Reduce the maintenance and proof burden of the single-production-domain Search Gen2 stack while
preserving its product behavior and the genuinely reusable typed kernel.

The problem is not that neutral fixtures exist. The domain-authoring principles explicitly allow a
neutral or adversarial fixture to prove a reusable representation before a second production domain
exists. The problem is that one production consumer currently carries platform-scale packaging and
proof depth:

- ten Gen2 sbt projects and an exact-DAG source-parsing firewall;
- production owners retained only as historical cutover evidence;
- trace, ledger, renderer, boundary, and compile-probe layers that sometimes prove one another rather
  than observable product behavior;
- a protected-evaluation workflow whose full Q2 operational shape is presented as the default path for
  a future domain;
- a dual-backend supplement whose communication and append-only safety are proven, but whose marginal
  product value over the Elasticsearch baseline has not yet been isolated by a baseline-versus-full
  quality comparison.

The intended destination is a modular monolith: semantic package boundaries remain explicit, but sbt
projects and standalone proof owners exist only where they provide a current compilation, dependency,
runtime, or evaluation boundary.

## 2. Review corrections and retained conclusions

### 2.1 Neutral fixtures are valid evidence

Neutral fixtures are not a substitute pretending to be a production domain. They are valid evidence
when a generic shape comes from a real requirement, contains no BeautyQ policy, and is challenged
independently of BeautyQ. They do not, by themselves, justify platform packaging, a broad public
vocabulary, or support claims beyond the proved shape.

No simplification milestone may inline a neutral algorithm into BeautyQ merely because BeautyQ is its
only current domain consumer. Package neutrality and project packaging are separate decisions.

### 2.2 The module firewall currently protects a real negative property

The Scala compiler cannot prevent a future edit from adding a BeautyQ dependency to a generic sbt
project. The current firewall therefore protects a real architectural property. Its problem is cost and
duplication: it re-declares and parses the exact ten-project constitution.

The firewall must not be deleted while leaving the current module constitution otherwise unchanged.
Module consolidation and firewall removal/collapse form one milestone. After consolidation, ordinary
sbt dependency direction and compilation must enforce the remaining important boundary; any retained
negative check must be small, source-owned, and must not implement another `build.sbt` parser.

### 2.3 Qdrant has proved operation and no-harm, not marginal product value

The managed Qdrant/embedding path exists and the current cutover gate proves Required/FullSearch
readiness, append-only behavior, baseline preservation, ordering, component preservation, and an append
budget. It is therefore incorrect to classify Qdrant as non-working.

The current cutover `improvement-observed` condition means that a non-worsening execution appended at
least one supplement-only identity. The 164-case correction gate additionally covers deterministic
execution, forbidden hits, duplicates, degradation, baseline prefix/components, and append budget. These
are meaningful safety and integrity proofs, but neither is a baseline-versus-full relevance delta.

Qdrant is therefore conditional, not a deletion candidate by footprint alone. Its long-term ownership
must be decided by a separate, predeclared product-value comparison.

### 2.4 Cutover code contains both history and live proofs

`BeautyQGen1SearchDeletionInventory` is historical completion evidence. `BeautyQCutoverGate` and
`BeautyQSearchGen2CutoverCommunicationSpec` also contain live readiness, no-harm, backend-wire, and
measured-evaluation proof. The historical portion may be deleted after Q2; the live portions must be
renamed, separated, and retained or replaced by equivalent current owners before cutover terminology is
removed.

## 3. Baseline and outcome ranges

The initial inventory counted raw Scala lines, including comments and whitespace:

| Area | Main lines | Test lines | Total lines |
|---|---:|---:|---:|
| Generic Gen2 projects | 12,785 | 15,019 | 27,804 |
| BeautyQ Gen2 projects | 9,267 | 14,951 | 24,218 |
| Search-owned app-shell sources | 634 | 5,980 | 6,614 |
| Minimum Search Gen2 footprint | 22,686 | 35,950 | 58,636 |

These counts are navigation signals, not acceptance criteria. A patch is accepted for clearer
ownership and preserved behavior, never merely for deleting lines.

Expected outcome ranges after all approved milestones:

- **Qdrant retained:** approximately 45–50k Search Gen2 Scala lines, with approximately 18–20k main
  lines;
- **Qdrant removed after a negative product decision:** approximately 38–45k Search Gen2 Scala lines,
  with approximately 15–18k main lines;
- **project graph:** ten Gen2 projects reduced to five when Qdrant is retained or four when it is
  removed, excluding the existing app HTTP/shell projects.

Thirty thousand lines is not a target. These ranges may be exceeded when an observable contract needs
more proof. The closeout report records actual changes and deviations.

## 4. Contracts that simplification must preserve

Unless a separate product requirement explicitly changes one, every milestone preserves:

1. `POST /beauty-search` request, error, status, and response contracts;
2. Elasticsearch ownership of canonical hits, order, totals, facets, groups, cursor state, and baseline
   diagnostics;
3. explicit stable public, field, facet, group, reason, plan, cursor, and generation identities;
4. one consistent source snapshot, explicit BeautyQ projection, invariant validation, and deterministic
   projected-document identity;
5. typed, fail-closed plan, backend, cursor, response, and generation validation;
6. source-readiness ordering before materialization and activation;
7. no serving dependency on evaluation code;
8. no BeautyQ policy or identity in generic packages;
9. current Qdrant append-only/no-baseline-harm semantics for as long as Qdrant remains;
10. evaluation rules that forbid output-driven relabeling of the same holdout.

Framework and domain boundaries remain:

- generic code owns typed field/plan mechanics, deterministic framing/materialization mechanics, and
  backend mechanics for retained backends;
- BeautyQ owns projection, text and intent policy, public names, facets/groups/ranking, backend
  activation, supplement acceptance, response projection, and evaluation thresholds;
- app HTTP/shell owns transport adaptation, DI/lifecycle composition, configuration, and managed
  resource integration.

## 5. Goals

### G1 — Remove completed history from executable ownership

Delete production/test/report wiring whose only current meaning is the already-complete Gen1 cutover.
Initial high-confidence scope:

- `BeautyQGen1SearchDeletionInventory` and its spec;
- generation of the Gen1 deletion-inventory artifact from the communication proof;
- dead eval `ModuleMarker` owners;
- historical Gen1 disposition assertions that do not protect a current input, plan, backend, route, or
  evaluation contract.

Split mixed owners first. The live communication/evaluation portion of a cutover-named suite is renamed
to its current responsibility before historical data is deleted.

**Done when:** no main or test source constructs a report about removed Gen1 owners; Git history is the
history owner; current readiness, no-harm, communication, and measured-evaluation proofs still have
named owners.

### G2 — Remove trace-only and duplicated presentation owners

Delete a trace/renderer/ledger only when reachability proves that it has no operator, response,
artifact, or diagnostic consumer outside its own tests and documentation. Initial candidates include:

- `PlanIdentityTrace` and its spec;
- `BeautyQSearchPlanCompilationTrace` and its golden spec;
- `BeautyQCandidatePlanTrace` and its golden spec;
- request/intent trace views made unreachable by those removals;
- the historical Gen1 disposition portion, not the current normalization/collision/action semantics,
  of intent vocabulary evidence.

`PlannedAlgebraTrace` remains while response projection consumes it. Fingerprints, typed identities,
and observable error diagnostics are not traces for this purpose and remain protected contracts.

**Done when:** every retained presentation owner has a named current consumer and one behavioral proof;
no production formatter exists only to satisfy its own golden test.

### G3 — Replace the ten-project constitution with current boundaries

Consolidate projects without initially changing package names or behavior. Target shape:

```text
search-gen2-core
  contract + core + shared transport packages

search-gen2-elasticsearch

search-gen2-qdrant
  retained after a KEEP decision;
  after a DELETE decision its runtime owners are removed in S5-D and its
  behavior-empty project boundary is removed with the old DAG/firewall in S6

beautyq-search-gen2
  BeautyQ contract + materialization + wiring packages

beautyq-search-gen2-eval
  BeautyQ evaluation + currently single-consumer neutral evaluation mechanics

app-http / leaderboard-app-shell
  unchanged application-edge projects
```

If D1 becomes actionable and is explicitly activated before this milestone, independently reusable
eval mechanics may remain in a small neutral eval project. Otherwise they remain package-neutral inside
the BeautyQ eval project and can be extracted from two real consumers later.

`search-gen2-transport` does not remain a standalone sbt project. It joins core while two backends use
it; if only Elasticsearch remains, its mechanics may become Elasticsearch-local in a later bounded
patch.

The exact-DAG firewall, materialization firewall, and dependency/package compile probes are removed or
collapsed in the same milestone that makes their old constitution false. No interim commit leaves the
old graph without its current protection. After a DELETE decision, S5-D therefore retains the
`search-gen2-qdrant` sbt project and its old dependency edges as a temporary behavior-empty structural
shell. If the current firewall's tracked-main-root check requires a source file, the shell may retain
one minimal behaviorless boundary marker; S6 removes that marker, project, obsolete edges, and firewall
together.

**Done when:** the smaller sbt DAG compiles; a generic project has no BeautyQ dependency; serving has no
eval dependency; the retained backend(s), BeautyQ domain, and app edge remain independently owned; no
test parses `build.sbt` or scans source text to restate the complete project graph.

### G5 — Decide Qdrant by marginal product value

Run a controlled comparison after evaluation simplification. Communication success and no-harm
remain prerequisites, not the value criterion.

Before execution, BeautyQ policy must name:

- the user-visible metric or ordered metric vector that supplementation is intended to improve;
- cutoffs and slices to which each metric applies;
- the minimum accepted improvement or an explicitly approved benefit rule;
- the maximum regression budget, normally zero unless a concrete product decision approves otherwise;
- forbidden-hit, hard-constraint, baseline-preservation, and degradation stop conditions;
- the exact visible corpus/source identity used for the decision.

The comparison runs the same application revision, source snapshot, Elasticsearch generation, corpus,
requests, and page policy in:

1. baseline-only mode with supplement construction/execution disabled;
2. Required/FullSearch mode with the production Qdrant path.

It reports aggregate and visible-case deltas for the declared metrics. Protected identities are not
required for the first ablation bundle. Existing `improvement-observed` append evidence must not be
reported as a relevance improvement.

Outcomes:

- **KEEP:** the predeclared product-value rule passes and all safety gates remain green. Retain Qdrant as
  a separate backend project, then simplify only duplicated proof/wiring around it.
- **DELETE:** the rule is red and product ownership rejects the cost/benefit. S5-D removes Qdrant
  runtime, wiring, operations, evaluation, embedding, supplement/candidate-only behavior with no
  remaining consumer, and dual-backend readiness/activation. It does not change the sbt project
  constitution: the now behavior-empty `search-gen2-qdrant` shell and old dependency edges remain under
  the old firewall until S6 removes the shell, obsolete edges, and exact-DAG firewall together.
- **BLOCKED:** inputs, thresholds, or product ownership are absent. Make no backend architecture change;
  do not reinterpret append-only activity as product value.

**Done when:** a reviewed decision record binds the exact evidence identity and authorizes KEEP or
DELETE; the technical specification, operations runbook, and runtime architecture are then reconciled
with that decision.

### G6 — Consolidate tests around observable contracts

Create a coverage map before deleting tests. Each invariant has one primary owner and only genuinely
different proof layers remain separate.

Retain at least:

- route-level request/error/response contract tests;
- intent and public-input business scenarios, including normalization, collision, and action semantics;
- projection/materialization invariants and deterministic fingerprints;
- Elasticsearch request/response, facets/groups/cursor, and generation lifecycle behavior;
- one Distage graph/startup proof for each supported serving mode;
- one real communication proof per retained external backend and embedding path;
- visible/regression evaluation and the durable holdout acceptance proof;
- initialization-order regressions for source-confirmed JVM/DI failures.

Merge or delete tests that differ only by rendering layer, private-wrapper construction boundary,
duplicated fixture inventory, module-source scanning, or historical delivery phase. Prefer table-driven
behavior cases over repeated builders and expected trees. Do not reduce meaningful corpus cases to hit a
line target.

**Done when:** every retained test names an observable contract or a distinct proof layer; no behavior is
accepted solely from an implementation-detail test; focused owners and the authorized closure command
are green.

### G7 — Reconcile and shorten canonical documentation

Only after code and proofs settle, rewrite current-state documentation to describe the resulting system:

- technical specification: current contracts, ownership, supported shapes, and accepted limits;
- operations: only commands and recovery paths for retained modes/backends;
- domain authoring/onboarding: the actual minimum learned from BeautyQ and, when available, D1;
- post-Q2 simplification closeout: decisions, deviations, and remaining work, not implementation
  chronology.

Historical delivery narrative and exact old project graphs belong to Git history. Executable policy and
focused tests remain the source of exact behavior.

**Done when:** no canonical document instructs D1 to reproduce removed Q2 machinery, names a removed
project/owner, or describes a backend/mode that no longer exists.

## 6. Milestone order

Each milestone is an atomic review boundary. Do not combine backend removal, public contract changes,
and module moves in one patch.

Project consolidation deliberately follows protected-workflow simplification and the Qdrant KEEP/DELETE
implementation. Until then, the existing firewall continues protecting the old DAG. This avoids moving
Q2-only code that will immediately be deleted and avoids building a five-project target before deleting
the Qdrant project. In the DELETE branch, S5-D removes backend behavior but deliberately leaves the
behavior-empty project shell and dependency constitution intact; S6 performs the structural removal.

| Milestone | Scope | Required proof | Stop condition |
|---|---|---|---|
| S1 | Split live cutover proof; delete historical inventory/dead markers | Current communication and measured-eval owners compile/run unchanged | A deleted value still feeds current readiness/evaluation behavior |
| S2 | Delete trace-only owners and historical ledger portions | Plan/candidate behavior, response projection, and stable identity proofs | A trace has a current operator/response/artifact consumer |
| S4 | Produce Qdrant baseline/full ablation and decision record | Same-source two-mode evidence with predeclared metric policy | Threshold/product decision missing or evidence identities differ |
| S5-K | Qdrant KEEP simplification | Qdrant communication, no-harm, product gate, runtime/status contracts | Product gate or safety proof turns red |
| S5-D | Remove Qdrant behavior while retaining its temporary sbt project shell, old edges, and firewall | ES-only route, lifecycle, status, evaluation, and operations proofs; old exact-DAG firewall remains green | Public baseline behavior changes or the pre-S6 project constitution changes |
| S6 | Consolidate the final retained projects; remove any empty Qdrant shell, obsolete edges, and old meta-firewalls together | Focused compile/tests for every moved owner; module/eval dependency direction | A generic package would acquire BeautyQ policy or serving would acquire eval |
| S7 | Test deduplication and canonical documentation closeout | Coverage map, focused owners, authorized closure command | Only proof of an invariant would be removed |

S5-K and S5-D are mutually exclusive.

## 7. Verification and reporting rules

For every source milestone:

1. Name generic and BeautyQ-specific owners before editing.
2. Record the exact observable contracts expected to stay unchanged.
3. Run focused owning-project tests; do not use an unrelated root suite as the first diagnostic.
4. Run route/communication/evaluation proof only when the milestone crosses that boundary.
5. Report command, result, useful counts, external-resource blocks, and remaining uncertainty.
6. Scan touched Scala specs for unsafe extraction before closeout.
7. Update canonical documentation only after implementation and proofs stabilize.

The final closeout records:

- projects, main/test files, and raw Scala lines before and after;
- historical, trace/meta-proof, protected-workflow, and backend-specific owners removed or retained;
- the Qdrant decision and exact evidence identity;
- retained public/runtime invariants and their primary proof owners;
- any result outside the expected size ranges and the behavioral reason for retaining it.

## 8. Non-goals

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
