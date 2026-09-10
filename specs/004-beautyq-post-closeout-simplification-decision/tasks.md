# Tasks: BeautyQ Post-Closeout Simplification Decision (DECISION-ONLY)

**Feature**: `specs/004-beautyq-post-closeout-simplification-decision`
**Input**: Design documents from `specs/004-beautyq-post-closeout-simplification-decision/`
**Prerequisites**: `spec.md`, `plan.md`, `research.md`, `data-model.md`, `quickstart.md`, `checklists/requirements.md`

**Template adaptation**: This is a **decision-only** feature, not a code feature. Its single durable execution output is
`specs/004-beautyq-post-closeout-simplification-decision/decision-record.md`. Every task below is an
evidence/adjudication/validation task. Allowed actions are: re-derive, derive, collect, resolve, admit, split, merge,
reject, assess, assign, record, synthesize, validate. Tasks MUST NOT implement, refactor, migrate, extract, delete,
edit any normative owner, run product test suites, or open any work item. `/speckit.tasks` itself executes none of
these tasks.

**Tests**: None generated. The spec/plan require no product suite; validation is plan/artifact-consistency and
evidence-reference resolution only.

## Format: `[ID] [P?] [Story?] Description with file path`

- **[P]**: may execute concurrently with another `[P]` task **without a same-file write race** (genuinely independent
  inputs AND independent write targets/scratch outputs; no dependency on an incomplete task).
- **[Story]**: `[US1]` = per-candidate individual assessment (P1); `[US2]` = explicit human decision with
  "nothing removed yet" as a valid outcome (P2). Setup, Foundational, and Validation phases carry no story label.
- **Same-file rule**: multiple tasks contributing to `decision-record.md` are **never** `[P]`, even when they target
  distinct sections. Distinct markdown sections do not make concurrent filesystem writes safe. Such tasks are
  serialized in one writer lane/context.
- **Concurrency unit**: a writer lane (one context per artifact), not an individual same-file slice.

## Non-Authorization Boundary (applies to every task)

- Worktree-only. The only durable execution artifact is `decision-record.md` under
  `specs/004-beautyq-post-closeout-simplification-decision/`. Throwaway evidence notes may be written only under
  gitignored `target/004-scratch/`; they are not decision artifacts and are left uncommitted.
- No task may stage, commit, unstage, stash, reset, clean, checkout/switch, or mutate refs/history. Pre-existing
  human-owned Git/index/worktree state is preserved exactly as found; every report states actual state truthfully
  (Constitution Principle I).
- No task may edit production, test, build, project, runtime, evaluation-corpus/threshold, or normative-owner
  documents — including after any human-approved disposition (spec FR-009).
- Findings and recommendations authorize no change. No disposition becomes a task in this feature. No
  `/speckit.implement` or next Spec Kit phase is invoked or chained.
- Absence of required evidence is recorded as the disposition `INSUFFICIENT_EVIDENCE` naming missing evidence and
  owner — a valid result, never a stalled or failed run.
- Banned everywhere: digest/freeze/pass-identity/fingerprint/baseline/review-bundle machinery, synthetic revision
  IDs, per-candidate durable-file explosion, broad repository rereads, and product test suites.

## Writer Lanes & Concurrency

- `decision-record.md` has exactly **one serialized writer lane**. Tasks that write its canonical content are
  `T001`, `T002`, `T008`–`T012`; the single final-validation task `T013` appends the one `## Validation` section to
  the same file. None of these is `[P]`.
- The **final content writer** is `T012` (it sets the terminal `AWAITING_HUMAN_DECISION` state); no second writer
  ever touches the canonical content concurrently.
- The only concurrency is five read-only evidence lanes writing **distinct** scratch files under
  `target/004-scratch/`: `T003 || T004 || T005 || T006 || T007`. They never write `decision-record.md`.
- Everything from `T008` onward is one serialized chain; no post-lane task is `[P]`.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Bind every later finding to the actually evaluated repository state.

