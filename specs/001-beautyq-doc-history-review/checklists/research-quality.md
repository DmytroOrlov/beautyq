# Pre-Execution Research-Contract Quality Gate Checklist: BeautyQ Search / Search Gen2 Documentation History Review

**Purpose**: Validate that the governing research artifacts — `.specify/memory/constitution.md` v1.0.2, `specs/001-beautyq-doc-history-review/spec.md`, and `specs/001-beautyq-doc-history-review/plan.md` — define the future forensic DOC-ONLY documentation-history research clearly, completely, consistently, measurably, and auditably enough for `/speckit.tasks` to decompose without silently weakening the method.
**Created**: 2026-09-04
**Feature**: [spec.md](../spec.md) · [plan.md](../plan.md) · [constitution](../../../.specify/memory/constitution.md)

**Depth**: formal / high rigor · **Audience**: architecture coordinator / independent reviewer · **Timing**: pre-`/speckit.tasks`
**Note**: This is a *requirements-quality* gate on the written research contract. It is NOT an implementation test, NOT a Git-history execution checklist, NOT a code review, NOT a test of whether historical findings are true, and NOT a test of whether research has already been performed. Every item is answerable later by inspecting the constitution/spec/plan text only. The absence of `tasks.md` is expected at this stage and is not a defect; task coverage is checked later by `/speckit.analyze`.

**Review Ownership**: This is a reviewer-owned requirements-quality artifact. Mark an item `[x]` only when the reviewer determines the requirement-quality criterion is satisfied by the written contract.
**Marker Semantics**: `[x]` means the criterion was reviewed and satisfied for requirements quality. It does NOT mean research was performed or implementation is complete.

## 1. Scope and Evidence Boundary

- [x] CHK001 - Do the constitution preamble, Spec **Type** line, and Plan §1 / §2 (with Plan §4 where relevant) state unambiguously that this is forensic documentation-history research and NOT a code review / software implementation, so that a task generator cannot reframe any phase as product delivery? [Boundary, Constitution scope note, Spec Type, Plan §1, Plan §2, Plan §4]
- [x] CHK002 - Is the evidence boundary closed and mutually identical across Constitution §3, FR-006…FR-008, FR-007, and Plan §2 — the eligible documentary class list (committed/historical `README.md`, `AGENTS.md`, relevant `docs/**`, deleted/renamed/superseded documents, documentation diffs, documentary lineage, commit metadata), the exhaustively enumerated forbidden set (application/Scala/Java/Kotlin/Python source, tests/test bodies, `build.sbt`/build config, CI, runtime config, code-inferred dependency graphs, Elasticsearch/Qdrant/route behavior, generated artifacts, evaluation-inferred-from-code), and mixed code+docs commits constrained to their documentation portion only? [Completeness, Constitution §3, Spec FR-006, FR-007, FR-008]
- [x] CHK003 - Is the "documentary claim vs implementation fact" rule defined both as a conclusion rule (Constitution §3, FR-010) and as a report-sentence rule (Constitution §26, FR-045), each with a concrete correct/incorrect example, so the boundary survives from classification into prose? [Clarity, Constitution §3, §26, Spec FR-010]

## 2. Frozen Baseline and Git-State Safety

- [x] CHK004 - Are the exact frozen baseline SHA, expected branch (`develop`), repository root, research-start timestamp, execution-time re-verification (explicitly distinct from planning-time confirmation), fail-closed mismatch behavior, read-only-only / no-branch-mutation / no-fetch-pull / locally-present-ref-universe constraints, AND the completion canonical-immutability diff gate (`git diff -- README.md AGENTS.md docs` empty, with defined behavior — report, do not auto-revert, do not claim completion) each specified at least once with a matching enforcement locus across Constitution §1/§4/§5, FR-001…FR-005, FR-051, SC-010, SC-013, and Plan §6/§19? [Completeness, Constitution §1, §5, Spec FR-001, FR-002, FR-003, FR-051, Plan §6]

## 3. Previous-Review Independence and Contamination (mandatory, high priority)

