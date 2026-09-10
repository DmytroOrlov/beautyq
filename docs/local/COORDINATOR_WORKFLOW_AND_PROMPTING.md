# COORDINATOR_WORKFLOW_AND_PROMPTING.md

Purpose: canonical coordinator guide for source-truth gating, acceptance, Spec Kit handoff, delegated work, validation, bundles, documentation ownership, Git boundaries, and human-facing response order.

Ownership:
- `.specify/memory/constitution.md` — cross-feature governance.
- `AGENTS.md` — repository execution guardrails.
- Spec Kit commands/templates/workflows — phase mechanics.
- `spec.md` / `research.md` / `plan.md` / `tasks.md` — feature-local state.
- This file — coordinator review, dispatch, acceptance, evidence/bundle strategy, model/context choice, and human-facing closeout.

If this guide conflicts with the Constitution, follow the Constitution. If it conflicts with `AGENTS.md` on repository execution safety, stop and request a focused control-plane correction.

---

# 0. Human-facing protocol

## 0.1 Actionable artifact first

If the response implies an immediately usable artifact, put it **first, before analysis**.

Priority cases:
- accepted non-trivial patch → expanded commit message first;
- delegated next step → ready-to-copy prompt first;
- bundle needed → copy-paste bundle script first;
- human terminal/Git/decision step → exact command/action first;
- deterministic standalone text correction with complete source available → corrected file first.

Then explain the result, uncertainty, and rationale.

If no actionable artifact applies, answer normally.

This rule is a safety rail and overrides narrative ordering elsewhere in this guide.

## 0.2 Execution-mode label

Before every prompt or phase handoff, emit exactly one human-facing label:

1. `REAL USER/OPERATOR-INVOKED SPEC KIT COMMAND + PHASE BRIEF — NOT EMULATION`
2. `SPEC KIT EMULATION — PLAIN PROMPT, NOT A REAL INVOCATION`
3. `PLAIN DELEGATED AGENT TASK — NO SPEC KIT INVOCATION / NO EMULATION`

These modes are not interchangeable. Emulation may reproduce useful phase-like reasoning, but it does not create Spec Kit phase state, authority, or a control-plane boundary.

## 0.3 Context choice

`CONTEXT CHOICE` is for the human, outside the executing prompt:

```text
CONTEXT CHOICE: SAME <context/session>
```

or:

```text
CONTEXT CHOICE: NEW / EMPTY context
```

Use `SAME` when continuation state or avoided rediscovery matters and independence is not required. Use `NEW / EMPTY` for independent review, adversarial checking, or contamination avoidance.

Do not put this bookkeeping line inside the agent prompt unless session provenance is itself part of the task.

## 0.4 Model / thinking effort

Model choice is also human-facing and stays outside the prompt/phase brief:

```text
MODEL / THINKING EFFORT: GPT-5.6 Sol — low-med
MODEL / THINKING EFFORT: GPT-5.6 Sol — high-xhigh
MODEL / THINKING EFFORT: GPT-5.6 Sol — max
```

Current policy:
- `low-med` — bounded mechanical work, dofixes, deterministic reconciliation, routine validation, cheap preparation;
- `high-xhigh` — difficult source reconciliation, adversarial review, multi-module semantic comparison, architecture/ownership-sensitive judgment;
- `max` — **escalation court**, not default; use only when `high-xhigh` is materially insufficient or the decision is unusually costly, ambiguous, or irreversible.

Choose the cheapest sufficient effort. Never use a stronger model to invent missing source truth.

The exact effort vocabulary may change later; update it centrally here.

---

# 1. Universal gates

## 1.1 Source-truth gate

Before patch design, phase briefing, or delegation, source-confirm decision-critical facts:
- architecture and executable ownership;
- public/compatibility contracts;
- delete/retain boundaries;
- acceptance criteria;
- validation/evidence seam.

Docs, chat summaries, memory, generated prose, and previous conclusions do not replace current source anchors.

Mechanically discoverable details inside an already named frontier — imports, direct callers, local signatures, test renames, compiler repairs — need not all be pre-inlined.

If decision-critical source truth is missing, stop with a focused block such as:
- `BLOCKED_NEED_BUNDLE`
- `BLOCKED_NEED_BUNDLE_SCOPE`
- `BLOCKED_NEED_CLOSEOUT_SCOPE`

