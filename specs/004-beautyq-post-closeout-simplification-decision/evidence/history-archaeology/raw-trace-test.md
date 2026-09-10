# RAW TRACE / TEST HISTORY

Read-only Git archaeology for Feature 004 pre-work. History only; current
source/tests/docs remain authoritative. No KEEP/REMOVE/INSUFFICIENT_EVIDENCE
dispositions are made here.

Authoritative history branch inspected: `develop` (HEAD `d66766c0`).
`--all` surfaced legacy/synthetic variants (`develop-old`, `stash`,
`rewrite/gen2-readable-history`, backup refs, and junk branches `d1..d9`,
`rev`, `npe`); those were excluded because the current tree descends from the
rewritten readable `develop` history. All commits below are develop-reachable.

Two Gen2 feature-kernel commits introduce all four renderers and their
byte-for-byte goldens:

- `b028526c` (2026-07-13) `feat(search-gen2): add reusable public-input and
  intent authoring kernel` — introduces `BeautySearchRequestTrace`,
  `BeautyIntentTrace`, `BeautyIntentRuleTrace` (all in
  `BeautyQInputTraceGen2.scala`) plus their request/intent/rule goldens.
- `02fd7843` (2026-07-13) `feat(search-gen2-contract): add validated
  search-plan algebra` — introduces `SearchPlanTrace` and its golden.

## BeautySearchRequestTrace

Rendering target: `ValidatedBeautySearchRequestGen2` -> deterministic string.
File: `beautyq-search-gen2-contract/.../contract/BeautyQInputTraceGen2.scala:5`.

- **E-SR-1** — `b028526c` (2026-07-13), "add reusable public-input and intent
  authoring kernel"; `BeautyQInputTraceGen2.scala`. Commit body:
  "BeautySearchRequestTrace/BeautyIntentTrace render both the decoded request
  and the matched intent for review." Supported proposition: introduced as a
  readable assertion / review surface for decoded public-input facts. Strength:
  EXPLICIT. Current-source confirmation of consumer set still required.
- **E-SR-2** — `b028526c` (2026-07-13);
  `BeautyQPublicInputGen2Spec.scala:475` block
  `"BeautySearchRequestTrace" should`. Introduced at the same commit (verified
  by `-S 'BeautySearchRequestTrace" should'`). Golden asserts request-validation
  facts: `provenance=ExplicitUi` vs `FacetSelection(sel-1)`, geo-radius decode,
  sort decode, `request.cursor=absent` (presence only, never opaque value).
  Inline comment (source lines 476-477) states the cursor is "only an untrusted
  carrier; plan identity validation belongs to the wiring compiler." Supported
  proposition: request-validation regression lock + readable assertion surface.
  Strength: EXPLICIT (comment + golden). Current confirmation required.
- **E-SR-3** — `5adb8ea9` (2026-07-13), "add reusable plan compilation and
  BeautyQ plan policy"; added as a call inside main-code
  `BeautyQSearchPlanCompilationTrace.render` (line 23). Supported proposition:
  a later aggregation path references it. Strength: INFERRED (see
  `SearchPlanTrace` E-SP-4 / unresolved Q2 for why this is not a production
  consumer).

## BeautyIntentTrace

Rendering target: `ParsedBeautyIntentGen2` -> deterministic string.
File: `BeautyQInputTraceGen2.scala:32`.

- **E-IT-1** — `b028526c` (2026-07-13), same commit as E-SR-1; commit body
  "render both the decoded request and the matched intent for review". Supported
  proposition: introduced as readable assertion / review surface for matched
  intent. Strength: EXPLICIT.
- **E-IT-2** — `b028526c` (2026-07-13);
  `BeautyQIntentParserGen2Spec.scala:564-568` block
  `"BeautyIntentTrace golden traces"`. Comment (source lines 564-567):
  "Independent evidence, not derived from BeautyIntentTrace or any other
  production traversal helper: each golden string below was authored by hand
  from the mechanics verified in the scenario tests above... Must fail the
  moment normalization, matching, hard constraints, signals, residual text or
  labels change for any of these eight queries." Eight normalization/matching
  scenarios (incl. budget interval, contextual coating r032, near-me overlay
  r087). Supported proposition: regression lock for normalization/matching
  scenarios; the per-scenario tests above are stated as the primary mechanics
  proof, the golden is an additional byte-for-byte lock. Strength: EXPLICIT.
