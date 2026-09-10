# Feature Specification: BeautyQ Search / Search Gen2 Documentation History Review

**Type**: Forensic RESEARCH specification — not a software feature specification.

**Research Baseline**: `789e56674145a45c31e2e25d997c60b359431a48` (expected branch `develop`). No Git feature branch is created or switched for this research (constitution §4); the research runs in the existing checkout.

**Governing Constitution**: `.specify/memory/constitution.md` v1.0.2 — non-negotiable; it overrides stock Spec Kit implementation-oriented assumptions wherever they conflict. This specification is a research contract subordinate to that constitution.

**Created**: 2026-09-04

**Status**: Draft (clarified 2026-09-04; ready for `/speckit.plan`)

**Input**: User description (abridged): "Create exactly one Spec Kit specification for an independent forensic historical review of BeautyQ Search / Search Gen2 documentation… reconstructed from documentation history only… strictly DOC-ONLY… frozen baseline 789e56674145a45c31e2e25d997c60b359431a48…" Full directive supplied in the `/speckit.specify` invocation.

## Clarifications

### Session 2026-09-04

- Q: Is the adversarial challenge phase (Research Story 3) optional for this run if the hypothesis pack is never supplied? → A: Mandatory for BeautyQ Run B — after BLIND FREEZE execution stops and waits for the user-supplied pack; Research Story 4 and REPORT.md finalization may occur only after Story 3 is performed.
- Q: What is the auditable denominator for "100% relevant coverage"? → A: A reproducible candidate documentary commit universe, with every candidate resolved to exactly one outcome — a relevant COVERAGE.tsv disposition row or a WORKING.md Candidate Corpus Exclusion Ledger record — reconciled by two exact arithmetic identities.
- Q: What Git-ref universe is audited without changing repository state? → A: Exactly the refs locally available in the repository at research start under the frozen baseline; no fetch, pull, remote expansion, or ref/branch/tag/stash mutation; a compact ref inventory with unique-substance assessment is recorded in WORKING.md.
- Q: How are finding severity and confidence defined before findings exist? → A: A neutral methodology-only severity scale (Critical/High/Medium/Low) and an independent confidence scale (High/Medium/Low); severity and confidence are never conflated.
- Q: How does provenance apply to complex findings? → A: Per material evidence anchor and per material analytical statement, not as one label forced on an entire finding; the constitution's six classes and all of its weighting rules are unchanged. Constitution v1.0.2 (2026-09-04 PATCH) now states this unit explicitly in §11 and records that severity and confidence are not substitutes for provenance.

## Research Purpose & Questions

The review MUST reconstruct, from documentation history only:

> What did BeautyQ documentation say a good search architecture should optimize for at each major historical stage, how did those criteria change, and does the current documentary architecture still form a coherent architecture and decision record?

The review MUST independently establish, from evidence:

- major architectural ideas and the problems those ideas were intended to solve;
- architectural values, and changes in architectural values;
- durable invariants;
- source-of-truth changes and ownership changes;
- requirement-strength changes;
- architectural scope expansion and contraction;
- focus changes and terminology changes;
- reversals; abandoned, narrowed, or deferred goals;
- temporary mechanisms that disappeared or remained;
- migration/cutover influence on permanent documentation;
- proof/evaluation/governance expansion;
- artifact ownership;
- rationale preservation and rationale loss;
- historical retrospective accuracy;
- successful simplification and successful ownership consolidation;
- the current documentary architecture.

**None of these phenomena are assumed to have occurred.** They are research questions to be established from evidence. The review MUST NOT assume in advance that the history is any particular shape (e.g., Gen1 → Gen2 → proof expansion → simplification, product → platform → governance, overengineered, coherent, incoherent, or self-correcting); structure MUST be discovered from evidence.

## Research Actors & Consumers

- **Independent documentation-history reviewer** (primary actor) — performs the DOC-ONLY forensic reconstruction under the constitution and this specification.
- **BeautyQ architecture coordinator** (consumer) — must be able to trace every final conclusion back to documentary evidence and exact coverage accounting.
- **Future maintainers who need a reliable historical architecture/decision record** (consumer) — receive the auditable report as a decision record.

No software users, APIs, UI flows, services, endpoints, features, or runtime behaviors are defined by this research. "Business value" here means auditability, historical understanding, evidence quality, and decision-record quality.

## Research Scenarios & Testing *(mandatory)*

**Note**: Story priorities P1/P2 express research-sequencing importance (the corpus foundation must exist before synthesis; synthesis before challenge; challenge before the final auditable report). They are NOT product MVP priorities; every story remains mandatory for review completion.

### Research Story 1 — Establish the complete documentary corpus (Priority: P1)

The reviewer can account for every relevant documentation-changing commit and historical documentary path without relying on metadata-only classification, and can show how the candidate documentary commit universe was exhausted (FR-046…FR-048, FR-051).

**Why this priority**: The coverage ledger is the audit foundation; without exhaustive, content-based accounting, no later conclusion is verifiable.

**Independent Test**: Reconcile three surfaces against each other: (1) the independently enumerated candidate documentary commit universe, (2) the relevant rows of `COVERAGE.tsv`, and (3) the `Candidate Corpus Exclusion Ledger` in `WORKING.md`. The test passes only when every candidate has exactly one accounting outcome (a relevant COVERAGE disposition or an exclusion-ledger record) and both arithmetic identities hold: `candidate_documentary_commits = relevant_documentary_commits + excluded_after_inspection` and `relevant_documentary_commits = deep + semantic-scan + clerical + duplicate`. Auditing `COVERAGE.tsv` alone is logically insufficient: a ledger cannot by itself prove that omitted commits do not exist. `COVERAGE.tsv` itself remains restricted to relevant documentary commits as required by the constitution.

**Acceptance Scenarios**:

