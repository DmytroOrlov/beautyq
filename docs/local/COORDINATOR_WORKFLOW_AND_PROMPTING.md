# COORDINATOR_WORKFLOW_AND_PROMPTING.md

Purpose: Canonical coordinator-only guide for workflow, source-truth gating, prompt packaging, closeout, bundle scripts, docs ownership, and model recommendation guidance.

---

AGENTS scope boundary:

* `AGENTS.md` is repo/delegated-agent guardrails, not coordinator closeout policy.
* Coordinator closeout structure is governed by this guide.
* The coordinator may use task-specific AGENTS excerpts as source truth when building delegated prompts or high-effort audit evidence packs.
* Allowed prompt forms only:

  * `Task:` is the only allowed reusable prompt form, and it is for delegated edit work only.
  * No reusable read-only prompt forms:

    * The coordinator must not emit reusable read-only prompt forms.
    * Read-only, audit, and source-truth work must be handled directly by the coordinator in the current response when sufficient bundle evidence exists.
    * If evidence is insufficient, the coordinator must stop with `BLOCKED_NEED_BUNDLE`, `BLOCKED_NEED_BUNDLE_SCOPE`, or `BLOCKED_NEED_CLOSEOUT_SCOPE`.
    * Do not invent labels such as `Coordinator analysis contract:`, `External high-effort audit:`, `source-confirm prompt`, `read-only brief`, or `audit brief`.
    * `Do not edit files` must not appear inside a reusable prompt.
    * After an accepted actionable audit/result, the next reusable prompt must be a delegated edit `Task:`.
* Do not cite AGENTS as the reason for coordinator-only response structure.
* Do not paste all of AGENTS into prompts; include only task-specific excerpts.
* If AGENTS and this guide appear to conflict on coordinator closeout format, this guide controls the coordinator closeout format.
* If AGENTS and this guide appear to conflict on repository safety, tests, or bounded edit behavior, stop and ask for a docs clarification patch.

# 1. Universal rules

## 1.1 Role split

Coordinator does: architecture, audit, strategy, design, patch decomposition, risk decisions.
Delegated agents do: bounded edits, focused tests, mechanical verification, small cleanup, claim verification.

Workflow:

```text
user gives bundle
→ coordinator reads bundle, extracts exact facts/files/types/imports
→ coordinator designs next minimal patch (or asks for additional focused bundle if needed)
→ delegated agent receives direct edit recipe only after source truth is sufficient
→ coordinator reviews patch → proposes commit message → applies one-step lookahead
→ user runs full test when coordinator recommends it
```

## 1.2 One-step lookahead

After closing patch N, the coordinator must include exactly one of:

```text
If N+1 is source-confirmed:
  include delegated-agent prompt for N+1.
  If N+2 is visible but not source-confirmed, also include focused source-truth bundle request for N+2.

If N+1 is not source-confirmed:
  include only focused source-truth bundle request for N+1.
  Do not write a delegated-agent prompt.
```

Never write delegated-agent prompts from guesses about APIs, fields, imports, signatures, or test seams.

After every accepted patch review / closed patch, include the extended commit message before the N+1 delegated prompt or N+1 bundle request.

Mandatory bundle cycle:

* Mandatory review is not a next-task option. Option `1` and option `2` must be substantive follow-up work.
* Accepted closeout without a three-part bundle is invalid.
* Every accepted-closeout bundle MUST contain these exact labeled sections:
  * `current result review`
  * `next option 1 source truth`
  * `next option 2 source truth`
* The bundle must copy source files needed for all three sections.
* `current result review` is mandatory plumbing. It does not count as option `1` or option `2`.
* `next option 1 source truth` and `next option 2 source truth` must be task-specific. No placeholders. No current-diff-only sections. No coverage for only one next option.
* If both next-option source-truth sections cannot be provided, the coordinator MUST output only `BLOCKED_NEED_BUNDLE_SCOPE` and a corrected bundle script. No verdict. No commit message. No next-task pair.
* A user reply of only `1` or `2` is only a task selection. It is never evidence, never review, and never source truth.
* For read-only, audit, or source-truth work, the coordinator MUST wait for a fresh user bundle/report unless the user explicitly says to reuse an exact prior bundle.
* If a bundle is missing, the coordinator MUST output only `BLOCKED_NEED_BUNDLE` and the exact bundle script. No source-truth verdict. No commit message. No next-task pair.
* The next user action must be explicit: run the bundle script and upload the zip/report. Do not ask for a bare `1` or `2` if the bundle is missing.

