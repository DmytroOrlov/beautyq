# Feature 004 — Data Model: Decision Record Shapes and Invariants

**Feature**: `specs/004-beautyq-post-closeout-simplification-decision`

**Phase**: `/speckit.plan` Phase 1. Conceptual record shapes (not code) for the execution-phase decision
package. These shapes make the twelve spec assessment dimensions executable and keep the human verdict
separate from agent recommendations.

No external contract is defined, so no `contracts/` tree is created. The single durable execution artifact
is a `DecisionRecord`; per-candidate records are embedded, not split into separate files.

## Entity Overview

```text
DecisionRecord
├── EvaluatedState (exactly one)
├── CandidateInventory[] (provisional derivation: split/merged/rejected/admitted, with traceability + basis;
│                        a rejected provisional unit is NOT a final Candidate and carries no disposition)
├── Candidates[] (final material decision units)
│   ├── Assessment (all 12 dimensions)
│   ├── EvidenceRefs[] (003 joins + source/owner anchors)
│   └── Recommendation (exactly one Disposition + confidence + human-approval requirement)
│       └── RetainedObligation[] (required for change-oriented dispositions)
├── Synthesis (aggregate answer; null-result valid)
└── HumanVerdict (absent while OPEN; verbatim when recorded)
```

## EvaluatedState

The exact source/architecture state every disposition is bound to (Principle V).

| Field | Type | Rule |
| --- | --- | --- |
| `repoRoot` | path | Repository root |
| `headSha` | git sha | Re-derived at execution time; never trusted from a prompt |
| `branch` | string | Actual checked-out branch/identifier |
| `worktreeState` | enum/notes | Clean vs. materialized patch; distinguish index from worktree if they differ |
| `recordedAt` | timestamp | When the state was observed |

**Invariants**
- Exactly one evaluated state anchors a decision record; its authority is the concrete observed state
  (repo root, `HEAD` SHA, branch, index/worktree state, `recordedAt`), not a synthetic identity token.
- Evidence and dispositions stay attached to the exact state actually evaluated and are never silently
  re-attributed to a later state; source-state drift **before a human verdict** marks affected evidence
  superseded and returns the affected candidates to evaluation.
- `headSha` reported must match the state actually evaluated.

## EvidenceClass

Distinguishes the five evidence classes so proposal claims never masquerade as fact.

| Value | Meaning |
| --- | --- |
| `PROPOSAL_CLAIM` | Attributed to the non-normative proposal |
| `NORMATIVE_OBLIGATION` | Owned by an operations/technical owner |
| `SOURCE_REALITY` | Read-only observation at the evaluated state |
| `AUDIT_EVIDENCE` | 003 classification/finding (with its own state/version binding) |
| `UNRESOLVED` | Evidence execution must still collect |

## Candidate Inventory (provisional derivation)

The provisional derivation entry may be split, merged, or rejected as a decision unit with traceability
and a recorded basis. A **rejected provisional unit is not a final Candidate** and therefore receives
**no** disposition. `INSUFFICIENT_EVIDENCE` is the disposition for a *real current candidate* whose
decision lacks necessary evidence — it is not a rejection label.

| Field | Type | Rule |
| --- | --- | --- |
| `entryId` | id | Unique within the inventory |
| `family` | enum | Provisional family: `F_QDRANT`, `F_TRACE`, `F_PROJECT`, `F_TEST`, `F_DOCS`, `F_AUDIT_DISC`, or `ADDITIONAL` |
| `proposalMilestone` | optional section ref | Traceability to originating proposal section; absent for audit-surfaced candidates |
| `derivation` | enum | `SPLIT`, `MERGED`, `REJECTED`, or `ADMITTED` |
| `basis` | string | Concrete evidence for the derivation decision |
| `resultingCandidateIds` | `candidateId[]` | Final candidate(s) produced; empty when `REJECTED` |

## Candidate (Final Decision Unit)

One simplifiable mechanism or coherent mechanism group that survives provisional derivation.

| Field | Type | Rule |
| --- | --- | --- |
| `candidateId` | id | Unique within the record |
| `title` | string | Human-readable |
| `family` | enum | Provisional family: `F_QDRANT`, `F_TRACE`, `F_PROJECT`, `F_TEST`, `F_DOCS`, `F_AUDIT_DISC`, or `ADDITIONAL` |
| `proposalMilestone` | optional section ref | Traceability to originating proposal section; absent for audit-surfaced candidates |
| `decisionUnitBasis` | string | Why this is a single decision unit (split/merge basis) |
| `auditJoins` | `AuditJoin[]` | Direct joins to relevant 003 candidate(s)/evidence where a real mechanic match exists |
| `assessment` | `Assessment` | All twelve dimensions |
| `recommendation` | `Recommendation` | Exactly one disposition |

