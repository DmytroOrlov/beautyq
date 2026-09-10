# Tasks: BeautyQ Q2 Delivery Closeout

**Feature**: `002-beautyq-q2-closeout` | **Date**: 2026-09-09

**Input**: Accepted design artifacts from `specs/002-beautyq-q2-closeout/` — [spec.md](spec.md), [plan.md](plan.md), [research.md](research.md) (Phase 0 reconciliation verdict).

**Phase Ownership**: This artifact is produced inside a user-invoked `/speckit.tasks` phase. It creates the execution graph only; it does not invoke `/speckit.implement`. Model selection for `[P]` tasks is a coordinator dispatch-time decision and is deliberately absent here.

## Exact First Unfinished Boundary

**Q2-B protected acceptance — RED at `36d3f9dd5bc509948e288ace438e11860114419f`, with the owner-defined recovery continuation from the live, unconsumed r8 authorization `q2-break-glass-exact-intent-recovery-rotation-8-v1` (classified "authorization only")** — research.md "Verdict". The remaining graph starts at the operator break-glass disclosure run; nothing upstream of it is work.

Already-complete boundaries appear below **only as inputs** (FR-005) and must never be re-executed, regenerated, re-reviewed or re-attributed:

- Recoveries rotations 1–7 including rotation-7 materialization `36d3f9dd` and r7 authorization consumption.
- Prerequisite closure for the `36d3f9dd` protected inputs (tracked `beautyq-protected-input-audit-v2.json` + r7 freeze/hash reproduction) — note: the runbook still requires a fresh freeze/audit reproduction on the **rotation-8 rebound bytes** inside T004/T005; that is new work on new bytes, not a re-run of the old prerequisite.
- Q1/O0/O1 as committed history; constitution v2.0.0 adoption (`12538b26`) and documentation-ownership alignment (`47d90b9f`).
- The r8 authorization record itself (`789e5667`) — consumed by disclosure (G001), never re-created.
- The FR-001 reconciliation record (research.md) — re-derivable read-only; not re-performed.

## Task / Gate Vocabulary

- **`[A] AGENT`** — model-executable worktree edit plus its focused validation. Worktree-only (FR-014): no commit, stage, unstage, stash, reset, restore, checkout/switch, ref or history mutation; every report states actual `HEAD`/index/worktree truthfully (FR-009).
- **`[H] HUMAN`** — human-owned Git or decision boundary. Not executable or completable by a model.
- **`[O] OPERATOR`** — owner-defined manual procedure / evidence run per the operations runbook. Not silently substitutable by a model; blocked ≠ passed (FR-011).
- **`[P]`** — independent AGENT tasks only.

Recurring owners cited by the tasks below (procedures never restated here — Principle II):

- Runbook: `docs/gen2/BEAUTYQ_SEARCH_GEN2_OPERATIONS.md` §"Protected input authoring and freeze", §"Protected acceptance and first baseline (manual only)", §"Explicit promotion and verify procedure".
- Feature/plan decisions: plan.md "Technical Decisions" 1–4 and "Remaining Sequence" 1–10.
- FR/SC references: spec.md.

## Execution Graph

### Phase 1: Operator r8 break-glass disclosure (plan Remaining Sequence step 1)

**Goal**: Consume the committed r8 authorization and produce the aggregate-safe disclosure output that all downstream agent work is gated on (plan decision 1).

**Consumes** (verified true at plan time — research.md):
- r8 authorization committed at `789e5667`, bound to corpus fp `ce020b49…` + policy fp `89189e94…`; bound content byte-unchanged at `HEAD 47d90b9f`.

**Completion condition**: Owner-defined disclosure artifact set preserved; consumed-authorization record names `…rotation-8-v1`.

- [ ] G001 [O] Operator executes `BeautyQProtectedBreakGlassMain` per runbook §"Protected acceptance and first baseline (manual only)" consuming `q2-break-glass-exact-intent-recovery-rotation-8-v1` against the tracked canonical protected inputs; preconditions: r8 authorization committed and its bound corpus/policy content fingerprints byte-current (research.md, re-verify at run time); evidence: aggregate-safe disclosure output under `.evidence-runs/q2-break-glass/q2-r8-*/`; directly unlocks: T001 and T003. T002–T005 remain downstream of their declared dependencies. No agent may substitute this run.

