# QDRANT VALUE FORENSIC

Read-only forensic pass before the Feature 004 human verdict. Repository
`/Users/do/git/sandbox/distage-example`, branch `develop`, `HEAD = d66766c0f66f81a0e390c94588b30e65ab325675`.
No tracked file was modified. Index holds a human-staged added
`specs/004-beautyq-post-closeout-simplification-decision/decision-record.md` (`A ` in porcelain), preserved as found.

Method note: every historical commit-message claim was re-checked against the file the commit actually changed.
Gate existence is kept distinct from gate result; old product evidence is kept distinct from current-transferable
evidence; whole-system Q2 acceptance is kept distinct from a Qdrant baseline-vs-full ablation.

---

## 1. Executive verdict

- **Did BeautyQ ever demonstrate real Qdrant relevance/user-value improvement?**
  **YES, narrowly and only for the Gen1 application state.** The Gen1 Y0C–Y0I measurement chain ran the *full* 74-query
  canonical BeautyQ eval set with real ES + real Qdrant + real embedding endpoint and measured ES-only vs
  ES+Qdrant-supplement using the canonical `acceptableVariantIds`. It found real recall wins (`q_broad_006`,
  `q_lashes_008`) but also widespread semantic harm and an unresolved lost-recall problem. It was never an accepted
  product policy: "Y1 remains blocked".
- **Was it an accepted decision-quality result at that time?**
  **NO accepted production/policy value result.** It was accepted *measurement evidence* that blocked promotion. The
  strongest accepted outcome was a local/test `no-worsening` gate whose "improvement" was a structural Qdrant-only
  append, not a relevance delta.
- **Does it transfer to current Gen2?**
  **NO** for QP18/QP19 or the Gen1 Y0 numbers themselves. **PARTIALLY** for the *method* (baseline+supplement
  ablation over a full canonical corpus with explicit judgments).
- **Did Gen2 later remeasure it?**
  **NO Qdrant ablation.** Gen2 (a67d9143 onward) executed the full 89-case V2 corpus in **one Required/FullSearch
  startup only**; protected acceptance compares a candidate run against a frozen accepted baseline over time. No
  baseline-only vs Required/FullSearch delta was produced.
- **Was any accepted knowledge lost?**
  **MIXED / LOST_WITHOUT_RECEIVER.** The Gen1 relevance findings (full-corpus ablation, harm, lost recall) were
  deleted with the Gen1 stack at cutover (58d3653b) and were already dropped from the handoff (33f3c319). The Gen2
  accepted baseline was materialized (67a3e843) then deleted (c8f1c95d). A durable Gen2 accepted-baseline *conclusion*
  did not survive as a tracked artifact.
- **Is current Feature 004 wording accurate?**
  The precise clause "the required baseline-vs-full relevance delta is **absent**" (for Gen2) is **CORRECT**. The
  broader clause "marginal product value is **unmeasured**" is **TOO_STRONG / missing historical qualification**:
  a relevant baseline-vs-full measurement *was* performed (Gen1), and it did not show accepted value. The fair
  classification is **CORRECT_BUT_MISSING_HISTORICAL_QUALIFICATION**.
- **Overall taxonomy:** the evidence establishes **B + C + D + part of E**, not A (knowledge of *positive* value was
  never held) and not F (no documented deliberate reset invalidated a positive result):
  - **B** (older, narrower proof valid for its exact Gen1 state, non-transferable after Gen2/corpus/model/source changes),
  - **C** (QP18/QP19 commit messages overstate what the assertions proved),
  - **D** (measurement machinery + gates existed, but no accepted *relevance* value result was ever produced for a
    shipping policy),
  - **part of E** (Gen2 Q2 produced whole-system/protected acceptance but the durable accepted-baseline artifact was
    removed; the Qdrant marginal ablation itself was never produced).

---

## 2. QP18 actual proof

Commit: `438c3407b4c18c8ef6fa72114baa74ea6ea0dafb` (2026-06-26), "search-test: prove Qdrant supplement improves without worsening ES".
Changed: **test-only + docs-only** (no production source).
- `bifunctor-tagless/src/test/scala/leaderboard/search/QP18QdrantSupplementImprovementNoWorseningSpec.scala` (new, 570 lines)
- `docs/BEAUTYQ_QDRANT_SUPPLEMENT_OPERATOR_CHECKLIST.md` (1 line)

