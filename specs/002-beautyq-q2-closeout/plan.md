# Implementation Plan: BeautyQ Q2 Delivery Closeout

**Feature**: `002-beautyq-q2-closeout` | **Date**: 2026-09-09 | **Spec**: [spec.md](spec.md)

**Input**: Accepted feature specification from `specs/002-beautyq-q2-closeout/spec.md`

**Phase Ownership**: This artifact is produced inside a user-invoked `/speckit.plan` phase. It plans the selected feature only; it does not invoke `/speckit.tasks` or `/speckit.implement`.

## Summary

The FR-001 reconciliation (durable record: [research.md](research.md)) replaced the assumed state with
derived state. It confirms the post-cutover plan's pre-002 claim directionally ("prerequisites complete;
Q2-B/Q2-C open") but refines it: the **latest protected acceptance is RED against
`36d3f9dd5bc509948e288ace438e11860114419f`** (failed `exact-intent-variants/success/10` at 0.75 vs ≥1.0,
2026-08-21 08:33, artifacts + committed authorization binding agree on the fingerprints), and the
accepted authorization records carry a **live, unconsumed rotation-8 break-glass authorization bound to
exactly those evaluated corpus/policy fingerprints**. No candidate, review record, promotion resource (`beautyq_accepted_evaluation_baseline_v1.json`
has never existed) or verify manifest exists at or after that state. The smallest coherent path is the
owner-defined Q2-B recovery continuation: operator disclosure of the existing r8 authorization →
independent reserve-v7 authoring → rotation-8 materialization (human commit) → fresh protected acceptance
→ then the unchanged ladder forward (bootstrap → review → human promotion commit → verify → attributed
closeout). No new machinery is introduced (FR-018); every step uses runbook-owned procedures and
owner-owned identity semantics.

## Planning Object and Current Boundary *(mandatory)*

**Plan Object**: Drive the Q2-B protected-acceptance boundary from its derived RED state at `36d3f9dd` to
a green protected acceptance on the post-rotation-8 materialization, then complete the remaining accepted
ladder (bootstrap → review → promotion → verify → closeout) exactly as the owners define it.

**Current / First Unfinished Boundary**: **Q2-B protected acceptance — RED at `36d3f9dd`** (research.md
"Verdict"). Its owner-defined recovery continuation begins at operator break-glass disclosure consuming
`q2-break-glass-exact-intent-recovery-rotation-8-v1`. This is an established conclusion from durable
evidence, not the FR-004 clue: the rotation-8 record classified as **"authorization only"**. Not
`BLOCKED_NEED_EVIDENCE`: every US-1 scenario-1 dimension is classified with an evidence reference.

**Already-Complete Inputs** (FR-005 — input only, never re-executed):

- Recovery rotations ≤7 including rotation-7 materialization `36d3f9dd` and r7 authorization consumption
  (disclosure record `.evidence-runs/q2-break-glass/q2-r7-disclosure-20260820-124611-5671/`).
- Prerequisite closure for the `36d3f9dd`-rebind protected inputs: tracked audit
  `beautyq-protected-input-audit-v2.json` + 36d3f9dd's recorded deterministic freeze/hash reproduction.
- Q1/O0/O1 completion as committed history (attributed to their own revisions).
- Constitution v2.0.0 adoption and documentation-ownership alignment (`12538b26`, `47d90b9f`).
- The r8 authorization record itself (`789e5667`) — consumed by disclosure, never re-created.

## Canonical Owners and Source Anchors *(mandatory)*