### Phase 2: Fresh reserve authoring + rotation-8 materialization (plan step 2; decisions 1–2)

**Depends on**: G001.

**Independence constraint (plan decision 2, runbook authoring section)**: the author pass and the judge pass are separate independent passes over distinct resources; no reuse of consumed v6 cases; disclosed-case migration follows only the G001 output. A capacity shortfall (unable to author 8 acceptable-variant exact-intent cases against the canonical seed catalog) is a **stop-and-report boundary**, never a license to weaken the slice or tune protected policy (FR-011).

- [ ] T001 [A] [P] [reserve-author / plan-decision-2] Author-pass only: create `beautyq-search-gen2-eval/src/test/resources/leaderboard/search/beautyq/gen2/eval/protected/provenance/beautyq-protected-recovery-author-reserve-v7.json` with 8 fresh independently authored acceptable-variant exact-intent cases validated against the canonical seed catalog, per the runbook §"Protected input authoring and freeze" authoring contract; no judge fields, no selection, no holdout/policy edits; validate with focused `beautyq-search-gen2-eval` reserve/case-identity spec runs scoped to the new resource's schema and catalog identity (no whole-graph run).
- [ ] T002 [A] [judge-audit / plan-decision-2] Judge + audit pass over T001's output only: create `…/provenance/beautyq-protected-recovery-judged-reserve-v7.json` and `…/provenance/beautyq-protected-recovery-selection-audit-v9.json` (selection audit consuming the v7 author/judge inputs; v8 is read-only history); validate with focused `beautyq-search-gen2-eval` audit/selection determinism specs.
- [ ] T003 [A] [P] [visible-corpus-owner / plan-decision-1] Disclosed-case migration: migrate the G001-disclosed q2i8 cases into visible Regression ownership in `beautyq-search-gen2-eval/src/main/resources/leaderboard/search/beautyq/gen2/eval/beautyq_evaluation_corpus_v2.json`, preserving honest failure representation per the r7 precedent (`36d3f9dd`); update the touched visible-corpus/intent-correction focused specs in `beautyq-search-gen2-eval/src/test/scala/leaderboard/search/beautyq/gen2/eval/` (precedent files: `BeautyQEvaluationCorpusSpec.scala`, `BeautyQEvaluationIntentCorrectionSpec.scala`, `BeautyQMeasuredEvaluationSpec.scala`); no protected-resource edits; validate with focused `beautyq-search-gen2-eval` corpus/intent-correction specs.
- [ ] T004 [A] [recovery-materializer / plan-decision-2] Rotation-8 materialization on the T001–T003 outputs: replace the protected exact-intent slice in `…/protected/beautyq-protected-holdout-v1.json`; rebind fingerprints only (thresholds and acceptance semantics unchanged) in `…/protected/beautyq-protected-acceptance-policy-v1.json` and `…/protected/beautyq-protected-input-audit-v2.json`; advance the reserve version in `beautyq-search-gen2-eval/src/main/scala/leaderboard/search/beautyq/gen2/eval/BeautyQProtectedRecoveryReserve.scala`; materialize final author/judged drafts (`…/provenance/beautyq-protected-author-draft-v1.json`, `…/provenance/beautyq-protected-judged-draft-v1.json`) and update the focused reserve/replenishment/composition specs they own in `beautyq-search-gen2-eval/src/test/scala/…` and `leaderboard-app-shell/src/test/scala/leaderboard/search/`; `BeautyQProtectedBreakGlassDisclosure.scala` stays read-only; validate with focused `beautyq-search-gen2-eval` and `leaderboard-app-shell` specs scoped to the touched owners.
- [ ] T005 [A] [materialization-validator / plan Validation layer "Patch-local"] Whole-patch local validation + handoff report: run the runbook freeze/audit reproduction on the rebound bytes via `BeautyQProtectedInputFreezeMain` and the focused eval/app-shell suites over the complete T001–T004 patch (precedent scope: `36d3f9dd`), plus `git diff --check`; report the exact uncommitted `HEAD`/index/worktree state and validation scope truthfully (FR-014/FR-015); no commit, no staging.

