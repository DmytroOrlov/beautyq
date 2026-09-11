# Feature 004 — Post-Closeout Follow-Up Queue

**Type**: planning/queue document. This is **not** a decision record and **not** a normative
architecture document. It orders optional, deferred, hygiene, and parked work that remains after the
completed Feature 004 closeout and the reconciled residual-commitments audit.

**State basis**: reconciled at the committed residual audit
(`evidence/residual-commitments-audit/RESIDUAL-COMMITMENTS.md` + `RC-001-FEATURE-001-FORENSIC.md`).
Current source, tests, and normative documents remain current truth.

**Feature 004 relationship**: Feature 004 remains `CLOSED` / `humanVerdict = APPROVE` /
`explicitNoImplementation = true`. This document does **not** reopen it, change any disposition, or
create implementation authorization.

---

## Queue status vocabulary

| Status | Meaning |
| --- | --- |
| `READY_HYGIENE` | Bounded low-risk cleanup that needs no new product/research decision. |
| `READY_BUT_REQUIRES_IMPLEMENTATION_AUTHORIZATION` | Scope/evidence already sufficient, but implementation must not begin without explicit human authorization. |
| `OPTIONAL_EVIDENCE` | Useful only if the human wants the answer needed for a later decision. |
| `OPTIONAL_HYGIENE` | Cosmetic/documentary consistency work whose omission leaves no product/runtime gap. |
| `PARKED` | Deliberately deprioritized by the human; no automatic reactivation. |
| `NO_ACTION_UNLESS_NEEDED` | Unresolved question with no current reason to spend work on it. |
| `COMPLETE` | Accepted hygiene outcome landed; retained for traceability only. |

These labels are queue-management only. They never rewrite a Feature 004 disposition.

---

## Queue table

| Order | ID | Status | What would cause us to start it | Authorization |
| ---: | --- | --- | --- | --- |
| `—` | `FUP-01` | `COMPLETE` | Done — stale current-source comments corrected. | Completed hygiene outcome; no further authorization. |
| 2 | `FUP-08` | `READY_BUT_REQUIRES_IMPLEMENTATION_AUTHORIZATION` | FUP-06 demonstrated substantial marginal relevance value but one human-confirmed trust-breaking semantic mismatch remains. | New explicit human implementation authorization is mandatory. |
| 3 | `FUP-02` | `READY_BUT_REQUIRES_IMPLEMENTATION_AUTHORIZATION` | Explicit human authorization of the approved `SIMPLIFY_LOCALLY` transformation. | New explicit human implementation authorization is mandatory. |
| 4 | `FUP-03` | `OPTIONAL_EVIDENCE` | Human wants further non-trace test consolidation after FUP-02. | Separate human decision to spend evidence/review effort. |
| `—` | `FUP-04` | `COMPLETE` | Done — Feature 003/004 completed-task checkboxes reconciled. | Completed hygiene outcome; no further authorization. |
| `—` | `FUP-05` | `COMPLETE` | Done — Feature 001 stale pre-finalization framing superseded. | Completed hygiene outcome; no further authorization. |
| `—` | `FUP-06` | `COMPLETE` | Done — current Gen2 Qdrant marginal-value evidence produced; substantial relevance value, acceptance verdict `HARM`. | Completed evidence outcome; no runtime/default/rollout authorization. |
| 7 | `FUP-07` | `NO_ACTION_UNLESS_NEEDED` | A future design decision actually depends on the framework answer. | None until then. |

Parked, not part of the numbered queue: **D1 second production domain** (see below).

---

## Authorization boundary

This document **prioritizes possible work; it authorizes no work.**

- `FUP-02` still needs **explicit human implementation authorization**.
- `FUP-08` requires **new explicit human implementation authorization**; queueing it authorizes no
  implementation and no Qdrant default/activation/rollout change.
- `FUP-03` requires a separate human decision to spend evidence effort.
- `FUP-01`, `FUP-04` and `FUP-05` are completed hygiene outcomes, and `FUP-06` is a completed
  evidence outcome; none authorizes further work or changes any Feature 004 disposition.
- D1 remains **human-deprioritized / `PARKED`**; no automatic reactivation.
- This document does not reopen Feature 004. Feature 004 remains `CLOSED` / `APPROVE` /
  `explicitNoImplementation = true`.