- **E-IT-3** — Q2 rotation churn: `718660275` (2026-08-04) "Generalize BeautyQ
  intent matching and replenish holdout", `eeccefe8` (2026-08-04) "Close BeautyQ
  exact-intent semantics and replenish holdout", and later rotations
  `1268e05d`,`4e3f6aed`,`21587343`,`f8f31f12`,`3c4ca064`,`ebb2a7a2`,`66525825`
  all touched `BeautyQIntentParserGen2Spec.scala`. No commit transfers the
  trace's ownership. Supported proposition: the trace/golden survived delivery
  churn but its ownership framing was not moved during rotations. Strength:
  INFERRED (absence of transfer, not evidence of transfer).

## BeautyIntentRuleTrace

Rendering target: one `BeautyIntentRule` declaration -> deterministic string.
File: `BeautyQInputTraceGen2.scala:52`.

- **E-IR-1** — `b028526c` (2026-07-13); `BeautyQInputTraceGen2.scala`. Commit
  body: "An 86-entry, index-for-index disposition ledger checks every inherited
  Gen1 rule against its Gen2 counterpart, with r087 recorded as the one
  deliberate Gen2-only addition, and exact golden traces pin every documented
  matching scenario." Supported proposition: introduced as a readable rendering
  of the full rule-declaration table for golden comparison. Strength: EXPLICIT.
- **E-IR-2** — `b028526c` (2026-07-13) introduced the golden and the original
  `-S 'not derived from BeautyIntentRuleTrace'` comment. The comment framing was
  **rewritten later** by `dee0c458` (2026-09-10): current
  `BeautyQIntentVocabularyEvidenceSpec.scala:127-130` reads "Current declaration
  regression golden, not derived from BeautyIntentRuleTrace or any other
  production traversal helper: every string below is authored independently and
  compared byte-for-byte against production output... must fail the moment any
  alias, action, relation, mode or noise flag changes for any rule." At
  `:236` the `actual` is `BeautyQIntentVocabulary.rules.map(BeautyIntentRuleTrace.render)`;
  `expected` is a hand-authored r001..r102 literal vector (source 132-235).
  Supported proposition: full intent-rule declaration-table regression lock;
  renderer is the assertion vehicle, the hand-authored expected is the
  independent side. Strength: EXPLICIT. Current confirmation still required.
- **E-IR-3** — `dee0c458` (2026-09-10), "remove architecture and governance
  fossils". Commit body deletes "the r001-r086 Gen1 intent-vocabulary
  disposition/parity ledger" (a retired representation fossil) while it
  "retain[s] current r087-r102 intent semantics and the complete current
  vocabulary/declaration regression golden". Supported proposition: an explicit
  separation between the retired Gen1 parity ledger and the retained
  current-vocabulary declaration golden. Strength: EXPLICIT.

## SearchPlanTrace

Rendering target: one `SearchPlan`/parts -> deterministic string.
File: `search-gen2-contract/.../contract/SearchPlanTrace.scala:13`.

- **E-SP-1** — `02fd7843` (2026-07-13), "add validated search-plan algebra";
  `SearchPlanTrace.scala`. Commit body: "PlannedAlgebraTrace and SearchPlanTrace
  render the accepted plan and its diagnostics as reviewer-readable strings".
  Supported proposition: introduced as reviewer-readable diagnostic rendering of
  the plan algebra. Strength: EXPLICIT.
- **E-SP-2** — `02fd7843` (2026-07-13);
  `SearchPlanSpec.scala:386` block `"SearchPlanTrace.render" should` (introduced
  same commit, `-S` confirmed). Golden over two structurally different neutral
  fixtures (CatalogDocument Shape A, VenueDocument Shape B) plus two negative
  invariants: "never expose cursor contents ... only presence" (:422) and "never
  leak function/object identity (no '@'-style default toString)" (:428).
  Supported proposition: canonical behavioral proof for plan rendering, on
  neutral fixtures (per repo "prove reusable code with neutral fixtures").
  Strength: EXPLICIT. Same commit also introduces `SearchGen2VocabularyLedgerSpec`
  (candidate sibling vocabulary owner).
