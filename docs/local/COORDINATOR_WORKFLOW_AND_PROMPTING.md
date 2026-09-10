# COORDINATOR_WORKFLOW_AND_PROMPTING.md

Capability tags used by the coordinator: `[W]` CHEAP MODEL OK, `[M]` MID MODEL RECOMMENDED, `[S]` SMART MODEL REQUIRED, `[U]` USER/OPERATOR INVOCATION — NO DELEGATED DISPATCH. Task tags are used only where behavior differs, such as `[CONTINUATION]` and `[DOCS]`.

Purpose: canonical coordinator guide for source-truth gating, architecture and ownership decisions, user-invoked Spec Kit phase handoff, delegated patch prompting, patch acceptance, verification/evidence boundaries, bundles, documentation ownership, audits, and model recommendations.

Ownership boundaries:

- `.specify/memory/constitution.md` owns cross-feature project governance.
- `AGENTS.md` owns repository execution guardrails for agents.
- Spec Kit commands/templates/workflows own phase mechanics.
- `spec.md` / `research.md` / `plan.md` / `tasks.md` own feature-local workflow state.
- This file owns coordinator workflow, review, acceptance, dispatch choice, bundle strategy, and human-facing closeout.

A delegated repository agent does not need to read this guide merely because a coordinator will later review its work.

If this guide conflicts with the Constitution, stop and follow the Constitution. If this guide conflicts with `AGENTS.md` on repository execution safety, stop and request a focused documentation/control-plane correction rather than choosing silently.

---

# 1. Universal gates

## 1.1 Source-truth gate

Before patch design, phase briefing, or delegation, source-confirm the decision-critical facts: architecture, executable ownership, public and compatibility contracts, delete/retain boundaries, acceptance criteria, and the validation/evidence seam.

Imports, direct callers, local signatures, test renames, and compiler-driven repairs inside an already named module frontier are mechanically discoverable. They need not all be pre-inlined, although `[W]` prompts should include them when doing so prevents broad reading.

Docs, handoff summaries, memory, generated prose, and previous conclusions do not replace current source anchors.

If required decision-critical source truth is missing, stop with one of:

- `BLOCKED_NEED_BUNDLE`
- `BLOCKED_NEED_BUNDLE_SCOPE`
- `BLOCKED_NEED_CLOSEOUT_SCOPE`

Do not provide a patch proposal, exact edit recipe, delegated prompt, phase brief that presupposes the missing fact, or test recipe when required anchors are missing.

Do not salvage a source-incomplete task by inventing a source-independent helper, adapter, model API, field mapping, merge rule, or test plan.

Do not rescope a source-incomplete task into an adjacent "safe" patch unless the user explicitly approves that different task after seeing the missing-source report.

Do not use a stronger model to infer missing source truth.

## 1.2 Acceptance gate

A patch is accepted only when its original requested DoD is complete and it is commit-ready as-is.

Required completion work must never become a downstream option. If the patch still needs tests, docs, validation, branch coverage, source-truth checks, edge-case handling, or runbook coverage to satisfy the original task, the decision is not `ACCEPT`.

Use:

- `ACCEPT` — commit-ready as-is.
- `REJECT` — wrong direction, unsafe, or not worth continuing.
- `CONTINUE_SAME_PATCH` — same uncommitted diff must be completed before review can close.
- `CORE_DIRECTION_OK_BUT_NOT_ACCEPTED` — direction is right, required DoD remains.
- `BLOCKED_*` — source truth, evidence, human boundary, or closeout scope is missing.

Do not write an extended commit message for a patch that is not accepted.

Do not mark a patch commit-ready if known required work remains.

Downstream options are only for new work after the current patch is complete.

A happy-path implementation is not accepted when the original task required branch coverage, failure behavior, cancel behavior, compatibility behavior, or matrix behavior.

Generated Spec Kit artifacts are not self-accepting. The coordinator reviews the output of each material phase before recommending the next phase.

## 1.3 Declarative authoring gate

For a declarative business DSL or reusable framework patch, classify requested behavior as:

- `BUSINESS_CHOICE`: facts that legitimately vary by consumer;
- `DERIVABLE_EVIDENCE`: facts already fixed by types, selectors, declared inventories, or order.

The canonical declaration should state business choices and avoid hand-writing derivable evidence. Continue or reject a patch when it repeats selector/type/name/path/semantic facts, maintains parallel ordered lists, manually folds generated structures, or makes a generated view a second policy owner.

