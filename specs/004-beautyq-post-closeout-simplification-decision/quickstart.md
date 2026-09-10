# Feature 004 — Quickstart: Running and Reviewing the Decision

**Feature**: `specs/004-beautyq-post-closeout-simplification-decision`

This is a **decision guide**, not an implementation runbook. It describes how the later decision execution
is run, reviewed, and handed to the human, and where the human verdict boundary sits. It creates no code,
no build change, and no work item.

## What this feature is (and is not)

- **Is**: authoring the decision contract; producing an evidence-backed per-candidate recommendation
  package; presenting it for a human verdict.
- **Is not**: implementing a simplification; editing source, build, tests, runtime, evaluation
  corpora/thresholds, or normative owner documents; opening a work item; re-running the framework audit.
- Even a human-approved disposition authorizes **no** implementation under 004.

## Prerequisites

- None blocking. The 003 Distage/Izumi audit is an **optional, non-blocking** input. Where a candidate
  needs 003 evidence and it is unavailable, the candidate records `INSUFFICIENT_EVIDENCE` naming the missing
  evidence — not a guess.
- Read-only access to: the proposal, the operations runbook, the technical specification, current
  source/build/tests, and `specs/003-.../research/05-synthesis-and-recommendations.md`.
- No product test suite is required. No external resource is required to author the decision.

## Execution Steps (decision execution, later phase)

1. **Re-derive the evaluated state.** Record `HEAD`, branch, and worktree/index state at execution time.
   Do not trust any prompt-supplied revision. If the state has moved since planning, bind findings to the
   new evaluated state.
2. **Derive the candidate inventory.** Start from the still-unresolved proposal sections (§2.1–2.5) and
   admit any genuinely relevant additional candidate surfaced by the current source state or 003 findings.
   Exclude candidates already resolved by the authorized pre-004 cleanup. Record each candidate's
   originating proposal section when one exists. Split/merge/reject-as-decision-unit where evidence shows
   the proposal grouped the wrong mechanics.
3. **Collect bounded evidence per candidate.** Read-only. Use the family evidence questions in
   `research.md`. Keep evidence classes distinct (proposal / normative / source / audit / unresolved). For
   any 003 join, use the primary synthesis first; reopen lower-level 003 artifacts only for exact evidence.
4. **Assess each final candidate across all twelve dimensions** (`data-model.md` → `Assessment`). A
   provisional inventory unit may instead be split, merged, or rejected-as-decision-unit with basis and
   traceability; a rejected provisional unit gets no disposition. Every final candidate receives exactly
   one disposition from the closed six-way set.
5. **Apply the change-oriented gate.** For anything other than `KEEP`/`INSUFFICIENT_EVIDENCE`, populate
   surviving obligations, proof/safety/operational/evidence owners, break/unowned analysis, replacement
   owner, and rollback/operational implications — as assessment fields, not tasks.
6. **Write the synthesis.** Answer what can be removed now, why, and what must remain; a "nothing should be
   removed yet" result is valid and presentable.
7. **Write the decision record** as a single durable artifact
   `specs/004-beautyq-post-closeout-simplification-decision/decision-record.md` with
   `state = AWAITING_HUMAN_DECISION`. Do not split into dozens of per-candidate files. Do not create
   digests/fingerprints/baselines/review bundles.
8. **Stop at the human boundary.** Do not record a verdict and do not implement.

### Qdrant-specific evidence path

For the Qdrant candidate, the proposal's predeclared baseline-vs-full comparison (proposal §2.1, current
lines 47–54: named metric/vector, cutoffs/slices, minimum improvement, max regression budget, stop
conditions, and identical inputs across both modes) is the **required evidence seam** for Qdrant marginal
product value and for any confident disposition that depends on that value. It does not replace the other
candidate assessment dimensions: obligation, demonstrated cost, risk, break/unowned, and ownership
analysis still apply. If that required value evidence is unavailable, the disposition may be
`INSUFFICIENT_EVIDENCE` naming the run and owner. Architectural elegance, framework availability,
historical effort, or the mere existence of semantic-search machinery do not substitute for product-value
evidence.

### Parallelism / economy

