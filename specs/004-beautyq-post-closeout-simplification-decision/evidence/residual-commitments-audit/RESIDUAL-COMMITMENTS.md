# Residual Commitments Audit

Read-only residual-obligation audit of develop-reachable BeautyQ / Search Gen2 history after
Feature 004 closeout. Current source/tests/normative docs decide current truth; history recovers
promises, rationale, and possibly lost obligations.

- Evaluated HEAD: `a92d0acf492f18440d00e92b4f294702b05451b8`
- Branch: `develop`
- Worktree/index as observed: `git status --porcelain` empty; no staged or unstaged changes.
- No tracked file was modified; no sbt/product test was run.
- Scope: develop-reachable history (`git log`, `git show`, read-only) plus the current tracked tree.
  `--all` was used only to enumerate historically added path names; status claims are bound to HEAD.
- Reconciliation revision (this pass): `RC-001` reclassified `DOCUMENTATION_STALE_ONLY`; classification
  tally and candidate IDs reconciled to `RC-001..RC-007`; see `RC-001-FEATURE-001-FORENSIC.md`.

---

## 1. Executive verdict

Counts by classification (material candidates only; see §8):

Material RC candidates: `RC-001`..`RC-007` (total 7). Each RC-ID appears exactly once; there are no
aliases and no `none` pseudo-candidate. Non-RC "family" rows are informational and are not counted.

| Classification | Count | IDs |
| --- | ---: | --- |
| CURRENT_REQUIRED_GAP | 0 | — |
| APPROVED_NOT_IMPLEMENTED | 0 | — |
| LOST_OBLIGATION | 0 | — |
| INTENTIONALLY_DEFERRED | 1 | RC-002 |
| OPTIONAL_FOLLOWUP | 2 | RC-003, RC-004 |
| STALE_FOSSIL | 2 | RC-005, RC-006 |
| INSUFFICIENT_EVIDENCE | 1 | RC-007 |
| DOCUMENTATION_STALE_ONLY | 1 | RC-001 |
| family: SUPERSEDED (not counted) | — | F-011 pointer resolution, M8 telemetry, Gen1 stack |
| family: COMPLETED_ELSEWHERE (not counted) | — | implementation-plan gaps, promise-audit D-01..D-09, module split/derivation |
| family: REJECTED_OR_NON_GOAL (not counted) | — | M8 telemetry, seed/full-loader dedup unification, multi-valued fields axis |

Count reconciliation: 0 + 0 + 0 + 1 + 2 + 2 + 1 + 1 = **7** material candidates.
`DOCUMENTATION_STALE_ONLY` is the RC-001-specific class from the reconciliation taxonomy
(`RC-001-FEATURE-001-FORENSIC.md`); it is a documentary-only stale-fossil subtype.

Answers:

- **Any CURRENT_REQUIRED_GAP?** No. Every deferred capability named by old plans is now carried by a
  current normative owner as an "Accepted limit" or "Non-goal"
  (`docs/gen2/BEAUTYQ_SEARCH_GEN2_TECHNICAL_SPEC.md:50-63, 2339-2355`) or by Feature 004 dispositions.
  No current normative doc requires an obligation that current source does not have.
- **Any APPROVED_NOT_IMPLEMENTED?** No — the previous audit's `RC-001 = APPROVED_NOT_IMPLEMENTED` is
  **overturned (TOO_STRONG)**. `WORKING.md` §O contains the full durable T039–T045 gate outputs ending
  in T045 `Status: COMPLETE` (2026-09-08); only `REPORT.md`'s own framing was never written back.
  `RC-001` is `DOCUMENTATION_STALE_ONLY`.
- **Any LOST_OBLIGATION?** No net-new one. The two historically lost knowledge items (Gen1 Qdrant
  relevance conclusion; Gen2 whole-system Q2 accepted-baseline conclusion) are now either preserved in
  tracked Feature 004 evidence or intentionally retired (`c8f1c95d`), so they are not open obligations.
- **Any old plan/TODO warranting a new scoped feature now?** No new feature is warranted. The only
  change-oriented item (`RC-002`, trace simplification) already has a human-approved disposition that
  explicitly authorizes no implementation; it needs a separate explicitly authorized scoped task, not a
  new speculative feature. `RC-003`/`RC-004` are optional, separately authorized evidence work already
  named by Feature 004.
