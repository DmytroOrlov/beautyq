# Feature Specification: BeautyQ Q2 Delivery Closeout

**Feature Branch**: `002-beautyq-q2-closeout` (Spec Kit feature directory; no Git branch is created or switched — ref and index mutation is human-owned under Constitution Principle I)

**Created**: 2026-09-08

**Status**: Draft

**Governing Constitution**: `.specify/memory/constitution.md` — BeautyQ Project Delivery Constitution v2.0.0 (effective 2026-09-08). Verified at specification time: the accepted v2.0.0 is the governing text in the committed state and the working copy was clean, so the governing version is determinable and this feature proceeds under v2.0.0. Work performed under this feature re-derives the governing state at each phase; it never trusts a prompt-supplied starting revision.

**Input**: User description (abridged): "`speckit.specify` for 002-beautyq-q2-closeout — closes the ALREADY ACCEPTED remaining delivery contract; does not redesign BeautyQ; does not begin by assuming Recovery Rotation 8 must be implemented; first responsibility is to reconcile the repository's actual present state and identify the exact first unfinished accepted boundary; remaining accepted work completed in owner-defined order; human-owned immutable Git boundary separated from agent work; honest closeout with truthful evidence/source-state attribution."

## Overview and Delivery Object

This feature closes the already-approved remaining Q2 post-cutover delivery contract for BeautyQ Search Gen2, and this feature specification owns the current Q2 closeout requirements, gates and state. The accepted delivery boundary set originated in, and is historically documented by, the [post-cutover plan](../../docs/gen2/BEAUTYQ_SEARCH_GEN2_POST_CUTOVER_PLAN.md), which is now transitional source material consumed here as source/history context — not a current Q2 status or execution authority. The protected-workflow boundaries are procedured by the [operations runbook](../../docs/gen2/BEAUTYQ_SEARCH_GEN2_OPERATIONS.md), whose "Protected workflow ownership" section owns the HOW of the protected-acceptance, bootstrap, promotion and verify procedures — not whether this feature is currently authorized to advance to them. The plan's recorded pre-002 status — recovery source, replenishment and freeze prerequisites recorded complete, and the Q2-B protected-acceptance/candidate-review boundary and the Q2-C promotion/verify/closeout boundary recorded open — is a reconciliation input and claim, not current authority; this feature's first obligation is to verify it against durable evidence, not to repeat it.

The feature adds no new milestones, gates, ladders or evaluation machinery, and removes none. It uses the owner-defined sequence exactly as it exists: prerequisite closure → protected acceptance → candidate bootstrap → candidate review → explicit promotion → independent verification → Q2 documentation closeout. It ends when the owner-defined closeout condition is honestly reached — green verify recorded against the committed baseline's evaluation content and the owner documents' status/closure contract satisfied — or when it honestly reports the boundary that cannot yet be closed and who owns the missing step.

## Ownership and Precedence

This specification owns the current Q2 closeout contract it states and otherwise navigates to the other current owners without duplicating their mutable content (Constitution Principle II):

| Fact needed | Canonical current owner |
| --- | --- |
| Current Q2 closeout requirements, gates and state | this feature specification (`specs/002-beautyq-q2-closeout`) |
| Historical accepted post-cutover boundary definition and pre-002 status context | the post-cutover plan (transitional source/history) |
| Current architecture and semantics | the Gen2 technical specification |
| HOW protected acceptance / bootstrap / promotion / verify are executed (procedures and canonical resource identities) | the operations runbook protected-workflow sections |
| Agent Git/index boundaries and evidence-attribution rules | Constitution Principles I, III, IV, V, VI, XI |
| Coordinator acceptance/review procedure | the coordinator workflow guide (coordinator role only) |

When this specification, an owner document, and durable repository evidence conflict, the conflict is a reconciliation finding to be resolved at its owner — never silently preferred.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Mechanical Reconciliation of the Actual Closeout State (Priority: P1)

