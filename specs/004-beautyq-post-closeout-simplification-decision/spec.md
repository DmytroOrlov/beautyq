# Feature Specification: BeautyQ Post-Closeout Simplification Decision

**Feature Branch**: `004-beautyq-post-closeout-simplification-decision` (Spec Kit feature directory; no Git branch is created or switched — ref and index mutation is human-owned under Constitution Principle I)

**Created**: 2026-09-08

**Status**: Draft — durable **decision contract**. Specification only. This document authorizes no source, build, or architecture change of any kind.

**Governing Constitution**: `.specify/memory/constitution.md` — BeautyQ Project Delivery Constitution v2.0.0 (effective 2026-09-08). Work under this feature re-derives the governing state at each phase and never trusts a prompt-supplied revision. The principles most load-bearing for this decision are I (human-owned Git/index), II (one canonical owner — this spec navigates to owners, it does not restate them), III (completion names its own object), IV (evidence is not permission — neither the proposal nor the audit authorizes simplification), VIII (proposals are not accepted architecture), and XII (smallest coherent change — a decision artifact, not a refactor).

**Input**: User description (abridged): "Specify a DECISION feature, `beautyq-post-closeout-simplification-decision` (NOT an implementation feature). Evaluate the still-unresolved post-closeout simplification proposal against the current architecture, operational, and evidence obligations, ownership boundaries, and demonstrated costs/value — per candidate, individually, consulting the Distage/Izumi leverage and framework-opportunity audit where it has completed findings. Produce an explicit human decision. Dispositions may include KEEP and INSUFFICIENT_EVIDENCE; nothing is pre-approved and no implementation is pre-committed. This decision feature MUST NOT modify production architecture."

## Overview and Decision Object

This feature is the **simplification decision** that considers what complexity BeautyQ can safely remove now, why, and what must remain. Its object is a **human decision**, not the BeautyQ architecture itself. Its focus is the **still-unresolved** candidates — in particular the Qdrant marginal product-value decision and possible project/test consolidation — and it treats any candidate already resolved by an authorized cleanup as out of scope.

The **evidence under evaluation** is the [post-Q2 simplification proposal](../../docs/gen2/BEAUTYQ_SEARCH_GEN2_SIMPLIFICATION_PLAN.md) (status *proposed*; explicitly not an approved implementation plan). This feature treats the proposal as **attributed criticism plus a proposed alternative** — nothing more. It is NOT proof that:

- the current architecture is overengineered (a constitutionally excluded claim — Non-Axioms);
- the proof/evaluation machinery exceeded its justified need;
- framework extraction is required;
- deletion is beneficial;
- product value is absent.

The decision phase MUST **test** each of these questions against evidence rather than assume any of them. The proposal's own review corrections are constraints the proposal itself accepts, and the decision phase inherits them as part of the proposal's content — not as independently established project fact until this feature's own evaluation re-derives them.

**This specification produces a decision contract only.** No candidate is evaluated here; the evaluation is the execution phase. No implementation of any disposition happens under this feature under any outcome, including full approval of every candidate (see Hard Gate to Implementation).

**Decision authority boundary.** The agent/coordinator role **prepares and reviews** the evidence-backed decision package and **may recommend** dispositions — the final candidate analysis and dispositions are **recommendations presented for human decision, not self-authorizing decisions**. Only the **human decision-maker/product owner** approves, rejects, defers, or narrows the recommendation. The coordinator cannot manufacture human approval: while the evidence-backed package is ready but no human verdict exists, `AWAITING_HUMAN_DECISION` is the correct **OPEN / WAITING state** — the analysis package may be complete and presentable, but the feature's **human decision object is not complete**. Approve, reject, defer, and narrow are all valid human verdicts; once such a verdict is recorded, the decision object may close with that verdict as its outcome — and even an approving verdict authorizes no implementation under this feature (FR-009).

## Framework-Audit Input (optional, non-blocking)

The Distage/Izumi leverage and framework-opportunity audit (`specs/003-beautyq-distage-izumi-leverage-audit`) is an **optional input** to this decision, not a gate. Where its completed findings are available, they inform the per-mechanic assessment (FR-012); where they are not, the affected candidate records `INSUFFICIENT_EVIDENCE` naming the missing research rather than inventing a classification.