- **E-SP-3** — `02fd7843` (2026-07-13); `SearchPlanTrace.scala:3-12` scaladoc.
  Explicit design-time non-ownership: "This trace is diagnostic only. It is not
  PlanIdentity, cursor input, backend JSON or a canonical wire format. Brick 4C's
  `PlanIdentity`/cursor encoding must derive its own canonical representation
  directly from typed plan values ... and must not depend on, parse, or embed
  these trace strings." Supported proposition: plan-identity/cursor facts are
  owned elsewhere (typed plan values / Brick 4C), not by the trace. Strength:
  EXPLICIT.
- **E-SP-4** — `5adb8ea9` (2026-07-13); `SearchPlanTrace.render` referenced from
  main-code `BeautyQSearchPlanCompilationTrace.scala:35`; `SearchPlanTrace.provenance`
  referenced from main-code `BeautyQInputTraceGen2.scala:16,35` (so `provenance`
  has an in-repo main->main consumer). `render` itself has no consumer outside
  its own spec, the test-only `BeautyQSearchPlanCompilationTraceSpec`, and the
  test-only compilation-trace object. Supported proposition: `provenance` is a
  shared helper with a main-side user; `render` is reached only through tests and
  a test-only aggregation object. Strength: EXPLICIT consumer map (verified by
  `git grep` at current HEAD). Current confirmation required.

## Explicit ownership-transfer evidence

- **T-1** — `3a6bed90` (2026-08-11), "Align BeautyQ declaration golden with the
  rotation 6 vocabulary ledger". Body: "The production declaration already
  exposes the intended 101-rule ledger; this commit removes the stale r102
  expectation from its test owner without changing runtime search semantics."
  Supported proposition: the declaration-ledger direction is authoritative and
  the golden is its subordinate "test owner" (the golden was aligned to the
  declaration, not vice versa). Note: current source again lists r001..r102
  (`BeautyQIntentVocabularyEvidenceSpec.scala:234`) after later rotation/deletion
  churn. Strength: EXPLICIT (subject+body). Current confirmation of the live rule
  set required.
- **T-2** — `dee0c458` (2026-09-10), "remove architecture and governance fossils".
  Body explicitly DEFERS these renderers: "do not consolidate sbt projects or
  remove trace owners without a separate decision", and "retain ... the complete
  current vocabulary/declaration regression golden". Supported proposition: a
  later, deliberate decision boundary for trace ownership exists and is not
  resolved by this history. Strength: EXPLICIT.
- **T-3** — `c8f1c95d` (2026-09-10), "remove expired Q2 evidence ceremony"
  (sibling wave to `dee0c458`). Body: deletes "obsolete tests and documentation
  owned only by the deleted workflows" and "Replace[s] the persisted
  protected-input audit ceremony with a small ordinary integrity spec covering
  only current quality invariants not already enforced by loaders and existing
  tests." Supported proposition: an explicit "delivery/recovery ceremony ->
  current behavioral/invariant proof" replacement pattern in the same cleanup
  wave (adjacent to, not the traces themselves). Strength: EXPLICIT.
- **T-4** — `b028526c` + `5adb8ea9` (2026-07-13). Bodies repeatedly frame
  `BeautyQSearchDeclarations.variants.*` as "executable inventories rather than a
  second, hand-maintained rendering of the same policy", with traces rendering
  those values. Supported proposition: declarations own the inventory; traces are
  derived views. Candidate current owner: `BeautyQSearchDeclarationsSpec`,
  `BeautyQInputVocabularyLedgerSpec`. Strength: EXPLICIT (commit body) / current
  confirmation required.

## Adjacent test-consolidation evidence