Recovered facts (source-confirmed in the spec at `438c3407`):

| Question | Finding |
|---|---|
| Executable test/gate name | `QP18QdrantSupplementImprovementNoWorseningSpec`, test "show one append-only improvement without worsening the ES baseline" |
| Query/corpus size | **2 hand-picked queries** (`q_broad_006_ready_append_probe` = "beauty near Wandsbek Markt"; `manicure_real_route_probe` = "маникюр"), not the canonical eval corpus |
| ES baseline result | served 200; default and rollback ES baselines asserted equal |
| ES+Qdrant result | served 200; asserted equal to the ready service response at id/order/component granularity |
| Metric(s) | none graded. "Improvement" = `appendedQdrantOnlyIds.size == 1` under `ExplicitConstraintsFilterPlusTop1` |
| Numerical/count result | exactly one Qdrant-only variant appended; 0 lost/duplicated ES ids; 0 prefix/order regressions; 0 ES-owned component changes; ≤1 append (lines 468–486, 503–513) |
| Genuinely user-relevant? | **Not proven by the test.** The spec never asserts the appended id is in `acceptableVariantIds` or otherwise relevant; it asserts only that it is *Qdrant-only and appended*. Relevance was established separately (Gen1 Y0G) but is not encoded here |
| Any query worsened? | No — structurally (prefix/loss/duplicate/append-budget/component) |
| Real ES? | Yes (`ElasticsearchTestClient`, resource-gated) |
| Real Qdrant? | Yes (`QdrantClient`, resource-gated) |
| Real embedding path/model? | Yes for the path — real `LlamaCppEmbeddingClient` at `M18_QDRANT_EMBEDDING_ENDPOINT` (default `http://localhost:8081`), dimension taken from the live endpoint; but the logical model name is a synthetic label `qp18-test-local-proof` |
| Hand-picked inputs? | Yes. Two known broad queries; the spec calls them "source-confirmed broad query". The query was selected after the Y0C–Y0H runs had already identified the positive-append family |
| Acceptance criteria predeclared? | Hardcoded in the spec before run (`≥1` query with one Qdrant-only append, zero structural worsening). Not a predeclared *relevance threshold* |
| Result persisted? | **No.** Only test assertions and diagnostic marker strings (e.g. `QP18_ROUTE_LAYER_BLOCKED_SERVICE_PROOF_GREEN`). No metric artifact |

**Classification: `NARROW_MECHANISM_VALUE_EVIDENCE`** — it proves the real end-to-end supplement mechanism can append
exactly one real Qdrant-only candidate without structural harm, but does not prove the appended candidate is useful.
The commit subject "prove Qdrant supplement **improves**" is separately **`OVERSTATED_BY_COMMIT_MESSAGE`** because the
assertion is an append, not a relevance improvement. If forced to one label for the *message*, it is overstatement;
for the *evidence*, it is narrow mechanism value.

---

## 3. QP19 gate vs result

Commit: `50650002f2417fd587fdbfa857f8875008f163d4` (2026-06-26), "search-test: add measured Qdrant supplement acceptance gate".
Changed: **test-only + docs-only**.
- `bifunctor-tagless/src/test/scala/leaderboard/search/QP19QdrantSupplementMeasuredAcceptanceGateSpec.scala` (new, 647 lines)
- `docs/BEAUTYQ_QDRANT_SUPPLEMENT_OPERATOR_CHECKLIST.md`

Later widened/locked on the same lineage: `5258042e` (2→4 queries), `2b50e33d` (locks QP23 measured baseline),
`b7e5f9ca` (route/service comparison stabilization), then `6004b548`, `c9e3754a`, `b8f3c4d7`.

Recovered facts:

- Metric definitions: `improvedQueries`, `unchangedQueries`, `worsenedQueries`, `totalQdrantOnlyAppends`,
  `duplicateEsIds`, `lostEsIds`, `prefixOrderRegressions`, `esOwnedComponentChanges`, `appendBudgetViolations`.
- Pass/fail semantics: hard-fail unless `improvedQueries ≥ 1`, `worsenedQueries == 0`, `lostEsIds == 0`,
  `duplicateEsIds == 0`, `prefixOrderRegressions == 0`, `esOwnedComponentChanges == 0`,
  `appendBudgetViolations == 0`, `testedQueries ≥ 2` (`assertAcceptanceGate`, lines 446–488).
