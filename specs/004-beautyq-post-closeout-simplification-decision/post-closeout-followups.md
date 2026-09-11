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
| `—` | `FUP-08` | `COMPLETE` | Done — human product decision: declared-family suppression rejected after measurement; demonstrated semantic value preserved; residual relevance mismatch accepted as a soft product trade-off. | Completed product decision; no implementation/runtime/default/rollout change. |
| `—` | `FUP-09` | `COMPLETE` | Done — architecture + offline feasibility completed; generic query-side semantic-label inference is credible and worth specifying, with no measured reranking win claimed. | Completed evidence outcome; separate human decision required to start/specify an implementation feature; no production implementation. |
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
- `FUP-08` is a completed product decision: the measured declared-family mitigation is rejected and the
  residual semantic relevance mismatch is accepted; no implementation and no Qdrant
  default/activation/rollout change is authorized or required.
- `FUP-03` requires a separate human decision to spend evidence effort.
- `FUP-09` is a completed evidence outcome: its `GO_TO_SPEC_KIT_FEATURE` verdict means a separate human
  decision is required to start/specify an implementation feature, and it authorizes no production
  implementation.
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
- **Queue status**: `COMPLETE`.
- **Outcome**: human product decision completed; no implementation.
- **Evidence owner**: `evidence/fup-08-declared-family-counterfactual/README.md`.

**Why it existed**: FUP-06 showed substantial Qdrant marginal relevance value but a human-confirmed
`STRONG_HARM` relevance-quality mismatch (`q_holdout_hair_armpits_laser_course_001`: PMU lips as the sole
semantic result for an armpit-hair-removal intent over an empty ES baseline).

**Human product decision**: this is a low-stakes search product and relevance is not safety-critical. The
observed mismatch is a soft relevance/UX cost, not a safety event or a zero-tolerance release blocker.
Preserving semantic recall/value is more valuable than suppressing every strongly irrelevant semantic
result. Therefore the declared-family gate is **rejected** and **not implemented**, the residual
semantic mismatch is **accepted**, and no new semantic classifier will be introduced solely to remove it.

- all 57 demonstrated corpus-certified semantic rescues preserved by not implementing the gate (the
  rejected gate would have lost 4 of them, plus 4 / 18 automated weak gains and 2 neutral additions);
- declared-family suppression rejected after measurement;
- residual relevance mismatch accepted as a product trade-off;
- no implementation/runtime/default/rollout change; no numeric error budget and no requirement that a
  specific bad result later be fixed.

The V2 rule "any `STRONG_HARM` ⇒ `HARM`" remains the historical FUP-06 experiment verdict but is **not**
the current ongoing product acceptance policy after this decision. Existing true hard invariants
(forbidden constraints, baseline loss/reordering, duplicates, degradation, baseline-owned component
corruption, append-budget violations) are unchanged and are not covered by this relevance-quality
decision.

A future narrower precision improvement may be pursued if it can improve relevance without materially
sacrificing retrieval value, but it is not a current obligation and is not added as a follow-up item.

---

## FUP-09 — Generic query-side semantic label inference feasibility

- **Origin**: FUP-08 human product decision; FUP-06 no-family mismatch analysis.
- **Queue status**: `COMPLETE`.
- **Outcome**: `GO_TO_SPEC_KIT_FEATURE` (completed architecture + offline feasibility investigation; no
  measured reranking win claimed and no implementation authorized). Evidence owner:
  `evidence/fup-09-semantic-label-inference-feasibility/README.md`. A separate human decision is required
  to start/specify an implementation feature; this does **not** authorize one.
- **Mode**: read-only architecture + offline feasibility spike. **Not** production implementation
  authorization.

**Purpose**: investigate whether Search Gen2 can cheaply complement lexical intent parsing with a
reusable, domain-neutral semantic-label inference mechanism over domain-owned service/category
declarations, so a no-family query can still carry query-side semantic evidence.

**Why**: FUP-08 established that the same no-family mechanism produces both a useful rescue
(`q_semantic_010`) and a strongly irrelevant case (`q_holdout_hair_armpits_laser_course_001`); blunt
suppression cannot distinguish them, and the declared-family gate was rejected because it would lose
useful recall. FUP-09 asks whether a cheaper, precision-improving query-side signal can be derived
without a blanket recall trade-off. No result is assumed or authorized.

