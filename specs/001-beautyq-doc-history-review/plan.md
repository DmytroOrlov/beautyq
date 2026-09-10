# Research Execution Plan: BeautyQ Search / Search Gen2 Documentation History Review

**Branch**: `develop` @ `789e56674145a45c31e2e25d997c60b359431a48` (frozen baseline — **no Spec Kit feature branch is created or switched**; constitution §4 / FR-004. `setup-plan.sh` derived the feature-directory label `001-beautyq-doc-history-review`, but the working tree remains on `develop`.) | **Date**: 2026-09-04 | **Spec**: [spec.md](./spec.md)

**Governing Constitution**: `.specify/memory/constitution.md` **v1.0.2** — non-negotiable; its supremacy clause overrides every stock Spec Kit implementation-oriented assumption in this plan wherever they conflict. The 1.0.2 PATCH clarifies only the §11 provenance unit (per material evidence anchor / per atomic material analytical statement; composite findings may combine classes); no methodology change is implied elsewhere in this plan.

**Input**: Forensic research specification from `specs/001-beautyq-doc-history-review/spec.md`.

**Type**: FORENSIC DOCUMENTATION-HISTORY RESEARCH PLAN. **This is NOT a software implementation plan.** No technology is selected, no data is modeled, no interface is contracted, and no product run is validated. The constitution and the clarified spec govern; stock Spec Kit Phase 0/Phase 1 artifact generation is intentionally suppressed (see §4).

---

## 1. Summary

This plan defines **how** the independent, DOC-ONLY forensic review of BeautyQ Search / Search Gen2 documentation will be executed — the phases, gates, ledgers, evidence discipline, and stop conditions — so that `/speckit.tasks` can later decompose it into an evidence-ordered task list. The review reconstructs, from committed documentary history only, what BeautyQ documentation said a good search architecture should optimize for at each stage, how those criteria changed, and whether the current documentary architecture still forms a coherent architecture-and-decision record.

**Planning-only boundary (this `/speckit.plan` invocation).** This command produces **only** `plan.md` (plus unavoidable Spec Kit bookkeeping). It performs **no historical research** and creates **no** research, coverage, blind, working, or report artifacts, and inspects **no** source/tests/build/CI/prior-review content. Specifically deferred and NOT done here: commit enumeration for analysis, documentary-diff synthesis, decision-thread derivation, epoch derivation, Gen1/Gen2/Elasticsearch/Qdrant/simplification evaluation, hypothesis testing, findings, ref-universe freeze, and any fetch/pull/branch/commit/ignore-rule/canonical-content change. The four analytical outputs and the ref freeze are created **only** during the research execution phase defined below.

**Method spine (order is load-bearing).** Preflight (baseline + isolation + local-ref freeze) → candidate documentary universe discovery → candidate reconciliation + chronological content inspection into `COVERAGE.tsv` + exclusion ledger → lineage + decision reconstruction → blind thread reconstruction / retrospective checks / open coding / epoch derivation → `BLIND.md` + **FREEZE (hard stop)** → [await user challenge pack] → adversarial challenge → full transfer/absence/counter-evidence audits → current-state reread → `REPORT.md` → mechanical + wording + scope-lint closeout → canonical-immutability verification.

---

## 2. Research Context

The subject is BeautyQ documentation as an evolving architecture / decision / ownership / rationale / migration / evaluation / governance record (constitution §3). The corpus is committed history reachable from the Git refs locally present at research start under the frozen baseline (FR-001/FR-051). Harness material (`.specify/**`, `.opencode/**`, `specs/**`, `.review-bundles/docs-history-speckit/**`) is never evidence (FR-009). `AGENTS.md` is handled under the dual-role rule (see §4 / §3 of the constitution / FR-011).