1. **Given** the frozen baseline, **When** the reviewer enumerates the candidate documentary commit universe, **Then** discovery is driven by committed/historical/deleted/renamed documentary paths relevant or potentially relevant to BeautyQ Search/Search Gen2 (including committed `README.md`, committed `AGENTS.md`, and relevant `docs/**`) across locally present refs, without line-churn, commit-message-keyword, current-paths-only, Gen2-date, or assumed-epoch restrictions (FR-046).
2. **Given** the candidate universe, **When** each candidate's documentary content has been inspected sufficiently, **Then** it resolves to exactly one accounting outcome: a `COVERAGE.tsv` relevant row with disposition `deep`, `semantic-scan`, `clerical`, or `duplicate`, or a `Candidate Corpus Exclusion Ledger` record for content outside the research boundary (FR-047).
3. **Given** deleted, renamed, or superseded documents, **When** the corpus is assembled, **Then** each is represented with its lineage and disposition.
4. **Given** relevant non-primary Git refs (branches, tags, local refs, reference/stash snapshots) locally available at research start, **When** they are checked read-only, **Then** each is accounted for by whether it contains unique documentary substance, and the compact ref inventory plus unique-substance assessment appears in `WORKING.md` (FR-051).
5. **Given** duplicate/rebased/cherry-picked lineages, **When** classified `duplicate`, **Then** the equivalence is proven against the other lineage and the equivalent commit is recorded.
6. **Given** a low-churn semantic change, **When** it is classified, **Then** it cannot be skipped solely because it is small.

---

### Research Story 2 — Reconstruct the documentary history independently (Priority: P1)

The reviewer can derive documentary lineages, decision threads, architectural values, transitions, candidate reversals, and candidate findings **before** receiving any BeautyQ-specific hypothesis pack.

**Why this priority**: Blind derivation is the control that makes later hypothesis testing meaningful rather than confirmation; it is the core value of the exercise.

**Independent Test**: Inspect `BLIND.md` alone: it contains independently derived threads with explicit provenance, coverage totals at freeze, a freeze timestamp, and the no-hypothesis-pack declaration, produced without any externally supplied BeautyQ narrative.

**Acceptance Scenarios**:

1. **Given** the corpus from Story 1, **When** the reviewer analyzes the chronological evidence, **Then** decision threads are derived from evidence, not prescribed in advance.
2. **Given** candidate historical periods, **When** they are proposed, **Then** they follow the decision-thread reconstruction and are not imposed before it.
3. **Given** any conclusion, **When** it is recorded, **Then** its provenance class (`CD`/`RD`/`ND`/`CM`/`HI`/`RJ`) is explicit.
4. **Given** the completed first pass, **When** the reviewer freezes it, **Then** `BLIND.md` can be produced and sealed independently of the user, and execution subsequently stops at BLIND FREEZE awaiting the user-provided challenge pack (the pack is never part of the blind input).

---

### Research Story 3 — Adversarially challenge the blind reconstruction (Priority: P2 — mandatory for this run)

After the BLIND freeze, execution stops and waits for the user-provided BeautyQ-specific adversarial hypothesis pack; the reviewer then tests it without rewriting the independent first-pass narrative. For BeautyQ Run B this story is mandatory (FR-040): Research Story 4 and final review completion may occur only after this challenge phase has been performed. The pack is supplied explicitly by the user after freeze — the reviewer MUST NOT request it before freeze, anticipate it, reconstruct it, infer it from conversation or history, search for it, or treat it as blind input.

**Why this priority**: It strengthens (does not create) the audit; it is meaningful only after the blind baseline exists, and in this run it gates all downstream completion.

**Independent Test**: Given a sealed `BLIND.md` and a later `WORKING.md` challenge section, verify each hypothesis received an evidence-based classification with supporting and disconfirming evidence recorded, and that `BLIND.md`'s narrative is unchanged apart from any appended `FACTUAL CORRECTION` notes.

**Acceptance Scenarios**:

1. **Given** the hypothesis pack arrives after freeze, **When** each hypothesis is examined, **Then** it is classified from evidence.
2. **Given** any hypothesis classification, **When** it is recorded, **Then** both supporting and disconfirming evidence are recorded.
3. **Given** the challenge phase, **When** it completes, **Then** `BLIND.md` remains sealed (immutable except `FACTUAL CORRECTION` appends).
4. **Given** any High/Critical finding, **When** it survives the challenge phase, **Then** it has explicit counter-evidence analysis.
5. **Given** the pack has not yet been supplied after freeze, **When** the reviewer is asked to continue, **Then** the run is reported as paused at BLIND FREEZE awaiting the challenge pack, `REPORT.md` is not finalized, and no fallback "not performed" completion path is taken.

---

### Research Story 4 — Produce an auditable final historical review (Priority: P2 — gated on Story 3)

The coordinator can trace final conclusions back to documentary evidence and exact coverage accounting. This story — and any finalization of `REPORT.md` — may occur only after Research Story 3 has been performed on the user-supplied post-BLIND challenge pack (FR-040); before that pack arrives the run is paused at BLIND FREEZE and the review is not complete.

**Why this priority**: It is the consumable deliverable, but it is only as strong as Stories 1–3; it must not precede them, and in this run it cannot precede the mandatory challenge phase.

**Independent Test**: Re-derive the headline numbers and every major conclusion of `REPORT.md` from `COVERAGE.tsv`, `WORKING.md`, and cited documentary evidence; both arithmetic identities (`candidate_documentary_commits = relevant_documentary_commits + excluded_after_inspection` and `relevant_documentary_commits = deep + semantic-scan + clerical + duplicate`) hold exactly and every candidate has exactly one accounting outcome; and no statement asserts implementation behavior.

**Acceptance Scenarios**:

1. **Given** every discovered major documentary retirement/transfer, **When** the report is finalized, **Then** each has a completed transfer audit.
2. **Given** every material absence claim used in findings, **When** the report is finalized, **Then** an explicit absence-search record exists.
3. **Given** current normative architecture, active plans, proposals, simplification proposals, and historical states, **When** they are described, **Then** they remain explicitly distinct.
4. **Given** the final ledgers, **When** the arithmetic gate runs, **Then** `relevant = deep + semantic-scan + clerical + duplicate` and `candidates = relevant + excluded_after_inspection` both hold exactly.
5. **Given** the finished `REPORT.md`, **When** it is scope-linted, **Then** no implementation claims appear and all recommendations concern documentation only.
6. **Given** the user-supplied challenge pack, **When** Story 3 completion is checked, **Then** the challenge results are present in `WORKING.md` and the challenge-phase state recorded there is "performed"; if that state is "awaiting pack", `REPORT.md` is not finalized.

---

### Edge Cases

