# FEATURE 004 HISTORY ARCHAEOLOGY HANDOFF

Pass 2 (reconcile history with current source). Read-only. History recovers WHY; current
source/tests/docs decide what is still true. No Feature 004 disposition is made here; all
"effect" terms are coordinator-handoff terms only.

**Re-derived state (2026-09-10):** `HEAD = d66766c0f66f81a0e390c94588b30e65ab325675`,
branch `develop`. Index holds a human-staged added `specs/004-.../decision-record.md`
(`A ` in porcelain); worktree otherwise as found. `HEAD` **equals** the decision-record
evaluated state (`headSha = d66766c0...`) — no drift. No tracked file was modified by this pass.

## 1. Executive delta

- History explicitly separated **Q1 reusable heterogeneous-backend capability** from
  **Q2 BeautyQ-specific supplement value/policy**; current normative owners still confirm the
  separation (generic `search-gen2-qdrant`/`transport`, backend-neutral `CandidatePlan`;
  `SupplementStartupPolicy` is BeautyQ wiring-owned). `C-QDRANT-VALUE` currently models only
  the Q2 value question under an `INSUFFICIENT_EVIDENCE` retention/removal frame — it should be
  **SPLIT/REFRAME** into (A) capability retention and (B) BeautyQ policy/value.
- `C-TRACE-INPUT-VIEWS`'s "full invariant ownership elsewhere is not established" is now
  **partially closed at source level, per trace** (see §2): one trace is `REMOVE_READY`, two are
  `KEEP_PROOF_OWNER`, one is `PARTIAL_SPLIT`. The four input views are not one candidate.
- `BeautyIntentRuleTrace`'s complete r001..r102 declaration golden was **deliberately retained**
  by `dee0c458` after the Gen1 parity ledger was deleted; current source confirms it remains the
  **sole complete executable owner** of the declaration table. Do not treat as redundant.
- `C-PROJECT-BOUNDARIES = KEEP` is **materially strengthened** by history: the neutral transport,
  generic-backend BeautyQ-freedom, eval isolation, and SQL confinement are deliberate ownership
  choices, not an accidental ten-project footprint.
- One cross-trace dependency is decision-relevant: the `C-TRACE-DEAD` (`REMOVE`) obligation gate
  cites intent/declaration ownership, but a material intent fact (canonical semantic **label text**)
  is currently pinned only by trace goldens — including one inside `C-TRACE-DEAD` itself. See §8.

## 2. Trace ownership matrix

Scope per instruction: only `BeautySearchRequestTrace`, `BeautyIntentTrace`,
`BeautyIntentRuleTrace`, `SearchPlanTrace`. (The three proposal-named renderers under
`C-TRACE-DEAD` are out of scope for this matrix.)

Legend for **D**: `CLOSED` = all material facts have a surviving executable owner;
`PARTIAL` = some do, some do not; `OWNED_BY_GOLDEN` = the golden is the current primary
executable owner of a material fact; `MISSING` = cannot establish.

### BeautySearchRequestTrace
- **A. Current consumers.** main-source: `BeautyQSearchPlanCompilationTrace.render`
  (`.../wiring/BeautyQSearchPlanCompilationTrace.scala:23`), which is itself `C-TRACE-DEAD`
  (no production consumer). test: `BeautyQPublicInputGen2Spec:475-513` (golden),
  `BeautyQIntentParserGen2Spec:385-396` (presence smoke). No production/runtime consumer; no doc ref.
- **B. Historical purpose.** `b028526c` introduced it as a readable review/assertion surface
  for decoded public input (E-SR-1/E-SR-2); classification: **readable review/assertion
  projection + secondary byte-for-byte regression lock**. Later reached by a test-only
  aggregation path (E-SR-3).
- **C. Material fact groups in its golden.** (1) provenance `ExplicitUi` vs
  `FacetSelection(sel-1)`; (2) decoded geo radius (`distanceMeters`→`radius=2000`);
  (3) decoded sort (`price`→`priceFrom:decimal Asc`; `distanceMeters`→geo-distance `Desc`);
  (4) cursor presence/non-disclosure; (5) query presence/escaping, facets, page-size,
  user-location encoding.