- [ ] T001 Re-derive and record the evaluated source/architecture state in `specs/004-beautyq-post-closeout-simplification-decision/decision-record.md` (section `## Evaluated State`).
  - Read-only inputs: `git rev-parse HEAD`, `git symbolic-ref --short HEAD`, `git status --porcelain`, `git diff --stat`, `git diff --cached --stat`, `git diff --check`, `git check-ignore -v target`.
  - Record `EvaluatedState` exactly per `data-model.md`: `repoRoot`, `headSha`, `branch`, `worktreeState` (distinguish index from worktree where they differ; do not require or force a clean tree), `recordedAt`. Set the record header `state: EVALUATING`.
  - Confirm `decision-record.md` did not already exist. Confirm required owners exist read-only: `docs/gen2/BEAUTYQ_SEARCH_GEN2_SIMPLIFICATION_PLAN.md`, `docs/gen2/BEAUTYQ_SEARCH_GEN2_OPERATIONS.md`, `docs/gen2/BEAUTYQ_SEARCH_GEN2_TECHNICAL_SPEC.md`, `specs/003-beautyq-distage-izumi-leverage-audit/research/05-synthesis-and-recommendations.md`, `.specify/memory/constitution.md`.
  - Do NOT trust `HEAD`/branch values recorded in `plan.md`/`research.md`; re-derive them. Carry the 003 evidence binding (`stateId=50e7945-clean`, `io.7mind.izumi 1.2.25`) only as upstream evidence, never as current state.
  - Exit: exactly one evaluated state is recorded and `headSha` equals the actually observed `HEAD`.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Establish the provisional candidate inventory that all assessment derives from. Blocks all evidence and adjudication.

- [ ] T002 Materialize the provisional candidate inventory facts knowable before evidence and record them in `decision-record.md` (section `## Candidate Inventory (provisional)`).
  - Read-only inputs: proposal `docs/gen2/BEAUTYQ_SEARCH_GEN2_SIMPLIFICATION_PLAN.md` §2.1–§2.5; `specs/003-.../research/05-synthesis-and-recommendations.md`; current source/owner reality referenced by `research.md`.
  - Create one provisional unit per family — `F-QDRANT` (§2.1), `F-TRACE` (§2.2), `F-PROJECT` (§2.3), `F-TEST` (§2.4), `F-DOCS` (§2.5), `F-AUDIT-DISC` (audit-surfaced, no milestone) — plus any genuinely relevant `ADDITIONAL` unit surfaced by the current source state or 003 findings. Refuse to resurrect candidates already resolved by the authorized pre-004 cleanup.
  - Record only the facts knowable now: `entryId`, `family`, `proposalMilestone` (or absent), and an initial navigation `basis`. Leave `derivation` and `resultingCandidateIds` **absent/unfilled** at this stage; `data-model.md` defines `derivation` only as `ADMITTED` | `SPLIT` | `MERGED` | `REJECTED`, so do not invent any placeholder state.
  - This is an **intentionally incomplete intermediate record**. The final `CandidateInventory` invariant (every unit resolved by a derivation, with `resultingCandidateIds` for non-rejected units) is enforced only after `T008`.
  - Do not assess dimensions, do not assign any disposition, and do not convert a 003 label into a state.
  - Exit: every still-unresolved proposal section is represented or explicitly excluded with basis; no `derivation` value is written yet.

**Checkpoint**: Provisional-inventory navigation facts are ready — evidence lanes may begin.

---

## Phase 3: User Story 1 - Per-Candidate Individual Assessment (Priority: P1)

**Goal**: Assess each simplification candidate individually across all twelve dimensions and assign exactly one
disposition from the closed six-way set, never as a bundle.

**Independent Test**: For each final Candidate an auditor finds all twelve dimensions completed, exactly one
disposition, stated confidence/uncertainty, and a named human-approval requirement, with evidence references that
resolve to the evaluated state and the cited audit finding; a rejected provisional unit instead shows traceability
and basis and receives no disposition.

### Bounded Evidence Collection (read-only, parallel-safe)

