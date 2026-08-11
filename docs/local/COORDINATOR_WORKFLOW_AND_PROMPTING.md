# COORDINATOR_WORKFLOW_AND_PROMPTING.md

Capability tags: `[ALL]` every model, `[W]` CHEAP MODEL OK, `[M]` MID MODEL RECOMMENDED, `[S]` SMART MODEL REQUIRED, `[NO]` NO MODEL — OPERATOR STEP. Task tags are used only where behavior differs, such as `[CONTINUATION]` and `[DOCS]`. Untagged review and safety rules apply to all.

Purpose: canonical coordinator guide for source-truth gating, patch acceptance, delegated prompts, review closeout, verification evidence, bundles, docs ownership, audits, and model recommendations.

`AGENTS.md` is repo/delegated-agent guardrails. This file controls coordinator workflow and closeout. If the two conflict on repo safety, tests, or bounded edit behavior, stop and request a docs clarification patch.

---

# 1. Universal gates

## 1.1 Source-truth gate

Before patch design or delegation, source-confirm the decision-critical facts: architecture, executable ownership, public and compatibility contracts, delete/retain boundaries, acceptance criteria, and the validation seam.

Imports, direct callers, local signatures, test renames, and compiler-driven repairs inside an already named module frontier are mechanically discoverable. They need not all be pre-inlined, although `[W]` prompts should include them when doing so prevents broad reading.

Docs, handoff, memory, and previous conclusions do not replace current source anchors.

If required decision-critical source truth is missing, stop with one of:

- `BLOCKED_NEED_BUNDLE`
- `BLOCKED_NEED_BUNDLE_SCOPE`
- `BLOCKED_NEED_CLOSEOUT_SCOPE`

Do not provide a patch proposal, exact edit recipe, delegated prompt, or test recipe when required anchors are missing.

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
- `BLOCKED_*` — source truth, evidence, or closeout scope is missing.

Do not write an extended commit message for a patch that is not accepted.

Do not mark a patch commit-ready if known required work remains.

Downstream options are only for new work after the current patch is complete.

A happy-path implementation is not accepted when the original task required branch coverage, failure behavior, cancel behavior, compatibility behavior, or matrix behavior.

## 1.3 Declarative authoring gate

For a declarative business DSL or reusable framework patch, classify requested behavior as:

- `BUSINESS_CHOICE`: facts that legitimately vary by consumer;
- `DERIVABLE_EVIDENCE`: facts already fixed by types, selectors, declared inventories, or order.

The canonical declaration should state business choices and avoid hand-writing derivable evidence. Continue or reject a patch when it repeats selector/type/name/path/semantic facts, maintains parallel ordered lists, manually folds generated structures, or makes a generated view a second policy owner.

When the reusable kernel changes, require both a real consumer need and a structurally different neutral/tracer usage that challenges the reusable shape. A fixture calibrates representation; it must not invent production vocabulary.

Every such prompt must answer:

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

Do not add defenses for overridden enum `toString`, reflection, malicious same-package callers,
impossible nulls/states, or unspecified future changes.

Apply the stable-value rule in `AGENTS.md`: preserve task-defined typed IDs/codes and active order used by contract views; use `toString` only for incidental diagnostics. Reject speculative labels or wrappers, not named contract values. Return `CONTINUE_SAME_PATCH` or `REJECT`.

## 1.4 No fake green

Unavailable external resources may cancel/resource-gate only when the test cannot verify its stated contract another valid way.

Reachable-but-broken resources fail red. Do not hide them behind saved data, fixtures, fallback branches, weaker checks, or saved-only downgrade.

If a selected path uses saved artifacts or fixtures, provenance must say so. Do not call it live coverage.

## 1.5 Protected source-truth invariant

Section 1.1 is protected and has higher priority than the requested output shape. Do not weaken it, move it into `AGENTS.md`, or produce a delegated prompt while decision-critical source anchors are missing.

## 1.6 Coordinator invariants

These invariants apply to every delegated patch, prompt and evidence cycle. They are general workflow
rules; product- or milestone-specific history does not belong here.

- **Never include `Starting revision` / `Starting HEAD` in delegated prompts.** A delegated agent
  must derive the exact evaluated source identity from the coordinator's already-accepted tracked
  boundary, never from a free-text prompt header.
- **Source-confirm sbt project IDs.** Project IDs are taken from `build.sbt`/the build graph, not from
  directory names. A directory rename or repackage does not move a project ID.
- **Delegated reports are delta-only.** A delegated report must not request or restate facts that the
  reviewer can recover directly from the patch or diff. Mention files only for scope deviations or
  generated/untracked evidence, and explain rationale only when it is not visible from the code or
  diagnostic.
- **Finish an accepted tracked boundary with the proper commit message before downstream evidence
  generation.** Evidence captured against an uncommitted or amended boundary cannot be attributed to
  the source it claims to evaluate.