- **D. Current primary owners.**
  - provenance → `BeautyQPublicInputGen2Spec:39-48` and `:372-394`;
    forms pinned by `SearchGen2VocabularyLedgerSpec:72-84`. **CLOSED**
  - geo radius decode → `BeautyQPublicInputGen2Spec:50-62`. **CLOSED**
  - sort decode → `BeautyQSearchPlanCompilerSpec:512-516` (and distance path `:146-170`). **CLOSED**
  - page/facets/user-location structure → `BeautyQPublicInputGen2Spec:66-77`;
    canonical codec by `SearchValueCodecSpec`. **CLOSED**
  - cursor non-disclosure / escaping → trace-mechanism-internal property; no independent
    behavior survives the trace's removal. **not a material gap**
- **Trace result: `REMOVE_READY`.** Surviving owners: `BeautyQPublicInputGen2Spec`
  (provenance, geo, page/order), `BeautyQSearchPlanCompilerSpec` (sort decode),
  `SearchValueCodecSpec` (canonical encoding), `SearchGen2VocabularyLedgerSpec` (provenance forms).
  Removal is chained to `C-TRACE-DEAD` (`BeautyQSearchPlanCompilationTrace` is its only
  main-source referrer).

### BeautyIntentTrace
- **A. Current consumers.** main-source: `BeautyQSearchPlanCompilationTrace.scala:26`
  (test-only chain). test: `BeautyQIntentParserGen2Spec:391` (presence smoke) and `:660`
  (eight-scenario golden). No production consumer.
- **B. Historical purpose.** `b028526c` review surface (E-IT-1); the golden comment
  (source lines 564-567) explicitly calls itself independent evidence whose mechanics are
  verified in the scenario tests above; classification: **readable review/assertion projection +
  secondary byte-for-byte regression lock**. Rotation churn never transferred ownership (E-IT-3).
- **C. Material fact groups in its golden.** normalization (`normalized-query`);
  matching (`matched-rules` order); hard constraints (order + values + provenance);
  signals; residual text; canonical semantic **labels** (`stableKey:text` + order);
  the eight named scenarios.
- **D. Current primary owners.**
  - normalization function → `BeautyQInputVocabularyLedgerSpec:189-229`. **CLOSED**
  - matching/rule order → typed per scenario in `BeautyQIntentParserGen2Spec`
    (e.g. `:105`, `:211`, `:228`, `:250`, `:268`). **CLOSED**
  - hard constraints → typed per scenario (e.g. `:106-118`, `:251-259`, `:269-285`). **CLOSED**
  - signals → typed (`:72-76`, `:337-341`). **CLOSED**
  - residual text → typed throughout (`:38`, `:52`, `:119`, `:260`, ...). **CLOSED**
  - canonical semantic **label text/order** → only this golden; `BeautyQSemanticLabelPolicy.forAction`
    (`BeautyQIntentVocabularyGen2.scala:59`) has **no non-trace spec**; typed tests pin only
    `stableKey` for two of eight scenarios (`:40`, `:413`). **OWNED_BY_GOLDEN**
- **Trace result: `KEEP_PROOF_OWNER`.** The unique current proof value is the per-scenario
  canonical semantic-label `stableKey:text` and its order produced by
  `BeautyQSemanticLabelPolicy.forAction`. All other mechanics are typed-owned; the renderer could
  later be narrowed to a label-only pin, or replaced by small typed label assertions, but until
  then deleting it drops current proof.

### BeautyIntentRuleTrace
- **A. Current consumers.** **none in main source.** Only
  `BeautyQIntentVocabularyEvidenceSpec:236` (`actual = rules.map(BeautyIntentRuleTrace.render)`).
  No doc reference.
- **B. Historical purpose.** `b028526c` (E-IR-1): readable rendering of the full
  rule-declaration table for golden comparison. `dee0c458` (E-IR-2/E-IR-3) rewrote the comment into
  "current declaration regression golden, not derived from BeautyIntentRuleTrace" and explicitly
  **deleted the Gen1 parity ledger while retaining the complete current vocabulary/declaration
  golden**. `3a6bed90` (T-1) frames the production declaration as authoritative and the golden as
  its subordinate test owner. Classification: **declaration inventory assertion vehicle +
  current declaration regression lock**.
