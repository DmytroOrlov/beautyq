# Feature Specification: BeautyQ Distage/Izumi Leverage Audit

**Feature Branch**: `003-beautyq-distage-izumi-leverage-audit` (Spec Kit feature directory; no Git branch is created or switched — ref and index mutation is human-owned under Constitution Principle I)

**Created**: 2026-09-08

**Status**: Draft — durable **research contract**. Specification only; the read-only research may be invoked against an explicitly selected current source state when the human chooses. This document authorizes no source, framework, or architecture change.

**Governing Constitution**: `.specify/memory/constitution.md` — BeautyQ Project Delivery Constitution v2.0.0 (effective 2026-09-08). Work under this feature re-derives the governing state at each phase and never trusts a prompt-supplied revision. The principles most load-bearing for this audit are I (human-owned Git/index), II (one canonical owner — this spec navigates to owners, it does not restate them), IV (evidence is not permission), V (evidence binds to the exact evaluated state — here, the exact framework version and evaluated BeautyQ source state studied), VII (domain policy versus reusable mechanics), VIII (proposals are not accepted architecture), and XI (truthful validation scope).

**Input**: User description (abridged): "A read-only research contract that determines whether infrastructure/mechanics BeautyQ implemented locally are already well served by existing Distage/Izumi primitives and correctly used, supported but underused, supported but hard to discover, genuinely missing as reusable framework primitives, or inherently BeautyQ/domain-specific and correct to keep local. Optimize for discovering BOTH framework opportunities and evidence that no framework extraction is warranted; it is not an extraction mandate. Implementation/refactoring derived from the audit is not authorized."

## Overview and Research Object

This feature is the read-only **Distage/Izumi leverage and framework-opportunity audit**. Its object of study is the boundary between *reusable Distage/Izumi framework mechanics* and *BeautyQ-owned domain mechanics*, as that boundary is actually realized in the current BeautyQ Search Gen2 source.

The research question, stated once and not repeated per finding: for each material piece of infrastructure or repeated mechanics that BeautyQ implements locally, is the local implementation (a) an already-provided framework capability used correctly, (b) an already-provided capability BeautyQ underuses, (c) an already-provided capability that is real but difficult for a competent developer or agent to discover or compose correctly from current docs/examples/APIs, (d) genuinely absent from the framework and worth a reusable Distage/Izumi primitive, or (e) correctly BeautyQ/domain-specific and correct to keep local?

The audit MUST **search** symmetrically: it hunts for genuine framework opportunities *and* actively looks for evidence that no framework extraction is warranted, applying the same skeptical effort to tempting labels in both directions (reflexive "extract this" and reflexive "keep it local" alike). What is mandatory is the **symmetric search and calibration process**, **not** any particular final distribution: the evidence is permitted to conclude that every material candidate needs some form of change, *or* that none of them does. A one-sided final result is legitimate when the evidence supports it; the audit is defective only when its *search or classification process* was one-sided — which is why any one-sided distribution must carry an explicit calibration check showing the opposite outcome class was genuinely considered, not skipped. "Keep it local" and "this is already well used" are legitimate, valued outcomes — not failures to be apologized for — and so are their opposites.

This specification produces a **research contract only**. The mechanical mapping, source comparison, and classification work happen in the execution phase, not here.

## Execution Invocation

The research may be run against an **explicitly selected current source state** when the human chooses to invoke it. There is no closeout, promotion, independent-verify, or other delivery precondition, and no separate ceremony is required to open the research.

- The specification may exist and be reviewed on its own; authoring this contract is not blocked by any other feature.
- When invoked, the run records the exact source state it studied (the committed revision or selected worktree state) as its evidence anchor.
- The framework baseline the audit studies is the **project's actual resolved dependency** (`io.7mind.izumi`, version recorded in the repository build definition at execution time — v1.2.25 at the time this contract was written), not the latest upstream release.
- Findings bound to a source state that later moves stay attributed to the state that was studied and are marked superseded; they are never silently reattributed.

## Relationship to Later Simplification

Findings from this audit **may inform** a later simplification decision, but the audit **does not authorize, perform, or gate** any simplification, and no simplification feature is required to consume it. The audit's completion condition is internal: its synthesis answering its standing questions on their own terms (Constitution Principle III).

