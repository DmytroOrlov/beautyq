# Research Execution Plan: BeautyQ Distage/Izumi Leverage Audit

**Feature directory**: `specs/003-beautyq-distage-izumi-leverage-audit` (Spec Kit feature directory; no Git branch is created or switched — ref/index mutation is human-owned under Constitution Principle I)

**Created**: 2026-09-10

**Spec**: `specs/003-beautyq-distage-izumi-leverage-audit/spec.md` (read-only research contract)

**Status**: Plan guide for the read-only research execution. This document schedules investigation and synthesis only. It does not execute the audit, authorize source/framework change, perform simplification, gate a simplification decision, or authorize/execute feature 004.

**Note**: This is the `/speckit.plan` output for a **research contract**. It intentionally excludes implementation task decomposition; `tasks.md` is not produced here. The audit itself runs only when the human separately invokes execution.

---

## Summary

Feature 003 answers one symmetric research question against the *current* BeautyQ Search Gen2 source and the *actually resolved* Distage/Izumi dependency: for each material BeautyQ-local infrastructure/mechanic, is the local work (a) an already-provided framework capability used correctly, (b) an underused/bypassed framework capability, (c) a real but hard-to-discover framework capability, (d) genuinely absent and genericity-filtered as a reusable primitive, or (e) correctly BeautyQ/domain-specific and best kept local?

The plan specifies six staged method phases (A–F), their dependency and parallel structure, a small durable artifact set, promotion/adversarial-falsification gates, blocked-evidence semantics, and audit completion criteria. No phase is named or scoped as implementation, migration, refactor, extraction, or cleanup execution; those may only appear as non-authoritative recommendations after classification.

---

## Research Context

**Object of study**: the boundary between reusable Distage/Izumi framework mechanics and BeautyQ-owned domain mechanics, as actually realized in the current BeautyQ Search Gen2 source (leadership board/search modules and their composition).

**Execution form**: user/operator-invoked read-only research. Mechanical mapping may also be performed by bounded read-only parallel agents inside that phase. No `/speckit.*` phase is discovered or chained from this plan.

**Evaluated BeautyQ source-state attribution (lightweight, no ceremony)**:
- Record `git rev-parse HEAD` and `git status --short` at run start.
- If the worktree is clean, the evaluated state is the committed revision.
- If the worktree is dirty, the evaluated state is `HEAD` plus the exact materialized worktree state; record changed paths from `git status --short` and `git diff --stat` (index state recorded separately with `git diff --cached --stat`), never collapsed into "HEAD".
- No `AcceptedEvaluationBaseline`, promoted baseline, `sourceRevision` slot, freeze/pass identity, digest chain, or evidence ceremony is recreated. A short human-readable `stateId` (e.g. `<short-sha>-clean` / `<short-sha>-dirty`) is sufficient.
- If the source state moves materially mid-audit, affected findings are marked `SUPERSEDED` and stay attributed to the state they were derived from; they are never silently reattributed. (FR-002, FR-015, Constitution V.)

**Pinned framework-version attribution**:
- The framework under audit is the project's actually-resolved `io.7mind.izumi` set as declared in `build.sbt` (`V.distage = "1.2.25"`, `V.logstage = "1.2.25"`), covering `distage-core`, `distage-framework`, `distage-extension-config`, `distage-framework-docker`, `distage-testkit-scalatest`, and `logstage-adapter-slf4j`.
- Version attribution is verified at execution against `build.sbt` and `project/*.sbt` (no override is currently present). Latest upstream/`master` behavior MUST NOT be substituted for the pinned version.
- If the resolved version differs at execution, findings name the actually-resolved version and existence/non-existence claims are re-checked against it.

**Cheapest trustworthy framework-source order** (stopping as soon as the claim is established):
1. Repository dependency coordinates/resolution: `build.sbt`, `project/*.sbt`, `project/build.properties`.
2. Locally available framework artifacts/source attachments for version `1.2.25` (Coursier/Ivy cache source jars, then class jars).
3. Pinned-version upstream source/docs/examples at the matching release/tag — never latest/`master`.
4. Only if 1–3 leave the claim ambiguous, a single focused, read-only sbt dependency query (e.g. resolved `libraryDependencies`/`evicted` for the owning project) as research evidence. This is not a test campaign and does not compile product code.

**Evidence sources in scope (read-only)**: BeautyQ source/tests; Distage/Izumi source/APIs/tests/examples for the pinned version; local docs (`docs/**`); dependency/version data; relevant framework docs.