- Query identity: **explicitly limited** to the QP18 source family — marker `QP19_QUERY_SET_LIMITED_TO_QP18_SOURCE`;
  initial 2 queries, widened to 4 (`q_broad_001`, `q_broad_003` added). Still a sample, not the canonical corpus.
- Exact executed/encoded result: yes, **encoded as assertions and then locked**: `testedQueries=4`,
  `improvedQueries=1`, `unchangedQueries=3`, `worsenedQueries=0`, `totalQdrantOnlyAppends=1`, all regression counters 0
  (locked at `2b50e33d`; drift fails `QP23_WIDENED_BASELINE_DRIFT`).
- **Critical semantic:** in the final pre-cutover spec,
  `improved = appendedQdrantOnly.size == 1 && worsenedFields.isEmpty` (line 474). It is **not**
  `appended acceptable / not appended unacceptable`.

**QP19 established a combination of (1) reusable measurement method, (2) BeautyQ gate threshold policy, and (3) a
concrete run that satisfied that policy — but the accepted quantity is append/no-worsening, NOT relevance.**
The mandatory distinction resolves as:

- **"GATE EXISTS"**: yes.
- **"VALUE WAS MEASURED AND ACCEPTED"**: **no** — a structural *append* was measured and accepted, not marginal
  product/relevance value. QP19 is a **method + policy + locally-accepted structural result**, not an accepted
  product-value result.

---

## 4. Pre-Gen2 value state

The decisive evidence predates QP18. The Gen1 Y0 measurement chain (2026-06-23…07-03) is the real value evidence:

- `b0a4e7c4` (M18 Qdrant real-leg candidate output), `0c969e75`/`5c324c15`/`7bbf3df9` (M18/M19 dual-engine offline
  eval and metrics over ES vs Qdrant candidates).
- `df0652b5` (Y0C, 2026-06-24): real ES + real Qdrant + real embedding over **all canonical BeautyQ eval queries**;
  compares **ES-only vs ES+Qdrant-supplement** at thresholds 0.60/0.62; Qdrant appends many candidates outside
  `acceptableVariantIds` while improving recall on only a small number of queries → policy blocked.
- `4f73070e` (Y0D): append-cap-of-1 barely reduces harmed queries and loses a recall win; top Qdrant candidate is
  often already unacceptable.
- `66f9e525` (Y0E): no embedding source-field candidate reduces harm.
- `ffe32a5b` (Y0G): parser `explicit_constraints_filter_plus_top1` reduces harmed queries from 43 to 0 but preserves
  only one of two baseline recall wins.
- `0259558c` (Y0I): `q_lashes_008` is the lost recall win; no non-oracle candidate recovers it.
- `e4359ec8` (Y0H): larger embedding model does not help under the zero-harm gate.
- `fc06f9cb` (2026-06-25): durable **evidence ledger** in `docs/BEAUTYQ_CURRENT_STATE_AND_HANDOFF.md`:
  "Worked: ES-first + Qdrant-additive seam preserves structural response ownership"; "Almost worked:
  `explicit_constraints_filter_plus_top1` … reduced harm to zero … preserved `q_broad_006` … lost `q_lashes_008` …
  measurement-useful but not Y1-ready"; "Decision: Y1 remains blocked. The BeautyQ runtime supplement tuning loop is
  stopped at Y0K."

Source confirmation: `RuntimeEsQdrantScorecardProofSpec.scala` (last pre-cutover state at `350a650a`) contains the Y0C
block "over all canonical queries" (line 3102), the Y0G/Y0H "all 74 canonical eval queries at scoreThreshold 0.62"
(lines ~4648, ~5085), and `appendedUnacceptableIds`/`recallImproved` fields. The spec records harm as measured
evidence; the exact integer `43` is **not** pinned in the spec (grep for a `43` literal is empty) — it appears only in
commit/doc prose, so treat the number as run-reported, not test-pinned.

Then the docs (`7dfe6aef`, `10e22316`, `fa78c402`) deliberately made the **method** transferable and the BeautyQ
thresholds/history non-transferable. QP18/QP19 (2026-06-26) re-expressed the outcome as a narrow local append gate.

