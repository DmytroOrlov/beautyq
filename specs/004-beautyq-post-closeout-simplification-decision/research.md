# Feature 004 — Research: Source/Owner Reconciliation and Bounded Evidence Questions

**Feature**: `specs/004-beautyq-post-closeout-simplification-decision`

**Phase**: `/speckit.plan` Phase 0. This document records **planning-time** reconciliation and the
bounded evidence questions execution must answer. It assigns **no candidate disposition**; dispositions
are an execution product.

**Scope discipline**: this feature is decision-only. No production/source/build/test/runtime/normative
owner document is read for the purpose of changing it, and nothing here authorizes a change.

## Evaluated State Anchor (planning-time)

| Field | Value |
| --- | --- |
| Repo root | `/Users/do/git/sandbox/distage-example` |
| Git `HEAD` (re-derived) | `f2147c4c94bc00d1e10e3a87f2d8b33c8a0d0e49` |
| Git branch | `develop` (no feature Git branch created/switched) |
| Worktree at phase start | Clean porcelain; this phase adds only `specs/004-.../*` Markdown |
| 003 evaluated source (evidence) | `50e794584d68768403d5c45d6d7fc21c2db87f6c`, `stateId = 50e7945-clean` |
| 003 resolved framework version | `io.7mind.izumi 1.2.25` |

Execution MUST re-derive and record its own evaluated source/architecture state (Principle V). The values
above are navigation context only; they are not authority and MUST NOT be silently reused if the state
has moved.

## Evidence Classes (kept distinct)

| Class | Source | Authority |
| --- | --- | --- |
| **A. Proposal claims** | `docs/gen2/BEAUTYQ_SEARCH_GEN2_SIMPLIFICATION_PLAN.md` (status: non-normative proposal) | Attributed criticism + proposed alternative. Never current architecture (Principle VIII) |
| **B. Current normative obligations** | operations runbook; technical specification | Current owners. Evaluated, never rewritten here |
| **C. Current executable/source reality** | Scala sources, `build.sbt`, tests at the evaluated state | Read-only factual anchor |
| **D. Completed 003 audit evidence** | `specs/003-.../research/05-synthesis-and-recommendations.md` (primary), lower-level artifacts only for exact evidence | Upstream evidence. Informs, never authorizes (Principles IV, VIII) |
| **E. Unresolved evidence** | Open questions execution must answer | Drives `INSUFFICIENT_EVIDENCE` when unavailable |

The plan MUST NOT let proposal claims become current architecture by repetition, and MUST NOT resurrect
ceremony already removed by the pre-004 authorized cleanup.

## Owner Map (navigation only — no mutable content copied)

| Fact needed | Canonical current owner |
| --- | --- |
| Current Gen2 architecture, semantics, invariants, accepted limits | `docs/gen2/BEAUTYQ_SEARCH_GEN2_TECHNICAL_SPEC.md` |
| Operator procedures / protected acceptance / recovery | `docs/gen2/BEAUTYQ_SEARCH_GEN2_OPERATIONS.md` |
| Reusable domain/search ownership principles | `docs/search/DOMAIN_AUTHORING_PRINCIPLES.md` |
| Non-normative simplification ideas under evaluation | `docs/gen2/BEAUTYQ_SEARCH_GEN2_SIMPLIFICATION_PLAN.md` |
| Framework-leverage classifications for BeautyQ-local mechanics | `specs/003-beautyq-distage-izumi-leverage-audit` |
| Entry map / where to read each owner | `README.md` |

## Source/Architecture Reality Observed at Plan Time (class C)

Read-only observations that establish existence and current owners. These are **not** dispositions.

- **Gen2 sbt projects present (10)**: `search-gen2-contract`, `search-gen2-core`, `search-gen2-transport`,
  `search-gen2-elasticsearch`, `search-gen2-qdrant`, `search-gen2-eval`, `beautyq-search-gen2-contract`,
  `beautyq-search-gen2-materialization`, `beautyq-search-gen2-wiring`, `beautyq-search-gen2-eval`
  (`build.sbt`; root `distage-example` aggregates them).
- **Trace mechanisms present**:
  - `search-gen2-core/.../plan/PlanIdentityTrace.scala` — observed consumers in source are its own spec.
  - `beautyq-search-gen2-wiring/.../BeautyQSearchPlanCompilationTrace.scala` — observed consumers in
    source are its own spec.
  - `beautyq-search-gen2-wiring/.../BeautyQCandidatePlanTrace.scala` — observed consumers are its own spec
    plus a scaladoc reference in `BeautyQCandidatePlanCompiler`.
  - `search-gen2-contract/.../contract/PlannedAlgebraTrace.scala` — **consumed by response projection**
    (`BeautyQSearchResponseGen2`, `BeautyQInputTraceGen2`) and by `SearchPlanTrace`; the proposal itself
    protects it while projection consumes it.
  - `beautyq-search-gen2-contract/.../contract/BeautyQInputTraceGen2.scala` — request/intent trace view.
  - Reachability **outside own tests/docs is the evidence question**, not assumed.