- **C. Material fact groups in its golden.** alias normalization (`BeautyQIntentTextGen2.normalize`);
  actions (service/service-any/category/enum/enum-any/bool/int/near-user); relations
  (`requires`/`excludes`); `mode`; `noise`; the **complete r001..r102 declaration inventory**.
- **D. Current primary owners.**
  - rule-id sequence and selected individual rules → `BeautyQInputVocabularyLedgerSpec:28`,
    `:40-113`; vocabulary validity → `BeautyQPublicInputGen2Spec:116-123`. **PARTIAL**
  - the **complete table across all 102 rules and all fields** → only
    `BeautyQIntentVocabularyEvidenceSpec:131-237` (the golden). Production declaration
    `BeautyQIntentVocabularyGen2.scala` is the source of truth but is not itself a test.
    **OWNED_BY_GOLDEN**
- **Trace result: `KEEP_PROOF_OWNER`.** The unique current proof value is the complete,
  independently-authored byte-for-byte declaration regression table. This matches the raw-history
  hypothesis; it is a legitimate declaration+proof pair, not redundancy.

### SearchPlanTrace (`render` vs `provenance`)
- **A. Current consumers.** `provenance`: **main→main** callers
  `BeautyQInputTraceGen2.scala:16,35` (BeautySearchRequestTrace, BeautyIntentTrace) plus
  `render` internally. `render`: main test-only chain
  `BeautyQSearchPlanCompilationTrace.scala:35` (`C-TRACE-DEAD`) plus `SearchPlanSpec:386-433`
  (golden). `SearchPlan.scala:69` is a scaladoc cross-link only. No production consumer.
- **B. Historical purpose.** `02fd7843` reviewer-readable diagnostic rendering (E-SP-1) and a
  canonical neutral-fixture proof on Catalog Shape A / Venue Shape B (E-SP-2); explicit
  non-ownership of PlanIdentity/cursor (E-SP-3); `provenance` has an in-repo main-side consumer
  while `render` does not (E-SP-4). Classification: **readable review/diagnostic projection +
  neutral-fixture canonical behavioral proof + secondary regression lock**.
- **C. Material fact groups in its golden.** neutral Shape A/B plan structure; constraint/signal/
  sort/facet/group rendering; provenance; cursor-value non-disclosure (presence only);
  no `@`-default-toString identity leak.
- **D. Current primary owners.**
  - constraint/signal/sort exact strings (delegated) → `PlannedConstraintSpec:231-330` **CLOSED**
  - provenance forms → `SearchGen2VocabularyLedgerSpec:72-84` (forms); `provenance` formatting
    remains a live helper **CLOSED-as-helper**
  - facet/group/page/notice/`suppressed-filter` **exact rendering** and section order → only
    `SearchPlanSpec:386-421`; structure (not strings) is owned by `FacetRequestSpec`,
    `GroupRequestSpec`, `FacetPlanRegistrySpec`. **OWNED_BY_GOLDEN (format only)**
  - cursor non-disclosure / no-`@` → trace-mechanism-internal properties. **not a material gap**
- **Trace result: `PARTIAL_SPLIT`.** Removable: `render` and its private rendering helpers
  (test-only; its constraint/signal/sort facts are already owned by `PlannedConstraintSpec`, and
  the facet/group/notice strings are mechanism-owned format). Must remain: `provenance` — it has a
  live main-source consumer while `BeautySearchRequestTrace`/`BeautyIntentTrace` exist. Delete the
  whole object/file only after the input views themselves are removed.

### Trace result tally
`REMOVE_READY = 1` · `KEEP_PROOF_OWNER = 2` · `PARTIAL_SPLIT = 1` · `STILL_INSUFFICIENT = 0`.

## 3. Concrete test-consolidation opportunities

Only source-confirmed relationships adjacent to the four trace/golden families:

