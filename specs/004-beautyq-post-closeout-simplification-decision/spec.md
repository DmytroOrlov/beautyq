# Feature Specification: BeautyQ Post-Closeout Simplification Decision

**Feature Branch**: `004-beautyq-post-closeout-simplification-decision` (Spec Kit feature directory; no Git branch is created or switched — ref and index mutation is human-owned under Constitution Principle I)

**Created**: 2026-09-08

**Status**: Draft — durable **decision contract**. Specification only; substantive **decision execution is gated** (see Execution Dependency Gates, mandatory and blocking). This document authorizes no source, build, or architecture change of any kind.

**Governing Constitution**: `.specify/memory/constitution.md` — BeautyQ Project Delivery Constitution v2.0.0 (effective 2026-09-08). Work under this feature re-derives the governing state at each phase and never trusts a prompt-supplied revision. The principles most load-bearing for this decision are I (human-owned Git/index), II (one canonical owner — this spec navigates to owners, it does not restate them), III (completion names its object and gate — both dependency gates name theirs), IV (evidence is not permission — neither the proposal, the closeout evidence, nor the audit authorizes simplification), VIII (proposals are not accepted architecture), and XII (smallest coherent change — a decision artifact, not a refactor).

**Input**: User description (abridged): "`speckit.specify` for a DECISION feature, `beautyq-post-closeout-simplification-decision` (NOT an implementation feature). Execution gated on BOTH: (1) verified completion of the current accepted BeautyQ delivery closeout; (2) completion of the separate Distage/Izumi leverage and framework-opportunity audit. Evaluate the post-closeout simplification proposal against the verified accepted baseline, current architecture/operational/evidence obligations, audit findings, ownership boundaries, and demonstrated costs/value — per candidate, individually. Produce an explicit human decision. Dispositions may include KEEP and INSUFFICIENT_EVIDENCE; nothing is pre-approved and no implementation is pre-committed. This decision feature MUST NOT modify production architecture."

## Overview and Decision Object

This feature is the **post-closeout simplification decision** that feature `003-beautyq-distage-izumi-leverage-audit` named as the primary consumer of its findings. Its object is not the BeautyQ architecture itself but a **human decision**: what complexity can BeautyQ safely remove now, why, and what must remain — after the accepted delivery closeout is verified complete and the framework-leverage audit is complete.

The **evidence under evaluation** is the [post-Q2 simplification proposal](../../docs/gen2/BEAUTYQ_SEARCH_GEN2_SIMPLIFICATION_PLAN.md) (status *proposed*; explicitly not an approved implementation plan). This feature treats the proposal as **attributed criticism plus a proposed alternative** — nothing more. It is NOT proof that:

- the current architecture is overengineered (a constitutionally excluded claim — Non-Axioms);
- the proof/evaluation machinery exceeded its justified need;
- framework extraction is required;
- deletion is beneficial;
- product value is absent.

The decision phase MUST **test** each of these questions against evidence rather than assume any of them. The proposal's own review corrections (§2 of that document — neutral fixtures are valid evidence; the module firewall protects a real negative property; Qdrant has proved operation and no-harm, not marginal product value; cutover owners mix history with live proofs) are constraints the proposal itself accepts, and the decision phase inherits them as part of the proposal's content — not as independently established project fact until this feature's own evaluation re-derives them.

**This specification produces a decision contract only.** No candidate is evaluated here; the evaluation is the gated execution phase. No implementation of any disposition happens under this feature under any outcome, including full approval of every candidate (see Hard Gate to Implementation).

**Decision authority boundary.** The agent/coordinator role **prepares and reviews** the evidence-backed decision package and **may recommend** dispositions — the final candidate analysis and dispositions are **recommendations presented for human decision, not self-authorizing decisions**. Only the **human decision-maker/product owner** approves, rejects, defers, or narrows the recommendation. The coordinator cannot manufacture human approval: while the evidence-backed package is ready but no human verdict exists, `AWAITING_HUMAN_DECISION` is the correct **OPEN / WAITING state** — the analysis package may be complete and presentable, but the feature's **human decision object is not complete**. Approve, reject, defer, and narrow are all valid human verdicts; once such a verdict is recorded, the decision object may close with that verdict as its outcome — and even an approving verdict authorizes no implementation under this feature (FR-009).