Do not:
- invent a helper, API, field mapping, merge rule, or test plan to compensate;
- silently rescope into an adjacent “safe” task;
- infer missing truth with a stronger model.

A read-only/reconciliation task whose explicit purpose is to obtain the missing truth is allowed.

## 1.2 Acceptance gate

Accept only when the original requested DoD is complete and the object is commit-ready as-is.

Use:
- `ACCEPT`
- `REJECT`
- `CONTINUE_SAME_PATCH`
- `CORE_DIRECTION_OK_BUT_NOT_ACCEPTED`
- `BLOCKED_*`

Required tests/docs/branches/validation cannot become downstream options.

Generated Spec Kit artifacts are not self-accepting. Review each material phase before recommending the next one.

Do not write an expanded commit message for a non-accepted patch.

## 1.3 Declarative/reusable authoring

For declarative DSL/framework work, separate:
- `BUSINESS_CHOICE` — facts consumers legitimately choose;
- `DERIVABLE_EVIDENCE` — facts already fixed by declared types/selectors/inventories/order.

Canonical declarations state choices and derive evidence. Reject or continue work that creates parallel policy owners, hand-maintained generated views, repeated selector/type/path facts, or copied reusable mechanics.

For reusable-kernel changes require:
- a real consumer need;
- a structurally different neutral/tracer use that challenges the reusable shape.

Every relevant task/phase must have clear answers for:
```text
Canonical entry point:
Domain-owned differences:
Framework-derived mechanics:
Reuse proof:
Executable owner:
```

## 1.4 No speculative defenses

Require a reachable failure or a real persistence/wire/backend/cursor/fingerprint/public-compatibility contract before adding safeguards.

Do not add defenses for impossible or unspecified states merely because they are imaginable.

Preserve task-defined typed IDs/codes and active contract order; use `toString` only for incidental diagnostics.

## 1.5 No fake green

Unavailable external resources may cancel/resource-gate only when the stated contract cannot be verified another valid way.

Reachable-but-broken resources fail red. Do not hide them behind saved fixtures, fallback branches, or weaker checks.

Saved/live/manual evidence must be labeled truthfully.

Evidence is not permission: generation, reconciliation, review, acceptance, promotion, verification, and closeout remain distinct when the active contract distinguishes them.

## 1.6 Coordinator invariants

- Never use a free-text “Starting revision/HEAD” as authority; derive evaluated state from the repository.
- Do not add revision plumbing, runner APIs, manifests, gates, or provenance machinery merely for coordination neatness.
- Coordinator-initiated repository changes require concrete product/operator/quality/risk value not already covered by code/tests/CI/Git/Spec Kit.
- Fix coordinator/prompting mistakes at the coordinator layer first.
- Source-confirm sbt project IDs from `build.sbt`/build graph, not directory names.
- Reports are delta-only; do not require workers to restate diff-visible facts.
- Git index/history are human-owned: agents do not commit, stage, unstage, or stash.
- Never create/request/recommend `git worktree`; sequence work in the current human-selected checkout.
- Decision-critical or expensive-to-regenerate boundary artifacts must not exist only under cleanup-prone paths such as `target/`.
- Patch-local validation may run against an exact uncommitted state; committed-identity acceptance evidence waits for a human commit when the contract requires it.
- Never relabel evidence from one committed state onto another.
- Never auto-chain user-owned workflow phases through agent instructions.

---

# 2. Execution forms

Choose the execution form before writing the handoff.

## 2.1 Coordinator / primary review

Coordinator owns:
- architecture/ownership decisions;
- source-truth sufficiency;
- scope decomposition;
- accept/reject/continue/block decisions;
- review of Spec Kit outputs;
- bundle/evidence-capture scope;
- model/context recommendation;
- sequencing;
- human-facing commit messages.

Mechanical reads may be delegated, but acceptance authority does not transfer.

## 2.2 Real user/operator Spec Kit phase

All `/speckit.*` control-plane commands are user/operator invocations, including:
`constitution`, `specify`, `clarify`, `plan`, `tasks`, `analyze`, `checklist`, `converge`, `implement`, `taskstoissues`.

The user creates the phase boundary by invoking the command.

Required handoff shape:

```text
REAL USER/OPERATOR-INVOKED SPEC KIT COMMAND + PHASE BRIEF — NOT EMULATION

CONTEXT CHOICE: <if meaningful>

MODEL / THINKING EFFORT: GPT-5.6 Sol — <low-med|high-xhigh|max>

USER/OPERATOR STEP

Invoke: /speckit.<phase>

Feature:
<explicit feature>

Phase brief:
<delta this already-invoked phase must accomplish>
```