- **Normative operational mechanisms present** (runbook): `SupplementStartupPolicy` (required/preferred/
  disabled), `StartupServingStatus`, `/beauty-search/status`, warning codes, partial-activation recovery,
  Qdrant cleanup procedure, protected holdout rule, protected acceptance resources/conventions.

## Candidate Families and Bounded Evidence Questions (class E)

Source-confirmed anchors (current proposal `docs/gen2/BEAUTYQ_SEARCH_GEN2_SIMPLIFICATION_PLAN.md`):
§2.1 *Qdrant has proved operation and no-harm, not marginal product value*; §2.2 *Trace-only and duplicated
presentation owners*; §2.3 *Project consolidation*; §2.4 *Test consolidation around observable contracts*;
§2.5 *Documentation reconciliation*. The family→section mapping below reflects these exact current
headings.

Families are provisional navigation; execution derives the actual decision units and MAY split/merge/
reject-as-decision-unit while preserving traceability to the originating proposal section.

### F-QDRANT — Qdrant marginal product value (proposal §2.1)

- **Question**: Does Qdrant supplementation demonstrate a marginal **product/user value** beyond the
  Elasticsearch baseline sufficient to justify its operational/ownership cost — or is the marginal value
  unestablished?
- **Currently established (evidence)**: operation + no-harm; Required/FullSearch readiness; append-only
  behavior; baseline preservation; ordering; component preservation; append budget. These are safety and
  integrity proofs, **not** a baseline-vs-full relevance delta.
- **Must collect to decide removal/retention**: the proposal's predeclared product-value comparison
  (proposal §2.1, current lines 47–54) — named user-visible metric or ordered metric vector; cutoffs/
  slices; minimum accepted improvement; maximum regression budget; forbidden-hit/hard-constraint/baseline-preservation/degradation stop
  conditions; identical application revision, source snapshot, ES generation, corpus, requests, page
  policy in baseline-only vs Required/FullSearch mode.
- **If unavailable**: `INSUFFICIENT_EVIDENCE` naming the comparison run + threshold owner (product owner).
- **Forbidden substitutes**: architectural elegance, framework availability, historical effort, existence
  of semantic-search machinery, append-only activity.

### F-TRACE — Trace-only / duplicated presentation owners (proposal §2.2)

- **Question**: Which trace/renderer/ledger mechanisms have no operator/response/artifact/diagnostic
  consumer outside their own tests and documentation, such that removing them leaves no unowned
  obligation?
- **Must collect**: per-mechanism reachability at the evaluated state (consumers outside own tests/docs);
  for each removal, the request/intent trace views it would make unreachable; confirmation that
  fingerprints, typed identities, and observable error diagnostics are **not** traces and stay protected.
- **003 join**: the diagnostic-trace portion joins 003 `C-20` (response projection and diagnostic
  traces), which is the single `INDETERMINATE` candidate. `C-20`'s existence question (generic
  domain-result trace/render surface at exact `1.2.25`, or only DI-graph rendering) stays unresolved where
  relevant.

### F-PROJECT — Project consolidation (proposal §2.3)

- **Question**: Which of the ten Gen2 sbt projects still enforce a current compilation, dependency,
  runtime, package-ownership, or evaluation boundary, and which can be merged without destroying one?
- **Must collect**: per-project boundary ownership at the evaluated state; whether `search-gen2-transport`
  must remain standalone while two backends use it; what breaks/unowns if a project is merged; whether a
  retained negative check stays small and source-owned.
- **Separation rule**: kept distinct from F-TEST unless source proves one coherent obligation.

### F-TEST — Test consolidation around observable contracts (proposal §2.4)

- **Question**: Which tests differ only by rendering layer, private-wrapper construction boundary,
  duplicated fixture inventory, module-source scanning, or historical delivery phase, and which are the
  single primary owner of a distinct invariant?
- **Must collect**: a coverage map **before** any deletion; per invariant one primary owner; genuinely
  different proof layers retained; no meaningful corpus case reduced to hit a line target; firewall/build
  ownership tests evaluated as proof value, not by count.
- **Separation rule**: kept distinct from F-PROJECT unless source proves one coherent obligation.

### F-DOCS — Documentation reconciliation (proposal §2.5)

- **Question**: For each candidate whose cost is documentary duplication rather than source complexity,
  is documentation-only consolidation a sufficient outcome? Where does the current owner still carry a
  live obligation that docs-only consolidation cannot preserve?
