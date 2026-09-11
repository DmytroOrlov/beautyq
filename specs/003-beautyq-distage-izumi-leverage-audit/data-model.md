# Phase 1 Data Model: BeautyQ Distage/Izumi Leverage Audit

**Feature**: `specs/003-beautyq-distage-izumi-leverage-audit`

**Scope**: Entities, fields, relationships, state transitions, and validation rules for the **research outputs**. These are durable artifact schemas, not product data models; the audit creates no product schema. They are the shape that later phases (and a later empty-context researcher) read and write.

---

## Entity relationships

```text
SourceStateRecord ──< binds >── (all findings)
FrameworkVersionReference ──< binds >── (all framework claims)
CandidateArea ──< contains >── CandidateSite
FrameworkTheme ──< contains >── FrameworkSurface        (B0; discovered independently of A)
CandidateSite ──> Need                                   (surfaced by A)
Need ── matched-by (joined at B1/C) ── FrameworkSurface  (matchedNeedIds)
CandidateSite ──> ComparisonRecord ──> DiscoverabilityAssessment
CandidateSite ──> Classification (current research state) ──> GenericityFilterResult (gate)
Classification ──> Recommendation (zero or more, non-authoritative)
CandidateSite/Classification ──> BlockedFinding (terminal-open) | Supersession (terminal-superseded)
ResearchArtifact ── owns ── (all of the above as rows in research/00..05)
```

---

## SourceStateRecord

**Answers**: which exact BeautyQ state do findings bind to? **Owner**: run opener. **Written to**: `research/00-selected-state.md`.

| Field | Type | Notes |
| --- | --- | --- |
| `headSha` | string | `git rev-parse HEAD` |
| `worktreeClean` | bool | from `git status --short` |
| `statusPorcelain` | string list | changed/untracked paths when dirty |
| `diffStat` / `cachedDiffStat` | string | `git diff --stat`, `git diff --cached --stat` when material |
| `stateId` | string | lightweight label, e.g. `<short-sha>-clean` / `<short-sha>-dirty` |
| `recordedAt` | timestamp | UTC |

**Validation rules**: `stateId` is derived from the actual commands, not supplied free-text. No digest/baseline/freeze fields. Index and worktree are distinguished, never collapsed into `HEAD`.

## FrameworkVersionReference

**Answers**: which resolved framework version do claims bind to? **Owner**: run opener. **Written to**: `research/00-selected-state.md`.

| Field | Type | Notes |
| --- | --- | --- |
| `groupId` | string | `io.7mind.izumi` |
| `version` | string | currently `1.2.25` |
| `artifacts` | string list | `distage-core`, `distage-framework`, `distage-extension-config`, `distage-framework-docker`, `distage-testkit-scalatest`, `logstage-adapter-slf4j` |
| `evidenceSource` | string | e.g. `build.sbt:5,24-29` or local cache path |
| `escalations` | string list | any use of cache/upstream/focused query, with result |
| `verifiedAt` | timestamp | UTC |

**Validation rules**: every framework claim cites this reference. Latest upstream/`master` is never substituted. If execution resolves a different version, the record changes and prior claims are re-checked.

## CandidateArea / CandidateSite

**Answers**: what local mechanics are worth evaluating, and where? **Owner**: BeautyQ-source mapper. **Written to**: `research/01-beautyq-candidate-map.md`.

| Field | Type | Notes |
| --- | --- | --- |
| `areaId` | enum | one of the nine spec candidate areas |
| `areaStatus` | enum | `MAPPED` \| `NO_CURRENT_MATERIAL_CANDIDATE` \| `REJECTED` |
| `areaBasis` | string | required for non-`MAPPED` statuses |
| `candidateId` | string | stable id for a material candidate |
| `ownerModule` | string | project/module + package |
| `anchors` | list of `file:line` | current source evidence |
| `mechanicSummary` | string | what the local mechanic does, no verdict |
| `currentOwner` | string? | canonical owner doc/type if one exists |
| `recurrenceSites` | list of `file:line` | sites sharing the mechanic |
| `stateId` | string | source state studied |

**Validation rules**: no classification labels here. Deleted Wave-A/Wave-B machinery is not reconstructed. A material candidate has at least one anchor; `file:line` must resolve at `stateId`.

## FrameworkTheme / FrameworkSurface (B0) and Need (A / B1 / C join)

**Answers**: what does the pinned framework provide by theme, and which A needs it matches? **Owner**: framework-surface mapper. **Written to**: `research/02-framework-capability-map.md`.

A B0 surface is discovered independently of A and does not require an A-derived `needId`; A needs are joined to surfaces at B1/C.