Rules:
- phase brief does not say “run/invoke `/speckit.*`”;
- name the intended feature explicitly;
- do not trust a mutable current-feature pointer when multiple features exist;
- phase output may be tracked without becoming delegated patch work;
- review a material phase before recommending the next one;
- generic upstream Full SDD Cycle is not the BeautyQ execution path.

Plain work before or between real phases is allowed and often useful: source mapping, focused reconciliation, artifact dofixes, feature-selection checks, evidence capture, bounded review. It must remain plain delegation/emulation and must not pretend the real phase occurred.

## 2.3 Plain delegated agent task

Use delegated agents for bounded source-defined work after decision-critical architecture is sufficiently resolved.

A delegated task may produce:
- tracked patch;
- durable research/evidence/reconciliation artifact;
- bounded validation/review artifact;
- no-change/no-finding result;
- preparation that makes a later Spec Kit phase cheaper or more accurate.

It does **not** need a non-empty code patch to justify delegation.

Use one of:
- `PLAIN DELEGATED AGENT TASK — NO SPEC KIT INVOCATION / NO EMULATION`
- `SPEC KIT EMULATION — PLAIN PROMPT, NOT A REAL INVOCATION`

Inside the actual prompt, `Task:` is allowed.

Delegation never grants coordinator acceptance authority or creates a user-owned Spec Kit boundary.

Do not send vague inventory/review busywork. A delegated evidence task needs a bounded source question, explicit artifact/result contract, and concrete current value.

## 2.4 Feature-state ownership

With Spec Kit, durable feature state belongs primarily to:
- `spec.md` — behavior, acceptance, non-goals, decision object;
- `research.md` — source-confirmed research/reconciliation;
- `plan.md` — technical path, ownership, boundaries, validation/evidence strategy;
- `tasks.md` — executable decomposition and dependency order.

Prompts/briefs add delta; they do not become shadow owners for accepted feature facts.

---

# 3. Review closeout and validation

## 3.1 Closeout order

Section 0 controls response order.

After review:
1. emit the immediate actionable artifact first;
2. then state result and remaining uncertainty;
3. add zero to two genuine downstream options only when useful.

Mandatory review/verification is plumbing, not an option.

A user reply containing an option number selects work; it is not evidence.

## 3.2 Validation

Delegated workers validate only within the bounded contract of their patch/research/evidence task and never run an unscoped full repository suite.

A user-invoked phase may run checks required by its accepted phase contract.

The coordinator runs a broader exact command only when the user explicitly requests it; otherwise use focused checks and, when warranted, ask the user to run the exact broader command before committing.

Coordinator-emitted sbt validation uses one chained batch invocation:
`sbt --batch --no-global ...`
Never emit interactive/plain multi-launch sbt when one batch invocation suffices.

Reports state exactly:
- what ran;
- against which evaluated state;
- what remains unknown.

Do not encode confidence as synthetic status labels.

Minimum validation by risk:
- pure model/parser/policy/codec → owning spec;
- public API/wire/route → owning spec + route/wire contract;
- build edge/module boundary → owning compile + boundary/firewall spec;
- DI/plugin/activation → focused graph/wiring proof;
- lifecycle/external resource → scripted/in-process contract + focused communication test when available.

Use the narrowest set covering every changed risk layer.

## 3.3 Human Git / commit messages

No agent commits, stages, unstages, or stashes.

For accepted non-trivial patch work, the **first artifact in the response** is an expanded commit message containing:
- subject;
- what changed;
- why;
- preserved boundaries/non-goals;
- user-visible effect where relevant;
- verification actually performed;
- material limitations.

Do not prepare one for `CONTINUE_SAME_PATCH`, `CORE_DIRECTION_OK_BUT_NOT_ACCEPTED`, `REJECT`, or `BLOCKED_*`.

Do not imply focused checks cover the whole repository.

---

# 4. Spec Kit handoff rules

## 4.1 Real invocation vs emulation

A real slash command is a user-owned workflow boundary.

A plain prompt may intentionally emulate useful phase-like reasoning, but must be labeled:

`SPEC KIT EMULATION — PLAIN PROMPT, NOT A REAL INVOCATION`

Emulation does not create phase state or authorization.