Prompted closeout cycle:

* Any coordinator response that contains an executable prompt/brief MUST also include exactly two substantive downstream tasks, exactly one recommendation, and a three-part bundle.
* This applies only to delegated edit prompts and correction prompts that are meant to be executed by an agent.
* The prompted task is the work to run now. It must not be repeated as option `1` or option `2`.
* Option `1` and option `2` must be downstream follow-up work after the prompted task completes.
* Mandatory review is not a next-task option.
* The three-part bundle MUST contain these exact labeled sections:
  * `current result review`
  * `next option 1 source truth`
  * `next option 2 source truth`
* `current result review` reviews the result produced by the prompted task.
* `next option 1 source truth` and `next option 2 source truth` must prepare the two downstream options, not the prompted task.
* No placeholders. No current-diff-only next-option sections. No source-truth for only one downstream option.
* A bundle script alone is not a next-task prompt.
* A list of two next tasks alone is not a next-task prompt.
* If the coordinator cannot provide a prompt/brief, two downstream tasks, one recommendation, and all three bundle sections, it MUST output only `BLOCKED_NEED_CLOSEOUT_SCOPE` and a corrected closeout.

Coordinator-owned read-only work is not an agent task:

* Read-only, audit, and source-truth work is coordinator-owned and must be handled directly in the current response when sufficient bundle evidence exists.
* Do not present coordinator-owned work as a delegated agent prompt.
* `Do not edit files` must not appear inside a reusable prompt.
* If evidence is insufficient, stop with `BLOCKED_NEED_BUNDLE`, `BLOCKED_NEED_BUNDLE_SCOPE`, or `BLOCKED_NEED_CLOSEOUT_SCOPE`.
* Model recommendation for coordinator-owned read-only work is forbidden. Model recommendations are only for delegated edit prompts.
* Delegated prompts are only for edit work.

Continuation rule:

* If the user has already chosen the next task, continue that task after review acceptance. Do not re-offer the previously rejected alternative as an equal patch option.
* Continue only unless blocked by source-truth or validation/safety issues.

## 1.3 Verification labels

* `FOCUSED GREEN`: requested focused suite passed; full repo unknown.
* `FULL GREEN`: full requested project test passed.
* `USER-VERIFIED FULL GREEN`: user ran the exact command and reported green.
* `VERIFICATION BLOCKED`: sbt/docker/local permissions blocked verification.

Focused-only is never `FULL GREEN`. Do not run full `sbt test` unless explicitly requested.

## 1.4 Full test policy

Default delegated-agent mode: focused checks only.

Prompt: `Do not run full sbt test.`
Report: `Focused result only. Full verification left to coordinator/user.`

The user runs full `sbt test` when the coordinator recommends it.

## 1.5 Delegated prompt DoD

Every delegated prompt must include:

```text
Task: feat(scope): exact small change

Read only:
- exact files

Edit only:
- exact files

Current facts (source-confirmed, inlined — never say "use attached bundle"):
- exact types, imports, package paths, constructors, helpers, aliases
- exact fixture/test style
- exact metric semantics (distinct variant-id counts unless source confirms otherwise)

Goal:
- exact behavior / tests / wording to add

Forbidden:
- exact scope boundaries

Validation:
- focused command (never full sbt test unless explicitly requested)

Report only:
- focused result
- deviations / compile fixes
```

Never say "use attached bundle" instead of inlining relevant facts.
For high-specificity edits where the target hunk is known, prefer exact replacement hunk or exact before/after snippet over prose-only instructions.