- This audit **does not approve, adopt, reject, or act on** any existing simplification proposal (its canonical non-normative owner is the [simplification proposal](../../docs/gen2/BEAUTYQ_SEARCH_GEN2_SIMPLIFICATION_PLAN.md), status *proposed*). It may observe where that proposal's assumptions about "framework-provided vs local" hold or fail, as findings — never as approvals or as edits to the proposal.
- Where this audit identifies a candidate for framework extraction, that is a *recommendation awaiting a separately authorized human decision*, not a work item this feature opens.

## Ownership and Precedence

This contract navigates to current owners and does not duplicate their mutable content (Constitution Principle II). It states *research* ownership; source-of-truth ownership stays where the repository already puts it:

| Fact needed | Canonical current owner |
| --- | --- |
| Current BeautyQ architecture, semantics, invariants, accepted limits | the Gen2 technical specification |
| Domain-versus-framework authoring boundary, proof selection, "what is generic now" | the search-domain authoring principles and the new-domain onboarding guide |
| Operator-visible status, launch/activation modes, response/degradation procedures | the operations runbook |
| The deferred-but-unapproved simplification ideas | the simplification proposal (non-normative here) |
| Actual framework version available to the project | the repository build definition (dependency coordinates), verified at execution |
| Distage/Izumi primitive behavior claims | the framework sources/APIs/examples/tests **of that pinned version** |
| Agent Git/index and evidence-attribution rules | Constitution Principles I, IV, V, XI |

When this contract, an owner, and durable evidence disagree, the disagreement is a finding to resolve at its owner — never silently preferred, and never a reason to edit the owner from inside a research feature.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Five-Way Classification of Every Material Candidate (Priority: P1)

A reader (coordinator, architect, or maintainer deciding whether to open a follow-up) receives a findings set in which each *material* local mechanic is assigned **exactly one** final classification, with a cited evidence reference and a plain-language justification, and in which evidence for "no extraction needed" is given the same standing as evidence for "extract this."

**Why this priority**: The classification *is* the product. Everything else (mapping, inventory) exists to make a defensible classification possible. Without a single, evidence-backed label per candidate, the audit cannot be consumed by the later simplification decision at all.

**Independent Test**: Given a findings set, an auditor can, per candidate: confirm exactly one of the five labels is present; follow its evidence reference to the specific BeautyQ usage and the specific framework surface (or documented absence) that justifies it; and confirm both the "no framework change" and "change needed" classifications are counted and reported, not buried. It delivers value whatever the final distribution turns out to be — all-supported, all-change, or mixed — provided the search that reached it was symmetric (FR-003).

**Acceptance Scenarios**:

1. **Given** the selected source state and a mapped mechanic, **When** it is classified, **Then** it receives exactly one of `WELL_USED` / `UNDERUSED` / `HARD_TO_DISCOVER` / `MISSING_GENERIC_PRIMITIVE` / `BEAUTYQ_SPECIFIC`, with the framework-version and evaluated-source-state references that support the label.
2. **Given** a mechanic where the framework already provides the capability and BeautyQ uses it directly, **When** classified, **Then** it is `WELL_USED` and no gap is manufactured to justify the run.
3. **Given** a mechanic the framework provides but BeautyQ reimplements or bypasses without a documented reason, **When** classified, **Then** it is `UNDERUSED` and names the specific framework surface BeautyQ did not use.
4. **Given** a mechanic whose framework capability exists but could not realistically be found or composed correctly from the current docs/examples/API naming by a competent developer or agent, **When** classified, **Then** it is `HARD_TO_DISCOVER` and is **not** reported as `MISSING_GENERIC_PRIMITIVE`.
5. **Given** the full findings set, **When** summarized, **Then** the "already correct / keep local" outcomes (`WELL_USED`, `BEAUTYQ_SPECIFIC`) are reported with equal prominence to the "change something" outcomes.

---

### User Story 2 - Discoverability Separated from Genuinely-Missing (Priority: P2)

The audit distinguishes *"the framework cannot do this"* from *"the framework can do this, but the primitive is unlikely to be found or composed correctly,"* so that the cheap remedies (documentation, examples, naming/searchability, a helper or facade) are visible as alternatives to inventing a new primitive.

**Why this priority**: Conflating discoverability with capability is the single most likely way this audit produces a bad recommendation — it would spend framework budget on new primitives that are really naming or docs problems. It is P2 because it refines P1's classification rather than replacing it.

**Independent Test**: For every finding labeled a problem, confirm the report states whether a matching framework surface exists *in the pinned version*; if it exists, the finding is a discoverability/usability outcome with a non-primitive remedy, and it is never silently promoted to `MISSING_GENERIC_PRIMITIVE`.