A coordinator (or a human reading the result) needs a single reconciliation record that states what the repository's durable evidence actually shows about the accepted Q2 contract right now: which boundaries are complete, which is the first unfinished accepted boundary, and on what evidence each conclusion rests — before any implementation or evidence-producing action is chosen. The record replaces assumption with derived state.

**Why this priority**: Every later decision in this feature — whether any protected acceptance must be re-run, whether a candidate exists to review, whether promotion is next — is wrong if it is built on an assumed state. The user directive is explicit: reconciliation comes before implementation assumptions.

**Independent Test**: The reconciliation record can be produced and audited independently of any downstream work: every required dimension carries an explicit conclusion and a durable evidence reference, and no implementation or evidence action has been taken on the feature's subject matter. It delivers standalone value even when everything downstream is blocked.

**Acceptance Scenarios**:

1. **Given** the repository at whatever `HEAD`, index and worktree state actually exists, **When** reconciliation runs, **Then** it reports each of: committed history relevant to the accepted contract; index/worktree state where it differs from `HEAD`; the plan's recorded pre-002 status claim; existing recovery-authorization and materialization records; protected-acceptance artifacts and the content fingerprints each is bound to; candidate artifacts and any candidate-review record; promotion state; verify state; and any other durable repository evidence identifying which boundary was actually completed — with an explicit conclusion and evidence reference per dimension.
2. **Given** a historical clue that an authorization associated with a recovery rotation 8 exists in the accepted authorization record, **When** reconciliation classifies it, **Then** the result is exactly one of: authorization only; selection/materialization followed; evidence consumed it; it became obsolete after later source changes; or a later accepted boundary superseded it — each classification backed by durable evidence, never by the clue itself.
3. **Given** reconciliation classifies a boundary as complete, **When** the feature later acts, **Then** nothing for that boundary is re-executed, regenerated, or re-reviewed; it is used as input.
4. **Given** durable evidence is insufficient to distinguish two classifications, **When** reconciliation concludes, **Then** it reports `BLOCKED_NEED_EVIDENCE` naming the missing dimension instead of guessing a boundary.
5. **Given** the plan's recorded pre-002 status claim and durable evidence disagree, **When** reconciliation concludes, **Then** it records the divergence as a source-status reconciliation finding so the plan's historical claim can be corrected at its owner, without deleting the evidence trail either claim rests on.

---

### User Story 2 - Remaining Accepted Boundaries Completed in Owner-Defined Order (Priority: P2)

Once the first unfinished accepted boundary is established, the remaining accepted boundaries are executed in the order the owners define — protected acceptance, candidate bootstrap, candidate review, promotion, verification — each producing evidence bound to the actual evaluated inputs and results, and the immutable promotion commit is made by the human alone at an explicit handoff.

**Why this priority**: This is the actual closeout work, but it is only legitimate after US-1 has established what is still required; performing it against an assumed state is the failure mode this feature exists to prevent.

**Independent Test**: With a completed reconciliation record as input, walk each executed boundary and confirm the owner-defined ordering and permission rules held: bootstrap followed only a green protected acceptance on the verified tracked canonical inputs; the candidate was preserved for separate review and not promoted in the same step; promotion followed explicit review approval and a human-created commit; verify ran independently against the committed baseline's evaluation content.

**Acceptance Scenarios**:

1. **Given** the first unfinished boundary is protected acceptance against the verified tracked canonical inputs, **When** acceptance runs fresh against those inputs, **Then** a green gate is the only condition under which bootstrap may proceed, and red or blocked stops the boundary with no downstream step taken.
2. **Given** bootstrap produced an aggregate-only candidate, **When** review is due, **Then** the candidate and aggregate artifacts are preserved and handed to coordinator/operator review, and generation is never treated as promotion permission.
3. **Given** review approved the preserved candidate unchanged, **When** promotion is materialized, **Then** an agent places the byte-for-byte copy into the one canonical tracked evaluation baseline resource in the worktree and validates it patch-locally, then stops and hands off — staging and the promotion commit belong to the human, and the agent never creates them.
4. **Given** the human created the promotion commit, **When** downstream verify evidence is captured, **Then** it runs against the committed baseline's evaluation content with the same verified canonical inputs, and independent verification produces the verify evidence this feature's closeout gate requires before Q2 documentation may close.
5. **Given** the pre-promotion application-source state changes after a green protected acceptance, **When** promotion is considered, **Then** Q2-B restarts with fresh protected acceptance against the changed inputs, and the earlier evidence stays attributed to the inputs it actually evaluated.
6. **Given** an agent reports any step, **When** the report names its Git state, **Then** it distinguishes `HEAD`, index and worktree truthfully and never calls a divergent uncommitted state "HEAD".

---

### User Story 3 - Honest Closeout with Owner-Scoped Documentation Updates (Priority: P3)

The feature ends with a closeout that a later reader can trust: each ladder layer named with its own achieved or open status, evidence attributed to the exact evaluated state, validation scope stated truthfully, and owner documents updated only where their own status/closure contract requires it.

**Why this priority**: The durable output humans act on is the closeout statement plus corrected owner status; inflated or under-attributed closeout is the specific failure this feature is chartered to prevent.

**Independent Test**: Audit the closeout report and documentation diff: every completion claim names its object and gate; every evidence claim names its evaluated source state; the doc diff touches only status/closure content the owners' own contracts require; no accepted limit or deferred initiative was converted into work.

**Acceptance Scenarios**:

1. **Given** all boundaries reconciled or executed, **When** closeout is written, **Then** it states, per layer — prerequisite closure, protected acceptance, candidate review, promotion, verification, Q2 documentation closeout — one of complete (with attributed evidence), open, blocked (with the exact missing resource/permission and its owner), or superseded, with no bare "Q2 complete" claim.
2. **Given** green verify against the committed baseline's evaluation content exists, **When** owner documents are updated, **Then** only the status/closure statements their own contracts require change, and the updates cite evidence rather than restating it.
3. **Given** the contract cannot be closed, **When** the feature stops, **Then** it reports the first unfinished boundary, what was already proven, what remains, and which human role owns the missing step — instead of manufacturing a pass or widening scope.

---

### Edge Cases