1. A **one-line semantic documentation change** (e.g., a modal or ownership word): MUST be classified from its documentary content, at least `semantic-scan` and typically `deep`; size is never evidence.
2. A **large but purely clerical rewrite of relevant documentation** (hundreds of formatting-only lines): MAY be `clerical`, but only after the documentary change has been inspected; `clerical` never means "unrelated documentation" (see 17, 20).
3. A **mixed code+docs commit**: researched only through its documentation changes and its commit metadata/message for navigation/provenance; its implementation diff is never inspected as evidence.
4. A **renamed document**: MUST be followed through the rename in lineage accounting; a rename is not evidence of loss or of a new decision.
5. A **deleted document**: MUST receive a corpus entry and, if it was a major retirement, a transfer audit.
6. A **superseded document**: MUST be marked with its claimed successor and audited as a transfer if material.
7. A **duplicate/rebased/cherry-picked documentary lineage**: `duplicate` disposition only with proven equivalent substance and the equivalent lineage/commit recorded; branch names alone never prove duplication.
8. **Local/stash refs containing duplicate documentary copies**: MUST be checked for unique documentary substance; substance-identical snapshots are `duplicate` with the equivalence recorded.
9. **Current documentation retrospectively describing old intent**: treated as `RD`, compared against contemporaneous documentation, and classified per the retrospective-claim rule — never automatically trusted.
10. A **transfer preserving a rule but losing its rationale**: MUST be reported as a partial transfer; rule preservation and rationale preservation are assessed separately.
11. A **transfer intentionally discarding obsolete chronology**: permissible; the review records intentional status/history discard separately from accidental loss.
12. A **later proposal criticizing earlier complexity**: its criticisms are retrospective documentary evidence (`RD`/`RJ`), not automatically objective historical truth.
13. An **ambiguous absence claim**: MUST be weakened to search-bounded wording ("No explanation was found in the reviewed corpus after searching X/Y/Z") unless exhaustive absence is demonstrable.
14. **Accidental exposure to a previous review**: reading stops immediately; only the constitution's three contamination fields are recorded (exposure occurred, what was exposed, potential blind-phase contamination).
15. **Uncommitted Spec Kit/review-harness artifacts**: never historical evidence; excluded from all corpus accounting.
16. A **historical `AGENTS.md` change relevant to architectural/process governance**: eligible documentary evidence under the dual-role rule.
17. **Generic irrelevant `AGENTS.md` changes**: after documentary-content inspection they are excluded from the BeautyQ historical corpus and recorded in the Candidate Corpus Exclusion Ledger — never dispositioned `clerical`, because relevance (not classification convenience) is established from content.
18. A **small modal change such as optional → required**: a requirement-strength transition; classified from content regardless of churn.
19. **Different historical decisions involving the same backend/component**: kept as separate decision threads; threads are not merged merely because they concern the same system.
20. A **candidate commit excluded on weak grounds** (unrelated-looking message, small or large diff, generic path name, early/late date, predates Gen2, or post-cutover timing): exclusion is invalid until documentary content has been inspected; genuine uncertainty errs toward inclusion in COVERAGE with a content-based disposition.
21. **Remote-only history**: refs not locally present at research start are outside this review; the reviewer MUST NOT fetch or pull to expand them and MUST NOT claim to have audited remote history (FR-051).
22. **Challenge pack outstanding after BLIND FREEZE**: the run pauses and is reported as awaiting the user-provided pack; `REPORT.md` is not finalized and completion is not claimed (FR-040).

## Requirements *(mandatory)*

### A. Baseline and repository state

- **FR-001**: The research corpus MUST consist ONLY of committed repository history reachable from the Git refs locally available in the repository at research start under the frozen baseline environment of `789e56674145a45c31e2e25d997c60b359431a48` (expected branch `develop`); the ref universe is frozen at research start and MUST NOT be expanded later (FR-051).
- **FR-002**: Before historical research begins, the reviewer MUST independently verify and record: `git rev-parse HEAD`, the current branch, the repository root, and the research start timestamp.
- **FR-003**: If the active HEAD differs from the frozen baseline, historical research MUST NOT begin; the mismatch MUST be reported, and it may proceed only after the user explicitly amends the constitution.
- **FR-004**: The review MUST use read-only Git operations only; it MUST NOT create or switch Git branches, or perform any state-changing Git operation (constitution §4).
- **FR-005**: Canonical BeautyQ content (`README.md`, `AGENTS.md`, `docs/**`, source, tests, build, CI/config) MUST remain unmodified throughout; the completion invariant `git diff -- README.md AGENTS.md docs` being empty MUST be verified at completion.

### B. Evidence boundary (DOC-ONLY)

- **FR-006**: Eligible evidence is limited to: committed `README.md` and its historical versions; committed `AGENTS.md` (and its historical versions) where materially relevant per FR-011; current and historical relevant `docs/**`; deleted, renamed, and superseded documentary files; documentation diffs; documentary file lineage; and commit metadata/commit messages, which carry weaker provenance than documentary text.
- **FR-007**: The reviewer MUST NOT inspect implementation as architectural evidence. Forbidden inspection: application source; Scala/Java/Kotlin/Python source; tests and test bodies; build definitions including `build.sbt`; CI; runtime configuration; dependency graphs inferred from code; Elasticsearch or Qdrant implementation/behavior; route/runtime behavior; generated implementation outputs; serving behavior; and evaluation behavior inferred from code/tests.
- **FR-008**: A mixed source+docs commit MAY be researched only through its documentation changes and its commit metadata/message for navigation/provenance; its implementation portion MUST NOT be inspected as evidence.
- **FR-009**: Uncommitted review-harness material MUST be excluded from evidence and MUST NOT be interpreted as evidence about BeautyQ history: `.specify/**`, `.opencode/**`, `specs/**`, `.review-bundles/docs-history-speckit/**`, and any other artifact created by this review. The exclusion applies only to uncommitted review-created material; committed `README.md`, `AGENTS.md`, and relevant `docs/**` history remain eligible corpus.
- **FR-010**: All conclusions MUST be phrased as documentary claims (e.g., "the documentation begins defining X as canonical owner here"), never as verified runtime/code facts. Documentation-named source paths, module names, or symbols MAY be mentioned but MUST NOT be opened to check documentary claims.
- **FR-011**: `AGENTS.md` MUST be handled under the constitution's dual-role rule: the current checked-out `AGENTS.md` may operationally govern reviewer behavior without being evidence; committed historical `AGENTS.md` versions are eligible documentary evidence ONLY when materially relevant to BeautyQ architecture, source-of-truth rules, ownership, scope/process governance, artifact policy, evidence/reproducibility, architecture-by-process, or documentation governance. Generic unrelated repository guidance MUST be excluded from the BeautyQ historical corpus.

### C. Previous-review independence