When the reusable kernel changes, require both a real consumer need and a structurally different neutral/tracer usage that challenges the reusable shape. A fixture calibrates representation; it must not invent production vocabulary.

Every such delegated prompt or phase brief must answer, directly or through accepted feature artifacts:

```text
Canonical entry point:
Domain-owned differences:
Framework-derived mechanics:
Reuse proof:
Executable owner:
```

Return `CONTINUE_SAME_PATCH` or `REJECT` when policy is unreachable, generic code contains consumer concepts, a consumer copies reusable mechanics, or generated output becomes a second policy owner.

## 1.3.1 No speculative defenses

Require a concrete reachable failure or real persistence, wire, backend, cursor, fingerprint, or public compatibility contract before adding safeguards.

Do not add defenses for overridden enum `toString`, reflection, malicious same-package callers, impossible nulls/states, or unspecified future changes.

Apply the stable-value rule owned by `AGENTS.md`: preserve task-defined typed IDs/codes and active order used by contract views; use `toString` only for incidental diagnostics. Reject speculative labels or wrappers, not named contract values.

## 1.4 No fake green

Unavailable external resources may cancel/resource-gate only when the test cannot verify its stated contract another valid way.

Reachable-but-broken resources fail red. Do not hide them behind saved data, fixtures, fallback branches, weaker checks, or saved-only downgrade.

If a selected path uses saved artifacts or fixtures, provenance must say so. Do not call it live coverage.

Evidence is never permission: generation, reconciliation, review, approval, promotion, verification, and closeout remain distinct whenever the active feature contract distinguishes them.

## 1.5 Protected source-truth invariant

Section 1.1 has higher priority than the requested output shape. Do not weaken it, move it into `AGENTS.md`, or emit a delegated patch prompt or execution-ready phase brief while decision-critical source anchors are missing.

A user may still invoke a read-only/reconciliation phase whose explicit purpose is to obtain the missing source truth; in that case the brief must describe the evidence question without presupposing its answer.

## 1.6 Coordinator invariants

These invariants are applied by the coordinator/primary review role. They are general workflow rules; product- or milestone-specific history does not belong here.

- **Never supply a free-text `Starting revision` / `Starting HEAD` as authority.** The executing phase or delegated agent derives the actual evaluated source state from the repository — the accepted committed boundary, or the exact uncommitted worktree/index state under test.
- **Do not introduce revision plumbing for coordination.** Do not ask the user for commit SHAs, invent revision labels, add Git-cleanliness gates, or add/reshape runner APIs merely to carry `HEAD` through the workflow. Use the runner/evidence contract that already exists; change revision handling only when the active product/feature owner explicitly requires revision selection as part of the behavior being delivered.
- **Coordinator must not invent repository changes without concrete business value.** Do not add or request new production/test code, runners, abstractions, manifests, gates, provenance plumbing, validation layers, or documentation ceremony merely to make coordination, evidence, governance, or an internally invented invariant cleaner. Before proposing any coordinator-initiated repository change, identify the concrete customer, production, operator, quality-decision, or irreversible-risk failure it prevents and show why existing code/tests/CI/Git/Spec Kit do not already cover it. If that justification is absent, do not change the repository.
- **Fix coordinator mistakes at the coordinator layer first.** When the user's request is to simplify prompts, handoffs, or coordination behavior, change the coordinator workflow/prompting rules only. Do not reinterpret that request as authorization to redesign product/test infrastructure. Repository changes require either an explicit user request or a source-confirmed active-owner requirement tied to concrete product/operational value.
- **Source-confirm sbt project IDs.** Project IDs come from `build.sbt` / the build graph, not directory names.
- **Reports are delta-only.** Do not require a worker or phase to restate facts the reviewer can recover directly from the patch or durable artifact. Mention files only for scope deviations or generated/untracked evidence; explain rationale only when it is not visible from code, artifact, or diagnostic.
- **Git index and history are human-owned.** No agent commits, stages, unstages, or stashes. Human-created staged state is preserved exactly as found. Reports distinguish `HEAD`, index, and worktree when material.
- **Coordinator must not create, request, recommend, or manage Git worktrees.** Never use or ask the user to use `git worktree ...`; parallel work must be sequenced in the current human-selected checkout.
- **Coordinator must not leave decision-critical or expensive-to-regenerate boundary artifacts only under `target/` or another cleanup-prone path.** Before ending a phase or asking the user to proceed, preserve an exact copy in the owner-defined durable evidence location; if no such owner exists, capture an external coordinator survival copy outside the repo. Ephemeral copies never become authority, but their loss must not force re-execution.
- **Patch validation and downstream acceptance evidence are different boundaries.** Patch-local validation may run against an exact uncommitted state. Feature acceptance evidence that requires a committed immutable identity waits for the human-created boundary.
- **Never amend or rewrite an evidence-bearing committed revision.** Later edits produce a new evaluated state. Earlier evidence remains attached to the state that produced it and is never relabeled onto the new state.
- **Do not chain user-owned workflow phases through agent instructions.** A material phase ends, the coordinator reviews it, and only then may the coordinator recommend the next user invocation.