Cheap independent evidence mapping may run in parallel only when writers and artifacts are truly
independent. Candidate adjudication stays serialized. Prefer one canonical record over many files.

## Coordinator Review Checklist

- [ ] Resolved feature path is `specs/004-beautyq-post-closeout-simplification-decision`.
- [ ] One evaluated source/architecture state is recorded; every disposition cites it.
- [ ] Candidate inventory derives from proposal + source reality + audit; resolved candidates excluded.
- [ ] Each final candidate: all twelve dimensions + exactly one disposition; rejected provisional units
      carry basis + traceability and no disposition.
- [ ] Dispositions use only the six-way vocabulary.
- [ ] `INSUFFICIENT_EVIDENCE` names missing evidence and owner.
- [ ] Every change-oriented disposition has retained obligations + break/unowned + human-approval text.
- [ ] 003 consumed as evidence only; `C-20` unresolved where relevant; no mechanical label→disposition map.
- [ ] Project and test consolidation assessed as separate questions unless source proves one obligation.
- [ ] Synthesis answers the standing question; no-removal is valid.
- [ ] No source/build/test/runtime/normative-owner changes; decision artifacts only.
- [ ] Record state is `AWAITING_HUMAN_DECISION`; no fabricated verdict.
- [ ] `/speckit.plan` created no `tasks.md`; any `tasks.md` present comes from a real `/speckit.tasks`
      phase following the accepted decision tasks; no `contracts/` tree or ceremony machinery exists.

## Human Verdict Boundary

1. Execution produces the recommendation package.
2. Coordinator reviews it (checklist above).
3. State is `AWAITING_HUMAN_DECISION`; the decision object is OPEN.
4. The human decision-maker/product owner explicitly **approves, rejects, defers, or narrows** (per
   candidate or for the whole package) — the human's words are the decision outcome.
5. The verdict is recorded **verbatim** in the record's `HumanVerdict`; the record may then move to
   `CLOSED` with that verdict as its outcome. After closure the verdict is immutable historical evidence
   bound to the state actually decided; later source drift does not rewrite or reopen it, and using a
   recommendation against a materially changed state requires a new evaluation against that state.
6. **No implementation starts under 004.** An approved disposition may only inform a separately scoped,
   explicitly human-authorized task. No model/agent may convert its recommendation into the human verdict.

If the human is unavailable, the package stays complete and presentable while the record remains OPEN in
`AWAITING_HUMAN_DECISION`; absence of the human is a reportable waiting state, not a failure and never a
fabricated completion.

## Lightweight Validation

Only plan/artifact consistency checks are expected (no product suite):

- Confirm the six disposition values appear as a closed set and no extra state is introduced.
- Confirm each final candidate record carries twelve dimensions and one disposition; rejected provisional
  units carry basis/traceability and no disposition.
- Confirm 003 references resolve to existing 003 artifacts and carry `50e7945-clean` / `1.2.25` bindings.
- Confirm the worktree change set is limited to `specs/004-.../` decision artifacts.
- Report actual `HEAD`/index/worktree state truthfully (Principle I, XI).

## Must-Not-Do Under 004

- No source, build, test, runtime, evaluation-corpus/threshold, or normative-owner edits.
- `/speckit.plan` creates no `tasks.md`; a later real `/speckit.tasks` phase may create the canonical
  feature-local `tasks.md`, and decision execution follows it without creating source/build/test/runtime/
  normative-owner changes. No `contracts/` tree.
- No implementation, even for an approved disposition.
- No Git commit/stage/unstage/stash/reset/checkout/switch or any ref/history mutation.
- No digest/freeze/pass-identity/fingerprint/baseline/review-bundle/Git-revision machinery.
- No silent resolution of 003 `C-20`; no mechanical mapping of 003 labels to dispositions.

## Execution Definition of Done

The decision **execution package** is complete when the single decision record contains a derived candidate
inventory, per-final-candidate twelve-dimension assessments with exactly one disposition each (rejected
provisional units carry basis/traceability only), the synthesis,
and retained-obligation/break-unowned analysis for change-oriented recommendations — with record state
`AWAITING_HUMAN_DECISION`. That is a complete **package**, not a completed **decision**; the decision
object closes only when the human verdict is recorded verbatim.