**Acceptance Scenarios**:

1. **Given** a candidate with a real framework surface, **When** the finding is written, **Then** the primary remedy is drawn from {documentation improvement, example improvement, naming/searchability improvement, helper/facade improvement} and the recommendation explicitly says **no new framework primitive is required**.
2. **Given** a candidate where no framework surface exists in the pinned version, **When** the finding is written, **Then** it is a candidate for `MISSING_GENERIC_PRIMITIVE` **only** after it passes the genericity filter below.
3. **Given** a weak-model/agent failure to use an existing primitive, **When** it is attributed, **Then** it is recorded as a discoverability signal, **not** as proof of a framework defect (this is a hard non-goal).

---

### User Story 3 - Human-Readable Synthesis with Evidence-Graded Recommendations (Priority: P3)

The primary human result is a synthesis that answers the eight standing questions, not a raw inventory; and any recommendation strong enough to inform a separately authorized follow-up implementation task or feature carries an explicit evidence grade.

**Why this priority**: The audit's consumer is a *decision-maker*, so the deliverable's shape matters — a huge classified table with no synthesis fails even if every row is technically correct. P3 because it sits on top of P1/P2 output.

**Independent Test**: Read only the synthesis and confirm it answers all eight questions in `Output — Standing Questions`, that each framework-change recommendation cites its genericity-filter result and a plausible second consumer, and that no recommendation is phrased as "already approved/implemented."

**Acceptance Scenarios**:

1. **Given** the classified findings, **When** the synthesis is written, **Then** it answers, in prose, each standing question (what the framework already provided; what BeautyQ used well; what BeautyQ rebuilt unnecessarily, if anything; which misses were discoverability; which generic primitives are actually missing, if any; what should stay BeautyQ-specific; which recommendations are docs/examples vs framework API/code; which have enough evidence for a follow-up).
2. **Given** a `MISSING_GENERIC_PRIMITIVE` recommendation, **When** graded, **Then** it either shows the genericity filter satisfied on substantially all eight conditions *or* it is downgraded to `UNDERUSED`/`HARD_TO_DISCOVER`/`BEAUTYQ_SPECIFIC` with the failing condition named.
3. **Given** a recommendation intended to inform a separately authorized follow-up task or feature, **When** stated, **Then** it names the concrete evidence behind it and remains visibly *a recommendation*, not an accepted change (Constitution Principle VIII).

---

### Edge Cases

- A mechanic appears to have no framework surface: before claiming `MISSING_GENERIC_PRIMITIVE`, the audit must have checked the **pinned** framework version's APIs/examples/tests, not memory or latest upstream; unresolved, it is `BLOCKED_NEED_EVIDENCE`, not "missing."
- A mechanic is both underused *and* hard to discover: the audit picks the more specific actionable label and records the secondary signal in the justification; it does not double-count a candidate under two labels in the headline classification.
- A finding is trivially BeautyQ-specific on inspection: `BEAUTYQ_SPECIFIC` is a *successful* terminal classification; it is recorded with the reason, not skipped or treated as an unfilled slot.
- One accidental site suggests a pattern: recurrence beyond a single accidental BeautyQ site is required to **support** a `MISSING_GENERIC_PRIMITIVE` claim, so a lone site may not be called "missing framework primitive." That failing condition removes **only that one label**; the candidate is then classified on its own evidence and may legitimately be `HARD_TO_DISCOVER` (a real but undiscoverable existing framework surface), `UNDERUSED`, or `BEAUTYQ_SPECIFIC`. The recurrence rule blocks an unsupported `MISSING_GENERIC_PRIMITIVE`; it does not by itself choose among the other labels.
- The studied source state moved after the run: re-anchor the source-state record; findings bound to the earlier state stay attributed to it and are marked superseded, not silently reattributed (Constitution Principle V).
- The framework version available at execution differs from the version this contract assumed: findings name the actually-resolved version; existence/non-existence claims are re-checked against it.
- A candidate belongs to a *rejected* research area (the audit is free to reject any candidate): record the rejection with its basis rather than forcing a classification.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001** This feature is a **read-only research contract**. Its specification creates no obligation to modify BeautyQ, Distage/Izumi, or any other source. It authorizes investigation and synthesis only.
- **FR-002** Research execution MAY begin against the explicitly selected current source state when the human invokes it. The run MUST record the exact source state it studied as its evidence anchor (see Execution Invocation).
- **FR-003** The audit MUST **search** symmetrically for evidence for *and* against framework change and MUST apply equal skeptical effort to tempting labels in both directions (reflexive "extract this" and reflexive "keep it local" alike); it MUST NOT treat "well used"/"keep it local" or "gap/change-needed" as inherently inferior or superior outcomes. **No particular final classification distribution is mandated and no quotas apply**: a set where every material candidate needs some change, one where none does, or any mixture are each valid when the evidence supports them and the search was symmetric — zero `WELL_USED`, zero `BEAUTYQ_SPECIFIC`, and zero change-oriented findings are each permitted. A **one-sided** final distribution is allowed but MUST carry an explicit **calibration check** showing the opposite outcome class was genuinely investigated rather than skipped. The audit is defective only when its *search or classification process* was one-sided — never because an evidence-supported outcome distribution happens to lean one way.
- **FR-004** Every **material** candidate MUST receive exactly one final classification from the five distinct labels, and the five meanings MUST remain distinct even if the display names are normalized:

  | Label | Meaning |
  | --- | --- |
  | `WELL_USED` | Framework already provides it and BeautyQ uses it correctly. |
  | `UNDERUSED` | Framework already provides it; BeautyQ underuses, bypasses, or reimplements it. |
  | `HARD_TO_DISCOVER` | Framework provides it, but it is difficult to find or compose correctly from current docs/examples/API naming. |
  | `MISSING_GENERIC_PRIMITIVE` | Genuinely absent from the pinned framework and passes the genericity filter for a reusable primitive. |
  | `BEAUTYQ_SPECIFIC` | Inherently BeautyQ/domain policy; correct to keep local. |