## 1.6 Unused-param invariant

If a suggested param/import/local is unused and not an intentional lifecycle/readiness edge (Distage roles, seed readiness, FK table creation order, constructor deps that force graph construction), remove it or use it in real behavior.

Do not investigate scalac flags, add `@nowarn`, `@unused`, or `val _ = x` unless source confirms an intentional edge.

## 1.7 Unsafe-extraction rule

Canonical rule is in `AGENTS.md` under "Test doubles and assertions". Delegated prompts must inherit `AGENTS.md` Scala/test-style rules; inline the unsafe-extraction constraint when a task touches decoded collections, options, Either, or JSON-derived structures.

## 1.8 Metrics semantics

For B-lite / M-ESQ-EVAL prompts, inline metric semantics from current handoff/source. Default is distinct variant-id counts unless source-confirmed metric says otherwise. Do not re-explain metric semantics here; reference `docs/BEAUTYQ_CURRENT_STATE_AND_HANDOFF.md`.

## 1.9 Test style defaults

For simple pure search/eval model tests: `AnyWordSpec`, deterministic UUID fixtures, direct `assert`, no effects, no Distage, no runtime, no Docker, no ES/Qdrant clients.

Do not use: `Ref`, `Atomic*`, `var`, `Recording*`, `Counting*`, `assert(true)`, empty success branch, `isInstanceOf`, `asInstanceOf`, `Option.get`, null assertions. Use pattern matching for ADTs/options; use direct equality for case objects.

## 1.10 Commit messages

Delegated agents do not commit. Non-trivial commits use extended messages: subject + body. The body must reconstruct what changed, why it changed, and what boundary/behavior was preserved. Include user-visible effects and non-goals when they matter. Keep verification as a short trailer, not the main content. Avoid generic water such as "compile clean" unless it changes trust status or explains a known failure/fix. Do not claim FULL GREEN from focused checks. One-line messages acceptable only for tiny mechanical cleanups. Prepare the full extended message unless user asks for short subject only.

## 1.11 What goes where

`AGENTS.md`: stable repo behavior for all agents.
This file: prompt-writing guidance and coordinator workflow.
Specific prompt: task-local facts (package paths, class names, imports, helpers, metric semantics).

## 1.12 Manual/env-gated/cancel-by-default test DoD

If a patch adds env vars, BEGIN/END markers, saved-artifact flow, or manual local-service flow, close it only with docs/runbook done, queued as immediate N+1, or explicitly blocked with focused executable bundle; report that status.

## 1.13 No terminal-closing copy-paste commands

PRIORITY: User-facing copy-paste terminal commands must never close, replace, or kill the user's interactive shell/session.

Forbidden commands and patterns:

* `exit` or `exit 1` — terminates the shell.
* `exec` — replaces the shell process.
* `kill $$` — kills the current shell.
* Terminating `trap` handlers that call `exit`.
* `set -e`, `set -u`, `set -o pipefail` — change shell behavior globally and may cause unexpected termination.

Required guidance:

* Validation guards must be non-terminating. On failure, print `BLOCKED` or `MISSING`, set a local flag, skip dependent steps with `if`, and still print diagnostics.
* Do not use `exit 1` in copy-paste commands. Use local flags and conditional branching instead.

Bad example:

```bash
if [ ! -s "$f" ]; then echo "MISSING"; exit 1; fi
```

Good example:

```bash
MISSING=0
if [ ! -s "$f" ]; then echo "MISSING"; MISSING=1; fi
if [ "$MISSING" = "0" ]; then
  # proceed with dependent steps
  echo "OK"
else
  echo "BLOCKED: required file missing"
fi
```

## 1.14 No `/tmp` for persisted evidence workspaces

PRIORITY: `/tmp` may be used only for disposable bundles or short-lived scratch output.

Multi-iteration evidence workspaces must not live in `/tmp`. Persisted evidence workspaces should default to project-local `./.beautyq-evidence-runs/<run-id>/`, and that path must be gitignored or added to `.git/info/exclude` before use. `$HOME` or another external path may be used only when the user explicitly asks for an external workspace.