- **FR-012**: The existence and location of a previous independent review are NOT assumed facts. Before BLIND historical research begins, the reviewer MUST verify the isolation precondition that previous-review outputs are absent from the repository and from every location the reviewer will search in this run, and MUST record only that the precondition passed.
- **FR-013**: If previous-review material is found inside the repository or a review-searchable location, BLIND research MUST NOT begin; the reviewer MUST report the contamination risk for the user to isolate before proceeding.
- **FR-014**: The reviewer MUST NOT search outside the repository to locate the sealed review, MUST NOT ask for it or its findings, MUST NOT infer its conclusions from conversation context, and MUST NOT attempt to reconstruct them. This specification does not request or depend on the previous review.
- **FR-015**: On accidental exposure, the reviewer MUST stop reading immediately and record only the constitution's contamination fields: that exposure occurred, exactly what was exposed before reading stopped, and whether it could have contaminated the blind phase.

### D. Historical scope

- **FR-016**: The review MUST reach the earliest relevant BeautyQ/Search documentary history available from the frozen baseline. It MUST NOT stop at: current docs; the Gen2 introduction; the cutover; recent history; a fixed commit count; or a fixed line-churn threshold.
- **FR-017**: The reviewer MUST discover and account for: current and historical documentary paths; deleted, renamed, copied/replaced, and superseded documents; superseded specifications; architecture plans; roadmaps; ADRs; handoffs; module-split documents; derivation documents; migration/cutover documents; evaluation documents; operations documents; architecture reviews; audit/proof documents; new-domain/reuse documentation; and relevant coordinator/governance documentation.
- **FR-018**: All Git refs locally available in the repository at research start (the ref universe of FR-051) MUST be checked sufficiently to determine whether they contain unique documentary substance. Read-only ref enumeration (`git show-ref`, `git for-each-ref`, `git rev-list`) is the permitted mechanism; "all relevant history" means the relevant documentary history available from those locally present refs at research start, and the review MUST NOT claim to have audited remote history that was not locally present.

### E. Coverage ledger

- **FR-019**: `COVERAGE.tsv` MUST eventually contain exactly one row for every relevant documentation-changing commit, with columns in this order: `commit`, `date`, `paths`, `disposition`, `reason`, `decision_threads`, `evidence_ids`.
- **FR-020**: Allowed dispositions are ONLY `deep`, `semantic-scan`, `clerical`, `duplicate`. The concepts `metadata-only`, `assumed clerical`, `probably irrelevant`, `sampled`, and `skipped because low churn` are FORBIDDEN. There is no fifth disposition: an inspected candidate found outside the BeautyQ research boundary is an **exclusion-ledger record in `WORKING.md`** (FR-047), never a COVERAGE disposition.
- **FR-021**: `deep` MUST be used when the change materially affects or may affect: the architectural problem statement; requirements; rationale; ownership; source truth; normative status; architectural boundary; backend role; lifecycle; migration/cutover status; evaluation; operations; artifact/evidence ownership; reuse/generalization; product goal; non-goal; semantic terminology; requirement strength; or documentation governance. A deep review MUST inspect enough surrounding historical document state to understand the change.
- **FR-022**: `semantic-scan` MUST be used only after inspecting the documentation diff, and only when the change contains semantic context/status/wording that does not require a complete before/after document deep read. Commit message or numstat alone is insufficient for any disposition.
- **FR-023**: `clerical` MUST be used ONLY for a commit already established as RELEVANT BeautyQ documentary content whose inspected change is mechanically/non-semantically editorial (spelling, formatting, mechanically updated references, path corrections, whitespace) with no architectural, ownership, rationale, normative, status-semantic, or terminology consequence. `clerical` MUST NOT be used to mean "unrelated documentation": candidates found unrelated to BeautyQ Search/Search Gen2 on content inspection belong in the Candidate Corpus Exclusion Ledger (FR-047), which keeps the final disposition statistics meaningful.
- **FR-024**: `duplicate` MUST be used only when equivalent documentary substance is proven elsewhere in reachable history, recording the equivalent lineage/commit.
- **FR-025**: The final arithmetic MUST satisfy BOTH exact identities: (1) `candidate_documentary_commits = relevant_documentary_commits + excluded_after_inspection` and (2) the constitution-authoritative `relevant_documentary_commits = deep + semantic-scan + clerical + duplicate`. Both MUST be mechanically recomputed at closeout, with the same exact numbers appearing in `COVERAGE.tsv`, the `WORKING.md` coverage summary, the `REPORT.md` Coverage Appendix, and the completion response. No approximate counts are acceptable when exact counts exist, and no constitution-required COVERAGE count may be removed or weakened.

### F. Provenance and claim discipline

- **FR-026**: Provenance applies at claim/evidence level using the constitution's exact six classes: `CD` (contemporaneous documentary), `RD` (retrospective documentary), `ND` (current normative documentary), `CM` (commit metadata/message), `HI` (historical inference), `RJ` (reviewer judgment). Each material documentary evidence anchor MUST carry exactly one provenance class, and each material analytical statement MUST make clear whether it is documentary evidence, historical inference, or reviewer judgment; they MUST NOT be collapsed into "documented fact". `RD` is not `CD`; `CM` is not `CD`; `HI` is not documentary fact; `RJ` is not historical fact. A High/Critical finding MUST NOT rest only on `RD` or `CM`. A complex finding MAY cite multiple anchors of different classes (e.g., an `RJ` severity judgment supported by two `CD` anchors, one `ND` anchor, and an `HI` reconstruction) and MUST NOT be forced into a single documentary provenance category; the per-anchor discipline above still applies to every material sub-claim it makes. Severity and confidence are independent axes and are NOT substitutes for provenance (constitution §11 v1.0.2).
- **FR-027**: Whenever a later document claims or implies "originally", "historically", "the purpose was", "this was temporary", "this was never intended", "the original goal was", "Gen2 was designed to", or "the framework exists because", the reviewer MUST compare that retrospective claim against contemporaneous documentation and classify each important claim as: accurately preserved; simplified but fair; stronger than contemporaneous evidence; changed interpretation; possible historical retcon; or unverifiable.
- **FR-028**: Every material absence claim ("no document", "never", "always", "only", "nothing explains", "no owner existed", "rationale disappeared", "goal silently disappeared"; material per FR-052) MUST have an absence-search record containing exactly this required field set: (1) stable claim ID (`A-001`, `A-002`, …); (2) exact proposed claim; (3) intended analytical use / why the claim is material; (4) search terms; (5) synonyms / semantic variants; (6) documentary paths / historical path families searched; (7) Git refs searched; (8) relevant hits inspected; (9) residual uncertainty; (10) final safe wording. This field set is the authoritative EXECUTION SCHEMA: fields 1, 2, 4, 5, 6, 7, 8, and 9 implement the constitution §13 mandatory minimum, and fields 3 and 10 are stricter execution-level additions, not constitutional weakening. The plan and any later tasks MUST reference this schema rather than maintaining a competing field list. If corpus-wide absence cannot be established, the final safe wording MUST be weakened to search-bounded language.
- **FR-029**: Every High/Critical finding MUST include explicit counter-evidence analysis: supporting evidence identified, deliberate search for weakening evidence, strongest plausible alternative interpretation articulated, severity/confidence re-assessed, and the finding removed if counter-evidence wins. A smaller set of strong findings is preferred over volume.