**Human-decided design direction (constraints for the future pass)**:

- **First production use, if ever**: soft preference / reranking / selection evidence for Qdrant
  supplements only; initially **not** a hard family constraint (occasional semantic junk is acceptable;
  preserving recall matters).
- **LLM role**: **offline only** — teacher/judge, first seed, multilingual paraphrase generation,
  hard-negative generation, offline calibration/evaluation. No runtime LLM dependency is currently
  desired (a future different decision is not precluded, but FUP-09 must not be designed around runtime
  LLM inference).
- **Domain authoring burden**: domain authors declare canonical meaning through the existing
  business-facing declaration path; they must **not** maintain a second semantic ontology or duplicated
  registry. Preferred direction: existing domain service/category declarations → generic derived
  semantic material/prototypes/evidence, with multilingual paraphrases/prototypes derived
  mechanically/offline where possible.
- **Hierarchy**: if the current service/category declarations already provide a reusable hierarchy
  without duplication, the generic mechanism may use it; otherwise v1 stays flat rather than introducing
  a second hierarchy.
- **Mode**: begin with a read-only architecture + offline feasibility spike that cheaply proves or kills
  the idea before any production implementation.

**Hypothesis to validate (not accepted architecture)**: a generic mechanism operating over opaque
domain-owned keys, conceptually `SemanticLabelInference[K]`, where `K` is supplied by the domain. Generic
code must know nothing about HairRemoval, Facial, PMU, Manicure, bicycles, or any other domain concept;
BeautyQ keeps owning its service/category keys and a future domain supplies its own keys without Search
Gen2 generic source changes. Preferred direction: existing domain declarations → derived semantic label
universe/prototypes → generic inference evidence over opaque keys → domain-specific policy decides how
that evidence affects search. Do **not** introduce a new BeautyQ `SemanticFamily` ontology for this.

**Design gates**: the completed pass evaluated any proposed API against the canonical Domain Authoring
Principles in `docs/search/DOMAIN_AUTHORING_PRINCIPLES.md` — one readable declaration path; business
declares policy while the framework derives mechanics; compose reusable domain-neutral components proven
with a neutral fixture; one executable source of truth. Reference that normative doc; do not restate it.

**Questions the completed pass answered** (the durable evidence owner records the answers):

- Can the label universe be derived from existing service/category declarations without a second source
  of truth?
- Does the current hierarchy already support generic derivation, or should v1 remain flat?
- What is the smallest credible generic API, and what remains domain-owned?
- Can generic behavior be proven with a neutral fixture?
- How should offline LLM teacher material remain derived rather than become a business truth owner?
- How well can a cheap embedding-based classifier infer known lexical families, and recover no-family
  useful rescues?
- Does it help distinguish useful semantic supplements from known bad/weak cases?
- Compare at least: (1) declaration text only; (2) declaration + child/service-derived material;
  (3) declaration + offline LLM-generated examples.
- What is the measured code-size impact: generic production LOC, domain integration LOC, tests, offline
  tooling, and share relative to the actual relevant Search Gen2 codebase/slice?
- Does the idea deserve implementation / a full Spec Kit feature after the spike?

**Authorization boundary**: FUP-09 creation authorizes no production implementation and no semantic
reranking, new framework API, LLM integration, parser/vocabulary change, prototype registry, runtime
threshold, new Qdrant behavior, Feature 004 disposition change, or FUP-02 implementation. The pass is
complete; a separate later explicit human action is still required to start/specify an implementation
feature.

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
- `evidence/fup-08-declared-family-counterfactual/` — FUP-08 declared-family counterfactual and the human
  product decision rejecting the gate (no implementation/rollout authorization).
- `evidence/fup-09-semantic-label-inference-feasibility/` — FUP-09 generic query-side semantic-label
  inference architecture + offline feasibility evidence, strategy comparison, and `GO_TO_SPEC_KIT_FEATURE`
  outcome (no implementation authorization).
- `post-closeout-followups.md` (this file) — the prioritized optional/deferred/hygiene queue.

No item in this document authorizes implementation or evidence gathering.