**Strongest legitimate statement immediately before the Gen2 cutover:**
"Over the full canonical Gen1 eval set, ES+Qdrant supplementation demonstrated at least one measured, acceptable
recall improvement (`q_broad_006`) under the zero-harm `ExplicitConstraintsFilterPlusTop1` gate, but it caused
substantial semantic harm in the unrestricted route and the zero-harm gate lost another recall win (`q_lashes_008`);
Qdrant therefore remained non-production measurement evidence and Y1 policy stayed blocked." It is **not** legitimate
to say Qdrant "passed an accepted product-value gate" or that Qdrant "remained blocked *despite* positive examples"
without the harm/lost-recall context.

---

## 5. Gen2 transferability matrix

Evaluated state: QP18/QP19 (Gen1, `BeautySearchSpecV1`, 74-query canonical corpus, Gen1 ES/ Qdrant stack) vs native
Gen2 (`BeautyQSearchApplication`, V2 corpus, generation lifecycle).

| Dimension | Classification | Evidence |
|---|---|---|
| Application/search implementation | **CHANGED** | Gen1 stack deleted at `58d3653b`; Gen2 `BeautyQSearchApplication`/plan compiler is a rewrite |
| ES baseline semantics | **CHANGED** | Gen2 deterministic ES generation/index lifecycle (`031c2c1e`, `87f47796`); different mapping/projection |
| Qdrant retrieval semantics | **CHANGED** | Gen2 neutral transport, `CandidatePlan`, Qdrant generation lifecycle (`89fdfa08`, `ffbb9dc1`) |
| Candidate gating | **CHANGED** | Gen2 `BeautyQQdrantPolicy`/`CandidatePipeline`; Gen1 `ExplicitConstraintsFilterPlusTop1` not the Gen2 owner |
| Merge/append semantics | **SEMANTICALLY_EQUIVALENT_WITH_PROOF** | Gen2 append-only baseline-preservation is re-proved by `BeautyQCutoverGate`/`BeautyQNoHarmSupplementEvidence` |
| Corpus/query set | **CHANGED** | Gen1 74-query `beautyq_search_eval_queries_v1.json` deleted; Gen2 89-case `beautyq_evaluation_corpus_v2.json` (`afbc03c7`) |
| Judgments | **CHANGED** | Gen1 `acceptableVariantIds` vs Gen2 typed V2 judgments/cutoffs 1,3,5,10; corpus fingerprint differs |
| Semantic source text | **CHANGED** | Gen2 declared variant document/embedding text-format version |
| Embedding model/version/config | **CHANGED / UNKNOWN** | Gen1 endpoint-probed dimension; Gen2 records embedding provider/model/revision/dimension/text-format as provenance |
| Materialized Qdrant vectors / generation semantics | **CHANGED** | Gen2 generation identities, alias switching, bootstrap fingerprint |
| Ranking/page policy | **CHANGED** | Gen2 page size 20, cutoffs 1/3/5/10; Gen1 different |
| Thresholds/acceptance policy | **CHANGED** | Gen2 `BeautyQEvaluationPolicy.scala:27`: no accepted relevance thresholds/holdout |

**Could QP18/QP19 legitimately serve as current Gen2 BeautyQ product-value evidence? `NO`.**
Reason: not material-identity-equivalent (different implementation, corpus, judgments, model, generation semantics),
and the QP18/QP19 "improvement" is a structural append, not a relevance measure. The Gen1 Y0 baseline-vs-full
measurement answers **`PARTIALLY`**: its *method* (full canonical corpus, ES-only vs supplemented, explicit
acceptable/unacceptable judgments, structural no-harm first) transfers, but its *numbers and verdict* do not transfer
to a different corpus, model, judgments, and rewritten search.

---

## 6. Post-cutover Q2 evidence chain

Starting at `1b3bac45` ("docs(search-gen2): define measurable post-cutover delivery plan", 2026-07-27):

1. `afbc03c7` — neutral `search-gen2-eval` kernel; migrates 89 cases to canonical V2 corpus as **visible regression**
   cases; explicitly **does not** execute the corpus, create a protected holdout, add thresholds, or create an accepted
   baseline.
