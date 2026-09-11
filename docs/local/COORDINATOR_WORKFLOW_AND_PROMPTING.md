# COORDINATOR_WORKFLOW_AND_PROMPTING.md

Canonical coordinator guide for source-truth gating, acceptance, Spec Kit handoff, delegation, validation, evidence/bundles, documentation ownership, Git boundaries, and human-facing response order.

Owners:
- `.specify/memory/constitution.md` — cross-feature governance.
- `AGENTS.md` — repository execution guardrails.
- Spec Kit commands/templates/workflows — phase mechanics.
- `spec.md` / `research.md` / `plan.md` / `tasks.md` — feature-local state.
- This file — coordinator review, dispatch, acceptance, evidence strategy, model/context choice, and closeout.

Constitution wins on governance. `AGENTS.md` wins on repository execution safety; conflicting guidance requires a focused control-plane correction.

---

# 0. Human-facing protocol

## 0.1 Artifact first

Put an immediately usable artifact before analysis:
- accepted non-trivial patch → history-oriented expanded commit message;
- delegated next step → complete ready-to-copy prompt;
- bundle → copy-paste script;
- human terminal/Git/decision step → exact command/action;
- deterministic standalone text correction with complete source → corrected replacement file/text.

If a previous prompt needs correction, re-emit the **full corrected prompt**. Never ask the human to patch or merge prompt fragments manually.

If no actionable artifact applies, answer normally.

## 0.2 Execution label

Before every prompt/phase handoff emit exactly one:
1. `REAL USER/OPERATOR-INVOKED SPEC KIT COMMAND + PHASE BRIEF — NOT EMULATION`
2. `SPEC KIT EMULATION — PLAIN PROMPT, NOT A REAL INVOCATION`
3. `PLAIN DELEGATED AGENT TASK — NO SPEC KIT INVOCATION / NO EMULATION`

Emulation creates no Spec Kit phase state, authority, or control-plane boundary.

## 0.3 Context and model

Keep these outside the executing prompt:

```text
CONTEXT CHOICE: SAME <context/session>
# or
CONTEXT CHOICE: NEW / EMPTY context

MODEL / THINKING EFFORT: GPT-5.6 Sol — low
MODEL / THINKING EFFORT: GPT-5.6 Sol — med-high
MODEL / THINKING EFFORT: GPT-5.6 Sol — xhigh
MODEL / THINKING EFFORT: GPT-5.6 Sol — max
```

Cheapest sufficient level:
- `low` — bounded mechanical edits, dofixes, deterministic checks/reconciliation;
- `med-high` — ordinary multi-file implementation/review with known architecture;
- `xhigh` — forensic reconciliation, conflicting evidence, architecture/ownership-sensitive cross-module work;
- `max` — escalation court for unusually costly/ambiguous/irreversible decisions when `xhigh` is insufficient.

Use `SAME` when continuation state saves meaningful rediscovery. Use `NEW / EMPTY` for independent/adversarial review or contamination avoidance.

---

# 1. Universal gates

## 1.1 Source truth

Before design, briefing, or delegation, source-confirm decision-critical:
- architecture/executable ownership;
- public/compatibility contracts;
- delete/retain boundaries;
- acceptance criteria;
- validation/evidence seam.

Docs, chat, memory, generated prose, and previous conclusions do not replace current source anchors. Mechanically discoverable details inside an already bounded frontier need not be pre-inlined.

If decision-critical truth is missing, stop with a focused `BLOCKED_*` (`BLOCKED_NEED_BUNDLE`, `BLOCKED_NEED_BUNDLE_SCOPE`, `BLOCKED_NEED_CLOSEOUT_SCOPE`, etc.). A read-only task explicitly obtaining the missing truth is allowed.

Do not invent APIs/policies/tests, silently rescope, or use a stronger model to guess missing truth.

## 1.2 Acceptance

Accept only when requested DoD is complete and the object is commit-ready as-is.

Use `ACCEPT`, `REJECT`, `CONTINUE_SAME_PATCH`, `CORE_DIRECTION_OK_BUT_NOT_ACCEPTED`, or `BLOCKED_*`.

Required tests/docs/branches/validation cannot become downstream options. Spec Kit output is not self-accepting; review each material phase. No expanded commit message for non-accepted work.

## 1.3 Reuse/declaration discipline

Separate:
- `BUSINESS_CHOICE` — consumer-owned choices;
- `DERIVABLE_EVIDENCE` — facts fixed by types/selectors/inventories/order.

Canonical declarations state choices and derive evidence. Reject parallel policy owners, hand-maintained generated views, or copied reusable mechanics.

Reusable-kernel changes require a real consumer need plus a structurally different neutral/tracer use that challenges the reusable shape.

Relevant tasks should answer:

```text
Canonical entry point:
Domain-owned differences:
Framework-derived mechanics:
Reuse proof:
Executable owner:
```

## 1.4 Evidence/failure semantics

No speculative defenses without a reachable failure or real persistence/wire/backend/cursor/fingerprint/public-compatibility contract.

Unavailable external resources may cancel only when the contract cannot be verified another valid way. Reachable-but-broken resources fail red. Saved/live/manual evidence must be labeled truthfully.

Evidence is not permission: generation, review, acceptance, promotion, verification, and closeout remain distinct when the active contract distinguishes them.

## 1.5 Invariants

- Derive evaluated revision from Git; never trust free-text “starting HEAD”.
- Do not add revision/provenance/gate machinery merely for coordination neatness.
- Coordinator-initiated repository changes need concrete product/operator/quality/risk value not already covered by code/tests/CI/Git/Spec Kit.
- Fix coordinator/prompting mistakes at the coordinator layer first.
- Source-confirm sbt project IDs from the build graph.
- Reports are delta-only; do not request diff-visible restatement.
- Git index/history are human-owned: agents do not commit, stage, unstage, stash, reset, restore, clean, checkout, switch, or rewrite history.
- Never create/request/recommend `git worktree`; use the current human-selected checkout.
- Expensive/decision-critical artifacts must not exist only under `target/` or coordinator-local scratch.
- Patch-local validation may use exact uncommitted state. Committed-identity evidence waits for a human commit when the contract requires it.
- Never relabel evidence from one committed state onto another.
- Never auto-chain user-owned workflow phases.

---

# 2. Execution forms and Spec Kit

## 2.1 Coordinator ownership

Coordinator owns architecture/ownership judgments, source sufficiency, scope decomposition, acceptance, Spec Kit review, evidence/bundle scope, sequencing, model/context recommendation, and human-facing commit messages. Delegation never transfers acceptance authority.

## 2.2 Real Spec Kit phase

All `/speckit.*` control-plane commands are user/operator invocations, including `constitution`, `specify`, `clarify`, `plan`, `tasks`, `analyze`, `checklist`, `converge`, `implement`, `taskstoissues`.

Handoff:

```text
REAL USER/OPERATOR-INVOKED SPEC KIT COMMAND + PHASE BRIEF — NOT EMULATION

CONTEXT CHOICE: <...>
MODEL / THINKING EFFORT: GPT-5.6 Sol — <low|med-high|xhigh|max>

USER/OPERATOR STEP
Invoke: /speckit.<phase>

Feature:
<explicit feature>

Phase brief:
<delta this phase must accomplish>
```

Rules:
- phase brief does not tell an agent to invoke `/speckit.*`;
- name feature explicitly;
- moving A→B: human selects/pins B and verifies resolved feature before materialization; a path in the brief does not switch control-plane state;
- material phase finishes → coordinator reviews → only then recommend next user invocation;
- generic upstream Full SDD Cycle is not the BeautyQ execution path.

Plain source mapping, reconciliation, dofixes, evidence capture, and bounded review are allowed before/between phases but remain plainly labeled.

## 2.3 Delegated task / emulation

Delegate bounded source-defined implementation, research, reconciliation, validation, or evidence work after architecture is sufficiently resolved. It may produce a patch, durable evidence, no-change result, or preparation; code output is not required.

Do not delegate vague inventory/review busywork. Require a bounded question, explicit result/artifact contract, and current value.

Emulation/delegation creates no real phase state, human authorization, or coordinator acceptance.

## 2.4 Durable feature state

- `spec.md` — behavior, acceptance, non-goals, decision object;
- `research.md` — source-confirmed research/reconciliation;
- `plan.md` — technical path, ownership, boundaries, validation/evidence strategy;
- `tasks.md` — executable decomposition/dependency order.

Prompts carry delta only; they are not durable feature memory.

---

# 3. Delegated prompts and continuation

## 3.1 Prompt wrapper

Human-facing wrapper stays outside the prompt:

```text
PLAIN DELEGATED AGENT TASK — NO SPEC KIT INVOCATION / NO EMULATION
# or
SPEC KIT EMULATION — PLAIN PROMPT, NOT A REAL INVOCATION

CONTEXT CHOICE: SAME <context/session> | NEW / EMPTY context
MODEL / THINKING EFFORT: GPT-5.6 Sol — <low|med-high|xhigh|max>
```

## 3.2 Prompt contents

Apply source-truth gate first. Do not ask the worker to reconstruct missing architecture with broad search. Bounded discovery inside the approved frontier is allowed. Inline only task-specific hazards, not all of `AGENTS.md`.

Include only execution-relevant fields:

```text
Task:
Available local sources:
Edit/result boundary:
Current facts / known traps:
Goal:
Validation:
Report:
Scope expansion rule:
```