- [ ] T003 [P] [US1] Collect seam E-1 (F-QDRANT marginal product value) evidence into `target/004-scratch/e1-qdrant.md`.
  - Read-only inputs: proposal §2.1 (current lines 34–58; comparison requirement lines 47–54); `docs/gen2/BEAUTYQ_SEARCH_GEN2_OPERATIONS.md` Qdrant/embedding/cutover obligations; `docs/gen2/BEAUTYQ_SEARCH_GEN2_TECHNICAL_SPEC.md` Qdrant/ES semantics; Qdrant/ES/eval sources; 003 synthesis for any Qdrant-related candidate.
  - Record at the evaluated state: (a) safety/integrity proofs currently established (Required/FullSearch readiness, append-only, baseline preservation, ordering, component preservation, append budget) versus (b) the absent baseline-vs-full relevance delta.
  - Locate the predeclared product-value comparison verbatim: named user-visible metric or ordered metric vector; cutoffs/slices; minimum accepted improvement; maximum regression budget; forbidden-hit/hard-constraint/baseline-preservation/degradation stop conditions; identical application revision, source snapshot, ES generation, corpus, requests, and page policy across baseline-only and Required/FullSearch. If unavailable, mark `UNRESOLVED` with `missingEvidence` + `owner` = product owner + evaluation owner and the exact comparison that would resolve it.
  - Forbidden substitutes: architectural elegance, framework availability, historical effort, existence of semantic-search machinery, append-only activity.
  - Do NOT run an evaluation or product test suite. Write only the scratch note (independent write target; no `decision-record.md` write).

- [ ] T004 [P] [US1] Collect seams E-2 and E-3 (F-TRACE reachability; 003 `C-20` bounded search) evidence into `target/004-scratch/e2-trace.md`.
  - E-2 read-only source inspection at the evaluated state: `search-gen2-core/.../plan/PlanIdentityTrace.scala`; `beautyq-search-gen2-wiring/.../BeautyQSearchPlanCompilationTrace.scala`; `beautyq-search-gen2-wiring/.../BeautyQCandidatePlanTrace.scala`; `search-gen2-contract/.../contract/PlannedAlgebraTrace.scala`; `beautyq-search-gen2-contract/.../contract/BeautyQInputTraceGen2.scala`; `SearchPlanTrace`; response projection consumers (`BeautyQSearchResponseGen2`).
  - Per mechanism record reachability: does any operator/response/artifact/diagnostic consumer exist outside its own tests/docs? Name the request/intent trace views that removals would make unreachable. Confirm fingerprints, typed identities, and observable error diagnostics are NOT traces and stay protected; confirm `PlannedAlgebraTrace` is consumed by response projection and is not a deletion candidate while that holds.
  - E-3: reopen lower-level 003 artifacts ONLY for the exact `C-20` evidence at exact `io.7mind.izumi 1.2.25`; perform the bounded exact-version search named by `C-20` (generic domain-result trace/render surface, or only DI-graph rendering) and record whether that named evidence question was answered **for the 004 decision**.
  - **Upstream 003 `C-20` remains recorded as `INDETERMINATE`.** The evidence lane may supply new 004 evidence, but it MUST NOT reclassify `C-20`, claim `C-20` changed state, or rewrite the completed Feature 003 result; Feature 003 may only change by being separately reopened and amended.
  - Read-only; write only the scratch note (independent write target; no `decision-record.md` write).

- [ ] T005 [P] [US1] Collect seam E-4 (F-PROJECT boundary ownership) evidence into `target/004-scratch/e4-project.md`.
  - Read-only inputs: `build.sbt`, `project/*.sbt`. Per Gen2 project (`search-gen2-{contract,core,transport,elasticsearch,qdrant,eval}`, `beautyq-search-gen2-{contract,materialization,wiring,eval}`) determine what current compilation, dependency, runtime, package-ownership, or evaluation boundary it enforces at the evaluated state.
  - Explicitly test whether `search-gen2-transport` must remain standalone while two backends use it; record what breaks/becomes unowned if a project is merged; record whether any retained negative check stays small and source-owned. Fewer modules is not assumed simpler.
  - Do NOT merge with `F-TEST` evidence unless source proves one coherent obligation.
  - Read-only; write only the scratch note (independent write target; no `decision-record.md` write).

- [ ] T006 [P] [US1] Collect seam E-5 (F-TEST coverage map) evidence into `target/004-scratch/e5-test.md`.
  - Read-only test-source inspection at the evaluated state. Build a coverage map BEFORE recommending deletion: per invariant name one primary owner; identify tests that differ only by rendering layer, private-wrapper construction boundary, duplicated fixture inventory, module-source scanning, or historical delivery phase; identify firewall/build-ownership tests and meaningful corpus cases.
  - Record which tests are the single primary owner of a distinct invariant and which are genuinely different proof layers. Do not reduce meaningful corpus cases to hit a line target. Fewer tests is not assumed simpler.
  - Do NOT merge with `F-PROJECT` evidence unless source proves one coherent obligation. Do NOT run the product suite.
  - Read-only; write only the scratch note (independent write target; no `decision-record.md` write).