- Reconciliation shows the accepted contract is already fully closed: the feature produces the verification-and-status record only; no evidence is re-run, and closeout reports each layer's attributed completion.
- A candidate artifact exists but no candidate-review decision record exists: the next boundary is review; the candidate is not promotable merely because it exists.
- The canonical baseline resource already matches the reviewed candidate byte-for-byte in the worktree but no promotion commit exists: the human commit boundary is still open; an agent must not close it.
- Worktree and index differ at any inspection point: both states are reported distinctly and evidence attribution names the exact evaluated state.
- An authorization record whose protected content fingerprints were later made obsolete by accepted source changes: reconciliation marks it obsolete with evidence, and the obsolete authority is not consumed.
- Multiple local refs or human-owned stash entries exist in the broader repository: reconciliation reads them if decision-relevant and never cleans, normalizes or mutates them.
- Required external resources (catalog data services, embedding services, application runtime) are unavailable for a needed run: verification is blocked, not passed; product failure and inability-to-perform-the-proof are distinguished in the report.
- The reviewed candidate changed between review and promotion: promotion is invalid; the changed artifact requires fresh review — byte-for-byte equality is the promotion condition.
- Search, evaluation-policy, lifecycle, route or corpus source changes between review and verify: the owner-defined restart rule applies; prior downstream evidence cannot carry over.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001** Reconciliation MUST be a first-class, auditable deliverable and acceptance condition of this feature — not informal setup. No implementation, evidence-producing, or promotion action may be chosen before the reconciliation record exists.
- **FR-002** The reconciliation MUST cover, at minimum, every dimension listed in Acceptance Scenario 1 of User Story 1 and MUST conclude each with an explicit classification plus a durable evidence reference; unresolved dimensions MUST produce `BLOCKED_NEED_EVIDENCE` naming what is missing.
- **FR-003** The reconciliation MUST establish the exact first unfinished accepted boundary within the owner-defined ladder; it MUST NOT invent a gate or reorder the ladder.
- **FR-004** Any historical clue naming a specific recovery rotation or authorization (including a rotation-8 association) MUST be treated as navigation only; the feature MUST NOT state or act on "implement rotation 8" (or any equivalent starting requirement) unless reconciliation independently concludes that specific boundary is the first unfinished accepted one.
- **FR-005** All boundaries classified complete MUST be treated as input: no repetition, regeneration, re-review or re-promotion of already-completed accepted work, and no re-attribution of their existing evidence.
- **FR-006** The evidence-not-permission boundaries of the owner contract MUST be preserved at every step: green protected acceptance gates bootstrap; bootstrap output is a preserved aggregate-only candidate, not promotion permission; explicit coordinator/operator review gates promotion; the human promotion commit gates downstream verify; green verify gates Q2 documentation closeout.
- **FR-007** The promotion boundary MUST be represented as a HUMAN Git boundary: agent materializes and patch-locally validates the byte-for-byte candidate copy in the worktree → coordinator accepts the patch → human decides staging and creates the promotion commit → downstream acceptance evidence runs against the resulting committed baseline's evaluation content. The agent MUST NOT create that commit, and nothing in this feature may weaken the owner contract's requirement that promotion be a committed tracked boundary.
- **FR-008** Patch-local validation and downstream acceptance evidence MUST remain distinct roles: patch-local checks may run against and are attributed to the exact evaluated uncommitted state and never require an agent commit; downstream acceptance evidence follows the feature's stricter committed-human-boundary contract where one exists, waiting on the human boundary rather than being substituted with uncommitted results.
- **FR-009** Every evidence-bearing claim MUST be attributed to the actual evaluated inputs and results. The feature MUST prevent: evidence floating onto another evaluation; old evidence relabeled after source edits; calling a divergent worktree or index state "HEAD"; and a source change inheriting acceptance evidence without the owner-defined restart/rerun.
- **FR-010** A change to the pre-promotion application source after protected acceptance MUST trigger the owner-defined Q2-B restart with fresh acceptance, with earlier evidence remaining attributed to the inputs it actually evaluated. Whether any particular accepted post-materialization change invalidates pending acceptance evidence MUST itself be a reconciliation determination under the owners' evidence semantics, not an assumption in either direction.
- **FR-011** The specification preserves the accepted no-fake-green rules for all work under it: no deleting failures after observing outcomes; no relabeling protected or visible cases to improve results; no changing criteria after outcomes while retaining the prior claim; no using weaker evidence while retaining a stronger claim; no treating unavailable required proof as PASS; no treating candidate generation as permission to promote.
- **FR-012** Completion language MUST be decision-scoped: every completion, acceptance or closure statement names its object and gate, distinguishing prerequisite closure, acceptance, candidate review, promotion, verification and delivery closeout, per the owner contract — without inventing a new ladder.
- **FR-013** Owner-document updates MUST be limited to what each owner's own status/closure contract requires at closeout (and to reconciliation-discovered status divergence corrected at its owner); no historical narrative, run logs or evidence payloads are copied into owner documents or into this feature's artifacts beyond attributed references.
- **FR-014** All agent work under this feature MUST be worktree-only: no agent commit, stage, unstage, stash, reset, restore, checkout/switch, history rewrite or ref mutation; existing human-created index state is preserved exactly as found; every report states the actual `HEAD`/index/worktree state truthfully.
- **FR-015** Validation claims MUST be scope-truthful: what ran, against which evaluated state, at what scope, and what remains unknown; a focused or patch-local run is never presented as downstream acceptance or full-contract proof.
- **FR-016** Reaching the human promotion-commit boundary MUST produce an explicit handoff report to the human stating the exact materialized uncommitted state, the pending human decision, and the downstream evidence that waits on it — the feature never treats the handoff itself as the boundary being closed.
- **FR-017** If, at any phase start, the governing constitution version cannot be safely derived from the repository (for example an unresolved older/staged candidate makes the governing version indeterminate), the work MUST stop and report `Q2 CLOSEOUT — GOVERNANCE BOUNDARY NOT YET STABLE` without staging, unstaging, resetting, restoring, stashing, committing or normalizing anything.
- **FR-018** The feature MUST NOT introduce a new universal hashing or manifest system, new evaluation or governance machinery, or any automated promotion service; it uses the identity and evidence mechanisms the current owners already define.