- **Is Feature 004 internally complete?** Yes as a decision object: `state = CLOSED`,
  `humanVerdict = APPROVE` for all eight dispositions, `explicitNoImplementation = true`, tracked
  archaeology preserved, and at audit execution the evaluated HEAD `a92d0acf` was the Feature 004
  closeout commit with no later commit present. The only wart
  is bookkeeping: `specs/004-.../tasks.md` still shows every task unchecked (`[ ]`) even though the
  decision record and closeout commit prove the work; this is stale Spec Kit bookkeeping, not an open
  obligation.

No Feature 004 disposition is reopened. No contradiction with Feature 004's final tracked evidence was
found.

---

## 2. Current-tree residual markers

Current tracked Scala sources contain **no** `TODO`/`FIXME`/`XXX`/`HACK`/`TBD`/`WIP` markers. The only
tracked marker hits are in opencode/Spec Kit tooling templates (`.opencode/commands/speckit.*.md`) and
in the tracked feature-001 review bundle, which are not BeautyQ product obligations.

Material current hits (none is a mandatory obligation):

| Location | Text (abridged) | Assessment |
| --- | --- | --- |
| `docs/gen2/BEAUTYQ_SEARCH_GEN2_TECHNICAL_SPEC.md:50-63` | Non-goals list | Current normative; intentional exclusions |
| `.../TECHNICAL_SPEC.md:2339-2355` | "Accepted limits ... not open defects or roadmap commitments" | Current normative owner of every deferred capability |
| `docs/gen2/BEAUTYQ_SEARCH_GEN2_OPERATIONS.md:144` | "Automatic GC is not implemented" | Matches accepted limit "no automatic Qdrant GC"; operator procedure present |
| `docs/search/NEW_DOMAIN_ONBOARDING.md:185-187` | "keep [seed vs full loader dedup] separate, don't unify without a coordinator decision" | Current normative rule; resolved deliberate separation, not an open TODO |
| `beautyq-search-gen2-eval/.../BeautyQEvaluationPolicy.scala:27` | "No relevance thresholds or protected holdout have been accepted." | **Stale fossil** — a protected holdout + acceptance policy are tracked and accepted (RC-005) |
| `beautyq-search-gen2-wiring/.../BeautyQQdrantPolicy.scala:8` | "Transport and physical lifecycle are deliberately later bricks." | **Stale fossil** — `search-gen2-qdrant` now owns `QdrantGenerationLifecycle` and the client (RC-005) |

---

## 3. Historical plan / TODO / handoff findings

Plans/roadmaps/handoffs/checklists/status docs that existed in develop history were enumerated by path
(`git log --diff-filter=A -- docs/ ONBOARDING.md`). All plan-like documents except the current
`docs/gen2/*` owners and `docs/search/*` principles were later deleted. Deleted-doc audit results:

- **Deleted Gen2 delivery docs** (`BEAUTYQ_SEARCH_GEN2_IMPLEMENTATION_PLAN.md` @ `581f8119`,
  `BEAUTYQ_SEARCH_GEN2_REVIEW.md` @ `05392db3`, `SEARCH_GEN2_FRAMEWORK_SCOPE.md` @ `71f60864`,
  `BEAUTYQ_SEARCH_GEN2_PROMISE_AUDIT.md` @ `2349670b`): recovered obligations are either implemented in
  Gen2 sources, explicitly carried as technical-spec accepted limits/non-goals, or superseded. Promise-audit
  deviations D-01..D-09 are all implemented or accepted limits (e.g. D-07 G-8 init re-entry is now owned
  by spec §7 L487-490 + `BeautyQInitializationOrderSpec`; D-08 one-case retention removed).
- **Deleted split/derivation docs** (`BEAUTYQ_SEARCH_CONTRACT_MODULE_SPLIT_PLAN.md` @ `157469e6`,
  `CATALOG_DECLARATION_DERIVATION_ROADMAP.md` @ `544b749c`,
  `CATALOG_DECLARATION_DERIVATION_HANDOFF.md` @ `6baaaea5`, `BEAUTYQ_NOMINAL_ID_REFACTOR.md` @ `cbb576b3`):
  closeout complete; residual policy questions (seed `toSnapshot` unification) were converted into a
  current normative "keep separate" rule (`NEW_DOMAIN_ONBOARDING.md:185-187`). No open obligation.