- **Never amend or rewrite an evidence-bearing revision.** Once evidence is bound to a source
  revision, that revision is immutable. Attribute the old evidence to its original identity; do not
  re-attribute it to a new commit.
---

# 2. Role split and prompt forms

Coordinator owns architecture, source-truth audit, patch strategy, decomposition, acceptance decisions, commit messages, evidence capture, and downstream planning.

Delegated agents own bounded tracked edits and the focused validation required to prove those edits.

A delegated task must have an expected non-empty tracked patch. Validation-only, evidence-only, inventory-only, review-only, and read-only tasks are coordinator/user work, not delegated-agent tasks.

The coordinator must resolve architecture before delegation; never leave an agent with `NEED_ARCHITECTURE_DECISION`.

Audit conclusions, architecture, source-sufficiency decisions, and mechanical source/evidence capture are coordinator-owned.

Allowed reusable prompt form:

- `Task:` for delegated edit work.

Forbidden reusable prompt labels:

- `Coordinator analysis contract:`
- `External high-effort audit:`
- `source-confirm prompt`
- `read-only brief`
- `audit brief`

Do not paste all of `AGENTS.md` into prompts. Inline only task-specific excerpts.

---

# 3. Review closeout

## 3.1 Accepted closeout shape

After an accepted review:

1. State the current result and remaining uncertainty.
2. Provide an extended commit message for non-trivial work.
3. If the user already selected a source-confirmed follow-up, provide one delegated edit `Task:` prompt.
4. Put the model recommendation outside the prompt.
5. Provide zero to two genuine downstream options only when useful; recommend one only when there is a real choice.
6. Provide a bundle script only when further source capture or remote handoff is actually needed.

Mandatory review and verification are plumbing, not downstream options. A user reply containing only an option number selects work; it is not evidence. Do not manufacture alternatives after the task is complete.
## 3.2 Verification

Delegated workers validate only as part of a patch-producing task and never run an unscoped full repository suite.

The primary/coordinator may run an exact full command only when the user explicitly requests it. Otherwise use focused checks and ask the user in plain language to run the exact broader command before committing when that confidence is warranted.

Any coordinator-provided sbt validation uses one chained batch invocation (`sbt --batch --no-global ...`) for the requested validation phase. Never emit plain/interactive `sbt ...` or multiple standalone sbt launches when one chained invocation can cover the same checks.

Every report states exactly what ran and what remains unknown. Do not encode confidence as synthetic status labels.
## 3.2.1 Risk-to-validation matrix

| Change shape | Minimum focused validation |
|---|---|
| Pure model, parser, policy, or codec helper | Owning spec |
| Public API, wire codec, or route contract | Owning spec plus route/wire contract |
| Build edge or module boundary | Owning production compile plus boundary/firewall spec |
| DI, plugin, or activation change | Focused graph/wiring proof; broader full command only by explicit user request to primary/coordinator, otherwise ask the user to run it |
| Lifecycle or external resource behavior | Scripted/in-process contract plus focused communication test when the harness is available |

Use the narrowest row that covers every changed risk layer. Split unrelated risk layers rather than validating them with one oversized command.

## 3.3 Commit messages

Delegated agents do not commit.

For accepted non-trivial patches, prepare an extended commit message:

- subject;
- what changed;
- why;
- preserved boundaries/non-goals;
- user-visible effects where relevant;
- verification trailer.

Avoid generic verification boilerplate unless it explains a known failure, fix, or remaining risk.

Do not prepare an extended commit message for `CONTINUE_SAME_PATCH`, `CORE_DIRECTION_OK_BUT_NOT_ACCEPTED`, `REJECT`, or `BLOCKED_*`.

Do not imply that focused checks cover the full repository.

---

# 4. Delegated prompt rules

## 4.1 Source-truth gate before prompt

Apply section 1.1 before writing a prompt: close decision-critical seams and request focused bundles only for missing decision evidence. Do not duplicate mechanically discoverable imports, callers, or local signatures for `[M/S]` unless they are known traps.

The prompt must not ask the agent to reconstruct missing architecture or deletion inventory with broad repository search. Bounded discovery is allowed only inside the approved edit frontier when needed to complete the tracked patch.

Do not say "use attached bundle". Inline relevant facts.

## 4.2 Required delegated prompt shape

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
- only the facts needed for this model tier

Goal:
- exact behavior/tests/docs outcome

Validation:
- focused commands
- exact post-fix rerun rule
- do not run the full repository suite

Report:
- runtime or generated evidence that is not recoverable from the patch
- focused command results
- diagnostic-driven deviations from the requested plan
- remaining uncertainty

Reports are delta-only. Do not list changed files or restate diff-visible edits when the reviewer can recover them from the patch. Mention files only for scope deviations or generated/untracked evidence, and explain rationale only when it is not visible from the code or diagnostic.

