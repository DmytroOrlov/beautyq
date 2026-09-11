# Tasks: BeautyQ Distage/Izumi Leverage Audit (READ-ONLY RESEARCH EXECUTION)

**Feature**: `specs/003-beautyq-distage-izumi-leverage-audit`
**Input**: Design documents from `specs/003-beautyq-distage-izumi-leverage-audit/`
**Prerequisites**: `spec.md`, `plan.md`, `research.md`, `data-model.md`, `quickstart.md`, `checklists/requirements.md`
**Template adaptation**: This is a **read-only research contract**, not a code feature. Every task below is a *research execution task*; allowed actions are record, map, inventory, inspect, compare, assess, falsify, classify, synthesize, validate. Tasks must not implement, refactor, migrate, extract, delete, or open any other feature.

## Format: `[ID] [P?] Description with file path`

- **[P]**: can execute concurrently with another `[P]` task **without a same-file write race** (different files; no dependency on an incomplete task).
- **Same-file rule**: multiple tasks contributing to one markdown artifact are **never** `[P]`, even when they target distinct predeclared sections. Distinct markdown sections do **not** make concurrent filesystem writes safe; two contexts can read the same pre-write file and overwrite each other. Such tasks are serialized within a single writer lane/context.
- **Concurrency unit**: the unit of concurrency is a **writer lane** (one context per artifact), not individual same-file slices. See "Writer Lanes & Concurrency".
- No `[US#]` labels are used: this feature is organized by the plan's method groups (0 / A / B0 / B1 / C / D / E / F / Validation), not by user story. A traceability mapping to US1/US2/US3 and the eight standing questions is provided at the end.
- Every task names the single artifact section it writes or validates.

## Writer Lanes & Concurrency

- After T001, exactly **two writer lanes** may run concurrently because they write **different files**:
  - **LANE A — BeautyQ mapper** (writes only `research/01-beautyq-candidate-map.md`): `T002 → T003 → T004 → T005 → T006 → T007`, sequential in one writer context.
  - **LANE B0 — framework mapper** (writes only `research/02-framework-capability-map.md`): `T008 → T009 → T010 → T011`, sequential in one writer context.
  - Boundary: `LANE A || LANE B0`. This is the only concurrency in the graph before T012 and it is safe purely because the two lanes own different files.
- Only **T002** (LANE A entry) and **T008** (LANE B0 entry) carry `[P]`: those two tasks may start concurrently because they touch different files. Every other A/B0 slice task is serialized behind its lane predecessor and is not `[P]`.
- Every task after the lanes (`T012` onward) is part of a single ordered chain; no post-lane task is `[P]`.
- This design matches the intended runtime: two cheap contexts running concurrently, not many agents racing on shared markdown files. No extra durable artifacts are introduced to enable concurrency.

## Non-Authorization Boundary (applies to every task)

- Worktree-only. Tasks may write **only** the feature research artifacts under `specs/003-beautyq-distage-izumi-leverage-audit/research/` during execution.
- No task may stage, commit, stash, reset, clean, checkout/switch, or mutate refs/history. Pre-existing human-owned Git/index/worktree state is preserved exactly.
- No task may edit production, test, build, project, or framework source. Framework inspection is read-only.
- Findings and recommendations authorize no change. No recommendation becomes a task in this feature. Feature 004 is not opened or required, and no Spec Kit phase is chained.
- Absence of evidence is `BLOCKED_NEED_EVIDENCE`, never a passing conclusion.

---

## Phase 1: Group 0 — Run Opener / Attribution

**Purpose**: Deterministically bind every later finding to the studied BeautyQ source state, and to the **actually resolved** Distage/Izumi version (not merely the declared coordinate). Must not require a clean repository; records the actual human-owned starting state truthfully.