## 1.7 User-owned Spec Kit invocation invariant

All `/speckit.*` control-plane commands are **USER/OPERATOR invocations**, including at minimum:

- `/speckit.constitution`
- `/speckit.specify`
- `/speckit.clarify`
- `/speckit.plan`
- `/speckit.tasks`
- `/speckit.analyze`
- `/speckit.checklist`
- `/speckit.converge`
- `/speckit.implement`
- `/speckit.taskstoissues`

The user/operator creates the phase boundary by invoking the command.

The coordinator may:

- recommend which command comes next;
- recommend a model/tier for the phase;
- prepare a detailed phase brief;
- name the exact intended feature and expected phase outputs;
- review the phase result before another phase is invoked.

The coordinator MUST NOT:

- tell a delegated repository agent to run or invoke `/speckit.*`;
- hide `/speckit.*` invocation inside a `Task:` prompt;
- ask the current delegated agent to chain into the next Spec Kit phase;
- treat tracked files created by a Spec Kit phase as proof that the invocation itself was delegated patch work;
- treat a plain agent prompt that happens to contain `/speckit.plan` (or another slash command) as equivalent to the user invoking that command.

A user-invoked Spec Kit command may itself launch a model and may create tracked artifacts. That does not transfer ownership of the invocation from the user to a delegated repository agent.

---

# 2. Execution forms and role split

There are three distinct execution forms. Choose one before writing any prompt or handoff.

## 2.1 Coordinator / primary review work

Coordinator owns:

- architecture and ownership decisions;
- source-truth sufficiency decisions;
- scope decomposition;
- acceptance/rejection decisions;
- review of Spec Kit phase outputs;
- bundle scope and evidence-capture strategy;
- model/tier recommendation;
- downstream sequencing;
- human-facing extended commit messages.

Coordinator review work may be read-only and may use focused bundles.

The coordinator owns the **interpretation and decision**, not necessarily every mechanical read. A user-invoked Spec Kit research/planning phase may perform bounded mechanical reconciliation when its accepted phase brief requires it.

## 2.2 User/operator-invoked Spec Kit phase

Use this form for a Spec Kit control-plane phase.

The user invokes the slash command. The coordinator supplies a **phase brief**, not a delegated patch task.

Required handoff form:

```text
USER/OPERATOR STEP

Invoke: /speckit.<phase>

Feature:
<explicit feature path/name when applicable>

Recommended workflow model:
<outside the brief>

Phase brief:
<what this already-invoked phase must accomplish>
```

Rules:

- The brief describes the work **inside the already selected phase**.
- The brief must not say `Run /speckit.plan`, `Invoke /speckit.tasks`, or equivalent.
- The phase brief must not ask its model to create the phase transition.
- Always name the intended feature when the phase is feature-scoped.
- Do not assume an ignored mutable current-feature pointer is correct when concurrent features exist. Source-confirm the intended feature selection and report ambiguity rather than silently targeting another feature.
- A phase may materialize tracked artifacts; this still remains a user-invoked phase, not a delegated `Task:`.
- Review material phase output before recommending the next phase. Do not auto-chain `specify → plan → tasks → implement` through coordinator instructions.
- Do not make this coordinator guide mandatory reading for an ordinary phase model unless that phase is explicitly acting as coordinator/reviewer. Phase execution should rely on Constitution, repository guardrails, phase command/template, and accepted feature artifacts.

## 2.3 Delegated patch task

Use delegated agents for bounded tracked edits plus the focused validation required to prove those edits.

A delegated task normally has an expected non-empty tracked patch.

Allowed reusable label:

- `Task:`

Do not label a user/operator phase brief `Task:`.

Do not dispatch validation-only, evidence-only, inventory-only, architecture-only, acceptance-only, or review-only work as a delegated patch task merely to keep a model busy. Those belong to coordinator/user work or to an explicitly user-invoked Spec Kit research/planning phase.