| Fact/invariant family | Current primary owner | Secondary/duplicate proof | Duplicate or distinct layer | Bounded cleanup |
|---|---|---|---|---|
| Request provenance / geo-radius / page-order | `BeautyQPublicInputGen2Spec:39-77,372-394` | `BeautySearchRequestTrace` golden (`:478-513`) | **Genuinely duplicate** for material facts (trace format is mechanism-owned) | Remove `BeautySearchRequestTrace` + its golden block after `C-TRACE-DEAD` |
| Request sort decode | `BeautyQSearchPlanCompilerSpec:512-516` | `BeautySearchRequestTrace` golden sort lines | **Duplicate** | same as above |
| Intent matching / hard constraints / signals / residual | per-scenario typed `BeautyQIntentParserGen2Spec` | `BeautyIntentTrace` golden scenarios | **Distinct layer only for label text/order** | Do not delete; narrow if labels get typed owners |
| Complete intent declaration table | `BeautyQIntentVocabularyEvidenceSpec:131-237` (golden) | `BeautyQInputVocabularyLedgerSpec` (id sequence + selected rules) | **Distinct layers** (complete vs sampling) | None |
| Plan constraint/signal/sort rendering | `PlannedConstraintSpec:231-330` | `SearchPlanTrace.render` golden (delegated) | **Duplicate** for constraint/signal/sort only | `SearchPlanTrace.render` removable; keep `provenance` |
| Plan facet/group/page/notice rendering | `SearchPlanTrace` golden only | none | **Not duplicate** | None until replacement proof authored |

**Concrete subset newly closed:** one — the request-view family (`BeautySearchRequestTrace` +
its golden block) now has independent executable owners for every material fact. This is the only
trace/test overlap that closes cleanly from this pass. It is **not** inflated into a repo-wide
test recommendation.

## 4. Qdrant capability vs BeautyQ policy

Current normative owners **confirm** the historical Q1/Q2 separation:

- **Domain-owned policy:** `SupplementStartupPolicy` (Required default / Preferred / Disabled) is
  BeautyQ wiring-owned and applied before DI planning (`OPERATIONS.md:7`,
  `TECHNICAL_SPEC.md:1974-1982`); backend activation is domain-owned (`DOMAIN_AUTHORING_PRINCIPLES.md:55`);
  a new domain chooses its own baseline and candidate-only/advisory/supplemental secondary backend
  and its own no-harm rule/thresholds (`NEW_DOMAIN_ONBOARDING.md:603-605,627-639`); no second
  production domain exists (`spec.md` D1 non-goal).
- **Reusable capability:** `search-gen2-qdrant` and `search-gen2-elasticsearch` are generic modules
  depending on contract/core/transport (`TECHNICAL_SPEC.md:300-334`); `search-gen2-transport` is
  domain/backend-neutral with **two real Gen2 backend consumers** (`TECHNICAL_SPEC.md:373-377`);
  `search-gen2-contract` owns the backend-neutral `CandidatePlan`/`CandidatePlanDecision` contract,
  with BeautyQ's ineligibility enum kept separate (`TECHNICAL_SPEC.md:797-829`); generic backends
  must stay BeautyQ-free (build firewall `SearchGen2ModuleFirewallSpec`; history `cc769ad4`).

**Assessment of `C-QDRANT-VALUE`:** the current candidate conflates two independent decision units.

- **A. Reusable heterogeneous-backend capability.** Question: retain reusable support for a
  materially different secondary retrieval backend, with Qdrant as the current generic
  implementation/proof. Current owners establish this as intended framework scope (generic module,
  neutral transport with two consumers, backend-neutral candidate contract, new-domain contract,
  BeautyQ-free firewalls). Handoff effect: **SPLIT** portion A; likely KEEP capability if owners
  hold (human-owned).
- **B. BeautyQ Qdrant product value / policy.** Question: how much BeautyQ should rely on the
  supplement and whether the `Required` default/policy is justified by measured marginal product
  value. Evidence: BeautyQ-specific thresholds, `Required/Preferred/Disabled`, and the still-absent
  predeclared baseline-vs-full comparison (`BeautyQEvaluationPolicy.scala:27`; simplification plan
  §2.1). Handoff effect: **SPLIT** portion B; still `INSUFFICIENT_EVIDENCE` until the comparison
  exists.

**Would a poor BeautyQ-specific uplift justify deleting the generic capability?** **Current owners
support "no."** The capability's existence is not conditioned on BeautyQ product value: backend
activation/policy is domain-owned while reuse is proven by neutral fixtures independently of BeautyQ
(`DOMAIN_AUTHORING_PRINCIPLES.md:55,85,87`); generic backends are BeautyQ-free; a second domain
chooses its own source mix (`NEW_DOMAIN_ONBOARDING.md:603-605`); history's extraction-discipline
rule conditions *new* seams, not deletion of existing ones (raw-qdrant §4). Residual: whether to
carry a single-production-consumer generic backend long-term is a repository-policy judgment that
current source cannot settle — that part remains **human-owned**.