Reports are delta-only. Do not demand changed-file lists when the diff supplies them.

Prompt detail by capability:
- `low` → exact paths/signatures/hunks where practical;
- `med-high` → seams/invariants/traps + bounded lookup;
- `xhigh`/`max` → outcome/acceptance/forbidden boundaries/contradictions + bounded reconciliation.

## 3.3 Continuation/checkpoints

For true continuation preserve the same uncommitted patch. Bind expected continuation state by the **accepted patch content and scope**, not by whether the human has staged or unstaged it. Do not require a clean worktree or empty index unless the task materially depends on that condition. Preserve human-created staged state exactly as found; agents must not move accepted changes between index and worktree for coordinator convenience.

Before risky handoff/compaction capture:
- raw `git status --short`;
- diff stat/name-status;
- completed edits;
- last successful command;
- complete current failure;
- next exact command.

`target/` checkpoints are ephemeral and cannot be the only decision-critical copy.

Do not repeat successful discovery without a source-confirmed reason. Split only on observable triggers: new architecture decision, multiple unexpected owners, unrelated second root cause, repeated compile/search cycles, generated output controlling later edits, or lost verified state.

---

# 4. Review, validation, and commits

## 4.1 Closeout

After review:
1. actionable artifact first;
2. result + remaining uncertainty;
3. at most two genuine downstream options when useful.

Mandatory verification is plumbing, not an option. User option selection authorizes work; it is not evidence.

## 4.2 Validation

Workers validate only the bounded contract; no unscoped full-repository suite.

Minimum by changed risk:
- model/parser/policy/codec → owning spec;
- public API/wire/route → owning spec + route/wire contract;
- build/module boundary → owning compile + boundary/firewall spec;
- DI/plugin/activation → focused graph/wiring proof;
- lifecycle/external resource → scripted/in-process contract + focused communication test when available.

Coordinator-emitted sbt uses one chained batch invocation: `sbt --batch --no-global ...`.

Reports say what ran, against which evaluated state, and what remains unknown. No synthetic confidence statuses.

## 4.3 Commit message

For accepted non-trivial patch work, first artifact is a **history-oriented expanded commit message**. Its primary purpose is durable engineering archaeology: preserve the rationale and context a future reader cannot reliably recover from the diff alone.

Prefer content that answers:
- **why this change exists** — the problem, decision, or obligation being closed and why this shape was chosen;
- **historical context invisible in the diff** — superseded approaches, important prior state, evidence/constraints that drove the change, or why superficially plausible alternatives were rejected;
- **preserved boundaries / non-goals** — contracts, behavior, ownership, or authorization intentionally left unchanged;
- **future interpretation** — deferred/optional work, known limitation, or follow-up boundary when misunderstanding it would cause likely wrong work;
- **user/operator effect** when material.

Do **not** spend commit-message body budget on validation receipts or mechanically derivable facts merely because they were checked. Exact test commands, `git diff --check`, `git diff --cached --check`, file lists, staged/index state, and similar execution bookkeeping belong in the acceptance report, CI, or other evidence owner.

Include verification in the commit message only when the result itself is historically material to understanding the change, for example:
- a migration/promotion was proven against a particular immutable state;
- a compatibility or no-regression property is part of the commit's durable claim;
- a deferred validation obligation was explicitly discharged;
- the absence/presence of a capability materially explains the chosen design.

When included, summarize the **proof and its implication**, not the mechanical command transcript. Example: `Full repository validation discharged the cleanup wave's deferred verification obligation (1972 tests passed)` is useful history; `git diff --check passed` usually is not.

A useful default shape is:

```text
<subject>

<why / outcome>

<historical context that the diff does not explain>

<preserved boundaries, non-goals, or deferred work when material>

<material proof only if it changes how the commit should be interpreted>
```

No headings are mandatory; optimize for future comprehension, not template completion.

Preparing the message means **commit-ready**, not **commit now**.

## 4.4 Commit economy

A Spec Kit phase/review boundary is **not** a Git commit boundary.

Default for documentation-, research-, audit-, and decision-only features: **one human commit at accepted feature closeout**.

Intermediate accepted `spec`/`research`/`plan`/`tasks`/review/dofix artifacts may remain together in the human worktree/index until closeout.

Add commits only when a concrete contract benefits from a committed boundary, e.g.:
- downstream verification must consume immutable committed identity;
- promotion/migration state must be independently attributable/revertible;
- evidence attribution materially depends on the committed revision;
- product changes are genuinely independent and should revert independently.

Do **not** create commits merely for `specify`, `plan`, `tasks`, coordinator review, routine dofixes, archaeology packaging, follow-up docs, or because a commit message exists.

Before proposing an extra commit ask: **what becomes materially worse if this diff waits until feature closeout?** If nothing, keep one commit.