User-facing commands must print the final workspace path and zip path. User-facing artifact commands must use `cpf "$ZIP"` and then `echo "$ZIP"`.
That final archive handoff remains the user-terminal flow even if an agent shell does not have `cpf` installed. In agent/non-interactive shells, `cpf "$ZIP"` may fail with `command not found`; the correct response is to still create the zip, print `echo "$ZIP"`, and report the deviation honestly. Do not replace `cpf "$ZIP"` with `pbcopy`, and do not remove it from user-facing terminal snippets because an agent shell lacks it.

---

# 2. Model recommendation guidance

Purpose and policy:

* Model recommendation blocks are only for non-trivial delegated edit prompts.
* Do not include model recommendations for coordinator-owned read-only, audit, review, or source-truth work.
* Keep delegated prompts themselves model-agnostic unless the user explicitly asks otherwise.

Decision order:

1. Source truth.
2. Task type and risk.
3. User preference.
4. Model choice.

Rules:

* Source-truth gate wins before model choice.
* If required source facts are missing, request a focused bundle and stop.
* Do not use a stronger model to invent APIs, signatures, fields, imports, tests, or docs facts.
* Use exact names when the recommendation is actionable.

Exact model names:

* `Qwen 256/512`
* `Qwen 1024/2048`
* `Qwen 4096`
* `MiMo-V2.5`
* `MiMo-V2.5-Pro`
* `MiniMax-M3`
* `GPT-5.5-medium`
* `GPT-5.5-high`

Compact mapping:

* Tiny mechanical docs/code edits: `Qwen 256/512` or `MiMo-V2.5`.
* Bounded pure code/docs patches from exact recipe: `Qwen 1024/2048` or `MiMo-V2.5-Pro`.
* Larger source-confirmed local work: `Qwen 4096`.
* Source-confirmed repo inventory or stronger non-GPT agentic work: `MiniMax-M3`.
* Complex code writing when cheaper/local models are likely to waste iterations: `GPT-5.5-medium`.
* Very high-risk production lifecycle/runtime/backend migration code: `GPT-5.5-high`.

## 2.1 Model recommendation block template

For non-trivial delegated edit prompts, provide a separate coordinator note before the prompt:

```text
Task classification:
- Type:
- Source truth:
- Risk:
- Preference:
- Availability:

Run recommendation:
- Cheapest likely to work:
- Faster cloud option:
- Stronger non-GPT option:
- GPT option, only if justified:
- If source truth is missing:
```

Constraints:

* This note is not part of the delegated prompt unless the user asks.
* Do not use it for coordinator-owned read-only, audit, review, or source-truth work.
* Keep delegated prompts model-agnostic: no `Model: ...`, no `thinking-budget=...`, no `Use AGENTS.md` boilerplate.
* Use exact model/tier names.
* Recommend the minimal sufficient model, not a comfortable/heavier default.
* If source truth is missing, recommend requesting a focused bundle first; do not recommend a stronger model to infer missing APIs.
* Docs-only or narrow review-fix delegated edit tasks should prefer cheaper/local models unless the policy/doc ownership complexity justifies a stronger model.

---

# 3. Bundle script rules

Bundles are for the coordinator, not delegated agents. After reading a bundle, the coordinator must inline important facts into the delegated prompt.

Combined post-patch bundle: when providing a delegated patch prompt, also provide one post-patch bundle script covering:

* `current result review`
* `next option 1 source truth`
* `next option 2 source truth`

This combined bundle must remain read-only, task-relevant, structured, and use the existing `cpf "$ZIP"` workflow. It does not bypass the source-truth gate; if chosen-task anchors are still missing after review, request a focused supplemental bundle.

When requesting a bundle from the user, provide an executable shell script, not a prose include-list.

Script rules:

* Create a structured bundle directory: `BASE="/tmp/beautyq-<topic>-<timestamp>-$RANDOM"`, `WORK="$BASE.dir"`, `BUNDLE_ID="$(basename "$BASE")"`.
* Every artifact inside the bundle zip must include the bundle id in its basename. Do not create generic internal filenames such as `bundle.txt`, `tracked-changes-from-head.patch`, `unstaged-tracked-changes.patch`, or `untracked-files.tar.gz`.
* Include only task-relevant status, compact diff, signatures, nearby specs, docs anchors, hazard scans.
* Cap/truncate output when large.
* After truncation, zip the bundle directory (`ZIP="$BASE.zip"`, `zip -9 -r "$ZIP" .` inside `WORK`), then print `wc -c` for each artifact and the zip, then `cpf "$ZIP"`, then `echo "$ZIP"`.
* Do not include `/tmp`, full `target`, generated build output, screenshots, stale numbered files, or broad `HEAD~N --patch` unless explicitly requested.
* Bundle scripts are read-only context capture only. They may use `git`, bounded `rg/sed`, diff generation, untracked-file archiving, truncation, and zip upload. They must not run `sbt`, tests, Docker cleanup/startup, `find target -delete`, network/resource probes, container launches, package managers, or other heavy/mutating commands. Full verification commands belong outside the bundle and must be run explicitly by the user/coordinator.

Patch-review bundle DoD (post-agent / patch-review bundles): include `git diff --binary HEAD --` as `tracked-changes-from-head.patch`, include `git diff --binary --cached` as `staged-tracked-changes.patch` and `git diff --binary` as `unstaged-tracked-changes.patch` when useful, collect untracked nonignored files NUL-safely and archive into `untracked-files.tar.gz` with a readable manifest, then zip the whole bundle directory and upload only the zip.

Canonical shell shape:

```bash
BASE="/tmp/beautyq-<topic>-$(date +%Y%m%d-%H%M%S)-$RANDOM"
WORK="$BASE.dir"
BUNDLE_ID="$(basename "$BASE")"
OUT="$WORK/${BUNDLE_ID}-bundle.txt"

mkdir -p "$WORK"

{
  echo "## status"
  git status --short
  echo

  echo "## relevant anchors"
  rg -n "PatternA|PatternB" AGENTS.md docs bifunctor-tagless/src/main bifunctor-tagless/src/test || true

  echo "## recent commits (subjects)"
  git --no-pager log -14 --oneline
  echo

  echo "## recent commits (full bodies)"
  git --no-pager log -14 --date=iso-strict --format='commit %H%nAuthor: %an <%ae>%nDate: %ad%n%n%s%n%n%b%n---END COMMIT---'
  echo
} > "$OUT" 2>&1

git --no-pager diff --binary HEAD -- > "$WORK/${BUNDLE_ID}-tracked-changes-from-head.patch" 2>&1 || true

git --no-pager diff --binary --cached > "$WORK/${BUNDLE_ID}-staged-tracked-changes.patch" 2>&1 || true

git --no-pager diff --binary > "$WORK/${BUNDLE_ID}-unstaged-tracked-changes.patch" 2>&1 || true

git ls-files --others --exclude-standard -z > "$WORK/${BUNDLE_ID}-untracked-files.nul"

python3 - <<'PY' "$WORK" "$BUNDLE_ID"
import pathlib
import sys
import tarfile

work = pathlib.Path(sys.argv[1])
bundle_id = sys.argv[2]
repo = pathlib.Path.cwd()
nul = work / f"{bundle_id}-untracked-files.nul"
manifest = work / f"{bundle_id}-untracked-files.manifest.txt"
tar_path = work / f"{bundle_id}-untracked-files.tar.gz"

items = [p for p in nul.read_bytes().split(b"\0") if p]
paths = [p.decode("utf-8", errors="replace") for p in items]

manifest.write_text(
    "\n".join(paths) + ("\n" if paths else ""),
    encoding="utf-8",
)

with tarfile.open(tar_path, "w:gz", dereference=False) as tar:
    for rel in paths:
        path = repo / rel
        if path.exists() or path.is_symlink():
            tar.add(path, arcname=rel, recursive=False)
PY

python3 - <<'PY' "$OUT"
import pathlib, sys
p = pathlib.Path(sys.argv[1])
data = p.read_text(errors="replace")
limit = 700_000
if len(data.encode()) > limit:
    p.write_text(data[:limit] + "\n\n## TRUNCATED\n")
PY

ZIP="$BASE.zip"
rm -f "$ZIP"
(cd "$WORK" && zip -9 -r "$ZIP" .) >/dev/null

wc -c "$OUT"
wc -c "$WORK/${BUNDLE_ID}-tracked-changes-from-head.patch"
wc -c "$WORK/${BUNDLE_ID}-staged-tracked-changes.patch"
wc -c "$WORK/${BUNDLE_ID}-unstaged-tracked-changes.patch"
wc -c "$WORK/${BUNDLE_ID}-untracked-files.manifest.txt"
wc -c "$WORK/${BUNDLE_ID}-untracked-files.tar.gz"
wc -c "$ZIP"
cpf "$ZIP"
echo "$ZIP"
```