- [ ] T007 [P] [US1] Collect seam E-6 (F-DOCS + F-AUDIT-DISC docs/discoverability) evidence into `target/004-scratch/e6-docs-auditdisc.md`.
  - F-DOCS: read proposal §2.5 and the current normative owners (`docs/gen2/BEAUTYQ_SEARCH_GEN2_TECHNICAL_SPEC.md`, `docs/gen2/BEAUTYQ_SEARCH_GEN2_OPERATIONS.md`, domain-authoring/onboarding docs). Determine whether each candidate's cost is documentary duplication only or hides a live source obligation; identify the surviving canonical owner (Principle II/X). Do NOT re-open `001-beautyq-doc-history-review`.
  - F-AUDIT-DISC: use 003 synthesis `R-01`/`C-05` (`addDependency` discoverability; `remedyCandidates=DOCS`; `WELL_USED`/`DISCOVERABLE`; Medium confidence) as evidence only. Determine whether it is a genuine current-complexity decision unit for 004 or evidence feeding F-DOCS/FR-012; it MAY be rejected as a decision unit with recorded basis. `R-01` is NOT implementation permission.
  - Keep F-DOCS and F-AUDIT-DISC in one lane because they share seam E-6. Read-only; write only the scratch note (independent write target; no `decision-record.md` write).

### Serialized Candidate Adjudication

- [ ] T008 [US1] Resolve bounded evidence seams and admit/split/merge/reject provisional units to produce the final decision units in `decision-record.md` (sections `## Candidate Inventory (derivation)` and `## Candidates`).
  - Consume scratch notes from T003–T007. Serialized single writer of `decision-record.md`.
  - For each provisional unit fill `derivation` ∈ `ADMITTED` | `SPLIT` | `MERGED` | `REJECTED` with a concrete `basis` and `resultingCandidateIds` per `data-model.md`. This completes the provisional inventory; no unit may remain unresolved after this task.
  - A `REJECTED` provisional unit produces NO final Candidate and receives NO disposition (record basis/traceability only). `INSUFFICIENT_EVIDENCE` is NOT a rejection label.
  - For each surviving final Candidate record `candidateId`, `title`, `family`, `proposalMilestone` (or absent), `decisionUnitBasis`, and `auditJoins` (exact mechanic match only; carry 003 `stateId`/version). Complete the final Candidate list skeleton; do NOT assess dimensions or assign dispositions yet.
  - Keep `F-PROJECT` and `F-TEST` as separate decision units unless source proves one coherent obligation. Carry any additional 004 evidence about `C-20` as evidence only, without changing upstream 003's `INDETERMINATE` classification. Do not stall on missing evidence: it carries forward to `INSUFFICIENT_EVIDENCE`.