| Field | Type | Notes |
| --- | --- | --- |
| `frameworkThemeId` | string | bounded theme/capability (B0 seed), independent of A |
| `surfaceId` | string | framework surface identifier within a theme |
| `surfaceSummary` | string | what the surface does |
| `apiNames` | string list | types/methods/packages |
| `surfaceEvidence` | string | pinned-version source/test/example reference |
| `versionRef` | FrameworkVersionReference | must match pin |
| `matchedNeedIds` | string list | populated/reconciled at B1/C from A candidates; may be empty at B0 |
| `absenceBasis` | string? | search-bounded basis when no surface exists for an A need after B1 |

**Need** (a candidate's required capability): `needId`, `needSummary`, `candidateId` (from A). Joining `needId` to `surfaceId` happens at B1/C; the join is not required to exist when B0 runs.

**Validation rules**: each B0 theme is grounded in `surfaceEvidence` + `versionRef`; after B1, each material need has ≥1 matched surface or a documented search-bounded absence. No surface claim from memory. A surface without a matched need is not itself a finding.

## ComparisonRecord / DiscoverabilityAssessment

**Answers**: how does the local mechanic compare, and is the surface discoverable? **Owner**: comparison/discoverability pass. **Written to**: `research/03-comparison-and-discoverability.md`.

| Field | Type | Notes |
| --- | --- | --- |
| `candidateId` | string | join key |
| `comparison` | enum | `DIRECT_USE` \| `LOCAL_DUPLICATION_OR_BYPASS` \| `MISSING_COMPOSITION` \| `NO_RELEVANT_PRIMITIVE` |
| `nearestSurfaceIds` | string list | may be empty only for `NO_RELEVANT_PRIMITIVE` |
| `comparisonEvidence` | string list | refs |
| `discoverability` | enum | `DISCOVERABLE` \| `HARD_TO_DISCOVER` \| `INDETERMINATE` \| `N_A` |
| `discoverabilityBasis` | string | API naming/docs/examples/tests basis |
| `remedyCandidates` | enum list | `DOCS` \| `EXAMPLES` \| `NAMING` \| `HELPER_FACADE` \| `NONE` |

**Validation rules**: `HARD_TO_DISCOVER` requires a real `surfaceId`. A matching surface that is merely hard to find is never promoted to "missing" (FR-005).

## Classification / GenericityFilterResult

**Answers**: the current research state per material candidate and, when CLASSIFIED, its single label. **Owner**: adversarial classifier. **Written to**: `research/04-classified-findings.md`.

| Field | Type | Notes |
| --- | --- | --- |
| `candidateId` | string | join key |
| `label` | enum? | required iff `status = CLASSIFIED`: exactly one of `WELL_USED` / `UNDERUSED` / `HARD_TO_DISCOVER` / `MISSING_GENERIC_PRIMITIVE` / `BEAUTYQ_SPECIFIC`; absent for `BLOCKED_NEED_EVIDENCE` / `INDETERMINATE`; retained but not current for `SUPERSEDED` |
| `beautyqEvidence` | string list | source anchors |
| `frameworkEvidence` | string list | pinned-version refs or documented absence |
| `justification` | string | plain language |
| `secondarySignal` | string? | e.g. "also hard to discover"; not a second headline label |
| `counterexampleSearch` | string | what was actively looked for and found |
| `genericityResult` | GenericityFilterResult? | required iff label = `MISSING_GENERIC_PRIMITIVE` |
| `stateId` / `versionRef` | refs | binding |
| `status` | enum | `CLASSIFIED` \| `BLOCKED_NEED_EVIDENCE` \| `INDETERMINATE` \| `SUPERSEDED` |

**GenericityFilterResult** (8 conditions from spec): `recurrence`, `noProductSemantics`, `infrastructuralNature`, `deNamedApi`, `secondConsumer`, `realReduction`, `genericTests`, `simplerOwnedCentrally` — each `PASS`/`FAIL`/`UNKNOWN`, plus `overall` (`ALLOWED` only when substantially all pass) and `failingConditions`.

**Validation rules**:
- every current material candidate has an explicit current research state; every `CLASSIFIED` candidate has exactly one headline label (SC-001);
- `CLASSIFIED` rule: exactly one label from the five-way vocabulary; no candidate has two or none *while classified*;
- `BLOCKED_NEED_EVIDENCE` rule: no headline label; `missingEvidence` and `wouldUnblock` required; never written as "missing" or passing;
- `INDETERMINATE` rule: no headline label; the competing plausible interpretations and the evidence that would separate them are recorded;
- `SUPERSEDED` rule: a previously classified row MAY retain its old label as historical evidence attributed to the old state, but it MUST NOT be consumed as a current classification until rechecked;
- `MISSING_GENERIC_PRIMITIVE` requires `genericityResult.overall = ALLOWED`, recurrence beyond one accidental site, and a named second consumer, else downgraded with the failing condition named (FR-007, SC-004);
- `HARD_TO_DISCOVER` requires a real pinned-version surface (SC-003);
- every row cites `stateId` + `versionRef` (SC-005/SC-007);
- any one-sided distribution records a calibration check in this artifact.

## Recommendation

**Answers**: what non-authoritative follow-ups are evidence-graded. **Owner**: synthesis pass. **Written to**: `research/05-synthesis-and-recommendations.md`.

| Field | Type | Notes |
| --- | --- | --- |
| `recommendationId` | string | |
| `candidateId` | string | source classification |
| `kind` | enum | `DOCS_EXAMPLES` \| `FRAMEWORK_API_CODE` |
| `statement` | string | visibly a recommendation |
| `evidenceRefs` | string list | |
| `confidence` | enum/string | confidence + uncertainty |
| `secondConsumer` | string? | required for `FRAMEWORK_API_CODE` primitive proposals |
| `genericityResult` | ref? | when primitive |
| `nonAuthoritative` | const true | never an accepted change |

**Validation rules**: no recommendation phrased as accepted/implemented; every `FRAMEWORK_API_CODE` (primitive) recommendation has a second consumer; `DOCS_EXAMPLES` remedies explicitly state no new primitive is required.

## BlockedFinding / Rejection / Supersession

| Entity | Fields | Rule |
| --- | --- | --- |
| BlockedFinding | `candidateId`, `missingEvidence`, `wouldUnblock`, `stateId` | terminal-open; no headline label; never recorded as `MISSING_GENERIC_PRIMITIVE` or passing |
| Indeterminate | `candidateId`, `competingInterpretations`, `evidenceToResolve`, `stateId` | terminal-open; no headline label |
| Rejection | `areaId`, `basis`, `stateId` | valid terminal result; not forced into a positive finding |
| Supersession | `findingRef`, `oldStateId`, `newStateId`, `reason` | retains prior attribution; never reattributes |

## ResearchArtifact (self-description)

**Written to**: header of each `research/*.md`.

| Field | Type | Notes |
| --- | --- | --- |
| `path` | string | artifact path |
| `question` | string | what it answers |
| `writer` | role | who writes it |
| `consumers` | role list | who reads it |
| `staleWhen` | string | stale condition |
| `stateId` / `versionRef` | refs | binding |

---

## Candidate lifecycle / state transitions

```text
[area surfaced] --A--> MAPPED
   MAPPED --A--> NO_CURRENT_MATERIAL_CANDIDATE | REJECTED   (area terminal, not classifications)
   MAPPED --B0--> framework surfaces by theme
   MAPPED + B0 --B1--> NEEDED (surface matched) | ABSENCE (search-bounded)
   NEEDED/ABSENCE --C/D--> COMPARED + DISCOVERY_ASSESSED (or N_A when no surface)
   DISCOVERY_ASSESSED --E--> CLASSIFIED (exactly one label)
                          \-> BLOCKED_NEED_EVIDENCE (no label)
                          \-> INDETERMINATE (no label)
   CLASSIFIED --F--> RECOMMENDED (zero or more, non-authoritative)
   any finding --drift--> SUPERSEDED (retains old label as historical, not current)
```

**Terminal states**: `NO_CURRENT_MATERIAL_CANDIDATE` and `REJECTED` (Phase-A area outcomes, not classifications); `CLASSIFIED` (one label); `BLOCKED_NEED_EVIDENCE` and `INDETERMINATE` (no label); `SUPERSEDED` (old label retained but not current). Only `CLASSIFIED` may feed recommendations.

---

## Cross-cutting validation (applies to the whole artifact set)

1. Every finding binds to `stateId` + `versionRef`.
2. Every material candidate has an explicit current state; every `CLASSIFIED` candidate has exactly one headline label, and `BLOCKED_NEED_EVIDENCE`/`INDETERMINATE` carry none; no double-counted headline labels.
3. No `MISSING_GENERIC_PRIMITIVE` where a surface exists but was hard to find.
4. No framework claim without pinned-version evidence.
5. No recommendation without evidence and a second consumer when it proposes a primitive.
6. Symmetric-search calibration recorded for any one-sided distribution.
7. Synthesis (not any raw inventory) answers the eight standing questions.
8. Research mutates no source/build/framework file and no Git index/history/ref.