Scope expansion:
- exact files, symbols, diagnostics, and smallest next action
```

Model-tier recipe:

- `[W]` Inline exact paths and material signatures, imports, constructors, fixtures, manifests, diagnostics, and replacement hunks. This is appropriate when it prevents broad repository reading.
- `[M]` Inline decision-critical seams, invariants, manifests, and known traps. Allow bounded lookup of stable local definitions and named dependency frontiers.
- `[S]` Inline outcome, acceptance criteria, forbidden boundaries, known evidence, and source contradictions. Architecture and ownership decisions remain coordinator-owned. Allow bounded source discovery and reconciliation within those decisions.
- `[W][CONTINUATION]` When exact hunks and current diagnostics are supplied, tell the model not to pre-read the whole source list.
- `[M/S]` For new code, owner-map changes, or `[DOCS]` reconciliation, allow reading the canonical owner and nearby tests before editing.

Do not paste all of `AGENTS.md` into prompts. Inline only task-specific guardrails the chosen model is likely to violate. Historical context stays in coordinator review unless it changes the edit.
## 4.2.1 Continuation and context control

- `[ALL][CONTINUATION]` Preserve the same uncommitted patch; a fresh session is a handoff, not a separate logical task.
- `[ALL][CONTINUATION]` Before handoff or compaction, save a checkpoint under `target/agent-checkpoint/` containing raw `git status --short`, `git diff --stat`, `git diff --name-status`, completed edits, the last successful command, the complete current failure, and the next exact command.
- `[ALL][CONTINUATION]` Checkpoints under target are ephemeral and may be deleted by build cleanup. They must not be the only copy of decision-critical continuation state.
- `[ALL][CONTINUATION]` The next prompt inlines the checkpoint summary and does not repeat discovery or already successful checks without a source-confirmed reason.
- `[W]` Prefer exact snippets and replacement hunks. `[M]` Allow bounded mechanical closure inside named frontiers. `[S]` Prefer bounded source inspection over duplicating large stable source.
- `[ALL]` Large documents, diffs, successful logs, status, and inventories stay in repo-local artifacts; include only summaries and unexpected excerpts in the conversation.
- `[ALL]` Follow `AGENTS.md` for diagnostics, sbt execution, deletion safety, and failure triage instead of repeating those rules here.
- `[W][DOCS]` Multiple large documents may use a fresh docs continuation in the same uncommitted patch with exact replacement anchors; do not defer required live documentation to a later logical task.
- `[ALL]` Split on observable triggers: a new architecture or ownership decision, a diagnostic opening several unplanned retained owners, a second unrelated root cause, repeated compile/search cycles, a generated result that determines later edit content, or lost verified state after compaction.

## 4.2.2 Prompt and execution economy

- Distinguish `Read first` from the edit and validation manifest. Pre-read only decision-critical owners, exact templates, and current diagnostics; an edit target or validation owner does not need to be read before it becomes relevant. Within a phase, batch coherent edits per owner and re-read only bounded changed ranges or diagnostics.
- Inline each decision-critical fact once, preferably as a compact manifest or table. Omit coordinator self-talk, inherited repository rules, and facts that neither change an edit decision nor prevent broad discovery.
- For large multi-module patches, run compile and pure owning specs before managed or external-resource suites. Rerun only the failed layer and downstream layers affected by its fix; do not repeat an already-green layer without a source-confirmed reason.

## 4.3 Runtime safety inheritance

Do not duplicate the Scala, test-double, unsafe-extraction, DI, lifecycle, HTTP, or sbt rules from `AGENTS.md`. Inline only the task-specific hazard the chosen model is likely to violate. Coordinator-emitted sbt commands must satisfy section 3.2.

---

# 5. Manual/env-gated/cancel-by-default test DoD

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

# 6. Bundle rules

Bundles are coordinator evidence capture only. After reading a bundle, the coordinator must inline important facts into the delegated prompt.

If anchors are still missing after review for the selected task, request a focused supplemental bundle and stop.

## 6.0 Delegated-agent bundle restriction

Delegated agents do not create review bundles, zip archives, grep-report archives, or evidence-only capture tasks.

Source-truth and closeout bundles are coordinator-owned scripts executed by the user or primary/coordinator when anchors are missing or handoff evidence is required. Generated artifacts that are part of a patch's actual runtime/product DoD are not review bundles and may be produced only inside a patch-producing task.

Full repository tests remain governed by section 3.2.

## 6.1 Bundle scripts must not mutate repository state

Allowed:

- `git status`;
- bounded `rg`/`sed`;
- compact diffs;
- relevant source/test/docs anchors;
- untracked manifest/archive;
- truncation;
- zip handoff.

Forbidden:

- `sbt` or tests;
- Docker cleanup/startup;
- network/resource probes;
- package managers;
- deleting `target`;
- heavy or mutating commands.

Do not include full `target`, generated build output, screenshots, stale numbered files, or broad `HEAD~N --patch` unless explicitly requested.

## 6.2 Required bundle shape

When explicitly requested, a bundle must:

- use a repo-local `.review-bundles/<topic>-<timestamp>-$RANDOM` workspace;
- `.review-bundles` is ignored transport storage, not durable source truth. `git clean -dfx` may delete it. Upload or externalize required evidence immediately and never use it as the only continuation copy.
- include task-relevant status, bounded anchors, diffs, and manifests;
- use NUL-safe untracked-file capture when untracked files matter;
- truncate large text output;
- include the bundle ID in artifact names;
- print artifact and zip sizes;
- print the final workspace and archive paths;
- copy the archive with:

```bash
cpf "$ZIP"
echo "$ZIP"
```

If `cpf` is unavailable, still create and print the archive path and report the deviation. Do not substitute a different clipboard command.

Use a repository-owned bundle script when available; otherwise provide a task-specific compact script. Do not embed a universal multi-page skeleton in this guide.
## 6.3 No shell/session hazards

User-facing copy-paste terminal commands must not close, replace, or mutate the user's shell behavior.

Forbidden:

- `exit`;
- `exec`;
- `kill $$`;
- terminating traps;
- `set -e`;
- `set -u`;
- `set -o pipefail`.

Use local flags and conditional branches. On failure, print `BLOCKED` or `MISSING`, skip dependent steps, and still print diagnostics.

## 6.4 Repository-local evidence

Temporary files, bundles, and generated evidence follow the repository-local
path rules in `AGENTS.md`.

Multi-iteration evidence workspaces default to `./.evidence-runs/<run-id>/`.

# 7. Documentation ownership

Before changing docs, identify the canonical owner of the fact.

Keep:

- stable repository behavior in `AGENTS.md`;
- coordinator workflow in this file;
- product/domain policy in product documentation;
- task-local facts in delegated prompts;
- volatile counts and verification outcomes in reports.

Do not duplicate long API lists, task state, metrics, bundle logic, or prompt rules across docs. Duplicate only short safety-critical guardrails that must be visible at multiple entry points.

When asked to edit this guide or another text document, provide a ready replacement artifact by default.
---

# 8. Model recommendations

Keep model recommendations outside delegated prompts. Recommend the cheapest capability/cost tier
likely to complete the task without expensive retries. A recommendation saves cost only when
dispatch actually selects that tier and reasoning variant; do not use a strong or `xhigh` variant
for source-confirmed bounded work unless the strong-tier criterion is met.

Capability/cost labels:

- `CHEAP MODEL OK` — narrow mechanical patches, exact replacements, well-bounded dofixes whose
  anchors and validation are already in the prompt.
- `MID MODEL RECOMMENDED` — source-confirmed multi-module work, bounded dependency closure,
  typical feature or refactor patches.
- `SMART MODEL REQUIRED` — source reconciliation, architecture, ownership changes, high-risk
  lifecycle or compatibility changes.
- `NO MODEL — OPERATOR STEP` — work that must be performed by the human coordinator or user,
  not by any delegated model.

Decision order:

1. source-truth sufficiency;
2. task type and risk;
3. expected discovery and output size;
4. user preference;
5. capability/cost tier.

Do not use a stronger model to invent missing source truth.
---

# 9. Senior audit playbook

Senior audit interpretation and exact mechanical evidence capture are coordinator-owned source-truth work.

Run audit waves independently and read-only. Each wave ends with either source-confirmed edit seams, no issue found, or a focused bundle request. An audit finding never skips the source-truth gate.

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

# 10. Pre-send / pre-accept checklist

Before sending a delegated prompt:

- Is the architecture already decided?
- Are decision-critical source anchors confirmed?
- Does the task have an expected non-empty tracked patch?
- Is the prompt sized for the chosen model tier?
- Are edit targets, retained owners, and forbidden changes explicit?
- Does a continuation include the standard checkpoint: status, diff summaries, completed edits, last success, complete failure, and next command?
- Are large outputs kept in repo-local artifacts?
- Does validation follow section 3.2 and the risk-to-validation matrix?
- Are product/domain/task-specific facts kept out of these two guides?
- Is any rule duplicated unnecessarily?

Before accepting a patch:

- Is the original requested behavior complete?
- Are required focused tests, docs, branches, and edge cases complete in this patch?
- Are ownership and compatibility boundaries preserved?
- Are known deviations and source contradictions resolved?
- Is the commit message only about the change, rationale, boundaries, and verification actually performed?
- Is broader verification requested separately when warranted?
- Does the commit have one cohesive architectural purpose rather than unrelated risk layers?