- **FR-005** The audit MUST treat the **discoverability** axis as first-class: `HARD_TO_DISCOVER` MUST remain distinct from `MISSING_GENERIC_PRIMITIVE`, and a matching framework surface that merely cannot be found/composed is never promoted to "missing."
- **FR-006** Non-primitive remedies — documentation improvement, example improvement, naming/searchability improvement, and helper/facade improvement — MUST be available as primary outcomes and MUST NOT require inventing a new framework primitive.
- **FR-007** A recommendation of a **generic Distage/Izumi primitive** (`MISSING_GENERIC_PRIMITIVE`) is permitted **only** when evidence supports substantially all of the *Genericity Filter* conditions below; failure of the filter MUST push the candidate to `UNDERUSED`, `HARD_TO_DISCOVER`, or `BEAUTYQ_SPECIFIC`, not to framework expansion.
- **FR-008** Before any claim that a primitive **exists** or **does not exist**, the audit MUST use the framework version **actually available to the project** (its resolved dependency), verified against that version's APIs/examples/tests/sources. It MUST NOT assume latest upstream behavior equals the project's pinned dependency.
- **FR-009** Research execution MUST combine all six method stages, in this dependency order, with later stages consuming earlier artifacts:
  1. **A — Exhaustive/mechanical BeautyQ usage mapping**: enumerate the local infrastructure/mechanics sites from the selected source state without yet judging them.
  2. **B — Targeted framework primitive inventory**: for the needs surfaced by A, inventory candidate framework surfaces in the pinned version.
  3. **C — Source-level comparison**: compare each local mechanic to its nearest framework surface at source/API level.
  4. **D — Discoverability assessment**: judge whether the framework surface is discoverable and correctly composable from current docs/examples/API naming.
  5. **E — Adversarial classification**: assign the five-way label while actively hunting counterexamples to the tempting label.
  6. **F — Recommendation after counterexamples**: emit recommendations only after counterexamples are considered and the genericity filter is applied.