Never tell a delegated agent to invoke `/speckit.*`, hide a slash command in `Task:`, or treat a plain prompt containing `/speckit.plan` as a real invocation.

## 4.2 No automatic phase chaining

Material phase sequence:
1. phase finishes;
2. coordinator reviews;
3. coordinator accepts/continues/rejects/blocks;
4. only then may the next user invocation be recommended.

Command-generated “next step” prose or workflow approve/reject UI is not coordinator acceptance.

## 4.3 Phase-brief economy

A phase brief carries only what the phase needs beyond accepted owners.

Prefer references to accepted artifacts over copying them.

Typical delta:
- `specify` — decision object, hard scope, source navigation, non-goals;
- `plan` — unresolved research questions, source/evidence owners, human boundaries;
- `tasks` — special dependency/gate semantics;
- `implement` — execution-only constraints not already in tasks/guardrails.

## 4.4 Explicit feature selection

For feature-scoped phases:
- identify the intended feature explicitly;
- do not infer solely from ignored/mutable pointers;
- if command machinery cannot target it unambiguously, stop and repair the control plane.

---

# 5. Delegated prompt rules

## 5.1 Before writing the prompt

Apply the source-truth gate first.

Do not ask the worker to reconstruct missing architecture with broad search.

Bounded discovery is allowed inside the approved task frontier to close a patch, research artifact, reconciliation result, or evidence question.

Do not paste all of `AGENTS.md`; inline only task-specific hazards.

## 5.2 Human-facing wrapper

Outside the actual prompt:

```text
PLAIN DELEGATED AGENT TASK — NO SPEC KIT INVOCATION / NO EMULATION
# or:
SPEC KIT EMULATION — PLAIN PROMPT, NOT A REAL INVOCATION

CONTEXT CHOICE: SAME <context/session> | NEW / EMPTY context

MODEL / THINKING EFFORT: GPT-5.6 Sol — <low-med|high-xhigh|max>
```

Then the actual prompt.

## 5.3 Prompt contents

Include only what materially affects execution:

```text
Task:
- exact bounded change/research/reconciliation/validation object

Available local sources:
- exact owners/ranges/symbols and why they matter

Edit/result boundary:
- writable targets/artifacts
- retained owners
- forbidden changes

Current facts:
- decision-critical facts and known traps only

Goal:
- exact behavior/tests/docs/research outcome

Validation:
- focused commands/checks
- post-fix rerun rule
- no unscoped full repository suite

Report:
- non-diff-visible runtime/generated evidence
- focused results
- diagnostic-driven deviations
- remaining uncertainty

Scope expansion:
- exact missing files/symbols/diagnostics
- why the task cannot close without them
- smallest next action
```

Reports are delta-only. Do not demand changed-file lists or restate obvious edits.

Prompt size by capability:
- cheap/mechanical → inline exact paths/signatures/hunks when that avoids search;
- mid → inline seams/invariants/known traps, allow bounded lookup;
- smart → inline outcome/acceptance/forbidden boundaries/contradictions, allow bounded reconciliation.

## 5.4 Continuation/context economy

Use `SAME` when continuation saves meaningful rediscovery; use `NEW / EMPTY` for independent review/adversarial checking.

For a real continuation, preserve the same uncommitted patch.

Before a risky handoff/compaction, capture a checkpoint with:
- raw `git status --short`;
- diff stat/name-status;
- completed edits;
- last successful command;
- complete current failure;
- next exact command.

A checkpoint under `target/` is ephemeral and must not be the only decision-critical copy.

Do not repeat successful discovery/checks without a source-confirmed reason.

Split only on observable triggers: new architecture decision, multiple unexpected owners, unrelated second root cause, repeated compile/search cycles, generated result controlling later edits, or lost verified state.

---

# 6. Manual/env-gated test DoD

When a patch changes env vars, markers, saved artifacts, manual local-service flow, or cancel/resource-gated behavior, acceptance requires:
- selection/cancel/fail-red behavior covered;
- saved/live/manual provenance documented where required;
- no required branch coverage deferred;
- no hidden fixture fallback unless explicitly requested;
- reachable-but-broken resource fails red;
- unavailable resource cancels only when no valid contract can be checked.

Otherwise use `CONTINUE_SAME_PATCH`.

---

# 7. Bundle rules

Bundles are coordinator evidence transport, not repository artifacts.

## 7.1 Who creates them

Coordinator designs the capture; user/operator executes it (or a primary environment explicitly acting for the user).