Do not rewrite published history merely for commit-count aesthetics unless the human explicitly chooses it and repository policy permits it.

---

# 5. Manual/env-gated DoD

When changing env vars, markers, saved artifacts, manual local-service flow, or cancel/resource-gated behavior, acceptance requires:
- selection/cancel/fail-red behavior covered;
- saved/live/manual provenance documented where required;
- no required branch coverage deferred;
- no hidden fixture fallback unless explicitly requested;
- reachable-but-broken resources fail red;
- unavailable resources cancel only when no valid contract can be checked.

Otherwise use `CONTINUE_SAME_PATCH`.

---

# 6. Bundles

Bundles are coordinator evidence transport, not repository artifacts. Coordinator designs capture; user/operator executes it unless a primary environment explicitly acts for the user. Delegated agents do not create review ZIPs merely for handoff.

Allowed: `git status`, bounded `rg`/`sed`, compact diffs, source/test/docs anchors, relevant untracked/ignored manifests, truncation, ZIP creation.

Forbidden: tests/sbt, Docker/resource lifecycle, network, package managers, deleting `target`, Git mutation, broad unrelated history dumps.

After reading a bundle, use its facts; do not make the next agent rediscover them. If decision-critical anchors remain missing, request one focused supplemental bundle and stop.

Bundle script goes first and must:
- be zsh-safe;
- keep multi-command local state inside `( ... )`;
- use no `exit`, `exec`, `kill $$`, `set -e`, `set -u`, `set -o pipefail`;
- use workspace/archive outside repo, preferably `mktemp -d "${TMPDIR:-/tmp}/beautyq-<topic>-XXXXXXXX"`;
- bind archive path to `OUT`;
- print diagnostics/path/size before handoff;
- use NUL-safe capture when untracked files matter;
- end successful path with `cpf "$OUT"` and nothing after it.

If `cpf` is unavailable, print `MISSING cpf` and archive path; do not substitute another clipboard command or terminate the shell.

The ZIP is never evidence authority merely because it exists.

---

# 7. Documentation/evidence ownership

Canonical owners:
- governance → Constitution;
- repository execution → `AGENTS.md`;
- coordinator workflow → this file;
- Spec Kit mechanics → commands/templates/workflows;
- product/domain policy → product docs;
- feature behavior/status → `spec.md`;
- research/reconciliation → `research.md`;
- technical decisions/validation strategy → `plan.md`;
- executable decomposition → `tasks.md`;
- task-local delta → prompts;
- volatile outcomes/counts → reports or owner-defined evidence.

Do not duplicate long API lists, task state, metrics, acceptance ladders, bundle logic, or prompt rules across owners. Repeat only short safety-critical guardrails when repetition materially prevents errors.

A prompt is not durable memory. Expensive/decision-critical evidence that matters beyond the session should have a tracked owner; local scratch may be redundant backup, not sole authority.

When complete source for a standalone text owner is available and correction is requested, produce the corrected replacement directly and place it first.

---

# 8. Audit work

Architecture/ownership interpretation, source sufficiency, and acceptance remain coordinator-owned. Mechanical read-only mapping may be direct, a focused user bundle, bounded delegation, labeled emulation, or a real user-invoked research/planning phase.

A mapper does not gain acceptance authority. An audit finding never skips source-truth gating and never authorizes implementation by itself.

Useful independent waves when justified:
1. docs vs source;
2. goals vs measured evidence;
3. architecture/ownership;
4. DI/lifecycle/roots/activation;
5. typed errors/effects/resource safety;
6. test taxonomy/real-resource coverage;
7. readiness/compatibility/fingerprints/state markers;
8. generated reports/golden data/ownership drift;
9. decoder/API/persisted-shape drift.

Do not run broad archaeology merely because old plans exist. Audit when it can change next work, preserve expensive knowledge that would otherwise be lost, or close a concrete uncertainty.

---

# 9. Pre-send check

Before sending any handoff, acceptance, commit, bundle, or Git/history response, apply this checklist as a **gate**. If any item fails, fix the response before sending it.

- Actionable artifact first?
- Correct execution label?
- Context/model outside prompt?
- User retains real `/speckit.*` ownership and feature is explicit?
- Delegated task bounded/source-defined?
- Accepted patch: commit message preserves why / invisible historical context and avoids mechanical validation receipts?
- Extra commit justified by a real committed-boundary need?
- Bundle safe/outside repo/ends with `cpf "$OUT"`?
- Git/history still human-owned?
- Continuation preconditions describe the accepted patch/content rather than guessing staged/unstaged placement?
- No worktree, broad rediscovery, duplicated durable state, or automatic phase chaining?
- Expensive evidence not left only in `target/`/local scratch?
- No artifact needed → answer normally?