## 5. Project-boundary delta

Source-confirm the recovered properties still hold: neutral `search-gen2-transport` with two
backend consumers (`TECHNICAL_SPEC.md:373-377`; `build.sbt:144-170`); generic
`search-elasticsearch`/`search-qdrant` BeautyQ-free (build firewall); eval isolation via
`test->test` (`build.sbt:207-249`; `TECHNICAL_SPEC.md:312-315`); SQL/doobie confined to
materialization (technical spec + `dee0c458` retained confinement). **History materially
strengthens `C-PROJECT-BOUNDARIES = KEEP`:** the ten-project shape is not an incidental module
count but a set of deliberate ownership/firewall choices (`89fdfa08` shared transport because both
backends consume it; `cc769ad4` generic-backend import guardrail; `dee0c458` retained eval/SQL
isolation), each with recovered rationale. **Nuance preserved:** "transport should not be folded
into either backend" does not mean "transport must forever remain a separate sbt project"; folding
it into another neutral module could be valid if the two-consumer dependency direction and
BeautyQ-freedom remain intact and measurable simplification value is shown.

## 6. Feature 004 effect matrix

| Candidate | Current disposition (decision-record) | History delta | Current-source confirmation | Recommended coordinator effect | Confidence | Remaining proof |
|---|---|---|---|---|---|---|
| `C-QDRANT-VALUE` | `INSUFFICIENT_EVIDENCE` | Q1/Q2 deliberately separated; product value deferred (`dee0c458`) | Owners confirm capability≠BeautyQ-policy; BeautyQ value comparison absent | **SPLIT / REFRAME** into A capability and B BeautyQ policy | High on split; value part unresolved | A: human policy on single-consumer generic capability. B: predeclared baseline-vs-full comparison (product+eval owner) |
| `C-TRACE-DEAD` | `REMOVE` | `dee0c458` deferred trace-owner removal to a separate decision | No consumer outside own specs (source-confirmed) | **UNCHANGED** | Medium-High | Label-text ownership crosses into `C-TRACE-INPUT-VIEWS` (see §8) |
| `C-TRACE-LIVE` | `KEEP` | — | Live response-projection consumer `BeautyQSearchResponseGen2.scala:206,213` | **UNCHANGED** | High | None material |
| `C-TRACE-INPUT-VIEWS` | `INSUFFICIENT_EVIDENCE` | Goldens deliberately authored/retained; not one family | Fact ownership splits by trace: 1 remove-ready, 2 proof-owner, 1 partial | **NARROW / SPLIT** per §2 | Medium-High | Intent label text/order; SearchPlanTrace facet/group/notice string invariants |
| `C-PROJECT-BOUNDARIES` | `KEEP` | Explicit rationale for transport/neutrality/eval/SQL | Owners and build.sbt confirm | **UNCHANGED** (strengthened) | High | None material |
| `C-TEST-COVERAGE` | `INSUFFICIENT_EVIDENCE` | Cleanup pattern: representation layers removed when owners exist | One concrete overlap closes (request-view golden) | **NARROW** (one concrete subset closed) | Medium | Full coverage map still absent; only the request-view subset closes |
| `C-DOCS-RECON` | `KEEP` | Old post-cutover plan artifact superseded; deferral persists | Owners current and non-chronological | **UNCHANGED** | Medium-High | None material |

Effects are report terms only. No human verdict is manufactured.

## 7. Primary evidence index