### Phase 3: Human materialization commit (plan step 3; decision 3)

- [ ] G002 [H] Coordinator accepts the T001–T005 patch and the HUMAN creates the rotation-8 materialization commit (`R8`); preconditions: T005 focused validation green and coordinator patch acceptance; unlocks: G003. A validated patch or accepted recommendation does not satisfy this gate.

### Phase 4: Fresh protected acceptance on the evaluated inputs (plan step 4)

- [ ] G003 [O] Operator runs fresh protected acceptance against the tracked canonical protected inputs per runbook §"Protected acceptance and first baseline (manual only)"; preconditions: the G002 rotation-8 materialization commit exists; evidence: acceptance gate artifacts attributed to the evaluated corpus/policy fingerprints; unlocks: G004 **only if green**. Red or blocked stops Q2-B: no bootstrap, review, promotion, verify or closeout follows; the red result stays reported red (FR-011) and the next owner step is a **new human authorization boundary (outside this graph; the ladder does not silently chain another rotation)**.

### Phase 5: Candidate bootstrap + review (plan steps 5–6)

- [ ] G004 [O] Operator bootstraps the aggregate-only candidate on the same evaluated inputs per runbook; preconditions: G003 green; evidence: preserved candidate + aggregate artifacts; unlocks: G005. Candidate generation is not promotion permission (FR-006).
- [ ] G005 [H] Coordinator/operator candidate review on aggregate-safe fields yields an explicit approval or rejection decision record; preconditions: G004 artifacts preserved at unchanged candidate bytes; unlocks: T006 **only on approval of the candidate unchanged**. Rejection stops the graph.

### Phase 6: Promotion materialization → human promotion commit (plan steps 7–8; decision 4)

- [ ] T006 [A] [promotion-materializer / FR-007] Place the approved candidate **byte-for-byte** into the one canonical tracked evaluation baseline resource `beautyq-search-gen2-eval/src/main/resources/leaderboard/search/beautyq/gen2/eval/beautyq_accepted_evaluation_baseline_v1.json` (absent until promotion; worktree-only creation); validate patch-locally with digest/byte-equality proof against the reviewed candidate plus the focused canonical-resource eval specs in `beautyq-search-gen2-eval`; then **stop and hand off** with the FR-016 report naming the exact materialized uncommitted state, the pending human decision, and the downstream evidence that waits.

- [ ] G006 [H] HUMAN stages and creates the promotion commit; preconditions: T006 copy byte-identical to the approved candidate, patch-local proofs green, coordinator patch acceptance; unlocks: G007. If any application-source class (search, evaluation-policy, lifecycle, route, corpus, protected input) changed between G005 and this boundary, promotion is invalid and the owner-defined restart rule applies (FR-010).

### Phase 7: Independent verify (plan step 9)

- [ ] G007 [O] Operator runs independent verify per runbook §"Explicit promotion and verify procedure" (`BeautyQAcceptedBaselineMain --mode verify`, same tracked canonical inputs); preconditions: the promotion commit exists and required inputs are byte-verified unchanged; evidence: green verify manifest comparing the committed baseline's evaluation content; unlocks: T007. Blocked (missing external ES/Qdrant/embedding resources) keeps the gate open and is never recorded as PASS.

### Phase 8: Attributed closeout (plan step 10; US-3)

- [ ] T007 [A] [closeout-scribe / FR-012/FR-013] Per-layer attributed closeout: extend the closeout/status record in `specs/002-beautyq-q2-closeout/research.md` (verdict section) classifying prerequisite closure, protected acceptance, candidate review, promotion, verification and documentation closeout each individually as complete (with evidence attributed to the evaluated inputs), open, blocked (missing element + owner), or superseded — no bare "Q2 complete" (SC-006); update **only** the status/closure statements that `docs/gen2` owner documents' own contracts require at closeout, citing evidence rather than restating it (FR-013, SC-007); validate with a diff audit proving the doc diff is status/closure-only plus `git diff --check`; no commit.