There is no closeout, promotion, independent-verify, or other delivery precondition. This decision contract may exist and be reviewed on its own, and substantive execution may begin whenever the human chooses to invoke it. The decision evaluates the current source/architecture state it explicitly records as its baseline; if that state moves after evaluation, affected evidence is re-attributed per Principle V — findings bound to the earlier state stay attributed to it and are marked superseded, never silently reattributed.

Where "D1" appears in this specification, it means only the existing deferred second-production-domain initiative owned by the current planning documentation.

## Relationship to the Proposal and the Audit

| Input | Role in this decision | Authority it carries |
| --- | --- | --- |
| Post-Q2 simplification proposal | Attributed criticism and a proposed alternative; its unresolved candidate list seeds the candidate inventory | Non-normative. Never an approval, never current architecture. Its diagnoses stay attributed to it (Principle VIII). |
| Operations runbook / technical specification | Current obligations the decision evaluates candidates against | Normative current owners. The decision evaluates, never rewrites them (any owner update belongs to a later accepted feature). |
| Distage/Izumi leverage-audit findings (where available) | Per-mechanic classification (`WELL_USED`/`UNDERUSED`/`HARD_TO_DISCOVER`/`MISSING_GENERIC_PRIMITIVE`/`BEAUTYQ_SPECIFIC`) feeding candidate assessment | Upstream evidence; findings inform but **do not themselves authorize simplification** (Principles IV, VIII). |

The decision phase MAY extend the candidate inventory beyond the proposal's list when the current baseline or audit findings surface additional candidates, and MAY split, merge, or reject proposal milestones as decision units. It is not bound to the proposal's milestone numbering; it MUST track which proposal milestone (if any) each candidate came from for traceability.

## Ownership and Precedence

This contract navigates to current owners and does not duplicate their mutable content (Constitution Principle II):

| Fact needed | Canonical current owner |
| --- | --- |
| Current BeautyQ architecture, semantics, invariants, accepted limits | the Gen2 technical specification |
| Protected acceptance/verify procedures | the operations runbook |
| The deferred-but-unapproved simplification ideas under evaluation | the post-Q2 simplification proposal (non-normative here) |
| Framework-leverage classifications for BeautyQ-local mechanics | the Distage/Izumi leverage audit (`specs/003-beautyq-distage-izumi-leverage-audit`), where completed |
| Domain-versus-framework authoring boundary | the search-domain authoring principles and the new-domain onboarding guide |
| Agent Git/index and evidence-attribution rules | Constitution Principles I, IV, V, XI |

When this contract, an owner, and durable evidence disagree, the disagreement is a finding to resolve at its owner — never silently preferred, and never a reason to edit an owner from inside a decision feature.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Per-Candidate Individual Assessment (Priority: P1)

A maintainer receives a decision record in which **each simplification candidate is assessed individually** across a fixed set of dimensions — never as part of an all-or-nothing bundle acceptance or rejection of the proposal — and receives exactly one final disposition from a fixed set that includes "no change."

**Why this priority**: The proposal bundles structurally different questions (trace owners, project-graph consolidation, evaluation-ceremony reduction, Qdrant value) whose evidence bases differ. Accepting or rejecting the bundle would hide which specific mechanisms are justified. Per-candidate assessment is the core decision product.

**Independent Test**: For each candidate in the decision record, an auditor can find all twelve assessment dimensions completed (or the candidate explicitly rejected as a decision unit with its basis recorded), exactly one disposition, a stated confidence/uncertainty level, and a named human-approval requirement — and can follow the evidence references to the evaluated baseline and the audit finding cited where one exists.

**Acceptance Scenarios**:

1. **Given** a candidate, **When** it is assessed, **Then** the record covers, at minimum: the current obligation the mechanism serves; whether that obligation still exists at the evaluated baseline; the demonstrated maintenance/complexity cost; the evidence of value/risk; the relevant Distage/Izumi audit classification for the mechanism (where available); what breaks or becomes unowned if the mechanism is removed; whether documentation-only consolidation is sufficient; whether framework reuse (existing primitive) is preferable; whether BeautyQ-local retention is preferable; a recommendation; explicit confidence/uncertainty; and the human approval requirement.
2. **Given** two candidates with different evidence, **When** dispositions are assigned, **Then** they may differ (one KEEP, one REMOVE) without the record forcing a single verdict for the whole proposal — and the record explicitly does not treat proposal acceptance or rejection as a unit.
3. **Given** a candidate whose evidence is incomplete at decision time, **When** it is assessed, **Then** its disposition is `INSUFFICIENT_EVIDENCE` naming the missing evidence and its owner — never a guess dressed as a disposition.
4. **Given** a mechanism the audit classified as already correctly framework-served (`WELL_USED`), **When** a proposal milestone proposes deleting or rebuilding it, **Then** the assessment names the audit classification and evaluates whether the proposal's premise for that candidate holds.

---

### User Story 2 - Explicit Human Decision with "Nothing Removed Yet" as a Valid Outcome (Priority: P2)

A human decision-maker receives a decision record whose synthesis answers "what complexity can BeautyQ safely remove now, why, and what must remain?" — where a legitimate, first-class outcome is **"nothing should be removed yet"** (every candidate KEEP or INSUFFICIENT_EVIDENCE) — and where the agent/coordinator-prepared dispositions are **recommendations awaiting the human verdict**: while no verdict exists the decision object remains **OPEN** in the waiting state `AWAITING_HUMAN_DECISION` (the package may be complete; the decision is not); the human explicitly approves, rejects, defers, or narrows, and the recorded verdict is the decision outcome that may close the decision object.

**Why this priority**: The feature exists to produce a decision a human can act on or decline; forcing change would corrupt the decision.

**Independent Test**: Read only the synthesis: confirm it states per-candidate recommended dispositions, retained obligations, and the aggregate answer including a null-result path; confirm every change-oriented recommendation names its required human approval; confirm a package with no recorded human verdict leaves the decision object OPEN in state `AWAITING_HUMAN_DECISION` and none of its content reads as "already approved", "decided", or "implementation may now begin" (Principle IV).

**Acceptance Scenarios**:

1. **Given** all candidates assessed, **When** the synthesis is written, **Then** it states what can be removed now (if anything), why (evidence-based), and what must remain — and a no-removal outcome is recorded with equal standing, not as a failed run.
2. **Given** the agent/coordinator recommends a change-oriented disposition (SIMPLIFY_LOCALLY, REPLACE_WITH_EXISTING_FRAMEWORK, FRAMEWORK_FOLLOWUP_REQUIRED, REMOVE), **When** no human verdict exists yet, **Then** that candidate's state is a recommendation with the package in `AWAITING_HUMAN_DECISION` — nothing is human-approved, and no source change follows from this feature alone.
3. **Given** the human verdict exists (approve, reject, defer, or narrow — per candidate or for the whole package), **When** the record is finalized, **Then** the verdict is recorded verbatim as the decision outcome, each approved item names the separately accepted later implementation feature as its only route to any source change, and a deferral records the deferred state without starting any work.
4. **Given** a candidate receives FRAMEWORK_FOLLOWUP_REQUIRED and the human approves it, **When** recorded, **Then** it names the follow-up owner (documentation vs framework feature) and remains a recommendation for that separate future decision, not an opened work item (Principle VIII).

---

### Edge Cases