- [ ] T009 [US1] Assess every final Candidate across all twelve dimensions **and** assign its single disposition in `decision-record.md`.
  - For each final Candidate populate exactly the twelve `data-model.md` dimensions: (1) `currentObligation`; (2) `obligationStillExists` (yes/no + evidence at the evaluated state); (3) `demonstratedCost`; (4) `valueEvidence`; (5) `auditClassification` (or `N/A` with basis); (6) `breakOrUnownedOnRemoval`; (7) `docsOnlySufficient`; (8) `existingFrameworkPrimitivePreferred`; (9) `beautyqLocalRetentionPreferred`; (10) `recommendedDisposition`; (11) `confidenceUncertainty`; (12) `humanApprovalRequirement`.
  - Dimension 10 is populated **exactly once here**, as the single value of the Candidate's one `Recommendation.disposition`. There is no separate disposition-assignment or "finalization" pass and no working-versus-final disposition distinction; the value written is the disposition.
  - Use only the closed six-way vocabulary: `KEEP`, `SIMPLIFY_LOCALLY`, `REPLACE_WITH_EXISTING_FRAMEWORK`, `FRAMEWORK_FOLLOWUP_REQUIRED`, `REMOVE`, `INSUFFICIENT_EVIDENCE`. Invent no additional state. `KEEP` and `INSUFFICIENT_EVIDENCE` are first-class with equal standing; a no-removal outcome is valid and MUST NOT be forced toward change. Every candidate gets exactly one disposition; no bundle-level accept/reject.
  - `INSUFFICIENT_EVIDENCE` MUST name `missingEvidence` and `owner`; it is a valid result, never a stall. A `REJECTED` provisional unit still has no disposition.
  - Attribute every cost/overengineering/deletion-benefit/value claim to evidence at the evaluated state or to the proposal as its claim. A proposal diagnosis MUST NOT be restated as established architecture fact.
  - Record `confidence`, `sourceStateRef` (the evaluated state), and `auditEvidenceRef` where applicable. Do not map 003 labels mechanically (`WELL_USED` ≠ automatic `KEEP`; `BEAUTYQ_SPECIFIC` ≠ automatic `KEEP`; absence of `UNDERUSED` does not prohibit local simplification).
  - `F-QDRANT`: the predeclared baseline-vs-full product-value comparison (T003) is a required evidence seam for any value-dependent conclusion, but it does NOT replace dimensions 1–9; if absent, dimensions 4/11 record the absence and dimension 10 is `INSUFFICIENT_EVIDENCE`.
  - Serialized writer.

- [ ] T010 [US1] Apply the change-oriented obligation/break/unowned gate for every non-`KEEP`/non-`INSUFFICIENT_EVIDENCE` disposition in `decision-record.md`.
  - Populate the `Recommendation`/`RetainedObligation` fields per `data-model.md`: surviving public/compatibility contracts; proof/validation ownership; safety properties; operational obligations; current evidence obligations; `breakOrUnownedOnRemoval`; `replacementOwner` where something becomes unowned; and `rollbackOperationalImplications` for any separately authorized implementation.
  - Each `RetainedObligation` records `kind` ∈ `PUBLIC_CONTRACT`/`PROOF_OWNER`/`SAFETY_PROPERTY`/`OPERATIONAL_OBLIGATION`/`EVIDENCE_OBLIGATION`, a description, and the current canonical `owner` (navigation ref, not copied policy).
  - These are assessment fields, NOT implementation tasks; no work item is created and no change is authorized.

**Checkpoint**: The complete per-candidate assessment (twelve dimensions, one disposition each, change gate applied)
exists and is independently reviewable.

---

## Phase 4: User Story 2 - Explicit Human Decision with "Nothing Removed Yet" as a Valid Outcome (Priority: P2)

**Goal**: Produce a synthesis answering what can safely be removed now, why, and what must remain — with a
no-removal outcome first-class — and stop OPEN at `AWAITING_HUMAN_DECISION` without fabricating a verdict.

**Independent Test**: Read the synthesis alone: per-candidate dispositions, retained obligations, and an aggregate
answer including a null-result path; every change-oriented recommendation names its human approval; the record is
`AWAITING_HUMAN_DECISION` with no content reading as approved/decided/implementation-may-begin.

- [ ] T011 [US2] Write the synthesis in `decision-record.md` (section `## Synthesis`).
  - Answer the standing question: what complexity can BeautyQ safely remove now, why (evidence-based), and what must remain. Populate `canRemoveNow`, `mustRemain`, `nullResultValid`, `aggregateStatement` per `data-model.md`.
  - "Nothing should be removed yet" (every candidate `KEEP`/`INSUFFICIENT_EVIDENCE`) is a valid, presentable aggregate result, not a failed run. State per-candidate dispositions and retained obligations.
  - Every change-oriented recommendation names its required human approval. Do not phrase anything as approved, decided, or "implementation may now begin".

- [ ] T012 [US2] Finalize the canonical decision record as the single serialized final content writer of `specs/004-beautyq-post-closeout-simplification-decision/decision-record.md`.
  - Set record `state: AWAITING_HUMAN_DECISION`; keep `humanVerdict` absent (OPEN); set `explicitNoImplementation: true`. Record that approve/reject/defer/narrow are the valid human verdicts and remain outstanding.
  - Verify no content anywhere reads as self-authorizing, "already approved", "decided", or "implementation may now begin". No model/agent may populate or phrase a `HumanVerdict`.
  - State explicitly that no implementation is authorized under 004 and any follow-up requires separate explicit human authorization and a separately scoped task.