- **Deleted Gen1/codebase-review/local docs** (`BEAUTYQ_CURRENT_STATE_AND_HANDOFF.md` @ `33f3c319`,
  local hybrid roadmap + Qdrant criteria @ `d144633b`, M15 checklist @ `35c7a374`,
  codebase-review gaps @ `fcb189cb`, Qdrant supplement gate/checklist/runbook @ `58d3653b`/`920250c4`):
  obligations are implemented by Gen2, superseded by the cutover, or now accepted limits. The one
  candidate with no current owner — "M8 production telemetry foundation" — is deliberately outside
  current scope: the technical spec lists "preserving historical M8–M21 evaluation scaffolding in the
  serving classpath" as a non-goal (L63) and no telemetry owner exists. Classified SUPERSEDED/REJECTED,
  not an open obligation.
- **`docs/BEAUTYQ_CURRENT_STATE_AND_HANDOFF.md` (Gen1 Y0 evidence ledger)**: its Qdrant harm/lost-recall
  conclusion was lost at `33f3c319` with no receiver, but the full conclusion is now durably preserved by
  Feature 004's tracked `QDRANT-VALUE-FORENSIC.md` §4/§7. No action; no resurrection.

---

## 4. Expanded commit-message commitments

Commit subjects and bodies across develop history were searched (`git log -i --grep=...`) for
next/remaining/follow-up/deferred/later/future/not yet/blocked/separate decision/phase/etc., and bodies
of the most recent 25 commits were read in full. Findings:

- `dee0c458` ("remove architecture and governance fossils") explicitly **defers** product/quality
  decisions: "do not decide Qdrant marginal product value ... do not consolidate sbt projects or remove
  trace owners without a separate decision." This matches the Feature 004 dispositions; no unrecorded
  implementation was later promised.
- `c8f1c95d` ("remove expired Q2 evidence ceremony") states "Full repository validation is intentionally
  deferred until the subsequent architecture/governance cleanup wave is complete." The subsequent
  `dee0c458` body records that the deferred full repository `sbt +test` was then run once
  (1972 succeeded, 0 failed/canceled). The deferral was discharged. No open obligation.
- `3fff92a8` says "Keep the completed documentation-history review frozen" and "make follow-up
  implementation work separately human-authorized" — consistent with 004, but it also treats Feature 001
  as completed, which conflicts with `RC-001`.
- Q2 rotation commits (`21587343`, `4e3f6aed`, `3c4ca064`, `ebb2a7a2`, `66525825`, `36d3f9dd`) repeatedly
  listed "fresh protected acceptance, bootstrap, candidate promotion, accepted-baseline verify" as
  remaining. Those were completed/closed and then the entire accepted-baseline workflow was intentionally
  retired at `c8f1c95d`. No surviving commitment.
- No commit body was found that promises a currently-unmet implementation.

---

## 5. Feature/spec residuals

- `specs/001-beautyq-doc-history-review`: **stale documentation, `RC-001` (`DOCUMENTATION_STALE_ONLY`).**
  `tasks.md` marks T001–T045 (including Phase-11 finalization T039–T045) `[x]`, and `WORKING.md` §O
  contains the full durable T039–T045 gate outputs ending in a T045 `Status: COMPLETE` (2026-09-08). The
  gates ran and `REPORT.md` was deliberately not edited (T041/T042: 0 repairs / 0 rewrites), so its own
  framing still says final gates "remain pending", T039–T045 "remain unstarted", "... has not been
  finalized" (`REPORT.md:3,414-415,511`). `REPORT.md` and `WORKING.md` were archived once at `d1f07798`
  and never modified. The defect is stale REPORT self-description only; see
  `RC-001-FEATURE-001-FORENSIC.md`.
- `specs/003-beautyq-distage-izumi-leverage-audit`: completed. All `tasks.md` checkboxes are `[ ]`
  (stale bookkeeping). One unresolved research state is intentionally preserved: `C-20`
  (`INDETERMINATE`) — the possible exact-`1.2.25` generic domain-result trace/render surface was never
  searched to resolution. Classified INSUFFICIENT_EVIDENCE/optional (`RC-007`).
- `specs/004-beautyq-post-closeout-simplification-decision`: completed decision object; all `tasks.md`
  checkboxes `[ ]` (stale bookkeeping). Dispositions recorded in `decision-record.md`. No implementation.
- `.specify/feature.json` still points at the closed `specs/004-...` feature. `dee0c458` intentionally
  left it as "mutable control-plane state for the next human-selected Spec Kit feature". The pointer
  intentionally remains on the last human-selected feature until a subsequent human feature selection;
  it is not a residual defect and is excluded from `RC-006`.

---

## 6. Explicit high-value-family status