- **FR-010** Independent mappings (stage A/B/C slices) MAY be produced by **parallel read-only agents**; the audit MUST NOT require one context to hold all source findings.
- **FR-011** Structured research memory MUST be **persisted in this feature's research artifacts**, not carried in conversation context, so that a later session resumes from durable intermediate state (Constitution Principle V: each finding names the source state and framework version it was derived from).
- **FR-012** The primary human deliverable MUST be a **synthesis** that answers every question in `Output — Standing Questions`; a raw source inventory is explicitly **not** the product (it is a means to the classified findings).
- **FR-013** The audit is **free to reject** any candidate research area in `Candidate Research Areas`; a rejected candidate is recorded with its basis and not forced into a positive finding.
- **FR-014** Every framework-change recommendation MUST be tagged by kind — **docs/examples** or **framework API/code** — and, if it is meant to inform a separately authorized follow-up task or feature, MUST state its evidence grade and remain phrased as a recommendation, never as an accepted change.
- **FR-015** All work under this feature MUST be **worktree-only**: no agent commit, stage, unstage, stash, reset, restore, checkout/switch, history rewrite, or ref mutation; existing human-created index state is preserved exactly as found; every report states the actual `HEAD`/index/worktree state truthfully (Constitution Principle I).
- **FR-016** This feature MUST NOT modify BeautyQ source/architecture, MUST NOT modify Distage/Izumi, and MUST NOT implement or scaffold any framework primitive.
- **FR-017** This feature MUST NOT perform, approve, or reopen simplification. Its findings may inform a later simplification decision but gate none, and this feature **grants that decision no permission** (Constitution Principles III, IV, VIII).
- **FR-018** The contract MUST NOT copy milestone-specific mechanics (specific class names, counts, run outcomes) into this durable research contract beyond the navigational references owners already require.
- **FR-019** This `/speckit.specify` invocation stops at the specification. It MUST NOT run or produce plan, tasks, implementation, or cross-artifact analysis.

### Genericity Filter (gate on `MISSING_GENERIC_PRIMITIVE`)

A candidate may be recommended as a new reusable framework primitive **only** when evidence supports **substantially all** of:

1. **Recurrence** — the need recurs beyond one accidental BeautyQ site.
2. **No product semantics** — the proposed API carries no BeautyQ/product semantics.
3. **Infrastructural nature** — it is mechanical/infrastructure, not domain policy.
4. **De-named API** — a plausible API exists without search/Qdrant/BeautyQ vocabulary.
5. **Second consumer** — at least one plausible consumer other than BeautyQ.
6. **Real reduction** — it meaningfully reduces bespoke orchestration.
7. **Generic tests** — the behavior can be tested by neutral/fixture-level tests, not only BeautyQ examples.
8. **Simpler owned centrally** — a framework-owned version is genuinely simpler than keeping the behavior local.

Failure on any substantial condition downgrades the candidate to `UNDERUSED`, `HARD_TO_DISCOVER`, or `BEAUTYQ_SPECIFIC`; it does not license framework expansion.

### Candidate Research Areas (hypotheses to investigate — the audit may reject any)

- lifecycle / readiness state;
- optional dependency / resource construction;
- generation / source identity;
- graph-boundary or construction-boundary proofs;
- operator-visible degradation / status;
- immutable evidence / source attribution;
- ownership / firewall constraints;
- startup / activation composition;
- reusable domain / kernel boundaries.

These are starting hypotheses, **not** predetermined findings or a to-do list. A candidate area is a valid, valuable result whichever way the evidence lands — all `WELL_USED`/`BEAUTYQ_SPECIFIC`, all change-oriented, `HARD_TO_DISCOVER` or `MISSING_GENERIC_PRIMITIVE`, or any mixture — so long as the search that produced it was symmetric and any one-sided distribution carries its calibration check (FR-003).

### Output — Standing Questions (the synthesis MUST answer each)

- What did Distage/Izumi already provide?
- What did BeautyQ use well?
- What did BeautyQ rebuild unnecessarily, if anything?
- Which misses were likely discoverability problems?
- Which generic framework primitives are actually missing, if any?
- What should remain BeautyQ-specific?
- Which recommendations are docs/examples versus framework API/code?
- Which recommendations have enough evidence for a separately authorized follow-up implementation task or feature?

### Key Entities

- **Material candidate**: a local infrastructure/mechanic site (or grouped mechanic) large enough to warrant a classification; the unit that receives exactly one label.
- **Classification**: one of the five labels, with its cited evidence (BeautyQ usage reference + framework surface or documented absence) and plain-language justification.
- **Evaluated BeautyQ source state**: the exact source state the audit studies; the attribution anchor for every finding (Constitution Principle V).
- **Framework version reference**: the project's actually-resolved Distage/Izumi dependency used for every exists/does-not-exist claim (not latest upstream).
- **Framework primitive inventory**: the pinned-version surfaces identified for a given need (method stage B).
- **Discoverability assessment**: the finding of whether a real surface is findable/composable from current docs/examples/API naming (method stage D).
- **Genericity filter result**: the per-candidate pass/fail across the eight filter conditions, deciding whether `MISSING_GENERIC_PRIMITIVE` is allowed.
- **Recommendation**: an output action, tagged docs/examples or API/code, graded by evidence, always non-authoritative.
- **Research memory artifact**: the persisted intermediate state (mappings, inventories, comparisons) that lets a later session resume without conversation context.
- **Source-state record**: the durable reference to the exact source state the audit studied.