The coordinator must resolve architecture before delegation; never leave a delegated patch agent with `NEED_ARCHITECTURE_DECISION`.

## 2.4 Feature-state ownership

With Spec Kit in use, feature workflow state belongs primarily to its artifacts:

- `spec.md` — required behavior, acceptance, non-goals, decision object;
- `research.md` — source-confirmed research/reconciliation and evidence-backed findings;
- `plan.md` — technical path, ownership, boundaries, validation/evidence strategy;
- `tasks.md` — executable decomposition and dependency order.

Direct prompts and phase briefs should add **delta**, not become shadow owners for facts already accepted in those artifacts.

Do not keep long-lived feature status, task state, candidate inventories, acceptance ladders, or implementation history only in conversation prompts.

---

# 3. Review closeout and next-action selection

## 3.1 Accepted closeout shape

After an accepted review:

1. State the current result and remaining uncertainty.
2. Provide an extended commit message for accepted non-trivial patch work; the patch stays uncommitted for the human.
3. If the user already selected a source-confirmed follow-up, classify the next action:
   - **delegated tracked edit** → provide one delegated `Task:` prompt;
   - **user/operator Spec Kit phase** → provide one `USER/OPERATOR STEP` with the exact slash command and a coordinator-prepared phase brief;
   - **human Git/decision boundary** → state the human action and what waits on it; do not fabricate a model task.
4. Put model/tier recommendation outside delegated prompts and outside the phase brief.
5. Provide zero to two genuine downstream options only when useful; recommend one only when there is a real choice.
6. Provide a bundle script only when further source capture or remote handoff is actually needed.

Mandatory review and verification are plumbing, not downstream options.

A user reply containing only an option number selects work; it is not evidence.

Do not manufacture alternatives after the task is complete.

## 3.2 Verification

Delegated workers validate only as part of a patch-producing task and never run an unscoped full repository suite.

A user-invoked phase may perform the checks that belong to that phase's accepted contract; it must still report exact scope and evaluated state and must not silently substitute a broad unrelated suite.

The coordinator/primary may run an exact broader command only when the user explicitly requests it. Otherwise use focused checks and ask the user in plain language to run the exact broader command before committing when that confidence is warranted.

Any coordinator-provided sbt validation uses one chained batch invocation (`sbt --batch --no-global ...`) for the requested validation phase. Never emit plain/interactive `sbt ...` or multiple standalone sbt launches when one chained invocation can cover the same checks.

Every report states exactly what ran, against which evaluated source state (a committed revision, or the exact uncommitted worktree/index state actually tested), and what remains unknown.

Do not encode confidence as synthetic status labels.

Where the feature contract requires downstream evidence against a committed immutable identity, that evidence waits for the human commit; say so instead of substituting uncommitted results.

## 3.2.1 Risk-to-validation matrix

| Change shape | Minimum focused validation |
|---|---|
| Pure model, parser, policy, or codec helper | Owning spec |
| Public API, wire codec, or route contract | Owning spec plus route/wire contract |
| Build edge or module boundary | Owning production compile plus boundary/firewall spec |
| DI, plugin, or activation change | Focused graph/wiring proof; broader full command only by explicit user request, otherwise ask the user to run it |
| Lifecycle or external resource behavior | Scripted/in-process contract plus focused communication test when the harness is available |

Use the narrowest row that covers every changed risk layer. Split unrelated risk layers rather than validating them with one oversized command.

## 3.3 Human Git and commit messages

No agent commits in any role. No agent stages, unstages, or stashes. Git index/history authority belongs only to the human.

This section is human-facing acceptance closeout work. Produce it after an accepted non-trivial patch, not as a delegated-agent reporting duty.

For accepted non-trivial patch work, prepare an extended commit message containing:

- subject;
- what changed;
- why;
- preserved boundaries/non-goals;
- user-visible effects where relevant;
- verification actually performed;
- known limitations where material.

Do not prepare an extended commit message for `CONTINUE_SAME_PATCH`, `CORE_DIRECTION_OK_BUT_NOT_ACCEPTED`, `REJECT`, or `BLOCKED_*`.

Do not imply that focused checks cover the full repository.

A feature contract may require more than one human-created commit boundary. Do not collapse distinct evidence or closeout boundaries merely to minimize commit count.

---

# 4. Spec Kit phase handoff rules

## 4.1 Phase boundary

A Spec Kit slash command is a user-owned workflow boundary, not text that a delegated agent should interpret as a command to emulate.

Correct:

```text
USER/OPERATOR STEP

Invoke: /speckit.plan

Feature:
specs/002-example

Phase brief:
Reconcile the actual source state first, then produce a technical plan from the first unfinished accepted boundary.
```

Incorrect:

```text
Task:
Run /speckit.plan for 002-example.
```

Also incorrect:

```text
Continue in the same agent session.
Run /speckit.plan.
```

The second form is still a plain agent instruction unless the user actually invoked the slash command through the Spec Kit control plane.

## 4.2 No automatic phase chaining

After a material phase:

1. phase finishes;
2. coordinator reviews artifacts and source/evidence implications;
3. coordinator accepts, continues, rejects, or blocks;
4. only then may the coordinator recommend the next user-owned phase.

Do not instruct a phase model to invoke the next slash command.

Do not treat a command's “next step” prose or auto-send affordance as coordinator acceptance.

The generic upstream `.specify/workflows/speckit/workflow.yml` ("Full SDD Cycle") is not a BeautyQ coordinator execution path. BeautyQ never uses that generic workflow to chain material phases: each material `/speckit.*` phase is separately selected and reviewed with the coordinator and separately invoked by the USER, and ordinary prompt/dofix work may happen between invocations. A workflow-internal approve/reject gate is not a substitute for this boundary.

## 4.3 Phase brief economy

A phase brief should carry only what the phase needs beyond its existing owners.

Prefer references to accepted feature artifacts over copying them.

For example:

- `/speckit.specify` brief: product/delivery/research decision object, hard scope, source-owner navigation, non-goals.
- `/speckit.plan` brief: decision-critical research questions, exact reconciliation expectations, required source/evidence owners, human boundaries.
- `/speckit.tasks` brief: special dependency/gate semantics not already recoverable from accepted plan.
- `/speckit.implement` brief: only execution-specific constraints not already present in accepted tasks/repository guardrails.

Do not repeat the full coordinator workflow inside a phase brief.

## 4.4 Explicit feature selection

For a feature-scoped phase, the coordinator handoff must identify the intended feature explicitly.

When multiple feature directories coexist:

- never infer the intended feature solely from an ignored mutable pointer;
- source-confirm what the phase will target;
- if command machinery cannot unambiguously target the selected feature, stop and repair the control plane rather than allowing a best guess.

This rule exists to prevent planning or task generation for one feature from silently writing into another.

---

# 5. Delegated prompt rules

## 5.1 Source-truth gate before prompt

Apply section 1.1 before writing a delegated prompt. Close decision-critical seams and request focused bundles only for missing decision evidence.

Do not duplicate mechanically discoverable imports, callers, or local signatures for `[M/S]` unless they are known traps.

The prompt must not ask the delegated agent to reconstruct missing architecture or deletion inventory with broad repository search. Bounded discovery is allowed only inside the approved edit frontier when needed to complete the tracked patch.

Do not say "use attached bundle". Inline the relevant facts.

## 5.2 Required delegated prompt shape

Every delegated edit prompt includes:

```text
Task: exact bounded change

Available local sources:
- exact files/ranges/symbols and why each may be needed

Edit boundary:
- edit targets or manifest
- retained owners
- forbidden changes

Current facts:
- only facts that change an edit decision or prevent broad discovery

Goal:
- exact behavior/tests/docs outcome

Validation:
- focused commands
- exact post-fix rerun rule
- do not run an unscoped full repository suite

Report:
- runtime or generated evidence not recoverable from the patch
- focused command results
- diagnostic-driven deviations from the requested plan
- remaining uncertainty

Scope expansion:
- exact files/symbols/diagnostics
- why the current task cannot close without them
- smallest next action
```

Reports are delta-only. Do not require lists of changed files or restatement of diff-visible edits when the reviewer can recover them from the patch.

Model-tier recipe:

- `[W]` Inline exact paths and material signatures, imports, constructors, fixtures, manifests, diagnostics, and replacement hunks when doing so prevents broad repository reading.
- `[M]` Inline decision-critical seams, invariants, manifests, and known traps. Allow bounded lookup of stable local definitions and named dependency frontiers.
- `[S]` Inline outcome, acceptance criteria, forbidden boundaries, known evidence, and source contradictions. Architecture and ownership decisions remain coordinator-owned. Allow bounded source discovery and reconciliation within those decisions.
- `[W][CONTINUATION]` When exact hunks and current diagnostics are supplied, tell the model not to pre-read the whole source list.
- `[M/S]` For new code, owner-map changes, or `[DOCS]` reconciliation, allow reading the canonical owner and nearby tests before editing.