| Family | Current status | Anchor |
| --- | --- | --- |
| Second production domain / D1 | INTENTIONALLY_DEFERRED. Deferred pending product identity + source topology; not authorized. | `TECHNICAL_SPEC.md:2327-2330,2343`; Feature 004 `C-QDRANT-CAPABILITY` note; deleted post-cutover plan @ `c8f1c95d^` |
| Qdrant current-Gen2 marginal-value experiment | OPTIONAL_FOLLOWUP (`RC-003`). Gen2 baseline-only vs Required/FullSearch comparison never produced; historical Gen1 full-corpus ablation (narrow wins + substantial harm, policy blocked) is non-transferable and now preserved in 004 evidence. | `BeautyQEvaluationPolicy.scala:27` (thresholds part); `QDRANT-VALUE-FORENSIC.md` §4/§6; 004 decision-record `C-QDRANT-BEAUTYQ-VALUE` |
| Remaining non-trace test-coverage consolidation | OPTIONAL_FOLLOWUP (`RC-004`). Per-invariant coverage map absent; no deletion recommended; trace family excluded. | 004 decision-record `C-TEST-COVERAGE` |
| Approved trace simplification (Feature 004) | INTENTIONALLY_DEFERRED (`RC-002`). Disposition `SIMPLIFY_LOCALLY` approved; implementation explicitly NOT authorized; ordered obligation: add typed `BeautyQSemanticLabelPolicy.forAction` `stableKey:text`+ordering proof FIRST, then remove six renderers/goldens. Renderers still present. | 004 decision-record `C-TRACE-REDUNDANT-DIAGNOSTICS` + change gate; `git ls-files` shows `PlanIdentityTrace`, `BeautyQSearchPlanCompilationTrace`, `BeautyQCandidatePlanTrace`, `BeautyQInputTraceGen2`, `SearchPlanTrace`, `BeautyIntentRuleTrace` still tracked |
| Old Gen1→Gen2 migration/cutover residue | SUPERSEDED. Gen1 search stack deleted; cutover-named specs (`BeautyQGen2CutoverGateSpec`, `BeautyQSearchGen2CutoverCommunicationSpec`) are current proof owners, not residue. | `TECHNICAL_SPEC.md:2334`; `c8f1c95d` |
| Qdrant lifecycle / cleanup / GC promises | No open promise. Automatic GC is an accepted limit; exact operator cleanup is owned by operations. `search-gen2-qdrant` implements lifecycle/transport. | `TECHNICAL_SPEC.md:2349`; `OPERATIONS.md:144`; `QdrantGenerationLifecycle.scala` |
| Accepted / protected evaluation artifacts and thresholds | Intentional end state. Protected holdout + acceptance policy retained (`beautyq-protected-holdout-v1.json` v9, `beautyq-protected-acceptance-policy-v1.json` v8); accepted-baseline workflow intentionally retired. No accepted *relevance* thresholds. | `c8f1c95d`; `67a3e843`; `BeautyQEvaluationPolicy.scala:27` |
| Recovery-rotation remnants | COMPLETED_ELSEWHERE/REMOVED. Recovery reserve/rotation/provenance resources deleted; only current protected acceptance policy + holdout remain. | `c8f1c95d`; `git ls-files` shows no recovery/reserve/rotation files |
| Module/project consolidation promises | REJECTED_OR_KEPT. Ten Gen2 projects retained with source-confirmed boundaries; consolidation requires new evaluation. | 004 decision-record `C-PROJECT-BOUNDARIES` |
| Distage/Izumi leverage findings (003) | No required action. 7 `WELL_USED`, 24 `BEAUTYQ_SPECIFIC`, 0 `UNDERUSED`/`HARD_TO_DISCOVER`/`MISSING_GENERIC_PRIMITIVE`; one docs-only recommendation. | `specs/003/.../research/05` |
| Docs discoverability R-01 / C-05 (`addDependency`) | REJECTED_OR_NON_GOAL / no residual candidate. Docs-only framework recommendation/evidence item; Feature 004 explicitly rejected it as a final decision unit; no BeautyQ/Search Gen2 implementation obligation follows. Not aliased to any RC-ID. | 003 `R-01` / `C-05`; 004 `P-AUDIT-DISC = REJECTED` |
| Cursor / PlanIdentity follow-ups | No open item. `PlanIdentity` is intentionally value-only and execution-scoped; cursor authentication is an accepted limit. | `TECHNICAL_SPEC.md:2345`; 003 `C-20` area |
| Elasticsearch / Qdrant backend-parity promises | REJECTED_OR_NON_GOAL. ES owns complete baseline; Qdrant candidate-only; no fusion/rerank; no Qdrant facets/groups/pagination. | `TECHNICAL_SPEC.md:50-63,69-71` |
| Eval / serving isolation promises | Implemented and retained. Eval is a downstream, non-serving consumer; firewall spec enforces. | `SearchGen2ModuleFirewallSpec`; `TECHNICAL_SPEC.md:2300-2305,2334`; 004 `C-PROJECT-BOUNDARIES` |
| SQL / materialization confinement | Implemented and retained. Doobie/SQL confined to materialization; firewall check retained. | `dee0c458`; `SearchGen2ModuleFirewallSpec`; 004 `C-PROJECT-BOUNDARIES` |
| Operator / runbook TODOs | None open. Automatic GC and latency gating are accepted limits; startup/recovery/cleanup are documented procedures. | `OPERATIONS.md`; `TECHNICAL_SPEC.md:2349-2350` |
| Source comments saying temporary/until/later/future domain/second domain/separate decision | Two stale comments remain (`BeautyQEvaluationPolicy.scala:27`, `BeautyQQdrantPolicy.scala:8`); other `later`/`future` hits are ordinary semantics ("later duplicate", "later page") or current non-goals. | RC-005 |