| Decision-Critical Fact | Canonical Owner / Source Anchor | How This Plan Uses It |
| --- | --- | --- |
| Current Q2 closeout requirements, gates, state | `specs/002-beautyq-q2-closeout/spec.md` + [research.md](research.md) | read / extend (this feature owns status) |
| Historical accepted boundary definition + pre-002 claim | `docs/gen2/BEAUTYQ_SEARCH_GEN2_POST_CUTOVER_PLAN.md` §"Q2 state", §"Approved milestones" | read; restart rules preserved; status refined only via reconciliation finding F-1 |
| HOW protected acceptance/bootstrap/promotion/verify execute (commands, outputs, safety) | `docs/gen2/BEAUTYQ_SEARCH_GEN2_OPERATIONS.md` §"Protected acceptance and first baseline (manual only)", §"Protected workflow ownership", §"Explicit promotion and verify procedure" | read / execute via operator; never restate procedure here (Principle II) |
| Authorization records + fail-closed content-fingerprint semantics | `beautyq-search-gen2-eval/src/main/scala/leaderboard/search/beautyq/gen2/eval/BeautyQProtectedBreakGlassDisclosure.scala` | preserve; r8 entry consumed by disclosure only |
| Recovery reserve/provenance inventory | `beautyq-search-gen2-eval/src/test/resources/leaderboard/search/beautyq/gen2/eval/protected/provenance/beautyq-protected-recovery-*-v{N}.json` + `beautyq-protected-recovery-selection-audit-v8.json` | read; r8 requires a new independently authored/judged reserve version |
| Protected inputs + acceptance policy | `.../eval/protected/beautyq-protected-holdout-v1.json`, `.../beautyq-protected-acceptance-policy-v1.json` | read; rebind only through an owner-defined materialization |
| Promotion target (one canonical tracked eval resource) | `beautyq-search-gen2-eval/src/main/resources/leaderboard/search/beautyq/gen2/eval/beautyq_accepted_evaluation_baseline_v1.json` (absent until promotion) | create byte-for-byte from reviewed candidate at promotion; human commit only |
| Runner entry points | `leaderboard-app-shell/src/test/scala/leaderboard/search/BeautyQ{ProtectedAcceptance,AcceptedBaseline,ProtectedBreakGlass,ProtectedInputFreeze}Main.scala` | invoke per runbook against the verified tracked canonical inputs; a Git application revision is not an input |
| Agent Git/index boundaries, evidence attribution | Constitution Principles I, III, IV, V, VI, XI | read / preserve |

## Exact Evaluated State *(mandatory when evidence/source identity matters)*

**Committed source identity**: `HEAD = 47d90b9f2966f250e4c0a45c0267e8afde6b5d23` on `develop`
(== `origin/develop`). The pending RED acceptance evidence is attributed to `36d3f9dd…` — never to HEAD.

**Index/worktree divergence relevant to this plan**: none staged; worktree == HEAD except this phase's
untracked `specs/002-beautyq-q2-closeout/{plan,research}.md`. Human-owned branches/stashes exist; read
if decision-relevant, never mutated (Principle I).

**Evidence identity semantics**: owner-defined — evidence binds to the actual evaluated inputs and results
(protected corpus/policy fingerprints, aggregate and candidate digests); disclosure/verify runners fail
closed on corpus/policy fingerprint mismatch. A Git application revision is not part of the evaluation
identity. Operator-evidence paths (`.evidence-runs/q2-*`, runner `--output-dir` artifacts) are
owner-defined evidence under the runbook and the spec's assumptions — used without being
re-declared as tracked history.

**Drift / restart rule**: any search, evaluation-policy, lifecycle, route or protected-input change before
a green acceptance invalidates the pending acceptance evidence: fresh acceptance against the changed inputs
(plan §Q2-B; FR-010). After acceptance/bootstrap and before review→promotion, the candidate must be byte-for-byte
unchanged; between review and verify no source class above may change (plan §Q2-C). A red or blocked run
stops its boundary with evidence attributed to the inputs actually evaluated.

## Technical Context

**Owning project/module(s)**: `beautyq-search-gen2-eval` (authorization records, protected resources,
canonical baseline resource, freeze/acceptance/baseline logic), `leaderboard-app-shell` (manual runner
mains), `docs/gen2/*` + `specs/002-beautyq-q2-closeout/*` (status owners).

**Language / build**: Scala (sbt, forked app-shell Test JVM for runners) — material only as the runbook's
command surface; no build change expected.

**Primary dependencies/frameworks**: Distage `scene:managed` test containers, circe strict decoders —
existing machinery; FR-018 forbids new hashing/manifest/promotion machinery.

**Persistence/external resources**: Elasticsearch, Qdrant, embedding services via managed scene for
acceptance/bootstrap/verify runs; canonical typed seed catalog for reserve/case identity validation.
Absence ⇒ **blocked**, never pass (FR-011, runbook failure semantics).