---

## FUP-01 — Correct stale current-source comments

- **Origin**: `RC-005` = `STALE_FOSSIL`.
- **Queue status**: `COMPLETE`.
- **Outcome**: Comment/scaladoc-only correction, no behavior change. `BeautyQEvaluationPolicy.scala` now
  distinguishes the existing protected holdout / protected-acceptance machinery from the still-absent
  accepted relevance thresholds; `BeautyQQdrantPolicy.scala` no longer calls the already-owned Qdrant
  transport/physical lifecycle "later bricks". Durable owner: the corrected source comments in
  `beautyq-search-gen2-eval` and `beautyq-search-gen2-wiring`.

---

## FUP-02 — Implement approved trace simplification

- **Origin**: `RC-002` = `INTENTIONALLY_DEFERRED`.
- **Feature 004**: `C-TRACE-REDUNDANT-DIAGNOSTICS = SIMPLIFY_LOCALLY`.
- **Queue status**: `READY_BUT_REQUIRES_IMPLEMENTATION_AUTHORIZATION`.
- **Priority**: 3 — high-value actual code simplification, **after** current misleading comments are
  corrected.

**Important**: the Feature 004 human `APPROVE` verdict accepted the disposition but explicitly did
**not** authorize implementation. A **new explicit human authorization is mandatory** before FUP-02
starts.

### Ordered obligation (mandatory sequence)

1. **FIRST**: add a small non-trace typed executable proof for
   `BeautyQSemanticLabelPolicy.forAction`, covering material `stableKey:text` values + ordering semantics.
2. **THEN**: remove the redundant diagnostic/review trace surfaces and string-only goldens identified by
   Feature 004:
   - `PlanIdentityTrace`
   - `BeautyQSearchPlanCompilationTrace`
   - `BeautyQCandidatePlanTrace`
   - `BeautySearchRequestTrace`
   - `BeautyIntentTrace`
   - `SearchPlanTrace`
   - their dedicated renderer-string golden/spec surfaces

### Preserve

- `BeautyIntentRuleTrace` + the complete `r001..r102` declaration regression golden.
- `PlannedAlgebraTrace`, because it has live production response-projection consumers.

Also preserve the source-confirmed `SearchPlanTrace` closure: once the coordinated
request/intent/compilation renderers disappear, `SearchPlanTrace.provenance` has no surviving consumer
and the whole `SearchPlanTrace` object can go.

Future implementation must update the already-recorded stale docs/scaladoc references. **No
runtime/data/lifecycle migration.**

---

## FUP-03 — Remaining non-trace test ownership / coverage map

- **Origin**: `RC-004` = `OPTIONAL_FOLLOWUP`.
- **Feature 004**: `C-TEST-COVERAGE = INSUFFICIENT_EVIDENCE`.
- **Queue status**: `OPTIONAL_EVIDENCE`.
- **Priority**: 4, but **only if** more test consolidation is desired after FUP-02.

Scope: produce a per-invariant primary-owner map for the remaining **non-trace** test corpus.

Explicitly exclude: the trace family already adjudicated by Feature 004.

Goal: separate genuinely duplicate proof layers from intentionally different proof layers, and from
compile-negative/firewall/backend/eval/corpus proofs with independent ownership.

This is **not required** for FUP-02. **No deletion is authorized** merely by documenting the map.

- **Authorization**: separate human choice to spend evidence/review effort.

---

## FUP-04 — Completed-feature Spec Kit checkbox housekeeping

- **Origin**: `RC-006` = `STALE_FOSSIL`.
- **Queue status**: `COMPLETE`.
- **Outcome**: Feature 003 / Feature 004 `tasks.md` completed-task checkboxes reconciled to `[x]`,
  preserving task text, order, IDs and conclusions. Bookkeeping only, not product/runtime work.

**Important**: `.specify/feature.json` is **NOT** part of this finding and was intentionally left
untouched. The feature pointer remains on the last human-selected feature until another human feature
selection occurs; do **not** "clean it up" merely because 004 is closed.

---

## FUP-05 — Feature 001 stale REPORT self-description