### G. Analytical method

- **FR-030**: Decision threads MUST be reconstructed before historical epochs. The review MUST NOT merge decisions merely because they concern the same system/component. Potentially independent dimensions (capability existence; capability role; canonical result ownership; supplementation; fusion/reranking; lifecycle/cleanup; operational readiness; evaluation/proof; product-value justification) constrain methodology only — the actual thread set MUST be historically derived and is not predetermined.
- **FR-031**: A causal relationship between two different decision threads MAY be asserted only when documentary evidence explicitly links them; otherwise the relationship MUST be classified as uncertain historical inference (`HI`).
- **FR-032**: Epochs/periods MUST be derived only after the chronological evidence pass. Epoch boundaries MUST reflect meaningful changes in at least one of: dominant problem; architectural thesis; architectural values; ownership; source truth; proof burden; migration status; documentation organization; product goal. An epoch MUST NOT be created merely because milestone labels changed.
- **FR-033**: The review MUST distinguish four drift types without assuming any occurred: scope drift (expansion beyond an earlier problem — complexity alone does not establish it); focus drift (a secondary concern becoming dominant, temporarily or legitimately); value drift (changed criteria for good architecture); terminology drift (semantic change in terms, meanings, or ownership).
- **FR-034**: The review MUST keep seven complexity categories distinct — product, architecture, infrastructure, migration, proof/evaluation, process/governance, documentation — and MUST NOT infer one from another without evidence. A large proof workflow does not prove runtime overengineering; a long specification does not prove architecture overengineering; many modules do not prove documentation failure.
- **FR-035**: The final review MUST actively search for historically supported positive outcomes, including where evidence supports: durable invariant preservation; good ownership consolidation; rationale preservation; successful plan retirement; artifact clarification; scope containment; appropriate safety constraints; honest acknowledgment of limits; useful simplification; and successful correction of documentary mistakes. Positive and negative findings MUST use the same evidence standards.
- **FR-036**: The review MUST explicitly distinguish: current normative documentary architecture; accepted limits; active delivery plans; proposals; simplification proposals; and historical states. A proposal MUST NOT be described as current architecture. A later proposal's criticism of an earlier architecture is retrospective documentary evidence, not automatically objective historical truth.
- **FR-037**: Every major deletion, retirement, consolidation, replacement, or source-of-truth relocation MUST be audited through: old owner → transition → claimed destination → decision preserved? → rationale preserved? → alternatives preserved/lost? → history/status intentionally discarded? → orphaned information? → review judgment. Deletion speed is not evidence of a clean transfer; a replacement's existence is not evidence of a lossless transfer; rule preservation and rationale preservation MUST be assessed separately.
- **FR-038**: The review MUST detect historically significant requirement-strength changes such as: may → should → must; optional → default → mandatory; experimental → supported → required; temporary → current/permanent; goal → deferred → non-goal; proposal → normative; candidate → required; allowed → forbidden. Line churn MUST NOT be used as a proxy for semantic importance.

### H. Phase order and the BLIND freeze

- **FR-039**: `BLIND.md` MUST be produced before any user-provided BeautyQ hypothesis pack exists in the reviewer's inputs; the challenge pack is never part of the blind input. It MUST contain: the frozen baseline; the research history range discovered; the exact coverage totals at freeze; documentary lineages; independently derived decision threads; candidate historical periods; candidate architectural values; candidate findings; candidate positive findings; unresolved uncertainty; evidence provenance; a freeze timestamp; and an explicit declaration that no user-provided BeautyQ hypothesis pack was seen.
- **FR-040**: After freeze, `BLIND.md` is immutable except for clearly appended `FACTUAL CORRECTION` notes, and the hypothesis-challenge phase (Research Story 3) MUST NOT rewrite the independent first-pass narrative. For BeautyQ Run B the challenge phase is MANDATORY: immediately after BLIND FREEZE execution MUST stop and report the run as paused awaiting the user-provided adversarial hypothesis pack; the reviewer MUST NOT request the pack before freeze, anticipate it, reconstruct it, infer it from conversation/history, or search for it. Stories 1, 2, the BLIND FREEZE, and Story 3 are all mandatory, and Story 4 and `REPORT.md` finalization may occur ONLY after Story 3 has been performed; while the pack is un-supplied the review is NOT complete and `REPORT.md` MUST NOT be finalized. The current challenge-phase state (awaiting pack / received / challenge performed) MUST be recorded in `WORKING.md`.

### I. Outputs