**Invariants**
- A final Candidate always carries the full twelve-dimension `Assessment` and exactly one disposition.
- A rejected provisional unit carries its rejection basis/traceability but no candidate disposition.
- Execution MAY split, merge, or reject a proposal milestone as a decision unit when evidence shows the
  proposal grouped the wrong mechanics; traceability is preserved where a milestone exists.
- Candidate assessment is individual; no bundle-level accept/reject.

## AuditJoin

Direct, economic join to 003 evidence only where a real mechanic match exists.

| Field | Type | Rule |
| --- | --- | --- |
| `auditCandidateId` | string | e.g. `C-20`, `C-05` |
| `classification` | enum | One of 003's five-way labels, or `INDETERMINATE`; recorded as evidence, never permission |
| `evidenceRef` | path/anchor | Reopen lower-level 003 artifacts only for this exact evidence |
| `stateId` / `versionRef` | string | 003's binding (`50e7945-clean`, `io.7mind.izumi 1.2.25`) |

**Invariants**
- `WELL_USED` does not automatically yield `KEEP`; `BEAUTYQ_SPECIFIC` does not automatically yield `KEEP`.
- The single `INDETERMINATE` 003 candidate (`C-20`) stays unresolved where relevant.

## Assessment (twelve dimensions)

| # | Field | Rule |
| --- | --- | --- |
| 1 | `currentObligation` | The current obligation the mechanism serves |
| 2 | `obligationStillExists` | Yes/no + source/owner evidence at the evaluated state |
| 3 | `demonstratedCost` | Demonstrated maintenance/complexity cost (not asserted) |
| 4 | `valueEvidence` | Product/operator/quality/risk value evidence (or absence, attributed) |
| 5 | `auditClassification` | Relevant 003 classification/evidence, or `N/A` with basis |
| 6 | `breakOrUnownedOnRemoval` | What breaks / becomes unowned if removed |
| 7 | `docsOnlySufficient` | Whether documentation-only consolidation is sufficient |
| 8 | `existingFrameworkPrimitivePreferred` | Whether reuse of an EXISTING framework primitive is preferable |
| 9 | `beautyqLocalRetentionPreferred` | Whether BeautyQ/repository-local retention is preferable |
| 10 | `recommendedDisposition` | Exactly one of the six dispositions |
| 11 | `confidenceUncertainty` | Confidence level + named remaining uncertainty |
| 12 | `humanApprovalRequirement` | Explicit human-approval requirement text |

**Invariants**
- All twelve dimensions are present on every final Candidate.
- No proposal diagnosis is restated as established fact; cost/overengineering/deletion-benefit claims are
  evidence-backed or attributed to the proposal as its claim.
- Dispositions cite the evaluated state and, where applicable, the audit finding.

## Disposition (closed set)

```text
KEEP
SIMPLIFY_LOCALLY
REPLACE_WITH_EXISTING_FRAMEWORK
FRAMEWORK_FOLLOWUP_REQUIRED
REMOVE
INSUFFICIENT_EVIDENCE
```

**Invariants**
- No additional states are invented.
- `KEEP` and `INSUFFICIENT_EVIDENCE` are first-class; a no-removal aggregate result is valid.
- `INSUFFICIENT_EVIDENCE` MUST name `missingEvidence` and `owner`; it is a valid result, not a failed run.
- `FRAMEWORK_FOLLOWUP_REQUIRED` names the separate follow-up owner (documentation vs framework feature) and
  remains a recommendation for that future decision, not an opened work item.

## Recommendation

The agent/coordinator product: a recommendation until a human verdict exists.

| Field | Type | Rule |
| --- | --- | --- |
| `disposition` | `Disposition` | Exactly one |
| `confidence` | enum + text | Confidence and named uncertainty |
| `humanApprovalRequirement` | text | Explicit statement the human must approve/reject/defer/narrow |
| `retainedObligations` | `RetainedObligation[]` | Required for change-oriented dispositions |
| `breakOrUnownedOnRemoval` | text | Required for change-oriented dispositions |
| `replacementOwner` | optional | Required when something becomes unowned |
| `rollbackOperationalImplications` | optional text | Noted for any separately authorized implementation |
| `sourceStateRef` | ref | The evaluated state |
| `auditEvidenceRef` | optional ref | Audit finding used, where applicable |

**Change-oriented gate**: any disposition other than `KEEP`/`INSUFFICIENT_EVIDENCE` MUST populate:
surviving public/compatibility contracts; proof/validation ownership; safety properties; operational
obligations; evidence obligations still current; break/unowned analysis; replacement owner where
applicable; rollback/operational implications. These are assessment fields, **not** implementation tasks.

## RetainedObligation