## Failure and Blocked Semantics

- **`BLOCKED_NEED_EVIDENCE`** → a source, framework-version, or example check could not be completed (missing access, unlocatable surface, unresolved dependency): the affected candidate stays open with the exact missing evidence named; blocked is never recorded as `MISSING_GENERIC_PRIMITIVE` or as a passing conclusion.
- **Indeterminate classification** (evidence insufficient to separate two labels) → report the tension and the additional evidence required; do not pick a label to fill a slot.
- **Source-state drift after a run** → re-anchor; earlier findings stay attributed to the state they studied and are marked superseded, never reattributed (Principle V).
- **Unauthenticated framework claim** (an exists/non-exists assertion without a pinned-version reference) → treated as not-yet-established; it cannot support a recommendation.

## Scope Boundaries and Non-Goals

This feature does **NOT**:

- modify BeautyQ architecture or source;
- modify Distage/Izumi or implement any framework primitive;
- perform simplification, or approve/reject/act on any existing simplification proposal (including the simplification proposal);
- create or scaffold a second production domain;
- reopen the completed documentation-history review feature (`001-beautyq-doc-history-review`, frozen under its own constitution version);
- prove that BeautyQ was overengineered (an excluded claim in the constitution's Non-Axioms — this audit neither assumes nor targets it);
- assume that a weak-model/agent failure implies a framework defect (that is, at most, a discoverability signal);
- authorize any implementation or refactoring derived from its findings;
- change the constitution.

In scope: read-only investigation of BeautyQ source/tests, Distage/Izumi source/APIs/tests/examples for the pinned version, local documentation, dependency/version information, and relevant framework documentation; synthesis and evidence-graded, non-authoritative recommendations.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of material candidates carry exactly one of the five classifications with a BeautyQ-usage reference and a framework-version-anchored evidence reference; 0 candidates hold two headline labels or none.
- **SC-002**: The audit performs a **symmetric search and calibration**, not a quota. For every one-sided final distribution — zero `WELL_USED`, zero `BEAUTYQ_SPECIFIC`, or zero change-oriented findings — an explicit calibration record shows the opposite outcome class was genuinely investigated rather than skipped. 0 findings sets are judged incomplete merely for their final distribution; the only process defect is one-sided *search/classification*. 0 mandatory outcome counts.
- **SC-003**: 0 findings are labeled `MISSING_GENERIC_PRIMITIVE` where a source-proven framework surface actually exists in the pinned version and the real issue was discoverability; every such case is `HARD_TO_DISCOVER` or `UNDERUSED`.
- **SC-004**: 100% of `MISSING_GENERIC_PRIMITIVE` recommendations show the genericity filter satisfied on substantially all eight conditions or are downgraded naming the failing condition; 0 framework-primitive recommendations lack a stated second consumer.
- **SC-005**: 100% of existence/non-existence framework claims cite the project's actually-resolved dependency version and a source/API/example reference; 0 rely on "latest upstream" or memory.
- **SC-006**: The synthesis answers all eight standing questions; an auditor can follow it without reading the raw inventory.
- **SC-007**: Each run records the exact source state it studied as its evidence anchor; 0 findings float onto an unrecorded source state.
- **SC-008**: 0 source, build, or framework files are modified attributable to this feature; the worktree change set is research artifacts only; 0 agent commits, ref changes, or index/stash mutations occur.

## Assumptions

- The source state studied is explicitly selected by the human when the research is invoked; this contract does not pre-select it or freeze any delivery milestone.
- The framework under audit is Distage/Izumi at the version the project's build actually resolves (v1.2.25 when this contract was authored), verified afresh at execution.
- The materials the research phase may inspect (BeautyQ source/tests, framework sources/APIs/tests/examples, local documentation, dependency/version data, relevant framework docs) are available read-only at execution; inability to reach them yields `BLOCKED_NEED_EVIDENCE`, not a conclusion.
- "Material candidate" is scoped by the execution phase's mechanical mapping; immaterial trivia need not be individually classified, but the exclusion basis is recorded.
- The audit is complete on its own synthesis (Constitution Principle III: completion names its own object — the research contract, not downstream decisions).