### Key Entities

- **Accepted delivery contract**: the already-approved Q2 boundary set (protected acceptance, candidate bootstrap/review, promotion, verify, closeout) as defined by this feature specification, historically documented in the post-cutover plan, with its protected workflows procedured by the operations runbook.
- **Reconciliation record**: the first-class output of FR-001/FR-002 — per-dimension conclusions with durable evidence references, yielding the established first unfinished accepted boundary.
- **Evaluated inputs and results**: the actual protected/visible inputs and their produced aggregate results (corpus and policy fingerprints, digests) that a piece of evidence was produced against; the attribution unit for all evidence claims. Attribution does not use a Git application revision.
- **Protected-acceptance evidence**: aggregate-only artifacts bound to the actual evaluated corpus and policy fingerprints.
- **Candidate**: the preserved aggregate-only bootstrap output awaiting separate coordinator/operator review; possession of it grants no promotion rights.
- **Authorization record**: an existing accepted break-glass/rotation authorization entry; a reconciliation input and clue, never self-acting permission.
- **Promotion boundary**: the human-created tracked commit placing the reviewed candidate byte-for-byte into the one canonical tracked evaluation baseline resource.
- **Verify evidence**: the independent post-promotion verification comparing the committed baseline's evaluation content, the gate on Q2 documentation closeout.
- **Owner status statements**: this feature's own closeout state plus the status/closure lines of the other current owners, updated only by their own closure contracts; the plan's pre-002 status section remains historical source content, not a live status line.

## Failure and Blocked Semantics

- **Red protected acceptance** stops that boundary: no bootstrap, review, promotion or verify follows; the red result stays reported as red even if surrounding patches are accepted.
- **Blocked** means a required proof could not be performed (missing resource, unavailable environment, missing human decision, missing durable evidence): the affected gate remains open, the report names the exact missing element and its owner, and blocked is never recorded as PASS or converted into silent product failure.
- **Reconciliation indeterminacy** (evidence insufficient to classify a dimension) yields `BLOCKED_NEED_EVIDENCE` naming the dimension and the evidence sought — the fallback is a focused evidence request, never an assumption.
- **Divergence between the plan's recorded pre-002 status claim and durable evidence** is a source-status reconciliation finding corrected at the owner of the statement the divergence concerns — the plan's historical claim at the plan, the current closeout state here; work does not proceed as if both were simultaneously true.
- **Unauthorized Git need** (a step that would require an agent commit/stash/index change) is a stop condition: the work is handed to the human with the exact pending decision.
- **Governance instability** at any phase start triggers FR-017 stop-and-report.

## Scope Boundaries and Non-Goals

Explicitly out of scope for this feature:

- Distage/Izumi framework leverage research, framework extraction, or any generic lifecycle/readiness redesign;
- post-closeout simplification — its approval, rejection, implementation, or any use of it to alter the current acceptance contract (it remains a separate future decision owned by its proposal document, which is non-normative here);
- implementation of any unapproved simplification proposal;
- second-production-domain (D1) work — deferred; its prerequisites remain owned by the current planning documentation and MUST NOT be converted into requirements of this feature;
- activation of any deferred initiative;
- opportunistic refactoring or unrelated cleanup;
- new evaluation, evidence or governance machinery, including any new universal hashing/manifest system;
- reopening the completed documentation-history review feature (frozen under its own constitution version);
- changing the constitution;
- any redesign of BeautyQ itself: this feature changes delivery state and, at closeout, owner status lines — not architecture, semantics, contracts or code behavior beyond the owner-defined promotion content.