2. `a67d9143` — first real full-corpus run. **One immutable Required/FullSearch startup** for all 356 executions
   (89 warmup + 267 measured). Produces quality report + latency. Correction gate = structural + no-harm +
   `no-forbidden-hits`. Result: red `observed forbidden hits: 9, expected 0`. Explicitly: "Do not add relevance
   thresholds, invent a protected holdout, generate an accepted baseline or require a synthetic relevance improvement."
   → **This is a full-mode relevance measurement of the whole system, but with no baseline-only comparison.**
3. `98a06b6d` — O1 operations + score-separation artifact (supplement-origin results only); still no ablation.
4. `d109a35c` — protected acceptance machinery; accepts a protected corpus/policy against aggregate protected
   metrics. Requires `visible forbidden-hit count == 0` and `quality-threshold status == not_required`. No baseline-only run.
5. `18d5046c` — accepted-baseline bootstrap **and** verify machinery. Verify compares a fresh candidate run against a
   checked-in canonical accepted manifest; deltas are `candidate average − canonical average` (run-over-run), not
   baseline-vs-full.
6. `fec552dc`, `e2c17cd7`, and the Q2 rotation commits — protected input authoring/freeze and recovery rotations;
   compare **rotation N vs rotation N−1** and whole-system acceptance, not Qdrant ablation.
7. `67a3e843` (2026-09-10) — materializes the coordinator-approved Q2 candidate as the canonical accepted evaluation
   baseline; removes Git-revision provenance.
8. `c8f1c95d` (2026-09-10) — deletes the accepted-baseline model/codec/verifier/resources/CLI and the
   bootstrap/candidate/promotion/verify workflow, while retaining protected holdout + acceptance policy.
9. `dee0c458` (2026-09-10) — explicitly **defers** the Qdrant marginal-value decision: "do not decide Qdrant marginal
   product value".

Answers to the distinct questions:

- **A. search-quality regression acceptance of the whole Gen2 system** — produced (correction gate, accepted baseline
  at `67a3e843`), then partly retired.
- **B. protected holdout acceptance** — machinery produced; real run required private product inputs; not a Qdrant ablation.
- **C. recovery-rotation success** — produced (rotations 1–7).
- **D. Qdrant baseline-vs-full marginal-value measurement** — **NEVER PRODUCED.** The eval runner requires
  `required-full-search`; `BaselineOnly` exists only as a serving/degradation mode, not an evaluation baseline. No
  artifact compares baseline-only vs Required/FullSearch.

Nothing in this chain produced a durable artifact whose proposition is "Qdrant supplementation improved BeautyQ by X
over ES-only."

---

## 7. Historical claim audit

| Claim | Commit/doc | Evidence it relied on | Sufficient for the literal claim? | Later superseded? | Current truth status |
|---|---|---|---|---|---|
| "prove Qdrant supplement improves without worsening ES" | `438c3407` (QP18) | real-resource test asserting one Qdrant-only append + structural no-harm | **No** — "improves" ≠ append | Gen2 cutover deleted spec (`58d3653b`) | **OVERCLAIM / historical** |
| "measured result is one improved query, one unchanged …" | `50650002` (QP19) | gate counts where `improved = append==1` | **Partly** — result matches its own gate, but gate is not a relevance/value gate | widened `5258042e`, locked `2b50e33d`, deleted `58d3653b` | **ACCURATE TO ITS GATE, NOT VALUE EVIDENCE** |
| "explicit_constraints_filter_plus_top1 … reduced harm to zero … preserved `q_broad_006` … lost `q_lashes_008` … not Y1-ready" | `fc06f9cb` ledger | full-corpus Gen1 Y0 measurement | **Yes** for the literal, qualified statement | `33f3c319` deleted the handoff, then cutover | **WAS CORRECT; EVIDENCE LOST** |
| "Qdrant remains non-production/eval/semantic-candidate evidence" | `fc06f9cb`, `d144633b` | Y0 measurement + blocked policy | **Yes** | — | **Still true** |
| "BeautyQ Qdrant marginal product value is unmeasured / baseline-vs-full comparison absent" | current 004 record (`550`, `678–679`); simplification plan §2.1 | current Gen2 state; `BeautyQEvaluationPolicy.scala:27` | **No as an absolute** — Gen1 ablation existed and showed harm; **Yes** for Gen2's predeclared comparison | — | **CORRECT_BUT_MISSING_HISTORICAL_QUALIFICATION** |
| "do not decide Qdrant marginal product value" | `dee0c458` | cleanup scope control | — | — | **Deliberate deferral, not evidence invalidation** |