- **FR-041**: The execution phase MUST eventually produce exactly these four analytical outputs and nothing else in that class: `.review-bundles/docs-history-speckit/COVERAGE.tsv`, `.review-bundles/docs-history-speckit/BLIND.md`, `.review-bundles/docs-history-speckit/WORKING.md`, `.review-bundles/docs-history-speckit/REPORT.md`. Raw Git-history dumps, full diff archives, giant command transcripts, scratch Markdown files, alternative competing reports, and any fifth analytical artifact (including standalone exclusion ledgers or raw ref dumps) MUST NOT be created: candidate-universe accounting, the Candidate Corpus Exclusion Ledger, the compact ref inventory, the severity/confidence rubric application, and challenge-phase state live inside `WORKING.md` (or this specification/plan) as sections. Spec Kit harness files remain outside the analytical-output count (FR-044).
- **FR-042**: `WORKING.md` MUST be a compressed evidence ledger retaining: coverage summaries including candidate and excluded counts; the `Candidate Corpus Exclusion Ledger` (FR-047); the compact locally-present ref inventory with its unique-substance assessment (FR-051); path lineage; evidence IDs with provenance classes; decision threads; decisions; value/requirement-strength changes; transfer audits; absence-search records; the challenge-phase state and post-freeze hypothesis-challenge results; counter-evidence; and unresolved uncertainty. It MUST NOT retain raw commands, repetitive discovery notes, long copied diffs, superseded guesses, chronological stream-of-consciousness, or raw ref dumps, and MUST be compressed after each substantial historical batch.
- **FR-043**: `REPORT.md` MUST contain these sections in order: 1. Executive Summary; 2. Current Documentary Architecture; 3. Historical Development; 4. Decision Threads; 5. Architecture Value Evolution; 6. Durable Ideas; 7. Reversals, Narrowings, and Abandoned Goals; 8. Source-of-Truth and Ownership History; 9. Scope Drift; 10. Focus Drift; 11. Value Drift; 12. Migration and Temporary-Machinery Audit; 13. Proof / Evaluation / Governance Expansion; 14. Artifact and Evidence Architecture; 15. Documentation Architecture; 16. Rationale Preservation and Retrospective Accuracy; 17. Findings; 18. What BeautyQ Got Right; 19. Documentation-Only Recommendations; 20. Coverage Appendix. Historical epochs inside it are derived findings and MUST NOT be predetermined.
- **FR-044**: Spec Kit artifacts (`spec.md`, `plan.md`, `tasks.md`, checklists, `.specify/feature.json`) are review-harness artifacts, not historical evidence and not part of the four analytical outputs.
- **FR-045**: Before `REPORT.md` is accepted, the reviewer MUST mechanically recompute from source ledgers: earliest/latest relevant dates, exact date span, total candidate documentary commits, excluded-after-inspection count, total relevant commits, and the four disposition counts; MUST verify BOTH arithmetic identities of FR-025 exactly and identically across all surfaces (including the `REPORT.md` Coverage Appendix, which MUST show candidate and excluded counts alongside the constitution-required COVERAGE counts); MUST confirm from `WORKING.md` that Research Story 3 was performed on the user-supplied pack (if it was not, `REPORT.md` MUST NOT be finalized); MUST scan the report for strong absolute wording (always, never, only, entire, all, unbroken, unchanged, clean, sole, "no document", completely, every) and verify or weaken each occurrence; and MUST scope-lint the report to reject or rewrite any sentence asserting actual runtime/code/test/CI/backend behavior.

### J. Candidate accounting, severity/confidence, and ref-universe methodology

- **FR-046**: Before semantic historical analysis, the reviewer MUST construct a reproducible candidate documentary commit universe from the frozen repository state, based on: committed documentary paths relevant or potentially relevant to BeautyQ Search/Search Gen2; historical/deleted/renamed documentary paths discovered through Git history; committed `README.md`; committed `AGENTS.md`; relevant `docs/**`; and locally available Git refs present at research start. Discovery MUST NOT be restricted by line-churn threshold, commit-message keyword, current paths only, known Gen2 dates, or previously assumed epochs. The candidate universe is a DISCOVERY SET, not evidence that every candidate is relevant.
- **FR-047**: After inspecting documentary content sufficiently, every candidate commit MUST resolve to exactly one accounting outcome: (A) **relevant** — one `COVERAGE.tsv` row with disposition `deep`, `semantic-scan`, `clerical`, or `duplicate` per FR-020…FR-024; or (B) **excluded after inspection** — documentary content outside the BeautyQ Search/Search Gen2 research boundary (e.g., generic unrelated `AGENTS.md` guidance or unrelated upstream/project documentation), recorded in a compact `Candidate Corpus Exclusion Ledger` section of `WORKING.md`. Excluded candidates MUST NOT be forced into `clerical` and MUST NOT create a fifth analytical artifact. Each exclusion record MUST contain: commit; date; documentary path(s); exclusion reason; and the level of inspection sufficient to establish exclusion.
- **FR-048**: A candidate MAY NOT be excluded solely because its commit message looks unrelated, its diff is small or large, its path name looks generic, its date is early or late, it predates Gen2, or it occurs after cutover — documentary content inspection is required. When relevance is genuinely uncertain, the reviewer MUST err toward inclusion in `COVERAGE.tsv` with an appropriate content-based disposition.
- **FR-049**: `REPORT.md`'s severity ratings MUST use this methodology-only scale, fixed before findings exist: **Critical** — a finding is eligible for Critical ONLY when ALL FOUR of these objective conditions are true: (1) it concerns a fundamental CURRENT documentary architectural decision, a current ownership/source-of-truth boundary, or the current rationale necessary to understand such a decision; (2) the documentary defect or materially misleading historical account prevents a reliable interpretation of that fundamental current decision from the documentary record; (3) the consequence is systemic to understanding that current decision record rather than merely local, stale, duplicated, awkward, or incomplete wording; and (4) classifying it High would materially understate the fact that the current documentary architecture/decision record cannot be reliably interpreted without resolving the issue. If any condition is not met, the maximum severity is High. Because all four conditions are demanding, Critical should naturally be rare — rarity is only a descriptive consequence of the four conditions, never an independent subjective criterion. **High** — a material documentary problem involving source truth, ownership, major architectural rationale, material reversal, value change, scope change, or current-vs-historical confusion, with meaningful consequences for architectural understanding; **Medium** — a significant but bounded issue (stale migration framing, duplicated authority, partial rationale loss, terminology drift, local process/documentation residue, incomplete transfer); **Low** — a minor documentary/navigation/wording problem with limited architectural-understanding impact. Severity MUST NOT be assigned merely from document length, number of modules mentioned, process complexity, amount of proof machinery, or reviewer dislike.
- **FR-050**: Findings MUST carry a confidence rating independent of severity — a High-severity finding MAY have Medium confidence — using: **High** — strong direct documentary evidence, good historical coverage, and survival of explicit counter-evidence search; **Medium** — meaningful support but partial reliance on inference, incomplete historical evidence, or unresolved alternative interpretation; **Low** — sparse, indirect, or materially ambiguous evidence. Low-confidence claims SHOULD NOT normally be promoted into headline findings. Severity and confidence MUST NOT be conflated.
- **FR-051**: "All relevant Git refs" is bounded to refs locally available in the repository at research start under the frozen baseline environment. The reviewer MUST NOT fetch new refs, pull, contact a remote to expand history, mutate refs, or create branches, tags, or stashes. Read-only enumeration (`git show-ref`, `git for-each-ref`, `git rev-list`) is permitted, and a compact ref inventory with unique-substance assessment MUST be recorded in `WORKING.md` (no raw ref dump artifact). "Entire relevant history" means the relevant documentary history available from those locally present refs at research start; the review MUST NOT claim to have audited remote history not locally present.

### K. Materiality and execution-state reporting