- [x] CHK005 - Does the contract unambiguously distinguish Run-A BeautyQ documentation-history outputs (MUST be physically absent from the working tree AND from every other searched location before BLIND begins) from unrelated ignored scratch material (MAY physically exist but must stay unread, unsearched, and outside evidence), so the two categories cannot be collapsed into one rule? [Boundary, Constitution §2, Spec FR-012, Plan §6, Plan §20]
- [x] CHK006 - Does the contract explicitly forbid satisfying Run-A isolation merely by ignore rules, by excluding the Run-A directory from searches, by the reviewer moving/renaming/deleting it, or by reading it to judge it harmless — and instead assign physical isolation to the user with a fail-closed stop-and-report path? [Completeness, Constitution §2, Spec FR-013, FR-014, Plan §6]
- [x] CHK007 - Does a successful preflight record ONLY `Previous-review isolation precondition: PASS` (never Run-A names, contents, conclusions, or summaries), with a stated "if PASS cannot be established without reading Run-A, stop and ask the user" rule? [Clarity, Spec FR-012, Plan §6, Plan §21 Phase 1]
- [x] CHK008 - Is the accidental-exposure protocol complete and limited to exactly the constitution's three fields (exposure occurred, exactly what was exposed before reading stopped, whether it could have contaminated the blind phase)? [Completeness, Constitution §2, Spec FR-015, Spec edge case 14]

## 4. Candidate-Universe Completeness

- [x] CHK009 - Is candidate documentary-commit discovery defined as a distinct phase preceding semantic historical synthesis (Phase 2 before Phase 5) and explicitly NOT bounded by line churn, commit-message keywords, current-paths-only, Gen1/Gen2 dates/labels, or assumed epochs — while requiring discoverability of deleted/renamed/copied/replaced/superseded paths and locally-present non-primary refs? [Completeness, Constitution §6, Spec FR-016, FR-018, FR-046, Plan §7]
- [x] CHK010 - Is it stated that candidate membership does NOT imply relevance, and that "entire relevant history" has an auditable candidate denominator independent of `COVERAGE.tsv`, so COVERAGE alone is never treated as proof of completeness? [Clarity, Spec Story 1 Independent Test, Spec FR-046, Spec SC-001, Plan §7]

## 5. Candidate Reconciliation and Coverage Semantics

- [x] CHK011 - Is it required that every candidate resolves, after documentary-content inspection, to exactly one of a relevant `COVERAGE.tsv` disposition row OR a `WORKING.md` Candidate Corpus Exclusion Ledger record — with no unaccounted third outcome, and with exclusion explicitly NOT being a fifth disposition? [Completeness, Spec FR-047, Spec edge case 20, Plan §8]
- [x] CHK012 - Is `clerical` defined strictly as relevant-but-mechanically-editorial, with an explicit rule that `clerical` never means "unrelated", that uncertain relevance errs toward COVERAGE inclusion, and that exclusion may not rest on metadata, message, churn, path name, or date alone? [Clarity, Spec FR-023, FR-048, Plan §8, Constitution §7]
- [x] CHK013 - Are BOTH arithmetic identities stated verbatim and mechanically recomputable — `candidate_documentary_commits = relevant_documentary_commits + excluded_after_inspection` and `relevant_documentary_commits = deep + semantic-scan + clerical + duplicate` — with exact counts required and approximate wording (`~30+`, `approximately 45`) forbidden? [Measurability, Constitution §8, Spec FR-025, Spec SC-003, Plan §8]

## 6. Disposition Quality

- [x] CHK014 - Are all four dispositions (deep, semantic-scan, clerical, duplicate) mutually distinguished by an inspection precondition, and are Constitution §7 and Spec FR-021 the authoritative deep-review categories while Plan §10 preserves every mandatory category and is permitted to refine or specialize them with non-weakening examples/triggers (semantic non-weakening, not literal list equality: a plan-level refinement is NOT a conflict merely because its exact words appear in no higher-level artifact, but a Plan §10 omission, weakening, or contradiction of a mandatory Constitution/FR-021 category IS a conflict)? [Consistency, Constitution §7, Spec FR-021, Plan §10]
- [x] CHK015 - Is it required that low line churn can never suppress semantic importance (single-word modal/ownership changes escalate to at least `semantic-scan`, typically `deep`) and that `duplicate` classification requires proven documentary equivalence with the equivalent commit recorded (never branch-name inference)? [Clarity, Constitution §7, §16, Spec FR-024, FR-038, Spec edge case 1, 7]

## 7. Evidence Provenance and Analytical Claims