---

## 7. Lost-knowledge / receiver map

| Historical conclusion | Deletion | Current receiver | Action needed |
| --- | --- | --- | --- |
| Gen1 full-corpus ES-only vs ES+Qdrant relevance ablation (narrow wins + substantial harm; Y1 blocked) | Lost with Gen1 stack (`58d3653b`, ledger deleted `33f3c319`) | **Preserved** by Feature 004 tracked `QDRANT-VALUE-FORENSIC.md` §4/§7 and `research.md` conclusion B | None; 004 evidence preserves it |
| Gen2 whole-system Q2 accepted-baseline conclusion | Retired (`c8f1c95d`) | Machinery retired intentionally; proposition recorded in `QDRANT-VALUE-FORENSIC.md` §6/§8 as intentional retirement | None (intentional) |
| QP18/QP19 "improvement" overstatement | Specs deleted at cutover | Corrected interpretation preserved by `QDRANT-VALUE-FORENSIC.md` §2/§3 (append/no-worsening, not relevance) | None |
| Gen1 intent parity ledger (r001–r086) | Deleted `dee0c458` | Complete current declaration golden retained (`BeautyQIntentVocabularyEvidenceSpec`); production declaration authoritative | None |
| Feature 001 final mechanical/wording/scope/cross-surface/canonical finalization | Ran 2026-09-08; recorded in `WORKING.md` §O, not written back into `REPORT.md` | `WORKING.md` §O T039–T044 PASS + T045 `Status: COMPLETE`; `REPORT.md` header stale | `RC-001` (`DOCUMENTATION_STALE_ONLY`) — optional one-line doc correction |
| C-20 possible generic domain-result trace/render surface at `1.2.25` | Never resolved | No receiver; Feature 004 resolved the local trace question differently | `RC-007` (optional research; not required) |

---

## 8. Actionable candidates

### RC-001 — Feature 001 review stale self-description
- **Classification:** DOCUMENTATION_STALE_ONLY (overturns the prior `APPROVED_NOT_IMPLEMENTED`)
- **Current owner:** `.review-bundles/docs-history-speckit/REPORT.md` header/appendix/closing lines;
  finalization evidence owned by `.review-bundles/docs-history-speckit/WORKING.md` §O
- **Historical origin:** review run B; gates executed 2026-09-08; bundle archived `d1f07798` (2026-09-10)
- **Why still relevant:** the mandatory finalization gates T039–T045 *did* run and are durably recorded
  in `WORKING.md` §O (T039–T044 PASS; T045 `Status: COMPLETE`). Because T041/T042 recorded 0 REPORT
  edits, `REPORT.md` retained its earlier "final gates pending / T039–T045 remain unstarted / has not
  been finalized" framing. Only the report's self-description is stale.
- **Proof it is already complete:** `WORKING.md` §O `### T039 ... PASS` through
  `### T045 completion gate — COMPLETE (2026-09-08)`, including both exact corpus identities, the T040
  scan (18 occurrences, 0 unsupported), T041/T042 zero edits, T043 cross-surface agreement, T044 both
  canonical diffs empty, and T045 `Status: COMPLETE`. See `RC-001-FEATURE-001-FORENSIC.md`.
- **Smallest sensible next scope:** optional one-line documentation correction to `REPORT.md`
  lines 3/414/511 (and the superseded pre-§O checkpoint line in `WORKING.md`) pointing at §O; or
  `NO_ACTION` if the frozen 001 record is intentionally preserved. Not an engineering obligation.
