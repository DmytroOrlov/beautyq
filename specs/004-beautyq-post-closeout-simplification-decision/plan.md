# Implementation Plan: BeautyQ Post-Closeout Simplification Decision

**Branch**: `004-beautyq-post-closeout-simplification-decision` (Spec Kit feature-directory identifier; no Git branch is created or switched — ref/index mutation is human-owned per Constitution Principle I) | **Date**: 2026-09-10 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/004-beautyq-post-closeout-simplification-decision/spec.md`

## Summary

This is a **decision feature**, not an implementation feature. Its deliverable is an evidence-backed,
per-candidate **simplification decision package** that a human decision-maker approves, rejects, defers,
or narrows. Planning here defines the smallest evidence workflow that can produce that package; it does
**not** evaluate candidates and assigns **no** dispositions.

The decision evaluates the still-unresolved candidate areas of the non-normative
[simplification proposal](../../docs/gen2/BEAUTYQ_SEARCH_GEN2_SIMPLIFICATION_PLAN.md) against the current
normative owners ([operations runbook](../../docs/gen2/BEAUTYQ_SEARCH_GEN2_OPERATIONS.md),
[technical specification](../../docs/gen2/BEAUTYQ_SEARCH_GEN2_TECHNICAL_SPEC.md)), the current read-only
source/build/test reality, and the completed
[003 Distage/Izumi leverage audit](../003-beautyq-distage-izumi-leverage-audit/research/05-synthesis-and-recommendations.md)
as **evidence only**. Candidate areas already resolved by the authorized pre-004 cleanup are out of scope;
the proposal is attributed criticism, never current architecture (Principle VIII).

No source, build, runtime, test, evaluation-corpus/threshold, or normative-owner-document change is
authorized by this feature or by any disposition it recommends — including after a human verdict
(spec FR-009).

## Decision Context

This feature authors Markdown decision artifacts only; it adds no production technology.

| Aspect | Value |
| --- | --- |
| Feature type | Internal evidence-backed **decision contract** (Spec Kit), documentation-only |
| Language/tooling authored | None. Evaluation reads existing Scala 3.3.8 / sbt 1.12.11 source **read-only** |
| Dependencies added | None |
| Storage / runtime | N/A — no application code, migrations, or runtime path |
| Testing | No product suite is required for this feature. Validation is plan/artifact consistency and evidence-reference resolution only (spec "lightweight" rule; Principle XI) |
| Target platform | N/A — repository documentation |
| Constraints | Worktree-only; no production/source/build/test/runtime/normative-owner edits; no internal `contracts/` tree; no `tasks.md` created by `/speckit.plan` (a later real `/speckit.tasks` may create the canonical feature-local `tasks.md`); no Git/index/ref mutation (Principle I); no digest/freeze/pass-identity/fingerprint/baseline/review-bundle/revision-plumbing ceremony (spec economy rule) |
| Scale | One canonical decision record; a bounded candidate inventory derived from proposal §2.1–2.5 plus audit-surfaced candidates |

### Planning-time evaluated state (re-derived, not trusted from the prompt)

| Field | Value (recorded at `/speckit.plan` time) |
| --- | --- |
| Repo root | `/Users/do/git/sandbox/distage-example` |
| Git `HEAD` | `f2147c4c94bc00d1e10e3a87f2d8b33c8a0d0e49` |
| Current Git branch | `develop` (no feature Git branch is created/switched) |
| Worktree at phase start | Clean porcelain; only this feature's Markdown artifacts are added by this phase |
| 003 evaluated source state (evidence) | `50e794584d68768403d5c45d6d7fc21c2db87f6c`, `stateId = 50e7945-clean` |
| Resolved framework version (003 evidence) | `io.7mind.izumi 1.2.25` |

Decision execution MUST re-derive its own evaluated source/architecture state at execution time and bind
every finding to it (Principle V); the planning-time values above are navigation/attribution context, not
an authority that execution may trust. Source-state drift **before a human verdict** marks affected
evidence superseded and returns the affected candidates to evaluation; dispositions are never silently
re-attributed to another source state. After a human verdict, the recorded verdict remains historical
evidence bound to the state actually decided and is not rewritten or reopened by later drift; using a
recommendation against a materially changed state requires a new evaluation against that state.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Gates derived from `.specify/memory/constitution.md` (v2.0.0), applied to a decision-only feature:

| Principle | Gate for this feature | Status |
| --- | --- | --- |
| I. Human-owned Git/index | No agent commit/stage/unstage/stash/reset/checkout/switch/ref mutation; worktree-only; report actual `HEAD`/index/worktree truthfully | PASS (planning made no Git mutations) |
| II. One canonical owner | The feature navigates to owners; it MUST NOT restate or duplicate mutable owner policy; any owner conflict is a finding at the owner, never an edit from here | PASS (ownership table in spec; plan navigates, does not copy) |
| III. Completion names object/gate | The decision object's state is named precisely: `AWAITING_HUMAN_DECISION` for an open package vs. a recorded human verdict as the outcome | PASS |
| IV. Evidence is not permission | Recommendations are not approval; `AWAITING_HUMAN_DECISION` is not completion; no state is described with stronger vocabulary | PASS |
| V. Evidence belongs to evaluated state | Every disposition cites the evaluated state; audit evidence carries its own `stateId`/version; no floating | PASS |
| VI. No fake green | `INSUFFICIENT_EVIDENCE` is first-class; blocked/missing evidence is never upgraded to REMOVE/KEEP | PASS |
| VII. Domain vs reusable | Framework-reuse candidates keep BeautyQ policy at BeautyQ owners; no domain policy pushed into a generic layer | PASS |
| VIII. Proposals are not architecture | Proposal stays attributed/non-normative; no proposal diagnosis becomes project fact; accepted limits are not backlog | PASS |
| IX. Minimal governance | Single decision record; no new ledger/pass/bundle/fingerprint; reuse existing owners and audit evidence | PASS |
| X. Rationale survives retirement | Any REMOVE/SIMPLIFY recommendation must name the surviving owner of material rationale before deletion is even separately authorized | PASS (encoded in the change-oriented gate) |
| XI. Truthful validation scope | Validation claims state command/scope/state; focused/lightweight checks are not presented as product proof | PASS |
| XII. Smallest coherent change | Plan scope is the decision artifact set only; no opportunistic refactor/cleanup/framework work | PASS |

No violations. Complexity Tracking is therefore empty.

## Project Structure

### Documentation (this feature)

```text
specs/004-beautyq-post-closeout-simplification-decision/
├── spec.md              # decision contract (existing)
├── plan.md              # This file (/speckit.plan output)
├── research.md          # Phase 0 output: source/owner reconciliation + bounded evidence questions
├── data-model.md        # Phase 1 output: candidate/assessment/recommendation/verdict record shapes
├── quickstart.md        # Phase 1 output: how decision execution is run/reviewed + human verdict boundary
└── checklists/
    └── requirements.md  # existing specification-quality checklist