**Research Technical Context** (adapted from the stock template's software fields; all values are known — there are **no** `NEEDS CLARIFICATION` markers, consistent with `checklists/requirements.md`):

- **Evidence medium / version**: Git at commit `789e56674145a45c31e2e25d997c60b359431a48`, branch `develop`; committed Markdown/`docs/**` as the object of study.
- **Primary tools (read-only only)**: `git log`, `show`, `diff`, `grep`, `rev-list`, `ls-tree`, `cat-file`, `blame`, `show-ref`, `for-each-ref`, `branch --contains` (constitution §4).
- **Storage**: none — no database; ledgers are flat text files under `.review-bundles/docs-history-speckit/`.
- **Verification**: mechanical ledger reconciliation + arithmetic identities + wording/scope lint (constitution §25/§26); no software test suite.
- **Target platform**: N/A (research, not deployment).
- **Project type**: forensic documentation-history research harness.
- **Performance goals**: N/A. The relevant budget is context reliability, driving batch sizing (§10).
- **Constraints**: DOC-only (FR-006–011); read-only Git / no ref mutation (FR-004/FR-051); canonical immutability (FR-005); exactly four analytical outputs (FR-041).
- **Scale / scope**: unknown candidate-commit count until Phase 2 discovery; batching and epoch framing adapt to actual density (§10, §17). Never bounded by churn/message/date/epoch assumptions (FR-046/FR-048).

**Stock-approach applicability (documented explicitly):** technology-selection research → **not applicable** (this plan chooses no software stack); software data modeling → **not applicable** (entities are ledger records, §5); API/interface contracts → **not applicable** (no interface is built); product quickstart validation → **not applicable** (no end-to-end product run). Validation is the phase gates and mechanical closeout below, not a runnable app.

---

## 3. Constitution Check

*GATE: must pass before execution Phase 1 (preflight); re-checked after design and at closeout.* Every violation of a constitution MUST is a **hard failure** that halts the run (see §20) — it may not be justified away through Complexity Tracking. **Constitution-alignment note:** this plan tracks constitution **v1.0.2**, whose only change from 1.0.1 is the §11 provenance-unit clarification (per material evidence anchor / per atomic material analytical statement; composite findings may combine classes; severity/confidence are not substitutes for provenance). That patch adds no class, removes no class, changes no weighting, and implies no other methodology change anywhere in this plan; the gates below are unchanged in substance.

| # | Gate (constitution MUST) | Governing refs | Planned enforcement | Status |
|---|---|---|---|---|
| 1 | Frozen baseline verified & HEAD match | §1; FR-001–003 | Preflight records `git rev-parse HEAD`, branch, root, start ts; mismatch → fail closed, report before any research | **PASS (planning)** — planning-time read-only check shows HEAD = `789e566…48`, branch `develop`; ref freeze still deferred to execution |
| 2 | Previous-review isolation precondition | §2; FR-012–015 | Before BLIND research begins, previous BeautyQ documentation-history **Run-A** outputs are physically absent from the repository working tree **and** from every other location this review searches; presence → fail closed, report the contamination risk, and the **user** isolates before research proceeds. Excluding the directory from search, ignore rules, moving/deleting, or reading Run-A content to judge it harmless do **not** satisfy the gate; on PASS record only `Previous-review isolation precondition: PASS` | **PASS (planning)** — enforcement defined for Phase 1 |
| 3 | DOC-only evidence boundary | §3; FR-006–011 | Only documentary content + commit metadata/message; mixed commits via docs portion only | **PASS** — plan mandates it |
| 4 | Read-only Git only | §4; FR-004 | Only the §4 allowed read-only commands | **PASS** |
| 5 | No branch create/switch/state change | §4; FR-004 | Run on existing `develop` checkout; forbids checkout/switch/reset/rebase/merge/commit/stash/clean | **PASS** |
| 6 | Canonical content immutable | §5; FR-005 / SC-010 | Writes limited to harness; closeout runs `git diff -- README.md AGENTS.md docs` (must be empty) | **PASS** |
| 7 | Exact candidate denominator | FR-046–048 / SC-001 | Candidate universe is a discovery set; every candidate resolves to exactly one accounting outcome | **PASS** |
| 8 | Four allowed dispositions only | §6–§7; FR-020 | `COVERAGE.tsv` ∈ {deep, semantic-scan, clerical, duplicate}; no 5th | **PASS** |
| 9 | Exclusion ≠ fifth disposition | FR-020/047; §23 | Inspected-unrelated → WORKING Exclusion Ledger; never `clerical` | **PASS** |
| 10 | BLIND before hypothesis pack | §9–§10; FR-039 / SC-007 | Threads/epochs/findings derived evidence-first; pack never in blind input | **PASS** |
| 11 | Mandatory post-BLIND challenge (Run B) | §27; FR-040 | Hard stop at FREEZE; Story 4/REPORT gated on challenge performed | **PASS** |
| 12 | Provenance per anchor/claim | §11 (v1.0.2 unit)–§12; FR-026/027 / SC-008 | Six classes CD/RD/ND/CM/HI/RJ per material anchor & per atomic material statement; composite findings may combine classes; High/Critical never only CM/RD | **PASS** |
| 13 | Transfer audits | §17; FR-037 / SC-004 | Every major retirement/relocation audited along the FR-037 chain | **PASS** |
| 14 | Absence-claim ledger | §13; FR-028 / SC-005 | Material absence claims get A-xxx records in the authoritative FR-028 execution schema or are weakened | **PASS** |
| 15 | Counter-evidence for strong findings | §18; FR-029 / SC-006 | High/Critical require deliberate weakening search + alternative interpretation | **PASS** |
| 16 | Exactly four analytical outputs | §23; FR-041 | Only COVERAGE.tsv / BLIND.md / WORKING.md / REPORT.md; no raw dumps / 5th artifact | **PASS** |
| 17 | Exact arithmetic, recomputed mechanically | §8; FR-025 / SC-003 | Both identities recomputed from source; identical across surfaces | **PASS** |
| 18 | Final scope lint + wording gate | §25–§26; FR-045 / SC-009 | Absolutes watchlist verified/weakened; no implementation claims | **PASS** |
| 19 | Threads precede epochs; threads precede causality | §14–§15; FR-030–032 | Cross-thread causal edges only on explicit documentary link else `HI` | **PASS** |
| 20 | Ref-universe confinement, no remote claim | FR-018/051 / SC-013 | Freeze locally present refs; no fetch/pull; no remote-history claim | **PASS** |
| 21 | Severity ≠ confidence; not churn/size-derived | FR-049/050 / SC-014 | Independent scales fixed before findings; Critical per the four objective FR-049 conditions (else max High) | **PASS** |
| 22 | `WORKING` = evidence memory, not diary; compressed each batch | §24; FR-042 | Structure §… (WORKING) enforced; compress after substantial batch | **PASS** |

**Post-design re-check:** generating this plan created no findings, no forbidden artifact, and touched no canonical file, so all gates remain **PASS**; no Complexity-Tracking justification is required (no violation exists). Any later FAIL during execution is a stop condition, not a justification.

---

## 4. Artifact Strategy

**Only `plan.md` is written by this command.** Stock Spec Kit Phase 0/Phase 1 design artifacts are intentionally **not** generated, and their template concepts are folded into this plan and the later four analytical outputs. No placeholder / "N/A" files are created.

| Stock Spec Kit artifact | Created? | Reason |
|---|---|---|
| `research.md` | **No** | The actual historical research **is** the feature execution, captured in the four analytical outputs; a separate pre-implementation research doc would duplicate `WORKING.md`/`BLIND.md`. |
| `data-model.md` | **No** | Research entities and their schema already exist in `spec.md` (Key Entities) and are operationalized by this plan / `tasks.md`; there is no software data model. |
| `contracts/**` | **No** | There is no API or interface-implementation contract to define. |
| `quickstart.md` | **No** | This is not a software feature needing an end-to-end product run guide; execution gates live in this plan, `tasks.md`, and the custom checklist. |

**The four analytical outputs (execution phase only, FR-041; constitution §23).** First created/populated during execution under `.review-bundles/docs-history-speckit/`: `COVERAGE.tsv`, `BLIND.md`, `WORKING.md`, `REPORT.md`. The analytical output directory may pre-exist as an empty harness directory; the four analytical output files are first created/populated only during research execution. Nothing else may be produced in that class — no raw Git-history dumps, diff archives, command transcripts, ref dumps, standalone exclusion ledgers, scratch Markdown, or alternative reports.

**Harness file layout for this feature (adapted — no Phase 0/1 files):**

```text
specs/001-beautyq-doc-history-review/
├── spec.md                     # research contract (unchanged by this command)
├── plan.md                     # THIS FILE — research execution plan (/speckit.plan output)
├── checklists/
│   ├── requirements.md         # built-in spec-quality checklist (maintained by /speckit.specify + /speckit.clarify)
│   └── research-quality.md     # custom pre-/speckit.tasks research-method gate (reviewer-owned markers)
└── tasks.md                    # LATER, by /speckit.tasks — NOT created here

.review-bundles/docs-history-speckit/    # may pre-exist as an empty harness directory; the four output FILES are first created/populated only during research execution
├── COVERAGE.tsv                # mechanical per-relevant-commit ledger (execution)
├── WORKING.md                  # compressed evidence memory + exclusion/ref/absence/challenge ledgers (execution)
├── BLIND.md                    # frozen independent first-pass synthesis (execution)
└── REPORT.md                   # final auditable report, 20 fixed sections (execution, after challenge)
```

**Dual-role `AGENTS.md` (constitution §3, FR-011).** (1) The current checked-out `AGENTS.md` is an **operational** instruction source governing safe agent behavior; its mere presence is not evidence. (2) Committed historical `AGENTS.md` versions are **eligible documentary evidence only** when materially relevant to BeautyQ Search / Search Gen2 architecture or documentary governance (ownership, source-of-truth, scope control, coordinator/reviewer duties, artifact ownership, evidence/reproducibility, migration/cutover process with permanent effect, documentation governance, architecture-by-process). Unrelated generic repository guidance is excluded (→ Exclusion Ledger, §8). The two roles must never be conflated.

---

## 5. Research Entities / Ledgers

The "data model" of this research is its evidence ledger schema (defined in `spec.md` §Key Entities; software data modeling is N/A). Execution maintains these structures, all inside the four allowed outputs:

- **Candidate Documentary Commit** — Phase-2 discovery member; NOT yet proof of relevance; after content inspection resolves to exactly one outcome (§8).
- **Exclusion Record** (in `WORKING` Candidate Corpus Exclusion Ledger) — commit; date; documentary path(s); exclusion reason; sufficient-inspection level. Never a COVERAGE disposition; never a 5th artifact.
- **Documentary Commit / COVERAGE row** — `commit`·`date`·`paths`·`disposition`·`reason`·`decision_threads`·`evidence_ids` (fixed schema, §6/§7).
- **Document Version / Document Lineage** — content state of one file at one commit; the creation/rename/copy/replacement/supersession/consolidation/deletion/retirement chain (§11).
- **Ref Inventory** (in `WORKING`) — compact read-only list of locally present refs at start + each ref's unique-documentary-substance assessment (§6).
- **Evidence Anchor** `E-0001…` — unique ID, exactly one provenance class (materiality per FR-052), exact commit/path/section, concise paraphrase (§9).
- **Decision** `D-001…` — problem, decision/rule, rationale, normative strength, temporary/permanent framing, owner, later change (§12).
- **Decision Thread** `T-01…` — independently derived separable dimension with its evolution (§13).
- **Historical Transition** — point where a thread's decision/value/status/ownership/terminology changes.
- **Transfer Audit** — the FR-037 chain for one major retirement/relocation (§16).
- **Absence Claim** `A-001…` — negative assertion + its authoritative FR-028 ten-field search record (§16).
- **Finding** — FR-049 severity (Critical only when all four FR-049 conditions hold) + FR-050 confidence (independent), composite evidence anchors, counter-evidence, retrospective classification (§18).

---

## 6. Ref-Universe Strategy

- **Mandatory execution preflight (Phase 1, before any candidate discovery or semantic historical research)** verifies and records, in order: (1) frozen HEAD (`git rev-parse HEAD`); (2) expected branch / branch record; (3) repository root; (4) research-start timestamp; (5) **physical absence of the previous independent BeautyQ documentation-history review's (Run-A) outputs from the repository working tree**; (6) **absence of those Run-A outputs from every other location this review will search**; (7) **the locally present Git-ref universe frozen** — historical documentary path/candidate discovery begins ONLY after gates (1)–(6) pass, per the invariant `baseline → Run-A isolation → local-ref freeze → historical documentary traversal`; local-ref enumeration is never permitted before the Run-A isolation gate passes merely because ref names are mechanical metadata. If HEAD ≠ `789e566…48`, or branch ≠ `develop`, the run **fails closed** and no research begins (FR-002/003). The planning-time HEAD/branch confirmation above does not substitute for the execution-start preflight. When condition (5)/(6) is satisfied the reviewer records ONLY: `Previous-review isolation precondition: PASS` — never Run-A names, contents, conclusions, or summaries. If PASS cannot be established without reading Run-A content, stop and ask the user to resolve isolation. Physical isolation is the **user's** responsibility: the reviewer MUST NOT resolve presence by excluding the directory from search, adding ignore rules, moving/renaming or deleting files, or reading Run-A material to determine whether it is harmless (constitution §2, FR-012–015).
- **Evidence retrieval is committed-Git-only, with operational reads distinguished:** historical BeautyQ documentary evidence MUST be retrieved exclusively via read-only Git plumbing on **committed** documentary objects (`git log -- <path>`, `git show`, `git cat-file`, `git ls-tree`, `git rev-list`); this minimizes accidental working-tree contamination. This is an evidence-retrieval rule, not a ban on operational reads: the reviewer may read current operational review-harness instructions where required — the current `AGENTS.md` in its operational role, constitution, spec, plan, and tasks/checklists — and such operational reads are **not** historical evidence. Two categories must not be collapsed: (1) **Run-A BeautyQ documentation-history outputs** — MUST be physically absent from the repository working tree (and from every other searched location) before BLIND research begins; presence anywhere → fail closed, report the contamination risk, and require user isolation before proceeding. (2) **Other unrelated ignored scratch material** (review bundles, patches, logs, source/test/build dumps that are not Run-A outputs) — MAY physically exist and does NOT itself violate the Run-A precondition, but it remains outside the historical corpus: it must never be searched or read as documentary evidence, source/test/build material remains forbidden by the DOC-only boundary, and its mere existence must never become historical evidence.
- **Freeze** the locally present ref universe at research start — preflight step (7), executed only after the baseline and Run-A isolation gates (1)–(6) have passed — via read-only `git show-ref` / `git for-each-ref` / `git rev-list`. **No** fetch, **no** pull, **no** remote contact to expand history, **no** branch/tag/stash create or mutate. Non-primary refs (local branches, tags, reference/stash snapshots) are checked read-only for unique documentary substance.
- The **compact** ref inventory + unique-substance assessment live in `WORKING.md`; no raw ref-dump artifact is created (FR-041/FR-051). "Entire relevant history" is bounded to what these locally present refs contain at research start; the run must never claim to have audited remote-only history.
- The universe is **frozen** at start and must not be expanded mid-run.

---

## 7. Candidate-Universe Strategy

Candidate discovery is a **distinct phase (Phase 2) before** semantic historical synthesis, and its purpose is to establish an **auditable denominator** (FR-046).

- **Include** committed/historical documentary material relevant or potentially relevant to BeautyQ Search / Search Gen2: `README.md`, `AGENTS.md`, relevant `docs/**`, deleted documentary paths, renamed paths, copied/replaced paths, superseded documents, paths visible only historically, and documentary content on locally available non-primary refs.
- **Must NOT** bound discovery by: line churn; commit-message keyword; current paths only; Gen1/Gen2 labels; known dates; assumed architectural epochs; the prior review; or expected findings (FR-046/FR-048).
- The candidate universe is a **discovery set**; membership does **not** imply relevance. Discovery output (candidate count + path/ref enumeration method) is recorded compactly in `WORKING`; substantive relevance is decided only in Phase 3 after content inspection.

---

## 8. Coverage / Exclusion Accounting

Every candidate documentary commit resolves, **after content inspection**, to exactly one outcome (FR-047):

- **Outcome A — relevant** → exactly one `COVERAGE.tsv` row with disposition ∈ {`deep`, `semantic-scan`, `clerical`, `duplicate`}.
- **Outcome B — excluded after inspection** → a `WORKING.md → Candidate Corpus Exclusion Ledger` record (fields: commit; date; documentary path(s); exclusion reason; sufficient-inspection level). Examples: unrelated upstream documentation; generic repository guidance; irrelevant `AGENTS.md` changes; unrelated docs.

Rules: **exclusion is NOT a fifth disposition**; `clerical` **never** means "unrelated" (it is reserved for already-relevant, mechanically/non-semantically editorial changes); when relevance is genuinely uncertain, **err toward relevant inclusion** with a content-based disposition (FR-048). A candidate may not be excluded merely because its message looks unrelated, its diff is small/large, its path looks generic, its date is early/late, it predates Gen2, or it is post-cutover (FR-048, edge cases 20).

**Identity that must hold (both, exactly, recomputed mechanically):**

```
(1) candidate_documentary_commits = relevant_documentary_commits + excluded_after_inspection
(2) relevant_documentary_commits  = deep + semantic-scan + clerical + duplicate   (constitution §8 authoritative)
```

**Disposition definitions (constitution §7 / FR-021–024):** `deep` = material (or possibly material) effect on architecture/ownership/source-truth/normative status/etc., with a sufficient before/after document read; `semantic-scan` = after reading the diff, semantic context/status/wording that needs no full deep read (message/numstat alone is never sufficient); `clerical` = relevant-but-mechanically-editorial with no architectural/ownership/rationale/normative/terminology consequence; `duplicate` = documentary substance proven equivalent elsewhere in reachable history (rebase/cherry-pick/duplicate lineage/stash snapshot), recording the equivalent commit — never inferred from branch names alone.

---

## 9. Evidence / Provenance Strategy

Stable evidence IDs `E-0001`, `E-0002`, …; one row in the `WORKING` Evidence Ledger per material anchor ("material" per the authoritative spec FR-052 definition, not re-defined here). Each anchor records: evidence ID; provenance class; commit; date; path; section/heading where available; concise paraphrase (not large copied quotations); and thread association once known.

Provenance uses **exactly** the six constitution classes and is asserted **per material evidence anchor and per atomic material analytical statement** — the constitution §11 (v1.0.2) unit — so a complex finding may combine `CD` anchors, an `ND` anchor, an `HI` reconstruction, and an `RJ` judgment and must not be forced into one label (FR-026): `CD` contemporaneous documentary; `RD` retrospective documentary; `ND` current normative documentary; `CM` commit metadata/message (navigation only, weaker); `HI` historical inference; `RJ` reviewer judgment. Hard weighting: `RD`≠`CD`, `CM`≠`CD`, `HI` is not documentary fact, `RJ` is not historical fact; a High/Critical finding must never rest only on `CM`/`RD` (constitution §11). Severity and confidence are not substitutes for provenance.

---

## 10. Chronological Batch Strategy

After candidate discovery, inspect candidates **chronologically** in balanced batches so that `WORKING` can be compressed before context becomes unreliable.

- **Batch size** is derived from actual candidate volume (a reasonable default is ~25–50 candidate commits per batch, adapted to documentary density). **Batch boundaries are operational only and never historical epochs**; a boundary must not imply architectural significance (§17 owns epoch derivation).
- **Per candidate, in order:** (1) inspect documentary content sufficiently; (2) choose relevant vs excluded; (3) if relevant, assign disposition; (4) identify material documentary changes; (5) promote to `deep` when required by FR-021; (6) capture evidence anchors for material decisions; (7) update lineage; (8) update `WORKING`; (9) compress `WORKING` before the next substantial batch (preserve stable IDs / evidence anchors / contradictions; drop superseded guesses and discovery noise — constitution §24).
- Never classify from message, filename, date, churn, or milestone number — those may guide navigation only.
- **`deep`-review mandatory promotion triggers** when a change may materially affect: architectural problem / goal; user-product goal; architecture value; requirement; non-goal; ownership; canonical/source-of-truth claim; derived-vs-authoritative status; backend role; result ownership; lifecycle; cleanup/retention; migration/cutover state; evaluation/proof role; artifact ownership; reproducibility policy; reuse/generalization claim; temporary/permanent framing; normative force; historical rationale; current/proposed status; or a coordinator/process rule with architectural or documentary-governance effect. Also on **modal transitions**: may→should, should→must, optional→default, default→required, experimental→supported, temporary→current, goal→deferred, goal→non-goal, proposal→normative, allowed→forbidden (constitution §16 / FR-038).

---

## 11. Lineage Strategy

A dedicated lineage reconstruction activity (Phase 4) tracks creation · rename · copy · replacement · supersession · consolidation · deletion · retirement across document versions, using read-only Git documentary history. For important lineages record: original role; historical owners; successor; current status. **Filename similarity alone is not lineage proof** (constitution §7 `duplicate` rule, FR-037, edge cases 4–7). Deleted/renamed/superseded documents must be represented with lineage and disposition (Story 1 scenarios 3, 5, 6).

---

## 12. Decision / Thread Strategy

- **Decision ledger first (Phase 4), before threads.** Assign stable IDs `D-001…`; each material decision records: evidence anchor(s); date; documentary owner; problem addressed; decision/rule; rationale; normative strength; temporary/permanent framing; and a "later known change" field left blank until encountered. **Do not write final findings in this phase.**
- **Blind thread derivation (Phase 5), after substantial chronological inspection.** Derive threads from evidence; **do not predefine BeautyQ-specific thread names.** A thread `T-01…` records: neutral evidence-derived name; initial problem; first documented decision; sequence of material changes; rationale evolution; owner evolution; requirement-strength evolution; temporary/permanent framing; current documentary state; unresolved contradictions.
- **Do not merge threads merely because they concern the same component.** A component may hold separate threads for, e.g., existence / permitted role / result ownership / lifecycle / evaluation / product justification — **methodology examples only, not predetermined BeautyQ threads** (constitution §14, FR-030).

---

## 13. Blind Analysis Strategy

Blind analysis (Phase 5) precedes and is independent of any user-supplied hypothesis pack (constitution §9, FR-039). It comprises: (a) thread reconstruction (§12); (b) **cross-thread causality rule** (hard gate before BLIND) — same-thread causality may be inferred with evidence and marked `HI` when appropriate; **cross-thread causality may be asserted only where documentary evidence explicitly links the threads**, otherwise record correlation/sequence as `HI` and do not state causality (constitution §14, FR-031); (c) **retrospective-check pass** — for each significant later/current statement describing earlier intent, classify it `RD`, search contemporaneous material, and record one of {accurately preserved, simplified but fair, stronger than evidence, changed interpretation, possible retcon, unverifiable} — never accept current docs as authority over earlier intent (constitution §12, FR-027); (d) **open-coding pass** — code freely from evidence (candidate categories such as architectural values, recurring problems, ownership patterns, documentary-governance patterns, product-goal changes, temporary/permanent tension, proof/evaluation patterns, migration residue, simplification patterns, successful corrections — **categories, not expected findings**), remaining free to conclude there is no meaningful drift / no reversal / no source-truth problem / no over-expansion, or that existing patterns are justified; and (e) **epoch derivation** — only after threads (§14 of constitution / this §17 note below).

**Epoch derivation:** historical periods may be derived **only after** threads, justified by actual change in dominant problem / architectural thesis / values / ownership / source truth / proof burden / migration state / documentary organization / product goal; **never** by milestone numbering, commit count, file count, or batching convenience; boundaries recorded as `HI`/`RJ` unless named contemporaneously (constitution §15, FR-032).

---

## 14. BLIND FREEZE Gate

When blind reconstruction is complete, create `.review-bundles/docs-history-speckit/BLIND.md` with sections **A. Freeze Metadata** (frozen HEAD; branch; repository root; research-start ts; freeze ts; local ref-universe summary; isolation-precondition result; **research history range discovered** — the earliest and latest relevant documentary date/commit at freeze, derived only from locally present refs; never inferred from remote history) · **B. Coverage at Freeze** (exact candidate/excluded/relevant + deep/semantic-scan/clerical/duplicate; show **both** identities) · **C. Documentary Lineages** · **D. Decision Threads** · **E. Candidate Historical Periods** · **F. Candidate Architecture Values** (durable + changing) · **G. Candidate Findings** (no hypothesis influence) · **H. Candidate Positive Findings** (same discipline) · **I. Unresolved Questions & Uncertainty** (do not hide ambiguity) · **J. Independence Declaration** — the exact substance: *"No user-provided BeautyQ historical hypothesis pack was seen, requested, reconstructed, or used during this blind phase."*

The A–J structure is exhaustive — no eleventh analytical section is added; BLIND remains a compressed independent synthesis, not a wholesale duplication of `WORKING.md`. **Provenance requirement applying across C–H:** every material evidence anchor retains its E-ID and provenance class, and every atomic material analytical statement makes its evidentiary posture explicit according to constitution §11 v1.0.2 / FR-026. Thus BLIND contains evidence provenance rather than merely referring to an external ledger; a compact provenance legend may appear in BLIND, but a legend alone is not sufficient if the material anchors/statements themselves lose their provenance.

Before writing BLIND, the COVERAGE lifecycle rule (§7 of the directive → see §6/§8 here) must be satisfied: material `decision_threads`/`evidence_ids` that were temporarily blank/unresolved during candidate inspection must be finalized — relevant rows carry final dispositions, material rows link evidence anchors, and thread references reflect the independently derived thread model.

**FREEZE = HARD STOP.** After `BLIND.md` is written, **STOP execution**; do not proceed to challenge; do not finalize `REPORT.md`; record in `WORKING`: `Challenge phase state: awaiting pack`; **do not request the pack**. `BLIND.md` is thereafter immutable except clearly appended `FACTUAL CORRECTION` notes (constitution §10). The execution command returns to the user here — this is a user-interaction boundary, not a completion — using the authoritative spec FR-053 `PAUSED — awaiting challenge pack` reporting contract (which must not request or preview the pack).

**BLIND is a freeze snapshot, not the mutable final corpus ledger.** Its original A–J content remains sealed and §B stays the exact `Coverage at Freeze` snapshot; freeze counts are never silently replaced by final counts. Final corpus arithmetic is owned by final `COVERAGE.tsv` + final `WORKING.md` + `REPORT.md` + the completion response, while BLIND's freeze snapshot is audited separately for internal correctness at freeze. If permitted post-BLIND work inside the frozen local-ref universe legitimately surfaces a previously absent FR-046 candidate, the controlled append/reconcile handling is owned by the tasks.md **Post-BLIND Candidate Discovery Rule**; a post-freeze material factual change to BLIND itself is recorded ONLY via clearly labelled `FACTUAL CORRECTION` appends that never masquerade as the original blind narrative.

---

## 15. Post-Freeze Challenge Strategy

**Described here; NOT executed now.** When the user later supplies the pack: record `WORKING` state `received`; do not alter the BLIND narrative (append only genuinely required labelled `FACTUAL CORRECTION` notes); test each supplied hypothesis against the existing corpus while **actively seeking disconfirming evidence**; classify each hypothesis using the **user's supplied classification system**; record results in `WORKING`; then set state `challenge performed`. The pack must not overwrite independently derived threads. Until state = `challenge performed`, Story 4 / `REPORT.md` cannot begin (FR-040, Story 3 scenario 5). If asked to continue while the pack is un-supplied, report the run as **paused awaiting pack**, never complete.

---

## 16. Transfer / Absence / Counter-Evidence Strategy

- **Full transfer audits (Phase 8, after challenge):** identify major transfer candidates — plan retirement, roadmap retirement, handoff retirement, ADR retirement, spec replacement, source-truth relocation, generated-vs-handwritten ownership move, migration-document retirement, audit/proof-document retirement. Before BLIND freeze, seed enough `WORKING` transfer records to support blind interpretation; **complete** the judgments only after the challenge phase. Per transfer determine along the FR-037 chain: `old owner → transition → claimed destination → decision preserved? → rationale preserved? → alternatives preserved/lost? → history/status intentionally discarded? → orphaned information? → judgment`. Deletion speed and replacement existence are **not** proof of quality; rule preservation and rationale preservation are assessed separately; intentional status/history discard is recorded separately from accidental loss.
- **Absence-claim pass (Phase 8):** only **material** candidate absence claims (materiality per spec FR-052) need exhaustive search records. Use the authoritative FR-028 absence-record schema (stable `A-001…` claim ID plus the ten required FR-028 fields); this plan deliberately maintains no competing field list. If corpus-wide absence is not established, weaken the final wording to search-bounded language (e.g., "No explanation was found in the reviewed documentary corpus after searching X/Y/Z") (constitution §13, FR-028).
- **Counter-evidence pass (Phase 8):** before accepting any High/Critical finding — find strongest supporting evidence; deliberately search for weakening evidence; formulate the strongest alternative interpretation; reassess severity and confidence; remove the finding if the alternative explanation wins. Perform this even when a finding seems obvious (constitution §18, FR-029). Fewer strong findings are preferred over volume.

---

## 17. Current-State Reread

**Only after historical reconstruction and the challenge phase (Phase 9)**, reread current normative documentary material and classify each item as: current normative architecture / accepted limit / active plan / proposal / simplification proposal / historical explanation. Do not let current docs rewrite earlier intent (constitution §21, FR-036). Determine whether a current reader needs hidden historical knowledge to understand current architecture, and keep complexity/drift categories distinct (§20) while doing so.

---

## 18. Report Strategy

`REPORT.md` (Phase 10) may be created **only** after: Story 1 complete; Story 2 complete; BLIND frozen; challenge pack supplied; Story 3 complete; transfer audits complete; absence audits complete; counter-evidence complete; current-state reread complete. It uses **exactly the 20-section order of FR-043** (1 Executive Summary; 2 Current Documentary Architecture; 3 Historical Development; 4 Decision Threads; 5 Architecture Value Evolution; 6 Durable Ideas; 7 Reversals, Narrowings, and Abandoned Goals; 8 Source-of-Truth and Ownership History; 9 Scope Drift; 10 Focus Drift; 11 Value Drift; 12 Migration and Temporary-Machinery Audit; 13 Proof/Evaluation/Governance Expansion; 14 Artifact and Evidence Architecture; 15 Documentation Architecture; 16 Rationale Preservation and Retrospective Accuracy; 17 Findings; 18 What BeautyQ Got Right; 19 Documentation-Only Recommendations; 20 Coverage Appendix). Historical epochs inside are derived findings, never predetermined. **Generate the report from ledgers/evidence, not from remembered narrative.** Severity uses the fixed FR-049 scale (Critical/High/Medium/Low), where **Critical requires ALL FOUR objective FR-049 eligibility conditions** (a fundamental current decision / ownership / source-of-truth / current-rationale subject; a documentary defect that prevents reliable interpretation of that current decision from the documentary record; a systemic rather than local/stale/duplicated/awkward/incomplete consequence; and High would materially understate that the current decision record cannot be reliably interpreted) — if any condition fails, the maximum severity is High, and Critical's natural rarity is only a consequence of those demanding conditions, never an independent reviewer preference; confidence uses the independent FR-050 scale (High/Medium/Low); severity must never derive from line count, module/project count, complexity amount, proof-machinery size, or reviewer preference; Low-confidence claims should not normally become headline findings.

**`WORKING.md` structure** (compressed evidence memory, not a diary): A. Run Metadata & Ref Inventory · B. Candidate Corpus Accounting (incl. **Candidate Corpus Exclusion Ledger**) · C. Coverage Summary · D. Document Lineage Ledger · E. Evidence Ledger · F. Decision Ledger · G. Decision Threads · H. Retrospective Claim Audit · I. Transfer Audits · J. Absence Claims · K. Blind Open-Coding Notes · L. Challenge Phase (state) · M. Counter-Evidence · N. Current-State Reread · O. Final Mechanical Checks. Compress after each substantial batch; preserve stable IDs, evidence anchors, and contradictions.

---

## 19. Mechanical Closeout

Before accepting `REPORT.md`, **recompute mechanically** from source ledgers: candidate-commit total; excluded total; relevant total; deep / semantic-scan / clerical / duplicate counts; earliest & latest relevant dates and the exact date span; historical documentary path count; finding IDs / severities / confidences. Verify **both** identities (§8) exactly, then compare every repeated count across `COVERAGE.tsv`, `WORKING.md`, `REPORT.md`, and the final completion response — **no approximation** (all such values are **final** counts after every allowed pre-final addition/reclassification; BLIND §B freeze counts are a historical snapshot audited separately at freeze and are not forced to equal final counts — WORKING records any `freeze → final corpus delta`). Run the **absolute-wording closeout** (search REPORT for always, never, only, entire, all, unbroken, unchanged, clean, sole, "no document", completely, every; each occurrence either gets documentary/search support or is weakened) and the **DOC-only scope lint** (sentence-by-sentence rewrite of any assertion of actual runtime / code ownership / tests / CI / dependencies / backend behavior into documentary wording, e.g. "The technical specification assigns X ownership of this responsibility," not "X is the runtime owner"). Finally run the **canonical immutability gate**: `git diff -- README.md AGENTS.md docs` **must be empty**; if not, do not claim completion — report the violation and do **not** auto-revert (constitution §5/§25/§26, FR-005/FR-045, SC-010).

---

## 20. Failure / Pause Conditions

**STOP** (fail closed) if: HEAD mismatch or wrong branch; canonical files modified; previous BeautyQ documentation-history **Run-A** outputs are present anywhere in the repository working tree or in another location searched by this run (stop before BLIND; report the contamination risk; physical isolation is the user's action — do not resolve by search exclusion, ignore rules, moving/deleting, or reading Run-A content); required local Git history unavailable; a step would require fetch/pull; source/test/build inspection becomes necessary; candidate accounting cannot reconcile; coverage arithmetic cannot reconcile; BLIND would need hypothesis-pack input; challenge pack missing after BLIND freeze; or `REPORT.md` would require unresolved High/Critical evidence gaps. Other unrelated ignored scratch material does **not** trigger this condition merely by existing; its read/search prohibition (§6) remains intact. Post-BLIND candidate discovery inside the already frozen locally-present-ref universe is **not** an automatic FAIL: an FR-046 candidate incorporable within the frozen DOC-only universe is appended and reconciled under the tasks.md Post-BLIND Candidate Discovery Rule; FR-053 FAIL applies only when that controlled reconciliation cannot be completed safely — e.g., candidate accounting cannot reconcile, required evidence would need forbidden source/test/build/CI inspection, the commit lies outside the frozen ref universe and would require fetch/pull, Run-A contamination occurs, a required material audit cannot be brought to a valid state before completion, or canonical immutability fails. **Do not fake-green the review.** The only completion state is `COMPLETE`; the valid non-complete states are `FAIL` and `PAUSED — awaiting challenge pack`, and a paused state (esp. awaiting the challenge pack) is valid and preferable to invalid completion. Every FAIL and every BLIND-FREEZE pause MUST follow the authoritative spec **FR-053** execution-state reporting contract — the exact required fields plus the explicit no-completion statement, with Run-A contamination reports revealing no Run-A content or conclusions; this plan maintains no competing reporting-field list (constitution §27 completion contract, FR-003/013/040/053).

---

## 21. Task-Decomposition Guidance (for later `/speckit.tasks`)

This plan is precise enough to be decomposed into tasks in this **evidence-ordered** sequence (organize execution by evidence/phase dependency, **not** as software user stories; Research Story IDs 1–4 may be cited for traceability only):

- **Phase 1 — Research preflight (before candidate discovery):** (1) frozen HEAD; (2) expected branch / branch record; (3) repository root; (4) research-start timestamp; (5) physical absence of previous BeautyQ documentation-history Run-A outputs from the repository working tree; (6) absence of those Run-A outputs from every other location this review will search; (7) local-ref universe freeze + compact inventory. Historical documentary path/candidate discovery (Phase 2) begins ONLY after all seven gates pass, per the invariant `baseline → Run-A isolation → local-ref freeze → historical documentary traversal`; tasks T001 → T002 → T003 → T004 → T005 remain the authoritative executable decomposition, and local-ref enumeration is never permitted before the Run-A isolation gate merely because ref names are mechanical metadata. Record only `Previous-review isolation precondition: PASS` when satisfied; if PASS cannot be established without reading Run-A content, stop and ask the user to resolve isolation (§6).
- **Phase 2 — Candidate universe:** discover the complete candidate documentary set (§7).
- **Phase 3 — Candidate reconciliation + chronological content inspection:** create `COVERAGE.tsv` + `WORKING` and inspect every candidate (§8, §10). **May be split into several chronological-batch tasks once the candidate count is known.**
- **Phase 4 — Lineage + decision reconstruction:** no final narrative yet (§11, §12).
- **Phase 5 — Blind threads + retrospective checks + open coding + epoch derivation** (§12, §13).
- **Phase 6 — BLIND FREEZE:** write + validate `BLIND.md`, then **STOP** (§14).
- **Phase 7 — Adversarial challenge:** blocked until the user supplies the pack (§15).
- **Phase 8 — Transfer / absence / counter-evidence audits** (§16).
- **Phase 9 — Current-state reread** (§17).
- **Phase 10 — REPORT** (§18).
- **Phase 11 — Mechanical closeout** (§19).

**Parallelism (use very conservatively):** historical interpretation is stateful, so prefer deterministic sequential evidence accumulation. Parallelism is acceptable **only** where results cannot silently diverge — e.g., mechanical arithmetic re-checks; final non-mutating consistency scans. Phase-1 preflight is strictly sequential: read-only ref enumeration is never parallelized with, or permitted before, the Run-A isolation gate (§6; tasks T001–T005). **Never parallelize** adjacent chronological batches whose interpretation depends on evolving prior lineages, decision-thread reconstruction, BLIND synthesis, per-hypothesis challenge evaluation, or final report synthesis.

---

## Plan Self-Validation (against the executing command's required checks)

- No historical research performed; no BeautyQ finding or epoch invented.
- No `research.md`, `data-model.md`, `contracts/`, or `quickstart.md` created; no COVERAGE/BLIND/WORKING/REPORT created; no source/tests/build/CI inspected; no previous review accessed; no branch created/switched; no fetch/pull; no canonical file changed.
- Candidate denominator explicit; exclusion is not a fifth disposition; both arithmetic identities explicit; BLIND FREEZE is a real stop boundary; challenge phase mandatory for Run B; REPORT impossible before challenge completion; provenance follows the constitution §11 v1.0.2 unit (per material anchor / per atomic material statement; composite findings may combine classes); severity and confidence remain independent with the four objective FR-049 Critical conditions authoritative; materiality, the absence-record schema, and the FAIL/PAUSED reporting contract defer to the single authoritative spec definitions (FR-052 / FR-028 / FR-053) with no competing plan-level lists; the four-analytical-output limit is intact; Constitution Check has no unresolved violation.
- Canonical immutability verified at plan completion: `git diff -- README.md AGENTS.md docs` empty (this command wrote only `plan.md`).