- **Must collect**: whether the mechanism's cost is documentary duplication; surviving canonical owner per
  Principle II/X; whether any source obligation hides behind the documentation.
- **001 boundary**: this feature MUST NOT re-open or re-litigate the completed `001-beautyq-doc-history-review`.

### F-AUDIT-DISC — 003 `R-01`/`C-05` `addDependency` docs/discoverability (audit-surfaced)

- **Question**: Is the 003 `R-01` docs/discoverability wrinkle a genuine **current-complexity
  simplification** decision unit for 004, or is it evidence feeding F-DOCS / FR-012 instead?
- **Evidence**: 003 `R-01`: a real public `1.2.25` `addDependency` surface
  (`AbstractBindingDefDSL`), `remedyCandidates = DOCS`, candidate `WELL_USED`/`DISCOVERABLE`, confidence
  Medium, docs-only, no new primitive. The current BeautyQ constructor dependency idiom is the primary
  documented mechanism and is `DISCOVERABLE`.
- **Permission boundary**: 003's docs-only recommendation is **not** implementation permission; execution
  MAY reject this as a decision unit with recorded basis.

## Feature 003 Integration (economic use)

- Primary synthesis consumed: `specs/003-.../research/05-synthesis-and-recommendations.md`.
- Accepted 003 state: 32 candidates; `CLASSIFIED = 31`; `INDETERMINATE = 1` (`C-20`);
  `WELL_USED = 7`; `BEAUTYQ_SPECIFIC = 24`; `UNDERUSED = 0`; `HARD_TO_DISCOVER = 0`;
  `MISSING_GENERIC_PRIMITIVE = 0`.
- Join rule: exact mechanic match only; reopen lower-level 003 artifacts only for the named candidate's
  exact evidence.
- Calibration rules preserved: `WELL_USED` ≠ automatic `KEEP`; `BEAUTYQ_SPECIFIC` ≠ automatic `KEEP`;
  no `UNDERUSED` does not prohibit local simplification; `C-20` stays uncertain; `R-01` is not permission.
- 004 asks the broader question: can this complexity safely be removed/simplified now, and is it worth
  doing?

## Bounded Evidence Seams Execution Must Answer (class E summary)

| Seam | Candidate family | Missing evidence | Named owner to resolve/carry |
| --- | --- | --- | --- |
| E-1 | F-QDRANT | Predeclared baseline-vs-full product-value comparison (proposal §2.1, current lines 47–54: metric/vector, slices, thresholds, stop conditions, identical inputs) | Product owner + evaluation owner |
| E-2 | F-TRACE | Reachability of each trace outside own tests/docs | Search-kernel/wiring owners |
| E-3 | F-TRACE / 003 `C-20` | Existence of an exact-`1.2.25` generic domain-result trace/render surface vs only DI-graph rendering | Framework-evidence owner (003 method), kept unresolved |
| E-4 | F-PROJECT | Per-project current boundary ownership; merge break/unown analysis | Build/repository owner |
| E-5 | F-TEST | Coverage map before deletion; primary invariant owner per test | Test/build owner |
| E-6 | F-DOCS / F-AUDIT-DISC | Whether docs-only is sufficient and where the live obligation sits | Documentation owner |

Unresolved seams become `INSUFFICIENT_EVIDENCE` naming the missing evidence and owner — never a guess.

## Explicit Non-Actions in This Phase

- No candidate evaluated; no disposition assigned.
- No product/framework/build/source read performed for change; no file outside `specs/004-.../` written.
- No `contracts/` tree; `/speckit.plan` creates no `tasks.md` (a later real `/speckit.tasks` may create
  the canonical feature-local `tasks.md`; decision execution follows those accepted tasks and still makes
  no source/build/test/runtime/normative-owner changes).
- No Git/index/ref mutation; no digest/freeze/pass/fingerprint/baseline/review-bundle machinery.

---

## Closeout Reconciliation — Durable Conclusions and Evidence Index

Added at human-verdict closeout. This section indexes the durable conclusions the decision rests on and
points to the tracked evidence trail. It does not paste raw evidence and assigns no new disposition. The
evidence directory is **supporting research, not normative authority**: current source/tests/normative docs
remain authoritative, and the evidence files preserve the detailed archaeology/forensic trail.

Detailed trail (tracked feature-local):
`evidence/history-archaeology/README.md`, `.../raw-trace-test.md`, `.../raw-qdrant-framework.md`,
`.../HANDOFF.md`, `.../QDRANT-VALUE-FORENSIC.md`.