No deception is implied. The pattern is: commit subjects used the word "improve" for a structural append, while the
documentary ledger (the same day) correctly said the result was "not Y1-ready" and "policy blocked."

---

## 8. Knowledge-retirement / receiver map

| Deleted owner | Commit | Replacement/current owner | Did the factual conclusion survive? |
|---|---|---|---|
| Gen1 Y0 `RuntimeEsQdrantScorecardProofSpec` (full-corpus ES-vs-Qdrant relevance measurement) | `58d3653b` | Gen2 `BeautyQCutoverGate` + `BeautyQNoHarmSupplementEvidence` (structural no-harm only) | **Machinery only partially; relevance conclusion did NOT survive** |
| `docs/BEAUTYQ_CURRENT_STATE_AND_HANDOFF.md` (Y0 evidence ledger, harm/lost-recall) | `33f3c319` | README section + later Gen2 docs | **NO receiver for the harm/lost-recall conclusion** |
| QP18/QP19 specs | `58d3653b` | Gen2 cutover/measured-evaluation specs | Append/no-worsening conclusion replaced; relevance conclusion never existed in them |
| Gen2 accepted evaluation baseline model/codec/verifier/resources/CLI | `c8f1c95d` | "Protected holdout + protected acceptance policy" retained | Whole-system Q2 acceptance conclusion **not retained as a tracked artifact** |
| Gen1 parity/vocabulary ledger (intent) | `dee0c458` | `BeautyQIntentVocabularyEvidenceSpec` golden retained | Unrelated to Qdrant value; conclusion preserved |

**Final knowledge-transfer classification: `MIXED`.** The Gen1 relevance conclusion is **LOST_WITHOUT_RECEIVER**;
the Gen2 whole-system acceptance is **PRESERVED as machinery** but the durable accepted-baseline conclusion was
**INTENTIONALLY_RETIRED** with the Q2 ceremony; the local append/no-worsening conclusion is **PRESERVED** as the Gen2
cutover gate. The Qdrant *marginal-value* conclusion was **NEVER_ESTABLISHED** for Gen2.

---

## 9. Effect on `C-QDRANT-BEAUTYQ-VALUE`

Using **only existing Feature 004 dispositions** (`KEEP`, `SIMPLIFY_LOCALLY`, `INSUFFICIENT_EVIDENCE`):

- `C-QDRANT-BEAUTYQ-VALUE` should remain **`INSUFFICIENT_EVIDENCE`**. There is still no transferable, accepted
  Gen2 baseline-vs-full marginal-value result, so the disposition does not change.
- The **basis wording** should be corrected; the current "unmeasured" phrasing erases a real Gen1 ablation whose
  result was *negative/insufficient*, not absent:
  - keep: "the required Gen2-transferable baseline-vs-full relevance delta is absent";
  - add the qualification: "BeautyQ *did* run a full-canonical-corpus ES-only vs ES+Qdrant relevance ablation on the
    Gen1 stack (`df0652b5`/`4f73070e`/`66f9e525`/`ffe32a5b`/`0259558c`/`e4359ec8`, ledger `fc06f9cb`), which found
    narrow acceptable recall wins but substantial semantic harm and a lost recall win, and left policy blocked; that
    evidence was retired with the Gen1 stack and is not transferable to the current corpus/model/implementation."
  - preserve the forbidden-substitute rule: the Gen1 measurement is *evidence*, not "historical effort," and does not
    satisfy the proposal's identical-input Gen2 requirement.
- `C-QDRANT-CAPABILITY` remains **`KEEP`**, unaffected: it is a separate candidate and is not conditioned on
  BeautyQ uplift.

This is a wording/qualification correction only; no new disposition is introduced.

---

## 10. Primary evidence index