- **FR-052 (Authoritative materiality definition)**: "Material" is defined exactly once by this requirement; every other use of "material" in this specification and plan (material evidence anchor, material decision, material absence claim, material documentary transition, material finding, material uncertainty) refers to this definition and no section re-invents it. A documentary change, evidence anchor, analytical statement, decision, transition, absence claim, or uncertainty is **MATERIAL** when omitting, misclassifying, or materially misstating it could change or undermine at least one required analytical outcome or the auditability of that outcome. The required analytical outcomes are: (1) corpus relevance or COVERAGE disposition; (2) documentary lineage; (3) a material decision or Decision Thread; (4) an epoch boundary; (5) architecture value / requirement-strength interpretation; (6) ownership or source-of-truth interpretation; (7) rationale preservation or loss; (8) temporary/permanent or current/proposed status; (9) transfer-audit judgment; (10) a material absence claim; (11) finding existence, severity, confidence, or wording; (12) the Executive Summary / headline conclusions; (13) documentation-only recommendations; and (14) the ability to audit any of the above from evidence. When materiality is genuinely uncertain, the item MUST be treated as material until sufficient documentary inspection demonstrates otherwise. Specifically: an **absence claim** is material when it is used, or is being considered for use, as support for a finding, headline conclusion, value/drift/reversal/ownership/rationale claim, or recommendation; an **evidence anchor** is material when it supports or materially weakens a material analytical statement, decision, thread, transition, transfer audit, absence claim, finding, or current-state classification.
- **FR-053 (Authoritative execution-state reporting contract)**: `COMPLETE` is the only completion state, and the constitution §27 completion contract is preserved unchanged: a response may use `Status: COMPLETE` only after every constitution/spec completion gate has passed. The valid non-complete states are exactly `FAIL` and `PAUSED — awaiting challenge pack`. This requirement is the authoritative reporting-field contract; the plan references it and maintains no competing list.
  - A **FAIL** report MUST contain: (1) `Status: FAIL`; (2) current phase and task ID, if tasks exist; (3) the governing constitution/spec/plan gate that failed; (4) a concise observed condition, WITHOUT reproducing forbidden or contaminating content — for a Run-A contamination FAIL, the observed condition MUST NOT reveal Run-A contents or conclusions; (5) the last successfully completed phase/task; (6) the analytical output files already created or modified in this run; (7) BLIND state (not created / created and sealed / factual-correction append only); (8) challenge state (not reached / awaiting pack / received / challenge performed); (9) candidate/coverage reconciliation state, if candidate accounting has begun; (10) the canonical diff state for `git diff -- README.md AGENTS.md docs`; (11) the smallest safe next action or required USER action; and (12) the explicit statement `Completion is NOT claimed.`
  - A **PAUSED-at-BLIND-FREEZE** report MUST contain: (1) `Status: PAUSED — awaiting challenge pack`; (2) the frozen HEAD; (3) the completed phase / task ID; (4) the exact paths of existing analytical outputs; (5) the BLIND path and sealed status; (6) challenge state: `awaiting pack`; (7) the exact candidate / excluded / relevant / deep / semantic-scan / clerical / duplicate counts at freeze; (8) both reconciled FR-025 arithmetic identities; (9) the canonical diff state; and (10) the explicit statement `REPORT.md has not been finalized and completion is NOT claimed.` The pause report MUST NOT request or preview the hypothesis pack.

## Key Entities

Research entities (not software domain objects):

- **Candidate Documentary Commit**: a commit surfaced by FR-046 discovery; a DISCOVERY-SET member (not yet evidence of relevance) that resolves after content inspection to exactly one accounting outcome — a relevant Documentary Commit row in `COVERAGE.tsv` or an Exclusion Record (FR-047/FR-048).
- **Exclusion Record**: a `WORKING.md` Candidate Corpus Exclusion Ledger entry for an inspected candidate outside the BeautyQ research boundary; carries commit, date, documentary path(s), exclusion reason, and sufficient-inspection level; never a fifth analytical artifact and never a `clerical` COVERAGE disposition.
- **Documentary Commit**: a commit with documentary change accepted into the relevant corpus; identified by SHA, date, touched documentary paths, coverage disposition, associated decision threads, and evidence IDs.
- **Document Version**: the content state of one documentary file at one commit; the unit that participates in lineage.
- **Document Lineage**: a chain of creation/rename/copy/replacement/supersession/retirement linking document versions across history; supports duplicate and transfer classification.
- **Ref Inventory**: the compact, read-only record (in `WORKING.md`) of Git refs locally available at research start plus each ref's unique-documentary-substance assessment; it bounds "entire relevant history" to locally present refs (FR-051).
- **Evidence Anchor**: a citable piece of evidence with a unique ID, exactly one provenance class (`CD`/`RD`/`ND`/`CM`/`HI`/`RJ`), and exact commit/path location; referenced from COVERAGE rows, findings, and audits. Provenance is asserted per anchor and per atomic material analytical statement (constitution §11 v1.0.2); a finding is a composite of anchors and is not itself forced into one class (FR-026). Anchor materiality is governed by the single authoritative FR-052 definition.
- **Decision Thread**: an independently derived sequence of related documentary decisions along one separable dimension; records initial problem, first documentary decision, rationale, ownership, changes, requirement-strength changes, temporary/permanent framing, current documentary state, and unresolved contradictions.
- **Historical Transition**: a point where a thread's decision, value, status, ownership, or terminology changes; linked to evidence anchors; materiality per the authoritative FR-052 definition.
- **Transfer Audit**: the FR-037 chain record for one major deletion/retirement/consolidation/replacement/relocation.
- **Absence Claim**: a negative assertion about the corpus whose materiality follows FR-052, paired with its authoritative FR-028 absence-search record (ten required fields; constitution §13 minimum implemented, with FR-028 adding the stricter analytical-use and safe-wording fields).
- **Finding**: a final conclusion with an FR-049 severity (Critical only when all four FR-049 eligibility conditions hold; rarity is only a consequence) and an FR-050 confidence (independent axes, and not substitutes for provenance), one or more Evidence Anchors whose provenance classes are individually explicit (a finding may combine `CD`/`ND` evidence with `HI` reconstruction and stand as an `RJ` evaluation), supporting evidence, counter-evidence analysis, and (for retrospective claims) the FR-027 classification; High/Critical findings never rest only on `RD`/`CM`.

## Success Criteria *(mandatory)*