- **A-1** — `dee0c458` (2026-09-10): removed "the exact Gen2 DAG mirror and
  dependency-tree golden", "extinct Gen1 package/symbol inventories and
  compiler-enforced import scans", "source-layout/test-tree representation
  locks", and "evidence-only constructor/privacy boundary tests whose subjects no
  longer provide independent product or runtime value", while retaining small
  compiler-invisible ownership checks. Pattern: representation-heavy
  duplication/inventory layers collapsed. Strength: EXPLICIT (subject+body).
- **A-2** — `2349670b` (2026-07-17), "refactor(search-gen2): replace the promise
  audit with executable pre-5D cleanup". Pattern: docs/promise-audit replaced by
  executable checks. Strength: EXPLICIT (subject); not directly about the four
  traces. Current relevance unverified.
- **A-3** — `89f69706`/`58d3653` (2026-07-22/23) "complete native Gen2 cutover
  and retire the Gen1 stack"; `dee0c458` later removes residual Gen1 history
  from executable ownership. Pattern: cutover/delivery-era Gen1 artifacts retired
  after Gen2 became current truth. Strength: EXPLICIT (subjects). Boundary only —
  does not by itself speak to the Gen2 trace owners.

## Current candidate proof owners (named or implied by transfers)

- `BeautyQSearchDeclarations` / `variants.request|intent|plan` — executable
  declaration inventory (T-4).
- `BeautyQSearchDeclarationsSpec`, `BeautyQInputVocabularyLedgerSpec` —
  declaration/registry owners (present at HEAD).
- `SearchGen2VocabularyLedgerSpec` (introduced with `SearchPlanTrace` in
  `02fd7843`) — plan-algebra vocabulary owner for constraint/signal/sort/facet/
  group shapes.
- Brick 4C `PlanIdentity`/cursor encoding (`leaderboard.search.gen2.core.plan`)
  — owner of plan-identity/cursor facts (E-SP-3).
- Compile-time document-safety checks (`02fd7843` body) — type-level owner of
  field-belonging-to-document facts.
- Per-scenario typed tests in `BeautyQIntentParserGen2Spec` (referenced by the
  golden comment as "the mechanics verified in the scenario tests above") —
  candidate primary owners for individual matching scenarios (E-IT-2).
- `PlannedAlgebraTrace` — sub-rendering owner invoked by three of the four
  renderers; `004` decision-record (staged, not treated as source truth) flags it
  as the one renderer with a live public-response-projection consumer.

## Unresolved questions

1. Whether **every** byte-for-byte golden literal (each rule trace string, each
   intent scenario trace, each request/plan line) is independently asserted by a
   non-trace typed spec. History shows the goldens were deliberately authored as
   the "must fail the moment X changes" lock; the commits do not prove each
   literal is separately owned elsewhere. Current-source/test cross-reference
   required.
2. Whether `BeautyQSearchPlanCompilationTrace` (aggregate of three renderers) is
   intended to become a runtime diagnostic (logging/metrics/response) or is
   deliberately test-only. No develop-reachable commit wires it into any
   production path; `dee0c458` deferred trace-owner decisions to a later step.
3. Whether removing `BeautyIntentRuleTrace` would force re-deriving the golden's
   `actual` side through another traversal helper. The expected side is
   documented independent, but no commit names a permanent actual-side renderer
   other than `BeautyIntentRuleTrace`.
4. Whether the r102 count churn (removed at `3a6bed90`, present again at HEAD)
   means the rule-count golden is a live moving target or a frozen current
   contract — history records rotation churn, not a stable disposition.
5. `PlannedAlgebraTrace` is the sub-rendering owner used by
   `BeautySearchRequestTrace`, `BeautyIntentTrace`, and `SearchPlanTrace`. Its
   true production status (the decision-record claims a live public-response
   consumer) is outside this history pass and needs current-source confirmation.
6. Whether the neutral-fixture status of the `SearchPlanSpec` goldens (Shape A/B)
   fully overlaps `SearchGen2VocabularyLedgerSpec` ownership of plan-algebra
   vocabulary — both introduced in `02fd7843`; the split of facts between them is
   not established by commit messages.