**Checkpoint**: The recommendation content is complete; the decision object is OPEN awaiting the human.

---

## Phase 5: Final Validation

**Purpose**: One compact validation of the complete decision package and the state boundary. Read-only checks against
the record and the repository; results are appended as a single `## Validation` section.

- [ ] T013 Validate the complete decision package and stop condition; append one compact `## Validation` section to `decision-record.md`.
  - Inventory and assessment: provisional inventory traceability complete with resolved `derivation` values; every final Candidate carries all twelve dimensions and exactly one six-way disposition; every rejected provisional unit has no disposition.
  - Change gate: every change-oriented disposition preserves retained obligations and names break/unowned effects and the replacement owner where needed.
  - Evidence and attribution: every disposition is bound to the actually evaluated state (`headSha`) and the 003 finding where applicable; proposal claims remain attributed, not restated as architecture facts; 003 is consumed as evidence only; upstream 003 `C-20` remains recorded as `INDETERMINATE` even if this feature collected additional evidence for its named question; no mechanical 003-label→disposition mapping.
  - Feature invariants: the Qdrant product-value seam is handled correctly (present for value-dependent conclusions and not a substitute for the other dimensions); `F-PROJECT` and `F-TEST` questions are not conflated without source evidence; the synthesis is complete and answers what can be simplified/removed now and what must remain; record `state = AWAITING_HUMAN_DECISION`; no fabricated `HumanVerdict`; no implementation authorization.
  - State integrity (read-only): `git rev-parse HEAD`, `git status --porcelain`, `git diff --stat`, `git diff --cached --stat`, `git diff --check`, `git diff --cached --check`, `git check-ignore -v target`; confirm no source/build/test/runtime/normative-owner mutation attributable to 004, the worktree change set is decision artifacts only, no agent commit/stage/unstage/stash/reset/checkout/switch or ref/history mutation, and scratch notes live only under gitignored `target/004-scratch/`; compare `HEAD` to the T001 recorded `headSha` and report exact current `HEAD`/index/worktree truth.
  - Fold in the stop condition: confirm no `/speckit.implement` was invoked, no next Spec Kit phase is chained, no human verdict was recorded, and no work item was opened. The decision object remains OPEN awaiting the human; absence of the human is a reportable waiting state, not a failure. This is the final task; nothing runs after it.
  - Do not run product tests. Report scope, result, and any unresolved item in the one `## Validation` section.

---

## Dependencies & Execution Order

### Ordered Chain

- `T001 → T002 → (T003 ‖ T004 ‖ T005 ‖ T006 ‖ T007) → T008 → T009 → T010 → T011 → T012 → T013`

### Phase Dependencies

- **Setup (T001)**: starts immediately; no dependency.
- **Foundational (T002)**: depends on T001; blocks all evidence and adjudication.
- **US1 (T003–T010)**: T003–T007 depend on T002 and may run in parallel; T008–T010 depend on the relevant evidence
  and are serialized on `decision-record.md`.
- **US2 (T011–T012)**: depends on T010; T012 is the single final content writer.
- **Validation (T013)**: depends on T012; final task.

### Parallel Opportunities

- Only **T003, T004, T005, T006, T007** are `[P]`: genuinely independent evidence questions and distinct scratch
  write targets under `target/004-scratch/`; none writes `decision-record.md`.
- `T005` (project) and `T006` (test) remain separate evidence questions and parallel lanes; they share only incidental
  read-only `build.sbt` access, which is not a write race, and source must prove a single coherent obligation before
  they are ever merged.
- No other task is `[P]`. In particular `T008`–`T013` all write the same `decision-record.md` and are serialized in
  one writer lane.

## Parallel Execution Example

```text
# After T002, five read-only evidence lanes run concurrently (distinct scratch files):
T003 (e1-qdrant)  ‖  T004 (e2-trace)  ‖  T005 (e4-project)  ‖  T006 (e5-test)  ‖  T007 (e6-docs-auditdisc)

# Then one serialized chain (single writer lane for decision-record.md):
T008 -> T009 -> T010 -> T011 -> T012 -> T013
```

## Artifact Ownership