- [x] T001 Record the run-start selected state, declared framework coordinates, and actually-resolved framework version in `specs/003-beautyq-distage-izumi-leverage-audit/research/00-selected-state.md`.
  - Source-state inputs (read-only): `git rev-parse HEAD`, `git status --short`, `git diff --stat`, `git diff --cached --stat`. Record `SourceStateRecord` fields per `data-model.md`: `headSha`, `worktreeClean`, `statusPorcelain`, `diffStat`, `cachedDiffStat`, `stateId` (`<short-sha>-clean` / `<short-sha>-dirty`), `recordedAt`. Do not require or force a clean worktree.
  - Declared coordinates: derive the declared/pinned `io.7mind.izumi` coordinates, version, and artifact set from `build.sbt` / `project/*.sbt` / `project/build.properties`. Declaration/override alone is not resolution proof.
  - Actually-resolved version: verify the **resolved** version with **one focused, read-only sbt dependency-resolution query/report appropriate to this build** (its exact form is not hard-coded here; use the build's existing focused dependency-report mechanism). Record the exact command/source and the observed resolved version. Do not run a broad compile or test campaign, and do not add a lockfile, dependency lock, or resolution subsystem.
  - Authority: if declared and resolved differ, record the **resolved** version as authoritative for all framework claims and record the discrepancy explicitly. Record `FrameworkVersionReference` fields (`groupId`, `version`, `artifacts`, `evidenceSource`, `escalations`, `verifiedAt`).
  - Escalation only if resolution remains ambiguous: local cache/source jars for the verified resolved version, then the matching upstream release/tag. A cache directory merely existing is **not** resolution proof; local artifacts may be inspected only after that version is verified.
  - Do not create any baseline/digest/freeze/promotion ceremony.
  - Exit: source-state and framework records present with declared versus resolved distinguished; `stateId` derived from actual commands; exact resolution command and observed result recorded.

---

## Phase 2: Group A — BeautyQ-Local Mapping (read-only, no classification)

**Purpose**: Enumerate current **BeautyQ-local** infrastructure/mechanics worth evaluating, with `file:line` anchors and recurrence, without judging or classifying them. LANE A runs as one sequential writer context and may run concurrently with LANE B0 (different file).

Each slice writes **only** its own predeclared `## Slice A<n> — <name>` section of `research/01-beautyq-candidate-map.md`. Each row follows the `CandidateArea` / `CandidateSite` schema in `data-model.md` (`areaId`, `areaStatus`, `areaBasis`, `candidateId`, `ownerModule`, `anchors`, `mechanicSummary`, `currentOwner?`, `recurrenceSites`, `stateId`). A slice MAY record `NO_CURRENT_MATERIAL_CANDIDATE` or `REJECTED` with a short basis. A slice MUST NOT assign any of the five classification labels and MUST NOT write recommendations. Deleted Wave-A/Wave-B machinery is not reconstructed.

- [x] T002 [P] Map app-shell / startup / composition mechanics in `research/01-beautyq-candidate-map.md` (section `## Slice A1`). LANE A entry; may start concurrently with T008.
  - Inspect `leaderboard-app-shell/src/main/scala/**` read-only: plugin/role composition, module graph, startup/activation composition, plugin include.
  - Candidate areas to surface (or reject with basis): startup / activation composition; graph-boundary / construction-boundary proofs realized at the app shell.
- [x] T003 Map BeautyQ wiring / lifecycle mechanics in `research/01-beautyq-candidate-map.md` (section `## Slice A2`). Depends on T002 (same LANE A, sequential).
  - Inspect `beautyq-search-gen2-wiring/src/main/scala/**` read-only: lifecycle / readiness state, resource construction seams, readiness edges, weak sets.
  - Candidate areas: lifecycle / readiness state; optional dependency / resource construction at wiring seams.
- [x] T004 Map BeautyQ-owned backend/search/runtime adaptation and composition around reusable Gen2 mechanics in `research/01-beautyq-candidate-map.md` (section `## Slice A3`). Depends on T003 (same LANE A, sequential).
  - Inspect `search-gen2-*/src/main/scala/**` read-only **as ownership/boundary context** for BeautyQ-owned use, adapters, wrappers, composition, and runtime mechanics.
  - Scope guard: a generic `search-gen2-*` kernel/backend type is **not** itself a BeautyQ-local candidate merely because BeautyQ consumes it. Promote a candidate only when there is a **BeautyQ-owned** local mechanic/wrapper/composition/proof whose framework leverage is actually in question.
  - Candidate areas (restricted to BeautyQ-owned sites): reusable domain / kernel boundaries; optional dependency / resource construction; operator-visible degradation / status. Do not duplicate T003/T005 candidates.
- [x] T005 Map contract / materialization / kernel boundary mechanics in `research/01-beautyq-candidate-map.md` (section `## Slice A4`). Depends on T004 (same LANE A, sequential).
  - Inspect `beautyq-search-gen2-contract/src/main/scala/**` and `beautyq-search-gen2-materialization/src/main/scala/**` read-only: declaration/contract boundaries, materialization/lifecycle seams, boundary proofs.
  - Candidate areas: graph-boundary or construction-boundary proofs; generation / source identity; immutable evidence / source attribution.
- [x] T006 Map BeautyQ-specific project edges, dependency/firewall constraints, and boundary mechanics in `research/01-beautyq-candidate-map.md` (section `## Slice A5`). Depends on T005 (same LANE A, sequential).
  - Inspect `build.sbt`, `project/*.sbt`, and the shared modules `repo-core`, `leaderboard-core`, `app-http`, `app-services` read-only **as graph endpoints/context**.
  - Scope guard: do **not** audit `repo-core`, `leaderboard-core`, `app-http`, or `app-services` generically; map only BeautyQ-specific project edges, dependency/firewall constraints, and boundary mechanics.
  - Candidate areas: ownership / firewall constraints; build/module boundary mechanics.
- [x] T007 Merge LANE A slices into the consolidated candidate index in `research/01-beautyq-candidate-map.md`.
  - Depends on T002–T006 (LANE A complete). Reconcile the five slice sections into one table; assign/validate unique stable `candidateId`s; ensure every spec candidate area is `MAPPED`, `NO_CURRENT_MATERIAL_CANDIDATE`, or `REJECTED` with a basis; verify anchors resolve at the `stateId` from T001.
  - Exit: every material candidate has a stable `candidateId`, an owner module, resolving `file:line` anchors, and recurrence sites. No classification labels present.

---

## Phase 3: Group B0 — Pinned-Framework Broad Inventory (read-only, no classification)

**Purpose**: Inventory what the *actually resolved* Distage/Izumi version provides, by bounded framework theme. LANE B0 runs as one sequential writer context and may run concurrently with LANE A (different file). B0 is seeded by the spec's Candidate Research Areas and known framework themes; it does not need A candidate IDs. Latest upstream/`master` MUST NOT be used as authority.

Each slice writes **only** its own predeclared `## B0<x> — <theme>` section of `research/02-framework-capability-map.md`, using the `FrameworkTheme` / `FrameworkSurface` schema in `data-model.md` (`frameworkThemeId`, `surfaceId`, `surfaceSummary`, `apiNames`, `surfaceEvidence`, `versionRef`, `matchedNeedIds` left empty at B0). Every surface is grounded in a reference to the version verified in T001.

- [x] T008 [P] Inventory B0-a module / plugin / role composition and activation/axes surfaces in `research/02-framework-capability-map.md` (section `## B0a`). LANE B0 entry; may start concurrently with T002.
- [x] T009 Inventory B0-b lifecycle / resource construction and optional-dependency surfaces in `research/02-framework-capability-map.md` (section `## B0b`). Depends on T008 (same LANE B0, sequential).
  - Cover `fromResource` / `Lifecycle` / `Resource` and optional dependency construction.
- [x] T010 Inventory B0-c weak sets / roots / graph GC, graph/construction boundary proofs, and integration checks / readiness surfaces in `research/02-framework-capability-map.md` (section `## B0c`). Depends on T009 (same LANE B0, sequential).
- [x] T011 Inventory B0-d config, docker framework, testkit, BIO/effect abstractions, and package/ownership helpers in `research/02-framework-capability-map.md` (section `## B0d`). Depends on T010 (same LANE B0, sequential).
  - Themes: `distage-extension-config`, `distage-framework-docker`, `distage-testkit-scalatest`, BIO/effect/lifecycle abstractions, package/ownership helpers.

---

## Phase 4: Group B1 — Post-A Focused Gap Fill (bounded)

**Purpose**: Reconcile A's actual material needs against B0 and fill only the missing focused pinned-version lookups. This is NOT a second broad framework inventory.

- [x] T012 Reconcile A needs against B0 and append bounded gap-fill evidence to `research/02-framework-capability-map.md`.
  - Depends on T007 (LANE A merged) and T011 (LANE B0 complete).
  - For each material candidate's need: populate `matchedNeedIds` where a B0 surface already covers it; where uncovered, perform only the missing focused pinned-version lookup and record either the matching surface or a search-bounded `absenceBasis`; normalize `surfaceId` references.
  - No-op/skip for needs already covered by B0. Do not expand into framework-wide archaeology.
  - Phase C may not complete for a candidate until its B1 gap fill is done.

---

## Phase 5: Group C — Need-to-Surface Comparison (no headline classification)

**Purpose**: Compare each local mechanic to its nearest pinned-version surface at source/API level. C records `DIRECT_USE` / `LOCAL_DUPLICATION_OR_BYPASS` / `MISSING_COMPOSITION` / `NO_RELEVANT_PRIMITIVE` (per `data-model.md` `ComparisonRecord`) but does NOT assign the five-way headline label. One writer context; T013 and T014 are sequential.

- [x] T013 Write the C1 comparison section in `research/03-comparison-and-discoverability.md` (section `## C1`).
  - Depends on T012. Consumes candidates from A slices A1–A3 and the B0/B1 surfaces from `research/02-*`.
- [x] T014 Write the C2 comparison section in `research/03-comparison-and-discoverability.md` (section `## C2`). Depends on T013 (sequential, same file).
  - Consumes candidates from A slices A4–A5 and the B0/B1 surfaces from `research/02-*`.
  - Exit (T013+T014): every promoted candidate has a nearest-surface finding with evidence refs, or a documented search-bounded absence.

---

## Phase 6: Group D — Discoverability (separate from genuinely-missing)

**Purpose**: Where a real pinned-version surface exists, judge whether a competent developer or agent could discover and compose it correctly from current API names, docs, examples, and tests. `HARD_TO_DISCOVER` is never evidence of `MISSING_GENERIC_PRIMITIVE`.

- [x] T015 Write the discoverability section in `research/03-comparison-and-discoverability.md` (section `## D`). Depends on T014.
  - Consumes the C comparison sections plus pinned-version docs/examples/tests.
  - Record `discoverability` (`DISCOVERABLE` / `HARD_TO_DISCOVER` / `INDETERMINATE` / `N_A`), `discoverabilityBasis`, and `remedyCandidates` (`DOCS` / `EXAMPLES` / `NAMING` / `HELPER_FACADE` / `NONE`) per candidate.
  - Do not turn "hard to find" into "primitive missing".

---

## Phase 7: Group E — Adversarial Classification (central adjudication)

**Purpose**: Assign every current material candidate an explicit current research state, after actively hunting counterexamples to the tempting label. This group stays central; it is not split into many cheap classification tasks.

- [x] T016 Assign the current research state and single headline label per material candidate in `research/04-classified-findings.md`.
  - Depends on T015. Consume only the compact A/B/C/D artifacts.
  - Per `data-model.md` `Classification`: `status` ∈ `CLASSIFIED` | `BLOCKED_NEED_EVIDENCE` | `INDETERMINATE` | (`SUPERSEDED` where applicable). `CLASSIFIED` carries exactly one of `WELL_USED` / `UNDERUSED` / `HARD_TO_DISCOVER` / `MISSING_GENERIC_PRIMITIVE` / `BEAUTYQ_SPECIFIC`; `BLOCKED_NEED_EVIDENCE` and `INDETERMINATE` carry no headline label and name the exact missing/required evidence.
  - For each tempting label, run the counterexample search from `plan.md` (WELL_USED ↔ bypass/duplication; UNDERUSED ↔ documented local reason; HARD_TO_DISCOVER ↔ confirm a real surface exists; MISSING_GENERIC_PRIMITIVE ↔ equivalent composition + all eight genericity conditions + recurrence + second consumer; BEAUTYQ_SPECIFIC ↔ de-named reusability test).
  - Apply the eight-condition genericity filter (`genericityResult`) to every `MISSING_GENERIC_PRIMITIVE`; otherwise downgrade naming the failing condition. Record `secondarySignal` without double-counting headline labels.
  - No recommendation is emitted here.
- [x] T017 Add the symmetric-search calibration and downgrade check to `research/04-classified-findings.md` (section `## Calibration`). Depends on T016.
  - For any one-sided distribution (zero `WELL_USED`, zero `BEAUTYQ_SPECIFIC`, or zero change-oriented), record an explicit calibration check showing the opposite outcome class was genuinely investigated, not skipped.
  - Verify no candidate holds two headline labels or none while `CLASSIFIED`; verify blocked/indeterminate rows carry no label; retain prior labels only as historical evidence for `SUPERSEDED` and do not consume them as current.

---

## Phase 8: Group F — Recommendations then Synthesis (central adjudication; non-authoritative)

**Purpose**: Produce the primary human deliverable. Recommendations are materialized **before** the synthesis so the synthesis answers the recommendation-oriented standing questions from an actual recommendation set, not by predicting it.

- [x] T018 Write the evidence-graded `## Recommendations` section in `research/05-synthesis-and-recommendations.md`. Depends on T017.
  - Derive recommendations from the current classified findings in `research/04-*`; emit only after counterexamples and the genericity filter were considered in Group E.
  - Follow the `Recommendation` schema in `data-model.md`: `recommendationId`, `candidateId`, `kind` (`DOCS_EXAMPLES` / `FRAMEWORK_API_CODE`), `statement`, `evidenceRefs`, `confidence`, `secondConsumer?`, `genericityResult?`, `nonAuthoritative=true`.
  - Distinguish docs/examples remedies from framework API/code recommendations; every primitive proposal names a second consumer; every recommendation states evidence/confidence/uncertainty and remains visibly non-authoritative. Opens no work item.
- [x] T019 Write the primary `## Synthesis` section in `research/05-synthesis-and-recommendations.md`. Depends on T018.
  - Consume classifications from `research/04-*` plus the now-materialized Recommendations section; avoid raw whole-repository context.
  - Answer, in prose, all eight standing questions from `spec.md` (what the framework already provided; what BeautyQ used well; what BeautyQ rebuilt unnecessarily, if anything; which misses were discoverability; which generic primitives are actually missing, if any; what should remain BeautyQ-specific; which recommendations are docs/examples vs framework API/code; which have enough evidence for a separately authorized follow-up).
  - Because recommendations already exist, questions 7 and 8 summarize the actual recommendation set (kind and evidence grade) rather than prefiguring it.
  - This synthesis is the primary human deliverable; no recommendation becomes a task in this feature.

---

## Phase 9: Final Validation (focused research-output checks only)

**Purpose**: Validate research-output properties only. No full test suite, no product CI campaign, no implementation validation. A tiny read-only dependency query is allowed only as framework evidence. Single writer context; T020→T021→T022→T023 are strictly sequential.

- [x] T020 Validate source anchors, framework-version binding, and negative-claim coverage; write `## Validation: Evidence` in `research/05-synthesis-and-recommendations.md`. Depends on T019.
  - Check: every cited BeautyQ anchor resolves at the `stateId` in `research/00-selected-state.md`; every framework claim cites the actually-resolved pinned version (no latest upstream/memory); every documented-absence or `MISSING_GENERIC_PRIMITIVE` claim records a search/counterexample basis; unresolved absence is `BLOCKED_NEED_EVIDENCE`, not "missing"; a lone accidental site did not yield `MISSING_GENERIC_PRIMITIVE`.
- [x] T021 Validate classification-state integrity; write `## Validation: Classification` in `research/05-synthesis-and-recommendations.md`. Depends on T020 (sequential, same file).
  - Check: every current material candidate has an explicit research state; every `CLASSIFIED` row has exactly one headline label with BeautyQ-usage + framework-version-anchored evidence; `BLOCKED_NEED_EVIDENCE`/`INDETERMINATE` rows carry no headline label and name missing/required evidence; `SUPERSEDED` labels are historical only; `HARD_TO_DISCOVER` rows have a real pinned surface; the genericity filter is applied to all `MISSING_GENERIC_PRIMITIVE` rows.
- [x] T022 Validate calibration, synthesis completeness, and recommendation discipline; write `## Validation: Synthesis` in `research/05-synthesis-and-recommendations.md`. Depends on T021 (sequential, same file).
  - Check: one-sided distributions carry the calibration check; `research/05-*` alone answers all eight standing questions; recommendations are non-authoritative and kind-tagged; primitive proposals name a second consumer; none is phrased as accepted/implemented or opens a work item.
- [x] T023 Validate final Git/source/index/ref state against the run-start record; write `## Validation: State Integrity` in `research/05-synthesis-and-recommendations.md`. Depends on T022 (sequential, same file, final check).
  - Read-only `git rev-parse HEAD`, `git status --short`, `git diff --stat`, `git diff --cached --stat`, `git diff --check`; compare to `research/00-selected-state.md`.
  - Pass: no source/build/framework/index/history/ref mutation attributable to the audit; pre-existing human-owned state unchanged and still classified as pre-existing; the intended `research/00..05` writes are the only research-owned changes.

---

## Dependencies & Execution Order

### Writer lanes (the only pre-T012 concurrency)

- **LANE A (single writer, `research/01-*`)**: `T002 → T003 → T004 → T005 → T006 → T007`.
- **LANE B0 (single writer, `research/02-*`)**: `T008 → T009 → T010 → T011`.
- Boundary: `LANE A || LANE B0` — safe because the lanes write different files. No same-file tasks run concurrently.

### Ordered chain after the lanes

- `T012 → T013 → T014 → T015 → T016 → T017 → T018 → T019 → T020 → T021 → T022 → T023`.
- **T012**: waits for T007 (A merged) and T011 (B0 complete); appends to `research/02-*`.
- **T013**: after T012; **T014**: after T013; **T015**: after T014.
- **T016**: after T015; **T017**: after T016.
- **T018** (Recommendations): after T017; **T019** (Synthesis): after T018.
- **Validation T020–T023**: strictly sequential, `T020 → T021 → T022 → T023`, all after T019.

### Parallel-safe tasks

- Only **T002 and T008** are `[P]`: distinct files, no dependency, may start together after T001.
- No other task is `[P]`. In particular, same-file slice groups (T002–T006, T008–T011, T013–T014, T020–T023) are serialized within one writer context.

### Explicitly not parallel

- All of LANE A is one sequential context; all of LANE B0 is one sequential context.
- T013/T014 (same file), T020/T021/T022/T023 (same file), and the Group F/validation chain are sequential.
- A and B0 are NOT one A → B sequence: B0 does not depend on A candidate IDs, and no task serializes all of B after A.

## Parallel Execution Example

```text
# After T001, two writer lanes run concurrently (different files):
LANE A  (research/01):  T002 -> T003 -> T004 -> T005 -> T006 -> T007
LANE B0 (research/02):  T008 -> T009 -> T010 -> T011

# Then a single ordered chain (one writer at a time):
T012 -> T013 -> T014 -> T015 -> T016 -> T017 -> T018 -> T019 -> T020 -> T021 -> T022 -> T023
```

## Artifact Ownership

| Artifact | Writer tasks | Merge / serialization mechanism |
| --- | --- | --- |
| `research/00-selected-state.md` | T001 | single writer |
| `research/01-beautyq-candidate-map.md` | T002–T006 (distinct `## Slice` sections), T007 | one sequential writer lane (LANE A); T007 merges slices and assigns stable `candidateId`s |
| `research/02-framework-capability-map.md` | T008–T011 (distinct `## B0` sections), T012 | one sequential writer lane (LANE B0); T012 reconciles A needs and appends matched/absence evidence |
| `research/03-comparison-and-discoverability.md` | T013 (`## C1`), T014 (`## C2`), T015 (`## D`) | single writer context, sequential `T013 → T014 → T015` |
| `research/04-classified-findings.md` | T016 (`## Classified`), T017 (`## Calibration`) | single writer context, sequential `T016 → T017` |
| `research/05-synthesis-and-recommendations.md` | T018 (`## Recommendations`), T019 (`## Synthesis`), T020–T023 (distinct `## Validation:` sections) | single writer context, sequential `T018 → T019 → T020 → T021 → T022 → T023` |

No other durable files are created. No per-candidate files, no temporary durable slice files, no per-pass manifests, no digest/freeze/baseline artifacts, no evidence identity chains, no separate `contracts/` artifact.

## User-Story and Standing-Question Traceability

| Spec user story / output | Satisfied primarily by |
| --- | --- |
| US1 — every material candidate receives an explicit current research state: `CLASSIFIED` rows carry exactly one of the five labels, while `BLOCKED_NEED_EVIDENCE` and `INDETERMINATE` rows carry no headline label (P1) | T016, T017; validated by T021 |
| US2 — discoverability separated from genuinely-missing (P2) | T015, T016; validated by T020, T021 |
| US3 — human-readable synthesis with evidence-graded recommendations (P3) | T018 (recommendations), T019 (synthesis); validated by T022 |
| Eight standing questions | T019; validated by T022 |
| Source-state / framework-version binding (declared vs actually resolved) | T001; validated by T020, T023 |
| Symmetric search / calibration | T017; validated by T022 |
| Read-only / no Git mutation | enforced on every task; validated by T023 |

## Notes

- Scope per task is one bounded agent context; each lane's slices return to their designated artifact section and terminate.
- Concurrency is deliberately limited to `LANE A || LANE B0` (two cheap contexts); all other tasks are serialized to avoid same-file write races.
- Groups E and F are deliberately central adjudication passes and are not split into cheap per-candidate tasks.
- Every running context may start from `research/00-selected-state.md` and the prior artifact in the chain `01 → 02 → 03 → 04 → 05` without rereading the whole repository.
- Recommendations produced by Group F are output data only; this feature opens no follow-up work item.