- **Origin**: `RC-001` = `DOCUMENTATION_STALE_ONLY`.
- **Queue status**: `COMPLETE`.
- **Outcome**: `REPORT.md` now carries one finalization/supersession note stating that the body was
  substantively populated before T039–T045 and that those gates completed 2026-09-08, with durable
  results in Feature 001 `WORKING.md` §O (`T045 Status: COMPLETE`); the superseded pre-§O checkpoint
  carries a minimal "superseded by §O" annotation. Substantive findings, counts and conclusions are
  unchanged; the historical report was not rewritten.

---

## FUP-06 — Current Gen2 Qdrant marginal-value ablation

- **Origin**: `RC-003` = `OPTIONAL_FOLLOWUP`.
- **Feature 004**: `C-QDRANT-BEAUTYQ-VALUE = INSUFFICIENT_EVIDENCE` (unchanged; not reopened here).
- **Queue status**: `COMPLETE`.
- **Outcome**: the accepted same-execution comparison (baseline-only projection vs actual FullSearch)
  plus the actual human blind calibration was produced and durable-owned at
  `evidence/fup-06-qdrant-marginal-value/README.md`:
  - hard gates pass; 57 corpus-certified `acceptable` gains over an empty ES baseline; success/MRR +57;
    no metric regressions;
  - one human-confirmed `STRONG_HARM` remains (`q_holdout_hair_armpits_laser_course_001`: PMU lips as
    the sole result for an armpit-hair-removal-course intent), so under the frozen V2 rule the
    acceptance verdict is `HARM` while marginal relevance value is demonstrated;
  - this changes no Feature 004 disposition and authorizes no runtime/default/rollout/activation change.

Historical qualification (non-transferable):

- Gen1 ran a real full-corpus ES-only vs ES+Qdrant relevance ablation;
- it found narrow acceptable wins plus substantial semantic harm;
- the zero-harm policy preserved one win and lost another;
- Y1 remained blocked;
- that evidence is non-transferable to current Gen2;
- QP18/QP19 are structural append/no-worsening evidence, **not** relevance/value evidence.

The previously missing same-state Gen2 marginal comparison is now produced using the accepted
same-execution baseline-only projection; source confirms that projection is sufficient, so no second
`BaselineOnly` run was required. Results and verdict are owned by the tracked evidence pointer above.

- **Authorization**: completed evidence outcome; no further authorization.

---

## FUP-07 — Resolve Feature 003 C-20 framework API question

- **Origin**: `RC-007` = `INSUFFICIENT_EVIDENCE`.
- **Queue status**: `NO_ACTION_UNLESS_NEEDED`.
- **Priority**: 7 — lowest.

Question: whether exact Izumi/Distage `1.2.25` exposes an appropriate generic domain-result
trace/render primitive.

Current relevance: **none required**. Feature 004 resolved the BeautyQ-local trace ownership problem
independently. Do not spend work on this unless a future design decision actually depends on the
framework answer.

---

## FUP-08 — Prevent trust-breaking semantic supplement mismatches

- **Origin**: FUP-06 current Gen2 marginal-value evidence.
- **Queue status**: `READY_BUT_REQUIRES_IMPLEMENTATION_AUTHORIZATION`.
- **Priority**: 2 — highest-priority remaining substantive product-quality item.

**Why it exists**: FUP-06 demonstrated substantial Qdrant marginal relevance value, but the frozen V2
acceptance verdict is `HARM` because at least one human-confirmed `STRONG_HARM` remains:
`q_holdout_hair_armpits_laser_course_001`.

- **Intent**: course / durable removal of armpit hair.
- **Observed sole semantic result over empty ES baseline**: PMU lips.
- **Human blind judgment**: empty result clearly better; the mismatch would reduce trust in search.

This follow-up must **not** be phrased as "Qdrant has no value", "disable Qdrant", "change
rollout/defaults", or "implement a particular filter". The exact mitigation owner/design must be
source-confirmed only after explicit human implementation authorization.

### Authorization boundary

- Starting FUP-08 requires **new explicit human implementation authorization**. Queueing it is **not**
  implementation authorization.
- It also does not authorize Qdrant default/activation changes, rollout changes, abandoning append-only
  baseline preservation, weakening hard gates, or changing corpus judgments to make the result pass.

### Future acceptance contract (record only — do not execute)