**Context budget / decomposition**: Mapping work is partitionable across two bounded read-only mapper contexts that each return a compact artifact rather than raw repository context: a BeautyQ-source mapper (Phase A) and a framework-surface mapper (Phase B0), which can start concurrently. After A completes, a small bounded Phase B1 gap-fill reconciles A's actual needs against B0 and records only missing focused lookups. Adversarial classification and synthesis are reserved for a stronger adjudication pass over compact artifacts.

**Out of scope**: any source/build/framework edit, simplification execution or approval, second production domain, feature 004 execution, constitution change, and any reopening of the completed `001` documentation-history review or the completed Wave A/B cleanup.

---

## Constitution Check

*GATE: evaluated before Phase 0 and re-checked after Phase 1 design. This feature creates research artifacts only, so the gates below constrain the plan, not a code change.*

| Principle | Gate | Status |
| --- | --- | --- |
| I — Human-owned Git/index | Plan performs no commit/stage/stash/ref mutation; reports actual `HEAD`/index/worktree. | PASS |
| II — One canonical owner | Plan navigates to current owners; does not restate mutable policy. Findings resolve disagreements at the owner, never by editing owners. | PASS |
| III — Completion names its object | Audit completion is defined against its own synthesis, not downstream decisions. | PASS |
| IV — Evidence is not permission | Findings may recommend; they authorize no change and name the achieved vs missing state. | PASS |
| V — Evidence binds to evaluated state | Every finding carries source-state + framework-version refs; drift marks findings superseded. | PASS |
| VI — No fake green | Blocked/unlocatable framework evidence is `BLOCKED_NEED_EVIDENCE`, never a passing conclusion. | PASS |
| VII — Domain policy vs reusable mechanics | The audit's core distinction; boundary conflicts are reported, not hidden. | PASS |
| VIII — Proposals are not accepted architecture | `MISSING_GENERIC_PRIMITIVE` and all recommendations stay visibly non-authoritative. | PASS |
| IX — Minimal governance | Small artifact set; no new baseline/freeze/digest ceremony. | PASS |
| XI — Truthful validation scope | Validation proves research-output properties, not product behavior; scope stated exactly. | PASS |
| XII — Smallest coherent change | Plan is bounded to research artifacts in the feature directory. | PASS |

**Result**: PASS. No Complexity Tracking entries required. Re-checked after Phase 1: still PASS (no artifact introduces implementation authority or duplicated mutable policy).

---

## Research Method Phases (A–F)

Later phases consume earlier artifacts. No classification is made before phase E; no recommendation before phase F.

### Phase A — BeautyQ local-mechanics mapping (no judgment)

- **Input**: selected source-state record; current BeautyQ source/tests.
- **Activity**: enumerate current BeautyQ-local infrastructure/mechanics sites that are worth evaluating, without classifying them. Candidate mechanical areas are the spec's hypotheses: lifecycle/readiness state; optional dependency/resource construction; generation/source identity; graph-boundary or construction-boundary proofs; operator-visible degradation/status; immutable evidence/source attribution; ownership/firewall constraints; startup/activation composition; reusable domain/kernel boundaries. Include Distage/Izumi usage points (plugin/module composition, `fromResource`/`Lifecycle`, readiness edges, weak sets, axes/activation, docker plugins, config modules, integration checks, boundary/firewall proofs, BIO/effect types) grounded in the selected state.
- **Explicit non-activity**: do not resurrect deleted Wave-A/Wave-B mechanisms merely because older documents name them; record `NO_CURRENT_MATERIAL_CANDIDATE` or `REJECTED` with a short basis per area where cleanup already removed the relevant local mechanism.
- **Output**: `research/01-beautyq-candidate-map.md` (candidate inventory/source map, not a verdict).
- **Owner role**: BeautyQ-source mapper.
- **Parallelizable**: yes — slice by module/layer (e.g. app-shell composition; wiring/lifecycle; backend/search; contract/materialization; build/module firewall).
- **Exit criteria**: every candidate area is either mapped with source anchors or rejected with a basis; each material candidate has a stable `candidateId`, owner module, `file:line` anchors, and recurrence sites.

### Phase B — Framework capability inventory for the pinned version (no judgment)

Phase B has two small steps sharing one artifact.