**Testing/verification seams**: focused eval/app-shell specs as materialized per rotation (precedent
`36d3f9dd`: 10 eval/app-shell suites, intent-parser, deterministic freeze/hash reproduction);
`BeautyQAcceptedBaselineMain --mode verify` for post-promotion canonical-resource proof; route to the
exact focused commands is task-phase work.

**Operational constraints**: all acceptance/bootstrap/disclosure/verify executions are **manual operator
procedures** per the runbook; commits are human-only (Principle I, FR-014); protected case data stays
aggregate-safe outside disclosure boundaries (FR-011 no-relabel rules).

## Constitution Check

*GATE: Evaluate before Phase 0 research/reconciliation and re-check after design.*

| Material Principle / Boundary | Pre-Research Check | Post-Design Check | Consequence |
| --- | --- | --- | --- |
| I — human-owned Git history/index | PASS — reconciliation was read-only; zero agent commits/staging/stash; index preserved empty | PASS — design assigns every materialization, promotion and closeout commit to the human; agent steps end at worktree + patch-local validation | FR-007/FR-014/FR-016 honored structurally |
| II — one canonical current owner | PASS — no owner content restated; plan cites runbook/plan/spec paths | PASS — `contracts/` and `quickstart.md` deliberately omitted to avoid duplicating runbook procedures; mutable state lives only in research.md/plan and owner docs | Artifacts limited to what owners don't already own |
| III — completion names object and gate | PASS — "Q2-B open" claim treated as claim until verified | PASS — remaining sequence and closeout word per-layer (acceptance ≠ bootstrap ≠ review ≠ promotion ≠ verify ≠ closeout) | SC-006 closeout form fixed in advance |
| IV — evidence is not permission | PASS — candidate generation history shows green was never assumed from artifacts | PASS — ladder edges enforced: green gates bootstrap; candidate gates nothing until explicit review; review gates promotion; human commit gates verify | No step may lean on possession alone |
| V — evidence belongs to exact evaluated inputs | PASS — RED attributed to `36d3f9dd` (artifact + committed binding agree); index/worktree distinction recorded | PASS — every remaining boundary names the inputs it evaluates before running; obsolete r7-era artifacts (e.g. Aug 6 candidate) explicitly not reused | SC-005 attribution is a design invariant, not a reporting habit |
| VI — no fake green | PASS — the r7-era RED stayed reported red and produced an authorization, not a relabel | PASS — blocked-vs-failure distinction wired into validation plan; reserve independence (fresh author/judge passes) prevents outcome-driven tuning of the replacement slice | FR-011 preserved at each boundary |
| VIII — proposals are not accepted architecture | PASS — simplification plan / features 003–004 read as non-normative | PASS — no plan step touches them; no accepted limit converted to work | Scope stays the accepted ladder |
| XI — truthful validation scope | PASS — reconciliation claims are read-only inspections; **no test or run was executed at plan time** | PASS — patch-local vs feature acceptance vs independent verify kept in three distinct rows below | FR-015 reportability is built in |
| XII — smallest coherent change | PASS | PASS — zero new machinery; the whole path is existing owner-defined steps sequenced from derived state | FR-018 satisfied |

**Gate verdict**: no violations; no unjustified complexity. Re-check after design confirms PASS.

## Phase 0 — Research / Reconciliation

### Questions to Resolve