| # | Durable conclusion | Primary anchors | Detailed evidence |
| --- | --- | --- | --- |
| A | Qdrant reusable-capability and BeautyQ product-value/policy are separate decision units. | `docs/gen2/BEAUTYQ_SEARCH_GEN2_TECHNICAL_SPEC.md:373-377,797-829`; `docs/gen2/BEAUTYQ_SEARCH_GEN2_OPERATIONS.md:7`; `build.sbt:207-252` | `HANDOFF.md` §4; `raw-qdrant-framework.md` |
| B | A historical Gen1 full-corpus (74-query) ES-only vs ES+Qdrant relevance ablation existed: narrow acceptable wins (including `q_broad_006`, `q_lashes_008`) plus substantial semantic harm; the zero-harm `ExplicitConstraintsFilterPlusTop1` gate preserved `q_broad_006` but lost `q_lashes_008`; Y1 policy blocked. | `df0652b5`, `4f73070e`, `66f9e525`, `ffe32a5b`, `0259558c`, `e4359ec8`; ledger `fc06f9cb` | `QDRANT-VALUE-FORENSIC.md` §4, §7 |
| C | QP18/QP19 "improvement" means structural append/no-worsening, not product relevance. | `438c3407` (QP18); `50650002` (QP19); `BeautyQCutoverGate.scala:216,238-257` | `QDRANT-VALUE-FORENSIC.md` §2, §3 |
| D | The Gen1 value result is non-transferable to current Gen2, and the current Gen2 baseline-only vs Required/FullSearch marginal ablation was never produced. | `a67d9143`; `BeautyQEvaluationPolicy.scala:27`; `dee0c458` | `QDRANT-VALUE-FORENSIC.md` §5, §6 |
| E | Trace simplification must add typed proof for `BeautyQSemanticLabelPolicy.forAction` `stableKey:text` + ordering FIRST. | `BeautyQIntentVocabularyGen2.scala:59`; `BeautyQIntentParserGen2Spec` | `HANDOFF.md` §2, §8; `raw-trace-test.md` |
| F | `BeautyIntentRuleTrace` + the complete r001..r102 golden remains: the sole complete executable declaration-table regression owner. | `BeautyQIntentVocabularyEvidenceSpec:131-237`; `dee0c458` | `HANDOFF.md` §2; `raw-trace-test.md` |
| G | `PlannedAlgebraTrace` remains: it has live production response-projection consumers. | `BeautyQSearchResponseGen2.scala:206,213` | `HANDOFF.md` §2; `raw-trace-test.md` |
| H | After coordinated trace removal, `SearchPlanTrace.provenance` has no surviving consumer, so the whole `SearchPlanTrace` object can be removed. | `BeautyQInputTraceGen2.scala:16,35`; `BeautyQSearchPlanCompilationTrace.scala:35`; `SearchPlan.scala:69` (scaladoc-only reference) | decision record `SearchPlanTrace`/`provenance` boundary; `HANDOFF.md` §2; `raw-trace-test.md` |
| I | Project-boundary KEEP preserves ownership/dependency properties (neutral two-backend transport, BeautyQ-free generic backends, eval isolation, SQL confinement); it does not sanctify the current sbt project count forever. | `build.sbt:144-170,207-252`; `SearchGen2ModuleFirewallSpec`; `dee0c458` | `HANDOFF.md` §5; `raw-qdrant-framework.md` |
| J | `C-TEST-COVERAGE` concerns only the remaining non-trace corpus. | decision record `C-TEST-COVERAGE` | `HANDOFF.md` §3; `raw-trace-test.md` |

These conclusions are retained in the decision record's dispositions and in this index; the evidence files
do not become normative owners and do not authorize any implementation or evidence follow-up.

---

## Post-Closeout Residual-Commitments Audit (evidence pointer)

A repository-wide residual-commitments audit was performed **after** this Feature 004 closeout. It is
tracked feature-local at
[`evidence/residual-commitments-audit/README.md`](evidence/residual-commitments-audit/README.md), with
the reconciled audit in `evidence/residual-commitments-audit/RESIDUAL-COMMITMENTS.md` and the RC-001
forensic reconciliation in `evidence/residual-commitments-audit/RC-001-FEATURE-001-FORENSIC.md`.

Durable conclusions only:

- the repository-wide historical/current residual audit found **zero `CURRENT_REQUIRED_GAP`, zero
  `APPROVED_NOT_IMPLEMENTED`, and zero `LOST_OBLIGATION`**;
- `RC-001` was source/history-reconciled to **`DOCUMENTATION_STALE_ONLY`** because Feature 001
  T039–T045 have durable completion evidence in its `WORKING.md` §O (`Status: COMPLETE`, 2026-09-08) and
  only the `REPORT.md` framing stayed stale;
- the remaining findings are intentionally deferred, optional, stale fossils, or evidence-limited;
- this evidence **does not modify or reopen the Feature 004 decision**.