---

# 4. Documentation ownership

Before adding or changing a documented fact, identify its canonical owner. Prefer one canonical owner per fact; other docs should use short summaries and pointers.

Duplicate only safety-critical guardrails that must be visible at multiple entrypoints; keep those duplicates short and free of implementation detail. Do not copy long API lists, metric semantics, roadmap state, bundle rules, or prompt-writing rules into multiple docs.

Exact volatile verification counts belong in reports, not long-lived docs nor commit messages.

The source-truth gate is safety-critical and must not be deduplicated away; keep the canonical rule in this file and only link to it from other docs.

---

# 5. Before sending any delegated prompt, check

```text
□ Did I ask the agent to audit/design when I can do it?
□ Did I say "use attached bundle" instead of inlining facts?
□ Did I include exact read/edit files?
□ Did I include known imports, package paths, constructors, aliases, fixture/test style, validation command, boundaries?
□ Did I include an unused suggested param?
□ Did I tell it not to run full sbt test?
□ Did I keep report short?
□ Did I avoid model/thinking boilerplate?
□ Did I provide a model recommendation block only for non-trivial delegated edit prompts?
□ Did I avoid model recommendations for coordinator-owned read-only work?
□ If I need a bundle, did I give an executable shell script?
```

If any answer is bad, rewrite the prompt before sending.

---

# 6. Source-truth gate

This section is a protected coordinator invariant. Do not remove, shorten, soften, or move it into `AGENTS.md`. It may only be replaced by wording that is at least as strict: missing source truth must stop patch planning, delegated-agent prompts, adjacent "safe" patches, and invented helpers/APIs.

Before any patch design or delegated-agent prompt, source-confirm the relevant files, types, functions, and fields.

Docs and handoff establish current state and priorities; they are not enough for exact patch APIs. If source truth is missing, ask the user for a focused bundle and stop.

Do not invent conceptual APIs, method signatures, field mappings, test recipes, or agent tasks from docs/memory. This applies before every task, not only during onboarding.

This gate is coordinator responsibility. Delegated prompts should contain exact source-confirmed facts, read/edit files, and validation commands; they should not ask agents to compensate with broad repo searches unless explicitly intended.

If the requested output includes a patch proposal, exact read/edit files, a test recipe, or a delegated-agent prompt, but required source truth is missing, explicitly decline that part of the output. Ask for a focused bundle and stop.

Do not salvage a source-incomplete task by inventing a source-independent helper, adapter, model API, field mapping, merge rule, or test plan unless the current bundle source-confirms that this helper/API is the next required seam.

Do not rescope a source-incomplete task into an adjacent "safe" patch. If the requested task requires missing anchors, stop at the bundle request unless the user explicitly approves a different task after seeing the missing-source report.

A partial inventory may list `SOURCE_CONFIRMED` and `DOC_LEVEL_ONLY` facts, but when required anchors are missing it must end with a bundle request, not a patch proposal. The source-truth gate has higher priority than the requested output shape.