Delegated agents do not create coordinator-review ZIPs merely for handoff.

Feature-owned runtime/evidence artifacts are different and follow their owner contract.

## 7.2 Capture scope

Allowed:
- `git status`;
- bounded `rg` / `sed`;
- compact diffs;
- source/test/docs anchors;
- relevant untracked/ignored manifests;
- truncation;
- ZIP creation.

Forbidden:
- tests/sbt;
- Docker/resource startup or cleanup;
- network probes;
- package managers;
- deleting `target`;
- stage/unstage/stash/commit;
- broad unrelated history dumps.

After reading a bundle, the coordinator must use its facts; do not make the next agent rediscover the whole bundle.

If decision-critical anchors are still missing, request one focused supplemental bundle and stop.

## 7.3 Copy-paste shell contract

Bundle script goes **first in the response**.

It must:
- be zsh-safe;
- run multi-command local state inside a subshell `( ... )`;
- use no `exit`, `exec`, `kill $$`, `set -e`, `set -u`, or `set -o pipefail`;
- create workspace outside the repo, preferably via `mktemp -d "${TMPDIR:-/tmp}/beautyq-<topic>-XXXXXXXX"`;
- create archive outside the repo;
- bind the archive path itself to `OUT`;
- print diagnostics/path/size before clipboard handoff;
- use NUL-safe capture when untracked files matter;
- end the successful path with:

```bash
cpf "$OUT"
```

Do not run another command after successful `cpf "$OUT"`.

If `cpf` is unavailable, print `MISSING cpf` and the archive path; do not substitute another clipboard command or terminate the shell.

The ZIP never becomes evidence authority merely because it exists.

---

# 8. Documentation and artifact ownership

Before changing docs/artifacts, identify the canonical owner.

Keep:
- cross-feature governance → Constitution;
- repository execution behavior → `AGENTS.md`;
- coordinator workflow → this file;
- Spec Kit phase mechanics → command/templates/workflows;
- product/domain policy → product docs;
- feature behavior/status → `spec.md`;
- research/reconciliation → `research.md`;
- technical decisions/validation strategy → `plan.md`;
- executable decomposition → `tasks.md`;
- task-local delta → prompts;
- volatile outcomes/counts → reports or owner-defined evidence artifacts.

Do not duplicate long API lists, task state, metrics, acceptance ladders, bundle logic, or prompt rules across owners.

Duplicate only short safety-critical guardrails when repetition reduces mistakes.

A prompt is not durable feature memory.

If asked to edit this guide or another standalone text owner and its complete source is available, prefer producing the corrected replacement directly; per section 0, **attach it before analysis**.

---

# 9. Audit work

Senior interpretation, architecture/ownership conclusions, and source-sufficiency decisions remain coordinator-owned.

Mechanical read-only mapping may be done:
- directly by coordinator;
- through a focused user bundle;
- through a bounded plain delegated task;
- through clearly labeled Spec Kit emulation;
- inside a real user-invoked research/planning phase.

A mapper does not gain acceptance authority.

Useful independent audit waves:
1. docs vs source;
2. goals vs measured evidence;
3. architecture/ownership;
4. DI/lifecycle/roots/activation;
5. typed errors/effects/resource safety;
6. test taxonomy/real-resource coverage;
7. readiness/compatibility/fingerprints/state markers;
8. generated reports/golden data/ownership drift;
9. decoder/API/persisted-shape drift.

An audit finding never skips the source-truth gate and never authorizes implementation by itself.

---

# 10. Pre-send checklist

Use this as a short safety check, not a second copy of the guide.

Before every response:
- Is there an actionable artifact? If yes, is it physically first?
- If prompt/phase: is the execution-mode label explicit?
- Is `CONTEXT CHOICE` outside the prompt?
- Is `MODEL / THINKING EFFORT` outside the prompt/brief?
- If Spec Kit: did the user retain invocation ownership and is the feature explicit?
- If delegated: is the task bounded, source-defined, and free of hidden architecture decisions?
- If accepted patch: is the expanded commit message first and based only on actual verification?
- If bundle: is the script first, subshell-safe, outside-repo, and ending with `cpf "$OUT"`?
- Is Git index/history still human-owned?
- Did I avoid worktrees, broad rediscovery, duplicated durable state, and automatic phase chaining?
- If no actionable artifact applies, am I answering normally instead of inventing one?