| SHA | Path (historical/current) | Test/gate/artifact | Exact proposition |
|---|---|---|---|
| `438c3407` | `bifunctor-tagless/src/test/scala/leaderboard/search/QP18QdrantSupplementImprovementNoWorseningSpec.scala` | QP18 spec | real Qdrant-only append of exactly 1 id, structural no-harm; no relevance assertion |
| `50650002` | `…/QP19QdrantSupplementMeasuredAcceptanceGateSpec.scala` | QP19 gate | hard gate; `improved = append==1`; `QP19_QUERY_SET_LIMITED_TO_QP18_SOURCE` |
| `5258042e` / `2b50e33d` | same QP19 spec | widened + locked baseline | 4 queries, 1 improved, 3 unchanged, 0 worsened; `QP23_WIDENED_BASELINE_DRIFT` |
| `df0652b5` | `…/RuntimeEsQdrantScorecardProofSpec.scala` | Y0C | full canonical ES-only vs ES+Qdrant, `acceptableVariantIds`, real resources |
| `4f73070e`/`66f9e525`/`ffe32a5b`/`0259558c`/`e4359ec8` | same spec | Y0D/E/G/I/H | harm despite cap; source fields don't help; filter+top1 zero-harm but loses `q_lashes_008`; model axis doesn't help |
| `fc06f9cb` | `docs/BEAUTYQ_CURRENT_STATE_AND_HANDOFF.md` | evidence ledger | "measurement-useful but not Y1-ready"; "Y1 remains blocked" |
| `33f3c319` | deletes that handoff | doc consolidation | relevance/harm conclusion removed from current ownership |
| `58d3653b` / `89f69706` | deletes Gen1 stack incl. QP18/QP19 + Y0 spec | Gen2 cutover | old value evidence deleted |
| `afbc03c7` | `beautyq-search-gen2-eval/.../BeautyQEvaluationCorpus.scala`, corpus v2 JSON | Q1 kernel | 89 V2 cases; **no** execution/thresholds/baseline |
| `a67d9143` | `BeautyQMeasuredEvaluation.scala` + artifacts under `target/search-gen2` | Q2 full-corpus run | one Required/FullSearch startup; 89 cases; forbidden hits 9 (red); **no baseline-only** |
| `98a06b6d` | score-separation artifact | O1/Q2 | supplement-origin score separation only |
| `d109a35c`/`18d5046c` | protected acceptance + accepted-baseline machinery | Q2 acceptance | candidate-vs-frozen-canonical over time; not baseline-vs-full |
| `67a3e843` | `beautyq_accepted_evaluation_baseline_v1.json` | accepted baseline | Gen2 whole-system Q2 accepted (materialized) |
| `c8f1c95d` | deletes accepted-baseline model/resources/CLI | cleanup | durable Gen2 accepted-baseline artifact retired |
| `dee0c458` | cleanup commit | deferral | "do not decide Qdrant marginal product value" |
| current | `beautyq-search-gen2-eval/.../BeautyQEvaluationPolicy.scala:27` | policy | "No relevance thresholds or protected holdout have been accepted." (present since `afbc03c7` as "No thresholds accepted in Q1") |
| current | `specs/004-.../decision-record.md:235-237,550,678-679` | 004 assessment | "baseline-vs-full relevance delta is absent"; "marginal product value is unmeasured" |
| current | `BeautyQCutoverGate.scala:216,238-257` | cutover gate | `improved = !worsened && supplementOnlyIds.nonEmpty` (append, not relevance) |

---

## 11. Remaining unknowns

1. The exact Gen1 Y0C emitted counts (e.g. "43 harmed queries", "2 recall wins") are not pinned by any test literal
   in the surviving spec; they are recoverable only from commit/doc prose and would require re-running the deleted
   Gen1 stack. The *structure* (full-corpus ES-only vs supplemented, acceptable-vs-unacceptable, harm/recall fields)
   is source-confirmed.
2. Whether the Gen2 Q2 full-corpus run (`a67d9143`) ever produced a baseline-only counterpart during an untracked
   local run cannot be proven from the repo: its artifacts were written only under `target/search-gen2` and are not
   tracked; the runner explicitly requires FullSearch.
3. The exact Gen2 accepted-baseline values (from `67a3e843`) are recoverable from `c8f1c95d`'s deleted diff, but their
   proposition is whole-system acceptance, not Qdrant marginal value; their product-value interpretation is not
   documented.
4. No artifact anywhere states "Qdrant improved BeautyQ by X over ES-only" for Gen2, and none states "Qdrant failed to
   improve Gen2 BeautyQ"; the Gen2 marginal question is genuinely open.