A separately authorized FUP-08 implementation must:

- source-confirm the exact current owner of semantic supplement acceptance/gating;
- preserve baseline results, order, and baseline-owned components;
- preserve all existing hard gates;
- eliminate the demonstrated trust-breaking `STRONG_HARM` class, not merely special-case a query ID;
- rerun the same FUP-06 comparison shape;
- report whether any of the 57 corpus-acceptable empty-baseline rescues are lost;
- report all human-calibrated cases whose classification changes;
- keep protected acceptance green;
- introduce no new human-confirmed `STRONG_HARM`.

Important trade-off rule: if eliminating the strong harm requires losing corpus-certified relevance
gains or creates a new material trade-off, **STOP for a human product decision**. Do not invent an
allowed-loss percentage or numeric trade-off budget.

- **Authorization**: new explicit human implementation authorization is mandatory.

---

## Parked / deprioritized by human — D1 second production domain

**Status**: `PARKED`.

- D1 is **NOT** a current required gap.
- It is **NOT** an approved-but-unimplemented obligation.
- It is **NOT** the next task.
- The human has explicitly **deprioritized it for now**.
- **No agent / Spec Kit phase should automatically reactivate it.**

Useful future trigger: only reconsider D1 when there is an actual second-domain product identity and
source topology worth building.

If reconsidered later, start eval-first:

- own corpus;
- simplest executable baseline;
- own source topology/identity;
- own backend activation / secondary-source role;
- own no-harm rule and thresholds;
- do **not** copy BeautyQ policy mechanically.

D1 remains potentially valuable as a cross-domain stress test of the reusable heterogeneous-backend
architecture, but that strategic value is **not** authorization to start it.

---

## Not backlog

These already-audited items must **not** be reintroduced as follow-ups merely because history mentioned
them.

| Item | Status |
| --- | --- |
| Automatic Qdrant GC | Accepted limit (`TECHNICAL_SPEC.md:2349`); operator cleanup owns the procedure. |
| Authenticated cursor | Accepted limit (`TECHNICAL_SPEC.md:2345`). |
| Qdrant facets/groups/pagination | Non-goal (`TECHNICAL_SPEC.md:50-63`); Qdrant is candidate-only. |
| Fusion / rerank | Non-goal. |
| Custom analyzers | Accepted limit (`TECHNICAL_SPEC.md:2351`). |
| Rate limiter | Accepted limit (`TECHNICAL_SPEC.md:2354`); deployment ingress owns it. |
| Broader multi-valued exact semantics | Accepted limit (`TECHNICAL_SPEC.md:2353`); limited documented shapes. |
| Old Q2 accepted-baseline / bootstrap / promotion / recovery-rotation ceremony | Intentionally retired (`c8f1c95d`); superseded. |
| Gen1 Y1 | Retired with the Gen1 stack; historical only. |
| M8 production telemetry / retired M8–M21 serving scaffolding | Non-goal (`TECHNICAL_SPEC.md:63`); no current telemetry owner/obligation is required. |
| Project consolidation as an assumed goal | Rejected/kept (`C-PROJECT-BOUNDARIES`); needs new evaluation if ever revisited. |
| Seed/full-loader `toSnapshot` unification | Deliberate current separation (`NEW_DOMAIN_ONBOARDING.md:185-187`). |
| 003 R-01 / C-05 `addDependency` docs item as a BeautyQ implementation task | Explicitly rejected as a Feature 004 decision unit; docs-only framework evidence, no BeautyQ obligation. |
| `.specify/feature.json` pointer cleanup | Intentionally mutable control-plane state (`dee0c458`); not a defect. |

---

## Evidence owners

- `decision-record.md` — Feature 004 final dispositions/verdict/state (unchanged).
- `research.md` — durable closeout conclusions + residual evidence pointer.
- `evidence/residual-commitments-audit/` — reconciled audit and RC-001 forensic.
- `evidence/history-archaeology/` — supporting archaeology.
- `evidence/fup-06-qdrant-marginal-value/` — FUP-06 current Gen2 Qdrant marginal-value evidence and
  verdict (no disposition/runtime authorization).
- `post-closeout-followups.md` (this file) — the prioritized optional/deferred/hygiene queue.

No item in this document authorizes implementation or evidence gathering.