## Execution Dependency Gates (mandatory, blocking)

**Substantive decision execution begins only after BOTH dependencies are satisfied:**

1. **DEP-CLOSEOUT — Verified accepted closeout.** The current accepted BeautyQ delivery closeout reaches its required verified closeout boundary — the verified closeout boundary owned by `specs/002-beautyq-q2-closeout` and procedured by the [operations runbook](../../docs/gen2/BEAUTYQ_SEARCH_GEN2_OPERATIONS.md) (Q2 documentation closeout gated on green independent verify against the committed promoted identity, per that feature's own ladder; the [post-cutover plan](../../docs/gen2/BEAUTYQ_SEARCH_GEN2_POST_CUTOVER_PLAN.md) is historical source/context for the accepted boundary definition, not the gate owner). Reason: the decision must evaluate the **verified accepted baseline**, not a delivery state still mid-acceptance; evidence bound to an open acceptance boundary cannot anchor a keep/remove judgment.

2. **DEP-FRAMEWORK-AUDIT — Completed framework audit.** The Distage/Izumi leverage and framework-opportunity audit (`specs/003-beautyq-distage-izumi-leverage-audit`) reaches its own completion condition (its synthesis answering its standing questions, on its own terms — Principle III). Reason: simplification must not delete, duplicate, or preserve BeautyQ-local mechanics before determining whether each mechanic already corresponds to a framework primitive, exposes a discoverability problem, should become generic framework functionality, or is correctly domain-specific. Deciding candidates without the audit would force exactly the blind classification this sequencing exists to prevent.

Gate rules for this contract:

- **Naming boundary**: `DEP-CLOSEOUT` and `DEP-FRAMEWORK-AUDIT` name THIS feature's two execution dependencies only. They are unrelated to, and MUST NOT be confused with, the existing BeautyQ deferred second-production-domain initiative "D1" owned by the current planning documentation. Where "D1" appears in this specification, it means only that deferred initiative.

- The specification **may exist and be reviewed before** either gate closes; authoring this decision contract is not blocked by it.
- No decision-phase activity — candidate evaluation, evidence assembly, disposition recommendation, or decision-record drafting — may begin until **both** gates are independently recorded as satisfied.
- Opening a gate is **evidenced, not asserted**: execution requires a durable reference to the verified closeout state (that boundary's own evidence) and to the audit's completed synthesis (its own artifact). Absent either, the only valid output is `DECISION — GATE NOT YET SATISFIED`, naming the unfinished dependency, its boundary, and its owner (Constitution Principles III, IV, V).
- Neither gate is performed by this feature. Finishing the closeout or running the audit are **out of scope** (see Non-Goals).
- If the verified accepted baseline changes after both gates open, the affected evidence is re-attributed per Principle V: findings bound to the earlier baseline stay attributed to it and are marked superseded, and affected candidates return to evaluation; nothing is silently reattributed.

## Relationship to the Proposal and the Audit

| Input | Role in this decision | Authority it carries |
| --- | --- | --- |
| Post-Q2 simplification proposal | Attributed criticism and a proposed alternative; its candidate list seeds the candidate inventory | Non-normative. Never an approval, never current architecture. Its diagnoses stay attributed to it (Principle VIII). |
| Operations runbook / technical specification | Current obligations the decision evaluates candidates against | Normative current owners. The decision evaluates, never rewrites them (any owner update belongs to a later accepted feature). |
| Post-cutover plan | Historical accepted boundary definition and pre-002 delivery context | Transitional source/history, not a current owner. |
| Distage/Izumi leverage-audit findings | Per-mechanic classification (`WELL_USED`/`UNDERUSED`/`HARD_TO_DISCOVER`/`MISSING_GENERIC_PRIMITIVE`/`BEAUTYQ_SPECIFIC`) feeding candidate assessment | Upstream evidence; findings inform but **do not themselves authorize simplification** (Principles IV, VIII). |
| Accepted-closeout verification record | Anchors the baseline identity the decision evaluates | Evidence identity, not permission. |

The decision phase MAY extend the candidate inventory beyond the proposal's list when the verified baseline or audit findings surface additional candidates, and MAY split, merge, or reject proposal milestones as decision units. It is not bound to the proposal's milestone numbering; it MUST track which proposal milestone (if any) each candidate came from for traceability.

## Ownership and Precedence

This contract navigates to current owners and does not duplicate their mutable content (Constitution Principle II):

| Fact needed | Canonical current owner |
| --- | --- |
| Current BeautyQ architecture, semantics, invariants, accepted limits | the Gen2 technical specification |
| Current delivery/remaining-work status and the closeout DEP-CLOSEOUT waits on | the Q2 closeout feature (`specs/002-beautyq-q2-closeout`) |
| Historical accepted post-cutover boundary definition (source/context only) | the post-cutover plan (transitional source material) |
| Protected acceptance/promotion/verify procedures | the operations runbook |
| The deferred-but-unapproved simplification ideas under evaluation | the post-Q2 simplification proposal (non-normative here) |
| Framework-leverage classifications for BeautyQ-local mechanics | the Distage/Izumi leverage audit (`specs/003-beautyq-distage-izumi-leverage-audit`, completed) |
| Domain-versus-framework authoring boundary | the search-domain authoring principles and the new-domain onboarding guide |
| Agent Git/index and evidence-attribution rules | Constitution Principles I, IV, V, XI |

When this contract, an owner, and durable evidence disagree, the disagreement is a finding to resolve at its owner — never silently preferred, and never a reason to edit an owner from inside a decision feature.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Two-Gate Dependency Enforcement Before Any Decision Work (Priority: P1)

A coordinator attempting to open the decision phase gets a gate check that refuses to start substantive work until both dependencies are verified: the accepted closeout is recorded complete, and the framework audit is recorded complete. If either is missing, the only output is a named, ownable gate report — no evaluation begins.

**Why this priority**: Every disposition this feature produces is worthless — and dangerous, if acted on — if it was drawn from an unverified baseline or a pre-audit guess. The gates are the feature's first product.

**Independent Test**: With the feature artifacts, an auditor can confirm: execution artifacts exist only after durable references to both the verified closeout and the completed audit; every premature attempt produced the gate report naming the missing dependency and its owner; zero evaluation actions predate both gate records.

**Acceptance Scenarios**:

1. **Given** either dependency is unverified, **When** decision execution is attempted, **Then** the only output is `DECISION — GATE NOT YET SATISFIED` naming the unfinished dependency (closeout or audit), the specific open boundary, and its owner; no candidate evaluation starts.
2. **Given** both gates are satisfied with durable references (closeout verification evidence; completed audit synthesis), **When** execution begins, **Then** the gate record names the exact verified baseline identity and the audit artifact the decision anchors to.
3. **Given** the accepted baseline changes after the gates open, **When** the change is detected, **Then** affected evidence is marked superseded and affected candidates return to evaluation rather than keeping dispositions silently (Principle V).

---

### User Story 2 - Per-Candidate Individual Assessment (Priority: P2)

A maintainer receives a decision record in which **each simplification candidate is assessed individually** across a fixed set of dimensions — never as part of an all-or-nothing bundle acceptance or rejection of the proposal — and receives exactly one final disposition from a fixed set that includes "no change."

**Why this priority**: The proposal bundles structurally different questions (historical-code deletion, trace owners, project-graph consolidation, evaluation-ceremony reduction, Qdrant value) whose evidence bases differ. Accepting or rejecting the bundle would hide which specific mechanisms are justified. Per-candidate assessment is the core decision product; P2 because it is the substance, produced after the gates (P1) permit it.

**Independent Test**: For each candidate in the decision record, an auditor can find all twelve assessment dimensions completed (or the candidate explicitly rejected as a decision unit with its basis recorded), exactly one disposition, a stated confidence/uncertainty level, and a named human-approval requirement — and can follow the evidence references to the verified baseline and the audit finding cited.

**Acceptance Scenarios**:

1. **Given** a candidate, **When** it is assessed, **Then** the record covers, at minimum: the current obligation the mechanism serves; whether that obligation still exists at the verified baseline; the demonstrated maintenance/complexity cost; the evidence of value/risk; the relevant Distage/Izumi audit classification for the mechanism; what breaks or becomes unowned if the mechanism is removed; whether documentation-only consolidation is sufficient; whether framework reuse (existing primitive) is preferable; whether BeautyQ-local retention is preferable; a recommendation; explicit confidence/uncertainty; and the human approval requirement.
2. **Given** two candidates with different evidence, **When** dispositions are assigned, **Then** they may differ (one KEEP, one REMOVE) without the record forcing a single verdict for the whole proposal — and the record explicitly does not treat proposal acceptance or rejection as a unit.
3. **Given** a candidate whose evidence is incomplete at decision time, **When** it is assessed, **Then** its disposition is `INSUFFICIENT_EVIDENCE` naming the missing evidence and its owner — never a guess dressed as a disposition.
4. **Given** a mechanism the audit classified as already correctly framework-served (`WELL_USED`), **When** a proposal milestone proposes deleting or rebuilding it, **Then** the assessment names the audit classification and evaluates whether the proposal's premise for that candidate holds.

---

### User Story 3 - Explicit Human Decision with "Nothing Removed Yet" as a Valid Outcome (Priority: P3)

A human decision-maker receives a decision record whose synthesis answers "what complexity can BeautyQ safely remove now, why, and what must remain?" — where a legitimate, first-class outcome is **"nothing should be removed yet"** (every candidate KEEP or INSUFFICIENT_EVIDENCE) — and where the agent/coordinator-prepared dispositions are **recommendations awaiting the human verdict**: while no verdict exists the decision object remains **OPEN** in the waiting state `AWAITING_HUMAN_DECISION` (the package may be complete; the decision is not); the human explicitly approves, rejects, defers, or narrows, and the recorded verdict is the decision outcome that may close the decision object.

**Why this priority**: The feature exists to produce a decision a human can act on or decline; forcing change would corrupt the decision. P3 because it is the final synthesis on top of the per-candidate product.

**Independent Test**: Read only the synthesis: confirm it states per-candidate recommended dispositions, retained obligations, and the aggregate answer including a null-result path; confirm every change-oriented recommendation names its required human approval; confirm a package with no recorded human verdict leaves the decision object OPEN in state `AWAITING_HUMAN_DECISION` and none of its content reads as "already approved", "decided", or "implementation may now begin" (Principle IV).

**Acceptance Scenarios**:

1. **Given** all candidates assessed, **When** the synthesis is written, **Then** it states what can be removed now (if anything), why (evidence-based), and what must remain — and a no-removal outcome is recorded with equal standing, not as a failed run.
2. **Given** the agent/coordinator recommends a change-oriented disposition (SIMPLIFY_LOCALLY, REPLACE_WITH_EXISTING_FRAMEWORK, FRAMEWORK_FOLLOWUP_REQUIRED, REMOVE), **When** no human verdict exists yet, **Then** that candidate's state is a recommendation with the package in `AWAITING_HUMAN_DECISION` — nothing is human-approved, and no source change follows from this feature alone.
3. **Given** the human verdict exists (approve, reject, defer, or narrow — per candidate or for the whole package), **When** the record is finalized, **Then** the verdict is recorded verbatim as the decision outcome, each approved item names the separately accepted later implementation feature as its only route to any source change, and a deferral records the deferred state without starting any work.
4. **Given** a candidate receives FRAMEWORK_FOLLOWUP_REQUIRED and the human approves it, **When** recorded, **Then** it names the follow-up owner (documentation vs framework feature) and remains a recommendation for that separate future decision, not an opened work item (Principle VIII).

---

### Edge Cases

- Both gates satisfied but the proposal document was amended after the audit completed: the decision re-reads the proposal at its current state; divergence between proposal content the audit assumed and current proposal text is recorded as a finding at the proposal owner, not silently consumed.
- A proposal candidate contradicts a normative owner contract (e.g., proposes removing an obligation the technical specification states): the conflict is a decision finding to resolve at the owner; the decision does not treat the proposal as authority over the contract (Principle VIII).
- A candidate maps to multiple proposal milestones: assess it once as one decision unit with all milestone references recorded, or split it when evidence genuinely differs per portion.
- A candidate is not BeautyQ-local mechanics (e.g., Qdrant backend value): the audit classification field is recorded as not-applicable with basis, and the assessment proceeds on product-value evidence.
- Candidate evidence depends on an evaluation run not yet performed (e.g., the proposal's own predeclared Qdrant comparison): the disposition is `INSUFFICIENT_EVIDENCE` with the named missing run; this decision feature does not run evaluations.
- All candidates conclude KEEP/INSUFFICIENT_EVIDENCE: the synthesis records "nothing should be removed yet" as the valid final answer, with retained obligations stated.
- A disposition is approved by the human but a separate implementation feature does not yet exist: that is a valid CLOSED outcome of this feature — the recorded verdict names the required follow-up feature; nothing is implemented by or under this feature.
- Both dependencies are complete and the analysis package is ready, but the human decision-maker is unavailable: the package may be completed and presented, but the decision object remains **OPEN** in the waiting state `AWAITING_HUMAN_DECISION` — absence of the human keeps the decision boundary open rather than fabricating completion, and no agent or coordinator state substitutes for the human verdict (Principle IV).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001** This feature is a **decision contract**. It MUST NOT modify production architecture, source, build definition, tests, runtime behavior, evaluation corpora/thresholds, or any normative owner document. Its only outputs are the decision artifacts and, where its own record requires, navigation updates in this feature's own directory.
- **FR-002** Substantive decision execution MUST NOT begin until BOTH execution dependencies (DEP-CLOSEOUT — verified accepted closeout; DEP-FRAMEWORK-AUDIT — completed Distage/Izumi audit) are independently recorded as satisfied with durable references. Until then the only valid output is `DECISION — GATE NOT YET SATISFIED` naming the unfinished dependency and its owner.
- **FR-003** The decision MUST evaluate the proposal **per candidate, individually**; it MUST NOT accept or reject the proposal as one bundle, and MUST NOT let one candidate's verdict imply another's.
- **FR-004** Every assessed candidate MUST receive all twelve assessment dimensions (US-2 scenario 1) or be explicitly rejected as a decision unit with its basis recorded; every candidate MUST then receive exactly one disposition.
- **FR-005** The disposition vocabulary is exactly: `KEEP`, `SIMPLIFY_LOCALLY`, `REPLACE_WITH_EXISTING_FRAMEWORK`, `FRAMEWORK_FOLLOWUP_REQUIRED`, `REMOVE`, `INSUFFICIENT_EVIDENCE`. `KEEP` and `INSUFFICIENT_EVIDENCE` are valid, first-class outcomes with equal standing; the decision MUST NOT force any candidate to change and a no-removal outcome is explicitly valid.
- **FR-006** The decision MUST test, per candidate and from evidence, whether the current architecture is overengineered for that mechanism, whether its proof machinery exceeded justified need, whether framework extraction is warranted, and whether removal is beneficial — and MUST NOT treat the proposal's diagnosis as established project fact for any of these (Principle VIII; Non-Axioms).
- **FR-007** Every disposition MUST cite: the verified baseline evidence it evaluates against (DEP-CLOSEOUT reference), the audit finding where applicable (DEP-FRAMEWORK-AUDIT reference), and the current owner obligations it preserves. Dispositions MUST NOT float to a baseline other than the one evaluated (Principle V).
- **FR-008** A change-oriented disposition (anything other than KEEP/INSUFFICIENT_EVIDENCE) MUST state the retained obligations that survive it — preserved public contracts, proof owners, safety properties, and operational obligations per the current owners — and what breaks or becomes unowned if removed, explicitly.
- **FR-009** This feature MUST be the **hard gate to implementation**: no implementation follows from any disposition under this feature, including a human-approved one. Any approved implementation change requires a separate later implementation feature with, at minimum: the exact accepted candidates; retained obligations; migration requirements; validation requirements; and rollback/operational implications where applicable. Human approval of the decision is required before such a feature begins.
- **FR-010** The decision boundary is explicit and role-separated: the agent/coordinator prepares and reviews the evidence-backed decision package and may recommend dispositions, but every disposition is a **recommendation presented for human decision**, never a self-authorizing outcome. Only the human decision-maker/product owner approves, rejects, defers, or narrows. While the package is ready but no human verdict exists, the decision object MUST remain OPEN in the waiting state `AWAITING_HUMAN_DECISION`; the coordinator MUST NOT record, phrase, or imply approval. Approve/reject/defer/narrow are all valid verdicts; a recorded verdict is the decision outcome and may close the decision object. Even a human-approved disposition authorizes NO implementation under this feature (FR-009); no outcome may be phrased as self-executing, "already approved", or "implementation may now begin" (Principle IV).
- **FR-011** Documentation-only consolidation MUST be available as a sufficient outcome for any candidate where the mechanism's cost is documentary duplication rather than source complexity; the assessment must actually test this option rather than default to source change.
- **FR-012** Framework-reuse preference MUST be evaluated against the completed audit's classifications: where the audit found an existing framework surface (`WELL_USED`/`UNDERUSED`/`HARD_TO_DISCOVER`), the decision MUST consider `REPLACE_WITH_EXISTING_FRAMEWORK` or a documentation/discoverability remedy before `KEEP`-with-rebuild or `REMOVE`; where the audit found `MISSING_GENERIC_PRIMITIVE`, `FRAMEWORK_FOLLOWUP_REQUIRED` names the separate framework follow-up as its own future decision. A `MISSING_GENERIC_PRIMITIVE`-backed follow-up is a recommendation awaiting its own decision, never an opened work item.
- **FR-013** The decision record MUST include the synthesis answering: what complexity can BeautyQ safely remove now, why, and what must remain — with "nothing should be removed yet" as a valid aggregate answer.
- **FR-014** All work under this feature MUST be worktree-only: no agent commit, stage, unstage, stash, reset, restore, checkout/switch, history rewrite, or ref mutation; existing human-created index state is preserved exactly as found; every report states the actual `HEAD`/index/worktree state truthfully (Principle I).
- **FR-015** This `/speckit.specify` invocation stops at the specification. It MUST NOT run or produce plan, tasks, implementation, analysis, or any candidate evaluation now.

### Key Entities

- **Candidate (decision unit)**: one simplifiable mechanism or coherent mechanism group, individually assessed; sourced from the proposal (tracked to its milestone) or surfaced by the verified baseline/audit. Receives exactly one disposition.
- **Proposal**: the post-Q2 simplification proposal — attributed criticism and a proposed alternative; non-normative input, never authority.
- **Verified accepted baseline**: the exact accepted BeautyQ source/evaluation state established by DEP-CLOSEOUT; the attribution anchor for every assessment (Principle V).
- **Audit classification**: the completed Distage/Izumi audit's five-way label for a BeautyQ-local mechanic (`WELL_USED`/`UNDERUSED`/`HARD_TO_DISCOVER`/`MISSING_GENERIC_PRIMITIVE`/`BEAUTYQ_SPECIFIC`), consumed as evidence, never as permission.
- **Retained obligation**: a current public/runtime/operational/evidence obligation that a disposition explicitly preserves or explicitly supersedes with its replacement owner.
- **Disposition**: one of the six fixed states per candidate (`KEEP`/`SIMPLIFY_LOCALLY`/`REPLACE_WITH_EXISTING_FRAMEWORK`/`FRAMEWORK_FOLLOWUP_REQUIRED`/`REMOVE`/`INSUFFICIENT_EVIDENCE`), with confidence/uncertainty — as produced by the agent/coordinator, always a **recommendation** until the human verdict exists; the verdict, not the recommendation, is the decision outcome.
- **Decision record**: the gated execution output — per-candidate assessments, recommended dispositions, synthesis, human-approval requirements, and its overall state: **OPEN** in `AWAITING_HUMAN_DECISION` while no human verdict is recorded; the recorded verdict (approve/reject/defer/narrow) thereafter, which may close the decision object with that verdict as its outcome.
- **Gate record**: the durable evidence that both execution dependencies (DEP-CLOSEOUT, DEP-FRAMEWORK-AUDIT) were satisfied and the baseline identity the decision anchored to.
- **Human decision-maker/product owner**: the role that holds the decision verdict — approves, rejects, defers, or narrows the recommendations. Distinct from the coordinator role, which prepares and reviews the package and may recommend, but cannot manufacture approval.

## Failure and Blocked Semantics

- **Gate unsatisfied** at execution attempt → `DECISION — GATE NOT YET SATISFIED`, naming the unfinished dependency (closeout boundary or audit), its open item, and its owner; no evaluation begins.
- **`INSUFFICIENT_EVIDENCE`** is a disposition, not a failure: the affected candidate records the exact missing evidence (e.g., a not-yet-run product-value comparison) and its owner; blocked or missing evidence is never recorded as REMOVE, KEEP-with-certainty, or any stronger state (Principle IV, VI).
- **Proposal/owner conflict** → a decision finding recorded at the owner; the proposal never wins by default, and the owner is never edited by this feature.
- **Baseline drift after gates open** → affected evidence marked superseded, affected candidates return to evaluation; dispositions are never silently reattributed (Principle V).
- **Approved disposition without a follow-up feature** → valid closed outcome; the recorded verdict names the required separate implementation feature and stops (FR-009).
- **Human unavailable at decision time** → the package is complete and presentable but the decision object remains OPEN in `AWAITING_HUMAN_DECISION`; absence of the human keeps the boundary open — no coordinator/agent state is recorded as approval or completion (Principle IV).
- **Human declines or defers the decision** → the human's verdict (including "reject everything", "docs-only", "defer", or a narrowed subset) is recorded verbatim as the decision outcome; the decision record supports the verdict rather than lobbying past it, and no implementation starts under this feature in any of these cases.

## Scope Boundaries and Non-Goals

This feature does **NOT**:
- finish the current accepted delivery closeout, or run any of its acceptance/promotion/verify boundaries (DEP-CLOSEOUT is consumed, never performed);

- perform the Distage/Izumi leverage audit itself, or re-open/redo its classifications (DEP-FRAMEWORK-AUDIT is consumed, never performed);
- modify production architecture, source, build definition, tests, runtime, or evaluation corpora/thresholds — even for approved candidates (FR-009);
- implement any proposal milestone (S0–S7 or their contents), including docs-only rewrites of normative owner documents;
- begin a second production domain (D1) or pull in its deferred prerequisites as requirements;
- perform unrelated cleanup, opportunistic refactoring, or new product requirements;
- implement generic framework functionality (framework extraction belongs to a separate framework follow-up feature where `FRAMEWORK_FOLLOWUP_REQUIRED` is approved);
- re-open the completed documentation-history review feature (`001-beautyq-doc-history-review`, frozen under its own constitution version) or re-litigate its findings;
- treat accepted limits or explicit non-goals (of the owners or of the proposal) as backlog to implement (Principle VIII);
- change the constitution, or force every candidate to a change disposition.

In scope: authoring this decision contract; the gated decision phase that assembles the candidate inventory from the proposal plus verified-baseline/audit evidence, assesses each candidate per FR-004, assigns dispositions per FR-005, and produces the synthesis and named human-approval requirements per FR-013/FR-010.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of decision-phase actions occur after the dual gate record (durable references to the verified accepted closeout AND the completed audit synthesis); 0 evaluation actions predate both gates.
- **SC-002**: 100% of assessed candidates carry all twelve assessment dimensions (or an explicit, basis-recorded rejection as a decision unit) and exactly one disposition; 0 candidates are decided as part of an unexamined bundle.
- **SC-003**: 0 proposal diagnoses are restated as established project fact anywhere in the decision record; every cost/overengineering/deletion-benefit claim traces to baseline evidence or is attributed to the proposal as its claim (auditable by reference check).
- **SC-004**: 0 change-oriented dispositions lack: explicit retained obligations, an explicit break/unowned-on-removal analysis, and a named human approval requirement; 100% name the separate implementation feature as the only route to any source change.
- **SC-005**: 0 source, build, test, runtime, or normative-owner-document modifications are attributable to this feature; the worktree change set is decision artifacts only; 0 agent commits, ref changes, or index/stash mutations occur.
- **SC-006**: The synthesis answers the standing question (what can be removed now, why, what must remain) and records KEEP/INSUFFICIENT_EVIDENCE as valid aggregate outcomes; a no-removal result is presentable without being flagged as an incomplete run.
- **SC-007**: 100% of dispositions cite the verified baseline identity and, where applicable, the audit finding; 0 dispositions float onto an unevaluated baseline (Principle V).
- **SC-008**: 100% of agent/coordinator-prepared dispositions are presented as recommendations; while no human verdict exists the decision object is OPEN in `AWAITING_HUMAN_DECISION` (0 fabricated completions); 0 approvals, verdicts, or decision outcomes are recorded from any non-human source; 100% of recorded human verdicts (approve/reject/defer/narrow) appear verbatim as the outcome, may close the decision object, and authorize 0 implementation actions under this feature.

## Assumptions

- The "current accepted BeautyQ delivery closeout" (DEP-CLOSEOUT) is the closeout owned by `specs/002-beautyq-q2-closeout`, whose accepted boundary set is historically documented in the post-cutover plan; this contract names the dependency by role and does not freeze its milestone internals.
- The "Distage/Izumi leverage and framework-opportunity audit" (DEP-FRAMEWORK-AUDIT) is realized by `specs/003-beautyq-distage-izumi-leverage-audit` and is complete on its own synthesis; this contract does not gate on that audit's downstream consumers.
- The proposal's own execution boundary (starts only after verified Q2-C closeout; cannot bypass Q2) is consistent with DEP-CLOSEOUT; any divergence discovered at execution is a finding at the owner, not a silently resolved conflict.
- The candidate inventory at execution will be derived from the proposal's milestones/candidates plus any additional candidates the verified baseline and audit findings surface; the exact candidate list is a decision-phase product, not fixed here.
- Role separation at the decision boundary: the agent/coordinator prepares and reviews the evidence-backed decision package and may recommend dispositions, but cannot manufacture human approval; the **human decision-maker/product owner** — a distinct role — explicitly approves, rejects, defers, or narrows, and the verdict is recorded as the decision outcome. While the package is ready but no verdict exists, the decision object stays OPEN in `AWAITING_HUMAN_DECISION`; waiting on the human is an expected, reportable state — not a failure, and never a fabricated completion. Even an approved verdict authorizes no implementation under this feature (FR-009).
- Repository-consistent feature numbering places this feature at `004`.