# Execution-phase output (defined here, produced later — NOT created by /speckit.plan):
#   decision-record.md   # single canonical durable decision record (recommendations + open state + verdict slot)
```

No `contracts/` tree is created: this feature exposes no externally consumed interface. `/speckit.plan`
itself does not create `tasks.md`; a later, real user/operator `/speckit.tasks` invocation may create the
canonical feature-local `tasks.md` for this feature. Decision execution follows those accepted tasks but
still creates no source/build/test/runtime/normative-owner changes. The execution phase MUST NOT create
dozens of per-candidate durable files; it prefers **one** canonical decision record with per-candidate
records embedded (see data-model.md).

### Source Code

None. This feature writes no source. From a structure standpoint it is a single `specs/004-.../`
documentation directory; no repository source tree is touched.

**Structure Decision**: Documentation/decision-only layout under `specs/004-.../`. The only durable
execution artifact is `decision-record.md`; any throwaway mapping notes used to gather evidence are not
retained as durable artifacts.

## Decision Workflow Design

The execution workflow mirrors the plan phases but stays decision-only.

### Phase 0 — Source/owner reconciliation (research.md)

Research is **planning-time** work that preserves the reconciliation already performed and the bounded
evidence questions execution must still answer. It MUST distinguish five evidence classes and never
merge them:

- **A. Proposal claims** — attributed to the proposal; never current fact.
- **B. Current normative obligations** — owned by the operations runbook and technical specification.
- **C. Current executable/source reality** — established by read-only inspection at the evaluated state.
- **D. Completed 003 audit evidence** — `WELL_USED`/`BEAUTYQ_SPECIFIC`/`INDETERMINATE` labels and the
  single `R-01` recommendation, consumed as evidence, not permission.
- **E. Unresolved evidence** — questions execution must answer (e.g., Qdrant marginal value, trace
  reachability, module-boundary ownership).

Research resolves no candidate disposition. It records candidate **families**, their originating proposal
section (if any), and the bounded evidence each family needs.

### Phase 1 — Design & record shapes (data-model.md, quickstart.md)

Defines the canonical record shapes (candidate, assessment, recommendation, retained obligation,
human verdict, decision record) and their invariants, plus how execution is run and reviewed. It is a
validation/decision guide, not an implementation runbook.

## Candidate Inventory Derivation (planning-time families; no dispositions assigned)

Candidate families below are **planning-time navigation**, not final decision units. Execution MAY split,
merge, or reject-as-decision-unit any family when evidence shows the proposal grouped the wrong mechanics,
while preserving traceability to the originating proposal section when one exists (spec FR-003/FR-004).

| Family (provisional) | Originating proposal section | Planning-time evidence status | Bounded evidence execution must collect |
| --- | --- | --- | --- |
| F-QDRANT — Qdrant marginal product value | §2.1 | Managed Qdrant path exists; safety/integrity proofs exist; **no measured baseline-vs-full relevance delta** (proposal states this) | A predeclared product-value comparison: user-visible metric/ordered metric vector, cutoffs/slices, minimum improvement, max regression budget, forbidden-hit/hard-constraint/baseline-preservation/degradation stop conditions; same revision/snapshot/ES generation/corpus/requests/page policy in baseline-only vs Required/FullSearch. If unavailable → `INSUFFICIENT_EVIDENCE`, owner = product owner + eval owner (see "Qdrant evidence seam") |
| F-TRACE — Trace-only / duplicated presentation owners | §2.2 | `PlanIdentityTrace`, `BeautyQSearchPlanCompilationTrace`, `BeautyQCandidatePlanTrace` exist; observed consumers are their own specs (plus a scaladoc reference). `PlannedAlgebraTrace` is consumed by response projection and is **not** a deletion candidate | Reachability evidence per mechanism: does any operator/response/artifact/diagnostic consumer exist outside its own tests/docs? Includes the request/intent trace views made unreachable by removals |
| F-PROJECT — Project consolidation | §2.3 | Ten Gen2 sbt projects exist (`search-gen2-{contract,core,transport,elasticsearch,qdrant,eval}` and `beautyq-search-gen2-{contract,materialization,wiring,eval}`); former exact-DAG/firewall machinery already collapsed | Per-project: what current compilation, dependency, runtime, package-ownership, or evaluation boundary does it enforce? What breaks/unowns if merged (e.g. `search-gen2-transport` shared by two backends)? |
| F-TEST — Test consolidation around observable contracts | §2.4 | Tests exist; no coverage map is established yet | A coverage map before any deletion: each invariant's one primary owner; which tests differ only by rendering layer, private-wrapper construction boundary, duplicated fixture inventory, module-source scanning, or historical delivery phase; which prove firewall/build ownership or meaningful corpus cases |
| F-DOCS — Documentation reconciliation | §2.5 | Normative owners exist; proposal proposes rewriting current-state docs after code stabilizes | Whether the cost is documentary duplication only (documentation-only consolidation) or hides a source obligation; whether `SIMPLIFY_LOCALLY`/docs-only is sufficient rather than source change |
| F-AUDIT-DISC — 003 `R-01`/`C-05` `addDependency` docs/discoverability | none (audit-surfaced) | 003 `R-01`: a real `1.2.25` `addDependency` surface; `remedyCandidates = DOCS`; `WELL_USED`/`DISCOVERABLE`; docs-only, Medium confidence | Whether this is a genuine current-complexity simplification decision unit at all (execution MAY reject-as-decision-unit) or evidence feeding F-DOCS/FR-012; value is a judgment, not measured demand |

Additional notes binding the families:

- **F-TRACE joins 003 `C-20`.** `C-20` (response projection and diagnostic traces) is the single
  **`INDETERMINATE`** 003 candidate. Its unresolved question (does an exact-`1.2.25` generic domain-result
  trace/render surface exist, or only DI-graph rendering?) MUST remain unresolved where relevant; the
  decision MUST NOT silently resolve `C-20` or convert its label into a disposition.
- **F-PROJECT and F-TEST stay separate evidence questions** unless source truth proves they share one
  coherent obligation. Source/build dependency ownership and compiler-visible boundaries are distinct
  from test-only/firewall proof value; a smaller module count is not automatically simpler if it destroys
  a useful compiler/build ownership boundary.
- **Candidate families already resolved by the authorized pre-004 cleanup are out of scope** and MUST NOT
  be resurrected from the old proposal merely because the proposal still lists them.

## Canonical Candidate Record (satisfies all twelve spec dimensions)

One record shape is used per **final candidate** (fully specified in `data-model.md`). Every final
candidate MUST answer all twelve dimensions and receive exactly one disposition. A provisional
inventory/derivation unit may instead be split, merged, or **rejected as a decision unit** with its basis
and traceability recorded; a rejected provisional unit is not a final candidate and receives **no**
disposition (`INSUFFICIENT_EVIDENCE` remains the disposition for a real candidate lacking evidence):

1. current obligation served; 2. whether it still exists at the evaluated state; 3. demonstrated
maintenance/complexity cost; 4. evidence of product/operator/quality/risk value; 5. relevant 003
classification/evidence (if any); 6. what breaks/becomes unowned if removed; 7. whether docs-only
consolidation is sufficient; 8. whether reuse of an EXISTING framework primitive is preferable; 9. whether
BeautyQ/repository-local retention is preferable; 10. recommended disposition; 11. confidence/uncertainty;
12. explicit human-approval requirement.

### Disposition vocabulary (exact, closed set)

`KEEP` · `SIMPLIFY_LOCALLY` · `REPLACE_WITH_EXISTING_FRAMEWORK` · `FRAMEWORK_FOLLOWUP_REQUIRED` · `REMOVE` ·
`INSUFFICIENT_EVIDENCE`. No additional states are invented. `KEEP` and `INSUFFICIENT_EVIDENCE` are
first-class; `INSUFFICIENT_EVIDENCE` MUST name the missing evidence and its owner and is a valid result,
not a failed run.

### Change-oriented disposition gate

Every disposition other than `KEEP` / `INSUFFICIENT_EVIDENCE` MUST explicitly record:

- surviving public/compatibility contracts;
- proof/validation ownership;
- safety properties;
- operational obligations (per current owners);
- evidence obligations where still current;
- what breaks or becomes unowned if the mechanism is removed;
- the replacement owner where applicable;
- whether rollback/operational implications would matter in a **separately authorized** implementation.

These are assessment fields, **not** implementation tasks under 004.

## Feature 003 Integration

The plan defines a direct, economic join from a 004 candidate to relevant 003 candidate(s)/evidence when a
real mechanic match exists. Execution MUST NOT re-run the whole framework audit; it reopens lower-level 003
artifacts only for exact evidence on a specific candidate.

Discipline encoded:

- `WELL_USED` does **not** automatically mean `KEEP`.
- `BEAUTYQ_SPECIFIC` does **not** automatically mean `KEEP`.
- Absence of `UNDERUSED` does **not** prohibit local simplification for reasons unrelated to framework
  leverage.
- The single unresolved 003 candidate (`C-20`) stays uncertain where relevant.
- The 003 docs-only framework recommendation (`R-01`) is **not** implementation permission.
- 004 asks a broader question than 003: *can this complexity safely be removed/simplified now, and is
  doing so worthwhile?*

## Qdrant Product-Value Decision (real evidence seam)

A real evidence seam is planned and MUST be honored. For `F-QDRANT`, the assessment may conclude
`INSUFFICIENT_EVIDENCE` naming the smallest evidence needed to resolve the marginal value/risk question:
the proposal's own predeclared baseline-vs-full product-value comparison (proposal §2.1, current lines 47–54). Planning does not run it
and does not invent a product-value experiment beyond what current owners/source already require.

The following MUST NOT substitute for demonstrated product/user value: architectural elegance, framework
availability, historical implementation effort, or the mere existence of semantic-search machinery.
`KEEP`, `DELETE`, and `BLOCKED/INSUFFICIENT_EVIDENCE` are all legitimate outcomes; append-only activity is
never reinterpreted as product value.

## Project / Test Consolidation Discipline

`F-PROJECT` and `F-TEST` are evaluated as **separate** evidence questions unless source truth proves one
coherent obligation. The assessment distinguishes: source/build dependency ownership; compiler-visible
architecture boundaries; test-only/firewall proof value; duplicate documentary ceremony; actual
runtime/product value. Fewer modules/tests is not assumed simpler, and an existing test is not retained
merely because it exists.

## Parallelism / Economy

- Cheap independent evidence mapping MAY run in parallel only when writers/artifacts are truly independent.
- Central candidate adjudication is **serialized**.
- No dozens of per-candidate durable files; one canonical decision record.
- Banned inside this feature: digest/freeze chains; pass identities; evidence fingerprints; baseline
  ceremony; review bundles; Git revision plumbing.

## Human Decision Boundary (explicit end state)

1. Execution produces an evidence-backed recommendation package (`decision-record.md`).
2. The coordinator reviews it.
3. While no verdict exists, the record state is `AWAITING_HUMAN_DECISION` and the decision object is OPEN.
4. The human explicitly **approves, rejects, defers, or narrows** (per candidate or whole package).
5. That verdict is recorded **verbatim** as the 004 decision outcome; it may close the decision object.
   After closure the verdict is immutable historical evidence bound to the state actually decided; later
   source drift does not rewrite or reopen it, and using a recommendation against a materially changed
   state requires a new evaluation against that state.
6. **No implementation starts under 004** — even an approving verdict only informs a separately scoped,
   explicitly human-authorized task (spec FR-009). No model/agent may convert a recommendation into a
   human verdict.

## Validation of the Plan

Lightweight consistency checks only (no product suite). See `quickstart.md` for the execution/review
checklist. Plan-level invariants to confirm before closeout:

- plan stays decision-only; no source/build/runtime implementation is authorized;
- proposal remains attributed/non-normative; current owners/source remain the authority for present
  obligations;
- 003 consumed as evidence, never permission or gate;
- exact six-way disposition vocabulary preserved;
- all twelve assessment dimensions executable;
- change-oriented dispositions preserve obligations and break/unowned analysis;
- `INSUFFICIENT_EVIDENCE` remains first-class;
- Qdrant product-value uncertainty has a real evidence path;
- project/test consolidation are not conflated without evidence;
- human verdict remains separate and explicit;
- `/speckit.plan` created no `tasks.md` (a later real `/speckit.tasks` may create the canonical
  feature-local `tasks.md`); no implementation; no Git/index/ref mutation.

## Complexity Tracking

> No Constitution Check violations. Nothing to justify.

| Violation | Why Needed | Simpler Alternative Rejected Because |
| --- | --- | --- |
| (none) | — | — |
