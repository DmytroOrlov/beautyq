# Quickstart / Validation Guide: BeautyQ Distage/Izumi Leverage Audit

**Feature**: `specs/003-beautyq-distage-izumi-leverage-audit`

**Purpose**: How to verify that a completed (or resumed) *research* run satisfies the feature contract. 003 changes no product code, so this guide validates research-output properties, not product behavior. Do not run implementation CI, a repository test campaign, or a full suite as "validation" here.

**Prerequisites**:
- The feature is explicitly selected as `specs/003-beautyq-distage-izumi-leverage-audit`.
- A run has populated `research/00-selected-state.md` and `research/01..05`.
- Only read-only commands are used; Git index/history/refs are never mutated.

---

## 0. Confirm the feature selection and read-only posture

```bash
cat .specify/feature.json
git status --short
git diff --stat
git diff --cached --stat
git diff --check
```

Expected: `feature.json` points at `specs/003-beautyq-distage-izumi-leverage-audit`. The Git/index/worktree state is compared against the run-start record in `research/00-selected-state.md`, not against an assumed-absolutely-clean repository. Pre-existing human-owned state (including plan artifacts already tracked or untracked, or a human-created staged state) is not an audit mutation and must be classified as pre-existing. If the pointer names another feature, stop and report the ambiguity.

## 1. Source-state anchor resolves

- Read `research/00-selected-state.md`; compare `headSha` to `git rev-parse HEAD`.
- If `worktreeClean = false`, confirm `statusPorcelain` and `diffStat`/`cachedDiffStat` match the current state.
- Every other artifact's header `state-id` must equal this record.

## 2. Framework version evidence matches the pin

```bash
rg -n 'distage|logstage|izumi' build.sbt project/plugins.sbt project/build.properties
```

- Confirm `research/00-selected-state.md` `framework-version` equals the version declared in `build.sbt` (`io.7mind.izumi`, currently `1.2.25`).
- Confirm no `project/*.sbt` override changes it.
- For every framework claim, confirm it cites a pinned-version reference (source/API/example/test), never "latest upstream" or memory.

## 3. Classification states are complete and singular

- In `research/04-classified-findings.md`, confirm each *material* candidate has an explicit current research state.
- For every `CLASSIFIED` row, confirm exactly one label from `WELL_USED` / `UNDERUSED` / `HARD_TO_DISCOVER` / `MISSING_GENERIC_PRIMITIVE` / `BEAUTYQ_SPECIFIC`, plus a BeautyQ-usage anchor and a framework-version-anchored reference (or a documented absence).
- For every `BLOCKED_NEED_EVIDENCE` row, confirm there is **no** headline label and the exact missing evidence plus unblock condition are named.
- For every `INDETERMINATE` row, confirm there is **no** headline label and the competing interpretations plus the evidence that would resolve them are recorded.
- For every `SUPERSEDED` row, confirm its old label is retained only as historical evidence and is not consumed as a current classification.
- Confirm no `CLASSIFIED` candidate has two headline labels or none.

## 4. Discoverability is separated from genuinely-missing

- For every `HARD_TO_DISCOVER` row, confirm a real pinned-version surface exists.
- Confirm no row labeled `MISSING_GENERIC_PRIMITIVE` where a surface exists and the real issue was discoverability.
- Confirm `research/03-*` records a discoverability basis (API naming/docs/examples/tests) for each existing surface.

## 5. Negative claims have search/counterexample coverage

- For every documented-absence or `MISSING_GENERIC_PRIMITIVE` row, confirm `research/03-*` records the search basis and counterexample search; unresolved absence is `BLOCKED_NEED_EVIDENCE`, not "missing".
- Confirm a lone accidental site did not yield `MISSING_GENERIC_PRIMITIVE`.

## 6. Genericity filter applied

- For every `MISSING_GENERIC_PRIMITIVE` row, confirm all eight conditions are recorded and `overall = ALLOWED`, with a named second consumer.
- Otherwise confirm the candidate is downgraded with the failing condition named.

## 7. Symmetric-search calibration

- If the distribution is one-sided (zero `WELL_USED`, zero `BEAUTYQ_SPECIFIC`, or zero change-oriented), confirm `research/04-*` contains an explicit calibration check showing the opposite outcome class was genuinely investigated.
- Confirm no classification distribution is treated as a defect merely for leaning one way.

## 8. Synthesis answers the standing questions

- Read `research/05-synthesis-and-recommendations.md` alone; confirm it answers all eight standing questions in prose.
- Confirm recommendations are visibly non-authoritative, kind-tagged (`DOCS_EXAMPLES` / `FRAMEWORK_API_CODE`), evidence-graded, and that primitive proposals name a second consumer.
- Confirm no recommendation is phrased as accepted/implemented and none opens a work item.

## 9. Git and source were not mutated by the research

```bash
git status --short
git diff --stat
git diff --cached --stat
git diff --check
```

Compare the current state to the run-start record in `research/00-selected-state.md` (Principle V), not to an assumed-clean repository. Pass condition: no new source/build/framework/index/history/ref mutation attributable to the audit; pre-existing human-owned status is unchanged and still classified as pre-existing; the intended new/updated `research/00..05` artifacts are the only research-owned writes. Plan artifacts may already be tracked or may still be pre-existing untracked files; their prior state is not misclassified as an audit mutation. Report the actual `HEAD`/index/worktree state truthfully, and do not clean, stash, or commit human state.

---

## Resume path (empty-context researcher)

1. Read `research/00-selected-state.md` for the current `state-id` and framework version.
2. Follow the consumer chain `01 → 02 → 03 → 04 → 05`.
3. Honor any `BLOCKED_NEED_EVIDENCE` / `SUPERSEDED` rows before using a finding.
4. Do not reread the whole repository or conversation history.

---

## Blocked / insufficient evidence

- `BLOCKED_NEED_EVIDENCE` = exact missing evidence named; not a finding and not a pass.
- Inability to reach framework/material sources is blocked verification, not product failure.
- Unauthenticated framework claims (no pinned-version reference) cannot support a recommendation.

## What this guide does NOT do

- It does not run product tests, compile product code, or substitute for any implementation validation.
- It does not approve, perform, or gate simplification.
- It does not authorize feature 004 or any source change.