- [ ] G008 [H] HUMAN decides staging/commit of the T007 closeout documentation patch; preconditions: G007 green verify manifest exists (candidate generation/acceptance green is not enough); unlocks: feature end. This boundary is distinct from G006 and must not be merged with it (SC-004).

## Dependencies and Execution Order *(mandatory)*

### Graph

```text
INPUTS (complete, never re-executed): rotations 1–7 · 36d3f9dd · r7 consumption ·
prerequisite closure at 36d3f9dd · constitution/governance commits · r8 authorization 789e5667 ·
FR-001 reconciliation record (research.md)
  -> G001 [OPERATOR disclosure]
  -> T001 [A author v7]      (‖ T003, see Parallel Opportunities)
  -> T002 [A judge v7 + audit v9]        (depends on T001)
  -> T003 [A disclosed-case migration]   (depends on G001 only)
  -> T004 [A slice replacement + rebind] (depends on T002 + T003)
  -> T005 [A patch-local validation + handoff]
  -> G002 [HUMAN rotation-8 materialization commit]
  -> G003 [OPERATOR fresh protected acceptance on the evaluated inputs]   --red/blocked--> STOP (new human authorization boundary)
  -> G004 [OPERATOR bootstrap aggregate-only candidate on the evaluated inputs]
  -> G005 [HUMAN coordinator/operator review decision]   --reject--> STOP
  -> T006 [A byte-for-byte promotion copy + patch-local proof + FR-016 handoff]
  -> G006 [HUMAN promotion commit]
  -> G007 [OPERATOR independent verify against the committed baseline]   --blocked--> STOP (blocked ≠ passed)
  -> T007 [A closeout record + owner status-line updates]
  -> G008 [HUMAN closeout commit decision] = feature end
```

### Dependency Table

| Node | Type | Depends On | Unlocks | Parallel? |
| --- | --- | --- | --- | --- |
| G001 | OPERATOR | complete inputs (r8 auth committed, bindings byte-current) | T001, T003 | no |
| T001 | AGENT | G001 | T002 | yes — with T003 |
| T002 | AGENT | T001 | T004 | no |
| T003 | AGENT | G001 | T004 | yes — with T001 |
| T004 | AGENT | T002, T003 | T005 | no |
| T005 | AGENT | T004 | G002 | no |
| G002 | HUMAN | T005 + coordinator patch acceptance | G003 | no |
| G003 | OPERATOR | G002 (rotation-8 materialization commit) | G004 (green only) | no |
| G004 | OPERATOR | G003 green | G005 | no |
| G005 | HUMAN | G004 preserved artifacts | T006 (approval only) | no |
| T006 | AGENT | G005 approval | G006 | no |
| G006 | HUMAN | T006 proofs + coordinator patch acceptance | G007 | no |
| G007 | OPERATOR | G006 (promotion commit) | T007 (green only) | no |
| T007 | AGENT | G007 green manifest | G008 | no |
| G008 | HUMAN | T007 patch | feature end | no |

### Parallel Opportunities