- **SC-001 (Exhaustive coverage with an auditable denominator)**: 100% of candidate documentary commits have exactly one accounting outcome (a relevant COVERAGE disposition or an exclusion-ledger record), and 100% of relevant documentation-changing commits have exactly one allowed COVERAGE disposition; candidate/excluded/relevant counts appear in the WORKING coverage summary, the REPORT Coverage Appendix, and the completion response.
- **SC-002 (Content-based classification)**: 0 relevant commits are classified solely from commit message, numstat, line churn, or filename, and 0 candidates are excluded without documentary content inspection (FR-048).
- **SC-003 (Exact arithmetic)**: Both identities hold exactly — `candidate_documentary_commits = relevant_documentary_commits + excluded_after_inspection` and `relevant_documentary_commits = deep + semantic-scan + clerical + duplicate` — with identical numbers in COVERAGE, WORKING, REPORT, and the completion response.
- **SC-004 (Transfer coverage)**: 100% of discovered major documentary retirements/transfers receive transfer audits.
- **SC-005 (Absence-claim coverage)**: 100% of material absence claims used in final findings have corresponding absence-search records.
- **SC-006 (Counter-evidence)**: 100% of High/Critical findings contain explicit counter-evidence analysis.
- **SC-007 (Blind independence and mandatory challenge)**: `BLIND.md` is created before the user-provided hypothesis pack and remains sealed afterward except for factual-correction appendices; execution halts at BLIND FREEZE awaiting the pack, and in this run Research Story 3 is performed on the pack before Research Story 4 or `REPORT.md` finalization — an awaiting-pack state is reported as paused, never as complete.
- **SC-008 (Provenance integrity)**: Important retrospective claims about earlier intent are distinguished from contemporaneous evidence in every finding that uses them, and every material evidence anchor and analytical statement carries its own explicit provenance class.
- **SC-009 (DOC-only integrity)**: 0 final conclusions depend on implementation/source/test/build/CI/runtime behavior.
- **SC-010 (Canonical immutability)**: At completion, `git diff -- README.md AGENTS.md docs` is empty.
- **SC-011 (Mechanical date/count validation)**: All exact historical counts and date spans in `REPORT.md` are mechanically recomputed from source ledgers before completion.
- **SC-012 (State classification)**: Current normative architecture, accepted limits, active plans, proposals, simplification proposals, and historical states are explicitly distinguished throughout the report.
- **SC-013 (Ref-universe confinement)**: 0 fetch/pull/remote-expansion/ref-mutation operations occur; the compact ref inventory recorded in `WORKING.md` shows that only refs locally present at research start were considered, and no report claim asserts audited remote history.
- **SC-014 (Severity/confidence discipline)**: Every final finding carries both an FR-049 severity and an FR-050 confidence rated independently, and 0 findings are rated from document length, module count, process complexity, or proof-machinery size alone.

## Out of Scope

The review explicitly excludes:

- code correctness; implementation verification; runtime verification; test correctness; CI correctness; backend implementation behavior;
- redesigning BeautyQ architecture; implementation recommendations;
- code patches; source changes; commits; branch creation; branch switching;
- any edit to canonical BeautyQ documentation during research;
- remote Git history not locally present at research start (no fetch/pull/remote expansion; FR-051).

Final recommendations are documentation-only.

## Assumptions

- "BeautyQ Search" and "Search Gen2" identify the documentary subject; their meanings are established from the corpus, not defined by this specification.
- A "relevant documentation-changing commit" is one whose documentary content falls within the FR-006/FR-011 boundary; relevance is established by inspection, marginal cases err toward inclusion with a recorded COVERAGE disposition, and inspected candidates found outside the boundary go to the Candidate Corpus Exclusion Ledger rather than any disposition (FR-047/FR-048).
- For BeautyQ Run B the adversarial hypothesis pack is user-supplied explicitly after the BLIND freeze and Research Story 3 is MANDATORY: execution stops at BLIND FREEZE until the pack arrives, and the review is not complete — `REPORT.md` is not finalized — until Story 3 has been performed (FR-040). The reviewer never requests the pack before freeze, anticipates it, reconstructs it, infers it from conversation/history, or searches for it.
- The Git-ref universe is the set of refs locally available in the repository at research start under the frozen baseline environment; non-primary refs (local branches, tags, reference/stash snapshots) may exist and are inspected read-only for unique documentary substance (FR-051).
- `.review-bundles/docs-history-speckit/` and its four outputs are created during the research execution phase only — never by this specification command; candidate-universe accounting, the exclusion ledger, the ref inventory, the severity/confidence rubrics, and challenge-phase state all live inside those existing outputs or this spec/plan, never as a fifth analytical artifact (FR-041).
- Where this research contract and the stock Spec Kit feature template conflict (branch workflow, product user stories, business value, technology choices), the constitution's supremacy clause resolves the conflict in favor of this research contract; those adaptations are intentional, not omissions.

## Constitution Alignment

This specification was drafted against constitution §§1–27, the Required Review Artifacts & Ledger Schema appendix, and the Verification Gates appendix, and now tracks constitution **v1.0.2**. It restates no weaker rule than the constitution, assumes no historical conclusion, and adds no evidence class, disposition, or writable location. The 2026-09-04 clarification pass added only constitution-compatible tightening: the mandatory Story 3 gate (constitution §27 requires BLIND-before-hypotheses; this run additionally requires the challenge itself before completion — stricter, not weaker), the candidate-universe denominator and WORKING.md Candidate Corpus Exclusion Ledger (auditable-accounting detail under §6/§8/§23–24 that keeps COVERAGE.tsv's constitution schema, four dispositions, and exact `relevant = deep + semantic-scan + clerical + duplicate` identity untouched and authoritative), the locally-present-refs confinement (§4 read-only), and methodology-only severity/confidence plus per-anchor/per-claim provenance application (§§11/18/25 semantics unchanged; all six classes preserved exactly). Constitution **v1.0.2** is a PATCH that resolves only the §11 provenance-UNIT wording (per material evidence anchor / per atomic material analytical statement; composite findings may combine classes; severity/confidence are not substitutes for provenance); it adds no class, removes no class, and changes no weighting, scope, or any other principle. This specification's corresponding pre-tasks dofix aligned to that patch: FR-026 states the anchor/statement unit, the new FR-052 supplies the single authoritative materiality definition, the new FR-053 supplies the authoritative FAIL/PAUSED/COMPLETE reporting contract, FR-028 reconciles the absence-record schema as the authoritative execution schema over the constitution §13 minimum (stricter, never weaker), and FR-049 replaces the subjective Critical cue with four objective eligibility conditions. No constitution weakening and no unresolved constitution conflict was found at specification, clarification, or dofix time.