**B0 — broad pinned-framework capability inventory (parallel with A)**
- **Input**: verified `FrameworkVersionReference`; the spec's Candidate Research Areas and known framework themes/categories. It does **not** need candidate IDs from A.
- **Activity**: inventory what the pinned Distage/Izumi version actually provides, by framework theme/capability, across APIs, source, relevant tests, and examples/docs. Cover the bounded themes relevant to the candidate areas (module/plugin/role composition; activation/axes; lifecycle/resource construction; weak sets/roots/GC; integration checks/readiness; docker framework; config; testkit; BIO/effect/lifecycle abstractions; package/ownership helpers). Ground each surface in a pinned-version reference.
- **Explicit non-activity**: do not substitute latest upstream behavior; do not assert existence/absence from memory; do not expand into an exhaustive framework inventory unrelated to BeautyQ — the seed is the bounded candidate areas/framework themes.

**B1 — bounded post-A gap fill (after A completes)**
- **Input**: A's `01-beautyq-candidate-map.md` and B0's inventory in `02-framework-capability-map.md`.
- **Activity**: reconcile A's actual material needs against B0. If A exposes a need not covered by B0, perform only the missing focused pinned-version framework lookup and append/fill that evidence in the **same** `research/02-framework-capability-map.md`. No seventh artifact and no separate B0/B1 bureaucracy.
- **Output**: `research/02-framework-capability-map.md` (framework surfaces by theme, with A-need matches reconciled where A has completed).
- **Owner role**: framework-surface mapper.
- **Parallelizable**: B0 runs concurrently with A; B1 is a bounded follow-up that cannot complete until A does.
- **Exit criteria**: every B0 theme is grounded in a pinned-version reference; every material need A surfaced is either covered by a B0 surface or has a search-bounded negative result appended at B1. Phase C cannot complete for a candidate until its B1 gap fill is done.

### Phase C — Source-level comparison (no classification)

- **Input**: phase A candidate map (01) and phase B0/B1 capability map (02).
- **Activity**: compare each local mechanic to its nearest pinned-version framework surface at source/API level, capturing enough evidence to distinguish: direct correct use; local duplication/bypass; missing composition (surface exists but is not composed); no relevant primitive.
- **Output**: `research/03-comparison-and-discoverability.md` (phase C portion).
- **Owner role**: comparison/adversarial reviewer.
- **Depends on**: A + B0 (+ any B1 gap fill). **Parallelizable**: internally by candidate group; cannot start before A and the relevant B0/B1 surfaces exist.
- **Exit criteria**: every promoted candidate has a nearest-surface finding or a documented absence, each with evidence refs.

### Phase D — Discoverability assessment (no classification)

- **Input**: phase C artifact plus framework docs/examples/tests for the pinned version.
- **Activity**: where a real framework surface exists, separately judge whether a competent developer or agent could realistically discover and compose it correctly from current API names, documentation, examples, and tests.
- **Output**: phase D portion of `research/03-comparison-and-discoverability.md`.
- **Owner role**: comparison/discoverability pass.
- **Depends on**: C. **Exit criteria**: each existing surface carries a `DISCOVERABLE` / `HARD_TO_DISCOVER` / `INDETERMINATE` assessment with a discoverability basis. `HARD_TO_DISCOVER` is never evidence of `MISSING_GENERIC_PRIMITIVE`.

### Phase E — Adversarial classification (one current research state per material candidate)

- **Input**: phases A–D artifacts.
- **Activity**: assign each material candidate an explicit current research state. A candidate whose evidence supports a classification is `CLASSIFIED` with exactly one of `WELL_USED` / `UNDERUSED` / `HARD_TO_DISCOVER` / `MISSING_GENERIC_PRIMITIVE` / `BEAUTYQ_SPECIFIC`, chosen after actively hunting counterexamples to the tempting label. A candidate whose required evidence is missing is `BLOCKED_NEED_EVIDENCE` (no headline label); a candidate whose evidence cannot separate two labels is `INDETERMINATE` (no headline label). Apply the genericity filter to any `MISSING_GENERIC_PRIMITIVE` candidate. Record secondary signals (e.g. underused *and* hard-to-discover) in the justification without double-counting headline labels. Record the symmetric-search calibration check for any one-sided distribution.
- **Output**: `research/04-classified-findings.md` (classifications + calibration).
- **Owner role**: adversarial classifier.
- **Depends on**: C + D. **Exit criteria**: FR-004/FR-005/FR-007 and SC-001..SC-004 hold; every material candidate has an explicit current research state, and every `CLASSIFIED` candidate has exactly one headline label with cited BeautyQ-usage and framework-version-anchored evidence; `BLOCKED_NEED_EVIDENCE` and `INDETERMINATE` candidates carry no headline label and name the missing/required evidence; blocked is never written as "missing".