- The proposal document is amended after evaluation: the decision re-reads the proposal at its current state; divergence between proposal content assumed during evaluation and current proposal text is recorded as a finding at the proposal owner, not silently consumed.
- A proposal candidate contradicts a normative owner contract (e.g., proposes removing an obligation the technical specification states): the conflict is a decision finding to resolve at the owner; the decision does not treat the proposal as authority over the contract (Principle VIII).
- A candidate maps to multiple proposal milestones: assess it once as one decision unit with all milestone references recorded, or split it when evidence genuinely differs per portion.
- A candidate is not BeautyQ-local mechanics (e.g., Qdrant backend value): the audit classification field is recorded as not-applicable with basis, and the assessment proceeds on product-value evidence.
- Candidate evidence depends on an evaluation run not yet performed (e.g., the proposal's own predeclared Qdrant comparison): the disposition is `INSUFFICIENT_EVIDENCE` with the named missing run; this decision feature does not run evaluations.
- All candidates conclude KEEP/INSUFFICIENT_EVIDENCE: the synthesis records "nothing should be removed yet" as the valid final answer, with retained obligations stated.
- A disposition is approved by the human but a separate implementation feature does not yet exist: that is a valid CLOSED outcome of this feature — the recorded verdict names the required follow-up feature; nothing is implemented by or under this feature.
- The analysis package is ready but the human decision-maker is unavailable: the package may be completed and presented, but the decision object remains **OPEN** in the waiting state `AWAITING_HUMAN_DECISION` — absence of the human keeps the decision boundary open rather than fabricating completion, and no agent or coordinator state substitutes for the human verdict (Principle IV).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001** This feature is a **decision contract**. It MUST NOT modify production architecture, source, build definition, tests, runtime behavior, evaluation corpora/thresholds, or any normative owner document. Its only outputs are the decision artifacts and, where its own record requires, navigation updates in this feature's own directory.
- **FR-002** There is no blocking delivery gate: substantive decision execution may begin against the explicitly recorded current state whenever the human invokes it. The decision record MUST name the source/architecture state it evaluated.
- **FR-003** The decision MUST evaluate the proposal **per candidate, individually**; it MUST NOT accept or reject the proposal as one bundle, and MUST NOT let one candidate's verdict imply another's.
- **FR-004** Every assessed candidate MUST receive all twelve assessment dimensions (US-1 scenario 1) or be explicitly rejected as a decision unit with its basis recorded; every candidate MUST then receive exactly one disposition.
- **FR-005** The disposition vocabulary is exactly: `KEEP`, `SIMPLIFY_LOCALLY`, `REPLACE_WITH_EXISTING_FRAMEWORK`, `FRAMEWORK_FOLLOWUP_REQUIRED`, `REMOVE`, `INSUFFICIENT_EVIDENCE`. `KEEP` and `INSUFFICIENT_EVIDENCE` are valid, first-class outcomes with equal standing; the decision MUST NOT force any candidate to change and a no-removal outcome is explicitly valid.
- **FR-006** The decision MUST test, per candidate and from evidence, whether the current architecture is overengineered for that mechanism, whether its proof machinery exceeded justified need, whether framework extraction is warranted, and whether removal is beneficial — and MUST NOT treat the proposal's diagnosis as established project fact for any of these (Principle VIII; Non-Axioms).
- **FR-007** Every disposition MUST cite the evaluated source/architecture baseline and, where available, the audit finding it uses; dispositions MUST NOT float to a state other than the one evaluated (Principle V).
- **FR-008** A change-oriented disposition (anything other than KEEP/INSUFFICIENT_EVIDENCE) MUST state the retained obligations that survive it — preserved public contracts, proof owners, safety properties, and operational obligations per the current owners — and what breaks or becomes unowned if removed, explicitly.
- **FR-009** This feature MUST be the **hard gate to implementation**: no implementation follows from any disposition under this feature, including a human-approved one. Any approved implementation change requires a separate later implementation feature with, at minimum: the exact accepted candidates; retained obligations; migration requirements; validation requirements; and rollback/operational implications where applicable. Human approval of the decision is required before such a feature begins.
- **FR-010** The decision boundary is explicit and role-separated: the agent/coordinator prepares and reviews the evidence-backed decision package and may recommend dispositions, but every disposition is a **recommendation presented for human decision**, never a self-authorizing outcome. Only the human decision-maker/product owner approves, rejects, defers, or narrows. While the package is ready but no human verdict exists, the decision object MUST remain OPEN in the waiting state `AWAITING_HUMAN_DECISION`; the coordinator MUST NOT record, phrase, or imply approval. Approve/reject/defer/narrow are all valid verdicts; a recorded verdict is the decision outcome and may close the decision object. Even a human-approved disposition authorizes NO implementation under this feature (FR-009); no outcome may be phrased as self-executing, "already approved", or "implementation may now begin" (Principle IV).
- **FR-011** Documentation-only consolidation MUST be available as a sufficient outcome for any candidate where the mechanism's cost is documentary duplication rather than source complexity; the assessment must actually test this option rather than default to source change.
- **FR-012** Framework-reuse preference MUST be evaluated against the audit's classifications where they are available: where the audit found an existing framework surface (`WELL_USED`/`UNDERUSED`/`HARD_TO_DISCOVER`), the decision MUST consider `REPLACE_WITH_EXISTING_FRAMEWORK` or a documentation/discoverability remedy before `KEEP`-with-rebuild or `REMOVE`; where the audit found `MISSING_GENERIC_PRIMITIVE`, `FRAMEWORK_FOLLOWUP_REQUIRED` names the separate framework follow-up as its own future decision. A `MISSING_GENERIC_PRIMITIVE`-backed follow-up is a recommendation awaiting its own decision, never an opened work item.
- **FR-013** The decision record MUST include the synthesis answering: what complexity can BeautyQ safely remove now, why, and what must remain — with "nothing should be removed yet" as a valid aggregate answer.
- **FR-014** All work under this feature MUST be worktree-only: no agent commit, stage, unstage, stash, reset, restore, checkout/switch, history rewrite, or ref mutation; existing human-created index state is preserved exactly as found; every report states the actual `HEAD`/index/worktree state truthfully (Principle I).
- **FR-015** This `/speckit.specify` invocation stops at the specification. It MUST NOT run or produce plan, tasks, implementation, analysis, or any candidate evaluation now.

### Key Entities

- **Candidate (decision unit)**: one simplifiable mechanism or coherent mechanism group, individually assessed; sourced from the proposal (tracked to its milestone) or surfaced by the evaluated baseline/audit. Receives exactly one disposition.
- **Proposal**: the post-Q2 simplification proposal — attributed criticism and a proposed alternative; non-normative input, never authority.
- **Evaluated baseline**: the exact source/architecture state the decision records as its attribution anchor (Principle V).
- **Audit classification**: the Distage/Izumi audit's five-way label for a BeautyQ-local mechanic (`WELL_USED`/`UNDERUSED`/`HARD_TO_DISCOVER`/`MISSING_GENERIC_PRIMITIVE`/`BEAUTYQ_SPECIFIC`), consumed as evidence where available, never as permission.
- **Retained obligation**: a current public/runtime/operational/evidence obligation that a disposition explicitly preserves or explicitly supersedes with its replacement owner.
- **Disposition**: one of the six fixed states per candidate (`KEEP`/`SIMPLIFY_LOCALLY`/`REPLACE_WITH_EXISTING_FRAMEWORK`/`FRAMEWORK_FOLLOWUP_REQUIRED`/`REMOVE`/`INSUFFICIENT_EVIDENCE`), with confidence/uncertainty — as produced by the agent/coordinator, always a **recommendation** until the human verdict exists; the verdict, not the recommendation, is the decision outcome.
- **Decision record**: the execution output — per-candidate assessments, recommended dispositions, synthesis, human-approval requirements, and its overall state: **OPEN** in `AWAITING_HUMAN_DECISION` while no human verdict is recorded; the recorded verdict (approve/reject/defer/narrow) thereafter, which may close the decision object with that verdict as its outcome.
- **Human decision-maker/product owner**: the role that holds the decision verdict — approves, rejects, defers, or narrows the recommendations. Distinct from the coordinator role, which prepares and reviews the package and may recommend, but cannot manufacture approval.

## Failure and Blocked Semantics

- **`INSUFFICIENT_EVIDENCE`** is a disposition, not a failure: the affected candidate records the exact missing evidence (e.g., a not-yet-run product-value comparison) and its owner; blocked or missing evidence is never recorded as REMOVE, KEEP-with-certainty, or any stronger state (Principle IV, VI).
- **Proposal/owner conflict** → a decision finding recorded at the owner; the proposal never wins by default, and the owner is never edited by this feature.
- **Source-state drift after evaluation** → affected evidence marked superseded, affected candidates return to evaluation; dispositions are never silently reattributed (Principle V).
- **Approved disposition without a follow-up feature** → valid closed outcome; the recorded verdict names the required separate implementation feature and stops (FR-009).
- **Human unavailable at decision time** → the package is complete and presentable but the decision object remains OPEN in `AWAITING_HUMAN_DECISION`; absence of the human keeps the boundary open — no coordinator/agent state is recorded as approval or completion (Principle IV).
- **Human declines or defers the decision** → the human's verdict (including "reject everything", "docs-only", "defer", or a narrowed subset) is recorded verbatim as the decision outcome; the decision record supports the verdict rather than lobbying past it, and no implementation starts under this feature in any of these cases.

## Scope Boundaries and Non-Goals

This feature does **NOT**:

- modify production architecture, source, build definition, tests, runtime, or evaluation corpora/thresholds — even for approved candidates (FR-009);
- implement any proposal milestone or its contents, including docs-only rewrites of normative owner documents;
- begin a second production domain (D1) or pull in its deferred prerequisites as requirements;
- perform unrelated cleanup, opportunistic refactoring, or new product requirements;
- implement generic framework functionality (framework extraction belongs to a separate framework follow-up feature where `FRAMEWORK_FOLLOWUP_REQUIRED` is approved);
- re-open the completed documentation-history review feature (`001-beautyq-doc-history-review`, frozen under its own constitution version) or re-litigate its findings;
- treat accepted limits or explicit non-goals (of the owners or of the proposal) as backlog to implement (Principle VIII);
- change the constitution, or force every candidate to a change disposition.

In scope: authoring this decision contract; the decision phase that assembles the candidate inventory from the proposal plus current-baseline/audit evidence, assesses each candidate per FR-004, assigns dispositions per FR-005, and produces the synthesis and named human-approval requirements per FR-013/FR-010.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of decision-phase actions record the source/architecture state evaluated; 0 candidates are assessed against an unrecorded baseline.
- **SC-002**: 100% of assessed candidates carry all twelve assessment dimensions (or an explicit, basis-recorded rejection as a decision unit) and exactly one disposition; 0 candidates are decided as part of an unexamined bundle.
- **SC-003**: 0 proposal diagnoses are restated as established project fact anywhere in the decision record; every cost/overengineering/deletion-benefit claim traces to baseline evidence or is attributed to the proposal as its claim (auditable by reference check).
- **SC-004**: 0 change-oriented dispositions lack: explicit retained obligations, an explicit break/unowned-on-removal analysis, and a named human approval requirement; 100% name the separate implementation feature as the only route to any source change.
- **SC-005**: 0 source, build, test, runtime, or normative-owner-document modifications are attributable to this feature; the worktree change set is decision artifacts only; 0 agent commits, ref changes, or index/stash mutations occur.
- **SC-006**: The synthesis answers the standing question (what can be removed now, why, what must remain) and records KEEP/INSUFFICIENT_EVIDENCE as valid aggregate outcomes; a no-removal result is presentable without being flagged as an incomplete run.
- **SC-007**: 100% of dispositions cite the evaluated baseline state and, where applicable, the audit finding; 0 dispositions float onto an unevaluated state (Principle V).
- **SC-008**: 100% of agent/coordinator-prepared dispositions are presented as recommendations; while no human verdict exists the decision object is OPEN in `AWAITING_HUMAN_DECISION` (0 fabricated completions); 0 approvals, verdicts, or decision outcomes are recorded from any non-human source; 100% of recorded human verdicts (approve/reject/defer/narrow) appear verbatim as the outcome, may close the decision object, and authorize 0 implementation actions under this feature.

## Assumptions

- The proposal under evaluation is the current [post-Q2 simplification proposal](../../docs/gen2/BEAUTYQ_SEARCH_GEN2_SIMPLIFICATION_PLAN.md); candidates already resolved by an authorized cleanup are out of scope for this decision.
- The Distage/Izumi leverage audit (`specs/003-beautyq-distage-izumi-leverage-audit`) is an optional, non-blocking input; where its findings are unavailable, the affected candidate is `INSUFFICIENT_EVIDENCE`, not guessed.
- The candidate inventory at execution will be derived from the proposal's still-unresolved candidates plus any additional candidates the current baseline and audit findings surface; the exact candidate list is a decision-phase product, not fixed here.
- Role separation at the decision boundary: the agent/coordinator prepares and reviews the evidence-backed decision package and may recommend dispositions, but cannot manufacture human approval; the **human decision-maker/product owner** — a distinct role — explicitly approves, rejects, defers, or narrows, and the verdict is recorded as the decision outcome. While the package is ready but no verdict exists, the decision object stays OPEN in `AWAITING_HUMAN_DECISION`; waiting on the human is an expected, reportable state — not a failure, and never a fabricated completion. Even an approved verdict authorizes no implementation under this feature (FR-009).
- Repository-consistent feature numbering places this feature at `004`.