Do not paste all of `AGENTS.md` into prompts. Inline only task-specific hazards the chosen model is likely to violate.

Historical context stays in coordinator review unless it changes the edit.

## 5.2.1 Continuation and context control

- `[CONTINUATION]` Preserve the same uncommitted patch; a fresh session is a handoff, not a separate logical task.
- Before a delegated handoff or compaction, save a checkpoint under `target/agent-checkpoint/` containing raw `git status --short`, `git diff --stat`, `git diff --name-status`, completed edits, the last successful command, the complete current failure, and the next exact command.
- Checkpoints under `target` are ephemeral and may be deleted by build cleanup. They must not be the only copy of decision-critical continuation state.
- The next prompt inlines the checkpoint summary and does not repeat discovery or already-successful checks without a source-confirmed reason.
- `[W]` Prefer exact snippets and replacement hunks. `[M]` Allow bounded mechanical closure inside named frontiers. `[S]` Prefer bounded source inspection over duplicating large stable source.
- Large documents, diffs, successful logs, status, and inventories stay in repo-local artifacts; include only summaries and unexpected excerpts in the conversation.
- Follow `AGENTS.md` for diagnostics, sbt execution, deletion safety, and failure triage instead of repeating those rules here.
- `[W][DOCS]` Multiple large documents may use a fresh docs continuation in the same uncommitted patch with exact replacement anchors; do not defer required live documentation to a later logical task.
- Split on observable triggers: a new architecture/ownership decision, a diagnostic opening several unplanned retained owners, a second unrelated root cause, repeated compile/search cycles, a generated result that determines later edit content, or lost verified state after compaction.

## 5.2.2 Prompt and execution economy

- Distinguish `Read first` from the edit and validation manifest. Pre-read only decision-critical owners, exact templates, and current diagnostics.
- Inline each decision-critical fact once, preferably as a compact manifest or table.
- Omit coordinator self-talk, inherited repository rules, and facts that neither change an edit decision nor prevent broad discovery.
- For large multi-module patches, run compile and pure owning specs before managed or external-resource suites. Rerun only the failed layer and downstream layers affected by its fix.
- With Spec Kit features, do not re-encode the accepted `spec.md` / `research.md` / `plan.md` / `tasks.md` into a huge delegated prompt. The prompt should identify the task boundary and only add the delta necessary for safe execution.

## 5.3 Runtime safety inheritance

Do not duplicate Scala, test-double, unsafe-extraction, DI, lifecycle, HTTP, or sbt rules from `AGENTS.md`.

Inline only the task-specific hazard the chosen model is likely to violate.

Coordinator-emitted sbt commands must satisfy section 3.2.

---

# 6. Manual/env-gated/cancel-by-default test DoD

If a patch adds or changes env vars, BEGIN/END markers, saved artifacts, manual local-service flows, or cancel/resource-gated behavior, it can be accepted only after its contract is complete.

Required before `ACCEPT`:

- selection/cancel/fail-red behavior covered;
- saved/live/manual inputs documented in code, tests, docs, or runbook as required by the task;
- no required branch coverage deferred as downstream work;
- no hidden fixture fallback unless explicitly requested;
- reachable-but-broken resources fail red;
- unavailable resources cancel/resource-gate only when no valid contract can be checked;
- cancel count reported when an explicitly requested broader run was performed under section 3.2.

If any required item remains, use `CONTINUE_SAME_PATCH`.

---

# 7. Bundle rules

Bundles are focused coordinator evidence capture and handoff transport.

After reading a bundle, the coordinator must use the relevant facts in its review/decision/brief. Do not make a delegated agent rediscover the entire bundle.

If decision-critical anchors are still missing after review, request one focused supplemental bundle and stop.

## 7.0 Who creates bundles

Delegated patch agents do not create coordinator review bundles, zip archives, grep-report archives, or evidence-only capture tasks.