### Deferred Follow-up (exactly one, durable)

The separate **Distage/Izumi leverage and framework-opportunity audit** is now materialized as `specs/003-beautyq-distage-izumi-leverage-audit` — the one durable deferred follow-up of this closeout. Its EXECUTION still waits for this feature's verified closeout; it is not a prerequisite for 002, grants no refactoring permission to 002, and is not an accepted framework change. 002 does not list a second direct follow-up: 003 owns its own downstream-consumer relationship to the later post-closeout simplification decision, which remains non-normative for this feature.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of the reconciliation dimensions from User Story 1 scenario 1 carry an explicit classification and a durable evidence reference before the first implementation or evidence action; 0 actions are chosen from assumed state.
- **SC-002**: 0 evidence-producing or review actions re-execute a boundary the reconciliation classified as complete.
- **SC-003**: the rotation-8-type clue resolves to exactly one of the five FR-004 classifications with cited durable evidence, or the feature honestly reports `BLOCKED_NEED_EVIDENCE`; 0 unclassified clues drive work.
- **SC-004**: 0 agent-created commits, ref changes, or index/stash mutations attributable to this feature; every required commit boundary is human-created. Where the owner contract requires a distinct promotion commit, it remains a distinct human-created immutable boundary before downstream verify; a later accepted closeout/status documentation update may itself require its own human commit. The specification prescribes no exact total number of human commits unless a current owner explicitly does, and never merges the promotion boundary with a later closeout-document boundary merely to reduce commit count.
- **SC-005**: 100% of downstream acceptance evidence claims name the actual evaluated inputs and results they bind to, and an audit finds 0 claims whose named evidence differs from the content actually evaluated.
- **SC-006**: the closeout report classifies each ladder layer (prerequisite, acceptance, candidate, review, promotion, verification, documentation closeout) individually with its attributed state; 0 bare "Q2 complete" claims appear.
- **SC-007**: the owner-document diff contains only status/closure content those owners' contracts require at closeout; 0 acceptance criteria, case sets, or thresholds changed within this feature without the owner-defined restart/rerun they trigger.

## Assumptions

- The post-cutover plan's recorded pre-002 status ("prerequisites complete; the acceptance/review and promotion/verify boundaries open") is the correct starting navigation input for reconciliation — a historical claim consumed as source context, not a current status authority — and reconciliation verifies rather than presumes it; the current Q2 closeout state is whatever the reconciliation record establishes.
- Evidence authority and durability follow the current owner-defined evidence contract and identity semantics. Committed/tracked history and tracked canonical resources are one important evidence class, not the sole possible authority, and trackedness alone confers none: where a current owner explicitly defines an operator-owned or repository-local artifact as authoritative and identity-bound, reconciliation may use it under that owner contract and MUST NOT reject it merely because it is untracked, ignored or local. Conversely, an ephemeral or unidentified local artifact that no current owner defines as authoritative — arbitrary scratch or disposable workspace output — is only a clue: it cannot alone establish a completion classification, and reconciliation MUST NOT promote it into evidence.
- The human with commit authority, the coordinator acceptance role, and the operator review role are available at the boundaries the owners define; a boundary waiting on one of them is an expected, reportable state — not a failure.
- The external services the operations runbook requires for acceptance/bootstrap/verify runs are available per that runbook's own availability and failure semantics; their absence blocks verification rather than passing it.
- Constitution v2.0.0 governs all work under this feature (verified governing at specification time); the completed documentation-history review feature remains frozen under its own historical version and this feature neither reopens nor cites it as current status.
- Repository-consistent feature numbering places this feature at `002`; the deferred Distage/Izumi framework audit is materialized as `003-beautyq-distage-izumi-leverage-audit`, whose execution remains gated on this feature's verified closeout.