- [x] CHK016 - Are all six provenance classes (CD, RD, ND, CM, HI, RJ) defined with fixed weighting rules (RD≠CD, CM≠CD, HI visibly inference, RJ visibly judgment, High/Critical never rest only on RD/CM) and stated identically in Constitution §11 and FR-026? [Completeness, Constitution §11, Spec FR-026, Spec SC-008]
- [x] CHK017 - Is provenance consistently defined at the material evidence-anchor / atomic material analytical-statement level, while composite findings may combine multiple classes without hiding the provenance of their material subclaims? [Consistency, Constitution §11, Spec FR-026, Plan §9]
- [x] CHK018 - Are evidence-anchor minimum fields and stable IDs specified (unique ID `E-0001…`, one provenance class, exact commit/path/section, concise paraphrase, thread association), and does the single authoritative FR-052 materiality threshold operationally cover evidence anchors, decisions, absence claims, transitions, findings, and auditability — so no artifact re-invents the word "material"? [Clarity, Spec Key Entities, Spec FR-052, Plan §9, Spec FR-026, FR-028]

## 8. Chronological Review and Context Control

- [x] CHK019 - Is candidate inspection required to proceed chronologically, with batch size adaptive to candidate volume/documentary density (stated default range), and batch boundaries defined as operational-only and explicitly forbidden from implying historical-epoch significance? [Boundary, Plan §10, Constitution §15, Spec FR-032]
- [x] CHK020 - Are the context-drift controls specified: prohibition on casually parallelizing adjacent interpretation-dependent batches, mandatory `WORKING` compression after each substantial batch, stable-ID survival through compression, and exclusion of raw commands/diffs/diary material from `WORKING`? [Completeness, Constitution §24, Spec FR-042, Plan §10, Plan §21]

## 9. Decision Reconstruction and Thread Separation

- [x] CHK021 - Does the contract require a decision ledger before final narrative and threads before epochs, with evidence-derived thread names, no preloaded BeautyQ-specific threads, and same-component decisions kept separate when conceptually distinct? [Completeness, Constitution §14, §15, Spec FR-030, Plan §12]
- [x] CHK022 - Is the cross-thread causality rule specified: same-thread inference may be marked `HI`, cross-thread causality requires an explicit documentary linkage, and otherwise chronology/correlation MUST NOT be called causality? [Clarity, Constitution §14, Spec FR-031, Plan §13]

## 10. Retrospective Claims and Historical Retcon Discipline

- [x] CHK023 - Is it required that later claims about earlier intent be compared against contemporaneous documentation and classified with the full vocabulary (accurately preserved / simplified but fair / stronger than contemporaneous evidence / changed interpretation / possible historical retcon / unverifiable), and that current documentation be prevented from becoming automatic authority over historical intent? [Completeness, Constitution §12, Spec FR-027, Plan §13c, Spec edge case 9]

## 11. BLIND Independence and Freeze Boundary (mandatory hard gate)

- [x] CHK024 - Does the contract require blind analysis to precede any user hypothesis pack, use open coding rather than confirmation, derive threads before epochs, and enumerate all required BLIND contents including exact coverage counts at freeze and the independence declaration? [Completeness, Constitution §9, §10, Spec FR-039, Plan §14]
- [x] CHK025 - Is `BLIND FREEZE → STOP EXECUTION → return to user` defined as an actual user-interaction boundary (execution halts and the command returns to the user) rather than only a logical phase transition, and is BLIND immutability after freeze (except labelled `FACTUAL CORRECTION` appends) required? [Clarity, Constitution §10, Spec FR-040, Plan §14]
- [x] CHK026 - Is the reviewer explicitly forbidden from requesting, anticipating, reconstructing, inferring from conversation/history, or searching for the hypothesis pack before the freeze? [Completeness, Spec FR-040, Spec Assumptions, Plan §14]

## 12. Mandatory Adversarial Challenge (mandatory)

- [x] CHK027 - Is Research Story 3 unambiguously mandatory for BeautyQ Run B, with the challenge beginning only after the user supplies the pack and defined state transitions `awaiting pack → received → challenge performed`? [Completeness, Spec FR-040, Spec Clarification 1, Plan §15]
- [x] CHK028 - Does the contract require the challenge to seek disconfirming evidence, keep hypothesis results in `WORKING.md`, and never rewrite the sealed BLIND narrative? [Clarity, Spec Story 3 scenarios 2–3, Plan §15, Constitution §10]
- [x] CHK029 - Is it explicitly impossible to finalize Story 4 / `REPORT.md` while the challenge state is `awaiting pack`, with NO "challenge not performed but report completed" fallback path? [Completeness, Spec FR-040, Spec edge case 22, Plan §18, Plan §20]