- What does durable evidence actually show for each US-1 scenario-1 dimension (vs the plan's pre-002 claim)?
- How does the rotation-8 authorization clue classify under FR-004's five options?
- Is the r8 authorization still consumable under owner identity semantics after the post-`36d3f9dd`
  documentation/authorization commits (FR-010)?
- Is there leftover reserve inventory for a rotation-8 materialization?
- Which boundary is the exact first unfinished accepted one, and is evidence sufficient to proceed?

### Required Research Output

All answered in [research.md](research.md): source-confirmed current state; exact identities (revisions +
fingerprints); the single reconciliation finding (F-1 refinement, no owner contradiction to repair at the
plan now); decisions taken (r8 = "authorization only"; no pending green evidence to invalidate); no
blocked/indeterminate dimensions.

**Phase 0 Verdict**: **resolved boundary** — Q2-B protected acceptance RED at `36d3f9dd`; first unfinished
accepted boundary is its recovery continuation via the live unconsumed r8 authorization.

## Phase 1 — Technical Design / Remaining Path

### Change Frontier

```text
beautyq-search-gen2-eval/src/test/resources/leaderboard/search/beautyq/gen2/eval/protected/beautyq-protected-holdout-v1.json
beautyq-search-gen2-eval/src/test/resources/leaderboard/search/beautyq/gen2/eval/protected/beautyq-protected-acceptance-policy-v1.json
beautyq-search-gen2-eval/src/test/resources/leaderboard/search/beautyq/gen2/eval/protected/beautyq-protected-input-audit-v2.json
beautyq-search-gen2-eval/src/test/resources/leaderboard/search/beautyq/gen2/eval/protected/provenance/**   (new reserve v7 authoring + selection audit v9 at materialization)
beautyq-search-gen2-eval/src/main/scala/leaderboard/search/beautyq/gen2/eval/BeautyQProtectedRecoveryReserve.scala   (reserve version advance, rotation-8 materialization)
beautyq-search-gen2-eval/src/main/scala/leaderboard/search/beautyq/gen2/eval/BeautyQProtectedBreakGlassDisclosure.scala   (read-only: r8 record consumed; no edit)
beautyq-search-gen2-eval/src/main/resources/leaderboard/search/beautyq/gen2/eval/beautyq_accepted_evaluation_baseline_v1.json   (created ONLY by promotion copy)
.evidence-runs/q2-break-glass/q2-r8-*/  target/search-gen2/protected/   (operator evidence outputs — never source of truth beyond attribution)
specs/002-beautyq-q2-closeout/** + docs/gen2 owner status lines at closeout
```

**Retained owners / forbidden changes**: technical specification architecture/semantics; runbook
procedures; evaluation policy/thresholds and acceptance semantics (rebind fingerprints only through a
materialization, as `36d3f9dd` did); historical authorization records and consumed disclosure evidence;
`specs/003`/`004` and the simplification plan (non-normative); all Git history/index/ref state.

### Technical Decisions

1. **Recovery continuation, not re-reconciliation or re-authorization**
   - Choice: consume the existing committed r8 authorization; do not create a new authorization.
   - Source/evidence basis: research.md FR-004 classification; runner fail-closed binding to
     `36d3f9dd` + `ce020b49…` + `89189e94…`, all byte-unchanged at HEAD.
   - Alternatives rejected: fresh authorization (none is justified while the r8 record is live and
     unconsumed — that would be new machinery per FR-018); treating the authorization as obsolete (no
     material app-source change since binding, r7 precedent shows record-commits don't invalidate
     bindings); running acceptance again at `36d3f9dd` unchanged (deterministic red input → red output;
     VI-compatible only as pointless, and recovery requires disclosed-failure remediation first).
   - Consequence: operator disclosure run is the first executable step; agent authoring work is gated on
     its aggregate-safe output (disclosed cases migrate to visible Regression, as `36d3f9dd` did for r7).

2. **Rotation-8 materialization requires fresh independent reserve authoring**
   - Choice: author/judge a new reserve version (v7 resources + selection audit v9) before slice
     replacement; no reuse of consumed v6 cases; freeze/audit reproduction re-run on the rebound bytes.
   - Source/evidence basis: `beautyq-protected-recovery-selection-audit-v8.json` shows candidateCount 8,
     selected 8, consumed 0 — nothing left to select; runbook §"Protected input authoring and freeze"
     owns the author/judge/audit separation; `36d3f9dd` is the structural precedent.
   - Alternatives rejected: selecting from existing reserve (inventory proves none); weakening the slice
     threshold to flip red→green (FR-011 violation).
   - Consequence: the largest agent-worktree step; capacity shortfall (unable to author 8
     acceptable-variant exact-intent cases against the canonical catalog) is a stop-and-report boundary,
     not a license to tune protected policy.

 3. **Materialization lands as a human commit; fresh acceptance runs against the resulting inputs**
    - Choice: agent prepares materialization + focused validation in the worktree → coordinator accepts the
      patch → human commits → operator runs fresh protected acceptance against the tracked canonical inputs.
    - Source/evidence basis: rotations 2–7 each materialized as their own commit; r7 acceptance ran after its
      materialization commit (private log). Principle I / FR-007 pattern.
    - Consequence: if that fresh acceptance is green, bootstrap proceeds on the same evaluated inputs; if red
      again, Q2-B stops and a **new authorization boundary (human-owned)** is required — the ladder does
      not silently chain another rotation.

 4. **Promotion stays the unique byte-for-byte human-committed tracked boundary**
    - Choice: unchanged from the runbook — reviewed candidate copied byte-for-byte into the one canonical
      resource by an agent, patch-locally validated (digest/equality + canonical-resource focused tests),
      then stop; human stages/commits; verify runs only against that committed baseline's evaluation content.
    - Source/evidence basis: runbook §"Explicit promotion and verify procedure"; FR-007/FR-008/FR-016.
    - Consequence: SC-004's two human boundaries (promotion commit; any later closeout doc commit) stay
      distinct; no count is invented beyond owner rules.

### Remaining Sequence

Starts at the first unfinished boundary only; completed boundaries above are not listed.

1. Operator break-glass disclosure consuming the r8 authorization against the byte-current tracked canonical
   inputs (runbook/manual; aggregate-safe output preserved under `.evidence-runs/q2-break-glass/q2-r8-*`).
2. Agent worktree: fresh reserve-v7 author/judge passes + selection audit; rotation-8 materialization
   (disclosed-case migration, slice replacement, fingerprint rebind) + focused validation; coordinator
   patch acceptance.
3. HUMAN commit of rotation-8 materialization (`R8`).
4. Operator fresh protected acceptance against the tracked canonical inputs (green gates everything downstream;
   red/blocked stops and routes to a new human authorization boundary; evidence attributed to the evaluated inputs).
5. Operator bootstrap of the aggregate-only candidate on the same evaluated inputs; preserve artifacts;
   stop (generation ≠ permission).
6. Coordinator/operator candidate review (aggregate-safe fields only); explicit approval or rejection.
7. Agent worktree: byte-for-byte candidate → canonical `beautyq_accepted_evaluation_baseline_v1.json`;
   digest/equality + focused canonical-resource proofs; **stop and hand off** (FR-016 report names exact
   uncommitted state, pending human decision, and waiting downstream evidence).
8. HUMAN stages + creates the promotion commit.
9. Operator independent verify (`--mode verify`, same tracked canonical inputs) → green verify manifest is the
   gate for documentation closeout.
10. Agent closeout: per-layer attributed status record (research.md → closeout report), owner status/closure
    line updates strictly per each owner's closure contract (FR-013); a resulting documentation patch is
    handed to the human for its own commit decision (SC-004 — distinct from the promotion boundary).

## Human / Operator Boundaries *(material — this feature is almost entirely boundaries)*

| Boundary | Preconditions | Human/Operator Action | What Must Wait |
| --- | --- | --- | --- |
| r8 disclosure run | r8 authorization committed (true), protected-input content fingerprints byte-current (true) | Operator executes `BeautyQProtectedBreakGlassMain` per runbook | Reserve authoring's case migration; materialization |
| Materialization commit | Agent patch validated; coordinator accepted patch | HUMAN commits rotation 8 (`R8`) | Fresh protected acceptance |
| Fresh acceptance run | rotation-8 materialization committed | Operator runs protected acceptance against the tracked canonical inputs | Bootstrap, review, promotion, verify, closeout |
| (if red again) new authorization | Fresh red evidence attributed to the evaluated inputs | HUMAN authorization boundary (out-of-plan step; ladder restarts) | Everything downstream |
| Bootstrap run | Green acceptance on the evaluated inputs | Operator bootstraps candidate | Review |
| Candidate review | Preserved aggregate artifacts + candidate | Coordinator/operator review + explicit approval/rejection | Promotion materialization |
| Promotion commit | Byte-for-byte copy in worktree + patch-local proofs + coordinator acceptance of patch | HUMAN stages and creates the promotion commit | Verify, closeout |
| Verify run | Promotion commit made; inputs byte-verified unchanged | Operator runs independent verify against the committed baseline's evaluation content | Documentation closeout |
| Closeout commit | Closeout patch prepared | HUMAN decides staging/commit of status updates | Nothing (feature end) |

## Validation and Evidence Plan *(mandatory)*

| Layer | What It Proves | Exact Scope / Owner | Source State Required | When It Runs |
| --- | --- | --- | --- | --- |
| Reconciliation audit (done at plan) | Derived boundary + classifications are re-derivable read-only | commands listed in research.md | `HEAD 47d90b9f`, clean index | already; auditable any time |
| Patch-local validation | Reserve/selection/freeze determinism; corpus/policy schema; focused eval + app-shell specs of the materialization patch | runbook freeze/audit reproduction + focused sbt specs scoped to `beautyq-search-gen2-eval`/`leaderboard-app-shell` | exact uncommitted worktree state; **no agent commit** | before materialization handoff |
| Protected acceptance evidence | Q2-B green gate on the evaluated inputs | runbook manual procedure; coordinator root-evidence acceptance of the corpus/policy fingerprints | post-materialization tracked canonical inputs | after human materialization commit |
| Candidate + review evidence | Aggregate-only candidate + explicit review decision | runbook review section; coordinator/operator | same evaluated inputs, unchanged bytes | after green acceptance |
| Promotion patch-local proof | Byte equality + digest + canonical-resource focused tests | eval module owners | uncommitted worktree copy; attributed to it | before human promotion commit |
| Independent verify | Closeout gate | `BeautyQAcceptedBaselineMain --mode verify` per runbook | committed baseline's evaluation content | after human promotion commit |
| Documentation closeout | Per-layer attributed status; owner status lines | this feature + each owner's closure contract | post-verify committed state | last |

Each layer stays its own layer: patch-local is never presented as acceptance evidence (FR-015); red stays
red; unavailable external resources produce **blocked**, distinguished from product failure (VI).

## Phase Artifact Decisions

| Artifact | Decision | Reason |
| --- | --- | --- |
| `research.md` | **REQUIRED — produced** | FR-001/FR-002 make the reconciliation record a first-class acceptance deliverable; it is the Phase 0 verdict source |
| `data-model.md` | N/A | The entities are delivery-process objects already owned by this spec's Key Entities and the eval resources' own codecs/schemas; inventing a data model here would duplicate owners and violate FR-018 |
| `contracts/` | N/A | No new interface: runner CLIs, evidence JSON schemas and the canonical resource are existing owner-defined contracts (runbook + eval code); this feature changes no contract |
| `quickstart.md` | N/A | Every runnable procedure is already owned by the runbook's protected-workflow sections; restating them duplicates a mutable owner (Principle II). Re-derivation commands for the reconciliation itself live in research.md |

## Risks, Restart Rules, and Open Questions

- **Fresh acceptance red again at rotation 8 (`R8`)**: boundary stops; a new (human) authorization decision is the
  next owner step; r8-era evidence stays attributed to the inputs it evaluated. Not a license to tune criteria.
- **Reserve-v7 authoring capacity**: if 8 independent acceptable-variant exact-intent cases cannot be
  authored against the canonical catalog, stop and report the authoring boundary; do not reuse consumed
  or disclosed cases, do not weaken the slice.
- **Source drift at any point before promotion**: owner restart rule applies — the changed source invalidates
  pending acceptance evidence and affected evidence reruns (FR-010); pending evidence never floats (Principle V).
- **External resource unavailability (ES/Qdrant/embedding)**: verification blocked ≠ passed; report names
  missing resource + owning role (FR-011, spec Failure semantics).
- **Governance instability at a later phase start**: re-derive constitution state at every phase entry;
  FR-017 stop-and-report if indeterminate.
- **Stale local artifacts** (Aug 6 candidate dir, r7-era target/ logs): read as attribution history only;
  never promoted into current evidence.
- **Open questions**: none for the boundary itself; reserve case authoring details are task-phase work
  under the existing authoring contract, not a planning unknown.

## Complexity Tracking

No material complexity exception introduced by this plan. Every step is an existing owner-defined
procedure sequenced from derived state; no new ledger, hashing system, review stage, or promotion
machinery (FR-018, Principle IX).