Only one true pair exists: **T001 ‖ T003** — disjoint edit owners (test-reserve provenance vs main visible corpus), no output dependency between them (both consume only G001/inputs), and neither may be skipped for the other. T002 cannot parallelize (it judges T001's bytes); T004 cannot (it rebinds over T002+T003 final bytes). Execution note: per `AGENTS.md`, sbt runs are never parallel — the coordinator must serialize any focused-validation invocations even when edit ownership is parallel.

## Validation Coverage *(mandatory)*

| Requirement / Risk | Task(s) | Focused Validation / Evidence | Source State |
| --- | --- | --- | --- |
| FR-004/FR-011: consume existing r8 auth, red stays red, no relabel | G001 | owner disclosure artifact `q2-r8-*` | r8 authorization corpus/policy content fingerprints, operator evidence |
| Plan decision 2: reserve independence, no v6 reuse | T001, T002 | independent author/judge resources + audit v9 determinism (focused eval specs) | uncommitted worktree |
| FR-010/Principle V: fingerprint rebind without threshold/semantics drift | T003, T004 | focused eval/app-shell specs on rebound bytes; runbook freeze/audit reproduction (T005) | uncommitted worktree |
| FR-015: patch-local proof ≠ acceptance evidence | T005 | freeze/audit reproduction + focused suites + `git diff --check`, scope-truthful report | exact uncommitted state, no commit |
| FR-006/US-2-1: green gates bootstrap | G003 | protected acceptance gate artifact bound to the evaluated corpus/policy fingerprints | rotation-8 materialization commit |
| FR-006: candidate ≠ permission; explicit review gates promotion | G004, G005 | preserved aggregate artifacts + explicit review decision | evaluated inputs, unchanged bytes |
| FR-007/FR-008/FR-016: promotion is one byte-for-byte human-committed boundary | T006, G006 | digest/byte-equality + focused canonical-resource proofs; then human promotion commit | uncommitted copy → committed promotion |
| FR-006: green verify gates closeout | G007 | independent verify manifest against the committed baseline's evaluation content | promotion commit |
| FR-012/FR-013, SC-006/SC-007: honest attributed closeout, status-only owner diff | T007 | closeout record + status/closure-only diff audit | post-verify committed state |
| FR-014, SC-004: zero agent Git mutation; two distinct human commit boundaries | every `[A]` task + G002/G006/G008 | worktree-only rule; reports state `HEAD`/index/worktree truthfully | n/a |

No unscoped full-repository suite is prescribed as an agent task; every focused command is scoped to the owning subproject(s) per `AGENTS.md`.

## Human / Operator Handoffs *(material — this feature is mostly boundaries)*

| Gate | Why Non-Agent | Exact Preconditions | Required Human/Operator Action | Downstream Work That Waits |
| --- | --- | --- | --- | --- |
| G001 | operator-owned protected disclosure procedure | r8 auth committed; content fingerprints byte-current (re-verified at run time) | run `BeautyQProtectedBreakGlassMain` per runbook | T001 and T003 directly; T002–T005 transitively through their declared dependencies |
| G002 | Principle I — only the human commits | T005 patch-local validation green; coordinator accepts patch | commit rotation-8 materialization | G003 onward |
| G003 | operator-owned manual acceptance run | rotation-8 materialization commit exists | fresh protected acceptance against the tracked canonical inputs | G004 onward; red/blocked ⇒ stop, new human authorization boundary |
| G004 | operator-owned manual bootstrap run | G003 green | bootstrap aggregate-only candidate on the evaluated inputs | G005 |
| G005 | human review decision (FR-006) | preserved candidate at unchanged candidate bytes | explicit approve/reject record | T006; reject ⇒ stop |
| G006 | Principle I — unique human promotion commit | T006 byte-for-byte copy + proofs + coordinator patch acceptance | stage + create promotion commit | G007 |
| G007 | operator-owned independent verify | promotion commit made; inputs unchanged | `BeautyQAcceptedBaselineMain --mode verify` against the committed baseline's evaluation content | T007; blocked ⇒ stop |
| G008 | Principle I — human closeout commit decision | G007 green; T007 diff audited | decide staging/commit of closeout patch (distinct from G006) | feature end |

A prepared patch, matching bytes, a recommendation, or a presentable analysis package does not satisfy any gate above.

## Notes

- Re-derive the governing constitution state at every phase entry; FR-017 stop-and-report (`Q2 CLOSEOUT — GOVERNANCE BOUNDARY NOT YET STABLE`) if indeterminate.
- Stale local artifacts (Aug 6 candidate dir, r7-era `target/` logs) are attribution history only; never reused as current evidence (research.md; plan Risks).
- If any task discovers the reconciliation verdict no longer matches durable evidence at execution time, it stops and reports the divergence — re-reconciliation belongs to its owner, not to silent adaptation.
- Feature artifacts own workflow state; check off nodes only as they actually complete; gates complete only by external human/operator action.