### Phase F — Recommendation after counterexamples (non-authoritative)

- **Input**: phase E artifact.
- **Activity**: emit recommendations only after counterexamples and the genericity filter are considered. Each recommendation stays visibly a recommendation, names its evidence, states confidence/uncertainty, is tagged `docs/examples` or `framework API/code`, and — if intended to inform a separately authorized follow-up — states its evidence grade and second consumer.
- **Output**: `research/05-synthesis-and-recommendations.md` (synthesis answering all eight standing questions + recommendations + validation section).
- **Owner role**: synthesis pass.
- **Depends on**: E. **Exit criteria**: all eight standing questions answered; no recommendation phrased as accepted/implemented; no work item opened.

### Dependency and parallelism summary

```text
        (selected-state record: 00)
           |                |
        Phase A (01)   Phase B0 (02)     [parallel, cheap-context slices]
           |                |
           └──── Phase B1 gap-fill ─────┘   (after A; appends to 02)
                          |
                       Phase C            (joins A needs to B0/B1 surfaces)
                          |
                       Phase D            (needs C)
                          |
                       Phase E            (needs C + D; adversarial)
                          |
                       Phase F            (needs E; synthesis)
```

- A and B0 may run concurrently in separate bounded contexts.
- B1 gap-fill starts only after A completes and appends to the same `02-framework-capability-map.md`; Phase C cannot complete for a candidate until its B1 fill is done.
- C joins actual A needs to the B0/B1 surfaces; D depends on C; E depends on C + D; F depends on E. D may be folded into C's pass only if ownership stays explicit; E/F stay with stronger adjudication.

---

## Durable Research Artifacts

All durable artifacts live under `specs/003-beautyq-distage-izumi-leverage-audit/research/`. The set is intentionally small and purpose-separated so a later empty-context researcher can resume without rereading the whole repository. Each artifact header binds it to the `stateId` and resolved framework version it was derived from.

| Path | Question it answers | Writer (role) | Consumer | Becomes stale when |
| --- | --- | --- | --- | --- |
| `research/00-selected-state.md` | Which exact BeautyQ source state and which resolved Distage/Izumi version does every finding bind to? | Run opener (deterministic) | Every later phase; validation | `HEAD` or the materialized worktree changes; resolved framework version changes |
| `research/01-beautyq-candidate-map.md` | Which current BeautyQ-local infrastructure/mechanics are worth evaluating, and where are they? | BeautyQ-source mapper | C, E, F, synthesis | Selected source state changes |
| `research/02-framework-capability-map.md` | What does the pinned framework version actually provide, by theme (B0), and which A needs are matched (B1)? | Framework-surface mapper | C, D, E, F | Pinned framework version changes |
| `research/03-comparison-and-discoverability.md` | How does each local mechanic compare to the nearest pinned surface, and is that surface discoverable/composable? | Comparison/discoverability pass | E, F, synthesis | Phase A or B artifact superseded |
| `research/04-classified-findings.md` | What is the current research state per material candidate and, when CLASSIFIED, its single evidence-backed classification, with calibration and genericity-filter result? | Adversarial classifier | F, validation, synthesis | Source state or framework version changes |
| `research/05-synthesis-and-recommendations.md` | What are the answers to the eight standing questions, and which evidence-graded, non-authoritative recommendations follow? | Synthesis pass | Human coordinator/decision-maker; any later separately authorized work | Any finding is superseded or the source/framework state changes |

**No evidence bureaucracy**: no cryptographic digest chains, freeze/pass identities, promotion manifests, accepted-baseline ceremony, or one-file-per-stage bookkeeping beyond the six artifact purposes above. The `stateId` is a short human-readable label; the framework reference is a coordinate + version + evidence pointer.

---

## Candidate Promotion and Adversarial Falsification

**Candidate areas are hypotheses, not a backlog.** For each of the spec's candidate areas, execution may record `MAPPED`, `NO_CURRENT_MATERIAL_CANDIDATE`, or `REJECTED` with a basis. Deleted Wave-A/Wave-B machinery is not reconstructed to evaluate an old candidate.