- **Human decision required:** no (trivial doc hygiene); yes only if choosing to alter the frozen bundle.

### RC-002 — Approved trace simplification (ordered transformation)
- **Classification:** INTENTIONALLY_DEFERRED
- **Current owner:** none for implementation; Feature 004 decision record owns the disposition and gate
- **Historical origin:** 004 `C-TRACE-REDUNDANT-DIAGNOSTICS`
- **Why still required:** disposition `SIMPLIFY_LOCALLY` is human-approved; the ordered obligation is
  precise. It is not currently authorized, so it is not a gap — but it is a ready, high-value cleanup.
- **Proof it is not already complete:** the six renderers + goldens are still tracked
  (`PlanIdentityTrace`, `BeautyQSearchPlanCompilationTrace`, `BeautyQCandidatePlanTrace`,
  `BeautyQInputTraceGen2`/`BeautySearchRequestTrace`+`BeautyIntentTrace`, `SearchPlanTrace`), and no
  non-trace typed proof for `BeautyQSemanticLabelPolicy.forAction` was found (`CanonicalSemanticLabel`
  appears only in the vocabulary-ledger/parser/candidate specs, not as a dedicated label-text+order proof).
- **Smallest sensible next scope:** a separate, explicitly human-authorized scoped task: FIRST add the
  typed `stableKey:text` + ordering proof; THEN remove the renderers/goldens and update the named
  docs/scaladoc references (`TECHNICAL_SPEC.md:856,1448`, `NEW_DOMAIN_ONBOARDING.md:396`,
  `SearchPlan.scala:69`, `BeautyQCandidatePlanCompiler.scala:21`).
- **Human decision required:** yes (implementation authorization).

### RC-003 — Current-Gen2 Qdrant baseline-vs-full marginal value
- **Classification:** OPTIONAL_FOLLOWUP
- **Current owner:** product owner + evaluation owner (named by 004)
- **Historical origin:** simplification plan §2.1; Gen1 Y0 ablation chain (`df0652b5`…`e4359ec8`,
  ledger `fc06f9cb`); 004 `C-QDRANT-BEAUTYQ-VALUE`
- **Why still required:** the required current-state comparison is absent, so the BeautyQ supplement
  policy/default cannot be justified or refuted on measured product value.
- **Proof it is not already complete:** `BeautyQEvaluationPolicy.scala:27` still states no relevance
  thresholds accepted; no tracked baseline-only vs Required/FullSearch artifact exists; `BeautyQCutoverGate`
  "improvement" is append/no-worsening.
- **Smallest sensible next scope:** optional predeclared ablation with identical inputs (application
  revision, snapshot, ES generation, corpus, requests, page policy) across BaselineOnly vs
  Required/FullSearch, with a named metric and stop conditions.
- **Human decision required:** yes (whether to spend the evidence work; not required).

### RC-004 — Non-trace test-coverage map
- **Classification:** OPTIONAL_FOLLOWUP
- **Current owner:** test/build owner (named by 004)
- **Historical origin:** simplification plan §2.4; 004 `C-TEST-COVERAGE`
- **Why still required:** overlaps are visible by name but none proved redundant; deletion is forbidden
  before a coverage map.
- **Proof it is not already complete:** 004 records no per-invariant ownership map for the remaining
  (non-trace) corpus.
- **Smallest sensible next scope:** optional per-invariant primary-owner map for the remaining corpus.
- **Human decision required:** yes (whether to invest).

### RC-005 — Stale source comments (`BeautyQEvaluationPolicy`, `BeautyQQdrantPolicy`)
- **Classification:** STALE_FOSSIL (listed as actionable because one was cited as current evidence by 004)
- **Current owner:** the files themselves
- **Historical origin:** `BeautyQEvaluationPolicy.scala:27` introduced at `a67d9143` (2026-07-31);
  `BeautyQQdrantPolicy.scala:8` introduced during Qdrant extraction
- **Why still required:** `BeautyQEvaluationPolicy.scala:27` asserts "No relevance thresholds or
  protected holdout have been accepted", but a protected holdout (`beautyq-protected-holdout-v1.json`,
  v9) and acceptance policy (`beautyq-protected-acceptance-policy-v1.json`, v8) are tracked and used, and
  `NEW_DOMAIN_ONBOARDING.md:666` treats a protected holdout as current. Feature 004 cited this line as
  current evidence. `BeautyQQdrantPolicy.scala:8` says transport/lifecycle are "later bricks", but
  `search-gen2-qdrant` now owns `QdrantGenerationLifecycle` + client/compiler.