| Field | Type | Rule |
| --- | --- | --- |
| `kind` | enum | `PUBLIC_CONTRACT` / `PROOF_OWNER` / `SAFETY_PROPERTY` / `OPERATIONAL_OBLIGATION` / `EVIDENCE_OBLIGATION` |
| `description` | string | The obligation that survives |
| `owner` | string | Current canonical owner (navigation ref, not a copy of policy) |

## Synthesis

| Field | Type | Rule |
| --- | --- | --- |
| `canRemoveNow` | list/string | What complexity can safely be removed now, why |
| `mustRemain` | list/string | What must remain |
| `nullResultValid` | boolean | `true` when every candidate is KEEP/INSUFFICIENT_EVIDENCE |
| `aggregateStatement` | string | Answers the standing question; "nothing should be removed yet" is valid |

**Invariant**: A no-removal result is presentable with equal standing, not flagged as incomplete.

## HumanVerdict

Absent while the decision object is OPEN. Recorded verbatim as the outcome.

| Field | Type | Rule |
| --- | --- | --- |
| `verdict` | enum | `APPROVE` / `REJECT` / `DEFER` / `NARROW` |
| `scope` | enum | Per-candidate or whole package |
| `verbatimText` | string | The human's verdict recorded verbatim |
| `recordedBy` | human role | Must be the human decision-maker/product owner |
| `recordedAt` | timestamp | When recorded |

**Invariants**
- No agent/model/machine source may populate or phrase a `HumanVerdict`.
- An approving verdict authorizes **no** implementation under this feature (FR-009).
- A verdict may close the decision object; it never opens a work item by itself.

## DecisionRecord

| Field | Type | Rule |
| --- | --- | --- |
| `feature` | ref | `specs/004-...` |
| `evaluatedState` | `EvaluatedState` | Exactly one |
| `candidates` | `Candidate[]` | Each with exactly one disposition |
| `synthesis` | `Synthesis` | Required |
| `state` | enum | `EVALUATING` / `AWAITING_HUMAN_DECISION` / `CLOSED` |
| `humanVerdict` | optional `HumanVerdict` | Present only when recorded by the human |
| `explicitNoImplementation` | boolean | Always `true` under 004 |

### State Transitions

```text
EVALUATING ──(package complete, no verdict)──────────> AWAITING_HUMAN_DECISION   [OPEN]
AWAITING_HUMAN_DECISION ──(human verdict)────────────> CLOSED                    [verdict = outcome]
(source-state drift before a verdict)────────────────> EVALUATING                [affected evidence superseded]
CLOSED + later source drift (new evaluation invoked)─> new DecisionRecord        [old verdict preserved]
```

**Invariants**
- While no human verdict exists, `state = AWAITING_HUMAN_DECISION` and the decision object is OPEN; the
  package may be complete but the decision is not.
- **Before** a human verdict, source-state drift that invalidates material evidence marks it superseded
  and returns the affected candidates to evaluation; dispositions are never silently re-attributed to
  another source state (spec.md, "Source-state drift before a human verdict").
- **After** a human verdict (`CLOSED`), the recorded verdict is immutable historical evidence bound to
  the state actually decided; later source drift does not rewrite or reopen it.
- Before recommendations are used against a materially changed state, a new evaluation must be performed
  against that state.
- An `EVALUATING`/`AWAITING_HUMAN_DECISION` record never carries an old `HumanVerdict` as the verdict for
  its newly evaluated state; a new evaluation is a distinct `DecisionRecord` whose `HumanVerdict` is
  absent until a new human verdict.
- Only a human-sourced verdict moves the record to `CLOSED`.
- A `CLOSED` record still authorizes no implementation under 004.
- No digest/freeze/pass-identity/fingerprint/baseline machinery is part of this model.

## Validation Rules (execution-phase)

1. Every decision-phase action records the evaluated source/architecture state (SC-001).
2. Every final Candidate carries all twelve dimensions and exactly one disposition; a rejected provisional
   inventory unit carries traceability + basis and no disposition (SC-002).
3. No proposal diagnosis appears as established fact; claims are evidence-backed or attributed (SC-003).
4. Every change-oriented disposition has retained obligations, break/unowned analysis, and a named human
   approval requirement (SC-004).
5. Zero source/build/test/runtime/normative-owner modifications; worktree change set is decision artifacts
   only; zero agent commits/ref/index/stash mutations (SC-005).
6. Synthesis answers the standing question; KEEP/INSUFFICIENT_EVIDENCE aggregate and no-removal outcomes
   are valid (SC-006).
7. Every disposition cites the evaluated state and, where applicable, the audit finding (SC-007).
8. All prepared dispositions are recommendations; no fabricated approval; while no verdict exists the
   record is OPEN in `AWAITING_HUMAN_DECISION`; recorded verdicts appear verbatim and authorize zero
   implementation actions (SC-008).