Source-truth and closeout bundles are coordinator-owned capture designs executed by the user/operator (or by a primary environment acting on the user's behalf when explicitly available).

Generated artifacts that are part of a patch's actual runtime/product DoD are not review bundles and may be produced only inside the owning patch/phase contract.

## 7.1 Bundle scripts must not mutate repository state

Allowed:

- `git status`;
- bounded `rg` / `sed`;
- compact diffs;
- relevant source/test/docs anchors;
- untracked/ignored manifests when material;
- truncation;
- zip handoff.

Forbidden:

- `sbt` or tests;
- Docker cleanup/startup;
- network/resource probes;
- package managers;
- deleting `target`;
- staging/unstaging/stash/commit;
- heavy or unrelated commands.

Do not include full `target`, generated build output, screenshots, stale numbered files, or broad `HEAD~N --patch` unless explicitly requested.

## 7.2 Required bundle shape

Coordinator review bundles are transport artifacts, not repository artifacts. Their capture workspace and archive MUST be outside the repository so bundle creation cannot create ignored/untracked repository dirt or alter repository status.

When explicitly requested, a bundle must:

- create its workspace under the OS/user temporary directory, preferring `${TMPDIR:-/tmp}`;
- use a unique temporary workspace, e.g. `mktemp -d "${TMPDIR:-/tmp}/beautyq-<topic>-XXXXXXXX"`;
- place the resulting archive outside the repository as well, normally `ZIP="${OUT}.zip"`;
- read repository state/source only with non-mutating commands;
- include task-relevant status, bounded anchors, diffs, and manifests;
- use NUL-safe untracked-file capture when untracked files matter;
- truncate large text output;
- include the bundle ID in artifact names where useful;
- print artifact and zip sizes;
- print final workspace/archive paths;
- copy the archive with:

```bash
cpf "$ZIP"
echo "$ZIP"
```

If `cpf` is unavailable, still create and print the archive path and report the deviation. Do not substitute a different clipboard command.

A repository-owned capture script may be reused only if its output location obeys this outside-repository transport rule; otherwise provide a task-specific compact coordinator script.

Do not create `.review-bundles/` or another bundle workspace in the repository merely for coordinator review transport.

Do not embed a universal multi-page bundle skeleton in this guide.

## 7.3 No shell/session hazards

User-facing copy-paste terminal commands must not close, replace, or mutate the user's shell behavior.

Forbidden:

- `exit`;
- `exec`;
- `kill $$`;
- terminating traps;
- `set -e`;
- `set -u`;
- `set -o pipefail`.

Use local command checks and conditional branches. On failure, print `BLOCKED` or `MISSING`, skip dependent steps, and still print diagnostics.

## 7.4 Transport bundles versus evidence workspaces

Coordinator review bundles do **not** inherit agent scratch-location rules from `AGENTS.md`. They are outside-repository transport artifacts governed by section 7.2.

Do not generalize that transport rule to feature-owned or owner-defined evidence.

- Agent scratch/build/generated work follows the repository execution rules that apply to the executing agent.
- Feature-owned durable artifacts stay in their canonical repository owners.
- Multi-iteration evidence workspaces use the location defined by their active evidence owner; when that owner currently defines `./.evidence-runs/<run-id>/`, preserve that contract rather than moving the evidence to OS temp.
- A coordinator transport ZIP may contain copies/references needed for review, but the ZIP itself is never promoted into evidence authority merely because it exists.

Evidence authority follows the active owner contract and exact identity semantics, not trackedness or transport location alone.

---

# 8. Documentation and artifact ownership

Before changing docs or generating feature artifacts, identify the canonical owner of the fact.

Keep:

- cross-feature governance in the Constitution;
- stable repository execution behavior in `AGENTS.md`;
- coordinator review/dispatch workflow in this file;
- Spec Kit phase mechanics in `.opencode/commands/speckit.*`, `.specify/templates/**`, and `.specify/workflows/**`;
- product/domain policy in product documentation;
- feature requirements/status/gates in `spec.md`;
- feature research/reconciliation in `research.md`;
- technical decisions and validation/evidence strategy in `plan.md`;
- executable dependency decomposition in `tasks.md`;
- task-local execution delta in delegated prompts;
- volatile verification counts/outcomes in reports or owner-defined evidence artifacts.

Do not duplicate long API lists, task state, metrics, bundle logic, feature acceptance ladders, or prompt rules across owners.

Duplicate only short safety-critical guardrails that must be visible at multiple entry points.

A prompt is not durable feature memory.

When asked to edit this guide or another standalone text owner whose complete current source is available, the coordinator should prefer producing a ready replacement artifact itself instead of dispatching an agent merely to edit one deterministic file.

---

# 9. Model recommendations

Model/tier selection is a coordinator concern, not an executing agent's identity problem.

Keep model recommendations outside delegated prompts and outside the phase brief.

Recommend the cheapest capability/cost tier likely to complete the task without expensive retries.

Capability/cost labels:

- `CHEAP MODEL OK` — narrow mechanical patches, exact replacements, well-bounded dofixes whose anchors and validation are already known.
- `MID MODEL RECOMMENDED` — source-confirmed multi-module work, bounded dependency closure, typical feature/refactor patches.
- `SMART MODEL REQUIRED` — source reconciliation, architecture/ownership changes, high-risk lifecycle/compatibility work.
- `USER/OPERATOR INVOCATION` — the user must invoke the workflow/control-plane command. The invoked phase may still use a model; this label means **no delegated dispatch of the invocation itself**.

Decision order:

1. source-truth sufficiency;
2. execution form: coordinator review vs user phase vs delegated patch;
3. task type and risk;
4. expected discovery/output size;
5. user preference;
6. capability/cost tier.

Do not use a stronger model to invent missing source truth.

Do not ask an executing agent to reason about whether it is “weak”, “mid”, “senior”, or “coordinator”. Give it the task/phase contract appropriate to the selected tier.

---

# 10. Senior audit playbook

Senior audit interpretation, architecture/ownership conclusions, and source-sufficiency decisions are coordinator-owned.

Mechanical read-only mapping may be performed:

- directly by the coordinator;
- through a focused user-executed bundle;
- inside an explicitly user-invoked Spec Kit research/planning phase whose accepted brief requires it.

A mechanical mapper does not acquire acceptance authority.

Run audit waves independently and read-only. Each wave ends with source-confirmed edit seams, no issue found, or a focused evidence request.

An audit finding never skips the source-truth gate and never becomes an implementation permission by itself.

Generic waves:

1. documentation claims versus current source;
2. project goals versus measured evidence;
3. architecture and ownership boundaries;
4. dependency injection, lifecycle, roots, and activation;
5. typed errors, effects, resource safety, and mutable-test creep;
6. test taxonomy and real-resource coverage;
7. readiness, compatibility, fingerprints, and state markers;
8. generated reports, golden data, and ownership drift;
9. external decoder, API, and persisted-shape drift.

---

# 11. Pre-send / pre-phase / pre-accept checklist

## 11.1 Before selecting the next execution form

- Is the architecture/ownership decision already resolved?
- Are decision-critical source anchors confirmed?
- Is the next action coordinator review, a user-invoked Spec Kit phase, a delegated tracked edit, or a human boundary?
- Am I choosing that form explicitly rather than treating every follow-up as a delegated prompt?
- If the next action is `/speckit.*`, am I giving the user an invocation + phase brief rather than telling an agent to run the command?
- If several features coexist, is the intended feature explicit and source-confirmed?
- Does the next action preserve human-only Git index/history?

## 11.2 Before sending a user/operator Spec Kit phase handoff

- Is the exact slash command named outside the brief?
- Is the intended feature explicit?
- Has the previous material phase been reviewed/accepted before recommending this one?
- Does the phase brief describe only what the already-invoked phase should accomplish?
- Does the brief avoid `run /speckit.*`, `invoke /speckit.*`, or any request to self-chain phases?
- Are model/tier recommendations outside the brief?
- Am I relying on accepted `spec.md` / `research.md` / `plan.md` rather than duplicating them?
- If current-feature selection is ambiguous, have I stopped rather than guessing?

## 11.3 Before sending a delegated patch prompt

- Does the task have an expected non-empty tracked patch?
- Is the prompt sized for the chosen model tier?
- Are edit targets, retained owners, and forbidden changes explicit?
- Does the prompt avoid user-owned slash-command invocation?
- Does a continuation include the standard checkpoint: status, diff summaries, completed edits, last success, complete failure, next command?
- Are large outputs kept in repo-local artifacts?
- Does validation follow section 3.2 and the risk-to-validation matrix?
- Are feature facts referenced from their accepted artifacts rather than re-owned by the prompt?
- Is any rule duplicated unnecessarily?

## 11.4 Before accepting a patch or phase output

- Is the original requested behavior/phase object complete?
- Are required focused tests/docs/branches/edge cases complete for this object?
- Are ownership and compatibility boundaries preserved?
- Are known deviations and source contradictions resolved?
- Is evidence attributed to the exact evaluated source state?
- Is a stronger state being claimed from weaker evidence?
- If a human decision/commit boundary remains, is the object correctly left open/waiting?
- For patch work, is the extended commit message only about the accepted change, rationale, boundaries, and verification actually performed?
- Is broader verification requested separately when warranted?
- Does the accepted object have one cohesive architectural purpose rather than unrelated risk layers?
- Is the next phase still a separate user invocation rather than an automatic continuation?