## 13. Transfer Audits

- [x] CHK030 - Does every major documentary retirement/deletion/consolidation/replacement/source-truth move require the full audit chain (old owner → transition → destination → decision preserved? → rationale preserved? → alternatives preserved/lost? → intentional history/status discard? → orphaned knowledge → review judgment), AND is it explicitly prevented that deletion speed or the mere existence of a replacement file proves a clean/lossless transfer, with rule vs rationale preservation assessed separately and intentional discard recorded apart from accidental loss? [Completeness, Constitution §17, Spec FR-037, Spec edge case 10, 11, Plan §16]

## 14. Absence Claims and Counter-Evidence

- [x] CHK031 - Is there ONE authoritative absence-record schema — the FR-028 execution schema whose field-to-§13 mapping is explicitly stated (which fields implement the constitution §13 minimum; which are stricter execution-level additions), with Plan §16 referencing FR-028 rather than maintaining a competing field list — and are unsupported corpus-wide absence claims required to be weakened to search-bounded wording? [Consistency, Constitution §13, Spec FR-028, Plan §16, Spec edge case 13]
- [x] CHK032 - Does every High/Critical finding require the five-step counter-evidence pass (support, deliberate weakening search, strongest alternative interpretation, severity reassessment, confidence reassessment/removal when counter-evidence wins)? [Completeness, Constitution §18, Spec FR-029, Spec Story 3 scenario 4, Plan §16]

## 15. Severity and Confidence Discipline

- [x] CHK033 - Are severity (Critical/High/Medium/Low) and confidence (High/Medium/Low) defined as fully independent scales fixed before findings exist, such that a High-severity finding may carry Medium confidence, with Low-confidence headline findings discouraged? [Clarity, Spec FR-049, FR-050, Spec Clarification 4, SC-014]
- [x] CHK034 - Does the contract forbid severity being inferred from document size, module/project count, process complexity, proof-machinery size, or reviewer dislike, and does FR-049 make Critical eligibility require ALL FOUR objective conjunctive conditions (with any failure capping severity at High) so that "rare" is only a stated consequence of the demanding conditions and never an independent reviewer preference? [Clarity, Constitution §20, Spec FR-049, FR-050, Plan §18]

## 16. Current-State Classification

- [x] CHK035 - Must the future current-state reread distinguish all six categories (current normative architecture, accepted limit, active plan, proposal, simplification proposal, historical explanation), and treat later criticism of earlier architecture as retrospective/`RJ`-like documentary evidence rather than objective fact? [Completeness, Constitution §21, Spec FR-036, Spec SC-012, Plan §17]

## 17. Artifact Ownership and Output Discipline

- [x] CHK036 - Are exactly four analytical outputs defined (COVERAGE.tsv, BLIND.md, WORKING.md, REPORT.md), with the contract forbidding standalone candidate ledgers, exclusion ledgers, ref dumps, diff archives, command transcripts, scratch reports, and alternative reports as a fifth artifact? [Completeness, Constitution §23, Spec FR-041, Plan §4, FR-044]
- [x] CHK037 - Does the contract correctly permit the `.review-bundles/docs-history-speckit/` directory to pre-exist empty while requiring the four output FILES to be first created/populated only during execution, and does it locate the exclusion ledger, ref inventory, severity/confidence rubric, and challenge state inside WORKING/spec/plan rather than as separate files? [Clarity, Spec FR-041, Spec Assumptions, Plan §4]

## 18. REPORT and Mechanical Closeout

- [x] CHK038 - Is `REPORT.md`'s fixed 20-section structure fully enumerated (FR-043 / Plan §18), with finalization gated on Story 3 completion and on generating the report from ledgers/evidence rather than remembered narrative? [Completeness, Constitution §23, Spec FR-043, Spec FR-045, Plan §18]
- [x] CHK039 - Does closeout explicitly recompute candidate/excluded/relevant counts, the four disposition counts, earliest/latest relevant dates, exact date span, path count, and finding IDs/severities/confidences; require repeated counts to agree across COVERAGE/WORKING/REPORT/completion response; carry the absolute-wording watchlist; require the sentence-level DOC-only scope lint; and make canonical immutability a hard final gate? [Measurability, Constitution §8, §25, §26, Spec FR-045, Spec SC-011, Plan §19]