- **Proof it is not already complete:** current tracked resources + `QdrantGenerationLifecycle.scala`.
- **Smallest sensible next scope:** correct the two comments to current truth (the "no relevance
  thresholds" half of the eval-policy comment remains true). Source-comment-only; no behavior change.
- **Human decision required:** no (trivial source-doc hygiene); yes only if it must touch Feature 004's
  cited anchor.

### RC-006 — Completed-feature task-checkbox bookkeeping
- **Classification:** STALE_FOSSIL
- **Current owner:** `specs/003-.../tasks.md`, `specs/004-.../tasks.md`
- **Why still required:** 003 and 004 are complete/closed, yet their `tasks.md` checkboxes remain `[ ]`
  (stale Spec Kit bookkeeping).
- **Proof it is not already complete:** `git grep "\[ \]"` in both task files.
- **Smallest sensible next scope:** update the completed-feature checkboxes if the records are ever
  touched; not a product/runtime obligation.
- **Human decision required:** no.
- **Excluded (not a residual defect):** `.specify/feature.json` intentionally remains on the last
  human-selected feature until a subsequent human feature selection (`dee0c458`); it is not part of this
  stale-fossil finding and must not be changed merely for cleanup.

### RC-007 — 003 `C-20` unresolved framework-API existence question
- **Classification:** INSUFFICIENT_EVIDENCE
- **Current owner:** none (research-only)
- **Historical origin:** 003 `C-20`; 004 `P-AUDIT-DISC`/trace analysis
- **Why still required:** not required. It is a non-authoritative research question; Feature 004 resolved
  the local trace question without it.
- **Proof:** 003 `research/05` states `C-20` stays `INDETERMINATE`; 004 record keeps it upstream.
- **Smallest sensible next scope:** none unless someone later wants the framework-doc answer.
- **Human decision required:** no.

### Candidate set closed at RC-001..RC-007

Former aliases are eliminated from the tally: stale-comment `RC-008`/`RC-009` are folded into `RC-005`;
the former `RC-010` is `RC-001`; there is no `RC-011`. Each material candidate is counted exactly once
in §1, and `RC-001`'s classification matches `RC-001-FEATURE-001-FORENSIC.md`.

---

## 9. Closed / superseded traps

Do **not** resurrect:

- **Gen1 Qdrant Y1 blockage / Gen1 Y0 numbers as current policy.** Retired with the Gen1 stack; now only
  non-transferable history preserved by Feature 004 evidence.
- **QP18/QP19 as relevance/"improvement" evidence.** They prove structural append/no-worsening only.
- **Removed Q2 acceptance ceremony** (accepted baseline, bootstrap/promotion/verify, freeze/break-glass,
  recovery rotations). Intentionally removed at `c8f1c95d`; do not recreate.
- **Feature 001's F-011 spec §13 misdirection.** The post-cutover plan it referenced is itself gone; the
  current technical spec no longer contains the misdirection. SUPERSEDED/COMPLETED.
- **M8 production telemetry / M8–M21 scaffolding.** Explicitly outside current serving scope
  (`TECHNICAL_SPEC.md:63`); no telemetry owner exists and none is required.
- **Second production domain D1.** Deferred, not authorized; do not manufacture a domain to prove
  genericity.
- **Promise-audit D-01..D-09 gaps** (forgeable aggregates, undocumented root, silent registries,
  keep-all retention, multi-valued fields, init re-entry, analyzer/caching/cursor gaps). All are
  implemented or explicit accepted limits.
- **Seed-loader vs full-loader `toSnapshot` dedup unification.** Current normative rule keeps them
  separate; not an open refactor.
- **Multi-valued (`Vector[String]`) / DateTime exact-facet-grouping / custom-analyzer / authenticated
  cursor / automatic readiness promotion / rate limiter** — accepted limits, not roadmap commitments.
- **Deleted trace diagnostics already adjudicated by Feature 004.**

---

## 10. Primary evidence index

Current:
- `docs/gen2/BEAUTYQ_SEARCH_GEN2_TECHNICAL_SPEC.md:50-63` — Non-goals (CDC, Qdrant facets/groups/pagination,
  fusion/rerank, M8–M21 serving scaffolding).
- `docs/gen2/BEAUTYQ_SEARCH_GEN2_TECHNICAL_SPEC.md:2327-2355` — delivery closure, D1 deferred, accepted
  limits (no second domain, no authenticated cursor, no automatic Qdrant GC, no custom analyzers,
  multi-valued limited, no rate limiter, no process-local generation cache).
- `docs/gen2/BEAUTYQ_SEARCH_GEN2_OPERATIONS.md:144` — "Automatic GC is not implemented".
- `docs/search/NEW_DOMAIN_ONBOARDING.md:185-187,193-205` — seed/full-loader dedup kept separate;
  catalog/materialization closeout complete.
- `docs/search/NEW_DOMAIN_ONBOARDING.md:666` — protected holdout is a current concept.
- `beautyq-search-gen2-eval/.../BeautyQEvaluationPolicy.scala:27` — "No relevance thresholds or protected
  holdout have been accepted." (stale in the holdout clause).
- `beautyq-search-gen2-wiring/.../BeautyQQdrantPolicy.scala:8` — "Transport and physical lifecycle are
  deliberately later bricks." (stale).
- `search-gen2-qdrant/src/main/.../QdrantGenerationLifecycle.scala`; `QdrantGen2JsonClient.scala` — Qdrant
  transport/lifecycle exist.
- `beautyq-search-gen2-eval/src/test/resources/.../protected/beautyq-protected-holdout-v1.json`,
  `beautyq-protected-acceptance-policy-v1.json` — accepted protected artifacts.
- `beautyq-search-gen2-contract/.../BeautyQIntentVocabularyGen2.scala:59` —
  `BeautyQSemanticLabelPolicy.forAction`; no dedicated typed label-text/order proof found.
- `specs/004-.../decision-record.md` — eight dispositions, `state = CLOSED`, `humanVerdict = APPROVE`,
  `explicitNoImplementation = true`; trace change gate; `C-QDRANT-BEAUTYQ-VALUE`,
  `C-TEST-COVERAGE`, `C-PROJECT-BOUNDARIES`, `C-TRACE-DECLARATION-GOLDEN`, `C-TRACE-LIVE`.
- `specs/004-.../research.md` — durable conclusions A–J.
- `specs/003-.../research/05-synthesis-and-recommendations.md` — R-01 docs-only; C-20 `INDETERMINATE`.
- `.review-bundles/docs-history-speckit/REPORT.md:3,414-415,511` — report not finalized; T039–T045
  unstarted.
- `specs/001-beautyq-doc-history-review/tasks.md:190-196` — T039–T045 marked `[x]`.
- `.specify/feature.json` — `{"feature_directory":"specs/004-beautyq-post-closeout-simplification-decision"}`.

Historical (develop-reachable):
- `a92d0acf` — Feature 004 closeout commit; evaluated HEAD at audit execution (no later commit present then).
- `dee0c458` — "do not decide Qdrant marginal product value ... do not consolidate sbt projects or remove
  trace owners without a separate decision"; full `sbt +test` 1972/0/0 recorded.
- `c8f1c95d` — removed Q2 recovery/freeze/accepted-baseline; retained protected holdout + acceptance;
  full-repo validation deferred then discharged by `dee0c458`.
- `67a3e843` — materialized accepted baseline (later retired).
- `d1f07798` — archived Feature 001 review bundle; REPORT.md added once, never modified.
- `33f3c319` — deleted Gen1 Y0 evidence ledger (conclusion now preserved by 004).
- `58d3653b` / `89f69706` — Gen1 stack deletion/cutover.
- `581f8119`, `05392db3`, `71f60864`, `2349670b`, `544b749c`, `6baaaea5`, `157469e6`, `cbb576b3`,
  `d144633b`, `35c7a374`, `fcb189cb`, `920250c4` — deleted plan/roadmap/handoff/audit docs; recovered
  obligations implemented, accepted, or superseded.

---

## 11. Remaining unknowns

- RESOLVED for `RC-001`: the T039–T045 gates were executed 2026-09-08 and durably recorded in
  `WORKING.md` §O (T045 `Status: COMPLETE`); only `REPORT.md`'s framing text is stale. No longer an
  unknown.
- Exact Gen1 Y0 emitted counts are not test-pinned (prose-only); structure is source-confirmed.
- Whether any untracked Gen2 baseline-only eval run ever occurred (`target/`-only); not provable from the
  repo; runner requires FullSearch.
- Whether the "no protected holdout accepted" comment in `BeautyQEvaluationPolicy.scala:27` reflects a
  narrower intended meaning (policy-level rather than evaluation-level acceptance).
- No remote/fetch history was audited; scope is the local develop-reachable history.