| Artifact | Writer tasks | Serialization mechanism |
| --- | --- | --- |
| `specs/004-.../decision-record.md` (canonical content) | T001, T002, T008, T009, T010, T011, T012 (final content writer) | one serialized writer lane |
| `specs/004-.../decision-record.md` (`## Validation`) | T013 | appended serially after T012 |
| `target/004-scratch/e1-qdrant.md` | T003 | independent scratch (parallel lane) |
| `target/004-scratch/e2-trace.md` | T004 | independent scratch (parallel lane) |
| `target/004-scratch/e4-project.md` | T005 | independent scratch (parallel lane) |
| `target/004-scratch/e5-test.md` | T006 | independent scratch (parallel lane) |
| `target/004-scratch/e6-docs-auditdisc.md` | T007 | independent scratch (parallel lane) |

No per-candidate durable files, no per-pass manifests, no digest/freeze/baseline artifacts, no evidence-identity
chains, no `contracts/` tree.

## Validation Coverage Map

| Required check | Task |
| --- | --- |
| Provisional inventory traceability and resolved derivations | T013 |
| Every final Candidate has 12 dimensions + one six-way disposition | T013 |
| Rejected provisional units have no disposition | T013 |
| Change-oriented obligations + break/unowned + replacement owner | T013 |
| Evidence bound to the actually evaluated repository state | T013 |
| Proposal claims remain attributed, not architecture facts | T013 |
| 003 evidence remains evidence only; upstream `C-20` stays `INDETERMINATE` | T013 |
| Qdrant value seam handled correctly | T013 |
| Project/test questions not conflated without evidence | T013 |
| Synthesis complete; state = `AWAITING_HUMAN_DECISION`; no fabricated verdict | T013 |
| No implementation authorization / no `/speckit.implement` / no work item | T013 |
| No source/build/test/runtime/normative-owner mutation; exact HEAD/index/worktree truth | T013 |

## User-Story & Requirement Traceability

| Spec user story / requirement | Satisfied primarily by | Validated by |
| --- | --- | --- |
| US1 — per-candidate individual assessment, twelve dimensions, one disposition (P1) | T003–T010 | T013 |
| US2 — explicit human decision, no-removal outcome valid (P2) | T011, T012 | T013 |
| FR-002 evaluated-state binding | T001 | T013 |
| FR-003 / FR-004 per-candidate; inventory vs final Candidate; rejected unit no disposition | T002, T008 | T013 |
| FR-005 closed six-way vocabulary; KEEP/INSUFFICIENT_EVIDENCE first-class | T009 | T013 |
| FR-007 / FR-012 audit evidence cited; framework-reuse preference per audit | T004, T007, T009 | T013 |
| FR-008 change-oriented gate: retained obligations + break/unowned | T010 | T013 |
| FR-010 / FR-013 human boundary; synthesis; no-removal valid | T011, T012 | T013 |
| FR-001 / FR-014 / FR-009 no source/owner change; worktree-only; no implementation | every task | T013 |
| SC-001 … SC-008 measurable outcomes | T001–T012 | T013 |

## Implementation Strategy

This feature is a single decision package, not an incremental delivery:

1. Complete T001–T002 to fix the evaluated state and the provisional inventory navigation facts.
2. Run the five bounded evidence lanes (T003–T007) once.
3. Run the serialized adjudication (T008–T010), then the synthesis and final record write (T011–T012).
4. Run the one final validation task (T013).

Content becomes complete when T012 sets `AWAITING_HUMAN_DECISION`. The Feature 004 execution package is
review-ready only after the final validation task T013 passes; then execution stops at `AWAITING_HUMAN_DECISION`.
`INSUFFICIENT_EVIDENCE` on any candidate is a valid package result and never blocks the remaining tasks.

## Notes

- `[P]` applies only to T003–T007; all `decision-record.md` tasks are serialized by the same-file rule.
- One owner for disposition: dimension 10 and the single `Recommendation.disposition` are written together in T009.
- `INSUFFICIENT_EVIDENCE` is a first-class, non-stalling result for any real final Candidate lacking required evidence.
- Upstream 003 `C-20` remains `INDETERMINATE`; 004 may add evidence but never reclassifies it.
- No proposal diagnosis becomes an architecture fact; no synthetic revision IDs, baselines, digests, or pass identities are introduced.
- No `/speckit.implement`, no work item, and no Git/index/ref mutation is performed by any task.