## 19. Failure and Pause Semantics

- [x] CHK040 - Do the artifacts distinguish FAIL, PAUSED-awaiting-challenge-pack, and COMPLETE, and state that a paused state is valid and can NEVER be misreported as successful completion? [Clarity, Spec FR-040, Spec edge case 22, Plan §20, SC-007]
- [x] CHK041 - Is the fail-closed condition set exhaustive (baseline mismatch, Run-A contamination, canonical modification, need for forbidden source inspection, need for fetch/pull, unreconciled candidate accounting, unreconciled arithmetic, hypothesis dependency before BLIND, unresolved High/Critical evidence gaps at report time), and does the authoritative FR-053 execution-state reporting contract give every FAIL and every BLIND-FREEZE pause its exact required fields — including the explicit no-completion statement and the Run-A non-disclosure rule — with Plan §20 referencing FR-053 rather than paraphrasing, so that "do not fake-green" is operationalized rather than left as a slogan? [Completeness, Spec FR-003, FR-013, FR-040, FR-053, Plan §20]

## 20. Task-Decomposition Readiness (pre-`/speckit.tasks`)

- [x] CHK042 - Do spec+plan define each of the 11 phases (preflight, candidate universe, chronological reconciliation, lineage/decisions, blind thread analysis, BLIND FREEZE+stop, challenge blocked on user pack, transfer/absence/counter-evidence, current-state reread, REPORT, mechanical closeout) with enough precision that `/speckit.tasks` cannot silently drop a hard gate — in particular the FREEZE stop and the mandatory challenge gate? [Traceability, Plan §21, Spec FR-039, FR-040]
- [x] CHK043 - Does the plan state that evidence/phase dependency order overrides stock software-user-story phase assumptions (Research Story IDs for traceability only), and are conservative parallelism constraints specified (sequential stateful interpretation; parallelism only for non-diverging read-only enumeration / arithmetic re-checks)? [Boundary, Spec P1/P2 note, Plan §9, Plan §21]

## 21. Anti-Failure Mode Guards (requirement-level prevention)

- [x] CHK044 - Do the written requirements collectively foreclose the corpus/accounting failure modes: claiming exhaustive history from a sampled/high-churn subset, treating COVERAGE alone as completeness proof, metadata-only classification, low-churn semantic changes lost, and unrelated docs hidden inside `clerical`? [Completeness, Constitution §6, §8, Spec FR-023, FR-046, FR-048, SC-001, SC-002]
- [x] CHK045 - Do the written requirements collectively foreclose the narrative/report failure modes: retrospective wording becoming original-intent evidence, one-backend-decisions merged into one story, neat epochs before decision reconstruction, temporal order becoming unsupported causality, source-truth transfer called clean because the old file was deleted, absence claims from casual grep, complexity called overengineering because it is large, current proposals described as current architecture, report counts copied from memory, final report before the adversarial challenge, Run-A contamination avoided only by ignoring its directory, and implementation claims leaking into a DOC-only report? [Completeness, Constitution §14, §17, §21, §25, §26, Spec FR-027, FR-030, FR-031, FR-037, FR-040, FR-045]

## Notes

- Mark items `[x]` only after reviewer inspection of the constitution/spec/plan text confirms the requirement-quality criterion is satisfied; leave items unchecked where the written contract still needs clarification, correction, or reviewer evaluation.
- This checklist tests research-method requirements quality only — it must never be read as confirming that historical BeautyQ research has been performed, nor as a Git-history or code-review checklist.
- `/speckit.implement` reads checklist checkbox state as a gate and must not modify markers.
- `checklists/requirements.md` is a separate built-in spec-quality checklist maintained by `/speckit.specify` and `/speckit.clarify`; its `[x]` states are unrelated to this custom artifact.
- Items flagged `[Conflict]`/`[Ambiguity]`/`[Gap]` point at potential soft spots in the written contract for the reviewer to resolve before task decomposition; they are review questions, not defects asserted by this generator.