**Promotion from mapping to classification** requires all of:
1. a stable `candidateId` with a current source anchor (`file:line`);
2. a recorded current owner (or an explicit note that no current owner exists);
3. either a nearest pinned-version framework surface (with reference) or a search-bounded absence result;
4. a discoverability assessment when a surface exists;
5. recurrence evidence and, for `MISSING_GENERIC_PRIMITIVE`, the genericity filter.

**Adversarial falsification before classifying** (equal skeptical effort in both directions):

| Tempting label | Counterexample search required before assigning it |
| --- | --- |
| `WELL_USED` | Look for a bypassed/duplicated framework surface or an unused capability. |
| `UNDERUSED` | Look for a documented BeautyQ-specific reason the local code exists. |
| `HARD_TO_DISCOVER` | Confirm a real pinned-version surface exists; if not, it is not a discoverability finding. |
| `MISSING_GENERIC_PRIMITIVE` | Look for equivalent framework composition; apply all eight genericity conditions; require recurrence beyond one accidental site and a plausible second consumer. |
| `BEAUTYQ_SPECIFIC` | Test whether the mechanic is genuinely reusable outside BeautyQ (de-named API, no product semantics, neutral testability). |

A lone accidental site may not be called a missing framework primitive; it is then classified on its own evidence (possibly `HARD_TO_DISCOVER`, `UNDERUSED`, or `BEAUTYQ_SPECIFIC`).

---

## Blocked and Insufficient-Evidence Semantics

- `BLOCKED_NEED_EVIDENCE`: a source, framework-version, or example check could not be completed. The candidate stays open with the exact missing evidence named. Blocked is never recorded as `MISSING_GENERIC_PRIMITIVE` or as a passing conclusion.
- An existence/non-existence framework claim without a pinned-version reference is not-yet-established and cannot support a recommendation.
- An indeterminate classification (evidence insufficient to separate two labels) is reported with the additional evidence required; no label is chosen to fill a slot.
- Inability to reach framework/material sources is blocked verification, not product failure and not a finding.

---

## Completion Criteria

The audit is complete when its own standing questions are answered with evidence:

1. All six artifacts exist and are internally consistent.
2. Every material candidate has an explicit current research state: `CLASSIFIED` with exactly one headline label plus BeautyQ-usage and framework-version-anchored evidence, or `BLOCKED_NEED_EVIDENCE`/`INDETERMINATE` (no headline label) with the exact missing evidence, or an explicit `REJECTED` area with basis.
3. The synthesis answers all eight standing questions in prose.
4. Any one-sided classification distribution carries a calibration check showing the opposite outcome class was genuinely investigated.
5. Every `MISSING_GENERIC_PRIMITIVE` recommendation either shows the genericity filter substantially satisfied or is downgraded with the failing condition named.
6. Open/blocked items are stated where completion claims appear.

Completion does **not** require a change disposition, feature 004 completion, framework implementation, BeautyQ refactoring, a product-value experiment, or any source modification.

---

## Non-Authorization Boundary

- No phase is named or scoped as implementation, migration, refactor, extraction, or cleanup execution.
- The audit authorizes no BeautyQ source change, no Distage/Izumi change, no simplification performance/approval/gating, and no feature-004 execution.
- Feature 004 is not a prerequisite; this audit opens no work item.
- Recommendations remain recommendations and are never phrased as accepted/implemented.
- All work is worktree-only; no agent commit/stage/stash/ref mutation; `HEAD`/index/worktree state is reported truthfully.

---

## Validation Strategy (read-only, no CI campaign)

Because 003 changes no product code, validation proves properties of the research output, not product behavior. It does **not** plan implementation CI, a repository test campaign, or a full-suite run.

| Check | Method | Pass condition |
| --- | --- | --- |
| Source references exist | Open each cited BeautyQ anchor in the selected state | Every `file:line` resolves at the recorded `stateId` |
| Framework-version evidence matches the pin | Re-read `build.sbt`/`project/*.sbt`; confirm each framework claim's version reference | No claim relies on latest upstream or memory |
| Classification states carry required evidence | Audit `research/04-*` against the five labels and the state model | Every material candidate has an explicit current state; 100% of `CLASSIFIED` candidates have exactly one label + evidence; `BLOCKED_NEED_EVIDENCE`/`INDETERMINATE` rows carry no headline label |
| Negative claims have coverage | Inspect the search/counterexample basis for each absence claim | Absence claims are search-bounded; discoverability is not promoted to "missing" |
| Genericity filter applied | Inspect every `MISSING_GENERIC_PRIMITIVE` row | Eight conditions recorded, second consumer present, or downgraded |
| Synthesis answers standing questions | Read `research/05-*` alone | All eight questions answered without reading the raw inventory |
| Calibration present | Inspect one-sided distributions | Explicit opposite-class calibration recorded |
| Git/source not mutated by research | Compare `git status --short`, `git diff --stat`, `git diff --check` to the run-start record in `research/00-selected-state.md` | No new source/build/framework/index/history/ref mutation attributable to the audit; pre-existing human-owned status unchanged and still classified as pre-existing; the intended `research/00..05` writes are the only research-owned changes |