| # | Proposition | Evidence class | Anchor |
|---|---|---|---|
| 1 | Request provenance/geo/sort facts are typed-owned | CURRENT_TEST | `BeautyQPublicInputGen2Spec:39-77,372-394`; `BeautyQSearchPlanCompilerSpec:512-516` |
| 2 | Intent matching/hard-constraints/signals/residual typed per eight scenarios | CURRENT_TEST | `BeautyQIntentParserGen2Spec:25-561` |
| 3 | Intent label text has no non-trace test owner | CURRENT_SOURCE | `BeautyQSemanticLabelPolicy` `BeautyQIntentVocabularyGen2.scala:59`; only `BeautyQIntentParserGen2Spec:582-655` asserts text |
| 4 | Complete declaration table owned by rule golden | CURRENT_TEST | `BeautyQIntentVocabularyEvidenceSpec:131-237` |
| 5 | Gen1 parity ledger removed but current declaration golden retained | HISTORICAL_EXPLICIT | `dee0c458` (2026-09-10), "remove architecture and governance fossils" |
| 6 | Declaration authoritative; golden subordinate test owner | HISTORICAL_EXPLICIT | `3a6bed90` (2026-08-11) body |
| 7 | `provenance` main→main consumer; `render` test-only | CURRENT_SOURCE / HISTORICAL_EXPLICIT | `BeautyQInputTraceGen2.scala:16,35`; `BeautyQSearchPlanCompilationTrace.scala:35`; `5adb8ea9` E-SP-4 |
| 8 | Plan constraint/signal/sort strings owned by `PlannedConstraintSpec` | CURRENT_TEST | `PlannedConstraintSpec:231-330` |
| 9 | Plan facet/group/page/notice strings only in `SearchPlanTrace` golden | CURRENT_TEST | `SearchPlanSpec:386-421` |
| 10 | Q1/Q2 separation (generic vs domain policy) | CURRENT_NORMATIVE_DOC + HISTORICAL_EXPLICIT | `TECHNICAL_SPEC.md:373-377,797-829,1974-1982`; `OPERATIONS.md:7`; `DOMAIN_AUTHORING_PRINCIPLES.md:55,85,87`; `NEW_DOMAIN_ONBOARDING.md:603-605`; `6baaaea5`,`7dfe6aef`,`78b85584`,`1b3bac45` |
| 11 | Capability not conditional on BeautyQ value | CURRENT_NORMATIVE_DOC | `DOMAIN_AUTHORING_PRINCIPLES.md:85-90`; extraction-discipline rule (raw-qdrant §4) |
| 12 | Boundaries deliberate | HISTORICAL_EXPLICIT + CURRENT_SOURCE | `89fdfa08`,`cc769ad4`,`dee0c458`; `build.sbt:144-170,207-249` |
| 13 | Qdrant value comparison absent | CURRENT_SOURCE | `BEAUTYQ_SEARCH_GEN2_SIMPLIFICATION_PLAN.md:36-57`; `BeautyQEvaluationPolicy.scala:27` |
| 14 | Trace renderers introduced as reviewer-readable review surfaces | HISTORICAL_EXPLICIT | `b028526c`, `02fd7843` (2026-07-13) |

## 8. Contradictions / unresolved

- **Cross-candidate dependency (current source):** the `C-TRACE-DEAD` (`REMOVE`) obligation gate
  claims all material invariants of its three golden specs are owned by canonical specs. But the
  canonical semantic **label text** (`stableKey:text`) is asserted (a) by
  `BeautyQSearchPlanCompilationTraceSpec:447,458,516` — inside `C-TRACE-DEAD` — and (b) by the
  `BeautyIntentTrace` golden inside `C-TRACE-INPUT-VIEWS` — which is currently
  `INSUFFICIENT_EVIDENCE`. There is **no** independent typed owner for
  `BeautyQSemanticLabelPolicy.forAction` output. Removing `C-TRACE-DEAD` while `C-TRACE-INPUT-VIEWS`
  stays unresolved would drop the only remaining label-text proof. The two candidates must be
  resolved together for this fact.
- **Current source cannot establish (human-owned):** whether the BeautyQ default (`Required`,
  Qdrant candidate-only) is the right product policy; only the predeclared baseline-vs-full
  comparison can. Current owner set names product owner + evaluation owner.
- **Current source cannot establish (human-owned):** whether a single-production-consumer generic
  backend (`search-gen2-qdrant`) is worth carrying long-term absent a second domain. The ownership
  distinction establishes that BeautyQ uplift is *not* the deletion criterion; it does not decide
  the maintenance-policy question.
- **Current source contradicts history?** No material contradiction found. The decision-record's
  `C-TRACE-INPUT-VIEWS` statement that per-fact ownership "is not established" is **incomplete**
  rather than wrong: current source now proves the request-view facts owned and isolates the
  remaining label/format gaps.
- **History cannot establish:** the final BeautyQ product-value verdict (`dee0c458` explicitly
  defers it); and no second production domain ever chose a different source mix (raw-qdrant §9).