A tiny read-only sbt dependency query (or framework source/test inspection) may be used as framework evidence when needed; it is not a test campaign. Broader confidence beyond these focused checks is reported as the exact command for separate user execution.

---

## Cheap-Model Decomposition

Later execution need not hold the whole repository/framework audit in one context. Roles (not vendor/model names) and their compact returns:

| Slice | Independence | Compact artifact returned | Reserved for stronger pass? |
| --- | --- | --- | --- |
| Selected-state record | Deterministic, first | `00-selected-state.md` | No |
| Phase A source mapper(s) | Independent per module/layer; parallel with B0 | `01-beautyq-candidate-map.md` (candidates + anchors, no verdicts) | No (bounded mechanical) |
| Phase B0 framework-surface mapper(s) | Independent per framework theme; parallel with A | `02-framework-capability-map.md` (surfaces by theme) | No (bounded mechanical) |
| Phase B1 gap fill | Bounded; after A | appends missing-surface evidence to `02-framework-capability-map.md` | No (bounded mechanical) |
| Phase C comparison | Needs A + B0; relevant B1 gap-fill must complete before candidate comparison | `03-*` comparison section | Partly — pairing judgments |
| Phase D discoverability | Needs C | `03-*` discoverability section | Partly |
| Phase E adversarial classification | Needs C + D | `04-classified-findings.md` | **Yes** — adjudication |
| Phase F synthesis | Needs E | `05-synthesis-and-recommendations.md` | **Yes** — adjudication + calibration |

A and B0 can start concurrently across two cheap contexts; B1 is a small bounded follow-up after A. C/D consume compact artifacts, not raw repository context. E (classification/adversarial) and F (synthesis/calibration) are reserved for the stronger adjudication pass. One artifact may be reused by multiple later phases; redundant parallel reads are avoided.

---

## Project Structure

### Documentation (this feature)

```text
specs/003-beautyq-distage-izumi-leverage-audit/
├── spec.md                          # accepted research contract
├── plan.md                          # this file (/speckit.plan output)
├── research.md                      # Phase 0 plan decisions (/speckit.plan output)
├── data-model.md                    # Phase 1 research entities/validation (/speckit.plan output)
├── quickstart.md                    # Phase 1 read-only validation guide (/speckit.plan output)
├── checklists/
│   └── requirements.md              # existing spec-quality checklist
├── research/                        # durable runtime research artifacts (created at execution, not now)
│   ├── 00-selected-state.md
│   ├── 01-beautyq-candidate-map.md
│   ├── 02-framework-capability-map.md
│   ├── 03-comparison-and-discoverability.md
│   ├── 04-classified-findings.md
│   └── 05-synthesis-and-recommendations.md
└── tasks.md                         # NOT produced by /speckit.plan
```

### Source Code (repository root)

No source tree is added or modified by this feature. Read-only surfaces the execution inspects:

```text
build.sbt, project/*.sbt, project/build.properties     # framework version + project graph
leaderboard-app-shell/src/main/scala/leaderboard/       # plugin/role/lifecycle/startup composition
beautyq-search-gen2-wiring/src/main/scala/...           # domain wiring/lifecycle owners
beautyq-search-gen2-contract/src/main/scala/...         # declaration/contract + boundary proofs
beautyq-search-gen2-materialization/src/main/scala/...  # materialization/lifecycle seams
search-gen2-*/src/main/scala/...                        # reusable Gen2 kernel and backend mechanics
repo-core, leaderboard-core, app-http, app-services     # shared kernel/edges
docs/**                                                 # current owners + domain/framework boundary
```

**Structure Decision**: Research artifacts are colocated in the feature directory under `research/`; no source module is created. Framework sources for the pinned version are consulted read-only from the local dependency cache or the matching upstream tag.

---

## Complexity Tracking

No constitutional violations; no complexity to justify. This table is intentionally empty.
